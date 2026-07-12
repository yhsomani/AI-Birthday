import CoreFoundation
import FirebaseAuth
import FirebaseFunctions
import Foundation

enum IOSCompanionStatusFailure: Error {
  case accountChanged
  case configuration
  case deleting
  case managedByAndroid
  case unavailable
}

/// Strict, content-free client for the account-global companionStatus callable.
/// Firebase Auth and replay-protected App Check are attached by the official
/// SDK. Every ambiguous result is persisted as unknown before review fails.
@MainActor
final class IOSCompanionStatusClient {
  static let shared = IOSCompanionStatusClient()

  private static let callableName = "companionStatus"
  private static let region = "asia-south1"
  private static let expectedLedgerGeneration = "birthday-ledger-v1"
  private static let maximumSafeInteger = 9_007_199_254_740_991.0
  private let store = CompanionProtectedStore.shared

  private init() {}

  func refreshControlImmediatelyBeforeReview(
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (Result<Void, IOSCompanionStatusFailure>) -> Void
  ) {
    guard Self.hasExactFirebaseSession(binding),
      Self.configuredLedgerGeneration() == Self.expectedLedgerGeneration
    else {
      persistUnknown(
        binding: binding,
        failure: Self.hasExactFirebaseSession(binding) ? .configuration : .accountChanged,
        completion: completion
      )
      return
    }

    let options = HTTPSCallableOptions(requireLimitedUseAppCheckTokens: true)
    let callable = Functions.functions(region: Self.region).httpsCallable(
      Self.callableName,
      options: options
    )
    callable.timeoutInterval = 10
    callable.call([
      "contractVersion": 1,
      "ledgerGeneration": Self.expectedLedgerGeneration,
    ]) { [weak self] result, error in
      guard let self else {
        completion(.failure(.unavailable))
        return
      }
      guard error == nil, Self.hasExactFirebaseSession(binding),
        let raw = result?.data as? [String: Any],
        let parsed = Self.parse(raw)
      else {
        self.persistUnknown(
          binding: binding,
          failure: Self.hasExactFirebaseSession(binding) ? .unavailable : .accountChanged,
          completion: completion
        )
        return
      }

      let coexistence: CompanionAndroidCoexistence
      let publicResult: Result<Void, IOSCompanionStatusFailure>
      switch parsed.state {
      case "NO_ANDROID_STATE" where parsed.composerAllowed:
        coexistence = .clear
        publicResult = .success(())
      case "MANAGED_BY_ANDROID" where !parsed.composerAllowed:
        coexistence = .managed
        publicResult = .failure(.managedByAndroid)
      case "DELETING" where !parsed.composerAllowed:
        coexistence = .deleting
        publicResult = .failure(.deleting)
      default:
        coexistence = .unknown
        publicResult = .failure(.unavailable)
      }
      let control = CompanionControlState(
        accountGeneration: binding.accountGeneration,
        androidCoexistence: coexistence,
        checkedAt: Date(),
        trustedServerTime: Date(timeIntervalSince1970: parsed.serverNowMs / 1_000)
      )
      self.store.updateControlForComposer(control, binding: binding) { persisted in
        switch persisted {
        case .success:
          self.releaseResetSafetyIfEligible(
            trustedServerTime: control.trustedServerTime!,
            observedAt: control.checkedAt
          ) {
            completion(publicResult)
          }
        case .failure:
          completion(.failure(.unavailable))
        }
      }
    }
  }

  private func persistUnknown(
    binding: IOSNativeGoogleAccountBinding,
    failure: IOSCompanionStatusFailure,
    completion: @escaping (Result<Void, IOSCompanionStatusFailure>) -> Void
  ) {
    let control = CompanionControlState(
      accountGeneration: binding.accountGeneration,
      androidCoexistence: .unknown,
      checkedAt: Date(),
      trustedServerTime: nil
    )
    store.updateControlForComposer(control, binding: binding) { _ in
      completion(.failure(failure))
    }
  }

  private func releaseResetSafetyIfEligible(
    trustedServerTime: Date,
    observedAt: Date,
    completion: @escaping () -> Void
  ) {
    store.readProjectionStatus { [store] result in
      guard case .success(let status) = result, status.resetSafetyRequiresRelease else {
        completion()
        return
      }
      // The callable's schema-validated serverNowMs is the trusted time source.
      // Comparing it with the local clock only verifies current clock accuracy;
      // network receipt time is never promoted into trusted time.
      let clockVerified = abs(observedAt.timeIntervalSince(trustedServerTime)) <= 5 * 60
      let attemptRelease = {
        store.canReleaseResetSafety(
          trustedServerTime: trustedServerTime,
          automaticTimeVerified: clockVerified,
          observedAt: observedAt
        ) { eligible in
          guard eligible else { return completion() }
          store.releaseResetSafety(
            trustedServerTime: trustedServerTime,
            automaticTimeVerified: clockVerified,
            observedAt: observedAt
          ) { _ in completion() }
        }
      }
      if status.resetSafetyVerified {
        attemptRelease()
      } else {
        store.establishVerifiedResetSafetyDate(
          automaticTimeVerified: clockVerified,
          observedAt: observedAt
        ) { _ in attemptRelease() }
      }
    }
  }

  private static func configuredLedgerGeneration(bundle: Bundle = .main) -> String? {
    guard let value = bundle.object(
      forInfoDictionaryKey: "BirthdayLedgerGeneration"
    ) as? String,
      value.range(
        of: "^[A-Za-z0-9._-]{8,64}$",
        options: .regularExpression
      ) != nil
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

  private static func parse(
    _ raw: [String: Any]
  ) -> (composerAllowed: Bool, state: String, serverNowMs: Double)? {
    guard let composerAllowed = raw["composerAllowed"] as? Bool,
      let state = raw["state"] as? String,
      let number = raw["serverNowMs"] as? NSNumber,
      CFGetTypeID(number) != CFBooleanGetTypeID()
    else { return nil }
    let serverNowMs = number.doubleValue
    guard serverNowMs.isFinite, serverNowMs >= 0,
      serverNowMs <= maximumSafeInteger, floor(serverNowMs) == serverNowMs
    else { return nil }

    let ledger = raw["ledgerGeneration"] as? String
    let allowedKeys: Set<String>
    switch state {
    case "NO_ANDROID_STATE":
      guard composerAllowed, ledger == expectedLedgerGeneration else { return nil }
      allowedKeys = ["composerAllowed", "ledgerGeneration", "serverNowMs", "state"]
    case "MANAGED_BY_ANDROID", "DELETING":
      guard !composerAllowed, ledger == expectedLedgerGeneration else { return nil }
      allowedKeys = ["composerAllowed", "ledgerGeneration", "serverNowMs", "state"]
    case "SAFETY_STATUS_UNAVAILABLE":
      guard !composerAllowed,
        ledger == nil || ledger == expectedLedgerGeneration
      else { return nil }
      allowedKeys = ledger == nil
        ? ["composerAllowed", "serverNowMs", "state"]
        : ["composerAllowed", "ledgerGeneration", "serverNowMs", "state"]
    default:
      return nil
    }
    guard Set(raw.keys) == allowedKeys else { return nil }
    return (composerAllowed, state, serverNowMs)
  }
}
