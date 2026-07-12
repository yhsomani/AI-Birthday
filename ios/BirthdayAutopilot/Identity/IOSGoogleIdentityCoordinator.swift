import FirebaseAppCheck
import FirebaseAuth
import FirebaseCore
import Foundation
import GoogleSignIn
import UIKit

enum IOSGoogleIdentityFailure: Error, Equatable {
  case accountMismatch
  case appCheckUnavailable
  case cancelled
  case configurationUnavailable
  case firebaseUserDisabled
  case internalFailure
  case networkOffline
  case presenterUnavailable
  case reconnectRequired
}

enum IOSGoogleIdentityOutcome {
  case connected(displayEmail: String)
  case failed(IOSGoogleIdentityFailure)
}

enum IOSAccountDeletionRecoveryIdentityOutcome {
  case submitted
  case unavailable
  case failed(IOSGoogleIdentityFailure)
}

enum IOSGoogleSafeIdentityState: Equatable {
  case connecting
  case connected(displayEmail: String)
  case reconnectRequired
  case signedOut(retainedSetup: Bool)
  case unavailable
}

private enum IOSGoogleIdentityOperation: Equatable {
  case deletionReceiptLookup
  case deletionRecovery
  case ordinarySignIn
  case recentAuthentication
  case restoration
}

/// Owns Google Sign-In, Firebase Auth exchange, subject binding, and App Check.
/// Provider objects and credentials never leave this native coordinator.
@MainActor
final class IOSGoogleIdentityCoordinator {
  static let shared = IOSGoogleIdentityCoordinator()

  private(set) var state: IOSGoogleSafeIdentityState = .connecting
  private var configuration: IOSGoogleRuntimeConfiguration?
  private var configured = false
  private var protectedDataRaceRetryUsed = false
  private var googleIdentityAppCheckReady = false
  private var pendingOpenURL: URL?
  private var rejectedAmbiguousPendingOpenURL = false
  private var identityOperationInFlight: IOSGoogleIdentityOperation?
  private var identitySafetyInterlock = false
  private var deletionRecoverySignInRunning: Bool {
    identityOperationInFlight == .deletionRecovery
  }
  private var deletionReceiptLookupInFlight: Bool {
    identityOperationInFlight == .deletionReceiptLookup
  }
  private let peopleStore = CompanionPeopleStore.shared
  private let companionStore = CompanionProtectedStore.shared

  private init() {}

  func configureAtLaunch() {
    guard !configured else { return }
    configured = true
    peopleStore.prepareAtLaunch { [weak self] preparation in
      guard let self else { return }
      switch preparation {
      case .protectedDataUnavailable:
        // A complete-protection/keychain lock is temporary and is not a
        // configuration verdict. Permit the protected-data lifecycle callback
        // to retry without ever touching the encrypted People store.
        self.configured = false
        if UIApplication.shared.isProtectedDataAvailable,
          !self.protectedDataRaceRetryUsed
        {
          // Covers unlock racing the asynchronous store read before the system
          // notification reaches AppDelegate. Bound to one self-retry.
          self.protectedDataRaceRetryUsed = true
          DispatchQueue.main.async { [weak self] in self?.configureAtLaunch() }
        }
        return
      case .unavailable:
        self.transition(.unavailable)
        return
      case .ready:
        self.protectedDataRaceRetryUsed = false
      }
      guard case .ready(let configuration) = IOSGoogleConfigurationResolver.resolve() else {
        self.transition(
          .signedOut(retainedSetup: self.peopleStore.currentBinding() != nil)
        )
        return
      }
      // This coordinator is the sole default-Firebase owner. Reusing an
      // unexpectedly preconfigured app could cross tiers; calling configure a
      // second time can also raise an Objective-C exception, so fail closed.
      guard FirebaseApp.app() == nil else {
        self.transition(.unavailable)
        return
      }
      AppCheck.setAppCheckProviderFactory(BirthdayAppAttestProviderFactory())
      FirebaseApp.configure(options: configuration.firebaseOptions)
      self.configuration = configuration
      IOSGeminiSuggestionGateway.shared
        .configureOperationalGateAfterFirebaseLaunch()
      GIDSignIn.sharedInstance.configuration = GIDConfiguration(
        clientID: configuration.googleClientID
      )
      GIDSignIn.sharedInstance.configure { [weak self] error in
        guard let self else { return }
        self.googleIdentityAppCheckReady = error == nil
        guard error == nil else {
          self.transition(.unavailable)
          return
        }
        if self.accountDeletionStateBlocksOrdinaryIdentity() {
          self.clearPendingOpenURL()
          let localDataErased = IOSAccountDeletionReceiptStore.shared.current()?
            .localDataErased == true
          guard self.signOutDeletionRecoverySession() else {
            self.transition(.unavailable)
            return
          }
          self.transition(
            localDataErased ? .signedOut(retainedSetup: false) : .reconnectRequired
          )
          // Lifecycle startup may have observed the receipt before Firebase was
          // configured. Resume only after both SDKs are ready and signed out.
          IOSAccountDeletionLocalCleanupCoordinator.shared.resumeIfNeeded()
          return
        }
        _ = self.consumePendingOpenURL(expectedScheme: configuration.reversedClientID)
        Task { @MainActor in await self.restorePreviousSession() }
      }
    }
  }

  func retryConfigurationAfterProtectedDataBecomesAvailable() {
    protectedDataRaceRetryUsed = false
    configureAtLaunch()
  }

