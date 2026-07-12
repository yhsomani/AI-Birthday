import CryptoKit
import Foundation
import Security

struct IOSNativeGoogleAccountBinding: Codable, Equatable {
  let googleSubject: String
  let firebaseUID: String
  let displayEmail: String
  let displayName: String?
  let accountGeneration: String

  func hasSameOwner(as other: IOSNativeGoogleAccountBinding) -> Bool {
    googleSubject == other.googleSubject && firebaseUID == other.firebaseUID
  }
}

enum IOSPeopleAccountAttachResult {
  case attached
  case accountMismatch
  case storageFailure
}

enum IOSPeopleDurableAttachmentState: Equatable {
  case attached
  case notAttached
  case unavailable
}

enum IOSPeopleSafeSyncState: Equatable {
  case authorizationRequired
  case failedRetained(lastSuccess: Date?, reason: String)
  case fresh(completedAt: Date, contactCount: Int)
  case neverSynced
  case syncing(mode: IOSPeopleCompletedMode, retainedGeneration: Bool)
}

struct IOSPeopleSafeContact: Equatable {
  let localId: String
  let displayName: String
  let maskedPhone: String?
  let readinessKind: String
  let readinessReasons: [String]
  let phoneChoices: [IOSPeopleSafePhoneChoice]
  let birthdayChoices: [IOSPeopleSafeBirthdayChoice]
}

struct IOSPeopleSafePhoneChoice: Equatable {
  let localId: String
  let maskedDisplay: String
  let sourceLabel: String
  let selectable: Bool
  let issue: String?
}

struct IOSPeopleSafeBirthdayChoice: Equatable {
  let localId: String
  let displayLabel: String
  let hasYear: Bool
  let selectable: Bool
  let issue: String?
}

struct IOSPeopleSafeProjection: Equatable {
  let binding: IOSNativeGoogleAccountBinding?
  let sync: IOSPeopleSafeSyncState
  let contacts: [IOSPeopleSafeContact]
  let localStorageBytes: Int
  let storageResetDetected: Bool
}

/// Native-only contact material used to validate configuration reviews and to
/// build foreground MessageUI proposals. None of these values are returned by
/// the React Native bridge.
struct IOSPeoplePrivatePhone: Equatable {
  let localId: String
  let e164: String?
}

struct IOSPeoplePrivateBirthday: Equatable {
  let localId: String
  let year: Int?
  let month: Int
  let day: Int
}

struct IOSPeoplePrivateContact: Equatable {
  let localId: String
  let displayName: String
  let givenName: String?
  let deleted: Bool
  let materialRevision: UInt64
  let phones: [IOSPeoplePrivatePhone]
  let birthdays: [IOSPeoplePrivateBirthday]
}

private struct IOSStoredPhone: Codable, Equatable {
  let localId: String
  let rawValue: String
  let type: String?
}

private struct IOSStoredBirthday: Codable, Equatable {
  let localId: String
  let year: Int?
  let month: Int?
  let day: Int?
}

private struct IOSStoredPeopleContact: Codable, Equatable {
  let localId: String
  var resourceName: String
  var previousResourceNames: [String]
  var contactSourceId: String
  var deleted: Bool
  var displayName: String?
  var givenName: String?
  var phones: [IOSStoredPhone]
  var birthdays: [IOSStoredBirthday]
  var materialRevision: UInt64
  var updatedAt: Date
}

private struct IOSStoredPeopleSync: Codable, Equatable {
  var nextSyncToken: String?
  var parameterFingerprint: String?
  var lastSuccessAt: Date?
  var lastCompletedMode: IOSPeopleCompletedMode?
  var lastFailureReason: String?
}

private struct IOSCompanionPeopleSnapshot: Codable {
  static let currentSchemaVersion = 1

  let schemaVersion: Int
  var binding: IOSNativeGoogleAccountBinding?
  var contacts: [IOSStoredPeopleContact]
  var sync: IOSStoredPeopleSync

  static var empty: IOSCompanionPeopleSnapshot {
    IOSCompanionPeopleSnapshot(
      schemaVersion: currentSchemaVersion,
      binding: nil,
      contacts: [],
      sync: IOSStoredPeopleSync(
        nextSyncToken: nil,
        parameterFingerprint: nil,
        lastSuccessAt: nil,
        lastCompletedMode: nil,
        lastFailureReason: nil
      )
    )
  }
}

private enum IOSPeopleStoreError: Error {
  case keyMissing
  case storageUnavailable
}

