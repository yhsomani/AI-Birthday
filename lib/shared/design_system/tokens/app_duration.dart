/// Canonical motion duration tokens.
abstract final class AppDuration {
  static const Duration fast = Duration(milliseconds: 150);
  static const Duration base = Duration(milliseconds: 250);
  static const Duration slow = Duration(milliseconds: 400);
  static const Duration shimmer = Duration(milliseconds: 1200);
}

/// Standard informational icon display sizes (never interactive touch targets).
abstract final class AppIconSize {
  static const double sm = 16.0;
  static const double md = 24.0;
  static const double lg = 48.0;
}