  func handleOpenURL(_ url: URL) -> Bool {
    guard isExpectedCallbackURL(url), !deletionReceiptLookupInFlight else {
      clearPendingOpenURL()
      return false
    }
    if accountDeletionStateBlocksOrdinaryIdentity() {
      guard deletionRecoverySignInRunning,
        IOSAccountDeletionRecoveryStore.shared.retryAuthorizedOperationId() != nil,
        configuration != nil
      else {
        clearPendingOpenURL()
        return false
      }
    }
    guard configuration != nil, googleIdentityAppCheckReady else {
      guard case .connecting = state, !rejectedAmbiguousPendingOpenURL else {
        clearPendingOpenURL()
        return false
      }
      guard pendingOpenURL == nil else {
        // More than one cold callback is ambiguous. Drop both and refuse any
        // later replay during this launch rather than guessing which is valid.
        pendingOpenURL = nil
        rejectedAmbiguousPendingOpenURL = true
        return false
      }
      pendingOpenURL = url
      return true
    }
    return GIDSignIn.sharedInstance.handle(url)
  }

  func rejectAmbiguousOpenURLs() {
    pendingOpenURL = nil
    rejectedAmbiguousPendingOpenURL = true
  }

  func continueWithGoogle(
    completion: @escaping (IOSGoogleIdentityOutcome) -> Void
  ) {
    guard identityOperationInFlight == nil,
      canBeginOrdinaryGoogleSelection()
    else {
      completion(.failed(.reconnectRequired))
      return
    }
    guard configuration != nil, googleIdentityAppCheckReady else {
      completion(.failed(.configurationUnavailable))
      return
    }
    guard let presenter = Self.foregroundPresenter() else {
      completion(.failed(.presenterUnavailable))
      return
    }
    guard acquireIdentityOperation(.ordinarySignIn) else {
      completion(.failed(.reconnectRequired))
      return
    }
    IOSGeminiSuggestionGateway.shared.clearProvenance()
    transition(.connecting)
    Task { @MainActor in
      defer { self.releaseIdentityOperation(.ordinarySignIn) }
      do {
        guard await firebaseAppCheckGate() else {
          throw IOSGoogleIdentityFailure.appCheckUnavailable
        }
        let result = try await GIDSignIn.sharedInstance.signIn(
          withPresenting: presenter,
          hint: peopleStore.currentBinding()?.displayEmail
        )
        guard result.serverAuthCode == nil else {
          throw IOSGoogleIdentityFailure.configurationUnavailable
        }
        // Keep a still-useful completed proof if configuration, App Check, the
        // presenter, or the chooser fails. Once a Google account is selected,
        // retire the prior lifecycle before any Firebase credential exchange
        // or protected-store attachment can establish the next account.
        guard !self.deletionReceiptLookupInFlight,
          IOSAccountDeletionReceiptStore.shared
            .retireCompletedReceiptBeforeNewIdentity()
        else {
          guard self.signOutDeletionRecoverySession() else {
            throw IOSGoogleIdentityFailure.internalFailure
          }
          throw IOSGoogleIdentityFailure.reconnectRequired
        }
        let outcome = await attachGoogleUser(result.user)
        completion(outcome)
      } catch let failure as IOSGoogleIdentityFailure {
        restoreSafeSignedOutState(after: failure)
        completion(.failed(failure))
      } catch {
        let failure = Self.mapGoogleError(error)
        restoreSafeSignedOutState(after: failure)
        completion(.failed(failure))
      }
    }
  }

  func exactSessionBinding() -> IOSNativeGoogleAccountBinding? {
    guard !accountDeletionStateBlocksOrdinaryIdentity(),
      case .connected = state,
      let stored = peopleStore.currentBinding(),
      Self.hasExactLiveProviderSession(stored)
    else {
      return nil
    }
    return stored
  }

