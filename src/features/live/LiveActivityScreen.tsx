import React, { useCallback, useState } from 'react';

import type {
  ActivityKind,
  ActivityRecord,
  ActivityRecoveryRoute,
} from '../../domain/activity/model';
import type {
  ActivityId,
  PageCursor,
  SafeSupportCode,
} from '../../domain/shared/brand';
import type { NativeResult } from '../../domain/shared/result';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  KeyValue,
  ReadinessBanner,
  Screen,
  SettingRow,
  StatusRow,
} from '../../design-system/components/Primitives';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { formatLiveInstant } from '../../localization/formatLive';
import { safeReasonMessageKey } from '../../localization/reasonCopy';
import type { TranslationKey } from '../../localization/resources';
import type { LiveAppPort } from './LiveAppPort';
import {
  LiveError,
  LiveLoading,
  LiveRefreshProblem,
} from './LiveProjectionState';
import { useLiveProjection } from './useLiveProjection';

const activityTone = (record: ActivityRecord) => {
  switch (record.kind) {
    case 'delivered':
      return 'positive' as const;
    case 'delivery-failed':
    case 'submission-failed':
    case 'missed':
    case 'composer-failed':
      return 'critical' as const;
    case 'delivery-unknown':
    case 'submission-unknown':
    case 'partial-delivery':
    case 'coordination-blocked':
    case 'armed-suppressed':
    case 'composer-outcome-unknown':
      return 'warning' as const;
    default:
      return 'info' as const;
  }
};

const activityKeys: Record<ActivityKind, TranslationKey> = {
  planned: 'live.activity.kind.planned',
  'coordination-blocked': 'live.activity.kind.coordinationBlocked',
  'armed-suppressed': 'live.activity.kind.armedSuppressed',
  skipped: 'live.activity.kind.skipped',
  missed: 'live.activity.kind.missed',
  submitted: 'live.activity.kind.submitted',
  'sent-from-device': 'live.activity.kind.sentFromDevice',
  delivered: 'live.activity.kind.delivered',
  'delivery-failed': 'live.activity.kind.deliveryFailed',
  'partial-delivery': 'live.activity.kind.partialDelivery',
  'delivery-unknown': 'live.activity.kind.deliveryUnknown',
  'submission-failed': 'live.activity.kind.submissionFailed',
  'submission-unknown': 'live.activity.kind.submissionUnknown',
  paused: 'live.activity.kind.paused',
  'approval-invalidated': 'live.activity.kind.approvalInvalidated',
  sync: 'live.activity.kind.sync',
  transfer: 'live.activity.kind.transfer',
  'settings-changed': 'live.activity.kind.settingsChanged',
  'reminder-scheduled': 'live.activity.kind.reminderScheduled',
  'composer-opened': 'live.activity.kind.composerOpened',
  'composer-cancelled': 'live.activity.kind.composerCancelled',
  'composer-failed': 'live.activity.kind.composerFailed',
  'composer-outcome-unknown': 'live.activity.kind.composerOutcomeUnknown',
  'composer-reported-sent': 'live.activity.kind.composerReportedSent',
};

const activityRecoveryKeys: Record<ActivityRecoveryRoute, TranslationKey> = {
  attention: 'live.activity.recovery.attention',
  automation: 'live.activity.recovery.automation',
  people: 'live.activity.recovery.people',
  settings: 'live.activity.recovery.settings',
};

const activityDetailDisclosure = (
  kind: ActivityKind,
):
  | Readonly<{
      body: TranslationKey;
      title: TranslationKey;
      tone: 'info' | 'warning';
      includesIosVisibilityBoundary: boolean;
    }>
  | undefined => {
  switch (kind) {
    case 'reminder-scheduled':
      return {
        title: 'live.activity.detail.reminderTitle',
        body: 'live.activity.detail.reminderBody',
        tone: 'info',
        includesIosVisibilityBoundary: false,
      };
    case 'composer-opened':
      return {
        title: 'live.activity.detail.composerOpenedTitle',
        body: 'live.activity.detail.composerOpenedBody',
        tone: 'info',
        includesIosVisibilityBoundary: true,
      };
    case 'composer-cancelled':
      return {
        title: 'live.activity.detail.composerCancelledTitle',
        body: 'live.activity.detail.composerCancelledBody',
        tone: 'warning',
        includesIosVisibilityBoundary: true,
      };
    case 'composer-failed':
      return {
        title: 'live.activity.detail.composerFailedTitle',
        body: 'live.activity.detail.composerFailedBody',
        tone: 'warning',
        includesIosVisibilityBoundary: true,
      };
    case 'composer-reported-sent':
      return {
        title: 'live.activity.detail.composerReportedSentTitle',
        body: 'live.activity.detail.composerReportedSentBody',
        tone: 'warning',
        includesIosVisibilityBoundary: true,
      };
    case 'composer-outcome-unknown':
      return {
        title: 'live.activity.detail.composerUnknownTitle',
        body: 'live.activity.detail.composerUnknownBody',
        tone: 'warning',
        includesIosVisibilityBoundary: true,
      };
    default:
      return undefined;
  }
};

