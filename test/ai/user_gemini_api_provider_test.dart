import 'dart:io';

import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/ai/data/user_gemini_api_provider.dart';
import 'package:ai_birthday/features/ai/domain/ai_prompt_builder.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:flutter_test/flutter_test.dart';

class FakeCredentialStorage implements CredentialStorage {
  String? key;
  DateTime? verifiedAt;

  @override
  Future<void> deleteGeminiApiKey() async {
    key = null;
    verifiedAt = null;
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

  @override
  Future<DateTime?> geminiKeyVerifiedAt() async => verifiedAt;

  @override
  Future<void> recordGeminiKeyVerifiedAt(DateTime at) async {
    verifiedAt = at;
  }

  @override
  Future<void> clearGeminiKeyVerifiedAt() async {
    verifiedAt = null;
  }

  @override
  Future<bool> hasCompletedOnboarding() async => false;

  @override
  Future<void> setCompletedOnboarding(bool completed) async {}
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
          expect(uri.queryParameters, isEmpty);
          expect(headers['x-goog-api-key'], 'AIzaSyValidKey');
          expect(headers['Content-Type'], 'application/json');
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

  group('UserGeminiApiProvider.generateMessage response parsing', () {
    late FakeCredentialStorage storage;
    final testPerson = Person(
      id: 'p-1',
      name: 'Alice',
      relationship: RelationshipCategory.friend,
      relationshipCloseness: RelationshipCloseness.close,
      preferredLanguage: 'en',
      preferredTone: MessageTone.warm,
      preferredDeliveryChannel: DeliveryChannel.whatsapp,
      createdAt: DateTime.now(),
      updatedAt: DateTime.now(),
      version: 1,
    );

    setUp(() {
      storage = FakeCredentialStorage()..saveGeminiApiKey('AIzaValidKey');
    });

    test('throws providerError on non-object JSON response', () async {
      final provider = UserGeminiApiProvider(
        credentialStorage: storage,
        httpSender: (uri, headers, body) async =>
            const HttpResponsePayload(statusCode: 200, body: '[]'),
      );

      expect(
        () => provider.generateMessage(AiGenerationRequest(person: testPerson)),
        throwsA(
          isA<AppFailure>()
              .having((f) => f.code, 'code', AppFailureCode.aiProviderError)
              .having(
                (f) => f.detail,
                'detail',
                'Failed to parse Gemini response payload.',
              ),
        ),
      );
    });

    test(
      'throws providerError when candidates or parts contain unexpected types',
      () async {
        final provider = UserGeminiApiProvider(
          credentialStorage: storage,
          httpSender: (uri, headers, body) async => const HttpResponsePayload(
            statusCode: 200,
            body: '{"candidates": [123]}',
          ),
        );

        expect(
          () =>
              provider.generateMessage(AiGenerationRequest(person: testPerson)),
          throwsA(
            isA<AppFailure>()
                .having((f) => f.code, 'code', AppFailureCode.aiProviderError)
                .having(
                  (f) => f.detail,
                  'detail',
                  'Malformed candidate in Gemini response.',
                ),
          ),
        );
      },
    );

    test('parses clean valid candidate text properly', () async {
      final provider = UserGeminiApiProvider(
        credentialStorage: storage,
        httpSender: (uri, headers, body) async => const HttpResponsePayload(
          statusCode: 200,
          body:
              '{"candidates":[{"content":{"parts":[{"text":"\\"Happy Birthday!\\""}]}}]}',
        ),
      );

      final result = await provider.generateMessage(
        AiGenerationRequest(person: testPerson),
      );
      expect(result.message, 'Happy Birthday!');
      expect(result.providerType, 'user_gemini');
    });
  });
}
