import { z } from 'zod';

import { ACTIVITY_KINDS } from '../../domain/activity/model';
import {
  ANDROID_BIRTHDAY_JOB_PHASES,
  ANDROID_TEST_PHASES,
  IOS_BIRTHDAY_JOB_PHASES,
} from '../../domain/automation/model';
import { CONTACT_ISSUE_CODES } from '../../domain/contacts/model';
import { MESSAGE_LANGUAGES, MESSAGE_TONES } from '../../domain/messages/model';
import { PRIVACY_ACTION_KINDS } from '../../domain/privacy/model';
import {
  accountProjectionSchema,
  androidReadinessProjectionSchema,
  approvalProjectionSchema,
  deviceEligibilitySchema,
  iosReadinessProjectionSchema,
  platformCapabilitySchema,
  readinessProjectionSchema,
  setupStepSchema,
} from './coreSchemas';
import {
  activationReviewHandleSchema,
  activityIdSchema,
  birthdayChoiceIdSchema,
  boundedUiTextSchema,
  contactIdSchema,
  composerProposalIdSchema,
  deletionPrivacyOperationIdSchema,
  enrollmentReviewHandleSchema,
  fieldIssueSchema,
  localDateSchema,
  localTimeSchema,
  maskedPhoneSchema,
  messagePreviewHandleSchema,
  nativeRouteIdSchema,
  occurrenceIdSchema,
  pageCursorSchema,
  phoneChoiceIdSchema,
  policyReviewHandleSchema,
  privateDisplayNameSchema,
  privateMessageTextSchema,
  privacyOperationIdSchema,
  privacyReviewHandleSchema,
  safeReasonCodeSchema,
  senderTransferOperationIdSchema,
  senderTransferReviewHandleSchema,
  strictObject,
  testReviewHandleSchema,
  todayOccurrenceReviewHandleSchema,
  utcInstantSchema,
} from './schemaPrimitives';

const boundedShortLabel = boundedUiTextSchema(128);
const boundedLabel = boundedUiTextSchema(512);
const boundedDisclosure = boundedUiTextSchema(2_000);
const boundedCount = z.number().int().nonnegative().max(1_000_000);
const publicResourceBaseUrlSchema = z
  .string()
  .max(128)
  .regex(/^https:\/\/[a-z][a-z0-9-]{4,28}[a-z0-9]\.web\.app$/u);

export const publicResourcesProjectionSchema = z.discriminatedUnion('kind', [
  strictObject({
    kind: z.literal('available'),
    buildLabel: boundedShortLabel,
    baseUrl: publicResourceBaseUrlSchema,
  }),
  strictObject({
    kind: z.literal('unavailable'),
    buildLabel: boundedShortLabel,
  }),
]);

export const nativeRouteProjectionSchema = z.discriminatedUnion('kind', [
  strictObject({ kind: z.literal('none') }),
  strictObject({
    kind: z.literal('automation-review'),
    routeId: nativeRouteIdSchema,
    source: z.literal('birthday-reminder'),
  }),
  strictObject({
    kind: z.literal('attention'),
    routeId: nativeRouteIdSchema,
    source: z.literal('attention'),
  }),
]);

export const nativeRouteAvailableSchema = strictObject({
  kind: z.literal('available'),
});

export const syncProjectionSchema = z.discriminatedUnion('kind', [
  strictObject({ kind: z.literal('never-synced') }),
  strictObject({
    kind: z.literal('syncing'),
    mode: z.enum(['full', 'incremental']),
    retainedGeneration: z.boolean(),
  }),
  strictObject({
    kind: z.literal('fresh'),
    completedAt: utcInstantSchema,
    contactCount: boundedCount,
  }),
  strictObject({
    kind: z.literal('stale'),
    lastSuccessAt: utcInstantSchema,
    reason: safeReasonCodeSchema,
  }),
  strictObject({
    kind: z.literal('failed-retained'),
    lastSuccessAt: utcInstantSchema.optional(),
    reason: safeReasonCodeSchema,
  }),
  strictObject({
    kind: z.literal('authorization-required'),
    reason: z.literal('contacts-authorization-required'),
  }),
]);

