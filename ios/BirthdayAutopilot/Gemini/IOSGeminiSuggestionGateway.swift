import FirebaseAILogic
import FirebaseAppCheck
import FirebaseAuth
import FirebaseCore
import Foundation
import UIKit

/// Firebase AI Logic adapter for foreground template authoring. This object never receives a
/// contact, phone, birthday, prior message, account identifier, or provider token.
@MainActor
final class IOSGeminiSuggestionGateway {
  static let shared = IOSGeminiSuggestionGateway()

  private let identity: IOSGoogleIdentityCoordinator
  private let operationalGate: IOSGeminiOperationalGate
  private let provenanceRegistry: IOSGeminiCandidateProvenanceRegistry
  private let rateGuard: IOSGeminiUXRateGuard
  private var requestInFlight = false

  init(
    identity: IOSGoogleIdentityCoordinator = .shared,
    defaults: UserDefaults = .standard,
    operationalGate: IOSGeminiOperationalGate = .shared,
    provenanceRegistry: IOSGeminiCandidateProvenanceRegistry =
      IOSGeminiCandidateProvenanceRegistry()
  ) {
    self.identity = identity
    self.operationalGate = operationalGate
    self.provenanceRegistry = provenanceRegistry
    rateGuard = IOSGeminiUXRateGuard(defaults: defaults)
  }

  func configureOperationalGateAfterFirebaseLaunch() {
    operationalGate.configureAfterFirebaseLaunch()
  }

  func refreshOperationalGateInBackground() {
    operationalGate.refreshInBackground()
  }

  func generate(request payload: [String: Any]) async -> [String: Any] {
    guard let request = IOSGeminiSuggestionPolicy.parseRequest(payload) else {
      provenanceRegistry.clear()
      return ["kind": "failed", "reason": "internal-contract-invalid"]
    }
    operationalGate.refreshInBackground()
    guard operationalGate.foregroundSuggestionsEnabled() else {
      provenanceRegistry.clear()
      // Reliable built-in templates remain visible in the editor. Keep the
      // provider path truthful and disabled instead of presenting them as AI.
      return fallback("policy-suspended")
    }
    guard !requestInFlight else { return fallback("policy-suspended") }
    requestInFlight = true
    defer { requestInFlight = false }

    guard UIApplication.shared.applicationState == .active,
      let binding = identity.exactSessionBinding(),
      let firebaseUser = Auth.auth().currentUser, !firebaseUser.isAnonymous,
      firebaseUser.providerData.filter({ $0.providerID == "google.com" }).count == 1
    else {
      provenanceRegistry.clear()
      return fallback("coordination-unavailable")
    }
    guard let accountSessionKey = Self.accountSessionKey(binding) else {
      provenanceRegistry.clear()
      return fallback("coordination-unavailable")
    }
    provenanceRegistry.clear()
    let appCheckReady = await appCheckReadyWithinTimeout()
    guard appCheckReady else { return fallback("coordination-unavailable") }
    guard rateGuard.tryAcquire(
      accountSessionKey: accountSessionKey,
      wallTime: Date().timeIntervalSince1970,
      uptime: ProcessInfo.processInfo.systemUptime
    ) else { return fallback("policy-suspended") }

    do {
      let raw = try await generateProviderText(request: request)
      let candidates = IOSGeminiSuggestionPolicy.validatedCandidates(raw, request: request)
      guard !candidates.isEmpty else { return fallback("coordination-unavailable") }
      guard let currentBinding = identity.exactSessionBinding(),
        Self.accountSessionKey(currentBinding) == accountSessionKey
      else {
        provenanceRegistry.clear()
        return fallback("coordination-unavailable")
      }
      provenanceRegistry.replace(
        accountSessionKey: accountSessionKey,
        request: request,
        candidates: candidates
      )
      return ["kind": "candidates", "candidates": candidates]
    } catch {
      return fallback(Self.isOffline(error) ? "network-offline" : "coordination-unavailable")
    }
  }

  func peekProvenance(
    for draft: IOSGeminiProvenanceDraft
  ) -> IOSGeminiCandidateProvenance? {
    provenance(draft: draft, consume: false)
  }

  func consumeProvenance(
    for draft: IOSGeminiProvenanceDraft
  ) -> IOSGeminiCandidateProvenance? {
    provenance(draft: draft, consume: true)
  }

  func clearProvenance() {
    provenanceRegistry.clear()
  }

