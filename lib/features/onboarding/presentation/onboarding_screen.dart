import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/app/router.dart';
import 'package:ai_birthday/shared/design_system/design_system.dart';

/// Guided 3-page First-Run Onboarding Flow.
///
/// Introduces the 5-step loop (Remember -> Prepare -> Personalize -> Review -> Send),
/// establishes the local-first privacy promise, and offers on-ramps to add
/// birthdays or learn about optional reminders.
class OnboardingScreen extends ConsumerStatefulWidget {
  const OnboardingScreen({super.key, this.isReplay = false});

  final bool isReplay;

  @override
  ConsumerState<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends ConsumerState<OnboardingScreen> {
  final PageController _pageController = PageController();
  int _currentPage = 0;
  bool _saveFailed = false;

  static const int _totalPages = 3;

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  Future<void> _finishOnboarding() async {
    HapticFeedback.lightImpact();
    if (!await _saveCompletion()) return;
    _navigateAfterCompletion(null);
  }

  Future<void> _finishTo(String destination) async {
    HapticFeedback.lightImpact();
    if (!await _saveCompletion()) return;
    _navigateAfterCompletion(destination);
  }

  void _navigateAfterCompletion(String? destination) {
    if (!mounted) return;
    if (destination != null) {
      context.go(AppRoutes.people);
      if (destination != AppRoutes.people) {
        context.push(destination);
      }
      return;
    }
    if (context.canPop()) {
      context.pop();
    } else {
      context.go(AppRoutes.dashboard);
    }
  }

  Future<bool> _saveCompletion() async {
    try {
      await ref.read(credentialStorageProvider).setCompletedOnboarding(true);
      ref.read(onboardingGateProvider).markSessionCompleted();
      if (mounted && _saveFailed) {
        setState(() => _saveFailed = false);
      }
      return true;
    } catch (error, stackTrace) {
      ref
          .read(loggerProvider)
          .warning(
            'onboarding',
            'Could not save onboarding completion.',
            error: error,
            stackTrace: stackTrace,
          );
      if (widget.isReplay) {
        ref.read(onboardingGateProvider).markSessionCompleted();
        return true;
      }
      if (mounted) {
        setState(() => _saveFailed = true);
        final l10n = context.l10n;
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(l10n.onboardingSaveError)));
      }
      return false;
    }
  }

  void _continueBypassingSave() {
    HapticFeedback.lightImpact();
    ref.read(onboardingGateProvider).markSessionCompleted();
    ScaffoldMessenger.of(context).hideCurrentSnackBar();
    _navigateAfterCompletion(null);
  }

  void _nextPage(bool disableAnimations) {
    HapticFeedback.lightImpact();
    if (_currentPage < _totalPages - 1) {
      if (disableAnimations) {
        _pageController.jumpToPage(_currentPage + 1);
      } else {
        _pageController.nextPage(
          duration: AppDuration.base,
          curve: Curves.easeInOut,
        );
      }
    } else {
      _finishOnboarding();
    }
  }

  @override
  Widget build(BuildContext context) {
    final palette = context.colors;
    final l10n = context.l10n;
    final disableAnimations =
        MediaQuery.maybeDisableAnimationsOf(context) ?? false;

    return Scaffold(
      backgroundColor: palette.background,
      appBar: AppBar(
        backgroundColor: palette.background.withValues(alpha: 0),
        elevation: 0,
        actions: [
          if (_currentPage < _totalPages - 1)
            TextButton(
              onPressed: _finishOnboarding,
              child: Text(l10n.onboardingSkip),
            ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            if (_saveFailed)
              Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: AppSpace.xl,
                  vertical: AppSpace.xs,
                ),
                child: AppResponsiveContainer(
                  child: Semantics(
                    liveRegion: true,
                    container: true,
                    child: EditorialCard(
                      backgroundColor: palette.warningContainer,
                      borderColor: palette.warning,
                      padding: const EdgeInsets.symmetric(
                        horizontal: AppSpace.md,
                        vertical: AppSpace.sm,
                      ),
                      child: Wrap(
                        alignment: WrapAlignment.spaceBetween,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        spacing: AppSpace.sm,
                        runSpacing: AppSpace.xs,
                        children: [
                          Text(
                            l10n.commonErrorTitle,
                            style: Theme.of(context).textTheme.bodyMedium
                                ?.copyWith(
                                  color: palette.onWarningContainer,
                                  fontWeight: FontWeight.w600,
                                ),
                          ),
                          TextButton(
                            onPressed: _continueBypassingSave,
                            child: Text(l10n.commonContinueAnyway),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            Expanded(
              child: PageView(
                controller: _pageController,
                onPageChanged: (page) => setState(() => _currentPage = page),
                children: [
                  _buildWelcomePage(context),
                  _buildAddBirthdaysPage(context),
                  _buildRemindersPage(context),
                ],
              ),
            ),
            _buildBottomControls(context, disableAnimations),
          ],
        ),
      ),
    );
  }

  Widget _buildWelcomePage(BuildContext context) {
    final palette = context.colors;
    final l10n = context.l10n;
    final textTheme = Theme.of(context).textTheme;

    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpace.xl,
          vertical: AppSpace.lg,
        ),
        child: AppResponsiveContainer(
          maxWidth: 560,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                  color: palette.primaryContainer,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.cake_rounded,
                  size: 40,
                  color: palette.primary,
                ),
              ),
              const SizedBox(height: AppSpace.xl),
              Text(
                l10n.onboardingPage1Title,
                textAlign: TextAlign.center,
                style: textTheme.headlineMedium,
              ),
              const SizedBox(height: AppSpace.md),
              Text(
                l10n.onboardingPage1Loop,
                textAlign: TextAlign.center,
                style: textTheme.bodyMedium?.copyWith(
                  fontWeight: FontWeight.w600,
                  color: palette.primary,
                ),
              ),
              const SizedBox(height: AppSpace.xxl),
              _buildFeatureRow(
                context,
                icon: Icons.timer_outlined,
                title: l10n.onboardingPage1Feature1Title,
                description: l10n.onboardingPage1Feature1Desc,
              ),
              const SizedBox(height: AppSpace.lg),
              _buildFeatureRow(
                context,
                icon: Icons.edit_note_rounded,
                title: l10n.onboardingPage1Feature2Title,
                description: l10n.onboardingPage1Feature2Desc,
              ),
              const SizedBox(height: AppSpace.lg),
              _buildFeatureRow(
                context,
                icon: Icons.shield_outlined,
                title: l10n.onboardingPage1Feature3Title,
                description: l10n.onboardingPage1Feature3Desc,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildAddBirthdaysPage(BuildContext context) {
    final palette = context.colors;
    final l10n = context.l10n;
    final textTheme = Theme.of(context).textTheme;

    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpace.xl,
          vertical: AppSpace.lg,
        ),
        child: AppResponsiveContainer(
          maxWidth: 560,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                  color: palette.surfaceAlt,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.people_alt_rounded,
                  size: 40,
                  color: palette.success,
                ),
              ),
              const SizedBox(height: AppSpace.xl),
              Text(
                l10n.onboardingPage2Title,
                textAlign: TextAlign.center,
                style: textTheme.headlineMedium,
              ),
              const SizedBox(height: AppSpace.md),
              Text(
                l10n.onboardingPage2Subtitle,
                textAlign: TextAlign.center,
                style: textTheme.bodyMedium?.copyWith(
                  color: palette.textSecondary,
                ),
              ),
              const SizedBox(height: AppSpace.xxl),
              EditorialCard(
                onTap: () => _finishTo(AppRoutes.peopleAdd),
                padding: const EdgeInsets.all(AppSpace.lg),
                child: Row(
                  children: [
                    Icon(Icons.person_add_rounded, color: palette.primary),
                    const SizedBox(width: AppSpace.lg),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            l10n.onboardingAddManualTitle,
                            style: textTheme.titleMedium?.copyWith(
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const SizedBox(height: AppSpace.xs),
                          Text(
                            l10n.onboardingAddManualSubtitle,
                            style: textTheme.bodyMedium?.copyWith(
                              color: palette.textSecondary,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: AppSpace.sm),
                    Icon(
                      Icons.arrow_forward_ios,
                      size: AppIconSize.sm,
                      color: palette.textSecondary,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: AppSpace.md),
              EditorialCard(
                onTap: () => _finishTo(AppRoutes.people),
                padding: const EdgeInsets.all(AppSpace.lg),
                child: Row(
                  children: [
                    Icon(Icons.contact_phone_rounded, color: palette.success),
                    const SizedBox(width: AppSpace.lg),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            l10n.onboardingImportContactsTitle,
                            style: textTheme.titleMedium?.copyWith(
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const SizedBox(height: AppSpace.xs),
                          Text(
                            l10n.onboardingImportContactsSubtitle,
                            style: textTheme.bodyMedium?.copyWith(
                              color: palette.textSecondary,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: AppSpace.sm),
                    Icon(
                      Icons.arrow_forward_ios,
                      size: AppIconSize.sm,
                      color: palette.textSecondary,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildRemindersPage(BuildContext context) {
    final palette = context.colors;
    final l10n = context.l10n;
    final textTheme = Theme.of(context).textTheme;

    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpace.xl,
          vertical: AppSpace.lg,
        ),
        child: AppResponsiveContainer(
          maxWidth: 560,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                  color: palette.warningContainer,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.notifications_active_rounded,
                  size: 40,
                  color: palette.onWarningContainer,
                ),
              ),
              const SizedBox(height: AppSpace.xl),
              Text(
                l10n.onboardingPage3Title,
                textAlign: TextAlign.center,
                style: textTheme.headlineMedium,
              ),
              const SizedBox(height: AppSpace.md),
              Text(
                l10n.onboardingPage3Subtitle,
                textAlign: TextAlign.center,
                style: textTheme.bodyMedium?.copyWith(
                  color: palette.textSecondary,
                ),
              ),
              const SizedBox(height: AppSpace.xxl),
              _buildReminderTimelineStep(
                context,
                days: l10n.onboardingTimeline7Days,
                label: l10n.onboardingTimeline7DaysDesc,
              ),
              const SizedBox(height: AppSpace.md),
              _buildReminderTimelineStep(
                context,
                days: l10n.onboardingTimeline2Days,
                label: l10n.onboardingTimeline2DaysDesc,
              ),
              const SizedBox(height: AppSpace.md),
              _buildReminderTimelineStep(
                context,
                days: l10n.onboardingTimelineToday,
                label: l10n.onboardingTimelineTodayDesc,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildReminderTimelineStep(
    BuildContext context, {
    required String days,
    required String label,
  }) {
    final palette = context.colors;
    final textTheme = Theme.of(context).textTheme;

    return Row(
      children: [
        Container(
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpace.md,
            vertical: AppSpace.sm,
          ),
          decoration: BoxDecoration(
            color: palette.primaryContainer,
            borderRadius: AppRadius.roundedSm,
          ),
          child: Text(
            days,
            style: textTheme.labelMedium?.copyWith(
              fontWeight: FontWeight.w700,
              color: palette.onPrimaryContainer,
            ),
          ),
        ),
        const SizedBox(width: AppSpace.md),
        Expanded(child: Text(label, style: textTheme.bodyMedium)),
      ],
    );
  }

  Widget _buildFeatureRow(
    BuildContext context, {
    required IconData icon,
    required String title,
    required String description,
  }) {
    final palette = context.colors;
    final textTheme = Theme.of(context).textTheme;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 22, color: palette.primary),
        const SizedBox(width: AppSpace.md),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: textTheme.bodyMedium?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                description,
                style: textTheme.bodySmall?.copyWith(
                  color: palette.textSecondary,
                  height: 1.3,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildBottomControls(BuildContext context, bool disableAnimations) {
    final palette = context.colors;
    final l10n = context.l10n;
    final isLastPage = _currentPage == _totalPages - 1;

    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppSpace.xl,
        AppSpace.lg,
        AppSpace.xl,
        AppSpace.xl,
      ),
      child: AppResponsiveContainer(
        child: SizedBox(
          width: double.infinity,
          child: Wrap(
            alignment: WrapAlignment.spaceBetween,
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: AppSpace.md,
            runSpacing: AppSpace.md,
            children: [
              Semantics(
                label: l10n.onboardingPageIndicatorSemantics(
                  _currentPage + 1,
                  _totalPages,
                ),
                child: ExcludeSemantics(
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: List.generate(
                      _totalPages,
                      (index) => AnimatedContainer(
                        duration: disableAnimations
                            ? Duration.zero
                            : AppDuration.fast,
                        margin: const EdgeInsets.only(right: AppSpace.sm),
                        width: _currentPage == index ? 24 : 8,
                        height: 8,
                        decoration: BoxDecoration(
                          color: _currentPage == index
                              ? palette.primary
                              : palette.border,
                          borderRadius: AppRadius.roundedSm,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
              FilledButton.icon(
                onPressed: () => _nextPage(disableAnimations),
                icon: Icon(
                  isLastPage ? Icons.check : Icons.arrow_forward,
                  size: 18,
                ),
                label: Text(
                  isLastPage ? l10n.onboardingGetStarted : l10n.onboardingNext,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
