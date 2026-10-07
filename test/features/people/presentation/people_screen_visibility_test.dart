import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/features/people/presentation/people_screen.dart';

import '../../../e2e/harness/test_harness.dart';

void main() {
  group('People dead-action visibility (audit §4)', () {
    late E2ETestHarness harness;

    setUp(() => harness = E2ETestHarness()..setUp());
    tearDown(() => harness.tearDown());

    Person buildPerson({required bool hasBirthday}) {
      final now = DateTime.now();
      return Person(
        id: 'p-test',
        name: 'Test Person',
        birthdayMonth: hasBirthday ? now.month : null,
        birthdayDay: hasBirthday ? now.day : null,
        birthYear: 1990,
        relationship: RelationshipCategory.friend,
        relationshipCloseness: RelationshipCloseness.close,
        preferredLanguage: 'en',
        preferredTone: MessageTone.warm,
        preferredDeliveryChannel: DeliveryChannel.whatsapp,
        createdAt: now,
        updatedAt: now,
        version: 1,
      );
    }

    Future<void> pumpPeople(WidgetTester tester, Person person) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            ...harness.providerOverrides,
            peopleStreamProvider.overrideWith((ref) => Stream.value([person])),
          ],
          child: const MaterialApp(home: PeopleScreen()),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('contact without a birthday has no Message Studio menu item', (
      tester,
    ) async {
      await pumpPeople(tester, buildPerson(hasBirthday: false));

      await tester.tap(find.byTooltip('Person actions'));
      await tester.pumpAndSettle();

      expect(find.text('Message Studio'), findsNothing);
      expect(find.text('Edit'), findsOneWidget);
      expect(find.text('Delete'), findsOneWidget);
    });

    testWidgets(
      'contact without a birthday has no AI draft action in details',
      (tester) async {
        await pumpPeople(tester, buildPerson(hasBirthday: false));

        await tester.tap(find.text('Test Person'));
        await tester.pumpAndSettle();

        expect(find.text('Draft Message with AI'), findsNothing);
        expect(find.text('Edit Contact Details'), findsOneWidget);
        expect(find.text('Delete Contact'), findsOneWidget);
      },
    );

    testWidgets('contact with a birthday keeps the Message Studio menu item', (
      tester,
    ) async {
      await pumpPeople(tester, buildPerson(hasBirthday: true));

      await tester.tap(find.byTooltip('Person actions'));
      await tester.pumpAndSettle();

      expect(find.text('Message Studio'), findsOneWidget);
    });

    testWidgets(
      'contact with a birthday keeps the AI draft action in details',
      (tester) async {
        await pumpPeople(tester, buildPerson(hasBirthday: true));

        await tester.tap(find.text('Test Person'));
        await tester.pumpAndSettle();

        expect(find.text('Draft Message with AI'), findsOneWidget);
      },
    );
  });
}
