import React from 'react';
import { AccessibilityInfo, AppState, type AppStateStatus } from 'react-native';
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
import type { LocalTime, UtcInstant } from '../../domain/shared/temporal';
import { LocalizationProvider } from '../../localization/LocalizationProvider';
import { appI18n } from '../../localization/i18n';
import type { LiveAppPort } from './LiveAppPort';
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

jest.setTimeout(20000);

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

const allowedGate: GateDecision = { kind: 'allowed' };

const androidReadiness: ReadinessProjection = {
  platform: 'android',
  test: allowedGate,
  activation: allowedGate,
  birthday: allowedGate,
  lastCheckedAt: generatedAt,
};

const androidHome = (
  effective: HomeProjection['automation']['effective'] = 'test-only',
): HomeProjection => ({
  counts: {
    enabled: 1,
    needsAttention: 0,
    unavailable: 0,
    today: 0,
    nextSevenDays: 0,
  },
  automation: {
    platform: 'android',
    desired: effective === 'active' ? 'on' : 'paused',
    effective,
    readiness: androidReadiness,
  },
  contactsSync: {
    kind: 'fresh',
    completedAt: generatedAt,
    contactCount: 1,
  },
});

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

const deferred = <Value,>() => {
  let resolve!: (value: Value) => void;
  const promise = new Promise<Value>(resolver => {
    resolve = resolver;
  });
  return { promise, resolve };
};

type AutomationHarness = Readonly<{
  activate: jest.Mock;
  emit(event: ProjectionInvalidation): void;
  getHome: jest.Mock;
  getAccount: jest.Mock;
  getLatestTest: jest.Mock;
  getMessageEditor: jest.Mock;
  getPolicyEditor: jest.Mock;
  getSetup: jest.Mock;
  pauseAll: jest.Mock;
  port: LiveAppPort;
  prepareActivation: jest.Mock;
  prepareResume: jest.Mock;
  prepareTest: jest.Mock;
  resume: jest.Mock;
  startTest: jest.Mock;
}>;

const createAutomationHarness = ({
  projectionRevision = revision('11'),
  androidAccountKind = 'test-only',
  setupRevision = revision('1'),
  activate = jest.fn(async () =>
    ok(androidHome('active').automation, projectionRevision),
  ),
  getAccount = jest.fn(async () =>
    ok(androidAccount(androidAccountKind), projectionRevision),
  ),
  getHome = jest.fn(async () =>
    ok(
      androidHome(
        androidAccountKind === 'automation-active'
          ? 'active'
          : androidAccountKind,
      ),
      projectionRevision,
    ),
  ),
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
    ok(configuredMessage, projectionRevision),
  ),
  getPolicyEditor = jest.fn(async () =>
    ok(configuredPolicy, projectionRevision),
  ),
  getSetup = jest.fn(async () =>
    ok(
      {
        step: 'complete' as const,
        initialActivationCompleted: false,
        eligibility: {
          kind: 'supported' as const,
          capability: androidCapability,
          channelLabel: 'test',
          chargeDisclosureVersion: 'sms-cost-v1',
        },
        account: androidAccount('test-only'),
        contacts: {
          kind: 'fresh' as const,
          completedAt: generatedAt,
          contactCount: 1,
        },
        readiness: androidReadiness,
        automation: androidHome('paused-repair').automation,
      },
      setupRevision,
    ),
  ),
  pauseAll = jest.fn(async () =>
    ok(androidHome('paused-repair').automation, projectionRevision),
  ),
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
  resume = jest.fn(async () =>
    ok(androidHome('active').automation, projectionRevision),
  ),
  startTest = jest.fn(async () =>
    ok(
      {
        platform: 'android' as const,
        phase: 'prepared' as const,
        updatedAt: generatedAt,
      },
      projectionRevision,
    ),
  ),
}: Partial<{
  projectionRevision: NativeRevision;
  androidAccountKind: 'test-only' | 'paused-repair' | 'automation-active';
  setupRevision: NativeRevision;
  activate: jest.Mock;
  getAccount: jest.Mock;
  getHome: jest.Mock;
  getLatestTest: jest.Mock;
  getMessageEditor: jest.Mock;
  getPolicyEditor: jest.Mock;
  getSetup: jest.Mock;
  pauseAll: jest.Mock;
  prepareActivation: jest.Mock;
  prepareResume: jest.Mock;
  prepareTest: jest.Mock;
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
  return {
    activate,
    emit: event => listeners.forEach(listener => listener(event)),
    getAccount,
    getHome,
    getLatestTest,
    getMessageEditor,
    getPolicyEditor,
    getSetup,
    pauseAll,
    port,
    prepareActivation,
    prepareResume,
    prepareTest,
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
  await render(
    <LocalizationProvider>
      <ThemeProvider initialPreference="light">
        <LiveAutomationScreen
          capability={capability}
          onBack={jest.fn()}
          onOpenMessage={onOpenMessage}
          onOpenSchedule={onOpenSchedule}
          port={harness.port}
        />
      </ThemeProvider>
    </LocalizationProvider>,
  );

beforeEach(async () => {
  await appI18n.changeLanguage('en');
  jest.clearAllMocks();
  jest
    .spyOn(AccessibilityInfo, 'announceForAccessibility')
    .mockImplementation();
});

afterEach(() => {
  cleanup();
});

it('treats only exact NATIVE_NOT_CONFIGURED as settled latest-TEST absence', async () => {
  const harness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(androidHome('not-configured'), revision('11')),
    ),
    getAccount: jest.fn(async () =>
      ok(androidAccount('test-only'), revision('11')),
    ),
    getLatestTest: jest.fn(async () =>
      nativeInternalError('NATIVE_NOT_CONFIGURED'),
    ),
  });

  await renderAutomation(harness);

  expect(await screen.findByTestId('live-automation-screen')).toBeTruthy();
  expect(screen.getByTestId('live-test-phone')).toBeTruthy();
  expect(screen.getByTestId('live-prepare-test')).toBeTruthy();
  expect(screen.queryByTestId('live-check-test-status')).toBeNull();
});

