/// Responsive viewport presets, keyboard simulation, navigation-destination
/// lookup, and strict layout-overflow assertions for opaque-box E2E tests.
library;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// Standard viewport & text-scale preset matching `PROJECT.md §2` and `SCOPE.md`.
class E2eViewport {
  const E2eViewport({
    required this.name,
    required this.logicalSize,
    required this.devicePixelRatio,
    this.textScale = 1.0,
  });

  final String name;
  final Size logicalSize;
  final double devicePixelRatio;
  final double textScale;

  /// Narrowest compact phone (`320 × 568` logical dp at `1.3×` DPR & `1.3×` text scale).
  static const compact320 = E2eViewport(
    name: 'compact_320x568_1.3x',
    logicalSize: Size(320, 568),
    devicePixelRatio: 1.3,
    textScale: 1.3,
  );

  /// Compact phone at accessibility 200% font scale (`360 × 740` logical dp at `2.0×`).
  static const compact360 = E2eViewport(
    name: 'compact_360x740_2.0x',
    logicalSize: Size(360, 740),
    devicePixelRatio: 2.0,
    textScale: 2.0,
  );

  /// Standard modern compact phone (`412 × 915` logical dp at `1.0×` DPR).
  static const phone412 = E2eViewport(
    name: 'phone_412x915_1.0x',
    logicalSize: Size(412, 915),
    devicePixelRatio: 1.0,
    textScale: 1.0,
  );

  /// Medium tablet (`600 × 960` logical dp at `1.0×`).
  static const medium600 = E2eViewport(
    name: 'medium_600x960_1.0x',
    logicalSize: Size(600, 960),
    devicePixelRatio: 1.0,
    textScale: 1.0,
  );

  /// Expanded tablet/desktop (`1024 × 768` logical dp at `1.0×`).
  static const expanded1024 = E2eViewport(
    name: 'expanded_1024x768_1.0x',
    logicalSize: Size(1024, 768),
    devicePixelRatio: 1.0,
    textScale: 1.0,
  );

  /// Wide desktop (`1280 × 800` logical dp at `1.0×`).
  static const expanded1280 = E2eViewport(
    name: 'expanded_1280x800_1.0x',
    logicalSize: Size(1280, 800),
    devicePixelRatio: 1.0,
    textScale: 1.0,
  );

  static const List<E2eViewport> allPresets = [
    compact320,
    compact360,
    medium600,
    expanded1024,
    expanded1280,
  ];
}

/// Configures [tester.view] so `logicalSize = physicalSize / devicePixelRatio`.
///
/// Registers `addTearDown` hooks to restore `physicalSize`, `devicePixelRatio`,
/// `textScaleFactorTestValue`, `platformBrightnessTestValue`, and `viewInsets`.
void applyViewport(
  WidgetTester tester,
  E2eViewport viewport, {
  Brightness brightness = Brightness.light,
  double imeBottomInset = 0,
}) {
  final dpr = viewport.devicePixelRatio;
  tester.view.devicePixelRatio = dpr;
  tester.view.physicalSize = Size(
    viewport.logicalSize.width * dpr,
    viewport.logicalSize.height * dpr,
  );
  tester.platformDispatcher.textScaleFactorTestValue = viewport.textScale;
  tester.platformDispatcher.platformBrightnessTestValue = brightness;

  if (imeBottomInset > 0) {
    simulateKeyboard(tester, logicalHeight: imeBottomInset);
  } else {
    tester.view.resetViewInsets();
  }

  addTearDown(() {
    tester.view.resetPhysicalSize();
    tester.view.resetDevicePixelRatio();
    tester.view.resetViewInsets();
    tester.platformDispatcher.clearTextScaleFactorTestValue();
    tester.platformDispatcher.clearPlatformBrightnessTestValue();
  });
}

/// Simulates a software keyboard (IME) inset scaled by the active DPR.
void simulateKeyboard(WidgetTester tester, {double logicalHeight = 300.0}) {
  final dpr = tester.view.devicePixelRatio;
  tester.view.viewInsets = FakeViewPadding(
    bottom: logicalHeight * dpr,
    left: 0,
    right: 0,
    top: 0,
  );
}

/// Dismisses the simulated software keyboard.
void dismissKeyboard(WidgetTester tester) {
  tester.view.resetViewInsets();
}

/// Finds a primary shell navigation destination label inside either
/// `NavigationBar` (`width < 840dp`) or `NavigationRail` (`width >= 840dp`).
Finder findNavDestination(String label) {
  final navBar = find.byType(NavigationBar);
  if (navBar.evaluate().isNotEmpty) {
    return find.descendant(of: navBar, matching: find.text(label));
  }
  final navRail = find.byType(NavigationRail);
  if (navRail.evaluate().isNotEmpty) {
    return find.descendant(of: navRail, matching: find.text(label));
  }
  return find.text(label);
}

/// Executes [body] and fails if any `RenderFlex overflowed`, layout
/// [FlutterError], or uncaught widget exception (`tester.takeException()`) occurs.
///
/// Non-overflow `FlutterError`s are forwarded to the prior `FlutterError.onError`
/// handler so real failures are never silently swallowed.
Future<void> expectNoLayoutOverflows(
  WidgetTester tester,
  Future<void> Function() body, {
  String? reason,
}) async {
  final overflows = <FlutterErrorDetails>[];
  final originalOnError = FlutterError.onError;

  FlutterError.onError = (FlutterErrorDetails details) {
    final msg = details.exceptionAsString();
    if (msg.contains('RenderFlex overflowed') ||
        msg.contains('A RenderFlex overflowed') ||
        msg.contains('overflowed by')) {
      overflows.add(details);
    } else {
      originalOnError?.call(details);
    }
  };

  try {
    await body();
  } finally {
    FlutterError.onError = originalOnError;
  }

  expect(
    overflows,
    isEmpty,
    reason:
        '${reason ?? 'Unexpected layout overflow'}: '
        '${overflows.map((e) => e.exceptionAsString()).join('\n')}',
  );
  expect(
    tester.takeException(),
    isNull,
    reason: reason ?? 'Unexpected framework exception during layout check',
  );
}
