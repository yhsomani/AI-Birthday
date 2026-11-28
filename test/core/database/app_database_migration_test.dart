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

import 'package:drift/drift.dart' show Value;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:timezone/standalone.dart' as tz;
import 'package:timezone/data/latest_all.dart' as tz_data;

import 'package:ai_birthday/core/database/app_database.dart' as appdb;
import 'package:ai_birthday/core/database/drift_repositories.dart';
import 'package:ai_birthday/features/jobs/data/jobs_repository.dart';
import 'package:ai_birthday/features/jobs/domain/job.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/people/data/drift_people_repository.dart';
import 'package:ai_birthday/features/birthdays/domain/birthday_engine.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
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

  test('v4 -> v9 adds missing tables and preserves existing data', () async {
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
    // runs every upgrade through v10, including deleted_at tombstones.
    final db = appdb.AppDatabase(NativeDatabase(dbFile));
    expect(
      (await db.customSelect('PRAGMA user_version').get()).single.read<int>(
        'user_version',
      ),
      10,
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
    'v7 and v8 -> v9 replace the shipped person-only unique index safely',
    () async {
      Future<void> verifyUpgrade(int oldVersion) async {
        final oldFile = File('${tempDir.path}/v$oldVersion.db');
        final old = appdb.AppDatabase(NativeDatabase(oldFile));
        final now = DateTime.utc(2026, 1, 1);
        await old
            .into(old.birthdays)
            .insert(
              appdb.BirthdaysCompanion.insert(
                id: 'b-existing',
                personId: 'p-existing',
                cycleYear: 2025,
                date: DateTime.utc(2025, 10, 15),
                status: 'handedOff',
                draftId: const Value('d-existing'),
                createdAt: now,
                updatedAt: now,
              ),
            );
        await old
            .into(old.messageDrafts)
            .insert(
              appdb.MessageDraftsCompanion.insert(
                id: 'd-existing',
                birthdayId: 'b-existing',
                personId: 'p-existing',
                body: 'Existing cycle draft',
                tone: 'warm',
                length: 'standard',
                status: 'ready',
                providerType: 'manual',
                createdAt: now,
                updatedAt: now,
              ),
            );
        await DriftDeliveryEventsRepository(old).recordHandoff(
          birthdayId: 'b-existing',
          draftId: 'd-existing',
          draftRevision: 4,
          bodyHash: 'sha256-existing',
          channel: DeliveryChannel.sms,
          at: now,
        );
        await DriftJobsRepository(old).enqueue(
          type: JobTypes.aiGenerate,
          subjectId: 'b-existing',
          payload: '{"birthdayId":"b-existing"}',
        );

        await old.customStatement(
          'DROP INDEX idx_birthdays_person_cycle_year_unique',
        );
        await old.customStatement(
          'CREATE UNIQUE INDEX idx_birthdays_person_id_unique ON birthdays (person_id)',
        );
        if (oldVersion == 7) {
          await old.customStatement(
            'ALTER TABLE delivery_events DROP COLUMN body_hash',
          );
          await old.customStatement(
            'ALTER TABLE delivery_events DROP COLUMN draft_revision',
          );
        }
        await old.customStatement('PRAGMA user_version = $oldVersion');
        await old.close();

        final current = appdb.AppDatabase(NativeDatabase(oldFile));
        expect(
          (await current.customSelect('PRAGMA user_version').get()).single
              .read<int>('user_version'),
          10,
        );
        expect(
          await current
              .customSelect(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_birthdays_person_id_unique'",
              )
              .get(),
          isEmpty,
        );
        final indexColumns = await current
            .customSelect(
              'PRAGMA index_info(idx_birthdays_person_cycle_year_unique)',
            )
            .get();
        expect(indexColumns.map((row) => row.read<String>('name')).toList(), [
          'person_id',
          'cycle_year',
        ]);
        expect(
          (await current.select(current.birthdays).get()).single.cycleYear,
          2025,
        );
        expect(
          (await current.select(current.messageDrafts).get()).single.birthdayId,
          'b-existing',
        );
        final handoff = await DriftDeliveryEventsRepository(
          current,
        ).latestHandoffForBirthday('b-existing');
        expect(handoff?.draftId, 'd-existing');
        expect(handoff?.draftRevision, oldVersion == 7 ? null : 4);
        expect(handoff?.bodyHash, oldVersion == 7 ? null : 'sha256-existing');
        expect(
          (await current.select(current.jobs).get()).single.subjectId,
          'b-existing',
        );

        await DriftBirthdaysRepository(current).saveBirthday(
          Birthday(
            id: 'b-next-cycle',
            personId: 'p-existing',
            cycleYear: 2026,
            date: DateTime.utc(2026, 10, 15),
            status: BirthdayStatus.upcoming,
            createdAt: now,
            updatedAt: now,
          ),
        );
        expect(
          await DriftBirthdaysRepository(current).getBirthday('b-existing'),
          isNotNull,
        );
        expect(
          await DriftBirthdaysRepository(current).getBirthday('b-next-cycle'),
          isNotNull,
        );
        await current.close();
      }

      await verifyUpgrade(7);
      await verifyUpgrade(8);
    },
  );

  test('v6 -> v9 re-derives calendar dates, dedupes birthdays, adds revision', () async {
    tz_data.initializeTimeZones();
    int unix(tz.TZDateTime d) => d.millisecondsSinceEpoch ~/ 1000;
    final created = DateTime.utc(2026, 1, 1);

    // Genuine v6 schema: INTEGER birthdays.date (unix seconds), no
    // message_drafts.revision, non-unique person index.
    final v6 = appdb.AppDatabase(NativeDatabase(dbFile));
    for (final (id, month, day, zone, leap) in [
      ('p-tokyo', 3, 5, 'Asia/Tokyo', null), // ahead of any device west of it
      ('p-la', 3, 5, 'America/Los_Angeles', null), // behind UTC
      ('p-kolkata', 3, 5, 'Asia/Kolkata', null), // +5:30
      ('p-leap-mar1', 2, 29, 'Asia/Tokyo', 'mar1'),
      ('p-leap-feb28', 2, 29, null, 'feb28'),
      ('p-dup', 10, 15, 'Asia/Tokyo', null),
      ('p-same-cycle', 11, 3, null, null),
    ]) {
      await DriftPeopleRepository(v6).savePerson(
        Person(
          id: id,
          name: id,
          birthdayMonth: month,
          birthdayDay: day,
          timezone: zone,
          leapDayResolution: leap == null
              ? null
              : LeapDayResolution.fromString(leap),
          createdAt: created,
          updatedAt: created,
        ),
      );
    }
    await v6.customStatement('DROP TABLE birthdays');
    await v6.customStatement('DROP TABLE message_drafts');
    await v6.customStatement(
      'CREATE TABLE birthdays (id TEXT NOT NULL, person_id TEXT NOT NULL, '
      'cycle_year INTEGER NOT NULL, date INTEGER NOT NULL, status TEXT NOT NULL, '
      'draft_id TEXT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, '
      'PRIMARY KEY (id))',
    );
    await v6.customStatement(
      'CREATE INDEX idx_birthdays_person_id ON birthdays (person_id)',
    );
    await v6.customStatement(
      'CREATE TABLE message_drafts (id TEXT NOT NULL, birthday_id TEXT NOT NULL, '
      'person_id TEXT NOT NULL, body TEXT NOT NULL, tone TEXT NOT NULL, '
      'length TEXT NOT NULL, status TEXT NOT NULL, provider_type TEXT NOT NULL, '
      'variation_index INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, '
      'updated_at INTEGER NOT NULL, PRIMARY KEY (id))',
    );
    Future<void> insertBirthday(
      String id,
      String personId,
      int cycleYear,
      int dateSeconds, {
      String? draftId,
      int updatedAt = 1767225600,
    }) => v6.customStatement(
      'INSERT INTO birthdays VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
      [
        id,
        personId,
        cycleYear,
        dateSeconds,
        'upcoming',
        draftId,
        1767225600,
        updatedAt,
      ],
    );

    // The old engine stored midnight in the recipient zone as an instant.
    await insertBirthday(
      'b-tokyo',
      'p-tokyo',
      2026,
      unix(tz.TZDateTime(tz.getLocation('Asia/Tokyo'), 2026, 3, 5)),
      draftId: 'b-tokyo',
    );
    await insertBirthday(
      'b-la',
      'p-la',
      2026,
      unix(tz.TZDateTime(tz.getLocation('America/Los_Angeles'), 2026, 3, 5)),
    );
    await insertBirthday(
      'b-kolkata',
      'p-kolkata',
      2026,
      unix(tz.TZDateTime(tz.getLocation('Asia/Kolkata'), 2026, 3, 5)),
    );
    await insertBirthday(
      'b-leap-mar1',
      'p-leap-mar1',
      2027,
      unix(tz.TZDateTime(tz.getLocation('Asia/Tokyo'), 2027, 3, 1)),
    );
    await insertBirthday(
      'b-leap-feb28',
      'p-leap-feb28',
      2027,
      DateTime(2027, 2, 28).millisecondsSinceEpoch ~/ 1000,
    );
    // Orphan (person row missing): falls back to the device-local day.
    final orphanInstant = DateTime(2026, 7, 9).millisecondsSinceEpoch ~/ 1000;
    await insertBirthday('b-orphan', 'p-gone', 2026, orphanInstant);
    // Duplicates for one person: newest updated_at wins.
    await insertBirthday(
      'b-dup-old',
      'p-dup',
      2025,
      unix(tz.TZDateTime(tz.getLocation('Asia/Tokyo'), 2025, 10, 15)),
      draftId: 'd-dup-old',
      updatedAt: 1700000000,
    );
    await insertBirthday(
      'b-dup-new',
      'p-dup',
      2026,
      unix(tz.TZDateTime(tz.getLocation('Asia/Tokyo'), 2026, 10, 15)),
      updatedAt: 1767225600,
    );
    await insertBirthday(
      'b-same-old',
      'p-same-cycle',
      2026,
      DateTime(2026, 11, 3).millisecondsSinceEpoch ~/ 1000,
      draftId: 'd-same-old',
      updatedAt: 1700000000,
    );
    await insertBirthday(
      'b-same-new',
      'p-same-cycle',
      2026,
      DateTime(2026, 11, 3).millisecondsSinceEpoch ~/ 1000,
      updatedAt: 1767225600,
    );

    for (final (id, birthdayId, status) in [
      ('b-tokyo', 'b-tokyo', 'draft'),
      ('d-dup-old', 'b-dup-old', 'draft'),
      ('d-dup-sent', 'b-dup-old', 'confirmedSent'),
      ('d-same-old', 'b-same-old', 'draft'),
    ]) {
      await v6.customStatement(
        "INSERT INTO message_drafts VALUES (?, ?, 'p', ?, 'warm', 'standard', ?, 'manual', 0, 1767225600, 1767225600)",
        [id, birthdayId, 'body of $id', status],
      );
    }
    await v6.customStatement(
      "INSERT INTO delivery_events (id, birthday_id, channel, handed_off_at) "
      "VALUES ('ev-old', 'b-dup-old', 'whatsapp', 1767225600)",
    );
    await v6.customStatement(
      "INSERT INTO delivery_events (id, birthday_id, channel, handed_off_at) "
      "VALUES ('ev-same', 'b-same-old', 'share', 1767225600)",
    );
    await v6.customStatement(
      "INSERT INTO jobs (id, type, status, subject_id, created_at) "
      "VALUES ('job-old', 'ai_generate', 'queued', 'b-dup-old', 1767225600)",
    );
    await v6.customStatement(
      "INSERT INTO jobs (id, type, status, subject_id, created_at) "
      "VALUES ('job-keep', 'ai_generate', 'queued', 'b-tokyo', 1767225600)",
    );
    await v6.customStatement(
      "INSERT INTO jobs (id, type, status, subject_id, created_at) "
      "VALUES ('job-same', 'ai_generate', 'queued', 'b-same-old', 1767225600)",
    );
    await v6.customStatement('PRAGMA user_version = 6');
    await v6.close();

    final db = appdb.AppDatabase(NativeDatabase(dbFile));
    final repo = DriftBirthdaysRepository(db);
    expect(
      (await db.customSelect('PRAGMA user_version').get()).single.read<int>(
        'user_version',
      ),
      10,
    );

    (int, int, int) ymd(DateTime d) => (d.year, d.month, d.day);
    Future<(int, int, int)> dateOf(String id) async =>
        ymd((await repo.getBirthday(id))!.date);

    // Calendar day is derived from the person, not the device zone.
    expect(await dateOf('b-tokyo'), (2026, 3, 5));
    expect(await dateOf('b-la'), (2026, 3, 5));
    expect(await dateOf('b-kolkata'), (2026, 3, 5));
    expect(await dateOf('b-leap-mar1'), (2027, 3, 1));
    expect(await dateOf('b-leap-feb28'), (2027, 2, 28));
    expect(await dateOf('b-orphan'), (2026, 7, 9));
    expect(await dateOf('b-dup-new'), (2026, 10, 15));
    expect(await dateOf('b-dup-old'), (2025, 10, 15));

    // Different cycles remain distinct; duplicates within the same cycle
    // still converge on the newest row.
    expect((await repo.getBirthday('b-dup-old'))!.cycleYear, 2025);
    expect(await repo.getBirthday('b-same-old'), isNull);
    final all = await repo.getBirthdays();
    expect(
      all.map((b) => (b.personId, b.cycleYear)).toSet().length,
      all.length,
    );
    expect(all, hasLength(9));

    // Cross-cycle drafts, history, and jobs retain the original cycle id.
    // Same-cycle duplicate dependents still point to the surviving row.
    final draftRows = await db.select(db.messageDrafts).get();
    expect(
      {for (final d in draftRows) d.id: d.birthdayId},
      {
        'b-tokyo': 'b-tokyo',
        'd-dup-old': 'b-dup-old',
        'd-dup-sent': 'b-dup-old',
        'd-same-old': 'b-same-new',
      },
    );
    expect(draftRows.every((d) => d.revision == 0), isTrue);
    expect(
      {
        for (final event in await db.select(db.deliveryEvents).get())
          event.id: event.birthdayId,
      },
      {'ev-old': 'b-dup-old', 'ev-same': 'b-same-new'},
    );
    expect(
      {for (final job in await db.select(db.jobs).get()) job.id: job.subjectId},
      {'job-old': 'b-dup-old', 'job-keep': 'b-tokyo'},
    );
    expect((await repo.getBirthday('b-tokyo'))!.draftId, 'b-tokyo');
    expect(
      (await repo.getBirthday('b-tokyo'))!.status,
      BirthdayStatus.upcoming,
    );

    // Schema: TEXT date column and a UNIQUE person/cycle index.
    final dateType =
        (await db.customSelect('PRAGMA table_info(birthdays)').get())
            .firstWhere((r) => r.read<String>('name') == 'date')
            .read<String>('type');
    expect(dateType.toUpperCase(), 'TEXT');
    await expectLater(
      repo.saveBirthday(
        Birthday(
          id: 'b-second',
          personId: 'p-tokyo',
          cycleYear: 2026,
          date: DateTime(2026, 3, 5),
          createdAt: created,
          updatedAt: created,
        ),
      ),
      throwsA(anything),
    );
    await repo.saveBirthday(
      Birthday(
        id: 'b-next-cycle',
        personId: 'p-tokyo',
        cycleYear: 2027,
        date: DateTime(2027, 3, 5),
        createdAt: created,
        updatedAt: created,
      ),
    );
    expect(await repo.getBirthday('b-next-cycle'), isNotNull);

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
      await DriftPeopleRepository(oldDb).savePerson(person);
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

  test(
    'v9 -> v10 adds deleted_at tombstones, preserves rows, and enables foreign_keys',
    () async {
      final v9 = appdb.AppDatabase(NativeDatabase(dbFile));
      final now = DateTime.utc(2026, 2, 1);
      await v9
          .into(v9.birthdays)
          .insert(
            appdb.BirthdaysCompanion.insert(
              id: 'b-v9',
              personId: 'p-v9',
              cycleYear: 2026,
              date: DateTime.utc(2026, 5, 12),
              status: 'upcoming',
              createdAt: now,
              updatedAt: now,
            ),
          );
      await v9
          .into(v9.messageDrafts)
          .insert(
            appdb.MessageDraftsCompanion.insert(
              id: 'd-v9',
              birthdayId: 'b-v9',
              personId: 'p-v9',
              body: 'Happy birthday!',
              tone: 'warm',
              length: 'standard',
              status: 'draft',
              providerType: 'manual',
              createdAt: now,
              updatedAt: now,
            ),
          );
      await v9.customStatement('ALTER TABLE birthdays DROP COLUMN deleted_at');
      await v9.customStatement(
        'ALTER TABLE message_drafts DROP COLUMN deleted_at',
      );
      await v9.customStatement('PRAGMA user_version = 9');
      await v9.close();

      final db = appdb.AppDatabase(NativeDatabase(dbFile));
      expect(
        (await db.customSelect('PRAGMA user_version').get()).single.read<int>(
          'user_version',
        ),
        10,
      );
      expect(
        (await db.customSelect('PRAGMA foreign_keys').get()).single.read<int>(
          'foreign_keys',
        ),
        1,
      );

      final birthdayCols =
          (await db.customSelect('PRAGMA table_info(birthdays)').get())
              .map((r) => r.read<String>('name'))
              .toSet();
      final draftCols =
          (await db.customSelect('PRAGMA table_info(message_drafts)').get())
              .map((r) => r.read<String>('name'))
              .toSet();
      expect(birthdayCols, contains('deleted_at'));
      expect(draftCols, contains('deleted_at'));

      final birthdaysRepo = DriftBirthdaysRepository(db);
      final draftsRepo = DriftDraftsRepository(db);
      expect((await birthdaysRepo.getBirthday('b-v9'))?.cycleYear, 2026);
      expect((await draftsRepo.getDraft('d-v9'))?.body, 'Happy birthday!');

      // Tombstoned birthday/draft rows are filtered out of active queries.
      await db.customStatement(
        "UPDATE birthdays SET deleted_at = 1769904000 WHERE id = 'b-v9'",
      );
      await db.customStatement(
        "UPDATE message_drafts SET deleted_at = 1769904000 WHERE id = 'd-v9'",
      );
      expect(await birthdaysRepo.getBirthday('b-v9'), isNull);
      expect(await birthdaysRepo.getBirthdays(), isEmpty);
      expect(await draftsRepo.getDraft('d-v9'), isNull);
      expect(await draftsRepo.getAllDrafts(), isEmpty);
      await db.close();
    },
  );
}
