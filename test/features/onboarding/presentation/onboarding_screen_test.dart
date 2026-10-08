import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/onboarding/presentation/onboarding_screen.dart';

void main() {
  group('OnboardingScreen Flow & Truthfulness', () {
    late InMemoryCredentialStorage fakeStorage;
    late GoRouter testRouter;

    setUp(() {
      fakeStorage = InMemoryCredentialStorage();
      testRouter = GoRouter(
        initialLocation: '/onboarding',
        routes: [
          GoRoute(
            path: '/onboarding',
            builder: (context, state) => const OnboardingScreen(),
          ),
          GoRoute(
            path: '/dashboard',
            builder: (context, state) =>
                const Scaffold(body: Text('Dashboard Screen')),
          ),
          GoRoute(
            path: '/people/add',
            builder: (context, state) =>
                const Scaffold(body: Text('Add Person Screen')),
          ),
          GoRoute(
            path: '/people',
            builder: (context, state) =>
                const Scaffold(body: Text('People Screen')),
          ),
        ],
      );
    });

    Widget buildTestApp() {
      return ProviderScope(
        overrides: [credentialStorageProvider.overrideWithValue(fakeStorage)],
        child: MaterialApp.router(routerConfig: testRouter),
      );
    }

    testWidgets('displays page 1 with core 5-step loop and privacy guarantee', (
      tester,
    ) async {
      await tester.pumpWidget(buildTestApp());
      await tester.pumpAndSettle();

      expect(find.text('Never miss a birthday that matters.'), findsOneWidget);
      expect(
        find.text('Remember → Prepare → Personalize → Review → Send'),
        findsOneWidget,
      );
      expect(find.text('Local by Default'), findsOneWidget);
      expect(find.text('Next'), findsOneWidget);
      expect(find.text('Skip'), findsOneWidget);
    });

    testWidgets('navigates through pages to completion and saves status', (
      tester,
    ) async {
      await tester.pumpWidget(buildTestApp());
      await tester.pumpAndSettle();

      // Page 1 -> Page 2
      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();

      expect(find.text('Bring in your birthdays'), findsOneWidget);
      expect(find.text('Add Birthday Manually'), findsOneWidget);
      expect(find.text('Import from Phone Contacts'), findsOneWidget);

      // Page 2 -> Page 3
      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();

      expect(find.text('Prepared ahead of time'), findsOneWidget);
      expect(find.text('7 Days'), findsOneWidget);
      expect(find.text('2 Days'), findsOneWidget);
      expect(find.text('Today'), findsOneWidget);
      expect(find.text('Get Started'), findsOneWidget);

      // Tap Get Started -> Saves onboarding completion and goes to dashboard
      await tester.tap(find.text('Get Started'));
      await tester.pumpAndSettle();

      expect(await fakeStorage.hasCompletedOnboarding(), isTrue);
      expect(find.text('Dashboard Screen'), findsOneWidget);
    });

    testWidgets('tapping Skip finishes onboarding and navigates to dashboard', (
      tester,
    ) async {
      await tester.pumpWidget(buildTestApp());
      await tester.pumpAndSettle();

      await tester.tap(find.text('Skip'));
      await tester.pumpAndSettle();

      expect(await fakeStorage.hasCompletedOnboarding(), isTrue);
      expect(find.text('Dashboard Screen'), findsOneWidget);
    });

    testWidgets(
      'Add Birthday Manually completes onboarding and keeps the form open',
      (tester) async {
        // Regression for the push-then-pop race: _finishOnboarding's pop()
        // used to land after the push and dismiss the just-opened form.
        await tester.pumpWidget(buildTestApp());
        await tester.pumpAndSettle();

        await tester.tap(find.text('Next'));
        await tester.pumpAndSettle();

        await tester.tap(find.text('Add Birthday Manually'));
        await tester.pumpAndSettle();

        expect(await fakeStorage.hasCompletedOnboarding(), isTrue);
        expect(find.text('Add Person Screen'), findsOneWidget);
        expect(find.text('Bring in your birthdays'), findsNothing);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      'Import from Phone Contacts completes onboarding and lands on People',
      (tester) async {
        await tester.pumpWidget(buildTestApp());
        await tester.pumpAndSettle();

        await tester.tap(find.text('Next'));
        await tester.pumpAndSettle();

        await tester.tap(find.text('Import from Phone Contacts'));
        await tester.pumpAndSettle();

        expect(await fakeStorage.hasCompletedOnboarding(), isTrue);
        expect(find.text('People Screen'), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );
  });
}
