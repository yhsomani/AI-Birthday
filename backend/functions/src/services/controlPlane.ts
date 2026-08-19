import {
  Timestamp,
  type DocumentReference,
  type DocumentSnapshot,
  type Firestore,
  type Query,
  type Transaction,
} from 'firebase-admin/firestore';

import {
  decideAdvanceContactDerivedReset,
  decideAdvanceSenderRelease,
  decideBeginContactDerivedReset,
  decideBeginSenderRelease,
  makeCoordinationOperationReceipt,
  type OperationRefusalReason,
} from '../domain/coordinationOperations.js';

import {
  completedDeletionReceipt,
  deletionReceiptResponse,
  deriveDeletionReceiptKey,
  inProgressDeletionReceipt,
  type AccountDeletionReceiptResponse,
} from '../domain/deletionReceipt.js';
import {
  decideAdvanceDeletionDrain,
  decideArm,
  decideArmStatus,
  decideBeginDeletion,
  decideBeginTransfer,
  decideClaim,

  decideCompleteTransfer,
  decideRegistration,
  decideSafeRetry,
  decideTestReport,

  type ArmDecision,
  type ArmInput,
  type ArmSnapshot,
  type ClaimCollisionSnapshot,
  type ClaimInput,
  type TestReportInput,
} from '../domain/decisions.js';
import {
  DAY_MS,
  SCHEMA_VERSION,
  type Claim,
  type CoordinationPresence,
  type CoordinationOperation,
  type CoordinationOperationReceipt,
  type DeletionTombstone,
  type GlobalControl,
  type AccountFence,
  type KeyRing,
} from '../domain/model.js';
import {
  deriveOperationAccountKey,
  deriveOperationIdentity,
} from '../domain/operationIdentity.js';
import {
  boundedSweepAttempt,
  checkControlCompatibility,
  checkGlobalControl,
  safeAddMs,
  nextRepairSweepAtMs,
  renewedLeaseUntil,
} from '../domain/policies.js';
import { deriveAliasKeys, deriveContentFreeKeys } from '../domain/opaque.js';
import {
  decodeAccountFence,
  decodeAccountDeletionReceipt,
  decodeBudget,
  decodeClaim,
  decodeClaimRequestRecord,
  decodeCoordinationOperation,
  decodeCoordinationOperationReceipt,
  decodeDestinationGuard,
  decodeGlobalControl,
  decodeInstallation,
  decodeOccurrenceKey,
  decodeOutcome,
  decodePresence,
  decodeTombstone,
  encodeDocument,
} from '../persistence/codecs.js';
import {
  accountPaths,
  deletionReceiptPath,
  globalControlPath,
} from '../persistence/paths.js';
import type {
  AccountModeRequest,
  ArmRequest,
  BirthdayClaimRequest,
  ContactDerivedResetRequest,
  CoordinationLifecycleStatusRequest,
  DeletionRequest,
  DeletionReceiptRequest,
  LeaseRequest,
  RegistrationRequest,
  RetryRequest,
  SenderReleaseRequest,
  TestClaimRequest,
  TestReportRequest,
  TransferRequest,
} from '../transport/schemas.js';

type Decoder<T> = (value: unknown) => T | null;

class LedgerCorruptError extends Error {
  public constructor() {
    super('LEDGER_CORRUPT');
    this.name = 'LedgerCorruptError';
  }
}

function decoded<T>(snapshot: DocumentSnapshot, decoder: Decoder<T>): T | null {
  if (!snapshot.exists) {
    return null;
  }
  const result = decoder(snapshot.data());
  if (result === null) {
    throw new LedgerCorruptError();
  }
  return result;
}

function setDocument(
  transaction: Transaction,
  reference: DocumentReference,
  value: object,
): void {
  transaction.set(reference, encodeDocument(value));
}

function presenceFor(
  ledgerGeneration: string,
  state: CoordinationPresence['state'],
  nowMs: number,
): CoordinationPresence {
  return {
    schemaVersion: SCHEMA_VERSION,
    state,
    ledgerGeneration,
    updatedAtMs: nowMs,
  };
}

function isPresenceConsistent(
  presence: CoordinationPresence | null,
  control: GlobalControl | null,
): boolean {
  return (
    presence !== null &&
    control !== null &&
    presence.ledgerGeneration === control.ledgerGeneration
  );
}

export type CoordinationOperationResponse =
  | {
      readonly kind: 'IN_PROGRESS';
      readonly operation: CoordinationOperation['operation'];
      readonly stage: CoordinationOperation['stage'];
      readonly androidStateExisted: boolean;
      readonly senderEpochAfter?: number | undefined;
      readonly resetGenerationAfter?: number | undefined;
      readonly birthdayAutomationNotBeforeMs?: number | undefined;
      readonly drainUntilMs?: number | undefined;
    }
  | {
      readonly kind: 'COMPLETED';
      readonly operation: 'CONTACT_DERIVED_RESET';
      readonly androidStateExisted: true;
      readonly senderEpochAfter: number;
      readonly resetGenerationAfter: number;
      readonly birthdayAutomationNotBeforeMs: number;
      readonly contactDerivedStateErased: true;
      readonly firebaseAuthPreserved: true;
      readonly completedAtMs: number;
    }
  | {
      readonly kind: 'COMPLETED';
      readonly operation: 'CONTACT_DERIVED_RESET';
      readonly androidStateExisted: false;
      readonly contactDerivedStateErased: true;
      readonly firebaseAuthPreserved: true;
      readonly completedAtMs: number;
    }
  | {
      readonly kind: 'COMPLETED';
      readonly operation: 'SENDER_RELEASE';
      readonly androidStateExisted: true;
      readonly senderEpochAfter: number;
      readonly resetGenerationAfter: number;
      readonly androidSenderStateErased: true;
      readonly firebaseAuthPreserved: true;
      readonly completedAtMs: number;
    }
  | { readonly kind: 'REFUSED'; readonly reason: OperationRefusalReason };

type CoordinationCompletion = Extract<
  CoordinationOperationResponse,
  { readonly kind: 'COMPLETED' }
>;

export type CoordinationLifecycleStatusResponse =
  | {
      readonly kind: 'OPERATION_IN_PROGRESS';
      readonly serverNowMs: number;
      readonly operation: CoordinationOperation['operation'];
      readonly stage: CoordinationOperation['stage'];
      readonly androidStateExisted: boolean;
      readonly senderEpochAfter?: number | undefined;
      readonly resetGenerationAfter?: number | undefined;
      readonly birthdayAutomationNotBeforeMs?: number | undefined;
      readonly drainUntilMs?: number | undefined;
    }
  | {
      readonly kind: 'ACCOUNT_DELETION_IN_PROGRESS';
      readonly serverNowMs: number;
      readonly stage: DeletionTombstone['stage'];
      readonly drainUntilMs: number;
    }
  | {
      readonly kind: 'ANDROID_STATE';
      readonly serverNowMs: number;
      readonly mode: Exclude<AccountFence['mode'], 'DELETING'>;
      readonly activeInstallationId: string;
      readonly senderEpoch: number;
      readonly resetGeneration: number;
      readonly ownerLeaseUntilMs: number;
      readonly latestIssuedSubmitNotAfterMs: number;
      readonly birthdayAutomationNotBeforeMs: number;
      readonly transferTargetInstallationId?: string | undefined;
      readonly transferDrainUntilMs?: number | undefined;
      readonly latestCompletion?: CoordinationCompletion | undefined;
    }
  | {
      readonly kind: 'NO_ANDROID_STATE';
      readonly serverNowMs: number;
      readonly latestCompletion?: CoordinationCompletion | undefined;
    }
  | {
      readonly kind: 'SAFETY_STATUS_UNAVAILABLE';
      readonly serverNowMs: number;
    };


interface OptionalOperationResultFields {
  readonly senderEpochAfter?: number | undefined;
  readonly resetGenerationAfter?: number | undefined;
  readonly birthdayAutomationNotBeforeMs?: number | undefined;
}

function optionalOperationResultFields(
  value: CoordinationOperation,
): OptionalOperationResultFields {
  return {
    ...(value.senderEpochAfter === undefined
      ? {}
      : { senderEpochAfter: value.senderEpochAfter }),
    ...(value.resetGenerationAfter === undefined
      ? {}
      : { resetGenerationAfter: value.resetGenerationAfter }),
    ...(value.birthdayAutomationNotBeforeMs === undefined
      ? {}
      : {
          birthdayAutomationNotBeforeMs: value.birthdayAutomationNotBeforeMs,
        }),
  };
}

function inProgressResponse(
  operation: CoordinationOperation,
): CoordinationOperationResponse {
  return {
    kind: 'IN_PROGRESS',
    operation: operation.operation,
    stage: operation.stage,
    androidStateExisted: operation.androidStateExisted,
    ...optionalOperationResultFields(operation),
    ...(operation.drainUntilMs === undefined
      ? {}
      : { drainUntilMs: operation.drainUntilMs }),
  };
}

