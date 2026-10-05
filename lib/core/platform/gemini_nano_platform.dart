/// Typed Flutter/Kotlin platform boundary for Gemini Nano.
///
/// This defines the domain-facing contract only. The concrete implementation
/// lives in the Kotlin layer (AICore / ML Kit GenAI) behind this interface and
/// is delivered in Phase 4. Nothing here fabricates Nano availability; a real
/// provider must be detected through the platform instead of assumed.
library;

import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:ai_birthday/core/errors/app_failure.dart';

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

/// Production MethodChannel platform bridge for Gemini Nano on Android.
class MethodChannelGeminiNanoPlatform implements GeminiNanoPlatform {
  MethodChannelGeminiNanoPlatform({
    MethodChannel channel = const MethodChannel(
      'com.yashsomani.ai_birthday/nano',
    ),
  }) : _channel = channel;

  final MethodChannel _channel;
  final StreamController<NanoState> _stateController =
      StreamController<NanoState>.broadcast();

  bool get _isLiveAndroid =>
      !kIsWeb &&
      defaultTargetPlatform == TargetPlatform.android &&
      WidgetsBinding.instance is WidgetsFlutterBinding;

  @override
  Future<NanoState> currentState() async {
    if (!_isLiveAndroid) {
      return NanoState.unavailable;
    }
    try {
      final res = await _channel.invokeMethod<String>('currentState');
      return switch (res) {
        'ready' || 'available' => NanoState.available,
        'downloadable' => NanoState.downloadable,
        'downloading' || 'downloading_model' => NanoState.downloading,
        'busy' => NanoState.busy,
        'quotaExceeded' => NanoState.quotaExceeded,
        _ => NanoState.unavailable,
      };
    } catch (_) {
      return NanoState.unavailable;
    }
  }

  @override
  Stream<NanoState> get stateChanges => _stateController.stream;

  @override
  Future<NanoState> startDownload() async {
    if (!_isLiveAndroid) {
      return NanoState.unavailable;
    }
    try {
      final res = await _channel.invokeMethod<String>('startDownload');
      final state = switch (res) {
        'ready' || 'available' => NanoState.available,
        'downloading' || 'downloading_model' => NanoState.downloading,
        _ => NanoState.unavailable,
      };
      _stateController.add(state);
      return state;
    } catch (_) {
      return NanoState.unavailable;
    }
  }

  @override
  Future<NanoGenerationResult> generate(String prompt) async {
    if (!_isLiveAndroid) {
      throw const AppFailure.nanoUnavailable();
    }
    try {
      final res = await _channel.invokeMethod<String>('generate', {
        'prompt': prompt,
      });
      return NanoGenerationResult(text: res ?? '');
    } on PlatformException catch (e) {
      throw AppFailure.providerError(
        detail: e.message ?? 'Nano platform error',
      );
    }
  }

  @override
  Future<void> dispose() async {
    await _stateController.close();
  }
}
