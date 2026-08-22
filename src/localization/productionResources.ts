import { liveEnglish, liveHindi } from './liveResources';

const productionEnglish = {
  ...liveEnglish,
  'home.title': 'WishWell',
  'settings.title': 'Settings',
  'tabs.home': 'Home',
  'tabs.people': 'People',
  'tabs.settings': 'Settings',
} as const;

const productionHindi: Record<keyof typeof productionEnglish, string> = {
  ...liveHindi,
  'home.title': 'WishWell',
  'settings.title': 'सेटिंग',
  'tabs.home': 'होम',
  'tabs.people': 'लोग',
  'tabs.settings': 'सेटिंग',
};

export const productionResources = {
  en: { translation: productionEnglish },
  hi: { translation: productionHindi },
} as const;

export type TranslationKey = keyof typeof productionEnglish;
