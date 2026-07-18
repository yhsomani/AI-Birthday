import CryptoKit
import Foundation
import Security

enum IOSCompanionWipeRecoveryKind: String, Codable {
  case protectedStoreReset = "protected-store-reset"
  case signOutWipe = "sign-out-wipe"
  case wipeLocalData = "wipe-local-data"
}

/// Content-free crash-recovery evidence for companion reset and multi-store
/// privacy wipes. Account identifiers are represented only by per-operation,
/// domain-separated equality digests. `accountGeneration` is an app-minted
/// random UUID and is retained so an interrupted wipe-local-data operation can
/// rebuild the exact empty binding after the People key has already vanished.
struct IOSCompanionWipeRecoveryJournal: Codable, Equatable {
  let schemaVersion: Int
  let markerId: String
  let operationId: String?
  let kind: IOSCompanionWipeRecoveryKind
  let resetSafetyGeneration: String
  var resetCivilDates: [String]
  var resetOverflowed: Bool
  var companionResetRequired: Bool
  var companionResetInstalled: Bool
  var notificationCleanupVerified: Bool
  var reservationJournalDestroyed: Bool
  var localCleanupComplete: Bool
  let accountBindingSalt: String?
  let firebaseUIDDigest: String?
  let googleSubjectDigest: String?
  let accountGeneration: String?
  let recordedAt: Date
  var updatedAt: Date
}

/// Device-local recovery journal independent of both encrypted native stores
/// and their Keychain keys. No value from this file crosses React Native.
final class IOSCompanionWipeRecoveryStore {
  static let shared = IOSCompanionWipeRecoveryStore()

  private static let schemaVersion = 1
  private static let maximumFileBytes = 4_096
  private static let maximumResetDates = 8
  private static let saltByteCount = 32
  private static let firebaseUIDDomain = Data(
    "birthday-ios-companion-wipe-firebase-uid-v1\0".utf8
  )
  private static let googleSubjectDomain = Data(
    "birthday-ios-companion-wipe-google-subject-v1\0".utf8
  )
  private static let allowedKeys: Set<String> = [
    "accountBindingSalt", "accountGeneration", "companionResetInstalled",
    "companionResetRequired", "firebaseUIDDigest", "googleSubjectDigest", "kind",
    "localCleanupComplete", "markerId", "notificationCleanupVerified",
    "operationId", "recordedAt", "reservationJournalDestroyed",
    "resetCivilDates", "resetOverflowed", "resetSafetyGeneration",
    "schemaVersion", "updatedAt",
  ]

  private let queue = DispatchQueue(
    label: "com.yashsomani.birthdayautopilot.companion-wipe-recovery",
    qos: .utility
  )
  private let fileManager: FileManager

  private init(fileManager: FileManager = .default) {
    self.fileManager = fileManager
  }

  func current() -> IOSCompanionWipeRecoveryJournal? {
    queue.sync { loadCurrent() }
  }

  func hasPendingOrUnreadableJournal() -> Bool {
    queue.sync {
      if loadCurrent() != nil { return true }
      guard let url = try? journalFileURL() else { return true }
      return fileManager.fileExists(atPath: url.path)
    }
  }

  /// Creates or advances the reset fence before any encrypted file or key is
  /// touched. A pre-existing saga is preserved and merely armed.
  func armCompanionReset(civilDate: String, now: Date = Date())
    -> IOSCompanionWipeRecoveryJournal?
  {
    queue.sync {
      guard Self.validCivilDate(civilDate), Self.validTimestamp(now) else {
        return nil
      }
      let url = try? journalFileURL()
      let exists = url.map { fileManager.fileExists(atPath: $0.path) } ?? true
      var journal: IOSCompanionWipeRecoveryJournal
      if let existing = loadCurrent() {
        journal = existing
      } else {
        guard !exists else { return nil }
        let markerId = UUID().uuidString.lowercased()
        journal = IOSCompanionWipeRecoveryJournal(
          schemaVersion: Self.schemaVersion,
          markerId: markerId,
          operationId: nil,
          kind: .protectedStoreReset,
          resetSafetyGeneration: UUID().uuidString.lowercased(),
          resetCivilDates: [],
          resetOverflowed: false,
          companionResetRequired: true,
          companionResetInstalled: false,
          notificationCleanupVerified: false,
          reservationJournalDestroyed: true,
          localCleanupComplete: true,
          accountBindingSalt: nil,
          firebaseUIDDigest: nil,
          googleSubjectDigest: nil,
          accountGeneration: nil,
          recordedAt: now,
          updatedAt: now
        )
      }
      Self.observe(civilDate: civilDate, in: &journal)
      journal.companionResetRequired = true
      journal.companionResetInstalled = false
      journal.notificationCleanupVerified = false
      if journal.kind == .wipeLocalData {
        // Replacing the companion snapshot after an empty binding was installed
        // removes that binding; exact-account local recovery must run again.
        journal.localCleanupComplete = false
      }
      journal.updatedAt = now
      return persist(journal) ? journal : nil
    }
  }

