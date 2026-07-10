import type { AppState } from '../domain/types';
import type { OperationSnapshot } from './operationCoordinator';
import type { OperationalIssue } from './operationalIssues';

export const buildFunctionalStateSummary = (state: AppState): readonly string[] => [
  `activeScreen=${state.activeScreen}`,
  `selectedContactId=${state.selectedContactId ?? 'none'}`,
  `selectedEventId=${state.selectedEventId ?? 'none'}`,
  `selectedMessageId=${state.selectedMessageId ?? 'none'}`,
  `contacts=${state.contacts.length}`,
  `events=${state.events.length}`,
  `messages=${state.messages.length}`,
  `memories=${state.memories.length}`,
  `gifts=${state.gifts.length}`,
  `activity=${state.activity.length}`,
  `reminderPlans=${state.reminderPlans.length}`,
  `persistence=${state.persistence.status}`,
  `accountMode=${state.settings.accountMode}`,
  `reviewMode=${state.settings.automationMode}`,
  `aiProvider=${state.aiProvider.status}`,
  `emailProvider=${state.emailDelivery.status}`,
  ...Object.entries(state.privacy.permissionRecords ?? {}).map(
    ([capability, record]) => `permission.${capability}=${record?.systemAuthorization ?? 'unknown'}`
  )
];

export const buildFunctionalOperationSummary = (operations: readonly OperationSnapshot[]): readonly string[] =>
  operations.map(
    operation => `${operation.scope}:${operation.status}:attempt=${operation.attempt}:request=${operation.requestId}`
  );

export const buildFunctionalIssueSummary = (issues: readonly OperationalIssue[]): readonly string[] =>
  issues.map(issue => `${issue.code}:${issue.severity}:attempts=${issue.attempts}:correlation=${issue.correlationId}`);
