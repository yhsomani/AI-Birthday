import 'dart:async';

import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/features/people/domain/person.dart';
import 'package:ai_birthday/features/reminders/application/notification_scheduler_gateway.dart';
import 'package:ai_birthday/features/reminders/application/reminder_service.dart';
import 'package:ai_birthday/features/reminders/application/reminder_settings_controller.dart';
import 'package:ai_birthday/features/reminders/domain/quiet_hours.dart';
import 'package:ai_birthday/features/reminders/domain/reminder_kind.dart';
import 'package:ai_birthday/features/reminders/domain/reminder_schedule.dart';
import 'package:flutter_test/flutter_test.dart';

/// Test double only; the product ships the abstract gateway, never a fake.
class RecordingGateway implements NotificationSchedulerGateway {
  final Completer<bool> permissionCompleter = Completer<bool>();
  final List<ReminderPlan> applied = [];
  int cancelAllCalls = 0;
  bool granted = true;

  @override
  Future<bool> hasPermission() async => granted;

  @override
  Future<bool> requestPermission() async => granted;

  @override
  Future<bool> hasExactAlarmPermission() async => granted;

  @override
  Future<bool> requestExactAlarmPermission() async => granted;

  @override
  Future<void> apply(ReminderPlan plan) async {
    applied.add(plan);
  }

  @override
  Future<void> cancelAll() async {
    cancelAllCalls++;
  }

  @override
  Future<void> sendTestNotification({
    required String title,
    required String body,
  }) async {}

  @override
  Future<String?> getInitialNotificationPersonId() async => null;

  @override
  void setNotificationOpenedHandler(void Function(String personId) onOpened) {}
}

void main() {
  Person person() {
    final now = DateTime.utc(2026, 1, 1, 8);
    return Person(
      id: 'p1',
      name: 'Ana',
      birthdayMonth: 3,
      birthdayDay: 20,
      createdAt: now,
      updatedAt: now,
    );
  }

  final RecordingLogger logger = RecordingLogger();

  test('disabled reminders cancel all outstanding notifications', () async {
    final gateway = RecordingGateway();
    final service = ReminderService(gateway, const ReminderScheduler(), logger);

    await service.sync(
      people: [person()],
      settings: const ReminderSettings(enabled: false),
      reference: DateTime.utc(2026, 3, 14),
    );

    expect(gateway.cancelAllCalls, 1);
    expect(gateway.applied, isEmpty);
  });

  test('enabled reminders forward the planned active triggers', () async {
    final gateway = RecordingGateway();
    final service = ReminderService(gateway, const ReminderScheduler(), logger);

    await service.sync(
      people: [person()],
      settings: const ReminderSettings(
        enabled: true,
        kinds: {ReminderKind.prepare, ReminderKind.birthday},
      ),
      reference: DateTime.utc(2026, 3, 14),
    );

    expect(gateway.cancelAllCalls, 0);
    final plan = gateway.applied.single;
    expect(plan.active, hasLength(2));
    expect(plan.active.map((t) => t.kind).toSet(), {
      ReminderKind.prepare,
      ReminderKind.birthday,
    });
    expect(plan.suppressed, isEmpty);
  });

  test('quiet hours are respected by the service sync', () async {
    final gateway = RecordingGateway();
    final service = ReminderService(gateway, const ReminderScheduler(), logger);

    await service.sync(
      people: [person()],
      settings: const ReminderSettings(
        enabled: true,
        kinds: {ReminderKind.birthday},
        quietHours: QuietHours(
          start: Duration(hours: 8),
          end: Duration(hours: 10),
        ),
      ),
      reference: DateTime.utc(2026, 3, 14),
    );

    final plan = gateway.applied.single;
    expect(plan.active, isEmpty);
    expect(plan.suppressed, hasLength(1));
  });
}
