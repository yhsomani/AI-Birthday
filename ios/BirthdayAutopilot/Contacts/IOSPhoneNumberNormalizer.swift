import CryptoKit
import Foundation
import libPhoneNumberSwiftCore
import libPhoneNumberSwiftShortNumber

enum IOSPhoneNumberKind: String, Equatable {
  case fixedLine
  case fixedLineOrMobile
  case mobile
  case pager
  case personal
  case premiumRate
  case tollFree
  case unknown
  case voip
}

enum IOSPhoneNormalizationIssue: String, Equatable {
  case ambiguous
  case emergencyNumber
  case extensionNotSupported
  case malformed
  case notSMSCapable
  case notValid
  case premiumRate
  case regionInvalid
  case regionRequired
  case shortCode

  var publicReasonCode: String {
    switch self {
    case .ambiguous, .regionInvalid, .regionRequired:
      return "phone-ambiguous-region"
    case .emergencyNumber, .extensionNotSupported, .malformed,
      .notSMSCapable, .notValid, .premiumRate, .shortCode:
      return "phone-invalid"
    }
  }
}

struct IOSNormalizedPhoneNumber: Equatable, CustomStringConvertible {
  let e164: String
  let kind: IOSPhoneNumberKind
  let maskedDisplay: String

  var description: String { "IOSNormalizedPhoneNumber(<redacted>)" }
}

struct IOSRejectedPhoneNumber: Equatable, CustomStringConvertible {
  let issue: IOSPhoneNormalizationIssue
  let maskedDisplay: String

  var description: String {
    "IOSRejectedPhoneNumber(issue=\(issue.rawValue), value=<redacted>)"
  }
}

enum IOSPhoneNormalizationResult: Equatable, CustomStringConvertible {
  case accepted(IOSNormalizedPhoneNumber)
  case rejected(IOSRejectedPhoneNumber)

  var description: String {
    switch self {
    case .accepted: return "IOSPhoneNormalizationResult.accepted(<redacted>)"
    case .rejected(let value): return value.description
    }
  }
}

struct IOSPhoneSafetyEvidence: Equatable {
  let extensionPresent: Bool
  let emergency: Bool
  let shortCode: Bool
  let premiumRate: Bool
  let possible: Bool
  let valid: Bool
  let kind: IOSPhoneNumberKind
}

enum IOSPhoneSafetyPolicy {
  private static let smsCapableKinds: Set<IOSPhoneNumberKind> = [
    .fixedLineOrMobile, .mobile,
  ]

  static func rejection(for evidence: IOSPhoneSafetyEvidence) -> IOSPhoneNormalizationIssue? {
    if evidence.extensionPresent { return .extensionNotSupported }
    if evidence.emergency { return .emergencyNumber }
    if evidence.shortCode { return .shortCode }
    if evidence.premiumRate || evidence.kind == .premiumRate { return .premiumRate }
    if !evidence.possible || !evidence.valid { return .notValid }
    if !smsCapableKinds.contains(evidence.kind) { return .notSMSCapable }
    return nil
  }
}

/// Native-only BA-04 normalization backed by the exact reviewed
/// libPhoneNumber-iOS 1.7.3 core and short-number metadata graph.
///
/// Parsing or an E.164 rendering is never treated as SMS capability. A number
/// becomes selectable only after core validity/type checks plus emergency,
/// short-code, short-number cost, and extension checks have all passed.
final class IOSPhoneNumberNormalizer {
  static let shared = IOSPhoneNumberNormalizer()
  static let metadataRelease = "libPhoneNumber-iOS-1.7.3"

  private static let maximumRawCharacters = 200
  private static let maximumRawBytes = 800
  private static let explicitExtension = try! NSRegularExpression(
    pattern: "(?:ext(?:ension)?\\.?|x|#)\\s*=*\\s*[0-9]+\\s*$",
    options: [.caseInsensitive]
  )
  private static let regionPattern = try! NSRegularExpression(
    pattern: "^[A-Z]{2}$"
  )
  private static let e164Pattern = try! NSRegularExpression(
    pattern: "^\\+[1-9][0-9]{1,14}$"
  )

  private final class CachedResult: NSObject {
    let value: IOSPhoneNormalizationResult

    init(_ value: IOSPhoneNormalizationResult) {
      self.value = value
    }
  }

