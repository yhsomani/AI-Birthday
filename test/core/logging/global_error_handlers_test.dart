import 'dart:ui' show ErrorCallback, PlatformDispatcher;

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/logging/global_error_handlers.dart';
import 'package:ai_birthday/main.dart' show bootstrapFirebase;

void main() {
  late FlutterExceptionHandler? originalFlutterHandler;
  late ErrorCallback? originalPlatformHandler;
  late void Function(FlutterErrorDetails) originalPresent;
  late List<String> lines;
  late ConsoleAppLogger logger;

  setUp(() {
    originalFlutterHandler = FlutterError.onError;
    originalPlatformHandler = PlatformDispatcher.instance.onError;
    originalPresent = FlutterError.presentError;
    FlutterError.presentError = (_) {}; // keep test output quiet
    lines = [];
    logger = ConsoleAppLogger(sink: lines.add);
  });

  tearDown(() {
    FlutterError.presentError = originalPresent;
    FlutterError.onError = originalFlutterHandler;
    PlatformDispatcher.instance.onError = originalPlatformHandler;
  });

  test('FlutterError.onError is routed to the logger without the message '
      'text (A3-009)', () {
    installGlobalErrorHandlers(logger);

    FlutterError.onError!(
      FlutterErrorDetails(
        exception: StateError('contact Alice phone +15551234567 token=abc123'),
        library: 'test library',
      ),
    );

    expect(lines, hasLength(1));
    expect(lines.single, contains('[error] App'));
    expect(lines.single, contains('StateError'));
    expect(lines.single, isNot(contains('Alice')));
    expect(lines.single, isNot(contains('+1555')));
    expect(lines.single, isNot(contains('abc123')));
  });

  test('PlatformDispatcher.onError logs, reports handled and leaks no '
      'message text (A3-009)', () {
    installGlobalErrorHandlers(logger);

    final handled = PlatformDispatcher.instance.onError!(
      Exception('secret-api-key-123'),
      StackTrace.current,
    );

    expect(handled, isTrue);
    expect(lines, hasLength(1));
    expect(lines.single, contains('[error] App'));
    expect(lines.single, isNot(contains('secret-api-key-123')));
  });

  group('bootstrapFirebase logs instead of swallowing (A3-009)', () {
    test('Firebase init failure logs a warning and skips App Check', () async {
      var appCheckCalled = false;
      final ok = await bootstrapFirebase(
        logger,
        initializeApp: () async => throw StateError('no google-services'),
        activateAppCheck: () async => appCheckCalled = true,
      );
      expect(ok, isFalse);
      expect(appCheckCalled, isFalse);
      expect(lines.single, contains('[warning] Bootstrap'));
      expect(lines.single, contains('Firebase initialization failed'));
    });

    test('App Check failure logs a warning', () async {
      final ok = await bootstrapFirebase(
        logger,
        initializeApp: () async {},
        activateAppCheck: () async => throw StateError('play integrity'),
      );
      expect(ok, isFalse);
      expect(lines.single, contains('App Check activation failed'));
    });

    test('success logs nothing and reports true', () async {
      final ok = await bootstrapFirebase(
        logger,
        initializeApp: () async {},
        activateAppCheck: () async {},
      );
      expect(ok, isTrue);
      expect(lines, isEmpty);
    });
  });
}
