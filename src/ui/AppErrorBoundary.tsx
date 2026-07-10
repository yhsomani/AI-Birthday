import React, { Component, type ErrorInfo, type ReactNode } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import type { OperationalIssueInput } from '../application/operationalIssues';
import { colors, spacing } from './theme';

type AppErrorBoundaryProps = {
  children: ReactNode;
  onOperationalIssue?: (issue: OperationalIssueInput) => void;
  title?: string;
  message?: string;
  retryLabel?: string;
};

type AppErrorBoundaryState = {
  failed: boolean;
};

export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = { failed: false };

  static getDerivedStateFromError(): AppErrorBoundaryState {
    return { failed: true };
  }

  componentDidCatch(_error: Error, _info: ErrorInfo) {
    this.props.onOperationalIssue?.({
      code: 'unexpected-ui-error',
      severity: 'blocking',
      summary: 'A screen could not be rendered. Relationship content was not included in diagnostics.',
      recovery: 'restart-screen'
    });
  }

  private retry = () => {
    this.setState({ failed: false });
  };

  render() {
    if (!this.state.failed) return this.props.children;

    return (
      <View style={styles.root} accessibilityRole="alert">
        <Text accessibilityRole="header" style={styles.title}>
          {this.props.title ?? 'This screen needs to restart'}
        </Text>
        <Text style={styles.message}>
          {this.props.message ?? 'Your local relationship data was not changed. Restart this screen to try again.'}
        </Text>
        <TouchableOpacity
          accessibilityLabel={this.props.retryLabel ?? 'Restart screen'}
          accessibilityRole="button"
          onPress={this.retry}
          style={styles.button}
        >
          <Text style={styles.buttonText}>{this.props.retryLabel ?? 'Restart screen'}</Text>
        </TouchableOpacity>
      </View>
    );
  }
}

const styles = StyleSheet.create({
  root: {
    alignItems: 'flex-start',
    backgroundColor: colors.bg,
    flex: 1,
    justifyContent: 'center',
    padding: spacing.xl
  },
  title: {
    color: colors.text,
    fontSize: 24,
    fontWeight: '800',
    lineHeight: 30
  },
  message: {
    color: colors.muted,
    fontSize: 16,
    lineHeight: 23,
    marginTop: spacing.sm
  },
  button: {
    alignItems: 'center',
    backgroundColor: colors.primary,
    borderRadius: 12,
    justifyContent: 'center',
    marginTop: spacing.lg,
    minHeight: 48,
    paddingHorizontal: spacing.lg
  },
  buttonText: {
    color: colors.surface,
    fontSize: 16,
    fontWeight: '700'
  }
});
