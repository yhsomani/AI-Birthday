import Foundation

private struct EnvelopeFixture: Codable, Equatable {
  let generation: String
  let writtenAt: Date
  let terminal: CompanionComposerOutcome
}

private struct NativeBoundaryFailure: Error, CustomStringConvertible {
  let description: String
}

private func require(
  _ condition: @autoclosure () -> Bool,
  _ message: String
) throws {
  guard condition() else { throw NativeBoundaryFailure(description: message) }
}

private func requireThrows(
  _ message: String,
  _ operation: () throws -> Void
) throws {
  do {
    try operation()
    throw NativeBoundaryFailure(description: message)
  } catch is NativeBoundaryFailure {
    throw NativeBoundaryFailure(description: message)
  } catch {
    return
  }
}

@main
private enum IOSNativeBoundaryPolicyTests {
  static func main() throws {
    let fixture = EnvelopeFixture(
      generation: "account-generation",
      writtenAt: Date(timeIntervalSince1970: 1_700_000_000.125),
      terminal: .outcomeUnknown
    )
    let plaintext = try IOSProtectedStoreEnvelope.encode(fixture)
    let key = Data((0..<32).map(UInt8.init))
    let otherKey = Data((1...32).map(UInt8.init))
    let context = Data("birthday-autopilot.native-boundary-test".utf8)
    let sealed = try IOSProtectedStoreEnvelope.seal(
      plaintext,
      keyData: key,
      authenticatedContext: context,
      maximumBytes: 4_096
    )
    let opened = try IOSProtectedStoreEnvelope.open(
      sealed,
      keyData: key,
      authenticatedContext: context,
      maximumBytes: 4_096
    )
    let decoded = try IOSProtectedStoreEnvelope.decode(
      EnvelopeFixture.self,
      from: opened
    )
    try require(
      decoded == fixture,
      "protected envelope did not round-trip the serialized fixture"
    )
    let encodedAgain = try IOSProtectedStoreEnvelope.encode(fixture)
    try require(
      plaintext == encodedAgain,
      "protected snapshot serialization was not deterministic"
    )

    var corrupted = sealed
    corrupted[corrupted.index(before: corrupted.endIndex)] ^= 0x01
    try requireThrows("authenticated corruption was accepted") {
      _ = try IOSProtectedStoreEnvelope.open(
        corrupted,
        keyData: key,
        authenticatedContext: context,
        maximumBytes: 4_096
      )
    }
    try requireThrows("wrong key was accepted") {
      _ = try IOSProtectedStoreEnvelope.open(
        sealed,
        keyData: otherKey,
        authenticatedContext: context,
        maximumBytes: 4_096
      )
    }
    try requireThrows("wrong authenticated context was accepted") {
      _ = try IOSProtectedStoreEnvelope.open(
        sealed,
        keyData: key,
        authenticatedContext: Data("wrong-context".utf8),
        maximumBytes: 4_096
      )
    }

    try require(
      IOSProtectedStoreLoadPolicy.action(
        fileExists: false,
        keyState: .notChecked
      ) == .installFencedReset,
      "missing file did not select a fenced reset"
    )
    try require(
      IOSProtectedStoreLoadPolicy.action(
        fileExists: true,
        keyState: .missing
      ) == .installFencedReset,
      "independent key loss did not select a fenced reset"
    )
    try require(
      IOSProtectedStoreLoadPolicy.action(
        fileExists: true,
        keyState: .unavailable
      ) == .refuseAccess,
      "temporarily unavailable Keychain did not fail closed"
    )
    try require(
      IOSProtectedStoreSchemaPolicy.action(
        storedVersion: 2,
        currentVersion: 3
      ) == .migrateV2,
      "schema v2 did not select the only supported migration"
    )
    try require(
      IOSProtectedStoreSchemaPolicy.action(
        storedVersion: 4,
        currentVersion: 3
      ) == .reject,
      "unknown future schema was accepted"
    )

    let unknownTerminal = IOSComposerDelegateTerminalSequencer.terminal(
      for: .unknown
    )
    try require(
      unknownTerminal.persistedOutcome == .outcomeUnknown
        && unknownTerminal.persistedOutcome.preventsRepeat
        && unknownTerminal.publicOutcome == "unknown",
      "unknown MessageUI result did not preserve repeat suppression"
    )

    var events: [String] = []
    var persistenceCompletion: ((Bool) -> Void)?
    var dismissalCompletion: (() -> Void)?
    var projectedOutcome: String?
    IOSComposerDelegateTerminalSequencer.finish(
      result: .reportedSent,
      persist: { outcome, completion in
        events.append("persist:\(outcome.rawValue)")
        persistenceCompletion = completion
      },
      dismiss: { completion in
        events.append("dismiss")
        dismissalCompletion = completion
      },
      completion: { outcome in
        events.append("resolve:\(outcome)")
        projectedOutcome = outcome
      }
    )
    try require(
      events == ["persist:reported-sent"],
      "MessageUI terminal handling did not persist before dismissal"
    )
    persistenceCompletion?(false)
    try require(
      events == ["persist:reported-sent", "dismiss"],
      "MessageUI terminal handling resolved before dismissal"
    )
    dismissalCompletion?()
    try require(
      projectedOutcome == "unknown",
      "ambiguous terminal persistence did not resolve Unknown"
    )

    events.removeAll()
    projectedOutcome = nil
    IOSComposerDelegateTerminalSequencer.finish(
      result: .cancelled,
      persist: { outcome, completion in
        events.append("persist:\(outcome.rawValue)")
        completion(true)
      },
      dismiss: { completion in
        events.append("dismiss")
        completion()
      },
      completion: { outcome in
        events.append("resolve:\(outcome)")
        projectedOutcome = outcome
      }
    )
    try require(
      events == ["persist:cancelled", "dismiss", "resolve:cancelled"],
      "definitive cancellation did not preserve terminal ordering"
    )
    try require(
      projectedOutcome == "cancelled",
      "persisted cancellation projected the wrong public outcome"
    )

    print("iOS native boundary policy tests passed")
  }
}
