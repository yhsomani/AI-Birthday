import 'package:clock/clock.dart' as clock;
import 'package:uuid/uuid.dart';

import '../../../core/errors/app_failure.dart';
import '../../../core/logging/app_logger.dart';
import '../domain/repositories/people_repository.dart';
import '../../birthdays/domain/birthday_engine.dart';
import '../../birthdays/domain/models/birthday.dart';
import '../domain/person.dart';
import '../domain/person_input.dart';
import '../domain/person_input_validator.dart';
import '../domain/person_enums.dart' show AutoSendPolicy;
import 'package:ai_birthday/core/config/product_capabilities.dart';

/// One import row rejected by validation, with every reason it failed.
class PersonImportFailure {
  const PersonImportFailure({
    required this.index,
    required this.name,
    required this.reasons,
  });

  /// Zero-based position of the row in the submitted list.
  final int index;
  final String name;
  final List<String> reasons;
}

/// Outcome of a per-row import: what was saved and what was skipped.
class PersonImportResult {
  const PersonImportResult({required this.imported, required this.failures});

  final List<Person> imported;
  final List<PersonImportFailure> failures;
}

/// Application service for recipient lifecycle (legacy SSOT §7 (PRODUCT.md §16.7)).
///
/// Owns validation, id generation, the sync envelope (version bumps) and
/// sanitized observability. Delivery policy is never changed here: only
/// [AutoSendPolicy.manualOnly] is supported by the product (legacy SSOT §9 (PRODUCT.md §16.7)).
class PersonService {
  PersonService(
    this._repository,
    this._validator,
    this._logger, {
    DateTime Function()? now,
    String Function()? newId,
    this._onChanged,
  }) : _now = now ?? clock.clock.now,
       _newId = newId ?? (() => const Uuid().v4());

  final PeopleRepository _repository;
  final PersonInputValidator _validator;
  final AppLogger _logger;
  final DateTime Function() _now;
  final String Function() _newId;
  final Future<void> Function()? _onChanged;

  Stream<List<Person>> watchAll() => _repository.watchActivePeople();

  Future<List<Person>> getAll() => _repository.listActivePeople();

  Future<Person?> getById(String id, {bool includeDeleted = false}) =>
      _repository.getPersonById(id, includeDeleted: includeDeleted);

  /// Validates [draft] and persists it as a new recipient.
  Future<Person> create(PersonDraft draft) async {
    _ensureValid(draft);
    final person = draft.toPerson(id: _newId(), now: _now());
    final safePerson = person.copyWith(
      autoPrepare: ProductCapabilities.autoPrepare && person.autoPrepare,
      autoSendPolicy: ProductCapabilities.automaticSms
          ? person.autoSendPolicy
          : AutoSendPolicy.manualOnly,
    );
    final birthday = _birthdayFor(safePerson, null);
    await _repository.savePersonAndBirthday(safePerson, birthday);
    _logger.info(
      'people',
      'created person',
      params: {'personId': safePerson.id},
    );
    await _onChanged?.call();
    return safePerson;
  }

  /// Validates [draft] and applies it to an existing recipient, bumping the
  /// sync-envelope version so updates remain deterministic.
  Future<Person> update(Person existing, PersonDraft draft) async {
    _ensureValid(draft, requireBirthday: existing.hasBirthday);
    final updated = draft.applyTo(existing, now: _now());
    final safeUpdated = updated.copyWith(
      autoPrepare: ProductCapabilities.autoPrepare && updated.autoPrepare,
      autoSendPolicy: ProductCapabilities.automaticSms
          ? updated.autoSendPolicy
          : AutoSendPolicy.manualOnly,
    );
    final birthday = await _nextBirthday(safeUpdated);
    await _repository.savePersonAndBirthday(safeUpdated, birthday);
    _logger.info(
      'people',
      'updated person',
      params: {'personId': safeUpdated.id, 'version': safeUpdated.version},
    );
    await _onChanged?.call();
    return safeUpdated;
  }

  /// Validates and persists an imported batch as one atomic aggregate write.
  ///
  /// Strict: a single invalid draft rejects the whole batch. Review-and-import
  /// flows that must not lose valid rows use [importValidRows] instead.
  Future<List<Person>> importBatch(List<PersonDraft> drafts) async {
    for (final draft in drafts) {
      _ensureValid(draft);
    }
    final result = await importValidRows(drafts);
    return result.imported;
  }

