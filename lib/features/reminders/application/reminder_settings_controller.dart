import 'dart:async';
import 'package:clock/clock.dart' as clock;
import 'package:drift/drift.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/core_providers.dart';
import '../../../core/database/app_database.dart' as db;
import '../../people/data/person_providers.dart';
import '../domain/quiet_hours.dart';
import '../domain/reminder_kind.dart';
import 'reminder_plan_stamp.dart';
import 'reminder_providers.dart';

/// Runtime reminder preferences (legacy SSOT §17 (PRODUCT.md §16.7), FR-005).
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
  static const _planStateKey = 'plan_state';

  ReminderSettings _lastCommitted = const ReminderSettings();
  int _revision = 0;
  Future<void> _persistChain = Future.value();
  Future<bool> _loadFuture = Future.value(false);
  Future<bool>? _loadRetryFuture;
  Future<bool> _replanQueue = Future.value(true);

  @override
  ReminderSettings build() {
    _lastCommitted = const ReminderSettings();
    _revision = 0;
    _persistChain = Future.value();
    _loadRetryFuture = null;
    _replanQueue = Future.value(true);
    _loadFuture = _loadFromDatabase();
    return _lastCommitted;
  }

  /// Completes with true once the persisted settings have been read (an empty
  /// store counts: nothing was ever configured). Completes with false when the
  /// read failed, in which case [state] holds placeholder defaults that must
  /// never drive a device schedule (audit A1-003).
  Future<bool> ensureLoaded() async {
    if (await _loadFuture) return true;

    final retry = _loadRetryFuture ??= _loadFromDatabase();
    final loaded = await retry;
    if (identical(_loadRetryFuture, retry)) {
      _loadRetryFuture = null;
      if (loaded) _loadFuture = Future.value(true);
    }
    return loaded;
  }

  Future<bool> _loadFromDatabase() async {
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
        }
      }
      return true;
    } catch (_) {
      state = state.copyWith(
        syncError:
            'Could not load saved reminder settings. Your existing reminders were not changed.',
      );
      return false;
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

  Future<ReminderPlanStamp?> _readStamp() async {
    try {
      final database = ref.read(databaseProvider);
      final row = await (database.select(
        database.reminderSettingsEntries,
      )..where((r) => r.key.equals(_planStateKey))).getSingleOrNull();
      return ReminderPlanStamp.decode(row?.kinds);
    } catch (_) {
      return null;
    }
  }

  Future<void> _writeStamp(ReminderPlanStamp stamp) async {
    try {
      final database = ref.read(databaseProvider);
      await database
          .into(database.reminderSettingsEntries)
          .insertOnConflictUpdate(
            db.ReminderSettingsEntriesCompanion(
              key: const Value(_planStateKey),
              enabled: Value(stamp.enabled),
              kinds: Value(stamp.encode()),
              quietHoursStartMinutes: const Value(0),
              quietHoursEndMinutes: const Value(0),
            ),
          );
    } catch (error, stackTrace) {
      ref
          .read(loggerProvider)
          .warning(
            'reminders',
            'Could not record the reminder plan state; the plan will be rebuilt on next start.',
            error: error,
            stackTrace: stackTrace,
          );
    }
  }

  /// Rebuilds the device reminder schedule from the stored settings and the
  /// current recipients.
  ///
  /// Always waits for the persisted settings first and refuses to plan when
  /// they could not be read, so default (disabled) settings can never cancel
  /// existing alarms. Calls are serialised.
  ///
  /// * [onlyIfStale]: skip when the last successful plan was made today, in the
  ///   same time zone (start-up / resume guard, audit A1-002).
  /// * [cancelWhenDisabled]: cancel device alarms even if nothing is known to
  ///   be scheduled. Otherwise a disabled plan is a no-op unless alarms may
  ///   exist, because the native cancel also clears unrelated notifications.
  ///
  /// Returns true when the schedule is confirmed (or nothing needed doing).
  /// Failures never throw: they are logged, surfaced through
  /// [ReminderSettings.syncError] and retried at the next start / resume.
  Future<bool> replan({
    required String reason,
    bool onlyIfStale = false,
    bool cancelWhenDisabled = false,
  }) {
    final run = _replanQueue.then(
      (_) => _replan(
        reason: reason,
        onlyIfStale: onlyIfStale,
        cancelWhenDisabled: cancelWhenDisabled,
      ),
    );
    _replanQueue = run.catchError((_) => false);
    return run;
  }

  Future<bool> _replan({
    required String reason,
    required bool onlyIfStale,
    required bool cancelWhenDisabled,
  }) async {
    final logger = ref.read(loggerProvider);
    final now = clock.clock.now();
    if (!await ensureLoaded()) {
      logger.warning(
        'reminders',
        'Reminder re-plan skipped because saved settings could not be loaded.',
        params: {'reason': reason},
      );
      return false;
    }
    final previous = await _readStamp();
    if (onlyIfStale && !reminderPlanNeedsRefresh(previous, now)) return true;
    if (!state.enabled && !cancelWhenDisabled && previous?.enabled != true) {
      return true;
    }

    var ok = false;
    var mayHaveAlarms = previous?.enabled ?? false;
    try {
      final people = await ref.read(peopleStoreProvider).getAll();
      final settings = state;
      mayHaveAlarms = mayHaveAlarms || settings.enabled;
      ok = await ref
          .read(reminderServiceProvider)
          .sync(people: people, settings: settings, reference: now);
      if (ok) mayHaveAlarms = settings.enabled;
      state = state.copyWith(
        syncError: ok
            ? null
            : reason == 'settings_changed'
            ? 'Reminders are enabled, but Android could not confirm the schedule. Check notification and precise reminder access.'
            : 'Reminders could not be refreshed. They will be retried automatically the next time the app opens.',
      );
    } catch (error, stackTrace) {
      logger.warning(
        'reminders',
        'Reminder re-plan failed.',
        params: {'reason': reason},
        error: error,
        stackTrace: stackTrace,
      );
      state = state.copyWith(
        syncError: reason == 'settings_changed'
            ? 'Reminders could not be updated on this device. Check notification access and try again.'
            : 'Reminders could not be refreshed. They will be retried automatically the next time the app opens.',
      );
    }
    if (!ok) {
      logger.warning(
        'reminders',
        'Reminder schedule is not confirmed; retry is pending.',
        params: {'reason': reason},
      );
    }
    await _writeStamp(
      ReminderPlanStamp.at(now, enabled: mayHaveAlarms, ok: ok),
    );
    return ok;
  }

  Future<void> _syncService() async {
    if (WidgetsBinding.instance is! WidgetsFlutterBinding) return;
    await replan(reason: 'settings_changed', cancelWhenDisabled: true);
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
