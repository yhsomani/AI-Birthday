import React, { PropsWithChildren, ReactNode } from 'react';
import {
  Pressable,
  ScrollView,
  StyleProp,
  StyleSheet,
  Switch,
  View,
  ViewStyle,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useAppTheme } from '../../app/providers/ThemeProvider';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { StatusTone, minimumTargetSize, radii, spacing } from '../tokens/theme';
import { AppText } from './AppText';
import { AccessibleTextInput } from './AccessibleTextInput';
import { Icon, IconName } from './Icon';

type ScreenProps = PropsWithChildren<{
  testID?: string;
  includeTopInset?: boolean;
  includeBottomInset?: boolean;
  contentStyle?: StyleProp<ViewStyle>;
}>;

export function Screen({
  children,
  testID,
  includeTopInset = false,
  includeBottomInset = includeTopInset,
  contentStyle,
}: ScreenProps) {
  const { colors } = useAppTheme();
  return (
    <SafeAreaView
      edges={[
        ...(includeTopInset ? (['top'] as const) : []),
        'left',
        'right',
        ...(includeBottomInset ? (['bottom'] as const) : []),
      ]}
      style={[styles.screen, { backgroundColor: colors.background }]}
      testID={testID}
    >
      <ScrollView
        keyboardShouldPersistTaps="handled"
        contentInsetAdjustmentBehavior="automatic"
        contentContainerStyle={[styles.screenContent, contentStyle]}
      >
        {children}
      </ScrollView>
    </SafeAreaView>
  );
}

export function Card({
  children,
  style,
  testID,
}: PropsWithChildren<{
  style?: StyleProp<ViewStyle>;
  testID?: string;
}>) {
  const { colors, isHighContrast } = useAppTheme();
  return (
    <View
      accessible={false}
      testID={testID}
      style={[
        styles.card,
        {
          backgroundColor: colors.surface,
          borderColor: colors.border,
        },
        isHighContrast ? styles.highContrastBorder : undefined,
        style,
      ]}
    >
      {children}
    </View>
  );
}

type ButtonProps = {
  label: string;
  onPress: () => void;
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  disabled?: boolean;
  accessibilityHint?: string;
  testID?: string;
  icon?: IconName;
};

export function Button({
  label,
  onPress,
  variant = 'primary',
  disabled = false,
  accessibilityHint,
  testID,
  icon,
}: ButtonProps) {
  const { colors, isHighContrast, isReduceMotionEnabled } = useAppTheme();
  const backgroundColor =
    variant === 'primary'
      ? colors.accent
      : variant === 'danger'
      ? colors.critical
      : variant === 'secondary'
      ? colors.surface
      : 'transparent';
  const foregroundColor =
    variant === 'primary' || variant === 'danger'
      ? colors.onAccent
      : variant === 'ghost'
      ? colors.accent
      : colors.text;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint={accessibilityHint}
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      testID={testID}
      style={({ pressed }) => [
        styles.button,
        {
          backgroundColor,
          borderColor:
            variant === 'secondary' || isHighContrast
              ? colors.border
              : backgroundColor,
          borderWidth: variant === 'secondary' || isHighContrast ? 2 : 0,
          opacity: disabled ? 0.48 : 1,
          transform: [{ scale: pressed && !isReduceMotionEnabled ? 0.99 : 1 }],
        },
      ]}
    >
      {icon ? <Icon name={icon} color={foregroundColor} size={20} /> : null}
      <AppText
        variant="label"
        style={[styles.buttonText, { color: foregroundColor }]}
      >
        {label}
      </AppText>
    </Pressable>
  );
}

const toneValues = (
  tone: StatusTone,
  colors: ReturnType<typeof useAppTheme>['colors'],
) => {
  switch (tone) {
    case 'positive':
      return {
        icon: 'check' as const,
        color: colors.positive,
        surface: colors.positiveSurface,
      };
    case 'warning':
      return {
        icon: 'warning' as const,
        color: colors.warning,
        surface: colors.warningSurface,
      };
    case 'critical':
      return {
        icon: 'warning' as const,
        color: colors.critical,
        surface: colors.criticalSurface,
      };
    case 'info':
      return {
        icon: 'info' as const,
        color: colors.info,
        surface: colors.infoSurface,
      };
    default:
      return {
        icon: 'clock' as const,
        color: colors.textMuted,
        surface: colors.surfaceMuted,
      };
  }
};

export function StatusRow({
  title,
  detail,
  tone = 'neutral',
  testID,
}: {
  title: string;
  detail?: string;
  tone?: StatusTone;
  testID?: string;
}) {
  const { colors } = useAppTheme();
  const status = toneValues(tone, colors);
  return (
    <View
      accessible
      accessibilityLabel={[title, detail].filter(Boolean).join('. ')}
      accessibilityRole="text"
      style={styles.statusRow}
      testID={testID}
    >
      <View style={[styles.statusIcon, { backgroundColor: status.surface }]}>
        <Icon name={status.icon} color={status.color} size={20} />
      </View>
      <View style={styles.flexText}>
        <AppText variant="label">{title}</AppText>
        {detail ? (
          <AppText color="muted" style={styles.detailText}>
            {detail}
          </AppText>
        ) : null}
      </View>
    </View>
  );
}

