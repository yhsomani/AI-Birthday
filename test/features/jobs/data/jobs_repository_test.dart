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

  test('enqueue dedupes an active job and returns the original row', () async {
    final first = await repo.enqueue(
      type: JobTypes.aiGenerate,
      subjectId: 'b1',
      maxAttempts: 3,
    );
    final again = await repo.enqueue(
      type: JobTypes.aiGenerate,
      subjectId: 'b1',
      maxAttempts: 5,
    );

    expect(again.id, first.id);
    expect(again.maxAttempts, 3); // the original job wins, not the new request

    // A different subject is a different job.
    final other = await repo.enqueue(type: JobTypes.aiGenerate, subjectId: 'b2');
    expect(other.id, isNot(first.id));

    // A terminal job frees the slot: a new generation can be queued.
    await repo.markSucceeded(first.id, resultRef: 'b1');
    final fresh = await repo.enqueue(
      type: JobTypes.aiGenerate,
      subjectId: 'b1',
      maxAttempts: 2,
    );
    expect(fresh.id, isNot(first.id));
    expect(fresh.id, startsWith('${JobTypes.aiGenerate}_b1_'));
  });

  test('claimNext claims the oldest due job first and returns running state',
      () async {
    final now = DateTime.now();
    await database.into(database.jobs).insert(
      appdb.JobsCompanion.insert(
        id: 'old',
        type: 't',
        status: JobStatus.queued.name,
        subjectId: 'a',
        createdAt: now.subtract(const Duration(seconds: 5)),
        maxAttempts: const Value(1),
      ),
    );
    await database.into(database.jobs).insert(
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
    expect(first.startedAt!.difference(now).inSeconds.abs(), lessThanOrEqualTo(1));

    final second = await repo.claimNext(now);
    expect(second!.id, 'new');
    expect(await repo.claimNext(now), isNull); // nothing else due
  });

  test('markRetrying defers the retry and records the failure', () async {
    final job = await repo.enqueue(type: 't', subjectId: 's', maxAttempts: 3);
    final nextRetryAt = DateTime.now().add(const Duration(seconds: 4));

    await repo.markRetrying(
      job.id,
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

  test('markSucceeded prunes older terminal rows (bounded retention)',
      () async {
    final j1 = await repo.enqueue(type: JobTypes.aiGenerate, subjectId: 'b1');
    await repo.markSucceeded(j1.id, resultRef: 'r1');

    final j2 = await repo.enqueue(type: JobTypes.aiGenerate, subjectId: 'b1');
    await repo.markSucceeded(j2.id, resultRef: 'r2');

    expect(await repo.getById(j1.id), isNull); // pruned
    expect((await repo.getById(j2.id))!.status, JobStatus.succeeded);
    expect(await repo.latestJob(JobTypes.aiGenerate, 'b1'), isNotNull);
    expect(await repo.latestJob(JobTypes.aiGenerate, 'other'), isNull);
  });

  test('cancel flips active jobs to canceled and never touches terminal rows',
      () async {
    final job = await repo.enqueue(type: 't', subjectId: 's');
    final claimed = await repo.claimNext(DateTime.now());
    await repo.cancel(claimed!.id);

    expect((await repo.getById(job.id))!.status, JobStatus.canceled);
    expect(await repo.isCanceled(job.id), isTrue);

    final done = await repo.enqueue(type: 't', subjectId: 's2');
    await repo.markSucceeded(done.id);
    await repo.cancel(done.id); // no-op

    expect((await repo.getById(done.id))!.status, JobStatus.succeeded);
  });

  test('resetStale rolls orphaned running rows back to queued after grace',
      () async {
    final now = DateTime.now();
    await database.into(database.jobs).insert(
      appdb.JobsCompanion.insert(
        id: 'stale',
        type: 't',
        status: JobStatus.running.name,
        subjectId: 's',
        startedAt: Value(now.subtract(const Duration(minutes: 5))),
        attempts: const Value(1),
        maxAttempts: const Value(1),
        createdAt: now.subtract(const Duration(minutes: 6)),
      ),
    );
    await database.into(database.jobs).insert(
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
  });
}