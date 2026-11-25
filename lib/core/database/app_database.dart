/// Drift/SQLite database foundation.
///
/// Local data is operationally primary (legacy SSOT §13, §19 (PRODUCT.md §16.7)). Firestore is a
/// synchronization target, never the operational store. The sync envelope
/// fields (`id`, `createdAt`, `updatedAt`, `version`, `deletedAt`) are carried
/// per table — only `Persons` currently has the full envelope
/// (`version` + `deletedAt` tombstones); `Birthdays`, `MessageDrafts` and
/// `ReminderSettingsEntries` carry only timestamps.
library;

import 'dart:io' show Platform;
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:drift_flutter/drift_flutter.dart';

part 'app_database.g.dart';

/// Persists a calendar date as `yyyy-MM-dd` text.
///
/// Only the year/month/day fields of the supplied [DateTime] are stored; the
/// instant and time zone are deliberately ignored so the stored day can never
/// shift with the device or recipient time zone. Reading returns a local
/// midnight [DateTime] whose year/month/day are the stored calendar date.
class CalendarDateConverter extends TypeConverter<DateTime, String> {
  const CalendarDateConverter();

  @override
  DateTime fromSql(String fromDb) {
    final parts = fromDb.substring(0, 10).split('-');
    return DateTime(
      int.parse(parts[0]),
      int.parse(parts[1]),
      int.parse(parts[2]),
    );
  }

  @override
  String toSql(DateTime value) => formatCalendarDate(value);
}

/// Formats the year/month/day fields of [value] as `yyyy-MM-dd`.
String formatCalendarDate(DateTime value) {
  final y = value.year.toString().padLeft(4, '0');
  final m = value.month.toString().padLeft(2, '0');
  final d = value.day.toString().padLeft(2, '0');
  return '$y-$m-$d';
}

/// A person (birthday recipient).
///
/// Columns mirror the recipient model (PRODUCT.md §6). Enums and lists are stored as
/// textual encodings; the repository is responsible for mapping to and from
/// the domain model.
class Persons extends Table {
  TextColumn get id => text()();
  TextColumn get name => text()();
  IntColumn get birthdayMonth => integer().nullable()();
  IntColumn get birthdayDay => integer().nullable()();
  IntColumn get birthYear => integer().nullable()();
  TextColumn get leapDayResolution => text().nullable()();
  TextColumn get phoneNumber => text().nullable()();
  TextColumn get email => text().nullable()();
  TextColumn get relationship => text()();
  TextColumn get relationshipCloseness => text()();
  TextColumn get preferredLanguage => text()();
  TextColumn get preferredTone => text()();
  TextColumn get importantFacts => text()();
  TextColumn get notes => text().nullable()();
  TextColumn get preferredDeliveryChannel => text()();
  TextColumn get timezone => text().nullable()();
  BoolColumn get autoPrepare => boolean().withDefault(const Constant(false))();
  TextColumn get autoSendPolicy => text()();
  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get updatedAt => dateTime()();
  IntColumn get version => integer()();
  DateTimeColumn get deletedAt => dateTime().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Tracked birthday event cycles.
class Birthdays extends Table {
  TextColumn get id => text()();
  TextColumn get personId => text()();
  IntColumn get cycleYear => integer()();

  /// Calendar date of the occurrence (`yyyy-MM-dd`), independent of any time
  /// zone. See [CalendarDateConverter].
  TextColumn get date => text().map(const CalendarDateConverter())();
  TextColumn get status => text()();
  TextColumn get draftId => text().nullable()();
  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get updatedAt => dateTime()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Message drafts prepared for birthdays.
class MessageDrafts extends Table {
  TextColumn get id => text()();
  TextColumn get birthdayId => text()();
  TextColumn get personId => text()();
  TextColumn get body => text()();
  TextColumn get tone => text()();
  TextColumn get length => text()();
  TextColumn get status => text()();
  TextColumn get providerType => text()();
  IntColumn get variationIndex => integer().withDefault(const Constant(0))();

