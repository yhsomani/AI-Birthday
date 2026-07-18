import CoreFoundation
import CryptoKit
import Foundation

enum IOSCompanionWorkflowEngineResult {
  case success(Any)
  case failure([String: Any])
}

private enum IOSCompanionPlanRebuildOutcome {
  case succeeded
  case failed([String: Any])

  var isSuccessful: Bool {
    if case .succeeded = self { return true }
    return false
  }
}

private struct IOSCompanionPlannedOccurrenceDescriptor {
  let occurrenceId: String
  let contactId: String
  let civilDate: String
}

private struct IOSCompanionLazyMaterialContext {
  let workflow: CompanionWorkflowState
  let index: IOSCompanionPlanningIndex
  let draft: CompanionWorkflowMessageDraft
  let materializationNow: Date
  let peopleSnapshotGeneration: String
  let privateContactsById: [String: IOSPeoplePrivateContact]
  let blockedDestinations: Set<String>
  let destinationCounts: [String: Int]
}

enum IOSBirthdayMessageContentCategory: String, CaseIterable {
  case birthdayIntentRequired = "birthday-intent-required"
  case url
  case trackingOrAffiliate = "tracking-or-affiliate"
  case promotion
  case literalPersonalData = "literal-personal-data"
  case age
  case gender
  case religion
  case health
  case relationship
  case privateMemory = "private-memory"
  case hate
  case sexual
  case selfHarm = "self-harm"
  case violence
  case deception
}

enum IOSBirthdayMessageContentPolicy {
  static let policyVersion = "birthday-message-semantic-v2"
  static let validatorVersion = "sms-template-validator-v2"

  static func issueCodes(text: String, declaredLanguage: String) -> [String] {
    let normalized = text.precomposedStringWithCanonicalMapping
    var issues: [String] = []
    let categories = classify(text: normalized, declaredLanguage: declaredLanguage)
    if categories.contains(.url) {
      issues.append("template-url-not-allowed")
    }
    if categories.contains(.trackingOrAffiliate) {
      issues.append("template-tracking-not-allowed")
    }
    if categories.contains(.promotion) {
      issues.append("template-promotional-content")
    }
    if !categories.isDisjoint(with: sensitiveCategories) {
      issues.append("template-sensitive-content")
    }
    if categories.contains(.birthdayIntentRequired) {
      issues.append("template-birthday-intent-required")
    }
    let templateOnly = normalized.replacingOccurrences(of: "{firstName}", with: "")
    let letters = String(
      templateOnly.unicodeScalars.filter { CharacterSet.letters.contains($0) }
    )
    let languagePattern = declaredLanguage == "hi"
      ? "^\\p{Devanagari}+$" : "^\\p{Latin}+$"
    if letters.isEmpty
      || letters.range(of: languagePattern, options: .regularExpression) == nil
    {
      issues.append("template-language-mismatch")
    }
    if normalized.unicodeScalars.contains(where: isBidiControl) {
      issues.append("template-bidi-control")
    }
    if normalized.unicodeScalars.contains(where: {
      isUnsafeMessageScalar($0) && !isBidiControl($0)
    }) {
      issues.append("template-control-character")
    }
    var seen = Set<String>()
    return issues.filter { seen.insert($0).inserted }
  }

  static func classify(
    text: String,
    declaredLanguage: String?
  ) -> Set<IOSBirthdayMessageContentCategory> {
    let value = semanticView(text)
    var categories = Set<IOSBirthdayMessageContentCategory>()
    let hasBirthdayIntent: Bool
    switch declaredLanguage {
    case "en": hasBirthdayIntent = matches(categoryBirthdayIntentEnglish, in: value)
    case "hi": hasBirthdayIntent = matches(categoryBirthdayIntentHindi, in: value)
    default:
      hasBirthdayIntent = matches(categoryBirthdayIntentEnglish, in: value)
        || matches(categoryBirthdayIntentHindi, in: value)
    }
    if !hasBirthdayIntent { categories.insert(.birthdayIntentRequired) }
    if matchesAny(categoryURLPatterns, in: value)
      || containsNonBenignURLDomain(value)
    {
      categories.insert(.url)
    }
    if matches(categoryTrackingOrAffiliate, in: value) {
      categories.insert(.trackingOrAffiliate)
    }
    if matches(categoryPromotion, in: value) {
      categories.insert(.promotion)
    }
    if matchesAny(categoryLiteralPersonalDataPatterns, in: value) {
      categories.insert(.literalPersonalData)
    }
    if matches(categoryAgeEnglish, in: value) || matches(categoryAgeHindi, in: value) {
      categories.insert(.age)
    }
    if matches(categoryGender, in: value) { categories.insert(.gender) }
    if matches(categoryReligion, in: value) { categories.insert(.religion) }
    if matches(categoryHealth, in: value) { categories.insert(.health) }
    if matches(categoryRelationship, in: value) { categories.insert(.relationship) }
    if matches(categoryPrivateMemory, in: value) { categories.insert(.privateMemory) }
    if matches(categoryHate, in: value) { categories.insert(.hate) }
    if matches(categorySexual, in: value) { categories.insert(.sexual) }
    if matches(categorySelfHarm, in: value) { categories.insert(.selfHarm) }
    if matches(categoryViolence, in: value) { categories.insert(.violence) }
    if matches(categoryDeception, in: value) { categories.insert(.deception) }
    return categories
  }

  /// Validates the exact body that the app is about to prefill. The language is
  /// deliberately inferred only for this final native boundary; authored
  /// drafts still have to match their explicitly declared language.
  static func isSafeRenderedBody(_ text: String) -> Bool {
    issueCodes(text: text, declaredLanguage: "en").isEmpty
      || issueCodes(text: text, declaredLanguage: "hi").isEmpty
  }

  /// Renders the exact prefilled body and validates it again after contact-name
  /// interpolation. A collision never causes a silent salutation replacement:
  /// the user must explicitly choose and reapprove a generic template instead.
  /// The returned body is the body shown during approval and later handed to
  /// MessageUI; edits inside MessageUI remain entirely user-controlled.
  static func renderedBody(
    templateText: String,
    placeholderMode: String,
    givenName: String?,
    declaredLanguage: String
  ) -> String? {
    guard ["en", "hi"].contains(declaredLanguage),
      issueCodes(text: templateText, declaredLanguage: declaredLanguage).isEmpty
    else { return nil }

    guard let personalized = IOSCompanionMessagePlaceholderPolicy.render(
      text: templateText,
      placeholderMode: placeholderMode,
      givenName: givenName
    ) else { return nil }
    return issueCodes(text: personalized, declaredLanguage: declaredLanguage).isEmpty
      ? personalized : nil
  }

  private static func matches(_ pattern: String, in value: String) -> Bool {
    value.range(of: pattern, options: [.regularExpression, .caseInsensitive]) != nil
  }

  private static func matchesAny(_ patterns: [String], in value: String) -> Bool {
    patterns.contains { matches($0, in: value) }
  }

  private static func semanticView(_ text: String) -> String {
    text.precomposedStringWithCompatibilityMapping
      .split(whereSeparator: { $0.isWhitespace })
      .joined(separator: " ")
  }

  private static func containsNonBenignURLDomain(_ text: String) -> Bool {
    guard let expression = try? NSRegularExpression(
      pattern: categoryURLDomain,
      options: [.caseInsensitive]
    ) else { return true }
    let range = NSRange(text.startIndex..<text.endIndex, in: text)
    return expression.matches(in: text, range: range).contains { match in
      guard let swiftRange = Range(match.range, in: text) else { return true }
      return !benignDottedTerms.contains(String(text[swiftRange]).lowercased())
    }
  }

  private static func isBidiControl(_ scalar: Unicode.Scalar) -> Bool {
    scalar.value == 0x061C || scalar.value == 0x200E || scalar.value == 0x200F
      || (0x202A...0x202E).contains(scalar.value)
      || (0x2066...0x2069).contains(scalar.value)
  }

  private static func isUnsafeMessageScalar(_ scalar: Unicode.Scalar) -> Bool {
    switch scalar.properties.generalCategory {
    case .control, .lineSeparator, .paragraphSeparator:
      return true
    default:
      return isBidiControl(scalar) || scalar.value == 0x200B
        || scalar.value == 0x2060 || scalar.value == 0xFEFF
    }
  }

  private static let sensitiveCategories: Set<IOSBirthdayMessageContentCategory> = [
    .literalPersonalData, .age, .gender, .religion, .health, .relationship,
    .privateMemory, .hate, .sexual, .selfHarm, .violence, .deception,
  ]

  private static let categoryBirthdayIntentEnglish =
    "\\b(?:birthday|b[\\s-]?day|bday)\\b|\\bmany\\s+happy\\s+returns\\b"
  private static let categoryBirthdayIntentHindi = "(?:जन्म\\s*दिन|जन्मदिवस)"
  private static let categoryURLDomain =
    "\\b(?:[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]{0,62}[\\p{L}\\p{N}])?\\.)+" +
    "(?:[a-z]{2,63}|xn--[a-z0-9-]{2,59})(?:[/?:#]\\S*)?"
  private static let categoryURLPatterns = [
    "(?:\\b(?:https?|ftp)\\s*:\\s*/\\s*/|\\b(?:mailto|tel|sms|smsto)\\s*:|\\bwww\\.)\\S+",
    "\\b[\\p{L}\\p{N}][\\p{L}\\p{N}-]{0,62}\\s*" +
      "(?:\\[\\s*dot\\s*\\]|\\(\\s*dot\\s*\\)|\\s+dot\\s+)\\s*" +
      "(?:[a-z]{2,63}|xn--[a-z0-9-]{2,59})\\b",
    "\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b",
    "\\b[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9-]+(?:\\.[a-z0-9-]+)+\\b",
  ]
  private static let categoryTrackingOrAffiliate =
    "(?:\\butm_[a-z0-9_]+\\s*=|\\b(?:gclid|fbclid|msclkid|ref|referrer|" +
    "affiliate_id|aff_id)\\s*=|#[\\p{L}\\p{N}_]+|" +
    "\\b(?:affiliate|referral|sponsored)\\s+(?:link|code|post)|" +
    "\\buse\\s+(?:my|code)\\s+(?:affiliate\\s+)?code\\b|" +
    "\\bearns?\\s+(?:a\\s+)?commission\\b|" +
    "(?:रेफरल|एफिलिएट|संबद्ध)\\s*(?:लिंक|कोड)|(?:प्रायोजित|कमीशन))"
  private static let categoryPromotion =
    "\\b(?:limited(?:[- ]time)? offer|special offer|special deal|flash sale|" +
    "birthday sale|discount(?: code)?|coupon(?: code)?|promo(?: code)?|buy now|" +
    "shop now|order now|free offer|free gift|claim (?:your )?(?:offer|gift|discount)|" +
    "save [0-9]{1,3}%|[0-9]{1,3}% off|subscribe(?: now| today)?|" +
    "start (?:a|your) subscription)\\b|" +
    "(?:सीमित|खास|विशेष)\\s*(?:समय का\\s*)?ऑफर|अभी\\s*(?:खरीदें|ऑर्डर करें)|" +
    "(?:विशेष\\s*)?छूट|कूपन|प्रोमो\\s*कोड|मुफ़्त\\s*(?:ऑफर|उपहार)|" +
    "फ्लैश\\s*सेल|सदस्यता\\s*लें"
  private static let benignDottedTerms: Set<String> = ["node.js", "dr.strange"]

  private static let categoryEmail =
    "\\b[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9-]+(?:\\.[a-z0-9-]+)+\\b"
  private static let categoryLiteralPersonalDataPatterns = [
    "(?<![\\p{L}\\p{N}])(?:\\+?[0-9०-९][\\s().-]*){10,15}(?![\\p{L}\\p{N}])",
    "\\b(?:[0-9]{4}[-/.][0-9]{1,2}[-/.][0-9]{1,2}|" +
      "[0-9]{1,2}[-/.][0-9]{1,2}[-/.][0-9]{2,4})\\b",
    "\\b(?:(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|" +
      "aug(?:ust)?|sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+" +
      "[0-9]{1,2}(?:st|nd|rd|th)?(?:,?\\s+[0-9]{4})?|" +
      "[0-9]{1,2}(?:st|nd|rd|th)?\\s+(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|" +
      "apr(?:il)?|may|june?|july?|aug(?:ust)?|sept?(?:ember)?|oct(?:ober)?|" +
      "nov(?:ember)?|dec(?:ember)?)(?:\\s+[0-9]{4})?)\\b",
    "[0-9०-९]{1,2}\\s*(?:जनवरी|फरवरी|मार्च|अप्रैल|मई|जून|जुलाई|अगस्त|" +
      "सितंबर|अक्टूबर|नवंबर|दिसंबर)(?:\\s*[0-9०-९]{2,4})?",
    "\\b(?:your|my)\\s+(?:full name|phone number|mobile number|email address|" +
      "home address|aadhaar(?: number)?|passport(?: number)?|social security number|" +
      "ssn|date of birth|birth date)\\b|(?:आपका|आपकी|मेरा|मेरी)\\s*" +
      "(?:पूरा नाम|फोन नंबर|मोबाइल नंबर|ईमेल|घर का पता|आधार नंबर|पासपोर्ट नंबर|जन्म तिथि)",
    categoryEmail,
  ]
  private static let categoryAgeEnglish =
    "\\b(?:turning\\s+[0-9]{1,3}\\b(?!\\s+(?:pages?|chapters?|books?|degrees?|" +
    "minutes?|seconds?|ideas?|recipes?))|[0-9]{1,3}(?:st|nd|rd|th)\\s+birthday|" +
    "[0-9]{1,3}\\s+years?\\s+old|(?:age|aged)\\s+[0-9]{1,3}|" +
    "[0-9]{1,3}\\s+candles?)\\b"
  private static let categoryAgeHindi =
    "[0-9०-९]{1,3}\\s*(?:वां|वाँ|वीं)?\\s*जन्मदिन|" +
    "[0-9०-९]{1,3}\\s*साल\\s*के\\s*हो\\s*गए|उम्र\\s*[0-9०-९]{1,3}|" +
    "[0-9०-९]{1,3}\\s*मोमबत्त"
  private static let categoryGender =
    "\\b(?:birthday\\s+(?:girl|boy|woman|man)|you\\s+are\\s+(?:a\\s+)?" +
    "(?:woman|man|girl|boy|female|male)|as\\s+(?:a|the)\\s+" +
    "(?:woman|man|girl|boy))\\b|(?:आप|तुम)\\s*(?:एक\\s*)?(?:शानदार\\s+)?" +
    "(?:महिला|पुरुष|लड़की|लड़का)\\s*(?:हैं|हो)|" +
    "जन्मदिन\\s+(?:की\\s+लड़की|का\\s+लड़का)"
  private static let categoryReligion =
    "\\b(?:god|jesus|allah|christ|lord)\\s+(?:bless|protect|guide)s?\\s+you\\b|" +
    "\\b(?:as\\s+(?:a|your)\\s+|you\\s+are\\s+(?:a\\s+)?)" +
    "(?:hindu|muslim|christian|jewish|sikh|buddhist)\\b|" +
    "(?:भगवान|ईश्वर|अल्लाह|यीशु|वाहेगुरु)\\s*(?:आपको|तुम्हें)?\\s*आशीर्वाद|" +
    "(?:आप|तुम)\\s*(?:हिंदू|मुसलमान|ईसाई|सिख|बौद्ध)\\s*(?:हैं|हो)"
  private static let categoryHealth =
    "\\b(?:your\\s+(?:illness|diagnosis|disease|disability|medical condition|" +
    "cancer|diabetes)|recover(?:y|ing)?\\s+from\\s+(?:your\\s+)?" +
    "(?:illness|diagnosis|surgery|cancer|disease)|get well soon|" +
    "beat(?:ing)?\\s+(?:cancer|your illness|the disease))\\b|" +
    "(?:आपकी|तुम्हारी)\\s*(?:बीमारी|निदान|विकलांगता|चिकित्सा स्थिति|कैंसर|मधुमेह)|" +
    "(?:बीमारी|ऑपरेशन|कैंसर)\\s*से\\s*जल्द\\s*ठीक"
  private static let categoryRelationship =
    "\\b(?:(?:my|your)\\s+(?:wife|husband|girlfriend|boyfriend|partner|daughter|" +
    "son|mother|father|sister|brother|best friend)|as\\s+your\\s+" +
    "(?:wife|husband|girlfriend|boyfriend|partner)|our\\s+" +
    "(?:marriage|relationship|friendship))\\b|(?:मेरी|आपकी|तुम्हारी)\\s*" +
    "(?:पत्नी|पति|प्रेमिका|प्रेमी|बेटी|बेटा|माँ|पिता|बहन|भाई)|" +
    "हमारा\\s*(?:विवाह|रिश्ता)"
  private static let categoryPrivateMemory =
    "\\b(?:remember\\s+(?:when(?!\\s+to\\b)|our|the time)|" +
    "our\\s+secret\\b(?!\\s+recipe)|inside\\s+joke|the\\s+trip\\s+we\\s+took|" +
    "that\\s+night\\s+we)\\b|(?:याद\\s+है\\s+जब|हमारा\\s+राज़|" +
    "हमारी\\s+गुप्त\\s+(?:यात्रा|बात)|हम\\s+जब\\s+साथ)"
  private static let categoryHate =
    "\\b(?:hate|despise)\\s+(?:all\\s+)?(?:women|men|muslims?|hindus?|" +
    "christians?|jews?|sikhs?|gays?|lesbians?|transgender\\s+people|" +
    "disabled\\s+people|people\\s+of\\s+(?:a\\s+)?(?:race|caste|religion))\\b|" +
    "\\b(?:inferior|disgusting)\\s+(?:race|caste|religion)\\b|" +
    "(?:महिलाओं|पुरुषों|मुसलमानों|हिंदुओं|ईसाइयों|सिखों|समलैंगिकों|विकलांगों)" +
    "\\s*से\\s*नफरत|(?:जाति|धर्म)\\s*(?:नीच|घटिया)"
  private static let categorySexual =
    "\\b(?:sex(?:ual)?|sexy|nude|naked|porn(?:ography)?|sleep\\s+with\\s+me|" +
    "explicit\\s+photos?)\\b|(?:यौन|सेक्सी|नग्न|अश्लील|पोर्न)"
  private static let categorySelfHarm =
    "\\b(?:kill\\s+yourself|end\\s+your\\s+(?:life|pain)|commit\\s+suicide|" +
    "suicide|self[- ]?harm|hurt\\s+yourself)\\b|" +
    "(?:आत्महत्या|खुद\\s+को\\s+मार|अपनी\\s+जान\\s+ले|खुद\\s+को\\s+नुकसान)"
  private static let categoryViolence =
    "\\b(?:kill|murder|hurt|attack|shoot|stab|beat)\\s+" +
    "(?:you|him|her|them|someone|people)\\b|\\b(?:death|bomb)\\s+threat\\b|" +
    "(?:आपको|तुम्हें|उसे|उन्हें)\\s*(?:मार\\s*(?:दूँगा|दूंगा|डालूँगा|डालूंगा)|" +
    "गोली\\s+मार|चाकू\\s+मार|पीट)|(?:जान\\s+से\\s+मारने|बम)\\s+की\\s+धमकी"
  private static let categoryDeception =
    "\\b(?:you(?:'ve| have)\\s+won\\s+(?:a\\s+)?(?:prize|lottery)|" +
    "share\\s+your\\s+(?:otp|pin|password)|send\\s+(?:money|payment|your\\s+otp)|" +
    "your\\s+(?:bank\\s+)?account\\s+is\\s+(?:locked|suspended)|" +
    "i\\s+am\\s+from\\s+your\\s+bank|guaranteed\\s+(?:prize|returns?)|" +
    "urgent\\s+payment)\\b|(?:आपका|तुम्हारा)\\s*बैंक\\s*खाता\\s*(?:बंद|निलंबित)|" +
    "(?:otp|पिन|पासवर्ड)\\s*(?:भेजें|बताएं|साझा करें)|" +
    "आप\\s*(?:इनाम|लॉटरी)\\s*जीत"

}

private struct IOSCompanionEffectiveContact {
  let safe: IOSPeopleSafeContact
  let privateValue: IOSPeoplePrivateContact
  let configuration: CompanionWorkflowContact?
  let selectedPhoneId: String?
  let selectedBirthdayId: String?
  let selectedPhone: IOSPeoplePrivatePhone?
  let selectedDestinationBlocked: Bool
  let selectedBirthday: IOSPeoplePrivateBirthday?
  let readinessKind: String
  let readinessReasons: [String]
  let approvalKind: String
  let approvalReasons: [String]
}

enum IOSActivityRecoveryPolicy {
  static func route(
    kind: String,
    reason: String?,
    currentIssueCodes: Set<String>,
    automationRepairAvailable: Bool,
    approvalRepairAvailable: Bool,
    composerRetryMatches: Bool
  ) -> String? {
    if (kind == "composer-cancelled" || kind == "composer-failed"),
      composerRetryMatches, currentIssueCodes.isEmpty
    {
      return "automation"
    }
    if kind == "paused", automationRepairAvailable { return "automation" }
    if kind == "approval-invalidated", approvalRepairAvailable { return "people" }
    if let reason, currentIssueCodes.contains(reason) { return "attention" }
    return nil
  }
}

/// Pure civil-date planner shared by iOS projections, simulation, and reminder materialization.
/// Google birthday month/day values are Gregorian even when the user selects another display
/// calendar. Scheduling still follows the device's autoupdating timezone.
enum IOSCompanionRecurrencePlanner {
  static let planningDays = 400

  static func occurrenceDates(
    birthday: IOSPeoplePrivateBirthday?,
    leapPolicy: String?,
    from now: Date,
    schedulingCalendar: Calendar
  ) -> [Date] {
    guard let birthday,
      leapPolicy.map({ ["feb-28", "mar-01", "skip"].contains($0) }) ?? true
    else { return [] }
    let calendar = gregorianCalendar(in: schedulingCalendar.timeZone)
    let start = calendar.startOfDay(for: now)
    guard
      let horizon = calendar.date(
        byAdding: .day, value: planningDays - 1, to: start
      )
    else { return [] }
    let currentYear = calendar.component(.year, from: start)
    var values: [Date] = []
    for year in currentYear...(currentYear + 2) {
      guard
        let value = occurrenceDate(
          in: year,
          birthday: birthday,
          leapPolicy: leapPolicy,
          schedulingCalendar: calendar
        ),
        value >= start,
        value <= horizon
      else { continue }
      values.append(value)
    }
    return values.sorted()
  }

