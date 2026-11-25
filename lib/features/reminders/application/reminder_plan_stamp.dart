/// Record of the last reminder plan applied to the device (audit A1-002).
///
/// Reminders are one-shot alarms for the next occurrence only, so the plan
/// must be rebuilt as time passes. The stamp lets start-up / resume decide
/// whether that is needed without re-planning on every foreground event.
class ReminderPlanStamp {
  const ReminderPlanStamp({
    required this.plannedAt,
    required this.timezoneKey,
    required this.dateKey,
    required this.enabled,
    required this.ok,
  });

  /// Wall-clock instant of the last attempt.
  final DateTime plannedAt;

  /// Device time-zone name and UTC offset at planning time.
  final String timezoneKey;

  /// Local calendar date (yyyy-MM-dd) at planning time.
  final String dateKey;

  /// Whether alarms may exist on the device (reminders enabled at the time, or
  /// a previous enabled plan could not be confirmed cancelled).
  final bool enabled;

  /// False when the last attempt could not be confirmed; forces a retry.
  final bool ok;

  static String timezoneKeyFor(DateTime now) =>
      '${now.timeZoneName}@${now.timeZoneOffset.inMinutes}';

  static String dateKeyFor(DateTime now) {
    final local = now.toLocal();
    final month = local.month.toString().padLeft(2, '0');
    final day = local.day.toString().padLeft(2, '0');
    return '${local.year.toString().padLeft(4, '0')}-$month-$day';
  }

  factory ReminderPlanStamp.at(
    DateTime now, {
    required bool enabled,
    required bool ok,
  }) => ReminderPlanStamp(
    plannedAt: now,
    timezoneKey: timezoneKeyFor(now),
    dateKey: dateKeyFor(now),
    enabled: enabled,
    ok: ok,
  );

  /// Whether the plan must be rebuilt at [now]: never planned, last attempt
  /// failed, device time zone changed, the calendar day rolled over, or the
  /// clock moved backwards.
  bool isStaleAt(DateTime now) =>
      !ok ||
      timezoneKey != timezoneKeyFor(now) ||
      dateKey != dateKeyFor(now) ||
      plannedAt.isAfter(now);

  String encode() =>
      'v1|${ok ? 1 : 0}|${enabled ? 1 : 0}|$timezoneKey|$dateKey|'
      '${plannedAt.millisecondsSinceEpoch}';

  static ReminderPlanStamp? decode(String? raw) {
    if (raw == null) return null;
    final parts = raw.split('|');
    if (parts.length != 6 || parts.first != 'v1') return null;
    final millis = int.tryParse(parts[5]);
    if (millis == null) return null;
    return ReminderPlanStamp(
      ok: parts[1] == '1',
      enabled: parts[2] == '1',
      timezoneKey: parts[3],
      dateKey: parts[4],
      plannedAt: DateTime.fromMillisecondsSinceEpoch(millis),
    );
  }
}

/// True when [stamp] is missing or stale at [now].
bool reminderPlanNeedsRefresh(ReminderPlanStamp? stamp, DateTime now) =>
    stamp == null || stamp.isStaleAt(now);
