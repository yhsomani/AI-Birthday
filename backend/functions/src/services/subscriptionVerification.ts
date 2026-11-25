import { createHash } from 'node:crypto';

import type {
  DocumentSnapshot,
  Firestore,
  Transaction,
} from 'firebase-admin/firestore';
import { Timestamp } from 'firebase-admin/firestore';
import { HttpsError } from 'firebase-functions/v2/https';

import type {
  VerifyPurchaseRequest,
  VerifyPurchaseResponse,
} from '../transport/schemas.js';
import {
  GooglePlayApiError,
  GooglePlaySubscriptionClient,
  type GooglePlaySubscriptionSnapshot,
} from './googlePlaySubscriptionClient.js';

export const PRO_PRODUCT_ID = 'ai_birthday_pro_monthly';
// MUST match android/app/build.gradle.kts applicationId (`com.yashsomani.ai_birthday`).
// One 's' (com.yashomani...) made Google Play return 404 for the purchases API
// call, so every subscription verified as 'none'.
export const EXPECTED_PACKAGE_NAME = 'com.yashsomani.ai_birthday';

const ENTITLED_STATES = new Set([
  'SUBSCRIPTION_STATE_ACTIVE',
  'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
  'SUBSCRIPTION_STATE_CANCELED',
]);

export interface GooglePlaySubscriptionVerifier {
  getSubscription(
    packageName: string,
    purchaseToken: string,
  ): Promise<GooglePlaySubscriptionSnapshot>;
}

export class SubscriptionVerificationService {
  constructor(
    private readonly db: Firestore,
    private readonly verifier: GooglePlaySubscriptionVerifier =
      new GooglePlaySubscriptionClient(),
  ) {}

  async verifyPurchase(
    uid: string,
    request: VerifyPurchaseRequest,
  ): Promise<VerifyPurchaseResponse> {
    if (request.packageName !== EXPECTED_PACKAGE_NAME) {
      throw new HttpsError('invalid-argument', 'PACKAGE_MISMATCH');
    }

    if (request.productId !== PRO_PRODUCT_ID) {
      throw new HttpsError('invalid-argument', 'INVALID_PRODUCT');
    }

    await this.assertAccountNotDeleting(uid);

    let purchase: GooglePlaySubscriptionSnapshot;
    try {
      purchase = await this.verifier.getSubscription(
        request.packageName,
        request.purchaseToken,
      );
    } catch (error) {
      if (
        error instanceof GooglePlayApiError &&
        (error.statusCode === 400 ||
          error.statusCode === 404 ||
          error.statusCode === 410)
      ) {
        return this.writeNone(uid, request.productId, request.purchaseToken);
      }

      throw new HttpsError(
        'unavailable',
        'GOOGLE_PLAY_VERIFICATION_UNAVAILABLE',
      );
    }

    const purchaseTokenHash = createHash('sha256')
      .update(request.purchaseToken, 'utf8')
      .digest('hex');

    const matchingItem = purchase.lineItems
      .filter(item => item.productId === request.productId)
      .sort((a, b) => {
        const aMs = a.expiryTime ? Date.parse(a.expiryTime) : 0;
        const bMs = b.expiryTime ? Date.parse(b.expiryTime) : 0;
        return bMs - aMs;
      })[0];

    const nowMs = Date.now();
    const parsedExpiryDateMs = matchingItem?.expiryTime
      ? Date.parse(matchingItem.expiryTime)
      : 0;
    const expiryDateMs = Number.isFinite(parsedExpiryDateMs)
      ? parsedExpiryDateMs
      : 0;
    const canUseAi =
      matchingItem !== undefined &&
      expiryDateMs > nowMs &&
      Number.isFinite(parsedExpiryDateMs) &&
      ENTITLED_STATES.has(purchase.subscriptionState);

    const status: VerifyPurchaseResponse['status'] = canUseAi
      ? 'active'
      : expiryDateMs > 0 && expiryDateMs <= nowMs
        ? 'expired'
        : 'none';

    const isAutoRenewing =
      purchase.subscriptionState === 'SUBSCRIPTION_STATE_ACTIVE';
    const playBinding = purchase.obfuscatedExternalAccountId;
    const bindingMismatch =
      playBinding !== null &&
      playBinding.length > 0 &&
      playBinding !== request.accountBinding;

    const ownershipRef = this.db.doc(
      'subscriptionPurchaseOwnership/' + purchaseTokenHash,
    );
    const entitlementRef = this.db.doc('users/' + uid + '/entitlement/status');

    // Fence check, ownership claim and entitlement write share one transaction
    // so a concurrent account deletion cannot interleave with the writes.
    try {
      return await this.db.runTransaction(
        async (transaction): Promise<VerifyPurchaseResponse> => {
          await this.assertAccountNotDeleting(uid, transaction);
          const ownershipSnapshot = await transaction.get(ownershipRef);
          const entitlementSnapshot = await transaction.get(entitlementRef);

          if (ownershipSnapshot.exists) {
            const ownerData = ownershipSnapshot.data() as
              | Record<string, unknown>
              | undefined;
            if (ownerData?.uid !== uid) {
              throw new HttpsError(
                'permission-denied',
                'PURCHASE_ACCOUNT_MISMATCH',
              );
            }
            // Ownership was established earlier by this uid, so a changed
            // Play account binding is tolerated.
          } else {
            if (bindingMismatch) {
              throw new HttpsError(
                'permission-denied',
                'PURCHASE_ACCOUNT_MISMATCH',
              );
            }
            transaction.create(ownershipRef, {
              uid,
              createdAt: Timestamp.now(),
            });
          }

          if (!canUseAi) {
            const active = activeEntitlementToPreserve(
              entitlementSnapshot,
              purchaseTokenHash,
            );
            if (active) {
              return active;
            }
          }

          transaction.set(
            entitlementRef,
            {
              status,
              productId: request.productId,
              expiryDateMs,
              canUseAi,
              isAutoRenewing,
              acknowledgementState: purchase.acknowledgementState,
              accountBinding: request.accountBinding,
              purchaseTokenHash,
              verifiedAtMs: nowMs,
              updatedAt: Timestamp.now(),
            },
            { merge: true },
          );

          return {
            status,
            productId: request.productId,
            expiryDateMs,
            canUseAi,
            isAutoRenewing,
          };
        },
      );
    } catch (error) {
      throw storeError(error);
    }
  }

