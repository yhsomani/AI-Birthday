/// CSV import, export, normalization, and duplicate detection service (SSOT §18).
library;

import 'package:uuid/uuid.dart';

import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';

/// Parsed contact candidate pending user confirmation.
class ParsedContactCandidate {
  const ParsedContactCandidate({
    required this.name,
    required this.birthdayMonth,
    required this.birthdayDay,
    this.birthYear,
    this.phoneNumber,
    this.email,
    this.relationship = RelationshipCategory.friend,
    this.preferredTone = MessageTone.warm,
    this.importantFacts = const [],
    this.notes,
    this.duplicateWarning,
  });

  final String name;
  final int birthdayMonth;
  final int birthdayDay;
  final int? birthYear;
  final String? phoneNumber;
  final String? email;
  final RelationshipCategory relationship;
  final MessageTone preferredTone;
  final List<String> importantFacts;
  final String? notes;

  /// Human-readable explanation if this candidate looks like a duplicate (SSOT §18).
  final String? duplicateWarning;

  bool get isPotentialDuplicate => duplicateWarning != null;

  Person toPerson({String? id}) {
    final now = DateTime.now();
    return Person(
      id: id ?? const Uuid().v4(),
      name: name,
      birthdayMonth: birthdayMonth,
      birthdayDay: birthdayDay,
      birthYear: birthYear,
      phoneNumber: phoneNumber,
      email: email,
      relationship: relationship,
      preferredTone: preferredTone,
      importantFacts: importantFacts,
      notes: notes,
      createdAt: now,
      updatedAt: now,
    );
  }
}

class ContactCsvService {
  const ContactCsvService();

  /// Exports [people] to RFC-4180 compliant CSV format.
  String exportToCsv(List<Person> people) {
    final buffer = StringBuffer();
    // CSV Header
    buffer.writeln(
      'Name,Birthday Month,Birthday Day,Birth Year,Phone Number,Relationship,Preferred Tone,Facts,Notes',
    );

    for (final person in people) {
      final fields = [
        _escapeCsv(person.name),
        person.birthdayMonth.toString(),
        person.birthdayDay.toString(),
        person.birthYear?.toString() ?? '',
        _escapeCsv(person.phoneNumber ?? ''),
        _escapeCsv(person.relationship.displayName),
        _escapeCsv(person.preferredTone.displayName),
        _escapeCsv(person.importantFacts.join('; ')),
        _escapeCsv(person.notes ?? ''),
      ];
      buffer.writeln(fields.join(','));
    }

    return buffer.toString();
  }

