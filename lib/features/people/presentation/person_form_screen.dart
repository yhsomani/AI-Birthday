import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/errors/app_failure.dart';
import '../../birthdays/domain/birthday_engine.dart';
import '../data/person_providers.dart';
import '../domain/person.dart';
import '../domain/person_enums.dart';
import '../domain/person_input.dart';
import '../domain/person_input_validator.dart';

/// Add / edit recipient form (SSOT §7, §14).
///
/// Create when [personId] is null; edit otherwise. Validation mirror the
/// service's [PersonInputValidator] so field errors surface on the exact
/// control that caused them.
class PersonFormScreen extends ConsumerStatefulWidget {
  const PersonFormScreen({super.key, this.personId});

  final String? personId;

  @override
  ConsumerState<PersonFormScreen> createState() => _PersonFormScreenState();
}

class _PersonFormScreenState extends ConsumerState<PersonFormScreen> {
  static const List<String> _monthNames = [
    'January',
    'February',
    'March',
    'April',
    'May',
    'June',
    'July',
    'August',
    'September',
    'October',
    'November',
    'December',
  ];

  final _name = TextEditingController();
  final _birthYear = TextEditingController();
  final _phone = TextEditingController();
  final _email = TextEditingController();
  final _relationship = TextEditingController();
  final _notes = TextEditingController();
  final _timezone = TextEditingController();
  final List<TextEditingController> _facts = [];

  int? _month;
  int? _day;
  PreferredTone _tone = PreferredTone.warm;
  Person? _loaded;
  PersonValidation? _validation;
  bool _saving = false;

  bool get _isEditing => _loaded != null;

