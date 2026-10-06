import 'package:flutter/material.dart';

import 'app_tokens.dart';

/// Static loading placeholder block.
///
/// ponytail: static color, no shimmer — motion belongs to Phase 7, add
/// animation only if profiling shows wait durations justify it.
class SkeletonBox extends StatelessWidget {
  const SkeletonBox({
    super.key,
    this.width,
    this.height = AppSpace.lg,
    this.radius = AppRadius.sm,
  });

  /// Null fills the cross axis in a Column (Container's bounded-parent
  /// behavior); set explicitly inside Rows.
  final double? width;
  final double height;
  final double radius;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: context.colors.surfaceAlt,
        borderRadius: BorderRadius.circular(radius),
      ),
    );
  }
}
