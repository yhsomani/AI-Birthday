import CryptoKit
import Foundation

struct IOSCompanionProposalCoordinates: Equatable {
  let dayOffset: Int
  let contactOrdinal: Int
}

/// Domain-separated, deterministic opaque identities for companion planning.
///
/// Raw contact identifiers are accepted only as transient inputs to HMAC. They
/// are never embedded in an occurrence ID, proposal handle, or contact-table
/// digest. A namespace is device-local protected material and must rotate only
/// with the protected account generation or a reset-safety wipe.
enum IOSCompanionOccurrenceIdentity {
  static let namespaceByteCount = 32
  static let digestByteCount = 32
  static let maximumOpaqueInputBytes = 128
  static let maximumContacts = 10_000
  static let planningDayCount = 400

  private static let occurrencePrefix = "occ_"
  private static let proposalPrefix = "prp_"

  static func makeNamespace() -> Data {
    var generator = SystemRandomNumberGenerator()
    return Data((0..<namespaceByteCount).map { _ in
      UInt8.random(in: UInt8.min...UInt8.max, using: &generator)
    })
  }

  /// Binds the canonical ordered workflow contact table without persisting its
  /// raw identifiers in the planning index.
  static func contactTableDigest(
    namespace: Data,
    canonicalContactIdentifiers: [String]
  ) -> Data? {
    guard validNamespace(namespace),
      canonicalContactIdentifiers.count <= maximumContacts
    else { return nil }

    var previous: String?
    var values: [Data] = []
    values.reserveCapacity(canonicalContactIdentifiers.count)
    for identifier in canonicalContactIdentifiers {
      guard validOpaqueInput(identifier),
        previous.map({ $0 < identifier }) ?? true
      else { return nil }
      previous = identifier
      values.append(Data(identifier.utf8))
    }
    return authenticationCode(
      namespace: namespace,
      domain: "birthday-autopilot.ios.contact-table.v1",
      components: values
    )
  }

  static func occurrenceDigest(
    namespace: Data,
    accountGeneration: String,
    contactIdentifier: String,
    civilDate: String
  ) -> Data? {
    guard validNamespace(namespace),
      validOpaqueInput(accountGeneration),
      validOpaqueInput(contactIdentifier),
      validCivilDate(civilDate)
    else { return nil }
    return authenticationCode(
      namespace: namespace,
      domain: "birthday-autopilot.ios.occurrence.v1",
      components: [
        Data(accountGeneration.utf8),
        Data(contactIdentifier.utf8),
        Data(civilDate.utf8),
      ]
    )
  }

  static func occurrenceId(
    namespace: Data,
    accountGeneration: String,
    contactIdentifier: String,
    civilDate: String
  ) -> String? {
    occurrenceDigest(
      namespace: namespace,
      accountGeneration: accountGeneration,
      contactIdentifier: contactIdentifier,
      civilDate: civilDate
    ).map { occurrencePrefix + base64URL($0) }
  }

  /// Produces an opaque proposal handle containing only a day offset and
  /// contact ordinal followed by a full HMAC tag. The coordinates are useful
  /// only after every supplied binding is revalidated.
  static func proposalHandle(
    namespace: Data,
    accountGeneration: String,
    configurationGeneration: UInt64,
    baseCivilDate: String,
    timeZoneIdentifier: String,
    contactTableDigest: Data,
    dayOffset: Int,
    contactOrdinal: Int,
    occurrenceId: String
  ) -> String? {
    guard validNamespace(namespace),
      validOpaqueInput(accountGeneration),
      validCivilDate(baseCivilDate),
      validTimeZoneIdentifier(timeZoneIdentifier),
      contactTableDigest.count == digestByteCount,
      (0..<planningDayCount).contains(dayOffset),
      (0..<maximumContacts).contains(contactOrdinal),
      validOccurrenceId(occurrenceId),
      let encodedDay = UInt16(exactly: dayOffset),
      let encodedOrdinal = UInt16(exactly: contactOrdinal)
    else { return nil }

    var coordinates = Data()
    append(encodedDay, to: &coordinates)
    append(encodedOrdinal, to: &coordinates)
    let tag = authenticationCode(
      namespace: namespace,
      domain: "birthday-autopilot.ios.proposal-handle.v1",
      components: [
        Data(accountGeneration.utf8),
        Data(String(configurationGeneration).utf8),
        Data(baseCivilDate.utf8),
        Data(timeZoneIdentifier.utf8),
        contactTableDigest,
        coordinates,
        Data(occurrenceId.utf8),
      ]
    )
    return proposalPrefix + base64URL(coordinates + tag)
  }

