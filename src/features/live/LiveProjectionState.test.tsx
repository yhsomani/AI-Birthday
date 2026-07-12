import React from 'react';
import { AccessibilityInfo, Platform } from 'react-native';
import {
  act,
  cleanup,
  render,
  screen,
  waitFor,
} from '@testing-library/react-native';

import { ThemeProvider } from '../../app/providers/ThemeProvider';
import { LocalizationProvider } from '../../localization/LocalizationProvider';
import {
  LiveActionFeedback,
  LiveError,
  LiveLoading,
  LiveValidationError,
} from './LiveProjectionState';

jest.mock('react-native-localize', () => ({
  getLocales: () => [{ languageCode: 'en' }],
}));

const originalPlatform = Platform.OS;

function Providers({ children }: React.PropsWithChildren) {
  return (
    <LocalizationProvider>
      <ThemeProvider>{children}</ThemeProvider>
    </LocalizationProvider>
  );
}

function setPlatform(platform: 'android' | 'ios') {
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: platform,
  });
}

function deferred<Value>() {
  let resolve!: (value: Value) => void;
  const promise = new Promise<Value>(resolver => {
    resolve = resolver;
  });
  return { promise, resolve };
}

beforeEach(() => {
  jest.clearAllMocks();
  setPlatform('ios');
  jest
    .spyOn(AccessibilityInfo, 'isScreenReaderEnabled')
    .mockResolvedValue(true);
  jest
    .spyOn(AccessibilityInfo, 'announceForAccessibilityWithOptions')
    .mockImplementation(() => undefined);
});

afterEach(async () => {
  await cleanup();
  jest.restoreAllMocks();
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: originalPlatform,
  });
});

it('keeps Android live regions and does not invoke the iOS announcement path', async () => {
  setPlatform('android');
  await render(
    <Providers>
      <LiveLoading label="Loading protected status" />
      <LiveActionFeedback message="Status checked again" />
      <LiveValidationError
        message="Enter a valid phone number"
        testID="validation-alert"
      />
    </Providers>,
  );

  const loading = screen.getByTestId('live-projection-loading');
  expect(loading.props.accessibilityRole).toBe('progressbar');
  expect(loading.props.accessibilityLiveRegion).toBe('polite');
  expect(loading.props.accessibilityLabel).toBe('Loading protected status');
  expect(
    screen.getByTestId('live-action-feedback-success').props
      .accessibilityLiveRegion,
  ).toBe('polite');
  const validation = screen.getByTestId('validation-alert');
  expect(validation.props.accessibilityRole).toBe('alert');
  expect(validation.props.accessibilityLiveRegion).toBe('assertive');
  expect(AccessibilityInfo.isScreenReaderEnabled).not.toHaveBeenCalled();
  expect(
    AccessibilityInfo.announceForAccessibilityWithOptions,
  ).not.toHaveBeenCalled();
});

it('queues iOS loading and success announcements without repeating a stable render', async () => {
  const view = await render(
    <React.StrictMode>
      <Providers>
        <LiveLoading label="Loading protected status" />
      </Providers>
    </React.StrictMode>,
  );

  await waitFor(() =>
    expect(
      AccessibilityInfo.announceForAccessibilityWithOptions,
    ).toHaveBeenCalledWith('Loading protected status', { queue: true }),
  );
  expect(
    screen.getByTestId('live-projection-loading').props.accessibilityLiveRegion,
  ).toBeUndefined();

  await view.rerender(
    <React.StrictMode>
      <Providers>
        <LiveLoading label="Loading protected status" />
      </Providers>
    </React.StrictMode>,
  );
  expect(
    AccessibilityInfo.announceForAccessibilityWithOptions,
  ).toHaveBeenCalledTimes(1);

  await view.rerender(
    <React.StrictMode>
      <Providers>
        <LiveActionFeedback message="Status checked again" />
      </Providers>
    </React.StrictMode>,
  );
  await waitFor(() =>
    expect(
      AccessibilityInfo.announceForAccessibilityWithOptions,
    ).toHaveBeenLastCalledWith(
      'Protected service response. Status checked again',
      { queue: true },
    ),
  );
  expect(
    screen.getByTestId('live-action-feedback-success').props
      .accessibilityLiveRegion,
  ).toBeUndefined();

  await view.rerender(
    <React.StrictMode>
      <Providers>
        <LiveActionFeedback />
      </Providers>
    </React.StrictMode>,
  );
  await view.rerender(
    <React.StrictMode>
      <Providers>
        <LiveActionFeedback message="Status checked again" />
      </Providers>
    </React.StrictMode>,
  );
  await waitFor(() =>
    expect(
      AccessibilityInfo.announceForAccessibilityWithOptions,
    ).toHaveBeenCalledTimes(3),
  );
});

