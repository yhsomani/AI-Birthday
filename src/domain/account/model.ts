import type { PrivateEmail } from '../shared/brand';
import type { ReadinessIssue } from '../readiness/model';
import type { UtcInstant } from '../shared/temporal';

export type AndroidSenderProjection =
  | Readonly<{
      platform: 'android';
      kind: 'test-only';
      epochLabel: string;
    }>
  | Readonly<{
      platform: 'android';
      kind: 'paused-repair';
      epochLabel: string;
    }>
  | Readonly<{
      platform: 'android';
      kind: 'automation-active';
      epochLabel: string;
    }>
  | Readonly<{
      platform: 'android';
      kind: 'standby';
      activeOtherDeviceLabel: string;
    }>
  | Readonly<{
      platform: 'android';
      kind: 'transfer-pending';
      preissuedPermitMayFinish: boolean;
      drainUntil?: UtcInstant | undefined;
    }>
  | Readonly<{
      platform: 'android';
      kind: 'deleting';
      preissuedPermitMayFinish: boolean;
      drainUntil?: UtcInstant | undefined;
    }>;

export type SenderProjection = AndroidSenderProjection;

export type AccountProjection =
  | Readonly<{
      kind: 'signed-out';
      retainedSetup: 'none' | 'same-account-only';
    }>
  | Readonly<{ kind: 'connecting' }>
  | Readonly<{
      kind: 'connected';
      displayEmail: PrivateEmail;
      sender: SenderProjection;
    }>
  | Readonly<{
      kind: 'reconnect-required';
      issue: ReadinessIssue;
    }>
  | Readonly<{
      kind: 'cleanup-pending';
      operation: 'disconnect' | 'revoke' | 'sign-out' | 'delete' | 'repair';
      issue: ReadinessIssue;
    }>;
