import Foundation

struct IOSAccountDeletionReceipt: Codable, Equatable {
  let schemaVersion: Int
  let operationId: String
  let recordedAt: Date
  let completedAt: Date?
  let localDataErased: Bool
  let remoteDeletionComplete: Bool
  let externalSmsCopiesNotErased: Bool
}

/// A content-free receipt marker written before destructive local cleanup.
///
/// This file has no account, provider, contact, message, or SDK token value. It
/// retains only a native-private random deletion receipt capability plus
/// content-free lifecycle proof after the encrypted account stores and their
/// Keychain keys are destroyed. Complete file protection and backup exclusion
/// keep it device-local and out of the React Native boundary. Remote acceptance
/// is proven separately: an ambiguous reviewed local wipe is paired with the
/// equality-only IOSAccountDeletionRecoveryStore through exact completion;
/// receipt status or exact-account replay disables further identity retry as
/// soon as server acceptance is proven.
final class IOSAccountDeletionReceiptStore {
  static let shared = IOSAccountDeletionReceiptStore()

  private static let schemaVersion = 1
  private static let retention: TimeInterval = 365 * 24 * 60 * 60
  private static let allowedKeys: Set<String> = [
    "completedAt", "externalSmsCopiesNotErased", "localDataErased",
    "operationId", "recordedAt", "remoteDeletionComplete", "schemaVersion",
  ]

  private let queue = DispatchQueue(
    label: "com.yashsomani.birthdayautopilot.account-deletion-receipt",
    qos: .utility
  )
  private let fileManager: FileManager

  private init(fileManager: FileManager = .default) {
    self.fileManager = fileManager
  }

  func current(now: Date = Date()) -> IOSAccountDeletionReceipt? {
    queue.sync { loadCurrent(now: now) }
  }

  func hasPendingOrUnreadableReceipt(now: Date = Date()) -> Bool {
    queue.sync {
      if let receipt = loadCurrent(now: now) {
        return !receipt.remoteDeletionComplete
      }
      guard let url = try? receiptFileURL() else { return true }
      return fileManager.fileExists(atPath: url.path)
    }
  }

  /// Retires the previous completed proof before any ordinary identity can be
  /// created for a later account lifecycle. Recovery is cleared first while
  /// COMPLETED remains authoritative; receipt removal happens only afterward.
  func retireCompletedReceiptBeforeNewIdentity(now: Date = Date()) -> Bool {
    queue.sync {
      guard let url = try? receiptFileURL() else { return false }
      guard fileManager.fileExists(atPath: url.path) else {
        return !IOSAccountDeletionRecoveryStore.shared
          .hasPendingOrUnreadableJournal()
      }
      guard let receipt = loadCurrent(now: now, expireCompleted: false),
        receipt.remoteDeletionComplete,
        IOSAccountDeletionRecoveryStore.shared
          .clearSynchronouslyForCompletedReceiptRetirement(
            operationId: receipt.operationId
          )
      else { return false }
      do {
        try fileManager.removeItem(at: url)
        return !fileManager.fileExists(atPath: url.path)
      } catch {
        return false
      }
    }
  }

  func recordRemoteDraining(
    operationId: String,
    now: Date = Date(),
    completion: @escaping (Bool) -> Void
  ) {
    recordPending(operationId: operationId, now: now, completion: completion)
  }

  func recordPending(
    operationId: String,
    now: Date = Date(),
    completion: @escaping (Bool) -> Void
  ) {
    queue.async {
      guard Self.isCanonicalUUID(operationId) else {
        DispatchQueue.main.async { completion(false) }
        return
      }
      // A write path must observe an expired completed receipt as terminal; it
      // may replace it only for a different reviewed operation, never expire it
      // and then recreate the same operation as PENDING in one call.
      let existing = self.loadCurrent(now: now, expireCompleted: false)
      let receiptFileExists = (try? self.receiptFileURL()).map {
        self.fileManager.fileExists(atPath: $0.path)
      } ?? true
      if existing == nil, receiptFileExists {
        DispatchQueue.main.async { completion(false) }
        return
      }
      if existing?.operationId == operationId,
        existing?.remoteDeletionComplete == true
      {
        DispatchQueue.main.async { completion(true) }
        return
      }
      guard
        existing == nil || existing?.remoteDeletionComplete == true
          || existing?.operationId == operationId
      else {
        DispatchQueue.main.async { completion(false) }
        return
      }
      let receipt = IOSAccountDeletionReceipt(
        schemaVersion: Self.schemaVersion,
        operationId: operationId,
        recordedAt: existing?.operationId == operationId ? existing?.recordedAt ?? now : now,
        completedAt: nil,
        localDataErased:
          existing?.operationId == operationId ? existing?.localDataErased ?? false : false,
        remoteDeletionComplete: false,
        externalSmsCopiesNotErased: true
      )
      let success = self.persist(receipt)
      DispatchQueue.main.async { completion(success) }
    }
  }

  func markLocalDataErased(
    operationId: String,
    now: Date = Date(),
    completion: @escaping (Bool) -> Void
  ) {
    queue.async {
      guard let existing = self.loadCurrent(now: now),
        existing.operationId == operationId,
        !existing.remoteDeletionComplete
      else {
        DispatchQueue.main.async { completion(false) }
        return
      }
      let receipt = IOSAccountDeletionReceipt(
        schemaVersion: Self.schemaVersion,
        operationId: existing.operationId,
        recordedAt: existing.recordedAt,
        completedAt: nil,
        localDataErased: true,
        remoteDeletionComplete: false,
        externalSmsCopiesNotErased: true
      )
      let success = self.persist(receipt)
      DispatchQueue.main.async { completion(success) }
    }
  }

