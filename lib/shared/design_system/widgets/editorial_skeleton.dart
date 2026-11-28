import 'package:flutter/material.dart';

import 'package:ai_birthday/l10n/l10n_extension.dart';

import '../tokens/app_duration.dart';
import '../tokens/app_palette.dart';
import '../tokens/app_radius.dart';
import '../tokens/app_space.dart';

/// Calm editorial skeleton placeholder box that respects system reduced-motion
/// (`MediaQuery.maybeDisableAnimationsOf(context)`).
class EditorialSkeletonBox extends StatefulWidget {
  const EditorialSkeletonBox({
    super.key,
    this.width,
    required this.height,
    this.borderRadius = AppRadius.sm,
  });

  final double? width;
  final double height;
  final double borderRadius;

  @override
  State<EditorialSkeletonBox> createState() => _EditorialSkeletonBoxState();
}

class _EditorialSkeletonBoxState extends State<EditorialSkeletonBox>
    with SingleTickerProviderStateMixin {
  AnimationController? _controller;

  bool get _disableAnimations =>
      MediaQuery.maybeDisableAnimationsOf(context) ?? false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_disableAnimations) {
      _controller?.dispose();
      _controller = null;
    } else {
      _controller ??= AnimationController(
        vsync: this,
        duration: AppDuration.shimmer,
      )..repeat(reverse: true);
    }
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final palette = context.colors;
    final radius = BorderRadius.circular(widget.borderRadius);
    final controller = _controller;

    if (controller == null) {
      return SizedBox(
        width: widget.width,
        height: widget.height,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: palette.skeletonBase,
            borderRadius: radius,
            border: Border.all(color: palette.border, width: 1),
          ),
        ),
      );
    }

    return RepaintBoundary(
      child: ExcludeSemantics(
        child: AnimatedBuilder(
          animation: controller,
          builder: (context, _) {
            final color = Color.lerp(
              palette.skeletonBase,
              palette.skeletonHighlight,
              controller.value,
            );
            return SizedBox(
              width: widget.width,
              height: widget.height,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: color,
                  borderRadius: radius,
                  border: Border.all(color: palette.border, width: 1),
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}

/// Proportional skeleton text line.
class EditorialSkeletonLine extends StatelessWidget {
  const EditorialSkeletonLine({
    super.key,
    this.widthFactor = 1.0,
    this.height = 14.0,
  });

  final double widthFactor;
  final double height;

  @override
  Widget build(BuildContext context) {
    return FractionallySizedBox(
      alignment: Alignment.centerLeft,
      widthFactor: widthFactor,
      child: EditorialSkeletonBox(height: height, borderRadius: AppRadius.sm),
    );
  }
}

/// Editorial skeleton card matching `CelebrationCard` geometry.
class EditorialSkeletonCard extends StatelessWidget {
  const EditorialSkeletonCard({super.key, this.height = 76.0});

  final double height;

  @override
  Widget build(BuildContext context) {
    final palette = context.colors;
    return Container(
      constraints: BoxConstraints(minHeight: height),
      padding: const EdgeInsets.all(AppSpace.md),
      decoration: BoxDecoration(
        color: palette.surface,
        borderRadius: AppRadius.roundedMd,
        border: Border.all(color: palette.border, width: 1),
      ),
      child: const Row(
        children: [
          EditorialSkeletonBox(
            width: 40,
            height: 40,
            borderRadius: AppRadius.pill,
          ),
          SizedBox(width: AppSpace.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                EditorialSkeletonLine(widthFactor: 0.55, height: 15),
                SizedBox(height: AppSpace.sm),
                EditorialSkeletonLine(widthFactor: 0.38, height: 12),
              ],
            ),
          ),
          SizedBox(width: AppSpace.sm),
          EditorialSkeletonBox(
            width: 64,
            height: 28,
            borderRadius: AppRadius.sm,
          ),
        ],
      ),
    );
  }
}

/// Vertical list of editorial skeleton cards with a single loading semantic label.
class EditorialSkeletonList extends StatelessWidget {
  const EditorialSkeletonList({
    super.key,
    this.itemCount = 4,
    this.semanticLabel,
    this.padding = const EdgeInsets.all(AppSpace.lg),
  });

  final int itemCount;
  final String? semanticLabel;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: semanticLabel ?? context.l10n.commonLoading,
      child: ExcludeSemantics(
        child: SingleChildScrollView(
          padding: padding,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (var i = 0; i < itemCount; i++) ...[
                const EditorialSkeletonCard(),
                if (i < itemCount - 1) const SizedBox(height: AppSpace.sm),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
