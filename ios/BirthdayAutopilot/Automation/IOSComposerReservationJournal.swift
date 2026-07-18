import CryptoKit
import Foundation
import Security

struct IOSComposerReservationCapability: Codable, Equatable {
  let schemaVersion: Int
  let reservationId: String
  let accountBindingSalt: String
  let firebaseUIDDigest: String
  let googleSubjectDigest: String
  let accountGeneration: String
  var phase: IOSComposerReservationLocalPhase
  var expiresAt: Date?
  let createdAt: Date
  var updatedAt: Date
}

private struct IOSComposerReservationJournalPayload: Codable, Equatable {
  let schemaVersion: Int
  var entries: [IOSComposerReservationCapability]
}

/// Device-only owner capabilities for the account-global iOS/Android exclusion
/// fence. Raw account identifiers, message material, contact identifiers,
/// dates, destinations, and bodies are never persisted here or sent through
/// React Native. Losing this journal never releases the server fence early.
final class IOSComposerReservationJournal {
  static let shared = IOSComposerReservationJournal()

  private static let schemaVersion = 1
  private static let maximumEntries = 8
  private static let maximumFileBytes = 16_384
  private static let saltByteCount = 32
  private static let uuidPattern = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
  private static let generationPattern = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
  private static let digestPattern = "^[0-9a-f]{64}$"
  private static let firebaseUIDDomain = Data(
    "birthday-ios-composer-reservation-firebase-uid-v1\0".utf8
  )
  private static let googleSubjectDomain = Data(
    "birthday-ios-composer-reservation-google-subject-v1\0".utf8
  )
  private static let allowedPayloadKeys: Set<String> = ["entries", "schemaVersion"]
  private static let allowedEntryKeys: Set<String> = [
    "accountBindingSalt", "accountGeneration", "createdAt", "expiresAt",
    "firebaseUIDDigest", "googleSubjectDigest", "phase", "reservationId",
    "schemaVersion", "updatedAt",
  ]
  private static let requiredEntryKeys = allowedEntryKeys.subtracting(["expiresAt"])

  private let queue = DispatchQueue(
    label: "com.yashsomani.birthdayautopilot.ios-composer-reservation-journal",
    qos: .utility
  )
  private let fileManager: FileManager
  private let storageRootURL: URL?

  private enum LoadResult {
    case missing
    case loaded(IOSComposerReservationJournalPayload)
    case confirmedCorrupt
    case unavailable
  }

  /// `storageRootURL` is an internal test seam. Production continues to use the
  /// protected Application Support directory selected by Foundation.
  init(
    fileManager: FileManager = .default,
    storageRootURL: URL? = nil
  ) {
    self.fileManager = fileManager
    self.storageRootURL = storageRootURL
  }

  func loadOrCreate(
    binding: IOSNativeGoogleAccountBinding,
    now: Date = Date()
  ) -> IOSComposerReservationCapability? {
    queue.sync {
      guard Self.validBinding(binding), Self.validTimestamp(now) else { return nil }
      var payload: IOSComposerReservationJournalPayload
      switch loadResult() {
      case .loaded(let existing):
        payload = existing
      case .missing:
        payload = IOSComposerReservationJournalPayload(
          schemaVersion: Self.schemaVersion,
          entries: []
        )
      case .confirmedCorrupt:
        // The server UUID remains authoritative. Replacing only positively
        // malformed local bytes cannot shorten a live server fence: a new UUID
        // is refused until that fence expires. Transient protected-data or I/O
        // failures take the separate unavailable path and are never deleted.
        guard destroyFileAndVerifyAbsence() else { return nil }
        payload = IOSComposerReservationJournalPayload(
          schemaVersion: Self.schemaVersion,
          entries: []
        )
      case .unavailable:
        return nil
      }
      if let existing = payload.entries.first(where: {
        Self.matches(binding: binding, capability: $0)
      }) {
        return existing
      }
      // Losing a local owner capability cannot release or shorten its server
      // fence. Pruning a recorded-expired entry is therefore safe even if the
      // wall clock is wrong; at worst the next acquire is refused by the still-
      // authoritative server record and remains fail-closed.
      payload.entries.removeAll { entry in
        IOSComposerReservationPruningPolicy.shouldPruneForDifferentBinding(
          phase: entry.phase,
          expiresAt: entry.expiresAt,
          observedAt: now
        )
      }
      guard payload.entries.count < Self.maximumEntries,
        let salt = Self.randomSalt()
      else { return nil }
      let reservationId = UUID().uuidString.lowercased()
      let capability = IOSComposerReservationCapability(
        schemaVersion: Self.schemaVersion,
        reservationId: reservationId,
        accountBindingSalt: salt.base64EncodedString(),
        firebaseUIDDigest: Self.identityDigest(
          domain: Self.firebaseUIDDomain,
          reservationId: reservationId,
          value: binding.firebaseUID,
          salt: salt
        ),
        googleSubjectDigest: Self.identityDigest(
          domain: Self.googleSubjectDomain,
          reservationId: reservationId,
          value: binding.googleSubject,
          salt: salt
        ),
        accountGeneration: binding.accountGeneration,
        phase: .prepared,
        expiresAt: nil,
        createdAt: now,
        updatedAt: now
      )
      payload.entries.append(capability)
      payload.entries.sort { $0.reservationId < $1.reservationId }
      return persist(payload) ? capability : nil
    }
  }

