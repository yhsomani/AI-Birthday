import 'package:timezone/timezone.dart' as tz;

/// Quiet hours: reminders inside this daily window are not delivered (SSOT §17).
///
/// The window is expressed as minutes from local midnight and may wrap midnight
/// (e.g. 22:00 → 08:00). An empty window ([QuietHours.none]) disables the rule.
class QuietHours {
  const QuietHours({required this.start, required this.end});

  const QuietHours.none() : start = Duration.zero, end = Duration.zero;

  const QuietHours.night()
    : start = const Duration(hours: 22),
      end = const Duration(hours: 8);

  /// Minutes-from-midnight window bounds, inclusive start / exclusive end.
  final Duration start;
  final Duration end;

  bool get isEmpty => start == end;

  bool contains(DateTime at) {
    if (isEmpty) return false;
    const minutesPerDay = 24 * 60;
    final minute = at.hour * 60 + at.minute;
    final s = start.inMinutes % minutesPerDay;
    final e = end.inMinutes % minutesPerDay;
    if (s == e) return false;
    return s < e ? minute >= s && minute < e : minute >= s || minute < e;
  }

  /// Calculates the earliest [DateTime] outside quiet hours at or after [at].
  ///
  /// Preserves timezone location frame when [at] is a [tz.TZDateTime].
  DateTime nextPermissible(DateTime at) {
    if (isEmpty || !contains(at)) return at;
    const minutesPerDay = 24 * 60;
    final s = start.inMinutes % minutesPerDay;
    final e = end.inMinutes % minutesPerDay;
    if (s == e) return at;

    final minute = at.hour * 60 + at.minute;
    final eHour = e ~/ 60;
    final eMinute = e % 60;

    if (s < e) {
      // Same-day quiet window (e.g. 08:00 to 10:00).
      return _atTime(at, at.year, at.month, at.day, eHour, eMinute);
    } else {
      // Overnight quiet window (e.g. 22:00 to 08:00).
      if (minute >= s) {
        final nextDay = at.add(const Duration(days: 1));
        return _atTime(
          at,
          nextDay.year,
          nextDay.month,
          nextDay.day,
          eHour,
          eMinute,
        );
      } else {
        return _atTime(at, at.year, at.month, at.day, eHour, eMinute);
      }
    }
  }

  static DateTime _atTime(DateTime ref, int y, int m, int d, int h, int min) {
    if (ref is tz.TZDateTime) {
      return tz.TZDateTime(ref.location, y, m, d, h, min);
    }
    return DateTime(y, m, d, h, min);
  }

  QuietHours copyWith({Duration? start, Duration? end}) {
    return QuietHours(start: start ?? this.start, end: end ?? this.end);
  }

  @override
  bool operator ==(Object other) =>
      other is QuietHours && other.start == start && other.end == end;

  @override
  int get hashCode => Object.hash(start, end);
}