  /// Performs the only sign-in allowed after a reviewed ambiguous-deletion
  /// wipe. The selected account is compared with the salted equality-only
  /// native journal, used for one idempotent replay, and signed out again.
  /// Neither account identifiers nor the raw receipt cross React Native.
  func continueAccountDeletionRecoveryWithGoogle(
    completion: @escaping (IOSAccountDeletionRecoveryIdentityOutcome) -> Void
  ) {
    guard identityOperationInFlight == nil,
      let operationId = IOSAccountDeletionRecoveryStore.shared
        .retryAuthorizedOperationId(),
      let receipt = IOSAccountDeletionReceiptStore.shared.current(),
      receipt.operationId == operationId,
      receipt.localDataErased,
      !receipt.remoteDeletionComplete
    else {
      completion(.failed(.reconnectRequired))
      return
    }
    guard configuration != nil, googleIdentityAppCheckReady else {
      completion(.failed(.configurationUnavailable))
      return
    }
    guard let presenter = Self.foregroundPresenter() else {
      completion(.failed(.presenterUnavailable))
      return
    }
    guard acquireIdentityOperation(.deletionRecovery) else {
      completion(.failed(.reconnectRequired))
      return
    }

    IOSGeminiSuggestionGateway.shared.clearLocalDataForAccountDeletion()
    transition(.connecting)
    Task { @MainActor in
      defer { self.releaseIdentityOperation(.deletionRecovery) }
      do {
        guard await self.firebaseAppCheckGate() else {
          throw IOSGoogleIdentityFailure.appCheckUnavailable
        }
        let signIn = try await GIDSignIn.sharedInstance.signIn(
          withPresenting: presenter
        )
        guard signIn.serverAuthCode == nil else {
          throw IOSGoogleIdentityFailure.configurationUnavailable
        }
        let googleUser = try await signIn.user.refreshTokensIfNeeded()
        guard
          let subject = IOSPeopleValuePolicy.googleSubject(googleUser.userID),
          let email = IOSPeopleValuePolicy.safeEmail(googleUser.profile?.email),
          let idToken = googleUser.idToken?.tokenString,
          let accessToken = IOSEphemeralGoogleAccessToken(
            googleUser.accessToken.tokenString
          ),
          Self.allowedIdentityScopes(googleUser.grantedScopes)
        else { throw IOSGoogleIdentityFailure.reconnectRequired }
        guard IOSAccountDeletionRecoveryStore.shared.matchesRetryGoogleSubject(
          operationId: operationId,
          googleSubject: subject
        ) else {
          // Reject a different chooser subject before Firebase Auth can create
          // a replacement user for it.
          throw IOSGoogleIdentityFailure.accountMismatch
        }
        defer { accessToken.clear() }
        let credential = accessToken.use { token in
          GoogleAuthProvider.credential(withIDToken: idToken, accessToken: token)
        }
        let firebaseResult: AuthDataResult
        do {
          firebaseResult = try await Auth.auth().signIn(with: credential)
        } catch {
          throw Self.mapFirebaseError(error)
        }
        if firebaseResult.additionalUserInfo?.isNewUser == true {
          // Inspect creation before trusting provider/profile metadata. Even a
          // malformed fresh result represents an app-created Auth user and must
          // be deleted under its fresh credential rather than merely signed out.
          _ = await Self.deleteReplacementFirebaseUser(firebaseResult.user)
          guard self.signOutDeletionRecoverySession() else {
            self.transition(.unavailable)
            completion(.failed(.internalFailure))
            return
          }
          self.transition(.signedOut(retainedSetup: false))
          completion(.unavailable)
          return
        }
        guard !firebaseResult.user.isAnonymous,
          IOSPeopleValuePolicy.providerIdentifier(
            firebaseResult.user.uid,
            maximumBytes: 256
          ),
          let provider = firebaseResult.user.providerData.first(where: {
            $0.providerID == "google.com"
          }),
          provider.uid == subject,
          IOSPeopleValuePolicy.safeEmail(provider.email)?.caseInsensitiveCompare(email)
            == .orderedSame
        else { throw IOSGoogleIdentityFailure.accountMismatch }

        let uidMatchesOriginal = IOSAccountDeletionRecoveryStore.shared
          .matchesRetryFirebaseUID(
            operationId: operationId,
            firebaseUID: firebaseResult.user.uid
          )
        if !uidMatchesOriginal {
          // `isNewUser` was not true, so this may be an existing Firebase user
          // that the app did not create. A UID mismatch alone is never deletion
          // authority. Do not replay or attach it; only sign both SDKs out and
          // leave the private recovery evidence for the support/external path.
          // Retrying deletion of a prior app-created replacement would require
          // a separately durable exact replacement-UID proof.
          guard self.signOutDeletionRecoverySession() else {
            self.transition(.unavailable)
            completion(.failed(.internalFailure))
            return
          }
          self.transition(.signedOut(retainedSetup: false))
          // The original UID is unavailable for an authenticated replay. Keep
          // the journal and route the user to support/external deletion.
          completion(.unavailable)
          return
        }

        let ephemeralBinding = IOSNativeGoogleAccountBinding(
          googleSubject: subject,
          firebaseUID: firebaseResult.user.uid,
          displayEmail: email,
          displayName: IOSPeopleValuePolicy.safeDisplayName(
            googleUser.profile?.name
          ),
          accountGeneration: UUID().uuidString.lowercased()
        )
        guard IOSAccountDeletionRecoveryStore.shared.matchesRetryAccount(
          operationId: operationId,
          binding: ephemeralBinding
        ) else { throw IOSGoogleIdentityFailure.accountMismatch }

        _ = try await firebaseResult.user.getIDTokenResult(forcingRefresh: true)
        let replay: Result<
          IOSAccountDeletionAcceptance,
          IOSAccountDeletionFailure
        > = await withCheckedContinuation { continuation in
          IOSAccountDeletionClient.shared.startOrReplay(
            binding: ephemeralBinding,
            requestId: operationId
          ) { continuation.resume(returning: $0) }
        }
        guard case .success(let acceptance) = replay,
          acceptance.receiptId == operationId
        else {
          if case .failure(let failure) = replay {
            throw Self.recoveryIdentityFailure(failure)
          }
          throw IOSGoogleIdentityFailure.internalFailure
        }
        guard self.signOutDeletionRecoverySession() else {
          throw IOSGoogleIdentityFailure.internalFailure
        }
        let acceptancePersisted = await withCheckedContinuation { continuation in
          IOSAccountDeletionRecoveryStore.shared.markRemoteAcceptanceConfirmed(
            operationId: operationId
          ) { continuation.resume(returning: $0) }
        }
        guard acceptancePersisted else {
          throw IOSGoogleIdentityFailure.internalFailure
        }
        self.transition(.signedOut(retainedSetup: false))
        completion(.submitted)
      } catch let failure as IOSGoogleIdentityFailure {
        _ = self.signOutDeletionRecoverySession()
        self.transition(.signedOut(retainedSetup: false))
        completion(.failed(failure))
      } catch {
        let failure = Self.mapGoogleError(error)
        _ = self.signOutDeletionRecoverySession()
        self.transition(.signedOut(retainedSetup: false))
        completion(.failed(failure))
      }
    }
  }

