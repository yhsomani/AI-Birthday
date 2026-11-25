import 'package:ai_birthday/core/database/app_database.dart' hide Person;
import 'package:ai_birthday/core/database/drift_repositories.dart';
import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';
import 'package:ai_birthday/features/people/application/person_service.dart';
import 'package:drift/drift.dart' show Value;
import 'package:ai_birthday/features/people/data/person_repository.dart';
import 'package:ai_birthday/features/people/domain/person.dart';
import 'package:ai_birthday/features/people/domain/person_enums.dart';
import 'package:ai_birthday/features/people/domain/person_input.dart';
import 'package:ai_birthday/features/people/domain/person_input_validator.dart';
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  late AppDatabase db;
  late PersonService service;
  final log = RecordingLogger();

  setUp(() {
    db = AppDatabase(NativeDatabase.memory());
    final store = DriftPeopleStore(db, now: () => DateTime.utc(2025, 6, 15, 9));
    service = PersonService(
      store,
      const PersonInputValidator(),
      log,
      now: () => DateTime.utc(2025, 6, 15, 9),
      newId: () => 'fixed-person-id',
    );
  });

  tearDown(() async => db.close());

  PersonDraft validDraft() => const PersonDraft(
    name: 'Grace Hopper',
    birthdayMonth: 12,
    birthdayDay: 9,
  );

  test('create validates, assigns envelope and persists', () async {
    final person = await service.create(validDraft());

    expect(person.id, 'fixed-person-id');
    expect(person.version, 1);
    expect(person.createdAt, DateTime.utc(2025, 6, 15, 9));
    expect(person.autoSendPolicy, AutoSendPolicy.manualOnly);

    final loaded = await service.getById(person.id);
    expect(loaded!.name, 'Grace Hopper');

    final all = await service.getAll();
    expect(all.map((p) => p.id), ['fixed-person-id']);
  });

  test(
    'person and birthday creation roll back together on birthday failure',
    () async {
      await db.customStatement('''
      CREATE TRIGGER reject_birthday BEFORE INSERT ON birthdays
      BEGIN
        SELECT RAISE(ABORT, 'birthday write failed');
      END;
    ''');

      await expectLater(
        service.create(validDraft()),
        throwsA(isA<Exception>()),
      );
      expect(await service.getById('fixed-person-id'), isNull);
      expect(await db.select(db.birthdays).get(), isEmpty);
    },
  );

  test('person edit and birthday update roll back together', () async {
    final created = await service.create(validDraft());
    await db.customStatement('''
      CREATE TRIGGER reject_birthday_update BEFORE UPDATE ON birthdays
      BEGIN
        SELECT RAISE(ABORT, 'birthday update failed');
      END;
    ''');

    await expectLater(
      service.update(created, validDraft().copyWith(name: 'Grace Edited')),
      throwsA(isA<Exception>()),
    );

    final unchanged = await service.getById(created.id);
    expect(unchanged!.name, 'Grace Hopper');
    expect(unchanged.version, 1);
  });

  test('Feb 29 requires an explicit policy before saving', () async {
    await expectLater(
      service.create(
        const PersonDraft(
          name: 'Leap Person',
          birthdayMonth: 2,
          birthdayDay: 29,
        ),
      ),
      throwsA(isA<AppFailure>()),
    );
  });

  test('create throws AppFailure(validation) for an invalid draft', () async {
    await expectLater(
      service.create(
        const PersonDraft(name: '', birthdayMonth: 4, birthdayDay: 31),
      ),
      throwsA(
        isA<AppFailure>()
            .having((f) => f.code, 'code', AppFailureCode.validation)
            .having((f) => f.isRetryable, 'isRetryable', isFalse),
      ),
    );
  });

  test('update bumps version and preserves createdAt', () async {
    final created = await service.create(validDraft());

    final edited = await service.update(
      created,
      validDraft().copyWith(name: 'Grace B. Hopper'),
    );

    expect(edited.version, 2);
    expect(edited.name, 'Grace B. Hopper');
    expect(edited.createdAt, created.createdAt);
    expect(edited.updatedAt, DateTime.utc(2025, 6, 15, 9));

    final reloaded = await service.getById(created.id);
    expect(reloaded!.version, 2);
  });

  test(
    'date change atomically removes active draft/job but keeps sent history',
    () async {
      final created = await service.create(validDraft());
      final birthdayId = 'birthday-${created.id}';
      final now = DateTime.utc(2026, 1, 1);
      for (final status in [DraftStatus.ready, DraftStatus.confirmedSent]) {
        await db
            .into(db.messageDrafts)
            .insert(
              MessageDraftsCompanion.insert(
                id: 'draft-${status.name}',
                birthdayId: birthdayId,
                personId: created.id,
                body: 'Saved message',
                tone: 'warm',
                length: 'standard',
                status: status.name,
                providerType: 'manual',
                createdAt: now,
                updatedAt: now,
              ),
            );
      }
      await db
          .into(db.jobs)
          .insert(
            JobsCompanion.insert(
              id: 'job-old-date',
              type: 'ai_generate',
              status: 'queued',
              subjectId: birthdayId,
              createdAt: now,
              maxAttempts: const Value(1),
            ),
          );

      await service.update(
        created,
        validDraft().copyWith(birthdayMonth: 11, birthdayDay: 8),
      );

      final drafts = await db.select(db.messageDrafts).get();
      expect(drafts.map((row) => row.id), ['draft-confirmedSent']);
      expect(await db.select(db.jobs).get(), isEmpty);
      final birthday = await (db.select(
        db.birthdays,
      )..where((row) => row.id.equals(birthdayId))).getSingle();
      expect(birthday.draftId, isNull);
      expect(
        await DriftDraftsRepository(db).getDraftForBirthday(birthdayId),
        isNull,
      );
    },
  );

  test('update rejects invalid edits without writing', () async {
    final created = await service.create(validDraft());
    await expectLater(
      service.update(created, validDraft().copyWith(name: ' ')),
      throwsA(isA<AppFailure>()),
    );
    final reloaded = await service.getById(created.id);
    expect(reloaded!.name, 'Grace Hopper');
    expect(reloaded.version, 1);
  });

  test('remove soft-deletes so the row is hidden but recoverable', () async {
    final created = await service.create(validDraft());
    await service.remove(created.id);

    final visible = await service.getAll();
    expect(visible, isEmpty);

    // The row itself remains with a tombstone (soft delete).
    final tombstoned = await service.getById(created.id);
    expect(tombstoned, isNotNull);
    expect(tombstoned!.deletedAt, isNotNull);
  });

  test('restore clears the tombstone and brings the person back', () async {
    final created = await service.create(validDraft());
    await service.remove(created.id);

    await service.restore(created.id);

    final visible = await service.getAll();
    expect(visible.map((p) => p.id), ['fixed-person-id']);
    final restored = await service.getById(created.id);
    expect(restored!.deletedAt, isNull);
  });

  test(
    'update allows editing a contact that has no birthday (AUD-010)',
    () async {
      // Contact imported without birthday
      final noBdayPerson = Person(
        id: 'imported-no-bday',
        name: 'No Birthday Contact',
        createdAt: DateTime.utc(2025, 6, 15, 9),
        updatedAt: DateTime.utc(2025, 6, 15, 9),
      );
      final store = DriftPeopleStore(
        db,
        now: () => DateTime.utc(2025, 6, 15, 9),
      );
      await store.save(noBdayPerson);

      // Edit notes and phone without adding birthday
      final updated = await service.update(
        noBdayPerson,
        const PersonDraft(
          name: 'No Birthday Contact',
          phoneNumber: '+1234567890',
          notes: 'Added some notes',
        ),
      );

      expect(updated.phoneNumber, '+1234567890');
      expect(updated.notes, 'Added some notes');
      expect(updated.hasBirthday, isFalse);
    },
  );

  test(
    'create forces conditional automation off in the v1 capability set',
    () async {
      final person = await service.create(
        validDraft().copyWith(
          autoPrepare: true,
          autoSendPolicy: AutoSendPolicy.automaticSmsApproved,
        ),
      );

      expect(person.autoPrepare, isFalse);
      expect(person.autoSendPolicy, AutoSendPolicy.manualOnly);
    },
  );
}
