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
import type { TestInstance } from 'test-renderer';

import { ThemeProvider } from '../../app/providers/ThemeProvider';
import type { ProjectionInvalidation } from '../../application/ports/AppProjectionPort';
import type { ActivityRecord } from '../../domain/activity/model';
import type {
  PrivacyActionKind,
  PrivacyActionReview,
  PrivacyInventory,
  PrivacyOperationProjection,
} from '../../domain/privacy/model';
import type {
  ActivityId,
  NativeRevision,
  PrivacyOperationId,
  PrivacyReviewHandle,
  SafeSupportCode,
} from '../../domain/shared/brand';
import type { PlatformCapability } from '../../domain/shared/platform';
import type { NativeProblem, NativeResult } from '../../domain/shared/result';
import type { UtcInstant } from '../../domain/shared/temporal';
import { LocalizationProvider } from '../../localization/LocalizationProvider';
import { appI18n } from '../../localization/i18n';
import type { LiveAppPort } from './LiveAppPort';
import {
  LiveActivityDetailScreen,
  LiveActivityScreen,
} from './LiveActivityScreen';

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

const internalError = (supportCode: string): NativeResult<never> => ({
  kind: 'error',
  problem: {
    kind: 'internal',
    supportCode: supportCode as SafeSupportCode,
  },
});

const deferred = <Value,>() => {
  let resolve!: (value: Value) => void;
  const promise = new Promise<Value>(resolver => {
    resolve = resolver;
  });
  return { promise, resolve };
};

const pressHandler = (instance: TestInstance): (() => unknown) => {
  let fiber = instance.unstable_fiber;
  while (fiber?.memoizedProps) {
    const onPress = (fiber.memoizedProps as { onPress?: unknown }).onPress;
    if (typeof onPress === 'function') return onPress as () => unknown;
    if (fiber.return === null || typeof fiber.return.type === 'string') break;
    fiber = fiber.return;
  }
  throw new Error('Press handler not found');
};

const activityRecord: ActivityRecord = {
  id: 'activity-1' as ActivityId,
  kind: 'paused',
  reason: 'coordination-unavailable',
  occurredAt: generatedAt,
  recovery: { route: 'people' },
};

const inventory = (activityCount: number): PrivacyInventory => ({
  localContactCount: 2,
  enabledRecipientCount: 1,
  approvalCount: 1,
  activityCount,
  templateCount: 1,
  localStorageBytes: 1024,
  consentVersions: ['privacy-v1'],
  externalSmsCopiesNotControlled: true,
});

const clearReview = (
  overrides: Partial<PrivacyActionReview> = {},
): PrivacyActionReview => ({
  handle: 'clear-activity-review' as PrivacyReviewHandle,
  kind: 'clear-activity',
  titleKey: 'privacy.clear-activity',
  consequenceKeys: [
    'privacy.consequence.activity-hidden',
    'privacy.consequence.safety-retained',
  ],
  preissuedPermitMayFinish: true,
  remoteConnectionRequired: false,
  externalSmsCopiesNotErased: true,
  ...overrides,
});

const completedOperation = (
  action: PrivacyActionKind = 'clear-activity',
): PrivacyOperationProjection => ({
  kind: 'complete',
  id: 'clear-activity-operation' as PrivacyOperationId,
  action,
  completedAt: generatedAt,
  externalSmsCopiesNotErased: true,
});

type HarnessState = {
  cleared: boolean;
  inventoryProblem?: NativeProblem | undefined;
  inventoryRevision?: NativeRevision | undefined;
  listProblem?: NativeProblem | undefined;
  revision: NativeRevision;
};

type ActivityHarness = Readonly<{
  confirmAction: jest.MockedFunction<LiveAppPort['confirmAction']>;
  emit: (event: ProjectionInvalidation) => void;
  getInventory: jest.MockedFunction<LiveAppPort['getInventory']>;
  listActivity: jest.MockedFunction<LiveAppPort['listActivity']>;
  port: LiveAppPort;
  prepareAction: jest.MockedFunction<LiveAppPort['prepareAction']>;
  state: HarnessState;
}>;

