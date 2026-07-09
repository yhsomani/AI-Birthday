import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createInitialState } from './relateReducer';
import {
  clearState,
  deserializeState,
  loadState,
  loadStateWithRecovery,
  PERSISTENCE_VERSION,
  RELATE_CORRUPT_STATE_KEY,
  RELATE_STATE_KEY,
  saveState,
  serializeState,
  type KeyValueStore
} from './persistence';

class MemoryStore implements KeyValueStore {
  private values = new Map<string, string>();

  async getItem(key: string) {
    return this.values.get(key) ?? null;
  }

  async setItem(key: string, value: string) {
    this.values.set(key, value);
  }

  async removeItem(key: string) {
    this.values.delete(key);
  }

  peek(key: string) {
    return this.values.get(key);
  }
}

describe('state persistence contract', () => {
  it('round-trips app state with a versioned envelope', () => {
    const state = createInitialState();
    const raw = serializeState(state);
    const restored = deserializeState(raw);
    const envelope = JSON.parse(raw) as { version: number };

    assert.equal(envelope.version, PERSISTENCE_VERSION);
    assert.equal(restored.contacts.length, state.contacts.length);
    assert.equal(restored.messages.length, state.messages.length);
  });

  it('saves, loads, and clears state through a key-value store', async () => {
    const store = new MemoryStore();
    const state = createInitialState();

    await saveState(store, state);
    const loaded = await loadState(store);
    assert.equal(loaded?.contacts[0].id, state.contacts[0].id);

    await clearState(store);
    const cleared = await loadState(store);
    assert.equal(cleared, undefined);
  });

  it('rejects unsupported versions', () => {
    const state = createInitialState();
    const raw = JSON.stringify({ version: 999, savedAt: new Date().toISOString(), state });

    assert.throws(() => deserializeState(raw), /newer app version/);
  });

  it('migrates older persisted state envelopes in place', async () => {
    const store = new MemoryStore();
    const state = createInitialState();
    const olderState = {
      ...state,
      aiProvider: undefined
    };
    await store.setItem(
      RELATE_STATE_KEY,
      JSON.stringify({
        version: 1,
        savedAt: new Date().toISOString(),
        state: olderState
      })
    );

    const loaded = await loadStateWithRecovery(store);
    const rewritten = JSON.parse(store.peek(RELATE_STATE_KEY) ?? '{}') as {
      version?: number;
      migratedFrom?: number[];
    };

    assert.equal(loaded.status, 'loaded');
    if (loaded.status === 'loaded') {
      assert.equal(loaded.migrated, true);
      assert.equal(loaded.state.aiProvider.status, 'Not configured');
    }
    assert.equal(rewritten.version, PERSISTENCE_VERSION);
    assert.deepEqual(rewritten.migratedFrom, [1]);
  });

  it('quarantines corrupt storage and clears the active state key', async () => {
    const store = new MemoryStore();
    await store.setItem(RELATE_STATE_KEY, '{not-json');

    const result = await loadStateWithRecovery(store);
    const quarantine = store.peek(RELATE_CORRUPT_STATE_KEY);

    assert.equal(result.status, 'recovered');
    assert.equal(store.peek(RELATE_STATE_KEY), undefined);
    assert.match(quarantine ?? '', /not valid JSON/);
  });
});
