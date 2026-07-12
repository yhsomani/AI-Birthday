import React, { useCallback, useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import type {
  AutomationProjection,
  TodayOccurrenceChoice,
  TodayOccurrenceReview,
} from '../../domain/automation/model';
import type { SenderProjection } from '../../domain/account/model';
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
  StatusRow,
} from '../../design-system/components/Primitives';
import { spacing } from '../../design-system/tokens/theme';
import type { StatusTone } from '../../design-system/tokens/theme';
import {
  formatLiveDate,
  formatLiveInstant,
} from '../../localization/formatLive';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { safeReasonMessageKey } from '../../localization/reasonCopy';
import type { TranslationKey } from '../../localization/resources';
import type { LiveAppPort } from './LiveAppPort';
import type { LiveCompanionPort } from './LiveAppPort';
import type { CompanionReminderState } from '../../infrastructure/native/ios/CompanionNativeGateway';
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
  if (automation.platform === 'ios') {
    switch (automation.effective) {
      case 'ready':
        return {
          title: 'live.home.iosReady',
          detail: 'live.home.iosReadyBody',
          tone: 'positive',
        };
      case 'paused':
        return {
          title: 'live.home.iosPaused',
          detail: 'live.home.iosPausedBody',
          tone: 'warning',
        };
      case 'action-required':
        return {
          title: 'live.home.iosAttention',
          detail: 'live.home.iosAttentionBody',
          tone: 'critical',
        };
      case 'not-configured':
        return {
          title: 'live.home.iosNotConfigured',
          detail: 'live.home.iosNotConfiguredBody',
          tone: 'warning',
        };
    }
  }
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
  revision: NativeRevision;
}>;

type IosCompanionHomeState =
  | Readonly<{ kind: 'loading' }>
  | Readonly<{ kind: 'error' }>
  | Readonly<{
      kind: 'ready';
      canOpenComposer: boolean;
      reminder: CompanionReminderState;
    }>;

const companionPermissionKeys: Readonly<
  Record<CompanionReminderState['authorization'], TranslationKey>
> = {
  authorized: 'live.companion.permission.authorized',
  denied: 'live.companion.permission.denied',
  ephemeral: 'live.companion.permission.ephemeral',
  'not-determined': 'live.companion.permission.notDetermined',
  provisional: 'live.companion.permission.provisional',
  unknown: 'live.companion.permission.unknown',
};

