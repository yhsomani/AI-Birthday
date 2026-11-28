import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/features/birthdays/domain/birthday_engine.dart';
import 'package:ai_birthday/features/people/data/contact_csv_service.dart';
import 'package:ai_birthday/features/people/data/person_providers.dart'
    show personServiceProvider;
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/person_input.dart';
import 'package:ai_birthday/features/people/presentation/csv_import_sheet.dart';
import 'package:ai_birthday/shared/design_system/design_system.dart';

class PeopleScreen extends ConsumerStatefulWidget {
  const PeopleScreen({super.key});

  @override
  ConsumerState<PeopleScreen> createState() => _PeopleScreenState();
}

class _PeopleScreenState extends ConsumerState<PeopleScreen> {
  static const BirthdayEngine _engine = BirthdayEngine();

  final TextEditingController _searchController = TextEditingController();
  String _query = '';
  RelationshipCategory? _relationshipFilter;
  int? _birthdayMonthFilter;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  NextBirthday? _next(Person person) {
    if (!person.hasBirthday ||
        (person.isLeapDayBirthday && person.leapDayResolution == null)) {
      return null;
    }
    return _engine.computeNext(
      month: person.birthdayMonth!,
      day: person.birthdayDay!,
      birthYear: person.birthYear,
      timezoneName: person.timezone,
      leapDay: person.leapDayResolution,
    );
  }

  String _countdownLabel(NextBirthday? next) {
    if (next == null) return '';
    // Single-sourced with CountdownChip (audit 05 P2-6).
    return CountdownChip.labelFor(
      daysUntil: next.daysUntil,
      isToday: next.isToday,
    );
  }

