import Foundation

enum IOSCompanionTerminalResolution: String, Codable {
  case cancelled
  case failed
  case outcomeUnknown = "outcome-unknown"
  case reportedSent = "reported-sent"

  var retainsRepeatMarker: Bool {
    self == .outcomeUnknown || self == .reportedSent
  }
}

enum IOSCompanionTerminalCheck: Equatable {
  case blocked
  case clear
  case invalid
}

private struct IOSCompanionDigestShard: Codable, Equatable {
  let prefix: UInt8
  var sortedSuffixes: Data
}

private struct IOSCompanionTerminalBucket: Codable, Equatable {
  let civilDate: String
  var latestCommittedAt: Date
  var legacySuppressAll: Bool
  var shards: [IOSCompanionDigestShard]
}

/// Content-free repeat protection for foreground iOS composer operations.
///
/// Each marker is a full 256-bit occurrence digest split into a one-byte shard
/// prefix and sorted 31-byte suffix. The ledger contains no contact identifier,
/// destination, name, birthday source field, or message. Detailed activity can
/// therefore be cleared independently without weakening repeat protection.
struct IOSCompanionTerminalLedger: Codable, Equatable {
  static let schemaVersion = 1
  static let digestByteCount = 32
  static let digestSuffixByteCount = digestByteCount - 1
  static let maximumEntryCount = 20_000
  static let maximumBucketCount = 400
  static let maximumSerializedBytes = 2 * 1_024 * 1_024
  static let detailedRetention: TimeInterval = 30 * 24 * 60 * 60
  static let trustedTimeFreshness: TimeInterval = 5 * 60

  private(set) var version: Int
  private(set) var entryCount: Int
  private var buckets: [IOSCompanionTerminalBucket]

  enum LedgerError: Error, Equatable {
    case capacityReached
    case digestInvalid
    case duplicateMarker
    case encodedPayloadInvalid
    case legacyFenceActive
    case markerMissing
    case schemaUnsupported
    case timestampInvalid
  }

  init() {
    version = Self.schemaVersion
    entryCount = 0
    buckets = []
  }

  private init(
    validatingVersion version: Int,
    entryCount: Int,
    buckets: [IOSCompanionTerminalBucket]
  ) throws {
    guard version == Self.schemaVersion else {
      throw LedgerError.schemaUnsupported
    }
    let actualEntryCount = try Self.validateBuckets(buckets)
    guard entryCount == actualEntryCount,
      (0...Self.maximumEntryCount).contains(entryCount)
    else {
      throw LedgerError.encodedPayloadInvalid
    }
    self.version = version
    self.entryCount = entryCount
    self.buckets = buckets
  }

  /// Invalid input is reported separately so callers can fail closed instead
  /// of accidentally treating malformed material as an absent marker.
  func check(digest: Data, civilDate: String) -> IOSCompanionTerminalCheck {
    guard digest.count == Self.digestByteCount,
      Self.validCivilDate(civilDate)
    else { return .invalid }
    guard let bucketIndex = bucketIndex(for: civilDate, requireExact: true) else {
      return .clear
    }
    let bucket = buckets[bucketIndex]
    if bucket.legacySuppressAll { return .blocked }
    return Self.contains(digest, in: bucket.shards) ? .blocked : .clear
  }

  func blocks(digest: Data, civilDate: String) -> Bool {
    check(digest: digest, civilDate: civilDate) != .clear
  }

  /// Called by the durable OpenCommitted transaction before MessageUI is
  /// presented. A duplicate is refused rather than treated as idempotent.
  mutating func recordCommitted(
    digest: Data,
    civilDate: String,
    committedAt: Date
  ) throws {
    guard digest.count == Self.digestByteCount,
      Self.validCivilDate(civilDate)
    else { throw LedgerError.digestInvalid }
    guard let timestamp = Self.canonicalTimestamp(committedAt) else {
      throw LedgerError.timestampInvalid
    }
    guard entryCount < Self.maximumEntryCount else {
      throw LedgerError.capacityReached
    }

    let insertionIndex = bucketIndex(for: civilDate, requireExact: false) ?? buckets.endIndex
    if insertionIndex < buckets.count, buckets[insertionIndex].civilDate == civilDate {
      guard !buckets[insertionIndex].legacySuppressAll else {
        throw LedgerError.legacyFenceActive
      }
      guard !Self.contains(digest, in: buckets[insertionIndex].shards) else {
        throw LedgerError.duplicateMarker
      }
      try Self.insert(digest, into: &buckets[insertionIndex].shards)
      if timestamp > buckets[insertionIndex].latestCommittedAt {
        buckets[insertionIndex].latestCommittedAt = timestamp
      }
    } else {
      guard buckets.count < Self.maximumBucketCount else {
        throw LedgerError.capacityReached
      }
      var shards: [IOSCompanionDigestShard] = []
      try Self.insert(digest, into: &shards)
      buckets.insert(
        IOSCompanionTerminalBucket(
          civilDate: civilDate,
          latestCommittedAt: timestamp,
          legacySuppressAll: false,
          shards: shards
        ),
        at: insertionIndex
      )
    }
    entryCount += 1
  }

