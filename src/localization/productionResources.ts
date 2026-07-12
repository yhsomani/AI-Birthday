import { liveEnglish, liveHindi } from './liveResources';

const productionEnglish = {
  ...liveEnglish,
  'home.title': 'Birthday Autopilot',
  'settings.title': 'Settings',
  'tabs.home': 'Home',
  'tabs.people': 'People',
  'tabs.settings': 'Settings',
} as const;

const productionHindi: Record<keyof typeof productionEnglish, string> = {
  ...liveHindi,
  'home.title': 'Birthday Autopilot',
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
