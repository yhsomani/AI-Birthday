import '../domain/reminder_schedule.dart';

/// Platform boundary for scheduling local notifications (SSOT §17).
///
/// Deliberately an interface: the device implementation
/// (`flutter_local_notifications` + Android permission flow) is delivered in a
/// device phase. No fake scheduler is shipped as product behaviour.
abstract class NotificationSchedulerGateway {
  /// Whether the app may currently post notifications.
  Future<bool> hasPermission();

  /// Request the notification permission from the user.
  Future<bool> requestPermission();

  /// Replace outstanding reminders with [plan]'s active triggers.
  Future<void> apply(ReminderPlan plan);

  /// Clear all scheduled reminders.
  Future<void> cancelAll();

  /// Send an immediate test notification to verify delivery (QA & user testing).
  Future<void> sendTestNotification({
    required String title,
    required String body,
  }) async {}

  /// Retrieves recipient personId if the app was launched by tapping a birthday notification.
  Future<String?> getInitialNotificationPersonId() async => null;

  /// Registers a listener invoked when a notification is tapped while the app is alive.
  void setNotificationOpenedHandler(void Function(String personId) onOpened) {}
}
