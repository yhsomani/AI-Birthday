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
import type {
  AccountProjection,
  AndroidSenderProjection,
} from '../../domain/account/model';
import type { SenderTransferOperationProjection } from '../../domain/device/model';
import type {
  IssueId,
  NativeRevision,
  PrivateEmail,
  SafeSupportCode,
  SenderTransferOperationId,
  SenderTransferReviewHandle,
} from '../../domain/shared/brand';
import type {
  NativeResult,
  ProjectionEnvelope,
} from '../../domain/shared/result';
import type { UtcInstant } from '../../domain/shared/temporal';
import { LocalizationProvider } from '../../localization/LocalizationProvider';
import { appI18n } from '../../localization/i18n';
import { LiveAndroidDeviceControls } from './LiveAndroidDeviceControls';
import type { LiveAppPort } from './LiveAppPort';

jest.mock('react-native-localize', () => ({
  getLocales: () => [{ languageCode: 'en' }],
}));

const originalPlatform = Platform.OS;
const generatedAt = '2026-07-19T07:00:00Z' as UtcInstant;
const revision = (value: string) => value as NativeRevision;
const operationId = `transfer_${'b'.repeat(32)}` as SenderTransferOperationId;

const ok = <Value,>(
  value: Value,
  currentRevision = revision('11'),
): NativeResult<Value> => ({
  kind: 'ok',
  envelope: {
    contractVersion: 1,
    generatedAt,
    revision: currentRevision,
    value,
  },
});

const androidSender = (
  kind: Exclude<AndroidSenderProjection['kind'], 'standby'>,
): AndroidSenderProjection =>
  kind === 'transfer-pending' || kind === 'deleting'
    ? {
        platform: 'android',
        kind,
        preissuedPermitMayFinish: false,
      }
    : { platform: 'android', kind, epochLabel: 'This phone' };

const accountEnvelope = (
  sender: AndroidSenderProjection,
  currentRevision = revision('11'),
): ProjectionEnvelope<AccountProjection> => ({
  contractVersion: 1,
  generatedAt,
  revision: currentRevision,
  value: {
    kind: 'connected' as const,
    displayEmail: 'user@example.test' as PrivateEmail,
    sender,
  },
});

const standbyAccount = accountEnvelope({
  platform: 'android',
  kind: 'standby',
  activeOtherDeviceLabel: 'Pixel 8',
});

const cleanupPendingAccount: ProjectionEnvelope<AccountProjection> = {
  contractVersion: 1,
  generatedAt,
  revision: revision('11'),
  value: {
    kind: 'cleanup-pending',
    operation: 'delete',
    issue: {
      id: 'account-cleanup-pending' as IssueId,
      code: 'firebase-account-deleting',
      severity: 'blocking',
      blocks: ['activation'],
    },
  },
};

const activeOperation = (
  kind: 'verifying' | 'remote-pending' | 'remote-draining' | 'failed',
): SenderTransferOperationProjection => {
  const base = {
    id: operationId,
    preissuedPermitMayFinish: false,
    updatedAt: generatedAt,
  } as const;
  if (kind === 'verifying') return { ...base, kind };
  if (kind === 'remote-draining') {
    return {
      ...base,
      kind,
      drainUntil: generatedAt,
      preissuedPermitMayFinish: true,
      reason: 'transfer-pending',
    };
  }
  return {
    ...base,
    kind,
    reason: 'transfer-pending',
    preissuedPermitMayFinish: false,
  };
};

const deferred = <Value,>() => {
  let resolve!: (value: Value) => void;
  const promise = new Promise<Value>(resolver => {
    resolve = resolver;
  });
  return { promise, resolve };
};

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

type DeviceHarness = Readonly<{
  beginSenderTransfer: jest.Mock;
  completeSenderTransfer: jest.Mock;
  emit(event: ProjectionInvalidation): void;
  getSenderTransferOperation: jest.Mock;
  openNotificationSettings: jest.Mock;
  port: LiveAppPort;
  prepareSenderTransfer: jest.Mock;
  requestNotificationPermission: jest.Mock;
  resumeSenderTransfer: jest.Mock;
}>;