/// Encrypted, backup-excluded native storage for the iOS account binding and People working set.
/// An active snapshot is replaced only after every page has parsed and the final sync token exists.
final class CompanionPeopleStore {
  static let shared = CompanionPeopleStore()

  private static let maximumFileBytes = 64 * 1_024 * 1_024
  private static let maximumContacts = 100_000
  private static let keychainService =
    "com.yashsomani.birthdayautopilot.people-store"
  private static let keychainAccount = "database-key-v1"
  private static let authenticatedContext = Data(
    "birthday-autopilot.people-store.v1".utf8
  )

  private let queue = DispatchQueue(
    label: "com.yashsomani.birthdayautopilot.people-store",
    qos: .userInitiated
  )
  private let cacheLock = NSLock()
  private var cachedProjection = IOSPeopleSafeProjection(
    binding: nil,
    sync: .neverSynced,
    contacts: [],
    localStorageBytes: 0,
    storageResetDetected: false
  )
  private var syncInProgress: IOSPeopleCompletedMode?
  private let fileManager: FileManager

  private init(fileManager: FileManager = .default) {
    self.fileManager = fileManager
  }

  func prepareAtLaunch(completion: @escaping (Bool) -> Void) {
    queue.async {
      let result: Bool
      do {
        let loaded = try self.loadOrCreate()
        self.refreshCache(snapshot: loaded.snapshot, storageResetDetected: loaded.didReset)
        result = true
      } catch {
        self.refreshCache(snapshot: .empty, storageResetDetected: true)
        result = false
      }
      DispatchQueue.main.async { completion(result) }
    }
  }

  func projection() -> IOSPeopleSafeProjection {
    cacheLock.lock()
    defer { cacheLock.unlock() }
    return cachedProjection
  }

  func currentBinding() -> IOSNativeGoogleAccountBinding? {
    projection().binding
  }

  /// Resolves an attach write that may have committed its atomic file before a
  /// later protection/backup attribute step failed. This read never repairs,
  /// replaces, or deletes storage, so an unavailable result remains ambiguous
  /// and can never authorize deletion of a newly created Firebase user.
  func durableAttachmentState(
    for binding: IOSNativeGoogleAccountBinding
  ) -> IOSPeopleDurableAttachmentState {
    queue.sync {
      do {
        guard let snapshot = try readExistingSnapshotWithoutRepair() else {
          return .notAttached
        }
        return snapshot.binding == binding ? .attached : .notAttached
      } catch {
        return .unavailable
      }
    }
  }

  func hasCompletedSyncGeneration() -> Bool {
    queue.sync {
      guard let snapshot = try? loadOrCreate().snapshot else { return false }
      return snapshot.sync.lastSuccessAt != nil
    }
  }

  func privateContacts() -> [IOSPeoplePrivateContact] {
    queue.sync {
      guard let snapshot = try? loadOrCreate().snapshot else { return [] }
      return snapshot.contacts.map(Self.privateContact)
    }
  }

  func privateContact(localId: String) -> IOSPeoplePrivateContact? {
    queue.sync {
      guard let snapshot = try? loadOrCreate().snapshot,
        let contact = snapshot.contacts.first(where: { $0.localId == localId })
      else { return nil }
      return Self.privateContact(contact)
    }
  }

  func syncMode(parameterFingerprint: String) -> IOSPeopleSyncMode {
    queue.sync {
      guard let loaded = try? loadOrCreate().snapshot,
        let token = loaded.sync.nextSyncToken,
        let fingerprint = loaded.sync.parameterFingerprint,
        fingerprint == parameterFingerprint,
        IOSPeopleValuePolicy.token(token) == token
      else {
        return .full
      }
      return .incremental(syncToken: token, parameterFingerprint: fingerprint)
    }
  }

  func attach(
    _ binding: IOSNativeGoogleAccountBinding,
    retainedCompanionSetupExists: Bool,
    completion: @escaping (IOSPeopleAccountAttachResult) -> Void
  ) {
    queue.async {
      do {
        var snapshot = try self.loadOrCreate().snapshot
        if let existing = snapshot.binding {
          guard existing.hasSameOwner(as: binding) else {
            DispatchQueue.main.async { completion(.accountMismatch) }
            return
          }
          snapshot.binding = IOSNativeGoogleAccountBinding(
            googleSubject: existing.googleSubject,
            firebaseUID: existing.firebaseUID,
            displayEmail: binding.displayEmail,
            displayName: binding.displayName,
            accountGeneration: existing.accountGeneration
          )
        } else {
          guard !retainedCompanionSetupExists, snapshot.contacts.isEmpty else {
            DispatchQueue.main.async { completion(.accountMismatch) }
            return
          }
          snapshot.binding = binding
        }
        try self.persist(snapshot)
        self.refreshCache(snapshot: snapshot, storageResetDetected: false)
        DispatchQueue.main.async { completion(.attached) }
      } catch {
        DispatchQueue.main.async { completion(.storageFailure) }
      }
    }
  }

