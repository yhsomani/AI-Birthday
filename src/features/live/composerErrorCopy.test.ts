import { liveEnglish, liveHindi } from '../../localization/liveResources';
import {
  composerErrorCanRepairContacts,
  composerErrorMessageKey,
} from './composerErrorCopy';

describe('iOS composer reservation error copy', () => {
  it.each([
    'COMPANION_ACCOUNT_UNAVAILABLE',
    'COMPOSER_ACCOUNT_DELETING',
    'COMPOSER_COEXISTENCE_UNVERIFIED',
    'COMPOSER_MANAGED_BY_ANDROID',
    'COMPOSER_RESERVATION_STALE',
    'COMPOSER_RESERVATION_HELD',
  ])('maps %s to actionable English and Hindi copy', code => {
    const key = composerErrorMessageKey(code);
    expect(liveEnglish[key]).toBeTruthy();
    expect(liveHindi[key]).toMatch(/[\u0900-\u097f]/u);
  });

  it('uses localized recovery copy for unknown safe support codes', () => {
    const key = composerErrorMessageKey('COMPOSER_FUTURE_SAFE_CODE');
    expect(key).toBe('live.companion.genericRecovery');
    expect(liveEnglish[key]).toContain('could not be confirmed');
    expect(liveEnglish[key]).toContain('Check Messages before trying again');
    expect(liveEnglish[key]).toContain('will not retry automatically');
    expect(liveEnglish[key]).not.toContain('no message was sent');
    expect(liveHindi[key]).toContain('पक्का नहीं हो सका');
    expect(liveHindi[key]).toContain('अपने-आप दोबारा कोशिश नहीं करेगा');
    expect(liveHindi[key]).not.toContain('कोई संदेश नहीं भेजा गया');
  });

  it.each([
    'COMPOSER_REVIEW_BLOCKED',
    'COMPOSER_REVIEW_EXPIRED',
    'COMPOSER_REVIEW_MISMATCH',
    'COMPOSER_REVIEW_STALE',
    'COMPOSER_UNAVAILABLE',
  ])(
    'maps guaranteed pre-presentation %s to benign review-again copy',
    code => {
      expect(composerErrorMessageKey(code)).toBe('live.companion.reviewRetry');
    },
  );

  it('keeps native or result-contract failure copy conservative', () => {
    for (const code of [
      'COMPOSER_NATIVE_FAILURE',
      'COMPOSER_RESULT_INVALID',
      'COMPOSER_REVIEW_RESULT_INVALID',
    ]) {
      const key = composerErrorMessageKey(code);
      expect(key).toBe('live.companion.genericRecovery');
      expect(liveEnglish[key]).toContain('could not be confirmed');
      expect(liveEnglish[key]).not.toContain('no message was sent');
    }
    expect(liveEnglish['live.companion.error']).toBe(
      'Messages review could not continue.',
    );
    expect(liveHindi['live.companion.error']).toBe(
      'Messages समीक्षा आगे नहीं बढ़ सकी।',
    );
  });

  it('offers Contacts repair only for the two recoverable Contacts failures', () => {
    expect(
      composerErrorCanRepairContacts('COMPOSER_CONTACTS_RECONNECT_REQUIRED'),
    ).toBe(true);
    expect(
      composerErrorCanRepairContacts('COMPOSER_CONTACTS_FRESHNESS_UNAVAILABLE'),
    ).toBe(true);
    expect(composerErrorCanRepairContacts('COMPOSER_RESERVATION_HELD')).toBe(
      false,
    );
  });

  it('does not falsely claim that a lost reservation capability belongs to another device', () => {
    const key = composerErrorMessageKey('COMPOSER_RESERVATION_HELD');
    expect(liveEnglish[key]).toContain(
      'Another iPhone or an earlier protected review',
    );
    expect(liveHindi[key]).toContain('दूसरे iPhone या पिछली सुरक्षित समीक्षा');
  });
});