const createDeviceHarness = ({
  beginSenderTransfer = jest.fn(async () => ok(activeOperation('verifying'))),
  completeSenderTransfer = jest.fn(async () =>
    ok({
      kind: 'complete' as const,
      id: operationId,
      preissuedPermitMayFinish: false as const,
      completedAt: generatedAt,
      requiresTest: true as const,
    }),
  ),
  getSenderTransferOperation = jest.fn(async () =>
    ok({ kind: 'none' as const }),
  ),
  getNotificationPermission = jest.fn(async () =>
    ok({ kind: 'granted' as const }),
  ),
  openNotificationSettings = jest.fn(async () =>
    ok({ kind: 'opened' as const }),
  ),
  prepareSenderTransfer = jest.fn(async () =>
    ok(
      {
        kind: 'sender-transfer' as const,
        handle: `st_${'a'.repeat(32)}` as SenderTransferReviewHandle,
        preissuedPermitMayFinish: true,
        completionRequiresRecentGoogleAuthentication: true as const,
        consequenceKeys: [
          'transfer.consequence.old-phone-revoked',
          'transfer.consequence.new-phone-test-only',
          'transfer.consequence.test-required',
        ] as const,
      },
      revision('15'),
    ),
  ),
  resumeSenderTransfer = jest.fn(async () => ok(activeOperation('verifying'))),
  requestNotificationPermission = jest.fn(async () =>
    ok({ kind: 'granted' as const }),
  ),
}: Partial<{
  beginSenderTransfer: jest.Mock;
  completeSenderTransfer: jest.Mock;
  getNotificationPermission: jest.Mock;
  getSenderTransferOperation: jest.Mock;
  openNotificationSettings: jest.Mock;
  prepareSenderTransfer: jest.Mock;
  requestNotificationPermission: jest.Mock;
  resumeSenderTransfer: jest.Mock;
}> = {}): DeviceHarness => {
  const listeners = new Set<(event: ProjectionInvalidation) => void>();
  const port = {
    beginSenderTransfer,
    completeSenderTransfer,
    getNotificationPermission,
    getSenderTransferOperation,
    openNotificationSettings,
    prepareSenderTransfer,
    requestNotificationPermission,
    resumeSenderTransfer,
    subscribeInvalidations: (
      listener: (event: ProjectionInvalidation) => void,
    ) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
  } as unknown as LiveAppPort;
  return {
    beginSenderTransfer,
    completeSenderTransfer,
    emit: event => listeners.forEach(listener => listener(event)),
    getSenderTransferOperation,
    openNotificationSettings,
    port,
    prepareSenderTransfer,
    requestNotificationPermission,
    resumeSenderTransfer,
  };
};

