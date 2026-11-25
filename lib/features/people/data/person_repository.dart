import 'dart:async';

import 'package:clock/clock.dart' as clock;
import 'package:drift/drift.dart' as drift;

import '../../../core/database/app_database.dart' as db;
import '../../birthdays/domain/models/birthday.dart';
import '../../birthdays/domain/birthday_engine.dart';
import '../../delivery/domain/models/delivery_channel.dart';
import '../../message_studio/domain/models/message_draft.dart';
import '../domain/models/relationship.dart';
import '../domain/models/tone.dart';
import '../domain/person.dart';
import '../domain/person_enums.dart' show AutoSendPolicy;

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
  Future<void> saveAggregate(Person person, {Birthday? birthday});
  Future<void> saveImportBatch({
    required List<Person> people,
    required List<Birthday> birthdays,
  });
  Future<Birthday?> getBirthdayForPerson(String personId);
  Future<void> softDelete(String id);
  Future<void> softDeleteAggregate(String id);
  Future<void> restore(String id);
  Future<void> restoreAggregate(String id, {Birthday? restoredBirthday});
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
    query.orderBy([
      (t) => drift.OrderingTerm(expression: t.name.lower()),
      (t) => drift.OrderingTerm(expression: t.id),
    ]);
    final rows = await query.get();
    return rows.map(_toDomain).toList();
  }

  @override
  Stream<List<Person>> watchAll({bool includeDeleted = false}) {
    final query = _database.select(_database.persons);
    if (!includeDeleted) {
      query.where((row) => row.deletedAt.isNull());
    }
    query.orderBy([
      (t) => drift.OrderingTerm(expression: t.name.lower()),
      (t) => drift.OrderingTerm(expression: t.id),
    ]);
    return query.watch().map((rows) {
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
  Future<void> saveAggregate(Person person, {Birthday? birthday}) async {
    await _database.transaction(() async {
      if (birthday != null) {
        final previous = await (_database.select(
          _database.birthdays,
        )..where((row) => row.id.equals(birthday.id))).getSingleOrNull();
        final occurrenceChanged =
            previous != null &&
            (previous.cycleYear != birthday.cycleYear ||
                previous.date.year != birthday.date.year ||
                previous.date.month != birthday.date.month ||
                previous.date.day != birthday.date.day);
        if (occurrenceChanged) {
          await (_database.delete(_database.messageDrafts)..where(
                (row) =>
                    row.birthdayId.equals(birthday.id) &
                    row.status.equals(DraftStatus.confirmedSent.name).not(),
              ))
              .go();
          await (_database.delete(
            _database.jobs,
          )..where((row) => row.subjectId.equals(birthday.id))).go();
        }
      }
      await save(person);
      if (birthday != null) {
        await _database
            .into(_database.birthdays)
            .insertOnConflictUpdate(_birthdayRow(birthday));
      }
    });
  }

  @override
  Future<void> saveImportBatch({
    required List<Person> people,
    required List<Birthday> birthdays,
  }) async {
    final personIds = people.map((person) => person.id).toSet();
    if (personIds.length != people.length ||
        birthdays.any((birthday) => !personIds.contains(birthday.personId))) {
      throw ArgumentError('Import batch contains duplicate or orphan records.');
    }

    await _database.transaction(() async {
      for (final person in people) {
        await _database.into(_database.persons).insert(_toRow(person));
      }
      for (final birthday in birthdays) {
        await _database
            .into(_database.birthdays)
            .insert(_birthdayRow(birthday));
      }
    });
  }

  @override
  Future<Birthday?> getBirthdayForPerson(String personId) async {
    final row = await (_database.select(
      _database.birthdays,
    )..where((r) => r.personId.equals(personId))).getSingleOrNull();
    return row == null ? null : _birthdayFromRow(row);
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
  Future<void> softDeleteAggregate(String id) async {
    final existing = await getById(id);
    if (existing == null) return;
    final now = _now();
    await _database.transaction(() async {
      await (_database.update(
        _database.persons,
      )..where((row) => row.id.equals(id))).write(
        db.PersonsCompanion(
          deletedAt: drift.Value(now.toUtc()),
          updatedAt: drift.Value(now.toUtc()),
          version: drift.Value(existing.version + 1),
        ),
      );
      await (_database.delete(
        _database.birthdays,
      )..where((row) => row.personId.equals(id))).go();
      await (_database.delete(_database.jobs)..where(
            (row) =>
                row.subjectId.equals(id) | row.subjectId.equals('birthday-$id'),
          ))
          .go();
    });
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

  @override
  Future<void> restoreAggregate(String id, {Birthday? restoredBirthday}) async {
    final existing = await getById(id);
    if (existing == null) return;
    final now = _now();
    await _database.transaction(() async {
      await (_database.update(
        _database.persons,
      )..where((row) => row.id.equals(id))).write(
        db.PersonsCompanion(
          deletedAt: const drift.Value(null),
          updatedAt: drift.Value(now.toUtc()),
          version: drift.Value(existing.version + 1),
        ),
      );
      if (restoredBirthday != null) {
        await _database
            .into(_database.birthdays)
            .insertOnConflictUpdate(
              db.BirthdaysCompanion(
                id: drift.Value(restoredBirthday.id),
                personId: drift.Value(restoredBirthday.personId),
                cycleYear: drift.Value(restoredBirthday.cycleYear),
                date: drift.Value(restoredBirthday.date),
                status: drift.Value(restoredBirthday.status.name),
                draftId: drift.Value(restoredBirthday.draftId),
                createdAt: drift.Value(restoredBirthday.createdAt),
                updatedAt: drift.Value(now.toUtc()),
              ),
            );
      }
    });
  }

  Person _toDomain(db.Person row) => Person(
    id: row.id,
    name: row.name,
    birthdayMonth: row.birthdayMonth,
    birthdayDay: row.birthdayDay,
    birthYear: row.birthYear,
    leapDayResolution: LeapDayResolution.fromString(row.leapDayResolution),
    phoneNumber: row.phoneNumber,
    email: row.email,
    relationship: RelationshipCategory.fromString(row.relationship),
    relationshipCloseness: RelationshipCloseness.fromStored(
      row.relationshipCloseness,
    ),
    preferredLanguage: row.preferredLanguage.isEmpty
        ? 'en'
        : row.preferredLanguage,
    preferredTone: MessageTone.fromString(row.preferredTone),
    importantFacts: _facts(row.importantFacts),
    notes: row.notes,
    preferredDeliveryChannel: DeliveryChannel.fromString(
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

  static db.BirthdaysCompanion _birthdayRow(Birthday birthday) =>
      db.BirthdaysCompanion(
        id: drift.Value(birthday.id),
        personId: drift.Value(birthday.personId),
        cycleYear: drift.Value(birthday.cycleYear),
        date: drift.Value(birthday.date),
        status: drift.Value(birthday.status.name),
        draftId: drift.Value(birthday.draftId),
        createdAt: drift.Value(birthday.createdAt),
        updatedAt: drift.Value(birthday.updatedAt),
      );

  static Birthday _birthdayFromRow(db.Birthday row) => Birthday(
    id: row.id,
    personId: row.personId,
    cycleYear: row.cycleYear,
    date: row.date,
    status: BirthdayStatus.fromString(row.status),
    draftId: row.draftId,
    createdAt: row.createdAt.toUtc(),
    updatedAt: row.updatedAt.toUtc(),
  );

  static db.Person _toRow(Person p) => db.Person(
    id: p.id,
    name: p.name,
    birthdayMonth: p.birthdayMonth,
    birthdayDay: p.birthdayDay,
    birthYear: p.birthYear,
    leapDayResolution: p.leapDayResolution?.name,
    phoneNumber: p.phoneNumber,
    email: p.email,
    relationship: p.relationship.name,
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
