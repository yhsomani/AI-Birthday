import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/calendar/presentation/calendar_screen.dart';
import 'package:ai_birthday/features/dashboard/presentation/dashboard_screen.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_handoff.dart';
import 'package:ai_birthday/features/history/presentation/history_screen.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';
import 'package:ai_birthday/features/message_studio/presentation/message_studio_screen.dart';
import 'package:ai_birthday/features/onboarding/presentation/onboarding_screen.dart';
import 'package:ai_birthday/features/people/data/person_providers.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/features/people/presentation/people_screen.dart';
import 'package:ai_birthday/features/people/presentation/person_form_screen.dart';
import 'package:ai_birthday/features/settings/presentation/settings_screen.dart';
import '../e2e/harness/test_harness.dart';

void main() {
  group('Multi-Device Responsive Mobile Tests', () {
    late E2ETestHarness harness;

    setUp(() => harness = E2ETestHarness()..setUp());
    tearDown(() => harness.tearDown());

    Person buildPerson({
      String id = 'p-sarah',
      String name = 'Sarah Jenkins With A Very Long Professional Name',
      String? phone = '+14155552671',
    }) => Person(
      id: id,
      name: name,
      birthdayMonth: DateTime.now().month,
      birthdayDay: DateTime.now().day,
      birthYear: 1992,
      phoneNumber: phone,
      relationship: RelationshipCategory.colleague,
      relationshipCloseness: RelationshipCloseness.close,
      preferredLanguage: 'en',
      preferredTone: MessageTone.warm,
      preferredDeliveryChannel: DeliveryChannel.whatsapp,
      createdAt: DateTime.now(),
      updatedAt: DateTime.now(),
      version: 1,
    );

    Birthday buildBirthday(String id, String personId, BirthdayStatus status) {
      final now = DateTime.now();
      return Birthday(
        id: id,
        personId: personId,
        cycleYear: now.year,
        date: DateTime(now.year, now.month, now.day),
        status: status,
        createdAt: now,
        updatedAt: now,
      );
    }

    testWidgets(
      'Dashboard adapts safely to small 320px compact mobile screen without overflow',
      (tester) async {
        tester.view.physicalSize = const Size(320, 568);
        tester.view.devicePixelRatio = 1.0;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);

        final person = buildPerson();
        final birthday = buildBirthday(
          'b-1',
          person.id,
          BirthdayStatus.messageDrafted,
        );

        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              ...harness.providerOverrides,
              peopleStreamProvider.overrideWith(
                (ref) => Stream.value([person]),
              ),
              birthdaysStreamProvider.overrideWith(
                (ref) => Stream.value([birthday]),
              ),
            ],
            child: const MaterialApp(home: DashboardScreen()),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.byType(DashboardScreen), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      'Dashboard adapts safely to large 480px phablet screen with 1.3x accessibility text scale',
      (tester) async {
        tester.view.physicalSize = const Size(480, 960);
        tester.view.devicePixelRatio = 1.0;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);

        final person = buildPerson(name: 'David Kim');
        final birthday = buildBirthday(
          'b-2',
          person.id,
          BirthdayStatus.readyForDelivery,
        );

        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              ...harness.providerOverrides,
              peopleStreamProvider.overrideWith(
                (ref) => Stream.value([person]),
              ),
              birthdaysStreamProvider.overrideWith(
                (ref) => Stream.value([birthday]),
              ),
            ],
            child: MediaQuery(
              data: const MediaQueryData(textScaler: TextScaler.linear(1.3)),
              child: const MaterialApp(home: DashboardScreen()),
            ),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.byType(DashboardScreen), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      'People directory adapts safely to 320px compact mobile screen without overflow',
      (tester) async {
        tester.view.physicalSize = const Size(320, 568);
        tester.view.devicePixelRatio = 1.0;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);

        final person = buildPerson();
        await harness.peopleRepo.savePerson(person);

        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              ...harness.providerOverrides,
              peopleStreamProvider.overrideWith(
                (ref) => Stream.value([person]),
              ),
              personListProvider.overrideWith((ref) => Stream.value([person])),
            ],
            child: const MaterialApp(home: PeopleScreen()),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.byType(PeopleScreen), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      'Calendar adapts safely to 320px compact mobile screen without overflow',
      (tester) async {
        tester.view.physicalSize = const Size(320, 568);
        tester.view.devicePixelRatio = 1.0;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);

        final person = buildPerson();

        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              ...harness.providerOverrides,
              personListProvider.overrideWith((ref) => Stream.value([person])),
            ],
            child: MaterialApp(home: CalendarScreen(now: () => DateTime.now())),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.byType(CalendarScreen), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      'Message Studio adapts safely to 320px compact screen with 1.3x text scale',
      (tester) async {
        tester.view.physicalSize = const Size(320, 568);
        tester.view.devicePixelRatio = 1.0;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);

        final person = buildPerson();
        final birthday = buildBirthday(
          'b-studio',
          person.id,
          BirthdayStatus.messageNotPrepared,
        );
        await harness.peopleRepo.savePerson(person);
        await harness.birthdaysRepo.saveBirthday(birthday);

        await tester.pumpWidget(
          ProviderScope(
            overrides: harness.providerOverrides,
            child: MediaQuery(
              data: const MediaQueryData(textScaler: TextScaler.linear(1.3)),
              child: MaterialApp(
                home: MessageStudioScreen(birthdayId: birthday.id),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.byType(MessageStudioScreen), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      'Celebration History adapts safely to 320px compact mobile screen',
      (tester) async {
        tester.view.physicalSize = const Size(320, 568);
        tester.view.devicePixelRatio = 1.0;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);

        final person = buildPerson();
        final draft = MessageDraft(
          id: 'draft-1',
          birthdayId: 'b-1',
          personId: person.id,
          body: 'Happy birthday Sarah! Have a wonderful day.',
          tone: MessageTone.warm,
          length: MessageLength.standard,
          status: DraftStatus.confirmedSent,
          providerType: 'template',
          createdAt: DateTime.now(),
          updatedAt: DateTime.now(),
        );

        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              ...harness.providerOverrides,
              peopleStreamProvider.overrideWith(
                (ref) => Stream.value([person]),
              ),
              draftsStreamProvider.overrideWith((ref) => Stream.value([draft])),
              latestHandoffsForDraftsProvider.overrideWith(
                (ref) => Stream.value(<DeliveryHandoff>[]),
              ),
            ],
            child: const MaterialApp(home: HistoryScreen()),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.byType(HistoryScreen), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      'Settings screen adapts safely to 320px compact mobile screen with 1.3x text scale',
      (tester) async {
        tester.view.physicalSize = const Size(320, 568);
        tester.view.devicePixelRatio = 1.0;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);

        await tester.pumpWidget(
          ProviderScope(
            overrides: harness.providerOverrides,
            child: MediaQuery(
              data: const MediaQueryData(textScaler: TextScaler.linear(1.3)),
              child: const MaterialApp(home: SettingsScreen()),
            ),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.byType(SettingsScreen), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      'Person Form adapts safely to 320px compact screen without overflow',
      (tester) async {
        tester.view.physicalSize = const Size(320, 568);
        tester.view.devicePixelRatio = 1.0;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);

        await tester.pumpWidget(
          ProviderScope(
            overrides: harness.providerOverrides,
            child: const MaterialApp(home: PersonFormScreen()),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.byType(PersonFormScreen), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      'Onboarding screen adapts safely to 320px compact mobile screen',
      (tester) async {
        tester.view.physicalSize = const Size(320, 568);
        tester.view.devicePixelRatio = 1.0;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);

        await tester.pumpWidget(
          ProviderScope(
            overrides: harness.providerOverrides,
            child: const MaterialApp(home: OnboardingScreen()),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.byType(OnboardingScreen), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );
  });
}
