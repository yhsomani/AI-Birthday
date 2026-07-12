import { z } from 'zod';

import type {
  ActionHandle,
  ActivityId,
  ActivationReviewHandle,
  ApprovalReviewHandle,
  BirthdayChoiceId,
  ContactId,
  ComposerProposalId,
  EnrollmentReviewHandle,
  IssueId,
  MessagePreviewHandle,
  NativeRevision,
  NativeRouteId,
  OccurrenceId,
  PageCursor,
  PhoneChoiceId,
  PolicyReviewHandle,
  PrivateDisplayName,
  PrivateEmail,
  PrivateMessageText,
  PrivacyOperationId,
  PrivacyReviewHandle,
  SafeSupportCode,
  SenderTransferOperationId,
  SenderTransferReviewHandle,
  TestReviewHandle,
  TodayOccurrenceReviewHandle,
} from '../../domain/shared/brand';
import { SAFE_REASON_CODES } from '../../domain/shared/reasonCodes';
import type {
  IanaTimeZone,
  LocalDate,
  LocalTime,
  UtcInstant,
} from '../../domain/shared/temporal';
import {
  isLocalDate,
  isLocalTime,
  isUtcInstant,
} from '../../domain/shared/temporal';

export const strictObject = <Shape extends z.ZodRawShape>(shape: Shape) =>
  z.object(shape).strict();

const bidiControlCodePoints = new Set([
  0x061c, 0x200e, 0x200f, 0x202a, 0x202b, 0x202c, 0x202d, 0x202e, 0x2066,
  0x2067, 0x2068, 0x2069,
]);

const hasUnsafeUiCharacter = (
  value: string,
  allowMessageWhitespace = false,
): boolean =>
  Array.from(value).some(character => {
    const codePoint = character.codePointAt(0);
    if (codePoint === undefined || bidiControlCodePoints.has(codePoint)) {
      return true;
    }
    if (codePoint === 0x7f) {
      return true;
    }
    return (
      codePoint <= 0x1f &&
      (!allowMessageWhitespace || ![0x09, 0x0a, 0x0d].includes(codePoint))
    );
  });

export const boundedUiTextSchema = (maximumLength: number) =>
  z
    .string()
    .min(1)
    .max(maximumLength)
    .refine(value => value.trim().length > 0)
    .refine(value => !hasUnsafeUiCharacter(value));

const opaqueString = <Value extends string>(maximumLength = 128) =>
  z
    .string()
    .min(1)
    .max(maximumLength)
    .regex(/^[A-Za-z0-9][A-Za-z0-9._:-]*$/u)
    .transform(value => value as Value);

export const nativeRevisionSchema = z
  .string()
  .regex(/^(0|[1-9]\d{0,18})$/u)
  .transform(value => value as NativeRevision);
export const utcInstantSchema = z
  .string()
  .max(35)
  .refine(isUtcInstant)
  .transform(value => value as UtcInstant);
export const localDateSchema = z
  .string()
  .length(10)
  .refine(isLocalDate)
  .transform(value => value as LocalDate);
export const localTimeSchema = z
  .string()
  .max(12)
  .refine(isLocalTime)
  .transform(value => value as LocalTime);
export const ianaTimeZoneSchema = z
  .string()
  .min(1)
  .max(255)
  .refine(value => {
    try {
      return (
        new Intl.DateTimeFormat('en', { timeZone: value }).resolvedOptions()
          .timeZone.length > 0
      );
    } catch {
      return false;
    }
  })
  .transform(value => value as IanaTimeZone);

export const contactIdSchema = opaqueString<ContactId>();
export const phoneChoiceIdSchema = opaqueString<PhoneChoiceId>();
export const birthdayChoiceIdSchema = opaqueString<BirthdayChoiceId>();
export const occurrenceIdSchema = opaqueString<OccurrenceId>();
export const activityIdSchema = opaqueString<ActivityId>();
export const issueIdSchema = opaqueString<IssueId>();
export const actionHandleSchema = opaqueString<ActionHandle>();
export const pageCursorSchema = opaqueString<PageCursor>(256);
export const privacyOperationIdSchema = opaqueString<PrivacyOperationId>();
export const deletionPrivacyOperationIdSchema = z
  .string()
  .regex(/^privacy_(?:[a-f0-9]{32}|[a-f0-9]{64})$/u)
  .transform(value => value as PrivacyOperationId);
