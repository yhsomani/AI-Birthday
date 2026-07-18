import CryptoKit
import Foundation

enum CompanionComposerOutcome: String, Codable, Hashable {
  case openCommitted = "open-committed"
  case presented
  case cancelled
  case failed
  case outcomeUnknown = "outcome-unknown"
  case reportedSent = "reported-sent"

  var preventsRepeat: Bool {
    self == .openCommitted || self == .presented || self == .outcomeUnknown
      || self == .reportedSent
  }
}

enum IOSProtectedStoreEnvelopeError: Error, Equatable {
  case invalidKey
  case oversized
}

/// The production protected store and the standalone native contract tests use
/// this exact codec. Keeping the authenticated envelope separate from file and
/// Keychain APIs lets corruption and wrong-key behavior be exercised without
/// introducing a second persistence format for tests.
enum IOSProtectedStoreEnvelope {
  static let keyByteCount = 32

  static func encode<Value: Encodable>(_ value: Value) throws -> Data {
    let encoder = JSONEncoder()
    encoder.dateEncodingStrategy = .millisecondsSince1970
    encoder.outputFormatting = [.sortedKeys]
    return try encoder.encode(value)
  }

  static func decode<Value: Decodable>(
    _ type: Value.Type,
    from plaintext: Data
  ) throws -> Value {
    let decoder = JSONDecoder()
    decoder.dateDecodingStrategy = .millisecondsSince1970
    return try decoder.decode(type, from: plaintext)
  }

  static func seal(
    _ plaintext: Data,
    keyData: Data,
    authenticatedContext: Data,
    maximumBytes: Int
  ) throws -> Data {
    guard keyData.count == keyByteCount else {
      throw IOSProtectedStoreEnvelopeError.invalidKey
    }
    let sealedBox = try AES.GCM.seal(
      plaintext,
      using: SymmetricKey(data: keyData),
      authenticating: authenticatedContext
    )
    guard let combined = sealedBox.combined, combined.count <= maximumBytes else {
      throw IOSProtectedStoreEnvelopeError.oversized
    }
    return combined
  }

  static func seal(
    _ plaintext: Data,
    using key: SymmetricKey,
    authenticatedContext: Data,
    maximumBytes: Int
  ) throws -> Data {
    let sealedBox = try AES.GCM.seal(
      plaintext,
      using: key,
      authenticating: authenticatedContext
    )
    guard let combined = sealedBox.combined, combined.count <= maximumBytes else {
      throw IOSProtectedStoreEnvelopeError.oversized
    }
    return combined
  }

  static func open(
    _ sealedData: Data,
    keyData: Data,
    authenticatedContext: Data,
    maximumBytes: Int
  ) throws -> Data {
    guard keyData.count == keyByteCount else {
      throw IOSProtectedStoreEnvelopeError.invalidKey
    }
    return try open(
      sealedData,
      using: SymmetricKey(data: keyData),
      authenticatedContext: authenticatedContext,
      maximumBytes: maximumBytes
    )
  }

  static func open(
    _ sealedData: Data,
    using key: SymmetricKey,
    authenticatedContext: Data,
    maximumBytes: Int
  ) throws -> Data {
    guard sealedData.count <= maximumBytes else {
      throw IOSProtectedStoreEnvelopeError.oversized
    }
    let sealedBox = try AES.GCM.SealedBox(combined: sealedData)
    return try AES.GCM.open(
      sealedBox,
      using: key,
      authenticating: authenticatedContext
    )
  }
}

enum IOSProtectedStoreKeyState: Equatable {
  case available
  case missing
  case notChecked
  case unavailable
}

enum IOSProtectedStoreLoadAction: Equatable {
  case installFencedReset
  case readAuthenticatedSnapshot
  case refuseAccess
}

/// Missing encrypted material or an independently missing key is never treated
/// as a clean empty database. Both paths install the same date-aware replay
/// fence. A temporarily unavailable Keychain fails closed and preserves state.
enum IOSProtectedStoreLoadPolicy {
  static func action(
    fileExists: Bool,
    keyState: IOSProtectedStoreKeyState
  ) -> IOSProtectedStoreLoadAction {
    guard fileExists else { return .installFencedReset }
    switch keyState {
    case .available:
      return .readAuthenticatedSnapshot
    case .missing:
      return .installFencedReset
    case .notChecked, .unavailable:
      return .refuseAccess
    }
  }
}

enum IOSProtectedStoreSchemaAction: Equatable {
  case accept
  case migrateV2
  case reject
}

enum IOSProtectedStoreSchemaPolicy {
  static func action(
    storedVersion: Int,
    currentVersion: Int
  ) -> IOSProtectedStoreSchemaAction {
    if storedVersion == currentVersion { return .accept }
    if storedVersion == 2, currentVersion == 3 { return .migrateV2 }
    return .reject
  }
}

enum IOSComposerDelegateResult: Equatable {
  case cancelled
  case failed
  case reportedSent
  case unknown
}

struct IOSComposerDelegateTerminal: Equatable {
  let persistedOutcome: CompanionComposerOutcome
  let publicOutcome: String
}

/// Serializes the MessageUI terminal boundary: durable state is attempted
/// first, UIKit dismissal happens second, and JavaScript is resolved only after
/// dismissal. Persistence ambiguity is always projected as Unknown, preserving
/// the repeat-suppression marker left by the earlier sticky commit.
enum IOSComposerDelegateTerminalSequencer {
  static func terminal(
    for result: IOSComposerDelegateResult
  ) -> IOSComposerDelegateTerminal {
    switch result {
    case .cancelled:
      return IOSComposerDelegateTerminal(
        persistedOutcome: .cancelled,
        publicOutcome: "cancelled"
      )
    case .failed:
      return IOSComposerDelegateTerminal(
        persistedOutcome: .failed,
        publicOutcome: "failed"
      )
    case .reportedSent:
      return IOSComposerDelegateTerminal(
        persistedOutcome: .reportedSent,
        publicOutcome: "reported-sent"
      )
    case .unknown:
      return IOSComposerDelegateTerminal(
        persistedOutcome: .outcomeUnknown,
        publicOutcome: "unknown"
      )
    }
  }

  static func finish(
    result: IOSComposerDelegateResult,
    persist: @escaping (
      _ outcome: CompanionComposerOutcome,
      _ completion: @escaping (Bool) -> Void
    ) -> Void,
    dismiss: @escaping (@escaping () -> Void) -> Void,
    completion: @escaping (_ publicOutcome: String) -> Void
  ) {
    let terminal = terminal(for: result)
    persist(terminal.persistedOutcome) { persisted in
      dismiss {
        completion(persisted ? terminal.publicOutcome : "unknown")
      }
    }
  }
}
