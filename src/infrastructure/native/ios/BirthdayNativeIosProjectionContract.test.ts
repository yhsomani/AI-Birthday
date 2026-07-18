import {
  accountProjectionSchema,
  deviceEligibilitySchema,
  readinessIssueSchema,
  readinessProjectionSchema,
} from '../coreSchemas';
import {
  activityPageSchema,
  automationProjectionSchema,
  bootstrapProjectionSchema,
  homeProjectionSchema,
  peoplePageSchema,
  policyPreviewSchema,
  privacyInventorySchema,
  setupProjectionSchema,
  syncProjectionSchema,
  testProjectionSchema,
} from '../featureSchemas';

const checkedAt = '2026-07-12T08:30:00.000Z';
const capability = {
  platform: 'ios',
  deliveryMode: 'user-controlled-composer',
  unattendedSms: 'unavailable',
  userComposer: 'required',
} as const;
const account = {
  kind: 'signed-out',
  retainedSetup: 'none',
} as const;
const contacts = {
  kind: 'authorization-required',
  reason: 'contacts-authorization-required',
} as const;
const issues = [
  {
    id: 'readiness-account-required',
    code: 'account-reconnect-required',
    severity: 'blocking',
    blocks: ['composer'],
  },
  {
    id: 'readiness-companion-status-unverified',
    code: 'coordination-unavailable',
    severity: 'blocking',
    blocks: ['composer'],
  },
] as const;
const readiness = {
  platform: 'ios',
  composer: { kind: 'blocked', issues },
  unattendedAutomation: {
    kind: 'unavailable',
    reason: 'platform-composer-only',
  },
  lastCheckedAt: checkedAt,
} as const;
const eligibility = {
  kind: 'supported',
  capability,
  channelLabel: 'iOS Companion',
  chargeDisclosureVersion: 'user-controlled-system-composer-v1',
} as const;
const automation = {
  platform: 'ios',
  desired: 'paused',
  effective: 'not-configured',
  readiness,
} as const;

describe('BirthdayNative iOS projection contract', () => {
  it('accepts the fail-closed setup and home projection shapes', () => {
    expect(() =>
      bootstrapProjectionSchema.parse({
        capability,
        eligibility,
        account,
        setupStep: 'google-account',
      }),
    ).not.toThrow();
    expect(() =>
      setupProjectionSchema.parse({
        step: 'google-account',
        initialActivationCompleted: false,
        eligibility,
        account,
        contacts,
        readiness,
        automation,
      }),
    ).not.toThrow();
    expect(() =>
      homeProjectionSchema.parse({
        automation,
        counts: {
          enabled: 0,
          needsAttention: 1,
          unavailable: 0,
          today: 0,
          nextSevenDays: 0,
        },
        contactsSync: contacts,
      }),
    ).not.toThrow();
  });

  it('accepts only content-minimized auxiliary iOS projections', () => {
    expect(() => deviceEligibilitySchema.parse(eligibility)).not.toThrow();
    expect(() => accountProjectionSchema.parse(account)).not.toThrow();
    expect(() => syncProjectionSchema.parse(contacts)).not.toThrow();
    expect(() => readinessProjectionSchema.parse(readiness)).not.toThrow();
    expect(() => automationProjectionSchema.parse(automation)).not.toThrow();
    expect(() => readinessIssueSchema.array().parse(issues)).not.toThrow();
    expect(() => peoplePageSchema.parse({ items: [], totalCount: 0 })).not.toThrow();
    expect(() =>
      activityPageSchema.parse({
        items: [
          {
            id: 'composer-operation-1',
            kind: 'composer-reported-sent',
            occurredAt: checkedAt,
          },
          {
            id: 'composer-operation-2',
            kind: 'composer-outcome-unknown',
            reason: 'internal-contract-invalid',
            occurredAt: checkedAt,
            recovery: { route: 'attention' },
          },
        ],
      }),
    ).not.toThrow();
    expect(() =>
      activityPageSchema.parse({
        items: [
          {
            id: 'stale-activity-contract',
            kind: 'composer-failed',
            occurredAt: checkedAt,
            actionable: true,
          },
        ],
      }),
    ).toThrow();
    expect(() =>
      activityPageSchema.parse({
        items: [
          {
            id: 'invalid-recovery-route',
            kind: 'composer-failed',
            occurredAt: checkedAt,
            recovery: { route: 'composer' },
          },
        ],
      }),
    ).toThrow();
    expect(() =>
      testProjectionSchema.parse({
        platform: 'ios',
        kind: 'unavailable',
        reason: 'platform-composer-only',
      }),
    ).not.toThrow();
    expect(() =>
      privacyInventorySchema.parse({
        localContactCount: 0,
        enabledRecipientCount: 0,
        approvalCount: 0,
        activityCount: 0,
        templateCount: 0,
        localStorageBytes: 256,
        consentVersions: [],
        externalSmsCopiesNotControlled: true,
      }),
    ).not.toThrow();
  });

  it('accepts policy previews with more than 20 same-day iOS proposals', () => {
    expect(() =>
      policyPreviewSchema.parse({
        kind: 'valid',
        handle: 'ios-dense-date-policy-review',
        summary: '09:00–11:00',
        simulatedDays: 400,
        maximumPlannedInLocalDay: 21,
        maximumPlannedInRolling24Hours: 27,
      }),
    ).not.toThrow();
  });
});
