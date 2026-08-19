import { z } from 'zod';

const contractVersion = z.literal(1);
const ledgerGeneration = z
  .string()
  .min(8)
  .max(64)
  .regex(/^[a-zA-Z0-9._-]+$/u);
const installationId = z.string().regex(/^[a-f0-9]{32}$/u);
const positiveVersion = z.number().int().positive().max(2_147_483_647);
const senderEpoch = z.number().int().positive().max(Number.MAX_SAFE_INTEGER);
const resetGeneration = z
  .number()
  .int()
  .positive()
  .max(Number.MAX_SAFE_INTEGER);
const distributionChannel = z.enum([
  'DEV',
  'STAGING',
  'RESTRICTED_LAB',
  'PLAY',
  'DIRECT_MANAGED',
]);
const uuid = z.uuid();
const stableRequestId = z
  .string()
  .regex(
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/u,
  );
const canonicalLowercaseUUID = z
  .string()
  .regex(
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/u,
  );
const deletionReceiptId = canonicalLowercaseUUID;
const sha256Hex = z.string().regex(/^[a-f0-9]{64}$/u);
const opaqueKey = z
  .string()
  .min(10)
  .max(80)
  .regex(/^[A-Za-z0-9._-]+$/u);

const binding = {
  contractVersion,
  ledgerGeneration,
  installationId,
  senderEpoch,
  resetGeneration,
  appBuildNumber: positiveVersion,
  policyVersion: positiveVersion,
  distributionChannel,
} as const;

export const registrationSchema = z
  .object({
    contractVersion,
    ledgerGeneration,
    installationId,
    appBuildNumber: positiveVersion,
    policyVersion: positiveVersion,
    distributionChannel,
  })
  .strict();

export const leaseSchema = z
  .object({
    ...binding,
    purpose: z.enum(['BIRTHDAY', 'TEST']),
  })
  .strict();

export const birthdayClaimSchema = z
  .object({
    ...binding,
    purpose: z.literal('BIRTHDAY'),
    claimRequestId: uuid,
    recipientPrehashAliases: z.array(sha256Hex).min(1).max(2),
    destinationPrehashAliases: z.array(sha256Hex).min(1).max(2),
  })
  .strict();

export const testClaimSchema = z
  .object({
    ...binding,
    purpose: z.literal('TEST'),
    testRequestId: uuid,
    testConfigurationPrehash: sha256Hex,
    testDestinationPrehash: sha256Hex,
  })
  .strict();

export const armSchema = z
  .object({
    ...binding,
    purpose: z.enum(['BIRTHDAY', 'TEST']),
    claimId: opaqueKey,
    armRequestId: uuid,
    attempt: z.union([z.literal(1), z.literal(2)]),
  })
  .strict();

export const retrySchema = z
  .object({
    ...binding,
    purpose: z.literal('BIRTHDAY'),
    claimId: opaqueKey,
    retryRequestId: uuid,
    proof: z.enum(['ALL_PARTS_RADIO_OFF', 'ALL_PARTS_NO_SERVICE', 'OTHER']),
  })
  .strict();

export const accountModeSchema = z.discriminatedUnion('action', [
  z
    .object({
      ...binding,
      action: z.literal('PAUSE_FOR_REPAIR'),
    })
    .strict(),
  z
    .object({
      ...binding,
      action: z.literal('ACTIVATE_AUTOMATION'),
      testClaimId: opaqueKey,
      boundTestReceiptPrehash: sha256Hex,
      readinessContractVersion: positiveVersion,
    })
    .strict(),
]);

export const testReportSchema = z
  .object({
    ...binding,
    purpose: z.literal('TEST'),
    testClaimId: opaqueKey,
    armRequestId: uuid,
    result: z.enum([
      'SENT_ALL_PARTS',
      'FAILED_ZERO_ACCEPTED',
      'FAILED_OR_UNKNOWN',
      'CLEANUP_CANCELLED',
    ]),
  })
  .strict();

export const transferSchema = z
  .object({
    ...binding,
    targetInstallationId: installationId,
  })
  .strict();

export const deletionSchema = z
  .object({
    contractVersion,
    requestId: deletionReceiptId,
  })
  .strict();

export const deletionReceiptSchema = z
  .object({
    contractVersion,
    receiptId: deletionReceiptId,
  })
  .strict();

export const contactDerivedResetSchema = z
  .object({
    contractVersion,
    requestId: stableRequestId,
  })
  .strict();

export const senderReleaseSchema = z
  .object({
    contractVersion,
    requestId: stableRequestId,
    installationId,
    senderEpoch,
    resetGeneration,
  })
  .strict();

export const coordinationLifecycleStatusSchema = z
  .object({
    contractVersion,
  })
  .strict();

export type RegistrationRequest = z.infer<typeof registrationSchema>;
export type LeaseRequest = z.infer<typeof leaseSchema>;
export type BirthdayClaimRequest = z.infer<typeof birthdayClaimSchema>;
export type TestClaimRequest = z.infer<typeof testClaimSchema>;
export type ArmRequest = z.infer<typeof armSchema>;
export type RetryRequest = z.infer<typeof retrySchema>;
export type AccountModeRequest = z.infer<typeof accountModeSchema>;
export type TestReportRequest = z.infer<typeof testReportSchema>;
export type TransferRequest = z.infer<typeof transferSchema>;
export type DeletionRequest = z.infer<typeof deletionSchema>;
export type DeletionReceiptRequest = z.infer<typeof deletionReceiptSchema>;
export type ContactDerivedResetRequest = z.infer<
  typeof contactDerivedResetSchema
>;
export type SenderReleaseRequest = z.infer<typeof senderReleaseSchema>;
export type CoordinationLifecycleStatusRequest = z.infer<
  typeof coordinationLifecycleStatusSchema
>;

