import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/features/birthdays/domain/birthday_engine.dart';
import 'package:ai_birthday/features/people/data/person_providers.dart'
    show personServiceProvider;
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/shared/design_system/empty_state.dart';

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
        ],
      ),
      body: peopleAsync.when(
        data: (people) {
          if (people.isEmpty) {
            return EmptyState(
              icon: Icons.people_outline,
              title: 'No contacts added yet',
              message: 'Add a birthday contact to start tracking.',
              action: FilledButton.icon(
                onPressed: () {
                  HapticFeedback.lightImpact();
                  context.push('/people/add');
                },
                icon: const Icon(Icons.add),
                label: const Text('Add Birthday Contact'),
              ),
            );
          }

          return ListView.separated(
            padding: const EdgeInsets.all(16),
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
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: next.isToday
                              ? Theme.of(context).colorScheme.primaryContainer
                              : Theme.of(
                                  context,
                                ).colorScheme.surfaceContainerHighest,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          _countdownLabel(next),
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: next.isToday
                                ? Theme.of(
                                    context,
                                  ).colorScheme.onPrimaryContainer
                                : Theme.of(
                                    context,
                                  ).colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ),
                      PopupMenuButton<String>(
                        tooltip: 'Person actions',
                        onSelected: (action) {
                          if (action == 'edit') {
                            context.push('/people/edit/${person.id}');
                          } else if (action == 'delete') {
                            _confirmAndDelete(context, ref, person);
                          }
                        },
                        itemBuilder: (context) => const [
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
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (sheetContext) {
        return Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  CircleAvatar(
                    radius: 24,
                    child: Text(
                      person.name[0],
                      style: const TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  const SizedBox(width: 16),
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
              _buildDetailRow(
                'Preferred Tone',
                person.preferredTone.displayName,
              ),
              _buildDetailRow(
                'Delivery Channel',
                person.preferredDeliveryChannel.displayName,
              ),
              const SizedBox(height: 12),
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
                          color: Colors.green,
                        ),
                        const SizedBox(width: 8),
                        Expanded(child: Text(f)),
                      ],
                    ),
                  ),
                ),
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: () {
                    Navigator.of(sheetContext).pop();
                    context.push('/people/edit/${person.id}');
                  },
                  icon: const Icon(Icons.edit_outlined),
                  label: const Text('Edit Contact Details'),
                ),
              ),
              const SizedBox(height: 8),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  style: OutlinedButton.styleFrom(
                    foregroundColor: Theme.of(context).colorScheme.error,
                  ),
                  onPressed: () {
                    Navigator.of(sheetContext).pop();
                    _confirmAndDelete(context, ref, person);
                  },
                  icon: const Icon(Icons.delete_outline),
                  label: const Text('Delete Contact'),
                ),
              ),
              const SizedBox(height: 12),
            ],
          ),
        );
      },
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