export function ReadinessBanner({
  title,
  detail,
  tone,
  actionLabel,
  actionDisabled = false,
  onAction,
  testID,
}: {
  title: string;
  detail: string;
  tone: StatusTone;
  actionLabel?: string;
  actionDisabled?: boolean;
  onAction?: () => void;
  testID?: string;
}) {
  const { colors } = useAppTheme();
  const status = toneValues(tone, colors);
  const hasAction = Boolean(actionLabel && onAction);
  return (
    <View
      accessible={!hasAction}
      accessibilityRole={hasAction ? undefined : 'summary'}
      accessibilityLabel={hasAction ? undefined : `${title}. ${detail}`}
      testID={testID}
      style={[
        styles.banner,
        { backgroundColor: status.surface, borderColor: status.color },
      ]}
    >
      <View style={styles.bannerHeading}>
        <Icon name={status.icon} color={status.color} size={26} />
        <AppText variant="heading" style={styles.flexText}>
          {title}
        </AppText>
      </View>
      <AppText style={styles.bannerDetail}>{detail}</AppText>
      {actionLabel && onAction ? (
        <Button
          label={actionLabel}
          disabled={actionDisabled}
          onPress={onAction}
          variant="secondary"
        />
      ) : null}
    </View>
  );
}

export function FixtureNotice() {
  const { t } = useAppLocalization();
  const { colors } = useAppTheme();
  return (
    <View
      accessibilityRole="text"
      style={[styles.fixtureNotice, { backgroundColor: colors.infoSurface }]}
    >
      <Icon name="info" color={colors.info} size={18} />
      <AppText variant="caption" style={styles.flexText}>
        {t('common.fixtureNotice')}
      </AppText>
    </View>
  );
}

export function SectionHeading({
  title,
  supporting,
}: {
  title: string;
  supporting?: string;
}) {
  return (
    <View style={styles.sectionHeading}>
      <AppText variant="heading" accessibilityRole="header">
        {title}
      </AppText>
      {supporting ? <AppText color="muted">{supporting}</AppText> : null}
    </View>
  );
}

export function ChoiceChip({
  label,
  selected,
  onPress,
  testID,
}: {
  label: string;
  selected: boolean;
  onPress: () => void;
  testID?: string;
}) {
  const { colors, isHighContrast } = useAppTheme();
  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityState={{ selected }}
      onPress={onPress}
      testID={testID}
      style={({ pressed }) => [
        styles.chip,
        {
          backgroundColor: selected ? colors.accent : colors.surface,
          borderColor: selected ? colors.accent : colors.border,
          borderWidth: isHighContrast ? 2 : 1,
          opacity: pressed ? 0.78 : 1,
        },
      ]}
    >
      <AppText
        variant="caption"
        style={{ color: selected ? colors.onAccent : colors.text }}
      >
        {label}
      </AppText>
    </Pressable>
  );
}

export function SearchField({
  value,
  onChangeText,
  label,
  hint,
  testID,
}: {
  value: string;
  onChangeText: (value: string) => void;
  label: string;
  hint: string;
  testID?: string;
}) {
  const { colors } = useAppTheme();
  return (
    <View
      style={[
        styles.search,
        { borderColor: colors.border, backgroundColor: colors.surface },
      ]}
    >
      <Icon name="search" color={colors.textMuted} size={20} />
      <AccessibleTextInput
        accessibilityLabel={label}
        accessibilityHint={hint}
        autoCorrect={false}
        placeholder={label}
        placeholderTextColor={colors.textMuted}
        value={value}
        onChangeText={onChangeText}
        style={[styles.searchInput, { color: colors.text }]}
        testID={testID}
      />
    </View>
  );
}

