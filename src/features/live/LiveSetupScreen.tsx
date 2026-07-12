import React, { useCallback, useEffect, useState } from 'react';
import { BackHandler, StyleSheet, View } from 'react-native';

import type { BootstrapProjection, SetupStep } from '../../domain/setup/model';
import type { NativeProblem, NativeResult } from '../../domain/shared/result';
import type { AccountProjection } from '../../domain/account/model';
import type { LifecycleRepairKind } from '../../domain/device/model';
import type { SyncProjection } from '../../domain/contacts/model';
import type { GateDecision } from '../../domain/readiness/model';
import type { ProjectionEnvelope } from '../../domain/shared/result';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  ReadinessBanner,
  Screen,
  SectionHeading,
  StatusRow,
} from '../../design-system/components/Primitives';
import { RouteAccessibilityFocus } from '../../design-system/components/RouteAccessibilityFocus';
import { spacing } from '../../design-system/tokens/theme';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { safeReasonMessageKey } from '../../localization/reasonCopy';
import type { TranslationKey } from '../../localization/resources';
import type { LiveAppPort } from './LiveAppPort';
import { LiveHelpLegalScreen } from './LiveHelpLegalScreen';
import {
  LiveActionFeedback,
  LiveError,
  LiveLoading,
  LiveRefreshProblem,
} from './LiveProjectionState';
import {
  nativeBridgeProblem,
  nativePlatformMismatchProblem,
} from './nativeProblem';
import { useLiveProjection } from './useLiveProjection';
import { LiveAndroidDeviceControls } from './LiveAndroidDeviceControls';

const stepActionKey = (step: SetupStep): TranslationKey | undefined => {
  switch (step) {
    case 'compatibility':
      return 'live.setup.checkCompatibility';
    case 'google-account':
      return 'live.setup.connectGoogle';
    case 'contacts-disclosure':
      return 'live.setup.authorizeContacts';
    case 'sync-summary':
      return 'live.setup.syncContacts';
    default:
      return undefined;
  }
};

const cleanupKeys: Record<
  Extract<AccountProjection, { kind: 'cleanup-pending' }>['operation'],
  TranslationKey
> = {
  disconnect: 'live.settings.cleanup.disconnect',
  revoke: 'live.settings.cleanup.revoke',
  'sign-out': 'live.settings.cleanup.signOut',
  delete: 'live.settings.cleanup.delete',
  repair: 'live.settings.cleanup.repair',
};

const lifecycleRepairActions: readonly Readonly<{
  kind: LifecycleRepairKind;
  label: TranslationKey;
}>[] = [
  { kind: 'disconnect-contacts', label: 'live.privacy.disconnect' },
  { kind: 'revoke-google-access', label: 'live.privacy.revoke' },
  { kind: 'sign-out-wipe', label: 'live.privacy.signOutWipe' },
  { kind: 'wipe-local-data', label: 'live.privacy.wipeLocal' },
];