  /// Commits a reviewed multi-store wipe intent before notifications, SDK
  /// sessions, the People store, or the companion store are mutated.
  func beginSaga(
    kind: IOSCompanionWipeRecoveryKind,
    operationId: String,
    binding: IOSNativeGoogleAccountBinding,
    civilDate: String,
    now: Date = Date()
  ) -> Bool {
    queue.sync {
      guard kind != .protectedStoreReset, Self.canonicalUUID(operationId),
        Self.validBinding(binding), Self.validCivilDate(civilDate),
        Self.validTimestamp(now)
      else { return false }

      let url = try? journalFileURL()
      let exists = url.map { fileManager.fileExists(atPath: $0.path) } ?? true
      if var existing = loadCurrent() {
        guard existing.kind == kind,
          existing.operationId.map({ Self.constantTimeEqual($0, operationId) }) == true,
          Self.matches(binding: binding, journal: existing)
        else { return false }
        Self.observe(civilDate: civilDate, in: &existing)
        existing.updatedAt = now
        return persist(existing)
      }
      guard !exists, let salt = Self.randomSalt() else { return false }
      let markerId = UUID().uuidString.lowercased()
      let journal = IOSCompanionWipeRecoveryJournal(
        schemaVersion: Self.schemaVersion,
        markerId: markerId,
        operationId: operationId,
        kind: kind,
        resetSafetyGeneration: UUID().uuidString.lowercased(),
        resetCivilDates: [civilDate],
        resetOverflowed: false,
        companionResetRequired: false,
        companionResetInstalled: false,
        notificationCleanupVerified: false,
        reservationJournalDestroyed: false,
        localCleanupComplete: false,
        accountBindingSalt: salt.base64EncodedString(),
        firebaseUIDDigest: Self.identityDigest(
          domain: Self.firebaseUIDDomain,
          markerId: markerId,
          value: binding.firebaseUID,
          salt: salt
        ),
        googleSubjectDigest: Self.identityDigest(
          domain: Self.googleSubjectDomain,
          markerId: markerId,
          value: binding.googleSubject,
          salt: salt
        ),
        accountGeneration: binding.accountGeneration,
        recordedAt: now,
        updatedAt: now
      )
      return persist(journal)
    }
  }

  /// Extends an already-authoritative marker across civil-date/timezone
  /// changes. It never creates a marker: absence is handled by the protected
  /// store's first-install/reset path.
  func observeResetCivilDate(
    _ civilDate: String,
    now: Date = Date()
  ) -> IOSCompanionWipeRecoveryJournal? {
    queue.sync {
      guard Self.validCivilDate(civilDate), Self.validTimestamp(now),
        var journal = loadCurrent()
      else { return nil }
      let previousDates = journal.resetCivilDates
      let previousOverflow = journal.resetOverflowed
      Self.observe(civilDate: civilDate, in: &journal)
      guard journal.resetCivilDates != previousDates
        || journal.resetOverflowed != previousOverflow
      else { return journal }
      journal.updatedAt = now
      return persist(journal) ? journal : nil
    }
  }

  func markCompanionResetInstalled(markerId: String, now: Date = Date()) -> Bool {
    queue.sync {
      guard var journal = loadCurrent(),
        Self.constantTimeEqual(journal.markerId, markerId),
        journal.companionResetRequired, Self.validTimestamp(now)
      else { return false }
      journal.companionResetInstalled = true
      journal.updatedAt = now
      return persist(journal)
    }
  }

  func markLocalCleanupComplete(
    operationId: String,
    now: Date = Date()
  ) -> Bool {
    queue.sync {
      guard var journal = loadCurrent(),
        journal.kind != .protectedStoreReset,
        journal.operationId.map({ Self.constantTimeEqual($0, operationId) }) == true,
        Self.validTimestamp(now)
      else { return false }
      journal.localCleanupComplete = true
      journal.updatedAt = now
      return persist(journal)
    }
  }

