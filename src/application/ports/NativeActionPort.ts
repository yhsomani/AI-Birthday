import type { ActionHandle, NativeRevision } from '../../domain/shared/brand';
import type { NativeResult } from '../../domain/shared/result';

export interface NativeActionPort {
  performAction(input: {
    handle: ActionHandle;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<{ kind: 'opened' | 'cancelled' }>>;
}
