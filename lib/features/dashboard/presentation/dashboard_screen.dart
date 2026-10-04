/// Command Center Dashboard for AI-Birthday (SSOT §15).
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final birthdaysAsync = ref.watch(birthdaysStreamProvider);
    final peopleAsync = ref.watch(peopleStreamProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Row(
          children: [
            Icon(Icons.cake_outlined, color: Color(0xFFE03E5D)),
            SizedBox(width: 8),
            Text('AI-Birthday', style: TextStyle(fontWeight: FontWeight.bold)),
          ],
        ),
      ),
      body: birthdaysAsync.when(
        data: (birthdays) {
          final people = peopleAsync.value ?? [];
          final peopleMap = {for (final p in people) p.id: p};
          final now = DateTime.now();

          // ⚡ PERFORMANCE: Replace multiple O(N) filters with expensive DateTime instantiations
          // inside `daysUntil()` with a single O(N) loop that computes the diff once per item.
          final todayBirthdays = <Birthday>[];
          final upcomingBirthdays = <Birthday>[];
          for (final b in birthdays) {
            final days = b.daysUntil(now);
            if (days == 0) {
              todayBirthdays.add(b);
            } else if (days > 0) {
              upcomingBirthdays.add(b);
            }
          }

          return ListView(
            padding: EdgeInsets.symmetric(
              horizontal: MediaQuery.of(context).size.width > 600
                  ? (MediaQuery.of(context).size.width - 600) / 2
                  : 16,
              vertical: 16,
            ),
            children: [
              // Hero Greeting Banner
              _buildGreetingBanner(context, todayBirthdays.length),
              const SizedBox(height: 20),

              // Today's Celebrations
              if (todayBirthdays.isNotEmpty) ...[
                _buildSectionHeader(
                  context,
                  title: "Today's Birthdays 🎉",
                  badgeCount: todayBirthdays.length,
                ),
                const SizedBox(height: 10),
                ...todayBirthdays.map((b) {
                  final person = peopleMap[b.personId];
                  return _buildBirthdayCard(
                    context,
                    ref,
                    b,
                    person,
                    isToday: true,
                  );
                }),
                const SizedBox(height: 24),
              ],

              // Upcoming Birthdays
              _buildSectionHeader(
                context,
                title: 'Upcoming Birthdays',
                badgeCount: upcomingBirthdays.length,
              ),
              const SizedBox(height: 10),
              if (upcomingBirthdays.isEmpty)
                const Card(
                  child: Padding(
                    padding: EdgeInsets.all(24),
                    child: Center(
                      child: Text('No upcoming birthdays tracked yet.'),
                    ),
                  ),
                )
              else
                ...upcomingBirthdays.map((b) {
                  final person = peopleMap[b.personId];
                  return _buildBirthdayCard(
                    context,
                    ref,
                    b,
                    person,
                    isToday: false,
                  );
                }),
            ],
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, _) => Center(child: Text('Error loading birthdays: $err')),
      ),
    );
  }

  Widget _buildGreetingBanner(BuildContext context, int todayCount) {
    final theme = Theme.of(context);
    final textTheme = theme.textTheme;
    final isToday = todayCount > 0;

    return Card(
      elevation: 0,
      color: theme.colorScheme.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: isToday
              ? theme.colorScheme.primary
              : theme.dividerColor.withValues(alpha: 0.1),
          width: isToday ? 2 : 1,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  isToday ? Icons.celebration : Icons.auto_awesome,
                  color: isToday
                      ? theme.colorScheme.primary
                      : theme.colorScheme.secondary,
                  size: 28,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    isToday
                        ? '$todayCount ${todayCount == 1 ? 'Birthday' : 'Birthdays'} Today'
                        : 'Your AI Birthday Assistant',
                    style: textTheme.headlineSmall?.copyWith(
                      color: isToday
                          ? theme.colorScheme.primary
                          : textTheme.titleLarge?.color,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              isToday
                  ? 'Review the AI-generated drafts and send a personalized message.'
                  : 'Never miss an important date. We monitor your contacts and prepare thoughtful messages in your exact tone before the day arrives.',
              style: textTheme.bodyLarge,
            ),
            const SizedBox(height: 24),
            Row(
              children: [
                if (!isToday)
                  FilledButton.icon(
                    onPressed: () => context.push('/people'),
                    icon: const Icon(Icons.person_add),
                    label: const Text('Add Contact'),
                  )
                else
                  FilledButton.icon(
                    onPressed: () {
                      // Action is contextual per list item below, this is just a quick action hint.
                    },
                    icon: const Icon(Icons.check_circle_outline),
                    label: const Text('View Drafts Below'),
                  ),
                const SizedBox(width: 12),
                if (!isToday)
                  TextButton(
                    onPressed: () => context.push('/people'),
                    child: const Text('View Directory'),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionHeader(
    BuildContext context, {
    required String title,
    int? badgeCount,
  }) {
    return Row(
      children: [
        Text(title, style: Theme.of(context).textTheme.titleLarge),
        if (badgeCount != null && badgeCount > 0) ...[
          const SizedBox(width: 8),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
            decoration: BoxDecoration(
              color: Theme.of(
                context,
              ).colorScheme.primary.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Text(
              '$badgeCount',
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.bold,
                color: Theme.of(context).colorScheme.primary,
              ),
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildBirthdayCard(
    BuildContext context,
    WidgetRef ref,
    Birthday birthday,
    Person? person, {
    required bool isToday,
  }) {
    final now = DateTime.now();
    final days = birthday.daysUntil(now);
    final daysText = isToday
        ? 'Today'
        : (days == 1 ? 'Tomorrow' : 'In $days days');

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: () {
          context.push('/message-studio/${birthday.id}');
        },
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  CircleAvatar(
                    backgroundColor: isToday
                        ? const Color(0xFFE03E5D)
                        : Theme.of(
                            context,
                          ).colorScheme.secondary.withValues(alpha: 0.2),
                    foregroundColor: isToday ? Colors.white : Colors.black87,
                    child: Text(
                      person != null && person.name.isNotEmpty
                          ? person.name[0].toUpperCase()
                          : '?',
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          person?.name ?? 'Unknown',
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        Text(
                          '${person?.relationship.displayName ?? 'Friend'} • ${DateFormat.MMMd().format(birthday.date)}',
                          style: TextStyle(
                            fontSize: 13,
                            color: Colors.grey[600],
                          ),
                        ),
                      ],
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: isToday
                          ? const Color(0xFFE03E5D)
                          : Theme.of(
                              context,
                            ).colorScheme.surfaceContainerHighest,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      daysText,
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: isToday ? Colors.white : null,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Chip(
                    label: Text(
                      birthday.status.displayName,
                      style: const TextStyle(fontSize: 11),
                    ),
                    padding: EdgeInsets.zero,
                    visualDensity: VisualDensity.compact,
                  ),
                  FilledButton.icon(
                    onPressed: () {
                      context.push('/message-studio/${birthday.id}');
                    },
                    icon: const Icon(Icons.edit_note, size: 18),
                    label: const Text('Open Studio'),
                    style: FilledButton.styleFrom(
                      visualDensity: VisualDensity.compact,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
