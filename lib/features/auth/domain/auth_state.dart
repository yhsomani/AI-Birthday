import 'google_identity.dart';

/// User-visible authentication state, driven only by the auth gateway.
enum AuthStatus {
  /// The gateway has not reported yet.
  unknown,

  signedOut,

  signedIn,

  /// Google/Firebase are not available on this build or device.
  unavailable,
}

class AuthState {
  const AuthState({required this.status, this.identity});

  const AuthState.unknown() : this(status: AuthStatus.unknown);

  final AuthStatus status;
  final GoogleIdentity? identity;

  bool get isSignedIn => status == AuthStatus.signedIn;
}
