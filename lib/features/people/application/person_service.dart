import 'package:clock/clock.dart' as clock;
import 'package:uuid/uuid.dart';

import '../../../core/errors/app_failure.dart';
import '../../../core/logging/app_logger.dart';
import '../data/person_repository.dart';
import '../../birthdays/domain/birthday_engine.dart';
import '../../birthdays/domain/models/birthday.dart';
import '../domain/person.dart';
import '../domain/person_input.dart';
import '../domain/person_input_validator.dart';
import '../domain/person_enums.dart' show AutoSendPolicy;
import 'package:ai_birthday/core/config/product_capabilities.dart';

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
    final safePerson = person.copyWith(
      autoPrepare: ProductCapabilities.autoPrepare && person.autoPrepare,
      autoSendPolicy: ProductCapabilities.automaticSms
          ? person.autoSendPolicy
          : AutoSendPolicy.manualOnly,
    );
    final birthday = _birthdayFor(safePerson, null);
    await _store.saveAggregate(safePerson, birthday: birthday);
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
    await _store.saveAggregate(safeUpdated, birthday: birthday);
    _logger.info(
      'people',
      'updated person',
      params: {'personId': safeUpdated.id, 'version': safeUpdated.version},
    );
    await _onChanged?.call();
    return safeUpdated;
  }

  /// Validates and persists an imported batch as one atomic aggregate write.
  Future<List<Person>> importBatch(List<PersonDraft> drafts) async {
    final people = <Person>[];
    final birthdays = <Birthday>[];
    for (final draft in drafts) {
      _ensureValid(draft);
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

    if (people.isEmpty) return const [];

    await _store.saveImportBatch(people: people, birthdays: birthdays);
    _logger.info(
      'people',
      'imported contacts',
      params: {'count': people.length},
    );
    await _onChanged?.call();
    return List.unmodifiable(people);
  }

  Future<void> remove(String id) async {
    _logger.info('people', 'removed person', params: {'personId': id});
    await _store.softDeleteAggregate(id);
    await _onChanged?.call();
  }

  /// Restores a soft-deleted recipient, clearing its tombstone (undo).
  Future<void> restore(String id, {Birthday? birthday}) async {
    _logger.info('people', 'restored person', params: {'personId': id});
    await _store.restoreAggregate(id, restoredBirthday: birthday);
    await _onChanged?.call();
  }

  Future<Birthday?> _nextBirthday(Person person) async {
    if (!person.hasBirthday) return null;
    final prior = await _store.getBirthdayForPerson(person.id);
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
            prior.date.month != next.nextDate.month ||
            prior.date.day != next.nextDate.day);
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
