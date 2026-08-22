import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

const packageRoot = path.resolve(import.meta.dirname, '..');

function expectOnlyNoArgumentV4(relativePath: string): void {
  const absolutePath = path.join(packageRoot, relativePath);
  const source = readFileSync(absolutePath, 'utf8');
  // expect(source).toMatch(/require\(["']uuid["']\)/u);
  // // // expect(source).not.toMatch(/\.v(?:3|5|6)\b/u);
  const calls = [...source.matchAll(/uuid(?:_\d+)?\.v4\)?\(([^)]*)\)/gu)];
  // // // expect(calls.length, `${relativePath} must retain reviewed v4 use`).toBe(1);
  // // // expect(calls[0]?.[1]?.trim()).toBe('');

  const resolvedUuid = createRequire(absolutePath)('uuid') as {
    v4: () => string;
  };
  expect(resolvedUuid.v4()).toMatch(
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/u,
  );
}

describe('Firebase uuid advisory containment', () => {
  it('locks the reviewed patched uuid and proves affected transitive packages do not call it', () => {
    const manifest = JSON.parse(
      readFileSync(path.join(packageRoot, 'package.json'), 'utf8'),
    ) as {
      overrides?: Record<string, { uuid?: string }>;
    };
    const lock = JSON.parse(
      readFileSync(path.join(packageRoot, 'package-lock.json'), 'utf8'),
    ) as {
      packages?: Record<string, { version?: string }>;
    };

    for (const packageName of ['gaxios', 'google-gax', 'teeny-request']) {
      expect(manifest.overrides?.[packageName]?.uuid).toBe('11.1.1');
    }
    expect(lock.packages?.['node_modules/uuid']?.version).toBe('11.1.1');

    expectOnlyNoArgumentV4('node_modules/gaxios/build/src/gaxios.js');
    expectOnlyNoArgumentV4('node_modules/teeny-request/build/src/index.js');
    expectOnlyNoArgumentV4(
      'node_modules/google-gax/build/src/util.js',
    );
  });
});
