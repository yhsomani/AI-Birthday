/// A1-005: relationship is a closed choice, so what the user picks is what is
/// stored (free text used to be silently coerced to "Other").
library;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/presentation/person_form_screen.dart';

void main() {
  testWidgets('relationship is a dropdown offering every category', (
    tester,
  ) async {
    await tester.pumpWidget(const MaterialApp(home: PersonFormScreen()));
    await tester.pumpAndSettle();

    await tester.ensureVisible(find.text('Relationship & Tone'));
    await tester.tap(find.text('Relationship & Tone'));
    await tester.pumpAndSettle();

    expect(find.text('Relationship (optional)'), findsNothing);
    final dropdown = find.byType(DropdownButtonFormField<RelationshipCategory>);
    expect(dropdown, findsOneWidget);

    await tester.ensureVisible(dropdown);
    await tester.tap(dropdown);
    await tester.pumpAndSettle();
    for (final category in RelationshipCategory.values) {
      expect(find.text(category.displayName), findsWidgets);
    }
    expect(tester.takeException(), isNull);
  });
}
