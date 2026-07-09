import type { SupportedLocale } from '../domain/types';

export type TranslationKey =
  | 'app.tagline'
  | 'nav.home'
  | 'nav.events'
  | 'nav.messages'
  | 'nav.contacts'
  | 'nav.more'
  | 'common.notScheduled'
  | 'settings.language'
  | 'settings.languageDetail'
  | 'settings.languageEnglish'
  | 'settings.languageHindi'
  | 'settings.languageHinglish'
  | 'settings.languageFallback'
  | 'status.on'
  | 'status.off';

type LocaleMetadata = {
  locale: SupportedLocale;
  label: string;
  dateLocale: string;
  currencyLocale: string;
  currency: 'INR';
};

export const localeMetadata: Record<SupportedLocale, LocaleMetadata> = {
  'en-IN': {
    locale: 'en-IN',
    label: 'English',
    dateLocale: 'en-IN',
    currencyLocale: 'en-IN',
    currency: 'INR'
  },
  'hi-IN': {
    locale: 'hi-IN',
    label: 'Hindi',
    dateLocale: 'hi-IN',
    currencyLocale: 'hi-IN',
    currency: 'INR'
  },
  'en-Hinglish': {
    locale: 'en-Hinglish',
    label: 'Hinglish',
    dateLocale: 'en-IN',
    currencyLocale: 'en-IN',
    currency: 'INR'
  }
};

export const supportedLocales = Object.keys(localeMetadata) as SupportedLocale[];

export const translations: Record<SupportedLocale, Record<TranslationKey, string>> = {
  'en-IN': {
    'app.tagline': 'Remember, write, review, send.',
    'nav.home': 'Home',
    'nav.events': 'Events',
    'nav.messages': 'Messages',
    'nav.contacts': 'Contacts',
    'nav.more': 'More',
    'common.notScheduled': 'Not scheduled',
    'settings.language': 'Language',
    'settings.languageDetail': 'Dates, currency, navigation, and status labels follow this preference.',
    'settings.languageEnglish': 'English',
    'settings.languageHindi': 'Hindi',
    'settings.languageHinglish': 'Hinglish',
    'settings.languageFallback': 'Unsupported language settings fall back to English.',
    'status.on': 'on',
    'status.off': 'off'
  },
  'hi-IN': {
    'app.tagline': 'याद रखें, लिखें, समीक्षा करें, भेजें.',
    'nav.home': 'होम',
    'nav.events': 'इवेंट',
    'nav.messages': 'संदेश',
    'nav.contacts': 'संपर्क',
    'nav.more': 'अधिक',
    'common.notScheduled': 'शेड्यूल नहीं है',
    'settings.language': 'भाषा',
    'settings.languageDetail': 'तारीख, मुद्रा, नेविगेशन और स्थिति लेबल इस पसंद का उपयोग करते हैं.',
    'settings.languageEnglish': 'अंग्रेजी',
    'settings.languageHindi': 'हिंदी',
    'settings.languageHinglish': 'हिंग्लिश',
    'settings.languageFallback': 'असमर्थित भाषा सेटिंग अंग्रेजी पर वापस आती है.',
    'status.on': 'चालू',
    'status.off': 'बंद'
  },
  'en-Hinglish': {
    'app.tagline': 'Yaad rakho, likho, review karo, send karo.',
    'nav.home': 'Home',
    'nav.events': 'Events',
    'nav.messages': 'Messages',
    'nav.contacts': 'Contacts',
    'nav.more': 'More',
    'common.notScheduled': 'Schedule nahi hai',
    'settings.language': 'Language',
    'settings.languageDetail': 'Dates, currency, navigation, aur status labels is preference ko follow karte hain.',
    'settings.languageEnglish': 'English',
    'settings.languageHindi': 'Hindi',
    'settings.languageHinglish': 'Hinglish',
    'settings.languageFallback': 'Unsupported language settings English par fallback karte hain.',
    'status.on': 'on',
    'status.off': 'off'
  }
};

export const resolveLocale = (locale: string | undefined): SupportedLocale =>
  supportedLocales.includes(locale as SupportedLocale) ? (locale as SupportedLocale) : 'en-IN';

export const t = (locale: SupportedLocale, key: TranslationKey): string =>
  translations[locale]?.[key] ?? translations['en-IN'][key];

export const formatDateForLocale = (iso: string | undefined, locale: SupportedLocale) => {
  if (!iso) {
    return t(locale, 'common.notScheduled');
  }
  return new Intl.DateTimeFormat(localeMetadata[locale].dateLocale, {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  }).format(new Date(iso));
};

export const formatCurrencyForLocale = (amount: number, locale: SupportedLocale) =>
  new Intl.NumberFormat(localeMetadata[locale].currencyLocale, {
    style: 'currency',
    currency: localeMetadata[locale].currency,
    maximumFractionDigits: 0
  }).format(amount);