const ACTIVITY_SCAN_PAGE_LIMIT = 100;
const activityContractProblem = {
  kind: 'internal' as const,
  supportCode: 'NATIVE_CONTRACT_INVALID' as SafeSupportCode,
};

const loadActivityById = async (
  port: LiveAppPort,
  activityId: ActivityId,
): Promise<NativeResult<ActivityRecord | undefined>> => {
  let cursor: PageCursor | undefined;
  let firstRevision:
    | import('../../domain/shared/brand').NativeRevision
    | undefined;
  const seenCursors = new Set<PageCursor>();

  for (let page = 0; page < ACTIVITY_SCAN_PAGE_LIMIT; page += 1) {
    const result = await port.listActivity(
      cursor === undefined ? { pageSize: 50 } : { cursor, pageSize: 50 },
    );
    if (result.kind === 'error') return result;
    if (
      firstRevision !== undefined &&
      firstRevision !== result.envelope.revision
    ) {
      return {
        kind: 'error',
        problem: {
          kind: 'stale-revision',
          latestRevision: result.envelope.revision,
        },
      };
    }
    firstRevision = result.envelope.revision;
    const record = result.envelope.value.items.find(
      item => item.id === activityId,
    );
    if (record) {
      return {
        kind: 'ok',
        envelope: { ...result.envelope, value: record },
      };
    }
    const nextCursor = result.envelope.value.nextCursor;
    if (nextCursor === undefined) {
      return {
        kind: 'ok',
        envelope: { ...result.envelope, value: undefined },
      };
    }
    if (seenCursors.has(nextCursor)) {
      return { kind: 'error', problem: activityContractProblem };
    }
    seenCursors.add(nextCursor);
    cursor = nextCursor;
  }

  return { kind: 'error', problem: activityContractProblem };
};

export function LiveActivityScreen({
  onBack,
  onOpenDetail,
  onOpenRecovery,
  port,
}: {
  onBack: () => void;
  onOpenDetail: (record: ActivityRecord) => void;
  onOpenRecovery: (route: ActivityRecoveryRoute) => void;
  port: LiveAppPort;
}) {
  const { language, t } = useAppLocalization();
  const [cursorHistory, setCursorHistory] = useState<
    readonly (PageCursor | null)[]
  >([null]);
  const cursor = cursorHistory[cursorHistory.length - 1] ?? null;
  const loadActivity = useCallback(
    () =>
      port.listActivity(
        cursor === null ? { pageSize: 25 } : { cursor, pageSize: 25 },
      ),
    [cursor, port],
  );
  const activity = useLiveProjection(loadActivity, port, ['activity']);

  return (
    <Screen includeTopInset testID="live-activity-screen">
      <Button
        label={t('live.common.back')}
        onPress={onBack}
        variant="ghost"
        testID="live-activity-back"
      />
      <AppText variant="title" accessibilityRole="header">
        {t('live.activity.title')}
      </AppText>
      <AppText color="muted">{t('live.activity.body')}</AppText>

      {activity.state.kind === 'loading' ? (
        <LiveLoading label={t('live.activity.loading')} />
      ) : null}
      {activity.state.kind === 'error' ? (
        <LiveError
          title={t('live.activity.unavailable')}
          problem={activity.state.problem}
          onRetry={() => activity.reload()}
        />
      ) : null}
      {activity.state.kind === 'ready' ? (
        <>
          {activity.state.refreshProblem ? (
            <LiveRefreshProblem problem={activity.state.refreshProblem} />
          ) : null}
          {activity.state.result.envelope.value.items.length === 0 ? (
            <Card>
              <AppText>{t('live.activity.empty')}</AppText>
            </Card>
          ) : (
            activity.state.result.envelope.value.items.map(record => (
              <Card key={record.id}>
                <SettingRow
                  title={t(activityKeys[record.kind])}
                  detail={formatLiveInstant(record.occurredAt, language)}
                  onPress={() => onOpenDetail(record)}
                  testID={`live-activity-${record.id}`}
                  icon={record.recovery ? 'warning' : 'chevron'}
                />
                {record.reason ? (
                  <StatusRow
                    title={t(safeReasonMessageKey(record.reason))}
                    tone={activityTone(record)}
                  />
                ) : null}
                {record.recovery ? (
                  <Button
                    label={t(activityRecoveryKeys[record.recovery.route])}
                    onPress={() => onOpenRecovery(record.recovery!.route)}
                    variant="secondary"
                    testID={`live-activity-recovery-${record.id}`}
                  />
                ) : null}
              </Card>
            ))
          )}
          <Button
            label={
              activity.state.refreshing
                ? t('live.common.refreshing')
                : t('live.common.refresh')
            }
            disabled={activity.state.refreshing}
            onPress={() => activity.reload()}
            variant="secondary"
          />
          {cursorHistory.length > 1 ? (
            <Button
              label={t('live.activity.previousPage')}
              onPress={() => setCursorHistory(current => current.slice(0, -1))}
              variant="secondary"
            />
          ) : null}
          {activity.state.result.envelope.value.nextCursor ? (
            <Button
              label={t('live.activity.nextPage')}
              onPress={() =>
                setCursorHistory(current => [
                  ...current,
                  activity.state.kind === 'ready'
                    ? activity.state.result.envelope.value.nextCursor ?? null
                    : null,
                ])
              }
            />
          ) : null}
        </>
      ) : null}
    </Screen>
  );
}

