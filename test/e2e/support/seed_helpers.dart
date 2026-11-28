/// Opaque-box UI & CSV seeding helpers for E2E tests.
///
/// These helpers interact ONLY with rendered Flutter UI widgets (never
/// importing or calling `lib/features/*/domain/` repositories behind the UI).
library;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/features/birthdays/domain/birthday_engine.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';

import 'responsive_helpers.dart';

const List<String> _monthNames = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
];

/// Completes onboarding from page 0 or 1 by tapping `'Skip'`.
Future<void> completeOnboardingBySkip(WidgetTester tester) async {
  final skip = find.text('Skip');
  expect(skip, findsOneWidget);
  await tester.tap(skip);
  await tester.pumpAndSettle();
}

/// Completes onboarding by stepping through `'Next'` -> `'Next'` -> `'Get Started'`.
Future<void> completeOnboardingByNext(WidgetTester tester) async {
  await tester.tap(find.text('Next'));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Next'));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Get Started'));
  await tester.pumpAndSettle();
}

/// Switches primary shell branch via `NavigationBar` or `NavigationRail`.
Future<void> switchTab(WidgetTester tester, String label) async {
  final target = findNavDestination(label);
  expect(target, findsOneWidget, reason: 'Nav destination "$label" not found');
  await tester.tap(target);
  await tester.pumpAndSettle();
}

Future<void> _selectDropdownItem(
  WidgetTester tester,
  Finder dropdown,
  String itemText,
) async {
  await tester.ensureVisible(dropdown);
  await tester.tap(dropdown);
  await tester.pumpAndSettle();

  final menuScrollable = find.byType(Scrollable).last;
  final itemFinder = find.descendant(
    of: menuScrollable,
    matching: find.text(itemText),
  );
  if (itemFinder.evaluate().isEmpty) {
    await tester.scrollUntilVisible(
      itemFinder,
      120,
      scrollable: menuScrollable,
    );
  }
  await tester.ensureVisible(itemFinder.last);
  await tester.tap(itemFinder.last);
  await tester.pumpAndSettle();
}

