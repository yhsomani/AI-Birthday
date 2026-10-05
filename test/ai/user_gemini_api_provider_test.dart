import 'dart:io';

import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/ai/data/user_gemini_api_provider.dart';
import 'package:flutter_test/flutter_test.dart';

class FakeCredentialStorage implements CredentialStorage {
  String? key;

  @override
  Future<void> deleteGeminiApiKey() async {
    key = null;
  }

  @override
  Future<String?> getGeminiApiKey() async {
    return key;
  }

  @override
  Future<bool> hasGeminiApiKey() async {
    return key != null && key!.isNotEmpty;
  }

  @override
  Future<void> saveGeminiApiKey(String apiKey) async {
    key = apiKey;
  }
}

void main() {
  group('UserGeminiApiProvider.testApiKey', () {
    late FakeCredentialStorage storage;

    setUp(() {
      storage = FakeCredentialStorage();
    });

    test('returns invalidKey when empty or whitespace', () async {
      final provider = UserGeminiApiProvider(
        credentialStorage: storage,
        httpSender: (uri, headers, body) async {
          return const HttpResponsePayload(statusCode: 200, body: '{}');
        },
      );

      final resultEmpty = await provider.testApiKey('');
      expect(resultEmpty.status, GeminiConnectionStatus.invalidKey);

      final resultWhitespace = await provider.testApiKey('   ');
      expect(resultWhitespace.status, GeminiConnectionStatus.invalidKey);
    });

    test('returns connected when API returns 200 OK', () async {
      final provider = UserGeminiApiProvider(
        credentialStorage: storage,
        httpSender: (uri, headers, body) async {
          expect(uri.queryParameters['key'], 'AIzaSyValidKey');
          return const HttpResponsePayload(
            statusCode: 200,
            body: '{"candidates":[{"content":{"parts":[{"text":"Hi"}]}}]}',
          );
        },
      );

      final result = await provider.testApiKey('AIzaSyValidKey');
      expect(result.status, GeminiConnectionStatus.connected);
      expect(result.message, contains('Gemini is ready'));
    });

    test('returns invalidKey when API returns 400 or 403', () async {
      final provider400 = UserGeminiApiProvider(
        credentialStorage: storage,
        httpSender: (uri, headers, body) async {
          return const HttpResponsePayload(
            statusCode: 400,
            body: '{"error":{"message":"API key not valid"}}',
          );
        },
      );

      final result400 = await provider400.testApiKey('AIzaSyInvalidKey');
      expect(result400.status, GeminiConnectionStatus.invalidKey);
      expect(result400.message, contains('Google rejected this key'));

      final provider403 = UserGeminiApiProvider(
        credentialStorage: storage,
        httpSender: (uri, headers, body) async {
          return const HttpResponsePayload(
            statusCode: 403,
            body: '{"error":{"message":"Permission denied"}}',
          );
        },
      );

      final result403 = await provider403.testApiKey('AIzaSyInvalidKey');
      expect(result403.status, GeminiConnectionStatus.invalidKey);
    });

    test('returns quotaExceeded when API returns 429', () async {
      final provider = UserGeminiApiProvider(
        credentialStorage: storage,
        httpSender: (uri, headers, body) async {
          return const HttpResponsePayload(
            statusCode: 429,
            body: '{"error":{"message":"Quota exceeded"}}',
          );
        },
      );

      final result = await provider.testApiKey('AIzaSyQuotaKey');
      expect(result.status, GeminiConnectionStatus.quotaExceeded);
      expect(result.message, contains('reached its current quota'));
    });

    test('returns networkUnavailable when SocketException occurs', () async {
      final provider = UserGeminiApiProvider(
        credentialStorage: storage,
        httpSender: (uri, headers, body) async {
          throw const SocketException('No route to host');
        },
      );

      final result = await provider.testApiKey('AIzaSyKey');
      expect(result.status, GeminiConnectionStatus.networkUnavailable);
      expect(result.message, contains('internet connection'));
    });

    test('returns error for 500 server error', () async {
      final provider = UserGeminiApiProvider(
        credentialStorage: storage,
        httpSender: (uri, headers, body) async {
          return const HttpResponsePayload(
            statusCode: 500,
            body: 'Internal server error',
          );
        },
      );

      final result = await provider.testApiKey('AIzaSyKey');
      expect(result.status, GeminiConnectionStatus.error);
      expect(result.message, contains('Google Gemini returned error 500'));
    });
  });
}
