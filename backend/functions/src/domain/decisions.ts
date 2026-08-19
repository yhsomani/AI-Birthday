import {
  CLAIM_AUTHORIZATION_MS,
  MAX_SUBMIT_AFTER_ARM_MS,
  NEVER_ARMED_CLEANUP_MS,
  SCHEMA_VERSION,
  type AccountFence,
  type ArmBudget,
  type ArmOutcome,
  type BindingInput,
  type Claim,
  type ClaimRequestRecord,
  type DeletionTombstone,

  type DestinationGuard,
  type GlobalControl,
  type Installation,
  type NoWriteReason,
  type OccurrenceKey,
  type Purpose,
  type SuppressionReason,
  type TestBarrierOutcome,
} from './model.js';
import {
  advanceFenceAfterArm,
  appendBudgetEntry,
  armClaim,
  armGuard,
  checkBinding,
  checkControlCompatibility,
  checkGlobalControl,
  cleanupForInstallation,
  expireClaim,
  expireGuard,
  makeArmedOutcome,
  makeNoWriteOutcome,
  renewedLeaseUntil,
  retentionMs,
  safeAddMs,
  worstCaseSameDateResetRelease,
} from './policies.js';

export interface RegisterInput {
  readonly ledgerGeneration: string;
  readonly installationId: string;
  readonly appBuildNumber: number;
  readonly policyVersion: number;
  readonly distributionChannel: string;
}

export type RegistrationDecision =
  | {
      readonly kind: 'REGISTERED_ACTIVE';
      readonly fence: AccountFence;
      readonly installation: Installation;
    }
  | {
      readonly kind: 'REGISTERED_STANDBY';
      readonly fence: AccountFence;
      readonly installation: Installation;
    }
  | {
      readonly kind: 'REPLAYED';
      readonly fence: AccountFence;
      readonly installation: Installation;
    }
  | {
      readonly kind: 'SUPPRESSED';
      readonly reason: SuppressionReason | NoWriteReason;
    };

export function decideRegistration(
  control: GlobalControl | null,
  tombstone: DeletionTombstone | null,
  fence: AccountFence | null,
  existingInstallation: Installation | null,
  input: RegisterInput,
  nowMs: number,
): RegistrationDecision {
  if (tombstone !== null) {
    return { kind: 'SUPPRESSED', reason: 'DELETION_SUPPRESSED' };
  }
  const compatibility = checkControlCompatibility(control, {
    ledgerGeneration: input.ledgerGeneration,
    appBuildNumber: input.appBuildNumber,
    policyVersion: input.policyVersion,
    distributionChannel: input.distributionChannel,
  });
  if (compatibility !== null) {
    return { kind: 'SUPPRESSED', reason: compatibility };
  }
  if (fence === null) {
    const activeFence: AccountFence = {
      schemaVersion: SCHEMA_VERSION,
      mode: 'TEST_ONLY',
      activeInstallationId: input.installationId,
      senderEpoch: 1,
      ownerLeaseUntilMs: renewedLeaseUntil(nowMs),
      nextArmNotBeforeMs: nowMs,
      latestIssuedSubmitNotAfterMs: nowMs,
      resetGeneration: 1,
      birthdayAutomationNotBeforeMs: worstCaseSameDateResetRelease(nowMs),
      createdAtMs: nowMs,
      updatedAtMs: nowMs,
    };
    return {
      kind: 'REGISTERED_ACTIVE',
      fence: activeFence,
      installation: {
        schemaVersion: SCHEMA_VERSION,
        installationId: input.installationId,
        state: 'ACTIVE',
        epoch: 1,
        appBuildNumber: input.appBuildNumber,
        policyVersion: input.policyVersion,
        distributionChannel: input.distributionChannel,
        lastSeenAtMs: nowMs,
      },
    };
  }
  if (fence.mode === 'DELETING') {
    return { kind: 'SUPPRESSED', reason: 'DELETION_SUPPRESSED' };
  }
  if (
    fence.activeInstallationId === input.installationId &&
    existingInstallation?.state === 'ACTIVE' &&
    existingInstallation.epoch === fence.senderEpoch
  ) {
    return {
      kind: 'REPLAYED',
      fence,
      installation: {
        ...existingInstallation,
        appBuildNumber: input.appBuildNumber,
        policyVersion: input.policyVersion,
        distributionChannel: input.distributionChannel,
        lastSeenAtMs: nowMs,
      },
    };
  }
  return {
    kind: 'REGISTERED_STANDBY',
    fence,
    installation: {
      schemaVersion: SCHEMA_VERSION,
      installationId: input.installationId,
      state: 'STANDBY',
      epoch: 0,
      appBuildNumber: input.appBuildNumber,
      policyVersion: input.policyVersion,
      distributionChannel: input.distributionChannel,
      lastSeenAtMs: nowMs,
      cleanupAtMs: cleanupForInstallation('STANDBY', nowMs),
    },
  };
}

