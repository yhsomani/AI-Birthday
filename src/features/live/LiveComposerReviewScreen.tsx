import React, { useCallback, useEffect, useState } from 'react';
import { AppState } from 'react-native';

import type { PlatformCapability } from '../../domain/shared/platform';
import type { NativeProblem } from '../../domain/shared/result';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  InlineReviewCard,
  KeyValue,
  ReadinessBanner,
  Screen,
  StatusRow,
} from '../../design-system/components/Primitives';
import type {
  CompanionComposerOutcome,
  CompanionComposerReviewProjection,
} from '../../infrastructure/native/ios/CompanionNativeGateway';
import { formatLiveDate } from '../../localization/formatLive';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { safeReasonMessageKey } from '../../localization/reasonCopy';
import type { LiveAppPort, LiveCompanionPort } from './LiveAppPort';
import {
  LiveActionFeedback,
  LiveError,
  LiveLoading,
  LiveRefreshProblem,
} from './LiveProjectionState';
import {
  composerErrorCanRepairContacts,
  composerErrorMessageKey,
} from './composerErrorCopy';
import {
  nativeBridgeProblem,
  nativePlatformMismatchProblem,
} from './nativeProblem';
import { useLiveProjection } from './useLiveProjection';

const isCurrentOrNewerRevision = (
  candidate: string,
  current: string,
): boolean =>
  candidate.length > current.length ||
  (candidate.length === current.length && candidate >= current);

function LiveComposerReviewFrame({
  children,
  onBack,
}: React.PropsWithChildren<{ onBack: () => void }>) {
  const { t } = useAppLocalization();
  return (
    <Screen includeTopInset testID="live-composer-review-screen">
      <Button
        label={t('live.common.back')}
        onPress={onBack}
        variant="ghost"
        testID="live-composer-review-back"
      />
      <AppText variant="title" accessibilityRole="header">
        {t('live.companion.composerTitle')}
      </AppText>
      <AppText color="muted">{t('live.companion.composerScreenBody')}</AppText>
      {children}
    </Screen>
  );
}

