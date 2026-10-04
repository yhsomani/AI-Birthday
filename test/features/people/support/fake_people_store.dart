import 'dart:async';

import 'package:ai_birthday/features/people/data/person_repository.dart';
import 'package:ai_birthday/features/people/domain/person.dart';

/// In-memory [PeopleStore] for widget tests. Mirrors the Drift store's
/// semantics: visible queries exclude tombstones and sort by name; `save` is
/// idempotent by id.
class FakePeopleStore implements PeopleStore {
  FakePeopleStore({DateTime Function()? now}) : _now = now ?? _defaultNow;

  static DateTime _defaultNow() => DateTime.utc(2025, 1, 1);

  final DateTime Function() _now;
  final Map<String, Person> _people = {};
  final StreamController<List<Person>> _controller =
      StreamController<List<Person>>.broadcast();

  List<Person> _snapshot({bool includeDeleted = false}) {
    final visible = includeDeleted
        ? _people.values.toList()
        : _people.values.where((p) => p.deletedAt == null).toList();
    visible.sort(
      (a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()),
    );
    return visible;
  }

  void _emit() => _controller.add(_snapshot());

  @override
  Future<List<Person>> getAll({bool includeDeleted = false}) async =>
      includeDeleted
      ? _people.values.toList()
      : _snapshot(includeDeleted: includeDeleted);

  @override
  Stream<List<Person>> watchAll({bool includeDeleted = false}) {
    // Broadcast controllers drop events without listeners; the provider only
    // subscribes after `watchAll` returns. Emit on the next microtask so the
    // stream always carries the current snapshot.
    scheduleMicrotask(
      () => _controller.add(_snapshot(includeDeleted: includeDeleted)),
    );
    return _controller.stream;
  }

  @override
  Future<Person?> getById(String id) async => _people[id];

  @override
  Future<void> save(Person person) async {
    _people[person.id] = person;
    _emit();
  }

  @override
  Future<void> softDelete(String id) async {
    final existing = _people[id];
    if (existing == null) return;
    _people[id] = _clone(existing, deletedAt: _now());
    _emit();
  }

  @override
  Future<void> restore(String id) async {
    final existing = _people[id];
    if (existing == null) return;
    _people[id] = _clone(existing, deletedAt: null);
    _emit();
  }

  static Person _clone(Person p, {DateTime? deletedAt}) => Person(
    id: p.id,
    name: p.name,
    birthdayMonth: p.birthdayMonth,
    birthdayDay: p.birthdayDay,
    birthYear: p.birthYear,
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
    updatedAt: p.updatedAt,
    version: p.version,
    deletedAt: deletedAt,
  );

  /// Test-only scan of all rows (including tombstones), unsorted.
  List<Person> get allRows => _people.values.toList();

  Future<void> close() => _controller.close();
}
