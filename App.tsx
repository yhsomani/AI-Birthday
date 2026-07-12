import React from 'react';
import { NativeEventEmitter } from 'react-native';

import { BirthdayAutopilotApp } from './src/app/AppRoot';
import { BirthdayNativeAdapter } from './src/infrastructure/native/BirthdayNativeAdapter';
import type { NativeInvalidationSource } from './src/infrastructure/native/NativeInvalidationSource';
import type { NativeRouteSource } from './src/infrastructure/native/NativeRouteSource';
import { createCompanionNativeGateway } from './src/infrastructure/native/ios/CompanionNativeGateway';
import BirthdayNative from './specs/native/NativeBirthday';

const invalidationSource: NativeInvalidationSource = {
  subscribe: listener => {
    if (BirthdayNative === null) {
      return () => undefined;
    }
    const emitter = new NativeEventEmitter(BirthdayNative);
    const subscription = emitter.addListener(
      'BirthdayNativeInvalidated',
      listener,
    );
    return () => subscription.remove();
  },
};

const routeSource: NativeRouteSource = {
  subscribe: listener => {
    if (BirthdayNative === null) {
      return () => undefined;
    }
    const emitter = new NativeEventEmitter(BirthdayNative);
    const subscription = emitter.addListener(
      'BirthdayNativeRouteAvailable',
      listener,
    );
    return () => subscription.remove();
  },
};

const nativeProjectionPort = new BirthdayNativeAdapter(
  BirthdayNative,
  invalidationSource,
  routeSource,
);
const companionPort = createCompanionNativeGateway();

export default function App() {
  return (
    <BirthdayAutopilotApp
      companionPort={companionPort}
      nativeProjectionPort={nativeProjectionPort}
    />
  );
}
