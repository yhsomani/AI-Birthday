import React from 'react';
import {
  AccessibilityInfo,
  AppState,
  Platform,
  type AppStateStatus,
} from 'react-native';
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react-native';

import { ThemeProvider } from '../../app/providers/ThemeProvider';
import type { ProjectionInvalidation } from '../../application/ports/AppProjectionPort';
import type { AccountProjection } from '../../domain/account/model';
import type { AndroidTestPhase } from '../../domain/automation/model';
import type { PolicyEditorProjection } from '../../domain/birthdays/model';
import type { HomeProjection } from '../../domain/home/model';
import type { MessageEditorProjection } from '../../domain/messages/model';
import type {
  GateDecision,
  ReadinessProjection,
} from '../../domain/readiness/model';
import type {
  ActivationReviewHandle,
  IssueId,
  NativeRevision,
  PrivateEmail,
  PrivateMessageText,
  SafeSupportCode,
  TestReviewHandle,
} from '../../domain/shared/brand';
import type { PlatformCapability } from '../../domain/shared/platform';
import type { NativeResult } from '../../domain/shared/result';
import type {
  LocalDate,
  LocalTime,
  UtcInstant,
} from '../../domain/shared/temporal';
import type { SetupProjection } from '../../domain/setup/model';
import type { CompanionReminderState } from '../../infrastructure/native/ios/CompanionNativeGateway';
import { LocalizationProvider } from '../../localization/LocalizationProvider';
import { appI18n } from '../../localization/i18n';
import type { LiveAppPort, LiveCompanionPort } from './LiveAppPort';
import { LiveAutomationScreen } from './LiveAutomationScreen';

jest.mock('react-native-localize', () => ({
  getLocales: () => [{ languageCode: 'en' }],
}));

jest.mock('react-native-safe-area-context', () => {
  const TestReact = require('react');
  const { View } = require('react-native');
  return {
    SafeAreaView: (props: { children?: unknown; [key: string]: unknown }) => {
      const { children, ...viewProps } = props;
      return TestReact.createElement(View, viewProps, children);
    },
  };
});

const originalPlatform = Platform.OS;
const generatedAt = '2026-07-19T07:00:00Z' as UtcInstant;
const revision = (value: string) => value as NativeRevision;

const ok = <Value,>(
  value: Value,
  currentRevision = revision('1'),
): NativeResult<Value> => ({
  kind: 'ok',
  envelope: {
    contractVersion: 1,
    generatedAt,
    revision: currentRevision,
    value,
  },
});

const nativeInternalError = <Value,>(
  supportCode: string,
): NativeResult<Value> => ({
  kind: 'error',
  problem: {
    kind: 'internal',
    supportCode: supportCode as SafeSupportCode,
  },
});

const captureAppStateListeners = () => {
  const listeners: Array<(state: AppStateStatus) => void> = [];
  jest
    .spyOn(AppState, 'addEventListener')
    .mockImplementation((_type, listener) => {
      listeners.push(listener);
      return { remove: jest.fn() };
    });
  return listeners;
};

const androidCapability: PlatformCapability = {
  platform: 'android',
  deliveryMode: 'unattended-device-sms',
  minimumApiLevel: 29,
  unattendedSms: 'release-gated',
  userComposer: 'available-as-explicit-alternative',
};

const iosCapability: PlatformCapability = {
  platform: 'ios',
  deliveryMode: 'user-controlled-composer',
  unattendedSms: 'unavailable',
  userComposer: 'required',
};

const invalidReceipt: GateDecision = {
  kind: 'blocked',
  issues: [
    {
      blocks: ['activation'],
      code: 'test-receipt-invalid',
      id: 'receipt-invalid' as IssueId,
      severity: 'blocking',
    },
  ],
};

const androidReadiness = (
  activation: GateDecision = { kind: 'allowed' },
): ReadinessProjection & { platform: 'android' } => ({
  platform: 'android',
  test: { kind: 'allowed' },
  activation,
  birthday: { kind: 'allowed' },
  lastCheckedAt: generatedAt,
});

const androidHome = (
  effective: Extract<
    HomeProjection['automation'],
    { platform: 'android' }
  >['effective'],
  activation: GateDecision = { kind: 'allowed' },
): HomeProjection => ({
  automation: {
    platform: 'android',
    desired:
      effective === 'active' || effective === 'action-required'
        ? 'on'
        : 'paused',
    effective,
    readiness: androidReadiness(activation),
  },
  counts: {
    enabled: 1,
    needsAttention: 0,
    unavailable: 0,
    today: 0,
    nextSevenDays: 1,
  },
  contactsSync: {
    kind: 'fresh',
    completedAt: generatedAt,
    contactCount: 1,
  },
});

const iosReadiness: ReadinessProjection & { platform: 'ios' } = {
  platform: 'ios',
  composer: { kind: 'allowed' },
  unattendedAutomation: {
    kind: 'unavailable',
    reason: 'platform-composer-only',
  },
  lastCheckedAt: generatedAt,
};

const iosHome = (
  effective: Extract<
    HomeProjection['automation'],
    { platform: 'ios' }
  >['effective'] = 'ready',
): HomeProjection => ({
  automation: {
    platform: 'ios',
    desired: effective === 'ready' ? 'composer-reminders-on' : 'paused',
    effective,
    readiness: iosReadiness,
  },
  counts: {
    enabled: 1,
    needsAttention: 0,
    unavailable: 0,
    today: 0,
    nextSevenDays: 1,
  },
  contactsSync: {
    kind: 'fresh',
    completedAt: generatedAt,
    contactCount: 1,
  },
});

type IosAutomationProjection = Extract<
  HomeProjection['automation'],
  { platform: 'ios' }
>;

const iosHomeWithMode = (
  desired: IosAutomationProjection['desired'],
  effective: IosAutomationProjection['effective'],
): HomeProjection => ({
  ...iosHome(),
  automation: {
    platform: 'ios',
    desired,
    effective,
    readiness: iosReadiness,
  },
});

const iosActivationReview = {
  platform: 'ios' as const,
  handle: 'ios-activation-review' as ActivationReviewHandle,
  reminderRecipientCount: 1,
  plannedReminderCount: 3,
  reminderWindowLabel: '09:00–11:00',
  reminderHorizon: 'full' as const,
  coexistence: 'clear' as const,
  contactsReady: true,
  messageUiReady: true,
  protectedStorageReady: true,
  readiness: iosReadiness,
  deliveryMode: 'user-controlled-composer' as const,
  limitationsDisclosure: 'Best effort.',
};

const androidAccount = (
  kind: 'test-only' | 'paused-repair' | 'automation-active' = 'test-only',
): AccountProjection => ({
  kind: 'connected',
  displayEmail: 'user@example.test' as PrivateEmail,
  sender: {
    platform: 'android',
    kind,
    epochLabel: 'This device',
  },
});

const iosAccount: AccountProjection = {
  kind: 'connected',
  displayEmail: 'user@example.test' as PrivateEmail,
  sender: {
    platform: 'ios',
    kind: 'companion',
    unattendedAutomation: 'unavailable',
    composer: 'available',
  },
};

const configuredMessage: MessageEditorProjection = {
  kind: 'configured',
  draft: {
    language: 'en',
    tone: 'warm',
    placeholderMode: { kind: 'generic', requiredCount: 0 },
    text: 'Happy birthday!' as PrivateMessageText,
    requestedSegmentCap: 1,
  },
};

const configuredPolicy: PolicyEditorProjection = {
  kind: 'configured',
  draft: {
    primaryStart: '09:00' as LocalTime,
    primaryEnd: '11:00' as LocalTime,
    latePolicy: { kind: 'none' },
    dailyCap: 10,
  },
};

const iosSetup = (initialActivationCompleted = false): SetupProjection => ({
  step: 'complete',
  initialActivationCompleted,
  eligibility: {
    kind: 'supported',
    capability: iosCapability,
    channelLabel: 'test',
    chargeDisclosureVersion: 'composer-v1',
  },
  account: iosAccount,
  contacts: {
    kind: 'fresh',
    completedAt: generatedAt,
    contactCount: 1,
  },
  readiness: iosReadiness,
  automation: iosHome('paused').automation,
});

const reminder = (
  overrides: Partial<CompanionReminderState> = {},
): CompanionReminderState => ({
  authorization: 'authorized',
  failedCount: 0,
  kind: 'ok',
  plannedDateCount: 3,
  scheduledCount: 3,
  truncated: false,
  ...overrides,
});

const deferred = <Value,>() => {
  let resolve!: (value: Value) => void;
  const promise = new Promise<Value>(resolver => {
    resolve = resolver;
  });
  return { promise, resolve };
};

