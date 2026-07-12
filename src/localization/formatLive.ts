import type { AppLanguage } from './i18n';

const localeFor = (language: AppLanguage): string =>
  language === 'hi' ? 'hi-IN' : 'en-IN';

export const formatLiveInstant = (
  value: string,
  language: AppLanguage,
): string => {
  const parsed = new Date(value);
  if (!Number.isFinite(parsed.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(localeFor(language), {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(parsed);
};

export const formatLiveDate = (
  value: string,
  language: AppLanguage,
): string => {
  const parsed = new Date(`${value}T12:00:00Z`);
  if (!Number.isFinite(parsed.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(localeFor(language), {
    day: 'numeric',
    month: 'long',
    timeZone: 'UTC',
    year: 'numeric',
  }).format(parsed);
};
