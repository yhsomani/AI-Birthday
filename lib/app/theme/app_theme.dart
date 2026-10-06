/// Material 3 theme for AI-Birthday — "Confetti, but grown-up".
///
/// All colors come from [AppPalette] (design tokens), typography from the
/// bundled fonts (Bricolage Grotesque display, Outfit body) — no network
/// font fetching. Touch targets are strictly 48dp+.
library;

import 'package:flutter/material.dart';

import 'package:ai_birthday/ui/design_system/app_tokens.dart';

class AppTheme {
  const AppTheme._();

  /// Bundled display font (headlines, app bar titles).
  static const String displayFont = 'BricolageGrotesque';

  /// Bundled body font.
  static const String bodyFont = 'Outfit';

  /// Material 3 Light Theme
  static ThemeData get light => _buildTheme(Brightness.light);

  /// Material 3 Dark Theme
  static ThemeData get dark => _buildTheme(Brightness.dark);

  static ThemeData _buildTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;
    final palette = isDark ? AppPalette.dark : AppPalette.light;

    final colorScheme = ColorScheme.fromSeed(
      seedColor: palette.primary,
      brightness: brightness,
      primary: palette.primary,
      onPrimary: palette.onPrimary,
      primaryContainer: palette.primaryContainer,
      onPrimaryContainer: palette.onPrimaryContainer,
      secondary: palette.accent,
      surface: palette.surface,
      onSurface: palette.textPrimary,
      outline: palette.border,
      error: palette.danger,
      onError: palette.onDanger,
    );

    TextStyle display(TextStyle? base, {FontWeight weight = FontWeight.w700}) =>
        (base ?? const TextStyle()).copyWith(
          fontFamily: displayFont,
          fontWeight: weight,
          letterSpacing: -0.5,
        );

    final baseTextTheme =
        (isDark ? ThemeData.dark() : ThemeData.light()).textTheme;
    final textTheme = baseTextTheme
        .apply(fontFamily: bodyFont)
        .copyWith(
          displayLarge: display(baseTextTheme.displayLarge),
          displayMedium: display(baseTextTheme.displayMedium),
          displaySmall: display(baseTextTheme.displaySmall),
          headlineLarge: display(baseTextTheme.headlineLarge),
          headlineMedium: display(baseTextTheme.headlineMedium),
          headlineSmall: display(baseTextTheme.headlineSmall),
          titleLarge: display(
            baseTextTheme.titleLarge,
          ).copyWith(letterSpacing: -0.2),
          titleMedium: baseTextTheme.titleMedium?.copyWith(
            fontFamily: displayFont,
            fontWeight: FontWeight.w600,
            letterSpacing: -0.2,
          ),
        );

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: palette.background,
      textTheme: textTheme,
      extensions: [palette],
      appBarTheme: AppBarTheme(
        backgroundColor: palette.background,
        foregroundColor: palette.textPrimary,
        elevation: 0,
        scrolledUnderElevation: 1,
        centerTitle: false,
        titleTextStyle: textTheme.titleLarge?.copyWith(
          fontFamily: displayFont,
          color: palette.textPrimary,
        ),
      ),
      cardTheme: CardThemeData(
        color: palette.surface,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.md),
          side: BorderSide(color: palette.border, width: 1),
        ),
        margin: EdgeInsets.zero,
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(
            64,
            48,
          ), // Strict 48dp accessible touch target
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpace.lg,
            vertical: AppSpace.md,
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AppRadius.sm),
          ),
          textStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(64, 48),
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpace.lg,
            vertical: AppSpace.md,
          ),
          side: BorderSide(color: palette.border, width: 1.2),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AppRadius.sm),
          ),
          textStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          minimumSize: const Size(48, 48),
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpace.lg,
            vertical: AppSpace.md,
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AppRadius.sm),
          ),
        ),
      ),
      iconButtonTheme: IconButtonThemeData(
        style: IconButton.styleFrom(minimumSize: const Size(48, 48)),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: palette.surfaceAlt,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: AppSpace.lg,
          vertical: 14,
        ),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.sm),
          borderSide: BorderSide(color: palette.border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.sm),
          borderSide: BorderSide(color: palette.border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.sm),
          borderSide: BorderSide(color: palette.primary, width: 2),
        ),
      ),
      // Bottom navigation shell: height is set at the widget site
      // (AppScaffold) so it grows with text scale — the Phase 0 audit flagged
      // the old fixed 68dp as a 200%-scale clipping risk. Selected/unselected
      // states use token colors only.
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: palette.surface,
        elevation: 0,
        indicatorColor: palette.primaryContainer,
        labelTextStyle: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return textTheme.labelSmall?.copyWith(
            fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
            color: selected ? palette.textPrimary : palette.textSecondary,
          );
        }),
        iconTheme: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return IconThemeData(
            color: selected ? palette.primary : palette.textSecondary,
          );
        }),
        labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
      ),
      dividerTheme: DividerThemeData(
        color: palette.border,
        thickness: 1,
        space: AppSpace.xl,
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.sm),
        ),
      ),
    );
  }
}
