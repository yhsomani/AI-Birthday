import Foundation

/// Locale-aware presentation strings that must be produced natively because
/// they are derived from protected contact/date material. Only the formatted
/// label crosses React Native; raw provider fields remain native-owned.
enum IOSNativePresentationFormatter {
  static func birthdayLabel(
    year: Int?,
    month: Int,
    day: Int,
    locale: Locale = .autoupdatingCurrent
  ) -> String? {
    var calendar = Calendar(identifier: .gregorian)
    calendar.locale = locale
    calendar.timeZone = TimeZone(secondsFromGMT: 0)!
    guard let date = calendar.date(from: DateComponents(
      calendar: calendar,
      timeZone: calendar.timeZone,
      year: year ?? 2_000,
      month: month,
      day: day
    )) else { return nil }
    let formatter = DateFormatter()
    formatter.calendar = calendar
    formatter.locale = locale
    formatter.timeZone = calendar.timeZone
    formatter.setLocalizedDateFormatFromTemplate(year == nil ? "dMMMM" : "dMMMMy")
    return formatter.string(from: date)
  }

  static func selectedBirthdayLabel(
    year: Int?,
    month: Int,
    day: Int,
    locale: Locale = .autoupdatingCurrent
  ) -> String {
    birthdayLabel(
      year: year,
      month: month,
      day: day,
      locale: locale
    ) ?? localized(
      english: "Birthday selected",
      hindi: "जन्मदिन चुना गया",
      locale: locale
    )
  }

  static func nextOccurrenceLabel(
    _ date: Date,
    calendar: Calendar,
    locale: Locale = .autoupdatingCurrent
  ) -> String {
    let formatter = DateFormatter()
    formatter.calendar = calendar
    formatter.locale = locale
    formatter.timeZone = calendar.timeZone
    formatter.dateStyle = .long
    formatter.timeStyle = .none
    let prefix = localized(english: "Next", hindi: "अगला", locale: locale)
    return "\(prefix): \(formatter.string(from: date))"
  }

  static func reminderWindowLabel(
    locale: Locale = .autoupdatingCurrent
  ) -> String {
    localized(
      english: "Reminder window",
      hindi: "रिमाइंडर समय",
      locale: locale
    )
  }

  static func windowLabel(
    primaryStart: String,
    primaryEnd: String,
    graceEnd: String?,
    locale: Locale = .autoupdatingCurrent
  ) -> String {
    var label = "\(primaryStart)–\(primaryEnd)"
    if let graceEnd {
      let grace = localized(
        english: "grace until",
        hindi: "अतिरिक्त समय",
        locale: locale
      )
      label += isHindi(locale)
        ? " · \(grace) \(graceEnd) तक"
        : " · \(grace) \(graceEnd)"
    }
    return label
  }

  private static func localized(
    english: String,
    hindi: String,
    locale: Locale
  ) -> String {
    isHindi(locale) ? hindi : english
  }

  private static func isHindi(_ locale: Locale) -> Bool {
    locale.languageCode?.lowercased() == "hi"
      || locale.identifier.lowercased().hasPrefix("hi_")
  }
}
