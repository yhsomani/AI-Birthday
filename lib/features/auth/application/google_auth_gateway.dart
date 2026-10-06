import '../domain/google_identity.dart';

/// Platform boundary for Google sign-in / Firebase Auth (SSOT §12, FR-001).
///
/// The device implementation follows the restored reference clients in
/// `firebase/reference/FirebaseIdentityRuntime.kt`: Google OAuth id token →
/// Firebase Auth (`com.google.firebase.auth`) gated by App Check / Play
/// Integrity; region `asia-south1`. Deliberately abstract — no fake sign-in is
/// ever shipped as product behaviour.
abstract class GoogleAuthGateway {
  /// Reports whether Google/Firebase are configured enough to attempt an
  /// account provider on this build.
  Future<bool> isConfigured();

  /// Starts the Google sign-in flow.
  Future<SignInOutcome> signIn();

  /// Signs the current Google/Firebase session out.
  Future<void> signOut();

  /// Retrieves any previously persisted active session identity.
  Future<GoogleIdentity?> getStoredIdentity();

  /// Refreshes the active session ID token using the persisted refresh token.
  Future<GoogleIdentity?> refreshSession();
}

/// Truthful adapter for builds without Firebase configured (the current host
/// build: `google-services` is disabled until a client matches
/// `com.yashsomani.ai_birthday`). Reports unavailability; never fabricates a
/// session.
class UnavailableGoogleAuthGateway implements GoogleAuthGateway {
  const UnavailableGoogleAuthGateway();

  @override
  Future<bool> isConfigured() async => false;

  @override
  Future<SignInOutcome> signIn() async => const SignInUnavailable(
    reason: 'Google sign-in is available on device builds.',
  );

  @override
  Future<void> signOut() async {}

  @override
  Future<GoogleIdentity?> getStoredIdentity() async => null;

  @override
  Future<GoogleIdentity?> refreshSession() async => null;
}