it('interrupts VoiceOver for blocking and validation errors without duplicate iOS alert semantics', async () => {
  const view = await render(
    <Providers>
      <LiveError
        onRetry={jest.fn()}
        problem={{
          kind: 'temporarily-unavailable',
          code: 'coordination-unavailable',
        }}
        title="Protected status unavailable"
      />
    </Providers>,
  );

  await waitFor(() =>
    expect(
      AccessibilityInfo.announceForAccessibilityWithOptions,
    ).toHaveBeenCalledWith(
      expect.stringContaining('Protected status unavailable'),
      { queue: false },
    ),
  );
  const error = screen.getByTestId('live-projection-error');
  expect(error.props.accessibilityRole).toBeUndefined();
  expect(error.props.accessibilityLiveRegion).toBeUndefined();

  await view.rerender(
    <Providers>
      <LiveValidationError
        message="Enter a valid phone number"
        testID="validation-alert"
      />
    </Providers>,
  );
  await waitFor(() =>
    expect(
      AccessibilityInfo.announceForAccessibilityWithOptions,
    ).toHaveBeenLastCalledWith('Enter a valid phone number', { queue: false }),
  );
  const validation = screen.getByTestId('validation-alert');
  expect(validation.props.accessibilityRole).toBeUndefined();
  expect(validation.props.accessibilityLiveRegion).toBeUndefined();
});

it('drops a stale iOS announcement when its rendered message changes', async () => {
  const firstCheck = deferred<boolean>();
  const secondCheck = deferred<boolean>();
  jest
    .mocked(AccessibilityInfo.isScreenReaderEnabled)
    .mockReset()
    .mockReturnValueOnce(firstCheck.promise)
    .mockReturnValueOnce(secondCheck.promise);

  const view = await render(
    <Providers>
      <LiveLoading label="Loading old status" />
    </Providers>,
  );
  await view.rerender(
    <Providers>
      <LiveLoading label="Loading current status" />
    </Providers>,
  );

  await act(async () => firstCheck.resolve(true));
  expect(
    AccessibilityInfo.announceForAccessibilityWithOptions,
  ).not.toHaveBeenCalled();
  await act(async () => secondCheck.resolve(true));
  expect(
    AccessibilityInfo.announceForAccessibilityWithOptions,
  ).toHaveBeenCalledTimes(1);
  expect(
    AccessibilityInfo.announceForAccessibilityWithOptions,
  ).toHaveBeenCalledWith('Loading current status', { queue: true });
});

it('does not announce after unmount or when VoiceOver is disabled', async () => {
  const pendingCheck = deferred<boolean>();
  jest
    .mocked(AccessibilityInfo.isScreenReaderEnabled)
    .mockReset()
    .mockReturnValueOnce(pendingCheck.promise)
    .mockResolvedValueOnce(false);

  const view = await render(
    <Providers>
      <LiveLoading label="Loading removed status" />
    </Providers>,
  );
  await view.unmount();
  await act(async () => pendingCheck.resolve(true));
  expect(
    AccessibilityInfo.announceForAccessibilityWithOptions,
  ).not.toHaveBeenCalled();

  await render(
    <Providers>
      <LiveActionFeedback message="Hidden from VoiceOver" />
    </Providers>,
  );
  await waitFor(() =>
    expect(AccessibilityInfo.isScreenReaderEnabled).toHaveBeenCalledTimes(2),
  );
  expect(
    AccessibilityInfo.announceForAccessibilityWithOptions,
  ).not.toHaveBeenCalled();
});
