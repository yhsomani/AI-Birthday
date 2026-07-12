import CoreFoundation
import FirebaseAuth
import FirebaseFunctions
import Foundation

enum IOSContactDerivedResetFailure: Error {
  case accountChanged
  case configuration
  case continuityUnavailable
  case deletionInProgress
  case generationExhausted
  case networkOffline
  case operationInProgress
  case recentAuthenticationRequired
  case requestMismatch
  case resetSuppressed
  case responseInvalid
  case unavailable
}

enum IOSContactDerivedResetAcceptance {
  case inProgress(drainUntil: Date?)
  case completed(androidStateExisted: Bool)
}

/// Replays the account-global, content-free contact-derived reset. The official
/// Firebase SDK attaches Auth and a consumed limited-use App Check token. No
/// credential, Google subject, Firebase UID, or request body is exposed to JS.
@MainActor
final class IOSContactDerivedResetClient {
  static let shared = IOSContactDerivedResetClient()

  private static let callableName = "resetContactDerivedState"
  private static let region = "asia-south1"
  private static let maximumSafeInteger = 9_007_199_254_740_991.0
  private static let refusalReasons: Set<String> = [
    "CONTINUITY_UNAVAILABLE", "COORDINATION_OPERATION_IN_PROGRESS",
    "DELETION_SUPPRESSED", "GENERATION_EXHAUSTED", "REQUEST_MISMATCH",
    "RESET_SUPPRESSED",
  ]

  private init() {}

  func startOrReplay(
    binding: IOSNativeGoogleAccountBinding,
    requestId: String,
    completion:
      @escaping (
        Result<IOSContactDerivedResetAcceptance, IOSContactDerivedResetFailure>
      ) -> Void
  ) {
    guard Self.isCanonicalUUID(requestId), Self.hasExactFirebaseSession(binding) else {
      completion(.failure(.accountChanged))
      return
    }

    let options = HTTPSCallableOptions(requireLimitedUseAppCheckTokens: true)
    let callable = Functions.functions(region: Self.region).httpsCallable(
      Self.callableName,
      options: options
    )
    callable.timeoutInterval = 30
    callable.call([
      "contractVersion": 1,
      "requestId": requestId,
    ]) { result, error in
      guard Self.hasExactFirebaseSession(binding) else {
        completion(.failure(.accountChanged))
        return
      }
      if let error {
        completion(.failure(Self.map(error)))
        return
      }
      guard let raw = result?.data as? [String: Any] else {
        completion(.failure(.responseInvalid))
        return
      }
      completion(Self.parse(raw))
    }
  }

  private static func parse(
    _ raw: [String: Any]
  ) -> Result<IOSContactDerivedResetAcceptance, IOSContactDerivedResetFailure> {
    guard let kind = raw["kind"] as? String else {
      return .failure(.responseInvalid)
    }
    switch kind {
    case "IN_PROGRESS":
      return parseInProgress(raw)
    case "COMPLETED":
      return parseCompleted(raw)
    case "REFUSED":
      guard Set(raw.keys) == ["kind", "reason"],
        let reason = raw["reason"] as? String,
        refusalReasons.contains(reason)
      else { return .failure(.responseInvalid) }
      return .failure(refusal(reason))
    default:
      return .failure(.responseInvalid)
    }
  }

  private static func parseInProgress(
    _ raw: [String: Any]
  ) -> Result<IOSContactDerivedResetAcceptance, IOSContactDerivedResetFailure> {
    guard raw["operation"] as? String == "CONTACT_DERIVED_RESET",
      let stage = raw["stage"] as? String,
      ["RESET_DRAINING", "RESET_PURGING"].contains(stage),
      let androidStateExisted = strictBoolean(raw["androidStateExisted"])
    else { return .failure(.responseInvalid) }

    let baseKeys: Set<String> = [
      "androidStateExisted", "kind", "operation", "stage",
    ]
    if !androidStateExisted {
      guard Set(raw.keys) == baseKeys, stage == "RESET_PURGING" else {
        return .failure(.responseInvalid)
      }
      return .success(.inProgress(drainUntil: nil))
    }

    var expected = baseKeys.union([
      "birthdayAutomationNotBeforeMs", "resetGenerationAfter", "senderEpochAfter",
    ])
    if stage == "RESET_DRAINING" { expected.insert("drainUntilMs") }
    guard Set(raw.keys) == expected,
      strictPositiveInteger(raw["senderEpochAfter"]) != nil,
      strictPositiveInteger(raw["resetGenerationAfter"]) != nil,
      strictNonnegativeInteger(raw["birthdayAutomationNotBeforeMs"]) != nil
    else { return .failure(.responseInvalid) }

    if stage == "RESET_DRAINING" {
      guard let drainUntil = strictNonnegativeInteger(raw["drainUntilMs"]) else {
        return .failure(.responseInvalid)
      }
      return .success(
        .inProgress(drainUntil: Date(timeIntervalSince1970: drainUntil / 1_000))
      )
    }
    return .success(.inProgress(drainUntil: nil))
  }

