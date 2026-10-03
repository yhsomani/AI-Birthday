/// Drift SQLite local database for offline-first data persistence (SSOT §3, §4, §13, §19).
library;

import 'package:drift/drift.dart';
import 'package:drift_flutter/drift_flutter.dart';

part 'app_database.g.dart';

/// Recipient contacts table.
class PeopleEntries extends Table {
  TextColumn get id => text()();
  TextColumn get name => text()();
  IntColumn get birthdayMonth => integer()();
  IntColumn get birthdayDay => integer()();
  IntColumn get birthYear => integer().nullable()();
  TextColumn get phoneNumber => text().nullable()();
  TextColumn get email => text().nullable()();
  TextColumn get relationship => text().withDefault(const Constant('other'))();
  TextColumn get relationshipCloseness =>
      text().withDefault(const Constant('casual'))();
  TextColumn get preferredLanguage =>
      text().withDefault(const Constant('en'))();
  TextColumn get preferredTone => text().withDefault(const Constant('warm'))();
  TextColumn get importantFacts => text().withDefault(const Constant('[]'))();
  TextColumn get notes => text().nullable()();
  TextColumn get preferredDeliveryChannel =>
      text().withDefault(const Constant('whatsapp'))();
  TextColumn get timezone => text().nullable()();
  BoolColumn get autoPrepare => boolean().withDefault(const Constant(true))();
  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get updatedAt => dateTime()();
  IntColumn get version => integer().withDefault(const Constant(1))();

  @override
  Set<Column> get primaryKey => {id};
}

/// Birthday cycles table.
class BirthdayEntries extends Table {
  TextColumn get id => text()();
  TextColumn get personId => text()();
  IntColumn get cycleYear => integer()();
  DateTimeColumn get date => dateTime()();
  TextColumn get status => text().withDefault(const Constant('upcoming'))();
  TextColumn get draftId => text().nullable()();
  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get updatedAt => dateTime()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Message drafts table.
class MessageDraftEntries extends Table {
  TextColumn get id => text()();
  TextColumn get birthdayId => text()();
  TextColumn get personId => text()();
  TextColumn get body => text()();
  TextColumn get tone => text()();
  TextColumn get length => text()();
  TextColumn get status => text().withDefault(const Constant('draft'))();
  TextColumn get providerType => text().withDefault(const Constant('manual'))();
  IntColumn get variationIndex => integer().withDefault(const Constant(0))();
  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get updatedAt => dateTime()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Deleted entity tombstones for conflict-free synchronization (SSOT §19).
class SyncTombstonesEntries extends Table {
  TextColumn get id => text()();
  TextColumn get entityType => text()();
  TextColumn get entityId => text()();
  DateTimeColumn get deletedAt => dateTime()();

  @override
  Set<Column> get primaryKey => {id};
}

@DriftDatabase(
  tables: [
    PeopleEntries,
    BirthdayEntries,
    MessageDraftEntries,
    SyncTombstonesEntries,
  ],
)
class AppDatabase extends _$AppDatabase {
  AppDatabase() : super(_openConnection());

  AppDatabase.forTesting(super.e);

  @override
  int get schemaVersion => 1;

  static QueryExecutor _openConnection() {
    return driftDatabase(name: 'ai_birthday_db');
  }
}
