import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/features/birthdays/application/birthday_lifecycle_service.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/birthdays/domain/repositories/birthdays_repository.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';

void main() {
  group('BirthdayLifecycleService', () {
    const service = BirthdayLifecycleService(now: _fixedNow);

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

    Person personAt(String id, {int month = 10, int day = 25}) {
      final timestamp = _fixedNow();
      return Person(
        id: id,
        name: id,
        birthdayMonth: month,
        birthdayDay: day,
        createdAt: timestamp,
        updatedAt: timestamp,
      );
    }

    test('creates the current birthday cycle when missing', () async {
      final repo = InMemoryBirthdaysRepository();

      await service.refresh(people: [person()], birthdaysRepository: repo);

      final birthday = await repo.getBirthdayForPerson('person-1');
      expect(birthday, isNotNull);
      expect(birthday!.cycleYear, 2026);
      expect(birthday.date, DateTime(2026, 10, 25));
      expect(birthday.status, BirthdayStatus.upcoming);
    });

    test(
      'rolls a stale cycle into the next year and clears its draft',
      () async {
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

        await service.refresh(people: [person()], birthdaysRepository: repo);

        final birthday = await repo.getBirthdayForPerson('person-1');
        expect(birthday!.cycleYear, 2026);
        expect(birthday.date, DateTime(2026, 10, 25));
        expect(birthday.status, BirthdayStatus.upcoming);
        expect(birthday.draftId, isNull);
      },
    );

    test(
      'promotes an existing upcoming birthday to reminder due today',
      () async {
        final repo = InMemoryBirthdaysRepository(
          initialBirthdays: [
            Birthday(
              id: 'birthday-person-1',
              personId: 'person-1',
              cycleYear: 2026,
              date: DateTime(2026, 10, 5),
              status: BirthdayStatus.upcoming,
              createdAt: DateTime(2026, 1, 1),
              updatedAt: DateTime(2026, 10, 4),
            ),
          ],
        );

        await service.refresh(
          people: [person(month: 10, day: 5)],
          birthdaysRepository: repo,
        );

        final birthday = await repo.getBirthdayForPerson('person-1');
        expect(birthday!.status, BirthdayStatus.reminderDue);
      },
    );

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

      await service.refresh(people: [person()], birthdaysRepository: repo);

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

    test(
      'reads the stored birthdays once for many people, not once per person',
      () async {
        final repo = _RecordingBirthdaysRepository(
          InMemoryBirthdaysRepository(
            initialBirthdays: [
              Birthday(
                id: 'birthday-person-1',
                personId: 'person-1',
                cycleYear: 2026,
                date: DateTime(2026, 10, 25),
                status: BirthdayStatus.upcoming,
                createdAt: DateTime(2026, 1, 1),
                updatedAt: DateTime(2026, 1, 1),
              ),
            ],
          ),
        );

        await service.refresh(
          people: [
            personAt('person-1'),
            personAt('person-2', month: 3, day: 5),
            personAt('person-3', month: 7, day: 1),
          ],
          birthdaysRepository: repo,
        );

        // The N+1 fix: one batched read, zero per-person lookups.
        expect(repo.birthdaysReads, 1);
        expect(repo.perPersonReads, 0);

        // Outcome parity: the existing cycle is untouched, new cycles created.
        final existing = await repo.getBirthdayForPerson('person-1');
        expect(existing!.status, BirthdayStatus.upcoming);
        expect(existing.date, DateTime(2026, 10, 25));
        final created = await repo.getBirthdayForPerson('person-2');
        expect(created, isNotNull);
        // Mar 5 falls after the Oct 5 reference date → rolls into next year.
        expect(created!.cycleYear, 2027);
        expect(created.date, DateTime(2027, 3, 5));
        expect(await repo.getBirthdayForPerson('person-3'), isNotNull);
      },
    );

    test('throws when a person has more than one stored birthday', () async {
      final repo = InMemoryBirthdaysRepository(
        initialBirthdays: [
          Birthday(
            id: 'birthday-person-1-a',
            personId: 'person-1',
            cycleYear: 2026,
            date: DateTime(2026, 10, 25),
            status: BirthdayStatus.upcoming,
            createdAt: DateTime(2026, 1, 1),
            updatedAt: DateTime(2026, 1, 1),
          ),
          Birthday(
            id: 'birthday-person-1-b',
            personId: 'person-1',
            cycleYear: 2025,
            date: DateTime(2025, 10, 25),
            status: BirthdayStatus.completed,
            createdAt: DateTime(2025, 1, 1),
            updatedAt: DateTime(2025, 10, 25),
          ),
        ],
      );

      // Matches drift's `getSingleOrNull` contract, which also throws on >1 row.
      await expectLater(
        service.refresh(people: [person()], birthdaysRepository: repo),
        throwsStateError,
      );
    });
  });
}

/// Wraps an in-memory repository to record how many bulk vs per-person reads a
/// refresh performs.
class _RecordingBirthdaysRepository implements BirthdaysRepository {
  _RecordingBirthdaysRepository(this._inner);

  final InMemoryBirthdaysRepository _inner;
  int birthdaysReads = 0;
  int perPersonReads = 0;

  @override
  Stream<List<Birthday>> watchBirthdays() => _inner.watchBirthdays();

  @override
  Future<List<Birthday>> getBirthdays() async {
    birthdaysReads++;
    return _inner.getBirthdays();
  }

  @override
  Future<Birthday?> getBirthday(String id) => _inner.getBirthday(id);

  @override
  Future<Birthday?> getBirthdayForPerson(String personId) {
    perPersonReads++;
    return _inner.getBirthdayForPerson(personId);
  }

  @override
  Future<void> saveBirthday(Birthday birthday) => _inner.saveBirthday(birthday);

  @override
  Future<void> updateBirthdayStatus(
    String id,
    BirthdayStatus status, {
    String? draftId,
  }) =>
      _inner.updateBirthdayStatus(id, status, draftId: draftId);

  @override
  Future<void> deleteBirthday(String id) => _inner.deleteBirthday(id);
}

DateTime _fixedNow() => DateTime(2026, 10, 5);
