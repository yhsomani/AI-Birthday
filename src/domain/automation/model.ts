import type {
  ActivationReviewHandle,
  OccurrenceId,
  PrivateDisplayName,
  PrivateMessageText,
  TestReviewHandle,
  TodayOccurrenceReviewHandle,
} from '../shared/brand';
import type { ReadinessProjection } from '../readiness/model';
import type { SafeReasonCode } from '../shared/reasonCodes';
import type { LocalDate, UtcInstant } from '../shared/temporal';

export const ANDROID_BIRTHDAY_JOB_PHASES = [
  'planned',
  'prepared',
  'scheduled',
  'claimed',
  'coordination-blocked',
  'cloud-claimed',
  'arm-reconciling',
  'coordination-unknown',
  'cloud-armed',
  'armed-suppressed',
  'submission-barrier-consumed',
  'submitted',
  'sent-from-device',
  'retryable-failure',
  'retry-exhausted',
  'delivered',
  'delivery-failed',
  'partial-delivery',
  'partial-delivery-unknown',
  'delivery-unknown',
  'unknown',
  'partial-unknown',
  'permanent-failure',
  'skipped',
  'missed',
  'cancelled',
] as const;

export const ANDROID_TEST_PHASES = [
  'prepared',
  'cloud-claimed',
  'arm-reconciling',
  'coordination-unknown',
  'cloud-armed',
  'armed-suppressed',
  'barrier-consumed',
  'submitted',
  'sent-from-device',
  'passed',
  'failed',
  'partial-unknown',
  'unknown',
  'permanent-failure',
  'cleanup-cancelled',
  'receipt-invalidated',
] as const;

export type AndroidBirthdayJobPhase =
  (typeof ANDROID_BIRTHDAY_JOB_PHASES)[number];
export type AndroidTestPhase = (typeof ANDROID_TEST_PHASES)[number];

export type BirthdayJobProjection = Readonly<{
  platform: 'android';
  occurrenceId: OccurrenceId;
  occurrenceDate: LocalDate;
  phase: AndroidBirthdayJobPhase;
  updatedAt: UtcInstant;
  attempt: 1 | 2;
}>;

export type TestProjection = Readonly<{
  platform: 'android';
  phase: AndroidTestPhase;
  updatedAt: UtcInstant;
  reason?: SafeReasonCode | undefined;
}>;

export type AutomationProjection = Readonly<{
  platform: 'android';
  desired: 'on' | 'paused';
  effective:
    | 'not-configured'
    | 'test-only'
    | 'paused-repair'
    | 'active'
    | 'action-required'
    | 'standby'
    | 'transfer-pending'
    | 'deleting';
  readiness: ReadinessProjection & { platform: 'android' };
}>;

export type TestReview = Readonly<{
  platform: 'android';
  handle: TestReviewHandle;
  maskedDestination: string;
  exactText: PrivateMessageText;
  simLabel: string;
  segmentCount: number;
  chargeDisclosure: string;
}>;

export type ActivationReview = Readonly<{
  platform: 'android';
  handle: ActivationReviewHandle;
  enabledRecipientCount: number;
  attentionCount: number;
  templatePreview: PrivateMessageText;
  windowLabel: string;
  simLabel: string;
  dailyCap: number;
  limitationsDisclosure: string;
}>;

export type UpcomingGreeting = Readonly<{
  occurrenceId: OccurrenceId;
  recipient: PrivateDisplayName;
  localDate: LocalDate;
  windowLabel: string;
  maskedPhone: string;
  /** Present only when native binds the next occurrence to a current valid approval. */
  exactText?: PrivateMessageText | undefined;
}>;

export type TodayOccurrenceChoice =
  | 'send-through-normal-path'
  | 'open-system-composer'
  | 'start-next-year';

export type TodayOccurrenceReview = Readonly<{
  handle: TodayOccurrenceReviewHandle;
  recipient: PrivateDisplayName;
  maskedDestination: string;
  exactText: PrivateMessageText;
  choice: TodayOccurrenceChoice;
  alternativeChoice?: 'start-next-year' | undefined;
  limitationsDisclosure: string;
}>;
