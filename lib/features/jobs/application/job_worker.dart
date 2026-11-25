/// Durable background-job worker (background-jobs audit).
///
/// Event-driven, not polled: `enqueue` wakes the worker, the worker drains
/// every due job once, and a delayed retry is scheduled with a single timer.
/// One job runs at a time (single user, single device) so no user or provider
/// can saturate the worker. Jobs are durable in SQLite: a process death mid-
/// run leaves the row `running`, and `resetStale` on the next drain rolls it
/// back to `queued` so it re-runs. Handlers are idempotent by construction —
/// re-running a completed or partially completed job produces no duplicate
/// side effects.
library;

import 'dart:async';
import 'dart:math';

import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';

import '../domain/job.dart';
import '../data/jobs_repository.dart';

/// Outcome of one handler run. Handlers persist their own effect; the worker
/// only translates outcomes into job-state transitions and the retry policy.
sealed class JobOutcome {
  const JobOutcome();
}

/// The handler completed and its effect is durable (`resultRef` points at
/// it, e.g. draft id or last-sync timestamp).
class JobSucceeded extends JobOutcome {
  const JobSucceeded([this.resultRef]);
  final String? resultRef;
}

/// The handler finished but deliberately did not apply its result (stale
/// draft, canceled, cycle changed, lost a race). This is NOT a failure: the job
/// is recorded as succeeded with `resultRef = dropped:<reason>` so callers can
/// report "result not applied" neutrally instead of claiming success.
class JobSkipped extends JobOutcome {
  const JobSkipped(this.reason);
  final String reason;
}

/// The handler failed. `retryable` decides between `retrying` (when attempts
/// remain) and final `failed`. `code`/`message` are user-safe.
class JobFailed extends JobOutcome {
  const JobFailed({required this.retryable, this.code, this.message});
  final bool retryable;
  final String? code;
  final String? message;
}

/// Runs one job to completion, including persisting its effect.
///
/// Handlers must be idempotent: the worker may re-run them after a crash or
/// after duplicate-delivery recovery, and a completion check must make that
/// safe (upserts, stable keys, stale-guards).
abstract interface class JobHandler {
  Future<JobOutcome> run(JobRecord job);
}

/// Processes durable jobs from [JobsRepository] through [JobHandler]s.
class JobWorker {
  JobWorker({
    required this._repository,
    required this._handlers,
    AppLogger? logger,
    this.backoffStep = const Duration(seconds: 2),
    this.handlerTimeout = const Duration(minutes: 3),
    this.errorRearmDelay = const Duration(seconds: 30),
    Random? random,
  }) : _logger = logger ?? ConsoleAppLogger(),
       _random = random ?? Random();

  final JobsRepository _repository;
  final Map<String, JobHandler> _handlers;
  final AppLogger _logger;
  final Random _random;

  /// Base delay for the first retry; doubled per retry (2s, 4s, …).
  final Duration backoffStep;

  /// Upper bound for one handler run. A handler that never completes becomes a
  /// retryable timeout instead of wedging the single serial drain.
  final Duration handlerTimeout;

  /// Delay before the worker re-arms itself after an unexpected repository
  /// error in the drain loop.
  final Duration errorRearmDelay;

  final StreamController<void> _wake = StreamController<void>.broadcast();
  StreamSubscription<void>? _wakeSubscription;
  Timer? _retryTimer;
  bool _draining = false;
  // Set when a wake arrives mid-drain so another pass runs afterwards; the
  // claim query that already ran may have missed the newly enqueued row.
  bool _dirty = false;
  bool _disposed = false;
  bool _startupRecoveryDone = false;

  /// Emits every job-state transition the worker performs (queued/running/
  /// retrying/succeeded/failed/canceled). Widgets subscribe to this instead
  /// of watching the jobs table directly: it is a plain in-process broadcast
  /// stream (no drift query timers), and it is inherently truthful because
  /// only the worker transitions jobs.
  final StreamController<JobRecord> _events =
      StreamController<JobRecord>.broadcast();

  /// Stream of job transitions, filtered by the listener.
  Stream<JobRecord> get events => _events.stream;

  /// Starts the worker: recovers stale jobs and processes everything due.
  void start() {
    _wakeSubscription = _wake.stream.listen((_) => unawaited(_drain()));
    unawaited(_drain());
  }

  Future<void> stop() async {
    _disposed = true;
    _retryTimer?.cancel();
    _retryTimer = null;
    final sub = _wakeSubscription;
    _wakeSubscription = null;
    await sub?.cancel();
    await _wake.close();
    await _events.close();
  }

  void _emit(JobRecord job) {
    if (_disposed || _events.isClosed) return;
    _events.add(job);
  }

