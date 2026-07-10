import type { ActivityItem, AppState, Screen } from './types';

export type ActivityTypeFilter = 'All' | ActivityItem['type'];
export type ActivitySeverityFilter = 'All' | ActivityItem['severity'];
export type ActivityDateFilter = 'All' | 'Today' | 'Last 7 days';

export type ActivityHistoryFilters = {
  query?: string;
  type?: ActivityTypeFilter;
  severity?: ActivitySeverityFilter;
  date?: ActivityDateFilter;
  nowIso?: string;
  state?: AppState;
};

export type ActivityHistoryRow = {
  item: ActivityItem;
  actionLabel: string;
  targetScreen: Screen;
  contactId?: string;
  messageId?: string;
  isOpenIssue: boolean;
  recoveryState: 'ready' | 'fallback';
  recoveryDetail: string;
};

export type ActivityHistoryResult = {
  rows: ActivityHistoryRow[];
  emptyState: 'No activity yet' | 'No matching activity' | undefined;
};

export const activityTypeFilters: ActivityTypeFilter[] = [
  'All',
  'Message',
  'Event',
  'Contact',
  'Backup',
  'Setup',
  'AI',
  'Gift',
  'Memory',
  'Analytics'
];

export const activitySeverityFilters: ActivitySeverityFilter[] = ['All', 'Info', 'Warning', 'Error'];
export const activityDateFilters: ActivityDateFilter[] = ['All', 'Today', 'Last 7 days'];

const targetForActivity = (type: ActivityItem['type']): Pick<ActivityHistoryRow, 'actionLabel' | 'targetScreen'> => {
  switch (type) {
    case 'Message':
      return { actionLabel: 'Open messages', targetScreen: 'messages' };
    case 'Event':
      return { actionLabel: 'Open events', targetScreen: 'events' };
    case 'Contact':
    case 'Gift':
    case 'Memory':
      return { actionLabel: 'Open contacts', targetScreen: 'contacts' };
    case 'Backup':
    case 'Setup':
    case 'AI':
    case 'Analytics':
      return { actionLabel: 'Open setup', targetScreen: 'more' };
  }
};

const validateExplicitTarget = (
  item: ActivityItem,
  state: AppState | undefined
): Pick<ActivityHistoryRow, 'actionLabel' | 'targetScreen' | 'contactId' | 'messageId' | 'recoveryState' | 'recoveryDetail'> | undefined => {
  if (!item.targetScreen) {
    return undefined;
  }

  const fallback = targetForActivity(item.type);
  if (!state) {
    return {
      actionLabel: item.actionLabel ?? fallback.actionLabel,
      targetScreen: item.targetScreen,
      contactId: item.contactId,
      messageId: item.messageId,
      recoveryState: 'ready',
      recoveryDetail: 'Recovery target is attached to this activity.'
    };
  }

  if (item.messageId && !state.messages.some(message => message.id === item.messageId)) {
    return {
      ...fallback,
      recoveryState: 'fallback',
      recoveryDetail: 'The linked message is no longer available, so this action opens the nearest safe recovery screen.'
    };
  }

  if (item.contactId && !state.contacts.some(contact => contact.id === item.contactId)) {
    return {
      ...fallback,
      recoveryState: 'fallback',
      recoveryDetail: 'The linked contact is no longer available, so this action opens the nearest safe recovery screen.'
    };
  }

  return {
    actionLabel: item.actionLabel ?? fallback.actionLabel,
    targetScreen: item.targetScreen,
    contactId: item.contactId,
    messageId: item.messageId,
    recoveryState: 'ready',
    recoveryDetail: 'Recovery target is available.'
  };
};

const dayKey = (iso: string) => iso.slice(0, 10);

const withinDateFilter = (item: ActivityItem, filter: ActivityDateFilter, nowIso: string) => {
  if (filter === 'All') {
    return true;
  }
  const created = new Date(item.createdAt).getTime();
  const now = new Date(nowIso).getTime();
  if (Number.isNaN(created) || Number.isNaN(now)) {
    return false;
  }
  if (filter === 'Today') {
    return dayKey(item.createdAt) === dayKey(nowIso);
  }
  return now - created <= 7 * 24 * 60 * 60 * 1000;
};

export const buildActivityHistory = (
  activity: ActivityItem[],
  filters: ActivityHistoryFilters = {}
): ActivityHistoryResult => {
  const query = filters.query?.trim().toLowerCase() ?? '';
  const type = filters.type ?? 'All';
  const severity = filters.severity ?? 'All';
  const date = filters.date ?? 'All';
  const nowIso = filters.nowIso ?? new Date().toISOString();
  const state = filters.state;

  const rows = [...activity]
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .filter(item => {
      const queryMatches =
        query.length === 0 ||
        `${item.title} ${item.detail} ${item.type} ${item.severity}`.toLowerCase().includes(query);
      const typeMatches = type === 'All' || item.type === type;
      const severityMatches = severity === 'All' || item.severity === severity;
      return queryMatches && typeMatches && severityMatches && withinDateFilter(item, date, nowIso);
    })
    .map(item => {
      const explicitTarget = validateExplicitTarget(item, state);
      const action = explicitTarget ?? {
        ...targetForActivity(item.type),
        recoveryState: 'ready' as const,
        recoveryDetail: 'General recovery action for this activity type.'
      };
      return {
        item,
        ...action,
        isOpenIssue: item.severity === 'Warning' || item.severity === 'Error'
      };
    });

  return {
    rows,
    emptyState: activity.length === 0 ? 'No activity yet' : rows.length === 0 ? 'No matching activity' : undefined
  };
};
