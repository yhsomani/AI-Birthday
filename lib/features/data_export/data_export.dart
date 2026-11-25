/// Portable, unencrypted, read-only local-data export, format version 1.
///
/// Records use SQLite column names; integer timestamps are Unix seconds and
/// birthday `date` values are calendar-date strings. A selected category may
/// contain zero records. Categories are independent and references can point to
/// unselected records. This format is not a restore or merge protocol.
library;

import 'dart:convert';

import 'package:ai_birthday/core/database/app_database.dart';

/// The explicitly supported operational data; jobs, credentials, account and
/// entitlement state are deliberately excluded.
enum ExportCategory {
  people('persons'),
  birthdays('birthdays'),
  drafts('message_drafts'),
  reminders('reminder_settings_entries'),
  history('delivery_events');

  const ExportCategory(this.table);
  final String table;
}

// A closed field allowlist also makes schema additions fail closed until reviewed.
// Types: s=string, i=integer, ?=nullable. Timestamps are SQLite Unix seconds.
const _fields = <ExportCategory, Map<String, String>>{
  ExportCategory.people: {
    'id': 's',
    'name': 's',
    'birthday_month': 'i?',
    'birthday_day': 'i?',
    'birth_year': 'i?',
    'leap_day_resolution': 's?',
    'phone_number': 's?',
    'email': 's?',
    'relationship': 's',
    'relationship_closeness': 's',
    'preferred_language': 's',
    'preferred_tone': 's',
    'important_facts': 's',
    'notes': 's?',
    'preferred_delivery_channel': 's',
    'timezone': 's?',
    'auto_prepare': 'i',
    'auto_send_policy': 's',
    'created_at': 'i',
    'updated_at': 'i',
    'version': 'i',
    'deleted_at': 'i?',
  },
  ExportCategory.birthdays: {
    'id': 's',
    'person_id': 's',
    'cycle_year': 'i',
    'date': 's',
    'status': 's',
    'draft_id': 's?',
    'created_at': 'i',
    'updated_at': 'i',
  },
  ExportCategory.drafts: {
    'id': 's',
    'birthday_id': 's',
    'person_id': 's',
    'body': 's',
    'tone': 's',
    'length': 's',
    'status': 's',
    'provider_type': 's',
    'variation_index': 'i',
    'revision': 'i',
    'created_at': 'i',
    'updated_at': 'i',
  },
  ExportCategory.reminders: {
    'key': 's',
    'enabled': 'i',
    'kinds': 's',
    'quiet_hours_start_minutes': 'i',
    'quiet_hours_end_minutes': 'i',
  },
  ExportCategory.history: {
    'id': 's',
    'birthday_id': 's',
    'draft_id': 's?',
    'draft_revision': 'i?',
    'body_hash': 's?',
    'channel': 's',
    'outcome': 's',
    'handed_off_at': 'i',
  },
};

const exportWarnings = <String>[
  'This file is unencrypted and may contain private contact details, notes and messages. User-entered text can contain secrets; review your data before exporting.',
  'Legacy, invalid, unknown and deleted records may be included unchanged; linked records in unselected categories are omitted.',
  'Credentials, tokens, entitlement data, account and sync metadata, onboarding state, background jobs and other unsupported local data are not included. This is not a restorable backup.',
];

class DataExportException implements Exception {
  const DataExportException(this.message);
  final String message;
  @override
  String toString() => message;
}

