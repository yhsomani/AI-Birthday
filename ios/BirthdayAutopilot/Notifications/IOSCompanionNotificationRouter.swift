import Foundation
import UIKit
import UserNotifications

extension Notification.Name {
  static let companionNativeRouteAvailable = Notification.Name(
    "BirthdayAutopilot.CompanionNativeRouteAvailable"
  )
}

/// Owns local-notification presentation and tap routing. Notification payloads
/// contain one opaque request UUID only. The UUID is atomically consumed inside
/// CompanionProtectedStore and never crosses the React Native boundary.
final class IOSCompanionNotificationRouter: NSObject, UNUserNotificationCenterDelegate {
  static let shared = IOSCompanionNotificationRouter()

  private static let identifierPrefix = "birthday-autopilot.reminder.v1."
  private let lock = NSLock()
  private var deferredRequestId: String?

  private override init() {
    super.init()
    NotificationCenter.default.addObserver(
      self,
      selector: #selector(retryDeferredRoute),
      name: UIApplication.protectedDataDidBecomeAvailableNotification,
      object: nil
    )
  }

  deinit {
    NotificationCenter.default.removeObserver(self)
  }

  /// Atomically returns and consumes the durable safe navigation hint. The
  /// protected notification request UUID was already rotated and is never part
  /// of this projection.
  func takeProjection(
    completion: @escaping (Result<[String: Any], CompanionStoreError>) -> Void
  ) {
    CompanionProtectedStore.shared.takePendingNativeRoute { result in
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

  @objc private func retryDeferredRoute() {
    lock.lock()
    let requestId = deferredRequestId
    deferredRequestId = nil
    lock.unlock()
    guard let requestId else { return }
    consume(requestId: requestId, completion: {})
  }

  private func consume(requestId: String, completion: @escaping () -> Void) {
    guard !IOSAccountDeletionReceiptStore.shared.hasPendingOrUnreadableReceipt(),
      !IOSAccountDeletionRecoveryStore.shared.hasPendingOrUnreadableJournal()
    else {
      completion()
      return
    }
    CompanionProtectedStore.shared.consumeReminderRouteRequest(requestId) {
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
        NotificationCenter.default.post(
          name: .companionNativeRouteAvailable,
          object: self,
          userInfo: ["kind": "available"]
        )
      case .failure(.storageUnavailable)
        where !UIApplication.shared.isProtectedDataAvailable:
        self.lock.lock()
        self.deferredRequestId = requestId
        self.lock.unlock()
      case .failure:
        break
      }
      completion()
    }
  }

  private static func opaqueRequestId(
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
