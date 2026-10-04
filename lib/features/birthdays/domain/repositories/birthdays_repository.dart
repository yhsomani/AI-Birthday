import 'dart:async';

import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';

abstract interface class BirthdaysRepository {
  Stream<List<Birthday>> watchBirthdays();
  Future<List<Birthday>> getBirthdays();
  Future<Birthday?> getBirthday(String id);
  Future<Birthday?> getBirthdayForPerson(String personId);
  Future<void> saveBirthday(Birthday birthday);
  Future<void> updateBirthdayStatus(
    String id,
    BirthdayStatus status, {
    String? draftId,
  });
  Future<void> deleteBirthday(String id);
}

/// In-memory implementation of [BirthdaysRepository] for testing and rapid startup.
class InMemoryBirthdaysRepository implements BirthdaysRepository {
  InMemoryBirthdaysRepository({List<Birthday>? initialBirthdays}) {
    if (initialBirthdays != null) {
      for (final b in initialBirthdays) {
        _store[b.id] = b;
      }
    }
  }

  final Map<String, Birthday> _store = {};
  final StreamController<List<Birthday>> _controller =
      StreamController<List<Birthday>>.broadcast();

  @override
  Stream<List<Birthday>> watchBirthdays() async* {
    yield _sortedBirthdays();
    yield* _controller.stream;
  }

  @override
  Future<List<Birthday>> getBirthdays() async => _sortedBirthdays();

  @override
  Future<Birthday?> getBirthday(String id) async => _store[id];

  @override
  Future<Birthday?> getBirthdayForPerson(String personId) async {
    return _store.values.cast<Birthday?>().firstWhere(
      (b) => b?.personId == personId,
      orElse: () => null,
    );
  }

  @override
  Future<void> saveBirthday(Birthday birthday) async {
    _store[birthday.id] = birthday;
    _controller.add(_sortedBirthdays());
  }

  @override
  Future<void> updateBirthdayStatus(
    String id,
    BirthdayStatus status, {
    String? draftId,
  }) async {
    final existing = _store[id];
    if (existing != null) {
      _store[id] = existing.copyWith(
        status: status,
        draftId: draftId ?? existing.draftId,
        updatedAt: DateTime.now(),
      );
      _controller.add(_sortedBirthdays());
    }
  }

  @override
  Future<void> deleteBirthday(String id) async {
    _store.remove(id);
    _controller.add(_sortedBirthdays());
  }

  List<Birthday> _sortedBirthdays() {
    final list = _store.values.toList();
    list.sort((a, b) => a.date.compareTo(b.date));
    return list;
  }
}

