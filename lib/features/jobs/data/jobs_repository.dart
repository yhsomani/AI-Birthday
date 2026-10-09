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

  /// Creates a job, or returns the existing active one (dedupe). `maxAttempts`
  /// is the bounded retry ceiling. Returns the active job in both cases.
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

  Future<void> markRetrying(
    String id, {
    required DateTime nextRetryAt,
    required String? errorCode,
    required String? errorMessage,
  });

  Future<void> markSucceeded(String id, {String? resultRef});

  Future<void> markFailed(String id, {String? errorCode, String? errorMessage});

  Future<void> cancel(String id);

  /// Crash recovery: rolls jobs left `running` longer than [grace] back to
  /// `queued` (they never completed). Returns the number reset.
  Future<int> resetStale(
    DateTime now, {
    Duration grace = const Duration(minutes: 2),
  });
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
    final existing = await activeJob(type, subjectId);
    if (existing != null) return existing;

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
  }

  @override
  Future<JobRecord?> claimNext(DateTime now) {
    return _database.transaction(() async {
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
  Future<void> markRetrying(
    String id, {
    required DateTime nextRetryAt,
    required String? errorCode,
    required String? errorMessage,
  }) async {
    await (_database.update(
      _database.jobs,
    )..where((j) => j.id.equals(id))).write(
      db.JobsCompanion(
        status: drift.Value(JobStatus.retrying.name),
        startedAt: const drift.Value(null),
        nextRetryAt: drift.Value(nextRetryAt),
        errorCode: drift.Value(errorCode),
        errorMessage: drift.Value(errorMessage),
      ),
    );
  }

  @override
  Future<void> markSucceeded(String id, {String? resultRef}) async {
    await _database.transaction(() async {
      final row = await (_database.select(
        _database.jobs,
      )..where((j) => j.id.equals(id))).getSingleOrNull();
      if (row == null) return;

      await (_database.update(
        _database.jobs,
      )..where((j) => j.id.equals(id))).write(
        db.JobsCompanion(
          status: drift.Value(JobStatus.succeeded.name),
          finishedAt: drift.Value(DateTime.now()),
          resultRef: drift.Value(resultRef),
          errorCode: const drift.Value(null),
          errorMessage: const drift.Value(null),
          nextRetryAt: const drift.Value(null),
        ),
      );

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
    });
  }

  @override
  Future<void> markFailed(
    String id, {
    String? errorCode,
    String? errorMessage,
  }) async {
    await (_database.update(
      _database.jobs,
    )..where((j) => j.id.equals(id))).write(
      db.JobsCompanion(
        status: drift.Value(JobStatus.failed.name),
        finishedAt: drift.Value(DateTime.now()),
        errorCode: drift.Value(errorCode),
        errorMessage: drift.Value(errorMessage),
        nextRetryAt: const drift.Value(null),
      ),
    );
  }

  @override
  Future<void> cancel(String id) async {
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
          ),
        );
  }

  @override
  Future<int> resetStale(
    DateTime now, {
    Duration grace = const Duration(minutes: 2),
  }) async {
    final cutoff = now.subtract(grace);
    final staleJobIds =
        await (_database.select(_database.jobs)..where(
              (j) =>
                  j.status.equals(JobStatus.running.name) &
                  j.startedAt.isSmallerThanValue(cutoff),
            ))
            .get();
    if (staleJobIds.isEmpty) return 0;

    await (_database.update(_database.jobs)..where(
          (j) =>
              j.status.equals(JobStatus.running.name) &
              j.startedAt.isSmallerThanValue(cutoff),
        ))
        .write(
          db.JobsCompanion(
            status: drift.Value(JobStatus.queued.name),
            startedAt: const drift.Value(null),
          ),
        );
    return staleJobIds.length;
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
