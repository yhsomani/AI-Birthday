import 'package:flutter/material.dart';

import 'app_tokens.dart';

/// Failure state with a retry action.
///
/// The Phase 0 audit found no retry affordance anywhere in the app; this is
/// the shared replacement every screen migrates to. Screen readers get one
/// bundled announcement (message + button).
///
/// All strings are supplied by the caller via gen-l10n; asserts the retry
/// label exists whenever [onRetry] does.
class AppErrorState extends StatelessWidget {
  const AppErrorState({
    super.key,
    required this.message,
    this.onRetry,
    this.retryLabel,
  }) : assert(
         onRetry == null || retryLabel != null,
         'retryLabel is required when onRetry is provided',
       );

  final String message;
  final VoidCallback? onRetry;
  final String? retryLabel;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final textTheme = Theme.of(context).textTheme;
    const tone = AppTone.danger;

    return Center(
      child: Semantics(
        container: true,
        label: message,
        child: Padding(
          padding: const EdgeInsets.all(AppSpace.xl),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(tone.icon, size: AppIconSize.lg, color: tone.ink(colors)),
              const SizedBox(height: AppSpace.lg),
              Text(
                message,
                textAlign: TextAlign.center,
                style: textTheme.bodyMedium?.copyWith(
                  color: colors.textSecondary,
                ),
              ),
              if (onRetry != null) ...[
                const SizedBox(height: AppSpace.xl),
                FilledButton(onPressed: onRetry, child: Text(retryLabel!)),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
