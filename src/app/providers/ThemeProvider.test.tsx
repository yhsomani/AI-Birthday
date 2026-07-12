import React from 'react';
import { AccessibilityInfo, Platform, Text } from 'react-native';
import {
  act,
  cleanup,
  render,
  screen,
  waitFor,
} from '@testing-library/react-native';

import { ThemeProvider, useAppTheme } from './ThemeProvider';

jest.mock('react-native-localize', () => ({
  getLocales: () => [{ languageCode: 'en' }],
}));

type BooleanListener = (enabled: boolean) => void;

const listeners = new Map<string, BooleanListener>();
const removers: jest.Mock[] = [];
const originalPlatform = Platform.OS;

function ThemeProbe() {
  const { isHighContrast, isReduceMotionEnabled } = useAppTheme();
  return (
    <Text testID="theme-probe">
      {JSON.stringify({ isHighContrast, isReduceMotionEnabled })}
    </Text>
  );
}

function setPlatform(platform: 'android' | 'ios') {
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: platform,
  });
}

async function emit(event: string, enabled: boolean) {
  const listener = listeners.get(event);
  if (!listener) {
    throw new Error(`No listener registered for ${event}`);
  }
  await act(async () => listener(enabled));
}

beforeEach(() => {
  jest.clearAllMocks();
  listeners.clear();
  removers.length = 0;
  setPlatform('ios');
  jest
    .spyOn(AccessibilityInfo, 'addEventListener')
    .mockImplementation(
      ((event: string, listener: BooleanListener) => {
        listeners.set(event, listener);
        const remove = jest.fn(() => listeners.delete(event));
        removers.push(remove);
        return { remove };
      }) as never,
    );
  jest
    .spyOn(AccessibilityInfo, 'isDarkerSystemColorsEnabled')
    .mockResolvedValue(false);
  jest
    .spyOn(AccessibilityInfo, 'isHighTextContrastEnabled')
    .mockResolvedValue(false);
  jest
    .spyOn(AccessibilityInfo, 'isReduceMotionEnabled')
    .mockResolvedValue(false);
});

afterEach(async () => {
  await cleanup();
  jest.restoreAllMocks();
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: originalPlatform,
  });
});

it('uses iOS darker-system-colors state and responds to both preferences', async () => {
  jest
    .mocked(AccessibilityInfo.isDarkerSystemColorsEnabled)
    .mockResolvedValue(true);

  const view = await render(
    <ThemeProvider>
      <ThemeProbe />
    </ThemeProvider>,
  );

  await waitFor(() =>
    expect(screen.getByTestId('theme-probe').props.children).toBe(
      '{"isHighContrast":true,"isReduceMotionEnabled":false}',
    ),
  );
  expect(AccessibilityInfo.isHighTextContrastEnabled).not.toHaveBeenCalled();

  await emit('darkerSystemColorsChanged', false);
  await emit('reduceMotionChanged', true);
  await waitFor(() =>
    expect(screen.getByTestId('theme-probe').props.children).toBe(
      '{"isHighContrast":false,"isReduceMotionEnabled":true}',
    ),
  );

  await view.unmount();
  expect(removers).toHaveLength(2);
  for (const remove of removers) expect(remove).toHaveBeenCalledTimes(1);
});

it('uses Android high-text-contrast state', async () => {
  setPlatform('android');
  jest
    .mocked(AccessibilityInfo.isHighTextContrastEnabled)
    .mockResolvedValue(true);

  await render(
    <ThemeProvider>
      <ThemeProbe />
    </ThemeProvider>,
  );

  await waitFor(() =>
    expect(screen.getByTestId('theme-probe').props.children).toContain(
      '"isHighContrast":true',
    ),
  );
  expect(AccessibilityInfo.isDarkerSystemColorsEnabled).not.toHaveBeenCalled();
  expect(listeners.has('highTextContrastChanged')).toBe(true);
});

it('fails safely when platform accessibility preferences cannot be read', async () => {
  jest
    .mocked(AccessibilityInfo.isDarkerSystemColorsEnabled)
    .mockRejectedValue(new Error('unavailable'));
  jest
    .mocked(AccessibilityInfo.isReduceMotionEnabled)
    .mockRejectedValue(new Error('unavailable'));

  await render(
    <ThemeProvider>
      <ThemeProbe />
    </ThemeProvider>,
  );

  await waitFor(() =>
    expect(screen.getByTestId('theme-probe').props.children).toBe(
      '{"isHighContrast":false,"isReduceMotionEnabled":false}',
    ),
  );
});