  /// Records only the verified postcondition. Callers must first remove the
  /// complete reservation-capability journal and prove the file is absent.
  /// Capability loss never releases a server fence; logical expiry/account
  /// deletion remains authoritative, so retrying this step is fail-closed.
  func markReservationJournalDestroyed(
    operationId: String,
    now: Date = Date()
  ) -> Bool {
    queue.sync {
      guard var journal = loadCurrent(), journal.kind != .protectedStoreReset,
        journal.operationId.map({ Self.constantTimeEqual($0, operationId) }) == true,
        Self.validTimestamp(now)
      else { return false }
      journal.reservationJournalDestroyed = true
      journal.updatedAt = now
      return persist(journal)
    }
  }

  func markNotificationCleanupVerified(
    markerId: String,
    now: Date = Date()
  ) -> Bool {
    queue.sync {
      guard var journal = loadCurrent(),
        Self.constantTimeEqual(journal.markerId, markerId),
        journal.kind != .protectedStoreReset,
        journal.companionResetInstalled, Self.validTimestamp(now)
      else { return false }
      journal.notificationCleanupVerified = true
      journal.updatedAt = now
      return persist(journal)
    }
  }

  func markNotificationCleanupVerified(
    operationId: String,
    now: Date = Date()
  ) -> Bool {
    queue.sync {
      guard var journal = loadCurrent(), journal.kind != .protectedStoreReset,
        journal.operationId.map({ Self.constantTimeEqual($0, operationId) }) == true,
        journal.companionResetInstalled, Self.validTimestamp(now)
      else { return false }
      journal.notificationCleanupVerified = true
      journal.updatedAt = now
      return persist(journal)
    }
  }

  /// The marker is retired only after every destructive store has reached its
  /// durable postcondition. A reset-only marker needs only the verified
  /// replacement companion snapshot.
  func clearIfComplete(markerId: String) -> Bool {
    queue.sync {
      guard let journal = loadCurrent(),
        Self.constantTimeEqual(journal.markerId, markerId),
        journal.companionResetInstalled,
        journal.kind == .protectedStoreReset
          || (journal.localCleanupComplete && journal.notificationCleanupVerified
            && journal.reservationJournalDestroyed),
        let url = try? journalFileURL()
      else { return false }
      do {
        try fileManager.removeItem(at: url)
        return !fileManager.fileExists(atPath: url.path)
      } catch {
        return false
      }
    }
  }

  func clearCompletedSaga(operationId: String) -> Bool {
    queue.sync {
      guard let journal = loadCurrent(), journal.kind != .protectedStoreReset,
        journal.operationId.map({ Self.constantTimeEqual($0, operationId) }) == true,
        journal.companionResetInstalled, journal.notificationCleanupVerified,
        journal.reservationJournalDestroyed, journal.localCleanupComplete,
        let url = try? journalFileURL()
      else { return false }
      do {
        try fileManager.removeItem(at: url)
        return !fileManager.fileExists(atPath: url.path)
      } catch {
        return false
      }
    }
  }

  func matches(
    binding: IOSNativeGoogleAccountBinding,
    journal: IOSCompanionWipeRecoveryJournal
  ) -> Bool {
    Self.matches(binding: binding, journal: journal)
  }

  func matchesProviderIdentity(
    firebaseUID: String,
    googleSubject: String,
    journal: IOSCompanionWipeRecoveryJournal
  ) -> Bool {
    guard IOSPeopleValuePolicy.providerIdentifier(firebaseUID, maximumBytes: 256),
      IOSPeopleValuePolicy.googleSubject(googleSubject) == googleSubject,
      let saltValue = journal.accountBindingSalt,
      let salt = Data(base64Encoded: saltValue), salt.count == Self.saltByteCount,
      let firebaseDigest = journal.firebaseUIDDigest,
      let googleDigest = journal.googleSubjectDigest
    else { return false }
    return Self.constantTimeEqual(
      Self.identityDigest(
        domain: Self.firebaseUIDDomain,
        markerId: journal.markerId,
        value: firebaseUID,
        salt: salt
      ), firebaseDigest
    ) && Self.constantTimeEqual(
      Self.identityDigest(
        domain: Self.googleSubjectDomain,
        markerId: journal.markerId,
        value: googleSubject,
        salt: salt
      ), googleDigest
    )
  }

