export const DELETION_RECEIPT_ID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/u;

const RECEIPT_KEY_DOMAIN = 'birthday-deletion-receipt-v1\0';
const SHA256_HEX_PATTERN = /^[a-f0-9]{64}$/u;
const DELETION_STAGES = new Set([
  'DRAINING',
  'PURGING',
  'AUTH_DELETION_PENDING',
  'VERIFYING',
]);

function isRecord(value) {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function exactKeys(value, keys) {
  return Object.keys(value).sort().join('|') === [...keys].sort().join('|');
}

function hasOwn(value, key) {
  return Object.prototype.hasOwnProperty.call(value, key);
}

function safeTime(value) {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
}

function positiveSafeInteger(value) {
  return safeTime(value) && value > 0;
}

export function isDeletionReceiptId(value) {
  return typeof value === 'string' && DELETION_RECEIPT_ID_PATTERN.test(value);
}

export async function deriveDeletionReceiptKey(
  receiptId,
  webCrypto = globalThis.crypto,
) {
  if (!isDeletionReceiptId(receiptId)) {
    throw new Error('Invalid deletion receipt identifier');
  }
  const digest = await webCrypto.subtle.digest(
    'SHA-256',
    new TextEncoder().encode(`${RECEIPT_KEY_DOMAIN}${receiptId}`),
  );
  return Array.from(new Uint8Array(digest), byte =>
    byte.toString(16).padStart(2, '0'),
  ).join('');
}

export async function deletionStartProjection(
  value,
  expectedReceiptId,
  webCrypto = globalThis.crypto,
) {
  if (
    !isDeletionReceiptId(expectedReceiptId) ||
    !isRecord(value) ||
    typeof value.kind !== 'string'
  ) {
    return { kind: 'UNKNOWN' };
  }
  if (value.kind === 'REFUSED') {
    if (!exactKeys(value, ['kind', 'reason'])) {
      return { kind: 'UNKNOWN' };
    }
    if (value.reason === 'COORDINATION_OPERATION_IN_PROGRESS') {
      return { kind: 'BUSY' };
    }
    return value.reason === 'REQUEST_MISMATCH'
      ? { kind: 'MISMATCH' }
      : { kind: 'UNKNOWN' };
  }
  if (
    (value.kind !== 'STARTED' && value.kind !== 'REPLAYED') ||
    !exactKeys(value, ['fence', 'kind', 'receiptId', 'tombstone']) ||
    value.receiptId !== expectedReceiptId ||
    !isRecord(value.tombstone)
  ) {
    return { kind: 'UNKNOWN' };
  }

  const tombstone = value.tombstone;
  const tombstoneKeys = [
    'createdAtMs',
    'drainUntilMs',
    'requestKey',
    'schemaVersion',
    'stage',
    'updatedAtMs',
    ...(hasOwn(tombstone, 'cleanupAtMs') ? ['cleanupAtMs'] : []),
  ];
  if (
    !exactKeys(tombstone, tombstoneKeys) ||
    tombstone.schemaVersion !== 1 ||
    typeof tombstone.requestKey !== 'string' ||
    !SHA256_HEX_PATTERN.test(tombstone.requestKey) ||
    typeof tombstone.stage !== 'string' ||
    !DELETION_STAGES.has(tombstone.stage) ||
    !safeTime(tombstone.createdAtMs) ||
    !safeTime(tombstone.updatedAtMs) ||
    !safeTime(tombstone.drainUntilMs) ||
    tombstone.createdAtMs > tombstone.updatedAtMs ||
    tombstone.createdAtMs > tombstone.drainUntilMs ||
    (hasOwn(tombstone, 'cleanupAtMs') &&
      (!safeTime(tombstone.cleanupAtMs) ||
        tombstone.updatedAtMs > tombstone.cleanupAtMs))
  ) {
    return { kind: 'UNKNOWN' };
  }

  let expectedRequestKey;
  try {
    expectedRequestKey = await deriveDeletionReceiptKey(
      expectedReceiptId,
      webCrypto,
    );
  } catch {
    return { kind: 'UNKNOWN' };
  }
  if (tombstone.requestKey !== expectedRequestKey) {
    return { kind: 'UNKNOWN' };
  }

  if (value.fence !== null) {
    if (
      !isRecord(value.fence) ||
      !exactKeys(value.fence, [
        'deletionDrainUntilMs',
        'mode',
        'resetGeneration',
        'senderEpoch',
      ]) ||
      value.fence.mode !== 'DELETING' ||
      !positiveSafeInteger(value.fence.senderEpoch) ||
      !positiveSafeInteger(value.fence.resetGeneration) ||
      !safeTime(value.fence.deletionDrainUntilMs) ||
      value.fence.deletionDrainUntilMs !== tombstone.drainUntilMs
    ) {
      return { kind: 'UNKNOWN' };
    }
  }

  return { kind: 'ACCEPTED', receiptId: expectedReceiptId };
}

export function receiptProjection(value) {
  if (!isRecord(value) || typeof value.kind !== 'string') {
    return { kind: 'UNKNOWN' };
  }
  if (value.kind === 'NOT_FOUND' && exactKeys(value, ['kind'])) {
    return { kind: 'NOT_FOUND' };
  }
  if (
    value.kind === 'IN_PROGRESS' &&
    exactKeys(value, ['kind', 'requestedAtMs', 'updatedAtMs']) &&
    safeTime(value.requestedAtMs) &&
    safeTime(value.updatedAtMs) &&
    value.requestedAtMs <= value.updatedAtMs
  ) {
    return {
      kind: 'IN_PROGRESS',
      requestedAtMs: value.requestedAtMs,
      updatedAtMs: value.updatedAtMs,
    };
  }
  if (
    value.kind === 'COMPLETED' &&
    exactKeys(value, [
      'appAccountDeleted',
      'completedAtMs',
      'externalCopiesNotDeleted',
      'kind',
      'requestedAtMs',
      'serverDataDeleted',
    ]) &&
    safeTime(value.requestedAtMs) &&
    safeTime(value.completedAtMs) &&
    value.requestedAtMs <= value.completedAtMs &&
    value.appAccountDeleted === true &&
    value.serverDataDeleted === true &&
    value.externalCopiesNotDeleted === true
  ) {
    return {
      kind: 'COMPLETED',
      requestedAtMs: value.requestedAtMs,
      completedAtMs: value.completedAtMs,
    };
  }
  return { kind: 'UNKNOWN' };
}
