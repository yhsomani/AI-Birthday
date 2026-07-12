import React from 'react';
import { render, screen } from '@testing-library/react-native';

import { AccessibleTextInput } from './AccessibleTextInput';

it('forces a normalized label and the 200 percent text scaling contract', async () => {
  const untrustedProps = {
    allowFontScaling: false,
    maxFontSizeMultiplier: 1,
  } as unknown as React.ComponentProps<typeof AccessibleTextInput>;

  await render(
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
});

it('rejects an empty accessibility label', async () => {
  await expect(
    (async () => {
      await render(<AccessibleTextInput accessibilityLabel="   " />);
    })(),
  ).rejects.toThrow(
    'AccessibleTextInput requires a non-empty accessibilityLabel',
  );
});
