import 'package:ai_birthday/core/database/app_database.dart' hide Person;
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/features/people/application/person_service.dart';
import 'package:ai_birthday/features/people/data/person_repository.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/person_input.dart';
import 'package:ai_birthday/features/people/domain/person_input_validator.dart';
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  late AppDatabase db;
  late PersonService service;
  var nextId = 0;

  setUp(() {
    nextId = 0;
    db = AppDatabase(NativeDatabase.memory());
    service = PersonService(
      DriftPeopleStore(db, now: () => DateTime.utc(2025, 6, 15, 9)),
      const PersonInputValidator(),
      const NoopLogger(),
      now: () => DateTime.utc(2025, 6, 15, 9),
      newId: () => 'person-${nextId++}',
    );
  });

  tearDown(() async => db.close());

  const filled = PersonDraft(
    name: 'Ada',
    birthdayMonth: 12,
    birthdayDay: 10,
    phoneNumber: '+14155550100',
    email: 'ada@example.com',
    notes: 'Loves tea',
    importantFacts: ['Plays chess', 'Has a cat'],
    relationship: RelationshipCategory.colleague,
  );

  group('clearing optional fields (A1-004)', () {
    test(
      'editing with empty phone/email/notes/facts clears them in SQLite',
      () async {
        final created = await service.create(filled);
        expect((await service.getById(created.id))!.phoneNumber, isNotNull);

        final cleared = PersonDraft(
          name: 'Ada',
          birthdayMonth: 12,
          birthdayDay: 10,
          relationship: RelationshipCategory.colleague,
          // phone/email/notes intentionally null, facts empty
        );
        final updated = await service.update(created, cleared);
        expect(updated.phoneNumber, isNull);
        expect(updated.email, isNull);
        expect(updated.notes, isNull);
        expect(updated.importantFacts, isEmpty);

        final reloaded = (await service.getById(created.id))!;
        expect(reloaded.phoneNumber, isNull);
        expect(reloaded.email, isNull);
        expect(reloaded.notes, isNull);
        expect(reloaded.importantFacts, isEmpty);
        expect(reloaded.version, 2);
      },
    );

    test('non-empty values still update and persist', () async {
      final created = await service.create(filled);
      final updated = await service.update(
        created,
        filled.copyWith(phoneNumber: '+14155550111', notes: 'Now loves coffee'),
      );
      final reloaded = (await service.getById(created.id))!;
      expect(updated.phoneNumber, '+14155550111');
      expect(reloaded.phoneNumber, '+14155550111');
      expect(reloaded.notes, 'Now loves coffee');
      expect(reloaded.email, 'ada@example.com');
    });

    test('PersonDraft.copyWith supports explicit clear flags', () {
      final draft = filled.copyWith(
        clearPhoneNumber: true,
        clearEmail: true,
        clearNotes: true,
        importantFacts: const [],
      );
      expect(draft.phoneNumber, isNull);
      expect(draft.email, isNull);
      expect(draft.notes, isNull);
      expect(draft.importantFacts, isEmpty);
      // Omitted arguments still mean "keep".
      expect(filled.copyWith(name: 'X').phoneNumber, '+14155550100');
    });

    test('relationship category round-trips every enum value', () async {
      for (final category in RelationshipCategory.values) {
        final person = await service.create(
          filled.copyWith(relationship: category),
        );
        expect((await service.getById(person.id))!.relationship, category);
      }
    });
  });

  group('per-row import (A1-006)', () {
    test(
      'valid rows import and invalid rows are reported individually',
      () async {
        final result = await service.importValidRows([
          const PersonDraft(name: 'Good One', birthdayMonth: 3, birthdayDay: 4),
          const PersonDraft(
            name: 'Bad Date',
            birthdayMonth: 2,
            birthdayDay: 30,
          ),
          const PersonDraft(name: '', birthdayMonth: 5, birthdayDay: 6),
          const PersonDraft(name: 'Good Two', birthdayMonth: 7, birthdayDay: 8),
        ]);

        expect(result.imported.map((p) => p.name), ['Good One', 'Good Two']);
        expect(result.failures.map((f) => f.index), [1, 2]);
        expect(result.failures.first.name, 'Bad Date');
        expect(result.failures.every((f) => f.reasons.isNotEmpty), isTrue);

        final stored = await service.getAll();
        expect(stored.map((p) => p.name), ['Good One', 'Good Two']);
        expect(await db.select(db.birthdays).get(), hasLength(2));
      },
    );

    test('all-invalid input imports nothing and reports every row', () async {
      final result = await service.importValidRows([
        const PersonDraft(name: '', birthdayMonth: 1, birthdayDay: 1),
        const PersonDraft(name: 'No date'),
      ]);
      expect(result.imported, isEmpty);
      expect(result.failures, hasLength(2));
      expect(await db.select(db.persons).get(), isEmpty);
    });

    test('importBatch stays strict: one bad row rejects the batch', () async {
      await expectLater(
        service.importBatch([
          const PersonDraft(name: 'Good', birthdayMonth: 3, birthdayDay: 4),
          const PersonDraft(name: 'Bad', birthdayMonth: 2, birthdayDay: 30),
        ]),
        throwsA(anything),
      );
      expect(await db.select(db.persons).get(), isEmpty);
    });
  });
}
