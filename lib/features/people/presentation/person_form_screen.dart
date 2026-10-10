import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/features/birthdays/domain/birthday_engine.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart'
    as b_models;
import 'package:ai_birthday/features/people/data/person_providers.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart'
    as p_models;
import 'package:ai_birthday/features/people/domain/models/relationship.dart'
    as p_rel;
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart'
    as d_chan;
import 'package:ai_birthday/features/people/domain/person.dart';
import 'package:ai_birthday/features/people/domain/person_enums.dart'
    hide DeliveryChannel, RelationshipCloseness;
import 'package:ai_birthday/features/people/domain/person_enums.dart'
    as p_enums;
import 'package:ai_birthday/features/people/domain/person_input.dart';
import 'package:ai_birthday/features/people/domain/person_input_validator.dart';
import 'package:ai_birthday/shared/design_system/design_system.dart';
import 'package:ai_birthday/ui/design_system/app_tokens.dart';

/// Add / edit recipient form with Progressive Disclosure (SSOT §7, §14).
///
/// Designed to minimize initial cognitive overload: Essentials (Name & Birthday)
/// are immediately front-and-center, while Relationship, Contact/Delivery, and
/// AI context facts are progressively disclosed via collapsible sections.
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
  p_models.Person? _existingPerson;
  p_rel.RelationshipCloseness _closeness = p_rel.RelationshipCloseness.casual;
  String _preferredLanguage = 'en';
  d_chan.DeliveryChannel _deliveryChannel = d_chan.DeliveryChannel.whatsapp;
  bool _autoPrepare = true;
  Person? _loaded;
  PersonValidation? _validation;
  bool _saving = false;

  /// True once the user edits any field; gates the discard-confirmation on
  /// back (the only other way to abandon the form).
  bool _dirty = false;

  bool get _isEditing => _loaded != null;

  void _markDirty() {
    if (!_dirty) setState(() => _dirty = true);
  }

  Future<bool?> _confirmDiscard(BuildContext context) {
    return showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Discard changes?'),
        content: const Text('Your unsaved changes will be lost.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Keep Editing'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(ctx).colorScheme.error,
            ),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Discard'),
          ),
        ],
      ),
    );
  }

  @override
  void initState() {
    super.initState();
    final id = widget.personId;
    if (id != null) {
      ref.read(personByIdProvider(id).future).then((person) async {
        if (!mounted) return;
        final memPerson = await ref
            .read(peopleRepositoryProvider)
            .getPerson(id);
        if (memPerson != null) {
          _existingPerson = memPerson;
        }
        var p = person;
        if (p == null && memPerson != null) {
          p = Person(
            id: memPerson.id,
            name: memPerson.name,
            birthdayMonth: memPerson.birthdayMonth,
            birthdayDay: memPerson.birthdayDay,
            birthYear: memPerson.birthYear,
            phoneNumber: memPerson.phoneNumber,
            email: memPerson.email,
            relationship: memPerson.relationship.displayName,
            preferredTone: PreferredTone.values.firstWhere(
              (t) =>
                  t.name.toLowerCase() ==
                  memPerson.preferredTone.name.toLowerCase(),
              orElse: () => PreferredTone.warm,
            ),
            importantFacts: memPerson.importantFacts,
            notes: memPerson.notes,
            timezone: memPerson.timezone,
            createdAt: memPerson.createdAt,
            updatedAt: memPerson.updatedAt,
            version: memPerson.version,
          );
        }
        if (!mounted) return;
        if (p == null) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('This person could not be found.')),
          );
          context.pop();
          return;
        }
        setState(() => _populate(p!));
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
    _closeness =
        _existingPerson?.relationshipCloseness ?? person.relationshipCloseness;
    _preferredLanguage =
        _existingPerson?.preferredLanguage ??
        (person.preferredLanguage.isNotEmpty ? person.preferredLanguage : 'en');
    _deliveryChannel =
        _existingPerson?.preferredDeliveryChannel ??
        d_chan.DeliveryChannel.fromString(person.preferredDeliveryChannel.name);
    _autoPrepare = _existingPerson?.autoPrepare ?? person.autoPrepare;
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
      _dirty = true;
      _month = month;
      if (month != null) {
        final maxDay = month == 2
            ? 29
            : BirthdayEngine.daysInMonth(month, 2024);
        if (_day != null && _day! > maxDay) {
          _day = null;
        }
      } else {
        _day = null;
      }
    });
  }

  void _addFact() {
    HapticFeedback.lightImpact();
    setState(() {
      _dirty = true;
      _facts.add(TextEditingController());
    });
  }

  void _removeFact(int index) {
    HapticFeedback.lightImpact();
    setState(() {
      _dirty = true;
      _facts.removeAt(index).dispose();
    });
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
      // The form and the model share one closeness type, so no name round
      // trip can lose a value (F10).
      relationshipCloseness: _closeness,
      preferredLanguage: _preferredLanguage,
      preferredTone: _tone,
      importantFacts: _facts
          .map((c) => c.text.trim())
          .where((f) => f.isNotEmpty)
          .toList(),
      notes: emptyToNull(_notes.text),
      preferredDeliveryChannel: p_enums.DeliveryChannel.parse(
        _deliveryChannel.name,
      ),
      timezone: emptyToNull(_timezone.text),
      autoPrepare: _autoPrepare,
    );
  }

  Future<void> _save() async {
    HapticFeedback.lightImpact();
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

      if (saved.hasBirthday) {
        final birthdaysRepo = ref.read(birthdaysRepositoryProvider);
        final now = DateTime.now();
        final nextDate = b_models.Birthday.nextBirthdayDate(
          month: saved.birthdayMonth!,
          day: saved.birthdayDay!,
          from: now,
        );
        final existingBirthday = await birthdaysRepo.getBirthdayForPerson(
          saved.id,
        );
        final isToday =
            nextDate.year == now.year &&
            nextDate.month == now.month &&
            nextDate.day == now.day;
        final birthdayChanged =
            existingBirthday != null &&
            (existingBirthday.date.month != nextDate.month ||
                existingBirthday.date.day != nextDate.day);

        final birthdayModel = b_models.Birthday(
          id: existingBirthday?.id ?? 'birthday-${saved.id}',
          personId: saved.id,
          cycleYear: nextDate.year,
          date: nextDate,
          status: birthdayChanged
              ? (isToday
                    ? b_models.BirthdayStatus.reminderDue
                    : b_models.BirthdayStatus.upcoming)
              : existingBirthday?.status ??
                    (isToday
                        ? b_models.BirthdayStatus.reminderDue
                        : b_models.BirthdayStatus.upcoming),
          draftId: birthdayChanged ? null : existingBirthday?.draftId,
          createdAt: existingBirthday?.createdAt ?? now,
          updatedAt: now,
        );
        await birthdaysRepo.saveBirthday(birthdayModel);
      }

      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Saved ${saved.name}.')));
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
    final theme = Theme.of(context);

    return PopScope<void>(
      canPop: !_dirty && !_saving,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop || !_dirty) return;
        final discard = await _confirmDiscard(context);
        if (discard != true) return;
        if (!context.mounted) return;
        Navigator.of(context).pop();
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(title),
          // The default leading slot is too narrow for a labelled button, which
          // wrapped "Cancel" onto two clipped lines on device.
          leadingWidth: 96,
          leading: TextButton(
            // Explicit labeled cancel: the default back affordance would hide
            // the abandon action and silently drop edits. maybePop routes
            // through the PopScope discard confirmation above.
            onPressed: _saving ? null : () => Navigator.of(context).maybePop(),
            child: const Text('Cancel'),
          ),
        ),
        body: SafeArea(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 48),
            children: [
              // STEP 1: Core Essentials (Name & Birthday Date)
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const AppSectionHeader(
                        title: 'Essential Information',
                        isAccent: true,
                      ),
                      const SizedBox(height: 8),
                      TextField(
                        controller: _name,
                        textCapitalization: TextCapitalization.words,
                        maxLength: 120,
                        onChanged: (_) => _markDirty(),
                        decoration: InputDecoration(
                          labelText: 'Name',
                          hintText: 'e.g. Priya',
                          errorText: _errorFor(PersonField.name),
                        ),
                      ),
                      const SizedBox(height: 12),
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
                              onChanged: (value) => setState(() {
                                _dirty = true;
                                _day = value;
                              }),
                              decoration: const InputDecoration(
                                labelText: 'Day',
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _birthYear,
                        keyboardType: TextInputType.number,
                        maxLength: 4,
                        onChanged: (_) => _markDirty(),
                        decoration: InputDecoration(
                          labelText: 'Birth year (optional)',
                          hintText: 'e.g. 1991',
                          errorText: _errorFor(PersonField.birthYear),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),

              // STEP 2: Progressive Disclosure - Relationship & Tone
              Card(
                clipBehavior: Clip.antiAlias,
                child: ExpansionTile(
                  initiallyExpanded: _relationship.text.isNotEmpty,
                  title: const Text(
                    'Relationship & Tone',
                    style: TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
                  ),
                  subtitle: Text(
                    'Customize message dynamics and tone',
                    style: TextStyle(
                      fontSize: 12,
                      color: context.colors.textSecondary,
                    ),
                  ),
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                      child: Column(
                        children: [
                          TextField(
                            controller: _relationship,
                            maxLength: 60,
                            onChanged: (_) => _markDirty(),
                            decoration: InputDecoration(
                              labelText: 'Relationship (optional)',
                              hintText: 'e.g. Best friend, Cousin, Colleague',
                              errorText: _errorFor(PersonField.relationship),
                            ),
                          ),
                          const SizedBox(height: 12),
                          DropdownButtonFormField<PreferredTone>(
                            initialValue: _tone,
                            items: [
                              for (final tone in PreferredTone.values)
                                DropdownMenuItem(
                                  value: tone,
                                  child: Text(_toneLabel(tone)),
                                ),
                            ],
                            onChanged: (value) => setState(() {
                              _dirty = true;
                              _tone = value ?? PreferredTone.warm;
                            }),
                            decoration: const InputDecoration(
                              labelText: 'Preferred tone',
                            ),
                          ),
                          const SizedBox(height: 12),
                          DropdownButtonFormField<p_rel.RelationshipCloseness>(
                            initialValue: _closeness,
                            items: [
                              for (final c
                                  in p_rel.RelationshipCloseness.values)
                                DropdownMenuItem(
                                  value: c,
                                  child: Text(c.displayName),
                                ),
                            ],
                            onChanged: (value) => setState(() {
                              _dirty = true;
                              _closeness =
                                  value ?? p_rel.RelationshipCloseness.casual;
                            }),
                            decoration: const InputDecoration(
                              labelText: 'Closeness',
                            ),
                          ),
                          const SizedBox(height: 12),
                          DropdownButtonFormField<String>(
                            initialValue: _preferredLanguage,
                            items: const [
                              DropdownMenuItem(
                                value: 'en',
                                child: Text('English'),
                              ),
                              DropdownMenuItem(
                                value: 'hi',
                                child: Text('Hindi'),
                              ),
                              DropdownMenuItem(
                                value: 'es',
                                child: Text('Spanish'),
                              ),
                              DropdownMenuItem(
                                value: 'fr',
                                child: Text('French'),
                              ),
                              DropdownMenuItem(
                                value: 'de',
                                child: Text('German'),
                              ),
                            ],
                            onChanged: (value) => setState(() {
                              _dirty = true;
                              _preferredLanguage = value ?? 'en';
                            }),
                            decoration: const InputDecoration(
                              labelText: 'Preferred language',
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // STEP 3: Progressive Disclosure - Contact & Delivery Details
              Card(
                clipBehavior: Clip.antiAlias,
                child: ExpansionTile(
                  initiallyExpanded:
                      _phone.text.isNotEmpty || _email.text.isNotEmpty,
                  title: const Text(
                    'Contact & Delivery',
                    style: TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
                  ),
                  subtitle: Text(
                    'Phone for WhatsApp handoff and timezone',
                    style: TextStyle(
                      fontSize: 12,
                      color: context.colors.textSecondary,
                    ),
                  ),
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                      child: Column(
                        children: [
                          TextField(
                            controller: _phone,
                            keyboardType: TextInputType.phone,
                            onChanged: (_) => _markDirty(),
                            decoration: InputDecoration(
                              labelText: 'Phone number (optional)',
                              hintText: 'e.g. +91 98765 43210',
                              errorText: _errorFor(PersonField.phoneNumber),
                            ),
                          ),
                          const SizedBox(height: 12),
                          TextField(
                            controller: _email,
                            keyboardType: TextInputType.emailAddress,
                            onChanged: (_) => _markDirty(),
                            decoration: InputDecoration(
                              labelText: 'Email (optional)',
                              hintText: 'e.g. priya@example.com',
                              errorText: _errorFor(PersonField.email),
                            ),
                          ),
                          const SizedBox(height: 12),
                          DropdownButtonFormField<d_chan.DeliveryChannel>(
                            initialValue: _deliveryChannel,
                            items: [
                              for (final ch in d_chan.DeliveryChannel.values)
                                DropdownMenuItem(
                                  value: ch,
                                  child: Text(ch.displayName),
                                ),
                            ],
                            onChanged: (value) => setState(() {
                              _dirty = true;
                              _deliveryChannel =
                                  value ?? d_chan.DeliveryChannel.whatsapp;
                            }),
                            decoration: const InputDecoration(
                              labelText: 'Preferred delivery channel',
                            ),
                          ),
                          const SizedBox(height: 12),
                          SwitchListTile.adaptive(
                            contentPadding: EdgeInsets.zero,
                            title: const Text('Prepare drafts automatically'),
                            subtitle: const Text(
                              'Drafts will be generated ahead of birthday for your review',
                            ),
                            value: _autoPrepare,
                            onChanged: (v) => setState(() {
                              _dirty = true;
                              _autoPrepare = v;
                            }),
                          ),
                          const SizedBox(height: 12),
                          TextField(
                            controller: _timezone,
                            textCapitalization: TextCapitalization.none,
                            onChanged: (_) => _markDirty(),
                            decoration: InputDecoration(
                              labelText: 'Timezone (optional, IANA)',
                              hintText: 'e.g. Asia/Kolkata',
                              helperText:
                                  'Used to compute birthdays in their location.',
                              errorText: _errorFor(PersonField.timezone),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // STEP 4: Progressive Disclosure - AI Personalization Facts & Notes
              Card(
                clipBehavior: Clip.antiAlias,
                child: ExpansionTile(
                  initiallyExpanded:
                      _facts.isNotEmpty || _notes.text.isNotEmpty,
                  title: const Text(
                    'Facts & Context for AI',
                    style: TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
                  ),
                  subtitle: Text(
                    'User-provided facts to personalize drafts',
                    style: TextStyle(
                      fontSize: 12,
                      color: context.colors.textSecondary,
                    ),
                  ),
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              const Text(
                                'Verified Facts:',
                                style: TextStyle(fontWeight: FontWeight.bold),
                              ),
                              TextButton.icon(
                                onPressed: _addFact,
                                icon: const Icon(Icons.add, size: 18),
                                label: const Text('Add Fact'),
                              ),
                            ],
                          ),
                          if (_errorFor(PersonField.importantFacts) != null)
                            Text(
                              _errorFor(PersonField.importantFacts)!,
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: theme.colorScheme.error,
                              ),
                            ),
                          for (final (index, controller) in _facts.indexed)
                            Padding(
                              padding: const EdgeInsets.only(bottom: 8),
                              child: TextField(
                                controller: controller,
                                maxLength: 200,
                                onChanged: (_) => _markDirty(),
                                decoration: InputDecoration(
                                  labelText: 'Fact ${index + 1}',
                                  hintText: 'e.g. Loves marathon running',
                                  suffixIcon: IconButton(
                                    icon: const Icon(Icons.close),
                                    tooltip: 'Remove fact',
                                    onPressed: () => _removeFact(index),
                                  ),
                                ),
                              ),
                            ),
                          if (_facts.isEmpty)
                            Padding(
                              padding: const EdgeInsets.only(bottom: 8),
                              child: Text(
                                'No facts added yet. AI will never invent facts.',
                                style: TextStyle(
                                  fontSize: 12,
                                  fontStyle: FontStyle.italic,
                                  color: Colors.grey[600],
                                ),
                              ),
                            ),
                          const SizedBox(height: 12),
                          TextField(
                            controller: _notes,
                            maxLines: 3,
                            maxLength: 2000,
                            onChanged: (_) => _markDirty(),
                            decoration: InputDecoration(
                              labelText: 'Notes (optional)',
                              alignLabelWithHint: true,
                              errorText: _errorFor(PersonField.notes),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),

              // PRIMARY SAVE ACTION
              FilledButton(
                style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(48),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 24,
                    vertical: 12,
                  ),
                ),
                onPressed: _saving ? null : _save,
                child: _saving
                    ? const Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          ),
                          SizedBox(width: 8),
                          Text(
                            'Saving…',
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 16,
                            ),
                          ),
                        ],
                      )
                    : const Text(
                        'Save',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
              ),
              const SizedBox(height: 32),
            ],
          ),
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