export interface ClaimInput extends BindingInput {
  readonly purpose: Purpose;
  readonly claimRequestId: string;
  readonly claimId: string;
  readonly requestKey: string;
  readonly occurrenceAliasKeys: readonly string[];
  readonly destinationAliasKeys: readonly string[];
  readonly testMaterialAliasKeys: readonly string[];
}

export interface ClaimCollisionSnapshot {
  readonly requestRecord: ClaimRequestRecord | null;
  readonly requestClaim: Claim | null;
  readonly occurrenceKeys: readonly OccurrenceKey[];
  readonly destinationGuards: readonly DestinationGuard[];
}

export type ClaimDecision =
  | {
      readonly kind: 'CLAIMED';
      readonly claim: Claim;
      readonly requestRecord: ClaimRequestRecord;
      readonly occurrenceKeys: readonly OccurrenceKey[];
      readonly destinationGuards: readonly DestinationGuard[];
    }
  | { readonly kind: 'REPLAYED'; readonly claim: Claim }
  | {
      readonly kind: 'REFUSED';
      readonly reason:
        | NoWriteReason
        | SuppressionReason
        | 'OCCURRENCE_RESERVED'
        | 'DESTINATION_RESERVED'
        | 'TEST_MATERIAL_MISMATCH'
        | 'REQUEST_RECORD_CORRUPT';
    };

function isReclaimableOccurrence(key: OccurrenceKey): boolean {
  return key.state === 'EXPIRED_NO_ARM_RECLAIMABLE';
}

function isReclaimableDestination(guard: DestinationGuard): boolean {
  return guard.state === 'EXPIRED_NO_ARM_RECLAIMABLE';
}

export function decideClaim(
  control: GlobalControl | null,
  tombstone: DeletionTombstone | null,
  fence: AccountFence | null,
  installation: Installation | null,
  collisions: ClaimCollisionSnapshot,
  input: ClaimInput,
  nowMs: number,
): ClaimDecision {
  const testMaterialMatches = (existing: Claim): boolean =>
    input.purpose !== 'TEST' ||
    existing.testMaterialAliasKeys.some(alias =>
      input.testMaterialAliasKeys.includes(alias),
    );
  if (collisions.requestRecord !== null) {
    if (
      collisions.requestRecord.linkedClaimId ===
        collisions.requestClaim?.claimId &&
      collisions.requestClaim.claimRequestId === input.claimRequestId
    ) {
      if (!testMaterialMatches(collisions.requestClaim)) {
        return { kind: 'REFUSED', reason: 'TEST_MATERIAL_MISMATCH' };
      }
      return { kind: 'REPLAYED', claim: collisions.requestClaim };
    }
    return { kind: 'REFUSED', reason: 'REQUEST_RECORD_CORRUPT' };
  }
  if (collisions.requestClaim !== null) {
    if (
      collisions.requestClaim.claimId === input.claimId &&
      collisions.requestClaim.claimRequestId === input.claimRequestId &&
      collisions.requestClaim.purpose === input.purpose
    ) {
      if (!testMaterialMatches(collisions.requestClaim)) {
        return { kind: 'REFUSED', reason: 'TEST_MATERIAL_MISMATCH' };
      }
      return { kind: 'REPLAYED', claim: collisions.requestClaim };
    }
    return { kind: 'REFUSED', reason: 'REQUEST_RECORD_CORRUPT' };
  }
  if (tombstone !== null || fence?.mode === 'DELETING') {
    return { kind: 'REFUSED', reason: 'DELETION_SUPPRESSED' };
  }
  if (fence === null) {
    return { kind: 'REFUSED', reason: 'MISSING_FENCE' };
  }
  if (fence.resetGeneration !== input.resetGeneration) {
    return { kind: 'REFUSED', reason: 'RESET_SUPPRESSED' };
  }
  const globalReason = checkGlobalControl(control, input);
  if (globalReason !== null) {
    return { kind: 'REFUSED', reason: globalReason };
  }
  const bindingReason = checkBinding(
    fence,
    installation,
    input,
    input.purpose,
    nowMs,
  );
  if (bindingReason !== null) {
    return { kind: 'REFUSED', reason: bindingReason };
  }
  if (
    input.purpose === 'BIRTHDAY' &&
    collisions.occurrenceKeys.some(key => !isReclaimableOccurrence(key))
  ) {
    return { kind: 'REFUSED', reason: 'OCCURRENCE_RESERVED' };
  }
  if (
    input.purpose === 'BIRTHDAY' &&
    collisions.destinationGuards.some(guard => !isReclaimableDestination(guard))
  ) {
    return { kind: 'REFUSED', reason: 'DESTINATION_RESERVED' };
  }

  const claimExpiresAtMs = safeAddMs(nowMs, CLAIM_AUTHORIZATION_MS);
  const maxPossibleSubmitNotAfterMs = safeAddMs(
    claimExpiresAtMs,
    MAX_SUBMIT_AFTER_ARM_MS,
  );
  const cleanupAtMs = safeAddMs(nowMs, NEVER_ARMED_CLEANUP_MS);
  const claim: Claim = {
    schemaVersion: SCHEMA_VERSION,
    claimId: input.claimId,
    purpose: input.purpose,
    claimRequestId: input.claimRequestId,
    ownerInstallationId: input.installationId,
    ownerEpoch: input.senderEpoch,
    resetGeneration: input.resetGeneration,
    state: 'CLAIMED',
    attempt: 1,
    retryAuthorizationGeneration: 0,
    claimExpiresAtMs,
    maxPossibleSubmitNotAfterMs,
    occurrenceAliasKeys:
      input.purpose === 'BIRTHDAY' ? input.occurrenceAliasKeys : [],
    destinationAliasKeys:
      input.purpose === 'BIRTHDAY' ? input.destinationAliasKeys : [],
    testMaterialAliasKeys:
      input.purpose === 'TEST' ? input.testMaterialAliasKeys : [],
    createdAtMs: nowMs,
    updatedAtMs: nowMs,
    cleanupAtMs,
  };
  const requestRecord: ClaimRequestRecord = {
    schemaVersion: SCHEMA_VERSION,
    requestKey: input.requestKey,
    purpose: input.purpose,
    linkedClaimId: claim.claimId,
    createdAtMs: nowMs,
    cleanupAtMs,
  };
  const occurrenceKeys: readonly OccurrenceKey[] =
    claim.occurrenceAliasKeys.map(aliasKey => ({
      schemaVersion: SCHEMA_VERSION,
      aliasKey,
      linkedClaimId: claim.claimId,
      state: 'RESERVED',
      createdAtMs: nowMs,
      updatedAtMs: nowMs,
      cleanupAtMs,
    }));
  const destinationGuards: readonly DestinationGuard[] =
    claim.destinationAliasKeys.map(aliasKey => ({
      schemaVersion: SCHEMA_VERSION,
      aliasKey,
      linkedClaimId: claim.claimId,
      ownerEpoch: claim.ownerEpoch,
      state: 'RESERVED',
      createdAtMs: nowMs,
      updatedAtMs: nowMs,
      cleanupAtMs,
    }));
  return {
    kind: 'CLAIMED',
    claim,
    requestRecord,
    occurrenceKeys,
    destinationGuards,
  };
}

