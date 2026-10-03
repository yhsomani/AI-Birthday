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
            padding: const EdgeInsets.all(16),
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
    final isToday = todayCount > 0;

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: isToday
              ? [const Color(0xFFE03E5D), const Color(0xFFF59E0B)]
              : [
                  theme.colorScheme.primaryContainer,
                  theme.colorScheme.surfaceVariant,
                ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            isToday
                ? '$todayCount ${todayCount == 1 ? 'Birthday' : 'Birthdays'} Today!'
                : 'All caught up!',
            style: TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.bold,
              color: isToday
                  ? Colors.white
                  : theme.colorScheme.onPrimaryContainer,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            isToday
                ? 'Review prepared messages and send personalized greetings on WhatsApp.'
                : 'Upcoming birthdays are monitored with automated draft preparation.',
            style: TextStyle(
              fontSize: 14,
              color: isToday
                  ? Colors.white.withOpacity(0.9)
                  : theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
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
        Text(
          title,
          style: Theme.of(
            context,
          ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
        ),
        if (badgeCount != null && badgeCount > 0) ...[
          const SizedBox(width: 8),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.primary.withOpacity(0.12),
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
                          ).colorScheme.secondary.withOpacity(0.2),
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
                          : Theme.of(context).colorScheme.surfaceVariant,
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
                  FilledButton.tonalIcon(
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