  func matchesGoogleSubject(
    _ googleSubject: String,
    journal: IOSCompanionWipeRecoveryJournal
  ) -> Bool {
    guard IOSPeopleValuePolicy.googleSubject(googleSubject) == googleSubject,
      let saltValue = journal.accountBindingSalt,
      let salt = Data(base64Encoded: saltValue), salt.count == Self.saltByteCount,
      let digest = journal.googleSubjectDigest
    else { return false }
    return Self.constantTimeEqual(
      Self.identityDigest(
        domain: Self.googleSubjectDomain,
        markerId: journal.markerId,
        value: googleSubject,
        salt: salt
      ), digest
    )
  }

  func matchesFirebaseUID(
    _ firebaseUID: String,
    journal: IOSCompanionWipeRecoveryJournal
  ) -> Bool {
    guard IOSPeopleValuePolicy.providerIdentifier(firebaseUID, maximumBytes: 256),
      let saltValue = journal.accountBindingSalt,
      let salt = Data(base64Encoded: saltValue), salt.count == Self.saltByteCount,
      let digest = journal.firebaseUIDDigest
    else { return false }
    return Self.constantTimeEqual(
      Self.identityDigest(
        domain: Self.firebaseUIDDomain,
        markerId: journal.markerId,
        value: firebaseUID,
        salt: salt
      ), digest
    )
  }

  private func loadCurrent() -> IOSCompanionWipeRecoveryJournal? {
    guard let url = try? journalFileURL(),
      fileManager.fileExists(atPath: url.path),
      let data = try? Data(contentsOf: url, options: [.mappedIfSafe]),
      data.count <= Self.maximumFileBytes,
      let raw = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
      Set(raw.keys).isSubset(of: Self.allowedKeys)
    else { return nil }

    let decoder = JSONDecoder()
    decoder.dateDecodingStrategy = .millisecondsSince1970
    guard let journal = try? decoder.decode(
      IOSCompanionWipeRecoveryJournal.self, from: data
    ), Self.validate(journal) else { return nil }
    return journal
  }

  private func persist(_ journal: IOSCompanionWipeRecoveryJournal) -> Bool {
    guard Self.validate(journal) else { return false }
    do {
      let encoder = JSONEncoder()
      encoder.dateEncodingStrategy = .millisecondsSince1970
      encoder.outputFormatting = [.sortedKeys]
      let data = try encoder.encode(journal)
      guard data.count <= Self.maximumFileBytes else { return false }
      let url = try journalFileURL()
      try data.write(to: url, options: [.atomic, .completeFileProtection])
      try fileManager.setAttributes(
        [.protectionKey: FileProtectionType.complete],
        ofItemAtPath: url.path
      )
      var values = URLResourceValues()
      values.isExcludedFromBackup = true
      var mutableURL = url
      try mutableURL.setResourceValues(values)
      return loadCurrent() == journal
    } catch {
      return false
    }
  }

