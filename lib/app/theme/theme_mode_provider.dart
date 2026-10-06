/// Persisted theme mode setting (system / light / dark).
///
/// The user-facing picker lives in Settings (Phase 6); this provider only
/// owns the value so `MaterialApp.router` can watch it from day one.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// String round-trip between storage and [ThemeMode].
ThemeMode themeModeFromStorage(String? raw) => switch (raw) {
  'light' => ThemeMode.light,
  'dark' => ThemeMode.dark,
  _ => ThemeMode.system,
};

class ThemeModeController extends Notifier<ThemeMode> {
  static const _key = 'theme_mode';
  final FlutterSecureStorage _storage = const FlutterSecureStorage();

  @override
  ThemeMode build() {
    _restore();
    return ThemeMode.system;
  }

  Future<void> _restore() async {
    try {
      final stored = themeModeFromStorage(await _storage.read(key: _key));
      state = stored;
    } catch (_) {
      // Storage unavailable (tests / platform): keep the system default.
    }
  }

  Future<void> set(ThemeMode mode) async {
    state = mode;
    try {
      await _storage.write(key: _key, value: mode.name);
    } catch (_) {
      // Preference stays in memory for this session.
    }
  }
}

final themeModeProvider = NotifierProvider<ThemeModeController, ThemeMode>(
  ThemeModeController.new,
);
