import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/features/birthdays/domain/birthday_engine.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart'
    as b_models;
import 'package:ai_birthday/features/people/data/contact_csv_service.dart';
import 'package:ai_birthday/features/people/data/person_providers.dart'
    show personServiceProvider, resyncReminderSchedule;
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/shared/design_system/design_system.dart';
import 'package:ai_birthday/ui/design_system/app_tokens.dart';

class PeopleScreen extends ConsumerStatefulWidget {
  const PeopleScreen({super.key});

  @override
  ConsumerState<PeopleScreen> createState() => _PeopleScreenState();
}

class _PeopleScreenState extends ConsumerState<PeopleScreen> {
  static const BirthdayEngine _engine = BirthdayEngine();

  final TextEditingController _searchController = TextEditingController();
  String _query = '';

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  NextBirthday? _next(Person person) => person.hasBirthday
      ? _engine.computeNext(
          month: person.birthdayMonth!,
          day: person.birthdayDay!,
          birthYear: person.birthYear,
          timezoneName: person.timezone,
        )
      : null;

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
    final textController = TextEditingController();
    final parsedCandidates = await showModalBottomSheet<CsvParseResult>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => AppBottomSheet(
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
                  Navigator.of(sheetContext).pop();
                },
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            'Paste CSV with columns: Name, Month, Day, Year, Phone, Relationship.',
            style: TextStyle(fontSize: 12, color: context.colors.textSecondary),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: textController,
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
              onPressed: () {
                final csvService = ref.read(contactCsvServiceProvider);
                final parseResult = csvService.parseCsvWithResult(
                  textController.text,
                  existingPeople: currentPeople,
                );
                Navigator.of(sheetContext).pop(parseResult);
              },
              child: const Text('Parse & Review Candidates'),
            ),
          ),
        ],
      ),
    );

    if (parsedCandidates == null || !context.mounted) return;

    if (parsedCandidates.invalidRows.isNotEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Skipped ${parsedCandidates.invalidRows.length} invalid rows (e.g. ${parsedCandidates.invalidRows.first})',
          ),
          duration: const Duration(seconds: 4),
        ),
      );
    }

    if (parsedCandidates.candidates.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('No valid contact entries found in CSV.')),
      );
      return;
    }

    await _reviewAndImportCandidates(
      context,
      ref,
      parsedCandidates.candidates,
      title: 'Review CSV Contacts',
    );
  }

  Future<void> _reviewAndImportCandidates(
    BuildContext context,
    WidgetRef ref,
    List<ParsedContactCandidate> parsedCandidates, {
    required String title,
  }) async {
    if (parsedCandidates.isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('No contacts to review.')));
      return;
    }

    final selectedIndices = <int>{
      for (int i = 0; i < parsedCandidates.length; i++)
        if (!parsedCandidates[i].isPotentialDuplicate) i,
    };

    // Calculate once to avoid multiple O(N) evaluations in the builder.
    final int duplicateCount = parsedCandidates.length - selectedIndices.length;
    final int newCount = selectedIndices.length;

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
                      if (selectedIndices.length == parsedCandidates.length) {
                        selectedIndices.clear();
                      } else {
                        selectedIndices.addAll(
                          List.generate(parsedCandidates.length, (i) => i),
                        );
                      }
                    });
                  },
                  child: Text(
                    selectedIndices.length == parsedCandidates.length
                        ? 'Deselect All'
                        : 'Select All',
                  ),
                ),
              ],
            ),
            Text(
              '${parsedCandidates.length} birthdays found: $newCount new, $duplicateCount already added.',
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
                    onChanged: (val) {
                      setReviewState(() {
                        if (val == true) {
                          selectedIndices.add(i);
                        } else {
                          selectedIndices.remove(i);
                        }
                      });
                    },
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

    final peopleRepo = ref.read(peopleRepositoryProvider);
    final birthdaysRepo = ref.read(birthdaysRepositoryProvider);
    final now = DateTime.now();
    int importedCount = 0;

    for (final index in selectedIndices) {
      final candidate = parsedCandidates[index];
      final person = candidate.toPerson();
      await peopleRepo.savePerson(person);

      if (person.hasBirthday) {
        final nextDate = b_models.Birthday.nextBirthdayDate(
          month: person.birthdayMonth!,
          day: person.birthdayDay!,
          from: now,
        );
        final isToday =
            nextDate.year == now.year &&
            nextDate.month == now.month &&
            nextDate.day == now.day;

        final birthday = b_models.Birthday(
          id: 'birthday-${person.id}',
          personId: person.id,
          cycleYear: nextDate.year,
          date: nextDate,
          status: isToday
              ? b_models.BirthdayStatus.reminderDue
              : b_models.BirthdayStatus.upcoming,
          createdAt: now,
          updatedAt: now,
        );
        await birthdaysRepo.saveBirthday(birthday);
      }
      importedCount++;
    }

    if (!context.mounted) return;

    final messenger = ScaffoldMessenger.of(context);
    // Batch imports write through the repositories (bypassing PersonService's
    // onChanged hook), so refresh the reminder plan explicitly.
    await resyncReminderSchedule(ProviderScope.containerOf(context));
    messenger.showSnackBar(
      SnackBar(
        content: Text('Successfully imported $importedCount contacts.'),
      ),
    );
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
          'Their birthday will be hidden. You can undo this at any time.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );

    if (confirmed != true || !context.mounted) return;

    final peopleRepo = ref.read(peopleRepositoryProvider);
    final birthdaysRepo = ref.read(birthdaysRepositoryProvider);
    final associatedBirthday = await birthdaysRepo.getBirthdayForPerson(
      person.id,
    );

    await peopleRepo.deletePerson(person.id);
    if (associatedBirthday != null) {
      await birthdaysRepo.deleteBirthday(associatedBirthday.id);
    }

    try {
      await ref.read(personServiceProvider).remove(person.id);
    } catch (_) {}

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
              await ref.read(personServiceProvider).restore(person.id);
            } catch (_) {}
            if (associatedBirthday != null) {
              await birthdaysRepo.saveBirthday(associatedBirthday);
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
        title: const Text('People & Contacts'),
        actions: [
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
      body: peopleAsync.when(
        data: (people) {
          if (people.isEmpty) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(
                    Icons.people_outline,
                    size: 64,
                    color: Colors.grey,
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'No contacts added yet',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Add contacts manually or import birthdays directly from your phone.',
                    style: TextStyle(
                      fontSize: 12,
                      color: context.colors.textSecondary,
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 16),
                  FilledButton.icon(
                    onPressed: () {
                      HapticFeedback.lightImpact();
                      context.push('/people/add');
                    },
                    icon: const Icon(Icons.add),
                    label: const Text('Add Birthday Contact'),
                  ),
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
                    onPressed: () =>
                        _syncDeviceContacts(context, ref, currentPeople),
                    icon: const Icon(Icons.contact_phone_outlined),
                    label: const Text('Import from Phone'),
                  ),
                ],
              ),
            );
          }

          final trimmed = _query.trim();
            final filtered = trimmed.isEmpty
                ? people
                : people
                      .where(
                        (p) => p.name.toLowerCase().contains(
                          trimmed.toLowerCase(),
                        ),
                      )
                      .toList();

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
              final dateStr = person.hasBirthday
                  ? DateFormat.MMMMd().format(
                      next?.nextDate ??
                          DateTime(
                            DateTime.now().year,
                            person.birthdayMonth!,
                            person.birthdayDay!,
                          ),
                    )
                  : 'No birthday set';
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
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                  ),
                  title: Text(
                    person.name,
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                  subtitle: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SizedBox(height: 4),
                      Text(
                        '$dateStr$ageTurn • ${person.relationship.displayName}',
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
                            color: Colors.grey[600],
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
                      if (action == 'message') {
                        // Person-scoped Studio route: no birthday lookup, so
                        // the action can never silently no-op on a missing row
                        // (same route notifications/calendar use).
                        context.push('/message-studio/person/${person.id}');
                      } else if (action == 'edit') {
                        context.push('/people/edit/${person.id}');
                      } else if (action == 'delete') {
                        _confirmAndDelete(context, ref, person);
                      }
                    },
                    itemBuilder: (context) => [
                      if (person.hasBirthday)
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
                      const PopupMenuItem(value: 'edit', child: Text('Edit')),
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
        error: (_, _) => const Center(
          child: Padding(
            padding: EdgeInsets.all(24),
            child: Text(
              'We could not load your contacts right now. Your saved birthdays have not been deleted. Try again later.',
              textAlign: TextAlign.center,
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
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.search_off, size: 48, color: Colors.grey),
            const SizedBox(height: 12),
            const Text(
              'No contacts found',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
            ),
            const SizedBox(height: 4),
            Text(
              'Nothing matches "$query". Try a different name.',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 12,
                color: context.colors.textSecondary,
              ),
            ),
          ],
        ),
      ),
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
          person.hasBirthday
              ? DateFormat.MMMMd().format(
                  _next(person)?.nextDate ??
                      DateTime(
                        DateTime.now().year,
                        person.birthdayMonth!,
                        person.birthdayDay!,
                      ),
                )
              : 'Not set',
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
        if (person.hasBirthday) ...[
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: () async {
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