  static func occurrenceDate(
    in year: Int,
    birthday: IOSPeoplePrivateBirthday,
    leapPolicy: String?,
    schedulingCalendar: Calendar
  ) -> Date? {
    guard leapPolicy.map({ ["feb-28", "mar-01", "skip"].contains($0) }) ?? true else {
      return nil
    }
    let calendar = gregorianCalendar(in: schedulingCalendar.timeZone)
    var month = birthday.month
    var day = birthday.day
    if month == 2 && day == 29 {
      guard let leapPolicy, ["feb-28", "mar-01", "skip"].contains(leapPolicy) else {
        return nil
      }
      if !isGregorianLeapYear(year) {
        switch leapPolicy {
        case "feb-28": day = 28
        case "mar-01":
          month = 3
          day = 1
        case "skip": return nil
        default: return nil
        }
      }
    }
    return exactCivilDate(year: year, month: month, day: day, calendar: calendar)
  }

  private static func gregorianCalendar(in timeZone: TimeZone) -> Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = timeZone
    return calendar
  }

  private static func exactCivilDate(
    year: Int,
    month: Int,
    day: Int,
    calendar: Calendar
  ) -> Date? {
    guard (1...12).contains(month), (1...31).contains(day),
      let value = calendar.date(
        from: DateComponents(
          calendar: calendar,
          timeZone: calendar.timeZone,
          year: year,
          month: month,
          day: day
        )
      )
    else { return nil }
    let resolved = calendar.dateComponents([.year, .month, .day], from: value)
    guard resolved.year == year, resolved.month == month, resolved.day == day else {
      return nil
    }
    return value
  }

  private static func isGregorianLeapYear(_ year: Int) -> Bool {
    year.isMultiple(of: 4)
      && (!year.isMultiple(of: 100) || year.isMultiple(of: 400))
  }
}

@MainActor
final class IOSCompanionWorkflowEngine {
  static let shared = IOSCompanionWorkflowEngine()

  private static let maximumReviewCount = 32
  private static let reviewLifetime: TimeInterval = 5 * 60
  private static let planningDays = IOSCompanionRecurrencePlanner.planningDays
  private static let maximumBatch = 50
  private static let deletionLocalWipeRecoveryReason =
    "deletion-local-wipe-recovery"
  private static let privacyActions: Set<String> = [
    "disconnect-contacts", "revoke-google-access", "sign-out-retain",
    "sign-out-wipe", "delete-account", "wipe-local-data",
    "clear-gemini-templates", "clear-activity",
  ]
  private static let activityKinds: Set<String> = [
    "reminder-scheduled", "composer-opened", "composer-cancelled",
    "composer-failed", "composer-outcome-unknown", "composer-reported-sent",
    "approval-invalidated", "coordination-blocked", "paused",
    "settings-changed", "sync",
  ]
  private static let isoFormatter: ISO8601DateFormatter = {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    return formatter
  }()

