export type ThemePreference = 'system' | 'light' | 'dark';

export type StatusTone =
  | 'neutral'
  | 'positive'
  | 'warning'
  | 'critical'
  | 'info';

export type AppColors = {
  background: string;
  surface: string;
  surfaceRaised: string;
  surfaceMuted: string;
  text: string;
  textMuted: string;
  border: string;
  accent: string;
  accentPressed: string;
  onAccent: string;
  positive: string;
  positiveSurface: string;
  warning: string;
  warningSurface: string;
  critical: string;
  criticalSurface: string;
  info: string;
  infoSurface: string;
  focus: string;
  scrim: string;
};

export type AppTheme = {
  isDark: boolean;
  isHighContrast: boolean;
  colors: AppColors;
};

const lightColors: AppColors = {
  background: '#F7F7FC',
  surface: '#FFFFFF',
  surfaceRaised: '#FFFFFF',
  surfaceMuted: '#EFF0F8',
  text: '#171824',
  textMuted: '#5D6073',
  border: '#D9DAE6',
  accent: '#4B52A3',
  accentPressed: '#373D82',
  onAccent: '#FFFFFF',
  positive: '#256A45',
  positiveSurface: '#E7F5EC',
  warning: '#8A4F08',
  warningSurface: '#FFF2D8',
  critical: '#A53535',
  criticalSurface: '#FCEAEA',
  info: '#315B91',
  infoSurface: '#E9F1FC',
  focus: '#111827',
  scrim: 'rgba(23, 24, 36, 0.45)',
};

const darkColors: AppColors = {
  background: '#11121A',
  surface: '#1B1C27',
  surfaceRaised: '#232431',
  surfaceMuted: '#292B3A',
  text: '#F5F5FA',
  textMuted: '#C2C3D0',
  border: '#3C3E50',
  accent: '#BFC2FF',
  accentPressed: '#D5D6FF',
  onAccent: '#202561',
  positive: '#8ED4AD',
  positiveSurface: '#173B2A',
  warning: '#F4C06E',
  warningSurface: '#463311',
  critical: '#FFB4AB',
  criticalSurface: '#4F2425',
  info: '#A8C8F3',
  infoSurface: '#203753',
  focus: '#FFFFFF',
  scrim: 'rgba(0, 0, 0, 0.7)',
};

export const createTheme = (
  isDark: boolean,
  isHighContrast: boolean,
): AppTheme => {
  const base = isDark ? darkColors : lightColors;

  return {
    isDark,
    isHighContrast,
    colors: {
      ...base,
      border: isHighContrast ? (isDark ? '#FFFFFF' : '#171824') : base.border,
      textMuted: isHighContrast ? base.text : base.textMuted,
    },
  };
};

export const spacing = {
  xs: 4,
  sm: 8,
  md: 16,
  lg: 24,
  xl: 32,
  xxl: 48,
} as const;

export const radii = {
  sm: 8,
  md: 14,
  lg: 20,
  pill: 999,
} as const;

export const minimumTargetSize = 48;
