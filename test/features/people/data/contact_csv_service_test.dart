import 'package:flutter_test/flutter_test.dart';
import 'package:ai_birthday/features/people/data/contact_csv_service.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';

void main() {
  group('ContactCsvService (SSOT §18)', () {
    const service = ContactCsvService();
    final now = DateTime.now();

    final testPeople = [
      Person(
        id: 'p1',
        name: 'Sarah Connor',
        birthdayMonth: 10,
        birthdayDay: 7,
        birthYear: 1992,
        phoneNumber: '+14155552671',
        relationship: RelationshipCategory.friend,
        preferredTone: MessageTone.funny,
        importantFacts: const ['Loves running', 'Has a pet dog'],
        notes: 'Best friend from college',
        createdAt: now,
        updatedAt: now,
      ),
      Person(
        id: 'p2',
        name: 'John Doe',
        birthdayMonth: 2,
        birthdayDay: 29,
        relationship: RelationshipCategory.family,
        preferredTone: MessageTone.warm,
        createdAt: now,
        updatedAt: now,
      ),
    ];

    test('exports contacts to valid CSV with escaped fields', () {
      final csv = service.exportToCsv(testPeople);
      expect(csv, contains('Name,Birthday Month,Birthday Day'));
      expect(csv, contains('Sarah Connor,10,7,1992'));
      expect(csv, contains('+14155552671'));
      expect(csv, contains('"Loves running; Has a pet dog"'));
      expect(csv, contains('John Doe,2,29'));
    });

    test('parses and normalizes CSV with valid entries', () {
      const csv =
          '''Name,Birthday Month,Birthday Day,Birth Year,Phone Number,Relationship,Preferred Tone,Facts,Notes
Alice Smith,5,15,1985,+1234567890,Colleague,Professional,Works in finance,Great mentor
Bob Jones,12,25,,+1987654321,Friend,Casual,,
''';

      final candidates = service.parseAndNormalizeCsv(csv);
      expect(candidates.length, 2);

      final alice = candidates[0];
      expect(alice.name, 'Alice Smith');
      expect(alice.birthdayMonth, 5);
      expect(alice.birthdayDay, 15);
      expect(alice.birthYear, 1985);
      expect(alice.phoneNumber, '+1234567890');
      expect(alice.relationship, RelationshipCategory.colleague);
      expect(alice.preferredTone, MessageTone.professional);
      expect(alice.importantFacts, ['Works in finance']);
      expect(alice.notes, 'Great mentor');
      expect(alice.isPotentialDuplicate, isFalse);

      final bob = candidates[1];
      expect(bob.name, 'Bob Jones');
      expect(bob.birthdayMonth, 12);
      expect(bob.birthdayDay, 25);
      expect(bob.birthYear, isNull);
    });

    test(
      'detects duplicate candidates by phone number and name (SSOT §18)',
      () {
        const csv = '''Name,Birthday Month,Birthday Day,Birth Year,Phone Number
Sarah Connor,10,7,1992,+14155552671
Sarah Connor,10,7,1992,+19999999999
Another Person,11,12,1990,+14155552671
Brand New Person,3,10,2000,+15550001111
''';

        final candidates = service.parseAndNormalizeCsv(
          csv,
          existingPeople: testPeople,
        );
        expect(candidates.length, 4);

        // Matches both name and phone
        expect(candidates[0].isPotentialDuplicate, isTrue);
        expect(candidates[0].duplicateWarning, contains('Exact match'));

        // Matches name and birthday
        expect(candidates[1].isPotentialDuplicate, isTrue);
        expect(candidates[1].duplicateWarning, contains('Name'));

        // Matches phone number
        expect(candidates[2].isPotentialDuplicate, isTrue);
        expect(candidates[2].duplicateWarning, contains('Phone match'));

        // Clean new person
        expect(candidates[3].isPotentialDuplicate, isFalse);
      },
    );

    test('parses ISO date string in second column', () {
      const csv = '''Name,Birthday Date
Dr. Brown,1955-11-05
''';
      final candidates = service.parseAndNormalizeCsv(csv);
      expect(candidates.length, 1);
      expect(candidates[0].name, 'Dr. Brown');
      expect(candidates[0].birthdayMonth, 11);
      expect(candidates[0].birthdayDay, 5);
      expect(candidates[0].birthYear, 1955);
    });

    test('exports person without birthday with empty month and day fields', () {
      final personWithoutBday = Person(
        id: 'no-bday',
        name: 'No Birthday Contact',
        phoneNumber: '+14155550000',
        createdAt: now,
        updatedAt: now,
      );
      final csv = service.exportToCsv([personWithoutBday]);
      expect(csv, contains('No Birthday Contact,,,,+14155550000'));
    });

    test(
      'ParsedContactCandidate without birthday sets hasBirthday false and converts to Person cleanly',
      () {
        const candidate = ParsedContactCandidate(
          name: 'Friend Without Birthday',
          phoneNumber: '+19998887777',
        );
        expect(candidate.hasBirthday, isFalse);
        expect(candidate.birthdayMonth, isNull);
        expect(candidate.birthdayDay, isNull);

        final person = candidate.toPerson();
        expect(person.hasBirthday, isFalse);
        expect(person.birthdayMonth, isNull);
        expect(person.birthdayDay, isNull);
        expect(person.name, 'Friend Without Birthday');
      },
    );

    test('multiline notes survive an export and re-import (F19)', () {
      final notes = 'Line one\nLine two, with "quotes"';
      final person = Person(
        id: 'multi',
        name: 'Multi Line',
        birthdayMonth: 6,
        birthdayDay: 15,
        relationship: RelationshipCategory.friend,
        preferredTone: MessageTone.warm,
        notes: notes,
        createdAt: now,
        updatedAt: now,
      );

      final csv = service.exportToCsv([person]);
      final result = service.parseCsvWithResult(csv);

      expect(result.invalidRows, isEmpty);
      expect(result.candidates, hasLength(1));
      expect(result.candidates.single.notes, notes);
      expect(result.candidates.single.birthdayMonth, 6);
      expect(result.candidates.single.birthdayDay, 15);
    });

    test('rejects impossible calendar days but accepts Feb 29 (F19)', () {
      final result = service.parseCsvWithResult(
        'Name,Month,Day\nApril Thirty-One,4,31\nLeap Day,2,29',
      );

      expect(result.candidates.map((c) => c.name), ['Leap Day']);
      expect(result.invalidRows, hasLength(1));
      expect(result.invalidRows.single, contains('April Thirty-One'));
    });

    test(
      'formula-leading names are guarded on export and restored on import (F19)',
      () {
        final person = Person(
          id: 'formula',
          name: '=HYPERLINK("https://example.test")',
          birthdayMonth: 1,
          birthdayDay: 2,
          relationship: RelationshipCategory.friend,
          preferredTone: MessageTone.warm,
          createdAt: now,
          updatedAt: now,
        );

        final csv = service.exportToCsv([person]);
        expect(csv, contains("'=HYPERLINK"));

        final result = service.parseCsvWithResult(csv);
        expect(
          result.candidates.single.name,
          '=HYPERLINK("https://example.test")',
        );
      },
    );
  });
}
