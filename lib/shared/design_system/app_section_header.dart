import 'package:flutter/material.dart';

import 'app_colors.dart';
import 'app_spacing.dart';

/// Editorial section header with uppercase tracking, optional count badge, or trailing action.
class AppSectionHeader extends StatelessWidget {
  const AppSectionHeader({
    super.key,
    required this.title,
    this.count,
    this.isAccent = false,
    this.trailing,
  });

  final String title;
  final int? count;
  final bool isAccent;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    final headerColor = isAccent
        ? (isDark
              ? AppColors.primaryTerracottaLight
              : AppColors.primaryTerracotta)
        : (isDark ? AppColors.darkTextPrimary : AppColors.lightTextPrimary);

    return Semantics(
      header: true,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            // Flexible must wrap the Text itself (a Row passes unbounded width
            // to children), so long 200%-scaled titles wrap instead of overflow.
            Flexible(
              child: Text(
                title.toUpperCase(),
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.8,
                  color: headerColor,
                ),
              ),
            ),
            if (count != null)
              Padding(
                padding: const EdgeInsets.only(left: AppSpacing.xs),
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppSpacing.xs - 1,
                    vertical: 2,
                  ),
                  decoration: BoxDecoration(
                    color: isAccent
                        ? (isDark
                              ? AppColors.primaryTerracotta.withValues(
                                  alpha: 0.25,
                                )
                              : AppColors.primaryTerracottaContainer)
                        : (isDark
                              ? AppColors.darkSurfaceVariant
                              : AppColors.lightSurfaceVariant),
                    borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
                  ),
                  child: Text(
                    '$count',
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: isAccent
                          ? (isDark
                                ? AppColors.primaryTerracottaLight
                                : AppColors.primaryTerracotta)
                          : (isDark
                                ? AppColors.darkTextSecondary
                                : AppColors.lightTextSecondary),
                    ),
                  ),
                ),
              ),
            ?trailing,
          ],
        ),
      ),
    );
  }
}
