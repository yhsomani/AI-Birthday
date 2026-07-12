import React, { PropsWithChildren, useEffect, useRef } from 'react';
import {
  AccessibilityInfo,
  ActivityIndicator,
  Platform,
  StyleSheet,
  View,
} from 'react-native';

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

type AnnouncementPriority = 'assertive' | 'polite';

const joinAnnouncement = (...parts: readonly string[]): string =>
  parts
    .map(part => part.trim())
    .filter(Boolean)
    .reduce(
      (announcement, part) =>
        announcement
          ? `${announcement}${/[.!?…]$/u.test(announcement) ? '' : '.'} ${part}`
          : part,
      '',
    );

function useIosVoiceOverAnnouncement(
  announcement: string | undefined,
  priority: AnnouncementPriority,
) {
  const lastAnnounced = useRef<string | undefined>(undefined);
  const requestGeneration = useRef(0);

  useEffect(() => {
    const generation = requestGeneration.current + 1;
    requestGeneration.current = generation;
    if (!announcement) {
      lastAnnounced.current = undefined;
      return;
    }
    if (Platform.OS !== 'ios') {
      return;
    }

    let isCurrent = true;
    AccessibilityInfo.isScreenReaderEnabled()
      .then(enabled => {
        if (
          !isCurrent ||
          requestGeneration.current !== generation ||
          !enabled ||
          lastAnnounced.current === announcement
        ) {
          return;
        }
        AccessibilityInfo.announceForAccessibilityWithOptions(announcement, {
          queue: priority === 'polite',
        });
        lastAnnounced.current = announcement;
      })
      .catch(() => undefined);

    return () => {
      isCurrent = false;
    };
  }, [announcement, priority]);
}

export function LiveLoading({ label }: { label: string }) {
  const { colors } = useAppTheme();
  useIosVoiceOverAnnouncement(label, 'polite');
  return (
    <View
      accessible
      accessibilityLabel={label}
      accessibilityLiveRegion={Platform.OS === 'android' ? 'polite' : undefined}
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
  const detail = t(nativeProblemMessageKey(problem));
  const referenceLabel = t('live.common.reference', { reference });
  useIosVoiceOverAnnouncement(
    joinAnnouncement(title, detail, referenceLabel),
    'assertive',
  );
  return (
    <View
      accessibilityLiveRegion={
        Platform.OS === 'android' ? 'assertive' : undefined
      }
      accessibilityRole={Platform.OS === 'android' ? 'alert' : undefined}
      style={styles.stack}
      testID={testID}
    >
      <ReadinessBanner title={title} detail={detail} tone="critical" />
      <Card>
        <AppText color="muted" variant="caption">
          {referenceLabel}
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
  const title = t('live.error.refreshTitle');
  const detail = t('live.error.refreshBody', {
    message: t(nativeProblemMessageKey(problem)),
  });
  useIosVoiceOverAnnouncement(joinAnnouncement(title, detail), 'polite');
  return (
    <View
      accessibilityLiveRegion={Platform.OS === 'android' ? 'polite' : undefined}
    >
      <ReadinessBanner title={title} detail={detail} tone="warning" />
    </View>
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
  const title = problem
    ? t('live.error.actionTitle')
    : t('live.action.responseTitle');
  const detail = problem
    ? t('live.error.actionBody', {
        message: t(nativeProblemMessageKey(problem)),
        reference: t('live.common.reference', {
          reference: nativeProblemReference(problem),
        }),
      })
    : message;
  useIosVoiceOverAnnouncement(
    detail ? joinAnnouncement(title, detail) : undefined,
    problem ? 'assertive' : 'polite',
  );
  if (problem) {
    return (
      <View
        accessibilityLiveRegion={
          Platform.OS === 'android' ? 'assertive' : undefined
        }
        accessibilityRole={Platform.OS === 'android' ? 'alert' : undefined}
      >
        <ReadinessBanner title={title} detail={detail!} tone="critical" />
      </View>
    );
  }
  return message ? (
    <View
      accessibilityLiveRegion={Platform.OS === 'android' ? 'polite' : undefined}
      testID="live-action-feedback-success"
    >
      <ReadinessBanner title={title} detail={message} tone="info" />
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
  useIosVoiceOverAnnouncement(message, 'assertive');
  return (
    <View
      accessibilityLiveRegion={
        Platform.OS === 'android' ? 'assertive' : undefined
      }
      accessibilityRole={Platform.OS === 'android' ? 'alert' : undefined}
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
