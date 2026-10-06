import 'package:flutter/material.dart';

import 'package:ai_birthday/app/theme/app_theme.dart';
import 'package:ai_birthday/ui/design_system/design_system.dart';

/// Design-system gallery: every component in one place, for golden tests and
/// text-scale checks. Test-only — never shipped in the app.
///
/// Renders its own [MaterialApp] so goldens and overflow tests can pump it
/// standalone at any surface size.
class ComponentGallery extends StatelessWidget {
  const ComponentGallery({
    super.key,
    this.brightness = Brightness.light,
    this.textScaler,
  });

  final Brightness brightness;
  final TextScaler? textScaler;

  @override
  Widget build(BuildContext context) {
    return RepaintBoundary(
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: brightness == Brightness.dark ? AppTheme.dark : AppTheme.light,
        home: Builder(
          builder: (context) {
            final media = MediaQuery.of(context);
            return MediaQuery(
              data: textScaler == null
                  ? media
                  : media.copyWith(textScaler: textScaler),
              child: const Scaffold(body: _GalleryBody()),
            );
          },
        ),
      ),
    );
  }
}

class _GalleryBody extends StatelessWidget {
  const _GalleryBody();

  static void _noop() {}

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final textTheme = Theme.of(context).textTheme;

    return ColoredBox(
      color: colors.background,
      child: Padding(
        padding: const EdgeInsets.all(AppSpace.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Section headers
            const SectionHeader(
              title: 'Section headers',
              isAccent: true,
              trailing: _StaticLink(label: 'See all'),
            ),
            const SectionHeader(title: 'Plain with count', count: 4),
            const SizedBox(height: AppSpace.md),

            // Chips
            const SectionHeader(title: 'Chips'),
            const SizedBox(height: AppSpace.sm),
            const Wrap(
              spacing: AppSpace.sm,
              runSpacing: AppSpace.sm,
              children: [
                AppChip(label: 'Today', tone: AppTone.primary),
                AppChip(label: 'In 3 days', tone: AppTone.warning),
                AppChip(label: 'In 45 days', tone: AppTone.neutral),
                AppChip(
                  label: 'Opened',
                  tone: AppTone.info,
                  icon: Icons.open_in_new,
                ),
                AppChip(
                  label: 'Sent',
                  tone: AppTone.success,
                  icon: Icons.check_circle_outline,
                ),
                AppChip(
                  label: 'Removed',
                  tone: AppTone.danger,
                  icon: Icons.delete_outline,
                ),
              ],
            ),
            const SizedBox(height: AppSpace.lg),

            // Buttons (theme-level components)
            const SectionHeader(title: 'Buttons'),
            const SizedBox(height: AppSpace.sm),
            Wrap(
              spacing: AppSpace.sm,
              runSpacing: AppSpace.sm,
              children: [
                FilledButton(onPressed: _noop, child: const Text('Primary')),
                OutlinedButton(
                  onPressed: _noop,
                  child: const Text('Secondary'),
                ),
                TextButton(onPressed: _noop, child: const Text('Text')),
              ],
            ),
            const SizedBox(height: AppSpace.lg),

            // Cards
            const SectionHeader(title: 'Cards'),
            const SizedBox(height: AppSpace.sm),
            AppCard(
              onTap: _noop,
              child: Row(
                children: [
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: colors.primaryContainer,
                      shape: BoxShape.circle,
                    ),
                    child: Center(
                      child: Text(
                        'A',
                        style: textTheme.titleMedium?.copyWith(
                          color: colors.onPrimaryContainer,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: AppSpace.md),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Asha', style: textTheme.titleMedium),
                        const SizedBox(height: AppSpace.xs),
                        Text(
                          'Turns 30 tomorrow',
                          style: textTheme.bodySmall?.copyWith(
                            color: colors.textSecondary,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const AppChip(label: 'Tomorrow', tone: AppTone.warning),
                ],
              ),
            ),
            const SizedBox(height: AppSpace.sm),
            const AppCard(child: Text('Static card, no tap target.')),
            const SizedBox(height: AppSpace.lg),

            // Banners
            const SectionHeader(title: 'Banners'),
            const SizedBox(height: AppSpace.sm),
            AppBanner(
              message: 'WhatsApp is not connected.',
              onAction: _noop,
              actionLabel: 'Connect',
            ),
            const SizedBox(height: AppSpace.sm),
            const AppBanner(
              message: 'Backup completed.',
              tone: AppTone.success,
            ),
            const SizedBox(height: AppSpace.sm),
            const AppBanner(
              message: 'Draft has not been sent yet.',
              tone: AppTone.warning,
            ),
            const SizedBox(height: AppSpace.sm),
            AppBanner(
              message: 'Could not load birthdays.',
              tone: AppTone.danger,
              onAction: _noop,
              actionLabel: 'Retry',
            ),
            const SizedBox(height: AppSpace.lg),

            // Empty & error states
            const SectionHeader(title: 'Empty & error states'),
            AppEmptyState(
              icon: Icons.cake_outlined,
              title: 'No birthdays yet',
              message: 'Add someone to get started.',
              action: FilledButton(
                onPressed: _noop,
                child: const Text('Add person'),
              ),
            ),
            AppErrorState(
              message: 'Could not load birthdays.',
              retryLabel: 'Try again',
              onRetry: _noop,
            ),
            const SizedBox(height: AppSpace.lg),

            // Skeletons
            const SectionHeader(title: 'Skeletons'),
            const SizedBox(height: AppSpace.sm),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SkeletonBox(
                  width: 48,
                  height: 48,
                  radius: AppRadius.pill,
                ),
                const SizedBox(width: AppSpace.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: const [
                      SkeletonBox(height: AppSpace.lg),
                      SizedBox(height: AppSpace.sm),
                      SkeletonBox(width: 160, height: AppSpace.md),
                      SizedBox(height: AppSpace.sm),
                      SkeletonBox(width: 120, height: AppSpace.md),
                    ],
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Trailing link stand-in for the header showcase (test content only).
class _StaticLink extends StatelessWidget {
  const _StaticLink({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return TextButton(onPressed: () {}, child: Text(label));
  }
}
