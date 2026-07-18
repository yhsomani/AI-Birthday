import React, { useCallback, useEffect, useState } from 'react';
import { AppState, StyleSheet } from 'react-native';

import type {
  ActivationReview,
  AndroidTestPhase,
  TestReview,
} from '../../domain/automation/model';
import type { NativeProblem } from '../../domain/shared/result';
import type { PlatformCapability } from '../../domain/shared/platform';
import { validateEphemeralPhoneInput } from '../../domain/validation/ephemeralPhone';
import { AccessibleTextInput } from '../../design-system/components/AccessibleTextInput';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  InlineReviewCard,
  KeyValue,
  ReadinessBanner,
  Screen,
  SectionHeading,
  StatusRow,
} from '../../design-system/components/Primitives';
import {
  minimumTargetSize,
  radii,
  spacing,
} from '../../design-system/tokens/theme';
import { useAppTheme } from '../../app/providers/ThemeProvider';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { formatLiveDate } from '../../localization/formatLive';
import { safeReasonMessageKey } from '../../localization/reasonCopy';
import type { TranslationKey } from '../../localization/resources';
import type {
  CompanionComposerReviewProjection,
  CompanionComposerOutcome,
  CompanionReminderState,
} from '../../infrastructure/native/ios/CompanionNativeGateway';
import type { LiveAppPort, LiveCompanionPort } from './LiveAppPort';
import {
  LiveActionFeedback,
  LiveError,
  LiveLoading,
  LiveRefreshProblem,
  LiveValidationError,
} from './LiveProjectionState';
import { LivePolicyEditor } from './LivePolicyEditor';
import {
  nativeBridgeProblem,
  nativePlatformMismatchProblem,
} from './nativeProblem';
import { useLiveProjection } from './useLiveProjection';
import {
  composerErrorCanRepairContacts,
  composerErrorMessageKey,
} from './composerErrorCopy';

type AndroidReview =
  | Readonly<{
      kind: 'test';
      review: Extract<TestReview, { platform: 'android' }>;
      revision: import('../../domain/shared/brand').NativeRevision;
    }>
  | Readonly<{
      kind: 'activate' | 'resume';
      review: Extract<ActivationReview, { platform: 'android' }>;
      revision: import('../../domain/shared/brand').NativeRevision;
    }>;

type IosActivationReviewState = Readonly<{
  kind: 'activate' | 'resume';
  review: Extract<ActivationReview, { platform: 'ios' }>;
  revision: import('../../domain/shared/brand').NativeRevision;
}>;

type IosActivationReview = IosActivationReviewState['review'];

const iosReminderHorizonKeys: Record<
  IosActivationReview['reminderHorizon'],
  TranslationKey
> = {
  denied: 'live.companion.horizon.denied',
  full: 'live.companion.horizon.full',
  'not-built': 'live.companion.horizon.notBuilt',
  partial: 'live.companion.horizon.partial',
};

const iosCoexistenceKeys: Record<
  IosActivationReview['coexistence'],
  TranslationKey
> = {
  clear: 'live.companion.coexistence.clear',
  deleting: 'live.companion.coexistence.deleting',
  managed: 'live.companion.coexistence.managed',
  'stale-or-unknown': 'live.companion.coexistence.unverified',
  unavailable: 'live.companion.coexistence.unavailable',
};

const iosActivationSnapshotComplete = (review: IosActivationReview): boolean =>
  review.contactsReady &&
  review.messageUiReady &&
  review.protectedStorageReady &&
  (review.reminderHorizon === 'full' ||
    review.reminderHorizon === 'not-built') &&
  review.coexistence === 'clear' &&
  review.readiness.composer.kind === 'allowed';

const androidStateKeys: Record<string, TranslationKey> = {
  'not-configured': 'live.automation.state.notConfigured',
  'test-only': 'live.automation.state.testOnly',
  'paused-repair': 'live.automation.state.pausedRepair',
  active: 'live.automation.state.active',
  'action-required': 'live.automation.state.actionRequired',
  standby: 'live.automation.state.standby',
  'transfer-pending': 'live.automation.state.transferPending',
  deleting: 'live.automation.state.deleting',
};

const androidTestKeys: Record<AndroidTestPhase, TranslationKey> = {
  prepared: 'live.automation.test.preparing',
  'cloud-claimed': 'live.automation.test.inProgress',
  'arm-reconciling': 'live.automation.test.inProgress',
  'coordination-unknown': 'live.automation.test.unknown',
  'cloud-armed': 'live.automation.test.inProgress',
  'armed-suppressed': 'live.automation.test.blocked',
  'barrier-consumed': 'live.automation.test.inProgress',
  submitted: 'live.automation.test.submitted',
  'sent-from-device': 'live.automation.test.sent',
  passed: 'live.automation.test.passed',
  failed: 'live.automation.test.failed',
  'partial-unknown': 'live.automation.test.unknown',
  unknown: 'live.automation.test.unknown',
  'permanent-failure': 'live.automation.test.failed',
  'cleanup-cancelled': 'live.automation.test.blocked',
  'receipt-invalidated': 'live.automation.test.unknown',
};