  /// Monotonic write counter used for stale-write detection; unlike
  /// `updatedAt` (unix seconds) it distinguishes saves within the same second.
  IntColumn get revision => integer().withDefault(const Constant(0))();
  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get updatedAt => dateTime()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Reminder settings and quiet hours preferences.
class ReminderSettingsEntries extends Table {
  TextColumn get key => text()();
  BoolColumn get enabled => boolean().withDefault(const Constant(false))();
  TextColumn get kinds => text()();
  IntColumn get quietHoursStartMinutes => integer()();
  IntColumn get quietHoursEndMinutes => integer()();

  @override
  Set<Column> get primaryKey => {key};
}

/// Append-only evidence of a manual delivery action and its outcome.
///
/// This is the durable record that a message was handed off, copied or not sent; the app
/// must never infer "opened" from birthday/draft status alone. `channel`
/// stores a [DeliveryChannel] name so History can label the exact app
/// (WhatsApp vs SMS vs Share Sheet) rather than guessing.
class DeliveryEvents extends Table {
  TextColumn get id => text()();
  TextColumn get birthdayId => text()();
  TextColumn get draftId => text().nullable()();
  IntColumn get draftRevision => integer().nullable()();
  TextColumn get bodyHash => text().nullable()();
  TextColumn get channel => text()();
  TextColumn get outcome => text().withDefault(const Constant('launched'))();
  DateTimeColumn get handedOffAt => dateTime()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Durable background-job queue (background-jobs audit).
///
/// One row per durable operation (`ai_generate`, `cloud_sync`). The worker
/// that owns processing lives in the app process — a job survives process
/// death in [JobStatus.running]/[JobStatus.retrying] and is reset to queued
/// by the worker's crash recovery on next launch, so no job is ever lost or
/// reported "succeeded" unless its handler actually completed.
///
/// `payload` stores only NON-secret, JSON-encoded inputs (no API keys, no
/// ID tokens — the worker reads credentials fresh at run time). `attempts` /
/// `maxAttempts` drive the bounded retry policy; `nextRetryAt` defers retries
/// after backoff. `resultRef` points at the durable effect (draft id,
/// last-sync timestamp).
class Jobs extends Table {
  TextColumn get id => text()();
  TextColumn get type => text()();
  TextColumn get status => text()();

  /// Owner-free, single-user app: `subjectId` scopes the job — a birthday id
  /// for `ai_generate`, the signed-in account uid for `cloud_sync`.
  TextColumn get subjectId => text()();
  TextColumn get payload => text().nullable()();
  IntColumn get attempts => integer().withDefault(const Constant(0))();
  IntColumn get maxAttempts => integer().withDefault(const Constant(1))();
  TextColumn get errorCode => text().nullable()();
  TextColumn get errorMessage => text().nullable()();
  TextColumn get resultRef => text().nullable()();
  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get startedAt => dateTime().nullable()();
  DateTimeColumn get finishedAt => dateTime().nullable()();
  DateTimeColumn get nextRetryAt => dateTime().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

@DriftDatabase(
  tables: [
    Persons,
    Birthdays,
    MessageDrafts,
    ReminderSettingsEntries,
    DeliveryEvents,
    Jobs,
  ],
)
class AppDatabase extends _$AppDatabase {
  AppDatabase(super.e);

  /// Opens the operational on-device database using the bundled SQLite,
  /// falling back to an in-memory database in headless test environments.
  factory AppDatabase.open() {
    if (!kIsWeb && Platform.environment.containsKey('FLUTTER_TEST')) {
      return AppDatabase(NativeDatabase.memory());
    }
    return AppDatabase(driftDatabase(name: 'ai_birthday'));
  }

