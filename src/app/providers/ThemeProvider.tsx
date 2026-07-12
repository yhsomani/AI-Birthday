import React, {
  PropsWithChildren,
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { AccessibilityInfo, Platform, useColorScheme } from 'react-native';

import {
  AppTheme,
  ThemePreference,
  createTheme,
} from '../../design-system/tokens/theme';

type ThemeContextValue = AppTheme & {
  isReduceMotionEnabled: boolean;
  preference: ThemePreference;
  setPreference: (preference: ThemePreference) => void;
};

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

type ThemeProviderProps = PropsWithChildren<{
  initialPreference?: ThemePreference;
}>;

export function ThemeProvider({
  children,
  initialPreference = 'system',
}: ThemeProviderProps) {
  const systemScheme = useColorScheme();
  const [preference, setPreference] =
    useState<ThemePreference>(initialPreference);
  const [isHighContrast, setIsHighContrast] = useState(false);
  const [isReduceMotionEnabled, setIsReduceMotionEnabled] = useState(false);

  useEffect(() => {
    let isMounted = true;
    const isIos = Platform.OS === 'ios';
    const contrastSubscription = AccessibilityInfo.addEventListener(
      isIos ? 'darkerSystemColorsChanged' : 'highTextContrastChanged',
      enabled => {
        if (isMounted) {
          setIsHighContrast(enabled);
        }
      },
    );
    const motionSubscription = AccessibilityInfo.addEventListener(
      'reduceMotionChanged',
      enabled => {
        if (isMounted) {
          setIsReduceMotionEnabled(enabled);
        }
      },
    );
    const readHighContrast = async () => {
      try {
        const enabled = isIos
          ? await AccessibilityInfo.isDarkerSystemColorsEnabled()
          : await AccessibilityInfo.isHighTextContrastEnabled();
        if (isMounted) {
          setIsHighContrast(enabled);
        }
      } catch {
        if (isMounted) {
          setIsHighContrast(false);
        }
      }
    };
    const readReduceMotion = async () => {
      try {
        const enabled = await AccessibilityInfo.isReduceMotionEnabled();
        if (isMounted) {
          setIsReduceMotionEnabled(enabled);
        }
      } catch {
        if (isMounted) {
          setIsReduceMotionEnabled(false);
        }
      }
    };

    readHighContrast();
    readReduceMotion();
    return () => {
      isMounted = false;
      contrastSubscription.remove();
      motionSubscription.remove();
    };
  }, []);

  const isDark =
    preference === 'dark' ||
    (preference === 'system' && systemScheme === 'dark');
  const theme = useMemo(
    () => createTheme(isDark, isHighContrast),
    [isDark, isHighContrast],
  );
  const value = useMemo(
    () => ({ ...theme, isReduceMotionEnabled, preference, setPreference }),
    [isReduceMotionEnabled, preference, theme],
  );

  return (
    <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
  );
}

export const useAppTheme = () => {
  const value = useContext(ThemeContext);
  if (!value) {
    throw new Error('useAppTheme must be used inside ThemeProvider');
  }
  return value;
};