  func beginSync(mode: IOSPeopleCompletedMode) {
    cacheLock.lock()
    syncInProgress = mode
    cachedProjection = IOSPeopleSafeProjection(
      binding: cachedProjection.binding,
      sync: .syncing(
        mode: mode,
        retainedGeneration: !cachedProjection.contacts.isEmpty
      ),
      contacts: cachedProjection.contacts,
      localStorageBytes: cachedProjection.localStorageBytes,
      storageResetDetected: cachedProjection.storageResetDetected
    )
    cacheLock.unlock()
  }

  func commit(
    expectedBinding: IOSNativeGoogleAccountBinding,
    mode: IOSPeopleCompletedMode,
    deltas: [IOSPeopleContactDelta],
    nextSyncToken: String,
    parameterFingerprint: String,
    completedAt: Date,
    completion: @escaping (Bool) -> Void
  ) {
    queue.async {
      do {
        guard IOSPeopleValuePolicy.token(nextSyncToken) == nextSyncToken else {
          throw IOSPeopleStoreError.storageUnavailable
        }
        var snapshot = try self.loadOrCreate().snapshot
        guard let activeBinding = snapshot.binding,
          activeBinding.hasSameOwner(as: expectedBinding),
          Set(deltas.map(\.resourceName)).count == deltas.count,
          Set(deltas.map(\.contactSourceId)).count == deltas.count
        else {
          throw IOSPeopleStoreError.storageUnavailable
        }
        var contactsById: [String: IOSStoredPeopleContact] = mode == .full
          ? [:]
          : Dictionary(uniqueKeysWithValues: snapshot.contacts.map { ($0.localId, $0) })
        let existing = Dictionary(uniqueKeysWithValues: snapshot.contacts.map { ($0.localId, $0) })
        var resourceIndex: [String: String] = [:]
        var sourceIndex: [String: String] = [:]
        for contact in snapshot.contacts {
          resourceIndex[contact.resourceName] = contact.localId
          contact.previousResourceNames.forEach { resourceIndex[$0] = contact.localId }
          sourceIndex[contact.contactSourceId] = contact.localId
        }

        for delta in deltas {
          let localId = sourceIndex[delta.contactSourceId]
            ?? resourceIndex[delta.resourceName]
            ?? delta.previousResourceNames.compactMap { resourceIndex[$0] }.first
            ?? UUID().uuidString.lowercased()
          if delta.deleted {
            guard var retained = existing[localId] ?? contactsById[localId] else {
              // A tombstone for a contact never observed on this installation carries no
              // user value and is intentionally not retained.
              continue
            }
            retained.deleted = true
            retained.displayName = nil
            retained.givenName = nil
            retained.phones = []
            retained.birthdays = []
            retained.resourceName = delta.resourceName
            retained.previousResourceNames = delta.previousResourceNames
            retained.contactSourceId = delta.contactSourceId
            retained.materialRevision = try Self.increment(retained.materialRevision)
            retained.updatedAt = completedAt
            contactsById[localId] = retained
            continue
          }

          let previous = existing[localId] ?? contactsById[localId]
          let phones = Self.mergePhones(delta.phoneNumbers, previous: previous?.phones ?? [])
          let birthdays = Self.mergeBirthdays(delta.birthdays, previous: previous?.birthdays ?? [])
          let displayName = delta.names.compactMap(\.displayName).first
          let givenName = delta.names.compactMap(\.givenName).first
          let materialChanged = previous.map {
            $0.displayName != displayName || $0.givenName != givenName ||
              $0.phones != phones || $0.birthdays != birthdays || $0.deleted ||
              $0.contactSourceId != delta.contactSourceId
          } ?? true
          contactsById[localId] = IOSStoredPeopleContact(
            localId: localId,
            resourceName: delta.resourceName,
            previousResourceNames: delta.previousResourceNames,
            contactSourceId: delta.contactSourceId,
            deleted: false,
            displayName: displayName,
            givenName: givenName,
            phones: phones,
            birthdays: birthdays,
            materialRevision: materialChanged
              ? try Self.increment(previous?.materialRevision ?? 0)
              : previous?.materialRevision ?? 1,
            updatedAt: completedAt
          )
          resourceIndex[delta.resourceName] = localId
          delta.previousResourceNames.forEach { resourceIndex[$0] = localId }
          sourceIndex[delta.contactSourceId] = localId
        }

        guard contactsById.count <= Self.maximumContacts else {
          throw IOSPeopleStoreError.storageUnavailable
        }
        snapshot.contacts = contactsById.values.sorted { $0.localId < $1.localId }
        snapshot.sync.nextSyncToken = nextSyncToken
        snapshot.sync.parameterFingerprint = parameterFingerprint
        snapshot.sync.lastSuccessAt = completedAt
        snapshot.sync.lastCompletedMode = mode
        snapshot.sync.lastFailureReason = nil
        try self.persist(snapshot)
        self.cacheLock.lock()
        self.syncInProgress = nil
        self.cacheLock.unlock()
        self.refreshCache(snapshot: snapshot, storageResetDetected: false)
        DispatchQueue.main.async { completion(true) }
      } catch {
        self.cacheLock.lock()
        self.syncInProgress = nil
        self.cacheLock.unlock()
        DispatchQueue.main.async { completion(false) }
      }
    }
  }

