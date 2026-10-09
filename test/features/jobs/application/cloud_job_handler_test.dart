/// Durable `cloud_sync` handler: ownership guard, operation routing, and
/// failure classification (background-jobs audit).
///
/// The Firestore HTTP behavior itself is covered by
/// `cloud_sync_service_test.dart`; here the service is faked so the handler's
/// contract is exercised deterministically.
library;

import 'dart:async';
import 'dart:convert';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/core/database/app_database.dart' show AppDatabase;
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/auth/domain/auth_state.dart';
import 'package:ai_birthday/features/auth/domain/google_identity.dart';
import 'package:ai_birthday/features/jobs/application/cloud_job_handler.dart';
import 'package:ai_birthday/features/jobs/application/job_worker.dart';
import 'package:ai_birthday/features/jobs/data/jobs_repository.dart';
import 'package:ai_birthday/features/jobs/domain/job.dart';
import 'package:ai_birthday/features/sync/data/cloud_sync_service.dart';

import '../../../helpers/counting_query_interceptor.dart';

class InMemoryStoreDriver implements SecureStoreDriver {
  final Map<String, String> data = {};

  @override
  Future<void> delete(String key) async => data.remove(key);

  @override
  Future<String?> read(String key) async => data[key];

  @override
  Future<void> write(String key, String value) async => data[key] = value;
}

/// Fake service: records which operation was asked for and returns canned
/// results, so the handler's routing and mapping are the only things under
/// test here.
class FakeCloudSyncService extends CloudSyncService {
  FakeCloudSyncService({required super.db})
    : super(store: InMemoryStoreDriver());

  CloudSyncResult backupResult = CloudSyncResult(
    success: true,
    timestamp: DateTime.utc(2026, 1, 1),
  );
  CloudSyncResult restoreResult = CloudSyncResult(
    success: true,
    timestamp: DateTime.utc(2026, 1, 1),
  );
  int backupCalls = 0;
  int restoreCalls = 0;

  @override
  Future<CloudSyncResult> sync(AuthState authState) async {
    backupCalls++;
    return backupResult;
  }

  @override
  Future<CloudSyncResult> restore(AuthState authState) async {
    restoreCalls++;
    return restoreResult;
  }
}

const _identity = GoogleIdentity(
  googleSubject: 'acct-1',
  firebaseUid: 'acct-1',
  email: 'ana@example.com',
  displayName: 'Ana',
  idToken: 'not-a-real-token',
);

