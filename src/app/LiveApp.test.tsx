import React from 'react';
import { BackHandler, Linking } from 'react-native';
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react-native';

import type { AccountProjection } from '../domain/account/model';
import type { ContactDetail, ContactSummary } from '../domain/contacts/model';
import type { HomeProjection } from '../domain/home/model';
import type { NativeRouteAvailable } from '../domain/navigation/model';
import type { PrivacyInventory } from '../domain/privacy/model';
import type { ReadinessProjection } from '../domain/readiness/model';
import type {
  ContactId,
  NativeRevision,
  ActivationReviewHandle,
  ActionHandle,
  ApprovalReviewHandle,
  BirthdayChoiceId,
  ComposerProposalId,
  MessagePreviewHandle,
  IssueId,
  OccurrenceId,
  PrivateDisplayName,
  PrivateEmail,
  PrivateMessageText,
  PrivacyOperationId,
  PrivacyReviewHandle,
  PhoneChoiceId,
  SafeSupportCode,
  SenderTransferOperationId,
  SenderTransferReviewHandle,
} from '../domain/shared/brand';
import type { ProjectionInvalidation } from '../application/ports/AppProjectionPort';
import type { NativeResult } from '../domain/shared/result';
import type { UtcInstant } from '../domain/shared/temporal';
import type {
  BootstrapProjection,
  SetupProjection,
} from '../domain/setup/model';
import type {
  LiveAppPort,
  LiveCompanionPort,
} from '../features/live/LiveAppPort';
import { CompanionNativeGateway } from '../infrastructure/native/ios/CompanionNativeGateway';
import { BirthdayAutopilotApp } from './AppRoot';

jest.mock('react-native-localize', () => ({
  getLocales: () => [{ languageCode: 'en' }],
}));

jest.mock('react-native-safe-area-context', () => {
  const TestReact = require('react');
  const { View } = require('react-native');
  return {
    SafeAreaProvider: (props: { children?: unknown }) => props.children,
    SafeAreaView: (props: { children?: unknown; [key: string]: unknown }) => {
      const { children, ...viewProps } = props;
      return TestReact.createElement(View, viewProps, children);
    },
    useSafeAreaInsets: () => ({ top: 0, right: 0, bottom: 0, left: 0 }),
  };
});

jest.mock('./providers/FixtureProvider', () => {
  throw new Error('Production live routes imported FixtureProvider');
});

jest.mock('../features/fixtures/data', () => {
  throw new Error('Production live routes imported fixture records');
});

const revision = (value: string) => value as NativeRevision;
const instant = (value: string) => value as UtcInstant;
const contactId = 'contact-live-1' as ContactId;
const generatedAt = instant('2026-07-12T07:00:00Z');
const unavailableCompanionPort = new CompanionNativeGateway(null, null);

const renderLiveApp = (
  port: LiveAppPort,
  companionPort: LiveCompanionPort = unavailableCompanionPort,
) =>
  render(
    <BirthdayAutopilotApp
      companionPort={companionPort}
      nativeProjectionPort={port}
    />,
  );

const capability = {
  platform: 'android',
  deliveryMode: 'unattended-device-sms',
  minimumApiLevel: 29,
  unattendedSms: 'release-gated',
  userComposer: 'available-as-explicit-alternative',
} as const;

const readiness: ReadinessProjection = {
  platform: 'android',
  test: { kind: 'allowed' },
  activation: { kind: 'allowed' },
  birthday: { kind: 'allowed' },
  lastCheckedAt: generatedAt,
};

const account: AccountProjection = {
  kind: 'connected',
  displayEmail: 'user@example.test' as PrivateEmail,
  sender: {
    platform: 'android',
    kind: 'automation-active',
    epochLabel: 'This device',
  },
};

const completeBootstrap: BootstrapProjection = {
  capability,
  eligibility: {
    kind: 'supported',
    capability,
    channelLabel: 'test',
    chargeDisclosureVersion: 'carrier-v1',
  },
  account,
  setupStep: 'complete',
};

const iosCapability = {
  platform: 'ios',
  deliveryMode: 'user-controlled-composer',
  unattendedSms: 'unavailable',
  userComposer: 'required',
} as const;

const iosReadiness: ReadinessProjection = {
  platform: 'ios',
  composer: { kind: 'allowed' },
  unattendedAutomation: {
    kind: 'unavailable',
    reason: 'platform-composer-only',
  },
  lastCheckedAt: generatedAt,
};

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

const iosBootstrap: BootstrapProjection = {
  capability: iosCapability,
  eligibility: {
    kind: 'supported',
    capability: iosCapability,
    channelLabel: 'test',
    chargeDisclosureVersion: 'composer-v1',
  },
  account: iosAccount,
  setupStep: 'complete',
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
    today: 0,
    nextSevenDays: 1,
  },
  contactsSync: {
    kind: 'fresh',
    completedAt: generatedAt,
    contactCount: 1,
  },
};

const liveHome = (effective: 'active' | 'paused-repair'): HomeProjection => ({
  automation: {
    platform: 'android',
    desired: effective === 'active' ? 'on' : 'paused',
    effective,
    readiness,
  },
  counts: {
    enabled: 1,
    needsAttention: effective === 'active' ? 0 : 1,
    unavailable: 0,
    today: 0,
    nextSevenDays: 0,
  },
  contactsSync: {
    kind: 'fresh',
    completedAt: generatedAt,
    contactCount: 1,
  },
});

const contactSummary = (
  kind: 'enabled' | 'paused' = 'enabled',
): ContactSummary => ({
  id: contactId,
  displayName: 'Live Contact' as PrivateDisplayName,
  birthdayLabel: '18 July',
  maskedPhone: '•••• 4321',
  readiness: { kind: 'ready' },
  enrollment:
    kind === 'enabled'
      ? {
          kind: 'enabled',
          approval: { kind: 'valid', approvedAt: generatedAt },
        }
      : {
          kind: 'paused',
          reason: 'policy-suspended',
          approval: { kind: 'valid', approvedAt: generatedAt },
        },
});

const contactDetail = (
  kind: 'enabled' | 'paused' = 'enabled',
): ContactDetail => ({
  summary: contactSummary(kind),
  phoneChoices: [],
  birthdayChoices: [],
  nextOccurrenceLabel: '18 July 2026',
});

const privacyInventory: PrivacyInventory = {
  localContactCount: 1,
  enabledRecipientCount: 1,
  approvalCount: 1,
  activityCount: 0,
  templateCount: 1,
  localStorageBytes: 512,
  consentVersions: ['contacts-v1'],
  externalSmsCopiesNotControlled: true,
};

const ok = <Value,>(
  value: Value,
  currentRevision = revision('1'),
): NativeResult<Value> => ({
  kind: 'ok',
  envelope: {
    contractVersion: 1,
    revision: currentRevision,
    generatedAt,
    value,
  },
});

const internalError = <Value,>(): NativeResult<Value> => ({
  kind: 'error',
  problem: {
    kind: 'internal',
    supportCode: 'NATIVE_NOT_CONFIGURED' as SafeSupportCode,
  },
});

type PortHarness = Readonly<{
  port: LiveAppPort;
  emit(event: ProjectionInvalidation): void;
  emitRoute(event: NativeRouteAvailable): void;
}>;

const createPort = (
  overrides: Readonly<Record<string, unknown>> = {},
): PortHarness => {
  const listeners = new Set<(event: ProjectionInvalidation) => void>();
  const routeListeners = new Set<(event: NativeRouteAvailable) => void>();
  const defaults = {
    getBootstrap: jest.fn(async () => ok(completeBootstrap)),
    getHome: jest.fn(async () => ok(liveHome('active'))),
    getSetup: jest.fn(async () => internalError<SetupProjection>()),
    getAccount: jest.fn(async () => ok(account)),
    getReadiness: jest.fn(async () => ok(readiness)),
    getInventory: jest.fn(async () => ok(privacyInventory)),
    getPublicResources: jest.fn(async () =>
      ok({
        kind: 'unavailable' as const,
        buildLabel: 'Birthday Autopilot 0.1.0 (1)',
      }),
    ),
    getLatestDeletionReceipt: jest.fn(async () =>
      ok({ kind: 'none' as const }),
    ),
    checkAccountDeletionStatus: jest.fn(async () =>
      ok({ kind: 'none' as const }),
    ),
    getCurrentOperation: jest.fn(async () => ok({ kind: 'none' as const })),
    getPendingRoute: jest.fn(async () => ok({ kind: 'none' as const })),
    getPolicyEditor: jest.fn(async () =>
      ok({ kind: 'not-configured' as const }),
    ),
    getApproval: jest.fn(async () =>
      ok({ kind: 'valid' as const, approvedAt: generatedAt }),
    ),
    getBirthdayJob: jest.fn(async () => internalError()),
    getMessageEditor: jest.fn(async () => internalError()),
    getNextComposerProposal: jest.fn(async () => internalError()),
    getLatestTest: jest.fn(async () => internalError()),
    listPeople: jest.fn(async () =>
      ok({ items: [contactSummary()], totalCount: 1 }),
    ),
    getPerson: jest.fn(async () => ok(contactDetail())),
    listActivity: jest.fn(async () => ok({ items: [] })),
    listIssues: jest.fn(async () => ok([])),
    previewDiagnostics: jest.fn(async () => internalError()),
    shareDiagnostics: jest.fn(async () => internalError()),
    previewMessage: jest.fn(async () => internalError()),
    previewPolicy: jest.fn(async () => internalError()),
    saveMessage: jest.fn(async () => internalError()),
    savePolicy: jest.fn(async () => internalError()),
    generateSuggestions: jest.fn(async () => internalError()),
    choosePhone: jest.fn(async () => internalError()),
    chooseBirthday: jest.fn(async () => internalError()),
    prepareApprovals: jest.fn(async () => internalError()),
    confirmApprovals: jest.fn(async () => internalError()),
    prepareTest: jest.fn(async () => internalError()),
    startTest: jest.fn(async () => internalError()),
    prepareActivation: jest.fn(async () => internalError()),
    activate: jest.fn(async () => internalError()),
    pauseAll: jest.fn(async () => internalError()),
    prepareResume: jest.fn(async () => internalError()),
    resume: jest.fn(async () => internalError()),
    prepareTodayOccurrence: jest.fn(async () => internalError()),
    confirmTodayOccurrence: jest.fn(async () => internalError()),
    prepareAction: jest.fn(async () => internalError()),
    confirmAction: jest.fn(async () => internalError()),
    getOperation: jest.fn(async () => internalError()),
    resumeOperation: jest.fn(async () => internalError()),
    getNotificationPermission: jest.fn(async () =>
      ok({ kind: 'granted' as const }),
    ),
    requestNotificationPermission: jest.fn(async () => internalError()),
    openNotificationSettings: jest.fn(async () => internalError()),
    getSenderTransferOperation: jest.fn(async () =>
      ok({ kind: 'none' as const }),
    ),
    prepareSenderTransfer: jest.fn(async () => internalError()),
    beginSenderTransfer: jest.fn(async () => internalError()),
    completeSenderTransfer: jest.fn(async () => internalError()),
    resumeSenderTransfer: jest.fn(async () => internalError()),
    repairLifecycleState: jest.fn(async () => internalError()),
    refreshCompatibility: jest.fn(async () => internalError()),
    continueWithGoogle: jest.fn(async () => internalError()),
    authorizeContacts: jest.fn(async () => internalError()),
    syncContacts: jest.fn(async () => internalError()),
    prepareEnrollmentReview: jest.fn(async () => internalError()),
    confirmEnrollment: jest.fn(async () => internalError()),
    pauseRecipient: jest.fn(async () => internalError()),
    restoreRecipient: jest.fn(async () => internalError()),
    excludeRecipient: jest.fn(async () => internalError()),
    performAction: jest.fn(async () => internalError()),
    subscribeInvalidations: (
      listener: (event: ProjectionInvalidation) => void,
    ) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    subscribeRouteAvailable: (
      listener: (event: NativeRouteAvailable) => void,
    ) => {
      routeListeners.add(listener);
      return () => routeListeners.delete(listener);
    },
  };
  return {
    port: { ...defaults, ...overrides } as unknown as LiveAppPort,
    emit: event => listeners.forEach(listener => listener(event)),
    emitRoute: event => routeListeners.forEach(listener => listener(event)),
  };
};

