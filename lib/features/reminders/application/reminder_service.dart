import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/features/people/domain/person.dart';

import '../domain/reminder_schedule.dart';
import 'notification_scheduler_gateway.dart';
import 'reminder_settings_controller.dart';

/// Orchestrates reminder planning for a set of recipients (FR-005).
///
/// Syncs the computed [ReminderPlan] onto the native scheduler only when
/// reminders are enabled; respects [ReminderSettings.quietHours]. The gateway
/// is the sole device touchpoint.
class ReminderService {
  ReminderService(this._gateway, this._scheduler, this._logger);

  final NotificationSchedulerGateway _gateway;
  final ReminderScheduler _scheduler;
  final AppLogger _logger;

  /// Recomputes and reapplies reminders for [people] under [settings].
  Future<void> sync({
    required List<Person> people,
    required ReminderSettings settings,
    DateTime? reference,
  }) async {
    if (!settings.enabled) {
      await _gateway.cancelAll();
      return;
    }

    final plan = _scheduler.plan(
      people: people,
      reference: reference ?? DateTime.now(),
      enabled: settings.kinds,
      quietHours: settings.quietHours,
    );
    await _gateway.apply(plan);
    _logger.info(
      'reminders',
      'Reminder sync',
      params: {
        'active': '${plan.active.length}',
        'suppressed': '${plan.suppressed.length}',
      },
    );
  }
}
