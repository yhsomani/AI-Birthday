import 'dart:async';

import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/people/domain/person.dart';
import 'package:ai_birthday/features/people/domain/repositories/people_repository.dart';

/// In-memory [PeopleRepository] for widget tests. Mirrors the Drift
/// repository's semantics: visible queries exclude tombstones and sort by name;
/// `savePerson` is idempotent by id.
class FakePeopleRepository implements PeopleRepository {
  FakePeopleRepository({DateTime Function()? now}) : _now = now ?? _defaultNow;

  static DateTime _defaultNow() => DateTime.utc(2025, 1, 1);

  final DateTime Function() _now;
  final Map<String, Person> _people = {};
  final Map<String, Birthday> _birthdays = {};
  final StreamController<List<Person>> _controller =
      StreamController<List<Person>>.broadcast();

  List<Person> _snapshot({bool includeDeleted = false}) {
    final visible = includeDeleted
        ? _people.values.toList()
        : _people.values.where((p) => p.deletedAt == null).toList();
    visible.sort((a, b) {
      final byName = a.name.toLowerCase().compareTo(b.name.toLowerCase());
      return byName != 0 ? byName : a.id.compareTo(b.id);
    });
    return visible;
  }

  void _emit() => _controller.add(_snapshot());

  @override
  Future<List<Person>> listActivePeople({bool includeDeleted = false}) async =>
      _snapshot(includeDeleted: includeDeleted);

  @override
  Stream<List<Person>> watchActivePeople({bool includeDeleted = false}) {
    scheduleMicrotask(
      () => _controller.add(_snapshot(includeDeleted: includeDeleted)),
    );
    return _controller.stream;
  }

  @override
  Future<Person?> getPersonById(
    String id, {
    bool includeDeleted = false,
  }) async {
    final p = _people[id];
    if (p == null) return null;
    if (!includeDeleted && p.deletedAt != null) return null;
    return p;
  }

  @override
  Future<void> savePerson(Person person) async {
    final existing = _people[person.id];
    final stored = person.deletedAt == null && existing?.deletedAt != null
        ? person.copyWith(deletedAt: existing!.deletedAt)
        : person;
    _people[person.id] = stored;
    _emit();
  }

  @override
  Future<void> savePersonAndBirthday(Person person, Birthday? birthday) async {
    _people[person.id] = person;
    if (birthday != null) _birthdays[birthday.id] = birthday;
    _emit();
  }

  @override
  Future<void> saveImportBatch({
    required List<Person> people,
    required List<Birthday> birthdays,
  }) async {
    final ids = people.map((person) => person.id).toSet();
    if (ids.length != people.length ||
        birthdays.any((birthday) => !ids.contains(birthday.personId))) {
      throw ArgumentError('Import batch contains duplicate or orphan records.');
    }
    for (final person in people) {
      if (_people.containsKey(person.id)) {
        throw StateError('Imported person already exists.');
      }
    }
    for (final person in people) {
      _people[person.id] = person;
    }
    for (final birthday in birthdays) {
      _birthdays[birthday.id] = birthday;
    }
    _emit();
  }

  @override
  Future<Birthday?> getBirthdayForPerson(String personId) async {
    for (final birthday in _birthdays.values) {
      if (birthday.personId == personId) return birthday;
    }
    return null;
  }

  @override
  Future<void> softDeletePerson(String personId, DateTime deletedAt) async {
    final existing = _people[personId];
    if (existing == null) return;
    _people[personId] = _clone(
      existing,
      deletedAt: deletedAt.toUtc(),
      version: existing.version + 1,
    );
    _birthdays.removeWhere((_, b) => b.personId == personId);
    _emit();
  }

  @override
  Future<void> restorePerson(Person person, Birthday? birthday) async {
    final existing = _people[person.id];
    if (existing == null) return;
    _people[person.id] = _clone(
      existing,
      deletedAt: null,
      version: existing.version + 1,
    );
    if (birthday != null) {
      _birthdays[birthday.id] = birthday;
    }
    _emit();
  }

  @override
  Future<int> purgeExpiredTombstones(DateTime olderThan) async {
    final cutoff = olderThan.toUtc();
    final expiredIds = _people.values
        .where((p) => p.deletedAt != null && p.deletedAt!.isBefore(cutoff))
        .map((p) => p.id)
        .toList();
    for (final id in expiredIds) {
      _people.remove(id);
      _birthdays.removeWhere((_, b) => b.personId == id);
    }
    if (expiredIds.isNotEmpty) _emit();
    return expiredIds.length;
  }

  Person _clone(Person p, {DateTime? deletedAt, int? version}) => Person(
    id: p.id,
    name: p.name,
    birthdayMonth: p.birthdayMonth,
    birthdayDay: p.birthdayDay,
    birthYear: p.birthYear,
    leapDayResolution: p.leapDayResolution,
    phoneNumber: p.phoneNumber,
    email: p.email,
    relationship: p.relationship,
    relationshipCloseness: p.relationshipCloseness,
    preferredLanguage: p.preferredLanguage,
    preferredTone: p.preferredTone,
    importantFacts: p.importantFacts,
    notes: p.notes,
    preferredDeliveryChannel: p.preferredDeliveryChannel,
    timezone: p.timezone,
    autoPrepare: p.autoPrepare,
    autoSendPolicy: p.autoSendPolicy,
    createdAt: p.createdAt,
    updatedAt: _now(),
    version: version ?? p.version,
    deletedAt: deletedAt,
  );

  /// Test-only scan of all rows (including tombstones), unsorted.
  List<Person> get allRows => _people.values.toList();

  Future<void> close() => _controller.close();
}
