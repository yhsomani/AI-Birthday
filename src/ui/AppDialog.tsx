import React, { useCallback, useEffect, useRef, useState, useSyncExternalStore } from 'react';
import {
  AccessibilityInfo,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  View,
  findNodeHandle,
  type TextProps,
  type ViewProps
} from 'react-native';
import {
  nextEnabledDialogActionIndex,
  preferredDialogActionIndex,
  type DialogAction,
  type DialogController
} from './dialogController';
import { colors, spacing } from './theme';

type FocusableHandle = {
  focus?: () => void;
};

type DialogKeyDownEvent = {
  nativeEvent: {
    key: string;
    shiftKey: boolean;
  };
  preventDefault(): void;
};

export interface AppDialogHostProps {
  controller: DialogController;
  reduceMotion?: boolean;
  testID?: string;
}

const useReducedMotion = (override?: boolean): boolean => {
  const [systemReducedMotion, setSystemReducedMotion] = useState(false);

  useEffect(() => {
    if (override !== undefined) {
      return;
    }
    let active = true;
    AccessibilityInfo.isReduceMotionEnabled().then(enabled => {
      if (active) {
        setSystemReducedMotion(enabled);
      }
    });
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setSystemReducedMotion);
    return () => {
      active = false;
      subscription.remove();
    };
  }, [override]);

  return override ?? systemReducedMotion;
};

const actionStyleFor = (action: Readonly<DialogAction>) => {
  switch (action.role) {
    case 'cancel':
      return styles.actionCancel;
    case 'destructive':
      return styles.actionDestructive;
    case 'default':
    case undefined:
      return styles.actionDefault;
  }
};

const actionTextStyleFor = (action: Readonly<DialogAction>) =>
  action.role === 'cancel' ? styles.actionCancelText : styles.actionDefaultText;

