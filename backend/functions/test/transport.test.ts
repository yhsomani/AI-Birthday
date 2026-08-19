import { describe, expect, it } from 'vitest';

import {
  birthdayClaimSchema,
  contactDerivedResetSchema,
  coordinationLifecycleStatusSchema,
  deletionReceiptSchema,
  deletionSchema,
  retrySchema,
  senderReleaseSchema,
  testClaimSchema,
} from '../src/transport/schemas.js';

import { INSTALLATION_ID } from './fixtures.js';

const binding = {
  contractVersion: 1,
  ledgerGeneration: 'ledger-generation-1',
  installationId: INSTALLATION_ID,
  senderEpoch: 1,
  resetGeneration: 1,
  appBuildNumber: 100,
  policyVersion: 7,
  distributionChannel: 'PLAY',
} as const;

describe('strict content-minimized callable schemas', () => {
  it('accepts only fixed-length prehashes for Birthday claims', () => {
    const valid = {
      ...binding,
      purpose: 'BIRTHDAY',
      claimRequestId: '00000000-0000-4000-8000-000000000001',
      recipientPrehashAliases: ['ab'.repeat(32)],
      destinationPrehashAliases: ['cd'.repeat(32)],
    } as const;
    expect(birthdayClaimSchema.safeParse(valid).success).toBe(true);
    expect(
      birthdayClaimSchema.safeParse({ ...valid, recipientName: 'forbidden' })
        .success,
    ).toBe(false);
    expect(
      birthdayClaimSchema.safeParse({
        ...valid,
        destinationPrehashAliases: ['+919999999999'],
      }).success,
    ).toBe(false);
  });

  it('keeps TEST separate from recipient occurrence and birthday fields', () => {
    const valid = {
      ...binding,
      purpose: 'TEST',
      testRequestId: '00000000-0000-4000-8000-000000000002',
      testConfigurationPrehash: '12'.repeat(32),
      testDestinationPrehash: '34'.repeat(32),
    } as const;
    expect(testClaimSchema.safeParse(valid).success).toBe(true);
    expect(
      testClaimSchema.safeParse({ ...valid, birthday: '2030-01-01' }).success,
    ).toBe(false);
    expect(
      testClaimSchema.safeParse({
        ...valid,
        recipientPrehashAliases: ['ab'.repeat(32)],
      }).success,
    ).toBe(false);
  });

  it('requires an exact idempotency identity for safe retry authorization', () => {
    const valid = {
      ...binding,
      purpose: 'BIRTHDAY',
      claimId: 'v1.claim-key',
      retryRequestId: '30000000-0000-4000-8000-000000000003',
      proof: 'ALL_PARTS_NO_SERVICE',
    } as const;
    expect(retrySchema.safeParse(valid).success).toBe(true);
    expect(
      retrySchema.safeParse({
        ...binding,
        purpose: 'BIRTHDAY',
        claimId: 'v1.claim-key',
        proof: 'ALL_PARTS_NO_SERVICE',
      }).success,
    ).toBe(false);
    expect(
      retrySchema.safeParse({ ...valid, phone: '+919999999999' }).success,
    ).toBe(false);
  });

  it('accepts only a lowercase stable UUID for account-global contact reset', () => {
    const valid = {
      contractVersion: 1,
      requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaa701',
    } as const;
    expect(contactDerivedResetSchema.safeParse(valid).success).toBe(true);
    expect(
      contactDerivedResetSchema.safeParse({ ...valid, email: 'forbidden' })
        .success,
    ).toBe(false);
    expect(
      contactDerivedResetSchema.safeParse({
        ...valid,
        requestId: valid.requestId.toUpperCase(),
      }).success,
    ).toBe(false);
  });

  it('uses the same lowercase bearer UUID for deletion and signed-out receipt status', () => {
    const requestId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaa703';
    expect(
      deletionSchema.safeParse({ contractVersion: 1, requestId }).success,
    ).toBe(true);
    expect(
      deletionReceiptSchema.safeParse({
        contractVersion: 1,
        receiptId: requestId,
      }).success,
    ).toBe(true);
    expect(
      deletionReceiptSchema.safeParse({
        contractVersion: 1,
        receiptId: requestId.toUpperCase(),
      }).success,
    ).toBe(false);
    expect(
      deletionReceiptSchema.safeParse({
        contractVersion: 1,
        receiptId: requestId,
        email: 'forbidden',
      }).success,
    ).toBe(false);
    for (const nonV4 of [
      'aaaaaaaa-aaaa-1aaa-8aaa-aaaaaaaaa703',
      'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaa703',
    ]) {
      expect(
        deletionSchema.safeParse({
          contractVersion: 1,
          requestId: nonV4,
        }).success,
      ).toBe(false);
      expect(
        deletionReceiptSchema.safeParse({
          contractVersion: 1,
          receiptId: nonV4,
        }).success,
      ).toBe(false);
    }
  });

  it('binds sender release to one exact Android generation without content', () => {
    const valid = {
      contractVersion: 1,
      requestId: '00000000-0000-4000-8000-000000000702',
      installationId: INSTALLATION_ID,
      senderEpoch: 4,
      resetGeneration: 3,
    } as const;
    expect(senderReleaseSchema.safeParse(valid).success).toBe(true);
    expect(
      senderReleaseSchema.safeParse({ ...valid, phone: '+919999999999' })
        .success,
    ).toBe(false);
    expect(
      senderReleaseSchema.safeParse({ ...valid, senderEpoch: 0 }).success,
    ).toBe(false);
  });

  it('keeps lost-journal lifecycle status account-scoped and content-free', () => {
    expect(
      coordinationLifecycleStatusSchema.safeParse({ contractVersion: 1 })
        .success,
    ).toBe(true);
    expect(
      coordinationLifecycleStatusSchema.safeParse({
        contractVersion: 1,
        requestId: 'forbidden',
      }).success,
    ).toBe(false);
  });
});
