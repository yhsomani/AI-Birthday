/// Repository contract for managing contacts and recipient preferences (SSOT §7, §18).
library;

import 'package:ai_birthday/features/people/domain/models/person.dart';

abstract interface class PeopleRepository {
  Stream<List<Person>> watchPeople();
  Future<List<Person>> getPeople();
  Future<Person?> getPerson(String id);
  Future<void> savePerson(Person person);
  Future<void> deletePerson(String id);
}

/// In-memory repository with seed data for fast testing and development.
class InMemoryPeopleRepository implements PeopleRepository {
  InMemoryPeopleRepository({List<Person>? initialPeople}) {
    if (initialPeople != null) {
      for (final p in initialPeople) {
        _store[p.id] = p;
      }
    }
  }

  final Map<String, Person> _store = {};

  @override
  Stream<List<Person>> watchPeople() async* {
    yield _sortedPeople();
  }

  @override
  Future<List<Person>> getPeople() async => _sortedPeople();

  @override
  Future<Person?> getPerson(String id) async => _store[id];

  @override
  Future<void> savePerson(Person person) async {
    _store[person.id] = person;
  }

  @override
  Future<void> deletePerson(String id) async {
    _store.remove(id);
  }

  List<Person> _sortedPeople() {
    final list = _store.values.toList();
    list.sort((a, b) => a.name.compareTo(b.name));
    return list;
  }
}
