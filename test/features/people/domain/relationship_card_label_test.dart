import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('relationshipCardLabel (device UI fix)', () {
    test('an unset category shows the chosen closeness, not "Other"', () {
      expect(
        relationshipCardLabel(
          RelationshipCategory.other,
          RelationshipCloseness.casual,
        ),
        'Casual',
      );
    });

    test('a chosen category is shown as its own label', () {
      expect(
        relationshipCardLabel(
          RelationshipCategory.family,
          RelationshipCloseness.distant,
        ),
        'Family',
      );
    });
  });
}