const createActivityHarness = (): ActivityHarness => {
  const state: HarnessState = {
    cleared: false,
    revision: revision('1'),
  };
  const listeners = new Set<(event: ProjectionInvalidation) => void>();
  const listActivity: jest.MockedFunction<LiveAppPort['listActivity']> =
    jest.fn(async (_query: Parameters<LiveAppPort['listActivity']>[0]) =>
      state.listProblem
        ? ({ kind: 'error', problem: state.listProblem } as const)
        : ok(
            {
              items: state.cleared ? [] : [activityRecord],
            },
            state.revision,
          ),
    );
  const getInventory: jest.MockedFunction<LiveAppPort['getInventory']> =
    jest.fn(async () =>
      state.inventoryProblem
        ? ({ kind: 'error', problem: state.inventoryProblem } as const)
        : ok(
            inventory(state.cleared ? 0 : 1),
            state.inventoryRevision ?? state.revision,
          ),
    );
  const prepareAction: jest.MockedFunction<LiveAppPort['prepareAction']> =
    jest.fn(async (_input: Parameters<LiveAppPort['prepareAction']>[0]) => {
      state.revision = revision('2');
      return ok(clearReview(), state.revision);
    });
  const confirmAction: jest.MockedFunction<LiveAppPort['confirmAction']> =
    jest.fn(async (_input: Parameters<LiveAppPort['confirmAction']>[0]) => {
      state.revision = revision('3');
      state.cleared = true;
      return ok(completedOperation(), state.revision);
    });
  const subscribeInvalidations: LiveAppPort['subscribeInvalidations'] =
    listener => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    };
  const port = {
    confirmAction,
    getInventory,
    listActivity,
    prepareAction,
    subscribeInvalidations,
  } as unknown as LiveAppPort;

  return {
    confirmAction,
    emit: event => listeners.forEach(listener => listener(event)),
    getInventory,
    listActivity,
    port,
    prepareAction,
    state,
  };
};

const androidCapability: PlatformCapability = {
  platform: 'android',
  deliveryMode: 'unattended-device-sms',
  minimumApiLevel: 29,
  unattendedSms: 'release-gated',
  userComposer: 'available-as-explicit-alternative',
};

const renderActivity = async (
  harness: ActivityHarness,
  capability: PlatformCapability = androidCapability,
) => {
  const onOpenDetail = jest.fn();
  const view = await render(
    <LocalizationProvider>
      <ThemeProvider initialPreference="light">
        <LiveActivityScreen
          capability={capability}
          onBack={jest.fn()}
          onOpenDetail={onOpenDetail}
          port={harness.port}
        />
      </ThemeProvider>
    </LocalizationProvider>,
  );
  return { ...view, onOpenDetail };
};

const renderDetail = async (harness: ActivityHarness) => {
  const onOpenRecovery = jest.fn();
  const view = await render(
    <LocalizationProvider>
      <ThemeProvider initialPreference="light">
        <LiveActivityDetailScreen
          activityId={activityRecord.id}
          onBack={jest.fn()}
          onOpenRecovery={onOpenRecovery}
          port={harness.port}
        />
      </ThemeProvider>
    </LocalizationProvider>,
  );
  return { ...view, onOpenRecovery };
};

