/// Design tokens for the "Confetti, but grown-up" redesign.
///
/// [AppPalette] is a ThemeExtension: screens read colors via
/// `Theme.of(context).extension<AppPalette>()` (or the `context.colors`
/// helper) — never hardcoded hex. WCAG AA contrast of every text pair is
/// enforced by `test/ui/contrast_test.dart`.
library;

import 'package:flutter/material.dart';

import 'package:ai_birthday/shared/design_system/app_colors.dart';

/// Semantic color roles, light + dark.
class AppPalette extends ThemeExtension<AppPalette> {
  const AppPalette({
    required this.background,
    required this.surface,
    required this.surfaceAlt,
    required this.border,
    required this.textPrimary,
    required this.textSecondary,
    required this.primary,
    required this.onPrimary,
    required this.primaryContainer,
    required this.onPrimaryContainer,
    required this.accent,
    required this.success,
    required this.onSuccess,
    required this.warning,
    required this.onWarning,
    required this.danger,
    required this.onDanger,
    required this.info,
  });

  // Surfaces
  final Color background;
  final Color surface;
  final Color surfaceAlt;
  final Color border;

  // Text
  final Color textPrimary;
  final Color textSecondary;

  // Brand
  final Color primary;
  final Color onPrimary;
  final Color primaryContainer;
  final Color onPrimaryContainer;
  final Color accent;

  // Status (fill + label color as a pair)
  final Color success;
  final Color onSuccess;
  final Color warning;
  final Color onWarning;
  final Color danger;
  final Color onDanger;
  final Color info;

  /// Warm paper light theme.
  static const light = AppPalette(
    background: Color(0xFFFFF9F5),
    surface: Color(0xFFFFFFFF),
    surfaceAlt: Color(0xFFFAF0EA),
    border: Color(0xFFE9DED5),
    textPrimary: Color(0xFF1E1A17),
    textSecondary: Color(0xFF6E645C),
    // Unified on the spec'd terracotta brand (audit 05 P1-1): screens and
    // shared components already paint AppColors.primaryTerracotta everywhere,
    // so the theme derives its brand roles from those constants instead of a
    // second coral palette. AA re-verified: white on #A64B2A ≈5.7:1, #6B2000
    // on #FBECE6 ≈10.5:1, #A64B2A on surfaceAlt ≈5.1:1 (contrast_test enforces).
    primary: AppColors.primaryTerracotta,
    onPrimary: Color(0xFFFFFFFF),
    primaryContainer: AppColors.primaryTerracottaContainer,
    onPrimaryContainer: Color(0xFF6B2000),
    accent: Color(0xFFC77700),
    success: Color(0xFF0F7B54),
    onSuccess: Color(0xFFFFFFFF),
    warning: Color(0xFFA05A00),
    onWarning: Color(0xFFFFFFFF),
    danger: Color(0xFFB3261E),
    onDanger: Color(0xFFFFFFFF),
    info: Color(0xFF1769AA),
  );

  /// Warm charcoal dark theme.
  static const dark = AppPalette(
    background: Color(0xFF14110F),
    surface: Color(0xFF1D1A17),
    surfaceAlt: Color(0xFF27231F),
    border: Color(0xFF3A342F),
    textPrimary: Color(0xFFF4EFEA),
    textSecondary: Color(0xFFADA49B),
    // Bright terracotta variant: ≥4.5:1 on every dark surface (audit 05 P1-1).
    primary: AppColors.primaryTerracottaLight,
    onPrimary: Color(0xFF17110D),
    primaryContainer: Color(0xFF5C2410),
    onPrimaryContainer: Color(0xFFFFDBCE),
    accent: Color(0xFFFFC44D),
    success: Color(0xFF34C88F),
    onSuccess: Color(0xFF06170F),
    warning: Color(0xFFF5B32C),
    onWarning: Color(0xFF1F1503),
    danger: Color(0xFFFFB4AB),
    onDanger: Color(0xFF17110D),
    info: Color(0xFF7FB6E8),
  );