afterEach(async () => {
  await cleanup();
  jest.restoreAllMocks();
  jest.useRealTimers();
});

describe('production live projections', () => {
  it('renders a successful native Home route without importing or rendering fixtures', async () => {
    const getHome = jest.fn(async () => ok(liveHome('active')));
    const { port } = createPort({ getHome });

    await renderLiveApp(port);

    await waitFor(() =>
      expect(screen.getByTestId('live-home-screen')).toBeTruthy(),
    );
    expect(screen.getByTestId('live-home-screen').props.edges).toEqual([
      'top',
      'left',
      'right',
    ]);
    expect(screen.getByText('Automation is on')).toBeTruthy();
    expect(screen.queryByText(/Synthetic data/u)).toBeNull();
    expect(screen.queryByText(/Interactive UI fixture/u)).toBeNull();
    expect(getHome).toHaveBeenCalledTimes(1);

    await fireEvent.press(screen.getByTestId('live-tab-settings'));
    await waitFor(() =>
      expect(screen.getByTestId('live-settings-screen')).toBeTruthy(),
    );
    expect(screen.getByText('Android Automation Edition')).toBeTruthy();
  });

  it('opens only fixed verified Hosting routes from Help and legal', async () => {
    const openURL = jest.spyOn(Linking, 'openURL').mockResolvedValue(true);
    const getPublicResources = jest.fn(async () =>
      ok({
        kind: 'available' as const,
        buildLabel: 'Birthday Autopilot 0.1.0 (1)',
        baseUrl: 'https://birthday-autopilot-prod.web.app',
      }),
    );
    const { port } = createPort({ getPublicResources });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-settings'));
    await fireEvent.press(screen.getByTestId('live-settings-help-legal'));

    expect(await screen.findByTestId('live-help-legal-screen')).toBeTruthy();
    expect(screen.getByText('Birthday Autopilot 0.1.0 (1)')).toBeTruthy();
    expect(screen.getByText(/every supported-device/u)).toBeTruthy();
    await fireEvent.press(screen.getByTestId('live-help-privacy'));
    await waitFor(() =>
      expect(openURL).toHaveBeenCalledWith(
        'https://birthday-autopilot-prod.web.app/privacy',
      ),
    );
    expect(getPublicResources).toHaveBeenCalledTimes(1);
  });

  it('keeps live iOS status in foreground, user-controlled Companion mode', async () => {
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(iosHome)),
      getAccount: jest.fn(async () => ok(iosAccount)),
      getReadiness: jest.fn(async () => ok(iosReadiness)),
    });

    await renderLiveApp(port);

    await waitFor(() =>
      expect(screen.getByText('Birthday reminders are ready')).toBeTruthy(),
    );
    expect(screen.getByText('iOS Companion Edition')).toBeTruthy();
    expect(screen.getByText(/editable Messages screen/u)).toBeTruthy();
    expect(screen.queryByText('Automation is on')).toBeNull();
  });

  it('requests Android safety alerts once and routes later denial to phone settings', async () => {
    const getNotificationPermission = jest
      .fn()
      .mockResolvedValueOnce(ok({ kind: 'not-requested' as const }))
      .mockResolvedValue(ok({ kind: 'settings-required' as const }));
    const requestNotificationPermission = jest.fn(async () =>
      ok({ kind: 'denied' as const }),
    );
    const openNotificationSettings = jest.fn(async () =>
      ok({ kind: 'opened' as const }),
    );
    const { port } = createPort({
      getNotificationPermission,
      requestNotificationPermission,
      openNotificationSettings,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-settings'));
    await fireEvent.press(
      await screen.findByTestId('live-request-notification-permission'),
    );

    await waitFor(() =>
      expect(requestNotificationPermission).toHaveBeenCalledTimes(1),
    );
    expect(
      await screen.findByText('Allow alerts in phone settings'),
    ).toBeTruthy();
    expect(screen.getByText(/does not guarantee/u)).toBeTruthy();
    await fireEvent.press(screen.getByTestId('live-open-notification-settings'));
    await waitFor(() =>
      expect(openNotificationSettings).toHaveBeenCalledTimes(1),
    );
  });

  it('reviews, drains, and completes Android sender transfer only into test-only mode', async () => {
    const standbyAccount: AccountProjection = {
      kind: 'connected',
      displayEmail: 'user@example.test' as PrivateEmail,
      sender: {
        platform: 'android',
        kind: 'standby',
        activeOtherDeviceLabel: 'Pixel 8',
      },
    };
    const operationId = `transfer_${'b'.repeat(
      32,
    )}` as SenderTransferOperationId;
    const getSenderTransferOperation = jest
      .fn()
      .mockResolvedValueOnce(ok({ kind: 'none' as const }))
      .mockResolvedValueOnce(
        ok({
          kind: 'remote-draining' as const,
          id: operationId,
          preissuedPermitMayFinish: true as const,
          reason: 'transfer-pending' as const,
          updatedAt: generatedAt,
          drainUntil: instant('2026-07-12T07:01:00Z'),
        }),
      )
      .mockResolvedValue(
        ok({
          kind: 'complete' as const,
          id: operationId,
          preissuedPermitMayFinish: false as const,
          completedAt: instant('2026-07-12T07:02:00Z'),
          requiresTest: true as const,
        }),
      );
    const prepareSenderTransfer = jest.fn(async () =>
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
        revision('5'),
      ),
    );
    const beginSenderTransfer = jest.fn(async () =>
      ok({
        kind: 'remote-draining' as const,
        id: operationId,
        preissuedPermitMayFinish: true as const,
        reason: 'transfer-pending' as const,
        updatedAt: generatedAt,
        drainUntil: instant('2026-07-12T07:01:00Z'),
      }),
    );
    const completeSenderTransfer = jest.fn(async () =>
      ok({
        kind: 'complete' as const,
        id: operationId,
        preissuedPermitMayFinish: false as const,
        completedAt: instant('2026-07-12T07:02:00Z'),
        requiresTest: true as const,
      }),
    );
    const { port } = createPort({
      getAccount: jest.fn(async () => ok(standbyAccount)),
      getSenderTransferOperation,
      prepareSenderTransfer,
      beginSenderTransfer,
      completeSenderTransfer,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-settings'));
    await fireEvent.press(
      await screen.findByTestId('live-prepare-sender-transfer'),
    );

    expect(await screen.findByText('Move the Android sender?')).toBeTruthy();
    expect(screen.getByText(/old phone loses sender authority/u)).toBeTruthy();
    expect(
      screen.getByText(
        'This phone becomes test-only; automation does not turn on.',
      ),
    ).toBeTruthy();
    expect(screen.getByText(/permit already issued/u)).toBeTruthy();
    await fireEvent.press(screen.getByTestId('live-confirm-sender-transfer'));
    await waitFor(() => expect(beginSenderTransfer).toHaveBeenCalledTimes(1));
    expect(prepareSenderTransfer).toHaveBeenCalledWith({
      expectedRevision: '1',
    });
    expect(beginSenderTransfer).toHaveBeenCalledWith({
      handle: `st_${'a'.repeat(32)}`,
      expectedRevision: '5',
    });

    await fireEvent.press(
      await screen.findByTestId('live-continue-sender-transfer'),
    );
    await waitFor(() => expect(completeSenderTransfer).toHaveBeenCalledTimes(1));
    expect(
      await screen.findByText('A real SMS test is required'),
    ).toBeTruthy();
    expect(screen.queryByText('Automation is on')).toBeNull();
  });

  it('does not expose Android notification or sender-transfer controls on iOS', async () => {
    const getNotificationPermission = jest.fn(async () =>
      ok({ kind: 'not-requested' as const }),
    );
    const getSenderTransferOperation = jest.fn(async () =>
      ok({ kind: 'none' as const }),
    );
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(iosHome)),
      getAccount: jest.fn(async () => ok(iosAccount)),
      getReadiness: jest.fn(async () => ok(iosReadiness)),
      getNotificationPermission,
      getSenderTransferOperation,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-settings'));

    expect(screen.queryByText('Safety alerts')).toBeNull();
    expect(screen.queryByText('Android sender phone')).toBeNull();
    expect(getNotificationPermission).not.toHaveBeenCalled();
    expect(getSenderTransferOperation).not.toHaveBeenCalled();
  });

  it('follows phone appearance and language while showing the complete Android inventory', async () => {
    const inventory = {
      ...privacyInventory,
      activityCount: 3,
      templateCount: 2,
      lastContactsSyncAt: generatedAt,
      consentVersions: ['contacts-v1', 'privacy-v2'],
    };
    const { port } = createPort({
      getInventory: jest.fn(async () => ok(inventory)),
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-settings'));

    expect(
      await screen.findByText('Appearance follows this phone'),
    ).toBeTruthy();
    expect(screen.getByText('Language follows this phone')).toBeTruthy();
    expect(screen.queryByTestId('live-appearance-dark')).toBeNull();
    expect(screen.queryByTestId('live-language-hi')).toBeNull();
    expect(screen.getByText('Saved message templates')).toBeTruthy();
    expect(screen.getByText('Last Contacts sync')).toBeTruthy();
    expect(screen.getByText('Recorded consent versions')).toBeTruthy();
    expect(screen.getByText('contacts-v1, privacy-v2')).toBeTruthy();
    expect(screen.getByText(/at most 30 days/u)).toBeTruthy();
    expect(screen.getByText(/up to 400 days/u)).toBeTruthy();
    expect(screen.getByText('Copies outside Birthday Autopilot')).toBeTruthy();
  });

  it('shows the iOS minimum composer marker instead of Android ledger retention', async () => {
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(iosHome)),
      getAccount: jest.fn(async () => ok(iosAccount)),
      getReadiness: jest.fn(async () => ok(iosReadiness)),
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-settings'));

    expect(
      await screen.findByText(/minimum opaque composer marker/u),
    ).toBeTruthy();
    expect(
      screen.queryByText(/retain a separate content-free duplicate-safety/u),
    ).toBeNull();
  });

  it('requeries a visible projection when native invalidation arrives', async () => {
    const getHome = jest
      .fn()
      .mockResolvedValueOnce(ok(liveHome('active'), revision('1')))
      .mockResolvedValueOnce(ok(liveHome('paused-repair'), revision('2')));
    const harness = createPort({ getHome });

    await renderLiveApp(harness.port);
    await waitFor(() =>
      expect(screen.getByText('Automation is on')).toBeTruthy(),
    );

    await act(async () => {
      harness.emit({ revision: revision('2'), areas: ['home'] });
    });

    await waitFor(() =>
      expect(screen.getByText('Automation needs attention')).toBeTruthy(),
    );
    expect(getHome).toHaveBeenCalledTimes(2);
  });

  it('does not retry a stale revision mutation and safely requeries details', async () => {
    const getPerson = jest
      .fn()
      .mockResolvedValueOnce(ok(contactDetail(), revision('1')))
      .mockResolvedValueOnce(ok(contactDetail(), revision('2')));
    const pauseRecipient = jest.fn(async () => ({
      kind: 'error' as const,
      problem: {
        kind: 'stale-revision' as const,
        latestRevision: revision('2'),
      },
    }));
    const { port } = createPort({ getPerson, pauseRecipient });

    await renderLiveApp(port);
    await waitFor(() =>
      expect(screen.getByTestId('live-home-screen')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-tab-people'));
    await waitFor(() =>
      expect(screen.getByTestId('live-person-contact-live-1')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-person-contact-live-1'));
    await waitFor(() =>
      expect(screen.getByTestId('live-person-pause')).toBeTruthy(),
    );
    expect(screen.getByTestId('live-person-detail-screen').props.edges).toEqual(
      ['top', 'left', 'right', 'bottom'],
    );

    await fireEvent.press(screen.getByTestId('live-person-pause'));

    await waitFor(() => expect(getPerson).toHaveBeenCalledTimes(2));
    expect(pauseRecipient).toHaveBeenCalledTimes(1);
    expect(screen.getByText(/information changed/u)).toBeTruthy();
    expect(
      screen.getByTestId('live-person-enrollment').props.accessibilityLabel,
    ).toContain('Enabled');
  });

  it('keeps the verified person projection unchanged when a mutation fails', async () => {
    const getPerson = jest.fn(async () => ok(contactDetail(), revision('1')));
    const pauseRecipient = jest.fn(async () => {
      throw new Error('native bridge rejected');
    });
    const { port } = createPort({ getPerson, pauseRecipient });

    await renderLiveApp(port);
    await waitFor(() =>
      expect(screen.getByTestId('live-home-screen')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-tab-people'));
    await waitFor(() =>
      expect(screen.getByTestId('live-person-contact-live-1')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-person-contact-live-1'));
    await waitFor(() =>
      expect(screen.getByTestId('live-person-pause')).toBeTruthy(),
    );

    await fireEvent.press(screen.getByTestId('live-person-pause'));

    await waitFor(() =>
      expect(screen.getByText('Action not completed')).toBeTruthy(),
    );
    expect(pauseRecipient).toHaveBeenCalledTimes(1);
    expect(getPerson).toHaveBeenCalledTimes(1);
    expect(
      screen.getByTestId('live-person-enrollment').props.accessibilityLabel,
    ).toContain('Enabled');
  });

  it('uses Android hardware back to leave a protected detail route', async () => {
    let hardwareBack: (() => boolean | null | undefined) | undefined;
    const backSpy = jest
      .spyOn(BackHandler, 'addEventListener')
      .mockImplementation((_event, handler) => {
        hardwareBack = () => handler({} as never);
        return { remove: jest.fn() };
      });
    const { port } = createPort();

    await renderLiveApp(port);
    await waitFor(() =>
      expect(screen.getByTestId('live-home-screen')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-tab-people'));
    await waitFor(() =>
      expect(screen.getByTestId('live-person-contact-live-1')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-person-contact-live-1'));
    await waitFor(() =>
      expect(screen.getByTestId('live-person-detail-screen')).toBeTruthy(),
    );

    await act(async () => {
      expect(hardwareBack?.()).toBe(true);
    });

    await waitFor(() =>
      expect(screen.getByTestId('live-people-screen')).toBeTruthy(),
    );
    expect(screen.queryByTestId('live-person-detail-screen')).toBeNull();
    backSpy.mockRestore();
  });

  it('uses Android hardware back through nested activity routes', async () => {
    let hardwareBack: (() => boolean | null | undefined) | undefined;
    const backSpy = jest
      .spyOn(BackHandler, 'addEventListener')
      .mockImplementation((_event, handler) => {
        hardwareBack = () => handler({} as never);
        return { remove: jest.fn() };
      });
    const { port } = createPort({
      listActivity: jest.fn(async () =>
        ok({
          items: [
            {
              id: 'activity-1' as import('../domain/shared/brand').ActivityId,
              kind: 'delivered' as const,
              occurredAt: generatedAt,
              actionable: false,
            },
          ],
        }),
      ),
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-home-activity'));
    await fireEvent.press(
      await screen.findByTestId('live-activity-activity-1'),
    );
    await waitFor(() =>
      expect(screen.getByTestId('live-activity-detail-screen')).toBeTruthy(),
    );

    await act(async () => {
      expect(hardwareBack?.()).toBe(true);
    });
    await waitFor(() =>
      expect(screen.getByTestId('live-activity-screen')).toBeTruthy(),
    );
    expect(screen.queryByTestId('live-activity-detail-screen')).toBeNull();

    await act(async () => {
      expect(hardwareBack?.()).toBe(true);
    });
    await waitFor(() =>
      expect(screen.getByTestId('live-home-screen')).toBeTruthy(),
    );
    expect(screen.queryByTestId('live-activity-screen')).toBeNull();
    backSpy.mockRestore();
  });

  it('keeps a dirty message draft when a newer native revision arrives', async () => {
    const oldDraft = {
      kind: 'configured' as const,
      draft: {
        language: 'en' as const,
        tone: 'warm' as const,
        placeholderMode: {
          kind: 'generic' as const,
          requiredCount: 0 as const,
        },
        text: 'Saved old message' as PrivateMessageText,
        requestedSegmentCap: 1 as const,
      },
    };
    const newDraft = {
      ...oldDraft,
      draft: {
        ...oldDraft.draft,
        text: 'Saved on another screen' as PrivateMessageText,
      },
    };
    const getMessageEditor = jest
      .fn()
      .mockResolvedValueOnce(ok(oldDraft, revision('1')))
      .mockResolvedValueOnce(ok(newDraft, revision('2')));
    const harness = createPort({ getMessageEditor });

    await renderLiveApp(harness.port);
    await waitFor(() =>
      expect(screen.getByTestId('live-home-message')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-home-message'));
    await waitFor(() =>
      expect(screen.getByTestId('live-message-input')).toBeTruthy(),
    );
    await fireEvent.changeText(
      screen.getByTestId('live-message-input'),
      'My unsaved message',
    );

    await act(async () => {
      harness.emit({ revision: revision('2'), areas: ['messages'] });
    });

    await waitFor(() => expect(getMessageEditor).toHaveBeenCalledTimes(2));
    expect(screen.getByTestId('live-message-input').props.value).toBe(
      'My unsaved message',
    );
    expect(screen.getByText(/saved message changed/u)).toBeTruthy();

    await fireEvent.press(screen.getByTestId('live-message-reload-saved'));
    expect(screen.getByTestId('live-message-input').props.value).toBe(
      'Saved on another screen',
    );
  });

  it('uses native Gemini candidates then previews and saves by review handle', async () => {
    const editorProjection = {
      kind: 'configured' as const,
      draft: {
        language: 'en' as const,
        tone: 'warm' as const,
        placeholderMode: {
          kind: 'generic' as const,
          requiredCount: 0 as const,
        },
        text: 'Saved message' as PrivateMessageText,
        requestedSegmentCap: 1 as const,
      },
    };
    const generateSuggestions = jest.fn(async () =>
      ok({
        kind: 'candidates' as const,
        candidates: ['Native suggested message' as PrivateMessageText],
      }),
    );
    const previewMessage = jest.fn(async () =>
      ok(
        {
          kind: 'valid' as const,
          handle: 'message-preview-1' as MessagePreviewHandle,
          examples: [
            {
              displayName: 'Live Contact' as PrivateDisplayName,
              finalText: 'Native suggested message' as PrivateMessageText,
              characterCount: 24,
              segmentCount: 1,
              encodingLabel: 'gsm-7' as const,
            },
          ],
          maximumSegmentCount: 1,
          affectedRecipientCount: 1,
        },
        revision('3'),
      ),
    );
    const saveMessage = jest.fn(async () =>
      ok({
        draft: {
          ...editorProjection.draft,
          text: 'Native suggested message' as PrivateMessageText,
        },
        affectedRecipientCount: 1,
        invalidatedApprovalCount: 1,
      }),
    );
    const { port } = createPort({
      getMessageEditor: jest.fn(async () => ok(editorProjection)),
      generateSuggestions,
      previewMessage,
      saveMessage,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-home-message'));
    await fireEvent.press(await screen.findByTestId('live-message-suggest'));
    await fireEvent.press(
      await screen.findByTestId('live-message-suggestion-0'),
    );
    await fireEvent.press(screen.getByTestId('live-message-preview'));
    await waitFor(() =>
      expect(screen.getByTestId('live-message-save')).toBeTruthy(),
    );
    expect(saveMessage).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByTestId('live-message-save'));

    await waitFor(() => expect(saveMessage).toHaveBeenCalledTimes(1));
    expect(previewMessage).toHaveBeenCalledWith({
      draft: expect.objectContaining({ text: 'Native suggested message' }),
      expectedRevision: '1',
    });
    expect(saveMessage).toHaveBeenCalledWith({
      handle: 'message-preview-1',
      expectedRevision: '3',
    });
  });

  it('turns template validation codes into actionable primary copy', async () => {
    const editorProjection = {
      kind: 'configured' as const,
      draft: {
        language: 'en' as const,
        tone: 'warm' as const,
        placeholderMode: {
          kind: 'generic' as const,
          requiredCount: 0 as const,
        },
        text: 'Saved message' as PrivateMessageText,
        requestedSegmentCap: 1 as const,
      },
    };
    const previewMessage = jest.fn(async () => internalError());
    const { port } = createPort({
      getMessageEditor: jest.fn(async () => ok(editorProjection)),
      previewMessage,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-home-message'));
    await fireEvent.changeText(
      await screen.findByTestId('live-message-input'),
      'See https://example.com',
    );
    await fireEvent.press(screen.getByTestId('live-message-preview'));

    expect(
      await screen.findByText('Remove links from the birthday message.'),
    ).toBeTruthy();
    expect(screen.queryByText('template-url-not-allowed')).toBeNull();
    expect(previewMessage).not.toHaveBeenCalled();
  });

  it('does not retry stale Android activation confirmation', async () => {
    const prepareActivation = jest.fn(async () =>
      ok(
        {
          platform: 'android' as const,
          handle: 'activation-review-1' as ActivationReviewHandle,
          enabledRecipientCount: 1,
          attentionCount: 0,
          templatePreview: 'Happy birthday!' as PrivateMessageText,
          windowLabel: '09:00–11:00',
          simLabel: 'SIM 1',
          dailyCap: 10,
          limitationsDisclosure: 'Carrier charges may apply.',
        },
        revision('7'),
      ),
    );
    const activate = jest.fn(async () => ({
      kind: 'error' as const,
      problem: {
        kind: 'stale-revision' as const,
        latestRevision: revision('8'),
      },
    }));
    const getHome = jest.fn(async () => ok(liveHome('active')));
    const { port } = createPort({ prepareActivation, activate, getHome });

    await renderLiveApp(port);
    await waitFor(() =>
      expect(screen.getByTestId('live-home-automation')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-home-automation'));
    await waitFor(() =>
      expect(screen.getByTestId('live-review-activation')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-review-activation'));
    await waitFor(() =>
      expect(screen.getByTestId('live-confirm-activation')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-confirm-activation'));

    await waitFor(() =>
      expect(screen.getByText(/information changed/u)).toBeTruthy(),
    );
    expect(activate).toHaveBeenCalledTimes(1);
    expect(activate).toHaveBeenCalledWith({
      handle: 'activation-review-1',
      expectedRevision: '7',
    });
  });

  it('invalidates an Android protected review before it can be confirmed', async () => {
    const prepareActivation = jest.fn(async () =>
      ok(
        {
          platform: 'android' as const,
          handle: 'activation-review-1' as ActivationReviewHandle,
          enabledRecipientCount: 1,
          attentionCount: 0,
          templatePreview: 'Happy birthday!' as PrivateMessageText,
          windowLabel: '09:00–11:00',
          simLabel: 'SIM 1',
          dailyCap: 10,
          limitationsDisclosure: 'Carrier charges may apply.',
        },
        revision('7'),
      ),
    );
    const activate = jest.fn(async () => internalError());
    const harness = createPort({ prepareActivation, activate });

    await renderLiveApp(harness.port);
    await fireEvent.press(await screen.findByTestId('live-home-automation'));
    await fireEvent.press(await screen.findByTestId('live-review-activation'));
    await waitFor(() =>
      expect(screen.getByTestId('live-confirm-activation')).toBeTruthy(),
    );

    await act(async () => {
      harness.emit({ revision: revision('8'), areas: ['automation'] });
    });

    await waitFor(() =>
      expect(screen.queryByTestId('live-confirm-activation')).toBeNull(),
    );
    expect(activate).not.toHaveBeenCalled();
  });

  it('localizes the latest Android test phase while preserving uncertainty', async () => {
    const { port } = createPort({
      getLatestTest: jest.fn(async () =>
        ok({
          platform: 'android' as const,
          phase: 'sent-from-device' as const,
          updatedAt: generatedAt,
        }),
      ),
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-home-automation'));

    await waitFor(() =>
      expect(
        screen.getByText(
          'SMS left this phone; carrier delivery is not confirmed',
        ),
      ).toBeTruthy(),
    );
    expect(screen.queryByText('sent-from-device')).toBeNull();
  });

  it('opens iOS Messages only with a reviewed opaque proposal and reports uncertainty', async () => {
    const proposalId = 'proposal-1' as ComposerProposalId;
    const getNextComposerProposal = jest.fn(async () =>
      ok({
        kind: 'ready' as const,
        proposalId,
        occurrenceId: 'occurrence-1' as OccurrenceId,
        occurrenceDate:
          '2026-07-18' as import('../domain/shared/temporal').LocalDate,
        recipient: 'Live Contact' as PrivateDisplayName,
      }),
    );
    const prepareComposerReview = jest.fn(async () => ({
      kind: 'ok' as const,
      value: {
        actionNonce: 'a'.repeat(43),
        body: 'Happy birthday!',
        expiresAtEpochMilliseconds: Date.now() + 60_000,
        maskedDestination: '•••• 4321',
        proposalId: 'proposal-1',
        revision: '1',
      },
    }));
    const openUserConfirmedComposer = jest.fn(async () => ({
      kind: 'ok' as const,
      value: 'reported-sent' as const,
    }));
    const companionPort: LiveCompanionPort = {
      canOpenComposer: jest.fn(async () => true),
      getReminderStatus: jest.fn(async () => ({
        kind: 'ok' as const,
        value: {
          authorization: 'authorized' as const,
          failedCount: 0,
          kind: 'ok' as const,
          plannedDateCount: 1,
          scheduledCount: 1,
          truncated: false,
        },
      })),
      openNotificationSettings: jest.fn(),
      openUserConfirmedComposer,
      prepareComposerReview,
      requestReminderAuthorization: jest.fn(),
    };
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(iosHome)),
      getNextComposerProposal,
    });

    await renderLiveApp(port, companionPort);
    await waitFor(() =>
      expect(screen.getByTestId('live-home-automation')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-home-automation'));
    await waitFor(() =>
      expect(screen.getByTestId('live-prepare-composer')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-prepare-composer'));
    await waitFor(() =>
      expect(screen.getByTestId('live-open-composer')).toBeTruthy(),
    );
    expect(screen.getByText('•••• 4321')).toBeTruthy();
    expect(screen.getByText('18 July 2026')).toBeTruthy();
    expect(screen.queryByText('2026-07-18')).toBeNull();
    expect(screen.queryByText('+919876543210')).toBeNull();

    await fireEvent.press(screen.getByTestId('live-open-composer'));

    await waitFor(() =>
      expect(screen.getByText(/carrier delivery are unknown/u)).toBeTruthy(),
    );
    expect(prepareComposerReview).toHaveBeenCalledWith({
      expectedRevision: '1',
      proposalId: 'proposal-1',
    });
    expect(openUserConfirmedComposer).toHaveBeenCalledWith({
      actionNonce: 'a'.repeat(43),
      expectedRevision: '1',
      proposalId: 'proposal-1',
    });
  });

  it('fails an unexpected iOS companion bridge rejection closed', async () => {
    const companionPort: LiveCompanionPort = {
      canOpenComposer: jest.fn(async () => false),
      getReminderStatus: jest.fn(async () => {
        throw new Error('bridge rejected');
      }),
      openNotificationSettings: jest.fn(),
      openUserConfirmedComposer: jest.fn(),
      prepareComposerReview: jest.fn(),
      requestReminderAuthorization: jest.fn(),
    };
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(iosHome)),
      getNextComposerProposal: jest.fn(async () =>
        ok({ kind: 'none' as const }),
      ),
    });

    await renderLiveApp(port, companionPort);
    await fireEvent.press(await screen.findByTestId('live-home-automation'));

    await waitFor(() =>
      expect(screen.getByText(/Reminder status is unavailable/u)).toBeTruthy(),
    );
    expect(screen.queryByTestId('live-open-composer')).toBeNull();
  });

  it('requires a native privacy review before starting a destructive operation', async () => {
    const prepareAction = jest.fn(async () =>
      ok(
        {
          handle: 'privacy-review-1' as PrivacyReviewHandle,
          kind: 'wipe-local-data' as const,
          titleKey: 'privacy.wipe.title',
          consequenceKeys: ['privacy.wipe.contacts'],
          preissuedPermitMayFinish: false,
          remoteConnectionRequired: false,
          externalSmsCopiesNotErased: true as const,
        },
        revision('4'),
      ),
    );
    const confirmAction = jest.fn(async () =>
      ok({
        kind: 'queued' as const,
        id: 'privacy-operation-1' as PrivacyOperationId,
        action: 'wipe-local-data' as const,
        updatedAt: generatedAt,
      }),
    );
    const { port } = createPort({ prepareAction, confirmAction });

    await renderLiveApp(port);
    await waitFor(() =>
      expect(screen.getByTestId('live-tab-settings')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-tab-settings'));
    await waitFor(() =>
      expect(screen.getByTestId('live-settings-privacy')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-settings-privacy'));
    await waitFor(() =>
      expect(screen.getByTestId('live-privacy-wipe-local-data')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-privacy-wipe-local-data'));
    expect(confirmAction).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByTestId('live-privacy-prepare'));
    await waitFor(() =>
      expect(screen.getByTestId('live-privacy-confirm')).toBeTruthy(),
    );
    expect(confirmAction).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByTestId('live-privacy-confirm'));

    await waitFor(() =>
      expect(screen.getByText(/still running/u)).toBeTruthy(),
    );
    expect(prepareAction).toHaveBeenCalledWith({
      kind: 'wipe-local-data',
      expectedRevision: '1',
    });
    expect(confirmAction).toHaveBeenCalledWith({
      handle: 'privacy-review-1',
      expectedRevision: '4',
    });
  });

  it('recovers a durable privacy operation after the screen is recreated', async () => {
    const operationId = 'privacy-operation-recovery' as PrivacyOperationId;
    const getCurrentOperation = jest.fn(async () =>
      ok({
        kind: 'remote-pending' as const,
        id: operationId,
        action: 'disconnect-contacts' as const,
        reason: 'coordination-unavailable' as const,
        updatedAt: generatedAt,
      }),
    );
    const resumeOperation = jest.fn(async () =>
      ok({
        kind: 'complete' as const,
        id: operationId,
        action: 'disconnect-contacts' as const,
        completedAt: generatedAt,
        externalSmsCopiesNotErased: true as const,
      }),
    );
    const { port } = createPort({ getCurrentOperation, resumeOperation });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-settings'));
    await fireEvent.press(await screen.findByTestId('live-settings-privacy'));

    expect(await screen.findByText('Privacy operation')).toBeTruthy();
    expect(screen.queryByTestId('live-privacy-delete-account')).toBeNull();
    await fireEvent.press(screen.getByTestId('live-privacy-resume-operation'));
    await waitFor(() => expect(resumeOperation).toHaveBeenCalledTimes(1));
    expect(resumeOperation).toHaveBeenCalledWith(operationId);
    expect(
      await screen.findAllByText('The protected operation is complete.'),
    ).not.toHaveLength(0);
  });

  it('requires a second native review before erasing this device while account deletion is unresolved', async () => {
    const operationId = 'privacy-delete-unresolved' as PrivacyOperationId;
    const prepareAction = jest.fn(async () =>
      ok(
        {
          handle: 'privacy-pending-wipe-review' as PrivacyReviewHandle,
          kind: 'wipe-local-data' as const,
          titleKey: 'privacy.wipe-local-data',
          consequenceKeys: [
            'privacy.consequence.local-data-erased',
            'privacy.consequence.external-sms',
          ],
          preissuedPermitMayFinish: true,
          remoteConnectionRequired: false,
          externalSmsCopiesNotErased: true as const,
        },
        revision('7'),
      ),
    );
    const confirmAction = jest.fn(async () =>
      ok({
        kind: 'remote-unknown' as const,
        id: operationId,
        action: 'delete-account' as const,
        reason: 'coordination-unavailable' as const,
        updatedAt: generatedAt,
        localDataErased: true as const,
        remoteDeletionComplete: false as const,
        sameAccountRetryAvailable: false,
        externalSmsCopiesNotErased: true as const,
      }),
    );
    const { port } = createPort({
      getCurrentOperation: jest.fn(async () =>
        ok({
          kind: 'remote-pending' as const,
          id: operationId,
          action: 'delete-account' as const,
          reason: 'network-offline' as const,
          updatedAt: generatedAt,
        }),
      ),
      prepareAction,
      confirmAction,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-settings'));
    await fireEvent.press(await screen.findByTestId('live-settings-privacy'));

    expect(
      await screen.findByText('Online account data may remain'),
    ).toBeTruthy();
    expect(screen.queryByTestId('live-privacy-delete-account')).toBeNull();
    expect(screen.getByTestId('live-privacy-deletion-help')).toBeTruthy();
    expect(confirmAction).not.toHaveBeenCalled();
    await fireEvent.press(
      screen.getByTestId('live-privacy-pending-deletion-wipe'),
    );
    expect(
      await screen.findByText('Erase this device now?'),
    ).toBeTruthy();
    expect(confirmAction).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByTestId('live-privacy-confirm'));

    await waitFor(() => expect(confirmAction).toHaveBeenCalledTimes(1));
    expect(prepareAction).toHaveBeenCalledWith({
      kind: 'wipe-local-data',
      expectedRevision: '1',
    });
    expect(confirmAction).toHaveBeenCalledWith({
      handle: 'privacy-pending-wipe-review',
      expectedRevision: '7',
    });
    expect(
      await screen.findByText(/online deletion is not confirmed/u),
    ).toBeTruthy();
    await fireEvent.press(screen.getByTestId('live-privacy-deletion-help'));
    expect(await screen.findByTestId('live-help-legal-screen')).toBeTruthy();
  });

  it('offers same-account replay after deletion proof is unavailable', async () => {
    const operationId = 'privacy-delete-recovery' as PrivacyOperationId;
    const unknownReceipt = {
      kind: 'remote-unknown' as const,
      id: operationId,
      action: 'delete-account' as const,
      reason: 'coordination-unavailable' as const,
      updatedAt: generatedAt,
      localDataErased: true as const,
      remoteDeletionComplete: false as const,
      sameAccountRetryAvailable: false,
      externalSmsCopiesNotErased: true as const,
    };
    const retryableReceipt = {
      ...unknownReceipt,
      sameAccountRetryAvailable: true,
    };
    const continueWithGoogle = jest.fn(async () =>
      ok({
        kind: 'cleanup-pending' as const,
        operation: 'delete' as const,
        issue: {
          id: 'delete-replay' as IssueId,
          code: 'firebase-account-deleting' as const,
          severity: 'blocking' as const,
          blocks: ['activation'] as const,
        },
      }),
    );
    const { port } = createPort({
      getCurrentOperation: jest.fn(async () => ok(unknownReceipt)),
      getLatestDeletionReceipt: jest
        .fn()
        .mockResolvedValueOnce(ok(unknownReceipt))
        .mockResolvedValue(ok(retryableReceipt)),
      checkAccountDeletionStatus: jest.fn(async () =>
        ok(retryableReceipt),
      ),
      continueWithGoogle,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-settings'));
    await fireEvent.press(await screen.findByTestId('live-settings-privacy'));
    await fireEvent.press(
      await screen.findByRole('button', { name: 'Check account deletion' }),
    );
    await fireEvent.press(
      await screen.findByTestId('live-privacy-retry-deletion-google'),
    );

    await waitFor(() => expect(continueWithGoogle).toHaveBeenCalledTimes(1));
    expect(
      screen.getByText(/same-account recovery was checked/u),
    ).toBeTruthy();
  });

  it('hides destructive controls when durable cleanup state is unreadable', async () => {
    const { port } = createPort({
      getCurrentOperation: jest.fn(async () =>
        ok({
          kind: 'unavailable' as const,
          reason: 'coordination-unavailable' as const,
        }),
      ),
      getLatestDeletionReceipt: jest.fn(async () =>
        ok({
          kind: 'unavailable' as const,
          reason: 'coordination-unavailable' as const,
        }),
      ),
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-settings'));
    await fireEvent.press(await screen.findByTestId('live-settings-privacy'));

    expect(
      await screen.findAllByText('Saved cleanup state cannot be read'),
    ).not.toHaveLength(0);
    expect(screen.queryByTestId('live-privacy-delete-account')).toBeNull();
    expect(screen.queryByTestId('live-privacy-disconnect-contacts')).toBeNull();
  });

  it('fails closed when the deletion receipt cannot be loaded', async () => {
    const getLatestDeletionReceipt = jest.fn(async () => internalError());
    const { port } = createPort({ getLatestDeletionReceipt });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-settings'));
    await fireEvent.press(await screen.findByTestId('live-settings-privacy'));

    expect(await screen.findByText('Cleanup status is unavailable')).toBeTruthy();
    expect(screen.queryByTestId('live-privacy-delete-account')).toBeNull();
    expect(screen.queryByTestId('live-privacy-disconnect-contacts')).toBeNull();
  });

  it('repairs an unreadable Android cleanup journal only through server proof', async () => {
    const operationId = 'privacy-operation-repair' as PrivacyOperationId;
    const repairLifecycleState = jest.fn(async () =>
      ok({
        kind: 'local-wiping' as const,
        id: operationId,
        action: 'disconnect-contacts' as const,
        updatedAt: generatedAt,
      }),
    );
    const { port } = createPort({
      getAccount: jest.fn(async () =>
        ok({
          kind: 'cleanup-pending' as const,
          operation: 'repair' as const,
          issue: {
            id: 'cleanup-repair' as IssueId,
            code: 'coordination-unavailable' as const,
            severity: 'blocking' as const,
            blocks: ['test', 'activation', 'birthday'] as const,
          },
        }),
      ),
      getCurrentOperation: jest.fn(async () =>
        ok({
          kind: 'unavailable' as const,
          reason: 'coordination-unavailable' as const,
        }),
      ),
      repairLifecycleState,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-settings'));
    await fireEvent.press(await screen.findByTestId('live-settings-privacy'));

    expect(await screen.findByText('Repair saved cleanup')).toBeTruthy();
    expect(screen.queryByTestId('live-privacy-delete-account')).toBeNull();
    await fireEvent.press(
      screen.getByTestId('live-privacy-repair-disconnect-contacts'),
    );
    await waitFor(() => expect(repairLifecycleState).toHaveBeenCalledTimes(1));
    expect(repairLifecycleState).toHaveBeenCalledWith({
      kind: 'disconnect-contacts',
    });
    expect(
      await screen.findByText(
        'Cleanup was checked and safely continued where possible.',
      ),
    ).toBeTruthy();
  });

  it('shows a failed privacy operation as terminal', async () => {
    const prepareAction = jest.fn(async () =>
      ok(
        {
          handle: 'privacy-review-1' as PrivacyReviewHandle,
          kind: 'wipe-local-data' as const,
          titleKey: 'privacy.wipe.title',
          consequenceKeys: ['privacy.wipe.contacts'],
          preissuedPermitMayFinish: false,
          remoteConnectionRequired: false,
          externalSmsCopiesNotErased: true as const,
        },
        revision('4'),
      ),
    );
    const confirmAction = jest.fn(async () =>
      ok({
        kind: 'failed' as const,
        id: 'privacy-operation-1' as PrivacyOperationId,
        action: 'wipe-local-data' as const,
        reason: 'network-offline' as const,
        updatedAt: generatedAt,
      }),
    );
    const { port } = createPort({ prepareAction, confirmAction });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-settings'));
    await fireEvent.press(await screen.findByTestId('live-settings-privacy'));
    await fireEvent.press(
      await screen.findByTestId('live-privacy-wipe-local-data'),
    );
    await fireEvent.press(screen.getByTestId('live-privacy-prepare'));
    await fireEvent.press(await screen.findByTestId('live-privacy-confirm'));

    await waitFor(() =>
      expect(screen.getByText(/protected operation failed/u)).toBeTruthy(),
    );
    expect(screen.queryByTestId('live-privacy-refresh-operation')).toBeNull();
  });

  it('shows localized activity copy and hides raw ISO time as primary text', async () => {
    const listActivity = jest.fn(async () =>
      ok({
        items: [
          {
            id: 'activity-1' as import('../domain/shared/brand').ActivityId,
            kind: 'delivered' as const,
            occurredAt: generatedAt,
            actionable: false,
          },
        ],
      }),
    );
    const { port } = createPort({ listActivity });

    await renderLiveApp(port);
    await waitFor(() =>
      expect(screen.getByTestId('live-home-activity')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-home-activity'));

    await waitFor(() =>
      expect(screen.getByText('Carrier reported delivered')).toBeTruthy(),
    );
    expect(screen.queryByText('delivered')).toBeNull();
    expect(screen.queryByText('2026-07-12T07:00:00Z')).toBeNull();
  });

  it('opens an actionable attention repair with the current native revision only', async () => {
    const listIssues = jest.fn(async () =>
      ok([
        {
          id: 'issue-1' as IssueId,
          code: 'notification-permission-missing' as const,
          severity: 'blocking' as const,
          blocks: ['birthday' as const],
          action: {
            kind: 'native-action' as const,
            handle: 'action-1' as ActionHandle,
            labelKey: 'settings.notifications',
          },
        },
      ]),
    );
    const performAction = jest.fn(async () =>
      ok({ kind: 'cancelled' as const }, revision('2')),
    );
    const { port } = createPort({ listIssues, performAction });

    await renderLiveApp(port);
    await waitFor(() =>
      expect(screen.getByTestId('live-home-attention')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-home-attention'));
    await waitFor(() =>
      expect(screen.getByTestId('live-attention-action-issue-1')).toBeTruthy(),
    );
    expect(
      screen.getByText(/Allow notifications to see reminders/u),
    ).toBeTruthy();
    expect(
      screen.getByText('Technical code: notification-permission-missing'),
    ).toBeTruthy();
    expect(screen.queryByText('notification-permission-missing')).toBeNull();
    await fireEvent.press(screen.getByTestId('live-attention-action-issue-1'));

    await waitFor(() =>
      expect(screen.getByText(/No fix is assumed/u)).toBeTruthy(),
    );
    expect(performAction).toHaveBeenCalledTimes(1);
    expect(performAction).toHaveBeenCalledWith({
      handle: 'action-1',
      expectedRevision: '1',
    });
  });

  it('requires explicit phone and leap-day birthday choices with revision CAS', async () => {
    const phoneId = 'phone-1' as PhoneChoiceId;
    const birthdayId = 'birthday-1' as BirthdayChoiceId;
    const detailed: ContactDetail = {
      ...contactDetail(),
      phoneChoices: [
        {
          id: phoneId,
          maskedDisplay: '•••• 4321',
          sourceLabel: 'Google mobile',
          selectable: true,
        },
      ],
      birthdayChoices: [
        {
          id: birthdayId,
          displayLabel: '29 February',
          hasYear: false,
          selectable: true,
          issue: 'leap-policy-required',
        },
      ],
    };
    const getPerson = jest.fn(async () => ok(detailed));
    const choosePhone = jest.fn(async () => ok(detailed));
    const chooseBirthday = jest.fn(async () => ok(detailed));
    const { port } = createPort({ getPerson, choosePhone, chooseBirthday });

    await renderLiveApp(port);
    await waitFor(() =>
      expect(screen.getByTestId('live-tab-people')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-tab-people'));
    await waitFor(() =>
      expect(screen.getByTestId('live-person-contact-live-1')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-person-contact-live-1'));
    await waitFor(() =>
      expect(screen.getByTestId('live-choose-phone-phone-1')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-choose-phone-phone-1'));
    await fireEvent.press(screen.getByTestId('live-confirm-choice'));
    await waitFor(() => expect(choosePhone).toHaveBeenCalledTimes(1));
    expect(choosePhone).toHaveBeenCalledWith({
      contactId: 'contact-live-1',
      phoneId: 'phone-1',
      expectedRevision: '1',
    });

    await fireEvent.press(
      screen.getByTestId('live-choose-birthday-birthday-1'),
    );
    await fireEvent.press(
      screen.getByRole('radio', { name: 'Use 28 February' }),
    );
    await fireEvent.press(screen.getByTestId('live-confirm-choice'));
    await waitFor(() => expect(chooseBirthday).toHaveBeenCalledTimes(1));
    expect(chooseBirthday).toHaveBeenCalledWith({
      contactId: 'contact-live-1',
      birthdayId: 'birthday-1',
      leapPolicy: 'feb-28',
      expectedRevision: '1',
    });
  });

  it('confirms the exact Android approval review handle before saving', async () => {
    const prepareApprovals = jest.fn(async () =>
      ok(
        {
          handle: 'approval-review-1' as ApprovalReviewHandle,
          items: [
            {
              platform: 'android' as const,
              contactId,
              recipient: 'Live Contact' as PrivateDisplayName,
              maskedPhone: '•••• 4321',
              birthdayLabel: '18 July',
              exactText: 'Happy birthday!' as PrivateMessageText,
              windowLabel: '09:00–11:00',
              simLabel: 'SIM 1',
              segmentCount: 1,
              chargeDisclosure: 'Carrier charges may apply.',
              consentDisclosure: 'You approved this exact message.',
            },
          ],
          readyCount: 1,
          blockedCount: 0,
          explicitConfirmationRequired: true as const,
        },
        revision('6'),
      ),
    );
    const confirmApprovals = jest.fn(async () =>
      ok({
        platform: 'android' as const,
        desired: 'on' as const,
        effective: 'active' as const,
        readiness,
      }),
    );
    const { port } = createPort({ prepareApprovals, confirmApprovals });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-people'));
    await fireEvent.press(
      await screen.findByTestId('live-person-contact-live-1'),
    );
    await fireEvent.press(await screen.findByTestId('live-review-approval'));
    await waitFor(() =>
      expect(screen.getByText('Happy birthday!')).toBeTruthy(),
    );
    expect(confirmApprovals).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByTestId('live-confirm-approval'));

    await waitFor(() => expect(confirmApprovals).toHaveBeenCalledTimes(1));
    expect(confirmApprovals).toHaveBeenCalledWith({
      handle: 'approval-review-1',
      expectedRevision: '6',
    });
  });

  it('previews and shares content-free diagnostics with revision CAS', async () => {
    const previewDiagnostics = jest.fn(async () =>
      ok(
        {
          buildLabel: '0.1.0 test',
          androidOrIosVersionLabel: 'Android 16',
          capabilityCodes: ['distribution-channel-unapproved' as const],
          transitionCount: 4,
          excludesPrivateContent: true as const,
        },
        revision('5'),
      ),
    );
    const shareDiagnostics = jest.fn(async () =>
      ok({ kind: 'cancelled' as const }),
    );
    const { port } = createPort({ previewDiagnostics, shareDiagnostics });

    await renderLiveApp(port);
    await waitFor(() =>
      expect(screen.getByTestId('live-tab-settings')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-tab-settings'));
    await waitFor(() =>
      expect(screen.getByTestId('live-settings-diagnostics')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-settings-diagnostics'));
    await waitFor(() =>
      expect(screen.getByTestId('live-diagnostics-preview')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-diagnostics-preview'));
    await waitFor(() =>
      expect(screen.getByTestId('live-diagnostics-share')).toBeTruthy(),
    );
    expect(screen.getAllByText(/excludes names, phone numbers/u)).toHaveLength(
      2,
    );
    await fireEvent.press(screen.getByTestId('live-diagnostics-share'));

    await waitFor(() =>
      expect(screen.getByText(/sharing was cancelled/u)).toBeTruthy(),
    );
    expect(shareDiagnostics).toHaveBeenCalledWith({ expectedRevision: '5' });
  });

  it('consumes cold and warm native routes into their safe review screens', async () => {
    const getPendingRoute = jest
      .fn()
      .mockResolvedValueOnce(
        ok({
          kind: 'automation-review' as const,
          routeId:
            '9c65f8be-f37d-4e57-a1c0-b93ddc51658b' as import('../domain/shared/brand').NativeRouteId,
          source: 'birthday-reminder' as const,
        }),
      )
      .mockResolvedValueOnce(
        ok({
          kind: 'attention' as const,
          routeId:
            'a4f2a2c0-8df3-4b2e-b9e4-661a2050d4a1' as import('../domain/shared/brand').NativeRouteId,
          source: 'attention' as const,
        }),
      );
    const harness = createPort({ getPendingRoute });

    await renderLiveApp(harness.port);
    await waitFor(() =>
      expect(screen.getByTestId('live-automation-screen')).toBeTruthy(),
    );
    expect(screen.queryByTestId('live-message-screen')).toBeNull();
    expect(getPendingRoute).toHaveBeenCalledTimes(1);

    await fireEvent.press(screen.getByRole('button', { name: 'Back' }));
    await waitFor(() =>
      expect(screen.getByTestId('live-home-screen')).toBeTruthy(),
    );
    await act(async () => {
      harness.emitRoute({ kind: 'available' });
    });
    await waitFor(() =>
      expect(screen.getByTestId('live-attention-screen')).toBeTruthy(),
    );
    expect(getPendingRoute).toHaveBeenCalledTimes(2);
  });

  it('previews and saves a strict Android policy review handle', async () => {
    const configured = {
      kind: 'configured' as const,
      draft: {
        primaryStart: '09:00' as const,
        primaryEnd: '11:00' as const,
        latePolicy: { kind: 'none' as const },
        dailyCap: 10,
      },
    };
    const getPolicyEditor = jest.fn(async () => ok(configured));
    const previewPolicy = jest.fn(async () =>
      ok(
        {
          kind: 'valid' as const,
          handle:
            'policy-review-1' as import('../domain/shared/brand').PolicyReviewHandle,
          summary: 'Protected 400-day simulation',
          maximumPlannedInLocalDay: 5,
          maximumPlannedInRolling24Hours: 5,
          simulatedDays: 400,
        },
        revision('7'),
      ),
    );
    const savePolicy = jest.fn(async () =>
      ok({
        platform: 'android' as const,
        desired: 'on' as const,
        effective: 'active' as const,
        readiness,
      }),
    );
    const { port } = createPort({
      getPolicyEditor,
      previewPolicy,
      savePolicy,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-home-automation'));
    await fireEvent.changeText(
      await screen.findByTestId('live-policy-start'),
      '10:00',
    );
    await fireEvent.changeText(screen.getByTestId('live-policy-end'), '12:00');
    await fireEvent.changeText(
      screen.getByTestId('live-policy-daily-cap'),
      '5',
    );
    await fireEvent.press(screen.getByTestId('live-policy-preview'));
    await fireEvent.press(await screen.findByTestId('live-policy-save'));

    await waitFor(() => expect(savePolicy).toHaveBeenCalledTimes(1));
    expect(previewPolicy).toHaveBeenCalledWith({
      draft: {
        primaryStart: '10:00',
        primaryEnd: '12:00',
        latePolicy: { kind: 'none' },
        dailyCap: 5,
      },
      expectedRevision: '1',
    });
    expect(savePolicy).toHaveBeenCalledWith({
      handle: 'policy-review-1',
      expectedRevision: '7',
    });
  });

  it('turns policy validation codes into actionable primary copy', async () => {
    const configured = {
      kind: 'configured' as const,
      draft: {
        primaryStart: '09:00' as const,
        primaryEnd: '11:00' as const,
        latePolicy: { kind: 'none' as const },
        dailyCap: 10,
      },
    };
    const previewPolicy = jest.fn(async () => internalError());
    const { port } = createPort({
      getPolicyEditor: jest.fn(async () => ok(configured)),
      previewPolicy,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-home-automation'));
    await fireEvent.changeText(
      await screen.findByTestId('live-policy-start'),
      'not-a-time',
    );
    await fireEvent.press(screen.getByTestId('live-policy-preview'));

    expect(
      await screen.findByText(/Enter a valid local-time window/u),
    ).toBeTruthy();
    expect(screen.queryByText('invalid-window')).toBeNull();
    expect(previewPolicy).not.toHaveBeenCalled();
  });

  it('selects only Ready and Off people on the visible page before confirmation', async () => {
    const secondId = 'contact-live-2' as ContactId;
    const readyOff = (id: ContactId, name: string): ContactSummary => ({
      ...contactSummary(),
      id,
      displayName: name as PrivateDisplayName,
      enrollment: { kind: 'off' },
    });
    const candidates = [
      readyOff(contactId, 'First Ready'),
      readyOff(secondId, 'Second Ready'),
    ];
    const prepareEnrollmentReview = jest.fn(async () =>
      ok(
        {
          handle:
            'enrollment-review-1' as import('../domain/shared/brand').EnrollmentReviewHandle,
          recipients: candidates,
          readyCount: 2,
          attentionCount: 0,
          explicitConfirmationRequired: true as const,
        },
        revision('4'),
      ),
    );
    const confirmEnrollment = jest.fn(async () =>
      ok({
        changedContactIds: [contactId, secondId],
        invalidatedApprovalCount: 0,
      }),
    );
    const { port } = createPort({
      listPeople: jest.fn(async () =>
        ok({ items: candidates, totalCount: candidates.length }),
      ),
      prepareEnrollmentReview,
      confirmEnrollment,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-people'));
    await fireEvent.press(
      await screen.findByTestId('live-people-select-page-ready'),
    );
    expect((await screen.findAllByText('First Ready')).length).toBeGreaterThan(
      0,
    );
    expect(screen.getAllByText('Second Ready').length).toBeGreaterThan(0);
    expect(confirmEnrollment).not.toHaveBeenCalled();
    await fireEvent.press(
      screen.getByTestId('live-people-confirm-page-enrollment'),
    );

    await waitFor(() => expect(confirmEnrollment).toHaveBeenCalledTimes(1));
    expect(prepareEnrollmentReview).toHaveBeenCalledWith({
      contactIds: ['contact-live-1', 'contact-live-2'],
      expectedRevision: '1',
    });
    expect(confirmEnrollment).toHaveBeenCalledWith({
      handle: 'enrollment-review-1',
      expectedRevision: '4',
    });
  });

  it('debounces People search and never loads the superseded query', async () => {
    const listPeople = jest.fn(async () =>
      ok({ items: [contactSummary()], totalCount: 1 }),
    );
    const { port } = createPort({ listPeople });
    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-people'));
    const search = await screen.findByTestId('live-people-search');
    await waitFor(() => expect(listPeople).toHaveBeenCalledTimes(1));
    jest.useFakeTimers();

    await fireEvent.changeText(search, 'old query');
    await act(async () => {
      jest.advanceTimersByTime(100);
      await Promise.resolve();
    });
    await fireEvent.changeText(search, 'new query');
    await act(async () => {
      jest.advanceTimersByTime(275);
      await Promise.resolve();
    });

    await waitFor(() => expect(listPeople).toHaveBeenCalledTimes(2));
    jest.useRealTimers();
    expect(listPeople).not.toHaveBeenCalledWith(
      expect.objectContaining({ search: 'old query' }),
    );
    expect(listPeople).toHaveBeenLastCalledWith({
      filter: 'all',
      pageSize: 50,
      search: 'new query',
    });
  });

  it('validates and confirms an iOS approval with explicit composer disclosure', async () => {
    const prepareApprovals = jest.fn(async () =>
      ok(
        {
          handle: 'approval-review-ios' as ApprovalReviewHandle,
          items: [
            {
              platform: 'ios' as const,
              contactId,
              recipient: 'Live Contact' as PrivateDisplayName,
              maskedPhone: '•••• 4321',
              birthdayLabel: '18 July',
              exactText: 'Happy birthday!' as PrivateMessageText,
              deliveryMode: 'user-controlled-composer' as const,
              consentDisclosure: 'Review and tap Send yourself.',
            },
          ],
          readyCount: 1,
          blockedCount: 0,
          explicitConfirmationRequired: true as const,
        },
        revision('6'),
      ),
    );
    const confirmApprovals = jest.fn(async () => ok(iosHome.automation));
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(iosHome)),
      listPeople: jest.fn(async () =>
        ok({ items: [contactSummary()], totalCount: 1 }),
      ),
      getPerson: jest.fn(async () => ok(contactDetail())),
      prepareApprovals,
      confirmApprovals,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-people'));
    await fireEvent.press(
      await screen.findByTestId('live-person-contact-live-1'),
    );
    await fireEvent.press(await screen.findByTestId('live-review-approval'));
    expect(
      await screen.findByText(/editable system Messages screen/u),
    ).toBeTruthy();
    await fireEvent.press(screen.getByTestId('live-confirm-approval'));

    await waitFor(() => expect(confirmApprovals).toHaveBeenCalledTimes(1));
    expect(confirmApprovals).toHaveBeenCalledWith({
      handle: 'approval-review-ios',
      expectedRevision: '6',
    });
  });

  it('activates and pauses iOS reminders only through explicit native reviews', async () => {
    const notConfigured: HomeProjection = {
      ...iosHome,
      automation: {
        platform: 'ios',
        desired: 'paused',
        effective: 'not-configured',
        readiness: iosReadiness,
      },
    };
    const paused: HomeProjection = {
      ...iosHome,
      automation: {
        platform: 'ios',
        desired: 'paused',
        effective: 'paused',
        readiness: iosReadiness,
      },
    };
    const getHome = jest
      .fn()
      .mockResolvedValueOnce(ok(notConfigured))
      .mockResolvedValueOnce(ok(notConfigured))
      .mockResolvedValueOnce(ok(iosHome, revision('2')))
      .mockResolvedValue(ok(paused, revision('3')));
    const prepareActivation = jest.fn(async () =>
      ok(
        {
          platform: 'ios' as const,
          handle: 'ios-activation-review' as ActivationReviewHandle,
          reminderRecipientCount: 1,
          deliveryMode: 'user-controlled-composer' as const,
          limitationsDisclosure: 'Reminders are best effort.',
        },
        revision('7'),
      ),
    );
    const activate = jest.fn(async () => ok(iosHome.automation));
    const pauseAll = jest.fn(async () => ok(paused.automation));
    const companionPort: LiveCompanionPort = {
      canOpenComposer: jest.fn(async () => false),
      getReminderStatus: jest.fn(async () => ({
        kind: 'ok' as const,
        value: {
          authorization: 'authorized' as const,
          failedCount: 0,
          kind: 'ok' as const,
          plannedDateCount: 1,
          scheduledCount: 1,
          truncated: false,
        },
      })),
      openNotificationSettings: jest.fn(),
      openUserConfirmedComposer: jest.fn(),
      prepareComposerReview: jest.fn(),
      requestReminderAuthorization: jest.fn(),
    };
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome,
      getNextComposerProposal: jest.fn(async () =>
        ok({ kind: 'none' as const }),
      ),
      prepareActivation,
      activate,
      pauseAll,
    });

    await renderLiveApp(port, companionPort);
    await fireEvent.press(await screen.findByTestId('live-home-automation'));
    await fireEvent.press(
      await screen.findByTestId('live-ios-review-activation'),
    );
    expect(screen.getByText(/iPhone never sends automatically/u)).toBeTruthy();
    await fireEvent.press(screen.getByTestId('live-ios-confirm-activation'));
    await fireEvent.press(await screen.findByTestId('live-ios-review-pause'));
    await fireEvent.press(screen.getByTestId('live-ios-confirm-pause'));

    await waitFor(() => expect(pauseAll).toHaveBeenCalledTimes(1));
    expect(activate).toHaveBeenCalledWith({
      handle: 'ios-activation-review',
      expectedRevision: '7',
    });
    expect(pauseAll).toHaveBeenCalledWith({ expectedRevision: '2' });
  });

  it('opens Settings for denied iOS notifications and reports unscheduled reminders', async () => {
    const openNotificationSettings = jest.fn(async () => ({
      kind: 'ok' as const,
      value: null,
    }));
    const requestReminderAuthorization = jest.fn();
    const companionPort: LiveCompanionPort = {
      canOpenComposer: jest.fn(async () => false),
      getReminderStatus: jest.fn(async () => ({
        kind: 'ok' as const,
        value: {
          authorization: 'denied' as const,
          earliestUnscheduledCivilDate:
            '2026-07-19' as import('../domain/shared/temporal').LocalDate,
          failedCount: 2,
          kind: 'ok' as const,
          plannedDateCount: 3,
          scheduledCount: 1,
          truncated: true,
        },
      })),
      openNotificationSettings,
      openUserConfirmedComposer: jest.fn(),
      prepareComposerReview: jest.fn(),
      requestReminderAuthorization,
    };
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(iosHome)),
      getNextComposerProposal: jest.fn(async () =>
        ok({ kind: 'none' as const }),
      ),
    });

    await renderLiveApp(port, companionPort);
    await fireEvent.press(await screen.findByTestId('live-home-automation'));
    expect(
      await screen.findByText('2 reminders could not be scheduled'),
    ).toBeTruthy();
    expect(screen.getByText('19 July 2026')).toBeTruthy();
    expect(screen.queryByTestId('live-policy-daily-cap')).toBeNull();
    await fireEvent.press(screen.getByTestId('live-reminder-settings'));

    await waitFor(() =>
      expect(openNotificationSettings).toHaveBeenCalledTimes(1),
    );
    expect(requestReminderAuthorization).not.toHaveBeenCalled();
  });

  it('sends a today decision only through native prepare and confirm reviews', async () => {
    const todayHome: HomeProjection = {
      ...liveHome('active'),
      next: {
        occurrenceId: 'occurrence-today' as OccurrenceId,
        recipient: 'Live Contact' as PrivateDisplayName,
        localDate:
          '2026-07-12' as import('../domain/shared/temporal').LocalDate,
        windowLabel: '09:00–11:00',
        maskedPhone: '•••• 4321',
      },
      counts: { ...liveHome('active').counts, today: 1 },
    };
    const prepareTodayOccurrence = jest.fn(async () =>
      ok(
        {
          handle:
            'today-review-1' as import('../domain/shared/brand').TodayOccurrenceReviewHandle,
          recipient: 'Live Contact' as PrivateDisplayName,
          exactText: 'Happy birthday!' as PrivateMessageText,
          choice: 'send-through-normal-path' as const,
          limitationsDisclosure:
            'The protected path may still be blocked and delivery is not promised.',
        },
        revision('5'),
      ),
    );
    const confirmTodayOccurrence = jest.fn(async () =>
      ok(todayHome.automation),
    );
    const { port } = createPort({
      getHome: jest.fn(async () => ok(todayHome)),
      prepareTodayOccurrence,
      confirmTodayOccurrence,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-home-review-today'));
    expect(await screen.findByText('Happy birthday!')).toBeTruthy();
    expect(confirmTodayOccurrence).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByTestId('live-home-confirm-today'));

    await waitFor(() =>
      expect(confirmTodayOccurrence).toHaveBeenCalledTimes(1),
    );
    expect(prepareTodayOccurrence).toHaveBeenCalledWith({
      occurrenceId: 'occurrence-today',
      expectedRevision: '1',
    });
    expect(confirmTodayOccurrence).toHaveBeenCalledWith({
      handle: 'today-review-1',
      expectedRevision: '5',
    });
  });

  it('shows the durable deletion receipt without claiming external copies were erased', async () => {
    const drainingReceipt = {
      kind: 'remote-draining' as const,
      id: 'privacy-operation-1' as PrivacyOperationId,
      action: 'delete-account' as const,
      updatedAt: generatedAt,
      localDataErased: true as const,
      remoteDeletionComplete: false as const,
      externalSmsCopiesNotErased: true as const,
    };
    const completedReceipt = {
      kind: 'complete' as const,
      id: 'privacy-operation-1' as PrivacyOperationId,
      action: 'delete-account' as const,
      completedAt: generatedAt,
      localDataErased: true as const,
      remoteDeletionComplete: true as const,
      externalSmsCopiesNotErased: true as const,
    };
    const getLatestDeletionReceipt = jest
      .fn()
      .mockResolvedValueOnce(ok(drainingReceipt))
      .mockResolvedValue(ok(completedReceipt));
    const checkAccountDeletionStatus = jest.fn(async () =>
      ok(completedReceipt),
    );
    const { port } = createPort({
      checkAccountDeletionStatus,
      getLatestDeletionReceipt,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-settings'));
    await fireEvent.press(await screen.findByTestId('live-settings-privacy'));

    expect(await screen.findByText('Saved message templates')).toBeTruthy();
    expect(screen.getByText('contacts-v1')).toBeTruthy();
    expect(screen.getByText(/at most 30 days/u)).toBeTruthy();
    expect(screen.getByText(/up to 400 days/u)).toBeTruthy();
    expect(await screen.findByText(/Local app data is erased/u)).toBeTruthy();
    expect(screen.getByText(/iCloud or other backups/u)).toBeTruthy();
    await fireEvent.press(
      screen.getByRole('button', { name: 'Check account deletion' }),
    );
    expect(
      await screen.findByText(
        'A deletion request from this device is complete',
      ),
    ).toBeTruthy();
    expect(checkAccountDeletionStatus).toHaveBeenCalledTimes(1);
    expect(getLatestDeletionReceipt).toHaveBeenCalledTimes(2);
  });

  it('keeps missing deletion completion proof distinct from in-progress proof', async () => {
    const drainingReceipt = {
      kind: 'remote-draining' as const,
      id: 'privacy-operation-1' as PrivacyOperationId,
      action: 'delete-account' as const,
      updatedAt: generatedAt,
      localDataErased: true as const,
      remoteDeletionComplete: false as const,
      externalSmsCopiesNotErased: true as const,
    };
    const { port } = createPort({
      getLatestDeletionReceipt: jest.fn(async () => ok(drainingReceipt)),
      checkAccountDeletionStatus: jest.fn(async () =>
        ok({
          kind: 'unavailable' as const,
          reason: 'coordination-unavailable' as const,
        }),
      ),
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-settings'));
    await fireEvent.press(await screen.findByTestId('live-settings-privacy'));
    await fireEvent.press(
      await screen.findByRole('button', { name: 'Check account deletion' }),
    );

    expect(
      await screen.findByText(/Server completion proof is unavailable/u),
    ).toBeTruthy();
  });

  it('keeps the deletion stage neutral throughout blocked setup', async () => {
    const deletingAccount: AccountProjection = {
      kind: 'cleanup-pending',
      operation: 'delete',
      issue: {
        id: 'deleting-account' as IssueId,
        code: 'firebase-account-deleting',
        severity: 'blocking',
        blocks: ['activation'],
      },
    };
    const deletingBootstrap: BootstrapProjection = {
      ...completeBootstrap,
      account: deletingAccount,
      setupStep: 'compatibility',
    };
    const deletingSetup: SetupProjection = {
      step: 'compatibility',
      eligibility: completeBootstrap.eligibility,
      account: deletingAccount,
      contacts: {
        kind: 'fresh',
        completedAt: generatedAt,
        contactCount: 0,
      },
      readiness,
      automation: {
        platform: 'android',
        desired: 'paused',
        effective: 'deleting',
        readiness,
      },
    };
    const checkAccountDeletionStatus = jest.fn(async () =>
      ok({
        kind: 'complete' as const,
        id: 'privacy-operation-1' as PrivacyOperationId,
        action: 'delete-account' as const,
        completedAt: generatedAt,
        localDataErased: true as const,
        remoteDeletionComplete: true as const,
        externalSmsCopiesNotErased: true as const,
      }),
    );
    const { port } = createPort({
      checkAccountDeletionStatus,
      getBootstrap: jest.fn(async () => ok(deletingBootstrap)),
      getSetup: jest.fn(async () => ok(deletingSetup)),
    });

    await renderLiveApp(port);

    expect(
      await screen.findByText(/Account deletion cleanup needs attention/u),
    ).toBeTruthy();
    expect(screen.getByText(/no stage is inferred/u)).toBeTruthy();
    expect(screen.queryByText(/Local app data is erased/u)).toBeNull();
    await fireEvent.press(
      screen.getByRole('button', { name: 'Check account deletion' }),
    );
    await waitFor(() =>
      expect(checkAccountDeletionStatus).toHaveBeenCalledTimes(1),
    );
    expect(
      screen.getByText(
        /server data associated with that request were verified deleted/u,
      ),
    ).toBeTruthy();
    await fireEvent.press(screen.getByTestId('live-setup-help-legal'));
    expect(await screen.findByTestId('live-help-legal-screen')).toBeTruthy();
    expect(screen.getByText('Help, legal and about')).toBeTruthy();
  });

  it('keeps exact-account deletion replay reachable after restart into blocked setup', async () => {
    const deletingAccount: AccountProjection = {
      kind: 'cleanup-pending',
      operation: 'delete',
      issue: {
        id: 'deleting-account-recovery' as IssueId,
        code: 'firebase-account-deleting',
        severity: 'blocking',
        blocks: ['activation'],
      },
    };
    const deletingBootstrap: BootstrapProjection = {
      ...completeBootstrap,
      account: deletingAccount,
      setupStep: 'compatibility',
    };
    const deletingSetup: SetupProjection = {
      step: 'compatibility',
      eligibility: completeBootstrap.eligibility,
      account: deletingAccount,
      contacts: {
        kind: 'authorization-required',
        reason: 'contacts-authorization-required',
      },
      readiness,
      automation: {
        platform: 'android',
        desired: 'paused',
        effective: 'deleting',
        readiness,
      },
    };
    const operationId = 'privacy-restart-recovery' as PrivacyOperationId;
    const retryableUnknown = {
      kind: 'remote-unknown' as const,
      id: operationId,
      action: 'delete-account' as const,
      reason: 'coordination-unavailable' as const,
      updatedAt: generatedAt,
      localDataErased: true as const,
      remoteDeletionComplete: false as const,
      sameAccountRetryAvailable: true,
      externalSmsCopiesNotErased: true as const,
    };
    const continueWithGoogle = jest.fn(async () => ok(deletingAccount));
    const { port } = createPort({
      checkAccountDeletionStatus: jest.fn(async () => ok(retryableUnknown)),
      continueWithGoogle,
      getBootstrap: jest.fn(async () => ok(deletingBootstrap)),
      getSetup: jest.fn(async () => ok(deletingSetup)),
    });

    await renderLiveApp(port);
    await fireEvent.press(
      await screen.findByRole('button', { name: 'Check account deletion' }),
    );
    await fireEvent.press(
      await screen.findByTestId('live-setup-retry-deletion-google'),
    );

    await waitFor(() => expect(continueWithGoogle).toHaveBeenCalledTimes(1));
    expect(
      screen.getByText(/same-account recovery was checked/u),
    ).toBeTruthy();
  });

  it('keeps exact-account lifecycle repair reachable from blocked setup', async () => {
    const repairingAccount: AccountProjection = {
      kind: 'cleanup-pending',
      operation: 'repair',
      issue: {
        id: 'repairing-lifecycle-journal' as IssueId,
        code: 'coordination-unavailable',
        severity: 'blocking',
        blocks: ['test', 'activation', 'birthday'],
      },
    };
    const repairingBootstrap: BootstrapProjection = {
      ...completeBootstrap,
      account: repairingAccount,
      setupStep: 'google-account',
    };
    const repairingSetup: SetupProjection = {
      step: 'google-account',
      eligibility: completeBootstrap.eligibility,
      account: repairingAccount,
      contacts: {
        kind: 'fresh',
        completedAt: generatedAt,
        contactCount: 1,
      },
      readiness,
      automation: {
        platform: 'android',
        desired: 'paused',
        effective: 'paused-repair',
        readiness,
      },
    };
    const continueWithGoogle = jest.fn(async () => ok(repairingAccount));
    const repairLifecycleState = jest.fn(async () =>
      ok({
        kind: 'complete' as const,
        id: `privacy_${'f'.repeat(32)}` as PrivacyOperationId,
        action: 'disconnect-contacts' as const,
        completedAt: generatedAt,
        externalSmsCopiesNotErased: true as const,
      }),
    );
    const { port } = createPort({
      continueWithGoogle,
      getBootstrap: jest.fn(async () => ok(repairingBootstrap)),
      getSetup: jest.fn(async () => ok(repairingSetup)),
      repairLifecycleState,
    });

    await renderLiveApp(port);

    expect(await screen.findByTestId('live-setup-repair-reauth')).toBeTruthy();
    expect(screen.queryByTestId('live-setup-action')).toBeNull();
    expect(
      screen.queryByTestId('live-setup-repair-disconnect-contacts'),
    ).toBeNull();

    await fireEvent.press(screen.getByTestId('live-setup-repair-reauth'));
    await waitFor(() => expect(continueWithGoogle).toHaveBeenCalledTimes(1));
    expect(
      await screen.findByText(
        'Account verified. Choose only the cleanup you previously started.',
      ),
    ).toBeTruthy();

    await fireEvent.press(
      screen.getByTestId('live-setup-repair-disconnect-contacts'),
    );
    await waitFor(() => expect(repairLifecycleState).toHaveBeenCalledTimes(1));
    expect(repairLifecycleState).toHaveBeenCalledWith({
      kind: 'disconnect-contacts',
    });
    expect(
      await screen.findByText('The protected operation is complete.'),
    ).toBeTruthy();
  });

  it('routes incomplete native state to setup and fails unsupported actions closed', async () => {
    const incompleteBootstrap: BootstrapProjection = {
      ...completeBootstrap,
      setupStep: 'compatibility',
    };
    const setupProjection: SetupProjection = {
      step: 'compatibility',
      eligibility: completeBootstrap.eligibility,
      account: { kind: 'signed-out', retainedSetup: 'none' },
      contacts: {
        kind: 'authorization-required',
        reason: 'contacts-authorization-required',
      },
      readiness,
      automation: {
        platform: 'android',
        desired: 'paused',
        effective: 'not-configured',
        readiness,
      },
    };
    const getHome = jest.fn(async () => ok(liveHome('active')));
    const refreshCompatibility = jest.fn(async () => internalError());
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(incompleteBootstrap)),
      getSetup: jest.fn(async () => ok(setupProjection)),
      getHome,
      refreshCompatibility,
    });

    await renderLiveApp(port);
    await waitFor(() =>
      expect(screen.getByTestId('live-setup-screen')).toBeTruthy(),
    );
    expect(screen.queryByTestId('live-app-shell')).toBeNull();

    await fireEvent.press(screen.getByTestId('live-setup-action'));

    await waitFor(() =>
      expect(screen.getByText('Action not completed')).toBeTruthy(),
    );
    expect(refreshCompatibility).toHaveBeenCalledTimes(1);
    expect(getHome).not.toHaveBeenCalled();
    expect(screen.getByText(/Nothing was changed/u)).toBeTruthy();
  });
});