export const contactReadinessSchema = z.discriminatedUnion('kind', [
  strictObject({ kind: z.literal('ready') }),
  strictObject({
    kind: z.literal('needs-attention'),
    reasons: z
      .array(z.enum(CONTACT_ISSUE_CODES))
      .min(1)
      .max(CONTACT_ISSUE_CODES.length),
  }),
  strictObject({
    kind: z.literal('unavailable'),
    reasons: z
      .array(z.enum(CONTACT_ISSUE_CODES))
      .min(1)
      .max(CONTACT_ISSUE_CODES.length),
  }),
]);

export const enrollmentProjectionSchema = z.discriminatedUnion('kind', [
  strictObject({ kind: z.literal('off') }),
  strictObject({
    kind: z.literal('enabled'),
    approval: approvalProjectionSchema,
  }),
  strictObject({
    kind: z.literal('paused'),
    reason: safeReasonCodeSchema,
    approval: approvalProjectionSchema,
  }),
  strictObject({
    kind: z.literal('excluded'),
    reason: safeReasonCodeSchema.optional(),
  }),
]);

export const contactSummarySchema = strictObject({
  id: contactIdSchema,
  displayName: privateDisplayNameSchema,
  birthdayLabel: boundedShortLabel.optional(),
  maskedPhone: maskedPhoneSchema.optional(),
  readiness: contactReadinessSchema,
  enrollment: enrollmentProjectionSchema,
});

export const contactPhoneChoiceSchema = strictObject({
  id: phoneChoiceIdSchema,
  maskedDisplay: maskedPhoneSchema,
  sourceLabel: boundedShortLabel,
  selectable: z.boolean(),
  issue: z.enum(CONTACT_ISSUE_CODES).optional(),
});

export const birthdayChoiceSchema = strictObject({
  id: birthdayChoiceIdSchema,
  displayLabel: boundedShortLabel,
  hasYear: z.boolean(),
  selectable: z.boolean(),
  issue: z.enum(CONTACT_ISSUE_CODES).optional(),
});

export const contactDetailSchema = strictObject({
  summary: contactSummarySchema,
  phoneChoices: z.array(contactPhoneChoiceSchema).max(20),
  birthdayChoices: z.array(birthdayChoiceSchema).max(20),
  selectedPhoneId: phoneChoiceIdSchema.optional(),
  selectedBirthdayId: birthdayChoiceIdSchema.optional(),
  nextOccurrenceLabel: boundedShortLabel.optional(),
  lastOutcomeLabel: boundedLabel.optional(),
});

export const peoplePageSchema = strictObject({
  items: z.array(contactSummarySchema).max(50),
  nextCursor: pageCursorSchema.optional(),
  totalCount: boundedCount,
});

export const enrollmentReviewSchema = strictObject({
  handle: enrollmentReviewHandleSchema,
  recipients: z.array(contactSummarySchema).min(1).max(50),
  readyCount: boundedCount.max(50),
  attentionCount: boundedCount.max(50),
  explicitConfirmationRequired: z.literal(true),
});

export const peopleMutationProjectionSchema = strictObject({
  changedContactIds: z.array(contactIdSchema).max(50),
  invalidatedApprovalCount: boundedCount,
});

export const placeholderModeSchema = z.discriminatedUnion('kind', [
  strictObject({ kind: z.literal('given-name'), requiredCount: z.literal(1) }),
  strictObject({ kind: z.literal('generic'), requiredCount: z.literal(0) }),
]);

export const messageDraftSchema = strictObject({
  language: z.enum(MESSAGE_LANGUAGES),
  tone: z.enum(MESSAGE_TONES),
  placeholderMode: placeholderModeSchema,
  text: privateMessageTextSchema,
  requestedSegmentCap: z.union([z.literal(1), z.literal(2)]),
});

