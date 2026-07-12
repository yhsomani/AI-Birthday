declare const domainBrand: unique symbol;

export type Brand<Value, Name extends string> = Value & {
  readonly [domainBrand]: Name;
};

export type NativeRevision = Brand<string, 'NativeRevision'>;
export type ContactId = Brand<string, 'ContactId'>;
export type PhoneChoiceId = Brand<string, 'PhoneChoiceId'>;
export type BirthdayChoiceId = Brand<string, 'BirthdayChoiceId'>;
export type OccurrenceId = Brand<string, 'OccurrenceId'>;
export type ActivityId = Brand<string, 'ActivityId'>;
export type IssueId = Brand<string, 'IssueId'>;
export type ActionHandle = Brand<string, 'ActionHandle'>;
export type PageCursor = Brand<string, 'PageCursor'>;
export type PrivacyOperationId = Brand<string, 'PrivacyOperationId'>;
export type NativeRouteId = Brand<string, 'NativeRouteId'>;
export type SenderTransferOperationId = Brand<
  string,
  'SenderTransferOperationId'
>;

export type EnrollmentReviewHandle = Brand<string, 'EnrollmentReviewHandle'>;
export type MessagePreviewHandle = Brand<string, 'MessagePreviewHandle'>;
export type ComposerProposalId = Brand<string, 'ComposerProposalId'>;
export type PolicyReviewHandle = Brand<string, 'PolicyReviewHandle'>;
export type ApprovalReviewHandle = Brand<string, 'ApprovalReviewHandle'>;
export type TestReviewHandle = Brand<string, 'TestReviewHandle'>;
export type ActivationReviewHandle = Brand<string, 'ActivationReviewHandle'>;
export type TodayOccurrenceReviewHandle = Brand<
  string,
  'TodayOccurrenceReviewHandle'
>;
export type PrivacyReviewHandle = Brand<string, 'PrivacyReviewHandle'>;
export type SenderTransferReviewHandle = Brand<
  string,
  'SenderTransferReviewHandle'
>;

export type PrivateDisplayName = Brand<string, 'PrivateDisplayName'>;
export type PrivateEmail = Brand<string, 'PrivateEmail'>;
export type PrivateMessageText = Brand<string, 'PrivateMessageText'>;
export type EphemeralPhoneInput = Brand<string, 'EphemeralPhoneInput'>;
export type SafeSupportCode = Brand<string, 'SafeSupportCode'>;