export interface ArmInput extends BindingInput {
  readonly purpose: Purpose;
  readonly claimId: string;
  readonly armRequestId: string;
  readonly attempt: 1 | 2;
}

export interface ArmSnapshot {
  readonly outcome: ArmOutcome | null;
  readonly tombstone: DeletionTombstone | null;
  readonly fence: AccountFence | null;
  readonly installation: Installation | null;
  readonly claim: Claim | null;
  readonly occurrenceKeys: readonly OccurrenceKey[];
  readonly destinationGuards: readonly DestinationGuard[];
  readonly budget: ArmBudget | null;
}

export type ArmDecision =
  | { readonly kind: 'REPLAYED'; readonly outcome: ArmOutcome }
  | { readonly kind: 'SUPPRESSED'; readonly reason: SuppressionReason }
  | {
      readonly kind: 'NO_WRITE';
      readonly outcome: ArmOutcome & { readonly kind: 'NO_WRITE' };
      readonly claim?: Claim | undefined;
      readonly destinationGuards?: readonly DestinationGuard[] | undefined;
      readonly occurrenceKeyState?: 'EXPIRED_NO_ARM_RECLAIMABLE' | undefined;
    }
  | {
      readonly kind: 'ARMED';
      readonly outcome: ArmOutcome & { readonly kind: 'ARMED' };
      readonly fence: AccountFence;
      readonly claim: Claim;
      readonly destinationGuards: readonly DestinationGuard[];
      readonly occurrenceKeyState: 'ARMED';
      readonly budget: ArmBudget;
    };

function noWrite(
  claim: Claim,
  input: ArmInput,
  reason: NoWriteReason,
  nowMs: number,
): ArmDecision {
  return {
    kind: 'NO_WRITE',
    outcome: makeNoWriteOutcome(claim, input.armRequestId, reason, nowMs),
  };
}

