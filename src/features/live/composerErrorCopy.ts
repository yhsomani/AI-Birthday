import type { liveEnglish } from '../../localization/liveResources';

type LiveTranslationKey = keyof typeof liveEnglish;

const composerErrorKeys: Readonly<Record<string, LiveTranslationKey>> = {
  COMPANION_ACCOUNT_UNAVAILABLE: 'live.companion.accountUnavailable',
  COMPOSER_ACCOUNT_DELETING: 'live.companion.accountDeleting',
  COMPOSER_COEXISTENCE_UNVERIFIED: 'live.companion.coexistenceUnverified',
  COMPOSER_CONTACTS_FRESHNESS_UNAVAILABLE:
    'live.companion.contactsFreshnessUnavailable',
  COMPOSER_CONTACTS_RECONNECT_REQUIRED:
    'live.companion.contactsReconnectRequired',
  COMPOSER_MANAGED_BY_ANDROID: 'live.companion.managedByAndroidComposer',
  COMPOSER_RESERVATION_STALE: 'live.companion.reservationStale',
  COMPOSER_RESERVATION_HELD: 'live.companion.reservationHeld',
  COMPOSER_REVIEW_BLOCKED: 'live.companion.reviewRetry',
  COMPOSER_REVIEW_EXPIRED: 'live.companion.reviewRetry',
  COMPOSER_REVIEW_MISMATCH: 'live.companion.reviewRetry',
  COMPOSER_REVIEW_STALE: 'live.companion.reviewRetry',
  COMPOSER_UNAVAILABLE: 'live.companion.reviewRetry',
};

export const composerErrorMessageKey = (code: string): LiveTranslationKey =>
  composerErrorKeys[code] ?? 'live.companion.genericRecovery';

export const composerErrorCanRepairContacts = (code: string): boolean =>
  code === 'COMPOSER_CONTACTS_RECONNECT_REQUIRED' ||
  code === 'COMPOSER_CONTACTS_FRESHNESS_UNAVAILABLE';
