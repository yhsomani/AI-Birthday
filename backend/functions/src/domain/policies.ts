import {
  ARM_SPACING_MS,
  BIRTHDAY_ARM_CAP,
  BIRTHDAY_RETENTION_MS,
  BUDGET_WINDOW_MS,
  DAY_MS,
  HOUR_MS,
  MAX_LEASE_MS,
  MAX_SUBMIT_AFTER_ARM_MS,
  MINUTE_MS,
  NEVER_ARMED_CLEANUP_MS,
  REVOKED_RETENTION_MS,
  SCHEMA_VERSION,
  STANDBY_RETENTION_MS,
  TEST_ARM_CAP,
  TEST_RETENTION_MS,
  type AccountFence,
  type ArmBudget,
  type ArmedOutcome,
  type BindingInput,
  type Claim,
  type DestinationGuard,
  type GlobalControl,
  type Installation,
  type NoWriteOutcome,
  type NoWriteReason,
  type Purpose,
} from './model.js';

export function safeAddMs(base: number, delta: number): number {
  if (
    !Number.isSafeInteger(base) ||
    !Number.isSafeInteger(delta) ||
    delta < 0
  ) {
    throw new Error('INVALID_TIME');
  }
  const result = base + delta;
  if (!Number.isSafeInteger(result)) {
    throw new Error('TIME_OVERFLOW');
  }
  return result;
}

export function boundedSweepAttempt(current: number): number {
  if (!Number.isSafeInteger(current) || current < 0) {
    return 1;
  }
  return Math.min(30, current + 1);
}

export function nextRepairSweepAtMs(
  nowMs: number,
  currentAttempt: number,
): number {
  const exponent = Math.min(6, Math.max(0, currentAttempt));
  return safeAddMs(nowMs, Math.min(HOUR_MS, MINUTE_MS * 2 ** exponent));
}

export function retentionMs(purpose: Purpose): number {
  return purpose === 'BIRTHDAY' ? BIRTHDAY_RETENTION_MS : TEST_RETENTION_MS;
}

export function capForPurpose(purpose: Purpose): number {
  return purpose === 'BIRTHDAY' ? BIRTHDAY_ARM_CAP : TEST_ARM_CAP;
}

export function pruneBudgetEntries(
  entries: ArmBudget['entries'],
  nowMs: number,
): ArmBudget['entries'] {
  const cutoff = nowMs - BUDGET_WINDOW_MS;
  return entries
    .filter(entry => entry.armedAtMs > cutoff && entry.armedAtMs <= nowMs)
    .sort((left, right) => left.armedAtMs - right.armedAtMs);
}

export function appendBudgetEntry(
  budget: ArmBudget | null,
  purpose: Purpose,
  entryId: string,
  nowMs: number,
): ArmBudget | null {
  const retained = pruneBudgetEntries(budget?.entries ?? [], nowMs);
  if (retained.some(entry => entry.id === entryId)) {
    const newest = retained.at(-1)?.armedAtMs ?? nowMs;
    return {
      schemaVersion: SCHEMA_VERSION,
      purpose,
      entries: retained,
      newestEntryAtMs: newest,
      cleanupAtMs: safeAddMs(newest, BUDGET_WINDOW_MS),
    };
  }
  if (retained.length >= capForPurpose(purpose)) {
    return null;
  }
  const entries = [...retained, { id: entryId, armedAtMs: nowMs }];
  return {
    schemaVersion: SCHEMA_VERSION,
    purpose,
    entries,
    newestEntryAtMs: nowMs,
    cleanupAtMs: safeAddMs(nowMs, BUDGET_WINDOW_MS),
  };
}

export function checkGlobalControl(
  control: GlobalControl | null,
  binding: Pick<
    BindingInput,
    | 'ledgerGeneration'
    | 'appBuildNumber'
    | 'policyVersion'
    | 'distributionChannel'
  >,
): NoWriteReason | null {
  if (control?.continuityState !== 'HEALTHY') {
    return 'CONTINUITY_UNAVAILABLE';
  }
  if (control.ledgerGeneration !== binding.ledgerGeneration) {
    return 'LEDGER_GENERATION_MISMATCH';
  }
  if (!control.armingEnabled) {
    return 'GLOBAL_ARMING_DISABLED';
  }
  if (binding.appBuildNumber < control.minimumBuildNumber) {
    return 'BUILD_UNSUPPORTED';
  }
  if (binding.policyVersion < control.minimumPolicyVersion) {
    return 'POLICY_UNSUPPORTED';
  }
  if (
    !control.allowedDistributionChannels.includes(binding.distributionChannel)
  ) {
    return 'CHANNEL_UNSUPPORTED';
  }
  return null;
}

export function checkControlCompatibility(
  control: GlobalControl | null,
  binding: Pick<
    BindingInput,
    | 'ledgerGeneration'
    | 'appBuildNumber'
    | 'policyVersion'
    | 'distributionChannel'
  >,
): Exclude<NoWriteReason, 'GLOBAL_ARMING_DISABLED'> | null {
  const result = checkGlobalControl(control, binding);
  return result === 'GLOBAL_ARMING_DISABLED' ? null : result;
}