  private let phoneUtility: PhoneNumberUtility
  private let shortUtility: ShortNumberUtility
  private let supportedRegions: Set<String>
  private let supportedShortRegions: Set<String>
  private let cache = NSCache<NSString, CachedResult>()
  private let metadataLock = NSLock()

  init(
    phoneUtility: PhoneNumberUtility = .shared,
    shortUtility: ShortNumberUtility = .shared
  ) {
    self.phoneUtility = phoneUtility
    self.shortUtility = shortUtility
    supportedRegions = Set(phoneUtility.supportedRegions.map { $0.uppercased() })
    supportedShortRegions = Set(shortUtility.supportedRegions.map { $0.uppercased() })
    cache.countLimit = 20_000
  }

  func normalize(
    _ rawValue: String,
    homeRegion rawRegion: String?
  ) -> IOSPhoneNormalizationResult {
    guard rawValue.utf8.count <= Self.maximumRawBytes,
      rawValue.count <= Self.maximumRawCharacters
    else {
      return rejected(.malformed, masked: "••••")
    }
    let regionResolution = normalizedRegion(rawRegion)
    let cacheKey = digestKey(rawValue: rawValue, regionResolution: regionResolution)
    if let cached = cache.object(forKey: cacheKey as NSString) {
      return cached.value
    }

    metadataLock.lock()
    defer { metadataLock.unlock() }
    if let cached = cache.object(forKey: cacheKey as NSString) {
      return cached.value
    }
    let result = analyze(rawValue, regionResolution: regionResolution)
    cache.setObject(CachedResult(result), forKey: cacheKey as NSString)
    return result
  }

  static func currentDeviceRegion() -> String? {
    let value = (Locale.autoupdatingCurrent as NSLocale)
      .object(forKey: .countryCode) as? String
    let normalized = value?.trimmingCharacters(in: .whitespacesAndNewlines)
      .uppercased()
    guard let normalized, matches(normalized, expression: regionPattern) else {
      return nil
    }
    return normalized
  }

  private enum RegionResolution: Equatable {
    case absent
    case invalid
    case ready(String)

    var cacheValue: String {
      switch self {
      case .absent: return "absent"
      case .invalid: return "invalid"
      case .ready(let value): return value
      }
    }
  }

  private func normalizedRegion(_ rawValue: String?) -> RegionResolution {
    guard let rawValue else { return .absent }
    let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
    guard Self.matches(value, expression: Self.regionPattern),
      supportedRegions.contains(value)
    else {
      return .invalid
    }
    return .ready(value)
  }

  private func analyze(
    _ rawValue: String,
    regionResolution: RegionResolution
  ) -> IOSPhoneNormalizationResult {
    let trimmed = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
    let masked = mask(rawValue)
    guard !trimmed.isEmpty,
      trimmed.count <= Self.maximumRawCharacters,
      trimmed.utf8.count <= Self.maximumRawBytes
    else {
      return rejected(.malformed, masked: masked)
    }

    // Extensions are an absolute BA-04 blocker. Reject the explicit syntax
    // before region resolution so an extension-only or regional value cannot
    // be mislabeled as merely needing a home-region hint.
    if Self.containsMatch(trimmed, expression: Self.explicitExtension) {
      return rejected(.extensionNotSupported, masked: masked)
    }

    let international = trimmed.hasPrefix("+")
    let region: String?
    switch regionResolution {
    case .ready(let value):
      region = value
    case .absent where !international:
      return rejected(.regionRequired, masked: masked)
    case .invalid where !international:
      return rejected(.regionInvalid, masked: masked)
    case .absent, .invalid:
      // An explicit international number is self-describing. A stale or
      // unavailable device-region hint must not change its interpretation.
      region = nil
    }

    let number: PhoneNumber
    do {
      number = try phoneUtility.parse(trimmed, defaultRegion: region)
    } catch {
      return rejected(.malformed, masked: masked)
    }

    let parsedRegion = phoneUtility.regionCode(for: number).flatMap { value -> String? in
      let normalized = value.uppercased()
      return supportedRegions.contains(normalized) ? normalized : nil
    }
    let candidateRegions = candidateRegions(
      number: number,
      suppliedRegion: region,
      parsedRegion: parsedRegion
    )
    let national = phoneUtility.nationalSignificantNumber(for: number)
    let emergency = candidateRegions.contains { candidate in
      shortUtility.isEmergencyNumber(trimmed, forRegion: candidate)
        || shortUtility.connectsToEmergencyNumber(trimmed, forRegion: candidate)
        || shortUtility.isEmergencyNumber(national, forRegion: candidate)
        || shortUtility.connectsToEmergencyNumber(national, forRegion: candidate)
    }
    let shortCode = shortUtility.isPossibleShortNumber(number)
      || shortUtility.isValidShortNumber(number)
      || candidateRegions.contains { candidate in
        shortUtility.isPossibleShortNumber(number, forRegion: candidate)
          || shortUtility.isValidShortNumber(number, forRegion: candidate)
      }
    let premiumShortCode = candidateRegions.contains { candidate in
      shortUtility.expectedCost(of: number, forRegion: candidate) == .premiumRate
    }

    if region == nil, parsedRegion == nil, candidateRegions.count > 1,
      !emergency, !shortCode
    {
      return rejected(.ambiguous, masked: masked)
    }

    let kind = domainKind(phoneUtility.type(of: number))
    let extensionPresent =
      !(number.extension?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true)
    let evidence = IOSPhoneSafetyEvidence(
      extensionPresent: extensionPresent,
      emergency: emergency,
      shortCode: shortCode,
      premiumRate: premiumShortCode,
      possible: phoneUtility.isPossibleNumber(number),
      valid: phoneUtility.isValidNumber(number),
      kind: kind
    )
    if let issue = IOSPhoneSafetyPolicy.rejection(for: evidence) {
      return rejected(issue, masked: masked)
    }

    let e164: String
    do {
      e164 = try phoneUtility.format(number, as: .e164)
    } catch {
      return rejected(.malformed, masked: masked)
    }
    guard Self.matches(e164, expression: Self.e164Pattern) else {
      return rejected(.malformed, masked: masked)
    }
    return .accepted(
      IOSNormalizedPhoneNumber(
        e164: e164,
        kind: kind,
        maskedDisplay: mask(e164)
      )
    )
  }

