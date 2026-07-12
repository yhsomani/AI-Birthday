import type { PrivacyOperationId, PrivacyReviewHandle } from '../shared/brand';
import type { SafeReasonCode } from '../shared/reasonCodes';
import type { UtcInstant } from '../shared/temporal';

export const PRIVACY_ACTION_KINDS = [
  'disconnect-contacts',
  'revoke-google-access',
  'sign-out-retain',
  'sign-out-wipe',
  'delete-account',
  'wipe-local-data',
  'clear-gemini-templates',
  'clear-activity',
] as const;

export type PrivacyActionKind = (typeof PRIVACY_ACTION_KINDS)[number];

export type PrivacyInventory = Readonly<{
  localContactCount: number;
  enabledRecipientCount: number;
  approvalCount: number;
  activityCount: number;
  templateCount: number;
  localStorageBytes: number;
  lastContactsSyncAt?: UtcInstant | undefined;
  consentVersions: readonly string[];
  externalSmsCopiesNotControlled: true;
}>;

export type PrivacyActionReview = Readonly<{
  handle: PrivacyReviewHandle;
  kind: PrivacyActionKind;
  titleKey: string;
  consequenceKeys: readonly string[];
  preissuedPermitMayFinish: boolean;
  remoteConnectionRequired: boolean;
  externalSmsCopiesNotErased: true;
}>;

export type RemoteDrainingDeletionReceipt = Readonly<{
  kind: 'remote-draining';
  id: PrivacyOperationId;
  action: 'delete-account';
  updatedAt: UtcInstant;
  localDataErased: true;
  remoteDeletionComplete: false;
  externalSmsCopiesNotErased: true;
}>;

export type RemoteUnknownDeletionReceipt = Readonly<{
  kind: 'remote-unknown';
  id: PrivacyOperationId;
  action: 'delete-account';
  reason: 'coordination-unavailable';
  updatedAt: UtcInstant;
  localDataErased: true;
  remoteDeletionComplete: false;
  sameAccountRetryAvailable: boolean;
  externalSmsCopiesNotErased: true;
}>;

export type CompletedDeletionReceipt = Readonly<{
  kind: 'complete';
  id: PrivacyOperationId;
  action: 'delete-account';
  completedAt: UtcInstant;
  localDataErased: true;
  remoteDeletionComplete: true;
  externalSmsCopiesNotErased: true;
}>;

export type LatestDeletionReceiptProjection =
  | Readonly<{ kind: 'none' }>
  | Readonly<{
      kind: 'unavailable';
      reason: 'coordination-unavailable';
    }>
  | RemoteUnknownDeletionReceipt
  | RemoteDrainingDeletionReceipt
  | CompletedDeletionReceipt;

export type CurrentPrivacyOperationProjection =
  | Readonly<{ kind: 'none' }>
  | Readonly<{
      kind: 'unavailable';
      reason: 'coordination-unavailable';
    }>
  | PrivacyOperationProjection;

export type PrivacyOperationProjection =
  | Readonly<{
      kind: 'queued' | 'pausing' | 'local-wiping';
      id: PrivacyOperationId;
      action: PrivacyActionKind;
      updatedAt: UtcInstant;
    }>
  | Readonly<{
      kind: 'remote-draining';
      id: PrivacyOperationId;
      action: PrivacyActionKind;
      updatedAt: UtcInstant;
    }>
  | RemoteUnknownDeletionReceipt
  | RemoteDrainingDeletionReceipt
  | Readonly<{
      kind: 'remote-pending';
      id: PrivacyOperationId;
      action: PrivacyActionKind;
      reason: SafeReasonCode;
      updatedAt: UtcInstant;
    }>
  | Readonly<{
      kind: 'verifying';
      id: PrivacyOperationId;
      action: PrivacyActionKind;
      updatedAt: UtcInstant;
    }>
  | Readonly<{
      kind: 'complete';
      id: PrivacyOperationId;
      action: PrivacyActionKind;
      completedAt: UtcInstant;
      externalSmsCopiesNotErased: true;
    }>
  | Readonly<{
      kind: 'failed';
      id: PrivacyOperationId;
      action: PrivacyActionKind;
      reason: SafeReasonCode;
      updatedAt: UtcInstant;
    }>;
