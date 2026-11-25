import '../../people/domain/models/person.dart';
import '../domain/birthday_engine.dart';
import '../domain/models/birthday.dart';
import '../domain/repositories/birthdays_repository.dart';

/// Keeps the stored birthday event aligned with the next real calendar cycle.
///
/// The database stores one occurrence per person and cycle. On app/provider
/// startup this service aligns the current occurrence for dashboard, reminder
/// and message workflows while keeping older cycles attached to their own
/// dependent records.
class BirthdayLifecycleService {
  const BirthdayLifecycleService({
    this.engine = const BirthdayEngine(),
    DateTime Function()? now,
  }) : _now = now ?? DateTime.now;

  final BirthdayEngine engine;
  final DateTime Function() _now;

  Future<void> refresh({
    required List<Person> people,
    required BirthdaysRepository birthdaysRepository,
  }) async {
    final reference = _now();

    // Performance audit: previously this loop issued one SELECT per person
    // (`getBirthdayForPerson`), an N+1 that ran on every `birthdaysStreamProvider`
    // build. Load all events once and index by person. Duplicate rows for one
    // person (restore/sync/legacy bugs) must never blank the dashboard. Merge
    // duplicate rows only when they represent the same cycle;
    // drafts and delivery evidence from another cycle must retain their owner.
    final birthdaysByPerson = <String, List<Birthday>>{};
    for (final birthday in await birthdaysRepository.getBirthdays()) {
      birthdaysByPerson.putIfAbsent(birthday.personId, () => []).add(birthday);
    }
    final existingByPerson = <String, List<Birthday>>{};
    for (final entry in birthdaysByPerson.entries) {
      final eventsByCycle = <int, List<Birthday>>{};
      for (final event in entry.value) {
        eventsByCycle.putIfAbsent(event.cycleYear, () => []).add(event);
      }

      final canonicalEvents = <Birthday>[];
      for (final events in eventsByCycle.values) {
        var canonical = events.reduce(
          (current, candidate) =>
              _isNewer(candidate, current) ? candidate : current,
        );
        if (events.length > 1) {
          final linked = events
              .where((event) => event.draftId != null)
              .toList();
          if (canonical.draftId == null && linked.isNotEmpty) {
            final draftOwner = linked.reduce(
              (current, candidate) =>
                  _isNewer(candidate, current) ? candidate : current,
            );
            canonical = canonical.copyWith(
              draftId: draftOwner.draftId,
              status: draftOwner.status,
            );
          }
          await birthdaysRepository.mergeDuplicateBirthdays(
            canonical,
            duplicateIds: events
                .where((event) => event.id != canonical.id)
                .map((event) => event.id)
                .toList(),
          );
        }
        canonicalEvents.add(canonical);
      }
      existingByPerson[entry.key] = canonicalEvents;
    }

    for (final person in people) {
      if (!person.hasBirthday) continue;
      if (person.isLeapDayBirthday && person.leapDayResolution == null) {
        // Legacy data is deliberately preserved until the user chooses a
        // policy. Scheduling/UI consumers suppress it through resolvability.
        continue;
      }

      final next = engine.computeNext(
        month: person.birthdayMonth!,
        day: person.birthdayDay!,
        birthYear: person.birthYear,
        timezoneName: person.timezone,
        leapDay: person.leapDayResolution,
        reference: reference,
      );
      if (next == null) continue;

      final storedEvents = existingByPerson[person.id] ?? const <Birthday>[];
      Birthday? existing;
      for (final event in storedEvents) {
        if (event.cycleYear == next.year &&
            event.isSameCalendarDate(next.nextDate)) {
          existing = event;
          break;
        }
      }
      existing ??= storedEvents.isEmpty
          ? null
          : storedEvents.reduce(
              (current, candidate) =>
                  _isNewer(candidate, current) ? candidate : current,
            );
      final targetStatus = next.isToday
          ? BirthdayStatus.reminderDue
          : BirthdayStatus.upcoming;

      if (existing == null) {
        final now = _now();
        await birthdaysRepository.saveBirthday(
          Birthday(
            id: 'birthday-${person.id}',
            personId: person.id,
            cycleYear: next.year,
            date: next.nextDate,
            status: targetStatus,
            createdAt: now,
            updatedAt: now,
          ),
        );
        continue;
      }

      final cycleMatches =
          existing.cycleYear == next.year &&
          existing.isSameCalendarDate(next.nextDate);

      if (cycleMatches) {
        final needsTodayStatus =
            targetStatus == BirthdayStatus.reminderDue &&
            (existing.status == BirthdayStatus.created ||
                existing.status == BirthdayStatus.upcoming);
        if (needsTodayStatus) {
          final now = _now();
          await birthdaysRepository.saveBirthday(
            existing.copyWith(status: targetStatus, updatedAt: now),
          );
        }
        continue;
      }

      final now = _now();
      await birthdaysRepository.rolloverBirthday(
        existing.copyWith(
          cycleYear: next.year,
          date: next.nextDate,
          status: targetStatus,
          clearDraftId: true,
          updatedAt: now,
        ),
      );
    }
  }

  static bool _isNewer(Birthday candidate, Birthday current) {
    final byUpdated = candidate.updatedAt.compareTo(current.updatedAt);
    if (byUpdated != 0) return byUpdated > 0;
    final byCreated = candidate.createdAt.compareTo(current.createdAt);
    if (byCreated != 0) return byCreated > 0;
    return candidate.id.compareTo(current.id) > 0;
  }
}
