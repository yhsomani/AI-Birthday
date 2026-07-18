import React, { useCallback, useEffect, useState } from 'react';

import type {
  PrivacyActionKind,
  PrivacyActionReview,
  PrivacyOperationProjection,
} from '../../domain/privacy/model';
import type { LifecycleRepairKind } from '../../domain/device/model';
import type { PlatformCapability } from '../../domain/shared/platform';
import type { NativeProblem } from '../../domain/shared/result';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  ChoiceChip,
  InlineReviewCard,
  ReadinessBanner,
  Screen,
  SectionHeading,
  SingleChoiceGroup,
  StatusRow,
} from '../../design-system/components/Primitives';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { safeReasonMessageKey } from '../../localization/reasonCopy';
import type { TranslationKey } from '../../localization/resources';
import type { LiveAppPort } from './LiveAppPort';
import {
  LiveActionFeedback,
  LiveError,
  LiveLoading,
  LiveRefreshProblem,
} from './LiveProjectionState';
import { nativeBridgeProblem } from './nativeProblem';
import { useLiveProjection } from './useLiveProjection';
import { LivePrivacyInventory } from './LivePrivacyInventory';

const privacyActions: readonly Readonly<{
  kind: PrivacyActionKind;
  label: TranslationKey;
}>[] = [
  { kind: 'disconnect-contacts', label: 'live.privacy.disconnect' },
  { kind: 'revoke-google-access', label: 'live.privacy.revoke' },
  { kind: 'sign-out-retain', label: 'live.privacy.signOutKeep' },
  { kind: 'sign-out-wipe', label: 'live.privacy.signOutWipe' },
  { kind: 'delete-account', label: 'live.privacy.deleteAccount' },
  { kind: 'wipe-local-data', label: 'live.privacy.wipeLocal' },
  { kind: 'clear-gemini-templates', label: 'live.privacy.clearTemplates' },
  { kind: 'clear-activity', label: 'live.privacy.clearActivity' },
];

const lifecycleRepairActions: readonly Readonly<{
  kind: LifecycleRepairKind;
  label: TranslationKey;
}>[] = [
  { kind: 'disconnect-contacts', label: 'live.privacy.disconnect' },
  { kind: 'revoke-google-access', label: 'live.privacy.revoke' },
  { kind: 'sign-out-wipe', label: 'live.privacy.signOutWipe' },
  { kind: 'wipe-local-data', label: 'live.privacy.wipeLocal' },
];

const privacyConsequenceKeys: Readonly<Record<string, TranslationKey>> = {
  'privacy.consequence.activity-hidden':
    'live.privacy.consequence.activityHidden',
  'privacy.consequence.safety-retained':
    'live.privacy.consequence.safetyRetained',
  'privacy.consequence.gemini-templates-removed':
    'live.privacy.consequence.templatesRemoved',
  'privacy.consequence.reapproval-required':
    'live.privacy.consequence.reapprovalRequired',
  'privacy.consequence.automation-paused':
    'live.privacy.consequence.automationPaused',
  'privacy.consequence.same-account-setup-retained':
    'live.privacy.consequence.sameAccountRetained',
  'privacy.consequence.google-working-data-removed':
    'live.privacy.consequence.googleDataRemoved',
  'privacy.consequence.all-google-scopes-revoked':
    'live.privacy.consequence.googleScopesRevoked',
  'privacy.consequence.remote-deletion-drain-started':
    'live.privacy.consequence.remoteDeletionStarted',
  'privacy.consequence.local-data-erased-after-drain':
    'live.privacy.consequence.localEraseAfterDrain',
  'privacy.consequence.local-data-erased':
    'live.privacy.consequence.localDataErased',
  'privacy.consequence.local-data': 'live.privacy.consequence.localDataErased',
  'privacy.consequence.external-sms':
    'live.privacy.consequence.externalCopiesRemain',
  'privacy.consequence.android-reset-paused':
    'live.privacy.consequence.androidResetPaused',
  'privacy.consequence.android-test-required':
    'live.privacy.consequence.androidTestRequired',
};

const operationStateKeys: Record<
  PrivacyOperationProjection['kind'],
  TranslationKey
