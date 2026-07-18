import CoreFoundation
import FirebaseAuth
import FirebaseFunctions
import Foundation

enum IOSComposerReservationFailure: Error {
  case accountChanged
  case configuration
  case deleting
  case managedByAndroid
  case reservationHeld
  case stale
  case unavailable
}

struct IOSComposerReservationGrant: Equatable {
  let reservationId: String
  let expiresAt: Date
  let safeDismissAfter: TimeInterval
  let earlyReleaseAllowed: Bool
}

struct IOSComposerCommittedReservation: Equatable {
  let reservationId: String
  let expiresAt: Date
  let safeDismissAfter: TimeInterval
}

/// Strict, content-free client for the account-global iOS composer fence. Each
/// callable uses a consumed limited-use App Check token. Ambiguous acquire or
/// commit outcomes never open MessageUI; ambiguous release never clears the
/// local owner capability.
@MainActor
final class IOSComposerReservationClient {
  static let shared = IOSComposerReservationClient()

  private static let region = "asia-south1"
  private static let acquireCallableName = "acquireIOSComposerReservation"
  private static let commitCallableName = "commitIOSComposerReservation"
  private static let releaseCallableName = "releaseIOSComposerReservation"
  private static let expectedLedgerGeneration = "birthday-ledger-v1"
  private static let reservationLifetimeMilliseconds = 72.0 * 60 * 60 * 1_000
  private static let dismissalGuardSeconds: TimeInterval = 5 * 60
  private static let maximumSafeInteger = 9_007_199_254_740_991.0
  private static let refusalReasons: Set<String> = [
    "DELETION_SUPPRESSED", "MANAGED_BY_ANDROID", "RESERVATION_EXPIRED",
    "RESERVATION_HELD", "RESERVATION_MISMATCH", "RESERVATION_MISSING",
    "SAFETY_STATUS_UNAVAILABLE", "STICKY_UNTIL_EXPIRY",
  ]

  private let journal = IOSComposerReservationJournal.shared

  private init() {}

  func acquireImmediatelyBeforePresentation(
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (
      Result<IOSComposerReservationGrant, IOSComposerReservationFailure>
    ) -> Void
  ) {
    guard Self.hasExactFirebaseSession(binding),
      Self.configuredLedgerGeneration() == Self.expectedLedgerGeneration,
      let capability = journal.loadOrCreate(binding: binding)
    else {
      completion(
        .failure(
          Self.hasExactFirebaseSession(binding) ? .configuration : .accountChanged
        )
      )
      return
    }
    let requestStartedAt = DispatchTime.now().uptimeNanoseconds
    let callable = Self.callable(named: Self.acquireCallableName)
    callable.call(Self.requestBody(reservationId: capability.reservationId)) {
      [weak self] result, error in
      guard let self else {
        completion(.failure(.unavailable))
        return
      }
      guard error == nil, Self.hasExactFirebaseSession(binding),
        let raw = result?.data as? [String: Any],
        let response = Self.parse(raw)
      else {
        completion(
          .failure(
            Self.hasExactFirebaseSession(binding) ? .unavailable : .accountChanged
          )
        )
        return
      }
      switch response {
      case .reserved(
        let serverNowMs,
        let expiresAtMs,
        let earlyReleaseAllowed
      ):
        guard expiresAtMs - serverNowMs
          == Self.reservationLifetimeMilliseconds,
          let safeDismissAfter = Self.safeDismissAfter(
            serverNowMs: serverNowMs,
            expiresAtMs: expiresAtMs,
            requestStartedAtUptimeNanoseconds: requestStartedAt
          )
        else {
          completion(.failure(.unavailable))
          return
        }
        let expiresAt = Date(timeIntervalSince1970: expiresAtMs / 1_000)
        guard let stored = self.journal.recordAcquire(
          binding: binding,
          reservationId: capability.reservationId,
          expiresAt: expiresAt,
          earlyReleaseAllowed: earlyReleaseAllowed
        ) else {
          completion(.failure(.unavailable))
          return
        }
        completion(
          .success(
            IOSComposerReservationGrant(
              reservationId: stored.reservationId,
              expiresAt: expiresAt,
              safeDismissAfter: safeDismissAfter,
              earlyReleaseAllowed:
                earlyReleaseAllowed && stored.phase == .prepared
            )
          )
        )
      case .refused(let reason, _):
        completion(.failure(Self.failure(for: reason)))
      default:
        completion(.failure(.unavailable))
      }
    }
  }

  func commitStickyImmediatelyBeforePresentation(
    binding: IOSNativeGoogleAccountBinding,
    grant: IOSComposerReservationGrant,
    completion: @escaping (
      Result<IOSComposerCommittedReservation, IOSComposerReservationFailure>
    ) -> Void
  ) {
    guard Self.hasExactFirebaseSession(binding),
      journal.current(binding: binding)?.reservationId == grant.reservationId,
      journal.markStickyBeforeCommit(
        binding: binding,
        reservationId: grant.reservationId
      )
    else {
      completion(
        .failure(
          Self.hasExactFirebaseSession(binding) ? .unavailable : .accountChanged
        )
      )
      return
    }
    let requestStartedAt = DispatchTime.now().uptimeNanoseconds
    let callable = Self.callable(named: Self.commitCallableName)
    callable.call(Self.requestBody(reservationId: grant.reservationId)) {
      result, error in
      guard error == nil, Self.hasExactFirebaseSession(binding),
        let raw = result?.data as? [String: Any],
        let response = Self.parse(raw)
      else {
        completion(
          .failure(
            Self.hasExactFirebaseSession(binding) ? .unavailable : .accountChanged
          )
        )
        return
      }
      switch response {
      case .committed(let serverNowMs, let expiresAtMs):
        guard Date(timeIntervalSince1970: expiresAtMs / 1_000) == grant.expiresAt,
          let safeDismissAfter = Self.safeDismissAfter(
            serverNowMs: serverNowMs,
            expiresAtMs: expiresAtMs,
            requestStartedAtUptimeNanoseconds: requestStartedAt
          )
        else {
          completion(.failure(.unavailable))
          return
        }
        completion(
          .success(
            IOSComposerCommittedReservation(
              reservationId: grant.reservationId,
              expiresAt: grant.expiresAt,
              safeDismissAfter: min(grant.safeDismissAfter, safeDismissAfter)
            )
          )
        )
      case .refused(let reason, _):
        completion(.failure(Self.failure(for: reason)))
      default:
        completion(.failure(.unavailable))
      }
    }
  }

