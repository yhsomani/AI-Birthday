import React from 'react';
import { StyleSheet } from 'react-native';
import { fireEvent, render, screen } from '@testing-library/react-native';

import { ThemeProvider } from '../../app/providers/ThemeProvider';
import { AccessibleTextInput } from './AccessibleTextInput';

const renderWithTheme = (input: React.ReactElement) =>
  render(<ThemeProvider>{input}</ThemeProvider>);

it('forces a normalized label and the 200 percent text scaling contract', async () => {
  const untrustedProps = {
    allowFontScaling: false,
    maxFontSizeMultiplier: 1,
  } as unknown as React.ComponentProps<typeof AccessibleTextInput>;

  await renderWithTheme(
    <AccessibleTextInput
      {...untrustedProps}
      accessibilityLabel="  Birthday phone number  "
      accessibilityHint="Used only for this test"
      testID="accessible-input"
    />,
  );

  const input = screen.getByTestId('accessible-input');
  expect(input.props.accessibilityLabel).toBe('Birthday phone number');
  expect(input.props.accessibilityHint).toBe('Used only for this test');
  expect(input.props.allowFontScaling).toBe(true);
  expect(input.props.maxFontSizeMultiplier).toBe(2);

  await fireEvent(input, 'focus');
  expect(
    StyleSheet.flatten(screen.getByTestId('accessible-input').props.style)
      .outlineWidth,
  ).toBe(3);
  await fireEvent(screen.getByTestId('accessible-input'), 'blur');
  expect(
    StyleSheet.flatten(screen.getByTestId('accessible-input').props.style)
      .outlineWidth,
  ).toBe(0);
});

it('rejects an empty accessibility label', async () => {
  await expect(
    (async () => {
      await renderWithTheme(<AccessibleTextInput accessibilityLabel="   " />);
    })(),
  ).rejects.toThrow(
    'AccessibleTextInput requires a non-empty accessibilityLabel',
  );
});

it('merges accessibilityState and properly computes disabled state', async () => {
  await renderWithTheme(
    <AccessibleTextInput
      accessibilityLabel="Disabled test"
      accessibilityState={{ expanded: true }}
      editable={false}
      testID="accessible-input-disabled"
    />,
  );

  const input = screen.getByTestId('accessible-input-disabled');
  expect(input.props.accessibilityState).toEqual({
    disabled: true,
    expanded: true,
  });
});
