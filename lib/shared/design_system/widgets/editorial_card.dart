import 'package:flutter/material.dart';

import '../tokens/app_palette.dart';
import '../tokens/app_radius.dart';
import '../tokens/app_space.dart';

/// 1px bordered Editorial Warmth surface card (zero gradients, zero heavy shadows).
class EditorialCard extends StatelessWidget {
  const EditorialCard({
    super.key,
    required this.child,
    this.onTap,
    this.padding = const EdgeInsets.all(AppSpace.lg),
    this.margin = EdgeInsets.zero,
    this.borderColor,
    this.backgroundColor,
    this.semanticLabel,
  });

  final Widget child;
  final VoidCallback? onTap;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry margin;
  final Color? borderColor;
  final Color? backgroundColor;
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    final palette = context.colors;
    final card = Material(
      color: backgroundColor ?? palette.surface,
      shape: RoundedRectangleBorder(
        borderRadius: AppRadius.roundedMd,
        side: BorderSide(color: borderColor ?? palette.border, width: 1),
      ),
      clipBehavior: Clip.antiAlias,
      child: onTap != null
          ? InkWell(
              onTap: onTap,
              borderRadius: AppRadius.roundedMd,
              child: ConstrainedBox(
                constraints: const BoxConstraints(
                  minHeight: AppSpace.touchTargetMin,
                ),
                child: Padding(padding: padding, child: child),
              ),
            )
          : Padding(padding: padding, child: child),
    );

    final wrapped = margin == EdgeInsets.zero
        ? card
        : Padding(padding: margin, child: card);

    if (semanticLabel != null) {
      return Semantics(
        button: onTap != null,
        label: semanticLabel,
        child: wrapped,
      );
    }
    return wrapped;
  }
}
