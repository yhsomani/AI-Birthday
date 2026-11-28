import 'package:flutter/material.dart';

import '../tokens/app_space.dart';

/// Responsive button container that eliminates horizontal RenderFlex overflows.
///
/// On wide viewports with standard text sizes, renders side-by-side with [Expanded].
/// On compact viewports (< 380dp) or large text scale (>= 1.15x), automatically
/// stacks buttons vertically at full width to preserve readability and tap targets.
class ResponsiveActionBar extends StatelessWidget {
  const ResponsiveActionBar({
    super.key,
    required this.primary,
    required this.secondary,
    this.breakpoint = 380.0,
    this.spacing = AppSpace.md,
  });

  final Widget primary;
  final Widget secondary;
  final double breakpoint;
  final double spacing;

  @override
  Widget build(BuildContext context) {
    final textScale = MediaQuery.textScalerOf(context).scale(1.0);

    return LayoutBuilder(
      builder: (context, constraints) {
        final shouldStack =
            constraints.maxWidth < breakpoint || textScale >= 1.15;

        if (shouldStack) {
          return Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              primary,
              SizedBox(height: spacing),
              secondary,
            ],
          );
        }

        return Row(
          children: [
            Expanded(child: secondary),
            SizedBox(width: spacing),
            Expanded(child: primary),
          ],
        );
      },
    );
  }
}
