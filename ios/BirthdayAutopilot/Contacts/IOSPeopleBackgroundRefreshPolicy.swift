import Foundation

/// Pure scheduling policy for the iOS People refresh boundary. `earliestBeginDate`
/// is only a lower bound; iOS decides whether and when the task actually runs.
enum IOSPeopleBackgroundRefreshPolicy {
  static let taskIdentifier =
    "com.yashsomani.birthdayautopilot.people-refresh"
  static let regularRefreshDelay: TimeInterval = 24 * 60 * 60
  static let transientRetryDelay: TimeInterval = 6 * 60 * 60
  static let minimumRateLimitDelay: TimeInterval = 60 * 60

  static func nextDelay(after outcome: IOSPeopleSyncOutcome) -> TimeInterval? {
    switch outcome {
    case .completed:
      return regularRefreshDelay
    case .failed(.authorizationRequired), .failed(.forbidden),
      .failed(.repeatedUnauthorized):
      // Only a foreground Google flow can resolve these outcomes. Scheduling
      // another background attempt would create a silent authorization loop.
      return nil
    case .failed(.rateLimited(let retryAfterSeconds)):
      guard let retryAfterSeconds else { return transientRetryDelay }
      return min(
        regularRefreshDelay,
        max(minimumRateLimitDelay, TimeInterval(retryAfterSeconds))
      )
    case .failed(.networkOffline), .failed(.partial), .failed(.unavailable),
      .failed(.cancelled):
      return transientRetryDelay
    case .failed(.malformed), .failed(.storage):
      return regularRefreshDelay
    }
  }

  static func taskCompletedSuccessfully(_ outcome: IOSPeopleSyncOutcome) -> Bool {
    if case .completed = outcome { return true }
    return false
  }
}
