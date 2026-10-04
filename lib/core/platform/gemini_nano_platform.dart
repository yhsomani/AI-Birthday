/// Typed Flutter/Kotlin platform boundary for Gemini Nano.
///
/// This defines the domain-facing contract only. The concrete implementation
/// lives in the Kotlin layer (AICore / ML Kit GenAI) behind this interface and
/// is delivered in Phase 4. Nothing here fabricates Nano availability; a real
/// provider must be detected through the platform instead of assumed.
library;

/// Normalized Gemini Nano states (SSOT §5).
///
/// Mapped from native AICore/ML Kit states into this authoritative set:
/// AVAILABLE, DOWNLOADABLE, DOWNLOADING, NOT_READY, UNAVAILABLE, BUSY,
/// QUOTA_EXCEEDED, ERROR.
enum NanoState {
  available,
  downloadable,
  downloading,
  notReady,
  unavailable,
  busy,
  quotaExceeded,
  error;

  bool get isUsable => this == NanoState.available;
  bool get isBusy => this == NanoState.busy || this == NanoState.downloading;
}

/// A cataly regulatable generation handle so callers can cancel.
abstract interface class NanoGenerationHandle {
  /// Requests cancellation of the in-flight generation. Best-effort.
  Future<void> cancel();
}

/// Result of a Nano generation.
class NanoGenerationResult {
  const NanoGenerationResult({required this.text});
  final String text;
}

/// Platform implementation contract for Gemini Nano.
///
/// The production implementation is a Kotlin bridge over AICore / ML Kit
/// GenAI. Tests supply a fake that models state transitions explicitly.
abstract interface class GeminiNanoPlatform {
  /// Current normalized Nano state.
  Future<NanoState> currentState();

  /// Stream of normalized state changes.
  Stream<NanoState> get stateChanges;

  /// Starts (or resumes) the model download. Returns the resulting state.
  Future<NanoState> startDownload();

  /// Runs a single generation. May only be called while state is
  /// [NanoState.available]; otherwise the platform must throw a normalized
  /// failure (e.g. busy/unavailable).
  Future<NanoGenerationResult> generate(String prompt);

  /// Releases native resources (lifecycle cleanup).
  Future<void> dispose();
}
