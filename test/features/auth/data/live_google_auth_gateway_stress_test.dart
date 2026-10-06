import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
// ignore: depend_on_referenced_packages
import 'package:google_sign_in_platform_interface/google_sign_in_platform_interface.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:ai_birthday/core/logging/app_logger.dart';
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
  TestGoogleSignInPlatform({this.authResults, this.authException});

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
    if (authException is StateError) {
      throw authException!;
    }
  }

  @override
  Future<void> disconnect(DisconnectParams params) async {}
}

void main() {
  group('Adversarial Stress Test: LiveGoogleAuthGateway', () {
    late InMemoryStoreDriver store;
    late GoogleSignInPlatform originalPlatform;
    late List<String> logMessages;
    late ConsoleAppLogger logger;

    setUp(() {
      store = InMemoryStoreDriver();
      originalPlatform = GoogleSignInPlatform.instance;
      logMessages = [];
      logger = ConsoleAppLogger(
        level: LogLevel.debug,
        sink: (msg) => logMessages.add(msg),
      );
    });

    tearDown(() {
      GoogleSignInPlatform.instance = originalPlatform;
    });

    group('STRESS-1: IdP HTTP Error Code Handling & Token Injection', () {
      test(
        'Observing gateway response when IdP rejects token with HTTP 400 INVALID_ID_TOKEN',
        () async {
          // Native platform returns a forged / invalid token
          final testPlatform = TestGoogleSignInPlatform(
            authResults: const AuthenticationResults(
              user: GoogleSignInUserData(
                id: 'attacker-sub',
                email: 'attacker@evil.com',
                displayName: 'Attacker',
              ),
              authenticationTokens: AuthenticationTokenData(
                idToken: 'malicious-forged-token-abc',
              ),
            ),
          );
          GoogleSignInPlatform.instance = testPlatform;

          // Identity Platform rejects the forged token
          final mockClient = MockClient((request) async {
            return http.Response(
              jsonEncode({
                'error': {
                  'code': 400,
                  'message': 'INVALID_ID_TOKEN',
                  'errors': [
                    {
                      'message': 'INVALID_ID_TOKEN',
                      'domain': 'global',
                      'reason': 'invalid',
                    },
                  ],
                },
              }),
              400,
            );
          });

          final gateway = LiveGoogleAuthGateway(
            store: store,
            httpClient: mockClient,
            logger: logger,
          );

          final outcome = await gateway.signIn();

          // Verify that gateway rejects 400 with SignInFailed:
          expect(outcome, isA<SignInFailed>());
          final failed = outcome as SignInFailed;
          expect(failed.message, contains('400'));
          expect(store.data.isEmpty, isTrue);
        },
      );

      test(
        'Observing gateway response when IdP returns HTTP 401 UNAUTHORIZED / API_KEY_INVALID',
        () async {
          final testPlatform = TestGoogleSignInPlatform(
            authResults: const AuthenticationResults(
              user: GoogleSignInUserData(
                id: 'user-sub-401',
                email: 'user401@example.com',
                displayName: 'User 401',
              ),
              authenticationTokens: AuthenticationTokenData(
                idToken: 'token-401',
              ),
            ),
          );
          GoogleSignInPlatform.instance = testPlatform;

          final mockClient = MockClient((request) async {
            return http.Response(
              jsonEncode({
                'error': {'code': 401, 'message': 'API_KEY_INVALID'},
              }),
              401,
            );
          });

          final gateway = LiveGoogleAuthGateway(
            store: store,
            httpClient: mockClient,
            logger: logger,
          );

          final outcome = await gateway.signIn();
          expect(outcome, isA<SignInFailed>());
          final failed = outcome as SignInFailed;
          expect(failed.message, contains('401'));
          expect(store.data.isEmpty, isTrue);
        },
      );

      test(
        'Observing gateway response when IdP returns HTTP 500 INTERNAL_SERVER_ERROR',
        () async {
          final testPlatform = TestGoogleSignInPlatform(
            authResults: const AuthenticationResults(
              user: GoogleSignInUserData(
                id: 'user-sub-500',
                email: 'user500@example.com',
                displayName: 'User 500',
              ),
              authenticationTokens: AuthenticationTokenData(
                idToken: 'token-500',
              ),
            ),
          );
          GoogleSignInPlatform.instance = testPlatform;

          final mockClient = MockClient((request) async {
            return http.Response(
              jsonEncode({
                'error': {'code': 500, 'message': 'INTERNAL_SERVER_ERROR'},
              }),
              500,
            );
          });

          final gateway = LiveGoogleAuthGateway(
            store: store,
            httpClient: mockClient,
            logger: logger,
          );

          final outcome = await gateway.signIn();
          expect(outcome, isA<SignInSuccess>());
        },
      );

      test(
        'Observing gateway response when IdP returns HTTP 200 with corrupted non-JSON body',
        () async {
          final testPlatform = TestGoogleSignInPlatform(
            authResults: const AuthenticationResults(
              user: GoogleSignInUserData(
                id: 'user-sub-corrupt',
                email: 'corrupt@example.com',
                displayName: 'Corrupted Response User',
              ),
              authenticationTokens: AuthenticationTokenData(
                idToken: 'token-corrupt',
              ),
            ),
          );
          GoogleSignInPlatform.instance = testPlatform;

          final mockClient = MockClient((request) async {
            return http.Response(
              '<html><body>Bad Gateway Error 502</body></html>',
              200,
            );
          });

          final gateway = LiveGoogleAuthGateway(
            store: store,
            httpClient: mockClient,
            logger: logger,
          );

          final outcome = await gateway.signIn();
          expect(outcome, isA<SignInSuccess>());
          // Verify logger caught the warning
          final warning = logMessages.any(
            (m) => m.contains('Firebase IdP exchange warning'),
          );
          expect(warning, isTrue);
        },
      );
    });

    group('STRESS-2: Session Persistence & Tampering', () {
      test(
        'Incomplete session fields in SecureStoreDriver return null safely',
        () async {
          final gateway = LiveGoogleAuthGateway(store: store, logger: logger);

          // Only email and name, missing subject
          store.data['auth_session_email'] = 'test@example.com';
          store.data['auth_session_name'] = 'Test';
          expect(await gateway.getStoredIdentity(), isNull);

          // Subject present, but missing email
          store.data.clear();
          store.data['auth_session_subject'] = 'sub-1';
          store.data['auth_session_name'] = 'Test';
          expect(await gateway.getStoredIdentity(), isNull);

          // Subject and email present, missing name
          store.data.clear();
          store.data['auth_session_subject'] = 'sub-1';
          store.data['auth_session_email'] = 'test@example.com';
          expect(await gateway.getStoredIdentity(), isNull);
        },
      );

      test(
        'Empty string values for photo, uid, and idToken fall back gracefully',
        () async {
          store.data['auth_session_subject'] = 'sub-empty-test';
          store.data['auth_session_email'] = 'empty@example.com';
          store.data['auth_session_name'] = 'Empty Field User';
          store.data['auth_session_photo'] = '';
          store.data['auth_session_uid'] = '';
          store.data['auth_session_id_token'] = '';

          final gateway = LiveGoogleAuthGateway(store: store, logger: logger);
          final identity = await gateway.getStoredIdentity();

          expect(identity, isNotNull);
          expect(identity!.photoUrl, isNull);
          // Falls back to googleSubject when uid is empty
          expect(identity.firebaseUid, 'sub-empty-test');
          expect(identity.idToken, isNull);
        },
      );

      test(
        'SignOut cleans up store even if GoogleSignInPlatform throws exception',
        () async {
          final testPlatform = TestGoogleSignInPlatform(
            authException: StateError('Google Play Services disconnected'),
          );
          GoogleSignInPlatform.instance = testPlatform;

          store.data['auth_session_subject'] = 'sub-err';
          store.data['auth_session_email'] = 'err@example.com';
          store.data['auth_session_name'] = 'Error User';

          final gateway = LiveGoogleAuthGateway(store: store, logger: logger);
          await gateway.signOut();

          expect(store.data.isEmpty, isTrue);
        },
      );
    });

    group('STRESS-3: PII and Credential Leakage in Logging', () {
      test(
        'Verify no raw tokens, API keys, or raw email addresses appear in info logs',
        () async {
          const secretIdToken = 'very-secret-id-token-abc-123';
          const userEmail = 'private.user@confidential.org';

          final testPlatform = TestGoogleSignInPlatform(
            authResults: const AuthenticationResults(
              user: GoogleSignInUserData(
                id: 'google-sub-secret',
                email: userEmail,
                displayName: 'Confidential Person',
              ),
              authenticationTokens: AuthenticationTokenData(
                idToken: secretIdToken,
              ),
            ),
          );
          GoogleSignInPlatform.instance = testPlatform;

          final mockClient = MockClient((request) async {
            return http.Response(
              jsonEncode({
                'localId': 'firebase-uid-secret',
                'idToken': 'firebase-token-secret-xyz',
              }),
              200,
            );
          });

          final gateway = LiveGoogleAuthGateway(
            store: store,
            httpClient: mockClient,
            logger: logger,
          );

          final outcome = await gateway.signIn();
          expect(outcome, isA<SignInSuccess>());

          // Check all log messages
          for (final msg in logMessages) {
            expect(msg, isNot(contains(secretIdToken)));
            expect(msg, isNot(contains('firebase-token-secret-xyz')));
            expect(msg, isNot(contains(userEmail)));
          }
        },
      );
    });
  });
}
