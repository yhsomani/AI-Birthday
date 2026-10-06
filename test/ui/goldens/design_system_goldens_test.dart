/// Golden tests for the design-system component library (Phase 2).
///
/// Local-only gate: CI runs `flutter test --exclude-tags golden`.
/// Regenerate with `flutter test --update-goldens test/ui/goldens`.
@Tags(['golden'])
library;

import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import '../component_gallery.dart';

/// flutter_test does not load the app's pubspec fonts; load them explicitly
/// so goldens render the bundled Outfit / Bricolage Grotesque, deterministically.
Future<void> _loadAppFonts() async {
  const fonts = <String, String>{
    'BricolageGrotesque': 'assets/fonts/BricolageGrotesque-VariableFont.ttf',
    'Outfit': 'assets/fonts/Outfit-VariableFont_wght.ttf',
  };
  for (final entry in fonts.entries) {
    final bytes = File(entry.value).readAsBytesSync();
    final loader = FontLoader(entry.key);
    loader.addFont(
      Future<ByteData>.value(
        ByteData.view(bytes.buffer, bytes.offsetInBytes, bytes.lengthInBytes),
      ),
    );
    await loader.load();
  }
}

void main() {
  setUpAll(_loadAppFonts);

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
