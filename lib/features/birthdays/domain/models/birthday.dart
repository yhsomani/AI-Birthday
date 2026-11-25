/// Birthday tracking and lifecycle model (legacy SSOT §8, §14 (PRODUCT.md §16.7)).
library;

import '../birthday_engine.dart';

/// Lifecycle states of a tracked birthday event.
enum BirthdayStatus {
  created,
  upcoming,
  reminderDue,
  messageNotPrepared,
  messageDrafted,
  messageReviewed,
  readyForDelivery,
  handedOff,
  completed,
  failed;

  String get displayName => switch (this) {
    BirthdayStatus.created => 'Created',
    BirthdayStatus.upcoming => 'Upcoming',
    BirthdayStatus.reminderDue => 'Reminder Due',
    BirthdayStatus.messageNotPrepared => 'Message Needed',
    BirthdayStatus.messageDrafted => 'Draft Prepared',
    BirthdayStatus.messageReviewed => 'Reviewed',
    BirthdayStatus.readyForDelivery => 'Ready to Send',
    BirthdayStatus.handedOff => 'Handed Off',
    BirthdayStatus.completed => 'Completed',
    BirthdayStatus.failed => 'Needs Action',
  };

  bool get isActionNeeded => switch (this) {
    BirthdayStatus.reminderDue ||
    BirthdayStatus.messageNotPrepared ||
    BirthdayStatus.messageDrafted ||
    BirthdayStatus.failed => true,
    _ => false,
  };

  static BirthdayStatus fromString(String? value) {
    if (value == null) return BirthdayStatus.upcoming;
    // ⚡ Bolt: Hoist invariant .toLowerCase() out of loop to avoid O(N) redundant string allocations
    final target = value.toLowerCase();
    return BirthdayStatus.values.firstWhere(
      (e) => e.name.toLowerCase() == target,
      orElse: () => BirthdayStatus.upcoming,
    );
  }
}

/// A specific birthday cycle for a person.
class Birthday {
  const Birthday({
    required this.id,
    required this.personId,
    required this.cycleYear,
    required this.date,
    this.status = BirthdayStatus.upcoming,
    this.draftId,
    required this.createdAt,
    required this.updatedAt,
  });

  final String id;
  final String personId;

  /// The calendar year of this celebration cycle (e.g. 2026).
  final int cycleYear;

  /// The resolved calendar date for this cycle.
  ///
  /// Only the year/month/day fields are meaningful; the time-of-day, zone and
  /// instant are ignored everywhere (persistence, comparison, [daysUntil]).
  final DateTime date;

  /// Whether [other] falls on the same calendar day as [date].
  bool isSameCalendarDate(DateTime other) =>
      date.year == other.year &&
      date.month == other.month &&
      date.day == other.day;

  /// Current lifecycle status.
  final BirthdayStatus status;

  /// Active message draft ID (if drafted).
  final String? draftId;

  final DateTime createdAt;
  final DateTime updatedAt;

  /// Orders birthdays by calendar date (never by instant).
  static int compareByDate(Birthday a, Birthday b) {
    final byYear = a.date.year.compareTo(b.date.year);
    if (byYear != 0) return byYear;
    final byMonth = a.date.month.compareTo(b.date.month);
    return byMonth != 0 ? byMonth : a.date.day.compareTo(b.date.day);
  }

  /// Days remaining until the birthday from [referenceDate].
  /// Returns 0 if today, positive if in future, negative if passed.
  int daysUntil(DateTime referenceDate) {
    final ref = referenceDate.isUtc ? referenceDate : referenceDate.toLocal();
    final targetUtc = DateTime.utc(date.year, date.month, date.day);
    final refUtc = DateTime.utc(ref.year, ref.month, ref.day);
    return targetUtc.difference(refUtc).inDays;
  }

  /// Whether the birthday is today relative to [referenceDate].
  bool isToday(DateTime referenceDate) => daysUntil(referenceDate) == 0;

  /// Resolves the next birthday date according to legacy SSOT §14 (PRODUCT.md §16.7) (Leap Day rules).
  ///
  /// For Feb 29:
  /// - On leap years, returns Feb 29.
  /// - On non-leap years, defaults to Feb 28 (or Mar 1 if [preferMar1] is true).
  static DateTime calculateOccurrenceDate({
    required int year,
    required int month,
    required int day,
    bool preferMar1 = false,
  }) {
    if (month == BirthdayEngine.feb29Month && day == BirthdayEngine.feb29Day) {
      if (!BirthdayEngine.isLeapYear(year)) {
        final resolved = const BirthdayEngine().resolveFeb29(
          year,
          resolution: preferMar1
              ? LeapDayResolution.mar1
              : LeapDayResolution.feb28,
        );
        return DateTime(year, resolved.month, resolved.day);
      }
    }
    return DateTime(year, month, day);
  }

  /// Calculates the next upcoming birthday date starting from [from].
  static DateTime nextBirthdayDate({
    required int month,
    required int day,
    required DateTime from,
    bool preferMar1 = false,
  }) {
    final today = DateTime(from.year, from.month, from.day);
    final thisYearTarget = calculateOccurrenceDate(
      year: from.year,
      month: month,
      day: day,
      preferMar1: preferMar1,
    );

    if (thisYearTarget.isAfter(today) ||
        thisYearTarget.isAtSameMomentAs(today)) {
      return thisYearTarget;
    } else {
      return calculateOccurrenceDate(
        year: from.year + 1,
        month: month,
        day: day,
        preferMar1: preferMar1,
      );
    }
  }

  Birthday copyWith({
    String? id,
    String? personId,
    int? cycleYear,
    DateTime? date,
    BirthdayStatus? status,
    String? draftId,
    bool clearDraftId = false,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    return Birthday(
      id: id ?? this.id,
      personId: personId ?? this.personId,
      cycleYear: cycleYear ?? this.cycleYear,
      date: date ?? this.date,
      status: status ?? this.status,
      draftId: clearDraftId ? null : (draftId ?? this.draftId),
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'personId': personId,
    'cycleYear': cycleYear,
    'date': _calendarDateString(date),
    'status': status.name,
    if (draftId != null) 'draftId': draftId,
    'createdAt': createdAt.toIso8601String(),
    'updatedAt': updatedAt.toIso8601String(),
  };

  factory Birthday.fromJson(Map<String, dynamic> json) {
    return Birthday(
      id: json['id'] as String,
      personId: json['personId'] as String,
      cycleYear: json['cycleYear'] as int,
      date: _parseCalendarDate(json['date'] as String),
      status: BirthdayStatus.fromString(json['status'] as String?),
      draftId: json['draftId'] as String?,
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
    );
  }

  static String _calendarDateString(DateTime value) {
    final y = value.year.toString().padLeft(4, '0');
    final m = value.month.toString().padLeft(2, '0');
    final d = value.day.toString().padLeft(2, '0');
    return '$y-$m-$d';
  }

  /// Accepts both `yyyy-MM-dd` and legacy ISO-8601 timestamps; the written
  /// calendar day is kept as-is instead of converting the instant to UTC.
  static DateTime _parseCalendarDate(String raw) {
    final parsed = DateTime.parse(raw.length > 10 ? raw.substring(0, 10) : raw);
    return DateTime(parsed.year, parsed.month, parsed.day);
  }
}
