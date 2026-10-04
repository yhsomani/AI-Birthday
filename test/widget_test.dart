import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/app.dart';

void main() {
  testWidgets('App root initializes and renders unified dashboard shell', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(800, 1400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(const ProviderScope(child: AiBirthdayApp()));
    await tester.pumpAndSettle();

    // Verify app title in AppBar
    expect(find.text('AI-Birthday'), findsOneWidget);

    // Verify navigation destinations
    expect(find.text('Dashboard'), findsOneWidget);
    expect(find.text('People'), findsOneWidget);
    expect(find.text('Settings'), findsOneWidget);
  });
}
