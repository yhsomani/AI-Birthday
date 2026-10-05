import type { DocumentReference, Firestore } from 'firebase-admin/firestore';
import { describe, expect, it } from 'vitest';

import {
  EXPECTED_PACKAGE_NAME,
  PRO_PRODUCT_ID,
  SubscriptionVerificationService,
} from '../src/services/subscriptionVerification.js';
import { verifyPurchaseSchema } from '../src/transport/schemas.js';

describe('Subscription Verification Service', () => {
  it('validates correct request schema', () => {
    const valid = {
      contractVersion: 1,
      purchaseToken: 'valid_token_123',
      productId: PRO_PRODUCT_ID,
      packageName: EXPECTED_PACKAGE_NAME,
    };
    expect(verifyPurchaseSchema.safeParse(valid).success).toBe(true);
  });

  it('rejects unexpected package or missing contract version', () => {
    expect(
      verifyPurchaseSchema.safeParse({
        contractVersion: 2,
        purchaseToken: 'token',
        productId: PRO_PRODUCT_ID,
        packageName: EXPECTED_PACKAGE_NAME,
      }).success,
    ).toBe(false);

    expect(
      verifyPurchaseSchema.safeParse({
        contractVersion: 1,
        purchaseToken: 'token',
        productId: PRO_PRODUCT_ID,
        packageName: 'com.other.app',
      }).success,
    ).toBe(true);
  });

  it('verifies purchase token and sets active entitlement in Firestore', async () => {
    let savedPath = '';
    let savedData: Record<string, unknown> = {};

    const mockDb = {
      doc: (path: string) =>
        ({
          set: (data: Record<string, unknown>): Promise<void> => {
            savedPath = path;
            savedData = data;
            return Promise.resolve();
          },
        }) as unknown as DocumentReference,
    } as unknown as Firestore;

    const service = new SubscriptionVerificationService(mockDb);
    const result = await service.verifyPurchase('user-abc', {
      contractVersion: 1,
      purchaseToken: 'play_token_valid',
      productId: PRO_PRODUCT_ID,
      packageName: EXPECTED_PACKAGE_NAME,
    });

    expect(result.status).toBe('active');
    expect(result.canUseAi).toBe(true);
    expect(result.productId).toBe(PRO_PRODUCT_ID);
    expect(result.expiryDateMs).toBeGreaterThan(Date.now());

    expect(savedPath).toBe('users/user-abc/entitlement/status');
    expect(savedData.status).toBe('active');
    expect(savedData.canUseAi).toBe(true);
  });

  it('rejects invalid purchase tokens by setting status to none', async () => {
    let savedData: Record<string, unknown> = {};

    const mockDb = {
      doc: () =>
        ({
          set: (data: Record<string, unknown>): Promise<void> => {
            savedData = data;
            return Promise.resolve();
          },
        }) as unknown as DocumentReference,
    } as unknown as Firestore;

    const service = new SubscriptionVerificationService(mockDb);
    const result = await service.verifyPurchase('user-abc', {
      contractVersion: 1,
      purchaseToken: 'invalid_token_test',
      productId: PRO_PRODUCT_ID,
      packageName: EXPECTED_PACKAGE_NAME,
    });

    expect(result.status).toBe('none');
    expect(result.canUseAi).toBe(false);
    expect(savedData.status).toBe('none');
    expect(savedData.canUseAi).toBe(false);
  });

  it('throws PACKAGE_MISMATCH when package name differs', async () => {
    const mockDb = {} as unknown as Firestore;
    const service = new SubscriptionVerificationService(mockDb);

    await expect(
      service.verifyPurchase('user-abc', {
        contractVersion: 1,
        purchaseToken: 'play_token',
        productId: PRO_PRODUCT_ID,
        packageName: 'com.wrong.pkg',
      }),
    ).rejects.toThrow('PACKAGE_MISMATCH');
  });
});
