import React, { PropsWithChildren, useCallback } from 'react';
import { I18nextProvider, useTranslation } from 'react-i18next';

import { AppLanguage, appI18n, normalizeLanguage } from './i18n';

export function LocalizationProvider({ children }: PropsWithChildren) {
  return <I18nextProvider i18n={appI18n}>{children}</I18nextProvider>;
}

export const useAppLocalization = () => {
  const { i18n, t } = useTranslation();
  const language = normalizeLanguage(i18n.resolvedLanguage ?? i18n.language);
  const setLanguage = useCallback(
    (nextLanguage: AppLanguage) => {
      i18n.changeLanguage(nextLanguage).catch(() => undefined);
    },
    [i18n],
  );

  return {
    language,
    isRtlFixture: language === 'ar-XB',
    setLanguage,
    t,
  };
};
