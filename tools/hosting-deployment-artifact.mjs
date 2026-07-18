#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import {
  closeSync,
  constants,
  fstatSync,
  lstatSync,
  mkdirSync,
  openSync,
  readFileSync,
  readdirSync,
  realpathSync,
  writeFileSync,
} from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { parseReleaseConfig } from '../backend/hosting/tools/release-config.mjs';
import {
  HOSTING_DEPLOYMENT_SOURCE_PATHS,
  digestSelectedSourcePaths,
} from './validate-cloud-release-evidence.mjs';

const ROOT = fileURLToPath(new URL('../', import.meta.url));
const REVISION = /^[0-9a-f]{40}$/u;
const SHA256 = /^[0-9a-f]{64}$/u;
const PROJECT_ID = /^[a-z][a-z0-9-]{4,28}[a-z0-9]$/u;
const SITE_ID = /^[a-z0-9][a-z0-9-]{4,62}$/u;
const SAFE_PATH = /^[A-Za-z0-9][A-Za-z0-9._+@()/-]{0,511}$/u;
const MAX_FILE_BYTES = 64 * 1024 * 1024;
const MAX_ARTIFACT_BYTES = 256 * 1024 * 1024;
const MAX_FILES = 10_000;
const CLI_VERSION = '15.23.0';

export const stableJson = value => {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(',')}]`;
  if (value !== null && typeof value === 'object') {
    return `{${Object.keys(value)
      .sort()
      .map(key => `${JSON.stringify(key)}:${stableJson(value[key])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value);
};

export const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');

const exactKeys = (value, expected, label) => {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${label} must be an object`);
  }
  const keys = Object.keys(value);
  if (
    keys.length !== expected.length ||
    keys.some(key => !expected.includes(key))
  ) {
    throw new Error(`${label} fields do not match the exact contract`);
  }
};

const hasStableMetadata = (left, right) =>
  ['dev', 'ino', 'mode', 'nlink', 'size', 'mtimeNs', 'ctimeNs'].every(
    key => left[key] === right[key],
  );

export const readStableRegularFile = (
  file,
  maximumBytes = MAX_FILE_BYTES,
  label = 'file',
) => {
  const requested = path.resolve(file);
  const before = lstatSync(requested, { bigint: true });
  if (
    before.isSymbolicLink() ||
    !before.isFile() ||
    before.nlink !== 1n ||
    before.size <= 0n ||
    before.size > BigInt(maximumBytes)
  ) {
    throw new Error(`${label} must be a bounded, non-linked regular file`);
  }
  const descriptor = openSync(
    requested,
    // File-descriptor flags intentionally form a bit mask.
    // eslint-disable-next-line no-bitwise
    constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0),
  );
  try {
    const opened = fstatSync(descriptor, { bigint: true });
    if (!hasStableMetadata(before, opened)) {
      throw new Error(`${label} changed before it was read`);
    }
    const bytes = readFileSync(descriptor);
    const after = fstatSync(descriptor, { bigint: true });
    const pathAfter = lstatSync(requested, { bigint: true });
    if (
      !hasStableMetadata(opened, after) ||
      !hasStableMetadata(opened, pathAfter) ||
      BigInt(bytes.byteLength) !== opened.size
    ) {
      throw new Error(`${label} changed while it was read`);
    }
    return bytes;
  } finally {
    closeSync(descriptor);
  }
};

const safeRelativePath = value =>
  typeof value === 'string' &&
  SAFE_PATH.test(value) &&
  !path.posix.isAbsolute(value) &&
  value.split('/').every(part => part !== '' && part !== '.' && part !== '..');

const collectPublicFiles = publicRoot => {
  const root = realpathSync(publicRoot);
  const records = [];
  let totalBytes = 0;
  const walk = directory => {
    for (const name of readdirSync(directory).sort()) {
      const absolute = path.join(directory, name);
      const metadata = lstatSync(absolute, { bigint: true });
      const relative = path.relative(root, absolute).split(path.sep).join('/');
      if (metadata.isSymbolicLink()) {
        throw new Error(
          `Hosting public output contains a symlink: ${relative}`,
        );
      }
      if (metadata.isDirectory()) {
        walk(absolute);
        continue;
      }
      if (!metadata.isFile() || metadata.nlink !== 1n) {
        throw new Error(
          `Hosting public output contains an unsupported or linked entry: ${relative}`,
        );
      }
      if (records.length >= MAX_FILES) {
        throw new Error('Hosting public output contains too many files');
      }
      const bytes = readStableRegularFile(
        absolute,
        MAX_FILE_BYTES,
        `Hosting public file ${relative}`,
      );
      totalBytes += bytes.byteLength;
      if (totalBytes > MAX_ARTIFACT_BYTES) {
        throw new Error('Hosting public output exceeds the safety bound');
      }
      records.push({
        path: `hosting/public/${relative}`,
        bytes: bytes.byteLength,
        sha256: sha256(bytes),
        contentBase64: bytes.toString('base64'),
      });
    }
  };
  walk(root);
  if (records.length === 0) {
    throw new Error('Hosting public output is empty');
  }
  return records;
};