export function LiveActivityDetailScreen({
  activityId,
  onBack,
  onOpenRecovery,
  port,
}: {
  activityId: ActivityId;
  onBack: () => void;
  onOpenRecovery: (route: ActivityRecoveryRoute) => void;
  port: LiveAppPort;
}) {
  const { language, t } = useAppLocalization();
  const loadDetail = useCallback(
    () => loadActivityById(port, activityId),
    [activityId, port],
  );
  const detail = useLiveProjection(loadDetail, port, ['activity']);
  const record =
    detail.state.kind === 'ready'
      ? detail.state.result.envelope.value
      : undefined;
  const disclosure = record ? activityDetailDisclosure(record.kind) : undefined;
  return (
    <Screen includeTopInset testID="live-activity-detail-screen">
      <Button
        label={t('live.common.back')}
        onPress={onBack}
        variant="ghost"
        testID="live-activity-detail-back"
      />
      <AppText variant="title" accessibilityRole="header">
        {t('live.activity.detailTitle')}
      </AppText>
      <AppText color="muted">{t('live.activity.detailBody')}</AppText>
      {detail.state.kind === 'loading' ? (
        <LiveLoading label={t('live.activity.loading')} />
      ) : null}
      {detail.state.kind === 'error' ? (
        <LiveError
          title={t('live.activity.unavailable')}
          problem={detail.state.problem}
          onRetry={() => detail.reload()}
        />
      ) : null}
      {detail.state.kind === 'ready' && detail.state.refreshProblem ? (
        <LiveRefreshProblem problem={detail.state.refreshProblem} />
      ) : null}
      {detail.state.kind === 'ready' && record === undefined ? (
        <Card>
          <AppText>{t('live.activity.detailMissing')}</AppText>
          <Button
            label={t('live.common.refresh')}
            onPress={() => detail.reload()}
            variant="secondary"
          />
        </Card>
      ) : null}
      {record ? (
        <>
          <Card>
            <StatusRow
              title={t(activityKeys[record.kind])}
              tone={activityTone(record)}
            />
            <KeyValue
              label={t('live.activity.time')}
              value={formatLiveInstant(record.occurredAt, language)}
            />
            {record.reason ? (
              <>
                <StatusRow
                  title={t(safeReasonMessageKey(record.reason))}
                  tone={activityTone(record)}
                />
                <KeyValue
                  label={t('live.activity.reasonCode')}
                  value={record.reason}
                />
              </>
            ) : null}
          </Card>
          {disclosure ? (
            <ReadinessBanner
              title={t(disclosure.title)}
              detail={t(disclosure.body)}
              tone={disclosure.tone}
              testID="live-activity-detail-disclosure"
            />
          ) : null}
          {disclosure?.includesIosVisibilityBoundary ? (
            <ReadinessBanner
              title={t('live.activity.detail.iosVisibilityTitle')}
              detail={t('live.activity.detail.iosVisibilityBody')}
              tone="info"
              testID="live-activity-detail-ios-visibility"
            />
          ) : null}
          {record.recovery ? (
            <Button
              label={t(activityRecoveryKeys[record.recovery.route])}
              onPress={() => onOpenRecovery(record.recovery!.route)}
              testID="live-activity-detail-recovery"
            />
          ) : null}
        </>
      ) : null}
    </Screen>
  );
}
