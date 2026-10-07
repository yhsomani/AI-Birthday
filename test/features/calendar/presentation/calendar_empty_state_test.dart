import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/features/calendar/presentation/calendar_screen.dart';
import 'package:ai_birthday/features/people/data/person_providers.dart';
import 'package:ai_birthday/features/people/domain/person.dart';

import '../../../e2e/harness/test_harness.dart';

void main() {
  group('Calendar empty state (audit scenario Y)', () {
    late E2ETestHarness harness;

    setUp(() => harness = E2ETestHarness()..setUp());
    tearDown(() => harness.tearDown());

    Future<void> pumpCalendar(WidgetTester tester, List<Person> people) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            ...harness.providerOverrides,
            personListProvider.overrideWith((ref) => Stream.value(people)),
          ],
          child: const MaterialApp(home: CalendarScreen()),
        ),
      );
      await tester.pumpAndSettle();
    }

    Person buildPerson() {
      final now = DateTime.now();
      return Person(
        id: 'p-cal',
        name: 'Cal Person',
        birthdayMonth: now.month,
        birthdayDay: now.day,
        birthYear: 1990,
        createdAt: now,
        updatedAt: now,
      );
    }

    testWidgets(
      'no contacts renders a meaningful empty state with an Add CTA',
      (tester) async {
        await pumpCalendar(tester, const []);

        expect(find.text('No birthdays yet'), findsOneWidget);
        expect(find.text('Add Birthday Contact'), findsOneWidget);
        // The empty month grid is not shown.
        expect(find.text('Mon'), findsNothing);
      },
    );

    testWidgets('contacts with birthdays render the month grid', (
      tester,
    ) async {
      await pumpCalendar(tester, [buildPerson()]);

      expect(find.text('No birthdays yet'), findsNothing);
      expect(find.text('Mon'), findsOneWidget);
    });
  });
}