/// Validates the complete closed v1 envelope, records and counts without
/// interpreting or modifying legacy values. Returns an independent JSON tree.
Map<String, dynamic> parseExport(String source) {
  try {
    final decoded = jsonDecode(source);
    if (decoded is! Map<String, dynamic> ||
        !_keys(decoded, {'manifest', 'data'})) {
      throw const FormatException();
    }
    final manifest = decoded['manifest'];
    final data = decoded['data'];
    if (manifest is! Map<String, dynamic> ||
        data is! Map<String, dynamic> ||
        !_keys(manifest, {
          'format',
          'version',
          'created_at',
          'categories',
          'counts',
          'warnings',
        }) ||
        manifest['format'] != 'ai_birthday_local_export' ||
        manifest['version'] is! int ||
        manifest['version'] != 1 ||
        manifest['created_at'] is! String ||
        DateTime.tryParse(manifest['created_at'] as String)?.isUtc != true ||
        manifest['categories'] is! List ||
        manifest['counts'] is! Map<String, dynamic> ||
        manifest['warnings'] is! List ||
        jsonEncode(manifest['warnings']) != jsonEncode(exportWarnings)) {
      throw const FormatException();
    }
    final categories = manifest['categories'] as List;
    final counts = manifest['counts'] as Map<String, dynamic>;
    if (categories.isEmpty ||
        categories.any((v) => v is! String) ||
        categories.toSet().length != categories.length ||
        !_keys(data, categories.cast<String>().toSet()) ||
        !_keys(counts, categories.cast<String>().toSet())) {
      throw const FormatException();
    }
    for (final name in categories) {
      final category = ExportCategory.values
          .where((c) => c.name == name)
          .firstOrNull;
      if (category == null || data[name] is! List) {
        throw const FormatException();
      }
      final records = data[name] as List;
      if (counts[name] is! int || counts[name] != records.length) {
        throw const FormatException();
      }
      final fields = _fields[category]!;
      for (final record in records) {
        if (record is! Map<String, dynamic> ||
            !_keys(record, fields.keys.toSet())) {
          throw const FormatException();
        }
        for (final entry in fields.entries) {
          final value = record[entry.key];
          if (value == null && entry.value.endsWith('?')) continue;
          if (entry.value.startsWith('s') ? value is! String : value is! int) {
            throw const FormatException();
          }
        }
      }
    }
    return decoded;
  } on FormatException {
    throw const DataExportException('Invalid or unsupported export file.');
  }
}

bool _keys(Map<String, dynamic> map, Set<String> keys) =>
    map.length == keys.length && map.keys.toSet().containsAll(keys);

class LocalDataExporter {
  const LocalDataExporter(this.database);
  final AppDatabase database;

  /// Reads one consistent transaction. No credential store, network or job
  /// payload is accessed. Schema drift is rejected rather than silently omitted.
  Future<String> create(Set<ExportCategory> selected) async {
    if (selected.isEmpty) {
      throw const DataExportException('Select at least one category.');
    }
    if (database.schemaVersion != 9) {
      throw const DataExportException(
        'The local data schema has changed; export is unavailable until reviewed.',
      );
    }
    return database.transaction(() async {
      final data = <String, dynamic>{};
      final counts = <String, int>{};
      for (final category in ExportCategory.values.where(selected.contains)) {
        final columns = {
          for (final row
              in await database
                  .customSelect('PRAGMA table_info(${category.table})')
                  .get())
            row.read<String>('name'): row.read<String>('type').toUpperCase(),
        };
        final fields = _fields[category]!;
        if (columns.length != fields.length ||
            !columns.keys.toSet().containsAll(fields.keys) ||
            fields.entries.any(
              (entry) =>
                  columns[entry.key] !=
                  (entry.value.startsWith('s') ? 'TEXT' : 'INTEGER'),
            )) {
          throw const DataExportException(
            'The local data schema has changed; export is unavailable until reviewed.',
          );
        }
        final rows = await database
            .customSelect(
              'SELECT ${fields.keys.join(', ')} FROM ${category.table} ORDER BY ${fields.keys.first}',
            )
            .get();
        data[category.name] = [
          for (final row in rows)
            {for (final key in fields.keys) key: row.data[key]},
        ];
        counts[category.name] = rows.length;
      }
      final result = jsonEncode({
        'manifest': {
          'format': 'ai_birthday_local_export',
          'version': 1,
          'created_at': DateTime.now().toUtc().toIso8601String(),
          'categories': data.keys.toList(),
          'counts': counts,
          'warnings': exportWarnings,
        },
        'data': data,
      });
      parseExport(result);
      return result;
    });
  }
}
