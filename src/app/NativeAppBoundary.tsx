import React, { useCallback } from 'react';

import { Screen } from '../design-system/components/Primitives';
import type {
  LiveAppPort,
  LiveCompanionPort,
} from '../features/live/LiveAppPort';
import { LiveAppShell } from '../features/live/LiveAppShell';
import {
  LiveError,
  LiveLoading,
} from '../features/live/LiveProjectionState';
import { LiveSetupScreen } from '../features/live/LiveSetupScreen';
import { useLiveProjection } from '../features/live/useLiveProjection';
import { useAppLocalization } from '../localization/LocalizationProvider';

export function NativeAppBoundary({
  companionPort,
  port,
}: {
  companionPort: LiveCompanionPort;
  port: LiveAppPort;
}) {
  const { t } = useAppLocalization();
  const loadBootstrap = useCallback(() => port.getBootstrap(), [port]);
  const bootstrap = useLiveProjection(loadBootstrap, port, [
    'bootstrap',
    'setup',
  ]);

  if (bootstrap.state.kind === 'loading') {
    return (
      <Screen includeTopInset testID="native-app-boundary">
        <LiveLoading label={t('live.bootstrap.loading')} />
      </Screen>
    );
  }

  if (bootstrap.state.kind === 'error') {
    return (
      <Screen includeTopInset testID="native-app-boundary">
        <LiveError
          title={t('live.bootstrap.unavailable')}
          problem={bootstrap.state.problem}
          onRetry={() => bootstrap.reload()}
          retryTestID="native-bootstrap-retry"
          testID="native-bootstrap-unavailable"
        />
      </Screen>
    );
  }

  const envelope = bootstrap.state.result.envelope;
  if (envelope.value.setupStep !== 'complete') {
    return (
      <LiveSetupScreen
        bootstrap={envelope}
        port={port}
        refreshBootstrap={bootstrap.reload}
      />
    );
  }

  return (
    <LiveAppShell
      capability={envelope.value.capability}
      companionPort={companionPort}
      port={port}
    />
  );
}
