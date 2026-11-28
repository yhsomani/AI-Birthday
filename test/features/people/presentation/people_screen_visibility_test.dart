import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/app/theme/app_theme.dart';
import 'package:ai_birthday/features/birthdays/domain/birthday_engine.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/people/data/device_contacts_service.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/features/people/presentation/people_screen.dart';

import '../../../e2e/harness/test_harness.dart';

class _LeapDeviceContactsService extends DeviceContactsService {
  const _LeapDeviceContactsService();

  @override
  Future<bool> hasPermission() async => true;

  @override
  Future<List<DeviceContact>> fetchDeviceContacts() async => const [
    DeviceContact(
      id: 'leap-device',
      name: 'Leap Contact',
      phoneNumber: '+15550001111',
      birthdayString: '2000-02-29',
    ),
  ];
}

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

    testWidgets('unresolved leap day hides Message Studio until repaired', (
      tester,
    ) async {
      final now = DateTime.now();
      await pumpPeople(
        tester,
        Person(
          id: 'p-leap',
          name: 'Legacy Leap',
          birthdayMonth: 2,
          birthdayDay: 29,
          createdAt: now,
          updatedAt: now,
        ),
      );

      expect(find.textContaining('choose celebration date'), findsOneWidget);
      await tester.tap(find.byTooltip('Person actions'));
      await tester.pumpAndSettle();
      expect(find.text('Message Studio'), findsNothing);
      expect(find.text('Edit'), findsOneWidget);
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

  group('People CSV sheets at 200% text scale (audit 05 P0-1)', () {
    late E2ETestHarness harness;

    setUp(() => harness = E2ETestHarness()..setUp());
    tearDown(() => harness.tearDown());

    testWidgets('paste and review/import sheets never overflow at 200%', (
      tester,
    ) async {
      // Narrow 360dp viewport at 2.0x text scale — where the fixed-height
      // CSV sheet previously crashed (audit 05 P0-1).
      tester.view.physicalSize = const Size(360, 640);
      tester.view.devicePixelRatio = 1.0;
      tester.platformDispatcher.textScaleFactorTestValue = 2.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

      final now = DateTime.now();
      final existingSarah = Person(
        id: 'p-sarah',
        name: 'Sarah',
        birthdayMonth: 10,
        birthdayDay: 7,
        birthYear: 1992,
        phoneNumber: '+14155552671',
        relationship: RelationshipCategory.friend,
        relationshipCloseness: RelationshipCloseness.close,
        preferredLanguage: 'en',
        preferredTone: MessageTone.warm,
        preferredDeliveryChannel: DeliveryChannel.whatsapp,
        createdAt: now,
        updatedAt: now,
        version: 1,
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            ...harness.providerOverrides,
            peopleStreamProvider.overrideWith(
              (ref) => Stream.value([existingSarah]),
            ),
          ],
          // The status-tone colors come from the AppPalette ThemeExtension,
          // so pump the real app theme (audit 05 P1-3/P0-1).
          child: MaterialApp(
            theme: AppTheme.light,
            darkTheme: AppTheme.dark,
            home: const PeopleScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Open the CSV paste sheet.
      await tester.tap(find.byTooltip('More actions'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Import CSV'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.text('Import Contacts (CSV)'), findsOneWidget);

      // Paste a 3-row CSV; Sarah already exists (duplicate candidate). Scope
      // to the sheet: the People screen now also has a search TextField.
      await tester.enterText(
        find.descendant(
          of: find.byType(BottomSheet),
          matching: find.byType(TextField),
        ),
        'Sarah,10,7,1992,+14155552671,Friend\n'
        'Alex,3,15,1988,+14155551234,Family\n'
        'Jamie,8,22,1995,+14155559876,Coworker',
      );
      await tester.pump();

      // Review sheet opens: scroll-safe, zero overflow. The button can sit
      // below the fold at 2.0× — the sheet scrolls, so bring it into view.
      await tester.ensureVisible(find.text('Parse & Review Candidates'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Parse & Review Candidates'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.text('Review CSV Contacts (3)'), findsOneWidget);
      // Sarah duplicates the existing contact → warning chip shown.
      expect(find.textContaining('Exact match'), findsOneWidget);
      // Sarah is a potential duplicate → unselected; Alex + Jamie selected.
      expect(find.text('Import Selected (2)'), findsOneWidget);
    });

    testWidgets('Feb 29 CSV row remains repairable in review', (tester) async {
      tester.view.physicalSize = const Size(360, 800);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            ...harness.providerOverrides,
            peopleStreamProvider.overrideWith((ref) => Stream.value(const [])),
          ],
          child: MaterialApp(
            theme: AppTheme.light,
            darkTheme: AppTheme.dark,
            home: const PeopleScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byTooltip('More actions'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Import CSV').last);
      await tester.pumpAndSettle();
      await tester.enterText(
        find.descendant(
          of: find.byType(BottomSheet),
          matching: find.byType(TextField),
        ),
        'Name,Month,Day\nCSV Leap,2,29',
      );
      await tester.tap(find.text('Parse & Review Candidates'));
      await tester.pumpAndSettle();

      expect(find.text('CSV Leap'), findsOneWidget);
      expect(find.textContaining('Needs repair'), findsOneWidget);
      expect(find.text('Import Selected (0)'), findsOneWidget);
      await tester.tap(find.text('In non-leap years, celebrate on'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('February 28').last);
      await tester.pumpAndSettle();
      expect(find.text('Import Selected (1)'), findsOneWidget);
    });

    testWidgets('Feb 29 device contact can be repaired before import', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(360, 800);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            ...harness.providerOverrides,
            peopleStreamProvider.overrideWith((ref) => Stream.value(const [])),
            deviceContactsServiceProvider.overrideWithValue(
              const _LeapDeviceContactsService(),
            ),
          ],
          child: MaterialApp(
            theme: AppTheme.light,
            darkTheme: AppTheme.dark,
            home: const PeopleScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byTooltip('More actions'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Import from Phone').last);
      await tester.pumpAndSettle();

      expect(find.textContaining('Needs repair'), findsOneWidget);
      expect(find.text('Import Selected (0)'), findsOneWidget);

      await tester.tap(find.text('In non-leap years, celebrate on'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('March 1').last);
      await tester.pumpAndSettle();

      expect(find.textContaining('Needs repair'), findsNothing);
      expect(find.text('Import Selected (1)'), findsOneWidget);
      await tester.ensureVisible(find.text('Import Selected (1)'));
      await tester.tap(find.text('Import Selected (1)'));
      await tester.pumpAndSettle();

      final imported = await harness.peopleRepo.getPersonById(
        (await harness.peopleRepo.listActivePeople()).single.id,
      );
      expect(imported!.leapDayResolution, LeapDayResolution.mar1);
      expect(
        await harness.birthdaysRepo.getBirthdayForPerson(imported.id),
        isNotNull,
      );
    });
  });
}
