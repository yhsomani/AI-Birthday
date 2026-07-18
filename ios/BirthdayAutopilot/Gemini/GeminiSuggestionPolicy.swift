import CoreFoundation
import CryptoKit
import Foundation

struct IOSGeminiSuggestionRequest: Equatable {
  let language: String
  let tone: String
  let placeholderMode: String
  let requestedSegmentCap: Int
}

/// Pure, deterministic policy shared by the iOS Firebase gateway and its command-line tests.
/// It contains no provider object and never receives contact or account data.
enum IOSGeminiSuggestionPolicy {
  static let modelName = "gemini-3.5-flash"
  static let modelLocation = "global"
  static let modelIdentifier = "vertex-ai/global/gemini-3.5-flash"
  static let promptPolicyVersion = "birthday-greeting-prompt-v2"
  static let validatorVersion = IOSBirthdayMessageContentPolicy.validatorVersion

  static let systemInstruction =
    "Create clearly positive, generic personal birthday greeting templates only. Never include or request a " +
    "person's name, phone number, birthday, age, gender, relationship, religion, health, " +
    "private history, contact data, secret, URL, hashtag, marketing, promotion, invented " +
    "memory, sensitive attribute, hateful, sexual, self-harm, violent, or deceptive content. " +
    "Follow the requested language, tone, placeholder mode, and segment cap. Return only the " +
    "structured candidates required by the response schema."

  static func parseRequest(_ value: [String: Any]) -> IOSGeminiSuggestionRequest? {
    guard Set(value.keys) == Set([
      "language", "tone", "placeholderMode", "requestedSegmentCap",
    ]),
      let language = strictString(value["language"]), ["en", "hi"].contains(language),
      let tone = strictString(value["tone"]), ["warm", "simple", "cheerful"].contains(tone),
      let placeholder = value["placeholderMode"] as? [String: Any],
      Set(placeholder.keys) == Set(["kind", "requiredCount"]),
      let mode = strictString(placeholder["kind"]), ["given-name", "generic"].contains(mode),
      let requiredCount = strictInt(placeholder["requiredCount"]),
      (mode == "given-name" && requiredCount == 1) ||
        (mode == "generic" && requiredCount == 0),
      let segmentCap = strictInt(value["requestedSegmentCap"]), (1...2).contains(segmentCap)
    else {
      return nil
    }
    return IOSGeminiSuggestionRequest(
      language: language,
      tone: tone,
      placeholderMode: mode,
      requestedSegmentCap: segmentCap
    )
  }

  static func prompt(_ request: IOSGeminiSuggestionRequest) -> String {
    let language = request.language == "hi" ? "Hindi (hi)" : "English (en)"
    let tone: String
    switch request.tone {
    case "simple": tone = "simple"
    case "cheerful": tone = "cheerful"
    default: tone = "warm"
    }
    let placeholder = request.placeholderMode == "given-name"
      ? "exactly one literal {firstName} placeholder"
      : "no placeholder and no person's name"
    return "policyVersion=\(promptPolicyVersion)\n" +
      "language=\(language)\n" +
      "tone=\(tone)\n" +
      "placeholderMode=\(placeholder)\n" +
      "requestedSegmentCap=\(request.requestedSegmentCap)\n" +
      "Generate 1 to 3 distinct generic birthday greeting templates."
  }

  static func validatedCandidates(
    _ raw: String,
    request: IOSGeminiSuggestionRequest
  ) -> [String] {
    guard let data = raw.data(using: .utf8), (2...16_384).contains(data.count),
      let decoded = try? JSONSerialization.jsonObject(with: data),
      let root = decoded as? [String: Any], Set(root.keys) == Set(["candidates"]),
      let values = root["candidates"] as? [Any], (1...3).contains(values.count)
    else {
      return []
    }
    var output: [String] = []
    var seen = Set<String>()
    for rawCandidate in values {
      guard let candidate = rawCandidate as? [String: Any],
        Set(candidate.keys) == Set(["text", "language"])
      else {
        return []
      }
      guard strictString(candidate["language"]) == request.language,
        let rawText = strictString(candidate["text"]), rawText.utf16.count <= 2_000
      else {
        continue
      }
      let text = rawText.precomposedStringWithCanonicalMapping
        .trimmingCharacters(in: .whitespacesAndNewlines)
      guard safeCandidate(text, request: request) else { continue }
      let key = request.language == "en" ? text.lowercased(with: Locale(identifier: "en_US_POSIX")) : text
      if seen.insert(key).inserted { output.append(text) }
    }
    return Array(output.prefix(3))
  }

