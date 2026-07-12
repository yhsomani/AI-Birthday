import FirebaseCore
import FirebaseRemoteConfig
import Foundation

/// Native-only, fail-closed Remote Config gate for Firebase AI Logic.
///
/// Foreground requests read only the cached activated value. Refresh is
/// non-blocking and bounded; no config value, Firebase Installation identifier,
/// project/provider detail, or fetch result crosses React Native or a log.
@MainActor
final class IOSGeminiOperationalGate {
  static let shared = IOSGeminiOperationalGate()

  private var remoteConfig: RemoteConfig?
  private var cachedActivatedEnabled = IOSGeminiOperationalPolicy.inAppDefault
  private var configured = false
  private var fetchInFlight = false
  private var fetchGeneration: UInt64 = 0

  private init() {}

  func configureAfterFirebaseLaunch() {
    guard !configured, FirebaseApp.app() != nil else { return }
    let config = RemoteConfig.remoteConfig()
    let settings = RemoteConfigSettings()
    settings.minimumFetchInterval =
      IOSGeminiOperationalPolicy.minimumFetchIntervalSeconds
    settings.fetchTimeout = IOSGeminiOperationalPolicy.fetchTimeoutSeconds
    config.configSettings = settings
    config.setDefaults([
      IOSGeminiOperationalPolicy.parameterKey:
        NSNumber(value: IOSGeminiOperationalPolicy.inAppDefault),
    ])
    remoteConfig = config
    cachedActivatedEnabled = Self.readEnabled(config)
    configured = true
    refreshInBackground()
  }

  func foregroundSuggestionsEnabled() -> Bool {
    configured && cachedActivatedEnabled
  }

  func refreshInBackground() {
    guard configured, !fetchInFlight, let config = remoteConfig else { return }
    fetchInFlight = true
    fetchGeneration &+= 1
    let generation = fetchGeneration

    Task { @MainActor [weak self] in
      do {
        _ = try await config.fetchAndActivate()
        self?.finishRefresh(
          generation: generation,
          config: config,
          fetchedOrActivated: true
        )
      } catch {
        self?.finishRefresh(
          generation: generation,
          config: config,
          fetchedOrActivated: false
        )
      }
    }

    DispatchQueue.main.asyncAfter(
      deadline: .now() + IOSGeminiOperationalPolicy.localCompletionTimeoutSeconds
    ) { [weak self] in
      guard let self, self.fetchInFlight,
        self.fetchGeneration == generation
      else { return }
      // Invalidate any late completion. The SDK request itself also carries the
      // shorter fetchTimeout above; the extra fence bounds app-owned state.
      self.fetchGeneration &+= 1
      self.fetchInFlight = false
    }
  }

  private func finishRefresh(
    generation: UInt64,
    config: RemoteConfig,
    fetchedOrActivated: Bool
  ) {
    guard fetchInFlight, fetchGeneration == generation,
      remoteConfig === config
    else { return }
    if fetchedOrActivated {
      cachedActivatedEnabled = Self.readEnabled(config)
    }
    fetchInFlight = false
  }

  private static func readEnabled(_ config: RemoteConfig) -> Bool {
    let value = config.configValue(
      forKey: IOSGeminiOperationalPolicy.parameterKey
    )
    return IOSGeminiOperationalPolicy.acceptsActivatedValue(
      sourceIsRemote: value.source == .remote,
      canonicalString: value.stringValue,
      boolValue: value.boolValue
    )
  }
}
