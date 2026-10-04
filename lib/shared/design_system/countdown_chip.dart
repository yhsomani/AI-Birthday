import 'package:flutter/material.dart';

import 'app_colors.dart';
import 'app_spacing.dart';

/// Contextual countdown badge indicating proximity to a birthday.
///
/// Ensures clear visual hierarchy and accessibility:
/// - Today: Terracotta container with bold primary text.
/// - Tomorrow: Warm amber container with bold accent text.
/// - In N days: Subtle surface container with structured font.
class CountdownChip extends StatelessWidget {
  const CountdownChip({
    super.key,
    required this.daysUntil,
    this.isToday = false,
    this.customLabel,
  });

  final int daysUntil;
  final bool isToday;
  final String? customLabel;

  String get _label {
    if (customLabel != null) return customLabel!;
    if (isToday || daysUntil == 0) return 'Today';
    if (daysUntil == 1) return 'Tomorrow';
    if (daysUntil <= 90) return 'In $daysUntil days';
    return 'In ${(daysUntil / 30).ceil()} months';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    Color bg;
    Color fg;

    if (isToday || daysUntil == 0) {
      bg = isDark
          ? AppColors.primaryTerracotta.withValues(alpha: 0.3)
          : AppColors.primaryTerracottaContainer;
      fg = isDark
          ? AppColors.primaryTerracottaLight
          : AppColors.primaryTerracotta;
    } else if (daysUntil == 1 || daysUntil <= 7) {
      bg = isDark
          ? AppColors.accentAmber.withValues(alpha: 0.25)
          : AppColors.accentAmberContainer;
      fg = isDark ? AppColors.accentAmberLight : AppColors.accentAmber;
    } else {
      bg = isDark
          ? AppColors.darkSurfaceVariant
          : AppColors.lightSurfaceVariant;
      fg = isDark ? AppColors.darkTextSecondary : AppColors.lightTextSecondary;
    }

    return Container(
      constraints: const BoxConstraints(minHeight: 28),
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.xs + 2,
        vertical: AppSpacing.xxs,
      ),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: AppSpacing.roundedSm,
        border: Border.all(color: fg.withValues(alpha: 0.2), width: 1),
      ),
      child: Center(
        widthFactor: 1.0,
        heightFactor: 1.0,
        child: Text(
          _label,
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w700,
            color: fg,
            letterSpacing: 0.2,
          ),
        ),
      ),
    );
  }
}
