import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// Helper for executing responsive layout and accessibility verification
/// at 360dp width and 1.5x font scale per R5 acceptance criteria.
class ResponsiveTester {
  static const Size narrowPhoneSize = Size(360, 640);
  static const double accessibilityFontScale = 1.5;

  /// Sets up the tester viewport for narrow screen (360dp) and 1.5x text scale.
  static void setupNarrowAccessibilityViewport(WidgetTester tester) {
    tester.view.physicalSize = narrowPhoneSize;
    tester.view.devicePixelRatio = 1.0;
    tester.platformDispatcher.textScaleFactorTestValue = accessibilityFontScale;

    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
      tester.platformDispatcher.clearTextScaleFactorTestValue();
      tester.view.resetViewInsets();
    });
  }

  /// Simulates software keyboard (IME) display with the given height (default 300dp).
  static void simulateKeyboardActive(
    WidgetTester tester, {
    double keyboardHeight = 300.0,
  }) {
    tester.view.viewInsets = FakeViewPadding(
      bottom: keyboardHeight,
      left: 0,
      right: 0,
      top: 0,
    );
  }

  /// Simulates keyboard dismissal.
  static void simulateKeyboardDismissed(WidgetTester tester) {
    tester.view.resetViewInsets();
  }

  /// Captures all RenderFlex overflow errors during a widget pump block.
  static List<FlutterErrorDetails> captureRenderFlexOverflows(
    void Function() action,
  ) {
    final overflows = <FlutterErrorDetails>[];
    final originalOnError = FlutterError.onError;

    FlutterError.onError = (FlutterErrorDetails details) {
      if (details.exceptionAsString().contains('A RenderFlex overflowed') ||
          details.exceptionAsString().contains('RenderFlex overflowed')) {
        overflows.add(details);
      } else {
        originalOnError?.call(details);
      }
    };

    try {
      action();
    } finally {
      FlutterError.onError = originalOnError;
    }

    return overflows;
  }
}
