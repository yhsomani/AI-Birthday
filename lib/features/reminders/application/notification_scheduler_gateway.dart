import '../domain/reminder_schedule.dart';

/// Platform boundary for scheduling local notifications (SSOT §17).
///
/// Deliberately an interface: the device implementation
/// (`flutter_local_notifications` + Android permission flow) is delivered in a
/// device phase. No fake scheduler is shipped as product behaviour.
abstract class NotificationSchedulerGateway {
  /// Whether the app may currently post notifications.
  Future<bool> hasPermission();

  /// Whether the app may schedule precise birthday reminders on Android.
  /// Non-Android implementations should return true.
  Future<bool> hasExactAlarmPermission() async => true;

  /// Opens the system page where precise alarm access can be granted.
  /// Returns false when the platform cannot provide that page.
  Future<bool> requestExactAlarmPermission() async => false;

  /// Request the notification permission from the user.
  Future<bool> requestPermission();

  /// Replace outstanding reminders with [plan]'s active triggers.
  Future<bool> apply(ReminderPlan plan);

  /// Clear all scheduled reminders.
  Future<bool> cancelAll();

  /// Send an immediate test notification to verify delivery (QA & user testing).
  Future<bool> sendTestNotification({
    required String title,
    required String body,
  }) async {}

  /// Retrieves recipient personId if the app was launched by tapping a birthday notification.
  Future<String?> getInitialNotificationPersonId() async => null;

  /// Registers a listener invoked when a notification is tapped while the app is alive.
  void setNotificationOpenedHandler(void Function(String personId) onOpened) {}
}
