import 'package:flutter/material.dart';

import 'app_palette.dart';

/// Semantic tone roles shared by chips, banners, and status indicators.
enum AppTone { neutral, primary, info, success, warning, danger }

extension AppToneColors on AppTone {
  Color fill(AppPalette p) => switch (this) {
    AppTone.neutral => p.surfaceAlt,
    AppTone.primary => p.primaryContainer,
    AppTone.info => p.surfaceAlt,
    AppTone.success => p.success,
    AppTone.warning => p.warning,
    AppTone.danger => p.danger,
  };

  Color label(AppPalette p) => switch (this) {
    AppTone.neutral => p.textSecondary,
    AppTone.primary => p.onPrimaryContainer,
    AppTone.info => p.info,
    AppTone.success => p.onSuccess,
    AppTone.warning => p.onWarning,
    AppTone.danger => p.onDanger,
  };

  Color ink(AppPalette p) => switch (this) {
    AppTone.neutral => p.textSecondary,
    AppTone.primary => p.primary,
    AppTone.info => p.info,
    AppTone.success => p.success,
    AppTone.warning => p.warning,
    AppTone.danger => p.danger,
  };

  IconData get icon => switch (this) {
    AppTone.neutral => Icons.info_outline,
    AppTone.primary => Icons.celebration_outlined,
    AppTone.info => Icons.info_outline,
    AppTone.success => Icons.check_circle_outline,
    AppTone.warning => Icons.warning_amber_outlined,
    AppTone.danger => Icons.error_outline,
  };
}