function completedResponse(
  receipt: CoordinationOperationReceipt,
): CoordinationCompletion {
  if (receipt.operation === 'CONTACT_DERIVED_RESET') {
    if (!receipt.androidStateExisted) {
      return {
        kind: 'COMPLETED',
        operation: 'CONTACT_DERIVED_RESET',
        androidStateExisted: false,
        contactDerivedStateErased: true,
        firebaseAuthPreserved: true,
        completedAtMs: receipt.completedAtMs,
      };
    }
    return {
      kind: 'COMPLETED',
      operation: 'CONTACT_DERIVED_RESET',
      androidStateExisted: true,
      senderEpochAfter: receipt.senderEpochAfter,
      resetGenerationAfter: receipt.resetGenerationAfter,
      birthdayAutomationNotBeforeMs: receipt.birthdayAutomationNotBeforeMs,
      contactDerivedStateErased: true,
      firebaseAuthPreserved: true,
      completedAtMs: receipt.completedAtMs,
    };
  }
  return {
    kind: 'COMPLETED',
    operation: 'SENDER_RELEASE',
    androidStateExisted: true,
    senderEpochAfter: receipt.senderEpochAfter,
    resetGenerationAfter: receipt.resetGenerationAfter,
    androidSenderStateErased: true,
    firebaseAuthPreserved: true,
    completedAtMs: receipt.completedAtMs,
  };
}

function isCoordinationMutationBlocked(
  operation: CoordinationOperation | null,
): boolean {
  return operation !== null;
}

export class ControlPlaneService {
  public constructor(
    private readonly db: Firestore,
    private readonly keyRing?: KeyRing,
    private readonly clock: () => number = () => Timestamp.now().toMillis(),
  ) {}

  private keys(): KeyRing {
    if (this.keyRing === undefined) {
      throw new Error('HMAC_KEYRING_UNAVAILABLE');
    }
    return this.keyRing;
  }

  private async deleteQuery(query: Query): Promise<void> {
    for (;;) {
      const snapshot = await query.limit(400).get();
      if (snapshot.empty) {
        return;
      }
      const batch = this.db.batch();
      for (const document of snapshot.docs) {
        batch.delete(document.ref);
      }
      await batch.commit();
    }
  }

  private async purgeContactDerivedState(uid: string): Promise<void> {
    const paths = accountPaths(this.db, uid);
    await this.deleteQuery(paths.occurrenceClaims);
    await this.deleteQuery(paths.occurrenceKeys);
    await this.deleteQuery(paths.destinationGuards);
    await this.deleteQuery(
      paths.claimRequests.where('purpose', '==', 'BIRTHDAY'),
    );
    await this.deleteQuery(
      paths.armOutcomes.where('purpose', '==', 'BIRTHDAY'),
    );
  }

  private async contactDerivedStateIsAbsent(uid: string): Promise<boolean> {
    const paths = accountPaths(this.db, uid);
    const snapshots = await Promise.all([
      paths.occurrenceClaims.limit(1).get(),
      paths.occurrenceKeys.limit(1).get(),
      paths.destinationGuards.limit(1).get(),
      paths.claimRequests.where('purpose', '==', 'BIRTHDAY').limit(1).get(),
      paths.armOutcomes.where('purpose', '==', 'BIRTHDAY').limit(1).get(),
    ]);
    return snapshots.every(snapshot => snapshot.empty);
  }

  private async accountTreeIsAbsent(uid: string): Promise<boolean> {
    const account = accountPaths(this.db, uid).account;
    if ((await account.get()).exists) {
      return false;
    }
    for (const collection of await account.listCollections()) {
      if (!(await collection.limit(1).get()).empty) {
        return false;
      }
    }
    return true;
  }

  public async requestContactDerivedReset(
    uid: string,
    request: ContactDerivedResetRequest,
  ): Promise<CoordinationOperationResponse> {
    const identity = deriveOperationIdentity(
      uid,
      'CONTACT_DERIVED_RESET',
      request.requestId,
    );
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    const decision = await this.db.runTransaction(async transaction => {
      const globalSnapshot = await transaction.get(globalControlPath(this.db));
      const receiptSnapshot = await transaction.get(
        paths.operationReceipt(identity.requestKey),
      );
      const operationSnapshot = await transaction.get(paths.operation);
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const fenceSnapshot = await transaction.get(paths.account);
      const presenceSnapshot = await transaction.get(paths.presence);
      const control = decoded(globalSnapshot, decodeGlobalControl);
      let receipt = decoded(
        receiptSnapshot,
        decodeCoordinationOperationReceipt,
      );
      if (receipt !== null && receipt.cleanupAtMs <= nowMs) {
        receipt = null;
      }
      const existingOperation = decoded(
        operationSnapshot,
        decodeCoordinationOperation,
      );
      const tombstone = decoded(tombstoneSnapshot, decodeTombstone);
      const fence = decoded(fenceSnapshot, decodeAccountFence);
      const presence = decoded(presenceSnapshot, decodePresence);
      const activeReference =
        fence === null ? null : paths.installation(fence.activeInstallationId);
      const activeInstallation =
        activeReference === null
          ? null
          : decoded(await transaction.get(activeReference), decodeInstallation);
      if (receipt !== null) {
        // A completed privacy operation is immutable proof, not a new Android
        // mutation. Preserve exact replay even if iOS acquired its fence after
        // the original response was lost. The reservation was still read in
        // this transaction; every new or still-active operation remains gated
        // below.
        return decideBeginContactDerivedReset(
          existingOperation,
          receipt,
          tombstone,
          fence,
          activeInstallation,
          identity,
          nowMs,
        );
      }
      if (
        existingOperation === null &&
        tombstone === null &&
        fence?.mode !== 'DELETING' &&
        ((fence === null && presence !== null) ||
          (fence !== null &&
            (presence?.state !== 'ANDROID_STATE' ||
              !isPresenceConsistent(presence, control))))
      ) {
        return { kind: 'REFUSED', reason: 'CONTINUITY_UNAVAILABLE' } as const;
      }
      const next = decideBeginContactDerivedReset(
        existingOperation,
        receipt,
        tombstone,
        fence,
        activeInstallation,
        identity,
        nowMs,
      );
      if (next.kind === 'STARTED') {
        setDocument(transaction, paths.operation, next.operation);
        if (next.fence !== null) {
          setDocument(transaction, paths.account, next.fence);
        }
        if (activeReference !== null && next.activeInstallation !== null) {
          setDocument(transaction, activeReference, next.activeInstallation);
        }
      }
      return next;
    });
    if (decision.kind === 'REFUSED') {
      return decision;
    }
    if (decision.kind === 'COMPLETED') {
      return completedResponse(decision.receipt);
    }
    return this.advanceCoordinationOperation(uid, identity.requestKey);
  }

  public async requestSenderRelease(
    uid: string,
    request: SenderReleaseRequest,
  ): Promise<CoordinationOperationResponse> {
    const identity = deriveOperationIdentity(
      uid,
      'SENDER_RELEASE',
      request.requestId,
      [
        request.installationId,
        String(request.senderEpoch),
        String(request.resetGeneration),
      ],
    );
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    const decision = await this.db.runTransaction(async transaction => {
      const globalSnapshot = await transaction.get(globalControlPath(this.db));
      const receiptSnapshot = await transaction.get(
        paths.operationReceipt(identity.requestKey),
      );
      const operationSnapshot = await transaction.get(paths.operation);
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const fenceSnapshot = await transaction.get(paths.account);
      const presenceSnapshot = await transaction.get(paths.presence);
      const control = decoded(globalSnapshot, decodeGlobalControl);
      let receipt = decoded(
        receiptSnapshot,
        decodeCoordinationOperationReceipt,
      );
      if (receipt !== null && receipt.cleanupAtMs <= nowMs) {
        receipt = null;
      }
      const existingOperation = decoded(
        operationSnapshot,
        decodeCoordinationOperation,
      );
      const tombstone = decoded(tombstoneSnapshot, decodeTombstone);
      const fence = decoded(fenceSnapshot, decodeAccountFence);
      const presence = decoded(presenceSnapshot, decodePresence);
      const activeReference =
        fence === null ? null : paths.installation(fence.activeInstallationId);
      const activeInstallation =
        activeReference === null
          ? null
          : decoded(await transaction.get(activeReference), decodeInstallation);
      if (receipt !== null) {
        // Exact completion replay is a read-only privacy proof. It must remain
        // available after iOS acquires the account fence; a changed request
        // fingerprint is still refused by the domain replay check.
        return decideBeginSenderRelease(
          existingOperation,
          receipt,
          tombstone,
          fence,
          activeInstallation,
          request,
          identity,
          nowMs,
        );
      }
      if (
        existingOperation === null &&
        tombstone === null &&
        fence?.mode !== 'DELETING' &&
        ((fence === null && presence !== null) ||
          (fence !== null &&
            (presence?.state !== 'ANDROID_STATE' ||
              !isPresenceConsistent(presence, control))))
      ) {
        return { kind: 'REFUSED', reason: 'CONTINUITY_UNAVAILABLE' } as const;
      }
      const next = decideBeginSenderRelease(
        existingOperation,
        receipt,
        tombstone,
        fence,
        activeInstallation,
        request,
        identity,
        nowMs,
      );
      if (next.kind === 'STARTED') {
        setDocument(transaction, paths.operation, next.operation);
        if (next.fence !== null) {
          setDocument(transaction, paths.account, next.fence);
        }
      }
      return next;
    });
    if (decision.kind === 'REFUSED') {
      return decision;
    }
    if (decision.kind === 'COMPLETED') {
      return completedResponse(decision.receipt);
    }
    return this.advanceCoordinationOperation(uid, identity.requestKey);
  }

