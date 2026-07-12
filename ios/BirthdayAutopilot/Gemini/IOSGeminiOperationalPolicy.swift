/// Pure policy for the native-only Gemini operational switch.
///
/// Remote Config values are client-visible operational metadata, never
/// credentials. Only one canonical, remotely activated boolean may enable the
/// provider path; every default, missing, malformed, or non-remote value is Off.
enum IOSGeminiOperationalPolicy {
  static let parameterKey = "gemini_suggestions_enabled"
  static let inAppDefault = false
  static let minimumFetchIntervalSeconds: Double = 60 * 60
  static let fetchTimeoutSeconds: Double = 8
  static let localCompletionTimeoutSeconds: Double = 10

  static func acceptsActivatedValue(
    sourceIsRemote: Bool,
    canonicalString: String,
    boolValue: Bool
  ) -> Bool {
    sourceIsRemote && canonicalString == "true" && boolValue
  }
}
