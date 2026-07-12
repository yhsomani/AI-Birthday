import i18next from 'i18next';
import { initReactI18next } from 'react-i18next';
import { getLocales } from 'react-native-localize';

import { productionResources } from './productionResources';

export type AppLanguage = 'en' | 'hi' | 'ar-XB';

const supportedLanguage = (languageCode?: string): AppLanguage => {
  if (languageCode === 'hi') {
    return 'hi';
  }

  return 'en';
};

const resolveInitialLanguage = (): AppLanguage => {
  try {
    return supportedLanguage(getLocales()[0]?.languageCode);
  } catch {
    return 'en';
  }
};

export const appI18n = i18next.createInstance();

export const appI18nReady = appI18n.use(initReactI18next).init({
  resources: productionResources,
  lng: resolveInitialLanguage(),
  fallbackLng: 'en',
  supportedLngs: ['en', 'hi', 'ar-XB'],
  interpolation: { escapeValue: false },
  initAsync: false,
  returnNull: false,
});

export const normalizeLanguage = (language?: string): AppLanguage => {
  if (language?.toLowerCase().startsWith('hi')) {
    return 'hi';
  }
  if (language === 'ar-XB') {
    return 'ar-XB';
  }
  return 'en';
};

export const formatFixtureDate = (isoDate: string, language: AppLanguage) => {
  const locale = language === 'hi' ? 'hi-IN' : 'en-IN';
  const formatted = new Intl.DateTimeFormat(locale, {
    weekday: 'short',
    day: 'numeric',
    month: 'long',
    timeZone: 'UTC',
  }).format(new Date(`${isoDate}T12:00:00Z`));

  return language === 'ar-XB' ? `⟦ ${formatted} ··· ⟧` : formatted;
};
