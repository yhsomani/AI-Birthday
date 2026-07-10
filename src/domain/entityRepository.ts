import type { AppState } from './types';

export const entityCollectionNames = [
  'contacts',
  'events',
  'memories',
  'gifts',
  'messages',
  'activity',
  'backups',
  'setupChecks',
  'reminderPlans'
] as const;

export const entitySingletonNames = [
  'styleProfile',
  'settings',
  'onboarding',
  'privacy',
  'aiProvider',
  'emailDelivery',
  'calendarSync',
  'persistence'
] as const;

export type EntityCollectionName = (typeof entityCollectionNames)[number];
export type EntitySingletonName = (typeof entitySingletonNames)[number];
export type RepositoryEntity<Name extends EntityCollectionName> = AppState[Name][number];

export type RepositoryIndexName =
  | 'id'
  | 'contactId'
  | 'eventId'
  | 'date'
  | 'createdAt'
  | 'sentAt'
  | 'scheduledFor'
  | 'triggerAt'
  | 'status'
  | 'type'
  | 'group'
  | 'severity'
  | 'name';

export type RepositoryIndexValue = string | number | boolean;

export type RepositoryWhere = {
  index: RepositoryIndexName;
  equalTo?: RepositoryIndexValue;
  from?: RepositoryIndexValue;
  to?: RepositoryIndexValue;
};

export type EntityPageRequest = {
  where?: readonly RepositoryWhere[];
  orderBy?: RepositoryIndexName;
  direction?: 'asc' | 'desc';
  limit: number;
  cursor?: string;
  includeArchived?: boolean;
};

export type EntityPage<Entity> = {
  items: Entity[];
  nextCursor?: string;
  matchedCount: number;
};

/**
 * The caller supplies the canonical next state and the exact dirty identities.
 * Missing dirty identities are interpreted as deletes. Unlisted entities are
 * retained by reference and are not rewritten.
 */
export type DirtyStateWrite = {
  state: AppState;
  collections?: Partial<Record<EntityCollectionName, readonly string[]>>;
  singletons?: readonly EntitySingletonName[];
  shell?: boolean;
};

export type EntityArchiveTarget = {
  collection: EntityCollectionName;
  id: string;
  archivedAt?: string;
};

export type RetentionPolicy = {
  activity: {
    activeDays: number;
    maximumActive: number;
    /** Permanent deletion is opt-in and applies only to already archived activity. */
    purgeArchivedAfterDays?: number;
  };
  terminalMessages: {
    archiveAfterDays: number;
  };
};

export type RetentionReport = {
  archivedActivity: number;
  archivedMessages: number;
  purgedActivity: number;
  retainedRelationshipHistory: number;
  appliedAt: string;
};

export type RepositoryInspection = {
  status: 'Missing' | 'Ready';
  schemaVersion?: number;
  generation?: number;
  aggregateCounts: Record<EntityCollectionName, number>;
  activeCounts: Record<EntityCollectionName, number>;
  archivedCounts: Record<EntityCollectionName, number>;
  stateChecksum?: string;
  manifestChecksum?: string;
  recordFileCount: number;
  payloadBytes: number;
  largestRecordBytes: number;
  savedAt?: string;
  recoveredFromRollback: boolean;
};

export interface EntityRepository {
  loadState(): Promise<AppState | undefined>;
  replaceState(state: AppState): Promise<RepositoryInspection>;
  writeDirty(write: DirtyStateWrite): Promise<RepositoryInspection>;
  query<Name extends EntityCollectionName>(
    collection: Name,
    request: EntityPageRequest
  ): Promise<EntityPage<RepositoryEntity<Name>>>;
  setArchiveState(targets: readonly EntityArchiveTarget[]): Promise<RepositoryInspection>;
  applyRetentionPolicy(policy: RetentionPolicy, nowIso: string): Promise<RetentionReport>;
  /** Removes generations older than the current verified state after destructive clear. */
  pruneRollbackGenerations(): Promise<RepositoryInspection>;
  /** Explicit, confirmation-gated recovery only: removes every app-owned repository generation and key. */
  destroyAllData?(): Promise<void>;
  inspect(): Promise<RepositoryInspection>;
}
