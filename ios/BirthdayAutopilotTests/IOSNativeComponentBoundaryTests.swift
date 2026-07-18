import Foundation
import UserNotifications
import XCTest
@testable import BirthdayAutopilot

final class IOSNativeComponentBoundaryTests: XCTestCase {
  func testRealSchemaV2MigrationPreservesUncertainTerminalAsRepeatFence() throws {
    var snapshot = CompanionProtectedSnapshot.reset(
      generation: "reset-generation",
      blockedCivilDates: ["2026-07-13"],
      overflowed: false
    )
    snapshot.schemaVersion = 2
    snapshot.composerRecords = [
      CompanionComposerRecord(
        operationId: "operation-1",
        proposalId: "proposal-1",
        occurrenceId: "occurrence-1",
        occurrenceCivilDate: "2026-07-13",
        occurrenceDigest: nil,
        openedAt: Date(timeIntervalSince1970: 1_700_000_000),
        outcome: .presented,
        resolvedAt: nil
      ),
    ]

    let store = CompanionProtectedStore()
    let migrationTime = Date(timeIntervalSince1970: 1_700_000_100)
    try store.migrateSchemaV2ForNativeTests(&snapshot, now: migrationTime)

    XCTAssertEqual(
      snapshot.schemaVersion,
      CompanionProtectedSnapshot.currentSchemaVersion
    )
    XCTAssertEqual(snapshot.composerRecords.first?.outcome, .outcomeUnknown)
    XCTAssertEqual(snapshot.composerRecords.first?.resolvedAt, migrationTime)
    let terminalLedger = try XCTUnwrap(snapshot.terminalLedger)
    XCTAssertTrue(
      terminalLedger.hasLegacyDateWideFence(civilDate: "2026-07-13")
    )
    XCTAssertTrue(snapshot.reminderPlans.isEmpty)
    XCTAssertNil(snapshot.pendingNativeRoute)
  }

  func testReminderPartialAddPersistsObservedPartialHorizon() {
    let schedule = Self.schedule(dayOffsets: [1, 2])
    let center = FakeNotificationCenter()
    let store = FakeReminderStore(schedule: schedule)
    let attention = FakeAttentionNotifier()
    center.failedIdentifiers = [
      "birthday-autopilot.reminder.v1."
        + schedule.requestIdByCivilDate[schedule.plans[1].civilDate]!,
    ]
    let coordinator = CompanionReminderCoordinator(
      center: center,
      store: store,
      attentionNotifier: attention
    )
    let completed = expectation(description: "partial reconciliation")

    coordinator.reconcilePersisted { result in
      XCTAssertEqual(result["kind"] as? String, "error")
      XCTAssertEqual(result["code"] as? String, "REMINDER_HORIZON_PARTIAL")
      XCTAssertEqual(result["plannedDateCount"] as? Int, 2)
      XCTAssertEqual(result["scheduledCount"] as? Int, 1)
      XCTAssertEqual(result["failedCount"] as? Int, 1)
      completed.fulfill()
    }

    wait(for: [completed], timeout: 2)
    XCTAssertEqual(store.recordedHorizons.count, 1)
    XCTAssertEqual(store.recordedHorizons[0].state, .partial)
    XCTAssertEqual(store.recordedHorizons[0].observedRequestIds.count, 1)
    XCTAssertEqual(attention.notifiedKinds, [.reminders])
  }

  func testReminderPersistenceFailureNeverReportsAFullHorizon() {
    let schedule = Self.schedule(dayOffsets: [1])
    let center = FakeNotificationCenter()
    let store = FakeReminderStore(schedule: schedule)
    store.recordHorizonResult = .failure(.storageUnavailable)
    let attention = FakeAttentionNotifier()
    let coordinator = CompanionReminderCoordinator(
      center: center,
      store: store,
      attentionNotifier: attention
    )
    let completed = expectation(description: "persistence failure")

    coordinator.reconcilePersisted { result in
      XCTAssertEqual(result["kind"] as? String, "error")
      XCTAssertEqual(result["code"] as? String, "REMINDER_HORIZON_PARTIAL")
      completed.fulfill()
    }

    wait(for: [completed], timeout: 2)
    XCTAssertEqual(store.recordedHorizons.first?.state, .full)
    XCTAssertEqual(attention.notifiedKinds, [.reminders])
  }

