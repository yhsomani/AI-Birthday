import type { BirthdayNativePort } from '../../application/ports/BirthdayNativePort';
import type { CompanionNativeGateway } from '../../infrastructure/native/ios/CompanionNativeGateway';

export type LiveAppPort = BirthdayNativePort;

export type LiveCompanionPort = Pick<
  CompanionNativeGateway,
  | 'canOpenComposer'
  | 'getReminderStatus'
  | 'openNotificationSettings'
  | 'openUserConfirmedComposer'
  | 'prepareComposerReview'
  | 'requestReminderAuthorization'
>;
