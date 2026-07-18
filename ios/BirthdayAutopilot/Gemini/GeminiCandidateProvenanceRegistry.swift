import CryptoKit
import Foundation

struct IOSGeminiProvenanceDraft: Equatable {
  let language: String
  let tone: String
  let placeholderMode: String
  let requestedSegmentCap: Int
  let text: String
}

struct IOSGeminiCandidateProvenance: Equatable {
  let source: String
  let candidateDigest: String
  let language: String
  let tone: String
  let placeholderMode: String
  let requestedSegmentCap: Int
  let modelIdentifier: String
  let promptPolicyVersion: String
  let validatorVersion: String
}

/// Process-memory-only provenance for the at-most-three candidates visible in the current
/// authoring flow. Only a domain-separated exact-text digest is retained; provider text is not.
@MainActor
final class IOSGeminiCandidateProvenanceRegistry {
  private struct Entry {
    let provenance: IOSGeminiCandidateProvenance
    let expiresAtUptime: TimeInterval
  }

  private let uptime: () -> TimeInterval
  private let ttl: TimeInterval
  private var accountSessionKey: String?
  private var entries: [Entry] = []

  init(
    uptime: @escaping () -> TimeInterval = { ProcessInfo.processInfo.systemUptime },
    ttl: TimeInterval = 15 * 60
  ) {
    precondition(ttl >= 1 && ttl <= 60 * 60)
    self.uptime = uptime
    self.ttl = ttl
  }

  func replace(
    accountSessionKey: String,
    request: IOSGeminiSuggestionRequest,
    candidates: [String]
  ) {
    let now = uptime()
    guard !accountSessionKey.isEmpty, now >= 0, now <= Double.greatestFiniteMagnitude - ttl,
      (1...3).contains(candidates.count)
    else {
      clear()
      return
    }
    self.accountSessionKey = accountSessionKey
    entries = Array(candidates.prefix(3)).map { candidate in
      Entry(
        provenance: IOSGeminiCandidateProvenance(
          source: "GEMINI",
          candidateDigest: Self.candidateDigest(candidate),
          language: request.language,
          tone: request.tone,
          placeholderMode: request.placeholderMode,
          requestedSegmentCap: request.requestedSegmentCap,
          modelIdentifier: IOSGeminiSuggestionPolicy.modelIdentifier,
          promptPolicyVersion: IOSGeminiSuggestionPolicy.promptPolicyVersion,
          validatorVersion: IOSGeminiSuggestionPolicy.validatorVersion
        ),
        expiresAtUptime: now + ttl
      )
    }
  }

  func peek(
    accountSessionKey: String,
    draft: IOSGeminiProvenanceDraft
  ) -> IOSGeminiCandidateProvenance? {
    match(accountSessionKey: accountSessionKey, draft: draft, consume: false)
  }

  func consume(
    accountSessionKey: String,
    draft: IOSGeminiProvenanceDraft
  ) -> IOSGeminiCandidateProvenance? {
    match(accountSessionKey: accountSessionKey, draft: draft, consume: true)
  }

  func clear() {
    accountSessionKey = nil
    entries = []
  }

  private func match(
    accountSessionKey: String,
    draft: IOSGeminiProvenanceDraft,
    consume: Bool
  ) -> IOSGeminiCandidateProvenance? {
    let now = uptime()
    guard now >= 0, self.accountSessionKey == accountSessionKey else {
      clear()
      return nil
    }
    entries.removeAll { $0.expiresAtUptime <= now }
    guard !entries.isEmpty else {
      clear()
      return nil
    }
    let digest = Self.candidateDigest(draft.text)
    guard let index = entries.firstIndex(where: { entry in
      let value = entry.provenance
      return value.candidateDigest == digest &&
        value.language == draft.language && value.tone == draft.tone &&
        value.placeholderMode == draft.placeholderMode &&
        value.requestedSegmentCap == draft.requestedSegmentCap
    }) else {
      return nil
    }
    let result = entries[index].provenance
    if consume {
      entries.remove(at: index)
      if entries.isEmpty { clear() }
    }
    return result
  }

  private static func candidateDigest(_ value: String) -> String {
    digest(domain: "BirthdayAutopilot.GeminiCandidateExactText.v1", value: value)
  }

  private static func digest(domain: String, value: String) -> String {
    let input = Data("\(domain)\u{0}\(value)".utf8)
    return SHA256.hash(data: input).map { String(format: "%02x", $0) }.joined()
  }
}
