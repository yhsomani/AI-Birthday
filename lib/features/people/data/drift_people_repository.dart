import 'dart:async';

import 'package:clock/clock.dart' as clock;
import 'package:drift/drift.dart' as drift;

import 'package:ai_birthday/core/database/app_database.dart' as db;
import 'package:ai_birthday/features/birthdays/domain/birthday_engine.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/features/people/domain/person.dart';
import 'package:ai_birthday/features/people/domain/person_enums.dart'
    show AutoSendPolicy;
import 'package:ai_birthday/features/people/domain/repositories/people_repository.dart';

/// Canonical Drift/SQLite implementation of [PeopleRepository].
class DriftPeopleRepository implements PeopleRepository {
  DriftPeopleRepository(this._database, {DateTime Function()? now})
    : _now = now ?? clock.clock.now;

  final db.AppDatabase _database;
  final DateTime Function() _now;

  @override
  Stream<List<Person>> watchActivePeople({bool includeDeleted = false}) {
    final query = _database.select(_database.persons);
    if (!includeDeleted) {
      query.where((row) => row.deletedAt.isNull());
    }
    query.orderBy([
      (t) => drift.OrderingTerm(expression: t.name.lower()),
      (t) => drift.OrderingTerm(expression: t.id),
    ]);
    return query.watch().map((rows) => rows.map(_toDomain).toList());
  }

