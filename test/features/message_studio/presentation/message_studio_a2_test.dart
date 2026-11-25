/// Message Studio audit A2 regressions: lifecycle autosave vs queued AI result
/// (A2-001), neutral dropped-result reporting (A2-002), no empty drafts
/// (A2-003), optimistic revision conflicts (A2-004), handoff evidence failures
/// (A2-011) and resilient variations (A2-012).
library;

import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:crypto/crypto.dart' as crypto;
import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/database/drift_repositories.dart';
import 'package:ai_birthday/features/ai/data/user_gemini_api_provider.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/delivery/data/native_share_service.dart';
import 'package:ai_birthday/features/delivery/data/sms_delivery_service.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/jobs/data/jobs_repository.dart';
import 'package:ai_birthday/features/jobs/domain/job.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';
import 'package:ai_birthday/features/message_studio/presentation/message_studio_screen.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

import '../../../e2e/harness/test_harness.dart';

const _aiPayload =
    '{"candidates":[{"content":{"parts":[{"text":"AI drafted message"}]}}]}';

class _OkSms extends SmsDeliveryService {
  @override
  Future<bool> sendSms({
    required String? phoneNumber,
    required String message,
  }) async => true;
}

class _OkShare extends NativeShareService {
  @override
  Future<bool> shareText({required String text, String? title}) async => true;
}

class _FailingBirthdayRepository extends DriftBirthdaysRepository {
  _FailingBirthdayRepository(super.database);

  @override
  Future<Birthday?> getBirthday(String id) async {
    throw StateError('database unavailable');
  }
}

/// Evidence repository whose handoff write can be made to fail after launch.
class _FlakyEventsRepository extends DriftDeliveryEventsRepository {
  _FlakyEventsRepository(super.database);

  bool failHandoff = true;
  int handoffCalls = 0;

  @override
  Future<void> recordHandoffAndMarkHandedOff({
    required String birthdayId,
    required DeliveryChannel channel,
    required DateTime at,
    String? draftId,
    int? draftRevision,
    String? bodyHash,
  }) async {
    handoffCalls++;
    if (failHandoff) throw StateError('disk full');
    await super.recordHandoffAndMarkHandedOff(
      birthdayId: birthdayId,
      channel: channel,
      at: at,
      draftId: draftId,
      draftRevision: draftRevision,
      bodyHash: bodyHash,
    );
  }
}