  func markRemoteDeletionComplete(
    operationId: String,
    completedAt: Date,
    now: Date = Date(),
    completion: @escaping (Bool) -> Void
  ) {
    queue.async {
      guard let existing = self.loadCurrent(now: now),
        existing.operationId == operationId,
        existing.localDataErased,
        !existing.remoteDeletionComplete,
        completedAt.timeIntervalSince1970.isFinite,
        completedAt.timeIntervalSince1970 >= 0
      else {
        DispatchQueue.main.async { completion(false) }
        return
      }
      let receipt = IOSAccountDeletionReceipt(
        schemaVersion: Self.schemaVersion,
        operationId: existing.operationId,
        recordedAt: existing.recordedAt,
        completedAt: completedAt,
        localDataErased: true,
        remoteDeletionComplete: true,
        externalSmsCopiesNotErased: true
      )
      let success = self.persist(receipt)
      DispatchQueue.main.async { completion(success) }
    }
  }

  private func loadCurrent(
    now: Date,
    expireCompleted: Bool = true
  ) -> IOSAccountDeletionReceipt? {
    guard let url = try? receiptFileURL(),
      fileManager.fileExists(atPath: url.path),
      let data = try? Data(contentsOf: url, options: [.mappedIfSafe]),
      data.count <= 4_096,
      let raw = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
      Set(raw.keys) == Self.allowedKeys
        || Set(raw.keys) == Self.allowedKeys.subtracting(["completedAt"])
    else { return nil }

    let decoder = JSONDecoder()
    decoder.dateDecodingStrategy = .millisecondsSince1970
    guard let receipt = try? decoder.decode(IOSAccountDeletionReceipt.self, from: data),
      receipt.schemaVersion == Self.schemaVersion,
      Self.isCanonicalUUID(receipt.operationId),
      receipt.recordedAt.timeIntervalSince1970.isFinite,
      receipt.recordedAt.timeIntervalSince1970 >= 0,
      receipt.externalSmsCopiesNotErased,
      receipt.remoteDeletionComplete
        ? receipt.localDataErased && receipt.completedAt != nil
        : receipt.completedAt == nil
    else { return nil }

    let age = now.timeIntervalSince(receipt.completedAt ?? receipt.recordedAt)
    // Pending/unreadable evidence never expires. A backwards clock jump also
    // keeps the readable marker intact; it must never replace the private
    // bearer or authorize cleanup of a later account.
    guard !expireCompleted || !receipt.remoteDeletionComplete || age < 0
      || age < Self.retention
    else {
      // The completed receipt remains authoritative while the matching
      // equality journal is synchronously removed and verified. Only then may
      // the receipt be removed. A crash between those operations leaves the
      // completed receipt in place, so recovery-only state can never recreate
      // PENDING. Mismatch or unreadable recovery keeps completion fail-closed.
      guard IOSAccountDeletionRecoveryStore.shared
        .clearSynchronouslyForCompletedReceiptRetirement(
          operationId: receipt.operationId
        )
      else {
        return receipt
      }
      do {
        try fileManager.removeItem(at: url)
        return fileManager.fileExists(atPath: url.path) ? receipt : nil
      } catch {
        return receipt
      }
    }
    return receipt
  }

  private func persist(_ receipt: IOSAccountDeletionReceipt) -> Bool {
    do {
      let encoder = JSONEncoder()
      encoder.dateEncodingStrategy = .millisecondsSince1970
      encoder.outputFormatting = [.sortedKeys]
      let data = try encoder.encode(receipt)
      guard data.count <= 4_096 else { return false }
      return persistProtectedData(data)
    } catch {
      return false
    }
  }

  private func persistProtectedData(_ data: Data) -> Bool {
    do {
      let url = try receiptFileURL()
      try data.write(to: url, options: [.atomic, .completeFileProtection])
      try fileManager.setAttributes(
        [.protectionKey: FileProtectionType.complete],
        ofItemAtPath: url.path
      )
      var resourceValues = URLResourceValues()
      resourceValues.isExcludedFromBackup = true
      var mutableURL = url
      try mutableURL.setResourceValues(resourceValues)
      return fileManager.fileExists(atPath: url.path)
    } catch {
      return false
    }
  }

  private func receiptFileURL() throws -> URL {
    let base = try fileManager.url(
      for: .applicationSupportDirectory,
      in: .userDomainMask,
      appropriateFor: nil,
      create: true
    )
    let directory = base.appendingPathComponent(
      "BirthdayAutopilotDeletionReceipt",
      isDirectory: true
    )
    try fileManager.createDirectory(
      at: directory,
      withIntermediateDirectories: true,
      attributes: [.protectionKey: FileProtectionType.complete]
    )
    try fileManager.setAttributes(
      [.protectionKey: FileProtectionType.complete],
      ofItemAtPath: directory.path
    )
    var resourceValues = URLResourceValues()
    resourceValues.isExcludedFromBackup = true
    var mutableDirectory = directory
    try mutableDirectory.setResourceValues(resourceValues)
    return directory.appendingPathComponent("account-deletion-receipt-v1.json")
  }

  private static func isCanonicalUUID(_ value: String) -> Bool {
    guard let uuid = UUID(uuidString: value) else { return false }
    return uuid.uuidString.lowercased() == value
      && value.range(
        of: "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
        options: .regularExpression
      ) != nil
  }

}
