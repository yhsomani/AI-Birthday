/// Canonical spacing, touch-target, and responsive breakpoint tokens.
abstract final class AppSpace {
  static const double xs = 4.0;
  static const double sm = 8.0;
  static const double md = 12.0;
  static const double lg = 16.0;
  static const double lgPlus = 20.0;
  static const double xl = 24.0;
  static const double xxl = 32.0;

  // Touch targets & interactive component dimensions
  static const double touchTargetMin = 48.0;
  static const double buttonHeight = 48.0;
  static const double inputHeight = 52.0;

  // Bottom scroll clearance so FAB / NavigationBar never obscures content
  static const double bottomClearance = 88.0;

  // Responsive layout constraints & breakpoints
  static const double maxContentWidth = 720.0;
  static const double compactBreakpoint = 600.0;
  static const double expandedBreakpoint = 840.0;
  static const double extendedRailBreakpoint = 1024.0;
}
