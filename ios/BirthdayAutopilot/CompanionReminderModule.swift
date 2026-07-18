import Foundation
import React
import UIKit
import UserNotifications

private enum CompanionReminderAuthorization: String {
  case authorized
  case denied
  case ephemeral
  case notDetermined = "not-determined"
  case provisional
  case unknown

  var permitsScheduling: Bool {
    switch self {
    case .authorized, .ephemeral, .provisional:
      return true
    case .denied, .notDetermined, .unknown:
      return false
    }
  }
}

private struct CompanionReminderReconciliation {
  let authorization: CompanionReminderAuthorization
  let plannedDateCount: Int
  let scheduledCount: Int
  let truncated: Bool
  let failedCount: Int
  let earliestUnscheduledCivilDate: String?

  func dictionary(kind: String = "ok", code: String? = nil) -> [String: Any] {
    var result: [String: Any] = [
      "authorization": authorization.rawValue,
      "failedCount": failedCount,
      "kind": kind,
      "plannedDateCount": plannedDateCount,
      "scheduledCount": scheduledCount,
      "truncated": truncated,
    ]
    if let code {
      result["code"] = code
    }
    if let earliestUnscheduledCivilDate {
      result["earliestUnscheduledCivilDate"] = earliestUnscheduledCivilDate
    }
    return result
  }
}

private struct QueuedReminderReconciliation {
  let generation: UInt64
  let schedule: CompanionReminderSchedule
  let completion: (([String: Any]) -> Void)?
}

private final class CompanionReminderDrainGate: @unchecked Sendable {
  private let lock = NSLock()
  private var completed = false

  func claim() -> Bool {
    lock.lock()
    defer { lock.unlock() }
    guard !completed else { return false }
    completed = true
    return true
  }
}

protocol IOSCompanionNotificationCenterClient: AnyObject {
  func authorizationStatus(
    completion: @escaping (UNAuthorizationStatus) -> Void
  )
  func requestAuthorization(
    options: UNAuthorizationOptions,
    completion: @escaping (Bool, Error?) -> Void
  )
  func pendingRequests(
    completion: @escaping ([UNNotificationRequest]) -> Void
  )
  func deliveredRequests(
    completion: @escaping ([UNNotificationRequest]) -> Void
  )
  func add(
    _ request: UNNotificationRequest,
    completion: ((Error?) -> Void)?
  )
  func removePending(withIdentifiers identifiers: [String])
  func removeDelivered(withIdentifiers identifiers: [String])
}

final class IOSSystemCompanionNotificationCenterClient:
  IOSCompanionNotificationCenterClient
{
  private let center: UNUserNotificationCenter

  init(center: UNUserNotificationCenter = .current()) {
    self.center = center
  }

  func authorizationStatus(
    completion: @escaping (UNAuthorizationStatus) -> Void
  ) {
    center.getNotificationSettings { completion($0.authorizationStatus) }
  }

  func requestAuthorization(
    options: UNAuthorizationOptions,
    completion: @escaping (Bool, Error?) -> Void
  ) {
    center.requestAuthorization(options: options, completionHandler: completion)
  }

  func pendingRequests(
    completion: @escaping ([UNNotificationRequest]) -> Void
  ) {
    center.getPendingNotificationRequests(completionHandler: completion)
  }

  func deliveredRequests(
    completion: @escaping ([UNNotificationRequest]) -> Void
  ) {
    center.getDeliveredNotifications { notifications in
      completion(notifications.map(\.request))
    }
  }

  func add(
    _ request: UNNotificationRequest,
    completion: ((Error?) -> Void)?
  ) {
    center.add(request, withCompletionHandler: completion)
  }

  func removePending(withIdentifiers identifiers: [String]) {
    center.removePendingNotificationRequests(withIdentifiers: identifiers)
  }

  func removeDelivered(withIdentifiers identifiers: [String]) {
    center.removeDeliveredNotifications(withIdentifiers: identifiers)
  }
}

protocol IOSCompanionReminderStore: AnyObject {
  func readReminderSchedule(
    completion: @escaping (
      Result<CompanionReminderSchedule, CompanionStoreError>
    ) -> Void
  )
  func replaceReminderPlans(
    _ plans: [CompanionReminderPlan],
    completion: @escaping (Result<Void, CompanionStoreError>) -> Void
  )
  func recordReminderHorizon(
    _ horizon: CompanionReminderHorizon,
    completion: @escaping (Result<Void, CompanionStoreError>) -> Void
  )
  func wipeAndInstallResetSafety(
    completion: @escaping (Result<Void, CompanionStoreError>) -> Void
  )
  func destroyAfterRemoteAccountDeletion(
    completion: @escaping (Result<Void, CompanionStoreError>) -> Void
  )
}

extension CompanionProtectedStore: IOSCompanionReminderStore {}

protocol IOSCompanionAttentionNotifying: AnyObject {
  func notify(_ kind: IOSCompanionAttentionKind)
  func beginCancellationDrain(_ completion: @escaping (Bool) -> Void)
  func endCancellationDrain()
}

extension IOSCompanionAttentionNotifier: IOSCompanionAttentionNotifying {}

