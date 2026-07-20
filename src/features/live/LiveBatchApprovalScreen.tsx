import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, StyleSheet, View } from 'react-native';

import type { ApprovalBatchReview } from '../../domain/approvals/model';
import type { ContactId, NativeRevision } from '../../domain/shared/brand';
import type { NativeProblem } from '../../domain/shared/result';
import type { PlatformCapability } from '../../domain/shared/platform';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  KeyValue,
  ReadinessBanner,
  Screen,
  StatusRow,
} from '../../design-system/components/Primitives';
import { spacing } from '../../design-system/tokens/theme';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import type { LiveAppPort } from './LiveAppPort';
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
import {
  needsBatchApproval,
  PEOPLE_REVIEW_BATCH_SIZE,
  scanPeoplePages,
} from './peoplePagination';
import { useLiveProjection } from './useLiveProjection';

type ReviewState = Readonly<{
  batchIds: readonly ContactId[];
  review: ApprovalBatchReview;
  revision: NativeRevision;
  sourceRevision: NativeRevision;
  requestedIds: readonly ContactId[];
  remainingIds: readonly ContactId[];
  processedCount: number;
  blockedCount: number;
  totalCount: number;
}>;

type IncompleteBatch = Readonly<{
  processedCount: number;
  totalCount: number;
}>;

