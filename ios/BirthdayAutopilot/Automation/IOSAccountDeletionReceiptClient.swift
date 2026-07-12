import CoreFoundation
import FirebaseCore
import FirebaseFunctions
import Foundation

enum IOSAccountDeletionReceiptStatus {
  case inProgress(requestedAt: Date, updatedAt: Date)
  case completed(completedAt: Date)
  case notFound
}

enum IOSAccountDeletionReceiptFailure: Error {
  case configuration
  case networkOffline
  case responseInvalid
  case signedOutRequired
  case unavailable
}

/// Checks a content-minimal deletion receipt after Firebase Auth has been removed.
/// The lowercase UUID is a high-entropy bearer held only in protected native
/// state. Firebase attaches a consumed limited-use App Check token; no account
/// identifier, credential, receipt, or response crosses React Native.
@MainActor
final class IOSAccountDeletionReceiptClient {
  static let shared = IOSAccountDeletionReceiptClient()

  private static let callableName = "accountDeletionReceipt"
  private static let region = "asia-south1"
  private static let maximumSafeInteger = 9_007_199_254_740_991.0

  private init() {}

  func check(
    receiptId: String,
    completion:
      @escaping (
        Result<IOSAccountDeletionReceiptStatus, IOSAccountDeletionReceiptFailure>
      ) -> Void
  ) {
    guard FirebaseApp.app() != nil, Self.isCanonicalUUID(receiptId) else {
      completion(.failure(.configuration))
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
      "receiptId": receiptId,
    ]) { result, error in
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
  ) -> Result<IOSAccountDeletionReceiptStatus, IOSAccountDeletionReceiptFailure> {
    guard let kind = raw["kind"] as? String else {
      return .failure(.responseInvalid)
    }
    switch kind {
    case "IN_PROGRESS":
      guard Set(raw.keys) == ["kind", "requestedAtMs", "updatedAtMs"],
        let requestedAtMs = strictTimestamp(raw["requestedAtMs"]),
        let updatedAtMs = strictTimestamp(raw["updatedAtMs"]),
        updatedAtMs >= requestedAtMs
      else { return .failure(.responseInvalid) }
      return .success(
        .inProgress(
          requestedAt: Date(timeIntervalSince1970: requestedAtMs / 1_000),
          updatedAt: Date(timeIntervalSince1970: updatedAtMs / 1_000)
        ))
    case "COMPLETED":
      guard
        Set(raw.keys) == [
          "appAccountDeleted", "completedAtMs", "externalCopiesNotDeleted", "kind",
          "requestedAtMs", "serverDataDeleted",
        ],
        strictBoolean(raw["appAccountDeleted"]) == true,
        strictBoolean(raw["serverDataDeleted"]) == true,
        strictBoolean(raw["externalCopiesNotDeleted"]) == true,
        let requestedAtMs = strictTimestamp(raw["requestedAtMs"]),
        let completedAtMs = strictTimestamp(raw["completedAtMs"]),
        completedAtMs >= requestedAtMs
      else { return .failure(.responseInvalid) }
      return .success(
        .completed(completedAt: Date(timeIntervalSince1970: completedAtMs / 1_000)))
    case "NOT_FOUND":
      guard Set(raw.keys) == ["kind"] else { return .failure(.responseInvalid) }
      return .success(.notFound)
    default:
      return .failure(.responseInvalid)
    }
  }

  private static func strictTimestamp(_ value: Any?) -> Double? {
    guard let number = value as? NSNumber,
      CFGetTypeID(number) != CFBooleanGetTypeID()
    else { return nil }
    let value = number.doubleValue
    guard value.isFinite, value >= 0, value <= maximumSafeInteger,
      floor(value) == value
    else { return nil }
    return value
  }

  private static func strictBoolean(_ value: Any?) -> Bool? {
    guard let number = value as? NSNumber,
      CFGetTypeID(number) == CFBooleanGetTypeID()
    else { return nil }
    return number.boolValue
  }

  private static func isCanonicalUUID(_ value: String) -> Bool {
    guard let uuid = UUID(uuidString: value) else { return false }
    return uuid.uuidString.lowercased() == value
      && value.range(
        of: "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
        options: .regularExpression
      ) != nil
  }

  private static func map(
    _ error: Error
  ) -> IOSAccountDeletionReceiptFailure {
    let nsError = error as NSError
    if nsError.domain == NSURLErrorDomain { return .networkOffline }
    if nsError.code == FunctionsErrorCode.invalidArgument.rawValue {
      return .configuration
    }
    if nsError.code == FunctionsErrorCode.failedPrecondition.rawValue {
      return .signedOutRequired
    }
    return .unavailable
  }
}
