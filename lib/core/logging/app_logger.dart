/// Sanitized, structured logging for AI-Birthday.
///
/// Observability contract (SSOT / SECURITY / CODE_STYLE):
/// - allowed: operation id, duration, provider type, error category,
///   success/failure, non-sensitive entity ids, sanitized error codes.
/// - never logged: API credentials, access tokens, message bodies, phone
///   numbers, private notes, or any sensitive personal content.
library;

enum LogLevel { verbose, debug, info, warning, error }

/// A single sanitized log record.
class LogRecord {
  const LogRecord({
    required this.level,
    required this.category,
    required this.message,
    this.operationId,
    this.params = const {},
    this.errorCategory,
    this.durationMs,
    this.timestamp,
    this.error,
    this.stackTrace,
  });

  final LogLevel level;
  final String category;
  final String message;
  final String? operationId;
  final Map<String, Object?> params;
  final String? errorCategory;
  final int? durationMs;
  final DateTime? timestamp;
  final Object? error;
  final StackTrace? stackTrace;

  bool get isError => level == LogLevel.error;
}

/// Sensitive parameter key fragments that must never appear in output.
const Set<String> _sensitiveKeyFragments = {
  'credential',
  'secret',
  'token',
  'apikey',
  'api_key',
  'api-key',
  'password',
  'key',
  'phone',
  'phonenumber',
  'phone_number',
  'address',
};

/// Pre-normalized sensitive key fragments for faster redaction checking.
final Set<String> _normalizedSensitiveKeyFragments = _sensitiveKeyFragments
    .map((f) => f.toLowerCase().replaceAll('_', '').replaceAll('-', ''))
    .toSet();

/// Redacts values whose key suggests sensitive content.
Object? _redactValue(String key, Object? value) {
  final normalized = key.toLowerCase().replaceAll('_', '').replaceAll('-', '');
  for (final fragment in _normalizedSensitiveKeyFragments) {
    if (normalized.contains(fragment)) {
      return '[REDACTED]';
    }
  }
  return value;
}

/// Abstract application logger.
///
/// Widgets and services depend on [AppLogger], never on a concrete sink, so
/// tests can substitute a recording implementation and assert that no
/// sensitive content is ever emitted.
abstract interface class AppLogger {
  bool get isLevelEnabled;
  LogLevel get level;

  void verbose(String category, String message, {String? operationId, Map<String, Object?> params});
  void debug(String category, String message, {String? operationId, Map<String, Object?> params});
  void info(String category, String message, {String? operationId, Map<String, Object?> params});
  void warning(String category, String message, {String? operationId, Map<String, Object?> params, Object? error, StackTrace? stackTrace, String? errorCategory});
  void error(String category, String message, {String? operationId, Map<String, Object?> params, Object? error, StackTrace? stackTrace, String? errorCategory});

  /// Returns a logger bound to [operationId] so all entries share an id.
  AppLogger forOperation(String operationId);
}

/// Default logger that writes sanitized records to `debugPrint`.
class ConsoleAppLogger implements AppLogger {
  const ConsoleAppLogger({this.level = LogLevel.info});

  @override
  final LogLevel level;

  @override
  bool get isLevelEnabled => true;

  Map<String, Object?> _sanitize(Map<String, Object?> params) =>
      params.map((key, value) => MapEntry(key, _redactValue(key, value)));

  void _emit(LogRecord record) {
    if (record.level.index < level.index) return;
    final params = _sanitize(record.params);
    final extra = params.isEmpty
        ? ''
        : ' ${params.entries.map((e) => '${e.key}=${e.value}').join(' ')}';
    final opacity = record.operationId == null ? '' : ' [op:${record.operationId}]';
    // ignore: avoid_print
    print('[${record.level.name}] ${record.category}$opacity: ${record.message}$extra');
  }

  void _log(LogRecord record) => _emit(record);

  @override
  void verbose(String category, String message, {String? operationId, Map<String, Object?> params = const {}}) =>
      _log(LogRecord(level: LogLevel.verbose, category: category, message: message, operationId: operationId, params: params));

  @override
  void debug(String category, String message, {String? operationId, Map<String, Object?> params = const {}}) =>
      _log(LogRecord(level: LogLevel.debug, category: category, message: message, operationId: operationId, params: params));

  @override
  void info(String category, String message, {String? operationId, Map<String, Object?> params = const {}}) =>
      _log(LogRecord(level: LogLevel.info, category: category, message: message, operationId: operationId, params: params));

  @override
  void warning(String category, String message, {String? operationId, Map<String, Object?> params = const {}, Object? error, StackTrace? stackTrace, String? errorCategory}) =>
      _log(LogRecord(level: LogLevel.warning, category: category, message: message, operationId: operationId, params: params, error: error, stackTrace: stackTrace, errorCategory: errorCategory));

