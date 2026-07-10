import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  createProtectedStateStore,
  ProtectedStorageError,
  type LegacyPlaintextInventory,
  type ProtectedStorageBackend
} from './secureStateStoreCore';

interface Harness {
  backend: ProtectedStorageBackend;
  legacyInventory: LegacyPlaintextInventory;
  protectedValues: Map<string, string>;
  legacyKeys: Set<string>;
  calls: string[];
}

const createHarness = (): Harness => {
  const calls: string[] = [];
  const protectedValues = new Map<string, string>();
  const legacyKeys = new Set<string>();
  return {
    calls,
    protectedValues,
    legacyKeys,
    backend: {
      async isAvailableAsync() {
        calls.push('protected:available');
        return true;
      },
      async getItemAsync(key) {
        calls.push(`protected:get:${key}`);
        return protectedValues.get(key) ?? null;
      },
      async setItemAsync(key, value) {
        calls.push(`protected:set:${key}`);
        protectedValues.set(key, value);
      },
      async deleteItemAsync(key) {
        calls.push(`protected:delete:${key}`);
        protectedValues.delete(key);
      }
    },
    legacyInventory: {
      async getAllKeys() {
        calls.push('legacy:list-keys');
        return [...legacyKeys];
      },
      async removeItem(key) {
        calls.push(`legacy:remove:${key}`);
        legacyKeys.delete(key);
      }
    }
  };
};

const createStore = (harness: Harness) =>
  createProtectedStateStore({
    protectedBackend: harness.backend,
    legacyInventory: harness.legacyInventory,
    keychainAccessible: 1
  });

const expectProtectedStorageError = async (
  action: () => Promise<unknown>,
  code: ProtectedStorageError['code']
) => {
  await assert.rejects(action, error => {
    assert.ok(error instanceof ProtectedStorageError);
    assert.equal(error.code, code);
    assert.doesNotMatch(error.message, /secret-value|relateai\.secure\.state/);
    return true;
  });
};

describe('secureStateStore fail-closed boundary', () => {
  it('rejects reads when protected storage is unavailable without reading legacy plaintext', async () => {
    const harness = createHarness();
    harness.legacyKeys.add('fallback.relateai.secure.state.v1');
    harness.backend.isAvailableAsync = async () => false;
    const store = createStore(harness);

    await expectProtectedStorageError(() => store.getItem('relateai.secure.state.v1'), 'unavailable');

    assert.deepEqual(harness.calls, []);
  });

  it('rejects writes when protected storage is unavailable and has no plaintext write capability', async () => {
    const harness = createHarness();
    harness.backend.isAvailableAsync = async () => false;
    const store = createStore(harness);

    await expectProtectedStorageError(
      () => store.setItem('relateai.secure.state.v1', 'secret-value'),
      'unavailable'
    );

    assert.equal(harness.protectedValues.size, 0);
    assert.equal('setItem' in harness.legacyInventory, false);
  });

  it('turns protected read failures into actionable non-sensitive errors', async () => {
    const harness = createHarness();
    harness.backend.getItemAsync = async () => {
      throw new Error('native failure containing secret-value');
    };
    const store = createStore(harness);

    await expectProtectedStorageError(() => store.getItem('relateai.secure.state.v1'), 'read-failed');
    assert.deepEqual(harness.calls, [
      'protected:available',
      'legacy:list-keys'
    ]);
    assert.equal('getItem' in harness.legacyInventory, false);
  });

  it('does not create plaintext when the protected backend rejects a write', async () => {
    const harness = createHarness();
    harness.backend.setItemAsync = async () => {
      throw new Error('native write failure containing secret-value');
    };
    const store = createStore(harness);

    await expectProtectedStorageError(
      () => store.setItem('relateai.secure.state.v1', 'secret-value'),
      'write-failed'
    );

    assert.equal(harness.protectedValues.size, 0);
    assert.equal('setItem' in harness.legacyInventory, false);
  });

  it('detects a legacy fallback by key without ingesting its plaintext value', async () => {
    const harness = createHarness();
    harness.legacyKeys.add('fallback.relateai.secure.state.v1');
    const store = createStore(harness);

    await expectProtectedStorageError(
      () => store.getItem('relateai.secure.state.v1'),
      'legacy-plaintext-detected'
    );

    assert.deepEqual(harness.calls, [
      'protected:available',
      'legacy:list-keys'
    ]);
    assert.equal('getItem' in harness.legacyInventory, false);
  });

  it('reports migration-required status without exposing legacy keys or values', async () => {
    const harness = createHarness();
    harness.legacyKeys.add('fallback.relateai.secure.state.v1');
    const store = createStore(harness);

    const status = await store.getProtectionStatus();

    assert.deepEqual(status, {
      available: true,
      protection: 'platform-protected',
      legacyPlaintext: 'migration-required',
      legacyPlaintextKeyCount: 1
    });
    assert.doesNotMatch(JSON.stringify(status), /relateai\.secure\.state|secret-value/);
  });

  it('writes and verifies protected state without giving legacy storage a write capability', async () => {
    const harness = createHarness();
    const store = createStore(harness);

    await store.setItem('state', 'secret-value');

    assert.equal(harness.protectedValues.get('state'), 'secret-value');
    assert.equal('setItem' in harness.legacyInventory, false);
    assert.deepEqual(harness.calls, [
      'protected:available',
      'legacy:list-keys',
      'protected:set:state',
      'protected:get:state'
    ]);
  });

  it('blocks ordinary writes when legacy plaintext exists so startup cannot overwrite it', async () => {
    const harness = createHarness();
    harness.legacyKeys.add('fallback.state');
    const store = createStore(harness);

    await expectProtectedStorageError(
      () => store.setItem('state', 'secret-value'),
      'legacy-plaintext-detected'
    );

    assert.equal(harness.legacyKeys.has('fallback.state'), true);
    assert.equal(harness.protectedValues.has('state'), false);
    assert.equal(harness.calls.includes('protected:set:state'), false);
  });

  it('preserves both copies and blocks reads when protected and legacy values conflict', async () => {
    const harness = createHarness();
    harness.protectedValues.set('state', 'secret-value');
    harness.legacyKeys.add('fallback.state');
    const store = createStore(harness);

    await expectProtectedStorageError(() => store.getItem('state'), 'legacy-plaintext-detected');

    assert.equal(harness.protectedValues.get('state'), 'secret-value');
    assert.equal(harness.legacyKeys.has('fallback.state'), true);
    assert.equal(harness.calls.includes('protected:get:state'), false);
  });

  it('does not touch legacy storage when a protected write cannot be verified', async () => {
    const harness = createHarness();
    harness.backend.getItemAsync = async key => {
      harness.calls.push(`protected:get:${key}`);
      return null;
    };
    const store = createStore(harness);

    await expectProtectedStorageError(
      () => store.setItem('state', 'secret-value'),
      'write-verification-failed'
    );

    assert.equal(harness.calls.some(call => call.startsWith('legacy:remove:')), false);
  });

  it('attempts legacy cleanup but still reports protected removal as unavailable', async () => {
    const harness = createHarness();
    harness.legacyKeys.add('fallback.state');
    harness.backend.isAvailableAsync = async () => false;
    const store = createStore(harness);

    await expectProtectedStorageError(() => store.removeItem('state'), 'unavailable');

    assert.equal(harness.legacyKeys.has('fallback.state'), false);
    assert.deepEqual(harness.calls, ['legacy:remove:fallback.state']);
  });
});