  private async advanceContactResetDrain(
    uid: string,
  ): Promise<CoordinationOperation | null> {
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    return this.db.runTransaction(async transaction => {
      const operationSnapshot = await transaction.get(paths.operation);
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const fenceSnapshot = await transaction.get(paths.account);
      const operation = decoded(operationSnapshot, decodeCoordinationOperation);
      const fence = decoded(fenceSnapshot, decodeAccountFence);
      if (operation?.stage !== 'RESET_DRAINING') {
        return operation;
      }
      if (decoded(tombstoneSnapshot, decodeTombstone) !== null) {
        throw new LedgerCorruptError();
      }
      const activeReference =
        fence === null ? null : paths.installation(fence.activeInstallationId);
      const activeInstallation =
        activeReference === null
          ? null
          : decoded(await transaction.get(activeReference), decodeInstallation);
      const decision = decideAdvanceContactDerivedReset(
        operation,
        fence,
        activeInstallation,
        nowMs,
      );
      if (decision.kind === 'WAIT') {
        return operation;
      }
      if (decision.kind !== 'READY_TO_PURGE') {
        throw new LedgerCorruptError();
      }
      setDocument(transaction, paths.operation, decision.operation);
      return decision.operation;
    });
  }

  private async advanceSenderReleaseDrain(
    uid: string,
  ): Promise<CoordinationOperation | null> {
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    return this.db.runTransaction(async transaction => {
      const operationSnapshot = await transaction.get(paths.operation);
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const fenceSnapshot = await transaction.get(paths.account);
      const operation = decoded(operationSnapshot, decodeCoordinationOperation);
      const fence = decoded(fenceSnapshot, decodeAccountFence);
      if (operation?.stage !== 'RELEASE_DRAINING') {
        return operation;
      }
      if (decoded(tombstoneSnapshot, decodeTombstone) !== null) {
        throw new LedgerCorruptError();
      }
      const activeReference =
        fence === null ? null : paths.installation(fence.activeInstallationId);
      const activeInstallation =
        activeReference === null
          ? null
          : decoded(await transaction.get(activeReference), decodeInstallation);
      const decision = decideAdvanceSenderRelease(
        operation,
        fence,
        activeInstallation,
        nowMs,
      );
      if (decision.kind === 'WAIT') {
        return operation;
      }
      if (decision.kind !== 'READY_TO_PURGE' || activeReference === null) {
        throw new LedgerCorruptError();
      }
      setDocument(transaction, paths.operation, decision.operation);
      setDocument(transaction, paths.account, decision.fence);
      setDocument(transaction, activeReference, decision.activeInstallation);
      return decision.operation;
    });
  }

  private async finalizeReset(
    uid: string,
    expected: CoordinationOperation,
  ): Promise<CoordinationOperationReceipt> {
    const paths = accountPaths(this.db, uid);
    return this.db.runTransaction(async transaction => {
      const operationSnapshot = await transaction.get(paths.operation);
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const fenceSnapshot = await transaction.get(paths.account);
      const receiptReference = paths.operationReceipt(expected.requestKey);
      const receiptSnapshot = await transaction.get(receiptReference);
      const existingReceipt = decoded(
        receiptSnapshot,
        decodeCoordinationOperationReceipt,
      );
      if (
        existingReceipt !== null &&
        existingReceipt.cleanupAtMs > this.clock()
      ) {
        return existingReceipt;
      }
      const operation = decoded(operationSnapshot, decodeCoordinationOperation);
      if (
        operation?.operation !== 'CONTACT_DERIVED_RESET' ||
        operation.stage !== 'RESET_PURGING' ||
        operation.requestKey !== expected.requestKey ||
        decoded(tombstoneSnapshot, decodeTombstone) !== null
      ) {
        throw new LedgerCorruptError();
      }
      if (operation.androidStateExisted) {
        const fence = decoded(fenceSnapshot, decodeAccountFence);
        if (
          fence?.mode !== 'PAUSED_REPAIR' ||
          fence.senderEpoch !== operation.senderEpochAfter ||
          fence.resetGeneration !== operation.resetGenerationAfter ||
          fence.birthdayAutomationNotBeforeMs !==
            operation.birthdayAutomationNotBeforeMs
        ) {
          throw new LedgerCorruptError();
        }
        const activeInstallation = decoded(
          await transaction.get(paths.installation(fence.activeInstallationId)),
          decodeInstallation,
        );
        if (
          activeInstallation?.state !== 'ACTIVE' ||
          activeInstallation.epoch !== fence.senderEpoch
        ) {
          throw new LedgerCorruptError();
        }
      }
      const receipt = makeCoordinationOperationReceipt(operation, this.clock());
      setDocument(transaction, receiptReference, receipt);
      setDocument(transaction, paths.latestOperationReceipt, receipt);
      transaction.delete(paths.operation);
      return receipt;
    });
  }

  private async finalizeSenderRelease(
    uid: string,
    expected: CoordinationOperation,
  ): Promise<CoordinationOperationReceipt> {
    const paths = accountPaths(this.db, uid);
    return this.db.runTransaction(async transaction => {
      const operationSnapshot = await transaction.get(paths.operation);
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const accountSnapshot = await transaction.get(paths.account);
      const receiptReference = paths.operationReceipt(expected.requestKey);
      const receiptSnapshot = await transaction.get(receiptReference);
      const existingReceipt = decoded(
        receiptSnapshot,
        decodeCoordinationOperationReceipt,
      );
      if (
        existingReceipt !== null &&
        existingReceipt.cleanupAtMs > this.clock()
      ) {
        return existingReceipt;
      }
      const operation = decoded(operationSnapshot, decodeCoordinationOperation);
      if (
        operation?.operation !== 'SENDER_RELEASE' ||
        operation.stage !== 'RELEASE_PURGING' ||
        operation.requestKey !== expected.requestKey ||
        decoded(tombstoneSnapshot, decodeTombstone) !== null ||
        accountSnapshot.exists
      ) {
        throw new LedgerCorruptError();
      }
      const receipt = makeCoordinationOperationReceipt(operation, this.clock());
      setDocument(transaction, receiptReference, receipt);
      setDocument(transaction, paths.latestOperationReceipt, receipt);
      transaction.delete(paths.presence);
      transaction.delete(paths.operation);
      return receipt;
    });
  }

  public async advanceCoordinationOperation(
    uid: string,
    expectedRequestKey?: string,
  ): Promise<CoordinationOperationResponse> {
    const paths = accountPaths(this.db, uid);
    let operation = decoded(
      await paths.operation.get(),
      decodeCoordinationOperation,
    );
    if (operation === null) {
      if (expectedRequestKey === undefined) {
        return { kind: 'REFUSED', reason: 'REQUEST_MISMATCH' };
      }
      const receipt = decoded(
        await paths.operationReceipt(expectedRequestKey).get(),
        decodeCoordinationOperationReceipt,
      );
      return receipt === null || receipt.cleanupAtMs <= this.clock()
        ? { kind: 'REFUSED', reason: 'REQUEST_MISMATCH' }
        : completedResponse(receipt);
    }
    if (
      expectedRequestKey !== undefined &&
      operation.requestKey !== expectedRequestKey
    ) {
      return { kind: 'REFUSED', reason: 'COORDINATION_OPERATION_IN_PROGRESS' };
    }
    if (operation.stage === 'RESET_DRAINING') {
      operation = await this.advanceContactResetDrain(uid);
      if (operation === null) {
        throw new LedgerCorruptError();
      }
      if (operation.stage === 'RESET_DRAINING') {
        return inProgressResponse(operation);
      }
    }
    if (operation.stage === 'RESET_PURGING') {
      await this.purgeContactDerivedState(uid);
      if (!(await this.contactDerivedStateIsAbsent(uid))) {
        return inProgressResponse(operation);
      }
      return completedResponse(await this.finalizeReset(uid, operation));
    }
    if (operation.stage === 'RELEASE_DRAINING') {
      operation = await this.advanceSenderReleaseDrain(uid);
      if (operation === null) {
        throw new LedgerCorruptError();
      }
      if (operation.stage === 'RELEASE_DRAINING') {
        return inProgressResponse(operation);
      }
    }
    if (operation.stage !== 'RELEASE_PURGING') {
      throw new LedgerCorruptError();
    }
    await this.db.recursiveDelete(paths.account);
    if (!(await this.accountTreeIsAbsent(uid))) {
      return inProgressResponse(operation);
    }
    return completedResponse(await this.finalizeSenderRelease(uid, operation));
  }

