import Foundation

/// Pure time policy for app-owned iOS companion detail. Wall-clock rollback
/// never accelerates deletion; trusted server time remains mandatory before a
/// duplicate-prevention terminal marker can be released.
enum IOSCompanionRetentionPolicy {
  static let detailedRetention: TimeInterval = 30 * 24 * 60 * 60
  static let trustedTimeFreshness: TimeInterval = 5 * 60
  private static let maximumPositiveTimeZoneOffset: TimeInterval = 14 * 60 * 60

  static func detailHasExpired(recordedAt: Date, now: Date) -> Bool {
    guard recordedAt.timeIntervalSince1970.isFinite,
      now.timeIntervalSince1970.isFinite
    else { return false }
    return now.timeIntervalSince(recordedAt) >= detailedRetention
  }

  static func mayReleaseTerminalMarker(
    recordedAt: Date,
    now: Date,
    trustedServerTime: Date?,
    releaseAfter: Date?
  ) -> Bool {
    guard detailHasExpired(recordedAt: recordedAt, now: now),
      let trustedServerTime,
      let releaseAfter,
      trustedServerTime.timeIntervalSince1970.isFinite,
      releaseAfter.timeIntervalSince1970.isFinite,
      abs(now.timeIntervalSince(trustedServerTime)) <= trustedTimeFreshness
    else { return false }
    return trustedServerTime > releaseAfter
  }

  /// Attention notification claims contain only a civil date. Expiring from
  /// the earliest instant that date can begin (UTC+14) guarantees the claim is
  /// never retained for more than 30 elapsed days, regardless of the timezone
  /// in which it was created. Invalid or future dates fail closed.
  static func attentionClaimHasExpired(civilDate: String, now: Date) -> Bool {
    guard now.timeIntervalSince1970.isFinite else { return false }
    let parts = civilDate.split(separator: "-", omittingEmptySubsequences: false)
    guard parts.count == 3, parts[0].count == 4, parts[1].count == 2,
      parts[2].count == 2, let year = Int(parts[0]), let month = Int(parts[1]),
      let day = Int(parts[2]), (1...9_999).contains(year)
    else { return false }

    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(secondsFromGMT: 0)!
    let components = DateComponents(
      calendar: calendar,
      timeZone: calendar.timeZone,
      year: year,
      month: month,
      day: day
    )
    guard let startUTC = calendar.date(from: components) else { return false }
    let roundTrip = calendar.dateComponents([.year, .month, .day], from: startUTC)
    guard roundTrip.year == year, roundTrip.month == month, roundTrip.day == day else {
      return false
    }

    let earliestPossibleClaim = startUTC.addingTimeInterval(
      -maximumPositiveTimeZoneOffset
    )
    return now.timeIntervalSince(earliestPossibleClaim) >= detailedRetention
  }

  /// Civil dates are fixed-width Gregorian values, so lexical ordering is
  /// chronological. Refusing equal or earlier dates prevents a timezone or
  /// manual-clock rollback from producing a second notification for a civil
  /// day that was already claimed.
  static func mayAdvanceAttentionClaim(
    previousCivilDate: String?,
    to civilDate: String
  ) -> Bool {
    previousCivilDate.map { civilDate > $0 } ?? true
  }
}