export const nativeRouteIdSchema = z
  .string()
  .regex(
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/u,
  )
  .transform(value => value as NativeRouteId);
export const senderTransferOperationIdSchema = z
  .string()
  .regex(/^transfer_[a-f0-9]{32}$/u)
  .transform(value => value as SenderTransferOperationId);
export const enrollmentReviewHandleSchema =
  opaqueString<EnrollmentReviewHandle>();
export const messagePreviewHandleSchema = opaqueString<MessagePreviewHandle>();
export const composerProposalIdSchema = z
  .string()
  .regex(/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/u)
  .transform(value => value as ComposerProposalId);
export const policyReviewHandleSchema = opaqueString<PolicyReviewHandle>();
export const approvalReviewHandleSchema = opaqueString<ApprovalReviewHandle>();
export const testReviewHandleSchema = opaqueString<TestReviewHandle>();
export const todayOccurrenceReviewHandleSchema =
  opaqueString<TodayOccurrenceReviewHandle>();
export const activationReviewHandleSchema =
  opaqueString<ActivationReviewHandle>();
export const privacyReviewHandleSchema = opaqueString<PrivacyReviewHandle>();
export const senderTransferReviewHandleSchema = z
  .string()
  .regex(/^st_[a-f0-9]{32}$/u)
  .transform(value => value as SenderTransferReviewHandle);
export const privateDisplayNameSchema = boundedUiTextSchema(256).transform(
  value => value as PrivateDisplayName,
);
export const privateEmailSchema = z
  .string()
  .min(3)
  .max(254)
  .refine(
    value =>
      !Array.from(value).some(character => {
        const codePoint = character.codePointAt(0);
        return (
          codePoint === undefined ||
          codePoint <= 0x20 ||
          codePoint === 0x7f ||
          bidiControlCodePoints.has(codePoint)
        );
      }),
  )
  .transform(value => value as PrivateEmail);
export const maskedPhoneSchema = boundedUiTextSchema(64).refine(value => {
  const visibleDigits = value.match(/[0-9]/gu)?.length ?? 0;
  return visibleDigits >= 1 && visibleDigits <= 4 && !value.includes('+');
});
export const privateMessageTextSchema = z
  .string()
  .min(1)
  .max(1_000)
  .refine(value => value.trim().length > 0)
  .refine(value => !hasUnsafeUiCharacter(value, true))
  .transform(value => value as PrivateMessageText);
export const safeSupportCodeSchema = z
  .string()
  .regex(/^[A-Z][A-Z0-9_]{2,63}$/)
  .transform(value => value as SafeSupportCode);

export const safeReasonCodeSchema = z.enum(SAFE_REASON_CODES);
export const fieldNameSchema = z.enum([
  'birthday',
  'confirmation',
  'dailyCap',
  'phone',
  'sim',
  'template',
  'window',
]);
export const fieldIssueSchema = strictObject({
  field: fieldNameSchema,
  code: safeReasonCodeSchema,
});

export const nativeProblemSchema = z.discriminatedUnion('kind', [
  strictObject({
    kind: z.literal('cancelled'),
    source: z.enum(['user', 'system']),
  }),
  strictObject({
    kind: z.literal('stale-revision'),
    latestRevision: nativeRevisionSchema,
  }),
  strictObject({
    kind: z.literal('validation'),
    issues: z.array(fieldIssueSchema).min(1).max(32),
  }),
  strictObject({
    kind: z.literal('action-required'),
    issueIds: z.array(issueIdSchema).min(1).max(64),
  }),
  strictObject({
    kind: z.literal('temporarily-unavailable'),
    code: safeReasonCodeSchema,
    retryAfterSeconds: z.number().int().nonnegative().max(86_400).optional(),
  }),
  strictObject({ kind: z.literal('conflict'), code: safeReasonCodeSchema }),
  strictObject({ kind: z.literal('unsupported'), code: safeReasonCodeSchema }),
  strictObject({
    kind: z.literal('internal'),
    supportCode: safeSupportCodeSchema,
  }),
]);