export const messagePreviewSchema = z.discriminatedUnion('kind', [
  strictObject({
    kind: z.literal('valid'),
    handle: messagePreviewHandleSchema,
    examples: z
      .array(
        strictObject({
          displayName: privateDisplayNameSchema,
          finalText: privateMessageTextSchema,
          characterCount: z.number().int().nonnegative().max(1_000),
          segmentCount: z.number().int().min(1).max(2),
          encodingLabel: z.enum(['gsm-7', 'unicode']),
        }),
      )
      .max(3),
    maximumSegmentCount: z.number().int().min(1).max(2),
    affectedRecipientCount: boundedCount,
  }),
  strictObject({
    kind: z.literal('invalid'),
    issues: z.array(fieldIssueSchema).min(1).max(32),
    affectedRecipientCount: boundedCount,
  }),
]);

export const savedMessageProjectionSchema = strictObject({
  draft: messageDraftSchema,
  affectedRecipientCount: boundedCount,
  invalidatedApprovalCount: boundedCount,
});

export const messageEditorProjectionSchema = z.discriminatedUnion('kind', [
  strictObject({ kind: z.literal('not-configured') }),
  strictObject({
    kind: z.literal('configured'),
    draft: messageDraftSchema,
  }),
]);

export const iosComposerProposalProjectionSchema = z.discriminatedUnion(
  'kind',
  [
    strictObject({ kind: z.literal('none') }),
    strictObject({
      kind: z.literal('ready'),
      proposalId: composerProposalIdSchema,
      occurrenceId: occurrenceIdSchema,
      occurrenceDate: localDateSchema,
      recipient: privateDisplayNameSchema,
    }),
  ],
);

export const geminiSuggestionsProjectionSchema = z.discriminatedUnion('kind', [
  strictObject({ kind: z.literal('requesting') }),
  strictObject({
    kind: z.literal('candidates'),
    candidates: z.array(privateMessageTextSchema).min(1).max(3),
  }),
  strictObject({
    kind: z.literal('fallback'),
    reason: z.enum([
      'network-offline',
      'coordination-unavailable',
      'policy-suspended',
    ]),
  }),
  strictObject({
    kind: z.literal('failed'),
    reason: z.enum(['unknown-native-value', 'internal-contract-invalid']),
  }),
]);

export const latePolicySchema = z.discriminatedUnion('kind', [
  strictObject({ kind: z.literal('none') }),
  strictObject({
    kind: z.literal('same-day-grace'),
    graceEnd: localTimeSchema,
  }),
]);

export const windowDraftSchema = strictObject({
  primaryStart: localTimeSchema,
  primaryEnd: localTimeSchema,
  latePolicy: latePolicySchema,
  dailyCap: z.number().int().min(1).max(20),
});

export const policyEditorProjectionSchema = z.discriminatedUnion('kind', [
  strictObject({ kind: z.literal('not-configured') }),
  strictObject({
    kind: z.literal('configured'),
    draft: windowDraftSchema,
  }),
]);

export const policyPreviewSchema = z.discriminatedUnion('kind', [
  strictObject({
    kind: z.literal('valid'),
    handle: policyReviewHandleSchema,
    summary: boundedLabel,
    simulatedDays: z.literal(400),
    maximumPlannedInLocalDay: z.number().int().nonnegative().max(20),
    maximumPlannedInRolling24Hours: z.number().int().nonnegative().max(20),
  }),
  strictObject({
    kind: z.literal('invalid'),
    issues: z.array(fieldIssueSchema).min(1).max(32),
    firstConflictDate: localDateSchema.optional(),
  }),
]);

export const birthdayJobProjectionSchema = z.discriminatedUnion('platform', [
  strictObject({
    platform: z.literal('android'),
    occurrenceId: occurrenceIdSchema,
    occurrenceDate: localDateSchema,
    phase: z.enum(ANDROID_BIRTHDAY_JOB_PHASES),
    updatedAt: utcInstantSchema,
    attempt: z.union([z.literal(1), z.literal(2)]),
  }),
  strictObject({
    platform: z.literal('ios'),
    occurrenceId: occurrenceIdSchema,
    occurrenceDate: localDateSchema,
    phase: z.enum(IOS_BIRTHDAY_JOB_PHASES),
    updatedAt: utcInstantSchema,
  }),
]);

