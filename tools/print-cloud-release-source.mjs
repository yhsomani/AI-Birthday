#!/usr/bin/env node

import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { collectExpectedCloudSource } from './validate-cloud-release-evidence.mjs';

const PROJECT_ROOT = fileURLToPath(new URL('../', import.meta.url));

try {
  if (process.argv.length > 3) {
    throw new Error('usage: print-cloud-release-source.mjs [source-root]');
  }
  const sourceRoot =
    process.argv[2] === undefined
      ? PROJECT_ROOT
      : path.resolve(process.argv[2]);
  process.stdout.write(
    `${JSON.stringify(collectExpectedCloudSource(sourceRoot), null, 2)}\n`,
  );
} catch (error) {
  process.stderr.write(
    `${error instanceof Error ? error.message : String(error)}\n`,
  );
  process.exitCode = 1;
}
