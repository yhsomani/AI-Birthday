import Foundation

@main
enum ComposerReservationPolicyTests {
  private enum StubStoreError: Error {
    case storageUnavailable
  }

  static func main() {
    let now = Date(timeIntervalSince1970: 1_800_000_000)
    precondition(
      IOSComposerReservationPruningPolicy.shouldPruneForDifferentBinding(
        phase: .prepared,
        expiresAt: nil,
        observedAt: now
      ),
      "nil-expiry prepared crash capability was not pruned"
    )
    precondition(
      IOSComposerReservationPruningPolicy.shouldPruneForDifferentBinding(
        phase: .prepared,
        expiresAt: now,
        observedAt: now
      ),
      "logically expired capability was not pruned"
    )
    precondition(
      !IOSComposerReservationPruningPolicy.shouldPruneForDifferentBinding(
        phase: .prepared,
        expiresAt: now.addingTimeInterval(1),
        observedAt: now
      ),
      "live prepared capability was pruned"
    )
    precondition(
      !IOSComposerReservationPruningPolicy.shouldPruneForDifferentBinding(
        phase: .sticky,
        expiresAt: now.addingTimeInterval(1),
        observedAt: now
      ),
      "live sticky capability was pruned"
    )
    precondition(
      IOSComposerTerminalPersistencePolicy.disposition(
        for: Result<Void, StubStoreError>.success(())
      ) == .definitiveFailure,
      "durably persisted composer failure was not definitive"
    )
    precondition(
      IOSComposerTerminalPersistencePolicy.disposition(
        for: Result<Void, StubStoreError>.failure(.storageUnavailable)
      ) == .outcomeUnknown,
      "storage-unavailable composer terminal write did not become Unknown"
    )
    print("IOS_COMPOSER_RESERVATION_POLICY_OK")
  }
}
