/// Message Studio durable-generation behavior (background-jobs audit).
///
/// Generation runs as a background job: the tap only enqueues, and the job's
/// durable effect (the drafted row) survives leaving the screen mid-flight.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/features/ai/data/user_gemini_api_provider.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/message_studio/presentation/message_studio_screen.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

import '../../../e2e/harness/test_harness.dart';

const _aiPayload =
    '{"candidates":[{"content":{"parts":[{"text":"AI drafted message"}]}}]}';

/// Host that swaps its child while keeping the same `ProviderScope` alive, so
/// the background worker outlives the studio screen — exactly like real
/// navigation that never tears down the app container.
class SwapHost extends StatefulWidget {
  const SwapHost({required this.overrides, required this.child, super.key});

  final List<Override> overrides;
  final Widget child;

  @override
  State<SwapHost> createState() => _SwapHostState();
}

class _SwapHostState extends State<SwapHost> {
  late Widget _child;

  @override
  void initState() {
    super.initState();
    _child = widget.child;
  }

  void show(Widget child) => setState(() => _child = child);

  @override
  Widget build(BuildContext context) {
    return ProviderScope(
      overrides: widget.overrides,
      child: MaterialApp(home: _child),
    );
  }
}

void main() {
  group('Message Studio durable generation (background-jobs audit)', () {
    late E2ETestHarness harness;

    setUp(() => harness = E2ETestHarness()..setUp());
    tearDown(() => harness.tearDown());

    Future<void> seedAndPump(
      WidgetTester tester, {
      required GlobalKey<_SwapHostState> hostKey,
      required Future<void> Function() beforeRespond,
    }) async {
      final now = DateTime.now();
      await harness.peopleRepo.savePerson(
        Person(
          id: 'p-test',
          name: 'Test Person',
          birthdayMonth: now.month,
          birthdayDay: now.day,
          birthYear: 1990,
          relationship: RelationshipCategory.friend,
          relationshipCloseness: RelationshipCloseness.close,
          preferredLanguage: 'en',
          preferredTone: MessageTone.warm,
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
      harness.subscriptionNotifier.setDevSandboxEntitlement(
        UserEntitlement.proActive,
      );
      // Route AI through the gated HTTP provider, not the instant fake core:
      // the test needs to hold the request in flight.
      await harness.credentialStorage.saveGeminiApiKey('AIzaSyTestKey');

      tester.view.physicalSize = const Size(800, 2000);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final overrides = [
        ...harness.providerOverrides,
        userGeminiApiProvider.overrideWithValue(
          UserGeminiApiProvider(
            credentialStorage: harness.credentialStorage,
            httpSender: (uri, headers, body) async {
              await beforeRespond();
              return const HttpResponsePayload(
                statusCode: 200,
                body: _aiPayload,
              );
            },
          ),
        ),
      ];
      await tester.pumpWidget(
        SwapHost(
          key: hostKey,
          overrides: overrides,
          child: const MessageStudioScreen(birthdayId: 'b-test'),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets(
      'leaving mid-generation persists the draft; returning seats from the job',
      (tester) async {
        final gate = Completer<void>();
        final hostKey = GlobalKey<_SwapHostState>();
        await seedAndPump(
          tester,
          hostKey: hostKey,
          beforeRespond: () => gate.future,
        );

        // Generate; the request is held in flight by the gate. The enqueue
        // sets the running state after an await, and the button shows an
        // indeterminate spinner, so poll with bounded pumps (never settle —
        // the spinner animates until the gate releases).
        await tester.ensureVisible(find.text('Generate with AI'));
        await tester.tap(find.text('Generate with AI'));
        for (
          var i = 0;
          i < 20 && find.text('Drafting...').evaluate().isEmpty;
          i++
        ) {
          await tester.pump(const Duration(milliseconds: 50));
        }
        expect(find.text('Drafting...'), findsOneWidget);
        expect(find.text('Generate with AI'), findsNothing);

        // Leave the screen mid-generation: swap in a placeholder. The
        // ProviderScope (and with it the job worker) stays alive — only the
        // studio's own subscription is torn down.
        hostKey.currentState!.show(const Scaffold(body: Text('away')));
        await tester.pumpAndSettle();
        expect(find.text('Drafting...'), findsNothing);

        // Complete the request while nobody is on the studio.
        gate.complete();
        // The worker's completion does not schedule widget frames (no studio
        // is listening), so poll the durable effect explicitly.
        for (
          var i = 0;
          i < 40 &&
              await harness.draftsRepo.getDraftForBirthday('b-test') == null;
          i++
        ) {
          await tester.pump(const Duration(milliseconds: 50));
        }

        final drafts = await harness.draftsRepo.getAllDrafts();
        expect(drafts, hasLength(1));
        expect(drafts.single.body, 'AI drafted message');

        // Return to the studio: it seats its state from the durable job row
        // (one-shot read of the succeeded job) and shows the drafted message.
        hostKey.currentState!.show(
          const MessageStudioScreen(birthdayId: 'b-test'),
        );
        await tester.pumpAndSettle();

        expect(find.text('AI drafted message'), findsOneWidget);
        expect(find.text('Drafting...'), findsNothing);
        expect(find.text('Generate with AI'), findsOneWidget);
      },
    );
  });
}
