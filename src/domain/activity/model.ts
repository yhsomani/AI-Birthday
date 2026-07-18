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

export const ACTIVITY_RECOVERY_ROUTES = [
  'attention',
  'automation',
  'people',
  'settings',
] as const;

export type ActivityRecoveryRoute = (typeof ACTIVITY_RECOVERY_ROUTES)[number];

export type ActivityRecovery = Readonly<{
  route: ActivityRecoveryRoute;
}>;

export type ActivityRecord = Readonly<{
  id: ActivityId;
  kind: ActivityKind;
  reason?: SafeReasonCode | undefined;
  occurredAt: UtcInstant;
  /** Present only while native state still exposes a concrete repair route. */
  recovery?: ActivityRecovery | undefined;
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
