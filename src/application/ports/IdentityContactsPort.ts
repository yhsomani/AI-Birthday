import type { AccountProjection } from '../../domain/account/model';
import type { SyncProjection } from '../../domain/contacts/model';
import type { DeviceEligibility } from '../../domain/readiness/model';
import type { NativeResult } from '../../domain/shared/result';

export interface IdentityContactsPort {
  refreshCompatibility(): Promise<NativeResult<DeviceEligibility>>;
  continueWithGoogle(): Promise<NativeResult<AccountProjection>>;
  authorizeContacts(): Promise<NativeResult<SyncProjection>>;
  syncContacts(reason: 'setup' | 'user'): Promise<NativeResult<SyncProjection>>;
}
