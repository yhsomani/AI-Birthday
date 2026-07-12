import React, { type PropsWithChildren } from 'react';
import { StatusBar, StyleSheet, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { ThemeProvider, useAppTheme } from './providers/ThemeProvider';
import {
  LocalizationProvider,
  useAppLocalization,
} from '../localization/LocalizationProvider';

function AppSurface({ children }: PropsWithChildren) {
  const { colors, isDark } = useAppTheme();
  const { language } = useAppLocalization();
  const isRtlLayout = language === 'ar-XB';
  return (
    <View
      testID="app-direction-root"
      style={[
        styles.root,
        { backgroundColor: colors.background },
        isRtlLayout ? styles.rtl : styles.ltr,
      ]}
    >
      <StatusBar
        barStyle={isDark ? 'light-content' : 'dark-content'}
        backgroundColor={colors.background}
      />
      {children}
    </View>
  );
}

export function AppProviders({ children }: PropsWithChildren) {
  return (
    <SafeAreaProvider>
      <LocalizationProvider>
        <ThemeProvider>
          <AppSurface>{children}</AppSurface>
        </ThemeProvider>
      </LocalizationProvider>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  ltr: { direction: 'ltr' },
  rtl: { direction: 'rtl' },
});
