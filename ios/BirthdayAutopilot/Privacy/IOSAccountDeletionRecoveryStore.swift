import CryptoKit
import Foundation
import Security

struct IOSAccountDeletionRecoveryJournal: Codable, Equatable {
  let schemaVersion: Int
  let operationId: String
  let accountBindingSalt: String
  let firebaseUIDDigest: String
  let googleSubjectDigest: String
  let recordedAt: Date
  let remoteAcceptanceConfirmed: Bool
  let retryAuthorized: Bool
}

/// Native-private recovery evidence for a reviewed local wipe whose remote
/// account-deletion acceptance is still unknown.
///
/// The journal retains the raw random receipt capability because an exact
/// replay may be required. It does not retain either account identifier: a
/// random per-operation salt and a domain-separated digest support equality
/// checks only. Complete file protection and backup exclusion keep this state
/// outside React Native, device backup, logs, analytics, and ordinary app data.
final class IOSAccountDeletionRecoveryStore {
  static let shared = IOSAccountDeletionRecoveryStore()

  private static let schemaVersion = 1
  private static let saltByteCount = 32
  private static let firebaseUIDDomain = Data(
    "birthday-ios-account-deletion-recovery-firebase-uid-v1\0".utf8
  )
  private static let googleSubjectDomain = Data(
    "birthday-ios-account-deletion-recovery-google-subject-v1\0".utf8
  )
  private static let allowedKeys: Set<String> = [
    "accountBindingSalt", "firebaseUIDDigest", "googleSubjectDigest",
    "operationId", "recordedAt", "remoteAcceptanceConfirmed",
    "retryAuthorized", "schemaVersion",
  ]

  private let queue = DispatchQueue(
    label: "com.yashsomani.birthdayautopilot.account-deletion-recovery",
    qos: .utility
  )
  private let fileManager: FileManager

  private init(fileManager: FileManager = .default) {
    self.fileManager = fileManager
  }

  func current() -> IOSAccountDeletionRecoveryJournal? {
    queue.sync { loadCurrent() }
  }

  func hasPendingOrUnreadableJournal() -> Bool {
    queue.sync {
      if loadCurrent() != nil { return true }
      guard let url = try? recoveryFileURL() else { return true }
      return fileManager.fileExists(atPath: url.path)
    }
  }

  func isAwaitingRemoteAcceptance(operationId: String) -> Bool {
    queue.sync {
      guard let journal = loadCurrent() else { return false }
      return !journal.remoteAcceptanceConfirmed
        && Self.constantTimeEqual(journal.operationId, operationId)
    }
  }

  func retryAuthorizedOperationId() -> String? {
    queue.sync {
      guard let journal = loadCurrent(), !journal.remoteAcceptanceConfirmed,
        journal.retryAuthorized
      else { return nil }
      return journal.operationId
    }
  }

  func isRetryAuthorized(operationId: String) -> Bool {
    queue.sync {
      guard let journal = loadCurrent(), !journal.remoteAcceptanceConfirmed,
        journal.retryAuthorized
      else { return false }
      return Self.constantTimeEqual(journal.operationId, operationId)
    }
  }

  func recordReviewedLocalWipe(
    operationId: String,
    binding: IOSNativeGoogleAccountBinding,
    now: Date = Date(),
    completion: @escaping (Bool) -> Void
  ) {
    queue.async {
      guard Self.isCanonicalUUID(operationId), Self.isValidBinding(binding),
        now.timeIntervalSince1970.isFinite, now.timeIntervalSince1970 >= 0
      else {
        DispatchQueue.main.async { completion(false) }
        return
      }

      if let existing = self.loadCurrent() {
        let success =
          Self.constantTimeEqual(existing.operationId, operationId)
          && Self.matches(binding: binding, journal: existing)
        DispatchQueue.main.async { completion(success) }
        return
      }
      guard let url = try? self.recoveryFileURL(),
        !self.fileManager.fileExists(atPath: url.path),
        let salt = Self.randomSalt()
      else {
        DispatchQueue.main.async { completion(false) }
        return
      }
      let journal = IOSAccountDeletionRecoveryJournal(
        schemaVersion: Self.schemaVersion,
        operationId: operationId,
        accountBindingSalt: salt.base64EncodedString(),
        firebaseUIDDigest: Self.identityDigest(
          domain: Self.firebaseUIDDomain,
          operationId: operationId,
          value: binding.firebaseUID,
          salt: salt
        ),
        googleSubjectDigest: Self.identityDigest(
          domain: Self.googleSubjectDomain,
          operationId: operationId,
          value: binding.googleSubject,
          salt: salt
        ),
        recordedAt: now,
        remoteAcceptanceConfirmed: false,
        retryAuthorized: false
      )
      let success = self.persist(journal)
      DispatchQueue.main.async { completion(success) }
    }
  }