export function LiveHomeScreen({
  capability,
  companionPort,
  onOpenActivity,
  onOpenAttention,
  onOpenAutomation,
  onOpenMessage,
  onOpenPeople,
  port,
  sender,
}: {
  capability: PlatformCapability;
  companionPort: LiveCompanionPort;
  onOpenActivity: () => void;
  onOpenAttention: () => void;
  onOpenAutomation: () => void;
  onOpenMessage: () => void;
  onOpenPeople: () => void;
  port: LiveAppPort;
  sender?: SenderProjection | undefined;
}) {
  const { language, t } = useAppLocalization();
  const loadHome = useCallback(() => port.getHome(), [port]);
  const home = useLiveProjection(loadHome, port, ['home']);
  const [todayReview, setTodayReview] = useState<TodayReviewState>();
  const [todayPending, setTodayPending] = useState(false);
  const [todayProblem, setTodayProblem] = useState<NativeProblem>();
  const [todayMessage, setTodayMessage] = useState<string>();
  const [confirmPause, setConfirmPause] = useState(false);
  const [iosCompanionState, setIosCompanionState] =
    useState<IosCompanionHomeState>({ kind: 'loading' });
  const homeRevision =
    home.state.kind === 'ready'
      ? home.state.result.envelope.revision
      : undefined;

  useEffect(() => {
    if (capability.platform !== 'ios') {
      return;
    }
    let active = true;
    setIosCompanionState({ kind: 'loading' });
    Promise.all([
      companionPort.getReminderStatus(),
      companionPort.canOpenComposer(),
    ])
      .then(([reminder, canOpenComposer]) => {
        if (!active) return;
        setIosCompanionState(
          reminder.kind === 'ok'
            ? { kind: 'ready', reminder: reminder.value, canOpenComposer }
            : { kind: 'error' },
        );
      })
      .catch(() => {
        if (active) setIosCompanionState({ kind: 'error' });
      });
    return () => {
      active = false;
    };
  }, [capability.platform, companionPort, homeRevision]);

  useEffect(
    () =>
      port.subscribeInvalidations(event => {
        if (
          event.areas.includes('home') ||
          event.areas.includes('automation')
        ) {
          setTodayReview(undefined);
        }
      }),
    [port],
  );

  const prepareToday = async () => {
    const next =
      home.state.kind === 'ready'
        ? home.state.result.envelope.value.next
        : undefined;
    if (
      capability.platform !== 'android' ||
      home.state.kind !== 'ready' ||
      home.state.result.envelope.value.counts.today <= 0 ||
      !next
    ) {
      return;
    }
    setTodayPending(true);
    setTodayProblem(undefined);
    setTodayMessage(undefined);
    const envelope = home.state.result.envelope;
    let result: Awaited<ReturnType<LiveAppPort['prepareTodayOccurrence']>>;
    try {
      result = await port.prepareTodayOccurrence({
        occurrenceId: next.occurrenceId,
        expectedRevision: envelope.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
        await home.reload();
      }
      setTodayProblem(result.problem);
      setTodayReview(undefined);
      setTodayPending(false);
      return;
    }
    setTodayReview({
      review: result.envelope.value,
      revision: result.envelope.revision,
    });
    setTodayPending(false);
  };

  const confirmToday = async (choice: TodayOccurrenceChoice) => {
    if (!todayReview) {
      return;
    }
    setTodayPending(true);
    setTodayProblem(undefined);
    setTodayMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['confirmTodayOccurrence']>>;
    try {
      result = await port.confirmTodayOccurrence({
        handle: todayReview.review.handle,
        choice,
        expectedRevision: todayReview.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      const composerResultRequiresReload =
        result.problem.kind === 'conflict' &&
        (result.problem.code === 'system-composer-unavailable' ||
          result.problem.code === 'system-composer-outcome-unknown');
      if (
        result.problem.kind === 'stale-revision' ||
        composerResultRequiresReload
      ) {
        await home.reload();
        setTodayReview(undefined);
      }
      setTodayProblem(result.problem);
      setTodayPending(false);
      return;
    }
    if (result.envelope.value.platform !== capability.platform) {
      setTodayProblem(nativePlatformMismatchProblem);
      setTodayReview(undefined);
      setTodayPending(false);
      return;
    }
    setTodayReview(undefined);
    await home.reload();
    setTodayMessage(
      t(
        choice === 'open-system-composer'
          ? 'live.home.todayComposerOpened'
          : choice === 'start-next-year'
          ? 'live.home.todayNextYearAccepted'
          : 'live.home.todayAccepted',
      ),
    );
    setTodayPending(false);
  };

  const pauseFromHome = async () => {
    if (home.state.kind !== 'ready') return;
    setTodayPending(true);
    setTodayProblem(undefined);
    setTodayMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['pauseAll']>>;
    try {
      result = await port.pauseAll({
        expectedRevision: home.state.result.envelope.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') await home.reload();
      setTodayProblem(result.problem);
      setTodayPending(false);
      return;
    }
    if (result.envelope.value.platform !== capability.platform) {
      setTodayProblem(nativePlatformMismatchProblem);
      setTodayPending(false);
      return;
    }
    setConfirmPause(false);
    await home.reload();
    setTodayMessage(t('live.home.pauseAccepted'));
    setTodayPending(false);
  };

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
  const status = automationStatus(projection.automation);
  const canPause =
    projection.automation.platform === 'android'
      ? projection.automation.desired === 'on' &&
        !['standby', 'transfer-pending', 'deleting'].includes(
          projection.automation.effective,
        )
      : projection.automation.desired === 'composer-reminders-on';
  const needsRepair =
    projection.counts.needsAttention > 0 ||
    projection.contactsSync.kind !== 'fresh' ||
    projection.automation.effective === 'action-required' ||
    projection.automation.effective === 'paused-repair' ||
    projection.automation.effective === 'transfer-pending';
  const iosComposerIssues =
    projection.automation.platform === 'ios' &&
    projection.automation.readiness.composer.kind === 'blocked'
      ? projection.automation.readiness.composer.issues
      : [];
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
        <AppText color="muted">
          {t(
            capability.platform === 'android'
              ? 'live.common.androidEdition'
              : 'live.common.iosEdition',
          )}
        </AppText>
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
          {projection.next.exactText ? (
            <>
              <AppText variant="label">
                {t('live.home.approvedMessage')}
              </AppText>
              <AppText>{projection.next.exactText}</AppText>
              <AppText color="muted" variant="caption">
                {t('live.home.approvedMessageBody')}
              </AppText>
            </>
          ) : null}
          <AppText color="muted" variant="caption">
            {t('live.home.planNotOutcome')}
          </AppText>
          {projection.counts.today > 0 ? (
            <Button
              label={
                capability.platform === 'ios'
                  ? t('live.home.reviewTodayIos')
                  : todayPending
                  ? t('live.home.preparingToday')
                  : t('live.home.reviewToday')
              }
              disabled={todayPending}
              onPress={
                capability.platform === 'android'
                  ? prepareToday
                  : onOpenAutomation
              }
              testID="live-home-review-today"
            />
          ) : null}
        </Card>
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

      {capability.platform === 'android' && todayReview ? (
        <Card>
          <AppText variant="heading">{t('live.home.todayReviewTitle')}</AppText>
          <KeyValue
            label={t('live.companion.recipient')}
            value={todayReview.review.recipient}
          />
          <KeyValue
            label={t('live.home.phone')}
            value={todayReview.review.maskedDestination}
          />
          <AppText>{todayReview.review.exactText}</AppText>
          <StatusRow
            title={t(
              todayReview.review.choice === 'send-through-normal-path'
                ? 'live.home.todayNormalPath'
                : todayReview.review.choice === 'open-system-composer'
                ? 'live.home.todaySystemComposer'
                : 'live.home.todayNextYear',
            )}
            tone="warning"
          />
          <ReadinessBanner
            title={t('live.home.todayExplicitConfirmation')}
            detail={t(
              todayReview.review.choice === 'send-through-normal-path'
                ? 'live.home.todayNormalDisclosure'
                : todayReview.review.choice === 'open-system-composer'
                ? 'live.home.todayComposerDisclosure'
                : 'live.home.todayNextYearDisclosure',
            )}
            tone="warning"
          />
          <Button
            label={t(
              todayReview.review.choice === 'send-through-normal-path'
                ? 'live.home.confirmNormalPath'
                : todayReview.review.choice === 'open-system-composer'
                ? 'live.home.openSystemComposer'
                : 'live.home.confirmNextYear',
            )}
            disabled={todayPending}
            onPress={() => confirmToday(todayReview.review.choice)}
            testID="live-home-confirm-today"
          />
          {todayReview.review.alternativeChoice ? (
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
              todayReview.review.choice === 'start-next-year' &&
                !todayReview.review.alternativeChoice
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

      {confirmPause ? (
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
            onPress={() => setConfirmPause(false)}
            variant="secondary"
          />
        </Card>
      ) : null}

      <SectionHeading title={t('live.home.counts')} />
      <Card>
        <StatusRow
          title={t('live.common.enabled')}
          detail={String(projection.counts.enabled)}
          tone="info"
        />
        <StatusRow
          title={t('live.home.needsAttention')}
          detail={String(projection.counts.needsAttention)}
          tone={projection.counts.needsAttention > 0 ? 'warning' : 'positive'}
        />
        <StatusRow
          title={t('live.home.today')}
          detail={String(projection.counts.today)}
        />
        <StatusRow
          title={t('live.home.nextSeven')}
          detail={String(projection.counts.nextSevenDays)}
        />
        <StatusRow
          title={t('live.common.unavailable')}
          detail={String(projection.counts.unavailable)}
        />
      </Card>

      <SectionHeading title={t('live.home.service')} />
      <Card>
        <StatusRow
          title={t('live.home.contacts')}
          detail={contactsLabel(projection.contactsSync)}
          tone={
            projection.contactsSync.kind === 'fresh' ? 'positive' : 'warning'
          }
        />
        {projection.schedulerHeartbeatAt ? (
          <StatusRow
            title={t('live.home.scheduler')}
            detail={formatLiveInstant(
              projection.schedulerHeartbeatAt,
              language,
            )}
            tone="info"
          />
        ) : null}
        {projection.lastCoordinationSuccessAt ? (
          <StatusRow
            title={t('live.home.coordination')}
            detail={formatLiveInstant(
              projection.lastCoordinationSuccessAt,
              language,
            )}
            tone="info"
          />
        ) : null}
        {capability.platform === 'android' && sender?.platform === 'android' ? (
          <StatusRow
            title={t('live.home.activeSender')}
            detail={
              'epochLabel' in sender
                ? sender.epochLabel
                : sender.kind === 'standby'
                ? sender.activeOtherDeviceLabel
                : t(`live.home.sender.${sender.kind}`)
            }
            tone={
              sender.kind === 'automation-active'
                ? 'positive'
                : sender.kind === 'deleting' ||
                  sender.kind === 'transfer-pending'
                ? 'warning'
                : 'info'
            }
            testID="live-home-active-sender"
          />
        ) : null}
      </Card>

      {capability.platform === 'ios' ? (
        <>
          <SectionHeading title={t('live.home.iosCompanionStatus')} />
          {iosCompanionState.kind === 'loading' ? (
            <LiveLoading label={t('live.home.iosStatusChecking')} />
          ) : iosCompanionState.kind === 'error' ? (
            <ReadinessBanner
              title={t('live.home.iosSafetyUnavailable')}
              detail={t('live.home.iosSafetyUnavailableBody')}
              tone="warning"
            />
          ) : (
            <Card>
              {iosCompanionState.reminder.kind === 'error' ? (
                <ReadinessBanner
                  title={t('live.home.reminderPlanProblem')}
                  detail={t('live.home.reminderPlanProblemBody')}
                  tone="warning"
                />
              ) : null}
              <StatusRow
                title={t('live.home.notificationVisibility')}
                detail={t(
                  companionPermissionKeys[
                    iosCompanionState.reminder.authorization
                  ],
                )}
                tone={
                  iosCompanionState.reminder.authorization === 'authorized'
                    ? 'positive'
                    : 'warning'
                }
              />
              <StatusRow
                title={t('live.companion.scheduled', {
                  count: iosCompanionState.reminder.scheduledCount,
                })}
                detail={t('live.companion.planned', {
                  count: iosCompanionState.reminder.plannedDateCount,
                })}
                tone={
                  iosCompanionState.reminder.failedCount > 0 ||
                  iosCompanionState.reminder.truncated
                    ? 'warning'
                    : 'positive'
                }
              />
              {iosCompanionState.reminder.failedCount > 0 ? (
                <StatusRow
                  title={t('live.companion.failedReminderCount', {
                    count: iosCompanionState.reminder.failedCount,
                  })}
                  tone="warning"
                />
              ) : null}
              {iosCompanionState.reminder.truncated ? (
                <StatusRow
                  title={t('live.companion.truncated')}
                  tone="warning"
                />
              ) : null}
              {iosCompanionState.reminder.earliestUnscheduledCivilDate ? (
                <StatusRow
                  title={t('live.companion.earliestUnscheduled')}
                  detail={formatLiveDate(
                    iosCompanionState.reminder.earliestUnscheduledCivilDate,
                    language,
                  )}
                  tone="warning"
                />
              ) : null}
              <StatusRow
                title={t('live.home.messageUiCapability')}
                detail={t(
                  iosCompanionState.canOpenComposer
                    ? 'live.home.messageUiAvailable'
                    : 'live.home.messageUiUnavailable',
                )}
                tone={
                  iosCompanionState.canOpenComposer ? 'positive' : 'warning'
                }
              />
            </Card>
          )}
          {iosComposerIssues.map(issue => (
            <StatusRow
              key={issue.id}
              title={
                issue.code === 'active-sender-other-device'
                  ? t('live.home.iosManagedByAndroid')
                  : issue.code === 'coordination-unavailable'
                  ? t('live.home.iosSafetyUnavailable')
                  : t(safeReasonMessageKey(issue.code))
              }
              detail={t('live.home.iosIssueAction')}
              tone={issue.severity === 'blocking' ? 'critical' : 'warning'}
              testID={`live-home-ios-issue-${issue.code}`}
            />
          ))}
        </>
      ) : null}

      <Button
        label={t('live.home.openActivity')}
        onPress={onOpenActivity}
        variant="secondary"
        testID="live-home-activity"
      />
      {needsRepair ? (
        <Button
          label={t('live.home.fixIssues')}
          onPress={onOpenAttention}
          variant="secondary"
          testID="live-home-attention"
        />
      ) : null}
      <Button
        label={t('live.home.openMessage')}
        onPress={onOpenMessage}
        variant="secondary"
        testID="live-home-message"
      />
      <Button
        label={t('live.home.openAutomation')}
        onPress={onOpenAutomation}
        testID="live-home-automation"
      />
      {canPause && !confirmPause ? (
        <Button
          label={t('live.home.pause')}
          disabled={todayPending}
          onPress={() => setConfirmPause(true)}
          variant="secondary"
          testID="live-home-pause"
        />
      ) : null}
      <Button
        label={
          home.state.refreshing
            ? t('live.common.refreshing')
            : t('live.home.refresh')
        }
        disabled={home.state.refreshing}
        onPress={() => home.reload()}
        variant="secondary"
        testID="live-home-refresh"
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  heading: { gap: spacing.xs },
});
