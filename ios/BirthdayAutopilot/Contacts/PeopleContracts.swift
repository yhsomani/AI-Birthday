import Foundation

let birthdayContactsReadOnlyScope =
  "https://www.googleapis.com/auth/contacts.readonly"
let birthdayPeoplePersonFields = "names,birthdays,phoneNumbers,metadata"
let birthdayPeopleContactSource = "READ_SOURCE_TYPE_CONTACT"

/// Capacity supported by the current encrypted whole-snapshot store and the signed-device
/// performance gate. Raising any value requires page-staged storage plus new 10k-or-larger RSS,
/// latency, cancellation, and rollback evidence; it is not a cosmetic API-limit change.
enum IOSPeopleCapacityPolicy {
  static let maximumPeople = 10_000
  static let maximumPageBytes = 4 * 1_024 * 1_024
  static let maximumTotalResponseBytes = 16 * 1_024 * 1_024
  static let maximumEncryptedSnapshotBytes = 32 * 1_024 * 1_024
}

enum IOSContactsFreshnessSourceState {
  case verified
  case retainedAfterFailure
  case authorizationRequired
  case safetyPaused
  case unavailable
}

enum IOSContactsFreshnessBand: String {
  case normal = "NORMAL"
  case staleWarning = "STALE_WARNING"
  case safetyPaused = "SAFETY_PAUSED"
  case untrusted = "UNTRUSTED"
}

struct IOSContactsFreshnessAssessment {
  let band: IOSContactsFreshnessBand
  let lastSuccessAt: Date?

  var allowsCompanionAction: Bool {
    band == .normal || band == .staleWarning
  }
}

/// Shared 7/30-day Contacts policy. All age comparisons use a recent authenticated server-time
/// observation advanced only by a bounded, nonnegative local receipt interval. A clock rollback,
/// future timestamp, missing observation, or arithmetic anomaly fails closed.
enum IOSContactsFreshnessPolicy {
  static let normalMaximumAge: TimeInterval = 7 * 24 * 60 * 60
  static let companionMaximumAge: TimeInterval = 30 * 24 * 60 * 60

  static func assess(
    sourceState: IOSContactsFreshnessSourceState,
    lastSuccessAt: Date?,
    trustedNow: Date?
  ) -> IOSContactsFreshnessAssessment {
    if sourceState == .authorizationRequired || sourceState == .safetyPaused {
      return IOSContactsFreshnessAssessment(
        band: .safetyPaused,
        lastSuccessAt: lastSuccessAt
      )
    }
    guard sourceState == .verified || sourceState == .retainedAfterFailure,
      let lastSuccessAt, let trustedNow,
      valid(lastSuccessAt), valid(trustedNow)
    else {
      return IOSContactsFreshnessAssessment(
        band: .untrusted,
        lastSuccessAt: lastSuccessAt
      )
    }
    let age = trustedNow.timeIntervalSince(lastSuccessAt)
    guard age.isFinite, age >= 0 else {
      return IOSContactsFreshnessAssessment(
        band: .untrusted,
        lastSuccessAt: lastSuccessAt
      )
    }
    let band: IOSContactsFreshnessBand
    if age <= normalMaximumAge {
      band = .normal
    } else if age <= companionMaximumAge {
      band = .staleWarning
    } else {
      band = .safetyPaused
    }
    return IOSContactsFreshnessAssessment(band: band, lastSuccessAt: lastSuccessAt)
  }

  static func estimateTrustedNow(
    serverObservedAt: Date?,
    locallyReceivedAt: Date,
    now: Date,
    maximumObservationAge: TimeInterval
  ) -> Date? {
    guard let serverObservedAt,
      valid(serverObservedAt), valid(locallyReceivedAt), valid(now),
      maximumObservationAge.isFinite, maximumObservationAge >= 0
    else { return nil }
    let receiptAge = now.timeIntervalSince(locallyReceivedAt)
    guard receiptAge.isFinite, receiptAge >= 0, receiptAge <= maximumObservationAge else {
      return nil
    }
    let candidate = serverObservedAt.addingTimeInterval(receiptAge)
    return valid(candidate) ? candidate : nil
  }

  private static func valid(_ date: Date) -> Bool {
    let value = date.timeIntervalSince1970
    return value.isFinite && value >= 0
  }
}

enum IOSPeopleSyncMode: Equatable {
  case full
  case incremental(syncToken: String, parameterFingerprint: String)
}

enum IOSPeopleSyncFailure: Equatable {
  case authorizationRequired
  case cancelled
  case forbidden
  case malformed
  case networkOffline
  case partial
  case rateLimited(retryAfterSeconds: Int?)
  case repeatedUnauthorized
  case storage
  case unavailable
}

enum IOSPeopleSyncOutcome: Equatable {
  case completed(contactCount: Int, mode: IOSPeopleCompletedMode, recoveredExpiredToken: Bool)
  case failed(IOSPeopleSyncFailure)
}

enum IOSPeopleCompletedMode: String, Codable, Equatable {
  case full
  case incremental
}

enum IOSPeopleTransportResult {
  case success(Data)
  case expiredSyncToken
  case forbidden
  case networkOffline
  case rateLimited(Int?)
  case timedOut
  case unauthorized
  case unexpectedResponse
}

struct IOSPeopleName: Codable, Equatable {
  let displayName: String?
  let givenName: String?
}

struct IOSPeopleBirthday: Codable, Equatable, Hashable {
  let year: Int?
  let month: Int?
  let day: Int?
}

