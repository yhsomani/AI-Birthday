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

@DriftDatabase(
  tables: [Persons, Birthdays, MessageDrafts, ReminderSettingsEntries],
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
  int get schemaVersion => 3;

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
    },
    beforeOpen: (details) async {
      await customStatement(
        "UPDATE persons SET birthday_month = NULL, birthday_day = NULL, notes = 'Imported from phone contacts' WHERE notes LIKE '%birthday default set to today%';",
      );
      await customStatement(
        "DELETE FROM birthdays WHERE person_id IN (SELECT id FROM persons WHERE birthday_month IS NULL);",
      );
    },
  );
}
