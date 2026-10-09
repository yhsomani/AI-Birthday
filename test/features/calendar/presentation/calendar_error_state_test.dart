import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/features/calendar/presentation/calendar_screen.dart';
import 'package:ai_birthday/features/people/data/person_providers.dart';

import '../../../e2e/harness/test_harness.dart';

void main() {
  group('Calendar load error state', () {
    late E2ETestHarness harness;

    setUp(() => harness = E2ETestHarness()..setUp());
    tearDown(() => harness.tearDown());

    testWidgets('load failure offers Try again that re-subscribes', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      var subscriptions = 0;
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            ...harness.providerOverrides,
            personListProvider.overrideWith((ref) {
              subscriptions++;
              return Stream.error(StateError('storage unavailable'));
            }),
          ],
          child: const MaterialApp(home: CalendarScreen()),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Could not load birthdays'), findsOneWidget);
      expect(find.text('Try again'), findsOneWidget);
      expect(subscriptions, 1);

      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();

      expect(subscriptions, 2);
      expect(find.text('Try again'), findsOneWidget);
    });
  });
}
