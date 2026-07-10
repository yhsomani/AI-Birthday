import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createProductionInitialState } from '../data/productionState';
import { PersistenceCoordinator } from './persistenceCoordinator';

describe('ordered persistence coordinator', () => {
  it('never lets an older delayed state overwrite the latest requested state', async () => {
    const saved: string[] = [];
    let releaseFirst: (() => void) | undefined;
    const firstGate = new Promise<void>(resolve => {
      releaseFirst = resolve;
    });
    let saveCount = 0;
    const coordinator = new PersistenceCoordinator({
      async save(state) {
        saveCount += 1;
        if (saveCount === 1) {
          await firstGate;
        }
        saved.push(state.searchQuery);
      },
      async inspect() {
        return undefined;
      },
      nowIso: () => '2026-07-10T00:00:00.000Z'
    });

    const first = coordinator.schedule({ ...createProductionInitialState(), searchQuery: 'old' });
    const latest = coordinator.schedule({ ...createProductionInitialState(), searchQuery: 'latest' });
    releaseFirst?.();

    assert.equal((await first).status, 'superseded');
    assert.equal((await latest).status, 'persisted');
    assert.deepEqual(saved, ['latest']);
  });

  it('serializes a write already in progress before persisting the newer state', async () => {
    const saved: string[] = [];
    let releaseFirst: (() => void) | undefined;
    const firstGate = new Promise<void>(resolve => {
      releaseFirst = resolve;
    });
    let saveCount = 0;
    const coordinator = new PersistenceCoordinator({
      async save(state) {
        saveCount += 1;
        if (saveCount === 1) {
          await firstGate;
        }
        saved.push(state.searchQuery);
      },
      async inspect() {
        return undefined;
      },
      nowIso: () => '2026-07-10T00:00:00.000Z'
    });

    const first = coordinator.schedule({ ...createProductionInitialState(), searchQuery: 'first' });
    await Promise.resolve();
    const second = coordinator.schedule({ ...createProductionInitialState(), searchQuery: 'second' });
    releaseFirst?.();
    assert.equal((await first).status, 'persisted');
    assert.equal((await second).status, 'persisted');
    assert.deepEqual(saved, ['first', 'second']);
  });

  it('does not rewrite an unchanged durable snapshot', async () => {
    let saves = 0;
    const state = createProductionInitialState();
    const coordinator = new PersistenceCoordinator({
      async save() {
        saves += 1;
      },
      async inspect() {
        return undefined;
      },
      nowIso: () => '2026-07-10T00:00:00.000Z'
    });

    assert.equal((await coordinator.schedule(state)).status, 'persisted');
    assert.equal((await coordinator.schedule(state)).status, 'unchanged');
    assert.equal(saves, 1);
  });

  it('does not loop on the same failed snapshot until state changes or the coordinator resets', async () => {
    let attempts = 0;
    const state = createProductionInitialState();
    const coordinator = new PersistenceCoordinator({
      async save() {
        attempts += 1;
        throw new Error('protected storage unavailable');
      },
      async inspect() {
        return undefined;
      },
      nowIso: () => '2026-07-10T00:00:00.000Z'
    });

    await assert.rejects(() => coordinator.schedule(state), /unavailable/);
    assert.equal((await coordinator.schedule(state)).status, 'unchanged');
    assert.equal(attempts, 1);
  });
});