  func recordSyncFailure(
    _ reason: String,
    authorizationRequired: Bool = false,
    completion: ((Bool) -> Void)? = nil
  ) {
    queue.async {
      guard var snapshot = try? self.loadOrCreate().snapshot else {
        DispatchQueue.main.async { completion?(false) }
        return
      }
      snapshot.sync.lastFailureReason = authorizationRequired
        ? "contacts-authorization-required" : reason
      do {
        try self.persist(snapshot)
      } catch {
        DispatchQueue.main.async { completion?(false) }
        return
      }
      self.cacheLock.lock()
      self.syncInProgress = nil
      self.cacheLock.unlock()
      self.refreshCache(snapshot: snapshot, storageResetDetected: false)
      DispatchQueue.main.async { completion?(true) }
    }
  }

  func signOutRetainingData() {
    // The encrypted binding and working set remain. Presentation is blocked by the
    // identity coordinator until the exact subject and Firebase UID reauthenticate.
    cacheLock.lock()
    syncInProgress = nil
    cacheLock.unlock()
  }

  /// Disconnects the People working set while retaining the proven account
  /// binding. This is intentionally separate from a full privacy wipe so an
  /// active Firebase/Google session cannot become silently rebound to another
  /// account generation.
  func clearContactsRetainingBinding(completion: @escaping (Bool) -> Void) {
    queue.async {
      do {
        var snapshot = try self.loadOrCreate().snapshot
        guard snapshot.binding != nil else {
          DispatchQueue.main.async { completion(false) }
          return
        }
        snapshot.contacts = []
        snapshot.sync = IOSStoredPeopleSync(
          nextSyncToken: nil,
          parameterFingerprint: nil,
          lastSuccessAt: nil,
          lastCompletedMode: nil,
          lastFailureReason: nil
        )
        try self.persist(snapshot)
        self.refreshCache(snapshot: snapshot, storageResetDetected: false)
        DispatchQueue.main.async { completion(true) }
      } catch {
        DispatchQueue.main.async { completion(false) }
      }
    }
  }

  func wipe(completion: @escaping (Bool) -> Void) {
    queue.async {
      do {
        let url = try self.storeFileURL()
        if self.fileManager.fileExists(atPath: url.path) {
          try self.fileManager.removeItem(at: url)
        }
        try self.deleteKey(allowMissing: true)
        let snapshot = IOSCompanionPeopleSnapshot.empty
        try self.persist(snapshot)
        self.refreshCache(snapshot: snapshot, storageResetDetected: true)
        DispatchQueue.main.async { completion(true) }
      } catch {
        DispatchQueue.main.async { completion(false) }
      }
    }
  }

