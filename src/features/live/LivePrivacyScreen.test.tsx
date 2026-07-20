import React from 'react';
import {
  AccessibilityInfo,
  AppState,
  type AppStateStatus,
  Platform,
} from 'react-native';
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
import type { AccountProjection } from '../../domain/account/model';
import type {
  CurrentPrivacyOperationProjection,
  LatestDeletionReceiptProjection,
  PrivacyActionKind,
  PrivacyActionReview,
  PrivacyInventory,
  PrivacyOperationProjection,
} from '../../domain/privacy/model';
import type {
  NativeRevision,
  PrivacyOperationId,
  PrivacyReviewHandle,
  PrivateEmail,
  SafeSupportCode,
} from '../../domain/shared/brand';
import type { NativeProblem, NativeResult } from '../../domain/shared/result';
import type { UtcInstant } from '../../domain/shared/temporal';
import { LocalizationProvider } from '../../localization/LocalizationProvider';
import { appI18n } from '../../localization/i18n';
import type { LiveAppPort } from './LiveAppPort';
import { LivePrivacyScreen } from './LivePrivacyScreen';

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
const operationId = (value: string) => value as PrivacyOperationId;

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

const internalProblem = (supportCode: string): NativeProblem => ({
  kind: 'internal',
  supportCode: supportCode as SafeSupportCode,
});

const error = <Value,>(problem: NativeProblem): NativeResult<Value> => ({
  kind: 'error',
  problem,
});

const deferred = <Value,>() => {
  let resolve!: (value: Value) => void;
  const promise = new Promise<Value>(resolver => {
    resolve = resolver;
  });
  return { promise, resolve };
};

const inventory: PrivacyInventory = {
  localContactCount: 2,
  enabledRecipientCount: 1,
  approvalCount: 1,
  activityCount: 3,
  templateCount: 1,
  localStorageBytes: 1024,
  consentVersions: ['contacts-v1'],
  externalSmsCopiesNotControlled: true,
};

const connectedAccount: AccountProjection = {
  kind: 'connected',
  displayEmail: 'person@example.test' as PrivateEmail,
  sender: {
    platform: 'android',
    kind: 'paused-repair',
    epochLabel: 'epoch-1',
  },
};

const validReview = (
  kind: PrivacyActionKind,
  overrides: Partial<PrivacyActionReview> = {},
): PrivacyActionReview => ({
  handle: `${kind}-review` as PrivacyReviewHandle,
  kind,
  titleKey: `privacy.${kind}`,
  consequenceKeys: ['privacy.consequence.local-data-erased'],
  preissuedPermitMayFinish: false,
  remoteConnectionRequired: false,
  externalSmsCopiesNotErased: true,
  ...overrides,
});

const completedOperation = (
  action: PrivacyActionKind,
  id = operationId(`${action}-operation`),
): PrivacyOperationProjection => ({
  kind: 'complete',
  id,
  action,
  completedAt: generatedAt,
  externalSmsCopiesNotErased: true,
});

const pendingDeletion = (
  id: PrivacyOperationId,
): PrivacyOperationProjection => ({
  kind: 'remote-pending',
  id,
  action: 'delete-account',
  reason: 'network-offline',
  updatedAt: generatedAt,
});

const unknownDeletion = (
  id: PrivacyOperationId,
  sameAccountRetryAvailable = false,
): Extract<PrivacyOperationProjection, { kind: 'remote-unknown' }> => ({
  kind: 'remote-unknown',
  id,
  action: 'delete-account',
  reason: 'coordination-unavailable',
  updatedAt: generatedAt,
  localDataErased: true,
  remoteDeletionComplete: false,
  sameAccountRetryAvailable,
  externalSmsCopiesNotErased: true,
});

const completeDeletionReceipt = (
  id: PrivacyOperationId,
): LatestDeletionReceiptProjection => ({
  kind: 'complete',
  id,
  action: 'delete-account',
  completedAt: generatedAt,
  localDataErased: true,
  remoteDeletionComplete: true,
  externalSmsCopiesNotErased: true,
});

