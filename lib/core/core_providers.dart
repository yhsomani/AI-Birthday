import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'database/app_database.dart';
import 'logging/app_logger.dart';

/// Application-wide providers.
///
/// Override these in tests with a recording logger and an in-memory database.

/// Sanitized structured logger.
final loggerProvider = Provider<AppLogger>((ref) {
  return ConsoleAppLogger(level: LogLevel.info);
});

/// Operational on-device database (Drift/SQLite). Opened lazily.
final databaseProvider = Provider<AppDatabase>((ref) {
  final db = AppDatabase.open();
  ref.onDispose(db.close);
  return db;
});

/// Bounded startup preflight. A Drift connection is lazy, so opening the
/// object alone does not prove SQLite is usable. The first real query is
/// allowed ten seconds; failure is surfaced by the app recovery shell instead
/// of escaping through the Flutter framework error handler.
final databaseStartupProvider = FutureProvider<void>((ref) async {
  final db = ref.watch(databaseProvider);
  await db.customSelect('SELECT 1').get().timeout(const Duration(seconds: 10));
});