  /// Destructive account-deletion teardown. It intentionally leaves neither an
  /// empty encrypted file nor a replacement Keychain key behind.
  func destroyAfterRemoteAccountDeletion(completion: @escaping (Bool) -> Void) {
    queue.async {
      do {
        let url = try self.storeFileURL()
        if self.fileManager.fileExists(atPath: url.path) {
          try self.fileManager.removeItem(at: url)
        }
        try self.deleteKey(allowMissing: true)
        guard !self.fileManager.fileExists(atPath: url.path) else {
          throw IOSPeopleStoreError.storageUnavailable
        }
        do {
          _ = try self.readKey()
          throw IOSPeopleStoreError.storageUnavailable
        } catch IOSPeopleStoreError.keyMissing {
          self.refreshCache(
            snapshot: .empty,
            storageResetDetected: true
          )
          DispatchQueue.main.async { completion(true) }
        }
      } catch {
        DispatchQueue.main.async { completion(false) }
      }
    }
  }

  private func refreshCache(
    snapshot: IOSCompanionPeopleSnapshot,
    storageResetDetected: Bool
  ) {
    let contacts = snapshot.contacts.map(Self.safeContact).sorted {
      $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending
    }
    let sync: IOSPeopleSafeSyncState
    cacheLock.lock()
    if let mode = syncInProgress {
      sync = .syncing(mode: mode, retainedGeneration: !contacts.isEmpty)
    } else if snapshot.sync.lastFailureReason == "contacts-authorization-required" {
      sync = .authorizationRequired
    } else if let reason = snapshot.sync.lastFailureReason {
      sync = .failedRetained(lastSuccess: snapshot.sync.lastSuccessAt, reason: reason)
    } else if let lastSuccess = snapshot.sync.lastSuccessAt {
      sync = .fresh(completedAt: lastSuccess, contactCount: contacts.filter { contact in
        contact.readinessKind != "unavailable"
      }.count)
    } else {
      sync = .neverSynced
    }
    var fileSize = 0
    if let url = try? storeFileURL(),
      let attributes = try? fileManager.attributesOfItem(atPath: url.path),
      let number = attributes[.size] as? NSNumber
    {
      fileSize = number.intValue
    }
    cachedProjection = IOSPeopleSafeProjection(
      binding: snapshot.binding,
      sync: sync,
      contacts: contacts,
      localStorageBytes: fileSize,
      storageResetDetected: storageResetDetected
    )
    cacheLock.unlock()
  }

  private static func safeContact(_ stored: IOSStoredPeopleContact) -> IOSPeopleSafeContact {
    let displayName = IOSPeopleValuePolicy.safeDisplayName(stored.displayName)
      ?? "Unnamed contact"
    let birthdayValues = Array(Set(stored.birthdays.compactMap { birthday -> IOSPeopleBirthday? in
      guard let month = birthday.month, let day = birthday.day else { return nil }
      return IOSPeopleBirthday(year: birthday.year, month: month, day: day)
    })).sorted {
      if $0.month != $1.month { return $0.month < $1.month }
      if $0.day != $1.day { return $0.day < $1.day }
      return ($0.year ?? 0) < ($1.year ?? 0)
    }
    let phoneChoices = stored.phones.compactMap { phone -> IOSPeopleSafePhoneChoice? in
      guard let analysis = analyzePhone(phone.rawValue) else { return nil }
      return IOSPeopleSafePhoneChoice(
        localId: phone.localId,
        maskedDisplay: analysis.masked,
        sourceLabel: safePhoneType(phone.type),
        selectable: analysis.issue == nil,
        issue: analysis.issue
      )
    }
    let birthdayChoices = birthdayValues.enumerated().map { index, birthday in
      IOSPeopleSafeBirthdayChoice(
        localId: stored.birthdays.first(where: {
          $0.year == birthday.year && $0.month == birthday.month && $0.day == birthday.day
        })?.localId ?? UUID().uuidString.lowercased(),
        // Exact birthdays remain native-only at this boundary.
        displayLabel: "Birthday option \(index + 1)",
        hasYear: birthday.year != nil,
        // A contact can legitimately contain multiple birthdays. Every valid
        // choice must remain selectable; readiness is resolved only after the
        // user explicitly chooses one in protected configuration state.
        selectable: true,
        issue: nil
      )
    }
    var reasons: [String] = []
    if stored.deleted { reasons.append("source-contact-deleted") }
    if birthdayValues.isEmpty { reasons.append("birthday-missing") }
    if birthdayValues.count > 1 { reasons.append("birthday-choice-required") }
    if stored.phones.isEmpty { reasons.append("phone-missing") }
    if !stored.phones.isEmpty && phoneChoices.isEmpty { reasons.append("phone-invalid") }
    if phoneChoices.count > 1 { reasons.append("phone-choice-required") }
    if phoneChoices.count == 1, let issue = phoneChoices[0].issue { reasons.append(issue) }
    if safeTemplateGivenName(stored.givenName) == nil {
      reasons.append("safe-given-name-missing")
    }
    reasons = Array(Set(reasons)).sorted()
    let readinessKind = stored.deleted ? "unavailable" : (reasons.isEmpty ? "ready" : "needs-attention")
    return IOSPeopleSafeContact(
      localId: stored.localId,
      displayName: displayName,
      maskedPhone: phoneChoices.count == 1 ? phoneChoices[0].maskedDisplay : nil,
      readinessKind: readinessKind,
      readinessReasons: reasons,
      phoneChoices: phoneChoices,
      birthdayChoices: birthdayChoices
    )
  }

