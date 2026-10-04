import 'package:ai_birthday/app/app.dart';
import 'package:ai_birthday/core/core_providers.dart';
import 'package:ai_birthday/core/database/app_database.dart' as db;
import 'package:drift/native.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Widget appWithMemoryDatabase(db.AppDatabase database) {
    return ProviderScope(
      overrides: [databaseProvider.overrideWithValue(database)],
      child: const AiBirthdayApp(),
    );
  }

  // Drift stream queries schedule a zero-delay cleanup timer when their
  // listeners drop. Disposing the tree and pumping clears it while the test
  // body is still active so no timers are pending at the invariant check.
  Future<void> disposeApp(WidgetTester tester, db.AppDatabase database) async {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(milliseconds: 10));
    await database.close();
  }

  testWidgets(
    'app boots to the Home shell and navigates between destinations',
    (tester) async {
      final database = db.AppDatabase(NativeDatabase.memory());
      await tester.pumpWidget(appWithMemoryDatabase(database));
      await tester.pumpAndSettle();

      expect(find.text('Home'), findsWidgets);
      expect(find.text('No birthdays yet'), findsOneWidget);

      await tester.tap(find.text('Settings'));
      await tester.pumpAndSettle();
      expect(find.text('AI provider'), findsOneWidget);

      await tester.tap(find.text('Calendar'));
      await tester.pumpAndSettle();
      expect(find.text('Sun'), findsOneWidget);
      expect(find.text('Sat'), findsOneWidget);

      await tester.tap(find.text('Birthdays'));
      await tester.pumpAndSettle();
      expect(find.text('No birthdays yet'), findsOneWidget);

      await disposeApp(tester, database);
    },
  );

  testWidgets('reduced motion and large text do not break the shell', (
    tester,
  ) async {
    final database = db.AppDatabase(NativeDatabase.memory());
    tester.platformDispatcher.textScaleFactorTestValue = 2.0;
    tester.platformDispatcher.accessibilityFeaturesTestValue =
        FakeAccessibilityFeatures(reduceMotion: true);
    addTearDown(tester.platformDispatcher.clearAllTestValues);

    await tester.pumpWidget(appWithMemoryDatabase(database));
    await tester.pumpAndSettle();
    expect(find.text('Home'), findsWidgets);
    expect(tester.takeException(), isNull);

    await disposeApp(tester, database);
  });
}