  func markRetryAuthorized(
    operationId: String,
    completion: @escaping (Bool) -> Void
  ) {
    queue.async {
      guard let existing = self.loadCurrent(),
        !existing.remoteAcceptanceConfirmed,
        Self.constantTimeEqual(existing.operationId, operationId)
      else {
        DispatchQueue.main.async { completion(false) }
        return
      }
      if existing.retryAuthorized {
        DispatchQueue.main.async { completion(true) }
        return
      }
      let updated = IOSAccountDeletionRecoveryJournal(
        schemaVersion: existing.schemaVersion,
        operationId: existing.operationId,
        accountBindingSalt: existing.accountBindingSalt,
        firebaseUIDDigest: existing.firebaseUIDDigest,
        googleSubjectDigest: existing.googleSubjectDigest,
        recordedAt: existing.recordedAt,
        remoteAcceptanceConfirmed: existing.remoteAcceptanceConfirmed,
        retryAuthorized: true
      )
      let success = self.persist(updated)
      DispatchQueue.main.async { completion(success) }
    }
  }

  func matchesRetryAccount(
    operationId: String,
    binding: IOSNativeGoogleAccountBinding
  ) -> Bool {
    queue.sync {
      guard let journal = loadCurrent(), !journal.remoteAcceptanceConfirmed,
        journal.retryAuthorized,
        Self.constantTimeEqual(journal.operationId, operationId)
      else { return false }
      return Self.matches(binding: binding, journal: journal)
    }
  }

  func matchesRetryGoogleSubject(
    operationId: String,
    googleSubject: String
  ) -> Bool {
    queue.sync {
      guard let journal = loadCurrent(), !journal.remoteAcceptanceConfirmed,
        journal.retryAuthorized,
        Self.constantTimeEqual(journal.operationId, operationId),
        IOSPeopleValuePolicy.googleSubject(googleSubject) == googleSubject,
        let salt = Data(base64Encoded: journal.accountBindingSalt)
      else { return false }
      return Self.constantTimeEqual(
        Self.identityDigest(
          domain: Self.googleSubjectDomain,
          operationId: operationId,
          value: googleSubject,
          salt: salt
        ),
        journal.googleSubjectDigest
      )
    }
  }

  func matchesRetryFirebaseUID(
    operationId: String,
    firebaseUID: String
  ) -> Bool {
    queue.sync {
      guard let journal = loadCurrent(), !journal.remoteAcceptanceConfirmed,
        journal.retryAuthorized,
        Self.constantTimeEqual(journal.operationId, operationId),
        IOSPeopleValuePolicy.providerIdentifier(firebaseUID, maximumBytes: 256),
        let salt = Data(base64Encoded: journal.accountBindingSalt)
      else { return false }
      return Self.constantTimeEqual(
        Self.identityDigest(
          domain: Self.firebaseUIDDomain,
          operationId: operationId,
          value: firebaseUID,
          salt: salt
        ),
        journal.firebaseUIDDigest
      )
    }
  }

  func markRemoteAcceptanceConfirmed(
    operationId: String,
    completion: @escaping (Bool) -> Void
  ) {
    queue.async {
      guard let existing = self.loadCurrent(),
        Self.constantTimeEqual(existing.operationId, operationId)
      else {
        DispatchQueue.main.async { completion(false) }
        return
      }
      if existing.remoteAcceptanceConfirmed {
        DispatchQueue.main.async { completion(true) }
        return
      }
      let updated = IOSAccountDeletionRecoveryJournal(
        schemaVersion: existing.schemaVersion,
        operationId: existing.operationId,
        accountBindingSalt: existing.accountBindingSalt,
        firebaseUIDDigest: existing.firebaseUIDDigest,
        googleSubjectDigest: existing.googleSubjectDigest,
        recordedAt: existing.recordedAt,
        remoteAcceptanceConfirmed: true,
        retryAuthorized: false
      )
      let success = self.persist(updated)
      DispatchQueue.main.async { completion(success) }
    }
  }

  func clearAfterRemoteCompletion(
    operationId: String,
    completion: @escaping (Bool) -> Void
  ) {
    queue.async {
      guard let existing = self.loadCurrent(),
        Self.constantTimeEqual(existing.operationId, operationId),
        let url = try? self.recoveryFileURL()
      else {
        DispatchQueue.main.async { completion(false) }
        return
      }
      do {
        try self.fileManager.removeItem(at: url)
        DispatchQueue.main.async {
          completion(!self.fileManager.fileExists(atPath: url.path))
        }
      } catch {
        DispatchQueue.main.async { completion(false) }
      }
    }
  }

  /// Used only while the completed receipt still exists as the authoritative
  /// proof. The receipt queue removes that proof only after this exact matching
  /// journal deletion succeeds. This store never calls back into the receipt
  /// store, so the cross-store sync is one-way and cannot deadlock.
  func clearSynchronouslyForCompletedReceiptRetirement(
    operationId: String
  ) -> Bool {
    queue.sync {
      guard let url = try? recoveryFileURL() else { return false }
      guard fileManager.fileExists(atPath: url.path) else { return true }
      guard let existing = loadCurrent(),
        Self.constantTimeEqual(existing.operationId, operationId)
      else { return false }
      do {
        try fileManager.removeItem(at: url)
        return !fileManager.fileExists(atPath: url.path)
      } catch {
        return false
      }
    }
  }