  func testWipeDrainsAndRemovesALateNotificationAddBeforeDestroyingStore() {
    let schedule = Self.schedule(dayOffsets: [1])
    let center = FakeNotificationCenter()
    center.holdAdds = true
    let store = FakeReminderStore(schedule: schedule)
    let attention = FakeAttentionNotifier()
    let coordinator = CompanionReminderCoordinator(
      center: center,
      store: store,
      attentionNotifier: attention
    )
    let addStarted = expectation(description: "notification add started")
    center.onHeldAdd = { addStarted.fulfill() }
    coordinator.reconcilePersisted(completion: nil)
    wait(for: [addStarted], timeout: 2)

    let wiped = expectation(description: "wipe completed")
    coordinator.wipeCompanionData { result in
      XCTAssertEqual(result["kind"] as? String, "ok")
      wiped.fulfill()
    }
    XCTAssertEqual(
      store.wipeCalls,
      0,
      "protected state was destroyed before the in-flight add drained"
    )

    center.completeHeldAdds()
    wait(for: [wiped], timeout: 2)
    XCTAssertEqual(store.wipeCalls, 1)
    XCTAssertTrue(
      center.pendingIdentifiers.filter {
        $0.hasPrefix("birthday-autopilot.reminder.v1.")
      }.isEmpty
    )
    XCTAssertEqual(attention.beginDrainCalls, 2)
    XCTAssertEqual(attention.endDrainCalls, 2)
  }

  func testNotificationRouterDefersLockedStorageAndRetriesExactlyOnce() {
    let requestId = UUID().uuidString.lowercased()
    let store = FakeRouteStore()
    store.consumeResults = [
      .failure(.storageUnavailable),
      .success(IOSCompanionNativeRoute(routeId: "route-1")),
    ]
    let privacy = FakeRoutePrivacyGate()
    let protectedData = FakeProtectedDataStatus(isAvailable: false)
    let events = NotificationCenter()
    let router = IOSCompanionNotificationRouter(
      store: store,
      privacyGate: privacy,
      protectedDataStatus: protectedData,
      eventCenter: events,
      observesProtectedData: false
    )
    let first = expectation(description: "locked consume")
    router.consume(requestId: requestId) { first.fulfill() }
    wait(for: [first], timeout: 1)
    XCTAssertEqual(store.consumedRequestIds, [requestId])

    protectedData.isAvailable = true
    let routed = expectation(description: "route published")
    let observer = events.addObserver(
      forName: .companionNativeRouteAvailable,
      object: router,
      queue: nil
    ) { _ in routed.fulfill() }
    router.retryDeferredRoute()
    wait(for: [routed], timeout: 1)
    events.removeObserver(observer)
    XCTAssertEqual(store.consumedRequestIds, [requestId, requestId])

    router.retryDeferredRoute()
    XCTAssertEqual(
      store.consumedRequestIds,
      [requestId, requestId],
      "a successful deferred route was replayed"
    )
  }

  func testNotificationRouterFailsClosedDuringDeletionAndValidatesOpaquePayload() {
    let requestId = UUID().uuidString.lowercased()
    let store = FakeRouteStore()
    let privacy = FakeRoutePrivacyGate()
    privacy.blocked = true
    let router = IOSCompanionNotificationRouter(
      store: store,
      privacyGate: privacy,
      protectedDataStatus: FakeProtectedDataStatus(isAvailable: true),
      eventCenter: NotificationCenter(),
      observesProtectedData: false
    )
    let blocked = expectation(description: "privacy gate")
    router.consume(requestId: requestId) { blocked.fulfill() }
    wait(for: [blocked], timeout: 1)
    XCTAssertTrue(store.consumedRequestIds.isEmpty)

    let validContent = UNMutableNotificationContent()
    validContent.userInfo = ["requestId": requestId]
    let valid = UNNotificationRequest(
      identifier: "birthday-autopilot.reminder.v1." + requestId,
      content: validContent,
      trigger: nil
    )
    XCTAssertEqual(
      IOSCompanionNotificationRouter.opaqueRequestId(from: valid),
      requestId
    )

    let injectedContent = UNMutableNotificationContent()
    injectedContent.userInfo = ["requestId": requestId, "recipient": "+911234567890"]
    let injected = UNNotificationRequest(
      identifier: "birthday-autopilot.reminder.v1." + requestId,
      content: injectedContent,
      trigger: nil
    )
    XCTAssertNil(IOSCompanionNotificationRouter.opaqueRequestId(from: injected))

    store.pendingRoute = IOSCompanionNativeRoute(routeId: "safe-route")
    let projectionTaken = expectation(description: "safe route projection")
    router.takeProjection { result in
      guard case .success(let projection) = result else {
        XCTFail("route projection failed")
        projectionTaken.fulfill()
        return
      }
      XCTAssertEqual(projection["kind"] as? String, "automation-review")
      XCTAssertEqual(projection["routeId"] as? String, "safe-route")
      XCTAssertEqual(Set(projection.keys), ["kind", "routeId", "source"])
      projectionTaken.fulfill()
    }
    wait(for: [projectionTaken], timeout: 1)
  }

