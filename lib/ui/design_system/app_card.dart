import 'package:flutter/material.dart';

import 'app_tokens.dart';

/// Themed surface card: crisp border, no elevation (editorial style).
///
/// When [onTap] is set, the card exposes button semantics so screen readers
/// announce it as actionable (accessibility requirement — the Phase 0 audit
/// found tappable cards announced as plain text).
class AppCard extends StatelessWidget {
  const AppCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(AppSpace.lg),
    this.onTap,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;

  /// Non-null turns the card into a tappable target with button semantics.
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final content = Padding(padding: padding, child: child);
    if (onTap == null) {
      return Card(child: content);
    }
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Semantics(
        button: true,
        child: InkWell(onTap: onTap, child: content),
      ),
    );
  }
}
