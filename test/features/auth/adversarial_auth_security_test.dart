import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
// ignore: depend_on_referenced_packages
import 'package:google_sign_in_platform_interface/google_sign_in_platform_interface.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/auth/application/auth_controller.dart';
import 'package:ai_birthday/features/auth/data/live_google_auth_gateway.dart';
import 'package:ai_birthday/features/auth/domain/google_identity.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'support/fake_auth_gateway.dart';

class AdversarialInMemoryStore implements SecureStoreDriver {
  final Map<String, String> data = {};
  bool throwOnRead = false;
  bool throwOnDelete = false;
  String? throwOnDeleteSpecificKey;

  @override
  Future<void> delete(String key) async {
    if (throwOnDelete || throwOnDeleteSpecificKey == key) {
      throw Exception('Simulated store delete failure for key: $key');
    }
    data.remove(key);
  }

  @override
  Future<String?> read(String key) async {
    if (throwOnRead) {
      throw Exception('Simulated store read failure for key: $key');
    }
    return data[key];
  }

  @override
  Future<void> write(String key, String value) async {
    data[key] = value;
  }
}

class AdversarialGoogleSignInPlatform extends GoogleSignInPlatform {
  AdversarialGoogleSignInPlatform({
    this.authResults,
    this.authException,
  });

