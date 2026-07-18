import Foundation

/// A compact, content-minimized index for the iOS companion's 400-day plan.
///
/// The payload stores only canonical contact ordinals grouped by civil-day
/// offset. It never stores a contact identifier, destination, name, birthday,
/// or rendered message. The owning workflow binds ordinals to its canonical
/// contact table through `contactTableDigest` and `configurationGeneration`.
struct IOSCompanionPlanningIndex: Codable, Equatable {
  static let schemaVersion = 1
  static let planningDayCount = 400
  static let maximumContactCount = 10_000
  static let maximumOccurrencesPerContact = 2
  static let maximumRecordCount = maximumContactCount * maximumOccurrencesPerContact
  static let maximumEncodedBucketBytes =
    (planningDayCount * MemoryLayout<UInt16>.size)
    + (maximumRecordCount * MemoryLayout<UInt16>.size)
  static let maximumSerializedBytes = 128 * 1_024
  static let maximumPageSize = 50

  let version: Int
  let configurationGeneration: UInt64
  let baseCivilDate: String
  let timeZoneIdentifier: String
  let contactTableDigest: Data
  let contactCount: Int
  let recordCount: Int
  let encodedBuckets: Data

  struct Page: Equatable {
    let ordinals: [UInt16]
    let nextRecordOffset: Int?
  }

  enum ValidationError: Error, Equatable {
    case bindingInvalid
    case bucketCountInvalid
    case contactCountInvalid
    case digestInvalid
    case duplicateOrdinal
    case encodedPayloadInvalid
    case occurrenceCountInvalid
    case ordinalOutOfRange
    case pageInvalid
    case recordCountInvalid
    case schemaUnsupported
  }

  init(
    configurationGeneration: UInt64,
    baseCivilDate: String,
    timeZoneIdentifier: String,
    contactTableDigest: Data,
    contactCount: Int,
    ordinalsByDay: [[UInt16]]
  ) throws {
    guard ordinalsByDay.count == Self.planningDayCount else {
      throw ValidationError.bucketCountInvalid
    }
    guard Self.validBinding(
      baseCivilDate: baseCivilDate,
      timeZoneIdentifier: timeZoneIdentifier,
      contactTableDigest: contactTableDigest,
      contactCount: contactCount
    ) else {
      throw ValidationError.bindingInvalid
    }

    var payload = Data()
    payload.reserveCapacity(Self.maximumEncodedBucketBytes)
    var total = 0
    var occurrencesByOrdinal = [UInt8](repeating: 0, count: contactCount)

    for ordinals in ordinalsByDay {
      guard ordinals.count <= Self.maximumContactCount,
        let encodedCount = UInt16(exactly: ordinals.count)
      else {
        throw ValidationError.recordCountInvalid
      }
      Self.append(encodedCount, to: &payload)

      var previous: UInt16?
      for ordinal in ordinals {
        guard Int(ordinal) < contactCount else {
          throw ValidationError.ordinalOutOfRange
        }
        guard previous.map({ $0 < ordinal }) ?? true else {
          throw ValidationError.duplicateOrdinal
        }
        previous = ordinal
        let ordinalIndex = Int(ordinal)
        guard occurrencesByOrdinal[ordinalIndex] < Self.maximumOccurrencesPerContact else {
          throw ValidationError.occurrenceCountInvalid
        }
        occurrencesByOrdinal[ordinalIndex] += 1
        total += 1
        guard total <= Self.maximumRecordCount else {
          throw ValidationError.recordCountInvalid
        }
        Self.append(ordinal, to: &payload)
      }
    }

    try self.init(
      validatingVersion: Self.schemaVersion,
      configurationGeneration: configurationGeneration,
      baseCivilDate: baseCivilDate,
      timeZoneIdentifier: timeZoneIdentifier,
      contactTableDigest: contactTableDigest,
      contactCount: contactCount,
      recordCount: total,
      encodedBuckets: payload
    )
  }

  /// Validation initializer used by protected-store decoding and migration.
  /// It accepts no unvalidated state: every byte is consumed exactly once.
  init(
    validatingVersion version: Int = IOSCompanionPlanningIndex.schemaVersion,
    configurationGeneration: UInt64,
    baseCivilDate: String,
    timeZoneIdentifier: String,
    contactTableDigest: Data,
    contactCount: Int,
    recordCount: Int,
    encodedBuckets: Data
  ) throws {
    guard version == Self.schemaVersion else {
      throw ValidationError.schemaUnsupported
    }
    guard Self.validBinding(
      baseCivilDate: baseCivilDate,
      timeZoneIdentifier: timeZoneIdentifier,
      contactTableDigest: contactTableDigest,
      contactCount: contactCount
    ) else {
      throw ValidationError.bindingInvalid
    }
    guard (0...Self.maximumRecordCount).contains(recordCount) else {
      throw ValidationError.recordCountInvalid
    }
    let decodedCount = try Self.validateEncodedBuckets(
      encodedBuckets,
      contactCount: contactCount
    )
    guard decodedCount == recordCount else {
      throw ValidationError.recordCountInvalid
    }

    self.version = version
    self.configurationGeneration = configurationGeneration
    self.baseCivilDate = baseCivilDate
    self.timeZoneIdentifier = timeZoneIdentifier
    self.contactTableDigest = contactTableDigest
    self.contactCount = contactCount
    self.recordCount = recordCount
    self.encodedBuckets = encodedBuckets
  }