export const testProjectionSchema = z.discriminatedUnion('platform', [
  strictObject({
    platform: z.literal('android'),
    phase: z.enum(ANDROID_TEST_PHASES),
    updatedAt: utcInstantSchema,
    reason: safeReasonCodeSchema.optional(),
  }),
  strictObject({
    platform: z.literal('ios'),
    kind: z.literal('unavailable'),
    reason: z.literal('platform-composer-only'),
  }),
]);

export const automationProjectionSchema = z.discriminatedUnion('platform', [
  strictObject({
    platform: z.literal('android'),
    desired: z.enum(['on', 'paused']),
    effective: z.enum([
      'not-configured',
      'test-only',
      'paused-repair',
      'active',
      'action-required',
      'standby',
      'transfer-pending',
      'deleting',
    ]),
    readiness: androidReadinessProjectionSchema,
  }),
  strictObject({
    platform: z.literal('ios'),
    desired: z.enum(['composer-reminders-on', 'paused']),
    effective: z.enum(['not-configured', 'ready', 'action-required', 'paused']),
    readiness: iosReadinessProjectionSchema,
  }),
]);

export const testReviewSchema = z.discriminatedUnion('platform', [
  strictObject({
    platform: z.literal('android'),
    handle: testReviewHandleSchema,
    maskedDestination: maskedPhoneSchema,
    exactText: privateMessageTextSchema,
    simLabel: boundedShortLabel,
    segmentCount: z.number().int().min(1).max(2),
    chargeDisclosure: boundedDisclosure,
  }),
  strictObject({
    platform: z.literal('ios'),
    kind: z.literal('unavailable'),
    reason: z.literal('platform-composer-only'),
  }),
]);

export const activationReviewSchema = z.discriminatedUnion('platform', [
  strictObject({
    platform: z.literal('android'),
    handle: activationReviewHandleSchema,
    enabledRecipientCount: boundedCount,
    attentionCount: boundedCount,
    templatePreview: privateMessageTextSchema,
    windowLabel: boundedShortLabel,
    simLabel: boundedShortLabel,
    dailyCap: z.number().int().min(1).max(20),
    limitationsDisclosure: boundedDisclosure,
  }),
  strictObject({
    platform: z.literal('ios'),
    handle: activationReviewHandleSchema,
    reminderRecipientCount: boundedCount,
    deliveryMode: z.literal('user-controlled-composer'),
    limitationsDisclosure: boundedDisclosure,
  }),
]);

export const upcomingGreetingSchema = strictObject({
  occurrenceId: occurrenceIdSchema,
  recipient: privateDisplayNameSchema,
  localDate: localDateSchema,
  windowLabel: boundedShortLabel,
  maskedPhone: maskedPhoneSchema,
});

export const todayOccurrenceReviewSchema = strictObject({
  handle: todayOccurrenceReviewHandleSchema,
  recipient: privateDisplayNameSchema,
  exactText: privateMessageTextSchema,
  choice: z.enum(['send-through-normal-path', 'start-next-year']),
  limitationsDisclosure: boundedDisclosure,
});

export const homeProjectionSchema = strictObject({
  automation: automationProjectionSchema,
  next: upcomingGreetingSchema.optional(),
  counts: strictObject({
    enabled: boundedCount,
    needsAttention: boundedCount,
    unavailable: boundedCount,
    today: boundedCount,
    nextSevenDays: boundedCount,
  }),
  contactsSync: syncProjectionSchema,
  schedulerHeartbeatAt: utcInstantSchema.optional(),
  lastCoordinationSuccessAt: utcInstantSchema.optional(),
});

export const bootstrapProjectionSchema = strictObject({
  capability: platformCapabilitySchema,
  eligibility: deviceEligibilitySchema,
  account: accountProjectionSchema,
  setupStep: setupStepSchema,
});

export const setupProjectionSchema = strictObject({
  step: setupStepSchema,
  eligibility: deviceEligibilitySchema,
  account: accountProjectionSchema,
  contacts: syncProjectionSchema,
  readiness: readinessProjectionSchema,
  automation: automationProjectionSchema,
});

export const activityRecordSchema = strictObject({
  id: activityIdSchema,
  kind: z.enum(ACTIVITY_KINDS),
  reason: safeReasonCodeSchema.optional(),
  occurredAt: utcInstantSchema,
  actionable: z.boolean(),
});

