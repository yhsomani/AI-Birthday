import 'package:drift/drift.dart';

/// Suppresses drift's debug-only "multiple databases" heuristic.
///
/// Performance-count scenarios open more than one in-memory [AppDatabase] in
/// the same test process, each with its own interceptor/executor — they never
/// share an executor, so the warning would be a false positive.
void allowMultipleInMemoryDatabases() {
  driftRuntimeOptions.dontWarnAboutMultipleDatabases = true;
}

/// Counts SQL statements executed through a drift [QueryExecutor].
///
/// Used as regression guards for N+1 fixes: construct the database as
/// `AppDatabase(NativeDatabase.memory().interceptWith(interceptor))` and assert
/// on the statement-category counters after an operation.
class CountingQueryInterceptor extends QueryInterceptor {
  int selects = 0;
  int inserts = 0;
  int updates = 0;
  int deletes = 0;

  @override
  Future<List<Map<String, Object?>>> runSelect(
    QueryExecutor executor,
    String statement,
    List<Object?> args,
  ) {
    selects++;
    return super.runSelect(executor, statement, args);
  }

  @override
  Future<int> runInsert(
    QueryExecutor executor,
    String statement,
    List<Object?> args,
  ) {
    inserts++;
    return super.runInsert(executor, statement, args);
  }

  @override
  Future<int> runUpdate(
    QueryExecutor executor,
    String statement,
    List<Object?> args,
  ) {
    updates++;
    return super.runUpdate(executor, statement, args);
  }

  @override
  Future<int> runDelete(
    QueryExecutor executor,
    String statement,
    List<Object?> args,
  ) {
    deletes++;
    return super.runDelete(executor, statement, args);
  }
}