> = {
  queued: 'live.privacy.state.queued',
  pausing: 'live.privacy.state.pausing',
  'remote-draining': 'live.privacy.state.remoteDraining',
  'remote-unknown': 'live.privacy.state.remoteUnknown',
  'local-wiping': 'live.privacy.state.localWiping',
  'remote-pending': 'live.privacy.state.remotePending',
  verifying: 'live.privacy.state.verifying',
  complete: 'live.privacy.state.complete',
  failed: 'live.privacy.state.failed',
};

type ReviewState = Readonly<{
  review: PrivacyActionReview;
  revision: import('../../domain/shared/brand').NativeRevision;
  source: 'action-list' | 'pending-deletion-local-wipe';
}>;

export function LivePrivacyScreen({
  onBack,
  onOpenHelpLegal,
  platform,
  port,
}: {
  onBack: () => void;
  onOpenHelpLegal: () => void;
  platform: PlatformCapability['platform'];
  port: LiveAppPort;
}) {
  const { t } = useAppLocalization();
  const loadInventory = useCallback(() => port.getInventory(), [port]);
  const inventory = useLiveProjection(loadInventory, port, ['privacy']);
  const loadAccount = useCallback(() => port.getAccount(), [port]);
  const account = useLiveProjection(loadAccount, port, ['account', 'privacy']);
  const loadDeletionReceipt = useCallback(
    () => port.getLatestDeletionReceipt(),
    [port],
  );
  const deletionReceipt = useLiveProjection(loadDeletionReceipt, port, [
    'privacy',
  ]);
  const loadCurrentOperation = useCallback(
    () => port.getCurrentOperation(),
    [port],
  );
  const currentOperation = useLiveProjection(loadCurrentOperation, port, [
    'account',
    'privacy',
  ]);
  const [selected, setSelected] = useState<PrivacyActionKind>();
  const [review, setReview] = useState<ReviewState>();
  const [operation, setOperation] = useState<PrivacyOperationProjection>();
  const [pending, setPending] = useState(false);
  const [problem, setProblem] = useState<NativeProblem>();
  const [message, setMessage] = useState<string>();

  useEffect(
    () =>
      port.subscribeInvalidations(event => {
        if (event.areas.includes('privacy')) {
          setReview(undefined);
        }
      }),
    [port],
  );

  const prepare = async () => {
    if (!selected || inventory.state.kind !== 'ready') {
      return;
    }
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['prepareAction']>>;
    try {
      result = await port.prepareAction({
        kind: selected,
        expectedRevision: inventory.state.result.envelope.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
        await inventory.reload();
      }
      setProblem(result.problem);
      setReview(undefined);
      setPending(false);
      return;
    }
    setReview({
      review: result.envelope.value,
      revision: result.envelope.revision,
      source: 'action-list',
    });
    setPending(false);
  };

  const preparePendingDeletionLocalWipe = async () => {
    if (inventory.state.kind !== 'ready') {
      return;
    }
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    setSelected(undefined);
    let result: Awaited<ReturnType<LiveAppPort['prepareAction']>>;
    try {
      result = await port.prepareAction({
        kind: 'wipe-local-data',
        expectedRevision: inventory.state.result.envelope.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
        await inventory.reload();
      }
      setProblem(result.problem);
      setReview(undefined);
      setPending(false);
      return;
    }
    setReview({
      review: result.envelope.value,
      revision: result.envelope.revision,
      source: 'pending-deletion-local-wipe',
    });
    setPending(false);
  };

  const confirm = async () => {
    if (!review) {
      return;
    }
    setPending(true);
    setProblem(undefined);
    let result: Awaited<ReturnType<LiveAppPort['confirmAction']>>;
    try {
      result = await port.confirmAction({
        handle: review.review.handle,
        expectedRevision: review.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
        await inventory.reload();
        setReview(undefined);
      }
      setProblem(result.problem);
      setPending(false);
      return;
    }
    setOperation(result.envelope.value);
    setReview(undefined);
    setSelected(undefined);
    setPending(false);
    if (result.envelope.value.kind === 'complete') {
      await inventory.reload();
      setMessage(t('live.privacy.operationComplete'));
    }
  };

  const refreshOperation = async () => {
    const current =
      operation ??
      (currentOperation.state.kind === 'ready' &&
      currentOperation.state.result.envelope.value.kind !== 'none' &&
      currentOperation.state.result.envelope.value.kind !== 'unavailable'
        ? currentOperation.state.result.envelope.value
        : undefined);
    if (!current) {
      return;
    }
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['getOperation']>>;
    try {
      result = await port.getOperation(current.id);
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      setProblem(result.problem);
    } else {
      setOperation(result.envelope.value);
      if (result.envelope.value.kind === 'complete') {
        await inventory.reload();
        setMessage(t('live.privacy.operationComplete'));
      }
    }
    setPending(false);
  };

  const resumeOperation = async () => {
    const current =
      operation ??
      (currentOperation.state.kind === 'ready' &&
      currentOperation.state.result.envelope.value.kind !== 'none' &&
      currentOperation.state.result.envelope.value.kind !== 'unavailable'
        ? currentOperation.state.result.envelope.value
        : undefined);
    if (!current || current.kind === 'complete' || current.kind === 'failed') {
      return;
    }
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['resumeOperation']>>;
    try {
      result = await port.resumeOperation(current.id);
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      setProblem(result.problem);
    } else {
      setOperation(result.envelope.value);
      await currentOperation.reload();
      if (result.envelope.value.kind === 'complete') {
        await inventory.reload();
        setMessage(t('live.privacy.operationComplete'));
      } else {
        setMessage(t('live.privacy.operationResumed'));
      }
    }
    setPending(false);
  };

  const repairLifecycleState = async (kind: LifecycleRepairKind) => {
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['repairLifecycleState']>>;
    try {
      result = await port.repairLifecycleState({ kind });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      setProblem(result.problem);
      setPending(false);
      return;
    }
    setOperation(result.envelope.value);
    await Promise.all([
      account.reload(),
      currentOperation.reload(),
      deletionReceipt.reload(),
      inventory.reload(),
    ]);
    setMessage(
      result.envelope.value.kind === 'complete'
        ? t('live.privacy.operationComplete')
        : t('live.privacy.operationResumed'),
    );
    setPending(false);
  };

  const checkAccountDeletionStatus = async () => {
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['checkAccountDeletionStatus']>>;
    try {
      result = await port.checkAccountDeletionStatus();
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      setProblem(result.problem);
      setPending(false);
      return;
    }
    await Promise.all([
      account.reload(),
      currentOperation.reload(),
      deletionReceipt.reload(),
      inventory.reload(),
    ]);
    const status = result.envelope.value;
    if (status.kind === 'complete') {
      setMessage(t('live.privacy.deletionCompleteBody'));
    } else if (status.kind === 'remote-draining') {
      setMessage(t('live.privacy.deletionStillRunning'));
    } else {
      setMessage(t('live.privacy.deletionProofUnavailable'));
    }
    setPending(false);
  };

  const retryPendingDeletionWithGoogle = async () => {
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['continueWithGoogle']>>;
    try {
      result = await port.continueWithGoogle();
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      setProblem(result.problem);
      setPending(false);
      return;
    }
    await Promise.all([
      account.reload(),
      currentOperation.reload(),
      deletionReceipt.reload(),
    ]);
    setMessage(t('live.privacy.deletionRetrySubmitted'));
    setPending(false);
  };

  const visibleOperation =
    operation ??
    (currentOperation.state.kind === 'ready' &&
    currentOperation.state.result.envelope.value.kind !== 'none' &&
    currentOperation.state.result.envelope.value.kind !== 'unavailable'
      ? currentOperation.state.result.envelope.value
      : undefined);
  const currentAllowsNewAction =
    currentOperation.state.kind === 'ready' &&
    (currentOperation.state.result.envelope.value.kind === 'none' ||
      currentOperation.state.result.envelope.value.kind === 'complete' ||
      currentOperation.state.result.envelope.value.kind === 'failed');
  const localAllowsNewAction =
    operation === undefined ||
    operation.kind === 'complete' ||
    operation.kind === 'failed';
  const deletionAllowsNewAction =
    deletionReceipt.state.kind === 'ready' &&
    (deletionReceipt.state.result.envelope.value.kind === 'none' ||
      deletionReceipt.state.result.envelope.value.kind === 'complete');
  const canStartNewAction =
    currentAllowsNewAction && localAllowsNewAction && deletionAllowsNewAction;
  const pendingDeletionNeedsLocalWipe =
    visibleOperation?.action === 'delete-account' &&
    (visibleOperation.kind === 'remote-pending' ||
      visibleOperation.kind === 'remote-draining') &&
    !(
      visibleOperation.kind === 'remote-draining' &&
      'localDataErased' in visibleOperation
    ) &&
    deletionReceipt.state.kind === 'ready' &&
    deletionReceipt.state.result.envelope.value.kind === 'none';
  const sameAccountDeletionRetryAvailable =
    (visibleOperation?.kind === 'remote-unknown' &&
      visibleOperation.sameAccountRetryAvailable) ||
    (deletionReceipt.state.kind === 'ready' &&
      deletionReceipt.state.result.envelope.value.kind === 'remote-unknown' &&
      deletionReceipt.state.result.envelope.value.sameAccountRetryAvailable);
  const lifecycleRepairRequired =
    platform === 'android' &&
    account.state.kind === 'ready' &&
    account.state.result.envelope.value.kind === 'cleanup-pending' &&
    account.state.result.envelope.value.operation === 'repair';

  return (
    <Screen includeTopInset testID="live-privacy-screen">
      <Button
        label={t('live.common.back')}
        onPress={onBack}
        variant="ghost"
        testID="live-privacy-back"
      />
      <AppText variant="title" accessibilityRole="header">
        {t('live.privacy.title')}
      </AppText>
      <AppText color="muted">{t('live.privacy.body')}</AppText>
      <Card>
        <AppText variant="heading">
          {t('live.privacy.screenCaptureTitle')}
        </AppText>
        <AppText color="muted">{t('live.privacy.screenCaptureBody')}</AppText>
      </Card>
      <LiveActionFeedback problem={problem} message={message} />
      {deletionReceipt.state.kind === 'ready' &&
      deletionReceipt.state.result.envelope.value.kind === 'remote-draining' ? (
        <ReadinessBanner
          title={t('live.privacy.deletionDrainingTitle')}
          detail={t('live.privacy.deletionDrainingBody')}
          tone="warning"
          actionLabel={
            pending
              ? t('live.privacy.checkingDeletion')
              : t('live.privacy.checkDeletion')
          }
          actionDisabled={pending}
          onAction={checkAccountDeletionStatus}
        />
      ) : null}
      {deletionReceipt.state.kind === 'ready' &&
      deletionReceipt.state.result.envelope.value.kind === 'remote-unknown' ? (
        <ReadinessBanner
          title={t('live.privacy.deletionUnknownTitle')}
          detail={t('live.privacy.deletionUnknownBody')}
          tone="critical"
          actionLabel={
            pending
              ? t('live.privacy.checkingDeletion')
              : t('live.privacy.checkDeletion')
          }
          actionDisabled={pending}
          onAction={checkAccountDeletionStatus}
        />
      ) : null}
      {deletionReceipt.state.kind === 'ready' &&
      deletionReceipt.state.result.envelope.value.kind === 'complete' ? (
        <ReadinessBanner
          title={t('live.privacy.deletionCompleteTitle')}
          detail={t('live.privacy.deletionCompleteBody')}
          tone="positive"
        />
      ) : null}
      {deletionReceipt.state.kind === 'ready' &&
      deletionReceipt.state.result.envelope.value.kind === 'unavailable' ? (
        <ReadinessBanner
          title={t('live.privacy.recoveryUnavailable')}
          detail={t('live.privacy.recoveryUnavailableBody')}
          tone="critical"
        />
      ) : null}
      {deletionReceipt.state.kind === 'error' ? (
        <LiveError
          title={t('live.privacy.operationUnavailable')}
          problem={deletionReceipt.state.problem}
          onRetry={() => deletionReceipt.reload()}
        />
      ) : null}
      {currentOperation.state.kind === 'error' ? (
        <LiveError
          title={t('live.privacy.operationUnavailable')}
          problem={currentOperation.state.problem}
          onRetry={() => currentOperation.reload()}
        />
      ) : null}
      {currentOperation.state.kind === 'ready' &&
      currentOperation.state.result.envelope.value.kind === 'unavailable' ? (
        <ReadinessBanner
          title={t('live.privacy.recoveryUnavailable')}
          detail={t('live.privacy.recoveryUnavailableBody')}
          tone="critical"
        />
      ) : null}
      {lifecycleRepairRequired ? (
        <Card>
          <AppText variant="heading">{t('live.privacy.repairTitle')}</AppText>
          <StatusRow title={t('live.privacy.repairBody')} tone="critical" />
          {lifecycleRepairActions.map(action => (
            <Button
              key={action.kind}
              label={t(action.label)}
              disabled={pending}
              onPress={() => repairLifecycleState(action.kind)}
              variant="secondary"
              testID={`live-privacy-repair-${action.kind}`}
            />
          ))}
        </Card>
      ) : null}

      {inventory.state.kind === 'loading' ? (
        <LiveLoading label={t('live.privacy.loading')} />
      ) : null}
      {inventory.state.kind === 'error' ? (
        <LiveError
          title={t('live.privacy.unavailable')}
          problem={inventory.state.problem}
          onRetry={() => inventory.reload()}
        />
      ) : null}
      {inventory.state.kind === 'ready' ? (
        <>
          {inventory.state.refreshProblem ? (
            <LiveRefreshProblem problem={inventory.state.refreshProblem} />
          ) : null}
          <LivePrivacyInventory
            inventory={inventory.state.result.envelope.value}
            platform={platform}
          />
          {canStartNewAction ? (
            <>
              <SectionHeading title={t('live.privacy.choose')} />
              <SingleChoiceGroup
                label={t('live.privacy.choose')}
                testID="live-privacy-action-group"
              >
                {privacyActions.map(action => (
                  <ChoiceChip
                    key={action.kind}
                    label={t(action.label)}
                    onPress={() => {
                      setSelected(action.kind);
                      setReview(undefined);
                    }}
                    selected={selected === action.kind}
                    testID={`live-privacy-${action.kind}`}
                  />
                ))}
              </SingleChoiceGroup>
              {selected && !review ? (
                <Button
                  label={
                    pending
                      ? t('live.privacy.preparing')
                      : t('live.privacy.prepare')
                  }
                  disabled={pending}
                  onPress={prepare}
                  testID="live-privacy-prepare"
                />
              ) : null}
            </>
          ) : null}
          {pendingDeletionNeedsLocalWipe ? (
            <Card>
              <AppText variant="heading">
                {t('live.privacy.pendingWipeTitle')}
              </AppText>
              <ReadinessBanner
                title={t('live.privacy.pendingWipeWarningTitle')}
                detail={t('live.privacy.pendingWipeBody')}
                tone="critical"
              />
              <Button
                label={
                  pending
                    ? t('live.privacy.preparing')
                    : t('live.privacy.pendingWipeAction')
                }
                disabled={pending || review !== undefined}
                onPress={preparePendingDeletionLocalWipe}
                variant="danger"
                testID="live-privacy-pending-deletion-wipe"
              />
            </Card>
          ) : null}
          {review ? (
            <InlineReviewCard
              reviewKey={review.review.handle}
              testID="live-privacy-review"
              title={
                review.source === 'pending-deletion-local-wipe'
                  ? t('live.privacy.pendingWipeReviewTitle')
                  : t('live.privacy.reviewTitle')
              }
            >
              <StatusRow
                title={t(
                  privacyActions.find(
                    action => action.kind === review.review.kind,
                  )?.label ?? 'live.privacy.reviewTitle',
                )}
                tone="warning"
              />
              {review.review.consequenceKeys.map(value => (
                <React.Fragment key={value}>
                  <StatusRow
                    title={t(
                      privacyConsequenceKeys[value] ??
                        'live.privacy.consequence.generic',
                    )}
                    tone="warning"
                  />
                  <AppText color="muted" variant="caption">
                    {t('live.privacy.contractReference', { value })}
                  </AppText>
                </React.Fragment>
              ))}
              {review.review.preissuedPermitMayFinish ? (
                <ReadinessBanner
                  title={t('live.privacy.preissued')}
                  detail={t('live.privacy.external')}
                  tone="critical"
                />
              ) : null}
              {review.review.remoteConnectionRequired ? (
                <StatusRow
                  title={t('live.privacy.remoteRequired')}
                  tone="warning"
                />
              ) : null}
              {review.source === 'pending-deletion-local-wipe' ? (
                <AppText color="muted">
                  {t('live.privacy.pendingWipeReviewBody')}
                </AppText>
              ) : null}
              <AppText color="muted">{t('live.privacy.external')}</AppText>
              <Button
                label={
                  pending
                    ? t('live.privacy.confirming')
                    : t('live.privacy.confirm')
                }
                disabled={pending}
                onPress={confirm}
                variant="danger"
                testID="live-privacy-confirm"
              />
              <Button
                label={t('live.common.cancel')}
                disabled={pending}
                onPress={() => setReview(undefined)}
                variant="secondary"
              />
            </InlineReviewCard>
          ) : null}
          {visibleOperation ? (
            <Card>
              <AppText variant="heading">{t('live.privacy.operation')}</AppText>
              <StatusRow
                title={t('live.privacy.operationState', {
                  kind: t(operationStateKeys[visibleOperation.kind]),
                })}
                tone={visibleOperation.kind === 'failed' ? 'critical' : 'info'}
              />
              {'reason' in visibleOperation ? (
                <>
                  <StatusRow
                    title={t(safeReasonMessageKey(visibleOperation.reason))}
                    tone="warning"
                  />
                  <AppText color="muted" variant="caption">
                    {t('live.common.code', { value: visibleOperation.reason })}
                  </AppText>
                </>
              ) : null}
              {visibleOperation.kind === 'remote-draining' &&
              'localDataErased' in visibleOperation ? (
                <ReadinessBanner
                  title={t('live.privacy.deletionDrainingTitle')}
                  detail={t('live.privacy.deletionDrainingBody')}
                  tone="warning"
                />
              ) : null}
              {visibleOperation.kind === 'remote-unknown' ? (
                <ReadinessBanner
                  title={t('live.privacy.deletionUnknownTitle')}
                  detail={t('live.privacy.deletionUnknownBody')}
                  tone="critical"
                />
              ) : null}
              <AppText color="muted">
                {visibleOperation.kind === 'complete'
                  ? t('live.privacy.operationComplete')
                  : visibleOperation.kind === 'failed'
                  ? t('live.privacy.operationFailed')
                  : t('live.privacy.operationPending')}
              </AppText>
              {visibleOperation.kind !== 'complete' &&
              visibleOperation.kind !== 'failed' ? (
                <>
                  {visibleOperation.kind !== 'remote-unknown' ? (
                    <Button
                      label={t('live.privacy.resumeOperation')}
                      disabled={pending}
                      onPress={resumeOperation}
                      testID="live-privacy-resume-operation"
                    />
                  ) : null}
                  <Button
                    label={t('live.privacy.refreshOperation')}
                    disabled={pending}
                    onPress={refreshOperation}
                    variant="secondary"
                    testID="live-privacy-refresh-operation"
                  />
                  {visibleOperation.action === 'delete-account' &&
                  sameAccountDeletionRetryAvailable ? (
                    <Button
                      label={
                        pending
                          ? t('live.privacy.deletionRetrying')
                          : t('live.privacy.deletionRetryWithGoogle')
                      }
                      disabled={pending}
                      onPress={retryPendingDeletionWithGoogle}
                      variant="secondary"
                      testID="live-privacy-retry-deletion-google"
                    />
                  ) : null}
                  {visibleOperation.action === 'delete-account' ? (
                    <Button
                      label={t('live.privacy.openDeletionHelp')}
                      disabled={pending}
                      onPress={onOpenHelpLegal}
                      variant="secondary"
                      testID="live-privacy-deletion-help"
                    />
                  ) : null}
                </>
              ) : null}
            </Card>
          ) : null}
        </>
      ) : null}
    </Screen>
  );
}
