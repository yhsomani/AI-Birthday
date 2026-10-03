/// Abstract AI Provider interface for message generation (SSOT §4, §5).
library;

import 'package:ai_birthday/features/ai/domain/ai_prompt_builder.dart';

/// Result of an AI generation operation.
class AiGenerationResult {
  const AiGenerationResult({
    required this.message,
    required this.providerType,
    this.modelName,
  });

  /// The generated birthday greeting.
  final String message;

  /// Identifier of the provider ('user_gemini', 'gemini_nano', 'mock').
  final String providerType;

  /// Optional model name reported by the engine.
  final String? modelName;
}

/// Status of Gemini Nano on this device.
enum GeminiNanoStatus {
  available,
  downloadable,
  downloading,
  notReady,
  unavailable,
  busy,
  quotaExceeded,
  error;

  bool get isReady => this == GeminiNanoStatus.available;
}

/// Contract that all AI message providers must implement.
abstract interface class AiMessageProvider {
  /// Unique identifier of the provider ('user_gemini', 'gemini_nano').
  String get providerId;

  /// Generates a message text from the given [request].
  Future<AiGenerationResult> generateMessage(AiGenerationRequest request);
}