const projectionSequence = <Value,>(
  value: Value,
  revisions: readonly NativeRevision[],
) => {
  let index = 0;
  return jest.fn(async () => {
    const currentRevision = revisions[Math.min(index, revisions.length - 1)]!;
    index += 1;
    return ok(value, currentRevision);
  });
};

type AutomationHarness = Readonly<{
  activate: jest.Mock;
  companionPort: LiveCompanionPort;
  emit(event: ProjectionInvalidation): void;
  getHome: jest.Mock;
  getAccount: jest.Mock;
  getLatestTest: jest.Mock;
  getMessageEditor: jest.Mock;
  getPolicyEditor: jest.Mock;
  getReminderStatus: jest.Mock;
  getSetup: jest.Mock;
  pauseAll: jest.Mock;
  port: LiveAppPort;
  prepareActivation: jest.Mock;
  prepareResume: jest.Mock;
  prepareTest: jest.Mock;
  requestReminderAuthorization: jest.Mock;
  resume: jest.Mock;
  startTest: jest.Mock;
}>;

const createAutomationHarness = ({
  projectionRevision = revision('11'),
  androidAccountKind = 'test-only',
  setupRevision = revision('1'),
  initialActivationCompleted = false,
  activate = jest.fn(async () => ok(androidHome('active').automation)),
  getAccount = jest.fn(async () =>
    ok(androidAccount(androidAccountKind), projectionRevision),
  ),
  getHome = jest.fn(async () => ok(androidHome('test-only'), revision('11'))),
  getLatestTest = jest.fn(async () =>
    ok(
      {
        platform: 'android' as const,
        phase: 'passed' as const,
        updatedAt: generatedAt,
      },
      projectionRevision,
    ),
  ),
  getMessageEditor = jest.fn(async () =>
    ok(
      configuredMessage,
      Platform.OS === 'ios' ? setupRevision : projectionRevision,
    ),
  ),
  getPolicyEditor = jest.fn(async () =>
    ok(
      configuredPolicy,
      Platform.OS === 'ios' ? setupRevision : projectionRevision,
    ),
  ),
  getReminderStatus = jest.fn(async () => ({
    kind: 'ok' as const,
    value: reminder(),
  })),
  getSetup = jest.fn(async () =>
    ok(iosSetup(initialActivationCompleted), setupRevision),
  ),
  pauseAll = jest.fn(async () => ok(androidHome('paused-repair').automation)),
  prepareActivation = jest.fn(async () =>
    ok(
      {
        platform: 'android' as const,
        handle: 'activation-review' as ActivationReviewHandle,
        enabledRecipientCount: 1,
        attentionCount: 0,
        templatePreview: 'Happy birthday!' as PrivateMessageText,
        windowLabel: '09:00–11:00',
        simLabel: 'SIM 1',
        dailyCap: 10,
        limitationsDisclosure: 'Best effort.',
      },
      revision('21'),
    ),
  ),
  prepareResume = jest.fn(async () =>
    ok(
      {
        platform: 'android' as const,
        handle: 'resume-review' as ActivationReviewHandle,
        enabledRecipientCount: 1,
        attentionCount: 0,
        templatePreview: 'Happy birthday!' as PrivateMessageText,
        windowLabel: '09:00–11:00',
        simLabel: 'SIM 1',
        dailyCap: 10,
        limitationsDisclosure: 'Best effort.',
      },
      revision('22'),
    ),
  ),
  prepareTest = jest.fn(async () =>
    ok(
      {
        platform: 'android' as const,
        handle: 'test-review' as TestReviewHandle,
        maskedDestination: '+91 •••••• 3210',
        exactText: 'Birthday test message' as PrivateMessageText,
        simLabel: 'SIM 1',
        segmentCount: 1,
        chargeDisclosure: 'Carrier charge.',
      },
      revision('17'),
    ),
  ),
  requestReminderAuthorization = jest.fn(async () => ({
    kind: 'ok' as const,
    value: reminder(),
  })),
  resume = jest.fn(async () => ok(androidHome('active').automation)),
  startTest = jest.fn(async () =>
    ok({
      platform: 'android' as const,
      phase: 'prepared' as const,
      updatedAt: generatedAt,
    }),
  ),
}: Partial<{
  projectionRevision: NativeRevision;
  androidAccountKind: 'test-only' | 'paused-repair' | 'automation-active';
  setupRevision: NativeRevision;
  initialActivationCompleted: boolean;
  activate: jest.Mock;
  getAccount: jest.Mock;
  getHome: jest.Mock;
  getLatestTest: jest.Mock;
  getMessageEditor: jest.Mock;
  getPolicyEditor: jest.Mock;
  getReminderStatus: jest.Mock;
  getSetup: jest.Mock;
  pauseAll: jest.Mock;
  prepareActivation: jest.Mock;
  prepareResume: jest.Mock;
  prepareTest: jest.Mock;
  requestReminderAuthorization: jest.Mock;
  resume: jest.Mock;
  startTest: jest.Mock;
}> = {}): AutomationHarness => {
  const listeners = new Set<(event: ProjectionInvalidation) => void>();
  const port = {
    activate,
    getAccount,
    getHome,
    getLatestTest,
    getMessageEditor,
    getPolicyEditor,
    getSetup,
    pauseAll,
    prepareActivation,
    prepareResume,
    prepareTest,
    resume,
    startTest,
    subscribeInvalidations: (
      listener: (event: ProjectionInvalidation) => void,
    ) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
  } as unknown as LiveAppPort;
  const companionPort = {
    canOpenComposer: jest.fn(async () => false),
    getReminderStatus,
    openNotificationSettings: jest.fn(async () => ({
      kind: 'ok' as const,
      value: null,
    })),
    openUserConfirmedComposer: jest.fn(),
    prepareComposerReview: jest.fn(),
    requestReminderAuthorization,
  } satisfies LiveCompanionPort;
  return {
    activate,
    companionPort,
    emit: event => listeners.forEach(listener => listener(event)),
    getAccount,
    getHome,
    getLatestTest,
    getMessageEditor,
    getPolicyEditor,
    getReminderStatus,
    getSetup,
    pauseAll,
    port,
    prepareActivation,
    prepareResume,
    prepareTest,
    requestReminderAuthorization,
    resume,
    startTest,
  };
};

const renderAutomation = async (
  harness: AutomationHarness,
  capability: PlatformCapability = androidCapability,
  onOpenMessage: () => void = jest.fn(),
  onOpenSchedule: () => void = jest.fn(),
) =>
  render(
    <LocalizationProvider>
      <ThemeProvider initialPreference="light">
        <LiveAutomationScreen
          capability={capability}
          companionPort={harness.companionPort}
          onBack={jest.fn()}
          onOpenMessage={onOpenMessage}
          onOpenSchedule={onOpenSchedule}
          port={harness.port}
        />
      </ThemeProvider>
    </LocalizationProvider>,
  );

beforeEach(() => {
  jest.clearAllMocks();
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: 'android',
  });
  jest
    .spyOn(AccessibilityInfo, 'isHighTextContrastEnabled')
    .mockResolvedValue(false);
  jest
    .spyOn(AccessibilityInfo, 'isReduceMotionEnabled')
    .mockResolvedValue(false);
});

afterEach(async () => {
  await cleanup();
  await appI18n.changeLanguage('en');
  jest.restoreAllMocks();
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: originalPlatform,
  });
});

it.each([
  ['not-configured', { kind: 'allowed' } as GateDecision],
  ['test-only', invalidReceipt],
  ['paused-repair', invalidReceipt],
] as const)(
  'shows the Android TEST form only when authoritative state requires it (%s)',
  async (effective, activation) => {
    const harness = createAutomationHarness({
      getHome: jest.fn(async () =>
        ok(androidHome(effective, activation), revision('11')),
      ),
    });
    await renderAutomation(harness);

    expect(await screen.findByTestId('live-test-phone')).toBeTruthy();
    expect(screen.getByTestId('live-prepare-test')).toBeTruthy();
  },
);

it('treats only exact NATIVE_NOT_CONFIGURED as settled latest-TEST absence', async () => {
  const harness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(androidHome('not-configured'), revision('11')),
    ),
    getLatestTest: jest.fn(async () =>
      nativeInternalError('NATIVE_NOT_CONFIGURED'),
    ),
  });
  await renderAutomation(harness);

  await fireEvent.changeText(
    await screen.findByTestId('live-test-phone'),
    '+919876543210',
  );
  await fireEvent.press(screen.getByTestId('live-prepare-test'));
  await fireEvent.press(await screen.findByTestId('live-start-test'));

  await waitFor(() => expect(harness.startTest).toHaveBeenCalledTimes(1));
  expect(harness.startTest).toHaveBeenCalledWith({
    handle: 'test-review',
    expectedRevision: '17',
  });
});

