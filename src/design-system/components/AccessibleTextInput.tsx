import React, { forwardRef, useState } from 'react';
import { StyleSheet, TextInput, TextInputProps } from 'react-native';

import { useAppTheme } from '../../app/providers/ThemeProvider';

export type AccessibleTextInputProps = Omit<
  TextInputProps,
  'accessibilityLabel' | 'allowFontScaling' | 'maxFontSizeMultiplier'
> & {
  accessibilityLabel: string;
};

export const AccessibleTextInput = forwardRef<
  TextInput,
  AccessibleTextInputProps
>(function AccessibleTextInputComponent(
  { accessibilityLabel, onBlur, onFocus, style, ...inputProps },
  reference,
) {
  const { colors } = useAppTheme();
  const [focused, setFocused] = useState(false);
  const focusColorStyle = { outlineColor: colors.focus };
  const normalizedLabel = accessibilityLabel.trim();
  if (!normalizedLabel) {
    throw new Error(
      'AccessibleTextInput requires a non-empty accessibilityLabel',
    );
  }

  const isEditable = inputProps.editable !== false;

  return (
    <TextInput
      {...inputProps}
      accessibilityLabel={normalizedLabel}
      accessibilityState={{
        disabled: !isEditable,
        ...inputProps.accessibilityState,
      }}
      allowFontScaling
      maxFontSizeMultiplier={2}
      onBlur={event => {
        setFocused(false);
        onBlur?.(event);
      }}
      onFocus={event => {
        setFocused(true);
        onFocus?.(event);
      }}
      ref={reference}
      style={[
        style,
        styles.focusOutline,
        focusColorStyle,
        focused ? styles.focused : styles.unfocused,
      ]}
    />
  );
});

const styles = StyleSheet.create({
  focusOutline: { outlineOffset: 2, outlineStyle: 'solid' },
  focused: { outlineWidth: 3 },
  unfocused: { outlineWidth: 0 },
});
