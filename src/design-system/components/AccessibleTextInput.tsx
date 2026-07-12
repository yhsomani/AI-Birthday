import React, { forwardRef } from 'react';
import { TextInput, TextInputProps } from 'react-native';

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
  { accessibilityLabel, ...inputProps },
  reference,
) {
  const normalizedLabel = accessibilityLabel.trim();
  if (!normalizedLabel) {
    throw new Error(
      'AccessibleTextInput requires a non-empty accessibilityLabel',
    );
  }

  return (
    <TextInput
      {...inputProps}
      accessibilityLabel={normalizedLabel}
      allowFontScaling
      maxFontSizeMultiplier={2}
      ref={reference}
    />
  );
});
