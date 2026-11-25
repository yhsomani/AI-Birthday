/// Drift schema migration verification.
///
/// schemaVersion 5 adds the durable `jobs` table via `createTable` with no
/// destructive operations. This test reproduces a genuine v4 database — the
/// v4 schema had the identical DDL for every pre-existing table, and no
/// `jobs` table — then reopens it with the current schema and proves the
/// v4 -> v5 upgrade applies cleanly, the jobs table is fully functional
/// through the repository contract, and pre-existing rows survive untouched.
library;

import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/core/database/app_database.dart' as appdb;
import 'package:ai_birthday/features/jobs/data/jobs_repository.dart';
import 'package:ai_birthday/features/jobs/domain/job.dart';
import 'package:ai_birthday/features/people/data/person_repository.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';

void main() {
  late Directory tempDir;
  late File dbFile;

  setUp(() {
    final root = Directory('.dart_tool/ai_birthday_migration_tests');
    root.createSync(recursive: true);
    tempDir = Directory(
      '${root.path}/run-${DateTime.now().microsecondsSinceEpoch}',
    )..createSync(recursive: true);
    dbFile = File('${tempDir.path}/migration_test.db');
  });

  tearDown(() {
    if (tempDir.existsSync()) {
      tempDir.deleteSync(recursive: true);
    }
  });

  test('v4 -> v6 adds missing tables and preserves existing data', () async {
    // Phase 1: build a genuine v4 database. v4's createAll produced the same
    // DDL for every pre-existing table; only `jobs` did not exist back then,
    // so drop it and pin user_version at 4 to reproduce that exact state.
    final v4 = appdb.AppDatabase(NativeDatabase(dbFile));
    await v4
        .into(v4.birthdays)
        .insert(
          appdb.BirthdaysCompanion.insert(
            id: 'b-keep',
            personId: 'p-keep',
            cycleYear: 2026,
            date: DateTime(2026, 3, 14),
            status: 'upcoming',
            createdAt: DateTime(2026, 1, 1),
            updatedAt: DateTime(2026, 1, 1),
          ),
        );
    await v4.customStatement('DROP TABLE jobs');
    await v4.customStatement('PRAGMA user_version = 4');
    expect(
      (await v4.customSelect('PRAGMA user_version').get()).single.read<int>(
        'user_version',
      ),
      4,
    );
    await v4.close();

    // Phase 2: reopen with the current schema. Drift sees user_version 4 and
    // runs onUpgrade(4 -> 6), including the idempotent delivery migration.
    final db = appdb.AppDatabase(NativeDatabase(dbFile));
    expect(
      (await db.customSelect('PRAGMA user_version').get()).single.read<int>(
        'user_version',
      ),
      6,
    );

    // The jobs table is functional end-to-end through the repository.
    final repo = DriftJobsRepository(db);
    final job = await repo.enqueue(
      type: JobTypes.aiGenerate,
      subjectId: 'b-keep',
      payload: '{}',
    );
    final row = (await repo.getById(job.id))!;
    expect(row.type, JobTypes.aiGenerate);
    expect(row.subjectId, 'b-keep');
    expect(row.status, JobStatus.queued);

    // Pre-existing data survived the upgrade untouched.
    final kept = await (db.select(
      db.birthdays,
    )..where((t) => t.id.equals('b-keep'))).getSingle();
    expect(kept.personId, 'p-keep');
    expect(kept.cycleYear, 2026);
    expect(kept.status, 'upcoming');

    // There is still exactly one jobs table: the retry/pruning lifecycle
    // depends on status transitions, not duplicate tables.
    expect(
      (await db
              .customSelect(
                "SELECT COUNT(*) AS n FROM sqlite_master WHERE type='table' AND name='jobs'",
              )
              .get())
          .single
          .read<int>('n'),
      1,
    );

    await db.close();
  });

  test(
    'v5 -> v6 preserves delivery history and defaults new outcome',
    () async {
      final v5 = appdb.AppDatabase(NativeDatabase(dbFile));
      await v5.customStatement(
        'ALTER TABLE delivery_events RENAME TO delivery_events_v6',
      );
      await v5.customStatement('''
      CREATE TABLE delivery_events (
        id TEXT NOT NULL PRIMARY KEY,
        birthday_id TEXT NOT NULL,
        channel TEXT NOT NULL,
        handed_off_at INTEGER NOT NULL
      )
    ''');
      await v5.customStatement('''
      INSERT INTO delivery_events (id, birthday_id, channel, handed_off_at)
      VALUES ('event-keep', 'b-keep', 'whatsapp', 1767225600000)
    ''');
      await v5.customStatement('DROP TABLE delivery_events_v6');
      await v5.customStatement('PRAGMA user_version = 5');
      await v5.close();

      final db = appdb.AppDatabase(NativeDatabase(dbFile));
      final columns = await db
          .customSelect('PRAGMA table_info(delivery_events)')
          .get();
      final names = columns.map((row) => row.read<String>('name')).toSet();
      expect(names, containsAll(['draft_id', 'outcome']));

      final event = await (db.select(
        db.deliveryEvents,
      )..where((row) => row.id.equals('event-keep'))).getSingle();
      expect(event.birthdayId, 'b-keep');
      expect(event.channel, 'whatsapp');
      expect(event.draftId, isNull);
      expect(event.outcome, 'launched');
      await db.close();
    },
  );

  test(
    'legacy compatibility migration preserves birthday, dates, and notes',
    () async {
      final oldDb = appdb.AppDatabase(NativeDatabase(dbFile));
      final now = DateTime.utc(2025, 1, 1);
      final person = Person(
        id: 'legacy-person',
        name: 'Birthday default set to today',
        birthdayMonth: 12,
        birthdayDay: 31,
        notes: 'Imported from phone contacts; birthday default set to today',
        createdAt: now,
        updatedAt: now,
      );
      await DriftPeopleStore(oldDb).save(person);
      await oldDb
          .into(oldDb.birthdays)
          .insert(
            appdb.BirthdaysCompanion.insert(
              id: 'legacy-birthday',
              personId: person.id,
              cycleYear: 2025,
              date: DateTime(2025, 12, 31),
              status: 'upcoming',
              createdAt: now,
              updatedAt: now,
            ),
          );
      await oldDb.customStatement('DROP TABLE jobs');
      await oldDb.customStatement('DROP TABLE delivery_events');
      await oldDb.customStatement('PRAGMA user_version = 2');
      await oldDb.close();

      final migrated = appdb.AppDatabase(NativeDatabase(dbFile));
      final savedPerson = await (migrated.select(
        migrated.persons,
      )..where((row) => row.id.equals(person.id))).getSingle();
      expect(savedPerson.birthdayMonth, 12);
      expect(savedPerson.birthdayDay, 31);
      expect(savedPerson.notes, person.notes);

      final savedBirthday = await (migrated.select(
        migrated.birthdays,
      )..where((row) => row.id.equals('legacy-birthday'))).getSingleOrNull();
      expect(savedBirthday, isNotNull);
      expect(savedBirthday!.personId, person.id);
      await migrated.close();
    },
  );
}
