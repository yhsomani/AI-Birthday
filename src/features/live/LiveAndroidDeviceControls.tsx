import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';

import type { AccountProjection } from '../../domain/account/model';
import type {
  SenderTransferOperationProjection,
  SenderTransferReview,
} from '../../domain/device/model';
import type { NativeRevision } from '../../domain/shared/brand';
import type {
  NativeProblem,
  ProjectionEnvelope,
} from '../../domain/shared/result';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  ReadinessBanner,
  SectionHeading,
  StatusRow,
} from '../../design-system/components/Primitives';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { formatLiveInstant } from '../../localization/formatLive';
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

const transferConsequenceKeys: Readonly<
  Record<SenderTransferReview['consequenceKeys'][number], TranslationKey>
> = {
  'transfer.consequence.old-phone-revoked':
    'live.device.transfer.oldPhoneRevoked',
  'transfer.consequence.new-phone-test-only':
    'live.device.transfer.newPhoneTestOnly',
  'transfer.consequence.test-required': 'live.device.transfer.testRequired',
};

const transferStateKeys: Readonly<
  Record<
    Exclude<SenderTransferOperationProjection['kind'], 'none' | 'unavailable'>,
    TranslationKey
  >
> = {
  verifying: 'live.device.transfer.state.verifying',
  'remote-pending': 'live.device.transfer.state.remotePending',
  'remote-draining': 'live.device.transfer.state.remoteDraining',
  failed: 'live.device.transfer.state.failed',
  complete: 'live.device.transfer.state.complete',
};

type TransferReviewState = Readonly<{
  accountRevision: NativeRevision;
  expiresAtMs: number;
  review: SenderTransferReview;
  revision: NativeRevision;
}>;

const transferReviewUiTtlMs = 9 * 60 * 1000 + 30 * 1000;

