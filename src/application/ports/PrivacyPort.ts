import type {
  PrivacyActionKind,
  PrivacyActionReview,
  PrivacyInventory,
  CurrentPrivacyOperationProjection,
  PrivacyOperationProjection,
} from '../../domain/privacy/model';
import type {
  NativeRevision,
  PrivacyOperationId,
  PrivacyReviewHandle,
} from '../../domain/shared/brand';
import type { NativeResult } from '../../domain/shared/result';

export interface PrivacyPort {
  getInventory(): Promise<NativeResult<PrivacyInventory>>;
  getCurrentOperation(): Promise<
    NativeResult<CurrentPrivacyOperationProjection>
  >;
  prepareAction(input: {
    kind: PrivacyActionKind;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<PrivacyActionReview>>;
  confirmAction(input: {
    handle: PrivacyReviewHandle;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<PrivacyOperationProjection>>;
  getOperation(
    operationId: PrivacyOperationId,
  ): Promise<NativeResult<PrivacyOperationProjection>>;
  resumeOperation(
    operationId: PrivacyOperationId,
  ): Promise<NativeResult<PrivacyOperationProjection>>;
}