  func matches(
    configurationGeneration: UInt64,
    baseCivilDate: String,
    timeZoneIdentifier: String,
    contactTableDigest: Data,
    contactCount: Int
  ) -> Bool {
    self.configurationGeneration == configurationGeneration
      && self.baseCivilDate == baseCivilDate
      && self.timeZoneIdentifier == timeZoneIdentifier
      && self.contactTableDigest == contactTableDigest
      && self.contactCount == contactCount
  }

  func ordinals(dayOffset: Int) throws -> [UInt16] {
    guard (0..<Self.planningDayCount).contains(dayOffset) else {
      throw ValidationError.pageInvalid
    }
    var values: [UInt16] = []
    var recordOffset = 0
    repeat {
      let result = try page(
        dayOffset: dayOffset,
        recordOffset: recordOffset,
        limit: Self.maximumPageSize
      )
      values.append(contentsOf: result.ordinals)
      guard let next = result.nextRecordOffset else { break }
      recordOffset = next
    } while true
    return values
  }

  /// Returns a deterministic bounded page without decoding any other day's
  /// ordinals into a retained collection.
  func page(
      dayOffset: Int,
      recordOffset: Int,
      limit: Int
  ) throws -> Page {
    guard (0..<Self.planningDayCount).contains(dayOffset),
      recordOffset >= 0,
      (1...Self.maximumPageSize).contains(limit)
    else {
      throw ValidationError.pageInvalid
    }

    var offset = 0
    for currentDay in 0..<Self.planningDayCount {
      guard let count = Self.readUInt16(encodedBuckets, offset: &offset) else {
        throw ValidationError.encodedPayloadInvalid
      }
      let dayRecordCount = Int(count)
      let byteCount = dayRecordCount * MemoryLayout<UInt16>.size
      guard offset <= encodedBuckets.count,
        byteCount <= encodedBuckets.count - offset
      else {
        throw ValidationError.encodedPayloadInvalid
      }

      if currentDay == dayOffset {
        guard recordOffset <= dayRecordCount else {
          throw ValidationError.pageInvalid
        }
        let end = min(dayRecordCount, recordOffset + limit)
        var values: [UInt16] = []
        values.reserveCapacity(end - recordOffset)
        var recordByteOffset = offset + (recordOffset * MemoryLayout<UInt16>.size)
        for _ in recordOffset..<end {
          guard let ordinal = Self.readUInt16(
            encodedBuckets,
            offset: &recordByteOffset
          ) else {
            throw ValidationError.encodedPayloadInvalid
          }
          values.append(ordinal)
        }
        return Page(
          ordinals: values,
          nextRecordOffset: end < dayRecordCount ? end : nil
        )
      }
      offset += byteCount
    }
    throw ValidationError.encodedPayloadInvalid
  }

  func serialized() throws -> Data {
    let encoder = JSONEncoder()
    encoder.outputFormatting = [.sortedKeys]
    let data = try encoder.encode(self)
    guard data.count <= Self.maximumSerializedBytes else {
      throw ValidationError.encodedPayloadInvalid
    }
    return data
  }

  static func deserialize(_ data: Data) throws -> IOSCompanionPlanningIndex {
    guard !data.isEmpty, data.count <= maximumSerializedBytes else {
      throw ValidationError.encodedPayloadInvalid
    }
    return try JSONDecoder().decode(IOSCompanionPlanningIndex.self, from: data)
  }

