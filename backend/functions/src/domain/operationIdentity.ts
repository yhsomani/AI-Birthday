import { createHash } from 'node:crypto';

import type { OperationIdentity } from './coordinationOperations.js';
import type { CoordinationOperationKind } from './model.js';

function digest(parts: readonly string[]): string {
  const hash = createHash('sha256');
  hash.update('birthday-autopilot-coordination-operation-v1\0', 'utf8');
  for (const part of parts) {
    hash.update(String(Buffer.byteLength(part, 'utf8')), 'utf8');
    hash.update(':', 'utf8');
    hash.update(part, 'utf8');
  }
  return hash.digest('hex');
}

export function deriveOperationIdentity(
  uid: string,
  operation: CoordinationOperationKind,
  requestId: string,
  bindingParts: readonly string[] = [],
): OperationIdentity {
  const requestKey = digest([uid, operation, requestId]);
  return {
    accountKey: deriveOperationAccountKey(uid),
    requestKey,
    requestFingerprint: digest([uid, operation, requestId, ...bindingParts]),
  };
}

export function deriveOperationAccountKey(uid: string): string {
  return digest([uid, 'ACCOUNT']);
}