  public async deferCoordinationOperation(
    uid: string,
    expectedRequestKey: string | null,
    minimumNextSweepAtMs: number,
  ): Promise<boolean> {
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    return this.db.runTransaction(async transaction => {
      const snapshot = await transaction.get(paths.operation);
      if (!snapshot.exists) {
        return false;
      }
      const rawData: unknown = snapshot.data();
      const data =
        typeof rawData === 'object' && rawData !== null
          ? (rawData as Readonly<Record<string, unknown>>)
          : {};
      const currentRequestKey = data.requestKey;
      if (
        (expectedRequestKey !== null &&
          currentRequestKey !== expectedRequestKey) ||
        (expectedRequestKey === null &&
          typeof currentRequestKey === 'string' &&
          /^[a-f0-9]{64}$/u.test(currentRequestKey))
      ) {
        return false;
      }
      const currentAttempt =
        typeof data.sweepAttemptCount === 'number' &&
        Number.isSafeInteger(data.sweepAttemptCount) &&
        data.sweepAttemptCount >= 0
          ? data.sweepAttemptCount
          : 0;
      transaction.update(paths.operation, {
        sweepAttemptCount: boundedSweepAttempt(currentAttempt),
        nextSweepAtMs: Math.max(
          minimumNextSweepAtMs,
          nextRepairSweepAtMs(nowMs, currentAttempt),
        ),
      });
      return true;
    });
  }

  public async registerAndroidInstallation(
    uid: string,
    request: RegistrationRequest,
  ): Promise<ReturnType<typeof decideRegistration>> {
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    return this.db.runTransaction(async transaction => {
      const globalSnapshot = await transaction.get(globalControlPath(this.db));
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const operationSnapshot = await transaction.get(paths.operation);
      const fenceSnapshot = await transaction.get(paths.account);
      const presenceSnapshot = await transaction.get(paths.presence);
      const installationReference = paths.installation(request.installationId);
      const installationSnapshot = await transaction.get(installationReference);

      const control = decoded(globalSnapshot, decodeGlobalControl);
      const tombstone = decoded(tombstoneSnapshot, decodeTombstone);
      const operation = decoded(operationSnapshot, decodeCoordinationOperation);
      const fence = decoded(fenceSnapshot, decodeAccountFence);
      const presence = decoded(presenceSnapshot, decodePresence);
      const installation = decoded(installationSnapshot, decodeInstallation);

      if (isCoordinationMutationBlocked(operation)) {
        return { kind: 'SUPPRESSED', reason: 'RESET_SUPPRESSED' } as const;
      }

      if (
        (tombstone === null && fence === null && presence !== null) ||
        (tombstone === null &&
          fence !== null &&
          !isPresenceConsistent(presence, control))
      ) {
        return {
          kind: 'SUPPRESSED',
          reason: 'CONTINUITY_UNAVAILABLE',
        } as const;
      }

      const decision = decideRegistration(
        control,
        tombstone,
        fence,
        installation,
        request,
        nowMs,
      );
      if (decision.kind === 'REGISTERED_ACTIVE') {
        setDocument(transaction, paths.account, decision.fence);
        setDocument(transaction, installationReference, decision.installation);
        setDocument(
          transaction,
          paths.presence,
          presenceFor(request.ledgerGeneration, 'ANDROID_STATE', nowMs),
        );
      } else if (
        decision.kind === 'REGISTERED_STANDBY' ||
        decision.kind === 'REPLAYED'
      ) {
        setDocument(transaction, installationReference, decision.installation);
      }
      return decision;
    });
  }

  public async renewLease(
    uid: string,
    request: LeaseRequest,
  ): Promise<
    | { readonly kind: 'RENEWED'; readonly leaseUntilMs: number }
    | { readonly kind: 'REFUSED'; readonly reason: string }
  > {
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    return this.db.runTransaction(async transaction => {
      const globalSnapshot = await transaction.get(globalControlPath(this.db));
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const operationSnapshot = await transaction.get(paths.operation);
      const fenceSnapshot = await transaction.get(paths.account);
      const presenceSnapshot = await transaction.get(paths.presence);
      const installationReference = paths.installation(request.installationId);
      const installationSnapshot = await transaction.get(installationReference);
      const control = decoded(globalSnapshot, decodeGlobalControl);
      const tombstone = decoded(tombstoneSnapshot, decodeTombstone);
      const operation = decoded(operationSnapshot, decodeCoordinationOperation);
      const fence = decoded(fenceSnapshot, decodeAccountFence);
      const presence = decoded(presenceSnapshot, decodePresence);
      const installation = decoded(installationSnapshot, decodeInstallation);

      if (isCoordinationMutationBlocked(operation)) {
        return { kind: 'REFUSED', reason: 'RESET_SUPPRESSED' } as const;
      }
      if (tombstone !== null || fence?.mode === 'DELETING') {
        return { kind: 'REFUSED', reason: 'DELETION_SUPPRESSED' } as const;
      }
      if (fence === null || installation === null) {
        return { kind: 'REFUSED', reason: 'MISSING_FENCE' } as const;
      }
      if (!isPresenceConsistent(presence, control)) {
        return { kind: 'REFUSED', reason: 'CONTINUITY_UNAVAILABLE' } as const;
      }
      const compatibility = checkControlCompatibility(control, request);
      if (compatibility !== null) {
        return { kind: 'REFUSED', reason: compatibility } as const;
      }
      if (
        fence.activeInstallationId !== request.installationId ||
        fence.senderEpoch !== request.senderEpoch ||
        fence.resetGeneration !== request.resetGeneration ||
        installation.state !== 'ACTIVE' ||
        installation.epoch !== request.senderEpoch
      ) {
        return { kind: 'REFUSED', reason: 'BINDING_MISMATCH' } as const;
      }
      const modeAllowed =
        request.purpose === 'TEST'
          ? fence.mode === 'TEST_ONLY' || fence.mode === 'PAUSED_REPAIR'
          : fence.mode === 'AUTOMATION_ACTIVE';
      if (!modeAllowed) {
        return { kind: 'REFUSED', reason: 'MODE_BLOCKED' } as const;
      }
      const leaseUntilMs = renewedLeaseUntil(nowMs);
      setDocument(transaction, paths.account, {
        ...fence,
        ownerLeaseUntilMs: leaseUntilMs,
        updatedAtMs: nowMs,
      });
      setDocument(transaction, installationReference, {
        ...installation,
        appBuildNumber: request.appBuildNumber,
        policyVersion: request.policyVersion,
        distributionChannel: request.distributionChannel,
        lastSeenAtMs: nowMs,
      });
      return { kind: 'RENEWED', leaseUntilMs } as const;
    });
  }