  private let store = CompanionProtectedStore.shared
  private let peopleStore = CompanionPeopleStore.shared
  private let reminderCoordinator = CompanionReminderCoordinator.shared
  private let deletionClient = IOSAccountDeletionClient.shared
  private let contactResetClient = IOSContactDerivedResetClient.shared
  private let deletionReceiptStore = IOSAccountDeletionReceiptStore.shared
  private let deletionRecoveryStore = IOSAccountDeletionRecoveryStore.shared
  private let deletionCleanup = IOSAccountDeletionLocalCleanupCoordinator.shared
  private var calendar: Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = .autoupdatingCurrent
    return calendar
  }

  private init() {}

  func contactsProjection(
    request: [String: Any],
    status: CompanionProjectionStatus
  ) -> IOSCompanionWorkflowEngineResult {
    let contacts = effectiveContacts(status: status)
    switch request["kind"] as? String {
    case "list":
      guard Set(request.keys) == ["kind", "query"],
        let query = request["query"] as? [String: Any],
        Self.validPeopleQuery(query),
        let filter = query["filter"] as? String,
        let pageSize = Self.strictInteger(query["pageSize"], range: 1...50),
        let offset = Self.pageOffset(query["cursor"] as? String)
      else { return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID")) }
      let search =
        (query["search"] as? String)?.trimmingCharacters(
          in: .whitespacesAndNewlines
        ).lowercased() ?? ""
      let filtered = contacts.filter { contact in
        let enrollment = contact.configuration?.enrollment ?? .off
        let matchesFilter: Bool
        switch filter {
        case "all": matchesFilter = enrollment != .excluded
        case "enabled": matchesFilter = enrollment == .enabled
        case "ready":
          matchesFilter =
            contact.readinessKind == "ready"
            && enrollment != .excluded
        case "needs-attention":
          matchesFilter =
            contact.readinessKind == "needs-attention"
            && enrollment != .excluded
        case "excluded": matchesFilter = enrollment == .excluded
        default: matchesFilter = false
        }
        return matchesFilter
          && (search.isEmpty
            || contact.safe.displayName.lowercased().contains(search))
      }
      guard offset <= filtered.count else {
        return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
      }
      let end = min(filtered.count, offset + pageSize)
      var page: [String: Any] = [
        "items": filtered[offset..<end].map(Self.contactSummary),
        "totalCount": filtered.count,
      ]
      if end < filtered.count { page["nextCursor"] = "page.\(end)" }
      return .success(page)
    case "detail":
      guard Set(request.keys) == ["contactId", "kind"],
        let contactId = request["contactId"] as? String,
        Self.validOpaque(contactId),
        let contact = contacts.first(where: { $0.safe.localId == contactId })
      else { return .failure(Self.temporarilyUnavailable("contacts-stale")) }
      return .success(contactDetail(contact))
    default:
      return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
    }
  }

  func messagesProjection(
    request: [String: Any],
    status: CompanionProjectionStatus
  ) -> IOSCompanionWorkflowEngineResult {
    guard let workflow = status.workflow else {
      return .failure(Self.temporarilyUnavailable("account-reconnect-required"))
    }
    switch request["kind"] as? String {
    case "editor":
      guard request.keys.count == 1 else {
        return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
      }
      guard let draft = workflow.messageDraft else {
        return .success(["kind": "not-configured"])
      }
      return .success(["kind": "configured", "draft": Self.messageDraftPayload(draft)])
    case "next-composer-proposal":
      guard request.keys.count == 1 else {
        return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
      }
      guard workflow.desired == .remindersOn else {
        return .success(["kind": "none"])
      }
      guard let binding = IOSGoogleIdentityCoordinator.shared.exactSessionBinding(),
        let material = nextLazyProposalMaterial(
          status: status,
          binding: binding,
          now: Date()
        ), let contact = peopleStore.projection().contacts.first(where: {
          $0.localId == material.contactId
        })
      else { return .success(["kind": "none"]) }
      return .success([
        "kind": "ready",
        "proposalId": material.proposalId,
        "occurrenceId": material.occurrenceId,
        "occurrenceDate": material.occurrenceCivilDate,
        "recipient": contact.displayName,
      ])
    default:
      return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
    }
  }

  func approvalProjection(
    contactId: String,
    status: CompanionProjectionStatus
  ) -> IOSCompanionWorkflowEngineResult {
    guard Self.validOpaque(contactId),
      let contact = effectiveContacts(status: status).first(where: {
        $0.safe.localId == contactId
      })
    else { return .failure(Self.temporarilyUnavailable("contacts-stale")) }
    return .success(Self.approvalPayload(contact))
  }

  func policyEditorProjection(
    status: CompanionProjectionStatus
  ) -> IOSCompanionWorkflowEngineResult {
    guard let workflow = status.workflow else {
      return .failure(Self.temporarilyUnavailable("account-reconnect-required"))
    }
    guard let policy = workflow.policy else {
      return .success(["kind": "not-configured"])
    }
    let latePolicy: [String: Any] =
      policy.graceEnd.map {
        ["kind": "same-day-grace", "graceEnd": $0]
      } ?? ["kind": "none"]
    return .success([
      "kind": "configured",
      "draft": [
        "primaryStart": policy.primaryStart,
        "primaryEnd": policy.primaryEnd,
        "latePolicy": latePolicy,
        // Wire compatibility only. The React Native editor shares this shape
        // with Android, but iOS never applies this value as a send limit.
        "dailyCap": policy.legacyAndroidDailyCap,
      ],
    ])
  }

  func birthdayJobProjection(
    occurrenceId: String,
    status: CompanionProjectionStatus
  ) -> IOSCompanionWorkflowEngineResult {
    let record = status.composerRecords.last(where: { $0.occurrenceId == occurrenceId })
    let planned = plannedOccurrenceDescriptor(
      occurrenceId: occurrenceId,
      status: status
    )
    guard Self.validOpaque(occurrenceId), record != nil || planned != nil else {
      return .failure(Self.temporarilyUnavailable("contacts-stale"))
    }
    let civilDate = record?.occurrenceCivilDate ?? planned!.civilDate
    let phase: String
    if let record {
      switch record.outcome {
      case .openCommitted, .presented, .reportedSent: phase = "composer-opened"
      case .cancelled, .failed: phase = "dismissed"
      case .outcomeUnknown: phase = "expired"
      }
    } else if civilDate == Self.localDate(Date(), calendar: calendar) {
      phase = "composer-ready"
    } else {
      phase = "reminder-planned"
    }
    return .success([
      "platform": "ios",
      "occurrenceId": occurrenceId,
      "occurrenceDate": civilDate,
      "phase": phase,
      "updatedAt": Self.dateString(record?.resolvedAt ?? record?.openedAt ?? Date()),
    ])
  }

  func activityProjection(
    request: [String: Any],
    status: CompanionProjectionStatus,
    currentIssueCodes: Set<String> = []
  ) -> IOSCompanionWorkflowEngineResult {
    guard request["kind"] as? String == "list",
      Set(request.keys) == ["kind", "query"],
      let query = request["query"] as? [String: Any],
      Self.validActivityQuery(query),
      let pageSize = Self.strictInteger(query["pageSize"], range: 1...50),
      let offset = Self.pageOffset(query["cursor"] as? String)
    else { return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID")) }

    let automationRepairAvailable = status.workflow.map {
      $0.desired == .paused && $0.messageDraft != nil && $0.policy != nil
    } ?? false
    let approvalRepairAvailable = effectiveContacts(status: status).contains {
      ($0.configuration?.enrollment == .enabled
        || $0.configuration?.enrollment == .paused)
        && $0.approvalKind != "valid"
    }
    let retryableProposalId = IOSGoogleIdentityCoordinator.shared.exactSessionBinding()
      .flatMap {
        nextLazyProposalMaterial(status: status, binding: $0, now: Date())?.proposalId
      }

    var records =
      status.workflow?.activity.filter {
        Self.activityKinds.contains($0.kind)
      }.map { activity in
        Self.activityPayload(
          id: activity.id,
          kind: activity.kind,
          reason: activity.reason,
          occurredAt: activity.occurredAt,
          recoveryRoute: IOSActivityRecoveryPolicy.route(
            kind: activity.kind,
            reason: activity.reason,
            currentIssueCodes: currentIssueCodes,
            automationRepairAvailable: automationRepairAvailable,
            approvalRepairAvailable: approvalRepairAvailable,
            composerRetryMatches: false
          )
        )
      } ?? []
    let activityCutoff = status.workflow?.activityClearedAt
    records.append(
      contentsOf: status.composerRecords.flatMap { record -> [[String: Any]] in
        var values: [[String: Any]] = []
        if activityCutoff.map({ record.openedAt > $0 }) ?? true {
          values.append(
            Self.activityPayload(
              id: "composer.\(record.operationId).opened",
              kind: "composer-opened",
              reason: nil,
              occurredAt: record.openedAt,
              recoveryRoute: nil
            ))
        }
        let terminalKind: String?
        switch record.outcome {
        case .openCommitted, .presented: terminalKind = nil
        case .cancelled: terminalKind = "composer-cancelled"
        case .failed: terminalKind = "composer-failed"
        case .outcomeUnknown: terminalKind = "composer-outcome-unknown"
        case .reportedSent: terminalKind = "composer-reported-sent"
        }
        let terminalAt = record.resolvedAt ?? record.openedAt
        if let terminalKind,
          activityCutoff.map({ terminalAt > $0 }) ?? true
        {
          values.append(
            Self.activityPayload(
              id: "composer.\(record.operationId).outcome",
              kind: terminalKind,
              reason: nil,
              occurredAt: terminalAt,
              recoveryRoute: IOSActivityRecoveryPolicy.route(
                kind: terminalKind,
                reason: nil,
                currentIssueCodes: currentIssueCodes,
                automationRepairAvailable: automationRepairAvailable,
                approvalRepairAvailable: approvalRepairAvailable,
                composerRetryMatches: record.proposalId == retryableProposalId
              )
            ))
        }
        return values
      })
    records.sort {
      (($0["occurredAt"] as? String) ?? "") > (($1["occurredAt"] as? String) ?? "")
    }
    guard offset <= records.count else {
      return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
    }
    let end = min(records.count, offset + pageSize)
    var payload: [String: Any] = ["items": Array(records[offset..<end])]
    if end < records.count { payload["nextCursor"] = "page.\(end)" }
    return .success(payload)
  }

  func privacyInventory(status: CompanionProjectionStatus) -> [String: Any] {
    let people = peopleStore.projection()
    let effective = effectiveContacts(status: status)
    let workflow = status.workflow
    let activityCutoff = workflow?.activityClearedAt
    let visibleComposerActivityCount = status.composerRecords.reduce(0) {
      count, record in
      let openedVisible = activityCutoff.map { record.openedAt > $0 } ?? true
      let terminalAt = record.resolvedAt ?? record.openedAt
      let terminalVisible = record.outcome != .openCommitted && record.outcome != .presented
        && (activityCutoff.map { terminalAt > $0 } ?? true)
      return count + (openedVisible ? 1 : 0) + (terminalVisible ? 1 : 0)
    }
    var result: [String: Any] = [
      "localContactCount": people.contacts.count,
      "enabledRecipientCount": effective.filter {
        $0.configuration?.enrollment == .enabled
      }.count,
      "approvalCount": effective.filter { $0.approvalKind == "valid" }.count,
      "activityCount": (workflow?.activity.count ?? 0) + visibleComposerActivityCount,
      "templateCount": workflow?.messageDraft == nil ? 0 : 1,
      "localStorageBytes": people.localStorageBytes + status.localStorageBytes,
      "consentVersions": IOSCompanionConsentLedgerPolicy.versions(
        workflow?.consentReceipts
      ),
      "externalSmsCopiesNotControlled": true,
    ]
    if case .fresh(let completedAt, _) = people.sync {
      result["lastContactsSyncAt"] = Self.dateString(completedAt)
    }
    return result
  }

  func homeMetrics(status: CompanionProjectionStatus) -> [String: Any] {
    let contacts = effectiveContacts(status: status)
    let enabled = contacts.filter { $0.configuration?.enrollment == .enabled }
    let now = Date()
    let today = Self.localDate(now, calendar: calendar)
    // Inclusive range: today plus the following six civil dates is seven days.
    var nextSevenCount = 0
    for offset in 0...6 {
      guard let date = calendar.date(byAdding: .day, value: offset, to: now) else {
        continue
      }
      nextSevenCount += plannedOccurrenceCount(
        civilDate: Self.localDate(date, calendar: calendar),
        status: status
      )
    }
    var result: [String: Any] = [
      "enabled": enabled.count,
      "needsAttention": contacts.filter { $0.readinessKind == "needs-attention" }.count,
      "unavailable": contacts.filter { $0.readinessKind == "unavailable" }.count,
      "today": plannedOccurrenceCount(civilDate: today, status: status),
      "nextSevenDays": nextSevenCount,
    ]
    if let binding = IOSGoogleIdentityCoordinator.shared.exactSessionBinding(),
      let next = firstLazyProposalMaterial(
        status: status,
        binding: binding,
        startingCivilDate: today,
        now: now
      ), let contact = contacts.first(where: { $0.safe.localId == next.contactId }),
      let masked = contact.safe.phoneChoices.first(where: {
        $0.localId == contact.selectedPhoneId
      })?.maskedDisplay
    {
      var nextPayload: [String: Any] = [
        "occurrenceId": next.occurrenceId,
        "recipient": contact.safe.displayName,
        "localDate": next.occurrenceCivilDate,
        "windowLabel": status.workflow?.policy.map(Self.windowLabel)
          ?? IOSNativePresentationFormatter.reminderWindowLabel(),
        "maskedPhone": masked,
      ]
      nextPayload["exactText"] = next.body
      result["next"] = nextPayload
    }
    return result
  }

  func automationPayload(
    status: CompanionProjectionStatus,
    readiness: [String: Any]
  ) -> [String: Any] {
    guard let workflow = status.workflow else {
      return [
        "platform": "ios", "desired": "paused", "effective": "not-configured",
        "readiness": readiness,
      ]
    }
    let effective: String
    if workflow.messageDraft == nil || workflow.policy == nil {
      effective = "not-configured"
    } else if workflow.desired == .paused {
      effective = "paused"
    } else if let composer = readiness["composer"] as? [String: Any],
      composer["kind"] as? String == "allowed",
      status.reminderHorizonState == .full,
      effectiveContacts(status: status).contains(where: {
        $0.configuration?.enrollment == .enabled && $0.approvalKind == "valid"
      })
    {
      effective = "ready"
    } else {
      effective = "action-required"
    }
    return [
      "platform": "ios",
      "desired": workflow.desired.rawValue,
      "effective": effective,
      "readiness": readiness,
    ]
  }

  private func effectiveContacts(
    status: CompanionProjectionStatus
  ) -> [IOSCompanionEffectiveContact] {
    let blockedDestinations = Set(
      IOSCompanionDestinationBlocklistPolicy.normalized(
        status.workflow?.blockedDestinations
      ) ?? []
    )
    let privateById = Dictionary(
      uniqueKeysWithValues: peopleStore.privateContacts().map { ($0.localId, $0) }
    )
    let configById = Dictionary(
      uniqueKeysWithValues: (status.workflow?.contacts ?? []).map { ($0.contactId, $0) }
    )
    var values = peopleStore.projection().contacts.compactMap { safe in
      guard let privateValue = privateById[safe.localId] else { return nil }
      let configuration = configById[safe.localId]
      let selectablePhones = privateValue.phones.filter { $0.e164 != nil }
      let selectedPhoneId =
        configuration?.selectedPhoneId.flatMap { selected in
          selectablePhones.contains(where: { $0.localId == selected }) ? selected : nil
        } ?? (selectablePhones.count == 1 ? selectablePhones[0].localId : nil)
      let selectedBirthdayId =
        configuration?.selectedBirthdayId.flatMap { selected in
          privateValue.birthdays.contains(where: { $0.localId == selected }) ? selected : nil
        } ?? (privateValue.birthdays.count == 1 ? privateValue.birthdays[0].localId : nil)
      let selectedPhone = selectablePhones.first { $0.localId == selectedPhoneId }
      let selectedDestinationBlocked = selectedPhone?.e164.map {
        blockedDestinations.contains($0)
      } ?? false
      let selectedBirthday = privateValue.birthdays.first { $0.localId == selectedBirthdayId }
      var reasons = Set(safe.readinessReasons)
      // A missing safe given name is material only for a personalized draft.
      // Enrollment remains possible before a draft is chosen and generic
      // templates never require a name substitution.
      if let draft = status.workflow?.messageDraft,
        draft.placeholderMode == "given-name"
      {
        if IOSBirthdayMessageContentPolicy.renderedBody(
          templateText: draft.text,
          placeholderMode: draft.placeholderMode,
          givenName: privateValue.givenName,
          declaredLanguage: draft.language
        ) == nil {
          reasons.insert("safe-given-name-missing")
        }
      } else {
        reasons.remove("safe-given-name-missing")
      }
      if selectedPhone != nil {
        reasons.remove("phone-choice-required")
        reasons.remove("phone-ambiguous-region")
        reasons.remove("phone-invalid")
      }
      if selectedDestinationBlocked { reasons.insert("phone-blocked-form") }
      if selectedBirthday != nil {
        reasons.remove("birthday-choice-required")
        reasons.remove("birthday-conflict")
      }
      if selectedBirthday?.month == 2, selectedBirthday?.day == 29,
        configuration?.leapPolicy == nil
      {
        reasons.insert("leap-policy-required")
      }
      let materialMatches = configuration?.materialRevision == privateValue.materialRevision
      let approvalMatches = configuration.map { config in
        Self.approvalMatches(
          config.approvalHash,
          contact: privateValue,
          configuration: config,
          message: status.workflow?.messageDraft,
          policy: status.workflow?.policy
        )
      } ?? false
      let approvalKind: String
      var approvalReasons = configuration?.approvalInvalidationReasons ?? []
      if configuration?.approvalHash == nil || configuration?.approvedAt == nil {
        approvalKind = "missing"
      } else if !materialMatches || !approvalMatches {
        approvalKind = "invalidated"
        if approvalReasons.isEmpty { approvalReasons = ["name-changed"] }
      } else if !approvalReasons.isEmpty {
        approvalKind = "invalidated"
      } else {
        approvalKind = "valid"
      }
      if let enrollment = configuration?.enrollment,
        enrollment == .enabled || enrollment == .paused,
        approvalKind != "valid"
      {
        reasons.insert("approval-invalid")
      }
      let kind =
        privateValue.deleted
        ? "unavailable"
        : (reasons.isEmpty ? "ready" : "needs-attention")
      return IOSCompanionEffectiveContact(
        safe: safe,
        privateValue: privateValue,
        configuration: configuration,
        selectedPhoneId: selectedPhoneId,
        selectedBirthdayId: selectedBirthdayId,
        selectedPhone: selectedPhone,
        selectedDestinationBlocked: selectedDestinationBlocked,
        selectedBirthday: selectedBirthday,
        readinessKind: kind,
        readinessReasons: Array(reasons).sorted(),
        approvalKind: approvalKind,
        approvalReasons: Array(Set(approvalReasons)).sorted()
      )
    }
    var destinationCounts: [String: Int] = [:]
    for value in values where value.configuration?.enrollment != .excluded {
      if let destination = value.selectedPhone?.e164 {
        destinationCounts[destination, default: 0] += 1
      }
    }
    values = values.map { value in
      guard let destination = value.selectedPhone?.e164,
        (destinationCounts[destination] ?? 0) > 1
      else { return value }
      var reasons = Set(value.readinessReasons)
      reasons.insert("duplicate-destination")
      return IOSCompanionEffectiveContact(
        safe: value.safe,
        privateValue: value.privateValue,
        configuration: value.configuration,
        selectedPhoneId: value.selectedPhoneId,
        selectedBirthdayId: value.selectedBirthdayId,
        selectedPhone: value.selectedPhone,
        selectedDestinationBlocked: value.selectedDestinationBlocked,
        selectedBirthday: value.selectedBirthday,
        readinessKind: value.privateValue.deleted ? "unavailable" : "needs-attention",
        readinessReasons: Array(reasons).sorted(),
        approvalKind: value.approvalKind,
        approvalReasons: value.approvalReasons
      )
    }
    return values.sorted {
      $0.safe.displayName.localizedCaseInsensitiveCompare($1.safe.displayName)
        == .orderedAscending
    }
  }

  private func contactDetail(_ contact: IOSCompanionEffectiveContact) -> [String: Any] {
    var result: [String: Any] = [
      "summary": Self.contactSummary(contact),
      "selectedDestinationBlocked": contact.selectedDestinationBlocked,
      "phoneChoices": contact.safe.phoneChoices.map { choice in
        var value: [String: Any] = [
          "id": choice.localId, "maskedDisplay": choice.maskedDisplay,
          "sourceLabel": choice.sourceLabel, "selectable": choice.selectable,
        ]
        if let issue = choice.issue { value["issue"] = issue }
        return value
      },
      "birthdayChoices": contact.safe.birthdayChoices.map { choice in
        var value: [String: Any] = [
          "id": choice.localId, "displayLabel": choice.displayLabel,
          "hasYear": choice.hasYear, "selectable": choice.selectable,
        ]
        if let issue = choice.issue { value["issue"] = issue }
        return value
      },
    ]
    if let id = contact.selectedPhoneId { result["selectedPhoneId"] = id }
    if let id = contact.selectedBirthdayId { result["selectedBirthdayId"] = id }
    if let next = nextOccurrence(
      birthday: contact.selectedBirthday,
      leapPolicy: contact.configuration?.leapPolicy,
      from: Date()
    ) {
      result["nextOccurrenceLabel"] = IOSNativePresentationFormatter.nextOccurrenceLabel(
        next,
        calendar: calendar
      )
    }
    if let label = contact.configuration?.lastOutcomeLabel {
      result["lastOutcomeLabel"] = label
    }
    return result
  }

  private static func contactSummary(
    _ contact: IOSCompanionEffectiveContact
  ) -> [String: Any] {
    let readiness: [String: Any] =
      contact.readinessKind == "ready"
      ? ["kind": "ready"]
      : ["kind": contact.readinessKind, "reasons": contact.readinessReasons]
    var enrollment: [String: Any]
    switch contact.configuration?.enrollment ?? .off {
    case .off:
      enrollment = ["kind": "off"]
    case .excluded:
      enrollment = ["kind": "excluded", "reason": "policy-suspended"]
    case .enabled:
      enrollment = ["kind": "enabled", "approval": approvalPayload(contact)]
    case .paused:
      enrollment = [
        "kind": "paused", "reason": "policy-suspended",
        "approval": approvalPayload(contact),
      ]
    }
    var result: [String: Any] = [
      "id": contact.safe.localId,
      "displayName": contact.safe.displayName,
      "readiness": readiness,
      "enrollment": enrollment,
    ]
    if let selected = contact.safe.phoneChoices.first(where: {
      $0.localId == contact.selectedPhoneId
    }) {
      result["maskedPhone"] = selected.maskedDisplay
    }
    if let birthday = contact.selectedBirthday {
      result["birthdayLabel"] = IOSNativePresentationFormatter.selectedBirthdayLabel(
        year: birthday.year,
        month: birthday.month,
        day: birthday.day
      )
    }
    return result
  }

  private static func approvalPayload(
    _ contact: IOSCompanionEffectiveContact
  ) -> [String: Any] {
    switch contact.approvalKind {
    case "valid":
      return [
        "kind": "valid",
        "approvedAt": dateString(contact.configuration?.approvedAt ?? Date()),
      ]
    case "invalidated":
      let allowed = Set([
        "phone-changed", "birthday-changed", "name-changed", "template-changed",
        "placeholder-semantics-changed", "window-changed", "late-policy-changed",
        "segment-plan-changed", "disclosure-changed",
      ])
      let reasons = contact.approvalReasons.filter { allowed.contains($0) }
      return ["kind": "invalidated", "reasons": reasons.isEmpty ? ["name-changed"] : reasons]
    default:
      return ["kind": "missing"]
    }
  }

  func execute(
    intent: String,
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    status: CompanionProjectionStatus,
    readiness: [String: Any],
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard status.workflow?.account.matches(binding) == true else {
      completion(.failure(Self.temporarilyUnavailable("account-reconnect-required")))
      return
    }
    switch intent {
    case "choose-phone":
      choosePhone(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        completion: completion
      )
    case "choose-birthday":
      chooseBirthday(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        completion: completion
      )
    case "prepare-enrollment-review":
      prepareEnrollment(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        status: status, completion: completion
      )
    case "confirm-enrollment":
      confirmEnrollment(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        completion: completion
      )
    case "pause-recipient", "exclude-recipient", "restore-recipient":
      mutateRecipient(
        intent: intent, payload: payload, binding: binding,
        expectedRevision: expectedRevision, completion: completion
      )
    case "block-recipient-destination", "unblock-recipient-destination":
      mutateSelectedDestinationBlock(
        intent: intent, payload: payload, binding: binding,
        expectedRevision: expectedRevision, completion: completion
      )
    case "preview-message":
      previewMessage(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        status: status, completion: completion
      )
    case "save-message":
      saveMessage(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        completion: completion
      )
    case "preview-policy":
      previewPolicy(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        status: status, completion: completion
      )
    case "save-policy":
      savePolicy(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        readiness: readiness, completion: completion
      )
    case "prepare-approvals":
      prepareApprovals(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        status: status, completion: completion
      )
    case "confirm-approvals":
      confirmApprovals(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        readiness: readiness, completion: completion
      )
    case "prepare-activation", "prepare-resume":
      prepareActivation(
        binding: binding, revision: status.revision, status: status,
        readiness: readiness,
        completion: completion
      )
    case "activate", "resume":
      confirmActivation(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        status: status, readiness: readiness, completion: completion
      )
    case "pause-all":
      pauseAll(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        readiness: readiness, completion: completion
      )
    case "prepare-privacy-action":
      preparePrivacy(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        completion: completion
      )
    case "confirm-privacy-action":
      confirmPrivacy(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        completion: completion
      )
    case "resume-lifecycle-operation":
      resumePrivacyOperation(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        status: status, completion: completion
      )
    case "generate-suggestions":
      Task { @MainActor in
        let projection = await IOSGeminiSuggestionGateway.shared.generate(request: payload)
        completion(.success(projection))
      }
    default:
      completion(.failure(Self.unsupported("platform-composer-only")))
    }
  }

  private func choosePhone(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["contactId", "expectedRevision", "phoneId"],
      let contactId = payload["contactId"] as? String,
      let phoneId = payload["phoneId"] as? String,
      let revision = Self.payloadRevision(payload, expected: expectedRevision),
      Self.validOpaque(contactId), Self.validOpaque(phoneId),
      let contact = peopleStore.privateContact(localId: contactId), !contact.deleted,
      contact.phones.contains(where: { $0.localId == phoneId && $0.e164 != nil })
    else {
      completion(.failure(Self.validation([["field": "phone", "code": "phone-invalid"]])))
      return
    }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, _ in
        var configuration = Self.contactConfiguration(contact, in: workflow)
        let changed = configuration.selectedPhoneId != phoneId
        configuration.selectedPhoneId = phoneId
        configuration.materialRevision = contact.materialRevision
        configuration.updatedAt = Date()
        if changed { Self.invalidateApproval(&configuration, reason: "phone-changed") }
        Self.upsert(configuration, in: &workflow)
        if changed { Self.bumpConfiguration(&workflow, activityKind: "settings-changed") }
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        self.finishMutation(
          result, binding: binding, rebuild: true,
          payload: { status in
            guard
              let contact = self.effectiveContacts(status: status).first(where: {
                $0.safe.localId == contactId
              })
            else { return Self.temporarilyUnavailable("contacts-stale") }
            return self.contactDetail(contact)
          },
          completion: completion
        )
      }
    )
  }

  private func chooseBirthday(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    let allowedKeys: Set<String> =
      payload["leapPolicy"] == nil
      ? ["birthdayId", "contactId", "expectedRevision"]
      : ["birthdayId", "contactId", "expectedRevision", "leapPolicy"]
    guard Set(payload.keys) == allowedKeys,
      let contactId = payload["contactId"] as? String,
      let birthdayId = payload["birthdayId"] as? String,
      let revision = Self.payloadRevision(payload, expected: expectedRevision),
      Self.validOpaque(contactId), Self.validOpaque(birthdayId),
      let contact = peopleStore.privateContact(localId: contactId), !contact.deleted,
      let birthday = contact.birthdays.first(where: { $0.localId == birthdayId })
    else {
      completion(.failure(Self.validation([["field": "birthday", "code": "birthday-missing"]])))
      return
    }
    let leapPolicy = payload["leapPolicy"] as? String
    guard leapPolicy.map({ ["feb-28", "mar-01", "skip"].contains($0) }) ?? true,
      birthday.month != 2 || birthday.day != 29 || leapPolicy != nil
    else {
      completion(
        .failure(Self.validation([["field": "birthday", "code": "leap-policy-required"]])))
      return
    }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, _ in
        var configuration = Self.contactConfiguration(contact, in: workflow)
        let changed =
          configuration.selectedBirthdayId != birthdayId
          || configuration.leapPolicy != leapPolicy
        configuration.selectedBirthdayId = birthdayId
        configuration.leapPolicy = leapPolicy
        configuration.materialRevision = contact.materialRevision
        configuration.updatedAt = Date()
        if changed { Self.invalidateApproval(&configuration, reason: "birthday-changed") }
        Self.upsert(configuration, in: &workflow)
        if changed { Self.bumpConfiguration(&workflow, activityKind: "settings-changed") }
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        self.finishMutation(
          result, binding: binding, rebuild: true,
          payload: { status in
            guard
              let contact = self.effectiveContacts(status: status).first(where: {
                $0.safe.localId == contactId
              })
            else { return Self.temporarilyUnavailable("contacts-stale") }
            return self.contactDetail(contact)
          },
          completion: completion
        )
      }
    )
  }

  private func prepareEnrollment(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    status: CompanionProjectionStatus,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["contactIds", "expectedRevision"],
      let rawIds = payload["contactIds"] as? [Any],
      let ids = Self.validContactIds(rawIds), !ids.isEmpty,
      let revision = Self.payloadRevision(payload, expected: expectedRevision)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    let byId = Dictionary(
      uniqueKeysWithValues: effectiveContacts(status: status).map {
        ($0.safe.localId, $0)
      })
    guard ids.allSatisfy({ byId[$0] != nil }) else {
      return completion(.failure(Self.temporarilyUnavailable("contacts-stale")))
    }
    let recipients = ids.compactMap { byId[$0] }
    let ready = recipients.filter { $0.readinessKind == "ready" }
    let readyIds = ready.map { $0.safe.localId }
    let blocker = Self.reviewHash(
      kind: "enrollment", contactIds: readyIds,
      workflow: status.workflow!, contacts: peopleStore.privateContacts()
    )
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, committedRevision in
        Self.installReview(
          CompanionWorkflowReview(
            handle: UUID().uuidString.lowercased(),
            kind: .enrollment,
            issuedForRevision: committedRevision,
            expiresAt: Date().addingTimeInterval(Self.reviewLifetime),
            blockerHash: blocker,
            contactIds: readyIds,
            messageDraft: nil,
            policy: nil,
            privacyAction: nil,
            occurrenceId: nil,
            consumedAt: nil
          ),
          in: &workflow
        )
        return workflow.reviews.last!.handle
      },
      completion: { result in
        switch result {
        case .failure(let error): completion(.failure(Self.storeProblem(error)))
        case .success(let handle):
          completion(
            .success([
              "handle": handle,
              "recipients": recipients.map(Self.contactSummary),
              "readyCount": ready.count,
              "attentionCount": recipients.count - ready.count,
              "explicitConfirmationRequired": true,
            ]))
        }
      }
    )
  }

  private func confirmEnrollment(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["expectedRevision", "handle"],
      let handle = payload["handle"] as? String, Self.validOpaque(handle),
      let revision = Self.payloadRevision(payload, expected: expectedRevision)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { [peopleStore] workflow, _ in
        let contacts = peopleStore.privateContacts()
        guard
          let reviewIndex = Self.reviewIndex(
            handle: handle, kind: .enrollment, revision: revision, workflow: workflow
          )
        else { throw CompanionStoreError.invalidReview }
        let review = workflow.reviews[reviewIndex]
        guard
          review.blockerHash
            == Self.reviewHash(
              kind: "enrollment", contactIds: review.contactIds,
              workflow: workflow, contacts: contacts
            )
        else { throw CompanionStoreError.staleMaterial }
        let byId = Dictionary(uniqueKeysWithValues: contacts.map { ($0.localId, $0) })
        var changed: [String] = []
        for id in review.contactIds {
          guard let contact = byId[id], !contact.deleted,
            let phoneId = Self.effectivePhoneId(contact, workflow: workflow),
            let birthdayId = Self.effectiveBirthdayId(contact, workflow: workflow),
            let phone = contact.phones.first(where: {
              $0.localId == phoneId && $0.e164 != nil
            }), phone.e164 != nil,
            let birthday = contact.birthdays.first(where: { $0.localId == birthdayId })
          else { continue }
          var configuration = Self.contactConfiguration(contact, in: workflow)
          if birthday.month == 2 && birthday.day == 29 && configuration.leapPolicy == nil {
            continue
          }
          configuration.selectedPhoneId = phoneId
          configuration.selectedBirthdayId = birthdayId
          configuration.materialRevision = contact.materialRevision
          configuration.enrollment = .enabled
          configuration.updatedAt = Date()
          Self.upsert(configuration, in: &workflow)
          changed.append(id)
        }
        guard !changed.isEmpty else { throw CompanionStoreError.staleMaterial }
        workflow.reviews[reviewIndex].consumedAt = Date()
        Self.bumpConfiguration(&workflow, activityKind: "settings-changed")
        return changed
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        self.finishMutation(
          result, binding: binding, rebuild: true,
          payload: { _, changed in
            ["changedContactIds": changed, "invalidatedApprovalCount": 0]
          },
          completion: completion
        )
      }
    )
  }

  private func mutateRecipient(
    intent: String,
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["contactId", "expectedRevision"],
      let contactId = payload["contactId"] as? String, Self.validOpaque(contactId),
      let revision = Self.payloadRevision(payload, expected: expectedRevision),
      let contact = peopleStore.privateContact(localId: contactId)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, _ in
        var configuration = Self.contactConfiguration(contact, in: workflow)
        let previous = configuration.enrollment
        switch intent {
        case "pause-recipient":
          guard previous == .enabled else { throw CompanionStoreError.invalidWorkflowState }
          configuration.enrollment = .paused
        case "exclude-recipient":
          configuration.enrollment = .excluded
          Self.invalidateApproval(&configuration, reason: "disclosure-changed")
        case "restore-recipient":
          guard previous == .paused || previous == .excluded else {
            throw CompanionStoreError.invalidWorkflowState
          }
          configuration.enrollment = configuration.approvalHash == nil ? .off : .enabled
        default:
          throw CompanionStoreError.invalidWorkflowState
        }
        configuration.updatedAt = Date()
        Self.upsert(configuration, in: &workflow)
        Self.bumpConfiguration(
          &workflow,
          activityKind: intent == "pause-recipient" ? "paused" : "settings-changed"
        )
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        self.finishMutation(
          result, binding: binding, rebuild: true,
          payload: { _ in
            [
              "changedContactIds": [contactId],
              "invalidatedApprovalCount": intent == "exclude-recipient" ? 1 : 0,
            ]
          },
          completion: completion
        )
      }
    )
  }

  private func mutateSelectedDestinationBlock(
    intent: String,
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["contactId", "expectedRevision"],
      let contactId = payload["contactId"] as? String, Self.validOpaque(contactId),
      let revision = Self.payloadRevision(payload, expected: expectedRevision),
      let contact = peopleStore.privateContact(localId: contactId),
      intent == "block-recipient-destination"
        || intent == "unblock-recipient-destination"
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    let shouldBlock = intent == "block-recipient-destination"
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { [peopleStore] workflow, _ in
        let configurationIndexById = Dictionary(
          uniqueKeysWithValues: workflow.contacts.indices.map {
            (workflow.contacts[$0].contactId, $0)
          }
        )
        let targetConfiguration = configurationIndexById[contact.localId].map {
          workflow.contacts[$0]
        }
        guard let phoneId = Self.effectivePhoneId(
          contact,
          configuration: targetConfiguration
        ),
          let destination = contact.phones.first(where: {
            $0.localId == phoneId
          })?.e164,
          let updated = IOSCompanionDestinationBlocklistPolicy.updated(
            blocked: shouldBlock,
            destination: destination,
            current: workflow.blockedDestinations
          )
        else { throw CompanionStoreError.invalidWorkflowState }
        let current = IOSCompanionDestinationBlocklistPolicy.normalized(
          workflow.blockedDestinations
        ) ?? []
        guard current != updated else { return 0 }
        workflow.blockedDestinations = updated
        var invalidated = 0
        if shouldBlock {
          let contactsById = Dictionary(
            uniqueKeysWithValues: peopleStore.privateContacts().map {
              ($0.localId, $0)
            }
          )
          guard let affectedIndices = IOSCompanionConfiguredContactScanner.matchingIndices(
            configurations: workflow.contacts,
            contactsByIdentifier: contactsById,
            identifier: { $0.contactId },
            matches: { configuration, affected in
              guard
                configuration.approvalHash != nil
                  || configuration.approvedAt != nil,
                let selectedId = Self.effectivePhoneId(
                  affected,
                  configuration: configuration
                ),
                affected.phones.first(where: { $0.localId == selectedId })?.e164
                  == destination
              else { return false }
              return true
            }
          ) else { throw CompanionStoreError.invalidWorkflowState }
          for index in affectedIndices {
            invalidated += 1
            Self.invalidateApproval(&workflow.contacts[index], reason: "phone-changed")
            workflow.contacts[index].updatedAt = Date()
          }
        }
        Self.bumpConfiguration(&workflow, activityKind: "settings-changed")
        return invalidated
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        self.finishMutation(
          result, binding: binding, rebuild: true,
          payload: { _, invalidated in
            [
              "changedContactIds": [contactId],
              "invalidatedApprovalCount": invalidated,
            ]
          },
          completion: completion
        )
      }
    )
  }

  private func previewMessage(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    status: CompanionProjectionStatus,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["draft", "expectedRevision"],
      let revision = Self.payloadRevision(payload, expected: expectedRevision),
      let rawDraft = payload["draft"] as? [String: Any]
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    let parsed = Self.parseMessageDraft(rawDraft)
    guard parsed.issues.isEmpty, let baseDraft = parsed.draft else {
      completion(
        .success([
          "kind": "invalid",
          "issues": parsed.issues,
          "affectedRecipientCount": Self.affectedContacts(status: status).count,
        ]))
      return
    }
    let draft = Self.draftWithProvenance(baseDraft)
    let affected = Self.affectedContacts(status: status)
    let rendered = affected.compactMap { contact -> (IOSCompanionEffectiveContact, String)? in
      guard let text = Self.render(draft: draft, contact: contact.privateValue) else {
        return nil
      }
      return (contact, text)
    }
    let estimates = rendered.map { Self.smsEstimate($0.1) }
    let maximumSegments = estimates.map(\.segments).max() ?? 1
    guard maximumSegments <= draft.requestedSegmentCap else {
      completion(
        .success([
          "kind": "invalid",
          "issues": [["field": "template", "code": "invalid-segment-cap"]],
          "affectedRecipientCount": affected.count,
        ]))
      return
    }
    let blocker = Self.messageReviewHash(
      draft: draft, workflow: status.workflow!, contacts: peopleStore.privateContacts()
    )
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, committedRevision in
        let review = CompanionWorkflowReview(
          handle: UUID().uuidString.lowercased(),
          kind: .message,
          issuedForRevision: committedRevision,
          expiresAt: Date().addingTimeInterval(Self.reviewLifetime),
          blockerHash: blocker,
          contactIds: affected.map { $0.safe.localId },
          messageDraft: draft,
          policy: nil,
          privacyAction: nil,
          occurrenceId: nil,
          consumedAt: nil
        )
        Self.installReview(review, in: &workflow)
        return review.handle
      },
      completion: { result in
        switch result {
        case .failure(let error): completion(.failure(Self.storeProblem(error)))
        case .success(let handle):
          let examples = Array(rendered.prefix(3)).map { contact, text -> [String: Any] in
            let estimate = Self.smsEstimate(text)
            return [
              "displayName": contact.safe.displayName,
              "finalText": text,
              "characterCount": text.unicodeScalars.count,
              "segmentCount": estimate.segments,
              "encodingLabel": estimate.encoding,
            ]
          }
          completion(
            .success([
              "kind": "valid", "handle": handle, "examples": examples,
              "maximumSegmentCount": maximumSegments,
              "affectedRecipientCount": affected.count,
            ]))
        }
      }
    )
  }

  private func saveMessage(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["expectedRevision", "handle"],
      let handle = payload["handle"] as? String, Self.validOpaque(handle),
      let revision = Self.payloadRevision(payload, expected: expectedRevision)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    // Re-read the protected review first, then revalidate process-memory
    // Gemini provenance on the main actor immediately before the CAS. A review
    // whose candidate was cleared, changed, or expired is still safe to save,
    // but it is deliberately downgraded to USER provenance.
    store.readWorkflowSnapshot { [weak self] snapshotResult in
      guard let self else {
        return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
      }
      guard case .success(let snapshot) = snapshotResult,
        snapshot.revision == revision,
        let reviewedWorkflow = snapshot.workflow,
        reviewedWorkflow.account.matches(binding),
        let reviewedIndex = Self.reviewIndex(
          handle: handle, kind: .message, revision: revision,
          workflow: reviewedWorkflow
        ), let reviewedDraft = reviewedWorkflow.reviews[reviewedIndex].messageDraft
      else { return completion(.failure(Self.storeProblem(.invalidReview))) }

      let currentContentIssues = IOSBirthdayMessageContentPolicy.issueCodes(
        text: reviewedDraft.text,
        declaredLanguage: reviewedDraft.language
      )
      guard currentContentIssues.isEmpty else {
        return completion(
          .failure(
            Self.validation(
              currentContentIssues.map { ["field": "template", "code": $0] }
            )))
      }
      let draftForCommit = Self.revalidatedDraftForSave(reviewedDraft)
      self.store.mutateWorkflow(
        expectedRevision: revision, binding: binding,
        body: { [peopleStore] workflow, _ in
          guard
            let index = Self.reviewIndex(
              handle: handle, kind: .message, revision: revision, workflow: workflow
            ), let originalDraft = workflow.reviews[index].messageDraft
          else {
            throw CompanionStoreError.invalidReview
          }
          guard
            workflow.reviews[index].blockerHash
              == Self.messageReviewHash(
                draft: originalDraft, workflow: workflow,
                contacts: peopleStore.privateContacts()
              )
          else { throw CompanionStoreError.staleMaterial }
          let invalidated = workflow.contacts.filter { $0.approvalHash != nil }.count
          workflow.messageDraft = draftForCommit
          for contactIndex in workflow.contacts.indices {
            Self.invalidateApproval(
              &workflow.contacts[contactIndex],
              reason: "template-changed"
            )
          }
          workflow.reviews[index].consumedAt = Date()
          Self.bumpConfiguration(&workflow, activityKind: "approval-invalidated")
          return (draftForCommit, invalidated)
        },
        completion: { [weak self] result in
          guard let self else {
            return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
          }
          if case .success(let value) = result,
            value.0.provenance?.source == "GEMINI"
          {
            _ = IOSGeminiSuggestionGateway.shared.consumeProvenance(
              for: Self.provenanceDraft(value.0)
            )
          }
          self.finishMutation(
            result, binding: binding, rebuild: true,
            payload: { status, value in
              [
                "draft": Self.messageDraftPayload(value.0),
                "affectedRecipientCount": Self.affectedContacts(status: status).count,
                "invalidatedApprovalCount": value.1,
              ]
            },
            completion: completion
          )
        }
      )
    }
  }

  private func previewPolicy(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    status: CompanionProjectionStatus,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["draft", "expectedRevision"],
      let revision = Self.payloadRevision(payload, expected: expectedRevision),
      let raw = payload["draft"] as? [String: Any]
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    guard let policy = Self.parsePolicy(raw) else {
      return completion(
        .success([
          "kind": "invalid",
          "issues": [["field": "window", "code": "invalid-window"]],
        ]))
    }
    let simulation = simulate(policy: policy, status: status)
    let blocker = Self.policyReviewHash(
      policy: policy, workflow: status.workflow!, contacts: peopleStore.privateContacts()
    )
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, committedRevision in
        let review = CompanionWorkflowReview(
          handle: UUID().uuidString.lowercased(),
          kind: .policy,
          issuedForRevision: committedRevision,
          expiresAt: Date().addingTimeInterval(Self.reviewLifetime),
          blockerHash: blocker,
          contactIds: [],
          messageDraft: nil,
          policy: policy,
          privacyAction: nil,
          occurrenceId: nil,
          consumedAt: nil
        )
        Self.installReview(review, in: &workflow)
        return review.handle
      },
      completion: { result in
        switch result {
        case .failure(let error): completion(.failure(Self.storeProblem(error)))
        case .success(let handle):
          completion(
            .success([
              "kind": "valid", "handle": handle,
              "summary": Self.windowLabel(policy),
              "simulatedDays": Self.planningDays,
              "maximumPlannedInLocalDay": simulation.maximumDaily,
              "maximumPlannedInRolling24Hours": simulation.maximumRolling,
            ]))
        }
      }
    )
  }

  private func savePolicy(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    readiness: [String: Any],
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["expectedRevision", "handle"],
      let handle = payload["handle"] as? String,
      let revision = Self.payloadRevision(payload, expected: expectedRevision)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { [peopleStore] workflow, _ in
        guard
          let index = Self.reviewIndex(
            handle: handle, kind: .policy, revision: revision, workflow: workflow
          ), let policy = workflow.reviews[index].policy
        else {
          throw CompanionStoreError.invalidReview
        }
        guard
          workflow.reviews[index].blockerHash
            == Self.policyReviewHash(
              policy: policy, workflow: workflow, contacts: peopleStore.privateContacts()
            )
        else { throw CompanionStoreError.staleMaterial }
        workflow.policy = policy
        for contactIndex in workflow.contacts.indices {
          Self.invalidateApproval(
            &workflow.contacts[contactIndex],
            reason: "window-changed"
          )
        }
        workflow.reviews[index].consumedAt = Date()
        Self.bumpConfiguration(&workflow, activityKind: "approval-invalidated")
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        self.finishMutation(
          result, binding: binding, rebuild: true,
          payload: { status in
            self.automationPayload(status: status, readiness: readiness)
          }, completion: completion
        )
      }
    )
  }

  private func prepareApprovals(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    status: CompanionProjectionStatus,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["contactIds", "expectedRevision"],
      let rawIds = payload["contactIds"] as? [Any],
      let ids = Self.validContactIds(rawIds),
      let revision = Self.payloadRevision(payload, expected: expectedRevision),
      let draft = status.workflow?.messageDraft,
      status.workflow?.policy != nil
    else { return completion(.failure(Self.actionRequired(["message-and-policy-required"]))) }
    let byId = Dictionary(
      uniqueKeysWithValues: effectiveContacts(status: status).map {
        ($0.safe.localId, $0)
      })
    let selected = ids.compactMap { byId[$0] }
    guard selected.count == ids.count else {
      return completion(.failure(Self.temporarilyUnavailable("contacts-stale")))
    }
    let ready = selected.filter { contact in
      (contact.configuration?.enrollment == .enabled
        || contact.configuration?.enrollment == .paused)
        && contact.selectedPhone != nil && contact.selectedBirthday != nil
        && contact.readinessReasons.allSatisfy { $0 == "approval-invalid" }
    }
    let blocker = Self.reviewHash(
      kind: "approval", contactIds: ready.map { $0.safe.localId },
      workflow: status.workflow!, contacts: peopleStore.privateContacts()
    )
    let items = ready.compactMap { contact -> [String: Any]? in
      guard let text = Self.render(draft: draft, contact: contact.privateValue),
        let masked = contact.safe.phoneChoices.first(where: {
          $0.localId == contact.selectedPhoneId
        })?.maskedDisplay,
        let birthday = contact.selectedBirthday
      else { return nil }
      return [
        "platform": "ios", "contactId": contact.safe.localId,
        "recipient": contact.safe.displayName, "maskedPhone": masked,
        "birthdayLabel": IOSNativePresentationFormatter.selectedBirthdayLabel(
          year: birthday.year,
          month: birthday.month,
          day: birthday.day
        ),
        "exactText": text,
        "deliveryMode": "user-controlled-composer",
        "consentDisclosure":
          "You decide whether to tap Send after reviewing the recipient and text. Messages and iOS control the available sender line and final transport; this app cannot select or guarantee either.",
      ]
    }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, committedRevision in
        let review = CompanionWorkflowReview(
          handle: UUID().uuidString.lowercased(), kind: .approval,
          issuedForRevision: committedRevision,
          expiresAt: Date().addingTimeInterval(Self.reviewLifetime),
          blockerHash: blocker, contactIds: ready.map { $0.safe.localId },
          messageDraft: nil, policy: nil, privacyAction: nil,
          occurrenceId: nil, consumedAt: nil
        )
        Self.installReview(review, in: &workflow)
        return review.handle
      },
      completion: { result in
        switch result {
        case .failure(let error): completion(.failure(Self.storeProblem(error)))
        case .success(let handle):
          completion(
            .success([
              "handle": handle, "items": items, "readyCount": items.count,
              "blockedCount": selected.count - items.count,
              "explicitConfirmationRequired": true,
            ]))
        }
      }
    )
  }

  private func confirmApprovals(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    readiness: [String: Any],
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["expectedRevision", "handle"],
      let handle = payload["handle"] as? String,
      let revision = Self.payloadRevision(payload, expected: expectedRevision)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { [peopleStore] workflow, _ in
        guard
          let index = Self.reviewIndex(
            handle: handle, kind: .approval, revision: revision, workflow: workflow
          )
        else { throw CompanionStoreError.invalidReview }
        let review = workflow.reviews[index]
        let contacts = peopleStore.privateContacts()
        guard
          review.blockerHash
            == Self.reviewHash(
              kind: "approval", contactIds: review.contactIds,
              workflow: workflow, contacts: contacts
            ), let message = workflow.messageDraft, let policy = workflow.policy
        else { throw CompanionStoreError.staleMaterial }
        let byId = Dictionary(uniqueKeysWithValues: contacts.map { ($0.localId, $0) })
        for id in review.contactIds {
          guard let contact = byId[id],
            let configIndex = workflow.contacts.firstIndex(where: { $0.contactId == id })
          else { throw CompanionStoreError.staleMaterial }
          workflow.contacts[configIndex].materialRevision = contact.materialRevision
          workflow.contacts[configIndex].approvalHash = Self.approvalHash(
            contact: contact, configuration: workflow.contacts[configIndex],
            message: message, policy: policy
          )
          workflow.contacts[configIndex].approvedAt = Date()
          workflow.contacts[configIndex].approvalInvalidationReasons = []
          workflow.contacts[configIndex].updatedAt = Date()
        }
        workflow.reviews[index].consumedAt = Date()
        Self.bumpConfiguration(&workflow, activityKind: "settings-changed")
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        self.finishMutation(
          result, binding: binding, rebuild: true,
          payload: { status in
            self.automationPayload(status: status, readiness: readiness)
          }, completion: completion
        )
      }
    )
  }

  private func prepareActivation(
    binding: IOSNativeGoogleAccountBinding,
    revision: String,
    status: CompanionProjectionStatus,
    readiness: [String: Any],
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard let workflow = status.workflow,
      workflow.messageDraft != nil, workflow.policy != nil
    else {
      completion(.failure(Self.actionRequired(["ios-configuration-incomplete"])))
      return
    }
    let privateContacts = peopleStore.privateContacts()
    let ids = Self.activationEligibleContactIds(
      workflow: workflow,
      contacts: privateContacts
    )
    guard !ids.isEmpty
    else {
      completion(.failure(Self.actionRequired(["ios-configuration-incomplete"])))
      return
    }
    guard let policy = workflow.policy,
      let composer = readiness["composer"] as? [String: Any],
      let composerKind = composer["kind"] as? String,
      ["allowed", "blocked"].contains(composerKind)
    else {
      completion(.failure(Self.internalProblem("NATIVE_CONTRACT_INVALID")))
      return
    }
    let readinessIssues: [[String: Any]]
    if composerKind == "blocked" {
      guard let blockedIssues = composer["issues"] as? [[String: Any]],
        !blockedIssues.isEmpty
      else {
        completion(.failure(Self.internalProblem("NATIVE_CONTRACT_INVALID")))
        return
      }
      readinessIssues = blockedIssues
    } else {
      readinessIssues = []
    }
    let readinessIssueIds = Set(readinessIssues.compactMap { $0["id"] as? String })
    let coexistence: String
    switch status.coexistence {
    case .clear: coexistence = "clear"
    case .deleting: coexistence = "deleting"
    case .managed: coexistence = "managed"
    case .staleOrUnknown: coexistence = "stale-or-unknown"
    case .unavailable: coexistence = "unavailable"
    }
    guard
      let blocker = Self.activationReviewHash(
        contactIds: ids, workflow: workflow, contacts: privateContacts,
        status: status, readiness: readiness
      )
    else {
      completion(.failure(Self.internalProblem("NATIVE_CONTRACT_INVALID")))
      return
    }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, committedRevision in
        let review = CompanionWorkflowReview(
          handle: UUID().uuidString.lowercased(), kind: .activation,
          issuedForRevision: committedRevision,
          expiresAt: Date().addingTimeInterval(Self.reviewLifetime),
          blockerHash: blocker, contactIds: [], messageDraft: nil,
          policy: nil, privacyAction: nil, occurrenceId: nil, consumedAt: nil
        )
        Self.installReview(review, in: &workflow)
        return review.handle
      },
      completion: { result in
        switch result {
        case .failure(let error): completion(.failure(Self.storeProblem(error)))
        case .success(let handle):
          completion(
            .success([
              "platform": "ios", "handle": handle,
              "reminderRecipientCount": ids.count,
              "plannedReminderCount": status.reminderPlans.count,
              "reminderWindowLabel": Self.windowLabel(policy),
              "reminderHorizon": status.reminderHorizonState?.rawValue ?? "not-built",
              "coexistence": coexistence,
              "contactsReady": !readinessIssueIds.contains(where: {
                $0 == "readiness-contacts-authorization"
                  || $0 == "readiness-contacts-stale"
              }),
              "messageUiReady": !readinessIssueIds.contains(
                "readiness-message-composer-unavailable"
              ),
              "protectedStorageReady": !readinessIssueIds.contains(
                "readiness-protected-data-unavailable"
              ),
              "readiness": readiness,
              "deliveryMode": "user-controlled-composer",
              "limitationsDisclosure":
                "iOS only reminds you. You review the recipient and text and decide whether to tap Send. Messages and iOS control the available sender line and final transport; this app cannot select or guarantee either.",
            ]))
        }
      }
    )
  }

  private func confirmActivation(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    status: CompanionProjectionStatus,
    readiness: [String: Any],
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["expectedRevision", "handle"],
      let handle = payload["handle"] as? String,
      let revision = Self.payloadRevision(payload, expected: expectedRevision)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { [peopleStore] workflow, _ in
        guard
          let index = Self.reviewIndex(
            handle: handle, kind: .activation, revision: revision, workflow: workflow
          )
        else { throw CompanionStoreError.invalidReview }
        let review = workflow.reviews[index]
        let privateContacts = peopleStore.privateContacts()
        let eligibleIds = Self.activationEligibleContactIds(
          workflow: workflow,
          contacts: privateContacts
        )
        let currentReviewHash = Self.activationReviewHash(
          contactIds: eligibleIds, workflow: workflow, contacts: privateContacts,
          status: status, readiness: readiness
        )
        guard let currentReviewHash else {
          throw CompanionStoreError.staleMaterial
        }
        guard
          !eligibleIds.isEmpty,
          Self.activationReviewIsConfirmable(status: status, readiness: readiness),
          review.blockerHash == currentReviewHash,
          workflow.messageDraft != nil, workflow.policy != nil
        else { throw CompanionStoreError.staleMaterial }
        workflow.desired = .remindersOn
        workflow.reviews[index].consumedAt = Date()
        Self.bumpConfiguration(&workflow, activityKind: "settings-changed")
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        self.finishMutation(
          result, binding: binding, rebuild: true,
          payload: { status in
            self.automationPayload(status: status, readiness: readiness)
          }, completion: completion
        )
      }
    )
  }

  private func pauseAll(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    readiness: [String: Any],
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["expectedRevision"],
      let revision = Self.payloadRevision(payload, expected: expectedRevision)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, _ in
        workflow.desired = .paused
        Self.bumpConfiguration(&workflow, activityKind: "paused")
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        self.finishMutation(
          result, binding: binding, rebuild: true,
          payload: { status in
            self.automationPayload(status: status, readiness: readiness)
          }, completion: completion
        )
      }
    )
  }

  func privacyOperationProjection(
    operationId: String,
    status: CompanionProjectionStatus
  ) -> IOSCompanionWorkflowEngineResult {
    guard Self.validOpaque(operationId) else {
      return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
    }
    if let receipt = deletionReceiptStore.current(),
      Self.matchesPrivacyProjectionId(
        operationId,
        nativeOperationId: receipt.operationId,
        action: "delete-account"
      )
    {
      if receipt.remoteDeletionComplete,
        deletionRecoveryStore.hasPendingOrUnreadableJournal()
      {
        return .failure(Self.temporarilyUnavailable("coordination-unavailable"))
      }
      if !receipt.remoteDeletionComplete, receipt.localDataErased,
        let recovery = deletionRecoveryStore.current(),
        recovery.operationId == receipt.operationId
      {
        return recovery.remoteAcceptanceConfirmed
          ? .success(Self.accountDeletionReceiptPayload(receipt))
          : .success(
            Self.accountDeletionRecoveryUnknownPayload(
              receipt,
              sameAccountRetryAvailable: recovery.retryAuthorized
            ))
      }
      if !receipt.remoteDeletionComplete,
        deletionRecoveryStore.hasPendingOrUnreadableJournal()
      {
        return .failure(Self.temporarilyUnavailable("coordination-unavailable"))
      }
      return .success(Self.accountDeletionReceiptPayload(receipt))
    }
    guard
      let operation = status.workflow?.privacyOperations.first(where: {
        Self.matchesPrivacyProjectionId(
          operationId,
          nativeOperationId: $0.id,
          action: $0.action
        )
      })
    else { return .failure(Self.temporarilyUnavailable("coordination-unavailable")) }
    return .success(Self.privacyOperationPayload(operation))
  }

  func currentPrivacyOperationProjection(
    status: CompanionProjectionStatus
  ) -> IOSCompanionWorkflowEngineResult {
    if let receipt = deletionReceiptStore.current() {
      if receipt.remoteDeletionComplete,
        deletionRecoveryStore.hasPendingOrUnreadableJournal()
      {
        return .success([
          "kind": "unavailable",
          "reason": "coordination-unavailable",
        ])
      }
      if !receipt.remoteDeletionComplete, receipt.localDataErased,
        let recovery = deletionRecoveryStore.current(),
        recovery.operationId == receipt.operationId
      {
        return recovery.remoteAcceptanceConfirmed
          ? .success(Self.accountDeletionReceiptPayload(receipt))
          : .success(
            Self.accountDeletionRecoveryUnknownPayload(
              receipt,
              sameAccountRetryAvailable: recovery.retryAuthorized
            ))
      }
      if !receipt.remoteDeletionComplete,
        deletionRecoveryStore.hasPendingOrUnreadableJournal()
      {
        return .success([
          "kind": "unavailable",
          "reason": "coordination-unavailable",
        ])
      }
      return .success(Self.accountDeletionReceiptPayload(receipt))
    }
    if deletionReceiptStore.hasPendingOrUnreadableReceipt()
      || deletionRecoveryStore.hasPendingOrUnreadableJournal()
    {
      return .success([
        "kind": "unavailable",
        "reason": "coordination-unavailable",
      ])
    }
    guard
      let operation = status.workflow?.privacyOperations.max(by: {
        $0.updatedAt < $1.updatedAt
      })
    else { return .success(["kind": "none"]) }
    return .success(Self.privacyOperationPayload(operation))
  }

  private func resumePrivacyOperation(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    status: CompanionProjectionStatus,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard expectedRevision == nil, Set(payload.keys) == ["operationId"],
      let operationId = payload["operationId"] as? String,
      Self.validOpaque(operationId),
      let operation = status.workflow?.privacyOperations.first(where: {
        Self.matchesPrivacyProjectionId(
          operationId,
          nativeOperationId: $0.id,
          action: $0.action
        )
      })
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    if ["complete", "failed"].contains(operation.phase) {
      completion(.success(Self.privacyOperationPayload(operation)))
      return
    }
    if operation.action == "delete-account", operation.phase == "local-wiping",
      operation.reason == Self.deletionLocalWipeRecoveryReason
    {
      performAmbiguousDeletionLocalWipe(
        operation: operation,
        binding: binding,
        completion: completion
      )
    } else {
      performPrivacyAction(
        operation: operation, binding: binding, completion: completion
      )
    }
  }

  private func preparePrivacy(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["expectedRevision", "kind"],
      let action = payload["kind"] as? String, Self.privacyActions.contains(action),
      let revision = Self.payloadRevision(payload, expected: expectedRevision)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    let recoveryStateIsClear =
      !deletionReceiptStore.hasPendingOrUnreadableReceipt()
      && !deletionRecoveryStore.hasPendingOrUnreadableJournal()
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, committedRevision in
        let recoveryOperationId: String? =
          action == "wipe-local-data" && recoveryStateIsClear
          ? Self.recoverableDeletionOperation(in: workflow)?.id
          : nil
        let blocker = Self.canonicalHash([
          "privacy", action, binding.accountGeneration, revision,
          recoveryOperationId ?? "ordinary-action",
        ])
        let review = CompanionWorkflowReview(
          handle: UUID().uuidString.lowercased(), kind: .privacy,
          issuedForRevision: committedRevision,
          expiresAt: Date().addingTimeInterval(Self.reviewLifetime),
          blockerHash: blocker, contactIds: [], messageDraft: nil, policy: nil,
          privacyAction: action, occurrenceId: nil, consumedAt: nil
        )
        Self.installReview(review, in: &workflow)
        return (
          handle: review.handle,
          isDeletionRecovery: recoveryOperationId != nil
        )
      },
      completion: { result in
        switch result {
        case .failure(let error): completion(.failure(Self.storeProblem(error)))
        case .success(let prepared):
          completion(
            .success([
              "handle": prepared.handle, "kind": action,
              "titleKey": "privacy.action.\(action)",
              "consequenceKeys": Self.privacyConsequenceKeys(action),
              // iOS cannot inspect another Android installation's in-flight permit
              // detail. Account-global reset and deletion actions therefore use the
              // conservative truthful disclosure.
              "preissuedPermitMayFinish": [
                  "delete-account", "revoke-google-access",
                  prepared.isDeletionRecovery ? action : "",
                ].contains(action),
              "remoteConnectionRequired": [
                "delete-account", "revoke-google-access",
              ].contains(action) && !prepared.isDeletionRecovery,
              "externalSmsCopiesNotErased": true,
            ]))
        }
      }
    )
  }

  private func confirmPrivacy(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["expectedRevision", "handle"],
      let handle = payload["handle"] as? String,
      let revision = Self.payloadRevision(payload, expected: expectedRevision)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    let recoveryStateIsClear =
      !deletionReceiptStore.hasPendingOrUnreadableReceipt()
      && !deletionRecoveryStore.hasPendingOrUnreadableJournal()
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, _ in
        let recoveryOperationIndex: Int? =
          recoveryStateIsClear
            && workflow.reviews.first(where: { $0.handle == handle })?
              .privacyAction == "wipe-local-data"
          ? Self.recoverableDeletionOperationIndex(in: workflow)
          : nil
        let recoveryOperationId = recoveryOperationIndex.map {
          workflow.privacyOperations[$0].id
        }
        guard
          let index = Self.reviewIndex(
            handle: handle, kind: .privacy, revision: revision, workflow: workflow
          ), let action = workflow.reviews[index].privacyAction,
          Self.privacyActions.contains(action),
          workflow.reviews[index].blockerHash
            == Self.canonicalHash([
              "privacy", action, binding.accountGeneration,
              // The reviewed revision is one before the review transaction's
              // committed revision carried by the confirmation envelope.
              Self.previousRevision(of: revision),
              recoveryOperationId ?? "ordinary-action",
            ])
        else { throw CompanionStoreError.invalidReview }
        workflow.reviews[index].consumedAt = Date()
        let operation: CompanionWorkflowPrivacyOperation
        let isDeletionRecovery = recoveryOperationIndex != nil
        if let recoveryOperationIndex {
          workflow.privacyOperations[recoveryOperationIndex].phase = "local-wiping"
          workflow.privacyOperations[recoveryOperationIndex].reason =
            Self.deletionLocalWipeRecoveryReason
          workflow.privacyOperations[recoveryOperationIndex].updatedAt = Date()
          operation = workflow.privacyOperations[recoveryOperationIndex]
        } else if action == "delete-account",
          let existingIndex = workflow.privacyOperations.lastIndex(where: {
            $0.action == action
              && ["pausing", "remote-pending", "remote-draining", "local-wiping"]
                .contains($0.phase)
          })
        {
          workflow.privacyOperations[existingIndex].phase = "pausing"
          workflow.privacyOperations[existingIndex].reason = nil
          workflow.privacyOperations[existingIndex].updatedAt = Date()
          operation = workflow.privacyOperations[existingIndex]
        } else {
          operation = CompanionWorkflowPrivacyOperation(
            id: UUID().uuidString.lowercased(), action: action,
            phase: action == "delete-account" ? "pausing" : "local-wiping",
            reason: nil, updatedAt: Date()
          )
          workflow.privacyOperations.append(operation)
        }
        switch action {
        case "clear-gemini-templates":
          if workflow.messageDraft?.provenance?.source == "GEMINI" {
            workflow.messageDraft = nil
            workflow.desired = .paused
            for contactIndex in workflow.contacts.indices {
              Self.invalidateApproval(
                &workflow.contacts[contactIndex], reason: "template-changed"
              )
            }
            Self.bumpConfiguration(&workflow, activityKind: "approval-invalidated")
          }
        case "clear-activity":
          // The activity feed, terminal proposal detail, retryable display
          // records, and operation completion are cleared atomically by
          // CompanionProtectedStore.completeClearActivity after this reviewed
          // operation has committed.
          break
        case "disconnect-contacts", "sign-out-retain", "sign-out-wipe",
          "wipe-local-data", "revoke-google-access", "delete-account":
          workflow.desired = .paused
          Self.bumpConfiguration(
            &workflow,
            activityKind: action == "disconnect-contacts" ? nil : "paused"
          )
        default:
          break
        }
        return (
          operation: operation,
          isDeletionRecovery: isDeletionRecovery
        )
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        switch result {
        case .failure(let error): completion(.failure(Self.storeProblem(error)))
        case .success(let confirmed):
          if confirmed.isDeletionRecovery {
            self.performAmbiguousDeletionLocalWipe(
              operation: confirmed.operation,
              binding: binding,
              completion: completion
            )
          } else {
            self.performPrivacyAction(
              operation: confirmed.operation,
              binding: binding,
              completion: completion
            )
          }
        }
      }
    )
  }

  private func performPrivacyAction(
    operation: CompanionWorkflowPrivacyOperation,
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    let clearsGeminiProvenance = [
      "clear-gemini-templates", "disconnect-contacts", "sign-out-retain",
      "sign-out-wipe", "wipe-local-data", "revoke-google-access", "delete-account",
    ].contains(operation.action)
    let requiresPeopleSyncFence = [
      "disconnect-contacts", "sign-out-retain", "sign-out-wipe",
      "wipe-local-data", "revoke-google-access", "delete-account",
    ].contains(operation.action)
    if clearsGeminiProvenance {
      IOSGeminiSuggestionGateway.shared.clearProvenance()
    }
    guard requiresPeopleSyncFence else {
      performPrivacyActionAfterPeopleSyncFence(
        operation: operation,
        binding: binding,
        completion: completion
      )
      return
    }
    IOSPeopleSyncCoordinator.shared.invalidateOutstandingSync { [weak self] fenced in
      guard let self else { return }
      guard fenced else {
        self.updatePrivacyOperation(
          operation,
          binding: binding,
          phase: "remote-pending",
          reason: "coordination-unavailable",
          completion: completion
        )
        return
      }
      Task { @MainActor in
        IOSPeopleBackgroundRefreshCoordinator.shared.suspendForPrivacyOperation()
        self.performPrivacyActionAfterPeopleSyncFence(
          operation: operation,
          binding: binding,
          completion: completion
        )
      }
    }
  }

  private func performPrivacyActionAfterPeopleSyncFence(
    operation: CompanionWorkflowPrivacyOperation,
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    if let wipeKind: IOSCompanionWipeRecoveryKind = {
      switch operation.action {
      case "sign-out-wipe": return .signOutWipe
      case "wipe-local-data": return .wipeLocalData
      default: return nil
      }
    }() {
      var recoveryCalendar = Calendar(identifier: .gregorian)
      recoveryCalendar.timeZone = .autoupdatingCurrent
      guard IOSCompanionWipeRecoveryStore.shared.beginSaga(
        kind: wipeKind,
        operationId: operation.id,
        binding: binding,
        civilDate: CompanionProtectedStore.civilDate(
          for: Date(), calendar: recoveryCalendar
        )
      ) else {
        return updatePrivacyOperation(
          operation, binding: binding, phase: "remote-pending",
          reason: "coordination-unavailable", completion: completion
        )
      }
    }
    switch operation.action {
    case "clear-gemini-templates":
      rebuildPlan(binding: binding) { [weak self] outcome in
        guard let self else { return }
        self.updatePrivacyOperation(
          operation, binding: binding,
          phase: outcome.isSuccessful ? "complete" : "remote-pending",
          reason: outcome.isSuccessful ? nil : "coordination-unavailable",
          completion: completion
        )
      }
    case "clear-activity":
      store.completeClearActivity(
        operationId: operation.id,
        binding: binding
      ) { result in
        switch result {
        case .success(let completed):
          completion(.success(Self.privacyOperationPayload(completed)))
        case .failure(let error):
          completion(.failure(Self.storeProblem(error)))
        }
      }
    case "disconnect-contacts":
      performLocalContactsDisconnect(
        operation: operation, binding: binding, continueWithGoogleRevocation: false,
        completion: completion
      )
    case "sign-out-retain":
      rebuildPlan(binding: binding) { [weak self] _ in
        guard let self else { return }
        self.reminderCoordinator.cancelPlansAndNotifications { result in
          guard result["kind"] as? String == "ok" else {
            self.updatePrivacyOperation(
              operation, binding: binding,
              phase: "failed", reason: "coordination-unavailable",
              completion: completion
            )
            return
          }
          Task { @MainActor in
            let success = await IOSGoogleIdentityCoordinator.shared
              .completeSignOutAfterSafetyShutdown(retainData: true)
            self.updatePrivacyOperation(
              operation, binding: binding,
              phase: success ? "complete" : "failed",
              reason: success ? nil : "coordination-unavailable",
              completion: completion
            )
          }
        }
      }
    case "sign-out-wipe":
      // Remove reminders first, then attempt SDK sign-out and People file/key
      // deletion independently. A Firebase sign-out exception cannot retain
      // Contacts; the still-durable operation reports cleanup pending and the
      // identity interlock blocks ordinary account use until a safe retry.
      reminderCoordinator.cancelPlansAndNotifications { [weak self] result in
        guard let self else { return }
        guard result["kind"] as? String == "ok" else {
          return self.updatePrivacyOperation(
            operation, binding: binding, phase: "remote-pending",
            reason: "coordination-unavailable", completion: completion
          )
        }
        Task { @MainActor in
          let success = await IOSGoogleIdentityCoordinator.shared
            .completeSignOutAfterSafetyShutdown(retainData: false)
          guard success else {
            self.updatePrivacyOperation(
              operation, binding: binding, phase: "remote-pending",
              reason: "coordination-unavailable", completion: completion
            )
            return
          }
          guard IOSCompanionWipeRecoveryStore.shared.markLocalCleanupComplete(
            operationId: operation.id
          ) else {
            self.updatePrivacyOperation(
              operation, binding: binding, phase: "remote-pending",
              reason: "coordination-unavailable", completion: completion
            )
            return
          }
          guard IOSComposerReservationJournal.shared.destroyAll(),
            IOSCompanionWipeRecoveryStore.shared.markReservationJournalDestroyed(
              operationId: operation.id
            )
          else {
            self.updatePrivacyOperation(
              operation, binding: binding, phase: "remote-pending",
              reason: "coordination-unavailable", completion: completion
            )
            return
          }
          self.reminderCoordinator.wipeCompanionData { wipeResult in
            guard wipeResult["kind"] as? String == "ok" else {
              self.updatePrivacyOperation(
                operation, binding: binding, phase: "remote-pending",
                reason: "coordination-unavailable", completion: completion
              )
              return
            }
            guard IOSCompanionWipeRecoveryStore.shared
              .markNotificationCleanupVerified(operationId: operation.id)
            else {
              completion(
                .success(
                  Self.privacyOperationPayload(
                    operation, phase: "remote-pending",
                    reason: "coordination-unavailable"
                  )))
              return
            }
            guard IOSCompanionWipeRecoveryStore.shared.clearCompletedSaga(
              operationId: operation.id
            ) else {
              completion(
                .success(
                  Self.privacyOperationPayload(
                    operation, phase: "remote-pending",
                    reason: "coordination-unavailable"
                  )))
              return
            }
            completion(
              .success(
                Self.privacyOperationPayload(
                  operation, phase: "complete", reason: nil
                )))
          }
        }
      }
    case "wipe-local-data":
      reminderCoordinator.wipeCompanionData { result in
        guard result["kind"] as? String == "ok" else {
          return completion(
            .success(
              Self.privacyOperationPayload(
                operation, phase: "failed", reason: "coordination-unavailable"
              )))
        }
        guard IOSCompanionWipeRecoveryStore.shared
          .markNotificationCleanupVerified(operationId: operation.id)
        else {
          return completion(
            .success(
              Self.privacyOperationPayload(
                operation, phase: "failed", reason: "coordination-unavailable"
              )))
        }
        Task { @MainActor in
          let success = await IOSGoogleIdentityCoordinator.shared
            .wipeLocalDataAfterSafetyShutdown(binding: binding)
          let localCleanupRecorded = success
            && IOSCompanionWipeRecoveryStore.shared.markLocalCleanupComplete(
              operationId: operation.id
            )
          let reservationJournalRecorded = localCleanupRecorded
            && IOSComposerReservationJournal.shared.destroyAll()
            && IOSCompanionWipeRecoveryStore.shared
              .markReservationJournalDestroyed(operationId: operation.id)
          let journalCompleted = reservationJournalRecorded
            && IOSCompanionWipeRecoveryStore.shared.clearCompletedSaga(
              operationId: operation.id
            )
          completion(
            .success(
              Self.privacyOperationPayload(
                operation,
                phase: journalCompleted ? "complete" : "failed",
                reason: journalCompleted ? nil : "coordination-unavailable"
              )))
        }
      }
    case "revoke-google-access":
      if ["local-cleared", "verifying", "remote-draining", "provider-revoked"]
        .contains(operation.phase)
      {
        performContactDerivedReset(
          operation: operation, binding: binding, completion: completion
        )
      } else {
        performLocalContactsDisconnect(
          operation: operation, binding: binding, continueWithGoogleRevocation: true,
          completion: completion
        )
      }
    case "delete-account":
      performAccountDeletion(
        operation: operation, binding: binding, completion: completion
      )
    default:
      completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID")))
    }
  }

  private func performAmbiguousDeletionLocalWipe(
    operation: CompanionWorkflowPrivacyOperation,
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard operation.action == "delete-account", operation.phase == "local-wiping",
      operation.reason == Self.deletionLocalWipeRecoveryReason
    else {
      completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID")))
      return
    }
    IOSGeminiSuggestionGateway.shared.clearProvenance()
    deletionRecoveryStore.recordReviewedLocalWipe(
      operationId: operation.id,
      binding: binding
    ) { [weak self] recoveryPersisted in
      guard let self else { return }
      guard recoveryPersisted else {
        self.updatePrivacyOperation(
          operation,
          binding: binding,
          phase: "remote-pending",
          reason: "coordination-unavailable",
          completion: completion
        )
        return
      }
      self.deletionReceiptStore.recordPending(operationId: operation.id) {
        [weak self] receiptPersisted in
        guard let self else { return }
        guard receiptPersisted else {
          // The reviewed recovery journal committed first. Keep the operation
          // blocked and let resumeIfNeeded recreate only this exact receipt;
          // destructive cleanup never starts without both durable records.
          self.deletionCleanup.resumeIfNeeded()
          completion(
            .success(
              Self.privacyOperationPayload(
                operation,
                phase: "local-wiping",
                reason: nil
              )))
          return
        }
        self.deletionCleanup.finishLocalCleanup(operationId: operation.id) {
          receipt in
          guard let receipt else {
            completion(
              .success(
                Self.privacyOperationPayload(
                  operation,
                  phase: "local-wiping",
                  reason: nil
                )))
            return
          }
          completion(
            .success(
              receipt.localDataErased
                ? Self.accountDeletionRecoveryUnknownPayload(
                  receipt,
                  sameAccountRetryAvailable: self.deletionRecoveryStore
                    .isRetryAuthorized(operationId: receipt.operationId)
                )
                : Self.accountDeletionReceiptPayload(receipt)
            ))
        }
      }
    }
  }

  private func performLocalContactsDisconnect(
    operation: CompanionWorkflowPrivacyOperation,
    binding: IOSNativeGoogleAccountBinding,
    continueWithGoogleRevocation: Bool,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    updatePrivacyOperation(
      operation, binding: binding, phase: "local-wiping", reason: nil
    ) { [weak self] transitionResult in
      guard let self else { return }
      guard case .success(let rawPayload) = transitionResult,
        let payload = rawPayload as? [String: Any],
        payload["kind"] as? String == "local-wiping"
      else {
        completion(transitionResult)
        return
      }
      self.reminderCoordinator.cancelPlansAndNotifications { result in
        guard result["kind"] as? String == "ok" else {
          return self.updatePrivacyOperation(
            operation, binding: binding, phase: "local-wiping",
            reason: "coordination-unavailable", completion: completion
          )
        }
        self.peopleStore.clearContactsRetainingBinding(
          expectedBinding: binding
        ) { cleared in
          guard cleared else {
            return self.updatePrivacyOperation(
              operation, binding: binding, phase: "local-wiping",
              reason: "coordination-unavailable", completion: completion
            )
          }
          self.store.clearContactDerivedState(
            operationId: operation.id,
            action: operation.action,
            completionPhase: continueWithGoogleRevocation
              ? "local-cleared" : "complete",
            binding: binding
          ) { workflowResult in
            guard case .success(let updatedOperation) = workflowResult else {
              self.updatePrivacyOperation(
                operation, binding: binding, phase: "local-wiping",
                reason: "coordination-unavailable", completion: completion
              )
              return
            }
            guard continueWithGoogleRevocation else {
              completion(.success(Self.privacyOperationPayload(updatedOperation)))
              return
            }
            self.performContactDerivedReset(
              operation: updatedOperation,
              binding: binding,
              completion: completion
            )
          }
        }
      }
    }
  }

  private func performContactDerivedReset(
    operation: CompanionWorkflowPrivacyOperation,
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    store.readProjectionStatus { [weak self] statusResult in
      guard let self, case .success(let status) = statusResult,
        let workflow = status.workflow, workflow.account.matches(binding)
      else {
        self?.updatePrivacyOperation(
          operation, binding: binding,
          phase: operation.phase == "provider-revoked"
            ? "provider-revoked" : "local-cleared",
          reason: "coordination-unavailable", completion: completion
        )
        return
      }
      if operation.phase == "provider-revoked"
        || IOSCompanionConsentLedgerPolicy.hasCurrentContactsScopeRevoked(
          workflow.consentReceipts
        )
      {
        self.finishProviderRevocationCleanup(
          operation: operation, binding: binding, completion: completion
        )
        return
      }
      self.performContactDerivedRemoteReset(
        operation: operation, binding: binding, completion: completion
      )
    }
  }

  private func performContactDerivedRemoteReset(
    operation: CompanionWorkflowPrivacyOperation,
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    updatePrivacyOperation(
      operation, binding: binding, phase: "verifying", reason: nil
    ) { [weak self] transitionResult in
      guard let self else { return }
      guard case .success(let rawPayload) = transitionResult,
        let payload = rawPayload as? [String: Any],
        payload["kind"] as? String == "verifying"
      else {
        completion(transitionResult)
        return
      }
      Task { @MainActor in
        let reauthentication = await IOSGoogleIdentityCoordinator.shared
          .ensureRecentExactGoogleAuthentication(binding: binding)
        guard case .success = reauthentication else {
          let reason: String
          if case .failure(let failure) = reauthentication {
            reason = Self.deletionIdentityFailureReason(failure)
          } else {
            reason = "account-reconnect-required"
          }
          self.updatePrivacyOperation(
            operation, binding: binding, phase: "local-cleared", reason: reason,
            completion: completion
          )
          return
        }

        self.contactResetClient.startOrReplay(
          binding: binding,
          requestId: operation.id
        ) { result in
          switch result {
          case .failure(let failure):
            self.updatePrivacyOperation(
              operation, binding: binding, phase: "local-cleared",
              reason: Self.contactResetFailureReason(failure),
              completion: completion
            )
          case .success(.inProgress(let drainUntil)):
            self.updatePrivacyOperation(
              operation, binding: binding,
              phase: drainUntil == nil ? "verifying" : "remote-draining",
              reason: nil, completion: completion
            )
          case .success(.completed):
            self.disconnectGoogleProviderAfterRemoteReset(
              operation: operation, binding: binding, completion: completion
            )
          }
        }
      }
    }
  }

  private func disconnectGoogleProviderAfterRemoteReset(
    operation: CompanionWorkflowPrivacyOperation,
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    Task { @MainActor in
      let providerDisconnected = await IOSGoogleIdentityCoordinator.shared
        .disconnectGoogleProviderAfterLocalCleanup()
      guard providerDisconnected else {
        self.updatePrivacyOperation(
          operation, binding: binding, phase: "local-cleared",
          reason: "coordination-unavailable", completion: completion
        )
        return
      }
      self.markProviderRevoked(
        operation: operation,
        binding: binding
      ) { markedOperation in
        guard let markedOperation else {
          self.updatePrivacyOperation(
            operation, binding: binding, phase: "local-cleared",
            reason: "coordination-unavailable", completion: completion
          )
          return
        }
        self.finishProviderRevocationCleanup(
          operation: markedOperation, binding: binding, completion: completion
        )
      }
    }
  }

  private func markProviderRevoked(
    operation: CompanionWorkflowPrivacyOperation,
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (CompanionWorkflowPrivacyOperation?) -> Void
  ) {
    store.markContactsProviderRevoked(
      operationId: operation.id,
      binding: binding
    ) { result in
      completion(try? result.get())
    }
  }

  private func finishProviderRevocationCleanup(
    operation: CompanionWorkflowPrivacyOperation,
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    recordContactsScopeRevoked(binding: binding) { [weak self] recorded in
      guard let self else { return }
      guard recorded else {
        self.updatePrivacyOperation(
          operation, binding: binding, phase: "provider-revoked",
          reason: "coordination-unavailable", completion: completion
        )
        return
      }
      Task { @MainActor in
        let sdkCleanupComplete = await IOSGoogleIdentityCoordinator.shared
          .finishRevokedGoogleSDKCleanupAfterLocalCleanup()
        self.updatePrivacyOperation(
          operation, binding: binding,
          phase: sdkCleanupComplete ? "complete" : "provider-revoked",
          reason: sdkCleanupComplete ? nil : "coordination-unavailable",
          completion: completion
        )
      }
    }
  }

  private func performAccountDeletion(
    operation: CompanionWorkflowPrivacyOperation,
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    if let receipt = deletionReceiptStore.current(),
      receipt.operationId == operation.id
    {
      deletionCleanup.finishLocalCleanup(operationId: operation.id) { receipt in
        guard let receipt else {
          completion(
            .success(
              Self.privacyOperationPayload(
                operation, phase: "remote-pending", reason: "coordination-unavailable"
              )))
          return
        }
        completion(.success(Self.accountDeletionReceiptPayload(receipt)))
      }
      return
    }

    Task { @MainActor in
      let reauthentication = await IOSGoogleIdentityCoordinator.shared
        .ensureRecentExactGoogleAuthentication(binding: binding)
      guard case .success = reauthentication else {
        let reason: String
        if case .failure(let failure) = reauthentication {
          reason = Self.deletionIdentityFailureReason(failure)
        } else {
          reason = "account-reconnect-required"
        }
        self.updatePrivacyOperation(
          operation, binding: binding, phase: "remote-pending", reason: reason,
          completion: completion
        )
        return
      }

      self.deletionClient.startOrReplay(
        binding: binding,
        requestId: operation.id
      ) { result in
        switch result {
        case .failure(let failure):
          self.updatePrivacyOperation(
            operation, binding: binding, phase: "remote-pending",
            reason: Self.deletionClientFailureReason(failure),
            completion: completion
          )
        case .success(let acceptance):
          guard acceptance.receiptId == operation.id else {
            self.updatePrivacyOperation(
              operation, binding: binding, phase: "remote-pending",
              reason: "coordination-unavailable", completion: completion
            )
            return
          }
          self.deletionReceiptStore.recordRemoteDraining(operationId: operation.id) {
            persisted in
            guard persisted else {
              self.updatePrivacyOperation(
                operation, binding: binding, phase: "remote-pending",
                reason: "coordination-unavailable", completion: completion
              )
              return
            }
            self.updatePrivacyOperation(
              operation, binding: binding, phase: "remote-draining", reason: nil
            ) { transitionResult in
              guard case .success(let rawPayload) = transitionResult,
                let payload = rawPayload as? [String: Any],
                payload["kind"] as? String == "remote-draining"
              else {
                completion(transitionResult)
                return
              }
              self.deletionCleanup.finishLocalCleanup(operationId: operation.id) {
                receipt in
                guard let receipt else {
                  completion(
                    .success(
                      Self.privacyOperationPayload(
                        operation, phase: "remote-pending",
                        reason: "coordination-unavailable"
                      )))
                  return
                }
                completion(.success(Self.accountDeletionReceiptPayload(receipt)))
              }
            }
          }
        }
      }
    }
  }

  private func updatePrivacyOperation(
    _ operation: CompanionWorkflowPrivacyOperation,
    binding: IOSNativeGoogleAccountBinding,
    phase: String,
    reason: String?,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    store.transitionPrivacyOperation(
      operationId: operation.id,
      action: operation.action,
      phase: phase,
      reason: reason,
      binding: binding
    ) { result in
      switch result {
      case .success(let updated):
        completion(.success(Self.privacyOperationPayload(updated)))
      case .failure(let error):
        completion(.failure(Self.storeProblem(error)))
      }
    }
  }

  func recordContactsConsent(
    binding: IOSNativeGoogleAccountBinding,
    disclosureAcknowledged: Bool,
    completion: @escaping (Bool) -> Void
  ) {
    store.readProjectionStatus { [weak self] result in
      guard let self, case .success(let status) = result else {
        completion(false)
        return
      }
      self.store.mutateWorkflow(
        expectedRevision: status.revision,
        binding: binding,
        body: { workflow, _ in
          guard IOSCompanionConsentLedgerPolicy.recordContactsGrant(
            receipts: &workflow.consentReceipts,
            disclosureAcknowledged: disclosureAcknowledged,
            at: Date()
          ) else { throw CompanionStoreError.invalidWorkflowState }
        },
        completion: { result in completion((try? result.get()) != nil) }
      )
    }
  }

  private func recordContactsScopeRevoked(
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (Bool) -> Void
  ) {
    store.readProjectionStatus { [weak self] result in
      guard let self, case .success(let status) = result else {
        completion(false)
        return
      }
      self.store.mutateWorkflow(
        expectedRevision: status.revision,
        binding: binding,
        body: { workflow, _ in
          guard IOSCompanionConsentLedgerPolicy.recordScopeRevoked(
            receipts: &workflow.consentReceipts,
            at: Date()
          ) else { throw CompanionStoreError.invalidWorkflowState }
        },
        completion: { result in completion((try? result.get()) != nil) }
      )
    }
  }

  func reconcileAfterPeopleSync(
    binding: IOSNativeGoogleAccountBinding,
    completion: (() -> Void)? = nil
  ) {
    store.readProjectionStatus { [weak self] result in
      guard let self, case .success(let status) = result,
        let workflow = status.workflow, workflow.account.matches(binding)
      else {
        completion?()
        return
      }
      let privateById = Dictionary(
        uniqueKeysWithValues: self.peopleStore.privateContacts().map { ($0.localId, $0) }
      )
      self.store.mutateWorkflow(
        expectedRevision: status.revision, binding: binding,
        body: { workflow, _ in
          var changed = false
          for index in workflow.contacts.indices {
            guard let contact = privateById[workflow.contacts[index].contactId] else {
              if workflow.contacts[index].approvalHash != nil {
                Self.invalidateApproval(
                  &workflow.contacts[index], reason: "name-changed"
                )
                changed = true
              }
              continue
            }
            if workflow.contacts[index].materialRevision != contact.materialRevision {
              Self.invalidateApproval(&workflow.contacts[index], reason: "name-changed")
              workflow.contacts[index].materialRevision = contact.materialRevision
              changed = true
            }
          }
          if changed { Self.bumpConfiguration(&workflow, activityKind: "sync") }
        },
        completion: { _ in
          self.rebuildPlan(binding: binding) { _ in completion?() }
        }
      )
    }
  }

  func reconcileReminderPlanForLifecycle(
    binding: IOSNativeGoogleAccountBinding,
    completion: (() -> Void)? = nil
  ) {
    rebuildPlan(binding: binding) { _ in completion?() }
  }

  private func rebuildPlan(
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (IOSCompanionPlanRebuildOutcome) -> Void
  ) {
    store.readWorkflowSnapshot { [weak self] result in
      guard let self else {
        return completion(.failed(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
      }
      guard case .success(let snapshot) = result else {
        if case .failure(let error) = result {
          return completion(.failed(Self.storeProblem(error)))
        }
        return completion(.failed(Self.internalProblem("COMPANION_STORAGE_UNAVAILABLE")))
      }
      guard let workflow = snapshot.workflow, workflow.account.matches(binding) else {
        return completion(.failed(Self.temporarilyUnavailable("account-reconnect-required")))
      }

      guard workflow.desired == .remindersOn,
        let draft = workflow.messageDraft,
        IOSCompanionMessagePlaceholderPolicy.isValid(
          text: draft.text,
          placeholderMode: draft.placeholderMode
        ),
        IOSBirthdayMessageContentPolicy.issueCodes(
          text: draft.text, declaredLanguage: draft.language
        ).isEmpty,
        let policy = workflow.policy,
        let time = Self.timeComponents(policy.primaryStart)
      else {
        self.store.replaceWorkflowPlan(
          binding: binding,
          expectedConfigurationGeneration: workflow.configurationGeneration,
          planningIndex: nil,
          plans: []
        ) { replaceResult in
          guard case .success = replaceResult else {
            if case .failure(let error) = replaceResult {
              return completion(.failed(Self.storeProblem(error)))
            }
            return completion(.failed(Self.internalProblem("COMPANION_STORAGE_UNAVAILABLE")))
          }
          self.reminderCoordinator.reconcilePersisted { response in
            completion(
              Self.planRebuildOutcome(
                from: response,
                remindersDesired: workflow.desired == .remindersOn
              ))
          }
        }
        return
      }

      guard workflow.contacts.map(\.contactId) == workflow.contacts.map(\.contactId).sorted(),
        let contactTableDigest = IOSCompanionOccurrenceIdentity.contactTableDigest(
          namespace: snapshot.occurrenceNamespace,
          canonicalContactIdentifiers: workflow.contacts.map(\.contactId)
        )
      else {
        return completion(.failed(Self.internalProblem("COMPANION_PLAN_BINDING_INVALID")))
      }
      let privateContacts = peopleStore.privateContacts()
      let privateById = Dictionary(
        uniqueKeysWithValues: privateContacts.map { ($0.localId, $0) }
      )
      let destinationCounts = Self.enabledDestinationCounts(
        workflow: workflow,
        contacts: privateById
      )
      let blockedDestinations = Set(
        IOSCompanionDestinationBlocklistPolicy.normalized(
          workflow.blockedDestinations
        ) ?? []
      )
      let now = Date()
      let todayStart = calendar.startOfDay(for: now)
      let baseCivilDate = Self.localDate(todayStart, calendar: calendar)
      let timeZoneIdentifier = calendar.timeZone.identifier
      var ordinalsByDay = [[UInt16]](
        repeating: [],
        count: IOSCompanionPlanningIndex.planningDayCount
      )
      for (ordinal, configuration) in workflow.contacts.enumerated() {
        guard let contact = privateById[configuration.contactId],
          Self.isPlanningEligible(
            contact: contact,
            configuration: configuration,
            workflow: workflow,
            blockedDestinations: blockedDestinations,
            destinationCounts: destinationCounts
          ),
          let birthdayId = configuration.selectedBirthdayId,
          let birthday = contact.birthdays.first(where: { $0.localId == birthdayId }),
          let rendered = Self.render(draft: draft, contact: contact),
          Self.smsEstimate(rendered).segments <= draft.requestedSegmentCap,
          let encodedOrdinal = UInt16(exactly: ordinal)
        else { continue }
        for date in self.occurrenceDates(
          birthday: birthday,
          leapPolicy: configuration.leapPolicy,
          from: now
        ) {
          let offset = calendar.dateComponents(
            [.day],
            from: todayStart,
            to: calendar.startOfDay(for: date)
          ).day
          guard let offset,
            (0..<IOSCompanionPlanningIndex.planningDayCount).contains(offset)
          else { continue }
          ordinalsByDay[offset].append(encodedOrdinal)
        }
      }
      let planningIndex: IOSCompanionPlanningIndex
      do {
        planningIndex = try IOSCompanionPlanningIndex(
          configurationGeneration: workflow.configurationGeneration,
          baseCivilDate: baseCivilDate,
          timeZoneIdentifier: timeZoneIdentifier,
          contactTableDigest: contactTableDigest,
          contactCount: workflow.contacts.count,
          ordinalsByDay: ordinalsByDay
        )
      } catch {
        return completion(.failed(Self.internalProblem("COMPANION_PLAN_CAPACITY_INVALID")))
      }
      var plans: [CompanionReminderPlan] = []
      for dayOffset in ordinalsByDay.indices where !ordinalsByDay[dayOffset].isEmpty {
        guard let date = calendar.date(byAdding: .day, value: dayOffset, to: todayStart)
        else { continue }
        let civilDate = Self.localDate(date, calendar: calendar)
        let firstOrdinal = Int(ordinalsByDay[dayOffset][0])
        guard firstOrdinal < workflow.contacts.count,
          let occurrenceId = IOSCompanionOccurrenceIdentity.occurrenceId(
            namespace: snapshot.occurrenceNamespace,
            accountGeneration: binding.accountGeneration,
            contactIdentifier: workflow.contacts[firstOrdinal].contactId,
            civilDate: civilDate
          )
        else { continue }
        plans.append(
          CompanionReminderPlan(
            occurrenceId: occurrenceId, civilDate: civilDate,
            hour: time.hour, minute: time.minute
          )
        )
      }
      self.store.replaceWorkflowPlan(
        binding: binding,
        expectedConfigurationGeneration: workflow.configurationGeneration,
        planningIndex: planningIndex,
        plans: plans
      ) { replaceResult in
        guard case .success = replaceResult else {
          if case .failure(let error) = replaceResult {
            return completion(.failed(Self.storeProblem(error)))
          }
          return completion(.failed(Self.internalProblem("COMPANION_STORAGE_UNAVAILABLE")))
        }
        self.reminderCoordinator.reconcilePersisted { response in
          let outcome = Self.planRebuildOutcome(from: response, remindersDesired: true)
          guard case .succeeded = outcome else { return completion(outcome) }
          self.store.markReminderActivationCompleted(
            binding: binding,
            expectedConfigurationGeneration: workflow.configurationGeneration
          ) { markerResult in
            switch markerResult {
            case .success:
              completion(.succeeded)
            case .failure(let error):
              completion(.failed(Self.storeProblem(error)))
            }
          }
        }
      }
    }
  }

  private static func planRebuildOutcome(
    from response: [String: Any],
    remindersDesired: Bool
  ) -> IOSCompanionPlanRebuildOutcome {
    let kind = response["kind"] as? String
    let authorization = response["authorization"] as? String
    if remindersDesired {
      if authorization == "denied" {
        return .failed(actionRequired(["notification-permission-missing"]))
      }
      if authorization == "not-determined" || authorization == "unknown" {
        return .failed(actionRequired(["notification-permission-missing"]))
      }
    }
    guard kind == "ok" else {
      let code = response["code"] as? String
      if code == "REMINDER_SETTINGS_REQUIRED" {
        return .failed(actionRequired(["notification-permission-missing"]))
      }
      if code == "REMINDER_HORIZON_PARTIAL"
        || code == "REMINDER_RECONCILIATION_SUPERSEDED"
      {
        return .failed(temporarilyUnavailable("scheduler-delayed"))
      }
      let supportCode = code?.range(
        of: "^[A-Z][A-Z0-9_]{2,63}$", options: .regularExpression
      ) != nil ? code! : "REMINDER_RECONCILIATION_FAILED"
      return .failed(internalProblem(supportCode))
    }
    guard remindersDesired else { return .succeeded }
    let planned = response["plannedDateCount"] as? Int ?? 0
    let scheduled = response["scheduledCount"] as? Int ?? 0
    let failed = response["failedCount"] as? Int ?? Int.max
    guard ["authorized", "ephemeral", "provisional"].contains(authorization ?? ""),
      planned >= 0, failed == 0,
      planned == 0 ? scheduled == 0 : scheduled > 0
    else {
      return .failed(actionRequired(["ios-configuration-incomplete"]))
    }
    return .succeeded
  }

  private func finishMutation<Value>(
    _ result: Result<Value, CompanionStoreError>,
    binding: IOSNativeGoogleAccountBinding,
    rebuild: Bool,
    payload: @escaping (CompanionProjectionStatus, Value) -> Any,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard case .success(let value) = result else {
      if case .failure(let error) = result {
        completion(.failure(Self.storeProblem(error)))
      }
      return
    }
    let finish = { [weak self] in
      guard let self else { return }
      self.store.readProjectionStatus { statusResult in
        switch statusResult {
        case .success(let status): completion(.success(payload(status, value)))
        case .failure(let error): completion(.failure(Self.storeProblem(error)))
        }
      }
    }
    if rebuild {
      rebuildPlan(binding: binding) { outcome in
        switch outcome {
        case .succeeded:
          finish()
        case .failed(let problem):
          completion(.failure(problem))
        }
      }
    } else {
      finish()
    }
  }

  private func finishMutation(
    _ result: Result<Void, CompanionStoreError>,
    binding: IOSNativeGoogleAccountBinding,
    rebuild: Bool,
    payload: @escaping (CompanionProjectionStatus) -> Any,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    finishMutation(
      result, binding: binding, rebuild: rebuild,
      payload: { status, _ in payload(status) }, completion: completion
    )
  }

  private static func contactConfiguration(
    _ contact: IOSPeoplePrivateContact,
    in workflow: CompanionWorkflowState
  ) -> CompanionWorkflowContact {
    workflow.contacts.first(where: { $0.contactId == contact.localId })
      ?? CompanionWorkflowContact(
        contactId: contact.localId,
        selectedPhoneId: contact.phones.filter({ $0.e164 != nil }).count == 1
          ? contact.phones.first(where: { $0.e164 != nil })?.localId : nil,
        selectedBirthdayId: contact.birthdays.count == 1
          ? contact.birthdays[0].localId : nil,
        leapPolicy: nil,
        enrollment: .off,
        materialRevision: contact.materialRevision,
        approvalHash: nil,
        approvedAt: nil,
        approvalInvalidationReasons: [],
        lastOutcomeLabel: nil,
        updatedAt: Date()
      )
  }

  private static func upsert(
    _ configuration: CompanionWorkflowContact,
    in workflow: inout CompanionWorkflowState
  ) {
    if let index = workflow.contacts.firstIndex(where: {
      $0.contactId == configuration.contactId
    }) {
      workflow.contacts[index] = configuration
    } else {
      workflow.contacts.append(configuration)
    }
  }

  private static func invalidateApproval(
    _ configuration: inout CompanionWorkflowContact,
    reason: String
  ) {
    guard configuration.approvalHash != nil || configuration.approvedAt != nil else { return }
    configuration.approvalInvalidationReasons = Array(
      Set(configuration.approvalInvalidationReasons + [reason])
    ).sorted()
  }

  private static func bumpConfiguration(
    _ workflow: inout CompanionWorkflowState,
    activityKind: String?
  ) {
    guard workflow.configurationGeneration < UInt64.max else { return }
    workflow.configurationGeneration += 1
    if let activityKind {
      workflow.activity.append(
        CompanionWorkflowActivity(
          id: UUID().uuidString.lowercased(), kind: activityKind,
          reason: nil, occurredAt: Date()
        )
      )
    }
  }

  private static func installReview(
    _ review: CompanionWorkflowReview,
    in workflow: inout CompanionWorkflowState
  ) {
    workflow.reviews.removeAll {
      $0.kind == review.kind || $0.consumedAt != nil || $0.expiresAt < Date()
    }
    workflow.reviews.append(review)
    if workflow.reviews.count > maximumReviewCount {
      workflow.reviews = Array(workflow.reviews.suffix(maximumReviewCount))
    }
  }

  private static func reviewIndex(
    handle: String,
    kind: CompanionWorkflowReviewKind,
    revision: String,
    workflow: CompanionWorkflowState
  ) -> Int? {
    workflow.reviews.firstIndex {
      $0.handle == handle && $0.kind == kind && $0.consumedAt == nil
        && $0.issuedForRevision == revision && $0.expiresAt >= Date()
    }
  }

  private static func effectivePhoneId(
    _ contact: IOSPeoplePrivateContact,
    workflow: CompanionWorkflowState
  ) -> String? {
    effectivePhoneId(
      contact,
      configuration: workflow.contacts.first { $0.contactId == contact.localId }
    )
  }

  private static func effectivePhoneId(
    _ contact: IOSPeoplePrivateContact,
    configuration: CompanionWorkflowContact?
  ) -> String? {
    if let selected = configuration?.selectedPhoneId,
      contact.phones.contains(where: { $0.localId == selected && $0.e164 != nil })
    {
      return selected
    }
    let valid = contact.phones.filter { $0.e164 != nil }
    return valid.count == 1 ? valid[0].localId : nil
  }

  private static func effectiveBirthdayId(
    _ contact: IOSPeoplePrivateContact,
    workflow: CompanionWorkflowState
  ) -> String? {
    let configuration = workflow.contacts.first { $0.contactId == contact.localId }
    if let selected = configuration?.selectedBirthdayId,
      contact.birthdays.contains(where: { $0.localId == selected })
    {
      return selected
    }
    return contact.birthdays.count == 1 ? contact.birthdays[0].localId : nil
  }

  private static func approvalHash(
    contact: IOSPeoplePrivateContact,
    configuration: CompanionWorkflowContact,
    message: CompanionWorkflowMessageDraft?,
    policy: CompanionWorkflowPolicy?,
    includeLegacyAndroidDailyCap: Bool = false
  ) -> String? {
    guard let message, let policy,
      let destination = IOSCompanionApprovalDestinationBinding.resolve(
        selectedPhoneId: configuration.selectedPhoneId,
        phones: contact.phones
      ),
      let birthday = configuration.selectedBirthdayId
    else { return nil }
    var parts = [
      "approval", contact.localId, String(contact.materialRevision),
    ] + destination.hashComponents + [
      birthday, configuration.leapPolicy ?? "none", message.language,
      message.tone, message.placeholderMode, message.text,
      String(message.requestedSegmentCap), policy.primaryStart,
      policy.primaryEnd, policy.graceEnd ?? "none",
    ]
    if includeLegacyAndroidDailyCap {
      parts.append(String(policy.legacyAndroidDailyCap))
    }
    parts.append(contentsOf: [
      message.provenance?.source ?? "USER",
      message.provenance?.modelIdentifier ?? "none",
      message.provenance?.promptPolicyVersion ?? "none",
      message.provenance?.validatorVersion ?? "legacy-user-v1",
      "user-controlled-composer-v1",
    ])
    return canonicalHash(parts)
  }

  private static func approvalMatches(
    _ storedHash: String?,
    contact: IOSPeoplePrivateContact,
    configuration: CompanionWorkflowContact,
    message: CompanionWorkflowMessageDraft?,
    policy: CompanionWorkflowPolicy?
  ) -> Bool {
    guard let storedHash,
      let current = approvalHash(
        contact: contact, configuration: configuration,
        message: message, policy: policy
      )
    else { return false }
    if storedHash == current { return true }
    // A one-way compatibility check prevents an app update from invalidating
    // every durable per-person approval solely because iOS stopped hashing the
    // non-effective Android cap. Newly confirmed approvals use only `current`.
    guard
      let legacy = approvalHash(
        contact: contact, configuration: configuration,
        message: message, policy: policy,
        includeLegacyAndroidDailyCap: true
      )
    else { return false }
    return storedHash == legacy
  }

  private static func reviewHash(
    kind: String,
    contactIds: [String],
    workflow: CompanionWorkflowState,
    contacts: [IOSPeoplePrivateContact]
  ) -> String {
    let byId = Dictionary(uniqueKeysWithValues: contacts.map { ($0.localId, $0) })
    let configurationById = Dictionary(
      uniqueKeysWithValues: workflow.contacts.map { ($0.contactId, $0) }
    )
    var parts = [
      kind, workflow.account.accountGeneration, String(workflow.configurationGeneration),
    ]
    for id in contactIds.sorted() {
      let contact = byId[id]
      let configuration = configurationById[id]
      let destination = IOSCompanionApprovalDestinationBinding.resolve(
        selectedPhoneId: configuration?.selectedPhoneId,
        phones: contact?.phones ?? []
      )
      parts.append(contentsOf: [
        id, String(contact?.materialRevision ?? 0),
        destination?.phoneId ?? configuration?.selectedPhoneId ?? "none",
        destination?.e164 ?? "none",
        destination?.metadataRelease ?? IOSPhoneNumberNormalizer.metadataRelease,
        IOSCompanionApprovalDestinationBinding.version,
        configuration?.selectedBirthdayId ?? "none",
        configuration?.leapPolicy ?? "none",
        configuration?.enrollment.rawValue ?? "off",
        configuration?.approvalHash ?? "none",
      ])
    }
    if let message = workflow.messageDraft {
      parts.append(contentsOf: [
        message.language, message.tone, message.placeholderMode,
        message.text, String(message.requestedSegmentCap),
        message.provenance?.source ?? "USER",
        message.provenance?.modelIdentifier ?? "none",
        message.provenance?.promptPolicyVersion ?? "none",
        message.provenance?.validatorVersion ?? "legacy-user-v1",
      ])
    }
    if let policy = workflow.policy {
      parts.append(contentsOf: [
        policy.primaryStart, policy.primaryEnd, policy.graceEnd ?? "none",
      ])
    }
    return canonicalHash(parts)
  }

  private static func activationCoexistenceValue(
    _ coexistence: CompanionProjectionStatus.Coexistence
  ) -> String {
    switch coexistence {
    case .clear: return "clear"
    case .deleting: return "deleting"
    case .managed: return "managed"
    case .staleOrUnknown: return "stale-or-unknown"
    case .unavailable: return "unavailable"
    }
  }

  /// Binds the content-free runtime facts shown in the final activation review
  /// to the same one-use native handle as workflow/contact material. A later
  /// horizon, coexistence, protected-data, Contacts, foreground, or MessageUI
  /// change makes confirmation stale instead of allowing a green-looking old
  /// snapshot to activate reminders.
  private static func activationReviewHash(
    contactIds: [String],
    workflow: CompanionWorkflowState,
    contacts: [IOSPeoplePrivateContact],
    status: CompanionProjectionStatus,
    readiness: [String: Any]
  ) -> String? {
    guard let composer = readiness["composer"] as? [String: Any],
      let composerKind = composer["kind"] as? String,
      ["allowed", "blocked"].contains(composerKind)
    else { return nil }

    var readinessParts = [composerKind]
    if composerKind == "blocked" {
      guard let issues = composer["issues"] as? [[String: Any]], !issues.isEmpty
      else { return nil }
      var canonicalIssues: [String] = []
      for issue in issues {
        guard let id = issue["id"] as? String,
          let code = issue["code"] as? String,
          let severity = issue["severity"] as? String,
          let blocks = issue["blocks"] as? [String],
          !blocks.isEmpty
        else { return nil }
        canonicalIssues.append(
          canonicalHash([id, code, severity] + blocks.sorted())
        )
      }
      readinessParts.append(contentsOf: canonicalIssues.sorted())
    }

    return canonicalHash([
      reviewHash(
        kind: "activation", contactIds: contactIds,
        workflow: workflow, contacts: contacts
      ),
      String(status.reminderPlans.count),
      status.reminderHorizonState?.rawValue ?? "not-built",
      activationCoexistenceValue(status.coexistence),
      canonicalHash(readinessParts),
    ])
  }

  private static func activationReviewIsConfirmable(
    status: CompanionProjectionStatus,
    readiness: [String: Any]
  ) -> Bool {
    guard
      status.reminderHorizonState == nil || status.reminderHorizonState == .full,
      status.coexistence == .clear,
      let composer = readiness["composer"] as? [String: Any],
      Set(composer.keys) == ["kind"],
      composer["kind"] as? String == "allowed"
    else { return false }
    return true
  }

  private static func messageReviewHash(
    draft: CompanionWorkflowMessageDraft,
    workflow: CompanionWorkflowState,
    contacts: [IOSPeoplePrivateContact]
  ) -> String {
    canonicalHash([
      reviewHash(
        kind: "message", contactIds: workflow.contacts.map(\.contactId),
        workflow: workflow, contacts: contacts
      ), draft.language, draft.tone, draft.placeholderMode, draft.text,
      String(draft.requestedSegmentCap),
      draft.provenance?.source ?? "USER",
      draft.provenance?.modelIdentifier ?? "none",
      draft.provenance?.promptPolicyVersion ?? "none",
      draft.provenance?.validatorVersion ?? "legacy-user-v1",
    ])
  }

  private static func policyReviewHash(
    policy: CompanionWorkflowPolicy,
    workflow: CompanionWorkflowState,
    contacts: [IOSPeoplePrivateContact]
  ) -> String {
    canonicalHash([
      reviewHash(
        kind: "policy", contactIds: workflow.contacts.map(\.contactId),
        workflow: workflow, contacts: contacts
      ), policy.primaryStart, policy.primaryEnd, policy.graceEnd ?? "none",
    ])
  }

  private static func canonicalHash(_ parts: [String]) -> String {
    var data = Data()
    for part in parts {
      let bytes = Data(part.utf8)
      var length = UInt64(bytes.count).bigEndian
      withUnsafeBytes(of: &length) { data.append(contentsOf: $0) }
      data.append(bytes)
    }
    return SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
  }

  private static func parseMessageDraft(
    _ raw: [String: Any]
  ) -> (draft: CompanionWorkflowMessageDraft?, issues: [[String: Any]]) {
    guard
      Set(raw.keys) == [
        "language", "placeholderMode", "requestedSegmentCap", "text", "tone",
      ], let language = raw["language"] as? String,
      let tone = raw["tone"] as? String,
      let mode = raw["placeholderMode"] as? [String: Any],
      let text = raw["text"] as? String,
      let cap = strictInteger(raw["requestedSegmentCap"], range: 1...2),
      ["en", "hi"].contains(language),
      ["warm", "simple", "cheerful"].contains(tone),
      [1, 2].contains(cap)
    else { return (nil, [["field": "template", "code": "internal-contract-invalid"]]) }
    let normalizedText = text.precomposedStringWithCanonicalMapping
    let modeKind: String
    if Set(mode.keys) == ["kind", "requiredCount"],
      mode["kind"] as? String == "given-name",
      strictInteger(mode["requiredCount"], range: 1...1) == 1
    {
      modeKind = "given-name"
    } else if Set(mode.keys) == ["kind", "requiredCount"],
      mode["kind"] as? String == "generic",
      strictInteger(mode["requiredCount"], range: 0...0) == 0
    {
      modeKind = "generic"
    } else {
      return (nil, [["field": "template", "code": "template-placeholder-count"]])
    }
    var issues: [[String: Any]] = []
    if normalizedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
      || normalizedText.count > 1_000 || normalizedText.utf8.count > 4_096
    {
      issues.append(["field": "template", "code": "template-empty"])
    }
    if let placeholderIssue = IOSCompanionMessagePlaceholderPolicy.issue(
      text: normalizedText,
      placeholderMode: modeKind
    ) {
      issues.append([
        "field": "template",
        "code": placeholderIssue == .unsupportedPlaceholder
          ? "template-unsupported-placeholder"
          : "template-placeholder-count",
      ])
    }
    for code in IOSBirthdayMessageContentPolicy.issueCodes(
      text: normalizedText, declaredLanguage: language
    ) {
      issues.append(["field": "template", "code": code])
    }
    guard issues.isEmpty else { return (nil, issues) }
    return (
      CompanionWorkflowMessageDraft(
        language: language, tone: tone, placeholderMode: modeKind,
        text: normalizedText, requestedSegmentCap: cap,
        provenance: CompanionWorkflowMessageProvenance(
          source: "USER", modelIdentifier: nil, promptPolicyVersion: nil,
          validatorVersion: IOSBirthdayMessageContentPolicy.validatorVersion
        )
      ),
      []
    )
  }

  private static func parsePolicy(_ raw: [String: Any]) -> CompanionWorkflowPolicy? {
    guard Set(raw.keys) == ["dailyCap", "latePolicy", "primaryEnd", "primaryStart"],
      let start = raw["primaryStart"] as? String,
      let end = raw["primaryEnd"] as? String,
      let compatibilityCap = strictInteger(raw["dailyCap"], range: 1...1_000_000),
      let startMinutes = minutes(start),
      let endMinutes = minutes(end), startMinutes < endMinutes,
      (30...240).contains(endMinutes - startMinutes),
      let late = raw["latePolicy"] as? [String: Any]
    else { return nil }
    let grace: String?
    if Set(late.keys) == ["kind"], late["kind"] as? String == "none" {
      grace = nil
    } else if Set(late.keys) == ["graceEnd", "kind"],
      late["kind"] as? String == "same-day-grace",
      let rawGrace = late["graceEnd"] as? String,
      let graceMinutes = minutes(rawGrace), graceMinutes > endMinutes,
      graceMinutes - startMinutes <= 240
    {
      grace = rawGrace
    } else {
      return nil
    }
    return CompanionWorkflowPolicy(
      primaryStart: start, primaryEnd: end, graceEnd: grace,
      // Canonicalized only to keep older protected-state decoders readable.
      // It is not consulted by any iOS planning or review decision.
      legacyAndroidDailyCap: min(compatibilityCap, 20)
    )
  }

  func lazyProposalMaterial(
    proposalId: String,
    expectedRevision: String?,
    status: CompanionProjectionStatus,
    binding: IOSNativeGoogleAccountBinding,
    now: Date = Date(),
    requireTrustedFreshness: Bool = false
  ) -> IOSCompanionLazyProposalMaterial? {
    guard expectedRevision == nil || status.revision == expectedRevision,
      let coordinates = IOSCompanionOccurrenceIdentity.untrustedProposalCoordinates(
        proposalId
      ),
      let context = lazyProposalMaterialContext(
        status: status,
        binding: binding,
        localNow: now,
        requireTrustedFreshness: requireTrustedFreshness
      ),
      let material = lazyProposalMaterial(
        dayOffset: coordinates.dayOffset,
        contactOrdinal: coordinates.contactOrdinal,
        context: context,
        status: status,
        binding: binding,
        ordinalMembershipAlreadyVerified: false
      ),
      material.proposalId == proposalId,
      material.occurrenceCivilDate
        == Self.localDate(context.materializationNow, calendar: calendar)
    else { return nil }
    return material
  }

  private func nextLazyProposalMaterial(
    status: CompanionProjectionStatus,
    binding: IOSNativeGoogleAccountBinding,
    now: Date
  ) -> IOSCompanionLazyProposalMaterial? {
    return try? IOSCompanionLazyOrdinalScanner.first(
      buildContext: {
        guard let context = self.lazyProposalMaterialContext(
          status: status,
          binding: binding,
          localNow: now,
          requireTrustedFreshness: false
        ), let dayOffset = Self.dayOffset(
          civilDate: Self.localDate(now, calendar: self.calendar),
          index: context.index
        ) else { return nil }
        return (material: context, dayOffset: dayOffset)
      },
      ordinals: { context in
        try context.material.index.ordinals(dayOffset: context.dayOffset)
      },
      materialize: { context, contactOrdinal in
        return self.lazyProposalMaterial(
          dayOffset: context.dayOffset,
          contactOrdinal: contactOrdinal,
          context: context.material,
          status: status,
          binding: binding,
          ordinalMembershipAlreadyVerified: true
        )
      }
    )
  }

  private func firstLazyProposalMaterial(
    status: CompanionProjectionStatus,
    binding: IOSNativeGoogleAccountBinding,
    startingCivilDate: String,
    now: Date
  ) -> IOSCompanionLazyProposalMaterial? {
    guard let context = lazyProposalMaterialContext(
      status: status,
      binding: binding,
      localNow: now,
      requireTrustedFreshness: false
    ), let firstOffset = Self.dayOffset(
      civilDate: startingCivilDate,
      index: context.index
    )
    else { return nil }
    for dayOffset in firstOffset..<IOSCompanionPlanningIndex.planningDayCount {
      guard let ordinals = try? context.index.ordinals(dayOffset: dayOffset)
      else { return nil }
      for ordinal in ordinals {
        if let material = lazyProposalMaterial(
          dayOffset: dayOffset,
          contactOrdinal: Int(ordinal),
          context: context,
          status: status,
          binding: binding,
          ordinalMembershipAlreadyVerified: true
        ) {
          return material
        }
      }
    }
    return nil
  }

  private func lazyProposalMaterialContext(
    status: CompanionProjectionStatus,
    binding: IOSNativeGoogleAccountBinding,
    localNow: Date,
    requireTrustedFreshness: Bool
  ) -> IOSCompanionLazyMaterialContext? {
    guard !status.resetSafetyRequiresRelease,
      let workflow = status.workflow,
      workflow.account.matches(binding),
      workflow.desired == .remindersOn,
      workflow.privacyOperations.allSatisfy({
        ["complete", "failed"].contains($0.phase)
      }),
      let index = status.planningIndex,
      index.timeZoneIdentifier == calendar.timeZone.identifier,
      index.configurationGeneration == workflow.configurationGeneration,
      index.contactCount == workflow.contacts.count,
      let draft = workflow.messageDraft,
      IOSCompanionMessagePlaceholderPolicy.isValid(
        text: draft.text,
        placeholderMode: draft.placeholderMode
      ),
      IOSBirthdayMessageContentPolicy.issueCodes(
        text: draft.text,
        declaredLanguage: draft.language
      ).isEmpty,
      workflow.policy != nil,
      let normalizedBlockedDestinations =
        IOSCompanionDestinationBlocklistPolicy.normalized(
          workflow.blockedDestinations
        )
    else { return nil }

    let contactIdentifiers = workflow.contacts.map(\.contactId)
    guard contactIdentifiers == contactIdentifiers.sorted(),
      index.contactTableDigest == IOSCompanionOccurrenceIdentity.contactTableDigest(
        namespace: status.occurrenceNamespace,
        canonicalContactIdentifiers: contactIdentifiers
      )
    else { return nil }

    let materializationNow: Date
    if requireTrustedFreshness {
      guard
        let trustedMaterializationNow =
          IOSCompanionTrustedClockPolicy.materializationNow(
            localNow: localNow,
            trustedServerEstimate: status.trustedNow
          ),
        Self.contactsFreshness(
          peopleStore.projection().sync,
          trustedNow: status.trustedNow
        ).allowsCompanionAction
      else { return nil }
      materializationNow = trustedMaterializationNow
    } else {
      materializationNow = localNow
    }

    guard let peopleSnapshot = peopleStore.privateSnapshot(),
      peopleSnapshot.binding == binding
    else { return nil }
    let privateContacts = peopleSnapshot.contacts
    let privateContactsById = Dictionary(
      uniqueKeysWithValues: privateContacts.map { ($0.localId, $0) }
    )
    return IOSCompanionLazyMaterialContext(
      workflow: workflow,
      index: index,
      draft: draft,
      materializationNow: materializationNow,
      peopleSnapshotGeneration: peopleSnapshot.generation,
      privateContactsById: privateContactsById,
      blockedDestinations: Set(normalizedBlockedDestinations),
      destinationCounts: Self.enabledDestinationCounts(
        workflow: workflow,
        contacts: privateContactsById
      )
    )
  }

  private func lazyProposalMaterial(
    dayOffset: Int,
    contactOrdinal: Int,
    context: IOSCompanionLazyMaterialContext,
    status: CompanionProjectionStatus,
    binding: IOSNativeGoogleAccountBinding,
    ordinalMembershipAlreadyVerified: Bool
  ) -> IOSCompanionLazyProposalMaterial? {
    let workflow = context.workflow
    let index = context.index
    let draft = context.draft
    guard contactOrdinal >= 0, contactOrdinal < workflow.contacts.count,
      let encodedOrdinal = UInt16(exactly: contactOrdinal),
      ordinalMembershipAlreadyVerified
        || (try? index.ordinals(dayOffset: dayOffset).contains(encodedOrdinal)) == true,
      let civilDate = Self.civilDate(dayOffset: dayOffset, index: index),
      let occurrenceDigest = IOSCompanionOccurrenceIdentity.occurrenceDigest(
        namespace: status.occurrenceNamespace,
        accountGeneration: binding.accountGeneration,
        contactIdentifier: workflow.contacts[contactOrdinal].contactId,
        civilDate: civilDate
      ),
      status.terminalLedger.check(
        digest: occurrenceDigest,
        civilDate: civilDate
      ) == .clear
    else { return nil }

    let configuration = workflow.contacts[contactOrdinal]
    guard let contact = context.privateContactsById[configuration.contactId]
    else { return nil }
    guard Self.isPlanningEligible(
      contact: contact,
      configuration: configuration,
      workflow: workflow,
      blockedDestinations: context.blockedDestinations,
      destinationCounts: context.destinationCounts
    ),
      let selectedPhoneId = configuration.selectedPhoneId,
      let recipient = contact.phones.first(where: {
        $0.localId == selectedPhoneId
      })?.e164,
      IOSCompanionDestinationBlocklistPolicy.isCanonical(recipient),
      let birthdayId = configuration.selectedBirthdayId,
      let birthday = contact.birthdays.first(where: { $0.localId == birthdayId }),
      occurrenceDates(
        birthday: birthday,
        leapPolicy: configuration.leapPolicy,
        from: context.materializationNow
      ).contains(where: { Self.localDate($0, calendar: calendar) == civilDate }),
      let body = Self.render(draft: draft, contact: contact),
      Self.smsEstimate(body).segments <= draft.requestedSegmentCap,
      let occurrenceId = IOSCompanionOccurrenceIdentity.occurrenceId(
        namespace: status.occurrenceNamespace,
        accountGeneration: binding.accountGeneration,
        contactIdentifier: contact.localId,
        civilDate: civilDate
      ),
      let proposalId = IOSCompanionOccurrenceIdentity.proposalHandle(
        namespace: status.occurrenceNamespace,
        accountGeneration: binding.accountGeneration,
        configurationGeneration: workflow.configurationGeneration,
        baseCivilDate: index.baseCivilDate,
        timeZoneIdentifier: index.timeZoneIdentifier,
        contactTableDigest: index.contactTableDigest,
        dayOffset: dayOffset,
        contactOrdinal: contactOrdinal,
        occurrenceId: occurrenceId
      )
    else { return nil }
    return IOSCompanionLazyProposalMaterial(
      proposalId: proposalId,
      accountGeneration: binding.accountGeneration,
      configurationGeneration: workflow.configurationGeneration,
      peopleSnapshotGeneration: context.peopleSnapshotGeneration,
      contactOrdinal: contactOrdinal,
      contactId: contact.localId,
      contactMaterialRevision: contact.materialRevision,
      selectedPhoneId: selectedPhoneId,
      occurrenceId: occurrenceId,
      occurrenceDigest: occurrenceDigest,
      occurrenceCivilDate: civilDate,
      recipient: recipient,
      body: body
    )
  }

  private func validatedPlanningDescriptorContext(
    status: CompanionProjectionStatus
  ) -> (IOSCompanionPlanningIndex, CompanionWorkflowState)? {
    guard let index = status.planningIndex, let workflow = status.workflow,
      index.contactCount == workflow.contacts.count,
      index.contactTableDigest == IOSCompanionOccurrenceIdentity.contactTableDigest(
        namespace: status.occurrenceNamespace,
        canonicalContactIdentifiers: workflow.contacts.map(\.contactId)
      )
    else { return nil }
    return (index, workflow)
  }

  private func plannedOccurrenceCount(
    civilDate: String,
    status: CompanionProjectionStatus
  ) -> Int {
    guard let (index, workflow) = validatedPlanningDescriptorContext(status: status),
      let dayOffset = Self.dayOffset(civilDate: civilDate, index: index),
      let ordinals = try? index.ordinals(dayOffset: dayOffset)
    else { return 0 }
    return ordinals.reduce(into: 0) { count, ordinalValue in
      let ordinal = Int(ordinalValue)
      guard ordinal < workflow.contacts.count,
        let digest = IOSCompanionOccurrenceIdentity.occurrenceDigest(
          namespace: status.occurrenceNamespace,
          accountGeneration: workflow.account.accountGeneration,
          contactIdentifier: workflow.contacts[ordinal].contactId,
          civilDate: civilDate
        ), status.terminalLedger.check(digest: digest, civilDate: civilDate) == .clear
      else { return }
      count += 1
    }
  }

  private func plannedOccurrenceDescriptor(
    occurrenceId: String,
    status: CompanionProjectionStatus
  ) -> IOSCompanionPlannedOccurrenceDescriptor? {
    guard let (index, workflow) = validatedPlanningDescriptorContext(status: status)
    else { return nil }
    for dayOffset in 0..<IOSCompanionPlanningIndex.planningDayCount {
      guard let civilDate = Self.civilDate(dayOffset: dayOffset, index: index),
        let ordinals = try? index.ordinals(dayOffset: dayOffset)
      else { return nil }
      for ordinalValue in ordinals {
        let ordinal = Int(ordinalValue)
        guard ordinal < workflow.contacts.count,
          let digest = IOSCompanionOccurrenceIdentity.occurrenceDigest(
            namespace: status.occurrenceNamespace,
            accountGeneration: workflow.account.accountGeneration,
            contactIdentifier: workflow.contacts[ordinal].contactId,
            civilDate: civilDate
          ), status.terminalLedger.check(digest: digest, civilDate: civilDate) == .clear,
          let candidateId = IOSCompanionOccurrenceIdentity.occurrenceId(
            namespace: status.occurrenceNamespace,
            accountGeneration: workflow.account.accountGeneration,
            contactIdentifier: workflow.contacts[ordinal].contactId,
            civilDate: civilDate
          ), candidateId == occurrenceId
        else { continue }
        return IOSCompanionPlannedOccurrenceDescriptor(
          occurrenceId: candidateId,
          contactId: workflow.contacts[ordinal].contactId,
          civilDate: civilDate
        )
      }
    }
    return nil
  }

  private static func enabledDestinationCounts(
    workflow: CompanionWorkflowState,
    contacts: [String: IOSPeoplePrivateContact]
  ) -> [String: Int] {
    var counts: [String: Int] = [:]
    for configuration in workflow.contacts where configuration.enrollment == .enabled {
      guard let contact = contacts[configuration.contactId],
        let destination = IOSCompanionApprovalDestinationBinding.resolve(
          selectedPhoneId: configuration.selectedPhoneId,
          phones: contact.phones
        )?.e164
      else { continue }
      counts[destination, default: 0] += 1
    }
    return counts
  }

  private static func isPlanningEligible(
    contact: IOSPeoplePrivateContact,
    configuration: CompanionWorkflowContact,
    workflow: CompanionWorkflowState,
    blockedDestinations: Set<String>,
    destinationCounts: [String: Int]
  ) -> Bool {
    guard configuration.enrollment == .enabled, !contact.deleted,
      configuration.materialRevision == contact.materialRevision,
      configuration.approvalInvalidationReasons.isEmpty,
      approvalMatches(
        configuration.approvalHash,
        contact: contact,
        configuration: configuration,
        message: workflow.messageDraft,
        policy: workflow.policy
      ),
      let destination = IOSCompanionApprovalDestinationBinding.resolve(
        selectedPhoneId: configuration.selectedPhoneId,
        phones: contact.phones
      )?.e164,
      !blockedDestinations.contains(destination),
      destinationCounts[destination] == 1,
      let birthdayId = configuration.selectedBirthdayId,
      contact.birthdays.contains(where: { $0.localId == birthdayId })
    else { return false }
    return true
  }

  private static func activationEligibleContactIds(
    workflow: CompanionWorkflowState,
    contacts: [IOSPeoplePrivateContact]
  ) -> [String] {
    let byId = Dictionary(uniqueKeysWithValues: contacts.map { ($0.localId, $0) })
    let blocked = Set(
      IOSCompanionDestinationBlocklistPolicy.normalized(
        workflow.blockedDestinations
      ) ?? []
    )
    let counts = enabledDestinationCounts(workflow: workflow, contacts: byId)
    guard let draft = workflow.messageDraft else { return [] }
    return workflow.contacts.compactMap { configuration in
      guard let contact = byId[configuration.contactId],
        isPlanningEligible(
          contact: contact,
          configuration: configuration,
          workflow: workflow,
          blockedDestinations: blocked,
          destinationCounts: counts
        ), let body = render(draft: draft, contact: contact),
        smsEstimate(body).segments <= draft.requestedSegmentCap
      else { return nil }
      return contact.localId
    }.sorted()
  }

  private static func contactsFreshness(
    _ state: IOSPeopleSafeSyncState,
    trustedNow: Date?
  ) -> IOSContactsFreshnessAssessment {
    let source: IOSContactsFreshnessSourceState
    let lastSuccess: Date?
    switch state {
    case .fresh(let completedAt, _):
      source = .verified
      lastSuccess = completedAt
    case .failedRetained(let retainedAt, _):
      source = .retainedAfterFailure
      lastSuccess = retainedAt
    case .authorizationRequired:
      source = .authorizationRequired
      lastSuccess = nil
    case .neverSynced, .syncing:
      source = .unavailable
      lastSuccess = nil
    }
    return IOSContactsFreshnessPolicy.assess(
      sourceState: source,
      lastSuccessAt: lastSuccess,
      trustedNow: trustedNow
    )
  }

  private static func civilDate(
    dayOffset: Int,
    index: IOSCompanionPlanningIndex
  ) -> String? {
    guard (0..<IOSCompanionPlanningIndex.planningDayCount).contains(dayOffset),
      let timeZone = TimeZone(identifier: index.timeZoneIdentifier)
    else { return nil }
    let parts = index.baseCivilDate.split(separator: "-", omittingEmptySubsequences: false)
    guard parts.count == 3, let year = Int(parts[0]), let month = Int(parts[1]),
      let day = Int(parts[2])
    else { return nil }
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = timeZone
    guard let base = calendar.date(
      from: DateComponents(year: year, month: month, day: day, hour: 12)
    ), let date = calendar.date(byAdding: .day, value: dayOffset, to: base)
    else { return nil }
    return localDate(date, calendar: calendar)
  }

  private static func dayOffset(
    civilDate: String,
    index: IOSCompanionPlanningIndex
  ) -> Int? {
    for offset in 0..<IOSCompanionPlanningIndex.planningDayCount
    where Self.civilDate(dayOffset: offset, index: index) == civilDate {
      return offset
    }
    return nil
  }

  private func simulate(
    policy: CompanionWorkflowPolicy,
    status: CompanionProjectionStatus
  ) -> (maximumDaily: Int, maximumRolling: Int) {
    var counts: [String: Int] = [:]
    var instants: [Date] = []
    let time = Self.timeComponents(policy.primaryStart)
    for contact in effectiveContacts(status: status)
    where contact.configuration?.enrollment == .enabled {
      for date in occurrenceDates(
        birthday: contact.selectedBirthday,
        leapPolicy: contact.configuration?.leapPolicy,
        from: Date()
      ) {
        let key = Self.localDate(date, calendar: calendar)
        counts[key, default: 0] += 1
        if let time,
          let instant = calendar.date(
            bySettingHour: time.hour,
            minute: time.minute,
            second: 0,
            of: date
          )
        {
          instants.append(instant)
        }
      }
    }
    instants.sort()
    var start = 0
    var maximumRolling = 0
    for end in instants.indices {
      while start < end, instants[end].timeIntervalSince(instants[start]) >= 86_400 {
        start += 1
      }
      let count = end - start + 1
      maximumRolling = max(maximumRolling, count)
    }
    return (counts.values.max() ?? 0, maximumRolling)
  }

  private func nextOccurrence(
    birthday: IOSPeoplePrivateBirthday?,
    leapPolicy: String?,
    from now: Date
  ) -> Date? {
    occurrenceDates(
      birthday: birthday, leapPolicy: leapPolicy, from: now
    ).first
  }

  private func occurrenceDates(
    birthday: IOSPeoplePrivateBirthday?,
    leapPolicy: String?,
    from now: Date
  ) -> [Date] {
    IOSCompanionRecurrencePlanner.occurrenceDates(
      birthday: birthday,
      leapPolicy: leapPolicy,
      from: now,
      schedulingCalendar: calendar
    )
  }

  private static func render(
    draft: CompanionWorkflowMessageDraft,
    contact: IOSPeoplePrivateContact
  ) -> String? {
    IOSBirthdayMessageContentPolicy.renderedBody(
      templateText: draft.text,
      placeholderMode: draft.placeholderMode,
      givenName: contact.givenName,
      declaredLanguage: draft.language
    )
  }

  private static func smsEstimate(_ text: String) -> (encoding: String, segments: Int) {
    let basic = Set(
      "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    )
    let extended = Set("^{}\\[~]|€")
    let characters = Array(text)
    let isGsm = characters.allSatisfy { basic.contains($0) || extended.contains($0) }
    let units =
      isGsm
      ? characters.reduce(0) { $0 + (extended.contains($1) ? 2 : 1) }
      : text.utf16.count
    let segments: Int
    if units == 0 {
      segments = 0
    } else if isGsm && units <= 160 {
      segments = 1
    } else if isGsm {
      segments = (units + 152) / 153
    } else if units <= 70 {
      segments = 1
    } else {
      segments = (units + 66) / 67
    }
    return (isGsm ? "gsm-7" : "unicode", segments)
  }

  private static func affectedContacts(
    status: CompanionProjectionStatus
  ) -> [IOSCompanionEffectiveContact] {
    IOSCompanionWorkflowEngine.shared.effectiveContacts(status: status).filter {
      $0.configuration?.enrollment == .enabled
        || $0.configuration?.enrollment == .paused
    }
  }

  private static func messageDraftPayload(
    _ draft: CompanionWorkflowMessageDraft
  ) -> [String: Any] {
    [
      "language": draft.language,
      "tone": draft.tone,
      "placeholderMode": draft.placeholderMode == "given-name"
        ? ["kind": "given-name", "requiredCount": 1]
        : ["kind": "generic", "requiredCount": 0],
      "text": draft.text,
      "requestedSegmentCap": draft.requestedSegmentCap,
    ]
  }

  private static func provenanceDraft(
    _ draft: CompanionWorkflowMessageDraft
  ) -> IOSGeminiProvenanceDraft {
    IOSGeminiProvenanceDraft(
      language: draft.language,
      tone: draft.tone,
      placeholderMode: draft.placeholderMode,
      requestedSegmentCap: draft.requestedSegmentCap,
      text: draft.text
    )
  }

  private static func draftWithProvenance(
    _ draft: CompanionWorkflowMessageDraft
  ) -> CompanionWorkflowMessageDraft {
    guard
      let provenance = IOSGeminiSuggestionGateway.shared.peekProvenance(
        for: provenanceDraft(draft)
      )
    else { return draft }
    return CompanionWorkflowMessageDraft(
      language: draft.language,
      tone: draft.tone,
      placeholderMode: draft.placeholderMode,
      text: draft.text,
      requestedSegmentCap: draft.requestedSegmentCap,
      provenance: CompanionWorkflowMessageProvenance(
        source: provenance.source,
        modelIdentifier: provenance.modelIdentifier,
        promptPolicyVersion: provenance.promptPolicyVersion,
        validatorVersion: provenance.validatorVersion
      )
    )
  }

  private static func revalidatedDraftForSave(
    _ draft: CompanionWorkflowMessageDraft
  ) -> CompanionWorkflowMessageDraft {
    guard let stored = draft.provenance, stored.source == "GEMINI" else {
      return draft
    }
    guard
      let live = IOSGeminiSuggestionGateway.shared.peekProvenance(
        for: provenanceDraft(draft)
      ), live.source == stored.source,
      live.modelIdentifier == stored.modelIdentifier,
      live.promptPolicyVersion == stored.promptPolicyVersion,
      live.validatorVersion == stored.validatorVersion
    else {
      return CompanionWorkflowMessageDraft(
        language: draft.language,
        tone: draft.tone,
        placeholderMode: draft.placeholderMode,
        text: draft.text,
        requestedSegmentCap: draft.requestedSegmentCap,
        provenance: CompanionWorkflowMessageProvenance(
          source: "USER", modelIdentifier: nil, promptPolicyVersion: nil,
          validatorVersion: IOSBirthdayMessageContentPolicy.validatorVersion
        )
      )
    }
    return draft
  }

  private static func windowLabel(_ policy: CompanionWorkflowPolicy) -> String {
    IOSNativePresentationFormatter.windowLabel(
      primaryStart: policy.primaryStart,
      primaryEnd: policy.primaryEnd,
      graceEnd: policy.graceEnd
    )
  }

  private static func recoverableDeletionOperationIndex(
    in workflow: CompanionWorkflowState
  ) -> Int? {
    workflow.privacyOperations.indices
      .filter {
        workflow.privacyOperations[$0].action == "delete-account"
          && workflow.privacyOperations[$0].phase == "remote-pending"
      }
      .max {
        workflow.privacyOperations[$0].updatedAt
          < workflow.privacyOperations[$1].updatedAt
      }
  }

  private static func recoverableDeletionOperation(
    in workflow: CompanionWorkflowState
  ) -> CompanionWorkflowPrivacyOperation? {
    recoverableDeletionOperationIndex(in: workflow).map {
      workflow.privacyOperations[$0]
    }
  }

  private static func privacyOperationPayload(
    _ operation: CompanionWorkflowPrivacyOperation,
    phase override: String? = nil,
    reason overrideReason: String? = nil
  ) -> [String: Any] {
    let phase = override ?? operation.phase
    let reason = overrideReason ?? operation.reason
    let projectionId = privacyProjectionId(
      nativeOperationId: operation.id,
      action: operation.action
    )
    switch phase {
    case "complete":
      return [
        "kind": "complete", "id": projectionId, "action": operation.action,
        "completedAt": dateString(operation.updatedAt), "externalSmsCopiesNotErased": true,
      ]
    case "remote-pending", "local-cleared", "provider-revoked":
      return [
        "kind": "remote-pending", "id": projectionId, "action": operation.action,
        "reason": reason ?? "coordination-unavailable",
        "updatedAt": dateString(operation.updatedAt),
      ]
    case "failed":
      return [
        "kind": "failed", "id": projectionId, "action": operation.action,
        "reason": reason ?? "coordination-unavailable",
        "updatedAt": dateString(operation.updatedAt),
      ]
    case "queued", "pausing", "remote-draining", "local-wiping", "verifying":
      return [
        "kind": phase, "id": projectionId, "action": operation.action,
        "updatedAt": dateString(operation.updatedAt),
      ]
    default:
      return [
        "kind": "failed", "id": projectionId, "action": operation.action,
        "reason": "internal-contract-invalid", "updatedAt": dateString(operation.updatedAt),
      ]
    }
  }

  static func accountDeletionReceiptPayload(
    _ receipt: IOSAccountDeletionReceipt
  ) -> [String: Any] {
    if receipt.remoteDeletionComplete, let completedAt = receipt.completedAt {
      return [
        "kind": "complete",
        "id": privacyProjectionId(
          nativeOperationId: receipt.operationId,
          action: "delete-account"
        ),
        "action": "delete-account",
        "completedAt": dateString(completedAt),
        "localDataErased": true,
        "remoteDeletionComplete": true,
        "externalSmsCopiesNotErased": true,
      ]
    }
    let projectionId = privacyProjectionId(
      nativeOperationId: receipt.operationId,
      action: "delete-account"
    )
    guard receipt.localDataErased else {
      return [
        "kind": "local-wiping",
        "id": projectionId,
        "action": "delete-account",
        "updatedAt": dateString(receipt.recordedAt),
      ]
    }
    return [
      "kind": "remote-draining",
      "id": projectionId,
      "action": "delete-account",
      "updatedAt": dateString(receipt.recordedAt),
      "localDataErased": true,
      "remoteDeletionComplete": false,
      "externalSmsCopiesNotErased": true,
    ]
  }

  static func accountDeletionRecoveryUnknownPayload(
    _ receipt: IOSAccountDeletionReceipt,
    sameAccountRetryAvailable: Bool
  ) -> [String: Any] {
    [
      "kind": "remote-unknown",
      "id": privacyProjectionId(
        nativeOperationId: receipt.operationId,
        action: "delete-account"
      ),
      "action": "delete-account",
      "reason": "coordination-unavailable",
      "updatedAt": dateString(receipt.recordedAt),
      "localDataErased": true,
      "remoteDeletionComplete": false,
      "sameAccountRetryAvailable": sameAccountRetryAvailable,
      "externalSmsCopiesNotErased": true,
    ]
  }

  private static func privacyProjectionId(
    nativeOperationId: String,
    action: String
  ) -> String {
    guard action == "delete-account" else { return nativeOperationId }
    return "privacy_"
      + canonicalHash([
        "birthday-ios-delete-operation-projection-v1", nativeOperationId,
      ])
  }

  private static func matchesPrivacyProjectionId(
    _ candidate: String,
    nativeOperationId: String,
    action: String
  ) -> Bool {
    constantTimeEqual(
      candidate,
      privacyProjectionId(nativeOperationId: nativeOperationId, action: action)
    )
  }

  private static func constantTimeEqual(_ lhs: String, _ rhs: String) -> Bool {
    let left = Array(lhs.utf8)
    let right = Array(rhs.utf8)
    guard left.count == right.count else { return false }
    var difference: UInt8 = 0
    for index in left.indices {
      difference |= left[index] ^ right[index]
    }
    return difference == 0
  }

  private static func deletionIdentityFailureReason(
    _ failure: IOSGoogleIdentityFailure
  ) -> String {
    switch failure {
    case .cancelled: return "account-cancelled"
    case .networkOffline: return "network-offline"
    case .accountMismatch: return "account-mismatch"
    case .firebaseUserDisabled: return "account-disabled"
    case .presenterUnavailable, .reconnectRequired:
      return "account-reconnect-required"
    case .appCheckUnavailable, .configurationUnavailable, .internalFailure:
      return "coordination-unavailable"
    }
  }

  private static func deletionClientFailureReason(
    _ failure: IOSAccountDeletionFailure
  ) -> String {
    switch failure {
    case .accountChanged, .recentAuthenticationRequired:
      return "account-reconnect-required"
    case .networkOffline:
      return "network-offline"
    case .configuration, .responseInvalid, .unavailable:
      return "coordination-unavailable"
    }
  }

  private static func contactResetFailureReason(
    _ failure: IOSContactDerivedResetFailure
  ) -> String {
    switch failure {
    case .accountChanged, .recentAuthenticationRequired:
      return "account-reconnect-required"
    case .networkOffline:
      return "network-offline"
    case .deletionInProgress:
      return "firebase-account-deleting"
    case .requestMismatch, .generationExhausted:
      return "internal-contract-invalid"
    case .configuration, .continuityUnavailable, .operationInProgress,
      .resetSuppressed, .responseInvalid, .unavailable:
      return "coordination-unavailable"
    }
  }

  private static func privacyConsequenceKeys(_ action: String) -> [String] {
    switch action {
    case "clear-activity":
      return [
        "privacy.consequence.activity-hidden",
        "privacy.consequence.safety-retained",
      ]
    case "clear-gemini-templates":
      return [
        "privacy.consequence.gemini-templates-removed",
        "privacy.consequence.reapproval-required",
      ]
    case "disconnect-contacts":
      return [
        "privacy.consequence.automation-paused",
        "privacy.consequence.google-working-data-removed",
        "privacy.consequence.reapproval-required",
        "privacy.consequence.android-reset-paused",
        "privacy.consequence.android-test-required",
        "privacy.consequence.external-sms",
      ]
    case "revoke-google-access":
      return [
        "privacy.consequence.automation-paused",
        "privacy.consequence.all-google-scopes-revoked",
        "privacy.consequence.google-working-data-removed",
        "privacy.consequence.reapproval-required",
        "privacy.consequence.android-reset-paused",
        "privacy.consequence.android-test-required",
        "privacy.consequence.external-sms",
      ]
    case "sign-out-retain":
      return [
        "privacy.consequence.automation-paused",
        "privacy.consequence.same-account-setup-retained",
        "privacy.consequence.external-sms",
      ]
    case "sign-out-wipe", "wipe-local-data":
      return [
        "privacy.consequence.automation-paused",
        "privacy.consequence.local-data-erased",
        "privacy.consequence.external-sms",
      ]
    case "delete-account":
      return [
        "privacy.consequence.automation-paused",
        "privacy.consequence.remote-deletion-drain-started",
        "privacy.consequence.local-data-erased-after-drain",
        "privacy.consequence.external-sms",
      ]
    default:
      return [
        "privacy.consequence.local-data",
        "privacy.consequence.external-sms",
      ]
    }
  }

  private static func activityPayload(
    id: String,
    kind: String,
    reason: String?,
    occurredAt: Date,
    recoveryRoute: String?
  ) -> [String: Any] {
    var result: [String: Any] = [
      "id": id, "kind": kind, "occurredAt": dateString(occurredAt),
    ]
    if let reason { result["reason"] = reason }
    if let recoveryRoute { result["recovery"] = ["route": recoveryRoute] }
    return result
  }

  private static func validPeopleQuery(_ query: [String: Any]) -> Bool {
    let allowed = Set(["all", "enabled", "ready", "needs-attention", "excluded"])
    let keys: Set<String> =
      query["cursor"] == nil && query["search"] == nil
      ? ["filter", "pageSize"]
      : query["cursor"] == nil
        ? ["filter", "pageSize", "search"]
        : query["search"] == nil
          ? ["cursor", "filter", "pageSize"]
          : ["cursor", "filter", "pageSize", "search"]
    guard Set(query.keys) == keys,
      let filter = query["filter"] as? String, allowed.contains(filter),
      let pageSize = strictInteger(query["pageSize"], range: 1...50),
      (1...50).contains(pageSize),
      (query["search"] as? String)?.count ?? 0 <= 256,
      pageOffset(query["cursor"] as? String) != nil
    else { return false }
    return true
  }

  private static func validActivityQuery(_ query: [String: Any]) -> Bool {
    let keys: Set<String> =
      query["cursor"] == nil
      ? ["pageSize"] : ["cursor", "pageSize"]
    guard Set(query.keys) == keys,
      let pageSize = strictInteger(query["pageSize"], range: 1...50),
      (1...50).contains(pageSize),
      pageOffset(query["cursor"] as? String) != nil
    else { return false }
    return true
  }

  private static func validContactIds(_ raw: [Any]) -> [String]? {
    guard !raw.isEmpty, raw.count <= maximumBatch else { return nil }
    let values = raw.compactMap { $0 as? String }
    guard values.count == raw.count, Set(values).count == values.count,
      values.allSatisfy(validOpaque)
    else { return nil }
    return values
  }

  private static func pageOffset(_ cursor: String?) -> Int? {
    guard let cursor else { return 0 }
    guard cursor.hasPrefix("page."), let value = Int(cursor.dropFirst(5)),
      value >= 0, value <= 1_000_000
    else { return nil }
    return value
  }

  private static func payloadRevision(
    _ payload: [String: Any],
    expected: String?
  ) -> String? {
    guard let value = payload["expectedRevision"] as? String,
      value == expected,
      value.range(of: "^(0|[1-9][0-9]{0,18})$", options: .regularExpression) != nil
    else { return nil }
    return value
  }

  private static func validOpaque(_ value: String) -> Bool {
    value.range(
      of: "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$",
      options: .regularExpression
    ) != nil
  }

  private static func minutes(_ value: String) -> Int? {
    guard
      value.range(of: "^(?:[01][0-9]|2[0-3]):[0-5][0-9]$", options: .regularExpression)
        != nil
    else { return nil }
    let parts = value.split(separator: ":").compactMap { Int($0) }
    guard parts.count == 2 else { return nil }
    return parts[0] * 60 + parts[1]
  }

  private static func strictInteger(
    _ raw: Any?,
    range: ClosedRange<Int>
  ) -> Int? {
    guard let number = raw as? NSNumber,
      CFGetTypeID(number) != CFBooleanGetTypeID(),
      number.doubleValue.isFinite,
      number.doubleValue.rounded() == number.doubleValue,
      number.doubleValue >= Double(Int.min),
      number.doubleValue <= Double(Int.max)
    else { return nil }
    let value = number.intValue
    return range.contains(value) ? value : nil
  }

  private static func timeComponents(_ value: String) -> (hour: Int, minute: Int)? {
    guard let value = minutes(value) else { return nil }
    return (value / 60, value % 60)
  }

  private static func previousRevision(of value: String) -> String {
    guard let revision = UInt64(value), revision > 0 else { return "0" }
    return String(revision - 1)
  }

  private static func localDate(_ date: Date, calendar: Calendar) -> String {
    let components = calendar.dateComponents([.year, .month, .day], from: date)
    return String(
      format: "%04d-%02d-%02d",
      components.year ?? 0, components.month ?? 0, components.day ?? 0
    )
  }

  private static func dateString(_ date: Date) -> String {
    isoFormatter.string(from: date)
  }

  private static func storeProblem(_ error: CompanionStoreError) -> [String: Any] {
    switch error {
    case .staleRevision:
      return ["kind": "temporarily-unavailable", "code": "stale-revision"]
    case .staleMaterial, .invalidReview:
      return ["kind": "conflict", "code": "stale-revision"]
    case .accountMismatch, .accountUnavailable:
      return temporarilyUnavailable("account-reconnect-required")
    case .androidManaged:
      return temporarilyUnavailable("active-sender-other-device")
    case .coexistenceUnverified:
      return temporarilyUnavailable("coordination-unavailable")
    case .resetFenceActive:
      return temporarilyUnavailable("reset-safety-blocked")
    case .resetFenceOverflow:
      return temporarilyUnavailable("reset-safety-overflow")
    default:
      return internalProblem(error.safeCode)
    }
  }

  private static func internalProblem(_ code: String) -> [String: Any] {
    ["kind": "internal", "supportCode": code]
  }

  private static func temporarilyUnavailable(_ code: String) -> [String: Any] {
    ["kind": "temporarily-unavailable", "code": code]
  }

  private static func unsupported(_ code: String) -> [String: Any] {
    ["kind": "unsupported", "code": code]
  }

  private static func validation(_ issues: [[String: Any]]) -> [String: Any] {
    ["kind": "validation", "issues": issues]
  }

  private static func actionRequired(_ issueIds: [String]) -> [String: Any] {
    ["kind": "action-required", "issueIds": issueIds]
  }
}
