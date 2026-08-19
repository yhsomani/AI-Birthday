import React, { useCallback, useEffect, useState } from 'react';
import { BackHandler, StyleSheet, View } from 'react-native';

import type { ContactId } from '../../domain/shared/brand';
import type { PlatformCapability } from '../../domain/shared/platform';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  ReadinessBanner,
  Screen,
  StatusRow,
} from '../../design-system/components/Primitives';
import { RouteAccessibilityFocus } from '../../design-system/components/RouteAccessibilityFocus';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import type { LiveAppPort } from './LiveAppPort';
import { LiveAutomationScreen } from './LiveAutomationScreen';
import { LiveBatchApprovalScreen } from './LiveBatchApprovalScreen';
import { LiveError, LiveLoading } from './LiveProjectionState';
import { LiveMessageScreen } from './LiveMessageScreen';
import { LivePeopleScreen } from './LivePeopleScreen';
import { LivePersonDetailScreen } from './LivePersonDetailScreen';
import { LiveScheduleScreen } from './LiveScheduleScreen';
import { needsBatchApproval, scanPeoplePages } from './peoplePagination';
import { useLiveProjection } from './useLiveProjection';

type JourneyRoute =
  | Readonly<{ kind: 'overview' }>
  | Readonly<{ kind: 'people' }>
  | Readonly<{ kind: 'person'; contactId: ContactId }>
  | Readonly<{ kind: 'message' }>
  | Readonly<{ kind: 'schedule'; returnTo: 'automation' | 'overview' }>
  | Readonly<{ kind: 'approvals' }>
  | Readonly<{ kind: 'automation' }>;

const routeKey = (route: JourneyRoute): string =>
  route.kind === 'person' ? `person:${route.contactId}` : route.kind;

