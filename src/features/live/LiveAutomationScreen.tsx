import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, StyleSheet } from 'react-native';

import type {
  ActivationReview,
  AndroidTestPhase,
  TestReview,
} from '../../domain/automation/model';
import type { NativeRevision } from '../../domain/shared/brand';
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
import type { CompanionReminderState } from '../../infrastructure/native/ios/CompanionNativeGateway';
import type { LiveAppPort, LiveCompanionPort } from './LiveAppPort';
import {
  LiveActionFeedback,
  LiveError,
  LiveLoading,
  LiveRefreshProblem,
  LiveValidationError,
} from './LiveProjectionState';
import {
  nativeBridgeProblem,
  nativePlatformMismatchProblem,
} from './nativeProblem';
import { useLiveProjection } from './useLiveProjection';

type AndroidReview =
  | Readonly<{
      kind: 'test';
      review: Extract<TestReview, { platform: 'android' }>;
      revision: import('../../domain/shared/brand').NativeRevision;
      sourceRevision: import('../../domain/shared/brand').NativeRevision;
      expiresAtMs: number;
    }>
  | Readonly<{
      kind: 'activate' | 'resume';
      review: Extract<ActivationReview, { platform: 'android' }>;
      revision: import('../../domain/shared/brand').NativeRevision;
      sourceRevision: import('../../domain/shared/brand').NativeRevision;
      expiresAtMs: number;
    }>;

