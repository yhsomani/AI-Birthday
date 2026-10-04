import 'package:flutter/material.dart';

import 'app_colors.dart';
import 'app_spacing.dart';

/// Standardized, scroll-safe modal bottom sheet container.
///
/// Eliminates the critical layout crash where dynamic content (such as facts
/// lists or action buttons) causes RenderFlex overflow inside unconstrained sheets.
class AppBottomSheet extends StatelessWidget {
  const AppBottomSheet({
    super.key,
    required this.children,
    this.padding = const EdgeInsets.all(AppSpacing.xl),
  });

  final List<Widget> children;
  final EdgeInsetsGeometry padding;

  /// Helper to present an [AppBottomSheet] with consistent styling.
  static Future<T?> show<T>({
    required BuildContext context,
    required List<Widget> children,
    EdgeInsetsGeometry padding = const EdgeInsets.all(AppSpacing.xl),
  }) {
    return showModalBottomSheet<T>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(
          top: Radius.circular(AppSpacing.radiusXl),
        ),
      ),
      builder: (sheetContext) =>
          AppBottomSheet(padding: padding, children: children),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return SafeArea(
      child: Padding(
        padding: MediaQuery.of(context).viewInsets,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Drag handle
            Center(
              child: Container(
                margin: const EdgeInsets.only(
                  top: AppSpacing.sm,
                  bottom: AppSpacing.xs,
                ),
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                  color: isDark ? AppColors.darkBorder : AppColors.lightBorder,
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
