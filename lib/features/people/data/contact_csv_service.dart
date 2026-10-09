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
    this.birthdayMonth,
    this.birthdayDay,
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
  final int? birthdayMonth;
  final int? birthdayDay;
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

  /// Returns true if this candidate has a valid birthday specified.
  bool get hasBirthday => _isValidMonthDay(birthdayMonth, birthdayDay);

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

/// Result of a CSV parse operation containing valid candidates and details on skipped rows.
class CsvParseResult {
  const CsvParseResult({required this.candidates, this.invalidRows = const []});

  final List<ParsedContactCandidate> candidates;
  final List<String> invalidRows;
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
        person.hasBirthday ? person.birthdayMonth.toString() : '',
        person.hasBirthday ? person.birthdayDay.toString() : '',
        person.birthYear?.toString() ?? '',
        _escapeCsv(person.phoneNumber ?? '', guardFormulas: false),
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
    return parseCsvWithResult(
      csvContent,
      existingPeople: existingPeople,
    ).candidates;
  }

  /// Parses CSV [csvContent] and returns candidates plus detailed invalid row messages.
  CsvParseResult parseCsvWithResult(
    String csvContent, {
    List<Person> existingPeople = const [],
  }) {
    final records = _splitRecords(csvContent);
    if (records.isEmpty) return const CsvParseResult(candidates: []);

    final candidates = <ParsedContactCandidate>[];
    final invalidRows = <String>[];
    bool isFirstLine = true;
    int rowNumber = 0;

    for (final record in records) {
      rowNumber++;
      if (record.trim().isEmpty) continue;

      // Parse the whole record, not the trimmed text, so quoted newlines and
      // trailing spaces inside a quoted field survive.
      final fields = _parseCsvLine(record);
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
      if (name.isEmpty) {
        invalidRows.add('Row $rowNumber: Name is missing');
        continue;
      }

      int? month;
      int? day;
      int? year;

      if (fields.length >= 3) {
        month = int.tryParse(fields[1].trim());
        day = int.tryParse(fields[2].trim());
      }

      // If month/day are null or impossible, try parsing as a unified date string (e.g. "1990-05-12" or "05/12")
      if (!_isValidMonthDay(month, day)) {
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
      if (!_isValidMonthDay(month, day)) {
        invalidRows.add(
          'Row $rowNumber ("$name"): Missing or invalid birthday',
        );
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
            existing.hasBirthday &&
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

    return CsvParseResult(candidates: candidates, invalidRows: invalidRows);
  }

  static String _cleanPhone(String phone) =>
      phone.replaceAll(RegExp(r'\D'), '');

  static String _escapeCsv(String value, {bool guardFormulas = true}) {
    // Spreadsheet-safe export: a leading = + - @ or tab/CR would be evaluated as
    // a formula when opened in a spreadsheet, so a single quote is prefixed.
    // Phone numbers are exempt: a leading + is part of the number itself.
    final guarded = guardFormulas && _formulaGuardPattern.hasMatch(value)
        ? "'$value"
        : value;
    if (guarded.contains(',') ||
        guarded.contains('"') ||
        guarded.contains('\n') ||
        guarded.contains('\r') ||
        guarded.contains(';')) {
      return '"${guarded.replaceAll('"', '""')}"';
    }
    return guarded;
  }

  static final RegExp _formulaGuardPattern = RegExp(r'^[=+\-@\t\r]');

  /// Splits [content] into records. Line breaks inside a quoted field belong to
  /// that field, so only unquoted CR, LF or CRLF end a record.
  static List<String> _splitRecords(String content) {
    final records = <String>[];
    final buffer = StringBuffer();
    bool insideQuotes = false;

    for (int i = 0; i < content.length; i++) {
      final char = content[i];
      if (char == '"') {
        // An escaped "" toggles twice, so the quote state stays correct.
        insideQuotes = !insideQuotes;
        buffer.write(char);
      } else if (!insideQuotes && (char == '\n' || char == '\r')) {
        if (char == '\r' && i + 1 < content.length && content[i + 1] == '\n') {
          i++;
        }
        records.add(buffer.toString());
        buffer.clear();
      } else {
        buffer.write(char);
      }
    }
    records.add(buffer.toString());
    return records;
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
        fields.add(_stripFormulaGuard(buffer.toString()));
        buffer.clear();
      } else {
        buffer.write(char);
      }
    }
    fields.add(_stripFormulaGuard(buffer.toString()));
    return fields;
  }

  /// Removes the guard added by [_escapeCsv] so an exported file round-trips.
  static String _stripFormulaGuard(String field) {
    if (field.length > 1 &&
        field[0] == "'" &&
        _formulaGuardPattern.hasMatch(field.substring(1))) {
      return field.substring(1);
    }
    return field;
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

/// True when [month]/[day] is a real calendar day. Feb 29 is accepted because a
/// birthday may omit the year, so the leap day cannot be ruled out.
bool _isValidMonthDay(int? month, int? day) {
  if (month == null || day == null || month < 1 || month > 12 || day < 1) {
    return false;
  }
  const daysInMonth = [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  return day <= daysInMonth[month - 1];
}
