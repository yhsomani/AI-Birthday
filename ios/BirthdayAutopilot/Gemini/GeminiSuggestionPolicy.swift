import CoreFoundation
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
    guard !withoutSupportedPlaceholder.contains("{"), !withoutSupportedPlaceholder.contains("}"),
      languageMatches(withoutSupportedPlaceholder, language: request.language)
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