  @override
  AppPalette copyWith({
    Color? background,
    Color? surface,
    Color? surfaceAlt,
    Color? border,
    Color? textPrimary,
    Color? textSecondary,
    Color? primary,
    Color? onPrimary,
    Color? primaryContainer,
    Color? onPrimaryContainer,
    Color? accent,
    Color? success,
    Color? onSuccess,
    Color? warning,
    Color? onWarning,
    Color? danger,
    Color? onDanger,
    Color? info,
  }) {
    return AppPalette(
      background: background ?? this.background,
      surface: surface ?? this.surface,
      surfaceAlt: surfaceAlt ?? this.surfaceAlt,
      border: border ?? this.border,
      textPrimary: textPrimary ?? this.textPrimary,
      textSecondary: textSecondary ?? this.textSecondary,
      primary: primary ?? this.primary,
      onPrimary: onPrimary ?? this.onPrimary,
      primaryContainer: primaryContainer ?? this.primaryContainer,
      onPrimaryContainer: onPrimaryContainer ?? this.onPrimaryContainer,
      accent: accent ?? this.accent,
      success: success ?? this.success,
      onSuccess: onSuccess ?? this.onSuccess,
      warning: warning ?? this.warning,
      onWarning: onWarning ?? this.onWarning,
      danger: danger ?? this.danger,
      onDanger: onDanger ?? this.onDanger,
      info: info ?? this.info,
    );
  }

  @override
  AppPalette lerp(ThemeExtension<AppPalette>? other, double t) {
    if (other is! AppPalette) return this;
    Color lerpC(Color a, Color b) => Color.lerp(a, b, t)!;
    return AppPalette(
      background: lerpC(background, other.background),
      surface: lerpC(surface, other.surface),
      surfaceAlt: lerpC(surfaceAlt, other.surfaceAlt),
      border: lerpC(border, other.border),
      textPrimary: lerpC(textPrimary, other.textPrimary),
      textSecondary: lerpC(textSecondary, other.textSecondary),
      primary: lerpC(primary, other.primary),
      onPrimary: lerpC(onPrimary, other.onPrimary),
      primaryContainer: lerpC(primaryContainer, other.primaryContainer),
      onPrimaryContainer: lerpC(onPrimaryContainer, other.onPrimaryContainer),
      accent: lerpC(accent, other.accent),
      success: lerpC(success, other.success),
      onSuccess: lerpC(onSuccess, other.onSuccess),
      warning: lerpC(warning, other.warning),
      onWarning: lerpC(onWarning, other.onWarning),
      danger: lerpC(danger, other.danger),
      onDanger: lerpC(onDanger, other.onDanger),
      info: lerpC(info, other.info),
    );
  }
}

/// Ergonomic access to the active palette: `context.colors.primary`.
///
/// Resolves the [AppPalette] registered by [AppTheme]; when no extension is
/// present (bare test harnesses, small standalone widgets) it falls back to
/// the brightness-correct built-in palette so screens never crash on color
/// lookup. The app itself always registers the extension via [AppTheme].
extension AppPaletteAccess on BuildContext {
  AppPalette get colors {
    final brightness = Theme.of(this).brightness;
    return Theme.of(this).extension<AppPalette>() ??
        (brightness == Brightness.dark ? AppPalette.dark : AppPalette.light);
  }
}

/// Spacing scale, 4dp base. Use instead of raw SizedBox/EdgeInsets values.
abstract final class AppSpace {
  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 24;
  static const double xxl = 32;
}

/// Corner radii. Pill = full round (chips, FABs).
abstract final class AppRadius {
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double pill = 999;
}

/// Motion durations (Phase 7 wires them into animations).
abstract final class AppDuration {
  static const Duration fast = Duration(milliseconds: 150);
  static const Duration base = Duration(milliseconds: 250);
  static const Duration slow = Duration(milliseconds: 400);
}

/// Standard icon display sizes (informational, never interactive).
abstract final class AppIconSize {
  static const double sm = 16;
  static const double lg = 48;
}

/// Semantic tone roles shared by chips, banners, and status indicators.
///
/// [fill] is the container color, [label] the on-container text color,
/// [ink] a tone-colored icon/accent that sits on `surfaceAlt`.
/// Every fill/label pair is WCAG-checked in `test/ui/contrast_test.dart`.
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

  /// Default banner icon per tone.
  IconData get icon => switch (this) {
    AppTone.neutral => Icons.info_outline,
    AppTone.primary => Icons.celebration_outlined,
    AppTone.info => Icons.info_outline,
    AppTone.success => Icons.check_circle_outline,
    AppTone.warning => Icons.warning_amber_outlined,
    AppTone.danger => Icons.error_outline,
  };
}
