import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
// ignore: depend_on_referenced_packages
import 'package:google_sign_in_platform_interface/google_sign_in_platform_interface.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/auth/data/live_google_auth_gateway.dart';
import 'package:ai_birthday/features/auth/domain/google_identity.dart';

class InMemoryStoreDriver implements SecureStoreDriver {
  final Map<String, String> data = {};

  @override
  Future<void> delete(String key) async {
    data.remove(key);
  }

  @override
  Future<String?> read(String key) async => data[key];

  @override
  Future<void> write(String key, String value) async {
    data[key] = value;
  }
}

class TestGoogleSignInPlatform extends GoogleSignInPlatform {
  TestGoogleSignInPlatform({
    this.authResults,
    this.authException,
  });

  AuthenticationResults? authResults;
  Object? authException;
  int signOutCalls = 0;
  int initCalls = 0;

  @override
  Future<void> init(InitParameters params) async {
    initCalls++;
  }

  @override
  Future<AuthenticationResults?>? attemptLightweightAuthentication(
    AttemptLightweightAuthenticationParameters params,
  ) async => null;

  @override
  bool supportsAuthenticate() => true;

  @override
  Future<AuthenticationResults> authenticate(
    AuthenticateParameters params,
  ) async {
    if (authException != null) {
      throw authException!;
    }
    if (authResults != null) {
      return authResults!;
    }
    throw const GoogleSignInException(
      code: GoogleSignInExceptionCode.canceled,
      description: 'Sign-in cancelled',
    );
  }

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
  Future<void> signOut(SignOutParams params) async {
    signOutCalls++;
  }

  @override
  Future<void> disconnect(DisconnectParams params) async {}
}

