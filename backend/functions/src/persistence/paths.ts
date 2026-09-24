import type {
  CollectionReference,
  DocumentReference,
  Firestore,
} from 'firebase-admin/firestore';

import type { Purpose } from '../domain/model.js';

export interface AccountPaths {
  readonly account: DocumentReference;
  readonly tombstone: DocumentReference;
  readonly presence: DocumentReference;
  readonly operation: DocumentReference;
  readonly operationReceipt: (requestKey: string) => DocumentReference;
  readonly latestOperationReceipt: DocumentReference;
  readonly installations: CollectionReference;
  readonly occurrenceClaims: CollectionReference;
  readonly testClaims: CollectionReference;
  readonly occurrenceKeys: CollectionReference;
  readonly destinationGuards: CollectionReference;
  readonly claimRequests: CollectionReference;
  readonly armOutcomes: CollectionReference;
  readonly armBudgets: CollectionReference;
  readonly installation: (installationId: string) => DocumentReference;
  readonly claim: (purpose: Purpose, claimId: string) => DocumentReference;
  readonly occurrenceKey: (aliasKey: string) => DocumentReference;
  readonly destinationGuard: (aliasKey: string) => DocumentReference;
  readonly request: (requestKey: string) => DocumentReference;
  readonly outcome: (outcomeKey: string) => DocumentReference;
  readonly budget: (purpose: Purpose) => DocumentReference;
}

export function accountPaths(db: Firestore, uid: string): AccountPaths {
  const account = db.collection('accounts').doc(uid);
  return {
    account,
    tombstone: db.collection('deletionTombstones').doc(uid),
    presence: db.collection('coordinationPresence').doc(uid),
    operation: db.collection('coordinationOperationFences').doc(uid),
    operationReceipt: requestKey =>
      db.collection('coordinationOperationReceipts').doc(requestKey),

    latestOperationReceipt: db
      .collection('coordinationLatestReceipts')
      .doc(uid),
    installations: account.collection('installations'),
    occurrenceClaims: account.collection('occurrenceClaims'),
    testClaims: account.collection('testClaims'),
    occurrenceKeys: account.collection('occurrenceKeys'),
    destinationGuards: account.collection('destinationGuards'),
    claimRequests: account.collection('claimRequests'),
    armOutcomes: account.collection('armOutcomes'),
    armBudgets: account.collection('armBudgets'),
    installation: installationId =>
      account.collection('installations').doc(installationId),
    claim: (purpose, claimId) =>
      account
        .collection(purpose === 'BIRTHDAY' ? 'occurrenceClaims' : 'testClaims')
        .doc(claimId),
    occurrenceKey: aliasKey =>
      account.collection('occurrenceKeys').doc(aliasKey),
    destinationGuard: aliasKey =>
      account.collection('destinationGuards').doc(aliasKey),
    request: requestKey => account.collection('claimRequests').doc(requestKey),
    outcome: outcomeKey => account.collection('armOutcomes').doc(outcomeKey),
    budget: purpose =>
      account.collection('armBudgets').doc(purpose.toLowerCase()),
  };
}

export function globalControlPath(db: Firestore): DocumentReference {
  return db.collection('globalControl').doc('current');
}

// --- AI Gateway paths -------------------------------------------------------
// Server-only collections (firestore.rules denies all direct client access).
// The AI entitlement is WishWell's OWN subscription record, materialised by
// billing RTDN/webhooks. Usage entries are an append-only ledger; per-account
// monthly aggregates live in aiUsageSummary for cheap quota reads.

export function aiEntitlementPath(db: Firestore, uid: string): DocumentReference {
  return db.collection('users').doc(uid).collection('meta').doc('aiEntitlement');
}

export function aiUsageLedger(db: Firestore, uid: string): CollectionReference {
  return db.collection('users').doc(uid).collection('aiUsage');
}

export function aiUsageSummary(
  db: Firestore,
  uid: string,
  periodKey: string,
): DocumentReference {
  return aiUsageLedger(db, uid).doc(`summary-${periodKey}`);
}

export function aiGlobalBudgetPath(db: Firestore): DocumentReference {
  return db.collection('aiGlobalBudget').doc('current');
}

export function deletionReceiptPath(
  db: Firestore,
  receiptKey: string,
): DocumentReference {
  return db.collection('deletionReceipts').doc(receiptKey);
}
