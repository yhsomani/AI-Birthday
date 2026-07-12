import BackgroundTasks
import Foundation

/// Best-effort iOS refresh of the already-authorized read-only People cache.
/// This boundary never presents Google consent, an account chooser, MessageUI,
/// or a notification-permission prompt. It also never promises an execution
/// time: `BGTaskScheduler` remains the scheduling authority.
@MainActor
final class IOSPeopleBackgroundRefreshCoordinator {
  static let shared = IOSPeopleBackgroundRefreshCoordinator()
  private static let privacySuspendedKey =
    "birthday.people-background-refresh.privacy-suspended-v1"

  private let identity: IOSGoogleIdentityCoordinator
  private let peopleSync: IOSPeopleSyncCoordinator
  private let defaults: UserDefaults
  private var registered = false
  private var privacySuspended: Bool
  private weak var activeTask: BGAppRefreshTask?
  private var activeRunID: UUID?

  var contactsAccessIsSuspendedForPrivacy: Bool { privacySuspended }

  init(
    identity: IOSGoogleIdentityCoordinator = .shared,
    peopleSync: IOSPeopleSyncCoordinator = .shared,
    defaults: UserDefaults = .standard
  ) {
    self.identity = identity
    self.peopleSync = peopleSync
    self.defaults = defaults
    privacySuspended = defaults.bool(forKey: Self.privacySuspendedKey)
  }

  /// Apple requires every permitted identifier to be registered exactly once
  /// before application launch finishes.
  @discardableResult
  func registerAtLaunch() -> Bool {
    guard !registered else { return true }
    let accepted = BGTaskScheduler.shared.register(
      forTaskWithIdentifier: IOSPeopleBackgroundRefreshPolicy.taskIdentifier,
      using: DispatchQueue.main
    ) { [weak self] task in
      Task { @MainActor in
        guard let refreshTask = task as? BGAppRefreshTask, let self else {
          task.setTaskCompleted(success: false)
          return
        }
        self.handle(refreshTask)
      }
    }
    registered = accepted
    return accepted
  }

  /// Called after a foreground sync or as the connected app enters the
  /// background. Re-submission replaces the previous request for this ID.
  @discardableResult
  func scheduleForConnectedSession(
    after delay: TimeInterval = IOSPeopleBackgroundRefreshPolicy.regularRefreshDelay
  ) -> Bool {
    guard registered, !privacySuspended, identity.exactSessionBinding() != nil,
      delay.isFinite, delay >= 0
    else { return false }

    return submitRequest(after: delay, notifyOnFailure: true)
  }

  private func submitRequest(after delay: TimeInterval, notifyOnFailure: Bool) -> Bool {
    guard registered, delay.isFinite, delay >= 0 else { return false }
    let request = BGAppRefreshTaskRequest(
      identifier: IOSPeopleBackgroundRefreshPolicy.taskIdentifier
    )
    request.earliestBeginDate = Date(timeIntervalSinceNow: delay)
    do {
      try BGTaskScheduler.shared.submit(request)
      return true
    } catch {
      // Scheduler errors contain no actionable private contact data. They are
      // intentionally not logged; foreground freshness remains visibly stale.
      if notifyOnFailure {
        IOSCompanionAttentionNotifier.shared.notify(.contacts)
      }
      return false
    }
  }

  /// Sign-out, revoke, account mismatch, and deletion must leave no pending
  /// refresh. If one is executing, fence its generation before completing it.
  func cancelForDisconnectedSession() {
    BGTaskScheduler.shared.cancel(
      taskRequestWithIdentifier: IOSPeopleBackgroundRefreshPolicy.taskIdentifier
    )
    guard let task = activeTask, let runID = activeRunID else { return }
    expire(task, runID: runID)
  }

  /// A reviewed privacy clear must remain authoritative while remote cleanup
  /// is pending, even though the exact Google session can temporarily remain
  /// live for reauthentication and reset replay.
  func suspendForPrivacyOperation() {
    privacySuspended = true
    defaults.set(true, forKey: Self.privacySuspendedKey)
    cancelForDisconnectedSession()
  }