  func testComposerTerminalSequencerWaitsForPersistenceAndDismissal() {
    var events: [String] = []
    var persisted: ((Bool) -> Void)?
    var dismissed: (() -> Void)?
    var publicOutcome: String?
    IOSComposerDelegateTerminalSequencer.finish(
      result: .reportedSent,
      persist: { outcome, completion in
        events.append("persist-" + outcome.rawValue)
        persisted = completion
      },
      dismiss: { completion in
        events.append("dismiss")
        dismissed = completion
      },
      completion: { outcome in
        events.append("resolve-" + outcome)
        publicOutcome = outcome
      }
    )
    XCTAssertEqual(events, ["persist-reported-sent"])
    persisted?(false)
    XCTAssertEqual(events, ["persist-reported-sent", "dismiss"])
    dismissed?()
    XCTAssertEqual(publicOutcome, "unknown")
  }

  private static func schedule(dayOffsets: [Int]) -> CompanionReminderSchedule {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(secondsFromGMT: 0)!
    let start = calendar.startOfDay(for: Date())
    var ids: [String: String] = [:]
    let plans = dayOffsets.enumerated().map { index, offset in
      let date = calendar.date(byAdding: .day, value: offset, to: start)!
      let components = calendar.dateComponents([.year, .month, .day], from: date)
      let civilDate = String(
        format: "%04d-%02d-%02d",
        components.year!, components.month!, components.day!
      )
      ids[civilDate] = UUID().uuidString.lowercased()
      return CompanionReminderPlan(
        occurrenceId: "occurrence-\(index)",
        civilDate: civilDate,
        hour: 12,
        minute: 0
      )
    }
    return CompanionReminderSchedule(
      plans: plans,
      requestIdByCivilDate: ids,
      horizon: nil
    )
  }
}

private final class FakeNotificationCenter: IOSCompanionNotificationCenterClient {
  var status: UNAuthorizationStatus = .authorized
  var failedIdentifiers = Set<String>()
  var holdAdds = false
  var onHeldAdd: (() -> Void)?

  private let lock = NSLock()
  private var pending: [String: UNNotificationRequest] = [:]
  private var delivered: [String: UNNotificationRequest] = [:]
  private var heldAdds: [(UNNotificationRequest, ((Error?) -> Void)?)] = []

  var pendingIdentifiers: [String] {
    lock.lock()
    defer { lock.unlock() }
    return Array(pending.keys)
  }

  func authorizationStatus(completion: @escaping (UNAuthorizationStatus) -> Void) {
    completion(status)
  }

  func requestAuthorization(
    options _: UNAuthorizationOptions,
    completion: @escaping (Bool, Error?) -> Void
  ) {
    completion(status == .authorized, nil)
  }

  func pendingRequests(completion: @escaping ([UNNotificationRequest]) -> Void) {
    lock.lock()
    let value = Array(pending.values)
    lock.unlock()
    completion(value)
  }

  func deliveredRequests(completion: @escaping ([UNNotificationRequest]) -> Void) {
    lock.lock()
    let value = Array(delivered.values)
    lock.unlock()
    completion(value)
  }

  func add(
    _ request: UNNotificationRequest,
    completion: ((Error?) -> Void)?
  ) {
    lock.lock()
    if holdAdds {
      heldAdds.append((request, completion))
      let callback = onHeldAdd
      onHeldAdd = nil
      lock.unlock()
      callback?()
      return
    }
    let failed = failedIdentifiers.contains(request.identifier)
    if !failed { pending[request.identifier] = request }
    lock.unlock()
    completion?(failed ? FakeNotificationError.addFailed : nil)
  }

