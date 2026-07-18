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
  realpathSync,
  writeFileSync,
} from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const SOURCE_REVISION = /^[0-9a-f]{40}$/u;
const PROJECT_ID = /^[a-z][a-z0-9-]{4,28}[a-z0-9]$/u;
const PROJECT_NUMBER = /^[1-9][0-9]{5,19}$/u;
const FIREBASE_APP_ID =
  /^1:[1-9][0-9]{5,19}:(?:android|ios|web):[0-9a-f]{8,64}$/u;
const SITE_ID = /^[a-z0-9][a-z0-9-]{4,62}$/u;
const SERVICE_ACCOUNT =
  /^[a-z][a-z0-9-]{2,62}@[a-z][a-z0-9-]{4,28}[a-z0-9]\.iam\.gserviceaccount\.com$/u;
const WIF_PROVIDER =
  /^projects\/[1-9][0-9]{5,19}\/locations\/global\/workloadIdentityPools\/[a-z0-9-]{4,32}\/providers\/[a-z0-9-]{4,32}$/u;
const BUCKET_NAME = /^[a-z0-9][a-z0-9-]{4,61}[a-z0-9]$/u;
const INSTANT =
  /^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?Z$/u;
const RUN_ID = /^[1-9][0-9]{0,19}$/u;
const REPOSITORY = /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/u;
const SHA256 = /^[0-9a-f]{64}$/u;
const MAXIMUM_RAW_FILE_BYTES = 64 * 1024 * 1024;
const MAXIMUM_MANIFEST_BYTES = 4 * 1024 * 1024;
const MAXIMUM_ARCHIVE_BYTES = 512 * 1024 * 1024;
const TAR_BLOCK_BYTES = 512;
const REQUIRED_RAW_FILES = Object.freeze([
  'admission-bucket-access-analysis.json',
  'admission-bucket-iam.json',
  'admission-bucket.json',
  'admission-reader-service-account-iam.json',
  'admission-reader-impersonation-access-analysis.json',
  'admission-reader-service-account.json',
  'admission-reader-user-managed-keys.json',
  'admission-reader-wif-provider.json',
  'admission-reader-wif-pool.json',
  'admission-reader-wif-providers.json',
  'audit-service-account.json',
  'application-project-buckets.json',
  'application-project-ancestors.json',
  'application-cross-project-sa-org-policy.json',
  'application-resource-assets.json',
  'application-project-iam.json',
  'firebase-apps.json',
  'firebase-project.json',
  'firebase-hosting-sites.json',
  'github-environment-audit-log.json',
  'github-environment-cloud-production-readonly-audit-branch-policies.json',
  'github-environment-cloud-production-readonly-audit.json',
  'github-environment-hosting-production-admission-branch-policies.json',
  'github-environment-hosting-production-admission.json',
  'github-environment-hosting-production-build-branch-policies.json',
  'github-environment-hosting-production-build.json',
  'github-environment-hosting-production-deploy-branch-policies.json',
  'github-environment-hosting-production-deploy.json',
  'github-environment-hosting-production-readonly-live-branch-policies.json',
  'github-environment-hosting-production-readonly-live.json',
  'github-main-branch-protection.json',
  'github-release-source-check-runs.json',
  'github-release-source-ci-runs.json',
  'github-repository.json',
  'hosting-deploy-service-account-iam.json',
  'hosting-deploy-impersonation-access-analysis.json',
  'hosting-deploy-service-account.json',
  'hosting-deploy-user-managed-keys.json',
  'hosting-deploy-wif-provider.json',
  'hosting-deploy-wif-pool.json',
  'hosting-deploy-wif-providers.json',
  'hosting-mutation-access-analysis.json',
  'hosting-observer-service-account-iam.json',
  'hosting-observer-impersonation-access-analysis.json',
  'hosting-observer-service-account.json',
  'hosting-observer-user-managed-keys.json',
  'hosting-observer-wif-provider.json',
  'hosting-observer-wif-pool.json',
  'hosting-observer-wif-providers.json',
  'project.json',
  'release-security-buckets.json',
  'release-security-project-ancestors.json',
  'release-security-cross-project-sa-org-policy.json',
  'release-security-resource-assets.json',
  'release-security-ancestor-iam-policies.json',
  'release-security-log-bucket.json',
  'release-security-logging-sinks.json',
  'release-security-project-iam.json',
  'release-security-project.json',
  'runtime-service-account.json',
  'workflow-context.json',
]);
const CLI_KEYS = new Set(['raw-root', 'manifest', 'archive', 'output']);
const ADMISSION_PERMISSION_UNIVERSE = new Set([
  'storage.buckets.get',
  'storage.objects.create',
  'storage.objects.get',
  'storage.objects.list',
]);
const HOSTING_MUTATION_PERMISSIONS = Object.freeze([
  'firebasehosting.sites.create',
  'firebasehosting.sites.delete',
  'firebasehosting.sites.update',
]);
const RELEASE_ADMISSION_CHECK_NAME = 'Release admission for exact source SHA';
const RELEASE_ADMISSION_WORKFLOW_PATH = '.github/workflows/ci.yml';

