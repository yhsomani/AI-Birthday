/// Root application widget for AI-Birthday.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/app/router.dart';
import 'package:ai_birthday/app/theme/app_theme.dart';
import 'package:ai_birthday/core/core_providers.dart' as core;
import 'package:ai_birthday/features/auth/application/auth_controller.dart';
import 'package:ai_birthday/features/auth/domain/auth_state.dart';
import 'package:ai_birthday/features/reminders/application/notification_scheduler_gateway.dart';
import 'package:ai_birthday/features/reminders/application/reminder_bootstrap.dart';
import 'package:ai_birthday/features/reminders/application/reminder_providers.dart';
import 'package:ai_birthday/l10n/app_localizations.dart';
import 'package:ai_birthday/shared/design_system/design_system.dart';

class AiBirthdayApp extends ConsumerWidget {
  const AiBirthdayApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final startup = ref.watch(core.databaseStartupProvider);
    return startup.when(
      loading: () => _startupMaterialApp(
        child: const _StartupRecoveryScreen(status: _StartupStatus.checking),
      ),
      error: (error, stackTrace) => _startupMaterialApp(
        child: _StartupRecoveryScreen(
          status: _StartupStatus.failed,
          isTimeout: error is TimeoutException,
          errorType: '${error.runtimeType}',
          onRetry: () {
            ref.invalidate(core.databaseProvider);
            ref.invalidate(core.databaseStartupProvider);
          },
        ),
      ),
      data: (_) => const _ReadyAppShell(),
    );
  }
}

class _ReadyAppShell extends ConsumerStatefulWidget {
  const _ReadyAppShell();

  @override
  ConsumerState<_ReadyAppShell> createState() => _ReadyAppShellState();
}

class _ReadyAppShellState extends ConsumerState<_ReadyAppShell> {
  late final NotificationSchedulerGateway _gateway;
  late final GoRouter _router;
  String? _pendingNotificationPersonId;
  bool _drainingPending = false;

  @override
  void initState() {
    super.initState();
    _gateway = ref.read(notificationSchedulerGatewayProvider);
    _router = ref.read(routerProvider);
    _router.routerDelegate.addListener(_onRouterChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      unawaited(_initNotifications());
    });
  }

  Future<void> _initNotifications() async {
    if (!mounted) return;
    _gateway.setNotificationOpenedHandler((personId) {
      unawaited(_routeOrQueueNotification(personId));
    });
    final initialPersonId = await _gateway.getInitialNotificationPersonId();
    if (initialPersonId != null && initialPersonId.isNotEmpty) {
      await _routeOrQueueNotification(initialPersonId);
    }
  }

  Future<void> _routeOrQueueNotification(String personId) async {
    if (!mounted || personId.isEmpty) return;
    final completed = await ref.read(onboardingGateProvider).isCompleted();
    if (!mounted) return;
    if (!completed) {
      _pendingNotificationPersonId = personId;
      return;
    }
    unawaited(_router.push(AppRoutes.studioForPerson(personId)));
  }

  void _onRouterChanged() {
    final pending = _pendingNotificationPersonId;
    if (pending == null || _drainingPending || !mounted) return;
    _drainingPending = true;
    unawaited(_drainPendingNotification(pending));
  }

  Future<void> _drainPendingNotification(String personId) async {
    try {
      final completed = await ref.read(onboardingGateProvider).isCompleted();
      if (!mounted || !completed) return;
      final location = _router.routeInformationProvider.value.uri.path;
      if (location == AppRoutes.onboarding) return;
      if (_pendingNotificationPersonId == personId) {
        _pendingNotificationPersonId = null;
        unawaited(_router.push(AppRoutes.studioForPerson(personId)));
      }
    } finally {
      _drainingPending = false;
    }
  }

  @override
  void dispose() {
    _router.routerDelegate.removeListener(_onRouterChanged);
    _gateway.clearNotificationOpenedHandler();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final router = ref.watch(routerProvider);

    // Eagerly start the subscription pipeline at launch so a returning
    // subscriber is restored/verified without opening a specific screen.
    ref.watch(entitlementProvider);
    ref.listen<AsyncValue<AuthState>>(authControllerProvider, (previous, next) {
      final wasSignedIn = previous?.value?.isSignedIn ?? false;
      final isSignedIn = next.value?.isSignedIn ?? false;
      if (isSignedIn && !wasSignedIn) {
        ref.read(subscriptionNotifierProvider.notifier).restorePurchases();
      }
    });
    ref.watch(authControllerProvider);
    ref.watch(jobWorkerProvider);
    ref.watch(draftPruneProvider);
    ref.watch(reminderBootstrapProvider);

    return MaterialApp.router(
      onGenerateTitle: (context) => context.l10n.appTitle,
      debugShowCheckedModeBanner: false,
      routerConfig: router,
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      themeMode: ThemeMode.system,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
    );
  }
}

enum _StartupStatus { checking, failed }

MaterialApp _startupMaterialApp({required Widget child}) => MaterialApp(
  onGenerateTitle: (context) => context.l10n.appTitle,
  debugShowCheckedModeBanner: false,
  theme: AppTheme.light,
  darkTheme: AppTheme.dark,
  themeMode: ThemeMode.system,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: child,
);

class _StartupRecoveryScreen extends StatelessWidget {
  const _StartupRecoveryScreen({
    required this.status,
    this.isTimeout = false,
    this.errorType,
    this.onRetry,
  });

  final _StartupStatus status;
  final bool isTimeout;
  final String? errorType;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final palette = context.colors;
    final textTheme = Theme.of(context).textTheme;
    final failed = status == _StartupStatus.failed;
    final diagnostic = !failed
        ? null
        : isTimeout
        ? l10n.startupRecoveryTimeoutDiagnostic
        : l10n.startupRecoveryErrorDiagnostic(errorType ?? 'Unknown');

    final content = Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(
          failed ? Icons.storage_rounded : Icons.hourglass_top_rounded,
          size: AppIconSize.lg,
          color: failed ? palette.danger : palette.primary,
        ),
        const SizedBox(height: AppSpace.lgPlus),
        Text(
          failed ? l10n.startupRecoveryTitle : l10n.startupCheckingTitle,
          textAlign: TextAlign.center,
          style: textTheme.headlineSmall,
        ),
        const SizedBox(height: AppSpace.md),
        Text(
          failed ? l10n.startupRecoveryBody : l10n.startupCheckingBody,
          textAlign: TextAlign.center,
          style: textTheme.bodyMedium?.copyWith(color: palette.textSecondary),
        ),
        if (diagnostic != null) ...[
          const SizedBox(height: AppSpace.lg),
          Text(
            diagnostic,
            textAlign: TextAlign.center,
            style: textTheme.bodySmall?.copyWith(color: palette.textSecondary),
          ),
        ],
        if (failed) ...[
          const SizedBox(height: AppSpace.xl),
          FilledButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
            label: Text(l10n.startupRetryButton),
          ),
          const SizedBox(height: AppSpace.md),
          Text(
            l10n.startupRecoveryHint,
            textAlign: TextAlign.center,
            style: textTheme.bodySmall?.copyWith(color: palette.textSecondary),
          ),
        ] else
          const Padding(
            padding: EdgeInsets.only(top: AppSpace.xl),
            child: CircularProgressIndicator(),
          ),
      ],
    );

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(AppSpace.xl),
            child: AppResponsiveContainer(
              maxWidth: 520,
              child: EditorialCard(
                padding: const EdgeInsets.all(AppSpace.xl),
                child: failed
                    ? Semantics(
                        liveRegion: true,
                        container: true,
                        child: content,
                      )
                    : content,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
