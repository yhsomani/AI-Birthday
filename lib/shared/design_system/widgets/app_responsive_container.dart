import 'package:flutter/widgets.dart';

import '../tokens/app_space.dart';

/// Centers and constrains page content to [maxWidth] (default `720 dp`) so
/// layouts remain readable on tablets and wide desktop windows without
/// restricting narrow mobile viewports.
class AppResponsiveContainer extends StatelessWidget {
  const AppResponsiveContainer({
    super.key,
    required this.child,
    this.maxWidth = AppSpace.maxContentWidth,
    this.padding,
    this.alignment = Alignment.topCenter,
  });

  final Widget child;
  final double maxWidth;
  final EdgeInsetsGeometry? padding;
  final AlignmentGeometry alignment;

  @override
  Widget build(BuildContext context) {
    final constrained = ConstrainedBox(
      constraints: BoxConstraints(maxWidth: maxWidth),
      child: padding != null ? Padding(padding: padding!, child: child) : child,
    );
    return Align(alignment: alignment, child: constrained);
  }
}
