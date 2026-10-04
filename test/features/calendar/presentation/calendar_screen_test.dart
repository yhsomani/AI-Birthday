import 'package:ai_birthday/features/calendar/presentation/calendar_screen.dart';
import 'package:ai_birthday/features/people/data/person_providers.dart';
import 'package:ai_birthday/features/people/domain/person.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../people/support/fake_people_store.dart';

void main() {
  final fixedNow = DateTime(2026, 2, 10);

  Person person({
    required String id,
    required String name,
    required int month,
    required int day,
    int? birthYear,
  }) {
    final now = DateTime.utc(2025, 1, 1, 8);
    return Person(
      id: id,
      name: name,
      birthdayMonth: month,
      birthdayDay: day,
      birthYear: birthYear,
      createdAt: now,
      updatedAt: now,
    );
  }

  Future<void> pumpHarness(WidgetTester tester, FakePeopleStore store) async {
    tester.view.physicalSize = const Size(600, 900);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [peopleStoreProvider.overrideWithValue(store)],
        child: MaterialApp(home: CalendarScreen(now: () => fixedNow)),
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
    final store = FakePeopleStore();
    addTearDown(store.close);
    await pumpHarness(tester, store);

    expect(find.text('February 2026'), findsOneWidget);
    expect(find.text('Mon'), findsOneWidget);
    expect(find.text('Sun'), findsOneWidget);
  });

  testWidgets('marks a birthday on its day and opens the day sheet', (
    tester,
  ) async {
    final store = FakePeopleStore();
    addTearDown(store.close);
    await store.save(
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
    final store = FakePeopleStore();
    addTearDown(store.close);
    await store.save(person(id: 'b', name: 'Bri', month: 2, day: 29));

    await pumpHarness(tester, store);

    expect(find.text('29'), findsNothing);
    await openDay(tester, '28');
    expect(find.text('Birthdays Feb 28'), findsOneWidget);
    expect(find.text('Bri'), findsOneWidget);
  });

  testWidgets('does not mark out-of-month birthdays and can navigate months', (
    tester,
  ) async {
    final store = FakePeopleStore();
    addTearDown(store.close);
    await store.save(person(id: 'c', name: 'Ced', month: 3, day: 3));

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
}
