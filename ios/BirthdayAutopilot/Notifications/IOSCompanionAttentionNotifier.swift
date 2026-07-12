import Foundation
import UserNotifications

enum IOSCompanionAttentionKind: String, CaseIterable {
  case composer
  case contacts
  case coordination
  case reminders
}

/// Adds at most one generic, content-free local notification per repair area.
/// It never requests notification permission. A denied or undetermined setting
/// simply leaves the in-app attention projection as the source of truth.
final class IOSCompanionAttentionNotifier {
  static let shared = IOSCompanionAttentionNotifier()
  static let identifierPrefix = "birthday-autopilot.attention.v1."

  private let center: UNUserNotificationCenter
  private let lock = NSLock()
  private var inFlight = Set<IOSCompanionAttentionKind>()
  private var drainWaiters: [UUID: (Bool) -> Void] = [:]
  private var cancellationDepth = 0

  init(center: UNUserNotificationCenter = .current()) {
    self.center = center
  }

  func notify(_ kind: IOSCompanionAttentionKind) {
    guard begin(kind) else { return }
    guard !Self.accountDeletionBlocksAttention else {
      finish(kind)
      return
    }
    center.getNotificationSettings { [weak self] settings in
      guard let self, Self.permitsExistingAuthorization(settings.authorizationStatus),
        !Self.accountDeletionBlocksAttention
      else {
        self?.finish(kind)
        return
      }
      var calendar = Calendar(identifier: .gregorian)
      calendar.timeZone = .autoupdatingCurrent
      let civilDate = CompanionProtectedStore.civilDate(for: Date(), calendar: calendar)
      let identifier = Self.identifierPrefix + kind.rawValue + "." + civilDate
      self.center.getPendingNotificationRequests { pending in
        self.center.getDeliveredNotifications { delivered in
          guard !Self.accountDeletionBlocksAttention else {
            self.finish(kind)
            return
          }
          let alreadyPresent = pending.contains { $0.identifier == identifier }
            || delivered.contains { $0.request.identifier == identifier }
          guard !alreadyPresent else {
            self.finish(kind)
            return
          }
          let stalePending = pending.map(\.identifier).filter {
            Self.attentionKind(from: $0) == kind && $0 != identifier
          }
          let staleDelivered = delivered.map { $0.request.identifier }.filter {
            Self.attentionKind(from: $0) == kind && $0 != identifier
          }
          self.center.removePendingNotificationRequests(withIdentifiers: stalePending)
          self.center.removeDeliveredNotifications(withIdentifiers: staleDelivered)
          CompanionProtectedStore.shared.claimAttentionNotification(
            kind: kind,
            civilDate: civilDate
          ) { [weak self] result in
            guard let self else { return }
            guard case .success(true) = result,
              !Self.accountDeletionBlocksAttention
            else {
              self.finish(kind)
              return
            }
            self.center.add(Self.request(kind: kind, identifier: identifier)) {
              [weak self] _ in self?.finish(kind)
            }
          }
        }
      }
    }
  }

  static func isAttentionIdentifier(_ value: String) -> Bool {
    attentionKind(from: value) != nil
  }

  private static func attentionKind(
    from identifier: String
  ) -> IOSCompanionAttentionKind? {
    guard identifier.hasPrefix(identifierPrefix) else { return nil }
    let suffix = String(identifier.dropFirst(identifierPrefix.count))
    let components = suffix.split(separator: ".", omittingEmptySubsequences: false)
    guard components.count == 2,
      String(components[1]).range(
        of: "^[0-9]{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12][0-9]|3[01])$",
        options: .regularExpression
      ) != nil
    else { return nil }
    return IOSCompanionAttentionKind(rawValue: String(components[0]))
  }

  /// Atomically blocks new adds before waiting for already-started requests.
  /// The caller must pair every invocation with `endCancellationDrain`, even
  /// when the bounded wait returns false.
  func beginCancellationDrain(_ completion: @escaping (Bool) -> Void) {
    let waiterID = UUID()
    lock.lock()
    cancellationDepth += 1
    guard !inFlight.isEmpty else {
      lock.unlock()
      DispatchQueue.main.async { completion(true) }
      return
    }
    drainWaiters[waiterID] = completion
    lock.unlock()
    DispatchQueue.main.asyncAfter(deadline: .now() + 5) { [weak self] in
      guard let self else { return }
      self.lock.lock()
      let waiter = self.drainWaiters.removeValue(forKey: waiterID)
      self.lock.unlock()
      waiter?(false)
    }
  }

  func endCancellationDrain() {
    lock.lock()
    if cancellationDepth > 0 { cancellationDepth -= 1 }
    lock.unlock()
  }

  private func begin(_ kind: IOSCompanionAttentionKind) -> Bool {
    lock.lock()
    defer { lock.unlock() }
    guard cancellationDepth == 0 else { return false }
    return inFlight.insert(kind).inserted
  }

  private func finish(_ kind: IOSCompanionAttentionKind) {
    lock.lock()
    inFlight.remove(kind)
    let waiters: [(Bool) -> Void]
    if inFlight.isEmpty {
      waiters = Array(drainWaiters.values)
      drainWaiters.removeAll()
    } else {
      waiters = []
    }
    lock.unlock()
    waiters.forEach { waiter in DispatchQueue.main.async { waiter(true) } }
  }

  private static var accountDeletionBlocksAttention: Bool {
    IOSAccountDeletionReceiptStore.shared.hasPendingOrUnreadableReceipt()
      || IOSAccountDeletionRecoveryStore.shared.hasPendingOrUnreadableJournal()
  }

  private static func permitsExistingAuthorization(
    _ status: UNAuthorizationStatus
  ) -> Bool {
    switch status {
    case .authorized, .provisional, .ephemeral:
      return true
    case .denied, .notDetermined:
      return false
    @unknown default:
      return false
    }
  }

  private static func request(
    kind: IOSCompanionAttentionKind,
    identifier: String
  ) -> UNNotificationRequest {
    let hindi = Locale.preferredLanguages.first?.lowercased().hasPrefix("hi") == true
    let content = UNMutableNotificationContent()
    content.title = hindi ? "Birthday Autopilot पर ध्यान दें" : "Birthday Autopilot needs attention"
    switch kind {
    case .contacts:
      content.body = hindi
        ? "Google Contacts को फिर से जोड़ने के लिए ऐप खोलें।"
        : "Open the app to reconnect Google Contacts."
    case .reminders:
      content.body = hindi
        ? "जन्मदिन रिमाइंडर ठीक करने के लिए ऐप खोलें।"
        : "Open the app to repair birthday reminders."
    case .coordination:
      content.body = hindi
        ? "सुरक्षा स्थिति जाँचने के लिए ऐप खोलें।"
        : "Open the app to check the sending safety status."
    case .composer:
      content.body = hindi
        ? "मैसेज कंपोज़र की स्थिति जाँचने के लिए ऐप खोलें।"
        : "Open the app to check the message composer."
    }
    content.sound = .default
    content.categoryIdentifier = "BIRTHDAY_AUTOPILOT_ATTENTION"
    content.threadIdentifier = "birthday-autopilot-attention"
    content.userInfo = [:]
    return UNNotificationRequest(
      identifier: identifier,
      content: content,
      trigger: nil
    )
  }
}
