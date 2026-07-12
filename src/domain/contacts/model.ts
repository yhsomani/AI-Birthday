import type { ApprovalProjection } from '../approvals/model';
import type {
  BirthdayChoiceId,
  ContactId,
  EnrollmentReviewHandle,
  PageCursor,
  PhoneChoiceId,
  PrivateDisplayName,
} from '../shared/brand';
import type { SafeReasonCode } from '../shared/reasonCodes';
import type { UtcInstant } from '../shared/temporal';

export const CONTACT_ISSUE_CODES = [
  'birthday-missing',
  'birthday-conflict',
  'birthday-choice-required',
  'leap-policy-required',
  'phone-missing',
  'phone-choice-required',
  'phone-ambiguous-region',
  'phone-invalid',
  'phone-blocked-form',
  'duplicate-destination',
  'stable-source-missing',
  'safe-given-name-missing',
  'source-contact-deleted',
  'approval-invalid',
] as const;

export type ContactIssueCode = (typeof CONTACT_ISSUE_CODES)[number];

export type SyncProjection =
  | Readonly<{ kind: 'never-synced' }>
  | Readonly<{
      kind: 'syncing';
      mode: 'full' | 'incremental';
      retainedGeneration: boolean;
    }>
  | Readonly<{
      kind: 'fresh';
      completedAt: UtcInstant;
      contactCount: number;
    }>
  | Readonly<{
      kind: 'stale';
      lastSuccessAt: UtcInstant;
      reason: SafeReasonCode;
    }>
  | Readonly<{
      kind: 'failed-retained';
      lastSuccessAt?: UtcInstant | undefined;
      reason: SafeReasonCode;
    }>
  | Readonly<{
      kind: 'authorization-required';
      reason: 'contacts-authorization-required';
    }>;

export type ContactReadiness =
  | Readonly<{ kind: 'ready' }>
  | Readonly<{
      kind: 'needs-attention';
      reasons: readonly ContactIssueCode[];
    }>
  | Readonly<{
      kind: 'unavailable';
      reasons: readonly ContactIssueCode[];
    }>;

export type EnrollmentProjection =
  | Readonly<{ kind: 'off' }>
  | Readonly<{ kind: 'enabled'; approval: ApprovalProjection }>
  | Readonly<{
      kind: 'paused';
      reason: SafeReasonCode;
      approval: ApprovalProjection;
    }>
  | Readonly<{ kind: 'excluded'; reason?: SafeReasonCode | undefined }>;

export type ContactSummary = Readonly<{
  id: ContactId;
  displayName: PrivateDisplayName;
  birthdayLabel?: string | undefined;
  maskedPhone?: string | undefined;
  readiness: ContactReadiness;
  enrollment: EnrollmentProjection;
}>;

export type ContactPhoneChoice = Readonly<{
  id: PhoneChoiceId;
  maskedDisplay: string;
  sourceLabel: string;
  selectable: boolean;
  issue?: ContactIssueCode | undefined;
}>;

export type BirthdayChoice = Readonly<{
  id: BirthdayChoiceId;
  displayLabel: string;
  hasYear: boolean;
  selectable: boolean;
  issue?: ContactIssueCode | undefined;
}>;

export type ContactDetail = Readonly<{
  summary: ContactSummary;
  phoneChoices: readonly ContactPhoneChoice[];
  birthdayChoices: readonly BirthdayChoice[];
  selectedPhoneId?: PhoneChoiceId | undefined;
  selectedBirthdayId?: BirthdayChoiceId | undefined;
  nextOccurrenceLabel?: string | undefined;
  lastOutcomeLabel?: string | undefined;
}>;

export type PeopleFilter =
  | 'all'
  | 'enabled'
  | 'ready'
  | 'needs-attention'
  | 'excluded';

export type PeopleQuery = Readonly<{
  filter: PeopleFilter;
  search?: string | undefined;
  cursor?: PageCursor | undefined;
  pageSize: number;
}>;

export type PeoplePage = Readonly<{
  items: readonly ContactSummary[];
  nextCursor?: PageCursor | undefined;
  totalCount: number;
}>;

export type EnrollmentReview = Readonly<{
  handle: EnrollmentReviewHandle;
  recipients: readonly ContactSummary[];
  readyCount: number;
  attentionCount: number;
  explicitConfirmationRequired: true;
}>;

export type PeopleMutationProjection = Readonly<{
  changedContactIds: readonly ContactId[];
  invalidatedApprovalCount: number;
}>;
