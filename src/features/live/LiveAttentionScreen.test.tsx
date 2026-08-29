import React from 'react';
import { AccessibilityInfo, Platform } from 'react-native';
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react-native';

import { ThemeProvider } from '../../app/providers/ThemeProvider';
import type { ReadinessIssue } from '../../domain/readiness/model';
import type {
  ActionHandle,
  IssueId,
  NativeRevision,
  SafeSupportCode,
} from '../../domain/shared/brand';
import type { NativeResult } from '../../domain/shared/result';
import type { SafeReasonCode } from '../../domain/shared/reasonCodes';
import type { UtcInstant } from '../../domain/shared/temporal';
import { LocalizationProvider } from '../../localization/LocalizationProvider';
import { appI18n } from '../../localization/i18n';
import type { LiveAppPort } from './LiveAppPort';
import { LiveAttentionScreen } from './LiveAttentionScreen';

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

const originalPlatform = Platform.OS;
const generatedAt = '2026-07-12T07:00:00Z' as UtcInstant;
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

const issue = ({
  action,
  blocks = [],
  code,
  id,
  severity,
}: {
  action?: ReadinessIssue['action'];
  blocks?: ReadinessIssue['blocks'];
  code: SafeReasonCode;
  id: string;
  severity: ReadinessIssue['severity'];
}): ReadinessIssue => ({
  action,
  blocks,
  code,
  id: id as IssueId,
  severity,
});

type AttentionHarness = Readonly<{
  listIssues: jest.MockedFunction<LiveAppPort['listIssues']>;
  performAction: jest.MockedFunction<LiveAppPort['performAction']>;
  port: LiveAppPort;
}>;

const createAttentionHarness = ({
  listIssues = jest.fn(async () => ok([])),
  performAction = jest.fn(
    async (_input: Parameters<LiveAppPort['performAction']>[0]) =>
      ok({ kind: 'cancelled' as const }),
  ),
}: {
  listIssues?: jest.MockedFunction<LiveAppPort['listIssues']>;
  performAction?: jest.MockedFunction<LiveAppPort['performAction']>;
} = {}): AttentionHarness => {
  const port = {
    listIssues,
    performAction,
    subscribeInvalidations: () => () => undefined,
  } as unknown as LiveAppPort;
  return { listIssues, performAction, port };
};

const callbacks = () => ({
  onBack: jest.fn(),
  onOpenAutomation: jest.fn(),
  onOpenMessage: jest.fn(),
  onOpenPeople: jest.fn(),
  onOpenSettings: jest.fn(),
});

const renderAttention = async (
  port: LiveAppPort,
  routeCallbacks = callbacks(),
) => {
  const view = await render(
    <LocalizationProvider>
      <ThemeProvider initialPreference="light">
        <LiveAttentionScreen {...routeCallbacks} port={port} />
      </ThemeProvider>
    </LocalizationProvider>,
  );
  return { ...view, routeCallbacks };
};

const deferred = <Value,>() => {
  let resolve!: (value: Value) => void;
  const promise = new Promise<Value>(resolver => {
    resolve = resolver;
  });
  return { promise, resolve };
};

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

