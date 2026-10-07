import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/dashboard/presentation/dashboard_screen.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';

import '../../../e2e/harness/test_harness.dart';

void main() {
  group('Dashboard visibility (audit scenarios C, Q, L)', () {
    late E2ETestHarness harness;

    setUp(() => harness = E2ETestHarness()..setUp());
    tearDown(() => harness.tearDown());

    Person buildPerson({String? phone = '+14155552671'}) => Person(
      id: 'p-asha',
      name: 'Asha',
      birthdayMonth: DateTime.now().month,
      birthdayDay: DateTime.now().day,
      birthYear: 1996,
      phoneNumber: phone,
      relationship: RelationshipCategory.friend,
      relationshipCloseness: RelationshipCloseness.close,
      preferredLanguage: 'en',
      preferredTone: MessageTone.warm,
      preferredDeliveryChannel: DeliveryChannel.whatsapp,
      createdAt: DateTime.now(),
      updatedAt: DateTime.now(),
      version: 1,
    );

    // Birthday pins to "today" so it lands in the today/action sections, unless
    // [date] overrides it (e.g. +3 days for window tests).
    Birthday buildBirthday(BirthdayStatus status, {DateTime? date}) {
      final now = DateTime.now();
      return Birthday(
        id: 'b-asha',
        personId: 'p-asha',
        cycleYear: now.year,
        date: date ?? DateTime(now.year, now.month, now.day),
        status: status,
        createdAt: now,
        updatedAt: now,
      );
    }

    Future<void> pumpDashboard(
      WidgetTester tester, {
      required List<Person> people,
      required List<Birthday> birthdays,
    }) async {
      tester.view.physicalSize = const Size(800, 2000);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            ...harness.providerOverrides,
            peopleStreamProvider.overrideWith((ref) => Stream.value(people)),
            birthdaysStreamProvider.overrideWith(
              (ref) => Stream.value(birthdays),
            ),
          ],
          child: const MaterialApp(home: DashboardScreen()),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets(
      'completed birthday today is celebrated, never re-sent (rule 10 / scenario Q)',
      (tester) async {
        await pumpDashboard(
          tester,
          people: [buildPerson()],
          birthdays: [buildBirthday(BirthdayStatus.completed)],
        );

        // Still celebrated in the today section...
        expect(find.text("TODAY'S BIRTHDAYS"), findsOneWidget);
        expect(find.text('Asha'), findsOneWidget);
        // ...but never re-sent: excluded from Action Needed, no send CTA.
        expect(find.text('ACTION NEEDED'), findsNothing);
        expect(find.text('Review & Send'), findsNothing);
        expect(find.text('Draft Greeting'), findsNothing);
        expect(find.text('Confirm Sent'), findsNothing);
      },
    );

    testWidgets(
      'handed-off birthday today shows Confirm Sent, not sent-yet text',
      (tester) async {
        await pumpDashboard(
          tester,
          people: [buildPerson()],
          birthdays: [buildBirthday(BirthdayStatus.handedOff)],
        );

        expect(find.text('ACTION NEEDED'), findsOneWidget);
        // The hand-off is still open (not completed): chip + confirm action.
        expect(find.text('Handed Off'), findsOneWidget);
        expect(find.text('Confirm Sent'), findsOneWidget);
        expect(find.text('Completed'), findsNothing);
      },
    );

    testWidgets(
      'handed-off birthday 3 days out stays in Action Needed (1-7d window)',
      (tester) async {
        final inThreeDays = DateTime.now().add(const Duration(days: 3));
        await pumpDashboard(
          tester,
          people: [buildPerson()],
          birthdays: [
            buildBirthday(BirthdayStatus.handedOff, date: inThreeDays),
          ],
        );

        // An unconfirmed hand-off must not vanish after the birthday day
        // passes: it remains actionable inside the 1-7d window (audit 03 AC2).
        expect(find.text('ACTION NEEDED'), findsOneWidget);
        expect(find.text('Confirm Sent'), findsOneWidget);
      },
    );

    testWidgets('birthday needing a draft leads with Draft Greeting', (
      tester,
    ) async {
      await pumpDashboard(
        tester,
        people: [buildPerson()],
        birthdays: [buildBirthday(BirthdayStatus.messageNotPrepared)],
      );

      expect(find.text('Draft Greeting'), findsOneWidget);
    });

    testWidgets('person without a phone shows the missing-number hint', (
      tester,
    ) async {
      await pumpDashboard(
        tester,
        people: [buildPerson(phone: null)],
        birthdays: [buildBirthday(BirthdayStatus.messageNotPrepared)],
      );

      expect(find.text('No phone number'), findsOneWidget);
    });
  });
}
