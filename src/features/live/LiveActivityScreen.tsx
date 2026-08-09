import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, View } from 'react-native';

import type {
  ActivityKind,
  ActivityPage,
  ActivityRecord,
  ActivityRecoveryRoute,
} from '../../domain/activity/model';
import type {
  ActivityId,
  NativeRevision,
  PageCursor,
  SafeSupportCode,
} from '../../domain/shared/brand';
import type { PlatformCapability } from '../../domain/shared/platform';
import type { NativeProblem, NativeResult } from '../../domain/shared/result';
import type { PrivacyActionReview } from '../../domain/privacy/model';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  InlineReviewCard,
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
  LiveActionFeedback,
  LiveError,
  LiveLoading,
  LiveRefreshProblem,
} from './LiveProjectionState';
import {
  nativeBridgeProblem,
  nativeContractProblem,
  staleRevisionProblem,
} from './nativeProblem';
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

const invalidClearActivityReviewProblem: NativeProblem = {
  kind: 'internal',
  supportCode: 'INVALID_CLEAR_ACTIVITY_REVIEW' as SafeSupportCode,
};

const clearActivityTruthProblem: NativeProblem = {
  kind: 'internal',
  supportCode: 'CLEAR_ACTIVITY_TRUTH_UNAVAILABLE' as SafeSupportCode,
};

const requiredClearActivityConsequences = [
  'privacy.consequence.activity-hidden',
  'privacy.consequence.safety-retained',
] as const;

type ClearActivityReviewState = Readonly<{
  review: PrivacyActionReview;
  revision: NativeRevision;
  sourceRevision: NativeRevision;
}>;

type ClearActivityPhase = 'idle' | 'preparing' | 'review' | 'confirming';

type ActivityTruth = Readonly<{
  usable: boolean;
  revision?: NativeRevision | undefined;
}>;

const loadFirstActivityPage = async (
  port: LiveAppPort,
): Promise<NativeResult<ActivityPage>> => {
  try {
    return await port.listActivity({ pageSize: 25 });
  } catch {
    return { kind: 'error', problem: nativeBridgeProblem };
  }
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
      return { kind: 'error', problem: nativeContractProblem };
    }
    seenCursors.add(nextCursor);
    cursor = nextCursor;
  }

  return { kind: 'error', problem: nativeContractProblem };
};

