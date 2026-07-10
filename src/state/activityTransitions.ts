import type { ActivityItem, AppState } from '../domain/types';
import { defaultActivityStatus } from '../domain/activityHistory';
import { commandId, type CommandMetadata } from './commandMetadata';

/** Pure activity-aggregate transition shared by the compatibility root reducer. */
export const prependActivity = (
  metadata: CommandMetadata,
  state: Pick<AppState, 'activity'>,
  type: ActivityItem['type'],
  title: string,
  detail: string,
  severity: ActivityItem['severity'] = 'Info',
  target?: Pick<ActivityItem, 'targetScreen' | 'contactId' | 'messageId' | 'actionLabel'>
): ActivityItem[] => [
  {
    id: commandId(metadata, 'activity'),
    type,
    title,
    detail,
    severity,
    status: defaultActivityStatus(severity),
    createdAt: metadata.occurredAt,
    ...target
  },
  ...state.activity
];
