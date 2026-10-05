import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
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

void main() {
  group('LiveGoogleAuthGateway', () {
    late InMemoryStoreDriver store;

    setUp(() {
      store = InMemoryStoreDriver();
    });

    test('isConfigured reports true', () async {
      final gateway = LiveGoogleAuthGateway(store: store);
      expect(await gateway.isConfigured(), isTrue);
    });

    test('getStoredIdentity returns null when store is empty', () async {
      final gateway = LiveGoogleAuthGateway(store: store);
      expect(await gateway.getStoredIdentity(), isNull);
    });

    test('getStoredIdentity restores persisted session', () async {
      store.data['auth_session_subject'] = 'sub-456';
      store.data['auth_session_email'] = 'test@example.com';
      store.data['auth_session_name'] = 'Test Person';
      store.data['auth_session_uid'] = 'firebase-uid-456';
      store.data['auth_session_id_token'] = 'token-xyz';

      final gateway = LiveGoogleAuthGateway(store: store);
      final identity = await gateway.getStoredIdentity();

      expect(identity, isNotNull);
      expect(identity!.googleSubject, 'sub-456');
      expect(identity.email, 'test@example.com');
      expect(identity.displayName, 'Test Person');
      expect(identity.firebaseUid, 'firebase-uid-456');
      expect(identity.idToken, 'token-xyz');
    });

    test('signOut clears all persisted session keys', () async {
      store.data['auth_session_subject'] = 'sub-456';
      store.data['auth_session_email'] = 'test@example.com';
      store.data['auth_session_name'] = 'Test Person';

      final gateway = LiveGoogleAuthGateway(store: store);
      await gateway.signOut();

      expect(await gateway.getStoredIdentity(), isNull);
      expect(store.data.isEmpty, isTrue);
    });

    test('signInWithEmail returns SignInSuccess on 200 response', () async {
      final mockClient = MockClient((request) async {
        expect(request.url.path, contains('signInWithPassword'));
        return http.Response(
          jsonEncode({
            'localId': 'user-123',
            'email': 'user@example.com',
            'displayName': 'User One',
            'idToken': 'mock-firebase-token',
          }),
          200,
        );
      });

      final gateway = LiveGoogleAuthGateway(
        store: store,
        httpClient: mockClient,
      );

      final outcome = await gateway.signInWithEmail(
        'user@example.com',
        'pass1234',
      );
      expect(outcome, isA<SignInSuccess>());
      final success = outcome as SignInSuccess;
      expect(success.identity.email, 'user@example.com');
      expect(success.identity.firebaseUid, 'user-123');

      // Verify session was saved to store
      expect(store.data['auth_session_email'], 'user@example.com');
    });

    test('signInWithEmail maps Firebase errors appropriately', () async {
      final mockClient = MockClient((request) async {
        return http.Response(
          jsonEncode({
            'error': {'message': 'INVALID_LOGIN_CREDENTIALS'},
          }),
          400,
        );
      });

      final gateway = LiveGoogleAuthGateway(
        store: store,
        httpClient: mockClient,
      );

      final outcome = await gateway.signInWithEmail(
        'user@example.com',
        'wrong',
      );
      expect(outcome, isA<SignInFailed>());
      final failed = outcome as SignInFailed;
      expect(failed.message, contains('Invalid email or password.'));
    });

    test('signUpWithEmail returns SignInSuccess on 200 response', () async {
      final mockClient = MockClient((request) async {
        expect(request.url.path, contains('signUp'));
        return http.Response(
          jsonEncode({
            'localId': 'new-user-456',
            'email': 'new@example.com',
            'idToken': 'mock-new-token',
          }),
          200,
        );
      });

      final gateway = LiveGoogleAuthGateway(
        store: store,
        httpClient: mockClient,
      );

      final outcome = await gateway.signUpWithEmail(
        'new@example.com',
        'pass1234',
      );
      expect(outcome, isA<SignInSuccess>());
      final success = outcome as SignInSuccess;
      expect(success.identity.email, 'new@example.com');
      expect(success.identity.firebaseUid, 'new-user-456');
    });

    test('phone OTP send and verify flow succeeds', () async {
      final gateway = LiveGoogleAuthGateway(store: store);
      final sent = await gateway.sendPhoneOtp('+15551234567');
      expect(sent, isTrue);

      final invalidResult = await gateway.verifyPhoneOtp(
        '+15551234567',
        '000000',
      );
      expect(invalidResult, isA<SignInFailed>());

      final otp = gateway.getActiveOtp('+15551234567');
      expect(otp, isNotNull);

      final verifyResult = await gateway.verifyPhoneOtp('+15551234567', otp!);
      expect(verifyResult, isA<SignInSuccess>());
      final success = verifyResult as SignInSuccess;
      expect(success.identity.displayName, '+15551234567');
      expect(store.data['auth_session_name'], '+15551234567');
    });

    test('email OTP send and verify flow succeeds', () async {
      final gateway = LiveGoogleAuthGateway(store: store);
      final sent = await gateway.sendEmailOtp('otpuser@example.com');
      expect(sent, isTrue);

      final invalidResult = await gateway.verifyEmailOtp(
        'otpuser@example.com',
        '000000',
      );
      expect(invalidResult, isA<SignInFailed>());

      final otp = gateway.getActiveOtp('otpuser@example.com');
      expect(otp, isNotNull);

      final verifyResult = await gateway.verifyEmailOtp(
        'otpuser@example.com',
        otp!,
      );
      expect(verifyResult, isA<SignInSuccess>());
      final success = verifyResult as SignInSuccess;
      expect(success.identity.email, 'otpuser@example.com');
      expect(store.data['auth_session_email'], 'otpuser@example.com');
    });
  });
}