it('suppresses the Android TEST form during refresh and after a non-absence refresh failure', async () => {
  const appStateListeners = captureAppStateListeners();
  const failingTestRefresh = deferred<
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
    .mockImplementationOnce(() => failingTestRefresh.promise);
  const harness = createAutomationHarness({ getLatestTest });

  await renderAutomation(harness);

  expect(await screen.findByTestId('live-run-another-test')).toBeTruthy();
  await fireEvent.press(screen.getByTestId('live-run-another-test'));
  expect(screen.getByTestId('live-test-phone')).toBeTruthy();

  await act(async () => {
    appStateListeners.forEach(listener => listener('active'));
  });

  expect(screen.queryByTestId('live-test-phone')).toBeNull();

  await act(async () => {
    failingTestRefresh.resolve(nativeInternalError('NETWORK_OFFLINE'));
  });

  await waitFor(() => {
    expect(screen.getByTestId('live-check-test-status')).toBeTruthy();
  });
  expect(screen.queryByTestId('live-test-phone')).toBeNull();
});

it('keeps an open TEST review non-actionable when latest-TEST truth becomes unstable', async () => {
  const harness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(androidHome('not-configured'), revision('11')),
    ),
    getAccount: jest.fn(async () =>
      ok(androidAccount('test-only'), revision('11')),
    ),
    getLatestTest: jest.fn(async () =>
      nativeInternalError('NATIVE_NOT_CONFIGURED'),
    ),
  });

  await renderAutomation(harness);

  const destinationInput = await screen.findByTestId('live-test-phone');
  await fireEvent.changeText(destinationInput, '+919876543210');
  await fireEvent.press(screen.getByTestId('live-prepare-test'));

  expect(await screen.findByTestId('live-start-test')).toBeTruthy();
  const startButton = screen.getByTestId('live-start-test');
  expect(startButton.props.accessibilityState?.disabled).toBe(false);

  harness.getLatestTest.mockImplementationOnce(async () =>
    nativeInternalError('DATABASE_BUSY'),
  );
  await act(async () => {
    harness.emit({ revision: revision('12'), areas: ['automation'] });
  });

  await waitFor(() => {
    expect(screen.getByTestId('live-check-test-status')).toBeTruthy();
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
          updatedAt: generatedAt,
          reason: 'network-offline',
        },
        revision('11'),
      ),
    ),
  });

  await renderAutomation(harness);

  expect(await screen.findByTestId('live-automation-screen')).toBeTruthy();
  expect(screen.getByText('Protected test failed')).toBeTruthy();
  expect(screen.queryByText(/network-offline/)).toBeNull();

  await fireEvent.press(screen.getByTestId('live-automation-support-toggle'));

  expect(screen.getByText(/network-offline/)).toBeTruthy();
  expect(screen.getByTestId('live-automation-support-details')).toBeTruthy();
});