export function LiveProductSetupJourney({
  capability,
  onDefer,
  onRefreshSetup,
  port,
}: {
  capability: PlatformCapability;
  onDefer: () => void;
  onRefreshSetup: () => Promise<unknown>;
  port: LiveAppPort;
}) {
  const { t } = useAppLocalization();
  const [route, setRoute] = useState<JourneyRoute>({ kind: 'overview' });
  const loadHome = useCallback(() => port.getHome(), [port]);
  const loadMessage = useCallback(() => port.getMessageEditor(), [port]);
  const loadPolicy = useCallback(() => port.getPolicyEditor(), [port]);
  const loadApprovalCandidates = useCallback(
    () =>
      scanPeoplePages(port, { filter: 'needs-attention' }, needsBatchApproval),
    [port],
  );
  const home = useLiveProjection(loadHome, port, ['home', 'contacts']);
  const message = useLiveProjection(loadMessage, port, ['messages']);
  const policy = useLiveProjection(loadPolicy, port, ['automation']);
  const approvalCandidates = useLiveProjection(loadApprovalCandidates, port, [
    'contacts',
    'messages',
    'automation',
  ]);

  const homeProjectionStable =
    home.state.kind === 'ready' &&
    !home.state.refreshing &&
    home.state.refreshProblem === undefined;
  const messageProjectionStable =
    message.state.kind === 'ready' &&
    !message.state.refreshing &&
    message.state.refreshProblem === undefined;
  const policyProjectionStable =
    policy.state.kind === 'ready' &&
    !policy.state.refreshing &&
    policy.state.refreshProblem === undefined;
  const approvalProjectionStable =
    approvalCandidates.state.kind === 'ready' &&
    !approvalCandidates.state.refreshing &&
    approvalCandidates.state.refreshProblem === undefined;
  const homeRevision =
    homeProjectionStable && home.state.kind === 'ready'
      ? home.state.result.envelope.revision
      : undefined;
  const messageRevisionMismatch =
    homeRevision !== undefined &&
    messageProjectionStable &&
    message.state.kind === 'ready' &&
    message.state.result.envelope.revision !== homeRevision;
  const policyRevisionMismatch =
    homeRevision !== undefined &&
    policyProjectionStable &&
    policy.state.kind === 'ready' &&
    policy.state.result.envelope.revision !== homeRevision;
  const approvalRevisionMismatch =
    homeRevision !== undefined &&
    approvalProjectionStable &&
    approvalCandidates.state.kind === 'ready' &&
    approvalCandidates.state.result.envelope.revision !== homeRevision;
  const projectionRevisionMismatch =
    messageRevisionMismatch ||
    policyRevisionMismatch ||
    approvalRevisionMismatch;
  const homeStable = homeProjectionStable;
  const messageStable =
    messageProjectionStable &&
    homeRevision !== undefined &&
    !messageRevisionMismatch;
  const policyStable =
    policyProjectionStable &&
    homeRevision !== undefined &&
    !policyRevisionMismatch;
  const approvalStable =
    approvalProjectionStable &&
    homeRevision !== undefined &&
    !approvalRevisionMismatch;
  const peopleReady =
    homeStable &&
    home.state.kind === 'ready' &&
    (home.state.result.envelope.value.counts.configured ??
      home.state.result.envelope.value.counts.enabled) > 0;
  const messageReady =
    messageStable &&
    message.state.kind === 'ready' &&
    message.state.result.envelope.value.kind === 'configured';
  const policyReady =
    policyStable &&
    policy.state.kind === 'ready' &&
    policy.state.result.envelope.value.kind === 'configured';
  const approvalPending =
    approvalStable &&
    approvalCandidates.state.kind === 'ready' &&
    approvalCandidates.state.result.envelope.value.contactIds.length > 0;
  const approvalReady =
    peopleReady &&
    homeStable &&
    home.state.kind === 'ready' &&
    home.state.result.envelope.value.counts.enabled > 0 &&
    approvalStable &&
    approvalCandidates.state.kind === 'ready' &&
    !approvalPending;
  const planningReady =
    peopleReady && messageReady && policyReady && approvalReady;
  const step = planningReady ? 4 : 3;
  const nextRoute: JourneyRoute = !peopleReady
    ? { kind: 'people' }
    : !messageReady
    ? { kind: 'message' }
    : !policyReady
    ? { kind: 'schedule', returnTo: 'overview' }
    : !approvalReady
    ? { kind: 'approvals' }
    : { kind: 'automation' };
  const nextAction = !peopleReady
    ? 'live.guidedSetup.choosePeople'
    : !messageReady
    ? 'live.guidedSetup.writeMessage'
    : !policyReady
    ? 'live.guidedSetup.chooseWindow'
    : !approvalReady
    ? 'live.guidedSetup.reviewApprovals'
    : capability.platform === 'android'
    ? 'live.guidedSetup.testAndEnable'
    : 'live.guidedSetup.enableReminders';

  const refreshProgress = useCallback(async () => {
    await Promise.all([
      home.reload(),
      message.reload(),
      policy.reload(),
      approvalCandidates.reload(),
      onRefreshSetup(),
    ]);
  }, [approvalCandidates, home, message, onRefreshSetup, policy]);

  const returnToOverview = useCallback(() => {
    setRoute({ kind: 'overview' });
    refreshProgress().catch(() => undefined);
  }, [refreshProgress]);

  useEffect(() => {
    if (route.kind === 'overview') return undefined;
    const subscription = BackHandler.addEventListener(
      'hardwareBackPress',
      () => {
        if (route.kind === 'person') {
          setRoute({ kind: 'people' });
        } else if (route.kind === 'schedule') {
          if (route.returnTo === 'automation') {
            setRoute({ kind: 'automation' });
          } else {
            returnToOverview();
          }
        } else {
          returnToOverview();
        }
        return true;
      },
    );
    return () => subscription.remove();
  }, [returnToOverview, route]);

  const announcement =
    route.kind === 'overview'
      ? t('live.guidedSetup.title')
      : route.kind === 'people'
      ? t('live.people.title')
      : route.kind === 'person'
      ? t('live.person.detailsTitle')
      : route.kind === 'message'
      ? t('live.message.title')
      : route.kind === 'schedule'
      ? t('live.settings.schedule')
      : route.kind === 'approvals'
      ? t('live.guidedSetup.approvalTitle')
      : t('live.automation.title');

  let content: React.ReactNode;
  if (route.kind === 'people') {
    content = (
      <LivePeopleScreen
        onBack={returnToOverview}
        onOpenPerson={contactId => setRoute({ kind: 'person', contactId })}
        port={port}
      />
    );
  } else if (route.kind === 'person') {
    content = (
      <LivePersonDetailScreen
        capability={capability}
        contactId={route.contactId}
        onBack={() => setRoute({ kind: 'people' })}
        port={port}
      />
    );
  } else if (route.kind === 'message') {
    content = <LiveMessageScreen onBack={returnToOverview} port={port} />;
  } else if (route.kind === 'schedule') {
    content = (
      <LiveScheduleScreen
        onBack={() => {
          if (route.returnTo === 'automation') {
            setRoute({ kind: 'automation' });
          } else {
            returnToOverview();
          }
        }}
        platform={capability.platform}
        port={port}
      />
    );
  } else if (route.kind === 'approvals') {
    content = (
      <LiveBatchApprovalScreen
        capability={capability}
        onBack={returnToOverview}
        port={port}
      />
    );
  } else if (route.kind === 'automation') {
    content = (
      <LiveAutomationScreen
        capability={capability}
        onBack={returnToOverview}
        onOpenMessage={() => setRoute({ kind: 'message' })}
        onOpenSchedule={() =>
          setRoute({ kind: 'schedule', returnTo: 'automation' })
        }
        port={port}
      />
    );
  } else {
    const progressUnavailable =
      home.state.kind === 'error' ||
      message.state.kind === 'error' ||
      policy.state.kind === 'error' ||
      (home.state.kind === 'ready' &&
        home.state.refreshProblem !== undefined) ||
      (message.state.kind === 'ready' &&
        message.state.refreshProblem !== undefined) ||
      (policy.state.kind === 'ready' &&
        policy.state.refreshProblem !== undefined) ||
      projectionRevisionMismatch;
    const approvalProgressUnavailable =
      approvalCandidates.state.kind === 'error' ||
      (approvalCandidates.state.kind === 'ready' &&
        approvalCandidates.state.refreshProblem !== undefined);
    const progressLoading =
      home.state.kind === 'loading' ||
      message.state.kind === 'loading' ||
      policy.state.kind === 'loading' ||
      (home.state.kind === 'ready' && home.state.refreshing) ||
      (message.state.kind === 'ready' && message.state.refreshing) ||
      (policy.state.kind === 'ready' && policy.state.refreshing);
    const approvalProgressLoading =
      approvalCandidates.state.kind === 'loading' ||
      (approvalCandidates.state.kind === 'ready' &&
        approvalCandidates.state.refreshing);
    const currentTaskUnavailable =
      progressUnavailable || approvalProgressUnavailable;
    const currentTaskLoading = progressLoading || approvalProgressLoading;
    content = (
      <Screen includeTopInset testID="live-product-setup-journey">
        <AppText variant="title" accessibilityRole="header">
          {t('live.guidedSetup.title')}
        </AppText>
        <AppText color="muted">{t('live.guidedSetup.body')}</AppText>
        <ReadinessBanner
          title={t('live.guidedSetup.step', { step })}
          detail={t(
            step === 3
              ? 'live.guidedSetup.stepThreeBody'
              : capability.platform === 'android'
              ? 'live.guidedSetup.androidStepFourBody'
              : 'live.guidedSetup.iosStepFourBody',
          )}
          tone="info"
          testID="live-product-setup-current-step"
        />

        <Card testID="live-product-setup-progress-summary">
          <StatusRow
            title={t('live.guidedSetup.completedWork')}
            detail={t(
              step === 3
                ? 'live.guidedSetup.completedThroughTwo'
                : 'live.guidedSetup.completedThroughThree',
            )}
            tone="positive"
          />
          <StatusRow
            title={t('live.guidedSetup.currentTask')}
            detail={t(
              currentTaskUnavailable
                ? 'live.common.unavailable'
                : currentTaskLoading
                ? 'live.common.checkingState'
                : nextAction,
            )}
            tone={
              currentTaskUnavailable
                ? 'critical'
                : currentTaskLoading
                ? 'info'
                : 'warning'
            }
          />
        </Card>

        {progressLoading || approvalProgressLoading ? (
          <LiveLoading label={t('live.guidedSetup.checking')} />
        ) : null}
        {progressUnavailable || approvalProgressUnavailable ? (
          <LiveError
            title={t('live.guidedSetup.progressUnavailable')}
            problem={
              home.state.kind === 'error'
                ? home.state.problem
                : home.state.kind === 'ready' && home.state.refreshProblem
                ? home.state.refreshProblem
                : message.state.kind === 'error'
                ? message.state.problem
                : message.state.kind === 'ready' && message.state.refreshProblem
                ? message.state.refreshProblem
                : policy.state.kind === 'error'
                ? policy.state.problem
                : policy.state.kind === 'ready' && policy.state.refreshProblem
                ? policy.state.refreshProblem
                : approvalCandidates.state.kind === 'error'
                ? approvalCandidates.state.problem
                : approvalCandidates.state.kind === 'ready' &&
                  approvalCandidates.state.refreshProblem
                ? approvalCandidates.state.refreshProblem
                : {
                    kind: 'conflict',
                    code: 'unknown-native-value',
                  }
            }
            onRetry={refreshProgress}
          />
        ) : null}

        <Button
          disabled={
            progressLoading ||
            approvalProgressLoading ||
            progressUnavailable ||
            approvalProgressUnavailable
          }
          label={t(nextAction)}
          onPress={() => setRoute(nextRoute)}
          testID="live-product-setup-next"
        />
        <Button
          label={t('live.guidedSetup.finishLater')}
          onPress={onDefer}
          variant="ghost"
          testID="live-product-setup-defer"
        />
        <AppText color="muted" variant="caption">
          {t('live.guidedSetup.savedProgress')}
        </AppText>
      </Screen>
    );
  }

  return (
    <View style={styles.root} testID="live-product-setup-root">
      <RouteAccessibilityFocus
        announcement={announcement}
        routeKey={routeKey(route)}
      />
      {content}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
});