  @override
  int get schemaVersion => 9;

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (m) => m.createAll(),
    onUpgrade: (m, from, to) async {
      if (from < 2) {
        await m.createTable(birthdays);
        await m.createTable(messageDrafts);
        await m.createTable(reminderSettingsEntries);
      }
      if (from < 3) {
        // Preserve existing notes and birthday rows. Migration errors must
        // propagate so a partially upgraded database is never treated as safe.
        // ignore: experimental_member_use
        await m.alterTable(TableMigration(persons));
      }
      if (from < 4) {
        await m.createTable(deliveryEvents);
      }
      if (from < 5) {
        await m.createTable(jobs);
      }
      if (from < 6 &&
          !await _tableColumnExists('persons', 'leap_day_resolution')) {
        await m.addColumn(persons, persons.leapDayResolution);
      }
      if (from < 6) {
        final draftIdExists = await _tableColumnExists(
          'delivery_events',
          'draft_id',
        );
        if (!draftIdExists) {
          await customStatement(
            'ALTER TABLE delivery_events ADD COLUMN draft_id TEXT NULL',
          );
        }
        final outcomeExists = await _tableColumnExists(
          'delivery_events',
          'outcome',
        );
        if (!outcomeExists) {
          await customStatement(
            "ALTER TABLE delivery_events ADD COLUMN outcome TEXT NOT NULL DEFAULT 'launched'",
          );
        }
      }
      if (from < 7) {
        await _migrateToV7(m);
      }
      if (from < 8) {
        if (!await _tableColumnExists('delivery_events', 'draft_revision')) {
          await m.addColumn(deliveryEvents, deliveryEvents.draftRevision);
        }
        if (!await _tableColumnExists('delivery_events', 'body_hash')) {
          await m.addColumn(deliveryEvents, deliveryEvents.bodyHash);
        }
      }
      if (from < 9) {
        await _dedupeBirthdaysPerCycle();
        await customStatement('DROP INDEX IF EXISTS idx_birthdays_person_id');
        await customStatement(
          'DROP INDEX IF EXISTS idx_birthdays_person_id_unique',
        );
        await customStatement(
          'CREATE UNIQUE INDEX IF NOT EXISTS idx_birthdays_person_cycle_year_unique '
          'ON birthdays (person_id, cycle_year)',
        );
      }
    },
    beforeOpen: (details) async {
      // Query audit: delivery_events is the only unbounded table and
      // birthday_id is its hot filter column (per-birthday handoff lookups).
      // IF NOT EXISTS keeps this idempotent for both fresh and existing DBs.
      await customStatement(
        'CREATE INDEX IF NOT EXISTS idx_delivery_events_birthday_id ON delivery_events (birthday_id)',
      );
      // Keep at most one row for a person and cycle. Older cycles remain
      // separate so their drafts and delivery evidence cannot become current.
      await customStatement('DROP INDEX IF EXISTS idx_birthdays_person_id');
      await customStatement(
        'DROP INDEX IF EXISTS idx_birthdays_person_id_unique',
      );
      await customStatement(
        'CREATE UNIQUE INDEX IF NOT EXISTS idx_birthdays_person_cycle_year_unique '
        'ON birthdays (person_id, cycle_year)',
      );
      await customStatement(
        'CREATE INDEX IF NOT EXISTS idx_message_drafts_birthday_id ON message_drafts (birthday_id)',
      );
      await customStatement(
        'CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs (status)',
      );
    },
  );

