import 'package:ai_birthday/features/people/presentation/person_form_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('preferred language accepts an editable language tag', (
    tester,
  ) async {
    await tester.pumpWidget(const MaterialApp(home: PersonFormScreen()));
    await tester.pumpAndSettle();

    await tester.ensureVisible(find.text('Relationship & Tone'));
    await tester.tap(find.text('Relationship & Tone'));
    await tester.pumpAndSettle();

    final languageField = find.byType(TextFormField);
    expect(languageField, findsOneWidget);
    expect(find.text('Preferred language code'), findsOneWidget);
    expect(find.byType(DropdownButtonFormField<String>), findsNothing);

    await tester.enterText(languageField, 'pt-BR');
    expect(find.text('pt-BR'), findsOneWidget);
  });
}