  func removePending(withIdentifiers identifiers: [String]) {
    lock.lock()
    identifiers.forEach { pending.removeValue(forKey: $0) }
    lock.unlock()
  }

  func removeDelivered(withIdentifiers identifiers: [String]) {
    lock.lock()
    identifiers.forEach { delivered.removeValue(forKey: $0) }
    lock.unlock()
  }

  func completeHeldAdds() {
    lock.lock()
    let completions = heldAdds
    heldAdds.removeAll()
    holdAdds = false
    for (request, _) in completions {
      pending[request.identifier] = request
    }
    lock.unlock()
    completions.forEach { $0.1?(nil) }
  }
}

private enum FakeNotificationError: Error {
  case addFailed
}

private final class FakeReminderStore: IOSCompanionReminderStore {
  let schedule: CompanionReminderSchedule
  var recordedHorizons: [CompanionReminderHorizon] = []
  var recordHorizonResult: Result<Void, CompanionStoreError> = .success(())
  var wipeCalls = 0
  var destroyCalls = 0

  init(schedule: CompanionReminderSchedule) {
    self.schedule = schedule
  }

  func readReminderSchedule(
    completion: @escaping (
      Result<CompanionReminderSchedule, CompanionStoreError>
    ) -> Void
  ) {
    completion(.success(schedule))
  }

  func replaceReminderPlans(
    _ plans: [CompanionReminderPlan],
    completion: @escaping (Result<Void, CompanionStoreError>) -> Void
  ) {
    completion(.success(()))
  }

  func recordReminderHorizon(
    _ horizon: CompanionReminderHorizon,
    completion: @escaping (Result<Void, CompanionStoreError>) -> Void
  ) {
    recordedHorizons.append(horizon)
    completion(recordHorizonResult)
  }

  func wipeAndInstallResetSafety(
    completion: @escaping (Result<Void, CompanionStoreError>) -> Void
  ) {
    wipeCalls += 1
    completion(.success(()))
  }

  func destroyAfterRemoteAccountDeletion(
    completion: @escaping (Result<Void, CompanionStoreError>) -> Void
  ) {
    destroyCalls += 1
    completion(.success(()))
  }
}

private final class FakeAttentionNotifier: IOSCompanionAttentionNotifying {
  var notifiedKinds: [IOSCompanionAttentionKind] = []
  var beginDrainCalls = 0
  var endDrainCalls = 0

  func notify(_ kind: IOSCompanionAttentionKind) {
    notifiedKinds.append(kind)
  }

  func beginCancellationDrain(_ completion: @escaping (Bool) -> Void) {
    beginDrainCalls += 1
    completion(true)
  }

  func endCancellationDrain() {
    endDrainCalls += 1
  }
}

private final class FakeRouteStore: IOSCompanionNativeRouteStore {
  var consumeResults: [Result<IOSCompanionNativeRoute, CompanionStoreError>] = []
  var consumedRequestIds: [String] = []
  var pendingRoute: IOSCompanionNativeRoute?

  func takePendingNativeRoute(
    now _: Date,
    completion: @escaping (
      Result<IOSCompanionNativeRoute?, CompanionStoreError>
    ) -> Void
  ) {
    completion(.success(pendingRoute))
    pendingRoute = nil
  }

  func consumeReminderRouteRequest(
    _ requestId: String,
    now _: Date,
    completion: @escaping (
      Result<IOSCompanionNativeRoute, CompanionStoreError>
    ) -> Void
  ) {
    consumedRequestIds.append(requestId)
    completion(
      consumeResults.isEmpty
        ? .failure(.invalidReview)
        : consumeResults.removeFirst()
    )
  }
}

private final class FakeRoutePrivacyGate: IOSCompanionRoutePrivacyGate {
  var blocked = false
  var routeConsumptionIsBlocked: Bool { blocked }
}

private final class FakeProtectedDataStatus: IOSCompanionProtectedDataStatus {
  var isAvailable: Bool
  var isProtectedDataAvailable: Bool { isAvailable }

  init(isAvailable: Bool) {
    self.isAvailable = isAvailable
  }
}