  /// Ensures the callable will carry a recent Firebase auth_time. If the
  /// existing token is not recent enough, foreground Google Sign-In is used to
  /// reauthenticate the same exact provider subject and Firebase UID. Tokens
  /// remain stack-local and never cross the native boundary.
  func ensureRecentExactGoogleAuthentication(
    binding: IOSNativeGoogleAccountBinding
  ) async -> Result<Void, IOSGoogleIdentityFailure> {
    guard acquireIdentityOperation(.recentAuthentication) else {
      return .failure(.reconnectRequired)
    }
    defer { releaseIdentityOperation(.recentAuthentication) }
    guard configuration != nil, googleIdentityAppCheckReady,
      exactSessionBinding() == binding,
      let firebaseUser = Auth.auth().currentUser,
      firebaseUser.uid == binding.firebaseUID
    else { return .failure(.reconnectRequired) }
    guard await firebaseAppCheckGate() else {
      return .failure(.appCheckUnavailable)
    }

    if let token = try? await firebaseUser.getIDTokenResult(forcingRefresh: false) {
      let age = Date().timeIntervalSince(token.authDate)
      if age >= -60, age <= 4 * 60 {
        return .success(())
      }
    }

    guard let presenter = Self.foregroundPresenter() else {
      return .failure(.presenterUnavailable)
    }
    do {
      let result = try await GIDSignIn.sharedInstance.signIn(
        withPresenting: presenter,
        hint: binding.displayEmail
      )
      guard result.serverAuthCode == nil,
        let subject = IOSPeopleValuePolicy.googleSubject(result.user.userID),
        subject == binding.googleSubject,
        let email = IOSPeopleValuePolicy.safeEmail(result.user.profile?.email),
        email.caseInsensitiveCompare(binding.displayEmail) == .orderedSame,
        Self.allowedIdentityScopes(result.user.grantedScopes)
      else {
        GIDSignIn.sharedInstance.signOut()
        transition(.reconnectRequired)
        return .failure(.accountMismatch)
      }
      let user = try await result.user.refreshTokensIfNeeded()
      guard let idToken = user.idToken?.tokenString else {
        return .failure(.reconnectRequired)
      }
      let accessToken = IOSEphemeralGoogleAccessToken(user.accessToken.tokenString)
      defer { accessToken.clear() }
      let credential = accessToken.use { token in
        GoogleAuthProvider.credential(withIDToken: idToken, accessToken: token)
      }
      let reauthenticated = try await firebaseUser.reauthenticate(with: credential)
      guard reauthenticated.user.uid == binding.firebaseUID,
        reauthenticated.user.providerData.contains(where: {
          $0.providerID == "google.com" && $0.uid == binding.googleSubject
        }), Self.hasExactLiveProviderSession(binding)
      else {
        transition(.reconnectRequired)
        return .failure(.accountMismatch)
      }
      _ = try await reauthenticated.user.getIDTokenResult(forcingRefresh: true)
      return .success(())
    } catch {
      let failure = (error as? IOSGoogleIdentityFailure) ?? Self.mapGoogleError(error)
      if failure == .accountMismatch || failure == .reconnectRequired {
        transition(.reconnectRequired)
      }
      return .failure(failure)
    }
  }

  func appCheckGate(completion: @escaping (Bool) -> Void) {
    Task { @MainActor in completion(await firebaseAppCheckGate()) }
  }

  /// A People 401 cannot be repaired by replaying `refreshTokensIfNeeded`
  /// because GoogleSignIn-iOS has no supported forced refresh for an otherwise
  /// unexpired access token. Retire the official Google session, invalidate all
  /// protected review capability, and require a new foreground Google flow.
  func requirePeopleReconnectAfterUnauthorized(
    expectedBinding: IOSNativeGoogleAccountBinding
  ) {
    guard peopleStore.currentBinding().map({
      $0.hasSameOwner(as: expectedBinding)
        && $0.accountGeneration == expectedBinding.accountGeneration
    }) == true else { return }
    identitySafetyInterlock = true
    IOSGeminiSuggestionGateway.shared.clearProvenance()
    GIDSignIn.sharedInstance.signOut()
    transition(.reconnectRequired)
    companionStore.invalidateAccountSession { [weak self] result in
      guard let self else { return }
      guard case .success = result else {
        self.transition(.unavailable)
        return
      }
      self.companionStore.verifyAccountSessionInvalidated { [weak self] verified in
        guard let self else { return }
        self.identitySafetyInterlock = !verified
        if !verified { self.transition(.unavailable) }
      }
    }
  }

  func foregroundViewController() -> UIViewController? {
    Self.foregroundPresenter()
  }

  /// Receipt lookup is intentionally unauthenticated and App Check-only. It is
  /// permitted only after both official identity SDKs prove that no account
  /// session remains; failure stays unavailable and cannot authorize replay.
  func beginSignedOutDeletionReceiptLookup() -> Bool {
    guard configuration != nil, googleIdentityAppCheckReady,
      FirebaseApp.app() != nil
    else { return false }
    guard acquireIdentityOperation(.deletionReceiptLookup) else {
      return false
    }
    let signedOut = signOutDeletionRecoverySession()
    guard signedOut else {
      releaseIdentityOperation(.deletionReceiptLookup)
      transition(.unavailable)
      return false
    }
    transition(.signedOut(retainedSetup: false))
    return true
  }

  func finishSignedOutDeletionReceiptLookup() {
    releaseIdentityOperation(.deletionReceiptLookup)
  }