  public async changeAccountMode(
    uid: string,
    request: AccountModeRequest,
  ): Promise<
    | { readonly kind: 'CHANGED'; readonly mode: AccountFence['mode'] }
    | { readonly kind: 'REFUSED'; readonly reason: string }
  > {
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    return this.db.runTransaction(async transaction => {
      const globalSnapshot = await transaction.get(globalControlPath(this.db));
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const operationSnapshot = await transaction.get(paths.operation);
      const fenceSnapshot = await transaction.get(paths.account);
      const presenceSnapshot = await transaction.get(paths.presence);
      const installationSnapshot = await transaction.get(
        paths.installation(request.installationId),
      );
      const testClaimSnapshot =
        request.action === 'ACTIVATE_AUTOMATION'
          ? await transaction.get(paths.claim('TEST', request.testClaimId))
          : null;
      const control = decoded(globalSnapshot, decodeGlobalControl);
      const tombstone = decoded(tombstoneSnapshot, decodeTombstone);
      const operation = decoded(operationSnapshot, decodeCoordinationOperation);
      const fence = decoded(fenceSnapshot, decodeAccountFence);
      const presence = decoded(presenceSnapshot, decodePresence);
      const installation = decoded(installationSnapshot, decodeInstallation);
      if (isCoordinationMutationBlocked(operation)) {
        return { kind: 'REFUSED', reason: 'RESET_SUPPRESSED' } as const;
      }
      if (tombstone !== null || fence?.mode === 'DELETING') {
        return { kind: 'REFUSED', reason: 'DELETION_SUPPRESSED' } as const;
      }
      if (
        fence === null ||
        installation === null ||
        fence.activeInstallationId !== request.installationId ||
        fence.senderEpoch !== request.senderEpoch ||
        fence.resetGeneration !== request.resetGeneration ||
        installation.state !== 'ACTIVE' ||
        installation.epoch !== request.senderEpoch
      ) {
        return { kind: 'REFUSED', reason: 'BINDING_MISMATCH' } as const;
      }
      if (request.action === 'PAUSE_FOR_REPAIR') {
        const mode = 'PAUSED_REPAIR' as const;
        setDocument(transaction, paths.account, {
          ...fence,
          mode,
          ownerLeaseUntilMs: nowMs,
          updatedAtMs: nowMs,
        });
        return { kind: 'CHANGED', mode } as const;
      }
      if (!isPresenceConsistent(presence, control)) {
        return { kind: 'REFUSED', reason: 'CONTINUITY_UNAVAILABLE' } as const;
      }
      const compatibility = checkGlobalControl(control, request);
      if (compatibility !== null) {
        return { kind: 'REFUSED', reason: compatibility } as const;
      }
      if (
        (fence.mode !== 'TEST_ONLY' && fence.mode !== 'PAUSED_REPAIR') ||
        fence.ownerLeaseUntilMs <= nowMs ||
        testClaimSnapshot === null
      ) {
        return {
          kind: 'REFUSED',
          reason: 'TEST_LEASE_OR_MODE_INVALID',
        } as const;
      }
      const testClaim = decoded(testClaimSnapshot, decodeClaim);
      if (
        testClaim?.claimId !== request.testClaimId ||
        testClaim.purpose !== 'TEST' ||
        testClaim.ownerInstallationId !== request.installationId ||
        testClaim.ownerEpoch !== request.senderEpoch ||
        testClaim.resetGeneration !== request.resetGeneration ||
        testClaim.state !== 'TERMINAL' ||
        testClaim.testBarrierOutcome !== 'SENT_ALL_PARTS_IN_WINDOW'
      ) {
        return {
          kind: 'REFUSED',
          reason: 'BOUND_TEST_RECEIPT_REQUIRED',
        } as const;
      }
      const mode = 'AUTOMATION_ACTIVE' as const;
      setDocument(transaction, paths.account, {
        ...fence,
        mode,
        updatedAtMs: nowMs,
      });
      return { kind: 'CHANGED', mode } as const;
    });
  }

  public async claimBirthdayOccurrence(
    uid: string,
    request: BirthdayClaimRequest,
  ): Promise<ReturnType<typeof decideClaim>> {
    const occurrenceAliasKeys = deriveAliasKeys(
      this.keys(),
      uid,
      'BIRTHDAY',
      'RECIPIENT',
      request.recipientPrehashAliases,
    );
    const destinationAliasKeys = deriveAliasKeys(
      this.keys(),
      uid,
      'BIRTHDAY',
      'DESTINATION',
      request.destinationPrehashAliases,
    );
    return this.claim(uid, request, {
      purpose: 'BIRTHDAY',
      claimRequestId: request.claimRequestId,
      claimId: request.claimRequestId,
      requestKey: request.claimRequestId,
      occurrenceAliasKeys,
      destinationAliasKeys,
      testMaterialAliasKeys: [],
    });
  }

  public async claimTest(
    uid: string,
    request: TestClaimRequest,
  ): Promise<ReturnType<typeof decideClaim>> {
    const material = `${request.testRequestId}:${request.testConfigurationPrehash}:${request.testDestinationPrehash}`;
    return this.claim(uid, request, {
      purpose: 'TEST',
      claimRequestId: request.testRequestId,
      claimId: request.testRequestId,
      requestKey: request.testRequestId,
      occurrenceAliasKeys: [],
      destinationAliasKeys: [],
      testMaterialAliasKeys: deriveContentFreeKeys(
        this.keys(),
        uid,
        'TEST_MATERIAL',
        material,
      ),
    });
  }

  private async claim(
    uid: string,
    request: BirthdayClaimRequest | TestClaimRequest,
    identity: Pick<
      ClaimInput,
      | 'purpose'
      | 'claimRequestId'
      | 'claimId'
      | 'requestKey'
      | 'occurrenceAliasKeys'
      | 'destinationAliasKeys'
      | 'testMaterialAliasKeys'
    >,
  ): Promise<ReturnType<typeof decideClaim>> {
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    const input: ClaimInput = {
      ledgerGeneration: request.ledgerGeneration,
      installationId: request.installationId,
      senderEpoch: request.senderEpoch,
      resetGeneration: request.resetGeneration,
      appBuildNumber: request.appBuildNumber,
      policyVersion: request.policyVersion,
      distributionChannel: request.distributionChannel,
      ...identity,
    };
    return this.db.runTransaction(async transaction => {
      const globalSnapshot = await transaction.get(globalControlPath(this.db));
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const operationSnapshot = await transaction.get(paths.operation);
      const fenceSnapshot = await transaction.get(paths.account);
      const presenceSnapshot = await transaction.get(paths.presence);
      const installationSnapshot = await transaction.get(
        paths.installation(input.installationId),
      );
      if (
        isCoordinationMutationBlocked(
          decoded(operationSnapshot, decodeCoordinationOperation),
        )
      ) {
        return { kind: 'REFUSED', reason: 'RESET_SUPPRESSED' } as const;
      }
      const requestReference = paths.request(input.requestKey);
      const requestSnapshot = await transaction.get(requestReference);
      const requestRecord = decoded(requestSnapshot, decodeClaimRequestRecord);
      let requestClaim = decoded(
        await transaction.get(paths.claim(input.purpose, input.claimId)),
        decodeClaim,
      );
      if (requestRecord !== null) {
        if (
          requestClaim?.claimId !== requestRecord.linkedClaimId ||
          requestClaim.purpose !== requestRecord.purpose
        ) {
          requestClaim = decoded(
            await transaction.get(
              paths.claim(requestRecord.purpose, requestRecord.linkedClaimId),
            ),
            decodeClaim,
          );
        }
      }
      const occurrenceKeys = [];
      for (const aliasKey of input.occurrenceAliasKeys) {
        const key = decoded(
          await transaction.get(paths.occurrenceKey(aliasKey)),
          decodeOccurrenceKey,
        );
        if (key !== null) {
          occurrenceKeys.push(key);
        }
      }
      const destinationGuards = [];
      for (const aliasKey of input.destinationAliasKeys) {
        const guard = decoded(
          await transaction.get(paths.destinationGuard(aliasKey)),
          decodeDestinationGuard,
        );
        if (guard !== null) {
          destinationGuards.push(guard);
        }
      }

      const control = decoded(globalSnapshot, decodeGlobalControl);
      const presence = decoded(presenceSnapshot, decodePresence);
      const tombstone = decoded(tombstoneSnapshot, decodeTombstone);
      const collisions: ClaimCollisionSnapshot = {
        requestRecord,
        requestClaim,
        occurrenceKeys,
        destinationGuards,
      };
      if (tombstone === null && !isPresenceConsistent(presence, control)) {
        return { kind: 'REFUSED', reason: 'CONTINUITY_UNAVAILABLE' } as const;
      }
      const decision = decideClaim(
        control,
        tombstone,
        decoded(fenceSnapshot, decodeAccountFence),
        decoded(installationSnapshot, decodeInstallation),
        collisions,
        input,
        nowMs,
      );
      if (decision.kind === 'CLAIMED') {
        setDocument(
          transaction,
          paths.claim(decision.claim.purpose, decision.claim.claimId),
          decision.claim,
        );
        setDocument(transaction, requestReference, decision.requestRecord);
        for (const key of decision.occurrenceKeys) {
          setDocument(transaction, paths.occurrenceKey(key.aliasKey), key);
        }
        for (const guard of decision.destinationGuards) {
          setDocument(
            transaction,
            paths.destinationGuard(guard.aliasKey),
            guard,
          );
        }
      }
      return decision;
    });
  }

  public async armAttempt(
    uid: string,
    request: ArmRequest,
  ): Promise<ArmDecision> {
    return this.resolveArm(uid, request, false);
  }

  public async getArmStatus(
    uid: string,
    request: ArmRequest,
  ): Promise<ReturnType<typeof decideArmStatus>> {
    return this.resolveArm(uid, request, true);
  }

