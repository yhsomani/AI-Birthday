import 'package:ai_birthday/core/database/app_database.dart';
import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/features/people/application/person_service.dart';
import 'package:ai_birthday/features/people/data/person_repository.dart';
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
}
