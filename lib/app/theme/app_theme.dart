/// Authoritative Material 3 Design System for AI-Birthday (SSOT §3, §22).
///
/// Eliminates generic AI/SaaS templates (no arbitrary gradients, no bubbly pill
/// badges, no repetitive card soup). Uses a warm, distinctive editorial aesthetic:
/// warm terracotta and vintage amber tones on linen surfaces, robust serif headlines,
/// crisp structural borders, and strict 48dp+ accessible touch targets.
library;

import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../shared/design_system/app_colors.dart';

class AppTheme {
  const AppTheme._();

  // Distinctive celebratory palette (warm terracotta, vintage amber, forest green accents)
  static const Color primaryTerracotta = AppColors.primaryTerracotta;
  static const Color primaryTerracottaDark = AppColors.primaryTerracottaLight;
  static const Color accentAmber = AppColors.accentAmber;
  static const Color accentForest = AppColors.accentForest;

  // Surface colors - Light (Linen / Editorial)
  static const Color lightBackground = AppColors.lightBackground;
  static const Color lightSurface = AppColors.lightSurface;
  static const Color lightSurfaceVariant = AppColors.lightSurfaceVariant;
  static const Color lightBorder = AppColors.lightBorder;

  // Surface colors - Dark (Warm Charcoal / Espresso)
  static const Color darkBackground = AppColors.darkBackground;
  static const Color darkSurface = AppColors.darkSurface;
  static const Color darkSurfaceVariant = AppColors.darkSurfaceVariant;
  static const Color darkBorder = AppColors.darkBorder;

  /// Material 3 Light Theme
  static ThemeData get light => _buildTheme(Brightness.light);

  /// Material 3 Dark Theme
  static ThemeData get dark => _buildTheme(Brightness.dark);

  // Backward compatibility with method syntax
  static ThemeData lightTheme() => light;
  static ThemeData darkTheme() => dark;

  static ThemeData _buildTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;

    final primary = isDark ? primaryTerracottaDark : primaryTerracotta;
    final background = isDark ? darkBackground : lightBackground;
    final surface = isDark ? darkSurface : lightSurface;
    final surfaceVariant = isDark ? darkSurfaceVariant : lightSurfaceVariant;
    final border = isDark ? darkBorder : lightBorder;

    final colorScheme = ColorScheme.fromSeed(
      seedColor: primaryTerracotta,
      brightness: brightness,
      primary: primary,
      secondary: accentAmber,
      tertiary: accentForest,
      surface: surface,
    );

    // Typography: Editorial serif headings with clean humanistic body
    TextTheme baseTextTheme;
    try {
      baseTextTheme = GoogleFonts.nunitoTextTheme(
        isDark ? ThemeData.dark().textTheme : ThemeData.light().textTheme,
      );
    } catch (_) {
      baseTextTheme = isDark
          ? ThemeData.dark().textTheme
          : ThemeData.light().textTheme;
    }

    TextStyle headlineStyle(TextStyle? fallback) {
      try {
        return GoogleFonts.playfairDisplay(
          textStyle: fallback,
          fontWeight: FontWeight.w700,
          letterSpacing: -0.5,
        );
      } catch (_) {
        return (fallback ?? const TextStyle()).copyWith(
          fontFamily: 'serif',
          fontWeight: FontWeight.w700,
          letterSpacing: -0.5,
        );
      }
    }

    final textTheme = baseTextTheme.copyWith(
      displayLarge: headlineStyle(baseTextTheme.displayLarge),
      displayMedium: headlineStyle(baseTextTheme.displayMedium),
      displaySmall: headlineStyle(baseTextTheme.displaySmall),
      headlineLarge: headlineStyle(baseTextTheme.headlineLarge),
      headlineMedium: headlineStyle(baseTextTheme.headlineMedium),
      headlineSmall: headlineStyle(baseTextTheme.headlineSmall),
      titleLarge: baseTextTheme.titleLarge?.copyWith(
        fontWeight: FontWeight.w700,
        letterSpacing: -0.2,
      ),
      titleMedium: baseTextTheme.titleMedium?.copyWith(
        fontWeight: FontWeight.w600,
      ),
      titleSmall: baseTextTheme.titleSmall?.copyWith(
        fontWeight: FontWeight.w600,
        letterSpacing: 0.5,
      ),
    );

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: background,
      textTheme: textTheme,
      appBarTheme: AppBarTheme(
        backgroundColor: background,
        foregroundColor: isDark ? Colors.white : const Color(0xFF1C1917),
        elevation: 0,
        scrolledUnderElevation: 1,
        centerTitle: false,
        titleTextStyle: textTheme.titleLarge?.copyWith(
          color: isDark ? Colors.white : const Color(0xFF1C1917),
        ),
      ),
      cardTheme: CardThemeData(
        color: surface,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: BorderSide(color: border, width: 1),
        ),
        margin: EdgeInsets.zero,
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(
            64,
            48,
          ), // Strict 48dp accessible touch target
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
          textStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(64, 48),
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
          side: BorderSide(color: border, width: 1.2),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
          textStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          minimumSize: const Size(48, 48),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        ),
      ),
      iconButtonTheme: IconButtonThemeData(
        style: IconButton.styleFrom(minimumSize: const Size(48, 48)),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surfaceVariant,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 14,
        ),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: BorderSide(color: border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: BorderSide(color: border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: BorderSide(color: primary, width: 2),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        height: 68,
        backgroundColor: surface,
        elevation: 0,
        indicatorColor: primary.withValues(alpha: 0.15),
        labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
      ),
      dividerTheme: DividerThemeData(color: border, thickness: 1, space: 24),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }
}
