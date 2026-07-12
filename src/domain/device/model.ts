import type {
  SenderTransferOperationId,
  SenderTransferReviewHandle,
} from '../shared/brand';
import type { SafeReasonCode } from '../shared/reasonCodes';
import type { UtcInstant } from '../shared/temporal';

export const LIFECYCLE_REPAIR_KINDS = [
  'disconnect-contacts',
  'revoke-google-access',
  'sign-out-wipe',
  'wipe-local-data',
] as const;

export type LifecycleRepairKind = (typeof LIFECYCLE_REPAIR_KINDS)[number];

export type NotificationPermissionProjection =
  | Readonly<{ kind: 'granted' }>
  | Readonly<{ kind: 'not-requested' }>
  | Readonly<{ kind: 'settings-required' }>;

export type NotificationPermissionRequestResult = Readonly<{
  kind: 'granted' | 'denied' | 'settings-required' | 'cancelled';
}>;

export type NotificationSettingsResult = Readonly<{
  kind: 'opened' | 'cancelled';
}>;

export type SenderTransferReview = Readonly<{
  kind: 'sender-transfer';
  handle: SenderTransferReviewHandle;
  preissuedPermitMayFinish: boolean;
  completionRequiresRecentGoogleAuthentication: true;
  consequenceKeys: readonly [
    'transfer.consequence.old-phone-revoked',
    'transfer.consequence.new-phone-test-only',
    'transfer.consequence.test-required',
  ];
}>;

type SenderTransferActiveOperation = Readonly<{
  id: SenderTransferOperationId;
  preissuedPermitMayFinish: boolean;
  updatedAt: UtcInstant;
}>;

export type SenderTransferOperationProjection =
  | Readonly<{ kind: 'none' }>
  | Readonly<{
      kind: 'unavailable';
      reason: 'coordination-unavailable';
    }>
  | (SenderTransferActiveOperation & Readonly<{ kind: 'verifying' }>)
  | (SenderTransferActiveOperation &
      Readonly<{
        kind: 'remote-pending';
        reason: SafeReasonCode;
      }>)
  | (SenderTransferActiveOperation &
      Readonly<{
        kind: 'remote-draining';
        reason: SafeReasonCode;
        drainUntil: UtcInstant;
        preissuedPermitMayFinish: true;
      }>)
  | (SenderTransferActiveOperation &
      Readonly<{
        kind: 'failed';
        reason: SafeReasonCode;
        preissuedPermitMayFinish: false;
      }>)
  | Readonly<{
      kind: 'complete';
      id: SenderTransferOperationId;
      preissuedPermitMayFinish: false;
      completedAt: UtcInstant;
      requiresTest: true;
    }>;
