import { readFileSync } from 'node:fs';

import { describe, expect, it } from 'vitest';

const read = (path: string): string =>
  readFileSync(new URL(path, import.meta.url), 'utf8');

describe('backend operational audit contracts', () => {
  it('declares TTL on every documented cleanup field override', () => {
    const indexes = JSON.parse(read('../../firestore.indexes.json')) as {
      fieldOverrides: {
        collectionGroup: string;
        fieldPath: string;
        ttl?: boolean;
      }[];
    };
    const policy = JSON.parse(read('../../ttl-policies.json')) as {
      field: string;
      collectionGroups: string[];
    };
    const declared = indexes.fieldOverrides
      .filter(override => override.ttl === true)
      .map(override => override.collectionGroup)
      .sort();

    expect(indexes.fieldOverrides.every(entry => entry.fieldPath === policy.field))
      .toBe(true);
    expect(declared).toEqual([...policy.collectionGroups].sort());
  });

  it('keeps content pages no-store under clean URLs and immutable assets cached', () => {
    const firebase = JSON.parse(read('../../firebase.json')) as {
      hosting: {
        headers: {
          source: string;
          headers: { key: string; value: string }[];
        }[];
      };
    };
    const headers = firebase.hosting.headers;
    for (const route of [
      '/',
      '/delete/**',
      '/privacy/**',
      '/terms/**',
      '/support/**',
      '/runtime-config.json',
    ]) {
      expect(
        headers.some(
          entry =>
            entry.source === route &&
            entry.headers.some(
              header =>
                header.key === 'Cache-Control' && header.value === 'no-store',
            ),
        ),
      ).toBe(true);
    }
    expect(
      headers.some(
        entry =>
          entry.source === '/assets/**' &&
          entry.headers.some(
            header =>
              header.key === 'Cache-Control' &&
              header.value === 'public,max-age=31536000,immutable',
          ),
      ),
    ).toBe(true);
  });

  it('uses least-privilege CI permissions and immutable action references', () => {
    const workflow = read('../../../.github/workflows/ci.yml');
    expect(workflow).toMatch(/permissions:\s*\n\s+contents:\s+read/u);
    expect(workflow).toMatch(/concurrency:/u);
    for (const match of workflow.matchAll(/uses:\s+([^\s#]+)/gu)) {
      expect(match[1]).toMatch(/^[^@]+@[a-f0-9]{40}$/u);
    }
  });

  it('logs only a safe failure classification for unexpected callable errors', () => {
    const source = read('../src/functions/index.ts');
    expect(source).toMatch(/logger\.error\('callable_internal_failure'/u);
    expect(source).toMatch(/errorType:\s*safeErrorCategory\(error\)/u);
    expect(read('../src/services/safeErrorCategory.ts')).toMatch(
      /function safeErrorCategory\(error: unknown\)/u,
    );
    expect(source).not.toMatch(/logger\.error\([^;]*error\.message/su);
    expect(read('../src/services/deletionOrchestrator.ts')).toMatch(
      /logger\.error\('deletion_sweep_item_failed'/u,
    );
    expect(read('../src/services/coordinationOperationOrchestrator.ts')).toMatch(
      /logger\.error\('coordination_operation_sweep_item_failed'/u,
    );
  });
});
