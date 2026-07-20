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
import type { HomeProjection } from '../../domain/home/model';
import type { IosComposerProposalProjection } from '../../domain/messages/model';
import type { ReadinessProjection } from '../../domain/readiness/model';
import type {
  ComposerProposalId,
  IssueId,
  NativeRevision,
  OccurrenceId,
  PrivateDisplayName,
  SafeSupportCode,
} from '../../domain/shared/brand';
import type { PlatformCapability } from '../../domain/shared/platform';
import type { SafeReasonCode } from '../../domain/shared/reasonCodes';
import type { NativeResult } from '../../domain/shared/result';
import type { LocalDate, UtcInstant } from '../../domain/shared/temporal';
import type {
  CompanionComposerOutcome,
  CompanionComposerReviewProjection,
} from '../../infrastructure/native/ios/CompanionNativeGateway';
import { LocalizationProvider } from '../../localization/LocalizationProvider';
import { appI18n } from '../../localization/i18n';
import type { LiveAppPort, LiveCompanionPort } from './LiveAppPort';
import { LiveComposerReviewScreen } from './LiveComposerReviewScreen';

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
const generatedAt = '2026-07-18T07:00:00Z' as UtcInstant;
const now = 1_752_822_800_000;
const revision = (value: string) => value as NativeRevision;
const proposalId = (value: string) => value as ComposerProposalId;

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

const internalError = <Value,>(): NativeResult<Value> => ({
  kind: 'error',
  problem: {
    kind: 'internal',
    supportCode: 'COMPOSER_TEST_INTERNAL' as SafeSupportCode,
  },
});

const iosCapability: PlatformCapability = {
  platform: 'ios',
  deliveryMode: 'user-controlled-composer',
  unattendedSms: 'unavailable',
  userComposer: 'required',
};

const androidCapability: PlatformCapability = {
  platform: 'android',
  deliveryMode: 'unattended-device-sms',
  minimumApiLevel: 29,
  unattendedSms: 'release-gated',
  userComposer: 'available-as-explicit-alternative',
};

const iosReadiness: ReadinessProjection = {
  platform: 'ios',
  composer: { kind: 'allowed' },
  unattendedAutomation: {
    kind: 'unavailable',
    reason: 'platform-composer-only',
  },
  lastCheckedAt: generatedAt,
};

const iosHome: HomeProjection = {
  automation: {
    platform: 'ios',
    desired: 'composer-reminders-on',
    effective: 'ready',
    readiness: iosReadiness,
  },
  counts: {
    enabled: 1,
    needsAttention: 0,
    unavailable: 0,
    today: 1,
    nextSevenDays: 1,
  },
  contactsSync: {
    kind: 'fresh',
    completedAt: generatedAt,
    contactCount: 1,
  },
  next: {
    occurrenceId: 'occurrence-1' as OccurrenceId,
    recipient: 'Live Contact' as PrivateDisplayName,
    localDate: '2026-07-18' as LocalDate,
    windowLabel: '09:00–11:00',
    maskedPhone: '•••• 4321',
  },
};

const androidHome: HomeProjection = {
  automation: {
    platform: 'android',
    desired: 'on',
    effective: 'active',
    readiness: {
      platform: 'android',
      test: { kind: 'allowed' },
      activation: { kind: 'allowed' },
      birthday: { kind: 'allowed' },
      lastCheckedAt: generatedAt,
    },
  },
  counts: {
    enabled: 1,
    needsAttention: 0,
    unavailable: 0,
    today: 1,
    nextSevenDays: 1,
  },
  contactsSync: {
    kind: 'fresh',
    completedAt: generatedAt,
    contactCount: 1,
  },
};

const readyProposal = (
  id = 'proposal-1',
  recipient = 'Live Contact',
): IosComposerProposalProjection => ({
  kind: 'ready',
  proposalId: proposalId(id),
  occurrenceId: `occurrence-${id}` as OccurrenceId,
  occurrenceDate: '2026-07-18' as LocalDate,
  recipient: recipient as PrivateDisplayName,
});

