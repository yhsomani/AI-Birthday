/// Sanitized, structured logging for AI-Birthday.
///
/// Observability contract (PRODUCT.md §11, SECURITY.md):
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

// 🛡️ SECURITY: Explicitly include sensitive keys (email, phone, credentials, note, text, body, facts, user identifiers) to prevent PII leakage.
// ⚡ PERFORMANCE: Pre-compiled RegExp avoids looping and repeated string operations.
final RegExp _sensitiveKeyRegExp = RegExp(
  r'(^sub$|subject|uid|user[-_]?id|credential|secret|token|api[-_]?key|password|key|phone([-._]?number)?|address|email|note|message|content|body|text|fact|quote)',
  caseSensitive: false,
);
final RegExp _emailRegExp = RegExp(
  r'\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b',
  caseSensitive: false,
);
final RegExp _credentialRegExp = RegExp(
  r'\b(?:Bearer\s+)?(?:AIza[0-9A-Za-z_-]{20,}|ya29\.[0-9A-Za-z._-]{20,}|eyJ[0-9A-Za-z_-]{20,}\.[0-9A-Za-z_-]{10,}\.[0-9A-Za-z_-]{10,})\b',
);
final RegExp _phoneRegExp = RegExp(r'(?<!\w)\+?\d[\d ()-]{7,}\d(?!\w)');
final RegExp _sensitiveAssignmentRegExp = RegExp(
  r'''\b(?:email|phone(?:Number)?|address|token|apiKey|credential|secret|password|note|message|content|body)\s*[:=]\s*(?:"[^"]*"|'[^']*'|[^\s,;]+)''',
  caseSensitive: false,
);

String _redactMessage(String message) => message
    .replaceAll(_emailRegExp, '[REDACTED]')
    .replaceAll(_credentialRegExp, '[REDACTED]')
    .replaceAll(_phoneRegExp, '[REDACTED]')
    .replaceAll(_sensitiveAssignmentRegExp, '[REDACTED]');

/// Redacts values whose key suggests sensitive content.
Object? _redactValue(String key, Object? value) {
  if (_sensitiveKeyRegExp.hasMatch(key)) {
    return '[REDACTED]';
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

  void verbose(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params,
  });
  void debug(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params,
  });
  void info(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params,
  });
  void warning(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params,
    Object? error,
    StackTrace? stackTrace,
    String? errorCategory,
  });
  void error(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params,
    Object? error,
    StackTrace? stackTrace,
    String? errorCategory,
  });

  /// Returns a new logger instance that automatically includes the
  /// given [operationId] in all records emitted by it.
  AppLogger forOperation(String operationId);
}

/// Default logger that writes sanitized records to an output sink.
class ConsoleAppLogger implements AppLogger {
  ConsoleAppLogger({this.level = LogLevel.info, void Function(String)? sink})
    : sink = sink ?? print;

  @override
  final LogLevel level;

