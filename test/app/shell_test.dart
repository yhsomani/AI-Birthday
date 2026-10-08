/// Shell & navigation tests (Phase 3): bottom-nav branch switching,
/// destination semantics, and 200% text-scale safety of the app shell.
///
/// Closes the Phase 0 audit gap: "Zero UI test coverage: ... AppScaffold
/// navigation".
library;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/features/calendar/presentation/calendar_screen.dart';
import 'package:ai_birthday/features/dashboard/presentation/dashboard_screen.dart';
import 'package:ai_birthday/features/history/presentation/history_screen.dart';
import 'package:ai_birthday/features/people/presentation/people_screen.dart';
import 'package:ai_birthday/features/people/presentation/person_form_screen.dart';
import 'package:ai_birthday/features/settings/presentation/settings_screen.dart';

import 'pump_app.dart';

/// Bottom-nav label lookup scoped to the [NavigationBar] — the Settings and
/// Calendar screens repeat these words in their AppBars.
Finder navLabel(String label) =>
    find.descendant(of: find.byType(NavigationBar), matching: find.text(label));

int selectedIndex(WidgetTester tester) =>
    tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex;

void main() {
  testWidgets('bottom nav switches between all five branches', (tester) async {
    await pumpCompletedApp(tester);

    expect(find.byType(DashboardScreen), findsOneWidget);
    expect(selectedIndex(tester), 0);

    const steps = <(String, int, Type)>[
      ('People', 1, PeopleScreen),
      ('Calendar', 2, CalendarScreen),
      ('History', 3, HistoryScreen),
      ('Settings', 4, SettingsScreen),
      ('Dashboard', 0, DashboardScreen),
    ];
    var previous = DashboardScreen;
    for (final (label, index, screen) in steps) {
      await tester.tap(navLabel(label));
      await tester.pumpAndSettle();
      expect(selectedIndex(tester), index, reason: 'after tapping "$label"');
      expect(
        find.byType(screen),
        findsOneWidget,
        reason: '"$label" branch visible',
      );
      expect(
        find.byType(previous),
        findsNothing,
        reason: 'previous branch hidden',
      );
      previous = screen;
      expect(tester.takeException(), isNull);
    }

    await drainApp(tester);
  });

  testWidgets('re-tapping the active destination stays on the branch', (
    tester,
  ) async {
    await pumpCompletedApp(tester);

    await tester.tap(navLabel('Dashboard'));
    await tester.pumpAndSettle();

    expect(selectedIndex(tester), 0);
    expect(find.byType(DashboardScreen), findsOneWidget);
    expect(tester.takeException(), isNull);

    await drainApp(tester);
  });

  // Regression: the empty-state Import CTA used context.push('/people'),
  // stacking a second shell instance instead of switching tabs.
  testWidgets('empty-state Import Contacts tab-switches to People', (
    tester,
  ) async {
    await pumpCompletedApp(tester);
    await tester.pumpAndSettle();

    await tester.tap(find.text('Import Contacts'));
    await tester.pumpAndSettle();

    expect(find.byType(PeopleScreen), findsOneWidget);
    expect(find.byType(NavigationBar), findsOneWidget);
    expect(selectedIndex(tester), 1);
    expect(tester.takeException(), isNull);

    await drainApp(tester);
  });

  testWidgets('History empty state offers an add-person CTA', (tester) async {
    await pumpCompletedApp(tester);
    await tester.pumpAndSettle();

    await tester.tap(navLabel('History'));
    await tester.pumpAndSettle();

    expect(find.text('No activity yet'), findsOneWidget);

    await tester.tap(find.text('Add Birthday Contact'));
    await tester.pumpAndSettle();

    expect(find.byType(PersonFormScreen), findsOneWidget);
    expect(tester.takeException(), isNull);

    await drainApp(tester);
  });

  testWidgets('destinations expose button semantics with selected state', (
    tester,
  ) async {
    final handle = tester.ensureSemantics();
    await pumpCompletedApp(tester);

    expect(
      tester.getSemantics(navLabel('Dashboard')),
      isSemantics(isButton: true, isSelected: true, hasTapAction: true),
    );
    // NavigationBar annotates destinations with an M3 "Tab N of M" suffix.
    expect(
      tester.getSemantics(navLabel('Dashboard')).label,
      contains('Dashboard'),
    );
    expect(
      tester.getSemantics(navLabel('People')),
      isSemantics(isButton: true, isSelected: false, hasTapAction: true),
    );
    expect(tester.getSemantics(navLabel('People')).label, contains('People'));

    await drainApp(tester);
    // Explicit disposal in the test body: addTearDown runs after the
    // binding verifies semantics handles were disposed.
    handle.dispose();
  });

  testWidgets('shell renders and navigates at 200% text scale', (tester) async {
    tester.platformDispatcher.textScaleFactorTestValue = 2.0;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

    // Realistic phone width: the Phase 0 audit flagged the old fixed 68dp
    // nav height as a 200%-text-scale clipping risk.
    await pumpCompletedApp(tester, size: const Size(400, 2400));
    expect(tester.takeException(), isNull, reason: 'dashboard at 200%');

    // Every nav label stays inside the (M3 default) bar bounds at 200% scale.
    final barRect = tester.getRect(find.byType(NavigationBar));
    for (final label in const [
      'Dashboard',
      'People',
      'Calendar',
      'History',
      'Settings',
    ]) {
      final rect = tester.getRect(navLabel(label));
      expect(rect.top, greaterThanOrEqualTo(barRect.top), reason: '$label top');
      expect(
        rect.bottom,
        lessThanOrEqualTo(barRect.bottom),
        reason: '$label bottom',
      );
    }

    await drainApp(tester);
  });
}
