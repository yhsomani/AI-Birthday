import {
  entityCollectionNames,
  type DirtyStateWrite,
  type EntityArchiveTarget,
  type EntityCollectionName,
  type EntityPage,
  type EntityPageRequest,
  type EntityRepository,
  type RepositoryEntity,
  type RepositoryInspection,
  type RetentionPolicy,
  type RetentionReport
} from '../domain/entityRepository';
import type { AppState } from '../domain/types';
import {
  clearState,
  loadStateForRepositoryMigration,
  type KeyValueStore,
  type PersistenceLoadResult
} from '../state/persistence';
import { computeEntityStoreStateChecksum, type ProtectedRepositoryKeyStore } from './encryptedEntityStoreCore';

export const NORMALIZED_STORE_MIGRATION_CHECKPOINT_KEY = 'relateai.entity-store.migration.v1';

export type RepositoryMigrationPhase = 'copying' | 'copied' | 'verified' | 'committed';

export type RepositoryMigrationCheckpoint = {
  format: 'relateai.normalized-to-entity-store';
  version: 1;
  phase: RepositoryMigrationPhase;
  sourcePersistenceVersion: number;
  sourceCounts: Record<EntityCollectionName, number>;
  sourceChecksum: string;
  repositoryGeneration?: number;
  repositoryChecksum?: string;
  updatedAt: string;
};

export type RepositoryMigrationResult =
  | { status: 'no-source' }
  | { status: 'already-committed'; checkpoint: RepositoryMigrationCheckpoint }
  | { status: 'committed'; checkpoint: RepositoryMigrationCheckpoint };

export interface RepositoryMigrationFaultHooks {
  afterCopy?(): void | Promise<void>;
  afterVerify?(): void | Promise<void>;
  afterCommit?(): void | Promise<void>;
}

export interface NormalizedStoreMigrationOptions {
  legacyStore: KeyValueStore;
  repository: EntityRepository;
  protectedStore: ProtectedRepositoryKeyStore;
  now?: () => string;
  faults?: RepositoryMigrationFaultHooks;
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  Boolean(value) && typeof value === 'object' && !Array.isArray(value);

const countsFor = (state: AppState): Record<EntityCollectionName, number> =>
  Object.fromEntries(entityCollectionNames.map(name => [name, state[name].length])) as Record<
    EntityCollectionName,
    number
  >;

const countsMatch = (
  left: Record<EntityCollectionName, number>,
  right: Record<EntityCollectionName, number>
): boolean => entityCollectionNames.every(name => left[name] === right[name]);

const parseCounts = (value: unknown): Record<EntityCollectionName, number> | undefined => {
  if (!isRecord(value)) return undefined;
  const result = {} as Record<EntityCollectionName, number>;
  for (const name of entityCollectionNames) {
    const count = value[name];
    if (typeof count !== 'number' || !Number.isInteger(count) || count < 0 || count > 10_000) return undefined;
    result[name] = count;
  }
  return result;
};

const parseCheckpoint = (raw: string | null): RepositoryMigrationCheckpoint | undefined => {
  if (raw === null || raw.length > 16_384) return undefined;
  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch {
    return undefined;
  }
  if (
    !isRecord(value) ||
    value.format !== 'relateai.normalized-to-entity-store' ||
    value.version !== 1 ||
    (value.phase !== 'copying' &&
      value.phase !== 'copied' &&
      value.phase !== 'verified' &&
      value.phase !== 'committed') ||
    typeof value.sourcePersistenceVersion !== 'number' ||
    !Number.isInteger(value.sourcePersistenceVersion) ||
    !parseCounts(value.sourceCounts) ||
    typeof value.sourceChecksum !== 'string' ||
    !/^[a-f0-9]{64}$/.test(value.sourceChecksum) ||
    (value.repositoryGeneration !== undefined &&
      (typeof value.repositoryGeneration !== 'number' || !Number.isInteger(value.repositoryGeneration))) ||
    (value.repositoryChecksum !== undefined &&
      (typeof value.repositoryChecksum !== 'string' || !/^[a-f0-9]{64}$/.test(value.repositoryChecksum))) ||
    typeof value.updatedAt !== 'string'
  ) {
    return undefined;
  }
  return value as unknown as RepositoryMigrationCheckpoint;
};

export class NormalizedStoreMigrationCoordinator {
  private readonly legacyStore: KeyValueStore;
  private readonly repository: EntityRepository;
  private readonly protectedStore: ProtectedRepositoryKeyStore;
  private readonly now: () => string;
  private readonly faults: RepositoryMigrationFaultHooks;
  private migrationPromise?: Promise<RepositoryMigrationResult>;

