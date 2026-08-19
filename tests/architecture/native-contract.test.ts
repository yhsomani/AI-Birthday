import {
  accountProjectionSchema,
  approvalProjectionSchema,
  deviceEligibilitySchema,
} from '../../src/infrastructure/native/coreSchemas';
import {
  automationProjectionSchema,
  birthdayJobProjectionSchema,
  contactSummarySchema,
  currentPrivacyOperationProjectionSchema,
  notificationPermissionProjectionSchema,
  peoplePageSchema,
  senderTransferOperationProjectionSchema,
  senderTransferReviewSchema,
} from '../../src/infrastructure/native/featureSchemas';
import { decodeNativeResponse } from '../../src/infrastructure/native/decodeNativeResponse';

const baseRawResponse = {
  contractVersion: 1,
  revision: '12',
  generatedAt: '2026-07-12T08:30:00Z',
  kind: 'ok',
};

const androidCapability = {
  platform: 'android',
  deliveryMode: 'unattended-device-sms',
  minimumApiLevel: 29,
  unattendedSms: 'release-gated',
  userComposer: 'available-as-explicit-alternative',
};

const supportedEligibility = {
  kind: 'supported',
  capability: androidCapability,
  channelLabel: 'Managed distribution',
  chargeDisclosureVersion: 'sms-cost-v1',
};

const expectContractFailure = (value: unknown): void => {
  const result = decodeNativeResponse(value, deviceEligibilitySchema);
  expect(result).toEqual({
    kind: 'error',
    problem: {
      kind: 'internal',
      supportCode: 'NATIVE_CONTRACT_INVALID',
    },
  });
};