it('groups shuffled issues in the fixed repair order and keeps technical data behind Support details', async () => {
  const shuffled = [
    issue({
      blocks: [],
      code: 'phone-state-permission-permanently-denied',
      id: 'platform-issue',
      severity: 'info',
    }),
    issue({
      blocks: ['birthday'],
      code: 'invalid-segment-cap',
      id: 'approval-issue',
      severity: 'warning',
    }),
    issue({
      blocks: ['activation'],
      code: 'account-mismatch',
      id: 'account-issue',
      severity: 'blocking',
    }),
    issue({
      blocks: ['birthday'],
      code: 'contacts-stale',
      id: 'contacts-issue',
      severity: 'warning',
    }),
  ];
  const listIssues = jest.fn(async () => ok(shuffled));
  const { port } = createAttentionHarness({ listIssues });
  const view = await renderAttention(port);

  await screen.findByTestId('live-attention-issue-account-issue');
  const rendered = JSON.stringify(view.toJSON());
  const categoryPositions = [
    'Account',
    'People and contact details',
    'Message and schedule',
    'This phone',
  ].map(label => rendered.indexOf(label));
  expect(categoryPositions.every(position => position >= 0)).toBe(true);
  expect(categoryPositions).toEqual(
    [...categoryPositions].sort((a, b) => a - b),
  );

  expect(
    screen
      .getByTestId('live-attention-category-platform')
      .queryAll(
        node => node.props.testID === 'live-attention-issue-platform-issue',
      ),
  ).toHaveLength(1);
  expect(
    screen
      .getByTestId('live-attention-category-approval')
      .queryAll(
        node => node.props.testID === 'live-attention-issue-approval-issue',
      ),
  ).toHaveLength(1);

  for (const rawValue of [
    'account-mismatch',
    'contacts-stale',
    'invalid-segment-cap',
    'phone-state-permission-permanently-denied',
    'account-issue',
    'contacts-issue',
    'approval-issue',
    'platform-issue',
  ]) {
    expect(screen.queryByText(new RegExp(rawValue, 'u'))).toBeNull();
  }

  const blocking = screen.getByTestId('live-attention-status-account-issue');
  const warning = screen.getByTestId('live-attention-status-contacts-issue');
  const info = screen.getByTestId('live-attention-status-platform-issue');
  expect(blocking.props.accessibilityLabel).toMatch(/Blocking/u);
  expect(warning.props.accessibilityLabel).toMatch(/Warning/u);
  expect(info.props.accessibilityLabel).toMatch(/Information/u);
  for (const status of [blocking, warning, info]) {
    expect(status.props.accessibilityRole).toBe('text');
    expect(status.props.accessibilityLabel).toEqual(expect.any(String));
    expect(status.props.accessibilityLabel).not.toMatch(
      /account-mismatch|contacts-stale|phone-state-permission|Blocks:/u,
    );
  }

  await fireEvent.press(screen.getByTestId('live-attention-support-toggle'));
  expect(screen.getByTestId('live-attention-support-details')).toBeTruthy();
  for (const rawValue of [
    'account-mismatch',
    'contacts-stale',
    'invalid-segment-cap',
    'phone-state-permission-permanently-denied',
    'account-issue',
    'contacts-issue',
    'approval-issue',
    'platform-issue',
  ]) {
    expect(screen.getByText(new RegExp(rawValue, 'u'))).toBeTruthy();
  }
  expect(screen.getByText('Blocks: automation activation')).toBeTruthy();
  expect(screen.getAllByText('Blocks: birthday jobs')).toHaveLength(2);

  await fireEvent.press(screen.getByTestId('live-attention-support-toggle'));
  expect(screen.queryByTestId('live-attention-support-details')).toBeNull();
  expect(screen.queryByText(/invalid-segment-cap/u)).toBeNull();
});

it('routes the message-size repair to Message and never misroutes phone-state permission to People', async () => {
  const listIssues = jest.fn(async () =>
    ok([
      issue({
        blocks: ['birthday'],
        code: 'phone-state-permission-permanently-denied',
        id: 'phone-state',
        severity: 'blocking',
      }),
      issue({
        blocks: ['birthday'],
        code: 'invalid-segment-cap',
        id: 'segment-cap',
        severity: 'blocking',
      }),
      issue({
        blocks: ['activation'],
        code: 'permission-denied',
        id: 'generic-permission-denied',
        severity: 'blocking',
      }),
      issue({
        blocks: ['activation'],
        code: 'permission-permanently-denied',
        id: 'generic-permission-permanent',
        severity: 'blocking',
      }),
    ]),
  );
  const { port } = createAttentionHarness({ listIssues });
  const routeCallbacks = callbacks();
  await renderAttention(port, routeCallbacks);

  await fireEvent.press(
    await screen.findByTestId('live-attention-route-segment-cap'),
  );
  expect(routeCallbacks.onOpenMessage).toHaveBeenCalledTimes(1);
  expect(routeCallbacks.onOpenAutomation).not.toHaveBeenCalled();
  expect(routeCallbacks.onOpenPeople).not.toHaveBeenCalled();

  await fireEvent.press(screen.getByTestId('live-attention-route-phone-state'));
  expect(routeCallbacks.onOpenAutomation).toHaveBeenCalledTimes(1);
  expect(routeCallbacks.onOpenPeople).not.toHaveBeenCalled();

  await fireEvent.press(
    screen.getByTestId('live-attention-route-generic-permission-denied'),
  );
  await fireEvent.press(
    screen.getByTestId('live-attention-route-generic-permission-permanent'),
  );
  expect(routeCallbacks.onOpenAutomation).toHaveBeenCalledTimes(3);
  expect(routeCallbacks.onOpenPeople).not.toHaveBeenCalled();
});

