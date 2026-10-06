import 'package:flutter/material.dart';

import 'app_tokens.dart';

/// Maps countdown proximity to a chip tone:
/// today/overdue → [AppTone.primary], within a week → [AppTone.warning],
/// further out → [AppTone.neutral].
///
/// The label is localized by the caller (gen-l10n); this only picks color.
AppTone toneForCountdown(int daysUntil) {
  if (daysUntil <= 0) return AppTone.primary;
  if (daysUntil <= 7) return AppTone.warning;
  return AppTone.neutral;
}

/// Tone-based badge for countdowns and history statuses (Draft/Opened/Sent).
///
/// Informational only — not interactive, so no 48dp requirement; text color
/// and fill are a WCAG-checked pair (see `test/ui/contrast_test.dart`).
class AppChip extends StatelessWidget {
  const AppChip({
    super.key,
    required this.label,
    required this.tone,
    this.icon,
  });

  final String label;
  final AppTone tone;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final fg = tone.label(colors);

    return Container(
      constraints: const BoxConstraints(minHeight: 28),
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpace.sm,
        vertical: AppSpace.xs,
      ),
      decoration: BoxDecoration(
        color: tone.fill(colors),
        borderRadius: BorderRadius.circular(AppRadius.sm),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: AppIconSize.sm, color: fg),
            const SizedBox(width: AppSpace.xs),
          ],
          Text(
            label,
            style: Theme.of(context).textTheme.labelMedium?.copyWith(
              fontWeight: FontWeight.w700,
              letterSpacing: 0.2,
              color: fg,
            ),
          ),
        ],
      ),
    );
  }
}
