import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

const sourceRoot = fileURLToPath(new URL('../src/', import.meta.url));

describe('purchase verification production boundary', () => {
  it('always enforces App Check for purchase verification', () => {
    const source = readFileSync(join(sourceRoot, 'functions/index.ts'), 'utf8');
    const start = source.indexOf('export const verifyPurchase');
    const end = source.indexOf('export const sweepDeletionDrains', start);
    const purchaseRange = source.slice(start, end);

    expect(purchaseRange).toContain('enforceAppCheck: true');
    expect(purchaseRange).toContain('consumeAppCheckToken: true');
    expect(purchaseRange).toContain('const uid = requireAuthenticated(request)');
    expect(purchaseRange).not.toContain('ENFORCE_APP_CHECK_ON_PURCHASE');
    expect(purchaseRange).not.toContain('requireAuthOnly');
  });

  it('logs aggregate sweeper outcomes without account identifiers', () => {
    const source = readFileSync(join(sourceRoot, 'functions/index.ts'), 'utf8');
    expect(source).toContain("logger.info('deletion_sweep_completed', result)");
    expect(source).toContain(
      "logger.info('coordination_operation_sweep_completed', result)",
    );
  });
});
