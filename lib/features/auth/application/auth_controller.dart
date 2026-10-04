import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/logging/app_logger.dart';
import '../domain/auth_state.dart';
import '../domain/google_identity.dart';
import 'google_auth_gateway.dart';

/// The application auth gateway. Defaults to the truthful unavailable adapter
/// for the current host build; device builds override with the real
/// Google/Firebase gateway (see `firebase/reference/FirebaseIdentityRuntime.kt`).
final googleAuthGatewayProvider = Provider<GoogleAuthGateway>(
  (ref) => const UnavailableGoogleAuthGateway(),
);

/// Application auth state, mutated only through the gateway. build() awaits a
/// real configuration report from the gateway — status is never invented.
class AuthController extends AsyncNotifier<AuthState> {
  AuthController({AppLogger? logger}) : _logger = logger ?? const NoopLogger();

  final AppLogger _logger;

  GoogleAuthGateway get _gateway => ref.read(googleAuthGatewayProvider);

  @override
  Future<AuthState> build() async {
    final ok = await _gateway.isConfigured();
    if (!ok) return const AuthState(status: AuthStatus.unavailable);
    // No persisted session in this phase; signing in is user-initiated.
    return const AuthState(status: AuthStatus.signedOut);
  }

  Future<SignInOutcome> signIn() async {
    final outcome = await _gateway.signIn();
    switch (outcome) {
      case SignInSuccess(identity: final identity):
        state = AsyncData(
          AuthState(status: AuthStatus.signedIn, identity: identity),
        );
        _logger.info(
          'auth',
          'signed in',
          params: {'subject': identity.googleSubject},
        );
      case SignInUnavailable():
        state = const AsyncData(AuthState(status: AuthStatus.unavailable));
      case SignInFailed():
        state = const AsyncData(AuthState(status: AuthStatus.signedOut));
    }
    return outcome;
  }

  Future<void> signOut() async {
    await _gateway.signOut();
    _logger.info('auth', 'signed out');
    state = const AsyncData(AuthState(status: AuthStatus.signedOut));
  }
}

final authControllerProvider = AsyncNotifierProvider<AuthController, AuthState>(
  () => AuthController(),
);