export function decideArm(
  control: GlobalControl | null,
  snapshot: ArmSnapshot,
  input: ArmInput,
  nowMs: number,
): ArmDecision {
  if (snapshot.outcome !== null) {
    return { kind: 'REPLAYED', outcome: snapshot.outcome };
  }
  if (snapshot.tombstone !== null || snapshot.fence?.mode === 'DELETING') {
    return { kind: 'SUPPRESSED', reason: 'DELETION_SUPPRESSED' };
  }
  if (snapshot.fence === null) {
    return { kind: 'SUPPRESSED', reason: 'MISSING_FENCE' };
  }
  if (snapshot.claim === null) {
    return { kind: 'SUPPRESSED', reason: 'MISSING_CLAIM' };
  }
  const claim = snapshot.claim;
  if (
    snapshot.fence.resetGeneration !== input.resetGeneration ||
    claim.resetGeneration !== input.resetGeneration
  ) {
    return { kind: 'SUPPRESSED', reason: 'RESET_SUPPRESSED' };
  }
  if (
    claim.claimId !== input.claimId ||
    claim.purpose !== input.purpose ||
    claim.ownerInstallationId !== input.installationId ||
    claim.ownerEpoch !== input.senderEpoch ||
    claim.attempt !== input.attempt
  ) {
    return noWrite(claim, input, 'CLAIM_STATE_MISMATCH', nowMs);
  }
  if (
    input.purpose === 'BIRTHDAY' &&
    (snapshot.occurrenceKeys.length !== claim.occurrenceAliasKeys.length ||
      snapshot.occurrenceKeys.some(
        key =>
          key.linkedClaimId !== claim.claimId ||
          (input.attempt === 1
            ? key.state !== 'RESERVED'
            : key.state !== 'ARMED'),
      ) ||
      snapshot.destinationGuards.length !== claim.destinationAliasKeys.length ||
      snapshot.destinationGuards.some(
        guard =>
          guard.linkedClaimId !== claim.claimId ||
          (input.attempt === 1
            ? guard.state !== 'RESERVED'
            : guard.state !== 'ARMED'),
      ))
  ) {
    return { kind: 'SUPPRESSED', reason: 'UNKNOWN_HISTORY' };
  }
  if (nowMs >= claim.claimExpiresAtMs) {
    const reason =
      claim.state === 'RETRY_CLAIMED' || claim.attempt === 2
        ? 'EXPIRED_RETRY'
        : 'EXPIRED';
    const expiredClaim = expireClaim(claim, nowMs);
    const expiredGuards = snapshot.destinationGuards.map(guard =>
      expireGuard(guard, claim, nowMs),
    );
    return {
      kind: 'NO_WRITE',
      outcome: makeNoWriteOutcome(claim, input.armRequestId, reason, nowMs),
      claim: expiredClaim,
      destinationGuards: expiredGuards,
      occurrenceKeyState:
        reason === 'EXPIRED' ? 'EXPIRED_NO_ARM_RECLAIMABLE' : undefined,
    };
  }
  const expectedState = input.attempt === 1 ? 'CLAIMED' : 'RETRY_CLAIMED';
  if (claim.state !== expectedState) {
    return noWrite(claim, input, 'CLAIM_STATE_MISMATCH', nowMs);
  }
  const globalReason = checkGlobalControl(control, input);
  if (globalReason !== null) {
    return noWrite(claim, input, globalReason, nowMs);
  }
  const bindingReason = checkBinding(
    snapshot.fence,
    snapshot.installation,
    input,
    input.purpose,
    nowMs,
  );
  if (bindingReason !== null) {
    return noWrite(claim, input, bindingReason, nowMs);
  }
  const budget = appendBudgetEntry(
    snapshot.budget,
    input.purpose,
    claim.claimId,
    nowMs,
  );
  if (budget === null) {
    return noWrite(claim, input, 'BUDGET_EXCEEDED', nowMs);
  }
  const outcome = makeArmedOutcome(claim, input.armRequestId, nowMs);
  return {
    kind: 'ARMED',
    outcome,
    fence: advanceFenceAfterArm(snapshot.fence, outcome, nowMs),
    claim: armClaim(claim, outcome, nowMs),
    destinationGuards: snapshot.destinationGuards.map(guard =>
      armGuard(guard, outcome, nowMs),
    ),
    occurrenceKeyState: 'ARMED',
    budget,
  };
}

export type ArmStatusDecision =
  | { readonly kind: 'REPLAYED'; readonly outcome: ArmOutcome }
  | { readonly kind: 'UNKNOWN' }
  | { readonly kind: 'SUPPRESSED'; readonly reason: SuppressionReason }
  | Extract<ArmDecision, { readonly kind: 'NO_WRITE' }>;