it.each([
  ['another internal error', nativeInternalError('LATEST_TEST_READ_FAILED')],
  [
    'a stale revision error',
    {
      kind: 'error' as const,
      problem: {
        kind: 'stale-revision' as const,
        latestRevision: revision('12'),
      },
    },
  ],
  [
    'an iOS projection mismatch',
    ok({
      kind: 'unavailable' as const,
      platform: 'ios' as const,
      reason: 'platform-composer-only' as const,
    }),
  ],
] as const)(
  'suppresses Android TEST entry when latest-TEST truth is %s',
  async (_label, latestResult) => {
    const getLatestTest = jest.fn(async () => latestResult);
    const harness = createAutomationHarness({
      getHome: jest.fn(async () =>
        ok(androidHome('not-configured'), revision('11')),
      ),
      getLatestTest,
    });
    await renderAutomation(harness);

    await waitFor(() => expect(getLatestTest).toHaveBeenCalledTimes(1));
    expect(screen.queryByTestId('live-test-phone')).toBeNull();
    expect(screen.queryByTestId('live-prepare-test')).toBeNull();
    expect(harness.prepareTest).not.toHaveBeenCalled();
    expect(harness.startTest).not.toHaveBeenCalled();
  },
);

it('suppresses the Android TEST form during refresh and after a non-absence refresh failure', async () => {
  const appStateListeners = captureAppStateListeners();
  const nextTest = deferred<NativeResult<never>>();
  const getLatestTest = jest
    .fn()
    .mockResolvedValueOnce(
      ok(
        {
          platform: 'android' as const,
          phase: 'passed' as const,
          updatedAt: generatedAt,
        },
        revision('11'),
      ),
    )
    .mockImplementationOnce(() => nextTest.promise);
  const harness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(androidHome('not-configured'), revision('11')),
    ),
    getLatestTest,
  });
  await renderAutomation(harness);
  await screen.findByTestId('live-test-phone');

  await act(async () => {
    appStateListeners.forEach(listener => listener('active'));
    await Promise.resolve();
  });
  await waitFor(() =>
    expect(screen.queryByTestId('live-test-phone')).toBeNull(),
  );
  expect(screen.queryByTestId('live-prepare-test')).toBeNull();

  await act(async () => {
    nextTest.resolve(nativeInternalError('LATEST_TEST_REFRESH_FAILED'));
    await nextTest.promise;
  });
  expect(screen.queryByTestId('live-test-phone')).toBeNull();
  expect(screen.queryByTestId('live-prepare-test')).toBeNull();
  expect(harness.prepareTest).not.toHaveBeenCalled();
});

it('keeps an open TEST review non-actionable when latest-TEST truth becomes unstable', async () => {
  const appStateListeners = captureAppStateListeners();
  const nextTest = deferred<NativeResult<never>>();
  const getLatestTest = jest
    .fn()
    .mockResolvedValueOnce(
      ok(
        {
          platform: 'android' as const,
          phase: 'passed' as const,
          updatedAt: generatedAt,
        },
        revision('11'),
      ),
    )
    .mockImplementationOnce(() => nextTest.promise);
  const harness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(androidHome('not-configured'), revision('11')),
    ),
    getLatestTest,
  });
  await renderAutomation(harness);
  await fireEvent.changeText(
    await screen.findByTestId('live-test-phone'),
    '+919876543210',
  );
  await fireEvent.press(screen.getByTestId('live-prepare-test'));
  await screen.findByTestId('live-start-test');

  await act(async () => {
    appStateListeners.forEach(listener => listener('active'));
    await Promise.resolve();
  });
  await waitFor(() =>
    expect(screen.queryByTestId('live-start-test')).toBeNull(),
  );
  expect(harness.startTest).not.toHaveBeenCalled();

  await act(async () => {
    nextTest.resolve(nativeInternalError('LATEST_TEST_REFRESH_FAILED'));
    await nextTest.promise;
  });
  expect(screen.queryByTestId('live-start-test')).toBeNull();
  expect(harness.startTest).not.toHaveBeenCalled();
});

it('keeps the raw Android TEST reason behind accessible support details', async () => {
  const harness = createAutomationHarness({
    getLatestTest: jest.fn(async () =>
      ok(
        {
          platform: 'android' as const,
          phase: 'failed' as const,
          reason: 'coordination-unavailable' as const,
          updatedAt: generatedAt,
        },
        revision('11'),
      ),
    ),
  });
  await renderAutomation(harness);

  expect(
    await screen.findByText(
      'The online duplicate-safety check is unavailable. Wait and try again; no unsafe send will start.',
    ),
  ).toBeTruthy();
  expect(
    screen.queryByText('Technical code: coordination-unavailable'),
  ).toBeNull();
  const toggle = screen.getByTestId('live-automation-support-toggle');
  expect(toggle.props.accessibilityRole).toBe('button');
  await fireEvent.press(toggle);
  expect(screen.getByTestId('live-automation-support-details')).toBeTruthy();
  expect(
    screen.getByText('Technical code: coordination-unavailable'),
  ).toBeTruthy();
});

it.each(['test-only', 'paused-repair'] as const)(
  'keeps a valid %s state compact until Run another test is chosen',
  async effective => {
    const harness = createAutomationHarness({
      getHome: jest.fn(async () => ok(androidHome(effective), revision('11'))),
      androidAccountKind:
        effective === 'paused-repair' ? 'paused-repair' : 'test-only',
    });
    await renderAutomation(harness);

    expect(await screen.findByTestId('live-run-another-test')).toBeTruthy();
    expect(screen.queryByTestId('live-test-phone')).toBeNull();
    await fireEvent.press(screen.getByTestId('live-run-another-test'));
    expect(screen.getByTestId('live-test-phone')).toBeTruthy();
  },
);

it('does not treat an unrelated activation blocker as a reason to rerun TEST', async () => {
  const activation: GateDecision = {
    kind: 'blocked',
    issues: [
      {
        blocks: ['activation'],
        code: 'account-mismatch',
        id: 'account-mismatch' as IssueId,
        severity: 'blocking',
      },
    ],
  };
  const harness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(androidHome('test-only', activation), revision('11')),
    ),
  });
  await renderAutomation(harness);
  await screen.findByText('Test only');

  expect(screen.queryByTestId('live-run-another-test')).toBeNull();
  expect(screen.queryByTestId('live-test-phone')).toBeNull();
  expect(screen.queryByTestId('live-review-activation')).toBeNull();
});

it('scopes active Android readiness to the native BIRTHDAY gate instead of TEST and ACTIVATION sentinels', async () => {
  const testSentinel: GateDecision = {
    kind: 'blocked',
    issues: [
      {
        blocks: ['test'],
        code: 'account-reconnect-required',
        id: 'native-test-sentinel' as IssueId,
        severity: 'blocking',
      },
    ],
  };
  const activationSentinel: GateDecision = {
    kind: 'blocked',
    issues: [
      {
        blocks: ['activation'],
        code: 'policy-suspended',
        id: 'native-activation-sentinel' as IssueId,
        severity: 'blocking',
      },
    ],
  };
  const birthdayIssue: GateDecision = {
    kind: 'blocked',
    issues: [
      {
        blocks: ['birthday'],
        code: 'contacts-stale',
        id: 'native-birthday-issue' as IssueId,
        severity: 'blocking',
      },
    ],
  };
  const nativeModeHome = (
    effective: 'active' | 'action-required',
    birthday: GateDecision,
  ): HomeProjection => ({
    ...androidHome(effective),
    automation: {
      platform: 'android',
      desired: 'on',
      effective,
      readiness: {
        platform: 'android',
        test: testSentinel,
        activation: activationSentinel,
        birthday,
        lastCheckedAt: generatedAt,
      },
    },
  });
  const sentinelCopy = [
    'Reconnect the same Google account before continuing.',
    'This feature is paused by the current safety policy. Review readiness before continuing.',
  ];

  const activeHarness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(nativeModeHome('active', { kind: 'allowed' })),
    ),
  });
  await renderAutomation(activeHarness);
  await screen.findByText('Current status');
  expect(screen.queryByTestId('live-automation-readiness-issues')).toBeNull();
  expect(screen.queryByTestId('live-automation-support-toggle')).toBeNull();
  for (const copy of sentinelCopy) {
    expect(screen.queryByText(copy)).toBeNull();
  }
  expect(screen.queryByText(/account-reconnect-required/u)).toBeNull();
  expect(screen.queryByText(/policy-suspended/u)).toBeNull();
  await cleanup();

  const actionRequiredHarness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(nativeModeHome('action-required', birthdayIssue)),
    ),
  });
  await renderAutomation(actionRequiredHarness);
  expect(
    await screen.findByText(
      'Contacts are out of date. Sync again before approving or sending anything.',
    ),
  ).toBeTruthy();
  for (const copy of sentinelCopy) {
    expect(screen.queryByText(copy)).toBeNull();
  }

  await fireEvent.press(screen.getByTestId('live-automation-support-toggle'));
  expect(
    await screen.findByText('Technical code: contacts-stale'),
  ).toBeTruthy();
  expect(screen.getAllByText(/^Technical code:/u)).toHaveLength(1);
  expect(
    screen.queryByText('Technical code: account-reconnect-required'),
  ).toBeNull();
  expect(screen.queryByText('Technical code: policy-suspended')).toBeNull();
});