  private async resolveArm(
    uid: string,
    request: ArmRequest,
    statusOnly: false,
  ): Promise<ArmDecision>;
  private async resolveArm(
    uid: string,
    request: ArmRequest,
    statusOnly: true,
  ): Promise<ReturnType<typeof decideArmStatus>>;
  private async resolveArm(
    uid: string,
    request: ArmRequest,
    statusOnly: boolean,
  ): Promise<ArmDecision | ReturnType<typeof decideArmStatus>> {
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    const outcomeKey = request.armRequestId;
    const input: ArmInput = request;
    return this.db.runTransaction(async transaction => {
      const outcomeReference = paths.outcome(outcomeKey);
      const outcomeSnapshot = await transaction.get(outcomeReference);
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const operationSnapshot = await transaction.get(paths.operation);
      const fenceSnapshot = await transaction.get(paths.account);
      const presenceSnapshot = await transaction.get(paths.presence);
      const globalSnapshot = await transaction.get(globalControlPath(this.db));
      const installationSnapshot = await transaction.get(
        paths.installation(request.installationId),
      );
      const claimReference = paths.claim(request.purpose, request.claimId);
      const claimSnapshot = await transaction.get(claimReference);
      const budgetReference = paths.budget(request.purpose);
      const budgetSnapshot = await transaction.get(budgetReference);

      const outcome = decoded(outcomeSnapshot, decodeOutcome);
      const claim = decoded(claimSnapshot, decodeClaim);
      if (
        isCoordinationMutationBlocked(
          decoded(operationSnapshot, decodeCoordinationOperation),
        )
      ) {
        return { kind: 'SUPPRESSED', reason: 'RESET_SUPPRESSED' } as const;
      }
      if (
        outcome !== null &&
        (outcome.armRequestId !== request.armRequestId ||
          outcome.claimId !== request.claimId)
      ) {
        throw new LedgerCorruptError();
      }
      const occurrenceKeys = [];
      const destinationGuards = [];
      for (const aliasKey of claim?.occurrenceAliasKeys ?? []) {
        const key = decoded(
          await transaction.get(paths.occurrenceKey(aliasKey)),
          decodeOccurrenceKey,
        );
        if (key !== null) {
          occurrenceKeys.push(key);
        }
      }
      for (const aliasKey of claim?.destinationAliasKeys ?? []) {
        const guard = decoded(
          await transaction.get(paths.destinationGuard(aliasKey)),
          decodeDestinationGuard,
        );
        if (guard !== null) {
          destinationGuards.push(guard);
        }
      }

      const control = decoded(globalSnapshot, decodeGlobalControl);
      const presence = decoded(presenceSnapshot, decodePresence);
      const snapshot: ArmSnapshot = {
        outcome,
        tombstone: decoded(tombstoneSnapshot, decodeTombstone),
        fence: decoded(fenceSnapshot, decodeAccountFence),
        installation: decoded(installationSnapshot, decodeInstallation),
        claim,
        occurrenceKeys,
        destinationGuards,
        budget: decoded(budgetSnapshot, decodeBudget),
      };
      if (
        outcome === null &&
        snapshot.tombstone === null &&
        snapshot.fence?.mode !== 'DELETING' &&
        !isPresenceConsistent(presence, control)
      ) {
        return { kind: 'SUPPRESSED', reason: 'UNKNOWN_HISTORY' } as const;
      }
      const decision = statusOnly
        ? decideArmStatus(snapshot, input, nowMs)
        : decideArm(control, snapshot, input, nowMs);
      this.applyArmDecision(
        transaction,
        paths,
        claim,
        occurrenceKeys,
        outcomeReference,
        claimReference,
        budgetReference,
        decision,
        nowMs,
      );
      return decision;
    });
  }

  private applyArmDecision(
    transaction: Transaction,
    paths: ReturnType<typeof accountPaths>,
    originalClaim: Claim | null,
    occurrenceKeys: readonly ReturnType<typeof decodeOccurrenceKey>[],
    outcomeReference: DocumentReference,
    claimReference: DocumentReference,
    budgetReference: DocumentReference,
    decision: ArmDecision | ReturnType<typeof decideArmStatus>,
    nowMs: number,
  ): void {
    if (decision.kind === 'NO_WRITE') {
      setDocument(transaction, outcomeReference, decision.outcome);
      if (decision.claim !== undefined) {
        setDocument(transaction, claimReference, decision.claim);
      }
      if (decision.destinationGuards !== undefined) {
        for (const guard of decision.destinationGuards) {
          setDocument(
            transaction,
            paths.destinationGuard(guard.aliasKey),
            guard,
          );
        }
      }
      if (decision.occurrenceKeyState !== undefined && originalClaim !== null) {
        for (const key of occurrenceKeys) {
          if (key !== null) {
            setDocument(transaction, paths.occurrenceKey(key.aliasKey), {
              ...key,
              state: decision.occurrenceKeyState,
              updatedAtMs: nowMs,
              cleanupAtMs: Math.max(
                key.cleanupAtMs,
                decision.outcome.cleanupAtMs,
              ),
            });
          }
        }
      }
      return;
    }
    if (decision.kind !== 'ARMED') {
      return;
    }
    setDocument(transaction, outcomeReference, decision.outcome);
    setDocument(transaction, paths.account, decision.fence);
    setDocument(transaction, claimReference, decision.claim);
    setDocument(transaction, budgetReference, decision.budget);
    for (const guard of decision.destinationGuards) {
      setDocument(transaction, paths.destinationGuard(guard.aliasKey), guard);
    }
    for (const key of occurrenceKeys) {
      if (key !== null) {
        setDocument(transaction, paths.occurrenceKey(key.aliasKey), {
          ...key,
          state: 'ARMED',
          updatedAtMs: nowMs,
          cleanupAtMs: decision.outcome.cleanupAtMs,
        });
      }
    }
  }

  public async authorizeSafeRetry(
    uid: string,
    request: RetryRequest,
  ): Promise<ReturnType<typeof decideSafeRetry>> {
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    return this.db.runTransaction(async transaction => {
      const globalSnapshot = await transaction.get(globalControlPath(this.db));
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const operationSnapshot = await transaction.get(paths.operation);
      const fenceSnapshot = await transaction.get(paths.account);
      const presenceSnapshot = await transaction.get(paths.presence);
      const installationSnapshot = await transaction.get(
        paths.installation(request.installationId),
      );
      const claimReference = paths.claim('BIRTHDAY', request.claimId);
      const claimSnapshot = await transaction.get(claimReference);
      const control = decoded(globalSnapshot, decodeGlobalControl);
      const presence = decoded(presenceSnapshot, decodePresence);
      const fence = decoded(fenceSnapshot, decodeAccountFence);
      const installation = decoded(installationSnapshot, decodeInstallation);
      const claim = decoded(claimSnapshot, decodeClaim);
      const tombstone = decoded(tombstoneSnapshot, decodeTombstone);
      if (
        isCoordinationMutationBlocked(
          decoded(operationSnapshot, decodeCoordinationOperation),
        )
      ) {
        return { kind: 'REFUSED', reason: 'RESET_SUPPRESSED' } as const;
      }
      if (tombstone === null && !isPresenceConsistent(presence, control)) {
        return { kind: 'REFUSED', reason: 'UNKNOWN_HISTORY' } as const;
      }
      const compatibility = checkControlCompatibility(control, request);
      if (compatibility !== null) {
        return { kind: 'REFUSED', reason: 'UNKNOWN_HISTORY' } as const;
      }
      if (
        fence === null ||
        installation === null ||
        fence.activeInstallationId !== request.installationId ||
        fence.senderEpoch !== request.senderEpoch ||
        fence.resetGeneration !== request.resetGeneration ||
        fence.mode !== 'AUTOMATION_ACTIVE' ||
        installation.state !== 'ACTIVE' ||
        installation.epoch !== request.senderEpoch
      ) {
        return { kind: 'REFUSED', reason: 'RESET_SUPPRESSED' } as const;
      }
      if (claim?.purpose !== 'BIRTHDAY') {
        return { kind: 'REFUSED', reason: 'UNKNOWN_HISTORY' } as const;
      }
      const decision = decideSafeRetry(
        fence,
        tombstone,
        claim,
        request.retryRequestId,
        request.proof,
        nowMs,
      );
      if (decision.kind === 'AUTHORIZED') {
        setDocument(transaction, claimReference, decision.claim);
      }
      return decision;
    });
  }

  public async reportTestOutcome(
    uid: string,
    request: TestReportRequest,
  ): Promise<ReturnType<typeof decideTestReport>> {
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    const outcomeKey = request.armRequestId;
    const input: TestReportInput = request;
    return this.db.runTransaction(async transaction => {
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const operationSnapshot = await transaction.get(paths.operation);
      const fenceSnapshot = await transaction.get(paths.account);
      const presenceSnapshot = await transaction.get(paths.presence);
      const installationSnapshot = await transaction.get(
        paths.installation(request.installationId),
      );
      const claimReference = paths.claim('TEST', request.testClaimId);
      const claimSnapshot = await transaction.get(claimReference);
      const outcomeSnapshot = await transaction.get(paths.outcome(outcomeKey));
      const presence = decoded(presenceSnapshot, decodePresence);
      if (
        isCoordinationMutationBlocked(
          decoded(operationSnapshot, decodeCoordinationOperation),
        )
      ) {
        return { kind: 'SUPPRESSED', reason: 'RESET_SUPPRESSED' } as const;
      }
      if (presence?.state !== 'ANDROID_STATE') {
        return { kind: 'SUPPRESSED', reason: 'UNKNOWN_HISTORY' } as const;
      }
      const decision = decideTestReport(
        decoded(fenceSnapshot, decodeAccountFence),
        decoded(tombstoneSnapshot, decodeTombstone),
        decoded(installationSnapshot, decodeInstallation),
        decoded(claimSnapshot, decodeClaim),
        decoded(outcomeSnapshot, decodeOutcome),
        input,
        nowMs,
      );
      if (decision.kind === 'RECORDED') {
        setDocument(transaction, claimReference, decision.claim);
      }
      return decision;
    });
  }

