import 'package:clock/clock.dart' as clock;
import 'package:uuid/uuid.dart';

import '../../../core/errors/app_failure.dart';
import '../../../core/logging/app_logger.dart';
import '../data/person_repository.dart';
import '../domain/person.dart';
import '../domain/person_input.dart';
import '../domain/person_input_validator.dart';

/// Application service for recipient lifecycle (SSOT §7).
///
/// Owns validation, id generation, the sync envelope (version bumps) and
/// sanitized observability. Delivery policy is never changed here: only
/// [AutoSendPolicy.manualOnly] is supported by the product (SSOT §9).
class PersonService {
  PersonService(
    this._store,
    this._validator,
    this._logger, {
    DateTime Function()? now,
    String Function()? newId,
    Future<void> Function()? onChanged,
  }) : _now = now ?? clock.clock.now,
       _newId = newId ?? (() => const Uuid().v4()),
       _onChanged = onChanged;

  final PeopleStore _store;
  final PersonInputValidator _validator;
  final AppLogger _logger;
  final DateTime Function() _now;
  final String Function() _newId;
  final Future<void> Function()? _onChanged;

  Stream<List<Person>> watchAll() => _store.watchAll();

  Future<List<Person>> getAll() => _store.getAll();

  Future<Person?> getById(String id) => _store.getById(id);

  /// Validates [draft] and persists it as a new recipient.
  Future<Person> create(PersonDraft draft) async {
    _ensureValid(draft);
    final person = draft.toPerson(id: _newId(), now: _now());
    await _store.save(person);
    _logger.info('people', 'created person', params: {'personId': person.id});
    await _onChanged?.call();
    return person;
  }

  /// Validates [draft] and applies it to an existing recipient, bumping the
  /// sync-envelope version so updates remain deterministic.
  Future<Person> update(Person existing, PersonDraft draft) async {
    _ensureValid(draft);
    final updated = draft.applyTo(existing, now: _now());
    await _store.save(updated);
    _logger.info(
      'people',
      'updated person',
      params: {'personId': updated.id, 'version': updated.version},
    );
    await _onChanged?.call();
    return updated;
  }

  Future<void> remove(String id) async {
    _logger.info('people', 'removed person', params: {'personId': id});
    await _store.softDelete(id);
    await _onChanged?.call();
  }

  /// Restores a soft-deleted recipient, clearing its tombstone (undo).
  Future<void> restore(String id) async {
    _logger.info('people', 'restored person', params: {'personId': id});
    await _store.restore(id);
    await _onChanged?.call();
  }

  void _ensureValid(PersonDraft draft) {
    final validation = _validator.validate(draft);
    if (!validation.isValid) {
      final labels = validation.fieldErrors.values.map((e) => e.label).toList();
      throw AppFailure.validation(
        detail: 'Cannot save recipient. ${labels.join(' ')}',
      );
    }
  }
}
