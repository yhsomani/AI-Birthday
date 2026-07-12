import React from 'react';

import { AppProviders } from './AppProviders';
import { NativeAppBoundary } from './NativeAppBoundary';
import type { LiveAppPort } from '../features/live/LiveAppPort';
import type { LiveCompanionPort } from '../features/live/LiveAppPort';

export { AppProviders } from './AppProviders';

export function BirthdayAutopilotApp({
  companionPort,
  nativeProjectionPort,
}: {
  companionPort: LiveCompanionPort;
  nativeProjectionPort: LiveAppPort;
}) {
  return (
    <AppProviders>
      <NativeAppBoundary
        companionPort={companionPort}
        port={nativeProjectionPort}
      />
    </AppProviders>
  );
}
