#!/usr/bin/env node

import { createHash } from 'node:crypto';
import process from 'node:process';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

import { collectDistributionEvidenceFiles } from './validate-distribution-evidence.mjs';

const DOMAIN = 'birthday-autopilot:distribution-evidence-root:v1\0';

export function hashDistributionEvidenceRoot(evidenceRoot) {
  const files = collectDistributionEvidenceFiles(evidenceRoot);
  const hash = createHash('sha256');
  hash.update(DOMAIN, 'utf8');
  for (const [relativePath, observed] of files) {
    const record = JSON.stringify([
      relativePath,
      observed.bytes,
      observed.sha256,
    ]);
    hash.update(`${Buffer.byteLength(record, 'utf8')}:`, 'utf8');
    hash.update(record, 'utf8');
  }
  return hash.digest('hex');
}

const direct =
  process.argv[1] !== undefined &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href;

if (direct) {
  try {
    if (process.argv.length !== 3) {
      throw new Error(
        'usage: hash-distribution-evidence-root.mjs <evidence-root>',
      );
    }
    process.stdout.write(`${hashDistributionEvidenceRoot(process.argv[2])}\n`);
  } catch (error) {
    process.stderr.write(
      `FAIL ${
        error instanceof Error ? error.message : 'evidence-root hashing failed'
      }\n`,
    );
    process.exitCode = 1;
  }
}
