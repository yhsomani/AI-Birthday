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
              startedAt: Value(now.subtract(const Duration(seconds: 30))),
              attempts: const Value(1),
              // Attempt budget remains, so recovery re-runs it (an exhausted
              // row is failed instead; see the poison-pill test).
              maxAttempts: const Value(3),
              createdAt: now.subtract(const Duration(seconds: 40)),
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
          // Budget remains; a restored row that already used it would fail.
          maxAttempts: const Value(3),
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

  group('resilience (A2-005..A2-008)', () {
    JobWorker resilientWorker(
      Map<String, JobHandler> handlers, {
      JobsRepository? repository,
      Duration handlerTimeout = const Duration(minutes: 3),
      Duration errorRearmDelay = const Duration(seconds: 30),
    }) {
      return JobWorker(
        repository: repository ?? repo,
        handlers: handlers,
        backoffStep: Duration.zero,
        handlerTimeout: handlerTimeout,
        errorRearmDelay: errorRearmDelay,
      );
    }

    test(
      'A2-005: a retrying row due later runs after start() without notify()',
      () async {
        final job = await repo.enqueue(
          type: JobTypes.aiGenerate,
          subjectId: 'b1',
          maxAttempts: 3,
        );
        await repo.claimNext(DateTime.now());
        await repo.markRetrying(
          job.id,
          expectedAttempt: 1,
          nextRetryAt: DateTime.now().add(const Duration(seconds: 2)),
          errorCode: 'network',
          errorMessage: 'later',
        );

        final handler = StubHandler((_, _) async => const JobSucceeded('ok'));
        final w = resilientWorker({JobTypes.aiGenerate: handler});
        final done = waitFor(w, JobStatus.succeeded);
        w.start();
        await done.timeout(const Duration(seconds: 8));

        expect(handler.calls, 1);
        expect((await repo.getById(job.id))!.status, JobStatus.succeeded);
        await w.stop();
      },
    );

    test(
      'A2-005: a job enqueued during an active drain is processed',
      () async {
        final gate = Completer<void>();
        final ran = <String>[];
        final handler = StubHandler((job, _) async {
          ran.add(job.subjectId);
          if (job.subjectId == 'first') await gate.future;
          return const JobSucceeded();
        });
        final w = resilientWorker({JobTypes.aiGenerate: handler});
        final running = waitFor(w, JobStatus.running);
        w.start();
        await w.enqueue(type: JobTypes.aiGenerate, subjectId: 'first');
        await running;

        // Enqueue + notify while the drain is busy inside the first handler.
        await w.enqueue(type: JobTypes.aiGenerate, subjectId: 'second');
        gate.complete();

        for (var i = 0; i < 100 && ran.length < 2; i++) {
          await Future<void>.delayed(const Duration(milliseconds: 20));
        }
        expect(ran, ['first', 'second']);
        await w.stop();
      },
    );

    test(
      'A2-006: poison pill (attempts == max) is failed on recovery, not re-run',
      () async {
        final now = DateTime.now();
        await database
            .into(database.jobs)
            .insert(
              appdb.JobsCompanion.insert(
                id: 'poison',
                type: JobTypes.aiGenerate,
                status: JobStatus.running.name,
                subjectId: 'b1',
                startedAt: Value(now.subtract(const Duration(seconds: 5))),
                attempts: const Value(3),
                maxAttempts: const Value(3),
                createdAt: now.subtract(const Duration(seconds: 40)),
              ),
            );
        final handler = StubHandler((_, _) async => const JobSucceeded());
        final w = resilientWorker({JobTypes.aiGenerate: handler});
        w.start();
        for (var i = 0; i < 50; i++) {
          await Future<void>.delayed(const Duration(milliseconds: 20));
          if ((await repo.getById('poison'))!.status == JobStatus.failed) break;
        }

        final row = (await repo.getById('poison'))!;
        expect(row.status, JobStatus.failed);
        expect(row.errorCode, 'interrupted');
        expect(handler.calls, 0);
        await w.stop();
      },
    );

    test(
      'A2-007: a never-completing handler times out retryably and the next job still runs',
      () async {
        final never = Completer<JobOutcome>();
        final handler = StubHandler((job, call) {
          if (job.subjectId == 'hang') return never.future;
          return Future.value(const JobSucceeded('fine'));
        });
        final w = resilientWorker({
          JobTypes.aiGenerate: handler,
        }, handlerTimeout: const Duration(milliseconds: 100));
        final retrying = waitFor(w, JobStatus.retrying);
        w.start();
        final hung = await w.enqueue(
          type: JobTypes.aiGenerate,
          subjectId: 'hang',
          maxAttempts: 2,
        );
        final event = await retrying.timeout(const Duration(seconds: 5));
        expect(event.errorCode, 'aiTimeout');

        final next = waitFor(w, JobStatus.succeeded);
        await w.enqueue(type: JobTypes.aiGenerate, subjectId: 'other');
        final done = await next.timeout(const Duration(seconds: 5));
        expect(done.subjectId, 'other');
        expect(
          (await repo.getById(hung.id))!.attempts,
          greaterThanOrEqualTo(1),
        );
        await w.stop();
      },
    );

    test(
      'A2-008: a repository error on markSucceeded does not escape and emits failed',
      () async {
        final flaky = _ThrowingJobsRepository(repo)..failMarkSucceeded = true;
        final handler = StubHandler((_, _) async => const JobSucceeded('ok'));
        final errors = <Object>[];
        late JobWorker w;
        final failed = Completer<JobRecord>();
        await runZonedGuarded(() async {
          w = resilientWorker(
            {JobTypes.aiGenerate: handler},
            repository: flaky,
            errorRearmDelay: const Duration(milliseconds: 50),
          );
          w.events.listen((e) {
            if (e.status == JobStatus.failed && !failed.isCompleted) {
              failed.complete(e);
            }
          });
          w.start();
          await w.enqueue(type: JobTypes.aiGenerate, subjectId: 'b1');
          final event = await failed.future.timeout(const Duration(seconds: 5));
          expect(event.errorCode, 'unknown');
        }, (e, _) => errors.add(e));

        expect(errors, isEmpty);
        await w.stop();
      },
    );

    test('A2-008: a claimNext error is logged and the drain re-arms', () async {
      final flaky = _ThrowingJobsRepository(repo)..claimFailures = 1;
      final handler = StubHandler((_, _) async => const JobSucceeded('ok'));
      final errors = <Object>[];
      late JobWorker w;
      final done = Completer<void>();
      await runZonedGuarded(() async {
        w = resilientWorker(
          {JobTypes.aiGenerate: handler},
          repository: flaky,
          errorRearmDelay: const Duration(milliseconds: 50),
        );
        w.events.listen((e) {
          if (e.status == JobStatus.succeeded && !done.isCompleted) {
            done.complete();
          }
        });
        await repo.enqueue(type: JobTypes.aiGenerate, subjectId: 'b1');
        w.start();
        await done.future.timeout(const Duration(seconds: 5));
      }, (e, _) => errors.add(e));

      expect(errors, isEmpty);
      expect(handler.calls, 1);
      await w.stop();
    });

    test(
      'JobSkipped is recorded as succeeded with a dropped: resultRef',
      () async {
        final handler = StubHandler((_, _) async => const JobSkipped('stale'));
        final w = worker({JobTypes.aiGenerate: handler});
        final done = waitFor(w, JobStatus.succeeded);
        w.start();
        final job = await w.enqueue(type: JobTypes.aiGenerate, subjectId: 'b1');
        final event = await done;

        expect(event.wasDropped, isTrue);
        final row = (await repo.getById(job.id))!;
        expect(row.status, JobStatus.succeeded);
        expect(row.resultRef, 'dropped:stale');
        expect(row.wasDropped, isTrue);
        await w.stop();
      },
    );
  });
}

