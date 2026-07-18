import Foundation

enum IOSComposerReservationLocalPhase: String, Codable {
  case prepared
  case sticky
}

enum IOSComposerReservationOperationKind: Equatable {
  case acquire
  case commit
  case release
}

struct IOSComposerReservationOperationToken: Equatable {
  let id: UUID
  let kind: IOSComposerReservationOperationKind
  let reservationId: String
}

/// Serializes the three capability operations. In particular, an asynchronous
/// PREPARED release must finish before the same UUID can be acquired again;
/// otherwise the old exact RELEASED callback could clear a freshly reused local
/// capability (an ABA race) and leave its new server reservation ownerless.
struct IOSComposerReservationOperationGate {
  private var active: IOSComposerReservationOperationToken?

  mutating func begin(
    _ kind: IOSComposerReservationOperationKind,
    reservationId: String
  ) -> IOSComposerReservationOperationToken? {
    guard active == nil else { return nil }
    let token = IOSComposerReservationOperationToken(
      id: UUID(),
      kind: kind,
      reservationId: reservationId
    )
    active = token
    return token
  }

  @discardableResult
  mutating func finish(_ token: IOSComposerReservationOperationToken) -> Bool {
    guard active == token else { return false }
    active = nil
    return true
  }
}

/// Pure pruning policy. Local capability loss cannot release a server fence;
/// it can only make the next acquire fail closed until server logical expiry.
enum IOSComposerReservationPruningPolicy {
  static func shouldPruneForDifferentBinding(
    phase: IOSComposerReservationLocalPhase,
    expiresAt: Date?,
    observedAt: Date
  ) -> Bool {
    (phase == .prepared && expiresAt == nil)
      || expiresAt.map({ $0 <= observedAt }) == true
  }
}

enum IOSComposerTerminalPersistenceDisposition: Equatable {
  case definitiveFailure
  case outcomeUnknown
}

/// Once a composer-open operation is committed, a definitive cancellation or
/// failure is truthful only after the protected terminal ledger persists it.
/// Any persistence error leaves the prior repeat-blocking marker authoritative
/// and must therefore be surfaced as outcome-Unknown.
enum IOSComposerTerminalPersistencePolicy {
  static func disposition<Failure: Error>(
    for result: Result<Void, Failure>
  ) -> IOSComposerTerminalPersistenceDisposition {
    switch result {
    case .success:
      return .definitiveFailure
    case .failure:
      return .outcomeUnknown
    }
  }
}