export function checkBinding(
  fence: AccountFence,
  installation: Installation | null,
  binding: BindingInput,
  purpose: Purpose,
  nowMs: number,
): NoWriteReason | null {
  if (fence.activeInstallationId !== binding.installationId) {
    return 'INSTALLATION_MISMATCH';
  }
  if (fence.senderEpoch !== binding.senderEpoch) {
    return 'EPOCH_MISMATCH';
  }
  if (fence.resetGeneration !== binding.resetGeneration) {
    return 'RESET_GENERATION_MISMATCH';
  }
  if (
    installation?.installationId !== binding.installationId ||
    installation.state !== 'ACTIVE'
  ) {
    return 'INSTALLATION_MISMATCH';
  }
  if (installation.epoch !== binding.senderEpoch) {
    return 'EPOCH_MISMATCH';
  }
  if (fence.ownerLeaseUntilMs <= nowMs) {
    return 'LEASE_EXPIRED';
  }
  const modeAllowsPurpose =
    purpose === 'BIRTHDAY'
      ? fence.mode === 'AUTOMATION_ACTIVE'
      : fence.mode === 'TEST_ONLY' || fence.mode === 'PAUSED_REPAIR';
  if (!modeAllowsPurpose) {
    return 'MODE_BLOCKED';
  }
  if (fence.nextArmNotBeforeMs > nowMs) {
    return 'TOO_EARLY';
  }
  if (purpose === 'BIRTHDAY' && fence.birthdayAutomationNotBeforeMs > nowMs) {
    return 'BIRTHDAY_RESET_FENCE';
  }
  return null;
}

export function renewedLeaseUntil(nowMs: number): number {
  return safeAddMs(nowMs, MAX_LEASE_MS);
}

export function makeNoWriteOutcome(
  claim: Claim,
  armRequestId: string,
  reason: NoWriteReason,
  nowMs: number,
): NoWriteOutcome {
  return {
    schemaVersion: SCHEMA_VERSION,
    armRequestId,
    purpose: claim.purpose,
    claimId: claim.claimId,
    ownerInstallationId: claim.ownerInstallationId,
    ownerEpoch: claim.ownerEpoch,
    resetGeneration: claim.resetGeneration,
    attempt: claim.attempt,
    kind: 'NO_WRITE',
    reason,
    resolvedAtMs: nowMs,
    cleanupAtMs: Math.max(
      claim.claimExpiresAtMs,
      safeAddMs(nowMs, NEVER_ARMED_CLEANUP_MS),
    ),
  };
}

export function makeArmedOutcome(
  claim: Claim,
  armRequestId: string,
  nowMs: number,
): ArmedOutcome {
  const serverSubmitNotAfterMs = Math.min(
    safeAddMs(nowMs, MAX_SUBMIT_AFTER_ARM_MS),
    claim.maxPossibleSubmitNotAfterMs,
  );
  return {
    schemaVersion: SCHEMA_VERSION,
    armRequestId,
    purpose: claim.purpose,
    claimId: claim.claimId,
    ownerInstallationId: claim.ownerInstallationId,
    ownerEpoch: claim.ownerEpoch,
    resetGeneration: claim.resetGeneration,
    attempt: claim.attempt,
    kind: 'ARMED',
    serverSubmitNotAfterMs,
    resolvedAtMs: nowMs,
    cleanupAtMs: safeAddMs(nowMs, retentionMs(claim.purpose)),
  };
}

export function armClaim(
  claim: Claim,
  outcome: ArmedOutcome,
  nowMs: number,
): Claim {
  return {
    ...claim,
    state: 'ARMED',
    serverSubmitNotAfterMs: outcome.serverSubmitNotAfterMs,
    updatedAtMs: nowMs,
    cleanupAtMs: outcome.cleanupAtMs,
  };
}

export function expireClaim(claim: Claim, nowMs: number): Claim {
  const isRetry = claim.state === 'RETRY_CLAIMED' || claim.attempt === 2;
  return {
    ...claim,
    state: isRetry ? 'RETRY_EXPIRED_NO_ARM' : 'EXPIRED_NO_ARM',
    updatedAtMs: nowMs,
    cleanupAtMs: Math.max(
      claim.cleanupAtMs,
      safeAddMs(nowMs, NEVER_ARMED_CLEANUP_MS),
    ),
  };
}

export function expireGuard(
  guard: DestinationGuard,
  claim: Claim,
  nowMs: number,
): DestinationGuard {
  if (claim.state === 'RETRY_CLAIMED' || claim.attempt === 2) {
    return guard;
  }
  return {
    ...guard,
    state: 'EXPIRED_NO_ARM_RECLAIMABLE',
    updatedAtMs: nowMs,
    cleanupAtMs: Math.max(
      guard.cleanupAtMs,
      safeAddMs(nowMs, NEVER_ARMED_CLEANUP_MS),
    ),
  };
}

export function armGuard(
  guard: DestinationGuard,
  outcome: ArmedOutcome,
  nowMs: number,
): DestinationGuard {
  return {
    ...guard,
    state: 'ARMED',
    updatedAtMs: nowMs,
    cleanupAtMs: outcome.cleanupAtMs,
  };
}

export function advanceFenceAfterArm(
  fence: AccountFence,
  outcome: ArmedOutcome,
  nowMs: number,
): AccountFence {
  return {
    ...fence,
    nextArmNotBeforeMs: Math.max(
      fence.nextArmNotBeforeMs,
      safeAddMs(outcome.serverSubmitNotAfterMs, ARM_SPACING_MS),
    ),
    latestIssuedSubmitNotAfterMs: Math.max(
      fence.latestIssuedSubmitNotAfterMs,
      outcome.serverSubmitNotAfterMs,
    ),
    updatedAtMs: nowMs,
  };
}

export function cleanupForInstallation(
  state: Installation['state'],
  nowMs: number,
): number | undefined {
  if (state === 'STANDBY') {
    return safeAddMs(nowMs, STANDBY_RETENTION_MS);
  }
  if (state === 'REVOKED') {
    return safeAddMs(nowMs, REVOKED_RETENTION_MS);
  }
  return undefined;
}

export function worstCaseSameDateResetRelease(nowMs: number): number {
  return safeAddMs(nowMs, DAY_MS);
}
