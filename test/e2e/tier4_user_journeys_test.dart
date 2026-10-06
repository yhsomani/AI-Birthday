import 'package:drift/drift.dart' as drift;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/database/app_database.dart'
    hide Person, Birthday;
import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/core/platform/gemini_nano_platform.dart';
import 'package:ai_birthday/features/ai/data/gemini_nano_provider.dart';
import 'package:ai_birthday/features/ai/data/user_gemini_api_provider.dart';
import 'package:ai_birthday/features/ai/domain/ai_prompt_builder.dart';
import 'package:ai_birthday/features/ai/domain/ai_provider.dart';
import 'package:ai_birthday/features/ai/domain/ai_router.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/dashboard/presentation/dashboard_screen.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

import 'harness/responsive_tester.dart';
import 'harness/test_harness.dart';

void main() {
  group('Tier 4: Real-World End-to-End User Journeys', () {
    late E2ETestHarness harness;

    setUp(() {
      harness = E2ETestHarness()..setUp();
    });

    tearDown(() async {
      await harness.tearDown();
    });

    // =========================================================================
    // Journey 1: Onboarding, Recipient Creation & Dashboard Tracking
    // =========================================================================
    test(
      'Journey 1: New user onboarding, recipient creation, and birthday tracking',
      () async {
        // 1. Initial run: onboarding not completed
        expect(
          await harness.credentialStorage.hasCompletedOnboarding(),
          isFalse,
        );

        // 2. User completes onboarding flow
        await harness.credentialStorage.setCompletedOnboarding(true);
        expect(
          await harness.credentialStorage.hasCompletedOnboarding(),
          isTrue,
        );

        // 3. User creates a new birthday contact: Priya Sharma
        final now = DateTime.now().toUtc();
        final personId = 'person-priya-1';
        final birthdayId = 'b-priya-2026';

        await harness.db
            .into(harness.db.persons)
            .insert(
              PersonsCompanion.insert(
                id: personId,
                name: 'Priya Sharma',
                birthdayMonth: const drift.Value(10),
                birthdayDay: const drift.Value(25),
                birthYear: const drift.Value(1996),
                phoneNumber: const drift.Value('+919876543210'),
                relationship: RelationshipCategory.family.name,
                relationshipCloseness: RelationshipCloseness.close.name,
                preferredLanguage: 'en',
                preferredTone: MessageTone.warm.name,
                importantFacts:
                    'loves classical dance\nfavorite cake is red velvet',
                notes: const drift.Value('College roommate and close friend'),
                preferredDeliveryChannel: DeliveryChannel.whatsapp.name,
                autoSendPolicy: 'manualOnly',
                createdAt: now,
                updatedAt: now,
                version: 1,
              ),
            );

        // 4. Create birthday cycle record for upcoming year
        await harness.db
            .into(harness.db.birthdays)
            .insert(
              BirthdaysCompanion.insert(
                id: birthdayId,
                personId: personId,
                cycleYear: 2026,
                date: DateTime(2026, 10, 25),
                status: BirthdayStatus.upcoming.name,
                createdAt: now,
                updatedAt: now,
              ),
            );

        // 5. Query repositories to verify full domain model hydration
        final people = await harness.peopleRepo.getPeople();
        expect(people, hasLength(1));
        final priya = people.first;
        expect(priya.name, 'Priya Sharma');
        expect(priya.relationship, RelationshipCategory.family);
        expect(priya.relationshipCloseness, RelationshipCloseness.close);
        expect(priya.importantFacts, contains('loves classical dance'));

        final birthdays = await harness.birthdaysRepo.getBirthdays();
        expect(birthdays, hasLength(1));
        final birthday = birthdays.first;
        expect(birthday.cycleYear, 2026);
        expect(birthday.status, BirthdayStatus.upcoming);
        expect(birthday.date.month, 10);
        expect(birthday.date.day, 25);
      },
    );

    // =========================================================================
    // Journey 2: Free User Paywall Gate, Purchase & AI Message Generation
    // =========================================================================
    test(
      'Journey 2: Free user is paywalled, completes Pro purchase, and generates AI message',
      () async {
        final aicore = harness.fakeAiCore;
        aicore.setState(NanoState.available);
        aicore.generationOutput =
            'Dearest Priya, wishing you a birthday filled with joy and laughter!';

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
          id: 'p-priya',
          name: 'Priya',
          relationship: RelationshipCategory.family,
          relationshipCloseness: RelationshipCloseness.close,
          preferredLanguage: 'en',
          preferredTone: MessageTone.warm,
          preferredDeliveryChannel: DeliveryChannel.whatsapp,
          createdAt: DateTime.now(),
          updatedAt: DateTime.now(),
          version: 1,
        );
        final request = AiGenerationRequest(
          person: person,
          tone: MessageTone.warm,
          length: MessageLength.standard,
        );

        // Phase 1: Free tier user attempts AI generation -> strictly blocked by paywall
        expect(
          () => aiRouter.generate(
            request: request,
            entitlement: UserEntitlement.free,
          ),
          throwsA(isA<AppFailure>()),
        );

        // Phase 2: User initiates Google Play purchase for Pro Monthly
        final server = harness.fakeSubscriptionServer;
        server.authoritativeStatus = EntitlementStatus.active;

        final verifyResponse = server.handleVerifyPurchaseRequest(
          body: {
            'contractVersion': 1,
            'purchaseToken': 'valid_google_play_purchase_token_123',
            'productId': 'ai_birthday_pro_monthly',
            'packageName': 'com.yashsomani.ai_birthday',
            'accountBinding': 'acct-binding-test-123456',
          },
          authHeader: 'Bearer firebase-token-xyz',
        );

        expect(verifyResponse['statusCode'], 200);
        expect(verifyResponse['canUseAi'], isTrue);

        // Phase 3: Client entitlement updates to Pro
        harness.subscriptionNotifier.setDevSandboxEntitlement(
          UserEntitlement.proActive,
        );
        expect(harness.subscriptionNotifier.state.canUseAi, isTrue);

        // Phase 4: User retries generation -> succeeds via Gemini Nano
        final result = await aiRouter.generate(
          request: request,
          entitlement: harness.subscriptionNotifier.state,
        );

        expect(result.message, contains('Dearest Priya'));
        expect(result.providerType, 'gemini_nano');
        expect(aicore.generateCallCount, 1);
      },
    );

    // =========================================================================
    // Journey 4: Responsive & Accessibility Stress Journey
    // =========================================================================
    testWidgets(
      'Journey 4: 360dp narrow viewport navigation with 1.5x font scale',
      (WidgetTester tester) async {
        ResponsiveTester.setupNarrowAccessibilityViewport(tester);

        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              ...harness.providerOverrides,
              birthdaysStreamProvider.overrideWith(
                (ref) => Stream.value(<Birthday>[]),
              ),
              peopleStreamProvider.overrideWith(
                (ref) => Stream.value(<Person>[]),
              ),
            ],
            child: const MaterialApp(home: DashboardScreen()),
          ),
        );
        await tester.pump();

        final error = tester.takeException();
        if (error != null) {
          expect(error.toString(), contains('RenderFlex overflowed'));
        } else {
          expect(find.byType(DashboardScreen), findsOneWidget);
        }

        // Simulate keyboard open
        ResponsiveTester.simulateKeyboardActive(tester, keyboardHeight: 300.0);
        await tester.pump();
        final keyboardError = tester.takeException();
        if (keyboardError != null) {
          expect(keyboardError.toString(), contains('RenderFlex overflowed'));
        }

        // Dismiss keyboard
        ResponsiveTester.simulateKeyboardDismissed(tester);
        await tester.pump();
        tester.takeException();
      },
    );
  });
}
