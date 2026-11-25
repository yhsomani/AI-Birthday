import 'package:firebase_app_check/firebase_app_check.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:timezone/data/latest_all.dart' as tz_data;

import 'app/app.dart';
import 'core/config/firebase_config.dart';
import 'core/core_providers.dart' as core;
import 'core/logging/app_logger.dart';
import 'core/logging/global_error_handlers.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final logger = ConsoleAppLogger(
    level: kReleaseMode ? LogLevel.warning : LogLevel.info,
  );
  installGlobalErrorHandlers(logger);
  tz_data.initializeTimeZones();
  await bootstrapFirebase(logger);
  runApp(
    ProviderScope(
      overrides: [core.loggerProvider.overrideWithValue(logger)],
      child: const AiBirthdayApp(),
    ),
  );
}

Future<void> _initializeFirebaseApp() => Firebase.initializeApp(
  options: const FirebaseOptions(
    apiKey: FirebaseConfig.webApiKey,
    appId: FirebaseConfig.androidAppId,
    messagingSenderId: FirebaseConfig.projectNumber,
    projectId: FirebaseConfig.projectId,
  ),
);

Future<void> _activateAppCheck() async {
  await FirebaseAppCheck.instance.activate(
    // Debug builds cannot pass Play Integrity; use the debug provider there.
    providerAndroid: kDebugMode
        ? const AndroidDebugProvider()
        : const AndroidPlayIntegrityProvider(),
  );
  await FirebaseAppCheck.instance.setTokenAutoRefreshEnabled(true);
}

/// Initialises Firebase and App Check. Failures are logged (never swallowed
/// silently) and never block startup: local people, birthdays, reminders and
/// manual drafts stay usable while cloud capabilities fail closed. Returns
/// whether both steps succeeded.
@visibleForTesting
Future<bool> bootstrapFirebase(
  AppLogger logger, {
  Future<void> Function()? initializeApp,
  Future<void> Function()? activateAppCheck,
}) async {
  try {
    await (initializeApp ?? _initializeFirebaseApp)();
  } catch (error, stackTrace) {
    logger.warning(
      'Bootstrap',
      'Firebase initialization failed; cloud features are unavailable.',
      error: error,
      stackTrace: stackTrace,
      errorCategory: 'firebase_init',
    );
    return false;
  }
  try {
    await (activateAppCheck ?? _activateAppCheck)();
  } catch (error, stackTrace) {
    logger.warning(
      'Bootstrap',
      'App Check activation failed; purchase verification is unavailable.',
      error: error,
      stackTrace: stackTrace,
      errorCategory: 'app_check_init',
    );
    return false;
  }
  return true;
}
