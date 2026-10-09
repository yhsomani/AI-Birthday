import 'package:timezone/timezone.dart' as tz;

/// How a Feb 29 birthday is treated in a non-leap year (SSOT §14).
enum LeapDayResolution {
  /// Celebrate on Feb 28 (default).
  feb28,

  /// Celebrate on Mar 1.
  mar1,
}

/// The next occurrence of a recipient birthday relative to a reference date.
class NextBirthday {
  const NextBirthday({
    required this.month,
    required this.day,
    required this.year,
    required this.nextDate,
    required this.daysUntil,
    required this.isToday,
    required this.isLeapDayAdjusted,
  });

  final int month;
  final int day;
  final int year;

  /// Wall-clock date of the next occurrence in the resolved calendar frame
  /// (the recipient timezone when [BirthdayEngine.computeNext] received a
  /// named timezone, otherwise the caller's own wall-clock fields).
  final DateTime nextDate;

  /// Whole calendar days between the reference date and [nextDate]; 0 when the
  /// birthday is today.
  final int daysUntil;

  final bool isToday;

  /// True when the stored date was Feb 29 and [year] is not a leap year, so an
  /// alternate date was used.
  final bool isLeapDayAdjusted;
}

/// Pure, timezone-aware birthday calculations (SSOT §14).
///
/// Recurrence operates on calendar dates: a birthday is the year-day
/// (month, day) in the recipient timezone. Non-leap years resolve Feb 29 per
/// [LeapDayResolution]. Day counts are pure calendar-date differences, so DST
/// does not drift them.
class BirthdayEngine {
  const BirthdayEngine();

  static const int feb29Month = 2;
  static const int feb29Day = 29;
  static const String fallbackTimezone = 'UTC';

  static bool isLeapYear(int year) =>
      (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);

  static int daysInMonth(int month, int year) {
    const months = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
    if (month < 1 || month > 12) return 0;
    if (month == 2 && isLeapYear(year)) return 29;
    return months[month - 1];
  }

  /// Resolves a Feb 29 into a non-leap [year] per [resolution].
  ({int month, int day}) resolveFeb29(
    int year, {
    LeapDayResolution resolution = LeapDayResolution.feb28,
  }) {
    assert(
      !isLeapYear(year),
      'resolveFeb29 must only be called for non-leap years',
    );
    return resolution == LeapDayResolution.feb28
        ? (month: 2, day: 28)
        : (month: 3, day: 1);
  }

  /// True when (month, day) is a valid year-day.
  ///
  /// When [year] is null (birth year unknown) Feb 29 is accepted; otherwise it
  /// must fit the calendar of that year (a non-leap Feb 29 is still accepted,
  /// since [leapDay] resolves it).
  bool isValidMonthDay(
    int month,
    int day, {
    int? year,
    LeapDayResolution leapDay = LeapDayResolution.feb28,
  }) {
    if (month < 1 || month > 12 || day < 1) return false;
    if (year == null) {
      final maxDay = switch (month) {
        2 => 29,
        4 || 6 || 9 || 11 => 30,
        _ => 31,
      };
      return day <= maxDay;
    }
    if (month == feb29Month && day == feb29Day && !isLeapYear(year)) {
      return true; // resolvable via [leapDay]
    }
    return day <= daysInMonth(month, year);
  }

  /// Computes the next occurrence of a (month, day) birthday relative to
  /// [reference] (default: now in UTC).
  ///
  /// When [timezoneName] is provided the recipient's calendar is used and
  /// [reference] is treated as an absolute instant converted to that zone.
  /// Otherwise [reference]'s own wall-clock fields define the calendar frame
  /// (the user's local device frame).
  NextBirthday computeNext({
    required int month,
    required int day,
    int? birthYear,
    String? timezoneName,
    LeapDayResolution leapDay = LeapDayResolution.feb28,
    DateTime? reference,
  }) {
    final ref = reference ?? DateTime.now().toUtc();
    if (timezoneName != null) {
      return _computeInTimezone(
        month: month,
        day: day,
        timezoneName: timezoneName,
        leapDay: leapDay,
        reference: ref.toUtc(),
      );
    }
    // Wall-clock frame is the device's local calendar. A UTC default has UTC
    // wall-clock fields, which show yesterday's date between local midnight
    // and the UTC offset (fixed after a manual test at 00:30 IST).
    return _computeWallClock(
      month: month,
      day: day,
      leapDay: leapDay,
      reference: ref.isUtc ? ref.toLocal() : ref,
    );
  }

