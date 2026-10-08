/// Root application widget for AI-Birthday (SSOT §3, §24).
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/app/router.dart';
import 'package:ai_birthday/app/theme/app_theme.dart';
import 'package:ai_birthday/app/theme/theme_mode_provider.dart';
import 'package:ai_birthday/features/auth/application/auth_controller.dart';
import 'package:ai_birthday/features/auth/domain/auth_state.dart';
import 'package:ai_birthday/l10n/app_localizations.dart';

class AiBirthdayApp extends ConsumerWidget {
  const AiBirthdayApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);

    // Eagerly start the subscription pipeline at launch so a returning
    // subscriber is restored/verified without opening a specific screen
    // (audit P1-2). Harmless when IAP/auth are unavailable.
    ref.watch(entitlementProvider);

    // A session that first materializes after sign-in must still be checked:
    // trigger restore the moment authentication completes.
    ref.listen<AsyncValue<AuthState>>(authControllerProvider, (previous, next) {
      final wasSignedIn = previous?.value?.isSignedIn ?? false;
      final isSignedIn = next.value?.isSignedIn ?? false;
      if (isSignedIn && !wasSignedIn) {
        ref.read(subscriptionNotifierProvider.notifier).restorePurchases();
      }
    });
    ref.watch(authControllerProvider);

    // Start the durable background-job worker at boot: crash recovery for
    // jobs left running by a previous process, then resume anything queued
    // (background-jobs audit).
    ref.watch(jobWorkerProvider);

    return MaterialApp.router(
      title: 'AI-Birthday',
      debugShowCheckedModeBanner: false,
      routerConfig: router,
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      themeMode: ref.watch(themeModeProvider),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
    );
  }
}
