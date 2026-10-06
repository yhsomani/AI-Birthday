/// WCAG AA contrast enforcement for the design-token palettes (Phase 1 gate).
///
/// Every text/background pair the design system ships must pass:
/// body text 4.5:1, large text / non-text accents 3:1 — light AND dark.
library;

import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/ui/design_system/app_tokens.dart';

double _luminance(Color c) {
  double channel(double s) =>
      s <= 0.04045 ? s / 12.92 : math.pow((s + 0.055) / 1.055, 2.4).toDouble();

  return 0.2126 * channel(c.r) + 0.7152 * channel(c.g) + 0.0722 * channel(c.b);
}

double contrastRatio(Color a, Color b) {
  final la = _luminance(a);
  final lb = _luminance(b);
  final lighter = math.max(la, lb);
  final darker = math.min(la, lb);
  return (lighter + 0.05) / (darker + 0.05);
}

void main() {
  void expectPass(String what, Color fg, Color bg, double min) {
    final ratio = contrastRatio(fg, bg);
    expect(
      ratio,
      greaterThanOrEqualTo(min),
      reason: '$what: ${ratio.toStringAsFixed(2)}:1, need $min:1',
    );
  }

  const palettes = {'light': AppPalette.light, 'dark': AppPalette.dark};

  for (final entry in palettes.entries) {
    final name = entry.key;
    final p = entry.value;

    group('[$name palette] WCAG AA', () {
      test('body text on every surface ≥ 4.5:1', () {
        for (final bg in [p.background, p.surface, p.surfaceAlt]) {
          expectPass('textPrimary on $bg', p.textPrimary, bg, 4.5);
          expectPass('textSecondary on $bg', p.textSecondary, bg, 4.5);
        }
      });

      test('button and container labels ≥ 4.5:1', () {
        expectPass('onPrimary on primary', p.onPrimary, p.primary, 4.5);
        expectPass(
          'onPrimaryContainer on primaryContainer',
          p.onPrimaryContainer,
          p.primaryContainer,
          4.5,
        );
      });

      test('status labels on their fills ≥ 4.5:1', () {
        expectPass('onSuccess on success', p.onSuccess, p.success, 4.5);
        expectPass('onWarning on warning', p.onWarning, p.warning, 4.5);
        expectPass('onDanger on danger', p.onDanger, p.danger, 4.5);
      });

      test('brand accents on surface ≥ 3:1', () {
        expectPass('primary on surface', p.primary, p.surface, 3.0);
        expectPass('accent on surface', p.accent, p.surface, 3.0);
        expectPass('info on surface', p.info, p.surface, 3.0);
      });

      test('tone accents on surfaceAlt ≥ 3:1', () {
        // Icons/banner accents: non-text but meaningful (WCAG 1.4.11).
        for (final tone in AppTone.values) {
          expectPass(
            '${tone.name} ink on surfaceAlt',
            tone.ink(p),
            p.surfaceAlt,
            3.0,
          );
        }
      });

      test('info as chip label on surfaceAlt ≥ 4.5:1', () {
        // AppTone.info is the only tone whose label sits on surfaceAlt as text
        // (others' label/fill pairs are covered by the tests above).
        expectPass(
          'info label on fill',
          AppTone.info.label(p),
          AppTone.info.fill(p),
          4.5,
        );
      });
    });
  }
}
