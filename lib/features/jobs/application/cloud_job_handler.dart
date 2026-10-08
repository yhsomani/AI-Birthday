/// Durable `cloud_sync` job handler (backup and restore).
///
/// The handler performs the upload/download itself — that IS the effect of a
/// cloud job, and both operations are idempotent by nature (full-document
/// replace uploads; merge-style restore converges). Canceling while running
/// therefore stops further progress at the next status check; a later backup
/// re-runs the full idempotent upload and converges the cloud state.
///
/// Ownership guard: the job stores the signed-in account uid as `subjectId`
/// and the handler refuses to run for a different account (or a signed-out
/// session), so a queued job can never write to a stranger's backup.
library;

import 'dart:convert';

import 'package:ai_birthday/features/auth/domain/auth_state.dart';
import 'package:ai_birthday/features/sync/data/cloud_sync_service.dart';

import '../domain/job.dart';
import 'job_worker.dart';

/// Non-secret inputs for a cloud sync job. The ID token is never stored —
/// the handler resolves the live session at run time.
class CloudJobPayload {
  const CloudJobPayload({required this.operation});

  /// 'backup' or 'restore'.
  final String operation;

  static const String backup = 'backup';
  static const String restore = 'restore';

  Map<String, dynamic> toJson() => {'operation': operation};

  static CloudJobPayload fromJson(String? raw) {
    final json = raw == null ? const <String, dynamic>{} : jsonDecode(raw);
    return CloudJobPayload(operation: (json['operation'] as String?) ?? backup);
  }
}

class CloudSyncJobHandler implements JobHandler {
  CloudSyncJobHandler({
    required CloudSyncService service,
    required Future<AuthState> Function() authState,
  }) : _service = service,
       _authState = authState;

  final CloudSyncService _service;
  final Future<AuthState> Function() _authState;

  @override
  Future<JobOutcome> run(JobRecord job) async {
    final payload = CloudJobPayload.fromJson(job.payload);
    final auth = await _authState();

    String? uid;
    if (auth.isSignedIn && auth.identity != null) {
      // googleSubject is non-nullable, so uid can only be null when signed
      // out or identity is missing; firebaseUid is preferrred for scoping.
      uid = auth.identity!.firebaseUid ?? auth.identity!.googleSubject;
    }

    // Ownership guard: the job must run under the account it was enqueued
    // for. Never run against a different or missing session.
    if (uid == null || uid != job.subjectId) {
      return JobFailed(
        retryable: false,
        code: 'auth',
        message: 'Sign in to your cloud account and try again.',
      );
    }

    final result = payload.operation == CloudJobPayload.restore
        ? await _service.restore(auth)
        : await _service.sync(auth);

    if (result.success) {
      return JobSucceeded(result.timestamp.toIso8601String());
    }
    return JobFailed(
      retryable: result.retryable,
      code: result.errorCode ?? 'network',
      message: result.error ?? 'Cloud operation failed.',
    );
  }
}