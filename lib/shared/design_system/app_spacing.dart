import 'package:flutter/material.dart';

import 'tokens/app_radius.dart';
import 'tokens/app_space.dart';

/// Legacy spacing, elevation, and shape tokens kept for M2–M5 migration.
@Deprecated('Use AppSpace and AppRadius via tokens/tokens.dart instead.')
class AppSpacing {
  const AppSpacing._();

  static const double xxs = AppSpace.xs;
  static const double xs = AppSpace.sm;
  static const double sm = AppSpace.md;
  static const double md = AppSpace.lg;
  static const double lg = AppSpace.lgPlus;
  static const double xl = AppSpace.xl;
  static const double xxl = AppSpace.xxl;

  // Touch targets & interactive components
  static const double touchTargetMin = AppSpace.touchTargetMin;
  static const double buttonHeight = AppSpace.buttonHeight;
  static const double inputHeight = AppSpace.inputHeight;

  // Bottom scroll clearance to guarantee FAB / NavigationBar never covers content
  static const double bottomClearance = AppSpace.bottomClearance;

  // Radii
  static const double radiusSm = AppRadius.sm;
  static const double radiusMd = AppRadius.md;
  static const double radiusLg = AppRadius.lg;
  static const double radiusXl = AppRadius.xl;

  static const BorderRadius roundedSm = AppRadius.roundedSm;
  static const BorderRadius roundedMd = AppRadius.roundedMd;
  static const BorderRadius roundedLg = AppRadius.roundedLg;
  static const BorderRadius roundedXl = AppRadius.roundedXl;
}
