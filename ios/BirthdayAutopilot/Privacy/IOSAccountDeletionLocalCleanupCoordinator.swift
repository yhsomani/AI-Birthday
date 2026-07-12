import Foundation

enum IOSAccountDeletionRemoteCheck {
  case inProgress(IOSAccountDeletionReceipt)
  case completed(IOSAccountDeletionReceipt)
  case remoteUnknown(
    IOSAccountDeletionReceipt,
    sameAccountRetryAvailable: Bool
  )
  case unavailable
}

/// Resumes the destructive local half of either an accepted backend deletion or
/// a separately reviewed local wipe whose server acceptance remains ambiguous.
/// A content-free receipt is written before this coordinator runs; ambiguous
/// work also has an equality-only native recovery journal, so every crash
/// boundary remains honest without retaining a raw account identifier.
@MainActor
final class IOSAccountDeletionLocalCleanupCoordinator {
  static let shared = IOSAccountDeletionLocalCleanupCoordinator()

  private let receiptStore = IOSAccountDeletionReceiptStore.shared
  private let recoveryStore = IOSAccountDeletionRecoveryStore.shared
  private let receiptClient = IOSAccountDeletionReceiptClient.shared
  private let reminderCoordinator = CompanionReminderCoordinator.shared
  private let identity = IOSGoogleIdentityCoordinator.shared
  private var running = false
  private var completions: [(IOSAccountDeletionReceipt?) -> Void] = []
  private var statusCheckRunning = false
  private var statusCheckOwnsIdentityLease = false
  private var statusCompletions: [(IOSAccountDeletionRemoteCheck) -> Void] = []

  private init() {}

  func resumeIfNeeded() {
    if let receipt = receiptStore.current() {
      if receipt.remoteDeletionComplete {
        if let recovery = recoveryStore.current() {
          if recovery.operationId == receipt.operationId {
            recoveryStore.clearAfterRemoteCompletion(
              operationId: receipt.operationId,
              completion: { _ in }
            )
          }
        }
        return
      }
      if receipt.localDataErased {
        checkRemoteCompletion(completion: { _ in })
        return
      }
      finishLocalCleanup(operationId: receipt.operationId, completion: { _ in })
      return
    }
    if let recovery = recoveryStore.current() {
      // A crash can occur after the reviewed recovery journal commits but
      // before the content-free receipt file commits. The journal itself is
      // sufficient proof of the one-time native review, so recreate only the
      // matching receipt and continue the already-authorized local teardown.
      receiptStore.recordPending(operationId: recovery.operationId) {
        [weak self] persisted in
        guard let self, persisted else { return }
        self.finishLocalCleanup(operationId: recovery.operationId) { _ in }
      }
      return
    }
    guard receiptStore.hasPendingOrUnreadableReceipt() else { return }
    // Structural corruption remains a visible support/recovery state. Never
    // mint a replacement bearer or wipe a potentially newer signed-in account.
  }