  func deletionSDKSessionIsAbsent() -> Bool {
    guard FirebaseApp.app() != nil else { return false }
    return Auth.auth().currentUser == nil
      && GIDSignIn.sharedInstance.currentUser == nil
  }

  func completeSignOutAfterSafetyShutdown(retainData: Bool) async -> Bool {
    IOSGeminiSuggestionGateway.shared.clearProvenance()
    let firebaseSignOutSucceeded: Bool
    if FirebaseApp.app() == nil {
      // With no configured Firebase app there cannot be an Auth session to
      // retain. Local People and companion cleanup still has to run.
      firebaseSignOutSucceeded = true
    } else {
      do {
        try Auth.auth().signOut()
        firebaseSignOutSucceeded = true
      } catch {
        // Local deletion must not depend on SDK sign-out success. The failure is
        // retained below and forces the identity interlock after every local
        // cleanup attempt has run.
        firebaseSignOutSucceeded = false
      }
    }
    GIDSignIn.sharedInstance.signOut()

    let peopleCleanupSucceeded: Bool
    if retainData {
      peopleStore.signOutRetainingData()
      peopleCleanupSucceeded = true
    } else {
      peopleCleanupSucceeded = await withCheckedContinuation { continuation in
        peopleStore.wipe { continuation.resume(returning: $0) }
      }
    }
    let companionSessionInvalidated = await invalidateCompanionAccountSession()
    let firebaseSessionAbsent = FirebaseApp.app() == nil
      || Auth.auth().currentUser == nil
    let sdkSessionsAbsent = firebaseSessionAbsent
      && GIDSignIn.sharedInstance.currentUser == nil
    guard firebaseSignOutSucceeded, sdkSessionsAbsent,
      peopleCleanupSucceeded, companionSessionInvalidated
    else {
      enterIdentitySafetyInterlock()
      return false
    }
    identitySafetyInterlock = false
    transition(.signedOut(retainedSetup: retainData))
    return true
  }

  /// Performs only the official Google provider revocation. Contact payloads
  /// and reminders are already gone before this method is called. Keeping this
  /// boundary separate lets the workflow durably record provider success
  /// before attempting fallible Firebase/SDK session cleanup.
  func disconnectGoogleProviderAfterLocalCleanup() async -> Bool {
    guard configuration != nil else { return false }
    IOSGeminiSuggestionGateway.shared.clearProvenance()
    do {
      try await GIDSignIn.sharedInstance.disconnect()
      return true
    } catch {
      return false
    }
  }

  /// Finishes local SDK cleanup only after the workflow has durably marked the
  /// provider disconnect and appended CONTACTS_READONLY/REVOKED. It is safe to
  /// retry after restart and never touches the already-cleared People payload.
  func finishRevokedGoogleSDKCleanupAfterLocalCleanup() async -> Bool {
    IOSGeminiSuggestionGateway.shared.clearProvenance()
    let firebaseSignOutSucceeded: Bool
    if FirebaseApp.app() == nil {
      firebaseSignOutSucceeded = true
    } else {
      do {
        try Auth.auth().signOut()
        firebaseSignOutSucceeded = true
      } catch {
        firebaseSignOutSucceeded = false
      }
    }
    GIDSignIn.sharedInstance.signOut()
    let companionSessionInvalidated = await invalidateCompanionAccountSession()
    let firebaseSessionAbsent = FirebaseApp.app() == nil
      || Auth.auth().currentUser == nil
    let sdkSessionsAbsent = firebaseSessionAbsent
      && GIDSignIn.sharedInstance.currentUser == nil
    guard firebaseSignOutSucceeded, sdkSessionsAbsent,
      companionSessionInvalidated
    else {
      enterIdentitySafetyInterlock()
      return false
    }
    identitySafetyInterlock = false
    transition(.signedOut(retainedSetup: true))
    return true
  }

  /// Clears official provider state and destroys the People database/key after
  /// backend acceptance or a separately reviewed ambiguous-acceptance local
  /// wipe. The companion store is destroyed after notification/composer shutdown.
  func completeAccountDeletionLocalShutdown() async -> Bool {
    guard configuration != nil else { return false }
    guard await invalidatePeopleSyncFence() else { return false }
    IOSGeminiSuggestionGateway.shared.clearLocalDataForAccountDeletion()
    do {
      try Auth.auth().signOut()
    } catch {
      return false
    }
    GIDSignIn.sharedInstance.signOut()
    guard Auth.auth().currentUser == nil,
      GIDSignIn.sharedInstance.currentUser == nil
    else { return false }
    let destroyed = await withCheckedContinuation { continuation in
      peopleStore.destroyAfterRemoteAccountDeletion {
        continuation.resume(returning: $0)
      }
    }
    guard destroyed, Auth.auth().currentUser == nil,
      GIDSignIn.sharedInstance.currentUser == nil
    else { return false }
    transition(.signedOut(retainedSetup: false))
    return true
  }

