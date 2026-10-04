import 'package:ai_birthday/features/people/data/person_providers.dart';
import 'package:ai_birthday/features/people/domain/person.dart';
import 'package:ai_birthday/features/people/presentation/person_form_screen.dart';
import 'package:ai_birthday/features/people/presentation/person_list_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../support/fake_people_store.dart';

void main() {
  Person person({
    String? id,
    String name = 'Sam',
    int month = 4,
    int day = 22,
  }) {
    final now = DateTime.utc(2025, 1, 1, 8);
    return Person(
      id: id ?? 'p-$name-$day',
      name: name,
      birthdayMonth: month,
      birthdayDay: day,
      createdAt: now,
      updatedAt: now,
    );
  }

  GoRouter router(FakePeopleStore store, {String? initialLocation}) => GoRouter(
    initialLocation: initialLocation ?? '/birthdays',
    routes: [
      GoRoute(
        path: '/birthdays',
        builder: (context, state) => const PersonListScreen(),
      ),
      GoRoute(
        path: '/people/add',
        builder: (context, state) => const PersonFormScreen(),
      ),
      GoRoute(
        path: '/people/edit/:id',
        builder: (context, state) =>
            PersonFormScreen(personId: state.pathParameters['id']),
      ),
    ],
  );

  Future<void> pumpHarness(
    WidgetTester tester,
    FakePeopleStore store, {
    String? initialLocation,
  }) async {
    tester.view.physicalSize = const Size(800, 2600);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [peopleStoreProvider.overrideWithValue(store)],
        child: MaterialApp.router(
          routerConfig: router(store, initialLocation: initialLocation),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> selectDropdown(
    WidgetTester tester,
    String fieldLabel,
    String option,
  ) async {
    await tester.tap(
      find.byWidgetPredicate(
        (widget) =>
            widget is DropdownButtonFormField<int> &&
            widget.decoration.labelText == fieldLabel,
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text(option).last);
    await tester.pumpAndSettle();
  }

  testWidgets('shows the empty state when there are no recipients', (
    tester,
  ) async {
    final store = FakePeopleStore();
    addTearDown(store.close);
    await pumpHarness(tester, store);

    expect(find.text('No birthdays yet'), findsOneWidget);
    expect(find.text('Birthdays'), findsOneWidget);
  });

  testWidgets('lists active recipients sorted by name with countdowns', (
    tester,
  ) async {
    final store = FakePeopleStore();
    addTearDown(store.close);
    await store.save(person(id: 'b', name: 'Bri', month: 1, day: 10));
    await store.save(person(id: 'a', name: 'Ana', month: 3, day: 3));
    await store.save(person(id: 'gone', name: 'Zoe', month: 6, day: 6));
    await store.softDelete('gone');

    await pumpHarness(tester, store);

    expect(find.text('Ana'), findsOneWidget);
    expect(find.text('Bri'), findsOneWidget);
    expect(find.text('Zoe'), findsNothing);
    expect(find.text('No birthdays yet'), findsNothing);
  });

  testWidgets('FAB opens the add form', (tester) async {
    final store = FakePeopleStore();
    addTearDown(store.close);
    await pumpHarness(tester, store);

    await tester.tap(find.byTooltip('Add a birthday'));
    await tester.pumpAndSettle();

    expect(find.text('Add birthday'), findsOneWidget);
    expect(find.text('Save'), findsOneWidget);
  });

  testWidgets('saving a valid form creates a person', (tester) async {
    final store = FakePeopleStore();
    addTearDown(store.close);
    await pumpHarness(tester, store, initialLocation: '/people/add');

    await tester.enterText(find.widgetWithText(TextField, 'Name'), 'Priya');
    await selectDropdown(tester, 'Month', 'April');
    await selectDropdown(tester, 'Day', '5');

    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    final saved = await store.getAll();
    expect(saved, hasLength(1));
    expect(saved.single.name, 'Priya');
    expect(saved.single.birthdayMonth, 4);
    expect(saved.single.birthdayDay, 5);
  });

  testWidgets('saving an invalid form surfaces field errors', (tester) async {
    final store = FakePeopleStore();
    addTearDown(store.close);
    await pumpHarness(tester, store, initialLocation: '/people/add');

    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    expect(find.text('Name is required'), findsOneWidget);
    expect(find.text('Pick a birthday (month and day)'), findsOneWidget);
    expect(await store.getAll(), isEmpty);
  });

  testWidgets('delete hides the person and undo restores it', (tester) async {
    final store = FakePeopleStore();
    addTearDown(store.close);
    await store.save(person(id: 'p1', name: 'Priya', month: 4, day: 22));
    await pumpHarness(tester, store);

    expect(find.text('Priya'), findsOneWidget);

    await tester.tap(find.byTooltip('Person actions'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Delete'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Delete'));
    await tester.pumpAndSettle();

    expect(find.text('Priya'), findsNothing);
    expect(find.text('Deleted Priya.'), findsOneWidget);

    await tester.tap(find.text('Undo'));
    await tester.pumpAndSettle();
    expect(find.text('Priya'), findsOneWidget);
  });

  testWidgets('edit flow pre-fills and persists updates', (tester) async {
    final store = FakePeopleStore();
    addTearDown(store.close);
    await store.save(person(id: 'p1', name: 'Priya', month: 4, day: 22));

    await pumpHarness(tester, store, initialLocation: '/people/edit/p1');

    expect(find.text('Edit birthday'), findsOneWidget);
    expect(find.text('Priya'), findsOneWidget);

    await tester.enterText(find.widgetWithText(TextField, 'Name'), 'Priya S');
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    final saved = await store.getById('p1');
    expect(saved!.name, 'Priya S');
    expect(saved.birthdayDay, 22);
    expect(saved.version, 2);
  });
}
