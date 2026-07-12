import React from 'react';
import { Text, View } from 'react-native';

import { FixturePreviewApp } from '../../src/app/FixturePreviewApp';
import type { AppLanguage } from '../../src/localization/i18n';

const runtimeToken = 'birthday-e2e-fixture-v1';

type NativeE2EProperties = Readonly<{
  e2eLanguage?: unknown;
  e2ePlatform?: unknown;
  e2eRuntimeToken?: unknown;
  e2eSetupComplete?: unknown;
}>;

const validLanguage = (value: unknown): value is AppLanguage =>
  value === 'en' || value === 'hi';

const validPlatform = (value: unknown): value is 'android' | 'ios' =>
  value === 'android' || value === 'ios';

/**
 * The E2E host supplies a compile-time-bound token and a fixed platform. An
 * ordinary app target cannot opt in with a URL, environment value, persisted
 * preference, or launch argument because it never loads this entry point.
 */
export function DeviceE2EFixtureApp(properties: NativeE2EProperties) {
  const isAuthorizedHost =
    properties.e2eRuntimeToken === runtimeToken &&
    validPlatform(properties.e2ePlatform) &&
    validLanguage(properties.e2eLanguage) &&
    typeof properties.e2eSetupComplete === 'boolean';

  if (!isAuthorizedHost) {
    return (
      <View accessible accessibilityRole="alert" testID="e2e-host-rejected">
        <Text>Device fixture host rejected.</Text>
      </View>
    );
  }

  return (
    <FixturePreviewApp
      initialLanguage={properties.e2eLanguage}
      initialSetupComplete={properties.e2eSetupComplete}
      platformOverride={properties.e2ePlatform}
    />
  );
}
