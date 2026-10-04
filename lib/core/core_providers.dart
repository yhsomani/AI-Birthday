import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'database/app_database.dart';
import 'database/drift_repositories.dart';
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
  seedInitialDataIfEmpty(db);
  return db;
});
