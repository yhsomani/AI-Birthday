@main
enum GeminiOperationalPolicyTests {
  static func main() {
    guard IOSGeminiOperationalPolicy.parameterKey
      == "gemini_suggestions_enabled",
      IOSGeminiOperationalPolicy.inAppDefault == false,
      IOSGeminiOperationalPolicy.minimumFetchIntervalSeconds == 3_600,
      IOSGeminiOperationalPolicy.fetchTimeoutSeconds == 8,
      IOSGeminiOperationalPolicy.localCompletionTimeoutSeconds == 10,
      IOSGeminiOperationalPolicy.acceptsActivatedValue(
        sourceIsRemote: true,
        canonicalString: "true",
        boolValue: true
      ),
      !IOSGeminiOperationalPolicy.acceptsActivatedValue(
        sourceIsRemote: false,
        canonicalString: "true",
        boolValue: true
      ),
      !IOSGeminiOperationalPolicy.acceptsActivatedValue(
        sourceIsRemote: true,
        canonicalString: "TRUE",
        boolValue: true
      ),
      !IOSGeminiOperationalPolicy.acceptsActivatedValue(
        sourceIsRemote: true,
        canonicalString: " true",
        boolValue: true
      ),
      !IOSGeminiOperationalPolicy.acceptsActivatedValue(
        sourceIsRemote: true,
        canonicalString: "1",
        boolValue: true
      ),
      !IOSGeminiOperationalPolicy.acceptsActivatedValue(
        sourceIsRemote: true,
        canonicalString: "true",
        boolValue: false
      )
    else {
      fatalError("Gemini operational switch accepted a non-canonical enable value")
    }
  }
}
