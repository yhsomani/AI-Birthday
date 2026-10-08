import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/features/ai/data/user_gemini_api_provider.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/delivery/data/sms_delivery_service.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/message_studio/presentation/message_studio_screen.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

import '../../../e2e/harness/test_harness.dart';

/// SMS service that records invocation count and can hold the launch in
/// flight to simulate the real slow window between tap and app open.
class RecordingSmsService extends SmsDeliveryService {
  RecordingSmsService();

  int sendCount = 0;
  Completer<void>? gate;

  @override
  Future<bool> sendSms({
    required String? phoneNumber,
    required String message,
  }) {
    sendCount++;
    final g = gate;
    if (g != null) {
      return g.future.then((_) => true);
    }
    return Future.value(true);
  }
}

const _aiPayload =
    '{"candidates":[{"content":{"parts":[{"text":"AI drafted message"}]}}]}';

void main() {
  group('Message Studio optimistic state (audit 06)', () {
    late E2ETestHarness harness;

    setUp(() => harness = E2ETestHarness()..setUp());
    tearDown(() => harness.tearDown());

    UserGeminiApiProvider okGemini({Future<void> Function()? beforeRespond}) {
      return UserGeminiApiProvider(
        credentialStorage: harness.credentialStorage,
        httpSender: (uri, headers, body) async {
          if (beforeRespond != null) await beforeRespond();
          return const HttpResponsePayload(statusCode: 200, body: _aiPayload);
        },
      );
    }

    Future<void> pumpStudio(
      WidgetTester tester, {
      required String? phone,
      List<Override> extraOverrides = const [],
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
        status: BirthdayStatus.messageNotPrepared,
        createdAt: now,
        updatedAt: now,
      );
      await harness.peopleRepo.savePerson(person);
      await harness.birthdaysRepo.saveBirthday(birthday);
      harness.subscriptionNotifier.setDevSandboxEntitlement(
        UserEntitlement.proActive,
      );

      tester.view.physicalSize = const Size(800, 2000);
      tester.view.devicePixelRatio = 1.0;
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

    Finder editorFinder() =>
        find.byWidgetPredicate((w) => w is TextField && w.maxLines == 5);

    testWidgets(
      'autosave + generate converge on one idempotent draft row (no orphans)',
      (tester) async {
        await harness.credentialStorage.saveGeminiApiKey('AIzaSyTestKey');
        await pumpStudio(
          tester,
          phone: '+14155552671',
          extraOverrides: [
            userGeminiApiProvider.overrideWithValue(okGemini()),
          ],
        );

        await tester.ensureVisible(editorFinder());
        await tester.enterText(editorFinder(), 'Happy birthday friend!');
        // Autosave fires after the 750ms debounce.
        await tester.pump(const Duration(milliseconds: 900));
        await tester.pumpAndSettle();

        var drafts = await harness.draftsRepo.getAllDrafts();
        expect(drafts, hasLength(1));
        expect(drafts.single.id, 'b-test');

        // Generate now: it must update the same row, never create an orphan
        // with a fresh timestamp id.
        await tester.ensureVisible(find.text('Generate with AI'));
        await tester.tap(find.text('Generate with AI'));
        await tester.pumpAndSettle();

        drafts = await harness.draftsRepo.getAllDrafts();
        expect(drafts, hasLength(1));
        expect(drafts.single.id, 'b-test');
        expect(drafts.single.body, 'AI drafted message');
        expect(find.text('AI drafted message'), findsOneWidget);
      },
    );

    testWidgets(
      'double-tap on SMS fires exactly one send, one handoff, one dialog',
      (tester) async {
        final sms = RecordingSmsService()..gate = Completer<void>();
        await pumpStudio(
          tester,
          phone: '+14155552671',
          extraOverrides: [smsDeliveryServiceProvider.overrideWithValue(sms)],
        );

        await tester.ensureVisible(editorFinder());
        await tester.enterText(editorFinder(), 'Happy birthday!');
        await tester.pump(const Duration(milliseconds: 900));
        await tester.pumpAndSettle();

        final sendButton = find.text('Send via SMS');
        await tester.ensureVisible(sendButton);

        // Two taps in the same frame: the second must be swallowed by the
        // single-flight guard (the real slow window is the SMS app opening).
        await tester.tap(sendButton);
        await tester.tap(sendButton, warnIfMissed: false);
        sms.gate!.complete();
        await tester.pumpAndSettle();

        expect(sms.sendCount, 1, reason: 'duplicate tap must not send twice');
        final events = await (harness.db.select(
          harness.db.deliveryEvents,
        )..where((r) => r.birthdayId.equals('b-test'))).get();
        expect(events, hasLength(1));
        final birthday = await harness.birthdaysRepo.getBirthday('b-test');
        expect(birthday!.status, BirthdayStatus.handedOff);
        expect(find.text('Did you send the message?'), findsOneWidget);
      },
    );

    testWidgets('stale AI result never overwrites edits made while drafting', (
      tester,
    ) async {
      final gate = Completer<void>();
      await harness.credentialStorage.saveGeminiApiKey('AIzaSyTestKey');
      await pumpStudio(
        tester,
        phone: '+14155552671',
        extraOverrides: [
          userGeminiApiProvider.overrideWithValue(
            okGemini(beforeRespond: () => gate.future),
          ),
        ],
      );

      await tester.tap(find.text('Generate with AI'));
      await tester.pump();
      expect(find.text('Drafting...'), findsOneWidget);

      // The user keeps typing while the (slow) request is in flight.
      await tester.ensureVisible(editorFinder());
      await tester.enterText(editorFinder(), 'My manual edit while generating');
      await tester.pump();

      gate.complete();
      await tester.pumpAndSettle();

      final field = tester.widget<TextField>(editorFinder());
      expect(
        field.controller!.text,
        'My manual edit while generating',
        reason: 'the stale AI text must not clobber the newer user edit',
      );
      expect(
        find.textContaining('You edited the message while drafting'),
        findsOneWidget,
      );
      // Busy state cleared; the generate affordance is usable again.
      expect(find.text('Generate with AI'), findsOneWidget);
      expect(find.text('Drafting...'), findsNothing);

      // The pending autosave from the user edit lands after the debounce.
      await tester.pump(const Duration(milliseconds: 900));
      await tester.pumpAndSettle();

      // And the user's text — not the dropped AI text — is what persisted.
      final drafts = await harness.draftsRepo.getAllDrafts();
      expect(drafts, hasLength(1));
      expect(drafts.single.body, 'My manual edit while generating');
    });
  });
}
