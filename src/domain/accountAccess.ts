import { productAvailability } from '../config/productAvailability';
import type { AppState } from './types';

export type AccountExitAction = 'disconnect-account' | 'clear-local-data';
export type AccountExitSeverity = 'Info' | 'Warning' | 'Danger';

export interface AccountExitChecklistItem {
  id: string;
  label: string;
  detail: string;
  severity: AccountExitSeverity;
  satisfied: boolean;
}

export interface AccountExitPlan {
  action: AccountExitAction;
  title: string;
  summary: string;
  primaryActionLabel: string;
  confirmationTitle: string;
  confirmationBody: string;
  available: boolean;
  requiresConfirmation: boolean;
  destructive: boolean;
  backupRecommended: boolean;
  backupReady: boolean;
  relationshipRecordCount: number;
  checklist: AccountExitChecklistItem[];
  unavailableReason?: string;
}

const relationshipRecordCount = (state: AppState) =>
  state.contacts.length + state.events.length + state.memories.length + state.gifts.length + state.messages.length;

const plural = (count: number, noun: string) => `${count} ${noun}${count === 1 ? '' : 's'}`;

const backupItem = (state: AppState, recommended: boolean): AccountExitChecklistItem => {
  const backupReady = state.backups.length > 0;
  return {
    id: 'backup',
    label: recommended ? 'Encrypted backup recommended first' : 'No relationship data needs backup',
    detail: backupReady
      ? `${plural(state.backups.length, 'encrypted backup snapshot')} is available for recovery.`
      : recommended
        ? 'Create an encrypted backup first if this local data matters.'
        : 'There is no local relationship data to preserve before this action.',
    severity: recommended && !backupReady ? 'Warning' : 'Info',
    satisfied: !recommended || backupReady
  };
};

const confirmationBodyFor = (summary: string, checklist: AccountExitChecklistItem[]) =>
  [
    summary,
    '',
    ...checklist.map(item => `${item.satisfied ? '[ready]' : '[review]'} ${item.label}: ${item.detail}`)
  ].join('\n');

export const buildAccountExitPlan = (state: AppState, action: AccountExitAction): AccountExitPlan => {
  const recordCount = relationshipRecordCount(state);
  const hasRelationshipData = recordCount > 0;
  const backupRecommended = hasRelationshipData;
  const backupReady = state.backups.length > 0;

  if (action === 'disconnect-account') {
    const available = state.settings.accountMode !== 'Local';
    const checklist: AccountExitChecklistItem[] = [
      {
        id: 'local-data-retained',
        label: 'Local relationship data is retained',
        detail: `${plural(recordCount, 'relationship record')} stays on this device after provider sync is disconnected.`,
        severity: 'Info',
        satisfied: true
      },
      {
        id: 'provider-sync-off',
        label: 'Unavailable provider mode is removed',
        detail: productAvailability.googleSync.reason,
        severity: 'Warning',
        satisfied: available
      },
      backupItem(state, backupRecommended)
    ];
    const summary = available
      ? 'Returning to Local mode keeps contacts, events, memories, gifts, messages, reminders, and backups on this device. No provider sync connection exists in this release.'
      : 'This device is already in Local mode, so there is no account sync to disconnect.';

    return {
      action,
      title: 'Return to Local mode',
      summary,
      primaryActionLabel: 'Disconnect account',
      confirmationTitle: 'Disconnect account?',
      confirmationBody: confirmationBodyFor(summary, checklist),
      available,
      requiresConfirmation: available,
      destructive: false,
      backupRecommended,
      backupReady,
      relationshipRecordCount: recordCount,
      checklist,
      unavailableReason: available ? undefined : 'No provider sync connection exists.'
    };
  }

  const checklist: AccountExitChecklistItem[] = [
    {
      id: 'relationship-data-cleared',
      label: 'Local relationship data will be removed',
      detail: `${plural(recordCount, 'relationship record')}, ${plural(state.reminderPlans.length, 'reminder plan')}, and ${plural(
        state.backups.length,
        'backup snapshot'
      )} will be cleared from this app state.`,
      severity: hasRelationshipData || state.backups.length > 0 ? 'Danger' : 'Info',
      satisfied: !hasRelationshipData && state.backups.length === 0
    },
    {
      id: 'account-mode-local',
      label: 'Account mode returns to Local',
      detail: 'Provider sync state is removed and onboarding reopens so setup can be rebuilt deliberately.',
      severity: 'Warning',
      satisfied: true
    },
    backupItem(state, backupRecommended)
  ];
  const summary = hasRelationshipData
    ? 'Clearing local data removes relationship data from this app state and should only happen after an intentional backup decision.'
    : 'Clearing local data resets the app state and returns to onboarding.';

  return {
    action,
    title: 'Clear local data',
    summary,
    primaryActionLabel: 'Clear local data',
    confirmationTitle: 'Clear local data?',
    confirmationBody: confirmationBodyFor(summary, checklist),
    available: true,
    requiresConfirmation: true,
    destructive: true,
    backupRecommended,
    backupReady,
    relationshipRecordCount: recordCount,
    checklist
  };
};
