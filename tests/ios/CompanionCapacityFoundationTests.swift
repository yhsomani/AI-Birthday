import Foundation

@main
enum CompanionCapacityFoundationTests {
  private static let accountGeneration = "11111111-2222-3333-4444-555555555555"
  private static let baseCivilDate = "2026-07-13"
  private static let timeZoneIdentifier = "Asia/Kolkata"

  static func main() throws {
    let startedAt = Date()
    let namespace = Data((0..<32).map(UInt8.init))
    let contactIdentifiers = (0..<10_000).map {
      String(format: "contact-%05d", $0)
    }
    let tableDigest = try require(
      IOSCompanionOccurrenceIdentity.contactTableDigest(
        namespace: namespace,
        canonicalContactIdentifiers: contactIdentifiers
      ),
      "canonical contact table did not produce a digest"
    )

    let planningStartedAt = Date()
    try testPlanningIndex(
      namespace: namespace,
      contactIdentifiers: contactIdentifiers,
      tableDigest: tableDigest
    )
    let planningMilliseconds = elapsedMilliseconds(since: planningStartedAt)

    try testOccurrenceIdentity(
      namespace: namespace,
      contactIdentifiers: contactIdentifiers,
      tableDigest: tableDigest
    )

    let terminalStartedAt = Date()
    let terminalBytes = try testTerminalLedger(
      namespace: namespace,
      contactIdentifiers: contactIdentifiers
    )
    let terminalMilliseconds = elapsedMilliseconds(since: terminalStartedAt)

    print(
      "IOS_CAPACITY_FOUNDATIONS_OK "
        + "planning_ms=\(planningMilliseconds) "
        + "terminal_ms=\(terminalMilliseconds) "
        + "terminal_bytes=\(terminalBytes) "
        + "total_ms=\(elapsedMilliseconds(since: startedAt))"
    )
  }