it.each(['opened', 'cancelled'] as const)(
  'uses one current native handle, disables competing actions, and reloads after %s without claiming repair',
  async outcome => {
    const firstAction = {
      handle: 'background-settings' as ActionHandle,
      kind: 'native-action' as const,
      labelKey: 'must-never-render-this-native-label',
    };
    const secondAction = {
      handle: 'sms-settings' as ActionHandle,
      kind: 'native-action' as const,
      labelKey: 'must-also-stay-hidden',
    };
    const issues = [
      issue({
        action: firstAction,
        blocks: ['birthday'],
        code: 'background-restricted',
        id: 'background',
        severity: 'blocking',
      }),
      issue({
        action: secondAction,
        blocks: ['activation'],
        code: 'sms-permission-permanently-denied',
        id: 'sms-permission',
        severity: 'blocking',
      }),
    ];
    const listIssues = jest
      .fn()
      .mockResolvedValueOnce(ok(issues, revision('17')))
      .mockResolvedValue(ok(issues, revision('18')));
    const pending =
      deferred<Awaited<ReturnType<LiveAppPort['performAction']>>>();
    const performAction = jest.fn(
      (_input: Parameters<LiveAppPort['performAction']>[0]) => pending.promise,
    );
    const { port } = createAttentionHarness({ listIssues, performAction });
    await renderAttention(port);

    const firstButton = await screen.findByTestId(
      'live-attention-action-background',
    );
    expect(
      screen.queryByTestId('live-attention-route-sms-permission'),
    ).toBeNull();
    expect(
      screen.queryByText('must-never-render-this-native-label'),
    ).toBeNull();
    expect(screen.queryByText('must-also-stay-hidden')).toBeNull();
    fireEvent.press(firstButton);

    await waitFor(() => expect(performAction).toHaveBeenCalledTimes(1));
    expect(performAction).toHaveBeenCalledWith({
      expectedRevision: '17',
      handle: 'background-settings',
    });
    expect(
      screen.getByTestId('live-attention-action-background').props
        .accessibilityState,
    ).toEqual({ disabled: true });
    expect(
      screen.getByTestId('live-attention-action-sms-permission').props
        .accessibilityState,
    ).toEqual({ disabled: true });

    await act(async () => {
      pending.resolve(ok({ kind: outcome }, revision('18')));
    });
    await waitFor(() => expect(listIssues).toHaveBeenCalledTimes(2));
    expect(
      await screen.findByTestId('live-attention-issue-background'),
    ).toBeTruthy();
    expect(
      screen.getByText(
        outcome === 'opened'
          ? /Return and choose Check again after making a choice/u
          : /No repair is assumed/u,
      ),
    ).toBeTruthy();
    expect(screen.queryByText(/Repair complete|Problem fixed/u)).toBeNull();
  },
);

it('reloads a stale action result and keeps the blocker without exposing the native revision', async () => {
  const blocker = issue({
    action: {
      handle: 'background-settings' as ActionHandle,
      kind: 'native-action',
      labelKey: 'settings.background',
    },
    blocks: ['birthday'],
    code: 'background-restricted',
    id: 'stale-background',
    severity: 'blocking',
  });
  const listIssues = jest
    .fn()
    .mockResolvedValueOnce(ok([blocker], revision('4')))
    .mockResolvedValue(ok([blocker], revision('5')));
  const performAction = jest.fn(
    async (_input: Parameters<LiveAppPort['performAction']>[0]) => ({
      kind: 'error' as const,
      problem: {
        kind: 'stale-revision' as const,
        latestRevision: revision('5'),
      },
    }),
  );
  const { port } = createAttentionHarness({ listIssues, performAction });
  await renderAttention(port);

  await fireEvent.press(
    await screen.findByTestId('live-attention-action-stale-background'),
  );
  await waitFor(() => expect(listIssues).toHaveBeenCalledTimes(2));
  expect(
    screen.getByTestId('live-attention-issue-stale-background'),
  ).toBeTruthy();
  const staleAlert = screen.getByText('Action not completed').parent?.parent
    ?.parent;
  expect(staleAlert?.props.accessibilityRole).toBe('alert');
  expect(staleAlert?.props.accessibilityLiveRegion).toBe('assertive');
  expect(screen.queryByText(/revision:5|stale-revision/u)).toBeNull();
  expect(screen.queryByText(/Repair complete|Problem fixed/u)).toBeNull();
});