  private func loadCurrent() -> IOSAccountDeletionRecoveryJournal? {
    guard let url = try? recoveryFileURL(),
      fileManager.fileExists(atPath: url.path),
      let data = try? Data(contentsOf: url, options: [.mappedIfSafe]),
      data.count <= 4_096,
      let raw = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
      Set(raw.keys) == Self.allowedKeys
    else { return nil }

    let decoder = JSONDecoder()
    decoder.dateDecodingStrategy = .millisecondsSince1970
    guard let journal = try? decoder.decode(
      IOSAccountDeletionRecoveryJournal.self,
      from: data
    ), journal.schemaVersion == Self.schemaVersion,
      Self.isCanonicalUUID(journal.operationId),
      let salt = Data(base64Encoded: journal.accountBindingSalt),
      salt.count == Self.saltByteCount,
      journal.firebaseUIDDigest.range(
        of: "^[0-9a-f]{64}$",
        options: .regularExpression
      ) != nil,
      journal.googleSubjectDigest.range(
        of: "^[0-9a-f]{64}$",
        options: .regularExpression
      ) != nil,
      !(journal.remoteAcceptanceConfirmed && journal.retryAuthorized),
      journal.recordedAt.timeIntervalSince1970.isFinite,
      journal.recordedAt.timeIntervalSince1970 >= 0
    else { return nil }
    return journal
  }

  private func persist(_ journal: IOSAccountDeletionRecoveryJournal) -> Bool {
    do {
      let encoder = JSONEncoder()
      encoder.dateEncodingStrategy = .millisecondsSince1970
      encoder.outputFormatting = [.sortedKeys]
      let data = try encoder.encode(journal)
      guard data.count <= 4_096 else { return false }
      let url = try recoveryFileURL()
      try data.write(to: url, options: [.atomic, .completeFileProtection])
      try fileManager.setAttributes(
        [.protectionKey: FileProtectionType.complete],
        ofItemAtPath: url.path
      )
      var values = URLResourceValues()
      values.isExcludedFromBackup = true
      var mutableURL = url
      try mutableURL.setResourceValues(values)
      return fileManager.fileExists(atPath: url.path)
    } catch {
      return false
    }
  }

  private func recoveryFileURL() throws -> URL {
    let base = try fileManager.url(
      for: .applicationSupportDirectory,
      in: .userDomainMask,
      appropriateFor: nil,
      create: true
    )
    let directory = base.appendingPathComponent(
      "BirthdayAutopilotDeletionRecovery",
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
    var values = URLResourceValues()
    values.isExcludedFromBackup = true
    var mutableDirectory = directory
    try mutableDirectory.setResourceValues(values)
    return directory.appendingPathComponent("account-deletion-recovery-v1.json")
  }

  private static func randomSalt() -> Data? {
    var salt = Data(count: saltByteCount)
    let status = salt.withUnsafeMutableBytes { bytes in
      guard let address = bytes.baseAddress else { return errSecParam }
      return SecRandomCopyBytes(kSecRandomDefault, saltByteCount, address)
    }
    return status == errSecSuccess ? salt : nil
  }

  private static func matches(
    binding: IOSNativeGoogleAccountBinding,
    journal: IOSAccountDeletionRecoveryJournal
  ) -> Bool {
    guard isValidBinding(binding),
      let salt = Data(base64Encoded: journal.accountBindingSalt),
      salt.count == saltByteCount
    else { return false }
    return constantTimeEqual(
      identityDigest(
        domain: firebaseUIDDomain,
        operationId: journal.operationId,
        value: binding.firebaseUID,
        salt: salt
      ),
      journal.firebaseUIDDigest
    ) && constantTimeEqual(
      identityDigest(
        domain: googleSubjectDomain,
        operationId: journal.operationId,
        value: binding.googleSubject,
        salt: salt
      ),
      journal.googleSubjectDigest
    )
  }

  private static func identityDigest(
    domain: Data,
    operationId: String,
    value: String,
    salt: Data
  ) -> String {
    var data = domain
    data.append(salt)
    appendLengthPrefixed(operationId, to: &data)
    appendLengthPrefixed(value, to: &data)
    return SHA256.hash(data: data)
      .map { String(format: "%02x", $0) }
      .joined()
  }

  private static func appendLengthPrefixed(_ value: String, to data: inout Data) {
    let bytes = Data(value.utf8)
    var length = UInt64(bytes.count).bigEndian
    withUnsafeBytes(of: &length) { data.append(contentsOf: $0) }
    data.append(bytes)
  }

  private static func isValidBinding(_ binding: IOSNativeGoogleAccountBinding) -> Bool {
    IOSPeopleValuePolicy.providerIdentifier(binding.firebaseUID, maximumBytes: 256)
      && IOSPeopleValuePolicy.googleSubject(binding.googleSubject) == binding.googleSubject
  }

  private static func constantTimeEqual(_ lhs: String, _ rhs: String) -> Bool {
    let left = Array(lhs.utf8)
    let right = Array(rhs.utf8)
    guard left.count == right.count else { return false }
    var difference: UInt8 = 0
    for index in left.indices { difference |= left[index] ^ right[index] }
    return difference == 0
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