type HarnessState = {
  account: AccountProjection;
  accountProblem?: NativeProblem | undefined;
  accountRevision?: NativeRevision | undefined;
  currentOperation: CurrentPrivacyOperationProjection;
  currentOperationProblem?: NativeProblem | undefined;
  currentOperationRevision?: NativeRevision | undefined;
  deletionReceipt: LatestDeletionReceiptProjection;
  deletionReceiptProblem?: NativeProblem | undefined;
  deletionReceiptRevision?: NativeRevision | undefined;
  inventory: PrivacyInventory;
  inventoryProblem?: NativeProblem | undefined;
  inventoryRevision?: NativeRevision | undefined;
  revision: NativeRevision;
};

type PrivacyHarness = Readonly<{
  confirmAction: jest.MockedFunction<LiveAppPort['confirmAction']>;
  continueWithGoogle: jest.MockedFunction<LiveAppPort['continueWithGoogle']>;
  emit(event: ProjectionInvalidation): void;
  getAccount: jest.MockedFunction<LiveAppPort['getAccount']>;
  getCurrentOperation: jest.MockedFunction<LiveAppPort['getCurrentOperation']>;
  getInventory: jest.MockedFunction<LiveAppPort['getInventory']>;
  getLatestDeletionReceipt: jest.MockedFunction<
    LiveAppPort['getLatestDeletionReceipt']
  >;
  port: LiveAppPort;
  prepareAction: jest.MockedFunction<LiveAppPort['prepareAction']>;
  state: HarnessState;
}>;

const createHarness = (
  stateOverrides: Partial<HarnessState> = {},
): PrivacyHarness => {
  const state: HarnessState = {
    account: connectedAccount,
    currentOperation: { kind: 'none' },
    deletionReceipt: { kind: 'none' },
    inventory,
    revision: revision('1'),
    ...stateOverrides,
  };
  const listeners = new Set<(event: ProjectionInvalidation) => void>();
  const getAccount: jest.MockedFunction<LiveAppPort['getAccount']> = jest.fn(
    async (): Promise<NativeResult<AccountProjection>> =>
      state.accountProblem
        ? error(state.accountProblem)
        : ok(state.account, state.accountRevision ?? state.revision),
  );
  const getCurrentOperation: jest.MockedFunction<
    LiveAppPort['getCurrentOperation']
  > = jest.fn(
    async (): Promise<NativeResult<CurrentPrivacyOperationProjection>> =>
      state.currentOperationProblem
        ? error(state.currentOperationProblem)
        : ok(
            state.currentOperation,
            state.currentOperationRevision ?? state.revision,
          ),
  );
  const getLatestDeletionReceipt: jest.MockedFunction<
    LiveAppPort['getLatestDeletionReceipt']
  > = jest.fn(
    async (): Promise<NativeResult<LatestDeletionReceiptProjection>> =>
      state.deletionReceiptProblem
        ? error(state.deletionReceiptProblem)
        : ok(
            state.deletionReceipt,
            state.deletionReceiptRevision ?? state.revision,
          ),
  );
  const getInventory: jest.MockedFunction<LiveAppPort['getInventory']> =
    jest.fn(
      async (): Promise<NativeResult<PrivacyInventory>> =>
        state.inventoryProblem
          ? error(state.inventoryProblem)
          : ok(state.inventory, state.inventoryRevision ?? state.revision),
    );
  const prepareAction: jest.MockedFunction<LiveAppPort['prepareAction']> =
    jest.fn(async input => ok(validReview(input.kind), revision('2')));
  const confirmAction: jest.MockedFunction<LiveAppPort['confirmAction']> =
    jest.fn(async (_input: Parameters<LiveAppPort['confirmAction']>[0]) =>
      error<PrivacyOperationProjection>(
        internalProblem('UNEXPECTED_PRIVACY_CONFIRM'),
      ),
    );
  const continueWithGoogle: jest.MockedFunction<
    LiveAppPort['continueWithGoogle']
  > = jest.fn(async () =>
    error<AccountProjection>(internalProblem('UNEXPECTED_PRIVACY_RETRY')),
  );
  const subscribeInvalidations: LiveAppPort['subscribeInvalidations'] =
    listener => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    };
  const port = {
    checkAccountDeletionStatus: jest.fn(async () =>
      ok(state.deletionReceipt, state.revision),
    ),
    confirmAction,
    continueWithGoogle,
    getAccount,
    getCurrentOperation,
    getInventory,
    getLatestDeletionReceipt,
    getOperation: jest.fn(async () => ({
      kind: 'error',
      problem: internalProblem('UNEXPECTED_PRIVACY_GET_OPERATION'),
    })),
    prepareAction,
    repairLifecycleState: jest.fn(async () => ({
      kind: 'error',
      problem: internalProblem('UNEXPECTED_PRIVACY_REPAIR'),
    })),
    resumeOperation: jest.fn(async () => ({
      kind: 'error',
      problem: internalProblem('UNEXPECTED_PRIVACY_RESUME'),
    })),
    subscribeInvalidations,
  } as unknown as LiveAppPort;

  return {
    confirmAction,
    continueWithGoogle,
    emit: event => listeners.forEach(listener => listener(event)),
    getAccount,
    getCurrentOperation,
    getInventory,
    getLatestDeletionReceipt,
    port,
    prepareAction,
    state,
  };
};

