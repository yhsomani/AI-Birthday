import { createHash } from 'node:crypto';

import type { Firestore } from 'firebase-admin/firestore';
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
        return this.writeNone(uid, request.productId);
      }

      throw new HttpsError(
        'unavailable',
        'GOOGLE_PLAY_VERIFICATION_UNAVAILABLE',
      );
    }

    const purchaseTokenHash = createHash('sha256')
      .update(request.purchaseToken, 'utf8')
      .digest('hex');

    await this.claimPurchaseOwnership(uid, purchaseTokenHash);

    const matchingItem = purchase.lineItems
      .filter(item => item.productId === request.productId)
      .sort((a, b) => {
        const aMs = a.expiryTime ? Date.parse(a.expiryTime) : 0;
        const bMs = b.expiryTime ? Date.parse(b.expiryTime) : 0;
        return bMs - aMs;
      })[0];

    const nowMs = Date.now();
    const expiryDateMs = matchingItem?.expiryTime
      ? Date.parse(matchingItem.expiryTime)
      : 0;

    const canUseAi =
      matchingItem !== undefined &&
      Number.isFinite(expiryDateMs) &&
      expiryDateMs > nowMs &&
      ENTITLED_STATES.has(purchase.subscriptionState);

    const status: VerifyPurchaseResponse['status'] = canUseAi
      ? 'active'
      : expiryDateMs > 0 && expiryDateMs <= nowMs
        ? 'expired'
        : 'none';

    await this.db.doc('users/' + uid + '/entitlement/status').set(
      {
        status,
        productId: request.productId,
        expiryDateMs,
        canUseAi,
        isAutoRenewing:
          purchase.subscriptionState === 'SUBSCRIPTION_STATE_ACTIVE',
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
      isAutoRenewing:
        purchase.subscriptionState === 'SUBSCRIPTION_STATE_ACTIVE',
    };
  }

  private async claimPurchaseOwnership(
    uid: string,
    purchaseTokenHash: string,
  ): Promise<void> {
    const ownershipRef = this.db.doc(
      'subscriptionPurchaseOwnership/' + purchaseTokenHash,
    );

    try {
      await ownershipRef.create({
        uid,
        createdAt: Timestamp.now(),
      });
      return;
    } catch (error) {
      const code =
        typeof error === 'object' &&
        error !== null &&
        'code' in error &&
        typeof error.code === 'number'
          ? error.code
          : null;

      if (code !== 6) {
        throw new HttpsError(
          'unavailable',
          'SUBSCRIPTION_OWNERSHIP_STORE_UNAVAILABLE',
        );
      }
    }

    const existing = await ownershipRef.get();
    const ownerData = existing.data() as Record<string, unknown> | undefined;
    const ownerUid = ownerData?.uid;
    if (ownerUid !== uid) {
      throw new HttpsError('permission-denied', 'PURCHASE_ACCOUNT_MISMATCH');
    }
  }

  private async writeNone(
    uid: string,
    productId: string,
  ): Promise<VerifyPurchaseResponse> {
    const nowMs = Date.now();
    await this.db.doc('users/' + uid + '/entitlement/status').set(
      {
        status: 'none',
        productId,
        expiryDateMs: 0,
        canUseAi: false,
        isAutoRenewing: false,
        verifiedAtMs: nowMs,
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
  }
}
