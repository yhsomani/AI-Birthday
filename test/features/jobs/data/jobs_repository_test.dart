/// Drift-backed durable job queue semantics (background-jobs audit).
///
/// Covers the contract the worker depends on: enqueue dedupe, FIFO claim
/// ordering, status transitions, retention pruning, cancel, and crash
/// recovery (`resetStale`).
library;

import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/core/database/app_database.dart' as appdb;
import 'package:ai_birthday/features/jobs/data/jobs_repository.dart';
import 'package:ai_birthday/features/jobs/domain/job.dart';

import '../../../helpers/counting_query_interceptor.dart';

void main() {
  allowMultipleInMemoryDatabases();
  late appdb.AppDatabase database;
  late DriftJobsRepository repo;

  setUp(() {
    database = appdb.AppDatabase(NativeDatabase.memory());
    repo = DriftJobsRepository(database);
  });

  tearDown(() => database.close());

  test(
    'enqueue dedupes identical inputs and rejects different active inputs',
    () async {
      final first = await repo.enqueue(
        type: JobTypes.aiGenerate,
        subjectId: 'b1',
        payload: '{"message":"one"}',
        maxAttempts: 3,
      );
      final again = await repo.enqueue(
        type: JobTypes.aiGenerate,
        subjectId: 'b1',
        payload: '{"message":"one"}',
        maxAttempts: 5,
      );

      expect(again.id, first.id);
      expect(
        again.maxAttempts,
        3,
      ); // the original job wins, not the new request
      await expectLater(
        repo.enqueue(
          type: JobTypes.aiGenerate,
          subjectId: 'b1',
          payload: '{"message":"two"}',
        ),
        throwsStateError,
      );

      // A different subject is a different job.
      final other = await repo.enqueue(
        type: JobTypes.aiGenerate,
        subjectId: 'b2',
      );
      expect(other.id, isNot(first.id));

      // A terminal job frees the slot: a new generation can be queued.
      await repo.claimNext(DateTime.now());
      await repo.markSucceeded(first.id, expectedAttempt: 1, resultRef: 'b1');
      final fresh = await repo.enqueue(
        type: JobTypes.aiGenerate,
        subjectId: 'b1',
        maxAttempts: 2,
      );
      expect(fresh.id, isNot(first.id));
      expect(fresh.id, startsWith('${JobTypes.aiGenerate}_b1_'));
    },
  );

  test(
    'concurrent identical enqueue calls create only one active job',
    () async {
      final jobs = await Future.wait(
        List.generate(
          4,
          (_) => repo.enqueue(
            type: JobTypes.aiGenerate,
            subjectId: 'concurrent',
            payload: '{}',
          ),
        ),
      );

      expect(jobs.map((job) => job.id).toSet(), hasLength(1));
      expect(
        await repo.activeJob(JobTypes.aiGenerate, 'concurrent'),
        isNotNull,
      );
    },
  );

  test(
    'claimNext claims the oldest due job first and returns running state',
    () async {
      final now = DateTime.now();
      await database
          .into(database.jobs)
          .insert(
            appdb.JobsCompanion.insert(
              id: 'old',
              type: 't',
              status: JobStatus.queued.name,
              subjectId: 'a',
              createdAt: now.subtract(const Duration(seconds: 5)),
              maxAttempts: const Value(1),
            ),
          );
      await database
          .into(database.jobs)
          .insert(
            appdb.JobsCompanion.insert(
              id: 'new',
              type: 't',
              status: JobStatus.queued.name,
              subjectId: 'b',
              createdAt: now.subtract(const Duration(seconds: 1)),
              maxAttempts: const Value(1),
            ),
          );

      final first = await repo.claimNext(now);
      expect(first!.id, 'old');
      expect(first.status, JobStatus.running);
      expect(first.attempts, 1);
      expect(
        first.startedAt!.difference(now).inSeconds.abs(),
        lessThanOrEqualTo(1),
      );

      final second = await repo.claimNext(now);
      expect(second!.id, 'new');
      expect(await repo.claimNext(now), isNull); // nothing else due
    },
  );

  test('markRetrying defers the retry and records the failure', () async {
    final job = await repo.enqueue(type: 't', subjectId: 's', maxAttempts: 3);
    await repo.claimNext(DateTime.now());
    final nextRetryAt = DateTime.now().add(const Duration(seconds: 4));

    await repo.markRetrying(
      job.id,
      expectedAttempt: 1,
      nextRetryAt: nextRetryAt,
      errorCode: 'network',
      errorMessage: 'Could not reach the service.',
    );

    final row = (await repo.getById(job.id))!;
    expect(row.status, JobStatus.retrying);
    // Drift stores DateTime as unix seconds (UTC, sub-second precision lost),
    // so compare within a second rather than exactly.
    expect(
      row.nextRetryAt!.difference(nextRetryAt).inSeconds.abs(),
      lessThanOrEqualTo(1),
    );
    expect(row.errorCode, 'network');
    expect(row.errorMessage, 'Could not reach the service.');
    expect(row.startedAt, isNull); // un-runs itself so a crash re-queues it

    // A retrying job still counts as active for dedupe.
    final again = await repo.enqueue(type: 't', subjectId: 's');
    expect(again.id, job.id);
  });

  test('claimNext waits for a retrying job until nextRetryAt is due', () async {
    final job = await repo.enqueue(type: 't', subjectId: 's', maxAttempts: 2);
    final now = DateTime.now();
    await repo.claimNext(now);
    await repo.markRetrying(
      job.id,
      expectedAttempt: 1,
      nextRetryAt: now.add(const Duration(seconds: 30)),
      errorCode: 'network',
      errorMessage: 'backoff',
    );

    expect(await repo.claimNext(now), isNull); // not due yet
    final later = now.add(const Duration(seconds: 31));
    final claimed = await repo.claimNext(later);
    expect(claimed!.id, job.id);
    expect(claimed.attempts, 2);
  });

  test(
    'markSucceeded prunes older terminal rows (bounded retention)',
    () async {
      final j1 = await repo.enqueue(type: JobTypes.aiGenerate, subjectId: 'b1');
      await repo.claimNext(DateTime.now());
      await repo.markSucceeded(j1.id, expectedAttempt: 1, resultRef: 'r1');

      final j2 = await repo.enqueue(type: JobTypes.aiGenerate, subjectId: 'b1');
      await repo.claimNext(DateTime.now());
      await repo.markSucceeded(j2.id, expectedAttempt: 1, resultRef: 'r2');

      expect(await repo.getById(j1.id), isNull); // pruned
      expect((await repo.getById(j2.id))!.status, JobStatus.succeeded);
      expect(await repo.latestJob(JobTypes.aiGenerate, 'b1'), isNotNull);
      expect(await repo.latestJob(JobTypes.aiGenerate, 'other'), isNull);
    },
  );

  test(
    'cancel flips active jobs to canceled and never touches terminal rows',
    () async {
      final job = await repo.enqueue(type: 't', subjectId: 's');
      final claimed = await repo.claimNext(DateTime.now());
      await repo.cancel(claimed!.id);

      expect((await repo.getById(job.id))!.status, JobStatus.canceled);
      expect(await repo.isCanceled(job.id), isTrue);

      final done = await repo.enqueue(type: 't', subjectId: 's2');
      await repo.claimNext(DateTime.now());
      await repo.markSucceeded(done.id, expectedAttempt: 1);
      await repo.cancel(done.id); // no-op

      expect((await repo.getById(done.id))!.status, JobStatus.succeeded);
    },
  );

  test('terminal AI transitions scrub persisted prompt text', () async {
    final success = await repo.enqueue(
      type: JobTypes.aiGenerate,
      subjectId: 'success',
      payload: '{"existingMessage":"private draft"}',
    );
    await repo.claimNext(DateTime.now());
    expect(await repo.markSucceeded(success.id, expectedAttempt: 1), isTrue);
    expect((await repo.getById(success.id))!.payload, isNull);

    final failed = await repo.enqueue(
      type: JobTypes.aiGenerate,
      subjectId: 'failed',
      payload: '{"customInstruction":"private instruction"}',
    );
    await repo.claimNext(DateTime.now());
    expect(await repo.markFailed(failed.id, expectedAttempt: 1), isTrue);
    expect((await repo.getById(failed.id))!.payload, isNull);
  });

  test(
    'terminal transition cannot overwrite a concurrent cancellation',
    () async {
      final job = await repo.enqueue(
        type: JobTypes.aiGenerate,
        subjectId: 'cancel-race',
        payload: '{}',
      );
      await repo.claimNext(DateTime.now());
      expect(await repo.cancel(job.id), isTrue);

      expect(await repo.markSucceeded(job.id, expectedAttempt: 1), isFalse);
      expect(
        await repo.markRetrying(
          job.id,
          expectedAttempt: 1,
          nextRetryAt: DateTime.now(),
          errorCode: null,
          errorMessage: null,
        ),
        isFalse,
      );
      expect(await repo.markFailed(job.id, expectedAttempt: 1), isFalse);
      expect((await repo.getById(job.id))!.status, JobStatus.canceled);
    },
  );

  test('stale attempt transitions cannot overwrite a retry claim', () async {
    final job = await repo.enqueue(
      type: JobTypes.aiGenerate,
      subjectId: 'stale-attempt',
      maxAttempts: 2,
    );
    final firstAttempt = (await repo.claimNext(DateTime.now()))!;
    expect(firstAttempt.attempts, 1);
    expect(
      await repo.markRetrying(
        job.id,
        expectedAttempt: firstAttempt.attempts,
        nextRetryAt: DateTime.now(),
        errorCode: 'timeout',
        errorMessage: 'Timed out.',
      ),
      isTrue,
    );

    final secondAttempt = (await repo.claimNext(DateTime.now()))!;
    expect(secondAttempt.attempts, 2);
    expect(
      await repo.markSucceeded(job.id, expectedAttempt: firstAttempt.attempts),
      isFalse,
    );
    expect(
      await repo.markFailed(job.id, expectedAttempt: firstAttempt.attempts),
      isFalse,
    );
    expect(
      await repo.markRetrying(
        job.id,
        expectedAttempt: firstAttempt.attempts,
        nextRetryAt: DateTime.now(),
        errorCode: 'late',
        errorMessage: 'Stale attempt.',
      ),
      isFalse,
    );
    expect(
      await repo.markSucceeded(job.id, expectedAttempt: secondAttempt.attempts),
      isTrue,
    );
  });

  test(
    'resetStale rolls orphaned running rows back to queued after grace',
    () async {
      final now = DateTime.now();
      await database
          .into(database.jobs)
          .insert(
            appdb.JobsCompanion.insert(
              id: 'stale',
              type: 't',
              status: JobStatus.running.name,
              subjectId: 's',
              startedAt: Value(now.subtract(const Duration(minutes: 5))),
              attempts: const Value(1),
              // Budget remains, so recovery re-queues (exhausted rows fail).
              maxAttempts: const Value(3),
              createdAt: now.subtract(const Duration(minutes: 6)),
            ),
          );
      await database
          .into(database.jobs)
          .insert(
            appdb.JobsCompanion.insert(
              id: 'fresh',
              type: 't',
              status: JobStatus.running.name,
              subjectId: 's2',
              startedAt: Value(now.subtract(const Duration(seconds: 30))),
              attempts: const Value(1),
              maxAttempts: const Value(1),
              createdAt: now,
            ),
          );

      final reset = await repo.resetStale(now);
      expect(reset, 1);
      expect((await repo.getById('stale'))!.status, JobStatus.queued);
      expect((await repo.getById('fresh'))!.status, JobStatus.running);
    },
  );

  test('resetRunningAtStartup recovers fresh previous-process jobs', () async {
    final job = await repo.enqueue(
      type: 't',
      subjectId: 'startup',
      maxAttempts: 3,
    );
    await repo.claimNext(DateTime.now());

    final reset = await repo.resetRunningAtStartup(DateTime.now());
    expect(reset, 1);
    expect((await repo.getById(job.id))!.status, JobStatus.queued);
  });

  test(
    'A2-006: recovery fails exhausted running rows instead of re-queuing',
    () async {
      final job = await repo.enqueue(type: 't', subjectId: 'poison');
      await repo.claimNext(DateTime.now()); // attempts 1 of max 1

      final reset = await repo.resetRunningAtStartup(DateTime.now());
      expect(reset, 0);
      final row = (await repo.getById(job.id))!;
      expect(row.status, JobStatus.failed);
      expect(row.errorCode, 'interrupted');
      expect(await repo.claimNext(DateTime.now()), isNull);
    },
  );

  test(
    'A2-006: resetStale fails exhausted rows; claimNext skips exhausted',
    () async {
      final now = DateTime.now();
      await database
          .into(database.jobs)
          .insert(
            appdb.JobsCompanion.insert(
              id: 'old',
              type: 't',
              status: JobStatus.running.name,
              subjectId: 's',
              startedAt: Value(now.subtract(const Duration(minutes: 5))),
              attempts: const Value(2),
              maxAttempts: const Value(2),
              createdAt: now.subtract(const Duration(minutes: 6)),
            ),
          );
      await database
          .into(database.jobs)
          .insert(
            appdb.JobsCompanion.insert(
              id: 'queued-exhausted',
              type: 't',
              status: JobStatus.queued.name,
              subjectId: 's2',
              attempts: const Value(3),
              maxAttempts: const Value(3),
              createdAt: now,
            ),
          );

      expect(await repo.resetStale(now), 0);
      expect((await repo.getById('old'))!.status, JobStatus.failed);
      expect(await repo.claimNext(now), isNull);
      expect(
        (await repo.getById('queued-exhausted'))!.status,
        JobStatus.failed,
      );
    },
  );

  test(
    'A2-005: nextWakeAt reports the earliest retry or stale deadline',
    () async {
      expect(await repo.nextWakeAt(), isNull);
      final job = await repo.enqueue(type: 't', subjectId: 'r', maxAttempts: 3);
      await repo.claimNext(DateTime.now());
      final due = DateTime.now().add(const Duration(seconds: 30));
      await repo.markRetrying(
        job.id,
        expectedAttempt: 1,
        nextRetryAt: due,
        errorCode: 'network',
        errorMessage: 'x',
      );
      final wake = (await repo.nextWakeAt())!;
      expect(wake.difference(due).inSeconds.abs(), lessThanOrEqualTo(1));
    },
  );
}
