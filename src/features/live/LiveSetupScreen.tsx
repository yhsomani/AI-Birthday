import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, BackHandler, StyleSheet, View } from 'react-native';

import type { BootstrapProjection, SetupStep } from '../../domain/setup/model';
import type { NativeProblem, NativeResult } from '../../domain/shared/result';
import type { AccountProjection } from '../../domain/account/model';
import type { LifecycleRepairKind } from '../../domain/device/model';
import type {
  CurrentPrivacyOperationProjection,
  PrivacyActionKind,
  PrivacyOperationProjection,
} from '../../domain/privacy/model';
import type { SyncProjection } from '../../domain/contacts/model';
import type { ProjectionEnvelope } from '../../domain/shared/result';
import type { NativeRevision } from '../../domain/shared/brand';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  ReadinessBanner,
  Screen,
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
  nativeContractProblem,
  nativePlatformMismatchProblem,
} from './nativeProblem';
import { useLiveProjection } from './useLiveProjection';
import { LiveAndroidDeviceControls } from './LiveAndroidDeviceControls';
import { lifecycleRecoveryIdentity } from './lifecycleRecovery';

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

const progressiveStepNumber = (step: SetupStep): 1 | 2 | 3 | 4 => {
  switch (step) {
    case 'compatibility':
    case 'google-account':
      return 1;
    case 'contacts-disclosure':
    case 'sync-summary':
      return 2;
    case 'recipient-selection':
    case 'message-and-policy':
      return 3;
    default:
      return 4;
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

const lifecycleRepairIdentityUiTtlMs = 4 * 60 * 1_000;

type LifecycleRepairIdentityLease = Readonly<{
  accountRevision: NativeRevision;
  setupRevision: NativeRevision;
  expiresAtMs: number;
}>;

const expectedCleanupActions: Readonly<
  Record<
    Exclude<
      Extract<AccountProjection, { kind: 'cleanup-pending' }>['operation'],
      'repair'
    >,
    readonly PrivacyActionKind[]
  >
> = {
  disconnect: ['disconnect-contacts'],
  revoke: ['revoke-google-access'],
  'sign-out': [
    'sign-out-retain',
    'sign-out-wipe',
    'wipe-local-data',
    'clear-gemini-templates',
    'clear-activity',
  ],
  delete: ['delete-account'],
};

export function LiveSetupScreen({
  bootstrap,
  onDefer,
  port,
  refreshBootstrap,
}: {
  bootstrap: ProjectionEnvelope<BootstrapProjection>;
  onDefer: () => void;
  port: LiveAppPort;
  refreshBootstrap: () => Promise<NativeResult<BootstrapProjection>>;
}) {
  const { t } = useAppLocalization();
  const loadSetup = useCallback(() => port.getSetup(), [port]);
  const setup = useLiveProjection(loadSetup, port, ['setup']);
  const reloadSetupAndBootstrap = useCallback(async () => {
    await Promise.all([setup.reload(), refreshBootstrap()]);
  }, [refreshBootstrap, setup]);
  const loadCurrentOperation = useCallback(
    () => port.getCurrentOperation(),
    [port],
  );
  const currentOperation = useLiveProjection(loadCurrentOperation, port, [
    'privacy',
    'account',
    'setup',
  ]);
  const [actionPending, setActionPending] = useState(false);
  const [actionProblem, setActionProblem] = useState<
    NativeProblem | undefined
  >();
  const [actionMessage, setActionMessage] = useState<string | undefined>();
  const [showHelpLegal, setShowHelpLegal] = useState(false);
  const [lifecycleRepairIdentityLease, setLifecycleRepairIdentityLease] =
    useState<LifecycleRepairIdentityLease | undefined>();
  const lifecycleRepairGenerationRef = useRef(0);
  const lifecycleRepairPreparingRef = useRef<number | undefined>(undefined);
  const lifecycleRepairInvalidationRevisionRef = useRef<
    NativeRevision | undefined
  >(undefined);
  const lifecycleRepairInvalidationConflictRef = useRef(false);
  const appIsActiveRef = useRef(
    AppState.currentState !== 'background' &&
      AppState.currentState !== 'inactive',
  );

  const clearLifecycleRepairIdentity = useCallback(() => {
    lifecycleRepairGenerationRef.current += 1;
    setLifecycleRepairIdentityLease(undefined);
  }, []);

  useEffect(
    () =>
      port.subscribeInvalidations(event => {
        if (lifecycleRepairPreparingRef.current !== undefined) {
          setLifecycleRepairIdentityLease(undefined);
          const previousRevision =
            lifecycleRepairInvalidationRevisionRef.current;
          if (
            previousRevision !== undefined &&
            previousRevision !== event.revision
          ) {
            lifecycleRepairInvalidationConflictRef.current = true;
          } else {
            lifecycleRepairInvalidationRevisionRef.current = event.revision;
          }
          return;
        }
        clearLifecycleRepairIdentity();
      }),
    [clearLifecycleRepairIdentity, port],
  );

  useEffect(() => {
    const subscription = AppState.addEventListener('change', nextState => {
      appIsActiveRef.current = nextState === 'active';
      if (nextState !== 'active') {
        clearLifecycleRepairIdentity();
      }
    });
    return () => subscription.remove();
  }, [clearLifecycleRepairIdentity]);

  useEffect(() => {
    if (!lifecycleRepairIdentityLease) return undefined;
    const remaining = lifecycleRepairIdentityLease.expiresAtMs - Date.now();
    if (remaining <= 0) {
      clearLifecycleRepairIdentity();
      return undefined;
    }
    const timeout = setTimeout(clearLifecycleRepairIdentity, remaining);
    return () => clearTimeout(timeout);
  }, [clearLifecycleRepairIdentity, lifecycleRepairIdentityLease]);

  useEffect(() => {
    if (!lifecycleRepairIdentityLease) return;
    const leaseStillMatchesProjection =
      setup.state.kind === 'ready' &&
      !setup.state.refreshing &&
      !setup.state.refreshProblem &&
      setup.state.result.envelope.revision ===
        lifecycleRepairIdentityLease.setupRevision &&
      setup.state.result.envelope.value.account.kind === 'cleanup-pending' &&
      setup.state.result.envelope.value.account.operation === 'repair';
    if (!leaseStillMatchesProjection) {
      clearLifecycleRepairIdentity();
    }
  }, [clearLifecycleRepairIdentity, lifecycleRepairIdentityLease, setup.state]);

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
    if (
      setup.state.kind !== 'ready' ||
      setup.state.refreshing ||
      setup.state.refreshProblem ||
      setup.state.result.envelope.value.step !== step
    ) {
      return;
    }
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

  const prepareLifecycleRepairIdentity = async () => {
    setActionPending(true);
    setActionProblem(undefined);
    setActionMessage(undefined);
    const generation = lifecycleRepairGenerationRef.current + 1;
    lifecycleRepairGenerationRef.current = generation;
    lifecycleRepairPreparingRef.current = generation;
    lifecycleRepairInvalidationRevisionRef.current = undefined;
    lifecycleRepairInvalidationConflictRef.current = false;
    setLifecycleRepairIdentityLease(undefined);
    let result: Awaited<ReturnType<LiveAppPort['continueWithGoogle']>>;
    try {
      result = await port.continueWithGoogle();
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      lifecycleRepairPreparingRef.current = undefined;
      lifecycleRepairInvalidationRevisionRef.current = undefined;
      lifecycleRepairInvalidationConflictRef.current = false;
      setActionProblem(result.problem);
      setActionPending(false);
      return;
    }
    const refreshedSetup = await setup.reload();
    const refreshedBootstrap = await refreshBootstrap();
    const expectedInvalidationRevision =
      lifecycleRepairInvalidationRevisionRef.current;
    const identityStillCurrent =
      lifecycleRepairGenerationRef.current === generation &&
      appIsActiveRef.current &&
      refreshedSetup.kind === 'ok' &&
      refreshedBootstrap.kind === 'ok' &&
      result.envelope.revision === refreshedSetup.envelope.revision &&
      refreshedSetup.envelope.revision ===
        refreshedBootstrap.envelope.revision &&
      !lifecycleRepairInvalidationConflictRef.current &&
      (expectedInvalidationRevision === undefined ||
        expectedInvalidationRevision === result.envelope.revision) &&
      refreshedSetup.envelope.value.account.kind === 'cleanup-pending' &&
      refreshedSetup.envelope.value.account.operation === 'repair' &&
      refreshedBootstrap.envelope.value.account.kind === 'cleanup-pending' &&
      refreshedBootstrap.envelope.value.account.operation === 'repair';
    lifecycleRepairPreparingRef.current = undefined;
    lifecycleRepairInvalidationRevisionRef.current = undefined;
    lifecycleRepairInvalidationConflictRef.current = false;
    if (identityStillCurrent) {
      setLifecycleRepairIdentityLease({
        accountRevision: result.envelope.revision,
        setupRevision: refreshedSetup.envelope.revision,
        expiresAtMs: Date.now() + lifecycleRepairIdentityUiTtlMs,
      });
      setActionMessage(t('live.setup.repairIdentityReady'));
    } else {
      setActionMessage(t('live.setup.actionUnverified'));
    }
    setActionPending(false);
  };

  const repairLifecycleState = async (kind: LifecycleRepairKind) => {
    if (!lifecycleRepairIdentityReady) return;
    clearLifecycleRepairIdentity();
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
      setActionProblem(result.problem);
      setActionPending(false);
      return;
    }
    setActionMessage(
      result.envelope.value.kind === 'complete'
        ? t('live.privacy.operationComplete')
        : t('live.privacy.operationResumed'),
    );
    await setup.reload();
    await refreshBootstrap();
    setActionPending(false);
  };

  const exactCurrentOperation = (
    operation: CurrentPrivacyOperationProjection,
    expectedActions: readonly PrivacyActionKind[],
  ): PrivacyOperationProjection | undefined =>
    operation.kind !== 'none' &&
    operation.kind !== 'unavailable' &&
    expectedActions.includes(operation.action)
      ? operation
      : undefined;

  const refreshLifecycleOperation = async (
    operation: PrivacyOperationProjection | undefined,
  ) => {
    setActionPending(true);
    setActionProblem(undefined);
    setActionMessage(undefined);
    let result: NativeResult<PrivacyOperationProjection> | undefined;
    if (operation) {
      try {
        result = await port.getOperation(operation.id);
      } catch {
        result = { kind: 'error', problem: nativeBridgeProblem };
      }
    }
    if (result?.kind === 'error') {
      setActionProblem(result.problem);
    } else if (result?.kind === 'ok') {
      setActionMessage(
        result.envelope.value.kind === 'complete'
          ? t('live.privacy.operationComplete')
          : result.envelope.value.kind === 'failed'
          ? t('live.privacy.operationFailed')
          : t('live.privacy.operationPending'),
      );
    }
    await currentOperation.reload();
    await setup.reload();
    await refreshBootstrap();
    setActionPending(false);
  };

  const resumeLifecycleOperation = async (
    operation: PrivacyOperationProjection,
  ) => {
    if (
      operation.kind === 'complete' ||
      operation.kind === 'failed' ||
      operation.kind === 'remote-unknown'
    ) {
      return;
    }
    setActionPending(true);
    setActionProblem(undefined);
    setActionMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['resumeOperation']>>;
    try {
      result = await port.resumeOperation(operation.id);
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      setActionProblem(result.problem);
      setActionPending(false);
      return;
    }
    setActionMessage(
      result.envelope.value.kind === 'complete'
        ? t('live.privacy.operationComplete')
        : t('live.privacy.operationResumed'),
    );
    await currentOperation.reload();
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
          onRetry={reloadSetupAndBootstrap}
        />
      </Screen>
    );
  }

  const projection = setup.state.result.envelope.value;
  const platform = bootstrap.value.capability.platform;
  const bootstrapLifecycleIdentity = lifecycleRecoveryIdentity(
    bootstrap.value.account,
    platform,
  );
  const setupLifecycleIdentity = lifecycleRecoveryIdentity(
    projection.account,
    platform,
  );
  const projectionRevisionMatchesBootstrap =
    setup.state.result.envelope.revision === bootstrap.revision;
  const bootstrapLifecycleConflict =
    bootstrapLifecycleIdentity !== undefined &&
    bootstrapLifecycleIdentity !== setupLifecycleIdentity;
  if (!projectionRevisionMatchesBootstrap || bootstrapLifecycleConflict) {
    return (
      <Screen includeTopInset testID="live-setup-screen">
        <LiveError
          title={t('live.setup.unavailable')}
          problem={nativeContractProblem}
          onRetry={reloadSetupAndBootstrap}
          testID="live-setup-projection-conflict"
        />
      </Screen>
    );
  }
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
  const connectedAndroidSenderDeleting =
    platform === 'android' &&
    projection.account.kind === 'connected' &&
    projection.account.sender.platform === 'android' &&
    projection.account.sender.kind === 'deleting';
  const genericLifecycleCleanupPending =
    projection.account.kind === 'cleanup-pending' &&
    !lifecycleRepairPending &&
    projection.account.operation !== 'delete';
  const lifecycleRecoveryPending =
    lifecycleRepairPending ||
    deletionCleanupPending ||
    genericLifecycleCleanupPending ||
    connectedAndroidSenderDeleting;
  const expectedLifecycleActions: readonly PrivacyActionKind[] =
    projection.account.kind === 'cleanup-pending' &&
    projection.account.operation !== 'repair'
      ? expectedCleanupActions[projection.account.operation]
      : connectedAndroidSenderDeleting
      ? ['delete-account']
      : [];
  const visibleLifecycleOperation =
    currentOperation.state.kind === 'ready' &&
    !currentOperation.state.refreshing &&
    !currentOperation.state.refreshProblem &&
    currentOperation.state.result.envelope.revision ===
      setup.state.result.envelope.revision
      ? exactCurrentOperation(
          currentOperation.state.result.envelope.value,
          expectedLifecycleActions,
        )
      : undefined;
  const lifecycleRepairIdentityReady =
    lifecycleRepairIdentityLease !== undefined &&
    lifecycleRepairIdentityLease.expiresAtMs > Date.now() &&
    lifecycleRepairIdentityLease.accountRevision ===
      lifecycleRepairIdentityLease.setupRevision &&
    lifecycleRepairIdentityLease.setupRevision ===
      setup.state.result.envelope.revision &&
    !setup.state.refreshing &&
    !setup.state.refreshProblem &&
    lifecycleRepairPending;
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
  const eligibilityIssues =
    projection.eligibility.kind === 'limited' ||
    projection.eligibility.kind === 'unsupported'
      ? [
          projection.eligibility.primaryIssue,
          ...projection.eligibility.otherIssues,
        ]
      : [];
  const setupProjectionStable =
    !setup.state.refreshing &&
    setup.state.refreshProblem === undefined &&
    projectionRevisionMatchesBootstrap &&
    !bootstrapLifecycleConflict;
  const ordinarySetup = !androidSenderGate && !lifecycleRecoveryPending;
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
          number: progressiveStepNumber(projection.step),
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
          number: progressiveStepNumber(projection.step),
          step: t(stepKeys[projection.step]),
        })}
        detail={
          platform === 'android'
            ? t('live.setup.androidBody')
            : t('live.setup.iosBody')
        }
        tone="info"
      />
      {ordinarySetup && progressiveStepNumber(projection.step) === 1 ? (
        <>
          <Card testID="live-setup-eligibility">
            <StatusRow
              title={t('live.setup.eligibility')}
              detail={t(eligibilityKeys[projection.eligibility.kind])}
              tone={eligibilityTone}
            />
            {eligibilityIssues.map((issue, index) => (
              <StatusRow
                key={issue.id}
                title={t(safeReasonMessageKey(issue.code))}
                tone={
                  issue.severity === 'blocking'
                    ? 'critical'
                    : issue.severity === 'warning'
                    ? 'warning'
                    : 'info'
                }
                testID={`live-setup-eligibility-issue-${index}`}
              />
            ))}
          </Card>
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
        </>
      ) : null}
      {ordinarySetup && progressiveStepNumber(projection.step) === 2 ? (
        <Card testID="live-setup-current-step-summary">
          <StatusRow
            title={t('live.guidedSetup.stepOne')}
            detail={t('live.guidedSetup.complete')}
            tone="positive"
            testID="live-setup-completed-step-one"
          />
          <StatusRow
            title={t('live.setup.contacts')}
            detail={contactsLabel(projection.contacts)}
            tone={projection.contacts.kind === 'fresh' ? 'positive' : 'warning'}
            testID="live-setup-contact-status"
          />
        </Card>
      ) : null}
      {deletionCleanupPending ? (
        <ReadinessBanner
          title={t('live.privacy.deletionPendingTitle')}
          detail={t('live.privacy.deletionPendingBody')}
          tone="warning"
        />
      ) : null}
      {genericLifecycleCleanupPending || connectedAndroidSenderDeleting ? (
        <Card>
          <AppText variant="heading">
            {projection.account.kind === 'cleanup-pending'
              ? t(cleanupKeys[projection.account.operation])
              : t('live.settings.cleanup.delete')}
          </AppText>
          {currentOperation.state.kind === 'loading' ? (
            <LiveLoading label={t('live.privacy.operationPending')} />
          ) : currentOperation.state.kind === 'error' ? (
            <LiveError
              title={t('live.privacy.operationUnavailable')}
              problem={currentOperation.state.problem}
              onRetry={() => currentOperation.reload()}
            />
          ) : visibleLifecycleOperation ? (
            <>
              <StatusRow
                title={
                  visibleLifecycleOperation.kind === 'complete'
                    ? t('live.privacy.operationComplete')
                    : visibleLifecycleOperation.kind === 'failed'
                    ? t('live.privacy.operationFailed')
                    : t('live.privacy.operationPending')
                }
                tone={
                  visibleLifecycleOperation.kind === 'failed'
                    ? 'critical'
                    : 'warning'
                }
              />
              {visibleLifecycleOperation.kind !== 'complete' &&
              visibleLifecycleOperation.kind !== 'failed' &&
              visibleLifecycleOperation.kind !== 'remote-unknown' ? (
                <Button
                  label={t('live.privacy.resumeOperation')}
                  disabled={actionPending}
                  onPress={() =>
                    resumeLifecycleOperation(visibleLifecycleOperation)
                  }
                  testID="live-setup-resume-cleanup"
                />
              ) : null}
              <Button
                label={t('live.privacy.refreshOperation')}
                disabled={actionPending}
                onPress={() =>
                  refreshLifecycleOperation(visibleLifecycleOperation)
                }
                variant="secondary"
                testID="live-setup-refresh-cleanup"
              />
            </>
          ) : (
            <ReadinessBanner
              title={t('live.privacy.recoveryUnavailable')}
              detail={t('live.privacy.recoveryUnavailableBody')}
              tone="critical"
              actionLabel={t('live.privacy.refreshOperation')}
              actionDisabled={actionPending}
              onAction={() => refreshLifecycleOperation(undefined)}
              testID="live-setup-cleanup-unavailable"
            />
          )}
        </Card>
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
            accountProjectionStable={
              !setup.state.refreshing && !setup.state.refreshProblem
            }
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
      ) : lifecycleRecoveryPending ? null : actionKey ? (
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
            disabled={actionPending || !setupProjectionStable}
            onPress={() => runStepAction(projection.step)}
            testID="live-setup-action"
          />
        </>
      ) : null}

      {androidSenderGate || lifecycleRecoveryPending || actionKey ? null : (
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
      {ordinarySetup ? (
        <Button
          label={t('live.guidedSetup.finishLater')}
          disabled={actionPending || !setupProjectionStable}
          onPress={() => {
            if (setupProjectionStable) onDefer();
          }}
          variant="ghost"
          testID="live-setup-defer"
        />
      ) : null}
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
