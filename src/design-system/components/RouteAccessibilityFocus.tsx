import React, { useEffect, useRef } from 'react';
import {
  AccessibilityInfo,
  findNodeHandle,
  Platform,
  StyleSheet,
  View,
} from 'react-native';

/**
 * Moves screen-reader focus to a stable route announcement after an app-owned
 * navigation change. The node is deliberately nonvisual and contains no route
 * parameters or private content.
 */
export function RouteAccessibilityFocus({
  announcement,
  routeKey,
}: {
  announcement: string;
  routeKey: string;
}) {
  const target = useRef<View>(null);

  useEffect(() => {
    let active = true;
    const timer = setTimeout(() => {
      if (!active) return;
      const handle = findNodeHandle(target.current);
      if (handle !== null) {
        AccessibilityInfo.setAccessibilityFocus(handle);
      }
      // Android reliably announces the newly focused node. VoiceOver can drop
      // focus changes during a native-route handoff, so queue the same bounded
      // route title as a fallback there.
      if (Platform.OS === 'ios') {
        AccessibilityInfo.announceForAccessibility(announcement);
      }
    }, 0);

    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [announcement, routeKey]);

  return (
    <View
      accessibilityLabel={announcement}
      accessibilityRole="header"
      accessible
      collapsable={false}
      ref={target}
      style={styles.focusTarget}
      testID="route-accessibility-focus"
    />
  );
}

const styles = StyleSheet.create({
  focusTarget: {
    height: 1,
    opacity: 0,
    position: 'absolute',
    width: 1,
  },
});