void main() {
  allowMultipleInMemoryDatabases();
  late AppDatabase db;
  late DriftJobsRepository repo;
  late FakeCloudSyncService service;

  setUp(() {
    db = AppDatabase(NativeDatabase.memory());
    repo = DriftJobsRepository(db);
    service = FakeCloudSyncService(db: db);
  });

  tearDown(() => db.close());

  CloudSyncJobHandler handler() {
    return CloudSyncJobHandler(
      service: service,
      authState: () async =>
          const AuthState(status: AuthStatus.signedIn, identity: _identity),
    );
  }

  Future<JobRecord> claimedJob({
    required String operation,
    String subjectId = 'acct-1',
  }) async {
    await repo.enqueue(
      type: JobTypes.cloudSync,
      subjectId: subjectId,
      payload: jsonEncode(CloudJobPayload(operation: operation).toJson()),
      maxAttempts: 1,
    );
    return (await repo.claimNext(DateTime.now()))!;
  }

  test(
    'signed-out session fails permanently with auth before any call',
    () async {
      final handler = CloudSyncJobHandler(
        service: service,
        authState: () async => const AuthState(status: AuthStatus.signedOut),
      );

      final outcome = await handler.run(
        await claimedJob(operation: CloudJobPayload.backup),
      );

      expect(outcome, isA<JobFailed>());
      final failed = outcome as JobFailed;
      expect(failed.retryable, isFalse);
      expect(failed.code, 'auth');
      expect(service.backupCalls, 0);
      expect(service.restoreCalls, 0);
    },
  );

  test('ownership guard: a job never runs under a different account', () async {
    final outcome = await handler().run(
      await claimedJob(
        operation: CloudJobPayload.backup,
        subjectId: 'stranger',
      ),
    );

    expect(outcome, isA<JobFailed>());
    final failed = outcome as JobFailed;
    expect(failed.retryable, isFalse);
    expect(failed.code, 'auth');
    expect(service.backupCalls, 0); // refused before touching the service
  });

  test('backup success routes to sync and reports the timestamp', () async {
    final outcome = await handler().run(
      await claimedJob(operation: CloudJobPayload.backup),
    );

    expect(outcome, isA<JobSucceeded>());
    expect((outcome as JobSucceeded).resultRef, '2026-01-01T00:00:00.000Z');
    expect(service.backupCalls, 1);
    expect(service.restoreCalls, 0);
  });

  test('restore success routes to restore', () async {
    final outcome = await handler().run(
      await claimedJob(operation: CloudJobPayload.restore),
    );

    expect(outcome, isA<JobSucceeded>());
    expect(service.restoreCalls, 1);
    expect(service.backupCalls, 0);
  });

  test(
    'transient failures map to retryable JobFailed with their code',
    () async {
      service.backupResult = CloudSyncResult(
        success: false,
        error: 'Could not reach the service.',
        errorCode: 'network',
        retryable: true,
        timestamp: DateTime.utc(2026, 1, 1),
      );

      final outcome = await handler().run(
        await claimedJob(operation: CloudJobPayload.backup),
      );

      expect(outcome, isA<JobFailed>());
      final failed = outcome as JobFailed;
      expect(failed.retryable, isTrue);
      expect(failed.code, 'network');
      expect(failed.message, 'Could not reach the service.');
    },
  );

  test('permanent failures (auth/config) are never retried', () async {
    service.backupResult = CloudSyncResult(
      success: false,
      error: 'Your cloud session has expired.',
      errorCode: 'auth',
      timestamp: DateTime.utc(2026, 1, 1),
    );

    final outcome = await handler().run(
      await claimedJob(operation: CloudJobPayload.backup),
    );

    expect(outcome, isA<JobFailed>());
    final failed = outcome as JobFailed;
    expect(failed.retryable, isFalse);
    expect(failed.code, 'auth');
  });

  group('worker enforces the Settings attempt budget', () {
    test('backup retries once (maxAttempts 2) before giving up', () async {
      service.backupResult = CloudSyncResult(
        success: false,
        error: 'Could not reach the service.',
        errorCode: 'network',
        retryable: true,
        timestamp: DateTime.utc(2026, 1, 1),
      );

      final worker = JobWorker(
        repository: repo,
        handlers: {
          JobTypes.cloudSync: CloudSyncJobHandler(
            service: service,
            authState: () async => const AuthState(
              status: AuthStatus.signedIn,
              identity: _identity,
            ),
          ),
        },
        backoffStep: Duration.zero,
      );
      worker.start();
      final done = Completer<JobRecord>();
      final sub = worker.events.listen((job) {
        if (job.type == JobTypes.cloudSync && job.status == JobStatus.failed) {
          done.complete(job);
        }
      });

      await worker.enqueue(
        type: JobTypes.cloudSync,
        subjectId: 'acct-1',
        payload: jsonEncode(
          const CloudJobPayload(operation: CloudJobPayload.backup).toJson(),
        ),
        maxAttempts: 2,
      );
      final job = await done.future;
      await sub.cancel();

      expect(service.backupCalls, 2); // first attempt + exactly one retry
      expect(job.status, JobStatus.failed);
      expect(job.attempts, 2);
      await worker.stop();
    });

    test(
      'restore does NOT auto-retry (maxAttempts 1) — user must ask again',
      () async {
        service.restoreResult = CloudSyncResult(
          success: false,
          error: 'Could not reach the service.',
          errorCode: 'network',
          retryable: true,
          timestamp: DateTime.utc(2026, 1, 1),
        );

        final worker = JobWorker(
          repository: repo,
          handlers: {
            JobTypes.cloudSync: CloudSyncJobHandler(
              service: service,
              authState: () async => const AuthState(
                status: AuthStatus.signedIn,
                identity: _identity,
              ),
            ),
          },
          backoffStep: Duration.zero,
        );
        worker.start();
        final done = Completer<JobRecord>();
        final sub = worker.events.listen((job) {
          if (job.type == JobTypes.cloudSync &&
              job.status == JobStatus.failed) {
            done.complete(job);
          }
        });

        await worker.enqueue(
          type: JobTypes.cloudSync,
          subjectId: 'acct-1',
          payload: jsonEncode(
            const CloudJobPayload(operation: CloudJobPayload.restore).toJson(),
          ),
          maxAttempts: 1,
        );
        final job = await done.future;
        await sub.cancel();

        expect(
          service.restoreCalls,
          1,
        ); // destructive merge only runs on demand
        expect(job.status, JobStatus.failed);
        await worker.stop();
      },
    );
  });
}
