import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

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

      tester.view.physicalSize = const Size(800, 2000);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        ProviderScope(
          overrides: harness.providerOverrides,
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
  });
}