  /// Erases both local encrypted stores while keeping the already-proven live
  /// Google/Firebase session. The same exact subject/UID/generation is rebound
  /// into an empty setup; this action never signs the user out or changes owner.
  func wipeLocalDataAfterSafetyShutdown(
    binding: IOSNativeGoogleAccountBinding
  ) async -> Bool {
    guard exactSessionBinding() == binding else { return false }
    guard await invalidatePeopleSyncFence() else {
      transition(.reconnectRequired)
      return false
    }
    IOSGeminiSuggestionGateway.shared.clearProvenance()
    let peopleWiped = await withCheckedContinuation { continuation in
      peopleStore.wipe { continuation.resume(returning: $0) }
    }
    guard peopleWiped, Self.hasExactLiveProviderSession(binding) else {
      transition(.reconnectRequired)
      return false
    }
    let attached = await withCheckedContinuation { continuation in
      peopleStore.attach(
        binding,
        retainedCompanionSetupExists: false
      ) { continuation.resume(returning: $0) }
    }
    guard case .attached = attached else {
      transition(.reconnectRequired)
      return false
    }
    let workflowBound = await withCheckedContinuation { continuation in
      companionStore.bindWorkflowAccount(binding) { result in
        continuation.resume(returning: (try? result.get()) != nil)
      }
    }
    guard workflowBound else {
      transition(.reconnectRequired)
      return false
    }
    companionStore.markExternalProjectionChanged()
    return true
  }

  private func restorePreviousSession() async {
    guard acquireIdentityOperation(.restoration) else { return }
    defer { releaseIdentityOperation(.restoration) }
    let retained = peopleStore.currentBinding() != nil
    guard retained else {
      // Official SDK keychain state can survive an app reinstall. Never use it to
      // silently recreate app-owned setup after the protected binding is absent.
      try? Auth.auth().signOut()
      GIDSignIn.sharedInstance.signOut()
      IOSGeminiSuggestionGateway.shared.clearProvenance()
      transition(.signedOut(retainedSetup: false))
      return
    }
    do {
      guard await firebaseAppCheckGate() else {
        throw IOSGoogleIdentityFailure.appCheckUnavailable
      }
      let googleUser = try await GIDSignIn.sharedInstance.restorePreviousSignIn()
      _ = await attachGoogleUser(googleUser)
    } catch {
      if retained {
        transition(.reconnectRequired)
      } else {
        transition(.signedOut(retainedSetup: false))
      }
    }
  }

  private func attachGoogleUser(_ initialUser: GIDGoogleUser) async -> IOSGoogleIdentityOutcome {
    do {
      guard !deletionReceiptLookupInFlight,
        !accountDeletionStateBlocksOrdinaryIdentity()
      else {
        throw IOSGoogleIdentityFailure.reconnectRequired
      }
      let user = try await initialUser.refreshTokensIfNeeded()
      guard let subject = IOSPeopleValuePolicy.googleSubject(user.userID),
        let email = IOSPeopleValuePolicy.safeEmail(user.profile?.email),
        let idToken = user.idToken?.tokenString,
        let accessToken = IOSEphemeralGoogleAccessToken(user.accessToken.tokenString),
        Self.allowedIdentityScopes(user.grantedScopes)
      else {
        throw IOSGoogleIdentityFailure.reconnectRequired
      }
      defer { accessToken.clear() }
      let credential = accessToken.use { token in
        GoogleAuthProvider.credential(withIDToken: idToken, accessToken: token)
      }
      let result: AuthDataResult
      do {
        result = try await Auth.auth().signIn(with: credential)
      } catch {
        throw Self.mapFirebaseError(error)
      }
      let firebaseUserWasCreated = result.additionalUserInfo?.isNewUser == true
      guard !deletionReceiptLookupInFlight,
        !accountDeletionStateBlocksOrdinaryIdentity()
      else {
        guard await endFailedIdentitySession(
          result.user,
          deleteDefinitelyFreshUser: firebaseUserWasCreated
        ) else { throw IOSGoogleIdentityFailure.internalFailure }
        throw IOSGoogleIdentityFailure.reconnectRequired
      }
      guard !result.user.isAnonymous,
        let provider = result.user.providerData.first(where: { $0.providerID == "google.com" }),
        provider.uid == subject,
        IOSPeopleValuePolicy.safeEmail(provider.email)?.caseInsensitiveCompare(email)
          == .orderedSame,
        IOSPeopleValuePolicy.providerIdentifier(result.user.uid, maximumBytes: 256)
      else {
        guard await endFailedIdentitySession(
          result.user,
          deleteDefinitelyFreshUser: firebaseUserWasCreated
        ) else { throw IOSGoogleIdentityFailure.internalFailure }
        throw IOSGoogleIdentityFailure.accountMismatch
      }
      let displayName = IOSPeopleValuePolicy.safeDisplayName(user.profile?.name)
      let existing = peopleStore.currentBinding()
      let binding = IOSNativeGoogleAccountBinding(
        googleSubject: subject,
        firebaseUID: result.user.uid,
        displayEmail: email,
        displayName: displayName,
        accountGeneration: existing?.accountGeneration ?? UUID().uuidString.lowercased()
      )
      let retainedCompanionSetup = await withCheckedContinuation { continuation in
        companionStore.readProjectionStatus { result in
          continuation.resume(returning: (try? result.get().retainedSetupExists) ?? true)
        }
      }
      let attachResult = await withCheckedContinuation { continuation in
        peopleStore.attach(
          binding,
          retainedCompanionSetupExists: retainedCompanionSetup && existing == nil
        ) { continuation.resume(returning: $0) }
      }
      switch attachResult {
      case .attached:
        let workflowBound = await withCheckedContinuation { continuation in
          companionStore.bindWorkflowAccount(binding) { result in
            continuation.resume(returning: (try? result.get()) != nil)
          }
        }
        guard workflowBound else {
          // People attachment already committed, so preserve it for exact
          // reconnect and never delete Auth. Both SDK sessions must still end.
          guard await endFailedIdentitySession(
            result.user,
            deleteDefinitelyFreshUser: false
          ) else { throw IOSGoogleIdentityFailure.internalFailure }
          throw IOSGoogleIdentityFailure.accountMismatch
        }
        await withCheckedContinuation { continuation in
          IOSCompanionWorkflowEngine.shared.reconcileAfterPeopleSync(binding: binding) {
            continuation.resume()
          }
        }
        transition(.connected(displayEmail: email))
        return .connected(displayEmail: email)
      case .accountMismatch:
        guard await endFailedIdentitySession(
          result.user,
          deleteDefinitelyFreshUser: firebaseUserWasCreated
        ) else { throw IOSGoogleIdentityFailure.internalFailure }
        throw IOSGoogleIdentityFailure.accountMismatch
      case .storageFailure:
        let durableState = peopleStore.durableAttachmentState(for: binding)
        let mayDeleteFreshUser = durableState == .notAttached
        guard await endFailedIdentitySession(
          result.user,
          deleteDefinitelyFreshUser: firebaseUserWasCreated && mayDeleteFreshUser
        ) else { throw IOSGoogleIdentityFailure.internalFailure }
        throw IOSGoogleIdentityFailure.internalFailure
      }
    } catch let failure as IOSGoogleIdentityFailure {
      restoreSafeSignedOutState(after: failure)
      return .failed(failure)
    } catch {
      let failure = Self.mapGoogleError(error)
      restoreSafeSignedOutState(after: failure)
      return .failed(failure)
    }
  }

