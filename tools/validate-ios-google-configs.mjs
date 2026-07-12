#!/usr/bin/env node
import { execFileSync } from 'node:child_process';
import { existsSync, lstatSync, readdirSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const tiers = {
  dev: 'com.yashsomani.birthdayautopilot.dev',
  staging: 'com.yashsomani.birthdayautopilot.staging',
  prod: 'com.yashsomani.birthdayautopilot',
};
const required = [
  'API_KEY',
  'BUNDLE_ID',
  'CLIENT_ID',
  'GCM_SENDER_ID',
  'GOOGLE_APP_ID',
  'PROJECT_ID',
  'REVERSED_CLIENT_ID',
];

export function validateIOSGoogleConfigInventory(configRoot) {
  const errors = [];
  const values = [];
  for (const [tier, bundleID] of Object.entries(tiers)) {
    const path = resolve(configRoot, tier, 'GoogleService-Info.plist');
    if (!existsSync(path)) continue;
    if (lstatSync(path).isSymbolicLink()) {
      errors.push(`${tier}: config must not be a symlink`);
      continue;
    }
    if (statSync(path).size > 65_536) {
      errors.push(`${tier}: config exceeds 64 KiB`);
      continue;
    }
    let parsed;
    try {
      parsed = JSON.parse(
        execFileSync(
          '/usr/bin/plutil',
          ['-convert', 'json', '-o', '-', '--', path],
          {
            encoding: 'utf8',
            stdio: ['ignore', 'pipe', 'ignore'],
          },
        ),
      );
    } catch {
      errors.push(`${tier}: config is not a valid property list`);
      continue;
    }
    if (
      !required.every(
        key => typeof parsed[key] === 'string' && parsed[key].length > 0,
      )
    ) {
      errors.push(`${tier}: config is missing required iOS fields`);
      continue;
    }
    if (parsed.BUNDLE_ID !== bundleID)
      errors.push(`${tier}: bundle ID mismatch`);
    if (!/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/u.test(parsed.PROJECT_ID)) {
      errors.push(`${tier}: invalid project ID`);
    }
    if (
      !/^[0-9]+-[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$/u.test(
        parsed.CLIENT_ID,
      )
    ) {
      errors.push(`${tier}: invalid iOS OAuth client ID`);
    }
    const reversed = parsed.CLIENT_ID.split('.').reverse().join('.');
    if (parsed.REVERSED_CLIENT_ID !== reversed) {
      errors.push(`${tier}: reversed client ID mismatch`);
    }
    values.push({ tier, parsed });
  }
  for (const key of ['PROJECT_ID', 'GOOGLE_APP_ID', 'CLIENT_ID', 'API_KEY']) {
    const seen = new Set();
    for (const { tier, parsed } of values) {
      if (seen.has(parsed[key]))
        errors.push(`${tier}: ${key} is shared across tiers`);
      seen.add(parsed[key]);
    }
  }
  if (existsSync(configRoot)) {
    for (const entry of readdirSync(configRoot, { withFileTypes: true })) {
      if (entry.isDirectory() && !(entry.name in tiers)) {
        errors.push(`unsupported config tier directory: ${entry.name}`);
      }
    }
  }
  return errors;
}

if (
  process.argv[1] &&
  resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  const root = resolve(process.argv[2] ?? 'ios/Config');
  const errors = validateIOSGoogleConfigInventory(root);
  for (const error of errors) process.stderr.write(`FAIL ${error}\n`);
  if (errors.length > 0) process.exit(1);
  process.stdout.write('PASS iOS Google configuration inventory\n');
}
