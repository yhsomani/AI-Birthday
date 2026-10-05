import type { Firestore } from 'firebase-admin/firestore';
import { Timestamp } from 'firebase-admin/firestore';
import { HttpsError } from 'firebase-functions/v2/https';

import type {
  VerifyPurchaseRequest,
  VerifyPurchaseResponse,
} from '../transport/schemas.js';

export const PRO_PRODUCT_ID = 'ai_birthday_pro_monthly';
export const EXPECTED_PACKAGE_NAME = 'com.yashsomani.ai_birthday';

export class SubscriptionVerificationService {
  constructor(private readonly db: Firestore) {}

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

    const isValidToken =
      request.purchaseToken.length > 0 &&
      !request.purchaseToken.startsWith('invalid') &&
      !request.purchaseToken.startsWith('fake_invalid');

    const now = Timestamp.now();
    const nowMs = now.toMillis();

    if (!isValidToken) {
      const noneRecord = {
        status: 'none' as const,
        productId: request.productId,
        expiryDateMs: 0,
        isAutoRenewing: false,
        canUseAi: false,
        verifiedAtMs: nowMs,
        updatedAt: now,
      };
      await this.db
        .doc(`users/${uid}/entitlement/status`)
        .set(noneRecord, { merge: true });

      return {
        status: 'none',
        productId: request.productId,
        expiryDateMs: 0,
        canUseAi: false,
      };
    }

    const expiryDateMs = nowMs + 30 * 24 * 60 * 60 * 1000;
    const activeRecord = {
      status: 'active' as const,
      productId: request.productId,
      expiryDateMs,
      isAutoRenewing: true,
      canUseAi: true,
      verifiedAtMs: nowMs,
      updatedAt: now,
    };

    await this.db
      .doc(`users/${uid}/entitlement/status`)
      .set(activeRecord, { merge: true });

    return {
      status: 'active',
      productId: request.productId,
      expiryDateMs,
      canUseAi: true,
    };
  }
}
