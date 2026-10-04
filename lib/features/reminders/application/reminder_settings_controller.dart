import 'dart:async';
import 'package:drift/drift.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/core_providers.dart';
import '../../../core/database/app_database.dart' as db;
import '../../people/data/person_providers.dart';
import '../domain/quiet_hours.dart';
import '../domain/reminder_kind.dart';
import 'reminder_providers.dart';

/// Runtime reminder preferences (SSOT §17, FR-005).
///
/// Persisted into SQLite via Drift.
class ReminderSettings {
  const ReminderSettings({
    this.enabled = false,
    this.kinds = const {
      ReminderKind.approaching,
      ReminderKind.prepare,
      ReminderKind.ready,
      ReminderKind.birthday,
    },
    this.quietHours = const QuietHours.night(),
  });

  final bool enabled;
  final Set<ReminderKind> kinds;
  final QuietHours quietHours;

  ReminderSettings copyWith({
    bool? enabled,
    Set<ReminderKind>? kinds,
    QuietHours? quietHours,
  }) {
    return ReminderSettings(
      enabled: enabled ?? this.enabled,
      kinds: kinds ?? this.kinds,
      quietHours: quietHours ?? this.quietHours,
    );
  }
}

class ReminderSettingsController extends Notifier<ReminderSettings> {
  @override
  ReminderSettings build() {
    _loadFromDatabase();
    return const ReminderSettings();
  }

  Future<void> _loadFromDatabase() async {
    try {
      final database = ref.read(databaseProvider);
      final row = await (database.select(
        database.reminderSettingsEntries,
      )..where((r) => r.key.equals('default'))).getSingleOrNull();
      if (row != null) {
        final kinds = row.kinds
            .split(',')
            .where((k) => k.isNotEmpty)
            .map(
              (k) => ReminderKind.values.firstWhere(
                (v) => v.name == k,
                orElse: () => ReminderKind.birthday,
              ),
            )
            .toSet();
        state = ReminderSettings(
          enabled: row.enabled,
          kinds: kinds.isEmpty ? state.kinds : kinds,
          quietHours: QuietHours(
            start: Duration(minutes: row.quietHoursStartMinutes),
            end: Duration(minutes: row.quietHoursEndMinutes),
          ),
        );
        _syncService();
      }
    } catch (_) {
      // In-memory / test environment fallback
    }
  }

  Future<void> _saveToDatabase(ReminderSettings s) async {
    try {
      final database = ref.read(databaseProvider);
      await database
          .into(database.reminderSettingsEntries)
          .insertOnConflictUpdate(
            db.ReminderSettingsEntriesCompanion(
              key: const Value('default'),
              enabled: Value(s.enabled),
              kinds: Value(s.kinds.map((k) => k.name).join(',')),
              quietHoursStartMinutes: Value(s.quietHours.start.inMinutes),
              quietHoursEndMinutes: Value(s.quietHours.end.inMinutes),
            ),
          );
      _syncService();
    } catch (_) {
      // In-memory / test environment fallback
    }
  }

  Future<void> _syncService() async {
    if (WidgetsBinding.instance is! WidgetsFlutterBinding) return;
    try {
      final reminderService = ref.read(reminderServiceProvider);
      final people = await ref.read(peopleStoreProvider).getAll();
      await reminderService.sync(people: people, settings: state);
    } catch (_) {
      // In-memory / test environment fallback
    }
  }

  void setEnabled(bool enabled) {
    state = state.copyWith(enabled: enabled);
    _saveToDatabase(state);
  }

  void setKind(ReminderKind kind, bool on) {
    final kinds = Set<ReminderKind>.of(state.kinds);
    if (on) {
      kinds.add(kind);
    } else {
      kinds.remove(kind);
    }
    state = state.copyWith(kinds: kinds);
    _saveToDatabase(state);
  }

  void setQuietHours(QuietHours quietHours) {
    state = state.copyWith(quietHours: quietHours);
    _saveToDatabase(state);
  }
}

final remindersEnabledProvider = Provider<bool>((ref) {
  return ref.watch(reminderSettingsProvider).enabled;
});

final reminderSettingsProvider =
    NotifierProvider<ReminderSettingsController, ReminderSettings>(
      ReminderSettingsController.new,
    );
