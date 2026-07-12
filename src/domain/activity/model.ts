import type { ActivityId, PageCursor } from '../shared/brand';
import type { SafeReasonCode } from '../shared/reasonCodes';
import type { UtcInstant } from '../shared/temporal';

export const ACTIVITY_KINDS = [
  'planned',
  'coordination-blocked',
  'armed-suppressed',
  'skipped',
  'missed',
  'submitted',
  'sent-from-device',
  'delivered',
  'delivery-failed',
  'partial-delivery',
  'delivery-unknown',
  'submission-failed',
  'submission-unknown',
  'paused',
  'approval-invalidated',
  'sync',
  'transfer',
  'settings-changed',
  'reminder-scheduled',
  'composer-opened',
  'composer-cancelled',
  'composer-failed',
  'composer-outcome-unknown',
  'composer-reported-sent',
] as const;

export type ActivityKind = (typeof ACTIVITY_KINDS)[number];

export type ActivityRecord = Readonly<{
  id: ActivityId;
  kind: ActivityKind;
  reason?: SafeReasonCode | undefined;
  occurredAt: UtcInstant;
  actionable: boolean;
}>;

export type ActivityQuery = Readonly<{
  cursor?: PageCursor | undefined;
  pageSize: number;
}>;

export type ActivityPage = Readonly<{
  items: readonly ActivityRecord[];
  nextCursor?: PageCursor | undefined;
}>;

export type DiagnosticsPreview = Readonly<{
  buildLabel: string;
  androidOrIosVersionLabel: string;
  capabilityCodes: readonly SafeReasonCode[];
  transitionCount: number;
  schedulerHeartbeatAt?: UtcInstant | undefined;
  earliestEventAt?: UtcInstant | undefined;
  latestEventAt?: UtcInstant | undefined;
  excludesPrivateContent: true;
}>;