  /// Enqueues a durable job (deduping against an active one) and wakes the
  /// worker. Fast path for the UI — the job row is the acknowledgement.
  Future<JobRecord> enqueue({
    required String type,
    required String subjectId,
    String? payload,
    int maxAttempts = 1,
  }) async {
    final job = await _repository.enqueue(
      type: type,
      subjectId: subjectId,
      payload: payload,
      maxAttempts: maxAttempts,
    );
    _logger.info(
      'JobWorker',
      'Job enqueued',
      params: {'job': job.id, 'type': job.type, 'subject': job.subjectId},
    );
    _emit(job);
    notify();
    return job;
  }

  /// Wakes the worker to process due jobs (also used for delayed retries).
  void notify() {
    if (_disposed || _wake.isClosed) return;
    _wake.add(null);
  }

  Future<void> _drain() async {
    if (_disposed) return;
    if (_draining) {
      _dirty = true;
      return;
    }
    _draining = true;
    try {
      do {
        _dirty = false;
        try {
          await _drainPass();
          await _armNextWake();
        } catch (e, st) {
          _logger.error(
            'JobWorker',
            'Job drain failed; will retry',
            error: e,
            stackTrace: st,
          );
          _armTimer(errorRearmDelay);
          break;
        }
      } while (_dirty && !_disposed);
    } finally {
      _draining = false;
    }
  }

  Future<void> _drainPass() async {
    final now = DateTime.now();
    final startupRecovered = !_startupRecoveryDone
        ? await _repository.resetRunningAtStartup(now)
        : 0;
    _startupRecoveryDone = true;
    if (startupRecovered > 0) {
      _logger.warning(
        'JobWorker',
        'Process recovery: rolled orphaned running jobs back to queued',
        params: {'count': startupRecovered},
      );
    }
    final recovered = await _repository.resetStale(now);
    if (recovered > 0) {
      _logger.warning(
        'JobWorker',
        'Crash recovery: rolled stale running jobs back to queued',
        params: {'count': recovered},
      );
    }
    while (!_disposed) {
      final job = await _repository.claimNext(DateTime.now());
      if (job == null) break;
      await _process(job);
    }
  }

  /// Arms the single wake-up timer for the earliest deferred retry (or stale
  /// running row) so a `retrying` job still runs after a restart or when a
  /// later retry is scheduled sooner than an earlier one.
  Future<void> _armNextWake() async {
    if (_disposed) return;
    final at = await _repository.nextWakeAt();
    if (at == null) {
      _retryTimer?.cancel();
      _retryTimer = null;
      return;
    }
    final delay = at.difference(DateTime.now());
    _armTimer(delay < _minWake ? _minWake : delay);
  }

  static const Duration _minWake = Duration(seconds: 1);

  void _armTimer(Duration delay) {
    if (_disposed) return;
    _retryTimer?.cancel();
    _retryTimer = Timer(delay, notify);
  }

