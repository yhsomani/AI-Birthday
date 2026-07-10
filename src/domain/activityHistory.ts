import type { ActivityItem, ActivityStatus, AppState, Screen } from './types';

export type ActivityTypeFilter = 'All' | ActivityItem['type'];
export type ActivitySeverityFilter = 'All' | ActivityItem['severity'];
export type ActivityStatusFilter = 'All' | ActivityStatus;
export type ActivityDateFilter = 'All' | 'Today' | 'Last 7 days';

export type ActivityHistoryFilters = {
  query?: string;
  type?: ActivityTypeFilter;
  severity?: ActivitySeverityFilter;
  status?: ActivityStatusFilter;
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
  status: ActivityStatus;
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
export const activityStatusFilters: ActivityStatusFilter[] = ['All', 'Open', 'Resolved', 'Obsolete', 'Completed'];
export const activityDateFilters: ActivityDateFilter[] = ['All', 'Today', 'Last 7 days'];

export const defaultActivityStatus = (severity: ActivityItem['severity']): ActivityStatus =>
  severity === 'Info' ? 'Completed' : 'Open';

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
      return { actionLabel: 'Open backup', targetScreen: 'backup' };
    case 'Setup':
    case 'AI':
      return { actionLabel: 'Open setup', targetScreen: 'setupCheck' };
    case 'Analytics':
      return { actionLabel: 'Open analytics', targetScreen: 'analytics' };
  }
};

const validateActivityTarget = (
  item: ActivityItem,
  state: AppState | undefined
):
  | Pick<
      ActivityHistoryRow,
      'actionLabel' | 'targetScreen' | 'contactId' | 'messageId' | 'recoveryState' | 'recoveryDetail'
    >
  | undefined => {
  const fallback = targetForActivity(item.type);
  const requiresMessage = item.targetScreen === 'wishPreview';
  const requiresContact =
    item.targetScreen === 'contactDetail' ||
    item.targetScreen === 'chatHistory' ||
    item.targetScreen === 'manualComposer';

  if ((requiresMessage && !item.messageId) || (requiresContact && !item.contactId)) {
    return {
      ...fallback,
      recoveryState: 'fallback',
      recoveryDetail:
        'The specific recovery target is no longer available, so this action opens the nearest safe screen.'
    };
  }

  if (!item.targetScreen) {
    return undefined;
  }

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

  const activeContactIds = new Set(state.contacts.filter(contact => !contact.archivedAt).map(contact => contact.id));
  if (
    item.messageId &&
    !state.messages.some(message => message.id === item.messageId && activeContactIds.has(message.contactId))
  ) {
    return {
      ...fallback,
      recoveryState: 'fallback',
      recoveryDetail:
        'The linked message is no longer available, so this action opens the nearest safe recovery screen.'
    };
  }

  if (item.contactId && !activeContactIds.has(item.contactId)) {
    return {
      ...fallback,
      recoveryState: 'fallback',
      recoveryDetail:
        'The linked contact is no longer available, so this action opens the nearest safe recovery screen.'
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

export const resolveActivityStatus = (
  item: ActivityItem,
  recoveryState: ActivityHistoryRow['recoveryState'] = 'ready'
): ActivityStatus =>
  recoveryState === 'fallback' ? 'Obsolete' : (item.status ?? defaultActivityStatus(item.severity));

const localDayKey = (iso: string) => {
  const value = new Date(iso);
  if (Number.isNaN(value.getTime())) return undefined;
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
};

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
    return localDayKey(item.createdAt) === localDayKey(nowIso);
  }
  return created <= now && now - created <= 7 * 24 * 60 * 60 * 1000;
};

export const buildActivityHistory = (
  activity: ActivityItem[],
  filters: ActivityHistoryFilters = {}
): ActivityHistoryResult => {
  const query = filters.query?.trim().toLowerCase() ?? '';
  const type = filters.type ?? 'All';
  const severity = filters.severity ?? 'All';
  const status = filters.status ?? 'All';
  const date = filters.date ?? 'All';
  const nowIso = filters.nowIso ?? new Date().toISOString();
  const state = filters.state;

  const rows = [...activity]
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .map(item => {
      const explicitTarget = validateActivityTarget(item, state);
      const action = explicitTarget ?? {
        ...targetForActivity(item.type),
        recoveryState: 'ready' as const,
        recoveryDetail: 'General recovery action for this activity type.'
      };
      const resolvedStatus = resolveActivityStatus(item, action.recoveryState);
      return {
        item,
        ...action,
        status: resolvedStatus,
        isOpenIssue: resolvedStatus === 'Open'
      };
    })
    .filter(row => {
      const { item } = row;
      const queryMatches =
        query.length === 0 ||
        `${item.title} ${item.detail} ${item.type} ${item.severity} ${row.status}`.toLowerCase().includes(query);
      const typeMatches = type === 'All' || item.type === type;
      const severityMatches = severity === 'All' || item.severity === severity;
      const statusMatches = status === 'All' || row.status === status;
      return queryMatches && typeMatches && severityMatches && statusMatches && withinDateFilter(item, date, nowIso);
    });

  return {
    rows,
    emptyState: activity.length === 0 ? 'No activity yet' : rows.length === 0 ? 'No matching activity' : undefined
  };
};
