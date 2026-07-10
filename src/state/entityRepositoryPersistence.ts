import {
  entityCollectionNames,
  entitySingletonNames,
  type DirtyStateWrite,
  type EntityCollectionName,
  type EntityRepository,
  type RepositoryInspection
} from '../domain/entityRepository';
import type { AppState, PersistenceStorageHealth } from '../domain/types';
import { PERSISTENCE_VERSION, type PersistenceLoadResult } from './persistence';
import type { PersistenceCommitAdapter } from './persistenceCoordinator';

const isRecord = (value: unknown): value is Record<string, unknown> =>
  Boolean(value) && typeof value === 'object' && !Array.isArray(value);

const stableJson = (value: unknown): string => {
  const normalize = (candidate: unknown): unknown => {
    if (Array.isArray(candidate)) return candidate.map(normalize);
    if (!isRecord(candidate)) return candidate;
    return Object.fromEntries(
      Object.keys(candidate)
        .sort()
        .filter(key => candidate[key] !== undefined)
        .map(key => [key, normalize(candidate[key])])
    );
  };
  return JSON.stringify(normalize(value));
};

const shellValue = (state: AppState) => ({
  activeScreen: state.activeScreen,
  selectedContactId: state.selectedContactId,
  selectedEventId: state.selectedEventId,
  selectedMessageId: state.selectedMessageId,
  searchQuery: state.searchQuery
});

const idsFor = (state: AppState, collection: EntityCollectionName): string[] =>
  state[collection].map(record => record.id);

const changedCollectionIds = (previous: AppState, next: AppState, collection: EntityCollectionName): string[] => {
  const previousById = new Map(previous[collection].map(record => [record.id, stableJson(record)]));
  const nextById = new Map(next[collection].map(record => [record.id, stableJson(record)]));
  return [
    ...new Set([
      ...[...nextById].filter(([id, raw]) => previousById.get(id) !== raw).map(([id]) => id),
      ...[...previousById.keys()].filter(id => !nextById.has(id))
    ])
  ];
};

export const computeDirtyStateWrite = (previous: AppState, next: AppState): DirtyStateWrite => {
  const collections: Partial<Record<EntityCollectionName, readonly string[]>> = {};
  for (const collection of entityCollectionNames) {
    const changedIds = changedCollectionIds(previous, next, collection);
    const orderChanged = stableJson(idsFor(previous, collection)) !== stableJson(idsFor(next, collection));
    if (changedIds.length > 0 || orderChanged) collections[collection] = changedIds;
  }
  const singletons = entitySingletonNames.filter(
    singleton => stableJson(previous[singleton]) !== stableJson(next[singleton])
  );
  const shell = stableJson(shellValue(previous)) !== stableJson(shellValue(next));
  return {
    state: next,
    ...(Object.keys(collections).length > 0 ? { collections } : {}),
    ...(singletons.length > 0 ? { singletons } : {}),
    ...(shell ? { shell: true } : {})
  };
};

export const hasDirtyStateWrite = (write: DirtyStateWrite): boolean =>
  Boolean(write.shell) ||
  (write.singletons?.length ?? 0) > 0 ||
  Object.values(write.collections ?? {}).some(ids => (ids?.length ?? 0) > 0) ||
  Object.keys(write.collections ?? {}).length > 0;

export const repositoryInspectionToStorageHealth = (
  inspection: RepositoryInspection,
  verifiedAt: string
): PersistenceStorageHealth =>
  inspection.status === 'Missing'
    ? {
        status: 'Missing',
        storageFormat: 'Missing',
        payloadBytes: 0,
        entryCount: 0,
        chunkCount: 0,
        largestEntryBytes: 0,
        lastVerifiedAt: verifiedAt
      }
    : {
        status: 'Ready',
        storageFormat: 'Encrypted entity repository',
        payloadBytes: inspection.payloadBytes,
        entryCount: inspection.recordFileCount,
        chunkCount: 0,
        largestEntryBytes: inspection.largestRecordBytes,
        savedAt: inspection.savedAt,
        envelopeVersion: inspection.schemaVersion,
        lastVerifiedAt: verifiedAt,
        ...(inspection.recoveredFromRollback
          ? { issue: 'Recovered the previous verified encrypted repository generation.' }
          : {})
      };

export type EntityRepositoryPersistenceOptions = {
  repository: EntityRepository | Promise<EntityRepository>;
  nowIso(): string;
};

export const createEntityRepositoryPersistenceAdapter = ({
  repository,
  nowIso
}: EntityRepositoryPersistenceOptions): PersistenceCommitAdapter => {
  const resolveRepository = () => Promise.resolve(repository);
  let repositoryStatus: RepositoryInspection['status'] | undefined;
  let latestInspection: RepositoryInspection | undefined;
  return {
    async save(state, previousState) {
      const target = await resolveRepository();
      repositoryStatus ??= (await target.inspect()).status;
      if (!previousState || repositoryStatus === 'Missing') {
        latestInspection = await target.replaceState(state);
        repositoryStatus = latestInspection.status;
        return;
      }
      const dirty = computeDirtyStateWrite(previousState, state);
      if (hasDirtyStateWrite(dirty)) {
        latestInspection = await target.writeDirty(dirty);
        repositoryStatus = latestInspection.status;
      }
    },
    async inspect() {
      latestInspection ??= await (await resolveRepository()).inspect();
      repositoryStatus = latestInspection.status;
      return repositoryInspectionToStorageHealth(latestInspection, nowIso());
    },
    nowIso
  };
};

export const loadEntityRepositoryState = async (
  repository: EntityRepository | Promise<EntityRepository>,
  nowIso: () => string
): Promise<PersistenceLoadResult> => {
  const target = await repository;
  const state = await target.loadState();
  const inspection = await target.inspect();
  if (!state) {
    if (inspection.status !== 'Missing') {
      throw new Error('Encrypted repository inspection is ready but its state could not be loaded.');
    }
    return { status: 'missing' };
  }
  return {
    status: 'loaded',
    state: {
      ...state,
      persistence: {
        status: 'Ready',
        storageHealth: repositoryInspectionToStorageHealth(inspection, nowIso())
      }
    },
    migrated: false,
    version: PERSISTENCE_VERSION
  };
};

export type EntityRepositoryStatePort = Pick<
  EntityRepository,
  'loadState' | 'replaceState' | 'inspect' | 'pruneRollbackGenerations' | 'destroyAllData'
>;
