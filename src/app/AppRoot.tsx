import React from 'react';

import { AppProviders } from './AppProviders';
import { NativeAppBoundary } from './NativeAppBoundary';
import type { LiveAppPort } from '../features/live/LiveAppPort';

export { AppProviders } from './AppProviders';

export function BirthdayAutopilotApp({
  nativeProjectionPort,
}: {
  nativeProjectionPort: LiveAppPort;
}) {
  return (
    <AppProviders>
      <NativeAppBoundary
        port={nativeProjectionPort}
      />
    </AppProviders>
  );
}