const composerReview = (
  overrides: Partial<CompanionComposerReviewProjection> = {},
): CompanionComposerReviewProjection => ({
  actionNonce: 'a'.repeat(43),
  body: 'Happy birthday!',
  expiresAtEpochMilliseconds: now + 60_000,
  maskedDestination: '•••• 4321',
  proposalId: 'proposal-1',
  revision: '2',
  ...overrides,
});

const blockedIosHome = (code: SafeReasonCode): HomeProjection => ({
  ...iosHome,
  automation: {
    platform: 'ios',
    desired: 'composer-reminders-on',
    effective: 'ready',
    readiness: {
      ...iosReadiness,
      composer: {
        kind: 'blocked',
        issues: [
          {
            blocks: ['composer'],
            code,
            id: `issue-${code}` as IssueId,
            severity: 'blocking',
          },
        ],
      },
    },
  },
});

type ComposerHarness = Readonly<{
  companionPort: LiveCompanionPort;
  continueWithGoogle: jest.Mock;
  emit(event: ProjectionInvalidation): void;
  getHome: jest.Mock;
  getNextComposerProposal: jest.Mock;
  openUserConfirmedComposer: jest.Mock;
  port: LiveAppPort;
  prepareComposerReview: jest.Mock;
  syncContacts: jest.Mock;
  canOpenComposer: jest.Mock;
}>;

const createComposerHarness = ({
  canOpenComposer = jest.fn(async () => true),
  continueWithGoogle = jest.fn(async () => ok({ kind: 'connected' as const })),
  getHome = jest.fn(async () => ok(iosHome)),
  getNextComposerProposal = jest.fn(async () => ok(readyProposal())),
  openUserConfirmedComposer = jest.fn(async () => ({
    kind: 'ok' as const,
    value: 'cancelled' as const,
  })),
  prepareComposerReview = jest.fn(async () => ({
    kind: 'ok' as const,
    value: composerReview(),
  })),
  syncContacts = jest.fn(async () =>
    ok({
      kind: 'fresh' as const,
      completedAt: generatedAt,
      contactCount: 1,
    }),
  ),
}: Partial<{
  canOpenComposer: jest.Mock;
  continueWithGoogle: jest.Mock;
  getHome: jest.Mock;
  getNextComposerProposal: jest.Mock;
  openUserConfirmedComposer: jest.Mock;
  prepareComposerReview: jest.Mock;
  syncContacts: jest.Mock;
}> = {}): ComposerHarness => {
  const listeners = new Set<(event: ProjectionInvalidation) => void>();
  const port = {
    continueWithGoogle,
    getHome,
    getNextComposerProposal,
    subscribeInvalidations: (
      listener: (event: ProjectionInvalidation) => void,
    ) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    syncContacts,
  } as unknown as LiveAppPort;
  const companionPort = {
    canOpenComposer,
    getReminderStatus: jest.fn(),
    openNotificationSettings: jest.fn(),
    openUserConfirmedComposer,
    prepareComposerReview,
    requestReminderAuthorization: jest.fn(),
  } as LiveCompanionPort;
  return {
    canOpenComposer,
    companionPort,
    continueWithGoogle,
    emit: event => listeners.forEach(listener => listener(event)),
    getHome,
    getNextComposerProposal,
    openUserConfirmedComposer,
    port,
    prepareComposerReview,
    syncContacts,
  };
};

const renderComposer = async (
  harness: ComposerHarness,
  capability: PlatformCapability = iosCapability,
  onBack = jest.fn(),
) =>
  render(
    <LocalizationProvider>
      <ThemeProvider initialPreference="light">
        <LiveComposerReviewScreen
          capability={capability}
          companionPort={harness.companionPort}
          onBack={onBack}
          port={harness.port}
        />
      </ThemeProvider>
    </LocalizationProvider>,
  );