  private static func analyzePhone(_ raw: String) -> (masked: String, issue: String?)? {
    let digits = raw.unicodeScalars
      .filter { (0x30...0x39).contains($0.value) }
      .map(String.init)
      .joined()
    guard !digits.isEmpty else { return nil }
    let visible = String(digits.suffix(min(2, digits.count)))
    let masked = visible.isEmpty ? "••••" : "•••• \(visible)"
    guard raw.unicodeScalars.allSatisfy({ scalar in
      CharacterSet(charactersIn: "+0123456789 ()-.\u{00A0}").contains(scalar)
    }), (7...15).contains(digits.count)
    else {
      return (masked, "phone-invalid")
    }
    let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    guard trimmed.hasPrefix("+") else {
      return (masked, "phone-ambiguous-region")
    }
    guard !trimmed.dropFirst().contains("+") else {
      return (masked, "phone-invalid")
    }
    return (masked, nil)
  }

  private static func normalizedE164(_ raw: String) -> String? {
    guard analyzePhone(raw)?.issue == nil else { return nil }
    let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    guard trimmed.first == "+", !trimmed.dropFirst().contains("+") else { return nil }
    let digits = raw.unicodeScalars
      .filter { (0x30...0x39).contains($0.value) }
      .map(String.init)
      .joined()
    guard (7...15).contains(digits.count), digits.first != "0" else { return nil }
    return "+\(digits)"
  }

  private static func privateContact(
    _ stored: IOSStoredPeopleContact
  ) -> IOSPeoplePrivateContact {
    var seenBirthdays = Set<String>()
    let birthdays = stored.birthdays.compactMap { birthday -> IOSPeoplePrivateBirthday? in
      guard let month = birthday.month, let day = birthday.day else { return nil }
      let key = "\(birthday.year.map(String.init) ?? "none")|\(month)|\(day)"
      guard seenBirthdays.insert(key).inserted else { return nil }
      return IOSPeoplePrivateBirthday(
        localId: birthday.localId,
        year: birthday.year,
        month: month,
        day: day
      )
    }
    return IOSPeoplePrivateContact(
      localId: stored.localId,
      displayName: IOSPeopleValuePolicy.safeDisplayName(stored.displayName)
        ?? "Unnamed contact",
      givenName: safeTemplateGivenName(stored.givenName),
      deleted: stored.deleted,
      materialRevision: stored.materialRevision,
      phones: stored.phones.map {
        IOSPeoplePrivatePhone(localId: $0.localId, e164: normalizedE164($0.rawValue))
      },
      birthdays: birthdays
    )
  }

  private static func safePhoneType(_ type: String?) -> String {
    switch type?.lowercased() {
    case "mobile": return "Mobile"
    case "home": return "Home"
    case "work": return "Work"
    default: return "Phone"
    }
  }

  private static func safeTemplateGivenName(_ raw: String?) -> String? {
    guard let value = IOSPeopleValuePolicy.safeDisplayName(raw), value.count <= 80,
      !value.contains("{"), !value.contains("}"),
      value.range(
        of: "(?:https?://|www\\.)\\S+",
        options: [.regularExpression, .caseInsensitive]
      ) == nil
    else { return nil }
    return value
  }

  private static func mergePhones(
    _ values: [IOSPeoplePhone],
    previous: [IOSStoredPhone]
  ) -> [IOSStoredPhone] {
    values.map { value in
      let existing = previous.first {
        $0.rawValue == value.value && $0.type == value.type
      }
      return IOSStoredPhone(
        localId: existing?.localId ?? UUID().uuidString.lowercased(),
        rawValue: value.value,
        type: value.type
      )
    }
  }