final class CompanionReminderCoordinator {
  static let shared = CompanionReminderCoordinator(
    center: IOSSystemCompanionNotificationCenterClient(),
    store: CompanionProtectedStore.shared,
    attentionNotifier: IOSCompanionAttentionNotifier.shared
  )

  private static let identifierPrefix = "birthday-autopilot.reminder.v1."
  private static let maximumScheduledDateCount = 60

  private let center: IOSCompanionNotificationCenterClient
  private let store: IOSCompanionReminderStore
  private let attentionNotifier: IOSCompanionAttentionNotifying
  private let queue = DispatchQueue(
    label: "com.yashsomani.birthdayautopilot.companion-reminders",
    qos: .utility
  )
  private let stateLock = NSLock()
  private var reconciliationGeneration: UInt64 = 0
  private var activeReconciliationGeneration: UInt64?
  private var queuedReconciliation: QueuedReminderReconciliation?
  private var wipeInProgress = false
  private var inFlightNotificationAdds = 0
  private var addDrainWaiters: [() -> Void] = []

  /// Internal dependency seam for the hosted native test target. The only
  /// production instance remains `shared`, wired to system adapters above.
  init(
    center: IOSCompanionNotificationCenterClient,
    store: IOSCompanionReminderStore,
    attentionNotifier: IOSCompanionAttentionNotifying
  ) {
    self.center = center
    self.store = store
    self.attentionNotifier = attentionNotifier
  }

  func requestAuthorizationAndReconcile(
    completion: @escaping ([String: Any]) -> Void
  ) {
    guard !isWiping else {
      completion(Self.errorDictionary(code: "COMPANION_WIPE_IN_PROGRESS"))
      return
    }
    center.authorizationStatus { [weak self] authorizationStatus in
      guard let self else {
        DispatchQueue.main.async {
          completion(Self.errorDictionary(code: "REMINDER_MODULE_UNAVAILABLE"))
        }
        return
      }
      // iOS never prompts again after denial. Return a settings-required state
      // so the UI can offer the explicit Settings action instead of repeatedly
      // calling requestAuthorization.
      guard authorizationStatus != .denied else {
        let denied = CompanionReminderReconciliation(
          authorization: .denied,
          plannedDateCount: 0,
          scheduledCount: 0,
          truncated: false,
          failedCount: 0,
          earliestUnscheduledCivilDate: nil
        ).dictionary(kind: "error", code: "REMINDER_SETTINGS_REQUIRED")
        DispatchQueue.main.async { completion(denied) }
        return
      }
      self.center.requestAuthorization(options: [.alert, .sound]) { [weak self] _, _ in
        guard let self else {
          DispatchQueue.main.async {
            completion(Self.errorDictionary(code: "REMINDER_MODULE_UNAVAILABLE"))
          }
          return
        }
        self.reconcilePersisted(completion: completion)
      }
    }
  }

  func openNotificationSettings(
    completion: @escaping ([String: Any]) -> Void
  ) {
    center.authorizationStatus { authorizationStatus in
      guard authorizationStatus == .denied else {
        DispatchQueue.main.async {
          completion(["code": "REMINDER_SETTINGS_NOT_REQUIRED", "kind": "error"])
        }
        return
      }
      DispatchQueue.main.async {
        guard UIApplication.shared.applicationState == .active,
          let url = URL(string: UIApplication.openSettingsURLString)
        else {
          completion(["code": "REMINDER_FOREGROUND_REQUIRED", "kind": "error"])
          return
        }
        UIApplication.shared.open(url, options: [:]) { opened in
          completion(opened
            ? ["kind": "ok"]
            : ["code": "REMINDER_SETTINGS_OPEN_FAILED", "kind": "error"])
        }
      }
    }
  }

  func reconcilePersisted(
    completion: (([String: Any]) -> Void)? = nil
  ) {
    guard !isWiping else {
      completion?(Self.errorDictionary(code: "COMPANION_WIPE_IN_PROGRESS"))
      return
    }
    store.readReminderSchedule { [weak self] result in
      guard let self else {
        completion?(Self.errorDictionary(code: "REMINDER_MODULE_UNAVAILABLE"))
        return
      }
      switch result {
      case .failure(let error):
        completion?(Self.errorDictionary(code: error.safeCode))
      case .success(let schedule):
        self.reconcile(schedule: schedule, completion: completion)
      }
    }
  }

