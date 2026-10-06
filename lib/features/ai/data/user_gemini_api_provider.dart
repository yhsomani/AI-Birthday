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
typedef HttpPostSender =
    Future<HttpResponsePayload> Function(
      Uri uri,
      Map<String, String> headers,
      String body,
    );

class HttpResponsePayload {
  const HttpResponsePayload({required this.statusCode, required this.body});
  final int statusCode;
  final String body;
}

enum GeminiConnectionStatus {
  connected,
  invalidKey,
  quotaExceeded,
  networkUnavailable,
  error,
}

class GeminiConnectionResult {
  const GeminiConnectionResult({required this.status, required this.message});

  final GeminiConnectionStatus status;
  final String message;

  const GeminiConnectionResult.connected([
    this.message =
        'Gemini is ready. Messages will use your personal Gemini API access.',
  ]) : status = GeminiConnectionStatus.connected;

  const GeminiConnectionResult.invalidKey([
    this.message =
        'Google rejected this key. Check the key in Google AI Studio and try again.',
  ]) : status = GeminiConnectionStatus.invalidKey;

  const GeminiConnectionResult.quotaExceeded([
    this.message =
        'Your Google Gemini project has reached its current quota. Check usage or billing in Google AI Studio.',
  ]) : status = GeminiConnectionStatus.quotaExceeded;

  const GeminiConnectionResult.networkUnavailable([
    this.message = 'Check your internet connection and try again.',
  ]) : status = GeminiConnectionStatus.networkUnavailable;

  const GeminiConnectionResult.error(this.message)
    : status = GeminiConnectionStatus.error;
}

class UserGeminiApiProvider implements AiMessageProvider {
  UserGeminiApiProvider({
    required CredentialStorage credentialStorage,
    AppLogger? logger,
    AiPromptBuilder? promptBuilder,
    HttpPostSender? httpSender,
    this.model = 'gemini-2.5-flash-lite',
  }) : _credentialStorage = credentialStorage,
       _logger = logger ?? ConsoleAppLogger(),
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
  Future<AiGenerationResult> generateMessage(
    AiGenerationRequest request,
  ) async {
    final apiKey = await _credentialStorage.getGeminiApiKey();
    if (apiKey == null || apiKey.isEmpty) {
      throw const AppFailure.credentialMissing(
        action: 'Configure your Gemini API key in Settings to use AI.',
      );
    }

    final prompt = _promptBuilder.buildPrompt(request);

    // Sanitized log: do not log apiKey or prompt body
    _logger.info(
      'UserGeminiApiProvider',
      'Sending generation request',
      params: {
        'model': model,
        'relationship': request.person.relationship.name,
        'hasCustomInstruction': request.customInstruction != null,
      },
    );

    final uri = Uri.parse(
      'https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent',
    );

    final requestBody = jsonEncode({
      'contents': [
        {
          'parts': [
            {'text': prompt},
          ],
        },
      ],
      'generationConfig': {'temperature': 0.7, 'maxOutputTokens': 256},
    });

    HttpResponsePayload response;
    try {
      response = await _httpSender(uri, {
        'Content-Type': 'application/json',
        'x-goog-api-key': apiKey,
      }, requestBody);
    } on SocketException {
      throw const AppFailure.networkUnavailable();
    } on HttpException catch (e) {
      throw AppFailure.providerError(detail: e.message);
    } catch (e, st) {
      if (e is AppFailure) rethrow;
      _logger.error(
        'UserGeminiApiProvider',
        'Unexpected error generating message',
        error: e,
        stackTrace: st,
      );

      throw const AppFailure.providerError(
        detail: 'An error occurred during message generation.',
      );
    }

    if (response.statusCode == 200) {
      return _parseSuccess(response.body);
    } else {
      _handleHttpError(response.statusCode, response.body);
    }
  }

  /// Tests connection to the Gemini API using the provided [apiKey].
  ///
  /// Sends a minimal ping using model [$model] to verify that the key is valid,
  /// the project has access to Gemini, and sufficient quota remains.
  Future<GeminiConnectionResult> testApiKey(String apiKey) async {
    final trimmed = apiKey.trim();
    if (trimmed.isEmpty) {
      return const GeminiConnectionResult.invalidKey(
        'Please enter an API key to test.',
      );
    }

    final uri = Uri.parse(
      'https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent',
    );

    final requestBody = jsonEncode({
      'contents': [
        {
          'parts': [
            {'text': 'Ping'},
          ],
        },
      ],
      'generationConfig': {'temperature': 0.0, 'maxOutputTokens': 1},
    });

    try {
      final response = await _httpSender(uri, {
        'Content-Type': 'application/json',
        'x-goog-api-key': trimmed,
      }, requestBody);

      if (response.statusCode == 200) {
        return const GeminiConnectionResult.connected();
      } else if (response.statusCode == 400 ||
          response.statusCode == 401 ||
          response.statusCode == 403) {
        return const GeminiConnectionResult.invalidKey();
      } else if (response.statusCode == 429) {
        return const GeminiConnectionResult.quotaExceeded();
      } else {
        return GeminiConnectionResult.error(
          'Google Gemini returned error ${response.statusCode}. Please try again.',
        );
      }
    } on SocketException {
      return const GeminiConnectionResult.networkUnavailable();
    } on HttpException {
      return const GeminiConnectionResult.networkUnavailable();
    } catch (e) {
      final str = e.toString().toLowerCase();
      if (str.contains('socketexception') ||
          str.contains('failed host lookup') ||
          str.contains('network') ||
          str.contains('connection refused') ||
          str.contains('clientexception')) {
        return const GeminiConnectionResult.networkUnavailable();
      }
      return GeminiConnectionResult.error('Connection error: $e');
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
    _logger.warning(
      'UserGeminiApiProvider',
      'Gemini API returned error',
      params: {'statusCode': statusCode},
    );

    if (statusCode == 400 || statusCode == 401 || statusCode == 403) {
      // Check if it's an API key error
      throw const AppFailure.credentialInvalid(
        detail:
            'Google rejected the API key or the project lacks Gemini API access.',
        action:
            'Check your Gemini API key in Google AI Studio and update it in Settings.',
      );
    } else if (statusCode == 429) {
      throw const AppFailure.quotaExceeded(
        action:
            'Check your Gemini API project quota limits or try again later.',
      );
    } else if (statusCode >= 500) {
      throw AppFailure.providerError(
        detail:
            'Google Gemini servers returned error $statusCode. Please try again.',
      );
    } else {
      throw AppFailure.providerError(
        detail: 'HTTP request failed with status $statusCode',
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
      request.add(utf8.encode(body));
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