  private static func mergeBirthdays(
    _ values: [IOSPeopleBirthday],
    previous: [IOSStoredBirthday]
  ) -> [IOSStoredBirthday] {
    values.map { value in
      let existing = previous.first {
        $0.year == value.year && $0.month == value.month && $0.day == value.day
      }
      return IOSStoredBirthday(
        localId: existing?.localId ?? UUID().uuidString.lowercased(),
        year: value.year,
        month: value.month,
        day: value.day
      )
    }
  }

  private static func increment(_ value: UInt64) throws -> UInt64 {
    guard value < UInt64.max else { throw IOSPeopleStoreError.storageUnavailable }
    return value + 1
  }

  private func loadOrCreate() throws -> (snapshot: IOSCompanionPeopleSnapshot, didReset: Bool) {
    do {
      guard let existing = try readExistingSnapshotWithoutRepair() else {
        let empty = IOSCompanionPeopleSnapshot.empty
        try persist(empty)
        return (empty, false)
      }
      return (existing, false)
    } catch {
      let url = try storeFileURL()
      // A lost key or corrupt snapshot can never be attached to an unproven
      // account. Ordinary load is allowed to repair; ambiguous attach
      // resolution above deliberately uses the non-repairing reader.
      try? fileManager.removeItem(at: url)
      try? deleteKey(allowMissing: true)
      let empty = IOSCompanionPeopleSnapshot.empty
      try persist(empty)
      return (empty, true)
    }
  }

  private func readExistingSnapshotWithoutRepair() throws
    -> IOSCompanionPeopleSnapshot?
  {
    let url = try storeFileURL()
    guard fileManager.fileExists(atPath: url.path) else { return nil }
    let attributes = try fileManager.attributesOfItem(atPath: url.path)
    guard (attributes[.size] as? NSNumber)?.intValue ?? 0 <= Self.maximumFileBytes else {
      throw IOSPeopleStoreError.storageUnavailable
    }
    var sealed = try Data(contentsOf: url, options: [.mappedIfSafe])
    defer { sealed.resetBytes(in: 0..<sealed.count) }
    let key = try readKey()
    let box = try AES.GCM.SealedBox(combined: sealed)
    var plaintext = try AES.GCM.open(
      box,
      using: key,
      authenticating: Self.authenticatedContext
    )
    defer { plaintext.resetBytes(in: 0..<plaintext.count) }
    let decoder = JSONDecoder()
    decoder.dateDecodingStrategy = .millisecondsSince1970
    let snapshot = try decoder.decode(IOSCompanionPeopleSnapshot.self, from: plaintext)
    guard snapshot.schemaVersion == IOSCompanionPeopleSnapshot.currentSchemaVersion,
      snapshot.contacts.count <= Self.maximumContacts,
      Set(snapshot.contacts.map(\.localId)).count == snapshot.contacts.count,
      Set(snapshot.contacts.map(\.contactSourceId)).count == snapshot.contacts.count,
      snapshot.contacts.allSatisfy(Self.validateStoredContact),
      snapshot.binding.map(Self.validateBinding) ?? true
    else {
      throw IOSPeopleStoreError.storageUnavailable
    }
    return snapshot
  }

  private func persist(_ snapshot: IOSCompanionPeopleSnapshot) throws {
    let encoder = JSONEncoder()
    encoder.dateEncodingStrategy = .millisecondsSince1970
    encoder.outputFormatting = [.sortedKeys]
    var plaintext = try encoder.encode(snapshot)
    defer { plaintext.resetBytes(in: 0..<plaintext.count) }
    let key: SymmetricKey
    do {
      key = try readKey()
    } catch IOSPeopleStoreError.keyMissing {
      key = try createKey()
    }
    let box = try AES.GCM.seal(
      plaintext,
      using: key,
      authenticating: Self.authenticatedContext
    )
    guard var combined = box.combined, combined.count <= Self.maximumFileBytes else {
      throw IOSPeopleStoreError.storageUnavailable
    }
    defer { combined.resetBytes(in: 0..<combined.count) }
    let url = try storeFileURL()
    try combined.write(to: url, options: [.atomic, .completeFileProtection])
    try fileManager.setAttributes(
      [.protectionKey: FileProtectionType.complete],
      ofItemAtPath: url.path
    )
    var values = URLResourceValues()
    values.isExcludedFromBackup = true
    var mutableURL = url
    try mutableURL.setResourceValues(values)
  }

  private func storeFileURL() throws -> URL {
    let base = try fileManager.url(
      for: .applicationSupportDirectory,
      in: .userDomainMask,
      appropriateFor: nil,
      create: true
    )
    let directory = base.appendingPathComponent("BirthdayAutopilotPeople", isDirectory: true)
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
    return directory.appendingPathComponent("people-state.bin")
  }

