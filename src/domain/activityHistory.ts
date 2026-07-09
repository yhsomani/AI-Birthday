import type { ActivityItem, Screen } from './types';

export type ActivityTypeFilter = 'All' | ActivityItem['type'];
export type ActivitySeverityFilter = 'All' | ActivityItem['severity'];
export type ActivityDateFilter = 'All' | 'Today' | 'Last 7 days';

export type ActivityHistoryFilters = {
  query?: string;
  type?: ActivityTypeFilter;
  severity?: ActivitySeverityFilter;
  date?: ActivityDateFilter;
  nowIso?: string;
};

export type ActivityHistoryRow = {
  item: ActivityItem;
  actionLabel: string;
  targetScreen: Screen;
  isOpenIssue: boolean;
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
  'Memory'
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
      return { actionLabel: 'Open setup', targetScreen: 'more' };
  }
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
      const action = targetForActivity(item.type);
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
