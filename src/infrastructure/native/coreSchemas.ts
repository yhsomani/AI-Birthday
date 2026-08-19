import { z } from 'zod';

import { APPROVAL_INVALIDATION_REASONS } from '../../domain/approvals/model';
import { PROJECTION_AREAS } from '../../application/ports/AppProjectionPort';
import { SETUP_STEPS } from '../../domain/setup/model';
import {
  actionHandleSchema,
  approvalReviewHandleSchema,
  boundedUiTextSchema,
  contactIdSchema,
  issueIdSchema,
  maskedPhoneSchema,
  nativeRevisionSchema,
  privateDisplayNameSchema,
  privateEmailSchema,
  privateMessageTextSchema,
  safeReasonCodeSchema,
  strictObject,
  utcInstantSchema,
} from './schemaPrimitives';

const boundedLabel = boundedUiTextSchema(512);
const boundedShortLabel = boundedUiTextSchema(128);
const boundedCount = z.number().int().nonnegative().max(1_000_000);

export const platformCapabilitySchema = strictObject({
  platform: z.literal('android'),
  deliveryMode: z.literal('unattended-device-sms'),
  minimumApiLevel: z.literal(29),
  unattendedSms: z.literal('release-gated'),
  userComposer: z.literal('available-as-explicit-alternative'),
});

export const readinessIssueSchema = strictObject({
  id: issueIdSchema,
  code: safeReasonCodeSchema,
  severity: z.enum(['info', 'warning', 'blocking']),
  blocks: z
    .array(z.enum(['test', 'activation', 'birthday']))
    .min(1)
    .max(3),
  action: strictObject({
    kind: z.literal('native-action'),
    handle: actionHandleSchema,
    labelKey: boundedShortLabel,
  }).optional(),
});

export const gateDecisionSchema = z.discriminatedUnion('kind', [
  strictObject({ kind: z.literal('checking') }),
  strictObject({ kind: z.literal('allowed') }),
  strictObject({
    kind: z.literal('blocked'),
    issues: z.array(readinessIssueSchema).min(1).max(64),
  }),
]);

export const androidReadinessProjectionSchema = strictObject({
  platform: z.literal('android'),
  test: gateDecisionSchema,
  activation: gateDecisionSchema,
  birthday: gateDecisionSchema,
  lastCheckedAt: utcInstantSchema,
});

export const readinessProjectionSchema = androidReadinessProjectionSchema;

const eligibilityIssueSet = {
  primaryIssue: readinessIssueSchema,
  otherIssues: z.array(readinessIssueSchema).max(63),
};

export const deviceEligibilitySchema = z.discriminatedUnion('kind', [
  strictObject({
    kind: z.literal('checking'),
    capability: platformCapabilitySchema,
  }),
  strictObject({
    kind: z.literal('supported'),
    capability: platformCapabilitySchema,
    channelLabel: boundedShortLabel,
    chargeDisclosureVersion: boundedShortLabel,
  }),
  strictObject({
    kind: z.literal('limited'),
    capability: platformCapabilitySchema,
    ...eligibilityIssueSet,
  }),
  strictObject({
    kind: z.literal('unsupported'),
    capability: platformCapabilitySchema,
    ...eligibilityIssueSet,
  }),
]);

export const senderProjectionSchema = z.union([
  strictObject({
    platform: z.literal('android'),
    kind: z.literal('test-only'),
    epochLabel: boundedShortLabel,
  }),
  strictObject({
    platform: z.literal('android'),
    kind: z.literal('paused-repair'),
    epochLabel: boundedShortLabel,
  }),
  strictObject({
    platform: z.literal('android'),
    kind: z.literal('automation-active'),
    epochLabel: boundedShortLabel,
  }),
  strictObject({
    platform: z.literal('android'),
    kind: z.literal('standby'),
    activeOtherDeviceLabel: boundedShortLabel,
  }),
  strictObject({
    platform: z.literal('android'),
    kind: z.literal('transfer-pending'),
    preissuedPermitMayFinish: z.boolean(),
    drainUntil: utcInstantSchema.optional(),
  }),
  strictObject({
    platform: z.literal('android'),
    kind: z.literal('deleting'),
    preissuedPermitMayFinish: z.boolean(),
    drainUntil: utcInstantSchema.optional(),
  }),
]);

export const accountProjectionSchema = z.discriminatedUnion('kind', [
  strictObject({
    kind: z.literal('signed-out'),
    retainedSetup: z.enum(['none', 'same-account-only']),
  }),
  strictObject({ kind: z.literal('connecting') }),
  strictObject({
    kind: z.literal('connected'),
    displayEmail: privateEmailSchema,
    sender: senderProjectionSchema,
  }),
  strictObject({
    kind: z.literal('reconnect-required'),
    issue: readinessIssueSchema,
  }),
  strictObject({
    kind: z.literal('cleanup-pending'),
    operation: z.enum(['disconnect', 'revoke', 'sign-out', 'delete', 'repair']),
    issue: readinessIssueSchema,
  }),
]);

export const approvalProjectionSchema = z.discriminatedUnion('kind', [
  strictObject({ kind: z.literal('missing') }),
  strictObject({ kind: z.literal('valid'), approvedAt: utcInstantSchema }),
  strictObject({
    kind: z.literal('invalidated'),
    reasons: z
      .array(z.enum(APPROVAL_INVALIDATION_REASONS))
      .min(1)
      .max(APPROVAL_INVALIDATION_REASONS.length),
  }),
]);

export const androidApprovalReviewSchema = strictObject({
  platform: z.literal('android'),
  handle: approvalReviewHandleSchema,
  contactId: contactIdSchema,
  recipient: privateDisplayNameSchema,
  maskedPhone: maskedPhoneSchema,
  birthdayLabel: boundedShortLabel,
  exactText: privateMessageTextSchema,
  windowLabel: boundedShortLabel,
  simLabel: boundedShortLabel,
  segmentCount: z.number().int().min(1).max(2),
  chargeDisclosure: boundedLabel,
  consentDisclosure: boundedLabel,
});

export const androidApprovalReviewItemSchema = androidApprovalReviewSchema.omit(
  {
    handle: true,
  },
);

export const approvalReviewSchema = androidApprovalReviewSchema;

export const approvalBatchReviewSchema = strictObject({
  handle: approvalReviewHandleSchema,
  items: z.array(androidApprovalReviewItemSchema).max(50),
  readyCount: boundedCount.max(50),
  blockedCount: boundedCount.max(50),
  explicitConfirmationRequired: z.literal(true),
});

export const projectionInvalidationSchema = strictObject({
  revision: nativeRevisionSchema,
  areas: z.array(z.enum(PROJECTION_AREAS)).min(1).max(PROJECTION_AREAS.length),
});

export const setupStepSchema = z.enum(SETUP_STEPS);