describe('native contract decoder', () => {
  it('strictly decodes device lifecycle recovery projections', () => {
    const decode = <Value>(
      payload: unknown,
      schema: Parameters<typeof decodeNativeResponse<Value>>[1],
    ) =>
      decodeNativeResponse(
        { ...baseRawResponse, payloadJson: JSON.stringify(payload) },
        schema,
      );

    expect(
      decode(
        { kind: 'settings-required' },
        notificationPermissionProjectionSchema,
      ).kind,
    ).toBe('ok');
    expect(
      decode(
        {
          kind: 'sender-transfer',
          handle: `st_${'a'.repeat(32)}`,
          preissuedPermitMayFinish: true,
          completionRequiresRecentGoogleAuthentication: true,
          consequenceKeys: [
            'transfer.consequence.old-phone-revoked',
            'transfer.consequence.new-phone-test-only',
            'transfer.consequence.test-required',
          ],
        },
        senderTransferReviewSchema,
      ).kind,
    ).toBe('ok');
    expect(
      decode(
        {
          kind: 'remote-draining',
          id: `transfer_${'b'.repeat(32)}`,
          preissuedPermitMayFinish: true,
          reason: 'transfer-pending',
          updatedAt: '2026-07-12T08:30:00Z',
          drainUntil: '2026-07-12T08:31:00Z',
        },
        senderTransferOperationProjectionSchema,
      ).kind,
    ).toBe('ok');
    expect(
      decode(
        { kind: 'unavailable', reason: 'coordination-unavailable' },
        currentPrivacyOperationProjectionSchema,
      ).kind,
    ).toBe('ok');
    expect(
      decode(
        {
          kind: 'remote-unknown',
          id: `privacy_${'a'.repeat(32)}`,
          action: 'delete-account',
          reason: 'coordination-unavailable',
          updatedAt: '2026-07-12T08:30:00Z',
          localDataErased: true,
          remoteDeletionComplete: false,
          sameAccountRetryAvailable: false,
          externalSmsCopiesNotErased: true,
        },
        currentPrivacyOperationProjectionSchema,
      ).kind,
    ).toBe('ok');
    expect(
      decode(
        {
          kind: 'complete',
          id: `privacy_${'b'.repeat(64)}`,
          action: 'delete-account',
          completedAt: '2026-07-12T08:30:00Z',
          localDataErased: true,
          remoteDeletionComplete: true,
          externalSmsCopiesNotErased: true,
        },
        currentPrivacyOperationProjectionSchema,
      ).kind,
    ).toBe('ok');
  });

  it('rejects weakened or ambiguous device lifecycle projections', () => {
    const invalidPayloads: readonly Readonly<{
      payload: unknown;
      schema: ZodType;
    }>[] = [
      {
        schema: senderTransferReviewSchema,
        payload: {
          kind: 'sender-transfer',
          handle: `st_${'a'.repeat(32)}`,
          preissuedPermitMayFinish: false,
          completionRequiresRecentGoogleAuthentication: false,
          consequenceKeys: [],
        },
      },
      {
        schema: senderTransferOperationProjectionSchema,
        payload: {
          kind: 'remote-draining',
          id: `transfer_${'b'.repeat(32)}`,
          preissuedPermitMayFinish: false,
          reason: 'transfer-pending',
          updatedAt: '2026-07-12T08:30:00Z',
        },
      },
      {
        schema: notificationPermissionProjectionSchema,
        payload: { kind: 'denied' },
      },
      {
        schema: currentPrivacyOperationProjectionSchema,
        payload: { kind: 'unavailable', reason: 'future-reason' },
      },
      {
        schema: currentPrivacyOperationProjectionSchema,
        payload: {
          kind: 'remote-unknown',
          id: `privacy_${'a'.repeat(32)}`,
          action: 'delete-account',
          reason: 'coordination-unavailable',
          updatedAt: '2026-07-12T08:30:00Z',
          localDataErased: true,
          remoteDeletionComplete: false,
          externalSmsCopiesNotErased: true,
        },
      },
      {
        schema: currentPrivacyOperationProjectionSchema,
        payload: {
          kind: 'remote-pending',
          id: '550e8400-e29b-41d4-a716-446655440000',
          action: 'delete-account',
          reason: 'coordination-unavailable',
          updatedAt: '2026-07-12T08:30:00Z',
        },
      },
      {
        schema: currentPrivacyOperationProjectionSchema,
        payload: {
          kind: 'complete',
          id: '550e8400-e29b-41d4-a716-446655440000',
          action: 'delete-account',
          completedAt: '2026-07-12T08:30:00Z',
          localDataErased: true,
          remoteDeletionComplete: true,
          externalSmsCopiesNotErased: true,
        },
      },
    ];

    invalidPayloads.forEach(({ payload, schema }) => {
      expect(
        decodeNativeResponse(
          { ...baseRawResponse, payloadJson: JSON.stringify(payload) },
          schema,
        ).kind,
      ).toBe('error');
    });
  });

  it('decodes a strict versioned projection envelope', () => {
    const result = decodeNativeResponse(
      { ...baseRawResponse, payloadJson: JSON.stringify(supportedEligibility) },
      deviceEligibilitySchema,
    );

    expect(result).toEqual({
      kind: 'ok',
      envelope: {
        contractVersion: 1,
        revision: '12',
        generatedAt: '2026-07-12T08:30:00Z',
        value: supportedEligibility,
      },
    });
  });

  it.each([
    { ...baseRawResponse, contractVersion: 2, payloadJson: '{}' },
    { ...baseRawResponse, revision: '-1', payloadJson: '{}' },
    { ...baseRawResponse, generatedAt: 'not-an-instant', payloadJson: '{}' },
    {
      ...baseRawResponse,
      generatedAt: '2026-02-31T08:30:00Z',
      payloadJson: '{}',
    },
    { ...baseRawResponse, kind: 'success', payloadJson: '{}' },
    { ...baseRawResponse, payloadJson: '{' },
    {
      ...baseRawResponse,
      payloadJson: JSON.stringify({
        ...supportedEligibility,
        unexpected: true,
      }),
    },
    {
      ...baseRawResponse,
      payloadJson: JSON.stringify({
        ...supportedEligibility,
        kind: 'future-state',
      }),
    },
  ])('fails closed for malformed or future contract data', raw => {
    expectContractFailure(raw);
  });

  it('decodes only closed, content-free native problems', () => {
    const result = decodeNativeResponse(
      {
        ...baseRawResponse,
        kind: 'error',
        payloadJson: JSON.stringify({
          kind: 'stale-revision',
          latestRevision: '13',
        }),
      },
      deviceEligibilitySchema,
    );

    expect(result).toEqual({
      kind: 'error',
      problem: { kind: 'stale-revision', latestRevision: '13' },
    });
  });

  it('rejects oversized bridge envelopes before parsing their payload', () => {
    expectContractFailure({
      ...baseRawResponse,
      payloadJson: 'x'.repeat(1_048_577),
    });
  });

  it('bounds native identifiers and projection pages', () => {
    const contact = {
      id: 'contact-1',
      displayName: 'Private name',
      readiness: { kind: 'ready' },
      enrollment: { kind: 'off' },
    };

    const oversizedId = decodeNativeResponse(
      {
        ...baseRawResponse,
        payloadJson: JSON.stringify({
          ...contact,
          id: `c${'a'.repeat(128)}`,
        }),
      },
      contactSummarySchema,
    );
    const oversizedPage = decodeNativeResponse(
      {
        ...baseRawResponse,
        payloadJson: JSON.stringify({
          items: Array.from({ length: 51 }, (_, index) => ({
            ...contact,
            id: `contact-${index}`,
          })),
          totalCount: 51,
        }),
      },
      peoplePageSchema,
    );
    const bidiSpoofedName = decodeNativeResponse(
      {
        ...baseRawResponse,
        payloadJson: JSON.stringify({
          ...contact,
          displayName: 'Trusted\u202Eeman',
        }),
      },
      contactSummarySchema,
    );
    const rawPhoneLeak = decodeNativeResponse(
      {
        ...baseRawResponse,
        payloadJson: JSON.stringify({
          ...contact,
          maskedPhone: '+919876543210',
        }),
      },
      contactSummarySchema,
    );

    expect(oversizedId.kind).toBe('error');
    expect(oversizedPage.kind).toBe('error');
    expect(bidiSpoofedName.kind).toBe('error');
    expect(rawPhoneLeak.kind).toBe('error');
  });

  it('rejects platform-shape confusion instead of inferring capabilities', () => {
    const iosWithAndroidAutomation = {
      platform: 'ios',
      desired: 'on',
      effective: 'active',
      readiness: {
        platform: 'android',
        test: { kind: 'allowed' },
        activation: { kind: 'allowed' },
        birthday: { kind: 'allowed' },
        lastCheckedAt: '2026-07-12T08:30:00Z',
      },
    };

    const result = decodeNativeResponse(
      {
        ...baseRawResponse,
        payloadJson: JSON.stringify(iosWithAndroidAutomation),
      },
      automationProjectionSchema,
    );

    expect(result.kind).toBe('error');
  });

  it('rejects unknown values in every safety-relevant union', () => {
    const results = [
      decodeNativeResponse(
        {
          ...baseRawResponse,
          payloadJson: JSON.stringify({
            kind: 'connected',
            displayEmail: 'private@example.com',
            sender: {
              platform: 'android',
              kind: 'future-sender-mode',
              epochLabel: 'Sender 1',
            },
          }),
        },
        accountProjectionSchema,
      ),
      decodeNativeResponse(
        {
          ...baseRawResponse,
          payloadJson: JSON.stringify({
            id: 'contact-1',
            displayName: 'Private name',
            readiness: { kind: 'future-readiness' },
            enrollment: { kind: 'off' },
          }),
        },
        contactSummarySchema,
      ),
      decodeNativeResponse(
        {
          ...baseRawResponse,
          payloadJson: JSON.stringify({
            kind: 'invalidated',
            reasons: ['future-invalidation-reason'],
          }),
        },
        approvalProjectionSchema,
      ),
      decodeNativeResponse(
        {
          ...baseRawResponse,
          payloadJson: JSON.stringify({
            platform: 'android',
            occurrenceId: 'occurrence-1',
            occurrenceDate: '2026-07-12',
            phase: 'future-job-phase',
            updatedAt: '2026-07-12T08:30:00Z',
            attempt: 1,
          }),
        },
        birthdayJobProjectionSchema,
      ),
    ];

    results.forEach(result => {
      expect(result.kind).toBe('error');
    });
  });
});
import type { ZodType } from 'zod';
