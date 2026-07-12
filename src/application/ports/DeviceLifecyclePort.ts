import type {
  LifecycleRepairKind,
  NotificationPermissionProjection,
  NotificationPermissionRequestResult,
  NotificationSettingsResult,
  SenderTransferOperationProjection,
  SenderTransferReview,
} from '../../domain/device/model';
import type { PrivacyOperationProjection } from '../../domain/privacy/model';
import type {
  NativeRevision,
  SenderTransferOperationId,
  SenderTransferReviewHandle,
} from '../../domain/shared/brand';
import type { NativeResult } from '../../domain/shared/result';

export interface DeviceLifecyclePort {
  getNotificationPermission(): Promise<
    NativeResult<NotificationPermissionProjection>
  >;
  requestNotificationPermission(): Promise<
    NativeResult<NotificationPermissionRequestResult>
  >;
  openNotificationSettings(): Promise<NativeResult<NotificationSettingsResult>>;
  getSenderTransferOperation(): Promise<
    NativeResult<SenderTransferOperationProjection>
  >;
  prepareSenderTransfer(input: {
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<SenderTransferReview>>;
  beginSenderTransfer(input: {
    handle: SenderTransferReviewHandle;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<SenderTransferOperationProjection>>;
  completeSenderTransfer(input: {
    operationId: SenderTransferOperationId;
  }): Promise<NativeResult<SenderTransferOperationProjection>>;
  resumeSenderTransfer(input: {
    operationId: SenderTransferOperationId;
  }): Promise<NativeResult<SenderTransferOperationProjection>>;
  repairLifecycleState(input: {
    kind: LifecycleRepairKind;
  }): Promise<NativeResult<PrivacyOperationProjection>>;
}
