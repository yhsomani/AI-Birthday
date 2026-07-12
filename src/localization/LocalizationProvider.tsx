import React, { PropsWithChildren, useEffect } from 'react';
import { AppState, type AppStateStatus } from 'react-native';
import { I18nextProvider, useTranslation } from 'react-i18next';

import { appI18n, normalizeLanguage, refreshDeviceLanguage } from './i18n';

export function LocalizationProvider({ children }: PropsWithChildren) {
  useEffect(() => {
    let priorState: AppStateStatus = AppState.currentState;
    const subscription = AppState.addEventListener('change', nextState => {
      const returningToForeground =
        nextState === 'active' && priorState !== 'active';
      priorState = nextState;
      if (returningToForeground) {
        refreshDeviceLanguage().catch(() => undefined);
      }
    });
    return () => subscription.remove();
  }, []);

  return <I18nextProvider i18n={appI18n}>{children}</I18nextProvider>;
}

export const useAppLocalization = () => {
  const { i18n, t } = useTranslation();
  const language = normalizeLanguage(i18n.resolvedLanguage ?? i18n.language);

  return {
    language,
    isRtlFixture: language === 'ar-XB',
    t,
  };
};