export function LiveSetupScreen({
  bootstrap,
  port,
  refreshBootstrap,
}: {
  bootstrap: ProjectionEnvelope<BootstrapProjection>;
  port: LiveAppPort;
  refreshBootstrap: () => Promise<NativeResult<BootstrapProjection>>;
}) {
  const { t } = useAppLocalization();
  const loadSetup = useCallback(() => port.getSetup(), [port]);
  const setup = useLiveProjection(loadSetup, port, ['setup']);
  const [actionPending, setActionPending] = useState(false);
  const [actionProblem, setActionProblem] = useState<
    NativeProblem | undefined
  >();
  const [actionMessage, setActionMessage] = useState<string | undefined>();
  const [showHelpLegal, setShowHelpLegal] = useState(false);
  const [deletionRecoveryRetryAvailable, setDeletionRecoveryRetryAvailable] =
    useState(false);
  const [lifecycleRepairIdentityReady, setLifecycleRepairIdentityReady] =
    useState(false);

  useEffect(() => {
    if (!showHelpLegal) return undefined;
    const subscription = BackHandler.addEventListener(
      'hardwareBackPress',
      () => {
        setShowHelpLegal(false);
        return true;
      },
    );
    return () => subscription.remove();
  }, [showHelpLegal]);

  if (showHelpLegal) {
    return (
      <View style={styles.root} testID="live-setup-help-route">
        <RouteAccessibilityFocus
          announcement={t('live.help.title')}
          routeKey="setup:help-legal"
        />
        <LiveHelpLegalScreen
          onBack={() => setShowHelpLegal(false)}
          platform={bootstrap.value.capability.platform}
          port={port}
        />
      </View>
    );
  }

  const runStepAction = async (step: SetupStep) => {
    setActionPending(true);
    setActionProblem(undefined);
    setActionMessage(undefined);

    let result: NativeResult<unknown>;
    try {
      switch (step) {
        case 'compatibility':
          result = await port.refreshCompatibility();
          break;
        case 'google-account':
          result = await port.continueWithGoogle();
          break;
        case 'contacts-disclosure':
          result = await port.authorizeContacts();
          break;
        case 'sync-summary':
          result = await port.syncContacts('setup');
          break;
        default:
          setActionPending(false);
          return;
      }
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }

    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
        await setup.reload();
        await refreshBootstrap();
      }
      setActionProblem(result.problem);
      setActionPending(false);
      return;
    }

    const refreshedSetup = await setup.reload();
    setActionMessage(
      refreshedSetup.kind === 'ok'
        ? t('live.setup.actionAccepted')
        : t('live.setup.actionUnverified'),
    );
    setActionPending(false);
    await refreshBootstrap();
  };

  const checkAccountDeletionStatus = async () => {
    setActionPending(true);
    setActionProblem(undefined);
    setActionMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['checkAccountDeletionStatus']>>;
    try {
      result = await port.checkAccountDeletionStatus();
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      setActionProblem(result.problem);
      setActionPending(false);
      return;
    }
    const status = result.envelope.value;
    if (status.kind === 'complete') {
      setDeletionRecoveryRetryAvailable(false);
      setActionMessage(t('live.privacy.deletionCompleteBody'));
    } else if (status.kind === 'remote-draining') {
      setDeletionRecoveryRetryAvailable(false);
      setActionMessage(t('live.privacy.deletionStillRunning'));
    } else if (status.kind === 'remote-unknown') {
      setDeletionRecoveryRetryAvailable(status.sameAccountRetryAvailable);
      setActionMessage(t('live.privacy.deletionProofUnavailable'));
    } else {
      setDeletionRecoveryRetryAvailable(false);
      setActionMessage(t('live.privacy.deletionProofUnavailable'));
    }
    await setup.reload();
    await refreshBootstrap();
    setActionPending(false);
  };

  const retryPendingDeletionWithGoogle = async () => {
    setActionPending(true);
    setActionProblem(undefined);
    setActionMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['continueWithGoogle']>>;
    try {
      result = await port.continueWithGoogle();
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      setActionProblem(result.problem);
      setActionPending(false);
      return;
    }
    setDeletionRecoveryRetryAvailable(false);
    setActionMessage(t('live.privacy.deletionRetrySubmitted'));
    await setup.reload();
    await refreshBootstrap();
    setActionPending(false);
  };

  const prepareLifecycleRepairIdentity = async () => {
    setActionPending(true);
    setActionProblem(undefined);
    setActionMessage(undefined);
    setLifecycleRepairIdentityReady(false);
    let result: Awaited<ReturnType<LiveAppPort['continueWithGoogle']>>;
    try {
      result = await port.continueWithGoogle();
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      setActionProblem(result.problem);
      setActionPending(false);
      return;
    }
    setLifecycleRepairIdentityReady(true);
    setActionMessage(t('live.setup.repairIdentityReady'));
    await setup.reload();
    await refreshBootstrap();
    setActionPending(false);
  };

  const repairLifecycleState = async (kind: LifecycleRepairKind) => {
    setActionPending(true);
    setActionProblem(undefined);
    setActionMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['repairLifecycleState']>>;
    try {
      result = await port.repairLifecycleState({ kind });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      // Native repair authorization is intentionally short-lived. Requiring
      // another exact-account check after any failed attempt avoids presenting
      // stale destructive choices as still authorized.
      setLifecycleRepairIdentityReady(false);
      setActionProblem(result.problem);
      setActionPending(false);
      return;
    }
    setLifecycleRepairIdentityReady(false);
    setActionMessage(
      result.envelope.value.kind === 'complete'
        ? t('live.privacy.operationComplete')
        : t('live.privacy.operationResumed'),
    );
    await setup.reload();
    await refreshBootstrap();
    setActionPending(false);
  };

  if (setup.state.kind === 'loading') {
    return (
      <Screen includeTopInset testID="live-setup-screen">
        <LiveLoading label={t('live.setup.loading')} />
      </Screen>
    );
  }
  if (setup.state.kind === 'error') {
    return (
      <Screen includeTopInset testID="live-setup-screen">
        <LiveError
          title={t('live.setup.unavailable')}
          problem={setup.state.problem}
          onRetry={() => setup.reload()}
        />
      </Screen>
    );
  }

  const projection = setup.state.result.envelope.value;
  const platform = bootstrap.value.capability.platform;
  const accountPlatform =
    projection.account.kind === 'connected'
      ? projection.account.sender.platform
      : platform;
  if (
    projection.eligibility.capability.platform !== platform ||
    projection.readiness.platform !== platform ||
    projection.automation.platform !== platform ||
    accountPlatform !== platform
  ) {
    return (
      <Screen includeTopInset testID="live-setup-screen">
        <LiveError
          title={t('live.setup.platformMismatch')}
          problem={nativePlatformMismatchProblem}
          onRetry={() => setup.reload()}
        />
      </Screen>
    );
  }
  const actionKey = stepActionKey(projection.step);
  const deletionCleanupPending =
    projection.account.kind === 'cleanup-pending' &&
    projection.account.operation === 'delete';
  const lifecycleRepairPending =
    platform === 'android' &&
    projection.account.kind === 'cleanup-pending' &&
    projection.account.operation === 'repair';
  const androidSenderGate =
    platform === 'android' &&
    projection.account.kind === 'connected' &&
    projection.account.sender.platform === 'android' &&
    (projection.account.sender.kind === 'standby' ||
      projection.account.sender.kind === 'transfer-pending');
  const accountEnvelope: ProjectionEnvelope<AccountProjection> = {
    ...setup.state.result.envelope,
    value: projection.account,
  };
  const eligibilityTone =
    projection.eligibility.kind === 'supported' ? 'positive' : 'warning';
  const eligibilityKeys: Record<
    typeof projection.eligibility.kind,
    TranslationKey
  > = {
    checking: 'live.setup.compatibility.checking',
    supported: 'live.setup.compatibility.supported',
    limited: 'live.setup.compatibility.limited',
    unsupported: 'live.setup.compatibility.unsupported',
  };
  const readiness = projection.readiness;
  const accountLabel = (account: AccountProjection): string => {
    switch (account.kind) {
      case 'connected':
        return account.displayEmail;
      case 'signed-out':
        return t('live.setup.notConnected');
      case 'connecting':
        return t('live.setup.connecting');
      case 'reconnect-required':
        return t('live.setup.reconnect');
      case 'cleanup-pending':
        return t(cleanupKeys[account.operation]);
    }
  };
  const contactsLabel = (contacts: SyncProjection): string => {
    switch (contacts.kind) {
      case 'never-synced':
        return t('live.setup.notSynced');
      case 'syncing':
        return t(
          contacts.mode === 'full'
            ? 'live.setup.syncingFull'
            : 'live.setup.syncingIncremental',
        );
      case 'fresh':
        return t('live.setup.contactsVerified', {
          count: contacts.contactCount,
        });
      case 'stale':
        return t('live.setup.contactsAttention', {
          reason: t(safeReasonMessageKey(contacts.reason)),
        });
      case 'failed-retained':
        return t('live.setup.contactsRetained', {
          reason: t(safeReasonMessageKey(contacts.reason)),
        });
      case 'authorization-required':
        return t('live.setup.contactsPermission');
    }
  };
  const gateLabel = (gate: GateDecision): string =>
    gate.kind === 'blocked'
      ? t('live.common.countChecks', { count: gate.issues.length })
      : t(
          gate.kind === 'allowed'
            ? 'live.common.allowed'
            : 'live.common.checkingState',
        );
  const stepKeys: Record<SetupStep, TranslationKey> = {
    compatibility: 'live.setup.step.compatibility',
    'google-account': 'live.setup.step.google',
    'contacts-disclosure': 'live.setup.step.contacts',
    'sync-summary': 'live.setup.step.sync',
    'recipient-selection': 'live.setup.step.people',
    'message-and-policy': 'live.setup.step.message',
    'test-review': 'live.setup.step.testReview',
    'test-progress': 'live.setup.step.testProgress',
    'reliability-repairs': 'live.setup.step.repairs',
    'activation-review': 'live.setup.step.activation',
    complete: 'live.setup.step.complete',
  };

  return (
    <Screen includeTopInset testID="live-setup-screen">
      <RouteAccessibilityFocus
        announcement={t('live.setup.currentStep', {
          step: t(stepKeys[projection.step]),
        })}
        routeKey={`setup:${projection.step}`}
      />
      <View style={styles.heading}>
        <AppText variant="title" accessibilityRole="header">
          {t('live.setup.title')}
        </AppText>
        <AppText color="muted">
          {t(
            platform === 'android'
              ? 'live.common.androidEdition'
              : 'live.common.iosEdition',
          )}
        </AppText>
      </View>

      <ReadinessBanner
        title={t('live.setup.currentStep', {
          step: t(stepKeys[projection.step]),
        })}
        detail={
          platform === 'android'
            ? t('live.setup.androidBody')
            : t('live.setup.iosBody')
        }
        tone="info"
      />
      <ReadinessBanner
        title={t('live.setup.costTitle')}
        detail={t(
          platform === 'android'
            ? 'live.setup.androidConsent'
            : 'live.setup.iosConsent',
        )}
        tone="warning"
        testID="live-setup-cost-consent"
      />
      {deletionCleanupPending ? (
        <>
          <ReadinessBanner
            title={t('live.privacy.deletionPendingTitle')}
            detail={t('live.privacy.deletionPendingBody')}
            tone="warning"
            actionLabel={
              actionPending
                ? t('live.privacy.checkingDeletion')
                : t('live.privacy.checkDeletion')
            }
            actionDisabled={actionPending}
            onAction={checkAccountDeletionStatus}
          />
          {deletionRecoveryRetryAvailable ? (
            <Button
              label={
                actionPending
                  ? t('live.privacy.deletionRetrying')
                  : t('live.privacy.deletionRetryWithGoogle')
              }
              disabled={actionPending}
              onPress={retryPendingDeletionWithGoogle}
              variant="secondary"
              testID="live-setup-retry-deletion-google"
            />
          ) : null}
        </>
      ) : null}
      {lifecycleRepairPending ? (
        <Card>
          <AppText variant="heading">{t('live.privacy.repairTitle')}</AppText>
          <ReadinessBanner
            title={t('live.privacy.recoveryUnavailable')}
            detail={t('live.privacy.repairBody')}
            tone="critical"
          />
          <Button
            label={
              actionPending
                ? t('live.privacy.deletionRetrying')
                : t('live.setup.repairReconnect')
            }
            disabled={actionPending}
            onPress={prepareLifecycleRepairIdentity}
            variant="secondary"
            testID="live-setup-repair-reauth"
          />
          {lifecycleRepairIdentityReady
            ? lifecycleRepairActions.map(action => (
                <Button
                  key={action.kind}
                  label={t(action.label)}
                  disabled={actionPending}
                  onPress={() => repairLifecycleState(action.kind)}
                  variant="secondary"
                  testID={`live-setup-repair-${action.kind}`}
                />
              ))
            : null}
          <Button
            label={t('live.help.title')}
            onPress={() => setShowHelpLegal(true)}
            variant="ghost"
            testID="live-setup-repair-help"
          />
        </Card>
      ) : null}
      {setup.state.refreshProblem ? (
        <LiveRefreshProblem problem={setup.state.refreshProblem} />
      ) : null}
      <LiveActionFeedback problem={actionProblem} message={actionMessage} />

      {androidSenderGate ? (
        <Card>
          <ReadinessBanner
            title={t('live.setup.senderGateTitle')}
            detail={t(
              projection.account.kind === 'connected' &&
                projection.account.sender.platform === 'android' &&
                projection.account.sender.kind === 'transfer-pending'
                ? 'live.setup.senderGatePendingBody'
                : 'live.setup.senderGateBody',
            )}
            tone="warning"
            testID="live-setup-sender-gate"
          />
          <LiveAndroidDeviceControls
            account={accountEnvelope}
            onAccountReload={async () => {
              await setup.reload();
              await refreshBootstrap();
            }}
            onOpenAutomation={() => {
              setup.reload().catch(() => undefined);
              refreshBootstrap().catch(() => undefined);
            }}
            port={port}
            showNotifications={false}
          />
        </Card>
      ) : deletionCleanupPending ||
        lifecycleRepairPending ? null : actionKey ? (
        <>
          {projection.step === 'contacts-disclosure' ? (
            <ReadinessBanner
              title={t('live.setup.contactsPrivacyTitle')}
              detail={t(
                platform === 'android'
                  ? 'live.setup.contactsPrivacyAndroid'
                  : 'live.setup.contactsPrivacyIos',
              )}
              tone="warning"
              testID="live-setup-contacts-privacy"
            />
          ) : null}
          <Button
            label={actionPending ? t('live.common.checking') : t(actionKey)}
            disabled={actionPending}
            onPress={() => runStepAction(projection.step)}
            testID="live-setup-action"
          />
        </>
      ) : null}

      <Card>
        <StatusRow
          title={t('live.setup.eligibility')}
          detail={t(eligibilityKeys[projection.eligibility.kind])}
          tone={eligibilityTone}
        />
        <StatusRow
          title={t('live.setup.account')}
          detail={accountLabel(projection.account)}
          tone={
            projection.account.kind === 'connected' ? 'positive' : 'warning'
          }
        />
        <StatusRow
          title={t('live.setup.contacts')}
          detail={contactsLabel(projection.contacts)}
          tone={projection.contacts.kind === 'fresh' ? 'positive' : 'warning'}
        />
      </Card>

      <SectionHeading
        title={t('live.setup.deliveryReadiness')}
        supporting={t('live.setup.checkingDoesNotSend')}
      />
      <Card>
        {readiness.platform === 'android' ? (
          <>
            <StatusRow
              title={t('live.setup.test')}
              detail={gateLabel(readiness.test)}
            />
            <StatusRow
              title={t('live.setup.activation')}
              detail={gateLabel(readiness.activation)}
            />
            <StatusRow
              title={t('live.setup.birthdayJob')}
              detail={gateLabel(readiness.birthday)}
            />
          </>
        ) : (
          <>
            <StatusRow
              title={t('live.setup.composer')}
              detail={gateLabel(readiness.composer)}
            />
            <StatusRow
              title={t('live.setup.iphoneAutomatic')}
              detail={t('live.setup.iphoneUnavailable')}
              tone="info"
            />
          </>
        )}
      </Card>

      {androidSenderGate ||
      deletionCleanupPending ||
      lifecycleRepairPending ||
      actionKey ? null : (
        <ReadinessBanner
          title={t('live.setup.reviewRequired')}
          detail={t('live.setup.reviewRequiredBody')}
          tone="warning"
          actionLabel={t('live.setup.refresh')}
          onAction={() => {
            setup.reload().catch(() => undefined);
            refreshBootstrap().catch(() => undefined);
          }}
        />
      )}
      <Button
        label={
          setup.state.refreshing
            ? t('live.common.refreshing')
            : t('live.setup.refresh')
        }
        disabled={setup.state.refreshing || actionPending}
        onPress={() => {
          setup.reload().catch(() => undefined);
          refreshBootstrap().catch(() => undefined);
        }}
        variant="secondary"
        testID="live-setup-refresh"
      />
      <Button
        label={t('live.help.title')}
        onPress={() => setShowHelpLegal(true)}
        variant="ghost"
        testID="live-setup-help-legal"
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  heading: { gap: spacing.xs },
  root: { flex: 1 },
});