export const activityPageSchema = strictObject({
  items: z.array(activityRecordSchema).max(50),
  nextCursor: pageCursorSchema.optional(),
});

export const diagnosticsPreviewSchema = strictObject({
  buildLabel: boundedShortLabel,
  androidOrIosVersionLabel: boundedShortLabel,
  capabilityCodes: z.array(safeReasonCodeSchema).max(64),
  transitionCount: boundedCount,
  earliestEventAt: utcInstantSchema.optional(),
  latestEventAt: utcInstantSchema.optional(),
  excludesPrivateContent: z.literal(true),
});

export const privacyInventorySchema = strictObject({
  localContactCount: boundedCount,
  enabledRecipientCount: boundedCount,
  approvalCount: boundedCount,
  activityCount: boundedCount,
  templateCount: boundedCount,
  localStorageBytes: z
    .number()
    .int()
    .nonnegative()
    .max(Number.MAX_SAFE_INTEGER),
  lastContactsSyncAt: utcInstantSchema.optional(),
  consentVersions: z.array(boundedShortLabel).max(32),
  externalSmsCopiesNotControlled: z.literal(true),
});

export const privacyActionReviewSchema = strictObject({
  handle: privacyReviewHandleSchema,
  kind: z.enum(PRIVACY_ACTION_KINDS),
  titleKey: boundedShortLabel,
  consequenceKeys: z.array(boundedShortLabel).min(1).max(16),
  preissuedPermitMayFinish: z.boolean(),
  remoteConnectionRequired: z.boolean(),
  externalSmsCopiesNotErased: z.literal(true),
});

const privacyOperationBase = {
  id: privacyOperationIdSchema,
  action: z.enum(PRIVACY_ACTION_KINDS),
};

export const remoteDrainingDeletionReceiptSchema = strictObject({
  kind: z.literal('remote-draining'),
  id: deletionPrivacyOperationIdSchema,
  action: z.literal('delete-account'),
  updatedAt: utcInstantSchema,
  localDataErased: z.literal(true),
  remoteDeletionComplete: z.literal(false),
  externalSmsCopiesNotErased: z.literal(true),
});

export const remoteUnknownDeletionReceiptSchema = strictObject({
  kind: z.literal('remote-unknown'),
  id: deletionPrivacyOperationIdSchema,
  action: z.literal('delete-account'),
  reason: z.literal('coordination-unavailable'),
  updatedAt: utcInstantSchema,
  localDataErased: z.literal(true),
  remoteDeletionComplete: z.literal(false),
  sameAccountRetryAvailable: z.boolean(),
  externalSmsCopiesNotErased: z.literal(true),
});

export const completedDeletionReceiptSchema = strictObject({
  kind: z.literal('complete'),
  id: deletionPrivacyOperationIdSchema,
  action: z.literal('delete-account'),
  completedAt: utcInstantSchema,
  localDataErased: z.literal(true),
  remoteDeletionComplete: z.literal(true),
  externalSmsCopiesNotErased: z.literal(true),
});

const privacyOperationWithoutReceiptSchema = z
  .discriminatedUnion('kind', [
    strictObject({
      kind: z.enum(['queued', 'pausing', 'local-wiping']),
      ...privacyOperationBase,
      updatedAt: utcInstantSchema,
    }),
    strictObject({
      kind: z.literal('remote-draining'),
      ...privacyOperationBase,
      updatedAt: utcInstantSchema,
    }),
    strictObject({
      kind: z.literal('remote-pending'),
      ...privacyOperationBase,
      reason: safeReasonCodeSchema,
      updatedAt: utcInstantSchema,
    }),
    strictObject({
      kind: z.literal('verifying'),
      ...privacyOperationBase,
      updatedAt: utcInstantSchema,
    }),
    strictObject({
      kind: z.literal('complete'),
      ...privacyOperationBase,
      completedAt: utcInstantSchema,
      externalSmsCopiesNotErased: z.literal(true),
    }),
    strictObject({
      kind: z.literal('failed'),
      ...privacyOperationBase,
      reason: safeReasonCodeSchema,
      updatedAt: utcInstantSchema,
    }),
  ])
  .superRefine((operation, context) => {
    if (
      operation.action === 'delete-account' &&
      !deletionPrivacyOperationIdSchema.safeParse(operation.id).success
    ) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['id'],
        message:
          'Delete-account operation IDs must be content-free projections',
      });
    }
  });