export function decideArmStatus(
  snapshot: ArmSnapshot,
  input: ArmInput,
  nowMs: number,
): ArmStatusDecision {
  if (snapshot.outcome !== null) {
    return { kind: 'REPLAYED', outcome: snapshot.outcome };
  }
  if (snapshot.tombstone !== null || snapshot.fence?.mode === 'DELETING') {
    return { kind: 'SUPPRESSED', reason: 'DELETION_SUPPRESSED' };
  }
  if (snapshot.fence === null) {
    return { kind: 'SUPPRESSED', reason: 'MISSING_FENCE' };
  }
  if (snapshot.claim === null) {
    return { kind: 'SUPPRESSED', reason: 'MISSING_CLAIM' };
  }
  if (
    snapshot.fence.resetGeneration !== input.resetGeneration ||
    snapshot.claim.resetGeneration !== input.resetGeneration
  ) {
    return { kind: 'SUPPRESSED', reason: 'RESET_SUPPRESSED' };
  }
  if (
    snapshot.claim.claimId !== input.claimId ||
    snapshot.claim.purpose !== input.purpose ||
    snapshot.claim.ownerInstallationId !== input.installationId ||
    snapshot.claim.ownerEpoch !== input.senderEpoch ||
    snapshot.claim.attempt !== input.attempt
  ) {
    return { kind: 'UNKNOWN' };
  }
  const expectedState = input.attempt === 1 ? 'CLAIMED' : 'RETRY_CLAIMED';
  if (snapshot.claim.state !== expectedState) {
    return { kind: 'UNKNOWN' };
  }
  if (
    input.purpose === 'BIRTHDAY' &&
    (snapshot.occurrenceKeys.length !==
      snapshot.claim.occurrenceAliasKeys.length ||
      snapshot.destinationGuards.length !==
        snapshot.claim.destinationAliasKeys.length ||
      snapshot.occurrenceKeys.some(
        key =>
          key.linkedClaimId !== snapshot.claim?.claimId ||
          (input.attempt === 1
            ? key.state !== 'RESERVED'
            : key.state !== 'ARMED'),
      ) ||
      snapshot.destinationGuards.some(
        guard =>
          guard.linkedClaimId !== snapshot.claim?.claimId ||
          (input.attempt === 1
            ? guard.state !== 'RESERVED'
            : guard.state !== 'ARMED'),
      ))
  ) {
    return { kind: 'UNKNOWN' };
  }
  if (nowMs < snapshot.claim.claimExpiresAtMs) {
    return { kind: 'UNKNOWN' };
  }
  const reason =
    snapshot.claim.state === 'RETRY_CLAIMED' || snapshot.claim.attempt === 2
      ? 'EXPIRED_RETRY'
      : 'EXPIRED';
  const claim = snapshot.claim;
  return {
    kind: 'NO_WRITE',
    outcome: makeNoWriteOutcome(claim, input.armRequestId, reason, nowMs),
    claim: expireClaim(claim, nowMs),
    destinationGuards: snapshot.destinationGuards.map(guard =>
      expireGuard(guard, claim, nowMs),
    ),
    occurrenceKeyState:
      reason === 'EXPIRED' ? 'EXPIRED_NO_ARM_RECLAIMABLE' : undefined,
  };
}

export type RetryDecision =
  | { readonly kind: 'AUTHORIZED'; readonly claim: Claim }
  | {
      readonly kind: 'REFUSED';
      readonly reason:
        | SuppressionReason
        | 'NOT_ARMED_ATTEMPT_ONE'
        | 'RETRY_REQUEST_MISMATCH'
        | 'UNSUPPORTED_ZERO_ACCEPTANCE_PROOF';
    };

