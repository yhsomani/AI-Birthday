import 'package:ai_birthday/app/providers.dart' show peopleRepositoryProvider;
import 'package:ai_birthday/core/core_providers.dart' show databaseProvider;
import 'package:ai_birthday/core/database/app_database.dart' as db;
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/people/data/drift_people_repository.dart';
import 'package:ai_birthday/features/people/data/person_providers.dart'
    show personServiceProvider;
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/features/people/domain/person.dart';
import 'package:drift/drift.dart' as drift;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

void main() {
  late db.AppDatabase database;
  late DriftPeopleRepository repo;

  setUp(() {
    database = db.AppDatabase(NativeDatabase.memory());
    repo = DriftPeopleRepository(database);
  });

  tearDown(() async => database.close());

  Person person({
    String? id,
    String name = 'Sam',
    int month = 4,
    int day = 22,
  }) {
    final now = DateTime.utc(2025, 1, 1, 8);
    return Person(
      id: id ?? 'p-$name-$day',
      name: name,
      birthdayMonth: month,
      birthdayDay: day,
      relationshipCloseness: RelationshipCloseness.close,
      preferredTone: MessageTone.funny,
      importantFacts: ['loves jazz', 'allergic to peanuts'],
      preferredDeliveryChannel: DeliveryChannel.whatsapp,
      createdAt: now,
      updatedAt: now,
    );
  }

  test('round-trips a person preserving enums, facts and envelope', () async {
    final p = person(month: 2, day: 29);
    await repo.savePerson(p);

    final loaded = await repo.getPersonById(p.id);
    expect(loaded, isNotNull);
    expect(loaded!.birthdayMonth, 2);
    expect(loaded.birthdayDay, 29);
    expect(loaded.relationshipCloseness, RelationshipCloseness.close);
    expect(loaded.preferredTone, MessageTone.funny);
    expect(
      loaded.importantFacts,
      unorderedEquals(['loves jazz', 'allergic to peanuts']),
    );
    expect(loaded.preferredDeliveryChannel, DeliveryChannel.whatsapp);
    expect(loaded.version, 1);
    expect(loaded.createdAt, DateTime.utc(2025, 1, 1, 8));
  });

  test(
    'application and repository providers share one people repository',
    () async {
      final container = ProviderContainer(
        overrides: [databaseProvider.overrideWithValue(database)],
      );
      addTearDown(container.dispose);

      final service = container.read(personServiceProvider);
      final repository = container.read(peopleRepositoryProvider);

      await repository.savePerson(person(id: 'shared', name: 'Shared'));
      expect((await service.getAll()).single.name, 'Shared');
    },
  );

  test(
    'watchActivePeople and listActivePeople omit soft-deleted rows and sort by name',
    () async {
      await repo.savePerson(person(id: 'b', name: 'Bri'));
      await repo.savePerson(person(id: 'a', name: 'Ana'));
      await repo.savePerson(person(id: 'z', name: 'Zoe'));
      await repo.softDeletePerson('z', DateTime.utc(2025, 2, 1));

      final visible = await repo.listActivePeople();
      expect(visible.map((p) => p.id), ['a', 'b']);

      final all = await repo.listActivePeople(includeDeleted: true);
      expect(all, hasLength(3));
      final deleted = all.firstWhere((p) => p.id == 'z');
      expect(deleted.deletedAt, isNotNull);
      expect(deleted.version, 2);
    },
  );

  test(
    'listActivePeople and watchActivePeople sort case-insensitively with id tie-breaker',
    () async {
      await repo.savePerson(person(id: 'p-3', name: 'bob'));
      await repo.savePerson(person(id: 'p-2', name: 'Alice'));
      await repo.savePerson(person(id: 'p-1', name: 'alice'));

      final all = await repo.listActivePeople();
      expect(all.map((p) => '${p.name}:${p.id}').toList(), [
        'alice:p-1',
        'Alice:p-2',
        'bob:p-3',
      ]);

      final streamed = await repo.watchActivePeople().first;
      expect(streamed.map((p) => '${p.name}:${p.id}').toList(), [
        'alice:p-1',
        'Alice:p-2',
        'bob:p-3',
      ]);
    },
  );

  test('watchActivePeople emits updates', () async {
    final emissions = <List<Person>>[];
    final sub = repo.watchActivePeople().listen(emissions.add);
    final p1 = person(id: 'p1');
    await repo.savePerson(p1);
    await repo.savePerson(person(id: 'p2', name: 'Abe'));
    await repo.softDeletePerson('p1', DateTime.utc(2025, 2, 1));
    await repo.restorePerson(p1, null);

    await Future<void>.delayed(Duration.zero);
    expect(emissions, isNotEmpty);
    expect(emissions.last, hasLength(2));
    await sub.cancel();
  });

  test('restorePerson clears the tombstone', () async {
    final p1 = person(id: 'p1');
    await repo.savePerson(p1);
    await repo.softDeletePerson('p1', DateTime.utc(2025, 2, 1));
    final hidden = await repo.getPersonById('p1', includeDeleted: true);
    expect(hidden!.deletedAt, isNotNull);
    expect(hidden.version, 2);

    await repo.restorePerson(p1, null);
    final restored = await repo.getPersonById('p1');
    expect(restored!.deletedAt, isNull);
    expect(restored.version, 3);
  });

  test('savePerson is idempotent by id and replaces the row', () async {
    await repo.savePerson(person(id: 'p1', name: 'Sam'));
    final updated = person(id: 'p1', name: 'Samantha');
    await repo.savePerson(updated);

    final loaded = await repo.getPersonById('p1');
    expect(loaded!.name, 'Samantha');
    expect(loaded.version, updated.version);
  });

  test(
    'saving through repository cannot resurrect a tombstoned person',
    () async {
      final original = person(id: 'deleted', name: 'Deleted');
      await repo.savePerson(original);
      await repo.softDeletePerson(original.id, DateTime.utc(2025, 2, 1));

      await repo.savePerson(original.copyWith(name: 'Stale update'));

      expect(await repo.listActivePeople(), isEmpty);
      expect(
        (await repo.getPersonById(
          original.id,
          includeDeleted: true,
        ))!.deletedAt,
        isNotNull,
      );
    },
  );

  test('softDeletePerson stamps the supplied deletedAt timestamp', () async {
    await repo.savePerson(person(id: 'p1'));
    await repo.softDeletePerson('p1', DateTime.utc(2030, 3, 3));
    final loaded = await repo.getPersonById('p1', includeDeleted: true);
    expect(loaded!.deletedAt, DateTime.utc(2030, 3, 3));
  });

  test(
    'every canonical closeness level round-trips unchanged (audit F10)',
    () async {
      for (final level in RelationshipCloseness.values) {
        final p = Person(
          id: 'closeness-${level.name}',
          name: 'Level ${level.name}',
          birthdayMonth: 5,
          birthdayDay: 6,
          relationshipCloseness: level,
          createdAt: DateTime.utc(2025, 1, 1),
          updatedAt: DateTime.utc(2025, 1, 1),
        );
        await repo.savePerson(p);
        final loaded = await repo.getPersonById(p.id);
        expect(loaded!.relationshipCloseness, level, reason: level.name);
      }
    },
  );

  test(
    'legacy stored closeness values collapse onto the three levels (F10)',
    () async {
      await repo.savePerson(person(id: 'legacy'));
      final cases = {
        'family': RelationshipCloseness.close,
        'goodFriend': RelationshipCloseness.close,
        'friend': RelationshipCloseness.casual,
        'acquaintance': RelationshipCloseness.casual,
        'colleague': RelationshipCloseness.casual,
        'other': RelationshipCloseness.casual,
        'distant': RelationshipCloseness.distant,
      };
      for (final entry in cases.entries) {
        await (database.update(
          database.persons,
        )..where((t) => t.id.equals('legacy'))).write(
          db.PersonsCompanion(relationshipCloseness: drift.Value(entry.key)),
        );
        final loaded = await repo.getPersonById('legacy');
        expect(loaded!.relationshipCloseness, entry.value, reason: entry.key);
      }
    },
  );

  test(
    'getBirthdayForPerson returns latest cycle when multiple cycles exist',
    () async {
      final now = DateTime.utc(2026, 1, 1);
      await repo.savePerson(person(id: 'p-multi'));
      await database
          .into(database.birthdays)
          .insert(
            db.BirthdaysCompanion.insert(
              id: 'b-2025',
              personId: 'p-multi',
              cycleYear: 2025,
              date: DateTime.utc(2025, 4, 22),
              status: 'completed',
              createdAt: now,
              updatedAt: now,
            ),
          );
      await database
          .into(database.birthdays)
          .insert(
            db.BirthdaysCompanion.insert(
              id: 'b-2026',
              personId: 'p-multi',
              cycleYear: 2026,
              date: DateTime.utc(2026, 4, 22),
              status: 'upcoming',
              createdAt: now,
              updatedAt: now,
            ),
          );

      final latest = await repo.getBirthdayForPerson('p-multi');
      expect(latest, isNotNull);
      expect(latest!.id, 'b-2026');
      expect(latest.cycleYear, 2026);
    },
  );

  test(
    'purgeExpiredTombstones removes expired tombstones and keeps recent/active rows',
    () async {
      final oldTime = DateTime.utc(2025, 1, 1);
      final recentTime = DateTime.utc(2026, 1, 15);
      final cutoff = DateTime.utc(2026, 1, 1);

      await repo.savePersonAndBirthday(
        person(id: 'expired'),
        Birthday(
          id: 'b-expired',
          personId: 'expired',
          cycleYear: 2025,
          date: DateTime.utc(2025, 4, 22),
          createdAt: oldTime,
          updatedAt: oldTime,
        ),
      );
      await repo.softDeletePerson('expired', oldTime);

      await repo.savePerson(person(id: 'recent-del'));
      await repo.softDeletePerson('recent-del', recentTime);

      await repo.savePerson(person(id: 'active'));

      final purged = await repo.purgeExpiredTombstones(cutoff);
      expect(purged, 1);
      expect(await repo.getPersonById('expired', includeDeleted: true), isNull);
      expect(
        await repo.getPersonById('recent-del', includeDeleted: true),
        isNotNull,
      );
      expect(await repo.getPersonById('active'), isNotNull);
    },
  );
}