  /// A definitive cancellation or failure removes the provisional marker.
  /// Reported-Sent and Unknown retain it. If persistence later fails, the
  /// caller must keep the pre-resolution ledger and report Unknown.
  mutating func resolve(
    digest: Data,
    civilDate: String,
    outcome: IOSCompanionTerminalResolution,
    resolvedAt: Date
  ) throws {
    guard digest.count == Self.digestByteCount,
      Self.validCivilDate(civilDate)
    else { throw LedgerError.digestInvalid }
    guard let timestamp = Self.canonicalTimestamp(resolvedAt) else {
      throw LedgerError.timestampInvalid
    }
    guard let bucketIndex = bucketIndex(for: civilDate, requireExact: true),
      Self.contains(digest, in: buckets[bucketIndex].shards)
    else { throw LedgerError.markerMissing }

    if outcome.retainsRepeatMarker {
      if timestamp > buckets[bucketIndex].latestCommittedAt {
        buckets[bucketIndex].latestCommittedAt = timestamp
      }
      return
    }

    guard Self.remove(digest, from: &buckets[bucketIndex].shards) else {
      throw LedgerError.markerMissing
    }
    entryCount -= 1
    if buckets[bucketIndex].shards.isEmpty,
      !buckets[bucketIndex].legacySuppressAll
    {
      buckets.remove(at: bucketIndex)
    }
  }

  /// Migration fallback when a legacy terminal operation no longer has a
  /// provable contact mapping. It suppresses the whole date until normal
  /// trusted retention release instead of guessing and risking a duplicate.
  mutating func installLegacyDateWideFence(
    civilDate: String,
    recordedAt: Date
  ) throws {
    guard Self.validCivilDate(civilDate) else {
      throw LedgerError.digestInvalid
    }
    guard let timestamp = Self.canonicalTimestamp(recordedAt) else {
      throw LedgerError.timestampInvalid
    }
    let insertionIndex = bucketIndex(for: civilDate, requireExact: false) ?? buckets.endIndex
    if insertionIndex < buckets.count, buckets[insertionIndex].civilDate == civilDate {
      buckets[insertionIndex].legacySuppressAll = true
      if timestamp > buckets[insertionIndex].latestCommittedAt {
        buckets[insertionIndex].latestCommittedAt = timestamp
      }
      return
    }
    guard buckets.count < Self.maximumBucketCount else {
      throw LedgerError.capacityReached
    }
    buckets.insert(
      IOSCompanionTerminalBucket(
        civilDate: civilDate,
        latestCommittedAt: timestamp,
        legacySuppressAll: true,
        shards: []
      ),
      at: insertionIndex
    )
  }

  /// Releases only after both the 30-day detail period and a fresh trusted
  /// server observation strictly beyond the civil date's latest possible end
  /// at UTC-12 plus five minutes.
  @discardableResult
  mutating func pruneReleased(
    now: Date,
    trustedServerTime: Date?
  ) -> [String] {
    guard let now = Self.canonicalTimestamp(now),
      let trustedServerTime = trustedServerTime.flatMap(Self.canonicalTimestamp),
      abs(now.timeIntervalSince(trustedServerTime)) <= Self.trustedTimeFreshness
    else { return [] }

    var released: [String] = []
    var retained: [IOSCompanionTerminalBucket] = []
    retained.reserveCapacity(buckets.count)
    var retainedEntryCount = 0
    for bucket in buckets {
      let age = now.timeIntervalSince(bucket.latestCommittedAt)
      let mayRelease = age >= Self.detailedRetention
        && age.isFinite
        && Self.latestPossibleReleaseBoundary(for: bucket.civilDate).map {
          trustedServerTime > $0
        } == true
      if mayRelease {
        released.append(bucket.civilDate)
      } else {
        retained.append(bucket)
        retainedEntryCount += Self.markerCount(in: bucket.shards)
      }
    }
    buckets = retained
    entryCount = retainedEntryCount
    return released
  }

