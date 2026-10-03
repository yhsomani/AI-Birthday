import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/app.dart';

void main() {
  testWidgets('AiBirthdayApp loads and renders bottom navigation and tabs', (
    WidgetTester tester,
  ) async {
    // Build app with ProviderScope
    await tester.pumpWidget(const ProviderScope(child: AiBirthdayApp()));

    // Initial pump & settle
    await tester.pumpAndSettle();

    // Verify AppBar title
    expect(find.text('AI-Birthday'), findsOneWidget);

    // Verify NavigationBar exists
    expect(find.byType(NavigationBar), findsOneWidget);
    expect(find.text('Dashboard'), findsOneWidget);
    expect(find.text('People'), findsOneWidget);
    expect(find.text('Settings'), findsOneWidget);

    // Navigate to People tab
    await tester.tap(find.text('People'));
    await tester.pumpAndSettle();

    // Verify People screen title
    expect(find.text('People & Contacts'), findsOneWidget);

    // Navigate to Settings tab
    await tester.tap(find.text('Settings'));
    await tester.pumpAndSettle();

    // Verify Settings screen title & key card
    expect(find.text('Personal Gemini API Key'), findsOneWidget);
    expect(find.text('Appearance'), findsOneWidget);
  });
}
