/// Drift-backed repository for the durable background-job queue.
///
/// Single-process guarantee: the worker runs in the app's own isolate, so a
/// `running` row can only exist while the process is alive — `resetStale`
/// (crash recovery) rolls any orphaned `running` row back to `queued` at
/// boot. `claimNext` performs an atomic select-then-claim inside one
/// transaction.
library;

import 'package:drift/drift.dart' as drift;

import '../../../core/database/app_database.dart' as db;
import '../domain/job.dart';

abstract interface class JobsRepository {
  /// The active (queued/running/retrying) job of [type] for [subjectId], if
  /// any. Used for enqueue dedupe: an in-flight job is never duplicated.
  Future<JobRecord?> activeJob(String type, String subjectId);

  /// The most recent job of [type] for [subjectId] in any state.
  Future<JobRecord?> latestJob(String type, String subjectId);

  /// Creates a job, or returns an active job with identical inputs (dedupe).
  /// An active job with different inputs is rejected rather than silently
  /// dropping the newer request.
  Future<JobRecord> enqueue({
    required String type,
    required String subjectId,
    String? payload,
    int maxAttempts = 1,
  });

  /// Atomically claims the oldest due job: sets it `running`, increments
  /// `attempts`, stamps `startedAt`. Returns null when nothing is due.
  Future<JobRecord?> claimNext(DateTime now);

  Future<JobRecord?> getById(String id);

  /// Whether the job was canceled while a handler was in flight (so the
  /// worker can discard the side effect).
  Future<bool> isCanceled(String id);

  Future<bool> markRetrying(
    String id, {
    required int expectedAttempt,
    required DateTime nextRetryAt,
    required String? errorCode,
    required String? errorMessage,
  });

  Future<bool> markSucceeded(
    String id, {
    required int expectedAttempt,
    String? resultRef,
  });

  Future<bool> markFailed(
    String id, {
    required int expectedAttempt,
    String? errorCode,
    String? errorMessage,
  });

  Future<bool> cancel(String id);

  /// Earliest moment the worker must wake without any external notification:
  /// the soonest deferred `nextRetryAt` of a queued/retrying job, or the point
  /// a `running` row becomes stale. Null when nothing is scheduled.
  Future<DateTime?> nextWakeAt({
    Duration staleGrace = const Duration(minutes: 2),
  });

  /// Crash recovery: rolls jobs left `running` longer than [grace] back to
  /// `queued` (they never completed). Rows that already used their whole
  /// attempt budget are marked `failed` (`interrupted`) instead, so a job that
  /// kills the process cannot crash-loop. Returns the number re-queued.
  Future<int> resetStale(
    DateTime now, {
    Duration grace = const Duration(minutes: 2),
  });

  /// At process start, no local handler from the previous isolate can still
  /// be running. Recover all `running` rows without making fresh work race.
  /// Exhausted rows are failed instead of re-queued (see [resetStale]).
  Future<int> resetRunningAtStartup(DateTime now);
}

/// Drift/SQLite implementation of [JobsRepository].
class DriftJobsRepository implements JobsRepository {
  DriftJobsRepository(this._database);

  final db.AppDatabase _database;

  @override
  Future<JobRecord?> activeJob(String type, String subjectId) async {
    final row =
        await (_database.select(_database.jobs)
              ..where(
                (j) =>
                    j.type.equals(type) &
                    j.subjectId.equals(subjectId) &
                    j.status.isIn([
                      JobStatus.queued.name,
                      JobStatus.running.name,
                      JobStatus.retrying.name,
                    ]),
              )
              ..orderBy([(j) => drift.OrderingTerm.asc(j.createdAt)])
              ..limit(1))
            .getSingleOrNull();
    return row == null ? null : _fromRow(row);
  }

  @override
  Future<JobRecord?> latestJob(String type, String subjectId) async {
    final row =
        await (_database.select(_database.jobs)
              ..where(
                (j) => j.type.equals(type) & j.subjectId.equals(subjectId),
              )
              ..orderBy([(j) => drift.OrderingTerm.desc(j.createdAt)])
              ..limit(1))
            .getSingleOrNull();
    return row == null ? null : _fromRow(row);
  }

  @override
  Future<JobRecord> enqueue({
    required String type,
    required String subjectId,
    String? payload,
    int maxAttempts = 1,
  }) async {
    return _database.transaction(() async {
      final existing = await activeJob(type, subjectId);
      if (existing != null) {
        if (existing.payload != payload) {
          throw StateError(
            'An active $type job for $subjectId already has different inputs.',
          );
        }
        return existing;
      }

      final now = DateTime.now();
      final id = '${type}_${subjectId}_${now.microsecondsSinceEpoch}';
      await _database
          .into(_database.jobs)
          .insert(
            db.JobsCompanion.insert(
              id: id,
              type: type,
              status: JobStatus.queued.name,
              subjectId: subjectId,
              payload: drift.Value(payload),
              attempts: const drift.Value(0),
              maxAttempts: drift.Value(maxAttempts),
              createdAt: now,
            ),
          );
      return (await getById(id))!;
    });
  }