  private func journalFileURL() throws -> URL {
    let base = try fileManager.url(
      for: .applicationSupportDirectory,
      in: .userDomainMask,
      appropriateFor: nil,
      create: true
    )
    let directory = base.appendingPathComponent(
      "BirthdayAutopilotCompanionWipeRecovery", isDirectory: true
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
    return directory.appendingPathComponent("companion-wipe-recovery-v1.json")
  }

  private static func observe(
    civilDate: String,
    in journal: inout IOSCompanionWipeRecoveryJournal
  ) {
    guard !journal.resetCivilDates.contains(civilDate) else { return }
    if journal.resetCivilDates.count < maximumResetDates {
      journal.resetCivilDates.append(civilDate)
      journal.resetCivilDates.sort()
    } else {
      journal.resetOverflowed = true
    }
    // A replacement snapshot installed on an earlier civil date must be
    // rebuilt before this independently durable marker can be retired.
    journal.companionResetRequired = true
    journal.companionResetInstalled = false
    journal.notificationCleanupVerified = false
    if journal.kind == .wipeLocalData {
      journal.localCleanupComplete = false
    }
  }

  private static func validate(_ journal: IOSCompanionWipeRecoveryJournal) -> Bool {
    guard journal.schemaVersion == schemaVersion,
      canonicalUUID(journal.markerId),
      canonicalUUID(journal.resetSafetyGeneration),
      journal.resetCivilDates.count <= maximumResetDates,
      Set(journal.resetCivilDates).count == journal.resetCivilDates.count,
      journal.resetCivilDates == journal.resetCivilDates.sorted(),
      journal.resetCivilDates.allSatisfy(validCivilDate),
      !journal.resetCivilDates.isEmpty,
      !journal.companionResetInstalled || journal.companionResetRequired,
      !journal.notificationCleanupVerified || journal.companionResetInstalled,
      validTimestamp(journal.recordedAt), validTimestamp(journal.updatedAt),
      journal.updatedAt >= journal.recordedAt
    else { return false }

    switch journal.kind {
    case .protectedStoreReset:
      return journal.operationId == nil && journal.localCleanupComplete
        && !journal.notificationCleanupVerified
        && journal.reservationJournalDestroyed
        && journal.accountBindingSalt == nil && journal.firebaseUIDDigest == nil
        && journal.googleSubjectDigest == nil && journal.accountGeneration == nil
    case .signOutWipe, .wipeLocalData:
      guard let operationId = journal.operationId, canonicalUUID(operationId),
        let saltValue = journal.accountBindingSalt,
        let salt = Data(base64Encoded: saltValue), salt.count == saltByteCount,
        journal.firebaseUIDDigest?.range(
          of: "^[0-9a-f]{64}$", options: .regularExpression
        ) != nil,
        journal.googleSubjectDigest?.range(
          of: "^[0-9a-f]{64}$", options: .regularExpression
        ) != nil,
        let generation = journal.accountGeneration, canonicalUUID(generation)
      else { return false }
      return true
    }
  }

  private static func matches(
    binding: IOSNativeGoogleAccountBinding,
    journal: IOSCompanionWipeRecoveryJournal
  ) -> Bool {
    guard validBinding(binding),
      journal.accountGeneration.map({ constantTimeEqual($0, binding.accountGeneration) })
        == true
    else { return false }
    return shared.matchesProviderIdentity(
      firebaseUID: binding.firebaseUID,
      googleSubject: binding.googleSubject,
      journal: journal
    )
  }

  private static func identityDigest(
    domain: Data,
    markerId: String,
    value: String,
    salt: Data
  ) -> String {
    var data = domain
    data.append(salt)
    appendLengthPrefixed(markerId, to: &data)
    appendLengthPrefixed(value, to: &data)
    return SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
  }

  private static func appendLengthPrefixed(_ value: String, to data: inout Data) {
    let bytes = Data(value.utf8)
    var length = UInt64(bytes.count).bigEndian
    withUnsafeBytes(of: &length) { data.append(contentsOf: $0) }
    data.append(bytes)
  }

  private static func randomSalt() -> Data? {
    var salt = Data(count: saltByteCount)
    let status = salt.withUnsafeMutableBytes { bytes in
      guard let address = bytes.baseAddress else { return errSecParam }
      return SecRandomCopyBytes(kSecRandomDefault, saltByteCount, address)
    }
    return status == errSecSuccess ? salt : nil
  }

  private static func validBinding(_ binding: IOSNativeGoogleAccountBinding) -> Bool {
    IOSPeopleValuePolicy.providerIdentifier(binding.firebaseUID, maximumBytes: 256)
      && IOSPeopleValuePolicy.googleSubject(binding.googleSubject) == binding.googleSubject
      && canonicalUUID(binding.accountGeneration)
  }

  private static func canonicalUUID(_ value: String) -> Bool {
    guard let uuid = UUID(uuidString: value) else { return false }
    return uuid.uuidString.lowercased() == value
  }

  private static func validCivilDate(_ value: String) -> Bool {
    guard value.range(
      of: "^[0-9]{4}-[0-9]{2}-[0-9]{2}$", options: .regularExpression
    ) != nil else { return false }
    let parts = value.split(separator: "-").compactMap { Int($0) }
    guard parts.count == 3 else { return false }
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(secondsFromGMT: 0)!
    guard let date = calendar.date(
      from: DateComponents(year: parts[0], month: parts[1], day: parts[2])
    ) else { return false }
    let roundTrip = calendar.dateComponents([.year, .month, .day], from: date)
    return roundTrip.year == parts[0] && roundTrip.month == parts[1]
      && roundTrip.day == parts[2]
  }

  private static func validTimestamp(_ value: Date) -> Bool {
    value.timeIntervalSince1970.isFinite && value.timeIntervalSince1970 >= 0
  }

  private static func constantTimeEqual(_ lhs: String, _ rhs: String) -> Bool {
    let left = Array(lhs.utf8)
    let right = Array(rhs.utf8)
    guard left.count == right.count else { return false }
    var difference: UInt8 = 0
    for index in left.indices { difference |= left[index] ^ right[index] }
    return difference == 0
  }
}

/// Idempotent cold-launch driver for the two reviewed multi-store wipes. The
/// journal remains authoritative across every callback and is cleared only
/// after notification cancellation, companion replacement verification, and
/// exact-account People/SDK cleanup all succeed.
@MainActor
final class IOSCompanionWipeRecoveryCoordinator {
  static let shared = IOSCompanionWipeRecoveryCoordinator()

