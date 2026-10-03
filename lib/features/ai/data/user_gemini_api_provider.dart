/// User-provided Gemini API provider implementation (SSOT §5, §6).
///
/// Features:
/// - Connects directly to Google Gemini API using the user's stored key.
/// - Never passes through an application-owned proxy.
/// - Never logs the API key or recipient personal notes (observability rule).
/// - Maps network and HTTP errors to strongly-typed [AppFailure] instances.
library;

import 'dart:convert';
import 'dart:io';

import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/ai/domain/ai_prompt_builder.dart';
import 'package:ai_birthday/features/ai/domain/ai_provider.dart';

/// Type signature for custom HTTP sender (used to mock network requests in tests).
typedef HttpPostSender = Future<HttpResponsePayload> Function(
  Uri uri,
  Map<String, String> headers,
  String body,
);

class HttpResponsePayload {
  const HttpResponsePayload({required this.statusCode, required this.body});
  final int statusCode;
  final String body;
}

class UserGeminiApiProvider implements AiMessageProvider {
  UserGeminiApiProvider({
    required CredentialStorage credentialStorage,
    AppLogger? logger,
    AiPromptBuilder? promptBuilder,
    HttpPostSender? httpSender,
    this.model = 'gemini-1.5-flash',
  })  : _credentialStorage = credentialStorage,
        _logger = logger ?? const ConsoleAppLogger(),
        _promptBuilder = promptBuilder ?? const AiPromptBuilder(),
        _httpSender = httpSender ?? _defaultHttpSender;

  final CredentialStorage _credentialStorage;
  final AppLogger _logger;
  final AiPromptBuilder _promptBuilder;
  final HttpPostSender _httpSender;
  final String model;

  @override
  String get providerId => 'user_gemini';

  @override
  Future<AiGenerationResult> generateMessage(AiGenerationRequest request) async {
    final apiKey = await _credentialStorage.getGeminiApiKey();
    if (apiKey == null || apiKey.isEmpty) {
      throw const AppFailure.credentialMissing(
        action: 'Configure your Gemini API key in Settings to use AI.',
      );
    }

    final prompt = _promptBuilder.buildPrompt(request);

    // Sanitized log: do not log apiKey or prompt body
    _logger.info('UserGeminiApiProvider', 'Sending generation request', params: {
      'model': model,
      'relationship': request.person.relationship.name,
      'hasCustomInstruction': request.customInstruction != null,
    });

    final uri = Uri.parse(
      'https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey',
    );

    final requestBody = jsonEncode({
      'contents': [
        {
          'parts': [
            {'text': prompt}
          ]
        }
      ],
      'generationConfig': {
        'temperature': 0.7,
        'maxOutputTokens': 256,
      }
    });

    HttpResponsePayload response;
    try {
      response = await _httpSender(
        uri,
        {'Content-Type': 'application/json'},
        requestBody,
      );
    } on SocketException {
      throw const AppFailure.networkUnavailable();
    } on HttpException catch (e) {
      throw AppFailure.providerError(detail: e.message);
    } catch (e) {
      if (e is AppFailure) rethrow;
      throw AppFailure.providerError(detail: e.toString());
    }

    if (response.statusCode == 200) {
      return _parseSuccess(response.body);
    } else {
      _handleHttpError(response.statusCode, response.body);
    }
  }

  AiGenerationResult _parseSuccess(String responseBody) {
    try {
      final json = jsonDecode(responseBody) as Map<String, dynamic>;
      final candidates = json['candidates'] as List<dynamic>?;
      if (candidates == null || candidates.isEmpty) {
        throw const AppFailure.providerError(
          detail: 'No response candidates returned by Gemini.',
        );
      }
      final firstCandidate = candidates.first as Map<String, dynamic>;
      final content = firstCandidate['content'] as Map<String, dynamic>?;
      final parts = content?['parts'] as List<dynamic>?;
      if (parts == null || parts.isEmpty) {
        throw const AppFailure.providerError(
          detail: 'Empty response content from Gemini.',
        );
      }
      final text = parts.first['text'] as String?;
      if (text == null || text.trim().isEmpty) {
        throw const AppFailure.providerError(
          detail: 'Empty text returned by Gemini.',
        );
      }

      // Clean up any surrounding quotes or markdown formatting
      var cleanText = text.trim();
      if ((cleanText.startsWith('"') && cleanText.endsWith('"')) ||
          (cleanText.startsWith("'") && cleanText.endsWith("'"))) {
        cleanText = cleanText.substring(1, cleanText.length - 1).trim();
      }

      return AiGenerationResult(
        message: cleanText,
        providerType: providerId,
        modelName: model,
      );
    } on FormatException {
      throw const AppFailure.providerError(
        detail: 'Failed to parse Gemini response payload.',
      );
    }
  }

  Never _handleHttpError(int statusCode, String responseBody) {
    _logger.warning('UserGeminiApiProvider', 'Gemini API returned error', params: {
      'statusCode': statusCode,
    });

    if (statusCode == 400 || statusCode == 401 || statusCode == 403) {
      // Check if it's an API key error
      throw const AppFailure.credentialInvalid(
        detail: 'Google rejected the API key or the project lacks Gemini API access.',
        action: 'Check your Gemini API key in Google AI Studio and update it in Settings.',
      );
    } else if (statusCode == 429) {
      throw const AppFailure.quotaExceeded(
        action: 'Check your Gemini API project quota limits or try again later.',
      );
    } else if (statusCode >= 500) {
      throw AppFailure.providerError(
        detail: 'Google Gemini servers returned error $statusCode. Please try again.',
      );
    } else {
      throw AppFailure.providerError(
        detail: 'HTTP $statusCode: $responseBody',
      );
    }
  }

  /// Default implementation using [dart:io] [HttpClient].
  static Future<HttpResponsePayload> _defaultHttpSender(
    Uri uri,
    Map<String, String> headers,
    String body,
  ) async {
    final client = HttpClient();
    client.connectionTimeout = const Duration(seconds: 15);
    try {
      final request = await client.postUrl(uri);
      headers.forEach((k, v) => request.headers.set(k, v));
      request.write(body);
      final response = await request.close();
      final responseBody = await response.transform(utf8.decoder).join();
      return HttpResponsePayload(
        statusCode: response.statusCode,
        body: responseBody,
      );
    } finally {
      client.close();
    }
  }
}
