import 'package:ai_birthday/core/core_providers.dart';
import 'package:ai_birthday/core/database/app_database.dart';
import 'package:ai_birthday/features/people/data/drift_people_repository.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart'
    as domain;
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/presentation/people_screen.dart';
import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  domain.Person person(String id, String name) {
    final now = DateTime.utc(2025, 1, 1, 8);
    return domain.Person(
      id: id,
      name: name,
      birthdayMonth: id == 'a' ? 10 : 11,
      birthdayDay: 15,
      relationship: id == 'a'
          ? RelationshipCategory.family
          : RelationshipCategory.friend,
      createdAt: now,
      updatedAt: now,
    );
  }

  Future<void> pumpScreen(WidgetTester tester) async {
    final db = AppDatabase(NativeDatabase.memory());
    addTearDown(db.close);
    final repo = DriftPeopleRepository(db);
    await repo.savePerson(person('a', 'Ana'));
    await repo.savePerson(person('b', 'Bob'));

    tester.view.physicalSize = const Size(600, 900);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [databaseProvider.overrideWithValue(db)],
        child: const MaterialApp(home: PeopleScreen()),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('search filters the contact list by name, case-insensitively', (
    tester,
  ) async {
    await pumpScreen(tester);

    expect(find.text('Ana'), findsOneWidget);
    expect(find.text('Bob'), findsOneWidget);

    await tester.enterText(find.byType(TextField), 'an');
    await tester.pump();

    expect(find.text('Ana'), findsOneWidget);
    expect(find.text('Bob'), findsNothing);

    // Case and surrounding whitespace do not matter.
    await tester.enterText(find.byType(TextField), '  ANA ');
    await tester.pump();

    expect(find.text('Ana'), findsOneWidget);
    expect(find.text('Bob'), findsNothing);

    // Dispose the tree inside the test so Drift's zero-delay stream-close
    // timer fires here instead of tripping the pending-timers invariant.
    await tester.pumpWidget(const SizedBox());
    await tester.pump(Duration.zero);
  });

  testWidgets('tapping a contact triggers haptic feedback', (tester) async {
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

    await pumpScreen(tester);

    expect(hapticCalls, isEmpty);

    await tester.tap(find.text('Ana'));
    await tester.pumpAndSettle();

    expect(hapticCalls, ['HapticFeedbackType.lightImpact']);

    // Dispose the tree inside the test so Drift's zero-delay stream-close
    // timer fires here instead of tripping the pending-timers invariant.
    await tester.pumpWidget(const SizedBox());
    await tester.pump(Duration.zero);
  });

  testWidgets('a query with no match shows the empty-search state', (
    tester,
  ) async {
    await pumpScreen(tester);

    await tester.enterText(find.byType(TextField), 'zzz');
    await tester.pump();

    expect(find.text('No contacts found'), findsOneWidget);
    expect(
      find.text(
        'Nothing matches "zzz". Try another name or clear the filters.',
      ),
      findsOneWidget,
    );
    expect(find.text('Ana'), findsNothing);

    // Dispose the tree inside the test so Drift's zero-delay stream-close
    // timer fires here instead of tripping the pending-timers invariant.
    await tester.pumpWidget(const SizedBox());
    await tester.pump(Duration.zero);
  });

  testWidgets('search and filters match relationship and birthday month', (
    tester,
  ) async {
    await pumpScreen(tester);

    await tester.enterText(find.byType(TextField), 'friend');
    await tester.pump();
    expect(find.text('Ana'), findsNothing);
    expect(find.text('Bob'), findsOneWidget);

    await tester.enterText(find.byType(TextField), '');
    await tester.pump();

    await tester.tap(find.byKey(const ValueKey('people-relationship-filter')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Family').last);
    await tester.pumpAndSettle();
    expect(find.text('Ana'), findsOneWidget);
    expect(find.text('Bob'), findsNothing);

    await tester.tap(find.byKey(const ValueKey('people-relationship-filter')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('All relationships').last);
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('people-month-filter')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('October').last);
    await tester.pumpAndSettle();

    expect(find.text('Ana'), findsOneWidget);
    expect(find.text('Bob'), findsNothing);

    await tester.pumpWidget(const SizedBox());
    await tester.pump(Duration.zero);
  });

  testWidgets('clearing the search restores the full list', (tester) async {
    await pumpScreen(tester);

    await tester.enterText(find.byType(TextField), 'bob');
    await tester.pump();
    expect(find.text('Ana'), findsNothing);

    await tester.tap(find.byTooltip('Clear search'));
    await tester.pump();

    expect(find.text('Ana'), findsOneWidget);
    expect(find.text('Bob'), findsOneWidget);
    expect(find.text('No contacts found'), findsNothing);

    // Dispose the tree inside the test so Drift's zero-delay stream-close
    // timer fires here instead of tripping the pending-timers invariant.
    await tester.pumpWidget(const SizedBox());
    await tester.pump(Duration.zero);
  });
}