void main() {
  group('Message Studio audit A2', () {
    late E2ETestHarness harness;

    setUp(() => harness = E2ETestHarness()..setUp());
    tearDown(() => harness.tearDown());

    UserGeminiApiProvider gemini({
      Future<void> Function()? beforeRespond,
      Future<HttpResponsePayload> Function(int call)? respond,
    }) {
      var call = 0;
      return UserGeminiApiProvider(
        credentialStorage: harness.credentialStorage,
        httpSender: (uri, headers, body) async {
          final n = ++call;
          if (beforeRespond != null) await beforeRespond();
          if (respond != null) return respond(n);
          return const HttpResponsePayload(statusCode: 200, body: _aiPayload);
        },
      );
    }

    Future<void> pumpStudio(
      WidgetTester tester, {
      List<Override> extraOverrides = const [],
      MessageDraft? seedDraft,
      DeliveryChannel preferredChannel = DeliveryChannel.whatsapp,
      bool seedFailedAiJob = false,
    }) async {
      await tester.pumpWidget(const SizedBox.shrink());
      final now = DateTime.now();
      await harness.peopleRepo.savePerson(
        Person(
          id: 'p-test',
          name: 'Test Person',
          birthdayMonth: now.month,
          birthdayDay: now.day,
          birthYear: 1990,
          phoneNumber: '+14155552671',
          relationship: RelationshipCategory.friend,
          relationshipCloseness: RelationshipCloseness.close,
          preferredLanguage: 'en',
          preferredTone: MessageTone.warm,
          preferredDeliveryChannel: preferredChannel,
          createdAt: now,
          updatedAt: now,
          version: 1,
        ),
      );
      await harness.birthdaysRepo.saveBirthday(
        Birthday(
          id: 'b-test',
          personId: 'p-test',
          cycleYear: now.year,
          date: now,
          status: BirthdayStatus.messageNotPrepared,
          createdAt: now,
          updatedAt: now,
        ),
      );
      if (seedFailedAiJob) {
        final jobs = DriftJobsRepository(harness.db);
        final job = await jobs.enqueue(
          type: JobTypes.aiGenerate,
          subjectId: 'b-test',
          payload: '{}',
        );
        await jobs.claimNext(DateTime.now());
        await jobs.markFailed(
          job.id,
          expectedAttempt: 1,
          errorCode: 'oldError',
          errorMessage: 'Old generation failed.',
        );
      }
      if (seedDraft != null) {
        await harness.draftsRepo.saveDraftAndUpdateBirthday(
          seedDraft,
          BirthdayStatus.messageDrafted,
        );
      }
      harness.subscriptionNotifier.setDevSandboxEntitlement(
        UserEntitlement.proActive,
      );
      await harness.credentialStorage.saveGeminiApiKey('AIzaSyTestKey');

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

    MessageDraft seed(String body) {
      final at = DateTime.now().subtract(const Duration(minutes: 5));
      return MessageDraft(
        id: 'b-test',
        birthdayId: 'b-test',
        personId: 'p-test',
        body: body,
        createdAt: at,
        updatedAt: at,
      );
    }

    Finder editorFinder() =>
        find.byWidgetPredicate((w) => w is TextField && w.maxLines == 5);

    // Legal lifecycle path: resumed -> inactive -> hidden -> paused.
    Future<void> background(WidgetTester tester) async {
      for (final state in [
        AppLifecycleState.inactive,
        AppLifecycleState.hidden,
        AppLifecycleState.paused,
      ]) {
        tester.binding.handleAppLifecycleStateChanged(state);
      }
      await tester.pump(const Duration(milliseconds: 100));
    }

    Future<void> foreground(WidgetTester tester) async {
      for (final state in [
        AppLifecycleState.hidden,
        AppLifecycleState.inactive,
        AppLifecycleState.resumed,
      ]) {
        tester.binding.handleAppLifecycleStateChanged(state);
      }
      await tester.pump(const Duration(milliseconds: 100));
    }

    Future<void> bounded(WidgetTester tester, bool Function() until) async {
      for (var i = 0; i < 40 && !until(); i++) {
        await tester.pump(const Duration(milliseconds: 50));
      }
    }

    testWidgets(
      'A2-001/A2-003: lifecycle pause with an empty editor persists nothing '
      'and does not discard a queued AI result',
      (tester) async {
        final gate = Completer<void>();
        await pumpStudio(
          tester,
          extraOverrides: [
            userGeminiApiProvider.overrideWithValue(
              gemini(beforeRespond: () => gate.future),
            ),
          ],
        );

        await tester.ensureVisible(find.text('Generate with AI'));
        await tester.tap(find.text('Generate with AI'));
        await bounded(
          tester,
          () => find.text('Drafting...').evaluate().isNotEmpty,
        );

        await background(tester);
        await foreground(tester);
        expect(await harness.draftsRepo.getAllDrafts(), isEmpty);

        gate.complete();
        await tester.pumpAndSettle();

        final drafts = await harness.draftsRepo.getAllDrafts();
        expect(drafts, hasLength(1));
        expect(drafts.single.body, 'AI drafted message');
        expect(
          tester.widget<TextField>(editorFinder()).controller!.text,
          'AI drafted message',
        );
        expect(find.text('Message drafted with AI ✨'), findsOneWidget);
      },
    );

    testWidgets(
      'A2-001: lifecycle flush of an unchanged saved draft is a no-op '
      '(AI result still applies)',
      (tester) async {
        final gate = Completer<void>();
        await pumpStudio(
          tester,
          seedDraft: seed('Saved text'),
          extraOverrides: [
            userGeminiApiProvider.overrideWithValue(
              gemini(beforeRespond: () => gate.future),
            ),
          ],
        );
        final before = (await harness.draftsRepo.getDraft('b-test'))!;

        await tester.ensureVisible(find.text('Generate with AI'));
        await tester.tap(find.text('Generate with AI'));
        await bounded(
          tester,
          () => find.text('Drafting...').evaluate().isNotEmpty,
        );

        await background(tester);
        final mid = (await harness.draftsRepo.getDraft('b-test'))!;
        expect(
          mid.revision,
          before.revision,
          reason: 'no-op flush must not write',
        );
        expect(mid.updatedAt, before.updatedAt);

        gate.complete();
        await tester.pumpAndSettle();

        expect(
          (await harness.draftsRepo.getDraft('b-test'))!.body,
          'AI drafted message',
        );
        expect(
          tester.widget<TextField>(editorFinder()).controller!.text,
          'AI drafted message',
        );
      },
    );

    testWidgets('A2-003: opening Studio and pausing creates no draft', (
      tester,
    ) async {
      await pumpStudio(tester);
      await background(tester);

      expect(await harness.draftsRepo.getAllDrafts(), isEmpty);
      expect(
        (await harness.birthdaysRepo.getBirthday('b-test'))!.status,
        BirthdayStatus.messageNotPrepared,
      );
    });

    testWidgets(
      'A2-002: a dropped AI result says "not applied", never success',
      (tester) async {
        final gate = Completer<void>();
        await pumpStudio(
          tester,
          extraOverrides: [
            userGeminiApiProvider.overrideWithValue(
              gemini(beforeRespond: () => gate.future),
            ),
          ],
        );
        await tester.ensureVisible(find.text('Generate with AI'));
        await tester.tap(find.text('Generate with AI'));
        await bounded(
          tester,
          () => find.text('Drafting...').evaluate().isNotEmpty,
        );

        // Another writer saves a draft while the request is in flight.
        await harness.draftsRepo.saveDraftAndUpdateBirthday(
          seed('Written elsewhere'),
          BirthdayStatus.messageDrafted,
        );
        gate.complete();
        await tester.pumpAndSettle();

        expect(find.textContaining('AI result not applied'), findsOneWidget);
        expect(find.text('Message drafted with AI ✨'), findsNothing);
        expect(
          (await harness.draftsRepo.getDraft('b-test'))!.body,
          'Written elsewhere',
        );
        expect(find.text('Generate with AI'), findsOneWidget);
      },
    );

    testWidgets(
      'A2-004: autosave never overwrites a newer stored revision; text is kept',
      (tester) async {
        await pumpStudio(tester, seedDraft: seed('Original'));

        await tester.ensureVisible(editorFinder());
        await tester.enterText(editorFinder(), 'My unsaved typing');

        // Another writer lands a newer revision before the debounce fires.
        final stored = (await harness.draftsRepo.getDraft('b-test'))!;
        await harness.draftsRepo.saveDraftAndUpdateBirthday(
          stored.copyWith(body: 'Newer stored text'),
          BirthdayStatus.messageDrafted,
        );

        await tester.pump(const Duration(milliseconds: 900));
        await tester.pumpAndSettle();

        expect(
          (await harness.draftsRepo.getDraft('b-test'))!.body,
          'Newer stored text',
          reason: 'stale autosave must be rejected',
        );
        expect(
          tester.widget<TextField>(editorFinder()).controller!.text,
          'My unsaved typing',
        );
        expect(find.textContaining('changed elsewhere'), findsOneWidget);
        expect(find.text('Save now'), findsOneWidget);

        // Explicit Save now knowingly keeps the user's version.
        await tester.tap(find.text('Save now'));
        await tester.pumpAndSettle();
        expect(
          (await harness.draftsRepo.getDraft('b-test'))!.body,
          'My unsaved typing',
        );
      },
    );

    testWidgets('A2-025: an old failed job is not replayed on re-entry', (
      tester,
    ) async {
      await pumpStudio(tester, seedFailedAiJob: true);

      expect(find.text('Old generation failed.'), findsNothing);
      expect(find.text('Generate with AI'), findsOneWidget);
    });

    testWidgets('A2-023: repository load failures leave the spinner state', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await pumpStudio(
        tester,
        extraOverrides: [
          birthdaysRepositoryProvider.overrideWithValue(
            _FailingBirthdayRepository(harness.db),
          ),
        ],
      );

      expect(find.byType(CircularProgressIndicator), findsNothing);
      expect(
        find.text('Could not load this birthday. Try again.'),
        findsOneWidget,
      );
      expect(find.text('Try Again'), findsOneWidget);
      expect(
        tester
            .getSemantics(
              find.bySemanticsLabel('Could not load this birthday. Try again.'),
            )
            .flagsCollection
            .isLiveRegion,
        isTrue,
      );
      semantics.dispose();
    });

    testWidgets('A2-030: preferred SMS action is not duplicated', (
      tester,
    ) async {
      await pumpStudio(tester, preferredChannel: DeliveryChannel.sms);
      expect(find.text('Send via SMS'), findsOneWidget);
    });

    testWidgets('A2-030: preferred share action is not duplicated', (
      tester,
    ) async {
      await pumpStudio(tester, preferredChannel: DeliveryChannel.share);
      expect(find.text('Open Share Sheet'), findsOneWidget);
      expect(find.text('Share Sheet'), findsNothing);
    });

    testWidgets('A2-030: preferred clipboard action is not duplicated', (
      tester,
    ) async {
      await pumpStudio(tester, preferredChannel: DeliveryChannel.clipboard);
      expect(find.text('Copy Message'), findsOneWidget);
      expect(find.text('Copy Text'), findsNothing);
    });

    testWidgets('A2-024: failed autosave offers an explicit discard path', (
      tester,
    ) async {
      await pumpStudio(tester, seedDraft: seed('Original'));
      await tester.ensureVisible(editorFinder());
      await tester.enterText(editorFinder(), 'Unsaved text');
      final stored = (await harness.draftsRepo.getDraft('b-test'))!;
      await harness.draftsRepo.saveDraftAndUpdateBirthday(
        stored.copyWith(body: 'Changed elsewhere'),
        BirthdayStatus.messageDrafted,
      );
      await tester.pump(const Duration(milliseconds: 900));
      await tester.pumpAndSettle();

      expect(find.text('Discard edits'), findsOneWidget);
      await tester.tap(find.text('Discard edits'));
      await tester.pumpAndSettle();
      expect(find.text('Discard unsaved changes?'), findsOneWidget);
      await tester.tap(find.text('Discard edits').last);
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    });

    for (final channel in ['SMS', 'Share']) {
      testWidgets(
        'A2-011: $channel evidence-write failure is reported separately and retryable',
        (tester) async {
          final events = _FlakyEventsRepository(harness.db);
          await pumpStudio(
            tester,
            extraOverrides: [
              deliveryEventsRepositoryProvider.overrideWithValue(events),
              smsDeliveryServiceProvider.overrideWithValue(_OkSms()),
              nativeShareServiceProvider.overrideWithValue(_OkShare()),
            ],
          );
          await tester.ensureVisible(editorFinder());
          await tester.enterText(editorFinder(), 'Happy birthday!');

          final label = channel == 'SMS' ? 'Send via SMS' : 'Share Sheet';
          await tester.ensureVisible(find.text(label));
          await tester.tap(find.text(label));
          await tester.pumpAndSettle();

          expect(tester.takeException(), isNull);
          expect(
            find.textContaining(
              'was opened, but the handoff could not be recorded',
            ),
            findsOneWidget,
          );
          expect(find.text('Could not open the delivery app.'), findsNothing);
          expect(find.text('Did you send the message?'), findsNothing);

          events.failHandoff = false;
          await tester.tap(find.text('Retry record'));
          await tester.pumpAndSettle();

          expect(events.handoffCalls, 2);
          expect(find.text('Did you send the message?'), findsOneWidget);
          expect(
            (await harness.birthdaysRepo.getBirthday('b-test'))!.status,
            BirthdayStatus.handedOff,
          );
          final handoff = await events.latestHandoffForBirthday('b-test');
          expect(
            handoff?.bodyHash,
            crypto.sha256.convert(utf8.encode('Happy birthday!')).toString(),
          );
          expect(handoff?.draftRevision, greaterThan(0));
        },
      );
    }

    testWidgets(
      'A2-012: one failing variation does not discard the others and calls '
      'never overlap',
      (tester) async {
        var inFlight = 0;
        var maxInFlight = 0;
        await pumpStudio(
          tester,
          extraOverrides: [
            userGeminiApiProvider.overrideWithValue(
              gemini(
                beforeRespond: () async {
                  inFlight++;
                  if (inFlight > maxInFlight) maxInFlight = inFlight;
                  await Future<void>.delayed(const Duration(milliseconds: 5));
                  inFlight--;
                },
                respond: (call) async => call == 2
                    ? const HttpResponsePayload(statusCode: 503, body: 'down')
                    : const HttpResponsePayload(
                        statusCode: 200,
                        body: _aiPayload,
                      ),
              ),
            ),
          ],
        );
        await tester.ensureVisible(find.text('Variations'));
        await tester.tap(find.text('Variations'));
        await tester.pumpAndSettle();

        expect(find.text('Select a Variation'), findsOneWidget);
        expect(find.text('AI drafted message'), findsNWidgets(2));
        expect(maxInFlight, 1);
      },
    );
  });
}
