import React from 'react';
import { AppState, Text, type AppStateStatus } from 'react-native';
import {
  act,
  cleanup,
  render,
  screen,
  waitFor,
} from '@testing-library/react-native';
import { getLocales } from 'react-native-localize';

import {
  LocalizationProvider,
  useAppLocalization,
} from './LocalizationProvider';
import { appI18n } from './i18n';

jest.mock('react-native-localize', () => ({
  getLocales: jest.fn(() => [{ languageCode: 'en' }]),
}));

const mockGetLocales = getLocales as jest.MockedFunction<typeof getLocales>;

function LanguageProbe() {
  const { language } = useAppLocalization();
  return <Text testID="language-probe">{language}</Text>;
}

afterEach(async () => {
  cleanup();
  mockGetLocales.mockReturnValue([
    {
      languageCode: 'en',
      countryCode: 'IN',
      languageTag: 'en-IN',
      isRTL: false,
    },
  ]);
  await appI18n.changeLanguage('en');
  jest.restoreAllMocks();
});

it('refreshes the device language when the app returns to the foreground', async () => {
  let appStateHandler: ((state: AppStateStatus) => void) | undefined;
  jest
    .spyOn(AppState, 'addEventListener')
    .mockImplementation((_event, handler) => {
      appStateHandler = handler;
      return { remove: jest.fn() };
    });
  await appI18n.changeLanguage('en');

  await render(
    <LocalizationProvider>
      <LanguageProbe />
    </LocalizationProvider>,
  );
  expect(screen.getByTestId('language-probe').props.children).toBe('en');

  mockGetLocales.mockReturnValue([
    {
      languageCode: 'hi',
      countryCode: 'IN',
      languageTag: 'hi-IN',
      isRTL: false,
    },
  ]);
  await act(async () => appStateHandler?.('background'));
  await act(async () => appStateHandler?.('active'));

  await waitFor(() =>
    expect(screen.getByTestId('language-probe').props.children).toBe('hi'),
  );
});

it('does not overwrite an explicit in-session language on mount or backgrounding', async () => {
  let appStateHandler: ((state: AppStateStatus) => void) | undefined;
  jest
    .spyOn(AppState, 'addEventListener')
    .mockImplementation((_event, handler) => {
      appStateHandler = handler;
      return { remove: jest.fn() };
    });
  await appI18n.changeLanguage('hi');

  await render(
    <LocalizationProvider>
      <LanguageProbe />
    </LocalizationProvider>,
  );
  await act(async () => appStateHandler?.('background'));

  expect(screen.getByTestId('language-probe').props.children).toBe('hi');
});
