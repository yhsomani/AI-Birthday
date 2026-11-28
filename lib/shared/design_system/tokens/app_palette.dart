import 'package:flutter/material.dart';

/// Semantic color roles for the "Editorial Warmth" design system, light + dark.
///
/// Screens and shared components read colors via `context.colors`
/// (`Theme.of(context).extension<AppPalette>()`) — never raw `Colors.*` or
/// hardcoded hex literals. WCAG AA contrast of every pair is verified in
/// `test/ui/contrast_test.dart` and `test/ui/contrast_app_colors_test.dart`.
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
    required this.warningContainer,
    required this.onWarningContainer,
    required this.danger,
    required this.onDanger,
    required this.dangerContainer,
    required this.onDangerContainer,
    required this.info,
    required this.shadow,
    required this.scrim,
    required this.skeletonBase,
    required this.skeletonHighlight,
    required this.whatsappGreen,
    required this.onWhatsappGreen,
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

  // Status (fill + label color pairs)
  final Color success;
  final Color onSuccess;
  final Color warning;
  final Color onWarning;
  final Color warningContainer;
  final Color onWarningContainer;
  final Color danger;
  final Color onDanger;
  final Color dangerContainer;
  final Color onDangerContainer;
  final Color info;

  // Elevation, scrims, loading skeletons & channel accents
  final Color shadow;
  final Color scrim;
  final Color skeletonBase;
  final Color skeletonHighlight;
  final Color whatsappGreen;
  final Color onWhatsappGreen;

  /// Warm linen paper light theme.
  static const light = AppPalette(
    background: Color(0xFFFAF7F2),
    surface: Color(0xFFFFFFFF),
    surfaceAlt: Color(0xFFF2ECE4),
    border: Color(0xFFE5DDD3),
    textPrimary: Color(0xFF1C1917),
    textSecondary: Color(0xFF6A635E),
    primary: Color(0xFFA64B2A),
    onPrimary: Color(0xFFFFFFFF),
    primaryContainer: Color(0xFFFBECE6),
    onPrimaryContainer: Color(0xFF6B2000),
    accent: Color(0xFFC77700),
    success: Color(0xFF0F7B54),
    onSuccess: Color(0xFFFFFFFF),
    warning: Color(0xFFA05A00),
    onWarning: Color(0xFFFFFFFF),
    warningContainer: Color(0xFFFEF3E7),
    onWarningContainer: Color(0xFFA35700),
    danger: Color(0xFFB3261E),
    onDanger: Color(0xFFFFFFFF),
    dangerContainer: Color(0xFFFFDAD6),
    onDangerContainer: Color(0xFF410002),
    info: Color(0xFF1769AA),
    shadow: Color(0xFF000000),
    scrim: Color(0xFF000000),
    skeletonBase: Color(0xFFEDE4DC),
    skeletonHighlight: Color(0xFFF8F2EC),
    whatsappGreen: Color(0xFF075E54),
    onWhatsappGreen: Color(0xFFFFFFFF),
  );

  /// Warm espresso dark theme.
  static const dark = AppPalette(
    background: Color(0xFF14110F),
    surface: Color(0xFF1D1A17),
    surfaceAlt: Color(0xFF27231F),
    border: Color(0xFF3A342F),
    textPrimary: Color(0xFFF4EFEA),
    textSecondary: Color(0xFFADA49B),
    primary: Color(0xFFE28464),
    onPrimary: Color(0xFF17110D),
    primaryContainer: Color(0xFF5C2410),
    onPrimaryContainer: Color(0xFFFFDBCE),
    accent: Color(0xFFFFC44D),
    success: Color(0xFF34C88F),
    onSuccess: Color(0xFF06170F),
    warning: Color(0xFFF5B32C),
    onWarning: Color(0xFF1F1503),
    warningContainer: Color(0xFF422C08),
    onWarningContainer: Color(0xFFF0A65B),
    danger: Color(0xFFFFB4AB),
    onDanger: Color(0xFF17110D),
    dangerContainer: Color(0xFF690005),
    onDangerContainer: Color(0xFFFFDAD6),
    info: Color(0xFF7FB6E8),
    shadow: Color(0xFF000000),
    scrim: Color(0xFF000000),
    skeletonBase: Color(0xFF2B2622),
    skeletonHighlight: Color(0xFF3A332E),
    whatsappGreen: Color(0xFF075E54),
    onWhatsappGreen: Color(0xFFFFFFFF),
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
    Color? warningContainer,
    Color? onWarningContainer,
    Color? danger,
    Color? onDanger,
    Color? dangerContainer,
    Color? onDangerContainer,
    Color? info,
    Color? shadow,
    Color? scrim,
    Color? skeletonBase,
    Color? skeletonHighlight,
    Color? whatsappGreen,
    Color? onWhatsappGreen,
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
      warningContainer: warningContainer ?? this.warningContainer,
      onWarningContainer: onWarningContainer ?? this.onWarningContainer,
      danger: danger ?? this.danger,
      onDanger: onDanger ?? this.onDanger,
      dangerContainer: dangerContainer ?? this.dangerContainer,
      onDangerContainer: onDangerContainer ?? this.onDangerContainer,
      info: info ?? this.info,
      shadow: shadow ?? this.shadow,
      scrim: scrim ?? this.scrim,
      skeletonBase: skeletonBase ?? this.skeletonBase,
      skeletonHighlight: skeletonHighlight ?? this.skeletonHighlight,
      whatsappGreen: whatsappGreen ?? this.whatsappGreen,
      onWhatsappGreen: onWhatsappGreen ?? this.onWhatsappGreen,
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
      warningContainer: lerpC(warningContainer, other.warningContainer),
      onWarningContainer: lerpC(onWarningContainer, other.onWarningContainer),
      danger: lerpC(danger, other.danger),
      onDanger: lerpC(onDanger, other.onDanger),
      dangerContainer: lerpC(dangerContainer, other.dangerContainer),
      onDangerContainer: lerpC(onDangerContainer, other.onDangerContainer),
      info: lerpC(info, other.info),
      shadow: lerpC(shadow, other.shadow),
      scrim: lerpC(scrim, other.scrim),
      skeletonBase: lerpC(skeletonBase, other.skeletonBase),
      skeletonHighlight: lerpC(skeletonHighlight, other.skeletonHighlight),
      whatsappGreen: lerpC(whatsappGreen, other.whatsappGreen),
      onWhatsappGreen: lerpC(onWhatsappGreen, other.onWhatsappGreen),
    );
  }
}

/// Ergonomic access to the active palette: `context.colors.primary`.
///
/// Resolves the [AppPalette] registered by [AppTheme]; when no extension is
/// present (bare test harnesses, small standalone widgets) it falls back to
/// the brightness-correct built-in palette so screens never crash on color
/// lookup.
extension AppPaletteAccess on BuildContext {
  AppPalette get colors {
    final brightness = Theme.of(this).brightness;
    return Theme.of(this).extension<AppPalette>() ??
        (brightness == Brightness.dark ? AppPalette.dark : AppPalette.light);
  }
}