const normalizeDeploymentConfig = (firebaseConfig, siteId) => {
  exactKeys(
    firebaseConfig.hosting,
    ['public', 'predeploy', 'ignore', 'cleanUrls', 'trailingSlash', 'headers'],
    'source firebase.json hosting configuration',
  );
  if (firebaseConfig.hosting.public !== 'hosting/public') {
    throw new Error('source firebase.json Hosting public path is unexpected');
  }
  return {
    hosting: {
      ...firebaseConfig.hosting,
      predeploy: [],
      site: siteId,
    },
  };
};

const manifestProjection = artifact => ({
  schemaVersion: artifact.schemaVersion,
  product: artifact.product,
  sourceRevision: artifact.sourceRevision,
  projectId: artifact.projectId,
  siteId: artifact.siteId,
  publicBaseUrl: artifact.publicBaseUrl,
  firebaseCliVersion: artifact.firebaseCliVersion,
  hostingSourceTreeSha256: artifact.hostingSourceTreeSha256,
  sourceFirebaseConfigSha256: artifact.sourceFirebaseConfigSha256,
  releaseConfigSha256: artifact.releaseConfigSha256,
  deploymentConfigSha256: artifact.deploymentConfigSha256,
  publicTreeSha256: artifact.publicTreeSha256,
  files: artifact.files.map(({ path: filePath, bytes, sha256: digest }) => ({
    path: filePath,
    bytes,
    sha256: digest,
  })),
});

const publicTreeDigest = files =>
  sha256(
    Buffer.from(
      files
        .map(file => `file\0${file.path}\0${file.bytes}\0${file.sha256}`)
        .sort()
        .join('\n'),
      'utf8',
    ),
  );

export function createHostingDeploymentArtifact({
  sourceRevision,
  projectId,
  siteId,
  hostingSourceTreeSha256,
  firebaseConfigBytes,
  releaseConfigBytes,
  publicFiles,
}) {
  if (!REVISION.test(sourceRevision))
    throw new Error('invalid source revision');
  if (!PROJECT_ID.test(projectId))
    throw new Error('invalid Firebase project ID');
  if (!SITE_ID.test(siteId))
    throw new Error('invalid Firebase Hosting site ID');
  if (!SHA256.test(hostingSourceTreeSha256)) {
    throw new Error('invalid Hosting source-tree digest');
  }
  let firebaseConfig;
  let rawReleaseConfig;
  try {
    firebaseConfig = JSON.parse(firebaseConfigBytes.toString('utf8'));
    rawReleaseConfig = JSON.parse(releaseConfigBytes.toString('utf8'));
  } catch {
    throw new Error('Hosting configuration input is not valid JSON');
  }
  const releaseConfig = parseReleaseConfig(rawReleaseConfig);
  const deploymentConfigBytes = Buffer.from(
    `${stableJson(normalizeDeploymentConfig(firebaseConfig, siteId))}\n`,
    'utf8',
  );
  const normalizedFiles = publicFiles
    .map(file => ({ ...file }))
    .sort((left, right) =>
      left.path < right.path ? -1 : left.path > right.path ? 1 : 0,
    );
  const runtimeFile = normalizedFiles.find(
    file => file.path === 'hosting/public/runtime-config.json',
  );
  const expectedRuntimeBytes = Buffer.from(
    `${JSON.stringify(releaseConfig)}\n`,
    'utf8',
  );
  if (
    runtimeFile === undefined ||
    runtimeFile.contentBase64 !== expectedRuntimeBytes.toString('base64')
  ) {
    throw new Error(
      'deployed runtime-config.json does not derive from the approved release config',
    );
  }
  const artifactWithoutManifestDigest = {
    schemaVersion: 1,
    product: 'birthday-autopilot-hosting-deployment-artifact',
    sourceRevision,
    projectId,
    siteId,
    publicBaseUrl: releaseConfig.publicBaseUrl,
    firebaseCliVersion: CLI_VERSION,
    hostingSourceTreeSha256,
    sourceFirebaseConfigSha256: sha256(firebaseConfigBytes),
    sourceFirebaseConfigBase64: firebaseConfigBytes.toString('base64'),
    releaseConfigSha256: sha256(releaseConfigBytes),
    deploymentConfigSha256: sha256(deploymentConfigBytes),
    deploymentConfigBase64: deploymentConfigBytes.toString('base64'),
    publicTreeSha256: publicTreeDigest(normalizedFiles),
    files: normalizedFiles,
  };
  return {
    ...artifactWithoutManifestDigest,
    manifestSha256: sha256(
      Buffer.from(
        stableJson(manifestProjection(artifactWithoutManifestDigest)),
      ),
    ),
  };
}

