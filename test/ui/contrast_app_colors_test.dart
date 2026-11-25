/// WCAG contrast gate for the `AppColors`-based components that the palette
/// test does not cover (audit A3-011 / A3-013): countdown chips, celebration
/// avatars, section badges and Settings secondary text.
///
/// Colours are read from the rendered widgets, so a change to the component
/// code is caught, not just a change to a token. Translucent fills are
/// composited over each surface the component can sit on.
library;

import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/app/theme/app_theme.dart';
import 'package:ai_birthday/shared/design_system/design_system.dart';

double _channel(double s) =>
    s <= 0.04045 ? s / 12.92 : math.pow((s + 0.055) / 1.055, 2.4).toDouble();

double _luminance(Color c) =>
    0.2126 * _channel(c.r) + 0.7152 * _channel(c.g) + 0.0722 * _channel(c.b);

/// WCAG 2.x contrast ratio between two opaque colours.
double contrast(Color a, Color b) {
  final la = _luminance(a);
  final lb = _luminance(b);
  return (math.max(la, lb) + 0.05) / (math.min(la, lb) + 0.05);
}

/// Source-over composite of [fg] (possibly translucent) on opaque [bg].
Color over(Color fg, Color bg) => Color.alphaBlend(fg, bg);

Color _opaque(Color c, Color bg) => c.a >= 1 ? c : over(c, bg);

void _expectRatio(String what, Color fg, Color bg, double min) {
  final solidBg = bg;
  final solidFg = _opaque(fg, solidBg);
  final ratio = contrast(solidFg, solidBg);
  expect(
    ratio,
    greaterThanOrEqualTo(min),
    reason: '$what: ${ratio.toStringAsFixed(2)}:1, need $min:1',
  );
}

Future<void> _pump(WidgetTester tester, Brightness b, Widget child) =>
    tester.pumpWidget(
      MaterialApp(
        theme: b == Brightness.dark ? AppTheme.dark : AppTheme.light,
        home: Scaffold(body: Center(child: child)),
      ),
    );

/// Surfaces a chip/badge can be painted over, per mode.
List<Color> _surfaces(Brightness b) => b == Brightness.dark
    ? [
        AppPalette.dark.background,
        AppPalette.dark.surface,
        AppPalette.dark.surfaceAlt,
        AppColors.darkBackground,
        AppColors.darkSurface,
      ]
    : [
        AppPalette.light.background,
        AppPalette.light.surface,
        AppPalette.light.surfaceAlt,
        AppColors.lightBackground,
        AppColors.lightSurface,
      ];

void main() {
  for (final brightness in Brightness.values) {
    final mode = brightness.name;

    group('[$mode] CountdownChip text ≥ 4.5:1 (A3-011)', () {
      const cases = {
        'today': 0,
        'tomorrow': 1,
        'within a week (amber)': 3,
        'later (neutral)': 20,
      };
      for (final entry in cases.entries) {
        testWidgets(entry.key, (tester) async {
          await _pump(
            tester,
            brightness,
            CountdownChip(daysUntil: entry.value),
          );
          final label = CountdownChip.labelFor(daysUntil: entry.value);
          final fg = tester.widget<Text>(find.text(label)).style!.color!;
          final fill =
              (tester
                          .widget<Container>(
                            find.descendant(
                              of: find.byType(CountdownChip),
                              matching: find.byType(Container),
                            ),
                          )
                          .decoration!
                      as BoxDecoration)
                  .color!;
          for (final surface in _surfaces(brightness)) {
            _expectRatio(
              '${entry.key} on $surface',
              fg,
              over(fill, surface),
              4.5,
            );
          }
        });
      }
    });

    testWidgets('[$mode] CelebrationCard avatar initial ≥ 4.5:1 (A3-011)', (
      tester,
    ) async {
      for (final isToday in [true, false]) {
        await _pump(
          tester,
          brightness,
          CelebrationCard(
            name: 'Asha',
            subtitle: 'Turns 30',
            daysUntil: isToday ? 0 : 12,
            isToday: isToday,
          ),
        );
        final avatar = tester.widget<CircleAvatar>(find.byType(CircleAvatar));
        _expectRatio(
          'avatar isToday=$isToday',
          avatar.foregroundColor!,
          avatar.backgroundColor!,
          4.5,
        );
      }
    });

    testWidgets('[$mode] section-header badge ≥ 4.5:1', (tester) async {
      for (final accent in [true, false]) {
        await _pump(
          tester,
          brightness,
          AppSectionHeader(title: 'People', count: 3, isAccent: accent),
        );
        final fg = tester.widget<Text>(find.text('3')).style!.color!;
        final fill =
            (tester
                        .widget<Container>(
                          find
                              .ancestor(
                                of: find.text('3'),
                                matching: find.byType(Container),
                              )
                              .first,
                        )
                        .decoration!
                    as BoxDecoration)
                .color!;
        for (final surface in _surfaces(brightness)) {
          _expectRatio(
            'badge accent=$accent on $surface',
            fg,
            over(fill, surface),
            4.5,
          );
        }
      }
    });

    test('[$mode] AppColors secondary text on its surfaces ≥ 4.5:1', () {
      final isDark = brightness == Brightness.dark;
      final secondary = isDark
          ? AppColors.darkTextSecondary
          : AppColors.lightTextSecondary;
      final surfaces = isDark
          ? [
              AppColors.darkBackground,
              AppColors.darkSurface,
              AppColors.darkSurfaceVariant,
            ]
          : [
              AppColors.lightBackground,
              AppColors.lightSurface,
              AppColors.lightSurfaceVariant,
            ];
      for (final s in surfaces) {
        _expectRatio('textSecondary on $s', secondary, s, 4.5);
      }
    });

    test('[$mode] Settings secondary text (theme token) ≥ 4.5:1 (A3-013)', () {
      final p = brightness == Brightness.dark
          ? AppPalette.dark
          : AppPalette.light;
      for (final s in [p.background, p.surface, p.surfaceAlt]) {
        _expectRatio('palette.textSecondary on $s', p.textSecondary, s, 4.5);
      }
    });
  }

  test('regression: the replaced colours really were failing', () {
    // Documents why the change was needed (audit A3-011 / A3-013).
    expect(
      contrast(AppColors.accentAmber, AppColors.accentAmberContainer),
      lessThan(3.0),
    );
    expect(
      contrast(Colors.white, AppColors.primaryTerracottaLight),
      lessThan(3.0),
    );
    expect(
      contrast(const Color(0xFF757575), AppPalette.dark.surface),
      lessThan(4.5),
    );
  });
}