struct IOSPeoplePhone: Codable, Equatable {
  let value: String
  let type: String?
}

struct IOSPeopleContactDelta: Codable, Equatable {
  let resourceName: String
  let previousResourceNames: [String]
  let contactSourceId: String
  let deleted: Bool
  let names: [IOSPeopleName]
  let birthdays: [IOSPeopleBirthday]
  let phoneNumbers: [IOSPeoplePhone]
}

struct IOSPeoplePage: Equatable {
  let contacts: [IOSPeopleContactDelta]
  let nextPageToken: String?
  let nextSyncToken: String?
  let totalItems: Int?
  let encodedBytes: Int
}

enum IOSPeoplePageParseError: Error, Equatable {
  case duplicatePerson
  case invalidJSON
  case invalidPage
  case malformedPerson
  case partialSourceMerge
}

struct IOSPeopleSyncLimits: Equatable {
  let pageSize: Int
  let maximumPageBytes: Int
  let maximumTotalBytes: Int
  let maximumPages: Int
  let maximumPeople: Int
  let maximumDuration: TimeInterval

  init(
    pageSize: Int = 1_000,
    maximumPageBytes: Int = IOSPeopleCapacityPolicy.maximumPageBytes,
    maximumTotalBytes: Int = IOSPeopleCapacityPolicy.maximumTotalResponseBytes,
    maximumPages: Int = 100,
    maximumPeople: Int = IOSPeopleCapacityPolicy.maximumPeople,
    maximumDuration: TimeInterval = 120
  ) {
    precondition((1...1_000).contains(pageSize))
    precondition((1...IOSPeopleCapacityPolicy.maximumPageBytes).contains(maximumPageBytes))
    precondition(
      maximumTotalBytes >= maximumPageBytes
        && maximumTotalBytes <= IOSPeopleCapacityPolicy.maximumTotalResponseBytes
    )
    precondition((1...1_000).contains(maximumPages))
    precondition(
      maximumPeople >= pageSize && maximumPeople <= IOSPeopleCapacityPolicy.maximumPeople
    )
    precondition((1...600).contains(maximumDuration))
    self.pageSize = pageSize
    self.maximumPageBytes = maximumPageBytes
    self.maximumTotalBytes = maximumTotalBytes
    self.maximumPages = maximumPages
    self.maximumPeople = maximumPeople
    self.maximumDuration = maximumDuration
  }
}

/// A short-lived credential wrapper. Its value is never Codable, printable, or bridgeable.
/// Clearing releases the app-owned reference; Google Sign-In retains its own protected SDK cache.
final class IOSEphemeralGoogleAccessToken {
  private var token: String?

  init?(_ value: String?) {
    guard let value, !value.isEmpty, value.utf8.count <= 16_384 else { return nil }
    token = value
  }

  func use<Value>(_ body: (String) throws -> Value) rethrows -> Value {
    guard let token else { preconditionFailure("Ephemeral credential already cleared") }
    return try body(token)
  }

  func clear() {
    token = nil
  }

  deinit {
    token = nil
  }
}

enum IOSPeopleValuePolicy {
  private static let unsafeScalarValues: Set<UInt32> = [
    0x061C, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
    0x2066, 0x2067, 0x2068, 0x2069,
  ]

  static func providerIdentifier(_ value: String, maximumBytes: Int = 1_024) -> Bool {
    !value.isEmpty && value.utf8.count <= maximumBytes && value.unicodeScalars.allSatisfy {
      !$0.properties.isWhitespace && !isControl($0) &&
        !unsafeScalarValues.contains($0.value)
    }
  }

  static func privateText(_ value: String?, maximumCharacters: Int) -> String? {
    guard let value, !value.isEmpty, value.count <= maximumCharacters,
      value.utf8.count <= maximumCharacters * 4,
      value.unicodeScalars.allSatisfy({ scalar in
        !isControl(scalar) && !unsafeScalarValues.contains(scalar.value)
      })
    else {
      return nil
    }
    return value
  }

  static func safeDisplayName(_ value: String?) -> String? {
    guard let value = privateText(value, maximumCharacters: 256) else { return nil }
    let collapsed = value
      .split(whereSeparator: { $0.isWhitespace })
      .joined(separator: " ")
    return collapsed.isEmpty ? nil : collapsed
  }

  static func safeEmail(_ value: String?) -> String? {
    guard let candidate = value?.trimmingCharacters(in: .whitespacesAndNewlines),
      (3...254).contains(candidate.utf8.count),
      candidate.filter({ $0 == "@" }).count == 1,
      candidate.unicodeScalars.allSatisfy({ scalar in
        scalar.value >= 0x21 && scalar.value <= 0x7E &&
          !unsafeScalarValues.contains(scalar.value)
      })
    else {
      return nil
    }
    return candidate
  }

  static func googleSubject(_ value: String?) -> String? {
    guard let value,
      value.range(of: "^[A-Za-z0-9_-]{1,256}$", options: .regularExpression) != nil
    else {
      return nil
    }
    return value
  }

  static func token(_ value: String?) -> String? {
    guard let value, (1...8_192).contains(value.utf8.count),
      value.unicodeScalars.allSatisfy({
        !$0.properties.isWhitespace && !isControl($0)
      })
    else {
      return nil
    }
    return value
  }

  private static func isControl(_ scalar: Unicode.Scalar) -> Bool {
    scalar.value <= 0x1F || scalar.value == 0x7F
  }
}
