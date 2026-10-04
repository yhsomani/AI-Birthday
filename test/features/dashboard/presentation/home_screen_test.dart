import 'package:ai_birthday/features/calendar/presentation/calendar_screen.dart';
import 'package:ai_birthday/features/dashboard/presentation/home_screen.dart';
import 'package:ai_birthday/features/people/data/person_providers.dart';
import 'package:ai_birthday/features/people/domain/person.dart';
import 'package:ai_birthday/features/people/presentation/person_form_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../../people/support/fake_people_store.dart';

void main() {
  final fixedNow = DateTime(2026, 3, 14);

  Person person({
    required String id,
    required String name,
    required int month,
    required int day,
    int? birthYear,
  }) {
    final now = DateTime.utc(2026, 1, 1, 8);
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

  GoRouter router() => GoRouter(
    initialLocation: '/',
    routes: [
      GoRoute(
        path: '/',
        builder: (context, state) => HomeScreen(now: () => fixedNow),
      ),
      GoRoute(
        path: '/people/add',
        builder: (context, state) => const PersonFormScreen(),
      ),
      GoRoute(
        path: '/calendar',
        builder: (context, state) => CalendarScreen(now: () => fixedNow),
      ),
    ],
  );

  Future<void> pumpHarness(WidgetTester tester, FakePeopleStore store) async {
    tester.view.physicalSize = const Size(800, 1400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [peopleStoreProvider.overrideWithValue(store)],
        child: MaterialApp.router(routerConfig: router()),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('empty home shows the empty state', (tester) async {
    final store = FakePeopleStore();
    addTearDown(store.close);
    await pumpHarness(tester, store);

    expect(find.text('No birthdays yet'), findsOneWidget);
    expect(find.text('Today'), findsNothing);
  });

  testWidgets('shows today, upcoming, action needed and quick actions', (
    tester,
  ) async {
    final store = FakePeopleStore();
    addTearDown(store.close);
    await store.save(person(id: 'a', name: 'Ana', month: 3, day: 14));
    await store.save(person(id: 'b', name: 'Bri', month: 3, day: 15));
    await store.save(person(id: 'f', name: 'Zoe', month: 4, day: 20));

    await pumpHarness(tester, store);

    expect(find.text('Today'), findsWidgets);
    expect(find.text('Ana'), findsOneWidget);
    expect(find.text('Today'), findsWidgets);

    expect(find.text('Bri'), findsOneWidget);
    expect(find.textContaining('Tomorrow'), findsOneWidget);

    expect(find.text('Action needed'), findsOneWidget);
    expect(
      find.text('2 birthdays within the next week need messages'),
      findsOneWidget,
    );

    expect(find.text('Zoe'), findsNothing);
    expect(find.text('Add a birthday'), findsOneWidget);
    expect(find.text('View calendar'), findsOneWidget);
  });

  testWidgets('quick actions navigate to add and calendar', (tester) async {
    final store = FakePeopleStore();
    addTearDown(store.close);
    await store.save(person(id: 'a', name: 'Ana', month: 3, day: 15));

    await pumpHarness(tester, store);

    await tester.tap(find.text('Add a birthday'));
    await tester.pumpAndSettle();
    expect(find.text('Add birthday'), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle();

    await tester.tap(find.text('View calendar'));
    await tester.pumpAndSettle();
    expect(find.text('March 2026'), findsOneWidget);
  });
}
