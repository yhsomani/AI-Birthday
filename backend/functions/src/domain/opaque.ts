import { createHmac } from 'node:crypto';

import { z } from 'zod';

import type { KeyRing, KeyRingEntry, Purpose } from './model.js';

const secretEntrySchema = z
  .object({
    version: z.string().regex(/^[a-z0-9][a-z0-9._-]{0,15}$/u),
    keyBase64: z.string().min(43).max(256),
  })
  .strict();

const secretKeyRingSchema = z
  .object({
    current: secretEntrySchema,
    previous: secretEntrySchema.optional(),
  })
  .strict();

function decodeEntry(value: z.infer<typeof secretEntrySchema>): KeyRingEntry {
  const key = Buffer.from(value.keyBase64, 'base64');
  if (key.length < 32 || key.length > 64) {
    throw new Error('INVALID_HMAC_KEY_LENGTH');
  }
  return { version: value.version, key };
}

export function parseKeyRing(raw: unknown): KeyRing {
  const value = secretKeyRingSchema.parse(raw);
  const current = decodeEntry(value.current);
  const previous =
    value.previous === undefined ? undefined : decodeEntry(value.previous);
  if (previous?.version === current.version) {
    throw new Error('DUPLICATE_HMAC_KEY_VERSION');
  }
  return previous === undefined ? { current } : { current, previous };
}

function addField(
  hmac: ReturnType<typeof createHmac>,
  value: Uint8Array,
): void {
  const length = Buffer.allocUnsafe(4);
  length.writeUInt32BE(value.length);
  hmac.update(length);
  hmac.update(value);
}

function derive(
  entry: KeyRingEntry,
  uid: string,
  purpose: string,
  value: Uint8Array,
): string {
  const hmac = createHmac('sha256', entry.key);
  addField(hmac, Buffer.from('birthday-autopilot/control-plane/v1', 'utf8'));
  addField(hmac, Buffer.from(entry.version, 'utf8'));
  addField(hmac, Buffer.from(uid, 'utf8'));
  addField(hmac, Buffer.from(purpose, 'utf8'));
  addField(hmac, value);
  return `${entry.version}.${hmac.digest('base64url')}`;
}

export function deriveAliasKeys(
  keyRing: KeyRing,
  uid: string,
  purpose: Purpose,
  namespace: 'RECIPIENT' | 'DESTINATION',
  prehashes: readonly string[],
): readonly string[] {
  const entries =
    keyRing.previous === undefined
      ? [keyRing.current]
      : [keyRing.current, keyRing.previous];
  const unique = new Set<string>();
  for (const entry of entries) {
    for (const prehash of prehashes) {
      unique.add(
        derive(
          entry,
          uid,
          `${purpose}/${namespace}`,
          Buffer.from(prehash, 'hex'),
        ),
      );
    }
  }
  return [...unique].sort();
}

export function deriveContentFreeKeys(
  keyRing: KeyRing,
  uid: string,
  namespace: 'TEST_MATERIAL',
  value: string,
): readonly string[] {
  const entries =
    keyRing.previous === undefined
      ? [keyRing.current]
      : [keyRing.current, keyRing.previous];
  return entries
    .map(entry => derive(entry, uid, namespace, Buffer.from(value, 'utf8')))
    .sort();
}