export function verifyHostingDeploymentArtifact(artifact) {
  exactKeys(
    artifact,
    [
      'schemaVersion',
      'product',
      'sourceRevision',
      'projectId',
      'siteId',
      'publicBaseUrl',
      'firebaseCliVersion',
      'hostingSourceTreeSha256',
      'sourceFirebaseConfigSha256',
      'sourceFirebaseConfigBase64',
      'releaseConfigSha256',
      'deploymentConfigSha256',
      'deploymentConfigBase64',
      'publicTreeSha256',
      'files',
      'manifestSha256',
    ],
    'Hosting deployment artifact',
  );
  if (
    artifact.schemaVersion !== 1 ||
    artifact.product !== 'birthday-autopilot-hosting-deployment-artifact' ||
    !REVISION.test(artifact.sourceRevision ?? '') ||
    !PROJECT_ID.test(artifact.projectId ?? '') ||
    !SITE_ID.test(artifact.siteId ?? '') ||
    artifact.firebaseCliVersion !== CLI_VERSION
  ) {
    throw new Error('Hosting deployment artifact identity is invalid');
  }
  let publicUrl;
  try {
    publicUrl = new URL(artifact.publicBaseUrl);
  } catch {
    throw new Error('Hosting deployment artifact origin is invalid');
  }
  if (
    publicUrl.protocol !== 'https:' ||
    publicUrl.pathname !== '/' ||
    publicUrl.search !== '' ||
    publicUrl.hash !== ''
  ) {
    throw new Error('Hosting deployment artifact origin is invalid');
  }
  for (const key of [
    'hostingSourceTreeSha256',
    'sourceFirebaseConfigSha256',
    'releaseConfigSha256',
    'deploymentConfigSha256',
    'publicTreeSha256',
    'manifestSha256',
  ]) {
    if (!SHA256.test(artifact[key] ?? '')) {
      throw new Error(`Hosting deployment artifact ${key} is invalid`);
    }
  }
  if (
    !Array.isArray(artifact.files) ||
    artifact.files.length === 0 ||
    artifact.files.length > MAX_FILES
  ) {
    throw new Error('Hosting deployment artifact file inventory is invalid');
  }
  const paths = new Set();
  let totalBytes = 0;
  for (const file of artifact.files) {
    exactKeys(
      file,
      ['path', 'bytes', 'sha256', 'contentBase64'],
      'public file',
    );
    if (
      !safeRelativePath(file.path) ||
      !file.path.startsWith('hosting/public/') ||
      paths.has(file.path) ||
      !Number.isSafeInteger(file.bytes) ||
      file.bytes <= 0 ||
      !SHA256.test(file.sha256 ?? '') ||
      typeof file.contentBase64 !== 'string'
    ) {
      throw new Error('Hosting deployment artifact file record is invalid');
    }
    const bytes = Buffer.from(file.contentBase64, 'base64');
    if (
      bytes.byteLength !== file.bytes ||
      bytes.toString('base64') !== file.contentBase64 ||
      sha256(bytes) !== file.sha256
    ) {
      throw new Error(
        `Hosting deployment artifact file bytes differ: ${file.path}`,
      );
    }
    paths.add(file.path);
    totalBytes += file.bytes;
    if (totalBytes > MAX_ARTIFACT_BYTES) {
      throw new Error('Hosting deployment artifact exceeds the safety bound');
    }
  }
  if (
    artifact.files.some(
      (file, index) => index > 0 && artifact.files[index - 1].path >= file.path,
    )
  ) {
    throw new Error(
      'Hosting deployment artifact file inventory is not canonical',
    );
  }
  const deploymentConfigBytes = Buffer.from(
    artifact.deploymentConfigBase64,
    'base64',
  );
  const sourceFirebaseConfigBytes = Buffer.from(
    artifact.sourceFirebaseConfigBase64,
    'base64',
  );
  if (
    deploymentConfigBytes.toString('base64') !==
      artifact.deploymentConfigBase64 ||
    sha256(deploymentConfigBytes) !== artifact.deploymentConfigSha256
  ) {
    throw new Error('Hosting deployment configuration bytes differ');
  }
  if (
    sourceFirebaseConfigBytes.toString('base64') !==
      artifact.sourceFirebaseConfigBase64 ||
    sha256(sourceFirebaseConfigBytes) !== artifact.sourceFirebaseConfigSha256
  ) {
    throw new Error('source firebase.json bytes differ');
  }
  const config = JSON.parse(deploymentConfigBytes.toString('utf8'));
  const sourceFirebaseConfig = JSON.parse(
    sourceFirebaseConfigBytes.toString('utf8'),
  );
  if (
    config.hosting?.site !== artifact.siteId ||
    config.hosting?.public !== 'hosting/public' ||
    !Array.isArray(config.hosting?.predeploy) ||
    config.hosting.predeploy.length !== 0
  ) {
    throw new Error('Hosting deployment configuration target is invalid');
  }
  const expectedDeploymentConfig = Buffer.from(
    `${stableJson(
      normalizeDeploymentConfig(sourceFirebaseConfig, artifact.siteId),
    )}\n`,
    'utf8',
  );
  if (!deploymentConfigBytes.equals(expectedDeploymentConfig)) {
    throw new Error(
      'Hosting deployment configuration does not derive from source firebase.json',
    );
  }
  if (publicTreeDigest(artifact.files) !== artifact.publicTreeSha256) {
    throw new Error('Hosting deployment public-tree digest differs');
  }
  if (
    sha256(Buffer.from(stableJson(manifestProjection(artifact)))) !==
    artifact.manifestSha256
  ) {
    throw new Error('Hosting deployment manifest digest differs');
  }
  return artifact;
}