  private static func testPlanningIndex(
    namespace: Data,
    contactIdentifiers: [String],
    tableDigest: Data
  ) throws {
    var oneDay = Array(
      repeating: [UInt16](),
      count: IOSCompanionPlanningIndex.planningDayCount
    )
    oneDay[0] = (0..<10_000).map { UInt16($0) }
    let index = try IOSCompanionPlanningIndex(
      configurationGeneration: 7,
      baseCivilDate: baseCivilDate,
      timeZoneIdentifier: timeZoneIdentifier,
      contactTableDigest: tableDigest,
      contactCount: contactIdentifiers.count,
      ordinalsByDay: oneDay
    )
    try expect(index.recordCount == 10_000, "one-day index dropped records")
    try expect(
      index.encodedBuckets.count == 20_800,
      "one-day index used an unexpected binary size"
    )
    try expect(
      index.matches(
        configurationGeneration: 7,
        baseCivilDate: baseCivilDate,
        timeZoneIdentifier: timeZoneIdentifier,
        contactTableDigest: tableDigest,
        contactCount: 10_000
      ),
      "exact planning binding was rejected"
    )
    try expect(
      !index.matches(
        configurationGeneration: 8,
        baseCivilDate: baseCivilDate,
        timeZoneIdentifier: timeZoneIdentifier,
        contactTableDigest: tableDigest,
        contactCount: 10_000
      ),
      "stale configuration generation was accepted"
    )
    try expect(
      !index.matches(
        configurationGeneration: 7,
        baseCivilDate: baseCivilDate,
        timeZoneIdentifier: "UTC",
        contactTableDigest: tableDigest,
        contactCount: 10_000
      ),
      "wrong timezone binding was accepted"
    )
    var wrongDigest = tableDigest
    wrongDigest[0] ^= 0xFF
    try expect(
      !index.matches(
        configurationGeneration: 7,
        baseCivilDate: baseCivilDate,
        timeZoneIdentifier: timeZoneIdentifier,
        contactTableDigest: wrongDigest,
        contactCount: 10_000
      ),
      "wrong contact-table digest was accepted"
    )

    var paged: [UInt16] = []
    var offset = 0
    repeat {
      let page = try index.page(dayOffset: 0, recordOffset: offset, limit: 50)
      try expect(page.ordinals.count <= 50, "planning page exceeded its bound")
      paged.append(contentsOf: page.ordinals)
      guard let next = page.nextRecordOffset else { break }
      try expect(next > offset, "planning cursor did not advance")
      offset = next
    } while true
    try expect(
      paged == oneDay[0],
      "bounded planning pages contained a duplicate or gap"
    )

    let serialized = try index.serialized()
    let serializedAgain = try index.serialized()
    let decodedIndex = try IOSCompanionPlanningIndex.deserialize(serialized)
    try expect(
      serialized == serializedAgain,
      "planning serialization is nondeterministic"
    )
    try expect(
      decodedIndex == index,
      "planning round trip changed the index"
    )
    let serializedText = String(data: serialized, encoding: .utf8) ?? ""
    try expect(
      !serializedText.contains("contact-"),
      "planning persistence contains a raw contact identifier"
    )

    var maximum = oneDay
    maximum[399] = oneDay[0]
    let maximumIndex = try IOSCompanionPlanningIndex(
      configurationGeneration: 7,
      baseCivilDate: baseCivilDate,
      timeZoneIdentifier: timeZoneIdentifier,
      contactTableDigest: tableDigest,
      contactCount: 10_000,
      ordinalsByDay: maximum
    )
    try expect(
      maximumIndex.recordCount == 20_000
        && maximumIndex.encodedBuckets.count
          == IOSCompanionPlanningIndex.maximumEncodedBucketBytes,
      "20k planning maximum was not represented exactly"
    )

    var duplicate = Array(
      repeating: [UInt16](),
      count: IOSCompanionPlanningIndex.planningDayCount
    )
    duplicate[0] = [3, 3]
    try expectThrows("duplicate ordinal was accepted") {
      _ = try IOSCompanionPlanningIndex(
        configurationGeneration: 7,
        baseCivilDate: baseCivilDate,
        timeZoneIdentifier: timeZoneIdentifier,
        contactTableDigest: tableDigest,
        contactCount: 10_000,
        ordinalsByDay: duplicate
      )
    }

    var thirdOccurrence = Array(
      repeating: [UInt16](),
      count: IOSCompanionPlanningIndex.planningDayCount
    )
    thirdOccurrence[0] = [1]
    thirdOccurrence[1] = [1]
    thirdOccurrence[2] = [1]
    try expectThrows("a third 400-day occurrence was accepted") {
      _ = try IOSCompanionPlanningIndex(
        configurationGeneration: 7,
        baseCivilDate: baseCivilDate,
        timeZoneIdentifier: timeZoneIdentifier,
        contactTableDigest: tableDigest,
        contactCount: 10_000,
        ordinalsByDay: thirdOccurrence
      )
    }

    var outOfRange = Array(
      repeating: [UInt16](),
      count: IOSCompanionPlanningIndex.planningDayCount
    )
    outOfRange[0] = [1]
    try expectThrows("an out-of-range contact ordinal was accepted") {
      _ = try IOSCompanionPlanningIndex(
        configurationGeneration: 7,
        baseCivilDate: baseCivilDate,
        timeZoneIdentifier: timeZoneIdentifier,
        contactTableDigest: tableDigest,
        contactCount: 1,
        ordinalsByDay: outOfRange
      )
    }

    try expectThrows("a truncated planning payload was accepted") {
      _ = try IOSCompanionPlanningIndex(
        validatingVersion: IOSCompanionPlanningIndex.schemaVersion,
        configurationGeneration: 7,
        baseCivilDate: baseCivilDate,
        timeZoneIdentifier: timeZoneIdentifier,
        contactTableDigest: tableDigest,
        contactCount: 10_000,
        recordCount: maximumIndex.recordCount,
        encodedBuckets: maximumIndex.encodedBuckets.dropLast()
      )
    }
    try expectThrows("a planning payload with trailing bytes was accepted") {
      var trailing = maximumIndex.encodedBuckets
      trailing.append(0)
      _ = try IOSCompanionPlanningIndex(
        validatingVersion: IOSCompanionPlanningIndex.schemaVersion,
        configurationGeneration: 7,
        baseCivilDate: baseCivilDate,
        timeZoneIdentifier: timeZoneIdentifier,
        contactTableDigest: tableDigest,
        contactCount: 10_000,
        recordCount: maximumIndex.recordCount,
        encodedBuckets: trailing
      )
    }
    try expectThrows("an invalid timezone was accepted") {
      _ = try IOSCompanionPlanningIndex(
        configurationGeneration: 7,
        baseCivilDate: baseCivilDate,
        timeZoneIdentifier: "Not/A_Timezone",
        contactTableDigest: tableDigest,
        contactCount: 10_000,
        ordinalsByDay: oneDay
      )
    }
    try expectThrows("truncated serialized planning state was accepted") {
      _ = try IOSCompanionPlanningIndex.deserialize(serialized.dropLast())
    }

    try expect(
      IOSCompanionOccurrenceIdentity.contactTableDigest(
        namespace: namespace,
        canonicalContactIdentifiers: [contactIdentifiers[1], contactIdentifiers[0]]
      ) == nil,
      "an unsorted contact table produced a digest"
    )
  }