  /// This is valid only before any sticky commit attempt. A failed or ambiguous
  /// response deliberately leaves the capability in place for exact replay.
  func releasePreparedReservation(
    binding: IOSNativeGoogleAccountBinding,
    reservationId: String,
    completion: ((Bool) -> Void)? = nil
  ) {
    guard Self.hasExactFirebaseSession(binding),
      let capability = journal.current(binding: binding),
      capability.reservationId == reservationId, capability.phase == .prepared
    else {
      completion?(false)
      return
    }
    let callable = Self.callable(named: Self.releaseCallableName)
    callable.call([
      "contractVersion": 1,
      "reservationId": reservationId,
    ]) { [weak self] result, error in
      guard let self, error == nil, Self.hasExactFirebaseSession(binding),
        let raw = result?.data as? [String: Any],
        let parsed = Self.parse(raw), case .released = parsed
      else {
        completion?(false)
        return
      }
      completion?(
        self.journal.clearAfterExactPreparedRelease(
          binding: binding,
          reservationId: reservationId
        )
      )
    }
  }

  private enum Response {
    case reserved(Double, Double, Bool)
    case committed(Double, Double)
    case released(Double)
    case refused(String, Double)
  }

  private static func parse(_ raw: [String: Any]) -> Response? {
    guard let kind = raw["kind"] as? String else { return nil }
    switch kind {
    case "RESERVED":
      guard Set(raw.keys) == [
        "earlyReleaseAllowed", "kind", "reservationExpiresAtMs", "serverNowMs",
      ], let serverNowMs = strictMilliseconds(raw["serverNowMs"]),
        let expiresAtMs = strictMilliseconds(raw["reservationExpiresAtMs"]),
        let earlyReleaseAllowed = raw["earlyReleaseAllowed"] as? Bool
      else { return nil }
      return .reserved(serverNowMs, expiresAtMs, earlyReleaseAllowed)
    case "COMMITTED":
      guard Set(raw.keys) == ["kind", "reservationExpiresAtMs", "serverNowMs"],
        let serverNowMs = strictMilliseconds(raw["serverNowMs"]),
        let expiresAtMs = strictMilliseconds(raw["reservationExpiresAtMs"])
      else { return nil }
      return .committed(serverNowMs, expiresAtMs)
    case "RELEASED":
      guard Set(raw.keys) == ["kind", "serverNowMs"],
        let serverNowMs = strictMilliseconds(raw["serverNowMs"])
      else { return nil }
      return .released(serverNowMs)
    case "REFUSED":
      guard Set(raw.keys) == ["kind", "reason", "serverNowMs"],
        let reason = raw["reason"] as? String, refusalReasons.contains(reason),
        let serverNowMs = strictMilliseconds(raw["serverNowMs"])
      else { return nil }
      return .refused(reason, serverNowMs)
    default:
      return nil
    }
  }

  private static func safeDismissAfter(
    serverNowMs: Double,
    expiresAtMs: Double,
    requestStartedAtUptimeNanoseconds: UInt64
  ) -> TimeInterval? {
    let now = DispatchTime.now().uptimeNanoseconds
    guard now >= requestStartedAtUptimeNanoseconds, expiresAtMs > serverNowMs,
      expiresAtMs - serverNowMs <= reservationLifetimeMilliseconds
    else { return nil }
    let roundTripSeconds = Double(now - requestStartedAtUptimeNanoseconds) / 1_000_000_000
    let safe = ((expiresAtMs - serverNowMs) / 1_000)
      - roundTripSeconds - dismissalGuardSeconds
    return safe > 0 ? safe : nil
  }

  private static func strictMilliseconds(_ raw: Any?) -> Double? {
    guard let number = raw as? NSNumber,
      CFGetTypeID(number) != CFBooleanGetTypeID()
    else { return nil }
    let value = number.doubleValue
    return value.isFinite && value >= 0 && value <= maximumSafeInteger
      && floor(value) == value ? value : nil
  }

  private static func failure(for reason: String) -> IOSComposerReservationFailure {
    switch reason {
    case "DELETION_SUPPRESSED": return .deleting
    case "MANAGED_BY_ANDROID": return .managedByAndroid
    case "RESERVATION_HELD": return .reservationHeld
    case "RESERVATION_EXPIRED", "RESERVATION_MISMATCH", "RESERVATION_MISSING":
      return .stale
    default: return .unavailable
    }
  }

  private static func callable(named name: String) -> HTTPSCallable {
    let options = HTTPSCallableOptions(requireLimitedUseAppCheckTokens: true)
    let callable = Functions.functions(region: region).httpsCallable(
      name,
      options: options
    )
    callable.timeoutInterval = 10
    return callable
  }

  private static func requestBody(reservationId: String) -> [String: Any] {
    [
      "contractVersion": 1,
      "ledgerGeneration": expectedLedgerGeneration,
      "reservationId": reservationId,
    ]
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
}