void main() {
  group('LiveGoogleAuthGateway', () {
    late InMemoryStoreDriver store;
    late GoogleSignInPlatform originalPlatform;

    setUp(() {
      store = InMemoryStoreDriver();
      originalPlatform = GoogleSignInPlatform.instance;
    });

    tearDown(() {
      GoogleSignInPlatform.instance = originalPlatform;
    });

    test('isConfigured reports true', () async {
      final gateway = LiveGoogleAuthGateway(store: store);
      expect(await gateway.isConfigured(), isTrue);
    });

    test(
      'getStoredIdentity returns null when store is empty or incomplete',
      () async {
        final gateway = LiveGoogleAuthGateway(store: store);
        expect(await gateway.getStoredIdentity(), isNull);

        store.data['auth_session_subject'] = 'sub-only';
        expect(await gateway.getStoredIdentity(), isNull);
      },
    );

    test('getStoredIdentity accurately restores session credentials', () async {
      store.data['auth_session_subject'] = 'sub-456';
      store.data['auth_session_email'] = 'test@example.com';
      store.data['auth_session_name'] = 'Test Person';
      store.data['auth_session_photo'] = 'https://example.com/avatar.png';
      store.data['auth_session_uid'] = 'firebase-uid-456';
      store.data['auth_session_id_token'] = 'token-xyz';

      final gateway = LiveGoogleAuthGateway(store: store);
      final identity = await gateway.getStoredIdentity();

      expect(identity, isNotNull);
      expect(identity!.googleSubject, 'sub-456');
      expect(identity.email, 'test@example.com');
      expect(identity.displayName, 'Test Person');
      expect(identity.photoUrl, 'https://example.com/avatar.png');
      expect(identity.firebaseUid, 'firebase-uid-456');
      expect(identity.idToken, 'token-xyz');
    });

    test('signOut cleans up all session keys', () async {
      final testPlatform = TestGoogleSignInPlatform();
      GoogleSignInPlatform.instance = testPlatform;

      store.data['auth_session_subject'] = 'sub-456';
      store.data['auth_session_email'] = 'test@example.com';
      store.data['auth_session_name'] = 'Test Person';
      store.data['auth_session_photo'] = 'https://example.com/avatar.png';
      store.data['auth_session_uid'] = 'firebase-uid-456';
      store.data['auth_session_id_token'] = 'token-xyz';

      final gateway = LiveGoogleAuthGateway(store: store);
      await gateway.signOut();

      expect(await gateway.getStoredIdentity(), isNull);
      expect(store.data.isEmpty, isTrue);
      expect(testPlatform.signOutCalls, 1);
    });

    test(
      'successful IdP token exchange saves firebaseUid and idToken to secure storage',
      () async {
        final testPlatform = TestGoogleSignInPlatform(
          authResults: const AuthenticationResults(
            user: GoogleSignInUserData(
              id: 'google-sub-123',
              email: 'user@example.com',
              displayName: 'Test User',
              photoUrl: 'https://example.com/photo.jpg',
            ),
            authenticationTokens: AuthenticationTokenData(
              idToken: 'raw-google-id-token-abc',
            ),
          ),
        );
        GoogleSignInPlatform.instance = testPlatform;

        final mockClient = MockClient((request) async {
          expect(request.url.host, 'identitytoolkit.googleapis.com');
          expect(request.url.path, '/v1/accounts:signInWithIdp');
          expect(
            request.url.queryParameters['key'],
            isNotEmpty,
          );
          final body = jsonDecode(request.body) as Map<String, dynamic>;
          expect(
            body['postBody'],
            contains('id_token=raw-google-id-token-abc'),
          );
          expect(body['postBody'], contains('providerId=google.com'));
          expect(body['returnSecureToken'], isTrue);

          return http.Response(
            jsonEncode({
              'localId': 'firebase-uid-789',
              'idToken': 'verified-firebase-id-token-xyz',
            }),
            200,
          );
        });

        final gateway = LiveGoogleAuthGateway(
          store: store,
          httpClient: mockClient,
        );

        final outcome = await gateway.signIn();
        expect(outcome, isA<SignInSuccess>());
        final success = outcome as SignInSuccess;
        expect(success.identity.googleSubject, 'google-sub-123');
        expect(success.identity.email, 'user@example.com');
        expect(success.identity.displayName, 'Test User');
        expect(success.identity.photoUrl, 'https://example.com/photo.jpg');
        expect(success.identity.firebaseUid, 'firebase-uid-789');
        expect(success.identity.idToken, 'verified-firebase-id-token-xyz');

        // Verify session was saved to store
        expect(store.data['auth_session_subject'], 'google-sub-123');
        expect(store.data['auth_session_email'], 'user@example.com');
        expect(store.data['auth_session_name'], 'Test User');
        expect(
          store.data['auth_session_photo'],
          'https://example.com/photo.jpg',
        );
        expect(store.data['auth_session_uid'], 'firebase-uid-789');
        expect(
          store.data['auth_session_id_token'],
          'verified-firebase-id-token-xyz',
        );
      },
    );

    test('graceful fallback when IdP network exchange fails', () async {
      final testPlatform = TestGoogleSignInPlatform(
        authResults: const AuthenticationResults(
          user: GoogleSignInUserData(
            id: 'google-sub-fallback',
            email: 'offline@example.com',
            displayName: 'Offline User',
          ),
          authenticationTokens: AuthenticationTokenData(
            idToken: 'raw-google-id-token-offline',
          ),
        ),
      );
      GoogleSignInPlatform.instance = testPlatform;

      final mockClient = MockClient((request) async {
        throw http.ClientException('Network unreachable');
      });

      final gateway = LiveGoogleAuthGateway(
        store: store,
        httpClient: mockClient,
      );

      final outcome = await gateway.signIn();
      expect(outcome, isA<SignInSuccess>());
      final success = outcome as SignInSuccess;
      expect(success.identity.googleSubject, 'google-sub-fallback');
      expect(success.identity.email, 'offline@example.com');
      // Falls back to Google subject ID for firebaseUid and raw idToken
      expect(success.identity.firebaseUid, 'google-sub-fallback');
      expect(success.identity.idToken, 'raw-google-id-token-offline');

      // Verify fallback session saved to store
      expect(store.data['auth_session_subject'], 'google-sub-fallback');
      expect(store.data['auth_session_uid'], 'google-sub-fallback');
      expect(
        store.data['auth_session_id_token'],
        'raw-google-id-token-offline',
      );
    });

    test(
      'returns SignInFailed when native Google Sign-In fails or cancels',
      () async {
        final testPlatform = TestGoogleSignInPlatform(
          authException: const GoogleSignInException(
            code: GoogleSignInExceptionCode.canceled,
            description: 'User cancelled sign-in',
          ),
        );
        GoogleSignInPlatform.instance = testPlatform;

        final gateway = LiveGoogleAuthGateway(store: store);
        final outcome = await gateway.signIn();

        expect(outcome, isA<SignInFailed>());
        final failed = outcome as SignInFailed;
        expect(
          failed.message,
          contains('Google Sign-In failed or was cancelled'),
        );
        expect(store.data.isEmpty, isTrue);
      },
    );

    test(
      'returns SignInFailed when Identity Platform returns HTTP 400 rejection',
      () async {
        final testPlatform = TestGoogleSignInPlatform(
          authResults: const AuthenticationResults(
            user: GoogleSignInUserData(
              id: 'google-sub-400',
              email: 'user@example.com',
              displayName: 'Test User',
            ),
            authenticationTokens: AuthenticationTokenData(
              idToken: 'invalid-id-token',
            ),
          ),
        );
        GoogleSignInPlatform.instance = testPlatform;

        final mockClient = MockClient((request) async {
          return http.Response(
            jsonEncode({
              'error': {
                'code': 400,
                'message': 'INVALID_ID_TOKEN',
              },
            }),
            400,
          );
        });

        final gateway = LiveGoogleAuthGateway(
          store: store,
          httpClient: mockClient,
        );

        final outcome = await gateway.signIn();
        expect(outcome, isA<SignInFailed>());
        final failed = outcome as SignInFailed;
        expect(failed.message, contains('400'));
        expect(store.data.isEmpty, isTrue);
      },
    );

    test(
      'refreshSession exchanges refresh token via Secure Token API and updates stored idToken',
      () async {
        store.data['auth_session_subject'] = 'sub-ref-123';
        store.data['auth_session_email'] = 'refresh@example.com';
        store.data['auth_session_name'] = 'Refresh User';
        store.data['auth_session_uid'] = 'uid-ref-123';
        store.data['auth_session_id_token'] = 'old-expired-id-token';
        store.data['auth_session_refresh_token'] = 'valid-refresh-token-456';

        final mockClient = MockClient((request) async {
          expect(
            request.url.toString(),
            contains('securetoken.googleapis.com/v1/token'),
          );
          expect(request.body, contains('grant_type=refresh_token'));
          expect(request.body, contains('valid-refresh-token-456'));
          return http.Response(
            jsonEncode({
              'id_token': 'new-fresh-id-token-789',
              'refresh_token': 'new-fresh-refresh-token-999',
              'expires_in': '3600',
            }),
            200,
          );
        });

        final gateway = LiveGoogleAuthGateway(
          store: store,
          httpClient: mockClient,
          firebaseApiKey: 'test-api-key',
        );

        final updatedIdentity = await gateway.refreshSession();
        expect(updatedIdentity, isNotNull);
        expect(updatedIdentity!.idToken, 'new-fresh-id-token-789');
        expect(store.data['auth_session_id_token'], 'new-fresh-id-token-789');
        expect(
          store.data['auth_session_refresh_token'],
          'new-fresh-refresh-token-999',
        );
      },
    );

    test('signOut removes auth_session_refresh_token from secure store', () async {
      store.data['auth_session_subject'] = 'sub-1';
      store.data['auth_session_email'] = 'test@example.com';
      store.data['auth_session_name'] = 'Test';
      store.data['auth_session_refresh_token'] = 'refresh-token-xyz';

      final gateway = LiveGoogleAuthGateway(store: store);
      await gateway.signOut();

      expect(store.data['auth_session_refresh_token'], isNull);
      expect(store.data.isEmpty, isTrue);
    });
  });
}
