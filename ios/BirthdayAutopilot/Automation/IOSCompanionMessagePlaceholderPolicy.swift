import Foundation

enum IOSCompanionMessagePlaceholderIssue: Equatable {
  case invalidCount
  case unsupportedPlaceholder
}

/// One native policy for both persisted and user-supplied template structure.
/// Generic templates contain no braces. Personalized templates contain exactly
/// one literal `{firstName}` and no other brace-delimited or stray brace text.
enum IOSCompanionMessagePlaceholderPolicy {
  static let givenNamePlaceholder = "{firstName}"

  static func issue(
    text: String,
    placeholderMode: String
  ) -> IOSCompanionMessagePlaceholderIssue? {
    let placeholderCount = text.components(
      separatedBy: givenNamePlaceholder
    ).count - 1
    let withoutSupportedPlaceholder = text.replacingOccurrences(
      of: givenNamePlaceholder,
      with: ""
    )

    guard !withoutSupportedPlaceholder.contains("{")
      && !withoutSupportedPlaceholder.contains("}")
    else {
      return .unsupportedPlaceholder
    }

    switch placeholderMode {
    case "generic":
      return placeholderCount == 0 ? nil : .invalidCount
    case "given-name":
      return placeholderCount == 1 ? nil : .invalidCount
    default:
      return .unsupportedPlaceholder
    }
  }

  static func isValid(text: String, placeholderMode: String) -> Bool {
    issue(text: text, placeholderMode: placeholderMode) == nil
  }

  /// Produces final composer text only from a structurally valid draft. A
  /// generic template never depends on a contact name; a personalized template
  /// requires one non-empty safe given name and replaces its sole placeholder.
  static func render(
    text: String,
    placeholderMode: String,
    givenName: String?
  ) -> String? {
    guard isValid(text: text, placeholderMode: placeholderMode) else {
      return nil
    }
    if placeholderMode == "generic" { return text }
    guard let givenName, !givenName.isEmpty else { return nil }
    return text.replacingOccurrences(
      of: givenNamePlaceholder,
      with: givenName
    )
  }
}
