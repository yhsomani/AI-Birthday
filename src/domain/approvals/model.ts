import type {
  ApprovalReviewHandle,
  ContactId,
  PrivateDisplayName,
  PrivateMessageText,
} from '../shared/brand';
import type { UtcInstant } from '../shared/temporal';

export const APPROVAL_INVALIDATION_REASONS = [
  'phone-changed',
  'birthday-changed',
  'name-changed',
  'template-changed',
  'placeholder-semantics-changed',
  'window-changed',
  'late-policy-changed',
  'sim-changed',
  'segment-plan-changed',
  'disclosure-changed',
  'sender-epoch-changed',
  'permission-policy-changed',
] as const;

export type ApprovalInvalidationReason =
  (typeof APPROVAL_INVALIDATION_REASONS)[number];

export type ApprovalProjection =
  | Readonly<{ kind: 'missing' }>
  | Readonly<{ kind: 'valid'; approvedAt: UtcInstant }>
  | Readonly<{
      kind: 'invalidated';
      reasons: readonly ApprovalInvalidationReason[];
    }>;

export type ApprovalReview =
  | Readonly<{
      platform: 'android';
      handle: ApprovalReviewHandle;
      contactId: ContactId;
      recipient: PrivateDisplayName;
      maskedPhone: string;
      birthdayLabel: string;
      exactText: PrivateMessageText;
      windowLabel: string;
      simLabel: string;
      segmentCount: number;
      chargeDisclosure: string;
      consentDisclosure: string;
    }>
  | Readonly<{
      platform: 'ios';
      handle: ApprovalReviewHandle;
      contactId: ContactId;
      recipient: PrivateDisplayName;
      maskedPhone: string;
      birthdayLabel: string;
      exactText: PrivateMessageText;
      deliveryMode: 'user-controlled-composer';
      consentDisclosure: string;
    }>;

export type ApprovalReviewItem =
  | Readonly<{
      platform: 'android';
      contactId: ContactId;
      recipient: PrivateDisplayName;
      maskedPhone: string;
      birthdayLabel: string;
      exactText: PrivateMessageText;
      windowLabel: string;
      simLabel: string;
      segmentCount: number;
      chargeDisclosure: string;
      consentDisclosure: string;
    }>
  | Readonly<{
      platform: 'ios';
      contactId: ContactId;
      recipient: PrivateDisplayName;
      maskedPhone: string;
      birthdayLabel: string;
      exactText: PrivateMessageText;
      deliveryMode: 'user-controlled-composer';
      consentDisclosure: string;
    }>;

export type ApprovalBatchReview = Readonly<{
  handle: ApprovalReviewHandle;
  items: readonly ApprovalReviewItem[];
  readyCount: number;
  blockedCount: number;
  explicitConfirmationRequired: true;
}>;
