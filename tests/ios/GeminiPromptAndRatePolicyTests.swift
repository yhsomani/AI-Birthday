import Foundation

// The production Gemini policy references the full companion validator. This focused executable
// supplies only its compile-time surface because these tests exercise prompt/account/rate policy.
enum IOSBirthdayMessageContentPolicy {
  static let validatorVersion = "sms-template-validator-v2"

  static func issueCodes(text: String, declaredLanguage: String) -> [String] { [] }

  static func renderedBody(
    templateText: String,
    placeholderMode: String,
    givenName: String?,
    declaredLanguage: String
  ) -> String? { templateText }
}

@main
@MainActor
enum GeminiPromptAndRatePolicyTests {
  static func main() {
    guard IOSGeminiSuggestionPolicy.promptPolicyVersion == "birthday-greeting-prompt-v2",
      IOSGeminiSuggestionPolicy.systemInstruction.hasPrefix(
        "Create clearly positive, generic personal birthday greeting templates only."
      )
    else { fatalError("gemini-v2-prompt-contract-invalid") }

    let suiteName = "BirthdayAutopilot.GeminiRateTests.\(UUID().uuidString)"
    guard let defaults = UserDefaults(suiteName: suiteName) else {
      fatalError("gemini-rate-defaults-unavailable")
    }
    defaults.removePersistentDomain(forName: suiteName)
    defer { defaults.removePersistentDomain(forName: suiteName) }

    let rawUIDA = "firebase-user-a"
    let rawGenerationA = "account-generation-a"
    guard let sessionA = IOSGeminiAccountScope.accountSessionKey(
      firebaseUID: rawUIDA,
      accountGeneration: rawGenerationA
    ), let sessionB = IOSGeminiAccountScope.accountSessionKey(
      firebaseUID: "firebase-user-b",
      accountGeneration: "account-generation-b"
    ), let rotatedSessionA = IOSGeminiAccountScope.accountSessionKey(
      firebaseUID: rawUIDA,
      accountGeneration: "account-generation-a-rotated"
    ), sessionA.count == 64, sessionB.count == 64,
      sessionA != sessionB, sessionA != rotatedSessionA
    else { fatalError("gemini-account-session-scope-invalid") }

    let guardPolicy = IOSGeminiUXRateGuard(
      defaults: defaults,
      dailyLimit: 2,
      cooldownSeconds: 5
    )
    guard guardPolicy.tryAcquire(accountSessionKey: sessionA, wallTime: 0, uptime: 10),
      !guardPolicy.tryAcquire(accountSessionKey: sessionA, wallTime: 1, uptime: 9),
      !guardPolicy.tryAcquire(accountSessionKey: sessionA, wallTime: 1, uptime: 11),
      guardPolicy.tryAcquire(accountSessionKey: sessionB, wallTime: 1, uptime: 11),
      guardPolicy.tryAcquire(accountSessionKey: sessionA, wallTime: 6, uptime: 16),
      !guardPolicy.tryAcquire(accountSessionKey: sessionA, wallTime: 7, uptime: 22),
      guardPolicy.tryAcquire(accountSessionKey: sessionA, wallTime: 86_400, uptime: 23),
      !guardPolicy.tryAcquire(accountSessionKey: sessionA, wallTime: 0, uptime: 24)
    else { fatalError("gemini-per-account-rate-scope-invalid") }

    let bounded = IOSGeminiUXRateGuard(
      defaults: defaults,
      dailyLimit: 100,
      cooldownSeconds: 0
    )
    for index in 0..<(IOSGeminiUXRateGuard.maximumRetainedScopes + 2) {
      guard let session = IOSGeminiAccountScope.accountSessionKey(
        firebaseUID: "bounded-user-\(index)",
        accountGeneration: "bounded-generation-\(index)"
      ), bounded.tryAcquire(
        accountSessionKey: session,
        wallTime: TimeInterval(index) * 86_400,
        uptime: TimeInterval(index + 100)
      ) else { fatalError("gemini-bounded-rate-insert-failed") }
    }
    guard bounded.storedScopeCountForTesting() == IOSGeminiUXRateGuard.maximumRetainedScopes
    else { fatalError("gemini-rate-state-unbounded") }

    let persistedDescription = defaults.dictionaryRepresentation().description
    guard !persistedDescription.contains(rawUIDA),
      !persistedDescription.contains(rawGenerationA)
    else { fatalError("gemini-rate-state-stored-raw-account") }

    defaults.set(1, forKey: "birthday.gemini.ux-guard.utc-day.v1")
    defaults.set(1, forKey: "birthday.gemini.ux-guard.attempts.v1")
    guard bounded.clearAll(), bounded.storedScopeCountForTesting() == 0,
      defaults.object(forKey: "birthday.gemini.ux-guard.utc-day.v1") == nil,
      defaults.object(forKey: "birthday.gemini.ux-guard.attempts.v1") == nil
    else { fatalError("gemini-rate-state-clear-failed") }
  }
}
