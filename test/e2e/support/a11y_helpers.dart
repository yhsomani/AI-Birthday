/// Accessibility & semantics verification helpers for opaque-box E2E tests.
library;

import 'package:flutter_test/flutter_test.dart';

/// Asserts the rendered widget box for [finder] meets the minimum WCAG/Material
/// touch target size (`>= 48 × 48 dp` by default).
void expectMinTapTarget(
  WidgetTester tester,
  Finder finder, {
  double minWidth = 48.0,
  double minHeight = 48.0,
  String? reason,
}) {
  expect(finder, findsOneWidget, reason: reason);
  final size = tester.getSize(finder);
  expect(
    size.width,
    greaterThanOrEqualTo(minWidth),
    reason: '${reason ?? 'Tap target'} width ${size.width} < $minWidth dp',
  );
  expect(
    size.height,
    greaterThanOrEqualTo(minHeight),
    reason: '${reason ?? 'Tap target'} height ${size.height} < $minHeight dp',
  );
}

/// Asserts that the semantics node for [finder] exposes a label matching [expected].
void expectSemanticLabel(
  WidgetTester tester,
  Finder finder,
  Pattern expected, {
  String? reason,
}) {
  final handle = tester.ensureSemantics();
  try {
    expect(finder, findsOneWidget, reason: reason);
    final semantics = tester.getSemantics(finder);
    expect(
      semantics.label,
      matches(expected),
      reason:
          reason ??
          'Expected semantic label matching $expected, got "${semantics.label}"',
    );
  } finally {
    handle.dispose();
  }
}

/// Asserts that the semantics node for [finder] is marked `isLiveRegion`
/// (required on Studio error recovery banners).
void expectLiveRegion(
  WidgetTester tester,
  Finder finder, {
  Pattern? expectedText,
  String? reason,
}) {
  final handle = tester.ensureSemantics();
  try {
    expect(finder, findsOneWidget, reason: reason);
    final semantics = tester.getSemantics(finder);
    expect(
      semantics.flagsCollection.isLiveRegion,
      isTrue,
      reason: reason ?? 'Expected SemanticsNode to have isLiveRegion == true',
    );
    if (expectedText != null) {
      expect(
        semantics.label,
        matches(expectedText),
        reason:
            reason ??
            'Expected liveRegion label matching $expectedText, got "${semantics.label}"',
      );
    }
  } finally {
    handle.dispose();
  }
}

/// Asserts that the semantics node for [finder] is marked `isHeader`.
void expectSemanticHeader(
  WidgetTester tester,
  Finder finder, {
  String? reason,
}) {
  final handle = tester.ensureSemantics();
  try {
    expect(finder, findsOneWidget, reason: reason);
    final semantics = tester.getSemantics(finder);
    expect(
      semantics.flagsCollection.isHeader,
      isTrue,
      reason: reason ?? 'Expected SemanticsNode to have isHeader == true',
    );
  } finally {
    handle.dispose();
  }
}
