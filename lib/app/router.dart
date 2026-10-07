/// Declarative routing configuration for AI-Birthday (SSOT §3, §15, §16, §24, §28).
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
import 'app_scaffold.dart';

final GlobalKey<NavigatorState> _rootNavigatorKey = GlobalKey<NavigatorState>(
  debugLabel: 'root',
);

GoRouter createAppRouter([CredentialStorage? credentialStorage]) {
  return GoRouter(
    navigatorKey: _rootNavigatorKey,
    initialLocation: '/dashboard',
    redirect: (context, state) async {
      if (credentialStorage == null) return null;
      final completed = await credentialStorage.hasCompletedOnboarding();
      final isOnboarding = state.matchedLocation == '/onboarding';
      if (!completed && !isOnboarding) {
        return '/onboarding';
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
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/settings',
                builder: (context, state) => const SettingsScreen(),
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