  func status(completion: @escaping ([String: Any]) -> Void) {
    guard !isWiping else {
      completion(Self.errorDictionary(code: "COMPANION_WIPE_IN_PROGRESS"))
      return
    }
    store.readReminderSchedule { [weak self] result in
      guard let self else {
        completion(Self.errorDictionary(code: "REMINDER_MODULE_UNAVAILABLE"))
        return
      }
      switch result {
      case .failure(let error):
        completion(Self.errorDictionary(code: error.safeCode))
      case .success(let schedule):
        self.center.authorizationStatus { authorizationStatus in
          self.center.pendingRequests { pending in
            let authorization = Self.authorization(from: authorizationStatus)
            let ownedCount = pending.filter {
              $0.identifier.hasPrefix(Self.identifierPrefix)
            }.count
            let plannedDateCount = Set(schedule.plans.map(\.civilDate)).count
            let observedIds = Set(
              pending.map(\.identifier).filter {
                $0.hasPrefix(Self.identifierPrefix)
              }.map { String($0.dropFirst(Self.identifierPrefix.count)) }
            )
            let recordedIds = Set(schedule.horizon?.observedRequestIds ?? [])
            let horizonPartial =
              (plannedDateCount > 0 && schedule.horizon == nil)
              || schedule.horizon?.state == .partial || observedIds != recordedIds
              || ownedCount > Self.maximumScheduledDateCount
            let status = CompanionReminderReconciliation(
              authorization: authorization,
              plannedDateCount: plannedDateCount,
              scheduledCount: min(ownedCount, Self.maximumScheduledDateCount),
              truncated: plannedDateCount > Self.maximumScheduledDateCount,
              failedCount: horizonPartial ? 1 : 0,
              earliestUnscheduledCivilDate: schedule.horizon?.earliestUnscheduledCivilDate
            ).dictionary(
              kind: horizonPartial ? "error" : "ok",
              code: horizonPartial ? "REMINDER_HORIZON_PARTIAL" : nil
            )
            DispatchQueue.main.async { completion(status) }
          }
        }
      }
    }
  }

  func cancelAppOwnedNotifications(completion: ((Bool) -> Void)? = nil) {
    invalidateActiveReconciliationForCancellation()
    waitForNotificationAddsToDrain { [weak self] drained in
      guard let self else {
        DispatchQueue.main.async { completion?(false) }
        return
      }
      guard drained else {
        DispatchQueue.main.async { completion?(false) }
        return
      }
      self.attentionNotifier.beginCancellationDrain { attentionDrained in
        guard attentionDrained else {
          self.attentionNotifier.endCancellationDrain()
          DispatchQueue.main.async { completion?(false) }
          return
        }
        self.removeAndVerifyAppOwnedNotifications(attemptsRemaining: 3) {
          verified in
          self.attentionNotifier.endCancellationDrain()
          DispatchQueue.main.async { completion?(verified) }
        }
      }
    }
  }

  func cancelPlansAndNotifications(completion: @escaping ([String: Any]) -> Void) {
    guard beginCancellation() else {
      completion(["code": "COMPANION_WIPE_IN_PROGRESS", "kind": "error"])
      return
    }
    store.replaceReminderPlans([]) { [weak self] result in
      guard let self else {
        completion(["code": "REMINDER_MODULE_UNAVAILABLE", "kind": "error"])
        return
      }
      switch result {
      case .failure(let error):
        completion(["code": error.safeCode, "kind": "error"])
      case .success:
        self.cancelAppOwnedNotifications { verified in
          guard verified else {
            completion(["code": "REMINDER_CANCELLATION_UNVERIFIED", "kind": "error"])
            return
          }
          let horizon = CompanionReminderHorizon(
            generation: UUID().uuidString.lowercased(),
            state: .denied,
            observedRequestIds: [],
            earliestUnscheduledCivilDate: nil,
            reconciledAt: Date()
          )
          self.store.recordReminderHorizon(horizon) { horizonResult in
            switch horizonResult {
            case .success:
              completion(["kind": "ok"])
            case .failure(let error):
              completion(["code": error.safeCode, "kind": "error"])
            }
          }
        }
      }
    }
  }

  func wipeCompanionData(completion: @escaping ([String: Any]) -> Void) {
    guard beginWipe() else {
      completion(["code": "COMPANION_WIPE_IN_PROGRESS", "kind": "error"])
      return
    }
    cancelAppOwnedNotifications { [store] cancelled in
      guard cancelled else {
        self.endWipe()
        completion(["code": "REMINDER_CANCELLATION_UNVERIFIED", "kind": "error"])
        return
      }
      store.wipeAndInstallResetSafety { result in
        self.cancelAppOwnedNotifications { verified in
          self.endWipe()
          guard verified else {
            completion(["code": "REMINDER_CANCELLATION_UNVERIFIED", "kind": "error"])
            return
          }
          switch result {
          case .success:
            completion(["kind": "ok"])
          case .failure(let error):
            completion(["code": error.safeCode, "kind": "error"])
          }
        }
      }
    }
  }

  /// Runs only after the authenticated backend has accepted an account
  /// deletion tombstone. It removes pending/delivered app reminders around the
  /// final protected-store and Keychain destruction without installing a new
  /// local reset generation.
  func destroyCompanionDataAfterAccountDeletion(
    completion: @escaping ([String: Any]) -> Void
  ) {
    guard beginWipe() else {
      completion(["code": "COMPANION_WIPE_IN_PROGRESS", "kind": "error"])
      return
    }
    cancelAppOwnedNotifications { [store] cancelled in
      guard cancelled else {
        self.endWipe()
        completion(["code": "REMINDER_CANCELLATION_UNVERIFIED", "kind": "error"])
        return
      }
      store.destroyAfterRemoteAccountDeletion { result in
        self.cancelAppOwnedNotifications { verified in
          self.endWipe()
          guard verified else {
            completion(["code": "REMINDER_CANCELLATION_UNVERIFIED", "kind": "error"])
            return
          }
          switch result {
          case .success:
            completion(["kind": "ok"])
          case .failure(let error):
            completion(["code": error.safeCode, "kind": "error"])
          }
        }
      }
    }
  }

