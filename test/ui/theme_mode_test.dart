/// themeModeProvider: system default + storage string round-trip (Phase 1).
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/theme/theme_mode_provider.dart';

void main() {
  test('themeMode defaults to system; strings round-trip', () {
    final container = ProviderContainer();
    addTearDown(container.dispose);

    expect(container.read(themeModeProvider), ThemeMode.system);

    expect(themeModeFromStorage('light'), ThemeMode.light);
    expect(themeModeFromStorage('dark'), ThemeMode.dark);
    expect(themeModeFromStorage(null), ThemeMode.system);
    expect(themeModeFromStorage('bogus'), ThemeMode.system);

    // set() persists via secure storage (unavailable in tests → in-memory).
    container.read(themeModeProvider.notifier).set(ThemeMode.dark);
    expect(container.read(themeModeProvider), ThemeMode.dark);
  });
}
