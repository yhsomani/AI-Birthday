import 'dart:async';

import 'package:clock/clock.dart' as clock;
import 'package:drift/drift.dart' as drift;

import '../../../core/database/app_database.dart' as db;
import '../domain/person.dart';
import '../domain/person_enums.dart';

/// Persistence face for recipients.
///
/// Implemented by [DriftPeopleStore] backed by the operational SQLite store
/// (SSOT §13/§19). A fake implementation exists for tests, so UI and services
/// never depend on an actual database.
abstract class PeopleStore {
  Future<List<Person>> getAll({bool includeDeleted = false});
  Stream<List<Person>> watchAll({bool includeDeleted = false});
  Future<Person?> getById(String id);
  Future<void> save(Person person);
  Future<void> softDelete(String id);
  Future<void> restore(String id);
}

/// Drift-backed [PeopleStore]. Version/timestamp integrity is enforced by the
/// service layer; save stores the entity as given (idempotent by id).
class DriftPeopleStore implements PeopleStore {
  DriftPeopleStore(this._database, {DateTime Function()? now})
    : _now = now ?? clock.clock.now;

  final db.AppDatabase _database;
  final DateTime Function() _now;

  @override
  Future<List<Person>> getAll({bool includeDeleted = false}) async {
    final query = _database.select(_database.persons);
    if (!includeDeleted) {
      query.where((row) => row.deletedAt.isNull());
    }
    final rows = await query.get();
    rows.sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
    return rows.map(_toDomain).toList();
  }

  @override
  Stream<List<Person>> watchAll({bool includeDeleted = false}) {
    final query = _database.select(_database.persons);
    if (!includeDeleted) {
      query.where((row) => row.deletedAt.isNull());
    }
    return query.watch().map((rows) {
      rows.sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
      return rows.map(_toDomain).toList();
    });
  }

  @override
  Future<Person?> getById(String id) async {
    final query = _database.select(_database.persons)
      ..where((row) => row.id.equals(id));
    final row = await query.getSingleOrNull();
    return row == null ? null : _toDomain(row);
  }

  @override
  Future<void> save(Person person) async {
    await _database
        .into(_database.persons)
        .insertOnConflictUpdate(_toRow(person));
  }

  @override
  Future<void> softDelete(String id) async {
    final existing = await getById(id);
    if (existing == null) return;
    final now = _now();
    await (_database.update(
      _database.persons,
    )..where((row) => row.id.equals(id))).write(
      db.PersonsCompanion(
        deletedAt: drift.Value(now.toUtc()),
        updatedAt: drift.Value(now.toUtc()),
        version: drift.Value(existing.version + 1),
      ),
    );
  }

  @override
  Future<void> restore(String id) async {
    final existing = await getById(id);
    if (existing == null) return;
    final now = _now();
    await (_database.update(
      _database.persons,
    )..where((row) => row.id.equals(id))).write(
      db.PersonsCompanion(
        deletedAt: drift.Value(null),
        updatedAt: drift.Value(now.toUtc()),
        version: drift.Value(existing.version + 1),
      ),
    );
  }

  Person _toDomain(db.Person row) => Person(
    id: row.id,
    name: row.name,
    birthdayMonth: row.birthdayMonth,
    birthdayDay: row.birthdayDay,
    birthYear: row.birthYear,
    phoneNumber: row.phoneNumber,
    email: row.email,
    relationship: row.relationship,
    relationshipCloseness: RelationshipCloseness.parse(
      row.relationshipCloseness,
    ),
    preferredLanguage: row.preferredLanguage.isEmpty
        ? 'en'
        : row.preferredLanguage,
    preferredTone: PreferredTone.parse(row.preferredTone),
    importantFacts: _facts(row.importantFacts),
    notes: row.notes,
    preferredDeliveryChannel: DeliveryChannel.parse(
      row.preferredDeliveryChannel,
    ),
    timezone: row.timezone,
    autoPrepare: row.autoPrepare,
    autoSendPolicy: AutoSendPolicy.parse(row.autoSendPolicy),
    createdAt: row.createdAt.toUtc(),
    updatedAt: row.updatedAt.toUtc(),
    version: row.version,
    deletedAt: row.deletedAt?.toUtc(),
  );

  static db.Person _toRow(Person p) => db.Person(
    id: p.id,
    name: p.name,
    birthdayMonth: p.birthdayMonth,
    birthdayDay: p.birthdayDay,
    birthYear: p.birthYear,
    phoneNumber: p.phoneNumber,
    email: p.email,
    relationship: p.relationship,
    relationshipCloseness: p.relationshipCloseness.name,
    preferredLanguage: p.preferredLanguage,
    preferredTone: p.preferredTone.name,
    importantFacts: p.importantFacts.join('\n'),
    notes: p.notes,
    preferredDeliveryChannel: p.preferredDeliveryChannel.name,
    timezone: p.timezone,
    autoPrepare: p.autoPrepare,
    autoSendPolicy: p.autoSendPolicy.name,
    createdAt: p.createdAt.toUtc(),
    updatedAt: p.updatedAt.toUtc(),
    version: p.version,
    deletedAt: p.deletedAt?.toUtc(),
  );

  static List<String> _facts(String joined) => joined
      .split('\n')
      .map((s) => s.trim())
      .where((s) => s.isNotEmpty)
      .toList();
}
