import XCTest
@testable import BirthdayAutopilot

final class IOSPeopleBackgroundRefreshPolicyTests: XCTestCase {
  func testCompletedSyncSchedulesTheRegularBestEffortRefresh() {
    let outcome = IOSPeopleSyncOutcome.completed(
      contactCount: 12,
      mode: .incremental,
      recoveredExpiredToken: false
    )

    XCTAssertEqual(
      IOSPeopleBackgroundRefreshPolicy.nextDelay(after: outcome),
      24 * 60 * 60
    )
    XCTAssertTrue(
      IOSPeopleBackgroundRefreshPolicy.taskCompletedSuccessfully(outcome)
    )
  }

  func testAuthorizationFailureStopsBackgroundAuthorizationLoop() {
    for failure in [
      IOSPeopleSyncFailure.authorizationRequired,
      .forbidden,
      .repeatedUnauthorized,
    ] {
      let outcome = IOSPeopleSyncOutcome.failed(failure)
      XCTAssertNil(IOSPeopleBackgroundRefreshPolicy.nextDelay(after: outcome))
      XCTAssertFalse(
        IOSPeopleBackgroundRefreshPolicy.taskCompletedSuccessfully(outcome)
      )
    }
  }

  func testTransientAndRateLimitedFailuresAreBounded() {
    XCTAssertEqual(
      IOSPeopleBackgroundRefreshPolicy.nextDelay(
        after: .failed(.networkOffline)
      ),
      6 * 60 * 60
    )
    XCTAssertEqual(
      IOSPeopleBackgroundRefreshPolicy.nextDelay(
        after: .failed(.rateLimited(retryAfterSeconds: 30))
      ),
      60 * 60
    )
    XCTAssertEqual(
      IOSPeopleBackgroundRefreshPolicy.nextDelay(
        after: .failed(.rateLimited(retryAfterSeconds: 200_000))
      ),
      24 * 60 * 60
    )
  }
}
