import 'package:flutter/material.dart';

import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/l10n/l10n_extension.dart';

import '../tokens/app_duration.dart';
import '../tokens/app_palette.dart';
import '../tokens/app_space.dart';
import 'app_responsive_container.dart';

/// Accessible error state with `liveRegion: true` announcement and retry CTA.
class EditorialErrorState extends StatelessWidget {
  const EditorialErrorState({
    super.key,
    this.failure,
    this.title,
    this.message,
    this.onRetry,
    this.retryLabel,
    this.secondaryAction,
  });

  final AppFailure? failure;
  final String? title;
  final String? message;
  final VoidCallback? onRetry;
  final String? retryLabel;
  final Widget? secondaryAction;

  @override
  Widget build(BuildContext context) {
    final palette = context.colors;
    final textTheme = Theme.of(context).textTheme;
    final l10n = context.l10n;

    final resolvedTitle = title ?? failure?.message ?? l10n.commonErrorTitle;
    final resolvedMessage =
        message ??
        (failure != null
            ? [
                if (failure!.detail != null && failure!.detail!.isNotEmpty)
                  failure!.detail!,
                if (failure!.action != null && failure!.action!.isNotEmpty)
                  failure!.action!,
              ].join(' ')
            : null);

    return Semantics(
      liveRegion: true,
      container: true,
      label: resolvedTitle,
      child: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(AppSpace.xl),
          child: AppResponsiveContainer(
            maxWidth: 480,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  Icons.error_outline_rounded,
                  size: AppIconSize.lg,
                  color: palette.danger,
                ),
                const SizedBox(height: AppSpace.lg),
                Text(
                  resolvedTitle,
                  textAlign: TextAlign.center,
                  style: textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                    color: palette.textPrimary,
                  ),
                ),
                if (resolvedMessage != null && resolvedMessage.isNotEmpty) ...[
                  const SizedBox(height: AppSpace.sm),
                  Text(
                    resolvedMessage,
                    textAlign: TextAlign.center,
                    style: textTheme.bodyMedium?.copyWith(
                      color: palette.textSecondary,
                    ),
                  ),
                ],
                if (onRetry != null) ...[
                  const SizedBox(height: AppSpace.lgPlus),
                  ConstrainedBox(
                    constraints: const BoxConstraints(
                      minHeight: AppSpace.buttonHeight,
                    ),
                    child: FilledButton.icon(
                      onPressed: onRetry,
                      icon: const Icon(Icons.refresh),
                      label: Text(retryLabel ?? l10n.commonRetry),
                    ),
                  ),
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
