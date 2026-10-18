/// Native SMS Gateway communicating with Android platform over MethodChannel.
library;

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

/// Representation of an automated birthday SMS job scheduled on Android.
class ScheduledSmsJob {
  const ScheduledSmsJob({
    required this.jobId,
    required this.personId,
    required this.birthdayId,
    required this.phoneNumber,
    required this.message,
    required this.timestampMs,
    required this.isApproved,
    this.status = 'scheduled',
    this.failureReason,
  });

  final String jobId;
  final String personId;
  final String birthdayId;
  final String phoneNumber;
  final String message;
  final int timestampMs;
  final bool isApproved;
  final String status;
  final String? failureReason;

  Map<String, dynamic> toMap() => {
    'jobId': jobId,
    'personId': personId,
    'birthdayId': birthdayId,
    'phoneNumber': phoneNumber,
    'message': message,
    'timestampMs': timestampMs,
    'isApproved': isApproved,
    'status': status,
    if (failureReason != null) 'failureReason': failureReason,
  };

  factory ScheduledSmsJob.fromMap(Map<String, dynamic> map) {
    return ScheduledSmsJob(
      jobId: map['jobId'] as String? ?? '',
      personId: map['personId'] as String? ?? '',
      birthdayId: map['birthdayId'] as String? ?? '',
      phoneNumber: map['phoneNumber'] as String? ?? '',
      message: map['message'] as String? ?? '',
      timestampMs: (map['timestampMs'] as num?)?.toInt() ?? 0,
      isApproved: map['isApproved'] as bool? ?? false,
      status: map['status'] as String? ?? 'scheduled',
      failureReason: map['failureReason'] as String?,
    );
  }
}

/// Abstract contract for interacting with native SMS automation capabilities.
abstract interface class NativeSmsGateway {
  /// Whether automatic direct SMS is supported by this build variant (i.e. personal build with SEND_SMS declared).
  Future<bool> isSupported();

  /// Whether SEND_SMS permission is currently granted by the user.
  Future<bool> hasSmsPermission();

  /// Requests SEND_SMS permission via system dialog in the foreground.
  Future<bool> requestSmsPermission();

  /// Schedules or updates an automated birthday SMS job with Android AlarmManager.
  Future<bool> scheduleSmsJob(ScheduledSmsJob job);

  /// Cancels an individual scheduled SMS job by [jobId].
  Future<bool> cancelSmsJob(String jobId);

  /// Cancels all scheduled automated SMS jobs.
  Future<bool> cancelAllSmsJobs();

  /// Retrieves the JSON array string of recent SMS execution and callback attempts.
  Future<String> getSmsAttemptsLog();
}

/// Concrete Android implementation communicating over MethodChannel.
class AndroidNativeSmsGateway implements NativeSmsGateway {
  const AndroidNativeSmsGateway({
    MethodChannel channel = const MethodChannel(
      'com.yashsomani.ai_birthday/sms_automation',
    ),
  }) : _channel = channel;

  final MethodChannel _channel;

  bool get _isLiveAndroid =>
      !kIsWeb &&
      defaultTargetPlatform == TargetPlatform.android &&
      WidgetsBinding.instance is WidgetsFlutterBinding;

  @override
  Future<bool> isSupported() async {
    if (!_isLiveAndroid) return false;
    try {
      final res = await _channel.invokeMethod<bool>('isSupported');
      return res ?? false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> hasSmsPermission() async {
    if (!_isLiveAndroid) return false;
    try {
      final res = await _channel.invokeMethod<bool>('hasSmsPermission');
      return res ?? false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> requestSmsPermission() async {
    if (!_isLiveAndroid) return false;
    try {
      final res = await _channel.invokeMethod<bool>('requestSmsPermission');
      return res ?? false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> scheduleSmsJob(ScheduledSmsJob job) async {
    if (!_isLiveAndroid) return true;
    try {
      final res = await _channel.invokeMethod<bool>('scheduleSmsJob', {
        'job': job.toMap(),
      });
      return res ?? false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> cancelSmsJob(String jobId) async {
    if (!_isLiveAndroid) return true;
    try {
      final res = await _channel.invokeMethod<bool>('cancelSmsJob', {
        'jobId': jobId,
      });
      return res ?? false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> cancelAllSmsJobs() async {
    if (!_isLiveAndroid) return true;
    try {
      final res = await _channel.invokeMethod<bool>('cancelAllSmsJobs');
      return res ?? false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<String> getSmsAttemptsLog() async {
    if (!_isLiveAndroid) return '[]';
    try {
      final res = await _channel.invokeMethod<String>('getSmsAttemptsLog');
      return res ?? '[]';
    } catch (_) {
      return '[]';
    }
  }
}
