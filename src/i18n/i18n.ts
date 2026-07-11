import type { SupportedLocale } from '../domain/types';

// Only strings emitted by the temporary functional harness or native surfaces
// belong here. The future Figma UI will define its own presentation catalog.
const englishTranslations = {
  'functionalConsole.title': 'RelateAI functional console',
  'functionalConsole.runtime': 'Runtime: {phase}',
  'functionalConsole.stateSummary': 'State summary',
  'functionalConsole.command': 'Command',
  'functionalConsole.commandJson': 'Functional command JSON',
  'functionalConsole.secret': 'Sensitive command secret',
  'functionalConsole.execute': 'Execute',
  'functionalConsole.running': 'Running',
  'functionalConsole.operations': 'Operations',
  'functionalConsole.operationsEmpty': 'No operations recorded.',
  'functionalConsole.issues': 'Operational issues',
  'functionalConsole.issuesEmpty': 'No active operational issues.',
  'functionalConsole.result.none': 'No command has run.',
  'functionalConsole.result.failed': 'Command failed without exposing private content. Review operational issues.',
  'notification.event.title': 'RelateAI event reminder',
  'notification.event.body': 'Open RelateAI to review an upcoming relationship event.',
  'notification.approval.title': 'Message review reminder',
  'notification.approval.body': 'A prepared message is waiting for your review.',
  'notification.fallback.title': 'Fallback draft ready',
  'notification.fallback.body': 'A local fallback draft needs review before any handoff.',
  'notification.setup.title': 'RelateAI setup reminder',
  'notification.setup.body': 'Open RelateAI to review a setup or backup item.',
  'notification.recovery.title': 'RelateAI recovery reminder',
  'notification.recovery.body': 'Open RelateAI to review a safe recovery step.',
  'notification.checkIn.title': 'Relationship check-in',
  'notification.checkIn.body': 'A relationship check-in is due for review.',
  'feature.home.widget.summaryTitle': 'RelateAI today',
  'feature.home.widget.summaryReady.one': '{count} safe shortcut ready.',
  'feature.home.widget.summaryReady.other': '{count} safe shortcuts ready.',
  'feature.home.widget.summaryEmpty': 'No relationship actions need attention.',
  'feature.home.widget.privacyNote':
    'Widget summaries avoid message bodies, phone numbers, email addresses, private notes, and send actions.',
  'feature.home.widget.emptyState': 'No events or approvals need attention.',
  'feature.home.widget.tile.todayEvents.title.one': '{count} event today',
  'feature.home.widget.tile.todayEvents.title.other': '{count} events today',
  'feature.home.widget.tile.todayEvents.detail': 'Open Events to prepare and review reminders.',
  'feature.home.widget.tile.todayEvents.accessibility.one': '{count} relationship event today. Open Events.',
  'feature.home.widget.tile.todayEvents.accessibility.other': '{count} relationship events today. Open Events.',
  'feature.home.widget.tile.pendingApprovals.title.one': '{count} message to review',
  'feature.home.widget.tile.pendingApprovals.title.other': '{count} messages to review',
  'feature.home.widget.tile.pendingApprovals.detail': 'Open Messages to approve, edit, reject, or retry.',
  'feature.home.widget.tile.pendingApprovals.accessibility.one': '{count} message waiting for review. Open Messages.',
  'feature.home.widget.tile.pendingApprovals.accessibility.other':
    '{count} messages waiting for review. Open Messages.',
  'common.notScheduled': 'Not scheduled',
  'common.invalidDate': 'Invalid date'
} as const;

export type TranslationKey = keyof typeof englishTranslations;
type TranslationValues = Record<string, string | number | boolean | null | undefined>;

type LocaleMetadata = Readonly<{
  locale: SupportedLocale;
  label: string;
  dateLocale: string;
  currencyLocale: string;
  currency: 'INR';
}>;

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

