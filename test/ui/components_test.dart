/// Behavioral tests for the design-system component library (Phase 2).
///
/// Rendering regressions are gated by the golden tests in `test/ui/goldens`
/// (tagged `golden`, local-only — CI runs `--exclude-tags golden`).
library;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/theme/app_theme.dart';
import 'package:ai_birthday/ui/design_system/design_system.dart';

import 'component_gallery.dart';

Widget _app(Widget child) => MaterialApp(
  theme: AppTheme.light,
  home: Scaffold(body: child),
);

void main() {
  group('toneForCountdown', () {
    test('today and overdue map to primary', () {
      expect(toneForCountdown(0), AppTone.primary);
      expect(toneForCountdown(-2), AppTone.primary);
    });

    test('within a week maps to warning', () {
      expect(toneForCountdown(1), AppTone.warning);
      expect(toneForCountdown(7), AppTone.warning);
    });

    test('beyond a week maps to neutral', () {
      expect(toneForCountdown(8), AppTone.neutral);
      expect(toneForCountdown(400), AppTone.neutral);
    });
  });

  test('AppErrorState asserts a retry label with an onRetry', () {
    expect(
      () => AppErrorState(message: 'Boom', onRetry: () {}),
      throwsAssertionError,
    );
  });

  test('AppBanner asserts an action label with an onAction', () {
    expect(
      () => AppBanner(message: 'Hi', onAction: () {}),
      throwsAssertionError,
    );
  });

  testWidgets('AppErrorState retry fires and meets the 48dp target', (
    tester,
  ) async {
    var retried = false;
    await tester.pumpWidget(
      _app(
        AppErrorState(
          message: 'Something went wrong',
          retryLabel: 'Try again',
          onRetry: () => retried = true,
        ),
      ),
    );

    final button = find.widgetWithText(FilledButton, 'Try again');
    expect(button, findsOneWidget);
    expect(tester.getSize(button).height, greaterThanOrEqualTo(48));
    await tester.tap(button);
    expect(retried, isTrue);
  });

  testWidgets('AppBanner action fires', (tester) async {
    var opened = false;
    await tester.pumpWidget(
      _app(
        AppBanner(
          message: 'Free plan',
          actionLabel: 'Upgrade',
          onAction: () => opened = true,
        ),
      ),
    );

    await tester.tap(find.widgetWithText(TextButton, 'Upgrade'));
    expect(opened, isTrue);
  });

  testWidgets('SectionHeader announces as a header', (tester) async {
    final handle = tester.ensureSemantics();

    await tester.pumpWidget(
      _app(const SectionHeader(title: 'Section', count: 3)),
    );

    expect(find.text('SECTION'), findsOneWidget);
    expect(
      tester.getSemantics(find.text('SECTION')),
      isSemantics(isHeader: true),
    );
    handle.dispose();
  });

  testWidgets('tappable AppCard exposes button semantics', (tester) async {
    final handle = tester.ensureSemantics();
    var tapped = false;

    await tester.pumpWidget(
      _app(AppCard(onTap: () => tapped = true, child: const Text('Card body'))),
    );

    expect(
      tester.getSemantics(find.text('Card body')),
      isSemantics(isButton: true, hasTapAction: true),
    );
    await tester.tap(find.text('Card body'));
    expect(tapped, isTrue);
    handle.dispose();
  });

  testWidgets('AppChip paints the tone fill under its label', (tester) async {
    await tester.pumpWidget(
      _app(const AppChip(label: 'Today', tone: AppTone.primary)),
    );

    expect(find.text('Today'), findsOneWidget);
    final chip = tester.widget<Container>(
      find.descendant(
        of: find.byType(AppChip),
        matching: find.byType(Container),
      ),
    );
    final decoration = chip.decoration! as BoxDecoration;
    expect(decoration.color, AppPalette.light.primaryContainer);
  });

  group('gallery', () {
    testWidgets('renders at 200% text scale without overflow', (tester) async {
      tester.view.physicalSize = const Size(400, 4600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(
        const ComponentGallery(textScaler: TextScaler.linear(2)),
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('renders in dark theme without exceptions', (tester) async {
      tester.view.physicalSize = const Size(400, 2400);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(
        const ComponentGallery(brightness: Brightness.dark),
      );
      expect(tester.takeException(), isNull);
    });
  });
}
