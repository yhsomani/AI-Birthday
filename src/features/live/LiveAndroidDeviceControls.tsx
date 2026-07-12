import React, { useCallback, useState } from 'react';

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
  review: SenderTransferReview;
  revision: NativeRevision;
}>;

export function LiveAndroidDeviceControls({
  account,
  onAccountReload,
  onOpenAutomation,
  port,
  showNotifications = true,
}: {
  account?: ProjectionEnvelope<AccountProjection> | undefined;
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

  const connected =
    account?.value.kind === 'connected' &&
    account.value.sender.platform === 'android'
      ? account.value
      : undefined;
  const standby =
    connected?.sender.kind === 'standby' ? connected.sender : undefined;

  const finish = () => {
    setPending(undefined);
  };

  const requestNotifications = async () => {
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
    if (!account) return;
    setPending('transfer-prepare');
    setProblem(undefined);
    setMessage(undefined);
    setReview(undefined);
    let result: Awaited<ReturnType<LiveAppPort['prepareSenderTransfer']>>;
    try {
      result = await port.prepareSenderTransfer({
        expectedRevision: account.revision,
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
      setReview({
        review: result.envelope.value,
        revision: result.envelope.revision,
      });
    }
    finish();
  };

  const beginTransfer = async () => {
    if (!review) return;
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

  const transferValue =
    transfer.state.kind === 'ready'
      ? transfer.state.result.envelope.value
      : undefined;
  const visibleTransfer =
    transferValue?.kind !== undefined &&
    transferValue.kind !== 'none' &&
    transferValue.kind !== 'unavailable'
      ? transferValue
      : undefined;

  return (
    <>
      <LiveActionFeedback problem={problem} message={message} />
      {showNotifications ? (
        <>
          <SectionHeading title={t('live.device.notifications.title')} />
          <AppText color="muted">{t('live.device.notifications.body')}</AppText>
        </>
      ) : null}
      {showNotifications && notifications.state.kind === 'loading' ? (
        <LiveLoading label={t('live.device.notifications.loading')} />
      ) : null}
      {showNotifications && notifications.state.kind === 'error' ? (
        <LiveError
          title={t('live.device.notifications.unavailable')}
          problem={notifications.state.problem}
          onRetry={() => notifications.reload()}
        />
      ) : null}
      {showNotifications && notifications.state.kind === 'ready' ? (
        <Card>
          {notifications.state.refreshProblem ? (
            <LiveRefreshProblem problem={notifications.state.refreshProblem} />
          ) : null}
          <StatusRow
            title={
              notifications.state.result.envelope.value.kind === 'granted'
                ? t('live.device.notifications.granted')
                : notifications.state.result.envelope.value.kind ===
                  'not-requested'
                ? t('live.device.notifications.notRequested')
                : t('live.device.notifications.settingsRequired')
            }
            tone={
              notifications.state.result.envelope.value.kind === 'granted'
                ? 'positive'
                : 'warning'
            }
          />
          {notifications.state.result.envelope.value.kind ===
          'not-requested' ? (
            <Button
              label={t('live.device.notifications.allow')}
              disabled={pending !== undefined}
              onPress={requestNotifications}
              testID="live-request-notification-permission"
            />
          ) : null}
          {notifications.state.result.envelope.value.kind ===
          'settings-required' ? (
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

      {standby || (transferValue && transferValue.kind !== 'none') ? (
        <>
          <SectionHeading title={t('live.device.transfer.title')} />
          <AppText color="muted">{t('live.device.transfer.body')}</AppText>
        </>
      ) : null}
      {transfer.state.kind === 'loading' && standby ? (
        <LiveLoading label={t('live.device.transfer.loading')} />
      ) : null}
      {transfer.state.kind === 'error' && standby ? (
        <LiveError
          title={t('live.device.transfer.unavailable')}
          problem={transfer.state.problem}
          onRetry={() => transfer.reload()}
        />
      ) : null}
      {transferValue?.kind === 'unavailable' ? (
        <ReadinessBanner
          title={t('live.device.transfer.safetyUnavailable')}
          detail={t('live.device.transfer.safetyUnavailableBody')}
          tone="critical"
        />
      ) : null}
      {transferValue?.kind === 'none' && standby ? (
        <Card>
          <StatusRow
            title={t('live.device.transfer.otherPhone')}
            detail={standby.activeOtherDeviceLabel}
            tone="warning"
          />
          <Button
            label={t('live.device.transfer.prepare')}
            disabled={pending !== undefined}
            onPress={prepareTransfer}
            testID="live-prepare-sender-transfer"
          />
        </Card>
      ) : null}
      {review ? (
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
      {visibleTransfer ? (
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
            <>
              <StatusRow
                title={t(safeReasonMessageKey(visibleTransfer.reason))}
                tone="warning"
              />
              <AppText color="muted" variant="caption">
                {t('live.common.code', { value: visibleTransfer.reason })}
              </AppText>
            </>
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
          {visibleTransfer.kind === 'verifying' ||
          visibleTransfer.kind === 'remote-pending' ||
          visibleTransfer.kind === 'remote-draining' ? (
            <Button
              label={t('live.device.transfer.continue')}
              disabled={pending !== undefined}
              onPress={() => continueTransfer(visibleTransfer)}
              variant="secondary"
              testID="live-continue-sender-transfer"
            />
          ) : null}
          {visibleTransfer.kind === 'failed' && standby ? (
            <Button
              label={t('live.common.tryAgain')}
              disabled={pending !== undefined}
              onPress={prepareTransfer}
              variant="secondary"
            />
          ) : null}
          {visibleTransfer.kind === 'complete' ? (
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
        </Card>
      ) : null}
    </>
  );
}