  func checkRemoteCompletion(
    completion: @escaping (IOSAccountDeletionRemoteCheck) -> Void
  ) {
    guard let receipt = receiptStore.current() else {
      if let recovery = recoveryStore.current() {
        receiptStore.recordPending(operationId: recovery.operationId) {
          [weak self] persisted in
          guard let self, persisted else {
            completion(.unavailable)
            return
          }
          self.checkRemoteCompletion(completion: completion)
        }
        return
      }
      completion(.unavailable)
      return
    }
    if !receipt.localDataErased {
      finishLocalCleanup(operationId: receipt.operationId) { [weak self] updated in
        guard let self, updated?.localDataErased == true else {
          completion(.unavailable)
          return
        }
        self.checkRemoteCompletion(completion: completion)
      }
      return
    }
    if receipt.remoteDeletionComplete {
      guard let recovery = recoveryStore.current() else {
        completion(.completed(receipt))
        return
      }
      guard recovery.operationId == receipt.operationId else {
        completion(.unavailable)
        return
      }
      recoveryStore.clearAfterRemoteCompletion(operationId: receipt.operationId) {
        cleared in
        completion(cleared ? .completed(receipt) : .unavailable)
      }
      return
    }
    statusCompletions.append(completion)
    guard !statusCheckRunning else { return }
    statusCheckRunning = true
    guard identity.beginSignedOutDeletionReceiptLookup() else {
      finishStatusCheck(.unavailable)
      return
    }
    statusCheckOwnsIdentityLease = true
    receiptClient.check(receiptId: receipt.operationId) { [weak self] result in
      guard let self else { return }
      switch result {
      case .success(.inProgress):
        self.finishRemoteAcceptance(
          operationId: receipt.operationId,
          result: .inProgress(receipt)
        )
      case .failure(.signedOutRequired), .failure(.configuration):
        self.finishStatusCheck(.unavailable)
      case .success(.notFound), .failure(.networkOffline),
        .failure(.responseInvalid), .failure(.unavailable):
        guard self.recoveryStore.isAwaitingRemoteAcceptance(
          operationId: receipt.operationId
        ) else {
          self.finishStatusCheck(.unavailable)
          return
        }
        self.recoveryStore.markRetryAuthorized(
          operationId: receipt.operationId
        ) { [weak self] persisted in
          self?.finishStatusCheck(
            .remoteUnknown(
              receipt,
              sameAccountRetryAvailable: persisted
            ))
        }
      case .success(.completed(let completedAt)):
        self.receiptStore.markRemoteDeletionComplete(
          operationId: receipt.operationId,
          completedAt: completedAt
        ) { [weak self] persisted in
          guard let self else { return }
          guard persisted, let completed = self.receiptStore.current(),
            completed.remoteDeletionComplete
          else {
            self.finishStatusCheck(.unavailable)
            return
          }
          NotificationCenter.default.post(
            name: .companionProtectedStoreDidChange,
            object: nil,
            userInfo: ["revision": "0"]
          )
          self.finishRemoteCompletion(
            operationId: receipt.operationId,
            result: .completed(completed)
          )
        }
      }
    }
  }

  func finishLocalCleanup(
    operationId: String,
    completion: @escaping (IOSAccountDeletionReceipt?) -> Void
  ) {
    guard let receipt = receiptStore.current(), receipt.operationId == operationId else {
      completion(nil)
      return
    }
    if receipt.localDataErased {
      completion(receipt)
      return
    }
    completions.append(completion)
    guard !running else { return }
    running = true

    CompanionMessageModule.beginAccountDeletionShutdown { [weak self] in
      guard let self else { return }
      self.reminderCoordinator.cancelAppOwnedNotifications {
        Task { @MainActor in
          let identityCleared = await self.identity.completeAccountDeletionLocalShutdown()
          guard identityCleared else {
            self.finish(receipt: self.receiptStore.current())
            return
          }
          self.reminderCoordinator.destroyCompanionDataAfterAccountDeletion {
            [weak self] result in
            guard let self else { return }
            guard result["kind"] as? String == "ok" else {
              self.finish(receipt: self.receiptStore.current())
              return
            }
            guard self.identity.deletionSDKSessionIsAbsent() else {
              self.finish(receipt: self.receiptStore.current())
              return
            }
            self.receiptStore.markLocalDataErased(operationId: operationId) {
              [weak self] _ in
              guard let self else { return }
              self.finish(receipt: self.receiptStore.current())
            }
          }
        }
      }
    }
  }

  private func finish(receipt: IOSAccountDeletionReceipt?) {
    running = false
    let callbacks = completions
    completions.removeAll()
    callbacks.forEach { $0(receipt) }
  }

  private func finishStatusCheck(_ result: IOSAccountDeletionRemoteCheck) {
    statusCheckRunning = false
    if statusCheckOwnsIdentityLease {
      identity.finishSignedOutDeletionReceiptLookup()
      statusCheckOwnsIdentityLease = false
    }
    let callbacks = statusCompletions
    statusCompletions.removeAll()
    callbacks.forEach { $0(result) }
  }

  private func finishRemoteAcceptance(
    operationId: String,
    result: IOSAccountDeletionRemoteCheck
  ) {
    guard recoveryStore.isAwaitingRemoteAcceptance(operationId: operationId) else {
      finishStatusCheck(result)
      return
    }
    recoveryStore.markRemoteAcceptanceConfirmed(operationId: operationId) {
      [weak self] persisted in
      self?.finishStatusCheck(persisted ? result : .unavailable)
    }
  }

  private func finishRemoteCompletion(
    operationId: String,
    result: IOSAccountDeletionRemoteCheck
  ) {
    guard recoveryStore.current()?.operationId == operationId else {
      finishStatusCheck(result)
      return
    }
    recoveryStore.clearAfterRemoteCompletion(operationId: operationId) {
      [weak self] cleared in
      self?.finishStatusCheck(cleared ? result : .unavailable)
    }
  }
}
