import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('ConsoleAppLogger sanitization', () {
    test('redacts API credential under sensitive keys', () {
      final lines = <String>[];
      final logger = ConsoleAppLogger(level: LogLevel.verbose, sink: lines.add);

      logger.info(
        'ai',
        'generation failed',
        params: {
          'operationId': 'op-1',
          'geminiApiKey': 'AIzaSy-SECRET',
          'message': 'Happy birthday',
        },
      );

      expect(lines, hasLength(1));
      expect(lines.first, contains('[REDACTED]'));
      expect(lines.first, isNot(contains('AIzaSy-SECRET')));
      expect(lines.first, isNot(contains('Happy birthday')));
      expect(lines.first, contains('op-1'));
    });

    test('redacts phone numbers and notes', () {
      final lines = <String>[];
      final logger = ConsoleAppLogger(level: LogLevel.verbose, sink: lines.add);

      logger.debug(
        'delivery',
        'handoff prepared',
        params: {
          'phoneNumber': '+911234567890',
          'notes': 'secret fondness detail',
        },
      );

      expect(lines.first, contains('[REDACTED]'));
      expect(lines.first, isNot(contains('+911234567890')));
      expect(lines.first, isNot(contains('secret fondness detail')));
    });

    test('redacts sensitive values embedded in message strings', () {
      final lines = <String>[];
      final logger = ConsoleAppLogger(level: LogLevel.verbose, sink: lines.add);
      const email = 'person@example.com';
      const key = 'AIzaSy123456789012345678901234567890';
      logger.error(
        'auth',
        'failed for email=$email with key=$key and phone +1 415 555 1212',
      );
      expect(lines.single, isNot(contains(email)));
      expect(lines.single, isNot(contains(key)));
      expect(lines.single, isNot(contains('+1 415 555 1212')));

      final recording = RecordingLogger();
      recording.info('auth', 'failed for $email');
      expect(recording.records.single.message, isNot(contains(email)));
    });

    test('forOperation binds every record to the operation id', () {
      final lines = <String>[];
      final logger = ConsoleAppLogger(level: LogLevel.verbose, sink: lines.add);

      logger
          .forOperation('op-42')
          .info('db', 'row written', params: {'personId': 'p1'});
      expect(lines.first, contains('op-42'));
    });

    test('emits top stack trace frame safely when provided', () {
      final lines = <String>[];
      final logger = ConsoleAppLogger(level: LogLevel.verbose, sink: lines.add);

      logger.error(
        'sync',
        'network fault',
        error: Exception('connection refused'),
        stackTrace: StackTrace.current,
      );
      expect(lines.first, contains('error='));
      expect(lines.first, contains('trace='));
    });
  });

  group('RecordingLogger', () {
    test('records entries and lets tests assert on them', () {
      final logger = RecordingLogger();
      logger.error(
        'ai',
        'provider error',
        operationId: 'op-1',
        params: {'code': 500},
      );

      expect(logger.records, hasLength(1));
      expect(logger.records.single.isError, isTrue);
      expect(logger.records.single.operationId, 'op-1');
    });

    test('sensitive values are never recorded in params', () {
      final logger = RecordingLogger();
      logger.debug(
        'ai',
        'test credential',
        params: {'apiKey': 'SECRETKEY123', 'personId': 'p9'},
      );

      expect(logger.records.single.params['personId'], 'p9');
      expect(logger.records.single.params['apiKey'], isNot('SECRETKEY123'));
    });
  });
}