  public async beginTransfer(
    uid: string,
    request: TransferRequest,
  ): Promise<ReturnType<typeof decideBeginTransfer>> {
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    return this.db.runTransaction(async transaction => {
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const operationSnapshot = await transaction.get(paths.operation);
      const fenceSnapshot = await transaction.get(paths.account);
      const presenceSnapshot = await transaction.get(paths.presence);
      const activeSnapshot = await transaction.get(
        paths.installation(request.installationId),
      );
      const targetSnapshot = await transaction.get(
        paths.installation(request.targetInstallationId),
      );
      const fence = decoded(fenceSnapshot, decodeAccountFence);
      const presence = decoded(presenceSnapshot, decodePresence);
      const active = decoded(activeSnapshot, decodeInstallation);
      if (
        isCoordinationMutationBlocked(
          decoded(operationSnapshot, decodeCoordinationOperation),
        )
      ) {
        return { kind: 'REFUSED', reason: 'RESET_SUPPRESSED' } as const;
      }
      if (
        presence?.state !== 'ANDROID_STATE' ||
        fence === null ||
        active === null ||
        fence.activeInstallationId !== request.installationId ||
        fence.senderEpoch !== request.senderEpoch ||
        fence.resetGeneration !== request.resetGeneration ||
        active.state !== 'ACTIVE' ||
        active.epoch !== request.senderEpoch
      ) {
        return { kind: 'REFUSED', reason: 'RESET_SUPPRESSED' } as const;
      }
      const decision = decideBeginTransfer(
        fence,
        decoded(tombstoneSnapshot, decodeTombstone),
        decoded(targetSnapshot, decodeInstallation),
        request.targetInstallationId,
        nowMs,
      );
      if (decision.kind === 'STARTED') {
        setDocument(transaction, paths.account, decision.fence);
      }
      return decision;
    });
  }

  public async completeTransfer(
    uid: string,
    request: TransferRequest,
  ): Promise<ReturnType<typeof decideCompleteTransfer>> {
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    return this.db.runTransaction(async transaction => {
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const operationSnapshot = await transaction.get(paths.operation);
      const fenceSnapshot = await transaction.get(paths.account);
      const presenceSnapshot = await transaction.get(paths.presence);
      const activeReference = paths.installation(request.installationId);
      const activeSnapshot = await transaction.get(activeReference);
      const targetReference = paths.installation(request.targetInstallationId);
      const targetSnapshot = await transaction.get(targetReference);
      const fence = decoded(fenceSnapshot, decodeAccountFence);
      const presence = decoded(presenceSnapshot, decodePresence);
      if (
        isCoordinationMutationBlocked(
          decoded(operationSnapshot, decodeCoordinationOperation),
        )
      ) {
        return { kind: 'REFUSED', reason: 'RESET_SUPPRESSED' } as const;
      }
      if (
        presence?.state !== 'ANDROID_STATE' ||
        fence?.activeInstallationId !== request.installationId ||
        fence.senderEpoch !== request.senderEpoch ||
        fence.resetGeneration !== request.resetGeneration
      ) {
        return { kind: 'REFUSED', reason: 'RESET_SUPPRESSED' } as const;
      }
      const decision = decideCompleteTransfer(
        fence,
        decoded(tombstoneSnapshot, decodeTombstone),
        decoded(activeSnapshot, decodeInstallation),
        decoded(targetSnapshot, decodeInstallation),
        nowMs,
      );
      if (decision.kind === 'COMPLETED') {
        setDocument(transaction, paths.account, decision.fence);
        setDocument(transaction, activeReference, decision.oldInstallation);
        setDocument(transaction, targetReference, decision.targetInstallation);
      }
      return decision;
    });
  }

  public async beginDeletion(
    uid: string,
    request: DeletionRequest,
  ): Promise<ReturnType<typeof decideBeginDeletion>> {
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    const requestKey = deriveDeletionReceiptKey(request.requestId);
    return this.db.runTransaction(async transaction => {
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const operationSnapshot = await transaction.get(paths.operation);
      const fenceSnapshot = await transaction.get(paths.account);
      await transaction.get(paths.presence);
      const tombstone = decoded(tombstoneSnapshot, decodeTombstone);
      const receiptReference = deletionReceiptPath(this.db, requestKey);
      const receiptSnapshot = await transaction.get(receiptReference);
      const receipt = decoded(receiptSnapshot, decodeAccountDeletionReceipt);
      if (
        isCoordinationMutationBlocked(
          decoded(operationSnapshot, decodeCoordinationOperation),
        )
      ) {
        return {
          kind: 'REFUSED',
          reason: 'COORDINATION_OPERATION_IN_PROGRESS',
        } as const;
      }
      if (tombstone === null && receipt !== null) {
        throw new LedgerCorruptError();
      }
      if (
        tombstone !== null &&
        tombstone.requestKey === requestKey &&
        receipt !== null &&
        (receipt.outcome !== 'IN_PROGRESS' ||
          receipt.requestedAtMs !== tombstone.createdAtMs)
      ) {
        throw new LedgerCorruptError();
      }
      const fence = decoded(fenceSnapshot, decodeAccountFence);
      const decision = decideBeginDeletion(tombstone, fence, requestKey, nowMs);
      if (decision.kind === 'STARTED' || decision.kind === 'REPLAYED') {
        setDocument(
          transaction,
          receiptReference,
          inProgressDeletionReceipt(decision.tombstone.createdAtMs, nowMs),
        );
        if (decision.kind === 'STARTED') {
          // Account deletion fences and terminates any active Android sender state.

          setDocument(transaction, paths.tombstone, decision.tombstone);
          setDocument(
            transaction,
            paths.presence,
            presenceFor('deletion-fence', 'DELETING', nowMs),
          );
          if (decision.fence !== null) {
            setDocument(transaction, paths.account, decision.fence);
          }
        }
      }
      return decision;
    });
  }

  public async accountDeletionReceipt(
    request: DeletionReceiptRequest,
  ): Promise<AccountDeletionReceiptResponse> {
    const reference = deletionReceiptPath(
      this.db,
      deriveDeletionReceiptKey(request.receiptId),
    );
    const snapshot = await reference.get();
    return deletionReceiptResponse(
      decoded(snapshot, decodeAccountDeletionReceipt),
    );
  }

