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
import 'package:ai_birthday/shared/design_system/design_system.dart';

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
            } else if (days > 7 && days <= 30) {
              upcomingBirthdays.add(b);
            }
          }

          return ListView(
            padding: const EdgeInsets.fromLTRB(
              16,
              12,
              16,
              AppSpacing.bottomClearance,
            ),
            children: [
              // 1. Above-The-Fold Command Center Summary (What / Why / Next)
              _buildCommandHeader(
                context,
                todayCount: todayBirthdays.length,
                actionNeededCount: actionNeededBirthdays.length,
                totalTracked: people.length,
              ),
              const SizedBox(height: AppSpacing.lg),

              // 2. Action Needed Section (High-Intent Next Steps)
              if (actionNeededBirthdays.isNotEmpty) ...[
                AppSectionHeader(
                  title: 'Action Needed',
                  count: actionNeededBirthdays.length,
                  isAccent: true,
                ),
                const SizedBox(height: AppSpacing.xs),
                ...actionNeededBirthdays.map((b) {
                  final person = peopleMap[b.personId];
                  return _buildActionCard(context, ref, b, person);
                }),
                const SizedBox(height: AppSpacing.lg),
              ],

              // 3. Today's Celebrations Section
              if (todayBirthdays.isNotEmpty) ...[
                AppSectionHeader(
                  title: "Today's Birthdays",
                  count: todayBirthdays.length,
                  isAccent: true,
                ),
                const SizedBox(height: AppSpacing.xs),
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
                const SizedBox(height: AppSpacing.lg),
              ],

              // 4. Upcoming Timeline Section
              AppSectionHeader(
                title: 'Upcoming Birthdays',
                count: upcomingBirthdays.length,
              ),
              const SizedBox(height: AppSpacing.xs),
              if (upcomingBirthdays.isEmpty)
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(AppSpacing.xl),
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
              const SizedBox(height: AppSpacing.lg),

              // 5. Quick Actions Bar
              _buildQuickActions(context),
              const SizedBox(height: AppSpacing.xl),
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
                  color: AppColors.primaryTerracotta,
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
            totalTracked == 0
                ? "Let's add your first birthday"
                : todayCount > 0
                ? '$todayCount ${todayCount == 1 ? 'Birthday' : 'Birthdays'} Today'
                : 'All Celebrations On Track',
            style: theme.textTheme.headlineSmall?.copyWith(
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            totalTracked == 0
                ? 'Add friends and family so AI-Birthday can prepare personalized greetings right on time.'
                : actionNeededCount > 0
                ? '$actionNeededCount greeting ${actionNeededCount == 1 ? 'requires' : 'require'} review before sending.'
                : 'No urgent actions needed. Messages will be prepared ahead of upcoming dates.',
            style: TextStyle(
              fontSize: 14,
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          if (totalTracked == 0) ...[
            const SizedBox(height: 16),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                FilledButton.icon(
                  onPressed: () {
                    HapticFeedback.lightImpact();
                    context.push('/people/add');
                  },
                  icon: const Icon(Icons.person_add, size: 16),
                  label: const Text('Add Birthday'),
                ),
                OutlinedButton.icon(
                  onPressed: () {
                    HapticFeedback.lightImpact();
                    context.push('/people');
                  },
                  icon: const Icon(Icons.contacts_outlined, size: 16),
                  label: const Text('Import Contacts'),
                ),
              ],
            ),
          ],
        ],
      ),
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
      margin: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                CircleAvatar(
                  backgroundColor: AppColors.primaryTerracottaContainer,
                  foregroundColor: AppColors.primaryTerracotta,
                  child: Text(
                    person != null && person.name.isNotEmpty
                        ? person.name[0].toUpperCase()
                        : '?',
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                ),
                const SizedBox(width: AppSpacing.sm),
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
                              ? AppColors.primaryTerracotta
                              : Colors.grey[700],
                          fontWeight: isToday
                              ? FontWeight.w600
                              : FontWeight.normal,
                        ),
                      ),
                    ],
                  ),
                ),
                CountdownChip(
                  daysUntil: days,
                  isToday: isToday,
                  customLabel: birthday.status.displayName,
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Flexible(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Channel: ${person?.preferredDeliveryChannel.displayName ?? 'WhatsApp'}',
                        style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                        overflow: TextOverflow.ellipsis,
                      ),
                      if (person != null &&
                          (person.phoneNumber == null ||
                              person.phoneNumber!.trim().isEmpty)) ...[
                        const SizedBox(height: 2),
                        const Text(
                          '⚠️ No phone number',
                          style: TextStyle(
                            fontSize: 11,
                            color: AppColors.accentAmber,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(width: AppSpacing.xs),
                FilledButton.icon(
                  onPressed: () {
                    HapticFeedback.lightImpact();
                    context.push('/message-studio/${birthday.id}');
                  },
                  icon: Icon(_actionButtonIcon(birthday.status), size: 16),
                  label: Text(_actionButtonLabel(birthday.status, person)),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  IconData _actionButtonIcon(BirthdayStatus status) {
    return switch (status) {
      BirthdayStatus.messageNotPrepared => Icons.edit_note_rounded,
      BirthdayStatus.messageDrafted => Icons.rate_review_outlined,
      BirthdayStatus.messageReviewed ||
      BirthdayStatus.readyForDelivery => Icons.send_rounded,
      BirthdayStatus.handedOff => Icons.check_circle_outline,
      _ => Icons.arrow_forward,
    };
  }

  String _actionButtonLabel(BirthdayStatus status, Person? person) {
    return switch (status) {
      BirthdayStatus.messageNotPrepared => 'Draft Greeting',
      BirthdayStatus.messageDrafted => 'Review Draft',
      BirthdayStatus.messageReviewed || BirthdayStatus.readyForDelivery =>
        'Send via ${person?.preferredDeliveryChannel.displayName ?? 'WhatsApp'}',
      BirthdayStatus.handedOff => 'Confirm Sent',
      _ => 'Review & Send',
    };
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
    final subtitle =
        '${person?.relationship.displayName ?? 'Friend'} • ${DateFormat.MMMd().format(birthday.date)}';

    return CelebrationCard(
      name: person?.name ?? 'Unknown',
      subtitle: subtitle,
      daysUntil: days,
      isToday: isToday,
      onTap: () {
        HapticFeedback.lightImpact();
        context.push('/message-studio/${birthday.id}');
      },
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
                color: AppColors.primaryTerracotta,
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
