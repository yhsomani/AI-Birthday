import 'package:flutter_test/flutter_test.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';

void main() {
  group('Birthday Date & Leap Year Calculations (SSOT §14)', () {
    test('Calculates standard birthday occurrence correctly', () {
      final date = Birthday.calculateOccurrenceDate(
        year: 2026,
        month: 5,
        day: 15,
      );
      expect(date, DateTime(2026, 5, 15));
    });

    test('Leap Day birthday on leap year (2024, 2028) remains Feb 29', () {
      final leapYearDate = Birthday.calculateOccurrenceDate(
        year: 2024,
        month: 2,
        day: 29,
      );
      expect(leapYearDate, DateTime(2024, 2, 29));

      final leapYearDate2028 = Birthday.calculateOccurrenceDate(
        year: 2028,
        month: 2,
        day: 29,
      );
      expect(leapYearDate2028, DateTime(2028, 2, 29));
    });

    test('Leap Day birthday on non-leap year defaults to Feb 28 per SSOT §14', () {
      final nonLeapDate = Birthday.calculateOccurrenceDate(
        year: 2026,
        month: 2,
        day: 29,
        preferMar1: false,
      );
      expect(nonLeapDate, DateTime(2026, 2, 28));
    });

    test('Leap Day birthday on non-leap year falls to Mar 1 if preferMar1 is chosen', () {
      final nonLeapDateMar1 = Birthday.calculateOccurrenceDate(
        year: 2026,
        month: 2,
        day: 29,
        preferMar1: true,
      );
      expect(nonLeapDateMar1, DateTime(2026, 3, 1));
    });

    test('nextBirthdayDate returns current year if birthday is upcoming', () {
      final refDate = DateTime(2026, 3, 10);
      final next = Birthday.nextBirthdayDate(
        month: 5,
        day: 20,
        from: refDate,
      );
      expect(next, DateTime(2026, 5, 20));
    });

    test('nextBirthdayDate returns next year if birthday has already passed this year', () {
      final refDate = DateTime(2026, 7, 10);
      final next = Birthday.nextBirthdayDate(
        month: 2,
        day: 14,
        from: refDate,
      );
      expect(next, DateTime(2027, 2, 14));
    });

    test('nextBirthdayDate returns today if birthday is today', () {
      final refDate = DateTime(2026, 6, 1);
      final next = Birthday.nextBirthdayDate(
        month: 6,
        day: 1,
        from: refDate,
      );
      expect(next, DateTime(2026, 6, 1));
    });

    test('daysUntil and isToday report accurate relative duration', () {
      final refDate = DateTime(2026, 10, 2);
      final todayBirthday = Birthday(
        id: 'b-1',
        personId: 'p-1',
        cycleYear: 2026,
        date: DateTime(2026, 10, 2),
        createdAt: DateTime(2026, 1, 1),
        updatedAt: DateTime(2026, 1, 1),
      );

      expect(todayBirthday.daysUntil(refDate), 0);
      expect(todayBirthday.isToday(refDate), isTrue);

      final futureBirthday = Birthday(
        id: 'b-2',
        personId: 'p-2',
        cycleYear: 2026,
        date: DateTime(2026, 10, 5),
        createdAt: DateTime(2026, 1, 1),
        updatedAt: DateTime(2026, 1, 1),
      );

      expect(futureBirthday.daysUntil(refDate), 3);
      expect(futureBirthday.isToday(refDate), isFalse);
    });
  });
}
