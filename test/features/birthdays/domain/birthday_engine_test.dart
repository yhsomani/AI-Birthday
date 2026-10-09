import 'package:ai_birthday/features/birthdays/domain/birthday_engine.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:timezone/data/latest_all.dart' as tz_data;
import 'package:timezone/timezone.dart' as tz;

void main() {
  setUpAll(tz_data.initializeTimeZones);

  group('BirthdayEngine calendar primitives', () {
    const engine = BirthdayEngine();

    test('isLeapYear follows the Gregorian rule', () {
      expect(BirthdayEngine.isLeapYear(2000), isTrue);
      expect(BirthdayEngine.isLeapYear(2024), isTrue);
      expect(BirthdayEngine.isLeapYear(1900), isFalse);
      expect(BirthdayEngine.isLeapYear(2100), isFalse);
      expect(BirthdayEngine.isLeapYear(2023), isFalse);
    });

    test('daysInMonth respects leap February', () {
      expect(BirthdayEngine.daysInMonth(2, 2024), 29);
      expect(BirthdayEngine.daysInMonth(2, 2023), 28);
      expect(BirthdayEngine.daysInMonth(4, 2025), 30);
      expect(BirthdayEngine.daysInMonth(12, 2025), 31);
      expect(BirthdayEngine.daysInMonth(0, 2025), 0);
      expect(BirthdayEngine.daysInMonth(13, 2025), 0);
    });

    test('resolveFeb29 picks Feb 28 or Mar 1', () {
      expect(engine.resolveFeb29(2023), (month: 2, day: 28));
      expect(engine.resolveFeb29(2023, resolution: LeapDayResolution.mar1), (
        month: 3,
        day: 1,
      ));
    });

    test('isValidMonthDay accepts legal and rejects illegal dates', () {
      expect(
        engine.isValidMonthDay(2, 29),
        isTrue,
      ); // leap-possible, year unknown
      expect(engine.isValidMonthDay(4, 31), isFalse);
      expect(engine.isValidMonthDay(6, 31), isFalse);
      expect(engine.isValidMonthDay(1, 32), isFalse);
      expect(engine.isValidMonthDay(13, 1), isFalse);
      expect(engine.isValidMonthDay(0, 1), isFalse);
      expect(
        engine.isValidMonthDay(2, 30),
        isFalse,
      ); // cannot exist in any year
      // With a known year:
      expect(engine.isValidMonthDay(2, 29, year: 2024), isTrue);
      expect(engine.isValidMonthDay(2, 29, year: 2023), isTrue); // resolvable
      expect(engine.isValidMonthDay(2, 30, year: 2024), isFalse);
      expect(engine.isValidMonthDay(4, 31, year: 2025), isFalse);
      expect(engine.isValidMonthDay(12, 31, year: 2025), isTrue);
    });
  });

  group('BirthdayEngine.computeNext (wall clock frame)', () {
    const engine = BirthdayEngine();

    test('next occurrence inside the same year', () {
      final next = engine.computeNext(
        month: 1,
        day: 15,
        reference: DateTime(2025, 1, 10),
      );
      expect(next.year, 2025);
      expect(next.daysUntil, 5);
      expect(next.isToday, isFalse);
      expect(next.isLeapDayAdjusted, isFalse);
    });

    test('rolls into the next year after the date passes', () {
      final next = engine.computeNext(
        month: 1,
        day: 15,
        reference: DateTime(2025, 1, 20),
      );
      expect(next.year, 2026);
      expect(next.daysUntil, 360); // Jan 20 2025 -> Jan 15 2026
    });

    test('birthday on the reference date is today (daysUntil 0)', () {
      final next = engine.computeNext(
        month: 3,
        day: 15,
        reference: DateTime(2025, 3, 15),
      );
      expect(next.isToday, isTrue);
      expect(next.daysUntil, 0);
      expect(next.year, 2025);
    });

    test('Feb 29 resolves to Feb 28 by default and Mar 1 when chosen', () {
      final feb28 = engine.computeNext(
        month: 2,
        day: 29,
        reference: DateTime(2025, 1, 5), // non-leap year
      );
      expect(feb28.isLeapDayAdjusted, isTrue);
      expect(feb28.nextDate.month, 2);
      expect(feb28.nextDate.day, 28);
      expect(feb28.daysUntil, 54);

      final mar1 = engine.computeNext(
        month: 2,
        day: 29,
        reference: DateTime(2025, 1, 5),
        leapDay: LeapDayResolution.mar1,
      );
      expect(mar1.nextDate.month, 3);
      expect(mar1.nextDate.day, 1);
    });

    test('Feb 29 in a leap year stays Feb 29', () {
      final next = engine.computeNext(
        month: 2,
        day: 29,
        reference: DateTime(2024, 2, 28),
      );
      expect(next.nextDate.day, 29);
      expect(next.daysUntil, 1);
      expect(next.isLeapDayAdjusted, isFalse);

      final sameDay = engine.computeNext(
        month: 2,
        day: 29,
        reference: DateTime(2024, 2, 29, 12),
      );
      expect(sameDay.isToday, isTrue);
    });

    test('a UTC reference is read as the local calendar date', () {
      // Pick an instant whose local calendar date differs from its UTC date
      // on this host, so the test is sensitive to the frame. On a UTC host
      // no such hour exists and the case degenerates to a plain check.
      final instant = List.generate(24, (h) => DateTime.utc(2025, 3, 15, h))
          .firstWhere(
            (t) => t.toLocal().day != t.day,
            orElse: () => DateTime.utc(2025, 3, 15, 12),
          );
      final local = instant.toLocal();
      final next = engine.computeNext(
        month: local.month,
        day: local.day,
        reference: instant,
      );
      expect(next.isToday, isTrue);
      expect(next.daysUntil, 0);
    });
  });

  group('BirthdayEngine.computeNext (named timezone frame)', () {
    const engine = BirthdayEngine();

    test('converts the reference instant into the recipient calendar', () {
      // 2025-03-05 22:30 UTC = 2025-03-06 04:00 IST.
      final next = engine.computeNext(
        month: 3,
        day: 8,
        timezoneName: 'Asia/Kolkata',
        reference: DateTime.utc(2025, 3, 5, 22, 30),
      );
      expect(next.daysUntil, 2);
      expect(next.isToday, isFalse);
    });

    test('recognizes a birthday today in the recipient timezone', () {
      // 2025-03-07 20:00 UTC = 2025-03-08 01:30 IST on Mar 8.
      final next = engine.computeNext(
        month: 3,
        day: 8,
        timezoneName: 'Asia/Kolkata',
        reference: DateTime.utc(2025, 3, 7, 20, 0),
      );
      expect(next.isToday, isTrue);
      expect(next.daysUntil, 0);
    });

    test('the same instant can be a different calendar day in UTC', () {
      final kolkata = engine.computeNext(
        month: 3,
        day: 9,
        timezoneName: 'Asia/Kolkata',
        reference: DateTime.utc(2025, 3, 8, 20, 0),
      );
      final utc = engine.computeNext(
        month: 3,
        day: 9,
        timezoneName: 'UTC',
        reference: DateTime.utc(2025, 3, 8, 20, 0),
      );
      // IST is already Mar 9; UTC is still Mar 8 (past the 9th's midnight).
      expect(kolkata.isToday, isTrue);
      expect(utc.daysUntil, 1);
    });

    test('resolves Feb 29 in the recipient calendar too', () {
      final next = engine.computeNext(
        month: 2,
        day: 29,
        timezoneName: 'Pacific/Auckland', // 2025 is not a leap year
        reference: DateTime.utc(2025, 2, 20),
      );
      expect(next.isLeapDayAdjusted, isTrue);
      expect(next.nextDate.day, 28);
    });
  });

  group('BirthdayEngine timezone utilities', () {
    const engine = BirthdayEngine();

    test('isKnownTimezone matches IANA zones', () {
      expect(engine.isKnownTimezone('Asia/Kolkata'), isTrue);
      expect(engine.isKnownTimezone('Mars/Olympus'), isFalse);
    });

    test('effectiveTimezone prefers recipient over user', () {
      expect(
        engine.effectiveTimezone('Asia/Kolkata', 'Europe/London'),
        'Asia/Kolkata',
      );
      expect(engine.effectiveTimezone(null, 'Europe/London'), 'Europe/London');
      expect(engine.effectiveTimezone('Nope/Bogus', 'Nope/Egg'), 'UTC');
      expect(engine.effectiveTimezone('  ', null), 'UTC');
    });
  });

  test('tz database is initialized and locations load', () {
    expect(tz.getLocation('America/New_York').name, 'America/New_York');
  });
}
