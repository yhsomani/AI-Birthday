import { createHash } from 'node:crypto';

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

const ownershipPath = (token: string): string =>
  'subscriptionPurchaseOwnership/' +
  createHash('sha256').update(token, 'utf8').digest('hex');

const purchaseRequest = (purchaseToken: string) => ({
  contractVersion: 1 as const,
  purchaseToken,
  productId: PRO_PRODUCT_ID,
  packageName: EXPECTED_PACKAGE_NAME,
  accountBinding: ACCOUNT_BINDING,
});

function mockDb(
  sink: (path: string, data: Record<string, unknown>) => void,
  owners: Map<string, string> = new Map<string, string>(),
  docs: Map<string, Record<string, unknown>> = new Map<string, Record<string, unknown>>(),
): Firestore {
  const doc = (path: string) =>
    ({
      set: (data: Record<string, unknown>): Promise<void> => {
        docs.set(path, data);
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
      get: (): Promise<DocumentSnapshot> => {
        if (docs.has(path)) {
          const docData = docs.get(path);
          return Promise.resolve({
            exists: true,
            data: () => docData,
          } as unknown as DocumentSnapshot);
        }
        const uid = owners.get(path);
        return Promise.resolve({
          exists: uid !== undefined,
          data: () => (uid === undefined ? undefined : { uid }),
        } as unknown as DocumentSnapshot);
      },
    }) as unknown as DocumentReference;

  // Writes apply immediately; the tests only need the transaction API shape.
  const runTransaction = async <T>(
    update: (transaction: unknown) => Promise<T>,
  ): Promise<T> =>
    update({
      get: (ref: DocumentReference) => ref.get(),
      create: (ref: DocumentReference, data: Record<string, unknown>) =>
        void ref.create(data),
      set: (ref: DocumentReference, data: Record<string, unknown>) =>
        void ref.set(data),
    });

  return { doc, runTransaction } as unknown as Firestore;
}

function verifier(
  snapshot: GooglePlaySubscriptionSnapshot,
): GooglePlaySubscriptionVerifier {
  return {
    getSubscription: () => Promise.resolve(snapshot),
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
    expect(savedData.isAutoRenewing).toBe(true);
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
      mockDb(() => undefined),
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

  it('does not report grace-period entitlement when expiry is already past', async () => {
    const snapshot: GooglePlaySubscriptionSnapshot = {
      ...activeSnapshot(),
      subscriptionState: 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
      lineItems: [
        {
          productId: PRO_PRODUCT_ID,
          expiryTime: new Date(Date.now() - 60_000).toISOString(),
        },
      ],
    };
    let savedData: Record<string, unknown> = {};
    const result = await new SubscriptionVerificationService(
      mockDb((_, data) => {
        savedData = data;
      }),
      verifier(snapshot),
    ).verifyPurchase('user-grace', purchaseRequest('grace-token'));

    expect(result.status).toBe('expired');
    expect(result.canUseAi).toBe(false);
    expect(result.expiryDateMs).toBeLessThan(Date.now());
    expect(savedData.status).toBe('expired');
    expect(savedData.canUseAi).toBe(false);
  });

  it('preserves a different usable entitlement when grace-period expiry is past', async () => {
    const docs = new Map<string, Record<string, unknown>>();
    const activeExpiry = Date.now() + 15 * 24 * 60 * 60 * 1000;
    const existing = {
      status: 'active',
      productId: PRO_PRODUCT_ID,
      expiryDateMs: activeExpiry,
      canUseAi: true,
      isAutoRenewing: true,
      purchaseTokenHash: 'existing_valid_token_hash',
    };
    docs.set('users/user-abc/entitlement/status', existing);
    const snapshot: GooglePlaySubscriptionSnapshot = {
      ...activeSnapshot(),
      subscriptionState: 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
      lineItems: [
        {
          productId: PRO_PRODUCT_ID,
          expiryTime: new Date(Date.now() - 60_000).toISOString(),
        },
      ],
    };

    const result = await new SubscriptionVerificationService(
      mockDb(() => undefined, new Map(), docs),
      verifier(snapshot),
    ).verifyPurchase('user-abc', purchaseRequest('different-grace-token'));

    expect(result.status).toBe('active');
    expect(result.canUseAi).toBe(true);
    expect(result.expiryDateMs).toBe(activeExpiry);
    expect(docs.get('users/user-abc/entitlement/status')).toEqual(existing);
  });

  it('accepts grace-period entitlement when Play reports a future expiry', async () => {
    const futureExpiry = Date.now() + 30 * 24 * 60 * 60 * 1000;
    const snapshot: GooglePlaySubscriptionSnapshot = {
      ...activeSnapshot(),
      subscriptionState: 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
      lineItems: [
        {
          productId: PRO_PRODUCT_ID,
          expiryTime: new Date(futureExpiry).toISOString(),
        },
      ],
    };
    const result = await new SubscriptionVerificationService(
      mockDb(() => undefined),
      verifier(snapshot),
    ).verifyPurchase('user-grace-future', purchaseRequest('grace-future-token'));

    expect(result.status).toBe('active');
    expect(result.canUseAi).toBe(true);
    expect(result.expiryDateMs).toBeGreaterThan(Date.now());
  });

  it('binds a verified purchase token to the first authenticated account', async () => {
    const owners = new Map<string, string>();
    const db = mockDb(() => undefined, owners);

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
      mockDb(() => undefined),
      {
        getSubscription: () =>
          Promise.reject(
            new GooglePlayApiError(
              'GOOGLE_PLAY_SUBSCRIPTION_LOOKUP_FAILED',
              404,
            ),
          ),
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
      mockDb(() => undefined),
      {
        getSubscription: () => Promise.reject(new Error('should not be called')),
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

  it('rejects purchase when Google Play obfuscatedExternalAccountId does not match accountBinding', async () => {
    const snapshot: GooglePlaySubscriptionSnapshot = {
      ...activeSnapshot(),
      obfuscatedExternalAccountId: 'different-account-binding',
    };

    const service = new SubscriptionVerificationService(
      mockDb(() => undefined),
      verifier(snapshot),
    );

    await expect(
      service.verifyPurchase('user-abc', {
        contractVersion: 1,
        purchaseToken: 'play_token_other_acct',
        productId: PRO_PRODUCT_ID,
        packageName: EXPECTED_PACKAGE_NAME,
        accountBinding: ACCOUNT_BINDING,
      }),
    ).rejects.toThrow('PURCHASE_ACCOUNT_MISMATCH');
  });

  it('accepts purchase when Google Play obfuscatedExternalAccountId matches accountBinding', async () => {
    const snapshot: GooglePlaySubscriptionSnapshot = {
      ...activeSnapshot(),
      obfuscatedExternalAccountId: ACCOUNT_BINDING,
    };

    const service = new SubscriptionVerificationService(
      mockDb(() => undefined),
      verifier(snapshot),
    );

    const result = await service.verifyPurchase('user-abc', {
      contractVersion: 1,
      purchaseToken: 'play_token_matching_acct',
      productId: PRO_PRODUCT_ID,
      packageName: EXPECTED_PACKAGE_NAME,
      accountBinding: ACCOUNT_BINDING,
    });

    expect(result.status).toBe('active');
    expect(result.canUseAi).toBe(true);
  });

  it('preserves existing active entitlement when verifying an invalid purchase token (404)', async () => {
    const docs = new Map<string, Record<string, unknown>>();
    const activeExpiry = Date.now() + 15 * 24 * 60 * 60 * 1000;
    docs.set('users/user-abc/entitlement/status', {
      status: 'active',
      productId: PRO_PRODUCT_ID,
      expiryDateMs: activeExpiry,
      canUseAi: true,
      isAutoRenewing: true,
      purchaseTokenHash: 'existing_valid_token_hash',
    });

    const service = new SubscriptionVerificationService(
      mockDb(() => undefined, new Map(), docs),
      {
        getSubscription: () =>
          Promise.reject(
            new GooglePlayApiError('GOOGLE_PLAY_SUBSCRIPTION_LOOKUP_FAILED', 404),
          ),
      },
    );

    const result = await service.verifyPurchase('user-abc', {
      contractVersion: 1,
      purchaseToken: 'invalid_unrelated_token',
      productId: PRO_PRODUCT_ID,
      packageName: EXPECTED_PACKAGE_NAME,
      accountBinding: ACCOUNT_BINDING,
    });

    expect(result.status).toBe('active');
    expect(result.canUseAi).toBe(true);
    expect(result.expiryDateMs).toBe(activeExpiry);
  });

  it('preserves existing active entitlement when checking a different expired token', async () => {
    const docs = new Map<string, Record<string, unknown>>();
    const activeExpiry = Date.now() + 15 * 24 * 60 * 60 * 1000;
    docs.set('users/user-abc/entitlement/status', {
      status: 'active',
      productId: PRO_PRODUCT_ID,
      expiryDateMs: activeExpiry,
      canUseAi: true,
      isAutoRenewing: true,
      purchaseTokenHash: 'existing_valid_token_hash',
    });

    const expiredSnapshot: GooglePlaySubscriptionSnapshot = {
      ...activeSnapshot(),
      subscriptionState: 'SUBSCRIPTION_STATE_EXPIRED',
      lineItems: [
        {
          productId: PRO_PRODUCT_ID,
          expiryTime: new Date(Date.now() - 3600_000).toISOString(),
        },
      ],
    };

    const service = new SubscriptionVerificationService(
      mockDb(() => undefined, new Map(), docs),
      verifier(expiredSnapshot),
    );

    const result = await service.verifyPurchase('user-abc', {
      contractVersion: 1,
      purchaseToken: 'different_expired_token',
      productId: PRO_PRODUCT_ID,
      packageName: EXPECTED_PACKAGE_NAME,
      accountBinding: ACCOUNT_BINDING,
    });

    expect(result.status).toBe('active');
    expect(result.canUseAi).toBe(true);
    expect(result.expiryDateMs).toBe(activeExpiry);
  });

  describe('account binding relaxation', () => {
    const mismatched = (): GooglePlaySubscriptionSnapshot => ({
      ...activeSnapshot(),
      obfuscatedExternalAccountId: 'different-account-binding',
    });

    it('denies a first-time claim with a binding mismatch and writes nothing', async () => {
      const owners = new Map<string, string>();
      const writes: string[] = [];
      const service = new SubscriptionVerificationService(
        mockDb(path => writes.push(path), owners),
        verifier(mismatched()),
      );

      await expect(
        service.verifyPurchase('user-abc', purchaseRequest('tok-first')),
      ).rejects.toThrow('PURCHASE_ACCOUNT_MISMATCH');
      expect(owners.size).toBe(0);
      expect(writes).toEqual([]);
    });

    it('accepts a binding mismatch when the same uid already owns the token', async () => {
      const owners = new Map<string, string>([
        [ownershipPath('tok-owned'), 'user-abc'],
      ]);
      const writes: string[] = [];
      const service = new SubscriptionVerificationService(
        mockDb(path => writes.push(path), owners),
        verifier(mismatched()),
      );

      const result = await service.verifyPurchase(
        'user-abc',
        purchaseRequest('tok-owned'),
      );

      expect(result.status).toBe('active');
      expect(result.canUseAi).toBe(true);
      expect(writes).toEqual(['users/user-abc/entitlement/status']);
    });

    it('denies a binding mismatch when another uid owns the token', async () => {
      const owners = new Map<string, string>([
        [ownershipPath('tok-other'), 'user-other'],
      ]);
      const writes: string[] = [];
      const service = new SubscriptionVerificationService(
        mockDb(path => writes.push(path), owners),
        verifier(mismatched()),
      );

      await expect(
        service.verifyPurchase('user-abc', purchaseRequest('tok-other')),
      ).rejects.toThrow('PURCHASE_ACCOUNT_MISMATCH');
      expect(writes).toEqual([]);
    });

    it('still denies another uid when the binding matches', async () => {
      const owners = new Map<string, string>([
        [ownershipPath('tok-other-match'), 'user-other'],
      ]);
      const service = new SubscriptionVerificationService(
        mockDb(() => undefined, owners),
        verifier({
          ...activeSnapshot(),
          obfuscatedExternalAccountId: ACCOUNT_BINDING,
        }),
      );

      await expect(
        service.verifyPurchase('user-abc', purchaseRequest('tok-other-match')),
      ).rejects.toThrow('PURCHASE_ACCOUNT_MISMATCH');
    });

    it('records ownership on a matching first-time claim', async () => {
      const owners = new Map<string, string>();
      const service = new SubscriptionVerificationService(
        mockDb(() => undefined, owners),
        verifier({
          ...activeSnapshot(),
          obfuscatedExternalAccountId: ACCOUNT_BINDING,
        }),
      );

      await service.verifyPurchase('user-abc', purchaseRequest('tok-match'));

      expect(owners.get(ownershipPath('tok-match'))).toBe('user-abc');
    });
  });

  describe('deletion fence', () => {
    const fences: [string, string, Record<string, unknown>][] = [
      ['a deletion tombstone', 'deletionTombstones/user-abc', { stage: 'DRAINING' }],
      [
        'a DELETING presence document',
        'coordinationPresence/user-abc',
        { state: 'DELETING' },
      ],
    ];

    for (const [label, fencePath, fenceData] of fences) {
      it('refuses verification and writes nothing when ' + label + ' exists', async () => {
        const owners = new Map<string, string>();
        const docs = new Map<string, Record<string, unknown>>([
          [fencePath, fenceData],
        ]);
        const writes: string[] = [];
        let playCalled = false;
        const service = new SubscriptionVerificationService(
          mockDb(path => writes.push(path), owners, docs),
          {
            getSubscription: () => {
              playCalled = true;
              return Promise.resolve(activeSnapshot());
            },
          },
        );

        await expect(
          service.verifyPurchase('user-abc', purchaseRequest('tok-deleting')),
        ).rejects.toThrow('ACCOUNT_DELETION_IN_PROGRESS');
        expect(playCalled).toBe(false);
        expect(owners.size).toBe(0);
        expect(writes).toEqual([]);
      });
    }

    it('refuses the invalid-token write path while deleting', async () => {
      const docs = new Map<string, Record<string, unknown>>([
        ['deletionTombstones/user-abc', { stage: 'PURGING' }],
      ]);
      const writes: string[] = [];
      const service = new SubscriptionVerificationService(
        mockDb(path => writes.push(path), new Map(), docs),
        {
          getSubscription: () =>
            Promise.reject(new GooglePlayApiError('LOOKUP_FAILED', 404)),
        },
      );

      await expect(
        service.verifyPurchase('user-abc', purchaseRequest('tok-gone')),
      ).rejects.toThrow('ACCOUNT_DELETION_IN_PROGRESS');
      expect(writes).toEqual([]);
    });

    it('re-checks the fence inside the write transaction', async () => {
      const docs = new Map<string, Record<string, unknown>>();
      const owners = new Map<string, string>();
      const writes: string[] = [];
      const db = mockDb(path => writes.push(path), owners, docs);
      // The fence appears while the Play lookup is in flight.
      const service = new SubscriptionVerificationService(db, {
        getSubscription: () => {
          docs.set('deletionTombstones/user-abc', { stage: 'DRAINING' });
          return Promise.resolve(activeSnapshot());
        },
      });

      await expect(
        service.verifyPurchase('user-abc', purchaseRequest('tok-race')),
      ).rejects.toThrow('ACCOUNT_DELETION_IN_PROGRESS');
      expect(owners.size).toBe(0);
      expect(writes).toEqual([]);
    });

    it('does not fence other accounts', async () => {
      const docs = new Map<string, Record<string, unknown>>([
        ['deletionTombstones/someone-else', { stage: 'DRAINING' }],
      ]);
      const service = new SubscriptionVerificationService(
        mockDb(() => undefined, new Map(), docs),
        verifier(activeSnapshot()),
      );

      const result = await service.verifyPurchase(
        'user-abc',
        purchaseRequest('tok-fine'),
      );
      expect(result.status).toBe('active');
    });
  });
});
