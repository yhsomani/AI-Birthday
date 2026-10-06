import 'package:flutter/material.dart';

import 'app_tokens.dart';

/// The correct rendered state when a feature has no data (SSOT §15).
///
/// Semantically bundled for screen readers; scales with system text size.
/// All strings are supplied by the caller via gen-l10n.
class AppEmptyState extends StatelessWidget {
  const AppEmptyState({
    super.key,
    required this.icon,
    required this.title,
    this.message,
    this.action,
    this.tone = AppTone.primary,
  });

  final IconData icon;
  final String title;
  final String? message;
  final Widget? action;
  final AppTone tone;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final textTheme = Theme.of(context).textTheme;

    return Center(
      child: Semantics(
        container: true,
        label: title,
        child: Padding(
          padding: const EdgeInsets.all(AppSpace.xl),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: AppIconSize.lg, color: tone.ink(colors)),
              const SizedBox(height: AppSpace.lg),
              Text(
                title,
                textAlign: TextAlign.center,
                style: textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w600,
                ),
              ),
              if (message != null) ...[
                const SizedBox(height: AppSpace.sm),
                Text(
                  message!,
                  textAlign: TextAlign.center,
                  style: textTheme.bodyMedium?.copyWith(
                    color: colors.textSecondary,
                  ),
                ),
              ],
              if (action != null) ...[
                const SizedBox(height: AppSpace.xl),
                action!,
              ],
            ],
          ),
        ),
      ),
    );
  }
}
