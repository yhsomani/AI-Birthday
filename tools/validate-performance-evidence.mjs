#!/usr/bin/env node

import { createHash } from 'node:crypto';
import {
  closeSync,
  fstatSync,
  openSync,
  readFileSync,
  readSync,
} from 'node:fs';
import { dirname, resolve } from 'node:path';
import process from 'node:process';
import { fileURLToPath, pathToFileURL } from 'node:url';

const DIRECTORY = dirname(fileURLToPath(import.meta.url));
const BUDGETS = JSON.parse(
  readFileSync(resolve(DIRECTORY, 'performance-budgets.json'), 'utf8'),
);
const TOP_LEVEL_KEYS = new Set([
  'schemaVersion',
  'platform',
  'sourceRevision',
  'measuredAt',
  'artifact',
  'device',
  'references',
  'shared',
  'platformMetrics',
]);
const ARTIFACT_KEYS = new Set([
  'applicationId',
  'version',
  'sha256',
  'signedReleaseLike',
]);
const DEVICE_KEYS = new Set(['model', 'osVersion', 'ramMiB']);
const REFERENCE_KEYS = new Set([
  'protocolReference',
  'protocolSha256',
  'rawResultsReference',
  'rawResultsSha256',
]);
const SHARED_KEYS = new Set([
  'coldStartHomeMs',
  'warmHomeMs',
  'search10000Ms',
  'normalizeCommit10000WallMs',
  'normalizeCommit10000PeakRssMiB',
  'crashAnrOomCount',
]);
const PLATFORM_KEYS = {
  android: new Set([
    'noDueReconcileCpuMs',
    'claimArmLatencyMs',
    'batteryDeltaPercentagePoints',
    'batteryBenchmarkHours',
  ]),
  ios: new Set([
    'reminderNoChangeCpuMs',
    'reminderReplaceWallMs',
    'composerReadyLatencyMs',
  ]),
};
const PRODUCTION_APPLICATION_ID = Object.freeze({
  android: 'com.yashsomani.birthdayautopilot',
  ios: 'com.yashsomani.birthdayautopilot',
});
const SHA256 = /^[0-9a-f]{64}$/u;
const REVISION = /^[0-9a-f]{40}$/u;
const SAFE_REFERENCE = /^[A-Za-z0-9][A-Za-z0-9 ._:/#-]{0,255}$/u;
const UTC_INSTANT =
  /^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?Z$/u;
const MAXIMUM_EVIDENCE_BYTES = 2 * 1024 * 1024;
const MAXIMUM_ARTIFACT_BYTES = 1024 * 1024 * 1024;
const MAXIMUM_EVIDENCE_AGE_MS = 30 * 24 * 60 * 60 * 1000;

const parseExactUtcInstant = value => {
  if (typeof value !== 'string' || !UTC_INSTANT.test(value)) return null;
  const epochMillis = Date.parse(value);
  if (!Number.isFinite(epochMillis)) return null;
  const instant = new Date(epochMillis);
  return instant.getUTCFullYear() === Number(value.slice(0, 4)) &&
    instant.getUTCMonth() + 1 === Number(value.slice(5, 7)) &&
    instant.getUTCDate() === Number(value.slice(8, 10)) &&
    instant.getUTCHours() === Number(value.slice(11, 13)) &&
    instant.getUTCMinutes() === Number(value.slice(14, 16)) &&
    instant.getUTCSeconds() === Number(value.slice(17, 19))
    ? epochMillis
    : null;
};

const isObject = value =>
  value !== null && typeof value === 'object' && !Array.isArray(value);

const exactKeys = (value, expected, label, errors) => {
  if (!isObject(value)) {
    errors.push(`${label} must be an object`);
    return false;
  }
  for (const key of Object.keys(value)) {
    if (!expected.has(key))
      errors.push(`${label} has unsupported field ${key}`);
  }
  for (const key of expected) {
    if (!Object.hasOwn(value, key)) errors.push(`${label} is missing ${key}`);
  }
  return true;
};

const finiteSamples = (value, minimum, label, errors) => {
  if (
    !Array.isArray(value) ||
    value.length < minimum ||
    value.length > 500 ||
    value.some(
      sample =>
        typeof sample !== 'number' || !Number.isFinite(sample) || sample < 0,
    )
  ) {
    errors.push(
      `${label} must contain ${minimum} to 500 finite nonnegative samples`,
    );
    return null;
  }
  return [...value].sort((left, right) => left - right);
};

const percentile = (sorted, percentage) =>
  sorted[Math.max(0, Math.ceil((percentage / 100) * sorted.length) - 1)];

const enforcePercentile = (
  value,
  minimum,
  percentage,
  maximum,
  label,
  errors,
  summary,
) => {
  const samples = finiteSamples(value, minimum, label, errors);
  if (samples === null) return;
  const measured = percentile(samples, percentage);
  summary[label] = measured;
  if (measured > maximum) {
    errors.push(`${label} P${percentage} ${measured} exceeds ${maximum}`);
  }
};

const enforceMaximum = (value, minimum, maximum, label, errors, summary) => {
  const samples = finiteSamples(value, minimum, label, errors);
  if (samples === null) return;
  const measured = samples.at(-1);
  summary[label] = measured;
  if (measured > maximum) {
    errors.push(`${label} maximum ${measured} exceeds ${maximum}`);
  }
};

export function validatePerformanceEvidence(
  document,
  {
    expectedPlatform,
    expectedSourceRevision,
    expectedArtifactSha256,
    nowMillis = Date.now(),
  },
) {
  const errors = [];
  const summary = {};
  if (!exactKeys(document, TOP_LEVEL_KEYS, 'evidence', errors)) {
    return { errors, summary };
  }
  if (document.schemaVersion !== BUDGETS.schemaVersion) {
    errors.push(`schemaVersion must be ${BUDGETS.schemaVersion}`);
  }
  if (!Object.hasOwn(PLATFORM_KEYS, document.platform)) {
    errors.push('platform must be android or ios');
  }
  if (document.platform !== expectedPlatform) {
    errors.push('platform does not match the requested release');
  }
  if (!REVISION.test(document.sourceRevision ?? '')) {
    errors.push('sourceRevision is invalid');
  } else if (document.sourceRevision !== expectedSourceRevision) {
    errors.push('sourceRevision does not match the release source');
  }
  const measuredAt = parseExactUtcInstant(document.measuredAt);
  if (measuredAt === null) {
    errors.push('measuredAt must be an RFC 3339 UTC instant');
  } else {
    if (
      measuredAt > nowMillis + 5 * 60 * 1000 ||
      nowMillis - measuredAt > MAXIMUM_EVIDENCE_AGE_MS
    ) {
      errors.push('measuredAt is in the future or older than 30 days');
    }
  }

  if (exactKeys(document.artifact, ARTIFACT_KEYS, 'artifact', errors)) {
    if (
      document.artifact.applicationId !==
      PRODUCTION_APPLICATION_ID[document.platform]
    ) {
      errors.push(
        `artifact applicationId must be the ${String(
          document.platform,
        )} production identifier`,
      );
    }
    if (
      typeof document.artifact.version !== 'string' ||
      !/^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$/u.test(document.artifact.version)
    ) {
      errors.push('artifact version is invalid');
    }
    if (!SHA256.test(document.artifact.sha256 ?? '')) {
      errors.push('artifact sha256 is invalid');
    } else if (document.artifact.sha256 !== expectedArtifactSha256) {
      errors.push(
        'artifact sha256 does not match the measured release artifact',
      );
    }
    if (document.artifact.signedReleaseLike !== true) {
      errors.push(
        'performance evidence requires a signed release-like artifact',
      );
    }
  }

  if (exactKeys(document.device, DEVICE_KEYS, 'device', errors)) {
    if (
      typeof document.device.model !== 'string' ||
      document.device.model.trim() !== document.device.model ||
      document.device.model.length < 2 ||
      document.device.model.length > 120
    ) {
      errors.push('device model is invalid');
    }
    if (
      typeof document.device.osVersion !== 'string' ||
      !/^[0-9A-Za-z][0-9A-Za-z ._()-]{0,63}$/u.test(document.device.osVersion)
    ) {
      errors.push('device osVersion is invalid');
    }
    if (
      !Number.isInteger(document.device.ramMiB) ||
      document.device.ramMiB < 1 ||
      document.device.ramMiB > 1_048_576
    ) {
      errors.push('device ramMiB is invalid');
    } else if (
      document.platform === 'android' &&
      document.device.ramMiB < BUDGETS.android.minimumRamMiB
    ) {
      errors.push(
        `Android reference device must have at least ${BUDGETS.android.minimumRamMiB} MiB RAM`,
      );
    }
  }

  if (exactKeys(document.references, REFERENCE_KEYS, 'references', errors)) {
    for (const key of ['protocolReference', 'rawResultsReference']) {
      if (
        typeof document.references[key] !== 'string' ||
        document.references[key] !== document.references[key].trim() ||
        !SAFE_REFERENCE.test(document.references[key])
      ) {
        errors.push(`references ${key} is invalid`);
      }
    }
    for (const key of ['protocolSha256', 'rawResultsSha256']) {
      if (!SHA256.test(document.references[key] ?? '')) {
        errors.push(`references ${key} is invalid`);
      }
    }
  }

  if (exactKeys(document.shared, SHARED_KEYS, 'shared', errors)) {
    const counts = BUDGETS.minimumSamples;
    enforcePercentile(
      document.shared.coldStartHomeMs,
      counts.latencyP95,
      95,
      BUDGETS.shared.coldStartHomeP95Ms,
      'coldStartHomeMs',
      errors,
      summary,
    );
    enforcePercentile(
      document.shared.warmHomeMs,
      counts.latencyP95,
      95,
      BUDGETS.shared.warmHomeP95Ms,
      'warmHomeMs',
      errors,
      summary,
    );
    enforcePercentile(
      document.shared.search10000Ms,
      counts.latencyP95,
      95,
      BUDGETS.shared.search10000P95Ms,
      'search10000Ms',
      errors,
      summary,
    );
    enforceMaximum(
      document.shared.normalizeCommit10000WallMs,
      counts.boundedOperation,
      BUDGETS.shared.normalizeCommit10000MaximumWallMs,
      'normalizeCommit10000WallMs',
      errors,
      summary,
    );
    enforceMaximum(
      document.shared.normalizeCommit10000PeakRssMiB,
      counts.boundedOperation,
      BUDGETS.shared.normalizeCommit10000MaximumPeakRssMiB,
      'normalizeCommit10000PeakRssMiB',
      errors,
      summary,
    );
    if (
      !Number.isInteger(document.shared.crashAnrOomCount) ||
      document.shared.crashAnrOomCount !==
        BUDGETS.shared.maximumCrashAnrOomCount
    ) {
      errors.push('crashAnrOomCount must be zero');
    }
  }

  const expectedPlatformKeys = PLATFORM_KEYS[document.platform];
  if (
    expectedPlatformKeys !== undefined &&
    exactKeys(
      document.platformMetrics,
      expectedPlatformKeys,
      'platformMetrics',
      errors,
    )
  ) {
    const counts = BUDGETS.minimumSamples;
    if (document.platform === 'android') {
      enforceMaximum(
        document.platformMetrics.noDueReconcileCpuMs,
        counts.boundedOperation,
        BUDGETS.android.noDueReconcileMaximumCpuMs,
        'noDueReconcileCpuMs',
        errors,
        summary,
      );
      enforcePercentile(
        document.platformMetrics.claimArmLatencyMs,
        counts.claimArmLatency,
        95,
        BUDGETS.android.claimArmP95Ms,
        'claimArmLatencyMs',
        errors,
        summary,
      );
      enforcePercentile(
        document.platformMetrics.claimArmLatencyMs,
        counts.claimArmLatency,
        99,
        BUDGETS.android.claimArmP99Ms,
        'claimArmLatencyP99Ms',
        errors,
        summary,
      );
      enforceMaximum(
        document.platformMetrics.batteryDeltaPercentagePoints,
        counts.battery,
        BUDGETS.android.maximumBatteryDeltaPercentagePoints,
        'batteryDeltaPercentagePoints',
        errors,
        summary,
      );
      if (
        document.platformMetrics.batteryBenchmarkHours !==
        BUDGETS.android.batteryBenchmarkHours
      ) {
        errors.push(
          `batteryBenchmarkHours must be ${BUDGETS.android.batteryBenchmarkHours}`,
        );
      }
    } else {
      enforceMaximum(
        document.platformMetrics.reminderNoChangeCpuMs,
        counts.boundedOperation,
        BUDGETS.ios.reminderNoChangeMaximumCpuMs,
        'reminderNoChangeCpuMs',
        errors,
        summary,
      );
      enforceMaximum(
        document.platformMetrics.reminderReplaceWallMs,
        counts.boundedOperation,
        BUDGETS.ios.reminderReplaceMaximumWallMs,
        'reminderReplaceWallMs',
        errors,
        summary,
      );
      enforcePercentile(
        document.platformMetrics.composerReadyLatencyMs,
        counts.latencyP95,
        95,
        BUDGETS.ios.composerReadyP95Ms,
        'composerReadyLatencyMs',
        errors,
        summary,
      );
    }
  }
  return { errors, summary };
}

const sha256File = path => {
  let descriptor;
  try {
    descriptor = openSync(path, 'r');
    const before = fstatSync(descriptor, { bigint: true });
    if (
      !before.isFile() ||
      before.size <= 0n ||
      before.size > BigInt(MAXIMUM_ARTIFACT_BYTES)
    ) {
      throw new Error('artifact has an invalid size or type');
    }
    const digest = createHash('sha256');
    const buffer = Buffer.allocUnsafe(1024 * 1024);
    let bytesRead = 0;
    do {
      bytesRead = readSync(descriptor, buffer, 0, buffer.length, null);
      if (bytesRead > 0) digest.update(buffer.subarray(0, bytesRead));
    } while (bytesRead > 0);
    const after = fstatSync(descriptor, { bigint: true });
    if (
      after.dev !== before.dev ||
      after.ino !== before.ino ||
      after.size !== before.size ||
      after.mtimeNs !== before.mtimeNs ||
      after.ctimeNs !== before.ctimeNs
    ) {
      throw new Error('artifact changed while hashing');
    }
    return digest.digest('hex');
  } finally {
    if (descriptor !== undefined) closeSync(descriptor);
  }
};

const parseArguments = argv => {
  if (argv.length % 2 !== 0) throw new Error('arguments must be pairs');
  const values = new Map();
  const allowed = new Set(['file', 'platform', 'source-revision', 'artifact']);
  for (let index = 0; index < argv.length; index += 2) {
    const raw = argv[index];
    if (!raw?.startsWith('--')) throw new Error('invalid argument');
    const name = raw.slice(2);
    if (!allowed.has(name) || values.has(name)) {
      throw new Error(`unsupported or duplicate argument ${raw}`);
    }
    values.set(name, argv[index + 1]);
  }
  for (const name of allowed) {
    if (!values.has(name)) throw new Error(`missing --${name}`);
  }
  return values;
};

const readEvidence = path => {
  const descriptor = openSync(path, 'r');
  try {
    const stat = fstatSync(descriptor);
    if (
      !stat.isFile() ||
      stat.size <= 0 ||
      stat.size > MAXIMUM_EVIDENCE_BYTES
    ) {
      throw new Error('evidence has an invalid size or type');
    }
    return JSON.parse(readFileSync(descriptor, 'utf8'));
  } finally {
    closeSync(descriptor);
  }
};

const isDirectInvocation =
  process.argv[1] !== undefined &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href;

if (isDirectInvocation) {
  try {
    const values = parseArguments(process.argv.slice(2));
    const result = validatePerformanceEvidence(
      readEvidence(values.get('file')),
      {
        expectedPlatform: values.get('platform'),
        expectedSourceRevision: values.get('source-revision'),
        expectedArtifactSha256: sha256File(values.get('artifact')),
      },
    );
    if (result.errors.length > 0) {
      for (const error of result.errors)
        process.stderr.write(`FAIL ${error}\n`);
      process.exitCode = 1;
    } else {
      process.stdout.write(`${JSON.stringify(result.summary)}\n`);
    }
  } catch (error) {
    process.stderr.write(
      `FAIL ${error instanceof Error ? error.message : 'validation failed'}\n`,
    );
    process.exitCode = 1;
  }
}