  private func candidateRegions(
    number: PhoneNumber,
    suppliedRegion: String?,
    parsedRegion: String?
  ) -> [String] {
    if let suppliedRegion { return [suppliedRegion] }
    if let parsedRegion { return [parsedRegion] }
    return phoneUtility.regionCodes(forCountryCode: number.countryCode.intValue)
      .map { $0.uppercased() }
      .filter { supportedRegions.contains($0) && supportedShortRegions.contains($0) }
      .uniquedAndSorted()
  }

  private func domainKind(_ type: PhoneNumberType) -> IOSPhoneNumberKind {
    switch type {
    case .mobile: return .mobile
    case .fixedLineOrMobile: return .fixedLineOrMobile
    case .fixedLine: return .fixedLine
    case .premiumRate: return .premiumRate
    case .tollFree: return .tollFree
    case .voip: return .voip
    case .pager: return .pager
    case .personalNumber: return .personal
    case .sharedCost, .uan, .voicemail, .unknown: return .unknown
    }
  }

  private func rejected(
    _ issue: IOSPhoneNormalizationIssue,
    masked: String
  ) -> IOSPhoneNormalizationResult {
    .rejected(IOSRejectedPhoneNumber(issue: issue, maskedDisplay: masked))
  }

  private func mask(_ rawValue: String) -> String {
    let digits = phoneUtility.digitsOnly(rawValue).filter { $0.isASCII && $0.isNumber }
    guard !digits.isEmpty else { return "••••" }
    return "•••• \(digits.suffix(min(4, digits.count)))"
  }

  private func digestKey(
    rawValue: String,
    regionResolution: RegionResolution
  ) -> String {
    let input = [
      Self.metadataRelease,
      regionResolution.cacheValue,
      rawValue,
    ].joined(separator: "\u{0}")
    return SHA256.hash(data: Data(input.utf8)).map { String(format: "%02x", $0) }.joined()
  }

  private static func matches(
    _ value: String,
    expression: NSRegularExpression
  ) -> Bool {
    let range = NSRange(value.startIndex..<value.endIndex, in: value)
    return expression.firstMatch(in: value, range: range)?.range == range
  }

  private static func containsMatch(
    _ value: String,
    expression: NSRegularExpression
  ) -> Bool {
    let range = NSRange(value.startIndex..<value.endIndex, in: value)
    return expression.firstMatch(in: value, range: range) != nil
  }
}

private extension Sequence where Element == String {
  func uniquedAndSorted() -> [String] {
    Array(Set(self)).sorted()
  }
}
