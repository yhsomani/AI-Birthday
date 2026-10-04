import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/core_providers.dart';
import '../domain/reminder_schedule.dart';
import 'android_notification_scheduler_gateway.dart';
import 'notification_scheduler_gateway.dart';
import 'reminder_service.dart';

/// Notification scheduler gateway provider using native Android platform implementation.
final notificationSchedulerGatewayProvider =
    Provider<NotificationSchedulerGateway>((ref) {
      return const AndroidNotificationSchedulerGateway();
    });

/// Application reminder service orchestrating reminder planning and device sync (SSOT §17).
final reminderServiceProvider = Provider<ReminderService>((ref) {
  final gateway = ref.watch(notificationSchedulerGatewayProvider);
  final logger = ref.watch(loggerProvider);
  return ReminderService(gateway, const ReminderScheduler(), logger);
});
