/// Material 3 celebratory theme for AI-Birthday (SSOT §3, §22).
library;

import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class AppTheme {
  const AppTheme._();

  // Primary celebration palette
  static const Color primaryCoral = Color(0xFFE03E5D);
  static const Color primaryCoralDark = Color(0xFFFF6B88);
  static const Color secondaryGold = Color(0xFFF59E0B);
  static const Color tertiaryTeal = Color(0xFF10B981);

  // Neutral surfaces - Light
  static const Color lightBackground = Color(0xFFFDFBF7);
  static const Color lightSurface = Color(0xFFFFFFFF);
  static const Color lightSurfaceVariant = Color(0xFFF3EFEA);
  static const Color lightText = Color(0xFF1C1917);
  static const Color lightTextMuted = Color(0xFF57534E);
  static const Color lightBorder = Color(0xFFE7E2DA);

  // Neutral surfaces - Dark
  static const Color darkBackground = Color(0xFF141216);
  static const Color darkSurface = Color(0xFF1E1A22);
  static const Color darkSurfaceVariant = Color(0xFF2C2632);
  static const Color darkText = Color(0xFFFAFAF9);
  static const Color darkTextMuted = Color(0xFFA8A29E);
  static const Color darkBorder = Color(0xFF383140);

  static TextTheme _buildTextTheme(
    TextTheme base,
    Color textColor,
    Color mutedColor,
  ) {
    final displayFont = GoogleFonts.playfairDisplayTextTheme(base);
    final bodyFont = GoogleFonts.nunitoTextTheme(base);

    return displayFont.copyWith(
      displayLarge: displayFont.displayLarge?.copyWith(
        color: textColor,
        fontWeight: FontWeight.bold,
        height: 1.1,
      ),
      displayMedium: displayFont.displayMedium?.copyWith(
        color: textColor,
        fontWeight: FontWeight.bold,
        height: 1.2,
      ),
      displaySmall: displayFont.displaySmall?.copyWith(
        color: textColor,
        fontWeight: FontWeight.bold,
        height: 1.2,
      ),
      headlineLarge: displayFont.headlineLarge?.copyWith(
        color: textColor,
        fontWeight: FontWeight.w700,
        height: 1.2,
      ),
      headlineMedium: displayFont.headlineMedium?.copyWith(
        color: textColor,
        fontWeight: FontWeight.w700,
        height: 1.2,
      ),
      headlineSmall: displayFont.headlineSmall?.copyWith(
        color: textColor,
        fontWeight: FontWeight.w700,
        height: 1.3,
      ),
      titleLarge: displayFont.titleLarge?.copyWith(
        color: textColor,
        fontWeight: FontWeight.w600,
        height: 1.3,
      ),
      titleMedium: displayFont.titleMedium?.copyWith(
        color: textColor,
        fontWeight: FontWeight.w600,
        height: 1.4,
      ),
      titleSmall: displayFont.titleSmall?.copyWith(
        color: textColor,
        fontWeight: FontWeight.w600,
        height: 1.4,
      ),
      bodyLarge: bodyFont.bodyLarge?.copyWith(
        color: textColor,
        fontSize: 16,
        height: 1.5,
      ),
      bodyMedium: bodyFont.bodyMedium?.copyWith(
        color: textColor,
        fontSize: 14,
        height: 1.5,
      ),
      bodySmall: bodyFont.bodySmall?.copyWith(
        color: mutedColor,
        fontSize: 12,
        height: 1.5,
      ),
      labelLarge: bodyFont.labelLarge?.copyWith(
        color: textColor,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.5,
      ),
      labelMedium: bodyFont.labelMedium?.copyWith(
        color: mutedColor,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.5,
      ),
      labelSmall: bodyFont.labelSmall?.copyWith(
        color: mutedColor,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.5,
      ),
    );
  }

  /// Material 3 Light Theme
  static ThemeData get light {
    final colorScheme = ColorScheme.fromSeed(
      seedColor: primaryCoral,
      primary: primaryCoral,
      secondary: secondaryGold,
      tertiary: tertiaryTeal,
      surface: lightSurface,
      surfaceContainerHighest: lightSurfaceVariant,
      brightness: Brightness.light,
    );

    final baseTheme = ThemeData(brightness: Brightness.light);

    return ThemeData(
      useMaterial3: true,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: lightBackground,
      textTheme: _buildTextTheme(
        baseTheme.textTheme,
        lightText,
        lightTextMuted,
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: lightBackground,
        elevation: 0,
        centerTitle: false,
        iconTheme: const IconThemeData(color: lightText),
        titleTextStyle: GoogleFonts.playfairDisplay(
          color: lightText,
          fontSize: 22,
          fontWeight: FontWeight.w700,
        ),
      ),
      cardTheme: CardThemeData(
        color: lightSurface,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: lightBorder, width: 1),
        ),
      ),
      chipTheme: ChipThemeData(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        side: BorderSide.none,
        backgroundColor: lightSurfaceVariant,
        labelStyle: GoogleFonts.nunito(
          color: lightText,
          fontWeight: FontWeight.w600,
          fontSize: 13,
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(88, 48), // Accessible touch target
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          elevation: 0,
          textStyle: GoogleFonts.nunito(
            fontWeight: FontWeight.bold,
            fontSize: 15,
          ),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          minimumSize: const Size(88, 48),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          elevation: 0,
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: lightSurfaceVariant,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 16,
        ),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: lightBorder),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: primaryCoral, width: 2),
        ),
        labelStyle: GoogleFonts.nunito(color: lightTextMuted),
      ),
    );
  }

  /// Material 3 Dark Theme
  static ThemeData get dark {
    final colorScheme = ColorScheme.fromSeed(
      seedColor: primaryCoral,
      primary: primaryCoralDark,
      secondary: secondaryGold,
      tertiary: tertiaryTeal,
      surface: darkSurface,
      surfaceContainerHighest: darkSurfaceVariant,
      brightness: Brightness.dark,
    );

    final baseTheme = ThemeData(brightness: Brightness.dark);

    return ThemeData(
      useMaterial3: true,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: darkBackground,
      textTheme: _buildTextTheme(baseTheme.textTheme, darkText, darkTextMuted),
      appBarTheme: AppBarTheme(
        backgroundColor: darkBackground,
        elevation: 0,
        centerTitle: false,
        iconTheme: const IconThemeData(color: darkText),
        titleTextStyle: GoogleFonts.playfairDisplay(
          color: darkText,
          fontSize: 22,
          fontWeight: FontWeight.w700,
        ),
      ),
      cardTheme: CardThemeData(
        color: darkSurface,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: darkBorder, width: 1),
        ),
      ),
      chipTheme: ChipThemeData(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        side: BorderSide.none,
        backgroundColor: darkSurfaceVariant,
        labelStyle: GoogleFonts.nunito(
          color: darkText,
          fontWeight: FontWeight.w600,
          fontSize: 13,
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(88, 48), // Accessible touch target
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          elevation: 0,
          textStyle: GoogleFonts.nunito(
            fontWeight: FontWeight.bold,
            fontSize: 15,
          ),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          minimumSize: const Size(88, 48),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          elevation: 0,
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: darkSurfaceVariant,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 16,
        ),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: darkBorder),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: primaryCoralDark, width: 2),
        ),
        labelStyle: GoogleFonts.nunito(color: darkTextMuted),
      ),
    );
  }
}
