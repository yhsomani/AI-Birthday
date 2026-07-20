import React, { useCallback, useState } from 'react';

import type { NativeResult, ProjectionEnvelope } from '../domain/shared/result';
import type { BootstrapProjection } from '../domain/setup/model';
import { Screen } from '../design-system/components/Primitives';
import type {
  LiveAppPort,
  LiveCompanionPort,
} from '../features/live/LiveAppPort';
import { LiveAppShell } from '../features/live/LiveAppShell';
import { LiveProductSetupJourney } from '../features/live/LiveProductSetupJourney';
import { LiveError, LiveLoading } from '../features/live/LiveProjectionState';
import { LiveSetupScreen } from '../features/live/LiveSetupScreen';
import { accountRequiresLifecycleRecovery } from '../features/live/lifecycleRecovery';
import { nativeContractProblem } from '../features/live/nativeProblem';
import { useLiveProjection } from '../features/live/useLiveProjection';
import { useAppLocalization } from '../localization/LocalizationProvider';

function CompletedIdentityBoundary({
  bootstrap,
  companionPort,
  port,
  refreshBootstrap,
}: {
  bootstrap: ProjectionEnvelope<BootstrapProjection>;
  companionPort: LiveCompanionPort;
  port: LiveAppPort;
  refreshBootstrap: () => Promise<NativeResult<BootstrapProjection>>;
}) {
  const { t } = useAppLocalization();
  const [journeyDeferred, setJourneyDeferred] = useState(false);
  const continueProductSetup = useCallback(() => setJourneyDeferred(false), []);
  const loadSetup = useCallback(() => port.getSetup(), [port]);
  const setup = useLiveProjection(loadSetup, port, [
    'setup',
    'contacts',
    'messages',
    'automation',
  ]);
  const reloadSetupAndBootstrap = useCallback(async () => {
    await Promise.all([setup.reload(), refreshBootstrap()]);
  }, [refreshBootstrap, setup]);

  if (setup.state.kind === 'loading') {
    return (
      <Screen includeTopInset testID="native-product-setup-boundary">
        <LiveLoading label={t('live.guidedSetup.checking')} />
      </Screen>
    );
  }
  if (setup.state.kind === 'error') {
    return (
      <Screen includeTopInset testID="native-product-setup-boundary">
        <LiveError
          title={t('live.guidedSetup.progressUnavailable')}
          problem={setup.state.problem}
          onRetry={reloadSetupAndBootstrap}
          testID="native-product-setup-unavailable"
        />
      </Screen>
    );
  }

  const retainedSetupEnvelope = setup.state.result.envelope;
  const retainedSetup = retainedSetupEnvelope.value;
  if (setup.state.refreshing) {
    return (
      <Screen includeTopInset testID="native-product-setup-boundary">
        <LiveLoading label={t('live.guidedSetup.checking')} />
      </Screen>
    );
  }
  if (setup.state.refreshProblem) {
    return (
      <Screen includeTopInset testID="native-product-setup-boundary">
        <LiveError
          title={t('live.guidedSetup.progressUnavailable')}
          problem={setup.state.refreshProblem}
          onRetry={reloadSetupAndBootstrap}
          testID="native-product-setup-unavailable"
        />
      </Screen>
    );
  }

  if (retainedSetupEnvelope.revision !== bootstrap.revision) {
    return (
      <Screen includeTopInset testID="native-product-setup-boundary">
        <LiveError
          title={t('live.guidedSetup.progressUnavailable')}
          problem={nativeContractProblem}
          onRetry={reloadSetupAndBootstrap}
          testID="native-product-setup-unavailable"
        />
      </Screen>
    );
  }

  if (
    accountRequiresLifecycleRecovery(
      retainedSetup.account,
      bootstrap.value.capability.platform,
    )
  ) {
    return (
      <LiveSetupScreen
        bootstrap={bootstrap}
        onDefer={() => setJourneyDeferred(true)}
        port={port}
        refreshBootstrap={refreshBootstrap}
      />
    );
  }

  const productSetupRequired = !retainedSetup.initialActivationCompleted;

  if (productSetupRequired && !journeyDeferred) {
    return (
      <LiveProductSetupJourney
        capability={bootstrap.value.capability}
        companionPort={companionPort}
        onDefer={() => setJourneyDeferred(true)}
        onRefreshSetup={setup.reload}
        port={port}
      />
    );
  }

  return (
    <LiveAppShell
      account={bootstrap.value.account}
      capability={bootstrap.value.capability}
      companionPort={companionPort}
      onContinueSetup={continueProductSetup}
      port={port}
      productSetupRequired={productSetupRequired}
      refreshBootstrap={refreshBootstrap}
    />
  );
}

export function NativeAppBoundary({
  companionPort,
  port,
}: {
  companionPort: LiveCompanionPort;
  port: LiveAppPort;
}) {
  const { t } = useAppLocalization();
  const [earlySetupDeferred, setEarlySetupDeferred] = useState(false);
  const continueEarlySetup = useCallback(
    () => setEarlySetupDeferred(false),
    [],
  );
  const loadBootstrap = useCallback(() => port.getBootstrap(), [port]);
  const bootstrap = useLiveProjection(loadBootstrap, port, [
    'bootstrap',
    'setup',
    'account',
    'privacy',
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
  const lifecycleRecoveryRequired = accountRequiresLifecycleRecovery(
    envelope.value.account,
    envelope.value.capability.platform,
  );
  if (bootstrap.state.refreshing && !lifecycleRecoveryRequired) {
    return (
      <Screen includeTopInset testID="native-app-boundary">
        <LiveLoading label={t('live.bootstrap.loading')} />
      </Screen>
    );
  }
  if (bootstrap.state.refreshProblem && !lifecycleRecoveryRequired) {
    return (
      <Screen includeTopInset testID="native-app-boundary">
        <LiveError
          title={t('live.bootstrap.unavailable')}
          problem={bootstrap.state.refreshProblem}
          onRetry={() => bootstrap.reload()}
          retryTestID="native-bootstrap-retry"
          testID="native-bootstrap-unavailable"
        />
      </Screen>
    );
  }
  if (envelope.value.setupStep !== 'complete' || lifecycleRecoveryRequired) {
    if (!lifecycleRecoveryRequired && earlySetupDeferred) {
      return (
        <LiveAppShell
          account={envelope.value.account}
          capability={envelope.value.capability}
          companionPort={companionPort}
          onContinueSetup={continueEarlySetup}
          port={port}
          productSetupRequired
          refreshBootstrap={bootstrap.reload}
        />
      );
    }
    return (
      <LiveSetupScreen
        bootstrap={envelope}
        onDefer={() => setEarlySetupDeferred(true)}
        port={port}
        refreshBootstrap={bootstrap.reload}
      />
    );
  }

  return (
    <CompletedIdentityBoundary
      bootstrap={envelope}
      companionPort={companionPort}
      port={port}
      refreshBootstrap={bootstrap.reload}
    />
  );
}
