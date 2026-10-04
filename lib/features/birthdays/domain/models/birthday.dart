/// Birthday tracking and lifecycle model (SSOT §8, §14).
library;

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
    return BirthdayStatus.values.firstWhere(
      (e) => e.name.toLowerCase() == value.toLowerCase(),
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

  /// The exact resolved date for this cycle.
  final DateTime date;

  /// Current lifecycle status.
  final BirthdayStatus status;

  /// Active message draft ID (if drafted).
  final String? draftId;

  final DateTime createdAt;
  final DateTime updatedAt;

  /// Days remaining until the birthday from [referenceDate].
  /// Returns 0 if today, positive if in future, negative if passed.
  int daysUntil(DateTime referenceDate) {
    final ref = DateTime(
      referenceDate.year,
      referenceDate.month,
      referenceDate.day,
    );
    final normalized = referenceDate.isUtc ? date.toUtc() : date.toLocal();
    final target = DateTime(normalized.year, normalized.month, normalized.day);
    return target.difference(ref).inDays;
  }

  /// Whether the birthday is today relative to [referenceDate].
  bool isToday(DateTime referenceDate) => daysUntil(referenceDate) == 0;

  /// Resolves the next birthday date according to SSOT §14 (Leap Day rules).
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
    if (month == 2 && day == 29) {
      final isLeapYear =
          (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
      if (!isLeapYear) {
        return preferMar1 ? DateTime(year, 3, 1) : DateTime(year, 2, 28);
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
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    return Birthday(
      id: id ?? this.id,
      personId: personId ?? this.personId,
      cycleYear: cycleYear ?? this.cycleYear,
      date: date ?? this.date,
      status: status ?? this.status,
      draftId: draftId ?? this.draftId,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'personId': personId,
    'cycleYear': cycleYear,
    'date': date.toIso8601String(),
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
      date: DateTime.parse(json['date'] as String),
      status: BirthdayStatus.fromString(json['status'] as String?),
      draftId: json['draftId'] as String?,
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
    );
  }
}
