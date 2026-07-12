import React from 'react';
import { StyleSheet } from 'react-native';
import { fireEvent, render, screen } from '@testing-library/react-native';

import { ThemeProvider } from '../../app/providers/ThemeProvider';
import { LocalizationProvider } from '../../localization/LocalizationProvider';
import {
  Button,
  ChoiceChip,
  LabeledSwitch,
  PersonRow,
  SearchField,
} from './Primitives';

jest.mock('react-native-localize', () => ({
  getLocales: () => [{ languageCode: 'en' }],
}));

function Providers({ children }: React.PropsWithChildren) {
  return (
    <LocalizationProvider>
      <ThemeProvider>{children}</ThemeProvider>
    </LocalizationProvider>
  );
}

function flattenedStyle(testID: string) {
  const style = screen.getByTestId(testID).props.style;
  return StyleSheet.flatten(
    typeof style === 'function' ? style({ pressed: false }) : style,
  );
}

it('exposes a disabled semantic button with a 48 point target', async () => {
  const onPress = jest.fn();
  await render(
    <Providers>
      <Button
        disabled
        label="Activate safely"
        onPress={onPress}
        testID="semantic-button"
      />
    </Providers>,
  );

  const button = screen.getByTestId('semantic-button');
  expect(button.props.accessibilityRole).toBe('button');
  expect(button.props.accessibilityLabel).toBe('Activate safely');
  expect(button.props.accessibilityState).toEqual({ disabled: true });
  expect(flattenedStyle('semantic-button').minHeight).toBeGreaterThanOrEqual(
    48,
  );
  fireEvent.press(button);
  expect(onPress).not.toHaveBeenCalled();
});

it('exposes radio and checkbox state on minimum-size selectable controls', async () => {
  await render(
    <Providers>
      <ChoiceChip
        label="Selected policy"
        onPress={jest.fn()}
        selected
        testID="semantic-choice"
      />
      <PersonRow
        accessibilityLabel="Select Asha"
        birthday="12 July"
        initials="AS"
        name="Asha"
        onPress={jest.fn()}
        role="checkbox"
        selected
        status="Ready"
        testID="semantic-person"
      />
    </Providers>,
  );

  const choice = screen.getByTestId('semantic-choice');
  expect(choice.props.accessibilityRole).toBe('radio');
  expect(choice.props.accessibilityState).toEqual({ selected: true });
  expect(flattenedStyle('semantic-choice').minHeight).toBeGreaterThanOrEqual(
    48,
  );

  const person = screen.getByTestId('semantic-person');
  expect(person.props.accessibilityRole).toBe('checkbox');
  expect(person.props.accessibilityState).toEqual({ checked: true });
  expect(flattenedStyle('semantic-person').minHeight).toBeGreaterThanOrEqual(
    48,
  );
});

it('routes search through the enforced accessible text-input boundary', async () => {
  await render(
    <Providers>
      <SearchField
        hint="Searches approved contacts"
        label="Search people"
        onChangeText={jest.fn()}
        testID="semantic-search"
        value=""
      />
    </Providers>,
  );

  const input = screen.getByTestId('semantic-search');
  expect(input.props.accessibilityLabel).toBe('Search people');
  expect(input.props.accessibilityHint).toBe('Searches approved contacts');
  expect(input.props.allowFontScaling).toBe(true);
  expect(input.props.maxFontSizeMultiplier).toBe(2);
  expect(flattenedStyle('semantic-search').minHeight).toBeGreaterThanOrEqual(
    48,
  );
});

it('exposes switch state on a full-row 48 point target', async () => {
  const onValueChange = jest.fn();
  await render(
    <Providers>
      <LabeledSwitch
        detail="Pauses new birthday work"
        onValueChange={onValueChange}
        testID="semantic-switch"
        title="Pause automation"
        value
      />
    </Providers>,
  );

  const control = screen.getByTestId('semantic-switch');
  expect(control.props.accessibilityRole).toBe('switch');
  expect(control.props.accessibilityState).toEqual({ checked: true });
  expect(control.props.accessibilityHint).toBe('Pauses new birthday work');
  expect(control.props.hitSlop).toBe(8);
  expect(flattenedStyle('semantic-switch').minHeight).toBeGreaterThanOrEqual(
    48,
  );

  fireEvent.press(control);
  expect(onValueChange).toHaveBeenCalledTimes(1);
});