it.each([
  'active',
  'action-required',
  'standby',
  'transfer-pending',
  'deleting',
] as const)(
  'does not offer TEST entry while Android state is %s',
  async effective => {
    const harness = createAutomationHarness({
      getHome: jest.fn(async () => ok(androidHome(effective), revision('11'))),
    });
    await renderAutomation(harness);
    await screen.findByTestId('live-automation-screen');

    expect(screen.queryByTestId('live-run-another-test')).toBeNull();
    expect(screen.queryByTestId('live-test-phone')).toBeNull();
    expect(screen.queryByTestId('live-prepare-test')).toBeNull();
  },
);

it.each(['standby', 'transfer-pending', 'deleting'] as const)(
  'hides Android Schedule repair while sender lifecycle is %s',
  async effective => {
    const harness = createAutomationHarness({
      getHome: jest.fn(async () => ok(androidHome(effective), revision('11'))),
    });
    await renderAutomation(harness);
    await screen.findByText('Current status');

    expect(screen.queryByTestId('live-automation-open-schedule')).toBeNull();
    expect(screen.queryByTestId('live-policy-editor')).toBeNull();
    expect(screen.queryByTestId('live-policy-preview')).toBeNull();
    expect(screen.queryByTestId('live-policy-save')).toBeNull();
  },
);

it('offers a checked retry for an iOS reminder error and hides its raw code by default', async () => {
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: 'ios',
  });
  const harness = createAutomationHarness({
    getHome: jest.fn(async () => ok(iosHome('paused'))),
    getReminderStatus: jest.fn(async () => ({
      code: 'REMINDER_TEST_UNAVAILABLE',
      kind: 'error' as const,
    })),
  });
  await renderAutomation(harness, iosCapability);

  expect(await screen.findByTestId('live-reminder-check-status')).toBeTruthy();
  expect(screen.queryByTestId('live-ios-review-activation')).toBeNull();
  expect(
    screen.queryByText('Technical code: REMINDER_TEST_UNAVAILABLE'),
  ).toBeNull();
  await fireEvent.press(screen.getByTestId('live-reminder-support-toggle'));
  expect(screen.getByTestId('live-reminder-support-details')).toBeTruthy();
  expect(
    screen.getByText('Technical code: REMINDER_TEST_UNAVAILABLE'),
  ).toBeTruthy();
});

it.each(['cloud-armed', 'coordination-unknown'] as const)(
  'suppresses duplicate TEST entry while phase %s is unresolved',
  async phase => {
    const harness = createAutomationHarness({
      getHome: jest.fn(async () =>
        ok(androidHome('test-only', invalidReceipt), revision('11')),
      ),
      getLatestTest: jest.fn(async () =>
        ok(
          {
            platform: 'android' as const,
            phase,
            updatedAt: generatedAt,
          },
          revision('11'),
        ),
      ),
    });
    await renderAutomation(harness);

    expect(await screen.findByTestId('live-check-test-status')).toBeTruthy();
    expect(screen.queryByTestId('live-test-phone')).toBeNull();
    expect(screen.queryByTestId('live-run-another-test')).toBeNull();
  },
);

it('binds TEST prepare/start to exact revisions and clears the ephemeral number after accepted start', async () => {
  const harness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(androidHome('not-configured'), revision('11')),
    ),
  });
  await renderAutomation(harness);

  const input = await screen.findByTestId('live-test-phone');
  await fireEvent.changeText(input, ' +919876543210 ');
  await fireEvent.press(screen.getByTestId('live-prepare-test'));
  expect(harness.prepareTest).toHaveBeenCalledWith({
    destination: '+919876543210',
    expectedRevision: '11',
  });
  await fireEvent.press(await screen.findByTestId('live-start-test'));
  await waitFor(() => expect(harness.startTest).toHaveBeenCalledTimes(1));
  expect(harness.startTest).toHaveBeenCalledWith({
    handle: 'test-review',
    expectedRevision: '17',
  });
  await waitFor(() =>
    expect(screen.getByTestId('live-test-phone').props.value).toBe(''),
  );
});

it('clears an unconfirmed TEST review and its ephemeral number on invalidation', async () => {
  const harness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(androidHome('not-configured'), revision('11')),
    ),
  });
  await renderAutomation(harness);
  await fireEvent.changeText(
    await screen.findByTestId('live-test-phone'),
    '+919876543210',
  );
  await fireEvent.press(screen.getByTestId('live-prepare-test'));
  expect(await screen.findByTestId('live-start-test')).toBeTruthy();

  await act(async () => {
    harness.emit({ revision: revision('18'), areas: ['automation'] });
  });
  await waitFor(() =>
    expect(screen.queryByTestId('live-start-test')).toBeNull(),
  );
  expect(screen.getByTestId('live-test-phone').props.value).toBe('');
  expect(harness.startTest).not.toHaveBeenCalled();
});

it.each([
  ['submitted', 'SMS was submitted; delivery is not confirmed'],
  [
    'sent-from-device',
    'SMS left this phone; carrier delivery is not confirmed',
  ],
  ['passed', 'Protected test passed'],
] as const)(
  'renders Android TEST phase %s with distinct truthful copy',
  async (phase, copy) => {
    const harness = createAutomationHarness({
      getLatestTest: jest.fn(async () =>
        ok(
          {
            platform: 'android' as const,
            phase: phase as AndroidTestPhase,
            updatedAt: generatedAt,
          },
          revision('11'),
        ),
      ),
    });
    await renderAutomation(harness);
    expect(await screen.findByText(copy)).toBeTruthy();
  },
);

it('uses exact Android activation, resume, and pause protected calls', async () => {
  const activationHarness = createAutomationHarness();
  await renderAutomation(activationHarness);
  await fireEvent.press(await screen.findByTestId('live-review-activation'));
  expect(activationHarness.prepareActivation).toHaveBeenCalledWith();
  await fireEvent.press(await screen.findByTestId('live-confirm-activation'));
  await waitFor(() =>
    expect(activationHarness.activate).toHaveBeenCalledWith({
      handle: 'activation-review',
      expectedRevision: '21',
    }),
  );
  await cleanup();

  const resumeHarness = createAutomationHarness({
    androidAccountKind: 'paused-repair',
    projectionRevision: revision('12'),
    getHome: jest.fn(async () =>
      ok(androidHome('paused-repair'), revision('12')),
    ),
  });
  await renderAutomation(resumeHarness);
  await fireEvent.press(await screen.findByTestId('live-review-resume'));
  expect(resumeHarness.prepareResume).toHaveBeenCalledWith();
  await fireEvent.press(await screen.findByTestId('live-confirm-activation'));
  await waitFor(() =>
    expect(resumeHarness.resume).toHaveBeenCalledWith({
      handle: 'resume-review',
      expectedRevision: '22',
    }),
  );
  await cleanup();

  const pauseHarness = createAutomationHarness({
    androidAccountKind: 'automation-active',
    projectionRevision: revision('13'),
    getHome: jest.fn(async () => ok(androidHome('active'), revision('13'))),
  });
  await renderAutomation(pauseHarness);
  await fireEvent.press(await screen.findByTestId('live-review-pause'));
  await fireEvent.press(await screen.findByTestId('live-confirm-pause'));
  await waitFor(() =>
    expect(pauseHarness.pauseAll).toHaveBeenCalledWith({
      expectedRevision: '13',
    }),
  );
});

it('keeps configured Android policy out of Automation without a Schedule action or editor', async () => {
  const harness = createAutomationHarness();
  await renderAutomation(harness);

  expect(await screen.findByTestId('live-review-activation')).toBeTruthy();
  expect(screen.queryByTestId('live-automation-open-schedule')).toBeNull();
  expect(screen.queryByTestId('live-policy-editor')).toBeNull();
  expect(screen.queryByTestId('live-policy-preview')).toBeNull();
  expect(screen.queryByTestId('live-policy-save')).toBeNull();
});

