import XCTest
@testable import BirthdayAutopilot

final class IOSCompanionRetentionPolicyTests: XCTestCase {
  func testDetailExpiresAtThirtyElapsedDaysButNotBefore() {
    let recordedAt = Date(timeIntervalSince1970: 1_700_000_000)
    XCTAssertFalse(
      IOSCompanionRetentionPolicy.detailHasExpired(
        recordedAt: recordedAt,
        now: recordedAt.addingTimeInterval(30 * 24 * 60 * 60 - 1)
      )
    )
    XCTAssertTrue(
      IOSCompanionRetentionPolicy.detailHasExpired(
        recordedAt: recordedAt,
        now: recordedAt.addingTimeInterval(30 * 24 * 60 * 60)
      )
    )
  }

  func testClockRollbackNeverAcceleratesDeletion() {
    let recordedAt = Date(timeIntervalSince1970: 1_700_000_000)
    XCTAssertFalse(
      IOSCompanionRetentionPolicy.detailHasExpired(
        recordedAt: recordedAt,
        now: recordedAt.addingTimeInterval(-24 * 60 * 60)
      )
    )
  }

  func testTerminalMarkerNeedsBothAgeAndTrustedWorstZoneRelease() {
    let recordedAt = Date(timeIntervalSince1970: 1_700_000_000)
    let now = recordedAt.addingTimeInterval(31 * 24 * 60 * 60)
    let releaseAfter = recordedAt.addingTimeInterval(36 * 60 * 60)

    XCTAssertFalse(
      IOSCompanionRetentionPolicy.mayReleaseTerminalMarker(
        recordedAt: recordedAt,
        now: now,
        trustedServerTime: nil,
        releaseAfter: releaseAfter
      )
    )
    XCTAssertFalse(
      IOSCompanionRetentionPolicy.mayReleaseTerminalMarker(
        recordedAt: recordedAt,
        now: now,
        trustedServerTime: releaseAfter,
        releaseAfter: releaseAfter
      )
    )
    XCTAssertFalse(
      IOSCompanionRetentionPolicy.mayReleaseTerminalMarker(
        recordedAt: recordedAt,
        now: now,
        trustedServerTime: now.addingTimeInterval(
          -(IOSCompanionRetentionPolicy.trustedTimeFreshness + 1)
        ),
        releaseAfter: releaseAfter
      ),
      "old persisted server time is proof of the past, but is not the fresh response required to release the marker"
    )
    XCTAssertTrue(
      IOSCompanionRetentionPolicy.mayReleaseTerminalMarker(
        recordedAt: recordedAt,
        now: now,
        trustedServerTime: now,
        releaseAfter: releaseAfter
      )
    )
  }

  func testAttentionClaimExpiresWithinThirtyDaysAcrossTimeZones() {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(secondsFromGMT: 0)!
    let startUTC = calendar.date(
      from: DateComponents(year: 2026, month: 7, day: 12)
    )!
    let earliestPossibleClaim = startUTC.addingTimeInterval(-14 * 60 * 60)

    XCTAssertFalse(
      IOSCompanionRetentionPolicy.attentionClaimHasExpired(
        civilDate: "2026-07-12",
        now: earliestPossibleClaim.addingTimeInterval(30 * 24 * 60 * 60 - 1)
      )
    )
    XCTAssertTrue(
      IOSCompanionRetentionPolicy.attentionClaimHasExpired(
        civilDate: "2026-07-12",
        now: earliestPossibleClaim.addingTimeInterval(30 * 24 * 60 * 60)
      )
    )
    XCTAssertFalse(
      IOSCompanionRetentionPolicy.attentionClaimHasExpired(
        civilDate: "2026-02-30",
        now: startUTC.addingTimeInterval(365 * 24 * 60 * 60)
      )
    )
  }

  func testAttentionClaimNeverRepeatsAfterCivilDateRollback() {
    XCTAssertTrue(
      IOSCompanionRetentionPolicy.mayAdvanceAttentionClaim(
        previousCivilDate: nil,
        to: "2026-07-12"
      )
    )
    XCTAssertTrue(
      IOSCompanionRetentionPolicy.mayAdvanceAttentionClaim(
        previousCivilDate: "2026-07-12",
        to: "2026-07-13"
      )
    )
    XCTAssertFalse(
      IOSCompanionRetentionPolicy.mayAdvanceAttentionClaim(
        previousCivilDate: "2026-07-12",
        to: "2026-07-12"
      )
    )
    XCTAssertFalse(
      IOSCompanionRetentionPolicy.mayAdvanceAttentionClaim(
        previousCivilDate: "2026-07-12",
        to: "2026-07-11"
      )
    )
  }
}
