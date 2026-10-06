import 'package:ai_birthday/features/people/domain/person.dart';
import 'package:ai_birthday/features/reminders/domain/quiet_hours.dart';
import 'package:ai_birthday/features/reminders/domain/reminder_kind.dart';
import 'package:ai_birthday/features/reminders/domain/reminder_schedule.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:timezone/timezone.dart' as tz;

import 'package:timezone/data/latest.dart' as tz_data;

void main() {
  setUpAll(() {
    tz_data.initializeTimeZones();
  });

  Person person({
    String id = 'p1',
    String name = 'Ana',
    int month = 3,
    int day = 20,
    String? timezone,
  }) {
    final now = DateTime.utc(2026, 1, 1, 8);
    return Person(
      id: id,
      name: name,
      birthdayMonth: month,
      birthdayDay: day,
      timezone: timezone,
      createdAt: now,
      updatedAt: now,
    );
  }

  test('plans each enabled lead at 9:00 on the right day', () {
    final reference = DateTime.utc(2026, 3, 14);
    const scheduler = ReminderScheduler();
    final plan = scheduler.plan(
      people: [person()],
      reference: reference,
      enabled: ReminderKind.values.toSet(),
    );

    expect(plan.suppressed, isEmpty);
    expect(plan.active, hasLength(4));

    final byKind = {for (final t in plan.active) t.kind: t};
    expect(byKind[ReminderKind.approaching]!.at, DateTime(2026, 3, 13, 9));
    expect(byKind[ReminderKind.prepare]!.at, DateTime(2026, 3, 18, 9));
    expect(byKind[ReminderKind.ready]!.at, DateTime(2026, 3, 19, 9));
    expect(byKind[ReminderKind.birthday]!.at, DateTime(2026, 3, 20, 9));
  });

  test('skips lead reminders that have already passed', () {
    const scheduler = ReminderScheduler();
    final plan = scheduler.plan(
      people: [person(month: 3, day: 20)],
      reference: DateTime.utc(2026, 3, 18, 10),
      enabled: ReminderKind.values.toSet(),
    );

    expect(
      plan.active.map((trigger) => trigger.kind).toSet(),
      {ReminderKind.ready, ReminderKind.birthday},
    );
    expect(plan.suppressed, isEmpty);
    expect(plan.active, hasLength(2));
  });

  test('plans only the enabled leads', () {
    const scheduler = ReminderScheduler();
    final plan = scheduler.plan(
      people: [person()],
      reference: DateTime.utc(2026, 3, 14),
      enabled: const {ReminderKind.prepare, ReminderKind.birthday},
    );
    expect(plan.active.map((t) => t.kind).toSet(), {
      ReminderKind.prepare,
      ReminderKind.birthday,
    });
  });

  test('dedupes repeated people by (person, kind)', () {
    const scheduler = ReminderScheduler();
    final plan = scheduler.plan(
      people: [person(), person()],
      reference: DateTime.utc(2026, 3, 14),
      enabled: ReminderKind.values.toSet(),
    );
    expect(plan.active, hasLength(4));
  });

  test('resolves leap-day birthdays in non-leap years via the engine', () {
    const scheduler = ReminderScheduler();
    final plan = scheduler.plan(
      people: [person(month: 2, day: 29, name: 'Leap')],
      reference: DateTime.utc(2026, 2, 1),
      enabled: const {ReminderKind.birthday},
    );
    final trigger = plan.active.single;
    expect(trigger.personName, 'Leap');
    expect(trigger.at, DateTime(2026, 2, 28, 9));
  });

  test('quiet hours move triggers into the suppressed bucket', () {
    const scheduler = ReminderScheduler();
    const quiet = QuietHours(
      start: Duration(hours: 8),
      end: Duration(hours: 10),
    );
    final plan = scheduler.plan(
      people: [person()],
      reference: DateTime.utc(2026, 3, 14),
      enabled: ReminderKind.values.toSet(),
      quietHours: quiet,
    );
    expect(plan.active, isEmpty);
    expect(plan.suppressed, hasLength(4));
  });

  test('plans in the recipient timezone calendar frame', () {
    const scheduler = ReminderScheduler();
    final plan = scheduler.plan(
      people: [person(timezone: 'America/New_York', name: 'NY')],
      reference: DateTime.utc(2026, 3, 10, 20),
      enabled: const {ReminderKind.ready},
    );
    final trigger = plan.active.single;
    expect(trigger.at, isA<tz.TZDateTime>());
    // Mar 20 in New York.
    expect(trigger.at.year, 2026);
    expect(trigger.at.month, 3);
    expect(trigger.at.day, 19);
    expect(trigger.at.hour, 9);
    expect(trigger.at.minute, 0);
  });

  test('skips people without a birthday', () {
    const scheduler = ReminderScheduler();
    final now = DateTime.utc(2026, 1, 1, 8);
    final noBdayPerson = Person(
      id: 'p-no-bday',
      name: 'No Birthday Person',
      createdAt: now,
      updatedAt: now,
    );
    final plan = scheduler.plan(
      people: [noBdayPerson],
      reference: DateTime.utc(2026, 3, 14),
      enabled: ReminderKind.values.toSet(),
    );
    expect(plan.active, isEmpty);
    expect(plan.suppressed, isEmpty);
  });
}
