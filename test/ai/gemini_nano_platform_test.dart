import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/core/errors/app_failure.dart';
import 'package:ai_birthday/core/platform/gemini_nano_platform.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('test/gemini_nano');

  setUpAll(() {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
  });
  tearDownAll(() {
    debugDefaultTargetPlatformOverride = null;
  });

  Future<void> expectNativeFailure(
    String code,
    AppFailureCode expected, {
    bool retryable = false,
  }) async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          throw PlatformException(code: code, message: 'native detail');
        });
    final platform = MethodChannelGeminiNanoPlatform(
      channel: channel,
      isLiveAndroid: () => true,
    );

    await expectLater(
      platform.generate('prompt'),
      throwsA(
        isA<AppFailure>()
            .having((failure) => failure.code, 'code', expected)
            .having(
              (failure) => failure.userMessage,
              'user message',
              isNot(contains('native detail')),
            )
            .having((failure) => failure.isRetryable, 'retryable', retryable),
      ),
    );
    await platform.dispose();
  }

  test(
    'maps native not-ready status to non-retryable availability failure',
    () {
      return expectNativeFailure(
        'NANO_NOT_READY',
        AppFailureCode.aiNanoUnavailable,
      );
    },
  );

  test('maps empty native response to a non-retryable response failure', () {
    return expectNativeFailure(
      'NANO_EMPTY_RESPONSE',
      AppFailureCode.aiInvalidResponse,
    );
  });

  test('maps native generation errors to a retryable Nano failure', () {
    return expectNativeFailure(
      'NANO_GENERATION_ERROR',
      AppFailureCode.aiNanoGenerationFailed,
      retryable: true,
    );
  });
}