  constructor(options: NormalizedStoreMigrationOptions) {
    this.legacyStore = options.legacyStore;
    this.repository = options.repository;
    this.protectedStore = options.protectedStore;
    this.now = options.now ?? (() => new Date().toISOString());
    this.faults = options.faults ?? {};
  }

  private async assertProtectedCheckpointStorage(): Promise<void> {
    const status = await this.protectedStore.getProtectionStatus();
    if (!status.available || status.protection !== 'platform-protected' || status.legacyPlaintext === 'unknown') {
      throw new Error('Repository migration checkpoints require verified platform-protected storage.');
    }
  }

  async checkpoint(): Promise<RepositoryMigrationCheckpoint | undefined> {
    await this.assertProtectedCheckpointStorage();
    return parseCheckpoint(await this.protectedStore.getItem(NORMALIZED_STORE_MIGRATION_CHECKPOINT_KEY));
  }

  private async writeCheckpoint(checkpoint: RepositoryMigrationCheckpoint): Promise<void> {
    await this.assertProtectedCheckpointStorage();
    const raw = JSON.stringify(checkpoint);
    await this.protectedStore.setItem(NORMALIZED_STORE_MIGRATION_CHECKPOINT_KEY, raw);
    if ((await this.protectedStore.getItem(NORMALIZED_STORE_MIGRATION_CHECKPOINT_KEY)) !== raw) {
      throw new Error('Protected repository migration checkpoint could not be verified.');
    }
  }

  private async source(): Promise<
    | {
        state: AppState;
        version: number;
        counts: Record<EntityCollectionName, number>;
        checksum: string;
      }
    | undefined
  > {
    const result: PersistenceLoadResult = await loadStateForRepositoryMigration(this.legacyStore);
    if (result.status === 'missing') return undefined;
    if (result.status !== 'loaded') {
      throw new Error('The normalized protected source could not be safely migrated. It was left unchanged.');
    }
    if (result.recovery) {
      throw new Error('The normalized protected source requires recovery review and was left unchanged.');
    }
    return {
      state: result.state,
      version: result.version,
      counts: countsFor(result.state),
      checksum: await computeEntityStoreStateChecksum(result.state)
    };
  }

  migrate(): Promise<RepositoryMigrationResult> {
    this.migrationPromise ??= this.runMigration().finally(() => {
      this.migrationPromise = undefined;
    });
    return this.migrationPromise;
  }

  private async runMigration(): Promise<RepositoryMigrationResult> {
    const existing = await this.checkpoint();
    if (existing?.phase === 'committed') {
      const health = await this.repository.inspect();
      // The checkpoint attests that the source copy was verified before the
      // repository became authoritative. Its state is expected to evolve via
      // later repository-only commits, so startup verifies repository health,
      // not equality with the historical source checksum.
      if (health.status === 'Ready') {
        return { status: 'already-committed', checkpoint: existing };
      }
      throw new Error('Committed repository migration verification failed; the normalized source remains available.');
    }

    const source = await this.source();
    if (!source) return { status: 'no-source' };
    const base = {
      format: 'relateai.normalized-to-entity-store' as const,
      version: 1 as const,
      sourcePersistenceVersion: source.version,
      sourceCounts: source.counts,
      sourceChecksum: source.checksum
    };
    await this.writeCheckpoint({ ...base, phase: 'copying', updatedAt: this.now() });

    let health = await this.repository.inspect();
    if (
      health.status !== 'Ready' ||
      health.stateChecksum !== source.checksum ||
      !countsMatch(health.aggregateCounts, source.counts)
    ) {
      health = await this.repository.replaceState(source.state);
    }
    const copied: RepositoryMigrationCheckpoint = {
      ...base,
      phase: 'copied',
      repositoryGeneration: health.generation,
      repositoryChecksum: health.stateChecksum,
      updatedAt: this.now()
    };
    await this.writeCheckpoint(copied);
    await this.faults.afterCopy?.();

    const copiedState = await this.repository.loadState();
    const verifiedHealth = await this.repository.inspect();
    if (
      !copiedState ||
      (await computeEntityStoreStateChecksum(copiedState)) !== source.checksum ||
      verifiedHealth.stateChecksum !== source.checksum ||
      !countsMatch(verifiedHealth.aggregateCounts, source.counts)
    ) {
      throw new Error('Encrypted repository copy did not match source counts and checksum.');
    }
    const verified: RepositoryMigrationCheckpoint = {
      ...copied,
      phase: 'verified',
      repositoryGeneration: verifiedHealth.generation,
      repositoryChecksum: verifiedHealth.stateChecksum,
      updatedAt: this.now()
    };
    await this.writeCheckpoint(verified);
    await this.faults.afterVerify?.();

    const committed: RepositoryMigrationCheckpoint = {
      ...verified,
      phase: 'committed',
      updatedAt: this.now()
    };
    await this.writeCheckpoint(committed);
    await this.faults.afterCommit?.();
    return { status: 'committed', checkpoint: committed };
  }