  @override
  Future<List<Person>> listActivePeople({bool includeDeleted = false}) async {
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

  // Compatibility convenience accessor for untouched E2E tier tests.
  Future<List<Person>> getPeople() => listActivePeople();

  @override
  Future<Person?> getPersonById(
    String id, {
    bool includeDeleted = false,
  }) async {
    final query = _database.select(_database.persons)
      ..where((row) {
        final byId = row.id.equals(id);
        return includeDeleted ? byId : byId & row.deletedAt.isNull();
      });
    final row = await query.getSingleOrNull();
    return row == null ? null : _toDomain(row);
  }

  @override
  Future<void> savePerson(Person person) async {
    final existing = await (_database.select(
      _database.persons,
    )..where((row) => row.id.equals(person.id))).getSingleOrNull();
    final storedPerson = person.deletedAt == null && existing?.deletedAt != null
        ? person.copyWith(deletedAt: existing!.deletedAt)
        : person;
    // nullToAbsent: false so a cleared optional field overwrites the old
    // value instead of being skipped by the upsert (audit A1-004).
    await _database
        .into(_database.persons)
        .insertOnConflictUpdate(_toRow(storedPerson).toCompanion(false));
  }

  @override
  Future<void> savePersonAndBirthday(Person person, Birthday? birthday) async {
    await _database.transaction(() async {
      final existingPerson = await (_database.select(
        _database.persons,
      )..where((row) => row.id.equals(person.id))).getSingleOrNull();
      if (existingPerson?.deletedAt != null && person.deletedAt == null) {
        throw StateError(
          'Cannot update a deleted contact without restoring it.',
        );
      }

      var birthdayToSave = birthday;
      if (birthday != null) {
        final previous = await (_database.select(
          _database.birthdays,
        )..where((row) => row.id.equals(birthday.id))).getSingleOrNull();
        final occurrenceChanged =
            previous != null &&
            (previous.cycleYear != birthday.cycleYear ||
                !birthday.isSameCalendarDate(previous.date));
        if (previous != null && !occurrenceChanged) {
          // A person edit must not overwrite progress recorded concurrently by
          // Message Studio for this same birthday cycle.
          birthdayToSave = birthday.copyWith(
            status: BirthdayStatus.fromString(previous.status),
            draftId: previous.draftId,
          );
        }
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
      await savePerson(person);
      if (birthdayToSave != null) {
        await _database
            .into(_database.birthdays)
            .insertOnConflictUpdate(_birthdayRow(birthdayToSave));
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
    final row =
        await (_database.select(_database.birthdays)
              ..where((r) => r.personId.equals(personId) & r.deletedAt.isNull())
              ..orderBy([
                (r) => drift.OrderingTerm.desc(r.cycleYear),
                (r) => drift.OrderingTerm.desc(r.updatedAt),
                (r) => drift.OrderingTerm.desc(r.id),
              ])
              ..limit(1))
            .getSingleOrNull();
    return row == null ? null : _birthdayFromRow(row);
  }

  @override
  Future<void> softDeletePerson(String personId, DateTime deletedAt) async {
    final existing = await getPersonById(personId, includeDeleted: true);
    if (existing == null) return;
    final utcDeletedAt = deletedAt.toUtc();
    await _database.transaction(() async {
      await (_database.update(
        _database.persons,
      )..where((row) => row.id.equals(personId))).write(
        db.PersonsCompanion(
          deletedAt: drift.Value(utcDeletedAt),
          updatedAt: drift.Value(utcDeletedAt),
          version: drift.Value(existing.version + 1),
        ),
      );
      await (_database.delete(
        _database.birthdays,
      )..where((row) => row.personId.equals(personId))).go();
      await (_database.delete(_database.jobs)..where(
            (row) =>
                row.subjectId.equals(personId) |
                row.subjectId.equals('birthday-$personId'),
          ))
          .go();
    });
  }

  @override
  Future<void> restorePerson(Person person, Birthday? birthday) async {
    final existing = await getPersonById(person.id, includeDeleted: true);
    if (existing == null) return;
    final now = _now().toUtc();
    await _database.transaction(() async {
      await (_database.update(
        _database.persons,
      )..where((row) => row.id.equals(person.id))).write(
        db.PersonsCompanion(
          deletedAt: const drift.Value(null),
          updatedAt: drift.Value(now),
          version: drift.Value(existing.version + 1),
        ),
      );
      if (birthday != null) {
        await _database
            .into(_database.birthdays)
            .insertOnConflictUpdate(
              db.BirthdaysCompanion(
                id: drift.Value(birthday.id),
                personId: drift.Value(birthday.personId),
                cycleYear: drift.Value(birthday.cycleYear),
                date: drift.Value(birthday.date),
                status: drift.Value(birthday.status.name),
                draftId: drift.Value(birthday.draftId),
                createdAt: drift.Value(birthday.createdAt),
                updatedAt: drift.Value(now),
                deletedAt: const drift.Value(null),
              ),
            );
      }
    });
  }

  @override
  Future<int> purgeExpiredTombstones(DateTime olderThan) async {
    final cutoff = olderThan.toUtc();
    return _database.transaction(() async {
      final expiredRows =
          await (_database.select(_database.persons)..where(
                (row) =>
                    row.deletedAt.isNotNull() &
                    row.deletedAt.isSmallerThanValue(cutoff),
              ))
              .get();
      if (expiredRows.isEmpty) return 0;

      final personIds = expiredRows.map((row) => row.id).toList();
      final birthdayRows = await (_database.select(
        _database.birthdays,
      )..where((row) => row.personId.isIn(personIds))).get();
      final subjectIds = <String>{
        ...personIds,
        for (final id in personIds) 'birthday-$id',
        for (final b in birthdayRows) b.id,
      }.toList();

      await (_database.delete(
        _database.jobs,
      )..where((row) => row.subjectId.isIn(subjectIds))).go();
      await (_database.delete(_database.messageDrafts)..where(
            (row) =>
                row.personId.isIn(personIds) &
                row.status.equals(DraftStatus.confirmedSent.name).not(),
          ))
          .go();
      await (_database.delete(
        _database.birthdays,
      )..where((row) => row.personId.isIn(personIds))).go();
      return (_database.delete(
        _database.persons,
      )..where((row) => row.id.isIn(personIds))).go();
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
        deletedAt: const drift.Value(null),
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
