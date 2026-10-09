/// Drift schema migration verification (background-jobs audit).
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

void main() {
  late Directory tempDir;
  late File dbFile;

  setUp(() {
    tempDir = Directory.systemTemp.createTempSync('ai_birthday_migration_');
    dbFile = File('${tempDir.path}/migration_test.db');
  });

  tearDown(() {
    if (tempDir.existsSync()) {
      tempDir.deleteSync(recursive: true);
    }
  });

  test('v4 -> v5 adds the jobs table and preserves existing data', () async {
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
    // runs onUpgrade(4 -> 5), which must only create the jobs table.
    final db = appdb.AppDatabase(NativeDatabase(dbFile));
    expect(
      (await db.customSelect('PRAGMA user_version').get()).single.read<int>(
        'user_version',
      ),
      5,
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
}