  @override
  void initState() {
    super.initState();
    final id = widget.personId;
    if (id != null) {
      ref.read(personByIdProvider(id).future).then((person) {
        if (!mounted) return;
        if (person == null) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('This person could not be found.')),
          );
          context.pop();
          return;
        }
        setState(() => _populate(person));
      });
    }
  }

  @override
  void dispose() {
    _name.dispose();
    _birthYear.dispose();
    _phone.dispose();
    _email.dispose();
    _relationship.dispose();
    _notes.dispose();
    _timezone.dispose();
    for (final c in _facts) {
      c.dispose();
    }
    super.dispose();
  }

  void _populate(Person person) {
    _loaded = person;
    _name.text = person.name;
    _month = person.birthdayMonth;
    _day = person.birthdayDay;
    _birthYear.text = person.birthYear?.toString() ?? '';
    _phone.text = person.phoneNumber ?? '';
    _email.text = person.email ?? '';
    _relationship.text = person.relationship;
    _notes.text = person.notes ?? '';
    _timezone.text = person.timezone ?? '';
    _tone = person.preferredTone;
    _facts
      ..clear()
      ..addAll(
        person.importantFacts.map((f) => TextEditingController(text: f)),
      );
  }

  String? _errorFor(PersonField field) => _validation?.errorFor(field)?.label;

  List<int> get _dayOptions {
    final month = _month;
    if (month == null) return const [];
    final maxDay = month == 2 ? 29 : BirthdayEngine.daysInMonth(month, 2024);
    return [for (var d = 1; d <= maxDay; d++) d];
  }

  void _onMonthChanged(int? month) {
    setState(() {
      _month = month;
      final maxDay = month == 2 ? 29 : BirthdayEngine.daysInMonth(month!, 2024);
      if (_day != null && _day! > maxDay) {
        _day = null;
      }
    });
  }

  void _addFact() {
    setState(() => _facts.add(TextEditingController()));
  }

  void _removeFact(int index) {
    setState(() => _facts.removeAt(index).dispose());
  }

  PersonDraft _draft() {
    String? emptyToNull(String value) {
      final trimmed = value.trim();
      return trimmed.isEmpty ? null : trimmed;
    }

    return PersonDraft(
      name: _name.text.trim(),
      birthdayMonth: _month,
      birthdayDay: _day,
      birthYear: int.tryParse(_birthYear.text.trim()),
      phoneNumber: emptyToNull(_phone.text),
      email: emptyToNull(_email.text),
      relationship: _relationship.text.trim(),
      preferredTone: _tone,
      importantFacts: _facts
          .map((c) => c.text.trim())
          .where((f) => f.isNotEmpty)
          .toList(),
      notes: emptyToNull(_notes.text),
      timezone: emptyToNull(_timezone.text),
    );
  }

  Future<void> _save() async {
    final draft = _draft();
    final validation = const PersonInputValidator().validate(draft);
    if (!validation.isValid) {
      setState(() => _validation = validation);
      return;
    }

    setState(() => _saving = true);
    try {
      final service = ref.read(personServiceProvider);
      final saved = _isEditing
          ? await service.update(_loaded!, draft)
          : await service.create(draft);
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('Saved ${saved.name}.')));
      context.pop(saved);
    } on AppFailure catch (failure) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(failure.detail ?? failure.message ?? 'Save failed'),
        ),
      );
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Could not save right now.')),
      );
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final title = _isEditing ? 'Edit birthday' : 'Add birthday';
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            TextField(
              controller: _name,
              textCapitalization: TextCapitalization.words,
              maxLength: 120,
              decoration: InputDecoration(
                labelText: 'Name',
                hintText: 'e.g. Priya',
                errorText: _errorFor(PersonField.name),
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: DropdownButtonFormField<int>(
                    initialValue: _month,
                    items: [
                      for (var m = 1; m <= 12; m++)
                        DropdownMenuItem(
                          value: m,
                          child: Text(_monthNames[m - 1]),
                        ),
                    ],
                    onChanged: _onMonthChanged,
                    decoration: InputDecoration(
                      labelText: 'Month',
                      errorText: _errorFor(PersonField.birthday),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: DropdownButtonFormField<int>(
                    initialValue: _day,
                    items: [
                      for (final d in _dayOptions)
                        DropdownMenuItem(value: d, child: Text('$d')),
                    ],
                    onChanged: (value) => setState(() => _day = value),
                    decoration: const InputDecoration(labelText: 'Day'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _birthYear,
              keyboardType: TextInputType.number,
              maxLength: 4,
              decoration: InputDecoration(
                labelText: 'Birth year (optional)',
                hintText: 'e.g. 1991',
                errorText: _errorFor(PersonField.birthYear),
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _relationship,
              maxLength: 60,
              decoration: InputDecoration(
                labelText: 'Relationship (optional)',
                hintText: 'e.g. Best friend, Cousin',
                errorText: _errorFor(PersonField.relationship),
              ),
            ),
            const SizedBox(height: 16),
            DropdownButtonFormField<PreferredTone>(
              initialValue: _tone,
              items: [
                for (final tone in PreferredTone.values)
                  DropdownMenuItem(value: tone, child: Text(_toneLabel(tone))),
              ],
              onChanged: (value) =>
                  setState(() => _tone = value ?? PreferredTone.warm),
              decoration: const InputDecoration(labelText: 'Preferred tone'),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _phone,
              keyboardType: TextInputType.phone,
              decoration: InputDecoration(
                labelText: 'Phone number (optional)',
                hintText: 'e.g. +91 98765 43210',
                errorText: _errorFor(PersonField.phoneNumber),
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _email,
              keyboardType: TextInputType.emailAddress,
              decoration: InputDecoration(
                labelText: 'Email (optional)',
                hintText: 'e.g. priya@example.com',
                errorText: _errorFor(PersonField.email),
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _timezone,
              textCapitalization: TextCapitalization.none,
              decoration: InputDecoration(
                labelText: 'Timezone (optional, IANA)',
                hintText: 'e.g. Asia/Kolkata',
                helperText: 'Used to compute birthdays in their location.',
                errorText: _errorFor(PersonField.timezone),
              ),
            ),
            const SizedBox(height: 24),
            _SectionHeader(
              title: 'Important facts',
              action: TextButton.icon(
                onPressed: _addFact,
                icon: const Icon(Icons.add),
                label: const Text('Add'),
              ),
            ),
            if (_errorFor(PersonField.importantFacts) != null)
              Text(
                _errorFor(PersonField.importantFacts)!,
                style: Theme.of(context).textTheme.bodySmall
                    ?.copyWith(color: Theme.of(context).colorScheme.error),
              ),
            for (final (index, controller) in _facts.indexed)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: TextField(
                  controller: controller,
                  maxLength: 200,
                  decoration: InputDecoration(
                    labelText: 'Fact ${index + 1}',
                    suffixIcon: IconButton(
                      icon: const Icon(Icons.close),
                      tooltip: 'Remove fact',
                      onPressed: () => _removeFact(index),
                    ),
                  ),
                ),
              ),
            if (_facts.isEmpty)
              Text(
                'Facts help AI personalise messages (optional).',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
            const SizedBox(height: 24),
            TextField(
              controller: _notes,
              maxLines: 4,
              maxLength: 2000,
              decoration: InputDecoration(
                labelText: 'Notes (optional)',
                alignLabelWithHint: true,
                errorText: _errorFor(PersonField.notes),
              ),
            ),
            const SizedBox(height: 24),
            FilledButton(
              onPressed: _saving ? null : _save,
              child: Text(_saving ? 'Saving…' : 'Save'),
            ),
            const SizedBox(height: 16),
          ],
        ),
      ),
    );
  }

  static String _toneLabel(PreferredTone tone) => switch (tone) {
    PreferredTone.warm => 'Warm',
    PreferredTone.funny => 'Funny',
    PreferredTone.emotional => 'Emotional',
    PreferredTone.casual => 'Casual',
    PreferredTone.professional => 'Professional',
    PreferredTone.neutral => 'Neutral',
  };
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({required this.title, this.action});

  final String title;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      children: [
        Expanded(
          child: Text(
            title,
            style: theme.textTheme.titleSmall?.copyWith(
              color: theme.colorScheme.primary,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        ?action,
      ],
    );
  }
}
