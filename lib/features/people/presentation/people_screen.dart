/// Directory of contacts and recipient preferences (SSOT §7, §18).
library;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';

class PeopleScreen extends ConsumerWidget {
  const PeopleScreen({super.key});

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
              _showAddPersonDialog(context, ref);
            },
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
                  const Text('No contacts added yet'),
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    onPressed: () {
                      HapticFeedback.lightImpact();
                      _showAddPersonDialog(context, ref);
                    },
                    icon: const Icon(Icons.add),
                    label: const Text('Add Birthday Contact'),
                  ),
                ],
              ),
            );
          }

          return ListView.separated(
            padding: EdgeInsets.symmetric(
              horizontal: MediaQuery.of(context).size.width > 600
                  ? (MediaQuery.of(context).size.width - 600) / 2
                  : 16,
              vertical: 16,
            ),
            itemCount: people.length,
            separatorBuilder: (context, index) => const SizedBox(height: 8),
            itemBuilder: (context, index) {
              final person = people[index];
              final dateStr = DateFormat.MMMMd().format(
                DateTime(2026, person.birthdayMonth, person.birthdayDay),
              );

              return Card(
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 12,
                  ),
                  leading: CircleAvatar(
                    backgroundColor: Theme.of(
                      context,
                    ).colorScheme.primaryContainer,
                    foregroundColor: Theme.of(
                      context,
                    ).colorScheme.onPrimaryContainer,
                    child: Text(
                      person.name.isNotEmpty
                          ? person.name[0].toUpperCase()
                          : '?',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                  ),
                  title: Text(
                    person.name,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  subtitle: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SizedBox(height: 4),
                      Text(
                        '$dateStr • ${person.relationship.displayName}',
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                      if (person.importantFacts.isNotEmpty) ...[
                        const SizedBox(height: 4),
                        Text(
                          'Facts: ${person.importantFacts.join(', ')}',
                          style: Theme.of(context).textTheme.bodySmall
                              ?.copyWith(
                                color: Theme.of(
                                  context,
                                ).textTheme.bodySmall?.color,
                              ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ],
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => _showPersonDetailsModal(context, person),
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
          _showAddPersonDialog(context, ref);
        },
        tooltip: 'Add Person',
        child: const Icon(Icons.add),
      ),
    );
  }

  void _showPersonDetailsModal(BuildContext context, Person person) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) {
        return Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  CircleAvatar(
                    radius: 28,
                    backgroundColor: Theme.of(
                      context,
                    ).colorScheme.primaryContainer,
                    foregroundColor: Theme.of(
                      context,
                    ).colorScheme.onPrimaryContainer,
                    child: Text(
                      person.name[0],
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          person.name,
                          style: Theme.of(context).textTheme.headlineSmall,
                        ),
                        Text(
                          '${person.relationship.displayName} (${person.relationshipCloseness.displayName})',
                          style: Theme.of(context).textTheme.bodyMedium
                              ?.copyWith(
                                color: Theme.of(
                                  context,
                                ).textTheme.bodySmall?.color,
                              ),
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
                DateFormat.MMMMd().format(
                  DateTime(2026, person.birthdayMonth, person.birthdayDay),
                ),
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
              const SizedBox(height: 24),
              Text(
                'Known Facts for AI',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 8),
              if (person.importantFacts.isEmpty)
                Text(
                  'No specific facts added yet.',
                  style: Theme.of(
                    context,
                  ).textTheme.bodyMedium?.copyWith(fontStyle: FontStyle.italic),
                )
              else
                ...person.importantFacts.map(
                  (f) => Padding(
                    padding: const EdgeInsets.symmetric(vertical: 2),
                    child: Row(
                      children: [
                        Icon(
                          Icons.check_circle_outline,
                          size: 16,
                          color: Theme.of(context).colorScheme.primary,
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            f,
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              const SizedBox(height: 24),
            ],
          ),
        );
      },
    );
  }

  Widget _buildDetailRow(BuildContext context, String label, String value) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.textTheme.bodySmall?.color,
            ),
          ),
          Text(value, style: theme.textTheme.titleSmall),
        ],
      ),
    );
  }

  void _showAddPersonDialog(BuildContext context, WidgetRef ref) {
    final nameCtrl = TextEditingController();
    final phoneCtrl = TextEditingController();
    final factsCtrl = TextEditingController();
    var selectedMonth = DateTime.now().month;
    var selectedDay = DateTime.now().day;
    var selectedCategory = RelationshipCategory.friend;
    var selectedTone = MessageTone.warm;

    showDialog(
      context: context,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setState) {
            return AlertDialog(
              title: const Text('Add Birthday Contact'),
              content: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextField(
                      controller: nameCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Full Name *',
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: phoneCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Phone (with country code, e.g. +1415...)',
                      ),
                      keyboardType: TextInputType.phone,
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: DropdownButtonFormField<int>(
                            initialValue: selectedMonth,
                            decoration: const InputDecoration(
                              labelText: 'Month',
                            ),
                            items: List.generate(12, (i) => i + 1).map((m) {
                              return DropdownMenuItem(
                                value: m,
                                child: Text(
                                  DateFormat.MMM().format(DateTime(2026, m)),
                                ),
                              );
                            }).toList(),
                            onChanged: (val) =>
                                setState(() => selectedMonth = val ?? 1),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: DropdownButtonFormField<int>(
                            initialValue: selectedDay,
                            decoration: const InputDecoration(labelText: 'Day'),
                            items: List.generate(31, (i) => i + 1).map((d) {
                              return DropdownMenuItem(
                                value: d,
                                child: Text('$d'),
                              );
                            }).toList(),
                            onChanged: (val) =>
                                setState(() => selectedDay = val ?? 1),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    DropdownButtonFormField<RelationshipCategory>(
                      initialValue: selectedCategory,
                      decoration: const InputDecoration(
                        labelText: 'Relationship',
                      ),
                      items: RelationshipCategory.values.map((c) {
                        return DropdownMenuItem(
                          value: c,
                          child: Text(c.displayName),
                        );
                      }).toList(),
                      onChanged: (val) => setState(
                        () => selectedCategory =
                            val ?? RelationshipCategory.other,
                      ),
                    ),
                    const SizedBox(height: 12),
                    DropdownButtonFormField<MessageTone>(
                      initialValue: selectedTone,
                      decoration: const InputDecoration(
                        labelText: 'Preferred Tone',
                      ),
                      items: MessageTone.values.map((t) {
                        return DropdownMenuItem(
                          value: t,
                          child: Text(t.displayName),
                        );
                      }).toList(),
                      onChanged: (val) => setState(
                        () => selectedTone = val ?? MessageTone.warm,
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: factsCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Verified Facts (comma-separated)',
                        hintText: 'e.g. Loves dogs, enjoys cycling',
                      ),
                    ),
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('Cancel'),
                ),
                FilledButton(
                  onPressed: () async {
                    final name = nameCtrl.text.trim();
                    if (name.isEmpty) return;

                    final facts = factsCtrl.text
                        .split(',')
                        .map((f) => f.trim())
                        .where((f) => f.isNotEmpty)
                        .toList();

                    final personId =
                        'person-${DateTime.now().millisecondsSinceEpoch}';
                    final newPerson = Person(
                      id: personId,
                      name: name,
                      birthdayMonth: selectedMonth,
                      birthdayDay: selectedDay,
                      phoneNumber: phoneCtrl.text.trim().isEmpty
                          ? null
                          : phoneCtrl.text.trim(),
                      relationship: selectedCategory,
                      preferredTone: selectedTone,
                      importantFacts: facts,
                      createdAt: DateTime.now(),
                      updatedAt: DateTime.now(),
                    );

                    final nextDate = Birthday.nextBirthdayDate(
                      month: selectedMonth,
                      day: selectedDay,
                      from: DateTime.now(),
                    );

                    final newBirthday = Birthday(
                      id: 'birthday-${DateTime.now().millisecondsSinceEpoch}',
                      personId: personId,
                      cycleYear: nextDate.year,
                      date: nextDate,
                      status: BirthdayStatus.upcoming,
                      createdAt: DateTime.now(),
                      updatedAt: DateTime.now(),
                    );

                    await ref
                        .read(peopleRepositoryProvider)
                        .savePerson(newPerson);
                    await ref
                        .read(birthdaysRepositoryProvider)
                        .saveBirthday(newBirthday);

                    if (context.mounted) {
                      Navigator.of(context).pop();
                    }
                  },
                  child: const Text('Save'),
                ),
              ],
            );
          },
        );
      },
    );
  }
}
