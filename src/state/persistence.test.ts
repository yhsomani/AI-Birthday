import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { AppState } from '../domain/types';
import { createTestState } from '../test/testState';
import {
  clearState,
  deserializeState,
  inspectPersistedState,
  loadState,
  loadStateWithRecovery,
  NORMALIZED_STATE_STORAGE_FORMAT,
  PERSISTENCE_VERSION,
  RELATE_CORRUPT_STATE_KEY,
  RELATE_STATE_KEY,
  saveState,
  serializeState,
  STATE_STORAGE_CHUNK_LENGTH,
  type KeyValueStore
} from './persistence';

const LEGACY_CHUNKED_STATE_STORAGE_FORMAT = 'relateai.chunked-state.v1';

type StoredNormalizedEntry = {
  name: string;
  kind: 'singleton' | 'collectionItem';
  index?: number;
  key?: string;
  chunkPrefix?: string;
  chunkCount?: number;
  rawLength: number;
  checksum: string;
};

type StoredNormalizedManifest = {
  storage: typeof NORMALIZED_STATE_STORAGE_FORMAT;
  envelopeVersion: number;
  migratedFrom?: number[];
  entryPrefix: string;
  entries: StoredNormalizedEntry[];
};

const checksumString = (value: string): string => {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(16).padStart(8, '0');
};

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

  keys() {
    return [...this.values.keys()];
  }
}

const createLargeState = (): AppState => {
  const state = createTestState();
  const note = 'Long relationship context with preferences, history, draft style, and reminders. '.repeat(120);
  return {
    ...state,
    contacts: [
      ...state.contacts,
      ...Array.from({ length: 40 }, (_, index) => ({
        ...state.contacts[index % state.contacts.length],
        id: `c-large-${index}`,
        name: `Large Contact ${index}`,
        notesSummary: `${note}Contact index ${index}.`
      }))
    ]
  };
};

const requireNormalizedManifest = (store: MemoryStore): StoredNormalizedManifest => {
  const activeRaw = store.peek(RELATE_STATE_KEY);
  assert.ok(activeRaw);
  const manifest = JSON.parse(activeRaw) as Partial<StoredNormalizedManifest>;

  assert.equal(manifest.storage, NORMALIZED_STATE_STORAGE_FORMAT);
  assert.equal(typeof manifest.envelopeVersion, 'number');
  assert.equal(typeof manifest.entryPrefix, 'string');
  assert.equal(Array.isArray(manifest.entries), true);

  return manifest as StoredNormalizedManifest;
};

const payloadKeysForEntry = (entry: StoredNormalizedEntry) => {
  if (entry.key) {
    return [entry.key];
  }
  return Array.from({ length: entry.chunkCount ?? 0 }, (_, index) => `${entry.chunkPrefix}${index}`);
};

const payloadKeysForManifest = (manifest: StoredNormalizedManifest) => manifest.entries.flatMap(payloadKeysForEntry);

const seedLegacyChunkedPayload = async (store: MemoryStore, state: AppState) => {
  const raw = serializeState(state);
  const checksum = checksumString(raw);
  const chunkPrefix = `${RELATE_STATE_KEY}.chunk.legacy.${raw.length}.${checksum}.`;
  const chunkCount = Math.ceil(raw.length / STATE_STORAGE_CHUNK_LENGTH);
  for (let index = 0; index < chunkCount; index += 1) {
    const start = index * STATE_STORAGE_CHUNK_LENGTH;
    await store.setItem(`${chunkPrefix}${index}`, raw.slice(start, start + STATE_STORAGE_CHUNK_LENGTH));
  }
  await store.setItem(
    RELATE_STATE_KEY,
    JSON.stringify({
      storage: LEGACY_CHUNKED_STATE_STORAGE_FORMAT,
      storageVersion: 1,
      envelopeVersion: PERSISTENCE_VERSION,
      savedAt: new Date().toISOString(),
      chunkPrefix,
      chunkCount,
      chunkLength: STATE_STORAGE_CHUNK_LENGTH,
      rawLength: raw.length,
      checksum
    })
  );
};

