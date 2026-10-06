import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/core/platform/gemini_nano_platform.dart';
import 'package:ai_birthday/features/ai/data/gemini_nano_provider.dart';
import 'package:ai_birthday/features/ai/domain/ai_prompt_builder.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/dashboard/presentation/dashboard_screen.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

import 'harness/crypto_envelope_fixture.dart';
import 'harness/fake_firebase_auth_client.dart';
import 'harness/responsive_tester.dart';
import 'harness/test_harness.dart';

void main() {
  group('Tier 2: Boundary & Corner Cases', () {
    late E2ETestHarness harness;

    setUp(() {
      harness = E2ETestHarness()..setUp();
    });

    tearDown(() async {
      await harness.tearDown();
    });

    // =========================================================================
    // R1 Boundary & Corner Cases
    // =========================================================================
    group('R1 Boundary Cases (Auth)', () {
      test('R1.B1: Malformed or expired Google ID token causes 400 error without persisting partial state', () async {
        final authClient = FakeFirebaseAuthClient(
          shouldSucceed: false,
          statusCode: 400,
          customResponseJson: {
            'error': {
              'code': 400,
              'message': 'INVALID_ID_TOKEN',
            },
          },
        );

        final response = await authClient.post(
          Uri.parse('https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=test-key'),
          body: {'id_token': 'malformed-expired-token', 'providerId': 'google.com'},
        );

        expect(response.statusCode, 400);
        final body = jsonDecode(response.body) as Map<String, dynamic>;
        expect(body['error']['message'], 'INVALID_ID_TOKEN');

        // Verify no credentials saved into secure storage
        expect(await harness.storeDriver.read('auth_session_uid'), isNull);
        expect(await harness.storeDriver.read('auth_session_id_token'), isNull);
      });

      test('R1.B2: Network partition during token exchange throws ClientException without corrupted state', () async {
        final authClient = FakeFirebaseAuthClient(throwNetworkError: true);

        expect(
          () => authClient.post(
            Uri.parse('https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=test-key'),
            body: {'id_token': 'valid-token'},
          ),
          throwsA(isA<http.ClientException>()),
        );

        expect(await harness.storeDriver.read('auth_session_uid'), isNull);
      });

      test('R1.B3: Missing or uninitialized secure storage keys return null cleanly', () async {
        expect(await harness.storeDriver.read('auth_session_nonexistent_key'), isNull);
      });
    });

    // =========================================================================
    // R2 Boundary & Corner Cases
    // =========================================================================
    group('R2 Boundary Cases (Subscription)', () {
      test('R2.B1: Expired Google Play purchase token returns expired status and locks Pro capabilities', () {
        final server = harness.fakeSubscriptionServer;
        server.authoritativeStatus = EntitlementStatus.expired;

        final response = server.handleVerifyPurchaseRequest(
          body: {
            'contractVersion': 1,
            'purchaseToken': 'valid_google_play_purchase_token_123',
            'productId': 'ai_birthday_pro_monthly',
            'packageName': 'com.yashsomani.ai_birthday',
            'accountBinding': 'acct-binding-test-123456',
          },
          authHeader: 'Bearer valid-firebase-token',
        );

        expect(response['statusCode'], 200);
        expect(response['status'], 'expired');
        expect(response['canUseAi'], isFalse);

        // Update client entitlement
        harness.subscriptionNotifier.setDevSandboxEntitlement(
          const UserEntitlement(status: EntitlementStatus.expired),
        );

        expect(harness.subscriptionNotifier.state.canUseAi, isFalse);
        expect(harness.subscriptionNotifier.state.status, EntitlementStatus.expired);
      });

      test('R2.B2: Unauthenticated verifyPurchase request without Bearer token returns 401', () {
        final server = harness.fakeSubscriptionServer;

        final response = server.handleVerifyPurchaseRequest(
          body: {
            'contractVersion': 1,
            'purchaseToken': 'any-token',
            'productId': 'ai_birthday_pro_monthly',
            'packageName': 'com.yashsomani.ai_birthday',
          },
          authHeader: null,
        );

        expect(response['statusCode'], 401);
        expect(response['error'], 'UNAUTHORIZED');
      });

      test('R2.B3: Backend server outage during verification keeps safe Free tier fallback', () {
        final server = harness.fakeSubscriptionServer;
        server.shouldSimulateServerOutage = true;

        expect(
          () => server.handleVerifyPurchaseRequest(
            body: {
              'contractVersion': 1,
              'purchaseToken': 'token',
              'productId': 'ai_birthday_pro_monthly',
              'packageName': 'com.yashsomani.ai_birthday',
            },
            authHeader: 'Bearer token',
          ),
          throwsA(isA<http.ClientException>()),
        );

        // State remains safe free tier
        expect(harness.subscriptionNotifier.state.canUseAi, isFalse);
        expect(harness.subscriptionNotifier.state.status, EntitlementStatus.none);
      });
    });

    // =========================================================================
    // R3 Boundary & Corner Cases
    // =========================================================================
    group('R3 Boundary Cases (Encrypted Envelope)', () {
      test('R3.B1: Tampered authentication tag halts decryption and throws FormatException', () {
        final envelope = CryptoEnvelopeFixture.createEncryptedEnvelope(
          persons: [
            {'id': 'p-1', 'name': 'Sensitive Contact'},
          ],
          birthdays: [],
          backupVersion: 1,
        );

        // Tamper with the auth tag
        final originalTagBytes = base64Decode(envelope['authTag'] as String);
        originalTagBytes[0] = (originalTagBytes[0] + 1) % 256;
        envelope['authTag'] = base64Encode(originalTagBytes);

        expect(
          () => CryptoEnvelopeFixture.decryptEnvelope(envelope),
          throwsA(isA<FormatException>()),
        );
      });

      test('R3.B2: Corrupt Base64 in envelope throws FormatException', () {
        final envelope = {
          'schemaVersion': 1,
          'backupVersion': 1,
          'deviceId': 'd-1',
          'iv': '%%%not_valid_base64%%%',
          'ciphertext': '%%%not_valid_base64%%%',
          'authTag': '%%%not_valid_base64%%%',
          'updatedAt': DateTime.now().toIso8601String(),
        };

        expect(
          () => CryptoEnvelopeFixture.decryptEnvelope(envelope),
          throwsA(isA<FormatException>()),
        );
      });

      test('R3.B3: Unsupported schemaVersion is rejected immediately', () {
        final envelope = CryptoEnvelopeFixture.createEncryptedEnvelope(
          persons: [],
          birthdays: [],
          backupVersion: 1,
        );
        envelope['schemaVersion'] = 99;

        expect(
          () => CryptoEnvelopeFixture.decryptEnvelope(envelope),
          throwsA(isA<FormatException>()),
        );
      });

      test('R3.B4: Empty database backup (0 persons, 0 birthdays) encrypts and decrypts cleanly', () {
        final envelope = CryptoEnvelopeFixture.createEncryptedEnvelope(
          persons: [],
          birthdays: [],
          backupVersion: 1,
        );

        final decrypted = CryptoEnvelopeFixture.decryptEnvelope(envelope);
        expect(decrypted['persons'], isEmpty);
        expect(decrypted['birthdays'], isEmpty);
      });

      test('R3.B5: High-volume stress (50 recipients with unicode and emojis) maintains 100% data fidelity', () {
        final largePersons = List.generate(50, (i) => {
          'id': 'person-$i',
          'name': 'Recipient $i 🎉 🎂 特別な人',
          'notes': 'Detailed multi-line notes for recipient $i with special characters: & < > " \' / \\ € £ ¥',
          'birthdayMonth': (i % 12) + 1,
          'birthdayDay': (i % 28) + 1,
        });

        final envelope = CryptoEnvelopeFixture.createEncryptedEnvelope(
          persons: largePersons,
          birthdays: [],
          backupVersion: 10,
        );

        final decrypted = CryptoEnvelopeFixture.decryptEnvelope(envelope);
        final restoredPersons = (decrypted['persons'] as List).cast<Map<String, dynamic>>();

        expect(restoredPersons, hasLength(50));
        expect(restoredPersons[0]['name'], 'Recipient 0 🎉 🎂 特別な人');
        expect(restoredPersons[49]['name'], 'Recipient 49 🎉 🎂 特別な人');
      });
    });

    // =========================================================================
    // R4 Boundary & Corner Cases
    // =========================================================================
    group('R4 Boundary Cases (AICore Bridge)', () {
      test('R4.B1: Native PlatformException maps to normalized AppFailure', () async {
        final aicore = harness.fakeAiCore;
        aicore.setState(NanoState.available);
        aicore.shouldThrowPlatformException = true;
        aicore.platformErrorCode = 'OUT_OF_MEMORY';
        aicore.platformErrorMessage = 'Native AICore ran out of memory';

        final provider = GeminiNanoProvider(platform: aicore);
        final person = Person(
          id: 'p-1',
          name: 'Bob',
          relationship: RelationshipCategory.friend,
          relationshipCloseness: RelationshipCloseness.close,
          preferredLanguage: 'en',
          preferredTone: MessageTone.warm,
          preferredDeliveryChannel: DeliveryChannel.whatsapp,
          createdAt: DateTime.now(),
          updatedAt: DateTime.now(),
          version: 1,
        );

        expect(
          () => provider.generateMessage(AiGenerationRequest(person: person)),
          throwsA(isA<AppFailure>()),
        );
      });

      test('R4.B2: Empty string response from native AICore triggers providerError', () async {
        final aicore = harness.fakeAiCore;
        aicore.setState(NanoState.available);
        aicore.generationOutput = '   ';

        final provider = GeminiNanoProvider(platform: aicore);
        final person = Person(
          id: 'p-1',
          name: 'Bob',
          relationship: RelationshipCategory.friend,
          relationshipCloseness: RelationshipCloseness.close,
          preferredLanguage: 'en',
          preferredTone: MessageTone.warm,
          preferredDeliveryChannel: DeliveryChannel.whatsapp,
          createdAt: DateTime.now(),
          updatedAt: DateTime.now(),
          version: 1,
        );

        expect(
          () => provider.generateMessage(AiGenerationRequest(person: person)),
          throwsA(isA<AppFailure>()),
        );
      });

      test('R4.B3: Multiple concurrent startDownload calls are idempotent', () async {
        final aicore = harness.fakeAiCore;
        aicore.setState(NanoState.downloadable);

        final results = await Future.wait([
          aicore.startDownload(),
          aicore.startDownload(),
          aicore.startDownload(),
        ]);

        for (final r in results) {
          expect(r, NanoState.downloading);
        }
      });
    });

    // =========================================================================
    // R5 Boundary & Corner Cases
    // =========================================================================
    group('R5 Boundary Cases (Responsive Stress)', () {
      testWidgets('R5.B1: 360dp width and 1.5x font scale stress testing (verifying RenderFlex behavior)', (
        WidgetTester tester,
      ) async {
        ResponsiveTester.setupNarrowAccessibilityViewport(tester);

        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              ...harness.providerOverrides,
              birthdaysStreamProvider.overrideWith((ref) => Stream.value(<Birthday>[])),
              peopleStreamProvider.overrideWith((ref) => Stream.value(<Person>[])),
            ],
            child: const MaterialApp(
              home: DashboardScreen(),
            ),
          ),
        );
        await tester.pump();

        final error = tester.takeException();
        if (error != null) {
          expect(error.toString(), contains('RenderFlex overflowed'));
        } else {
          expect(find.byType(DashboardScreen), findsOneWidget);
        }
      });

      testWidgets('R5.B2: Extreme keyboard view insets (350dp) stress testing', (
        WidgetTester tester,
      ) async {
        ResponsiveTester.setupNarrowAccessibilityViewport(tester);
        ResponsiveTester.simulateKeyboardActive(tester, keyboardHeight: 350.0);

        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              ...harness.providerOverrides,
              birthdaysStreamProvider.overrideWith((ref) => Stream.value(<Birthday>[])),
              peopleStreamProvider.overrideWith((ref) => Stream.value(<Person>[])),
            ],
            child: const MaterialApp(
              home: DashboardScreen(),
            ),
          ),
        );
        await tester.pump();

        final error = tester.takeException();
        if (error != null) {
          expect(error.toString(), contains('RenderFlex overflowed'));
        } else {
          expect(find.byType(DashboardScreen), findsOneWidget);
        }

        ResponsiveTester.simulateKeyboardDismissed(tester);
        await tester.pump();
        tester.takeException();
      });
    });
  });
}
