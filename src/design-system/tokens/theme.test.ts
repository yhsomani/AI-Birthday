import { createTheme } from './theme';

const luminance = (hex: string): number => {
  const channels = hex
    .slice(1)
    .match(/.{2}/gu)
    ?.map(value => Number.parseInt(value, 16) / 255)
    .map(value =>
      value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4),
    );
  if (!channels || channels.length !== 3) {
    throw new Error('Expected a six-digit hexadecimal color');
  }
  return 0.2126 * channels[0]! + 0.7152 * channels[1]! + 0.0722 * channels[2]!;
};

const contrast = (foreground: string, background: string): number => {
  const values = [luminance(foreground), luminance(background)].sort(
    (left, right) => right - left,
  );
  return (values[0]! + 0.05) / (values[1]! + 0.05);
};

describe.each([
  ['light', false, false],
  ['light high contrast', false, true],
  ['dark', true, false],
  ['dark high contrast', true, true],
] as const)('%s theme contrast', (_name, isDark, isHighContrast) => {
  const theme = createTheme(isDark, isHighContrast);
  const colors = theme.colors;

  it.each([
    ['primary text', colors.text, colors.background],
    ['muted text', colors.textMuted, colors.background],
    ['accent action', colors.onAccent, colors.accent],
    ['positive status', colors.positive, colors.positiveSurface],
    ['warning status', colors.warning, colors.warningSurface],
    ['critical status', colors.critical, colors.criticalSurface],
    ['informational status', colors.info, colors.infoSurface],
  ])('keeps %s at WCAG AA text contrast', (_label, foreground, background) => {
    expect(contrast(foreground, background)).toBeGreaterThanOrEqual(4.5);
  });

  it('projects the requested contrast preference without reducing legibility', () => {
    expect(theme.isHighContrast).toBe(isHighContrast);
    if (isHighContrast) {
      expect(colors.textMuted).toBe(colors.text);
      expect(contrast(colors.border, colors.background)).toBeGreaterThanOrEqual(
        4.5,
      );
    }
  });
});
