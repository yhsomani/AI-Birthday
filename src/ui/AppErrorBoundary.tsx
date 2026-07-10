import React, { Component, type ErrorInfo, type ReactNode } from 'react';
import { Text, TouchableOpacity, View } from 'react-native';
import type { OperationalIssueInput } from '../application/operationalIssues';

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
      <View accessibilityRole="alert">
        <Text accessibilityRole="header">{this.props.title ?? 'This screen needs to restart'}</Text>
        <Text>
          {this.props.message ?? 'Your local relationship data was not changed. Restart this screen to try again.'}
        </Text>
        <TouchableOpacity
          accessibilityLabel={this.props.retryLabel ?? 'Restart screen'}
          accessibilityRole="button"
          onPress={this.retry}
        >
          <Text>{this.props.retryLabel ?? 'Restart screen'}</Text>
        </TouchableOpacity>
      </View>
    );
  }
}
