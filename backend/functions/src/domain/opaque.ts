import { createHmac } from 'crypto';

export function addField(
  hmac: ReturnType<typeof createHmac>,
  value: Uint8Array,
): void {
  // Use Buffer.alloc(4) instead of Buffer.allocUnsafe(4) to ensure memory is zero-initialized
  const length = Buffer.alloc(4);
  length.writeUInt32BE(value.length);
  hmac.update(length);
  hmac.update(value);
}
