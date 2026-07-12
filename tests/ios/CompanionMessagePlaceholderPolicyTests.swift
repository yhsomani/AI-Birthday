@main
enum CompanionMessagePlaceholderPolicyTests {
  static func main() {
    guard IOSCompanionMessagePlaceholderPolicy.issue(
      text: "Happy birthday, {firstName}!",
      placeholderMode: "generic"
    ) == .invalidCount,
      IOSCompanionMessagePlaceholderPolicy.issue(
        text: "Happy birthday!",
        placeholderMode: "given-name"
      ) == .invalidCount,
      IOSCompanionMessagePlaceholderPolicy.issue(
        text: "Hi {firstName}, happy birthday {firstName}!",
        placeholderMode: "given-name"
      ) == .invalidCount,
      IOSCompanionMessagePlaceholderPolicy.issue(
        text: "Happy birthday, {first_name}!",
        placeholderMode: "given-name"
      ) == .unsupportedPlaceholder,
      IOSCompanionMessagePlaceholderPolicy.issue(
        text: "Happy birthday {friend!",
        placeholderMode: "generic"
      ) == .unsupportedPlaceholder,
      IOSCompanionMessagePlaceholderPolicy.isValid(
        text: "Happy birthday!",
        placeholderMode: "generic"
      ),
      IOSCompanionMessagePlaceholderPolicy.render(
        text: "Happy birthday!",
        placeholderMode: "generic",
        givenName: nil
      ) == "Happy birthday!",
      IOSCompanionMessagePlaceholderPolicy.render(
        text: "Happy birthday, {firstName}!",
        placeholderMode: "given-name",
        givenName: "Asha"
      ) == "Happy birthday, Asha!",
      IOSCompanionMessagePlaceholderPolicy.render(
        text: "Happy birthday, {firstName}!",
        placeholderMode: "given-name",
        givenName: nil
      ) == nil
    else {
      fatalError("Companion placeholder policy accepted unsafe template structure")
    }
  }
}
