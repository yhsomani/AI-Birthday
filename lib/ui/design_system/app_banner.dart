import 'package:flutter/material.dart';

import 'app_tokens.dart';

/// Inline banner: soft surface with a tone-colored accent bar + icon.
///
/// For contextual notices (offline, free tier, draft state) — not a modal.
/// Optional single action. All strings are supplied by the caller via
/// gen-l10n; asserts the action label exists whenever [onAction] does.
class AppBanner extends StatelessWidget {
  const AppBanner({
    super.key,
    required this.message,
    this.tone = AppTone.info,
    this.onAction,
    this.actionLabel,
  }) : assert(
         onAction == null || actionLabel != null,
         'actionLabel is required when onAction is provided',
       );

  final String message;
  final AppTone tone;
  final VoidCallback? onAction;
  final String? actionLabel;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    return Container(
      decoration: BoxDecoration(
        color: colors.surfaceAlt,
        border: Border(left: BorderSide(color: tone.ink(colors), width: 3)),
      ),
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpace.md,
        vertical: AppSpace.md,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Icon(tone.icon, size: AppIconSize.sm, color: tone.ink(colors)),
          const SizedBox(width: AppSpace.sm),
          Expanded(
            child: Text(
              message,
              style: Theme.of(
                context,
              ).textTheme.bodyMedium?.copyWith(color: colors.textPrimary),
            ),
          ),
          if (onAction != null) ...[
            const SizedBox(width: AppSpace.sm),
            TextButton(onPressed: onAction, child: Text(actionLabel!)),
          ],
        ],
      ),
    );
  }
}
