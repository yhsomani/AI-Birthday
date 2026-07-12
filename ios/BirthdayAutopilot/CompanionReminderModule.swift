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

private enum CompanionReminderInputError: Error {
  case invalid

  var safeCode: String { "REMINDER_INPUT_INVALID" }
}

private struct QueuedReminderReconciliation {
  let generation: UInt64
  let schedule: CompanionReminderSchedule
  let completion: (([String: Any]) -> Void)?
}

final class CompanionReminderCoordinator {
  static let shared = CompanionReminderCoordinator()

  private static let identifierPrefix = "birthday-autopilot.reminder.v1."
  private static let maximumScheduledDateCount = 60
  private static let maximumPlanCount = 500
  private static let maximumPlanningDays = 400

  private let center: UNUserNotificationCenter
  private let store: CompanionProtectedStore
  private let queue = DispatchQueue(
    label: "com.yashsomani.birthdayautopilot.companion-reminders",
    qos: .utility
  )
  private let stateLock = NSLock()
  private var reconciliationGeneration: UInt64 = 0
  private var activeReconciliationGeneration: UInt64?
  private var queuedReconciliation: QueuedReminderReconciliation?
  private var wipeInProgress = false

  private init(
    center: UNUserNotificationCenter = .current(),
    store: CompanionProtectedStore = .shared
  ) {
    self.center = center
    self.store = store
  }

