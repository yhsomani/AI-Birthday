import Foundation

let birthdayContactsReadOnlyScope =
  "https://www.googleapis.com/auth/contacts.readonly"
let birthdayPeoplePersonFields = "names,birthdays,phoneNumbers,metadata"
let birthdayPeopleContactSource = "READ_SOURCE_TYPE_CONTACT"

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
    maximumPageBytes: Int = 8 * 1_024 * 1_024,
    maximumTotalBytes: Int = 64 * 1_024 * 1_024,
    maximumPages: Int = 100,
    maximumPeople: Int = 100_000,
    maximumDuration: TimeInterval = 120
  ) {
    precondition((1...1_000).contains(pageSize))
    precondition((1...16 * 1_024 * 1_024).contains(maximumPageBytes))
    precondition(maximumTotalBytes >= maximumPageBytes)
    precondition((1...1_000).contains(maximumPages))
    precondition(maximumPeople >= pageSize)
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
