import 'package:ai_birthday/core/database/app_database.dart';
import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  late AppDatabase db;

  setUp(() {
    db = AppDatabase(NativeDatabase.memory());
  });

  tearDown(() => db.close());

  group('AppDatabase (in-memory)', () {
    test('inserts and reads back a person row', () async {
      final now = DateTime.utc(2026, 9, 24);
      await db
          .into(db.persons)
          .insert(
            PersonsCompanion.insert(
              id: 'p-1',
              name: 'Ananya',
              birthdayMonth: 5,
              birthdayDay: 17,
              relationship: 'Sister',
              relationshipCloseness: 'family',
              preferredLanguage: 'en',
              preferredTone: 'warm',
              importantFacts: '[]',
              preferredDeliveryChannel: 'whatsapp',
              autoSendPolicy: 'manualOnly',
              createdAt: now,
              updatedAt: now,
              version: 1,
            ),
          );

      final rows = await db.select(db.persons).get();
      expect(rows, hasLength(1));
      expect(rows.single.name, 'Ananya');
      expect(rows.single.birthdayMonth, 5);
      expect(rows.single.version, 1);
    });

    test('sync envelope columns persist and can be queried', () async {
      final now = DateTime.utc(2026, 9, 24);
      await db.batch(
        (b) => b.insertAll(db.persons, [
          PersonsCompanion.insert(
            id: 'p-1',
            name: 'Rahul',
            birthdayMonth: 9,
            birthdayDay: 1,
            relationship: 'Friend',
            relationshipCloseness: 'friend',
            preferredLanguage: 'en',
            preferredTone: 'funny',
            importantFacts: '["loves cricket"]',
            preferredDeliveryChannel: 'whatsapp',
            autoSendPolicy: 'manualOnly',
            createdAt: now,
            updatedAt: now,
            version: 3,
          ),
          PersonsCompanion.insert(
            id: 'p-2',
            name: 'Mia',
            birthdayMonth: 2,
            birthdayDay: 29,
            relationship: 'Friend',
            relationshipCloseness: 'goodFriend',
            preferredLanguage: 'en',
            preferredTone: 'emotional',
            importantFacts: '[]',
            preferredDeliveryChannel: 'none',
            autoSendPolicy: 'manualOnly',
            createdAt: now,
            updatedAt: now,
            version: 1,
            deletedAt: Value(now),
          ),
        ]),
      );

      final active = await db.select(db.persons).get();
      expect(active, hasLength(2));

      final tombstones = await (db.select(
        db.persons,
      )..where((p) => p.deletedAt.isNotNull())).get();
      expect(tombstones, hasLength(1));
      expect(tombstones.single.id, 'p-2');
    });

    test('version is unique per row and updates replace the row', () async {
      final now = DateTime.utc(2026, 9, 24);
      await db
          .into(db.persons)
          .insert(
            PersonsCompanion.insert(
              id: 'p-1',
              name: 'Ananya',
              birthdayMonth: 5,
              birthdayDay: 17,
              relationship: 'Sister',
              relationshipCloseness: 'family',
              preferredLanguage: 'en',
              preferredTone: 'warm',
              importantFacts: '[]',
              preferredDeliveryChannel: 'whatsapp',
              autoSendPolicy: 'manualOnly',
              createdAt: now,
              updatedAt: now,
              version: 1,
            ),
          );

      await (db.update(db.persons)..where((p) => p.id.equals('p-1'))).write(
        PersonsCompanion(
          updatedAt: Value(now.add(const Duration(days: 1))),
          version: const Value(2),
        ),
      );

      final rows = await db.select(db.persons).get();
      expect(rows.single.version, 2);
      expect(rows, hasLength(1));
    });
  });
}
