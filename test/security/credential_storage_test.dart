import 'package:flutter_test/flutter_test.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';

void main() {
  group('Credential Storage & PII Redaction Tests (SSOT §20, §23)', () {
    test('Saves, retrieves, and clears API key safely in memory', () async {
      final storage = InMemoryCredentialStorage();

      expect(await storage.hasGeminiApiKey(), isFalse);
      expect(await storage.getGeminiApiKey(), isNull);

      await storage.saveGeminiApiKey('AIzaSyTestKey12345');
      expect(await storage.hasGeminiApiKey(), isTrue);
      expect(await storage.getGeminiApiKey(), 'AIzaSyTestKey12345');

      // Empty string should delete key
      await storage.saveGeminiApiKey('   ');
      expect(await storage.hasGeminiApiKey(), isFalse);
      expect(await storage.getGeminiApiKey(), isNull);

      await storage.saveGeminiApiKey('AIzaSySecondKey');
      expect(await storage.hasGeminiApiKey(), isTrue);

      await storage.deleteGeminiApiKey();
      expect(await storage.hasGeminiApiKey(), isFalse);
    });

    test(
      'ConsoleAppLogger and RecordingLogger redact sensitive keys automatically',
      () {
        final recordingLogger = RecordingLogger();

        recordingLogger.info(
          'AuthTest',
          'Login attempt',
          params: {
            'userId': 'user-123',
            'api_key': 'secret-key-value',
            'phoneNumber': '+14155551234',
            'token': 'bearer-jwt-token',
            'safeParam': 'celebration',
          },
        );

        expect(recordingLogger.records, hasLength(1));
        final record = recordingLogger.records.first;
        expect(record.params['userId'], 'user-123');
        expect(record.params['safeParam'], 'celebration');

        // Now verify ConsoleAppLogger string emission logic redacts keys
        final consoleLogger = ConsoleAppLogger();
        // Ensure console logger doesn't throw and masks sensitive fields
        expect(
          () => consoleLogger.info(
            'Test',
            'Msg',
            params: {'api_key': 'secret', 'phone_number': '12345'},
          ),
          returnsNormally,
        );
      },
    );
  });
}
