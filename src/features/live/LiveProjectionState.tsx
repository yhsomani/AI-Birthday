import React, { PropsWithChildren } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

import type { NativeProblem } from '../../domain/shared/result';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  ReadinessBanner,
  StatusRow,
} from '../../design-system/components/Primitives';
import { spacing } from '../../design-system/tokens/theme';
import { useAppTheme } from '../../app/providers/ThemeProvider';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import {
  nativeProblemMessageKey,
  nativeProblemReference,
} from './nativeProblem';

export function LiveLoading({ label }: { label: string }) {
  const { colors } = useAppTheme();
  return (
    <View
      accessible
      accessibilityLabel={label}
      accessibilityLiveRegion="polite"
      accessibilityRole="progressbar"
      style={styles.centered}
      testID="live-projection-loading"
    >
      <ActivityIndicator
        accessibilityElementsHidden
        color={colors.accent}
        importantForAccessibility="no-hide-descendants"
        size="large"
      />
      <AppText variant="heading">{label}</AppText>
    </View>
  );
}

export function LiveError({
  title,
  problem,
  onRetry,
  retryTestID = 'live-retry',
  testID = 'live-projection-error',
}: {
  title: string;
  problem: NativeProblem;
  onRetry: () => void;
  retryTestID?: string;
  testID?: string;
}) {
  const { t } = useAppLocalization();
  const reference = nativeProblemReference(problem);
  return (
    <View
      accessibilityLiveRegion="assertive"
      accessibilityRole="alert"
      style={styles.stack}
      testID={testID}
    >
      <ReadinessBanner
        title={title}
        detail={t(nativeProblemMessageKey(problem))}
        tone="critical"
      />
      <Card>
        <AppText color="muted" variant="caption">
          {t('live.common.reference', { reference })}
        </AppText>
      </Card>
      <Button
        label={t('live.common.tryAgain')}
        onPress={onRetry}
        testID={retryTestID}
      />
    </View>
  );
}

export function LiveRefreshProblem({ problem }: { problem: NativeProblem }) {
  const { t } = useAppLocalization();
  return (
    <ReadinessBanner
      title={t('live.error.refreshTitle')}
      detail={t('live.error.refreshBody', {
        message: t(nativeProblemMessageKey(problem)),
      })}
      tone="warning"
    />
  );
}

export function LiveActionFeedback({
  problem,
  message,
}: {
  problem?: NativeProblem | undefined;
  message?: string | undefined;
}) {
  const { t } = useAppLocalization();
  if (problem) {
    return (
      <View accessibilityLiveRegion="assertive" accessibilityRole="alert">
        <ReadinessBanner
          title={t('live.error.actionTitle')}
          detail={t('live.error.actionBody', {
            message: t(nativeProblemMessageKey(problem)),
            reference: t('live.common.reference', {
              reference: nativeProblemReference(problem),
            }),
          })}
          tone="critical"
        />
      </View>
    );
  }
  return message ? (
    <View accessibilityLiveRegion="polite">
      <ReadinessBanner
        title={t('live.action.responseTitle')}
        detail={message}
        tone="info"
      />
    </View>
  ) : null;
}

export function LiveValidationError({
  message,
  testID,
}: {
  message: string;
  testID?: string;
}) {
  return (
    <View
      accessibilityLiveRegion="assertive"
      accessibilityRole="alert"
      testID={testID}
    >
      <StatusRow title={message} tone="warning" />
    </View>
  );
}

export function LiveRefreshing({ children }: PropsWithChildren) {
  return <View style={styles.inline}>{children}</View>;
}

const styles = StyleSheet.create({
  centered: {
    alignItems: 'center',
    flex: 1,
    gap: spacing.md,
    justifyContent: 'center',
    minHeight: 320,
  },
  inline: { opacity: 0.7 },
  stack: { gap: spacing.md },
});