export function decideSafeRetry(
  fence: AccountFence | null,
  tombstone: DeletionTombstone | null,
  claim: Claim | null,
  retryRequestId: string,
  proof: 'ALL_PARTS_RADIO_OFF' | 'ALL_PARTS_NO_SERVICE' | 'OTHER',
  nowMs: number,
): RetryDecision {
  if (tombstone !== null || fence?.mode === 'DELETING') {
    return { kind: 'REFUSED', reason: 'DELETION_SUPPRESSED' };
  }
  if (fence === null) {
    return { kind: 'REFUSED', reason: 'MISSING_FENCE' };
  }
  if (claim === null) {
    return { kind: 'REFUSED', reason: 'MISSING_CLAIM' };
  }
  if (claim.resetGeneration !== fence.resetGeneration) {
    return { kind: 'REFUSED', reason: 'RESET_SUPPRESSED' };
  }
  if (proof === 'OTHER') {
    return { kind: 'REFUSED', reason: 'UNSUPPORTED_ZERO_ACCEPTANCE_PROOF' };
  }
  if (
    claim.ownerInstallationId !== fence.activeInstallationId ||
    claim.ownerEpoch !== fence.senderEpoch
  ) {
    return { kind: 'REFUSED', reason: 'RETRY_REQUEST_MISMATCH' };
  }
  if (claim.state === 'RETRY_CLAIMED' && claim.attempt === 2) {
    return claim.retryRequestId === retryRequestId && claim.retryProof === proof
      ? { kind: 'AUTHORIZED', claim }
      : { kind: 'REFUSED', reason: 'RETRY_REQUEST_MISMATCH' };
  }
  if (claim.state !== 'ARMED' || claim.attempt !== 1) {
    return { kind: 'REFUSED', reason: 'NOT_ARMED_ATTEMPT_ONE' };
  }
  return {
    kind: 'AUTHORIZED',
    claim: {
      ...claim,
      state: 'RETRY_CLAIMED',
      attempt: 2,
      retryAuthorizationGeneration: claim.retryAuthorizationGeneration + 1,
      retryRequestId,
      retryProof: proof,
      claimExpiresAtMs: safeAddMs(nowMs, CLAIM_AUTHORIZATION_MS),
      maxPossibleSubmitNotAfterMs: safeAddMs(
        safeAddMs(nowMs, CLAIM_AUTHORIZATION_MS),
        MAX_SUBMIT_AFTER_ARM_MS,
      ),
      updatedAtMs: nowMs,
      cleanupAtMs: Math.max(
        claim.cleanupAtMs,
        safeAddMs(nowMs, retentionMs(claim.purpose)),
      ),
    },
  };
}

export interface TestReportInput extends BindingInput {
  readonly purpose: 'TEST';
  readonly testClaimId: string;
  readonly armRequestId: string;
  readonly result:
    | 'SENT_ALL_PARTS'
    | 'FAILED_ZERO_ACCEPTED'
    | 'FAILED_OR_UNKNOWN'
    | 'CLEANUP_CANCELLED';
}

export type TestReportDecision =
  | { readonly kind: 'RECORDED'; readonly claim: Claim }
  | { readonly kind: 'REPLAYED'; readonly outcome: TestBarrierOutcome }
  | { readonly kind: 'SUPPRESSED'; readonly reason: SuppressionReason }
  | {
      readonly kind: 'REFUSED';
      readonly reason:
        | 'BINDING_MISMATCH'
        | 'MODE_BLOCKED'
        | 'ARMED_OUTCOME_REQUIRED';
    };

export function decideTestReport(
  fence: AccountFence | null,
  tombstone: DeletionTombstone | null,
  installation: Installation | null,
  claim: Claim | null,
  outcome: ArmOutcome | null,
  input: TestReportInput,
  nowMs: number,
): TestReportDecision {
  if (tombstone !== null || fence?.mode === 'DELETING') {
    return { kind: 'SUPPRESSED', reason: 'DELETION_SUPPRESSED' };
  }
  if (fence === null) {
    return { kind: 'SUPPRESSED', reason: 'MISSING_FENCE' };
  }
  if (claim === null) {
    return { kind: 'SUPPRESSED', reason: 'MISSING_CLAIM' };
  }
  if (
    fence.resetGeneration !== input.resetGeneration ||
    claim.resetGeneration !== input.resetGeneration
  ) {
    return { kind: 'SUPPRESSED', reason: 'RESET_SUPPRESSED' };
  }
  if (claim.testBarrierOutcome !== undefined) {
    return { kind: 'REPLAYED', outcome: claim.testBarrierOutcome };
  }
  if (
    fence.activeInstallationId !== input.installationId ||
    fence.senderEpoch !== input.senderEpoch ||
    installation?.state !== 'ACTIVE' ||
    installation.installationId !== input.installationId ||
    installation.epoch !== input.senderEpoch ||
    claim.claimId !== input.testClaimId ||
    claim.purpose !== 'TEST' ||
    claim.ownerInstallationId !== input.installationId ||
    claim.ownerEpoch !== input.senderEpoch
  ) {
    return { kind: 'REFUSED', reason: 'BINDING_MISMATCH' };
  }
  if (fence.mode !== 'TEST_ONLY' && fence.mode !== 'PAUSED_REPAIR') {
    return { kind: 'REFUSED', reason: 'MODE_BLOCKED' };
  }
  if (
    claim.state !== 'ARMED' ||
    outcome?.kind !== 'ARMED' ||
    outcome.purpose !== 'TEST' ||
    outcome.claimId !== claim.claimId ||
    outcome.armRequestId !== input.armRequestId ||
    outcome.ownerInstallationId !== input.installationId ||
    outcome.ownerEpoch !== input.senderEpoch ||
    outcome.resetGeneration !== input.resetGeneration
  ) {
    return { kind: 'REFUSED', reason: 'ARMED_OUTCOME_REQUIRED' };
  }
  const successfulInWindow =
    input.result === 'SENT_ALL_PARTS' &&
    nowMs <= safeAddMs(outcome.serverSubmitNotAfterMs, 15 * 60_000);
  const testBarrierOutcome: TestBarrierOutcome = successfulInWindow
    ? 'SENT_ALL_PARTS_IN_WINDOW'
    : input.result === 'SENT_ALL_PARTS'
    ? 'SENT_EVIDENCE_LATE'
    : input.result;
  return {
    kind: 'RECORDED',
    claim: {
      ...claim,
      state: 'TERMINAL',
      testBarrierOutcome,
      updatedAtMs: nowMs,
      cleanupAtMs: Math.max(
        claim.cleanupAtMs,
        safeAddMs(nowMs, retentionMs('TEST')),
      ),
    },
  };
}