export function LiveBatchApprovalScreen({
  capability,
  onBack,
  port,
}: {
  capability: PlatformCapability;
  onBack: () => void;
  port: LiveAppPort;
}) {
  const { t } = useAppLocalization();
  const loadCandidates = useCallback(
    () =>
      scanPeoplePages(port, { filter: 'needs-attention' }, needsBatchApproval),
    [port],
  );
  const candidates = useLiveProjection(loadCandidates, port, [
    'contacts',
    'messages',
    'automation',
  ]);
  const [pending, setPending] = useState(false);
  const [problem, setProblem] = useState<NativeProblem>();
  const [message, setMessage] = useState<string>();
  const [review, setReview] = useState<ReviewState>();
  const [incompleteBatch, setIncompleteBatch] = useState<IncompleteBatch>();
  const mountedRef = useRef(true);
  const protectedWorkGenerationRef = useRef(0);
  const protectedRequestPendingRef = useRef(false);
  const protectedSourceRevisionRef = useRef<NativeRevision | undefined>(
    undefined,
  );
  const protectedInvalidationRevisionsRef = useRef<Set<NativeRevision>>(
    new Set(),
  );
  const protectedRefreshRevisionRef = useRef<NativeRevision | undefined>(
    undefined,
  );
  const protectedSettlingRevisionRef = useRef<NativeRevision | undefined>(
    undefined,
  );
  const candidateTruthRef = useRef<
    Readonly<{
      usable: boolean;
      revision?: NativeRevision | undefined;
    }>
  >({ usable: false });
  const reviewRef = useRef<ReviewState | undefined>(undefined);

  const candidateUsable =
    candidates.state.kind === 'ready' &&
    !candidates.state.refreshing &&
    !candidates.state.refreshProblem;
  const candidateRevision =
    candidates.state.kind === 'ready'
      ? candidates.state.result.envelope.revision
      : undefined;
  candidateTruthRef.current = {
    usable: candidateUsable,
    revision: candidateRevision,
  };
  reviewRef.current = review;

  const invalidateProtectedWork = useCallback(() => {
    protectedWorkGenerationRef.current += 1;
    protectedRequestPendingRef.current = false;
    protectedSourceRevisionRef.current = undefined;
    protectedInvalidationRevisionsRef.current.clear();
    protectedRefreshRevisionRef.current = undefined;
    protectedSettlingRevisionRef.current = undefined;
    reviewRef.current = undefined;
    if (!mountedRef.current) return;
    setReview(undefined);
    setPending(false);
  }, []);

  useEffect(() => {
    const protectedInvalidationRevisions =
      protectedInvalidationRevisionsRef.current;
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      protectedWorkGenerationRef.current += 1;
      protectedRequestPendingRef.current = false;
      protectedSourceRevisionRef.current = undefined;
      protectedInvalidationRevisions.clear();
      protectedRefreshRevisionRef.current = undefined;
      protectedSettlingRevisionRef.current = undefined;
    };
  }, []);

  useEffect(
    () =>
      port.subscribeInvalidations(event => {
        if (
          event.areas.some(
            area =>
              area === 'contacts' ||
              area === 'messages' ||
              area === 'automation',
          )
        ) {
          if (protectedRequestPendingRef.current) {
            protectedInvalidationRevisionsRef.current.add(event.revision);
            protectedRefreshRevisionRef.current = event.revision;
            return;
          }
          if (
            protectedSourceRevisionRef.current === event.revision &&
            candidateTruthRef.current.revision === event.revision
          ) {
            // Native may publish the successful mutation before its promise
            // resolves. Once that exact revision has been re-read, a late
            // duplicate event is only a request to verify the same truth.
            protectedRefreshRevisionRef.current = event.revision;
            return;
          }
          invalidateProtectedWork();
        }
      }),
    [invalidateProtectedWork, port],
  );

  useEffect(() => {
    const subscription = AppState.addEventListener('change', nextState => {
      if (nextState === 'active') invalidateProtectedWork();
    });
    return () => subscription.remove();
  }, [invalidateProtectedWork]);

  useEffect(() => {
    const protectedSourceRevision = protectedSourceRevisionRef.current;
    if (protectedSourceRevision === undefined) return;
    if (protectedRequestPendingRef.current) return;
    if (candidateUsable && candidateRevision === protectedSourceRevision) {
      protectedRefreshRevisionRef.current = undefined;
      protectedSettlingRevisionRef.current = undefined;
      return;
    }
    const matchingRefreshInFlight =
      candidates.state.kind === 'ready' &&
      candidates.state.refreshing &&
      !candidates.state.refreshProblem &&
      candidateRevision === protectedSourceRevision &&
      protectedRefreshRevisionRef.current === protectedSourceRevision;
    const verifiedReloadSettling =
      protectedSettlingRevisionRef.current === protectedSourceRevision &&
      candidates.state.kind === 'ready' &&
      !candidates.state.refreshProblem;
    if (!matchingRefreshInFlight && !verifiedReloadSettling) {
      invalidateProtectedWork();
    }
  }, [
    candidateRevision,
    candidateUsable,
    candidates.state,
    invalidateProtectedWork,
  ]);

  const beginProtectedWork = (sourceRevision: NativeRevision) => {
    const generation = protectedWorkGenerationRef.current + 1;
    protectedWorkGenerationRef.current = generation;
    protectedRequestPendingRef.current = true;
    protectedSourceRevisionRef.current = sourceRevision;
    protectedInvalidationRevisionsRef.current.clear();
    protectedRefreshRevisionRef.current = undefined;
    protectedSettlingRevisionRef.current = undefined;
    return generation;
  };

  const isProtectedWorkCurrent = (
    generation: number,
    sourceRevision: NativeRevision,
  ) => {
    const candidateTruth = candidateTruthRef.current;
    return (
      mountedRef.current &&
      generation === protectedWorkGenerationRef.current &&
      protectedSourceRevisionRef.current === sourceRevision &&
      candidateTruth.usable &&
      candidateTruth.revision === sourceRevision
    );
  };

  const isProtectedRequestCurrent = (
    generation: number,
    sourceRevision: NativeRevision,
  ) =>
    mountedRef.current &&
    generation === protectedWorkGenerationRef.current &&
    protectedRequestPendingRef.current &&
    protectedSourceRevisionRef.current === sourceRevision;

  const failBatch = async (
    nextProblem: NativeProblem,
    processedCount: number,
    totalCount: number,
  ) => {
    invalidateProtectedWork();
    if (!mountedRef.current) return;
    setProblem(nextProblem);
    setIncompleteBatch(
      processedCount > 0 ? { processedCount, totalCount } : undefined,
    );
    if (nextProblem.kind === 'stale-revision' || processedCount > 0) {
      await candidates.reload();
    }
  };

  const reloadProtectedCandidates = async (
    generation: number,
    sourceRevision: NativeRevision,
    expectedRevision: NativeRevision,
  ) => {
    if (!isProtectedRequestCurrent(generation, sourceRevision)) {
      return { kind: 'retired' as const };
    }
    const refreshed = await candidates.reload();
    if (!isProtectedRequestCurrent(generation, sourceRevision)) {
      return { kind: 'retired' as const };
    }
    if (refreshed.kind === 'error') {
      return { kind: 'error' as const, problem: refreshed.problem };
    }
    const conflictingInvalidation = [
      ...protectedInvalidationRevisionsRef.current,
    ].find(revision => revision !== expectedRevision);
    if (conflictingInvalidation !== undefined) {
      return {
        kind: 'error' as const,
        problem: {
          kind: 'stale-revision' as const,
          latestRevision: conflictingInvalidation,
        },
      };
    }
    if (refreshed.envelope.revision !== expectedRevision) {
      return {
        kind: 'error' as const,
        problem: {
          kind: 'stale-revision' as const,
          latestRevision: refreshed.envelope.revision,
        },
      };
    }

    // This exact re-read is the new source of truth for the next protected
    // operation. Rebase both the CAS source and the render-time truth ref before
    // continuing; any later, different revision still retires the whole batch.
    protectedSourceRevisionRef.current = expectedRevision;
    protectedInvalidationRevisionsRef.current.clear();
    protectedRefreshRevisionRef.current = undefined;
    protectedSettlingRevisionRef.current = expectedRevision;
    candidateTruthRef.current = {
      usable: true,
      revision: expectedRevision,
    };
    return {
      kind: 'ok' as const,
      contactIds: refreshed.envelope.value.contactIds,
      revision: expectedRevision,
    };
  };

  const verifyCompletedWork = (
    blockedCount: number,
    processedCount: number,
  ) => {
    invalidateProtectedWork();
    if (!mountedRef.current) return;
    setIncompleteBatch(undefined);
    setMessage(
      blockedCount > 0
        ? t('live.guidedSetup.approvalSavedWithBlocked', {
            approved: processedCount - blockedCount,
            blocked: blockedCount,
          })
        : t('live.guidedSetup.approvalSaved'),
    );
  };

  const prepareNext = async ({
    blockedCount,
    batchIds,
    expectedRevision,
    generation,
    processedCount,
    remainingIds,
    sourceRevision,
    totalCount,
  }: {
    blockedCount: number;
    batchIds: readonly ContactId[];
    expectedRevision: NativeRevision;
    generation: number;
    processedCount: number;
    remainingIds: readonly ContactId[];
    sourceRevision: NativeRevision;
    totalCount: number;
  }): Promise<void> => {
    if (!isProtectedWorkCurrent(generation, sourceRevision)) return;
    const requestedIds = remainingIds.slice(0, PEOPLE_REVIEW_BATCH_SIZE);
    const followingIds = remainingIds.slice(PEOPLE_REVIEW_BATCH_SIZE);
    if (requestedIds.length === 0) {
      verifyCompletedWork(blockedCount, processedCount);
      return;
    }
    let result: Awaited<ReturnType<LiveAppPort['prepareApprovals']>>;
    try {
      result = await port.prepareApprovals({
        contactIds: requestedIds,
        expectedRevision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (!isProtectedRequestCurrent(generation, sourceRevision)) return;
    if (result.kind === 'error') {
      await failBatch(result.problem, processedCount, totalCount);
      return;
    }
    const requestedIdSet = new Set(requestedIds);
    const returnedIds = result.envelope.value.items.map(item => item.contactId);
    const valid =
      result.envelope.value.explicitConfirmationRequired === true &&
      result.envelope.value.readyCount === returnedIds.length &&
      result.envelope.value.readyCount + result.envelope.value.blockedCount ===
        requestedIds.length &&
      new Set(returnedIds).size === returnedIds.length &&
      returnedIds.every(id => requestedIdSet.has(id)) &&
      result.envelope.value.items.every(
        item => item.platform === capability.platform,
      );
    if (!valid) {
      await failBatch(nativeBridgeProblem, processedCount, totalCount);
      return;
    }

    const refreshed = await reloadProtectedCandidates(
      generation,
      sourceRevision,
      result.envelope.revision,
    );
    if (refreshed.kind === 'retired') return;
    if (refreshed.kind === 'error') {
      await failBatch(refreshed.problem, processedCount, totalCount);
      return;
    }
    const batchIdSet = new Set(batchIds);
    const refreshedIdSet = new Set(refreshed.contactIds);
    const candidatesStillMatchBatch =
      refreshed.contactIds.every(id => batchIdSet.has(id)) &&
      remainingIds.every(id => refreshedIdSet.has(id));
    if (!candidatesStillMatchBatch) {
      await failBatch(nativeBridgeProblem, processedCount, totalCount);
      return;
    }

    const nextBlockedCount = blockedCount + result.envelope.value.blockedCount;
    if (result.envelope.value.readyCount === 0) {
      const nextProcessedCount = processedCount + requestedIds.length;
      if (followingIds.length === 0) {
        verifyCompletedWork(nextBlockedCount, nextProcessedCount);
        return;
      }
      await prepareNext({
        blockedCount: nextBlockedCount,
        batchIds,
        expectedRevision: refreshed.revision,
        generation,
        processedCount: nextProcessedCount,
        remainingIds: followingIds,
        sourceRevision: refreshed.revision,
        totalCount,
      });
      return;
    }

    setReview({
      batchIds,
      review: result.envelope.value,
      revision: result.envelope.revision,
      sourceRevision: refreshed.revision,
      requestedIds,
      remainingIds: followingIds,
      processedCount,
      blockedCount,
      totalCount,
    });
    protectedRequestPendingRef.current = false;
    setPending(false);
  };

  const prepare = async (
    contactIds: readonly ContactId[],
    revision: NativeRevision,
  ) => {
    const candidateTruth = candidateTruthRef.current;
    if (
      contactIds.length === 0 ||
      protectedRequestPendingRef.current ||
      !candidateTruth.usable ||
      candidateTruth.revision !== revision
    ) {
      return;
    }
    const generation = beginProtectedWork(revision);
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    setReview(undefined);
    setIncompleteBatch(undefined);
    await prepareNext({
      blockedCount: 0,
      batchIds: contactIds,
      expectedRevision: revision,
      generation,
      processedCount: 0,
      remainingIds: contactIds,
      sourceRevision: revision,
      totalCount: contactIds.length,
    });
  };

  const confirm = async () => {
    const currentReview = reviewRef.current;
    const candidateTruth = candidateTruthRef.current;
    if (
      !currentReview ||
      protectedRequestPendingRef.current ||
      !candidateTruth.usable ||
      candidateTruth.revision !== currentReview.sourceRevision
    ) {
      invalidateProtectedWork();
      return;
    }
    const generation = beginProtectedWork(currentReview.sourceRevision);
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['confirmApprovals']>>;
    try {
      result = await port.confirmApprovals({
        handle: currentReview.review.handle,
        expectedRevision: currentReview.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (!isProtectedRequestCurrent(generation, currentReview.sourceRevision)) {
      return;
    }
    if (result.kind === 'error') {
      await failBatch(
        result.problem,
        currentReview.processedCount,
        currentReview.totalCount,
      );
      return;
    }
    if (result.envelope.value.platform !== capability.platform) {
      invalidateProtectedWork();
      await candidates.reload();
      if (!mountedRef.current) return;
      setProblem(nativePlatformMismatchProblem);
      setIncompleteBatch(
        currentReview.processedCount > 0
          ? {
              processedCount: currentReview.processedCount,
              totalCount: currentReview.totalCount,
            }
          : undefined,
      );
      return;
    }
    const refreshed = await reloadProtectedCandidates(
      generation,
      currentReview.sourceRevision,
      result.envelope.revision,
    );
    if (refreshed.kind === 'retired') return;
    if (refreshed.kind === 'error') {
      await failBatch(
        refreshed.problem,
        currentReview.processedCount,
        currentReview.totalCount,
      );
      return;
    }
    const batchIdSet = new Set(currentReview.batchIds);
    const refreshedIdSet = new Set(refreshed.contactIds);
    const approvedIdSet = new Set(
      currentReview.review.items.map(item => item.contactId),
    );
    const candidatesStillMatchBatch =
      refreshed.contactIds.every(id => batchIdSet.has(id)) &&
      currentReview.remainingIds.every(id => refreshedIdSet.has(id)) &&
      refreshed.contactIds.every(id => !approvedIdSet.has(id));
    if (!candidatesStillMatchBatch) {
      await failBatch(
        nativeBridgeProblem,
        currentReview.processedCount,
        currentReview.totalCount,
      );
      return;
    }
    const processedCount =
      currentReview.processedCount + currentReview.requestedIds.length;
    const blockedCount =
      currentReview.blockedCount + currentReview.review.blockedCount;
    setReview(undefined);
    reviewRef.current = undefined;
    if (currentReview.remainingIds.length === 0) {
      verifyCompletedWork(blockedCount, processedCount);
      return;
    }
    await prepareNext({
      blockedCount,
      batchIds: currentReview.batchIds,
      expectedRevision: refreshed.revision,
      generation,
      processedCount,
      remainingIds: currentReview.remainingIds,
      sourceRevision: refreshed.revision,
      totalCount: currentReview.totalCount,
    });
  };

  const cancelReview = async () => {
    const currentReview = reviewRef.current;
    if (!currentReview) return;
    const { processedCount, totalCount } = currentReview;
    invalidateProtectedWork();
    if (processedCount === 0) return;
    setIncompleteBatch({ processedCount, totalCount });
    const refreshGeneration = protectedWorkGenerationRef.current;
    const refreshed = await candidates.reload();
    if (
      mountedRef.current &&
      refreshGeneration === protectedWorkGenerationRef.current &&
      refreshed.kind === 'error'
    ) {
      setProblem(refreshed.problem);
    }
  };

  if (candidates.state.kind === 'loading') {
    return (
      <Screen includeTopInset testID="live-batch-approval-screen">
        <Button
          label={t('live.common.back')}
          onPress={onBack}
          variant="ghost"
        />
        <LiveLoading label={t('live.guidedSetup.approvalChecking')} />
      </Screen>
    );
  }
  if (candidates.state.kind === 'error') {
    return (
      <Screen includeTopInset testID="live-batch-approval-screen">
        <Button
          label={t('live.common.back')}
          onPress={onBack}
          variant="ghost"
        />
        <LiveError
          title={t('live.guidedSetup.approvalUnavailable')}
          problem={candidates.state.problem}
          onRetry={() => {
            invalidateProtectedWork();
            return candidates.reload();
          }}
        />
      </Screen>
    );
  }

  const readyEnvelope = candidates.state.result.envelope;
  const pendingContactIds = readyEnvelope.value.contactIds;
  const currentReview =
    review &&
    candidateUsable &&
    review.sourceRevision === readyEnvelope.revision
      ? review
      : undefined;
  return (
    <Screen includeTopInset testID="live-batch-approval-screen">
      <Button label={t('live.common.back')} onPress={onBack} variant="ghost" />
      <AppText variant="title" accessibilityRole="header">
        {t('live.guidedSetup.approvalTitle')}
      </AppText>
      <AppText color="muted">{t('live.guidedSetup.approvalBody')}</AppText>
      {candidates.state.refreshProblem ? (
        <LiveRefreshProblem problem={candidates.state.refreshProblem} />
      ) : null}
      <LiveActionFeedback problem={problem} message={message} />
      {incompleteBatch ? (
        <ReadinessBanner
          title={t('live.guidedSetup.approvalIncomplete')}
          detail={t('live.guidedSetup.approvalIncompleteBody', {
            completed: incompleteBatch.processedCount,
            total: incompleteBatch.totalCount,
          })}
          tone="warning"
        />
      ) : null}

      {!currentReview &&
      candidateUsable &&
      pendingContactIds.length === 0 &&
      !problem &&
      !candidates.state.refreshProblem ? (
        <Card>
          <StatusRow
            title={t('live.guidedSetup.approvalComplete')}
            tone="positive"
          />
          <Button
            label={t('live.guidedSetup.continueFinalStep')}
            onPress={onBack}
            testID="live-batch-approval-complete"
          />
        </Card>
      ) : null}

      {!currentReview && pendingContactIds.length > 0 ? (
        <Card>
          <StatusRow
            title={t('live.guidedSetup.approvalPending', {
              count: pendingContactIds.length,
            })}
            tone="warning"
          />
          <Button
            label={
              pending
                ? t('live.common.checking')
                : t('live.guidedSetup.reviewExactMessages')
            }
            disabled={pending || !candidateUsable}
            onPress={() => prepare(pendingContactIds, readyEnvelope.revision)}
            testID="live-batch-approval-prepare"
          />
        </Card>
      ) : null}

      {currentReview ? (
        <>
          <ReadinessBanner
            title={t('live.guidedSetup.exactApprovalRequired')}
            detail={t('live.guidedSetup.exactApprovalBatchBody', {
              count: currentReview.review.items.length,
              processed: currentReview.processedCount,
              total: currentReview.totalCount,
            })}
            tone="warning"
          />
          {currentReview.review.items.map(item => (
            <Card key={item.contactId}>
              <AppText variant="heading">{item.recipient}</AppText>
              <KeyValue
                label={t('live.person.phone')}
                value={item.maskedPhone}
              />
              <KeyValue
                label={t('live.person.birthday')}
                value={item.birthdayLabel}
              />
              <KeyValue
                label={t('live.common.message')}
                value={item.exactText}
              />
              {item.platform === 'android' ? (
                <View style={styles.details}>
                  <KeyValue
                    label={t('live.common.sim')}
                    value={item.simLabel}
                  />
                  <KeyValue
                    label={t('live.home.window')}
                    value={item.windowLabel}
                  />
                  <KeyValue
                    label={t('live.automation.segmentCount')}
                    value={String(item.segmentCount)}
                  />
                  <AppText color="muted">
                    {t('live.person.androidChargeDisclosure')}
                  </AppText>
                </View>
              ) : null}
              <AppText color="muted">
                {t(
                  item.platform === 'android'
                    ? 'live.person.androidConsentDisclosure'
                    : 'live.person.iosConsentDisclosure',
                )}
              </AppText>
            </Card>
          ))}
          {currentReview.review.blockedCount > 0 ? (
            <ReadinessBanner
              title={t('live.guidedSetup.approvalBlocked', {
                count: currentReview.review.blockedCount,
              })}
              detail={t('live.guidedSetup.approvalBlockedBody')}
              tone="warning"
            />
          ) : null}
          <Button
            label={t('live.guidedSetup.confirmExactMessages')}
            disabled={pending || !candidateUsable}
            onPress={confirm}
            testID="live-batch-approval-confirm"
          />
          <Button
            label={t('live.common.cancel')}
            disabled={pending}
            onPress={() => cancelReview().catch(() => undefined)}
            variant="secondary"
          />
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  details: { gap: spacing.sm },
});
