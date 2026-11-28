import 'package:flutter/material.dart';

import '../tokens/app_palette.dart';
import '../tokens/app_radius.dart';
import '../tokens/app_space.dart';

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
    final palette = context.colors;
    final headerColor = isAccent ? palette.primary : palette.textPrimary;

    return Semantics(
      header: true,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpace.sm),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
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
                padding: const EdgeInsets.only(left: AppSpace.sm),
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppSpace.sm - 1,
                    vertical: 2,
                  ),
                  decoration: BoxDecoration(
                    color: isAccent
                        ? palette.primaryContainer
                        : palette.surfaceAlt,
                    borderRadius: AppRadius.roundedSm,
                  ),
                  child: Text(
                    '$count',
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: isAccent
                          ? palette.onPrimaryContainer
                          : palette.textSecondary,
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
