import Foundation
import UIKit
import UserNotifications

extension Notification.Name {
  static let companionNativeRouteAvailable = Notification.Name(
    "BirthdayAutopilot.CompanionNativeRouteAvailable"
  )
}

protocol IOSCompanionNativeRouteStore: AnyObject {
  func takePendingNativeRoute(
    now: Date,
    completion: @escaping (
      Result<IOSCompanionNativeRoute?, CompanionStoreError>
    ) -> Void
  )
  func consumeReminderRouteRequest(
    _ requestId: String,
    now: Date,
    completion: @escaping (
      Result<IOSCompanionNativeRoute, CompanionStoreError>
    ) -> Void
  )
}

extension CompanionProtectedStore: IOSCompanionNativeRouteStore {}

protocol IOSCompanionRoutePrivacyGate {
  var routeConsumptionIsBlocked: Bool { get }
}

struct IOSSystemCompanionRoutePrivacyGate: IOSCompanionRoutePrivacyGate {
  var routeConsumptionIsBlocked: Bool {
    IOSAccountDeletionReceiptStore.shared.hasPendingOrUnreadableReceipt()
      || IOSAccountDeletionRecoveryStore.shared.hasPendingOrUnreadableJournal()
      || IOSCompanionWipeRecoveryStore.shared.hasPendingOrUnreadableJournal()
  }
}

protocol IOSCompanionProtectedDataStatus {
  var isProtectedDataAvailable: Bool { get }
}

struct IOSSystemCompanionProtectedDataStatus: IOSCompanionProtectedDataStatus {
  var isProtectedDataAvailable: Bool {
    UIApplication.shared.isProtectedDataAvailable
  }
}

/// Owns local-notification presentation and tap routing. Notification payloads
/// contain one opaque request UUID only. The UUID is atomically consumed inside
/// CompanionProtectedStore and never crosses the React Native boundary.
final class IOSCompanionNotificationRouter: NSObject, UNUserNotificationCenterDelegate {
  static let shared = IOSCompanionNotificationRouter(
    store: CompanionProtectedStore.shared,
    privacyGate: IOSSystemCompanionRoutePrivacyGate(),
    protectedDataStatus: IOSSystemCompanionProtectedDataStatus(),
    eventCenter: .default,
    observesProtectedData: true
  )

  private static let identifierPrefix = "birthday-autopilot.reminder.v1."
  private let lock = NSLock()
  private let store: IOSCompanionNativeRouteStore
  private let privacyGate: IOSCompanionRoutePrivacyGate
  private let protectedDataStatus: IOSCompanionProtectedDataStatus
  private let eventCenter: NotificationCenter
  private let observesProtectedData: Bool
  private var deferredRequestId: String?
  private var pendingAttentionRouteId: String?

  /// Internal dependency seam for hosted native tests. Production uses the
  /// singleton above and still observes the real protected-data notification.
  init(
    store: IOSCompanionNativeRouteStore,
    privacyGate: IOSCompanionRoutePrivacyGate,
    protectedDataStatus: IOSCompanionProtectedDataStatus,
    eventCenter: NotificationCenter,
    observesProtectedData: Bool
  ) {
    self.store = store
    self.privacyGate = privacyGate
    self.protectedDataStatus = protectedDataStatus
    self.eventCenter = eventCenter
    self.observesProtectedData = observesProtectedData
    super.init()
    if observesProtectedData {
      eventCenter.addObserver(
        self,
        selector: #selector(retryDeferredRoute),
        name: UIApplication.protectedDataDidBecomeAvailableNotification,
        object: nil
      )
    }
  }

  deinit {
    if observesProtectedData {
      eventCenter.removeObserver(self)
    }
  }

  /// Atomically returns and consumes the durable safe navigation hint. The
  /// protected notification request UUID was already rotated and is never part
  /// of this projection.
  func takeProjection(
    completion: @escaping (Result<[String: Any], CompanionStoreError>) -> Void
  ) {
    lock.lock()
    let attentionRouteId = pendingAttentionRouteId
    pendingAttentionRouteId = nil
    lock.unlock()
    if let attentionRouteId {
      completion(.success([
        "kind": "attention",
        "routeId": attentionRouteId,
        "source": "attention",
      ]))
      return
    }
    store.takePendingNativeRoute(now: Date()) { result in
      completion(result.map { route in
        route?.projection ?? ["kind": "none"]
      })
    }
  }

  func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    willPresent notification: UNNotification,
    withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
  ) {
    if IOSCompanionAttentionNotifier.isAttentionIdentifier(
      notification.request.identifier
    ) {
      // The active app already exposes the richer in-app attention item.
      completionHandler([])
      return
    }
    guard Self.opaqueRequestId(from: notification.request) != nil else {
      completionHandler([])
      return
    }
    if #available(iOS 14.0, *) {
      completionHandler([.banner, .list, .sound])
    } else {
      completionHandler([.alert, .sound])
    }
  }

  func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    didReceive response: UNNotificationResponse,
    withCompletionHandler completionHandler: @escaping () -> Void
  ) {
    if response.actionIdentifier == UNNotificationDefaultActionIdentifier,
      IOSCompanionAttentionNotifier.isAttentionIdentifier(
        response.notification.request.identifier
      )
    {
      lock.lock()
      pendingAttentionRouteId = UUID().uuidString.lowercased()
      lock.unlock()
      center.removeDeliveredNotifications(
        withIdentifiers: [response.notification.request.identifier]
      )
      eventCenter.post(
        name: .companionNativeRouteAvailable,
        object: self,
        userInfo: ["kind": "available"]
      )
      completionHandler()
      return
    }
    guard response.actionIdentifier == UNNotificationDefaultActionIdentifier,
      let requestId = Self.opaqueRequestId(from: response.notification.request)
    else {
      completionHandler()
      return
    }
    consume(requestId: requestId) {
      center.removeDeliveredNotifications(
        withIdentifiers: [response.notification.request.identifier]
      )
      completionHandler()
    }
  }

  @objc func retryDeferredRoute() {
    lock.lock()
    let requestId = deferredRequestId
    deferredRequestId = nil
    lock.unlock()
    guard let requestId else { return }
    consume(requestId: requestId, completion: {})
  }

  func consume(requestId: String, completion: @escaping () -> Void) {
    guard !privacyGate.routeConsumptionIsBlocked else {
      completion()
      return
    }
    store.consumeReminderRouteRequest(requestId, now: Date()) {
      [weak self] result in
      guard let self else {
        completion()
        return
      }
      switch result {
      case .success:
        self.lock.lock()
        self.deferredRequestId = nil
        self.lock.unlock()
        self.eventCenter.post(
          name: .companionNativeRouteAvailable,
          object: self,
          userInfo: ["kind": "available"]
        )
      case .failure(.storageUnavailable)
        where !self.protectedDataStatus.isProtectedDataAvailable:
        self.lock.lock()
        self.deferredRequestId = requestId
        self.lock.unlock()
      case .failure:
        break
      }
      completion()
    }
  }

  static func opaqueRequestId(
    from request: UNNotificationRequest
  ) -> String? {
    guard request.identifier.hasPrefix(identifierPrefix),
      let value = request.content.userInfo["requestId"] as? String,
      request.content.userInfo.count == 1,
      Set(request.content.userInfo.keys.compactMap { $0 as? String }) == ["requestId"],
      request.identifier == identifierPrefix + value,
      let uuid = UUID(uuidString: value),
      uuid.uuidString.lowercased() == value
    else { return nil }
    return value
  }
}
