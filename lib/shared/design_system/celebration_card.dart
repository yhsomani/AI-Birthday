import 'package:flutter/material.dart';

import 'app_colors.dart';
import 'app_spacing.dart';
import 'countdown_chip.dart';

/// Editorial celebration card for birthdays.
///
/// Features clean structural framing (1px border, 0 elevation), avatar initials,
/// subtitle with age calculations, and an accessible countdown chip or action.
class CelebrationCard extends StatelessWidget {
  const CelebrationCard({
    super.key,
    required this.name,
    required this.subtitle,
    required this.daysUntil,
    this.isToday = false,
    this.statusText,
    this.onTap,
    this.trailingAction,
  });

  final String name;
  final String subtitle;
  final int daysUntil;
  final bool isToday;
  final String? statusText;
  final VoidCallback? onTap;
  final Widget? trailingAction;

  String _initial(String n) {
    final trimmed = n.trim();
    return trimmed.isNotEmpty ? trimmed[0].toUpperCase() : '?';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    final avatarBg = isToday
        ? (isDark
              ? AppColors.primaryTerracottaLight
              : AppColors.primaryTerracotta)
        : (isDark
              ? AppColors.darkSurfaceVariant
              : AppColors.lightSurfaceVariant);
    final avatarFg = isToday
        ? Colors.white
        : (isDark ? AppColors.darkTextPrimary : AppColors.lightTextPrimary);

    return Card(
      margin: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: InkWell(
        onTap: onTap,
        borderRadius: AppSpacing.roundedMd,
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.md),
          child: Row(
            children: [
              CircleAvatar(
                radius: 20,
                backgroundColor: avatarBg,
                foregroundColor: avatarFg,
                child: Text(
                  _initial(name),
                  style: const TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 16,
                  ),
                ),
              ),
              const SizedBox(width: AppSpacing.sm + 2),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      name,
                      style: const TextStyle(
                        fontWeight: FontWeight.w700,
                        fontSize: 15,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: TextStyle(
                        fontSize: 13,
                        color: isDark
                            ? AppColors.darkTextSecondary
                            : AppColors.lightTextSecondary,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: AppSpacing.xs),
              if (trailingAction != null)
                trailingAction!
              else
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    CountdownChip(
                      daysUntil: daysUntil,
                      isToday: isToday,
                      customLabel: statusText,
                    ),
                    if (onTap != null) ...[
                      const SizedBox(width: AppSpacing.xs),
                      Icon(
                        Icons.chevron_right,
                        size: 20,
                        color: isDark
                            ? AppColors.darkTextSecondary
                            : AppColors.lightTextSecondary,
                      ),
                    ],
                  ],
                ),
            ],
          ),
        ),
      ),
    );
  }
}
