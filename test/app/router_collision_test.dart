import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:ai_birthday/app/app.dart';
import 'package:ai_birthday/app/app_scaffold.dart';
import 'package:ai_birthday/features/message_studio/presentation/message_studio_screen.dart';

import '../e2e/harness/test_harness.dart';

void main() {
  late E2ETestHarness harness;

  setUp(() {
    harness = E2ETestHarness()..setUp();
    addTearDown(() async => harness.tearDown());
  });

  Future<GoRouter> pumpReadyApp(WidgetTester tester) async {
    tester.view.physicalSize = const Size(800, 1400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await harness.credentialStorage.setCompletedOnboarding(true);
    await tester.pumpWidget(
      ProviderScope(
        overrides: harness.providerOverrides,
        child: const AiBirthdayApp(),
      ),
    );
    await tester.pumpAndSettle();
    return GoRouter.of(tester.element(find.byType(AppScaffold)));
  }

  testWidgets('/message-studio/person renders NotFoundScreen, not Studio', (
    tester,
  ) async {
    final router = await pumpReadyApp(tester);

    router.go('/message-studio/person');
    await tester.pumpAndSettle();

    expect(find.text('Screen not found'), findsOneWidget);
    expect(find.byType(MessageStudioScreen), findsNothing);

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(milliseconds: 10));
  });

  testWidgets(
    '/message-studio/person/:personId routes to person MessageStudioScreen',
    (tester) async {
      final router = await pumpReadyApp(tester);

      router.go('/message-studio/person/p-42');
      await tester.pumpAndSettle();

      final studio = tester.widget<MessageStudioScreen>(
        find.byType(MessageStudioScreen),
      );
      expect(studio.personId, 'p-42');
      expect(studio.birthdayId, isNull);

      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(milliseconds: 10));
    },
  );

  testWidgets(
    '/message-studio/:birthdayId routes to birthday MessageStudioScreen',
    (tester) async {
      final router = await pumpReadyApp(tester);

      router.go('/message-studio/b-99');
      await tester.pumpAndSettle();

      final studio = tester.widget<MessageStudioScreen>(
        find.byType(MessageStudioScreen),
      );
      expect(studio.birthdayId, 'b-99');
      expect(studio.personId, isNull);

      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(milliseconds: 10));
    },
  );
}
