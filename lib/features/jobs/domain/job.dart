/// Durable background-job domain model (background-jobs audit).
///
/// One [JobRecord] per durable operation. Status transitions are monotonic
/// from the perspective of the worker: `queued -> running -> (retrying) ->
/// succeeded | failed | canceled`. A job that failed retryably returns to
/// `retrying` with a deferred `nextRetryAt`; `attempts` counts every run.
library;

/// Job lifecycle states. Only states with product meaning exist (Phase 3):
/// "queued" is not "completed" — the UI must never claim success from a row
/// that is merely queued or running.
enum JobStatus {
  queued,
  running,
  retrying,
  succeeded,
  failed,
  canceled;

  static JobStatus fromString(String? value) {
    return JobStatus.values.firstWhere(
      (e) => e.name == value,
      orElse: () => JobStatus.queued,
    );
  }

  /// Whether the job is still eligible to run now or later.
  bool get isActive => this == JobStatus.queued || this == JobStatus.running;

  /// Whether the job has reached an end state and will not run again.
  bool get isTerminal => this == JobStatus.succeeded ||
      this == JobStatus.failed ||
      this == JobStatus.canceled;
}

/// Stable job-type identifiers (stored in the `type` column).
abstract final class JobTypes {
  static const String aiGenerate = 'ai_generate';
  static const String cloudSync = 'cloud_sync';
}

/// A durable background job.
class JobRecord {
  const JobRecord({
    required this.id,
    required this.type,
    required this.status,
    required this.subjectId,
    this.payload,
    this.attempts = 0,
    this.maxAttempts = 1,
    this.errorCode,
    this.errorMessage,
    this.resultRef,
    required this.createdAt,
    this.startedAt,
    this.finishedAt,
    this.nextRetryAt,
  });

  final String id;
  final String type;
  final JobStatus status;

  /// Scopes the job to its subject (birthday id for `ai_generate`, the
  /// signed-in account uid for `cloud_sync`). Single-user local app.
  final String subjectId;

  /// Non-secret JSON-encoded inputs the handler needs to re-run after a
  /// restart. Never contains API keys or ID tokens.
  final String? payload;

  /// Number of times the handler has run (first attempt + retries).
  final int attempts;

  /// Bounded retry ceiling; the worker never runs the handler more times
  /// than this.
  final int maxAttempts;

  final String? errorCode;
  final String? errorMessage;

  /// Durable effect of a successful job (draft id, last-sync timestamp).
  final String? resultRef;

  final DateTime createdAt;
  final DateTime? startedAt;
  final DateTime? finishedAt;

  /// Earliest time a retrying job may be claimed again.
  final DateTime? nextRetryAt;

  JobRecord copyWith({
    JobStatus? status,
    String? payload,
    int? attempts,
    int? maxAttempts,
    String? errorCode,
    String? errorMessage,
    String? resultRef,
    DateTime? startedAt,
    DateTime? finishedAt,
    DateTime? nextRetryAt,
    bool clearErrorFields = false,
  }) {
    return JobRecord(
      id: id,
      type: type,
      status: status ?? this.status,
      subjectId: subjectId,
      payload: payload ?? this.payload,
      attempts: attempts ?? this.attempts,
      maxAttempts: maxAttempts ?? this.maxAttempts,
      errorCode: clearErrorFields ? null : (errorCode ?? this.errorCode),
      errorMessage: clearErrorFields
          ? null
          : (errorMessage ?? this.errorMessage),
      resultRef: resultRef ?? this.resultRef,
      createdAt: createdAt,
      startedAt: startedAt ?? this.startedAt,
      finishedAt: finishedAt ?? this.finishedAt,
      nextRetryAt: nextRetryAt ?? this.nextRetryAt,
    );
  }
}