  final void Function(String) sink;

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
    final opacity = record.operationId == null
        ? ''
        : ' [op:${record.operationId}]';
    final categoryInfo = record.errorCategory == null
        ? ''
        : ' [category:${record.errorCategory}]';
    final durationInfo = record.durationMs == null
        ? ''
        : ' (${record.durationMs}ms)';
    final errorInfo = record.error == null
        ? ''
        : ' error=${record.error.runtimeType}';
    final traceInfo = record.stackTrace == null
        ? ''
        : ' trace=${record.stackTrace.toString().split('\n').firstWhere((line) => line.trim().isNotEmpty, orElse: () => '')}';
    sink(
      '[${record.level.name}] ${record.category}$opacity$categoryInfo: ${_redactMessage(record.message)}$durationInfo$extra$errorInfo$traceInfo',
    );
  }

  void _log(LogRecord record) => _emit(record);

  @override
  void verbose(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
  }) => _log(
    LogRecord(
      level: LogLevel.verbose,
      category: category,
      message: _redactMessage(message),
      operationId: operationId,
      params: params,
    ),
  );

  @override
  void debug(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
  }) => _log(
    LogRecord(
      level: LogLevel.debug,
      category: category,
      message: message,
      operationId: operationId,
      params: params,
    ),
  );

  @override
  void info(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
  }) => _log(
    LogRecord(
      level: LogLevel.info,
      category: category,
      message: message,
      operationId: operationId,
      params: params,
    ),
  );

  @override
  void warning(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
    Object? error,
    StackTrace? stackTrace,
    String? errorCategory,
  }) => _log(
    LogRecord(
      level: LogLevel.warning,
      category: category,
      message: message,
      operationId: operationId,
      params: params,
      error: error,
      stackTrace: stackTrace,
      errorCategory: errorCategory,
    ),
  );

  @override
  void error(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
    Object? error,
    StackTrace? stackTrace,
    String? errorCategory,
  }) => _log(
    LogRecord(
      level: LogLevel.error,
      category: category,
      message: message,
      operationId: operationId,
      params: params,
      error: error,
      stackTrace: stackTrace,
      errorCategory: errorCategory,
    ),
  );

  @override
  AppLogger forOperation(String operationId) =>
      _OperationBoundLogger(this, operationId);
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
  void verbose(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
  }) => _inner.verbose(
    category,
    message,
    operationId: operationId ?? _operationId,
    params: params,
  );

  @override
  void debug(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
  }) => _inner.debug(
    category,
    message,
    operationId: operationId ?? _operationId,
    params: params,
  );

  @override
  void info(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
  }) => _inner.info(
    category,
    message,
    operationId: operationId ?? _operationId,
    params: params,
  );

  @override
  void warning(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
    Object? error,
    StackTrace? stackTrace,
    String? errorCategory,
  }) => _inner.warning(
    category,
    message,
    operationId: operationId ?? _operationId,
    params: params,
    error: error,
    stackTrace: stackTrace,
    errorCategory: errorCategory,
  );

  @override
  void error(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
    Object? error,
    StackTrace? stackTrace,
    String? errorCategory,
  }) => _inner.error(
    category,
    message,
    operationId: operationId ?? _operationId,
    params: params,
    error: error,
    stackTrace: stackTrace,
    errorCategory: errorCategory,
  );

  @override
  AppLogger forOperation(String operationId) =>
      _inner.forOperation(operationId);
}

/// No-op logger for tests and non-diagnostic environments.
class NoopLogger implements AppLogger {
  const NoopLogger();

  @override
  LogLevel get level => LogLevel.verbose;

  @override
  bool get isLevelEnabled => false;

  @override
  void verbose(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
  }) {}

  @override
  void debug(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
  }) {}

  @override
  void info(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
  }) {}

  @override
  void warning(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
    Object? error,
    StackTrace? stackTrace,
    String? errorCategory,
  }) {}

  @override
  void error(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
    Object? error,
    StackTrace? stackTrace,
    String? errorCategory,
  }) {}

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

  LogRecord _record(
    LogLevel level,
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
    Object? error,
    StackTrace? stackTrace,
    String? errorCategory,
  }) {
    final record = LogRecord(
      level: level,
      category: category,
      message: _redactMessage(message),
      operationId: operationId,
      params: params.map(
        (key, value) => MapEntry(key, _redactValue(key, value)),
      ),
      error: error,
      stackTrace: stackTrace,
      errorCategory: errorCategory,
      timestamp: DateTime.now(),
    );
    records.add(record);
    return record;
  }

  @override
  void verbose(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
  }) => _record(
    LogLevel.verbose,
    category,
    message,
    operationId: operationId,
    params: params,
  );

  @override
  void debug(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
  }) => _record(
    LogLevel.debug,
    category,
    message,
    operationId: operationId,
    params: params,
  );

  @override
  void info(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
  }) => _record(
    LogLevel.info,
    category,
    message,
    operationId: operationId,
    params: params,
  );

  @override
  void warning(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
    Object? error,
    StackTrace? stackTrace,
    String? errorCategory,
  }) => _record(
    LogLevel.warning,
    category,
    message,
    operationId: operationId,
    params: params,
    error: error,
    stackTrace: stackTrace,
    errorCategory: errorCategory,
  );

  @override
  void error(
    String category,
    String message, {
    String? operationId,
    Map<String, Object?> params = const {},
    Object? error,
    StackTrace? stackTrace,
    String? errorCategory,
  }) => _record(
    LogLevel.error,
    category,
    message,
    operationId: operationId,
    params: params,
    error: error,
    stackTrace: stackTrace,
    errorCategory: errorCategory,
  );

  @override
  AppLogger forOperation(String operationId) =>
      _OperationBoundLogger(this, operationId);
}