it('keeps Android blocker copy and recovery actions visible without mounting policy editing', async () => {
  const testRequiredHarness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(androidHome('test-only', invalidReceipt), revision('11')),
    ),
  });
  await renderAutomation(testRequiredHarness);
  expect(await screen.findByText('A new SMS test is required')).toBeTruthy();
  expect(screen.getByTestId('live-test-phone')).toBeTruthy();
  expect(screen.getByTestId('live-prepare-test')).toBeTruthy();
  expect(screen.getByTestId('live-automation-support-toggle')).toBeTruthy();
  expect(screen.queryByTestId('live-automation-open-schedule')).toBeNull();
  expect(screen.queryByTestId('live-policy-editor')).toBeNull();
  await cleanup();

  const actionRequired = androidHome('action-required');
  const birthdayBlockedHarness = createAutomationHarness({
    androidAccountKind: 'automation-active',
    getHome: jest.fn(async () =>
      ok(
        {
          ...actionRequired,
          automation: {
            platform: 'android' as const,
            desired: 'on' as const,
            effective: 'action-required' as const,
            readiness: {
              ...androidReadiness(),
              birthday: {
                kind: 'blocked' as const,
                issues: [
                  {
                    blocks: ['birthday'] as const,
                    code: 'contacts-stale' as const,
                    id: 'policy-visible-birthday-blocker' as IssueId,
                    severity: 'blocking' as const,
                  },
                ],
              },
            },
          },
        },
        revision('11'),
      ),
    ),
  });
  await renderAutomation(birthdayBlockedHarness);
  expect(
    await screen.findByText(
      'Contacts are out of date. Sync again before approving or sending anything.',
    ),
  ).toBeTruthy();
  expect(screen.getByTestId('live-automation-check-readiness')).toBeTruthy();
  expect(
    screen.getByText(
      'Contacts are out of date. Sync again before approving or sending anything.',
    ),
  ).toBeTruthy();
  expect(screen.getByTestId('live-review-pause')).toBeTruthy();
  expect(screen.getByTestId('live-automation-support-toggle')).toBeTruthy();
  expect(screen.queryByTestId('live-automation-open-schedule')).toBeNull();
  expect(screen.queryByTestId('live-policy-editor')).toBeNull();
});

it('suppresses Android consequential actions while verified Home is refreshing', async () => {
  const nextHome = deferred<NativeResult<HomeProjection>>();
  const prepareActivation = jest.fn(async () =>
    ok(
      {
        platform: 'android' as const,
        handle: 'must-not-open' as ActivationReviewHandle,
        enabledRecipientCount: 1,
        attentionCount: 0,
        templatePreview: 'Happy birthday!' as PrivateMessageText,
        windowLabel: '09:00–11:00',
        simLabel: 'SIM 1',
        dailyCap: 10,
        limitationsDisclosure: 'Best effort.',
      },
      revision('25'),
    ),
  );
  const getHome = jest
    .fn()
    .mockResolvedValueOnce(ok(androidHome('test-only'), revision('11')))
    .mockImplementationOnce(() => nextHome.promise);
  const harness = createAutomationHarness({ getHome, prepareActivation });
  await renderAutomation(harness);
  await screen.findByTestId('live-review-activation');

  await act(async () => {
    harness.emit({ revision: revision('24'), areas: ['home'] });
    await Promise.resolve();
  });
  await waitFor(() =>
    expect(
      screen.queryByTestId('live-review-activation')?.props.accessibilityState
        ?.disabled ?? true,
    ).toBe(true),
  );
  if (screen.queryByTestId('live-review-activation')) {
    await fireEvent.press(screen.getByTestId('live-review-activation'));
  }
  expect(prepareActivation).not.toHaveBeenCalled();

  await act(async () => {
    nextHome.resolve(ok(androidHome('test-only'), revision('24')));
    await nextHome.promise;
  });
});

it('suppresses Android activation while latest TEST truth is refreshing', async () => {
  const nextTest = deferred<
    NativeResult<{
      platform: 'android';
      phase: AndroidTestPhase;
      updatedAt: UtcInstant;
    }>
  >();
  const getLatestTest = jest
    .fn()
    .mockResolvedValueOnce(
      ok(
        {
          platform: 'android' as const,
          phase: 'passed' as const,
          updatedAt: generatedAt,
        },
        revision('11'),
      ),
    )
    .mockImplementationOnce(() => nextTest.promise);
  const prepareActivation = jest.fn();
  const harness = createAutomationHarness({
    getLatestTest,
    prepareActivation,
  });
  await renderAutomation(harness);
  await screen.findByTestId('live-review-activation');

  await act(async () => {
    harness.emit({ revision: revision('26'), areas: ['automation'] });
    await Promise.resolve();
  });
  await waitFor(() =>
    expect(screen.queryByTestId('live-review-activation')).toBeNull(),
  );
  expect(prepareActivation).not.toHaveBeenCalled();

  await act(async () => {
    nextTest.resolve(
      ok(
        {
          platform: 'android',
          phase: 'passed',
          updatedAt: generatedAt,
        },
        revision('26'),
      ),
    );
    await nextTest.promise;
  });
});

it.each([
  ['not-determined', 'live-reminder-permission'],
  ['denied', 'live-reminder-settings'],
  ['unknown', 'live-reminder-check-status'],
] as const)(
  'prioritizes iOS reminder state %s over activation',
  async (authorization, expectedTestId) => {
    Object.defineProperty(Platform, 'OS', {
      configurable: true,
      value: 'ios',
    });
    const harness = createAutomationHarness({
      getHome: jest.fn(async () => ok(iosHome('paused'))),
      getReminderStatus: jest.fn(async () => ({
        kind: 'ok' as const,
        value: reminder({ authorization, scheduledCount: 0 }),
      })),
    });
    await renderAutomation(harness, iosCapability);

    expect(await screen.findByTestId(expectedTestId)).toBeTruthy();
    expect(screen.queryByTestId('live-ios-review-activation')).toBeNull();
    expect(screen.queryByTestId('live-ios-review-resume')).toBeNull();
  },
);

it('waits for both authoritative iOS Home and reminder state before offering activation', async () => {
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: 'ios',
  });
  const reminderResult =
    deferred<Awaited<ReturnType<LiveCompanionPort['getReminderStatus']>>>();
  const harness = createAutomationHarness({
    getHome: jest.fn(async () => ok(iosHome('paused'))),
    getReminderStatus: jest.fn(() => reminderResult.promise),
  });
  await renderAutomation(harness, iosCapability);
  await screen.findByText('Paused');
  expect(screen.queryByTestId('live-ios-review-activation')).toBeNull();

  await act(async () => {
    reminderResult.resolve({
      kind: 'ok',
      value: reminder({ scheduledCount: 0 }),
    });
    await reminderResult.promise;
  });
  expect(await screen.findByTestId('live-ios-review-activation')).toBeTruthy();
});

it('does not offer iOS activation when composer readiness is blocked', async () => {
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: 'ios',
  });
  const blocked = iosHome('not-configured');
  const harness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok({
        ...blocked,
        automation: {
          platform: 'ios' as const,
          desired: 'paused' as const,
          effective: 'not-configured' as const,
          readiness: {
            ...iosReadiness,
            composer: {
              kind: 'blocked' as const,
              issues: [
                {
                  blocks: ['composer'] as const,
                  code: 'contacts-stale' as const,
                  id: 'ios-contacts-stale' as IssueId,
                  severity: 'blocking' as const,
                },
              ],
            },
          },
        },
      }),
    ),
  });
  await renderAutomation(harness, iosCapability);
  expect(
    await screen.findByText(
      'Contacts are out of date. Sync again before approving or sending anything.',
    ),
  ).toBeTruthy();
  expect(screen.queryByTestId('live-ios-review-activation')).toBeNull();
});

it.each([
  ['not-determined', 'not-configured'],
  ['denied', 'paused'],
] as const)(
  'keeps iOS firebase deletion read-only for %s reminders in %s mode',
  async (authorization, effective) => {
    Object.defineProperty(Platform, 'OS', {
      configurable: true,
      value: 'ios',
    });
    const deleting = iosHome(effective);
    const harness = createAutomationHarness({
      getHome: jest.fn(async () =>
        ok({
          ...deleting,
          automation: {
            ...deleting.automation,
            platform: 'ios' as const,
            readiness: {
              ...iosReadiness,
              composer: {
                kind: 'blocked' as const,
                issues: [
                  {
                    blocks: ['composer'] as const,
                    code: 'firebase-account-deleting' as const,
                    id: 'ios-account-deleting' as IssueId,
                    severity: 'blocking' as const,
                  },
                ],
              },
            },
          },
        }),
      ),
      getReminderStatus: jest.fn(async () => ({
        kind: 'ok' as const,
        value: reminder({ authorization, scheduledCount: 0 }),
      })),
    });
    await renderAutomation(harness, iosCapability);

    expect(
      await screen.findByText('Safety status is unavailable'),
    ).toBeTruthy();
    expect(screen.getByText('Reminder plan')).toBeTruthy();
    expect(screen.getByTestId('live-reminder-support-toggle')).toBeTruthy();
    for (const id of [
      'live-reminder-permission',
      'live-reminder-settings',
      'live-ios-review-activation',
      'live-ios-review-resume',
      'live-ios-confirm-activation',
      'live-ios-open-schedule',
      'live-policy-editor',
      'live-policy-preview',
      'live-policy-save',
    ]) {
      expect(screen.queryByTestId(id)).toBeNull();
    }
    expect(harness.requestReminderAuthorization).not.toHaveBeenCalled();
    expect(harness.prepareActivation).not.toHaveBeenCalled();
    expect(harness.prepareResume).not.toHaveBeenCalled();
  },
);