type IosActivationReviewState = Readonly<{
  kind: 'activate' | 'resume';
  review: Extract<ActivationReview, { platform: 'ios' }>;
  revision: import('../../domain/shared/brand').NativeRevision;
  sourceRevision: import('../../domain/shared/brand').NativeRevision;
  expiresAtMs: number;
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

const androidProtectedReviewUiTtlMs = 9 * 60 * 1000 + 30 * 1000;
const iosProtectedReviewUiTtlMs = 4 * 60 * 1000 + 30 * 1000;

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

const androidTestInFlightPhases = new Set<AndroidTestPhase>([
  'prepared',
  'cloud-claimed',
  'arm-reconciling',
  'coordination-unknown',
  'cloud-armed',
  'barrier-consumed',
  'submitted',
  'sent-from-device',
]);

const isLatestTestAbsent = (problem: NativeProblem): boolean =>
  problem.kind === 'internal' &&
  problem.supportCode === 'NATIVE_NOT_CONFIGURED';

const androidTestStatusButtonId = 'live-check-test-status';

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

function LiveAndroidAutomation({
  onBack,
  onOpenMessage,
  onOpenSchedule,
  port,
}: {
  onBack: () => void;
  onOpenMessage: () => void;
  onOpenSchedule: () => void;
  port: LiveAppPort;
}) {
  const { colors } = useAppTheme();
  const { t } = useAppLocalization();
  const loadAccount = useCallback(() => port.getAccount(), [port]);
  const loadHome = useCallback(() => port.getHome(), [port]);
  const loadMessageConfiguration = useCallback(
    () => port.getMessageEditor(),
    [port],
  );
  const loadPolicyConfiguration = useCallback(
    () => port.getPolicyEditor(),
    [port],
  );
  const loadTest = useCallback(() => port.getLatestTest(), [port]);
  const account = useLiveProjection(loadAccount, port, [
    'account',
    'automation',
    'messages',
    'privacy',
  ]);
  const home = useLiveProjection(loadHome, port, ['home', 'automation']);
  const messageConfiguration = useLiveProjection(
    loadMessageConfiguration,
    port,
    ['account', 'automation', 'messages'],
  );
  const policyConfiguration = useLiveProjection(loadPolicyConfiguration, port, [
    'account',
    'automation',
    'messages',
  ]);
  const latestTest = useLiveProjection(loadTest, port, ['automation']);
  const [testPhone, setTestPhone] = useState('');
  const [review, setReview] = useState<AndroidReview>();
  const [confirmPause, setConfirmPause] = useState<NativeRevision>();
  const [pending, setPending] = useState<string>();
  const [problem, setProblem] = useState<NativeProblem>();
  const [message, setMessage] = useState<string>();
  const [localIssue, setLocalIssue] = useState<string>();
  const [testFormRequested, setTestFormRequested] = useState(false);
  const [supportExpanded, setSupportExpanded] = useState(false);
  const reviewRequestSequence = useRef(0);
  const readinessActionRequestSequence = useRef(0);
  const reviewMounted = useRef(true);
  const homeTruthRef = useRef<
    Readonly<{ revision: NativeRevision | undefined; trusted: boolean }>
  >({ revision: undefined, trusted: false });

  useEffect(() => {
    reviewMounted.current = true;
    return () => {
      reviewMounted.current = false;
      reviewRequestSequence.current += 1;
      readinessActionRequestSequence.current += 1;
    };
  }, []);

  const clearEphemeralTest = useCallback(() => {
    setTestPhone('');
    setLocalIssue(undefined);
    setTestFormRequested(false);
  }, []);

  useEffect(
    () =>
      port.subscribeInvalidations(event => {
        if (
          event.areas.includes('account') ||
          event.areas.includes('home') ||
          event.areas.includes('automation') ||
          event.areas.includes('messages')
        ) {
          reviewRequestSequence.current += 1;
          setReview(undefined);
          setConfirmPause(undefined);
          setPending(current =>
            current === 'readiness-action' ? current : undefined,
          );
          clearEphemeralTest();
        }
      }),
    [clearEphemeralTest, port],
  );
  useEffect(() => {
    const subscription = AppState.addEventListener('change', nextState => {
      if (nextState === 'active') {
        reviewRequestSequence.current += 1;
        setReview(undefined);
        setConfirmPause(undefined);
        setPending(current =>
          current === 'readiness-action' ? current : undefined,
        );
        clearEphemeralTest();
      }
    });
    return () => subscription.remove();
  }, [clearEphemeralTest]);

  const fail = async (actionProblem: NativeProblem) => {
    if (actionProblem.kind === 'stale-revision') {
      reviewRequestSequence.current += 1;
      await Promise.all([
        account.reload(),
        home.reload(),
        latestTest.reload(),
        messageConfiguration.reload(),
        policyConfiguration.reload(),
      ]);
      setReview(undefined);
      setConfirmPause(undefined);
    }
    setProblem(actionProblem);
    setPending(undefined);
  };

  const checkAndroidStatus = async () => {
    reviewRequestSequence.current += 1;
    setReview(undefined);
    setConfirmPause(undefined);
    setPending('status');
    setProblem(undefined);
    await Promise.all([
      account.reload(),
      home.reload(),
      latestTest.reload(),
      messageConfiguration.reload(),
      policyConfiguration.reload(),
    ]);
    setPending(undefined);
  };

  const performReadinessAction = async () => {
    if (
      !canPerformReadinessAction ||
      !actionableReadinessIssue?.action ||
      home.state.kind !== 'ready' ||
      home.state.refreshing ||
      home.state.refreshProblem
    ) {
      return;
    }
    const action = actionableReadinessIssue.action;
    const sourceRevision = home.state.result.envelope.revision;
    const request = readinessActionRequestSequence.current + 1;
    readinessActionRequestSequence.current = request;
    reviewRequestSequence.current += 1;
    setReview(undefined);
    setConfirmPause(undefined);
    clearEphemeralTest();
    setPending('readiness-action');
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['performAction']>>;
    try {
      result = await port.performAction({
        handle: action.handle,
        expectedRevision: sourceRevision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (
      !reviewMounted.current ||
      request !== readinessActionRequestSequence.current
    ) {
      return;
    }
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
        await Promise.all([
          account.reload(),
          home.reload(),
          latestTest.reload(),
          messageConfiguration.reload(),
          policyConfiguration.reload(),
        ]);
      }
      if (
        !reviewMounted.current ||
        request !== readinessActionRequestSequence.current
      ) {
        return;
      }
      setProblem(result.problem);
      setPending(undefined);
      return;
    }
    await Promise.all([
      account.reload(),
      home.reload(),
      latestTest.reload(),
      messageConfiguration.reload(),
      policyConfiguration.reload(),
    ]);
    if (
      !reviewMounted.current ||
      request !== readinessActionRequestSequence.current
    ) {
      return;
    }
    setMessage(
      result.envelope.value.kind === 'opened'
        ? t('live.attention.opened')
        : t('live.attention.cancelled'),
    );
    setPending(undefined);
  };

  const prepareTest = async () => {
    if (
      !androidTestOwnerCurrent ||
      !testConfigurationReady ||
      !latestTestSettled ||
      testInFlight ||
      home.state.kind !== 'ready' ||
      home.state.refreshing ||
      home.state.refreshProblem ||
      home.state.result.envelope.value.automation.platform !== 'android' ||
      home.state.result.envelope.value.automation.readiness.test.kind !==
        'allowed' ||
      !['not-configured', 'test-only', 'paused-repair'].includes(
        home.state.result.envelope.value.automation.effective,
      )
    ) {
      return;
    }
    const sourceRevision = home.state.result.envelope.revision;
    const validated = validateEphemeralPhoneInput(testPhone);
    if (validated.kind === 'invalid') {
      setLocalIssue(t(safeReasonMessageKey(validated.issues[0]!.code)));
      return;
    }
    setPending('prepare-test');
    setProblem(undefined);
    setMessage(undefined);
    setLocalIssue(undefined);
    const request = reviewRequestSequence.current + 1;
    reviewRequestSequence.current = request;
    const expiresAtMs = Date.now() + androidProtectedReviewUiTtlMs;
    let result: Awaited<ReturnType<LiveAppPort['prepareTest']>>;
    try {
      result = await port.prepareTest({
        destination: validated.value,
        expectedRevision: home.state.result.envelope.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (
      !reviewMounted.current ||
      request !== reviewRequestSequence.current ||
      !homeTruthRef.current.trusted ||
      homeTruthRef.current.revision !== sourceRevision
    ) {
      return;
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
      expiresAtMs,
      kind: 'test',
      review: result.envelope.value,
      revision: result.envelope.revision,
      sourceRevision,
    });
    setTestPhone('');
    setLocalIssue(undefined);
    setPending(undefined);
  };

  const startTest = async () => {
    if (
      !androidTestOwnerCurrent ||
      !testConfigurationReady ||
      !latestTestSettled ||
      testInFlight ||
      !review ||
      review.kind !== 'test' ||
      review.expiresAtMs <= Date.now() ||
      home.state.kind !== 'ready' ||
      home.state.refreshing ||
      home.state.refreshProblem ||
      home.state.result.envelope.value.automation.platform !== 'android' ||
      review.sourceRevision !== home.state.result.envelope.revision ||
      home.state.result.envelope.value.automation.readiness.test.kind !==
        'allowed' ||
      !['not-configured', 'test-only', 'paused-repair'].includes(
        home.state.result.envelope.value.automation.effective,
      )
    ) {
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
    clearEphemeralTest();
    setMessage(t('live.automation.testStarted'));
    setPending(undefined);
  };

  const prepareActivationReview = async (kind: 'activate' | 'resume') => {
    if (
      (kind === 'activate'
        ? !androidActivationOwnerCurrent
        : !androidResumeOwnerCurrent) ||
      !testConfigurationReady ||
      androidConfigurationLoading ||
      androidConfigurationStatusRecoveryVisible ||
      !latestTestSettled ||
      testInFlight ||
      home.state.kind !== 'ready' ||
      home.state.refreshing ||
      home.state.refreshProblem ||
      home.state.result.envelope.value.automation.platform !== 'android' ||
      home.state.result.envelope.value.automation.readiness.activation.kind !==
        'allowed' ||
      (kind === 'activate'
        ? home.state.result.envelope.value.automation.effective !== 'test-only'
        : home.state.result.envelope.value.automation.effective !==
          'paused-repair')
    ) {
      return;
    }
    const sourceRevision = home.state.result.envelope.revision;
    setPending(kind);
    setProblem(undefined);
    setMessage(undefined);
    const request = reviewRequestSequence.current + 1;
    reviewRequestSequence.current = request;
    const expiresAtMs = Date.now() + androidProtectedReviewUiTtlMs;
    let result: Awaited<ReturnType<LiveAppPort['prepareActivation']>>;
    try {
      result =
        kind === 'activate'
          ? await port.prepareActivation()
          : await port.prepareResume();
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (
      !reviewMounted.current ||
      request !== reviewRequestSequence.current ||
      !homeTruthRef.current.trusted ||
      homeTruthRef.current.revision !== sourceRevision
    ) {
      return;
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
      expiresAtMs,
      kind,
      review: result.envelope.value,
      revision: result.envelope.revision,
      sourceRevision,
    });
    setPending(undefined);
  };

  const confirmActivationReview = async () => {
    if (
      (review?.kind === 'activate'
        ? !androidActivationOwnerCurrent
        : !androidResumeOwnerCurrent) ||
      !testConfigurationReady ||
      androidConfigurationLoading ||
      androidConfigurationStatusRecoveryVisible ||
      !latestTestSettled ||
      testInFlight ||
      !review ||
      review.kind === 'test' ||
      review.expiresAtMs <= Date.now() ||
      home.state.kind !== 'ready' ||
      home.state.refreshing ||
      home.state.refreshProblem ||
      home.state.result.envelope.value.automation.platform !== 'android' ||
      review.sourceRevision !== home.state.result.envelope.revision ||
      home.state.result.envelope.value.automation.readiness.activation.kind !==
        'allowed' ||
      (review.kind === 'activate'
        ? home.state.result.envelope.value.automation.effective !== 'test-only'
        : home.state.result.envelope.value.automation.effective !==
          'paused-repair')
    ) {
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
    if (
      confirmPause === undefined ||
      !androidPauseOwnerCurrent ||
      home.state.kind !== 'ready' ||
      home.state.refreshing ||
      home.state.refreshProblem ||
      confirmPause !== home.state.result.envelope.revision ||
      home.state.result.envelope.value.automation.platform !== 'android' ||
      home.state.result.envelope.value.automation.desired !== 'on' ||
      !['active', 'action-required'].includes(
        home.state.result.envelope.value.automation.effective,
      )
    ) {
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
    setConfirmPause(undefined);
    setMessage(t('live.automation.pauseAccepted'));
    setPending(undefined);
  };

  const androidAutomation =
    home.state.kind === 'ready' &&
    home.state.result.envelope.value.automation.platform === 'android'
      ? home.state.result.envelope.value.automation
      : undefined;
  const homeTrusted =
    home.state.kind === 'ready' &&
    !home.state.refreshing &&
    !home.state.refreshProblem;
  const homeRevision =
    home.state.kind === 'ready'
      ? home.state.result.envelope.revision
      : undefined;
  homeTruthRef.current = { revision: homeRevision, trusted: homeTrusted };
  const accountProjectionTrusted =
    account.state.kind === 'ready' &&
    !account.state.refreshing &&
    !account.state.refreshProblem &&
    account.state.result.envelope.revision === homeRevision;
  const accountPlatformMismatch = Boolean(
    account.state.kind === 'ready' &&
    account.state.result.envelope.value.kind === 'connected' &&
    account.state.result.envelope.value.sender.platform !== 'android',
  );
  const androidAccountRecoveryVisible = Boolean(
    homeTrusted &&
    (account.state.kind === 'error' ||
      accountPlatformMismatch ||
      (account.state.kind === 'ready' &&
        (Boolean(account.state.refreshProblem) ||
          (!account.state.refreshing &&
            account.state.result.envelope.revision !== homeRevision)))),
  );
  const androidSender =
    accountProjectionTrusted &&
    account.state.kind === 'ready' &&
    account.state.result.envelope.value.kind === 'connected' &&
    account.state.result.envelope.value.sender.platform === 'android'
      ? account.state.result.envelope.value.sender
      : undefined;
  const androidSenderMatchesHome = Boolean(
    androidAutomation &&
    androidSender &&
    ((androidAutomation.effective === 'not-configured' &&
      (androidSender.kind === 'test-only' ||
        androidSender.kind === 'paused-repair')) ||
      (androidAutomation.effective === 'test-only' &&
        androidSender.kind === 'test-only') ||
      (androidAutomation.effective === 'paused-repair' &&
        androidSender.kind === 'paused-repair') ||
      ((androidAutomation.effective === 'active' ||
        androidAutomation.effective === 'action-required') &&
        androidSender.kind === 'automation-active') ||
      (androidAutomation.effective === 'standby' &&
        androidSender.kind === 'standby') ||
      (androidAutomation.effective === 'transfer-pending' &&
        androidSender.kind === 'transfer-pending') ||
      (androidAutomation.effective === 'deleting' &&
        androidSender.kind === 'deleting')),
  );
  const androidOwnershipRecoveryVisible = Boolean(
    androidAccountRecoveryVisible ||
    (homeTrusted && accountProjectionTrusted && !androidSenderMatchesHome),
  );
  const androidTestOwnerCurrent =
    androidSender?.kind === 'test-only' ||
    androidSender?.kind === 'paused-repair';
  const androidActivationOwnerCurrent = androidSender?.kind === 'test-only';
  const androidResumeOwnerCurrent = androidSender?.kind === 'paused-repair';
  const androidPauseOwnerCurrent = androidSender?.kind === 'automation-active';
  const androidLocalOwnerCurrent = Boolean(
    androidSender &&
    ['test-only', 'paused-repair', 'automation-active'].includes(
      androidSender.kind,
    ) &&
    androidSenderMatchesHome,
  );
  const androidSetupConfigurationOwnerCurrent = Boolean(
    androidAutomation?.effective === 'not-configured' &&
    (androidSender?.kind === 'test-only' ||
      androidSender?.kind === 'paused-repair'),
  );
  const messageConfigurationTrusted =
    messageConfiguration.state.kind === 'ready' &&
    !messageConfiguration.state.refreshing &&
    !messageConfiguration.state.refreshProblem &&
    messageConfiguration.state.result.envelope.revision === homeRevision;
  const messageConfigurationReady = Boolean(
    messageConfigurationTrusted &&
    messageConfiguration.state.kind === 'ready' &&
    messageConfiguration.state.result.envelope.value.kind === 'configured',
  );
  const androidMessageRepairVisible = Boolean(
    homeTrusted &&
    androidSetupConfigurationOwnerCurrent &&
    messageConfigurationTrusted &&
    messageConfiguration.state.kind === 'ready' &&
    messageConfiguration.state.result.envelope.value.kind === 'not-configured',
  );
  const androidMessageStatusRecoveryVisible = Boolean(
    homeTrusted &&
    androidLocalOwnerCurrent &&
    (messageConfiguration.state.kind === 'error' ||
      (messageConfiguration.state.kind === 'ready' &&
        (Boolean(messageConfiguration.state.refreshProblem) ||
          (!messageConfiguration.state.refreshing &&
            messageConfiguration.state.result.envelope.revision !==
              homeRevision)))),
  );
  const policyConfigurationTrusted =
    policyConfiguration.state.kind === 'ready' &&
    !policyConfiguration.state.refreshing &&
    !policyConfiguration.state.refreshProblem &&
    policyConfiguration.state.result.envelope.revision === homeRevision;
  const androidPolicyStatusRecoveryVisible = Boolean(
    homeTrusted &&
    androidLocalOwnerCurrent &&
    (policyConfiguration.state.kind === 'error' ||
      (policyConfiguration.state.kind === 'ready' &&
        (Boolean(policyConfiguration.state.refreshProblem) ||
          (!policyConfiguration.state.refreshing &&
            policyConfiguration.state.result.envelope.revision !==
              homeRevision)))),
  );
  const androidConfigurationStatusRecoveryVisible =
    androidMessageStatusRecoveryVisible ||
    androidPolicyStatusRecoveryVisible ||
    Boolean(
      homeTrusted &&
      androidLocalOwnerCurrent &&
      androidAutomation?.effective !== 'not-configured' &&
      ((messageConfigurationTrusted &&
        messageConfiguration.state.kind === 'ready' &&
        messageConfiguration.state.result.envelope.value.kind ===
          'not-configured') ||
        (policyConfigurationTrusted &&
          policyConfiguration.state.kind === 'ready' &&
          policyConfiguration.state.result.envelope.value.kind ===
            'not-configured')),
    );
  const androidConfigurationLoading = Boolean(
    homeTrusted &&
    androidLocalOwnerCurrent &&
    (messageConfiguration.state.kind === 'loading' ||
      policyConfiguration.state.kind === 'loading' ||
      (messageConfiguration.state.kind === 'ready' &&
        messageConfiguration.state.refreshing) ||
      (policyConfiguration.state.kind === 'ready' &&
        policyConfiguration.state.refreshing)),
  );
  const testConfigurationReady = Boolean(
    messageConfigurationReady &&
    policyConfigurationTrusted &&
    policyConfiguration.state.kind === 'ready' &&
    policyConfiguration.state.result.envelope.value.kind === 'configured',
  );
  const androidPolicyRepairRequired = Boolean(
    homeTrusted &&
    androidSetupConfigurationOwnerCurrent &&
    messageConfigurationReady &&
    policyConfigurationTrusted &&
    policyConfiguration.state.kind === 'ready' &&
    policyConfiguration.state.result.envelope.value.kind === 'not-configured',
  );
  const androidHomeRecoveryVisible =
    home.state.kind === 'ready' && Boolean(home.state.refreshProblem);
  const activationIssues =
    androidAutomation?.readiness.activation.kind === 'blocked'
      ? androidAutomation.readiness.activation.issues
      : [];
  const testIssues =
    androidAutomation?.readiness.test.kind === 'blocked'
      ? androidAutomation.readiness.test.issues
      : [];
  const birthdayIssues =
    androidAutomation?.readiness.birthday.kind === 'blocked'
      ? androidAutomation.readiness.birthday.issues
      : [];
  const readinessIssueSource =
    androidAutomation?.effective === 'active' ||
    androidAutomation?.effective === 'action-required'
      ? birthdayIssues
      : [...testIssues, ...activationIssues];
  const testRequired = readinessIssueSource.some(
    issue => issue.code === 'test-receipt-invalid',
  );
  const readinessIssues = readinessIssueSource.filter(
    (issue, index, issues) =>
      issue.code !== 'test-receipt-invalid' &&
      issues.findIndex(candidate => candidate.code === issue.code) === index,
  );
  const actionableReadinessIssue =
    readinessIssueSource.find(
      issue =>
        issue.code !== 'test-receipt-invalid' &&
        issue.severity === 'blocking' &&
        issue.action !== undefined,
    ) ??
    readinessIssueSource.find(
      issue =>
        issue.code !== 'test-receipt-invalid' && issue.action !== undefined,
    );
  const readinessSupportCodes = readinessIssueSource
    .map(issue => issue.code)
    .filter((code, index, codes) => codes.indexOf(code) === index);
  const latestTestTrusted =
    latestTest.state.kind === 'ready' &&
    !latestTest.state.refreshing &&
    !latestTest.state.refreshProblem &&
    latestTest.state.result.envelope.revision === homeRevision;
  const latestAndroidTest =
    latestTestTrusted &&
    latestTest.state.kind === 'ready' &&
    latestTest.state.result.envelope.value.platform === 'android'
      ? latestTest.state.result.envelope.value
      : undefined;
  const latestTestReasonRepeatedAsReadinessIssue = Boolean(
    latestAndroidTest?.reason &&
    readinessIssues.some(issue => issue.code === latestAndroidTest.reason),
  );
  const latestTestAbsent =
    latestTest.state.kind === 'error' &&
    isLatestTestAbsent(latestTest.state.problem);
  const latestTestPlatformMismatch =
    latestTestTrusted &&
    latestTest.state.kind === 'ready' &&
    latestTest.state.result.envelope.value.platform !== 'android';
  const latestTestRevisionMismatch = Boolean(
    latestTest.state.kind === 'ready' &&
    !latestTest.state.refreshing &&
    !latestTest.state.refreshProblem &&
    latestTest.state.result.envelope.revision !== homeRevision,
  );
  const testInFlight = Boolean(
    latestAndroidTest && androidTestInFlightPhases.has(latestAndroidTest.phase),
  );
  const initialTestRequired = androidAutomation?.effective === 'not-configured';
  const testCapableMode =
    androidAutomation?.effective === 'test-only' ||
    androidAutomation?.effective === 'paused-repair';
  const testGateAllowed = androidAutomation?.readiness.test.kind === 'allowed';
  const latestTestSettled = latestTestAbsent || latestAndroidTest !== undefined;
  const latestTestStatusRecoveryVisible = Boolean(
    (latestTest.state.kind === 'error' && !latestTestAbsent) ||
    latestTestPlatformMismatch ||
    latestTestRevisionMismatch ||
    (latestTest.state.kind === 'ready' && latestTest.state.refreshProblem),
  );
  const canStartTest =
    homeTrusted &&
    androidTestOwnerCurrent &&
    testConfigurationReady &&
    latestTestSettled &&
    (initialTestRequired || testCapableMode) &&
    testGateAllowed &&
    !testInFlight;
  const showTestForm =
    canStartTest && (initialTestRequired || testRequired || testFormRequested);
  const protectedReviewOpen = Boolean(review || confirmPause);
  const actionReviewOpen = protectedReviewOpen;
  const readinessActionProjectionLoading = Boolean(
    account.state.kind === 'loading' ||
    (account.state.kind === 'ready' && account.state.refreshing) ||
    messageConfiguration.state.kind === 'loading' ||
    (messageConfiguration.state.kind === 'ready' &&
      messageConfiguration.state.refreshing) ||
    policyConfiguration.state.kind === 'loading' ||
    (policyConfiguration.state.kind === 'ready' &&
      policyConfiguration.state.refreshing) ||
    latestTest.state.kind === 'loading' ||
    (latestTest.state.kind === 'ready' && latestTest.state.refreshing),
  );
  const canPerformReadinessAction = Boolean(
    actionableReadinessIssue?.action &&
    homeTrusted &&
    homeRevision !== undefined &&
    accountProjectionTrusted &&
    !protectedReviewOpen &&
    !readinessActionProjectionLoading &&
    !androidHomeRecoveryVisible &&
    !androidOwnershipRecoveryVisible &&
    !androidConfigurationLoading &&
    !androidConfigurationStatusRecoveryVisible &&
    !androidMessageRepairVisible &&
    !androidPolicyRepairRequired &&
    !latestTestStatusRecoveryVisible &&
    !testInFlight &&
    pending === undefined,
  );
  const canReviewActivation =
    homeTrusted &&
    androidActivationOwnerCurrent &&
    testConfigurationReady &&
    !androidConfigurationLoading &&
    !androidConfigurationStatusRecoveryVisible &&
    !actionReviewOpen &&
    !showTestForm &&
    !testInFlight &&
    latestTestSettled &&
    androidAutomation?.effective === 'test-only' &&
    androidAutomation.readiness.activation.kind === 'allowed';
  const canReviewResume =
    homeTrusted &&
    androidResumeOwnerCurrent &&
    testConfigurationReady &&
    !androidConfigurationLoading &&
    !androidConfigurationStatusRecoveryVisible &&
    !actionReviewOpen &&
    !showTestForm &&
    !testInFlight &&
    latestTestSettled &&
    androidAutomation?.effective === 'paused-repair' &&
    androidAutomation.readiness.activation.kind === 'allowed';
  const canReviewPause =
    homeTrusted &&
    androidPauseOwnerCurrent &&
    !androidConfigurationLoading &&
    !androidConfigurationStatusRecoveryVisible &&
    !actionReviewOpen &&
    androidAutomation?.desired === 'on' &&
    (androidAutomation.effective === 'active' ||
      androidAutomation.effective === 'action-required');
  const testReviewCurrent =
    homeTrusted &&
    androidTestOwnerCurrent &&
    testConfigurationReady &&
    review?.kind === 'test' &&
    review.expiresAtMs > Date.now() &&
    review.sourceRevision === homeRevision &&
    androidAutomation?.readiness.test.kind === 'allowed' &&
    (androidAutomation.effective === 'not-configured' ||
      androidAutomation.effective === 'test-only' ||
      androidAutomation.effective === 'paused-repair') &&
    latestTestSettled &&
    !testInFlight;
  const activationReviewCurrent =
    homeTrusted &&
    latestTestSettled &&
    !latestTestStatusRecoveryVisible &&
    !testInFlight &&
    review !== undefined &&
    review.kind !== 'test' &&
    review.expiresAtMs > Date.now() &&
    review.sourceRevision === homeRevision &&
    androidAutomation?.readiness.activation.kind === 'allowed' &&
    (review?.kind === 'activate'
      ? androidActivationOwnerCurrent
      : androidResumeOwnerCurrent) &&
    (review.kind === 'activate'
      ? androidAutomation.effective === 'test-only'
      : androidAutomation.effective === 'paused-repair');
  const pauseReviewCurrent =
    homeTrusted &&
    androidPauseOwnerCurrent &&
    confirmPause === homeRevision &&
    androidAutomation?.desired === 'on' &&
    (androidAutomation.effective === 'active' ||
      androidAutomation.effective === 'action-required');
  const hasPrimaryPlatformAction = Boolean(
    showTestForm ||
    canReviewActivation ||
    canReviewResume ||
    androidHomeRecoveryVisible ||
    androidMessageRepairVisible ||
    androidPolicyRepairRequired ||
    androidConfigurationStatusRecoveryVisible ||
    androidOwnershipRecoveryVisible ||
    latestTestStatusRecoveryVisible ||
    actionableReadinessIssue,
  );
  const supportAvailable = Boolean(
    latestAndroidTest?.reason || readinessSupportCodes.length > 0,
  );
  useEffect(() => {
    if (!review) return;
    const remaining = review.expiresAtMs - Date.now();
    if (remaining <= 0) {
      reviewRequestSequence.current += 1;
      setReview(undefined);
      clearEphemeralTest();
      return;
    }
    const timeout = setTimeout(() => {
      reviewRequestSequence.current += 1;
      setReview(undefined);
      clearEphemeralTest();
    }, remaining);
    return () => clearTimeout(timeout);
  }, [clearEphemeralTest, review]);
  useEffect(() => {
    if (latestTestStatusRecoveryVisible) {
      setReview(undefined);
    }
  }, [latestTestStatusRecoveryVisible]);
  useEffect(() => {
    if (androidOwnershipRecoveryVisible) {
      setReview(undefined);
      setConfirmPause(undefined);
      clearEphemeralTest();
    }
  }, [androidOwnershipRecoveryVisible, clearEphemeralTest]);
  useEffect(() => {
    if (androidConfigurationStatusRecoveryVisible) {
      reviewRequestSequence.current += 1;
      setReview(undefined);
      setConfirmPause(undefined);
      clearEphemeralTest();
    }
  }, [androidConfigurationStatusRecoveryVisible, clearEphemeralTest]);
  useEffect(() => {
    if (!homeTrusted) {
      if (review) setReview(undefined);
      if (confirmPause) setConfirmPause(undefined);
      return;
    }
    if (review?.kind === 'test' && !testReviewCurrent) {
      setReview(undefined);
    }
    if (review && review.kind !== 'test' && !activationReviewCurrent) {
      setReview(undefined);
    }
    if (confirmPause && !pauseReviewCurrent) {
      setConfirmPause(undefined);
    }
  }, [
    activationReviewCurrent,
    confirmPause,
    homeTrusted,
    pauseReviewCurrent,
    review,
    testReviewCurrent,
  ]);
  const leaveScreen = () => {
    reviewRequestSequence.current += 1;
    readinessActionRequestSequence.current += 1;
    clearEphemeralTest();
    setReview(undefined);
    setConfirmPause(undefined);
    onBack();
  };

  if (
    home.state.kind === 'ready' &&
    home.state.result.envelope.value.automation.platform !== 'android'
  ) {
    return (
      <Screen includeTopInset testID="live-automation-screen">
        <Button
          label={t('live.common.back')}
          onPress={leaveScreen}
          variant="ghost"
          testID="live-automation-back"
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
        onPress={leaveScreen}
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
            <>
              <LiveRefreshProblem problem={home.state.refreshProblem} />
              <Button
                label={t('live.automation.checkReadiness')}
                disabled={pending !== undefined || home.state.refreshing}
                onPress={checkAndroidStatus}
                testID="live-automation-check-status"
              />
            </>
          ) : null}
          {androidOwnershipRecoveryVisible && !androidHomeRecoveryVisible ? (
            <Button
              label={t('live.automation.checkReadiness')}
              disabled={
                pending !== undefined || account.state.kind === 'loading'
              }
              onPress={checkAndroidStatus}
              testID="live-automation-check-ownership-status"
            />
          ) : null}
          {androidConfigurationStatusRecoveryVisible &&
          !androidHomeRecoveryVisible &&
          !androidOwnershipRecoveryVisible ? (
            <Button
              label={t('live.automation.checkReadiness')}
              disabled={pending !== undefined}
              onPress={checkAndroidStatus}
              testID="live-automation-check-configuration-status"
            />
          ) : null}
          {androidConfigurationLoading &&
          !androidHomeRecoveryVisible &&
          !androidOwnershipRecoveryVisible ? (
            <LiveLoading label={t('live.automation.loading')} />
          ) : null}
          <Card>
            <StatusRow
              title={t('live.automation.current')}
              detail={t(
                androidStateKeys[
                  home.state.result.envelope.value.automation.effective
                ] ?? 'live.common.unavailable',
              )}
              tone={
                home.state.result.envelope.value.automation.effective ===
                'active'
                  ? 'positive'
                  : 'warning'
              }
            />
            {latestAndroidTest ? (
              <StatusRow
                title={t('live.automation.latestTest')}
                detail={t(androidTestKeys[latestAndroidTest.phase])}
                tone={
                  latestAndroidTest.phase === 'passed'
                    ? 'positive'
                    : latestAndroidTest.phase === 'submitted' ||
                        latestAndroidTest.phase === 'sent-from-device'
                      ? 'info'
                      : 'warning'
                }
              />
            ) : null}
            {latestAndroidTest?.reason &&
            !latestTestReasonRepeatedAsReadinessIssue ? (
              <StatusRow
                title={t('live.automation.latestTestReason')}
                detail={t(safeReasonMessageKey(latestAndroidTest.reason))}
                tone="warning"
              />
            ) : null}
          </Card>
          {latestTest.state.kind === 'loading' ||
          (latestTest.state.kind === 'ready' && latestTest.state.refreshing) ? (
            <LiveLoading label={t('live.automation.testStatusLoading')} />
          ) : null}
          {latestTest.state.kind === 'error' &&
          !latestTestAbsent &&
          !confirmPause &&
          !androidHomeRecoveryVisible &&
          !androidOwnershipRecoveryVisible &&
          !androidConfigurationStatusRecoveryVisible &&
          !androidMessageRepairVisible &&
          !androidPolicyRepairRequired ? (
            <LiveError
              title={t('live.automation.testStatusUnavailable')}
              problem={latestTest.state.problem}
              onRetry={() => latestTest.reload()}
              retryTestID={androidTestStatusButtonId}
              testID="live-test-status-error"
            />
          ) : null}
          {latestTestPlatformMismatch &&
          !confirmPause &&
          !androidHomeRecoveryVisible &&
          !androidOwnershipRecoveryVisible &&
          !androidConfigurationStatusRecoveryVisible &&
          !androidMessageRepairVisible &&
          !androidPolicyRepairRequired ? (
            <LiveError
              title={t('live.automation.testStatusUnavailable')}
              problem={nativePlatformMismatchProblem}
              onRetry={() => latestTest.reload()}
              retryTestID={androidTestStatusButtonId}
              testID="live-test-status-error"
            />
          ) : null}
          {latestTest.state.kind === 'ready' &&
          latestTest.state.refreshProblem &&
          !confirmPause &&
          !androidHomeRecoveryVisible &&
          !androidOwnershipRecoveryVisible &&
          !androidConfigurationStatusRecoveryVisible &&
          !androidMessageRepairVisible &&
          !androidPolicyRepairRequired ? (
            <>
              <LiveRefreshProblem problem={latestTest.state.refreshProblem} />
              <Button
                label={t('live.automation.checkTestStatus')}
                disabled={pending !== undefined || latestTest.state.refreshing}
                onPress={() => latestTest.reload()}
                testID={androidTestStatusButtonId}
              />
            </>
          ) : null}
          {latestTestRevisionMismatch &&
          !confirmPause &&
          !androidHomeRecoveryVisible &&
          !androidOwnershipRecoveryVisible &&
          !androidConfigurationStatusRecoveryVisible &&
          !androidMessageRepairVisible &&
          !androidPolicyRepairRequired ? (
            <Button
              label={t('live.automation.checkTestStatus')}
              disabled={pending !== undefined}
              onPress={() => latestTest.reload()}
              testID={androidTestStatusButtonId}
            />
          ) : null}
          {readinessIssues.length > 0 && !protectedReviewOpen ? (
            <Card testID="live-automation-readiness-issues">
              {readinessIssues.map(issue => (
                <StatusRow
                  key={issue.code}
                  title={t(safeReasonMessageKey(issue.code))}
                  tone={issue.severity === 'blocking' ? 'critical' : 'warning'}
                />
              ))}
              {actionableReadinessIssue?.action ? (
                <Button
                  label={
                    pending === 'readiness-action'
                      ? t('live.attention.openingAction')
                      : t('live.attention.openAction')
                  }
                  disabled={!canPerformReadinessAction}
                  onPress={performReadinessAction}
                  testID="live-automation-readiness-action"
                />
              ) : null}
              {!androidHomeRecoveryVisible &&
              !androidMessageRepairVisible &&
              !androidConfigurationStatusRecoveryVisible &&
              !androidOwnershipRecoveryVisible &&
              !latestTestStatusRecoveryVisible ? (
                <Button
                  label={t('live.automation.checkReadiness')}
                  disabled={
                    pending !== undefined ||
                    home.state.refreshing ||
                    latestTest.state.kind === 'loading' ||
                    (latestTest.state.kind === 'ready' &&
                      latestTest.state.refreshing)
                  }
                  onPress={checkAndroidStatus}
                  variant={hasPrimaryPlatformAction ? 'secondary' : 'primary'}
                  testID="live-automation-check-readiness"
                />
              ) : null}
            </Card>
          ) : null}
          {androidMessageRepairVisible && !protectedReviewOpen ? (
            <Button
              label={t('live.guidedSetup.writeMessage')}
              disabled={pending !== undefined}
              onPress={onOpenMessage}
              testID="live-automation-open-message"
            />
          ) : null}
          {testInFlight && !actionReviewOpen ? (
            <Button
              label={t('live.automation.checkTestStatus')}
              disabled={pending !== undefined}
              onPress={() => latestTest.reload()}
              variant="secondary"
              testID={androidTestStatusButtonId}
            />
          ) : null}
          {(initialTestRequired || testRequired) &&
          homeTrusted &&
          !protectedReviewOpen ? (
            <ReadinessBanner
              title={t('live.automation.testRequiredTitle')}
              detail={t('live.automation.testRequiredBody')}
              tone="warning"
            />
          ) : null}
          {showTestForm && !actionReviewOpen ? (
            <Card>
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
                disabled={pending !== undefined || !homeTrusted}
                onPress={prepareTest}
                testID="live-prepare-test"
              />
            </Card>
          ) : null}
          {canStartTest &&
          testCapableMode &&
          !testRequired &&
          androidAutomation.readiness.activation.kind === 'allowed' &&
          !testFormRequested &&
          !actionReviewOpen ? (
            <Button
              label={t('live.automation.runAnotherTest')}
              disabled={pending !== undefined}
              onPress={() => setTestFormRequested(true)}
              variant="secondary"
              testID="live-run-another-test"
            />
          ) : null}
          {canReviewActivation ? (
            <Button
              label={t('live.automation.reviewActivation')}
              disabled={pending !== undefined || !homeTrusted}
              onPress={() => prepareActivationReview('activate')}
              testID="live-review-activation"
            />
          ) : null}
          {canReviewResume ? (
            <Button
              label={t('live.automation.reviewResume')}
              disabled={pending !== undefined || !homeTrusted}
              onPress={() => prepareActivationReview('resume')}
              testID="live-review-resume"
            />
          ) : null}
          {canReviewPause ? (
            <Button
              label={t('live.automation.pause')}
              disabled={pending !== undefined || !homeTrusted}
              onPress={() => {
                if (canReviewPause && homeRevision) {
                  setConfirmPause(homeRevision);
                }
              }}
              variant="secondary"
              testID="live-review-pause"
            />
          ) : null}
          {supportAvailable && !actionReviewOpen ? (
            <>
              <Button
                label={t(
                  supportExpanded
                    ? 'live.automation.hideSupportDetails'
                    : 'live.automation.showSupportDetails',
                )}
                onPress={() => setSupportExpanded(expanded => !expanded)}
                variant="secondary"
                testID="live-automation-support-toggle"
              />
              {supportExpanded ? (
                <Card testID="live-automation-support-details">
                  <AppText color="muted">
                    {t('live.automation.supportDetailsBody')}
                  </AppText>
                  {latestAndroidTest?.reason ? (
                    <AppText color="muted" variant="caption">
                      {t('live.common.code', {
                        value: latestAndroidTest.reason,
                      })}
                    </AppText>
                  ) : null}
                  {readinessSupportCodes.map(code => (
                    <AppText color="muted" key={code} variant="caption">
                      {t('live.common.code', { value: code })}
                    </AppText>
                  ))}
                </Card>
              ) : null}
            </>
          ) : null}

          {review?.kind === 'test' && testReviewCurrent ? (
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
              {testReviewCurrent ? (
                <Button
                  label={t('live.automation.startTest')}
                  disabled={pending !== undefined}
                  onPress={startTest}
                  testID="live-start-test"
                />
              ) : null}
              <Button
                label={t('live.common.cancel')}
                onPress={() => {
                  setReview(undefined);
                  clearEphemeralTest();
                }}
                variant="secondary"
              />
            </Card>
          ) : null}
          {review && review.kind !== 'test' && activationReviewCurrent ? (
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
              {activationReviewCurrent ? (
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
              ) : null}
              <Button
                label={t('live.common.cancel')}
                onPress={() => setReview(undefined)}
                variant="secondary"
              />
            </Card>
          ) : null}
          {confirmPause && pauseReviewCurrent ? (
            <Card>
              <AppText variant="heading">
                {t('live.automation.pauseTitle')}
              </AppText>
              <AppText>{t('live.automation.pauseBody')}</AppText>
              {pauseReviewCurrent ? (
                <Button
                  label={t('live.automation.pauseConfirm')}
                  disabled={pending !== undefined}
                  onPress={pauseAll}
                  variant="danger"
                  testID="live-confirm-pause"
                />
              ) : null}
              <Button
                label={t('live.common.cancel')}
                onPress={() => setConfirmPause(undefined)}
                variant="secondary"
              />
            </Card>
          ) : null}
          {androidPolicyRepairRequired && !protectedReviewOpen ? (
            <Button
              label={t('live.guidedSetup.chooseWindow')}
              disabled={pending !== undefined}
              onPress={onOpenSchedule}
              testID="live-automation-open-schedule"
            />
          ) : null}
        </>
      ) : null}
    </Screen>
  );
}

function LiveIosCompanion({
  companionPort,
  onBack,
  onOpenMessage,
  onOpenSchedule,
  port,
}: {
  companionPort: LiveCompanionPort;
  onBack: () => void;
  onOpenMessage: () => void;
  onOpenSchedule: () => void;
  port: LiveAppPort;
}) {
  const { language, t } = useAppLocalization();
  const loadHome = useCallback(() => port.getHome(), [port]);
  const loadSetup = useCallback(() => port.getSetup(), [port]);
  const loadMessageConfiguration = useCallback(
    () => port.getMessageEditor(),
    [port],
  );
  const loadPolicyConfiguration = useCallback(
    () => port.getPolicyEditor(),
    [port],
  );
  const home = useLiveProjection(loadHome, port, [
    'home',
    'automation',
    'readiness',
  ]);
  const setup = useLiveProjection(loadSetup, port, [
    'account',
    'automation',
    'messages',
    'setup',
  ]);
  const messageConfiguration = useLiveProjection(
    loadMessageConfiguration,
    port,
    ['account', 'automation', 'messages', 'setup'],
  );
  const policyConfiguration = useLiveProjection(loadPolicyConfiguration, port, [
    'account',
    'automation',
    'messages',
    'setup',
  ]);
  const [reminder, setReminder] = useState<
    | Readonly<{ kind: 'loading' }>
    | Readonly<{ kind: 'ready'; value: CompanionReminderState }>
    | Readonly<{ kind: 'error'; code: string }>
  >({ kind: 'loading' });
  const [activationReview, setActivationReview] =
    useState<IosActivationReviewState>();
  const [confirmPause, setConfirmPause] = useState<NativeRevision>();
  const [actionProblem, setActionProblem] = useState<NativeProblem>();
  const [pending, setPending] = useState<
    | 'permission'
    | 'settings'
    | 'activate'
    | 'resume'
    | 'pause'
    | 'status'
    | 'verify-pause'
  >();
  const [message, setMessage] = useState<string>();
  const [pauseVerificationRequired, setPauseVerificationRequired] =
    useState(false);
  const [reminderSupportExpanded, setReminderSupportExpanded] = useState(false);
  const reminderRequestSequence = useRef(0);
  const activationReviewRequestSequence = useRef(0);
  const reminderMounted = useRef(true);
  const iosHomeTruthRef = useRef<
    Readonly<{ revision: NativeRevision | undefined; trusted: boolean }>
  >({ revision: undefined, trusted: false });
  const pauseHomeGeneration = useRef(0);
  const pauseVerificationRequestSequence = useRef(0);

  const fetchReminder = useCallback(async () => {
    try {
      return await companionPort.getReminderStatus();
    } catch {
      return { kind: 'error' as const, code: 'REMINDER_NATIVE_FAILURE' };
    }
  }, [companionPort]);

  const commitReminder = useCallback(
    (
      request: number,
      result: Awaited<ReturnType<LiveCompanionPort['getReminderStatus']>>,
    ) => {
      if (
        !reminderMounted.current ||
        request !== reminderRequestSequence.current
      ) {
        return;
      }
      setReminder(
        result.kind === 'ok'
          ? { kind: 'ready', value: result.value }
          : { kind: 'error', code: result.code },
      );
    },
    [],
  );

  const loadReminder = useCallback(async () => {
    activationReviewRequestSequence.current += 1;
    const request = reminderRequestSequence.current + 1;
    reminderRequestSequence.current = request;
    setActivationReview(undefined);
    setReminder({ kind: 'loading' });
    const result = await fetchReminder();
    commitReminder(request, result);
    return { request, result } as const;
  }, [commitReminder, fetchReminder]);

  useEffect(() => {
    reminderMounted.current = true;
    return () => {
      reminderMounted.current = false;
      reminderRequestSequence.current += 1;
      activationReviewRequestSequence.current += 1;
    };
  }, []);

  useEffect(() => {
    loadReminder().catch(() => undefined);
  }, [loadReminder]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', nextState => {
      if (nextState === 'active') {
        activationReviewRequestSequence.current += 1;
        pauseHomeGeneration.current += 1;
        setConfirmPause(undefined);
        setPending(undefined);
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
          event.areas.includes('messages') ||
          event.areas.includes('setup') ||
          event.areas.includes('readiness')
        ) {
          activationReviewRequestSequence.current += 1;
          pauseHomeGeneration.current += 1;
          setActivationReview(undefined);
          setConfirmPause(undefined);
          setPending(undefined);
        }
      }),
    [port],
  );

  const requestPermission = async () => {
    if (
      accountDeletionInProgress ||
      pauseVerificationVisible ||
      home.state.kind !== 'ready' ||
      home.state.refreshing ||
      home.state.refreshProblem ||
      reminder.kind !== 'ready' ||
      reminder.value.authorization !== 'not-determined'
    ) {
      return;
    }
    const request = reminderRequestSequence.current + 1;
    reminderRequestSequence.current = request;
    setPending('permission');
    setMessage(undefined);
    let result: Awaited<
      ReturnType<LiveCompanionPort['requestReminderAuthorization']>
    >;
    try {
      result = await companionPort.requestReminderAuthorization();
    } catch {
      result = { kind: 'error', code: 'REMINDER_NATIVE_FAILURE' };
    }
    commitReminder(request, result);
    if (result.kind === 'ok') {
      setMessage(t('live.companion.permissionResult'));
    }
    setPending(undefined);
  };

  const openNotificationSettings = async () => {
    if (
      accountDeletionInProgress ||
      pauseVerificationVisible ||
      home.state.kind !== 'ready' ||
      home.state.refreshing ||
      home.state.refreshProblem ||
      reminder.kind !== 'ready' ||
      reminder.value.authorization !== 'denied'
    ) {
      return;
    }
    const request = reminderRequestSequence.current + 1;
    reminderRequestSequence.current = request;
    setPending('settings');
    setMessage(undefined);
    let result: Awaited<
      ReturnType<LiveCompanionPort['openNotificationSettings']>
    >;
    try {
      result = await companionPort.openNotificationSettings();
    } catch {
      result = { kind: 'error', code: 'REMINDER_NATIVE_FAILURE' };
    }
    if (result.kind === 'error') {
      commitReminder(request, result);
    } else {
      setMessage(t('live.companion.settingsOpened'));
    }
    setPending(undefined);
  };

  const failAutomation = async (problem: NativeProblem) => {
    if (problem.kind === 'stale-revision') {
      activationReviewRequestSequence.current += 1;
      await Promise.all([
        home.reload(),
        setup.reload(),
        messageConfiguration.reload(),
        policyConfiguration.reload(),
      ]);
      setActivationReview(undefined);
      setConfirmPause(undefined);
    }
    setActionProblem(problem);
    setPending(undefined);
  };

  const checkIosStatus = async () => {
    activationReviewRequestSequence.current += 1;
    setActivationReview(undefined);
    setConfirmPause(undefined);
    pauseHomeGeneration.current += 1;
    setPending('status');
    setActionProblem(undefined);
    await Promise.all([
      home.reload(),
      setup.reload(),
      messageConfiguration.reload(),
      policyConfiguration.reload(),
      loadReminder(),
    ]);
    if (reminderMounted.current) {
      setPending(undefined);
    }
  };

  const prepareActivationReview = async (kind: 'activate' | 'resume') => {
    if (
      accountDeletionInProgress ||
      !setupTrusted ||
      !iosConfigurationReady ||
      iosConfigurationLoading ||
      iosConfigurationStatusRecoveryVisible ||
      (kind === 'activate'
        ? initialActivationCompleted !== false
        : initialActivationCompleted !== true) ||
      pauseVerificationVisible ||
      home.state.kind !== 'ready' ||
      home.state.refreshing ||
      home.state.refreshProblem ||
      home.state.result.envelope.value.automation.platform !== 'ios' ||
      home.state.result.envelope.value.automation.desired !== 'paused' ||
      home.state.result.envelope.value.automation.effective !== 'paused' ||
      home.state.result.envelope.value.automation.readiness.composer.kind !==
        'allowed' ||
      reminder.kind !== 'ready' ||
      reminder.value.kind !== 'ok' ||
      !['authorized', 'ephemeral', 'provisional'].includes(
        reminder.value.authorization,
      )
    ) {
      return;
    }
    const sourceRevision = home.state.result.envelope.revision;
    setPending(kind);
    setActionProblem(undefined);
    setMessage(undefined);
    const request = activationReviewRequestSequence.current + 1;
    activationReviewRequestSequence.current = request;
    const expiresAtMs = Date.now() + iosProtectedReviewUiTtlMs;
    let result: Awaited<ReturnType<LiveAppPort['prepareActivation']>>;
    try {
      result =
        kind === 'activate'
          ? await port.prepareActivation()
          : await port.prepareResume();
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (
      !reminderMounted.current ||
      request !== activationReviewRequestSequence.current ||
      !iosHomeTruthRef.current.trusted ||
      iosHomeTruthRef.current.revision !== sourceRevision
    ) {
      return;
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
      expiresAtMs,
      kind,
      review: result.envelope.value,
      revision: result.envelope.revision,
      sourceRevision,
    });
    setPending(undefined);
  };

  const confirmActivationReview = async () => {
    if (
      accountDeletionInProgress ||
      !setupTrusted ||
      !iosConfigurationReady ||
      iosConfigurationLoading ||
      iosConfigurationStatusRecoveryVisible ||
      pauseVerificationVisible ||
      !activationReview ||
      activationReview.expiresAtMs <= Date.now() ||
      (activationReview.kind === 'activate'
        ? initialActivationCompleted !== false
        : initialActivationCompleted !== true) ||
      !iosActivationSnapshotComplete(activationReview.review) ||
      home.state.kind !== 'ready' ||
      home.state.refreshing ||
      home.state.refreshProblem ||
      home.state.result.envelope.value.automation.platform !== 'ios' ||
      activationReview.sourceRevision !== home.state.result.envelope.revision ||
      home.state.result.envelope.value.automation.desired !== 'paused' ||
      home.state.result.envelope.value.automation.effective !== 'paused' ||
      home.state.result.envelope.value.automation.readiness.composer.kind !==
        'allowed' ||
      reminder.kind !== 'ready' ||
      reminder.value.kind !== 'ok' ||
      !['authorized', 'ephemeral', 'provisional'].includes(
        reminder.value.authorization,
      )
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
    await Promise.all([
      home.reload(),
      setup.reload(),
      messageConfiguration.reload(),
      policyConfiguration.reload(),
    ]);
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
      confirmPause === undefined ||
      pauseVerificationVisible ||
      home.state.kind !== 'ready' ||
      home.state.result.envelope.value.automation.platform !== 'ios' ||
      home.state.refreshing ||
      home.state.refreshProblem ||
      confirmPause !== home.state.result.envelope.revision ||
      home.state.result.envelope.value.automation.desired !==
        'composer-reminders-on' ||
      !['ready', 'action-required'].includes(
        home.state.result.envelope.value.automation.effective,
      )
    ) {
      return;
    }
    setPending('pause');
    setActionProblem(undefined);
    let result: Awaited<ReturnType<LiveAppPort['pauseAll']>>;
    try {
      result = await port.pauseAll({
        expectedRevision: home.state.result.envelope.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      await Promise.all([
        home.reload(),
        setup.reload(),
        messageConfiguration.reload(),
        policyConfiguration.reload(),
        loadReminder(),
      ]);
      setConfirmPause(undefined);
      setPauseVerificationRequired(true);
      await failAutomation(result.problem);
      return;
    }
    await Promise.all([
      home.reload(),
      setup.reload(),
      messageConfiguration.reload(),
      policyConfiguration.reload(),
      loadReminder(),
    ]);
    setConfirmPause(undefined);
    setPauseVerificationRequired(false);
    setMessage(t('live.companion.pauseAccepted'));
    setPending(undefined);
  };

  const verifyPauseState = async () => {
    const verificationRequest = pauseVerificationRequestSequence.current + 1;
    pauseVerificationRequestSequence.current = verificationRequest;
    const homeGeneration = pauseHomeGeneration.current + 1;
    pauseHomeGeneration.current = homeGeneration;
    setPending('verify-pause');
    setActionProblem(undefined);
    setMessage(undefined);
    setConfirmPause(undefined);
    const [homeResult, , , , reminderCheck] = await Promise.all([
      home.reload(),
      setup.reload(),
      messageConfiguration.reload(),
      policyConfiguration.reload(),
      loadReminder(),
    ]);
    if (!reminderMounted.current) {
      return;
    }
    const verifiedAutomation =
      homeResult.kind === 'ok'
        ? homeResult.envelope.value.automation
        : undefined;
    const reminderResult = reminderCheck.result;
    const reminderStillCurrent =
      reminderCheck.request === reminderRequestSequence.current;
    const homeStillCurrent = homeGeneration === pauseHomeGeneration.current;
    if (verificationRequest !== pauseVerificationRequestSequence.current) {
      return;
    }
    if (!homeStillCurrent || !reminderStillCurrent) {
      setPauseVerificationRequired(true);
      setMessage(t('live.companion.pauseVerificationStillRequired'));
      setPending(undefined);
      return;
    }
    const homeIsPaused =
      verifiedAutomation?.platform === 'ios' &&
      verifiedAutomation.desired === 'paused' &&
      verifiedAutomation.effective === 'paused';
    const remindersAreCancelled =
      reminderResult.kind === 'ok' &&
      reminderResult.value.kind === 'ok' &&
      reminderResult.value.scheduledCount === 0 &&
      reminderResult.value.failedCount === 0 &&
      !reminderResult.value.earliestUnscheduledCivilDate &&
      !reminderResult.value.truncated &&
      !reminderResult.value.code;
    if (homeIsPaused && remindersAreCancelled) {
      setConfirmPause(undefined);
      setPauseVerificationRequired(false);
      setMessage(t('live.companion.pauseVerificationComplete'));
    } else {
      setPauseVerificationRequired(true);
      setMessage(t('live.companion.pauseVerificationStillRequired'));
    }
    setPending(undefined);
  };

  const iosAutomation =
    home.state.kind === 'ready' &&
    home.state.result.envelope.value.automation.platform === 'ios'
      ? home.state.result.envelope.value.automation
      : undefined;
  const homeTrusted =
    home.state.kind === 'ready' &&
    !home.state.refreshing &&
    !home.state.refreshProblem;
  iosHomeTruthRef.current = {
    revision:
      home.state.kind === 'ready'
        ? home.state.result.envelope.revision
        : undefined,
    trusted: homeTrusted,
  };
  const setupTrusted =
    homeTrusted &&
    setup.state.kind === 'ready' &&
    !setup.state.refreshing &&
    !setup.state.refreshProblem &&
    home.state.kind === 'ready' &&
    setup.state.result.envelope.revision ===
      home.state.result.envelope.revision &&
    setup.state.result.envelope.value.automation.platform === 'ios';
  const iosSetupRecoveryVisible = Boolean(
    homeTrusted &&
    (setup.state.kind === 'error' ||
      (setup.state.kind === 'ready' &&
        (Boolean(setup.state.refreshProblem) ||
          setup.state.result.envelope.value.automation.platform !== 'ios' ||
          (!setup.state.refreshing &&
            home.state.kind === 'ready' &&
            setup.state.result.envelope.revision !==
              home.state.result.envelope.revision)))),
  );
  const initialActivationCompleted = setupTrusted
    ? setup.state.kind === 'ready'
      ? setup.state.result.envelope.value.initialActivationCompleted
      : undefined
    : undefined;
  const iosHomeRevision =
    home.state.kind === 'ready'
      ? home.state.result.envelope.revision
      : undefined;
  const iosMessageConfigurationTrusted =
    messageConfiguration.state.kind === 'ready' &&
    !messageConfiguration.state.refreshing &&
    !messageConfiguration.state.refreshProblem &&
    messageConfiguration.state.result.envelope.revision === iosHomeRevision;
  const iosMessageConfigurationReady = Boolean(
    iosMessageConfigurationTrusted &&
    messageConfiguration.state.kind === 'ready' &&
    messageConfiguration.state.result.envelope.value.kind === 'configured',
  );
  const iosPolicyConfigurationTrusted =
    policyConfiguration.state.kind === 'ready' &&
    !policyConfiguration.state.refreshing &&
    !policyConfiguration.state.refreshProblem &&
    policyConfiguration.state.result.envelope.revision === iosHomeRevision;
  const iosPolicyConfigurationReady = Boolean(
    iosPolicyConfigurationTrusted &&
    policyConfiguration.state.kind === 'ready' &&
    policyConfiguration.state.result.envelope.value.kind === 'configured',
  );
  const iosConfigurationReady =
    iosMessageConfigurationReady && iosPolicyConfigurationReady;
  const iosConfigurationLoading = Boolean(
    homeTrusted &&
    (messageConfiguration.state.kind === 'loading' ||
      policyConfiguration.state.kind === 'loading' ||
      (messageConfiguration.state.kind === 'ready' &&
        messageConfiguration.state.refreshing) ||
      (policyConfiguration.state.kind === 'ready' &&
        policyConfiguration.state.refreshing)),
  );
  const iosMessageRepairVisible = Boolean(
    homeTrusted &&
    iosAutomation?.effective === 'not-configured' &&
    iosMessageConfigurationTrusted &&
    messageConfiguration.state.kind === 'ready' &&
    messageConfiguration.state.result.envelope.value.kind === 'not-configured',
  );
  const iosPolicyRepairRequired = Boolean(
    homeTrusted &&
    iosAutomation?.effective === 'not-configured' &&
    iosMessageConfigurationReady &&
    iosPolicyConfigurationTrusted &&
    policyConfiguration.state.kind === 'ready' &&
    policyConfiguration.state.result.envelope.value.kind === 'not-configured',
  );
  const iosConfigurationStatusRecoveryVisible = Boolean(
    homeTrusted &&
    (messageConfiguration.state.kind === 'error' ||
      policyConfiguration.state.kind === 'error' ||
      (messageConfiguration.state.kind === 'ready' &&
        (Boolean(messageConfiguration.state.refreshProblem) ||
          (!messageConfiguration.state.refreshing &&
            messageConfiguration.state.result.envelope.revision !==
              iosHomeRevision))) ||
      (policyConfiguration.state.kind === 'ready' &&
        (Boolean(policyConfiguration.state.refreshProblem) ||
          (!policyConfiguration.state.refreshing &&
            policyConfiguration.state.result.envelope.revision !==
              iosHomeRevision))) ||
      (iosAutomation?.effective !== 'not-configured' &&
        ((iosMessageConfigurationTrusted &&
          messageConfiguration.state.kind === 'ready' &&
          messageConfiguration.state.result.envelope.value.kind ===
            'not-configured') ||
          (iosPolicyConfigurationTrusted &&
            policyConfiguration.state.kind === 'ready' &&
            policyConfiguration.state.result.envelope.value.kind ===
              'not-configured'))) ||
      (iosAutomation?.effective === 'not-configured' && iosConfigurationReady)),
  );
  const iosHomeRecoveryVisible =
    home.state.kind === 'ready' && Boolean(home.state.refreshProblem);
  const reminderReady = reminder.kind === 'ready' ? reminder.value : undefined;
  const reminderTrusted = reminderReady?.kind === 'ok';
  const pausedReminderCancellationPending = Boolean(
    homeTrusted &&
    iosAutomation?.desired === 'paused' &&
    iosAutomation.effective === 'paused' &&
    reminderReady?.kind === 'ok' &&
    (reminderReady.scheduledCount > 0 ||
      reminderReady.failedCount > 0 ||
      reminderReady.earliestUnscheduledCivilDate ||
      reminderReady.truncated ||
      reminderReady.code),
  );
  const pauseVerificationVisible =
    pauseVerificationRequired || pausedReminderCancellationPending;
  const reminderAllowsActivation =
    reminderTrusted &&
    reminderReady !== undefined &&
    (reminderReady.authorization === 'authorized' ||
      reminderReady.authorization === 'ephemeral' ||
      reminderReady.authorization === 'provisional');
  const composerIssues =
    iosAutomation?.readiness.composer.kind === 'blocked'
      ? iosAutomation.readiness.composer.issues
      : [];
  const managedByAndroid = composerIssues.some(
    issue => issue.code === 'active-sender-other-device',
  );
  const accountDeletionInProgress = composerIssues.some(
    issue => issue.code === 'firebase-account-deleting',
  );
  const safetyStatusUnavailable = composerIssues.some(
    issue =>
      issue.code === 'coordination-unavailable' ||
      issue.code === 'firebase-account-deleting',
  );
  const visibleComposerIssues = composerIssues.filter(
    issue =>
      issue.code !== 'active-sender-other-device' &&
      issue.code !== 'coordination-unavailable' &&
      issue.code !== 'firebase-account-deleting',
  );
  const reminderNeedsCheck =
    reminder.kind === 'error' ||
    reminderReady?.kind === 'error' ||
    reminderReady?.authorization === 'unknown';
  const reminderPrimaryRecoveryVisible = Boolean(
    reminderNeedsCheck ||
    reminderReady?.authorization === 'not-determined' ||
    reminderReady?.authorization === 'denied',
  );
  const composerAllowed = iosAutomation?.readiness.composer.kind === 'allowed';
  const activationReviewModeCurrent = Boolean(
    activationReview &&
    activationReview.expiresAtMs > Date.now() &&
    setupTrusted &&
    iosConfigurationReady &&
    !iosConfigurationLoading &&
    !iosConfigurationStatusRecoveryVisible &&
    activationReview.sourceRevision ===
      (home.state.kind === 'ready'
        ? home.state.result.envelope.revision
        : undefined) &&
    !accountDeletionInProgress &&
    iosAutomation?.desired === 'paused' &&
    iosAutomation.effective === 'paused' &&
    (activationReview.kind === 'activate'
      ? initialActivationCompleted === false
      : initialActivationCompleted === true),
  );
  const activationReviewCurrent =
    homeTrusted && activationReviewModeCurrent && composerAllowed;
  const pauseReviewCurrent = Boolean(
    homeTrusted &&
    setupTrusted &&
    !iosSetupRecoveryVisible &&
    !iosConfigurationLoading &&
    !iosConfigurationStatusRecoveryVisible &&
    confirmPause ===
      (home.state.kind === 'ready'
        ? home.state.result.envelope.revision
        : undefined) &&
    iosAutomation?.desired === 'composer-reminders-on' &&
    (iosAutomation.effective === 'ready' ||
      iosAutomation.effective === 'action-required'),
  );
  const protectedReviewOpen = Boolean(activationReview || confirmPause);
  const actionReviewOpen = protectedReviewOpen || pauseVerificationVisible;
  const canReviewActivation =
    homeTrusted &&
    setupTrusted &&
    iosConfigurationReady &&
    !iosConfigurationLoading &&
    !iosConfigurationStatusRecoveryVisible &&
    initialActivationCompleted === false &&
    !accountDeletionInProgress &&
    reminderAllowsActivation &&
    composerAllowed &&
    !pauseVerificationVisible &&
    !actionReviewOpen &&
    iosAutomation?.desired === 'paused' &&
    iosAutomation.effective === 'paused';
  const canReviewResume =
    homeTrusted &&
    setupTrusted &&
    iosConfigurationReady &&
    !iosConfigurationLoading &&
    !iosConfigurationStatusRecoveryVisible &&
    initialActivationCompleted === true &&
    !accountDeletionInProgress &&
    reminderAllowsActivation &&
    composerAllowed &&
    !pauseVerificationVisible &&
    !actionReviewOpen &&
    iosAutomation?.desired === 'paused' &&
    iosAutomation.effective === 'paused';
  const canReviewPause =
    homeTrusted &&
    setupTrusted &&
    !iosSetupRecoveryVisible &&
    !iosConfigurationLoading &&
    !iosConfigurationStatusRecoveryVisible &&
    !pauseVerificationVisible &&
    !actionReviewOpen &&
    iosAutomation?.desired === 'composer-reminders-on' &&
    (iosAutomation.effective === 'ready' ||
      iosAutomation.effective === 'action-required');
  const reminderSupportAvailable =
    reminder.kind === 'error' ||
    reminderReady !== undefined ||
    composerIssues.length > 0;
  const hasReminderWarnings = Boolean(
    reminderReady &&
    (reminderReady.failedCount > 0 ||
      reminderReady.earliestUnscheduledCivilDate ||
      reminderReady.truncated),
  );
  useEffect(() => {
    if (!activationReview) return;
    const remaining = activationReview.expiresAtMs - Date.now();
    if (remaining <= 0) {
      activationReviewRequestSequence.current += 1;
      setActivationReview(undefined);
      return;
    }
    const timeout = setTimeout(() => {
      activationReviewRequestSequence.current += 1;
      setActivationReview(undefined);
    }, remaining);
    return () => clearTimeout(timeout);
  }, [activationReview]);
  useEffect(() => {
    if (
      iosSetupRecoveryVisible ||
      iosConfigurationLoading ||
      iosConfigurationStatusRecoveryVisible
    ) {
      activationReviewRequestSequence.current += 1;
      setActivationReview(undefined);
      setConfirmPause(undefined);
    }
  }, [
    iosConfigurationLoading,
    iosConfigurationStatusRecoveryVisible,
    iosSetupRecoveryVisible,
  ]);
  useEffect(() => {
    if (!homeTrusted) {
      if (activationReview) setActivationReview(undefined);
      if (confirmPause) setConfirmPause(undefined);
      return;
    }
    if (activationReview && !activationReviewModeCurrent) {
      setActivationReview(undefined);
    }
    if (confirmPause && !pauseReviewCurrent) {
      setConfirmPause(undefined);
    }
  }, [
    activationReview,
    activationReviewModeCurrent,
    confirmPause,
    homeTrusted,
    pauseReviewCurrent,
  ]);
  const leaveScreen = () => {
    activationReviewRequestSequence.current += 1;
    setActivationReview(undefined);
    setConfirmPause(undefined);
    setReminderSupportExpanded(false);
    onBack();
  };

  return (
    <Screen includeTopInset testID="live-automation-screen">
      <Button
        label={t('live.common.back')}
        onPress={leaveScreen}
        variant="ghost"
        testID="live-automation-back"
      />
      <AppText variant="title" accessibilityRole="header">
        {t('live.companion.activationTitle')}
      </AppText>
      <AppText color="muted">{t('live.automation.iosBody')}</AppText>
      <LiveActionFeedback problem={actionProblem} message={message} />
      {pauseVerificationVisible ? (
        <>
          <ReadinessBanner
            title={t('live.companion.pauseVerificationTitle')}
            detail={t('live.companion.pauseVerificationBody')}
            tone="critical"
            testID="live-ios-pause-verification-required"
          />
          <Button
            label={t(
              pending === 'verify-pause'
                ? 'live.common.checking'
                : 'live.companion.checkPauseStatus',
            )}
            disabled={pending !== undefined}
            onPress={verifyPauseState}
            testID="live-ios-check-pause-status"
          />
        </>
      ) : null}

      {home.state.kind === 'loading' ? (
        <LiveLoading label={t('live.companion.reminderLoading')} />
      ) : null}
      {home.state.kind === 'error' && !pauseVerificationVisible ? (
        <LiveError
          title={t('live.companion.reminderUnavailable')}
          problem={home.state.problem}
          onRetry={() => home.reload()}
        />
      ) : null}
      {home.state.kind === 'ready' &&
      !iosAutomation &&
      !pauseVerificationVisible ? (
        <LiveError
          title={t('live.home.platformMismatch')}
          problem={nativePlatformMismatchProblem}
          onRetry={() => home.reload()}
        />
      ) : null}
      {home.state.kind === 'ready' && iosAutomation ? (
        <>
          {home.state.refreshProblem ? (
            <>
              <LiveRefreshProblem problem={home.state.refreshProblem} />
              {!pauseVerificationVisible ? (
                <Button
                  label={t('live.companion.checkReminderStatus')}
                  disabled={pending !== undefined || home.state.refreshing}
                  onPress={checkIosStatus}
                  testID="live-ios-check-automation-status"
                />
              ) : null}
            </>
          ) : null}
          {iosSetupRecoveryVisible &&
          !iosHomeRecoveryVisible &&
          !pauseVerificationVisible ? (
            <Button
              label={t('live.companion.checkReminderStatus')}
              disabled={pending !== undefined}
              onPress={checkIosStatus}
              testID="live-ios-check-setup-status"
            />
          ) : null}
          {iosConfigurationStatusRecoveryVisible &&
          !iosHomeRecoveryVisible &&
          !iosSetupRecoveryVisible &&
          !pauseVerificationVisible ? (
            <Button
              label={t('live.companion.checkReminderStatus')}
              disabled={pending !== undefined}
              onPress={checkIosStatus}
              testID="live-ios-check-configuration-status"
            />
          ) : null}
          {iosConfigurationLoading &&
          !iosHomeRecoveryVisible &&
          !iosSetupRecoveryVisible &&
          !pauseVerificationVisible ? (
            <LiveLoading label={t('live.companion.reminderLoading')} />
          ) : null}
          {iosMessageRepairVisible &&
          !iosHomeRecoveryVisible &&
          !iosSetupRecoveryVisible &&
          !iosConfigurationStatusRecoveryVisible &&
          !pauseVerificationVisible ? (
            <Button
              label={t('live.guidedSetup.writeMessage')}
              disabled={pending !== undefined}
              onPress={onOpenMessage}
              testID="live-ios-open-message"
            />
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
            {reminderReady ? (
              <StatusRow
                title={t('live.home.notificationVisibility')}
                detail={t(reminderPermissionKeys[reminderReady.authorization])}
                tone={
                  reminderReady.authorization === 'authorized'
                    ? 'positive'
                    : 'warning'
                }
              />
            ) : null}
          </Card>
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
          {visibleComposerIssues.length > 0 &&
          !protectedReviewOpen &&
          !pauseVerificationVisible ? (
            <Card>
              {visibleComposerIssues.map(issue => (
                <StatusRow
                  key={issue.id}
                  title={t(safeReasonMessageKey(issue.code))}
                  tone={issue.severity === 'blocking' ? 'critical' : 'warning'}
                />
              ))}
            </Card>
          ) : null}

          {reminder.kind === 'loading' && !actionReviewOpen ? (
            <LiveLoading label={t('live.companion.reminderLoading')} />
          ) : null}
          {reminderNeedsCheck &&
          !protectedReviewOpen &&
          !pauseVerificationVisible &&
          !iosHomeRecoveryVisible &&
          !iosSetupRecoveryVisible &&
          !iosConfigurationLoading &&
          !iosConfigurationStatusRecoveryVisible &&
          !iosMessageRepairVisible ? (
            <>
              <ReadinessBanner
                title={t('live.companion.reminderUnavailable')}
                detail={t('live.error.bridge')}
                tone="warning"
              />
              <Button
                label={t('live.companion.checkReminderStatus')}
                disabled={pending !== undefined || !homeTrusted}
                onPress={() => loadReminder()}
                testID="live-reminder-check-status"
              />
            </>
          ) : null}
          {reminderReady?.authorization === 'not-determined' &&
          homeTrusted &&
          !iosSetupRecoveryVisible &&
          !iosConfigurationLoading &&
          !iosConfigurationStatusRecoveryVisible &&
          !iosMessageRepairVisible &&
          !accountDeletionInProgress &&
          !actionReviewOpen ? (
            <Button
              label={t('live.companion.requestPermission')}
              disabled={pending !== undefined}
              onPress={requestPermission}
              testID="live-reminder-permission"
            />
          ) : null}
          {reminderReady?.authorization === 'denied' &&
          homeTrusted &&
          !iosSetupRecoveryVisible &&
          !iosConfigurationLoading &&
          !iosConfigurationStatusRecoveryVisible &&
          !iosMessageRepairVisible &&
          !accountDeletionInProgress &&
          !actionReviewOpen ? (
            <Button
              label={t('live.companion.openNotificationSettings')}
              disabled={pending !== undefined}
              onPress={openNotificationSettings}
              testID="live-reminder-settings"
            />
          ) : null}
          {canReviewActivation ? (
            <Button
              label={t('live.companion.reviewActivation')}
              disabled={pending !== undefined || !homeTrusted}
              onPress={() => prepareActivationReview('activate')}
              testID="live-ios-review-activation"
            />
          ) : null}
          {canReviewResume ? (
            <Button
              label={t('live.companion.reviewResume')}
              disabled={pending !== undefined || !homeTrusted}
              onPress={() => prepareActivationReview('resume')}
              testID="live-ios-review-resume"
            />
          ) : null}
          {canReviewPause ? (
            <Button
              label={t('live.companion.pause')}
              disabled={pending !== undefined || !homeTrusted}
              onPress={() => {
                if (canReviewPause && home.state.kind === 'ready') {
                  setConfirmPause(home.state.result.envelope.revision);
                }
              }}
              variant="secondary"
              testID="live-ios-review-pause"
            />
          ) : null}
          {activationReview && activationReviewModeCurrent ? (
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
              {iosActivationSnapshotComplete(activationReview.review) &&
              activationReviewCurrent &&
              reminderAllowsActivation &&
              !pauseVerificationVisible ? (
                <Button
                  label={t(
                    activationReview.kind === 'activate'
                      ? 'live.companion.activate'
                      : 'live.companion.resume',
                  )}
                  disabled={pending !== undefined || !activationReviewCurrent}
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
          {confirmPause && pauseReviewCurrent ? (
            <Card>
              <AppText variant="heading">
                {t('live.companion.pauseReview')}
              </AppText>
              <AppText>{t('live.companion.pauseBody')}</AppText>
              <Button
                label={t('live.companion.pauseConfirm')}
                disabled={pending !== undefined || !pauseReviewCurrent}
                onPress={pauseReminders}
                variant="danger"
                testID="live-ios-confirm-pause"
              />
              <Button
                label={t('live.common.cancel')}
                disabled={pending !== undefined}
                onPress={() => setConfirmPause(undefined)}
                variant="secondary"
              />
            </Card>
          ) : null}
        </>
      ) : null}

      {home.state.kind === 'ready' &&
      iosAutomation &&
      !protectedReviewOpen &&
      !pauseVerificationVisible &&
      reminderReady &&
      hasReminderWarnings ? (
        <>
          <SectionHeading title={t('live.companion.reminderTitle')} />
          <Card>
            {reminderReady.failedCount > 0 ? (
              <StatusRow
                title={t('live.companion.failedReminderCount', {
                  count: reminderReady.failedCount,
                })}
                tone="warning"
              />
            ) : null}
            {reminderReady.earliestUnscheduledCivilDate ? (
              <StatusRow
                title={t('live.companion.earliestUnscheduled')}
                detail={formatLiveDate(
                  reminderReady.earliestUnscheduledCivilDate,
                  language,
                )}
                tone="warning"
              />
            ) : null}
            {reminderReady.truncated ? (
              <StatusRow title={t('live.companion.truncated')} tone="warning" />
            ) : null}
          </Card>
        </>
      ) : null}
      {home.state.kind === 'ready' &&
      iosAutomation &&
      !actionReviewOpen &&
      reminderSupportAvailable ? (
        <>
          <Button
            label={t(
              reminderSupportExpanded
                ? 'live.companion.hideReminderDetails'
                : 'live.companion.showReminderDetails',
            )}
            onPress={() => setReminderSupportExpanded(expanded => !expanded)}
            variant="secondary"
            testID="live-reminder-support-toggle"
          />
          {reminderSupportExpanded ? (
            <Card testID="live-reminder-support-details">
              <AppText color="muted">
                {t('live.companion.reminderDetailsBody')}
              </AppText>
              {reminderReady ? (
                <>
                  <StatusRow
                    title={t('live.companion.scheduled', {
                      count: reminderReady.scheduledCount,
                    })}
                  />
                  <StatusRow
                    title={t('live.companion.planned', {
                      count: reminderReady.plannedDateCount,
                    })}
                  />
                  {reminderReady.code ? (
                    <AppText color="muted" variant="caption">
                      {t('live.common.code', { value: reminderReady.code })}
                    </AppText>
                  ) : null}
                </>
              ) : null}
              {reminder.kind === 'error' ? (
                <AppText color="muted" variant="caption">
                  {t('live.common.code', { value: reminder.code })}
                </AppText>
              ) : null}
              {composerIssues.map(issue => (
                <React.Fragment key={issue.id}>
                  <StatusRow
                    title={t(safeReasonMessageKey(issue.code))}
                    tone={
                      issue.severity === 'blocking' ? 'critical' : 'warning'
                    }
                  />
                  <AppText color="muted" variant="caption">
                    {t('live.common.code', { value: issue.code })}
                  </AppText>
                </React.Fragment>
              ))}
            </Card>
          ) : null}
        </>
      ) : null}
      {homeTrusted &&
      iosAutomation &&
      reminderTrusted &&
      setupTrusted &&
      !iosSetupRecoveryVisible &&
      !iosConfigurationLoading &&
      !iosConfigurationStatusRecoveryVisible &&
      !iosMessageRepairVisible &&
      !reminderPrimaryRecoveryVisible &&
      !accountDeletionInProgress &&
      iosPolicyRepairRequired &&
      !protectedReviewOpen &&
      !pauseVerificationVisible ? (
        <Button
          label={t('live.guidedSetup.chooseWindow')}
          disabled={pending !== undefined}
          onPress={onOpenSchedule}
          testID="live-ios-open-schedule"
        />
      ) : null}
    </Screen>
  );
}

export function LiveAutomationScreen({
  capability,
  companionPort,
  onBack,
  onOpenMessage,
  onOpenSchedule,
  port,
}: {
  capability: PlatformCapability;
  companionPort: LiveCompanionPort;
  onBack: () => void;
  onOpenMessage: () => void;
  onOpenSchedule: () => void;
  port: LiveAppPort;
}) {
  return capability.platform === 'android' ? (
    <LiveAndroidAutomation
      onBack={onBack}
      onOpenMessage={onOpenMessage}
      onOpenSchedule={onOpenSchedule}
      port={port}
    />
  ) : (
    <LiveIosCompanion
      companionPort={companionPort}
      onBack={onBack}
      onOpenMessage={onOpenMessage}
      onOpenSchedule={onOpenSchedule}
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
