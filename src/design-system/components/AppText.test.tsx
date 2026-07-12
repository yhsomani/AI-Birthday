import React from 'react';
import { render, screen } from '@testing-library/react-native';

import { ThemeProvider } from '../../app/providers/ThemeProvider';
import { AppText } from './AppText';

it('allows system text scaling through 200 percent without truncation', async () => {
  await render(
    <ThemeProvider>
      <AppText>Scalable critical status</AppText>
    </ThemeProvider>,
  );

  const text = screen.getByText('Scalable critical status');
  expect(text.props.allowFontScaling).toBe(true);
  expect(text.props.maxFontSizeMultiplier).toBe(2);
  expect(text.props.numberOfLines).toBeUndefined();
});
