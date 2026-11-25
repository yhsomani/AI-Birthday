import 'dart:convert';
import 'dart:io';

import 'package:ai_birthday/core/database/app_database.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:drift/native.dart' show SqliteException;
import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'data_export.dart';

/// Push this page with the application's existing AppDatabase instance.
/// Saving uses the platform picker; no app-operated upload or share occurs.
class DataExportPage extends StatefulWidget {
  const DataExportPage({
    super.key,
    required this.database,
    this.logger,
    this.filePicker,
  });
  final AppDatabase database;
  final AppLogger? logger;
  final FilePicker? filePicker;

  @override
  State<DataExportPage> createState() => _DataExportPageState();
}

class _DataExportPageState extends State<DataExportPage> {
  final _selected = <ExportCategory>{};
  late final AppLogger _logger = widget.logger ?? ConsoleAppLogger();
  bool _busy = false;
  String? _message;
  bool get _saveSupported =>
      !kIsWeb && defaultTargetPlatform != TargetPlatform.iOS;

  Future<void> _export() async {
    setState(() {
      _busy = true;
      _message = null;
    });
    var saving = false;
    try {
      final json = await LocalDataExporter(
        widget.database,
      ).create(Set.of(_selected));
      final manifest = parseExport(json)['manifest'] as Map<String, dynamic>;
      final counts = manifest['counts'] as Map<String, dynamic>;
      if (!mounted) return;
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Save unencrypted export?'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                for (final entry in counts.entries)
                  Text('${entry.key}: ${entry.value}'),
                const SizedBox(height: 12),
                const Text(
                  'This file contains sensitive data and is not encrypted. Store it securely. The file picker may offer cloud locations; choose a local destination to avoid uploading it.',
                ),
                const SizedBox(height: 8),
                const Text(
                  'Some Android file providers may not return a result. Check the destination before retrying.',
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Choose destination'),
            ),
          ],
        ),
      );
      if (confirmed != true || !mounted) return;
      saving = true;
      final path = await (widget.filePicker ?? FilePicker.platform).saveFile(
        dialogTitle: 'Save local data export',
        fileName: 'birthday-data-export.json',
        type: FileType.custom,
        allowedExtensions: ['json'],
        bytes: Uint8List.fromList(utf8.encode(json)),
      );
      if (mounted) {
        setState(
          () => _message = path == null
              ? 'No location was returned. Saving may have been cancelled or the provider returned no location; check the destination before retrying.'
              : 'The picker returned but cannot confirm the file contents. Verify the file at your chosen destination and keep it private.',
        );
      }
    } on DataExportException {
      _showFailure(
        'export_validation',
        'Export could not be prepared. The local data format may require an app update.',
      );
    } on SqliteException {
      _showFailure(
        'database',
        'Local data could not be read. No export file was created.',
      );
    } on PlatformException {
      _showPlatformFailure(saving);
    } on MissingPluginException {
      _showPlatformFailure(saving);
    } on FileSystemException {
      _showPlatformFailure(saving);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _showPlatformFailure(bool saving) {
    _showFailure(
      saving ? 'save' : 'database',
      saving
          ? 'The file could not be saved. Check the destination for a partial file and remove it if needed.'
          : 'Local data could not be read. No export file was created.',
    );
  }

  void _showFailure(String category, String message) {
    _logger.warning(
      'data_export',
      'Local export failed',
      errorCategory: category,
    );
    if (mounted) setState(() => _message = message);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Export local data')),
    body: ListView(
      padding: const EdgeInsets.all(16),
      children: [
        for (final warning in exportWarnings)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Text(warning),
          ),
        const Text(
          'Choose categories. Counts are shown before saving. Only the selected categories are included.',
        ),
        for (final category in ExportCategory.values)
          CheckboxListTile(
            title: Text(switch (category) {
              ExportCategory.people => 'People (including deleted records)',
              ExportCategory.birthdays => 'Birthdays',
              ExportCategory.drafts => 'Message drafts',
              ExportCategory.reminders => 'Reminder settings',
              ExportCategory.history => 'Delivery history',
            }),
            value: _selected.contains(category),
            onChanged: _busy
                ? null
                : (value) => setState(() {
                    if (value == true) {
                      _selected.add(category);
                    } else {
                      _selected.remove(category);
                    }
                  }),
          ),
        if (!_saveSupported)
          const Text('Saving is unavailable on this platform.'),
        if (_message != null)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Text(_message!),
          ),
        FilledButton(
          onPressed: _busy || _selected.isEmpty || !_saveSupported
              ? null
              : _export,
          child: Text(_busy ? 'Preparing…' : 'Prepare export'),
        ),
      ],
    ),
  );
}