  AuthenticationResults? authResults;
  Object? authException;
  int signOutCalls = 0;

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
  ) async {
    if (authException != null) throw authException!;
    if (authResults != null) return authResults!;
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
  group('Adversarial Auth Challenge 1: Session Security & Key Cleanup', () {
    late AdversarialInMemoryStore store;
    late GoogleSignInPlatform originalPlatform;

    setUp(() {
      store = AdversarialInMemoryStore();
      originalPlatform = GoogleSignInPlatform.instance;
    });

    tearDown(() {
      GoogleSignInPlatform.instance = originalPlatform;
    });

    test('signOut cleanly purges all 6 session keys', () async {
      final platform = AdversarialGoogleSignInPlatform();
      GoogleSignInPlatform.instance = platform;

      store.data['auth_session_subject'] = 'sub-test';
      store.data['auth_session_email'] = 'test@example.com';
      store.data['auth_session_name'] = 'Tester';
      store.data['auth_session_photo'] = 'https://example.com/avatar.jpg';
      store.data['auth_session_uid'] = 'uid-test';
      store.data['auth_session_id_token'] = 'token-test';

      final gateway = LiveGoogleAuthGateway(store: store);
      await gateway.signOut();

      expect(store.data.containsKey('auth_session_subject'), isFalse);
      expect(store.data.containsKey('auth_session_email'), isFalse);
      expect(store.data.containsKey('auth_session_name'), isFalse);
      expect(store.data.containsKey('auth_session_photo'), isFalse);
      expect(store.data.containsKey('auth_session_uid'), isFalse);
      expect(store.data.containsKey('auth_session_id_token'), isFalse);
      expect(store.data.isEmpty, isTrue);
      expect(platform.signOutCalls, 1);
    });

    test('signOut is idempotent on an already empty store', () async {
      final platform = AdversarialGoogleSignInPlatform();
      GoogleSignInPlatform.instance = platform;

      final gateway = LiveGoogleAuthGateway(store: store);
      await expectLater(gateway.signOut(), completes);
      await expectLater(gateway.signOut(), completes);
      expect(store.data.isEmpty, isTrue);
      expect(platform.signOutCalls, 2);
    });

    test('signOut succeeds even when GoogleSignInPlatform throws on sign out', () async {
      final platform = AdversarialGoogleSignInPlatform();
      GoogleSignInPlatform.instance = platform;

      store.data['auth_session_subject'] = 'sub-test';
      store.data['auth_session_email'] = 'test@example.com';
      store.data['auth_session_name'] = 'Tester';

      final gateway = LiveGoogleAuthGateway(store: store);
      await gateway.signOut();

      expect(store.data.isEmpty, isTrue);
    });

    test('FINDING 1: Cross-user session contamination when re-authenticating without explicit prior signOut', () async {
      // Scenario:
      // User 1 has authenticated and has photo and token saved.
      // User 2 logs in on the device, but User 2 has photoUrl = null.
      // Because _saveSession only writes _keyPhoto when photoUrl != null,
      // User 1's photo remains in storage and gets attached to User 2!
      final platform = AdversarialGoogleSignInPlatform(
        authResults: const AuthenticationResults(
          user: GoogleSignInUserData(
            id: 'user-2-sub',
            email: 'user2@example.com',
            displayName: 'User Two',
            photoUrl: null, // User 2 has NO photo
          ),
          authenticationTokens: AuthenticationTokenData(
            idToken: 'user-2-token',
          ),
        ),
      );
      GoogleSignInPlatform.instance = platform;

      // User 1's remnants in store
      store.data['auth_session_subject'] = 'user-1-sub';
      store.data['auth_session_email'] = 'user1@example.com';
      store.data['auth_session_name'] = 'User One';
      store.data['auth_session_photo'] = 'https://user1.com/private_avatar.png';
      store.data['auth_session_uid'] = 'user-1-uid';
      store.data['auth_session_id_token'] = 'user-1-token';

      final mockClient = MockClient((request) async {
        return http.Response(
          jsonEncode({
            'localId': 'user-2-firebase-uid',
            'idToken': 'user-2-firebase-token',
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

      // When session is reloaded via getStoredIdentity:
      final restored = await gateway.getStoredIdentity();
      expect(restored, isNotNull);
      expect(restored!.googleSubject, 'user-2-sub');

      // VERIFICATION: Check whether User 1's photo leaked into User 2's session
      // Purging old keys prevents cross-user contamination:
      expect(restored.photoUrl, isNull, reason: 'User 1 photo must not leak to User 2');
    });
  });

  group('Adversarial Auth Challenge 2: Corrupted & Partial Session Rejection', () {
    late AdversarialInMemoryStore store;

    setUp(() {
      store = AdversarialInMemoryStore();
    });

    test('getStoredIdentity rejects when subject is missing (null)', () async {
      store.data['auth_session_email'] = 'user@example.com';
      store.data['auth_session_name'] = 'User';
      final gateway = LiveGoogleAuthGateway(store: store);
      expect(await gateway.getStoredIdentity(), isNull);
    });

    test('getStoredIdentity rejects when email is missing (null)', () async {
      store.data['auth_session_subject'] = 'sub-123';
      store.data['auth_session_name'] = 'User';
      final gateway = LiveGoogleAuthGateway(store: store);
      expect(await gateway.getStoredIdentity(), isNull);
    });

    test('getStoredIdentity rejects when name is missing (null)', () async {
      store.data['auth_session_subject'] = 'sub-123';
      store.data['auth_session_email'] = 'user@example.com';
      final gateway = LiveGoogleAuthGateway(store: store);
      expect(await gateway.getStoredIdentity(), isNull);
    });

    test('getStoredIdentity rejects when only uid and id_token exist', () async {
      store.data['auth_session_uid'] = 'orphan-uid';
      store.data['auth_session_id_token'] = 'orphan-token';
      final gateway = LiveGoogleAuthGateway(store: store);
      expect(await gateway.getStoredIdentity(), isNull);
    });

    test('getStoredIdentity handles driver read exception without crashing', () async {
      store.throwOnRead = true;
      final recordingLogger = RecordingLogger();
      final gateway = LiveGoogleAuthGateway(
        store: store,
        logger: recordingLogger,
      );
      expect(await gateway.getStoredIdentity(), isNull);
      expect(
        recordingLogger.records.any(
          (r) => r.category == 'AuthGateway' && r.message.contains('Failed reading stored session'),
        ),
        isTrue,
      );
    });

    test('FINDING 2: getStoredIdentity rejects corrupted empty string session credentials', () async {
      // Storage corrupted with empty strings
      store.data['auth_session_subject'] = '';
      store.data['auth_session_email'] = '';
      store.data['auth_session_name'] = '';

      final gateway = LiveGoogleAuthGateway(store: store);
      final identity = await gateway.getStoredIdentity();

      expect(identity, isNull, reason: 'Empty string credentials must be rejected');
    });

    test('FINDING 3: getStoredIdentity rejects corrupted whitespace session credentials', () async {
      store.data['auth_session_subject'] = '   ';
      store.data['auth_session_email'] = '   ';
      store.data['auth_session_name'] = '   ';

      final gateway = LiveGoogleAuthGateway(store: store);
      final identity = await gateway.getStoredIdentity();

      expect(identity, isNull, reason: 'Whitespace credentials must be rejected');
    });

    test('FINDING 4: Dangling corrupted/partial keys are never cleaned up on rejection', () async {
      // If store contains only subject and uid (corrupted/partial session)
      store.data['auth_session_subject'] = 'sub-partial';
      store.data['auth_session_uid'] = 'uid-partial';

      final gateway = LiveGoogleAuthGateway(store: store);
      final identity = await gateway.getStoredIdentity();
      expect(identity, isNull);

      // The partial corrupted keys are left in secure storage forever
      expect(store.data['auth_session_subject'], 'sub-partial');
      expect(store.data['auth_session_uid'], 'uid-partial');
    });
  });

  group('Adversarial Auth Challenge 3: PII Logging Audit', () {
    test('FINDING 5: AppLogger regex redacts "subject", "uid", and "userId"', () {
      final recordingLogger = RecordingLogger();
      recordingLogger.info(
        'auth',
        'signed in',
        params: {
          'subject': '108234098234098230498',
          'uid': 'firebase-uid-secret-12345',
          'userId': 'user-secret-id',
          'email': 'real.user@gmail.com',
          'phone': '+15551234567',
        },
      );

      final record = recordingLogger.records.first;

      // email and phone ARE redacted:
      expect(record.params['email'], '[REDACTED]');
      expect(record.params['phone'], '[REDACTED]');

      // User identifiers ARE ALSO REDACTED:
      expect(record.params['subject'], '[REDACTED]');
      expect(record.params['uid'], '[REDACTED]');
      expect(record.params['userId'], '[REDACTED]');
    });

    test('FINDING 6: AuthController.signIn does not log raw Google subject ID', () async {
      final recordingLogger = RecordingLogger();
      final container = ProviderContainer(
        overrides: [
          googleAuthGatewayProvider.overrideWithValue(
            FakeAuthGateway(
              outcome: const SignInSuccess(
                GoogleIdentity(
                  googleSubject: 'google-sub-secret-user-id',
                  email: 'alice@example.com',
                  displayName: 'Alice',
                ),
              ),
            ),
          ),
          authControllerProvider.overrideWith(
            () => AuthController(logger: recordingLogger),
          ),
        ],
      );
      addTearDown(container.dispose);

      final notifier = container.read(authControllerProvider.notifier);
      await notifier.signIn();

      final logRecord = recordingLogger.records.firstWhere(
        (r) => r.category == 'auth' && r.message == 'signed in',
      );
      expect(logRecord.params.containsKey('subject'), isFalse);
    });

    test('LiveGoogleAuthGateway sign-in does not leak email in log messages', () async {
      final recordingLogger = RecordingLogger();
      final store = AdversarialInMemoryStore();
      final platform = AdversarialGoogleSignInPlatform(
        authResults: const AuthenticationResults(
          user: GoogleSignInUserData(
            id: 'sub-safe',
            email: 'secret.user@company.com',
            displayName: 'Secret User',
          ),
          authenticationTokens: AuthenticationTokenData(
            idToken: 'token-safe',
          ),
        ),
      );
      GoogleSignInPlatform.instance = platform;

      final gateway = LiveGoogleAuthGateway(
        store: store,
        logger: recordingLogger,
        httpClient: MockClient((_) async => http.Response(jsonEncode({}), 200)),
      );

      await gateway.signIn();

      // Check all log messages in recordingLogger
      for (final r in recordingLogger.records) {
        expect(r.message.contains('secret.user@company.com'), isFalse);
        expect(r.message.contains('Secret User'), isFalse);
      }
    });
  });
}
