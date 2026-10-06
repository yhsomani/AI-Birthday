import 'package:timezone/timezone.dart' as tz;

import '../../birthdays/domain/birthday_engine.dart';
import '../../people/domain/person.dart';
import 'quiet_hours.dart';
import 'reminder_kind.dart';

/// A single concrete reminder delivery.
class ReminderTrigger {
  const ReminderTrigger({
    required this.personId,
    required this.personName,
    required this.kind,
    required this.at,
  });

  final String personId;
  final String personName;
  final ReminderKind kind;

  /// Wall-clock delivery time in the recipient calendar frame (recipient
  /// timezone when known, otherwise the caller's local frame).
  final DateTime at;
}

/// Result of a reminder planning pass.
class ReminderPlan {
  const ReminderPlan({required this.active, required this.suppressed});

  /// Reminders to hand to the notification scheduler.
  final List<ReminderTrigger> active;

  /// Reminders that fell inside quiet hours and must not be delivered.
  final List<ReminderTrigger> suppressed;

  bool get isEmpty => active.isEmpty && suppressed.isEmpty;

  int get count => active.length + suppressed.length;
}

/// Pure reminder planning (SSOT §17, FR-005).
///
/// Uses [BirthdayEngine] for the next occurrence (timezone-aware, leap-day
/// resolved), then places each enabled lead at [reminderHour] local time on
/// the matching day. Naturally deduplicates by (person, kind). Triggers inside
/// quiet hours are reported as [ReminderPlan.suppressed] rather than dropped,
/// so a device implementation can decide whether to slide them to the next
/// available minute.
class ReminderScheduler {
  const ReminderScheduler({
    this.engine = const BirthdayEngine(),
    this.reminderHour = 9,
  });

  final BirthdayEngine engine;
  final int reminderHour;

  ReminderPlan plan({
    required List<Person> people,
    required DateTime reference,
    Set<ReminderKind> enabled = const {
      ReminderKind.approaching,
      ReminderKind.prepare,
      ReminderKind.ready,
      ReminderKind.birthday,
    },
    QuietHours quietHours = const QuietHours.none(),
    String? fallbackTimezone,
  }) {
    final active = <ReminderTrigger>[];
    final suppressed = <ReminderTrigger>[];
    final seen = <({String personId, ReminderKind kind})>{};

    for (final person in people) {
      if (!person.hasBirthday) continue;
      final next = engine.computeNext(
        month: person.birthdayMonth!,
        day: person.birthdayDay!,
        birthYear: person.birthYear,
        timezoneName: person.timezone ?? fallbackTimezone,
        reference: reference,
      );
      final frameTimezone = person.timezone ?? fallbackTimezone;

      for (final kind in enabled) {
        final key = (personId: person.id, kind: kind);
        if (!seen.add(key)) continue;

        final trigger = _triggerFor(
          person: person,
          kind: kind,
          nextDate: next.nextDate,
          frameTimezone: frameTimezone,
        );
        // A birthday can be less than the full lead window away. Do not
        // manufacture reminders whose trigger time has already passed.
        if (!trigger.at.isAfter(reference)) continue;

        if (quietHours.contains(trigger.at)) {
          suppressed.add(trigger);
        } else {
          active.add(trigger);
        }
      }
    }

    active.sort(_byTime);
    suppressed.sort(_byTime);
    return ReminderPlan(active: active, suppressed: suppressed);
  }

  ReminderTrigger _triggerFor({
    required Person person,
    required ReminderKind kind,
    required DateTime nextDate,
    required String? frameTimezone,
  }) {
    final year = nextDate.year;
    final month = nextDate.month;
    final day = nextDate.day - kind.daysBefore;

    final at = frameTimezone != null
        ? tz.TZDateTime(
            tz.getLocation(frameTimezone),
            year,
            month,
            day,
            reminderHour,
          )
        : DateTime(year, month, day, reminderHour);

    return ReminderTrigger(
      personId: person.id,
      personName: person.name,
      kind: kind,
      at: at,
    );
  }

  static int _byTime(ReminderTrigger a, ReminderTrigger b) =>
      a.at.compareTo(b.at);
}
