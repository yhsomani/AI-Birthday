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
import { spacing } from '../../design-system/tokens/theme';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import type { LiveAppPort, LiveCompanionPort } from './LiveAppPort';
import { LiveAutomationScreen } from './LiveAutomationScreen';
import { LiveBatchApprovalScreen } from './LiveBatchApprovalScreen';
import { LiveError, LiveLoading } from './LiveProjectionState';
import { LiveMessageScreen } from './LiveMessageScreen';
import { LivePeopleScreen } from './LivePeopleScreen';
import { LivePersonDetailScreen } from './LivePersonDetailScreen';
import { needsBatchApproval, scanPeoplePages } from './peoplePagination';
import { useLiveProjection } from './useLiveProjection';

type JourneyRoute =
  | Readonly<{ kind: 'overview' }>
  | Readonly<{ kind: 'people' }>
  | Readonly<{ kind: 'person'; contactId: ContactId }>
  | Readonly<{ kind: 'message' }>
  | Readonly<{ kind: 'approvals' }>
  | Readonly<{ kind: 'automation' }>;

const routeKey = (route: JourneyRoute): string =>
  route.kind === 'person' ? `person:${route.contactId}` : route.kind;

export function LiveProductSetupJourney({
  capability,
  companionPort,
  onDefer,
  onRefreshSetup,
  port,
}: {
  capability: PlatformCapability;
  companionPort: LiveCompanionPort;
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

  const peopleReady =
    home.state.kind === 'ready' &&
    (home.state.result.envelope.value.counts.configured ??
      home.state.result.envelope.value.counts.enabled) > 0;
  const messageReady =
    message.state.kind === 'ready' &&
    message.state.result.envelope.value.kind === 'configured';
  const policyReady =
    policy.state.kind === 'ready' &&
    policy.state.result.envelope.value.kind === 'configured';
  const approvalPending =
    approvalCandidates.state.kind === 'ready' &&
    approvalCandidates.state.result.envelope.value.contactIds.length > 0;
  const approvalReady =
    peopleReady &&
    home.state.kind === 'ready' &&
    home.state.result.envelope.value.counts.enabled > 0 &&
    approvalCandidates.state.kind === 'ready' &&
    approvalCandidates.state.refreshProblem === undefined &&
    !approvalPending;
  const planningReady =
    peopleReady && messageReady && policyReady && approvalReady;
  const step = planningReady ? 4 : 3;
  const nextRoute: JourneyRoute = !peopleReady
    ? { kind: 'people' }
    : !messageReady
    ? { kind: 'message' }
    : !policyReady
    ? { kind: 'automation' }
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
        } else {
          returnToOverview();
        }
        return true;
      },
    );
    return () => subscription.remove();
  }, [returnToOverview, route.kind]);

  const announcement =
    route.kind === 'overview'
      ? t('live.guidedSetup.title')
      : route.kind === 'people'
      ? t('live.people.title')
      : route.kind === 'person'
      ? t('live.person.detailsTitle')
      : route.kind === 'message'
      ? t('live.message.title')
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
        companionPort={companionPort}
        onBack={returnToOverview}
        port={port}
      />
    );
  } else {
    const progressUnavailable =
      home.state.kind === 'error' ||
      message.state.kind === 'error' ||
      policy.state.kind === 'error';
    const approvalProgressUnavailable =
      approvalCandidates.state.kind === 'error' ||
      (approvalCandidates.state.kind === 'ready' &&
        approvalCandidates.state.refreshProblem !== undefined);
    const progressLoading =
      home.state.kind === 'loading' ||
      message.state.kind === 'loading' ||
      policy.state.kind === 'loading';
    const approvalProgressLoading = approvalCandidates.state.kind === 'loading';
    const progressStatus = (
      kind: 'error' | 'loading' | 'ready',
      ready: boolean,
      neededKey:
        | 'live.guidedSetup.peopleNeeded'
        | 'live.guidedSetup.messageNeeded'
        | 'live.guidedSetup.windowNeeded'
        | 'live.guidedSetup.approvalNeeded',
    ) => ({
      detail: t(
        kind === 'loading'
          ? 'live.common.checkingState'
          : kind === 'error'
          ? 'live.common.unavailable'
          : ready
          ? 'live.guidedSetup.ready'
          : neededKey,
      ),
      tone:
        kind === 'loading'
          ? ('info' as const)
          : kind === 'error'
          ? ('critical' as const)
          : ready
          ? ('positive' as const)
          : ('warning' as const),
    });
    const peopleStatus = progressStatus(
      home.state.kind,
      peopleReady,
      'live.guidedSetup.peopleNeeded',
    );
    const messageStatus = progressStatus(
      message.state.kind,
      messageReady,
      'live.guidedSetup.messageNeeded',
    );
    const policyStatus = progressStatus(
      policy.state.kind,
      policyReady,
      'live.guidedSetup.windowNeeded',
    );
    const approvalStatus = progressStatus(
      approvalCandidates.state.kind,
      approvalReady,
      'live.guidedSetup.approvalNeeded',
    );
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

        <Card>
          <StatusRow
            title={t('live.guidedSetup.stepOne')}
            detail={t('live.guidedSetup.complete')}
            tone="positive"
          />
          <StatusRow
            title={t('live.guidedSetup.stepTwo')}
            detail={t('live.guidedSetup.complete')}
            tone="positive"
          />
          <StatusRow
            title={t('live.guidedSetup.people')}
            detail={peopleStatus.detail}
            tone={peopleStatus.tone}
          />
          <StatusRow
            title={t('live.guidedSetup.message')}
            detail={messageStatus.detail}
            tone={messageStatus.tone}
          />
          <StatusRow
            title={t('live.guidedSetup.window')}
            detail={policyStatus.detail}
            tone={policyStatus.tone}
          />
          <StatusRow
            title={t('live.guidedSetup.approvals')}
            detail={approvalStatus.detail}
            tone={approvalStatus.tone}
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
                : message.state.kind === 'error'
                ? message.state.problem
                : policy.state.kind === 'error'
                ? policy.state.problem
                : approvalCandidates.state.kind === 'error'
                ? approvalCandidates.state.problem
                : approvalCandidates.state.kind === 'ready' &&
                  approvalCandidates.state.refreshProblem
                ? approvalCandidates.state.refreshProblem
                : { kind: 'conflict', code: 'unknown-native-value' }
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
        <View style={styles.secondaryActions}>
          {nextRoute.kind !== 'people' ? (
            <Button
              label={t('live.guidedSetup.choosePeople')}
              onPress={() => setRoute({ kind: 'people' })}
              variant="secondary"
              testID="live-product-setup-people"
            />
          ) : null}
          {nextRoute.kind !== 'message' ? (
            <Button
              label={t('live.guidedSetup.writeMessage')}
              onPress={() => setRoute({ kind: 'message' })}
              variant="secondary"
              testID="live-product-setup-message"
            />
          ) : null}
          {nextRoute.kind !== 'automation' ? (
            <Button
              label={t('live.guidedSetup.openFinalStep')}
              onPress={() => setRoute({ kind: 'automation' })}
              variant="secondary"
              testID="live-product-setup-automation"
            />
          ) : null}
          {policyReady && nextRoute.kind !== 'approvals' ? (
            <Button
              label={t('live.guidedSetup.reviewApprovals')}
              onPress={() => setRoute({ kind: 'approvals' })}
              variant="secondary"
              testID="live-product-setup-approvals"
            />
          ) : null}
        </View>
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
  secondaryActions: { gap: spacing.sm },
});
