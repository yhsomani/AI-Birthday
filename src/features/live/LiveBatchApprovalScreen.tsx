import React, { useCallback, useState } from 'react';
import { StyleSheet, View } from 'react-native';

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
  review: ApprovalBatchReview;
  revision: NativeRevision;
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

  const failBatch = async (
    nextProblem: NativeProblem,
    processedCount: number,
    totalCount: number,
  ) => {
    setReview(undefined);
    setProblem(nextProblem);
    setIncompleteBatch(
      processedCount > 0 ? { processedCount, totalCount } : undefined,
    );
    if (nextProblem.kind === 'stale-revision' || processedCount > 0) {
      await candidates.reload();
    }
    setPending(false);
  };

  const verifyCompletedWork = async (
    blockedCount: number,
    processedCount: number,
    totalCount: number,
  ) => {
    const refreshed = await candidates.reload();
    if (refreshed.kind === 'error') {
      setProblem(refreshed.problem);
      setIncompleteBatch({ processedCount, totalCount });
      setPending(false);
      return;
    }
    setIncompleteBatch(undefined);
    setMessage(
      blockedCount > 0
        ? t('live.guidedSetup.approvalSavedWithBlocked', {
            approved: processedCount - blockedCount,
            blocked: blockedCount,
          })
        : t('live.guidedSetup.approvalSaved'),
    );
    setPending(false);
  };

  const prepareNext = async ({
    blockedCount,
    expectedRevision,
    processedCount,
    remainingIds,
    totalCount,
  }: {
    blockedCount: number;
    expectedRevision: NativeRevision;
    processedCount: number;
    remainingIds: readonly ContactId[];
    totalCount: number;
  }): Promise<void> => {
    const requestedIds = remainingIds.slice(0, PEOPLE_REVIEW_BATCH_SIZE);
    const followingIds = remainingIds.slice(PEOPLE_REVIEW_BATCH_SIZE);
    if (requestedIds.length === 0) {
      await verifyCompletedWork(blockedCount, processedCount, totalCount);
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

    const nextBlockedCount = blockedCount + result.envelope.value.blockedCount;
    if (result.envelope.value.readyCount === 0) {
      const nextProcessedCount = processedCount + requestedIds.length;
      if (followingIds.length === 0) {
        await verifyCompletedWork(
          nextBlockedCount,
          nextProcessedCount,
          totalCount,
        );
        return;
      }
      await prepareNext({
        blockedCount: nextBlockedCount,
        expectedRevision: result.envelope.revision,
        processedCount: nextProcessedCount,
        remainingIds: followingIds,
        totalCount,
      });
      return;
    }

    setReview({
      review: result.envelope.value,
      revision: result.envelope.revision,
      requestedIds,
      remainingIds: followingIds,
      processedCount,
      blockedCount,
      totalCount,
    });
    setPending(false);
  };

  const prepare = async (
    contactIds: readonly ContactId[],
    revision: NativeRevision,
  ) => {
    if (contactIds.length === 0) return;
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    setReview(undefined);
    setIncompleteBatch(undefined);
    await prepareNext({
      blockedCount: 0,
      expectedRevision: revision,
      processedCount: 0,
      remainingIds: contactIds,
      totalCount: contactIds.length,
    });
  };

  const confirm = async () => {
    if (!review) return;
    setPending(true);
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['confirmApprovals']>>;
    try {
      result = await port.confirmApprovals({
        handle: review.review.handle,
        expectedRevision: review.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      await failBatch(result.problem, review.processedCount, review.totalCount);
      return;
    }
    if (result.envelope.value.platform !== capability.platform) {
      setReview(undefined);
      await candidates.reload();
      setProblem(nativePlatformMismatchProblem);
      setIncompleteBatch(
        review.processedCount > 0
          ? {
              processedCount: review.processedCount,
              totalCount: review.totalCount,
            }
          : undefined,
      );
      setPending(false);
      return;
    }
    const processedCount = review.processedCount + review.requestedIds.length;
    const blockedCount = review.blockedCount + review.review.blockedCount;
    setReview(undefined);
    if (review.remainingIds.length === 0) {
      await verifyCompletedWork(
        blockedCount,
        processedCount,
        review.totalCount,
      );
      return;
    }
    await prepareNext({
      blockedCount,
      expectedRevision: result.envelope.revision,
      processedCount,
      remainingIds: review.remainingIds,
      totalCount: review.totalCount,
    });
  };

  const cancelReview = async () => {
    if (!review) return;
    const { processedCount, totalCount } = review;
    setReview(undefined);
    if (processedCount === 0) return;
    setPending(true);
    setIncompleteBatch({ processedCount, totalCount });
    const refreshed = await candidates.reload();
    if (refreshed.kind === 'error') setProblem(refreshed.problem);
    setPending(false);
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
          onRetry={() => candidates.reload()}
        />
      </Screen>
    );
  }

  const readyEnvelope = candidates.state.result.envelope;
  const pendingContactIds = readyEnvelope.value.contactIds;
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

      {!review &&
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

      {!review && pendingContactIds.length > 0 ? (
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
            disabled={pending}
            onPress={() => prepare(pendingContactIds, readyEnvelope.revision)}
            testID="live-batch-approval-prepare"
          />
        </Card>
      ) : null}

      {review ? (
        <>
          <ReadinessBanner
            title={t('live.guidedSetup.exactApprovalRequired')}
            detail={t('live.guidedSetup.exactApprovalBatchBody', {
              count: review.review.items.length,
              processed: review.processedCount,
              total: review.totalCount,
            })}
            tone="warning"
          />
          {review.review.items.map(item => (
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
          {review.review.blockedCount > 0 ? (
            <ReadinessBanner
              title={t('live.guidedSetup.approvalBlocked', {
                count: review.review.blockedCount,
              })}
              detail={t('live.guidedSetup.approvalBlockedBody')}
              tone="warning"
            />
          ) : null}
          <Button
            label={t('live.guidedSetup.confirmExactMessages')}
            disabled={pending}
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
