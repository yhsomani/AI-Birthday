/// Drift-backed persistent implementations of domain repositories (SSOT §13, §19).
library;

import 'dart:async';
import 'package:drift/drift.dart';

import 'package:ai_birthday/core/database/app_database.dart' as db;
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/birthdays/domain/repositories/birthdays_repository.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';
import 'package:ai_birthday/features/message_studio/domain/repositories/drafts_repository.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/features/people/domain/repositories/people_repository.dart';

/// Drift/SQLite-backed implementation of [PeopleRepository].
class DriftPeopleRepository implements PeopleRepository {
  DriftPeopleRepository(this._database);

  final db.AppDatabase _database;

  @override
  Stream<List<Person>> watchPeople() {
    return (_database.select(
      _database.persons,
    )..where((r) => r.deletedAt.isNull())).watch().map((rows) {
      final list = rows.map(_personFromRow).toList();
      list.sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
      return list;
    });
  }

  @override
  Future<List<Person>> getPeople() async {
    final rows = await (_database.select(
      _database.persons,
    )..where((r) => r.deletedAt.isNull())).get();
    final list = rows.map(_personFromRow).toList();
    list.sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
    return list;
  }

  @override
  Future<Person?> getPerson(String id) async {
    final row = await (_database.select(
      _database.persons,
    )..where((r) => r.id.equals(id) & r.deletedAt.isNull())).getSingleOrNull();
    return row == null ? null : _personFromRow(row);
  }

  @override
  Future<void> savePerson(Person person) async {
    await _database
        .into(_database.persons)
        .insertOnConflictUpdate(
          db.PersonsCompanion(
            id: Value(person.id),
            name: Value(person.name),
            birthdayMonth: Value(person.birthdayMonth),
            birthdayDay: Value(person.birthdayDay),
            birthYear: Value(person.birthYear),
            phoneNumber: Value(person.phoneNumber),
            email: Value(person.email),
            relationship: Value(person.relationship.name),
            relationshipCloseness: Value(person.relationshipCloseness.name),
            preferredLanguage: Value(person.preferredLanguage),
            preferredTone: Value(person.preferredTone.name),
            importantFacts: Value(person.importantFacts.join('\n')),
            notes: Value(person.notes),
            preferredDeliveryChannel: Value(
              person.preferredDeliveryChannel.name,
            ),
            timezone: Value(person.timezone),
            autoPrepare: Value(person.autoPrepare),
            autoSendPolicy: const Value('manualOnly'),
            createdAt: Value(person.createdAt),
            updatedAt: Value(person.updatedAt),
            version: Value(person.version),
            deletedAt: const Value(null),
          ),
        );
  }

  @override
  Future<void> deletePerson(String id) async {
    await (_database.update(
      _database.persons,
    )..where((r) => r.id.equals(id))).write(
      db.PersonsCompanion(
        deletedAt: Value(DateTime.now().toUtc()),
        updatedAt: Value(DateTime.now().toUtc()),
      ),
    );
  }

  static Person _personFromRow(db.Person row) {
    return Person(
      id: row.id,
      name: row.name,
      birthdayMonth: row.birthdayMonth,
      birthdayDay: row.birthdayDay,
      birthYear: row.birthYear,
      phoneNumber: row.phoneNumber,
      email: row.email,
      relationship: RelationshipCategory.fromString(row.relationship),
      relationshipCloseness: RelationshipCloseness.fromString(
        row.relationshipCloseness,
      ),
      preferredLanguage: row.preferredLanguage.isEmpty
          ? 'en'
          : row.preferredLanguage,
      preferredTone: MessageTone.fromString(row.preferredTone),
      importantFacts: row.importantFacts
          .split('\n')
          .map((s) => s.trim())
          .where((s) => s.isNotEmpty)
          .toList(),
      notes: row.notes,
      preferredDeliveryChannel: DeliveryChannel.fromString(
        row.preferredDeliveryChannel,
      ),
      timezone: row.timezone,
      autoPrepare: row.autoPrepare,
      createdAt: row.createdAt.toUtc(),
      updatedAt: row.updatedAt.toUtc(),
      version: row.version,
    );
  }
}

/// Drift/SQLite-backed implementation of [BirthdaysRepository].
class DriftBirthdaysRepository implements BirthdaysRepository {
  DriftBirthdaysRepository(this._database);

  final db.AppDatabase _database;

  @override
  Stream<List<Birthday>> watchBirthdays() {
    return _database.select(_database.birthdays).watch().map((rows) {
      final list = rows.map(_birthdayFromRow).toList();
      list.sort((a, b) => a.date.compareTo(b.date));
      return list;
    });
  }