  private func reconcile(
    schedule: CompanionReminderSchedule,
    completion: (([String: Any]) -> Void)?
  ) {
    let item: QueuedReminderReconciliation
    var replacedCompletion: (([String: Any]) -> Void)?
    var shouldStart = false
    stateLock.lock()
    if wipeInProgress {
      stateLock.unlock()
      completion?(Self.errorDictionary(code: "COMPANION_WIPE_IN_PROGRESS"))
      return
    }
    reconciliationGeneration &+= 1
    item = QueuedReminderReconciliation(
      generation: reconciliationGeneration,
      schedule: schedule,
      completion: completion
    )
    if activeReconciliationGeneration == nil {
      activeReconciliationGeneration = item.generation
      shouldStart = true
    } else {
      replacedCompletion = queuedReconciliation?.completion
      queuedReconciliation = item
    }
    stateLock.unlock()

    if let replacedCompletion {
      completeOnMain(
        replacedCompletion,
        result: Self.errorDictionary(code: "REMINDER_RECONCILIATION_SUPERSEDED")
      )
    }
    if shouldStart {
      runReconciliation(item)
    }
  }

  private func runReconciliation(_ item: QueuedReminderReconciliation) {
    let generation = item.generation
    let schedule = item.schedule
    let completion = item.completion
    center.authorizationStatus { [weak self] authorizationStatus in
      guard let self else {
        DispatchQueue.main.async {
          completion?(Self.errorDictionary(code: "REMINDER_MODULE_UNAVAILABLE"))
        }
        return
      }
      guard self.isCurrentReconciliation(generation) else {
        self.finishReconciliation(
          completion,
          generation: generation,
          result: Self.errorDictionary(code: "REMINDER_RECONCILIATION_SUPERSEDED")
        )
        return
      }
      let authorization = Self.authorization(from: authorizationStatus)
      self.queue.async {
        guard self.isCurrentReconciliation(generation) else {
          self.finishReconciliation(
            completion,
            generation: generation,
            result: Self.errorDictionary(code: "REMINDER_RECONCILIATION_SUPERSEDED")
          )
          return
        }
        let candidates = Self.notificationCandidates(from: schedule, now: Date())
        let plannedDateCount = candidates.count
        let bounded = Array(candidates.prefix(Self.maximumScheduledDateCount))
        let truncated = plannedDateCount > bounded.count

        guard authorization.permitsScheduling else {
          self.removeAllPendingAppOwnedRequests {
            self.center.pendingRequests { observed in
              guard self.isCurrentReconciliation(generation) else {
                self.finishReconciliation(
                  completion,
                  generation: generation,
                  result: Self.errorDictionary(code: "REMINDER_RECONCILIATION_SUPERSEDED")
                )
                return
              }
              let remainingOwned = observed.map(\.identifier).filter {
                $0.hasPrefix(Self.identifierPrefix)
              }
              let horizon = CompanionReminderHorizon(
                generation: String(generation),
                state: remainingOwned.isEmpty ? .denied : .partial,
                observedRequestIds: remainingOwned.map {
                  String($0.dropFirst(Self.identifierPrefix.count))
                }.sorted(),
                earliestUnscheduledCivilDate: candidates.first?.civilDate,
                reconciledAt: Date()
              )
              self.store.recordReminderHorizon(horizon) { persistenceResult in
                guard self.isCurrentReconciliation(generation) else {
                  self.finishReconciliation(
                    completion,
                    generation: generation,
                    result: Self.errorDictionary(
                      code: "REMINDER_RECONCILIATION_SUPERSEDED"
                    )
                  )
                  return
                }
                let persistenceFailed: Bool
                if case .failure = persistenceResult {
                  persistenceFailed = true
                } else {
                  persistenceFailed = false
                }
                let state = CompanionReminderReconciliation(
                  authorization: authorization,
                  plannedDateCount: plannedDateCount,
                  scheduledCount: 0,
                  truncated: truncated,
                  failedCount: min(remainingOwned.count, Self.maximumScheduledDateCount),
                  earliestUnscheduledCivilDate: candidates.first?.civilDate
                )
                self.finishReconciliation(
                  completion,
                  generation: generation,
                  result: state.dictionary(
                    kind: remainingOwned.isEmpty && !persistenceFailed ? "ok" : "error",
                    code: remainingOwned.isEmpty && !persistenceFailed
                      ? nil : "REMINDER_HORIZON_PARTIAL"
                  )
                )
              }
            }
          }
          return
        }

        let requests = bounded.compactMap(Self.makeNotificationRequest)
        let desiredIdentifiers = Set(requests.map(\.identifier))
        // Free the app-owned quota before adding replacements. Adding a new
        // sixty-request horizon while an old sixty-request horizon is still
        // pending can make every add fail at the system capacity boundary.
        self.removeStaleOwnedRequestsBeforeAdding(
          desiredIdentifiers: desiredIdentifiers,
          generation: generation
        ) { staleRemoved in
          guard self.isCurrentReconciliation(generation) else {
            self.finishReconciliation(
              completion,
              generation: generation,
              result: Self.errorDictionary(code: "REMINDER_RECONCILIATION_SUPERSEDED")
            )
            return
          }
          guard staleRemoved else {
            self.attentionNotifier.notify(.reminders)
            self.finishReconciliation(
              completion,
              generation: generation,
              result: Self.errorDictionary(code: "REMINDER_HORIZON_PARTIAL")
            )
            return
          }

          let group = DispatchGroup()
          let failureLock = NSLock()
          var addFailureCount = 0
          for request in requests {
            guard self.registerNotificationAdd(generation: generation) else { break }
            group.enter()
            self.center.add(request) { error in
              if error != nil {
                failureLock.lock()
                addFailureCount += 1
                failureLock.unlock()
              }
              // A superseding generation owns a different desired set. Remove
              // any late completion from this generation before releasing its
              // in-flight registration.
              if !self.isCurrentReconciliation(generation) {
                self.center.removePending(withIdentifiers: [request.identifier])
                self.center.removeDelivered(withIdentifiers: [request.identifier])
              }
              self.finishNotificationAdd()
              group.leave()
            }
          }
          group.notify(queue: self.queue) {
            guard self.isCurrentReconciliation(generation) else {
              self.finishReconciliation(
                completion,
                generation: generation,
                result: Self.errorDictionary(code: "REMINDER_RECONCILIATION_SUPERSEDED")
              )
              return
            }
            // First post-add observation removes any unexpected late request;
            // the following query alone is authoritative for the horizon.
            self.center.pendingRequests { firstObserved in
              guard self.isCurrentReconciliation(generation) else {
                self.finishReconciliation(
                  completion,
                  generation: generation,
                  result: Self.errorDictionary(code: "REMINDER_RECONCILIATION_SUPERSEDED")
                )
                return
              }
              let firstOwnedIdentifiers = Set(
                firstObserved.map(\.identifier).filter {
                  $0.hasPrefix(Self.identifierPrefix)
                }
              )
              let unexpectedFirst = firstOwnedIdentifiers.subtracting(
                desiredIdentifiers
              )
              self.center.removePending(withIdentifiers: Array(unexpectedFirst))

              self.center.pendingRequests { finalObserved in
                guard self.isCurrentReconciliation(generation) else {
                  self.finishReconciliation(
                    completion,
                    generation: generation,
                    result: Self.errorDictionary(code: "REMINDER_RECONCILIATION_SUPERSEDED")
                  )
                  return
                }
                let finalOwnedIdentifiers = Set(
                  finalObserved.map(\.identifier).filter {
                    $0.hasPrefix(Self.identifierPrefix)
                  }
                )
                let missingDesired = desiredIdentifiers.subtracting(
                  finalOwnedIdentifiers
                )
                let unexpected = finalOwnedIdentifiers.subtracting(desiredIdentifiers)
                let full = addFailureCount == 0 && missingDesired.isEmpty
                  && unexpected.isEmpty
                let earliestMissing = bounded.first(where: {
                  missingDesired.contains(Self.identifierPrefix + $0.requestId)
                })?.civilDate
                let earliestUnscheduled = earliestMissing
                  ?? (truncated
                    ? candidates.dropFirst(bounded.count).first?.civilDate : nil)
                let observedRequestIds = finalOwnedIdentifiers.compactMap { identifier in
                  guard desiredIdentifiers.contains(identifier) else { return nil }
                  return String(identifier.dropFirst(Self.identifierPrefix.count))
                }.sorted()
                let horizon = CompanionReminderHorizon(
                  generation: String(generation),
                  state: full ? .full : .partial,
                  observedRequestIds: observedRequestIds,
                  earliestUnscheduledCivilDate: earliestUnscheduled,
                  reconciledAt: Date()
                )
                self.store.recordReminderHorizon(horizon) { persistenceResult in
                  guard self.isCurrentReconciliation(generation) else {
                    self.finishReconciliation(
                      completion,
                      generation: generation,
                      result: Self.errorDictionary(
                        code: "REMINDER_RECONCILIATION_SUPERSEDED"
                      )
                    )
                    return
                  }
                  let persistenceFailed: Bool
                  if case .failure = persistenceResult {
                    persistenceFailed = true
                  } else {
                    persistenceFailed = false
                  }
                  let failedCount = min(
                    Self.maximumScheduledDateCount,
                    addFailureCount + missingDesired.count + unexpected.count
                  )
                  let state = CompanionReminderReconciliation(
                    authorization: authorization,
                    plannedDateCount: plannedDateCount,
                    scheduledCount: desiredIdentifiers.intersection(
                      finalOwnedIdentifiers
                    ).count,
                    truncated: truncated,
                    failedCount: failedCount,
                    earliestUnscheduledCivilDate: earliestUnscheduled
                  )
                  let successful = full && !persistenceFailed
                  if !successful {
                    self.attentionNotifier.notify(.reminders)
                  }
                  self.finishReconciliation(
                    completion,
                    generation: generation,
                    result: state.dictionary(
                      kind: successful ? "ok" : "error",
                      code: successful ? nil : "REMINDER_HORIZON_PARTIAL"
                    )
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  private func removeStaleOwnedRequestsBeforeAdding(
    desiredIdentifiers: Set<String>,
    generation: UInt64,
    attemptsRemaining: Int = 3,
    completion: @escaping (Bool) -> Void
  ) {
    center.pendingRequests { [weak self] pending in
      guard let self, self.isCurrentReconciliation(generation) else {
        completion(false)
        return
      }
      let ownedIdentifiers = Set(
        pending.map(\.identifier).filter {
          $0.hasPrefix(Self.identifierPrefix)
        }
      )
      let staleIdentifiers = ownedIdentifiers.subtracting(desiredIdentifiers)
      guard !staleIdentifiers.isEmpty else {
        completion(true)
        return
      }
      self.center.removePending(withIdentifiers: Array(staleIdentifiers))
      guard attemptsRemaining > 1 else {
        self.center.pendingRequests { observed in
          guard self.isCurrentReconciliation(generation) else {
            completion(false)
            return
          }
          let remainingStale = observed.map(\.identifier).contains {
            $0.hasPrefix(Self.identifierPrefix)
              && !desiredIdentifiers.contains($0)
          }
          completion(!remainingStale)
        }
        return
      }
      DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
        self.removeStaleOwnedRequestsBeforeAdding(
          desiredIdentifiers: desiredIdentifiers,
          generation: generation,
          attemptsRemaining: attemptsRemaining - 1,
          completion: completion
        )
      }
    }
  }

  private func removeAllPendingAppOwnedRequests(
    completion: @escaping () -> Void
  ) {
    center.pendingRequests { [weak self] pending in
      guard let self else {
        completion()
        return
      }
      let identifiers =
        pending
        .map(\.identifier)
        .filter { $0.hasPrefix(Self.identifierPrefix) }
      self.center.removePending(withIdentifiers: identifiers)
      completion()
    }
  }

  private func invalidateActiveReconciliationForCancellation() {
    var queuedCompletion: (([String: Any]) -> Void)?
    stateLock.lock()
    reconciliationGeneration &+= 1
    activeReconciliationGeneration = nil
    queuedCompletion = queuedReconciliation?.completion
    queuedReconciliation = nil
    stateLock.unlock()
    if let queuedCompletion {
      completeOnMain(
        queuedCompletion,
        result: Self.errorDictionary(code: "REMINDER_RECONCILIATION_SUPERSEDED")
      )
    }
  }

  private func registerNotificationAdd(generation: UInt64) -> Bool {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard !wipeInProgress, activeReconciliationGeneration == generation else {
      return false
    }
    inFlightNotificationAdds += 1
    return true
  }

  private func finishNotificationAdd() {
    var waiters: [() -> Void] = []
    stateLock.lock()
    if inFlightNotificationAdds > 0 { inFlightNotificationAdds -= 1 }
    if inFlightNotificationAdds == 0 {
      waiters = addDrainWaiters
      addDrainWaiters.removeAll()
    }
    stateLock.unlock()
    waiters.forEach { waiter in DispatchQueue.main.async(execute: waiter) }
  }

  private func waitForNotificationAddsToDrain(
    _ completion: @escaping (Bool) -> Void
  ) {
    let gate = CompanionReminderDrainGate()
    let finish: (Bool) -> Void = { drained in
      guard gate.claim() else { return }
      completion(drained)
    }
    stateLock.lock()
    if inFlightNotificationAdds == 0 {
      stateLock.unlock()
      DispatchQueue.main.async { finish(true) }
      return
    }
    addDrainWaiters.append { finish(true) }
    stateLock.unlock()
    DispatchQueue.main.asyncAfter(deadline: .now() + 5) { finish(false) }
  }

  private func removeAndVerifyAppOwnedNotifications(
    attemptsRemaining: Int,
    completion: @escaping (Bool) -> Void
  ) {
    center.pendingRequests { [weak self] pending in
      guard let self else { return completion(false) }
      let pendingIds = pending.map(\.identifier).filter {
        $0.hasPrefix(Self.identifierPrefix)
          || IOSCompanionAttentionNotifier.isAttentionIdentifier($0)
      }
      self.center.removePending(withIdentifiers: pendingIds)
      self.center.deliveredRequests { delivered in
        let deliveredIds = delivered.map(\.identifier).filter {
          $0.hasPrefix(Self.identifierPrefix)
            || IOSCompanionAttentionNotifier.isAttentionIdentifier($0)
        }
        self.center.removeDelivered(withIdentifiers: deliveredIds)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
          self.center.pendingRequests { observedPending in
            self.center.deliveredRequests { observedDelivered in
              let remaining = observedPending.contains {
                $0.identifier.hasPrefix(Self.identifierPrefix)
                  || IOSCompanionAttentionNotifier.isAttentionIdentifier($0.identifier)
              } || observedDelivered.contains {
                $0.identifier.hasPrefix(Self.identifierPrefix)
                  || IOSCompanionAttentionNotifier.isAttentionIdentifier(
                    $0.identifier
                  )
              }
              guard remaining, attemptsRemaining > 1 else {
                completion(!remaining)
                return
              }
              self.removeAndVerifyAppOwnedNotifications(
                attemptsRemaining: attemptsRemaining - 1,
                completion: completion
              )
            }
          }
        }
      }
    }
  }

  private func releaseReconciliation(generation: UInt64) {
    var next: QueuedReminderReconciliation?
    stateLock.lock()
    if activeReconciliationGeneration == generation {
      activeReconciliationGeneration = nil
      if !wipeInProgress, let queued = queuedReconciliation {
        queuedReconciliation = nil
        activeReconciliationGeneration = queued.generation
        next = queued
      }
    }
    stateLock.unlock()
    if let next {
      runReconciliation(next)
    }
  }

  private func finishReconciliation(
    _ itemCompletion: (([String: Any]) -> Void)?,
    generation: UInt64,
    result: [String: Any]
  ) {
    completeOnMain(itemCompletion, result: result)
    releaseReconciliation(generation: generation)
  }

  private func completeOnMain(
    _ completion: (([String: Any]) -> Void)?,
    result: [String: Any]
  ) {
    DispatchQueue.main.async { completion?(result) }
  }

  private var isWiping: Bool {
    stateLock.lock()
    defer { stateLock.unlock() }
    return wipeInProgress
  }

  private func beginCancellation() -> Bool {
    stateLock.lock()
    guard !wipeInProgress else {
      stateLock.unlock()
      return false
    }
    reconciliationGeneration &+= 1
    activeReconciliationGeneration = nil
    let queuedCompletion = queuedReconciliation?.completion
    queuedReconciliation = nil
    stateLock.unlock()
    if let queuedCompletion {
      completeOnMain(
        queuedCompletion,
        result: Self.errorDictionary(code: "REMINDER_RECONCILIATION_SUPERSEDED")
      )
    }
    return true
  }

  private func isCurrentReconciliation(_ generation: UInt64) -> Bool {
    stateLock.lock()
    defer { stateLock.unlock() }
    return !wipeInProgress && activeReconciliationGeneration == generation
  }

  private func beginWipe() -> Bool {
    stateLock.lock()
    guard !wipeInProgress else {
      stateLock.unlock()
      return false
    }
    wipeInProgress = true
    reconciliationGeneration &+= 1
    activeReconciliationGeneration = nil
    let queuedCompletion = queuedReconciliation?.completion
    queuedReconciliation = nil
    stateLock.unlock()
    if let queuedCompletion {
      completeOnMain(
        queuedCompletion,
        result: Self.errorDictionary(code: "COMPANION_WIPE_IN_PROGRESS")
      )
    }
    return true
  }

  private func endWipe() {
    stateLock.lock()
    wipeInProgress = false
    stateLock.unlock()
  }

  private static func notificationCandidates(
    from schedule: CompanionReminderSchedule,
    now: Date
  ) -> [(civilDate: String, fireDate: Date, requestId: String)] {
    let calendar = schedulingCalendar()
    var earliestByCivilDate: [String: Date] = [:]
    for plan in schedule.plans {
      guard let fireDate = fireDate(for: plan, calendar: calendar),
        fireDate > now
      else {
        continue
      }
      if let current = earliestByCivilDate[plan.civilDate] {
        earliestByCivilDate[plan.civilDate] = min(current, fireDate)
      } else {
        earliestByCivilDate[plan.civilDate] = fireDate
      }
    }
    return
      earliestByCivilDate
      .compactMap { civilDate, fireDate in
        guard let requestId = schedule.requestIdByCivilDate[civilDate] else {
          return nil
        }
        return (civilDate: civilDate, fireDate: fireDate, requestId: requestId)
      }
      .sorted {
        if $0.fireDate == $1.fireDate {
          return $0.civilDate < $1.civilDate
        }
        return $0.fireDate < $1.fireDate
      }
  }

  private static func fireDate(
    for plan: CompanionReminderPlan,
    calendar: Calendar
  ) -> Date? {
    guard let dayStart = civilDateStart(plan.civilDate, calendar: calendar),
      let searchStart = calendar.date(byAdding: .second, value: -1, to: dayStart),
      let fireDate = calendar.nextDate(
        after: searchStart,
        matching: DateComponents(hour: plan.hour, minute: plan.minute),
        matchingPolicy: .nextTime,
        repeatedTimePolicy: .first,
        direction: .forward
      ),
      calendar.isDate(fireDate, inSameDayAs: dayStart)
    else {
      return nil
    }
    return fireDate
  }

  private static func civilDateStart(
    _ value: String,
    calendar: Calendar
  ) -> Date? {
    let parts = value.split(separator: "-", omittingEmptySubsequences: false)
    guard parts.count == 3,
      let year = Int(parts[0]),
      let month = Int(parts[1]),
      let day = Int(parts[2])
    else {
      return nil
    }
    let components = DateComponents(
      calendar: calendar,
      timeZone: calendar.timeZone,
      year: year,
      month: month,
      day: day,
      hour: 0,
      minute: 0,
      second: 0
    )
    guard let date = calendar.date(from: components),
      CompanionProtectedStore.civilDate(for: date, calendar: calendar) == value
    else {
      return nil
    }
    return date
  }

  private static func makeNotificationRequest(
    candidate: (civilDate: String, fireDate: Date, requestId: String)
  ) -> UNNotificationRequest? {
    let content = UNMutableNotificationContent()
    if Locale.preferredLanguages.first?.lowercased().hasPrefix("hi") == true {
      content.title = "जन्मदिन रिमाइंडर"
      content.body = "आज के जन्मदिन ड्राफ़्ट की समीक्षा के लिए Birthday Autopilot खोलें।"
    } else {
      content.title = "Birthday reminder"
      content.body = "Open Birthday Autopilot to review today's birthday drafts."
    }
    content.sound = .default
    content.categoryIdentifier = "BIRTHDAY_AUTOPILOT_REMINDER"
    content.threadIdentifier = "birthday-autopilot-reminders"
    content.userInfo = ["requestId": candidate.requestId]

    let calendar = schedulingCalendar()
    var components = calendar.dateComponents(
      [.year, .month, .day, .hour, .minute],
      from: candidate.fireDate
    )
    components.calendar = calendar
    components.timeZone = calendar.timeZone
    let trigger = UNCalendarNotificationTrigger(
      dateMatching: components,
      repeats: false
    )
    return UNNotificationRequest(
      identifier: identifierPrefix + candidate.requestId,
      content: content,
      trigger: trigger
    )
  }

  private static func authorization(
    from authorizationStatus: UNAuthorizationStatus
  ) -> CompanionReminderAuthorization {
    switch authorizationStatus {
    case .authorized:
      return .authorized
    case .denied:
      return .denied
    case .ephemeral:
      return .ephemeral
    case .notDetermined:
      return .notDetermined
    case .provisional:
      return .provisional
    @unknown default:
      return .unknown
    }
  }

  private static func errorDictionary(code: String) -> [String: Any] {
    CompanionReminderReconciliation(
      authorization: .unknown,
      plannedDateCount: 0,
      scheduledCount: 0,
      truncated: false,
      failedCount: 0,
      earliestUnscheduledCivilDate: nil
    ).dictionary(kind: "error", code: code)
  }

  private static func schedulingCalendar() -> Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = .autoupdatingCurrent
    return calendar
  }

}

@objc(CompanionReminderModule)
final class CompanionReminderModule: NSObject {
  private let coordinator = CompanionReminderCoordinator.shared

  @objc
  static func requiresMainQueueSetup() -> Bool {
    false
  }

  @objc(getStatus:rejecter:)
  func getStatus(
    _ resolve: @escaping RCTPromiseResolveBlock,
    rejecter _: RCTPromiseRejectBlock
  ) {
    coordinator.status { result in resolve(result) }
  }

  /// This is the only notification method that may show a system permission
  /// prompt. It must be called from the explicit Enable reminders user action.
  @objc(requestAuthorization:rejecter:)
  func requestAuthorization(
    _ resolve: @escaping RCTPromiseResolveBlock,
    rejecter _: RCTPromiseRejectBlock
  ) {
    coordinator.requestAuthorizationAndReconcile { result in resolve(result) }
  }

  @objc(openNotificationSettings:rejecter:)
  func openNotificationSettings(
    _ resolve: @escaping RCTPromiseResolveBlock,
    rejecter _: RCTPromiseRejectBlock
  ) {
    coordinator.openNotificationSettings { result in resolve(result) }
  }

}

/// Foreground/lifecycle reconciliation never requests notification permission and
/// never opens MessageUI. It only replenishes the bounded generic reminder set.
final class CompanionLifecycleCoordinator {
  static let shared = CompanionLifecycleCoordinator()

  private var observers: [NSObjectProtocol] = []
  private var started = false

  private init() {}

  func start() {
    guard !started else { return }
    started = true

    let notificationCenter = NotificationCenter.default
    let names: [Notification.Name] = [
      UIApplication.didBecomeActiveNotification,
      UIApplication.protectedDataDidBecomeAvailableNotification,
      UIApplication.significantTimeChangeNotification,
      .NSSystemTimeZoneDidChange,
    ]
    observers = names.map { name in
      notificationCenter.addObserver(
        forName: name,
        object: nil,
        queue: .main
      ) { _ in
        CompanionProtectedStore.shared.observeResetSafetyDate()
        if IOSCompanionWipeRecoveryStore.shared.hasPendingOrUnreadableJournal() {
          IOSCompanionWipeRecoveryCoordinator.shared.resumeIfNeeded()
        } else if IOSAccountDeletionReceiptStore.shared.hasPendingOrUnreadableReceipt()
          || IOSAccountDeletionRecoveryStore.shared.hasPendingOrUnreadableJournal()
        {
          IOSAccountDeletionLocalCleanupCoordinator.shared.resumeIfNeeded()
        } else {
          self.replenishPlanBeforeReconciliation()
        }
      }
    }

    if IOSCompanionWipeRecoveryStore.shared.hasPendingOrUnreadableJournal() {
      IOSCompanionWipeRecoveryCoordinator.shared.resumeIfNeeded()
    } else if IOSAccountDeletionReceiptStore.shared.hasPendingOrUnreadableReceipt()
      || IOSAccountDeletionRecoveryStore.shared.hasPendingOrUnreadableJournal()
    {
      IOSAccountDeletionLocalCleanupCoordinator.shared.resumeIfNeeded()
    } else {
      CompanionProtectedStore.shared.reconcileComposerOnCleanLaunch()
      replenishPlanBeforeReconciliation()
    }
  }

  private func replenishPlanBeforeReconciliation() {
    Task { @MainActor in
      guard let binding = IOSGoogleIdentityCoordinator.shared.exactSessionBinding() else {
        CompanionReminderCoordinator.shared.reconcilePersisted()
        return
      }
      IOSCompanionWorkflowEngine.shared.reconcileReminderPlanForLifecycle(
        binding: binding
      )
    }
  }
}