it('does not treat an unrelated activation blocker as a reason to rerun TEST', async () => {
  const blockedReadiness: ReadinessProjection = {
    platform: 'android',
    test: allowedGate,
    activation: {
      kind: 'blocked',
      issues: [
        {
          id: 'issue-permission' as IssueId,
          code: 'sms-permission-denied',
          severity: 'blocking',
          blocks: ['activation'],
        },
      ],
    },
    birthday: allowedGate,
    lastCheckedAt: generatedAt,
  };
  const harness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(
        {
          ...androidHome('test-only'),
          automation: {
            platform: 'android',
            desired: 'paused',
            effective: 'test-only',
            readiness: blockedReadiness,
          },
        },
        revision('11'),
      ),
    ),
  });

  await renderAutomation(harness);

  expect(
    await screen.findByTestId('live-automation-readiness-issues'),
  ).toBeTruthy();
  expect(screen.getByText(/permission was not allowed/i)).toBeTruthy();
  expect(screen.queryByTestId('live-review-activation')).toBeNull();
});

it('scopes active Android readiness to the native BIRTHDAY gate instead of TEST and ACTIVATION sentinels', async () => {
  const activeHome: HomeProjection = {
    ...androidHome('active'),
    automation: {
      platform: 'android',
      desired: 'on',
      effective: 'active',
      readiness: {
        platform: 'android',
        test: {
          kind: 'blocked',
          issues: [
            {
              id: 'issue-test' as IssueId,
              code: 'test-receipt-invalid',
              severity: 'warning',
              blocks: ['test'],
            },
          ],
        },
        activation: {
          kind: 'blocked',
          issues: [
            {
              id: 'issue-activation' as IssueId,
              code: 'permission-denied',
              severity: 'warning',
              blocks: ['activation'],
            },
          ],
        },
        birthday: allowedGate,
        lastCheckedAt: generatedAt,
      },
    },
  };

  const harness = createAutomationHarness({
    projectionRevision: revision('30'),
    androidAccountKind: 'automation-active',
    getHome: jest.fn(async () => ok(activeHome, revision('30'))),
  });

  await renderAutomation(harness);

  expect(await screen.findByTestId('live-automation-screen')).toBeTruthy();
  expect(screen.getByText('On')).toBeTruthy();
  expect(screen.queryByTestId('live-automation-readiness-issues')).toBeNull();
  expect(screen.getByTestId('live-review-pause')).toBeTruthy();
});

it('binds TEST prepare/start to exact revisions and clears the ephemeral number after accepted start', async () => {
  const harness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(androidHome('not-configured'), revision('11')),
    ),
    getAccount: jest.fn(async () =>
      ok(androidAccount('test-only'), revision('11')),
    ),
    getLatestTest: jest.fn(async () =>
      nativeInternalError('NATIVE_NOT_CONFIGURED'),
    ),
  });

  await renderAutomation(harness);

  const destinationInput = await screen.findByTestId('live-test-phone');
  await fireEvent.changeText(destinationInput, '+919876543210');
  await fireEvent.press(screen.getByTestId('live-prepare-test'));

  expect(await screen.findByTestId('live-start-test')).toBeTruthy();
  expect(harness.prepareTest).toHaveBeenCalledWith({
    destination: '+919876543210',
    expectedRevision: revision('11'),
  });

  await fireEvent.press(screen.getByTestId('live-start-test'));

  await waitFor(() => {
    expect(harness.startTest).toHaveBeenCalledWith({
      handle: 'test-review',
      expectedRevision: revision('17'),
    });
  });

  expect(screen.queryByTestId('live-start-test')).toBeNull();
  expect(screen.getByTestId('live-test-phone').props.value).toBe('');
});

