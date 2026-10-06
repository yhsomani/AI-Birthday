/// Shared font loading for golden tests.
///
/// flutter_test does not load the app's pubspec fonts; load them explicitly
/// so goldens render the bundled Outfit / Bricolage Grotesque deterministically.
library;

import 'dart:io';

import 'package:flutter/services.dart';

Future<void> loadAppFonts() async {
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