  func requestAuthorizationAndReconcile(
    completion: @escaping ([String: Any]) -> Void
  ) {
    guard !isWiping else {
      completion(Self.errorDictionary(code: "COMPANION_WIPE_IN_PROGRESS"))
      return
    }
    center.getNotificationSettings { [weak self] settings in
      guard let self else {
        DispatchQueue.main.async {
          completion(Self.errorDictionary(code: "REMINDER_MODULE_UNAVAILABLE"))
        }
        return
      }
      // iOS never prompts again after denial. Return a settings-required state
      // so the UI can offer the explicit Settings action instead of repeatedly
      // calling requestAuthorization.
      guard settings.authorizationStatus != .denied else {
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
    center.getNotificationSettings { settings in
      guard settings.authorizationStatus == .denied else {
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

  func replacePlans(
    _ plans: [CompanionReminderPlan],
    completion: @escaping ([String: Any]) -> Void
  ) {
    guard beginPlanMutation() else {
      completion(Self.errorDictionary(code: "COMPANION_WIPE_IN_PROGRESS"))
      return
    }
    store.replaceReminderPlans(plans) { [weak self] result in
      guard let self else {
        completion(Self.errorDictionary(code: "REMINDER_MODULE_UNAVAILABLE"))
        return
      }
      switch result {
      case .failure(let error):
        completion(Self.errorDictionary(code: error.safeCode))
      case .success:
        self.reconcilePersisted(completion: completion)
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
        self.center.getNotificationSettings { settings in
          self.center.getPendingNotificationRequests { pending in
            let authorization = Self.authorization(from: settings)
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

  func cancelAppOwnedNotifications(completion: (() -> Void)? = nil) {
    center.getPendingNotificationRequests { [weak self] pending in
      guard let self else {
        DispatchQueue.main.async { completion?() }
        return
      }
      let identifiers =
        pending
        .map(\.identifier)
        .filter { $0.hasPrefix(Self.identifierPrefix) }
      self.center.removePendingNotificationRequests(withIdentifiers: identifiers)
      self.center.getDeliveredNotifications { delivered in
        let deliveredIdentifiers =
          delivered
          .map { $0.request.identifier }
          .filter { $0.hasPrefix(Self.identifierPrefix) }
        self.center.removeDeliveredNotifications(
          withIdentifiers: deliveredIdentifiers
        )
        DispatchQueue.main.async { completion?() }
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
        self.cancelAppOwnedNotifications {
          self.cancelAppOwnedNotifications {
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
  }

  func wipeCompanionData(completion: @escaping ([String: Any]) -> Void) {
    guard beginWipe() else {
      completion(["code": "COMPANION_WIPE_IN_PROGRESS", "kind": "error"])
      return
    }
    cancelAppOwnedNotifications { [store] in
      store.wipeAndInstallResetSafety { result in
        self.cancelAppOwnedNotifications {
          self.endWipe()
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
    cancelAppOwnedNotifications { [store] in
      store.destroyAfterRemoteAccountDeletion { result in
        self.cancelAppOwnedNotifications {
          self.endWipe()
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
    center.getNotificationSettings { [weak self] settings in
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
      let authorization = Self.authorization(from: settings)
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
            self.center.getPendingNotificationRequests { observed in
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
        let group = DispatchGroup()
        let failureLock = NSLock()
        var addFailureCount = 0
        for request in requests {
          guard self.isCurrentReconciliation(generation) else { break }
          group.enter()
          self.center.add(request) { error in
            if error != nil {
              failureLock.lock()
              addFailureCount += 1
              failureLock.unlock()
            }
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
          // First verification proves which desired additions actually exist.
          self.center.getPendingNotificationRequests { firstObserved in
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
            let staleIdentifiers = firstOwnedIdentifiers.filter {
              !desiredIdentifiers.contains($0)
            }
            self.center.removePendingNotificationRequests(
              withIdentifiers: Array(staleIdentifiers)
            )

            // The second query is authoritative for the committed horizon.
            self.center.getPendingNotificationRequests { finalObserved in
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
              let missingDesired = desiredIdentifiers.subtracting(finalOwnedIdentifiers)
              let unexpected = finalOwnedIdentifiers.subtracting(desiredIdentifiers)
              let full = addFailureCount == 0 && missingDesired.isEmpty && unexpected.isEmpty
              let earliestMissing = bounded.first(where: {
                missingDesired.contains(Self.identifierPrefix + $0.requestId)
              })?.civilDate
              let earliestUnscheduled =
                earliestMissing
                ?? (truncated ? candidates.dropFirst(bounded.count).first?.civilDate : nil)
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
                    result: Self.errorDictionary(code: "REMINDER_RECONCILIATION_SUPERSEDED")
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
                  scheduledCount: desiredIdentifiers.intersection(finalOwnedIdentifiers).count,
                  truncated: truncated,
                  failedCount: failedCount,
                  earliestUnscheduledCivilDate: earliestUnscheduled
                )
                let successful = full && !persistenceFailed
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

  private func removeAllPendingAppOwnedRequests(
    completion: @escaping () -> Void
  ) {
    center.getPendingNotificationRequests { [weak self] pending in
      guard let self else {
        completion()
        return
      }
      let identifiers =
        pending
        .map(\.identifier)
        .filter { $0.hasPrefix(Self.identifierPrefix) }
      self.center.removePendingNotificationRequests(withIdentifiers: identifiers)
      completion()
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

  private func beginPlanMutation() -> Bool {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard !wipeInProgress else { return false }
    reconciliationGeneration &+= 1
    return true
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
    let calendar = Calendar.autoupdatingCurrent
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

    let calendar = Calendar.autoupdatingCurrent
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
    from settings: UNNotificationSettings
  ) -> CompanionReminderAuthorization {
    switch settings.authorizationStatus {
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

  static func validatePlans(
    _ rawPlans: NSArray,
    now: Date = Date()
  ) throws -> [CompanionReminderPlan] {
    guard rawPlans.count <= maximumPlanCount,
      let plans = rawPlans as? [[String: Any]]
    else {
      throw CompanionReminderInputError.invalid
    }
    let calendar = Calendar.autoupdatingCurrent
    let today = calendar.startOfDay(for: now)
    guard
      let maximumDate = calendar.date(
        byAdding: .day,
        value: maximumPlanningDays,
        to: today
      )
    else {
      throw CompanionReminderInputError.invalid
    }

    var seenOccurrenceIds = Set<String>()
    return try plans.map { raw in
      guard Set(raw.keys) == ["civilDate", "hour", "minute", "occurrenceId"],
        let occurrenceId = raw["occurrenceId"] as? String,
        CompanionMessageModule.isValidOpaqueIdentifier(occurrenceId),
        seenOccurrenceIds.insert(occurrenceId).inserted,
        let civilDate = raw["civilDate"] as? String,
        let dayStart = civilDateStart(civilDate, calendar: calendar),
        dayStart >= today,
        dayStart <= maximumDate,
        let hour = finiteInteger(raw["hour"], range: 0...23),
        let minute = finiteInteger(raw["minute"], range: 0...59)
      else {
        throw CompanionReminderInputError.invalid
      }
      return CompanionReminderPlan(
        occurrenceId: occurrenceId,
        civilDate: civilDate,
        hour: hour,
        minute: minute
      )
    }
  }

  private static func finiteInteger(
    _ value: Any?,
    range: ClosedRange<Int>
  ) -> Int? {
    guard let number = value as? NSNumber,
      CFGetTypeID(number) != CFBooleanGetTypeID(),
      number.doubleValue.isFinite,
      number.doubleValue.rounded() == number.doubleValue
    else {
      return nil
    }
    let integer = number.intValue
    return range.contains(integer) ? integer : nil
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

  /// Replaces native plans and reconciles them without requesting permission.
  @objc(replacePlans:resolver:rejecter:)
  func replacePlans(
    _ rawPlans: NSArray,
    resolver resolve: @escaping RCTPromiseResolveBlock,
    rejecter _: RCTPromiseRejectBlock
  ) {
    do {
      let plans = try CompanionReminderCoordinator.validatePlans(rawPlans)
      coordinator.replacePlans(plans) { result in resolve(result) }
    } catch let error as CompanionReminderInputError {
      resolve([
        "authorization": "unknown",
        "code": error.safeCode,
        "failedCount": 0,
        "kind": "error",
        "plannedDateCount": 0,
        "scheduledCount": 0,
        "truncated": false,
      ])
    } catch {
      resolve([
        "authorization": "unknown",
        "code": "REMINDER_INPUT_INVALID",
        "failedCount": 0,
        "kind": "error",
        "plannedDateCount": 0,
        "scheduledCount": 0,
        "truncated": false,
      ])
    }
  }

  @objc(cancelAppOwned:rejecter:)
  func cancelAppOwned(
    _ resolve: @escaping RCTPromiseResolveBlock,
    rejecter _: RCTPromiseRejectBlock
  ) {
    coordinator.cancelPlansAndNotifications { result in resolve(result) }
  }

  @objc(wipeCompanionData:rejecter:)
  func wipeCompanionData(
    _ resolve: @escaping RCTPromiseResolveBlock,
    rejecter _: RCTPromiseRejectBlock
  ) {
    coordinator.wipeCompanionData { result in resolve(result) }
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
        if IOSAccountDeletionReceiptStore.shared.hasPendingOrUnreadableReceipt()
          || IOSAccountDeletionRecoveryStore.shared.hasPendingOrUnreadableJournal()
        {
          IOSAccountDeletionLocalCleanupCoordinator.shared.resumeIfNeeded()
        } else {
          CompanionReminderCoordinator.shared.reconcilePersisted()
        }
      }
    }

    if IOSAccountDeletionReceiptStore.shared.hasPendingOrUnreadableReceipt()
      || IOSAccountDeletionRecoveryStore.shared.hasPendingOrUnreadableJournal()
    {
      IOSAccountDeletionLocalCleanupCoordinator.shared.resumeIfNeeded()
    } else {
      CompanionProtectedStore.shared.reconcileComposerOnCleanLaunch()
      CompanionReminderCoordinator.shared.reconcilePersisted()
    }
  }
}