it('clears an unconfirmed TEST review and its ephemeral number on invalidation', async () => {
  const harness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(androidHome('not-configured'), revision('11')),
    ),
    getAccount: jest.fn(async () =>
      ok(androidAccount('test-only'), revision('11')),
    ),
    getLatestTest: jest.fn(async () =>
      nativeInternalError('NATIVE_NOT_CONFIGURED'),
    ),
  });

  await renderAutomation(harness);

  const destinationInput = await screen.findByTestId('live-test-phone');
  await fireEvent.changeText(destinationInput, '+919876543210');
  await fireEvent.press(screen.getByTestId('live-prepare-test'));

  expect(await screen.findByTestId('live-start-test')).toBeTruthy();

  harness.getLatestTest.mockResolvedValueOnce(
    ok(
      {
        platform: 'android' as const,
        phase: 'passed' as const,
        updatedAt: generatedAt,
      },
      revision('18'),
    ),
  );
  harness.getHome.mockResolvedValueOnce(
    ok(androidHome('not-configured'), revision('18')),
  );
  harness.getAccount.mockResolvedValueOnce(
    ok(androidAccount('test-only'), revision('18')),
  );
  harness.getMessageEditor.mockResolvedValueOnce(
    ok(configuredMessage, revision('18')),
  );
  harness.getPolicyEditor.mockResolvedValueOnce(
    ok(configuredPolicy, revision('18')),
  );
  await act(async () => {
    harness.emit({
      revision: revision('18'),
      areas: ['automation', 'home', 'account', 'messages', 'setup'],
    });
  });

  await waitFor(() => {
    expect(screen.queryByTestId('live-start-test')).toBeNull();
  });
  expect(screen.getByTestId('live-test-phone').props.value).toBe('');
  expect(harness.startTest).not.toHaveBeenCalled();
});

it('uses exact Android activation, resume, and pause protected calls', async () => {
  const harness = createAutomationHarness();

  await renderAutomation(harness);

  await fireEvent.press(await screen.findByTestId('live-review-activation'));
  expect(await screen.findByTestId('live-confirm-activation')).toBeTruthy();
  expect(harness.prepareActivation).toHaveBeenCalledTimes(1);

  await fireEvent.press(screen.getByTestId('live-confirm-activation'));
  await waitFor(() => {
    expect(harness.activate).toHaveBeenCalledWith({
      handle: 'activation-review',
      expectedRevision: revision('21'),
    });
  });

  harness.getHome.mockResolvedValueOnce(
    ok(androidHome('paused-repair'), revision('22')),
  );
  harness.getAccount.mockResolvedValueOnce(
    ok(androidAccount('paused-repair'), revision('22')),
  );
  harness.getMessageEditor.mockResolvedValueOnce(
    ok(configuredMessage, revision('22')),
  );
  harness.getPolicyEditor.mockResolvedValueOnce(
    ok(configuredPolicy, revision('22')),
  );
  harness.getLatestTest.mockResolvedValueOnce(
    ok(
      {
        platform: 'android' as const,
        phase: 'passed' as const,
        updatedAt: generatedAt,
      },
      revision('22'),
    ),
  );
  await act(async () => {
    harness.emit({
      revision: revision('22'),
      areas: ['home', 'account', 'messages', 'setup', 'automation'],
    });
  });

  await fireEvent.press(await screen.findByTestId('live-review-resume'));
  expect(await screen.findByTestId('live-confirm-activation')).toBeTruthy();
  expect(harness.prepareResume).toHaveBeenCalledTimes(1);

  await fireEvent.press(screen.getByTestId('live-confirm-activation'));
  await waitFor(() => {
    expect(harness.resume).toHaveBeenCalledWith({
      handle: 'resume-review',
      expectedRevision: revision('22'),
    });
  });

  harness.getHome.mockResolvedValueOnce(
    ok(androidHome('active'), revision('23')),
  );
  harness.getAccount.mockResolvedValueOnce(
    ok(androidAccount('automation-active'), revision('23')),
  );
  harness.getMessageEditor.mockResolvedValueOnce(
    ok(configuredMessage, revision('23')),
  );
  harness.getPolicyEditor.mockResolvedValueOnce(
    ok(configuredPolicy, revision('23')),
  );
  harness.getLatestTest.mockResolvedValueOnce(
    ok(
      {
        platform: 'android' as const,
        phase: 'passed' as const,
        updatedAt: generatedAt,
      },
      revision('23'),
    ),
  );
  await act(async () => {
    harness.emit({
      revision: revision('23'),
      areas: ['home', 'account', 'messages', 'setup', 'automation'],
    });
  });

  await fireEvent.press(await screen.findByTestId('live-review-pause'));
  await fireEvent.press(await screen.findByTestId('live-confirm-pause'));
  await waitFor(() => {
    expect(harness.pauseAll).toHaveBeenCalledWith({
      expectedRevision: revision('23'),
    });
  });
});