  Future<void> _process(JobRecord job) async {
    // The claimed record is already `running` (claimNext returns the state it
    // wrote to the DB), so listeners see the transition immediately.
    _emit(job);

    final handler = _handlers[job.type];
    if (handler == null) {
      _logger.error(
        'JobWorker',
        'No handler registered for job type',
        params: {'job': job.id, 'type': job.type},
      );
      await _commit(
        job,
        () => _repository.markFailed(
          job.id,
          expectedAttempt: job.attempts,
          errorCode: 'unknown',
          errorMessage: 'This job type is no longer supported.',
        ),
        job.copyWith(
          status: JobStatus.failed,
          errorCode: 'unknown',
          errorMessage: 'This job type is no longer supported.',
          finishedAt: DateTime.now(),
        ),
      );
      return;
    }

    JobOutcome outcome;
    try {
      outcome = await handler.run(job).timeout(handlerTimeout);
    } on TimeoutException {
      _logger.warning(
        'JobWorker',
        'Job handler timed out',
        params: {'job': job.id, 'timeoutMs': handlerTimeout.inMilliseconds},
      );
      outcome = job.type == JobTypes.aiGenerate
          ? const JobFailed(
              retryable: true,
              code: 'aiTimeout',
              message: 'AI request timed out. Please try again.',
            )
          : const JobFailed(
              retryable: true,
              code: 'network',
              message: 'The operation timed out. Please try again.',
            );
    } catch (e, st) {
      _logger.error(
        'JobWorker',
        'Job handler threw an unexpected error',
        error: e,
        stackTrace: st,
        params: {'job': job.id},
      );
      outcome = e is AppFailure
          ? JobFailed(
              retryable: e.isRetryable,
              code: e.code.name,
              message: e.userMessage,
            )
          : const JobFailed(
              retryable: false,
              code: 'unknown',
              message: 'An unexpected error occurred.',
            );
    }

    // A cancel that landed while the handler ran discards the result: the
    // row stays canceled and is never flipped to succeeded.
    final bool canceled;
    try {
      canceled = await _repository.isCanceled(job.id);
    } catch (e, st) {
      _failTransition(job, e, st);
      return;
    }
    if (canceled) {
      _logger.info(
        'JobWorker',
        'Job canceled while running; result discarded',
        params: {'job': job.id},
      );
      _emit(
        job.copyWith(status: JobStatus.canceled, finishedAt: DateTime.now()),
      );
      return;
    }

    switch (outcome) {
      case JobSucceeded(:final resultRef):
        await _succeed(job, resultRef);
      case JobSkipped(:final reason):
        _logger.info(
          'JobWorker',
          'Job result not applied',
          params: {'job': job.id, 'reason': reason},
        );
        await _succeed(job, '${JobRecord.droppedResultPrefix}$reason');
      case JobFailed(:final retryable, :final code, :final message):
        if (retryable && job.attempts < job.maxAttempts) {
          final delay = _backoff(job.attempts);
          final nextRetryAt = DateTime.now().add(delay);
          final recorded = await _commit(
            job,
            () => _repository.markRetrying(
              job.id,
              expectedAttempt: job.attempts,
              nextRetryAt: nextRetryAt,
              errorCode: code,
              errorMessage: message,
            ),
            job.copyWith(
              status: JobStatus.retrying,
              errorCode: code,
              errorMessage: message,
              nextRetryAt: nextRetryAt,
            ),
          );
          if (recorded) {
            _logger.warning(
              'JobWorker',
              'Job failed retryably; retry scheduled',
              params: {
                'job': job.id,
                'attempt': job.attempts,
                'max': job.maxAttempts,
                'code': code,
                'retryInMs': delay.inMilliseconds,
              },
            );
          }
        } else {
          final recorded = await _commit(
            job,
            () => _repository.markFailed(
              job.id,
              expectedAttempt: job.attempts,
              errorCode: code,
              errorMessage: message,
            ),
            job.copyWith(
              status: JobStatus.failed,
              errorCode: code,
              errorMessage: message,
              finishedAt: DateTime.now(),
            ),
          );
          if (recorded) {
            _logger.warning(
              'JobWorker',
              'Job failed (final)',
              params: {
                'job': job.id,
                'attempt': job.attempts,
                'max': job.maxAttempts,
                'code': code,
                'retryable': retryable,
              },
            );
          }
        }
    }
  }

  Future<void> _succeed(JobRecord job, String? resultRef) async {
    final recorded = await _commit(
      job,
      () => _repository.markSucceeded(
        job.id,
        expectedAttempt: job.attempts,
        resultRef: resultRef,
      ),
      job.copyWith(
        status: JobStatus.succeeded,
        resultRef: resultRef,
        finishedAt: DateTime.now(),
      ),
    );
    if (!recorded) return;
    _logger.info(
      'JobWorker',
      'Job succeeded',
      params: {
        'job': job.id,
        'type': job.type,
        'durationMs': DateTime.now()
            .difference(job.startedAt ?? job.createdAt)
            .inMilliseconds,
      },
    );
  }

  /// Persists one state transition (one retry on error) and then emits
  /// [event]. A persistent repository error must never escape the drain or
  /// leave listeners spinning: it is logged, surfaced as a failed event, and
  /// the row is left for stale-`running` recovery.
  Future<bool> _commit(
    JobRecord job,
    Future<bool> Function() write,
    JobRecord event,
  ) async {
    Object? error;
    StackTrace? stackTrace;
    for (var attempt = 0; attempt < 2; attempt++) {
      try {
        if (await write()) {
          _emit(event);
          return true;
        }
        final current = await _repository.getById(job.id);
        if (current != null) _emit(current);
        return false;
      } catch (e, st) {
        error = e;
        stackTrace = st;
      }
    }
    _failTransition(job, error!, stackTrace);
    return false;
  }

  void _failTransition(JobRecord job, Object error, StackTrace? stackTrace) {
    _logger.error(
      'JobWorker',
      'Could not record job transition',
      error: error,
      stackTrace: stackTrace,
      params: {'job': job.id},
    );
    _emit(
      job.copyWith(
        status: JobStatus.failed,
        errorCode: 'unknown',
        errorMessage: 'Could not record the result of this task. Try again.',
        finishedAt: DateTime.now(),
      ),
    );
    // The row stays `running`; the post-drain wake re-arms for its stale
    // recovery window.
  }

  /// 2^attempt seconds plus up to 500ms jitter, unless backoff is disabled
  /// (tests) in which case the retry is due immediately.
  Duration _backoff(int attempt) {
    if (backoffStep == Duration.zero) return Duration.zero;
    final base = backoffStep * (1 << (attempt - 1));
    return base + Duration(milliseconds: _random.nextInt(501));
  }
}
