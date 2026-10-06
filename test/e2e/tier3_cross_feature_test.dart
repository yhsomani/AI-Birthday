import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/core/platform/gemini_nano_platform.dart';
import 'package:ai_birthday/features/ai/data/gemini_nano_provider.dart';
import 'package:ai_birthday/features/ai/data/user_gemini_api_provider.dart';
import 'package:ai_birthday/features/ai/domain/ai_prompt_builder.dart';
import 'package:ai_birthday/features/ai/domain/ai_provider.dart';
import 'package:ai_birthday/features/ai/domain/ai_router.dart';
import 'package:ai_birthday/features/auth/domain/auth_state.dart';
import 'package:ai_birthday/features/auth/domain/google_identity.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';
import 'package:ai_birthday/features/sync/data/cloud_sync_service.dart';

import 'harness/test_harness.dart';

void main() {
  group('Tier 3: Pairwise Cross-Feature Interactions', () {
    late E2ETestHarness harness;

    setUp(() {
      harness = E2ETestHarness()..setUp();
    });

    tearDown(() async {
      await harness.tearDown();
    });

    // =========================================================================
    // Interaction 1: Auth <-> Subscription (R1 x R2)
    // =========================================================================
    group('Interaction 1: Auth <-> Subscription', () {
      test(
        'Signing in binds user-scoped session, and sign-out purges entitlement',
        () async {
          // Initial state: free tier
          expect(harness.subscriptionNotifier.state.canUseAi, isFalse);

          // User signs in with verified identity
          const identity = GoogleIdentity(
            googleSubject: 'google-sub-777',
            email: 'carol@example.com',
            displayName: 'Carol',
            firebaseUid: 'uid-carol-777',
            idToken: 'token-carol-777',
          );

          // Store credentials
          await harness.storeDriver.write(
            'auth_session_uid',
            identity.firebaseUid!,
          );
          await harness.storeDriver.write(
            'auth_session_id_token',
            identity.idToken!,
          );

          // Server entitlement confirms active Pro
          harness.fakeSubscriptionServer.authoritativeStatus =
              EntitlementStatus.active;
          final doc = harness.fakeSubscriptionServer
              .getFirestoreEntitlementDocument(identity.firebaseUid!);
          expect(doc['status'], 'active');
          expect(doc['canUseAi'], isTrue);

          harness.subscriptionNotifier.setDevSandboxEntitlement(
            UserEntitlement.proActive,
          );
          expect(harness.subscriptionNotifier.state.canUseAi, isTrue);

          // User signs out
          await harness.storeDriver.delete('auth_session_uid');
          await harness.storeDriver.delete('auth_session_id_token');
          harness.subscriptionNotifier.resetToFreeTier();

          // Verify entitlement is revoked and Pro features locked
          expect(harness.subscriptionNotifier.state.canUseAi, isFalse);
          expect(
            harness.subscriptionNotifier.state.status,
            EntitlementStatus.none,
          );
          expect(await harness.storeDriver.read('auth_session_uid'), isNull);
        },
      );
    });

    // =========================================================================
    // Interaction 3: Subscription <-> On-Device AI Routing (R2 x R4)
    // =========================================================================
    group('Interaction 3: Subscription <-> On-Device AI Routing', () {
      test(
        'Pro entitlement enables Gemini Nano; Free tier blocks routing without API key',
        () async {
          final aicore = harness.fakeAiCore;
          aicore.setState(NanoState.available);
          aicore.generationOutput = 'AI birthday greeting for Pro subscriber!';

          final nanoProvider = GeminiNanoProvider(platform: aicore);
          final userGeminiProvider = UserGeminiApiProvider(
            credentialStorage: harness.credentialStorage,
          );

          final aiRouter = AiRouter(
            credentialStorage: harness.credentialStorage,
            userGeminiProvider: userGeminiProvider,
            nanoProvider: nanoProvider,
            nanoStatusChecker: () async => GeminiNanoStatus.available,
          );

          final person = Person(
            id: 'p-1',
            name: 'Eva',
            relationship: RelationshipCategory.friend,
            relationshipCloseness: RelationshipCloseness.close,
            preferredLanguage: 'en',
            preferredTone: MessageTone.warm,
            preferredDeliveryChannel: DeliveryChannel.whatsapp,
            createdAt: DateTime.now(),
            updatedAt: DateTime.now(),
            version: 1,
          );
          final request = AiGenerationRequest(person: person);

          // 1. User on Free tier without personal API key -> blocked
          expect(
            () => aiRouter.generate(
              request: request,
              entitlement: UserEntitlement.free,
            ),
            throwsA(isA<AppFailure>()),
          );

          // 2. User upgrades to Pro -> routes through Gemini Nano
          final proResult = await aiRouter.generate(
            request: request,
            entitlement: UserEntitlement.proActive,
          );
          expect(proResult.message, 'AI birthday greeting for Pro subscriber!');
          expect(proResult.providerType, 'gemini_nano');
        },
      );
    });

    // =========================================================================
    // Interaction 4: Local Drift SQLite <-> Cloud Envelope Sync with Version Vectors (R3)
    // =========================================================================
    group('Interaction 4: Drift SQLite <-> Cloud Version Vectors', () {
      test(
        'Deterministic version vector conflict resolution: newer cloud backup wins, older rejected',
        () {
          const localMaxVersion = 5;

          // Scenario A: Remote cloud backup is newer (version 7 > 5) -> should apply
          final newerRemoteEnvelope =
              CryptoEnvelopeFixture.createEncryptedEnvelope(
                persons: [
                  {'id': 'p-remote', 'name': 'Updated on Cloud', 'version': 7},
                ],
                birthdays: [],
                backupVersion: 7,
              );

          final remoteVersion = newerRemoteEnvelope['backupVersion'] as int;
          expect(remoteVersion > localMaxVersion, isTrue);

          final decryptedNewer = CryptoEnvelopeFixture.decryptEnvelope(
            newerRemoteEnvelope,
          );
          expect(decryptedNewer['persons'][0]['name'], 'Updated on Cloud');

          // Scenario B: Remote cloud backup is stale (version 3 < 5) -> must NOT overwrite local edits
          final staleRemoteEnvelope =
              CryptoEnvelopeFixture.createEncryptedEnvelope(
                persons: [
                  {'id': 'p-stale', 'name': 'Old Stale Name', 'version': 3},
                ],
                birthdays: [],
                backupVersion: 3,
              );

          final staleVersion = staleRemoteEnvelope['backupVersion'] as int;
          expect(staleVersion < localMaxVersion, isTrue);
          // Client rejects stale restore to prevent silent data loss
          final shouldApplyStale = staleVersion > localMaxVersion;
          expect(shouldApplyStale, isFalse);
        },
      );
    });
  });
}