export const privacyOperationProjectionSchema = z.union([
  remoteUnknownDeletionReceiptSchema,
  remoteDrainingDeletionReceiptSchema,
  completedDeletionReceiptSchema,
  privacyOperationWithoutReceiptSchema,
]);

export const latestDeletionReceiptProjectionSchema = z.union([
  strictObject({ kind: z.literal('none') }),
  strictObject({
    kind: z.literal('unavailable'),
    reason: z.literal('coordination-unavailable'),
  }),
  remoteUnknownDeletionReceiptSchema,
  remoteDrainingDeletionReceiptSchema,
  completedDeletionReceiptSchema,
]);

export const currentPrivacyOperationProjectionSchema = z.union([
  strictObject({ kind: z.literal('none') }),
  strictObject({
    kind: z.literal('unavailable'),
    reason: z.literal('coordination-unavailable'),
  }),
  remoteUnknownDeletionReceiptSchema,
  remoteDrainingDeletionReceiptSchema,
  completedDeletionReceiptSchema,
  privacyOperationWithoutReceiptSchema,
]);

export const notificationPermissionProjectionSchema = z.discriminatedUnion(
  'kind',
  [
    strictObject({ kind: z.literal('granted') }),
    strictObject({ kind: z.literal('not-requested') }),
    strictObject({ kind: z.literal('settings-required') }),
  ],
);

export const notificationPermissionRequestResultSchema = strictObject({
  kind: z.enum(['granted', 'denied', 'settings-required', 'cancelled']),
});

export const notificationSettingsResultSchema = strictObject({
  kind: z.enum(['opened', 'cancelled']),
});

export const senderTransferReviewSchema = strictObject({
  kind: z.literal('sender-transfer'),
  handle: senderTransferReviewHandleSchema,
  preissuedPermitMayFinish: z.boolean(),
  completionRequiresRecentGoogleAuthentication: z.literal(true),
  consequenceKeys: z.tuple([
    z.literal('transfer.consequence.old-phone-revoked'),
    z.literal('transfer.consequence.new-phone-test-only'),
    z.literal('transfer.consequence.test-required'),
  ]),
});

const senderTransferActiveBase = {
  id: senderTransferOperationIdSchema,
  updatedAt: utcInstantSchema,
};

export const senderTransferOperationProjectionSchema = z.discriminatedUnion(
  'kind',
  [
    strictObject({ kind: z.literal('none') }),
    strictObject({
      kind: z.literal('unavailable'),
      reason: z.literal('coordination-unavailable'),
    }),
    strictObject({
      kind: z.literal('verifying'),
      ...senderTransferActiveBase,
      preissuedPermitMayFinish: z.literal(false),
    }),
    strictObject({
      kind: z.literal('remote-pending'),
      ...senderTransferActiveBase,
      reason: safeReasonCodeSchema,
      preissuedPermitMayFinish: z.literal(false),
    }),
    strictObject({
      kind: z.literal('remote-draining'),
      ...senderTransferActiveBase,
      reason: safeReasonCodeSchema,
      drainUntil: utcInstantSchema,
      preissuedPermitMayFinish: z.literal(true),
    }),
    strictObject({
      kind: z.literal('failed'),
      ...senderTransferActiveBase,
      reason: safeReasonCodeSchema,
      preissuedPermitMayFinish: z.literal(false),
    }),
    strictObject({
      kind: z.literal('complete'),
      id: senderTransferOperationIdSchema,
      preissuedPermitMayFinish: z.literal(false),
      completedAt: utcInstantSchema,
      requiresTest: z.literal(true),
    }),
  ],
);

export const sharedActionResultSchema = strictObject({
  kind: z.enum(['shared', 'cancelled']),
});

export const nativeActionResultSchema = strictObject({
  kind: z.enum(['opened', 'cancelled']),
});
