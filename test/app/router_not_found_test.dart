/// Unknown-route handling: an unmatched location renders the fallback screen
/// (never a blank or error page) and offers a way back into the shell.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:ai_birthday/app/app.dart';
import 'package:ai_birthday/app/app_scaffold.dart';

import '../e2e/harness/test_harness.dart';

void main() {
  late E2ETestHarness harness;

  setUp(() {
    harness = E2ETestHarness()..setUp();
    addTearDown(() async => harness.tearDown());
  });

  Future<void> pumpApp(WidgetTester tester) async {
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
  }

  GoRouter routerOf(WidgetTester tester) =>
      GoRouter.of(tester.element(find.byType(AppScaffold)));

  testWidgets('an unmatched location shows the fallback, not an error', (
    tester,
  ) async {
    await pumpApp(tester);

    routerOf(tester).go('/no-such-screen');
    await tester.pumpAndSettle();

    expect(find.text('Screen not found'), findsOneWidget);
    expect(find.text('No screen matches "/no-such-screen".'), findsOneWidget);

    // The fallback offers a road back into the shell (other tabs intact).
    await tester.tap(find.text('Back to dashboard'));
    await tester.pumpAndSettle();

    expect(find.text('Screen not found'), findsNothing);
    expect(find.text('Dashboard'), findsOneWidget);
    expect(find.text('People'), findsOneWidget);

    // Drain drift stream-query cleanup timers before the tree is disposed.
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(milliseconds: 10));
  });
}
