import React from 'react';
import { NativeEventEmitter } from 'react-native';

import { BirthdayAutopilotApp } from './src/app/AppRoot';
import { BirthdayNativeAdapter } from './src/infrastructure/native/BirthdayNativeAdapter';
import type { NativeInvalidationSource } from './src/infrastructure/native/NativeInvalidationSource';
import type { NativeRouteSource } from './src/infrastructure/native/NativeRouteSource';
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

export default function App() {
  return <BirthdayAutopilotApp nativeProjectionPort={nativeProjectionPort} />;
}