  /** Dual-read: source remains authoritative until the verified commit checkpoint exists. */
  async loadState(): Promise<AppState | undefined> {
    const checkpoint = await this.checkpoint();
    if (checkpoint?.phase === 'committed') {
      return this.repository.loadState();
    }
    return (await this.source())?.state;
  }

  async inspect(): Promise<RepositoryInspection> {
    const checkpoint = await this.checkpoint();
    if (checkpoint?.phase === 'committed') return this.repository.inspect();
    const source = await this.source();
    if (!source) return this.repository.inspect();
    const zeroCounts = Object.fromEntries(entityCollectionNames.map(name => [name, 0])) as Record<
      EntityCollectionName,
      number
    >;
    return {
      status: 'Ready',
      schemaVersion: source.version,
      generation: 0,
      aggregateCounts: source.counts,
      activeCounts: { ...source.counts },
      archivedCounts: zeroCounts,
      stateChecksum: source.checksum,
      recordFileCount: entityCollectionNames.reduce((sum, name) => sum + source.counts[name], 0),
      payloadBytes: 0,
      largestRecordBytes: 0,
      recoveredFromRollback: false
    };
  }
}

/**
 * Compatibility facade: reads remain dual-source during migration, while every
 * mutation first reaches a committed encrypted repository and writes only there.
 */
export class DualReadSingleWriteEntityRepository implements EntityRepository {
  private readonly migration: NormalizedStoreMigrationCoordinator;
  private readonly repository: EntityRepository;

  constructor(
    migration: NormalizedStoreMigrationCoordinator,
    repository: EntityRepository,
    private readonly legacyStore?: KeyValueStore,
    private readonly protectedStore?: ProtectedRepositoryKeyStore
  ) {
    this.migration = migration;
    this.repository = repository;
  }

  private async committed(): Promise<void> {
    const result = await this.migration.migrate();
    if (result.status === 'no-source' && (await this.repository.inspect()).status === 'Missing') {
      throw new Error('No repository source exists for a single-write operation.');
    }
  }

  async loadState(): Promise<AppState | undefined> {
    await this.migration.migrate();
    return this.repository.loadState();
  }

  async replaceState(state: AppState): Promise<RepositoryInspection> {
    const result = await this.migration.migrate();
    if (result.status === 'no-source') return this.repository.replaceState(state);
    return this.repository.replaceState(state);
  }

  async writeDirty(write: DirtyStateWrite): Promise<RepositoryInspection> {
    await this.committed();
    return this.repository.writeDirty(write);
  }

  async query<Name extends EntityCollectionName>(
    collection: Name,
    request: EntityPageRequest
  ): Promise<EntityPage<RepositoryEntity<Name>>> {
    await this.committed();
    return this.repository.query(collection, request);
  }

  async setArchiveState(targets: readonly EntityArchiveTarget[]): Promise<RepositoryInspection> {
    await this.committed();
    return this.repository.setArchiveState(targets);
  }

  async applyRetentionPolicy(policy: RetentionPolicy, nowIso: string): Promise<RetentionReport> {
    await this.committed();
    return this.repository.applyRetentionPolicy(policy, nowIso);
  }

  async pruneRollbackGenerations(): Promise<RepositoryInspection> {
    await this.committed();
    return this.repository.pruneRollbackGenerations();
  }

  async destroyAllData(): Promise<void> {
    if (!this.repository.destroyAllData || !this.legacyStore || !this.protectedStore) {
      throw new Error('Encrypted repository recovery is unavailable. No local data was removed.');
    }
    await clearState(this.legacyStore);
    await this.repository.destroyAllData();
    await this.protectedStore.removeItem(NORMALIZED_STORE_MIGRATION_CHECKPOINT_KEY);
  }

  inspect(): Promise<RepositoryInspection> {
    return this.migration.inspect();
  }
}

export const createDualReadSingleWriteEntityRepository = (
  options: NormalizedStoreMigrationOptions
): EntityRepository => {
  const migration = new NormalizedStoreMigrationCoordinator(options);
  return new DualReadSingleWriteEntityRepository(
    migration,
    options.repository,
    options.legacyStore,
    options.protectedStore
  );
};