  @override
  void error(String category, String message, {String? operationId, Map<String, Object?> params = const {}, Object? error, StackTrace? stackTrace, String? errorCategory}) =>
      _log(LogRecord(level: LogLevel.error, category: category, message: message, operationId: operationId, params: params, error: error, stackTrace: stackTrace, errorCategory: errorCategory));

  @override
  AppLogger forOperation(String operationId) => _OperationBoundLogger(this, operationId);
}

class _OperationBoundLogger implements AppLogger {
  const _OperationBoundLogger(this._inner, this._operationId);

  final AppLogger _inner;
  final String _operationId;

  @override
  LogLevel get level => _inner.level;

  @override
  bool get isLevelEnabled => _inner.isLevelEnabled;

  @override
  void verbose(String category, String message, {String? operationId, Map<String, Object?> params = const {}}) =>
      _inner.verbose(category, message, operationId: _operationId, params: params);

  @override
  void debug(String category, String message, {String? operationId, Map<String, Object?> params = const {}}) =>
      _inner.debug(category, message, operationId: _operationId, params: params);

  @override
  void info(String category, String message, {String? operationId, Map<String, Object?> params = const {}}) =>
      _inner.info(category, message, operationId: _operationId, params: params);

  @override
  void warning(String category, String message, {String? operationId, Map<String, Object?> params = const {}, Object? error, StackTrace? stackTrace, String? errorCategory}) =>
      _inner.warning(category, message, operationId: _operationId, params: params, error: error, stackTrace: stackTrace, errorCategory: errorCategory);

  @override
  void error(String category, String message, {String? operationId, Map<String, Object?> params = const {}, Object? error, StackTrace? stackTrace, String? errorCategory}) =>
      _inner.error(category, message, operationId: _operationId, params: params, error: error, stackTrace: stackTrace, errorCategory: errorCategory);

  @override
  AppLogger forOperation(String operationId) => _inner.forOperation(operationId);
}

/// No-op logger for tests and non-diagnostic environments.
class NoopLogger implements AppLogger {
  const NoopLogger();

  @override
  LogLevel get level => LogLevel.verbose;

  @override
  bool get isLevelEnabled => false;

  @override
  void verbose(String category, String message, {String? operationId, Map<String, Object?> params = const {}}) {}

  @override
  void debug(String category, String message, {String? operationId, Map<String, Object?> params = const {}}) {}

  @override
  void info(String category, String message, {String? operationId, Map<String, Object?> params = const {}}) {}

  @override
  void warning(String category, String message, {String? operationId, Map<String, Object?> params = const {}, Object? error, StackTrace? stackTrace, String? errorCategory}) {}

  @override
  void error(String category, String message, {String? operationId, Map<String, Object?> params = const {}, Object? error, StackTrace? stackTrace, String? errorCategory}) {}

  @override
  AppLogger forOperation(String operationId) => this;
}

/// Recording logger used in tests to assert on emitted records and prove that
/// sensitive content is never logged.
class RecordingLogger implements AppLogger {
  RecordingLogger({this.level = LogLevel.verbose});

  @override
  final LogLevel level;

  @override
  bool get isLevelEnabled => true;

  final List<LogRecord> records = [];

  LogRecord _record(LogLevel level, String category, String message, {String? operationId, Map<String, Object?> params = const {}, Object? error, StackTrace? stackTrace, String? errorCategory}) {
    final record = LogRecord(
      level: level,
      category: category,
      message: message,
      operationId: operationId,
      params: params,
      error: error,
      stackTrace: stackTrace,
      errorCategory: errorCategory,
      timestamp: DateTime.now(),
    );
    records.add(record);
    return record;
  }

  @override
  void verbose(String category, String message, {String? operationId, Map<String, Object?> params = const {}}) => _record(LogLevel.verbose, category, message, operationId: operationId, params: params);

  @override
  void debug(String category, String message, {String? operationId, Map<String, Object?> params = const {}}) => _record(LogLevel.debug, category, message, operationId: operationId, params: params);

  @override
  void info(String category, String message, {String? operationId, Map<String, Object?> params = const {}}) => _record(LogLevel.info, category, message, operationId: operationId, params: params);

  @override
  void warning(String category, String message, {String? operationId, Map<String, Object?> params = const {}, Object? error, StackTrace? stackTrace, String? errorCategory}) => _record(LogLevel.warning, category, message, operationId: operationId, params: params, error: error, stackTrace: stackTrace, errorCategory: errorCategory);

  @override
  void error(String category, String message, {String? operationId, Map<String, Object?> params = const {}, Object? error, StackTrace? stackTrace, String? errorCategory}) => _record(LogLevel.error, category, message, operationId: operationId, params: params, error: error, stackTrace: stackTrace, errorCategory: errorCategory);

  @override
  AppLogger forOperation(String operationId) => _OperationBoundLogger(this, operationId);
}