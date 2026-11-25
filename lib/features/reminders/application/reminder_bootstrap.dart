import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/core_providers.dart';
import '../../../core/logging/app_logger.dart';
import '../../../app/providers.dart' show birthdaysStreamProvider;
import 'reminder_settings_controller.dart';

/// Keeps device reminders in step with the passage of time (audit A1-002).
///
/// Reminders are one-shot alarms for the next occurrence, so something must
/// re-plan them without the user touching a contact or a setting. This runs a
/// reconcile at start-up and each time the app returns to the foreground; the
/// A cold start and each resume rebuild the plan. A process may have stopped
/// after a contact/settings write but before its reminder replan, and Android
/// can revoke exact-alarm access while the app is in system settings.
class ReminderBootstrap {
  ReminderBootstrap({
    required Future<bool> Function(String reason) reconcile,
    required AppLogger logger,
    this.onResume,
    bool observeLifecycle = true,
  }) {
    _reconcile = reconcile;
    _logger = logger;
    unawaited(_run('startup'));
    if (observeLifecycle) {
      _listener = AppLifecycleListener(
        onResume: () {
          unawaited(_run('resume'));
          onResume?.call();
        },
      );
    }
  }

  late final Future<bool> Function(String reason) _reconcile;
  late final AppLogger _logger;
  final VoidCallback? onResume;
  AppLifecycleListener? _listener;
  bool _disposed = false;

  Future<void> _run(String reason) async {
    if (_disposed) return;
    try {
      await _reconcile(reason);
    } catch (error, stackTrace) {
      _logger.warning(
        'reminders',
        'Reminder reconcile threw unexpectedly.',
        params: {'reason': reason},
        error: error,
        stackTrace: stackTrace,
      );
    }
  }

  void dispose() {
    _disposed = true;
    _listener?.dispose();
    _listener = null;
  }
}

/// Watched once at app start (see `AiBirthdayApp`). Inert outside a real
/// Flutter binding so widget tests never reach the native scheduler.
final reminderBootstrapProvider = Provider<ReminderBootstrap?>((ref) {
  if (WidgetsBinding.instance is! WidgetsFlutterBinding) return null;
  final notifier = ref.read(reminderSettingsProvider.notifier);
  final bootstrap = ReminderBootstrap(
    reconcile: (reason) => notifier.replan(reason: reason, onlyIfStale: false),
    logger: ref.read(loggerProvider),
    onResume: () => ref.invalidate(birthdaysStreamProvider),
  );
  ref.onDispose(bootstrap.dispose);
  return bootstrap;
});
