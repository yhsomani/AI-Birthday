/// A stale or deleted birthday deep link must never dead-end the user: the
/// Studio shows a recovery action that returns to the People list.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:ai_birthday/app/app.dart';
import 'package:ai_birthday/app/app_scaffold.dart';

import '../../../e2e/harness/test_harness.dart';

void main() {
  late E2ETestHarness harness;

  setUp(() {
    harness = E2ETestHarness()..setUp();
    addTearDown(() async => harness.tearDown());
  });

  testWidgets('stale birthday link offers a recovery action back to People', (
    tester,
  ) async {
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

    final router = GoRouter.of(tester.element(find.byType(AppScaffold)));
    router.go('/message-studio/zzz-missing');
    await tester.pumpAndSettle();

    // The stale link lands on a clear message, not a dead end.
    expect(find.text('Birthday event not found.'), findsOneWidget);
    expect(find.text('Go to People'), findsOneWidget);

    await tester.tap(find.text('Go to People'));
    await tester.pumpAndSettle();

    expect(find.text('Birthday event not found.'), findsNothing);
    expect(router.routerDelegate.currentConfiguration.uri.path, '/people');

    // Drain drift stream-query cleanup timers before the tree is disposed.
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(milliseconds: 10));
  });
}