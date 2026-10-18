import 'package:ai_birthday/features/reminders/application/reminder_settings_controller.dart';
import 'package:ai_birthday/features/reminders/domain/reminder_kind.dart';
import 'package:ai_birthday/features/settings/presentation/settings_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Future<void> pumpSettings(
    WidgetTester tester, {
    ProviderContainer? container,
  }) async {
    tester.view.physicalSize = const Size(800, 2400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container ?? ProviderContainer(),
        child: const MaterialApp(home: SettingsScreen()),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('reminders card is expandable and collapsible like Gemini card', (
    tester,
  ) async {
    await pumpSettings(tester);

    // Header is rendered
    expect(find.text('REMINDERS'), findsOneWidget);
    expect(find.text('Reminders'), findsOneWidget);
    expect(find.byIcon(Icons.notifications_outlined), findsWidgets);

    // Initial state: starts expanded when reminders are not configured yet,
    // matching Gemini API key card behavior (!hasKey -> initiallyExpanded: true).
    final remindersSwitch = find.widgetWithText(
      SwitchListTile,
      'Birthday reminders',
    );
    expect(remindersSwitch, findsOneWidget);

    // Tapping the ExpansionTile header toggles to collapsed
    final headerFinder = find.text('Reminders');
    await tester.tap(headerFinder);
    await tester.pumpAndSettle();

    // In collapsed state, the inner switch and controls are hidden
    expect(
      find.widgetWithText(SwitchListTile, 'Birthday reminders'),
      findsNothing,
    );
    expect(find.text('7 days before'), findsNothing);

    // Tapping header again expands the card back
    await tester.tap(headerFinder);
    await tester.pumpAndSettle();

    expect(
      find.widgetWithText(SwitchListTile, 'Birthday reminders'),
      findsOneWidget,
    );
  });

  testWidgets('reminders section defaults to off with leads hidden', (
    tester,
  ) async {
    await pumpSettings(tester);

    expect(find.text('REMINDERS'), findsOneWidget);
    expect(find.text('AI PROVIDER'), findsOneWidget);
    expect(find.text('7 days before'), findsNothing);
  });

  testWidgets('enabling reminders reveals leads and quiet hours', (
    tester,
  ) async {
    await pumpSettings(tester);

    final remindersSwitch = find.widgetWithText(
      SwitchListTile,
      'Birthday reminders',
    );
    await tester.ensureVisible(remindersSwitch);
    await tester.pumpAndSettle();
    await tester.tap(remindersSwitch);
    await tester.pumpAndSettle();

    expect(find.text('7 days before'), findsOneWidget);
    expect(find.text('2 days before'), findsOneWidget);
    expect(find.text('1 day before'), findsOneWidget);
    expect(find.text('Today'), findsWidgets);
    expect(find.textContaining('Quiet hours'), findsOneWidget);
    expect(find.textContaining('No delivery between'), findsOneWidget);
  });

  testWidgets('leads can be individually toggled off', (tester) async {
    await pumpSettings(tester);

    final remindersSwitch = find.widgetWithText(
      SwitchListTile,
      'Birthday reminders',
    );
    await tester.ensureVisible(remindersSwitch);
    await tester.pumpAndSettle();
    await tester.tap(remindersSwitch);
    await tester.pumpAndSettle();

    final approachingFinder = find.widgetWithText(
      SwitchListTile,
      'Approaching',
    );
    await tester.ensureVisible(approachingFinder);
    await tester.pumpAndSettle();
    await tester.tap(approachingFinder);
    await tester.pumpAndSettle();

    final controller = ProviderScope.containerOf(
      tester.element(find.byType(SettingsScreen)),
    ).read(reminderSettingsProvider);
    expect(controller.kinds, isNot(contains(ReminderKind.approaching)));
    expect(controller.kinds, contains(ReminderKind.birthday));
  });

  testWidgets('quiet hours picker updates the stored window', (tester) async {
    await pumpSettings(tester);
    final remindersSwitch = find.widgetWithText(
      SwitchListTile,
      'Birthday reminders',
    );
    await tester.ensureVisible(remindersSwitch);
    await tester.pumpAndSettle();
    await tester.tap(remindersSwitch);
    await tester.pumpAndSettle();

    final quietFinder = find.textContaining('No delivery between');
    await tester.ensureVisible(quietFinder);
    await tester.pumpAndSettle();

    await tester.tap(quietFinder);
    await tester.pumpAndSettle();

    // The time picker opens for the start bound first.
    expect(find.text('Select time'), findsOneWidget);
    // Cancel without changing, so the test stays deterministic.
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
  });

  testWidgets(
    'no nested dropdowns in reminders and no theme selector in settings',
    (tester) async {
      await pumpSettings(tester);

      // Verify reminder cadence dropdown is removed
      expect(find.text('Reminder Cadence Preference'), findsNothing);
      expect(find.byType(DropdownButtonFormField<String>), findsNothing);

      // Verify Appearance and Theme controls are removed
      expect(find.text('APPEARANCE'), findsNothing);
      expect(find.text('Theme'), findsNothing);
      expect(find.byType(SegmentedButton<ThemeMode>), findsNothing);
    },
  );
}
