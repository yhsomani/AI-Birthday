/// Golden of the bottom-navigation shell chrome (Phase 3), light + dark.
///
/// Local-only gate: CI runs `flutter test --exclude-tags golden`.
/// Regenerate with `flutter test --update-goldens test/app`.
@Tags(['golden'])
library;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../ui/goldens/app_fonts.dart';
import 'pump_app.dart';

void main() {
  setUpAll(loadAppFonts);

  for (final brightness in const <Brightness>[
    Brightness.light,
    Brightness.dark,
  ]) {
    final name = brightness == Brightness.light ? 'light' : 'dark';
    testWidgets('bottom navigation bar [$name]', (tester) async {
      tester.platformDispatcher.platformBrightnessTestValue = brightness;
      addTearDown(tester.platformDispatcher.clearPlatformBrightnessTestValue);

      await pumpCompletedApp(tester, size: const Size(400, 900));

      await expectLater(
        find.byType(NavigationBar),
        matchesGoldenFile('shell_nav_$name.png'),
      );

      await drainApp(tester);
    });
  }
}
