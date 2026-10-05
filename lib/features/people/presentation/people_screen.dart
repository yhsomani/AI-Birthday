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
    show personServiceProvider;
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/shared/design_system/design_system.dart';

class PeopleScreen extends ConsumerWidget {
  const PeopleScreen({super.key});

  static const BirthdayEngine _engine = BirthdayEngine();

  NextBirthday _next(Person person) => _engine.computeNext(
    month: person.birthdayMonth,
    day: person.birthdayDay,
    birthYear: person.birthYear,
    timezoneName: person.timezone,
  );

  String _countdownLabel(NextBirthday next) {
    if (next.isToday) return 'Today';
    if (next.daysUntil == 1) return 'Tomorrow';
    if (next.daysUntil <= 90) return 'In ${next.daysUntil} days';
    return 'In ${(next.daysUntil / 30).ceil()} months';
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

    await Clipboard.setData(ClipboardData(text: csvText));

    final shareService = ref.read(nativeShareServiceProvider);
    await shareService.shareText(
      text: csvText,
      title: 'AI-Birthday Contacts Export (${people.length})',
    );

    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Exported ${people.length} contacts to CSV (copied to clipboard)! 📋',
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
    final parsedCandidates = await showModalBottomSheet<List<ParsedContactCandidate>>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => StatefulBuilder(
        builder: (ctx, setModalState) => SafeArea(
          child: Padding(
            padding: EdgeInsets.only(
              left: 16,
              right: 16,
              top: 16,
              bottom: MediaQuery.of(ctx).viewInsets.bottom + 16,
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text(
                      'Import Contacts (CSV)',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 18,
                      ),
                    ),
                    TextButton(
                      onPressed: () {
                        textController.text =
                            'Name,Birthday Month,Birthday Day,Birth Year,Phone Number,Relationship\n'
                            'Alex Rivera,6,15,1990,+15552345678,Friend\n'
                            'Taylor Brooks,11,28,1988,+15558765432,Colleague';
                        setModalState(() {});
                      },
                      child: const Text('Load Sample'),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                const Text(
                  'Paste CSV with columns: Name, Month, Day, Year, Phone, Relationship.',
                  style: TextStyle(fontSize: 12, color: Colors.grey),
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
                      final candidates = csvService.parseAndNormalizeCsv(
                        textController.text,
                        existingPeople: currentPeople,
                      );
                      Navigator.of(sheetContext).pop(candidates);
                    },
                    child: const Text('Parse & Review Candidates'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );

    if (parsedCandidates == null || !context.mounted) return;

    if (parsedCandidates.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('No valid contact entries found in CSV.')),
      );
      return;
    }

    await _reviewAndImportCandidates(
      context,
      ref,
      parsedCandidates,
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

    final confirmed = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (reviewContext) => StatefulBuilder(
        builder: (ctx, setReviewState) => SafeArea(
          child: Container(
            height: MediaQuery.of(ctx).size.height * 0.7,
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
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
                          if (selectedIndices.length ==
                              parsedCandidates.length) {
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
                const Text(
                  'Duplicate candidates are unselected by default (SSOT §18: Never auto-merge).',
                  style: TextStyle(fontSize: 12, color: Colors.grey),
                ),
                const Divider(),
                Expanded(
                  child: ListView.builder(
                    itemCount: parsedCandidates.length,
                    itemBuilder: (ctx, i) {
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
                              'Birthday: ${c.birthdayMonth}/${c.birthdayDay}'
                              '${c.birthYear != null ? ' (${c.birthYear})' : ''}'
                              ' • ${c.relationship.displayName}'
                              '${c.phoneNumber != null ? ' • ${c.phoneNumber}' : ''}',
                            ),
                            if (c.isPotentialDuplicate) ...[
                              const SizedBox(height: 4),
                              Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 6,
                                  vertical: 2,
                                ),
                                decoration: BoxDecoration(
                                  color: Colors.amber.withValues(alpha: 0.2),
                                  borderRadius: BorderRadius.circular(4),
                                  border: Border.all(color: Colors.amber),
                                ),
                                child: Text(
                                  '⚠️ ${c.duplicateWarning}',
                                  style: const TextStyle(
                                    fontSize: 11,
                                    color: Colors.brown,
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

      final nextDate = b_models.Birthday.nextBirthdayDate(
        month: person.birthdayMonth,
        day: person.birthdayDay,
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
      importedCount++;
    }

    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Successfully imported $importedCount contacts! 🎉'),
        ),
      );
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
      final granted = await contactsService.requestPermission();
      if (!granted) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text(
                'Contacts permission is required to sync phone contacts.',
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
      title: 'Sync Device Contacts',
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

    if (!context.mounted) return;

    messenger.showSnackBar(
      SnackBar(
        content: Text('Deleted ${person.name}.'),
        action: SnackBarAction(
          label: 'Undo',
          onPressed: () async {
            await peopleRepo.savePerson(person);
            if (associatedBirthday != null) {
              await birthdaysRepo.saveBirthday(associatedBirthday);
            }
            try {
              await ref.read(personServiceProvider).restore(person.id);
            } catch (_) {}
          },
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
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
                    Text('Sync Phone Contacts'),
                  ],
                ),
              ),
              const PopupMenuItem(
                value: 'import',
                child: Row(
                  children: [
                    Icon(Icons.file_download_outlined, size: 20),
                    SizedBox(width: 8),
                    Text('Import CSV'),
                  ],
                ),
              ),
              const PopupMenuItem(
                value: 'export',
                child: Row(
                  children: [
                    Icon(Icons.file_upload_outlined, size: 20),
                    SizedBox(width: 8),
                    Text('Export CSV'),
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
                  const Text(
                    'Add contacts manually or sync birthdays directly from your phone.',
                    style: TextStyle(fontSize: 12, color: Colors.grey),
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
                    label: const Text('Sync Phone Contacts'),
                  ),
                ],
              ),
            );
          }

          return ListView.separated(
            padding: const EdgeInsets.fromLTRB(
              16,
              16,
              16,
              AppSpacing.bottomClearance,
            ),
            itemCount: people.length,
            separatorBuilder: (_, _) => const SizedBox(height: 8),
            itemBuilder: (context, index) {
              final person = people[index];
              final next = _next(person);
              final dateStr = DateFormat.MMMMd().format(
                DateTime(2026, person.birthdayMonth, person.birthdayDay),
              );
              final ageTurn = person.birthYear != null
                  ? ' • turns ${next.year - person.birthYear!}'
                  : '';

              return Card(
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 8,
                  ),
                  leading: CircleAvatar(
                    backgroundColor: next.isToday
                        ? AppColors.primaryTerracottaContainer
                        : null,
                    foregroundColor: next.isToday
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
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      CountdownChip(
                        daysUntil: next.daysUntil,
                        isToday: next.isToday,
                        customLabel: _countdownLabel(next),
                      ),
                      PopupMenuButton<String>(
                        tooltip: 'Person actions',
                        onSelected: (action) async {
                          if (action == 'message') {
                            final bRepo = ref.read(birthdaysRepositoryProvider);
                            final b = await bRepo.getBirthdayForPerson(
                              person.id,
                            );
                            if (b != null && context.mounted) {
                              context.push('/message-studio/${b.id}');
                            }
                          } else if (action == 'edit') {
                            context.push('/people/edit/${person.id}');
                          } else if (action == 'delete') {
                            _confirmAndDelete(context, ref, person);
                          }
                        },
                        itemBuilder: (context) => const [
                          PopupMenuItem(
                            value: 'message',
                            child: Row(
                              children: [
                                Icon(Icons.auto_awesome, size: 18),
                                SizedBox(width: 8),
                                Text('Message Studio'),
                              ],
                            ),
                          ),
                          PopupMenuItem(value: 'edit', child: Text('Edit')),
                          PopupMenuItem(value: 'delete', child: Text('Delete')),
                        ],
                      ),
                    ],
                  ),
                  onTap: () => _showPersonDetailsModal(context, ref, person),
                ),
              );
            },
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, _) => Center(child: Text('Error loading contacts: $err')),
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
                    style: TextStyle(color: Colors.grey[600]),
                  ),
                ],
              ),
            ),
          ],
        ),
        const Divider(height: 32),
        _buildDetailRow(
          'Birthday',
          DateFormat.MMMMd().format(
            DateTime(2026, person.birthdayMonth, person.birthdayDay),
          ),
        ),
        if (person.phoneNumber != null)
          _buildDetailRow('Phone', person.phoneNumber!),
        _buildDetailRow('Preferred Tone', person.preferredTone.displayName),
        _buildDetailRow(
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
        SizedBox(
          width: double.infinity,
          child: FilledButton.icon(
            onPressed: () async {
              Navigator.of(context).pop();
              final bRepo = ref.read(birthdaysRepositoryProvider);
              final b = await bRepo.getBirthdayForPerson(person.id);
              if (b != null && context.mounted) {
                context.push('/message-studio/${b.id}');
              }
            },
            icon: const Icon(Icons.auto_awesome),
            label: const Text('Draft Message with AI'),
          ),
        ),
        const SizedBox(height: AppSpacing.xs),
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

  Widget _buildDetailRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: TextStyle(color: Colors.grey[600])),
          Text(value, style: const TextStyle(fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}
