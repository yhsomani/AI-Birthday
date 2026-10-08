/// Drift/SQLite database foundation.
///
/// Local data is operationally primary (SSOT §13, §19). Firestore is a
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

/// A person (birthday recipient).
///
/// Columns mirror the SSOT recipient model. Enums and lists are stored as
/// textual encodings; the repository is responsible for mapping to and from
/// the domain model.
class Persons extends Table {
  TextColumn get id => text()();
  TextColumn get name => text()();
  IntColumn get birthdayMonth => integer().nullable()();
  IntColumn get birthdayDay => integer().nullable()();
  IntColumn get birthYear => integer().nullable()();
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
  DateTimeColumn get date => dateTime()();
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

/// Persisted evidence of a successful external-app launch (audit 03 P1-1).
///
/// This is the ONLY durable record that a message was handed off; the app
/// must never infer "opened" from birthday/draft status alone. `channel`
/// stores a [DeliveryChannel] name so History can label the exact app
/// (WhatsApp vs SMS vs Share Sheet) rather than guessing.
class DeliveryEvents extends Table {
  TextColumn get id => text()();
  TextColumn get birthdayId => text()();
  TextColumn get channel => text()();
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
  int get schemaVersion => 5;

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
        await customStatement(
          "UPDATE persons SET birthday_month = NULL, birthday_day = NULL, notes = 'Imported from phone contacts' WHERE notes LIKE '%birthday default set to today%';",
        );
        await customStatement(
          "DELETE FROM birthdays WHERE person_id IN (SELECT id FROM persons WHERE birthday_month IS NULL);",
        );
      }
      if (from < 4) {
        await m.createTable(deliveryEvents);
      }
      if (from < 5) {
        await m.createTable(jobs);
      }
    },
    beforeOpen: (details) async {
      await customStatement(
        "UPDATE persons SET birthday_month = NULL, birthday_day = NULL, notes = 'Imported from phone contacts' WHERE notes LIKE '%birthday default set to today%';",
      );
      await customStatement(
        "DELETE FROM birthdays WHERE person_id IN (SELECT id FROM persons WHERE birthday_month IS NULL);",
      );
      // Query audit: delivery_events is the only unbounded table and
      // birthday_id is its hot filter column (per-birthday handoff lookups).
      // IF NOT EXISTS keeps this idempotent for both fresh and existing DBs.
      await customStatement(
        'CREATE INDEX IF NOT EXISTS idx_delivery_events_birthday_id ON delivery_events (birthday_id)',
      );
    },
  );
}
