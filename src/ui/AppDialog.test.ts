import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, it } from 'node:test';

const source = readFileSync(join(process.cwd(), 'src/ui/AppDialog.tsx'), 'utf8');

describe('cross-platform app dialog component contract', () => {
  it('uses an in-app React Native Modal instead of platform Alert behavior', () => {
    assert.match(source, /<Modal/);
    assert.match(source, /transparent/);
    assert.match(source, /visible=\{dialog !== null\}/);
    assert.doesNotMatch(source, /\bAlert\b|window\.confirm|window\.alert/);
  });

  it('exposes dialog title and description relationships without grouping action buttons away', () => {
    assert.match(source, /role="alertdialog"/);
    assert.match(source, /accessibilityViewIsModal/);
    assert.match(source, /accessibilityLabelledBy=\{titleId\}/);
    assert.match(source, /'aria-describedby': descriptionId/);
    assert.match(source, /accessibilityRole="header"/);
    assert.match(source, /nativeID=\{descriptionId\}/);
    assert.match(source, /accessibilityRole="button"/);
    assert.match(source, /accessibilityState=\{\{ disabled:/);
  });

  it('handles Android back, keyboard escape and tab cycling, and VoiceOver escape', () => {
    assert.match(source, /onRequestClose=\{\(\) => requestDismiss\('back'\)\}/);
    assert.match(source, /event\.nativeEvent\.key === 'Escape'/);
    assert.match(source, /event\.nativeEvent\.key !== 'Tab'/);
    assert.match(source, /onAccessibilityEscape=\{\(\) => requestDismiss\('accessibility-escape'\)\}/);
    assert.match(source, /nextEnabledDialogActionIndex/);
  });

  it('moves initial accessibility focus safely and honors reduced motion', () => {
    assert.match(source, /preferredDialogActionIndex/);
    assert.match(source, /AccessibilityInfo\.setAccessibilityFocus/);
    assert.match(source, /AccessibilityInfo\.isReduceMotionEnabled/);
    assert.match(source, /shouldReduceMotion \? 'none' : 'fade'/);
  });

  it('reuses the shared palette and provides platform-sized action targets', () => {
    assert.match(source, /import \{ colors, spacing \} from '\.\/theme'/);
    assert.match(source, /minHeight: 48/);
    assert.match(source, /backgroundColor: colors\.surface/);
    assert.match(source, /backgroundColor: colors\.danger/);
  });
});