export function SettingRow({
  title,
  detail,
  onPress,
  testID,
  icon = 'chevron',
}: {
  title: string;
  detail: string;
  onPress: () => void;
  testID?: string;
  icon?: IconName;
}) {
  const { colors } = useAppTheme();
  const { isRtlFixture } = useAppLocalization();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${title}. ${detail}`}
      onPress={onPress}
      testID={testID}
      style={({ pressed }) => [
        styles.settingRow,
        { borderBottomColor: colors.border, opacity: pressed ? 0.72 : 1 },
      ]}
    >
      <View style={styles.flexText}>
        <AppText variant="label">{title}</AppText>
        <AppText color="muted" style={styles.detailText}>
          {detail}
        </AppText>
      </View>
      <Icon
        name={icon}
        color={colors.textMuted}
        size={22}
        mirrored={icon === 'chevron' && isRtlFixture}
      />
    </Pressable>
  );
}

export function LabeledSwitch({
  title,
  detail,
  value,
  onValueChange,
  testID,
}: {
  title: string;
  detail: string;
  value: boolean;
  onValueChange: () => void;
  testID?: string;
}) {
  const { colors } = useAppTheme();
  return (
    <Pressable
      accessibilityHint={detail}
      accessibilityLabel={title}
      accessibilityRole="switch"
      accessibilityState={{ checked: value }}
      hitSlop={spacing.sm}
      onPress={onValueChange}
      style={({ pressed }) => [
        styles.switchRow,
        { opacity: pressed ? 0.78 : 1 },
      ]}
      testID={testID}
    >
      <View style={styles.flexText}>
        <AppText variant="label">{title}</AppText>
        <AppText color="muted" style={styles.detailText}>
          {detail}
        </AppText>
      </View>
      <Switch
        accessible={false}
        importantForAccessibility="no-hide-descendants"
        pointerEvents="none"
        value={value}
        trackColor={{ false: colors.border, true: colors.accent }}
        thumbColor={value ? colors.onAccent : colors.surfaceRaised}
      />
    </Pressable>
  );
}

export function PersonRow({
  initials,
  name,
  birthday,
  phone,
  status,
  selected,
  onPress,
  accessibilityLabel,
  statusTone = 'info',
  role = 'button',
  testID,
}: {
  initials: string;
  name: string;
  birthday: string;
  phone?: string;
  status: string;
  selected?: boolean;
  onPress: () => void;
  accessibilityLabel: string;
  statusTone?: StatusTone;
  role?: 'button' | 'checkbox';
  testID?: string;
}) {
  const { colors } = useAppTheme();
  const { isRtlFixture } = useAppLocalization();
  return (
    <Pressable
      accessibilityRole={role}
      accessibilityLabel={accessibilityLabel}
      accessibilityState={
        role === 'checkbox' ? { checked: selected } : undefined
      }
      onPress={onPress}
      testID={testID}
      style={({ pressed }) => [
        styles.personRow,
        {
          backgroundColor: selected ? colors.infoSurface : colors.surface,
          borderColor: selected ? colors.info : colors.border,
          opacity: pressed ? 0.75 : 1,
        },
      ]}
    >
      <View style={[styles.avatar, { backgroundColor: colors.surfaceMuted }]}>
        <AppText variant="label">{initials}</AppText>
      </View>
      <View style={styles.flexText}>
        <AppText variant="label">{name}</AppText>
        <AppText color="muted">{birthday}</AppText>
        {phone ? (
          <AppText color="muted" variant="caption">
            {phone}
          </AppText>
        ) : null}
        <AppText
          color={
            statusTone === 'critical' || statusTone === 'warning'
              ? 'critical'
              : 'accent'
          }
          variant="caption"
        >
          {status}
        </AppText>
      </View>
      <Icon
        name={selected ? 'check' : 'chevron'}
        color={selected ? colors.positive : colors.textMuted}
        size={22}
        mirrored={!selected && isRtlFixture}
      />
    </Pressable>
  );
}

export function KeyValue({
  label,
  value,
}: {
  label: string;
  value: ReactNode;
}) {
  return (
    <View style={styles.keyValue}>
      <AppText variant="caption" color="muted">
        {label}
      </AppText>
      {typeof value === 'string' ? <AppText>{value}</AppText> : value}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  screenContent: {
    flexGrow: 1,
    paddingHorizontal: spacing.md,
    paddingTop: spacing.md,
    paddingBottom: spacing.xxl,
    gap: spacing.md,
  },
  card: {
    borderRadius: radii.md,
    borderWidth: StyleSheet.hairlineWidth,
    padding: spacing.md,
    gap: spacing.sm,
  },
  highContrastBorder: { borderWidth: 2 },
  button: {
    minHeight: minimumTargetSize,
    borderRadius: radii.pill,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
  },
  buttonText: { textAlign: 'center', flexShrink: 1 },
  statusRow: {
    minHeight: minimumTargetSize,
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.md,
    paddingVertical: spacing.sm,
  },
  statusIcon: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  flexText: { flex: 1, minWidth: 0 },
  detailText: { marginTop: spacing.xs },
  banner: {
    borderWidth: 1,
    borderRadius: radii.lg,
    padding: spacing.md,
    gap: spacing.md,
  },
  bannerHeading: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.sm,
  },
  bannerDetail: { marginStart: 34 },
  fixtureNotice: {
    borderRadius: radii.sm,
    padding: spacing.sm,
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.sm,
  },
  sectionHeading: { gap: spacing.xs, marginTop: spacing.sm },
  chip: {
    minHeight: minimumTargetSize,
    borderRadius: radii.pill,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    alignItems: 'center',
    justifyContent: 'center',
  },
  search: {
    minHeight: minimumTargetSize,
    borderWidth: 1,
    borderRadius: radii.pill,
    paddingHorizontal: spacing.md,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
  },
  searchInput: {
    flex: 1,
    fontSize: 17,
    minHeight: minimumTargetSize,
    paddingVertical: spacing.sm,
  },
  settingRow: {
    minHeight: minimumTargetSize,
    paddingVertical: spacing.md,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  switchRow: {
    minHeight: minimumTargetSize,
    paddingVertical: spacing.sm,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
  },
  personRow: {
    minHeight: minimumTargetSize,
    borderWidth: 1,
    borderRadius: radii.md,
    padding: spacing.md,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
  },
  avatar: {
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  keyValue: { gap: spacing.xs, paddingVertical: spacing.xs },
});
