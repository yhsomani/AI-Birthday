import FirebaseAppCheck
import FirebaseCore
import Foundation

enum IOSGoogleConfigurationFailure: String {
  case bundleMismatch
  case clientMismatch
  case configurationMissing
  case invalidConfiguration
  case projectMismatch
  case tierMismatch
  case urlSchemeMismatch
}

struct IOSGoogleRuntimeConfiguration {
  let environment: String
  let firebaseOptions: FirebaseOptions
  let googleClientID: String
  let reversedClientID: String

  // Provider values are intentionally omitted.
  var safeDescription: String { "IOSGoogleRuntimeConfiguration(<redacted>)" }
}

enum IOSGoogleConfigurationResult {
  case ready(IOSGoogleRuntimeConfiguration)
  case unavailable(IOSGoogleConfigurationFailure)
}

enum IOSGoogleConfigurationResolver {
  private static let productionBundleID = "com.yashsomani.birthdayautopilot"
  private static let maximumPlistBytes = 64 * 1_024

  static func resolve(bundle: Bundle = .main) -> IOSGoogleConfigurationResult {
    guard let environment = bundle.object(
      forInfoDictionaryKey: "BirthdayFirebaseEnvironment"
    ) as? String,
      ["dev", "staging", "prod"].contains(environment)
    else {
      return .unavailable(.tierMismatch)
    }
    let expectedBundleID: String
    switch environment {
    case "dev": expectedBundleID = "\(productionBundleID).dev"
    case "staging": expectedBundleID = "\(productionBundleID).staging"
    case "prod": expectedBundleID = productionBundleID
    default: return .unavailable(.tierMismatch)
    }
    guard bundle.bundleIdentifier == expectedBundleID else {
      return .unavailable(.bundleMismatch)
    }
    guard let expectedProjectID = bundle.object(
      forInfoDictionaryKey: "BirthdayExpectedFirebaseProjectID"
    ) as? String,
      validProjectID(expectedProjectID)
    else {
      return .unavailable(.projectMismatch)
    }
    guard bundle.object(forInfoDictionaryKey: "GIDServerClientID") == nil else {
      // A server client ID can enable serverAuthCode/offline-access flows, which
      // are explicitly outside this product's mobile-only Contacts contract.
      return .unavailable(.invalidConfiguration)
    }
    guard let path = bundle.path(forResource: "GoogleService-Info", ofType: "plist"),
      let attributes = try? FileManager.default.attributesOfItem(atPath: path),
      (attributes[.size] as? NSNumber)?.intValue ?? 0 <= maximumPlistBytes,
      let data = try? Data(contentsOf: URL(fileURLWithPath: path), options: [.mappedIfSafe]),
      let raw = try? PropertyListSerialization.propertyList(from: data, options: [], format: nil),
      let config = raw as? [String: Any],
      let options = FirebaseOptions(contentsOfFile: path)
    else {
      return .unavailable(.configurationMissing)
    }
    let requiredKeys = [
      "API_KEY", "BUNDLE_ID", "CLIENT_ID", "GCM_SENDER_ID", "GOOGLE_APP_ID",
      "PROJECT_ID", "REVERSED_CLIENT_ID",
    ]
    guard requiredKeys.allSatisfy({ key in
      guard let value = config[key] as? String else { return false }
      return !value.isEmpty && value.utf8.count <= 2_048
    }) else {
      return .unavailable(.invalidConfiguration)
    }
    guard (config["BUNDLE_ID"] as? String) == expectedBundleID,
      options.bundleID == expectedBundleID
    else {
      return .unavailable(.bundleMismatch)
    }
    guard (config["PROJECT_ID"] as? String) == expectedProjectID,
      options.projectID == expectedProjectID
    else {
      return .unavailable(.projectMismatch)
    }
    guard let clientID = config["CLIENT_ID"] as? String,
      let reversedClientID = config["REVERSED_CLIENT_ID"] as? String,
      validClientID(clientID), reversedClientID == reverseClientID(clientID),
      options.clientID == clientID
    else {
      return .unavailable(.clientMismatch)
    }
    let declaredSetting = bundle.object(
      forInfoDictionaryKey: "BirthdayGoogleReversedClientID"
    ) as? String
    guard declaredSetting == reversedClientID,
      bundleURLSchemes(bundle).filter({ $0 == reversedClientID }).count == 1
    else {
      return .unavailable(.urlSchemeMismatch)
    }
    return .ready(
      IOSGoogleRuntimeConfiguration(
        environment: environment,
        firebaseOptions: options,
        googleClientID: clientID,
        reversedClientID: reversedClientID
      )
    )
  }

  private static func validProjectID(_ value: String) -> Bool {
    value.range(
      of: "^[a-z][a-z0-9-]{4,28}[a-z0-9]$",
      options: .regularExpression
    ) != nil
  }

  private static func validClientID(_ value: String) -> Bool {
    value.range(
      of: "^[0-9]+-[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com$",
      options: .regularExpression
    ) != nil
  }

  private static func reverseClientID(_ value: String) -> String {
    value.split(separator: ".").reversed().joined(separator: ".")
  }

  private static func bundleURLSchemes(_ bundle: Bundle) -> [String] {
    guard let types = bundle.object(forInfoDictionaryKey: "CFBundleURLTypes") as? [[String: Any]]
    else {
      return []
    }
    return types.flatMap { ($0["CFBundleURLSchemes"] as? [String]) ?? [] }
  }
}

final class BirthdayAppAttestProviderFactory: NSObject, AppCheckProviderFactory {
  func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
    AppAttestProvider(app: app)
  }
}