export type TransferDecision =
  | {
      readonly kind: 'STARTED';
      readonly fence: AccountFence;
    }
  | {
      readonly kind: 'COMPLETED';
      readonly fence: AccountFence;
      readonly oldInstallation: Installation;
      readonly targetInstallation: Installation;
    }
  | {
      readonly kind: 'REFUSED';
      readonly reason:
        | SuppressionReason
        | 'TARGET_NOT_STANDBY'
        | 'WRONG_MODE'
        | 'DRAIN_NOT_COMPLETE';
    };

export function decideBeginTransfer(
  fence: AccountFence | null,
  tombstone: DeletionTombstone | null,
  target: Installation | null,
  targetInstallationId: string,
  nowMs: number,
): TransferDecision {
  if (tombstone !== null || fence?.mode === 'DELETING') {
    return { kind: 'REFUSED', reason: 'DELETION_SUPPRESSED' };
  }
  if (fence === null) {
    return { kind: 'REFUSED', reason: 'MISSING_FENCE' };
  }
  if (fence.mode === 'TRANSFER_PENDING') {
    if (fence.transferTargetInstallationId === targetInstallationId) {
      return { kind: 'STARTED', fence };
    }
    return { kind: 'REFUSED', reason: 'WRONG_MODE' };
  }
  if (
    target?.state !== 'STANDBY' ||
    target.installationId !== targetInstallationId
  ) {
    return { kind: 'REFUSED', reason: 'TARGET_NOT_STANDBY' };
  }
  return {
    kind: 'STARTED',
    fence: {
      ...fence,
      mode: 'TRANSFER_PENDING',
      ownerLeaseUntilMs: nowMs,
      transferTargetInstallationId: targetInstallationId,
      transferDrainUntilMs: Math.max(nowMs, fence.latestIssuedSubmitNotAfterMs),
      updatedAtMs: nowMs,
    },
  };
}

export function decideCompleteTransfer(
  fence: AccountFence | null,
  tombstone: DeletionTombstone | null,
  oldInstallation: Installation | null,
  targetInstallation: Installation | null,
  nowMs: number,
): TransferDecision {
  if (tombstone !== null || fence?.mode === 'DELETING') {
    return { kind: 'REFUSED', reason: 'DELETION_SUPPRESSED' };
  }
  if (fence === null || oldInstallation === null) {
    return { kind: 'REFUSED', reason: 'MISSING_FENCE' };
  }
  if (
    fence.mode !== 'TRANSFER_PENDING' ||
    fence.transferTargetInstallationId === undefined ||
    fence.transferDrainUntilMs === undefined ||
    targetInstallation?.installationId !== fence.transferTargetInstallationId ||
    targetInstallation.state !== 'STANDBY'
  ) {
    return { kind: 'REFUSED', reason: 'WRONG_MODE' };
  }
  if (nowMs <= fence.transferDrainUntilMs) {
    return { kind: 'REFUSED', reason: 'DRAIN_NOT_COMPLETE' };
  }
  const senderEpoch = fence.senderEpoch + 1;
  const completedFence: AccountFence = {
    schemaVersion: SCHEMA_VERSION,
    mode: 'TEST_ONLY',
    activeInstallationId: targetInstallation.installationId,
    senderEpoch,
    ownerLeaseUntilMs: renewedLeaseUntil(nowMs),
    nextArmNotBeforeMs: Math.max(fence.nextArmNotBeforeMs, nowMs),
    latestIssuedSubmitNotAfterMs: fence.latestIssuedSubmitNotAfterMs,
    resetGeneration: fence.resetGeneration,
    birthdayAutomationNotBeforeMs: fence.birthdayAutomationNotBeforeMs,
    createdAtMs: fence.createdAtMs,
    updatedAtMs: nowMs,
  };
  return {
    kind: 'COMPLETED',
    fence: completedFence,
    oldInstallation: {
      ...oldInstallation,
      state: 'REVOKED',
      lastSeenAtMs: nowMs,
      cleanupAtMs: cleanupForInstallation('REVOKED', nowMs),
    },
    targetInstallation: {
      ...targetInstallation,
      state: 'ACTIVE',
      epoch: senderEpoch,
      lastSeenAtMs: nowMs,
      cleanupAtMs: undefined,
    },
  };
}

