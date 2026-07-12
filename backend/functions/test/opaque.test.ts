import { describe, expect, it } from 'vitest';

import {
  deriveAliasKeys,
  deriveContentFreeKeys,
  parseKeyRing,
} from '../src/domain/opaque.js';

const KEY_A = Buffer.alloc(32, 1).toString('base64');
const KEY_B = Buffer.alloc(32, 2).toString('base64');
const PREHASH = 'ab'.repeat(32);

describe('opaque identity derivation', () => {
  it('is deterministic and purpose/UID separated', () => {
    const ring = parseKeyRing({ current: { version: 'v2', keyBase64: KEY_A } });
    const first = deriveAliasKeys(ring, 'uid-one', 'BIRTHDAY', 'RECIPIENT', [
      PREHASH,
    ]);
    expect(first).toEqual(
      deriveAliasKeys(ring, 'uid-one', 'BIRTHDAY', 'RECIPIENT', [PREHASH]),
    );
    expect(first).not.toEqual(
      deriveAliasKeys(ring, 'uid-two', 'BIRTHDAY', 'RECIPIENT', [PREHASH]),
    );
    expect(first).not.toEqual(
      deriveAliasKeys(ring, 'uid-one', 'BIRTHDAY', 'DESTINATION', [PREHASH]),
    );
    expect(first[0]).not.toContain(PREHASH);
  });

  it('checks old/new pepper aliases during the entire migration interval', () => {
    const ring = parseKeyRing({
      current: { version: 'v2', keyBase64: KEY_A },
      previous: { version: 'v1', keyBase64: KEY_B },
    });
    const aliases = deriveAliasKeys(ring, 'uid-one', 'BIRTHDAY', 'RECIPIENT', [
      PREHASH,
      'cd'.repeat(32),
    ]);
    expect(aliases).toHaveLength(4);
    expect(aliases.some(alias => alias.startsWith('v1.'))).toBe(true);
    expect(aliases.some(alias => alias.startsWith('v2.'))).toBe(true);
  });

  it('purpose-separates TEST material aliases and includes pepper migrations', () => {
    const ring = parseKeyRing({ current: { version: 'v2', keyBase64: KEY_A } });
    const values = deriveContentFreeKeys(
      ring,
      'uid-one',
      'TEST_MATERIAL',
      'content-free-test-material',
    );
    expect(values).toHaveLength(1);
    expect(values[0]).not.toContain('content-free-test-material');
  });

  it('rejects short keys and duplicate key versions', () => {
    expect(() =>
      parseKeyRing({
        current: {
          version: 'v1',
          keyBase64: Buffer.alloc(8).toString('base64'),
        },
      }),
    ).toThrow();
    expect(() =>
      parseKeyRing({
        current: { version: 'v1', keyBase64: KEY_A },
        previous: { version: 'v1', keyBase64: KEY_B },
      }),
    ).toThrow('DUPLICATE_HMAC_KEY_VERSION');
  });
});