  private static func testOccurrenceIdentity(
    namespace: Data,
    contactIdentifiers: [String],
    tableDigest: Data
  ) throws {
    let occurrence = try require(
      IOSCompanionOccurrenceIdentity.occurrenceId(
        namespace: namespace,
        accountGeneration: accountGeneration,
        contactIdentifier: contactIdentifiers[42],
        civilDate: "2026-07-18"
      ),
      "valid occurrence identity was rejected"
    )
    try expect(
      occurrence == IOSCompanionOccurrenceIdentity.occurrenceId(
        namespace: namespace,
        accountGeneration: accountGeneration,
        contactIdentifier: contactIdentifiers[42],
        civilDate: "2026-07-18"
      ),
      "occurrence HMAC was not deterministic"
    )
    try expect(
      occurrence != IOSCompanionOccurrenceIdentity.occurrenceId(
        namespace: namespace,
        accountGeneration: accountGeneration,
        contactIdentifier: contactIdentifiers[43],
        civilDate: "2026-07-18"
      ),
      "different contacts received the same occurrence identity"
    )
    try expect(
      occurrence != IOSCompanionOccurrenceIdentity.occurrenceId(
        namespace: namespace,
        accountGeneration: accountGeneration,
        contactIdentifier: contactIdentifiers[42],
        civilDate: "2026-07-19"
      ),
      "different dates received the same occurrence identity"
    )

    let handle = try require(
      IOSCompanionOccurrenceIdentity.proposalHandle(
        namespace: namespace,
        accountGeneration: accountGeneration,
        configurationGeneration: 7,
        baseCivilDate: baseCivilDate,
        timeZoneIdentifier: timeZoneIdentifier,
        contactTableDigest: tableDigest,
        dayOffset: 5,
        contactOrdinal: 42,
        occurrenceId: occurrence
      ),
      "valid proposal handle was rejected"
    )
    try expect(handle.utf8.count <= 64, "proposal handle exceeds bridge bound")
    try expect(
      IOSCompanionOccurrenceIdentity.verifyProposalHandle(
        handle,
        namespace: namespace,
        accountGeneration: accountGeneration,
        configurationGeneration: 7,
        baseCivilDate: baseCivilDate,
        timeZoneIdentifier: timeZoneIdentifier,
        contactTableDigest: tableDigest,
        contactCount: 10_000,
        occurrenceId: occurrence
      ) == IOSCompanionProposalCoordinates(dayOffset: 5, contactOrdinal: 42),
      "exact proposal binding did not verify"
    )
    try expect(
      IOSCompanionOccurrenceIdentity.verifyProposalHandle(
        handle,
        namespace: namespace,
        accountGeneration: accountGeneration,
        configurationGeneration: 8,
        baseCivilDate: baseCivilDate,
        timeZoneIdentifier: timeZoneIdentifier,
        contactTableDigest: tableDigest,
        contactCount: 10_000,
        occurrenceId: occurrence
      ) == nil,
      "stale proposal configuration verified"
    )
    var wrongDigest = tableDigest
    wrongDigest[31] ^= 0x01
    try expect(
      IOSCompanionOccurrenceIdentity.verifyProposalHandle(
        handle,
        namespace: namespace,
        accountGeneration: accountGeneration,
        configurationGeneration: 7,
        baseCivilDate: baseCivilDate,
        timeZoneIdentifier: timeZoneIdentifier,
        contactTableDigest: wrongDigest,
        contactCount: 10_000,
        occurrenceId: occurrence
      ) == nil,
      "wrong table digest verified"
    )
    let tampered = String(handle.dropLast()) + (handle.last == "A" ? "B" : "A")
    try expect(
      IOSCompanionOccurrenceIdentity.verifyProposalHandle(
        tampered,
        namespace: namespace,
        accountGeneration: accountGeneration,
        configurationGeneration: 7,
        baseCivilDate: baseCivilDate,
        timeZoneIdentifier: timeZoneIdentifier,
        contactTableDigest: tableDigest,
        contactCount: 10_000,
        occurrenceId: occurrence
      ) == nil,
      "tampered proposal handle verified"
    )
    try expect(
      IOSCompanionOccurrenceIdentity.occurrenceId(
        namespace: Data(repeating: 0, count: 31),
        accountGeneration: accountGeneration,
        contactIdentifier: contactIdentifiers[0],
        civilDate: "2026-07-18"
      ) == nil,
      "invalid namespace was accepted"
    )
  }

