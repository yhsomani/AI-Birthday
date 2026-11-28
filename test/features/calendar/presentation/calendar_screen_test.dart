import 'package:ai_birthday/app/providers.dart' show peopleRepositoryProvider;
import 'package:ai_birthday/features/birthdays/domain/birthday_engine.dart';
import 'package:ai_birthday/features/calendar/presentation/calendar_screen.dart';
import 'package:ai_birthday/features/people/domain/person.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../../people/support/fake_people_repository.dart';

void main() {
  final fixedNow = DateTime(2026, 2, 10);

  Person person({
    required String id,
    required String name,
    required int month,
    required int day,
    int? birthYear,
    LeapDayResolution? leapDayResolution,
  }) {
    final now = DateTime.utc(2025, 1, 1, 8);
    return Person(
      id: id,
      name: name,
      birthdayMonth: month,
      birthdayDay: day,
      birthYear: birthYear,
      leapDayResolution: leapDayResolution,
      createdAt: now,
      updatedAt: now,
    );
  }

  Future<void> pumpHarness(
    WidgetTester tester,
    FakePeopleRepository repo,
  ) async {
    tester.view.physicalSize = const Size(600, 900);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    // A mock GoRouter wrapper is not needed if we provide mock push implementation
    // or we can use a mock router. Since we just want to suppress GoRouter error:
    final router = GoRouter(
      routes: [
        GoRoute(
          path: '/',
          builder: (context, state) => CalendarScreen(now: () => fixedNow),
        ),
      ],
    );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [peopleRepositoryProvider.overrideWithValue(repo)],
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> openDay(WidgetTester tester, String dayNumber) async {
    await tester.tap(find.text(dayNumber));
    await tester.pumpAndSettle();
  }

  testWidgets('shows the visible month header with today marked', (
    tester,
  ) async {
    final store = FakePeopleRepository();
    addTearDown(store.close);
    await store.savePerson(
      person(id: 'a', name: 'Ana', month: 2, day: 5, birthYear: 2025),
    );
    await pumpHarness(tester, store);

    expect(find.text('February 2026'), findsOneWidget);
    expect(find.text('Mon'), findsOneWidget);
    expect(find.text('Sun'), findsOneWidget);
  });

  testWidgets('marks a birthday on its day and opens the day sheet', (
    tester,
  ) async {
    final store = FakePeopleRepository();
    addTearDown(store.close);
    await store.savePerson(
      person(id: 'a', name: 'Ana', month: 2, day: 5, birthYear: 2025),
    );

    await pumpHarness(tester, store);

    await openDay(tester, '5');

    expect(find.text('Birthdays Feb 5'), findsOneWidget);
    expect(find.text('Ana'), findsOneWidget);
    expect(find.text('2025 · turns 1'), findsOneWidget);
  });

  testWidgets('resolves a Feb 29 birthday to Feb 28 in a non-leap year', (
    tester,
  ) async {
    final store = FakePeopleRepository();
    addTearDown(store.close);
    await store.savePerson(
      person(
        id: 'b',
        name: 'Bri',
        month: 2,
        day: 29,
        leapDayResolution: LeapDayResolution.feb28,
      ),
    );

    await pumpHarness(tester, store);

    expect(find.text('29'), findsNothing);
    await openDay(tester, '28');
    expect(find.text('Birthdays Feb 28'), findsOneWidget);
    expect(find.text('Bri'), findsOneWidget);
  });

  testWidgets('does not mark out-of-month birthdays and can navigate months', (
    tester,
  ) async {
    final store = FakePeopleRepository();
    addTearDown(store.close);
    await store.savePerson(person(id: 'c', name: 'Ced', month: 3, day: 3));

    await pumpHarness(tester, store);

    expect(find.text('Ced'), findsNothing);

    await tester.tap(find.byTooltip('Next month'));
    await tester.pumpAndSettle();
    expect(find.text('March 2026'), findsOneWidget);

    await openDay(tester, '3');
    expect(find.text('Birthdays Mar 3'), findsOneWidget);
    expect(find.text('Ced'), findsOneWidget);

    await tester.tapAt(const Offset(4, 4));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Previous month'));
    await tester.pumpAndSettle();
    expect(find.text('February 2026'), findsOneWidget);
  });

  testWidgets(
    'Mar 1 policy places leap birthday in March and Today jumps back',
    (tester) async {
      final store = FakePeopleRepository();
      addTearDown(store.close);
      await store.savePerson(
        person(
          id: 'leap',
          name: 'Leap',
          month: 2,
          day: 29,
          leapDayResolution: LeapDayResolution.mar1,
        ),
      );
      await pumpHarness(tester, store);

      await tester.tap(find.byTooltip('Next month'));
      await tester.pumpAndSettle();
      await openDay(tester, '1');
      expect(find.text('Leap'), findsOneWidget);

      await tester.tapAt(const Offset(4, 4));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Jump to today'));
      await tester.pumpAndSettle();
      expect(find.text('February 2026'), findsOneWidget);
    },
  );

  testWidgets('day cells expose full dates at narrow large-text sizes', (
    tester,
  ) async {
    final store = FakePeopleRepository();
    addTearDown(store.close);
    await store.savePerson(person(id: 'a', name: 'Ana', month: 2, day: 5));
    tester.view.physicalSize = const Size(320, 640);
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    final router = GoRouter(
      routes: [
        GoRoute(
          path: '/',
          builder: (context, state) => CalendarScreen(now: () => fixedNow),
        ),
      ],
    );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [peopleRepositoryProvider.overrideWithValue(store)],
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();

    final daySemantics = tester.getSemantics(find.text('10'));
    expect(daySemantics.label, contains('February 10, 2026'));
    expect(daySemantics.label, contains('today'));
    expect(tester.takeException(), isNull);
  });

  testWidgets('month navigation buttons trigger haptic feedback', (
    tester,
  ) async {
    final hapticCalls = <String>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (MethodCall methodCall) async {
        if (methodCall.method == 'HapticFeedback.vibrate') {
          hapticCalls.add(methodCall.arguments as String);
        }
        return null;
      },
    );

    final store = FakePeopleRepository();
    addTearDown(store.close);
    await store.savePerson(person(id: 'a', name: 'Ana', month: 2, day: 5));
    await pumpHarness(tester, store);

    expect(hapticCalls, isEmpty);

    await tester.tap(find.byTooltip('Next month'));
    await tester.pumpAndSettle();
    expect(hapticCalls, ['HapticFeedbackType.lightImpact']);

    await tester.tap(find.byTooltip('Previous month'));
    await tester.pumpAndSettle();
    expect(hapticCalls, [
      'HapticFeedbackType.lightImpact',
      'HapticFeedbackType.lightImpact',
    ]);
  });

  testWidgets(
    'tapping day cell and person in bottom sheet triggers haptic feedback',
    (tester) async {
      final hapticCalls = <String>[];
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (MethodCall methodCall) async {
          if (methodCall.method == 'HapticFeedback.vibrate') {
            hapticCalls.add(methodCall.arguments as String);
          }
          return null;
        },
      );

      final store = FakePeopleRepository();
      addTearDown(store.close);
      await store.savePerson(person(id: 'a', name: 'Ana', month: 2, day: 5));
      await pumpHarness(tester, store);

      expect(hapticCalls, isEmpty);

      // Tap day cell
      await tester.tap(find.text('5'));
      await tester.pumpAndSettle();
      expect(hapticCalls, ['HapticFeedbackType.lightImpact']);

      // Tap person in bottom sheet
      await tester.tap(find.text('Ana'));
      await tester.pumpAndSettle();
      expect(hapticCalls, [
        'HapticFeedbackType.lightImpact',
        'HapticFeedbackType.lightImpact',
      ]);
    },
  );

  testWidgets('does not show a negative age before the birth year', (
    tester,
  ) async {
    final store = FakePeopleRepository();
    addTearDown(store.close);
    await store.savePerson(
      person(
        id: 'future-birth-year',
        name: 'Future Birthday',
        month: 2,
        day: 11,
        birthYear: 2027,
      ),
    );
    await pumpHarness(tester, store);

    await openDay(tester, '11');

    expect(find.textContaining('turns -'), findsNothing);
    expect(find.text('2027 · turns -1'), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
