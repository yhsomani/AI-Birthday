/// Declarative routing configuration for AI-Birthday (SSOT §3, §15, §16, §24, §28).
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:ai_birthday/features/calendar/presentation/calendar_screen.dart';
import 'package:ai_birthday/features/dashboard/presentation/dashboard_screen.dart';
import 'package:ai_birthday/features/history/presentation/history_screen.dart';
import 'package:ai_birthday/features/message_studio/presentation/message_studio_screen.dart';
import 'package:ai_birthday/features/people/presentation/people_screen.dart';
import 'package:ai_birthday/features/people/presentation/person_form_screen.dart';
import 'package:ai_birthday/features/settings/presentation/settings_screen.dart';
import 'app_scaffold.dart';

final GlobalKey<NavigatorState> _rootNavigatorKey = GlobalKey<NavigatorState>(
  debugLabel: 'root',
);

final appRouter = GoRouter(
  navigatorKey: _rootNavigatorKey,
  initialLocation: '/dashboard',
  routes: [
    // Redirect aliases to canonical destinations
    GoRoute(path: '/', redirect: (_, _) => '/dashboard'),
    GoRoute(path: '/home', redirect: (_, _) => '/dashboard'),
    GoRoute(path: '/birthdays', redirect: (_, _) => '/people'),

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

/// Riverpod provider for GoRouter instance.
final routerProvider = Provider<GoRouter>((ref) => appRouter);