  NextBirthday _computeWallClock({
    required int month,
    required int day,
    required LeapDayResolution leapDay,
    required DateTime reference,
  }) {
    final refDate = DateTime(reference.year, reference.month, reference.day);
    var year = reference.year;
    final (occurrence, adjusted) = _occurrenceInYear(
      year,
      month: month,
      day: day,
      leapDay: leapDay,
    );
    if (occurrence.isBefore(refDate)) {
      year += 1;
      final (next, adjustedNext) = _occurrenceInYear(
        year,
        month: month,
        day: day,
        leapDay: leapDay,
      );
      return _result(
        month: month,
        day: day,
        year: year,
        nextDate: next,
        refDate: refDate,
        adjusted: adjustedNext,
      );
    }
    final sameDay =
        occurrence.year == refDate.year &&
        occurrence.month == refDate.month &&
        occurrence.day == refDate.day;
    return _result(
      month: month,
      day: day,
      year: year,
      nextDate: occurrence,
      refDate: refDate,
      adjusted: adjusted,
      isToday: sameDay,
      daysUntil: sameDay ? 0 : occurrence.difference(refDate).inDays,
    );
  }

  NextBirthday _computeInTimezone({
    required int month,
    required int day,
    required String timezoneName,
    required LeapDayResolution leapDay,
    required DateTime reference,
  }) {
    final loc = tz.getLocation(timezoneName);
    final refLocal = tz.TZDateTime.from(reference, loc);
    final refDate = tz.TZDateTime(
      loc,
      refLocal.year,
      refLocal.month,
      refLocal.day,
    );
    var year = refLocal.year;
    final (occurrence, adjusted) = _occurrenceInYearTz(
      loc,
      year,
      month: month,
      day: day,
      leapDay: leapDay,
    );
    if (occurrence.isBefore(refDate)) {
      year += 1;
      final (next, adjustedNext) = _occurrenceInYearTz(
        loc,
        year,
        month: month,
        day: day,
        leapDay: leapDay,
      );
      return _result(
        month: month,
        day: day,
        year: year,
        nextDate: next,
        refDate: refDate,
        adjusted: adjustedNext,
      );
    }
    final sameDay =
        occurrence.year == refDate.year &&
        occurrence.month == refDate.month &&
        occurrence.day == refDate.day;
    return _result(
      month: month,
      day: day,
      year: year,
      nextDate: occurrence,
      refDate: refDate,
      adjusted: adjusted,
      isToday: sameDay,
      daysUntil: sameDay ? 0 : occurrence.difference(refDate).inDays,
    );
  }

  NextBirthday _result({
    required int month,
    required int day,
    required int year,
    required DateTime nextDate,
    required DateTime refDate,
    required bool adjusted,
    bool isToday = false,
    int? daysUntil,
  }) {
    return NextBirthday(
      month: month,
      day: day,
      year: year,
      nextDate: nextDate,
      daysUntil: daysUntil ?? nextDate.difference(refDate).inDays,
      isToday: isToday,
      isLeapDayAdjusted: adjusted,
    );
  }

  (DateTime, bool) _occurrenceInYear(
    int year, {
    required int month,
    required int day,
    required LeapDayResolution leapDay,
  }) {
    if (month == feb29Month && day == feb29Day && !isLeapYear(year)) {
      final resolved = resolveFeb29(year, resolution: leapDay);
      return (DateTime(year, resolved.month, resolved.day), true);
    }
    return (DateTime(year, month, day), false);
  }

  (DateTime, bool) _occurrenceInYearTz(
    tz.Location loc,
    int year, {
    required int month,
    required int day,
    required LeapDayResolution leapDay,
  }) {
    if (month == feb29Month && day == feb29Day && !isLeapYear(year)) {
      final resolved = resolveFeb29(year, resolution: leapDay);
      return (tz.TZDateTime(loc, year, resolved.month, resolved.day), true);
    }
    return (tz.TZDateTime(loc, year, month, day), false);
  }

  /// True when [timezoneName] resolves to a known IANA zone.
  bool isKnownTimezone(String timezoneName) {
    try {
      tz.getLocation(timezoneName);
      return true;
    } on tz.LocationNotFoundException {
      return false;
    }
  }

  /// Applies recipient > user > fallback timezone precedence.
  String effectiveTimezone(String? recipientTimezone, String? userTimezone) {
    for (final candidate in [recipientTimezone, userTimezone]) {
      if (candidate != null) {
        final trimmed = candidate.trim();
        if (trimmed.isNotEmpty && isKnownTimezone(trimmed)) {
          return trimmed;
        }
      }
    }
    return fallbackTimezone;
  }
}
