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

it('exposes a disabled state to screen readers when not editable', async () => {
  await renderWithTheme(
    <AccessibleTextInput
      accessibilityLabel="Read only field"
      editable={false}
      testID="disabled-input"
    />,
  );

  const input = screen.getByTestId('disabled-input');
  expect(input.props.accessibilityState).toEqual({ disabled: true });
});

it('merges an explicitly provided accessibilityState', async () => {
  await renderWithTheme(
    <AccessibleTextInput
      accessibilityLabel="Read only field"
      accessibilityState={{ busy: true }}
      editable={false}
      testID="disabled-busy-input"
    />,
  );

  const input = screen.getByTestId('disabled-busy-input');
  expect(input.props.accessibilityState).toEqual({
    busy: true,
    disabled: true,
  });
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
