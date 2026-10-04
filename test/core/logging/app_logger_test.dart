import 'package:flutter_test/flutter_test.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';

void main() {
  group('RecordingLogger', () {
    late RecordingLogger logger;

    setUp(() {
      logger = RecordingLogger();
    });

    test('initializes with empty records', () {
      expect(logger.records, isEmpty);
      expect(logger.isLevelEnabled, isTrue);
      expect(logger.level, equals(LogLevel.verbose));
    });

    test('verbose logs correctly', () {
      final before = DateTime.now();
      logger.verbose(
        'Network',
        'Fetch started',
        params: {'url': 'https://api.example.com'},
      );
      final after = DateTime.now();

      expect(logger.records, hasLength(1));
      final record = logger.records.first;
      expect(record.level, equals(LogLevel.verbose));
      expect(record.category, equals('Network'));
      expect(record.message, equals('Fetch started'));
      expect(record.params, equals({'url': 'https://api.example.com'}));
      expect(
        record.timestamp!.isAfter(before) ||
            record.timestamp!.isAtSameMomentAs(before),
        isTrue,
      );
      expect(
        record.timestamp!.isBefore(after) ||
            record.timestamp!.isAtSameMomentAs(after),
        isTrue,
      );
    });

    test('debug logs correctly', () {
      logger.debug('UI', 'Button pressed', operationId: 'op_123');

      expect(logger.records, hasLength(1));
      final record = logger.records.first;
      expect(record.level, equals(LogLevel.debug));
      expect(record.category, equals('UI'));
      expect(record.message, equals('Button pressed'));
      expect(record.operationId, equals('op_123'));
    });

    test('info logs correctly', () {
      logger.info('Auth', 'User logged in');

      expect(logger.records, hasLength(1));
      final record = logger.records.first;
      expect(record.level, equals(LogLevel.info));
      expect(record.category, equals('Auth'));
      expect(record.message, equals('User logged in'));
    });

    test('warning logs correctly with error and stackTrace', () {
      final exception = Exception('Rate limited');
      final stackTrace = StackTrace.current;

      logger.warning(
        'API',
        'Request failed',
        error: exception,
        stackTrace: stackTrace,
        errorCategory: 'NetworkError',
      );

      expect(logger.records, hasLength(1));
      final record = logger.records.first;
      expect(record.level, equals(LogLevel.warning));
      expect(record.category, equals('API'));
      expect(record.message, equals('Request failed'));
      expect(record.error, equals(exception));
      expect(record.stackTrace, equals(stackTrace));
      expect(record.errorCategory, equals('NetworkError'));
    });

    test('error logs correctly with all properties', () {
      final exception = FormatException('Invalid JSON');
      final stackTrace = StackTrace.current;

      logger.error(
        'Database',
        'Parse error',
        operationId: 'op_456',
        params: {'id': 99},
        error: exception,
        stackTrace: stackTrace,
        errorCategory: 'ParseError',
      );

      expect(logger.records, hasLength(1));
      final record = logger.records.first;
      expect(record.level, equals(LogLevel.error));
      expect(record.category, equals('Database'));
      expect(record.message, equals('Parse error'));
      expect(record.operationId, equals('op_456'));
      expect(record.params, equals({'id': 99}));
      expect(record.error, equals(exception));
      expect(record.stackTrace, equals(stackTrace));
      expect(record.errorCategory, equals('ParseError'));
    });

    test('forOperation creates a logger that appends operationId', () {
      final opLogger = logger.forOperation('op_789');

      // Should inherit properties
      expect(opLogger.isLevelEnabled, equals(logger.isLevelEnabled));
      expect(opLogger.level, equals(logger.level));

      // Log with operation logger
      opLogger.info('Task', 'Started');
      opLogger.error('Task', 'Failed');

      expect(logger.records, hasLength(2));

      final firstRecord = logger.records[0];
      expect(firstRecord.level, equals(LogLevel.info));
      expect(firstRecord.category, equals('Task'));
      expect(firstRecord.message, equals('Started'));
      expect(firstRecord.operationId, equals('op_789'));

      final secondRecord = logger.records[1];
      expect(secondRecord.level, equals(LogLevel.error));
      expect(secondRecord.category, equals('Task'));
      expect(secondRecord.message, equals('Failed'));
      expect(secondRecord.operationId, equals('op_789'));
    });

    test('forOperation allows overriding operationId in log call', () {
      final opLogger = logger.forOperation('op_default');

      opLogger.info('Task', 'Overridden', operationId: 'op_specific');

      expect(logger.records, hasLength(1));
      final record = logger.records.first;
      expect(record.operationId, equals('op_specific'));
    });
  });
}
