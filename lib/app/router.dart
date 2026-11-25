/// Declarative routing configuration for AI-Birthday (legacy SSOT §3, §15, §16, §24, §28 (PRODUCT.md §16.7)).
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/calendar/presentation/calendar_screen.dart';
import 'package:ai_birthday/features/dashboard/presentation/dashboard_screen.dart';
import 'package:ai_birthday/features/history/presentation/history_screen.dart';
import 'package:ai_birthday/features/message_studio/presentation/message_studio_screen.dart';
import 'package:ai_birthday/features/onboarding/presentation/onboarding_screen.dart';
import 'package:ai_birthday/features/people/presentation/people_screen.dart';
import 'package:ai_birthday/features/people/presentation/person_form_screen.dart';
import 'package:ai_birthday/features/settings/presentation/settings_screen.dart';
import 'package:ai_birthday/shared/design_system/design_system.dart';
import 'app_scaffold.dart';

final GlobalKey<NavigatorState> _rootNavigatorKey = GlobalKey<NavigatorState>(
  debugLabel: 'root',
);

/// In-memory cache of the onboarding flag so navigation does not hit secure
/// storage every time.
///
/// Only a positive result is cached (completion never reverts), so finishing
/// onboarding is picked up by the very next redirect. A read *failure* is not
/// "not onboarded": the user is let through and the read retried later,
/// instead of being trapped in onboarding by a flaky Keystore.
class OnboardingGate {
  OnboardingGate(this._storage);

  final CredentialStorage _storage;
  bool _completed = false;

  Future<bool> isCompleted() async {
    if (_completed) return true;
    try {
      _completed = await _storage.hasCompletedOnboarding();
    } catch (_) {
      return true;
    }
    return _completed;
  }
}

GoRouter createAppRouter([CredentialStorage? credentialStorage]) {
  final gate = credentialStorage == null
      ? null
      : OnboardingGate(credentialStorage);
  return GoRouter(
    navigatorKey: _rootNavigatorKey,
    initialLocation: '/dashboard',
    // Unmatched locations get a real screen (not a blank/error page) with a
    // way back into the shell.
    errorBuilder: (context, state) => _NotFoundScreen(state),
    redirect: (context, state) async {
      if (gate == null) return null;
      final completed = await gate.isCompleted();
      final isOnboarding = state.matchedLocation == '/onboarding';
      if (!completed && !isOnboarding) {
        return '/onboarding';
      }
      if (completed &&
          isOnboarding &&
          state.uri.queryParameters['replay'] != 'true') {
        return '/dashboard';
      }
      return null;
    },
    routes: [
      // First-run / Onboarding walkthrough
      GoRoute(
        path: '/onboarding',
        parentNavigatorKey: _rootNavigatorKey,
        builder: (context, state) => const OnboardingScreen(),
      ),

      // Modal / Detail routes mounted above the shell
      GoRoute(
        path: '/message-studio/:birthdayId',
        parentNavigatorKey: _rootNavigatorKey,
        builder: (context, state) {
          final birthdayId = state.pathParameters['birthdayId'] ?? '';
          return MessageStudioScreen(birthdayId: birthdayId);
        },
      ),
      GoRoute(
        path: '/message-studio/person/:personId',
        parentNavigatorKey: _rootNavigatorKey,
        builder: (context, state) {
          final personId = state.pathParameters['personId'] ?? '';
          return MessageStudioScreen(personId: personId);
        },
      ),
      GoRoute(
        path: '/people/add',
        parentNavigatorKey: _rootNavigatorKey,
        builder: (context, state) => const PersonFormScreen(),
      ),
      GoRoute(
        path: '/people/edit/:id',
        parentNavigatorKey: _rootNavigatorKey,
        builder: (context, state) =>
            PersonFormScreen(personId: state.pathParameters['id']),
      ),
      GoRoute(
        path: '/settings',
        parentNavigatorKey: _rootNavigatorKey,
        builder: (context, state) => const SettingsScreen(),
      ),

      // Bottom Navigation Stateful Shell
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) =>
            AppScaffold(navigationShell: navigationShell),
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/dashboard',
                builder: (context, state) => const DashboardScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/people',
                builder: (context, state) => const PeopleScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/calendar',
                builder: (context, state) => const CalendarScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/history',
                builder: (context, state) => const HistoryScreen(),
              ),
            ],
          ),
        ],
      ),
    ],
  );
}

/// Riverpod provider for GoRouter instance with onboarding guard.
final routerProvider = Provider<GoRouter>((ref) {
  final storage = ref.watch(credentialStorageProvider);
  return createAppRouter(storage);
});

/// Fallback for any location that matches no route: show what was asked and
/// hand the user back to the dashboard shell (they can return to their tab).
class _NotFoundScreen extends StatelessWidget {
  const _NotFoundScreen(this.state);

  final GoRouterState state;

  @override
  Widget build(BuildContext context) {
    final location = state.uri.path == '/' ? '' : state.uri.path;
    return Scaffold(
      appBar: AppBar(title: const Text('Screen not found')),
      body: SafeArea(
        child: EmptyState(
          icon: Icons.explore_off_outlined,
          title: location.isEmpty
              ? 'That screen does not exist.'
              : 'No screen matches "$location".',
          message: 'Let\'s get you back to your celebrations.',
          action: FilledButton.icon(
            onPressed: () => context.go('/dashboard'),
            icon: const Icon(Icons.home_outlined),
            label: const Text('Back to dashboard'),
          ),
        ),
      ),
    );
  }
}
