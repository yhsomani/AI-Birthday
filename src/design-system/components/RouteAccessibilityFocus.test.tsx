import React from 'react';
import { AccessibilityInfo } from 'react-native';
import { render, screen, waitFor } from '@testing-library/react-native';

import { RouteAccessibilityFocus } from './RouteAccessibilityFocus';

it('moves native accessibility focus whenever the app-owned route changes', async () => {
  const reactNative = require('react-native') as {
    findNodeHandle: (component: unknown) => number | null;
  };
  jest.spyOn(reactNative, 'findNodeHandle').mockReturnValue(42);
  const focus = jest
    .spyOn(AccessibilityInfo, 'setAccessibilityFocus')
    .mockImplementation(() => undefined);
  const view = await render(
    <RouteAccessibilityFocus announcement="Home" routeKey="tab:home" />,
  );

  await waitFor(() => expect(focus).toHaveBeenCalledWith(42));
  focus.mockClear();

  await view.rerender(
    <RouteAccessibilityFocus announcement="People" routeKey="tab:people" />,
  );

  await waitFor(() => expect(focus).toHaveBeenCalledWith(42));
  expect(
    screen.getByTestId('route-accessibility-focus').props.accessibilityLabel,
  ).toBe('People');
});
