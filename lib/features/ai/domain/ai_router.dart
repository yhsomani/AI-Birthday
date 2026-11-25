/// Authoritative AI Router implementing the routing rule defined in legacy SSOT §5 (PRODUCT.md §16.7).
library;

import 'dart:async';

import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/ai/domain/ai_prompt_builder.dart';
import 'package:ai_birthday/features/ai/domain/ai_provider.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

/// Routes AI requests according to the legacy SSOT §5 (PRODUCT.md §16.7) logic:
/// 1. Active application entitlement is required before credential access or
///    provider selection; a personal Gemini API key never bypasses Pro.
/// 2. After entitlement passes, use the personal Gemini API key when configured,
///    unless the caller explicitly forces Nano.
/// 3. Without a personal key, use Nano only when it is available on this device.
/// 4. Forced Nano requires entitlement and device support; otherwise the request
///    returns a typed locked/unavailable error.
class AiRouter {
  AiRouter({
    required this._credentialStorage,
    required this._userGeminiProvider,
    this._nanoProvider,
    this._nanoStatusChecker,
    AppLogger? logger,
    this.generationTimeout = const Duration(seconds: 30),
  }) : _logger = logger ?? ConsoleAppLogger();

  final CredentialStorage _credentialStorage;
  final AiMessageProvider _userGeminiProvider;
  final AiMessageProvider? _nanoProvider;
  final Future<GeminiNanoStatus> Function()? _nanoStatusChecker;
  final AppLogger _logger;

  /// Upper bound for a single provider request; maps to a retryable
  /// [AppFailureCode.aiTimeout]. Injectable so tests do not wait the real
  /// production window.
  final Duration generationTimeout;

  /// Generates a birthday message, strictly enforcing the entitlement and provider routing sequence.
  Future<AiGenerationResult> generate({
    required AiGenerationRequest request,
    required UserEntitlement entitlement,
    bool forceNano = false,
  }) async {
    // Enforce the application entitlement before reading credentials, probing
    // device capabilities, selecting a provider, or invoking an AI provider.
    if (!entitlement.canUseAi) {
      _logger.info('AiRouter', 'AI request blocked: no active entitlement');
      throw const AppFailure.lockedAi(
        action: 'Subscribe to AI-Birthday Pro to use AI-powered drafting.',
      );
    }

    final hasUserKey = await _withTimeout(_credentialStorage.hasGeminiApiKey());

    // If explicit Nano requested or no user key configured, check Nano
    if (forceNano || !hasUserKey) {
      final nanoStatus = _nanoStatusChecker != null
          ? await _withTimeout(_nanoStatusChecker())
          : GeminiNanoStatus.unavailable;

      if (nanoStatus == GeminiNanoStatus.available && _nanoProvider != null) {
        _logger.info('AiRouter', 'Routing request to Gemini Nano');
        return _withTimeout(_nanoProvider.generateMessage(request));
      }

      if (forceNano) {
        throw const AppFailure.nanoUnavailable(
          action: 'Gemini Nano is not ready or unavailable on this device.',
        );
      }
    }

    // User Gemini API key route (header step 2)
    if (hasUserKey) {
      _logger.info('AiRouter', 'Routing request to User Gemini API');
      return _withTimeout(_userGeminiProvider.generateMessage(request));
    }

    // No provider available
    _logger.info('AiRouter', 'AI request failed: No provider available');
    throw const AppFailure.credentialMissing(
      action:
          'AI is not currently available on this device. Add your Google Gemini API key in Settings, or use Gemini Nano when available on this device.',
    );
  }

  /// Bounds one awaited step (provider call, credential read, or Nano status
  /// probe) so a stalled call (no response, no error) cannot hold a durable job
  /// in `running` forever. Timeouts map to the retryable
  /// [AppFailureCode.aiTimeout] so the job worker retries it.
  Future<T> _withTimeout<T>(Future<T> request) async {
    try {
      return await request.timeout(generationTimeout);
    } on TimeoutException {
      _logger.info('AiRouter', 'AI router step timed out; will retry.');
      throw const AppFailure.timeout();
    }
  }
}