  /// Validates each draft on its own, imports every valid one as one atomic
  /// write and reports invalid rows individually (audit A1-006).
  ///
  /// Storage failures still reject the whole write and rethrow, because the
  /// valid rows then share one rolled-back transaction.
  Future<PersonImportResult> importValidRows(List<PersonDraft> drafts) async {
    final people = <Person>[];
    final birthdays = <Birthday>[];
    final failures = <PersonImportFailure>[];
    for (final (index, draft) in drafts.indexed) {
      final validation = _validator.validate(draft, requireBirthday: true);
      if (!validation.isValid) {
        failures.add(
          PersonImportFailure(
            index: index,
            name: draft.name,
            reasons: [for (final e in validation.fieldErrors.values) e.label],
          ),
        );
        continue;
      }
      final person = draft.toPerson(id: _newId(), now: _now());
      final safePerson = person.copyWith(
        autoPrepare: ProductCapabilities.autoPrepare && person.autoPrepare,
        autoSendPolicy: ProductCapabilities.automaticSms
            ? person.autoSendPolicy
            : AutoSendPolicy.manualOnly,
      );
      final birthday = _birthdayFor(safePerson, null);
      people.add(safePerson);
      if (birthday != null) birthdays.add(birthday);
    }

    if (failures.isNotEmpty) {
      _logger.warning(
        'people',
        'import rows rejected by validation',
        params: {'rejected': failures.length, 'accepted': people.length},
      );
    }
    if (people.isEmpty) {
      return PersonImportResult(imported: const [], failures: failures);
    }

    await _repository.saveImportBatch(people: people, birthdays: birthdays);
    _logger.info(
      'people',
      'imported contacts',
      params: {'count': people.length},
    );
    await _onChanged?.call();
    return PersonImportResult(
      imported: List.unmodifiable(people),
      failures: List.unmodifiable(failures),
    );
  }

  Future<void> remove(String id) async {
    _logger.info('people', 'removed person', params: {'personId': id});
    await _repository.softDeletePerson(id, _now());
    await _onChanged?.call();
  }

  /// Restores a soft-deleted recipient, clearing its tombstone (undo).
  Future<void> restore(String id, {Birthday? birthday}) async {
    _logger.info('people', 'restored person', params: {'personId': id});
    final existing = await _repository.getPersonById(id, includeDeleted: true);
    if (existing == null) return;
    await _repository.restorePerson(existing, birthday);
    await _onChanged?.call();
  }

  Future<Birthday?> _nextBirthday(Person person) async {
    if (!person.hasBirthday) return null;
    final prior = await _repository.getBirthdayForPerson(person.id);
    return _birthdayFor(person, prior);
  }

  Birthday? _birthdayFor(Person person, Birthday? prior) {
    if (!person.hasBirthday) return null;
    final now = _now();
    final next = const BirthdayEngine().computeNext(
      month: person.birthdayMonth!,
      day: person.birthdayDay!,
      birthYear: person.birthYear,
      timezoneName: person.timezone,
      leapDay: person.leapDayResolution,
      reference: now,
    );
    if (next == null) return null;
    final dateChanged =
        prior != null &&
        (prior.cycleYear != next.year ||
            !prior.isSameCalendarDate(next.nextDate));
    final status = dateChanged
        ? (next.isToday ? BirthdayStatus.reminderDue : BirthdayStatus.upcoming)
        : prior?.status ??
              (next.isToday
                  ? BirthdayStatus.reminderDue
                  : BirthdayStatus.upcoming);
    return Birthday(
      id: prior?.id ?? 'birthday-${person.id}',
      personId: person.id,
      cycleYear: next.year,
      date: next.nextDate,
      status: status,
      draftId: dateChanged ? null : prior?.draftId,
      createdAt: prior?.createdAt ?? now,
      updatedAt: now,
    );
  }

  void _ensureValid(PersonDraft draft, {bool requireBirthday = true}) {
    final validation = _validator.validate(
      draft,
      requireBirthday: requireBirthday,
    );
    if (!validation.isValid) {
      final labels = validation.fieldErrors.values.map((e) => e.label).toList();
      throw AppFailure.validation(
        detail: 'Cannot save recipient. ${labels.join(' ')}',
      );
    }
  }
}