export type DeletionDecision =
  | {
      readonly kind: 'STARTED';
      readonly tombstone: DeletionTombstone;
      readonly fence: AccountFence | null;
    }
  | {
      readonly kind: 'READY_TO_PURGE';
      readonly tombstone: DeletionTombstone;
      readonly fence: AccountFence | null;
      readonly activeInstallation: Installation | null;
    }
  | {
      readonly kind: 'REPLAYED';
      readonly tombstone: DeletionTombstone;
      readonly fence: AccountFence | null;
    }
  | {
      readonly kind: 'REFUSED';
      readonly reason:
        | 'COORDINATION_OPERATION_IN_PROGRESS'
        | 'REQUEST_MISMATCH';
    }
  | { readonly kind: 'WAIT'; readonly drainUntilMs: number };

export function decideBeginDeletion(
  existingTombstone: DeletionTombstone | null,
  fence: AccountFence | null,
  requestKey: string,
  nowMs: number,
): DeletionDecision {
  if (existingTombstone !== null) {
    if (existingTombstone.requestKey !== requestKey) {
      return { kind: 'REFUSED', reason: 'REQUEST_MISMATCH' };
    }
    if (existingTombstone.stage !== 'DRAINING') {
      return {
        kind: 'REPLAYED',
        tombstone: existingTombstone,
        fence,
      };
    }
    const drainUntilMs = Math.max(
      existingTombstone.drainUntilMs,
      fence?.latestIssuedSubmitNotAfterMs ?? existingTombstone.drainUntilMs,
    );
    return {
      kind: 'STARTED',
      tombstone: {
        ...existingTombstone,
        drainUntilMs,
        nextSweepAtMs: safeAddMs(drainUntilMs, 1),
        sweepAttemptCount: 0,
        updatedAtMs: nowMs,
      },
      fence:
        fence === null
          ? null
          : {
              ...fence,
              mode: 'DELETING',
              ownerLeaseUntilMs: nowMs,
              deletionDrainUntilMs: drainUntilMs,
              updatedAtMs: nowMs,
            },
    };
  }
  const drainUntilMs = Math.max(
    nowMs,
    fence?.latestIssuedSubmitNotAfterMs ?? nowMs,
  );
  const tombstone: DeletionTombstone = {
    schemaVersion: SCHEMA_VERSION,
    requestKey,
    stage: 'DRAINING',
    drainUntilMs,
    nextSweepAtMs: safeAddMs(drainUntilMs, 1),
    sweepAttemptCount: 0,
    createdAtMs: nowMs,
    updatedAtMs: nowMs,
  };
  const deletingFence =
    fence === null
      ? null
      : {
          ...fence,
          mode: 'DELETING' as const,
          ownerLeaseUntilMs: nowMs,
          deletionDrainUntilMs: drainUntilMs,
          updatedAtMs: nowMs,
        };
  return { kind: 'STARTED', tombstone, fence: deletingFence };
}

export function decideAdvanceDeletionDrain(
  tombstone: DeletionTombstone,
  fence: AccountFence | null,
  activeInstallation: Installation | null,
  nowMs: number,
): DeletionDecision {
  if (nowMs <= tombstone.drainUntilMs) {
    return { kind: 'WAIT', drainUntilMs: tombstone.drainUntilMs };
  }
  const purgingTombstone: DeletionTombstone = {
    ...tombstone,
    stage: 'PURGING',
    nextSweepAtMs: nowMs,
    sweepAttemptCount: 0,
    updatedAtMs: nowMs,
  };
  const revoked =
    activeInstallation === null
      ? null
      : {
          ...activeInstallation,
          state: 'REVOKED' as const,
          epoch: activeInstallation.epoch + 1,
          lastSeenAtMs: nowMs,
          cleanupAtMs: cleanupForInstallation('REVOKED', nowMs),
        };
  const drainedFence =
    fence === null
      ? null
      : {
          ...fence,
          ownerLeaseUntilMs: nowMs,
          senderEpoch: fence.senderEpoch + 1,
          updatedAtMs: nowMs,
        };
  return {
    kind: 'READY_TO_PURGE',
    tombstone: purgingTombstone,
    fence: drainedFence,
    activeInstallation: revoked,
  };
}

