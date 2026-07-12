import CoreFoundation
import CryptoKit
import FirebaseAuth
import FirebaseFunctions
import Foundation

enum IOSAccountDeletionFailure: Error {
  case accountChanged
  case configuration
  case networkOffline
  case recentAuthenticationRequired
  case responseInvalid
  case unavailable
}

struct IOSAccountDeletionAcceptance {
  let receiptId: String
  let drainUntil: Date
}

/// Starts or replays the authenticated deletion saga. The official SDK owns
/// Auth and limited-use App Check attachment; no credential or account value is
/// accepted from or returned to React Native.
@MainActor
final class IOSAccountDeletionClient {
  static let shared = IOSAccountDeletionClient()

  private static let callableName = "requestAccountDeletion"
  private static let region = "asia-south1"
  private static let maximumSafeInteger = 9_007_199_254_740_991.0
  private static let tombstoneRequiredKeys: Set<String> = [
    "createdAtMs", "drainUntilMs", "requestKey", "schemaVersion", "stage",
    "updatedAtMs",
  ]
  private static let fenceRequiredKeys: Set<String> = [
    "deletionDrainUntilMs", "mode", "resetGeneration", "senderEpoch",
  ]
  private static let receiptKeyDomain = "birthday-deletion-receipt-v1\0"

  private init() {}

  func startOrReplay(
    binding: IOSNativeGoogleAccountBinding,
    requestId: String,
    completion: @escaping (Result<IOSAccountDeletionAcceptance, IOSAccountDeletionFailure>) -> Void
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
      guard let raw = result?.data as? [String: Any],
        let acceptance = Self.parse(raw, expectedReceiptId: requestId)
      else {
        completion(.failure(.responseInvalid))
        return
      }
      completion(.success(acceptance))
    }
  }

  private static func parse(
    _ raw: [String: Any],
    expectedReceiptId: String
  ) -> IOSAccountDeletionAcceptance? {
    guard Set(raw.keys) == ["fence", "kind", "receiptId", "tombstone"],
      let kind = raw["kind"] as? String,
      ["STARTED", "REPLAYED"].contains(kind),
      raw["receiptId"] as? String == expectedReceiptId,
      let tombstone = raw["tombstone"] as? [String: Any],
      let drainUntilMs = parseTombstone(
        tombstone,
        expectedReceiptId: expectedReceiptId
      )
    else { return nil }

    guard let fenceValue = raw["fence"] else { return nil }
    if !(fenceValue is NSNull) {
      guard let fence = fenceValue as? [String: Any],
        parseDeletingFence(fence, expectedDrainUntilMs: drainUntilMs)
      else { return nil }
    }
    return IOSAccountDeletionAcceptance(
      receiptId: expectedReceiptId,
      drainUntil: Date(timeIntervalSince1970: drainUntilMs / 1_000)
    )
  }

  private static func parseTombstone(
    _ raw: [String: Any],
    expectedReceiptId: String
  ) -> Double? {
    let keys = Set(raw.keys)
    guard
      keys == tombstoneRequiredKeys
        || keys == tombstoneRequiredKeys.union(["cleanupAtMs"]),
      strictInteger(raw["schemaVersion"], minimum: 1, maximum: 1) == 1,
      raw["requestKey"] as? String == receiptKey(expectedReceiptId),
      let stage = raw["stage"] as? String,
      ["DRAINING", "PURGING", "AUTH_DELETION_PENDING", "VERIFYING"].contains(stage),
      let drainUntil = strictInteger(
        raw["drainUntilMs"], minimum: 0, maximum: maximumSafeInteger
      ),
      let createdAt = strictInteger(
        raw["createdAtMs"], minimum: 0, maximum: maximumSafeInteger
      ),
      let updatedAt = strictInteger(
        raw["updatedAtMs"], minimum: 0, maximum: maximumSafeInteger
      ),
      updatedAt >= createdAt,
      drainUntil >= createdAt,
      raw["cleanupAtMs"].map({
        guard
          let cleanupAt = strictInteger(
            $0, minimum: 0, maximum: maximumSafeInteger
          )
        else { return false }
        return cleanupAt >= updatedAt
      }) ?? true
    else { return nil }
    return drainUntil
  }

  private static func parseDeletingFence(
    _ raw: [String: Any],
    expectedDrainUntilMs: Double
  ) -> Bool {
    guard Set(raw.keys) == fenceRequiredKeys,
      raw["mode"] as? String == "DELETING",
      strictInteger(raw["senderEpoch"], minimum: 1, maximum: maximumSafeInteger) != nil,
      strictInteger(raw["resetGeneration"], minimum: 1, maximum: maximumSafeInteger) != nil,
      strictInteger(
        raw["deletionDrainUntilMs"], minimum: 0, maximum: maximumSafeInteger
      ) == expectedDrainUntilMs
    else { return false }
    return true
  }

  private static func receiptKey(_ receiptId: String) -> String {
    SHA256.hash(data: Data((receiptKeyDomain + receiptId).utf8))
      .map { String(format: "%02x", $0) }
      .joined()
  }

  private static func strictInteger(
    _ value: Any?,
    minimum: Double,
    maximum: Double
  ) -> Double? {
    guard let number = value as? NSNumber,
      CFGetTypeID(number) != CFBooleanGetTypeID()
    else { return nil }
    let double = number.doubleValue
    guard double.isFinite, double >= minimum, double <= maximum,
      floor(double) == double
    else { return nil }
    return double
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
      && value.range(
        of: "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
        options: .regularExpression
      ) != nil
  }

  private static func map(_ error: Error) -> IOSAccountDeletionFailure {
    let nsError = error as NSError
    if nsError.domain == NSURLErrorDomain {
      return .networkOffline
    }
    if nsError.code == FunctionsErrorCode.failedPrecondition.rawValue {
      return .recentAuthenticationRequired
    }
    if nsError.code == FunctionsErrorCode.unauthenticated.rawValue {
      return .accountChanged
    }
    return .unavailable
  }
}
