/// AppSectionHeader announces section titles as headings (audit 05 W2).
library;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/shared/design_system/design_system.dart';

void main() {
  Widget host(Widget child) => MaterialApp(
        home: Scaffold(body: SingleChildScrollView(child: child)),
      );

  testWidgets('announces the section title as a heading (header: true)', (
    tester,
  ) async {
    await tester.pumpWidget(
      host(const AppSectionHeader(title: 'Action Needed', isAccent: true)),
    );

    final node = tester.getSemantics(find.byType(AppSectionHeader));
    expect(node.flagsCollection.isHeader, isTrue,
        reason: 'TalkBack must announce section titles as headings');
  });

  testWidgets('renders the title uppercase with the count pill', (
    tester,
  ) async {
    await tester.pumpWidget(
      host(const AppSectionHeader(title: 'Upcoming Birthdays', count: 3)),
    );

    expect(find.text('UPCOMING BIRTHDAYS'), findsOneWidget);
    expect(find.text('3'), findsOneWidget);
  });

  testWidgets('wraps long titles instead of overflowing at large scales', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 200,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: const [
                AppSectionHeader(title: 'Subscription & Entitlement'),
                Text('body'),
              ],
            ),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
    expect(find.text('SUBSCRIPTION & ENTITLEMENT'), findsOneWidget);
  });
}