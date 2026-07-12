import type { ApprovalInvalidationReason } from '../domain/approvals/model';
import type { SafeReasonCode } from '../domain/shared/reasonCodes';
import type { TranslationKey } from './resources';

/**
 * Native reason codes are stable support identifiers, not user copy. Keep this
 * map exhaustive so a newly introduced native reason cannot silently become a
 * raw label in the React Native UI.
 */
export const safeReasonMessageKeys: Record<SafeReasonCode, TranslationKey> = {
  'account-cancelled': 'live.reason.accountCancelled',
  'account-disabled': 'live.reason.accountDisabled',
  'account-mismatch': 'live.reason.accountMismatch',
  'account-reconnect-required': 'live.reason.accountReconnect',
  'active-sender-other-device': 'live.reason.activeSenderOtherDevice',
  'approval-invalid': 'live.people.issue.approvalInvalid',
  'approval-missing': 'live.reason.approvalMissing',
  'background-restricted': 'live.reason.backgroundRestricted',
  'birthday-choice-required': 'live.people.issue.birthdayChoice',
  'birthday-conflict': 'live.people.issue.birthdayConflict',
  'birthday-missing': 'live.people.issue.birthdayMissing',
  'clock-untrusted': 'live.reason.clockUntrusted',
  'contacts-authorization-required': 'live.reason.contactsAuthorization',
  'contacts-empty': 'live.reason.contactsEmpty',
  'contacts-partial-sync': 'live.reason.contactsPartialSync',
  'contacts-stale': 'live.reason.contactsStale',
  'coordination-unavailable': 'live.reason.coordinationUnavailable',
  'data-saver-restricted': 'live.reason.dataSaverRestricted',
  'distribution-channel-unapproved': 'live.reason.distributionUnapproved',
  'doze-exemption-missing': 'live.reason.dozeExemptionMissing',
  'duplicate-destination': 'live.people.issue.duplicateDestination',
  'firebase-account-deleting': 'live.reason.accountDeleting',
  'google-play-services-missing': 'live.reason.playServicesMissing',
  'hibernation-status-unsafe': 'live.reason.hibernationUnsafe',
  'installer-allowlist-missing': 'live.reason.installerAllowlistMissing',
  'internal-contract-invalid': 'live.reason.internalContractInvalid',
  'invalid-daily-cap': 'live.reason.invalidDailyCap',
  'invalid-segment-cap': 'live.reason.invalidSegmentCap',
  'invalid-window': 'live.reason.invalidWindow',
  'leap-policy-required': 'live.people.issue.leapPolicy',
  'low-power-standby-unsafe': 'live.reason.lowPowerStandbyUnsafe',
  'native-bridge-unavailable': 'live.reason.nativeBridgeUnavailable',
  'network-offline': 'live.reason.networkOffline',
  'no-active-sim': 'live.reason.noActiveSim',
  'no-telephony': 'live.reason.noTelephony',
  'notification-permission-missing': 'live.reason.notificationPermission',
  'permission-denied': 'live.reason.permissionDenied',
  'permission-permanently-denied': 'live.reason.permissionPermanentlyDenied',
  'phone-ambiguous-region': 'live.people.issue.phoneRegion',
  'phone-blocked-form': 'live.people.issue.phoneBlocked',
  'phone-choice-required': 'live.people.issue.phoneChoice',
  'phone-invalid': 'live.people.issue.phoneInvalid',
  'phone-missing': 'live.people.issue.phoneMissing',
  'platform-composer-only': 'live.reason.platformComposerOnly',
  'platform-unsupported': 'live.reason.platformUnsupported',
  'policy-suspended': 'live.reason.policySuspended',
  'reset-safety-blocked': 'live.reason.resetSafetyBlocked',
  'reset-safety-overflow': 'live.reason.resetSafetyOverflow',
  'restricted-profile': 'live.reason.restrictedProfile',
  'safe-given-name-missing': 'live.people.issue.givenNameMissing',
  'scheduler-delayed': 'live.reason.schedulerDelayed',
  'send-sms-not-grantable': 'live.reason.smsPermissionUnavailable',
  'sim-changed': 'live.reason.simChanged',
  'sim-invalid': 'live.reason.simInvalid',
  'source-contact-deleted': 'live.people.issue.sourceDeleted',
  'stable-source-missing': 'live.people.issue.sourceMissing',
  'stale-revision': 'live.reason.staleRevision',
  'template-bidi-control': 'live.reason.templateBidiControl',
  'template-control-character': 'live.reason.templateControlCharacter',
  'template-empty': 'live.reason.templateEmpty',
  'template-placeholder-count': 'live.reason.templatePlaceholderCount',
  'template-unsupported-placeholder':
    'live.reason.templateUnsupportedPlaceholder',
  'template-url-not-allowed': 'live.reason.templateUrlNotAllowed',
  'test-budget-exhausted': 'live.reason.testBudgetExhausted',
  'test-receipt-invalid': 'live.reason.testReceiptInvalid',
  'transfer-pending': 'live.reason.transferPending',
  'unknown-native-value': 'live.reason.unknownNativeValue',
  'unused-app-restrictions-unsafe': 'live.reason.unusedAppRestrictions',
  'window-capacity-conflict': 'live.reason.windowCapacityConflict',
};

export const safeReasonMessageKey = (reason: SafeReasonCode): TranslationKey =>
  safeReasonMessageKeys[reason];

export const approvalInvalidationMessageKeys: Record<
  ApprovalInvalidationReason,
  TranslationKey
> = {
  'phone-changed': 'live.approvalReason.contactChanged',
  'birthday-changed': 'live.approvalReason.contactChanged',
  'name-changed': 'live.approvalReason.contactChanged',
  'template-changed': 'live.approvalReason.messageChanged',
  'placeholder-semantics-changed': 'live.approvalReason.messageChanged',
  'window-changed': 'live.approvalReason.scheduleChanged',
  'late-policy-changed': 'live.approvalReason.scheduleChanged',
  'sim-changed': 'live.approvalReason.sendingSetupChanged',
  'segment-plan-changed': 'live.approvalReason.messageChanged',
  'disclosure-changed': 'live.approvalReason.disclosureChanged',
  'sender-epoch-changed': 'live.approvalReason.sendingSetupChanged',
  'permission-policy-changed': 'live.approvalReason.sendingSetupChanged',
};

export const approvalInvalidationMessageKey = (
  reason: ApprovalInvalidationReason,
): TranslationKey => approvalInvalidationMessageKeys[reason];