it('keeps configured Android policy out of Automation without a Schedule action or editor', async () => {
  const harness = createAutomationHarness();

  await renderAutomation(harness);

  expect(await screen.findByTestId('live-automation-screen')).toBeTruthy();
  expect(screen.queryByTestId('live-automation-open-schedule')).toBeNull();
});

it('keeps Android blocker copy and recovery actions visible without mounting policy editing', async () => {
  const harness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(
        {
          ...androidHome('test-only'),
          automation: {
            platform: 'android',
            desired: 'paused',
            effective: 'test-only',
            readiness: {
              platform: 'android',
              test: allowedGate,
              activation: {
                kind: 'blocked',
                issues: [
                  {
                    id: 'issue-1' as IssueId,
                    code: 'sms-permission-denied',
                    severity: 'blocking',
                    blocks: ['activation'],
                  },
                ],
              },
              birthday: allowedGate,
              lastCheckedAt: generatedAt,
            },
          },
        },
        revision('11'),
      ),
    ),
  });

  await renderAutomation(harness);

  expect(
    await screen.findByTestId('live-automation-readiness-issues'),
  ).toBeTruthy();
  expect(screen.getByText(/permission was not allowed/i)).toBeTruthy();
  expect(screen.queryByTestId('live-automation-open-schedule')).toBeNull();
});

it('suppresses Android consequential actions while verified Home is refreshing', async () => {
  const appStateListeners = captureAppStateListeners();
  const refreshedHome = deferred<NativeResult<HomeProjection>>();
  const getHome = jest
    .fn()
    .mockResolvedValueOnce(ok(androidHome('test-only'), revision('23')))
    .mockImplementationOnce(() => refreshedHome.promise);
  const harness = createAutomationHarness({
    projectionRevision: revision('23'),
    getHome,
  });

  await renderAutomation(harness);

  expect(await screen.findByTestId('live-review-activation')).toBeTruthy();

  await act(async () => {
    appStateListeners.forEach(listener => listener('active'));
  });

  expect(screen.queryByTestId('live-review-activation')).toBeNull();
  expect(screen.queryByTestId('live-prepare-test')).toBeNull();

  await act(async () => {
    refreshedHome.resolve(ok(androidHome('test-only'), revision('23')));
  });

  expect(await screen.findByTestId('live-review-activation')).toBeTruthy();
});

it('suppresses Android activation while latest TEST truth is refreshing', async () => {
  const appStateListeners = captureAppStateListeners();
  const refreshedTest = deferred<
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
        revision('25'),
      ),
    )
    .mockImplementationOnce(() => refreshedTest.promise);
  const harness = createAutomationHarness({
    projectionRevision: revision('25'),
    getLatestTest,
  });

  await renderAutomation(harness);

  expect(await screen.findByTestId('live-review-activation')).toBeTruthy();

  await act(async () => {
    appStateListeners.forEach(listener => listener('active'));
  });

  expect(screen.queryByTestId('live-review-activation')).toBeNull();

  await act(async () => {
    refreshedTest.resolve(
      ok(
        {
          platform: 'android' as const,
          phase: 'passed' as const,
          updatedAt: generatedAt,
        },
        revision('25'),
      ),
    );
  });

  expect(await screen.findByTestId('live-review-activation')).toBeTruthy();
});