  @override
  Future<List<Birthday>> getBirthdays() async {
    final rows = await _database.select(_database.birthdays).get();
    final list = rows.map(_birthdayFromRow).toList();
    list.sort((a, b) => a.date.compareTo(b.date));
    return list;
  }

  @override
  Future<Birthday?> getBirthday(String id) async {
    final row = await (_database.select(
      _database.birthdays,
    )..where((r) => r.id.equals(id))).getSingleOrNull();
    return row == null ? null : _birthdayFromRow(row);
  }

  @override
  Future<Birthday?> getBirthdayForPerson(String personId) async {
    final row = await (_database.select(
      _database.birthdays,
    )..where((r) => r.personId.equals(personId))).getSingleOrNull();
    return row == null ? null : _birthdayFromRow(row);
  }

  @override
  Future<void> saveBirthday(Birthday birthday) async {
    await _database
        .into(_database.birthdays)
        .insertOnConflictUpdate(
          db.BirthdaysCompanion(
            id: Value(birthday.id),
            personId: Value(birthday.personId),
            cycleYear: Value(birthday.cycleYear),
            date: Value(birthday.date),
            status: Value(birthday.status.name),
            draftId: Value(birthday.draftId),
            createdAt: Value(birthday.createdAt),
            updatedAt: Value(birthday.updatedAt),
          ),
        );
  }

  @override
  Future<void> updateBirthdayStatus(
    String id,
    BirthdayStatus status, {
    String? draftId,
  }) async {
    final existing = await getBirthday(id);
    if (existing != null) {
      await (_database.update(
        _database.birthdays,
      )..where((r) => r.id.equals(id))).write(
        db.BirthdaysCompanion(
          status: Value(status.name),
          draftId: draftId != null ? Value(draftId) : Value(existing.draftId),
          updatedAt: Value(DateTime.now()),
        ),
      );
    }
  }

  @override
  Future<void> deleteBirthday(String id) async {
    await (_database.delete(
      _database.birthdays,
    )..where((r) => r.id.equals(id))).go();
  }

  static Birthday _birthdayFromRow(db.Birthday row) {
    return Birthday(
      id: row.id,
      personId: row.personId,
      cycleYear: row.cycleYear,
      date: row.date,
      status: BirthdayStatus.fromString(row.status),
      draftId: row.draftId,
      createdAt: row.createdAt.toUtc(),
      updatedAt: row.updatedAt.toUtc(),
    );
  }
}

/// Drift/SQLite-backed implementation of [DraftsRepository].
class DriftDraftsRepository implements DraftsRepository {
  DriftDraftsRepository(this._database);

  final db.AppDatabase _database;

  @override
  Stream<List<MessageDraft>> watchDrafts() {
    return _database.select(_database.messageDrafts).watch().map((rows) {
      final list = rows.map(_draftFromRow).toList();
      list.sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
      return list;
    });
  }

  @override
  Future<List<MessageDraft>> getAllDrafts() async {
    final rows = await _database.select(_database.messageDrafts).get();
    final list = rows.map(_draftFromRow).toList();
    list.sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
    return list;
  }

  @override
  Future<MessageDraft?> getDraft(String id) async {
    final row = await (_database.select(
      _database.messageDrafts,
    )..where((r) => r.id.equals(id))).getSingleOrNull();
    return row == null ? null : _draftFromRow(row);
  }

  @override
  Future<MessageDraft?> getDraftForBirthday(String birthdayId) async {
    final row = await (_database.select(
      _database.messageDrafts,
    )..where((r) => r.birthdayId.equals(birthdayId))).getSingleOrNull();
    return row == null ? null : _draftFromRow(row);
  }

  @override
  Future<void> saveDraft(MessageDraft draft) async {
    await _database
        .into(_database.messageDrafts)
        .insertOnConflictUpdate(
          db.MessageDraftsCompanion(
            id: Value(draft.id),
            birthdayId: Value(draft.birthdayId),
            personId: Value(draft.personId),
            body: Value(draft.body),
            tone: Value(draft.tone.name),
            length: Value(draft.length.name),
            status: Value(draft.status.name),
            providerType: Value(draft.providerType),
            variationIndex: Value(draft.variationIndex),
            createdAt: Value(draft.createdAt),
            updatedAt: Value(draft.updatedAt),
          ),
        );
  }

  @override
  Future<void> deleteDraft(String id) async {
    await (_database.delete(
      _database.messageDrafts,
    )..where((r) => r.id.equals(id))).go();
  }

