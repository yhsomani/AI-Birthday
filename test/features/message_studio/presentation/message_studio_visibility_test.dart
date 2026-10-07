import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/platform/gemini_nano_platform.dart';
import 'package:ai_birthday/features/ai/data/user_gemini_api_provider.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/message_studio/presentation/message_studio_screen.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

import '../../../e2e/harness/test_harness.dart';

void main() {
  group('Message Studio visibility (audit H, L, M, Q)', () {
    late E2ETestHarness harness;

    setUp(() => harness = E2ETestHarness()..setUp());
    tearDown(() => harness.tearDown());

    Future<void> pumpStudio(
      WidgetTester tester, {
      required String? phone,
      BirthdayStatus status = BirthdayStatus.messageNotPrepared,
      bool pro = true,
      List<Override> extraOverrides = const [],
      Size size = const Size(800, 2000),
      double textScale = 1.0,
    }) async {
      final now = DateTime.now();
      final person = Person(
        id: 'p-test',
        name: 'Test Person',
        birthdayMonth: now.month,
        birthdayDay: now.day,
        birthYear: 1990,
        phoneNumber: phone,
        relationship: RelationshipCategory.friend,
        relationshipCloseness: RelationshipCloseness.close,
        preferredLanguage: 'en',
        preferredTone: MessageTone.warm,
        preferredDeliveryChannel: DeliveryChannel.whatsapp,
        createdAt: now,
        updatedAt: now,
        version: 1,
      );
      final birthday = Birthday(
        id: 'b-test',
        personId: 'p-test',
        cycleYear: now.year,
        date: now,
        status: status,
        createdAt: now,
        updatedAt: now,
      );
      await harness.peopleRepo.savePerson(person);
      await harness.birthdaysRepo.saveBirthday(birthday);
      if (pro) {
        harness.subscriptionNotifier.setDevSandboxEntitlement(
          UserEntitlement.proActive,
        );
      }

      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1.0;
      tester.platformDispatcher.textScaleFactorTestValue = textScale;
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [...harness.providerOverrides, ...extraOverrides],
          child: const MaterialApp(
            home: MessageStudioScreen(birthdayId: 'b-test'),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('valid phone keeps WhatsApp primary and SMS delivery (G)', (
      tester,
    ) async {
      await pumpStudio(tester, phone: '+14155552671');

      expect(find.text('Send on WhatsApp'), findsOneWidget);
      expect(find.text('Send via SMS'), findsOneWidget);
      expect(find.text('Generate with AI'), findsOneWidget);
      expect(find.text('Tone:'), findsOneWidget);
    });

    testWidgets(
      'editor header and status cluster never overflow at 200% on 360dp '
      '(audit 05 P2-1)',
      (tester) async {
        await pumpStudio(
          tester,
          phone: '+14155552671',
          size: const Size(360, 640),
          textScale: 2.0,
        );

        // The 'Your message' header sits below the fold on a small screen
        // (lazy list — scroll until it builds), then confirm zero
        // overflow/exceptions.
        await tester.scrollUntilVisible(
          find.text('Your message'),
          200,
          scrollable: find.byType(Scrollable).first,
        );
        await tester.pumpAndSettle();
        expect(find.text('Your message'), findsOneWidget);
        expect(tester.takeException(), isNull);

        // Typing grows the 'N chars' readout next to the header — still no
        // overflow once the Wrap lets the status cluster fall to its own line.
        await tester.enterText(
          find.byType(TextField).last,
          'Happy birthday to a wonderful friend — hope you have an amazing day!',
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets('missing phone leads with Add Phone Number, SMS hidden (L)', (
      tester,
    ) async {
      await pumpStudio(tester, phone: null);

      expect(find.text('Add Phone Number'), findsOneWidget);
      expect(find.text('Send on WhatsApp'), findsNothing);
      expect(find.text('Send via SMS'), findsNothing);
      expect(find.textContaining('No phone number yet'), findsOneWidget);
      // Working alternatives stay available.
      expect(find.text('Share Sheet'), findsOneWidget);
      expect(find.text('Copy Text'), findsOneWidget);
    });

    testWidgets('unusable phone leads with Fix Phone Number and guidance (M)', (
      tester,
    ) async {
      await pumpStudio(tester, phone: '123');

      expect(find.text('Fix Phone Number'), findsOneWidget);
      expect(find.text('Send on WhatsApp'), findsNothing);
      expect(find.text('Send via SMS'), findsNothing);
      expect(find.textContaining('country code'), findsOneWidget);
    });

    testWidgets(
      'free tier sees Unlock with Pro, AI tools hidden, manual path intact (H)',
      (tester) async {
        await pumpStudio(tester, phone: '+14155552671', pro: false);

        expect(find.text('Unlock with Pro'), findsOneWidget);
        expect(find.text('Generate with AI'), findsNothing);
        expect(find.text('Tone:'), findsNothing);
        expect(find.text('Shorten'), findsNothing);
        expect(find.text('Translate'), findsNothing);
        expect(find.textContaining('Pro feature'), findsOneWidget);
        // Manual writing + delivery still fully available.
        expect(find.text('Your message'), findsOneWidget);
        expect(find.text('Send on WhatsApp'), findsOneWidget);
        expect(find.text('Share Sheet'), findsOneWidget);
      },
    );

    testWidgets(
      'completed birthday leads with View in History, send demoted (Q)',
      (tester) async {
        await pumpStudio(
          tester,
          phone: '+14155552671',
          status: BirthdayStatus.completed,
        );

        expect(find.text('View in History'), findsOneWidget);
        expect(find.text('Send on WhatsApp'), findsNothing);
        expect(find.textContaining('marked as sent'), findsOneWidget);
        // Resending stays intentionally reachable via the alternate channels.
        expect(find.text('Send via SMS'), findsOneWidget);
        expect(find.text('Share Sheet'), findsOneWidget);
      },
    );

    testWidgets(
      'invalid stored key offers on-device fallback that unlocks draft (P1-1)',
      (tester) async {
        harness.fakeAiCore.setState(NanoState.available);
        await harness.credentialStorage.saveGeminiApiKey('AIzaSyDeadKey');
        final failingProvider = UserGeminiApiProvider(
          credentialStorage: harness.credentialStorage,
          httpSender: (uri, headers, body) async {
            return const HttpResponsePayload(
              statusCode: 401,
              body: '{"error":{"message":"API key not valid"}}',
            );
          },
        );

        await pumpStudio(
          tester,
          phone: '+14155552671',
          extraOverrides: [
            userGeminiApiProvider.overrideWithValue(failingProvider),
          ],
        );

        await tester.tap(find.text('Generate with AI'));
        await tester.pumpAndSettle();

        // Credential failure surfaces the error AND the on-device action.
        expect(
          find.textContaining('Gemini API key could not be used'),
          findsOneWidget,
        );
        expect(find.text('Use on-device AI (Gemini Nano)'), findsOneWidget);

        await tester.tap(find.text('Use on-device AI (Gemini Nano)'));
        await tester.pumpAndSettle();

        expect(find.text('Message drafted with AI ✨'), findsOneWidget);
        expect(
          find.text('Happy Birthday! Wishing you a fantastic day!'),
          findsOneWidget,
        );
      },
    );

    testWidgets(
      'handed-off birthday confirms without re-launching (audit 03 AC3)',
      (tester) async {
        await pumpStudio(
          tester,
          phone: '+14155552671',
          status: BirthdayStatus.handedOff,
        );

        // Primary becomes Confirm Sent; the WhatsApp re-send affordance
        // disappears (SMS/Share remain as alternate channels, per Q).
        expect(find.text('Confirm Sent'), findsOneWidget);
        expect(find.text('Send on WhatsApp'), findsNothing);

        await tester.tap(find.text('Confirm Sent'));
        await tester.pumpAndSettle();

        // The dialog asks the user, it never re-opens the external app.
        expect(find.text('Did you send the message?'), findsOneWidget);
        expect(find.text('Yes, Message Sent!'), findsOneWidget);
        expect(find.text('Not Sent Yet'), findsOneWidget);
      },
    );
  });
}