  @override
  Future<JobRecord?> claimNext(DateTime now) {
    return _database.transaction(() async {
      // Never run a job past its attempt budget (recovered/restored rows).
      final exhausted =
          await (_database.select(_database.jobs)..where(
                (j) =>
                    j.status.isIn([
                      JobStatus.queued.name,
                      JobStatus.retrying.name,
                    ]) &
                    j.attempts.isBiggerOrEqual(j.maxAttempts),
              ))
              .get();
      for (final row in exhausted) {
        await (_database.update(
          _database.jobs,
        )..where((j) => j.id.equals(row.id))).write(
          _interruptedCompanion(
            now,
            clearPayload: row.type == JobTypes.aiGenerate,
          ),
        );
      }

      final query = _database.select(_database.jobs)
        ..where(
          (j) =>
              j.status.isIn([JobStatus.queued.name, JobStatus.retrying.name]) &
              (j.nextRetryAt.isNull() |
                  j.nextRetryAt.isSmallerOrEqualValue(now)),
        )
        ..orderBy([(j) => drift.OrderingTerm.asc(j.createdAt)])
        ..limit(1);
      final row = await query.getSingleOrNull();
      if (row == null) return null;

      await (_database.update(
        _database.jobs,
      )..where((j) => j.id.equals(row.id))).write(
        db.JobsCompanion(
          status: drift.Value(JobStatus.running.name),
          attempts: drift.Value(row.attempts + 1),
          startedAt: drift.Value(now),
          errorCode: const drift.Value(null),
          errorMessage: const drift.Value(null),
          nextRetryAt: const drift.Value(null),
        ),
      );
      return _fromRow(
        row.copyWith(
          status: JobStatus.running.name,
          attempts: row.attempts + 1,
          startedAt: drift.Value(now),
        ),
      );
    });
  }

  @override
  Future<JobRecord?> getById(String id) async {
    final row = await (_database.select(
      _database.jobs,
    )..where((j) => j.id.equals(id))).getSingleOrNull();
    return row == null ? null : _fromRow(row);
  }

  @override
  Future<bool> isCanceled(String id) async {
    final row = await (_database.select(
      _database.jobs,
    )..where((j) => j.id.equals(id))).getSingleOrNull();
    return row?.status == JobStatus.canceled.name;
  }

  @override
  Future<bool> markRetrying(
    String id, {
    required int expectedAttempt,
    required DateTime nextRetryAt,
    required String? errorCode,
    required String? errorMessage,
  }) async {
    final changed =
        await (_database.update(_database.jobs)..where(
              (j) =>
                  j.id.equals(id) &
                  j.status.equals(JobStatus.running.name) &
                  j.attempts.equals(expectedAttempt),
            ))
            .write(
              db.JobsCompanion(
                status: drift.Value(JobStatus.retrying.name),
                startedAt: const drift.Value(null),
                nextRetryAt: drift.Value(nextRetryAt),
                errorCode: drift.Value(errorCode),
                errorMessage: drift.Value(errorMessage),
              ),
            );
    return changed == 1;
  }

  @override
  Future<bool> markSucceeded(
    String id, {
    required int expectedAttempt,
    String? resultRef,
  }) {
    return _database.transaction(() async {
      final row = await (_database.select(
        _database.jobs,
      )..where((j) => j.id.equals(id))).getSingleOrNull();
      if (row == null ||
          row.status != JobStatus.running.name ||
          row.attempts != expectedAttempt) {
        return false;
      }

      final changed =
          await (_database.update(_database.jobs)..where(
                (j) =>
                   j.id.equals(id) &
                   j.status.equals(JobStatus.running.name) &
                   j.attempts.equals(expectedAttempt),
              ))
              .write(
                db.JobsCompanion(
                  status: drift.Value(JobStatus.succeeded.name),
                  finishedAt: drift.Value(DateTime.now()),
                  resultRef: drift.Value(resultRef),
                  payload: drift.Value(
                    row.type == JobTypes.aiGenerate ? null : row.payload,
                  ),
                  errorCode: const drift.Value(null),
                  errorMessage: const drift.Value(null),
                  nextRetryAt: const drift.Value(null),
                ),
              );
      if (changed != 1) return false;

      // Retention: keep only the active job + latest terminal row per
      // (type, subject) so the queue stays bounded on long-lived birthdays.
      await (_database.delete(_database.jobs)..where(
            (j) =>
                j.type.equals(row.type) &
                j.subjectId.equals(row.subjectId) &
                j.id.equals(row.id).not() &
                j.status.isIn([
                  JobStatus.succeeded.name,
                  JobStatus.failed.name,
                  JobStatus.canceled.name,
                ]),
          ))
          .go();
      return true;
    });
  }