  private func readKey() throws -> SymmetricKey {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: Self.keychainService,
      kSecAttrAccount as String: Self.keychainAccount,
      kSecAttrSynchronizable as String: kCFBooleanFalse as Any,
      kSecReturnData as String: kCFBooleanTrue as Any,
      kSecMatchLimit as String: kSecMatchLimitOne,
      kSecUseDataProtectionKeychain as String: kCFBooleanTrue as Any,
    ]
    var result: CFTypeRef?
    let status = SecItemCopyMatching(query as CFDictionary, &result)
    if status == errSecItemNotFound { throw IOSPeopleStoreError.keyMissing }
    guard status == errSecSuccess, var data = result as? Data, data.count == 32 else {
      throw IOSPeopleStoreError.storageUnavailable
    }
    defer { data.resetBytes(in: 0..<data.count) }
    return SymmetricKey(data: data)
  }

  private func createKey() throws -> SymmetricKey {
    var data = Data(count: 32)
    let status = data.withUnsafeMutableBytes { bytes in
      SecRandomCopyBytes(kSecRandomDefault, 32, bytes.baseAddress!)
    }
    guard status == errSecSuccess else { throw IOSPeopleStoreError.storageUnavailable }
    defer { data.resetBytes(in: 0..<data.count) }
    let add: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: Self.keychainService,
      kSecAttrAccount as String: Self.keychainAccount,
      kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
      kSecAttrSynchronizable as String: kCFBooleanFalse as Any,
      kSecValueData as String: data,
      kSecUseDataProtectionKeychain as String: kCFBooleanTrue as Any,
    ]
    let addStatus = SecItemAdd(add as CFDictionary, nil)
    if addStatus == errSecDuplicateItem { return try readKey() }
    guard addStatus == errSecSuccess else { throw IOSPeopleStoreError.storageUnavailable }
    return SymmetricKey(data: data)
  }

  private func deleteKey(allowMissing: Bool) throws {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: Self.keychainService,
      kSecAttrAccount as String: Self.keychainAccount,
      kSecAttrSynchronizable as String: kSecAttrSynchronizableAny,
      kSecUseDataProtectionKeychain as String: kCFBooleanTrue as Any,
    ]
    let status = SecItemDelete(query as CFDictionary)
    guard status == errSecSuccess || (allowMissing && status == errSecItemNotFound) else {
      throw IOSPeopleStoreError.storageUnavailable
    }
  }

  private static func validateBinding(_ binding: IOSNativeGoogleAccountBinding) -> Bool {
    IOSPeopleValuePolicy.googleSubject(binding.googleSubject) == binding.googleSubject &&
      IOSPeopleValuePolicy.safeEmail(binding.displayEmail) == binding.displayEmail &&
      IOSPeopleValuePolicy.providerIdentifier(binding.firebaseUID, maximumBytes: 256) &&
      binding.accountGeneration.range(
        of: "^[a-f0-9-]{36}$",
        options: .regularExpression
      ) != nil
  }

  private static func validateStoredContact(_ contact: IOSStoredPeopleContact) -> Bool {
    contact.localId.range(of: "^[a-f0-9-]{36}$", options: .regularExpression) != nil &&
      IOSPeopleValuePolicy.providerIdentifier(contact.resourceName, maximumBytes: 300) &&
      IOSPeopleValuePolicy.providerIdentifier(contact.contactSourceId, maximumBytes: 300) &&
      contact.phones.count <= 100 && contact.birthdays.count <= 16 &&
      Set(contact.phones.map(\.localId)).count == contact.phones.count &&
      Set(contact.birthdays.map(\.localId)).count == contact.birthdays.count &&
      contact.phones.allSatisfy {
        $0.localId.range(of: "^[a-f0-9-]{36}$", options: .regularExpression) != nil
          && !$0.rawValue.isEmpty && $0.rawValue.utf8.count <= 512
      } && contact.birthdays.allSatisfy { birthday in
        guard birthday.localId.range(
          of: "^[a-f0-9-]{36}$",
          options: .regularExpression
        ) != nil, let month = birthday.month, let day = birthday.day,
          (1...12).contains(month), (1...31).contains(day)
        else { return false }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let year = birthday.year ?? 2_000
        return calendar.date(from: DateComponents(
          year: year, month: month, day: day
        )) != nil
      }
  }
}
