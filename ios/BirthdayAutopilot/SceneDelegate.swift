import UIKit

/// Owns the single foreground window. Core identity and MessageUI presenters intentionally
/// require exactly one active UIWindowScene, so scene adoption is a product-safety invariant,
/// not an optional lifecycle modernization.
final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
  var window: UIWindow?
  private var privacyCoverView: UIView?

  func scene(
    _ scene: UIScene,
    willConnectTo session: UISceneSession,
    options connectionOptions: UIScene.ConnectionOptions
  ) {
    guard let windowScene = scene as? UIWindowScene,
      let appDelegate = UIApplication.shared.delegate as? AppDelegate,
      let factory = appDelegate.reactNativeFactory
    else { return }

    let window = UIWindow(windowScene: windowScene)
    self.window = window
    #if BIRTHDAY_E2E
      factory.startReactNative(
        withModuleName: "BirthdayAutopilotE2E",
        in: window,
        initialProperties: appDelegate.e2eInitialProperties,
        launchOptions: appDelegate.reactNativeLaunchOptions
      )
    #else
      factory.startReactNative(
        withModuleName: "BirthdayAutopilot",
        in: window,
        launchOptions: appDelegate.reactNativeLaunchOptions
      )
    #endif

    // A cold Google callback is delivered in the scene connection options rather than through
    // UIApplicationDelegate once the scene lifecycle is enabled. Multiple callbacks are
    // ambiguous and are deliberately ignored.
    #if !BIRTHDAY_E2E && !BIRTHDAY_SMOKE
      if connectionOptions.urlContexts.count > 1 {
        IOSGoogleIdentityCoordinator.shared.rejectAmbiguousOpenURLs()
      } else if connectionOptions.urlContexts.count == 1,
        let url = connectionOptions.urlContexts.first?.url
      {
        _ = IOSGoogleIdentityCoordinator.shared.handleOpenURL(url)
      }
    #endif
  }

  func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
    #if BIRTHDAY_E2E || BIRTHDAY_SMOKE
      return
    #else
    guard URLContexts.count == 1, let url = URLContexts.first?.url else {
      if URLContexts.count > 1 {
        IOSGoogleIdentityCoordinator.shared.rejectAmbiguousOpenURLs()
      }
      return
    }
    _ = IOSGoogleIdentityCoordinator.shared.handleOpenURL(url)
    #endif
  }

  func sceneDidBecomeActive(_ scene: UIScene) {
    removePrivacyCover()
    #if !BIRTHDAY_E2E && !BIRTHDAY_SMOKE
      IOSGeminiSuggestionGateway.shared.refreshOperationalGateInBackground()
    #endif
  }

  func sceneWillResignActive(_ scene: UIScene) {
    installPrivacyCover()
  }

  func sceneDidEnterBackground(_ scene: UIScene) {
    #if !BIRTHDAY_E2E && !BIRTHDAY_SMOKE
      _ = IOSPeopleBackgroundRefreshCoordinator.shared.scheduleForConnectedSession()
    #endif
  }

  private func installPrivacyCover() {
    guard let window, privacyCoverView == nil else { return }
    // The system keyboard is hosted outside the app's ordinary view hierarchy,
    // so resign it before the app-switcher snapshot can be taken. The reviewed
    // MessageUI draft remains owned by the system composer.
    window.endEditing(true)
    let cover = UIView(frame: window.bounds)
    cover.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    cover.backgroundColor = .systemBackground
    cover.isAccessibilityElement = true
    cover.accessibilityLabel = "Birthday Autopilot"
    cover.accessibilityTraits = .staticText
    cover.accessibilityViewIsModal = true

    let label = UILabel()
    label.translatesAutoresizingMaskIntoConstraints = false
    label.text = "Birthday Autopilot"
    label.font = .preferredFont(forTextStyle: .title2)
    label.textColor = .label
    label.adjustsFontForContentSizeCategory = true
    label.isAccessibilityElement = false
    cover.addSubview(label)
    NSLayoutConstraint.activate([
      label.centerXAnchor.constraint(equalTo: cover.centerXAnchor),
      label.centerYAnchor.constraint(equalTo: cover.centerYAnchor),
      label.leadingAnchor.constraint(greaterThanOrEqualTo: cover.leadingAnchor, constant: 24),
      label.trailingAnchor.constraint(lessThanOrEqualTo: cover.trailingAnchor, constant: -24),
    ])
    window.addSubview(cover)
    privacyCoverView = cover
  }

  private func removePrivacyCover() {
    privacyCoverView?.removeFromSuperview()
    privacyCoverView = nil
  }
}