  /// Verifies every material binding before returning the embedded ordinals.
  /// A malformed, stale, noncanonical, or wrong-binding handle returns nil.
  static func verifyProposalHandle(
    _ handle: String,
    namespace: Data,
    accountGeneration: String,
    configurationGeneration: UInt64,
    baseCivilDate: String,
    timeZoneIdentifier: String,
    contactTableDigest: Data,
    contactCount: Int,
    occurrenceId: String
  ) -> IOSCompanionProposalCoordinates? {
    guard validNamespace(namespace),
      validOpaqueInput(accountGeneration),
      validCivilDate(baseCivilDate),
      validTimeZoneIdentifier(timeZoneIdentifier),
      contactTableDigest.count == digestByteCount,
      (0...maximumContacts).contains(contactCount),
      validOccurrenceId(occurrenceId),
      handle.hasPrefix(proposalPrefix),
      let payload = decodeBase64URL(String(handle.dropFirst(proposalPrefix.count))),
      payload.count == 4 + digestByteCount
    else { return nil }

    let coordinateBytes = payload.prefix(4)
    let suppliedTag = payload.suffix(digestByteCount)
    let dayOffset = (UInt16(coordinateBytes[coordinateBytes.startIndex]) << 8)
      | UInt16(coordinateBytes[coordinateBytes.startIndex + 1])
    let ordinal = (UInt16(coordinateBytes[coordinateBytes.startIndex + 2]) << 8)
      | UInt16(coordinateBytes[coordinateBytes.startIndex + 3])
    guard Int(dayOffset) < planningDayCount,
      Int(ordinal) < contactCount
    else { return nil }

    let expectedTag = authenticationCode(
      namespace: namespace,
      domain: "birthday-autopilot.ios.proposal-handle.v1",
      components: [
        Data(accountGeneration.utf8),
        Data(String(configurationGeneration).utf8),
        Data(baseCivilDate.utf8),
        Data(timeZoneIdentifier.utf8),
        contactTableDigest,
        Data(coordinateBytes),
        Data(occurrenceId.utf8),
      ]
    )
    guard constantTimeEqual(Data(suppliedTag), expectedTag) else { return nil }
    return IOSCompanionProposalCoordinates(
      dayOffset: Int(dayOffset),
      contactOrdinal: Int(ordinal)
    )
  }

  private static func authenticationCode(
    namespace: Data,
    domain: String,
    components: [Data]
  ) -> Data {
    var message = Data(domain.utf8)
    message.append(0)
    for component in components {
      var length = UInt32(component.count).bigEndian
      withUnsafeBytes(of: &length) { message.append(contentsOf: $0) }
      message.append(component)
    }
    return Data(HMAC<SHA256>.authenticationCode(
      for: message,
      using: SymmetricKey(data: namespace)
    ))
  }

  private static func validNamespace(_ value: Data) -> Bool {
    value.count == namespaceByteCount
  }

  private static func validOpaqueInput(_ value: String) -> Bool {
    let bytes = Array(value.utf8)
    guard !bytes.isEmpty, bytes.count <= maximumOpaqueInputBytes,
      asciiAlphaNumeric(bytes[0])
    else { return false }
    return bytes.dropFirst().allSatisfy {
      asciiAlphaNumeric($0) || [45, 46, 58, 95].contains($0)
    }
  }

  private static func asciiAlphaNumeric(_ value: UInt8) -> Bool {
    (48...57).contains(value) || (65...90).contains(value)
      || (97...122).contains(value)
  }

  private static func validOccurrenceId(_ value: String) -> Bool {
    guard value.hasPrefix(occurrencePrefix),
      let decoded = decodeBase64URL(String(value.dropFirst(occurrencePrefix.count))),
      decoded.count == digestByteCount
    else { return false }
    return occurrencePrefix + base64URL(decoded) == value
  }

  private static func validCivilDate(_ value: String) -> Bool {
    let parts = value.split(separator: "-", omittingEmptySubsequences: false)
    guard parts.count == 3,
      parts[0].count == 4,
      parts[1].count == 2,
      parts[2].count == 2,
      let year = Int(parts[0]),
      let month = Int(parts[1]),
      let day = Int(parts[2]),
      (1...9_999).contains(year)
    else { return false }
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(secondsFromGMT: 0)!
    guard let date = calendar.date(
      from: DateComponents(year: year, month: month, day: day)
    ) else { return false }
    let roundTrip = calendar.dateComponents([.year, .month, .day], from: date)
    return roundTrip.year == year && roundTrip.month == month && roundTrip.day == day
  }

  private static func validTimeZoneIdentifier(_ value: String) -> Bool {
    guard !value.isEmpty,
      value.utf8.count <= maximumOpaqueInputBytes,
      value.unicodeScalars.allSatisfy({
        !CharacterSet.controlCharacters.contains($0)
      })
    else { return false }
    return TimeZone(identifier: value) != nil
  }

  private static func append(_ value: UInt16, to data: inout Data) {
    data.append(UInt8((value >> 8) & 0xFF))
    data.append(UInt8(value & 0xFF))
  }

  private static func base64URL(_ data: Data) -> String {
    data.base64EncodedString()
      .replacingOccurrences(of: "+", with: "-")
      .replacingOccurrences(of: "/", with: "_")
      .replacingOccurrences(of: "=", with: "")
  }

  private static func decodeBase64URL(_ value: String) -> Data? {
    guard !value.isEmpty,
      value.utf8.allSatisfy({ byte in
        asciiAlphaNumeric(byte) || byte == 45 || byte == 95
      })
    else { return nil }
    var base64 = value
      .replacingOccurrences(of: "-", with: "+")
      .replacingOccurrences(of: "_", with: "/")
    let remainder = base64.count % 4
    if remainder != 0 {
      base64 += String(repeating: "=", count: 4 - remainder)
    }
    guard let decoded = Data(base64Encoded: base64),
      base64URL(decoded) == value
    else { return nil }
    return decoded
  }

  private static func constantTimeEqual(_ lhs: Data, _ rhs: Data) -> Bool {
    guard lhs.count == rhs.count else { return false }
    var difference: UInt8 = 0
    for index in lhs.indices {
      difference |= lhs[index] ^ rhs[index]
    }
    return difference == 0
  }
}