  /// Parses CSV [csvContent] and identifies potential duplicates against [existingPeople].
  List<ParsedContactCandidate> parseAndNormalizeCsv(
    String csvContent, {
    List<Person> existingPeople = const [],
  }) {
    final lines = csvContent.split(RegExp(r'\r?\n'));
    if (lines.isEmpty) return [];

    final candidates = <ParsedContactCandidate>[];
    bool isFirstLine = true;

    for (final rawLine in lines) {
      final line = rawLine.trim();
      if (line.isEmpty) continue;

      final fields = _parseCsvLine(line);
      if (fields.isEmpty) continue;

      // Skip header row if detected
      if (isFirstLine) {
        isFirstLine = false;
        final firstField = fields.first.toLowerCase();
        if (firstField == 'name' || firstField.contains('recipient')) {
          continue;
        }
      }

      final name = fields.isNotEmpty ? fields[0].trim() : '';
      if (name.isEmpty) continue;

      int? month;
      int? day;
      int? year;

      if (fields.length >= 3) {
        month = int.tryParse(fields[1].trim());
        day = int.tryParse(fields[2].trim());
      }

      // If month/day are null or out of range, try parsing as a unified date string (e.g. "1990-05-12" or "05/12")
      if (month == null ||
          day == null ||
          month < 1 ||
          month > 12 ||
          day < 1 ||
          day > 31) {
        if (fields.length >= 2) {
          final parsed = _parseDateString(fields[1].trim());
          if (parsed != null) {
            month = parsed['month'];
            day = parsed['day'];
            year = parsed['year'];
          }
        }
      }

      // Validation fallback: Skip if valid birthday cannot be resolved
      if (month == null ||
          day == null ||
          month < 1 ||
          month > 12 ||
          day < 1 ||
          day > 31) {
        continue;
      }

      if (year == null && fields.length >= 4) {
        year = int.tryParse(fields[3].trim());
      }

      final phone = fields.length >= 5 && fields[4].trim().isNotEmpty
          ? fields[4].trim()
          : null;
      final relStr = fields.length >= 6 ? fields[5].trim() : null;
      final toneStr = fields.length >= 7 ? fields[6].trim() : null;
      final factsStr = fields.length >= 8 ? fields[7].trim() : null;
      final notesStr = fields.length >= 9 ? fields[8].trim() : null;

      final relationship = RelationshipCategory.fromString(relStr);
      final preferredTone = MessageTone.fromString(toneStr);
      final facts = factsStr != null && factsStr.isNotEmpty
          ? factsStr
                .split(';')
                .map((s) => s.trim())
                .where((s) => s.isNotEmpty)
                .toList()
          : <String>[];

      // Duplicate candidate check (SSOT §18: Detect duplicate candidates, never auto-merge on name alone)
      String? duplicateWarning;
      for (final existing in existingPeople) {
        final sameName =
            existing.name.trim().toLowerCase() == name.toLowerCase();
        final samePhone =
            phone != null &&
            existing.phoneNumber != null &&
            _cleanPhone(phone) == _cleanPhone(existing.phoneNumber!);

        if (samePhone && sameName) {
          duplicateWarning =
              'Exact match: name and phone match "${existing.name}"';
          break;
        } else if (samePhone) {
          duplicateWarning = 'Phone match: number matches "${existing.name}"';
          break;
        } else if (sameName &&
            existing.birthdayMonth == month &&
            existing.birthdayDay == day) {
          duplicateWarning =
              'Name & birthday match existing contact "${existing.name}"';
          break;
        } else if (sameName) {
          duplicateWarning = 'Name matches existing contact "${existing.name}"';
        }
      }

      candidates.add(
        ParsedContactCandidate(
          name: name,
          birthdayMonth: month,
          birthdayDay: day,
          birthYear: year,
          phoneNumber: phone,
          relationship: relationship,
          preferredTone: preferredTone,
          importantFacts: facts,
          notes: notesStr,
          duplicateWarning: duplicateWarning,
        ),
      );
    }

    return candidates;
  }

  static String _cleanPhone(String phone) =>
      phone.replaceAll(RegExp(r'\D'), '');

  static String _escapeCsv(String value) {
    if (value.contains(',') ||
        value.contains('"') ||
        value.contains('\n') ||
        value.contains(';')) {
      return '"${value.replaceAll('"', '""')}"';
    }
    return value;
  }

  static List<String> _parseCsvLine(String line) {
    final fields = <String>[];
    final buffer = StringBuffer();
    bool insideQuotes = false;

    for (int i = 0; i < line.length; i++) {
      final char = line[i];
      if (char == '"') {
        if (insideQuotes && i + 1 < line.length && line[i + 1] == '"') {
          buffer.write('"');
          i++; // Skip escaped quote
        } else {
          insideQuotes = !insideQuotes;
        }
      } else if (char == ',' && !insideQuotes) {
        fields.add(buffer.toString());
        buffer.clear();
      } else {
        buffer.write(char);
      }
    }
    fields.add(buffer.toString());
    return fields;
  }

  static Map<String, int?>? _parseDateString(String dateStr) {
    final parts = dateStr.split(RegExp(r'[-/.]'));
    if (parts.length == 3) {
      final p0 = int.tryParse(parts[0]);
      final p1 = int.tryParse(parts[1]);
      final p2 = int.tryParse(parts[2]);
      if (p0 != null && p1 != null && p2 != null) {
        if (p0 > 1000) {
          // YYYY-MM-DD
          return {'year': p0, 'month': p1, 'day': p2};
        } else if (p2 > 1000) {
          // MM/DD/YYYY
          return {'year': p2, 'month': p0, 'day': p1};
        }
      }
    } else if (parts.length == 2) {
      final p0 = int.tryParse(parts[0]);
      final p1 = int.tryParse(parts[1]);
      if (p0 != null &&
          p1 != null &&
          p0 >= 1 &&
          p0 <= 12 &&
          p1 >= 1 &&
          p1 <= 31) {
        return {'year': null, 'month': p0, 'day': p1};
      }
    }
    return null;
  }
}