it('clears an Android protected review when automation invalidates', async () => {
  const harness = createAutomationHarness();

  await renderAutomation(harness);

  await fireEvent.press(await screen.findByTestId('live-review-activation'));
  expect(await screen.findByTestId('live-confirm-activation')).toBeTruthy();

  await act(async () => {
    harness.emit({ revision: revision('23'), areas: ['automation'] });
  });

  await waitFor(() => {
    expect(screen.queryByTestId('live-confirm-activation')).toBeNull();
  });
});

it('routes missing Android message and policy configuration before TEST', async () => {
  const onOpenMessage = jest.fn();
  const onOpenSchedule = jest.fn();
  const harness = createAutomationHarness({
    getHome: jest.fn(async () =>
      ok(androidHome('not-configured'), revision('11')),
    ),
    getAccount: jest.fn(async () =>
      ok(androidAccount('test-only'), revision('11')),
    ),
    getMessageEditor: jest.fn(async () =>
      ok({ kind: 'not-configured' as const }, revision('11')),
    ),
    getPolicyEditor: jest.fn(async () =>
      ok({ kind: 'not-configured' as const }, revision('11')),
    ),
  });

  await renderAutomation(
    harness,
    androidCapability,
    onOpenMessage,
    onOpenSchedule,
  );

  expect(
    await screen.findByTestId('live-automation-open-message'),
  ).toBeTruthy();
  await fireEvent.press(screen.getByTestId('live-automation-open-message'));
  expect(onOpenMessage).toHaveBeenCalledTimes(1);

  harness.getMessageEditor.mockResolvedValueOnce(
    ok(configuredMessage, revision('12')),
  );
  harness.getHome.mockResolvedValueOnce(
    ok(androidHome('not-configured'), revision('12')),
  );
  harness.getAccount.mockResolvedValueOnce(
    ok(androidAccount('test-only'), revision('12')),
  );
  harness.getPolicyEditor.mockResolvedValueOnce(
    ok({ kind: 'not-configured' as const }, revision('12')),
  );
  await act(async () => {
    harness.emit({
      revision: revision('12'),
      areas: ['messages', 'home', 'account', 'setup'],
    });
  });

  expect(
    await screen.findByTestId('live-automation-open-schedule'),
  ).toBeTruthy();
  await fireEvent.press(screen.getByTestId('live-automation-open-schedule'));
  expect(onOpenSchedule).toHaveBeenCalledTimes(1);
});

it('fails closed with one Android configuration retry for stale or unavailable editor truth', async () => {
  const failingEditor = deferred<NativeResult<MessageEditorProjection>>();
  const getMessageEditor = jest
    .fn()
    .mockImplementationOnce(() => failingEditor.promise)
    .mockResolvedValueOnce(ok(configuredMessage, revision('11')));
  const harness = createAutomationHarness({ getMessageEditor });

  await renderAutomation(harness);

  await act(async () => {
    failingEditor.resolve(nativeInternalError('NATIVE_CONTRACT_INVALID'));
  });

  expect(
    await screen.findByTestId('live-automation-check-configuration-status'),
  ).toBeTruthy();
  expect(screen.queryByTestId('live-run-another-test')).toBeNull();

  await fireEvent.press(
    screen.getByTestId('live-automation-check-configuration-status'),
  );

  expect(await screen.findByTestId('live-run-another-test')).toBeTruthy();
  expect(
    screen.queryByTestId('live-automation-check-configuration-status'),
  ).toBeNull();
});

it('does not expose Android setup editing when not-configured masks a non-owner lifecycle', async () => {
  const harness = createAutomationHarness({
    getAccount: jest.fn(async () =>
      ok(
        {
          kind: 'connected' as const,
          displayEmail: 'user@example.test' as PrivateEmail,
          sender: {
            platform: 'android' as const,
            kind: 'standby' as const,
            activeOtherDeviceLabel: 'Pixel 9',
          },
        },
        revision('11'),
      ),
    ),
    getHome: jest.fn(async () => ok(androidHome('standby'), revision('11'))),
    getMessageEditor: jest.fn(async () =>
      ok({ kind: 'not-configured' as const }, revision('11')),
    ),
  });

  await renderAutomation(harness);

  expect(await screen.findByTestId('live-automation-screen')).toBeTruthy();
  expect(screen.queryByTestId('live-automation-open-message')).toBeNull();
  expect(screen.getByText('Another phone is active')).toBeTruthy();
});