/// Delegating repository whose transitions can be made to throw.
class _ThrowingJobsRepository implements JobsRepository {
  _ThrowingJobsRepository(this._inner);

  final JobsRepository _inner;
  bool failMarkSucceeded = false;
  int claimFailures = 0;

  @override
  Future<JobRecord?> claimNext(DateTime now) {
    if (claimFailures > 0) {
      claimFailures--;
      throw StateError('database is locked');
    }
    return _inner.claimNext(now);
  }

  @override
  Future<bool> markSucceeded(
    String id, {
    required int expectedAttempt,
    String? resultRef,
  }) {
    if (failMarkSucceeded) throw StateError('disk I/O error');
    return _inner.markSucceeded(
      id,
      expectedAttempt: expectedAttempt,
      resultRef: resultRef,
    );
  }

  @override
  Future<JobRecord?> activeJob(String type, String subjectId) =>
      _inner.activeJob(type, subjectId);
  @override
  Future<JobRecord?> latestJob(String type, String subjectId) =>
      _inner.latestJob(type, subjectId);
  @override
  Future<JobRecord> enqueue({
    required String type,
    required String subjectId,
    String? payload,
    int maxAttempts = 1,
  }) => _inner.enqueue(
    type: type,
    subjectId: subjectId,
    payload: payload,
    maxAttempts: maxAttempts,
  );
  @override
  Future<JobRecord?> getById(String id) => _inner.getById(id);
  @override
  Future<bool> isCanceled(String id) => _inner.isCanceled(id);
  @override
  Future<bool> markRetrying(
    String id, {
    required int expectedAttempt,
    required DateTime nextRetryAt,
    required String? errorCode,
    required String? errorMessage,
  }) => _inner.markRetrying(
    id,
    expectedAttempt: expectedAttempt,
    nextRetryAt: nextRetryAt,
    errorCode: errorCode,
    errorMessage: errorMessage,
  );
  @override
  Future<bool> markFailed(
    String id, {
    required int expectedAttempt,
    String? errorCode,
    String? errorMessage,
  }) => _inner.markFailed(
    id,
    expectedAttempt: expectedAttempt,
    errorCode: errorCode,
    errorMessage: errorMessage,
  );
  @override
  Future<bool> cancel(String id) => _inner.cancel(id);
  @override
  Future<DateTime?> nextWakeAt({
    Duration staleGrace = const Duration(minutes: 2),
  }) => _inner.nextWakeAt(staleGrace: staleGrace);
  @override
  Future<int> resetStale(
    DateTime now, {
    Duration grace = const Duration(minutes: 2),
  }) => _inner.resetStale(now, grace: grace);
  @override
  Future<int> resetRunningAtStartup(DateTime now) =>
      _inner.resetRunningAtStartup(now);
}
