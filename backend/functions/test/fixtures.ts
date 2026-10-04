import {
  SCHEMA_VERSION,
  type AccountFence,
  type ArmBudget,
  type BindingInput,
  type Claim,
  type GlobalControl,
  type Installation,
  type Purpose,
} from '../src/domain/model.js';

export const NOW_MS = 1_800_000_000_000;
export const INSTALLATION_ID = 'a'.repeat(32);
export const SECOND_INSTALLATION_ID = 'b'.repeat(32);

export function globalControl(
  overrides: Partial<GlobalControl> = {},
): GlobalControl {
  return {
    schemaVersion: SCHEMA_VERSION,
    armingEnabled: true,
    continuityState: 'HEALTHY',
    ledgerGeneration: 'ledger-generation-1',
    minimumBuildNumber: 100,
    minimumPolicyVersion: 7,
    allowedDistributionChannels: ['PLAY', 'DIRECT_MANAGED'],
    reasonCode: 'OK',
    updatedAtMs: NOW_MS,
    ...overrides,
  };
}

export function binding(overrides: Partial<BindingInput> = {}): BindingInput {
  return {
    ledgerGeneration: 'ledger-generation-1',
    installationId: INSTALLATION_ID,
    senderEpoch: 4,
    resetGeneration: 3,
    appBuildNumber: 100,
    policyVersion: 7,
    distributionChannel: 'PLAY',
    ...overrides,
  };
}

export function fence(overrides: Partial<AccountFence> = {}): AccountFence {
  return {
    schemaVersion: SCHEMA_VERSION,
    mode: 'AUTOMATION_ACTIVE',
    activeInstallationId: INSTALLATION_ID,
    senderEpoch: 4,
    ownerLeaseUntilMs: NOW_MS + 60_000,
    nextArmNotBeforeMs: NOW_MS,
    latestIssuedSubmitNotAfterMs: NOW_MS,
    resetGeneration: 3,
    birthdayAutomationNotBeforeMs: NOW_MS,
    createdAtMs: NOW_MS - 1_000,
    updatedAtMs: NOW_MS,
    ...overrides,
  };
}

export function installation(
  overrides: Partial<Installation> = {},
): Installation {
  return {
    schemaVersion: SCHEMA_VERSION,
    installationId: INSTALLATION_ID,
    state: 'ACTIVE',
    epoch: 4,
    appBuildNumber: 100,
    policyVersion: 7,
    distributionChannel: 'PLAY',
    lastSeenAtMs: NOW_MS,
    ...overrides,
  };
}

export function claim(
  purpose: Purpose = 'BIRTHDAY',
  overrides: Partial<Claim> = {},
): Claim {
  return {
    schemaVersion: SCHEMA_VERSION,
    claimId: 'v1.claim-key',
    purpose,
    claimRequestId: '00000000-0000-4000-8000-000000000001',
    ownerInstallationId: INSTALLATION_ID,
    ownerEpoch: 4,
    resetGeneration: 3,
    state: 'CLAIMED',
    attempt: 1,
    retryAuthorizationGeneration: 0,
    claimExpiresAtMs: NOW_MS + 10 * 60_000,
    maxPossibleSubmitNotAfterMs: NOW_MS + 11 * 60_000,
    occurrenceAliasKeys: purpose === 'BIRTHDAY' ? ['v1.occurrence'] : [],
    destinationAliasKeys: purpose === 'BIRTHDAY' ? ['v1.destination'] : [],
    testMaterialAliasKeys: purpose === 'TEST' ? ['v1.test-material'] : [],
    createdAtMs: NOW_MS,
    updatedAtMs: NOW_MS,
    cleanupAtMs: NOW_MS + 24 * 60 * 60_000,
    ...overrides,
  };
}

export function budget(
  purpose: Purpose,
  entries: ArmBudget['entries'],
): ArmBudget {
  const newestEntryAtMs = entries.at(-1)?.armedAtMs ?? NOW_MS;
  return {
    schemaVersion: SCHEMA_VERSION,
    purpose,
    entries,
    newestEntryAtMs,
    cleanupAtMs: newestEntryAtMs + 24 * 60 * 60_000,
  };
}