  func current(
    binding: IOSNativeGoogleAccountBinding
  ) -> IOSComposerReservationCapability? {
    queue.sync {
      load()?.entries.first(where: {
        Self.matches(binding: binding, capability: $0)
      })
    }
  }

  func recordAcquire(
    binding: IOSNativeGoogleAccountBinding,
    reservationId: String,
    expiresAt: Date,
    earlyReleaseAllowed: Bool,
    now: Date = Date()
  ) -> IOSComposerReservationCapability? {
    queue.sync {
      guard Self.validTimestamp(expiresAt), Self.validTimestamp(now),
        var payload = load(),
        let index = payload.entries.firstIndex(where: {
          Self.matches(binding: binding, capability: $0)
            && Self.constantTimeEqual($0.reservationId, reservationId)
        })
      else { return nil }
      let existing = payload.entries[index]
      payload.entries[index].phase =
        existing.phase == .sticky || !earlyReleaseAllowed ? .sticky : .prepared
      payload.entries[index].expiresAt = expiresAt
      payload.entries[index].updatedAt = now
      return persist(payload) ? payload.entries[index] : nil
    }
  }

  /// Must commit before the sticky transition callable is attempted. A timeout,
  /// cancellation, or process death can then never authorize local early release.
  func markStickyBeforeCommit(
    binding: IOSNativeGoogleAccountBinding,
    reservationId: String,
    now: Date = Date()
  ) -> Bool {
    queue.sync {
      guard Self.validTimestamp(now), var payload = load(),
        let index = payload.entries.firstIndex(where: {
          Self.matches(binding: binding, capability: $0)
            && Self.constantTimeEqual($0.reservationId, reservationId)
        }), payload.entries[index].expiresAt != nil
      else { return false }
      payload.entries[index].phase = .sticky
      payload.entries[index].updatedAt = now
      return persist(payload)
    }
  }

  /// Clears only a never-committed exact capability after the authenticated
  /// release callable returned an exact RELEASED result.
  func clearAfterExactPreparedRelease(
    binding: IOSNativeGoogleAccountBinding,
    reservationId: String
  ) -> Bool {
    queue.sync {
      guard var payload = load(),
        let index = payload.entries.firstIndex(where: {
          Self.matches(binding: binding, capability: $0)
            && Self.constantTimeEqual($0.reservationId, reservationId)
        }), payload.entries[index].phase == .prepared
      else { return false }
      payload.entries.remove(at: index)
      return persist(payload)
    }
  }

  /// Privacy lifecycle integration erases every account capability. This does
  /// not call the server or release a reservation; server logical expiry (or
  /// account deletion) remains the only authority that re-enables Android.
  func destroyAll() -> Bool {
    queue.sync { destroyFileAndVerifyAbsence() }
  }

  private func load() -> IOSComposerReservationJournalPayload? {
    guard case .loaded(let payload) = loadResult() else { return nil }
    return payload
  }

  private func loadResult() -> LoadResult {
    guard let url = try? fileURL() else { return .unavailable }
    guard fileManager.fileExists(atPath: url.path) else { return .missing }
    let data: Data
    do {
      data = try Data(contentsOf: url, options: [.mappedIfSafe])
    } catch {
      return .unavailable
    }
    guard !data.isEmpty, data.count <= Self.maximumFileBytes,
      let rawValue = try? JSONSerialization.jsonObject(with: data),
      let raw = rawValue as? [String: Any],
      Set(raw.keys) == Self.allowedPayloadKeys,
      let rawEntries = raw["entries"] as? [[String: Any]],
      rawEntries.allSatisfy({ rawEntry in
        let keys = Set(rawEntry.keys)
        return Self.requiredEntryKeys.isSubset(of: keys)
          && keys.isSubset(of: Self.allowedEntryKeys)
      })
    else { return .confirmedCorrupt }
    let decoder = JSONDecoder()
    decoder.dateDecodingStrategy = .millisecondsSince1970
    guard let payload = try? decoder.decode(
      IOSComposerReservationJournalPayload.self,
      from: data
    ), Self.validate(payload) else { return .confirmedCorrupt }
    return .loaded(payload)
  }

  private func destroyFileAndVerifyAbsence() -> Bool {
    guard let url = try? fileURL() else { return false }
    do {
      if fileManager.fileExists(atPath: url.path) {
        try fileManager.removeItem(at: url)
      }
      return !fileManager.fileExists(atPath: url.path)
    } catch {
      return false
    }
  }

