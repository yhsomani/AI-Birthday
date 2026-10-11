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
    this.deferQuietHours = false,
    this.hideNames = false,
    this.syncError,
  });

  final bool enabled;
  final Set<ReminderKind> kinds;
  final QuietHours quietHours;
  final bool deferQuietHours;
  final bool hideNames;
  final String? syncError;

  ReminderSettings copyWith({
    bool? enabled,
    Set<ReminderKind>? kinds,
    QuietHours? quietHours,
    bool? deferQuietHours,
    bool? hideNames,
    String? syncError,
  }) {
    return ReminderSettings(
      enabled: enabled ?? this.enabled,
      kinds: kinds ?? this.kinds,
      quietHours: quietHours ?? this.quietHours,
      deferQuietHours: deferQuietHours ?? this.deferQuietHours,
      hideNames: hideNames ?? this.hideNames,
      syncError: syncError,
    );
  }
}

class ReminderSettingsController extends Notifier<ReminderSettings> {
  ReminderSettings _lastCommitted = const ReminderSettings();
  int _revision = 0;
  Future<void> _persistChain = Future.value();

  @override
  ReminderSettings build() {
    _lastCommitted = const ReminderSettings();
    _revision = 0;
    _persistChain = Future.value();
    _loadFromDatabase();
    return _lastCommitted;
  }

  Future<void> _loadFromDatabase() async {
    try {
      final database = ref.read(databaseProvider);
      final rows = await database
          .select(database.reminderSettingsEntries)
          .get();
      final row = rows.where((r) => r.key == 'default').firstOrNull;
      if (row != null) {
        final tokens = row.kinds.split(',').where((k) => k.isNotEmpty).toList();
        final deferRow = rows
            .where((r) => r.key == 'defer_quiet_hours')
            .firstOrNull;
        final hideRow = rows.where((r) => r.key == 'hide_names').firstOrNull;
        final deferQuiet =
            tokens.contains('opt:defer_quiet_hours') ||
            (deferRow?.enabled ?? false);
        final hideNames =
            tokens.contains('opt:hide_names') || (hideRow?.enabled ?? false);

        final kinds = tokens
            .where((k) => !k.startsWith('opt:'))
            .map(
              (k) => ReminderKind.values.firstWhere(
                (v) => v.name == k,
                orElse: () => ReminderKind.birthday,
              ),
            )
            .toSet();
        final loaded = ReminderSettings(
          enabled: row.enabled,
          // A stored empty string is a deliberate "no milestones" choice, not
          // missing data, so it must not fall back to the default set.
          kinds: kinds,
          quietHours: QuietHours(
            start: Duration(minutes: row.quietHoursStartMinutes),
            end: Duration(minutes: row.quietHoursEndMinutes),
          ),
          deferQuietHours: deferQuiet,
          hideNames: hideNames,
          syncError: null,
        );
        if (_revision == 0) {
          _lastCommitted = loaded;
          state = loaded;
          await _syncService();
        }
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
      final kindTokens = [
        ...s.kinds.map((k) => k.name),
        if (s.deferQuietHours) 'opt:defer_quiet_hours',
        if (s.hideNames) 'opt:hide_names',
      ].join(',');

      await database
          .into(database.reminderSettingsEntries)
          .insertOnConflictUpdate(
            db.ReminderSettingsEntriesCompanion(
              key: const Value('default'),
              enabled: Value(s.enabled),
              kinds: Value(kindTokens),
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

  void _schedulePersistAndSync() {
    _revision++;
    final rev = _revision;
    _persistChain = _persistChain
        .then((_) async {
          final target = state;
          final saved = await _saveToDatabase(target);
          if (!saved) {
            if (rev == _revision) {
              // Persistence failed: never keep a toggle the store rejected. Restore
              // the pre-change value (UI back to truth) and keep the error visible.
              state = _lastCommitted.copyWith(syncError: state.syncError);
            }
            return;
          }
          _lastCommitted = target;
          if (rev == _revision) {
            await _syncService();
          }
        })
        .catchError((_) {});
  }

  void setEnabled(bool enabled) {
    state = state.copyWith(enabled: enabled, syncError: null);
    _schedulePersistAndSync();
  }

  void setKind(ReminderKind kind, bool on) {
    final kinds = Set<ReminderKind>.of(state.kinds);
    if (on) {
      kinds.add(kind);
    } else {
      kinds.remove(kind);
    }
    state = state.copyWith(kinds: kinds, syncError: null);
    _schedulePersistAndSync();
  }

  void setQuietHours(QuietHours quietHours) {
    state = state.copyWith(quietHours: quietHours, syncError: null);
    _schedulePersistAndSync();
  }

  void setDeferQuietHours(bool deferQuietHours) {
    state = state.copyWith(deferQuietHours: deferQuietHours, syncError: null);
    _schedulePersistAndSync();
  }

  void setHideNames(bool hideNames) {
    state = state.copyWith(hideNames: hideNames, syncError: null);
    _schedulePersistAndSync();
  }
}

final remindersEnabledProvider = Provider<bool>((ref) {
  return ref.watch(reminderSettingsProvider).enabled;
});

final reminderSettingsProvider =
    NotifierProvider<ReminderSettingsController, ReminderSettings>(
      ReminderSettingsController.new,
    );