const prepareReview = async () => {
  await fireEvent.press(await screen.findByTestId('live-activity-clear'));
  return screen.findByTestId('live-activity-clear-review');
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

it('keeps compact rows free of recovery actions and omits a routine Refresh control', async () => {
  const harness = createActivityHarness();
  const { onOpenDetail } = await renderActivity(harness);

  const row = await screen.findByTestId('live-activity-activity-1');
  expect(screen.getByTestId('live-activity-list')).toBeTruthy();
  expect(screen.queryByTestId('live-activity-recovery-activity-1')).toBeNull();
  expect(screen.queryByTestId('live-activity-detail-recovery')).toBeNull();
  expect(screen.queryByText('Refresh')).toBeNull();

  await fireEvent.press(row);
  expect(onOpenDetail).toHaveBeenCalledWith(activityRecord);
});

it('exposes recovery only in a usable detail and hides the raw reason until Support details', async () => {
  const harness = createActivityHarness();
  const { onOpenRecovery } = await renderDetail(harness);

  const recovery = await screen.findByTestId('live-activity-detail-recovery');
  expect(screen.queryByText('coordination-unavailable')).toBeNull();
  expect(
    screen.queryByTestId('live-activity-detail-support-details'),
  ).toBeNull();

  await fireEvent.press(recovery);
  expect(onOpenRecovery).toHaveBeenCalledWith('people');

  await fireEvent.press(
    screen.getByTestId('live-activity-detail-support-toggle'),
  );
  expect(
    screen.getByTestId('live-activity-detail-support-details'),
  ).toBeTruthy();
  expect(screen.getByText('coordination-unavailable')).toBeTruthy();
});

it.each([
  {
    capability: androidCapability,
    expected:
      'Android can retain a separate content-free duplicate-safety record for up to 400 days. Clear activity does not remove it.',
    platform: 'Android',
  },
])(
  '$platform clear review shows only its exact retention boundary',
  async ({ capability, expected }) => {
    const harness = createActivityHarness();
    await renderActivity(harness, capability);

    await prepareReview();

    expect(screen.getByTestId('live-activity-clear-retention')).toBeTruthy();
    expect(screen.getByText(expected)).toBeTruthy();
    expect(
      screen.getAllByText(
        'Copies in carriers, recipients, SMS, Messages and outside backups are not erased.',
      ),
    ).toHaveLength(1);
  },
);

it('fails closed when native returns a malformed clear review', async () => {
  const harness = createActivityHarness();
  harness.prepareAction.mockResolvedValue(
    ok(
      clearReview({
        consequenceKeys: ['privacy.consequence.activity-hidden'],
      }),
    ),
  );
  await renderActivity(harness);

  await fireEvent.press(await screen.findByTestId('live-activity-clear'));

  expect(await screen.findByText('Action not completed')).toBeTruthy();
  expect(screen.queryByTestId('live-activity-clear-review')).toBeNull();
  expect(harness.confirmAction).not.toHaveBeenCalled();
});

it('retires a late prepare review after an Activity or Privacy invalidation', async () => {
  const harness = createActivityHarness();
  harness.prepareAction.mockImplementation(async () => {
    harness.state.revision = revision('2');
    harness.emit({
      areas: ['activity', 'privacy'],
      revision: harness.state.revision,
    });
    await Promise.resolve();
    return ok(clearReview(), harness.state.revision);
  });
  await renderActivity(harness);

  await fireEvent.press(await screen.findByTestId('live-activity-clear'));

  await waitFor(() =>
    expect(screen.queryByTestId('live-activity-clear-review')).toBeNull(),
  );
  expect(await screen.findByTestId('live-activity-clear')).toBeTruthy();
  expect(harness.confirmAction).not.toHaveBeenCalled();
});

it('accepts a matching confirm invalidation only after exact cleared Activity and inventory truth', async () => {
  const harness = createActivityHarness();
  const nativeOrder: string[] = [];
  harness.confirmAction.mockImplementation(async () => {
    nativeOrder.push('confirm');
    harness.state.cleared = true;
    harness.state.revision = revision('3');
    harness.emit({
      areas: ['activity', 'privacy'],
      revision: harness.state.revision,
    });
    nativeOrder.push('matching invalidation');
    await Promise.resolve();
    nativeOrder.push('deferred success');
    return ok(completedOperation(), harness.state.revision);
  });
  await renderActivity(harness);
  await prepareReview();

  await fireEvent.press(screen.getByTestId('live-activity-clear-confirm'));

  expect(nativeOrder).toEqual([
    'confirm',
    'matching invalidation',
    'deferred success',
  ]);
  expect(
    await screen.findByText('The protected operation is complete.'),
  ).toBeTruthy();
  expect(screen.getByTestId('live-action-feedback-success')).toBeTruthy();
  expect(screen.queryByTestId('live-activity-activity-1')).toBeNull();
  expect(harness.listActivity.mock.calls.length).toBeGreaterThanOrEqual(4);
  expect(harness.getInventory.mock.calls.length).toBeGreaterThanOrEqual(4);
});

it.each([
  'wrong action',
  'mismatched reload revision',
  'failed reload',
] as const)('%s never claims Activity was cleared', async failureMode => {
  const harness = createActivityHarness();
  await renderActivity(harness);
  await prepareReview();

  harness.confirmAction.mockImplementation(async () => {
    harness.state.revision = revision('3');
    if (failureMode === 'wrong action') {
      return ok(
        completedOperation('clear-gemini-templates'),
        harness.state.revision,
      );
    }
    harness.state.cleared = true;
    if (failureMode === 'mismatched reload revision') {
      harness.state.inventoryRevision = revision('4');
    } else {
      const failed = internalError('ACTIVITY_CLEAR_RELOAD_FAILED');
      if (failed.kind === 'error') {
        harness.state.listProblem = failed.problem;
      }
    }
    return ok(completedOperation(), harness.state.revision);
  });

  await fireEvent.press(screen.getByTestId('live-activity-clear-confirm'));

  expect(await screen.findByText('Action not completed')).toBeTruthy();
  expect(screen.queryByTestId('live-action-feedback-success')).toBeNull();
  expect(screen.queryByText('The protected operation is complete.')).toBeNull();
  expect(harness.confirmAction).toHaveBeenCalledTimes(1);
});

it('coalesces a double confirm into one native request', async () => {
  const harness = createActivityHarness();
  const pending = deferred<NativeResult<PrivacyOperationProjection>>();
  harness.confirmAction.mockReturnValue(pending.promise);
  await renderActivity(harness);
  await prepareReview();

  const confirm = screen.getByTestId('live-activity-clear-confirm');
  const onPress = pressHandler(confirm);

  await act(async () => {
    const firstRequest = onPress() as Promise<unknown>;
    const secondRequest = onPress() as Promise<unknown>;
    expect(harness.confirmAction).toHaveBeenCalledTimes(1);
    harness.state.cleared = true;
    harness.state.revision = revision('3');
    pending.resolve(ok(completedOperation(), harness.state.revision));
    await Promise.all([firstRequest, secondRequest]);
  });

  expect(
    await screen.findByText('The protected operation is complete.'),
  ).toBeTruthy();
  expect(harness.confirmAction).toHaveBeenCalledTimes(1);
});
