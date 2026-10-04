import 'package:flutter/material.dart';

/// Centralized spacing, elevation, and shape tokens for AI-Birthday.
class AppSpacing {
  const AppSpacing._();

  static const double xxs = 4.0;
  static const double xs = 8.0;
  static const double sm = 12.0;
  static const double md = 16.0;
  static const double lg = 20.0;
  static const double xl = 24.0;
  static const double xxl = 32.0;

  // Touch targets & interactive components
  static const double touchTargetMin = 48.0;
  static const double buttonHeight = 48.0;
  static const double inputHeight = 52.0;

  // Bottom scroll clearance to guarantee FAB / NavigationBar never covers content
  static const double bottomClearance = 88.0;

  // Radii
  static const double radiusSm = 6.0;
  static const double radiusMd = 10.0;
  static const double radiusLg = 14.0;
  static const double radiusXl = 20.0;

  static const BorderRadius roundedSm = BorderRadius.all(
    Radius.circular(radiusSm),
  );
  static const BorderRadius roundedMd = BorderRadius.all(
    Radius.circular(radiusMd),
  );
  static const BorderRadius roundedLg = BorderRadius.all(
    Radius.circular(radiusLg),
  );
  static const BorderRadius roundedXl = BorderRadius.all(
    Radius.circular(radiusXl),
  );
}