  static func latestPossibleReleaseBoundary(for civilDate: String) -> Date? {
    guard let startUTC = utcStart(of: civilDate) else { return nil }
    // A date finishes latest at UTC-12: UTC midnight + 36 hours. The five-minute
    // tolerance matches the companion reset and terminal-marker policy.
    return startUTC.addingTimeInterval((36 * 60 * 60) + (5 * 60))
  }

  func serialized() throws -> Data {
    let encoder = JSONEncoder()
    encoder.dateEncodingStrategy = .millisecondsSince1970
    encoder.outputFormatting = [.sortedKeys]
    let data = try encoder.encode(self)
    guard data.count <= Self.maximumSerializedBytes else {
      throw LedgerError.encodedPayloadInvalid
    }
    return data
  }

  static func deserialize(_ data: Data) throws -> IOSCompanionTerminalLedger {
    guard !data.isEmpty, data.count <= maximumSerializedBytes else {
      throw LedgerError.encodedPayloadInvalid
    }
    let decoder = JSONDecoder()
    decoder.dateDecodingStrategy = .millisecondsSince1970
    return try decoder.decode(IOSCompanionTerminalLedger.self, from: data)
  }

  init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    try self.init(
      validatingVersion: container.decode(Int.self, forKey: .version),
      entryCount: container.decode(Int.self, forKey: .entryCount),
      buckets: container.decode(
        [IOSCompanionTerminalBucket].self,
        forKey: .buckets
      )
    )
  }

  private func bucketIndex(for civilDate: String, requireExact: Bool) -> Int? {
    var lower = 0
    var upper = buckets.count
    while lower < upper {
      let middle = lower + ((upper - lower) / 2)
      if buckets[middle].civilDate < civilDate {
        lower = middle + 1
      } else {
        upper = middle
      }
    }
    if requireExact {
      return lower < buckets.count && buckets[lower].civilDate == civilDate
        ? lower : nil
    }
    return lower
  }

  private static func validateBuckets(
    _ buckets: [IOSCompanionTerminalBucket]
  ) throws -> Int {
    guard buckets.count <= maximumBucketCount else {
      throw LedgerError.capacityReached
    }
    var previousDate: String?
    var total = 0
    for bucket in buckets {
      guard validCivilDate(bucket.civilDate),
        previousDate.map({ $0 < bucket.civilDate }) ?? true,
        canonicalTimestamp(bucket.latestCommittedAt) == bucket.latestCommittedAt,
        bucket.shards.count <= 256
      else { throw LedgerError.encodedPayloadInvalid }
      previousDate = bucket.civilDate

      var previousPrefix: UInt8?
      for shard in bucket.shards {
        guard previousPrefix.map({ $0 < shard.prefix }) ?? true,
          !shard.sortedSuffixes.isEmpty,
          shard.sortedSuffixes.count % digestSuffixByteCount == 0
        else { throw LedgerError.encodedPayloadInvalid }
        previousPrefix = shard.prefix
        let count = shard.sortedSuffixes.count / digestSuffixByteCount
        for index in 1..<count {
          guard compareSuffixes(
            shard.sortedSuffixes,
            lhsRecord: index - 1,
            rhsRecord: index
          ) == .orderedAscending else {
            throw LedgerError.encodedPayloadInvalid
          }
        }
        total += count
        guard total <= maximumEntryCount else {
          throw LedgerError.capacityReached
        }
      }
    }
    return total
  }

  private static func contains(
    _ digest: Data,
    in shards: [IOSCompanionDigestShard]
  ) -> Bool {
    guard let components = digestComponents(digest),
      let shardIndex = shardIndex(
        prefix: components.prefix,
        in: shards,
        requireExact: true
      )
    else { return false }
    return suffixSearch(
      components.suffix,
      in: shards[shardIndex].sortedSuffixes
    ).found
  }

  private static func insert(
    _ digest: Data,
    into shards: inout [IOSCompanionDigestShard]
  ) throws {
    guard let components = digestComponents(digest) else {
      throw LedgerError.digestInvalid
    }
    let insertionIndex = shardIndex(
      prefix: components.prefix,
      in: shards,
      requireExact: false
    ) ?? shards.endIndex
    if insertionIndex < shards.count,
      shards[insertionIndex].prefix == components.prefix
    {
      let result = suffixSearch(
        components.suffix,
        in: shards[insertionIndex].sortedSuffixes
      )
      guard !result.found else { throw LedgerError.duplicateMarker }
      shards[insertionIndex].sortedSuffixes.insert(
        contentsOf: components.suffix,
        at: result.recordIndex * digestSuffixByteCount
      )
    } else {
      shards.insert(
        IOSCompanionDigestShard(
          prefix: components.prefix,
          sortedSuffixes: components.suffix
        ),
        at: insertionIndex
      )
    }
  }

  private static func remove(
    _ digest: Data,
    from shards: inout [IOSCompanionDigestShard]
  ) -> Bool {
    guard let components = digestComponents(digest),
      let shardIndex = shardIndex(
        prefix: components.prefix,
        in: shards,
        requireExact: true
      )
    else { return false }
    let result = suffixSearch(
      components.suffix,
      in: shards[shardIndex].sortedSuffixes
    )
    guard result.found else { return false }
    let lower = result.recordIndex * digestSuffixByteCount
    shards[shardIndex].sortedSuffixes.removeSubrange(
      lower..<(lower + digestSuffixByteCount)
    )
    if shards[shardIndex].sortedSuffixes.isEmpty {
      shards.remove(at: shardIndex)
    }
    return true
  }

  private static func digestComponents(
    _ digest: Data
  ) -> (prefix: UInt8, suffix: Data)? {
    guard digest.count == digestByteCount, let prefix = digest.first else {
      return nil
    }
    return (prefix, Data(digest.dropFirst()))
  }

  private static func shardIndex(
    prefix: UInt8,
    in shards: [IOSCompanionDigestShard],
    requireExact: Bool
  ) -> Int? {
    var lower = 0
    var upper = shards.count
    while lower < upper {
      let middle = lower + ((upper - lower) / 2)
      if shards[middle].prefix < prefix {
        lower = middle + 1
      } else {
        upper = middle
      }
    }
    if requireExact {
      return lower < shards.count && shards[lower].prefix == prefix ? lower : nil
    }
    return lower
  }

  private static func suffixSearch(
    _ suffix: Data,
    in sortedSuffixes: Data
  ) -> (recordIndex: Int, found: Bool) {
    let recordCount = sortedSuffixes.count / digestSuffixByteCount
    var lower = 0
    var upper = recordCount
    while lower < upper {
      let middle = lower + ((upper - lower) / 2)
      let comparison = compareSuffix(
        sortedSuffixes,
        record: middle,
        with: suffix
      )
      if comparison == .orderedAscending {
        lower = middle + 1
      } else {
        upper = middle
      }
    }
    let found = lower < recordCount
      && compareSuffix(sortedSuffixes, record: lower, with: suffix) == .orderedSame
    return (lower, found)
  }

  private static func compareSuffix(
    _ values: Data,
    record: Int,
    with suffix: Data
  ) -> ComparisonResult {
    let start = record * digestSuffixByteCount
    for index in 0..<digestSuffixByteCount {
      let lhs = values[start + index]
      let rhs = suffix[index]
      if lhs < rhs { return .orderedAscending }
      if lhs > rhs { return .orderedDescending }
    }
    return .orderedSame
  }

  private static func compareSuffixes(
    _ values: Data,
    lhsRecord: Int,
    rhsRecord: Int
  ) -> ComparisonResult {
    let lhsStart = lhsRecord * digestSuffixByteCount
    let rhsStart = rhsRecord * digestSuffixByteCount
    for index in 0..<digestSuffixByteCount {
      let lhs = values[lhsStart + index]
      let rhs = values[rhsStart + index]
      if lhs < rhs { return .orderedAscending }
      if lhs > rhs { return .orderedDescending }
    }
    return .orderedSame
  }

  private static func markerCount(
    in shards: [IOSCompanionDigestShard]
  ) -> Int {
    shards.reduce(0) {
      $0 + ($1.sortedSuffixes.count / digestSuffixByteCount)
    }
  }

  private static func canonicalTimestamp(_ value: Date) -> Date? {
    let seconds = value.timeIntervalSince1970
    guard seconds.isFinite, seconds >= 0 else { return nil }
    return Date(timeIntervalSince1970: (seconds * 1_000).rounded(.down) / 1_000)
  }

  private static func validCivilDate(_ value: String) -> Bool {
    utcStart(of: value) != nil
  }

  private static func utcStart(of civilDate: String) -> Date? {
    let parts = civilDate.split(separator: "-", omittingEmptySubsequences: false)
    guard parts.count == 3,
      parts[0].count == 4,
      parts[1].count == 2,
      parts[2].count == 2,
      let year = Int(parts[0]),
      let month = Int(parts[1]),
      let day = Int(parts[2]),
      (1...9_999).contains(year)
    else { return nil }
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(secondsFromGMT: 0)!
    guard let date = calendar.date(
      from: DateComponents(year: year, month: month, day: day)
    ) else { return nil }
    let roundTrip = calendar.dateComponents([.year, .month, .day], from: date)
    guard roundTrip.year == year, roundTrip.month == month, roundTrip.day == day else {
      return nil
    }
    return date
  }
}
