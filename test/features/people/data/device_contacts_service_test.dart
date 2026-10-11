import 'package:ai_birthday/features/people/data/device_contacts_service.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('DeviceContact.parseBirthday', () {
    test('parses valid --MM-DD format', () {
      const contact = DeviceContact(
        id: '1',
        name: 'John',
        birthdayString: '--10-07',
      );
      final parsed = contact.parseBirthday();
      expect(parsed, {'month': 10, 'day': 7, 'year': null});
    });

    test('rejects invalid --MM-DD days like April 31 or February 30', () {
      const april31 = DeviceContact(
        id: '1',
        name: 'John',
        birthdayString: '--04-31',
      );
      expect(april31.parseBirthday(), isNull);

      const feb30 = DeviceContact(
        id: '2',
        name: 'Jane',
        birthdayString: '--02-30',
      );
      expect(feb30.parseBirthday(), isNull);
    });

    test('parses valid YYYY-MM-DD format', () {
      const contact = DeviceContact(
        id: '1',
        name: 'John',
        birthdayString: '1994-10-07',
      );
      final parsed = contact.parseBirthday();
      expect(parsed, {'year': 1994, 'month': 10, 'day': 7});
    });

    test('rejects invalid YYYY-MM-DD dates like 1994-04-31', () {
      const contact = DeviceContact(
        id: '1',
        name: 'John',
        birthdayString: '1994-04-31',
      );
      expect(contact.parseBirthday(), isNull);
    });

    test('handles leap day correctly for leap vs non-leap years', () {
      const leap = DeviceContact(
        id: '1',
        name: 'Leap',
        birthdayString: '2024-02-29',
      );
      expect(leap.parseBirthday(), {'year': 2024, 'month': 2, 'day': 29});

      const feb29NoYear = DeviceContact(
        id: '2',
        name: 'LeapNoYear',
        birthdayString: '--02-29',
      );
      expect(feb29NoYear.parseBirthday(), {
        'month': 2,
        'day': 29,
        'year': null,
      });
    });

    test('returns null for null, empty or invalid strings', () {
      const c1 = DeviceContact(id: '1', name: 'A', birthdayString: null);
      expect(c1.parseBirthday(), isNull);

      const c2 = DeviceContact(id: '2', name: 'B', birthdayString: '   ');
      expect(c2.parseBirthday(), isNull);

      const c3 = DeviceContact(
        id: '3',
        name: 'C',
        birthdayString: 'not-a-date',
      );
      expect(c3.parseBirthday(), isNull);
    });
  });
}
