import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

function sourceFiles(directory: string): readonly string[] {
  return readdirSync(directory).flatMap(name => {
    const path = join(directory, name);
    return statSync(path).isDirectory()
      ? sourceFiles(path)
      : path.endsWith('.ts')
      ? [path]
      : [];
  });
}

describe('privacy and server-boundary architecture', () => {
  const sourceRoot = fileURLToPath(new URL('../src/', import.meta.url));

  it('does not define raw contact or message fields in persisted domain records', () => {
    const model = readFileSync(join(sourceRoot, 'domain/model.ts'), 'utf8');
    expect(model).not.toMatch(
      /readonly\s+(?:email|peopleId|phoneNumber|rawPhone|messageText|recipientName|birthday|prompt|approvalHash)\??\s*:/u,
    );
  });

  it('contains no embedded service account, private key, or secret value', () => {
    for (const path of sourceFiles(sourceRoot)) {
      const source = readFileSync(path, 'utf8');
      expect(source).not.toContain('BEGIN PRIVATE KEY');
      expect(source).not.toMatch(/private_key_id\s*[:=]/u);
      expect(source).not.toMatch(/client_email\s*[:=]/u);
      expect(source).not.toMatch(/keyBase64\s*:\s*['"][A-Za-z0-9+/=]{32,}/u);
    }
  });

  it('contains only approved aggregate operational logging', () => {
    const loggerSites: string[] = [];
    for (const path of sourceFiles(sourceRoot)) {
      const source = readFileSync(path, 'utf8');
      expect(source).not.toMatch(/\bconsole\s*\./u);
      const matches = source.matchAll(/\b(?:logger|functionsLogger)\.\w+\(([^)]*)\)/gu);
      for (const match of matches) {
        loggerSites.push(`${path}: ${match[0]}`);
      }
    }

    const allowList = [
      'deletion_sweep_completed',
      'coordination_operation_sweep_completed',
      'callable_internal_failure',
      'deletion_sweep_item_failed',
      'deletion_sweep_defer_failed',
      'coordination_operation_sweep_item_failed',
    ];
    expect(loggerSites).toHaveLength(allowList.length);
    expect(
      allowList.every(event => loggerSites.some(site => site.includes(event))),
    ).toBe(true);
    for (const site of loggerSites) {
      expect(site).not.toMatch(
        /error\.message|uid|requestId|receiptId|purchaseToken/iu,
      );
    }
  });
});
