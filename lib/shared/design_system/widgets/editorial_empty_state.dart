import 'package:flutter/material.dart';

import '../tokens/app_duration.dart';
import '../tokens/app_palette.dart';
import '../tokens/app_space.dart';
import 'app_responsive_container.dart';

/// A calm, accessible empty-state view.
///
/// Semantically bundled for screen readers and scrollable inside narrow
/// viewports or 200% text scaling.
class EditorialEmptyState extends StatelessWidget {
  const EditorialEmptyState({
    super.key,
    required this.icon,
    required this.title,
    this.message,
    this.action,
    this.secondaryAction,
  });

  final IconData icon;
  final String title;
  final String? message;
  final Widget? action;
  final Widget? secondaryAction;

  @override
  Widget build(BuildContext context) {
    final palette = context.colors;
    final textTheme = Theme.of(context).textTheme;
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(AppSpace.xl),
        child: AppResponsiveContainer(
          maxWidth: 480,
          child: Semantics(
            container: true,
            label: title,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(icon, size: AppIconSize.lg, color: palette.primary),
                const SizedBox(height: AppSpace.lg),
                Text(
                  title,
                  textAlign: TextAlign.center,
                  style: textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                    color: palette.textPrimary,
                  ),
                ),
                if (message != null) ...[
                  const SizedBox(height: AppSpace.sm),
                  Text(
                    message!,
                    textAlign: TextAlign.center,
                    style: textTheme.bodyMedium?.copyWith(
                      color: palette.textSecondary,
                    ),
                  ),
                ],
                if (action != null) ...[
                  const SizedBox(height: AppSpace.lgPlus),
                  action!,
                ],
                if (secondaryAction != null) ...[
                  const SizedBox(height: AppSpace.sm),
                  secondaryAction!,
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}