  private func persist(_ payload: IOSComposerReservationJournalPayload) -> Bool {
    guard Self.validate(payload) else { return false }
    do {
      let encoder = JSONEncoder()
      encoder.dateEncodingStrategy = .millisecondsSince1970
      encoder.outputFormatting = [.sortedKeys]
      let data = try encoder.encode(payload)
      guard data.count <= Self.maximumFileBytes else { return false }
      let url = try fileURL()
      try data.write(to: url, options: [.atomic, .completeFileProtection])
      try fileManager.setAttributes(
        [.protectionKey: FileProtectionType.complete],
        ofItemAtPath: url.path
      )
      var values = URLResourceValues()
      values.isExcludedFromBackup = true
      var mutableURL = url
      try mutableURL.setResourceValues(values)
      // JSONEncoder's millisecondsSince1970 strategy can round a Date by one
      // floating-point ULP on decode. Exact synthesized Date equality therefore
      // reports some successful durable writes as failures. Verify the bytes we
      // atomically wrote and independently require a fully validated decode.
      let persistedData = try Data(contentsOf: url)
      guard persistedData == data else { return false }
      guard case .loaded = loadResult() else { return false }
      return true
    } catch {
      return false
    }
  }

  private func fileURL() throws -> URL {
    let base = try storageRootURL ?? fileManager.url(
      for: .applicationSupportDirectory,
      in: .userDomainMask,
      appropriateFor: nil,
      create: true
    )
    let directory = base.appendingPathComponent(
      "BirthdayAutopilotIOSComposerReservation",
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
    return directory.appendingPathComponent("ios-composer-reservation-v1.json")
  }

  private static func validate(
    _ payload: IOSComposerReservationJournalPayload
  ) -> Bool {
    guard payload.schemaVersion == schemaVersion,
      payload.entries.count <= maximumEntries,
      Set(payload.entries.map(\.reservationId)).count == payload.entries.count,
      payload.entries == payload.entries.sorted(by: {
        $0.reservationId < $1.reservationId
      })
    else { return false }
    return payload.entries.allSatisfy { entry in
      guard entry.schemaVersion == schemaVersion,
        matches(entry.reservationId, pattern: uuidPattern),
        matches(entry.accountGeneration, pattern: generationPattern),
        matches(entry.firebaseUIDDigest, pattern: digestPattern),
        matches(entry.googleSubjectDigest, pattern: digestPattern),
        let salt = Data(base64Encoded: entry.accountBindingSalt),
        salt.count == saltByteCount,
        validTimestamp(entry.createdAt), validTimestamp(entry.updatedAt),
        entry.updatedAt >= entry.createdAt,
        entry.expiresAt.map(validTimestamp) ?? true,
        entry.phase != .sticky || entry.expiresAt != nil
      else { return false }
      return true
    }
  }

  private static func matches(
    binding: IOSNativeGoogleAccountBinding,
    capability: IOSComposerReservationCapability
  ) -> Bool {
    guard validBinding(binding),
      constantTimeEqual(binding.accountGeneration, capability.accountGeneration),
      let salt = Data(base64Encoded: capability.accountBindingSalt),
      salt.count == saltByteCount
    else { return false }
    return constantTimeEqual(
      identityDigest(
        domain: firebaseUIDDomain,
        reservationId: capability.reservationId,
        value: binding.firebaseUID,
        salt: salt
      ),
      capability.firebaseUIDDigest
    ) && constantTimeEqual(
      identityDigest(
        domain: googleSubjectDomain,
        reservationId: capability.reservationId,
        value: binding.googleSubject,
        salt: salt
      ),
      capability.googleSubjectDigest
    )
  }

  private static func identityDigest(
    domain: Data,
    reservationId: String,
    value: String,
    salt: Data
  ) -> String {
    var input = Data()
    input.append(domain)
    input.append(Data(reservationId.utf8))
    input.append(0)
    input.append(salt)
    input.append(0)
    input.append(Data(value.utf8))
    return SHA256.hash(data: input).map { String(format: "%02x", $0) }.joined()
  }

  private static func randomSalt() -> Data? {
    var bytes = [UInt8](repeating: 0, count: saltByteCount)
    guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess
    else { return nil }
    return Data(bytes)
  }

  private static func validBinding(_ binding: IOSNativeGoogleAccountBinding) -> Bool {
    IOSPeopleValuePolicy.providerIdentifier(binding.firebaseUID, maximumBytes: 256)
      && IOSPeopleValuePolicy.googleSubject(binding.googleSubject) == binding.googleSubject
      && matches(binding.accountGeneration, pattern: generationPattern)
  }

  private static func validTimestamp(_ value: Date) -> Bool {
    value.timeIntervalSince1970.isFinite
      && value.timeIntervalSince1970 >= 0
      && value.timeIntervalSince1970 <= 253_402_300_799
  }

  private static func matches(_ value: String, pattern: String) -> Bool {
    value.range(of: pattern, options: .regularExpression) != nil
  }

  private static func constantTimeEqual(_ left: String, _ right: String) -> Bool {
    let leftBytes = Array(left.utf8)
    let rightBytes = Array(right.utf8)
    var difference = UInt8(truncatingIfNeeded: leftBytes.count ^ rightBytes.count)
    let count = max(leftBytes.count, rightBytes.count)
    for index in 0..<count {
      let lhs = index < leftBytes.count ? leftBytes[index] : 0
      let rhs = index < rightBytes.count ? rightBytes[index] : 0
      difference |= lhs ^ rhs
    }
    return difference == 0
  }
}
