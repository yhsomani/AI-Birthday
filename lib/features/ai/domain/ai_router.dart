/// Authoritative AI Router implementing the routing rule defined in SSOT §5.
library;

import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/ai/domain/ai_prompt_builder.dart';
import 'package:ai_birthday/features/ai/domain/ai_provider.dart';
import 'package:ai_birthday/features/subscription/domain/entitlement.dart';

/// Routes AI requests strictly according to the SSOT §5 logic:
/// 1. Entitlement check -> If not active, throw [AppFailure.lockedAi].
/// 2. User Gemini API key -> If configured, route to [userGeminiProvider].
/// 3. On-device Gemini Nano -> If available, route to [nanoProvider].
/// 4. Otherwise -> throw [AppFailure.credentialMissing] or [AppFailure.nanoUnavailable].
class AiRouter {
  AiRouter({
    required CredentialStorage credentialStorage,
    required AiMessageProvider userGeminiProvider,
    AiMessageProvider? nanoProvider,
    Future<GeminiNanoStatus> Function()? nanoStatusChecker,
    AppLogger? logger,
  }) : _credentialStorage = credentialStorage,
       _userGeminiProvider = userGeminiProvider,
       _nanoProvider = nanoProvider,
       _nanoStatusChecker = nanoStatusChecker,
       _logger = logger ?? ConsoleAppLogger();

  final CredentialStorage _credentialStorage;
  final AiMessageProvider _userGeminiProvider;
  final AiMessageProvider? _nanoProvider;
  final Future<GeminiNanoStatus> Function()? _nanoStatusChecker;
  final AppLogger _logger;

  /// Generates a birthday message, strictly enforcing the entitlement and provider routing sequence.
  Future<AiGenerationResult> generate({
    required AiGenerationRequest request,
    required UserEntitlement entitlement,
    bool forceNano = false,
  }) async {
    // Application entitlement is always checked before provider lookup.
    // A personal Gemini key never bypasses the application's AI entitlement.
    if (!entitlement.canUseAi) {
      _logger.info('AiRouter', 'AI request blocked: no active entitlement');
      throw const AppFailure.lockedAi(
        action: 'Subscribe to AI-Birthday Pro to use AI-powered drafting.',
      );
    }

    final hasUserKey = await _credentialStorage.hasGeminiApiKey();

    // If explicit Nano requested or no user key configured, check Nano
    if (forceNano || !hasUserKey) {
      final nanoStatus = _nanoStatusChecker != null
          ? await _nanoStatusChecker()
          : GeminiNanoStatus.unavailable;

      if (nanoStatus == GeminiNanoStatus.available && _nanoProvider != null) {
        _logger.info('AiRouter', 'Routing request to Gemini Nano');
        return _nanoProvider.generateMessage(request);
      }

      if (forceNano) {
        throw const AppFailure.nanoUnavailable(
          action: 'Gemini Nano is not ready or unavailable on this device.',
        );
      }
    }

    // 2. User Gemini API key check
    if (hasUserKey) {
      _logger.info('AiRouter', 'Routing request to User Gemini API');
      return _userGeminiProvider.generateMessage(request);
    }

    // 3. Neither provider is available
    _logger.info('AiRouter', 'AI request failed: No provider available');
    throw const AppFailure.credentialMissing(
      action:
          'AI is not currently available on this device. Add your Google Gemini API key in Settings, or use Gemini Nano when available on this device.',
    );
  }
}