  /// Only an explicit foreground Contacts authorization/sync after no pending
  /// privacy operation may make background refresh eligible again.
  func resumeAfterExplicitContactsAction() {
    privacySuspended = false
    defaults.removeObject(forKey: Self.privacySuspendedKey)
  }

  private func handle(_ task: BGAppRefreshTask) {
    guard registered, !privacySuspended, activeTask == nil else {
      task.setTaskCompleted(
        success: registered && !privacySuspended && activeTask == nil
      )
      return
    }
    guard let expectedBinding = identity.exactSessionBinding() else {
      if case .connecting = identity.state {
        // A cold BG launch can deliver the task before protected storage,
        // Firebase, App Check, and Google restoration finish. Completing the
        // task may suspend the process, so durably request one bounded retry
        // without assuming an account. A later signed-out transition cancels it.
        _ = submitRequest(
          after: IOSPeopleBackgroundRefreshPolicy.transientRetryDelay,
          notifyOnFailure: false
        )
      }
      task.setTaskCompleted(success: false)
      return
    }

    let runID = UUID()
    activeTask = task
    activeRunID = runID
    task.expirationHandler = { [weak self, weak task] in
      Task { @MainActor in
        guard let self, let task else { return }
        self.expire(task, runID: runID)
      }
    }

    peopleSync.sync(interactiveAuthorization: false) { [weak self, weak task] outcome in
      Task { @MainActor in
        guard let self, let task, self.isActive(task, runID: runID) else { return }
        guard case .completed = outcome,
          self.identity.exactSessionBinding()?.hasSameOwner(as: expectedBinding) == true,
          self.identity.exactSessionBinding()?.accountGeneration
            == expectedBinding.accountGeneration
        else {
          self.scheduleOrStop(after: outcome)
          self.finish(task, runID: runID, success: false)
          return
        }

        IOSCompanionWorkflowEngine.shared.reconcileAfterPeopleSync(
          binding: expectedBinding
        ) { [weak self, weak task] in
          Task { @MainActor in
            guard let self, let task, self.isActive(task, runID: runID) else { return }
            self.scheduleOrStop(after: outcome)
            self.finish(
              task,
              runID: runID,
              success: IOSPeopleBackgroundRefreshPolicy.taskCompletedSuccessfully(outcome)
            )
          }
        }
      }
    }
  }

  private func scheduleOrStop(after outcome: IOSPeopleSyncOutcome) {
    guard let delay = IOSPeopleBackgroundRefreshPolicy.nextDelay(after: outcome) else {
      BGTaskScheduler.shared.cancel(
        taskRequestWithIdentifier: IOSPeopleBackgroundRefreshPolicy.taskIdentifier
      )
      IOSCompanionAttentionNotifier.shared.notify(.contacts)
      return
    }
    _ = scheduleForConnectedSession(after: delay)
  }

  private func expire(_ task: BGAppRefreshTask, runID: UUID) {
    guard isActive(task, runID: runID) else { return }
    peopleSync.invalidateOutstandingSync { [weak self, weak task] _ in
      Task { @MainActor in
        guard let self, let task, self.isActive(task, runID: runID) else { return }
        _ = self.scheduleForConnectedSession(
          after: IOSPeopleBackgroundRefreshPolicy.transientRetryDelay
        )
        self.finish(task, runID: runID, success: false)
      }
    }
  }

  private func isActive(_ task: BGAppRefreshTask, runID: UUID) -> Bool {
    activeTask === task && activeRunID == runID
  }

  private func finish(_ task: BGAppRefreshTask, runID: UUID, success: Bool) {
    guard isActive(task, runID: runID) else { return }
    task.expirationHandler = nil
    activeTask = nil
    activeRunID = nil
    task.setTaskCompleted(success: success)
  }
}
