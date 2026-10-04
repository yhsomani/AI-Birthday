/// Gemini Nano on-device AI provider (SSOT §4, §5, §25).
library;

import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/platform/gemini_nano_platform.dart';
import 'package:ai_birthday/features/ai/domain/ai_prompt_builder.dart';
import 'package:ai_birthday/features/ai/domain/ai_provider.dart';

/// Concrete on-device AI provider using Gemini Nano / AICore / ML Kit GenAI.
class GeminiNanoProvider implements AiMessageProvider {
  GeminiNanoProvider({
    required GeminiNanoPlatform platform,
    AiPromptBuilder? promptBuilder,
    AppLogger? logger,
  }) : _platform = platform,
       _promptBuilder = promptBuilder ?? const AiPromptBuilder(),
       _logger = logger ?? ConsoleAppLogger();

  final GeminiNanoPlatform _platform;
  final AiPromptBuilder _promptBuilder;
  final AppLogger _logger;

  @override
  String get providerId => 'gemini_nano';

  @override
  Future<AiGenerationResult> generateMessage(
    AiGenerationRequest request,
  ) async {
    _logger.info('GeminiNano', 'Requesting generation on-device via AICore');

    final state = await _platform.currentState();
    if (!state.isUsable) {
      _logger.warning(
        'GeminiNano',
        'Model not in usable state',
        params: {'state': state.name},
      );
      throw AppFailure.nanoUnavailable(
        action: switch (state) {
          NanoState.downloadable =>
            'Model download required before generation.',
          NanoState.downloading =>
            'Model is currently downloading. Please wait.',
          NanoState.busy =>
            'On-device model is busy. Please try again in a moment.',
          _ => 'Gemini Nano is unavailable on this device.',
        },
      );
    }

    try {
      final prompt = _promptBuilder.buildPrompt(request);
      final result = await _platform.generate(prompt);
      final trimmed = result.text.trim();

      if (trimmed.isEmpty) {
        throw const AppFailure.providerError(
          detail: 'Gemini Nano returned an empty response.',
        );
      }

      return AiGenerationResult(
        message: trimmed,
        providerType: providerId,
        modelName: 'Gemini Nano (AICore)',
      );
    } on AppFailure {
      rethrow;
    } catch (e, st) {
      _logger.error(
        'GeminiNano',
        'Generation error on device',
        error: e,
        stackTrace: st,
      );
      throw AppFailure.providerError(
        detail: 'On-device model error: ${e.toString()}',
      );
    }
  }
}

/// Fallback / Default platform implementation for Gemini Nano when running on
/// non-Android platforms or before native AICore connection is bound.
class DefaultGeminiNanoPlatform implements GeminiNanoPlatform {
  const DefaultGeminiNanoPlatform({
    NanoState initialState = NanoState.unavailable,
  }) : _initialState = initialState;

  final NanoState _initialState;

  @override
  Future<NanoState> currentState() async => _initialState;

  @override
  Stream<NanoState> get stateChanges => Stream.value(_initialState);

  @override
  Future<NanoState> startDownload() async => _initialState;

  @override
  Future<NanoGenerationResult> generate(String prompt) async {
    if (_initialState != NanoState.available) {
      throw const AppFailure.nanoUnavailable();
    }
    return const NanoGenerationResult(
      text:
          'Wishing you a truly wonderful and memorable birthday filled with happiness!',
    );
  }

  @override
  Future<void> dispose() async {}
}
