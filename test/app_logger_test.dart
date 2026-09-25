import 'dart:async';
import 'package:flutter_test/flutter_test.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';

void main() {
  test('redacts sensitive values nested', () {
    final logger = ConsoleAppLogger();

    // We can capture stdout to test ConsoleAppLogger which runs _sanitize during _emit
    List<String> logOutput = [];
    runZoned(
      () {
        logger.info('TestCategory', 'test message', params: {
          'normal_id': 'visible',
          'api_key': 'sk_123',
          'nested': {'api_key': 'should_be_redacted', 'password': 'hidden'},
          'list': [
            {'token': 'secret_token'},
            'normal_string'
          ]
        });
      },
      zoneSpecification: ZoneSpecification(
        print: (Zone self, ZoneDelegate parent, Zone zone, String line) {
          logOutput.add(line);
        },
      ),
    );

    expect(logOutput.length, 1);
    final logLine = logOutput.first;

    expect(logLine.contains('normal_id=visible'), isTrue);
    expect(logLine.contains('api_key=[REDACTED]'), isTrue);
    expect(
        logLine.contains('nested={api_key: [REDACTED], password: [REDACTED]}'),
        isTrue);
    expect(
        logLine.contains('list=[{token: [REDACTED]}, normal_string]'), isTrue);
  });
}