  /// v6 -> v7: calendar-date birthdays, one birthday per cycle, draft
  /// revisions. Idempotent so partially pre-migrated databases are safe.
  Future<void> _migrateToV7(Migrator m) async {
    if (!await _tableColumnExists('message_drafts', 'revision')) {
      await customStatement(
        'ALTER TABLE message_drafts ADD COLUMN revision INTEGER NOT NULL DEFAULT 0',
      );
    }

    await _dedupeBirthdaysPerCycle();

    final dateColumn = (await customSelect(
      'PRAGMA table_info(birthdays)',
    ).get()).firstWhere((row) => row.read<String>('name') == 'date');
    if (dateColumn.read<String>('type').toUpperCase() == 'TEXT') return;

    // Legacy `date` held a unix-seconds instant that was produced as midnight
    // in the recipient's zone but read back in the device zone, so it cannot
    // be trusted. Re-derive the calendar day from the person's month/day and
    // the stored cycle year; fall back to the device-local day of the stored
    // instant only when the person (or their birthday) is unavailable.
    final people = {
      for (final row in await customSelect(
        'SELECT id, birthday_month, birthday_day, leap_day_resolution FROM persons',
      ).get())
        row.read<String>('id'): row,
    };
    final rows = await customSelect(
      'SELECT id, person_id, cycle_year, date FROM birthdays',
    ).get();
    for (final row in rows) {
      final person = people[row.read<String>('person_id')];
      final month = person?.read<int?>('birthday_month');
      final day = person?.read<int?>('birthday_day');
      final cycleYear = row.read<int>('cycle_year');
      DateTime? derived;
      if (month != null && day != null) {
        var resolvedMonth = month;
        var resolvedDay = day;
        final isLeapYear =
            (cycleYear % 4 == 0 && cycleYear % 100 != 0) ||
            cycleYear % 400 == 0;
        if (month == 2 && day == 29 && !isLeapYear) {
          final preferMar1 =
              person!.read<String?>('leap_day_resolution') == 'mar1';
          resolvedMonth = preferMar1 ? 3 : 2;
          resolvedDay = preferMar1 ? 1 : 28;
        }
        derived = DateTime.utc(cycleYear, resolvedMonth, resolvedDay);
      } else {
        final raw = row.data['date'];
        if (raw is int) {
          final local = DateTime.fromMillisecondsSinceEpoch(raw * 1000);
          derived = DateTime.utc(local.year, local.month, local.day);
        } else if (raw is String && raw.length >= 10) {
          derived = DateTime.tryParse(raw.substring(0, 10));
        }
      }
      if (derived == null) continue;
      await customStatement('UPDATE birthdays SET date = ? WHERE id = ?', [
        derived.millisecondsSinceEpoch ~/ 1000,
        row.read<String>('id'),
      ]);
    }

    await m.alterTable(
      // ignore: experimental_member_use
      TableMigration(
        birthdays,
        columnTransformer: {
          birthdays.date: CustomExpression<String>(
            "strftime('%Y-%m-%d', date, 'unixepoch')",
          ),
        },
      ),
    );
  }

  /// Keeps the newest duplicate within each person/cycle pair. Earlier cycles
  /// are independent records; their drafts, history, and jobs must stay linked
  /// to their original birthday id.
  Future<void> _dedupeBirthdaysPerCycle() async {
    final rows = await customSelect(
      'SELECT id, person_id, cycle_year FROM birthdays '
      'ORDER BY updated_at DESC, created_at DESC, id DESC',
    ).get();
    final keptByCycle = <(String, int), String>{};
    for (final row in rows) {
      final id = row.read<String>('id');
      final personId = row.read<String>('person_id');
      final cycleYear = row.read<int>('cycle_year');
      final key = (personId, cycleYear);
      final keptId = keptByCycle[key];
      if (keptId == null) {
        keptByCycle[key] = id;
        continue;
      }
      await customStatement(
        'UPDATE message_drafts SET birthday_id = ? WHERE birthday_id = ?',
        [keptId, id],
      );
      await customStatement(
        'UPDATE delivery_events SET birthday_id = ? WHERE birthday_id = ?',
        [keptId, id],
      );
      await customStatement('DELETE FROM jobs WHERE subject_id = ?', [id]);
      await customStatement('DELETE FROM birthdays WHERE id = ?', [id]);
    }
  }

  Future<bool> _tableColumnExists(String table, String column) async {
    final columns = await customSelect('PRAGMA table_info($table)').get();
    return columns.any((row) => row.read<String>('name') == column);
  }
}
