/// Declarative routing configuration for AI-Birthday.
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
import 'package:ai_birthday/features/onboarding/application/onboarding_gate.dart';
import 'package:ai_birthday/features/onboarding/presentation/onboarding_screen.dart';
import 'package:ai_birthday/features/people/presentation/people_screen.dart';
import 'package:ai_birthday/features/people/presentation/person_form_screen.dart';
import 'package:ai_birthday/features/settings/presentation/settings_screen.dart';
import 'package:ai_birthday/shared/design_system/design_system.dart';
import 'app_scaffold.dart';

export 'package:ai_birthday/features/onboarding/application/onboarding_gate.dart';

/// Canonical application route paths & builders.
abstract final class AppRoutes {
  static const String onboarding = '/onboarding';
  static const String onboardingReplay = '/onboarding?replay=true';
  static const String dashboard = '/dashboard';
  static const String people = '/people';
  static const String peopleAdd = '/people/add';
  static const String calendar = '/calendar';
  static const String history = '/history';
  static const String settings = '/settings';

  static String peopleEdit(String id) => '/people/edit/$id';
  static String studioForBirthday(String birthdayId) =>
      '/message-studio/$birthdayId';
  static String studioForPerson(String personId) =>
      '/message-studio/person/$personId';
}

final GlobalKey<NavigatorState> _rootNavigatorKey = GlobalKey<NavigatorState>(
  debugLabel: 'root',
);

GoRouter createAppRouter([
  CredentialStorage? credentialStorage,
  OnboardingGate? gateOverride,
]) {
  final gate =
      gateOverride ??
      (credentialStorage == null ? null : OnboardingGate(credentialStorage));
  return GoRouter(
    navigatorKey: _rootNavigatorKey,
    initialLocation: AppRoutes.dashboard,
    // Unmatched locations get a real screen (not a blank/error page) with a
    // way back into the shell.
    errorBuilder: (context, state) => _NotFoundScreen(state),
    redirect: (context, state) async {
      if (gate == null) return null;
      final completed = await gate.isCompleted();
      final isOnboarding = state.matchedLocation == AppRoutes.onboarding;
      if (!completed && !isOnboarding) {
        return AppRoutes.onboarding;
      }
      if (completed &&
          isOnboarding &&
          state.uri.queryParameters['replay'] != 'true') {
        return AppRoutes.dashboard;
      }
      return null;
    },
    routes: [
      // First-run / Onboarding walkthrough
      GoRoute(
        path: AppRoutes.onboarding,
        parentNavigatorKey: _rootNavigatorKey,
        builder: (context, state) => OnboardingScreen(
          isReplay: state.uri.queryParameters['replay'] == 'true',
        ),
      ),

      // Modal / Detail routes mounted above the shell.
      // Specific `/message-studio/person/:personId` MUST precede
      // `/message-studio/:birthdayId` to prevent `:birthdayId` from capturing
      // `'person'` (NAV-ROUTE-COLLISION).
      GoRoute(
        path: '/message-studio/person/:personId',
        parentNavigatorKey: _rootNavigatorKey,
        builder: (context, state) {
          final personId = state.pathParameters['personId'] ?? '';
          if (personId.isEmpty) {
            return _NotFoundScreen(state);
          }
          return MessageStudioScreen(personId: personId);
        },
      ),
      GoRoute(
        path: '/message-studio/:birthdayId',
        parentNavigatorKey: _rootNavigatorKey,
        builder: (context, state) {
          final birthdayId = state.pathParameters['birthdayId'] ?? '';
          if (birthdayId.isEmpty || birthdayId == 'person') {
            return _NotFoundScreen(state);
          }
          return MessageStudioScreen(birthdayId: birthdayId);
        },
      ),
      GoRoute(
        path: AppRoutes.peopleAdd,
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
        path: AppRoutes.settings,
        parentNavigatorKey: _rootNavigatorKey,
        builder: (context, state) => const SettingsScreen(),
      ),

      // Bottom Navigation / Rail Stateful Shell
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) =>
            AppScaffold(navigationShell: navigationShell),
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.dashboard,
                builder: (context, state) => const DashboardScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.people,
                builder: (context, state) => const PeopleScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.calendar,
                builder: (context, state) => const CalendarScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.history,
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
  final gate = ref.watch(onboardingGateProvider);
  return createAppRouter(storage, gate);
});

/// Fallback for any location that matches no route: show what was asked and
/// hand the user back to the dashboard shell.
class _NotFoundScreen extends StatelessWidget {
  const _NotFoundScreen(this.state);

  final GoRouterState state;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final location = state.uri.path == '/' ? '' : state.uri.path;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.notFoundAppBarTitle)),
      body: SafeArea(
        child: AppResponsiveContainer(
          child: EditorialEmptyState(
            icon: Icons.explore_off_outlined,
            title: location.isEmpty
                ? l10n.notFoundEmptyPathTitle
                : l10n.notFoundPathTitle(location),
            message: l10n.notFoundMessage,
            action: FilledButton.icon(
              onPressed: () => context.go(AppRoutes.dashboard),
              icon: const Icon(Icons.home_outlined),
              label: Text(l10n.notFoundBackToDashboard),
            ),
          ),
        ),
      ),
    );
  }
}