  private func firebaseAppCheckGate() async -> Bool {
    guard configuration != nil, googleIdentityAppCheckReady else { return false }
    do {
      _ = try await AppCheck.appCheck().token(forcingRefresh: false)
      return true
    } catch {
      return false
    }
  }

  private func restoreSafeSignedOutState(after failure: IOSGoogleIdentityFailure) {
    IOSGeminiSuggestionGateway.shared.clearProvenance()
    let retained = peopleStore.currentBinding() != nil
    switch failure {
    case .cancelled where !retained:
      transition(.signedOut(retainedSetup: false))
    case .configurationUnavailable where !retained:
      transition(.signedOut(retainedSetup: false))
    default:
      transition(retained ? .reconnectRequired : .signedOut(retainedSetup: false))
    }
  }

  private func transition(_ next: IOSGoogleSafeIdentityState) {
    guard state != next else { return }
    state = next
    switch next {
    case .connected:
      _ = IOSPeopleBackgroundRefreshCoordinator.shared
        .scheduleForConnectedSession()
    case .connecting:
      break
    case .reconnectRequired, .signedOut, .unavailable:
      clearPendingOpenURL()
      IOSPeopleBackgroundRefreshCoordinator.shared
        .cancelForDisconnectedSession()
    }
    companionStore.markExternalProjectionChanged()
  }

  private func isExpectedCallbackURL(_ url: URL) -> Bool {
    guard let scheme = url.scheme,
      let expected = configuration?.reversedClientID
        ?? IOSGoogleConfigurationResolver.declaredCallbackScheme()
    else { return false }
    return scheme.caseInsensitiveCompare(expected) == .orderedSame
  }

  private func consumePendingOpenURL(expectedScheme: String) -> Bool {
    guard !rejectedAmbiguousPendingOpenURL, let url = pendingOpenURL else {
      clearPendingOpenURL()
      return false
    }
    clearPendingOpenURL()
    guard url.scheme?.caseInsensitiveCompare(expectedScheme) == .orderedSame,
      !deletionReceiptLookupInFlight,
      !accountDeletionStateBlocksOrdinaryIdentity()
    else { return false }
    return GIDSignIn.sharedInstance.handle(url)
  }

  private func clearPendingOpenURL() {
    pendingOpenURL = nil
  }

  private func acquireIdentityOperation(_ operation: IOSGoogleIdentityOperation) -> Bool {
    guard identityOperationInFlight == nil else { return false }
    identityOperationInFlight = operation
    return true
  }

  private func releaseIdentityOperation(_ operation: IOSGoogleIdentityOperation) {
    guard identityOperationInFlight == operation else { return }
    identityOperationInFlight = nil
  }

  private func accountDeletionStateBlocksOrdinaryIdentity() -> Bool {
    IOSAccountDeletionReceiptStore.shared.hasPendingOrUnreadableReceipt()
      || IOSAccountDeletionRecoveryStore.shared.hasPendingOrUnreadableJournal()
  }

  /// A completed receipt may coexist briefly with its exact recovery journal
  /// after a crash between durable completion and journal retirement. The
  /// chooser may open in that state, but the pair must retire atomically in the
  /// safe order before Firebase Auth is called. Every other pending or
  /// unreadable deletion state blocks even account selection.
  private func canBeginOrdinaryGoogleSelection() -> Bool {
    guard !identitySafetyInterlock else { return false }
    let receiptStore = IOSAccountDeletionReceiptStore.shared
    guard !receiptStore.hasPendingOrUnreadableReceipt() else { return false }
    guard IOSAccountDeletionRecoveryStore.shared.hasPendingOrUnreadableJournal() else {
      return true
    }
    return receiptStore.current()?.remoteDeletionComplete == true
  }

  private func signOutDeletionRecoverySession() -> Bool {
    IOSGeminiSuggestionGateway.shared.clearLocalDataForAccountDeletion()
    guard FirebaseApp.app() != nil else {
      GIDSignIn.sharedInstance.signOut()
      return false
    }
    do {
      try Auth.auth().signOut()
      GIDSignIn.sharedInstance.signOut()
      return Auth.auth().currentUser == nil
        && GIDSignIn.sharedInstance.currentUser == nil
    } catch {
      GIDSignIn.sharedInstance.signOut()
      return false
    }
  }