export function LiveActivityScreen({
  capability,
  onBack,
  onOpenDetail,
  port,
}: {
  capability: PlatformCapability;
  onBack: () => void;
  onOpenDetail: (record: ActivityRecord) => void;
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
  const loadInventory = useCallback(() => port.getInventory(), [port]);
  const inventory = useLiveProjection(loadInventory, port, ['privacy']);
  const [clearReview, setClearReview] = useState<ClearActivityReviewState>();
  const [clearPending, setClearPending] = useState(false);
  const [clearProblem, setClearProblem] = useState<NativeProblem>();
  const [clearMessage, setClearMessage] = useState<string>();
  const [acceptedClearRevision, setAcceptedClearRevision] =
    useState<NativeRevision>();
  const mountedRef = useRef(true);
  const clearGenerationRef = useRef(0);
  const clearRequestPendingRef = useRef(false);
  const clearSourceRevisionRef = useRef<NativeRevision | undefined>(undefined);
  const clearPhaseRef = useRef<ClearActivityPhase>('idle');
  const clearInvalidationRevisionsRef = useRef<Set<NativeRevision>>(new Set());
  const clearReviewRef = useRef<ClearActivityReviewState | undefined>(
    undefined,
  );
  const activityTruthRef = useRef<ActivityTruth>({ usable: false });
  const activityReloadRef = useRef(activity.reload);
  const inventoryReloadRef = useRef(inventory.reload);
  const cursorRef = useRef(cursor);

  const activityUsable =
    activity.state.kind === 'ready' &&
    !activity.state.refreshing &&
    !activity.state.refreshProblem;
  const inventoryUsable =
    inventory.state.kind === 'ready' &&
    !inventory.state.refreshing &&
    !inventory.state.refreshProblem;
  const activityRevision =
    activity.state.kind === 'ready'
      ? activity.state.result.envelope.revision
      : undefined;
  const inventoryRevision =
    inventory.state.kind === 'ready'
      ? inventory.state.result.envelope.revision
      : undefined;
  const joinedRevision =
    activityUsable && inventoryUsable && activityRevision === inventoryRevision
      ? activityRevision
      : undefined;
  const activityTruth: ActivityTruth = {
    usable: joinedRevision !== undefined,
    ...(joinedRevision === undefined ? {} : { revision: joinedRevision }),
  };
  activityTruthRef.current = activityTruth;
  activityReloadRef.current = activity.reload;
  inventoryReloadRef.current = inventory.reload;
  cursorRef.current = cursor;
  clearReviewRef.current = clearReview;

  const invalidateClearWork = useCallback(() => {
    clearGenerationRef.current += 1;
    clearRequestPendingRef.current = false;
    clearSourceRevisionRef.current = undefined;
    clearPhaseRef.current = 'idle';
    clearInvalidationRevisionsRef.current.clear();
    clearReviewRef.current = undefined;
    if (!mountedRef.current) return;
    setClearReview(undefined);
    setClearPending(false);
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    const invalidationRevisions = clearInvalidationRevisionsRef.current;
    return () => {
      mountedRef.current = false;
      clearGenerationRef.current += 1;
      clearRequestPendingRef.current = false;
      clearSourceRevisionRef.current = undefined;
      clearPhaseRef.current = 'idle';
      invalidationRevisions.clear();
    };
  }, []);

  useEffect(
    () =>
      port.subscribeInvalidations(event => {
        if (
          !event.areas.some(area => area === 'activity' || area === 'privacy')
        ) {
          return;
        }
        if (
          clearPhaseRef.current === 'confirming' &&
          clearRequestPendingRef.current
        ) {
          clearInvalidationRevisionsRef.current.add(event.revision);
          return;
        }
        if (clearPhaseRef.current !== 'idle') invalidateClearWork();
      }),
    [invalidateClearWork, port],
  );

  useEffect(() => {
    const subscription = AppState.addEventListener('change', nextState => {
      if (nextState === 'active') invalidateClearWork();
    });
    return () => subscription.remove();
  }, [invalidateClearWork]);

  useEffect(() => {
    if (clearPhaseRef.current !== 'review') return;
    if (
      joinedRevision === undefined ||
      joinedRevision !== clearSourceRevisionRef.current
    ) {
      invalidateClearWork();
    }
  }, [invalidateClearWork, joinedRevision]);

  useEffect(() => {
    if (
      acceptedClearRevision === undefined ||
      joinedRevision !== acceptedClearRevision ||
      activity.state.kind !== 'ready' ||
      inventory.state.kind !== 'ready' ||
      activity.state.result.envelope.value.items.length !== 0 ||
      activity.state.result.envelope.value.nextCursor !== undefined ||
      inventory.state.result.envelope.value.activityCount !== 0
    ) {
      return;
    }
    setAcceptedClearRevision(undefined);
    setClearProblem(undefined);
    setClearMessage(t('live.privacy.operationComplete'));
  }, [
    acceptedClearRevision,
    activity.state,
    inventory.state,
    joinedRevision,
    t,
  ]);

  const isClearRequestCurrent = (
    generation: number,
    sourceRevision: NativeRevision,
    phase: 'preparing' | 'confirming',
  ) =>
    mountedRef.current &&
    generation === clearGenerationRef.current &&
    clearRequestPendingRef.current &&
    clearSourceRevisionRef.current === sourceRevision &&
    clearPhaseRef.current === phase;

  const beginClearRequest = (
    sourceRevision: NativeRevision,
    phase: 'preparing' | 'confirming',
  ): number | undefined => {
    const truth = activityTruthRef.current;
    if (
      clearRequestPendingRef.current ||
      !truth.usable ||
      truth.revision !== sourceRevision
    ) {
      return undefined;
    }
    const generation = clearGenerationRef.current + 1;
    clearGenerationRef.current = generation;
    clearRequestPendingRef.current = true;
    clearSourceRevisionRef.current = sourceRevision;
    clearPhaseRef.current = phase;
    clearInvalidationRevisionsRef.current.clear();
    return generation;
  };

  const reloadClearTruth = async ({
    expectCleared,
    expectedRevision,
    generation,
    phase,
    sourceRevision,
  }: {
    expectCleared: boolean;
    expectedRevision: NativeRevision;
    generation: number;
    phase: 'preparing' | 'confirming';
    sourceRevision: NativeRevision;
  }) => {
    if (!isClearRequestCurrent(generation, sourceRevision, phase)) {
      return { kind: 'retired' as const };
    }
    const activityRequest = expectCleared
      ? loadFirstActivityPage(port)
      : activityReloadRef.current();
    const [refreshedActivity, refreshedInventory] = await Promise.all([
      activityRequest,
      inventoryReloadRef.current(),
    ]);
    if (!isClearRequestCurrent(generation, sourceRevision, phase)) {
      return { kind: 'retired' as const };
    }
    if (refreshedActivity.kind === 'error') {
      return {
        kind: 'error' as const,
        problem: refreshedActivity.problem,
      };
    }
    if (refreshedInventory.kind === 'error') {
      return {
        kind: 'error' as const,
        problem: refreshedInventory.problem,
      };
    }
    const conflictingInvalidation = [
      ...clearInvalidationRevisionsRef.current,
    ].find(revision => revision !== expectedRevision);
    if (conflictingInvalidation !== undefined) {
      return {
        kind: 'error' as const,
        problem: staleRevisionProblem(conflictingInvalidation),
      };
    }
    if (
      refreshedActivity.envelope.revision !== expectedRevision ||
      refreshedInventory.envelope.revision !== expectedRevision
    ) {
      return {
        kind: 'error' as const,
        problem: staleRevisionProblem(
          refreshedInventory.envelope.revision !== expectedRevision
            ? refreshedInventory.envelope.revision
            : refreshedActivity.envelope.revision,
        ),
      };
    }
    if (
      expectCleared &&
      (refreshedActivity.envelope.value.items.length !== 0 ||
        refreshedActivity.envelope.value.nextCursor !== undefined ||
        refreshedInventory.envelope.value.activityCount !== 0)
    ) {
      return {
        kind: 'error' as const,
        problem: clearActivityTruthProblem,
      };
    }
    clearSourceRevisionRef.current = expectedRevision;
    clearInvalidationRevisionsRef.current.clear();
    if (!expectCleared) {
      activityTruthRef.current = {
        usable: true,
        revision: expectedRevision,
      };
    }
    return { kind: 'ok' as const, revision: expectedRevision };
  };

  const failClearAction = async (
    nextProblem: NativeProblem,
    refreshTruth = false,
  ) => {
    invalidateClearWork();
    if (!mountedRef.current) return;
    setClearProblem(nextProblem);
    setClearMessage(undefined);
    if (refreshTruth || nextProblem.kind === 'stale-revision') {
      await Promise.all([
        activityReloadRef.current(),
        inventoryReloadRef.current(),
      ]);
    }
  };

  const prepareClearActivity = async () => {
    const truth = activityTruthRef.current;
    if (!truth.usable || truth.revision === undefined) return;
    const sourceRevision = truth.revision;
    const generation = beginClearRequest(sourceRevision, 'preparing');
    if (generation === undefined) return;
    setClearPending(true);
    setClearProblem(undefined);
    setClearMessage(undefined);
    setClearReview(undefined);
    clearReviewRef.current = undefined;

    let result: Awaited<ReturnType<LiveAppPort['prepareAction']>>;
    try {
      result = await port.prepareAction({
        kind: 'clear-activity',
        expectedRevision: sourceRevision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (!isClearRequestCurrent(generation, sourceRevision, 'preparing')) return;
    if (result.kind === 'error') {
      await failClearAction(result.problem);
      return;
    }
    const consequenceKeys = [...result.envelope.value.consequenceKeys].sort();
    const requiredConsequences = [...requiredClearActivityConsequences].sort();
    const validReview =
      result.envelope.value.kind === 'clear-activity' &&
      result.envelope.value.externalSmsCopiesNotErased === true &&
      result.envelope.value.remoteConnectionRequired === false &&
      consequenceKeys.length === requiredConsequences.length &&
      new Set(consequenceKeys).size === consequenceKeys.length &&
      consequenceKeys.every(
        (value, index) => value === requiredConsequences[index],
      );
    if (!validReview) {
      await failClearAction(invalidClearActivityReviewProblem);
      return;
    }
    const refreshed = await reloadClearTruth({
      expectCleared: false,
      expectedRevision: result.envelope.revision,
      generation,
      phase: 'preparing',
      sourceRevision,
    });
    if (refreshed.kind === 'retired') return;
    if (refreshed.kind === 'error') {
      await failClearAction(refreshed.problem);
      return;
    }
    if (!isClearRequestCurrent(generation, refreshed.revision, 'preparing')) {
      return;
    }
    const nextReview: ClearActivityReviewState = {
      review: result.envelope.value,
      revision: result.envelope.revision,
      sourceRevision: refreshed.revision,
    };
    clearReviewRef.current = nextReview;
    clearPhaseRef.current = 'review';
    clearRequestPendingRef.current = false;
    setClearReview(nextReview);
    setClearPending(false);
  };

  const confirmClearActivity = async () => {
    if (clearRequestPendingRef.current) return;
    const currentReview = clearReviewRef.current;
    const truth = activityTruthRef.current;
    if (
      !currentReview ||
      !truth.usable ||
      truth.revision !== currentReview.sourceRevision
    ) {
      invalidateClearWork();
      return;
    }
    const generation = beginClearRequest(
      currentReview.sourceRevision,
      'confirming',
    );
    if (generation === undefined) return;
    clearReviewRef.current = undefined;
    setClearReview(undefined);
    setClearPending(true);
    setClearProblem(undefined);
    setClearMessage(undefined);

    let result: Awaited<ReturnType<LiveAppPort['confirmAction']>>;
    try {
      result = await port.confirmAction({
        handle: currentReview.review.handle,
        expectedRevision: currentReview.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (
      !isClearRequestCurrent(
        generation,
        currentReview.sourceRevision,
        'confirming',
      )
    ) {
      return;
    }
    if (result.kind === 'error') {
      await failClearAction(result.problem);
      return;
    }
    if (result.envelope.value.action !== 'clear-activity') {
      await failClearAction(invalidClearActivityReviewProblem, true);
      return;
    }
    const operationComplete = result.envelope.value.kind === 'complete';
    const refreshed = await reloadClearTruth({
      expectCleared: operationComplete,
      expectedRevision: result.envelope.revision,
      generation,
      phase: 'confirming',
      sourceRevision: currentReview.sourceRevision,
    });
    if (refreshed.kind === 'retired') return;
    if (refreshed.kind === 'error') {
      await failClearAction(refreshed.problem);
      return;
    }
    if (!isClearRequestCurrent(generation, refreshed.revision, 'confirming')) {
      return;
    }
    const lateConflictingInvalidation = [
      ...clearInvalidationRevisionsRef.current,
    ].find(revision => revision !== result.envelope.revision);
    if (lateConflictingInvalidation !== undefined) {
      await failClearAction(staleRevisionProblem(lateConflictingInvalidation));
      return;
    }

    invalidateClearWork();
    if (!mountedRef.current) return;
    if (operationComplete) {
      setAcceptedClearRevision(result.envelope.revision);
      setCursorHistory([null]);
    } else {
      setAcceptedClearRevision(undefined);
    }
    setClearProblem(undefined);
    setClearMessage(
      result.envelope.value.kind === 'complete'
        ? t('live.privacy.operationComplete')
        : result.envelope.value.kind === 'failed'
        ? t('live.privacy.operationFailed')
        : t('live.privacy.operationPending'),
    );
    if (operationComplete && cursorRef.current === null) {
      activityReloadRef.current().catch(() => undefined);
    }
  };

  const cancelClearReview = () => {
    invalidateClearWork();
    setClearProblem(undefined);
  };

  const currentClearReview =
    clearReview &&
    joinedRevision !== undefined &&
    clearReview.sourceRevision === joinedRevision
      ? clearReview
      : undefined;
  const currentItems =
    acceptedClearRevision === undefined && activity.state.kind === 'ready'
      ? activity.state.result.envelope.value.items
      : [];
  const clearActionAvailable =
    acceptedClearRevision === undefined &&
    joinedRevision !== undefined &&
    inventory.state.kind === 'ready' &&
    inventory.state.result.envelope.value.activityCount > 0;

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
      <LiveActionFeedback problem={clearProblem} message={clearMessage} />

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
          {currentItems.length === 0 ? (
            <Card>
              <AppText>{t('live.activity.empty')}</AppText>
            </Card>
          ) : (
            <View testID="live-activity-list">
              {currentItems.map(record => {
                const occurredAt = formatLiveInstant(
                  record.occurredAt,
                  language,
                );
                const detail = record.reason
                  ? `${t(safeReasonMessageKey(record.reason))} · ${occurredAt}`
                  : occurredAt;
                return (
                  <SettingRow
                    key={record.id}
                    title={t(activityKeys[record.kind])}
                    detail={detail}
                    onPress={() => onOpenDetail(record)}
                    testID={`live-activity-${record.id}`}
                    icon={
                      activityTone(record) === 'critical' ||
                      activityTone(record) === 'warning'
                        ? 'warning'
                        : 'chevron'
                    }
                  />
                );
              })}
            </View>
          )}
          {acceptedClearRevision === undefined && cursorHistory.length > 1 ? (
            <Button
              label={t('live.activity.previousPage')}
              disabled={
                activity.state.refreshing ||
                clearPending ||
                currentClearReview !== undefined
              }
              onPress={() => {
                invalidateClearWork();
                setCursorHistory(current => current.slice(0, -1));
              }}
              variant="secondary"
            />
          ) : null}
          {acceptedClearRevision === undefined &&
          activity.state.result.envelope.value.nextCursor ? (
            <Button
              label={t('live.activity.nextPage')}
              disabled={
                activity.state.refreshing ||
                clearPending ||
                currentClearReview !== undefined
              }
              onPress={() => {
                invalidateClearWork();
                setCursorHistory(current => [
                  ...current,
                  activity.state.kind === 'ready'
                    ? activity.state.result.envelope.value.nextCursor ?? null
                    : null,
                ]);
              }}
            />
          ) : null}
          {inventory.state.kind === 'error' ? (
            <LiveError
              title={t('live.settings.privacyUnavailable')}
              problem={inventory.state.problem}
              onRetry={() => inventory.reload()}
              retryTestID="live-activity-clear-retry"
            />
          ) : null}
          {inventory.state.kind === 'ready' &&
          inventory.state.refreshProblem ? (
            <LiveRefreshProblem problem={inventory.state.refreshProblem} />
          ) : null}
          {clearActionAvailable && !currentClearReview ? (
            <Card testID="live-activity-clear-card">
              <AppText variant="heading">
                {t('live.privacy.clearActivity')}
              </AppText>
              <AppText color="muted">
                {t('live.settings.activityRetention')}
              </AppText>
              <Button
                label={
                  clearPending
                    ? t('live.privacy.preparing')
                    : t('live.privacy.prepare')
                }
                disabled={clearPending}
                onPress={() => prepareClearActivity().catch(() => undefined)}
                variant="secondary"
                testID="live-activity-clear"
              />
            </Card>
          ) : null}
          {currentClearReview ? (
            <InlineReviewCard
              reviewKey={currentClearReview.review.handle}
              testID="live-activity-clear-review"
              title={t('live.privacy.reviewTitle')}
            >
              <StatusRow
                title={t('live.privacy.consequence.activityHidden')}
                tone="warning"
              />
              <ReadinessBanner
                title={t('live.settings.retention')}
                detail={t(
                  capability.platform === 'android'
                    ? 'live.settings.androidSafetyRetention'
                    : 'live.settings.iosSafetyRetention',
                )}
                tone="warning"
                testID="live-activity-clear-retention"
              />
              {currentClearReview.review.preissuedPermitMayFinish ? (
                <ReadinessBanner
                  title={t('live.privacy.preissued')}
                  detail={t('live.privacy.external')}
                  tone="critical"
                />
              ) : (
                <AppText color="muted">{t('live.privacy.external')}</AppText>
              )}
              <Button
                label={
                  clearPending
                    ? t('live.privacy.confirming')
                    : t('live.privacy.confirm')
                }
                disabled={clearPending}
                onPress={() => confirmClearActivity().catch(() => undefined)}
                variant="danger"
                testID="live-activity-clear-confirm"
              />
              <Button
                label={t('live.common.cancel')}
                disabled={clearPending}
                onPress={cancelClearReview}
                variant="secondary"
                testID="live-activity-clear-cancel"
              />
            </InlineReviewCard>
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
  const [supportExpanded, setSupportExpanded] = useState(false);
  const loadDetail = useCallback(
    () => loadActivityById(port, activityId),
    [activityId, port],
  );
  const detail = useLiveProjection(loadDetail, port, ['activity']);
  const record =
    detail.state.kind === 'ready' &&
    (detail.state.result.envelope.value === undefined ||
      detail.state.result.envelope.value.id === activityId)
      ? detail.state.result.envelope.value
      : undefined;
  const disclosure = record ? activityDetailDisclosure(record.kind) : undefined;
  const detailUsable =
    detail.state.kind === 'ready' &&
    !detail.state.refreshing &&
    !detail.state.refreshProblem;
  useEffect(() => setSupportExpanded(false), [activityId]);
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
              <StatusRow
                title={t(safeReasonMessageKey(record.reason))}
                tone={activityTone(record)}
              />
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
          {record.reason ? (
            <>
              <Button
                label={t(
                  supportExpanded
                    ? 'live.attention.hideSupportDetails'
                    : 'live.attention.showSupportDetails',
                )}
                onPress={() => setSupportExpanded(expanded => !expanded)}
                variant="secondary"
                testID="live-activity-detail-support-toggle"
              />
              {supportExpanded ? (
                <Card testID="live-activity-detail-support-details">
                  <AppText color="muted">
                    {t('live.attention.supportDetailsBody')}
                  </AppText>
                  <KeyValue
                    label={t('live.activity.reasonCode')}
                    value={record.reason}
                  />
                </Card>
              ) : null}
            </>
          ) : null}
          {record.recovery && detailUsable ? (
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
