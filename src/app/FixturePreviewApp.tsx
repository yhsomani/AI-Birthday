import React, { useEffect } from 'react';
import { Text, View } from 'react-native';

import { AppProviders } from './AppProviders';
import { RootNavigator } from './navigation/RootNavigator';
import { FixturePlatform, FixtureProvider } from './providers/FixtureProvider';
import { appI18n, type AppLanguage } from '../localization/i18n';
import { resources as fixtureResources } from '../localization/resources';

export type FixturePreviewAppProps = {
  platformOverride?: FixturePlatform;
  initialLanguage?: AppLanguage;
  initialSetupComplete?: boolean;
  initialSelectedPersonIds?: string[];
};

export function FixturePreviewApp({
  platformOverride = 'android',
  initialLanguage,
  initialSetupComplete,
  initialSelectedPersonIds,
}: FixturePreviewAppProps) {
  useEffect(() => {
    if (__DEV__ && (initialLanguage === 'en' || initialLanguage === 'hi')) {
      appI18n.changeLanguage(initialLanguage).catch(() => undefined);
    }
  }, [initialLanguage]);

  if (!__DEV__) {
    return (
      <View accessible accessibilityRole="alert">
        <Text>Developer preview is unavailable in this build.</Text>
      </View>
    );
  }

  // Fixture-only copy and pseudo-RTL resources stay out of the production
  // module graph. Install them only when this explicit developer preview runs.
  appI18n.addResourceBundle(
    'en',
    'translation',
    fixtureResources.en.translation,
    true,
    true,
  );

  appI18n.addResourceBundle(
    'hi',
    'translation',
    fixtureResources.hi.translation,
    true,
    true,
  );
  appI18n.addResourceBundle(
    'ar-XB',
    'translation',
    fixtureResources['ar-XB'].translation,
    true,
    true,
  );

  return (
    <AppProviders>
      <FixtureProvider
        platform={platformOverride}
        {...(initialSetupComplete === undefined
          ? {}
          : { initialSetupComplete })}
        {...(initialSelectedPersonIds === undefined
          ? {}
          : { initialSelectedPersonIds })}
      >
        <RootNavigator />
      </FixtureProvider>
    </AppProviders>
  );
}
