import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  formatCurrencyForLocale,
  formatDateForLocale,
  resolveLocale,
  supportedLocales,
  t,
  translations,
  type TranslationKey
} from './i18n';

describe('localization contract', () => {
  it('keeps every supported locale complete for core UI keys', () => {
    const englishKeys = Object.keys(translations['en-IN']) as TranslationKey[];

    for (const locale of supportedLocales) {
      for (const key of englishKeys) {
        assert.equal(typeof translations[locale][key], 'string', `${locale} missing ${key}`);
        assert.ok(translations[locale][key].trim().length > 0, `${locale} has blank ${key}`);
      }
    }
  });

  it('falls back unsupported locales to English India', () => {
    assert.equal(resolveLocale('fr-FR'), 'en-IN');
    assert.equal(t(resolveLocale(undefined), 'nav.home'), 'Home');
  });

  it('formats dates and currency through the selected locale', () => {
    const iso = '2026-07-09T00:00:00.000Z';

    assert.match(formatDateForLocale(iso, 'en-IN'), /2026/);
    assert.match(formatDateForLocale(undefined, 'hi-IN'), /शेड्यूल/);
    assert.match(formatCurrencyForLocale(2500, 'en-IN'), /₹|INR/);
  });
});