const prepareVisibleReview = async (harness: ComposerHarness) => {
  await fireEvent.press(await screen.findByTestId('live-prepare-composer'));
  await waitFor(() =>
    expect(screen.getByTestId('live-open-composer')).toBeTruthy(),
  );
  expect(harness.canOpenComposer).not.toHaveBeenCalled();
  expect(harness.openUserConfirmedComposer).not.toHaveBeenCalled();
};

const expandSupportCode = async (code: string) => {
  expect(screen.queryByText(`Technical code: ${code}`)).toBeNull();
  expect(screen.queryByTestId('live-composer-support-details')).toBeNull();
  const toggle = await screen.findByTestId('live-composer-support-toggle');
  expect(toggle.props.accessibilityState).toMatchObject({ expanded: false });

  await fireEvent.press(toggle);

  expect(
    await screen.findByTestId('live-composer-support-details'),
  ).toBeTruthy();
  expect(screen.getByText(`Technical code: ${code}`)).toBeTruthy();
  expect(
    screen.getByText(
      'Technical codes and references can help support diagnose a problem. They do not change the repair status.',
    ),
  ).toBeTruthy();
  expect(
    screen.getByTestId('live-composer-support-toggle').props.accessibilityState,
  ).toMatchObject({ expanded: true });
};

let appStateListeners: Array<(state: AppStateStatus) => void> = [];