const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const stableJson = value => {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(',')}]`;
  if (value !== null && typeof value === 'object') {
    return `{${Object.keys(value)
      .sort()
      .map(key => `${JSON.stringify(key)}:${stableJson(value[key])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value);
};

const readStableFile = (file, maximumBytes, label) => {
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
    if (
      opened.dev !== before.dev ||
      opened.ino !== before.ino ||
      opened.size !== before.size
    ) {
      throw new Error(`${label} changed before it was read`);
    }
    const bytes = readFileSync(descriptor);
    const after = fstatSync(descriptor, { bigint: true });
    const pathAfter = lstatSync(requested, { bigint: true });
    if (
      BigInt(bytes.byteLength) !== opened.size ||
      after.dev !== opened.dev ||
      after.ino !== opened.ino ||
      after.size !== opened.size ||
      after.mtimeNs !== opened.mtimeNs ||
      pathAfter.dev !== opened.dev ||
      pathAfter.ino !== opened.ino ||
      pathAfter.size !== opened.size
    ) {
      throw new Error(`${label} changed while it was read`);
    }
    return bytes;
  } finally {
    closeSync(descriptor);
  }
};

const parseJson = (bytes, label) => {
  try {
    return JSON.parse(bytes.toString('utf8'));
  } catch {
    throw new Error(`${label} is not valid JSON`);
  }
};

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

const walkObjects = (value, result = []) => {
  if (value === null || typeof value !== 'object') return result;
  if (!Array.isArray(value)) result.push(value);
  for (const child of Array.isArray(value) ? value : Object.values(value)) {
    walkObjects(child, result);
  }
  return result;
};

const rawFiles = root => {
  const records = new Map();
  const visit = (directory, prefix = '') => {
    for (const name of readdirSync(directory).sort()) {
      const absolute = path.join(directory, name);
      const relative = prefix === '' ? name : `${prefix}/${name}`;
      const metadata = lstatSync(absolute);
      if (metadata.isSymbolicLink()) {
        throw new Error(
          `raw observation contains a symbolic link: ${relative}`,
        );
      }
      if (metadata.isDirectory()) visit(absolute, relative);
      else if (metadata.isFile()) {
        const bytes = readStableFile(
          absolute,
          MAXIMUM_RAW_FILE_BYTES,
          `raw observation ${relative}`,
        );
        records.set(relative, {
          bytes,
          byteCount: bytes.byteLength,
          sha256: sha256(bytes),
        });
      } else {
        throw new Error(`raw observation entry is unsupported: ${relative}`);
      }
    }
  };
  visit(root);
  return records;
};

const tarText = (header, offset, length, label) => {
  const field = header.subarray(offset, offset + length);
  const terminator = field.indexOf(0);
  const content = terminator === -1 ? field : field.subarray(0, terminator);
  const padding =
    terminator === -1 ? Buffer.alloc(0) : field.subarray(terminator);
  if (
    [...content].some(byte => byte < 0x20 || byte > 0x7e) ||
    [...padding].some(byte => byte !== 0 && byte !== 0x20)
  ) {
    throw new Error(`cloud read-only archive ${label} is not canonical ASCII`);
  }
  return content.toString('ascii');
};

const tarOctal = (header, offset, length, label) => {
  const value = tarText(header, offset, length, label).trim();
  if (!/^[0-7]+$/u.test(value)) {
    throw new Error(`cloud read-only archive ${label} is not canonical octal`);
  }
  const parsed = Number.parseInt(value, 8);
  if (!Number.isSafeInteger(parsed) || parsed < 0) {
    throw new Error(`cloud read-only archive ${label} is out of range`);
  }
  return parsed;
};

const safeTarPath = (value, directory) => {
  if (
    typeof value !== 'string' ||
    value === '' ||
    value.includes('\\') ||
    path.posix.isAbsolute(value) ||
    directory !== value.endsWith('/')
  ) {
    return false;
  }
  const components = (directory ? value.slice(0, -1) : value).split('/');
  return components.every(
    component => component !== '' && component !== '.' && component !== '..',
  );
};

const parseCloudReadonlyArchive = archiveBytes => {
  if (
    !Buffer.isBuffer(archiveBytes) ||
    archiveBytes.byteLength < TAR_BLOCK_BYTES * 2 ||
    archiveBytes.byteLength % TAR_BLOCK_BYTES !== 0 ||
    archiveBytes.byteLength > MAXIMUM_ARCHIVE_BYTES
  ) {
    throw new Error(
      'cloud read-only archive is not a bounded block-aligned tar',
    );
  }
  const entries = new Map();
  let offset = 0;
  let zeroBlocks = 0;
  while (offset < archiveBytes.byteLength) {
    const header = archiveBytes.subarray(offset, offset + TAR_BLOCK_BYTES);
    if (header.every(byte => byte === 0)) {
      zeroBlocks += 1;
      offset += TAR_BLOCK_BYTES;
      continue;
    }
    if (zeroBlocks > 0) {
      throw new Error('cloud read-only archive has data after its trailer');
    }
    if (
      !header.subarray(257, 263).equals(Buffer.from('ustar\0', 'ascii')) ||
      !header.subarray(263, 265).equals(Buffer.from('00', 'ascii'))
    ) {
      throw new Error('cloud read-only archive is not strict POSIX ustar');
    }
    const storedChecksum = tarOctal(header, 148, 8, 'checksum');
    const checksumHeader = Buffer.from(header);
    checksumHeader.fill(0x20, 148, 156);
    const calculatedChecksum = checksumHeader.reduce(
      (sum, byte) => sum + byte,
      0,
    );
    if (storedChecksum !== calculatedChecksum) {
      throw new Error('cloud read-only archive header checksum is invalid');
    }
    const name = tarText(header, 0, 100, 'name');
    const prefix = tarText(header, 345, 155, 'prefix');
    const archivePath = prefix === '' ? name : `${prefix}/${name}`;
    const typeByte = header[156];
    const directory = typeByte === 0x35;
    const regular = typeByte === 0 || typeByte === 0x30;
    const size = tarOctal(header, 124, 12, 'size');
    const uid = tarOctal(header, 108, 8, 'uid');
    const gid = tarOctal(header, 116, 8, 'gid');
    const modifiedAt = tarOctal(header, 136, 12, 'mtime');
    if (
      (!regular && !directory) ||
      !safeTarPath(archivePath, directory) ||
      entries.has(archivePath) ||
      tarText(header, 157, 100, 'link name') !== '' ||
      uid !== 0 ||
      gid !== 0 ||
      modifiedAt !== 0 ||
      (directory && size !== 0)
    ) {
      throw new Error(
        'cloud read-only archive contains an unsafe, duplicate, linked, or non-reproducible entry',
      );
    }
    const contentOffset = offset + TAR_BLOCK_BYTES;
    const contentEnd = contentOffset + size;
    const paddedEnd =
      contentOffset + Math.ceil(size / TAR_BLOCK_BYTES) * TAR_BLOCK_BYTES;
    if (
      contentEnd > archiveBytes.byteLength ||
      paddedEnd > archiveBytes.byteLength
    ) {
      throw new Error('cloud read-only archive entry is truncated');
    }
    const content = archiveBytes.subarray(contentOffset, contentEnd);
    if (archiveBytes.subarray(contentEnd, paddedEnd).some(byte => byte !== 0)) {
      throw new Error('cloud read-only archive entry padding is not zeroed');
    }
    entries.set(archivePath, {
      kind: directory ? 'directory' : 'file',
      bytes: size,
      sha256: directory ? null : sha256(content),
      content,
    });
    offset = paddedEnd;
  }
  if (zeroBlocks < 2) {
    throw new Error(
      'cloud read-only archive is missing its zero-block trailer',
    );
  }
  return entries;
};

export function verifyCloudReadonlyArchive({
  archiveBytes,
  manifestBytes,
  actualRawFiles,
}) {
  const manifest = parseJson(manifestBytes, 'evidence manifest');
  if (
    manifest.schemaVersion !== 3 ||
    manifest.base !== 'cloud-production-readonly' ||
    !Array.isArray(manifest.entries)
  ) {
    throw new Error('cloud read-only archive manifest identity is invalid');
  }
  const manifestFiles = manifest.entries.filter(
    entry => entry.kind === 'file' && entry.path.includes('/raw/'),
  );
  const expectedFiles = new Map([
    [
      'evidence-manifest.json',
      { bytes: manifestBytes.byteLength, sha256: sha256(manifestBytes) },
    ],
  ]);
  const expectedDirectories = new Set(['raw/']);
  for (const entry of manifestFiles) {
    const marker = '/raw/';
    const markerIndex = entry.path.indexOf(marker);
    const relative = entry.path.slice(markerIndex + marker.length);
    if (
      markerIndex < 0 ||
      !safeTarPath(relative, false) ||
      !Number.isSafeInteger(entry.bytes) ||
      entry.bytes <= 0 ||
      !SHA256.test(entry.sha256 ?? '')
    ) {
      throw new Error('cloud read-only archive manifest file is invalid');
    }
    const archivePath = `raw/${relative}`;
    if (expectedFiles.has(archivePath)) {
      throw new Error(
        'cloud read-only archive manifest has duplicate raw files',
      );
    }
    expectedFiles.set(archivePath, {
      bytes: entry.bytes,
      sha256: entry.sha256,
    });
    const components = archivePath.split('/');
    for (let index = 1; index < components.length; index += 1) {
      expectedDirectories.add(`${components.slice(0, index).join('/')}/`);
    }
  }
  if (manifestFiles.length === 0) {
    throw new Error('cloud read-only archive manifest has no raw files');
  }
  const archiveEntries = parseCloudReadonlyArchive(archiveBytes);
  const expectedEntryCount = expectedFiles.size + expectedDirectories.size;
  if (archiveEntries.size !== expectedEntryCount) {
    throw new Error('cloud read-only archive inventory is not exact');
  }
  for (const directory of expectedDirectories) {
    if (archiveEntries.get(directory)?.kind !== 'directory') {
      throw new Error(
        `cloud read-only archive is missing directory ${directory}`,
      );
    }
  }
  for (const [archivePath, expected] of expectedFiles) {
    const observed = archiveEntries.get(archivePath);
    if (
      observed?.kind !== 'file' ||
      observed.bytes !== expected.bytes ||
      observed.sha256 !== expected.sha256 ||
      (archivePath === 'evidence-manifest.json' &&
        !observed.content.equals(manifestBytes))
    ) {
      throw new Error(`cloud read-only archive does not bind ${archivePath}`);
    }
  }
  if (actualRawFiles !== undefined) {
    if (
      actualRawFiles.size !== manifestFiles.length ||
      [...actualRawFiles].some(([relative, actual]) => {
        const observed = archiveEntries.get(`raw/${relative}`);
        return (
          observed?.kind !== 'file' ||
          observed.bytes !== actual.byteCount ||
          observed.sha256 !== actual.sha256 ||
          !observed.content.equals(actual.bytes)
        );
      })
    ) {
      throw new Error(
        'cloud read-only archive differs from the observed raw root',
      );
    }
  }
  return { manifest, manifestFiles, archiveEntries };
}

const requireMatch = (value, pattern, label) => {
  if (typeof value !== 'string' || !pattern.test(value)) {
    throw new Error(`${label} is invalid`);
  }
  return value;
};

const requireEmptyArray = (value, label) => {
  if (!Array.isArray(value) || value.length !== 0) {
    throw new Error(`${label} must be an authoritative empty array`);
  }
};

const parseIamAnalysisScope = ({ bytes, projectId, projectNumber, label }) => {
  const ancestors = parseJson(bytes, `${label} ancestors`);
  if (!Array.isArray(ancestors) || ancestors.length < 1) {
    throw new Error(`${label} ancestors must be a non-empty array`);
  }
  const projects = ancestors.filter(ancestor => ancestor?.type === 'project');
  const organizations = ancestors.filter(
    ancestor => ancestor?.type === 'organization',
  );
  const folders = ancestors.filter(ancestor => ancestor?.type === 'folder');
  if (
    projects.length !== 1 ||
    String(projects[0].id) !== projectNumber ||
    organizations.length > 1 ||
    (folders.length > 0 && organizations.length !== 1) ||
    ancestors.some(
      ancestor =>
        !['project', 'folder', 'organization'].includes(ancestor?.type) ||
        !/^[1-9][0-9]{5,19}$/u.test(String(ancestor?.id ?? '')),
    )
  ) {
    throw new Error(`${label} ancestor chain is invalid or ambiguous`);
  }
  return organizations.length === 1
    ? `organizations/${organizations[0].id}`
    : `projects/${projectId}`;
};

const expectedWifCondition = ({
  workflowPath,
  environment,
  repositoryId,
  repositoryOwnerId,
}) =>
  `assertion.repository=='yhsomani/AI-Birthday' && assertion.repository_id=='${repositoryId}' && assertion.repository_owner_id=='${repositoryOwnerId}' && assertion.workflow_ref=='yhsomani/AI-Birthday/${workflowPath}@refs/heads/main' && assertion.ref=='refs/heads/main' && assertion.sub=='repo:yhsomani/AI-Birthday:environment:${environment}'`;

const parseWifProvider = ({
  bytes,
  resource,
  workflowPath,
  environment,
  repositoryId,
  repositoryOwnerId,
}) => {
  const provider = parseJson(bytes, `${workflowPath} WIF provider`);
  const expectedMapping = {
    'google.subject': 'assertion.sub',
    'attribute.repository': 'assertion.repository',
    'attribute.repository_id': 'assertion.repository_id',
    'attribute.repository_owner_id': 'assertion.repository_owner_id',
    'attribute.workflow_ref': 'assertion.workflow_ref',
    'attribute.ref': 'assertion.ref',
  };
  if (
    provider.name !== resource ||
    provider.state !== 'ACTIVE' ||
    provider.disabled === true ||
    provider.oidc?.issuerUri !==
      'https://token.actions.githubusercontent.com' ||
    !Array.isArray(provider.oidc?.allowedAudiences ?? []) ||
    (provider.oidc?.allowedAudiences ?? []).length !== 0 ||
    stableJson(provider.attributeMapping) !== stableJson(expectedMapping) ||
    provider.attributeCondition !==
      expectedWifCondition({
        workflowPath,
        environment,
        repositoryId,
        repositoryOwnerId,
      })
  ) {
    throw new Error(`${workflowPath} WIF provider is not exact and active`);
  }
  return {
    resource,
    workflowPath,
    protectedEnvironment: environment,
    subject: `repo:yhsomani/AI-Birthday:environment:${environment}`,
    attributeCondition: provider.attributeCondition,
    attributeMapping: expectedMapping,
  };
};

const parseServiceAccountPolicy = ({ bytes, provider, subject, label }) => {
  const policy = parseJson(bytes, `${label} service-account IAM policy`);
  if (!Array.isArray(policy.bindings)) {
    throw new Error(`${label} service-account IAM policy has no bindings`);
  }
  const pool = provider.split('/providers/')[0];
  const expectedMember = `principal://iam.googleapis.com/${pool}/subject/${subject}`;
  const workloadBindings = policy.bindings.filter(binding =>
    [
      'roles/iam.workloadIdentityUser',
      'roles/iam.serviceAccountTokenCreator',
      'roles/iam.serviceAccountUser',
    ].includes(binding?.role),
  );
  if (
    workloadBindings.length !== 1 ||
    workloadBindings[0].role !== 'roles/iam.workloadIdentityUser' ||
    workloadBindings[0].condition !== undefined ||
    !Array.isArray(workloadBindings[0].members) ||
    workloadBindings[0].members.length !== 1 ||
    workloadBindings[0].members[0] !== expectedMember
  ) {
    throw new Error(
      `${label} impersonation policy is not one exact WIF subject`,
    );
  }
  return expectedMember;
};

const requireCrossProjectServiceAccountUsageDisabled = (bytes, label) => {
  const policy = parseJson(bytes, `${label} effective cross-project policy`);
  const rules = policy.spec?.rules;
  if (
    !Array.isArray(rules) ||
    rules.length !== 1 ||
    rules[0]?.enforce !== true ||
    rules[0]?.condition !== undefined
  ) {
    throw new Error(
      `${label} must enforce iam.disableCrossProjectServiceAccountUsage`,
    );
  }
};

const requireNoRuntimeAttachments = ({ bytes, accounts, label }) => {
  const assets = parseJson(bytes, `${label} resource asset inventory`);
  if (!Array.isArray(assets)) {
    throw new Error(`${label} resource asset inventory must be an array`);
  }
  for (const asset of assets) {
    if (
      asset === null ||
      typeof asset !== 'object' ||
      Array.isArray(asset) ||
      typeof asset.assetType !== 'string'
    ) {
      throw new Error(`${label} resource asset inventory is malformed`);
    }
    const serialized = stableJson({
      name: asset.name ?? null,
      resource: asset.resource ?? null,
    });
    const matchingAccounts = accounts.filter(account =>
      serialized.includes(account),
    );
    if (matchingAccounts.length === 0) continue;
    if (
      asset.assetType === 'iam.googleapis.com/ServiceAccount' &&
      matchingAccounts.length === 1 &&
      typeof asset.name === 'string' &&
      asset.name.endsWith(`/serviceAccounts/${matchingAccounts[0]}`)
    ) {
      continue;
    }
    throw new Error(
      `${label} release service account is attached to a compute/runtime resource`,
    );
  }
  return assets.length;
};

const parseGithubSourceCi = (files, context, protection) => {
  const requiredStatusChecks = protection.required_status_checks;
  const configuredChecks = requiredStatusChecks?.checks;
  const configuredContexts = requiredStatusChecks?.contexts;
  if (
    requiredStatusChecks?.strict !== true ||
    !Array.isArray(configuredChecks) ||
    configuredChecks.length !== 1 ||
    configuredChecks[0]?.context !== RELEASE_ADMISSION_CHECK_NAME ||
    !Number.isSafeInteger(configuredChecks[0]?.app_id) ||
    configuredChecks[0].app_id <= 0 ||
    !Array.isArray(configuredContexts) ||
    configuredContexts.length !== 1 ||
    configuredContexts[0] !== RELEASE_ADMISSION_CHECK_NAME
  ) {
    throw new Error(
      'GitHub main branch does not strictly require the exact release-admission check',
    );
  }

  const checkObservation = parseJson(
    files.get('github-release-source-check-runs.json').bytes,
    'GitHub release-source check runs',
  );
  if (
    !Number.isSafeInteger(checkObservation?.total_count) ||
    checkObservation.total_count < 1 ||
    checkObservation.total_count >= 100 ||
    !Array.isArray(checkObservation.check_runs) ||
    checkObservation.check_runs.length !== checkObservation.total_count
  ) {
    throw new Error(
      'GitHub release-source check-run observation is incomplete',
    );
  }
  const matchingChecks = checkObservation.check_runs.filter(
    check =>
      check?.name === RELEASE_ADMISSION_CHECK_NAME &&
      check?.head_sha === context.sourceRevision,
  );
  if (matchingChecks.length !== 1) {
    throw new Error(
      'GitHub release source does not have exactly one aggregate CI check',
    );
  }
  const aggregateCheck = matchingChecks[0];
  if (
    !RUN_ID.test(String(aggregateCheck.id ?? '')) ||
    aggregateCheck.status !== 'completed' ||
    aggregateCheck.conclusion !== 'success' ||
    !RUN_ID.test(String(aggregateCheck.check_suite?.id ?? '')) ||
    !Number.isSafeInteger(aggregateCheck.app?.id) ||
    aggregateCheck.app.id !== configuredChecks[0].app_id ||
    aggregateCheck.app.slug !== 'github-actions' ||
    aggregateCheck.app.owner?.login !== 'github' ||
    aggregateCheck.app.owner?.type !== 'Organization' ||
    aggregateCheck.app.html_url !== 'https://github.com/apps/github-actions' ||
    aggregateCheck.url !==
      `https://api.github.com/repos/yhsomani/AI-Birthday/check-runs/${aggregateCheck.id}` ||
    typeof aggregateCheck.html_url !== 'string' ||
    !aggregateCheck.html_url.startsWith(
      'https://github.com/yhsomani/AI-Birthday/',
    )
  ) {
    throw new Error(
      'GitHub release-source aggregate CI check is not an exact successful GitHub Actions check',
    );
  }

  const runObservation = parseJson(
    files.get('github-release-source-ci-runs.json').bytes,
    'GitHub release-source CI workflow runs',
  );
  if (
    runObservation?.total_count !== 1 ||
    !Array.isArray(runObservation.workflow_runs) ||
    runObservation.workflow_runs.length !== 1
  ) {
    throw new Error(
      'GitHub release source does not have exactly one successful main CI run',
    );
  }
  const workflowRun = runObservation.workflow_runs[0];
  const workflowPathIsExact = [
    RELEASE_ADMISSION_WORKFLOW_PATH,
    `${RELEASE_ADMISSION_WORKFLOW_PATH}@main`,
    `${RELEASE_ADMISSION_WORKFLOW_PATH}@refs/heads/main`,
  ].includes(workflowRun?.path);
  if (
    !RUN_ID.test(String(workflowRun?.id ?? '')) ||
    workflowRun.name !== 'CI' ||
    !workflowPathIsExact ||
    workflowRun.head_sha !== context.sourceRevision ||
    workflowRun.head_branch !== 'main' ||
    workflowRun.event !== 'push' ||
    workflowRun.status !== 'completed' ||
    workflowRun.conclusion !== 'success' ||
    String(workflowRun.check_suite_id ?? '') !==
      String(aggregateCheck.check_suite.id) ||
    !RUN_ID.test(String(workflowRun.run_attempt ?? '')) ||
    workflowRun.repository?.full_name !== 'yhsomani/AI-Birthday' ||
    String(workflowRun.repository?.id ?? '') !== context.repositoryId ||
    String(workflowRun.repository?.owner?.id ?? '') !==
      context.repositoryOwnerId ||
    workflowRun.url !==
      `https://api.github.com/repos/yhsomani/AI-Birthday/actions/runs/${workflowRun.id}` ||
    workflowRun.html_url !==
      `https://github.com/yhsomani/AI-Birthday/actions/runs/${workflowRun.id}`
  ) {
    throw new Error(
      'GitHub release-source CI run is not the exact successful main workflow run',
    );
  }

  return {
    aggregateCheckName: RELEASE_ADMISSION_CHECK_NAME,
    aggregateCheckRunId: String(aggregateCheck.id),
    requiredCheckAppId: String(aggregateCheck.app.id),
    workflowPath: RELEASE_ADMISSION_WORKFLOW_PATH,
    workflowRunId: String(workflowRun.id),
    workflowRunAttempt: String(workflowRun.run_attempt),
    checkSuiteId: String(workflowRun.check_suite_id),
    sourceRevision: context.sourceRevision,
    conclusion: 'success',
  };
};

const parseGithubGovernance = (files, context) => {
  const repository = parseJson(
    files.get('github-repository.json').bytes,
    'GitHub repository governance observation',
  );
  if (
    repository.full_name !== 'yhsomani/AI-Birthday' ||
    String(repository.id) !== context.repositoryId ||
    repository.owner?.type !== 'Organization' ||
    String(repository.owner?.id) !== context.repositoryOwnerId
  ) {
    throw new Error(
      'GitHub repository is not the exact organization-owned repository',
    );
  }
  const protection = parseJson(
    files.get('github-main-branch-protection.json').bytes,
    'GitHub main branch protection',
  );
  const sourceCi = parseGithubSourceCi(files, context, protection);
  const bypass =
    protection.required_pull_request_reviews?.bypass_pull_request_allowances;
  if (
    bypass === null ||
    typeof bypass !== 'object' ||
    Array.isArray(bypass) ||
    protection.enforce_admins?.enabled !== true ||
    protection.required_pull_request_reviews?.dismiss_stale_reviews !== true ||
    protection.required_pull_request_reviews?.require_code_owner_reviews !==
      true ||
    protection.required_pull_request_reviews?.require_last_push_approval !==
      true ||
    !Number.isSafeInteger(
      protection.required_pull_request_reviews?.required_approving_review_count,
    ) ||
    protection.required_pull_request_reviews.required_approving_review_count <
      1 ||
    protection.allow_force_pushes?.enabled === true ||
    protection.allow_deletions?.enabled === true ||
    ['users', 'teams', 'apps'].some(
      field => !Array.isArray(bypass?.[field]) || bypass[field].length !== 0,
    )
  ) {
    throw new Error('GitHub main branch protection is not fail-closed');
  }
  const auditEvents = parseJson(
    files.get('github-environment-audit-log.json').bytes,
    'GitHub environment audit log',
  );
  if (!Array.isArray(auditEvents)) {
    throw new Error('GitHub environment audit log must be an array');
  }
  const environments = [
    'cloud-production-readonly-audit',
    'hosting-production-readonly-live',
    'hosting-production-build',
    'hosting-production-admission',
    'hosting-production-deploy',
  ];
  const environmentIds = {};
  const reviewerIds = {};
  const auditEventIds = {};
  for (const environmentName of environments) {
    const environment = parseJson(
      files.get(`github-environment-${environmentName}.json`).bytes,
      `GitHub ${environmentName} environment`,
    );
    const branchPolicies = parseJson(
      files.get(`github-environment-${environmentName}-branch-policies.json`)
        .bytes,
      `GitHub ${environmentName} branch policies`,
    );
    const requiredReviewerRules = (environment.protection_rules ?? []).filter(
      rule => rule?.type === 'required_reviewers',
    );
    const branchRules = (environment.protection_rules ?? []).filter(
      rule => rule?.type === 'branch_policy',
    );
    const reviewers = requiredReviewerRules[0]?.reviewers;
    const ids = Array.isArray(reviewers)
      ? reviewers.map(item => String(item?.reviewer?.id ?? ''))
      : [];
    if (
      environment.name !== environmentName ||
      !/^[1-9][0-9]{0,19}$/u.test(String(environment.id ?? '')) ||
      environment.protection_rules?.length !== 2 ||
      requiredReviewerRules.length !== 1 ||
      branchRules.length !== 1 ||
      requiredReviewerRules[0].prevent_self_review !== true ||
      ids.length < 1 ||
      new Set(ids).size !== ids.length ||
      ids.some(id => !/^[1-9][0-9]{0,19}$/u.test(id)) ||
      environment.deployment_branch_policy?.protected_branches !== false ||
      environment.deployment_branch_policy?.custom_branch_policies !== true ||
      branchPolicies.total_count !== 1 ||
      !Array.isArray(branchPolicies.branch_policies) ||
      branchPolicies.branch_policies.length !== 1 ||
      branchPolicies.branch_policies[0]?.name !== 'main' ||
      (Object.hasOwn(branchPolicies.branch_policies[0] ?? {}, 'type') &&
        branchPolicies.branch_policies[0].type !== 'branch')
    ) {
      throw new Error(`GitHub ${environmentName} protection is not exact`);
    }
    const relevant = auditEvents.filter(
      event =>
        event?.action === 'environment.update_protection_rule' &&
        event.org === 'yhsomani' &&
        String(event.org_id ?? '') === context.repositoryOwnerId &&
        String(event.repo_id ?? event.repository_id ?? '') ===
          context.repositoryId &&
        [event.repo, event.repository].includes('yhsomani/AI-Birthday') &&
        String(event.environment_id ?? '') === String(environment.id) &&
        event.environment_name === environmentName,
    );
    const timestamp = event => {
      const value = event?.['@timestamp'] ?? event?.created_at;
      const parsed =
        typeof value === 'number' || /^[0-9]+$/u.test(String(value ?? ''))
          ? Number(value)
          : Date.parse(value);
      return Number.isFinite(parsed) ? parsed : Number.NEGATIVE_INFINITY;
    };
    relevant.sort((left, right) => timestamp(right) - timestamp(left));
    const latest = relevant[0];
    if (
      latest === undefined ||
      timestamp(latest) === Number.NEGATIVE_INFINITY ||
      latest.can_admins_bypass !== false ||
      latest.prevent_self_review !== true ||
      typeof latest._document_id !== 'string' ||
      latest._document_id === ''
    ) {
      throw new Error(
        `GitHub ${environmentName} lacks current no-bypass audit proof`,
      );
    }
    environmentIds[environmentName] = String(environment.id);
    reviewerIds[environmentName] = [...ids].sort();
    auditEventIds[environmentName] = latest._document_id;
  }
  return {
    organizationId: context.repositoryOwnerId,
    repositoryId: context.repositoryId,
    branch: 'main',
    branchProtectionEnforced: true,
    requiredStatusChecksStrict: true,
    sourceCi,
    environmentIds,
    reviewerIds,
    auditEventIds,
  };
};

const iamAnalysis = (bytes, label, expectedScope) => {
  const analysis = parseJson(bytes, label);
  exactKeys(analysis, ['scope', 'response'], label);
  if (
    analysis.scope !== expectedScope ||
    analysis.response === null ||
    typeof analysis.response !== 'object' ||
    Array.isArray(analysis.response)
  ) {
    throw new Error(`${label} does not bind the authoritative analysis scope`);
  }
  const objects = walkObjects(analysis.response);
  const observedQueryScopes = objects
    .filter(object => typeof object.analysisQuery?.scope === 'string')
    .map(object => object.analysisQuery.scope);
  const explorationFlags = objects
    .filter(object => Object.hasOwn(object, 'fullyExplored'))
    .map(object => object.fullyExplored);
  if (
    explorationFlags.length === 0 ||
    explorationFlags.some(value => value !== true) ||
    observedQueryScopes.length === 0 ||
    observedQueryScopes.some(scope => scope !== expectedScope)
  ) {
    throw new Error(`${label} is not fully explored`);
  }
  const permissionsByMember = new Map();
  const results = objects.filter(
    object =>
      Array.isArray(object.accessControlLists) &&
      object.iamBinding !== null &&
      typeof object.iamBinding === 'object',
  );
  for (const result of results) {
    const members = new Set([
      ...(Array.isArray(result.iamBinding.members)
        ? result.iamBinding.members
        : []),
      ...(Array.isArray(result.identityList?.identities)
        ? result.identityList.identities.map(identity => identity?.name)
        : []),
    ]);
    const permissions = new Set();
    for (const accessList of result.accessControlLists) {
      for (const access of Array.isArray(accessList?.accesses)
        ? accessList.accesses
        : []) {
        if (typeof access?.permission === 'string') {
          permissions.add(access.permission);
        }
      }
    }
    for (const member of members) {
      if (typeof member !== 'string' || member === '') continue;
      const memberPermissions = permissionsByMember.get(member) ?? new Set();
      for (const permission of permissions) memberPermissions.add(permission);
      permissionsByMember.set(member, memberPermissions);
    }
  }
  return new Map(
    [...permissionsByMember].map(([member, permissions]) => [
      member,
      [...permissions].sort(),
    ]),
  );
};

const requireExactPermissionAnalysis = ({
  bytes,
  label,
  expected,
  permittedPermissionUniverse,
  analysisScope,
}) => {
  const analysis = iamAnalysis(bytes, label, analysisScope);
  if (
    analysis.size !== expected.size ||
    [...analysis].some(([member, permissions]) => {
      const expectedPermissions = expected.get(member);
      return (
        expectedPermissions === undefined ||
        stableJson(permissions) !==
          stableJson([...expectedPermissions].sort()) ||
        permissions.some(
          permission => !permittedPermissionUniverse.has(permission),
        )
      );
    })
  ) {
    throw new Error(`${label} does not project the exact effective access set`);
  }
  return analysis;
};

export function createCloudReadonlyObservationReport({
  rawRoot,
  rawRecords,
  manifestBytes,
  archiveBytes,
  manifestFileName,
  archiveFileName,
}) {
  if (
    manifestFileName !== 'evidence-manifest.json' ||
    archiveFileName !== 'cloud-readonly-observation.tar'
  ) {
    throw new Error(
      'read-only manifest/archive names do not match the workflow contract',
    );
  }
  const files = rawRecords ?? rawFiles(realpathSync(rawRoot));
  if (!(files instanceof Map)) {
    throw new Error('raw observation records are unavailable');
  }
  for (const required of REQUIRED_RAW_FILES) {
    if (!files.has(required))
      throw new Error(`raw observation is missing ${required}`);
  }
  const context = parseJson(
    files.get('workflow-context.json').bytes,
    'workflow context',
  );
  exactKeys(
    context,
    [
      'schemaVersion',
      'sourceRevision',
      'projectId',
      'projectNumber',
      'androidAppId',
      'iosAppId',
      'webAppId',
      'runtimeServiceAccount',
      'auditServiceAccount',
      'hostingSiteId',
      'loggingLocation',
      'releaseSecurityProjectId',
      'releaseSecurityProjectNumber',
      'applicationIamAnalysisScope',
      'releaseSecurityIamAnalysisScope',
      'admissionBucketName',
      'hostingObserverServiceAccount',
      'hostingObserverWifProvider',
      'admissionReaderServiceAccount',
      'admissionReaderWifProvider',
      'hostingDeployServiceAccount',
      'hostingDeployWifProvider',
      'releaseSecurityLogSinkName',
      'releaseSecurityLogBucketName',
      'releaseSecurityLoggingLocation',
      'retainedProjectAssignment',
      'repository',
      'repositoryId',
      'repositoryOwnerId',
      'runId',
      'runAttempt',
      'workflowRef',
      'workflowRun',
      'observedAt',
      'mutationAuthorized',
    ],
    'workflow context',
  );
  if (context.schemaVersion !== 1 || context.mutationAuthorized !== false) {
    throw new Error('workflow context identity/mutation boundary is invalid');
  }
  for (const [value, pattern, label] of [
    [context.sourceRevision, SOURCE_REVISION, 'source revision'],
    [context.projectId, PROJECT_ID, 'project ID'],
    [context.projectNumber, PROJECT_NUMBER, 'project number'],
    [context.androidAppId, FIREBASE_APP_ID, 'Android app ID'],
    [context.iosAppId, FIREBASE_APP_ID, 'iOS app ID'],
    [context.webAppId, FIREBASE_APP_ID, 'web app ID'],
    [context.hostingSiteId, SITE_ID, 'Hosting site ID'],
    [context.runtimeServiceAccount, SERVICE_ACCOUNT, 'runtime identity'],
    [context.auditServiceAccount, SERVICE_ACCOUNT, 'audit identity'],
    [
      context.releaseSecurityProjectId,
      PROJECT_ID,
      'release-security project ID',
    ],
    [
      context.releaseSecurityProjectNumber,
      PROJECT_NUMBER,
      'release-security project number',
    ],
    [context.admissionBucketName, BUCKET_NAME, 'admission bucket'],
    [
      context.hostingObserverServiceAccount,
      SERVICE_ACCOUNT,
      'Hosting observer',
    ],
    [
      context.hostingObserverWifProvider,
      WIF_PROVIDER,
      'Hosting observer provider',
    ],
    [
      context.admissionReaderServiceAccount,
      SERVICE_ACCOUNT,
      'admission reader',
    ],
    [
      context.admissionReaderWifProvider,
      WIF_PROVIDER,
      'admission reader provider',
    ],
    [context.hostingDeployServiceAccount, SERVICE_ACCOUNT, 'Hosting deployer'],
    [context.hostingDeployWifProvider, WIF_PROVIDER, 'Hosting deploy provider'],
    [context.repository, REPOSITORY, 'repository'],
    [context.repositoryId, RUN_ID, 'repository ID'],
    [context.repositoryOwnerId, RUN_ID, 'repository owner ID'],
    [context.runId, RUN_ID, 'workflow run ID'],
    [context.runAttempt, RUN_ID, 'workflow run attempt'],
    [context.observedAt, INSTANT, 'observation time'],
  ]) {
    requireMatch(value, pattern, label);
  }
  if (
    !context.androidAppId.startsWith(`1:${context.projectNumber}:android:`) ||
    !context.iosAppId.startsWith(`1:${context.projectNumber}:ios:`) ||
    !context.webAppId.startsWith(`1:${context.projectNumber}:web:`) ||
    context.runtimeServiceAccount === context.auditServiceAccount ||
    context.releaseSecurityProjectId === context.projectId ||
    context.releaseSecurityProjectNumber === context.projectNumber ||
    !context.hostingObserverServiceAccount.endsWith(
      `@${context.projectId}.iam.gserviceaccount.com`,
    ) ||
    !context.admissionReaderServiceAccount.endsWith(
      `@${context.releaseSecurityProjectId}.iam.gserviceaccount.com`,
    ) ||
    !context.hostingDeployServiceAccount.endsWith(
      `@${context.projectId}.iam.gserviceaccount.com`,
    ) ||
    new Set([
      context.runtimeServiceAccount,
      context.auditServiceAccount,
      context.hostingObserverServiceAccount,
      context.admissionReaderServiceAccount,
      context.hostingDeployServiceAccount,
    ]).size !== 5 ||
    new Set([
      context.hostingObserverWifProvider,
      context.admissionReaderWifProvider,
      context.hostingDeployWifProvider,
    ]).size !== 3 ||
    new Set(
      [
        context.hostingObserverWifProvider,
        context.admissionReaderWifProvider,
        context.hostingDeployWifProvider,
      ].map(provider => provider.split('/providers/')[0]),
    ).size !== 3 ||
    !context.hostingObserverWifProvider.startsWith(
      `projects/${context.projectNumber}/`,
    ) ||
    !context.admissionReaderWifProvider.startsWith(
      `projects/${context.releaseSecurityProjectNumber}/`,
    ) ||
    !context.hostingDeployWifProvider.startsWith(
      `projects/${context.projectNumber}/`,
    ) ||
    !/^(?:organizations\/[1-9][0-9]{5,19}|projects\/[a-z][a-z0-9-]{4,28}[a-z0-9])$/u.test(
      context.applicationIamAnalysisScope ?? '',
    ) ||
    !/^(?:organizations\/[1-9][0-9]{5,19}|projects\/[a-z][a-z0-9-]{4,28}[a-z0-9])$/u.test(
      context.releaseSecurityIamAnalysisScope ?? '',
    ) ||
    !/^[A-Za-z][A-Za-z0-9._-]{0,99}$/u.test(
      context.releaseSecurityLogSinkName ?? '',
    ) ||
    !/^[A-Za-z0-9._-]{1,100}$/u.test(
      context.releaseSecurityLogBucketName ?? '',
    ) ||
    !/^(?:global|eu|us|[a-z]+-[a-z]+[0-9])$/u.test(
      context.releaseSecurityLoggingLocation ?? '',
    ) ||
    context.repository !== 'yhsomani/AI-Birthday' ||
    typeof context.workflowRef !== 'string' ||
    !/^yhsomani\/AI-Birthday\/\.github\/workflows\/cloud-readonly-evidence\.yml@[A-Za-z0-9_./-]+$/u.test(
      context.workflowRef,
    ) ||
    context.workflowRef.includes('..') ||
    context.workflowRun !==
      `https://github.com/${context.repository}/actions/runs/${context.runId}`
  ) {
    throw new Error('workflow context cross-association is invalid');
  }
  const githubGovernance = parseGithubGovernance(files, context);

  const project = parseJson(
    files.get('project.json').bytes,
    'project observation',
  );
  if (
    String(project.projectId) !== context.projectId ||
    String(project.projectNumber) !== context.projectNumber
  ) {
    throw new Error('observed project does not match the workflow context');
  }
  for (const [file, expected, label] of [
    ['runtime-service-account.json', context.runtimeServiceAccount, 'runtime'],
    ['audit-service-account.json', context.auditServiceAccount, 'audit'],
  ]) {
    const identity = parseJson(files.get(file).bytes, `${label} identity`);
    if (identity.email !== expected) {
      throw new Error(`observed ${label} identity does not match context`);
    }
  }

  const releaseSecurityProject = parseJson(
    files.get('release-security-project.json').bytes,
    'release-security project observation',
  );
  if (
    String(releaseSecurityProject.projectId) !==
      context.releaseSecurityProjectId ||
    String(releaseSecurityProject.projectNumber) !==
      context.releaseSecurityProjectNumber
  ) {
    throw new Error('release-security project does not match context');
  }
  const applicationIamAnalysisScope = parseIamAnalysisScope({
    bytes: files.get('application-project-ancestors.json').bytes,
    projectId: context.projectId,
    projectNumber: context.projectNumber,
    label: 'application project',
  });
  const releaseSecurityIamAnalysisScope = parseIamAnalysisScope({
    bytes: files.get('release-security-project-ancestors.json').bytes,
    projectId: context.releaseSecurityProjectId,
    projectNumber: context.releaseSecurityProjectNumber,
    label: 'release-security project',
  });
  if (
    applicationIamAnalysisScope !== context.applicationIamAnalysisScope ||
    releaseSecurityIamAnalysisScope !== context.releaseSecurityIamAnalysisScope
  ) {
    throw new Error('IAM analysis scopes do not match authoritative ancestry');
  }
  requireCrossProjectServiceAccountUsageDisabled(
    files.get('application-cross-project-sa-org-policy.json').bytes,
    'application project',
  );
  requireCrossProjectServiceAccountUsageDisabled(
    files.get('release-security-cross-project-sa-org-policy.json').bytes,
    'release-security project',
  );
  const applicationResourceAssetCount = requireNoRuntimeAttachments({
    bytes: files.get('application-resource-assets.json').bytes,
    accounts: [
      context.hostingObserverServiceAccount,
      context.hostingDeployServiceAccount,
    ],
    label: 'application project',
  });
  const releaseSecurityResourceAssetCount = requireNoRuntimeAttachments({
    bytes: files.get('release-security-resource-assets.json').bytes,
    accounts: [context.admissionReaderServiceAccount],
    label: 'release-security project',
  });

  const identityDefinitions = [
    {
      key: 'observer',
      prefix: 'hosting-observer',
      account: context.hostingObserverServiceAccount,
      provider: context.hostingObserverWifProvider,
      workflowPath: '.github/workflows/hosting-current-live-observation.yml',
      environment: 'hosting-production-readonly-live',
      analysisScope: applicationIamAnalysisScope,
      permissions: [
        'storage.buckets.get',
        'storage.objects.create',
        'storage.objects.get',
      ],
    },
    {
      key: 'admissionReader',
      prefix: 'admission-reader',
      account: context.admissionReaderServiceAccount,
      provider: context.admissionReaderWifProvider,
      workflowPath: '.github/workflows/hosting-production-deploy.yml',
      environment: 'hosting-production-admission',
      analysisScope: releaseSecurityIamAnalysisScope,
      permissions: [
        'storage.buckets.get',
        'storage.objects.get',
        'storage.objects.list',
      ],
    },
    {
      key: 'deployer',
      prefix: 'hosting-deploy',
      account: context.hostingDeployServiceAccount,
      provider: context.hostingDeployWifProvider,
      workflowPath: '.github/workflows/hosting-production-deploy.yml',
      environment: 'hosting-production-deploy',
      analysisScope: applicationIamAnalysisScope,
      permissions: [],
    },
  ];
  const releaseIdentities = {};
  for (const definition of identityDefinitions) {
    const identity = parseJson(
      files.get(`${definition.prefix}-service-account.json`).bytes,
      `${definition.key} service account`,
    );
    if (identity.email !== definition.account) {
      throw new Error(
        `${definition.key} service account does not match context`,
      );
    }
    requireEmptyArray(
      parseJson(
        files.get(`${definition.prefix}-user-managed-keys.json`).bytes,
        `${definition.key} user-managed keys`,
      ),
      `${definition.key} user-managed keys`,
    );
    const poolResource = definition.provider.split('/providers/')[0];
    const pool = parseJson(
      files.get(`${definition.prefix}-wif-pool.json`).bytes,
      `${definition.key} WIF pool`,
    );
    const poolProviders = parseJson(
      files.get(`${definition.prefix}-wif-providers.json`).bytes,
      `${definition.key} WIF provider inventory`,
    );
    if (
      pool.name !== poolResource ||
      pool.state !== 'ACTIVE' ||
      pool.disabled === true ||
      !Array.isArray(poolProviders) ||
      poolProviders.length !== 1 ||
      poolProviders[0]?.name !== definition.provider ||
      poolProviders[0]?.state !== 'ACTIVE' ||
      poolProviders[0]?.disabled === true
    ) {
      throw new Error(
        `${definition.key} WIF pool must be active and contain exactly the approved provider`,
      );
    }
    const provider = parseWifProvider({
      bytes: files.get(`${definition.prefix}-wif-provider.json`).bytes,
      resource: definition.provider,
      workflowPath: definition.workflowPath,
      environment: definition.environment,
      repositoryId: context.repositoryId,
      repositoryOwnerId: context.repositoryOwnerId,
    });
    const principal = parseServiceAccountPolicy({
      bytes: files.get(`${definition.prefix}-service-account-iam.json`).bytes,
      provider: definition.provider,
      subject: provider.subject,
      label: definition.key,
    });
    requireExactPermissionAnalysis({
      bytes: files.get(
        `${definition.prefix}-impersonation-access-analysis.json`,
      ).bytes,
      label: `${definition.key} effective impersonation analysis`,
      expected: new Map([
        [
          principal,
          [
            'iam.serviceAccounts.getAccessToken',
            'iam.serviceAccounts.getOpenIdToken',
          ],
        ],
      ]),
      permittedPermissionUniverse: new Set([
        'iam.serviceAccounts.getAccessToken',
        'iam.serviceAccounts.getOpenIdToken',
        'iam.serviceAccounts.implicitDelegation',
        'iam.serviceAccounts.signBlob',
        'iam.serviceAccounts.signJwt',
        'iam.serviceAccounts.actAs',
      ]),
      analysisScope: definition.analysisScope,
    });
    releaseIdentities[definition.key] = {
      serviceAccount: definition.account,
      userManagedKeyCount: 0,
      wifProvider: definition.provider,
      workflowPath: definition.workflowPath,
      protectedEnvironment: definition.environment,
      subject: provider.subject,
      attributeCondition: provider.attributeCondition,
      attributeMapping: provider.attributeMapping,
      admissionBucketPermissions: [...definition.permissions].sort(),
      impersonationPrincipal: principal,
    };
  }

  const applicationProjectBuckets = parseJson(
    files.get('application-project-buckets.json').bytes,
    'application project bucket inventory',
  );
  if (!Array.isArray(applicationProjectBuckets)) {
    throw new Error('application project bucket inventory must be an array');
  }
  const firebaseProject = parseJson(
    files.get('firebase-project.json').bytes,
    'Firebase project observation',
  );
  if (
    firebaseProject.projectId !== context.projectId ||
    String(firebaseProject.projectNumber) !== context.projectNumber ||
    (typeof firebaseProject.resources?.storageBucket === 'string' &&
      firebaseProject.resources.storageBucket !== '')
  ) {
    throw new Error(
      'application project exposes a Firebase Storage application-product bucket',
    );
  }
  const releaseSecurityBuckets = parseJson(
    files.get('release-security-buckets.json').bytes,
    'release-security bucket inventory',
  );
  if (
    !Array.isArray(releaseSecurityBuckets) ||
    releaseSecurityBuckets.length !== 1 ||
    releaseSecurityBuckets[0]?.name !== context.admissionBucketName ||
    String(releaseSecurityBuckets[0]?.projectNumber) !==
      context.releaseSecurityProjectNumber
  ) {
    throw new Error(
      'release-security must contain the one exact admission bucket',
    );
  }
  const admissionBucket = parseJson(
    files.get('admission-bucket.json').bytes,
    'admission bucket observation',
  );
  const lifecycleRules = admissionBucket.lifecycle?.rule;
  if (
    admissionBucket.name !== context.admissionBucketName ||
    String(admissionBucket.projectNumber) !==
      context.releaseSecurityProjectNumber ||
    !/^[1-9][0-9]{0,19}$/u.test(String(admissionBucket.metageneration ?? '')) ||
    admissionBucket.iamConfiguration?.publicAccessPrevention !== 'enforced' ||
    admissionBucket.iamConfiguration?.uniformBucketLevelAccess?.enabled !==
      true ||
    admissionBucket.versioning?.enabled === true ||
    String(
      admissionBucket.softDeletePolicy?.retentionDurationSeconds ?? '0',
    ) !== '0' ||
    String(admissionBucket.retentionPolicy?.retentionPeriod ?? '') !== '900' ||
    admissionBucket.retentionPolicy?.isLocked !== true ||
    !Array.isArray(lifecycleRules) ||
    lifecycleRules.length !== 1 ||
    lifecycleRules[0]?.action?.type !== 'Delete' ||
    lifecycleRules[0]?.condition?.age !== 1 ||
    stableJson(lifecycleRules[0]?.condition?.matchesPrefix) !==
      stableJson(['hosting-production-change-freezes/'])
  ) {
    throw new Error(
      'admission bucket is not the exact locked release-control bucket',
    );
  }
  const admissionBucketPolicy = parseJson(
    files.get('admission-bucket-iam.json').bytes,
    'admission bucket IAM policy',
  );
  if (
    !Array.isArray(admissionBucketPolicy.bindings) ||
    admissionBucketPolicy.bindings.some(binding =>
      (binding.members ?? []).some(member =>
        /^(?:allUsers|allAuthenticatedUsers|user:|group:|domain:)/u.test(
          member,
        ),
      ),
    )
  ) {
    throw new Error(
      'admission bucket IAM permits a user, group, domain, or public principal',
    );
  }
  const observerMember = `serviceAccount:${context.hostingObserverServiceAccount}`;
  const readerMember = `serviceAccount:${context.admissionReaderServiceAccount}`;
  const bucketAccess = requireExactPermissionAnalysis({
    bytes: files.get('admission-bucket-access-analysis.json').bytes,
    label: 'admission bucket effective IAM analysis',
    expected: new Map([
      [observerMember, releaseIdentities.observer.admissionBucketPermissions],
      [
        readerMember,
        releaseIdentities.admissionReader.admissionBucketPermissions,
      ],
    ]),
    permittedPermissionUniverse: ADMISSION_PERMISSION_UNIVERSE,
    analysisScope: releaseSecurityIamAnalysisScope,
  });
  if (
    bucketAccess.has(`serviceAccount:${context.hostingDeployServiceAccount}`) ||
    bucketAccess.has(`serviceAccount:${context.runtimeServiceAccount}`) ||
    bucketAccess.has(`serviceAccount:${context.auditServiceAccount}`)
  ) {
    throw new Error(
      'application/runtime/deploy identity has admission-bucket access',
    );
  }

  const hostingMutation = requireExactPermissionAnalysis({
    bytes: files.get('hosting-mutation-access-analysis.json').bytes,
    label: 'Hosting mutation effective IAM analysis',
    expected: new Map([
      [
        `serviceAccount:${context.hostingDeployServiceAccount}`,
        HOSTING_MUTATION_PERMISSIONS,
      ],
    ]),
    permittedPermissionUniverse: new Set(HOSTING_MUTATION_PERMISSIONS),
    analysisScope: applicationIamAnalysisScope,
  });
  const applicationProjectIam = parseJson(
    files.get('application-project-iam.json').bytes,
    'application project IAM policy',
  );
  if (!Array.isArray(applicationProjectIam.bindings)) {
    throw new Error('application project IAM policy has no bindings');
  }

  const releaseSecurityIam = parseJson(
    files.get('release-security-project-iam.json').bytes,
    'release-security project IAM policy',
  );
  const securityAncestors = parseJson(
    files.get('release-security-project-ancestors.json').bytes,
    'release-security project ancestors',
  );
  const ancestorPolicies = parseJson(
    files.get('release-security-ancestor-iam-policies.json').bytes,
    'release-security ancestor IAM policies',
  );
  const expectedAncestors = new Map(
    securityAncestors
      .filter(ancestor => ancestor.type !== 'project')
      .map(ancestor => [`${ancestor.type}:${ancestor.id}`, ancestor]),
  );
  if (
    !Array.isArray(ancestorPolicies) ||
    ancestorPolicies.length !== expectedAncestors.size
  ) {
    throw new Error(
      'release-security ancestor IAM policy inventory is incomplete',
    );
  }
  const hierarchyPolicies = [releaseSecurityIam];
  const observedAncestors = new Set();
  for (const record of ancestorPolicies) {
    exactKeys(
      record,
      ['type', 'id', 'policy'],
      'release-security ancestor IAM policy',
    );
    const coordinate = `${record.type}:${record.id}`;
    if (
      !expectedAncestors.has(coordinate) ||
      observedAncestors.has(coordinate) ||
      record.policy === null ||
      typeof record.policy !== 'object' ||
      Array.isArray(record.policy)
    ) {
      throw new Error('release-security ancestor IAM policy is mismatched');
    }
    observedAncestors.add(coordinate);
    hierarchyPolicies.push(record.policy);
  }
  const applicableAuditConfigs = hierarchyPolicies.flatMap(policy =>
    (policy.auditConfigs ?? []).filter(config =>
      ['allServices', 'storage.googleapis.com'].includes(config?.service),
    ),
  );
  const auditLogTypes = [
    ...new Set(
      applicableAuditConfigs.flatMap(config =>
        (config.auditLogConfigs ?? []).map(logConfig => logConfig?.logType),
      ),
    ),
  ].sort();
  if (
    applicableAuditConfigs.length === 0 ||
    stableJson(auditLogTypes) !==
      stableJson(['ADMIN_READ', 'DATA_READ', 'DATA_WRITE']) ||
    applicableAuditConfigs.some(config =>
      (config.auditLogConfigs ?? []).some(
        logConfig =>
          !Array.isArray(logConfig.exemptedMembers ?? []) ||
          (logConfig.exemptedMembers ?? []).length !== 0,
      ),
    )
  ) {
    throw new Error(
      'release-security storage audit logs are incomplete or exempt principals',
    );
  }
  const logBucketName = `projects/${context.releaseSecurityProjectId}/locations/${context.releaseSecurityLoggingLocation}/buckets/${context.releaseSecurityLogBucketName}`;
  const logBucket = parseJson(
    files.get('release-security-log-bucket.json').bytes,
    'release-security log bucket',
  );
  if (
    logBucket.name !== logBucketName ||
    logBucket.retentionDays !== 30 ||
    logBucket.locked !== true
  ) {
    throw new Error(
      'release-security log bucket retention is not exact and locked',
    );
  }
  const sinkName = `projects/${context.releaseSecurityProjectId}/sinks/${context.releaseSecurityLogSinkName}`;
  const sinkFilter = `resource.type="gcs_bucket" AND resource.labels.bucket_name="${context.admissionBucketName}"`;
  const sinks = parseJson(
    files.get('release-security-logging-sinks.json').bytes,
    'release-security logging sinks',
  );
  const matchingSinks = Array.isArray(sinks)
    ? sinks.filter(sink => sink?.name === context.releaseSecurityLogSinkName)
    : [];
  if (
    matchingSinks.length !== 1 ||
    matchingSinks[0].destination !==
      `logging.googleapis.com/${logBucketName}` ||
    matchingSinks[0].filter !== sinkFilter ||
    matchingSinks[0].disabled === true ||
    !Array.isArray(matchingSinks[0].exclusions ?? []) ||
    (matchingSinks[0].exclusions ?? []).length !== 0
  ) {
    throw new Error(
      'release-security log sink is missing, duplicated, or misconfigured',
    );
  }

  const appObjects = walkObjects(
    parseJson(files.get('firebase-apps.json').bytes, 'Firebase app inventory'),
  );
  const apps = [
    ['ANDROID', context.androidAppId],
    ['IOS', context.iosAppId],
    ['WEB', context.webAppId],
  ].map(([platform, appId]) => {
    const matches = appObjects.filter(
      candidate =>
        candidate.appId === appId &&
        String(candidate.platform ?? '').toUpperCase() === platform,
    );
    if (matches.length !== 1) {
      throw new Error(`Firebase ${platform} app is not observed exactly once`);
    }
    const resourceName = matches[0].name;
    if (
      typeof resourceName !== 'string' ||
      !resourceName.startsWith(`projects/${context.projectNumber}/`)
    ) {
      throw new Error(`Firebase ${platform} app resource is cross-project`);
    }
    return { appId, platform, resourceName };
  });

  const siteName = `projects/${context.projectId}/sites/${context.hostingSiteId}`;
  const siteMatches = walkObjects(
    parseJson(
      files.get('firebase-hosting-sites.json').bytes,
      'Firebase Hosting site inventory',
    ),
  ).filter(candidate => candidate.name === siteName);
  if (siteMatches.length !== 1) {
    throw new Error('Firebase Hosting site is not observed exactly once');
  }

  const { manifest, manifestFiles } = verifyCloudReadonlyArchive({
    archiveBytes,
    manifestBytes,
    actualRawFiles: files,
  });
  if (
    manifest.schemaVersion !== 3 ||
    manifest.base !== 'cloud-production-readonly' ||
    manifest.provenance?.sourceRevision !== context.sourceRevision ||
    !Array.isArray(manifest.entries)
  ) {
    throw new Error('evidence manifest provenance is invalid');
  }
  if (manifestFiles.length !== files.size) {
    throw new Error('evidence manifest does not cover the exact raw inventory');
  }
  for (const [relative, record] of files) {
    const suffix = `/raw/${relative}`;
    const matches = manifestFiles.filter(entry => entry.path.endsWith(suffix));
    if (
      matches.length !== 1 ||
      matches[0].sha256 !== record.sha256 ||
      matches[0].bytes !== record.byteCount
    ) {
      throw new Error(`evidence manifest does not bind raw/${relative}`);
    }
  }

  return {
    schemaVersion: 1,
    product: 'birthday-autopilot-cloud-readonly-observation',
    status: 'observed-not-approved',
    sourceRevision: context.sourceRevision,
    observedAt: context.observedAt,
    mutationAuthorized: false,
    project: {
      projectId: context.projectId,
      projectNumber: context.projectNumber,
      androidAppId: context.androidAppId,
      iosAppId: context.iosAppId,
      webAppId: context.webAppId,
      hostingSiteId: context.hostingSiteId,
    },
    identities: {
      runtimeServiceAccount: context.runtimeServiceAccount,
      auditServiceAccount: context.auditServiceAccount,
    },
    hostingReleaseControl: {
      releaseSecurityProjectId: context.releaseSecurityProjectId,
      releaseSecurityProjectNumber: context.releaseSecurityProjectNumber,
      repositoryId: context.repositoryId,
      repositoryOwnerId: context.repositoryOwnerId,
      applicationIamAnalysisScope,
      releaseSecurityIamAnalysisScope,
      applicationResourceAssetCount,
      releaseSecurityResourceAssetCount,
      githubGovernance,
      identities: releaseIdentities,
      admissionBucket: {
        name: admissionBucket.name,
        resourceName: `//storage.googleapis.com/projects/_/buckets/${admissionBucket.name}`,
        metageneration: String(admissionBucket.metageneration),
        publicAccessPrevention:
          admissionBucket.iamConfiguration.publicAccessPrevention,
        uniformBucketLevelAccess:
          admissionBucket.iamConfiguration.uniformBucketLevelAccess.enabled,
        versioningEnabled: admissionBucket.versioning?.enabled === true,
        softDeleteRetentionSeconds: Number(
          admissionBucket.softDeletePolicy?.retentionDurationSeconds ?? 0,
        ),
        retentionSeconds: Number(
          admissionBucket.retentionPolicy.retentionPeriod,
        ),
        retentionLocked: admissionBucket.retentionPolicy.isLocked,
        lifecycleDeleteAgeDays: lifecycleRules[0].condition.age,
        lifecycleMatchesPrefix: lifecycleRules[0].condition.matchesPrefix[0],
        releaseSecurityProjectBucketCount: releaseSecurityBuckets.length,
      },
      bucketAccessPrincipalCount: bucketAccess.size,
      applicationAndClientBucketAccessCount: 0,
      applicationProjectCloudStorageEnabled: false,
      hostingMutation: {
        siteResourceName: `//firebasehosting.googleapis.com/projects/${context.projectNumber}/sites/${context.hostingSiteId}`,
        serviceAccount: context.hostingDeployServiceAccount,
        workflowPath: '.github/workflows/hosting-production-deploy.yml',
        mutationIdentityCount: hostingMutation.size,
        mutationWorkflowCount: 1,
        alternateMutationIdentityCount: 0,
        permissions: [...HOSTING_MUTATION_PERMISSIONS],
      },
      auditLogging: {
        service: 'storage.googleapis.com',
        logTypes: auditLogTypes,
        exemptedMembers: [],
        sinkName,
        sinkDestination: matchingSinks[0].destination,
        sinkFilter,
        sinkDisabled: matchingSinks[0].disabled === true,
        sinkExclusions: [],
        logBucketName,
        logBucketLocation: context.releaseSecurityLoggingLocation,
        retentionDays: logBucket.retentionDays,
      },
    },
    workflow: {
      repository: context.repository,
      runId: context.runId,
      runAttempt: context.runAttempt,
      workflowRef: context.workflowRef,
      runUrl: context.workflowRun,
    },
    observed: {
      firebaseApps: apps,
      hostingSiteResourceName: siteName,
      projectResourceName: `projects/${context.projectNumber}`,
    },
    evidenceManifest: {
      path: manifestFileName,
      sha256: sha256(manifestBytes),
      bytes: manifestBytes.byteLength,
    },
    rawArchive: {
      path: archiveFileName,
      sha256: sha256(archiveBytes),
      bytes: archiveBytes.byteLength,
    },
  };
}

export function validateCloudReadonlyObservationReport(
  report,
  {
    reference,
    document,
    expectedSource,
    reportSha256,
    reportBytes,
    evidenceFiles,
    allowedCompanionPaths,
  },
) {
  try {
    exactKeys(
      report,
      [
        'schemaVersion',
        'product',
        'status',
        'sourceRevision',
        'observedAt',
        'mutationAuthorized',
        'project',
        'identities',
        'hostingReleaseControl',
        'workflow',
        'observed',
        'evidenceManifest',
        'rawArchive',
      ],
      'live-readonly-audit report',
    );
    if (
      report.schemaVersion !== 1 ||
      report.product !== 'birthday-autopilot-cloud-readonly-observation' ||
      report.status !== 'observed-not-approved' ||
      report.mutationAuthorized !== false
    ) {
      throw new Error('live-readonly-audit report identity/status is invalid');
    }
    if (
      report.sourceRevision !== document.source?.revision ||
      report.sourceRevision !== expectedSource?.revision ||
      report.observedAt !== reference?.capturedAt ||
      reference?.kind !== 'attestation' ||
      reportSha256 !== reference?.sha256 ||
      !Number.isSafeInteger(reportBytes) ||
      reportBytes <= 0
    ) {
      throw new Error(
        'live-readonly-audit report bytes/source/capturedAt are not bound',
      );
    }
    exactKeys(
      report.project,
      [
        'projectId',
        'projectNumber',
        'androidAppId',
        'iosAppId',
        'webAppId',
        'hostingSiteId',
      ],
      'live-readonly-audit project',
    );
    if (
      report.project.projectId !== document.project?.projectId ||
      report.project.projectNumber !== document.project?.projectNumber ||
      report.project.androidAppId !== document.project?.androidAppId ||
      report.project.iosAppId !== document.project?.iosAppId ||
      report.project.webAppId !== document.project?.webAppId ||
      report.project.hostingSiteId !== document.hosting?.siteId
    ) {
      throw new Error(
        'live-readonly-audit project/apps/site do not match signed cloud evidence',
      );
    }
    exactKeys(
      report.identities,
      ['runtimeServiceAccount', 'auditServiceAccount'],
      'live-readonly-audit identities',
    );
    if (
      report.identities.runtimeServiceAccount !==
        document.functions?.runtimeServiceAccount ||
      report.identities.auditServiceAccount !==
        document.iam?.auditServiceAccount
    ) {
      throw new Error(
        'live-readonly-audit identities do not match signed cloud evidence',
      );
    }
    const control = report.hostingReleaseControl;
    exactKeys(
      control,
      [
        'releaseSecurityProjectId',
        'releaseSecurityProjectNumber',
        'repositoryId',
        'repositoryOwnerId',
        'applicationIamAnalysisScope',
        'releaseSecurityIamAnalysisScope',
        'applicationResourceAssetCount',
        'releaseSecurityResourceAssetCount',
        'githubGovernance',
        'identities',
        'admissionBucket',
        'bucketAccessPrincipalCount',
        'applicationAndClientBucketAccessCount',
        'applicationProjectCloudStorageEnabled',
        'hostingMutation',
        'auditLogging',
      ],
      'live-readonly-audit Hosting release control',
    );
    exactKeys(
      control.identities,
      ['observer', 'admissionReader', 'deployer'],
      'live-readonly-audit Hosting release identities',
    );
    const signedControl = document.hostingReleaseControl;
    if (
      control.releaseSecurityProjectId !==
        signedControl?.releaseSecurityProjectId ||
      control.releaseSecurityProjectNumber !==
        signedControl?.releaseSecurityProjectNumber ||
      control.repositoryId !== signedControl?.repositoryId ||
      control.repositoryOwnerId !== signedControl?.repositoryOwnerId ||
      control.applicationIamAnalysisScope !==
        signedControl?.applicationIamAnalysisScope ||
      control.releaseSecurityIamAnalysisScope !==
        signedControl?.releaseSecurityIamAnalysisScope ||
      !Number.isSafeInteger(control.applicationResourceAssetCount) ||
      control.applicationResourceAssetCount < 0 ||
      !Number.isSafeInteger(control.releaseSecurityResourceAssetCount) ||
      control.releaseSecurityResourceAssetCount < 0 ||
      control.bucketAccessPrincipalCount !== 2 ||
      control.applicationAndClientBucketAccessCount !==
        signedControl?.applicationAndClientBucketAccessCount ||
      control.applicationProjectCloudStorageEnabled !==
        document.prohibitedServices?.applicationProjectCloudStorageEnabled
    ) {
      throw new Error(
        'live-readonly-audit release-security identity/access projection does not match signed evidence',
      );
    }
    exactKeys(
      control.githubGovernance,
      [
        'organizationId',
        'repositoryId',
        'branch',
        'branchProtectionEnforced',
        'requiredStatusChecksStrict',
        'sourceCi',
        'environmentIds',
        'reviewerIds',
        'auditEventIds',
      ],
      'live-readonly-audit GitHub governance',
    );
    exactKeys(
      control.githubGovernance.sourceCi,
      [
        'aggregateCheckName',
        'aggregateCheckRunId',
        'requiredCheckAppId',
        'workflowPath',
        'workflowRunId',
        'workflowRunAttempt',
        'checkSuiteId',
        'sourceRevision',
        'conclusion',
      ],
      'live-readonly-audit exact-source CI proof',
    );
    const governanceEnvironments = [
      'cloud-production-readonly-audit',
      'hosting-production-readonly-live',
      'hosting-production-build',
      'hosting-production-admission',
      'hosting-production-deploy',
    ];
    for (const [field, value] of [
      ['environmentIds', control.githubGovernance.environmentIds],
      ['reviewerIds', control.githubGovernance.reviewerIds],
      ['auditEventIds', control.githubGovernance.auditEventIds],
    ]) {
      exactKeys(
        value,
        governanceEnvironments,
        `live-readonly-audit GitHub governance ${field}`,
      );
    }
    if (
      control.githubGovernance.organizationId !==
        signedControl?.repositoryOwnerId ||
      control.githubGovernance.repositoryId !== signedControl?.repositoryId ||
      control.githubGovernance.branch !== 'main' ||
      control.githubGovernance.branchProtectionEnforced !== true ||
      control.githubGovernance.requiredStatusChecksStrict !== true ||
      control.githubGovernance.sourceCi.aggregateCheckName !==
        RELEASE_ADMISSION_CHECK_NAME ||
      !RUN_ID.test(
        control.githubGovernance.sourceCi.aggregateCheckRunId ?? '',
      ) ||
      !RUN_ID.test(
        control.githubGovernance.sourceCi.requiredCheckAppId ?? '',
      ) ||
      control.githubGovernance.sourceCi.workflowPath !==
        RELEASE_ADMISSION_WORKFLOW_PATH ||
      !RUN_ID.test(control.githubGovernance.sourceCi.workflowRunId ?? '') ||
      !RUN_ID.test(
        control.githubGovernance.sourceCi.workflowRunAttempt ?? '',
      ) ||
      !RUN_ID.test(control.githubGovernance.sourceCi.checkSuiteId ?? '') ||
      control.githubGovernance.sourceCi.sourceRevision !==
        report.sourceRevision ||
      control.githubGovernance.sourceCi.conclusion !== 'success' ||
      governanceEnvironments.some(
        name =>
          !/^[1-9][0-9]{0,19}$/u.test(
            control.githubGovernance.environmentIds[name] ?? '',
          ) ||
          !Array.isArray(control.githubGovernance.reviewerIds[name]) ||
          control.githubGovernance.reviewerIds[name].length < 1 ||
          typeof control.githubGovernance.auditEventIds[name] !== 'string' ||
          control.githubGovernance.auditEventIds[name] === '',
      )
    ) {
      throw new Error(
        'live-readonly-audit GitHub governance is not signed and exact',
      );
    }
    const identityFields = [
      'serviceAccount',
      'userManagedKeyCount',
      'wifProvider',
      'workflowPath',
      'protectedEnvironment',
      'subject',
      'attributeCondition',
      'attributeMapping',
      'admissionBucketPermissions',
    ];
    for (const role of ['observer', 'admissionReader', 'deployer']) {
      exactKeys(
        control.identities[role],
        [...identityFields, 'impersonationPrincipal'],
        `live-readonly-audit ${role} identity`,
      );
      const projected = Object.fromEntries(
        identityFields.map(field => [field, control.identities[role][field]]),
      );
      const expectedPrincipal = `principal://iam.googleapis.com/${
        control.identities[role].wifProvider.split('/providers/')[0]
      }/subject/${control.identities[role].subject}`;
      if (
        stableJson(projected) !== stableJson(signedControl?.[role]) ||
        control.identities[role].impersonationPrincipal !== expectedPrincipal
      ) {
        throw new Error(
          `live-readonly-audit ${role} identity/provider is not signed and principal-bound`,
        );
      }
    }
    if (
      stableJson(control.admissionBucket) !==
      stableJson(signedControl?.admissionBucket)
    ) {
      throw new Error(
        'live-readonly-audit admission bucket does not match signed evidence',
      );
    }
    exactKeys(
      control.hostingMutation,
      [
        'siteResourceName',
        'serviceAccount',
        'workflowPath',
        'mutationIdentityCount',
        'mutationWorkflowCount',
        'alternateMutationIdentityCount',
        'permissions',
      ],
      'live-readonly-audit Hosting mutation projection',
    );
    const mutationProjection = Object.fromEntries(
      Object.keys(control.hostingMutation)
        .filter(field => field !== 'permissions')
        .map(field => [field, control.hostingMutation[field]]),
    );
    if (
      stableJson(mutationProjection) !==
        stableJson(signedControl?.hostingMutation) ||
      stableJson(control.hostingMutation.permissions) !==
        stableJson(HOSTING_MUTATION_PERMISSIONS)
    ) {
      throw new Error(
        'live-readonly-audit Hosting writer projection does not match signed evidence',
      );
    }
    if (
      stableJson(control.auditLogging) !==
      stableJson(signedControl?.auditLogging)
    ) {
      throw new Error(
        'live-readonly-audit release-security audit logging does not match signed evidence',
      );
    }
    exactKeys(
      report.workflow,
      ['repository', 'runId', 'runAttempt', 'workflowRef', 'runUrl'],
      'live-readonly-audit workflow',
    );
    const repository = 'yhsomani/AI-Birthday';
    if (
      report.workflow.repository !== repository ||
      !RUN_ID.test(report.workflow.runId ?? '') ||
      !RUN_ID.test(report.workflow.runAttempt ?? '') ||
      report.workflow.runUrl !==
        `https://github.com/${repository}/actions/runs/${report.workflow.runId}` ||
      typeof report.workflow.workflowRef !== 'string' ||
      !report.workflow.workflowRef.startsWith(
        `${repository}/.github/workflows/cloud-readonly-evidence.yml@`,
      )
    ) {
      throw new Error('live-readonly-audit workflow provenance is invalid');
    }
    exactKeys(
      report.observed,
      ['firebaseApps', 'hostingSiteResourceName', 'projectResourceName'],
      'live-readonly-audit parsed observations',
    );
    const expectedApps = new Map([
      ['ANDROID', document.project.androidAppId],
      ['IOS', document.project.iosAppId],
      ['WEB', document.project.webAppId],
    ]);
    if (
      !Array.isArray(report.observed.firebaseApps) ||
      report.observed.firebaseApps.length !== expectedApps.size
    ) {
      throw new Error(
        'live-readonly-audit Firebase app projection is incomplete',
      );
    }
    const platforms = new Set();
    for (const app of report.observed.firebaseApps) {
      exactKeys(
        app,
        ['appId', 'platform', 'resourceName'],
        'live-readonly-audit Firebase app',
      );
      const collection = {
        ANDROID: 'androidApps',
        IOS: 'iosApps',
        WEB: 'webApps',
      }[app.platform];
      if (
        collection === undefined ||
        platforms.has(app.platform) ||
        app.appId !== expectedApps.get(app.platform) ||
        app.resourceName !==
          `projects/${document.project.projectNumber}/${collection}/${app.appId
            .split(':')
            .at(-1)}`
      ) {
        throw new Error(
          'live-readonly-audit Firebase app projection is invalid',
        );
      }
      platforms.add(app.platform);
    }
    if (
      platforms.size !== expectedApps.size ||
      report.observed.projectResourceName !==
        `projects/${document.project.projectNumber}` ||
      report.observed.hostingSiteResourceName !==
        `projects/${document.project.projectId}/sites/${document.hosting.siteId}`
    ) {
      throw new Error(
        'live-readonly-audit parsed project/site resources are invalid',
      );
    }
    const companions = new Set();
    for (const [field, label] of [
      ['evidenceManifest', 'manifest'],
      ['rawArchive', 'raw archive'],
    ]) {
      const value = report[field];
      exactKeys(
        value,
        ['path', 'sha256', 'bytes'],
        `live-readonly-audit ${label}`,
      );
      if (
        value.path !==
          (field === 'evidenceManifest'
            ? 'evidence-manifest.json'
            : 'cloud-readonly-observation.tar') ||
        typeof value.path !== 'string' ||
        !/^[A-Za-z0-9][A-Za-z0-9._/@()+ -]{0,511}$/u.test(value.path) ||
        path.isAbsolute(value.path) ||
        value.path
          .split(/[\\/]/u)
          .some(part => part === '' || part === '.' || part === '..') ||
        companions.has(value.path) ||
        !SHA256.test(value.sha256) ||
        !Number.isSafeInteger(value.bytes) ||
        value.bytes <= 0
      ) {
        throw new Error(
          `live-readonly-audit ${label} reference is unsafe or invalid`,
        );
      }
      companions.add(value.path);
      const actual = evidenceFiles?.get(value.path);
      if (
        actual === undefined ||
        actual.sha256 !== value.sha256 ||
        actual.bytes !== value.bytes
      ) {
        throw new Error(
          `live-readonly-audit ${label} bytes do not match the report`,
        );
      }
    }
    if (
      allowedCompanionPaths?.size !== companions.size ||
      [...companions].some(item => !allowedCompanionPaths.has(item))
    ) {
      throw new Error('live-readonly-audit companion inventory is not exact');
    }
    return [];
  } catch (error) {
    return [error instanceof Error ? error.message : String(error)];
  }
}

const parseArguments = argv => {
  if (argv.length % 2 !== 0)
    throw new Error('arguments must be --name value pairs');
  const values = new Map();
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    const value = argv[index + 1];
    if (!flag?.startsWith('--') || value === undefined) {
      throw new Error('arguments must be --name value pairs');
    }
    const name = flag.slice(2);
    if (!CLI_KEYS.has(name) || values.has(name)) {
      throw new Error(`unsupported or duplicate argument ${flag}`);
    }
    values.set(name, value);
  }
  const missing = [...CLI_KEYS].filter(name => !values.has(name));
  if (missing.length > 0)
    throw new Error(`missing arguments: ${missing.join(', ')}`);
  return values;
};

const run = () => {
  const args = parseArguments(process.argv.slice(2));
  const manifestPath = path.resolve(args.get('manifest'));
  const archivePath = path.resolve(args.get('archive'));
  const report = createCloudReadonlyObservationReport({
    rawRoot: path.resolve(args.get('raw-root')),
    manifestBytes: readStableFile(
      manifestPath,
      MAXIMUM_MANIFEST_BYTES,
      'evidence manifest',
    ),
    archiveBytes: readStableFile(
      archivePath,
      MAXIMUM_ARCHIVE_BYTES,
      'raw observation archive',
    ),
    manifestFileName: path.basename(manifestPath),
    archiveFileName: path.basename(archivePath),
  });
  writeFileSync(args.get('output'), `${stableJson(report)}\n`, {
    encoding: 'utf8',
    flag: 'wx',
    mode: 0o600,
  });
  process.stdout.write(
    `PASS cloud read-only observation report source=${report.sourceRevision} project=${report.project.projectId}\n`,
  );
};

if (
  process.argv[1] !== undefined &&
  realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url))
) {
  try {
    run();
  } catch (error) {
    process.stderr.write(
      `FAIL ${error instanceof Error ? error.message : String(error)}\n`,
    );
    process.exitCode = 1;
  }
}