describe('state persistence contract', () => {
  it('round-trips app state with a versioned envelope', () => {
    const state = createTestState();
    state.selectedEventId = state.events[0].id;
    const raw = serializeState(state);
    const restored = deserializeState(raw);
    const envelope = JSON.parse(raw) as { version: number };

    assert.equal(envelope.version, PERSISTENCE_VERSION);
    assert.equal(restored.contacts.length, state.contacts.length);
    assert.equal(restored.messages.length, state.messages.length);
    assert.equal(restored.selectedEventId, state.events[0].id);
  });

  it('saves, loads, and clears state through a key-value store', async () => {
    const store = new MemoryStore();
    const state = createTestState();

    await saveState(store, state);
    assert.equal(requireNormalizedManifest(store).envelopeVersion, PERSISTENCE_VERSION);
    const loaded = await loadState(store);
    assert.equal(loaded?.contacts[0].id, state.contacts[0].id);

    await clearState(store);
    const cleared = await loadState(store);
    assert.equal(cleared, undefined);
  });

  it('rejects unsupported versions', () => {
    const state = createTestState();
    const raw = JSON.stringify({ version: 999, savedAt: new Date().toISOString(), state });

    assert.throws(() => deserializeState(raw), /newer app version/);
  });

  it('migrates older persisted state envelopes in place', async () => {
    const store = new MemoryStore();
    const state = createTestState();
    const olderState = {
      ...state,
      aiProvider: undefined,
      privacy: {
        ...state.privacy,
        whatsappHandoffConsent: undefined,
        whatsappAutomationConsent: true
      }
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
    const rewritten = requireNormalizedManifest(store);

    assert.equal(loaded.status, 'loaded');
    if (loaded.status === 'loaded') {
      assert.equal(loaded.migrated, true);
      assert.equal(loaded.state.aiProvider.status, 'Not configured');
      assert.deepEqual(loaded.state.settings.blackouts, []);
      assert.equal(loaded.state.onboarding.currentStepId, 'intro');
      assert.equal(loaded.state.privacy.permissionDecisions.Contacts, 'Not requested');
      assert.equal(loaded.state.privacy.whatsappHandoffConsent, true);
      assert.equal('whatsappAutomationConsent' in (loaded.state.privacy as unknown as Record<string, unknown>), false);
    }
    assert.equal(rewritten.envelopeVersion, PERSISTENCE_VERSION);
    assert.deepEqual(rewritten.migratedFrom, [1]);
  });

  it('normalizes large persisted state payloads into bounded entries and restores them', async () => {
    const store = new MemoryStore();
    const state = createLargeState();

    await saveState(store, state);
    const manifest = requireNormalizedManifest(store);
    const contactEntries = manifest.entries.filter(entry => entry.name === 'contacts');
    const chunkedEntries = manifest.entries.filter(entry => entry.chunkPrefix);
    const loaded = await loadState(store);

    assert.equal(contactEntries.length, state.contacts.length);
    assert.equal(chunkedEntries.length > 0, true);
    for (const entry of chunkedEntries) {
      for (const key of payloadKeysForEntry(entry)) {
        assert.ok((store.peek(key)?.length ?? 0) <= STATE_STORAGE_CHUNK_LENGTH);
      }
    }
    assert.equal(loaded?.contacts.length, state.contacts.length);
    assert.equal(loaded?.contacts[39].notesSummary, state.contacts[39].notesSummary);
  });

  it('inspects normalized storage health without exposing payload data', async () => {
    const store = new MemoryStore();
    const state = createLargeState();

    await saveState(store, state);
    const health = await inspectPersistedState(store);

    assert.equal(health.status, 'Ready');
    assert.equal(health.storageFormat, 'Normalized');
    assert.equal(health.entryCount > state.contacts.length, true);
    assert.equal(health.payloadBytes > 0, true);
    const largestContactNote = Math.max(...state.contacts.map(contact => contact.notesSummary.length));
    assert.equal(health.largestEntryBytes <= largestContactNote + 8000, true);
    assert.doesNotMatch(JSON.stringify(health), /Large Contact 39|Long relationship context/);
  });

  it('inspects missing and corrupt storage without mutating stored data', async () => {
    const missingStore = new MemoryStore();
    const missingHealth = await inspectPersistedState(missingStore);

    assert.equal(missingHealth.status, 'Missing');
    assert.equal(missingHealth.storageFormat, 'Missing');

    const corruptStore = new MemoryStore();
    const state = createLargeState();
    await saveState(corruptStore, state);
    const manifest = requireNormalizedManifest(corruptStore);
    const removedKey = payloadKeysForEntry(manifest.entries.find(entry => entry.chunkPrefix) ?? manifest.entries[0])[0];
    await corruptStore.removeItem(removedKey);
    const corruptHealth = await inspectPersistedState(corruptStore);

    assert.equal(corruptHealth.status, 'Corrupt');
    assert.equal(corruptHealth.storageFormat, 'Corrupt');
    assert.match(corruptHealth.issue ?? '', /missing|integrity|length/i);
    assert.notEqual(corruptStore.peek(RELATE_STATE_KEY), undefined);
  });

  it('loads legacy chunked state payloads for migration compatibility', async () => {
    const store = new MemoryStore();
    const state = createLargeState();

    await seedLegacyChunkedPayload(store, state);
    const loaded = await loadState(store);

    assert.equal(loaded?.contacts.length, state.contacts.length);
    assert.equal(loaded?.contacts[39].notesSummary, state.contacts[39].notesSummary);
  });

  it('removes stale normalized entries after a large state is replaced', async () => {
    const store = new MemoryStore();
    const state = createLargeState();

    await saveState(store, state);
    const firstManifest = requireNormalizedManifest(store);
    const firstPayloadKeys = payloadKeysForManifest(firstManifest);
    await saveState(store, {
      ...state,
      contacts: state.contacts.map((contact, index) =>
        index === 0 ? { ...contact, notesSummary: `${contact.notesSummary} Updated.` } : contact
      )
    });
    const nextManifest = requireNormalizedManifest(store);

    assert.notEqual(nextManifest.entryPrefix, firstManifest.entryPrefix);
    assert.equal(
      firstPayloadKeys.every(key => store.peek(key) === undefined),
      true
    );
  });

  it('selectively recovers from missing normalized entries without deleting valid payloads', async () => {
    const store = new MemoryStore();
    const state = createLargeState();

    await saveState(store, state);
    const manifest = requireNormalizedManifest(store);
    const payloadKeys = payloadKeysForManifest(manifest);
    const chunkedEntry = manifest.entries.find(entry => entry.chunkPrefix);
    const removedKey = payloadKeysForEntry(chunkedEntry ?? manifest.entries[0])[0];
    await store.removeItem(removedKey);

    const result = await loadStateWithRecovery(store);
    const recoveryRaw = store.peek(RELATE_CORRUPT_STATE_KEY);

    assert.equal(result.status, 'loaded');
    if (result.status === 'loaded') {
      assert.ok(result.recovery);
      assert.ok(result.recovery.issueCount > 0);
      assert.equal(result.recovery.redacted, true);
      assert.ok(result.state.contacts.length > 0);
    }
    const recovery = JSON.parse(recoveryRaw ?? '{}') as Record<string, unknown>;
    assert.equal(recovery.redacted, true);
    assert.equal(recovery.outcome, 'selective');
    assert.equal(recovery.raw, undefined);
    assert.equal(recovery.checksum, undefined);
    assert.notEqual(store.peek(RELATE_STATE_KEY), undefined);
    assert.equal(
      payloadKeys.some(key => store.peek(key) !== undefined),
      true
    );
  });

  it('records an unrecoverable redacted manifest without private payload fragments', async () => {
    const store = new MemoryStore();
    const corruptPayload = '{not-json';
    await store.setItem(RELATE_STATE_KEY, corruptPayload);

    const result = await loadStateWithRecovery(store);
    const quarantine = store.peek(RELATE_CORRUPT_STATE_KEY);

    assert.equal(result.status, 'recovered');
    assert.equal(store.peek(RELATE_STATE_KEY), corruptPayload);
    const manifest = JSON.parse(quarantine ?? '{}') as Record<string, unknown>;
    assert.equal(manifest.redacted, true);
    assert.equal(manifest.outcome, 'unrecoverable');
    assert.equal(manifest.raw, undefined);
    assert.equal(manifest.checksum, undefined);
    assert.doesNotMatch(quarantine ?? '', /not-json/);
  });

  it('preserves a newer-version payload for rollback instead of deleting it during load', async () => {
    const store = new MemoryStore();
    const newerPayload = JSON.stringify({
      version: PERSISTENCE_VERSION + 1,
      savedAt: '2026-07-10T00:00:00.000Z',
      state: createTestState()
    });
    await store.setItem(RELATE_STATE_KEY, newerPayload);

    const result = await loadStateWithRecovery(store);
    const recovery = store.peek(RELATE_CORRUPT_STATE_KEY);

    assert.equal(result.status, 'recovered');
    if (result.status === 'recovered') {
      assert.match(result.reason, /newer app version/i);
    }
    assert.equal(store.peek(RELATE_STATE_KEY), newerPayload);
    assert.match(recovery ?? '', /unrecoverable/);
    assert.doesNotMatch(recovery ?? '', /Asha|notesSummary|message/i);
  });

  it('recovers valid direct-envelope records and emits only redacted issue metadata', async () => {
    const store = new MemoryStore();
    const state = createTestState();
    const privateMarker = 'DO-NOT-PERSIST-PRIVATE-MARKER';
    const malformedState = {
      ...state,
      contacts: [...state.contacts, { id: 'broken-contact', name: privateMarker, phone: '+911234567890' }],
      events: [
        ...state.events,
        { ...state.events[0], id: 'orphan-event', contactId: 'broken-contact', label: privateMarker }
      ],
      activity: [
        ...state.activity,
        {
          ...state.activity[0],
          id: 'stale-activity',
          contactId: 'missing-contact',
          messageId: 'missing-message',
          detail: privateMarker
        }
      ]
    };
    const raw = JSON.stringify({
      version: PERSISTENCE_VERSION,
      savedAt: new Date().toISOString(),
      state: malformedState
    });
    await store.setItem(RELATE_STATE_KEY, raw);

    const result = await loadStateWithRecovery(store);
    assert.equal(result.status, 'loaded');
    if (result.status !== 'loaded') return;
    assert.equal(result.state.contacts.length, state.contacts.length);
    assert.equal(
      result.state.events.some(event => event.id === 'orphan-event'),
      false
    );
    const recoveredActivity = result.state.activity.find(item => item.id === 'stale-activity');
    assert.ok(recoveredActivity);
    assert.equal(recoveredActivity.contactId, undefined);
    assert.equal(recoveredActivity.messageId, undefined);
    assert.equal(recoveredActivity.status, 'Obsolete');
    assert.ok(result.recovery);
    assert.ok((result.recovery?.excludedRecordCount ?? 0) >= 2);

    const manifest = store.peek(RELATE_CORRUPT_STATE_KEY) ?? '';
    assert.doesNotMatch(manifest, new RegExp(privateMarker));
    assert.doesNotMatch(manifest, /1234567890|broken-contact|missing-contact|missing-message/);
    assert.match(manifest, /invalid-field|missing-reference|reference-cleared/);
    assert.throws(() => deserializeState(raw), /bounded runtime validation/i);
  });

  it('selectively excludes one malformed normalized record while retaining its siblings', async () => {
    const store = new MemoryStore();
    const state = createTestState();
    await saveState(store, state);
    const manifest = requireNormalizedManifest(store);
    const contactEntry = manifest.entries.find(entry => entry.name === 'contacts' && entry.index === 1);
    assert.ok(contactEntry?.key);
    const malformed = JSON.stringify({ id: 'private-id', name: 'PRIVATE-NAME', notesSummary: 'PRIVATE-NOTE' });
    await store.setItem(contactEntry.key, malformed);
    contactEntry.rawLength = malformed.length;
    contactEntry.checksum = checksumString(malformed);
    await store.setItem(RELATE_STATE_KEY, JSON.stringify(manifest));

    const health = await inspectPersistedState(store);
    assert.equal(health.status, 'Corrupt');
    assert.match(health.issue ?? '', /redacted validation issue/i);
    assert.doesNotMatch(JSON.stringify(health), /PRIVATE-NAME|PRIVATE-NOTE|private-id/);

    const result = await loadStateWithRecovery(store);
    assert.equal(result.status, 'loaded');
    if (result.status !== 'loaded') return;
    assert.equal(result.state.contacts.length, state.contacts.length - 1);
    assert.ok(result.state.contacts.some(contact => contact.id === state.contacts[0].id));
    assert.ok(result.recovery);
    const recoveryRaw = store.peek(RELATE_CORRUPT_STATE_KEY) ?? '';
    assert.doesNotMatch(recoveryRaw, /PRIVATE-NAME|PRIVATE-NOTE|private-id/);
    assert.match(recoveryRaw, /contacts/);
  });

  it('rethrows protected-storage failures at normalized child depth without deleting state', async () => {
    const store = new MemoryStore();
    const state = createTestState();
    await saveState(store, state);
    const manifest = requireNormalizedManifest(store);
    const protectedEntryKey = payloadKeysForEntry(manifest.entries[0])[0];
    const originalGet = store.getItem.bind(store);
    store.getItem = async key => {
      if (key === protectedEntryKey) {
        const error = new Error('native error with private diagnostics');
        error.name = 'ProtectedStorageError';
        throw error;
      }
      return originalGet(key);
    };

    await assert.rejects(
      () => loadStateWithRecovery(store),
      error => {
        assert.ok(error instanceof Error);
        assert.equal(error.name, 'ProtectedStorageError');
        return true;
      }
    );
    assert.notEqual(store.peek(RELATE_STATE_KEY), undefined);
    assert.equal(store.peek(RELATE_CORRUPT_STATE_KEY), undefined);
  });
});
