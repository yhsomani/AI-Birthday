import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../shared/design_system/empty_state.dart';
import '../../birthdays/domain/birthday_engine.dart';
import '../data/person_providers.dart';
import '../domain/person.dart';

/// Birthdays list — every active recipient (SSOT §7, §14).
///
/// Live-updating via [personListProvider]. Tapping a recipient opens edit;
/// the FAB opens create; the tile menu offers edit/delete with undo.
class PersonListScreen extends ConsumerWidget {
  const PersonListScreen({super.key});

  static const BirthdayEngine _engine = BirthdayEngine();

  String _dateLabel(Person person) {
    final date = DateTime(2000, person.birthdayMonth, person.birthdayDay);
    return DateFormat.MMMd().format(date);
  }

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

  String _subtitle(Person person) {
    final date = _dateLabel(person);
    final year = person.birthYear;
    if (year == null) return date;
    return '$date · turns ${_next(person).year - year}';
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final people = ref.watch(personListProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Birthdays')),
      floatingActionButton: FloatingActionButton(
        onPressed: () => context.push('/people/add'),
        tooltip: 'Add a birthday',
        child: const Icon(Icons.add),
      ),
      body: people.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stackTrace) => EmptyState(
          icon: Icons.error_outline,
          title: 'Could not load birthdays',
          message: 'Try again in a moment.',
          action: FilledButton.tonalIcon(
            onPressed: () => ref.invalidate(personListProvider),
            icon: const Icon(Icons.refresh),
            label: const Text('Retry'),
          ),
        ),
        data: (list) {
          if (list.isEmpty) {
            return const EmptyState(
              icon: Icons.calendar_month_outlined,
              title: 'No birthdays yet',
              message: 'People you add will appear here.',
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.only(bottom: 88),
            itemCount: list.length,
            separatorBuilder: (context, index) => const Divider(height: 1),
            itemBuilder: (context, index) {
              final person = list[index];
              return _PersonTile(
                person: person,
                subtitle: _subtitle(person),
                countdown: _countdownLabel(_next(person)),
              );
            },
          );
        },
      ),
    );
  }
}

/// One recipient row with edit/delete actions. Delete is soft (tombstone)
/// and always undoable, so removing a person is never destructive.
class _PersonTile extends ConsumerWidget {
  const _PersonTile({
    required this.person,
    required this.subtitle,
    required this.countdown,
  });

  final Person person;
  final String subtitle;
  final String countdown;

  Future<void> _delete(BuildContext context, WidgetRef ref) async {
    final messenger = ScaffoldMessenger.of(context);
    final service = ref.read(personServiceProvider);
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

    await service.remove(person.id);
    if (!context.mounted) return;
    messenger.showSnackBar(
      SnackBar(
        content: Text('Deleted ${person.name}.'),
        action: SnackBarAction(
          label: 'Undo',
          // The tile may already be unmounted when this fires, so use the
          // captured service rather than the widget's `ref`.
          onPressed: () => service.restore(person.id),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ListTile(
      leading: CircleAvatar(child: Text(_initials(person.name))),
      title: Text(person.name),
      subtitle: Text(subtitle),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: Text(countdown),
          ),
          PopupMenuButton<_TileAction>(
            tooltip: 'Person actions',
            onSelected: (action) {
              switch (action) {
                case _TileAction.edit:
                  context.push('/people/edit/${person.id}');
                case _TileAction.delete:
                  _delete(context, ref);
              }
            },
            itemBuilder: (context) => const [
              PopupMenuItem(value: _TileAction.edit, child: Text('Edit')),
              PopupMenuItem(value: _TileAction.delete, child: Text('Delete')),
            ],
          ),
        ],
      ),
      onTap: () => context.push('/people/edit/${person.id}'),
    );
  }
}

enum _TileAction { edit, delete }

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
