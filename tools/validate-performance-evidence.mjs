#!/usr/bin/env node

import { createHash } from 'node:crypto';
import {
  closeSync,
  constants,
  fstatSync,
  lstatSync,
  openSync,
  readFileSync,
  readdirSync,
  readSync,
} from 'node:fs';
import { dirname, relative, resolve, sep } from 'node:path';
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
const DEVICE_KEYS = new Set([
  'model',
  'osVersion',
  'ramMiB',
  'physicalDevice',
  'deviceIdSha256',
  'installationSource',
  'measurementTool',
  'measurementToolVersion',
]);
const REFERENCE_KEYS = new Set([
  'protocolReference',
  'protocolSha256',
  'protocolBytes',
  'rawResultsReference',
  'rawResultsSha256',
  'rawResultsBytes',
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
const SAFE_REFERENCE = /^[A-Za-z0-9][A-Za-z0-9._/-]{0,255}$/u;
const UTC_INSTANT =
  /^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?Z$/u;
const MAXIMUM_EVIDENCE_BYTES = 2 * 1024 * 1024;
const MAXIMUM_ARTIFACT_BYTES = 1024 * 1024 * 1024;
const MAXIMUM_PROTOCOL_BYTES = 2 * 1024 * 1024;
const MAXIMUM_RAW_RESULTS_BYTES = 512 * 1024 * 1024;
const MAXIMUM_SUPPORT_FILES = 2;
const MAXIMUM_EVIDENCE_AGE_MS = 30 * 24 * 60 * 60 * 1000;
const PLACEHOLDER =
  /(?:^|[\s._/-])(?:example|fixture|placeholder|replace|sample|tbd|todo|unknown)(?:$|[\s._/-])/iu;

const isSafeRelativeReference = value =>
  typeof value === 'string' &&
  value === value.trim() &&
  SAFE_REFERENCE.test(value) &&
  !value.startsWith('/') &&
  !value.includes('\\') &&
  value
    .split('/')
    .every(segment => segment !== '' && segment !== '.' && segment !== '..');

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
    expectedApplicationId = PRODUCTION_APPLICATION_ID[expectedPlatform],
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
    if (document.artifact.applicationId !== expectedApplicationId) {
      errors.push(
        `artifact applicationId must match the requested ${String(
          document.platform,
        )} release`,
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
    if (document.device.physicalDevice !== true) {
      errors.push('performance evidence requires a physical device');
    }
    if (
      !SHA256.test(document.device.deviceIdSha256 ?? '') ||
      /^0{64}$/u.test(document.device.deviceIdSha256 ?? '')
    ) {
      errors.push('device deviceIdSha256 must be a nonzero digest');
    }
    const expectedInstallationSources =
      document.platform === 'android'
        ? ['google-play', 'managed-enterprise', 'controlled-direct']
        : ['testflight', 'app-store', 'ad-hoc'];
    if (
      !expectedInstallationSources.includes(document.device.installationSource)
    ) {
      errors.push(
        'device installationSource does not match the release platform',
      );
    }
    for (const key of ['measurementTool', 'measurementToolVersion']) {
      const value = document.device[key];
      if (
        typeof value !== 'string' ||
        value !== value.trim() ||
        value.length < 1 ||
        value.length > 120 ||
        PLACEHOLDER.test(value)
      ) {
        errors.push(`device ${key} must identify a real measurement tool`);
      }
    }
  }

  if (exactKeys(document.references, REFERENCE_KEYS, 'references', errors)) {
    for (const key of ['protocolReference', 'rawResultsReference']) {
      if (!isSafeRelativeReference(document.references[key])) {
        errors.push(`references ${key} is invalid`);
      }
    }
    for (const key of ['protocolSha256', 'rawResultsSha256']) {
      if (!SHA256.test(document.references[key] ?? '')) {
        errors.push(`references ${key} is invalid`);
      }
    }
    for (const [key, maximum] of [
      ['protocolBytes', MAXIMUM_PROTOCOL_BYTES],
      ['rawResultsBytes', MAXIMUM_RAW_RESULTS_BYTES],
    ]) {
      if (
        !Number.isSafeInteger(document.references[key]) ||
        document.references[key] < 1 ||
        document.references[key] > maximum
      ) {
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

const stableMetadata = metadata => ({
  dev: metadata.dev,
  ino: metadata.ino,
  mode: metadata.mode,
  nlink: metadata.nlink,
  size: metadata.size,
  mtimeNs: metadata.mtimeNs,
  ctimeNs: metadata.ctimeNs,
});

const sameMetadata = (left, right) =>
  Object.keys(left).every(key => left[key] === right[key]);

const sha256File = (
  path,
  maximumBytes = MAXIMUM_ARTIFACT_BYTES,
  label = 'artifact',
) => {
  let descriptor;
  try {
    descriptor = openSync(
      path,
      // eslint-disable-next-line no-bitwise
      constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0),
    );
    const before = fstatSync(descriptor, { bigint: true });
    if (
      !before.isFile() ||
      before.nlink !== 1n ||
      before.size <= 0n ||
      before.size > BigInt(maximumBytes)
    ) {
      throw new Error(`${label} has an invalid size, link count, or type`);
    }
    const digest = createHash('sha256');
    const buffer = Buffer.allocUnsafe(1024 * 1024);
    let bytesRead = 0;
    do {
      bytesRead = readSync(descriptor, buffer, 0, buffer.length, null);
      if (bytesRead > 0) digest.update(buffer.subarray(0, bytesRead));
    } while (bytesRead > 0);
    const after = fstatSync(descriptor, { bigint: true });
    if (!sameMetadata(stableMetadata(before), stableMetadata(after))) {
      throw new Error(`${label} changed while hashing`);
    }
    return digest.digest('hex');
  } finally {
    if (descriptor !== undefined) closeSync(descriptor);
  }
};

const inventoryEvidenceRoot = root => {
  const rootMetadata = lstatSync(root, { bigint: true });
  if (!rootMetadata.isDirectory() || rootMetadata.isSymbolicLink()) {
    throw new Error('evidence root must be a non-symlink directory');
  }
  const files = [];
  const directories = new Map([['', stableMetadata(rootMetadata)]]);
  const visit = (directory, prefix) => {
    const entries = readdirSync(directory).sort();
    for (const entry of entries) {
      const reference = prefix === '' ? entry : `${prefix}/${entry}`;
      if (!isSafeRelativeReference(reference)) {
        throw new Error(`evidence root contains unsafe path ${reference}`);
      }
      const absolute = resolve(directory, entry);
      const metadata = lstatSync(absolute, { bigint: true });
      if (metadata.isSymbolicLink()) {
        throw new Error(`evidence root contains symlink ${reference}`);
      }
      if (metadata.isDirectory()) {
        directories.set(reference, stableMetadata(metadata));
        visit(absolute, reference);
      } else if (metadata.isFile()) {
        files.push(reference);
      } else {
        throw new Error(
          `evidence root contains unsupported entry ${reference}`,
        );
      }
      if (files.length > MAXIMUM_SUPPORT_FILES) {
        throw new Error('evidence root contains unreferenced files');
      }
    }
  };
  visit(root, '');
  files.sort();
  return { files, directories };
};

const sameInventory = (before, after) =>
  before.files.length === after.files.length &&
  before.files.every((file, index) => file === after.files[index]) &&
  before.directories.size === after.directories.size &&
  [...before.directories].every(([reference, metadata]) => {
    const candidate = after.directories.get(reference);
    return candidate !== undefined && sameMetadata(metadata, candidate);
  });

const resolveEvidenceReference = (root, reference) => {
  if (!isSafeRelativeReference(reference)) {
    throw new Error(`evidence reference ${String(reference)} is unsafe`);
  }
  const absolute = resolve(root, ...reference.split('/'));
  const fromRoot = relative(root, absolute);
  if (
    fromRoot === '' ||
    fromRoot === '..' ||
    fromRoot.startsWith(`..${sep}`) ||
    resolve(root, fromRoot) !== absolute
  ) {
    throw new Error(
      `evidence reference ${reference} escapes the evidence root`,
    );
  }
  return absolute;
};

export const performanceEvidenceReferenceBindings = document => {
  const references = document?.references;
  if (!isObject(references)) return [];
  return [
    {
      reference: references.protocolReference,
      digest: references.protocolSha256,
      bytes: references.protocolBytes,
      label: 'performance protocol',
    },
    {
      reference: references.rawResultsReference,
      digest: references.rawResultsSha256,
      bytes: references.rawResultsBytes,
      label: 'raw performance results',
    },
  ];
};

export const validatePerformanceEvidenceReferenceFiles = (
  document,
  evidenceFiles,
) => {
  const errors = [];
  const bindings = performanceEvidenceReferenceBindings(document);
  if (bindings.length !== 2) {
    return {
      errors: ['performance evidence references are missing'],
      references: [],
    };
  }
  if (
    new Set(bindings.map(binding => binding.reference)).size !== bindings.length
  ) {
    errors.push(
      'performance evidence references must identify two distinct files',
    );
  }
  const verifiedReferences = [];
  for (const binding of bindings) {
    if (
      !isSafeRelativeReference(binding.reference) ||
      !SHA256.test(binding.digest ?? '') ||
      !Number.isSafeInteger(binding.bytes) ||
      binding.bytes < 1
    ) {
      errors.push(`${binding.label} reference binding is invalid`);
      continue;
    }
    verifiedReferences.push(binding.reference);
    const observed =
      evidenceFiles instanceof Map
        ? evidenceFiles.get(binding.reference)
        : undefined;
    if (observed === undefined) {
      errors.push(
        `${binding.label} is missing from the explicit evidence root`,
      );
    } else if (
      observed.sha256 !== binding.digest ||
      observed.bytes !== binding.bytes
    ) {
      errors.push(`${binding.label} digest or size does not match exact bytes`);
    }
  }
  return { errors, references: verifiedReferences };
};

export const verifyPerformanceEvidenceReferences = (document, evidenceRoot) => {
  const errors = [];
  try {
    const root = resolve(evidenceRoot);
    const before = inventoryEvidenceRoot(root);
    const references = document?.references;
    if (!isObject(references)) {
      return { errors: ['references must be an object'] };
    }
    const expected = [
      {
        reference: references.protocolReference,
        digest: references.protocolSha256,
        bytes: references.protocolBytes,
        maximumBytes: MAXIMUM_PROTOCOL_BYTES,
        label: 'performance protocol',
      },
      {
        reference: references.rawResultsReference,
        digest: references.rawResultsSha256,
        bytes: references.rawResultsBytes,
        maximumBytes: MAXIMUM_RAW_RESULTS_BYTES,
        label: 'raw performance results',
      },
    ];
    if (
      new Set(expected.map(item => item.reference)).size !== expected.length
    ) {
      errors.push(
        'performance evidence references must identify two distinct files',
      );
    }
    const expectedReferences = expected
      .map(item => item.reference)
      .filter(isSafeRelativeReference)
      .sort();
    if (
      expectedReferences.length !== expected.length ||
      before.files.length !== expectedReferences.length ||
      before.files.some((file, index) => file !== expectedReferences[index])
    ) {
      errors.push(
        'evidence root must contain exactly the two referenced support files',
      );
    }
    for (const item of expected) {
      if (
        !isSafeRelativeReference(item.reference) ||
        !SHA256.test(item.digest ?? '')
      ) {
        continue;
      }
      const actual = sha256File(
        resolveEvidenceReference(root, item.reference),
        item.maximumBytes,
        item.label,
      );
      if (actual !== item.digest) {
        errors.push(`${item.label} sha256 does not match the referenced bytes`);
      }
      const actualBytes = Number(
        lstatSync(resolveEvidenceReference(root, item.reference), {
          bigint: true,
        }).size,
      );
      if (actualBytes !== item.bytes) {
        errors.push(`${item.label} size does not match the referenced bytes`);
      }
    }
    const after = inventoryEvidenceRoot(root);
    if (!sameInventory(before, after)) {
      errors.push('evidence root changed while references were verified');
    }
  } catch (error) {
    errors.push(
      error instanceof Error
        ? error.message
        : 'performance reference verification failed',
    );
  }
  return { errors };
};

const parseArguments = argv => {
  if (argv.length % 2 !== 0) throw new Error('arguments must be pairs');
  const values = new Map();
  const allowed = new Set([
    'file',
    'platform',
    'source-revision',
    'artifact',
    'evidence-root',
  ]);
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

const readBoundedRegularFile = (path, maximumBytes, label) => {
  let descriptor;
  try {
    descriptor = openSync(
      path,
      // eslint-disable-next-line no-bitwise
      constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0),
    );
    const before = fstatSync(descriptor, { bigint: true });
    if (
      !before.isFile() ||
      before.nlink !== 1n ||
      before.size <= 0n ||
      before.size > BigInt(maximumBytes)
    ) {
      throw new Error(`${label} has an invalid size, link count, or type`);
    }
    const bytes = readFileSync(descriptor);
    const after = fstatSync(descriptor, { bigint: true });
    if (
      BigInt(bytes.byteLength) !== before.size ||
      !sameMetadata(stableMetadata(before), stableMetadata(after))
    ) {
      throw new Error(`${label} changed while it was read`);
    }
    return bytes;
  } finally {
    if (descriptor !== undefined) closeSync(descriptor);
  }
};

const readEvidence = path => {
  const bytes = readBoundedRegularFile(
    path,
    MAXIMUM_EVIDENCE_BYTES,
    'performance evidence',
  );
  return JSON.parse(bytes.toString('utf8'));
};

const isDirectInvocation =
  process.argv[1] !== undefined &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href;

if (isDirectInvocation) {
  try {
    const values = parseArguments(process.argv.slice(2));
    const evidence = readEvidence(values.get('file'));
    const result = validatePerformanceEvidence(evidence, {
      expectedPlatform: values.get('platform'),
      expectedSourceRevision: values.get('source-revision'),
      expectedArtifactSha256: sha256File(values.get('artifact')),
    });
    result.errors.push(
      ...verifyPerformanceEvidenceReferences(
        evidence,
        values.get('evidence-root'),
      ).errors,
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
