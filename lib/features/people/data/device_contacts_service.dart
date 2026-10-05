/// Service querying Android device contacts via native ContactsContract (SSOT §18, §25).
library;

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import 'package:ai_birthday/features/people/data/contact_csv_service.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';

/// Raw contact extracted from the device's native address book.
class DeviceContact {
  const DeviceContact({
    required this.id,
    required this.name,
    this.phoneNumber,
    this.birthdayString,
  });

  final String id;
  final String name;
  final String? phoneNumber;
  final String? birthdayString;

  factory DeviceContact.fromMap(Map<dynamic, dynamic> map) {
    return DeviceContact(
      id: map['id']?.toString() ?? '',
      name: map['name']?.toString() ?? '',
      phoneNumber: map['phone']?.toString(),
      birthdayString: map['birthday']?.toString(),
    );
  }

  /// Parses Android birthday string into month, day, and optional birthYear.
  ///
  /// Android ContactsContract supports:
  /// - `YYYY-MM-DD` (e.g. `1994-10-07`)
  /// - `--MM-DD` (e.g. `--10-07` where birth year is omitted)
  Map<String, int?>? parseBirthday() {
    if (birthdayString == null || birthdayString!.trim().isEmpty) return null;
    final str = birthdayString!.trim();

    // Check --MM-DD format
    if (str.startsWith('--')) {
      final parts = str.substring(2).split('-');
      if (parts.length == 2) {
        final m = int.tryParse(parts[0]);
        final d = int.tryParse(parts[1]);
        if (m != null && d != null && m >= 1 && m <= 12 && d >= 1 && d <= 31) {
          return {'month': m, 'day': d, 'year': null};
        }
      }
    }

    // Check standard YYYY-MM-DD
    final parts = str.split(RegExp(r'[-/.]'));
    if (parts.length == 3) {
      final p0 = int.tryParse(parts[0]);
      final p1 = int.tryParse(parts[1]);
      final p2 = int.tryParse(parts[2]);
      if (p0 != null && p1 != null && p2 != null) {
        if (p0 > 1000) {
          return {'year': p0, 'month': p1, 'day': p2};
        } else if (p2 > 1000) {
          return {'year': p2, 'month': p0, 'day': p1};
        }
      }
    }
    return null;
  }

  /// Converts this device contact into a candidate for user review.
  ParsedContactCandidate toCandidate({List<Person> existingPeople = const []}) {
    final parsed = parseBirthday();
    final month = parsed?['month'];
    final day = parsed?['day'];
    final year = parsed?['year'];

    // Duplicate detection (SSOT §18)
    String? duplicateWarning;
    for (final existing in existingPeople) {
      final sameName =
          existing.name.trim().toLowerCase() == name.trim().toLowerCase();
      final samePhone =
          phoneNumber != null &&
          existing.phoneNumber != null &&
          _cleanPhone(phoneNumber!) == _cleanPhone(existing.phoneNumber!);

      if (samePhone && sameName) {
        duplicateWarning =
            'Exact match: name and phone match "${existing.name}"';
        break;
      } else if (samePhone) {
        duplicateWarning = 'Phone match: number matches "${existing.name}"';
        break;
      } else if (sameName &&
          parsed != null &&
          existing.hasBirthday &&
          existing.birthdayMonth == month &&
          existing.birthdayDay == day) {
        duplicateWarning = 'Name & birthday match existing "${existing.name}"';
        break;
      } else if (sameName) {
        duplicateWarning = 'Name matches existing "${existing.name}"';
      }
    }

    return ParsedContactCandidate(
      name: name,
      birthdayMonth: month,
      birthdayDay: day,
      birthYear: year,
      phoneNumber: phoneNumber,
      relationship: RelationshipCategory.friend,
      preferredTone: MessageTone.warm,
      importantFacts: const [],
      notes: 'Imported from phone contacts',
      duplicateWarning: duplicateWarning,
    );
  }

  static String _cleanPhone(String phone) =>
      phone.replaceAll(RegExp(r'\D'), '');
}

class DeviceContactsService {
  const DeviceContactsService();

  static const _channel = MethodChannel('com.yashsomani.ai_birthday/contacts');

  /// Checks if READ_CONTACTS permission has been granted.
  Future<bool> hasPermission() async {
    if (WidgetsBinding.instance is! WidgetsFlutterBinding) {
      return true; // Host/test boundary
    }
    try {
      final granted = await _channel.invokeMethod<bool>('hasPermission');
      return granted ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Prompts Android OS permission request dialog for READ_CONTACTS.
  Future<bool> requestPermission() async {
    if (WidgetsBinding.instance is! WidgetsFlutterBinding) {
      return true;
    }
    try {
      final granted = await _channel.invokeMethod<bool>('requestPermission');
      return granted ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Queries the live Android ContactsContract provider and returns real contacts.
  Future<List<DeviceContact>> fetchDeviceContacts() async {
    if (WidgetsBinding.instance is! WidgetsFlutterBinding) {
      return [];
    }
    try {
      final result = await _channel.invokeListMethod<dynamic>(
        'fetchDeviceContacts',
      );
      if (result == null) return [];
      return result
          .whereType<Map<dynamic, dynamic>>()
          .map(DeviceContact.fromMap)
          .where((c) => c.name.isNotEmpty)
          .toList();
    } catch (_) {
      return [];
    }
  }
}