  private static func safeCandidate(
    _ text: String,
    request: IOSGeminiSuggestionRequest
  ) -> Bool {
    guard !text.isEmpty, text.count <= 1_000,
      IOSBirthdayMessageContentPolicy.issueCodes(
        text: text,
        declaredLanguage: request.language
      ).isEmpty
    else {
      return false
    }
    let placeholderCount = text.components(separatedBy: "{firstName}").count - 1
    if request.placeholderMode == "given-name" {
      guard placeholderCount == 1 else { return false }
    } else {
      guard placeholderCount == 0 else { return false }
    }
    let withoutSupportedPlaceholder = text.replacingOccurrences(of: "{firstName}", with: "")
    guard !withoutSupportedPlaceholder.contains("{"), !withoutSupportedPlaceholder.contains("}")
    else {
      return false
    }
    let representativeName = request.language == "hi" ? "मित्र" : "Friend"
    guard let rendered = IOSBirthdayMessageContentPolicy.renderedBody(
      templateText: text,
      placeholderMode: request.placeholderMode,
      givenName: request.placeholderMode == "given-name" ? representativeName : nil,
      declaredLanguage: request.language
    ) else { return false }
    return segmentCount(rendered) <= request.requestedSegmentCap
  }

  private static func segmentCount(_ value: String) -> Int {
    let basic = Set(gsmBasic.unicodeScalars)
    let extensionSet = Set(gsmExtension.unicodeScalars)
    let scalars = Array(value.unicodeScalars)
    let isGSM = scalars.allSatisfy { basic.contains($0) || extensionSet.contains($0) }
    let units = isGSM
      ? scalars.reduce(0) { $0 + (extensionSet.contains($1) ? 2 : 1) }
      : value.utf16.count
    guard units > 0 else { return 0 }
    if isGSM { return units <= 160 ? 1 : (units + 152) / 153 }
    return units <= 70 ? 1 : (units + 66) / 67
  }

  private static func strictString(_ value: Any?) -> String? {
    guard let value = value as? String, !value.isEmpty else { return nil }
    return value
  }

  private static func strictInt(_ value: Any?) -> Int? {
    guard let number = value as? NSNumber,
      CFGetTypeID(number) != CFBooleanGetTypeID(),
      number.doubleValue.isFinite,
      number.doubleValue.rounded(.towardZero) == number.doubleValue
    else {
      return nil
    }
    let signed = number.int64Value
    guard signed >= Int64(Int.min), signed <= Int64(Int.max) else { return nil }
    return Int(signed)
  }

  private static let gsmBasic =
    "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ" +
    "ÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡" +
    "ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
  private static let gsmExtension = "^{}\\[~]|€"
}

/// Purpose-separated native account keys. Raw provider identifiers are consumed only here and the
/// resulting fixed-length digest remains inside the native Gemini boundary.
enum IOSGeminiAccountScope {
  static func accountSessionKey(firebaseUID: String, accountGeneration: String) -> String? {
    guard !firebaseUID.isEmpty, firebaseUID.count <= maximumInputCharacters,
      !accountGeneration.isEmpty, accountGeneration.count <= maximumInputCharacters
    else { return nil }
    return digest(
      domain: accountSessionDomain,
      value: "\(firebaseUID)\u{0}\(accountGeneration)"
    )
  }

  static func rateScopeKey(accountSessionKey: String) -> String? {
    guard accountSessionKey.count == 64,
      accountSessionKey.unicodeScalars.allSatisfy({ scalar in
        (48...57).contains(scalar.value) || (97...102).contains(scalar.value)
      })
    else { return nil }
    return digest(domain: rateScopeDomain, value: accountSessionKey)
  }

  private static func digest(domain: String, value: String) -> String {
    SHA256.hash(data: Data("\(domain)\u{0}\(value)".utf8))
      .map { String(format: "%02x", $0) }
      .joined()
  }

  private static let accountSessionDomain = "BirthdayAutopilot.GeminiAccountSession.v1"
  private static let rateScopeDomain = "BirthdayAutopilot.GeminiRateScope.v1"
  private static let maximumInputCharacters = 512
}

/// Bounded, content-free, per-account UX/cost state. Provider quota and App Check remain the abuse
/// boundary; this local guard deliberately contains no UID, subject, prompt, response, or contact.
@MainActor
final class IOSGeminiUXRateGuard {
  private struct StoredRecord: Codable, Equatable {
    let scopeKey: String
    let epochDay: Int64
    let attempts: Int
  }

  private struct StoredSnapshot: Codable {
    let schemaVersion: Int
    let records: [StoredRecord]
  }

  private let defaults: UserDefaults
  private let dailyLimit: Int
  private let cooldownSeconds: TimeInterval
  private var lastAcceptedUptimeByScope: [String: TimeInterval] = [:]

