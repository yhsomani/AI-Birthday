import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/platform/gemini_nano_platform.dart';
import 'package:ai_birthday/features/ai/data/user_gemini_api_provider.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';
import 'package:ai_birthday/features/message_studio/presentation/message_studio_screen.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/features/settings/presentation/settings_screen.dart';
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
      DeliveryChannel channel = DeliveryChannel.whatsapp,
      int month = 0,
      int day = 0,
      MessageDraft? draft,
      bool linkDraftToBirthday = true,
      List<Override> extraOverrides = const [],
      Size size = const Size(800, 2000),
      double textScale = 1.0,
      bool useRouter = false,
    }) async {
      final now = DateTime.now();
      final person = Person(
        id: 'p-test',
        name: 'Test Person',
        birthdayMonth: month == 0 ? now.month : month,
        birthdayDay: day == 0 ? now.day : day,
        birthYear: 1990,
        phoneNumber: phone,
        relationship: RelationshipCategory.friend,
        relationshipCloseness: RelationshipCloseness.close,
        preferredLanguage: 'en',
        preferredTone: MessageTone.warm,
        preferredDeliveryChannel: channel,
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
        draftId: linkDraftToBirthday ? draft?.id : null,
        createdAt: now,
        updatedAt: now,
      );
      await harness.peopleRepo.savePerson(person);
      await harness.birthdaysRepo.saveBirthday(birthday);
      if (draft != null) await harness.draftsRepo.saveDraft(draft);
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

      final router = useRouter
          ? GoRouter(
              initialLocation: '/message-studio/b-test',
              routes: [
                GoRoute(
                  path: '/message-studio/:birthdayId',
                  builder: (context, state) => MessageStudioScreen(
                    birthdayId: state.pathParameters['birthdayId']!,
                  ),
                ),
                GoRoute(
                  path: '/settings',
                  builder: (context, state) => const SettingsScreen(),
                ),
              ],
            )
          : null;
      if (router != null) addTearDown(router.dispose);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [...harness.providerOverrides, ...extraOverrides],
          child: router == null
              ? const MaterialApp(
                  home: MessageStudioScreen(birthdayId: 'b-test'),
                )
              : MaterialApp.router(routerConfig: router),
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

    testWidgets('preferred SMS becomes the primary action', (tester) async {
      await pumpStudio(
        tester,
        phone: '+14155552671',
        channel: DeliveryChannel.sms,
      );

      final smsText = find.text('Send via SMS');
      expect(smsText, findsNWidgets(2));
      final primary = tester.widget<FilledButton>(
        find.ancestor(of: smsText.first, matching: find.byType(FilledButton)),
      );
      expect(primary, isNotNull);
      expect(find.text('Send on WhatsApp'), findsNothing);
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

    testWidgets(
      'editor field announces its purpose to screen readers (audit 05 W6)',
      (tester) async {
        await pumpStudio(tester, phone: '+14155552671');

        final editor = find.byWidgetPredicate(
          (w) => w is TextField && w.maxLines == 5,
        );
        await tester.scrollUntilVisible(
          editor,
          200,
          scrollable: find.byType(Scrollable).first,
        );
        final semantics = tester.getSemantics(editor);
        expect(semantics.label, contains('Your message'));
      },
    );

    testWidgets('direct Studio route is blocked for unresolved leap day', (
      tester,
    ) async {
      await pumpStudio(tester, phone: '+14155552671', month: 2, day: 29);

      expect(find.textContaining('Choose a non-leap-year'), findsOneWidget);
      expect(find.text('Send on WhatsApp'), findsNothing);
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
      'tapping Unlock with Pro opens Settings as modal and pops back cleanly',
      (tester) async {
        await pumpStudio(
          tester,
          phone: '+14155552671',
          pro: false,
          useRouter: true,
        );

        expect(find.text('Unlock with Pro'), findsOneWidget);
        await tester.tap(find.text('Unlock with Pro'));
        await tester.pumpAndSettle();

        expect(find.byType(SettingsScreen), findsOneWidget);
        expect(find.text('Settings'), findsOneWidget);

        // Pop back to MessageStudio
        await tester.pageBack();
        await tester.pumpAndSettle();

        expect(find.byType(SettingsScreen), findsNothing);
        expect(find.byType(MessageStudioScreen), findsOneWidget);
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

    testWidgets('editing a confirmed draft creates an unsent revision', (
      tester,
    ) async {
      final now = DateTime.now();
      await pumpStudio(
        tester,
        phone: '+14155552671',
        status: BirthdayStatus.completed,
        draft: MessageDraft(
          id: 'b-test',
          birthdayId: 'b-test',
          personId: 'p-test',
          body: 'Immutable confirmed copy',
          status: DraftStatus.confirmedSent,
          createdAt: now.subtract(const Duration(minutes: 2)),
          updatedAt: now.subtract(const Duration(minutes: 1)),
        ),
      );

      final editor = find.byWidgetPredicate(
        (widget) => widget is TextField && widget.maxLines == 5,
      );
      await tester.enterText(editor, 'A later revision that is not confirmed');
      await tester.pump(const Duration(milliseconds: 900));
      await tester.pumpAndSettle();

      final rows = await harness.db.select(harness.db.messageDrafts).get();
      expect(rows, hasLength(2));
      final original = rows.singleWhere((row) => row.id == 'b-test');
      expect(original.body, 'Immutable confirmed copy');
      expect(original.status, DraftStatus.confirmedSent.name);
      final revision = rows.singleWhere((row) => row.id != 'b-test');
      expect(revision.body, 'A later revision that is not confirmed');
      expect(revision.status, DraftStatus.draft.name);
    });

    testWidgets(
      'rollover with null draft link preserves confirmed canonical row',
      (tester) async {
        final now = DateTime.now();
        await pumpStudio(
          tester,
          phone: '+14155552671',
          status: BirthdayStatus.messageNotPrepared,
          linkDraftToBirthday: false,
          draft: MessageDraft(
            id: 'b-test',
            birthdayId: 'b-test',
            personId: 'p-test',
            body: 'Last cycle confirmed message',
            status: DraftStatus.confirmedSent,
            createdAt: now.subtract(const Duration(days: 365)),
            updatedAt: now.subtract(const Duration(days: 365)),
          ),
        );

        final editor = find.byWidgetPredicate(
          (widget) => widget is TextField && widget.maxLines == 5,
        );
        await tester.enterText(editor, 'Next cycle message');
        await tester.pump(const Duration(milliseconds: 900));
        await tester.pumpAndSettle();

        final rows = await harness.db.select(harness.db.messageDrafts).get();
        expect(rows, hasLength(2));
        final canonical = rows.singleWhere((row) => row.id == 'b-test');
        expect(canonical.body, 'Last cycle confirmed message');
        expect(canonical.status, DraftStatus.confirmedSent.name);
        final revision = rows.singleWhere((row) => row.id != 'b-test');
        expect(revision.body, 'Next cycle message');
        final birthday = await harness.birthdaysRepo.getBirthday('b-test');
        expect(birthday?.draftId, revision.id);
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
        expect(find.text('Yes, I Sent It'), findsOneWidget);
        expect(find.text('Not Sent Yet'), findsOneWidget);

        await tester.tap(find.text('Not Sent Yet'));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 20));
        final events = await harness.db.select(harness.db.deliveryEvents).get();
        expect(events, hasLength(1));
        expect(events.single.outcome, 'notSent');
      },
    );
  });
}
