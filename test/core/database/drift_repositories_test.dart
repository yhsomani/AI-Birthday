import 'package:ai_birthday/core/database/app_database.dart';
import 'package:ai_birthday/core/database/drift_repositories.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart'
    as domain;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('DriftPeopleRepository', () {
    late AppDatabase db;
    late DriftPeopleRepository repo;

    domain.Person person({String id = 'p', int version = 1}) {
      final now = DateTime.utc(2025, 1, 1, 8);
      return domain.Person(
        id: id,
        name: 'Ana',
        birthdayMonth: 10,
        birthdayDay: 15,
        createdAt: now,
        updatedAt: now,
        version: version,
      );
    }

    setUp(() {
      db = AppDatabase(NativeDatabase.memory());
      repo = DriftPeopleRepository(db);
    });

    tearDown(() async {
      await db.close();
    });

    test('deletePerson tombstones the row and bumps the sync version', () async {
      await repo.savePerson(person(version: 3));

      await repo.deletePerson('p');

      final rows = await db.select(db.persons).get();
      expect(rows, hasLength(1));
      expect(rows.single.deletedAt, isNotNull);
      expect(rows.single.version, 4);
      // Tombstoned rows must no longer surface through the repository API.
      expect(await repo.getPeople(), isEmpty);
      expect(await repo.getPerson('p'), isNull);
    });

    test('savePerson never resurrects a tombstoned row', () async {
      await repo.savePerson(person());
      await repo.deletePerson('p');

      // A later save touching the same id must keep the tombstone, not force
      // deletedAt back to null (audit F-1).
      await repo.savePerson(person());

      final row = await (db.select(db.persons)
            ..where((r) => r.id.equals('p')))
          .getSingle();
      expect(row.deletedAt, isNotNull);
    });
  });

  group('DriftDeliveryEventsRepository (audit 03 P1-1)', () {
    late AppDatabase db;
    late DriftDeliveryEventsRepository repo;

    setUp(() {
      db = AppDatabase(NativeDatabase.memory());
      repo = DriftDeliveryEventsRepository(db);
    });

    tearDown(() async {
      await db.close();
    });

    test('records persist and latestHandoffForBirthday returns newest', () async {
      final early = DateTime.utc(2026, 1, 1, 9);
      final late = DateTime.utc(2026, 1, 2, 9);

      expect(await repo.latestHandoffForBirthday('b1'), isNull);

      await repo.recordHandoff(
        birthdayId: 'b1',
        channel: DeliveryChannel.whatsapp,
        at: early,
      );
      await repo.recordHandoff(
        birthdayId: 'b1',
        channel: DeliveryChannel.sms,
        at: late,
      );
      await repo.recordHandoff(
        birthdayId: 'b2',
        channel: DeliveryChannel.share,
        at: early,
      );

      // Newest wins, and the channel is preserved exactly as launched.
      final latest = await repo.latestHandoffForBirthday('b1');
      expect(latest?.channel, DeliveryChannel.sms);
      expect(latest?.at, late);

      final all = await repo.watchHandoffs().first;
      expect(all, hasLength(3));
      final b2 = all.singleWhere((h) => h.birthdayId == 'b2');
      expect(b2.channel, DeliveryChannel.share);
    });
  });
}