it('keeps healthy iOS horizon counts behind details while warnings remain visible', async () => {
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: 'ios',
  });
  const healthy = createAutomationHarness({
    getHome: jest.fn(async () => ok(iosHome())),
  });
  await renderAutomation(healthy, iosCapability);
  expect(
    await screen.findByTestId('live-reminder-support-toggle'),
  ).toBeTruthy();
  expect(screen.queryByText('3 reminders scheduled')).toBeNull();
  expect(screen.queryByText('3 birthday dates planned')).toBeNull();
  await fireEvent.press(screen.getByTestId('live-reminder-support-toggle'));
  expect(screen.getByTestId('live-reminder-support-details')).toBeTruthy();
  expect(screen.getByText('3 reminders scheduled')).toBeTruthy();
  expect(screen.getByText('3 birthday dates planned')).toBeTruthy();
  await cleanup();

  const warnings = createAutomationHarness({
    getHome: jest.fn(async () => ok(iosHome())),
    getReminderStatus: jest.fn(async () => ({
      kind: 'ok' as const,
      value: reminder({
        earliestUnscheduledCivilDate: '2026-07-20' as LocalDate,
        failedCount: 2,
        truncated: true,
      }),
    })),
  });
  await renderAutomation(warnings, iosCapability);
  expect(
    await screen.findByText('2 reminders could not be scheduled'),
  ).toBeTruthy();
  expect(screen.getByText('20 July 2026')).toBeTruthy();
  expect(
    screen.getByText('Some dates could not fit in iPhone’s reminder limit.'),
  ).toBeTruthy();
});

it('keeps configured iOS policy out of Automation without a Schedule action or editor', async () => {
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: 'ios',
  });
  const harness = createAutomationHarness({
    getHome: jest.fn(async () => ok(iosHome('paused'))),
    getReminderStatus: jest.fn(async () => ({
      kind: 'ok' as const,
      value: reminder({ scheduledCount: 0 }),
    })),
  });
  await renderAutomation(harness, iosCapability);

  expect(await screen.findByTestId('live-ios-review-activation')).toBeTruthy();
  expect(screen.queryByTestId('live-ios-open-schedule')).toBeNull();
  expect(screen.queryByTestId('live-policy-editor')).toBeNull();
  expect(screen.queryByTestId('live-policy-preview')).toBeNull();
  expect(screen.queryByTestId('live-policy-save')).toBeNull();
});

it('keeps iOS composer and reminder blocker copy visible while reminder recovery suppresses Schedule repair', async () => {
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: 'ios',
  });
  const blocked = iosHomeWithMode('composer-reminders-on', 'action-required');
  const harness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok({
        ...blocked,
        automation: {
          platform: 'ios' as const,
          desired: 'composer-reminders-on' as const,
          effective: 'action-required' as const,
          readiness: {
            ...iosReadiness,
            composer: {
              kind: 'blocked' as const,
              issues: [
                {
                  blocks: ['composer'] as const,
                  code: 'contacts-stale' as const,
                  id: 'policy-visible-ios-blocker' as IssueId,
                  severity: 'blocking' as const,
                },
              ],
            },
          },
        },
      }),
    ),
    getReminderStatus: jest.fn(async () => ({
      kind: 'ok' as const,
      value: reminder({
        authorization: 'unknown',
        earliestUnscheduledCivilDate: '2026-07-20' as LocalDate,
        failedCount: 2,
        truncated: true,
      }),
    })),
  });
  await renderAutomation(harness, iosCapability);
  expect(
    await screen.findByText(
      'Contacts are out of date. Sync again before approving or sending anything.',
    ),
  ).toBeTruthy();
  expect(screen.getByText('Reminder status is unavailable')).toBeTruthy();
  expect(screen.getByText('2 reminders could not be scheduled')).toBeTruthy();
  expect(screen.getByTestId('live-reminder-check-status')).toBeTruthy();
  for (const id of [
    'live-ios-open-schedule',
    'live-policy-editor',
    'live-reminder-permission',
    'live-reminder-settings',
    'live-ios-review-activation',
    'live-ios-review-resume',
  ]) {
    expect(screen.queryByTestId(id)).toBeNull();
  }
  expect(screen.getByTestId('live-reminder-support-toggle')).toBeTruthy();
  expect(harness.requestReminderAuthorization).not.toHaveBeenCalled();
  expect(harness.prepareActivation).not.toHaveBeenCalled();
  expect(harness.prepareResume).not.toHaveBeenCalled();
});

it.each([
  [
    'activate',
    iosHome('paused'),
    iosHome(),
    'live-ios-review-activation',
    false,
  ],
  ['resume', iosHome('paused'), iosHome(), 'live-ios-review-resume', true],
] as const)(
  'closes an iOS %s review when authoritative Home moves out of its exact mode',
  async (
    kind,
    initialHome,
    supersedingHome,
    reviewActionId,
    initialActivationCompleted,
  ) => {
    Object.defineProperty(Platform, 'OS', {
      configurable: true,
      value: 'ios',
    });
    const appStateListeners = captureAppStateListeners();
    const getHome = jest
      .fn()
      .mockResolvedValueOnce(ok(initialHome, revision('50')))
      .mockResolvedValueOnce(ok(supersedingHome, revision('51')));
    const harness = createAutomationHarness({
      initialActivationCompleted,
      setupRevision: revision('50'),
      getHome,
      getReminderStatus: jest.fn(async () => ({
        kind: 'ok' as const,
        value: reminder({ scheduledCount: 0 }),
      })),
      prepareActivation: jest.fn(async () =>
        ok(iosActivationReview, revision('52')),
      ),
      prepareResume: jest.fn(async () =>
        ok(iosActivationReview, revision('52')),
      ),
    });
    await renderAutomation(harness, iosCapability);

    await fireEvent.press(await screen.findByTestId(reviewActionId));
    expect(
      await screen.findByTestId('live-ios-confirm-activation'),
    ).toBeTruthy();

    await act(async () => {
      appStateListeners.forEach(listener => listener('active'));
    });
    await waitFor(() => expect(getHome).toHaveBeenCalledTimes(2));
    await waitFor(() =>
      expect(screen.queryByTestId('live-ios-activation-review')).toBeNull(),
    );
    expect(screen.queryByTestId('live-ios-confirm-activation')).toBeNull();
    expect(harness.activate).not.toHaveBeenCalled();
    expect(harness.resume).not.toHaveBeenCalled();
    expect(
      kind === 'activate' ? harness.prepareActivation : harness.prepareResume,
    ).toHaveBeenCalledTimes(1);
  },
);

it.each([
  ['composer-reminders-on', 'ready', true],
  ['composer-reminders-on', 'action-required', true],
  ['paused', 'ready', false],
  ['composer-reminders-on', 'paused', false],
] as const)(
  'binds the iOS pause review to desired %s and effective %s',
  async (desired, effective, expected) => {
    Object.defineProperty(Platform, 'OS', {
      configurable: true,
      value: 'ios',
    });
    const harness = createAutomationHarness({
      setupRevision: revision('53'),
      getHome: jest.fn(async () =>
        ok(iosHomeWithMode(desired, effective), revision('53')),
      ),
    });
    await renderAutomation(harness, iosCapability);
    await screen.findByText('Reminder plan');

    if (expected) {
      await fireEvent.press(await screen.findByTestId('live-ios-review-pause'));
      expect(await screen.findByTestId('live-ios-confirm-pause')).toBeTruthy();
    } else {
      expect(screen.queryByTestId('live-ios-review-pause')).toBeNull();
      expect(screen.queryByTestId('live-ios-confirm-pause')).toBeNull();
    }
  },
);