  init(
    defaults: UserDefaults = .standard,
    dailyLimit: Int = 10,
    cooldownSeconds: TimeInterval = 5
  ) {
    precondition((1...100).contains(dailyLimit))
    precondition(cooldownSeconds >= 0 && cooldownSeconds <= 60)
    self.defaults = defaults
    self.dailyLimit = dailyLimit
    self.cooldownSeconds = cooldownSeconds
  }

  func tryAcquire(
    accountSessionKey: String,
    wallTime: TimeInterval,
    uptime: TimeInterval
  ) -> Bool {
    guard wallTime.isFinite, wallTime >= 0, uptime.isFinite, uptime >= 0,
      let scopeKey = IOSGeminiAccountScope.rateScopeKey(
        accountSessionKey: accountSessionKey
      )
    else { return false }
    let dayValue = floor(wallTime / 86_400)
    guard dayValue <= Double(Int64.max) else { return false }
    let epochDay = Int64(dayValue)
    if let previous = lastAcceptedUptimeByScope[scopeKey] {
      if uptime < previous { return false }
      if uptime - previous < cooldownSeconds { return false }
    }
    guard let stored = load() else { return false }
    let current = stored.first { $0.scopeKey == scopeKey }
    // A wall-clock rollback must not reset this account's already-observed daily budget.
    if let current, current.epochDay > epochDay { return false }
    let attempts = current?.epochDay == epochDay ? current?.attempts ?? 0 : 0
    guard attempts < dailyLimit else { return false }

    var retained = stored.filter {
      $0.scopeKey != scopeKey && $0.epochDay <= epochDay
        && epochDay - $0.epochDay <= Self.scopeRetentionDays
    }.sorted {
      $0.epochDay == $1.epochDay ? $0.scopeKey < $1.scopeKey : $0.epochDay > $1.epochDay
    }
    retained = Array(retained.prefix(Self.maximumRetainedScopes - 1))
    retained.append(StoredRecord(
      scopeKey: scopeKey,
      epochDay: epochDay,
      attempts: attempts + 1
    ))
    guard persist(retained) else { return false }

    lastAcceptedUptimeByScope[scopeKey] = uptime
    let retainedScopes = Set(retained.map(\.scopeKey))
    lastAcceptedUptimeByScope = lastAcceptedUptimeByScope.filter {
      retainedScopes.contains($0.key)
    }
    return true
  }

  @discardableResult
  func clearAll() -> Bool {
    lastAcceptedUptimeByScope = [:]
    defaults.removeObject(forKey: Self.stateKey)
    defaults.removeObject(forKey: Self.legacyDayKey)
    defaults.removeObject(forKey: Self.legacyAttemptsKey)
    return defaults.object(forKey: Self.stateKey) == nil
      && defaults.object(forKey: Self.legacyDayKey) == nil
      && defaults.object(forKey: Self.legacyAttemptsKey) == nil
  }

  func storedScopeCountForTesting() -> Int? { load()?.count }

  private func load() -> [StoredRecord]? {
    guard let object = defaults.object(forKey: Self.stateKey) else { return [] }
    guard let data = object as? Data,
      let snapshot = try? PropertyListDecoder().decode(StoredSnapshot.self, from: data),
      snapshot.schemaVersion == 1,
      snapshot.records.count <= Self.maximumRetainedScopes
    else { return nil }
    var scopes = Set<String>()
    for record in snapshot.records {
      guard IOSGeminiAccountScope.rateScopeKey(accountSessionKey: record.scopeKey) != nil,
        scopes.insert(record.scopeKey).inserted,
        record.epochDay >= 0, (0...100).contains(record.attempts)
      else { return nil }
    }
    return snapshot.records
  }

  private func persist(_ records: [StoredRecord]) -> Bool {
    guard records.count <= Self.maximumRetainedScopes else { return false }
    let snapshot = StoredSnapshot(schemaVersion: 1, records: records)
    let encoder = PropertyListEncoder()
    encoder.outputFormat = .binary
    guard let data = try? encoder.encode(snapshot) else { return false }
    defaults.set(data, forKey: Self.stateKey)
    return defaults.data(forKey: Self.stateKey) == data
  }

  static let maximumRetainedScopes = 8
  private static let scopeRetentionDays: Int64 = 32
  private static let stateKey = "birthday.gemini.ux-guard.scoped-state.v2"
  private static let legacyDayKey = "birthday.gemini.ux-guard.utc-day.v1"
  private static let legacyAttemptsKey = "birthday.gemini.ux-guard.attempts.v1"
}
