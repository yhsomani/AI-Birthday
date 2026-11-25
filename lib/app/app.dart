/// Root application widget for AI-Birthday (legacy SSOT §3, §24 (PRODUCT.md §16.7)).
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/app/router.dart';
import 'package:ai_birthday/core/core_providers.dart' as core;
import 'package:ai_birthday/app/theme/app_theme.dart';
import 'package:ai_birthday/features/auth/application/auth_controller.dart';
import 'package:ai_birthday/features/auth/domain/auth_state.dart';
import 'package:ai_birthday/features/reminders/application/reminder_bootstrap.dart';
import 'package:ai_birthday/l10n/app_localizations.dart';

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
          diagnostic: error is TimeoutException
              ? 'Database check exceeded 10 seconds.'
              : 'Database initialization failed (${error.runtimeType}).',
          onRetry: () {
            ref.invalidate(core.databaseProvider);
            ref.invalidate(core.databaseStartupProvider);
          },
        ),
      ),
      data: (_) => _buildReadyApp(ref),
    );
  }

  Widget _buildReadyApp(WidgetRef ref) {
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
      title: 'AI-Birthday',
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
  title: 'AI-Birthday',
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
    this.diagnostic,
    this.onRetry,
  });

  final _StartupStatus status;
  final String? diagnostic;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final failed = status == _StartupStatus.failed;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(28),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 520),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    failed
                        ? Icons.storage_rounded
                        : Icons.hourglass_top_rounded,
                    size: 52,
                    color: failed
                        ? Theme.of(context).colorScheme.error
                        : Theme.of(context).colorScheme.primary,
                  ),
                  const SizedBox(height: 20),
                  Text(
                    failed
                        ? 'Local data recovery needed'
                        : 'Preparing your local data…',
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 12),
                  Text(
                    failed
                        ? 'AI-Birthday stopped rather than risk opening or changing unreadable data. No cloud upload or local write has been attempted.'
                        : 'AI-Birthday is checking the on-device database. This check is limited to 10 seconds.',
                    textAlign: TextAlign.center,
                  ),
                  if (diagnostic != null) ...[
                    const SizedBox(height: 16),
                    Text(
                      diagnostic!,
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                  if (failed) ...[
                    const SizedBox(height: 24),
                    FilledButton.icon(
                      onPressed: onRetry,
                      icon: const Icon(Icons.refresh),
                      label: const Text('Retry database initialization'),
                    ),
                    const SizedBox(height: 10),
                    Text(
                      'If this repeats, restart the device, ensure storage space is available, and preserve app data. Reinstalling may remove it.',
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ] else
                    const Padding(
                      padding: EdgeInsets.only(top: 24),
                      child: CircularProgressIndicator(),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
