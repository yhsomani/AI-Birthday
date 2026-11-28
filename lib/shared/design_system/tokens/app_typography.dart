import 'package:flutter/material.dart';

import 'app_palette.dart';

/// Bundled typography hierarchy for "Editorial Warmth".
///
/// Uses local bundled fonts (`BricolageGrotesque` display/headings and `Outfit`
/// body/labels) — zero network font fetching.
abstract final class AppTypography {
  static const String displayFont = 'BricolageGrotesque';
  static const String bodyFont = 'Outfit';

  static TextTheme buildTextTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;
    final palette = isDark ? AppPalette.dark : AppPalette.light;
    final baseTextTheme =
        (isDark ? ThemeData.dark() : ThemeData.light()).textTheme;

    TextStyle displayStyle(
      TextStyle? base, {
      double? fontSize,
      double? heightRatio,
      double letterSpacing = -0.5,
      FontWeight fontWeight = FontWeight.w700,
    }) => (base ?? const TextStyle()).copyWith(
      fontFamily: displayFont,
      fontWeight: fontWeight,
      fontSize: fontSize,
      height: heightRatio,
      letterSpacing: letterSpacing,
      color: palette.textPrimary,
    );

    TextStyle bodyStyle(
      TextStyle? base, {
      double? fontSize,
      double? heightRatio,
      double letterSpacing = 0.0,
      FontWeight fontWeight = FontWeight.w400,
      Color? color,
    }) => (base ?? const TextStyle()).copyWith(
      fontFamily: bodyFont,
      fontWeight: fontWeight,
      fontSize: fontSize,
      height: heightRatio,
      letterSpacing: letterSpacing,
      color: color ?? palette.textPrimary,
    );

    return baseTextTheme
        .apply(
          fontFamily: bodyFont,
          bodyColor: palette.textPrimary,
          displayColor: palette.textPrimary,
        )
        .copyWith(
          displayLarge: displayStyle(
            baseTextTheme.displayLarge,
            fontSize: 32,
            heightRatio: 40 / 32,
            letterSpacing: -0.5,
          ),
          displayMedium: displayStyle(baseTextTheme.displayMedium),
          displaySmall: displayStyle(baseTextTheme.displaySmall),
          headlineLarge: displayStyle(
            baseTextTheme.headlineLarge,
            fontSize: 32,
            heightRatio: 40 / 32,
            letterSpacing: -0.5,
          ),
          headlineMedium: displayStyle(
            baseTextTheme.headlineMedium,
            fontSize: 24,
            heightRatio: 32 / 24,
            letterSpacing: -0.3,
          ),
          headlineSmall: displayStyle(
            baseTextTheme.headlineSmall,
            fontSize: 20,
            heightRatio: 28 / 20,
            letterSpacing: -0.2,
          ),
          titleLarge: displayStyle(
            baseTextTheme.titleLarge,
            fontSize: 18,
            heightRatio: 24 / 18,
            letterSpacing: 0.0,
          ),
          titleMedium: bodyStyle(
            baseTextTheme.titleMedium,
            fontSize: 16,
            heightRatio: 22 / 16,
            letterSpacing: 0.1,
            fontWeight: FontWeight.w600,
          ),
          bodyLarge: bodyStyle(
            baseTextTheme.bodyLarge,
            fontSize: 16,
            heightRatio: 24 / 16,
          ),
          bodyMedium: bodyStyle(
            baseTextTheme.bodyMedium,
            fontSize: 14,
            heightRatio: 20 / 14,
          ),
          labelMedium: bodyStyle(
            baseTextTheme.labelMedium,
            fontSize: 12,
            heightRatio: 16 / 12,
            letterSpacing: 0.5,
            fontWeight: FontWeight.w600,
          ),
          labelSmall: bodyStyle(
            baseTextTheme.labelSmall,
            fontSize: 11,
            heightRatio: 14 / 11,
            letterSpacing: 0.4,
            fontWeight: FontWeight.w500,
          ),
        );
  }
}
