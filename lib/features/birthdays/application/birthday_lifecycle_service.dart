import '../../people/domain/models/person.dart';
import '../domain/birthday_engine.dart';
import '../domain/models/birthday.dart';
import '../domain/repositories/birthdays_repository.dart';

/// Keeps the stored birthday event aligned with the next real calendar cycle.
///
/// The database stores one active occurrence per person for dashboard,
/// reminder and message workflows. On app/provider startup this service moves
/// an elapsed occurrence to the next cycle instead of leaving the previous
/// year's date visible indefinitely.
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
    for (final person in people) {
      if (!person.hasBirthday) continue;

      final next = engine.computeNext(
        month: person.birthdayMonth!,
        day: person.birthdayDay!,
        birthYear: person.birthYear,
        timezoneName: person.timezone,
        reference: reference,
      );

      final existing = await birthdaysRepository.getBirthdayForPerson(person.id);
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
          existing.date.year == next.nextDate.year &&
          existing.date.month == next.nextDate.month &&
          existing.date.day == next.nextDate.day;

      if (cycleMatches) continue;

      final now = _now();
      await birthdaysRepository.saveBirthday(
        existing.copyWith(
          cycleYear: next.year,
          date: next.nextDate,
          status: targetStatus,
          draftId: null,
          updatedAt: now,
        ),
      );
    }
  }
}
