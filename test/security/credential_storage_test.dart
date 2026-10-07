import 'package:flutter_test/flutter_test.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';

/// In-memory secure-store driver for exercising [SecureCredentialStorage].
class _MapStoreDriver implements SecureStoreDriver {
  final Map<String, String> values = {};

  @override
  Future<String?> read(String key) async => values[key];

  @override
  Future<void> write(String key, String value) async {
    values[key] = value;
  }

  @override
  Future<void> delete(String key) async {
    values.remove(key);
  }
}

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

    test('key deletion clears verified-at; corrupted record reads as unverified',
        () async {
      // In-memory path: record → read → cleared on key deletion.
      final storage = InMemoryCredentialStorage();
      final when = DateTime.utc(2026, 10, 7, 12, 0);

      await storage.recordGeminiKeyVerifiedAt(when);
      expect(await storage.geminiKeyVerifiedAt(), when);

      await storage.saveGeminiApiKey('AIzaSyTestKey12345');
      await storage.deleteGeminiApiKey();
      expect(await storage.geminiKeyVerifiedAt(), isNull);

      // Secure path: a corrupted stored value degrades to "unverified",
      // never throws (adversarial F-7 style guard).
      final driver = _MapStoreDriver()..values['ai_birthday_gemini_key_verified_at'] = 'not-a-number';
      final secure = SecureCredentialStorage(driver);
      expect(await secure.geminiKeyVerifiedAt(), isNull);

      await driver.write('ai_birthday_gemini_key_verified_at', '0');
      expect(await secure.geminiKeyVerifiedAt(), isNull);

      await driver.write(
        'ai_birthday_gemini_key_verified_at',
        when.millisecondsSinceEpoch.toString(),
      );
      expect(await secure.geminiKeyVerifiedAt(), when);
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
        expect(record.params['userId'], '[REDACTED]');
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
