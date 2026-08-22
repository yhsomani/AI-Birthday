import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, StyleSheet, View } from 'react-native';

import type {
  AutomationProjection,
  TodayOccurrenceChoice,
  TodayOccurrenceReview,
} from '../../domain/automation/model';
import type { NativeRevision } from '../../domain/shared/brand';
import type { NativeProblem } from '../../domain/shared/result';
import type { SyncProjection } from '../../domain/contacts/model';
import type { PlatformCapability } from '../../domain/shared/platform';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  KeyValue,
  ReadinessBanner,
  Screen,
  SectionHeading,
  SettingRow,
  StatusRow,
} from '../../design-system/components/Primitives';
import { spacing } from '../../design-system/tokens/theme';
import type { StatusTone } from '../../design-system/tokens/theme';
import { formatLiveDate } from '../../localization/formatLive';
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
import {
  nativeBridgeProblem,
  nativePlatformMismatchProblem,
} from './nativeProblem';
import { useLiveProjection } from './useLiveProjection';

const automationStatus = (
  automation: AutomationProjection,
): Readonly<{
  title: TranslationKey;
  detail: TranslationKey;
  tone: StatusTone;
}> => {
  switch (automation.effective) {
    case 'active':
      return {
        title: 'live.home.androidActive',
        detail: 'live.home.androidActiveBody',
        tone: 'positive',
      };
    case 'test-only':
      return {
        title: 'live.home.androidTestOnly',
        detail: 'live.home.androidTestOnlyBody',
        tone: 'info',
      };
    case 'paused-repair':
    case 'action-required':
      return {
        title: 'live.home.androidAttention',
        detail: 'live.home.androidAttentionBody',
        tone: 'critical',
      };
    case 'standby':
      return {
        title: 'live.home.androidStandby',
        detail: 'live.home.androidStandbyBody',
        tone: 'info',
      };
    case 'transfer-pending':
      return {
        title: 'live.home.androidTransfer',
        detail: 'live.home.androidTransferBody',
        tone: 'warning',
      };
    case 'deleting':
      return {
        title: 'live.home.androidDeleting',
        detail: 'live.home.androidDeletingBody',
        tone: 'warning',
      };
    case 'not-configured':
      return {
        title: 'live.home.androidNotConfigured',
        detail: 'live.home.androidNotConfiguredBody',
        tone: 'warning',
      };
  }
};

type TodayReviewState = Readonly<{
  review: TodayOccurrenceReview;
  reviewRevision: NativeRevision;
  sourceHomeRevision: NativeRevision;
  sourceOccurrenceId: string;
  sourceTrustGeneration: number;
}>;

type PauseReviewState = Readonly<{
  sourceHomeRevision: NativeRevision;
  sourceTrustGeneration: number;
}>;

