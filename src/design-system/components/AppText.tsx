import React, { PropsWithChildren } from 'react';
import { StyleProp, Text, TextProps, TextStyle } from 'react-native';

import { useAppTheme } from '../../app/providers/ThemeProvider';

export type TextVariant =
  | 'display'
  | 'title'
  | 'heading'
  | 'body'
  | 'label'
  | 'caption';

type AppTextProps = PropsWithChildren<
  TextProps & {
    variant?: TextVariant;
    color?: 'default' | 'muted' | 'accent' | 'critical';
    style?: StyleProp<TextStyle>;
  }
>;

const variants: Record<TextVariant, TextStyle> = {
  display: { fontSize: 32, lineHeight: 40, fontWeight: '700' },
  title: { fontSize: 26, lineHeight: 34, fontWeight: '700' },
  heading: { fontSize: 20, lineHeight: 28, fontWeight: '700' },
  body: { fontSize: 17, lineHeight: 25, fontWeight: '400' },
  label: { fontSize: 16, lineHeight: 22, fontWeight: '600' },
  caption: { fontSize: 14, lineHeight: 20, fontWeight: '500' },
};

export function AppText({
  children,
  variant = 'body',
  color = 'default',
  style,
  ...textProps
}: AppTextProps) {
  const { colors } = useAppTheme();
  const resolvedColor =
    color === 'muted'
      ? colors.textMuted
      : color === 'accent'
      ? colors.accent
      : color === 'critical'
      ? colors.critical
      : colors.text;

  return (
    <Text
      allowFontScaling
      maxFontSizeMultiplier={2}
      {...textProps}
      style={[variants[variant], { color: resolvedColor }, style]}
    >
      {children}
    </Text>
  );
}