export function createHostingDeploymentManifest(artifactBytes, artifact) {
  verifyHostingDeploymentArtifact(artifact);
  const projection = manifestProjection(artifact);
  return {
    schemaVersion: 1,
    product: 'birthday-autopilot-hosting-deployment-manifest',
    artifactSchemaVersion: projection.schemaVersion,
    artifactProduct: projection.product,
    artifactFileName: 'hosting-deployment-artifact.json',
    artifactSha256: sha256(artifactBytes),
    artifactBytes: artifactBytes.byteLength,
    manifestSha256: artifact.manifestSha256,
    sourceRevision: projection.sourceRevision,
    projectId: projection.projectId,
    siteId: projection.siteId,
    publicBaseUrl: projection.publicBaseUrl,
    firebaseCliVersion: projection.firebaseCliVersion,
    hostingSourceTreeSha256: projection.hostingSourceTreeSha256,
    sourceFirebaseConfigSha256: projection.sourceFirebaseConfigSha256,
    releaseConfigSha256: projection.releaseConfigSha256,
    deploymentConfigSha256: projection.deploymentConfigSha256,
    publicTreeSha256: projection.publicTreeSha256,
    files: projection.files,
  };
}

const parseJson = (bytes, label) => {
  try {
    return JSON.parse(bytes.toString('utf8'));
  } catch {
    throw new Error(`${label} is not valid JSON`);
  }
};

const writeExclusive = (file, bytes) =>
  writeFileSync(file, bytes, { flag: 'wx', mode: 0o600 });