const renderPrivacy = async (harness: PrivacyHarness) => {
  const onLifecycleStateChange = jest.fn(async () => undefined);
  const view = await render(
    <LocalizationProvider>
      <ThemeProvider initialPreference="light">
        <LivePrivacyScreen
          onBack={jest.fn()}
          onLifecycleStateChange={onLifecycleStateChange}
          onOpenHelpLegal={jest.fn()}
          platform="android"
          port={harness.port}
        />
      </ThemeProvider>
    </LocalizationProvider>,
  );
  return { ...view, onLifecycleStateChange };
};

const prepare = async (testID: string) => {
  await fireEvent.press(await screen.findByTestId(testID));
  return screen.findByTestId('live-privacy-review');
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

const actionGroupTestIDs = [
  'live-privacy-group-data-on-phone',
  'live-privacy-group-contacts-google',
  'live-privacy-group-sign-out',
  'live-privacy-group-wipe-local',
  'live-privacy-group-delete-account',
] as const;

let appStateListeners: Array<(state: AppStateStatus) => void> = [];

beforeEach(() => {
  jest.clearAllMocks();
  appStateListeners = [];
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: 'android',
  });
  jest
    .spyOn(AppState, 'addEventListener')
    .mockImplementation((_type, listener) => {
      appStateListeners.push(listener);
      return { remove: jest.fn() };
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

it('shows grouped direct actions without the legacy radio, prepare button, or Clear activity action', async () => {
  const harness = createHarness();
  await renderPrivacy(harness);

  for (const testID of actionGroupTestIDs) {
    expect(await screen.findByTestId(testID)).toBeTruthy();
  }
  for (const action of [
    'clear-gemini-templates',
    'disconnect-contacts',
    'revoke-google-access',
    'sign-out-retain',
    'sign-out-wipe',
    'wipe-local-data',
    'delete-account',
  ]) {
    expect(screen.getByTestId(`live-privacy-${action}`)).toBeTruthy();
  }
  expect(screen.queryAllByRole('radio')).toHaveLength(0);
  expect(screen.queryByTestId('live-privacy-action-group')).toBeNull();
  expect(screen.queryByTestId('live-privacy-prepare')).toBeNull();
  expect(screen.queryByText('Review this action')).toBeNull();
  expect(screen.queryByText('Clear activity')).toBeNull();

  await fireEvent.press(
    screen.getByTestId('live-privacy-clear-gemini-templates'),
  );
  expect(harness.prepareAction).toHaveBeenCalledWith({
    kind: 'clear-gemini-templates',
    expectedRevision: revision('1'),
  });
  expect(await screen.findByTestId('live-privacy-review')).toBeTruthy();
  expect(harness.confirmAction).not.toHaveBeenCalled();
});

it('keeps data details collapsed by default while critical guidance and action groups stay visible', async () => {
  const harness = createHarness();
  await renderPrivacy(harness);

  const toggle = await screen.findByTestId('live-privacy-data-details-toggle');
  expect(toggle.props.accessibilityRole).toBe('button');
  expect(toggle.props.accessibilityLabel).toBe('Data details');
  expect(toggle.props.accessibilityState).toMatchObject({ expanded: false });
  expect(screen.queryByTestId('live-privacy-inventory')).toBeNull();
  expect(screen.queryByTestId('live-cloud-privacy-boundary')).toBeNull();
  expect(screen.getByText('Screen privacy')).toBeTruthy();
  for (const testID of actionGroupTestIDs) {
    expect(screen.getByTestId(testID)).toBeTruthy();
  }
});

it('reveals and collapses the complete inventory and cloud boundary from one accessible toggle', async () => {
  const harness = createHarness();
  await renderPrivacy(harness);

  await fireEvent.press(
    await screen.findByTestId('live-privacy-data-details-toggle'),
  );

  expect(await screen.findByTestId('live-privacy-inventory')).toBeTruthy();
  expect(screen.getByTestId('live-cloud-privacy-boundary')).toBeTruthy();
  for (const inventoryFact of [
    'Local contacts',
    'Enabled people',
    'Approvals',
    'Activity records',
    'Saved message templates',
    'Local storage',
    'Last Contacts sync',
    'Recorded consent versions',
    'What the app retains',
    'Copies outside Birthday Autopilot',
  ]) {
    expect(screen.getByText(inventoryFact)).toBeTruthy();
  }
  expect(
    screen.getByText('Cloud service metadata is content-free, not data-free'),
  ).toBeTruthy();
  expect(screen.getByText('Provider retention boundary')).toBeTruthy();
  expect(screen.getByText(/cannot erase carrier/u)).toBeTruthy();
  expect(screen.getByText(/cannot promise immediate erasure/u)).toBeTruthy();
  const expandedToggle = screen.getByTestId('live-privacy-data-details-toggle');
  expect(expandedToggle.props.accessibilityLabel).toBe('Hide data details');
  expect(expandedToggle.props.accessibilityState).toMatchObject({
    expanded: true,
  });

  await fireEvent.press(expandedToggle);

  expect(screen.queryByTestId('live-privacy-inventory')).toBeNull();
  expect(screen.queryByTestId('live-cloud-privacy-boundary')).toBeNull();
  expect(
    screen.getByTestId('live-privacy-data-details-toggle').props
      .accessibilityState,
  ).toMatchObject({ expanded: false });
});

it('reloads all four projections and locks destructive actions after a malformed prepared review', async () => {
  const harness = createHarness();
  harness.prepareAction.mockResolvedValue(
    ok(
      validReview('wipe-local-data', {
        consequenceKeys: [],
      }),
      revision('2'),
    ),
  );
  await renderPrivacy(harness);
  await screen.findByTestId('live-privacy-wipe-local-data');

  await fireEvent.press(screen.getByTestId('live-privacy-wipe-local-data'));

  expect(await screen.findByText('Action not completed')).toBeTruthy();
  await waitFor(() => {
    expect(harness.getAccount).toHaveBeenCalledTimes(2);
    expect(harness.getCurrentOperation).toHaveBeenCalledTimes(2);
    expect(harness.getLatestDeletionReceipt).toHaveBeenCalledTimes(2);
    expect(harness.getInventory).toHaveBeenCalledTimes(2);
  });
  for (const testID of actionGroupTestIDs) {
    expect(screen.queryByTestId(testID)).toBeNull();
  }
  expect(screen.queryByTestId('live-privacy-review')).toBeNull();
  expect(harness.confirmAction).not.toHaveBeenCalled();
});

it.each(['invalidation', 'AppState'] as const)(
  'retires a late prepared review after a protected %s transition',
  async transition => {
    const harness = createHarness();
    const pending =
      deferred<Awaited<ReturnType<LiveAppPort['prepareAction']>>>();
    harness.prepareAction.mockReturnValue(pending.promise);
    await renderPrivacy(harness);

    const prepareButton = await screen.findByTestId(
      'live-privacy-wipe-local-data',
    );
    let prepareRequest!: Promise<unknown>;
    await act(() => {
      prepareRequest = pressHandler(prepareButton)() as Promise<unknown>;
    });
    await waitFor(() => expect(harness.prepareAction).toHaveBeenCalledTimes(1));

    await act(async () => {
      if (transition === 'invalidation') {
        harness.emit({ areas: ['privacy'], revision: revision('2') });
      } else {
        appStateListeners.forEach(listener => listener('active'));
      }
      await Promise.resolve();
    });
    await act(async () => {
      pending.resolve(ok(validReview('wipe-local-data'), revision('2')));
      await prepareRequest;
    });

    expect(screen.queryByTestId('live-privacy-review')).toBeNull();
    expect(
      await screen.findByTestId('live-privacy-wipe-local-data'),
    ).toBeTruthy();
    expect(harness.confirmAction).not.toHaveBeenCalled();
  },
);

it('fails closed when an ordinary confirmation returns a different action', async () => {
  const harness = createHarness();
  const wrongOperation = completedOperation('disconnect-contacts');
  harness.confirmAction.mockImplementation(async () => {
    harness.state.revision = revision('3');
    harness.state.currentOperation = wrongOperation;
    return ok(wrongOperation, harness.state.revision);
  });
  const { onLifecycleStateChange } = await renderPrivacy(harness);
  await prepare('live-privacy-clear-gemini-templates');

  await fireEvent.press(screen.getByTestId('live-privacy-confirm'));

  expect(await screen.findByText('Action not completed')).toBeTruthy();
  expect(screen.queryByTestId('live-action-feedback-success')).toBeNull();
  expect(onLifecycleStateChange).not.toHaveBeenCalled();
  expect(harness.confirmAction).toHaveBeenCalledTimes(1);
});

it('allows the pending-deletion local wipe exception only for the captured delete operation with authoritative corroboration', async () => {
  const deletionId = operationId('delete-operation-original');
  const confirmed = unknownDeletion(deletionId);
  const harness = createHarness({
    currentOperation: pendingDeletion(deletionId),
  });
  harness.confirmAction.mockImplementation(async () => {
    harness.state.revision = revision('3');
    harness.state.currentOperation = confirmed;
    return ok(confirmed, harness.state.revision);
  });
  const { onLifecycleStateChange } = await renderPrivacy(harness);
  await prepare('live-privacy-pending-deletion-wipe');

  await fireEvent.press(screen.getByTestId('live-privacy-confirm'));

  expect(
    await screen.findByText(
      'The operation is still running. Refresh to check again.',
    ),
  ).toBeTruthy();
  expect(screen.getByTestId('live-action-feedback-success')).toBeTruthy();
  expect(harness.prepareAction).toHaveBeenCalledWith({
    kind: 'wipe-local-data',
    expectedRevision: revision('1'),
  });
  expect(harness.confirmAction).toHaveBeenCalledWith({
    handle: 'wipe-local-data-review',
    expectedRevision: revision('2'),
  });
  expect(onLifecycleStateChange).toHaveBeenCalledTimes(1);
});

it('rejects a pending-deletion local wipe response with a different operation ID', async () => {
  const deletionId = operationId('delete-operation-original');
  const wrongOperation = unknownDeletion(operationId('delete-operation-other'));
  const harness = createHarness({
    currentOperation: pendingDeletion(deletionId),
  });
  harness.confirmAction.mockImplementation(async () => {
    harness.state.revision = revision('3');
    harness.state.currentOperation = wrongOperation;
    return ok(wrongOperation, harness.state.revision);
  });
  const { onLifecycleStateChange } = await renderPrivacy(harness);
  await prepare('live-privacy-pending-deletion-wipe');

  await fireEvent.press(screen.getByTestId('live-privacy-confirm'));

  expect(await screen.findByText('Action not completed')).toBeTruthy();
  expect(screen.queryByTestId('live-action-feedback-success')).toBeNull();
  expect(onLifecycleStateChange).not.toHaveBeenCalled();
});

it.each(['reload error', 'revision mismatch', 'value mismatch'] as const)(
  'suppresses confirmation success after an authoritative %s',
  async failureMode => {
    const harness = createHarness();
    const response = completedOperation(
      'wipe-local-data',
      operationId('wipe-response'),
    );
    harness.confirmAction.mockImplementation(async () => {
      harness.state.revision = revision('3');
      harness.state.currentOperation = response;
      if (failureMode === 'reload error') {
        harness.state.inventoryProblem = internalProblem(
          'PRIVACY_RELOAD_FAILED',
        );
      } else if (failureMode === 'revision mismatch') {
        harness.state.inventoryRevision = revision('4');
      } else {
        harness.state.currentOperation = completedOperation(
          'wipe-local-data',
          operationId('wipe-authoritative-other'),
        );
      }
      return ok(response, harness.state.revision);
    });
    const { onLifecycleStateChange } = await renderPrivacy(harness);
    await prepare('live-privacy-wipe-local-data');

    await fireEvent.press(screen.getByTestId('live-privacy-confirm'));

    expect(await screen.findByText('Action not completed')).toBeTruthy();
    expect(screen.queryByTestId('live-action-feedback-success')).toBeNull();
    expect(onLifecycleStateChange).not.toHaveBeenCalled();
    expect(harness.confirmAction).toHaveBeenCalledTimes(1);
  },
);

it('gives a durable complete deletion receipt precedence over a stale current operation', async () => {
  const harness = createHarness({
    currentOperation: unknownDeletion(operationId('stale-delete'), true),
    deletionReceipt: completeDeletionReceipt(operationId('durable-delete')),
  });
  await renderPrivacy(harness);

  expect(
    await screen.findByText('A deletion request from this device is complete'),
  ).toBeTruthy();
  for (const testID of actionGroupTestIDs) {
    expect(screen.queryByTestId(testID)).toBeNull();
  }
  expect(screen.queryByTestId('live-privacy-pending-deletion-wipe')).toBeNull();
  expect(screen.queryByTestId('live-privacy-retry-deletion-google')).toBeNull();
  expect(screen.queryByTestId('live-privacy-check-deletion')).toBeNull();
  expect(screen.queryByTestId('live-privacy-resume-operation')).toBeNull();
});

it('offers same-account deletion retry only after all four projections share a revision', async () => {
  const retryReceipt = unknownDeletion(operationId('retry-delete'), true);
  const harness = createHarness({
    deletionReceipt: retryReceipt,
    inventoryRevision: revision('2'),
  });
  const { onLifecycleStateChange } = await renderPrivacy(harness);
  expect(
    await screen.findByTestId('live-privacy-deletion-status'),
  ).toBeTruthy();
  expect(screen.queryByTestId('live-privacy-retry-deletion-google')).toBeNull();

  harness.state.inventoryRevision = undefined;
  await act(async () => {
    harness.emit({ areas: ['privacy'], revision: revision('1') });
    await Promise.resolve();
  });
  const retry = await screen.findByTestId('live-privacy-retry-deletion-google');
  harness.continueWithGoogle.mockImplementation(async () => {
    harness.state.revision = revision('3');
    return ok(harness.state.account, harness.state.revision);
  });

  await fireEvent.press(retry);

  expect(
    await screen.findByText(
      'The same-account recovery was checked. Refresh the deletion receipt for authoritative status.',
    ),
  ).toBeTruthy();
  expect(harness.continueWithGoogle).toHaveBeenCalledTimes(1);
  expect(onLifecycleStateChange).toHaveBeenCalledTimes(1);
});

it('coalesces a double confirmation into one native request', async () => {
  const harness = createHarness();
  const response = completedOperation('clear-gemini-templates');
  const pending = deferred<Awaited<ReturnType<LiveAppPort['confirmAction']>>>();
  harness.confirmAction.mockReturnValue(pending.promise);
  await renderPrivacy(harness);
  await prepare('live-privacy-clear-gemini-templates');

  const confirm = pressHandler(screen.getByTestId('live-privacy-confirm'));
  let requests!: readonly [unknown, unknown];
  await act(() => {
    requests = [confirm(), confirm()];
  });
  expect(harness.confirmAction).toHaveBeenCalledTimes(1);

  await act(async () => {
    harness.state.revision = revision('3');
    harness.state.currentOperation = response;
    pending.resolve(ok(response, harness.state.revision));
    await Promise.all(requests);
  });
  expect(harness.confirmAction).toHaveBeenCalledTimes(1);
});
