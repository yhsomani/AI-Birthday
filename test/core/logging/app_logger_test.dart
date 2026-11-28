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

    test('preserves HTTP status prose and ISO dates while redacting secrets', () {
      final lines = <String>[];
      final logger = ConsoleAppLogger(level: LogLevel.verbose, sink: lines.add);

      logger.warning(
        'auth',
        'Firebase IdP rejected token: HTTP 400 INVALID_ID_TOKEN',
      );
      logger.warning(
        'auth',
        'Firebase IdP rejected token: HTTP 401 API_KEY_INVALID',
      );
      logger.info('birthdays', 'Scheduled cycle date 2026-11-27 completed');
      logger.error(
        'ai',
        'Auth header Bearer abcdef123456 failed with jwt eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abc123def456',
      );
      logger.error(
        'sync',
        'api_key=AIzaSy123456789012345678901234567890 refresh_token=rt_secret {"id_token":"tok123"}',
      );
      logger.info(
        'http',
        'request detail',
        params: {
          'url':
              'https://example.test/?key=AIzaSy123456789012345678901234567890',
          'nested': {
            'reason': 'email user@example.com bounced',
            'items': ['Bearer secret_token_123'],
          },
        },
      );

      expect(lines[0], contains('HTTP 400'));
      expect(lines[0], contains('INVALID_ID_TOKEN'));
      expect(lines[1], contains('HTTP 401'));
      expect(lines[1], contains('API_KEY_INVALID'));
      expect(lines[2], contains('2026-11-27'));
      expect(lines[3], isNot(contains('abcdef123456')));
      expect(lines[3], isNot(contains('eyJhbGciOiJIUzI1NiJ9')));
      expect(lines[4], isNot(contains('AIzaSy123456789012345678901234567890')));
      expect(lines[4], isNot(contains('rt_secret')));
      expect(lines[4], isNot(contains('tok123')));
      expect(lines[5], isNot(contains('AIzaSy123456789012345678901234567890')));
      expect(lines[5], isNot(contains('user@example.com')));
      expect(lines[5], isNot(contains('secret_token_123')));
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
