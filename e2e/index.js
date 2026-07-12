/**
 * Device-E2E-only React Native entry point.
 *
 * Production, staging, and restricted SMS variants always use index.js. The
 * native E2E hosts select this file only from their separately identified,
 * simulator/emulator-only configuration.
 */

import { AppRegistry } from 'react-native';

import { DeviceE2EFixtureApp } from './src/DeviceE2EFixtureApp';

AppRegistry.registerComponent(
  'BirthdayAutopilotE2E',
  () => DeviceE2EFixtureApp,
);
