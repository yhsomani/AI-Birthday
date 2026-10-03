/// Directory of contacts and recipient preferences (SSOT §7, §18).
library;

import 'package:flutter/material.dart';
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
            onPressed: () => _showAddPersonDialog(context, ref),
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
                  const Icon(Icons.people_outline, size: 64, color: Colors.grey),
                  const SizedBox(height: 16),
                  const Text('No contacts added yet'),
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    onPressed: () => _showAddPersonDialog(context, ref),
                    icon: const Icon(Icons.add),
                    label: const Text('Add Birthday Contact'),
                  ),
                ],
              ),
            );
          }

          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: people.length,
            separatorBuilder: (_, __) => const SizedBox(height: 8),
            itemBuilder: (context, index) {
              final person = people[index];
              final dateStr = DateFormat.MMMMd().format(
                DateTime(2026, person.birthdayMonth, person.birthdayDay),
              );

              return Card(
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  leading: CircleAvatar(
                    child: Text(
                      person.name.isNotEmpty ? person.name[0].toUpperCase() : '?',
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
                      Text('$dateStr • ${person.relationship.displayName}'),
                      if (person.importantFacts.isNotEmpty) ...[
                        const SizedBox(height: 4),
                        Text(
                          'Facts: ${person.importantFacts.join(', ')}',
                          style: TextStyle(fontSize: 12, color: Colors.grey[600]),
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
        onPressed: () => _showAddPersonDialog(context, ref),
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
                    radius: 24,
                    child: Text(
                      person.name[0],
                      style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          person.name,
                          style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
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
                DateFormat.MMMMd().format(DateTime(2026, person.birthdayMonth, person.birthdayDay)),
              ),
              if (person.phoneNumber != null) _buildDetailRow('Phone', person.phoneNumber!),
              _buildDetailRow('Preferred Tone', person.preferredTone.displayName),
              _buildDetailRow('Delivery Channel', person.preferredDeliveryChannel.displayName),
              const SizedBox(height: 12),
              const Text('Known Facts for AI (User-provided only):', style: TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 6),
              if (person.importantFacts.isEmpty)
                const Text('No specific facts added yet.', style: TextStyle(fontStyle: FontStyle.italic))
              else
                ...person.importantFacts.map((f) => Padding(
                      padding: const EdgeInsets.symmetric(vertical: 2),
                      child: Row(
                        children: [
                          const Icon(Icons.check_circle_outline, size: 16, color: Colors.green),
                          const SizedBox(width: 8),
                          Expanded(child: Text(f)),
                        ],
                      ),
                    )),
              const SizedBox(height: 24),
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
                      decoration: const InputDecoration(labelText: 'Full Name *'),
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
                            value: selectedMonth,
                            decoration: const InputDecoration(labelText: 'Month'),
                            items: List.generate(12, (i) => i + 1).map((m) {
                              return DropdownMenuItem(
                                value: m,
                                child: Text(DateFormat.MMM().format(DateTime(2026, m))),
                              );
                            }).toList(),
                            onChanged: (val) => setState(() => selectedMonth = val ?? 1),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: DropdownButtonFormField<int>(
                            value: selectedDay,
                            decoration: const InputDecoration(labelText: 'Day'),
                            items: List.generate(31, (i) => i + 1).map((d) {
                              return DropdownMenuItem(value: d, child: Text('$d'));
                            }).toList(),
                            onChanged: (val) => setState(() => selectedDay = val ?? 1),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    DropdownButtonFormField<RelationshipCategory>(
                      value: selectedCategory,
                      decoration: const InputDecoration(labelText: 'Relationship'),
                      items: RelationshipCategory.values.map((c) {
                        return DropdownMenuItem(value: c, child: Text(c.displayName));
                      }).toList>,
                      onChanged: (val) => setState(() => selectedCategory = val ?? RelationshipCategory.other),
                    ),
                    const SizedBox(height: 12),
                    DropdownButtonFormField<MessageTone>(
                      value: selectedTone,
                      decoration: const InputDecoration(labelText: 'Preferred Tone'),
                      items: MessageTone.values.map((t) {
                        return DropdownMenuItem(value: t, child: Text(t.displayName));
                      }).toList(),
                      onChanged: (val) => setState(() => selectedTone = val ?? MessageTone.warm),
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

                    final personId = 'person-${DateTime.now().millisecondsSinceEpoch}';
                    final newPerson = Person(
                      id: personId,
                      name: name,
                      birthdayMonth: selectedMonth,
                      birthdayDay: selectedDay,
                      phoneNumber: phoneCtrl.text.trim().isEmpty ? null : phoneCtrl.text.trim(),
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

                    await ref.read(peopleRepositoryProvider).savePerson(newPerson);
                    await ref.read(birthdaysRepositoryProvider).saveBirthday(newBirthday);

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