  init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    try self.init(
      validatingVersion: container.decode(Int.self, forKey: .version),
      configurationGeneration: container.decode(
        UInt64.self,
        forKey: .configurationGeneration
      ),
      baseCivilDate: container.decode(String.self, forKey: .baseCivilDate),
      timeZoneIdentifier: container.decode(
        String.self,
        forKey: .timeZoneIdentifier
      ),
      contactTableDigest: container.decode(Data.self, forKey: .contactTableDigest),
      contactCount: container.decode(Int.self, forKey: .contactCount),
      recordCount: container.decode(Int.self, forKey: .recordCount),
      encodedBuckets: container.decode(Data.self, forKey: .encodedBuckets)
    )
  }

  private static func validateEncodedBuckets(
    _ payload: Data,
    contactCount: Int
  ) throws -> Int {
    guard payload.count >= planningDayCount * MemoryLayout<UInt16>.size,
      payload.count <= maximumEncodedBucketBytes
    else {
      throw ValidationError.encodedPayloadInvalid
    }

    var offset = 0
    var total = 0
    var occurrencesByOrdinal = [UInt8](repeating: 0, count: contactCount)
    for _ in 0..<planningDayCount {
      guard let countValue = readUInt16(payload, offset: &offset) else {
        throw ValidationError.encodedPayloadInvalid
      }
      let count = Int(countValue)
      guard count <= maximumContactCount else {
        throw ValidationError.recordCountInvalid
      }
      var previous: UInt16?
      for _ in 0..<count {
        guard let ordinal = readUInt16(payload, offset: &offset) else {
          throw ValidationError.encodedPayloadInvalid
        }
        guard Int(ordinal) < contactCount else {
          throw ValidationError.ordinalOutOfRange
        }
        guard previous.map({ $0 < ordinal }) ?? true else {
          throw ValidationError.duplicateOrdinal
        }
        previous = ordinal
        let ordinalIndex = Int(ordinal)
        guard occurrencesByOrdinal[ordinalIndex] < maximumOccurrencesPerContact else {
          throw ValidationError.occurrenceCountInvalid
        }
        occurrencesByOrdinal[ordinalIndex] += 1
        total += 1
        guard total <= maximumRecordCount else {
          throw ValidationError.recordCountInvalid
        }
      }
    }
    guard offset == payload.count else {
      throw ValidationError.encodedPayloadInvalid
    }
    return total
  }

  private static func validBinding(
    baseCivilDate: String,
    timeZoneIdentifier: String,
    contactTableDigest: Data,
    contactCount: Int
  ) -> Bool {
    validCivilDate(baseCivilDate)
      && validTimeZoneIdentifier(timeZoneIdentifier)
      && contactTableDigest.count == 32
      && (0...maximumContactCount).contains(contactCount)
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
      value.utf8.count <= 128,
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

  private static func readUInt16(_ data: Data, offset: inout Int) -> UInt16? {
    guard offset >= 0, offset <= data.count, data.count - offset >= 2 else {
      return nil
    }
    let value = (UInt16(data[offset]) << 8) | UInt16(data[offset + 1])
    offset += 2
    return value
  }
}

/// Builds one immutable resolution context for an ordinal scan and keeps
/// looking when an individual candidate fails exact material validation.
/// Keeping this primitive pure makes the 10k invalid-prefix behavior directly
/// executable without loading UIKit, React, or a protected store.
enum IOSCompanionLazyOrdinalScanner {
  static func first<Context, Material>(
    buildContext: () -> Context?,
    ordinals: (Context) throws -> [UInt16],
    materialize: (Context, Int) -> Material?
  ) rethrows -> Material? {
    guard let context = buildContext() else { return nil }
    let candidates = try ordinals(context)
    guard candidates.count <= IOSCompanionPlanningIndex.maximumContactCount else {
      return nil
    }
    for ordinal in candidates {
      if let material = materialize(context, Int(ordinal)) {
        return material
      }
    }
    return nil
  }
}

/// Resolves configured contacts with one bounded pass over the configuration
/// table. Callers can then mutate the returned indices without repeated linear
/// searches or index-invalidating insertions.
enum IOSCompanionConfiguredContactScanner {
  static func matchingIndices<Configuration, Contact>(
    configurations: [Configuration],
    contactsByIdentifier: [String: Contact],
    identifier: (Configuration) -> String,
    matches: (Configuration, Contact) -> Bool
  ) -> [Int]? {
    guard configurations.count <= IOSCompanionPlanningIndex.maximumContactCount,
      contactsByIdentifier.count <= IOSCompanionPlanningIndex.maximumContactCount
    else { return nil }

    var result: [Int] = []
    result.reserveCapacity(configurations.count)
    for index in configurations.indices {
      let configuration = configurations[index]
      guard let contact = contactsByIdentifier[identifier(configuration)],
        matches(configuration, contact)
      else { continue }
      result.append(index)
    }
    return result
  }
}

/// Final composer material may use local calendar calculations only after the
/// device clock agrees with the recent authenticated server-time estimate.
enum IOSCompanionTrustedClockPolicy {
  static let maximumLocalSkew: TimeInterval = 5 * 60

  static func materializationNow(
    localNow: Date,
    trustedServerEstimate: Date?
  ) -> Date? {
    guard let trustedServerEstimate,
      valid(localNow), valid(trustedServerEstimate)
    else { return nil }
    let skew = localNow.timeIntervalSince(trustedServerEstimate)
    guard skew.isFinite, abs(skew) <= maximumLocalSkew else { return nil }
    return trustedServerEstimate
  }

  private static func valid(_ date: Date) -> Bool {
    let value = date.timeIntervalSince1970
    return value.isFinite && value >= 0
  }
}
