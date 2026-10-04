<<<<<<< HEAD
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'router.dart';
import 'theme/app_theme.dart';

/// The AI-Birthday application root.
=======
/// Root application widget for AI-Birthday (SSOT §3, §24).
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:ai_birthday/app/providers.dart';
import 'package:ai_birthday/app/router.dart';
import 'package:ai_birthday/app/theme/app_theme.dart';

>>>>>>> e8906b8fe21fa6f7bcb6466936b85c0d59161f88
class AiBirthdayApp extends ConsumerWidget {
  const AiBirthdayApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
<<<<<<< HEAD
    final router = ref.watch(routerProvider);
    return MaterialApp.router(
      title: 'AI-Birthday',
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      themeMode: ThemeMode.system,
      routerConfig: router,
      debugShowCheckedModeBanner: false,
=======
    final themeMode = ref.watch(themeModeProvider);

    return MaterialApp.router(
      title: 'AI-Birthday',
      debugShowCheckedModeBanner: false,
      routerConfig: appRouter,
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      themeMode: themeMode,
>>>>>>> e8906b8fe21fa6f7bcb6466936b85c0d59161f88
    );
  }
}
