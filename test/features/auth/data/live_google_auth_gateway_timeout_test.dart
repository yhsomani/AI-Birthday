import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
// ignore: depend_on_referenced_packages
import 'package:google_sign_in_platform_interface/google_sign_in_platform_interface.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/auth/data/live_google_auth_gateway.dart';
import 'package:ai_birthday/features/auth/domain/google_identity.dart';

class _MapStore implements SecureStoreDriver {
  final Map<String, String> data = {};
  @override
  Future<void> delete(String key) async => data.remove(key);
  @override
  Future<String?> read(String key) async => data[key];
  @override
  Future<void> write(String key, String value) async => data[key] = value;
}

class _Platform extends GoogleSignInPlatform {
  @override
  Future<void> init(InitParameters params) async {}
  @override
  Future<AuthenticationResults?>? attemptLightweightAuthentication(
    AttemptLightweightAuthenticationParameters params,
  ) async => null;
  @override
  bool supportsAuthenticate() => true;
  @override
  Future<AuthenticationResults> authenticate(
    AuthenticateParameters params,
  ) async => const AuthenticationResults(
    user: GoogleSignInUserData(
      id: 'sub-1',
      email: 'a@example.com',
      displayName: 'A',
    ),
    authenticationTokens: AuthenticationTokenData(idToken: 'google-token'),
  );
  @override
  bool authorizationRequiresUserInteraction() => false;
  @override
  Future<ClientAuthorizationTokenData?> clientAuthorizationTokensForScopes(
    ClientAuthorizationTokensForScopesParameters params,
  ) async => null;
  @override
  Future<ServerAuthorizationTokenData?> serverAuthorizationTokensForScopes(
    ServerAuthorizationTokensForScopesParameters params,
  ) async => null;
  @override
  Future<void> signOut(SignOutParams params) async {}
  @override
  Future<void> disconnect(DisconnectParams params) async {}
}

void main() {
  late GoogleSignInPlatform original;
  setUp(() {
    original = GoogleSignInPlatform.instance;
    GoogleSignInPlatform.instance = _Platform();
  });
  tearDown(() => GoogleSignInPlatform.instance = original);

  MockClient hangingClient() =>
      MockClient((_) => Completer<http.Response>().future);

  test('signInWithIdp that never answers fails with a retryable timeout '
      'error and stores nothing (A3-008)', () async {
    final store = _MapStore();
    final gateway = LiveGoogleAuthGateway(
      store: store,
      httpClient: hangingClient(),
      requestTimeout: const Duration(milliseconds: 50),
    );

    final outcome = await gateway.signIn();

    expect(outcome, isA<SignInFailed>());
    expect((outcome as SignInFailed).message, contains('timed out'));
    expect(store.data, isEmpty);
  });

  test('securetoken refresh that never answers times out and keeps the '
      'stored session (retryable, not signed out) (A3-008)', () async {
    final store = _MapStore()
      ..data.addAll({
        'auth_session_subject': 'sub-1',
        'auth_session_email': 'a@example.com',
        'auth_session_name': 'A',
        'auth_session_uid': 'uid-1',
        'auth_session_id_token': 'old-token',
        'auth_session_refresh_token': 'refresh-1',
      });
    final gateway = LiveGoogleAuthGateway(
      store: store,
      httpClient: hangingClient(),
      requestTimeout: const Duration(milliseconds: 50),
    );

    final identity = await gateway.refreshSession();

    expect(identity, isNotNull);
    expect(identity!.idToken, 'old-token');
    expect(store.data['auth_session_refresh_token'], 'refresh-1');
  });
}
