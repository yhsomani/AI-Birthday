import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import 'app_colors.dart';

/// Centralized editorial typography scale for AI-Birthday (SSOT §3, §22).
///
/// Pairs a warm editorial sans-serif (Outfit) for headlines with a readable,
/// humanistic sans-sans-serif (Nunito) for body and UI elements.
class AppTypography {
  const AppTypography._();

  static TextStyle headlineXl({Color? color}) {
    try {
      return GoogleFonts.outfit(
        fontSize: 32,
        fontWeight: FontWeight.w700,
        height: 1.25,
        letterSpacing: -0.5,
        color: color,
      );
    } catch (_) {
      return TextStyle(
        fontFamily: 'sans-serif',
        fontSize: 32,
        fontWeight: FontWeight.w700,
        height: 1.25,
        letterSpacing: -0.5,
        color: color,
      );
    }
  }

  static TextStyle headlineLg({Color? color}) {
    try {
      return GoogleFonts.outfit(
        fontSize: 24,
        fontWeight: FontWeight.w700,
        height: 1.33,
        letterSpacing: -0.3,
        color: color,
      );
    } catch (_) {
      return TextStyle(
        fontFamily: 'sans-serif',
        fontSize: 24,
        fontWeight: FontWeight.w700,
        height: 1.33,
        letterSpacing: -0.3,
        color: color,
      );
    }
  }

  static TextStyle headlineMd({Color? color}) {
    try {
      return GoogleFonts.outfit(
        fontSize: 20,
        fontWeight: FontWeight.w600,
        height: 1.4,
        letterSpacing: -0.2,
        color: color,
      );
    } catch (_) {
      return TextStyle(
        fontFamily: 'sans-serif',
        fontSize: 20,
        fontWeight: FontWeight.w600,
        height: 1.4,
        letterSpacing: -0.2,
        color: color,
      );
    }
  }

  static TextStyle titleLg({
    Color? color,
    FontWeight weight = FontWeight.w700,
  }) {
    return _nunito(
      fontSize: 18,
      fontWeight: weight,
      height: 1.33,
      letterSpacing: 0.0,
      color: color,
    );
  }

  static TextStyle titleMd({
    Color? color,
    FontWeight weight = FontWeight.w600,
  }) {
    return _nunito(
      fontSize: 16,
      fontWeight: weight,
      height: 1.375,
      letterSpacing: 0.1,
      color: color,
    );
  }

  static TextStyle bodyLg({Color? color, FontWeight weight = FontWeight.w400}) {
    return _nunito(
      fontSize: 16,
      fontWeight: weight,
      height: 1.5,
      letterSpacing: 0.0,
      color: color,
    );
  }

  static TextStyle bodyMd({Color? color, FontWeight weight = FontWeight.w400}) {
    return _nunito(
      fontSize: 14,
      fontWeight: weight,
      height: 1.43,
      letterSpacing: 0.0,
      color: color,
    );
  }

  static TextStyle labelMd({
    Color? color,
    FontWeight weight = FontWeight.w600,
    bool uppercase = true,
  }) {
    final style = _nunito(
      fontSize: 12,
      fontWeight: weight,
      height: 1.33,
      letterSpacing: uppercase ? 0.8 : 0.2,
      color: color ?? AppColors.primaryTerracotta,
    );
    return style;
  }

  static TextStyle labelSm({
    Color? color,
    FontWeight weight = FontWeight.w500,
  }) {
    return _nunito(
      fontSize: 11,
      fontWeight: weight,
      height: 1.27,
      letterSpacing: 0.4,
      color: color,
    );
  }

  static TextStyle _nunito({
    required double fontSize,
    required FontWeight fontWeight,
    required double height,
    required double letterSpacing,
    Color? color,
  }) {
    try {
      return GoogleFonts.outfit(
        fontSize: fontSize,
        fontWeight: fontWeight,
        height: height,
        letterSpacing: letterSpacing,
        color: color,
      );
    } catch (_) {
      return TextStyle(
        fontSize: fontSize,
        fontWeight: fontWeight,
        height: height,
        letterSpacing: letterSpacing,
        color: color,
      );
    }
  }
}
