import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../../../core/logging/app_logger.dart';
import '../../../core/security/flutter_secure_storage_driver.dart';
import '../data/live_google_auth_gateway.dart';
import '../domain/auth_state.dart';
import '../domain/google_identity.dart';
import 'google_auth_gateway.dart';

/// The application auth gateway. Defaults to [LiveGoogleAuthGateway] for
/// device and production runtime; test environments override with test doubles.
final googleAuthGatewayProvider = Provider<GoogleAuthGateway>((ref) {
  return LiveGoogleAuthGateway(
    store: const FlutterSecureStorageDriver(FlutterSecureStorage()),
    logger: ConsoleAppLogger(),
  );
});

/// Application auth state, mutated only through the gateway. build() checks
/// gateway configuration and restores any previously persisted session identity.
class AuthController extends AsyncNotifier<AuthState> {
  AuthController({AppLogger? logger}) : _logger = logger ?? const NoopLogger();

  final AppLogger _logger;

  GoogleAuthGateway get _gateway => ref.read(googleAuthGatewayProvider);

  @override
  Future<AuthState> build() async {
    final ok = await _gateway.isConfigured();
    if (!ok) return const AuthState(status: AuthStatus.unavailable);
    final stored = await _gateway.getStoredIdentity();
    if (stored != null) {
      return AuthState(status: AuthStatus.signedIn, identity: stored);
    }
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

  Future<SignInOutcome> signInWithEmail(String email, String password) async {
    final gateway = _gateway;
    if (gateway is LiveGoogleAuthGateway) {
      final outcome = await gateway.signInWithEmail(email, password);
      if (outcome is SignInSuccess) {
        state = AsyncData(
          AuthState(status: AuthStatus.signedIn, identity: outcome.identity),
        );
      }
      return outcome;
    }
    return const SignInUnavailable(reason: 'Email sign-in not available');
  }

  Future<SignInOutcome> signUpWithEmail(String email, String password) async {
    final gateway = _gateway;
    if (gateway is LiveGoogleAuthGateway) {
      final outcome = await gateway.signUpWithEmail(email, password);
      if (outcome is SignInSuccess) {
        state = AsyncData(
          AuthState(status: AuthStatus.signedIn, identity: outcome.identity),
        );
      }
      return outcome;
    }
    return const SignInUnavailable(reason: 'Email registration not available');
  }

  Future<bool> sendPhoneOtp(String phoneNumber) async {
    final gateway = _gateway;
    if (gateway is LiveGoogleAuthGateway) {
      return gateway.sendPhoneOtp(phoneNumber);
    }
    return false;
  }

  Future<SignInOutcome> verifyPhoneOtp(String phoneNumber, String otp) async {
    final gateway = _gateway;
    if (gateway is LiveGoogleAuthGateway) {
      final outcome = await gateway.verifyPhoneOtp(phoneNumber, otp);
      if (outcome is SignInSuccess) {
        state = AsyncData(
          AuthState(status: AuthStatus.signedIn, identity: outcome.identity),
        );
      }
      return outcome;
    }
    return const SignInUnavailable(reason: 'Phone verification not available');
  }

  Future<bool> sendEmailOtp(String email) async {
    final gateway = _gateway;
    if (gateway is LiveGoogleAuthGateway) {
      return gateway.sendEmailOtp(email);
    }
    return false;
  }

  Future<SignInOutcome> verifyEmailOtp(String email, String otp) async {
    final gateway = _gateway;
    if (gateway is LiveGoogleAuthGateway) {
      final outcome = await gateway.verifyEmailOtp(email, otp);
      if (outcome is SignInSuccess) {
        state = AsyncData(
          AuthState(status: AuthStatus.signedIn, identity: outcome.identity),
        );
      }
      return outcome;
    }
    return const SignInUnavailable(reason: 'Email verification not available');
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
