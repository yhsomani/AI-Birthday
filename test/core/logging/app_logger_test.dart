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

    test('forOperation binds every record to the operation id', () {
      final lines = <String>[];
      final logger = ConsoleAppLogger(level: LogLevel.verbose, sink: lines.add);

      logger
          .forOperation('op-42')
          .info('db', 'row written', params: {'personId': 'p1'});
      expect(lines.first, contains('op-42'));
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