  private static func parseCompleted(
    _ raw: [String: Any]
  ) -> Result<IOSContactDerivedResetAcceptance, IOSContactDerivedResetFailure> {
    guard raw["operation"] as? String == "CONTACT_DERIVED_RESET",
      let androidStateExisted = strictBoolean(raw["androidStateExisted"]),
      strictBoolean(raw["contactDerivedStateErased"]) == true,
      strictBoolean(raw["firebaseAuthPreserved"]) == true,
      strictNonnegativeInteger(raw["completedAtMs"]) != nil
    else { return .failure(.responseInvalid) }

    var expected: Set<String> = [
      "androidStateExisted", "completedAtMs", "contactDerivedStateErased",
      "firebaseAuthPreserved", "kind", "operation",
    ]
    if androidStateExisted {
      expected.formUnion([
        "birthdayAutomationNotBeforeMs", "resetGenerationAfter", "senderEpochAfter",
      ])
      guard strictPositiveInteger(raw["senderEpochAfter"]) != nil,
        strictPositiveInteger(raw["resetGenerationAfter"]) != nil,
        strictNonnegativeInteger(raw["birthdayAutomationNotBeforeMs"]) != nil
      else { return .failure(.responseInvalid) }
    }
    guard Set(raw.keys) == expected else { return .failure(.responseInvalid) }
    return .success(.completed(androidStateExisted: androidStateExisted))
  }

  private static func refusal(_ reason: String) -> IOSContactDerivedResetFailure {
    switch reason {
    case "DELETION_SUPPRESSED": return .deletionInProgress
    case "COORDINATION_OPERATION_IN_PROGRESS": return .operationInProgress
    case "REQUEST_MISMATCH": return .requestMismatch
    case "RESET_SUPPRESSED": return .resetSuppressed
    case "CONTINUITY_UNAVAILABLE": return .continuityUnavailable
    case "GENERATION_EXHAUSTED": return .generationExhausted
    default: return .responseInvalid
    }
  }

  private static func strictBoolean(_ value: Any?) -> Bool? {
    guard let number = value as? NSNumber,
      CFGetTypeID(number) == CFBooleanGetTypeID()
    else { return nil }
    return number.boolValue
  }

  private static func strictPositiveInteger(_ value: Any?) -> Double? {
    strictInteger(value, minimum: 1)
  }

  private static func strictNonnegativeInteger(_ value: Any?) -> Double? {
    strictInteger(value, minimum: 0)
  }

  private static func strictInteger(_ value: Any?, minimum: Double) -> Double? {
    guard let number = value as? NSNumber,
      CFGetTypeID(number) != CFBooleanGetTypeID()
    else { return nil }
    let value = number.doubleValue
    guard value.isFinite, value >= minimum, value <= maximumSafeInteger,
      floor(value) == value
    else { return nil }
    return value
  }

  private static func hasExactFirebaseSession(
    _ binding: IOSNativeGoogleAccountBinding
  ) -> Bool {
    guard let user = Auth.auth().currentUser, user.uid == binding.firebaseUID else {
      return false
    }
    return user.providerData.contains {
      $0.providerID == "google.com" && $0.uid == binding.googleSubject
    }
  }

  private static func isCanonicalUUID(_ value: String) -> Bool {
    guard let uuid = UUID(uuidString: value) else { return false }
    return uuid.uuidString.lowercased() == value
  }

  private static func map(_ error: Error) -> IOSContactDerivedResetFailure {
    let nsError = error as NSError
    if nsError.domain == NSURLErrorDomain { return .networkOffline }
    if nsError.code == FunctionsErrorCode.failedPrecondition.rawValue {
      return .recentAuthenticationRequired
    }
    if nsError.code == FunctionsErrorCode.unauthenticated.rawValue {
      return .accountChanged
    }
    if nsError.code == FunctionsErrorCode.invalidArgument.rawValue {
      return .configuration
    }
    return .unavailable
  }
}
