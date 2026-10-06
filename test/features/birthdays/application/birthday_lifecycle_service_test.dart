import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/features/birthdays/application/birthday_lifecycle_service.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/birthdays/domain/repositories/birthdays_repository.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';

void main() {
  group('BirthdayLifecycleService', () {
    const service = BirthdayLifecycleService(
      now: _fixedNow,
    );

    Person person({int month = 10, int day = 25}) {
      final timestamp = _fixedNow();
      return Person(
        id: 'person-1',
        name: 'Priya',
        birthdayMonth: month,
        birthdayDay: day,
        createdAt: timestamp,
        updatedAt: timestamp,
      );
    }

    test('creates the current birthday cycle when missing', () async {
      final repo = InMemoryBirthdaysRepository();

      await service.refresh(
        people: [person()],
        birthdaysRepository: repo,
      );

      final birthday = await repo.getBirthdayForPerson('person-1');
      expect(birthday, isNotNull);
      expect(birthday!.cycleYear, 2026);
      expect(birthday.date, DateTime(2026, 10, 25));
      expect(birthday.status, BirthdayStatus.upcoming);
    });

    test('rolls a stale cycle into the next year and clears its draft', () async {
      final repo = InMemoryBirthdaysRepository(
        initialBirthdays: [
          Birthday(
            id: 'birthday-person-1',
            personId: 'person-1',
            cycleYear: 2025,
            date: DateTime(2025, 10, 25),
            status: BirthdayStatus.completed,
            draftId: 'draft-1',
            createdAt: DateTime(2025, 1, 1),
            updatedAt: DateTime(2025, 10, 25),
          ),
        ],
      );

      await service.refresh(
        people: [person()],
        birthdaysRepository: repo,
      );

      final birthday = await repo.getBirthdayForPerson('person-1');
      expect(birthday!.cycleYear, 2026);
      expect(birthday.date, DateTime(2026, 10, 25));
      expect(birthday.status, BirthdayStatus.upcoming);
      expect(birthday.draftId, isNull);
    });

    test('preserves completed state for the same active cycle', () async {
      final repo = InMemoryBirthdaysRepository(
        initialBirthdays: [
          Birthday(
            id: 'birthday-person-1',
            personId: 'person-1',
            cycleYear: 2026,
            date: DateTime(2026, 10, 25),
            status: BirthdayStatus.completed,
            draftId: 'draft-1',
            createdAt: DateTime(2026, 1, 1),
            updatedAt: DateTime(2026, 10, 25),
          ),
        ],
      );

      await service.refresh(
        people: [person()],
        birthdaysRepository: repo,
      );

      final birthday = await repo.getBirthdayForPerson('person-1');
      expect(birthday!.status, BirthdayStatus.completed);
      expect(birthday.draftId, 'draft-1');
    });

    test('resolves Feb 29 consistently through the BirthdayEngine', () async {
      final repo = InMemoryBirthdaysRepository();

      await service.refresh(
        people: [person(month: 2, day: 29)],
        birthdaysRepository: repo,
      );

      final birthday = await repo.getBirthdayForPerson('person-1');
      expect(birthday!.date, DateTime(2027, 2, 28));
    });
  });
}

DateTime _fixedNow() => DateTime(2026, 10, 5);