export const AppDialogHost = ({ controller, reduceMotion, testID = 'app-dialog-host' }: AppDialogHostProps) => {
  const state = useSyncExternalStore(controller.subscribe, controller.getState, controller.getState);
  const dialog = state.active;
  const shouldReduceMotion = useReducedMotion(reduceMotion);
  const titleRef = useRef<FocusableHandle | null>(null);
  const actionRefs = useRef<Array<FocusableHandle | null>>([]);
  const focusedActionIndex = useRef(-1);
  const titleId = dialog ? `${dialog.id}-title` : 'app-dialog-title';
  const descriptionId = dialog ? `${dialog.id}-description` : 'app-dialog-description';

  const focusAction = useCallback(
    (index: number) => {
      if (!dialog || index < 0) {
        return;
      }
      focusedActionIndex.current = index;
      const target = actionRefs.current[index];
      target?.focus?.();
      const nativeHandle = findNodeHandle(target as never);
      if (nativeHandle !== null) {
        AccessibilityInfo.setAccessibilityFocus(nativeHandle);
      }
    },
    [dialog]
  );

  const focusDialog = useCallback(() => {
    if (!dialog) {
      return;
    }
    titleRef.current?.focus?.();
    const titleHandle = findNodeHandle(titleRef.current as never);
    if (titleHandle !== null) {
      AccessibilityInfo.setAccessibilityFocus(titleHandle);
    }
  }, [dialog]);

  useEffect(() => {
    if (!dialog) {
      actionRefs.current = [];
      focusedActionIndex.current = -1;
      return;
    }
    const frame = requestAnimationFrame(focusDialog);
    return () => cancelAnimationFrame(frame);
  }, [dialog?.id, focusDialog]);

  const requestDismiss = useCallback(
    (reason: 'back' | 'escape' | 'accessibility-escape') => {
      controller.dismiss(reason);
    },
    [controller]
  );

  const handleKeyDown = useCallback(
    (event: DialogKeyDownEvent) => {
      if (!dialog) {
        return;
      }
      if (event.nativeEvent.key === 'Escape') {
        event.preventDefault();
        requestDismiss('escape');
        return;
      }
      if (event.nativeEvent.key !== 'Tab') {
        return;
      }
      const currentIndex = focusedActionIndex.current;
      const nextIndex =
        currentIndex < 0
          ? event.nativeEvent.shiftKey
            ? nextEnabledDialogActionIndex(dialog, 0, -1)
            : preferredDialogActionIndex(dialog)
          : nextEnabledDialogActionIndex(
              dialog,
              currentIndex,
              event.nativeEvent.shiftKey ? -1 : 1
            );
      if (nextIndex >= 0) {
        event.preventDefault();
        focusAction(nextIndex);
      }
    },
    [dialog, focusAction, requestDismiss]
  );

  // React Native Web forwards these standard DOM accessibility/keyboard props,
  // while the native View safely ignores the web-only entries in the spread.
  const webRelationshipProps = {
    'aria-labelledby': titleId,
    'aria-describedby': descriptionId,
    onKeyDown: handleKeyDown
  } as unknown as ViewProps;
  const webTitleFocusProps = { tabIndex: -1 } as unknown as TextProps;

  return (
    <Modal
      animationType={shouldReduceMotion ? 'none' : 'fade'}
      transparent
      visible={dialog !== null}
      onRequestClose={() => requestDismiss('back')}
      onShow={focusDialog}
      statusBarTranslucent
      testID={testID}
    >
      {dialog ? (
        <View style={styles.overlay}>
          <View
            {...webRelationshipProps}
            accessibilityLabelledBy={titleId}
            accessibilityViewIsModal
            aria-modal
            role="alertdialog"
            onAccessibilityEscape={() => requestDismiss('accessibility-escape')}
            style={styles.dialog}
          >
            <Text
              {...webTitleFocusProps}
              accessibilityRole="header"
              nativeID={titleId}
              ref={node => {
                titleRef.current = node as unknown as FocusableHandle | null;
              }}
              style={styles.title}
            >
              {dialog.title}
            </Text>
            <Text nativeID={descriptionId} style={styles.description}>
              {dialog.description}
            </Text>
            <View accessibilityRole="toolbar" style={styles.actions}>
              {dialog.actions.map((action, index) => (
                <Pressable
                  accessibilityHint={action.accessibilityHint}
                  accessibilityLabel={action.label}
                  accessibilityRole="button"
                  accessibilityState={{ disabled: action.disabled === true }}
                  disabled={action.disabled}
                  key={action.id}
                  onFocus={() => {
                    focusedActionIndex.current = index;
                  }}
                  onPress={() => controller.chooseAction(action.id)}
                  ref={node => {
                    actionRefs.current[index] = node as unknown as FocusableHandle | null;
                  }}
                  style={({ pressed }) => [
                    styles.action,
                    actionStyleFor(action),
                    action.disabled && styles.actionDisabled,
                    pressed && !action.disabled && styles.actionPressed
                  ]}
                >
                  <Text style={[styles.actionText, actionTextStyleFor(action)]}>{action.label}</Text>
                </Pressable>
              ))}
            </View>
          </View>
        </View>
      ) : null}
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(24, 33, 31, 0.48)',
    padding: spacing.xl
  },
  dialog: {
    width: '100%',
    maxWidth: 520,
    maxHeight: '90%',
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: 8,
    padding: spacing.lg
  },
  title: {
    color: colors.text,
    fontSize: 20,
    fontWeight: '800',
    lineHeight: 26,
    marginBottom: spacing.sm
  },
  description: {
    color: colors.text,
    fontSize: 16,
    lineHeight: 23,
    marginBottom: spacing.lg
  },
  actions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'flex-end',
    gap: spacing.sm
  },
  action: {
    minWidth: 96,
    minHeight: 48,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 8,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm
  },
  actionDefault: {
    backgroundColor: colors.primary
  },
  actionCancel: {
    backgroundColor: colors.primarySoft
  },
  actionDestructive: {
    backgroundColor: colors.danger
  },
  actionDisabled: {
    opacity: 0.45
  },
  actionPressed: {
    opacity: 0.78
  },
  actionText: {
    fontWeight: '800',
    textAlign: 'center'
  },
  actionDefaultText: {
    color: colors.surface
  },
  actionCancelText: {
    color: colors.primary
  }
});
