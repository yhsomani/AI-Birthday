import 'dart:convert';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:ai_birthday/features/people/data/contact_csv_service.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/shared/design_system/design_system.dart';

class CsvImportSheet extends StatefulWidget {
  const CsvImportSheet({
    super.key,
    required this.currentPeople,
    required this.csvService,
  });

  final List<Person> currentPeople;
  final ContactCsvService csvService;

  @override
  State<CsvImportSheet> createState() => _CsvImportSheetState();
}

class _CsvImportSheetState extends State<CsvImportSheet> {
  final TextEditingController _textController = TextEditingController();

  @override
  void dispose() {
    _textController.dispose();
    super.dispose();
  }

  Future<void> _pickCsv() async {
    final picked = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: const ['csv'],
      withData: true,
    );
    if (picked == null) return;

    final file = picked.files.single;
    if (file.size > ContactCsvService.maxInputBytes || file.bytes == null) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Choose a CSV file no larger than 1 MB.'),
          ),
        );
      }
      return;
    }

    try {
      _textController.text = utf8.decode(file.bytes!, allowMalformed: false);
    } on FormatException {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('The CSV must be UTF-8 encoded.')),
        );
      }
    }
  }

  void _parseAndClose() {
    final parsed = widget.csvService.parseCsvWithResult(
      _textController.text,
      existingPeople: widget.currentPeople,
    );
    Navigator.of(context).pop(parsed);
  }

  @override
  Widget build(BuildContext context) {
    return AppBottomSheet(
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Expanded(
              child: Text(
                'Import Contacts (CSV)',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
              ),
            ),
            IconButton(
              icon: const Icon(Icons.close),
              tooltip: 'Close',
              onPressed: () {
                HapticFeedback.lightImpact();
                Navigator.of(context).pop();
              },
            ),
          ],
        ),
        const SizedBox(height: 8),
        Text(
          'Paste CSV with columns: Name, Month, Day, Year, Phone, Relationship.',
          style: TextStyle(fontSize: 12, color: context.colors.textSecondary),
        ),
        const SizedBox(height: 8),
        OutlinedButton.icon(
          onPressed: _pickCsv,
          icon: const Icon(Icons.attach_file),
          label: const Text('Choose CSV File'),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _textController,
          maxLines: 5,
          decoration: const InputDecoration(
            border: OutlineInputBorder(),
            hintText: 'Sarah,10,7,1992,+14155552671,Friend\n...',
          ),
        ),
        const SizedBox(height: 12),
        SizedBox(
          width: double.infinity,
          child: FilledButton(
            onPressed: _parseAndClose,
            child: const Text('Parse & Review Candidates'),
          ),
        ),
      ],
    );
  }
}