export const translations = {
  'en-IN': englishTranslations,
  'hi-IN': {
    'functionalConsole.title': 'RelateAI कार्यात्मक कंसोल',
    'functionalConsole.runtime': 'रनटाइम: {phase}',
    'functionalConsole.stateSummary': 'स्थिति सारांश',
    'functionalConsole.command': 'कमांड',
    'functionalConsole.commandJson': 'कार्यात्मक कमांड JSON',
    'functionalConsole.secret': 'संवेदनशील कमांड गोपनीय मान',
    'functionalConsole.execute': 'चलाएँ',
    'functionalConsole.running': 'चल रहा है',
    'functionalConsole.operations': 'कार्य',
    'functionalConsole.operationsEmpty': 'कोई कार्य दर्ज नहीं है.',
    'functionalConsole.issues': 'सक्रिय समस्याएँ',
    'functionalConsole.issuesEmpty': 'कोई सक्रिय समस्या नहीं है.',
    'functionalConsole.result.none': 'अभी कोई कमांड नहीं चला है.',
    'functionalConsole.result.failed': 'निजी सामग्री दिखाए बिना कमांड विफल हुआ. सक्रिय समस्याओं की समीक्षा करें.',
    'notification.event.title': 'RelateAI इवेंट रिमाइंडर',
    'notification.event.body': 'आने वाले रिश्ते के इवेंट की समीक्षा के लिए RelateAI खोलें.',
    'notification.approval.title': 'संदेश समीक्षा रिमाइंडर',
    'notification.approval.body': 'एक तैयार संदेश आपकी समीक्षा की प्रतीक्षा कर रहा है.',
    'notification.fallback.title': 'फ़ॉलबैक ड्राफ़्ट तैयार है',
    'notification.fallback.body': 'किसी भी हैंडऑफ़ से पहले स्थानीय फ़ॉलबैक ड्राफ़्ट की समीक्षा करें.',
    'notification.setup.title': 'RelateAI सेटअप रिमाइंडर',
    'notification.setup.body': 'सेटअप या बैकअप आइटम की समीक्षा के लिए RelateAI खोलें.',
    'notification.recovery.title': 'RelateAI रिकवरी रिमाइंडर',
    'notification.recovery.body': 'सुरक्षित रिकवरी चरण की समीक्षा के लिए RelateAI खोलें.',
    'notification.checkIn.title': 'रिश्ते का चेक-इन',
    'notification.checkIn.body': 'एक रिश्ते का चेक-इन समीक्षा के लिए देय है.',
    'feature.home.widget.summaryTitle': 'RelateAI आज',
    'feature.home.widget.summaryReady.one': '{count} सुरक्षित शॉर्टकट तैयार है.',
    'feature.home.widget.summaryReady.other': '{count} सुरक्षित शॉर्टकट तैयार हैं.',
    'feature.home.widget.summaryEmpty': 'अभी किसी रिश्ते की कार्रवाई पर ध्यान नहीं चाहिए.',
    'feature.home.widget.privacyNote':
      'विजेट सारांश संदेश body, फ़ोन नंबर, ईमेल पते, निजी नोट और भेजने वाली actions से बचते हैं.',
    'feature.home.widget.emptyState': 'अभी कोई इवेंट या approval ध्यान नहीं चाहता.',
    'feature.home.widget.tile.todayEvents.title.one': 'आज {count} इवेंट',
    'feature.home.widget.tile.todayEvents.title.other': 'आज {count} इवेंट',
    'feature.home.widget.tile.todayEvents.detail': 'तैयारी और रिमाइंडर समीक्षा के लिए Events खोलें.',
    'feature.home.widget.tile.todayEvents.accessibility.one': 'आज {count} रिलेशनशिप इवेंट. Events खोलें.',
    'feature.home.widget.tile.todayEvents.accessibility.other': 'आज {count} रिलेशनशिप इवेंट. Events खोलें.',
    'feature.home.widget.tile.pendingApprovals.title.one': '{count} संदेश समीक्षा के लिए',
    'feature.home.widget.tile.pendingApprovals.title.other': '{count} संदेश समीक्षा के लिए',
    'feature.home.widget.tile.pendingApprovals.detail': 'Approve, edit, reject या retry करने के लिए Messages खोलें.',
    'feature.home.widget.tile.pendingApprovals.accessibility.one':
      '{count} संदेश समीक्षा का इंतजार कर रहा है. Messages खोलें.',
    'feature.home.widget.tile.pendingApprovals.accessibility.other':
      '{count} संदेश समीक्षा का इंतजार कर रहे हैं. Messages खोलें.',
    'common.notScheduled': 'शेड्यूल नहीं है',
    'common.invalidDate': 'अमान्य तारीख'
  },
  'en-Hinglish': {
    'functionalConsole.title': 'RelateAI functional console',
    'functionalConsole.runtime': 'Runtime: {phase}',
    'functionalConsole.stateSummary': 'State summary',
    'functionalConsole.command': 'Command',
    'functionalConsole.commandJson': 'Functional command JSON',
    'functionalConsole.secret': 'Sensitive command secret',
    'functionalConsole.execute': 'Chalao',
    'functionalConsole.running': 'Chal raha hai',
    'functionalConsole.operations': 'Operations',
    'functionalConsole.operationsEmpty': 'Koi operation record nahi hua.',
    'functionalConsole.issues': 'Operational issues',
    'functionalConsole.issuesEmpty': 'Koi active operational issue nahi hai.',
    'functionalConsole.result.none': 'Abhi koi command run nahi hua.',
    'functionalConsole.result.failed': 'Private content dikhaye bina command fail hua. Operational issues review karo.',
    'notification.event.title': 'RelateAI event reminder',
    'notification.event.body': 'Upcoming relationship event review karne ke liye RelateAI kholo.',
    'notification.approval.title': 'Message review reminder',
    'notification.approval.body': 'Ek prepared message aapke review ka wait kar raha hai.',
    'notification.fallback.title': 'Fallback draft ready hai',
    'notification.fallback.body': 'Kisi handoff se pehle local fallback draft review karo.',
    'notification.setup.title': 'RelateAI setup reminder',
    'notification.setup.body': 'Setup ya backup item review karne ke liye RelateAI kholo.',
    'notification.recovery.title': 'RelateAI recovery reminder',
    'notification.recovery.body': 'Safe recovery step review karne ke liye RelateAI kholo.',
    'notification.checkIn.title': 'Relationship check-in',
    'notification.checkIn.body': 'Ek relationship check-in review ke liye due hai.',
    'feature.home.widget.summaryTitle': 'RelateAI today',
    'feature.home.widget.summaryReady.one': '{count} safe shortcut ready hai.',
    'feature.home.widget.summaryReady.other': '{count} safe shortcuts ready hain.',
    'feature.home.widget.summaryEmpty': 'Abhi kisi relationship action par attention nahi chahiye.',
    'feature.home.widget.privacyNote':
      'Widget summaries message bodies, phone numbers, email addresses, private notes, aur send actions avoid karte hain.',
    'feature.home.widget.emptyState': 'Abhi koi event ya approval attention nahi chahta.',
    'feature.home.widget.tile.todayEvents.title.one': 'Aaj {count} event',
    'feature.home.widget.tile.todayEvents.title.other': 'Aaj {count} events',
    'feature.home.widget.tile.todayEvents.detail': 'Prepare aur reminders review karne ke liye Events kholo.',
    'feature.home.widget.tile.todayEvents.accessibility.one': 'Aaj {count} relationship event. Events kholo.',
    'feature.home.widget.tile.todayEvents.accessibility.other': 'Aaj {count} relationship events. Events kholo.',
    'feature.home.widget.tile.pendingApprovals.title.one': '{count} message review ke liye',
    'feature.home.widget.tile.pendingApprovals.title.other': '{count} messages review ke liye',
    'feature.home.widget.tile.pendingApprovals.detail': 'Approve, edit, reject, ya retry karne ke liye Messages kholo.',
    'feature.home.widget.tile.pendingApprovals.accessibility.one':
      '{count} message review ka wait kar raha hai. Messages kholo.',
    'feature.home.widget.tile.pendingApprovals.accessibility.other':
      '{count} messages review ka wait kar rahe hain. Messages kholo.',
    'common.notScheduled': 'Schedule nahi hai',
    'common.invalidDate': 'Date valid nahi hai'
  }
} satisfies Record<SupportedLocale, Record<TranslationKey, string>>;