  private static func testTerminalLedger(
    namespace: Data,
    contactIdentifiers: [String]
  ) throws -> Int {
    let committedAt = Date(timeIntervalSince1970: 1_768_219_200)
    var digests: [Data] = []
    digests.reserveCapacity(contactIdentifiers.count)
    var ledger = IOSCompanionTerminalLedger()
    for identifier in contactIdentifiers {
      let digest = try require(
        IOSCompanionOccurrenceIdentity.occurrenceDigest(
          namespace: namespace,
          accountGeneration: accountGeneration,
          contactIdentifier: identifier,
          civilDate: "2026-01-12"
        ),
        "terminal digest generation failed"
      )
      digests.append(digest)
      try ledger.recordCommitted(
        digest: digest,
        civilDate: "2026-01-12",
        committedAt: committedAt
      )
    }
    try expect(ledger.entryCount == 10_000, "terminal ledger dropped markers")
    for index in stride(from: 0, to: digests.count, by: 97) {
      try expect(
        ledger.check(digest: digests[index], civilDate: "2026-01-12") == .blocked,
        "terminal ledger missed an exact marker"
      )
    }
    let absent = Data(repeating: 0xFF, count: 32)
    try expect(
      ledger.check(digest: absent, civilDate: "2026-01-12") == .clear,
      "terminal ledger produced a false positive"
    )
    try expect(
      ledger.check(digest: Data(), civilDate: "2026-01-12") == .invalid,
      "invalid terminal input was treated as clear"
    )
    try expect(
      ledger.blocks(digest: Data(), civilDate: "2026-01-12"),
      "invalid terminal input did not fail closed"
    )
    try expectThrows("duplicate terminal marker was accepted") {
      try ledger.recordCommitted(
        digest: digests[0],
        civilDate: "2026-01-12",
        committedAt: committedAt
      )
    }

    let serialized = try ledger.serialized()
    try expect(
      serialized.count < 1_000_000,
      "10k terminal ledger exceeded its compact storage budget"
    )
    let serializedAgain = try ledger.serialized()
    let decodedLedger = try IOSCompanionTerminalLedger.deserialize(serialized)
    try expect(
      serialized == serializedAgain,
      "terminal serialization is nondeterministic"
    )
    try expect(
      decodedLedger == ledger,
      "terminal round trip changed the ledger"
    )
    let serializedText = String(data: serialized, encoding: .utf8) ?? ""
    try expect(
      !serializedText.contains("contact-")
        && !serializedText.localizedCaseInsensitiveContains("birthday"),
      "terminal persistence contains raw private material"
    )
    try expectThrows("truncated terminal JSON was accepted") {
      _ = try IOSCompanionTerminalLedger.deserialize(serialized.dropLast())
    }

    var cancelled = IOSCompanionTerminalLedger()
    try cancelled.recordCommitted(
      digest: digests[0], civilDate: "2026-01-12", committedAt: committedAt
    )
    try cancelled.resolve(
      digest: digests[0], civilDate: "2026-01-12", outcome: .cancelled,
      resolvedAt: committedAt.addingTimeInterval(1)
    )
    try expect(
      cancelled.check(digest: digests[0], civilDate: "2026-01-12") == .clear,
      "Cancelled retained a repeat marker"
    )

    var failed = IOSCompanionTerminalLedger()
    try failed.recordCommitted(
      digest: digests[1], civilDate: "2026-01-12", committedAt: committedAt
    )
    try failed.resolve(
      digest: digests[1], civilDate: "2026-01-12", outcome: .failed,
      resolvedAt: committedAt.addingTimeInterval(1)
    )
    try expect(
      failed.check(digest: digests[1], civilDate: "2026-01-12") == .clear,
      "Failed retained a repeat marker"
    )

    var reportedSent = IOSCompanionTerminalLedger()
    try reportedSent.recordCommitted(
      digest: digests[2], civilDate: "2026-01-12", committedAt: committedAt
    )
    try reportedSent.resolve(
      digest: digests[2], civilDate: "2026-01-12", outcome: .reportedSent,
      resolvedAt: committedAt.addingTimeInterval(1)
    )
    try expect(
      reportedSent.check(digest: digests[2], civilDate: "2026-01-12") == .blocked,
      "Reported-Sent released its repeat marker"
    )

    var unknown = IOSCompanionTerminalLedger()
    try unknown.recordCommitted(
      digest: digests[3], civilDate: "2026-01-12", committedAt: committedAt
    )
    try unknown.resolve(
      digest: digests[3], civilDate: "2026-01-12", outcome: .outcomeUnknown,
      resolvedAt: committedAt.addingTimeInterval(1)
    )
    try expect(
      unknown.check(digest: digests[3], civilDate: "2026-01-12") == .blocked,
      "Unknown released its repeat marker"
    )

    var legacy = IOSCompanionTerminalLedger()
    try legacy.installLegacyDateWideFence(
      civilDate: "2026-01-12",
      recordedAt: committedAt
    )
    try expect(
      legacy.check(digest: absent, civilDate: "2026-01-12") == .blocked,
      "legacy date-wide fence did not fail closed"
    )

    var retained = IOSCompanionTerminalLedger()
    try retained.recordCommitted(
      digest: digests[4], civilDate: "2026-01-12", committedAt: committedAt
    )
    let beforeThirtyDays = committedAt.addingTimeInterval(
      IOSCompanionTerminalLedger.detailedRetention - 1
    )
    try expect(
      retained.pruneReleased(
        now: beforeThirtyDays,
        trustedServerTime: beforeThirtyDays
      ).isEmpty,
      "terminal marker released before 30 days"
    )
    let afterThirtyDays = committedAt.addingTimeInterval(
      IOSCompanionTerminalLedger.detailedRetention + 1
    )
    try expect(
      retained.pruneReleased(
        now: afterThirtyDays,
        trustedServerTime: afterThirtyDays.addingTimeInterval(
          -(IOSCompanionTerminalLedger.trustedTimeFreshness + 1)
        )
      ).isEmpty,
      "stale trusted time released a terminal marker"
    )
    try expect(
      retained.pruneReleased(
        now: afterThirtyDays,
        trustedServerTime: afterThirtyDays
      ) == ["2026-01-12"],
      "fresh trusted time did not release an expired terminal marker"
    )
    try expect(retained.entryCount == 0, "released marker remained counted")

    var legacyRetention = IOSCompanionTerminalLedger()
    try legacyRetention.installLegacyDateWideFence(
      civilDate: "2026-01-12", recordedAt: committedAt
    )
    try expect(
      legacyRetention.pruneReleased(
        now: afterThirtyDays,
        trustedServerTime: afterThirtyDays
      ) == ["2026-01-12"],
      "legacy date fence did not follow trusted retention"
    )

    return serialized.count
  }

  private static func elapsedMilliseconds(since start: Date) -> Int {
    Int((Date().timeIntervalSince(start) * 1_000).rounded())
  }

  private static func expect(
    _ condition: @autoclosure () -> Bool,
    _ message: String
  ) throws {
    guard condition() else { throw TestFailure(message) }
  }

  private static func expectThrows(
    _ message: String,
    _ body: () throws -> Void
  ) throws {
    do {
      try body()
    } catch {
      return
    }
    throw TestFailure(message)
  }

  private static func require<Value>(
    _ value: Value?,
    _ message: String
  ) throws -> Value {
    guard let value else { throw TestFailure(message) }
    return value
  }

  private struct TestFailure: Error, CustomStringConvertible {
    let description: String

    init(_ description: String) {
      self.description = description
    }
  }
}