export function LiveAndroidDeviceControls({
  account,
  accountProjectionStable,
  onAccountReload,
  onOpenAutomation,
  port,
  showNotifications = true,
}: {
  account?: ProjectionEnvelope<AccountProjection> | undefined;
  accountProjectionStable: boolean;
  onAccountReload: () => Promise<unknown>;
  onOpenAutomation: () => void;
  port: LiveAppPort;
  showNotifications?: boolean | undefined;
}) {
  const { language, t } = useAppLocalization();
  const loadNotifications = useCallback(
    () => port.getNotificationPermission(),
    [port],
  );
  const notifications = useLiveProjection(loadNotifications, port, []);
  const loadTransfer = useCallback(
    () => port.getSenderTransferOperation(),
    [port],
  );
  const transfer = useLiveProjection(loadTransfer, port, [
    'account',
    'automation',
    'privacy',
  ]);
  const [pending, setPending] = useState<string>();
  const [problem, setProblem] = useState<NativeProblem>();
  const [message, setMessage] = useState<string>();
  const [review, setReview] = useState<TransferReviewState>();
  const [transferSupportExpanded, setTransferSupportExpanded] = useState(false);
  const reviewRequestSequence = useRef(0);
  const mounted = useRef(true);
  const accountTruthRef = useRef<
    Readonly<{
      revision: NativeRevision | undefined;
      stable: boolean;
      standby: boolean;
    }>
  >({ revision: undefined, stable: false, standby: false });

  const connected =
    account?.value.kind === 'connected' &&
    account.value.sender.platform === 'android'
      ? account.value
      : undefined;
  const standby =
    connected?.sender.kind === 'standby' ? connected.sender : undefined;
  const accountTransferPending = connected?.sender.kind === 'transfer-pending';
  const accountLifecycleEligible = Boolean(
    connected && connected.sender.kind !== 'deleting',
  );
  const notificationProjectionStable =
    notifications.state.kind === 'ready' &&
    !notifications.state.refreshing &&
    !notifications.state.refreshProblem;
  accountTruthRef.current = {
    revision: account?.revision,
    stable: accountProjectionStable,
    standby: Boolean(standby),
  };

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      reviewRequestSequence.current += 1;
    };
  }, []);

  useEffect(
    () =>
      port.subscribeInvalidations(event => {
        if (
          event.areas.includes('account') ||
          event.areas.includes('automation') ||
          event.areas.includes('privacy')
        ) {
          reviewRequestSequence.current += 1;
          setReview(undefined);
          setPending(undefined);
          setTransferSupportExpanded(false);
        }
      }),
    [port],
  );
  useEffect(() => {
    const subscription = AppState.addEventListener('change', nextState => {
      if (nextState === 'active') {
        reviewRequestSequence.current += 1;
        setReview(undefined);
        setPending(undefined);
        setTransferSupportExpanded(false);
      }
    });
    return () => subscription.remove();
  }, []);

  const finish = () => {
    setPending(undefined);
  };

  const requestNotifications = async () => {
    if (
      !accountProjectionStable ||
      !accountLifecycleEligible ||
      !notificationProjectionStable ||
      notifications.state.kind !== 'ready' ||
      notifications.state.result.envelope.value.kind !== 'not-requested' ||
      transferApplicable
    ) {
      return;
    }
    setPending('notifications');
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<
      ReturnType<LiveAppPort['requestNotificationPermission']>
    >;
    try {
      result = await port.requestNotificationPermission();
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      setProblem(result.problem);
    } else {
      setMessage(
        result.envelope.value.kind === 'granted'
          ? t('live.device.notifications.grantedMessage')
          : result.envelope.value.kind === 'cancelled'
          ? t('live.device.notifications.cancelledMessage')
          : t('live.device.notifications.reviewSettingsMessage'),
      );
      await notifications.reload();
    }
    finish();
  };

  const openNotificationSettings = async () => {
    if (
      !accountProjectionStable ||
      !accountLifecycleEligible ||
      !notificationProjectionStable ||
      notifications.state.kind !== 'ready' ||
      notifications.state.result.envelope.value.kind !== 'settings-required' ||
      transferApplicable
    ) {
      return;
    }
    setPending('notification-settings');
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['openNotificationSettings']>>;
    try {
      result = await port.openNotificationSettings();
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      setProblem(result.problem);
    } else {
      setMessage(
        result.envelope.value.kind === 'opened'
          ? t('live.device.notifications.settingsOpened')
          : t('live.device.notifications.cancelledMessage'),
      );
    }
    finish();
  };

  const prepareTransfer = async () => {
    if (
      !account ||
      !standby ||
      !accountProjectionStable ||
      !transferProjectionStable ||
      (transferValue?.kind !== 'none' && transferValue?.kind !== 'failed')
    )
      return;
    setPending('transfer-prepare');
    setProblem(undefined);
    setMessage(undefined);
    setReview(undefined);
    const accountRevision = account.revision;
    const request = reviewRequestSequence.current + 1;
    reviewRequestSequence.current = request;
    const expiresAtMs = Date.now() + transferReviewUiTtlMs;
    let result: Awaited<ReturnType<LiveAppPort['prepareSenderTransfer']>>;
    try {
      result = await port.prepareSenderTransfer({
        expectedRevision: account.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (
      !mounted.current ||
      request !== reviewRequestSequence.current ||
      !accountTruthRef.current.stable ||
      !accountTruthRef.current.standby ||
      accountTruthRef.current.revision !== accountRevision
    ) {
      return;
    }
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
        await onAccountReload();
      }
      setProblem(result.problem);
    } else {
      setReview({
        accountRevision,
        expiresAtMs,
        review: result.envelope.value,
        revision: result.envelope.revision,
      });
    }
    finish();
  };

  const beginTransfer = async () => {
    if (
      !review ||
      review.expiresAtMs <= Date.now() ||
      !standby ||
      account?.revision !== review.accountRevision ||
      !accountProjectionStable ||
      !transferProjectionStable ||
      transferValue?.kind !== 'none'
    )
      return;
    setPending('transfer-begin');
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['beginSenderTransfer']>>;
    try {
      result = await port.beginSenderTransfer({
        handle: review.review.handle,
        expectedRevision: review.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
        await onAccountReload();
      }
      setProblem(result.problem);
    } else {
      setReview(undefined);
      setMessage(t('live.device.transfer.started'));
      await transfer.reload();
      await onAccountReload();
    }
    finish();
  };

  const continueTransfer = async (
    operation: Exclude<
      SenderTransferOperationProjection,
      { kind: 'none' | 'unavailable' | 'complete' | 'failed' }
    >,
  ) => {
    if (
      !accountProjectionStable ||
      !transferProjectionStable ||
      !activeTransferModeCurrent ||
      !activeTransfer ||
      activeTransfer.kind !== operation.kind ||
      activeTransfer.id !== operation.id
    )
      return;
    setPending('transfer-continue');
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['resumeSenderTransfer']>>;
    try {
      result =
        operation.kind === 'remote-draining'
          ? await port.completeSenderTransfer({ operationId: operation.id })
          : await port.resumeSenderTransfer({ operationId: operation.id });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      setProblem(result.problem);
    } else {
      setMessage(
        result.envelope.value.kind === 'complete'
          ? t('live.device.transfer.completedMessage')
          : t('live.device.transfer.stillRunning'),
      );
      await transfer.reload();
      await onAccountReload();
    }
    finish();
  };

  const checkTransferStatus = async () => {
    if (transfer.state.kind === 'ready' && transfer.state.refreshing) return;
    reviewRequestSequence.current += 1;
    setReview(undefined);
    setPending('transfer-check');
    setProblem(undefined);
    setMessage(undefined);
    try {
      await Promise.all([transfer.reload(), onAccountReload()]);
    } finally {
      finish();
    }
  };

  const checkAccountStatus = async () => {
    setPending('account-check');
    setProblem(undefined);
    setMessage(undefined);
    try {
      await onAccountReload();
    } finally {
      finish();
    }
  };

  const transferValue =
    transfer.state.kind === 'ready'
      ? transfer.state.result.envelope.value
      : undefined;
  const applicableSender = Boolean(standby || accountTransferPending);
  const activeTransfer =
    transferValue &&
    transferValue.kind !== 'none' &&
    transferValue.kind !== 'unavailable' &&
    transferValue.kind !== 'failed' &&
    (transferValue.kind !== 'complete' ||
      connected?.sender.kind === 'test-only')
      ? transferValue
      : undefined;
  const activeTransferModeCurrent = Boolean(
    activeTransfer &&
      accountLifecycleEligible &&
      (activeTransfer.kind === 'complete'
        ? connected?.sender.kind === 'test-only'
        : activeTransfer.kind === 'remote-draining'
        ? accountTransferPending
        : Boolean(standby || accountTransferPending)),
  );
  const applicableFailedTransfer =
    transferValue?.kind === 'failed' && applicableSender
      ? transferValue
      : undefined;
  const terminalTransferNeedsReconciliation = Boolean(
    transferValue?.kind === 'complete' && applicableSender,
  );
  const visibleTransfer = activeTransfer ?? applicableFailedTransfer;
  const applicableUnavailable =
    transferValue?.kind === 'unavailable' &&
    Boolean(applicableSender || review);
  const transferProjectionStable =
    transfer.state.kind === 'ready' &&
    !transfer.state.refreshing &&
    !transfer.state.refreshProblem &&
    transfer.state.result.envelope.revision === account?.revision;
  const transferRefreshing =
    transfer.state.kind === 'ready' && transfer.state.refreshing;
  const transferApplicable = Boolean(
    applicableSender ||
      review ||
      activeTransfer ||
      applicableFailedTransfer ||
      applicableUnavailable,
  );
  const notificationSectionVisible = Boolean(
    showNotifications &&
      accountProjectionStable &&
      accountLifecycleEligible &&
      !transferApplicable &&
      (notifications.state.kind !== 'ready' ||
        notifications.state.refreshing ||
        notifications.state.refreshProblem ||
        notifications.state.result.envelope.value.kind !== 'granted'),
  );
  const transferReviewCurrent = Boolean(
    review &&
      review.expiresAtMs > Date.now() &&
      standby &&
      account?.revision === review.accountRevision &&
      accountProjectionStable &&
      transferProjectionStable &&
      transferValue?.kind === 'none',
  );
  const transferStatusCheckVisible = Boolean(
    transfer.state.kind === 'ready' &&
      transferApplicable &&
      !(transferValue?.kind === 'unavailable' && !review) &&
      (!accountProjectionStable ||
        !transferProjectionStable ||
        !accountLifecycleEligible ||
        transfer.state.refreshProblem ||
        Boolean(activeTransfer && !activeTransferModeCurrent) ||
        terminalTransferNeedsReconciliation ||
        Boolean(applicableFailedTransfer && accountTransferPending) ||
        (transferValue?.kind === 'none' && accountTransferPending && !review) ||
        (review && transferValue?.kind !== 'none')),
  );
  const accountStatusCheckVisible = Boolean(
    account && !accountProjectionStable && !transferApplicable,
  );
  useEffect(() => {
    reviewRequestSequence.current += 1;
    setReview(undefined);
    setPending(current =>
      current === 'transfer-prepare' || current === 'transfer-begin'
        ? undefined
        : current,
    );
  }, [
    account?.revision,
    accountProjectionStable,
    standby?.activeOtherDeviceLabel,
  ]);
  useEffect(() => {
    if (!review) return;
    const remaining = review.expiresAtMs - Date.now();
    if (remaining <= 0) {
      reviewRequestSequence.current += 1;
      setReview(undefined);
      return;
    }
    const timeout = setTimeout(() => {
      reviewRequestSequence.current += 1;
      setReview(undefined);
    }, remaining);
    return () => clearTimeout(timeout);
  }, [review]);
  useEffect(() => {
    if (
      review &&
      accountProjectionStable &&
      (!standby || account?.revision !== review.accountRevision)
    ) {
      setReview(undefined);
    }
  }, [account?.revision, accountProjectionStable, review, standby]);

  return (
    <>
      <LiveActionFeedback problem={problem} message={message} />
      {accountStatusCheckVisible ? (
        <Button
          label={t('live.device.checkAccountStatus')}
          disabled={pending !== undefined}
          onPress={checkAccountStatus}
          testID="live-check-account-status"
        />
      ) : null}
      {notificationSectionVisible ? (
        <>
          <SectionHeading title={t('live.device.notifications.title')} />
          <AppText color="muted">{t('live.device.notifications.body')}</AppText>
        </>
      ) : null}
      {notificationSectionVisible && notifications.state.kind === 'loading' ? (
        <LiveLoading label={t('live.device.notifications.loading')} />
      ) : null}
      {notificationSectionVisible && notifications.state.kind === 'error' ? (
        <LiveError
          title={t('live.device.notifications.unavailable')}
          problem={notifications.state.problem}
          onRetry={() => notifications.reload()}
        />
      ) : null}
      {notificationSectionVisible && notifications.state.kind === 'ready' ? (
        <Card>
          {notifications.state.refreshProblem ? (
            <>
              <LiveRefreshProblem
                problem={notifications.state.refreshProblem}
              />
              <Button
                label={t('live.device.notifications.checkStatus')}
                disabled={
                  pending !== undefined || notifications.state.refreshing
                }
                onPress={() => notifications.reload()}
                testID="live-check-notification-status"
              />
            </>
          ) : null}
          <StatusRow
            title={
              notifications.state.refreshing
                ? t('live.device.notifications.loading')
                : notifications.state.result.envelope.value.kind === 'granted'
                ? t('live.device.notifications.granted')
                : notifications.state.result.envelope.value.kind ===
                  'not-requested'
                ? t('live.device.notifications.notRequested')
                : t('live.device.notifications.settingsRequired')
            }
            tone={
              notifications.state.refreshing
                ? 'neutral'
                : notifications.state.result.envelope.value.kind === 'granted'
                ? 'positive'
                : 'warning'
            }
          />
          {notifications.state.result.envelope.value.kind === 'not-requested' &&
          accountLifecycleEligible &&
          notificationProjectionStable &&
          !transferApplicable ? (
            <Button
              label={t('live.device.notifications.allow')}
              disabled={pending !== undefined}
              onPress={requestNotifications}
              testID="live-request-notification-permission"
            />
          ) : null}
          {notifications.state.result.envelope.value.kind ===
            'settings-required' &&
          accountLifecycleEligible &&
          notificationProjectionStable &&
          !transferApplicable ? (
            <Button
              label={t('live.device.notifications.openSettings')}
              disabled={pending !== undefined}
              onPress={openNotificationSettings}
              variant="secondary"
              testID="live-open-notification-settings"
            />
          ) : null}
        </Card>
      ) : null}

      {transferApplicable ? (
        <>
          <SectionHeading title={t('live.device.transfer.title')} />
          <AppText color="muted">{t('live.device.transfer.body')}</AppText>
        </>
      ) : null}
      {transfer.state.kind === 'loading' && applicableSender ? (
        <LiveLoading label={t('live.device.transfer.loading')} />
      ) : null}
      {transfer.state.kind === 'error' &&
      Boolean(applicableSender || review) ? (
        <LiveError
          title={t('live.device.transfer.unavailable')}
          problem={transfer.state.problem}
          onRetry={checkTransferStatus}
          retryTestID="live-retry-sender-transfer"
        />
      ) : null}
      {transfer.state.kind === 'ready' &&
      transfer.state.refreshProblem &&
      transferApplicable ? (
        <LiveRefreshProblem problem={transfer.state.refreshProblem} />
      ) : null}
      {transferStatusCheckVisible ? (
        <Button
          label={t('live.device.transfer.checkStatus')}
          disabled={pending !== undefined || transferRefreshing}
          onPress={checkTransferStatus}
          testID="live-check-sender-transfer"
        />
      ) : null}
      {applicableUnavailable && !review ? (
        <>
          <ReadinessBanner
            title={t('live.device.transfer.safetyUnavailable')}
            detail={t('live.device.transfer.safetyUnavailableBody')}
            tone="critical"
          />
          <Button
            label={t('live.common.tryAgain')}
            disabled={pending !== undefined}
            onPress={checkTransferStatus}
            testID="live-retry-sender-transfer"
          />
        </>
      ) : null}
      {transferValue?.kind === 'none' &&
      standby &&
      !review &&
      !transferStatusCheckVisible ? (
        <Card>
          <StatusRow
            title={t('live.device.transfer.otherPhone')}
            detail={standby.activeOtherDeviceLabel}
            tone="warning"
          />
          <Button
            label={t('live.device.transfer.prepare')}
            disabled={
              pending !== undefined ||
              !accountProjectionStable ||
              !transferProjectionStable
            }
            onPress={prepareTransfer}
            testID="live-prepare-sender-transfer"
          />
        </Card>
      ) : null}
      {transferValue?.kind === 'none' && accountTransferPending ? (
        <Card>
          <StatusRow
            title={t(safeReasonMessageKey('transfer-pending'))}
            tone="warning"
          />
        </Card>
      ) : null}
      {review && transferReviewCurrent ? (
        <Card>
          <AppText variant="heading">
            {t('live.device.transfer.reviewTitle')}
          </AppText>
          {review.review.consequenceKeys.map(key => (
            <StatusRow
              key={key}
              title={t(transferConsequenceKeys[key])}
              tone="warning"
            />
          ))}
          <StatusRow
            title={t('live.device.transfer.reauthentication')}
            tone="warning"
          />
          {review.review.preissuedPermitMayFinish ? (
            <ReadinessBanner
              title={t('live.device.transfer.preissuedTitle')}
              detail={t('live.device.transfer.preissuedBody')}
              tone="critical"
            />
          ) : null}
          <Button
            label={t('live.device.transfer.confirm')}
            disabled={pending !== undefined}
            onPress={beginTransfer}
            variant="danger"
            testID="live-confirm-sender-transfer"
          />
          <Button
            label={t('live.common.cancel')}
            disabled={pending !== undefined}
            onPress={() => setReview(undefined)}
            variant="secondary"
          />
        </Card>
      ) : null}
      {visibleTransfer && !review ? (
        <Card>
          <StatusRow
            title={t(transferStateKeys[visibleTransfer.kind])}
            tone={
              visibleTransfer.kind === 'complete'
                ? 'positive'
                : visibleTransfer.kind === 'failed'
                ? 'critical'
                : 'warning'
            }
          />
          {'reason' in visibleTransfer ? (
            <StatusRow
              title={t(safeReasonMessageKey(visibleTransfer.reason))}
              tone="warning"
            />
          ) : null}
          {'drainUntil' in visibleTransfer ? (
            <AppText color="muted">
              {t('live.device.transfer.drainUntil', {
                time: formatLiveInstant(visibleTransfer.drainUntil, language),
              })}
            </AppText>
          ) : null}
          {visibleTransfer.preissuedPermitMayFinish ? (
            <ReadinessBanner
              title={t('live.device.transfer.preissuedTitle')}
              detail={t('live.device.transfer.preissuedBody')}
              tone="critical"
            />
          ) : null}
          {(visibleTransfer.kind === 'verifying' ||
            visibleTransfer.kind === 'remote-pending' ||
            visibleTransfer.kind === 'remote-draining') &&
          activeTransferModeCurrent &&
          !transferStatusCheckVisible ? (
            <Button
              label={t('live.device.transfer.continue')}
              disabled={
                pending !== undefined ||
                !accountProjectionStable ||
                !transferProjectionStable
              }
              onPress={() => continueTransfer(visibleTransfer)}
              testID="live-continue-sender-transfer"
            />
          ) : null}
          {visibleTransfer.kind === 'failed' &&
          standby &&
          !transferStatusCheckVisible ? (
            <Button
              label={t('live.common.tryAgain')}
              disabled={
                pending !== undefined ||
                !accountProjectionStable ||
                !transferProjectionStable
              }
              onPress={prepareTransfer}
              testID="live-retry-sender-transfer"
            />
          ) : null}
          {visibleTransfer.kind === 'complete' &&
          activeTransferModeCurrent &&
          !transferStatusCheckVisible ? (
            <>
              <ReadinessBanner
                title={t('live.device.transfer.testRequiredTitle')}
                detail={t('live.device.transfer.testRequiredBody')}
                tone="warning"
              />
              <Button
                label={t('live.device.transfer.openAutomation')}
                onPress={onOpenAutomation}
                testID="live-transfer-open-automation"
              />
            </>
          ) : null}
          {'reason' in visibleTransfer ? (
            <>
              <Button
                label={t(
                  transferSupportExpanded
                    ? 'live.attention.hideSupportDetails'
                    : 'live.attention.showSupportDetails',
                )}
                onPress={() =>
                  setTransferSupportExpanded(expanded => !expanded)
                }
                variant="secondary"
                testID="live-transfer-support-toggle"
              />
              {transferSupportExpanded ? (
                <Card testID="live-transfer-support-details">
                  <AppText color="muted" variant="caption">
                    {t('live.common.code', {
                      value: visibleTransfer.reason,
                    })}
                  </AppText>
                </Card>
              ) : null}
            </>
          ) : null}
        </Card>
      ) : null}
    </>
  );
}
