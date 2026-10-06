import 'package:ai_birthday/core/database/app_database.dart' as db;
import 'package:ai_birthday/features/people/data/person_repository.dart';
import 'package:ai_birthday/features/people/domain/person.dart';
import 'package:ai_birthday/features/people/domain/person_enums.dart';
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  late db.AppDatabase database;
  late DriftPeopleStore store;

  setUp(() {
    database = db.AppDatabase(NativeDatabase.memory());
    store = DriftPeopleStore(database);
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
      relationshipCloseness: RelationshipCloseness.goodFriend,
      preferredTone: PreferredTone.funny,
      importantFacts: ['loves jazz', 'allergic to peanuts'],
      preferredDeliveryChannel: DeliveryChannel.whatsapp,
      createdAt: now,
      updatedAt: now,
    );
  }

  test('round-trips a person preserving enums, facts and envelope', () async {
    final p = person(month: 2, day: 29);
    await store.save(p);

    final loaded = await store.getById(p.id);
    expect(loaded, isNotNull);
    expect(loaded!.birthdayMonth, 2);
    expect(loaded.birthdayDay, 29);
    expect(loaded.relationshipCloseness, RelationshipCloseness.goodFriend);
    expect(loaded.preferredTone, PreferredTone.funny);
    expect(
      loaded.importantFacts,
      unorderedEquals(['loves jazz', 'allergic to peanuts']),
    );
    expect(loaded.preferredDeliveryChannel, DeliveryChannel.whatsapp);
    expect(loaded.version, 1);
    expect(loaded.createdAt, DateTime.utc(2025, 1, 1, 8));
  });

  test('watchAll and getAll omit soft-deleted rows and sort by name', () async {
    await store.save(person(id: 'b', name: 'Bri'));
    await store.save(person(id: 'a', name: 'Ana'));
    await store.save(person(id: 'z', name: 'Zoe'));
    await store.softDelete('z');

    final visible = await store.getAll();
    expect(visible.map((p) => p.id), ['a', 'b']);

    final all = await store.getAll(includeDeleted: true);
    expect(all, hasLength(3));
    final deleted = all.firstWhere((p) => p.id == 'z');
    expect(deleted.deletedAt, isNotNull);
    expect(deleted.version, 2);
  });

  test('watchAll emits updates', () async {
    final emissions = <List<Person>>[];
    final sub = store.watchAll().listen(emissions.add);
    await store.save(person(id: 'p1'));
    await store.save(person(id: 'p2', name: 'Abe'));
    await store.softDelete('p1');
    await store.restore('p1');

    await Future<void>.delayed(Duration.zero);
    expect(emissions, isNotEmpty);
    expect(emissions.last, hasLength(2));
    await sub.cancel();
  });

  test('restore clears the tombstone', () async {
    await store.save(person(id: 'p1'));
    await store.softDelete('p1');
    final hidden = await store.getById('p1');
    expect(hidden!.deletedAt, isNotNull);
    expect(hidden.version, 2);

    await store.restore('p1');
    final restored = await store.getById('p1');
    expect(restored!.deletedAt, isNull);
    expect(restored.version, 3);
  });

  test('save is idempotent by id and replaces the row', () async {
    await store.save(person(id: 'p1', name: 'Sam'));
    final updated = person(id: 'p1', name: 'Samantha');
    await store.save(updated);

    final loaded = await store.getById('p1');
    expect(loaded!.name, 'Samantha');
    expect(loaded.version, updated.version);
  });

  test('softDelete stamps a timestamp via the injected clock', () async {
    final clocked = DriftPeopleStore(
      database,
      now: () => DateTime.utc(2030, 3, 3),
    );
    await clocked.save(person(id: 'p1'));
    await clocked.softDelete('p1');
    final loaded = await clocked.getById('p1');
    expect(loaded!.deletedAt, DateTime.utc(2030, 3, 3));
  });
}
