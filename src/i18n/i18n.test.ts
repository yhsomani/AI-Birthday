import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  formatCurrencyForLocale,
  formatDateForLocale,
  formatMonthForLocale,
  localeMetadata,
  resolveLocale,
  supportedLocales,
  t,
  tc,
  translations,
  type TranslationKey
} from './i18n';

describe('active localization contract', () => {
  const englishKeys = Object.keys(translations['en-IN']) as TranslationKey[];
  const placeholdersFor = (value: string) =>
    [...value.matchAll(/\{([A-Za-z][A-Za-z0-9_]*)\}/g)].map(match => match[1]).sort();

  it('contains only the temporary console, native notification, widget, and formatting keys', () => {
    assert.ok(englishKeys.length > 0);
    assert.ok(
      englishKeys.every(
        key =>
          key.startsWith('functionalConsole.') ||
          key.startsWith('notification.') ||
          key.startsWith('feature.home.widget.') ||
          key === 'common.notScheduled' ||
          key === 'common.invalidDate'
      )
    );
    assert.equal(
      englishKeys.some(key => key.startsWith('nav.') || key.startsWith('feature.messages.')),
      false
    );
  });

  it('keeps every active string and interpolation placeholder complete across locales', () => {
    for (const locale of supportedLocales) {
      for (const key of englishKeys) {
        assert.ok(translations[locale][key].trim().length > 0, `${locale} missing ${key}`);
        assert.deepEqual(
          placeholdersFor(translations[locale][key]),
          placeholdersFor(translations['en-IN'][key]),
          `${locale} placeholder drift for ${key}`
        );
      }
    }
  });

  it('localizes the functional harness, native notification, and widget plural surfaces', () => {
    assert.equal(t('hi-IN', 'functionalConsole.execute'), 'चलाएँ');
    assert.match(t('en-Hinglish', 'notification.recovery.body'), /Recovery|recovery/);
    assert.equal(
      tc('hi-IN', 2, {
        one: 'feature.home.widget.tile.pendingApprovals.title.one',
        other: 'feature.home.widget.tile.pendingApprovals.title.other'
      }),
      '2 संदेश समीक्षा के लिए'
    );
    assert.equal(t('en-IN', 'functionalConsole.runtime', { phase: 'ready' }), 'Runtime: ready');
  });

  it('resolves locale preferences and preserves locale-aware value formatting', () => {
    assert.equal(resolveLocale('fr-FR'), 'en-IN');
    assert.equal(resolveLocale('hi'), 'hi-IN');
    assert.equal(resolveLocale('hi-Deva-IN'), 'hi-IN');
    assert.equal(resolveLocale('en-Hinglish'), 'en-Hinglish');

    const iso = '2026-07-09T00:00:00.000Z';
    assert.match(formatDateForLocale(iso, 'en-IN'), /2026/);
    assert.equal(formatDateForLocale(undefined, 'hi-IN'), 'शेड्यूल नहीं है');
    assert.equal(formatDateForLocale('not-a-date', 'en-Hinglish'), 'Date valid nahi hai');
    assert.match(formatMonthForLocale('2026-07', 'hi-IN'), /2026/);
    assert.equal(formatMonthForLocale('bad-month', 'en-Hinglish'), 'Date valid nahi hai');
    assert.match(formatCurrencyForLocale(2500, 'en-IN'), /₹|INR/);
  });

  it('keeps locale metadata aligned to the supported locale list', () => {
    assert.deepEqual(new Set(supportedLocales), new Set(Object.keys(localeMetadata)));
    for (const locale of supportedLocales) {
      assert.equal(localeMetadata[locale].locale, locale);
      assert.ok(localeMetadata[locale].label.length > 0);
      assert.equal(localeMetadata[locale].currency, 'INR');
    }
  });
});