const command = (binary, args, cwd) =>
  execFileSync(binary, args, {
    cwd,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim();

const build = args => {
  const sourceRoot = realpathSync(path.resolve(args.get('source-root')));
  const repositoryRoot = realpathSync(
    command('git', ['rev-parse', '--show-toplevel'], sourceRoot),
  );
  if (sourceRoot !== repositoryRoot) {
    throw new Error('source root must be the repository root');
  }
  const sourceRevision = command(
    'git',
    ['rev-parse', '--verify', 'HEAD'],
    sourceRoot,
  );
  if (
    process.env.GITHUB_SHA !== undefined &&
    process.env.GITHUB_SHA !== sourceRevision
  ) {
    throw new Error('GitHub revision does not match checked-out HEAD');
  }
  if (
    command(
      'git',
      ['status', '--porcelain=v1', '--untracked-files=all'],
      sourceRoot,
    ) !== ''
  ) {
    throw new Error(
      'Hosting deployment artifact requires a clean source checkout',
    );
  }
  const artifact = createHostingDeploymentArtifact({
    sourceRevision,
    projectId: args.get('project-id'),
    siteId: args.get('site-id'),
    hostingSourceTreeSha256: digestSelectedSourcePaths(
      sourceRoot,
      HOSTING_DEPLOYMENT_SOURCE_PATHS,
    ),
    firebaseConfigBytes: readStableRegularFile(
      args.get('firebase-config'),
      MAX_FILE_BYTES,
      'source firebase.json',
    ),
    releaseConfigBytes: readStableRegularFile(
      args.get('release-config'),
      MAX_FILE_BYTES,
      'approved Hosting release config',
    ),
    publicFiles: collectPublicFiles(args.get('public-root')),
  });
  const artifactBytes = Buffer.from(`${stableJson(artifact)}\n`, 'utf8');
  if (artifactBytes.byteLength > MAX_ARTIFACT_BYTES) {
    throw new Error('Hosting deployment artifact exceeds the safety bound');
  }
  const manifest = createHostingDeploymentManifest(artifactBytes, artifact);
  writeExclusive(args.get('output'), artifactBytes);
  writeExclusive(
    args.get('manifest-output'),
    Buffer.from(`${stableJson(manifest)}\n`, 'utf8'),
  );
};

const extract = args => {
  const artifactBytes = readStableRegularFile(
    args.get('artifact'),
    MAX_ARTIFACT_BYTES,
    'Hosting deployment artifact',
  );
  const artifact = verifyHostingDeploymentArtifact(
    parseJson(artifactBytes, 'Hosting deployment artifact'),
  );
  const root = path.resolve(args.get('output-root'));
  mkdirSync(root, { mode: 0o700 });
  writeExclusive(
    path.join(root, 'firebase.json'),
    Buffer.from(artifact.deploymentConfigBase64, 'base64'),
  );
  for (const file of artifact.files) {
    const destination = path.join(root, ...file.path.split('/'));
    if (!destination.startsWith(`${root}${path.sep}`)) {
      throw new Error(
        'Hosting deployment artifact path escapes extraction root',
      );
    }
    mkdirSync(path.dirname(destination), { recursive: true, mode: 0o700 });
    writeFileSync(destination, Buffer.from(file.contentBase64, 'base64'), {
      flag: 'wx',
      mode: 0o644,
    });
  }
  process.stdout.write(
    `PASS extracted Hosting artifact sha256=${sha256(artifactBytes)} source=${
      artifact.sourceRevision
    }\n`,
  );
};

const parseArgs = argv => {
  if (argv.length < 2 || argv.length % 2 !== 0) {
    throw new Error('arguments must be --name value pairs');
  }
  const values = new Map();
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    if (!flag?.startsWith('--') || values.has(flag.slice(2))) {
      throw new Error(`unsupported or duplicate argument ${flag}`);
    }
    values.set(flag.slice(2), argv[index + 1]);
  }
  const mode = values.get('mode');
  const required =
    mode === 'build'
      ? [
          'mode',
          'source-root',
          'public-root',
          'firebase-config',
          'release-config',
          'project-id',
          'site-id',
          'output',
          'manifest-output',
        ]
      : mode === 'extract'
      ? ['mode', 'artifact', 'output-root']
      : [];
  if (
    required.length === 0 ||
    values.size !== required.length ||
    required.some(key => !values.has(key))
  ) {
    throw new Error(
      'arguments do not match the selected Hosting artifact mode',
    );
  }
  return values;
};

if (
  process.argv[1] !== undefined &&
  realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url))
) {
  try {
    const args = parseArgs(process.argv.slice(2));
    if (args.get('mode') === 'build') build(args);
    else extract(args);
  } catch (error) {
    process.stderr.write(
      `FAIL ${error instanceof Error ? error.message : String(error)}\n`,
    );
    process.exitCode = 1;
  }
}

export const PROJECT_ROOT = ROOT;
