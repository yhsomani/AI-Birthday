import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../domain/quiet_hours.dart';
import '../domain/reminder_kind.dart';

/// Runtime reminder preferences (SSOT §17, FR-005).
///
/// In-memory for this phase; persistence into the settings store lands with the
/// reliability slice (Phase 8). Device scheduled delivery is gated on the
/// notification gateway implementation.
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
  ReminderSettings build() => const ReminderSettings();

  void setEnabled(bool enabled) => state = state.copyWith(enabled: enabled);

  void setKind(ReminderKind kind, bool on) {
    final kinds = Set<ReminderKind>.of(state.kinds);
    if (on) {
      kinds.add(kind);
    } else {
      kinds.remove(kind);
    }
    state = state.copyWith(kinds: kinds);
  }

  void setQuietHours(QuietHours quietHours) =>
      state = state.copyWith(quietHours: quietHours);
}

final remindersEnabledProvider = Provider<bool>((ref) {
  return ref.watch(reminderSettingsProvider).enabled;
});

final reminderSettingsProvider =
    NotifierProvider<ReminderSettingsController, ReminderSettings>(
      ReminderSettingsController.new,
    );