beforeEach(() => {
  jest.clearAllMocks();
  appStateListeners = [];
  jest.spyOn(Date, 'now').mockReturnValue(now);
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: 'ios',
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

it('prepares, reviews, checks availability, and opens with only opaque native fields in order', async () => {
  const harness = createComposerHarness({
    getNextComposerProposal: jest
      .fn()
      .mockResolvedValueOnce(ok(readyProposal(), revision('1')))
      .mockResolvedValue(ok({ kind: 'none' as const }, revision('3'))),
    openUserConfirmedComposer: jest.fn(async () => ({
      kind: 'ok' as const,
      value: 'reported-sent' as const,
    })),
  });
  await renderComposer(harness);

  await prepareVisibleReview(harness);
  expect(harness.prepareComposerReview).toHaveBeenCalledWith({
    expectedRevision: '1',
    proposalId: 'proposal-1',
  });
  expect(
    Object.keys(
      (harness.prepareComposerReview.mock.calls[0]?.[0] ?? {}) as object,
    ).sort(),
  ).toEqual(['expectedRevision', 'proposalId']);
  expect(screen.getByText('•••• 4321')).toBeTruthy();
  expect(screen.getByText('Happy birthday!')).toBeTruthy();

  await fireEvent.press(screen.getByTestId('live-open-composer'));

  await waitFor(() =>
    expect(harness.openUserConfirmedComposer).toHaveBeenCalledTimes(1),
  );
  expect(harness.openUserConfirmedComposer).toHaveBeenCalledWith({
    actionNonce: 'a'.repeat(43),
    expectedRevision: '2',
    proposalId: 'proposal-1',
  });
  expect(
    Object.keys(
      (harness.openUserConfirmedComposer.mock.calls[0]?.[0] ?? {}) as object,
    ).sort(),
  ).toEqual(['actionNonce', 'expectedRevision', 'proposalId']);
  const prepareOrder = harness.prepareComposerReview.mock
    .invocationCallOrder[0] as number;
  const availabilityOrder = harness.canOpenComposer.mock
    .invocationCallOrder[0] as number;
  const openOrder = harness.openUserConfirmedComposer.mock
    .invocationCallOrder[0] as number;
  const reloadOrder = harness.getNextComposerProposal.mock
    .invocationCallOrder[1] as number;
  expect(prepareOrder).toBeLessThan(availabilityOrder);
  expect(availabilityOrder).toBeLessThan(openOrder);
  expect(openOrder).toBeLessThan(reloadOrder);
});

it.each([
  {
    label: 'a mismatched proposal',
    review: composerReview({ proposalId: 'proposal-other' }),
    code: 'COMPOSER_REVIEW_MISMATCH',
  },
  {
    label: 'an already expired review',
    review: composerReview({ expiresAtEpochMilliseconds: now }),
    code: 'COMPOSER_REVIEW_MISMATCH',
  },
])(
  'rejects $label returned by native preparation',
  async ({ code, review }) => {
    const harness = createComposerHarness({
      prepareComposerReview: jest.fn(async () => ({
        kind: 'ok' as const,
        value: review,
      })),
    });
    await renderComposer(harness);

    await fireEvent.press(await screen.findByTestId('live-prepare-composer'));

    expect(
      await screen.findByText(
        'This protected review is no longer current or available. Close it, refresh, and review the draft again before opening Messages.',
      ),
    ).toBeTruthy();
    await expandSupportCode(code);
    expect(screen.queryByTestId('live-open-composer')).toBeNull();
    expect(screen.getByTestId('live-prepare-composer')).toBeTruthy();
    expect(harness.canOpenComposer).not.toHaveBeenCalled();
    expect(harness.openUserConfirmedComposer).not.toHaveBeenCalled();
  },
);

it('allows a fresh native preparation retry after a transient prepare error', async () => {
  const prepareComposerReview = jest
    .fn()
    .mockResolvedValueOnce({
      code: 'COMPOSER_RESERVATION_STALE',
      kind: 'error' as const,
    })
    .mockResolvedValue({
      kind: 'ok' as const,
      value: composerReview(),
    });
  const harness = createComposerHarness({ prepareComposerReview });
  await renderComposer(harness);

  await fireEvent.press(await screen.findByTestId('live-prepare-composer'));
  await expandSupportCode('COMPOSER_RESERVATION_STALE');
  await fireEvent.press(screen.getByTestId('live-prepare-composer'));

  expect(await screen.findByTestId('live-open-composer')).toBeTruthy();
  expect(screen.queryByTestId('live-composer-support-details')).toBeNull();
  expect(
    screen.queryByText('Technical code: COMPOSER_RESERVATION_STALE'),
  ).toBeNull();
  expect(prepareComposerReview).toHaveBeenCalledTimes(2);
  expect(prepareComposerReview.mock.calls).toEqual([
    [{ expectedRevision: '1', proposalId: 'proposal-1' }],
    [{ expectedRevision: '1', proposalId: 'proposal-1' }],
  ]);
});

it('collapses support details when a different composer error replaces the current one', async () => {
  const prepareComposerReview = jest
    .fn()
    .mockResolvedValueOnce({
      code: 'COMPOSER_RESERVATION_STALE',
      kind: 'error' as const,
    })
    .mockResolvedValueOnce({
      code: 'COMPOSER_RESERVATION_HELD',
      kind: 'error' as const,
    });
  const harness = createComposerHarness({ prepareComposerReview });
  await renderComposer(harness);

  await fireEvent.press(await screen.findByTestId('live-prepare-composer'));
  await expandSupportCode('COMPOSER_RESERVATION_STALE');
  await fireEvent.press(screen.getByTestId('live-prepare-composer'));

  expect(screen.queryByTestId('live-composer-support-details')).toBeNull();
  expect(
    screen.queryByText('Technical code: COMPOSER_RESERVATION_STALE'),
  ).toBeNull();
  await expandSupportCode('COMPOSER_RESERVATION_HELD');
  expect(prepareComposerReview).toHaveBeenCalledTimes(2);
});

it.each(['native invalidation', 'AppState', 'Back'] as const)(
  'collapses expanded support details on %s retirement',
  async retirementSource => {
    const onBack = jest.fn();
    const harness = createComposerHarness({
      prepareComposerReview: jest.fn(async () => ({
        code: 'COMPOSER_RESERVATION_STALE',
        kind: 'error' as const,
      })),
    });
    await renderComposer(harness, iosCapability, onBack);
    await fireEvent.press(await screen.findByTestId('live-prepare-composer'));
    await expandSupportCode('COMPOSER_RESERVATION_STALE');

    if (retirementSource === 'native invalidation') {
      await act(async () => {
        harness.emit({ areas: ['activity'], revision: revision('3') });
      });
    } else if (retirementSource === 'AppState') {
      await act(async () => {
        appStateListeners.forEach(listener => listener('active'));
        await Promise.resolve();
      });
    } else {
      await fireEvent.press(screen.getByTestId('live-composer-review-back'));
    }

    await waitFor(() =>
      expect(screen.queryByTestId('live-composer-support-details')).toBeNull(),
    );
    expect(
      screen.queryByText('Technical code: COMPOSER_RESERVATION_STALE'),
    ).toBeNull();
    expect(
      screen.getByTestId('live-composer-support-toggle').props
        .accessibilityState,
    ).toMatchObject({ expanded: false });
    expect(onBack).toHaveBeenCalledTimes(retirementSource === 'Back' ? 1 : 0);
  },
);

it('rechecks expiry before availability and never opens an expired visible review', async () => {
  const harness = createComposerHarness({
    prepareComposerReview: jest.fn(async () => ({
      kind: 'ok' as const,
      value: composerReview({ expiresAtEpochMilliseconds: now + 10 }),
    })),
  });
  await renderComposer(harness);
  await prepareVisibleReview(harness);
  jest.spyOn(Date, 'now').mockReturnValue(now + 11);

  await fireEvent.press(screen.getByTestId('live-open-composer'));

  await expandSupportCode('COMPOSER_REVIEW_EXPIRED');
  expect(harness.canOpenComposer).not.toHaveBeenCalled();
  expect(harness.openUserConfirmedComposer).not.toHaveBeenCalled();
});

it('preserves the reviewed nonce when availability is false and retries without preparing again', async () => {
  const canOpenComposer = jest
    .fn()
    .mockResolvedValueOnce(false)
    .mockResolvedValueOnce(true);
  const harness = createComposerHarness({
    canOpenComposer,
  });
  await renderComposer(harness);
  await prepareVisibleReview(harness);

  await fireEvent.press(screen.getByTestId('live-open-composer'));

  await expandSupportCode('COMPOSER_UNAVAILABLE');
  expect(screen.getByTestId('live-ios-composer-review')).toBeTruthy();
  expect(screen.getByTestId('live-open-composer')).toBeTruthy();
  expect(harness.canOpenComposer).toHaveBeenCalledTimes(1);
  expect(harness.openUserConfirmedComposer).not.toHaveBeenCalled();

  await fireEvent.press(screen.getByTestId('live-open-composer'));
  await waitFor(() =>
    expect(harness.openUserConfirmedComposer).toHaveBeenCalledTimes(1),
  );
  expect(harness.prepareComposerReview).toHaveBeenCalledTimes(1);
  expect(harness.openUserConfirmedComposer).toHaveBeenCalledWith({
    actionNonce: 'a'.repeat(43),
    expectedRevision: '2',
    proposalId: 'proposal-1',
  });
});

it('preserves the visible review when the availability bridge throws', async () => {
  const harness = createComposerHarness({
    canOpenComposer: jest.fn(async () => {
      throw new Error('availability bridge rejected');
    }),
  });
  await renderComposer(harness);
  await prepareVisibleReview(harness);

  await fireEvent.press(screen.getByTestId('live-open-composer'));

  await expandSupportCode('COMPOSER_NATIVE_FAILURE');
  expect(screen.getByTestId('live-ios-composer-review')).toBeTruthy();
  expect(screen.getByTestId('live-open-composer')).toBeTruthy();
  expect(harness.openUserConfirmedComposer).not.toHaveBeenCalled();
});

it.each([
  {
    label: 'throws',
    open: jest.fn(async () => {
      throw new Error('open bridge rejected');
    }),
    code: 'COMPOSER_NATIVE_FAILURE',
  },
  {
    label: 'returns an error',
    open: jest.fn(async () => ({
      code: 'COMPOSER_RESERVATION_STALE',
      kind: 'error' as const,
    })),
    code: 'COMPOSER_RESERVATION_STALE',
  },
])(
  'preserves the reviewed nonce when native open $label',
  async ({ code, open }) => {
    const harness = createComposerHarness({
      openUserConfirmedComposer: open,
    });
    await renderComposer(harness);
    await prepareVisibleReview(harness);

    await fireEvent.press(screen.getByTestId('live-open-composer'));

    await expandSupportCode(code);
    expect(screen.getByTestId('live-ios-composer-review')).toBeTruthy();
    expect(screen.getByTestId('live-open-composer')).toBeTruthy();
    expect(harness.prepareComposerReview).toHaveBeenCalledTimes(1);
    expect(open).toHaveBeenCalledWith({
      actionNonce: 'a'.repeat(43),
      expectedRevision: '2',
      proposalId: 'proposal-1',
    });
  },
);

it('collapses expanded support details when the retained review is invalidated', async () => {
  const getNextComposerProposal = jest
    .fn()
    .mockResolvedValueOnce(ok(readyProposal(), revision('1')))
    .mockResolvedValue(
      ok(readyProposal('proposal-2', 'Next Contact'), revision('3')),
    );
  const harness = createComposerHarness({
    canOpenComposer: jest.fn(async () => false),
    getNextComposerProposal,
  });
  await renderComposer(harness);
  await prepareVisibleReview(harness);
  await fireEvent.press(screen.getByTestId('live-open-composer'));
  await expandSupportCode('COMPOSER_UNAVAILABLE');

  await act(async () => {
    harness.emit({ areas: ['messages'], revision: revision('3') });
  });

  await waitFor(() => expect(getNextComposerProposal).toHaveBeenCalledTimes(2));
  await waitFor(() =>
    expect(screen.queryByTestId('live-open-composer')).toBeNull(),
  );
  expect(screen.queryByTestId('live-composer-support-details')).toBeNull();
  expect(screen.queryByText('Technical code: COMPOSER_UNAVAILABLE')).toBeNull();
  expect(
    screen.queryByText('Technical code: COMPOSER_REVIEW_STALE'),
  ).toBeNull();
  expect(
    screen.getByTestId('live-composer-support-toggle').props.accessibilityState,
  ).toMatchObject({ expanded: false });
});

it.each([
  {
    label: 'a newer projection revision',
    next: ok(readyProposal(), revision('3')),
  },
  {
    label: 'a different proposal',
    next: ok(readyProposal('proposal-2', 'Next Contact'), revision('2')),
  },
  {
    label: 'no remaining proposal',
    next: ok({ kind: 'none' as const }, revision('2')),
  },
])('invalidates a visible review after $label', async ({ next }) => {
  const getNextComposerProposal = jest
    .fn()
    .mockResolvedValueOnce(ok(readyProposal(), revision('1')))
    .mockResolvedValue(next);
  const harness = createComposerHarness({ getNextComposerProposal });
  await renderComposer(harness);
  await prepareVisibleReview(harness);

  await act(async () => {
    harness.emit({ areas: ['messages'], revision: revision('3') });
  });

  await waitFor(() => expect(getNextComposerProposal).toHaveBeenCalledTimes(2));
  await waitFor(() =>
    expect(screen.queryByTestId('live-open-composer')).toBeNull(),
  );
  await expandSupportCode('COMPOSER_REVIEW_STALE');
  expect(harness.canOpenComposer).not.toHaveBeenCalled();
  expect(harness.openUserConfirmedComposer).not.toHaveBeenCalled();
});

it('invalidates a visible review as soon as readiness becomes blocked', async () => {
  const getHome = jest
    .fn()
    .mockResolvedValueOnce(ok(iosHome, revision('1')))
    .mockResolvedValueOnce(
      ok(blockedIosHome('coordination-unavailable'), revision('3')),
    )
    .mockResolvedValue(ok(iosHome, revision('4')));
  const harness = createComposerHarness({ getHome });
  await renderComposer(harness);
  await prepareVisibleReview(harness);

  await act(async () => {
    harness.emit({ areas: ['readiness'], revision: revision('3') });
  });

  await waitFor(() => expect(getHome).toHaveBeenCalledTimes(2));
  await waitFor(() =>
    expect(screen.queryByTestId('live-open-composer')).toBeNull(),
  );
  await expandSupportCode('COMPOSER_REVIEW_BLOCKED');
  expect(harness.canOpenComposer).not.toHaveBeenCalled();
  expect(harness.openUserConfirmedComposer).not.toHaveBeenCalled();

  await act(async () => {
    harness.emit({ areas: ['readiness'], revision: revision('4') });
  });
  await waitFor(() => expect(getHome).toHaveBeenCalledTimes(3));
  await fireEvent.press(await screen.findByTestId('live-prepare-composer'));
  expect(await screen.findByTestId('live-open-composer')).toBeTruthy();
});

it.each([
  {
    code: 'COMPOSER_CONTACTS_RECONNECT_REQUIRED',
    label: 'reconnects Google Contacts',
    repair: 'reconnect' as const,
  },
  {
    code: 'COMPOSER_CONTACTS_FRESHNESS_UNAVAILABLE',
    label: 'syncs retained Contacts',
    repair: 'sync' as const,
  },
])(
  '$label, reloads both projections, and requires a new preparation',
  async ({ code, repair }) => {
    const getHome = jest.fn(async () => ok(iosHome));
    const getNextComposerProposal = jest
      .fn()
      .mockResolvedValueOnce(ok(readyProposal(), revision('1')))
      .mockResolvedValue(
        ok(readyProposal('proposal-2', 'Next Contact'), revision('3')),
      );
    const prepareComposerReview = jest
      .fn()
      .mockResolvedValueOnce({ kind: 'error' as const, code })
      .mockResolvedValue({
        kind: 'ok' as const,
        value: composerReview({
          actionNonce: 'b'.repeat(43),
          proposalId: 'proposal-2',
          revision: '4',
        }),
      });
    const harness = createComposerHarness({
      getHome,
      getNextComposerProposal,
      prepareComposerReview,
    });
    await renderComposer(harness);

    await fireEvent.press(await screen.findByTestId('live-prepare-composer'));
    const repairContacts = await screen.findByTestId(
      'live-composer-repair-contacts',
    );
    expect(
      screen.getByText(
        code === 'COMPOSER_CONTACTS_RECONNECT_REQUIRED'
          ? 'Reconnect Google Contacts, then review this Messages draft again.'
          : 'Contacts could not be safely refreshed. Check your connection and try the review again.',
      ),
    ).toBeTruthy();
    await expandSupportCode(code);
    expect(screen.queryByTestId('live-prepare-composer')).toBeNull();
    expect(screen.queryByTestId('live-open-composer')).toBeNull();
    await fireEvent.press(repairContacts);

    expect(screen.queryByTestId('live-composer-support-details')).toBeNull();
    expect(screen.queryByText(`Technical code: ${code}`)).toBeNull();

    await waitFor(() => expect(getHome).toHaveBeenCalledTimes(2));
    await waitFor(() =>
      expect(getNextComposerProposal).toHaveBeenCalledTimes(2),
    );
    if (repair === 'reconnect') {
      expect(harness.continueWithGoogle).toHaveBeenCalledTimes(1);
      expect(harness.syncContacts).not.toHaveBeenCalled();
    } else {
      expect(harness.syncContacts).toHaveBeenCalledWith('user');
      expect(harness.continueWithGoogle).not.toHaveBeenCalled();
    }
    expect(screen.queryByTestId('live-open-composer')).toBeNull();

    await fireEvent.press(await screen.findByTestId('live-prepare-composer'));
    await waitFor(() =>
      expect(screen.getByTestId('live-open-composer')).toBeTruthy(),
    );
    expect(prepareComposerReview.mock.calls).toEqual([
      [{ expectedRevision: '1', proposalId: 'proposal-1' }],
      [{ expectedRevision: '3', proposalId: 'proposal-2' }],
    ]);
  },
);

it.each([
  {
    capability: iosCapability,
    home: blockedIosHome('active-sender-other-device'),
    label: 'an Android-managed account',
  },
  {
    capability: iosCapability,
    home: blockedIosHome('coordination-unavailable'),
    label: 'unavailable coexistence status',
  },
  {
    capability: iosCapability,
    home: undefined,
    label: 'an unavailable Home projection',
  },
  {
    capability: iosCapability,
    home: androidHome,
    label: 'a Home platform mismatch',
  },
  {
    capability: androidCapability,
    home: androidHome,
    label: 'an Android capability',
  },
])('never prepares or opens for $label', async ({ capability, home }) => {
  const harness = createComposerHarness({
    getHome: jest.fn(async () => (home ? ok(home) : internalError())),
  });
  await renderComposer(harness, capability);

  if (capability.platform === 'android') {
    expect(harness.getHome).not.toHaveBeenCalled();
    expect(harness.getNextComposerProposal).not.toHaveBeenCalled();
  } else {
    await waitFor(() => expect(harness.getHome).toHaveBeenCalled());
  }
  expect(screen.queryByTestId('live-prepare-composer')).toBeNull();
  expect(screen.queryByTestId('live-open-composer')).toBeNull();
  expect(harness.prepareComposerReview).not.toHaveBeenCalled();
  expect(harness.canOpenComposer).not.toHaveBeenCalled();
  expect(harness.openUserConfirmedComposer).not.toHaveBeenCalled();
});

it.each([
  {
    outcome: 'cancelled' as const,
    title: 'Messages was closed. No message was sent by the app.',
    terminal: false,
  },
  {
    outcome: 'failed' as const,
    title: 'Messages could not finish. No send is confirmed.',
    terminal: false,
  },
  {
    outcome: 'reported-sent' as const,
    title: /Messages reported sent; delivery not confirmed/u,
    terminal: true,
  },
  {
    outcome: 'unknown' as const,
    title: 'The Messages result is unknown. Do not assume it sent.',
    terminal: true,
  },
])(
  'keeps the distinct $outcome outcome and obeys native repeat suppression',
  async ({
    outcome,
    terminal,
    title,
  }: {
    outcome: CompanionComposerOutcome;
    terminal: boolean;
    title: RegExp | string;
  }) => {
    const nextProjection = terminal
      ? ({ kind: 'none' } as const)
      : readyProposal('proposal-2', 'Next Contact');
    const getNextComposerProposal = jest
      .fn()
      .mockResolvedValueOnce(ok(readyProposal(), revision('1')))
      .mockResolvedValue(ok(nextProjection, revision('3')));
    const harness = createComposerHarness({
      getNextComposerProposal,
      openUserConfirmedComposer: jest.fn(async () => ({
        kind: 'ok' as const,
        value: outcome,
      })),
    });
    await renderComposer(harness);
    await prepareVisibleReview(harness);

    await fireEvent.press(screen.getByTestId('live-open-composer'));

    expect(await screen.findByText(title)).toBeTruthy();
    await waitFor(() =>
      expect(getNextComposerProposal).toHaveBeenCalledTimes(2),
    );
    expect(screen.queryByTestId('live-open-composer')).toBeNull();
    if (terminal) {
      expect(screen.queryByTestId('live-prepare-composer')).toBeNull();
    } else {
      expect(await screen.findByTestId('live-prepare-composer')).toBeTruthy();
    }
    expect(harness.canOpenComposer).toHaveBeenCalledTimes(1);
    expect(harness.openUserConfirmedComposer).toHaveBeenCalledTimes(1);
  },
);