const reminderPermissionKeys: Record<
  CompanionReminderState['authorization'],
  TranslationKey
> = {
  authorized: 'live.companion.permission.authorized',
  denied: 'live.companion.permission.denied',
  ephemeral: 'live.companion.permission.ephemeral',
  'not-determined': 'live.companion.permission.notDetermined',
  provisional: 'live.companion.permission.provisional',
  unknown: 'live.companion.permission.unknown',
};

const iosStateKeys: Record<string, TranslationKey> = {
  'not-configured': 'live.companion.state.notConfigured',
  ready: 'live.companion.state.ready',
  'action-required': 'live.companion.state.actionRequired',
  paused: 'live.companion.state.paused',
};

const isCurrentOrNewerRevision = (
  candidate: string,
  current: string,
): boolean =>
  candidate.length > current.length ||
  (candidate.length === current.length && candidate >= current);

function LiveAndroidAutomation({
  onBack,
  port,
}: {
  onBack: () => void;
  port: LiveAppPort;
}) {
  const { colors } = useAppTheme();
  const { t } = useAppLocalization();
  const loadHome = useCallback(() => port.getHome(), [port]);
  const loadTest = useCallback(() => port.getLatestTest(), [port]);
  const home = useLiveProjection(loadHome, port, ['home', 'automation']);
  const latestTest = useLiveProjection(loadTest, port, ['automation']);
  const [testPhone, setTestPhone] = useState('');
  const [review, setReview] = useState<AndroidReview>();
  const [confirmPause, setConfirmPause] = useState(false);
  const [pending, setPending] = useState<string>();
  const [problem, setProblem] = useState<NativeProblem>();
  const [message, setMessage] = useState<string>();
  const [localIssue, setLocalIssue] = useState<string>();

  useEffect(
    () =>
      port.subscribeInvalidations(event => {
        if (
          event.areas.includes('home') ||
          event.areas.includes('automation')
        ) {
          setReview(undefined);
          setConfirmPause(false);
        }
      }),
    [port],
  );

  const fail = async (actionProblem: NativeProblem) => {
    if (actionProblem.kind === 'stale-revision') {
      await home.reload();
      await latestTest.reload();
      setReview(undefined);
      setConfirmPause(false);
    }
    setProblem(actionProblem);
    setPending(undefined);
  };

  const prepareTest = async () => {
    if (home.state.kind !== 'ready') {
      return;
    }
    const validated = validateEphemeralPhoneInput(testPhone);
    if (validated.kind === 'invalid') {
      setLocalIssue(t(safeReasonMessageKey(validated.issues[0]!.code)));
      return;
    }
    setPending('prepare-test');
    setProblem(undefined);
    setMessage(undefined);
    setLocalIssue(undefined);
    let result: Awaited<ReturnType<LiveAppPort['prepareTest']>>;
    try {
      result = await port.prepareTest({
        destination: validated.value,
        expectedRevision: home.state.result.envelope.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      await fail(result.problem);
      return;
    }
    if (result.envelope.value.platform !== 'android') {
      await fail(nativePlatformMismatchProblem);
      return;
    }
    setReview({
      kind: 'test',
      review: result.envelope.value,
      revision: result.envelope.revision,
    });
    setPending(undefined);
  };

  const startTest = async () => {
    if (!review || review.kind !== 'test') {
      return;
    }
    setPending('start-test');
    setProblem(undefined);
    let result: Awaited<ReturnType<LiveAppPort['startTest']>>;
    try {
      result = await port.startTest({
        handle: review.review.handle,
        expectedRevision: review.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      await fail(result.problem);
      return;
    }
    await latestTest.reload();
    await home.reload();
    setReview(undefined);
    setMessage(t('live.automation.testStarted'));
    setPending(undefined);
  };

  const prepareActivationReview = async (kind: 'activate' | 'resume') => {
    setPending(kind);
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['prepareActivation']>>;
    try {
      result =
        kind === 'activate'
          ? await port.prepareActivation()
          : await port.prepareResume();
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      await fail(result.problem);
      return;
    }
    if (result.envelope.value.platform !== 'android') {
      await fail(nativePlatformMismatchProblem);
      return;
    }
    setReview({
      kind,
      review: result.envelope.value,
      revision: result.envelope.revision,
    });
    setPending(undefined);
  };

  const confirmActivationReview = async () => {
    if (!review || review.kind === 'test') {
      return;
    }
    setPending(review.kind);
    setProblem(undefined);
    let result: Awaited<ReturnType<LiveAppPort['activate']>>;
    try {
      result =
        review.kind === 'activate'
          ? await port.activate({
              handle: review.review.handle,
              expectedRevision: review.revision,
            })
          : await port.resume({
              handle: review.review.handle,
              expectedRevision: review.revision,
            });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      await fail(result.problem);
      return;
    }
    await home.reload();
    setReview(undefined);
    setMessage(
      t(
        review.kind === 'activate'
          ? 'live.automation.activationAccepted'
          : 'live.automation.resumeAccepted',
      ),
    );
    setPending(undefined);
  };

  const pauseAll = async () => {
    if (home.state.kind !== 'ready') {
      return;
    }
    setPending('pause');
    setProblem(undefined);
    let result: Awaited<ReturnType<LiveAppPort['pauseAll']>>;
    try {
      result = await port.pauseAll({
        expectedRevision: home.state.result.envelope.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      await fail(result.problem);
      return;
    }
    await home.reload();
    setConfirmPause(false);
    setMessage(t('live.automation.pauseAccepted'));
    setPending(undefined);
  };

  if (
    home.state.kind === 'ready' &&
    home.state.result.envelope.value.automation.platform !== 'android'
  ) {
    return (
      <Screen includeTopInset testID="live-automation-screen">
        <Button
          label={t('live.common.back')}
          onPress={onBack}
          variant="ghost"
        />
        <LiveError
          title={t('live.home.platformMismatch')}
          problem={nativePlatformMismatchProblem}
          onRetry={() => home.reload()}
        />
      </Screen>
    );
  }

  return (
    <Screen includeTopInset testID="live-automation-screen">
      <Button
        label={t('live.common.back')}
        onPress={onBack}
        variant="ghost"
        testID="live-automation-back"
      />
      <AppText variant="title" accessibilityRole="header">
        {t('live.automation.title')}
      </AppText>
      <AppText color="muted">{t('live.automation.androidBody')}</AppText>
      <LiveActionFeedback problem={problem} message={message} />
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
      {home.state.kind === 'ready' ? (
        <>
          {home.state.refreshProblem ? (
            <LiveRefreshProblem problem={home.state.refreshProblem} />
          ) : null}
          <Card>
            <StatusRow
              title={t('live.automation.current')}
              detail={t(
                androidStateKeys[
                  home.state.result.envelope.value.automation.effective
                ] ?? 'live.common.unavailable',
              )}
              tone="info"
            />
          </Card>

          <LivePolicyEditor platform="android" port={port} />

          <SectionHeading title={t('live.automation.testTitle')} />
          <AccessibleTextInput
            accessibilityLabel={t('live.automation.testPhone')}
            accessibilityHint={t('live.automation.testPhoneHint')}
            keyboardType="phone-pad"
            maxLength={32}
            onChangeText={setTestPhone}
            style={[
              styles.input,
              {
                backgroundColor: colors.surface,
                borderColor: colors.border,
                color: colors.text,
              },
            ]}
            testID="live-test-phone"
            value={testPhone}
          />
          {localIssue ? (
            <LiveValidationError
              message={localIssue}
              testID="live-test-phone-validation"
            />
          ) : null}
          <Button
            label={
              pending === 'prepare-test'
                ? t('live.common.checking')
                : t('live.automation.prepareTest')
            }
            disabled={pending !== undefined}
            onPress={prepareTest}
            testID="live-prepare-test"
          />
          {latestTest.state.kind === 'ready' &&
          latestTest.state.result.envelope.value.platform === 'android' ? (
            <StatusRow
              title={t('live.automation.latestTest')}
              detail={t(
                androidTestKeys[latestTest.state.result.envelope.value.phase],
              )}
              tone="info"
            />
          ) : null}
          {latestTest.state.kind === 'ready' &&
          latestTest.state.result.envelope.value.platform === 'android' &&
          latestTest.state.result.envelope.value.reason ? (
            <>
              <StatusRow
                title={t('live.automation.latestTestReason')}
                detail={t(
                  safeReasonMessageKey(
                    latestTest.state.result.envelope.value.reason,
                  ),
                )}
                tone="warning"
              />
              <AppText color="muted" variant="caption">
                {t('live.common.code', {
                  value: latestTest.state.result.envelope.value.reason,
                })}
              </AppText>
            </>
          ) : null}

          <SectionHeading title={t('live.automation.activationTitle')} />
          {home.state.result.envelope.value.automation.effective ===
          'test-only' ? (
            <Button
              label={t('live.automation.reviewActivation')}
              disabled={pending !== undefined}
              onPress={() => prepareActivationReview('activate')}
              testID="live-review-activation"
            />
          ) : null}
          {home.state.result.envelope.value.automation.effective ===
          'paused-repair' ? (
            <Button
              label={t('live.automation.reviewResume')}
              disabled={pending !== undefined}
              onPress={() => prepareActivationReview('resume')}
              testID="live-review-resume"
            />
          ) : null}
          {home.state.result.envelope.value.automation.desired === 'on' &&
          (home.state.result.envelope.value.automation.effective === 'active' ||
            home.state.result.envelope.value.automation.effective ===
              'action-required') ? (
            <Button
              label={t('live.automation.pause')}
              disabled={pending !== undefined}
              onPress={() => setConfirmPause(true)}
              variant="secondary"
              testID="live-review-pause"
            />
          ) : null}

          {review?.kind === 'test' ? (
            <Card>
              <AppText variant="heading">
                {t('live.automation.testReview')}
              </AppText>
              <KeyValue
                label={t('live.person.phone')}
                value={review.review.maskedDestination}
              />
              <KeyValue
                label={t('live.common.sim')}
                value={review.review.simLabel}
              />
              <KeyValue
                label={t('live.automation.segmentCount')}
                value={String(review.review.segmentCount)}
              />
              <AppText>{review.review.exactText}</AppText>
              <AppText color="muted">
                {t('live.automation.testChargeDisclosure')}
              </AppText>
              <AppText variant="heading">
                {t('live.automation.permissionTitle')}
              </AppText>
              <AppText>
                {t('live.automation.sendSmsPermissionDisclosure')}
              </AppText>
              <AppText>
                {t('live.automation.phoneStatePermissionDisclosure')}
              </AppText>
              <AppText color="critical">
                {t('live.automation.permissionDenialDisclosure')}
              </AppText>
              <Button
                label={t('live.automation.startTest')}
                disabled={pending !== undefined}
                onPress={startTest}
                testID="live-start-test"
              />
              <Button
                label={t('live.common.cancel')}
                onPress={() => setReview(undefined)}
                variant="secondary"
              />
            </Card>
          ) : null}
          {review && review.kind !== 'test' ? (
            <Card>
              <AppText variant="heading">
                {t(
                  review.kind === 'activate'
                    ? 'live.automation.activationReview'
                    : 'live.automation.resumeTitle',
                )}
              </AppText>
              <KeyValue
                label={t('live.settings.enabledRecipients')}
                value={String(review.review.enabledRecipientCount)}
              />
              <KeyValue
                label={t('live.home.needsAttention')}
                value={String(review.review.attentionCount)}
              />
              <KeyValue
                label={t('live.common.sim')}
                value={review.review.simLabel}
              />
              <KeyValue
                label={t('live.home.window')}
                value={review.review.windowLabel}
              />
              <KeyValue
                label={t('live.policy.dailyCap')}
                value={String(review.review.dailyCap)}
              />
              <AppText>{review.review.templatePreview}</AppText>
              <AppText color="muted">
                {t('live.automation.activationLimitations')}
              </AppText>
              <Button
                label={t(
                  review.kind === 'activate'
                    ? 'live.automation.activate'
                    : 'live.automation.resume',
                )}
                disabled={pending !== undefined}
                onPress={confirmActivationReview}
                testID="live-confirm-activation"
              />
              <Button
                label={t('live.common.cancel')}
                onPress={() => setReview(undefined)}
                variant="secondary"
              />
            </Card>
          ) : null}
          {confirmPause ? (
            <Card>
              <AppText variant="heading">
                {t('live.automation.pauseTitle')}
              </AppText>
              <AppText>{t('live.automation.pauseBody')}</AppText>
              <Button
                label={t('live.automation.pauseConfirm')}
                disabled={pending !== undefined}
                onPress={pauseAll}
                variant="danger"
                testID="live-confirm-pause"
              />
              <Button
                label={t('live.common.cancel')}
                onPress={() => setConfirmPause(false)}
                variant="secondary"
              />
            </Card>
          ) : null}
        </>
      ) : null}
    </Screen>
  );
}

function LiveIosCompanion({
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
  const [reminder, setReminder] = useState<
    | Readonly<{ kind: 'loading' }>
    | Readonly<{ kind: 'ready'; value: CompanionReminderState }>
    | Readonly<{ kind: 'error'; code: string }>
  >({ kind: 'loading' });
  const [review, setReview] = useState<CompanionComposerReviewProjection>();
  const [composerOutcome, setComposerOutcome] =
    useState<CompanionComposerOutcome>();
  const [composerError, setComposerError] = useState<string>();
  const [activationReview, setActivationReview] =
    useState<IosActivationReviewState>();
  const [confirmPause, setConfirmPause] = useState(false);
  const [actionProblem, setActionProblem] = useState<NativeProblem>();
  const [pending, setPending] = useState<
    | 'permission'
    | 'settings'
    | 'review'
    | 'open'
    | 'activate'
    | 'resume'
    | 'pause'
    | 'contacts-repair'
  >();
  const [message, setMessage] = useState<string>();
  const [pauseVerificationRequired, setPauseVerificationRequired] =
    useState(false);

  const loadReminder = useCallback(async () => {
    setReminder({ kind: 'loading' });
    try {
      const result = await companionPort.getReminderStatus();
      setReminder(
        result.kind === 'ok'
          ? { kind: 'ready', value: result.value }
          : { kind: 'error', code: result.code },
      );
    } catch {
      setReminder({ kind: 'error', code: 'REMINDER_NATIVE_FAILURE' });
    }
  }, [companionPort]);

  useEffect(() => {
    loadReminder().catch(() => undefined);
  }, [loadReminder]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', nextState => {
      if (nextState === 'active') {
        loadReminder().catch(() => undefined);
      }
    });
    return () => subscription.remove();
  }, [loadReminder]);

  useEffect(
    () =>
      port.subscribeInvalidations(event => {
        if (
          event.areas.includes('automation') ||
          event.areas.includes('home') ||
          event.areas.includes('readiness')
        ) {
          setActivationReview(undefined);
          setConfirmPause(false);
        }
      }),
    [port],
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
      setComposerError('COMPOSER_REVIEW_STALE');
    }
  }, [proposal.state, review]);

  const requestPermission = async () => {
    setPending('permission');
    setMessage(undefined);
    let result: Awaited<
      ReturnType<LiveCompanionPort['requestReminderAuthorization']>
    >;
    try {
      result = await companionPort.requestReminderAuthorization();
    } catch {
      setReminder({ kind: 'error', code: 'REMINDER_NATIVE_FAILURE' });
      setPending(undefined);
      return;
    }
    setReminder(
      result.kind === 'ok'
        ? { kind: 'ready', value: result.value }
        : { kind: 'error', code: result.code },
    );
    if (result.kind === 'ok') {
      setMessage(t('live.companion.permissionResult'));
    }
    setPending(undefined);
  };

  const openNotificationSettings = async () => {
    setPending('settings');
    setMessage(undefined);
    setComposerError(undefined);
    let result: Awaited<
      ReturnType<LiveCompanionPort['openNotificationSettings']>
    >;
    try {
      result = await companionPort.openNotificationSettings();
    } catch {
      setReminder({ kind: 'error', code: 'REMINDER_NATIVE_FAILURE' });
      setPending(undefined);
      return;
    }
    if (result.kind === 'error') {
      setReminder({ kind: 'error', code: result.code });
    } else {
      setMessage(t('live.companion.settingsOpened'));
    }
    setPending(undefined);
  };

  const failAutomation = async (problem: NativeProblem) => {
    if (problem.kind === 'stale-revision') {
      await home.reload();
      setActivationReview(undefined);
      setConfirmPause(false);
    }
    setActionProblem(problem);
    setPending(undefined);
  };

  const prepareActivationReview = async (kind: 'activate' | 'resume') => {
    setPending(kind);
    setActionProblem(undefined);
    setMessage(undefined);
    setPauseVerificationRequired(false);
    let result: Awaited<ReturnType<LiveAppPort['prepareActivation']>>;
    try {
      result =
        kind === 'activate'
          ? await port.prepareActivation()
          : await port.prepareResume();
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      await failAutomation(result.problem);
      return;
    }
    if (result.envelope.value.platform !== 'ios') {
      await failAutomation(nativePlatformMismatchProblem);
      return;
    }
    setActivationReview({
      kind,
      review: result.envelope.value,
      revision: result.envelope.revision,
    });
    setPending(undefined);
  };

  const confirmActivationReview = async () => {
    if (
      !activationReview ||
      !iosActivationSnapshotComplete(activationReview.review)
    ) {
      return;
    }
    setPending(activationReview.kind);
    setActionProblem(undefined);
    let result: Awaited<ReturnType<LiveAppPort['activate']>>;
    try {
      result =
        activationReview.kind === 'activate'
          ? await port.activate({
              handle: activationReview.review.handle,
              expectedRevision: activationReview.revision,
            })
          : await port.resume({
              handle: activationReview.review.handle,
              expectedRevision: activationReview.revision,
            });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      await failAutomation(result.problem);
      return;
    }
    await home.reload();
    await loadReminder();
    setActivationReview(undefined);
    setMessage(
      t(
        activationReview.kind === 'activate'
          ? 'live.companion.activationAccepted'
          : 'live.companion.resumeAccepted',
      ),
    );
    setPending(undefined);
  };

  const pauseReminders = async () => {
    if (
      home.state.kind !== 'ready' ||
      home.state.result.envelope.value.automation.platform !== 'ios'
    ) {
      return;
    }
    setPending('pause');
    setActionProblem(undefined);
    setPauseVerificationRequired(false);
    let result: Awaited<ReturnType<LiveAppPort['pauseAll']>>;
    try {
      result = await port.pauseAll({
        expectedRevision: home.state.result.envelope.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      await Promise.all([home.reload(), loadReminder()]);
      setPauseVerificationRequired(true);
      await failAutomation(result.problem);
      return;
    }
    await home.reload();
    await loadReminder();
    setConfirmPause(false);
    setPauseVerificationRequired(false);
    setMessage(t('live.companion.pauseAccepted'));
    setPending(undefined);
  };

  const prepareComposer = async () => {
    if (
      proposal.state.kind !== 'ready' ||
      proposal.state.result.envelope.value.kind !== 'ready'
    ) {
      return;
    }
    setPending('review');
    setComposerError(undefined);
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
      setComposerError('COMPOSER_NATIVE_FAILURE');
      setPending(undefined);
      return;
    }
    if (result.kind === 'error') {
      setComposerError(result.code);
      setPending(undefined);
      return;
    }
    const matches =
      result.value.proposalId === proposalValue.proposalId &&
      isCurrentOrNewerRevision(result.value.revision, envelope.revision) &&
      result.value.expiresAtEpochMilliseconds > Date.now();
    if (!matches) {
      setComposerError('COMPOSER_REVIEW_MISMATCH');
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
      setComposerError(undefined);
      setMessage(t('live.companion.contactsRepairAccepted'));
    } catch {
      setActionProblem(nativeBridgeProblem);
    }
    setPending(undefined);
  };

  const openComposer = async () => {
    if (!review) {
      return;
    }
    if (review.expiresAtEpochMilliseconds <= Date.now()) {
      setReview(undefined);
      setComposerError('COMPOSER_REVIEW_EXPIRED');
      return;
    }
    setPending('open');
    setComposerError(undefined);
    let available = false;
    try {
      available = await companionPort.canOpenComposer();
    } catch {
      setComposerError('COMPOSER_NATIVE_FAILURE');
      setPending(undefined);
      return;
    }
    if (!available) {
      setComposerError('COMPOSER_UNAVAILABLE');
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
      setComposerError('COMPOSER_NATIVE_FAILURE');
      setPending(undefined);
      return;
    }
    if (result.kind === 'error') {
      setComposerError(result.code);
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
  const iosAutomation =
    home.state.kind === 'ready' &&
    home.state.result.envelope.value.automation.platform === 'ios'
      ? home.state.result.envelope.value.automation
      : undefined;
  const composerIssues =
    iosAutomation?.readiness.composer.kind === 'blocked'
      ? iosAutomation.readiness.composer.issues
      : [];
  const managedByAndroid = composerIssues.some(
    issue => issue.code === 'active-sender-other-device',
  );
  const safetyStatusUnavailable = composerIssues.some(
    issue =>
      issue.code === 'coordination-unavailable' ||
      issue.code === 'firebase-account-deleting',
  );
  const composerAllowed = iosAutomation?.readiness.composer.kind === 'allowed';

  useEffect(() => {
    if (review && !composerAllowed) {
      setReview(undefined);
      setComposerError('COMPOSER_REVIEW_BLOCKED');
    }
  }, [composerAllowed, review]);

  return (
    <Screen includeTopInset testID="live-automation-screen">
      <Button
        label={t('live.common.back')}
        onPress={onBack}
        variant="ghost"
        testID="live-automation-back"
      />
      <AppText variant="title" accessibilityRole="header">
        {t('live.automation.title')}
      </AppText>
      <AppText color="muted">{t('live.automation.iosBody')}</AppText>
      <LiveActionFeedback problem={actionProblem} message={message} />
      {pauseVerificationRequired ? (
        <ReadinessBanner
          title={t('live.companion.pauseVerificationTitle')}
          detail={t('live.companion.pauseVerificationBody')}
          tone="critical"
          testID="live-ios-pause-verification-required"
        />
      ) : null}

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
          <Card>
            <StatusRow
              title={t('live.companion.currentState')}
              detail={t(
                iosStateKeys[iosAutomation.effective] ??
                  'live.common.unavailable',
              )}
              tone={
                iosAutomation.effective === 'ready' ? 'positive' : 'warning'
              }
            />
            <StatusRow
              title={t('live.companion.composerReadiness')}
              detail={
                composerAllowed
                  ? t('live.common.allowed')
                  : t('live.common.countChecks', {
                      count: composerIssues.length,
                    })
              }
              tone={composerAllowed ? 'positive' : 'warning'}
            />
          </Card>
          {composerIssues.map(issue => (
            <Card key={issue.id}>
              <StatusRow
                title={t(safeReasonMessageKey(issue.code))}
                tone={issue.severity === 'blocking' ? 'critical' : 'warning'}
              />
              <AppText color="muted" variant="caption">
                {t('live.common.code', { value: issue.code })}
              </AppText>
            </Card>
          ))}
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

          <SectionHeading title={t('live.companion.activationTitle')} />
          {iosAutomation.effective === 'not-configured' ? (
            <Button
              label={t('live.companion.reviewActivation')}
              disabled={pending !== undefined}
              onPress={() => prepareActivationReview('activate')}
              testID="live-ios-review-activation"
            />
          ) : null}
          {iosAutomation.effective === 'paused' ? (
            <Button
              label={t('live.companion.reviewResume')}
              disabled={pending !== undefined}
              onPress={() => prepareActivationReview('resume')}
              testID="live-ios-review-resume"
            />
          ) : null}
          {iosAutomation.desired === 'composer-reminders-on' ? (
            <Button
              label={t('live.companion.pause')}
              disabled={pending !== undefined}
              onPress={() => setConfirmPause(true)}
              variant="secondary"
              testID="live-ios-review-pause"
            />
          ) : null}
          {activationReview ? (
            <InlineReviewCard
              reviewKey={activationReview.review.handle}
              testID="live-ios-activation-review"
              title={t(
                activationReview.kind === 'activate'
                  ? 'live.companion.activationReview'
                  : 'live.companion.resumeReview',
              )}
            >
              <KeyValue
                label={t('live.companion.reminderRecipients')}
                value={String(activationReview.review.reminderRecipientCount)}
              />
              <KeyValue
                label={t('live.companion.plannedReminderCount')}
                value={String(activationReview.review.plannedReminderCount)}
              />
              <KeyValue
                label={t('live.home.window')}
                value={activationReview.review.reminderWindowLabel}
              />
              <StatusRow
                title={t('live.companion.horizonTitle')}
                detail={t(
                  iosReminderHorizonKeys[
                    activationReview.review.reminderHorizon
                  ],
                )}
                tone={
                  activationReview.review.reminderHorizon === 'full'
                    ? 'positive'
                    : 'warning'
                }
              />
              <StatusRow
                title={t('live.companion.contactsSnapshot')}
                detail={t(
                  activationReview.review.contactsReady
                    ? 'live.common.allowed'
                    : 'live.common.unavailable',
                )}
                tone={
                  activationReview.review.contactsReady
                    ? 'positive'
                    : 'critical'
                }
              />
              <StatusRow
                title={t('live.home.messageUiCapability')}
                detail={t(
                  activationReview.review.messageUiReady
                    ? 'live.home.messageUiAvailable'
                    : 'live.home.messageUiUnavailable',
                )}
                tone={
                  activationReview.review.messageUiReady
                    ? 'positive'
                    : 'critical'
                }
              />
              <StatusRow
                title={t('live.companion.protectedStorage')}
                detail={t(
                  activationReview.review.protectedStorageReady
                    ? 'live.common.allowed'
                    : 'live.common.unavailable',
                )}
                tone={
                  activationReview.review.protectedStorageReady
                    ? 'positive'
                    : 'critical'
                }
              />
              <StatusRow
                title={t('live.companion.coexistenceTitle')}
                detail={t(
                  iosCoexistenceKeys[activationReview.review.coexistence],
                )}
                tone={
                  activationReview.review.coexistence === 'clear'
                    ? 'positive'
                    : 'critical'
                }
              />
              {activationReview.review.readiness.composer.kind === 'blocked'
                ? activationReview.review.readiness.composer.issues.map(
                    issue => (
                      <StatusRow
                        key={issue.id}
                        title={t(safeReasonMessageKey(issue.code))}
                        tone={
                          issue.severity === 'blocking' ? 'critical' : 'warning'
                        }
                      />
                    ),
                  )
                : null}
              <ReadinessBanner
                title={t('live.common.iosEdition')}
                detail={t('live.companion.activationDisclosure')}
                tone="info"
              />
              {iosActivationSnapshotComplete(activationReview.review) ? (
                <Button
                  label={t(
                    activationReview.kind === 'activate'
                      ? 'live.companion.activate'
                      : 'live.companion.resume',
                  )}
                  disabled={pending !== undefined}
                  onPress={confirmActivationReview}
                  testID="live-ios-confirm-activation"
                />
              ) : (
                <ReadinessBanner
                  title={t('live.companion.activationSnapshotUnavailable')}
                  detail={t('live.companion.activationSnapshotUnavailableBody')}
                  tone="critical"
                  testID="live-ios-activation-snapshot-blocked"
                />
              )}
              <Button
                label={t('live.common.cancel')}
                disabled={pending !== undefined}
                onPress={() => setActivationReview(undefined)}
                variant="secondary"
              />
            </InlineReviewCard>
          ) : null}
          {confirmPause ? (
            <Card>
              <AppText variant="heading">
                {t('live.companion.pauseReview')}
              </AppText>
              <AppText>{t('live.companion.pauseBody')}</AppText>
              <Button
                label={t('live.companion.pauseConfirm')}
                disabled={pending !== undefined}
                onPress={pauseReminders}
                variant="danger"
                testID="live-ios-confirm-pause"
              />
              <Button
                label={t('live.common.cancel')}
                disabled={pending !== undefined}
                onPress={() => setConfirmPause(false)}
                variant="secondary"
              />
            </Card>
          ) : null}
        </>
      ) : null}

      <LivePolicyEditor platform="ios" port={port} />

      <SectionHeading title={t('live.companion.reminderTitle')} />
      {reminder.kind === 'loading' ? (
        <LiveLoading label={t('live.companion.reminderLoading')} />
      ) : null}
      {reminder.kind === 'error' ? (
        <>
          <ReadinessBanner
            title={t('live.companion.reminderUnavailable')}
            detail={t('live.error.bridge')}
            tone="warning"
            actionLabel={t('live.common.tryAgain')}
            onAction={() => loadReminder()}
          />
          <AppText color="muted" variant="caption">
            {t('live.common.code', { value: reminder.code })}
          </AppText>
        </>
      ) : null}
      {reminder.kind === 'ready' ? (
        <Card>
          <StatusRow
            title={t('live.companion.authorization')}
            detail={t(reminderPermissionKeys[reminder.value.authorization])}
            tone={
              reminder.value.authorization === 'authorized'
                ? 'positive'
                : 'warning'
            }
          />
          <StatusRow
            title={t('live.companion.scheduled', {
              count: reminder.value.scheduledCount,
            })}
          />
          <StatusRow
            title={t('live.companion.planned', {
              count: reminder.value.plannedDateCount,
            })}
          />
          {reminder.value.failedCount > 0 ? (
            <StatusRow
              title={t('live.companion.failedReminderCount', {
                count: reminder.value.failedCount,
              })}
              tone="warning"
            />
          ) : null}
          {reminder.value.earliestUnscheduledCivilDate ? (
            <StatusRow
              title={t('live.companion.earliestUnscheduled')}
              detail={formatLiveDate(
                reminder.value.earliestUnscheduledCivilDate,
                language,
              )}
              tone="warning"
            />
          ) : null}
          {reminder.value.truncated ? (
            <StatusRow title={t('live.companion.truncated')} tone="warning" />
          ) : null}
          {reminder.value.authorization === 'not-determined' ? (
            <Button
              label={t('live.companion.requestPermission')}
              disabled={pending !== undefined}
              onPress={requestPermission}
              testID="live-reminder-permission"
            />
          ) : null}
          {reminder.value.authorization === 'denied' ? (
            <Button
              label={t('live.companion.openNotificationSettings')}
              disabled={pending !== undefined}
              onPress={openNotificationSettings}
              testID="live-reminder-settings"
            />
          ) : null}
        </Card>
      ) : null}

      <SectionHeading title={t('live.companion.composerTitle')} />
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
          {composerAllowed ? (
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
          ) : (
            <StatusRow
              title={t('live.companion.composerBlocked')}
              tone="warning"
            />
          )}
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
          <Button
            label={t('live.companion.openComposer')}
            disabled={pending !== undefined}
            onPress={openComposer}
            testID="live-open-composer"
          />
          <Button
            label={t('live.common.cancel')}
            onPress={() => setReview(undefined)}
            variant="secondary"
          />
        </InlineReviewCard>
      ) : null}
      {composerError ? (
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
          <AppText color="muted" variant="caption">
            {t('live.common.code', { value: composerError })}
          </AppText>
        </Card>
      ) : null}
      {outcomeKey ? (
        <ReadinessBanner
          title={t(outcomeKey)}
          detail={t('live.companion.postComposerSafety')}
          testID="live-composer-post-safety"
          tone="warning"
        />
      ) : null}
    </Screen>
  );
}

export function LiveAutomationScreen({
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
  return capability.platform === 'android' ? (
    <LiveAndroidAutomation onBack={onBack} port={port} />
  ) : (
    <LiveIosCompanion
      companionPort={companionPort}
      onBack={onBack}
      port={port}
    />
  );
}

const styles = StyleSheet.create({
  input: {
    borderRadius: radii.md,
    borderWidth: 1,
    fontSize: 17,
    minHeight: minimumTargetSize,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
  },
});
