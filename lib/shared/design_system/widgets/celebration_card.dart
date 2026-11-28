import 'package:flutter/material.dart';

import '../tokens/app_palette.dart';
import '../tokens/app_radius.dart';
import '../tokens/app_space.dart';
import 'countdown_chip.dart';

/// Editorial celebration card for birthdays.
///
/// Features clean structural framing (1px border, 0 elevation), avatar initials,
/// subtitle, and an accessible countdown chip or action.
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
    final palette = context.colors;

    final avatarBg = isToday ? palette.primary : palette.surfaceAlt;
    final avatarFg = isToday ? palette.onPrimary : palette.textPrimary;

    return Card(
      margin: const EdgeInsets.only(bottom: AppSpace.sm),
      child: InkWell(
        onTap: onTap,
        borderRadius: AppRadius.roundedMd,
        child: Padding(
          padding: const EdgeInsets.all(AppSpace.md),
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
              const SizedBox(width: AppSpace.md + 2),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      name,
                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                        fontSize: 15,
                        color: palette.textPrimary,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: TextStyle(
                        fontSize: 13,
                        color: palette.textSecondary,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: AppSpace.sm),
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
                      const SizedBox(width: AppSpace.sm),
                      Icon(
                        Icons.chevron_right,
                        size: 20,
                        color: palette.textSecondary,
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
