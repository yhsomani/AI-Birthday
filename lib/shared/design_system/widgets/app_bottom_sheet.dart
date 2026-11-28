import 'package:flutter/material.dart';

import '../tokens/app_palette.dart';
import '../tokens/app_radius.dart';
import '../tokens/app_space.dart';

/// Standardized, scroll-safe modal bottom sheet container.
class AppBottomSheet extends StatelessWidget {
  const AppBottomSheet({
    super.key,
    required this.children,
    this.padding = const EdgeInsets.all(AppSpace.xl),
  });

  final List<Widget> children;
  final EdgeInsetsGeometry padding;

  /// Helper to present an [AppBottomSheet] with consistent styling.
  static Future<T?> show<T>({
    required BuildContext context,
    required List<Widget> children,
    EdgeInsetsGeometry padding = const EdgeInsets.all(AppSpace.xl),
  }) {
    return showModalBottomSheet<T>(
      context: context,
      isScrollControlled: true,
      backgroundColor: context.colors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(AppRadius.xl)),
      ),
      builder: (sheetContext) =>
          AppBottomSheet(padding: padding, children: children),
    );
  }

  @override
  Widget build(BuildContext context) {
    final palette = context.colors;

    return SafeArea(
      child: Padding(
        padding: MediaQuery.of(context).viewInsets,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(
              child: Container(
                margin: const EdgeInsets.only(
                  top: AppSpace.md,
                  bottom: AppSpace.sm,
                ),
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                  color: palette.border,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            Flexible(
              child: SingleChildScrollView(
                padding: padding,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: children,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