/// Navigates to People -> Add Person and creates a contact strictly through UI controls.
Future<void> addPersonViaUi(
  WidgetTester tester, {
  required String name,
  required int month,
  required int day,
  int? birthYear,
  String? phone,
  String? email,
  String? relationshipLabel,
  String? channelLabel,
  String? timezone,
  List<String> facts = const [],
  String? notes,
  LeapDayResolution? leapPolicy,
}) async {
  if (find.byTooltip('Add Person').evaluate().isEmpty) {
    await switchTab(tester, 'People');
  }
  await tester.tap(find.byTooltip('Add Person').first);
  await tester.pumpAndSettle();

  // Name
  await tester.enterText(find.widgetWithText(TextField, 'Name'), name);
  await tester.pump();

  // Month dropdown
  await _selectDropdownItem(
    tester,
    find.widgetWithText(DropdownButtonFormField<int>, 'Month'),
    _monthNames[month - 1],
  );

  // Day dropdown
  await _selectDropdownItem(
    tester,
    find.widgetWithText(DropdownButtonFormField<int>, 'Day'),
    '$day',
  );

  // Optional Feb 29 leap policy
  if (month == 2 && day == 29 && leapPolicy != null) {
    await _selectDropdownItem(
      tester,
      find.widgetWithText(
        DropdownButtonFormField<LeapDayResolution>,
        'In non-leap years, celebrate on',
      ),
      leapPolicy.displayName,
    );
  }

  // Optional Birth year
  if (birthYear != null) {
    final yearField = find.widgetWithText(TextField, 'Birth year (optional)');
    await tester.ensureVisible(yearField);
    await tester.enterText(yearField, '$birthYear');
    await tester.pump();
  }

  // Optional Relationship & Tone
  if (relationshipLabel != null) {
    final relTile = find.text('Relationship & Tone');
    await tester.ensureVisible(relTile);
    await tester.tap(relTile);
    await tester.pumpAndSettle();

    await _selectDropdownItem(
      tester,
      find.widgetWithText(
        DropdownButtonFormField<RelationshipCategory>,
        'Relationship',
      ),
      relationshipLabel,
    );
  }

  // Optional Contact & Delivery
  if (phone != null ||
      email != null ||
      channelLabel != null ||
      timezone != null) {
    final contactTile = find.text('Contact & Delivery');
    await tester.ensureVisible(contactTile);
    await tester.tap(contactTile);
    await tester.pumpAndSettle();

    if (phone != null) {
      final phoneField = find.widgetWithText(
        TextField,
        'Phone number (optional)',
      );
      await tester.ensureVisible(phoneField);
      await tester.enterText(phoneField, phone);
      await tester.pump();
    }
    if (email != null) {
      final emailField = find.widgetWithText(TextField, 'Email (optional)');
      await tester.ensureVisible(emailField);
      await tester.enterText(emailField, email);
      await tester.pump();
    }
    if (channelLabel != null) {
      await _selectDropdownItem(
        tester,
        find.widgetWithText(
          DropdownButtonFormField<DeliveryChannel>,
          'Preferred delivery channel',
        ),
        channelLabel,
      );
    }
    if (timezone != null) {
      final tzField = find.widgetWithText(
        TextField,
        'Timezone (optional, IANA)',
      );
      await tester.ensureVisible(tzField);
      await tester.enterText(tzField, timezone);
      await tester.pump();
    }
  }

  // Optional Facts & Notes
  if (facts.isNotEmpty || notes != null) {
    final factsTile = find.text('Facts & Context for AI');
    await tester.ensureVisible(factsTile);
    await tester.tap(factsTile);
    await tester.pumpAndSettle();

    for (var i = 0; i < facts.length; i++) {
      final addFactBtn = find.text('Add Fact');
      await tester.ensureVisible(addFactBtn);
      await tester.tap(addFactBtn);
      await tester.pumpAndSettle();
      final factField = find.widgetWithText(TextField, 'Fact ${i + 1}');
      await tester.ensureVisible(factField);
      await tester.enterText(factField, facts[i]);
      await tester.pump();
    }

    if (notes != null) {
      final notesField = find.widgetWithText(TextField, 'Notes (optional)');
      await tester.ensureVisible(notesField);
      await tester.enterText(notesField, notes);
      await tester.pump();
    }
  }

  // Save
  final saveBtn = find.widgetWithText(FilledButton, 'Save');
  await tester.ensureVisible(saveBtn);
  await tester.tap(saveBtn);
  await tester.pumpAndSettle();
}

/// Opens People -> More actions -> Import CSV, pastes [csvContent], parses,
/// and imports selected candidates through UI controls.
Future<void> importCsvViaUi(
  WidgetTester tester,
  String csvContent, {
  bool repairFeb29ToMar1 = false,
}) async {
  await switchTab(tester, 'People');

  await tester.tap(find.byTooltip('More actions'));
  await tester.pumpAndSettle();

  await tester.tap(find.text('Import CSV'));
  await tester.pumpAndSettle();

  final csvField = find.descendant(
    of: find.byType(BottomSheet),
    matching: find.byType(TextField),
  );
  await tester.enterText(csvField, csvContent);
  await tester.pump();

  await tester.tap(find.text('Parse & Review Candidates'));
  await tester.pumpAndSettle();

  if (repairFeb29ToMar1) {
    final repairDropdown = find.widgetWithText(
      DropdownButtonFormField<LeapDayResolution>,
      'In non-leap years, celebrate on',
    );
    if (repairDropdown.evaluate().isNotEmpty) {
      await tester.ensureVisible(repairDropdown.first);
      await tester.tap(repairDropdown.first);
      await tester.pumpAndSettle();
      await tester.tap(find.text(LeapDayResolution.mar1.displayName).last);
      await tester.pumpAndSettle();
    }
  }

  final importBtn = find.textContaining('Import Selected');
  if (importBtn.evaluate().isNotEmpty) {
    await tester.ensureVisible(importBtn.first);
    await tester.tap(importBtn.first);
    await tester.pumpAndSettle();
  }
}
