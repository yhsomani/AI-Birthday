import Foundation

@main
struct ContactsFreshnessPolicyTests {
  static func main() throws {
    guard CommandLine.arguments.count == 2 else {
      fatalError("contacts-freshness-contract-path-required")
    }
    let data = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
    guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
      root["version"] as? String == "contacts-freshness-v1",
      let normalMilliseconds = root["normalMaximumAgeMillis"] as? NSNumber,
      let maximumMilliseconds = root["automationMaximumAgeMillis"] as? NSNumber,
      let cases = root["cases"] as? [[String: Any]]
    else { fatalError("contacts-freshness-contract-invalid") }

    require(
      normalMilliseconds.doubleValue / 1_000
        == IOSContactsFreshnessPolicy.normalMaximumAge,
      "normal-boundary-mismatch"
    )
    require(
      maximumMilliseconds.doubleValue / 1_000
        == IOSContactsFreshnessPolicy.companionMaximumAge,
      "maximum-boundary-mismatch"
    )

    var ids = Set<String>()
    for item in cases {
      guard let id = item["id"] as? String,
        ids.insert(id).inserted,
        let storedState = item["storedState"] as? String,
        let expectedBand = item["expectedBand"] as? String,
        let expectedAllows = item["allowsAutomation"] as? Bool
      else { fatalError("contacts-freshness-case-invalid") }
      let lastSuccess = millisecondsDate(item["lastSuccessMillis"])
      let trustedNow = millisecondsDate(item["trustedNowMillis"])
      let source: IOSContactsFreshnessSourceState
      switch storedState {
      case "FRESH": source = .verified
      case "STALE_WARNING": source = .retainedAfterFailure
      case "AUTH_ACTION_REQUIRED": source = .authorizationRequired
      case "SAFETY_PAUSED": source = .safetyPaused
      case "NEVER_SYNCED": source = .unavailable
      default: fatalError("unsupported-stored-state-\(id)")
      }
      let assessment = IOSContactsFreshnessPolicy.assess(
        sourceState: source,
        lastSuccessAt: lastSuccess,
        trustedNow: trustedNow
      )
      require(assessment.band.rawValue == expectedBand, "band-mismatch-\(id)")
      require(
        assessment.allowsCompanionAction == expectedAllows,
        "allowance-mismatch-\(id)"
      )
    }

    let server = Date(timeIntervalSince1970: 4_000_000)
    let received = Date(timeIntervalSince1970: 10_000)
    require(
      IOSContactsFreshnessPolicy.estimateTrustedNow(
        serverObservedAt: server,
        locallyReceivedAt: received,
        now: received.addingTimeInterval(60),
        maximumObservationAge: 60
      ) == server.addingTimeInterval(60),
      "trusted-time-advance-mismatch"
    )
    require(
      IOSContactsFreshnessPolicy.estimateTrustedNow(
        serverObservedAt: server,
        locallyReceivedAt: received,
        now: received.addingTimeInterval(-0.001),
        maximumObservationAge: 60
      ) == nil,
      "clock-rollback-did-not-fail-closed"
    )
  }

  private static func millisecondsDate(_ value: Any?) -> Date? {
    guard let number = value as? NSNumber else { return nil }
    return Date(timeIntervalSince1970: number.doubleValue / 1_000)
  }

  private static func require(
    _ condition: @autoclosure () -> Bool,
    _ message: String
  ) {
    if !condition() { fatalError(message) }
  }
}