  // Refuses while the account has a deletion tombstone or a DELETING presence
  // fence, so verification cannot recreate data after (or during) the purge.
  private async assertAccountNotDeleting(
    uid: string,
    transaction?: Transaction,
  ): Promise<void> {
    const tombstoneRef = this.db.doc('deletionTombstones/' + uid);
    const presenceRef = this.db.doc('coordinationPresence/' + uid);
    const tombstone = await (transaction
      ? transaction.get(tombstoneRef)
      : tombstoneRef.get());
    const presence = await (transaction
      ? transaction.get(presenceRef)
      : presenceRef.get());
    const presenceData = presence.data() as Record<string, unknown> | undefined;
    if (tombstone.exists || presenceData?.state === 'DELETING') {
      throw new HttpsError(
        'failed-precondition',
        'ACCOUNT_DELETION_IN_PROGRESS',
      );
    }
  }

  private async writeNone(
    uid: string,
    productId: string,
    purchaseToken?: string,
  ): Promise<VerifyPurchaseResponse> {
    const purchaseTokenHash = purchaseToken
      ? createHash('sha256').update(purchaseToken, 'utf8').digest('hex')
      : undefined;
    const entitlementRef = this.db.doc('users/' + uid + '/entitlement/status');

    try {
      return await this.db.runTransaction(
        async (transaction): Promise<VerifyPurchaseResponse> => {
          await this.assertAccountNotDeleting(uid, transaction);
          const existing = await transaction.get(entitlementRef);
          const active = activeEntitlementToPreserve(
            existing,
            purchaseTokenHash,
          );
          if (active) {
            return active;
          }

          transaction.set(
            entitlementRef,
            {
              status: 'none',
              productId,
              expiryDateMs: 0,
              canUseAi: false,
              isAutoRenewing: false,
              verifiedAtMs: Date.now(),
              updatedAt: Timestamp.now(),
            },
            { merge: true },
          );

          return {
            status: 'none',
            productId,
            expiryDateMs: 0,
            canUseAi: false,
            isAutoRenewing: false,
          };
        },
      );
    } catch (error) {
      throw storeError(error);
    }
  }
}

function storeError(error: unknown): unknown {
  return error instanceof HttpsError
    ? error
    : new HttpsError('unavailable', 'SUBSCRIPTION_OWNERSHIP_STORE_UNAVAILABLE');
}

function activeEntitlementToPreserve(
  existingDoc: DocumentSnapshot,
  purchaseTokenHash?: string,
): VerifyPurchaseResponse | null {
  if (!existingDoc.exists) {
    return null;
  }
  const data = existingDoc.data() as Record<string, unknown> | undefined;
  if (!data) {
    return null;
  }
  const currentExpiry =
    typeof data.expiryDateMs === 'number' ? data.expiryDateMs : 0;
  if (
    data.canUseAi === true &&
    currentExpiry > Date.now() &&
    (!purchaseTokenHash || data.purchaseTokenHash !== purchaseTokenHash)
  ) {
    return {
      status: 'active',
      productId:
        typeof data.productId === 'string' ? data.productId : PRO_PRODUCT_ID,
      expiryDateMs: currentExpiry,
      canUseAi: true,
      isAutoRenewing: data.isAutoRenewing === true,
    };
  }
  return null;
}