export function LiveHomeScreen({
  capability,
  onOpenActivity,
  onOpenAttention,
  onOpenAutomation,
  onContinueSetup,
  onOpenPeople,
  port,
  productSetupRequired,
}: {
  capability: PlatformCapability;
  onOpenActivity: () => void;
  onOpenAttention: () => void;
  onOpenAutomation: () => void;
  onContinueSetup: () => void;
  onOpenPeople: () => void;
  port: LiveAppPort;
  productSetupRequired: boolean;
}) {
  const { language, t } = useAppLocalization();
  const loadHome = useCallback(() => port.getHome(), [port]);
  const home = useLiveProjection(loadHome, port, ['home']);
  const trustedHomeEnvelope =
    home.state.kind === 'ready' &&
    !home.state.refreshing &&
    home.state.refreshProblem === undefined &&
    home.state.result.envelope.value.automation.platform === capability.platform
      ? home.state.result.envelope
      : undefined;
  const [todayReview, setTodayReview] = useState<TodayReviewState>();
  const [todayPending, setTodayPending] = useState(false);
  const [todayProblem, setTodayProblem] = useState<NativeProblem>();
  const [todayMessage, setTodayMessage] = useState<string>();
  const [pauseReview, setPauseReview] = useState<PauseReviewState>();
  const [expandedApprovedOccurrenceId, setExpandedApprovedOccurrenceId] =
    useState<string>();
  const homeActionSequence = useRef(0);
  const homeTrustGeneration = useRef(0);
  const trustedHomeEnvelopeRef = useRef(trustedHomeEnvelope);
  trustedHomeEnvelopeRef.current = trustedHomeEnvelope;
  const renderTrustGeneration = homeTrustGeneration.current;

  useEffect(() => {
    const subscription = AppState.addEventListener('change', nextState => {
      if (nextState === 'active') {
        homeActionSequence.current += 1;
        homeTrustGeneration.current += 1;
        setTodayReview(undefined);
        setPauseReview(undefined);
        setExpandedApprovedOccurrenceId(undefined);
        setTodayPending(false);
        setTodayProblem(undefined);
        setTodayMessage(undefined);
      }
    });
    return () => subscription.remove();
  }, []);

  useEffect(
    () =>
      port.subscribeInvalidations(event => {
        if (
          event.areas.includes('home') ||
          event.areas.includes('account') ||
          event.areas.includes('automation') ||
          event.areas.includes('contacts') ||
          event.areas.includes('setup')
        ) {
          homeActionSequence.current += 1;
          homeTrustGeneration.current += 1;
          setTodayReview(undefined);
          setPauseReview(undefined);
          setExpandedApprovedOccurrenceId(undefined);
          setTodayPending(false);
          setTodayProblem(undefined);
          setTodayMessage(undefined);
        }
      }),
    [port],
  );

  useEffect(
    () => () => {
      homeActionSequence.current += 1;
      homeTrustGeneration.current += 1;
    },
    [],
  );

  useEffect(() => {
    const currentRevision = trustedHomeEnvelope?.revision;
    const currentOccurrenceId = trustedHomeEnvelope?.value.next?.occurrenceId;

    setTodayReview(current =>
      current &&
      (current.sourceHomeRevision !== currentRevision ||
        current.sourceOccurrenceId !== currentOccurrenceId)
        ? undefined
        : current,
    );
    setPauseReview(current =>
      current !== undefined && current.sourceHomeRevision !== currentRevision
        ? undefined
        : current,
    );
    setExpandedApprovedOccurrenceId(current =>
      current !== undefined && current !== currentOccurrenceId
        ? undefined
        : current,
    );
  }, [
    trustedHomeEnvelope?.revision,
    trustedHomeEnvelope?.value.next?.occurrenceId,
  ]);

  const prepareToday = async () => {
    const currentHome = trustedHomeEnvelope;
    if (
      !currentHome ||
      renderTrustGeneration !== homeTrustGeneration.current ||
      currentHome.value.automation.platform !== 'android' ||
      currentHome.value.counts.today <= 0 ||
      currentHome.value.next === undefined
    ) {
      setTodayReview(undefined);
      return;
    }
    const sourceOccurrenceId = currentHome.value.next.occurrenceId;
    const request = homeActionSequence.current + 1;
    homeActionSequence.current = request;
    setTodayPending(true);
    setTodayProblem(undefined);
    setTodayMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['prepareTodayOccurrence']>>;
    try {
      result = await port.prepareTodayOccurrence({
        occurrenceId: sourceOccurrenceId,
        expectedRevision: currentHome.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (homeActionSequence.current !== request) return;
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
        await home.reload();
        if (homeActionSequence.current !== request) return;
      }
      setTodayProblem(result.problem);
      setTodayPending(false);
      return;
    }
    const reviewEnvelope = result.envelope;
    if (reviewEnvelope.revision !== currentHome.revision) {
      await home.reload();
      if (homeActionSequence.current !== request) return;
      setTodayProblem(nativeBridgeProblem);
      setTodayPending(false);
      return;
    }
    setTodayReview({
      review: reviewEnvelope.value,
      reviewRevision: reviewEnvelope.revision,
      sourceHomeRevision: currentHome.revision,
      sourceOccurrenceId,
      sourceTrustGeneration: homeTrustGeneration.current,
    });
    setTodayPending(false);
  };

  const confirmToday = async (choice: TodayOccurrenceChoice) => {
    const currentHome = trustedHomeEnvelope;
    if (
      !currentHome ||
      todayReview?.sourceHomeRevision !== currentHome.revision ||
      todayReview?.reviewRevision !== currentHome.revision ||
      todayReview.sourceOccurrenceId !== currentHome.value.next?.occurrenceId ||
      todayReview.sourceTrustGeneration !== homeTrustGeneration.current ||
      renderTrustGeneration !== homeTrustGeneration.current
    ) {
      setTodayReview(undefined);
      return;
    }
    const request = homeActionSequence.current + 1;
    homeActionSequence.current = request;
    setTodayPending(true);
    setTodayProblem(undefined);
    setTodayMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['confirmTodayOccurrence']>>;
    try {
      result = await port.confirmTodayOccurrence({
        handle: todayReview.review.handle,
        expectedRevision: todayReview.reviewRevision,
        choice,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }

    if (homeActionSequence.current !== request) return;
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
        await home.reload();
        if (homeActionSequence.current !== request) return;
      }
      setTodayProblem(result.problem);
      setTodayPending(false);
      return;
    }
    if (result.envelope.value.platform !== capability.platform) {
      setTodayProblem(nativePlatformMismatchProblem);
      setTodayPending(false);
      return;
    }
    setTodayReview(undefined);
    await home.reload();
    if (homeActionSequence.current !== request) return;
    setTodayMessage(
      t(
        choice === 'open-system-composer'
          ? 'live.home.todayComposerAccepted'
          : choice === 'start-next-year'
          ? 'live.home.todayNextYearAccepted'
          : 'live.home.todayAccepted',
      ),
    );
    setTodayPending(false);
  };

  const pauseFromHome = async () => {
    const currentHome = trustedHomeEnvelope;
    const currentAutomation = currentHome?.value.automation;
    const stillPausable =
      currentAutomation?.platform === 'android' &&
      currentAutomation.desired === 'on' &&
      !['standby', 'transfer-pending', 'deleting'].includes(
        currentAutomation.effective,
      );
    if (
      !currentHome ||
      pauseReview?.sourceHomeRevision !== currentHome.revision ||
      pauseReview.sourceTrustGeneration !== homeTrustGeneration.current ||
      renderTrustGeneration !== homeTrustGeneration.current ||
      !stillPausable
    ) {
      setPauseReview(undefined);
      return;
    }
    const request = homeActionSequence.current + 1;
    homeActionSequence.current = request;
    setTodayPending(true);
    setTodayProblem(undefined);
    setTodayMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['pauseAll']>>;
    try {
      result = await port.pauseAll({
        expectedRevision: pauseReview.sourceHomeRevision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (homeActionSequence.current !== request) return;
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
        await home.reload();
        if (homeActionSequence.current !== request) return;
      }
      setTodayProblem(result.problem);
      setTodayPending(false);
      return;
    }
    if (result.envelope.value.platform !== capability.platform) {
      setTodayProblem(nativePlatformMismatchProblem);
      setTodayPending(false);
      return;
    }
    setPauseReview(undefined);
    await home.reload();
    if (homeActionSequence.current !== request) return;
    setTodayMessage(t('live.home.pauseAccepted'));
    setTodayPending(false);
  };

  if (productSetupRequired) {
    return (
      <Screen
        includeTopInset
        includeBottomInset={false}
        testID="live-home-screen"
      >
        <AppText variant="title" accessibilityRole="header">
          {t('tabs.home')}
        </AppText>
        <ReadinessBanner
          title={t('live.home.setupIncompleteTitle')}
          detail={t('live.home.setupIncompleteBody')}
          tone="info"
          testID="live-home-setup-incomplete"
        />
        <Button
          label={t('live.home.continueSetup')}
          onPress={onContinueSetup}
          testID="live-home-continue-setup"
        />
      </Screen>
    );
  }

  if (home.state.kind === 'loading') {
    return (
      <Screen
        includeTopInset
        includeBottomInset={false}
        testID="live-home-screen"
      >
        <LiveLoading label={t('live.home.loading')} />
      </Screen>
    );
  }
  if (home.state.kind === 'error') {
    return (
      <Screen
        includeTopInset
        includeBottomInset={false}
        testID="live-home-screen"
      >
        <LiveError
          title={t('live.home.unavailable')}
          problem={home.state.problem}
          onRetry={() => home.reload()}
        />
      </Screen>
    );
  }

  const projection = home.state.result.envelope.value;
  if (projection.automation.platform !== capability.platform) {
    return (
      <Screen
        includeTopInset
        includeBottomInset={false}
        testID="live-home-screen"
      >
        <LiveError
          title={t('live.home.platformMismatch')}
          problem={nativePlatformMismatchProblem}
          onRetry={() => home.reload()}
        />
      </Screen>
    );
  }
  const homeStable = trustedHomeEnvelope !== undefined;
  const homeRevision = home.state.result.envelope.revision;
  const status = automationStatus(projection.automation);
  const canPause =
    homeStable &&
    projection.automation.platform === 'android' &&
    projection.automation.desired === 'on' &&
    !['standby', 'transfer-pending', 'deleting'].includes(
      projection.automation.effective,
    );
  const needsRepair =
    projection.counts.needsAttention > 0 ||
    projection.contactsSync.kind !== 'fresh' ||
    projection.automation.effective === 'action-required' ||
    projection.automation.effective === 'paused-repair' ||
    projection.automation.effective === 'transfer-pending';
  const currentTodayReview =
    homeStable &&
    todayReview?.sourceHomeRevision === homeRevision &&
    todayReview.sourceOccurrenceId === projection.next?.occurrenceId &&
    todayReview.sourceTrustGeneration === homeTrustGeneration.current &&
    projection.counts.today > 0
      ? todayReview
      : undefined;
  const currentPauseReview =
    homeStable &&
    pauseReview?.sourceHomeRevision === homeRevision &&
    pauseReview.sourceTrustGeneration === homeTrustGeneration.current &&
    canPause;
  const isInlineReviewOpen = Boolean(currentTodayReview || currentPauseReview);
  const todayReviewCapabilityReady =
    projection.automation.platform === 'android' &&
    projection.automation.desired === 'on' &&
    projection.automation.effective === 'active' &&
    projection.automation.readiness.birthday.kind === 'allowed';
  const hasTodayReview =
    homeStable &&
    todayReviewCapabilityReady &&
    projection.counts.today > 0 &&
    projection.next !== undefined;
  const planIsPaused = projection.automation.desired === 'paused';
  const needsPlanSetup =
    projection.automation.effective === 'not-configured' ||
    (projection.automation.platform === 'android' &&
      projection.automation.effective === 'test-only');
  const showPlanAction = homeStable && (needsPlanSetup || planIsPaused);
  const approvedMessageVisible =
    projection.next?.occurrenceId === expandedApprovedOccurrenceId;
  const contactsLabel = (sync: SyncProjection) => {
    switch (sync.kind) {
      case 'fresh':
        return t('live.home.contactsFresh', { count: sync.contactCount });
      case 'syncing':
        return t('live.home.contactsSyncing');
      case 'never-synced':
        return t('live.home.contactsNever');
      case 'authorization-required':
        return t('live.home.contactsPermission');
      case 'stale':
      case 'failed-retained':
        return t('live.home.contactsProblem', {
          reason: t(safeReasonMessageKey(sync.reason)),
        });
    }
  };

  return (
    <Screen
      includeTopInset
      includeBottomInset={false}
      testID="live-home-screen"
    >
      <View style={styles.heading}>
        <AppText variant="title" accessibilityRole="header">
          {t('home.title')}
        </AppText>
        <AppText color="muted">{t('live.common.androidEdition')}</AppText>
      </View>
      <ReadinessBanner
        title={t(status.title)}
        detail={t(status.detail)}
        tone={status.tone}
      />
      {home.state.refreshProblem ? (
        <LiveRefreshProblem problem={home.state.refreshProblem} />
      ) : null}
      <LiveActionFeedback problem={todayProblem} message={todayMessage} />

      <SectionHeading title={t('live.home.upcoming')} />
      {projection.next ? (
        <>
          <Card>
            <AppText variant="heading">{projection.next.recipient}</AppText>
            <KeyValue
              label={t('live.home.birthday')}
              value={formatLiveDate(projection.next.localDate, language)}
            />
            <KeyValue
              label={t('live.home.window')}
              value={projection.next.windowLabel}
            />
            <KeyValue
              label={t('live.home.phone')}
              value={projection.next.maskedPhone}
            />
            {projection.next.exactText && homeStable ? (
              <>
                <Button
                  label={t(
                    approvedMessageVisible
                      ? 'live.home.hideApprovedMessage'
                      : 'live.home.viewApprovedMessage',
                  )}
                  onPress={() =>
                    setExpandedApprovedOccurrenceId(
                      approvedMessageVisible
                        ? undefined
                        : projection.next?.occurrenceId,
                    )
                  }
                  variant="secondary"
                  testID="live-home-approved-message-toggle"
                />
                {approvedMessageVisible ? (
                  <View testID="live-home-approved-message">
                    <AppText variant="label">
                      {t('live.home.approvedMessage')}
                    </AppText>
                    <AppText>{projection.next.exactText}</AppText>
                    <AppText color="muted" variant="caption">
                      {t('live.home.approvedMessageBody')}
                    </AppText>
                  </View>
                ) : null}
              </>
            ) : null}
            {projection.next?.occurrenceId &&
            hasTodayReview &&
            !isInlineReviewOpen ? (
              <Button
                label={t('live.home.skipOccurrence')}
                onPress={prepareToday}
                variant="secondary"
                testID="live-home-skip-occurrence"
              />
            ) : null}
            <AppText color="muted" variant="caption">
              {t('live.home.planNotOutcome')}
            </AppText>
          </Card>

          {projection.counts.nextSevenDays > 1 ? (
            <Card testID="live-home-weekly-preview">
              <AppText variant="heading">
                {t('live.home.weeklyPreviewTitle')}
              </AppText>
              <StatusRow
                title={t('live.home.weeklyPreviewBody', {
                  count: projection.counts.nextSevenDays - 1,
                })}
                tone="info"
              />
            </Card>
          ) : null}
        </>
      ) : (
        <Card>
          <AppText>{t('live.home.noUpcoming')}</AppText>
          <Button
            label={t('live.home.reviewPeople')}
            onPress={onOpenPeople}
            variant="secondary"
            testID="live-home-open-people"
          />
        </Card>
      )}

      {currentTodayReview ? (
        <Card>
          <AppText variant="heading">{t('live.home.todayReviewTitle')}</AppText>
          <KeyValue
            label={t('live.home.phone')}
            value={currentTodayReview.review.maskedDestination}
          />
          <AppText>{currentTodayReview.review.exactText}</AppText>
          <StatusRow
            title={t(
              currentTodayReview.review.choice === 'send-through-normal-path'
                ? 'live.home.todayNormalPath'
                : currentTodayReview.review.choice === 'open-system-composer'
                ? 'live.home.todaySystemComposer'
                : 'live.home.todayNextYear',
            )}
            tone="warning"
          />
          <StatusRow
            title={t('live.home.todayExplicitConfirmation')}
            detail={t(
              currentTodayReview.review.choice === 'send-through-normal-path'
                ? 'live.home.todayNormalDisclosure'
                : currentTodayReview.review.choice === 'open-system-composer'
                ? 'live.home.todayComposerDisclosure'
                : 'live.home.todayNextYearDisclosure',
            )}
            tone="warning"
          />
          <Button
            label={t(
              currentTodayReview.review.choice === 'send-through-normal-path'
                ? 'live.home.confirmNormalPath'
                : currentTodayReview.review.choice === 'open-system-composer'
                ? 'live.home.openSystemComposer'
                : 'live.home.confirmNextYear',
            )}
            disabled={todayPending}
            onPress={() => confirmToday(currentTodayReview.review.choice)}
            testID="live-home-confirm-today"
          />
          {currentTodayReview.review.alternativeChoice ? (
            <Button
              label={t('live.home.confirmNextYear')}
              disabled={todayPending}
              onPress={() => confirmToday('start-next-year')}
              variant="secondary"
              testID="live-home-confirm-today-next-year"
            />
          ) : null}
          <Button
            label={t(
              currentTodayReview.review.choice === 'start-next-year' &&
                !currentTodayReview.review.alternativeChoice
                ? 'live.home.keepTodaySchedule'
                : 'live.common.cancel',
            )}
            disabled={todayPending}
            onPress={() => setTodayReview(undefined)}
            variant="secondary"
            testID="live-home-cancel-today"
          />
        </Card>
      ) : null}

      {currentPauseReview ? (
        <Card>
          <AppText variant="heading">{t('live.home.pauseTitle')}</AppText>
          <AppText>{t('live.home.pauseBody')}</AppText>
          <Button
            label={t('live.home.pauseConfirm')}
            disabled={todayPending}
            onPress={pauseFromHome}
            variant="danger"
            testID="live-home-confirm-pause"
          />
          <Button
            label={t('live.common.cancel')}
            disabled={todayPending}
            onPress={() => setPauseReview(undefined)}
            variant="secondary"
          />
        </Card>
      ) : null}

      {homeStable && !isInlineReviewOpen ? (
        needsRepair ? (
          <Button
            label={t('live.home.fixIssues')}
            onPress={onOpenAttention}
            testID="live-home-attention"
          />
        ) : hasTodayReview ? (
          <Button
            label={
              todayPending
                ? t('live.home.preparingToday')
                : t('live.home.reviewToday')
            }
            disabled={todayPending}
            onPress={prepareToday}
            testID="live-home-review-today"
          />
        ) : showPlanAction ? (
          <Button
            label={t(
              needsPlanSetup ? 'live.home.setupPlan' : 'live.home.managePlan',
            )}
            onPress={onOpenAutomation}
            testID="live-home-automation"
          />
        ) : null
      ) : null}

      <SectionHeading title={t('live.home.atAGlance')} />
      <Card>
        <StatusRow
          title={t('live.home.birthdays')}
          detail={t('live.home.birthdaysSummary', {
            today: projection.counts.today,
            week: projection.counts.nextSevenDays,
          })}
          tone="info"
        />
        <StatusRow
          title={t('live.people.title')}
          detail={t('live.home.peopleSummary', {
            enabled: projection.counts.enabled,
            attention: projection.counts.needsAttention,
          })}
          tone={projection.counts.needsAttention > 0 ? 'warning' : 'positive'}
        />
      </Card>

      {projection.contactsSync.kind !== 'fresh' ? (
        <Card>
          <StatusRow
            title={t('live.home.contacts')}
            detail={contactsLabel(projection.contactsSync)}
            tone="warning"
          />
        </Card>
      ) : null}

      <Card>
        <SettingRow
          title={t('live.home.openActivity')}
          detail={t('live.home.activityBody')}
          onPress={onOpenActivity}
          testID="live-home-activity"
        />
      </Card>
      {canPause && !isInlineReviewOpen ? (
        <Button
          label={t('live.home.pause')}
          disabled={todayPending}
          onPress={() => {
            if (renderTrustGeneration !== homeTrustGeneration.current) return;
            setPauseReview({
              sourceHomeRevision: homeRevision,
              sourceTrustGeneration: homeTrustGeneration.current,
            });
          }}
          variant="secondary"
          testID="live-home-pause"
        />
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  heading: { gap: spacing.xs },
});
