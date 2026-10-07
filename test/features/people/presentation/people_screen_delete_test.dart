import 'package:ai_birthday/core/core_providers.dart';
import 'package:ai_birthday/core/database/app_database.dart';
import 'package:ai_birthday/core/database/drift_repositories.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart'
    as b_models;
import 'package:ai_birthday/features/people/domain/models/person.dart'
    as domain;
import 'package:ai_birthday/features/people/presentation/people_screen.dart';
import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  domain.Person person() {
    final now = DateTime.utc(2025, 1, 1, 8);
    return domain.Person(
      id: 'a',
      name: 'Ana',
      birthdayMonth: 10,
      birthdayDay: 15,
      createdAt: now,
      updatedAt: now,
    );
  }

  b_models.Birthday birthday() {
    final now = DateTime.utc(2025, 1, 1, 8);
    return b_models.Birthday(
      id: 'b-a',
      personId: 'a',
      cycleYear: 2025,
      date: DateTime.utc(2025, 10, 15),
      createdAt: now,
      updatedAt: now,
    );
  }

  Future<AppDatabase> seedDb({bool withBirthday = false}) async {
    final db = AppDatabase(NativeDatabase.memory());
    addTearDown(db.close);
    await DriftPeopleRepository(db).savePerson(person());
    if (withBirthday) {
      await DriftBirthdaysRepository(db).saveBirthday(birthday());
    }
    return db;
  }

  Future<void> pumpHarness(WidgetTester tester, AppDatabase db) async {
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

  Future<void> deleteAna(WidgetTester tester) async {
    await tester.tap(find.byTooltip('Person actions'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Delete'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Delete'));
    await tester.pumpAndSettle();
  }

  testWidgets('delete shows a snackbar whose Undo restores the contact', (
    tester,
  ) async {
    await pumpHarness(tester, await seedDb());
    expect(find.text('Ana'), findsOneWidget);

    await deleteAna(tester);

    // Regression guard: the caller passes the list item's context, which is
    // unmounted by the delete itself, so the Undo bar used to never show.
    expect(find.text('Deleted Ana.'), findsOneWidget);
    expect(find.text('Undo'), findsOneWidget);

    await tester.tap(find.text('Undo'));
    await tester.pumpAndSettle();

    expect(find.text('Ana'), findsOneWidget);
    expect(find.text('No contacts added yet'), findsNothing);

    // Dispose the tree inside the test so Drift's zero-delay stream-close
    // timer fires here instead of tripping the pending-timers invariant.
    await tester.pumpWidget(const SizedBox());
    await tester.pump(Duration.zero);
  });

  testWidgets('undo restores the hard-deleted birthday row too', (
    tester,
  ) async {
    final db = await seedDb(withBirthday: true);
    await pumpHarness(tester, db);
    expect(find.text('Ana'), findsOneWidget);

    await deleteAna(tester);

    final birthdaysRepo = DriftBirthdaysRepository(db);
    expect(
      await birthdaysRepo.getBirthdayForPerson('a'),
      isNull,
      reason: 'the birthday row is hard-deleted on delete',
    );

    await tester.tap(find.text('Undo'));
    await tester.pumpAndSettle();

    expect(find.text('Ana'), findsOneWidget);
    final restored = await birthdaysRepo.getBirthdayForPerson('a');
    expect(restored, isNotNull);
    expect(restored!.personId, 'a');

    await tester.pumpWidget(const SizedBox());
    await tester.pump(Duration.zero);
  });
}
