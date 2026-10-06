/// Golden tests for the design-system component library (Phase 2).
///
/// Local-only gate: CI runs `flutter test --exclude-tags golden`.
/// Regenerate with `flutter test --update-goldens test/ui/goldens`.
@Tags(['golden'])
library;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../component_gallery.dart';
import 'app_fonts.dart';

void main() {
  setUpAll(loadAppFonts);

  for (final entry in const <String, Brightness>{
    'light': Brightness.light,
    'dark': Brightness.dark,
  }.entries) {
    testWidgets('design-system components [${entry.key}]', (tester) async {
      tester.view.physicalSize = const Size(400, 2400);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(ComponentGallery(brightness: entry.value));
      await tester.pumpAndSettle();

      await expectLater(
        find.byType(ComponentGallery),
        matchesGoldenFile('design_system_${entry.key}.png'),
      );
    });
  }
}