export const resolveLocale = (locale: string | undefined): SupportedLocale => {
  if (supportedLocales.includes(locale as SupportedLocale)) return locale as SupportedLocale;
  const language = locale?.trim().toLowerCase();
  if (language?.startsWith('hi')) return 'hi-IN';
  return 'en-IN';
};

const interpolate = (template: string, values: TranslationValues = {}) =>
  template.replace(/\{([A-Za-z][A-Za-z0-9_]*)\}/g, (_match, token: string) => {
    if (!Object.prototype.hasOwnProperty.call(values, token)) return '';
    const value = values[token];
    return value === undefined || value === null ? '' : String(value);
  });

export const t = (locale: SupportedLocale, key: TranslationKey, values?: TranslationValues): string =>
  interpolate(translations[locale][key] ?? translations['en-IN'][key], values);

export const tc = (
  locale: SupportedLocale,
  count: number,
  keys: { one: TranslationKey; other: TranslationKey },
  values: TranslationValues = {}
): string => {
  const category = new Intl.PluralRules(localeMetadata[locale].dateLocale).select(count);
  return t(locale, category === 'one' ? keys.one : keys.other, { ...values, count });
};

export const formatDateForLocale = (iso: string | undefined, locale: SupportedLocale) => {
  if (!iso) return t(locale, 'common.notScheduled');
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return t(locale, 'common.invalidDate');
  return new Intl.DateTimeFormat(localeMetadata[locale].dateLocale, {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  }).format(date);
};

export const formatMonthForLocale = (monthKey: string | undefined, locale: SupportedLocale) => {
  if (!monthKey) return t(locale, 'common.invalidDate');
  const date = new Date(`${monthKey}-01T12:00:00.000Z`);
  if (Number.isNaN(date.getTime())) return t(locale, 'common.invalidDate');
  return new Intl.DateTimeFormat(localeMetadata[locale].dateLocale, {
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC'
  }).format(date);
};

export const formatCurrencyForLocale = (amount: number, locale: SupportedLocale) =>
  new Intl.NumberFormat(localeMetadata[locale].currencyLocale, {
    style: 'currency',
    currency: localeMetadata[locale].currency,
    maximumFractionDigits: 0
  }).format(amount);