  @override
  Future<bool> markFailed(
    String id, {
    required int expectedAttempt,
    String? errorCode,
    String? errorMessage,
  }) async {
    final row = await getById(id);
    if (row == null ||
        row.status != JobStatus.running ||
        row.attempts != expectedAttempt) {
      return false;
    }
    final changed =
        await (_database.update(_database.jobs)..where(
              (j) =>
                  j.id.equals(id) &
                  j.status.equals(JobStatus.running.name) &
                  j.attempts.equals(expectedAttempt),
            ))
            .write(
              db.JobsCompanion(
                status: drift.Value(JobStatus.failed.name),
                finishedAt: drift.Value(DateTime.now()),
                payload: drift.Value(
                  row.type == JobTypes.aiGenerate ? null : row.payload,
                ),
                errorCode: drift.Value(errorCode),
                errorMessage: drift.Value(errorMessage),
                nextRetryAt: const drift.Value(null),
              ),
            );
    return changed == 1;
  }

  @override
  Future<bool> cancel(String id) {
    return _database.transaction(() async {
      final row = await getById(id);
      if (row == null ||
          ![
            JobStatus.queued,
            JobStatus.running,
            JobStatus.retrying,
          ].contains(row.status)) {
        return false;
      }
      final changed =
          await (_database.update(_database.jobs)..where(
                (j) =>
                    j.id.equals(id) &
                    j.status.isIn([
                      JobStatus.queued.name,
                      JobStatus.running.name,
                      JobStatus.retrying.name,
                    ]),
              ))
              .write(
                db.JobsCompanion(
                  status: drift.Value(JobStatus.canceled.name),
                  finishedAt: drift.Value(DateTime.now()),
                  payload: drift.Value(
                    row.type == JobTypes.aiGenerate ? null : row.payload,
                  ),
                ),
              );
      return changed == 1;
    });
  }

  @override
  Future<DateTime?> nextWakeAt({
    Duration staleGrace = const Duration(minutes: 2),
  }) async {
    final rows =
        await (_database.select(_database.jobs)..where(
              (j) => j.status.isIn([
                JobStatus.queued.name,
                JobStatus.retrying.name,
                JobStatus.running.name,
              ]),
            ))
            .get();
    DateTime? earliest;
    void consider(DateTime? candidate) {
      if (candidate == null) return;
      if (earliest == null || candidate.isBefore(earliest!)) {
        earliest = candidate;
      }
    }

    for (final row in rows) {
      if (row.status == JobStatus.running.name) {
        final startedAt = row.startedAt;
        if (startedAt != null) consider(startedAt.add(staleGrace));
      } else {
        consider(row.nextRetryAt);
      }
    }
    return earliest;
  }

  @override
  Future<int> resetStale(
    DateTime now, {
    Duration grace = const Duration(minutes: 2),
  }) {
    final cutoff = now.subtract(grace);
    return _recoverRunning(
      now,
      where: (j) =>
          j.status.equals(JobStatus.running.name) &
          j.startedAt.isSmallerThanValue(cutoff),
      deferTo: null,
    );
  }

  @override
  Future<int> resetRunningAtStartup(DateTime now) {
    return _recoverRunning(
      now,
      where: (j) => j.status.equals(JobStatus.running.name),
      deferTo: now,
    );
  }

  Future<int> _recoverRunning(
    DateTime now, {
    required drift.Expression<bool> Function(db.$JobsTable) where,
    required DateTime? deferTo,
  }) {
    return _database.transaction(() async {
      final rows = await (_database.select(_database.jobs)..where(where)).get();
      var requeued = 0;
      for (final row in rows) {
        final target = _database.update(_database.jobs)
          ..where((j) => j.id.equals(row.id));
        if (row.attempts >= row.maxAttempts) {
          await target.write(
            _interruptedCompanion(
              now,
              clearPayload: row.type == JobTypes.aiGenerate,
            ),
          );
        } else {
          await target.write(
            db.JobsCompanion(
              status: drift.Value(JobStatus.queued.name),
              startedAt: const drift.Value(null),
              nextRetryAt: deferTo == null
                  ? const drift.Value.absent()
                  : drift.Value(deferTo),
            ),
          );
          requeued++;
        }
      }
      return requeued;
    });
  }

  static db.JobsCompanion _interruptedCompanion(
    DateTime now, {
    bool clearPayload = false,
  }) {
    return db.JobsCompanion(
      status: drift.Value(JobStatus.failed.name),
      startedAt: const drift.Value(null),
      finishedAt: drift.Value(now),
      payload: clearPayload
          ? const drift.Value(null)
          : const drift.Value.absent(),
      nextRetryAt: const drift.Value(null),
      errorCode: const drift.Value('interrupted'),
      errorMessage: const drift.Value(
        'This task was interrupted and used all of its attempts. Please try again.',
      ),
    );
  }

  static JobRecord _fromRow(db.Job row) {
    return JobRecord(
      id: row.id,
      type: row.type,
      status: JobStatus.fromString(row.status),
      subjectId: row.subjectId,
      payload: row.payload,
      attempts: row.attempts,
      maxAttempts: row.maxAttempts,
      errorCode: row.errorCode,
      errorMessage: row.errorMessage,
      resultRef: row.resultRef,
      createdAt: row.createdAt.toUtc(),
      startedAt: row.startedAt?.toUtc(),
      finishedAt: row.finishedAt?.toUtc(),
      nextRetryAt: row.nextRetryAt?.toUtc(),
    );
  }
}
