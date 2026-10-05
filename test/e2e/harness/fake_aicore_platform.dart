import 'dart:async';
import 'package:flutter/services.dart';
import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/core/platform/gemini_nano_platform.dart';

/// Test double implementing GeminiNanoPlatform simulating the Kotlin MainActivity
/// AICore platform channel bridge for R4 requirement testing.
class FakeAICorePlatform implements GeminiNanoPlatform {
  FakeAICorePlatform({
    NanoState initialState = NanoState.unavailable,
    this.generationOutput = 'Happy Birthday! Wishing you a fantastic day!',
  }) : _state = initialState;

  NanoState _state;
  final StreamController<NanoState> _controller =
      StreamController<NanoState>.broadcast();

  String generationOutput;
  int generateCallCount = 0;
  int startDownloadCallCount = 0;
  String? lastPromptReceived;
  bool shouldThrowPlatformException = false;
  String platformErrorCode = 'NANO_UNAVAILABLE';
  String platformErrorMessage = 'AICore is not ready for inference';

  void setState(NanoState newState) {
    _state = newState;
    _controller.add(newState);
  }

  @override
  Future<NanoState> currentState() async => _state;

  @override
  Stream<NanoState> get stateChanges => _controller.stream;

  @override
  Future<NanoState> startDownload() async {
    startDownloadCallCount++;
    if (_state == NanoState.downloadable || _state == NanoState.unavailable) {
      setState(NanoState.downloading);
    }
    return _state;
  }

  @override
  Future<NanoGenerationResult> generate(String prompt) async {
    generateCallCount++;
    lastPromptReceived = prompt;

    if (shouldThrowPlatformException) {
      throw PlatformException(
        code: platformErrorCode,
        message: platformErrorMessage,
      );
    }

    if (_state != NanoState.available) {
      throw const AppFailure.nanoUnavailable();
    }

    return NanoGenerationResult(text: generationOutput);
  }

  @override
  Future<void> dispose() async {
    await _controller.close();
  }
}
