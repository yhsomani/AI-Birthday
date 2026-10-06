import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/shared/design_system/design_system.dart';

/// Guided First-Run Onboarding Flow (SSOT §3, §14, §15).
///
/// Introduces the 5-step loop (Remember → Prepare → Personalize → Review → Send),
/// establishes the local-first privacy promise, and gives clear on-ramps to
/// add birthdays or enable reminders.
class OnboardingScreen extends ConsumerStatefulWidget {
  const OnboardingScreen({super.key});

  @override
  ConsumerState<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends ConsumerState<OnboardingScreen> {
  final PageController _pageController = PageController();
  int _currentPage = 0;

  static const int _totalPages = 3;

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  Future<void> _finishOnboarding() async {
    HapticFeedback.lightImpact();
    await ref.read(credentialStorageProvider).setCompletedOnboarding(true);
    if (!mounted) return;
    if (context.canPop()) {
      context.pop();
    } else {
      context.go('/dashboard');
    }
  }

  void _nextPage() {
    HapticFeedback.lightImpact();
    if (_currentPage < _totalPages - 1) {
      _pageController.nextPage(
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
      );
    } else {
      _finishOnboarding();
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        actions: [
          if (_currentPage < _totalPages - 1)
            TextButton(onPressed: _finishOnboarding, child: const Text('Skip')),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: PageView(
                controller: _pageController,
                onPageChanged: (page) => setState(() => _currentPage = page),
                children: [
                  _buildWelcomePage(theme),
                  _buildAddBirthdaysPage(theme),
                  _buildRemindersPage(theme),
                ],
              ),
            ),
            _buildBottomControls(theme),
          ],
        ),
      ),
    );
  }

  Widget _buildWelcomePage(ThemeData theme) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 80,
            height: 80,
            decoration: BoxDecoration(
              color: AppColors.primaryTerracottaContainer,
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.cake_rounded,
              size: 40,
              color: AppColors.primaryTerracotta,
            ),
          ),
          const SizedBox(height: 24),
          Text(
            'Never miss a birthday that matters.',
            textAlign: TextAlign.center,
            style: theme.textTheme.headlineMedium?.copyWith(
              fontWeight: FontWeight.bold,
              letterSpacing: -0.5,
            ),
          ),
          const SizedBox(height: 12),
          Text(
            'Remember → Prepare → Personalize → Review → Send',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: AppColors.primaryTerracotta,
            ),
          ),
          const SizedBox(height: 32),
          _buildFeatureRow(
            icon: Icons.timer_outlined,
            title: 'Always Prepared Ahead',
            description:
                'Get smart reminders days in advance so you can craft thoughtful messages without rushing.',
          ),
          const SizedBox(height: 16),
          _buildFeatureRow(
            icon: Icons.edit_note_rounded,
            title: 'Thoughtful, Personal Greetings',
            description:
                'Generate warm drafts tailored to your shared memories and preferred tone.',
          ),
          const SizedBox(height: 16),
          _buildFeatureRow(
            icon: Icons.shield_outlined,
            title: 'Local by Default',
            description:
                'Your birthday data stays on this device unless you explicitly choose cloud backup or an external service.',
          ),
        ],
      ),
    );
  }

  Widget _buildAddBirthdaysPage(ThemeData theme) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 80,
            height: 80,
            decoration: BoxDecoration(
              color: AppColors.accentForestContainer,
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.people_alt_rounded,
              size: 40,
              color: AppColors.accentForest,
            ),
          ),
          const SizedBox(height: 24),
          Text(
            'Bring in your birthdays',
            textAlign: TextAlign.center,
            style: theme.textTheme.headlineMedium?.copyWith(
              fontWeight: FontWeight.bold,
              letterSpacing: -0.5,
            ),
          ),
          const SizedBox(height: 12),
          Text(
            'Add your first birthday now or explore the command center.',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 14,
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 32),
          Card(
            child: ListTile(
              leading: const Icon(
                Icons.person_add_rounded,
                color: AppColors.primaryTerracotta,
              ),
              title: const Text(
                'Add Birthday Manually',
                style: TextStyle(fontWeight: FontWeight.w600),
              ),
              subtitle: const Text('Enter name, date, relationship, and tone.'),
              trailing: const Icon(Icons.arrow_forward_ios, size: 16),
              onTap: () {
                _finishOnboarding();
                context.push('/people/add');
              },
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: ListTile(
              leading: const Icon(
                Icons.contact_phone_rounded,
                color: AppColors.accentForest,
              ),
              title: const Text(
                'Import from Phone Contacts',
                style: TextStyle(fontWeight: FontWeight.w600),
              ),
              subtitle: const Text(
                'Quickly scan phone contacts to import existing birthdays.',
              ),
              trailing: const Icon(Icons.arrow_forward_ios, size: 16),
              onTap: () {
                _finishOnboarding();
                context.push('/people');
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildRemindersPage(ThemeData theme) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 80,
            height: 80,
            decoration: BoxDecoration(
              color: AppColors.accentAmberContainer,
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.notifications_active_rounded,
              size: 40,
              color: AppColors.accentAmber,
            ),
          ),
          const SizedBox(height: 24),
          Text(
            'Prepared ahead of time',
            textAlign: TextAlign.center,
            style: theme.textTheme.headlineMedium?.copyWith(
              fontWeight: FontWeight.bold,
              letterSpacing: -0.5,
            ),
          ),
          const SizedBox(height: 12),
          Text(
            'AI-Birthday schedules gentle leads so greetings can be reviewed without stress.',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 14,
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 32),
          _buildReminderTimelineStep(
            days: '7 Days',
            label: 'Upcoming reminder: Plan gifts or special messages.',
          ),
          const SizedBox(height: 14),
          _buildReminderTimelineStep(
            days: '2 Days',
            label:
                'Draft ready: Personalized message is prepared for your review.',
          ),
          const SizedBox(height: 14),
          _buildReminderTimelineStep(
            days: 'Today',
            label:
                'Send day: One tap handoff to WhatsApp with your confirmed greeting.',
          ),
        ],
      ),
    );
  }

  Widget _buildReminderTimelineStep({
    required String days,
    required String label,
  }) {
    return Row(
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
          decoration: BoxDecoration(
            color: AppColors.primaryTerracottaContainer,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Text(
            days,
            style: const TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.bold,
              color: AppColors.primaryTerracotta,
            ),
          ),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Text(label, style: const TextStyle(fontSize: 13, height: 1.3)),
        ),
      ],
    );
  }

  Widget _buildFeatureRow({
    required IconData icon,
    required String title,
    required String description,
  }) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 22, color: AppColors.primaryTerracotta),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                description,
                style: TextStyle(
                  fontSize: 12,
                  color: Colors.grey[600],
                  height: 1.3,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildBottomControls(ThemeData theme) {
    final isLastPage = _currentPage == _totalPages - 1;

    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 16, 24, 24),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Row(
            children: List.generate(
              _totalPages,
              (index) => AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                margin: const EdgeInsets.only(right: 6),
                width: _currentPage == index ? 24 : 8,
                height: 8,
                decoration: BoxDecoration(
                  color: _currentPage == index
                      ? AppColors.primaryTerracotta
                      : theme.colorScheme.outlineVariant,
                  borderRadius: BorderRadius.circular(4),
                ),
              ),
            ),
          ),
          FilledButton.icon(
            onPressed: _nextPage,
            icon: Icon(
              isLastPage ? Icons.check : Icons.arrow_forward,
              size: 18,
            ),
            label: Text(isLastPage ? 'Get Started' : 'Next'),
          ),
        ],
      ),
    );
  }
}
