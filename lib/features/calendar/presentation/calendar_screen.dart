import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../birthdays/domain/birthday_engine.dart';
import '../../people/data/person_providers.dart';
import '../../people/domain/person.dart';
import '../../../shared/design_system/design_system.dart';

/// Calendar view of annual birthdays (SSOT §15 navigation, §14).
///
/// Renders a month grid; every birthday that falls in the visible month is
/// marked on its (leap-resolved) day. Days are Monday-start, consistent with
/// [DateTime.weekday]. Tapping a day with birthdays opens the list for it.
class CalendarScreen extends ConsumerStatefulWidget {
  const CalendarScreen({super.key, this.now});

  /// Injected clock for deterministic tests; defaults to the wall clock.
  final DateTime Function()? now;

  @override
  ConsumerState<CalendarScreen> createState() => _CalendarScreenState();
}

class _CalendarScreenState extends ConsumerState<CalendarScreen> {
  static const List<String> _weekdays = [
    'Mon',
    'Tue',
    'Wed',
    'Thu',
    'Fri',
    'Sat',
    'Sun',
  ];

  late DateTime _visibleMonth;

  @override
  void initState() {
    super.initState();
    final now = (widget.now ?? DateTime.now)();
    _visibleMonth = DateTime(now.year, now.month);
  }

  void _shiftMonth(int delta) {
    setState(
      () => _visibleMonth = DateTime(
        _visibleMonth.year,
        _visibleMonth.month + delta,
      ),
    );
  }

  /// Resolves a birthday onto the calendar of [year] using the engine's
  /// default Feb 29 → Feb 28 leap rule, matching [BirthdayEngine] exactly.
  static int _resolvedDay(int month, int day, int year) {
    if (month == BirthdayEngine.feb29Month &&
        day == BirthdayEngine.feb29Day &&
        !BirthdayEngine.isLeapYear(year)) {
      return 28;
    }
    return day;
  }

  void _showDay(BuildContext context, DateTime date, List<Person> people) {
    final theme = Theme.of(context);
    AppBottomSheet.show<void>(
      context: context,
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: AppSpacing.sm),
          child: Text(
            'Birthdays ${DateFormat.MMMd().format(date)}',
            style: theme.textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
        for (final person in people)
          ListTile(
            contentPadding: EdgeInsets.zero,
            onTap: () {
              Navigator.of(context).pop();
              context.push('/message-studio/person/${person.id}');
            },
            leading: CircleAvatar(
              backgroundColor: AppColors.primaryTerracottaContainer,
              foregroundColor: AppColors.primaryTerracotta,
              child: Text(_initials(person.name)),
            ),
            title: Text(
              person.name,
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
            subtitle: person.birthYear != null
                ? Text(
                    '${person.birthYear!}'
                    ' · turns ${date.year - person.birthYear!}',
                  )
                : null,
          ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final peopleAsync = ref.watch(personListProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Calendar')),
      body: peopleAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stackTrace) => EmptyState(
          icon: Icons.cloud_off_outlined,
          title: 'Could not load birthdays',
          message: 'Your saved birthdays have not been deleted.',
          action: TextButton.icon(
            onPressed: () => ref.invalidate(personListProvider),
            icon: const Icon(Icons.refresh),
            label: const Text('Try again'),
          ),
        ),
        data: (people) {
          if (people.isEmpty) {
            return EmptyState(
              icon: Icons.calendar_month_outlined,
              title: 'No birthdays yet',
              message:
                  'Add a birthday contact and their special day will appear on the calendar.',
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
          return _monthGrid(context, people);
        },
      ),
    );
  }

  Widget _monthGrid(BuildContext context, List<Person> people) {
    final theme = Theme.of(context);
    final year = _visibleMonth.year;
    final month = _visibleMonth.month;
    final today = (widget.now ?? DateTime.now)();
    final todayDay = today.year == year && today.month == month ? today.day : 0;

    final birthdaysForDay = <int, List<Person>>{};
    for (final person in people) {
      if (!person.hasBirthday) continue;
      if (person.birthdayMonth != month) continue;
      final day = _resolvedDay(month, person.birthdayDay!, year);
      birthdaysForDay.putIfAbsent(day, () => []).add(person);
    }

    final first = DateTime(year, month, 1);
    final lead = first.weekday - 1;
    final daysInMonth = BirthdayEngine.daysInMonth(month, year);
    final totalCells = lead + daysInMonth;

    return Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        children: [
          Row(
            children: [
              IconButton(
                tooltip: 'Previous month',
                onPressed: () => _shiftMonth(-1),
                icon: const Icon(Icons.chevron_left),
              ),
              Expanded(
                child: Text(
                  DateFormat('MMMM yyyy').format(first),
                  textAlign: TextAlign.center,
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              IconButton(
                tooltip: 'Next month',
                onPressed: () => _shiftMonth(1),
                icon: const Icon(Icons.chevron_right),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Row(
            children: [
              for (final label in _weekdays)
                Expanded(
                  child: Text(
                    label,
                    textAlign: TextAlign.center,
                    style: theme.textTheme.labelMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 4),
          Expanded(
            child: Column(
              children: [
                for (var row = 0; row < (totalCells / 7).ceil(); row++)
                  Expanded(
                    child: Row(
                      children: [
                        for (var col = 0; col < 7; col++)
                          Expanded(
                            child: _cell(
                              context,
                              row * 7 + col,
                              lead,
                              daysInMonth,
                              todayDay,
                              birthdaysForDay,
                            ),
                          ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _cell(
    BuildContext context,
    int cellIndex,
    int lead,
    int daysInMonth,
    int todayDay,
    Map<int, List<Person>> birthdaysForDay,
  ) {
    final theme = Theme.of(context);
    final day = cellIndex - lead + 1;
    if (day < 1 || day > daysInMonth) {
      return const SizedBox.shrink();
    }
    final isToday = day == todayDay;
    final people = birthdaysForDay[day];

    final dayNumber = Text(
      '$day',
      style: theme.textTheme.bodyMedium?.copyWith(
        fontWeight: isToday ? FontWeight.w700 : FontWeight.w500,
        color: isToday ? theme.colorScheme.primary : null,
      ),
    );

    final dots = people == null || people.isEmpty
        ? const SizedBox.shrink()
        : Wrap(
            alignment: WrapAlignment.center,
            spacing: 3,
            children: [
              for (final _ in people.take(3))
                Container(
                  width: 6,
                  height: 6,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: theme.colorScheme.primary,
                  ),
                ),
            ],
          );

    return Semantics(
      label: people == null || people.isEmpty
          ? 'Day $day'
          : 'Day $day, ${people.length} birthday${people.length == 1 ? '' : 's'}',
      button: people != null,
      child: InkWell(
        borderRadius: BorderRadius.circular(8),
        onTap: people == null
            ? null
            : () => _showDay(
                context,
                DateTime(_visibleMonth.year, _visibleMonth.month, day),
                people,
              ),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 4),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (isToday)
                Container(
                  constraints: const BoxConstraints(
                    minWidth: 28,
                    minHeight: 28,
                  ),
                  padding: const EdgeInsets.all(2),
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: theme.colorScheme.primary,
                      width: 2,
                    ),
                  ),
                  child: dayNumber,
                )
              else
                dayNumber,
              const SizedBox(height: 4),
              SizedBox(height: 6, child: Center(child: dots)),
            ],
          ),
        ),
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