  @discardableResult
  func clearLocalDataForAccountWipe() -> Bool {
    provenanceRegistry.clear()
    return rateGuard.clearAll()
  }

  @discardableResult
  func clearLocalDataForAccountDeletion() -> Bool { clearLocalDataForAccountWipe() }

  private func provenance(
    draft: IOSGeminiProvenanceDraft,
    consume: Bool
  ) -> IOSGeminiCandidateProvenance? {
    guard let binding = identity.exactSessionBinding() else {
      provenanceRegistry.clear()
      return nil
    }
    guard let key = Self.accountSessionKey(binding) else {
      provenanceRegistry.clear()
      return nil
    }
    return consume
      ? provenanceRegistry.consume(accountSessionKey: key, draft: draft)
      : provenanceRegistry.peek(accountSessionKey: key, draft: draft)
  }

  private static func accountSessionKey(
    _ binding: IOSNativeGoogleAccountBinding
  ) -> String? {
    IOSGeminiAccountScope.accountSessionKey(
      firebaseUID: binding.firebaseUID,
      accountGeneration: binding.accountGeneration
    )
  }

  private func generateProviderText(request: IOSGeminiSuggestionRequest) async throws -> String {
    let candidateSchema = Schema.object(
      properties: [
        "text": .string(
          description: "A generic birthday greeting template and no explanatory prose."
        ),
        "language": .enumeration(
          values: ["en", "hi"],
          description: "The language tag of the greeting."
        ),
      ],
      description: "One locally validated birthday greeting candidate."
    )
    let outputSchema = Schema.object(
      properties: [
        "candidates": .array(
          items: candidateSchema,
          description: "One to three distinct generic birthday greeting templates.",
          minItems: 1,
          maxItems: 3
        ),
      ],
      description: "Birthday greeting suggestions only."
    )
    // The Vertex backend has no provider key in the app. Authenticated-users mode attaches the
    // current Firebase session and this exact flag consumes limited-use App Check tokens.
    let ai = FirebaseAI.firebaseAI(
      app: FirebaseApp.app(),
      backend: .vertexAI(location: IOSGeminiSuggestionPolicy.modelLocation),
      useLimitedUseAppCheckTokens: true
    )
    let model = ai.generativeModel(
      modelName: IOSGeminiSuggestionPolicy.modelName,
      generationConfig: GenerationConfig(
        temperature: 0.7,
        maxOutputTokens: 512,
        responseMIMEType: "application/json",
        responseSchema: outputSchema
      ),
      systemInstruction: ModelContent(
        role: "system",
        parts: IOSGeminiSuggestionPolicy.systemInstruction
      ),
      requestOptions: RequestOptions(timeout: 12)
    )
    let response = try await model.generateContent(IOSGeminiSuggestionPolicy.prompt(request))
    guard let text = response.text else { throw IOSGeminiGatewayError.emptyResponse }
    return text
  }

  private func appCheckReadyWithinTimeout() async -> Bool {
    await withCheckedContinuation { continuation in
      let gate = IOSGeminiContinuationGate()
      let finish: (Bool) -> Void = { value in
        guard gate.claim() else { return }
        continuation.resume(returning: value)
      }
      identity.appCheckGate(completion: finish)
      DispatchQueue.main.asyncAfter(deadline: .now() + 10, execute: { finish(false) })
    }
  }

  private func fallback(_ reason: String) -> [String: Any] {
    ["kind": "fallback", "reason": reason]
  }

  private static func isOffline(_ error: Error) -> Bool {
    let offlineCodes = [
      NSURLErrorNotConnectedToInternet,
      NSURLErrorNetworkConnectionLost,
      NSURLErrorCannotFindHost,
      NSURLErrorCannotConnectToHost,
      NSURLErrorDNSLookupFailed,
      NSURLErrorInternationalRoamingOff,
      NSURLErrorDataNotAllowed,
    ]
    var current: NSError? = error as NSError
    var depth = 0
    while let candidate = current, depth < 4 {
      if candidate.domain == NSURLErrorDomain, offlineCodes.contains(candidate.code) { return true }
      current = candidate.userInfo[NSUnderlyingErrorKey] as? NSError
      depth += 1
    }
    return false
  }

  private enum IOSGeminiGatewayError: Error { case emptyResponse }
}

private final class IOSGeminiContinuationGate: @unchecked Sendable {
  private let lock = NSLock()
  private var completed = false

  func claim() -> Bool {
    lock.lock()
    defer { lock.unlock() }
    guard !completed else { return false }
    completed = true
    return true
  }
}
