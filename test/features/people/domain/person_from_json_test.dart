import 'package:ai_birthday/features/people/domain/person.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('missing legacy autoPrepare defaults to false', () {
    final person = Person.fromJson({
      'id': 'p1',
      'name': 'Legacy Person',
      'birthdayMonth': 1,
      'birthdayDay': 2,
      'createdAt': DateTime.utc(2026, 1, 1).toIso8601String(),
      'updatedAt': DateTime.utc(2026, 1, 1).toIso8601String(),
    });

    expect(person.autoPrepare, isFalse);
  });
}