  static MessageDraft _draftFromRow(db.MessageDraft row) {
    return MessageDraft(
      id: row.id,
      birthdayId: row.birthdayId,
      personId: row.personId,
      body: row.body,
      tone: MessageTone.fromString(row.tone),
      length: MessageLength.fromString(row.length),
      status: DraftStatus.fromString(row.status),
      providerType: row.providerType.isEmpty ? 'manual' : row.providerType,
      variationIndex: row.variationIndex,
      createdAt: row.createdAt.toUtc(),
      updatedAt: row.updatedAt.toUtc(),
    );
  }
}

/// Seeds demo people, birthdays, and drafts if the database has 0 people.
Future<void> seedInitialDataIfEmpty(db.AppDatabase database) async {
  try {
    final existing = await database.select(database.persons).get();
    if (existing.isNotEmpty) return;

    final now = DateTime.now();
    final p1 = Person(
      id: 'person-1',
      name: 'Sarah Connor',
      birthdayMonth: now.month,
      birthdayDay: now.day,
      birthYear: 1994,
      phoneNumber: '+14155552671',
      relationship: RelationshipCategory.friend,
      relationshipCloseness: RelationshipCloseness.close,
      preferredTone: MessageTone.warm,
      importantFacts: const [
        'Loves marathon running',
        'Adopted a rescue golden retriever',
      ],
      notes: 'Know each other from college running club.',
      createdAt: now.subtract(const Duration(days: 30)),
      updatedAt: now,
    );
    final p2 = Person(
      id: 'person-2',
      name: 'David Miller',
      birthdayMonth: now.add(const Duration(days: 3)).month,
      birthdayDay: now.add(const Duration(days: 3)).day,
      birthYear: 1988,
      phoneNumber: '+14155558912',
      relationship: RelationshipCategory.colleague,
      relationshipCloseness: RelationshipCloseness.casual,
      preferredTone: MessageTone.funny,
      importantFacts: const [
        'Coffee connoisseur',
        'Just promoted to Lead Architect',
      ],
      createdAt: now.subtract(const Duration(days: 60)),
      updatedAt: now,
    );
    final p3 = Person(
      id: 'person-3',
      name: 'Elena Rostova',
      birthdayMonth: now.add(const Duration(days: 14)).month,
      birthdayDay: now.add(const Duration(days: 14)).day,
      birthYear: 1996,
      phoneNumber: '+14155554321',
      relationship: RelationshipCategory.family,
      relationshipCloseness: RelationshipCloseness.close,
      preferredTone: MessageTone.emotional,
      importantFacts: const [
        'Passionate about watercolor painting',
        'New mother to baby Leo',
      ],
      createdAt: now.subtract(const Duration(days: 90)),
      updatedAt: now,
    );

    final peopleRepo = DriftPeopleRepository(database);
    final birthdaysRepo = DriftBirthdaysRepository(database);
    final draftsRepo = DriftDraftsRepository(database);

    await peopleRepo.savePerson(p1);
    await peopleRepo.savePerson(p2);
    await peopleRepo.savePerson(p3);

    await birthdaysRepo.saveBirthday(
      Birthday(
        id: 'birthday-1',
        personId: 'person-1',
        cycleYear: now.year,
        date: DateTime(now.year, now.month, now.day),
        status: BirthdayStatus.messageDrafted,
        draftId: 'draft-1',
        createdAt: now.subtract(const Duration(days: 7)),
        updatedAt: now,
      ),
    );
    await birthdaysRepo.saveBirthday(
      Birthday(
        id: 'birthday-2',
        personId: 'person-2',
        cycleYear: now.year,
        date: now.add(const Duration(days: 3)),
        status: BirthdayStatus.reminderDue,
        createdAt: now.subtract(const Duration(days: 7)),
        updatedAt: now,
      ),
    );
    await birthdaysRepo.saveBirthday(
      Birthday(
        id: 'birthday-3',
        personId: 'person-3',
        cycleYear: now.year,
        date: now.add(const Duration(days: 14)),
        status: BirthdayStatus.upcoming,
        createdAt: now.subtract(const Duration(days: 7)),
        updatedAt: now,
      ),
    );

    await draftsRepo.saveDraft(
      MessageDraft(
        id: 'draft-1',
        birthdayId: 'birthday-1',
        personId: 'person-1',
        body:
            'Happy Birthday Sarah! Wishing you another incredible year full of great marathon milestones and sweet moments with your pup! 🏃‍♀️🐕 Have a wonderful celebration!',
        tone: MessageTone.warm,
        length: MessageLength.standard,
        status: DraftStatus.draft,
        providerType: 'user_gemini',
        createdAt: now.subtract(const Duration(hours: 4)),
        updatedAt: now.subtract(const Duration(hours: 4)),
      ),
    );
  } catch (_) {
    // Non-fatal if initial seed fails
  }
}
