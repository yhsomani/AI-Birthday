/// Single-flight durable job worker behavior (background-jobs audit).
///
/// All retries use `backoffStep: Duration.zero`, which makes a retried job
/// immediately claimable again, so the tests run deterministically without
/// real timers and exercise the full attempt budget in one drain.
library;

import 'dart:async';

import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/core/database/app_database.dart' as appdb;
import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/features/jobs/application/job_worker.dart';
import 'package:ai_birthday/features/jobs/data/jobs_repository.dart';
import 'package:ai_birthday/features/jobs/domain/job.dart';

import '../../../helpers/counting_query_interceptor.dart';

/// Handler stub with explicit outcomes and a run counter.
class StubHandler implements JobHandler {
  StubHandler(this.outcomeFactory);

  final Future<JobOutcome> Function(JobRecord job, int call) outcomeFactory;
  int calls = 0;

  @override
  Future<JobOutcome> run(JobRecord job) {
    calls++;
    return outcomeFactory(job, calls);
  }
}

void main() {
  allowMultipleInMemoryDatabases();
  late appdb.AppDatabase database;
  late DriftJobsRepository repo;

  setUp(() {
    database = appdb.AppDatabase(NativeDatabase.memory());
    repo = DriftJobsRepository(database);
  });

  tearDown(() => database.close());

  JobWorker worker(Map<String, JobHandler> handlers) {
    return JobWorker(
      repository: repo,
      handlers: handlers,
      backoffStep: Duration.zero,
    );
  }

  /// Completes with the first event at [status] on [worker]'s bus.
  ///
  /// Attach it before `start()`/`enqueue` so no transition is missed: the
  /// job id is only known after enqueue, so the matcher is status-only.
  Future<JobRecord> waitFor(JobWorker w, JobStatus status) {
    final done = Completer<JobRecord>();
    late StreamSubscription<JobRecord> sub;
    sub = w.events.listen((job) {
      if (job.status == status && !done.isCompleted) {
        done.complete(job);
      }
    });
    return done.future.whenComplete(sub.cancel);
  }

  test(
    'happy path: run once, emit states, mark succeeded with resultRef',
    () async {
      final handler = StubHandler((_, _) async => const JobSucceeded('ref-1'));
      final w = worker({JobTypes.aiGenerate: handler});
      final done = waitFor(w, JobStatus.succeeded);
      w.start();

      final job = await w.enqueue(
        type: JobTypes.aiGenerate,
        subjectId: 'b1',
        maxAttempts: 1,
      );
      await done;

      expect(handler.calls, 1);
      final row = (await repo.getById(job.id))!;
      expect(row.status, JobStatus.succeeded);
      expect(row.resultRef, 'ref-1');
      expect(row.attempts, 1);
      await w.stop();
    },
  );

  test(
    'retryable failure then success: eager retry within attempt budget',
    () async {
      final handler = StubHandler((_, call) async {
        if (call == 1) {
          return const JobFailed(
            retryable: true,
            code: 'network',
            message: 'Try again.',
          );
        }
        return const JobSucceeded('ref-2');
      });
      final w = worker({JobTypes.aiGenerate: handler});
      final done = waitFor(w, JobStatus.succeeded);
      w.start();

      final job = await w.enqueue(
        type: JobTypes.aiGenerate,
        subjectId: 'b1',
        maxAttempts: 2,
      );
      await done;

      expect(handler.calls, 2);
      final row = (await repo.getById(job.id))!;
      expect(row.status, JobStatus.succeeded);
      expect(row.attempts, 2);
      await w.stop();
    },
  );

  test('permanent failure is final on the first run', () async {
    final handler = StubHandler(
      (_, _) async => const JobFailed(
        retryable: false,
        code: 'auth',
        message: 'Sign in first.',
      ),
    );
    final w = worker({JobTypes.aiGenerate: handler});
    final done = waitFor(w, JobStatus.failed);
    w.start();

    final job = await w.enqueue(
      type: JobTypes.aiGenerate,
      subjectId: 'b1',
      maxAttempts: 3, // never used: permanent errors do not retry
    );
    await done;

    expect(handler.calls, 1);
    final row = (await repo.getById(job.id))!;
    expect(row.status, JobStatus.failed);
    expect(row.errorCode, 'auth');
    expect(row.finishedAt, isNotNull);
    await w.stop();
  });

  test('retry budget is bounded: exhausted attempts end in failed', () async {
    final handler = StubHandler(
      (_, _) async => const JobFailed(
        retryable: true,
        code: 'network',
        message: 'Still down.',
      ),
    );
    final w = worker({JobTypes.aiGenerate: handler});
    final done = waitFor(w, JobStatus.failed);
    w.start();

    final job = await w.enqueue(
      type: JobTypes.aiGenerate,
      subjectId: 'b1',
      maxAttempts: 2,
    );
    await done;

    expect(handler.calls, 2); // attempt 1 + exactly one retry, never more
    expect((await repo.getById(job.id))!.status, JobStatus.failed);
    await w.stop();
  });

  test(
    'handler throwing AppFailure maps retryability + user-safe copy',
    () async {
      final handler = StubHandler(
        (_, _) async => throw const AppFailure.networkUnavailable(),
      );
      final w = worker({JobTypes.aiGenerate: handler});
      final done = waitFor(w, JobStatus.failed);
      w.start();

      final job = await w.enqueue(
        type: JobTypes.aiGenerate,
        subjectId: 'b1',
        maxAttempts: 1, // retryable but budget is 1 -> final failed
      );
      await done;

      final row = (await repo.getById(job.id))!;
      expect(row.status, JobStatus.failed);
      expect(row.errorCode, AppFailureCode.networkUnavailable.name);
      expect(row.errorMessage, contains('No connection'));
      await w.stop();
    },
  );

  test('unregistered job type fails fast with a stable code', () async {
    final w = worker(const {});
    final done = waitFor(w, JobStatus.failed);
    w.start();

    final job = await w.enqueue(type: 'unknown', subjectId: 's');
    await done;

    final row = (await repo.getById(job.id))!;
    expect(row.status, JobStatus.failed);
    expect(row.errorCode, 'unknown');
    await w.stop();
  });

  test(
    'cancel while running discards the result and keeps the row canceled',
    () async {
      final gate = Completer<void>();
      final handler = StubHandler((_, _) async {
        await gate.future;
        return const JobSucceeded('must-be-discarded');
      });
      final w = worker({JobTypes.aiGenerate: handler});
      final running = waitFor(w, JobStatus.running);
      w.start();

      final job = await w.enqueue(type: JobTypes.aiGenerate, subjectId: 'b1');
      await running; // handler is in flight, awaiting the gate

      await repo.cancel(job.id);
      final canceled = waitFor(w, JobStatus.canceled);
      gate.complete();
      await canceled;

      final row = (await repo.getById(job.id))!;
      expect(row.status, JobStatus.canceled);
      expect(row.resultRef, isNull); // effect discarded
      expect(handler.calls, 1);
      await w.stop();
    },
  );

  test(
    'crash recovery: stale running job is reset and run on next start',
    () async {
      final now = DateTime.now();
      await database
          .into(database.jobs)
          .insert(
            appdb.JobsCompanion.insert(
              id: 'crashed',
              type: JobTypes.aiGenerate,
              status: JobStatus.running.name,
              subjectId: 'b1',
              startedAt: Value(now.subtract(const Duration(minutes: 9))),
              attempts: const Value(1),
              maxAttempts: const Value(1),
              createdAt: now.subtract(const Duration(minutes: 10)),
            ),
          );

      final handler = StubHandler((_, _) async => const JobSucceeded('ref'));
      final w = worker({JobTypes.aiGenerate: handler});
      final done = waitFor(w, JobStatus.succeeded);
      w.start();
      await done;

      final row = (await repo.getById('crashed'))!;
      expect(row.status, JobStatus.succeeded);
      expect(row.attempts, 2); // the pre-crash attempt + this run
      expect(handler.calls, 1);
      await w.stop();
    },
  );

  test(
    'duplicate delivery after recovery re-runs cleanly (idempotent)',
    () async {
      final handler = StubHandler((_, _) async => const JobSucceeded('ref'));
      final w = worker({JobTypes.aiGenerate: handler});
      final first = waitFor(w, JobStatus.succeeded);
      w.start();

      final job = await w.enqueue(type: JobTypes.aiGenerate, subjectId: 'b1');
      await first;
      expect(handler.calls, 1);

      // Simulate a crash that restored the terminal row to queued.
      await (database.update(database.jobs)).write(
        appdb.JobsCompanion(
          status: Value(JobStatus.queued.name),
          startedAt: const Value(null),
          finishedAt: const Value(null),
        ),
      );

      final second = waitFor(w, JobStatus.succeeded);
      w.notify();
      await second;

      expect(handler.calls, 2); // delivered twice, both completed cleanly
      expect((await repo.getById(job.id))!.status, JobStatus.succeeded);
      await w.stop();
    },
  );
}
