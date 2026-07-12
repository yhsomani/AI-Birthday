import Foundation
import GoogleSignIn

enum IOSPeopleAuthorizationResult {
  case authorized(IOSEphemeralGoogleAccessToken)
  case failed(IOSPeopleSyncFailure)
}

/// Requests only contacts.readonly after the primary Google identity is connected.
/// No server client ID, offline access, refresh token, or authorization code is requested.
@MainActor
final class IOSPeopleAuthorizationGateway {
  private let identity: IOSGoogleIdentityCoordinator

  init(identity: IOSGoogleIdentityCoordinator = .shared) {
    self.identity = identity
  }

  func acquire(interactive: Bool) async -> IOSPeopleAuthorizationResult {
    guard await appCheckPasses(), let expected = identity.exactSessionBinding(),
      let initialUser = GIDSignIn.sharedInstance.currentUser
    else {
      return .failed(.authorizationRequired)
    }
    let scopedUser: GIDGoogleUser
    do {
      let currentScopes = Set(initialUser.grantedScopes ?? [])
      if !currentScopes.contains(birthdayContactsReadOnlyScope) {
        guard interactive, let presenter = identity.foregroundViewController() else {
          return .failed(.authorizationRequired)
        }
        let result = try await initialUser.addScopes(
          [birthdayContactsReadOnlyScope],
          presenting: presenter
        )
        guard result.serverAuthCode == nil else { return .failed(.authorizationRequired) }
        scopedUser = result.user
      } else {
        scopedUser = initialUser
      }
      let user = try await scopedUser.refreshTokensIfNeeded()
      guard Self.validScopes(user.grantedScopes),
        user.userID == expected.googleSubject,
        IOSPeopleValuePolicy.safeEmail(user.profile?.email)?.caseInsensitiveCompare(
          expected.displayEmail
        ) == .orderedSame,
        identity.exactSessionBinding()?.hasSameOwner(as: expected) == true,
        let token = IOSEphemeralGoogleAccessToken(user.accessToken.tokenString)
      else {
        return .failed(.authorizationRequired)
      }
      return .authorized(token)
    } catch {
      let nsError = error as NSError
      if nsError.domain == kGIDSignInErrorDomain,
        nsError.code == GIDSignInErrorCode.canceled.rawValue
      {
        return .failed(.cancelled)
      }
      if nsError.domain == NSURLErrorDomain { return .failed(.networkOffline) }
      return .failed(.authorizationRequired)
    }
  }

  private func appCheckPasses() async -> Bool {
    await withCheckedContinuation { continuation in
      identity.appCheckGate { continuation.resume(returning: $0) }
    }
  }

  private static func validScopes(_ raw: [String]?) -> Bool {
    let scopes = Set((raw ?? []).map { $0.trimmingCharacters(in: .whitespacesAndNewlines) })
    let allowed = Set([
      "email", "openid", "profile",
      "https://www.googleapis.com/auth/userinfo.email",
      "https://www.googleapis.com/auth/userinfo.profile",
      birthdayContactsReadOnlyScope,
    ])
    return scopes.contains(birthdayContactsReadOnlyScope) && scopes.isSubset(of: allowed)
  }
}