  private func invalidatePeopleSyncFence() async -> Bool {
    await withCheckedContinuation { continuation in
      peopleStore.invalidateOutstandingSync {
        continuation.resume(returning: $0)
      }
    }
  }

  private func invalidateCompanionAccountSession() async -> Bool {
    let invalidated = await withCheckedContinuation { continuation in
      companionStore.invalidateAccountSession { result in
        continuation.resume(returning: (try? result.get()) != nil)
      }
    }
    guard invalidated else { return false }
    return await withCheckedContinuation { continuation in
      companionStore.verifyAccountSessionInvalidated {
        continuation.resume(returning: $0)
      }
    }
  }

  private func enterIdentitySafetyInterlock() {
    identitySafetyInterlock = true
    transition(.unavailable)
  }

  /// Ends a failed ordinary identity exchange. Automatic Auth deletion is
  /// permitted only when Firebase explicitly reported that this exact sign-in
  /// created the user and durable attachment resolution has ruled out a
  /// committed People binding. Every path proves both SDK sessions absent;
  /// post-attachment failures pass false and preserve the durable binding.
  private func endFailedIdentitySession(
    _ user: User,
    deleteDefinitelyFreshUser: Bool
  ) async -> Bool {
    let deletionSucceeded: Bool
    if deleteDefinitelyFreshUser {
      deletionSucceeded = await Self.deleteReplacementFirebaseUser(user)
    } else {
      deletionSucceeded = true
    }
    try? Auth.auth().signOut()
    GIDSignIn.sharedInstance.signOut()
    return deletionSucceeded && Auth.auth().currentUser == nil
      && GIDSignIn.sharedInstance.currentUser == nil
  }

  private static func deleteReplacementFirebaseUser(_ user: User) async -> Bool {
    for _ in 0..<2 {
      do {
        try await user.delete()
        if Auth.auth().currentUser == nil { return true }
      } catch {
        continue
      }
    }
    return false
  }

  private static func allowedIdentityScopes(_ raw: [String]?) -> Bool {
    let scopes = Set((raw ?? []).map { $0.trimmingCharacters(in: .whitespacesAndNewlines) })
    let forbidden = "https://www.googleapis.com/auth/contacts"
    guard !scopes.contains(forbidden) else { return false }
    let allowed = Set([
      "email", "openid", "profile",
      "https://www.googleapis.com/auth/userinfo.email",
      "https://www.googleapis.com/auth/userinfo.profile",
      birthdayContactsReadOnlyScope,
    ])
    return scopes.isSubset(of: allowed)
  }

  private static func mapGoogleError(_ error: Error) -> IOSGoogleIdentityFailure {
    let nsError = error as NSError
    if nsError.domain == kGIDSignInErrorDomain,
      nsError.code == GIDSignInErrorCode.canceled.rawValue
    {
      return .cancelled
    }
    if nsError.domain == NSURLErrorDomain {
      return .networkOffline
    }
    return .reconnectRequired
  }

  private static func mapFirebaseError(_ error: Error) -> IOSGoogleIdentityFailure {
    switch (error as NSError).code {
    case AuthErrorCode.userDisabled.rawValue: return .firebaseUserDisabled
    case AuthErrorCode.networkError.rawValue: return .networkOffline
    case AuthErrorCode.invalidUserToken.rawValue,
      AuthErrorCode.userTokenExpired.rawValue,
      AuthErrorCode.requiresRecentLogin.rawValue:
      return .reconnectRequired
    default: return .internalFailure
    }
  }

  private static func recoveryIdentityFailure(
    _ failure: IOSAccountDeletionFailure
  ) -> IOSGoogleIdentityFailure {
    switch failure {
    case .accountChanged: return .accountMismatch
    case .networkOffline: return .networkOffline
    case .recentAuthenticationRequired: return .reconnectRequired
    case .configuration, .responseInvalid, .unavailable:
      return .internalFailure
    }
  }

  private static func hasExactLiveProviderSession(
    _ binding: IOSNativeGoogleAccountBinding
  ) -> Bool {
    guard let google = GIDSignIn.sharedInstance.currentUser,
      google.userID == binding.googleSubject,
      let firebase = Auth.auth().currentUser,
      firebase.uid == binding.firebaseUID
    else { return false }
    return firebase.providerData.contains {
      $0.providerID == "google.com" && $0.uid == binding.googleSubject
    }
  }

  private static func foregroundPresenter() -> UIViewController? {
    guard UIApplication.shared.applicationState == .active else { return nil }
    let scenes = UIApplication.shared.connectedScenes
      .compactMap { $0 as? UIWindowScene }
      .filter { $0.activationState == .foregroundActive }
    guard scenes.count == 1,
      let window = scenes[0].windows.first(where: { $0.isKeyWindow }),
      let controller = visibleController(window.rootViewController),
      controller.viewIfLoaded?.window === window,
      controller.presentedViewController == nil,
      !controller.isBeingDismissed
    else {
      return nil
    }
    return controller
  }

  private static func visibleController(_ controller: UIViewController?) -> UIViewController? {
    if let presented = controller?.presentedViewController {
      return visibleController(presented)
    }
    if let navigation = controller as? UINavigationController {
      return visibleController(navigation.visibleViewController)
    }
    if let tabs = controller as? UITabBarController {
      return visibleController(tabs.selectedViewController)
    }
    return controller
  }
}
