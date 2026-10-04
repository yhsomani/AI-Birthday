<<<<<<< HEAD
/// Drift/SQLite database foundation.
///
/// Local data is operationally primary (SSOT §13, §19). Firestore is a
/// synchronization target, never the operational store. Tables carry the
/// shared sync envelope fields (`id`, `createdAt`, `updatedAt`, `version`,
/// `deletedAt`) so synchronization, conflict handling and tombstones stay
/// deterministic.
=======
/// Drift SQLite local database for offline-first data persistence (SSOT §3, §4, §13, §19).
>>>>>>> e8906b8fe21fa6f7bcb6466936b85c0d59161f88
library;

import 'package:drift/drift.dart';
import 'package:drift_flutter/drift_flutter.dart';

part 'app_database.g.dart';

<<<<<<< HEAD
/// A person (birthday recipient).
///
/// Columns mirror the SSOT recipient model. Enums and lists are stored as
/// textual encodings; the repository is responsible for mapping to and from
/// the domain model.
class Persons extends Table {
=======
/// Recipient contacts table.
class PeopleEntries extends Table {
>>>>>>> e8906b8fe21fa6f7bcb6466936b85c0d59161f88
  TextColumn get id => text()();
  TextColumn get name => text()();
  IntColumn get birthdayMonth => integer()();
  IntColumn get birthdayDay => integer()();
  IntColumn get birthYear => integer().nullable()();
  TextColumn get phoneNumber => text().nullable()();
  TextColumn get email => text().nullable()();
<<<<<<< HEAD
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
=======
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
>>>>>>> e8906b8fe21fa6f7bcb6466936b85c0d59161f88

  @override
  Set<Column> get primaryKey => {id};
}

<<<<<<< HEAD
@DriftDatabase(tables: [Persons])
class AppDatabase extends _$AppDatabase {
  AppDatabase(super.e);

  /// Opens the operational on-device database using the bundled SQLite.
  factory AppDatabase.open() => AppDatabase(driftDatabase(name: 'ai_birthday'));

  @override
  int get schemaVersion => 1;
=======
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
>>>>>>> e8906b8fe21fa6f7bcb6466936b85c0d59161f88
}
