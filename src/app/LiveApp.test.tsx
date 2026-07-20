import React from 'react';
import { AppState, BackHandler, Linking, StyleSheet } from 'react-native';
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
  TestReviewHandle,
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
import { appI18n } from '../localization/i18n';
import { resources } from '../localization/resources';
import { BirthdayAutopilotApp } from './AppRoot';

jest.mock('react-native-localize', () => ({
  getLocales: () => [{ languageCode: 'en' }],
}));

jest.mock('react-native-safe-area-context', () => {
  const TestReact = require('react');
  const { View } = require('react-native');
  const insets = { top: 0, right: 0, bottom: 0, left: 0 };
  const frame = { x: 0, y: 0, width: 390, height: 844 };
  const SafeAreaInsetsContext = TestReact.createContext(insets);
  const SafeAreaFrameContext = TestReact.createContext(frame);
  return {
    SafeAreaFrameContext,
    SafeAreaInsetsContext,
    SafeAreaProvider: (props: { children?: unknown }) =>
      TestReact.createElement(
        SafeAreaFrameContext.Provider,
        { value: frame },
        TestReact.createElement(
          SafeAreaInsetsContext.Provider,
          { value: insets },
          props.children,
        ),
      ),
    SafeAreaView: (props: { children?: unknown; [key: string]: unknown }) => {
      const { children, ...viewProps } = props;
      return TestReact.createElement(View, viewProps, children);
    },
    initialWindowMetrics: { frame, insets },
    useSafeAreaFrame: () => frame,
    useSafeAreaInsets: () => insets,
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

const openSettingsDestination = async (
  destination: 'automation' | 'message' | 'schedule',
) => {
  await fireEvent.press(await screen.findByTestId('live-tab-settings'));
  await fireEvent.press(
    await screen.findByTestId(`live-settings-${destination}`),
  );
};

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

const androidAccountForSender = (
  kind: 'test-only' | 'paused-repair' | 'automation-active',
): AccountProjection => ({
  kind: 'connected',
  displayEmail: 'user@example.test' as PrivateEmail,
  sender: {
    platform: 'android',
    kind,
    epochLabel: 'This device',
  },
});

const latestPassedTest = {
  platform: 'android' as const,
  phase: 'passed' as const,
  updatedAt: generatedAt,
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

const nextBirthday = {
  occurrenceId: 'occurrence-next' as OccurrenceId,
  recipient: 'Live Contact' as PrivateDisplayName,
  localDate: '2026-07-18' as import('../domain/shared/temporal').LocalDate,
  windowLabel: '09:00–11:00',
  maskedPhone: '•••• 4321',
  exactText: 'Happy birthday!' as PrivateMessageText,
};

const iosDueHome: HomeProjection = {
  ...iosHome,
  counts: {
    ...iosHome.counts,
    today: 1,
  },
  next: {
    ...nextBirthday,
    occurrenceId: 'occurrence-1' as OccurrenceId,
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

const activationReadyHome: HomeProjection = {
  ...liveHome('active'),
  automation: {
    platform: 'android',
    desired: 'paused',
    effective: 'test-only',
    readiness,
  },
};

const completeSetup: SetupProjection = {
  step: 'complete',
  initialActivationCompleted: true,
  eligibility: completeBootstrap.eligibility,
  account,
  contacts: {
    kind: 'fresh',
    completedAt: generatedAt,
    contactCount: 1,
  },
  readiness,
  automation: liveHome('active').automation,
};

const configuredMessage = {
  kind: 'configured' as const,
  draft: {
    language: 'en' as const,
    tone: 'warm' as const,
    placeholderMode: {
      kind: 'generic' as const,
      requiredCount: 0 as const,
    },
    text: 'Happy birthday!' as PrivateMessageText,
    requestedSegmentCap: 1 as const,
  },
};

const configuredPolicy = {
  kind: 'configured' as const,
  draft: {
    primaryStart: '09:00' as const,
    primaryEnd: '11:00' as const,
    latePolicy: { kind: 'none' as const },
    dailyCap: 10,
  },
};

const iosCompleteSetup: SetupProjection = {
  step: 'complete',
  initialActivationCompleted: true,
  eligibility: iosBootstrap.eligibility,
  account: iosAccount,
  contacts: {
    kind: 'fresh',
    completedAt: generatedAt,
    contactCount: 1,
  },
  readiness: iosReadiness,
  automation: iosHome.automation,
};

const lifecycleRepairFixture = () => {
  const repairingAccount: AccountProjection = {
    kind: 'cleanup-pending',
    operation: 'repair',
    issue: {
      id: 'repairing-lifecycle-lease' as IssueId,
      code: 'coordination-unavailable',
      severity: 'blocking',
      blocks: ['test', 'activation', 'birthday'],
    },
  };
  const repairingBootstrap: BootstrapProjection = {
    ...completeBootstrap,
    account: repairingAccount,
    setupStep: 'complete',
  };
  const repairingSetup: SetupProjection = {
    ...completeSetup,
    step: 'complete',
    account: repairingAccount,
    automation: {
      platform: 'android',
      desired: 'paused',
      effective: 'paused-repair',
      readiness,
    },
  };
  return { repairingAccount, repairingBootstrap, repairingSetup } as const;
};

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
  selectedDestinationBlocked: false,
  nextOccurrenceLabel: '18 July 2026',
});

const contactDetailNeedingApproval = (): ContactDetail => ({
  ...contactDetail(),
  summary: {
    ...contactSummary(),
    readiness: {
      kind: 'needs-attention',
      reasons: ['approval-invalid'],
    },
    enrollment: {
      kind: 'enabled',
      approval: { kind: 'missing' },
    },
  },
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

const deferred = <Value,>() => {
  let resolve!: (value: Value) => void;
  const promise = new Promise<Value>(next => {
    resolve = next;
  });
  return { promise, resolve } as const;
};

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
    getSetup: jest.fn(async () => ok(completeSetup)),
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
    getPolicyEditor: jest.fn(async () => ok(configuredPolicy)),
    getApproval: jest.fn(async () =>
      ok({ kind: 'valid' as const, approvedAt: generatedAt }),
    ),
    getBirthdayJob: jest.fn(async () => internalError()),
    getMessageEditor: jest.fn(async () => ok(configuredMessage)),
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
    blockRecipientDestination: jest.fn(async () => internalError()),
    unblockRecipientDestination: jest.fn(async () => internalError()),
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
  await appI18n.changeLanguage('en');
  if (appI18n.hasResourceBundle('ar-XB', 'translation')) {
    appI18n.removeResourceBundle('ar-XB', 'translation');
  }
  jest.restoreAllMocks();
  jest.useRealTimers();
});

describe('production live projections', () => {
  it('renders a successful native Home route without importing or rendering fixtures', async () => {
    const getHome = jest.fn(async () =>
      ok({
        ...liveHome('active'),
        next: nextBirthday,
        counts: {
          ...liveHome('active').counts,
          nextSevenDays: 1,
        },
        schedulerHeartbeatAt: generatedAt,
        lastCoordinationSuccessAt: generatedAt,
      }),
    );
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
    expect(screen.getByText('Live Contact')).toBeTruthy();
    expect(screen.getByText('18 July 2026')).toBeTruthy();
    expect(screen.queryByText('Happy birthday!')).toBeNull();
    const approvedMessageToggle = screen.getByTestId(
      'live-home-approved-message-toggle',
    );
    expect(approvedMessageToggle.props.accessibilityLabel).toBe(
      'View approved message',
    );
    await fireEvent.press(approvedMessageToggle);
    expect(screen.getByTestId('live-home-approved-message')).toBeTruthy();
    expect(screen.getByText('Happy birthday!')).toBeTruthy();
    expect(
      screen.getByTestId('live-home-approved-message-toggle').props
        .accessibilityLabel,
    ).toBe('Hide approved message');
    await fireEvent.press(
      screen.getByTestId('live-home-approved-message-toggle'),
    );
    expect(screen.queryByTestId('live-home-approved-message')).toBeNull();
    expect(screen.queryByText('Happy birthday!')).toBeNull();
    expect(screen.getByText('At a glance')).toBeTruthy();
    const summaryRows = screen
      .getAllByRole('text')
      .filter(node =>
        [
          'Birthdays. 0 today · 1 in the next 7 days',
          'People. 1 enabled · 0 need attention',
        ].includes(node.props.accessibilityLabel),
      );
    expect(summaryRows).toHaveLength(2);
    expect(
      new Set(summaryRows.map(row => row.props.accessibilityLabel)).size,
    ).toBe(2);
    expect(screen.getByTestId('live-home-activity')).toBeTruthy();
    expect(screen.queryByTestId('live-home-attention')).toBeNull();
    expect(screen.queryByTestId('live-home-automation')).toBeNull();
    expect(screen.queryByTestId('live-home-review-today')).toBeNull();
    expect(screen.getByTestId('live-home-pause')).toBeTruthy();
    expect(screen.queryByTestId('live-home-message')).toBeNull();
    expect(screen.queryByTestId('live-home-refresh')).toBeNull();
    expect(screen.queryByTestId('live-home-active-sender')).toBeNull();
    expect(screen.queryByText('Protected service')).toBeNull();
    expect(screen.queryByText('Scheduler heartbeat')).toBeNull();
    expect(screen.queryByText('Last safety check')).toBeNull();
    const tabs = screen.getAllByRole('tab');
    expect(tabs).toHaveLength(3);
    expect(tabs.map(tab => tab.props.accessibilityLabel)).toEqual([
      'Home',
      'People',
      'Settings',
    ]);
    const homeTab = screen.getByTestId('live-tab-home');
    expect(homeTab.props.accessibilityRole).toBe('tab');
    expect(homeTab.props.accessibilityState).toEqual({
      disabled: false,
      selected: true,
    });
    expect(screen.getByTestId('live-tab-list').props.accessibilityRole).toBe(
      'tablist',
    );
    expect(
      StyleSheet.flatten(
        typeof homeTab.props.style === 'function'
          ? homeTab.props.style({ pressed: false })
          : homeTab.props.style,
      ).minHeight,
    ).toBeGreaterThanOrEqual(48);
    await fireEvent(screen.getByTestId('live-tab-home'), 'focus');
    const focusedHomeTab = screen.getByTestId('live-tab-home');
    expect(
      StyleSheet.flatten(
        typeof focusedHomeTab.props.style === 'function'
          ? focusedHomeTab.props.style({ pressed: false })
          : focusedHomeTab.props.style,
      ).outlineWidth,
    ).toBe(3);
    await fireEvent(screen.getByTestId('live-tab-home'), 'blur');

    await fireEvent.press(screen.getByTestId('live-tab-settings'));
    await waitFor(() =>
      expect(screen.getByTestId('live-settings-screen')).toBeTruthy(),
    );
    const usefulSettingsRows = [
      screen.getByTestId('live-settings-message'),
      screen.getByTestId('live-settings-schedule'),
      screen.getByTestId('live-settings-automation'),
      screen.getByTestId('live-settings-privacy'),
      screen.getByTestId('live-settings-help-legal'),
    ];
    const accessibleLabels = usefulSettingsRows.map(
      row => row.props.accessibilityLabel,
    );
    expect(accessibleLabels.every(label => typeof label === 'string')).toBe(
      true,
    );
    expect(new Set(accessibleLabels).size).toBe(usefulSettingsRows.length);
    const settingsGroupNames = ['Birthday plan', 'Account and privacy', 'Help'];
    for (const name of settingsGroupNames) {
      expect(screen.getByRole('header', { name })).toBeTruthy();
    }
    expect(new Set(settingsGroupNames).size).toBe(settingsGroupNames.length);
    expect(
      screen.getByTestId('live-settings-automation').props.accessibilityLabel,
    ).toMatch(/^Android sending\./u);
    expect(screen.queryByTestId('live-settings-attention')).toBeNull();
    expect(screen.queryByTestId('live-settings-activity')).toBeNull();
    expect(screen.queryByTestId('live-settings-diagnostics')).toBeNull();
    expect(screen.queryByTestId('live-settings-refresh')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Activity' })).toBeNull();
    expect(
      screen.queryByRole('button', { name: 'Needs attention' }),
    ).toBeNull();
    expect(screen.queryByRole('button', { name: 'Diagnostics' })).toBeNull();
    expect(screen.queryByText('Phone readiness')).toBeNull();
    expect(screen.queryByText('Privacy summary')).toBeNull();
    expect(screen.queryByText('Appearance follows this phone')).toBeNull();
    expect(screen.queryByText('Language follows this phone')).toBeNull();
  });

  it('prioritizes Fix issues over a simultaneous today review and plan action', async () => {
    const needsRepairHome: HomeProjection = {
      ...liveHome('paused-repair'),
      next: nextBirthday,
      automation: {
        platform: 'android',
        desired: 'paused',
        effective: 'paused-repair',
        readiness,
      },
      counts: {
        ...liveHome('paused-repair').counts,
        today: 1,
        nextSevenDays: 1,
      },
    };
    const { port } = createPort({
      getHome: jest.fn(async () => ok(needsRepairHome)),
    });

    await renderLiveApp(port);

    const repair = await screen.findByTestId('live-home-attention');
    expect(repair.props.accessibilityLabel).toBe('Fix issues');
    expect(screen.getAllByTestId('live-home-attention')).toHaveLength(1);
    expect(screen.queryByTestId('live-home-review-today')).toBeNull();
    expect(screen.queryByTestId('live-home-automation')).toBeNull();
    expect(screen.getByTestId('live-home-activity')).toBeTruthy();
    expect(screen.queryByTestId('live-home-message')).toBeNull();
    expect(screen.queryByTestId('live-home-refresh')).toBeNull();
  });

  it.each(['not-configured', 'test-only'] as const)(
    'offers one contextual setup action for Android %s and returns Back to Home',
    async effective => {
      const inactiveHome: HomeProjection = {
        ...liveHome('active'),
        automation: {
          platform: 'android',
          desired: 'paused',
          effective,
          readiness,
        },
      };
      const { port } = createPort({
        getHome: jest.fn(async () => ok(inactiveHome)),
      });

      await renderLiveApp(port);

      const setup = await screen.findByTestId('live-home-automation');
      expect(setup.props.accessibilityLabel).toBe('Set up birthday plan');
      expect(screen.getAllByTestId('live-home-automation')).toHaveLength(1);
      expect(screen.queryByTestId('live-home-attention')).toBeNull();
      expect(screen.queryByTestId('live-home-review-today')).toBeNull();
      expect(screen.queryByTestId('live-home-pause')).toBeNull();

      await fireEvent.press(setup);
      expect(await screen.findByTestId('live-automation-screen')).toBeTruthy();
      await fireEvent.press(screen.getByTestId('live-automation-back'));
      expect(await screen.findByTestId('live-home-screen')).toBeTruthy();
    },
  );

  it('prioritizes Android setup over a due action while automation is test-only', async () => {
    const testOnlyDueHome: HomeProjection = {
      ...activationReadyHome,
      next: nextBirthday,
      counts: {
        ...activationReadyHome.counts,
        today: 1,
        nextSevenDays: 1,
      },
    };
    const { port } = createPort({
      getHome: jest.fn(async () => ok(testOnlyDueHome)),
    });

    await renderLiveApp(port);

    expect(await screen.findByTestId('live-home-automation')).toBeTruthy();
    expect(screen.queryByTestId('live-home-review-today')).toBeNull();
  });

  it('keeps pausing behind an explicit Home review and native revision check', async () => {
    const getHome = jest
      .fn()
      .mockResolvedValueOnce(ok(liveHome('active'), revision('4')))
      .mockResolvedValue(ok(activationReadyHome, revision('5')));
    const pauseAll = jest.fn(async () =>
      ok(activationReadyHome.automation, revision('5')),
    );
    const { port } = createPort({ getHome, pauseAll });

    await renderLiveApp(port);

    await fireEvent.press(await screen.findByTestId('live-home-pause'));
    expect(await screen.findByText('Pause birthday actions?')).toBeTruthy();
    expect(screen.queryByTestId('live-home-pause')).toBeNull();
    expect(screen.queryByTestId('live-home-attention')).toBeNull();
    expect(screen.queryByTestId('live-home-review-today')).toBeNull();
    expect(screen.queryByTestId('live-home-automation')).toBeNull();
    expect(pauseAll).not.toHaveBeenCalled();

    await fireEvent.press(screen.getByTestId('live-home-confirm-pause'));

    await waitFor(() => expect(pauseAll).toHaveBeenCalledTimes(1));
    expect(pauseAll).toHaveBeenCalledWith({ expectedRevision: '4' });
    expect(getHome).toHaveBeenCalledTimes(2);
    expect(
      await screen.findByText('Birthday actions are paused.'),
    ).toBeTruthy();
    expect(screen.getByTestId('live-home-automation')).toBeTruthy();
  });

  it('retires a Home pause review when the same mode is reloaded at a new revision', async () => {
    const getHome = jest
      .fn()
      .mockResolvedValueOnce(ok(liveHome('active'), revision('4')))
      .mockResolvedValue(ok(liveHome('active'), revision('5')));
    const pauseAll = jest.fn(async () =>
      ok(activationReadyHome.automation, revision('6')),
    );
    const harness = createPort({ getHome, pauseAll });

    await renderLiveApp(harness.port);
    await fireEvent.press(await screen.findByTestId('live-home-pause'));
    expect(await screen.findByTestId('live-home-confirm-pause')).toBeTruthy();

    await act(async () => {
      harness.emit({ revision: revision('5'), areas: ['home'] });
    });

    await waitFor(() =>
      expect(getHome.mock.calls.length).toBeGreaterThanOrEqual(2),
    );
    await waitFor(() =>
      expect(screen.queryByTestId('live-home-confirm-pause')).toBeNull(),
    );
    expect(screen.getByTestId('live-home-pause')).toBeTruthy();
    expect(pauseAll).not.toHaveBeenCalled();
  });

  it('hides healthy iPhone internals while keeping a useful ready Home', async () => {
    const healthyCompanionPort: LiveCompanionPort = {
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
      openUserConfirmedComposer: jest.fn(),
      prepareComposerReview: jest.fn(),
      requestReminderAuthorization: jest.fn(),
    };
    const readyHome: HomeProjection = {
      ...iosHome,
      next: nextBirthday,
    };
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(readyHome)),
    });

    await renderLiveApp(port, healthyCompanionPort);

    expect(
      await screen.findByText('Birthday reminders are ready'),
    ).toBeTruthy();
    await waitFor(() =>
      expect(healthyCompanionPort.getReminderStatus).toHaveBeenCalledTimes(1),
    );
    await waitFor(() =>
      expect(
        screen.queryByText('iPhone reminder and Messages readiness'),
      ).toBeNull(),
    );
    expect(screen.queryByText('Allowed')).toBeNull();
    expect(screen.queryByText('1 reminder scheduled')).toBeNull();
    expect(screen.queryByText('1 birthday date planned')).toBeNull();
    expect(screen.queryByText('Available for your review')).toBeNull();
    expect(screen.queryByTestId('live-home-attention')).toBeNull();
    expect(screen.queryByTestId('live-home-review-today')).toBeNull();
    expect(screen.queryByTestId('live-home-automation')).toBeNull();
    expect(screen.queryByTestId('live-home-refresh')).toBeNull();
    expect(screen.getByTestId('live-home-activity')).toBeTruthy();

    const labels = [
      screen.getByTestId('live-home-approved-message-toggle'),
      screen.getByTestId('live-home-activity'),
      screen.getByTestId('live-home-pause'),
    ].map(control => control.props.accessibilityLabel);
    expect(labels.every(label => typeof label === 'string')).toBe(true);
    expect(new Set(labels).size).toBe(labels.length);
  });

  it('offers one contextual manage-plan action when iPhone reminders are paused', async () => {
    const pausedHome: HomeProjection = {
      ...iosHome,
      automation: {
        platform: 'ios',
        desired: 'paused',
        effective: 'paused',
        readiness: iosReadiness,
      },
    };
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(pausedHome)),
    });

    await renderLiveApp(port);

    const manage = await screen.findByTestId('live-home-automation');
    expect(manage.props.accessibilityLabel).toBe('Review birthday plan');
    expect(screen.getAllByTestId('live-home-automation')).toHaveLength(1);
    expect(screen.queryByTestId('live-home-attention')).toBeNull();
    expect(screen.queryByTestId('live-home-review-today')).toBeNull();
    expect(screen.queryByTestId('live-home-pause')).toBeNull();
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
    expect(screen.queryByTestId('live-cloud-privacy-boundary')).toBeNull();
    expect(screen.queryByText(/content-free, not data-free/u)).toBeNull();
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

  it('reports native iPhone notification, horizon, MessageUI, and Android coexistence status on Home', async () => {
    const coexistenceHome: HomeProjection = {
      ...iosHome,
      automation: {
        platform: 'ios',
        desired: 'composer-reminders-on',
        effective: 'action-required',
        readiness: {
          ...iosReadiness,
          composer: {
            kind: 'blocked',
            issues: [
              {
                id: 'ios-active-android' as IssueId,
                code: 'active-sender-other-device',
                severity: 'blocking',
                blocks: ['composer'],
              },
            ],
          },
        },
      },
    };
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
      openNotificationSettings: jest.fn(),
      openUserConfirmedComposer: jest.fn(),
      prepareComposerReview: jest.fn(),
      requestReminderAuthorization: jest.fn(),
    };
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(coexistenceHome)),
    });

    await renderLiveApp(port, companionPort);

    expect(
      await screen.findByText('iPhone reminder and Messages readiness'),
    ).toBeTruthy();
    expect(screen.getByText('Not allowed')).toBeTruthy();
    expect(screen.queryByText('1 reminder scheduled')).toBeNull();
    expect(screen.queryByText('3 birthday dates planned')).toBeNull();
    expect(screen.getByText('2 reminders could not be scheduled')).toBeTruthy();
    expect(
      screen.getByText('Some dates could not fit in iPhone’s reminder limit.'),
    ).toBeTruthy();
    expect(screen.getByText('Earliest unscheduled birthday')).toBeTruthy();
    expect(screen.getByText('19 July 2026')).toBeTruthy();
    expect(screen.getByText('Unavailable on this device')).toBeTruthy();
    expect(
      screen.getByText('Managed by an active Android sender'),
    ).toBeTruthy();
  });

  it('withholds the due iPhone review action when MessageUI is unavailable', async () => {
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
      getHome: jest.fn(async () => ok(iosDueHome)),
    });

    await renderLiveApp(port, companionPort);

    expect(await screen.findByText('Unavailable on this device')).toBeTruthy();
    expect(screen.queryByTestId('live-home-review-today')).toBeNull();
  });

  it('withholds the due iPhone review action when native composer readiness is blocked', async () => {
    const blockedHome: HomeProjection = {
      ...iosDueHome,
      automation: {
        platform: 'ios',
        desired: 'paused',
        effective: 'paused',
        readiness: {
          ...iosReadiness,
          composer: {
            kind: 'blocked',
            issues: [
              {
                id: 'ios-managed-elsewhere' as IssueId,
                code: 'active-sender-other-device',
                severity: 'blocking',
                blocks: ['composer'],
              },
            ],
          },
        },
      },
    };
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
      openUserConfirmedComposer: jest.fn(),
      prepareComposerReview: jest.fn(),
      requestReminderAuthorization: jest.fn(),
    };
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(blockedHome)),
    });

    await renderLiveApp(port, companionPort);

    expect(
      await screen.findByText('Managed by an active Android sender'),
    ).toBeTruthy();
    expect(screen.queryByTestId('live-home-review-today')).toBeNull();
    expect(screen.getByTestId('live-home-automation')).toBeTruthy();
  });

  it('refreshes iPhone warning status on foreground without restoring a Home refresh button', async () => {
    let appStateHandler: ((state: 'active' | 'background') => void) | undefined;
    jest
      .spyOn(AppState, 'addEventListener')
      .mockImplementation((_event, handler) => {
        appStateHandler = handler as (state: 'active' | 'background') => void;
        return { remove: jest.fn() };
      });
    const getReminderStatus = jest
      .fn()
      .mockResolvedValueOnce({
        kind: 'ok' as const,
        value: {
          authorization: 'denied' as const,
          failedCount: 0,
          kind: 'ok' as const,
          plannedDateCount: 1,
          scheduledCount: 0,
          truncated: false,
        },
      })
      .mockResolvedValue({
        kind: 'ok' as const,
        value: {
          authorization: 'authorized' as const,
          failedCount: 0,
          kind: 'ok' as const,
          plannedDateCount: 1,
          scheduledCount: 1,
          truncated: false,
        },
      });
    const canOpenComposer = jest
      .fn()
      .mockResolvedValueOnce(false)
      .mockResolvedValue(true);
    const companionPort: LiveCompanionPort = {
      canOpenComposer,
      getReminderStatus,
      openNotificationSettings: jest.fn(),
      openUserConfirmedComposer: jest.fn(),
      prepareComposerReview: jest.fn(),
      requestReminderAuthorization: jest.fn(),
    };
    const getHome = jest.fn(async () => ok(iosHome, revision('7')));
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome,
    });

    await renderLiveApp(port, companionPort);

    expect(await screen.findByText('Not allowed')).toBeTruthy();
    expect(screen.getByText('Unavailable on this device')).toBeTruthy();
    expect(screen.queryByTestId('live-home-refresh')).toBeNull();

    await act(async () => appStateHandler?.('background'));
    await act(async () => appStateHandler?.('active'));

    await waitFor(() => expect(getReminderStatus).toHaveBeenCalledTimes(2));
    expect(screen.queryByText('Not allowed')).toBeNull();
    expect(screen.queryByText('Unavailable on this device')).toBeNull();
    expect(getReminderStatus).toHaveBeenCalledTimes(2);
    expect(canOpenComposer).toHaveBeenCalledTimes(2);
    expect(getHome).toHaveBeenCalledTimes(1);
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
    await fireEvent.press(
      screen.getByTestId('live-open-notification-settings'),
    );
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
    const transferPendingAccount: AccountProjection = {
      kind: 'connected',
      displayEmail: 'user@example.test' as PrivateEmail,
      sender: {
        platform: 'android',
        kind: 'transfer-pending',
        preissuedPermitMayFinish: true,
        drainUntil: instant('2026-07-12T07:01:00Z'),
      },
    };
    const testOnlyAccount: AccountProjection = {
      kind: 'connected',
      displayEmail: 'user@example.test' as PrivateEmail,
      sender: {
        platform: 'android',
        kind: 'test-only',
        epochLabel: 'Current sender epoch',
      },
    };
    const operationId = `transfer_${'b'.repeat(
      32,
    )}` as SenderTransferOperationId;
    const getSenderTransferOperation = jest
      .fn()
      .mockResolvedValueOnce(ok({ kind: 'none' as const }))
      .mockResolvedValueOnce(
        ok(
          {
            kind: 'remote-draining' as const,
            id: operationId,
            preissuedPermitMayFinish: true as const,
            reason: 'transfer-pending' as const,
            updatedAt: generatedAt,
            drainUntil: instant('2026-07-12T07:01:00Z'),
          },
          revision('2'),
        ),
      )
      .mockResolvedValue(
        ok(
          {
            kind: 'complete' as const,
            id: operationId,
            preissuedPermitMayFinish: false as const,
            completedAt: instant('2026-07-12T07:02:00Z'),
            requiresTest: true as const,
          },
          revision('3'),
        ),
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
    const getAccount = jest
      .fn()
      .mockResolvedValueOnce(ok(standbyAccount))
      .mockResolvedValueOnce(ok(transferPendingAccount, revision('2')))
      .mockResolvedValue(ok(testOnlyAccount, revision('3')));
    const { port } = createPort({
      getAccount,
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
    await waitFor(() =>
      expect(completeSenderTransfer).toHaveBeenCalledTimes(1),
    );
    expect(await screen.findByText('A real SMS test is required')).toBeTruthy();
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
    expect(
      screen.getByTestId('live-settings-automation').props.accessibilityLabel,
    ).toMatch(/^iPhone reminders\./u);
    expect(getNotificationPermission).not.toHaveBeenCalled();
    expect(getSenderTransferOperation).not.toHaveBeenCalled();
  });

  it('keeps phone preferences and the detailed inventory out of Settings while preserving them in Privacy', async () => {
    const inventory = {
      ...privacyInventory,
      activityCount: 3,
      templateCount: 2,
      lastContactsSyncAt: generatedAt,
      consentVersions: ['contacts-v1', 'privacy-v2'],
    };
    const getInventory = jest.fn(async () => ok(inventory));
    const { port } = createPort({
      getInventory,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-settings'));

    expect(screen.queryByText('Appearance follows this phone')).toBeNull();
    expect(screen.queryByText('Language follows this phone')).toBeNull();
    expect(screen.queryByTestId('live-appearance-dark')).toBeNull();
    expect(screen.queryByTestId('live-language-hi')).toBeNull();
    expect(screen.queryByText('Saved message templates')).toBeNull();
    expect(screen.queryByText('Recorded consent versions')).toBeNull();
    expect(getInventory).not.toHaveBeenCalled();

    await fireEvent.press(screen.getByTestId('live-settings-privacy'));

    expect(await screen.findByTestId('live-privacy-screen')).toBeTruthy();
    expect(screen.queryByText('Saved message templates')).toBeNull();
    await fireEvent.press(
      screen.getByTestId('live-privacy-data-details-toggle'),
    );
    expect(screen.getByText('Saved message templates')).toBeTruthy();
    expect(screen.getByText('Last Contacts sync')).toBeTruthy();
    expect(screen.getByText('Recorded consent versions')).toBeTruthy();
    expect(screen.getByText('contacts-v1, privacy-v2')).toBeTruthy();
    expect(screen.getByText(/at most 30 days/u)).toBeTruthy();
    expect(screen.getByText(/up to 400 days/u)).toBeTruthy();
    expect(screen.getByText('Copies outside Birthday Autopilot')).toBeTruthy();
    expect(
      screen.getByText(/For enabled Android recipients only/u),
    ).toBeTruthy();
    expect(screen.getByText(/not anonymous/u)).toBeTruthy();
    expect(
      screen.getByText(/cannot promise immediate erasure of provider logs/u),
    ).toBeTruthy();
    expect(screen.getAllByTestId('live-cloud-privacy-boundary')).toHaveLength(
      1,
    );
    expect(getInventory).toHaveBeenCalledTimes(1);
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
    await fireEvent.press(screen.getByTestId('live-settings-privacy'));
    await fireEvent.press(
      screen.getByTestId('live-privacy-data-details-toggle'),
    );

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

  it('removes a retained complete shell while lifecycle bootstrap truth refreshes and fails', async () => {
    const bootstrapRefresh = deferred<NativeResult<BootstrapProjection>>();
    const getBootstrap = jest
      .fn()
      .mockResolvedValueOnce(ok(completeBootstrap, revision('1')))
      .mockImplementationOnce(() => bootstrapRefresh.promise);
    const harness = createPort({ getBootstrap });

    await renderLiveApp(harness.port);
    expect(await screen.findByTestId('live-app-shell')).toBeTruthy();

    await act(async () => {
      harness.emit({ revision: revision('2'), areas: ['account'] });
    });
    await waitFor(() =>
      expect(screen.queryByTestId('live-app-shell')).toBeNull(),
    );
    expect(screen.getByTestId('native-app-boundary')).toBeTruthy();

    await act(async () => {
      bootstrapRefresh.resolve(internalError());
    });
    expect(
      await screen.findByTestId('native-bootstrap-unavailable'),
    ).toBeTruthy();
    expect(screen.getByTestId('native-bootstrap-retry')).toBeTruthy();
    expect(screen.queryByTestId('live-app-shell')).toBeNull();
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
      expect(screen.getByTestId('live-person-manage-toggle')).toBeTruthy(),
    );
    expect(screen.getByTestId('live-person-detail-screen').props.edges).toEqual(
      ['top', 'left', 'right', 'bottom'],
    );

    await fireEvent.press(screen.getByTestId('live-person-manage-toggle'));
    await fireEvent.press(screen.getByTestId('live-person-pause'));
    expect(pauseRecipient).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByTestId('live-person-confirm-pause'));

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
      expect(screen.getByTestId('live-person-manage-toggle')).toBeTruthy(),
    );

    await fireEvent.press(screen.getByTestId('live-person-manage-toggle'));
    await fireEvent.press(screen.getByTestId('live-person-pause'));
    expect(pauseRecipient).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByTestId('live-person-confirm-pause'));

    await waitFor(() =>
      expect(screen.getByText('Action not completed')).toBeTruthy(),
    );
    expect(pauseRecipient).toHaveBeenCalledTimes(1);
    expect(getPerson).toHaveBeenCalledTimes(1);
    expect(
      screen.getByTestId('live-person-enrollment').props.accessibilityLabel,
    ).toContain('Enabled');
  });

  it('rejects a single-person mutation response for a different contact', async () => {
    const otherContactId = 'contact-live-other' as ContactId;
    const getPerson = jest.fn(async () => ok(contactDetail(), revision('1')));
    const pauseRecipient = jest.fn(async () =>
      ok({
        changedContactIds: [otherContactId],
        invalidatedApprovalCount: 1,
      }),
    );
    const { port } = createPort({ getPerson, pauseRecipient });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-people'));
    await fireEvent.press(
      await screen.findByTestId('live-person-contact-live-1'),
    );
    await fireEvent.press(
      await screen.findByTestId('live-person-manage-toggle'),
    );
    await fireEvent.press(await screen.findByTestId('live-person-pause'));
    expect(pauseRecipient).not.toHaveBeenCalled();
    await fireEvent.press(
      await screen.findByTestId('live-person-confirm-pause'),
    );

    expect(await screen.findByText('Action not completed')).toBeTruthy();
    expect(screen.queryByText(/NATIVE_CONTRACT_INVALID/u)).toBeNull();
    expect(pauseRecipient).toHaveBeenCalledTimes(1);
    expect(getPerson).toHaveBeenCalledTimes(2);
    expect(
      screen.getByTestId('live-person-enrollment').props.accessibilityLabel,
    ).toContain('Enabled');
  });

  it('fails closed when person detail returns a different contact', async () => {
    const otherContactId = 'contact-live-other' as ContactId;
    const getPerson = jest.fn(async () =>
      ok({
        ...contactDetail(),
        summary: { ...contactSummary(), id: otherContactId },
      }),
    );
    const { port } = createPort({ getPerson });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-people'));
    await fireEvent.press(
      await screen.findByTestId('live-person-contact-live-1'),
    );

    expect(await screen.findByText('Person details are unavailable')).toBeTruthy();
    expect(screen.queryByText(/NATIVE_CONTRACT_INVALID/u)).toBeNull();
    expect(screen.queryByTestId('live-person-manage-toggle')).toBeNull();
    expect(screen.queryByTestId('live-review-approval')).toBeNull();
  });

  it('keeps person mutations collapsed until Manage this person is expanded', async () => {
    const phoneId = 'phone-manage-choice' as PhoneChoiceId;
    const getPerson = jest.fn(async () =>
      ok({
        ...contactDetail(),
        phoneChoices: [
          {
            id: phoneId,
            maskedDisplay: '•••• 4321',
            sourceLabel: 'Google mobile',
            selectable: true,
          },
        ],
        selectedPhoneId: phoneId,
      }),
    );
    const { port } = createPort({ getPerson });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-people'));
    await fireEvent.press(
      await screen.findByTestId('live-person-contact-live-1'),
    );

    const manage = await screen.findByTestId('live-person-manage-toggle');
    expect(manage.props.accessibilityState).toEqual({
      disabled: false,
      expanded: false,
    });
    expect(screen.queryByTestId('live-person-manage')).toBeNull();
    expect(screen.queryByTestId('live-person-pause')).toBeNull();
    expect(screen.queryByTestId('live-person-exclude')).toBeNull();
    expect(screen.queryByTestId('live-person-block-destination')).toBeNull();

    await fireEvent.press(manage);
    expect(
      screen.getByTestId('live-person-manage-toggle').props.accessibilityState,
    ).toEqual({ disabled: false, expanded: true });
    expect(screen.getByTestId('live-person-manage')).toBeTruthy();
    expect(screen.getByTestId('live-person-pause')).toBeTruthy();
    expect(screen.getByTestId('live-person-exclude')).toBeTruthy();
    expect(screen.getByTestId('live-person-block-destination')).toBeTruthy();

    await fireEvent.press(screen.getByTestId('live-person-manage-toggle'));
    expect(screen.queryByTestId('live-person-manage')).toBeNull();
    expect(screen.queryByTestId('live-person-pause')).toBeNull();
  });

  it('retires an open person confirmation when contact truth is invalidated', async () => {
    const getPerson = jest
      .fn()
      .mockResolvedValueOnce(ok(contactDetail(), revision('1')))
      .mockResolvedValueOnce(ok(contactDetail(), revision('2')));
    const pauseRecipient = jest.fn(async () => internalError());
    const harness = createPort({ getPerson, pauseRecipient });

    await renderLiveApp(harness.port);
    await fireEvent.press(await screen.findByTestId('live-tab-people'));
    await fireEvent.press(
      await screen.findByTestId('live-person-contact-live-1'),
    );
    await fireEvent.press(
      await screen.findByTestId('live-person-manage-toggle'),
    );
    await fireEvent.press(await screen.findByTestId('live-person-pause'));
    const retiredConfirm = await screen.findByTestId(
      'live-person-confirm-pause',
    );

    await act(async () => {
      harness.emit({ revision: revision('2'), areas: ['contacts'] });
    });
    await waitFor(() => expect(getPerson).toHaveBeenCalledTimes(2));
    await waitFor(() =>
      expect(screen.queryByTestId('live-person-confirm-pause')).toBeNull(),
    );
    await fireEvent.press(retiredConfirm);
    expect(pauseRecipient).not.toHaveBeenCalled();
  });

  it('submits a person confirmation only once while native work is pending', async () => {
    const pauseResult =
      deferred<Awaited<ReturnType<LiveAppPort['pauseRecipient']>>>();
    const getPerson = jest
      .fn()
      .mockResolvedValueOnce(ok(contactDetail(), revision('1')))
      .mockResolvedValueOnce(ok(contactDetail('paused'), revision('2')));
    const pauseRecipient = jest.fn(() => pauseResult.promise);
    const { port } = createPort({ getPerson, pauseRecipient });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-people'));
    await fireEvent.press(
      await screen.findByTestId('live-person-contact-live-1'),
    );
    await fireEvent.press(
      await screen.findByTestId('live-person-manage-toggle'),
    );
    await fireEvent.press(await screen.findByTestId('live-person-pause'));
    const confirm = await screen.findByTestId('live-person-confirm-pause');

    const pendingConfirmation = fireEvent.press(confirm);
    await waitFor(() => expect(pauseRecipient).toHaveBeenCalledTimes(1));
    await fireEvent.press(screen.getByTestId('live-person-confirm-pause'));
    expect(pauseRecipient).toHaveBeenCalledTimes(1);

    await act(async () => {
      pauseResult.resolve(
        ok(
          { changedContactIds: [contactId], invalidatedApprovalCount: 0 },
          revision('2'),
        ),
      );
    });
    await pendingConfirmation;
    await waitFor(() => expect(getPerson).toHaveBeenCalledTimes(2));
    expect(
      screen.getByTestId('live-person-enrollment').props.accessibilityLabel,
    ).toContain('Paused');
  });

  it('settles one pause after native invalidation arrives before success', async () => {
    const pauseResult =
      deferred<Awaited<ReturnType<LiveAppPort['pauseRecipient']>>>();
    const pausedDetail = contactDetail('paused');
    const getPerson = jest
      .fn()
      .mockResolvedValueOnce(ok(contactDetail(), revision('1')))
      .mockResolvedValueOnce(ok(pausedDetail, revision('2')))
      .mockResolvedValueOnce(ok(pausedDetail, revision('2')));
    const pauseRecipient = jest.fn(() => pauseResult.promise);
    const harness = createPort({ getPerson, pauseRecipient });

    await renderLiveApp(harness.port);
    await fireEvent.press(await screen.findByTestId('live-tab-people'));
    await fireEvent.press(
      await screen.findByTestId('live-person-contact-live-1'),
    );
    await fireEvent.press(
      await screen.findByTestId('live-person-manage-toggle'),
    );
    await fireEvent.press(await screen.findByTestId('live-person-pause'));
    const pendingConfirmation = fireEvent.press(
      await screen.findByTestId('live-person-confirm-pause'),
    );
    await waitFor(() => expect(pauseRecipient).toHaveBeenCalledTimes(1));

    await act(async () => {
      harness.emit({ revision: revision('2'), areas: ['contacts'] });
    });
    await waitFor(() => expect(getPerson).toHaveBeenCalledTimes(2));

    await act(async () => {
      pauseResult.resolve(
        ok(
          { changedContactIds: [contactId], invalidatedApprovalCount: 0 },
          revision('2'),
        ),
      );
    });
    await pendingConfirmation;

    await waitFor(() => expect(getPerson).toHaveBeenCalledTimes(3));
    expect(pauseRecipient).toHaveBeenCalledTimes(1);
    expect(
      await screen.findByText(
        'The pause was saved and details were checked again.',
      ),
    ).toBeTruthy();
    expect(
      screen.getByTestId('live-person-enrollment').props.accessibilityLabel,
    ).toContain('Paused');
  });

  it('requires confirmation to block and unblock only the selected native destination', async () => {
    const phoneId = 'phone-block-choice' as PhoneChoiceId;
    const base: ContactDetail = {
      ...contactDetail(),
      phoneChoices: [
        {
          id: phoneId,
          maskedDisplay: '•••• 4321',
          sourceLabel: 'Google mobile',
          selectable: true,
        },
      ],
      selectedPhoneId: phoneId,
      selectedDestinationBlocked: false,
    };
    const blocked: ContactDetail = {
      ...base,
      selectedDestinationBlocked: true,
      summary: {
        ...base.summary,
        readiness: {
          kind: 'needs-attention',
          reasons: ['phone-blocked-form'],
        },
        enrollment: {
          kind: 'paused',
          reason: 'approval-invalid',
          approval: { kind: 'invalidated', reasons: ['phone-changed'] },
        },
      },
    };
    const unblockedForReview: ContactDetail = {
      ...blocked,
      selectedDestinationBlocked: false,
      summary: {
        ...blocked.summary,
        readiness: {
          kind: 'needs-attention',
          reasons: ['approval-invalid'],
        },
      },
    };
    const getPerson = jest
      .fn()
      .mockResolvedValueOnce(ok(base, revision('1')))
      .mockResolvedValueOnce(ok(blocked, revision('2')))
      .mockResolvedValueOnce(ok(unblockedForReview, revision('3')));
    const blockRecipientDestination = jest.fn(async () =>
      ok(
        { changedContactIds: [contactId], invalidatedApprovalCount: 1 },
        revision('2'),
      ),
    );
    const unblockRecipientDestination = jest.fn(async () =>
      ok(
        { changedContactIds: [contactId], invalidatedApprovalCount: 0 },
        revision('3'),
      ),
    );
    const { port } = createPort({
      getPerson,
      blockRecipientDestination,
      unblockRecipientDestination,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-people'));
    await fireEvent.press(
      await screen.findByTestId('live-person-contact-live-1'),
    );
    expect(screen.queryByTestId('live-person-block-destination')).toBeNull();
    await fireEvent.press(
      await screen.findByTestId('live-person-manage-toggle'),
    );
    await fireEvent.press(
      await screen.findByTestId('live-person-block-destination'),
    );
    expect(blockRecipientDestination).not.toHaveBeenCalled();
    expect(
      screen.getByText(/\u2068•••• 4321\u2069 will be blocked/u),
    ).toBeTruthy();
    await fireEvent.press(
      screen.getByTestId('live-person-confirm-destination-block'),
    );

    await waitFor(() =>
      expect(blockRecipientDestination).toHaveBeenCalledWith({
        contactId,
        expectedRevision: '1',
      }),
    );
    expect(await screen.findByTestId('live-person-source-repair')).toBeTruthy();
    expect(screen.queryByTestId('live-review-approval')).toBeNull();
    expect(screen.queryByTestId('live-person-unblock-destination')).toBeNull();
    await fireEvent.press(
      await screen.findByTestId('live-person-manage-toggle'),
    );
    await fireEvent.press(
      screen.getByTestId('live-person-unblock-destination'),
    );
    expect(unblockRecipientDestination).not.toHaveBeenCalled();
    await fireEvent.press(
      screen.getByTestId('live-person-confirm-destination-unblock'),
    );

    await waitFor(() =>
      expect(unblockRecipientDestination).toHaveBeenCalledWith({
        contactId,
        expectedRevision: '2',
      }),
    );
    await waitFor(() =>
      expect(
        screen.queryByTestId('live-person-source-repair'),
      ).toBeNull(),
    );
    expect(screen.getByText(/No previous approval was restored/u)).toBeTruthy();
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

  it('keeps Activity and Fix issues rooted on Home and returns both flows there', async () => {
    const { port } = createPort({
      getHome: jest.fn(async () => ok(liveHome('paused-repair'))),
    });

    await renderLiveApp(port);
    await screen.findByTestId('live-home-screen');

    await fireEvent.press(screen.getByTestId('live-home-attention'));
    expect(await screen.findByTestId('live-attention-screen')).toBeTruthy();
    await fireEvent.press(screen.getByTestId('live-attention-back'));
    expect(await screen.findByTestId('live-home-screen')).toBeTruthy();

    await fireEvent.press(screen.getByTestId('live-home-activity'));
    expect(await screen.findByTestId('live-activity-screen')).toBeTruthy();
    await fireEvent.press(screen.getByTestId('live-activity-back'));
    expect(await screen.findByTestId('live-home-screen')).toBeTruthy();

    await fireEvent.press(screen.getByTestId('live-tab-settings'));
    expect(await screen.findByTestId('live-settings-screen')).toBeTruthy();
    expect(screen.queryByTestId('live-settings-activity')).toBeNull();
    expect(screen.queryByTestId('live-settings-attention')).toBeNull();
  });

  it.each([
    ['message', 'live-message-screen', 'live-message-back'],
    ['schedule', 'live-schedule-screen', 'live-schedule-back'],
    ['automation', 'live-automation-screen', 'live-automation-back'],
    ['privacy', 'live-privacy-screen', 'live-privacy-back'],
    ['help-legal', 'live-help-legal-screen', 'live-help-back'],
  ] as const)(
    'returns the useful Settings %s leaf to its visible Settings origin',
    async (destination, screenId, backId) => {
      const { port } = createPort();

      await renderLiveApp(port);
      await fireEvent.press(await screen.findByTestId('live-tab-settings'));
      await fireEvent.press(
        await screen.findByTestId(`live-settings-${destination}`),
      );
      expect(await screen.findByTestId(screenId)).toBeTruthy();
      await fireEvent.press(screen.getByTestId(backId));
      expect(await screen.findByTestId('live-settings-screen')).toBeTruthy();
    },
  );

  it('reuses the Schedule leaf for contextual missing-policy repair and returns to Automation', async () => {
    const missingPolicyHome: HomeProjection = {
      ...activationReadyHome,
      automation: {
        ...activationReadyHome.automation,
        desired: 'paused',
        effective: 'not-configured',
      },
    };
    const { port } = createPort({
      getHome: jest.fn(async () => ok(missingPolicyHome)),
      getAccount: jest.fn(async () => ok(androidAccountForSender('test-only'))),
      getPolicyEditor: jest.fn(async () =>
        ok({ kind: 'not-configured' as const }),
      ),
    });

    await renderLiveApp(port);
    await openSettingsDestination('automation');
    expect(await screen.findByTestId('live-automation-screen')).toBeTruthy();
    expect(screen.queryByTestId('live-policy-editor')).toBeNull();

    await fireEvent.press(
      await screen.findByTestId('live-automation-open-schedule'),
    );
    expect(await screen.findByTestId('live-schedule-screen')).toBeTruthy();
    expect(screen.getByTestId('live-policy-editor')).toBeTruthy();

    await fireEvent.press(screen.getByTestId('live-schedule-back'));
    expect(await screen.findByTestId('live-automation-screen')).toBeTruthy();
    expect(screen.queryByTestId('live-policy-editor')).toBeNull();
  });

  it('explains iOS reported-Sent and final MessageUI visibility boundaries in activity detail', async () => {
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(iosHome)),
      listActivity: jest.fn(async () =>
        ok({
          items: [
            {
              id: 'activity-ios-sent' as import('../domain/shared/brand').ActivityId,
              kind: 'composer-reported-sent' as const,
              occurredAt: generatedAt,
            },
          ],
        }),
      ),
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-home-activity'));
    await fireEvent.press(
      await screen.findByTestId('live-activity-activity-ios-sent'),
    );

    expect(
      await screen.findAllByText(
        'Messages reported sent; delivery not confirmed',
      ),
    ).not.toHaveLength(0);
    expect(
      screen.getByTestId('live-activity-detail-ios-visibility').props
        .accessibilityLabel,
    ).toMatch(/cannot see the final edited recipient or text/u);
    expect(
      screen.getByTestId('live-activity-detail-disclosure').props
        .accessibilityLabel,
    ).toMatch(/will not offer a second in-app composer/u);
  });

  it('uses tab history for Android back and leaves Home to the operating system', async () => {
    let hardwareBack: (() => boolean | null | undefined) | undefined;
    const backSpy = jest
      .spyOn(BackHandler, 'addEventListener')
      .mockImplementation((_event, handler) => {
        hardwareBack = () => handler({} as never);
        return { remove: jest.fn() };
      });
    const { port } = createPort();

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-settings'));
    await waitFor(() =>
      expect(screen.getByTestId('live-settings-screen')).toBeTruthy(),
    );

    await act(async () => {
      expect(hardwareBack?.()).toBe(true);
    });
    await waitFor(() =>
      expect(screen.getByTestId('live-home-screen')).toBeTruthy(),
    );

    await act(async () => {
      expect(hardwareBack?.()).toBe(false);
    });
    expect(screen.getByTestId('live-home-screen')).toBeTruthy();
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
    await openSettingsDestination('message');
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
        text: 'Happy birthday!' as PrivateMessageText,
        requestedSegmentCap: 1 as const,
      },
    };
    const generateSuggestions = jest.fn(async () =>
      ok({
        kind: 'candidates' as const,
        candidates: ['Native suggested birthday message' as PrivateMessageText],
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
              finalText:
                'Native suggested birthday message' as PrivateMessageText,
              characterCount: 33,
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
          text: 'Native suggested birthday message' as PrivateMessageText,
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
    await openSettingsDestination('message');
    expect(screen.queryByTestId('live-message-gemini-privacy')).toBeNull();
    expect(generateSuggestions).not.toHaveBeenCalled();
    await fireEvent.press(
      await screen.findByTestId('live-message-help-toggle'),
    );
    expect(
      await screen.findByTestId('live-message-gemini-privacy'),
    ).toBeTruthy();
    expect(
      screen.getByText(/receives no contact names, phone numbers, birthdays/u),
    ).toBeTruthy();
    expect(
      screen.getByText(/current saved or draft message text/u),
    ).toBeTruthy();
    await fireEvent.press(await screen.findByTestId('live-message-suggest'));
    await fireEvent.press(
      await screen.findByTestId('live-message-suggestion-0'),
    );
    await fireEvent.press(screen.getByTestId('live-message-preview'));
    await waitFor(() =>
      expect(screen.getByTestId('live-message-save')).toBeTruthy(),
    );
    expect(screen.getByText('\u2068Live Contact\u2069')).toBeTruthy();
    expect(screen.getByText('Characters')).toBeTruthy();
    expect(screen.getByText('33')).toBeTruthy();
    expect(screen.getByText('SMS encoding')).toBeTruthy();
    expect(screen.getByText('gsm-7')).toBeTruthy();
    expect(screen.getAllByText('1 SMS part(s)').length).toBeGreaterThanOrEqual(
      2,
    );
    expect(saveMessage).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByTestId('live-message-save'));

    await waitFor(() => expect(saveMessage).toHaveBeenCalledTimes(1));
    expect(previewMessage).toHaveBeenCalledWith({
      draft: expect.objectContaining({
        text: 'Native suggested birthday message',
      }),
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
        text: 'Happy birthday!' as PrivateMessageText,
        requestedSegmentCap: 1 as const,
      },
    };
    const previewMessage = jest.fn(async () => internalError());
    const { port } = createPort({
      getMessageEditor: jest.fn(async () => ok(editorProjection)),
      previewMessage,
    });

    await renderLiveApp(port);
    await openSettingsDestination('message');
    await fireEvent.changeText(
      await screen.findByTestId('live-message-input'),
      'Happy birthday! See https://example.com',
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
    const getHome = jest.fn(async () => ok(activationReadyHome));
    const { port } = createPort({
      prepareActivation,
      activate,
      getHome,
      getAccount: jest.fn(async () => ok(androidAccountForSender('test-only'))),
      getLatestTest: jest.fn(async () => ok(latestPassedTest)),
    });

    await renderLiveApp(port);
    await openSettingsDestination('automation');
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

  it('explains both dangerous Android permissions before the first request', async () => {
    const prepareTest = jest.fn(async () =>
      ok(
        {
          platform: 'android' as const,
          handle: 'test-review-1' as TestReviewHandle,
          maskedDestination: '+91 •••••• 3210',
          exactText: 'Birthday test message' as PrivateMessageText,
          simLabel: 'SIM 1',
          segmentCount: 1,
          chargeDisclosure: 'Your carrier may charge for this SMS.',
        },
        revision('6'),
      ),
    );
    const startTest = jest.fn(async () => internalError());
    const { port } = createPort({
      getHome: jest.fn(async () =>
        ok({
          ...activationReadyHome,
          automation: {
            ...activationReadyHome.automation,
            effective: 'not-configured' as const,
          },
        }),
      ),
      getAccount: jest.fn(async () => ok(androidAccountForSender('test-only'))),
      prepareTest,
      startTest,
    });

    await renderLiveApp(port);
    await openSettingsDestination('automation');
    await fireEvent.changeText(
      await screen.findByTestId('live-test-phone'),
      '+919876543210',
    );
    await fireEvent.press(screen.getByTestId('live-prepare-test'));

    expect(await screen.findByText('Android permission review')).toBeTruthy();
    expect(
      screen.getByText(/SEND_SMS lets this signed Android edition/u),
    ).toBeTruthy();
    expect(screen.getByText(/READ_PHONE_STATE is used only/u)).toBeTruthy();
    expect(screen.getByText(/If either is denied/u)).toBeTruthy();
    expect(screen.getByText(/Carrier charges may apply/u)).toBeTruthy();
    expect(screen.getByTestId('live-start-test')).toBeTruthy();
    expect(startTest).not.toHaveBeenCalled();
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
    const harness = createPort({
      getHome: jest.fn(async () => ok(activationReadyHome)),
      getAccount: jest.fn(async () => ok(androidAccountForSender('test-only'))),
      getLatestTest: jest.fn(async () => ok(latestPassedTest)),
      prepareActivation,
      activate,
    });

    await renderLiveApp(harness.port);
    await openSettingsDestination('automation');
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
    await openSettingsDestination('automation');

    await waitFor(() =>
      expect(
        screen.getByText(
          'SMS left this phone; carrier delivery is not confirmed',
        ),
      ).toBeTruthy(),
    );
    expect(screen.queryByText('sent-from-device')).toBeNull();
  });

  it('enters the due iOS review from Home and opens Messages only with a reviewed opaque proposal', async () => {
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
        revision: '2',
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
      getHome: jest.fn(async () => ok(iosDueHome)),
      getNextComposerProposal,
    });

    await renderLiveApp(port, companionPort);
    await fireEvent.press(await screen.findByTestId('live-home-review-today'));
    await waitFor(() =>
      expect(screen.getByTestId('live-prepare-composer')).toBeTruthy(),
    );
    expect(screen.getByTestId('live-composer-review-screen')).toBeTruthy();
    await fireEvent.press(screen.getByTestId('live-prepare-composer'));
    await waitFor(() =>
      expect(screen.getByTestId('live-open-composer')).toBeTruthy(),
    );
    expect(
      screen.getByTestId('live-ios-composer-review-focus').props
        .accessibilityRole,
    ).toBe('header');
    expect(
      screen.getByTestId('live-ios-composer-review-focus').props
        .accessibilityLabel,
    ).toBe('Ready to review message?');
    expect(
      screen.getByTestId('live-composer-final-disclosure').props
        .accessibilityLabel,
    ).toMatch(/SMS or MMS carrier charges may apply/u);
    expect(
      screen.getByTestId('live-composer-final-disclosure').props
        .accessibilityLabel,
    ).toMatch(/Messages and iOS control the available sender line/u);
    expect(
      StyleSheet.flatten(
        screen.getByTestId('live-composer-final-disclosure').props.style,
      ).backgroundColor,
    ).toBe('#FFF2D8');
    expect(openUserConfirmedComposer).not.toHaveBeenCalled();
    expect(screen.getByText('•••• 4321')).toBeTruthy();
    expect(screen.getByText('18 July 2026')).toBeTruthy();
    expect(screen.getByText('Review message')).toBeTruthy();
    expect(screen.queryByText('2026-07-18')).toBeNull();
    expect(screen.queryByText('+919876543210')).toBeNull();

    await fireEvent.press(screen.getByTestId('live-open-composer'));

    await waitFor(() =>
      expect(
        screen.getByText(/Messages reported sent; delivery not confirmed/u),
      ).toBeTruthy(),
    );
    const postComposerSafety = screen.getByTestId('live-composer-post-safety')
      .props.accessibilityLabel;
    expect(postComposerSafety).toMatch(/hold remains until its server expiry/u);
    expect(postComposerSafety).toMatch(
      /Check Messages before any manual retry/u,
    );
    expect(postComposerSafety).toMatch(/will not retry automatically/u);
    expect(postComposerSafety).not.toMatch(/Tapping Open Messages/u);
    expect(
      StyleSheet.flatten(
        screen.getByTestId('live-composer-post-safety').props.style,
      ).backgroundColor,
    ).toBe('#FFF2D8');
    expect(prepareComposerReview).toHaveBeenCalledWith({
      expectedRevision: '1',
      proposalId: 'proposal-1',
    });
    expect(openUserConfirmedComposer).toHaveBeenCalledWith({
      actionNonce: 'a'.repeat(43),
      expectedRevision: '2',
      proposalId: 'proposal-1',
    });
  });

  it('keeps every Messages composer control out of iOS Settings Automation', async () => {
    const getNextComposerProposal = jest.fn(async () =>
      ok({ kind: 'none' as const }),
    );
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
      openUserConfirmedComposer: jest.fn(),
      prepareComposerReview: jest.fn(),
      requestReminderAuthorization: jest.fn(),
    };
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(iosDueHome)),
      getNextComposerProposal,
    });

    await renderLiveApp(port, companionPort);
    await openSettingsDestination('automation');
    expect(await screen.findByTestId('live-automation-screen')).toBeTruthy();
    expect(
      screen.getAllByRole('header', { name: 'iPhone reminders' }),
    ).not.toHaveLength(0);
    expect(screen.queryByText(/automation/iu)).toBeNull();
    for (const id of [
      'live-composer-review-screen',
      'live-prepare-composer',
      'live-ios-composer-review',
      'live-composer-final-disclosure',
      'live-open-composer',
      'live-composer-repair-contacts',
      'live-composer-post-safety',
    ]) {
      expect(screen.queryByTestId(id)).toBeNull();
    }
    expect(getNextComposerProposal).not.toHaveBeenCalled();
    expect(companionPort.prepareComposerReview).not.toHaveBeenCalled();
    expect(companionPort.openUserConfirmedComposer).not.toHaveBeenCalled();
  });

  it('repairs required Google Contacts access from the due iOS Home review', async () => {
    const continueWithGoogle = jest.fn(async () => ok(iosAccount));
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
      openUserConfirmedComposer: jest.fn(),
      prepareComposerReview: jest.fn(async () => ({
        kind: 'error' as const,
        code: 'COMPOSER_CONTACTS_RECONNECT_REQUIRED',
      })),
      requestReminderAuthorization: jest.fn(),
    };
    const { port } = createPort({
      continueWithGoogle,
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(iosDueHome)),
      getNextComposerProposal: jest.fn(async () =>
        ok({
          kind: 'ready' as const,
          proposalId: 'proposal-1' as ComposerProposalId,
          occurrenceId: 'occurrence-1' as OccurrenceId,
          occurrenceDate:
            '2026-07-18' as import('../domain/shared/temporal').LocalDate,
          recipient: 'Live Contact' as PrivateDisplayName,
        }),
      ),
    });

    await renderLiveApp(port, companionPort);
    await fireEvent.press(await screen.findByTestId('live-home-review-today'));
    await fireEvent.press(await screen.findByTestId('live-prepare-composer'));

    expect(
      await screen.findByText(
        'Reconnect Google Contacts, then review this Messages draft again.',
      ),
    ).toBeTruthy();
    expect(
      screen.queryByText(
        'Technical code: COMPOSER_CONTACTS_RECONNECT_REQUIRED',
      ),
    ).toBeNull();
    await fireEvent.press(screen.getByTestId('live-composer-support-toggle'));
    expect(
      screen.getByText('Technical code: COMPOSER_CONTACTS_RECONNECT_REQUIRED'),
    ).toBeTruthy();
    expect(screen.queryByTestId('live-open-composer')).toBeNull();

    await fireEvent.press(screen.getByTestId('live-composer-repair-contacts'));
    await waitFor(() => expect(continueWithGoogle).toHaveBeenCalledTimes(1));
    expect(
      await screen.findByText(
        'Contacts access was checked again. Review the Messages draft once more.',
      ),
    ).toBeTruthy();
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
      getSetup: jest.fn(async () => ok(iosCompleteSetup)),
      getNextComposerProposal: jest.fn(async () =>
        ok({ kind: 'none' as const }),
      ),
    });

    await renderLiveApp(port, companionPort);
    await openSettingsDestination('automation');

    await waitFor(() =>
      expect(screen.getByText(/Reminder status is unavailable/u)).toBeTruthy(),
    );
    expect(screen.queryByTestId('live-open-composer')).toBeNull();
  });

  it('requires a native privacy review and revalidates root lifecycle truth after confirmation', async () => {
    const prepareAction = jest.fn(async () =>
      ok(
        {
          handle: 'privacy-review-1' as PrivacyReviewHandle,
          kind: 'wipe-local-data' as const,
          titleKey: 'privacy.wipe.title',
          consequenceKeys: [
            'privacy.consequence.automation-paused',
            'privacy.consequence.local-data-erased',
          ],
          preissuedPermitMayFinish: false,
          remoteConnectionRequired: false,
          externalSmsCopiesNotErased: true as const,
        },
        revision('4'),
      ),
    );
    const confirmedOperation = {
      kind: 'queued' as const,
      id: 'privacy-operation-1' as PrivacyOperationId,
      action: 'wipe-local-data' as const,
      updatedAt: generatedAt,
    };
    const confirmAction = jest.fn(async () => ok(confirmedOperation));
    const getCurrentOperation = jest
      .fn()
      .mockResolvedValueOnce(ok({ kind: 'none' as const }))
      .mockResolvedValue(ok(confirmedOperation));
    const pendingAccount: AccountProjection = {
      kind: 'cleanup-pending',
      operation: 'sign-out',
      issue: {
        id: 'privacy-root-revalidation' as IssueId,
        code: 'coordination-unavailable',
        severity: 'blocking',
        blocks: ['test', 'activation', 'birthday'],
      },
    };
    const getBootstrap = jest
      .fn()
      .mockResolvedValueOnce(ok(completeBootstrap))
      .mockResolvedValue(
        ok({ ...completeBootstrap, account: pendingAccount }, revision('2')),
      );
    const { port } = createPort({
      prepareAction,
      confirmAction,
      getBootstrap,
      getCurrentOperation,
    });

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
    expect(screen.getByTestId('live-privacy-group-data-on-phone')).toBeTruthy();
    expect(
      screen.getByTestId('live-privacy-group-delete-account'),
    ).toBeTruthy();
    expect(screen.queryByTestId('live-privacy-action-group')).toBeNull();
    expect(screen.queryByTestId('live-privacy-prepare')).toBeNull();
    await fireEvent.press(screen.getByTestId('live-privacy-wipe-local-data'));
    expect(confirmAction).not.toHaveBeenCalled();
    await waitFor(() =>
      expect(screen.getByTestId('live-privacy-confirm')).toBeTruthy(),
    );
    expect(
      screen.getByTestId('live-privacy-review-focus').props.accessibilityRole,
    ).toBe('header');
    expect(
      screen.getByTestId('live-privacy-review-focus').props.accessibilityLabel,
    ).toBe('Confirm privacy action?');
    expect(confirmAction).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByTestId('live-privacy-confirm'));

    await waitFor(() =>
      expect(screen.getByTestId('live-setup-screen')).toBeTruthy(),
    );
    expect(screen.queryByTestId('live-privacy-screen')).toBeNull();
    expect(getBootstrap).toHaveBeenCalledTimes(2);
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
    const pendingOperation = {
      kind: 'remote-pending' as const,
      id: operationId,
      action: 'disconnect-contacts' as const,
      reason: 'coordination-unavailable' as const,
      updatedAt: generatedAt,
    };
    const completedOperation = {
      kind: 'complete' as const,
      id: operationId,
      action: 'disconnect-contacts' as const,
      completedAt: generatedAt,
      externalSmsCopiesNotErased: true as const,
    };
    const getCurrentOperation = jest
      .fn()
      .mockResolvedValueOnce(ok(pendingOperation))
      .mockResolvedValue(ok(completedOperation));
    const resumeOperation = jest.fn(async () => ok(completedOperation));
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
    const pendingDeletionOperation = {
      kind: 'remote-pending' as const,
      id: operationId,
      action: 'delete-account' as const,
      reason: 'network-offline' as const,
      updatedAt: generatedAt,
    };
    const confirmedDeletionOperation = {
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
    const confirmAction = jest.fn(async () => ok(confirmedDeletionOperation));
    const { port } = createPort({
      getCurrentOperation: jest
        .fn()
        .mockResolvedValueOnce(ok(pendingDeletionOperation))
        .mockResolvedValue(ok(confirmedDeletionOperation)),
      getLatestDeletionReceipt: jest
        .fn()
        .mockResolvedValueOnce(ok({ kind: 'none' as const }))
        .mockResolvedValue(ok(confirmedDeletionOperation)),
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
    expect(await screen.findByText('Erase this device now?')).toBeTruthy();
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
    await waitFor(() =>
      expect(screen.getByTestId('live-privacy-deletion-status')).toBeTruthy(),
    );
    expect(
      screen.getByText(
        'Local app data is erased; online deletion is not confirmed',
      ),
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
    const retryAccount = {
      kind: 'cleanup-pending' as const,
      operation: 'delete' as const,
      issue: {
        id: 'delete-replay' as IssueId,
        code: 'firebase-account-deleting' as const,
        severity: 'blocking' as const,
        blocks: ['activation'] as const,
      },
    };
    const continueWithGoogle = jest.fn(async () => ok(retryAccount));
    const { port } = createPort({
      getAccount: jest
        .fn()
        .mockResolvedValueOnce(ok(account))
        .mockResolvedValueOnce(ok(account))
        .mockResolvedValue(ok(retryAccount)),
      getCurrentOperation: jest.fn(async () => ok(unknownReceipt)),
      getLatestDeletionReceipt: jest
        .fn()
        .mockResolvedValueOnce(ok(unknownReceipt))
        .mockResolvedValue(ok(retryableReceipt)),
      checkAccountDeletionStatus: jest.fn(async () => ok(retryableReceipt)),
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
    expect(screen.getByText(/same-account recovery was checked/u)).toBeTruthy();
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

    expect(
      await screen.findByText('Cleanup status is unavailable'),
    ).toBeTruthy();
    expect(screen.queryByTestId('live-privacy-delete-account')).toBeNull();
    expect(screen.queryByTestId('live-privacy-disconnect-contacts')).toBeNull();
  });

  it('repairs an unreadable Android cleanup journal only through server proof', async () => {
    const operationId = 'privacy-operation-repair' as PrivacyOperationId;
    const repairAccount = {
      kind: 'cleanup-pending' as const,
      operation: 'repair' as const,
      issue: {
        id: 'cleanup-repair' as IssueId,
        code: 'coordination-unavailable' as const,
        severity: 'blocking' as const,
        blocks: ['test', 'activation', 'birthday'] as const,
      },
    };
    const repairedOperation = {
      kind: 'local-wiping' as const,
      id: operationId,
      action: 'disconnect-contacts' as const,
      updatedAt: generatedAt,
    };
    const repairLifecycleState = jest.fn(async () => ok(repairedOperation));
    const { port } = createPort({
      getAccount: jest.fn(async () => ok(repairAccount)),
      getCurrentOperation: jest
        .fn()
        .mockResolvedValueOnce(
          ok({
            kind: 'unavailable' as const,
            reason: 'coordination-unavailable' as const,
          }),
        )
        .mockResolvedValue(ok(repairedOperation)),
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
          consequenceKeys: [
            'privacy.consequence.automation-paused',
            'privacy.consequence.local-data-erased',
          ],
          preissuedPermitMayFinish: false,
          remoteConnectionRequired: false,
          externalSmsCopiesNotErased: true as const,
        },
        revision('4'),
      ),
    );
    const failedOperation = {
      kind: 'failed' as const,
      id: 'privacy-operation-1' as PrivacyOperationId,
      action: 'wipe-local-data' as const,
      reason: 'network-offline' as const,
      updatedAt: generatedAt,
    };
    const confirmAction = jest.fn(async () => ok(failedOperation));
    const { port } = createPort({
      prepareAction,
      confirmAction,
      getCurrentOperation: jest
        .fn()
        .mockResolvedValueOnce(ok({ kind: 'none' as const }))
        .mockResolvedValue(ok(failedOperation)),
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-settings'));
    await fireEvent.press(await screen.findByTestId('live-settings-privacy'));
    await fireEvent.press(
      await screen.findByTestId('live-privacy-wipe-local-data'),
    );
    await fireEvent.press(await screen.findByTestId('live-privacy-confirm'));

    expect(
      await screen.findAllByText(/protected operation failed/u),
    ).not.toHaveLength(0);
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
    expect(
      screen.queryByTestId('live-activity-recovery-activity-1'),
    ).toBeNull();
  });

  it('opens a native-projected recovery only from activity detail', async () => {
    const { port } = createPort({
      listActivity: jest.fn(async () =>
        ok({
          items: [
            {
              id: 'activity-paused' as import('../domain/shared/brand').ActivityId,
              kind: 'paused' as const,
              occurredAt: generatedAt,
              recovery: { route: 'automation' as const },
            },
          ],
        }),
      ),
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-home-activity'));
    expect(
      screen.queryByTestId('live-activity-recovery-activity-paused'),
    ).toBeNull();
    await fireEvent.press(
      await screen.findByTestId('live-activity-activity-paused'),
    );
    await fireEvent.press(
      await screen.findByTestId('live-activity-detail-recovery'),
    );

    expect(await screen.findByTestId('live-automation-screen')).toBeTruthy();
    expect(screen.queryByTestId('live-activity-screen')).toBeNull();
  });

  it('keeps an item-specific recovery available in activity detail', async () => {
    const { port } = createPort({
      listActivity: jest.fn(async () =>
        ok({
          items: [
            {
              id: 'activity-approval' as import('../domain/shared/brand').ActivityId,
              kind: 'approval-invalidated' as const,
              occurredAt: generatedAt,
              recovery: { route: 'people' as const },
            },
          ],
        }),
      ),
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-home-activity'));
    await fireEvent.press(
      await screen.findByTestId('live-activity-activity-approval'),
    );
    await fireEvent.press(
      await screen.findByTestId('live-activity-detail-recovery'),
    );

    expect(await screen.findByTestId('live-people-screen')).toBeTruthy();
    expect(screen.queryByTestId('live-activity-detail-screen')).toBeNull();
  });

  it('opens an actionable attention repair with the current native revision only', async () => {
    const listIssues = jest.fn(async () =>
      ok([
        {
          id: 'issue-1' as IssueId,
          code: 'background-restricted' as const,
          severity: 'blocking' as const,
          blocks: ['birthday' as const],
          action: {
            kind: 'native-action' as const,
            handle: 'action-1' as ActionHandle,
            labelKey: 'settings.background',
          },
        },
      ]),
    );
    const performAction = jest.fn(async () =>
      ok({ kind: 'cancelled' as const }, revision('2')),
    );
    const { port } = createPort({
      getHome: jest.fn(async () =>
        ok({
          ...liveHome('active'),
          counts: { ...liveHome('active').counts, needsAttention: 1 },
        }),
      ),
      listIssues,
      performAction,
    });

    await renderLiveApp(port);
    await waitFor(() =>
      expect(screen.getByTestId('live-home-attention')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-home-attention'));
    await waitFor(() =>
      expect(screen.getByTestId('live-attention-action-issue-1')).toBeTruthy(),
    );
    expect(
      screen.getByText(/Allow Birthday Autopilot to run in the background/u),
    ).toBeTruthy();
    expect(screen.queryByText(/background-restricted/u)).toBeNull();
    await fireEvent.press(screen.getByTestId('live-attention-support-toggle'));
    expect(screen.getByTestId('live-attention-support-issue-1')).toBeTruthy();
    expect(
      screen.getByText('Technical code: background-restricted'),
    ).toBeTruthy();
    await fireEvent.press(screen.getByTestId('live-attention-action-issue-1'));

    await waitFor(() =>
      expect(screen.getByText(/No repair is assumed/u)).toBeTruthy(),
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
    const initial: ContactDetail = {
      ...contactDetail(),
      summary: {
        ...contactSummary(),
        readiness: {
          kind: 'needs-attention',
          reasons: ['phone-choice-required', 'leap-policy-required'],
        },
        enrollment: {
          kind: 'enabled',
          approval: { kind: 'invalidated', reasons: ['phone-changed'] },
        },
      },
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
          selectable: false,
          issue: 'leap-policy-required',
        },
      ],
      selectedBirthdayId: birthdayId,
    };
    const afterPhone: ContactDetail = {
      ...initial,
      summary: {
        ...initial.summary,
        readiness: {
          kind: 'needs-attention',
          reasons: ['leap-policy-required'],
        },
      },
      selectedPhoneId: phoneId,
    };
    const afterBirthday: ContactDetail = {
      ...afterPhone,
      summary: {
        ...afterPhone.summary,
        readiness: {
          kind: 'needs-attention',
          reasons: ['approval-invalid'],
        },
      },
      birthdayChoices: afterPhone.birthdayChoices.map(choice => ({
        ...choice,
        issue: undefined,
      })),
    };
    const getPerson = jest
      .fn()
      .mockResolvedValueOnce(ok(initial, revision('1')))
      .mockResolvedValueOnce(ok(afterPhone, revision('2')))
      .mockResolvedValueOnce(ok(afterBirthday, revision('3')));
    const choosePhone = jest.fn(async () => ok(afterPhone, revision('2')));
    const chooseBirthday = jest.fn(async () =>
      ok(afterBirthday, revision('3')),
    );
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
    expect(screen.queryByTestId('live-choose-birthday-birthday-1')).toBeNull();
    await fireEvent.press(screen.getByTestId('live-choose-phone-phone-1'));
    await fireEvent.press(screen.getByTestId('live-confirm-choice'));
    await waitFor(() => expect(choosePhone).toHaveBeenCalledTimes(1));
    expect(choosePhone).toHaveBeenCalledWith({
      contactId: 'contact-live-1',
      phoneId: 'phone-1',
      expectedRevision: '1',
    });

    const selectedLeapChoice = await screen.findByTestId(
      'live-choose-birthday-birthday-1',
    );
    expect(screen.queryByTestId('live-choose-phone-phone-1')).toBeNull();
    await fireEvent.press(selectedLeapChoice);
    expect(screen.getByText('For a 29 February birthday')).toBeTruthy();
    expect(
      screen.getByTestId('live-confirm-choice').props.accessibilityState,
    ).toEqual({ disabled: true });
    await fireEvent.press(
      screen.getByRole('radio', { name: 'Use 28 February' }),
    );
    await fireEvent.press(screen.getByTestId('live-confirm-choice'));
    await waitFor(() => expect(chooseBirthday).toHaveBeenCalledTimes(1));
    expect(chooseBirthday).toHaveBeenCalledWith({
      contactId: 'contact-live-1',
      birthdayId: 'birthday-1',
      leapPolicy: 'feb-28',
      expectedRevision: '2',
    });
    await waitFor(() => expect(getPerson).toHaveBeenCalledTimes(3));
  });

  it('opens only the generic Google Contacts repair route and resyncs on return', async () => {
    let appStateHandler: ((state: 'active' | 'background') => void) | undefined;
    jest
      .spyOn(AppState, 'addEventListener')
      .mockImplementation((_event, handler) => {
        appStateHandler = handler as (state: 'active' | 'background') => void;
        return { remove: jest.fn() };
      });
    const canOpenURL = jest
      .spyOn(Linking, 'canOpenURL')
      .mockResolvedValue(true);
    const openURL = jest.spyOn(Linking, 'openURL').mockResolvedValue(true);
    const getPerson = jest.fn(async () =>
      ok({
        ...contactDetail(),
        summary: {
          ...contactSummary(),
          readiness: {
            kind: 'needs-attention' as const,
            reasons: ['birthday-missing' as const],
          },
        },
      }),
    );
    const syncContacts = jest.fn(async () =>
      ok({
        kind: 'fresh' as const,
        completedAt: generatedAt,
        contactCount: 1,
      }),
    );
    const { port } = createPort({ getPerson, syncContacts });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-people'));
    await fireEvent.press(
      await screen.findByTestId('live-person-contact-live-1'),
    );
    await fireEvent.press(
      await screen.findByTestId('live-person-open-google-contacts'),
    );

    expect(canOpenURL).toHaveBeenCalledWith('https://contacts.google.com/');
    expect(openURL).toHaveBeenCalledWith('https://contacts.google.com/');
    expect(openURL.mock.calls[0]?.[0]).not.toContain(contactId);
    expect(syncContacts).not.toHaveBeenCalled();

    await act(async () => appStateHandler?.('background'));
    await act(async () => appStateHandler?.('active'));

    await waitFor(() => expect(syncContacts).toHaveBeenCalledWith('user'));
    await waitFor(() => expect(getPerson).toHaveBeenCalledTimes(2));
    expect(await screen.findByText(/no repair was assumed/u)).toBeTruthy();
  });

  it('does not claim Google Contacts success when reload has another revision', async () => {
    let appStateHandler: ((state: 'active' | 'background') => void) | undefined;
    jest
      .spyOn(AppState, 'addEventListener')
      .mockImplementation((_event, handler) => {
        appStateHandler = handler as (state: 'active' | 'background') => void;
        return { remove: jest.fn() };
      });
    jest.spyOn(Linking, 'canOpenURL').mockResolvedValue(true);
    jest.spyOn(Linking, 'openURL').mockResolvedValue(true);
    const sourceRepairDetail: ContactDetail = {
      ...contactDetail(),
      summary: {
        ...contactSummary(),
        readiness: {
          kind: 'needs-attention',
          reasons: ['birthday-missing'],
        },
      },
    };
    const getPerson = jest
      .fn()
      .mockResolvedValueOnce(ok(sourceRepairDetail, revision('1')))
      .mockResolvedValueOnce(ok(sourceRepairDetail, revision('3')));
    const syncContacts = jest.fn(async () =>
      ok(
        {
          kind: 'fresh' as const,
          completedAt: generatedAt,
          contactCount: 1,
        },
        revision('2'),
      ),
    );
    const { port } = createPort({ getPerson, syncContacts });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-people'));
    await fireEvent.press(
      await screen.findByTestId('live-person-contact-live-1'),
    );
    await fireEvent.press(
      await screen.findByTestId('live-person-open-google-contacts'),
    );
    await act(async () => appStateHandler?.('background'));
    await act(async () => appStateHandler?.('active'));

    expect(await screen.findByText('Action not completed')).toBeTruthy();
    expect(screen.queryByText(/NATIVE_CONTRACT_INVALID/u)).toBeNull();
    expect(
      screen.getByText(
        'Contacts synced, but these details could not be checked again.',
      ),
    ).toBeTruthy();
    expect(
      screen.queryByText(
        'Contacts were synced again. Review the updated birthday and phone; no repair was assumed.',
      ),
    ).toBeNull();
    expect(syncContacts).toHaveBeenCalledTimes(1);
    expect(getPerson).toHaveBeenCalledTimes(2);
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
    const { port } = createPort({
      getPerson: jest.fn(async () => ok(contactDetailNeedingApproval())),
      prepareApprovals,
      confirmApprovals,
    });

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

  it('shows an already-valid exact approval as read-only with Close only', async () => {
    const prepareApprovals = jest.fn(async () =>
      ok(
        {
          handle: 'approval-review-valid' as ApprovalReviewHandle,
          items: [
            {
              platform: 'android' as const,
              contactId,
              recipient: 'Live Contact' as PrivateDisplayName,
              maskedPhone: '•••• 4321',
              birthdayLabel: '18 July',
              exactText: 'Already approved birthday text' as PrivateMessageText,
              windowLabel: '09:00–11:00',
              simLabel: 'SIM 1',
              segmentCount: 2,
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

    expect(
      await screen.findByText('Already approved birthday text'),
    ).toBeTruthy();
    expect(screen.getByText('2 SMS part(s)')).toBeTruthy();
    expect(screen.queryByTestId('live-confirm-approval')).toBeNull();
    expect(screen.getByTestId('live-close-approved-review')).toBeTruthy();
    expect(confirmApprovals).not.toHaveBeenCalled();

    await fireEvent.press(screen.getByTestId('live-close-approved-review'));
    expect(screen.queryByTestId('live-person-approval-review')).toBeNull();
    expect(confirmApprovals).not.toHaveBeenCalled();
    expect(prepareApprovals).toHaveBeenCalledWith({
      contactIds: [contactId],
      expectedRevision: '1',
    });
  });

  it('renders canonical Hindi safety disclosures instead of native English prose', async () => {
    await appI18n.changeLanguage('hi');
    const prepareActivation = jest.fn(async () =>
      ok(
        {
          platform: 'android' as const,
          handle: 'activation-review-hi' as ActivationReviewHandle,
          enabledRecipientCount: 1,
          attentionCount: 0,
          templatePreview: 'जन्मदिन मुबारक!' as PrivateMessageText,
          windowLabel: '09:00–11:00',
          simLabel: 'SIM 1',
          dailyCap: 10,
          limitationsDisclosure: 'NATIVE ENGLISH LIMITATIONS',
        },
        revision('4'),
      ),
    );
    const prepareApprovals = jest.fn(async () =>
      ok(
        {
          handle: 'approval-review-hi' as ApprovalReviewHandle,
          items: [
            {
              platform: 'android' as const,
              contactId,
              recipient: 'Live Contact' as PrivateDisplayName,
              maskedPhone: '•••• 4321',
              birthdayLabel: '18 जुलाई',
              exactText: 'जन्मदिन मुबारक!' as PrivateMessageText,
              windowLabel: '09:00–11:00',
              simLabel: 'SIM 1',
              segmentCount: 1,
              chargeDisclosure: 'NATIVE ENGLISH CHARGE DISCLOSURE',
              consentDisclosure: 'NATIVE ENGLISH CONSENT DISCLOSURE',
            },
          ],
          readyCount: 1,
          blockedCount: 0,
          explicitConfirmationRequired: true as const,
        },
        revision('6'),
      ),
    );
    const { port } = createPort({
      getHome: jest.fn(async () => ok(activationReadyHome)),
      getAccount: jest.fn(async () => ok(androidAccountForSender('test-only'))),
      getLatestTest: jest.fn(async () => ok(latestPassedTest)),
      getPerson: jest.fn(async () => ok(contactDetailNeedingApproval())),
      prepareActivation,
      prepareApprovals,
    });

    await renderLiveApp(port);
    await openSettingsDestination('automation');
    await fireEvent.press(await screen.findByTestId('live-review-activation'));
    expect(
      await screen.findByText(
        'Android बैकग्राउंड काम सर्वोत्तम प्रयास है। फ़ोन प्रतिबंध, नेटवर्क, SIM, कैरियर स्थिति या force-stop से शुभकामना देर से जा सकती है या रुक सकती है।',
      ),
    ).toBeTruthy();
    expect(screen.queryByText('NATIVE ENGLISH LIMITATIONS')).toBeNull();

    await fireEvent.press(screen.getByRole('button', { name: 'वापस' }));
    await fireEvent.press(await screen.findByTestId('live-tab-people'));
    await fireEvent.press(
      await screen.findByTestId('live-person-contact-live-1'),
    );
    await fireEvent.press(await screen.findByTestId('live-review-approval'));

    expect(
      await screen.findByText(
        'हर SMS भाग पर कैरियर शुल्क लग सकता है। रोमिंग केवल अलग से स्वीकृत होने पर इस्तेमाल होती है।',
      ),
    ).toBeTruthy();
    expect(
      screen.getByText(
        'पुष्टि करने पर सुरक्षित भावी जन्मदिन कामों के लिए यही प्राप्तकर्ता, चुना फ़ोन नंबर, जन्मदिन, संदेश, समय-सीमा, SIM और भाग योजना सहेजी जाती है।',
      ),
    ).toBeTruthy();
    expect(screen.queryByText('NATIVE ENGLISH CHARGE DISCLOSURE')).toBeNull();
    expect(screen.queryByText('NATIVE ENGLISH CONSENT DISCLOSURE')).toBeNull();
  });

  it('previews and shares content-free diagnostics with revision CAS', async () => {
    const previewDiagnostics = jest.fn(async () =>
      ok(
        {
          buildLabel: '0.1.0 test',
          androidOrIosVersionLabel: 'Android 16',
          capabilityCodes: ['distribution-channel-unapproved' as const],
          transitionCount: 4,
          earliestEventAt: instant('2026-07-10T07:00:00Z'),
          latestEventAt: instant('2026-07-12T08:30:00Z'),
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
      expect(screen.getByTestId('live-settings-help-legal')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-settings-help-legal'));
    await waitFor(() =>
      expect(screen.getByTestId('live-help-diagnostics')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-help-diagnostics'));
    await waitFor(() =>
      expect(screen.getByTestId('live-diagnostics-preview')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-diagnostics-preview'));
    await waitFor(() =>
      expect(screen.getByTestId('live-diagnostics-share')).toBeTruthy(),
    );
    expect(screen.getAllByText(/excludes names, phone numbers/u)).toHaveLength(
      1,
    );
    expect(screen.getByText('1 technical check reported')).toBeTruthy();
    expect(screen.getByText('Earliest retained status change')).toBeTruthy();
    expect(screen.getByText('Latest retained status change')).toBeTruthy();
    expect(screen.queryByText('2026-07-10T07:00:00Z')).toBeNull();
    expect(screen.queryByText('2026-07-12T08:30:00Z')).toBeNull();
    await fireEvent.press(screen.getByTestId('live-diagnostics-share'));

    await waitFor(() =>
      expect(screen.getByText(/sharing was cancelled/u)).toBeTruthy(),
    );
    expect(shareDiagnostics).toHaveBeenCalledWith({ expectedRevision: '5' });

    await fireEvent.press(screen.getByTestId('live-diagnostics-back'));
    await waitFor(() =>
      expect(screen.getByTestId('live-help-legal-screen')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-help-back'));
    await waitFor(() =>
      expect(screen.getByTestId('live-settings-screen')).toBeTruthy(),
    );
  });

  it('consumes cold and warm iOS reminder routes into Composer Review above Home', async () => {
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
          kind: 'automation-review' as const,
          routeId:
            'a4f2a2c0-8df3-4b2e-b9e4-661a2050d4a1' as import('../domain/shared/brand').NativeRouteId,
          source: 'birthday-reminder' as const,
        }),
      )
      .mockResolvedValue(ok({ kind: 'none' as const }));
    const harness = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(iosDueHome)),
      getNextComposerProposal: jest.fn(async () =>
        ok({ kind: 'none' as const }),
      ),
      getPendingRoute,
    });

    await renderLiveApp(harness.port);
    await waitFor(() =>
      expect(screen.getByTestId('live-composer-review-screen')).toBeTruthy(),
    );
    expect(screen.queryByTestId('live-automation-screen')).toBeNull();
    expect(screen.queryByTestId('live-message-screen')).toBeNull();
    expect(getPendingRoute).toHaveBeenCalledTimes(1);

    await fireEvent.press(screen.getByTestId('live-composer-review-back'));
    await waitFor(() =>
      expect(screen.getByTestId('live-home-screen')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('live-tab-settings'));
    await waitFor(() =>
      expect(screen.getByTestId('live-settings-screen')).toBeTruthy(),
    );
    await act(async () => {
      harness.emitRoute({ kind: 'available' });
    });
    await waitFor(() =>
      expect(screen.getByTestId('live-composer-review-screen')).toBeTruthy(),
    );
    expect(getPendingRoute).toHaveBeenCalledTimes(2);
    await fireEvent.press(screen.getByTestId('live-composer-review-back'));
    await waitFor(() =>
      expect(screen.getByTestId('live-home-screen')).toBeTruthy(),
    );
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
    await openSettingsDestination('schedule');
    await fireEvent.changeText(
      await screen.findByTestId('live-policy-start'),
      '10:00',
    );
    await fireEvent.changeText(screen.getByTestId('live-policy-end'), '12:00');
    await fireEvent.press(screen.getByTestId('live-policy-options-toggle'));
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

  it('keeps a dense iOS reminder preview valid beyond Android send caps', async () => {
    const configured = {
      kind: 'configured' as const,
      draft: {
        primaryStart: '09:00' as const,
        primaryEnd: '11:00' as const,
        latePolicy: { kind: 'none' as const },
        dailyCap: 1,
      },
    };
    const previewPolicy = jest.fn(async () =>
      ok(
        {
          kind: 'valid' as const,
          handle:
            'ios-dense-policy-review' as import('../domain/shared/brand').PolicyReviewHandle,
          summary: '09:00–11:00',
          maximumPlannedInLocalDay: 21,
          maximumPlannedInRolling24Hours: 27,
          simulatedDays: 400 as const,
        },
        revision('8'),
      ),
    );
    const savePolicy = jest.fn(async () => ok(iosHome.automation));
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
      openUserConfirmedComposer: jest.fn(),
      prepareComposerReview: jest.fn(),
      requestReminderAuthorization: jest.fn(),
    };
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(iosHome)),
      getAccount: jest.fn(async () => ok(iosAccount)),
      getPolicyEditor: jest.fn(async () => ok(configured)),
      getNextComposerProposal: jest.fn(async () =>
        ok({ kind: 'none' as const }),
      ),
      previewPolicy,
      savePolicy,
    });

    await renderLiveApp(port, companionPort);
    await openSettingsDestination('schedule');
    expect(screen.queryByTestId('live-policy-daily-cap')).toBeNull();
    expect(screen.queryByText(/60 civil dates/u)).toBeNull();
    await fireEvent.press(screen.getByTestId('live-policy-options-toggle'));
    expect(await screen.findByText(/60 civil dates/u)).toBeTruthy();
    await fireEvent.press(screen.getByTestId('live-policy-preview'));
    expect(await screen.findByText(/checked 400 days/u)).toBeTruthy();
    expect(screen.queryByText('21')).toBeNull();
    expect(screen.queryByText('27')).toBeNull();
    await fireEvent.press(screen.getByTestId('live-policy-save'));

    await waitFor(() => expect(savePolicy).toHaveBeenCalledTimes(1));
    expect(previewPolicy).toHaveBeenCalledWith({
      draft: configured.draft,
      expectedRevision: '1',
    });
    expect(savePolicy).toHaveBeenCalledWith({
      handle: 'ios-dense-policy-review',
      expectedRevision: '8',
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
    await openSettingsDestination('schedule');
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

  it('selects only Ready and Off people before confirmation', async () => {
    const secondId = 'contact-live-2' as ContactId;
    const enabledId = 'contact-live-enabled' as ContactId;
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
    const allContacts = [
      ...candidates,
      {
        ...contactSummary(),
        id: enabledId,
        displayName: 'Already Enabled' as PrivateDisplayName,
      },
    ];
    let currentRevision = '1';
    const listPeople = jest.fn(async () =>
      ok(
        { items: allContacts, totalCount: allContacts.length },
        revision(currentRevision),
      ),
    );
    const getHome = jest.fn(async () =>
      ok(liveHome('active'), revision(currentRevision)),
    );
    const prepareEnrollmentReview = jest.fn(async () => {
      currentRevision = '4';
      return ok(
        {
          handle:
            'enrollment-review-1' as import('../domain/shared/brand').EnrollmentReviewHandle,
          recipients: candidates,
          readyCount: 2,
          attentionCount: 0,
          explicitConfirmationRequired: true as const,
        },
        revision('4'),
      );
    });
    const confirmEnrollment = jest.fn(async () => {
      currentRevision = '5';
      return ok(
        {
          changedContactIds: [contactId, secondId],
          invalidatedApprovalCount: 0,
        },
        revision('5'),
      );
    });
    const { port } = createPort({
      getHome,
      listPeople,
      prepareEnrollmentReview,
      confirmEnrollment,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-people'));
    await fireEvent.press(
      await screen.findByTestId('live-people-select-page-ready'),
    );
    expect(screen.getByText('3 people')).toBeTruthy();
    expect((await screen.findAllByText('First Ready')).length).toBeGreaterThan(
      0,
    );
    expect(screen.getAllByText('Second Ready').length).toBeGreaterThan(0);
    expect(
      screen.getByText(
        'Review the 2 people below. 0 of 2 selected people were confirmed before this group.',
      ),
    ).toBeTruthy();
    expect(screen.queryByText(/0 of 3 selected people/u)).toBeNull();
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

  it('selects and confirms Ready and Off people beyond the first 50', async () => {
    const contacts = Array.from({ length: 51 }, (_, index) => ({
      ...contactSummary(),
      id: `contact-large-${index + 1}` as ContactId,
      displayName: `Large Person ${index + 1}` as PrivateDisplayName,
      enrollment: { kind: 'off' as const },
    }));
    const byId = new Map(contacts.map(contact => [contact.id, contact]));
    const enrolled = new Set<ContactId>();
    const reviewIds = new Map<string, readonly ContactId[]>();
    let currentRevision = 1;
    let reviewSequence = 0;
    const projected = (contact: ContactSummary): ContactSummary =>
      enrolled.has(contact.id)
        ? {
            ...contact,
            enrollment: { kind: 'enabled', approval: { kind: 'missing' } },
          }
        : contact;
    const listPeople = jest.fn(
      async (query: { cursor?: import('../domain/shared/brand').PageCursor }) =>
        query.cursor === undefined
          ? ok(
              {
                items: contacts.slice(0, 50).map(projected),
                nextCursor:
                  'large-page-2' as import('../domain/shared/brand').PageCursor,
                totalCount: contacts.length,
              },
              revision(String(currentRevision)),
            )
          : ok(
              {
                items: contacts.slice(50).map(projected),
                totalCount: contacts.length,
              },
              revision(String(currentRevision)),
            ),
    );
    const prepareEnrollmentReview = jest.fn(
      async ({ contactIds }: { contactIds: readonly ContactId[] }) => {
        reviewSequence += 1;
        currentRevision += 1;
        const handle =
          `large-enrollment-${reviewSequence}` as import('../domain/shared/brand').EnrollmentReviewHandle;
        reviewIds.set(handle, contactIds);
        return ok(
          {
            handle,
            recipients: contactIds.map(id => byId.get(id)!).map(projected),
            readyCount: contactIds.length,
            attentionCount: 0,
            explicitConfirmationRequired: true as const,
          },
          revision(String(currentRevision)),
        );
      },
    );
    const confirmEnrollment = jest.fn(
      async ({ handle }: { handle: string }) => {
        const changedContactIds = reviewIds.get(handle) ?? [];
        changedContactIds.forEach(id => enrolled.add(id));
        currentRevision += 1;
        return ok(
          { changedContactIds, invalidatedApprovalCount: 0 },
          revision(String(currentRevision)),
        );
      },
    );
    const { port } = createPort({
      confirmEnrollment,
      getHome: jest.fn(async () =>
        ok(liveHome('active'), revision(String(currentRevision))),
      ),
      listPeople,
      prepareEnrollmentReview,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-people'));
    await fireEvent.press(
      await screen.findByTestId('live-people-select-page-ready'),
    );
    await waitFor(() =>
      expect(prepareEnrollmentReview).toHaveBeenCalledTimes(1),
    );
    expect(prepareEnrollmentReview.mock.calls[0]?.[0].contactIds).toHaveLength(
      50,
    );

    await fireEvent.press(
      screen.getByTestId('live-people-confirm-page-enrollment'),
    );
    await waitFor(() =>
      expect(prepareEnrollmentReview).toHaveBeenCalledTimes(2),
    );
    expect(prepareEnrollmentReview.mock.calls[1]?.[0]).toEqual({
      contactIds: ['contact-large-51'],
      expectedRevision: '3',
    });
    expect(await screen.findByText('Large Person 51')).toBeTruthy();

    await fireEvent.press(
      screen.getByTestId('live-people-confirm-page-enrollment'),
    );
    await waitFor(() => expect(confirmEnrollment).toHaveBeenCalledTimes(2));
    expect(enrolled.size).toBe(51);
    expect(
      await screen.findByText(/51 people were updated across all pages/u),
    ).toBeTruthy();
  });

  it('does not prepare or claim a People selection when a later page fails', async () => {
    const contacts = Array.from({ length: 50 }, (_, index) => ({
      ...contactSummary(),
      id: `contact-failure-${index + 1}` as ContactId,
      displayName: `Failure Person ${index + 1}` as PrivateDisplayName,
      enrollment: { kind: 'off' as const },
    }));
    const laterProblem = {
      kind: 'internal' as const,
      supportCode: 'LATER_PEOPLE_PAGE_FAILED' as SafeSupportCode,
    };
    const listPeople = jest.fn(
      async (query: { cursor?: import('../domain/shared/brand').PageCursor }) =>
        query.cursor === undefined
          ? ok({
              items: contacts,
              nextCursor:
                'failure-page-2' as import('../domain/shared/brand').PageCursor,
              totalCount: 51,
            })
          : ({ kind: 'error', problem: laterProblem } as const),
    );
    const prepareEnrollmentReview = jest.fn(async () => internalError());
    const { port } = createPort({
      listPeople,
      prepareEnrollmentReview,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-people'));
    await fireEvent.press(
      await screen.findByTestId('live-people-select-page-ready'),
    );

    expect(await screen.findByText('Action not completed')).toBeTruthy();
    expect(prepareEnrollmentReview).not.toHaveBeenCalled();
    expect(
      screen.queryByText(/people were updated across all pages/u),
    ).toBeNull();
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
      getPerson: jest.fn(async () => ok(contactDetailNeedingApproval())),
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
      await screen.findByText(
        'You decide whether to tap Send after reviewing the recipient and text. Messages and iOS control the available sender line and final transport; this app cannot select or guarantee either.',
      ),
    ).toBeTruthy();
    expect(screen.queryByText('Review and tap Send yourself.')).toBeNull();
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
        effective: 'paused',
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
    let currentHome = notConfigured;
    let currentRevision = revision('1');
    let initialActivationCompleted = false;
    const getHome = jest.fn(async () => ok(currentHome, currentRevision));
    const prepareActivation = jest.fn(async () =>
      ok(
        {
          platform: 'ios' as const,
          handle: 'ios-activation-review' as ActivationReviewHandle,
          reminderRecipientCount: 1,
          plannedReminderCount: 1,
          reminderWindowLabel: '09:00–11:00',
          reminderHorizon: 'full' as const,
          coexistence: 'clear' as const,
          contactsReady: true,
          messageUiReady: true,
          protectedStorageReady: true,
          readiness: iosReadiness,
          deliveryMode: 'user-controlled-composer' as const,
          limitationsDisclosure: 'Reminders are best effort.',
        },
        revision('7'),
      ),
    );
    const activate = jest.fn(async () => {
      currentHome = iosHome;
      currentRevision = revision('2');
      initialActivationCompleted = true;
      return ok(iosHome.automation, currentRevision);
    });
    const pauseAll = jest.fn(async () => {
      currentHome = paused;
      currentRevision = revision('3');
      return ok(paused.automation, currentRevision);
    });
    const companionPort: LiveCompanionPort = {
      canOpenComposer: jest.fn(async () => false),
      getReminderStatus: jest.fn(async () => ({
        kind: 'ok' as const,
        value: {
          authorization: 'authorized' as const,
          failedCount: 0,
          kind: 'ok' as const,
          plannedDateCount: 1,
          scheduledCount: currentHome.automation.desired === 'paused' ? 0 : 1,
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
      getAccount: jest.fn(async () => ok(iosAccount, currentRevision)),
      getHome,
      getSetup: jest.fn(async () =>
        ok(
          {
            ...iosCompleteSetup,
            initialActivationCompleted,
            automation: currentHome.automation,
          },
          currentRevision,
        ),
      ),
      getMessageEditor: jest.fn(async () =>
        ok(configuredMessage, currentRevision),
      ),
      getPolicyEditor: jest.fn(async () =>
        ok(configuredPolicy, currentRevision),
      ),
      getNextComposerProposal: jest.fn(async () =>
        ok({ kind: 'none' as const }),
      ),
      prepareActivation,
      activate,
      pauseAll,
    });

    await renderLiveApp(port, companionPort);
    await fireEvent.press(await screen.findByTestId('live-product-setup-next'));
    expect(await screen.findByTestId('live-automation-screen')).toBeTruthy();
    await fireEvent.press(
      await screen.findByTestId('live-ios-review-activation'),
    );
    expect(
      (await screen.findByTestId('live-ios-activation-review-focus')).props
        .accessibilityRole,
    ).toBe('header');
    expect(
      screen.getByText(
        'This enables reminders only. iPhone never sends automatically; you review an editable system Messages screen and tap Send yourself. SMS or MMS carrier charges may apply, and iOS/Messages controls the available sender line and transport.',
      ),
    ).toBeTruthy();
    expect(screen.getByText('09:00–11:00')).toBeTruthy();
    expect(
      screen.getByText('Current reminder horizon is fully reconciled'),
    ).toBeTruthy();
    expect(
      screen.getByText('No Android sender is managing this account'),
    ).toBeTruthy();
    expect(screen.queryByText('Reminders are best effort.')).toBeNull();
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
      getSetup: jest.fn(async () => ok(iosCompleteSetup)),
      getNextComposerProposal: jest.fn(async () =>
        ok({ kind: 'none' as const }),
      ),
    });

    await renderLiveApp(port, companionPort);
    await openSettingsDestination('automation');
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
          maskedDestination: '•••• 4321',
          exactText: 'Happy birthday!' as PrivateMessageText,
          choice: 'send-through-normal-path' as const,
          alternativeChoice: 'start-next-year' as const,
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
    const todayAction = await screen.findByTestId('live-home-review-today');
    expect(todayAction.props.accessibilityLabel).toBe(
      'Review today’s birthday decision',
    );
    expect(screen.getAllByTestId('live-home-review-today')).toHaveLength(1);
    expect(screen.queryByTestId('live-home-attention')).toBeNull();
    expect(screen.queryByTestId('live-home-automation')).toBeNull();
    await fireEvent.press(todayAction);
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
      choice: 'send-through-normal-path',
      expectedRevision: '5',
    });
  });

  it('does not reinstall a deferred today review after Home is invalidated', async () => {
    const todayHome: HomeProjection = {
      ...liveHome('active'),
      next: {
        ...nextBirthday,
        occurrenceId: 'occurrence-stale-review' as OccurrenceId,
      },
      counts: { ...liveHome('active').counts, today: 1 },
    };
    const getHome = jest
      .fn()
      .mockResolvedValueOnce(ok(todayHome, revision('1')))
      .mockResolvedValue(
        ok(
          {
            ...todayHome,
            next: {
              ...todayHome.next!,
              occurrenceId: 'occurrence-current' as OccurrenceId,
            },
          },
          revision('2'),
        ),
      );
    let harness!: PortHarness;
    const prepareTodayOccurrence = jest.fn(async () => {
      harness.emit({ revision: revision('2'), areas: ['home'] });
      return ok(
        {
          handle:
            'stale-today-review' as import('../domain/shared/brand').TodayOccurrenceReviewHandle,
          recipient: 'Private old recipient' as PrivateDisplayName,
          maskedDestination: '•••• 9999',
          exactText: 'Stale private message' as PrivateMessageText,
          choice: 'send-through-normal-path' as const,
          limitationsDisclosure: 'Review disclosure.',
        },
        revision('3'),
      );
    });
    const confirmTodayOccurrence = jest.fn();
    harness = createPort({
      confirmTodayOccurrence,
      getHome,
      prepareTodayOccurrence,
    });

    await renderLiveApp(harness.port);
    await fireEvent.press(await screen.findByTestId('live-home-review-today'));
    await waitFor(() =>
      expect(prepareTodayOccurrence).toHaveBeenCalledTimes(1),
    );

    await waitFor(() =>
      expect(getHome.mock.calls.length).toBeGreaterThanOrEqual(2),
    );

    await waitFor(() =>
      expect(screen.queryByText('Stale private message')).toBeNull(),
    );
    expect(screen.queryByText('Private old recipient')).toBeNull();
    expect(screen.queryByTestId('live-home-confirm-today')).toBeNull();
    expect(confirmTodayOccurrence).not.toHaveBeenCalled();
  });

  it('clears exact today review content when the app returns to foreground', async () => {
    const appStateHandlers: Array<(state: 'active' | 'background') => void> =
      [];
    jest
      .spyOn(AppState, 'addEventListener')
      .mockImplementation((_event, handler) => {
        appStateHandlers.push(
          handler as (state: 'active' | 'background') => void,
        );
        return { remove: jest.fn() };
      });
    const todayHome: HomeProjection = {
      ...liveHome('active'),
      next: nextBirthday,
      counts: { ...liveHome('active').counts, today: 1 },
    };
    const confirmTodayOccurrence = jest.fn();
    const { port } = createPort({
      confirmTodayOccurrence,
      getHome: jest.fn(async () => ok(todayHome, revision('1'))),
      prepareTodayOccurrence: jest.fn(async () =>
        ok(
          {
            handle:
              'foreground-today-review' as import('../domain/shared/brand').TodayOccurrenceReviewHandle,
            recipient: 'Foreground recipient' as PrivateDisplayName,
            maskedDestination: '•••• 4321',
            exactText: 'Foreground private message' as PrivateMessageText,
            choice: 'send-through-normal-path' as const,
            limitationsDisclosure: 'Review disclosure.',
          },
          revision('2'),
        ),
      ),
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-home-review-today'));
    expect(await screen.findByText('Foreground private message')).toBeTruthy();

    await act(async () => {
      appStateHandlers.forEach(handler => handler('active'));
    });

    await waitFor(() =>
      expect(screen.queryByText('Foreground private message')).toBeNull(),
    );
    expect(screen.queryByText('Foreground recipient')).toBeNull();
    expect(screen.queryByTestId('live-home-confirm-today')).toBeNull();
    expect(confirmTodayOccurrence).not.toHaveBeenCalled();
  });

  it('does not restore a stale today error after invalidation during recovery reload', async () => {
    const todayHome: HomeProjection = {
      ...liveHome('active'),
      next: nextBirthday,
      counts: { ...liveHome('active').counts, today: 1 },
    };
    let harness!: PortHarness;
    let homeRequests = 0;
    const getHome = jest.fn(async () => {
      homeRequests += 1;
      if (homeRequests === 2) {
        harness.emit({ revision: revision('3'), areas: ['home'] });
      }
      return ok(todayHome, revision(String(homeRequests)));
    });
    harness = createPort({
      getHome,
      prepareTodayOccurrence: jest.fn(async () => ({
        kind: 'error' as const,
        problem: {
          kind: 'stale-revision' as const,
          latestRevision: revision('2'),
        },
      })),
    });

    await renderLiveApp(harness.port);
    await fireEvent.press(await screen.findByTestId('live-home-review-today'));

    await waitFor(() =>
      expect(getHome.mock.calls.length).toBeGreaterThanOrEqual(3),
    );
    expect(screen.queryByText('Action not completed')).toBeNull();
  });

  it('withholds Home mutations when a retained projection refresh fails', async () => {
    const todayHome: HomeProjection = {
      ...liveHome('active'),
      next: nextBirthday,
      counts: { ...liveHome('active').counts, today: 1 },
    };
    const getHome = jest
      .fn()
      .mockResolvedValueOnce(ok(todayHome, revision('1')))
      .mockResolvedValue(internalError<HomeProjection>());
    const prepareTodayOccurrence = jest.fn();
    const pauseAll = jest.fn();
    const harness = createPort({ getHome, pauseAll, prepareTodayOccurrence });

    await renderLiveApp(harness.port);
    expect(await screen.findByTestId('live-home-review-today')).toBeTruthy();
    expect(screen.getByTestId('live-home-pause')).toBeTruthy();

    await act(async () => {
      harness.emit({ revision: revision('2'), areas: ['home'] });
    });

    expect(await screen.findByText('Could not refresh')).toBeTruthy();
    expect(screen.queryByTestId('live-home-review-today')).toBeNull();
    expect(screen.queryByTestId('live-home-pause')).toBeNull();
    expect(
      screen.queryByTestId('live-home-approved-message-toggle'),
    ).toBeNull();
    expect(prepareTodayOccurrence).not.toHaveBeenCalled();
    expect(pauseAll).not.toHaveBeenCalled();
  });

  it('opens the Android system composer only after its explicit reviewed choice', async () => {
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
            'today-review-2' as import('../domain/shared/brand').TodayOccurrenceReviewHandle,
          recipient: 'Live Contact' as PrivateDisplayName,
          maskedDestination: '•••• 4321',
          exactText: 'Happy birthday!' as PrivateMessageText,
          choice: 'open-system-composer' as const,
          alternativeChoice: 'start-next-year' as const,
          limitationsDisclosure:
            'Opening the composer retires unattended automation for today.',
        },
        revision('6'),
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
    expect(screen.getAllByText('•••• 4321')).toHaveLength(2);
    expect(
      screen.getByTestId('live-home-confirm-today-next-year'),
    ).toBeTruthy();
    expect(confirmTodayOccurrence).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByTestId('live-home-confirm-today'));

    await waitFor(() =>
      expect(confirmTodayOccurrence).toHaveBeenCalledWith({
        handle: 'today-review-2',
        choice: 'open-system-composer',
        expectedRevision: '6',
      }),
    );
  });

  it('keeps start-next-year as a separate explicit choice from a composer review', async () => {
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
    const confirmTodayOccurrence = jest.fn(async () =>
      ok(todayHome.automation),
    );
    const { port } = createPort({
      getHome: jest.fn(async () => ok(todayHome)),
      prepareTodayOccurrence: jest.fn(async () =>
        ok(
          {
            handle:
              'today-review-3' as import('../domain/shared/brand').TodayOccurrenceReviewHandle,
            recipient: 'Live Contact' as PrivateDisplayName,
            maskedDestination: '•••• 4321',
            exactText: 'Happy birthday!' as PrivateMessageText,
            choice: 'open-system-composer' as const,
            alternativeChoice: 'start-next-year' as const,
            limitationsDisclosure:
              'Opening the composer retires unattended automation for today.',
          },
          revision('7'),
        ),
      ),
      confirmTodayOccurrence,
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-home-review-today'));
    await fireEvent.press(
      screen.getByTestId('live-home-confirm-today-next-year'),
    );

    await waitFor(() =>
      expect(confirmTodayOccurrence).toHaveBeenCalledWith({
        handle: 'today-review-3',
        choice: 'start-next-year',
        expectedRevision: '7',
      }),
    );
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
    await fireEvent.press(
      screen.getByTestId('live-privacy-data-details-toggle'),
    );

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
    const unavailableReceipt = {
      kind: 'unavailable' as const,
      reason: 'coordination-unavailable' as const,
    };
    const { port } = createPort({
      getLatestDeletionReceipt: jest
        .fn()
        .mockResolvedValueOnce(ok(drainingReceipt))
        .mockResolvedValue(ok(unavailableReceipt)),
      checkAccountDeletionStatus: jest.fn(async () => ok(unavailableReceipt)),
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

  it('gives a Setup lifecycle-repair state precedence over a complete Bootstrap account', async () => {
    const { repairingSetup } = lifecycleRepairFixture();
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(completeBootstrap)),
      getSetup: jest.fn(async () => ok(repairingSetup)),
    });

    await renderLiveApp(port);

    expect(await screen.findByTestId('live-setup-repair-reauth')).toBeTruthy();
    expect(screen.queryByTestId('live-app-shell')).toBeNull();
    expect(screen.queryByTestId('live-product-setup-journey')).toBeNull();
    expect(screen.queryByTestId('live-setup-defer')).toBeNull();
  });

  it('fails closed when Bootstrap requires lifecycle repair but Setup does not', async () => {
    const { repairingBootstrap } = lifecycleRepairFixture();
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(repairingBootstrap)),
      getSetup: jest.fn(async () => ok(completeSetup)),
    });

    await renderLiveApp(port);

    expect(
      await screen.findByTestId('live-setup-projection-conflict'),
    ).toBeTruthy();
    expect(screen.queryByTestId('live-app-shell')).toBeNull();
    expect(screen.queryByTestId('live-setup-action')).toBeNull();
    expect(screen.queryByTestId('live-setup-defer')).toBeNull();
  });

  it('fails closed before ordinary early-setup actions when Bootstrap and Setup revisions differ', async () => {
    const signedOutAccount: AccountProjection = {
      kind: 'signed-out',
      retainedSetup: 'none',
    };
    const earlyBootstrap: BootstrapProjection = {
      ...completeBootstrap,
      account: signedOutAccount,
      setupStep: 'google-account',
    };
    const earlySetup: SetupProjection = {
      ...completeSetup,
      account: signedOutAccount,
      initialActivationCompleted: false,
      step: 'google-account',
    };
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(earlyBootstrap, revision('1'))),
      getSetup: jest.fn(async () => ok(earlySetup, revision('2'))),
    });

    await renderLiveApp(port);

    expect(
      await screen.findByTestId('live-setup-projection-conflict'),
    ).toBeTruthy();
    expect(screen.queryByTestId('live-setup-action')).toBeNull();
    expect(screen.queryByTestId('live-setup-defer')).toBeNull();
  });

  it('fails closed before the product journey or shell when Bootstrap and Setup revisions differ', async () => {
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(completeBootstrap, revision('1'))),
      getSetup: jest.fn(async () => ok(completeSetup, revision('2'))),
    });

    await renderLiveApp(port);

    expect(
      await screen.findByTestId('native-product-setup-unavailable'),
    ).toBeTruthy();
    expect(screen.queryByTestId('live-app-shell')).toBeNull();
    expect(screen.queryByTestId('live-product-setup-journey')).toBeNull();
  });

  it.each([
    ['disconnect', 'disconnect-contacts'],
    ['revoke', 'revoke-google-access'],
    ['sign-out', 'sign-out-wipe'],
  ] as const)(
    'routes complete-bootstrap Android %s cleanup to its exact resumable operation',
    async (accountOperation, privacyAction) => {
      const cleanupAccount: AccountProjection = {
        kind: 'cleanup-pending',
        operation: accountOperation,
        issue: {
          id: `cleanup-${accountOperation}` as IssueId,
          code: 'coordination-unavailable',
          severity: 'blocking',
          blocks: ['test', 'activation', 'birthday'],
        },
      };
      const cleanupBootstrap: BootstrapProjection = {
        ...completeBootstrap,
        account: cleanupAccount,
        setupStep: 'complete',
      };
      const cleanupSetup: SetupProjection = {
        ...completeSetup,
        step: 'complete',
        account: cleanupAccount,
        automation: {
          platform: 'android',
          desired: 'paused',
          effective: 'deleting',
          readiness,
        },
      };
      const operationId = `privacy-${accountOperation}` as PrivacyOperationId;
      const currentOperation = {
        kind: 'remote-pending' as const,
        id: operationId,
        action: privacyAction,
        reason: 'coordination-unavailable' as const,
        updatedAt: generatedAt,
      };
      const resumeOperation = jest.fn(async () =>
        ok({
          kind: 'complete' as const,
          id: operationId,
          action: privacyAction,
          completedAt: generatedAt,
          externalSmsCopiesNotErased: true as const,
        }),
      );
      const getOperation = jest.fn(async () => ok(currentOperation));
      const { port } = createPort({
        getBootstrap: jest.fn(async () => ok(cleanupBootstrap)),
        getCurrentOperation: jest.fn(async () => ok(currentOperation)),
        getOperation,
        getSetup: jest.fn(async () => ok(cleanupSetup)),
        resumeOperation,
      });

      await renderLiveApp(port);

      expect(await screen.findByTestId('live-setup-screen')).toBeTruthy();
      expect(screen.queryByTestId('live-app-shell')).toBeNull();
      expect(screen.queryByTestId('live-setup-action')).toBeNull();
      await fireEvent.press(screen.getByTestId('live-setup-refresh-cleanup'));
      await waitFor(() =>
        expect(getOperation).toHaveBeenCalledWith(operationId),
      );
      await fireEvent.press(screen.getByTestId('live-setup-resume-cleanup'));
      await waitFor(() => expect(resumeOperation).toHaveBeenCalledTimes(1));
      expect(resumeOperation).toHaveBeenCalledWith(operationId);
    },
  );

  it('withholds a retained cleanup operation after its status refresh fails', async () => {
    const cleanupAccount: AccountProjection = {
      kind: 'cleanup-pending',
      operation: 'revoke',
      issue: {
        id: 'cleanup-stale-operation' as IssueId,
        code: 'coordination-unavailable',
        severity: 'blocking',
        blocks: ['test', 'activation', 'birthday'],
      },
    };
    const cleanupBootstrap: BootstrapProjection = {
      ...completeBootstrap,
      account: cleanupAccount,
      setupStep: 'complete',
    };
    const cleanupSetup: SetupProjection = {
      ...completeSetup,
      account: cleanupAccount,
      automation: {
        platform: 'android',
        desired: 'paused',
        effective: 'deleting',
        readiness,
      },
    };
    const getCurrentOperation = jest
      .fn()
      .mockResolvedValueOnce(
        ok({
          kind: 'remote-pending' as const,
          id: 'privacy-stale-operation' as PrivacyOperationId,
          action: 'revoke-google-access' as const,
          reason: 'coordination-unavailable' as const,
          updatedAt: generatedAt,
        }),
      )
      .mockResolvedValue(internalError());
    const harness = createPort({
      getBootstrap: jest.fn(async () => ok(cleanupBootstrap)),
      getCurrentOperation,
      getSetup: jest.fn(async () => ok(cleanupSetup)),
    });

    await renderLiveApp(harness.port);
    expect(await screen.findByTestId('live-setup-resume-cleanup')).toBeTruthy();

    await act(async () => {
      harness.emit({ revision: revision('2'), areas: ['privacy'] });
    });

    expect(
      await screen.findByTestId('live-setup-cleanup-unavailable'),
    ).toBeTruthy();
    expect(screen.queryByTestId('live-setup-resume-cleanup')).toBeNull();
  });

  it('routes complete-bootstrap iOS cleanup to its exact resumable operation', async () => {
    const cleanupAccount: AccountProjection = {
      kind: 'cleanup-pending',
      operation: 'disconnect',
      issue: {
        id: 'ios-cleanup-disconnect' as IssueId,
        code: 'coordination-unavailable',
        severity: 'blocking',
        blocks: ['composer'],
      },
    };
    const cleanupBootstrap: BootstrapProjection = {
      ...iosBootstrap,
      account: cleanupAccount,
      setupStep: 'complete',
    };
    const cleanupSetup: SetupProjection = {
      step: 'complete',
      initialActivationCompleted: true,
      eligibility: iosBootstrap.eligibility,
      account: cleanupAccount,
      contacts: {
        kind: 'fresh',
        completedAt: generatedAt,
        contactCount: 1,
      },
      readiness: iosReadiness,
      automation: {
        platform: 'ios',
        desired: 'paused',
        effective: 'paused',
        readiness: iosReadiness,
      },
    };
    const operationId = 'privacy-ios-disconnect' as PrivacyOperationId;
    const resumeOperation = jest.fn(async () =>
      ok({
        kind: 'complete' as const,
        id: operationId,
        action: 'disconnect-contacts' as const,
        completedAt: generatedAt,
        externalSmsCopiesNotErased: true as const,
      }),
    );
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(cleanupBootstrap)),
      getCurrentOperation: jest.fn(async () =>
        ok({
          kind: 'remote-pending' as const,
          id: operationId,
          action: 'disconnect-contacts' as const,
          reason: 'coordination-unavailable' as const,
          updatedAt: generatedAt,
        }),
      ),
      getSetup: jest.fn(async () => ok(cleanupSetup)),
      resumeOperation,
    });

    await renderLiveApp(port);

    expect(await screen.findByTestId('live-setup-screen')).toBeTruthy();
    expect(screen.queryByTestId('live-app-shell')).toBeNull();
    expect(screen.queryByTestId('live-setup-action')).toBeNull();
    await fireEvent.press(screen.getByTestId('live-setup-resume-cleanup'));
    await waitFor(() =>
      expect(resumeOperation).toHaveBeenCalledWith(operationId),
    );
  });

  it('routes complete-bootstrap iOS deletion to authoritative deletion status', async () => {
    const deletingAccount: AccountProjection = {
      kind: 'cleanup-pending',
      operation: 'delete',
      issue: {
        id: 'ios-cleanup-delete' as IssueId,
        code: 'coordination-unavailable',
        severity: 'blocking',
        blocks: ['composer'],
      },
    };
    const deletingBootstrap: BootstrapProjection = {
      ...iosBootstrap,
      account: deletingAccount,
      setupStep: 'complete',
    };
    const deletingSetup: SetupProjection = {
      step: 'complete',
      initialActivationCompleted: true,
      eligibility: iosBootstrap.eligibility,
      account: deletingAccount,
      contacts: {
        kind: 'fresh',
        completedAt: generatedAt,
        contactCount: 1,
      },
      readiness: iosReadiness,
      automation: {
        platform: 'ios',
        desired: 'paused',
        effective: 'paused',
        readiness: iosReadiness,
      },
    };
    const checkAccountDeletionStatus = jest.fn(async () =>
      ok({
        kind: 'unavailable' as const,
        reason: 'coordination-unavailable' as const,
      }),
    );
    const { port } = createPort({
      checkAccountDeletionStatus,
      getBootstrap: jest.fn(async () => ok(deletingBootstrap)),
      getSetup: jest.fn(async () => ok(deletingSetup)),
    });

    await renderLiveApp(port);

    expect(await screen.findByTestId('live-setup-screen')).toBeTruthy();
    expect(screen.queryByTestId('live-app-shell')).toBeNull();
    await fireEvent.press(
      screen.getByRole('button', { name: 'Check account deletion' }),
    );
    await waitFor(() =>
      expect(checkAccountDeletionStatus).toHaveBeenCalledTimes(1),
    );
    expect(screen.queryByTestId('live-setup-resume-cleanup')).toBeNull();
  });

  it('routes a complete-bootstrap connected Android sender deleting state to exact lifecycle recovery', async () => {
    const deletingSenderAccount: AccountProjection = {
      kind: 'connected',
      displayEmail: 'user@example.test' as PrivateEmail,
      sender: {
        platform: 'android',
        kind: 'deleting',
        preissuedPermitMayFinish: true,
      },
    };
    const deletingBootstrap: BootstrapProjection = {
      ...completeBootstrap,
      account: deletingSenderAccount,
      setupStep: 'complete',
    };
    const deletingSetup: SetupProjection = {
      ...completeSetup,
      step: 'complete',
      account: deletingSenderAccount,
      automation: {
        platform: 'android',
        desired: 'paused',
        effective: 'deleting',
        readiness,
      },
    };
    const operationId = 'privacy-connected-deleting' as PrivacyOperationId;
    const resumeOperation = jest.fn(async () =>
      ok({
        kind: 'remote-draining' as const,
        id: operationId,
        action: 'delete-account' as const,
        updatedAt: generatedAt,
      }),
    );
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(deletingBootstrap)),
      getCurrentOperation: jest.fn(async () =>
        ok({
          kind: 'remote-pending' as const,
          id: operationId,
          action: 'delete-account' as const,
          reason: 'coordination-unavailable' as const,
          updatedAt: generatedAt,
        }),
      ),
      getSetup: jest.fn(async () => ok(deletingSetup)),
      resumeOperation,
    });

    await renderLiveApp(port);

    expect(await screen.findByTestId('live-setup-screen')).toBeTruthy();
    expect(screen.queryByTestId('live-app-shell')).toBeNull();
    expect(screen.queryByTestId('live-setup-action')).toBeNull();
    await fireEvent.press(screen.getByTestId('live-setup-resume-cleanup'));
    await waitFor(() =>
      expect(resumeOperation).toHaveBeenCalledWith(operationId),
    );
  });

  it('keeps the deletion stage neutral and reachable from a complete bootstrap', async () => {
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
      setupStep: 'complete',
    };
    const deletingSetup: SetupProjection = {
      step: 'compatibility',
      initialActivationCompleted: false,
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
    let setupHardwareBack: (() => boolean | null | undefined) | undefined;
    const setupBackSpy = jest
      .spyOn(BackHandler, 'addEventListener')
      .mockImplementation((_event, handler) => {
        setupHardwareBack = () => handler({} as never);
        return { remove: jest.fn() };
      });
    await fireEvent.press(screen.getByTestId('live-setup-help-legal'));
    expect(await screen.findByTestId('live-help-legal-screen')).toBeTruthy();
    expect(screen.getByText('Help, legal and about')).toBeTruthy();
    expect(
      screen.getByTestId('route-accessibility-focus').props.accessibilityLabel,
    ).toBe('Help, legal and about');

    await act(async () => {
      expect(setupHardwareBack?.()).toBe(true);
    });
    await waitFor(() =>
      expect(screen.getByTestId('live-setup-screen')).toBeTruthy(),
    );
    expect(
      screen.getByTestId('route-accessibility-focus').props.accessibilityLabel,
    ).toBe('Step 1 of 4: Check this phone');
    setupBackSpy.mockRestore();
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
      initialActivationCompleted: false,
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
    expect(screen.getByText(/same-account recovery was checked/u)).toBeTruthy();
  });

  it('keeps exact-account lifecycle repair reachable from a complete bootstrap', async () => {
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
      setupStep: 'complete',
    };
    const repairingSetup: SetupProjection = {
      step: 'google-account',
      initialActivationCompleted: true,
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
    expect(
      screen.queryByTestId('live-setup-repair-revoke-google-access'),
    ).toBeNull();
  });

  it('expires lifecycle repair identity before the native five-minute lease', async () => {
    const { repairingAccount, repairingBootstrap, repairingSetup } =
      lifecycleRepairFixture();
    const { port } = createPort({
      continueWithGoogle: jest.fn(async () =>
        ok(repairingAccount, revision('7')),
      ),
      getBootstrap: jest.fn(async () => ok(repairingBootstrap, revision('7'))),
      getSetup: jest.fn(async () => ok(repairingSetup, revision('7'))),
    });

    const realSetTimeout = globalThis.setTimeout;
    let expireIdentity: (() => void) | undefined;
    jest.spyOn(Date, 'now').mockReturnValue(1_721_347_200_000);
    jest
      .spyOn(globalThis, 'setTimeout')
      .mockImplementation((handler, delay, ...arguments_) => {
        if (delay === 4 * 60 * 1_000) {
          expireIdentity = handler as () => void;
          return 98_765 as unknown as ReturnType<typeof setTimeout>;
        }
        return realSetTimeout(handler, delay, ...arguments_);
      });

    await renderLiveApp(port);
    await fireEvent.press(
      await screen.findByTestId('live-setup-repair-reauth'),
    );
    expect(
      await screen.findByTestId('live-setup-repair-disconnect-contacts'),
    ).toBeTruthy();

    await act(async () => {
      expireIdentity?.();
    });

    expect(
      screen.queryByTestId('live-setup-repair-disconnect-contacts'),
    ).toBeNull();
  });

  it('revokes lifecycle repair identity when the app backgrounds', async () => {
    const appStateHandlers: Array<(state: 'active' | 'background') => void> =
      [];
    jest
      .spyOn(AppState, 'addEventListener')
      .mockImplementation((_event, handler) => {
        appStateHandlers.push(
          handler as (state: 'active' | 'background') => void,
        );
        return { remove: jest.fn() };
      });
    const { repairingAccount, repairingBootstrap, repairingSetup } =
      lifecycleRepairFixture();
    const { port } = createPort({
      continueWithGoogle: jest.fn(async () =>
        ok(repairingAccount, revision('8')),
      ),
      getBootstrap: jest.fn(async () => ok(repairingBootstrap, revision('8'))),
      getSetup: jest.fn(async () => ok(repairingSetup, revision('8'))),
    });

    await renderLiveApp(port);
    await fireEvent.press(
      await screen.findByTestId('live-setup-repair-reauth'),
    );
    expect(
      await screen.findByTestId('live-setup-repair-disconnect-contacts'),
    ).toBeTruthy();

    await act(async () => {
      appStateHandlers.forEach(handler => handler('background'));
    });

    expect(
      screen.queryByTestId('live-setup-repair-disconnect-contacts'),
    ).toBeNull();
  });

  it('revokes lifecycle repair identity on a later native invalidation', async () => {
    const { repairingAccount, repairingBootstrap, repairingSetup } =
      lifecycleRepairFixture();
    const harness = createPort({
      continueWithGoogle: jest.fn(async () =>
        ok(repairingAccount, revision('9')),
      ),
      getBootstrap: jest.fn(async () => ok(repairingBootstrap, revision('9'))),
      getSetup: jest.fn(async () => ok(repairingSetup, revision('9'))),
    });

    await renderLiveApp(harness.port);
    await fireEvent.press(
      await screen.findByTestId('live-setup-repair-reauth'),
    );
    expect(
      await screen.findByTestId('live-setup-repair-disconnect-contacts'),
    ).toBeTruthy();

    await act(async () => {
      harness.emit({ revision: revision('10'), areas: ['privacy'] });
    });

    expect(
      screen.queryByTestId('live-setup-repair-disconnect-contacts'),
    ).toBeNull();
  });

  it('accepts only the matching in-flight identity invalidation and revokes on the next one', async () => {
    const { repairingAccount, repairingBootstrap, repairingSetup } =
      lifecycleRepairFixture();
    let harness!: PortHarness;
    const continueWithGoogle = jest.fn(async () => {
      harness.emit({
        revision: revision('14'),
        areas: ['bootstrap', 'setup', 'account'],
      });
      return ok(repairingAccount, revision('14'));
    });
    harness = createPort({
      continueWithGoogle,
      getBootstrap: jest.fn(async () => ok(repairingBootstrap, revision('14'))),
      getSetup: jest.fn(async () => ok(repairingSetup, revision('14'))),
    });

    await renderLiveApp(harness.port);
    await fireEvent.press(
      await screen.findByTestId('live-setup-repair-reauth'),
    );
    await waitFor(() => expect(continueWithGoogle).toHaveBeenCalledTimes(1));

    expect(
      await screen.findByTestId('live-setup-repair-disconnect-contacts'),
    ).toBeTruthy();

    await act(async () => {
      harness.emit({ revision: revision('15'), areas: ['privacy'] });
    });
    expect(
      screen.queryByTestId('live-setup-repair-disconnect-contacts'),
    ).toBeNull();
  });

  it('fails lifecycle repair identity closed when refreshed projection revisions differ', async () => {
    const { repairingAccount, repairingBootstrap, repairingSetup } =
      lifecycleRepairFixture();
    const getSetup = jest
      .fn()
      .mockResolvedValueOnce(ok(repairingSetup, revision('11')))
      .mockResolvedValue(ok(repairingSetup, revision('12')));
    const getBootstrap = jest
      .fn()
      .mockResolvedValueOnce(ok(repairingBootstrap, revision('11')))
      .mockResolvedValue(ok(repairingBootstrap, revision('12')));
    const { port } = createPort({
      continueWithGoogle: jest.fn(async () =>
        ok(repairingAccount, revision('11')),
      ),
      getBootstrap,
      getSetup,
    });

    await renderLiveApp(port);
    await fireEvent.press(
      await screen.findByTestId('live-setup-repair-reauth'),
    );
    await waitFor(() => expect(getSetup).toHaveBeenCalledTimes(2));

    expect(
      screen.queryByTestId('live-setup-repair-disconnect-contacts'),
    ).toBeNull();
  });

  it('routes incomplete native state to setup and fails unsupported actions closed', async () => {
    const unsupportedEligibility = {
      kind: 'unsupported' as const,
      capability,
      primaryIssue: {
        id: 'setup-compatibility-background' as IssueId,
        code: 'background-restricted' as const,
        severity: 'blocking' as const,
        blocks: ['activation' as const, 'birthday' as const],
      },
      otherIssues: [],
    };
    const incompleteBootstrap: BootstrapProjection = {
      ...completeBootstrap,
      eligibility: unsupportedEligibility,
      setupStep: 'compatibility',
    };
    const setupProjection: SetupProjection = {
      step: 'compatibility',
      initialActivationCompleted: false,
      eligibility: unsupportedEligibility,
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

    const rendered = await renderLiveApp(port);
    await waitFor(() =>
      expect(screen.getByTestId('live-setup-screen')).toBeTruthy(),
    );
    expect(screen.queryByTestId('live-app-shell')).toBeNull();
    expect(screen.getByText(/Step 1 of 4:/u)).toBeTruthy();
    expect(
      screen.getByText(
        'Allow Birthday Autopilot to run in the background, then check readiness again.',
      ),
    ).toBeTruthy();
    const setupTree = JSON.stringify(rendered.toJSON());
    expect(setupTree.indexOf('live-setup-eligibility')).toBeLessThan(
      setupTree.indexOf('live-setup-cost-consent'),
    );
    expect(setupTree.indexOf('live-setup-cost-consent')).toBeLessThan(
      setupTree.indexOf('live-setup-action'),
    );

    await fireEvent.press(screen.getByTestId('live-setup-action'));

    await waitFor(() =>
      expect(screen.getByText('Action not completed')).toBeTruthy(),
    );
    expect(refreshCompatibility).toHaveBeenCalledTimes(1);
    expect(getHome).not.toHaveBeenCalled();
    expect(screen.getByText(/Nothing was changed/u)).toBeTruthy();
  });

  it.each(['android', 'ios'] as const)(
    'starts a supported signed-out %s install at Step 1 and resumes it in-session after Finish later',
    async platform => {
      const platformCapability =
        platform === 'android' ? capability : iosCapability;
      const platformReadiness =
        platform === 'android' ? readiness : iosReadiness;
      const signedOutAccount: AccountProjection = {
        kind: 'signed-out',
        retainedSetup: 'none',
      };
      const platformEligibility = {
        kind: 'supported' as const,
        capability: platformCapability,
        channelLabel: 'test',
        chargeDisclosureVersion:
          platform === 'android' ? 'carrier-v1' : 'composer-v1',
      };
      const bootstrap: BootstrapProjection = {
        capability: platformCapability,
        eligibility: platformEligibility,
        account: signedOutAccount,
        setupStep: 'google-account',
      };
      const setupProjection: SetupProjection = {
        step: 'google-account',
        initialActivationCompleted: false,
        eligibility: platformEligibility,
        account: signedOutAccount,
        contacts: { kind: 'never-synced' },
        readiness: platformReadiness,
        automation:
          platform === 'android'
            ? {
                platform: 'android',
                desired: 'paused',
                effective: 'not-configured',
                readiness,
              }
            : {
                platform: 'ios',
                desired: 'paused',
                effective: 'not-configured',
                readiness: iosReadiness,
              },
      };
      const getPendingRoute = jest.fn(async () =>
        ok({ kind: 'attention' as const }),
      );
      const { port } = createPort({
        getBootstrap: jest.fn(async () => ok(bootstrap)),
        getPendingRoute,
        getSetup: jest.fn(async () => ok(setupProjection)),
      });

      await renderLiveApp(port);

      expect(await screen.findByText(/Step 1 of 4:/u)).toBeTruthy();
      expect(screen.queryByText(/Step 2 of 4:/u)).toBeNull();
      expect(screen.getByTestId('live-setup-eligibility')).toBeTruthy();
      expect(screen.getByTestId('live-setup-cost-consent')).toBeTruthy();
      expect(
        screen.getByRole('button', { name: 'Continue with Google' }),
      ).toBeTruthy();
      expect(screen.queryByTestId('live-setup-contacts-privacy')).toBeNull();

      await fireEvent.press(screen.getByTestId('live-setup-defer'));
      expect(await screen.findByTestId('live-home-screen')).toBeTruthy();
      expect(screen.getByTestId('live-home-setup-incomplete')).toBeTruthy();
      expect(screen.queryByTestId('live-attention-screen')).toBeNull();
      expect(getPendingRoute).not.toHaveBeenCalled();
      expect(
        screen.getByTestId('live-tab-people').props.accessibilityState,
      ).toMatchObject({ disabled: true });
      expect(
        screen.getByTestId('live-tab-settings').props.accessibilityState,
      ).toMatchObject({ disabled: true });
      await fireEvent.press(screen.getByTestId('live-tab-settings'));
      expect(screen.queryByTestId('live-settings-screen')).toBeNull();
      expect(screen.getByTestId('live-home-setup-incomplete')).toBeTruthy();

      await fireEvent.press(screen.getByTestId('live-home-continue-setup'));
      expect(await screen.findByTestId('live-setup-screen')).toBeTruthy();
      expect(screen.getByText(/Step 1 of 4:/u)).toBeTruthy();
    },
  );

  it('shows the Android Contacts cloud boundary immediately before read-only consent', async () => {
    const contactsConsentSetup: SetupProjection = {
      ...completeSetup,
      step: 'contacts-disclosure',
      initialActivationCompleted: false,
      contacts: {
        kind: 'authorization-required',
        reason: 'contacts-authorization-required',
      },
      automation: {
        platform: 'android',
        desired: 'paused',
        effective: 'not-configured',
        readiness,
      },
    };
    const { port } = createPort({
      getBootstrap: jest.fn(async () =>
        ok({ ...completeBootstrap, setupStep: 'contacts-disclosure' as const }),
      ),
      getSetup: jest.fn(async () => ok(contactsConsentSetup)),
    });

    await renderLiveApp(port);

    expect(
      await screen.findByTestId('live-setup-contacts-privacy'),
    ).toBeTruthy();
    expect(
      screen.getByText(/protected, encrypted storage on this phone/u),
    ).toBeTruthy();
    expect(
      screen.getByText(/Only after you enable an Android recipient/u),
    ).toBeTruthy();
    expect(screen.getByText(/fixed-length pseudonymous/u)).toBeTruthy();
    expect(screen.getByText(/are not anonymous/u)).toBeTruthy();
    expect(
      screen.getByRole('button', { name: 'Allow read-only Google Contacts' }),
    ).toBeTruthy();
  });

  it('states before iOS Contacts consent that recipients are not registered or sent automatically', async () => {
    const contactsConsentSetup: SetupProjection = {
      step: 'contacts-disclosure',
      initialActivationCompleted: false,
      eligibility: iosBootstrap.eligibility,
      account: iosAccount,
      contacts: {
        kind: 'authorization-required',
        reason: 'contacts-authorization-required',
      },
      readiness: iosReadiness,
      automation: {
        platform: 'ios',
        desired: 'paused',
        effective: 'not-configured',
        readiness: iosReadiness,
      },
    };
    const { port } = createPort({
      getBootstrap: jest.fn(async () =>
        ok({ ...iosBootstrap, setupStep: 'contacts-disclosure' as const }),
      ),
      getSetup: jest.fn(async () => ok(contactsConsentSetup)),
    });

    await renderLiveApp(port);

    expect(
      await screen.findByTestId('live-setup-contacts-privacy'),
    ).toBeTruthy();
    expect(
      screen.getByText(/protected, encrypted storage on this iPhone/u),
    ).toBeTruthy();
    expect(
      screen.getByText(
        /does not register recipients.*cannot send automatically/u,
      ),
    ).toBeTruthy();
    expect(
      screen.getByText(/Remote Config and service metadata/u),
    ).toBeTruthy();
    expect(
      screen.getByRole('button', { name: 'Allow read-only Google Contacts' }),
    ).toBeTruthy();
  });

  it('keeps the current setup action fail-closed during recheck and after setup recheck failure', async () => {
    const contactsConsentSetup: SetupProjection = {
      ...completeSetup,
      step: 'contacts-disclosure',
      initialActivationCompleted: false,
      contacts: {
        kind: 'authorization-required',
        reason: 'contacts-authorization-required',
      },
      automation: {
        platform: 'android',
        desired: 'paused',
        effective: 'not-configured',
        readiness,
      },
    };
    const setupRefresh = deferred<NativeResult<SetupProjection>>();
    const authorization = deferred<NativeResult<{ kind: 'accepted' }>>();
    const getSetup = jest
      .fn()
      .mockResolvedValueOnce(ok(contactsConsentSetup))
      .mockImplementationOnce(() => setupRefresh.promise)
      .mockResolvedValue(internalError());
    const authorizeContacts = jest.fn(() => authorization.promise);
    const { port } = createPort({
      authorizeContacts,
      getBootstrap: jest.fn(async () =>
        ok({ ...completeBootstrap, setupStep: 'contacts-disclosure' as const }),
      ),
      getSetup,
    });

    await renderLiveApp(port);
    const action = await screen.findByTestId('live-setup-action');
    fireEvent.press(action);

    expect(authorizeContacts).toHaveBeenCalledTimes(1);
    await waitFor(() =>
      expect(
        screen.getByTestId('live-setup-action').props.accessibilityState,
      ).toEqual(expect.objectContaining({ disabled: true })),
    );
    await fireEvent.press(screen.getByTestId('live-setup-action'));
    expect(authorizeContacts).toHaveBeenCalledTimes(1);

    await act(async () => {
      authorization.resolve(ok({ kind: 'accepted' }));
      await Promise.resolve();
    });
    await waitFor(() => expect(getSetup).toHaveBeenCalledTimes(2));
    expect(
      screen.getByTestId('live-setup-action').props.accessibilityState,
    ).toEqual(expect.objectContaining({ disabled: true }));

    await act(async () => {
      setupRefresh.resolve(internalError());
    });
    expect(await screen.findByText('Setup status is unavailable')).toBeTruthy();
    expect(screen.queryByTestId('live-setup-action')).toBeNull();
    expect(authorizeContacts).toHaveBeenCalledTimes(1);
  });

  it('blocks Contacts behind the explicit sender transfer gate on a standby Android install', async () => {
    const standbyAccount: AccountProjection = {
      kind: 'connected',
      displayEmail: 'user@example.test' as PrivateEmail,
      sender: {
        platform: 'android',
        kind: 'standby',
        activeOtherDeviceLabel: 'Other verified Android phone',
      },
    };
    const standbySetup: SetupProjection = {
      ...completeSetup,
      step: 'contacts-disclosure',
      initialActivationCompleted: false,
      account: standbyAccount,
      contacts: { kind: 'never-synced' },
      automation: {
        platform: 'android',
        desired: 'paused',
        effective: 'standby',
        readiness,
      },
    };
    const authorizeContacts = jest.fn(async () => internalError());
    const { port } = createPort({
      authorizeContacts,
      getBootstrap: jest.fn(async () =>
        ok({
          ...completeBootstrap,
          account: standbyAccount,
          setupStep: 'contacts-disclosure' as const,
        }),
      ),
      getSenderTransferOperation: jest.fn(async () =>
        ok({ kind: 'none' as const }),
      ),
      getSetup: jest.fn(async () => ok(standbySetup)),
    });

    await renderLiveApp(port);

    expect(await screen.findByTestId('live-setup-sender-gate')).toBeTruthy();
    expect(screen.getByText(/will not read Contacts/u)).toBeTruthy();
    expect(
      await screen.findByTestId('live-prepare-sender-transfer'),
    ).toBeTruthy();
    expect(screen.queryByTestId('live-setup-action')).toBeNull();
    expect(authorizeContacts).not.toHaveBeenCalled();
  });

  it('keeps the four-step product setup resumable until native planning and activation are complete', async () => {
    let planningReady = false;
    const testOnlyHome: HomeProjection = {
      ...liveHome('active'),
      automation: {
        platform: 'android',
        desired: 'paused',
        effective: 'test-only',
        readiness,
      },
      counts: {
        ...liveHome('active').counts,
        enabled: 0,
      },
    };
    const testOnlySetup: SetupProjection = {
      ...completeSetup,
      initialActivationCompleted: false,
      automation: testOnlyHome.automation,
    };
    const getHome = jest.fn(async () =>
      ok({
        ...testOnlyHome,
        counts: {
          ...testOnlyHome.counts,
          enabled: planningReady ? 1 : 0,
        },
      }),
    );
    const getMessageEditor = jest.fn(async () =>
      ok(
        planningReady
          ? {
              kind: 'configured' as const,
              draft: {
                language: 'en' as const,
                tone: 'warm' as const,
                placeholderMode: {
                  kind: 'generic' as const,
                  requiredCount: 0 as const,
                },
                text: 'Happy birthday!' as PrivateMessageText,
                requestedSegmentCap: 1 as const,
              },
            }
          : { kind: 'not-configured' as const },
      ),
    );
    const getPolicyEditor = jest.fn(async () =>
      ok(
        planningReady
          ? {
              kind: 'configured' as const,
              draft: {
                primaryStart: '09:00',
                primaryEnd: '11:00',
                latePolicy: { kind: 'none' as const },
                dailyCap: 10,
              },
            }
          : { kind: 'not-configured' as const },
      ),
    );
    const { port } = createPort({
      getHome,
      getMessageEditor,
      getPolicyEditor,
      getSetup: jest.fn(async () => ok(testOnlySetup)),
    });

    const first = await renderLiveApp(port);
    expect(
      await screen.findByTestId('live-product-setup-journey'),
    ).toBeTruthy();
    expect(screen.getByText('Step 3 of 4')).toBeTruthy();
    expect(screen.queryByTestId('live-app-shell')).toBeNull();
    expect(screen.getByRole('button', { name: 'Choose people' })).toBeTruthy();
    expect(
      screen.getByText('Welcome, Google and Contacts are complete.'),
    ).toBeTruthy();
    expect(screen.queryByTestId('live-product-setup-people')).toBeNull();
    expect(screen.queryByTestId('live-product-setup-message')).toBeNull();
    expect(screen.queryByTestId('live-product-setup-automation')).toBeNull();
    expect(screen.queryByTestId('live-product-setup-approvals')).toBeNull();

    let journeyHardwareBack: (() => boolean | null | undefined) | undefined;
    const journeyBackSpy = jest
      .spyOn(BackHandler, 'addEventListener')
      .mockImplementation((_event, handler) => {
        journeyHardwareBack = () => handler({} as never);
        return { remove: jest.fn() };
      });
    await fireEvent.press(screen.getByTestId('live-product-setup-next'));
    expect(await screen.findByTestId('live-people-screen')).toBeTruthy();
    await fireEvent.press(screen.getByTestId('live-people-back'));
    expect(
      await screen.findByTestId('live-product-setup-journey'),
    ).toBeTruthy();

    await fireEvent.press(screen.getByTestId('live-product-setup-next'));
    expect(await screen.findByTestId('live-people-screen')).toBeTruthy();
    await fireEvent.press(
      await screen.findByTestId('live-person-contact-live-1'),
    );
    expect(await screen.findByTestId('live-person-detail-screen')).toBeTruthy();

    await act(async () => {
      expect(journeyHardwareBack?.()).toBe(true);
    });
    await waitFor(() =>
      expect(screen.getByTestId('live-people-screen')).toBeTruthy(),
    );
    await act(async () => {
      expect(journeyHardwareBack?.()).toBe(true);
    });
    expect(
      await screen.findByTestId('live-product-setup-journey'),
    ).toBeTruthy();
    journeyBackSpy.mockRestore();

    await fireEvent.press(screen.getByTestId('live-product-setup-defer'));
    expect(await screen.findByTestId('live-home-screen')).toBeTruthy();
    expect(screen.getByTestId('live-home-setup-incomplete')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Continue setup' })).toBeTruthy();
    expect(screen.queryByTestId('live-home-automation')).toBeNull();

    await fireEvent.press(screen.getByTestId('live-home-continue-setup'));
    expect(
      await screen.findByTestId('live-product-setup-journey'),
    ).toBeTruthy();
    expect(screen.getByText('Step 3 of 4')).toBeTruthy();

    await fireEvent.press(screen.getByTestId('live-product-setup-defer'));
    expect(await screen.findByTestId('live-home-screen')).toBeTruthy();
    await first.unmount();

    const second = await renderLiveApp(port);
    expect(
      await screen.findByTestId('live-product-setup-journey'),
    ).toBeTruthy();
    expect(screen.getByText('Step 3 of 4')).toBeTruthy();
    await second.unmount();

    planningReady = true;
    await renderLiveApp(port);
    expect(
      await screen.findByTestId('live-product-setup-journey'),
    ).toBeTruthy();
    expect(screen.getByText('Step 4 of 4')).toBeTruthy();
    expect(
      screen.getByRole('button', { name: 'Test and turn on automation' }),
    ).toBeTruthy();
    expect(
      screen.getByText(
        'People, message, window and exact approvals are complete.',
      ),
    ).toBeTruthy();
    expect(screen.queryByTestId('live-product-setup-people')).toBeNull();
    expect(screen.queryByTestId('live-product-setup-message')).toBeNull();
    expect(screen.queryByTestId('live-product-setup-automation')).toBeNull();
    expect(screen.queryByTestId('live-product-setup-approvals')).toBeNull();
  });

  it('routes the exact unfinished window task to Schedule without exposing Step 4 early', async () => {
    const configuredHome: HomeProjection = {
      ...activationReadyHome,
      counts: {
        ...activationReadyHome.counts,
        configured: 1,
        enabled: 1,
      },
    };
    const { port } = createPort({
      getHome: jest.fn(async () => ok(configuredHome)),
      getMessageEditor: jest.fn(async () => ok(configuredMessage)),
      getPolicyEditor: jest.fn(async () =>
        ok({ kind: 'not-configured' as const }),
      ),
      getSetup: jest.fn(async () =>
        ok({
          ...completeSetup,
          initialActivationCompleted: false,
          automation: configuredHome.automation,
        }),
      ),
    });

    await renderLiveApp(port);

    expect(await screen.findByText('Step 3 of 4')).toBeTruthy();
    expect(
      screen.getByRole('button', { name: 'Choose the time window' }),
    ).toBeTruthy();
    expect(screen.queryByText('Step 4 of 4')).toBeNull();
    expect(screen.queryByTestId('live-product-setup-automation')).toBeNull();

    await fireEvent.press(screen.getByTestId('live-product-setup-next'));
    expect(await screen.findByTestId('live-schedule-screen')).toBeTruthy();
    expect(screen.queryByTestId('live-automation-screen')).toBeNull();

    await fireEvent.press(screen.getByTestId('live-schedule-back'));
    expect(
      await screen.findByTestId('live-product-setup-journey'),
    ).toBeTruthy();
    expect(screen.getByText('Step 3 of 4')).toBeTruthy();
  });

  it('opens the exact blocking Android readiness repair from Step 4 and keeps activation hidden', async () => {
    const warningHandle = 'setup-warning-action' as ActionHandle;
    const blockingHandle = 'setup-blocking-action' as ActionHandle;
    const blockedReadiness: ReadinessProjection = {
      ...readiness,
      activation: {
        kind: 'blocked',
        issues: [
          {
            id: 'setup-warning-issue' as IssueId,
            code: 'data-saver-restricted',
            severity: 'warning',
            blocks: ['activation'],
            action: {
              kind: 'native-action',
              handle: warningHandle,
              labelKey: 'RAW_WARNING_NATIVE_LABEL',
            },
          },
          {
            id: 'setup-blocking-issue' as IssueId,
            code: 'background-restricted',
            severity: 'blocking',
            blocks: ['activation'],
            action: {
              kind: 'native-action',
              handle: blockingHandle,
              labelKey: 'RAW_BLOCKING_NATIVE_LABEL',
            },
          },
        ],
      },
    };
    const blockedHome: HomeProjection = {
      ...activationReadyHome,
      automation: {
        platform: 'android',
        desired: 'paused',
        effective: 'test-only',
        readiness: blockedReadiness,
      },
      counts: {
        ...activationReadyHome.counts,
        configured: 1,
        enabled: 1,
      },
    };
    const getAccount = jest.fn(async () =>
      ok(androidAccountForSender('test-only')),
    );
    const getHome = jest.fn(async () => ok(blockedHome));
    const getLatestTest = jest.fn(async () => ok(latestPassedTest));
    const getMessageEditor = jest.fn(async () => ok(configuredMessage));
    const getPolicyEditor = jest.fn(async () => ok(configuredPolicy));
    const performAction = jest.fn(async () =>
      ok({ kind: 'opened' as const }, revision('2')),
    );
    const { port } = createPort({
      getAccount,
      getHome,
      getLatestTest,
      getMessageEditor,
      getPolicyEditor,
      getSetup: jest.fn(async () =>
        ok({
          ...completeSetup,
          initialActivationCompleted: false,
          readiness: blockedReadiness,
          automation: blockedHome.automation,
        }),
      ),
      performAction,
    });

    await renderLiveApp(port);
    expect(await screen.findByText('Step 4 of 4')).toBeTruthy();
    await fireEvent.press(screen.getByTestId('live-product-setup-next'));

    expect(
      await screen.findByTestId('live-automation-readiness-action'),
    ).toBeTruthy();
    expect(
      screen.getByRole('button', { name: 'Fix on this phone' }),
    ).toBeTruthy();
    expect(screen.queryByText('RAW_WARNING_NATIVE_LABEL')).toBeNull();
    expect(screen.queryByText('RAW_BLOCKING_NATIVE_LABEL')).toBeNull();
    expect(screen.queryByTestId('live-review-activation')).toBeNull();

    await fireEvent.press(
      screen.getByTestId('live-automation-readiness-action'),
    );

    await waitFor(() =>
      expect(performAction).toHaveBeenCalledWith({
        handle: blockingHandle,
        expectedRevision: '1',
      }),
    );
    expect(
      await screen.findByText(
        'The phone step opened. Return and choose Check again after making a choice.',
      ),
    ).toBeTruthy();
    expect(getAccount.mock.calls.length).toBeGreaterThanOrEqual(2);
    expect(getHome.mock.calls.length).toBeGreaterThanOrEqual(2);
    expect(getLatestTest.mock.calls.length).toBeGreaterThanOrEqual(2);
    expect(getMessageEditor.mock.calls.length).toBeGreaterThanOrEqual(2);
    expect(getPolicyEditor.mock.calls.length).toBeGreaterThanOrEqual(2);
    expect(screen.queryByTestId('live-review-activation')).toBeNull();
  });

  it.each([
    ['Home', 'home'],
    ['Message', 'messages'],
    ['Policy', 'automation'],
  ] as const)(
    'disables setup Next while a retained %s projection refreshes and after it fails',
    async (_label, area) => {
      const configuredHome: HomeProjection = {
        ...activationReadyHome,
        counts: {
          ...activationReadyHome.counts,
          configured: 1,
          enabled: 1,
        },
      };
      const setupMessageProjection = {
        kind: 'configured' as const,
        draft: {
          language: 'en' as const,
          tone: 'warm' as const,
          placeholderMode: { kind: 'generic' as const, requiredCount: 0 },
          text: 'Happy birthday!' as PrivateMessageText,
          requestedSegmentCap: 1 as const,
        },
      };
      const setupPolicyProjection = {
        kind: 'configured' as const,
        draft: {
          primaryStart: '09:00' as const,
          primaryEnd: '11:00' as const,
          latePolicy: { kind: 'none' as const },
          dailyCap: 10,
        },
      };
      const refresh = deferred<NativeResult<unknown>>();
      let homeRequests = 0;
      let messageRequests = 0;
      let policyRequests = 0;
      const getHome = jest.fn(async () => {
        homeRequests += 1;
        return area === 'home' && homeRequests > 1
          ? ((await refresh.promise) as NativeResult<HomeProjection>)
          : ok(configuredHome);
      });
      const getMessageEditor = jest.fn(async () => {
        messageRequests += 1;
        return area === 'messages' && messageRequests > 1
          ? ((await refresh.promise) as NativeResult<
              typeof setupMessageProjection
            >)
          : ok(setupMessageProjection);
      });
      const getPolicyEditor = jest.fn(async () => {
        policyRequests += 1;
        return area === 'automation' && policyRequests > 1
          ? ((await refresh.promise) as NativeResult<
              typeof setupPolicyProjection
            >)
          : ok(setupPolicyProjection);
      });
      const harness = createPort({
        getHome,
        getMessageEditor,
        getPolicyEditor,
        getSetup: jest.fn(async () =>
          ok({
            ...completeSetup,
            initialActivationCompleted: false,
            automation: configuredHome.automation,
          }),
        ),
      });

      await renderLiveApp(harness.port);
      expect(await screen.findByText('Step 4 of 4')).toBeTruthy();
      expect(
        screen.getByTestId('live-product-setup-next').props.accessibilityState,
      ).toEqual(expect.objectContaining({ disabled: false }));

      await act(async () => {
        harness.emit({ revision: revision('2'), areas: [area] });
      });

      await waitFor(() => {
        const requests =
          area === 'home'
            ? getHome
            : area === 'messages'
            ? getMessageEditor
            : getPolicyEditor;
        expect(requests).toHaveBeenCalledTimes(2);
        expect(
          screen.getByTestId('live-product-setup-next').props
            .accessibilityState,
        ).toEqual(expect.objectContaining({ disabled: true }));
      });

      await act(async () => {
        refresh.resolve(internalError<unknown>());
      });

      expect(
        await screen.findByText('Saved setup could not be checked'),
      ).toBeTruthy();
      expect(
        screen.getByTestId('live-product-setup-next').props.accessibilityState,
      ).toEqual(expect.objectContaining({ disabled: true }));
    },
  );

  it('fails setup progress closed when stable global projections have different revisions', async () => {
    const configuredHome: HomeProjection = {
      ...activationReadyHome,
      counts: {
        ...activationReadyHome.counts,
        configured: 1,
        enabled: 1,
      },
    };
    const { port } = createPort({
      getHome: jest.fn(async () => ok(configuredHome, revision('1'))),
      getMessageEditor: jest.fn(async () =>
        ok(
          {
            kind: 'configured' as const,
            draft: {
              language: 'en' as const,
              tone: 'warm' as const,
              placeholderMode: {
                kind: 'generic' as const,
                requiredCount: 0,
              },
              text: 'Happy birthday!' as PrivateMessageText,
              requestedSegmentCap: 1 as const,
            },
          },
          revision('2'),
        ),
      ),
      getPolicyEditor: jest.fn(async () =>
        ok(
          {
            kind: 'configured' as const,
            draft: {
              primaryStart: '09:00' as const,
              primaryEnd: '11:00' as const,
              latePolicy: { kind: 'none' as const },
              dailyCap: 10,
            },
          },
          revision('1'),
        ),
      ),
      getSetup: jest.fn(async () =>
        ok({
          ...completeSetup,
          initialActivationCompleted: false,
          automation: configuredHome.automation,
        }),
      ),
    });

    await renderLiveApp(port);

    expect(
      await screen.findByText('Saved setup could not be checked'),
    ).toBeTruthy();
    expect(screen.getByText('Step 3 of 4')).toBeTruthy();
    expect(screen.queryByText('Step 4 of 4')).toBeNull();
    expect(
      screen.getByTestId('live-product-setup-next').props.accessibilityState,
    ).toEqual(expect.objectContaining({ disabled: true }));
  });

  it('routes enrolled Android recipients through one exact batch approval before Test', async () => {
    const pendingContact: ContactSummary = {
      ...contactSummary(),
      readiness: {
        kind: 'needs-attention',
        reasons: ['approval-invalid'],
      },
      enrollment: {
        kind: 'paused',
        reason: 'approval-invalid',
        approval: { kind: 'missing' },
      },
    };
    const configuredHome: HomeProjection = {
      ...activationReadyHome,
      counts: {
        ...activationReadyHome.counts,
        configured: 1,
        enabled: 0,
      },
    };
    let approvalSaved = false;
    let approvalRevision = revision('1');
    const prepareApprovals = jest.fn(async () => {
      approvalRevision = revision('6');
      return ok(
        {
          handle: 'guided-approval-review' as ApprovalReviewHandle,
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
              chargeDisclosure: 'Exact carrier charge disclosure.',
              consentDisclosure: 'Exact unattended-send consent disclosure.',
            },
          ],
          readyCount: 1,
          blockedCount: 0,
          explicitConfirmationRequired: true as const,
        },
        approvalRevision,
      );
    });
    const confirmApprovals = jest.fn(async () => {
      approvalSaved = true;
      approvalRevision = revision('7');
      return ok(activationReadyHome.automation, approvalRevision);
    });
    const { port } = createPort({
      confirmApprovals,
      getHome: jest.fn(async () => ok(configuredHome)),
      getMessageEditor: jest.fn(async () =>
        ok({
          kind: 'configured' as const,
          draft: {
            language: 'en' as const,
            tone: 'warm' as const,
            placeholderMode: { kind: 'generic' as const, requiredCount: 0 },
            text: 'Happy birthday!' as PrivateMessageText,
            requestedSegmentCap: 1 as const,
          },
        }),
      ),
      getPolicyEditor: jest.fn(async () =>
        ok({
          kind: 'configured' as const,
          draft: {
            primaryStart: '09:00' as const,
            primaryEnd: '11:00' as const,
            latePolicy: { kind: 'none' as const },
            dailyCap: 10,
          },
        }),
      ),
      getSetup: jest.fn(async () =>
        ok({
          ...completeSetup,
          initialActivationCompleted: false,
          automation: configuredHome.automation,
        }),
      ),
      listPeople: jest.fn(async () =>
        ok(
          {
            items: approvalSaved ? [] : [pendingContact],
            totalCount: approvalSaved ? 0 : 1,
          },
          approvalRevision,
        ),
      ),
      prepareApprovals,
    });

    await renderLiveApp(port);
    await fireEvent.press(
      await screen.findByRole('button', { name: 'Review exact messages' }),
    );
    await fireEvent.press(
      await screen.findByTestId('live-batch-approval-prepare'),
    );

    expect(
      await screen.findByText(
        'Your carrier may charge for every SMS segment. Roaming is used only when separately approved.',
      ),
    ).toBeTruthy();
    expect(
      screen.getByText(
        'Confirming stores this exact recipient, chosen phone number, birthday, message, window, SIM and segment plan for protected future birthday jobs.',
      ),
    ).toBeTruthy();
    expect(screen.queryByText('Exact carrier charge disclosure.')).toBeNull();
    expect(
      screen.queryByText('Exact unattended-send consent disclosure.'),
    ).toBeNull();
    await fireEvent.press(screen.getByTestId('live-batch-approval-confirm'));
    await waitFor(() => expect(confirmApprovals).toHaveBeenCalledTimes(1));
    expect(prepareApprovals).toHaveBeenCalledWith({
      contactIds: [contactId],
      expectedRevision: '1',
    });
    expect(confirmApprovals).toHaveBeenCalledWith({
      handle: 'guided-approval-review',
      expectedRevision: '6',
    });
  });

  it('invalidates an exact batch review and disables retained candidates when native truth refresh fails', async () => {
    const pendingContact: ContactSummary = {
      ...contactSummary(),
      readiness: {
        kind: 'needs-attention',
        reasons: ['approval-invalid'],
      },
      enrollment: {
        kind: 'paused',
        reason: 'approval-invalid',
        approval: { kind: 'missing' },
      },
    };
    const configuredHome: HomeProjection = {
      ...activationReadyHome,
      counts: {
        ...activationReadyHome.counts,
        configured: 1,
        enabled: 0,
      },
    };
    const candidateRefresh =
      deferred<NativeResult<{ items: ContactSummary[]; totalCount: number }>>();
    let prepared = false;
    let returnedVerifiedPrepareReload = false;
    const listPeople = jest.fn(async () => {
      if (!prepared) {
        return ok({ items: [pendingContact], totalCount: 1 });
      }
      if (!returnedVerifiedPrepareReload) {
        returnedVerifiedPrepareReload = true;
        return ok({ items: [pendingContact], totalCount: 1 }, revision('6'));
      }
      return candidateRefresh.promise;
    });
    const prepareApprovals = jest.fn(async () => {
      prepared = true;
      return ok(
        {
          handle: 'stale-guided-review' as ApprovalReviewHandle,
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
              chargeDisclosure: 'Native label is not UI copy.',
              consentDisclosure: 'Native label is not UI copy.',
            },
          ],
          readyCount: 1,
          blockedCount: 0,
          explicitConfirmationRequired: true as const,
        },
        revision('6'),
      );
    });
    const confirmApprovals = jest.fn(async () =>
      ok(activationReadyHome.automation),
    );
    const harness = createPort({
      confirmApprovals,
      getHome: jest.fn(async () => ok(configuredHome)),
      getMessageEditor: jest.fn(async () => ok(configuredMessage)),
      getPolicyEditor: jest.fn(async () => ok(configuredPolicy)),
      getSetup: jest.fn(async () =>
        ok({
          ...completeSetup,
          initialActivationCompleted: false,
          automation: configuredHome.automation,
        }),
      ),
      listPeople,
      prepareApprovals,
    });

    await renderLiveApp(harness.port);
    await fireEvent.press(
      await screen.findByRole('button', { name: 'Review exact messages' }),
    );
    await fireEvent.press(
      await screen.findByTestId('live-batch-approval-prepare'),
    );
    const staleConfirm = await screen.findByTestId(
      'live-batch-approval-confirm',
    );

    const requestsBeforeInvalidation = listPeople.mock.calls.length;
    await act(async () => {
      harness.emit({ revision: revision('7'), areas: ['contacts'] });
    });

    await waitFor(() =>
      expect(listPeople.mock.calls.length).toBeGreaterThan(
        requestsBeforeInvalidation,
      ),
    );
    expect(screen.queryByTestId('live-batch-approval-confirm')).toBeNull();
    await fireEvent.press(staleConfirm);
    expect(confirmApprovals).not.toHaveBeenCalled();

    await act(async () => {
      candidateRefresh.resolve(internalError());
    });
    const retainedPrepare = await screen.findByTestId(
      'live-batch-approval-prepare',
    );
    expect(retainedPrepare.props.accessibilityState).toEqual(
      expect.objectContaining({ disabled: true }),
    );
    await fireEvent.press(retainedPrepare);
    expect(prepareApprovals).toHaveBeenCalledTimes(1);
    expect(confirmApprovals).not.toHaveBeenCalled();
  });

  it('continues exact approvals beyond 50 when native invalidates before confirm resolves', async () => {
    const contacts = Array.from({ length: 51 }, (_, index) => ({
      ...contactSummary(),
      id: `approval-large-${index + 1}` as ContactId,
      displayName: `Approval Person ${index + 1}` as PrivateDisplayName,
      readiness: {
        kind: 'needs-attention' as const,
        reasons: ['approval-invalid' as const],
      },
      enrollment: {
        kind: 'enabled' as const,
        approval: { kind: 'missing' as const },
      },
    }));
    const byId = new Map(contacts.map(contact => [contact.id, contact]));
    const approved = new Set<ContactId>();
    const reviewIds = new Map<string, readonly ContactId[]>();
    let harness!: PortHarness;
    let currentRevision = 1;
    let reviewSequence = 0;
    const listPeople = jest.fn(
      async (query: {
        cursor?: import('../domain/shared/brand').PageCursor;
      }) => {
        const pending = contacts.filter(contact => !approved.has(contact.id));
        if (pending.length === 0) {
          return ok(
            { items: [], totalCount: 0 },
            revision(String(currentRevision)),
          );
        }
        return query.cursor === undefined
          ? ok(
              {
                items: pending.slice(0, 50),
                ...(pending.length > 50
                  ? {
                      nextCursor:
                        'approval-page-2' as import('../domain/shared/brand').PageCursor,
                    }
                  : {}),
                totalCount: pending.length,
              },
              revision(String(currentRevision)),
            )
          : ok(
              { items: pending.slice(50), totalCount: pending.length },
              revision(String(currentRevision)),
            );
      },
    );
    const prepareApprovals = jest.fn(
      async ({ contactIds }: { contactIds: readonly ContactId[] }) => {
        reviewSequence += 1;
        currentRevision += 1;
        const handle =
          `large-approval-${reviewSequence}` as ApprovalReviewHandle;
        reviewIds.set(handle, contactIds);
        return ok(
          {
            handle,
            items: contactIds.map(id => ({
              platform: 'android' as const,
              contactId: id,
              recipient: byId.get(id)!.displayName,
              maskedPhone: '•••• 4321',
              birthdayLabel: '18 July',
              exactText: 'Happy birthday!' as PrivateMessageText,
              windowLabel: '09:00–11:00',
              simLabel: 'SIM 1',
              segmentCount: 1,
              chargeDisclosure: 'Exact carrier charge disclosure.',
              consentDisclosure: 'Exact unattended-send consent disclosure.',
            })),
            readyCount: contactIds.length,
            blockedCount: 0,
            explicitConfirmationRequired: true as const,
          },
          revision(String(currentRevision)),
        );
      },
    );
    const confirmApprovals = jest.fn(async ({ handle }: { handle: string }) => {
      (reviewIds.get(handle) ?? []).forEach(id => approved.add(id));
      currentRevision += 1;
      const resultRevision = revision(String(currentRevision));
      const result = ok(activationReadyHome.automation, resultRevision);
      harness.emit({
        revision: resultRevision,
        areas: ['contacts', 'automation', 'home', 'readiness'],
      });
      return result;
    });
    const configuredHome: HomeProjection = {
      ...activationReadyHome,
      counts: {
        ...activationReadyHome.counts,
        configured: contacts.length,
        enabled: contacts.length,
      },
    };
    harness = createPort({
      confirmApprovals,
      getHome: jest.fn(async () => ok(configuredHome)),
      getMessageEditor: jest.fn(async () =>
        ok({
          kind: 'configured' as const,
          draft: {
            language: 'en' as const,
            tone: 'warm' as const,
            placeholderMode: { kind: 'generic' as const, requiredCount: 0 },
            text: 'Happy birthday!' as PrivateMessageText,
            requestedSegmentCap: 1 as const,
          },
        }),
      ),
      getPolicyEditor: jest.fn(async () =>
        ok({
          kind: 'configured' as const,
          draft: {
            primaryStart: '09:00' as const,
            primaryEnd: '11:00' as const,
            latePolicy: { kind: 'none' as const },
            dailyCap: 10,
          },
        }),
      ),
      getSetup: jest.fn(async () =>
        ok({
          ...completeSetup,
          initialActivationCompleted: false,
          automation: configuredHome.automation,
        }),
      ),
      listPeople,
      prepareApprovals,
    });

    await renderLiveApp(harness.port);
    await fireEvent.press(
      await screen.findByRole('button', { name: 'Review exact messages' }),
    );
    await fireEvent.press(
      await screen.findByTestId('live-batch-approval-prepare'),
    );
    await waitFor(() => expect(prepareApprovals).toHaveBeenCalledTimes(1));
    expect(prepareApprovals.mock.calls[0]?.[0].contactIds).toHaveLength(50);

    await fireEvent.press(screen.getByTestId('live-batch-approval-confirm'));
    await waitFor(() => expect(prepareApprovals).toHaveBeenCalledTimes(2));
    expect(prepareApprovals.mock.calls[1]?.[0]).toEqual({
      contactIds: ['approval-large-51'],
      expectedRevision: '3',
    });
    expect(await screen.findByText('Approval Person 51')).toBeTruthy();

    await fireEvent.press(screen.getByTestId('live-batch-approval-confirm'));
    await waitFor(() => expect(confirmApprovals).toHaveBeenCalledTimes(2));
    expect(approved.size).toBe(51);
    expect(
      await screen.findByTestId('live-batch-approval-complete'),
    ).toBeTruthy();
  });

  it('keeps setup incomplete when a later approval-candidate page fails', async () => {
    const pendingContacts = Array.from({ length: 50 }, (_, index) => ({
      ...contactSummary(),
      id: `setup-page-failure-${index + 1}` as ContactId,
      displayName: `Setup Failure ${index + 1}` as PrivateDisplayName,
      readiness: {
        kind: 'needs-attention' as const,
        reasons: ['approval-invalid' as const],
      },
      enrollment: {
        kind: 'enabled' as const,
        approval: { kind: 'missing' as const },
      },
    }));
    const configuredHome: HomeProjection = {
      ...activationReadyHome,
      counts: {
        ...activationReadyHome.counts,
        configured: 51,
        enabled: 51,
      },
    };
    const listPeople = jest.fn(
      async (query: { cursor?: import('../domain/shared/brand').PageCursor }) =>
        query.cursor === undefined
          ? ok({
              items: pendingContacts,
              nextCursor:
                'setup-failure-page-2' as import('../domain/shared/brand').PageCursor,
              totalCount: 51,
            })
          : internalError(),
    );
    const { port } = createPort({
      getHome: jest.fn(async () => ok(configuredHome)),
      getMessageEditor: jest.fn(async () =>
        ok({
          kind: 'configured' as const,
          draft: {
            language: 'en' as const,
            tone: 'warm' as const,
            placeholderMode: { kind: 'generic' as const, requiredCount: 0 },
            text: 'Happy birthday!' as PrivateMessageText,
            requestedSegmentCap: 1 as const,
          },
        }),
      ),
      getPolicyEditor: jest.fn(async () =>
        ok({
          kind: 'configured' as const,
          draft: {
            primaryStart: '09:00' as const,
            primaryEnd: '11:00' as const,
            latePolicy: { kind: 'none' as const },
            dailyCap: 10,
          },
        }),
      ),
      getSetup: jest.fn(async () =>
        ok({
          ...completeSetup,
          initialActivationCompleted: false,
          automation: configuredHome.automation,
        }),
      ),
      listPeople,
    });

    await renderLiveApp(port);

    expect(
      await screen.findByText('Saved setup could not be checked'),
    ).toBeTruthy();
    expect(screen.getByText('Step 3 of 4')).toBeTruthy();
    expect(screen.queryByText('Step 4 of 4')).toBeNull();
    expect(
      screen.getByTestId('live-product-setup-next').props.accessibilityState,
    ).toEqual(expect.objectContaining({ disabled: true }));
    expect(listPeople).toHaveBeenCalledTimes(2);
  });

  it('uses the same resumable final setup step for iPhone without implying automatic sending', async () => {
    const iosNotConfigured: SetupProjection = {
      step: 'complete',
      initialActivationCompleted: false,
      eligibility: iosBootstrap.eligibility,
      account: iosAccount,
      contacts: iosHome.contactsSync,
      readiness: iosReadiness,
      automation: {
        platform: 'ios',
        desired: 'paused',
        effective: 'not-configured',
        readiness: iosReadiness,
      },
    };
    const { port } = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(iosHome)),
      getSetup: jest.fn(async () => ok(iosNotConfigured)),
      getMessageEditor: jest.fn(async () =>
        ok({
          kind: 'configured' as const,
          draft: {
            language: 'en' as const,
            tone: 'warm' as const,
            placeholderMode: {
              kind: 'generic' as const,
              requiredCount: 0 as const,
            },
            text: 'Happy birthday!' as PrivateMessageText,
            requestedSegmentCap: 1 as const,
          },
        }),
      ),
      getPolicyEditor: jest.fn(async () =>
        ok({
          kind: 'configured' as const,
          draft: {
            primaryStart: '09:00',
            primaryEnd: '11:00',
            latePolicy: { kind: 'none' as const },
            dailyCap: 10,
          },
        }),
      ),
    });

    await renderLiveApp(port);

    expect(await screen.findByText('Step 4 of 4')).toBeTruthy();
    expect(
      screen.getByRole('button', { name: 'Turn on reminders' }),
    ).toBeTruthy();
    expect(screen.queryByText(/automatic iPhone sending/iu)).toBeNull();
  });

  it('keeps configured but never-enabled iPhone setup at step four without reopening after a later pause', async () => {
    const pausedHome: HomeProjection = {
      ...iosHome,
      automation: {
        platform: 'ios',
        desired: 'paused',
        effective: 'paused',
        readiness: iosReadiness,
      },
    };
    const neverEnabledMessage = {
      kind: 'configured' as const,
      draft: {
        language: 'en' as const,
        tone: 'warm' as const,
        placeholderMode: {
          kind: 'generic' as const,
          requiredCount: 0 as const,
        },
        text: 'Happy birthday!' as PrivateMessageText,
        requestedSegmentCap: 1 as const,
      },
    };
    const neverEnabledPolicy = {
      kind: 'configured' as const,
      draft: {
        primaryStart: '09:00',
        primaryEnd: '11:00',
        latePolicy: { kind: 'none' as const },
        dailyCap: 10,
      },
    };
    const neverEnabledSetup: SetupProjection = {
      step: 'complete',
      initialActivationCompleted: false,
      eligibility: iosBootstrap.eligibility,
      account: iosAccount,
      contacts: iosHome.contactsSync,
      readiness: iosReadiness,
      automation: pausedHome.automation,
    };
    const neverEnabled = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(pausedHome)),
      getSetup: jest.fn(async () => ok(neverEnabledSetup)),
      getMessageEditor: jest.fn(async () => ok(neverEnabledMessage)),
      getPolicyEditor: jest.fn(async () => ok(neverEnabledPolicy)),
    });

    const first = await renderLiveApp(neverEnabled.port);
    expect(await screen.findByText('Step 4 of 4')).toBeTruthy();
    expect(screen.queryByTestId('live-app-shell')).toBeNull();
    await first.unmount();

    const intentionallyPaused = createPort({
      getBootstrap: jest.fn(async () => ok(iosBootstrap)),
      getHome: jest.fn(async () => ok(pausedHome)),
      getSetup: jest.fn(async () =>
        ok({ ...neverEnabledSetup, initialActivationCompleted: true }),
      ),
    });
    await renderLiveApp(intentionallyPaused.port);

    expect(
      await screen.findByText('Birthday reminders are paused'),
    ).toBeTruthy();
    expect(screen.queryByTestId('live-product-setup-journey')).toBeNull();
  });

  it.each(['action-required', 'paused-repair'] as const)(
    'uses durable Android activation history for %s instead of guessing from the current mode',
    async effective => {
      const interruptedHome: HomeProjection = {
        ...liveHome('active'),
        automation: {
          platform: 'android',
          desired: 'paused',
          effective,
          readiness,
        },
      };
      const interruptedSetup: SetupProjection = {
        ...completeSetup,
        initialActivationCompleted: false,
        automation: interruptedHome.automation,
      };
      const firstActivationMessage = {
        kind: 'configured' as const,
        draft: {
          language: 'en' as const,
          tone: 'warm' as const,
          placeholderMode: {
            kind: 'generic' as const,
            requiredCount: 0 as const,
          },
          text: 'Happy birthday!' as PrivateMessageText,
          requestedSegmentCap: 1 as const,
        },
      };
      const firstActivationPolicy = {
        kind: 'configured' as const,
        draft: {
          primaryStart: '09:00',
          primaryEnd: '11:00',
          latePolicy: { kind: 'none' as const },
          dailyCap: 10,
        },
      };
      const firstActivation = createPort({
        getHome: jest.fn(async () => ok(interruptedHome)),
        getSetup: jest.fn(async () => ok(interruptedSetup)),
        getMessageEditor: jest.fn(async () => ok(firstActivationMessage)),
        getPolicyEditor: jest.fn(async () => ok(firstActivationPolicy)),
      });

      const first = await renderLiveApp(firstActivation.port);
      expect(await screen.findByText('Step 4 of 4')).toBeTruthy();
      expect(screen.queryByTestId('live-app-shell')).toBeNull();
      await first.unmount();

      const laterState = createPort({
        getHome: jest.fn(async () => ok(interruptedHome)),
        getSetup: jest.fn(async () =>
          ok({ ...interruptedSetup, initialActivationCompleted: true }),
        ),
      });
      await renderLiveApp(laterState.port);

      expect(await screen.findByTestId('live-home-screen')).toBeTruthy();
      expect(screen.queryByTestId('live-product-setup-journey')).toBeNull();
    },
  );

  it('updates the accessibility focus target after tab, nested, back, and native deep-link routes', async () => {
    let hardwareBack: (() => boolean | null | undefined) | undefined;
    jest
      .spyOn(BackHandler, 'addEventListener')
      .mockImplementation((_event, handler) => {
        hardwareBack = () => handler({} as never);
        return { remove: jest.fn() };
      });
    let pendingRoute: import('../domain/navigation/model').NativeRouteProjection =
      { kind: 'none' };
    const harness = createPort({
      getPendingRoute: jest.fn(async () => ok(pendingRoute)),
    });

    await renderLiveApp(harness.port);
    await screen.findByTestId('live-home-screen');

    await fireEvent.press(screen.getByTestId('live-tab-people'));
    await waitFor(() =>
      expect(
        screen.getByTestId('route-accessibility-focus').props
          .accessibilityLabel,
      ).toBe('People'),
    );

    await fireEvent.press(
      await screen.findByTestId('live-person-contact-live-1'),
    );
    await waitFor(() =>
      expect(
        screen.getByTestId('route-accessibility-focus').props
          .accessibilityLabel,
      ).toBe('Person details'),
    );

    await act(async () => {
      expect(hardwareBack?.()).toBe(true);
    });
    await waitFor(() =>
      expect(
        screen.getByTestId('route-accessibility-focus').props
          .accessibilityLabel,
      ).toBe('People'),
    );

    pendingRoute = {
      kind: 'attention',
      routeId:
        'route-attention' as import('../domain/shared/brand').NativeRouteId,
      source: 'attention',
    };
    await act(async () => {
      harness.emitRoute({ kind: 'available' });
    });
    await waitFor(() =>
      expect(
        screen.getByTestId('route-accessibility-focus').props
          .accessibilityLabel,
      ).toBe('Needs attention'),
    );
  });

  it('isolates a raw person name inside its localized accessibility label', async () => {
    const displayName = 'ليلى' as PrivateDisplayName;
    const { port } = createPort({
      listPeople: jest.fn(async () =>
        ok({
          items: [{ ...contactSummary(), displayName }],
          totalCount: 1,
        }),
      ),
    });

    await renderLiveApp(port);
    await fireEvent.press(await screen.findByTestId('live-tab-people'));

    expect(
      (await screen.findByTestId('live-person-contact-live-1')).props
        .accessibilityLabel,
    ).toContain(`\u2068${displayName}\u2069`);
  });

  it('runs a non-shipping production pseudo-RTL harness without fixture content', async () => {
    appI18n.addResourceBundle(
      'ar-XB',
      'translation',
      resources['ar-XB'].translation,
      true,
      true,
    );
    await appI18n.changeLanguage('ar-XB');
    const { port } = createPort();

    await renderLiveApp(port);

    expect(await screen.findByTestId('live-home-screen')).toBeTruthy();
    expect(
      StyleSheet.flatten(screen.getByTestId('app-direction-root').props.style)
        .direction,
    ).toBe('rtl');
    expect(screen.queryByText(/Interactive UI fixture/iu)).toBeNull();
    expect(screen.queryByText(/Synthetic data/iu)).toBeNull();
  });
});