  private var recoveryRunning = false

  private init() {}

  func resumeIfNeeded() {
    guard !recoveryRunning else { return }
    let recoveryStore = IOSCompanionWipeRecoveryStore.shared
    guard let journal = recoveryStore.current() else {
      // A present-but-unreadable marker deliberately remains fail-closed.
      return
    }
    recoveryRunning = true
    if journal.kind == .protectedStoreReset {
      CompanionProtectedStore.shared.observeResetSafetyDate { [weak self] _ in
        self?.recoveryRunning = false
      }
      return
    }
    Task { @MainActor [weak self] in
      guard let self else { return }
      await self.resume(journal)
      self.recoveryRunning = false
    }
  }

  private func resume(_ original: IOSCompanionWipeRecoveryJournal) async {
    let recoveryStore = IOSCompanionWipeRecoveryStore.shared
    guard var journal = recoveryStore.current(),
      journal.markerId == original.markerId
    else { return }
    if journal.companionResetInstalled,
      journal.notificationCleanupVerified,
      journal.reservationJournalDestroyed,
      journal.localCleanupComplete,
      let operationId = journal.operationId
    {
      _ = recoveryStore.clearCompletedSaga(operationId: operationId)
      return
    }

    switch journal.kind {
    case .protectedStoreReset:
      return
    case .signOutWipe:
      if !journal.localCleanupComplete {
        guard await IOSGoogleIdentityCoordinator.shared
          .resumeJournaledSignOutWipe(journal),
          let operationId = journal.operationId,
          recoveryStore.markLocalCleanupComplete(operationId: operationId)
        else { return }
      }
      if !journal.companionResetInstalled
        || !journal.notificationCleanupVerified
      {
        guard await wipeCompanionAndNotifications() else { return }
      }
    case .wipeLocalData:
      if !journal.companionResetInstalled
        || !journal.notificationCleanupVerified
      {
        guard await wipeCompanionAndNotifications() else { return }
      }
      guard let refreshed = recoveryStore.current() else { return }
      journal = refreshed
      if !journal.localCleanupComplete {
        guard await IOSGoogleIdentityCoordinator.shared
          .resumeJournaledLocalDataWipe(journal),
          let operationId = journal.operationId,
          recoveryStore.markLocalCleanupComplete(operationId: operationId)
        else { return }
      }
    }

    guard let operationId = journal.operationId,
      ensureReservationJournalDestroyed(operationId: operationId)
    else { return }
    _ = recoveryStore.clearCompletedSaga(operationId: operationId)
  }

  private func ensureReservationJournalDestroyed(operationId: String) -> Bool {
    let recoveryStore = IOSCompanionWipeRecoveryStore.shared
    guard let journal = recoveryStore.current(),
      journal.operationId == operationId
    else { return false }
    if journal.reservationJournalDestroyed { return true }
    guard IOSComposerReservationJournal.shared.destroyAll() else { return false }
    return recoveryStore.markReservationJournalDestroyed(
      operationId: operationId
    )
  }

  private func wipeCompanionAndNotifications() async -> Bool {
    let notificationsAbsent = await withCheckedContinuation { continuation in
      CompanionReminderCoordinator.shared.wipeCompanionData { result in
        continuation.resume(returning: result["kind"] as? String == "ok")
      }
    }
    guard notificationsAbsent,
      let journal = IOSCompanionWipeRecoveryStore.shared.current()
    else { return false }
    return IOSCompanionWipeRecoveryStore.shared.markNotificationCleanupVerified(
      markerId: journal.markerId
    )
  }
}