it('keeps the last verified blockers when refresh fails', async () => {
  const blocker = issue({
    blocks: ['birthday'],
    code: 'background-restricted',
    id: 'verified-background',
    severity: 'blocking',
  });
  const listIssues = jest
    .fn()
    .mockResolvedValueOnce(ok([blocker], revision('2')))
    .mockResolvedValue({
      kind: 'error' as const,
      problem: {
        code: 'coordination-unavailable' as const,
        kind: 'temporarily-unavailable' as const,
      },
    });
  const { port } = createAttentionHarness({ listIssues });
  await renderAttention(port);

  await fireEvent.press(
    await screen.findByTestId('live-attention-check-again'),
  );
  await waitFor(() => expect(listIssues).toHaveBeenCalledTimes(2));
  expect(
    screen.getByTestId('live-attention-issue-verified-background'),
  ).toBeTruthy();
  expect(screen.getByText('Could not refresh')).toBeTruthy();
  expect(screen.queryByText(/Repair complete|Problem fixed/u)).toBeNull();
});

it('keeps unsupported and coordination issues blocked without inventing a repair action', async () => {
  const listIssues = jest.fn(async () =>
    ok([
      issue({
        blocks: ['activation'],
        code: 'coordination-unavailable',
        id: 'coordination',
        severity: 'blocking',
      }),
      issue({
        blocks: ['birthday'],
        code: 'platform-unsupported',
        id: 'unsupported',
        severity: 'blocking',
      }),
    ]),
  );
  const { port } = createAttentionHarness({ listIssues });
  await renderAttention(port);

  expect(
    await screen.findByTestId('live-attention-no-action-coordination'),
  ).toBeTruthy();
  expect(
    screen.getByTestId('live-attention-no-action-unsupported'),
  ).toBeTruthy();
  expect(screen.queryByTestId('live-attention-route-coordination')).toBeNull();
  expect(screen.queryByTestId('live-attention-route-unsupported')).toBeNull();
  expect(screen.queryByTestId('live-attention-action-coordination')).toBeNull();
  expect(screen.queryByTestId('live-attention-action-unsupported')).toBeNull();
  expect(screen.queryByText(/Repair complete|Problem fixed/u)).toBeNull();
});

it('keeps action errors assertive while hiding the internal support code until explicit issue disclosure', async () => {
  const blocker = issue({
    action: {
      handle: 'background-settings' as ActionHandle,
      kind: 'native-action',
      labelKey: 'settings.background',
    },
    blocks: ['birthday'],
    code: 'background-restricted',
    id: 'internal-action',
    severity: 'blocking',
  });
  const listIssues = jest.fn(async () => ok([blocker]));
  const performAction = jest.fn(
    async (_input: Parameters<LiveAppPort['performAction']>[0]) => ({
      kind: 'error' as const,
      problem: {
        kind: 'internal' as const,
        supportCode: 'ATTENTION_PRIVATE_INTERNAL' as SafeSupportCode,
      },
    }),
  );
  const { port } = createAttentionHarness({ listIssues, performAction });
  await renderAttention(port);

  await fireEvent.press(
    await screen.findByTestId('live-attention-action-internal-action'),
  );
  const alert = screen.getByText('Action not completed').parent?.parent?.parent;
  expect(alert?.props.accessibilityRole).toBe('alert');
  expect(alert?.props.accessibilityLiveRegion).toBe('assertive');
  expect(screen.queryByText(/ATTENTION_PRIVATE_INTERNAL/u)).toBeNull();
  expect(screen.queryByText(/background-restricted/u)).toBeNull();

  await fireEvent.press(screen.getByTestId('live-attention-support-toggle'));
  expect(screen.getByText(/background-restricted/u)).toBeTruthy();
  expect(screen.getByText(/ATTENTION_PRIVATE_INTERNAL/u)).toBeTruthy();
  expect(
    screen.getByTestId('live-attention-support-action-error'),
  ).toBeTruthy();
});
