import type {
  DocumentReference,
  DocumentSnapshot,
  Firestore,
} from 'firebase-admin/firestore';
import { describe, expect, it } from 'vitest';

import {
  GooglePlayApiError,
  type GooglePlaySubscriptionSnapshot,
} from '../src/services/googlePlaySubscriptionClient.js';
import {
  EXPECTED_PACKAGE_NAME,
  PRO_PRODUCT_ID,
  SubscriptionVerificationService,
  type GooglePlaySubscriptionVerifier,
} from '../src/services/subscriptionVerification.js';
import { verifyPurchaseSchema } from '../src/transport/schemas.js';

const ACCOUNT_BINDING = 'acct-binding-test-123456';

function mockDb(
  sink: (path: string, data: Record<string, unknown>) => void,
  owners: Map<string, string> = new Map(),
): Firestore {
  const doc = (path: string) =>
    ({
      set: (data: Record<string, unknown>): Promise<void> => {
        sink(path, data);
        return Promise.resolve();
      },
      create: (data: Record<string, unknown>): Promise<void> => {
        if (owners.has(path)) {
          return Promise.reject(
            Object.assign(new Error('ALREADY_EXISTS'), { code: 6 }),
          );
        }
        owners.set(path, String(data.uid));
        return Promise.resolve();
      },
      get: async (): Promise<DocumentSnapshot> => {
        const uid = owners.get(path);
        return {
          exists: uid !== undefined,
          data: () => (uid === undefined ? undefined : { uid }),
        } as unknown as DocumentSnapshot;
      },
    }) as unknown as DocumentReference;

  return { doc } as unknown as Firestore;
}

function verifier(
  snapshot: GooglePlaySubscriptionSnapshot,
): GooglePlaySubscriptionVerifier {
  return {
    getSubscription: async () => snapshot,
  };
}

const activeSnapshot = (): GooglePlaySubscriptionSnapshot => ({
  subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
  acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING',
  obfuscatedExternalAccountId: null,
  lineItems: [
    {
      productId: PRO_PRODUCT_ID,
      expiryTime: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
    },
  ],
});

describe('Subscription Verification Service', () => {
  it('validates the complete request schema', () => {
    const valid = {
      contractVersion: 1,
      purchaseToken: 'play_token_valid',
      productId: PRO_PRODUCT_ID,
      packageName: EXPECTED_PACKAGE_NAME,
      accountBinding: ACCOUNT_BINDING,
    };

    expect(verifyPurchaseSchema.safeParse(valid).success).toBe(true);
  });

  it('rejects requests that omit the account binding', () => {
    expect(
      verifyPurchaseSchema.safeParse({
        contractVersion: 1,
        purchaseToken: 'play_token',
        productId: PRO_PRODUCT_ID,
        packageName: EXPECTED_PACKAGE_NAME,
      }).success,
    ).toBe(false);
  });

  it('grants entitlement only for a verified active Play subscription', async () => {
    let savedData: Record<string, unknown> = {};
    const owners = new Map<string, string>();
    const service = new SubscriptionVerificationService(
      mockDb((_, data) => {
        savedData = data;
      }, owners),
      verifier(activeSnapshot()),
    );

    const result = await service.verifyPurchase('user-abc', {
      contractVersion: 1,
      purchaseToken: 'play_token_valid',
      productId: PRO_PRODUCT_ID,
      packageName: EXPECTED_PACKAGE_NAME,
      accountBinding: ACCOUNT_BINDING,
    });

    expect(result.status).toBe('active');
    expect(result.canUseAi).toBe(true);
    expect(result.productId).toBe(PRO_PRODUCT_ID);
    expect(result.expiryDateMs).toBeGreaterThan(Date.now());
    expect(savedData.status).toBe('active');
    expect(savedData.canUseAi).toBe(true);
  });

  it('does not grant entitlement for expired subscriptions', async () => {
    const snapshot: GooglePlaySubscriptionSnapshot = {
      ...activeSnapshot(),
      subscriptionState: 'SUBSCRIPTION_STATE_EXPIRED',
      lineItems: [
        {
          productId: PRO_PRODUCT_ID,
          expiryTime: new Date(Date.now() - 60_000).toISOString(),
        },
      ],
    };

    const result = await new SubscriptionVerificationService(
      mockDb(() => {}),
      verifier(snapshot),
    ).verifyPurchase('user-abc', {
      contractVersion: 1,
      purchaseToken: 'expired_token',
      productId: PRO_PRODUCT_ID,
      packageName: EXPECTED_PACKAGE_NAME,
      accountBinding: ACCOUNT_BINDING,
    });

    expect(result.status).toBe('expired');
    expect(result.canUseAi).toBe(false);
  });

  it('binds a verified purchase token to the first authenticated account', async () => {
    const owners = new Map<string, string>();
    const db = mockDb(() => {}, owners);

    const firstUser = new SubscriptionVerificationService(
      db,
      verifier(activeSnapshot()),
    );
    await firstUser.verifyPurchase('user-one', {
      contractVersion: 1,
      purchaseToken: 'shared-token',
      productId: PRO_PRODUCT_ID,
      packageName: EXPECTED_PACKAGE_NAME,
      accountBinding: ACCOUNT_BINDING,
    });

    const secondUser = new SubscriptionVerificationService(
      db,
      verifier(activeSnapshot()),
    );

    await expect(
      secondUser.verifyPurchase('user-two', {
        contractVersion: 1,
        purchaseToken: 'shared-token',
        productId: PRO_PRODUCT_ID,
        packageName: EXPECTED_PACKAGE_NAME,
        accountBinding: ACCOUNT_BINDING,
      }),
    ).rejects.toThrow('PURCHASE_ACCOUNT_MISMATCH');
  });

  it('marks invalid Play tokens as unentitled', async () => {
    const result = await new SubscriptionVerificationService(
      mockDb(() => {}),
      {
        getSubscription: async () => {
          throw new GooglePlayApiError(
            'GOOGLE_PLAY_SUBSCRIPTION_LOOKUP_FAILED',
            404,
          );
        },
      },
    ).verifyPurchase('user-abc', {
      contractVersion: 1,
      purchaseToken: 'not-a-real-token',
      productId: PRO_PRODUCT_ID,
      packageName: EXPECTED_PACKAGE_NAME,
      accountBinding: ACCOUNT_BINDING,
    });

    expect(result.status).toBe('none');
    expect(result.canUseAi).toBe(false);
  });

  it('throws PACKAGE_MISMATCH before contacting Google Play', async () => {
    const service = new SubscriptionVerificationService(
      mockDb(() => {}),
      {
        getSubscription: async () => {
          throw new Error('should not be called');
        },
      },
    );

    await expect(
      service.verifyPurchase('user-abc', {
        contractVersion: 1,
        purchaseToken: 'play_token',
        productId: PRO_PRODUCT_ID,
        packageName: 'com.wrong.pkg',
        accountBinding: ACCOUNT_BINDING,
      }),
    ).rejects.toThrow('PACKAGE_MISMATCH');
  });
});
