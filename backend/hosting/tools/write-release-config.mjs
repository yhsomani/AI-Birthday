import { writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { readReleaseConfig } from './release-config.mjs';

const configPath = process.env.BIRTHDAY_HOSTING_RELEASE_CONFIG_PATH;
if (typeof configPath !== 'string' || configPath.trim().length === 0) {
  throw new Error(
    'BIRTHDAY_HOSTING_RELEASE_CONFIG_PATH must point to an approved out-of-repository release config',
  );
}

const root = fileURLToPath(new URL('..', import.meta.url));
const output = resolve(root, 'public/runtime-config.json');
const config = await readReleaseConfig(resolve(configPath));
await writeFile(output, `${JSON.stringify(config)}\n`, {
  encoding: 'utf8',
  mode: 0o644,
});