const renderControls = async (
  harness: DeviceHarness,
  account: ProjectionEnvelope<AccountProjection> | undefined,
  onOpenAutomation = jest.fn(),
  showNotifications = false,
  accountProjectionStable = true,
) => {
  const onAccountReload = jest.fn(async () => undefined);
  const view = await render(
    <LocalizationProvider>
      <ThemeProvider initialPreference="light">
        <LiveAndroidDeviceControls
          account={account}
          accountProjectionStable={accountProjectionStable}
          onAccountReload={onAccountReload}
          onOpenAutomation={onOpenAutomation}
          port={harness.port}
          showNotifications={showNotifications}
        />
      </ThemeProvider>
    </LocalizationProvider>,
  );
  return { ...view, onAccountReload, onOpenAutomation };
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

it.each(['automation-active', 'test-only', 'paused-repair'] as const)(
  'hides an idle transfer for an Android %s sender',
  async kind => {
    const harness = createDeviceHarness();
    await renderControls(harness, accountEnvelope(androidSender(kind)));

    await waitFor(() =>
      expect(harness.getSenderTransferOperation).toHaveBeenCalledTimes(1),
    );
    expect(screen.queryByTestId('live-prepare-sender-transfer')).toBeNull();
    expect(screen.queryByTestId('live-continue-sender-transfer')).toBeNull();
    expect(screen.queryByTestId('live-transfer-open-automation')).toBeNull();
  },
);

it.each([
  ['deleting sender', 'verifying', accountEnvelope(androidSender('deleting'))],
  [
    'deleting sender',
    'remote-pending',
    accountEnvelope(androidSender('deleting')),
  ],
  [
    'deleting sender',
    'remote-draining',
    accountEnvelope(androidSender('deleting')),
  ],
  ['account cleanup', 'verifying', cleanupPendingAccount],
  ['account cleanup', 'remote-pending', cleanupPendingAccount],
  ['account cleanup', 'remote-draining', cleanupPendingAccount],
] as const)(
  'offers only transfer status recovery during %s with a %s operation',
  async (_lifecycle, operationKind, lifecycleAccount) => {
    const harness = createDeviceHarness({
      getNotificationPermission: jest.fn(async () =>
        ok({ kind: 'not-requested' as const }),
      ),
      getSenderTransferOperation: jest.fn(async () =>
        ok(activeOperation(operationKind)),
      ),
    });
    const onOpenAutomation = jest.fn();
    const { onAccountReload } = await renderControls(
      harness,
      lifecycleAccount,
      onOpenAutomation,
      true,
    );
    await waitFor(() =>
      expect(harness.getSenderTransferOperation).toHaveBeenCalledTimes(1),
    );

    expect(screen.getAllByTestId('live-check-sender-transfer')).toHaveLength(1);
    for (const id of [
      'live-prepare-sender-transfer',
      'live-confirm-sender-transfer',
      'live-continue-sender-transfer',
      'live-transfer-open-automation',
      'live-request-notification-permission',
      'live-open-notification-settings',
    ]) {
      expect(screen.queryByTestId(id)).toBeNull();
    }

    await fireEvent.press(screen.getByTestId('live-check-sender-transfer'));
    await waitFor(() =>
      expect(harness.getSenderTransferOperation).toHaveBeenCalledTimes(2),
    );
    expect(onAccountReload).toHaveBeenCalledTimes(1);
    expect(harness.prepareSenderTransfer).not.toHaveBeenCalled();
    expect(harness.beginSenderTransfer).not.toHaveBeenCalled();
    expect(harness.resumeSenderTransfer).not.toHaveBeenCalled();
    expect(harness.completeSenderTransfer).not.toHaveBeenCalled();
    expect(harness.requestNotificationPermission).not.toHaveBeenCalled();
    expect(harness.openNotificationSettings).not.toHaveBeenCalled();
    expect(onOpenAutomation).not.toHaveBeenCalled();
  },
);

it.each(['verifying', 'remote-pending'] as const)(
  'lets a STANDBY phone Continue a %s operation through resume',
  async operationKind => {
    const operation = activeOperation(operationKind);
    const harness = createDeviceHarness({
      getSenderTransferOperation: jest.fn(async () => ok(operation)),
    });
    await renderControls(harness, standbyAccount);

    expect(
      await screen.findByTestId('live-continue-sender-transfer'),
    ).toBeTruthy();
    expect(screen.queryByTestId('live-check-sender-transfer')).toBeNull();
    await fireEvent.press(screen.getByTestId('live-continue-sender-transfer'));

    await waitFor(() =>
      expect(harness.resumeSenderTransfer).toHaveBeenCalledTimes(1),
    );
    expect(harness.resumeSenderTransfer).toHaveBeenCalledWith({ operationId });
    expect(harness.completeSenderTransfer).not.toHaveBeenCalled();
    expect(harness.prepareSenderTransfer).not.toHaveBeenCalled();
  },
);

it('keeps a STANDBY remote-draining operation status-check only', async () => {
  const getSenderTransferOperation = jest.fn(async () =>
    ok(activeOperation('remote-draining')),
  );
  const harness = createDeviceHarness({ getSenderTransferOperation });
  const { onAccountReload } = await renderControls(harness, standbyAccount);

  expect(await screen.findByTestId('live-check-sender-transfer')).toBeTruthy();
  expect(screen.queryByTestId('live-continue-sender-transfer')).toBeNull();
  expect(screen.queryByTestId('live-prepare-sender-transfer')).toBeNull();
  await fireEvent.press(screen.getByTestId('live-check-sender-transfer'));

  await waitFor(() =>
    expect(getSenderTransferOperation).toHaveBeenCalledTimes(2),
  );
  expect(onAccountReload).toHaveBeenCalledTimes(1);
  expect(harness.resumeSenderTransfer).not.toHaveBeenCalled();
  expect(harness.completeSenderTransfer).not.toHaveBeenCalled();
});

it('binds transfer prepare and confirmation to the displayed revisions and clears review on invalidation', async () => {
  const harness = createDeviceHarness();
  await renderControls(harness, standbyAccount);

  await fireEvent.press(
    await screen.findByTestId('live-prepare-sender-transfer'),
  );
  expect(harness.prepareSenderTransfer).toHaveBeenCalledWith({
    expectedRevision: '11',
  });
  expect(
    await screen.findByTestId('live-confirm-sender-transfer'),
  ).toBeTruthy();

  await act(async () => {
    harness.emit({ revision: revision('16'), areas: ['account'] });
  });
  await waitFor(() =>
    expect(screen.queryByTestId('live-confirm-sender-transfer')).toBeNull(),
  );
  expect(harness.beginSenderTransfer).not.toHaveBeenCalled();

  await fireEvent.press(screen.getByTestId('live-prepare-sender-transfer'));
  await fireEvent.press(
    await screen.findByTestId('live-confirm-sender-transfer'),
  );
  await waitFor(() =>
    expect(harness.beginSenderTransfer).toHaveBeenCalledTimes(1),
  );
  expect(harness.beginSenderTransfer).toHaveBeenCalledWith({
    handle: `st_${'a'.repeat(32)}`,
    expectedRevision: '15',
  });
});

it('hides a transfer confirmation when refreshed operation truth supersedes its review', async () => {
  const getSenderTransferOperation = jest
    .fn()
    .mockResolvedValueOnce(ok({ kind: 'none' as const }))
    .mockResolvedValueOnce(ok(activeOperation('verifying'), revision('16')));
  const harness = createDeviceHarness({ getSenderTransferOperation });
  await renderControls(harness, standbyAccount);

  await fireEvent.press(
    await screen.findByTestId('live-prepare-sender-transfer'),
  );
  expect(
    await screen.findByTestId('live-confirm-sender-transfer'),
  ).toBeTruthy();

  await act(async () => {
    harness.emit({ revision: revision('16'), areas: ['privacy'] });
  });
  await waitFor(() =>
    expect(getSenderTransferOperation).toHaveBeenCalledTimes(2),
  );

  expect(screen.queryByTestId('live-confirm-sender-transfer')).toBeNull();
  expect(harness.beginSenderTransfer).not.toHaveBeenCalled();
  expect(screen.queryByTestId('live-continue-sender-transfer')).toBeNull();
  expect(screen.getAllByTestId('live-check-sender-transfer')).toHaveLength(1);
});

it('fails closed when an idle transfer projection is from a different account revision', async () => {
  const harness = createDeviceHarness({
    getSenderTransferOperation: jest.fn(async () =>
      ok({ kind: 'none' as const }, revision('12')),
    ),
  });
  await renderControls(harness, standbyAccount);

  expect(await screen.findByTestId('live-check-sender-transfer')).toBeTruthy();
  expect(screen.getAllByTestId('live-check-sender-transfer')).toHaveLength(1);
  expect(screen.queryByTestId('live-prepare-sender-transfer')).toBeNull();
  expect(screen.queryByTestId('live-confirm-sender-transfer')).toBeNull();
  expect(harness.prepareSenderTransfer).not.toHaveBeenCalled();
  expect(harness.beginSenderTransfer).not.toHaveBeenCalled();
});

it('suppresses transfer preparation while the operation projection is refreshing', async () => {
  const nextOperation =
    deferred<NativeResult<SenderTransferOperationProjection>>();
  const getSenderTransferOperation = jest
    .fn()
    .mockResolvedValueOnce(ok({ kind: 'none' as const }))
    .mockImplementationOnce(() => nextOperation.promise);
  const prepareSenderTransfer = jest.fn(async () =>
    ok({
      kind: 'sender-transfer' as const,
      handle: `st_${'c'.repeat(32)}` as SenderTransferReviewHandle,
      preissuedPermitMayFinish: false,
      completionRequiresRecentGoogleAuthentication: true as const,
      consequenceKeys: [
        'transfer.consequence.old-phone-revoked',
        'transfer.consequence.new-phone-test-only',
        'transfer.consequence.test-required',
      ] as const,
    }),
  );
  const harness = createDeviceHarness({
    getSenderTransferOperation,
    prepareSenderTransfer,
  });
  await renderControls(harness, standbyAccount);
  await screen.findByTestId('live-prepare-sender-transfer');

  await act(async () => {
    harness.emit({ revision: revision('12'), areas: ['privacy'] });
    await Promise.resolve();
  });
  await waitFor(() =>
    expect(
      screen.queryByTestId('live-prepare-sender-transfer')?.props
        .accessibilityState?.disabled ?? true,
    ).toBe(true),
  );
  if (screen.queryByTestId('live-prepare-sender-transfer')) {
    await fireEvent.press(screen.getByTestId('live-prepare-sender-transfer'));
  }
  expect(prepareSenderTransfer).not.toHaveBeenCalled();

  await act(async () => {
    nextOperation.resolve(ok({ kind: 'none' as const }, revision('12')));
    await nextOperation.promise;
  });
});

it.each([
  ['prepare', standbyAccount, { kind: 'none' as const }],
  [
    'continue',
    accountEnvelope(androidSender('transfer-pending')),
    activeOperation('verifying'),
  ],
] as const)(
  'offers only status recovery instead of transfer %s while the account projection is unstable',
  async (action, transferAccount, operation) => {
    const harness = createDeviceHarness({
      getSenderTransferOperation: jest.fn(async () => ok(operation)),
    });
    const { onAccountReload } = await renderControls(
      harness,
      transferAccount,
      jest.fn(),
      false,
      false,
    );
    const testID =
      action === 'prepare'
        ? 'live-prepare-sender-transfer'
        : 'live-continue-sender-transfer';
    await waitFor(() =>
      expect(harness.getSenderTransferOperation).toHaveBeenCalledTimes(1),
    );

    expect(screen.queryByTestId(testID)).toBeNull();
    expect(screen.queryByTestId('live-confirm-sender-transfer')).toBeNull();
    expect(screen.getAllByTestId('live-check-sender-transfer')).toHaveLength(1);
    await fireEvent.press(screen.getByTestId('live-check-sender-transfer'));
    await waitFor(() =>
      expect(harness.getSenderTransferOperation).toHaveBeenCalledTimes(2),
    );
    expect(onAccountReload).toHaveBeenCalledTimes(1);
    expect(harness.prepareSenderTransfer).not.toHaveBeenCalled();
    expect(harness.resumeSenderTransfer).not.toHaveBeenCalled();
    expect(harness.completeSenderTransfer).not.toHaveBeenCalled();
  },
);

it.each([
  ['verifying', 'resume'],
  ['remote-pending', 'resume'],
  ['remote-draining', 'complete'],
] as const)(
  'routes a TRANSFER_PENDING %s operation through the protected %s call',
  async (kind, expectedCall) => {
    const operation = activeOperation(kind);
    const harness = createDeviceHarness({
      getSenderTransferOperation: jest.fn(async () => ok(operation)),
    });
    await renderControls(
      harness,
      accountEnvelope(androidSender('transfer-pending')),
    );

    expect(screen.queryByTestId('live-check-sender-transfer')).toBeNull();
    await fireEvent.press(
      await screen.findByTestId('live-continue-sender-transfer'),
    );
    const selected =
      expectedCall === 'complete'
        ? harness.completeSenderTransfer
        : harness.resumeSenderTransfer;
    const rejected =
      expectedCall === 'complete'
        ? harness.resumeSenderTransfer
        : harness.completeSenderTransfer;
    await waitFor(() => expect(selected).toHaveBeenCalledTimes(1));
    expect(selected).toHaveBeenCalledWith({ operationId });
    expect(rejected).not.toHaveBeenCalled();
  },
);

it('keeps failed and complete transfer truth visible without treating completion as activation', async () => {
  const failedHarness = createDeviceHarness({
    getSenderTransferOperation: jest.fn(async () =>
      ok(activeOperation('failed')),
    ),
  });
  await renderControls(
    failedHarness,
    accountEnvelope(androidSender('transfer-pending')),
  );
  expect(
    await screen.findByText('Sender transfer needs attention'),
  ).toBeTruthy();
  expect(screen.queryByTestId('live-continue-sender-transfer')).toBeNull();
  await cleanup();

  const onOpenAutomation = jest.fn();
  const completeHarness = createDeviceHarness({
    getSenderTransferOperation: jest.fn(async () =>
      ok({
        kind: 'complete' as const,
        id: operationId,
        preissuedPermitMayFinish: false as const,
        completedAt: generatedAt,
        requiresTest: true as const,
      }),
    ),
  });
  await renderControls(
    completeHarness,
    accountEnvelope(androidSender('test-only')),
    onOpenAutomation,
  );

  expect(await screen.findByText('A real SMS test is required')).toBeTruthy();
  expect(screen.queryByText('Automation is on')).toBeNull();
  await fireEvent.press(screen.getByTestId('live-transfer-open-automation'));
  expect(onOpenAutomation).toHaveBeenCalledTimes(1);
});

it('uses status recovery instead of retry when a failed operation conflicts with a transfer-pending account', async () => {
  const harness = createDeviceHarness({
    getSenderTransferOperation: jest.fn(async () =>
      ok(activeOperation('failed')),
    ),
  });
  await renderControls(
    harness,
    accountEnvelope(androidSender('transfer-pending')),
  );

  expect(await screen.findByTestId('live-check-sender-transfer')).toBeTruthy();
  expect(screen.getAllByTestId('live-check-sender-transfer')).toHaveLength(1);
  expect(screen.queryByTestId('live-retry-sender-transfer')).toBeNull();
  expect(screen.queryByTestId('live-continue-sender-transfer')).toBeNull();
  expect(harness.prepareSenderTransfer).not.toHaveBeenCalled();
  expect(harness.resumeSenderTransfer).not.toHaveBeenCalled();
});

it('expires a prepared transfer review before the native review handle does', async () => {
  jest.useFakeTimers();
  jest.setSystemTime(new Date('2026-07-19T07:00:00Z'));
  try {
    const harness = createDeviceHarness();
    await renderControls(harness, standbyAccount);

    await fireEvent.press(
      await screen.findByTestId('live-prepare-sender-transfer'),
    );
    expect(
      await screen.findByTestId('live-confirm-sender-transfer'),
    ).toBeTruthy();

    await act(async () => {
      jest.advanceTimersByTime(9 * 60 * 1000 + 30 * 1000);
    });

    expect(screen.queryByTestId('live-confirm-sender-transfer')).toBeNull();
    expect(harness.beginSenderTransfer).not.toHaveBeenCalled();
  } finally {
    await cleanup();
    jest.useRealTimers();
  }
});

it.each(['invalidation', 'AppState'] as const)(
  'does not resurrect a deferred transfer review after %s invalidates it',
  async invalidationSource => {
    const appStateListeners = captureAppStateListeners();
    let invalidateReviewRequest = () => undefined;
    const prepareSenderTransfer = jest.fn(async () => {
      await Promise.resolve();
      invalidateReviewRequest();
      await Promise.resolve();
      return ok(
        {
          kind: 'sender-transfer' as const,
          handle: `st_${'d'.repeat(32)}` as SenderTransferReviewHandle,
          preissuedPermitMayFinish: false,
          completionRequiresRecentGoogleAuthentication: true as const,
          consequenceKeys: [
            'transfer.consequence.old-phone-revoked',
            'transfer.consequence.new-phone-test-only',
            'transfer.consequence.test-required',
          ] as const,
        },
        revision('15'),
      );
    });
    const harness = createDeviceHarness({ prepareSenderTransfer });
    invalidateReviewRequest =
      invalidationSource === 'invalidation'
        ? () => {
            harness.emit({ revision: revision('12'), areas: ['account'] });
          }
        : () => {
            appStateListeners.forEach(listener => listener('active'));
          };
    await renderControls(harness, standbyAccount);

    await fireEvent.press(
      await screen.findByTestId('live-prepare-sender-transfer'),
    );
    expect(prepareSenderTransfer).toHaveBeenCalledTimes(1);

    expect(screen.queryByTestId('live-confirm-sender-transfer')).toBeNull();
    expect(harness.beginSenderTransfer).not.toHaveBeenCalled();
  },
);

it('shows coordination unavailability only for a sender role where transfer applies', async () => {
  const getSenderTransferOperation = jest.fn(async () =>
    ok({
      kind: 'unavailable' as const,
      reason: 'coordination-unavailable' as const,
    }),
  );
  const hiddenHarness = createDeviceHarness({ getSenderTransferOperation });
  await renderControls(
    hiddenHarness,
    accountEnvelope(androidSender('automation-active')),
  );
  await waitFor(() => expect(getSenderTransferOperation).toHaveBeenCalled());
  expect(screen.queryByText('Sender safety state cannot be read')).toBeNull();
  await cleanup();

  const visibleHarness = createDeviceHarness({ getSenderTransferOperation });
  await renderControls(visibleHarness, standbyAccount);
  expect(
    await screen.findByText('Sender safety state cannot be read'),
  ).toBeTruthy();
  expect(screen.queryByTestId('live-prepare-sender-transfer')).toBeNull();
});

it('prioritizes an applicable transfer action over a notification permission action', async () => {
  const harness = createDeviceHarness({
    getNotificationPermission: jest.fn(async () =>
      ok({ kind: 'not-requested' as const }),
    ),
    getSenderTransferOperation: jest.fn(async () =>
      ok(activeOperation('remote-pending')),
    ),
  });
  await renderControls(
    harness,
    accountEnvelope(androidSender('transfer-pending')),
    jest.fn(),
    true,
  );

  expect(
    await screen.findByTestId('live-continue-sender-transfer'),
  ).toBeTruthy();
  expect(
    screen.queryByText('Alert permission has not been asked for'),
  ).toBeNull();
  expect(
    screen.queryByTestId('live-request-notification-permission'),
  ).toBeNull();
});

it('hides the routine notification section when permission is already granted', async () => {
  const harness = createDeviceHarness();
  await renderControls(
    harness,
    accountEnvelope(androidSender('automation-active')),
    jest.fn(),
    true,
  );

  await waitFor(() =>
    expect(harness.getSenderTransferOperation).toHaveBeenCalledTimes(1),
  );
  await waitFor(() => expect(screen.queryByText('Safety alerts')).toBeNull());
  expect(screen.queryByText('Alerts are allowed')).toBeNull();
  expect(
    screen.queryByTestId('live-request-notification-permission'),
  ).toBeNull();
  expect(screen.queryByTestId('live-open-notification-settings')).toBeNull();
});

it('keeps the notification section visible when permission needs action', async () => {
  const harness = createDeviceHarness({
    getNotificationPermission: jest.fn(async () =>
      ok({ kind: 'not-requested' as const }),
    ),
  });
  await renderControls(
    harness,
    accountEnvelope(androidSender('automation-active')),
    jest.fn(),
    true,
  );

  expect(await screen.findByText('Safety alerts')).toBeTruthy();
  expect(
    await screen.findByTestId('live-request-notification-permission'),
  ).toBeTruthy();
});

it('keeps raw transfer reasons behind an accessible support-details control', async () => {
  const harness = createDeviceHarness({
    getSenderTransferOperation: jest.fn(async () =>
      ok(activeOperation('remote-pending')),
    ),
  });
  await renderControls(
    harness,
    accountEnvelope(androidSender('transfer-pending')),
  );

  const toggle = await screen.findByTestId('live-transfer-support-toggle');
  expect(toggle.props.accessibilityRole).toBe('button');
  expect(screen.queryByText('Technical code: transfer-pending')).toBeNull();
  await fireEvent.press(toggle);
  expect(screen.getByTestId('live-transfer-support-details')).toBeTruthy();
  expect(screen.getByText('Technical code: transfer-pending')).toBeTruthy();
});

it('shows a retryable transfer error only for an applicable sender role', async () => {
  const getSenderTransferOperation = jest.fn(async () => ({
    kind: 'error' as const,
    problem: {
      kind: 'internal' as const,
      supportCode: 'TRANSFER_TEST_INTERNAL' as SafeSupportCode,
    },
  }));
  const hidden = createDeviceHarness({ getSenderTransferOperation });
  await renderControls(
    hidden,
    accountEnvelope(androidSender('automation-active')),
  );
  await waitFor(() => expect(getSenderTransferOperation).toHaveBeenCalled());
  expect(screen.queryByTestId('live-retry-sender-transfer')).toBeNull();
  await cleanup();

  const visible = createDeviceHarness({ getSenderTransferOperation });
  await renderControls(visible, standbyAccount);
  expect(await screen.findByTestId('live-retry-sender-transfer')).toBeTruthy();
});

it.each([
  [
    'not-requested',
    'live-request-notification-permission',
    'requestNotificationPermission',
  ],
  [
    'settings-required',
    'live-open-notification-settings',
    'openNotificationSettings',
  ],
] as const)(
  'suppresses the %s notification action during refresh and after refresh failure',
  async (permissionKind, actionTestId, actionName) => {
    const appStateListeners = captureAppStateListeners();
    const nextPermission = deferred<NativeResult<never>>();
    const getNotificationPermission = jest
      .fn()
      .mockResolvedValueOnce(ok({ kind: permissionKind }))
      .mockImplementationOnce(() => nextPermission.promise);
    const requestNotificationPermission = jest.fn(async () =>
      ok({ kind: 'granted' as const }),
    );
    const openNotificationSettings = jest.fn(async () =>
      ok({ kind: 'opened' as const }),
    );
    const harness = createDeviceHarness({
      getNotificationPermission,
      openNotificationSettings,
      requestNotificationPermission,
    });
    await renderControls(
      harness,
      accountEnvelope(androidSender('automation-active')),
      jest.fn(),
      true,
    );
    await screen.findByTestId(actionTestId);

    await act(async () => {
      appStateListeners.forEach(listener => listener('active'));
      await Promise.resolve();
    });
    await waitFor(() =>
      expect(getNotificationPermission).toHaveBeenCalledTimes(2),
    );
    const suppressedWhileRefreshing =
      screen.queryByTestId(actionTestId)?.props.accessibilityState?.disabled ??
      true;
    if (screen.queryByTestId(actionTestId) && suppressedWhileRefreshing) {
      await fireEvent.press(screen.getByTestId(actionTestId));
    }
    const callsWhileRefreshing = harness[actionName].mock.calls.length;

    await act(async () => {
      nextPermission.resolve({
        kind: 'error',
        problem: {
          kind: 'internal',
          supportCode: 'NOTIFICATION_REFRESH_FAILED' as SafeSupportCode,
        },
      });
      await nextPermission.promise;
    });
    expect(suppressedWhileRefreshing).toBe(true);
    expect(callsWhileRefreshing).toBe(0);
    const suppressedAfterFailure =
      screen.queryByTestId(actionTestId)?.props.accessibilityState?.disabled ??
      true;
    expect(suppressedAfterFailure).toBe(true);
    if (screen.queryByTestId(actionTestId) && suppressedAfterFailure) {
      await fireEvent.press(screen.getByTestId(actionTestId));
    }
    expect(harness[actionName]).not.toHaveBeenCalled();
  },
);

it('reloads the notification permission projection on a notifications invalidation', async () => {
  const getNotificationPermission = jest.fn(async () =>
    ok({ kind: 'granted' as const }),
  );
  const harness = createDeviceHarness({ getNotificationPermission });
  await renderControls(
    harness,
    accountEnvelope(androidSender('automation-active')),
    jest.fn(),
    true,
  );
  await waitFor(() =>
    expect(getNotificationPermission).toHaveBeenCalledTimes(1),
  );

  await act(async () => {
    harness.emit({ revision: revision('12'), areas: ['notifications'] });
    await Promise.resolve();
  });
  await waitFor(() =>
    expect(getNotificationPermission).toHaveBeenCalledTimes(2),
  );
});

it('offers an explicit status check when account transfer is pending but no operation is reported', async () => {
  const getSenderTransferOperation = jest
    .fn()
    .mockResolvedValueOnce(ok({ kind: 'none' as const }))
    .mockResolvedValueOnce(ok(activeOperation('verifying'), revision('12')));
  const harness = createDeviceHarness({ getSenderTransferOperation });
  await renderControls(
    harness,
    accountEnvelope(androidSender('transfer-pending')),
  );

  await fireEvent.press(
    await screen.findByTestId('live-check-sender-transfer'),
  );
  await waitFor(() =>
    expect(getSenderTransferOperation).toHaveBeenCalledTimes(2),
  );
  expect(harness.prepareSenderTransfer).not.toHaveBeenCalled();
  expect(screen.queryByTestId('live-continue-sender-transfer')).toBeNull();
  expect(screen.getAllByTestId('live-check-sender-transfer')).toHaveLength(1);
});