  public async coordinationLifecycleStatus(
    uid: string,
    request: CoordinationLifecycleStatusRequest,
  ): Promise<CoordinationLifecycleStatusResponse> {
    void request;
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    try {
      return await this.db.runTransaction(async transaction => {
        const globalSnapshot = await transaction.get(
          globalControlPath(this.db),
        );
        const tombstoneSnapshot = await transaction.get(paths.tombstone);
        const operationSnapshot = await transaction.get(paths.operation);
        const fenceSnapshot = await transaction.get(paths.account);
        const presenceSnapshot = await transaction.get(paths.presence);
        const latestReceiptSnapshot = await transaction.get(
          paths.latestOperationReceipt,
        );
        const tombstone = decoded(tombstoneSnapshot, decodeTombstone);
        const operation = decoded(
          operationSnapshot,
          decodeCoordinationOperation,
        );
        const fence = decoded(fenceSnapshot, decodeAccountFence);
        const presence = decoded(presenceSnapshot, decodePresence);
        if (operation !== null && tombstone !== null) {
          return { kind: 'SAFETY_STATUS_UNAVAILABLE', serverNowMs: nowMs };
        }
        if (operation !== null) {
          const status = inProgressResponse(operation);
          if (status.kind !== 'IN_PROGRESS') {
            throw new LedgerCorruptError();
          }
          return {
            ...status,
            kind: 'OPERATION_IN_PROGRESS',
            serverNowMs: nowMs,
          };
        }
        if (tombstone !== null) {
          return {
            kind: 'ACCOUNT_DELETION_IN_PROGRESS',
            serverNowMs: nowMs,
            stage: tombstone.stage,
            drainUntilMs: tombstone.drainUntilMs,
          };
        }
        if (fence?.mode === 'DELETING') {
          return { kind: 'SAFETY_STATUS_UNAVAILABLE', serverNowMs: nowMs };
        }
        const latestReceipt = decoded(
          latestReceiptSnapshot,
          decodeCoordinationOperationReceipt,
        );
        if (
          latestReceipt !== null &&
          latestReceipt.accountKey !== deriveOperationAccountKey(uid)
        ) {
          return { kind: 'SAFETY_STATUS_UNAVAILABLE', serverNowMs: nowMs };
        }
        const latestCompletion =
          latestReceipt !== null && latestReceipt.cleanupAtMs > nowMs
            ? completedResponse(latestReceipt)
            : null;
        if (fence === null) {
          if (presence !== null) {
            return { kind: 'SAFETY_STATUS_UNAVAILABLE', serverNowMs: nowMs };
          }
          return {
            kind: 'NO_ANDROID_STATE',
            serverNowMs: nowMs,
            ...(latestCompletion === null ? {} : { latestCompletion }),
          };
        }
        const control = decoded(globalSnapshot, decodeGlobalControl);
        const activeInstallation = decoded(
          await transaction.get(paths.installation(fence.activeInstallationId)),
          decodeInstallation,
        );
        if (
          !isPresenceConsistent(presence, control) ||
          presence?.state !== 'ANDROID_STATE' ||
          activeInstallation?.state !== 'ACTIVE' ||
          activeInstallation.installationId !== fence.activeInstallationId ||
          activeInstallation.epoch !== fence.senderEpoch
        ) {
          return { kind: 'SAFETY_STATUS_UNAVAILABLE', serverNowMs: nowMs };
        }
        if (
          fence.mode === 'TRANSFER_PENDING' &&
          (fence.transferTargetInstallationId === undefined ||
            fence.transferDrainUntilMs === undefined)
        ) {
          return { kind: 'SAFETY_STATUS_UNAVAILABLE', serverNowMs: nowMs };
        }
        return {
          kind: 'ANDROID_STATE',
          serverNowMs: nowMs,
          mode: fence.mode,
          activeInstallationId: fence.activeInstallationId,
          senderEpoch: fence.senderEpoch,
          resetGeneration: fence.resetGeneration,
          ownerLeaseUntilMs: fence.ownerLeaseUntilMs,
          latestIssuedSubmitNotAfterMs: fence.latestIssuedSubmitNotAfterMs,
          birthdayAutomationNotBeforeMs: fence.birthdayAutomationNotBeforeMs,
          ...(fence.mode === 'TRANSFER_PENDING'
            ? {
                transferTargetInstallationId:
                  fence.transferTargetInstallationId,
                transferDrainUntilMs: fence.transferDrainUntilMs,
              }
            : {}),
          ...(latestCompletion === null ? {} : { latestCompletion }),
        };
      });
    } catch (error) {
      if (error instanceof LedgerCorruptError) {
        return { kind: 'SAFETY_STATUS_UNAVAILABLE', serverNowMs: nowMs };
      }
      throw error;
    }
  }

  public async advanceDeletion(uid: string): Promise<'WAIT' | 'ADVANCED'> {



    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    const decision = await this.db.runTransaction(async transaction => {
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const operationSnapshot = await transaction.get(paths.operation);
      const fenceSnapshot = await transaction.get(paths.account);
      if (
        isCoordinationMutationBlocked(
          decoded(operationSnapshot, decodeCoordinationOperation),
        )
      ) {
        return null;
      }
      const tombstone = decoded(tombstoneSnapshot, decodeTombstone);
      const fence = decoded(fenceSnapshot, decodeAccountFence);
      if (tombstone?.stage === 'PURGING') {
        return { kind: 'PURGING' } as const;
      }
      if (tombstone?.stage !== 'DRAINING') {
        return null;
      }
      const activeReference =
        fence === null ? null : paths.installation(fence.activeInstallationId);
      const activeInstallation =
        activeReference === null
          ? null
          : decoded(await transaction.get(activeReference), decodeInstallation);
      const next = decideAdvanceDeletionDrain(
        tombstone,
        fence,
        activeInstallation,
        nowMs,
      );
      if (next.kind === 'READY_TO_PURGE') {
        setDocument(transaction, paths.tombstone, next.tombstone);
        setDocument(
          transaction,
          paths.presence,
          presenceFor('deletion-fence', 'DELETING', nowMs),
        );
        if (next.fence !== null) {
          setDocument(transaction, paths.account, next.fence);
        }
        if (activeReference !== null && next.activeInstallation !== null) {
          setDocument(transaction, activeReference, next.activeInstallation);
        }
      }
      return next;
    });
    if (decision?.kind !== 'READY_TO_PURGE' && decision?.kind !== 'PURGING') {
      return 'WAIT';
    }
    await this.db.recursiveDelete(paths.account);
    const receiptQuery = this.db
      .collection('coordinationOperationReceipts')
      .where('accountKey', '==', deriveOperationAccountKey(uid));
    await this.deleteQuery(receiptQuery);
    await paths.latestOperationReceipt.delete();
    if (
      !(await receiptQuery.limit(1).get()).empty ||
      (await paths.latestOperationReceipt.get()).exists
    ) {
      throw new LedgerCorruptError();
    }
    await this.db.runTransaction(async transaction => {
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const accountSnapshot = await transaction.get(paths.account);
      const tombstone = decoded(tombstoneSnapshot, decodeTombstone);
      if (tombstone?.stage !== 'PURGING' || accountSnapshot.exists) {
        throw new LedgerCorruptError();
      }
      setDocument(transaction, paths.tombstone, {
        ...tombstone,
        stage: 'AUTH_DELETION_PENDING',
        updatedAtMs: this.clock(),
        nextSweepAtMs: this.clock(),
        sweepAttemptCount: 0,
      });
    });
    return 'ADVANCED';
  }

  public async markAuthDeleted(uid: string): Promise<void> {
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    await this.db.runTransaction(async transaction => {
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const accountSnapshot = await transaction.get(paths.account);
      const tombstone = decoded(tombstoneSnapshot, decodeTombstone);
      if (
        tombstone?.stage !== 'AUTH_DELETION_PENDING' ||
        accountSnapshot.exists
      ) {
        throw new LedgerCorruptError();
      }
      setDocument(transaction, paths.tombstone, {
        ...tombstone,
        stage: 'VERIFYING',
        updatedAtMs: nowMs,
        cleanupAtMs: safeAddMs(nowMs, DAY_MS),
        nextSweepAtMs: safeAddMs(nowMs, DAY_MS),
        sweepAttemptCount: 0,
      });
      transaction.delete(paths.presence);
    });
  }

  public async deferDeletionSweep(
    uid: string,
    expectedRequestKey: string | null,
    minimumNextSweepAtMs: number,
  ): Promise<boolean> {
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    return this.db.runTransaction(async transaction => {
      const snapshot = await transaction.get(paths.tombstone);
      if (!snapshot.exists) {
        return false;
      }
      const rawData: unknown = snapshot.data();
      const data =
        typeof rawData === 'object' && rawData !== null
          ? (rawData as Readonly<Record<string, unknown>>)
          : {};
      const currentRequestKey = data.requestKey;
      if (
        (expectedRequestKey !== null &&
          currentRequestKey !== expectedRequestKey) ||
        (expectedRequestKey === null && typeof currentRequestKey === 'string')
      ) {
        return false;
      }
      const currentAttempt =
        typeof data.sweepAttemptCount === 'number' &&
        Number.isSafeInteger(data.sweepAttemptCount) &&
        data.sweepAttemptCount >= 0
          ? data.sweepAttemptCount
          : 0;
      transaction.update(paths.tombstone, {
        sweepAttemptCount: boundedSweepAttempt(currentAttempt),
        nextSweepAtMs: Math.max(
          minimumNextSweepAtMs,
          nextRepairSweepAtMs(nowMs, currentAttempt),
        ),
      });
      return true;
    });
  }

  public async finalizeDeletionTombstone(uid: string): Promise<boolean> {
    const nowMs = this.clock();
    const paths = accountPaths(this.db, uid);
    return this.db.runTransaction(async transaction => {
      const tombstoneSnapshot = await transaction.get(paths.tombstone);
      const accountSnapshot = await transaction.get(paths.account);
      const presenceSnapshot = await transaction.get(paths.presence);
      const tombstone = decoded(tombstoneSnapshot, decodeTombstone);
      if (tombstone === null) {
        return false;
      }
      const receiptReference = deletionReceiptPath(
        this.db,
        tombstone.requestKey,
      );
      const receiptSnapshot = await transaction.get(receiptReference);
      const existingReceipt = decoded(
        receiptSnapshot,
        decodeAccountDeletionReceipt,
      );
      if (
        tombstone.stage !== 'VERIFYING' ||
        tombstone.cleanupAtMs === undefined ||
        tombstone.cleanupAtMs > nowMs ||
        accountSnapshot.exists ||
        presenceSnapshot.exists ||        existingReceipt?.outcome === 'COMPLETED' ||
        (existingReceipt !== null &&
          existingReceipt.requestedAtMs !== tombstone.createdAtMs)
      ) {
        return false;
      }
      const inProgress =
        existingReceipt?.outcome === 'IN_PROGRESS'
          ? existingReceipt
          : inProgressDeletionReceipt(tombstone.createdAtMs, nowMs);
      setDocument(
        transaction,
        receiptReference,
        completedDeletionReceipt(inProgress, nowMs),
      );
      transaction.delete(paths.tombstone);
      return true;
    });
  }
}
