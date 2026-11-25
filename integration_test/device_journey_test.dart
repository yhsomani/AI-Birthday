import 'package:ai_birthday/main.dart' as app;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('physical-device core journeys stay coherent', (tester) async {
    await app.main();
    await tester.pumpAndSettle(const Duration(milliseconds: 300));

    expect(find.text('Never miss a birthday that matters.'), findsOneWidget);
    await tester.tap(find.text('Skip'));
    await tester.pumpAndSettle(const Duration(milliseconds: 300));

    await tester.tap(find.text('People').last);
    await tester.pumpAndSettle(const Duration(milliseconds: 300));
    expect(find.text('People & Contacts'), findsOneWidget);

    await tester.tap(find.text('Add Birthday Contact'));
    await tester.pumpAndSettle(const Duration(milliseconds: 300));
    await tester.enterText(
      find.widgetWithText(TextField, 'Name'),
      'Device Journey Contact',
    );

    final dateFields = find.byType(DropdownButtonFormField<int>);
    await tester.tap(dateFields.at(0));
    await tester.pumpAndSettle();
    await tester.tap(find.text('November').last);
    await tester.pumpAndSettle();
    await tester.tap(dateFields.at(1));
    await tester.pumpAndSettle();
    await tester.tap(find.text('24').last);
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.text('Save'),
      500,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle(const Duration(milliseconds: 300));
    expect(find.text('Device Journey Contact'), findsOneWidget);

    await tester.tap(find.byTooltip('Person actions'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Message Studio'));
    await tester.pumpAndSettle(const Duration(milliseconds: 300));
    expect(find.text('Message for Device Journey Contact'), findsOneWidget);

    final editor = find.byWidgetPredicate(
      (widget) =>
          widget is TextField &&
          widget.decoration?.hintText?.startsWith(
                'Write a birthday greeting',
              ) ==
              true,
    );
    await tester.enterText(
      editor,
      'Happy birthday, and thank you for everything!',
    );
    await tester.pumpAndSettle(const Duration(milliseconds: 700));

    await tester.scrollUntilVisible(
      find.text('Copy Text'),
      400,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.tap(find.text('Copy Text'));
    await tester.pumpAndSettle(const Duration(milliseconds: 300));
    expect(find.textContaining('Copied to clipboard'), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle(const Duration(milliseconds: 300));
    await tester.tap(find.text('Calendar').last);
    await tester.pumpAndSettle(const Duration(milliseconds: 300));
    expect(find.textContaining('November'), findsWidgets);

    await tester.tap(find.text('Activity').last);
    await tester.pumpAndSettle(const Duration(milliseconds: 300));
    expect(find.text('Activity'), findsWidgets);
    expect(find.text('Copied to Clipboard'), findsOneWidget);

    await tester.tap(find.byTooltip('Settings').last);
    await tester.pumpAndSettle(const Duration(milliseconds: 300));
    expect(find.text('Settings'), findsWidgets);
    expect(find.text('Automatic SMS'), findsNothing);
  });
}
