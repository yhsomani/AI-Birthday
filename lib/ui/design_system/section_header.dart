import 'package:flutter/material.dart';

import 'app_tokens.dart';

/// Editorial section header: uppercase tracking, optional count badge,
/// optional trailing action.
///
/// Announced as a heading by screen readers (`Semantics.header`).
/// All strings are supplied by the caller via gen-l10n.
class SectionHeader extends StatelessWidget {
  const SectionHeader({
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
    final colors = context.colors;
    final textTheme = Theme.of(context).textTheme;

    final headerColor = isAccent ? colors.primary : colors.textPrimary;
    final badgeColor = isAccent ? colors.primaryContainer : colors.surfaceAlt;
    final badgeTextColor = isAccent
        ? colors.onPrimaryContainer
        : colors.textSecondary;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpace.xs),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Flexible(
            child: Row(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Flexible(
                  child: Semantics(
                    header: true,
                    child: Text(
                      title.toUpperCase(),
                      style: textTheme.labelMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                        letterSpacing: 0.8,
                        color: headerColor,
                      ),
                    ),
                  ),
                ),
                if (count != null) ...[
                  const SizedBox(width: AppSpace.xs),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: AppSpace.xs,
                      vertical: AppSpace.xs / 2,
                    ),
                    decoration: BoxDecoration(
                      color: badgeColor,
                      borderRadius: BorderRadius.circular(AppRadius.sm),
                    ),
                    child: Text(
                      '$count',
                      style: textTheme.labelSmall?.copyWith(
                        fontWeight: FontWeight.w700,
                        color: badgeTextColor,
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
          ?trailing,
        ],
      ),
    );
  }
}
