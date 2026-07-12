import React
import ReactAppDependencyProvider
import React_RCTAppDelegate
import UIKit
import UserNotifications

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
  var reactNativeDelegate: ReactNativeDelegate?
  var reactNativeFactory: RCTReactNativeFactory?
  private(set) var reactNativeLaunchOptions: [UIApplication.LaunchOptionsKey: Any]?

  func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {
    #if BIRTHDAY_E2E
      validateE2EHost()
    #elseif BIRTHDAY_SMOKE
      validateProductionSmokeHost()
    #else
    // Apple requires permitted background task handlers to be registered once
    // before launch finishes. A scheduling failure never broadens access or
    // changes the foreground companion behavior.
    _ = IOSPeopleBackgroundRefreshCoordinator.shared.registerAtLaunch()

    // Configuration is validated and App Check is installed before any Firebase
    // or Google Identity request. Missing/mismatched tier config stays fail-closed.
    if application.isProtectedDataAvailable {
      IOSGoogleIdentityCoordinator.shared.configureAtLaunch()
    }
    UNUserNotificationCenter.current().delegate = IOSCompanionNotificationRouter.shared
    #endif

    let delegate = ReactNativeDelegate()
    let factory = RCTReactNativeFactory(delegate: delegate)
    #if BIRTHDAY_E2E
      // React codegen still knows the production module name, but its Objective-C
      // implementation is compiled out above this host. Avoid even evaluating
      // that generated mapping (and its missing-provider error) in the fixture.
      delegate.dependencyProvider = E2EReactNativeDependencyProvider()
    #else
      delegate.dependencyProvider = RCTAppDependencyProvider()
    #endif

    reactNativeDelegate = delegate
    reactNativeFactory = factory
    reactNativeLaunchOptions = launchOptions

    #if !BIRTHDAY_E2E && !BIRTHDAY_SMOKE
      // Reconcile only already-approved, generic local reminders. This never
      // requests notification authorization and never presents MessageUI.
      CompanionLifecycleCoordinator.shared.start()
    #endif

    return true
  }

  func application(
    _ application: UIApplication,
    configurationForConnecting connectingSceneSession: UISceneSession,
    options: UIScene.ConnectionOptions
  ) -> UISceneConfiguration {
    UISceneConfiguration(
      name: "Default Configuration",
      sessionRole: connectingSceneSession.role
    )
  }

  func application(
    _ app: UIApplication,
    open url: URL,
    options: [UIApplication.OpenURLOptionsKey: Any] = [:]
  ) -> Bool {
    #if BIRTHDAY_E2E || BIRTHDAY_SMOKE
      return false
    #else
      return IOSGoogleIdentityCoordinator.shared.handleOpenURL(url)
    #endif
  }

  func applicationDidBecomeActive(_ application: UIApplication) {
    #if !BIRTHDAY_E2E && !BIRTHDAY_SMOKE
      IOSGeminiSuggestionGateway.shared.refreshOperationalGateInBackground()
    #endif
  }

  func applicationProtectedDataDidBecomeAvailable(_ application: UIApplication) {
    #if !BIRTHDAY_E2E && !BIRTHDAY_SMOKE
      IOSGoogleIdentityCoordinator.shared
        .retryConfigurationAfterProtectedDataBecomesAvailable()
    #endif
  }

  var e2eInitialProperties: [String: Any]? {
    #if BIRTHDAY_E2E
      let defaults = UserDefaults.standard
      let language = defaults.string(forKey: "e2eLanguage") == "hi" ? "hi" : "en"
      return [
        "e2eLanguage": language,
        "e2ePlatform": "ios",
        "e2eRuntimeToken": "birthday-e2e-fixture-v1",
        "e2eSetupComplete": defaults.bool(forKey: "e2eSetupComplete"),
      ]
    #else
      return nil
    #endif
  }

  #if BIRTHDAY_E2E
    private func validateE2EHost() {
      precondition(
        Bundle.main.bundleIdentifier == "com.yashsomani.birthdayautopilot.e2e",
        "Invalid E2E bundle identifier"
      )
      precondition(
        Bundle.main.object(forInfoDictionaryKey: "BirthdayE2EFixture") as? Bool == true,
        "Missing E2E fixture marker"
      )
      #if !targetEnvironment(simulator)
        fatalError("The fixture host is simulator-only")
      #endif
    }
  #endif

  #if BIRTHDAY_SMOKE
    private func validateProductionSmokeHost() {
      precondition(
        Bundle.main.bundleIdentifier == "com.yashsomani.birthdayautopilot.smoke",
        "Invalid production-path smoke bundle identifier"
      )
      precondition(
        Bundle.main.object(forInfoDictionaryKey: "BirthdayProductionPathSmoke") as? Bool == true,
        "Missing production-path smoke marker"
      )
      #if !targetEnvironment(simulator)
        fatalError("The production-path smoke host is simulator-only")
      #endif
    }
  #endif
}

#if BIRTHDAY_E2E
  private final class E2EReactNativeDependencyProvider: RCTAppDependencyProvider {
    override func moduleProviders() -> [String: any RCTModuleProvider] {
      [:]
    }
  }
#endif

class ReactNativeDelegate: RCTDefaultReactNativeFactoryDelegate {
  override func sourceURL(for bridge: RCTBridge) -> URL? {
    self.bundleURL()
  }

  override func bundleURL() -> URL? {
    #if BIRTHDAY_E2E
      RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "e2e/index")
    #elseif DEBUG
      RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
    #else
      Bundle.main.url(forResource: "main", withExtension: "jsbundle")
    #endif
  }
}