it('closes an open iOS pause review when Home no longer reports the pausable mode', async () => {
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: 'ios',
  });
  const appStateListeners = captureAppStateListeners();
  const getHome = jest
    .fn()
    .mockResolvedValueOnce(
      ok(iosHomeWithMode('composer-reminders-on', 'ready'), revision('54')),
    )
    .mockResolvedValueOnce(
      ok(iosHomeWithMode('paused', 'paused'), revision('55')),
    );
  const harness = createAutomationHarness({
    getHome,
    setupRevision: revision('54'),
  });
  await renderAutomation(harness, iosCapability);

  await fireEvent.press(await screen.findByTestId('live-ios-review-pause'));
  expect(await screen.findByTestId('live-ios-confirm-pause')).toBeTruthy();

  await act(async () => {
    appStateListeners.forEach(listener => listener('active'));
  });
  await waitFor(() => expect(getHome).toHaveBeenCalledTimes(2));
  await waitFor(() =>
    expect(screen.queryByTestId('live-ios-confirm-pause')).toBeNull(),
  );
  expect(harness.pauseAll).not.toHaveBeenCalled();
});

it('keeps iOS pause uncertainty fail-closed until explicit paused and zero-reminder verification', async () => {
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: 'ios',
  });
  const paused = iosHome('paused');
  const getHome = jest
    .fn()
    .mockResolvedValueOnce(ok(iosHome(), revision('40')))
    .mockResolvedValueOnce(ok(paused, revision('41')))
    .mockResolvedValueOnce(ok(iosHome(), revision('42')))
    .mockResolvedValueOnce(ok(paused, revision('43')));
  const healthyZero = reminder({
    failedCount: 0,
    scheduledCount: 0,
    truncated: false,
  });
  const getReminderStatus = jest
    .fn()
    .mockResolvedValueOnce({ kind: 'ok' as const, value: reminder() })
    .mockResolvedValueOnce({
      kind: 'ok' as const,
      value: reminder({ scheduledCount: 1 }),
    })
    .mockResolvedValueOnce({ kind: 'ok' as const, value: healthyZero })
    .mockResolvedValueOnce({ kind: 'ok' as const, value: healthyZero });
  const pauseAll = jest.fn(async () =>
    nativeInternalError('IOS_PAUSE_TEST_FAILURE'),
  );
  const supportingRevisions = ['40', '41', '42', '43'].map(revision);
  const harness = createAutomationHarness({
    initialActivationCompleted: true,
    setupRevision: revision('40'),
    getSetup: projectionSequence(iosSetup(true), supportingRevisions),
    getMessageEditor: projectionSequence(
      configuredMessage,
      supportingRevisions,
    ),
    getPolicyEditor: projectionSequence(configuredPolicy, supportingRevisions),
    getHome,
    getReminderStatus,
    pauseAll,
  });
  await renderAutomation(harness, iosCapability);
  await fireEvent.press(await screen.findByTestId('live-ios-review-pause'));
  await fireEvent.press(await screen.findByTestId('live-ios-confirm-pause'));

  expect(
    await screen.findByTestId('live-ios-pause-verification-required'),
  ).toBeTruthy();
  expect(screen.getByTestId('live-ios-check-pause-status')).toBeTruthy();
  for (const id of [
    'live-ios-review-activation',
    'live-ios-review-resume',
    'live-ios-review-pause',
    'live-ios-open-schedule',
  ]) {
    expect(screen.queryByTestId(id)).toBeNull();
  }

  await fireEvent.press(screen.getByTestId('live-ios-check-pause-status'));
  await waitFor(() => expect(getHome).toHaveBeenCalledTimes(3));
  expect(
    screen.getByTestId('live-ios-pause-verification-required'),
  ).toBeTruthy();
  expect(screen.queryByTestId('live-ios-review-resume')).toBeNull();

  await fireEvent.press(screen.getByTestId('live-ios-check-pause-status'));
  await waitFor(() =>
    expect(
      screen.queryByTestId('live-ios-pause-verification-required'),
    ).toBeNull(),
  );
  expect(getHome).toHaveBeenCalledTimes(4);
  expect(getReminderStatus).toHaveBeenCalledTimes(4);
  expect(await screen.findByTestId('live-ios-review-resume')).toBeTruthy();
});

it('does not clear iOS pause verification when a later Home invalidation supersedes its reload', async () => {
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: 'ios',
  });
  const verifiedHome = deferred<NativeResult<HomeProjection>>();
  const verifiedReminder =
    deferred<Awaited<ReturnType<LiveCompanionPort['getReminderStatus']>>>();
  const paused = iosHomeWithMode('paused', 'paused');
  const getHome = jest
    .fn()
    .mockResolvedValueOnce(ok(iosHome(), revision('60')))
    .mockResolvedValueOnce(ok(paused, revision('61')))
    .mockImplementationOnce(() => verifiedHome.promise)
    .mockResolvedValueOnce(ok(paused, revision('63')));
  const healthyZero = reminder({
    failedCount: 0,
    scheduledCount: 0,
    truncated: false,
  });
  const getReminderStatus = jest
    .fn()
    .mockResolvedValueOnce({ kind: 'ok' as const, value: reminder() })
    .mockResolvedValueOnce({
      kind: 'ok' as const,
      value: reminder({ scheduledCount: 1 }),
    })
    .mockImplementationOnce(() => verifiedReminder.promise);
  const supportingRevisions = ['60', '61', '62'].map(revision);
  const harness = createAutomationHarness({
    initialActivationCompleted: true,
    setupRevision: revision('60'),
    getSetup: projectionSequence(iosSetup(true), supportingRevisions),
    getMessageEditor: projectionSequence(
      configuredMessage,
      supportingRevisions,
    ),
    getPolicyEditor: projectionSequence(configuredPolicy, supportingRevisions),
    getHome,
    getReminderStatus,
    pauseAll: jest.fn(async () =>
      nativeInternalError('IOS_PAUSE_RACE_FAILURE'),
    ),
  });
  await renderAutomation(harness, iosCapability);
  await fireEvent.press(await screen.findByTestId('live-ios-review-pause'));
  await fireEvent.press(await screen.findByTestId('live-ios-confirm-pause'));
  expect(
    await screen.findByTestId('live-ios-pause-verification-required'),
  ).toBeTruthy();

  fireEvent.press(screen.getByTestId('live-ios-check-pause-status'));
  await waitFor(() => expect(getHome).toHaveBeenCalledTimes(3));
  await act(async () => {
    harness.emit({ revision: revision('63'), areas: ['home'] });
  });
  await waitFor(() => expect(getHome).toHaveBeenCalledTimes(4));
  await act(async () => {
    verifiedReminder.resolve({ kind: 'ok', value: healthyZero });
    verifiedHome.resolve(ok(paused, revision('62')));
    await Promise.all([verifiedHome.promise, verifiedReminder.promise]);
  });

  expect(
    screen.getByTestId('live-ios-pause-verification-required'),
  ).toBeTruthy();
  expect(screen.queryByTestId('live-ios-review-resume')).toBeNull();
  expect(getReminderStatus).toHaveBeenCalledTimes(3);
});

it('uses exact iOS activation and pause calls without claiming message automation', async () => {
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: 'ios',
  });
  const activate = jest.fn(async () => ok(iosHome().automation));
  const activationHarness = createAutomationHarness({
    activate,
    setupRevision: revision('31'),
    getHome: jest.fn(async () => ok(iosHome('paused'), revision('31'))),
    getReminderStatus: jest.fn(async () => ({
      kind: 'ok' as const,
      value: reminder({ scheduledCount: 0 }),
    })),
    prepareActivation: jest.fn(async () =>
      ok(iosActivationReview, revision('32')),
    ),
  });
  await renderAutomation(activationHarness, iosCapability);
  const activationAction = await screen.findByTestId(
    'live-ios-review-activation',
  );
  expect(activationAction.props.accessibilityLabel).not.toMatch(
    /automatically send|message automation/u,
  );
  await fireEvent.press(activationAction);
  await fireEvent.press(
    await screen.findByTestId('live-ios-confirm-activation'),
  );
  await waitFor(() =>
    expect(activate).toHaveBeenCalledWith({
      handle: 'ios-activation-review',
      expectedRevision: '32',
    }),
  );
  await cleanup();

  const pauseAll = jest.fn(async () => ok(iosHome('paused').automation));
  const pauseHarness = createAutomationHarness({
    setupRevision: revision('33'),
    getHome: jest.fn(async () => ok(iosHome(), revision('33'))),
    pauseAll,
  });
  await renderAutomation(pauseHarness, iosCapability);
  await fireEvent.press(await screen.findByTestId('live-ios-review-pause'));
  await fireEvent.press(await screen.findByTestId('live-ios-confirm-pause'));
  await waitFor(() =>
    expect(pauseAll).toHaveBeenCalledWith({ expectedRevision: '33' }),
  );
});

