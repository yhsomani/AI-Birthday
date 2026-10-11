import 'dart:async';

import 'package:ai_birthday/app/providers.dart' hide loggerProvider;
import 'package:ai_birthday/core/core_providers.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart'
    as b_models;
import 'package:ai_birthday/features/birthdays/domain/repositories/birthdays_repository.dart';
import 'package:ai_birthday/features/people/application/person_service.dart';
import 'package:ai_birthday/features/people/data/person_providers.dart';
import 'package:ai_birthday/features/people/domain/person.dart';
import 'package:ai_birthday/features/people/domain/person_input.dart';
import 'package:ai_birthday/features/people/presentation/person_form_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

class _ControlledPersonService extends Fake implements PersonService {
  _ControlledPersonService(this.completer);

  final Completer<Person> completer;

  @override
  Future<Person> create(PersonDraft draft) => completer.future;
}

class _FakeBirthdaysRepo extends Fake implements BirthdaysRepository {
  @override
  Future<b_models.Birthday?> getBirthdayForPerson(String personId) async =>
      null;

  @override
  Future<void> saveBirthday(b_models.Birthday birthday) async {}
}

void main() {
  testWidgets(
    'shows CircularProgressIndicator alongside Saving… while save is in flight',
    (tester) async {
      tester.view.physicalSize = const Size(800, 2400);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final saveCompleter = Completer<Person>();
      final fakeService = _ControlledPersonService(saveCompleter);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            personServiceProvider.overrideWithValue(fakeService),
            birthdaysRepositoryProvider.overrideWithValue(_FakeBirthdaysRepo()),
            loggerProvider.overrideWithValue(RecordingLogger()),
          ],
          child: const MaterialApp(home: Scaffold(body: PersonFormScreen())),
        ),
      );
      await tester.pumpAndSettle();

      // Before saving: "Save" label is visible, no spinner
      expect(find.text('Save'), findsOneWidget);
      expect(find.text('Saving…'), findsNothing);
      expect(find.byType(CircularProgressIndicator), findsNothing);

      // Fill in Name
      await tester.enterText(find.byType(TextField).first, 'Maya');
      await tester.pumpAndSettle();

      // Select Month (May = 5)
      await tester.tap(
        find.widgetWithText(DropdownButtonFormField<int>, 'Month'),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('May').last);
      await tester.pumpAndSettle();

      // Select Day (12)
      await tester.tap(
        find.widgetWithText(DropdownButtonFormField<int>, 'Day'),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('12').last);
      await tester.pumpAndSettle();

      // Tap Save
      await tester.tap(find.widgetWithText(FilledButton, 'Save'));
      await tester.pump();

      // In flight: button shows spinner and "Saving…"
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.text('Saving…'), findsOneWidget);
      expect(find.text('Save'), findsNothing);

      // Complete the save
      final now = DateTime.utc(2025, 1, 1);
      saveCompleter.complete(
        Person(
          id: 'p-maya',
          name: 'Maya',
          birthdayMonth: 5,
          birthdayDay: 12,
          createdAt: now,
          updatedAt: now,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byType(CircularProgressIndicator), findsNothing);
    },
  );
}