function LiveIosComposerReview({
  companionPort,
  onBack,
  port,
}: {
  companionPort: LiveCompanionPort;
  onBack: () => void;
  port: LiveAppPort;
}) {
  const { language, t } = useAppLocalization();
  const loadHome = useCallback(() => port.getHome(), [port]);
  const home = useLiveProjection(loadHome, port, [
    'home',
    'automation',
    'readiness',
  ]);
  const loadProposal = useCallback(
    () => port.getNextComposerProposal(),
    [port],
  );
  const proposal = useLiveProjection(loadProposal, port, [
    'messages',
    'automation',
  ]);
  const [review, setReview] = useState<CompanionComposerReviewProjection>();
  const [composerOutcome, setComposerOutcome] =
    useState<CompanionComposerOutcome>();
  const [composerError, setComposerError] = useState<string>();
  const [actionProblem, setActionProblem] = useState<NativeProblem>();
  const [pending, setPending] = useState<
    'review' | 'open' | 'contacts-repair'
  >();
  const [message, setMessage] = useState<string>();
  const [supportExpanded, setSupportExpanded] = useState(false);

  const setProtectedComposerError = useCallback(
    (nextError: string | undefined) => {
      setSupportExpanded(false);
      setComposerError(nextError);
    },
    [],
  );

  const handleBack = useCallback(() => {
    setSupportExpanded(false);
    onBack();
  }, [onBack]);

  useEffect(
    () =>
      port.subscribeInvalidations(() => {
        setSupportExpanded(false);
      }),
    [port],
  );

  useEffect(() => {
    const subscription = AppState.addEventListener('change', () => {
      setSupportExpanded(false);
    });
    return () => subscription.remove();
  }, []);

  const iosAutomation =
    home.state.kind === 'ready' &&
    home.state.result.envelope.value.automation.platform === 'ios'
      ? home.state.result.envelope.value.automation
      : undefined;
  const composerIssues =
    iosAutomation?.readiness.composer.kind === 'blocked'
      ? iosAutomation.readiness.composer.issues
      : [];
  const composerAllowed = iosAutomation?.readiness.composer.kind === 'allowed';
  const managedByAndroid = composerIssues.some(
    issue => issue.code === 'active-sender-other-device',
  );
  const safetyStatusUnavailable = composerIssues.some(
    issue =>
      issue.code === 'coordination-unavailable' ||
      issue.code === 'firebase-account-deleting',
  );

  useEffect(() => {
    if (!review || proposal.state.kind !== 'ready') {
      return;
    }
    const envelope = proposal.state.result.envelope;
    if (
      envelope.value.kind !== 'ready' ||
      !isCurrentOrNewerRevision(review.revision, envelope.revision) ||
      envelope.value.proposalId !== review.proposalId
    ) {
      setReview(undefined);
      setProtectedComposerError('COMPOSER_REVIEW_STALE');
    }
  }, [proposal.state, review, setProtectedComposerError]);

  useEffect(() => {
    if (!review || composerAllowed) {
      return;
    }
    setReview(undefined);
    if (iosAutomation?.readiness.composer.kind === 'blocked') {
      setProtectedComposerError('COMPOSER_REVIEW_BLOCKED');
    }
  }, [composerAllowed, iosAutomation, review, setProtectedComposerError]);

  const prepareComposer = async () => {
    if (
      !composerAllowed ||
      proposal.state.kind !== 'ready' ||
      proposal.state.result.envelope.value.kind !== 'ready'
    ) {
      return;
    }
    setPending('review');
    setProtectedComposerError(undefined);
    setComposerOutcome(undefined);
    const envelope = proposal.state.result.envelope;
    const proposalValue = envelope.value;
    if (proposalValue.kind !== 'ready') {
      setPending(undefined);
      return;
    }
    let result: Awaited<ReturnType<LiveCompanionPort['prepareComposerReview']>>;
    try {
      result = await companionPort.prepareComposerReview({
        expectedRevision: envelope.revision,
        proposalId: proposalValue.proposalId,
      });
    } catch {
      setProtectedComposerError('COMPOSER_NATIVE_FAILURE');
      setPending(undefined);
      return;
    }
    if (result.kind === 'error') {
      setProtectedComposerError(result.code);
      setPending(undefined);
      return;
    }
    const matches =
      result.value.proposalId === proposalValue.proposalId &&
      isCurrentOrNewerRevision(result.value.revision, envelope.revision) &&
      result.value.expiresAtEpochMilliseconds > Date.now();
    if (!matches) {
      setReview(undefined);
      setProtectedComposerError('COMPOSER_REVIEW_MISMATCH');
      setPending(undefined);
      return;
    }
    setReview(result.value);
    setPending(undefined);
  };

  const repairComposerContacts = async () => {
    if (
      composerError !== 'COMPOSER_CONTACTS_RECONNECT_REQUIRED' &&
      composerError !== 'COMPOSER_CONTACTS_FRESHNESS_UNAVAILABLE'
    ) {
      return;
    }
    setSupportExpanded(false);
    setPending('contacts-repair');
    setActionProblem(undefined);
    setMessage(undefined);
    try {
      const result =
        composerError === 'COMPOSER_CONTACTS_RECONNECT_REQUIRED'
          ? await port.continueWithGoogle()
          : await port.syncContacts('user');
      if (result.kind === 'error') {
        setActionProblem(result.problem);
        setPending(undefined);
        return;
      }
      await Promise.all([home.reload(), proposal.reload()]);
      setProtectedComposerError(undefined);
      setMessage(t('live.companion.contactsRepairAccepted'));
    } catch {
      setActionProblem(nativeBridgeProblem);
    }
    setPending(undefined);
  };

  const openComposer = async () => {
    if (!review || !composerAllowed) {
      return;
    }
    if (review.expiresAtEpochMilliseconds <= Date.now()) {
      setReview(undefined);
      setProtectedComposerError('COMPOSER_REVIEW_EXPIRED');
      return;
    }
    setPending('open');
    setProtectedComposerError(undefined);
    let available = false;
    try {
      available = await companionPort.canOpenComposer();
    } catch {
      setProtectedComposerError('COMPOSER_NATIVE_FAILURE');
      setPending(undefined);
      return;
    }
    if (!available) {
      setProtectedComposerError('COMPOSER_UNAVAILABLE');
      setPending(undefined);
      return;
    }
    let result: Awaited<
      ReturnType<LiveCompanionPort['openUserConfirmedComposer']>
    >;
    try {
      result = await companionPort.openUserConfirmedComposer({
        actionNonce: review.actionNonce,
        expectedRevision: review.revision,
        proposalId: review.proposalId,
      });
    } catch {
      setProtectedComposerError('COMPOSER_NATIVE_FAILURE');
      setPending(undefined);
      return;
    }
    if (result.kind === 'error') {
      setProtectedComposerError(result.code);
    } else {
      setComposerOutcome(result.value);
      setReview(undefined);
      await proposal.reload();
    }
    setPending(undefined);
  };

  const outcomeKey = composerOutcome
    ? (
        {
          cancelled: 'live.companion.cancelled',
          failed: 'live.companion.failed',
          'reported-sent': 'live.companion.reportedSent',
          unknown: 'live.companion.unknown',
        } as const
      )[composerOutcome]
    : undefined;
  const composerErrorKey = composerError
    ? composerErrorMessageKey(composerError)
    : undefined;
  const composerContactRepairable = composerError
    ? composerErrorCanRepairContacts(composerError)
    : false;

  return (
    <LiveComposerReviewFrame onBack={handleBack}>
      <LiveActionFeedback problem={actionProblem} message={message} />
      {home.state.kind === 'loading' ? (
        <LiveLoading label={t('live.automation.loading')} />
      ) : null}
      {home.state.kind === 'error' ? (
        <LiveError
          title={t('live.automation.unavailable')}
          problem={home.state.problem}
          onRetry={() => home.reload()}
        />
      ) : null}
      {home.state.kind === 'ready' && !iosAutomation ? (
        <LiveError
          title={t('live.home.platformMismatch')}
          problem={nativePlatformMismatchProblem}
          onRetry={() => home.reload()}
        />
      ) : null}
      {home.state.kind === 'ready' && iosAutomation ? (
        <>
          {home.state.refreshProblem ? (
            <LiveRefreshProblem problem={home.state.refreshProblem} />
          ) : null}
          {!composerAllowed ? (
            <Card>
              <StatusRow
                title={t('live.companion.composerBlocked')}
                tone="warning"
              />
              {composerIssues.map(issue => (
                <StatusRow
                  key={issue.id}
                  title={t(safeReasonMessageKey(issue.code))}
                  tone={issue.severity === 'blocking' ? 'critical' : 'warning'}
                />
              ))}
            </Card>
          ) : null}
          {managedByAndroid ? (
            <ReadinessBanner
              title={t('live.companion.managedByAndroid')}
              detail={t('live.companion.managedByAndroidBody')}
              tone="info"
            />
          ) : null}
          {safetyStatusUnavailable ? (
            <ReadinessBanner
              title={t('live.companion.safetyUnavailable')}
              detail={t('live.companion.safetyUnavailableBody')}
              tone="warning"
            />
          ) : null}
          {composerAllowed ? (
            <>
              {proposal.state.kind === 'loading' ? (
                <LiveLoading label={t('live.companion.reviewing')} />
              ) : null}
              {proposal.state.kind === 'error' ? (
                <LiveError
                  title={t('live.companion.proposalUnavailable')}
                  problem={proposal.state.problem}
                  onRetry={() => proposal.reload()}
                />
              ) : null}
              {proposal.state.kind === 'ready' &&
              proposal.state.result.envelope.value.kind === 'none' ? (
                <Card>
                  <AppText>{t('live.companion.noProposal')}</AppText>
                </Card>
              ) : null}
              {proposal.state.kind === 'ready' &&
              proposal.state.result.envelope.value.kind === 'ready' ? (
                <Card>
                  <KeyValue
                    label={t('live.companion.recipient')}
                    value={proposal.state.result.envelope.value.recipient}
                  />
                  <KeyValue
                    label={t('live.companion.date')}
                    value={formatLiveDate(
                      proposal.state.result.envelope.value.occurrenceDate,
                      language,
                    )}
                  />
                  {!review && !composerContactRepairable ? (
                    <Button
                      label={
                        pending === 'review'
                          ? t('live.companion.reviewing')
                          : t('live.companion.prepareReview')
                      }
                      disabled={pending !== undefined}
                      onPress={prepareComposer}
                      testID="live-prepare-composer"
                    />
                  ) : null}
                </Card>
              ) : null}
              {review ? (
                <InlineReviewCard
                  reviewKey={`${review.proposalId}:${review.revision}`}
                  testID="live-ios-composer-review"
                  title={t('live.companion.reviewTitle')}
                >
                  <KeyValue
                    label={t('live.companion.destination')}
                    value={review.maskedDestination}
                  />
                  <AppText>{review.body}</AppText>
                  <ReadinessBanner
                    title={t('live.common.iosEdition')}
                    detail={t('live.companion.editableWarning')}
                    testID="live-composer-final-disclosure"
                    tone="warning"
                  />
                  {!composerContactRepairable ? (
                    <Button
                      label={t('live.companion.openComposer')}
                      disabled={pending !== undefined}
                      onPress={openComposer}
                      testID="live-open-composer"
                    />
                  ) : null}
                  <Button
                    label={t('live.common.cancel')}
                    onPress={() => {
                      setSupportExpanded(false);
                      setReview(undefined);
                    }}
                    variant="secondary"
                  />
                </InlineReviewCard>
              ) : null}
            </>
          ) : null}
        </>
      ) : null}
      {composerError ? (
        <>
          <Card>
            <StatusRow title={t('live.companion.error')} tone="critical" />
            {composerErrorKey ? <AppText>{t(composerErrorKey)}</AppText> : null}
            {composerContactRepairable ? (
              <Button
                label={
                  pending === 'contacts-repair'
                    ? t('live.common.checking')
                    : t(
                        composerError === 'COMPOSER_CONTACTS_RECONNECT_REQUIRED'
                          ? 'live.companion.reconnectContacts'
                          : 'live.people.syncNow',
                      )
                }
                disabled={pending !== undefined}
                onPress={repairComposerContacts}
                testID="live-composer-repair-contacts"
              />
            ) : null}
            <Button
              label={t(
                supportExpanded
                  ? 'live.attention.hideSupportDetails'
                  : 'live.attention.showSupportDetails',
              )}
              disabled={pending !== undefined}
              expanded={supportExpanded}
              onPress={() => setSupportExpanded(expanded => !expanded)}
              variant="secondary"
              testID="live-composer-support-toggle"
            />
          </Card>
          {supportExpanded ? (
            <Card testID="live-composer-support-details">
              <AppText color="muted">
                {t('live.attention.supportDetailsBody')}
              </AppText>
              <AppText color="muted" variant="caption">
                {t('live.common.code', { value: composerError })}
              </AppText>
            </Card>
          ) : null}
        </>
      ) : null}
      {outcomeKey ? (
        <ReadinessBanner
          title={t(outcomeKey)}
          detail={t('live.companion.postComposerSafety')}
          testID="live-composer-post-safety"
          tone="warning"
        />
      ) : null}
    </LiveComposerReviewFrame>
  );
}

export function LiveComposerReviewScreen({
  capability,
  companionPort,
  onBack,
  port,
}: {
  capability: PlatformCapability;
  companionPort: LiveCompanionPort;
  onBack: () => void;
  port: LiveAppPort;
}) {
  const { t } = useAppLocalization();
  if (capability.platform === 'android') {
    return (
      <LiveComposerReviewFrame onBack={onBack}>
        <ReadinessBanner
          title={t('live.common.iosEdition')}
          detail={t('live.automation.iosBody')}
          tone="info"
        />
      </LiveComposerReviewFrame>
    );
  }

  return (
    <LiveIosComposerReview
      companionPort={companionPort}
      onBack={onBack}
      port={port}
    />
  );
}
