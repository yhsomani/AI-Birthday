/// Root application widget for AI-Birthday (SSOT §3, §24).
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:ai_birthday/app/router.dart';
import 'package:ai_birthday/app/theme/app_theme.dart';

class AiBirthdayApp extends ConsumerWidget {
  const AiBirthdayApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);

    return MaterialApp.router(
      title: 'AI-Birthday',
      debugShowCheckedModeBanner: false,
      routerConfig: router,
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      themeMode: ThemeMode.system,
    );
  }
}
