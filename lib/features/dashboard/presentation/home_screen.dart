import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../shared/design_system/empty_state.dart';
import '../../people/data/person_providers.dart';
import '../domain/home_feed.dart';

/// Home — action-oriented command center (SSOT §15).
///
/// Today / Upcoming come from the birthday engine over the live people list.
/// "Action needed" reflects birthdays inside the 7-day approach window; true
/// message status lands with Message Studio.
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key, this.now});

  /// Injected clock for deterministic tests; defaults to the wall clock.
  final DateTime Function()? now;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final peopleAsync = ref.watch(personListProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Home')),
      body: peopleAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stackTrace) =>
            const Center(child: Text('Could not load birthdays.')),
        data: (people) {
          final feed = const HomeFeedBuilder().build(
            people: people,
            reference: (now ?? DateTime.now)(),
          );
          if (feed.isEmpty) {
            return const EmptyState(
              icon: Icons.cake_outlined,
              title: 'No birthdays yet',
              message: 'Add a birthday and AI-Birthday will take care of reminders, drafts and delivery.',
            );
          }
          return _HomeBody(feed: feed);
        },
      ),
    );
  }
}

class _HomeBody extends StatelessWidget {
  const _HomeBody({required this.feed});

  final HomeFeed feed;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _SectionCard(
          title: 'Today',
          children: feed.today.isEmpty
              ? [
                  Text(
                    'No birthdays today.',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ]
              : [for (final item in feed.today) _PersonRow(item: item)],
        ),
        const SizedBox(height: 12),
        _SectionCard(
          title: 'Upcoming',
          children: feed.upcoming.isEmpty
              ? [
                  Text(
                    'Nothing in the next 30 days.',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ]
              : [for (final item in feed.upcoming) _PersonRow(item: item)],
        ),
        const SizedBox(height: 12),
        _SectionCard(
          title: 'Action needed',
          children: [
            Text(
              feed.actionNeededCount == 1
                  ? '1 birthday within the next week needs a message'
                  : '${feed.actionNeededCount} birthdays within the next week need messages',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Quick actions',
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    FilledButton.tonalIcon(
                      onPressed: () => context.push('/people/add'),
                      icon: const Icon(Icons.person_add_alt_1),
                      label: const Text('Add a birthday'),
                    ),
                    OutlinedButton.icon(
                      onPressed: () => context.go('/calendar'),
                      icon: const Icon(Icons.calendar_month_outlined),
                      label: const Text('View calendar'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({required this.title, required this.children});

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 8),
            ...children,
          ],
        ),
      ),
    );
  }
}

class _PersonRow extends StatelessWidget {
  const _PersonRow({required this.item});

  final HomeItem item;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final turn = item.turnLabel;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          CircleAvatar(radius: 14, child: Text(_initials(item.person.name))),
          const SizedBox(width: 12),
          Expanded(
            child: Text(item.person.name, style: theme.textTheme.bodyLarge),
          ),
          Text(
            [item.countdownLabel, ?turn].join(' · '),
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

String _initials(String name) {
  final parts = name
      .trim()
      .split(RegExp(r'\s+'))
      .where((p) => p.isNotEmpty)
      .toList();
  if (parts.isEmpty) return '?';
  if (parts.length == 1) return parts.first.substring(0, 1).toUpperCase();
  return '${parts.first[0]}${parts.last[0]}'.toUpperCase();
}
