import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../domain/reminder_kind.dart';
import '../domain/reminder_schedule.dart';
import 'notification_scheduler_gateway.dart';

/// Production Android notification scheduler gateway (SSOT §17).
///
/// Communicates over MethodChannel with native Android AlarmManager and
/// NotificationManager. Gracefully handles host platforms (tests, desktop)
/// without error.
class AndroidNotificationSchedulerGateway
    implements NotificationSchedulerGateway {
  const AndroidNotificationSchedulerGateway({
    MethodChannel channel = const MethodChannel(
      'com.yashsomani.ai_birthday/notifications',
    ),
  }) : _channel = channel;

  final MethodChannel _channel;

  bool get _isLiveAndroid =>
      !kIsWeb &&
      defaultTargetPlatform == TargetPlatform.android &&
      WidgetsBinding.instance is WidgetsFlutterBinding;

  @override
  Future<bool> hasPermission() async {
    if (!_isLiveAndroid) return true;
    try {
      final res = await _channel.invokeMethod<bool>('hasPermission');
      return res ?? false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> requestPermission() async {
    if (!_isLiveAndroid) return true;
    try {
      final res = await _channel.invokeMethod<bool>('requestPermission');
      return res ?? false;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<void> apply(ReminderPlan plan) async {
    if (!_isLiveAndroid) return;
    try {
      final triggers = plan.active.map((t) {
        final whenText = switch (t.kind) {
          ReminderKind.birthday => 'Today is their birthday! 🎉',
          ReminderKind.ready => 'Birthday is tomorrow!',
          ReminderKind.prepare => 'Birthday is in 2 days.',
          ReminderKind.approaching => 'Birthday is in 7 days.',
        };
        return {
          'id': '${t.personId}_${t.kind.name}'.hashCode.abs() % 1000000,
          'personId': t.personId,
          'personName': t.personName,
          'kind': t.kind.name,
          'title': t.kind == ReminderKind.birthday
              ? "🎉 It's ${t.personName}'s Birthday!"
              : "Upcoming: ${t.personName}'s Birthday",
          'body': whenText,
          'timestampMs': t.at.millisecondsSinceEpoch,
        };
      }).toList();

      await _channel.invokeMethod('apply', {'triggers': triggers});
    } catch (_) {
      // Ignored on test / unsupported platform environments
    }
  }

  @override
  Future<void> cancelAll() async {
    if (!_isLiveAndroid) return;
    try {
      await _channel.invokeMethod('cancelAll');
    } catch (_) {
      // Ignored
    }
  }

  @override
  Future<void> sendTestNotification({
    required String title,
    required String body,
  }) async {
    if (!_isLiveAndroid) return;
    try {
      await _channel.invokeMethod('testNotification', {
        'title': title,
        'body': body,
      });
    } catch (_) {
      // Ignored
    }
  }
}