it('clears an Android protected review when automation invalidates', async () => {
  const harness = createAutomationHarness();
  await renderAutomation(harness);
  await fireEvent.press(await screen.findByTestId('live-review-activation'));
  expect(await screen.findByTestId('live-confirm-activation')).toBeTruthy();

  await act(async () => {
    harness.emit({ revision: revision('23'), areas: ['automation'] });
  });
  await waitFor(() =>
    expect(screen.queryByTestId('live-confirm-activation')).toBeNull(),
  );
  expect(harness.activate).not.toHaveBeenCalled();
});

it('routes missing Android message and policy configuration before TEST', async () => {
  const openMessage = jest.fn();
  const openSchedule = jest.fn();
  const missingMessage = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(androidHome('not-configured'), revision('11')),
    ),
    getMessageEditor: jest.fn(async () =>
      ok({ kind: 'not-configured' as const }, revision('11')),
    ),
  });
  await renderAutomation(missingMessage, androidCapability, openMessage);

  await fireEvent.press(
    await screen.findByTestId('live-automation-open-message'),
  );
  expect(openMessage).toHaveBeenCalledTimes(1);
  expect(screen.queryByTestId('live-test-phone')).toBeNull();
  expect(screen.queryByTestId('live-automation-open-schedule')).toBeNull();
  expect(screen.queryByTestId('live-policy-editor')).toBeNull();
  await cleanup();

  const missingPolicy = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(androidHome('not-configured'), revision('11')),
    ),
    getPolicyEditor: jest.fn(async () =>
      ok({ kind: 'not-configured' as const }, revision('11')),
    ),
  });
  await renderAutomation(
    missingPolicy,
    androidCapability,
    jest.fn(),
    openSchedule,
  );

  const chooseWindow = await screen.findByTestId(
    'live-automation-open-schedule',
  );
  expect(chooseWindow.props.accessibilityLabel).toBe('Choose the time window');
  await fireEvent.press(chooseWindow);
  expect(openSchedule).toHaveBeenCalledTimes(1);
  expect(screen.queryByTestId('live-test-phone')).toBeNull();
  expect(screen.queryByTestId('live-policy-editor')).toBeNull();
  expect(screen.queryByTestId('live-policy-preview')).toBeNull();
  expect(screen.queryByTestId('live-policy-save')).toBeNull();
});

it('fails closed with one Android configuration retry for stale or unavailable editor truth', async () => {
  const harness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(androidHome('not-configured'), revision('11')),
    ),
    getMessageEditor: jest.fn(async () =>
      nativeInternalError('MESSAGE_CONFIGURATION_UNAVAILABLE'),
    ),
    getPolicyEditor: jest.fn(async () => ok(configuredPolicy, revision('10'))),
  });
  await renderAutomation(harness);

  expect(
    await screen.findByTestId('live-automation-check-configuration-status'),
  ).toBeTruthy();
  expect(
    screen.queryByTestId('live-automation-check-ownership-status'),
  ).toBeNull();
  expect(screen.queryByTestId('live-check-test-status')).toBeNull();
  expect(screen.queryByTestId('live-test-phone')).toBeNull();
  expect(screen.queryByTestId('live-automation-open-schedule')).toBeNull();
});

it('does not expose Android setup editing when not-configured masks a non-owner lifecycle', async () => {
  const standbyAccount: AccountProjection = {
    kind: 'connected',
    displayEmail: 'user@example.test' as PrivateEmail,
    sender: {
      platform: 'android',
      kind: 'standby',
      activeOtherDeviceLabel: 'Other phone',
    },
  };
  const harness = createAutomationHarness({
    getAccount: jest.fn(async () => ok(standbyAccount, revision('11'))),
    getHome: jest.fn(async () =>
      ok(androidHome('not-configured'), revision('11')),
    ),
  });
  await renderAutomation(harness);

  expect(
    await screen.findByTestId('live-automation-check-ownership-status'),
  ).toBeTruthy();
  expect(screen.queryByTestId('live-automation-open-message')).toBeNull();
  expect(screen.queryByTestId('live-automation-open-schedule')).toBeNull();
  expect(screen.queryByTestId('live-test-phone')).toBeNull();
});

it('routes iOS missing configuration and never treats not-configured as activation-ready', async () => {
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: 'ios',
  });
  const openMessage = jest.fn();
  const openSchedule = jest.fn();
  const missingMessage = createAutomationHarness({
    getHome: jest.fn(async () => ok(iosHome('not-configured'))),
    getMessageEditor: jest.fn(async () =>
      ok({ kind: 'not-configured' as const }),
    ),
    getReminderStatus: jest.fn(async () => ({
      kind: 'ok' as const,
      value: reminder({ scheduledCount: 0 }),
    })),
  });
  await renderAutomation(missingMessage, iosCapability, openMessage);

  await fireEvent.press(await screen.findByTestId('live-ios-open-message'));
  expect(openMessage).toHaveBeenCalledTimes(1);
  expect(screen.queryByTestId('live-ios-review-activation')).toBeNull();
  expect(screen.queryByTestId('live-ios-open-schedule')).toBeNull();
  expect(screen.queryByTestId('live-policy-editor')).toBeNull();
  expect(missingMessage.prepareActivation).not.toHaveBeenCalled();
  await cleanup();

  const missingPolicy = createAutomationHarness({
    getHome: jest.fn(async () => ok(iosHome('not-configured'))),
    getPolicyEditor: jest.fn(async () =>
      ok({ kind: 'not-configured' as const }),
    ),
    getReminderStatus: jest.fn(async () => ({
      kind: 'ok' as const,
      value: reminder({ scheduledCount: 0 }),
    })),
  });
  await renderAutomation(missingPolicy, iosCapability, jest.fn(), openSchedule);

  const chooseWindow = await screen.findByTestId('live-ios-open-schedule');
  expect(chooseWindow.props.accessibilityLabel).toBe('Choose the time window');
  await fireEvent.press(chooseWindow);
  expect(openSchedule).toHaveBeenCalledTimes(1);
  expect(screen.queryByTestId('live-ios-review-activation')).toBeNull();
  expect(screen.queryByTestId('live-policy-editor')).toBeNull();
  expect(screen.queryByTestId('live-policy-preview')).toBeNull();
  expect(screen.queryByTestId('live-policy-save')).toBeNull();
});

it('uses durable iOS activation history to choose Activate versus Resume', async () => {
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: 'ios',
  });
  const prepareActivation = jest.fn(async () =>
    ok(iosActivationReview, revision('2')),
  );
  const firstActivation = createAutomationHarness({
    getHome: jest.fn(async () => ok(iosHome('paused'))),
    getReminderStatus: jest.fn(async () => ({
      kind: 'ok' as const,
      value: reminder({ scheduledCount: 0 }),
    })),
    prepareActivation,
  });
  await renderAutomation(firstActivation, iosCapability);
  await fireEvent.press(
    await screen.findByTestId('live-ios-review-activation'),
  );
  expect(prepareActivation).toHaveBeenCalledTimes(1);
  expect(firstActivation.prepareResume).not.toHaveBeenCalled();
  await cleanup();

  const prepareResume = jest.fn(async () =>
    ok(iosActivationReview, revision('3')),
  );
  const resumed = createAutomationHarness({
    initialActivationCompleted: true,
    getHome: jest.fn(async () => ok(iosHome('paused'))),
    getReminderStatus: jest.fn(async () => ({
      kind: 'ok' as const,
      value: reminder({ scheduledCount: 0 }),
    })),
    prepareResume,
  });
  await renderAutomation(resumed, iosCapability);
  await fireEvent.press(await screen.findByTestId('live-ios-review-resume'));
  expect(prepareResume).toHaveBeenCalledTimes(1);
  expect(resumed.prepareActivation).not.toHaveBeenCalled();
});

it('offers one iOS setup retry and suppresses competing actions when history truth is stale', async () => {
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: 'ios',
  });
  const harness = createAutomationHarness({
    getHome: jest.fn(async () => ok(iosHome('paused'), revision('7'))),
    getSetup: jest.fn(async () => ok(iosSetup(false), revision('6'))),
    getMessageEditor: jest.fn(async () => ok(configuredMessage, revision('7'))),
    getPolicyEditor: jest.fn(async () => ok(configuredPolicy, revision('7'))),
    getReminderStatus: jest.fn(async () => ({
      kind: 'ok' as const,
      value: reminder({ authorization: 'not-determined', scheduledCount: 0 }),
    })),
  });
  await renderAutomation(harness, iosCapability);

  expect(await screen.findByTestId('live-ios-check-setup-status')).toBeTruthy();
  expect(screen.queryByTestId('live-reminder-permission')).toBeNull();
  expect(screen.queryByTestId('live-ios-review-activation')).toBeNull();
  expect(screen.queryByTestId('live-ios-open-schedule')).toBeNull();
});
