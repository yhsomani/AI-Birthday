/// Command Center Dashboard for AI-Birthday (SSOT §15).
///
/// Designed to eliminate generic AI/SaaS templates: no gradients, no arbitrary
/// pill-shaped badges, no repetitive card soup. Above the fold clearly establishes
/// What, Who, Why, and Next.
library;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(
          'AI-Birthday',
          style: theme.textTheme.titleLarge?.copyWith(
            fontWeight: FontWeight.w700,
            letterSpacing: -0.3,
          ),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.person_add_outlined),
            tooltip: 'Add Birthday Contact',
            onPressed: () {
              HapticFeedback.lightImpact();
              context.push('/people/add');
            },
          ),
        ],
      ),
      body: birthdaysAsync.when(
        data: (birthdays) {
          final people = peopleAsync.value ?? [];
          final peopleMap = {for (final p in people) p.id: p};
          final now = DateTime.now();

          // Categorize birthdays
          final todayBirthdays = <Birthday>[];
          final upcomingBirthdays = <Birthday>[];
          final actionNeededBirthdays = <Birthday>[];

          for (final b in birthdays) {
            final days = b.daysUntil(now);
            if (days == 0) {
              todayBirthdays.add(b);
              if (b.status != BirthdayStatus.completed) {
                actionNeededBirthdays.add(b);
              }
            } else if (days > 0 && days <= 7) {
              upcomingBirthdays.add(b);
              if (b.status == BirthdayStatus.reminderDue ||
                  b.status == BirthdayStatus.messageNotPrepared ||
                  b.status == BirthdayStatus.messageDrafted) {
                actionNeededBirthdays.add(b);
              }
            } else if (days > 7) {
              upcomingBirthdays.add(b);
            }
          }

          return ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            children: [
              // 1. Above-The-Fold Command Center Summary (What / Why / Next)
              _buildCommandHeader(
                context,
                todayCount: todayBirthdays.length,
                actionNeededCount: actionNeededBirthdays.length,
                totalTracked: people.length,
              ),
              const SizedBox(height: 20),

              // 2. Action Needed Section (High-Intent Next Steps)
              if (actionNeededBirthdays.isNotEmpty) ...[
                _buildSectionTitle(
                  context,
                  title: 'Action Needed',
                  count: actionNeededBirthdays.length,
                  highlight: true,
                ),
                const SizedBox(height: 10),
                ...actionNeededBirthdays.map((b) {
                  final person = peopleMap[b.personId];
                  return _buildActionCard(context, ref, b, person);
                }),
                const SizedBox(height: 20),
              ],

              // 3. Today's Celebrations Section
              if (todayBirthdays.isNotEmpty) ...[
                _buildSectionTitle(
                  context,
                  title: "Today's Birthdays",
                  count: todayBirthdays.length,
                ),
                const SizedBox(height: 10),
                ...todayBirthdays.map((b) {
                  final person = peopleMap[b.personId];
                  return _buildCelebrationCard(
                    context,
                    ref,
                    b,
                    person,
                    isToday: true,
                  );
                }),
                const SizedBox(height: 20),
              ],

              // 4. Upcoming Timeline Section
              _buildSectionTitle(
                context,
                title: 'Upcoming Birthdays',
                count: upcomingBirthdays.length,
              ),
              const SizedBox(height: 10),
              if (upcomingBirthdays.isEmpty)
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Center(
                      child: Text(
                        'No upcoming birthdays tracked in the next 30 days.',
                        style: TextStyle(color: Colors.grey[600]),
                      ),
                    ),
                  ),
                )
              else
                ...upcomingBirthdays.take(5).map((b) {
                  final person = peopleMap[b.personId];
                  return _buildCelebrationCard(
                    context,
                    ref,
                    b,
                    person,
                    isToday: false,
                  );
                }),
              const SizedBox(height: 20),

              // 5. Quick Actions Bar
              _buildQuickActions(context),
              const SizedBox(height: 24),
            ],
          );
        },
        loading: () => const Center(
          child: Padding(
            padding: EdgeInsets.all(40),
            child: CircularProgressIndicator(),
          ),
        ),
        error: (err, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text('Error loading birthdays: $err'),
          ),
        ),
      ),
    );
  }

  /// Structural Command Header establishing What, Who, and Why with crisp editorial styling.
  Widget _buildCommandHeader(
    BuildContext context, {
    required int todayCount,
    required int actionNeededCount,
    required int totalTracked,
  }) {
    final theme = Theme.of(context);
    final dateFormatted = DateFormat.yMMMMd().format(DateTime.now());

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: theme.colorScheme.outlineVariant.withValues(alpha: 0.6),
          width: 1,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                dateFormatted.toUpperCase(),
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 0.8,
                  color: Color(0xFFA64B2A),
                ),
              ),
              Text(
                '$totalTracked Tracked',
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: Colors.grey[600],
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            todayCount > 0
                ? '$todayCount ${todayCount == 1 ? 'Birthday' : 'Birthdays'} Today'
                : 'All Celebrations On Track',
            style: theme.textTheme.headlineSmall?.copyWith(
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            actionNeededCount > 0
                ? '$actionNeededCount greeting ${actionNeededCount == 1 ? 'requires' : 'require'} review before sending.'
                : 'No urgent actions needed. Messages will be prepared ahead of upcoming dates.',
            style: TextStyle(
              fontSize: 14,
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionTitle(
    BuildContext context, {
    required String title,
    required int count,
    bool highlight = false,
  }) {
    return Row(
      children: [
        Text(
          title,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
                color: highlight ? const Color(0xFFA64B2A) : null,
              ),
        ),
        const SizedBox(width: 8),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
          decoration: BoxDecoration(
            color: highlight
                ? const Color(0xFFA64B2A).withValues(alpha: 0.12)
                : Colors.grey.withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(6),
          ),
          child: Text(
            '$count',
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.bold,
              color: highlight ? const Color(0xFFA64B2A) : Colors.grey[700],
            ),
          ),
        ),
      ],
    );
  }

  /// High-priority card clearly establishing Who, Why, and Next action.
  Widget _buildActionCard(
    BuildContext context,
    WidgetRef ref,
    Birthday birthday,
    Person? person,
  ) {
    final now = DateTime.now();
    final days = birthday.daysUntil(now);
    final isToday = days == 0;

    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                CircleAvatar(
                  backgroundColor: const Color(0xFFA64B2A).withValues(alpha: 0.12),
                  foregroundColor: const Color(0xFFA64B2A),
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
                        isToday
                            ? 'Birthday is today! 🎂'
                            : 'Birthday in $days ${days == 1 ? 'day' : 'days'} (${DateFormat.MMMd().format(birthday.date)})',
                        style: TextStyle(
                          fontSize: 13,
                          color: isToday
                              ? const Color(0xFFA64B2A)
                              : Colors.grey[700],
                          fontWeight: isToday ? FontWeight.w600 : FontWeight.normal,
                        ),
                      ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: const Color(0xFFD9822B).withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    birthday.status.displayName,
                    style: const TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFFD9822B),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Channel: ${person?.preferredDeliveryChannel.displayName ?? 'WhatsApp'}',
                  style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                ),
                FilledButton.icon(
                  onPressed: () {
                    HapticFeedback.lightImpact();
                    context.push('/message-studio/${birthday.id}');
                  },
                  icon: const Icon(Icons.arrow_forward, size: 16),
                  label: const Text('Review & Send'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCelebrationCard(
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
      margin: const EdgeInsets.only(bottom: 10),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        leading: CircleAvatar(
          backgroundColor: isToday
              ? const Color(0xFFA64B2A)
              : Colors.grey.withValues(alpha: 0.15),
          foregroundColor: isToday ? Colors.white : Colors.black87,
          child: Text(
            person != null && person.name.isNotEmpty
                ? person.name[0].toUpperCase()
                : '?',
            style: const TextStyle(fontWeight: FontWeight.bold),
          ),
        ),
        title: Text(
          person?.name ?? 'Unknown',
          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
        ),
        subtitle: Text(
          '${person?.relationship.displayName ?? 'Friend'} • ${DateFormat.MMMd().format(birthday.date)}',
          style: TextStyle(fontSize: 13, color: Colors.grey[600]),
        ),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              daysText,
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: isToday ? const Color(0xFFA64B2A) : Colors.grey[600],
              ),
            ),
            const SizedBox(width: 8),
            IconButton(
              icon: const Icon(Icons.chevron_right),
              onPressed: () {
                HapticFeedback.lightImpact();
                context.push('/message-studio/${birthday.id}');
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildQuickActions(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'QUICK ACTIONS',
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.bold,
                letterSpacing: 0.8,
                color: Color(0xFFA64B2A),
              ),
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                OutlinedButton.icon(
                  onPressed: () {
                    HapticFeedback.lightImpact();
                    context.push('/people/add');
                  },
                  icon: const Icon(Icons.person_add_outlined, size: 18),
                  label: const Text('Add Birthday'),
                ),
                OutlinedButton.icon(
                  onPressed: () {
                    HapticFeedback.lightImpact();
                    context.go('/people');
                  },
                  icon: const Icon(Icons.people_outline, size: 18),
                  label: const Text('View People'),
                ),
                OutlinedButton.icon(
                  onPressed: () {
                    HapticFeedback.lightImpact();
                    context.go('/settings');
                  },
                  icon: const Icon(Icons.tune_outlined, size: 18),
                  label: const Text('App Settings'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