  Future<void> _exportCsv(
    BuildContext context,
    WidgetRef ref,
    List<Person> people,
  ) async {
    HapticFeedback.lightImpact();
    if (people.isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('No contacts to export.')));
      return;
    }

    final csvService = ref.read(contactCsvServiceProvider);
    final csvText = csvService.exportToCsv(people);

    final shareService = ref.read(nativeShareServiceProvider);
    final shared = await shareService.shareText(
      text: csvText,
      title: 'AI-Birthday Contacts Export (${people.length})',
    );

    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            shared
                ? 'CSV export is ready to share.'
                : 'Could not open the share sheet. Your contact data was not exported.',
          ),
        ),
      );
    }
  }

  Future<void> _showImportCsvSheet(
    BuildContext context,
    WidgetRef ref,
    List<Person> currentPeople,
  ) async {
    final parsedCandidates = await showModalBottomSheet<CsvParseResult>(
      context: context,
      isScrollControlled: true,
      builder: (_) => CsvImportSheet(
        currentPeople: currentPeople,
        csvService: ref.read(contactCsvServiceProvider),
      ),
    );

    if (parsedCandidates == null || !context.mounted) return;
    final result = parsedCandidates;

    if (result.candidates.isEmpty) {
      await showDialog<void>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: const Text('CSV repair summary'),
          content: SizedBox(
            width: double.maxFinite,
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '0 of ${result.totalRows} rows are ready. No records were saved.',
                  ),
                  if (result.mappedColumns.isNotEmpty)
                    Text('Mapped: ${result.mappedColumns.join(', ')}'),
                  for (final issue in result.invalidRows) ...[
                    const SizedBox(height: 8),
                    Text(issue),
                  ],
                ],
              ),
            ),
          ),
          actions: [
            FilledButton(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: const Text('Close'),
            ),
          ],
        ),
      );
      return;
    }

    await _reviewAndImportCandidates(
      context,
      ref,
      result.candidates,
      title: 'Review CSV Contacts',
      repairIssues: result.invalidRows,
      mappedColumns: result.mappedColumns,
    );
  }

  Future<void> _reviewAndImportCandidates(
    BuildContext context,
    WidgetRef ref,
    List<ParsedContactCandidate> initialCandidates, {
    required String title,
    List<String> repairIssues = const [],
    List<String> mappedColumns = const [],
  }) async {
    final parsedCandidates = [...initialCandidates];
    if (parsedCandidates.isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('No contacts to review.')));
      return;
    }

    final selectedIndices = <int>{
      for (int i = 0; i < parsedCandidates.length; i++)
        if (parsedCandidates[i].isImportable &&
            !parsedCandidates[i].isPotentialDuplicate)
          i,
    };

    // Calculate once to avoid multiple O(N) evaluations in the builder.
    int duplicateCount() => parsedCandidates
        .where((candidate) => candidate.isPotentialDuplicate)
        .length;
    int repairCount() =>
        parsedCandidates.where((candidate) => !candidate.isImportable).length +
        repairIssues.length;
    int readyCount() =>
        parsedCandidates.where((candidate) => candidate.isImportable).length;

    final confirmed = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (reviewContext) => StatefulBuilder(
        builder: (ctx, setReviewState) => AppBottomSheet(
          children: [
            // Wrap (not Row): at large text scales the action wraps onto its
            // own line instead of starving the title (audit 05 P0-1).
            Wrap(
              alignment: WrapAlignment.spaceBetween,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 8,
              children: [
                Text(
                  '$title (${parsedCandidates.length})',
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 18,
                  ),
                ),
                TextButton(
                  onPressed: () {
                    setReviewState(() {
                      if (selectedIndices.length == readyCount()) {
                        selectedIndices.clear();
                      } else {
                        selectedIndices.addAll([
                          for (var i = 0; i < parsedCandidates.length; i++)
                            if (parsedCandidates[i].isImportable) i,
                        ]);
                      }
                    });
                  },
                  child: Text(
                    selectedIndices.length == readyCount()
                        ? 'Deselect All'
                        : 'Select All',
                  ),
                ),
              ],
            ),
            Text(
              '${parsedCandidates.length} candidates: ${selectedIndices.length} selected, ${readyCount()} ready, '
              '${duplicateCount()} duplicate${duplicateCount() == 1 ? '' : 's'}, '
              '${repairCount()} need repair.',
              style: TextStyle(
                fontSize: 12,
                color: ctx.colors.textSecondary,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              'Potential duplicates are unselected by default for safety.',
              style: TextStyle(
                fontSize: 11,
                color: context.colors.textSecondary,
              ),
            ),
            if (mappedColumns.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(
                  'Mapped columns: ${mappedColumns.join(', ')}',
                  style: TextStyle(
                    fontSize: 11,
                    color: ctx.colors.textSecondary,
                  ),
                ),
              ),
            if (repairIssues.isNotEmpty)
              ExpansionTile(
                tilePadding: EdgeInsets.zero,
                title: Text('${repairIssues.length} CSV rows need repair'),
                children: [
                  for (final issue in repairIssues)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 6),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Icon(Icons.warning_amber_rounded, size: 16),
                          const SizedBox(width: 6),
                          Expanded(child: Text(issue)),
                        ],
                      ),
                    ),
                ],
              ),
            const Divider(),
            ConstrainedBox(
              constraints: BoxConstraints(
                maxHeight: MediaQuery.of(ctx).size.height * 0.5,
              ),
              child: ListView.builder(
                itemCount: parsedCandidates.length,
                itemBuilder: (idxCtx, i) {
                  final c = parsedCandidates[i];
                  final isSelected = selectedIndices.contains(i);
                  return CheckboxListTile(
                    value: isSelected,
                    onChanged: c.isImportable
                        ? (val) {
                            setReviewState(() {
                              if (val == true) {
                                selectedIndices.add(i);
                              } else {
                                selectedIndices.remove(i);
                              }
                            });
                          }
                        : null,
                    title: Text(
                      c.name,
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                    subtitle: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          c.hasBirthday
                              ? 'Birthday: ${c.birthdayMonth}/${c.birthdayDay}'
                                    '${c.birthYear != null ? ' (${c.birthYear})' : ''}'
                                    ' • ${c.relationship.displayName}'
                                    '${c.phoneNumber != null ? ' • ${c.phoneNumber}' : ''}'
                              : 'No birthday • ${c.relationship.displayName}'
                                    '${c.phoneNumber != null ? ' • ${c.phoneNumber}' : ''}',
                        ),
                        if (c.birthdayMonth == 2 && c.birthdayDay == 29) ...[
                          const SizedBox(height: 8),
                          DropdownButtonFormField<LeapDayResolution>(
                            initialValue: c.leapDayResolution,
                            isExpanded: true,
                            decoration: const InputDecoration(
                              labelText: 'In non-leap years, celebrate on',
                              isDense: true,
                            ),
                            items: [
                              for (final policy in LeapDayResolution.values)
                                DropdownMenuItem(
                                  value: policy,
                                  child: Text(policy.displayName),
                                ),
                            ],
                            onChanged: (policy) {
                              if (policy == null) return;
                              setReviewState(() {
                                parsedCandidates[i] = c.copyWith(
                                  leapDayResolution: policy,
                                  clearInvalidReason: true,
                                );
                                if (!c.isPotentialDuplicate) {
                                  selectedIndices.add(i);
                                }
                              });
                            },
                          ),
                        ],
                        if (c.invalidReason != null) ...[
                          const SizedBox(height: 4),
                          Text(
                            'Needs repair: ${c.invalidReason}',
                            style: TextStyle(
                              fontSize: 11,
                              color: Theme.of(ctx).colorScheme.error,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                        if (c.isPotentialDuplicate) ...[
                          const SizedBox(height: 4),
                          // Contrast-tested warning tone, light + dark
                          // (audit 05 P1-3).
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 6,
                              vertical: 2,
                            ),
                            decoration: BoxDecoration(
                              color: AppTone.warning.fill(ctx.colors),
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: Text(
                              c.duplicateWarning ?? 'Potential duplicate',
                              style: TextStyle(
                                fontSize: 11,
                                color: AppTone.warning.label(ctx.colors),
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ],
                      ],
                    ),
                  );
                },
              ),
            ),
            const SizedBox(height: 8),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: selectedIndices.isEmpty
                    ? null
                    : () => Navigator.of(reviewContext).pop(true),
                child: Text('Import Selected (${selectedIndices.length})'),
              ),
            ),
          ],
        ),
      ),
    );

    if (confirmed != true || !context.mounted) return;

    final importDrafts = [
      for (final index in selectedIndices)
        if (parsedCandidates[index].isImportable)
          PersonDraft.fromPerson(parsedCandidates[index].toPerson()),
    ];

    try {
      final result = await ref
          .read(personServiceProvider)
          .importValidRows(importDrafts);
      if (!context.mounted) return;
      final messenger = ScaffoldMessenger.of(context);
      final remaining =
          repairIssues.length +
          parsedCandidates.where((c) => !c.isImportable).length;
      final skipped = result.failures.length;
      final examples = [
        for (final f in result.failures.take(3))
          '${f.name.isEmpty ? 'Row ${f.index + 1}' : f.name} (${f.reasons.join(' ')})',
      ];
      final skippedDetail = skipped == 0
          ? ''
          : ' $skipped skipped: ${examples.join('; ')}'
                '${skipped > 3 ? '; and ${skipped - 3} more' : ''}.';
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            'Imported ${result.imported.length} of ${importDrafts.length}; $remaining rows remain for repair.$skippedDetail',
          ),
        ),
      );
    } catch (error, stackTrace) {
      ref
          .read(loggerProvider)
          .warning(
            'people',
            'CSV contact import failed.',
            params: {'count': importDrafts.length},
            error: error,
            stackTrace: stackTrace,
          );
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Import failed. 0 of ${importDrafts.length} contacts were saved; no partial import occurred. You can retry the exact batch.',
            ),
          ),
        );
      }
    }
  }

  Future<void> _syncDeviceContacts(
    BuildContext context,
    WidgetRef ref,
    List<Person> currentPeople,
  ) async {
    HapticFeedback.lightImpact();
    final contactsService = ref.read(deviceContactsServiceProvider);

    final hasPerm = await contactsService.hasPermission();
    if (!hasPerm) {
      if (!context.mounted) return;
      final proceed = await showDialog<bool>(
        context: context,
        builder: (dialogCtx) => AlertDialog(
          icon: const Icon(
            Icons.contact_phone_outlined,
            size: 36,
            color: AppColors.primaryTerracotta,
          ),
          title: const Text('Import Birthdays from Phone'),
          content: const Text(
            'AI-Birthday scans your device contacts only to locate names, phone numbers, and birthdays. '
            'Imported contact data stays on this device unless you explicitly use Cloud Backup.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogCtx).pop(false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(dialogCtx).pop(true),
              child: const Text('Continue'),
            ),
          ],
        ),
      );

      if (proceed != true) return;

      final granted = await contactsService.requestPermission();
      if (!granted) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text(
                'Contacts permission is required to import phone birthdays.',
              ),
            ),
          );
        }
        return;
      }
    }

    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Reading device contacts...'),
        duration: Duration(seconds: 1),
      ),
    );

    final deviceContacts = await contactsService.fetchDeviceContacts();
    if (!context.mounted) return;

    if (deviceContacts.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('No contacts found on this device.')),
      );
      return;
    }

    final candidates = deviceContacts
        .map((dc) => dc.toCandidate(existingPeople: currentPeople))
        .toList();

    await _reviewAndImportCandidates(
      context,
      ref,
      candidates,
      title: 'Import Birthdays from Phone',
    );
  }

  Future<void> _confirmAndDelete(
    BuildContext context,
    WidgetRef ref,
    Person person,
  ) async {
    final messenger = ScaffoldMessenger.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text('Remove ${person.name}?'),
        content: const Text(
          'Their birthday will be hidden. You can restore this contact from the Undo prompt immediately after removal.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(dialogContext).colorScheme.error,
              foregroundColor: Theme.of(dialogContext).colorScheme.onError,
            ),
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );

    if (confirmed != true || !context.mounted) return;

    final birthdaysRepo = ref.read(birthdaysRepositoryProvider);
    final associatedBirthday = await birthdaysRepo.getBirthdayForPerson(
      person.id,
    );

    try {
      await ref.read(personServiceProvider).remove(person.id);
    } catch (_) {
      messenger.showSnackBar(
        SnackBar(content: Text('Failed to delete ${person.name}.')),
      );
      return;
    }

    // ponytail: show from the messenger captured above, not `context` — the
    // caller passes the list item's context, which is unmounted as soon as
    // the person is deleted, so a context.mounted guard drops the Undo bar.
    messenger.showSnackBar(
      SnackBar(
        content: Text('Deleted ${person.name}.'),
        action: SnackBarAction(
          label: 'Undo',
          onPressed: () async {
            // Restore through the single tombstone owner (legacy service
            // clears `deletedAt` and bumps the version); do NOT write the row
            // through the new repository, which would resurrect the tombstone
            // and rewrite enum values through a second model stack.
            try {
              await ref
                  .read(personServiceProvider)
                  .restore(person.id, birthday: associatedBirthday);
            } catch (error, stackTrace) {
              ref
                  .read(loggerProvider)
                  .warning(
                    'people',
                    'Undo failed while restoring a deleted contact.',
                    params: {'personId': person.id},
                    error: error,
                    stackTrace: stackTrace,
                  );
              messenger.showSnackBar(
                SnackBar(content: Text('Could not restore ${person.name}.')),
              );
            }
          },
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final peopleAsync = ref.watch(peopleStreamProvider);
    final currentPeople = peopleAsync.valueOrNull ?? [];

    return Scaffold(
      appBar: AppBar(
        title: const Text('People'),
        actions: [
          const SettingsActionButton(),
          IconButton(
            icon: const Icon(Icons.person_add_outlined),
            tooltip: 'Add Person',
            onPressed: () {
              HapticFeedback.lightImpact();
              context.push('/people/add');
            },
          ),
          PopupMenuButton<String>(
            tooltip: 'More actions',
            onSelected: (val) {
              HapticFeedback.lightImpact();
              if (val == 'sync_phone') {
                _syncDeviceContacts(context, ref, currentPeople);
              } else if (val == 'import') {
                _showImportCsvSheet(context, ref, currentPeople);
              } else if (val == 'export') {
                _exportCsv(context, ref, currentPeople);
              }
            },
            itemBuilder: (ctx) => [
              const PopupMenuItem(
                value: 'sync_phone',
                child: Row(
                  children: [
                    Icon(Icons.contact_phone_outlined, size: 20),
                    SizedBox(width: 8),
                    // Flexible keeps the label from overflowing the fixed
                    // popup width at large text scales (audit 05 P0-1).
                    Flexible(
                      child: Text(
                        'Import from Phone',
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
              const PopupMenuItem(
                value: 'import',
                child: Row(
                  children: [
                    Icon(Icons.file_download_outlined, size: 20),
                    SizedBox(width: 8),
                    Flexible(
                      child: Text(
                        'Import CSV',
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
              const PopupMenuItem(
                value: 'export',
                child: Row(
                  children: [
                    Icon(Icons.file_upload_outlined, size: 20),
                    SizedBox(width: 8),
                    Flexible(
                      child: Text(
                        'Export CSV',
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
      body: AppResponsiveContainer(
        maxWidth: 720,
        child: peopleAsync.when(
          data: (people) {
            if (people.isEmpty) {
              return EditorialEmptyState(
                icon: Icons.people_outline,
                title: 'No contacts added yet',
                message:
                    'Add contacts manually or import birthdays directly from your phone.',
                action: FilledButton.icon(
                  onPressed: () {
                    HapticFeedback.lightImpact();
                    context.push('/people/add');
                  },
                  icon: const Icon(Icons.add),
                  label: const Text('Add Birthday Contact'),
                ),
                secondaryAction: OutlinedButton.icon(
                  onPressed: () =>
                      _syncDeviceContacts(context, ref, currentPeople),
                  icon: const Icon(Icons.contact_phone_outlined),
                  label: const Text('Import from Phone'),
                ),
              );
            }

            final trimmed = _query.trim();
            // ⚡ Bolt: Pre-compile case-insensitive RegExp to avoid O(N) .toLowerCase() string allocations
            final searchRegex = trimmed.isEmpty
                ? null
                : RegExp(RegExp.escape(trimmed), caseSensitive: false);
            final filtered = people.where((person) {
              final matchesQuery =
                  trimmed.isEmpty ||
                  searchRegex!.hasMatch(person.name) ||
                  searchRegex.hasMatch(person.relationship.displayName);
              final matchesRelationship =
                  _relationshipFilter == null ||
                  person.relationship == _relationshipFilter;
              final matchesMonth =
                  _birthdayMonthFilter == null ||
                  person.birthdayMonth == _birthdayMonthFilter;
              return matchesQuery && matchesRelationship && matchesMonth;
            }).toList();

            return Column(
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
                  child: TextField(
                    controller: _searchController,
                    onChanged: (value) => setState(() => _query = value),
                    decoration: InputDecoration(
                      hintText: 'Search contacts',
                      prefixIcon: const Icon(Icons.search),
                      suffixIcon: _query.isEmpty
                          ? null
                          : IconButton(
                              icon: const Icon(Icons.close),
                              tooltip: 'Clear search',
                              onPressed: () {
                                HapticFeedback.lightImpact();
                                _searchController.clear();
                                setState(() => _query = '');
                              },
                            ),
                      isDense: true,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
                  child: LayoutBuilder(
                    builder: (context, constraints) {
                      final relationshipFilter =
                          DropdownButtonFormField<RelationshipCategory?>(
                            key: const ValueKey('people-relationship-filter'),
                            initialValue: _relationshipFilter,
                            isExpanded: true,
                            decoration: const InputDecoration(
                              labelText: 'Relationship',
                              isDense: true,
                            ),
                            items: [
                              const DropdownMenuItem<RelationshipCategory?>(
                                value: null,
                                child: Text('All relationships'),
                              ),
                              for (final relationship
                                  in RelationshipCategory.values)
                                DropdownMenuItem(
                                  value: relationship,
                                  child: Text(relationship.displayName),
                                ),
                            ],
                            onChanged: (value) =>
                                setState(() => _relationshipFilter = value),
                          );
                      final monthFilter = DropdownButtonFormField<int>(
                        key: const ValueKey('people-month-filter'),
                        initialValue: _birthdayMonthFilter ?? 0,
                        isExpanded: true,
                        decoration: const InputDecoration(
                          labelText: 'Birthday month',
                          isDense: true,
                        ),
                        items: [
                          const DropdownMenuItem(
                            value: 0,
                            child: Text('Any month'),
                          ),
                          for (var month = 1; month <= 12; month++)
                            DropdownMenuItem(
                              value: month,
                              child: Text(
                                DateFormat.MMMM().format(DateTime(2000, month)),
                              ),
                            ),
                        ],
                        onChanged: (value) => setState(
                          () =>
                              _birthdayMonthFilter = value == 0 ? null : value,
                        ),
                      );
                      if (constraints.maxWidth < 440) {
                        return Column(
                          children: [
                            relationshipFilter,
                            const SizedBox(height: 8),
                            monthFilter,
                          ],
                        );
                      }
                      return Row(
                        children: [
                          Expanded(child: relationshipFilter),
                          const SizedBox(width: 12),
                          Expanded(child: monthFilter),
                        ],
                      );
                    },
                  ),
                ),
                Expanded(
                  child: filtered.isEmpty
                      ? _noSearchResults(trimmed)
                      : ListView.separated(
                          padding: const EdgeInsets.fromLTRB(
                            16,
                            8,
                            16,
                            AppSpacing.bottomClearance,
                          ),
                          itemCount: filtered.length,
                          separatorBuilder: (_, _) => const SizedBox(height: 8),
                          itemBuilder: (context, index) {
                            final person = filtered[index];
                            final next = _next(person);
                            final dateStr = !person.hasBirthday
                                ? 'No birthday set'
                                : !person.hasResolvableBirthday
                                ? 'Feb 29 • choose celebration date'
                                : DateFormat.MMMMd().format(next!.nextDate);
                            final ageTurn =
                                (person.hasBirthday &&
                                    person.birthYear != null &&
                                    next != null)
                                ? ' • turns ${next.year - person.birthYear!}'
                                : '';

                            return Card(
                              child: ListTile(
                                contentPadding: const EdgeInsets.symmetric(
                                  horizontal: 16,
                                  vertical: 8,
                                ),
                                leading: CircleAvatar(
                                  backgroundColor: (next?.isToday ?? false)
                                      ? AppColors.primaryTerracottaContainer
                                      : null,
                                  foregroundColor: (next?.isToday ?? false)
                                      ? AppColors.primaryTerracotta
                                      : null,
                                  child: Text(
                                    person.name.isNotEmpty
                                        ? person.name[0].toUpperCase()
                                        : '?',
                                    style: const TextStyle(
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ),
                                title: Text(
                                  person.name,
                                  style: const TextStyle(
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                                subtitle: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    const SizedBox(height: 4),
                                    Text(
                                      '$dateStr$ageTurn • ${relationshipCardLabel(person.relationship, person.relationshipCloseness)}',
                                    ),
                                    if (next != null) ...[
                                      const SizedBox(height: 4),
                                      Align(
                                        alignment: Alignment.centerLeft,
                                        child: CountdownChip(
                                          daysUntil: next.daysUntil,
                                          isToday: next.isToday,
                                          customLabel: _countdownLabel(next),
                                        ),
                                      ),
                                    ],
                                    if (person.importantFacts.isNotEmpty) ...[
                                      const SizedBox(height: 4),
                                      Text(
                                        'Facts: ${person.importantFacts.join(', ')}',
                                        style: TextStyle(
                                          fontSize: 12,
                                          color: context.colors.textSecondary,
                                        ),
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ],
                                  ],
                                ),
                                trailing: PopupMenuButton<String>(
                                  tooltip: 'Person actions',
                                  onSelected: (action) async {
                                    HapticFeedback.lightImpact();
                                    if (action == 'message') {
                                      // Person-scoped Studio route: no birthday lookup, so
                                      // the action can never silently no-op on a missing row
                                      // (same route notifications/calendar use).
                                      context.push(
                                        '/message-studio/person/${person.id}',
                                      );
                                    } else if (action == 'edit') {
                                      context.push('/people/edit/${person.id}');
                                    } else if (action == 'delete') {
                                      _confirmAndDelete(context, ref, person);
                                    }
                                  },
                                  itemBuilder: (context) => [
                                    if (person.hasResolvableBirthday)
                                      const PopupMenuItem(
                                        value: 'message',
                                        child: Row(
                                          children: [
                                            Icon(Icons.auto_awesome, size: 18),
                                            SizedBox(width: 8),
                                            Text('Message Studio'),
                                          ],
                                        ),
                                      ),
                                    const PopupMenuItem(
                                      value: 'edit',
                                      child: Text('Edit'),
                                    ),
                                    const PopupMenuItem(
                                      value: 'delete',
                                      child: Text('Delete'),
                                    ),
                                  ],
                                ),
                                onTap: () {
                                  HapticFeedback.lightImpact();
                                  _showPersonDetailsModal(context, ref, person);
                                },
                              ),
                            );
                          },
                        ),
                ),
              ],
            );
          },
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (_, _) => EmptyState(
            icon: Icons.cloud_off_outlined,
            title: 'Could not load contacts',
            message: 'Your saved birthdays have not been deleted.',
            action: TextButton.icon(
              onPressed: () => ref.invalidate(peopleStreamProvider),
              icon: const Icon(Icons.refresh),
              label: const Text('Try again'),
            ),
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          HapticFeedback.lightImpact();
          context.push('/people/add');
        },
        tooltip: 'Add Person',
        child: const Icon(Icons.add),
      ),
    );
  }

  Widget _noSearchResults(String query) {
    final hasFilters =
        _relationshipFilter != null || _birthdayMonthFilter != null;
    return EditorialEmptyState(
      icon: Icons.search_off,
      title: 'No contacts found',
      message: query.isNotEmpty
          ? 'Nothing matches "$query". Try another name or clear the filters.'
          : hasFilters
          ? 'No people match the selected relationship and birthday month.'
          : 'Add a contact or import birthdays from your phone or a CSV file.',
      action: query.isNotEmpty || hasFilters
          ? TextButton(
              onPressed: () => setState(() {
                _searchController.clear();
                _query = '';
                _relationshipFilter = null;
                _birthdayMonthFilter = null;
              }),
              child: const Text('Clear search and filters'),
            )
          : null,
    );
  }

  void _showPersonDetailsModal(
    BuildContext context,
    WidgetRef ref,
    Person person,
  ) {
    AppBottomSheet.show<void>(
      context: context,
      children: [
        Row(
          children: [
            CircleAvatar(
              radius: 24,
              backgroundColor: AppColors.primaryTerracottaContainer,
              foregroundColor: AppColors.primaryTerracotta,
              child: Text(
                person.name.isNotEmpty ? person.name[0].toUpperCase() : '?',
                style: const TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    person.name,
                    style: const TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Text(
                    '${person.relationship.displayName} (${person.relationshipCloseness.displayName})',
                    style: TextStyle(color: context.colors.textSecondary),
                  ),
                ],
              ),
            ),
          ],
        ),
        const Divider(height: 32),
        _buildDetailRow(
          context,
          'Birthday',
          !person.hasBirthday
              ? 'Not set'
              : !person.hasResolvableBirthday
              ? 'February 29 • celebration date needs repair'
              : DateFormat.MMMMd().format(_next(person)!.nextDate),
        ),
        if (person.phoneNumber != null)
          _buildDetailRow(context, 'Phone', person.phoneNumber!),
        _buildDetailRow(
          context,
          'Preferred Tone',
          person.preferredTone.displayName,
        ),
        _buildDetailRow(
          context,
          'Delivery Channel',
          person.preferredDeliveryChannel.displayName,
        ),
        const SizedBox(height: AppSpacing.sm),
        const Text(
          'Known Facts for AI (User-provided only):',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 6),
        if (person.importantFacts.isEmpty)
          const Text(
            'No specific facts added yet.',
            style: TextStyle(fontStyle: FontStyle.italic),
          )
        else
          ...person.importantFacts.map(
            (f) => Padding(
              padding: const EdgeInsets.symmetric(vertical: 2),
              child: Row(
                children: [
                  const Icon(
                    Icons.check_circle_outline,
                    size: 16,
                    color: AppColors.accentForest,
                  ),
                  const SizedBox(width: 8),
                  Expanded(child: Text(f)),
                ],
              ),
            ),
          ),
        const SizedBox(height: AppSpacing.lg),
        if (person.hasResolvableBirthday) ...[
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: () async {
                HapticFeedback.lightImpact();
                Navigator.of(context).pop();
                context.push('/message-studio/person/${person.id}');
              },
              icon: const Icon(Icons.auto_awesome),
              label: const Text('Draft Message with AI'),
            ),
          ),
          const SizedBox(height: AppSpacing.xs),
        ],
        SizedBox(
          width: double.infinity,
          child: OutlinedButton.icon(
            onPressed: () {
              HapticFeedback.lightImpact();
              Navigator.of(context).pop();
              context.push('/people/edit/${person.id}');
            },
            icon: const Icon(Icons.edit_outlined),
            label: const Text('Edit Contact Details'),
          ),
        ),
        const SizedBox(height: AppSpacing.xs),
        SizedBox(
          width: double.infinity,
          child: OutlinedButton.icon(
            style: OutlinedButton.styleFrom(
              foregroundColor: Theme.of(context).colorScheme.error,
            ),
            onPressed: () {
              HapticFeedback.lightImpact();
              Navigator.of(context).pop();
              _confirmAndDelete(context, ref, person);
            },
            icon: const Icon(Icons.delete_outline),
            label: const Text('Delete Contact'),
          ),
        ),
        const SizedBox(height: AppSpacing.xs),
      ],
    );
  }

  Widget _buildDetailRow(BuildContext context, String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: TextStyle(color: context.colors.textSecondary)),
          Text(value, style: const TextStyle(fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}
