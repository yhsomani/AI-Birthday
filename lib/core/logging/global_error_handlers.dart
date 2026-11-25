/// Process-wide error handlers that route uncaught errors to the [AppLogger].
///
/// Only the error type and the first stack frame are emitted by the logger;
/// exception messages are never interpolated because they can carry PII or
/// secrets (request bodies, tokens, contact names).
library;

import 'package:flutter/foundation.dart';

import 'app_logger.dart';

/// Installs [FlutterError.onError] and [PlatformDispatcher.onError].
///
/// In debug builds the framework's default presentation is kept so developers
/// still see the full red-screen/console report.
void installGlobalErrorHandlers(AppLogger logger) {
  FlutterError.onError = (FlutterErrorDetails details) {
    logger.error(
      'App',
      'Unhandled Flutter framework error.',
      params: {'library': details.library ?? 'unknown'},
      error: details.exception,
      stackTrace: details.stack,
      errorCategory: 'flutter',
    );
    if (kDebugMode) FlutterError.presentError(details);
  };

  PlatformDispatcher.instance.onError = (Object error, StackTrace stack) {
    logger.error(
      'App',
      'Unhandled asynchronous error.',
      error: error,
      stackTrace: stack,
      errorCategory: 'platform',
    );
    // Handled: do not terminate the process for a logged async error.
    return true;
  };
}
