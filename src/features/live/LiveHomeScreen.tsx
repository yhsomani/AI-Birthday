import React, { useCallback, useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import type {
  AutomationProjection,
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

export function LiveHomeScreen({
  capability,
  onOpenActivity,
  onOpenAttention,
  onOpenAutomation,
  onOpenMessage,
  onOpenPeople,
  port,
}: {
  capability: PlatformCapability;
  onOpenActivity: () => void;
  onOpenAttention: () => void;
  onOpenAutomation: () => void;
  onOpenMessage: () => void;
  onOpenPeople: () => void;
  port: LiveAppPort;
}) {
  const { language, t } = useAppLocalization();
  const loadHome = useCallback(() => port.getHome(), [port]);
  const home = useLiveProjection(loadHome, port, ['home']);
  const [todayReview, setTodayReview] = useState<TodayReviewState>();
  const [todayPending, setTodayPending] = useState(false);
  const [todayProblem, setTodayProblem] = useState<NativeProblem>();
  const [todayMessage, setTodayMessage] = useState<string>();

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

  const confirmToday = async () => {
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
        expectedRevision: todayReview.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
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
    setTodayMessage(t('live.home.todayAccepted'));
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
        <Card accessibilityLabel={t('live.home.nextPlan')}>
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
          <AppText>{todayReview.review.exactText}</AppText>
          <StatusRow
            title={t(
              todayReview.review.choice === 'send-through-normal-path'
                ? 'live.home.todayNormalPath'
                : 'live.home.todayNextYear',
            )}
            tone="warning"
          />
          <ReadinessBanner
            title={t('live.home.todayExplicitConfirmation')}
            detail={todayReview.review.limitationsDisclosure}
            tone="warning"
          />
          <Button
            label={t(
              todayReview.review.choice === 'send-through-normal-path'
                ? 'live.home.confirmNormalPath'
                : 'live.home.confirmNextYear',
            )}
            disabled={todayPending}
            onPress={confirmToday}
            testID="live-home-confirm-today"
          />
          <Button
            label={t('live.common.cancel')}
            disabled={todayPending}
            onPress={() => setTodayReview(undefined)}
            variant="secondary"
            testID="live-home-cancel-today"
          />
        </Card>
      ) : null}

      <SectionHeading title={t('live.home.counts')} />
      <Card accessibilityLabel={t('live.home.countsLabel')}>
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
      </Card>

      <Button
        label={t('live.home.openActivity')}
        onPress={onOpenActivity}
        variant="secondary"
        testID="live-home-activity"
      />
      <Button
        label={t('live.home.openAttention')}
        onPress={onOpenAttention}
        variant="secondary"
        testID="live-home-attention"
      />
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
