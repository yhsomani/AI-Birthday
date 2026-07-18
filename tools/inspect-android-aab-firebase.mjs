#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { lstatSync, realpathSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const SCRIPT_FILE = fileURLToPath(import.meta.url);
const PROJECT_ROOT = resolve(dirname(SCRIPT_FILE), '..');
const ANDROID_ROOT = resolve(PROJECT_ROOT, 'android');
const GRADLEW = resolve(ANDROID_ROOT, 'gradlew');
const MAXIMUM_AAB_BYTES = 1024 * 1024 * 1024;
const MAXIMUM_RESOURCE_DUMP_BYTES = 64 * 1024 * 1024;
const PROJECT_ID = /^[a-z][a-z0-9-]{4,28}[a-z0-9]$/u;
const PROJECT_NUMBER = /^[1-9][0-9]{5,19}$/u;
const ANDROID_APP_ID = /^1:[1-9][0-9]{5,19}:android:[0-9a-f]{8,64}$/u;
const OAUTH_CLIENT = /^[0-9]+-[a-z0-9-]+\.apps\.googleusercontent\.com$/u;

const RESOURCE_FIELDS = Object.freeze({
  projectId: 'project_id',
  projectNumber: 'gcm_defaultSenderId',
  androidAppId: 'google_app_id',
  webOauthClientId: 'default_web_client_id',
});

const parseArguments = arguments_ => {
  if (arguments_.length !== 2 || arguments_[0] !== '--aab') {
    throw new Error('usage: inspect-android-aab-firebase.mjs --aab <path>');
  }
  return arguments_[1];
};

const readOneDefaultString = (lines, resourceName) => {
  const header = new RegExp(`^0x[0-9a-f]{8} - string/${resourceName}$`, 'u');
  const indices = lines.flatMap((line, index) =>
    header.test(line) ? [index] : [],
  );
  if (indices.length !== 1) {
    throw new Error(
      `compiled AAB must contain exactly one string/${resourceName}`,
    );
  }
  const values = [];
  for (let index = indices[0] + 1; index < lines.length; index += 1) {
    if (!lines[index].startsWith('\t')) break;
    values.push(lines[index]);
  }
  const match = /^\t\(default\) - \[STR\] "([^"\\]+)"$/u.exec(
    values.length === 1 ? values[0] : '',
  );
  if (match === null) {
    throw new Error(
      `compiled AAB string/${resourceName} must have one unqualified literal value`,
    );
  }
  return match[1];
};

export function parseAabFirebaseResourceDump(resourceDump) {
  if (
    typeof resourceDump !== 'string' ||
    Buffer.byteLength(resourceDump, 'utf8') === 0 ||
    Buffer.byteLength(resourceDump, 'utf8') > MAXIMUM_RESOURCE_DUMP_BYTES ||
    resourceDump.includes('\0')
  ) {
    throw new Error('bundletool resource dump has an invalid size or encoding');
  }
  const lines = resourceDump.split('\n');
  const result = Object.fromEntries(
    Object.entries(RESOURCE_FIELDS).map(([field, resourceName]) => [
      field,
      readOneDefaultString(lines, resourceName),
    ]),
  );
  if (!PROJECT_ID.test(result.projectId)) {
    throw new Error('compiled AAB Firebase project ID is invalid');
  }
  if (!PROJECT_NUMBER.test(result.projectNumber)) {
    throw new Error('compiled AAB Firebase project number is invalid');
  }
  if (
    !ANDROID_APP_ID.test(result.androidAppId) ||
    !result.androidAppId.startsWith(`1:${result.projectNumber}:android:`)
  ) {
    throw new Error(
      'compiled AAB Firebase Android app ID does not belong to its project number',
    );
  }
  if (
    !OAUTH_CLIENT.test(result.webOauthClientId) ||
    !result.webOauthClientId.startsWith(`${result.projectNumber}-`)
  ) {
    throw new Error(
      'compiled AAB Firebase OAuth client does not belong to its project number',
    );
  }
  return Object.freeze(result);
}

const sameStableFile = (before, after) =>
  before.dev === after.dev &&
  before.ino === after.ino &&
  before.mode === after.mode &&
  before.nlink === after.nlink &&
  before.size === after.size &&
  before.mtimeNs === after.mtimeNs &&
  before.ctimeNs === after.ctimeNs;

export function inspectAabFirebase(aabPath) {
  const requestedPath = resolve(aabPath);
  const beforePath = lstatSync(requestedPath, { bigint: true });
  if (
    !beforePath.isFile() ||
    beforePath.isSymbolicLink() ||
    beforePath.nlink !== 1n ||
    beforePath.size <= 0n ||
    beforePath.size > BigInt(MAXIMUM_AAB_BYTES)
  ) {
    throw new Error('AAB must be a non-linked regular file of bounded size');
  }
  const canonicalAab = realpathSync(requestedPath);
  if (canonicalAab !== requestedPath) {
    throw new Error('AAB path must already be canonical');
  }
  if (!process.env.JAVA_HOME || !process.env.ANDROID_HOME) {
    throw new Error('JAVA_HOME and ANDROID_HOME are required');
  }
  const result = spawnSync(
    GRADLEW,
    [
      '-q',
      'dumpBirthdayAabResources',
      `-PbirthdayAabPath=${canonicalAab}`,
      '--offline',
      '--no-daemon',
      '--no-configuration-cache',
      '--console=plain',
    ],
    {
      cwd: ANDROID_ROOT,
      encoding: 'utf8',
      env: process.env,
      maxBuffer: MAXIMUM_RESOURCE_DUMP_BYTES,
      stdio: ['ignore', 'pipe', 'pipe'],
    },
  );
  if (result.error !== undefined || result.status !== 0) {
    throw new Error(
      'locked bundletool could not decode the AAB Firebase resources',
    );
  }
  const afterPath = lstatSync(requestedPath, { bigint: true });
  if (!sameStableFile(beforePath, afterPath)) {
    throw new Error('AAB changed while Firebase resources were decoded');
  }
  return parseAabFirebaseResourceDump(result.stdout);
}

function run() {
  try {
    const result = inspectAabFirebase(parseArguments(process.argv.slice(2)));
    process.stdout.write(
      [
        result.projectId,
        result.projectNumber,
        result.androidAppId,
        result.webOauthClientId,
      ].join('\t') + '\n',
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : 'unknown error';
    process.stderr.write(`FAIL ${message}\n`);
    process.exitCode = 1;
  }
}

if (process.argv[1] && realpathSync(process.argv[1]) === SCRIPT_FILE) run();
