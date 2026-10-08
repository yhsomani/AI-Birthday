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
    this.syncError,
  });

  final bool enabled;
  final Set<ReminderKind> kinds;
  final QuietHours quietHours;
  final String? syncError;

  ReminderSettings copyWith({
    bool? enabled,
    Set<ReminderKind>? kinds,
    QuietHours? quietHours,
    String? syncError,
  }) {
    return ReminderSettings(
      enabled: enabled ?? this.enabled,
      kinds: kinds ?? this.kinds,
      quietHours: quietHours ?? this.quietHours,
      syncError: syncError,
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
          syncError: null,
        );
        await _syncService();
      }
    } catch (_) {
      state = state.copyWith(
        syncError:
            'Could not load saved reminder settings. Your existing reminders were not changed.',
      );
    }
  }

  Future<bool> _saveToDatabase(ReminderSettings s) async {
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
      return true;
    } catch (_) {
      state = state.copyWith(
        syncError: 'Could not save your reminder settings. Please try again.',
      );
      return false;
    }
  }

  Future<void> _syncService() async {
    if (WidgetsBinding.instance is! WidgetsFlutterBinding) return;
    try {
      final reminderService = ref.read(reminderServiceProvider);
      final people = await ref.read(peopleStoreProvider).getAll();
      final success = await reminderService.sync(
        people: people,
        settings: state,
      );
      if (success) {
        state = state.copyWith(syncError: null);
      } else {
        state = state.copyWith(
          syncError:
              'Reminders are enabled, but Android could not confirm the schedule. Check notification and precise reminder access.',
        );
      }
    } catch (_) {
      state = state.copyWith(
        syncError:
            'Reminders could not be updated on this device. Check notification access and try again.',
      );
    }
  }

  Future<void> _persistAndSync(ReminderSettings previous) async {
    final saved = await _saveToDatabase(state);
    if (!saved) {
      // Persistence failed: never keep a toggle the store rejected. Restore
      // the pre-change value (UI back to truth) and keep the error visible.
      state = previous.copyWith(syncError: state.syncError);
      return;
    }
    await _syncService();
  }

  void setEnabled(bool enabled) {
    final previous = state;
    state = state.copyWith(enabled: enabled, syncError: null);
    unawaited(_persistAndSync(previous));
  }

  void setKind(ReminderKind kind, bool on) {
    final kinds = Set<ReminderKind>.of(state.kinds);
    if (on) {
      kinds.add(kind);
    } else {
      kinds.remove(kind);
    }
    final previous = state;
    state = state.copyWith(kinds: kinds, syncError: null);
    unawaited(_persistAndSync(previous));
  }

  void setQuietHours(QuietHours quietHours) {
    final previous = state;
    state = state.copyWith(quietHours: quietHours, syncError: null);
    unawaited(_persistAndSync(previous));
  }
}

final remindersEnabledProvider = Provider<bool>((ref) {
  return ref.watch(reminderSettingsProvider).enabled;
});

final reminderSettingsProvider =
    NotifierProvider<ReminderSettingsController, ReminderSettings>(
      ReminderSettingsController.new,
    );
