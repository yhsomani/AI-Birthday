#!/usr/bin/env node

import { createHash } from 'node:crypto';
import {
  closeSync,
  constants,
  fstatSync,
  lstatSync,
  openSync,
  readFileSync,
  writeFileSync,
} from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import {
  parseCocoaPodsLock,
  parseGradleLock,
} from './generate-native-sbom.mjs';
import { verifyDistributionEvidenceAuthority } from './validate-distribution-evidence.mjs';

const projectRoot = fileURLToPath(new URL('../', import.meta.url));
const exceptionPath = path.join(
  projectRoot,
  'tools/native-advisory-exceptions.json',
);
const cocoaPodsSourceMapPath = path.join(
  projectRoot,
  'tools/cocoapods-osv-source-map.json',
);
const packageLockPath = path.join(projectRoot, 'package-lock.json');
const authorityPinPath = path.join(
  projectRoot,
  'tools/distribution-authority-pin.json',
);

export const OSV_API_ENDPOINT = 'https://api.osv.dev/v1/querybatch';

const MAX_INPUT_BYTES = 16 * 1024 * 1024;
const MAX_RESPONSE_BYTES = 32 * 1024 * 1024;
const MAX_QUERY_BATCH = 500;
const MAX_PAGES = 20;
const MAX_EXCEPTION_DAYS = 30;
const SAFE_LABEL = /^[a-z][a-z0-9-]{2,63}$/u;
const SHA1 = /^[0-9a-f]{40}$/u;
const SHA256 = /^[0-9a-f]{64}$/u;
const VULNERABILITY_ID = /^[A-Z][A-Za-z0-9._-]{2,127}$/u;
const RFC3339 =
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/u;
const SAFE_PERSON = /^[A-Za-z0-9][A-Za-z0-9 ._@+-]{2,99}$/u;
const SAFE_REFERENCE = /^[A-Za-z0-9][A-Za-z0-9._:/#-]{7,255}$/u;
const SAFE_SOURCE_REPOSITORY =
  /^github\.com\/[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/u;
const SAFE_SOURCE_TAG = /^[A-Za-z0-9][A-Za-z0-9._/+:-]{0,127}$/u;
const PLACEHOLDER =
  /(?:^|[^a-z])(todo|tbd|placeholder|example)(?:[^a-z]|$)|[<>]/iu;
const CANARIES = Object.freeze([
  Object.freeze({
    purl: 'pkg:maven/com.google.guava/guava@30.1.1-jre',
    vulnerabilityId: 'GHSA-5mg8-w23w-74h3',
  }),
  Object.freeze({
    purl: 'pkg:npm/lodash@4.17.20',
    vulnerabilityId: 'GHSA-35jh-r3h4-6jhm',
  }),
  Object.freeze({
    purl: 'pkg:swift/github.com/apple/swift-asn1@1.3.0',
    vulnerabilityId: 'GHSA-w8xv-rwgf-4fwh',
  }),
]);

const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');

const isRecord = value =>
  value !== null && typeof value === 'object' && !Array.isArray(value);

const exactKeys = (value, expected, label) => {
  if (!isRecord(value)) throw new Error(`${label} must be an object`);
  const actual = Object.keys(value).sort();
  const required = [...expected].sort();
  if (
    actual.length !== required.length ||
    actual.some((key, index) => key !== required[index])
  ) {
    throw new Error(`${label} has missing or unsupported fields`);
  }
};

const parseJson = (bytes, label) => {
  let value;
  try {
    value = JSON.parse(bytes.toString('utf8'));
  } catch {
    throw new Error(`${label} is malformed or truncated JSON`);
  }
  if (!isRecord(value)) throw new Error(`${label} root must be an object`);
  return value;
};

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

export function readStableRegularFile(
  filePath,
  label,
  maxBytes = MAX_INPUT_BYTES,
) {
  let descriptor;
  try {
    const beforePath = lstatSync(filePath, { bigint: true });
    if (!beforePath.isFile() || beforePath.isSymbolicLink()) {
      throw new Error(`${label} must be a regular non-symbolic-link file`);
    }
    if (beforePath.size <= 0n || beforePath.size > BigInt(maxBytes)) {
      throw new Error(`${label} has an invalid size`);
    }
    descriptor = openSync(
      filePath,
      // File descriptor flags are intentionally composed as a bit mask.
      // eslint-disable-next-line no-bitwise
      constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0),
    );
    const before = fstatSync(descriptor, { bigint: true });
    if (
      !before.isFile() ||
      !sameMetadata(stableMetadata(beforePath), stableMetadata(before))
    ) {
      throw new Error(`${label} changed before it could be read`);
    }
    const bytes = readFileSync(descriptor);
    const after = fstatSync(descriptor, { bigint: true });
    const afterPath = lstatSync(filePath, { bigint: true });
    if (
      !after.isFile() ||
      !afterPath.isFile() ||
      !sameMetadata(stableMetadata(before), stableMetadata(after)) ||
      !sameMetadata(stableMetadata(before), stableMetadata(afterPath)) ||
      BigInt(bytes.byteLength) !== before.size
    ) {
      throw new Error(`${label} changed while it was read`);
    }
    return bytes;
  } finally {
    if (descriptor !== undefined) closeSync(descriptor);
  }
}

const validateEncodedSegment = (segment, label) => {
  if (!segment || /[?#\s]/u.test(segment)) {
    throw new Error(`${label} is not a canonical package URL`);
  }
  let decoded;
  try {
    decoded = decodeURIComponent(segment);
  } catch {
    throw new Error(`${label} has invalid percent encoding`);
  }
  if (
    !decoded ||
    // Control bytes are forbidden even when percent encoded in package IDs.
    // eslint-disable-next-line no-control-regex
    /[\u0000-\u001f\u007f/@*]/u.test(decoded) ||
    encodeURIComponent(decoded) !== segment
  ) {
    throw new Error(`${label} is not canonically encoded`);
  }
};

export function validatePurl(purl, allowedTypes, label = 'package URL') {
  if (typeof purl !== 'string' || !purl.startsWith('pkg:')) {
    throw new Error(`${label} must be a versioned package URL`);
  }
  const match = /^pkg:([a-z0-9.-]+)\/(.+)@([^?#\s]+)$/u.exec(purl);
  if (!match || !allowedTypes.has(match[1])) {
    throw new Error(`${label} uses an unsupported package URL type`);
  }
  const nameSegments = match[2].split('/');
  const expectedSegments = match[1] === 'maven' ? 2 : 1;
  if (match[1] === 'swift') {
    if (nameSegments.length !== 3 || nameSegments[0] !== 'github.com') {
      throw new Error(`${label} must identify an exact GitHub Swift source`);
    }
  } else if (
    match[1] === 'npm' &&
    nameSegments.length === 2 &&
    nameSegments[0].startsWith('%40')
  ) {
    // Scoped npm names are represented as an encoded scope plus package name.
  } else if (nameSegments.length !== expectedSegments) {
    throw new Error(`${label} has an invalid package name`);
  }
  for (const segment of nameSegments) validateEncodedSegment(segment, label);
  validateEncodedSegment(match[3], label);
  return purl;
}

const validateSbom = ({ bytes, kind, lockBytes, label }) => {
  const document = parseJson(bytes, `${label} SBOM`);
  if (
    document.bomFormat !== 'CycloneDX' ||
    document.specVersion !== '1.6' ||
    document.version !== 1 ||
    !isRecord(document.metadata) ||
    !Array.isArray(document.metadata.properties) ||
    !Array.isArray(document.components)
  ) {
    throw new Error(`${label} SBOM is not the required CycloneDX 1.6 document`);
  }
  const properties = new Map();
  for (const property of document.metadata.properties) {
    exactKeys(property, ['name', 'value'], `${label} SBOM property`);
    if (
      typeof property.name !== 'string' ||
      typeof property.value !== 'string' ||
      properties.has(property.name)
    ) {
      throw new Error(`${label} SBOM has an invalid or duplicate property`);
    }
    properties.set(property.name, property.value);
  }
  if (
    properties.get('birthday:dependency-manager') !== kind ||
    properties.get('birthday:lockfile-sha256') !== sha256(lockBytes)
  ) {
    throw new Error(`${label} SBOM is not bound to the exact lockfile`);
  }
  const configuration = properties.get('birthday:gradle-configuration') ?? null;
  const expectedPropertyNames = new Set([
    'birthday:dependency-manager',
    'birthday:lockfile-sha256',
    ...(configuration === null ? [] : ['birthday:gradle-configuration']),
  ]);
  if (
    properties.size !== expectedPropertyNames.size ||
    [...properties.keys()].some(name => !expectedPropertyNames.has(name)) ||
    (configuration !== null &&
      (kind !== 'gradle' ||
        !/^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$/u.test(configuration)))
  ) {
    throw new Error(`${label} SBOM scope metadata is invalid`);
  }

  const allowedType = new Set([kind === 'gradle' ? 'maven' : 'cocoapods']);
  const actual = [];
  const seen = new Set();
  for (const [index, component] of document.components.entries()) {
    if (
      !isRecord(component) ||
      component.type !== 'library' ||
      typeof component.name !== 'string' ||
      !component.name ||
      typeof component.version !== 'string' ||
      !component.version ||
      component['bom-ref'] !== component.purl
    ) {
      throw new Error(`${label} SBOM component ${index} is malformed`);
    }
    validatePurl(
      component.purl,
      allowedType,
      `${label} SBOM component ${index}`,
    );
    if (seen.has(component.purl)) {
      throw new Error(`${label} SBOM contains a duplicate component`);
    }
    seen.add(component.purl);
    actual.push(component.purl);
  }
  const expected = (
    kind === 'gradle'
      ? parseGradleLock(lockBytes.toString('utf8'), { configuration })
      : parseCocoaPodsLock(lockBytes.toString('utf8'))
  ).map(component => component.purl);
  actual.sort((left, right) => left.localeCompare(right, 'en'));
  expected.sort((left, right) => left.localeCompare(right, 'en'));
  if (
    actual.length !== expected.length ||
    actual.some((purl, index) => purl !== expected[index])
  ) {
    throw new Error(`${label} SBOM does not exactly represent the lock graph`);
  }
  return { components: document.components, configuration };
};

const sectionLines = (raw, sectionName) => {
  const lines = raw.split(/\r?\n/u);
  const start = lines.findIndex(line => line === `${sectionName}:`);
  if (start < 0) throw new Error(`Podfile.lock is missing ${sectionName}`);
  const output = [];
  for (let index = start + 1; index < lines.length; index += 1) {
    if (/^[A-Z][A-Z ]+:/u.test(lines[index])) break;
    output.push(lines[index]);
  }
  return output;
};

const parsePodSpecRepos = raw => {
  const lines = sectionLines(raw, 'SPEC REPOS');
  const roots = new Set();
  let repository = null;
  for (const line of lines) {
    const repositoryMatch = /^ {2}([^:]+):$/u.exec(line);
    if (repositoryMatch) {
      repository = repositoryMatch[1];
      if (repository !== 'trunk') {
        throw new Error('Podfile.lock uses an unreviewed CocoaPods spec repo');
      }
      continue;
    }
    const podMatch = /^ {4}- ([A-Za-z0-9+_.-]+)$/u.exec(line);
    if (podMatch && repository === 'trunk') roots.add(podMatch[1]);
    else if (line.trim())
      throw new Error('Podfile.lock SPEC REPOS is malformed');
  }
  if (roots.size === 0) throw new Error('Podfile.lock has no trunk pods');
  return roots;
};

const decodePodLockScalar = (raw, label) => {
  const value = raw.trim();
  if (!value.startsWith('"')) return value;
  try {
    const parsed = JSON.parse(value);
    if (typeof parsed === 'string') return parsed;
  } catch {
    // The shared failure below deliberately does not echo lockfile content.
  }
  throw new Error(`${label} has a malformed quoted value`);
};

const parsePodExternalSources = raw => {
  const lines = sectionLines(raw, 'EXTERNAL SOURCES');
  const sources = new Map();
  let name = null;
  for (const line of lines) {
    const nameMatch = /^ {2}([A-Za-z0-9+_.-]+):$/u.exec(line);
    if (nameMatch) {
      name = nameMatch[1];
      if (sources.has(name)) {
        throw new Error('Podfile.lock has duplicate external sources');
      }
      sources.set(name, {});
      continue;
    }
    const valueMatch = /^ {4}:(path|podspec|tag): (.+)$/u.exec(line);
    if (!valueMatch || name === null) {
      if (line.trim())
        throw new Error('Podfile.lock EXTERNAL SOURCES is malformed');
      continue;
    }
    const source = sources.get(name);
    if (source[valueMatch[1]] !== undefined) {
      throw new Error('Podfile.lock has a duplicate external source field');
    }
    source[valueMatch[1]] = decodePodLockScalar(
      valueMatch[2],
      'Podfile.lock external source',
    );
  }
  for (const source of sources.values()) {
    if ((source.path === undefined) === (source.podspec === undefined)) {
      throw new Error(
        'Podfile.lock external source must have one local location',
      );
    }
  }
  return sources;
};

const parsePodSpecChecksums = raw => {
  const lines = sectionLines(raw, 'SPEC CHECKSUMS');
  const checksums = new Map();
  for (const line of lines) {
    if (!line.trim()) continue;
    const match = /^ {2}([A-Za-z0-9+_.-]+): ([0-9a-f]{40})$/u.exec(line);
    if (!match || checksums.has(match[1])) {
      throw new Error('Podfile.lock SPEC CHECKSUMS is malformed');
    }
    checksums.set(match[1], match[2]);
  }
  return checksums;
};

const readNpmVersions = packageLockBytes => {
  const lock = parseJson(packageLockBytes, 'package-lock.json');
  if (lock.lockfileVersion !== 3 || !isRecord(lock.packages)) {
    throw new Error('package-lock.json must be an npm lockfileVersion 3 graph');
  }
  const versions = new Map();
  for (const [packagePath, metadata] of Object.entries(lock.packages)) {
    if (!packagePath.startsWith('node_modules/') || !isRecord(metadata))
      continue;
    const name = packagePath.slice('node_modules/'.length);
    if (
      name.includes('/node_modules/') ||
      typeof metadata.version !== 'string'
    ) {
      continue;
    }
    versions.set(name, metadata.version);
  }
  return versions;
};

const npmOwnerFromLocation = (location, podName) => {
  if (location.startsWith('../node_modules/')) {
    const suffix = location.slice('../node_modules/'.length);
    const parts = suffix.split('/');
    const owner = parts[0].startsWith('@')
      ? `${parts[0]}/${parts[1] ?? ''}`
      : parts[0];
    if (!owner || owner.endsWith('/')) {
      throw new Error(
        `cannot resolve npm owner for CocoaPods component ${podName}`,
      );
    }
    return owner;
  }
  if (
    location.startsWith('build/generated/ios/') &&
    ['ReactAppDependencyProvider', 'ReactCodegen'].includes(podName)
  ) {
    return 'react-native';
  }
  throw new Error(
    `CocoaPods component ${podName} has an unreviewed local source`,
  );
};

export function validateCocoaPodsSourceMap(document) {
  exactKeys(
    document,
    ['schemaVersion', 'scope', 'mappings'],
    'CocoaPods OSV source map',
  );
  if (
    document.schemaVersion !== 1 ||
    typeof document.scope !== 'string' ||
    document.scope.length < 80 ||
    !Array.isArray(document.mappings) ||
    document.mappings.length === 0
  ) {
    throw new Error('CocoaPods OSV source map header is invalid');
  }
  const pods = new Map();
  for (const [mappingIndex, mapping] of document.mappings.entries()) {
    exactKeys(
      mapping,
      ['sourceRepository', 'sourceTag', 'queryPurl', 'pods'],
      `CocoaPods OSV source mapping ${mappingIndex}`,
    );
    if (
      !SAFE_SOURCE_REPOSITORY.test(mapping.sourceRepository) ||
      !SAFE_SOURCE_TAG.test(mapping.sourceTag) ||
      !Array.isArray(mapping.pods) ||
      mapping.pods.length === 0
    ) {
      throw new Error(
        `CocoaPods OSV source mapping ${mappingIndex} is invalid`,
      );
    }
    validatePurl(
      mapping.queryPurl,
      new Set(['swift']),
      `CocoaPods OSV source mapping ${mappingIndex}`,
    );
    for (const [podIndex, pod] of mapping.pods.entries()) {
      exactKeys(
        pod,
        ['name', 'version', 'podspecSha1'],
        `CocoaPods OSV source mapping ${mappingIndex} pod ${podIndex}`,
      );
      if (
        typeof pod.name !== 'string' ||
        !pod.name ||
        typeof pod.version !== 'string' ||
        !pod.version ||
        !SHA1.test(pod.podspecSha1) ||
        mapping.queryPurl !==
          `pkg:swift/${mapping.sourceRepository}@${encodeURIComponent(
            pod.version,
          )}`
      ) {
        throw new Error(
          `CocoaPods OSV source mapping for ${pod.name} is invalid`,
        );
      }
      if (pods.has(pod.name)) {
        throw new Error(`CocoaPods OSV source map duplicates ${pod.name}`);
      }
      pods.set(pod.name, {
        ...pod,
        queryPurl: mapping.queryPurl,
        sourceRepository: mapping.sourceRepository,
        sourceTag: mapping.sourceTag,
      });
    }
  }
  return pods;
}

export function mapCocoaPodsComponents({
  lockBytes,
  components,
  sourceMapDocument,
  packageLockBytes,
}) {
  const raw = lockBytes.toString('utf8');
  const trunk = parsePodSpecRepos(raw);
  const external = parsePodExternalSources(raw);
  const checksums = parsePodSpecChecksums(raw);
  const sourceMappings = validateCocoaPodsSourceMap(sourceMapDocument);
  const npmVersions = readNpmVersions(packageLockBytes);
  const componentNames = new Set(components.map(component => component.name));
  const classified = new Set([...trunk, ...external.keys()]);
  if (
    [...trunk].some(name => external.has(name)) ||
    classified.size !== componentNames.size ||
    [...classified].some(name => !componentNames.has(name)) ||
    [...componentNames].some(name => !classified.has(name))
  ) {
    throw new Error('CocoaPods source classes do not exactly cover the SBOM');
  }
  if (
    checksums.size !== componentNames.size ||
    [...componentNames].some(name => !checksums.has(name))
  ) {
    throw new Error('CocoaPods spec checksums do not exactly cover the SBOM');
  }
  if (
    sourceMappings.size !== trunk.size ||
    [...sourceMappings].some(([name]) => !trunk.has(name))
  ) {
    throw new Error('CocoaPods trunk source map is stale or incomplete');
  }

  return components.map(component => {
    if (trunk.has(component.name)) {
      const mapping = sourceMappings.get(component.name);
      if (
        mapping.version !== component.version ||
        checksums.get(component.name) !== mapping.podspecSha1
      ) {
        throw new Error(
          `CocoaPods source mapping for ${component.name} does not match the lock`,
        );
      }
      return {
        componentPurl: component.purl,
        queryPurl: mapping.queryPurl,
        mappingKind: 'checksum-bound-trunk-source',
        podName: component.name,
        podVersion: component.version,
        podspecSha1: mapping.podspecSha1,
        sourceRepository: mapping.sourceRepository,
        sourceTag: mapping.sourceTag,
      };
    }
    const source = external.get(component.name);
    const owner = npmOwnerFromLocation(
      source.path ?? source.podspec,
      component.name,
    );
    const version = npmVersions.get(owner);
    if (!version) {
      throw new Error(`npm owner ${owner} is absent from package-lock.json`);
    }
    const encodedOwner = owner
      .split('/')
      .map(segment => encodeURIComponent(segment))
      .join('/');
    const queryPurl = `pkg:npm/${encodedOwner}@${encodeURIComponent(version)}`;
    validatePurl(queryPurl, new Set(['npm']), 'CocoaPods npm owner');
    return {
      componentPurl: component.purl,
      queryPurl,
      mappingKind: 'lock-bound-local-npm-owner',
    };
  });
}

export function prepareNativeTargets(
  targetSpecs,
  {
    root = projectRoot,
    sourceMapBytes = readStableRegularFile(
      cocoaPodsSourceMapPath,
      'CocoaPods OSV source map',
    ),
    packageLockBytes = readStableRegularFile(
      packageLockPath,
      'package-lock.json',
    ),
  } = {},
) {
  if (!Array.isArray(targetSpecs) || targetSpecs.length === 0) {
    throw new Error('at least one native dependency set is required');
  }
  const labels = new Set();
  const sourceMapDocument = parseJson(
    sourceMapBytes,
    'CocoaPods OSV source map',
  );
  return targetSpecs.map(spec => {
    if (
      !isRecord(spec) ||
      !SAFE_LABEL.test(spec.label ?? '') ||
      !['gradle', 'cocoapods'].includes(spec.kind) ||
      typeof spec.lockPath !== 'string' ||
      !spec.lockPath ||
      typeof spec.sbomPath !== 'string' ||
      !spec.sbomPath
    ) {
      throw new Error('native dependency set argument is invalid');
    }
    if (labels.has(spec.label))
      throw new Error('native dependency set labels must be unique');
    labels.add(spec.label);
    const resolvedLock = path.resolve(root, spec.lockPath);
    const resolvedSbom = path.resolve(root, spec.sbomPath);
    const lockBytes = readStableRegularFile(
      resolvedLock,
      `${spec.label} lockfile`,
    );
    const sbomBytes = readStableRegularFile(resolvedSbom, `${spec.label} SBOM`);
    const { components, configuration } = validateSbom({
      bytes: sbomBytes,
      kind: spec.kind,
      lockBytes,
      label: spec.label,
    });
    const componentMappings =
      spec.kind === 'gradle'
        ? components.map(component => ({
            componentPurl: component.purl,
            queryPurl: component.purl,
            mappingKind: 'direct-maven-purl',
          }))
        : mapCocoaPodsComponents({
            lockBytes,
            components,
            sourceMapDocument,
            packageLockBytes,
          });
    return {
      label: spec.label,
      kind: spec.kind,
      lockfileSha256: sha256(lockBytes),
      sbomSha256: sha256(sbomBytes),
      componentCount: components.length,
      configuration,
      componentMappings,
      ...(spec.kind === 'cocoapods'
        ? {
            sourceMapSha256: sha256(sourceMapBytes),
            npmLockSha256: sha256(packageLockBytes),
          }
        : {}),
    };
  });
}

const parseInstant = (value, label) => {
  if (typeof value !== 'string' || !RFC3339.test(value)) {
    throw new Error(`${label} must be an RFC3339 instant`);
  }
  const instant = Date.parse(value);
  if (!Number.isFinite(instant)) throw new Error(`${label} is invalid`);
  return instant;
};

export function validateExceptions(document, now = new Date()) {
  exactKeys(
    document,
    ['schemaVersion', 'exceptions'],
    'native advisory exceptions',
  );
  if (document.schemaVersion !== 1 || !Array.isArray(document.exceptions)) {
    throw new Error('native advisory exception document is invalid');
  }
  if (!(now instanceof Date) || !Number.isFinite(now.getTime())) {
    throw new Error('native advisory scan time is invalid');
  }
  const exceptions = new Map();
  for (const [index, exception] of document.exceptions.entries()) {
    const label = `native advisory exception ${index}`;
    exactKeys(
      exception,
      [
        'vulnerabilityId',
        'dependencySet',
        'componentPurl',
        'queryPurl',
        'owner',
        'approvedBy',
        'rationale',
        'trackingReference',
        'approvalEvidenceSha256',
        'approvedAt',
        'expiresAt',
      ],
      label,
    );
    validatePurl(
      exception.componentPurl,
      new Set(['maven', 'cocoapods']),
      `${label} component`,
    );
    validatePurl(
      exception.queryPurl,
      new Set(['maven', 'npm', 'swift']),
      `${label} query`,
    );
    const approvedAt = parseInstant(
      exception.approvedAt,
      `${label} approvedAt`,
    );
    const expiresAt = parseInstant(exception.expiresAt, `${label} expiresAt`);
    if (
      !VULNERABILITY_ID.test(exception.vulnerabilityId ?? '') ||
      !SAFE_LABEL.test(exception.dependencySet ?? '') ||
      !SAFE_PERSON.test(exception.owner ?? '') ||
      !SAFE_PERSON.test(exception.approvedBy ?? '') ||
      exception.owner === exception.approvedBy ||
      typeof exception.rationale !== 'string' ||
      exception.rationale.length < 40 ||
      exception.rationale.length > 500 ||
      PLACEHOLDER.test(exception.rationale) ||
      !SAFE_REFERENCE.test(exception.trackingReference ?? '') ||
      PLACEHOLDER.test(exception.trackingReference) ||
      !SHA256.test(exception.approvalEvidenceSha256 ?? '') ||
      /^0{64}$/u.test(exception.approvalEvidenceSha256) ||
      approvedAt > now.getTime() ||
      expiresAt <= now.getTime() ||
      expiresAt <= approvedAt ||
      expiresAt - approvedAt > MAX_EXCEPTION_DAYS * 24 * 60 * 60 * 1000
    ) {
      throw new Error(
        `${label} is invalid, expired, future-dated, or over-broad`,
      );
    }
    const key = [
      exception.dependencySet,
      exception.componentPurl,
      exception.queryPurl,
      exception.vulnerabilityId,
    ].join('\u0000');
    if (exceptions.has(key))
      throw new Error('native advisory exception is duplicated');
    exceptions.set(key, Object.freeze({ ...exception }));
  }
  return exceptions;
}

const checkedOsvResponse = (document, expectedCount) => {
  exactKeys(document, ['results'], 'OSV querybatch response');
  if (
    !Array.isArray(document.results) ||
    document.results.length !== expectedCount
  ) {
    throw new Error('OSV querybatch response count does not match the request');
  }
  return document.results.map((result, index) => {
    if (!isRecord(result)) throw new Error(`OSV result ${index} is malformed`);
    const allowed = new Set(['vulns', 'next_page_token']);
    if (Object.keys(result).some(key => !allowed.has(key))) {
      throw new Error(`OSV result ${index} has an unsupported field`);
    }
    const vulns = result.vulns ?? [];
    if (!Array.isArray(vulns))
      throw new Error(`OSV result ${index} is malformed`);
    const validated = vulns.map((vulnerability, vulnerabilityIndex) => {
      exactKeys(
        vulnerability,
        ['id', 'modified'],
        `OSV result ${index} vulnerability ${vulnerabilityIndex}`,
      );
      if (
        !VULNERABILITY_ID.test(vulnerability.id ?? '') ||
        typeof vulnerability.modified !== 'string' ||
        !RFC3339.test(vulnerability.modified) ||
        !Number.isFinite(Date.parse(vulnerability.modified))
      ) {
        throw new Error(`OSV result ${index} vulnerability is malformed`);
      }
      return vulnerability;
    });
    const token = result.next_page_token;
    if (
      token !== undefined &&
      (typeof token !== 'string' || !token || token.length > 2048)
    ) {
      throw new Error(`OSV result ${index} has an invalid page token`);
    }
    return { vulns: validated, nextPageToken: token ?? null };
  });
};

const retryableStatus = status => status === 429 || status >= 500;

const safeFailureDescription = error => {
  if (!(error instanceof Error)) return 'request failure';
  const causeCode = error.cause?.code;
  if (typeof causeCode === 'string' && /^[A-Z0-9_]{2,40}$/u.test(causeCode)) {
    return `${error.name}:${causeCode}`;
  }
  if (
    typeof error.message === 'string' &&
    /^[A-Za-z0-9 (),.;:'_-]{1,160}$/u.test(error.message)
  ) {
    return `${error.name}:${error.message}`;
  }
  return error.name;
};

const canonicalGitHubRepository = value => {
  if (typeof value !== 'string') return null;
  let parsed;
  try {
    parsed = new URL(value);
  } catch {
    return null;
  }
  if (
    parsed.protocol !== 'https:' ||
    parsed.hostname !== 'github.com' ||
    parsed.port ||
    parsed.username ||
    parsed.password ||
    parsed.search ||
    parsed.hash
  ) {
    return null;
  }
  const segments = parsed.pathname
    .replace(/^\//u, '')
    .replace(/\.git$/u, '')
    .split('/');
  if (
    segments.length !== 2 ||
    segments.some(segment => !/^[A-Za-z0-9_.-]+$/u.test(segment))
  ) {
    return null;
  }
  return `github.com/${segments.join('/')}`;
};

const cocoaPodsPodspecUrl = ({ podName, podVersion }) => {
  // CocoaPods' official CDN routes Specs by the first three hexadecimal
  // characters of MD5(pod name). MD5 is used only for public path routing;
  // the lock-bound podspec integrity check below is SHA-1 as CocoaPods defines.
  const routingHash = createHash('md5').update(podName).digest('hex');
  return `https://cdn.cocoapods.org/Specs/${routingHash[0]}/${routingHash[1]}/${
    routingHash[2]
  }/${encodeURIComponent(podName)}/${encodeURIComponent(
    podVersion,
  )}/${encodeURIComponent(podName)}.podspec.json`;
};

const cocoaPodsPodspecMirrorUrl = source =>
  cocoaPodsPodspecUrl(source).replace(
    'https://cdn.cocoapods.org/',
    'https://cdn.jsdelivr.net/cocoa/',
  );

const fetchVerifiedPodspec = async (
  source,
  {
    fetchImpl = globalThis.fetch,
    sleeper = milliseconds =>
      new Promise(resolve => setTimeout(resolve, milliseconds)),
  } = {},
) => {
  if (typeof fetchImpl !== 'function') throw new Error('fetch is unavailable');
  const url = cocoaPodsPodspecUrl(source);
  const mirrorUrl = cocoaPodsPodspecMirrorUrl(source);
  let lastFailure = null;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      const request = requestUrl =>
        fetchImpl(requestUrl, {
          method: 'GET',
          headers: {
            accept: 'application/json',
            'user-agent': 'birthday-autopilot-native-advisory-gate/1',
          },
          redirect: 'manual',
          credentials: 'omit',
          cache: 'no-store',
          signal: AbortSignal.timeout(20_000),
        });
      let response = await request(url);
      if ([301, 302, 307, 308].includes(response?.status)) {
        const location = response.headers?.get?.('location');
        let resolvedLocation = null;
        try {
          resolvedLocation = new URL(location, url).href;
        } catch {
          // The explicit comparison below rejects missing or malformed targets.
        }
        if (resolvedLocation !== mirrorUrl) {
          throw new Error('CocoaPods CDN redirect is not trusted');
        }
        response = await request(mirrorUrl);
      }
      if (!response || typeof response.status !== 'number') {
        throw new Error('CocoaPods CDN returned no HTTP response');
      }
      if (response.status !== 200) {
        if (!retryableStatus(response.status)) {
          throw new Error(
            `CocoaPods CDN rejected the podspec with HTTP ${response.status}`,
          );
        }
        lastFailure = new Error(
          `CocoaPods CDN was unavailable with HTTP ${response.status}`,
        );
      } else {
        const contentType = response.headers?.get?.('content-type') ?? '';
        const contentLength = response.headers?.get?.('content-length');
        if (!/^application\/json(?:;|$)/iu.test(contentType)) {
          throw new Error('CocoaPods CDN returned an unexpected content type');
        }
        if (
          contentLength !== null &&
          (!/^\d+$/u.test(contentLength) ||
            Number(contentLength) > 2 * 1024 * 1024)
        ) {
          throw new Error('CocoaPods podspec response size is invalid');
        }
        const bytes = Buffer.from(await response.arrayBuffer());
        if (
          bytes.length === 0 ||
          bytes.length > 2 * 1024 * 1024 ||
          createHash('sha1').update(bytes).digest('hex') !== source.podspecSha1
        ) {
          throw new Error(
            'CocoaPods podspec bytes do not match the lock checksum',
          );
        }
        const podspec = parseJson(bytes, 'CocoaPods podspec');
        const repository = canonicalGitHubRepository(podspec.source?.git);
        if (
          podspec.name !== source.podName ||
          podspec.version !== source.podVersion ||
          repository?.toLowerCase() !== source.sourceRepository.toLowerCase() ||
          podspec.source?.tag !== source.sourceTag
        ) {
          throw new Error(
            'CocoaPods podspec source identity does not match the reviewed OSV mapping',
          );
        }
        return {
          podName: source.podName,
          podVersion: source.podVersion,
          podspecSha1: source.podspecSha1,
          sourceRepository: source.sourceRepository,
          sourceTag: source.sourceTag,
          queryPurl: source.queryPurl,
        };
      }
    } catch (error) {
      if (
        error instanceof Error &&
        /rejected the podspec|redirect is not trusted|unexpected content type|response size|do not match/u.test(
          error.message,
        )
      ) {
        throw error;
      }
      lastFailure =
        error instanceof Error ? error : new Error('podspec request failed');
    }
    if (attempt < 2) await sleeper(250 * 2 ** attempt);
  }
  throw new Error(
    `CocoaPods source verification unavailable; release remains blocked (${safeFailureDescription(
      lastFailure,
    )})`,
  );
};

export async function verifyCocoaPodsPodspecSources(sources, options = {}) {
  if (!Array.isArray(sources)) {
    throw new Error('CocoaPods podspec source set is invalid');
  }
  const unique = new Map();
  for (const source of sources) {
    if (
      !isRecord(source) ||
      typeof source.podName !== 'string' ||
      typeof source.podVersion !== 'string' ||
      !SHA1.test(source.podspecSha1 ?? '') ||
      !SAFE_SOURCE_REPOSITORY.test(source.sourceRepository ?? '') ||
      !SAFE_SOURCE_TAG.test(source.sourceTag ?? '')
    ) {
      throw new Error('CocoaPods podspec source set is malformed');
    }
    if (unique.has(source.podName)) {
      throw new Error('CocoaPods podspec source set contains a duplicate pod');
    }
    unique.set(source.podName, source);
  }
  const verified = [];
  const values = [...unique.values()].sort((left, right) =>
    left.podName.localeCompare(right.podName, 'en'),
  );
  for (let offset = 0; offset < values.length; offset += 8) {
    verified.push(
      ...(await Promise.all(
        values
          .slice(offset, offset + 8)
          .map(source => fetchVerifiedPodspec(source, options)),
      )),
    );
  }
  return verified;
}

export async function requestOsvBatch(
  queries,
  {
    fetchImpl = globalThis.fetch,
    sleeper = milliseconds =>
      new Promise(resolve => setTimeout(resolve, milliseconds)),
  } = {},
) {
  if (
    !Array.isArray(queries) ||
    queries.length === 0 ||
    queries.length > MAX_QUERY_BATCH
  ) {
    throw new Error('OSV query batch size is invalid');
  }
  if (typeof fetchImpl !== 'function') throw new Error('fetch is unavailable');
  const request = {
    queries: queries.map(query => ({
      package: { purl: query.purl },
      ...(query.pageToken ? { page_token: query.pageToken } : {}),
    })),
  };
  let lastFailure = null;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      const response = await fetchImpl(OSV_API_ENDPOINT, {
        method: 'POST',
        headers: {
          accept: 'application/json',
          'content-type': 'application/json',
          'user-agent': 'birthday-autopilot-native-advisory-gate/1',
        },
        body: JSON.stringify(request),
        redirect: 'error',
        credentials: 'omit',
        cache: 'no-store',
        signal: AbortSignal.timeout(20_000),
      });
      if (!response || typeof response.status !== 'number') {
        throw new Error('OSV returned no HTTP response');
      }
      if (response.status !== 200) {
        if (!retryableStatus(response.status)) {
          throw new Error(
            `OSV rejected the query with HTTP ${response.status}`,
          );
        }
        lastFailure = new Error(
          `OSV was unavailable with HTTP ${response.status}`,
        );
      } else {
        const contentType = response.headers?.get?.('content-type') ?? '';
        const contentLength = response.headers?.get?.('content-length');
        if (!/^application\/json(?:;|$)/iu.test(contentType)) {
          throw new Error('OSV returned an unexpected content type');
        }
        if (
          contentLength !== null &&
          (!/^\d+$/u.test(contentLength) ||
            Number(contentLength) > MAX_RESPONSE_BYTES)
        ) {
          throw new Error('OSV response size is invalid');
        }
        const bytes = Buffer.from(await response.arrayBuffer());
        if (bytes.length === 0 || bytes.length > MAX_RESPONSE_BYTES) {
          throw new Error('OSV response size is invalid');
        }
        return checkedOsvResponse(
          parseJson(bytes, 'OSV response'),
          queries.length,
        );
      }
    } catch (error) {
      if (
        error instanceof Error &&
        (/OSV rejected/u.test(error.message) ||
          /unexpected content type|response size|querybatch response|OSV result/u.test(
            error.message,
          ))
      ) {
        throw error;
      }
      lastFailure =
        error instanceof Error ? error : new Error('OSV request failed');
    }
    if (attempt < 2) await sleeper(250 * 2 ** attempt);
  }
  throw new Error(
    `OSV advisory service unavailable; release remains blocked (${safeFailureDescription(
      lastFailure,
    )})`,
  );
}

export async function queryOsvPurls(
  purls,
  { requestBatch = requestOsvBatch } = {},
) {
  const uniquePurls = [...new Set(purls)].sort((left, right) =>
    left.localeCompare(right, 'en'),
  );
  if (uniquePurls.length === 0)
    throw new Error('no package URLs were selected for OSV');
  const findings = new Map(uniquePurls.map(purl => [purl, new Map()]));
  let pending = uniquePurls.map(purl => ({ purl, pageToken: null }));
  const seenTokens = new Set();
  let page = 0;
  let requestCount = 0;
  while (pending.length > 0) {
    page += 1;
    if (page > MAX_PAGES)
      throw new Error('OSV pagination exceeded the safety bound');
    const next = [];
    for (let offset = 0; offset < pending.length; offset += MAX_QUERY_BATCH) {
      const batch = pending.slice(offset, offset + MAX_QUERY_BATCH);
      const results = await requestBatch(batch);
      requestCount += 1;
      if (!Array.isArray(results) || results.length !== batch.length) {
        throw new Error('OSV query adapter returned an invalid result count');
      }
      for (let index = 0; index < batch.length; index += 1) {
        const query = batch[index];
        const result = results[index];
        if (
          !isRecord(result) ||
          !Array.isArray(result.vulns) ||
          !Object.prototype.hasOwnProperty.call(result, 'nextPageToken')
        ) {
          throw new Error('OSV query adapter returned a malformed result');
        }
        const matches = findings.get(query.purl);
        for (const vulnerability of result.vulns) {
          if (
            !isRecord(vulnerability) ||
            !VULNERABILITY_ID.test(vulnerability.id ?? '') ||
            typeof vulnerability.modified !== 'string' ||
            !RFC3339.test(vulnerability.modified)
          ) {
            throw new Error(
              'OSV query adapter returned a malformed vulnerability',
            );
          }
          const prior = matches.get(vulnerability.id);
          if (prior !== undefined && prior !== vulnerability.modified) {
            throw new Error('OSV returned inconsistent advisory revisions');
          }
          matches.set(vulnerability.id, vulnerability.modified);
        }
        if (result.nextPageToken !== null) {
          if (
            typeof result.nextPageToken !== 'string' ||
            !result.nextPageToken ||
            result.nextPageToken.length > 2048
          ) {
            throw new Error('OSV query adapter returned an invalid page token');
          }
          const tokenKey = `${query.purl}\u0000${result.nextPageToken}`;
          if (seenTokens.has(tokenKey))
            throw new Error('OSV pagination token repeated');
          seenTokens.add(tokenKey);
          next.push({ purl: query.purl, pageToken: result.nextPageToken });
        }
      }
    }
    pending = next;
  }
  return { findings, requestCount };
}

export async function buildNativeAdvisoryReport({
  targets,
  exceptionDocument,
  exceptionBytes,
  detachedExceptionSignature = null,
  exceptionAuthorityPublicKeyBytes = null,
  authorityPinDocument = null,
  now = new Date(),
  requestBatch = requestOsvBatch,
  verifyPodspecSources = verifyCocoaPodsPodspecSources,
}) {
  if (!Array.isArray(targets) || targets.length === 0) {
    throw new Error('prepared native dependency sets are required');
  }
  const exceptions = validateExceptions(exceptionDocument, now);
  let exceptionAuthorityPublicKeySpkiSha256 = null;
  if (exceptions.size > 0) {
    if (
      !Buffer.isBuffer(detachedExceptionSignature) ||
      !Buffer.isBuffer(exceptionAuthorityPublicKeyBytes) ||
      !isRecord(authorityPinDocument)
    ) {
      throw new Error(
        'ordinary native advisory scans require zero exceptions; any exception requires detached authority approval',
      );
    }
    const authority = verifyDistributionEvidenceAuthority({
      rawEvidence: exceptionBytes,
      detachedSignature: detachedExceptionSignature,
      publicKeyBytes: exceptionAuthorityPublicKeyBytes,
      pinDocument: authorityPinDocument,
    });
    if (authority.errors.length > 0) {
      throw new Error(
        `native advisory exception authority rejected: ${authority.errors[0]}`,
      );
    }
    exceptionAuthorityPublicKeySpkiSha256 = authority.publicKeySpkiSha256;
  } else if (
    detachedExceptionSignature !== null ||
    exceptionAuthorityPublicKeyBytes !== null ||
    authorityPinDocument !== null
  ) {
    throw new Error(
      'authority inputs are forbidden when there are no exceptions',
    );
  }
  const podspecSources = targets.flatMap(target =>
    target.componentMappings.filter(
      mapping => mapping.mappingKind === 'checksum-bound-trunk-source',
    ),
  );
  const verifiedPodspecSources =
    podspecSources.length === 0
      ? []
      : await verifyPodspecSources(podspecSources);
  if (verifiedPodspecSources.length !== podspecSources.length) {
    throw new Error('CocoaPods source verification result is incomplete');
  }
  const allQueryPurls = targets.flatMap(target =>
    target.componentMappings.map(mapping => mapping.queryPurl),
  );
  const canaryResult = await queryOsvPurls(
    CANARIES.map(canary => canary.purl),
    { requestBatch },
  );
  for (const canary of CANARIES) {
    if (!canaryResult.findings.get(canary.purl)?.has(canary.vulnerabilityId)) {
      throw new Error(
        `OSV ${canary.purl
          .split('/')[0]
          .slice(4)} ecosystem canary did not match; release remains blocked`,
      );
    }
  }
  const queryResult = await queryOsvPurls(allQueryPurls, { requestBatch });
  const usedExceptions = new Set();
  const findings = [];
  for (const target of targets) {
    for (const mapping of target.componentMappings) {
      for (const [vulnerabilityId, modified] of queryResult.findings.get(
        mapping.queryPurl,
      )) {
        const key = [
          target.label,
          mapping.componentPurl,
          mapping.queryPurl,
          vulnerabilityId,
        ].join('\u0000');
        const exception = exceptions.get(key) ?? null;
        if (exception !== null) usedExceptions.add(key);
        findings.push({
          dependencySet: target.label,
          componentPurl: mapping.componentPurl,
          queryPurl: mapping.queryPurl,
          mappingKind: mapping.mappingKind,
          vulnerabilityId,
          modified,
          exception:
            exception === null
              ? null
              : {
                  owner: exception.owner,
                  approvedBy: exception.approvedBy,
                  rationale: exception.rationale,
                  trackingReference: exception.trackingReference,
                  approvalEvidenceSha256: exception.approvalEvidenceSha256,
                  approvedAt: exception.approvedAt,
                  expiresAt: exception.expiresAt,
                },
        });
      }
    }
  }
  if (usedExceptions.size !== exceptions.size) {
    throw new Error(
      'native advisory exception is stale, unmatched, or out of scope',
    );
  }
  findings.sort((left, right) =>
    [left.dependencySet, left.componentPurl, left.vulnerabilityId]
      .join('\u0000')
      .localeCompare(
        [right.dependencySet, right.componentPurl, right.vulnerabilityId].join(
          '\u0000',
        ),
        'en',
      ),
  );
  const unresolvedCount = findings.filter(
    finding => finding.exception === null,
  ).length;
  const componentCount = targets.reduce(
    (total, target) => total + target.componentCount,
    0,
  );
  return {
    schemaVersion: 1,
    scanner: {
      name: 'birthday-native-osv-api-gate',
      implementationVersion: 1,
      provider: 'OSV.dev',
      apiEndpoint: OSV_API_ENDPOINT,
      apiContract: 'POST /v1/querybatch',
      scannedAt: now.toISOString(),
      policy: 'block-every-active-osv-match-unless-exact-unexpired-exception',
      canaries: CANARIES,
      requestCount: canaryResult.requestCount + queryResult.requestCount,
    },
    exceptionPolicy: {
      path: 'tools/native-advisory-exceptions.json',
      sha256: sha256(exceptionBytes),
      configuredCount: exceptions.size,
      appliedCount: usedExceptions.size,
      maximumLifetimeDays: MAX_EXCEPTION_DAYS,
      authorityPublicKeySpkiSha256: exceptionAuthorityPublicKeySpkiSha256,
    },
    dependencySets: targets.map(target => ({
      label: target.label,
      kind: target.kind,
      configuration: target.configuration,
      lockfileSha256: target.lockfileSha256,
      sbomSha256: target.sbomSha256,
      componentCount: target.componentCount,
      queryIdentityCount: new Set(
        target.componentMappings.map(mapping => mapping.queryPurl),
      ).size,
      verifiedPodspecSourceCount: target.componentMappings.filter(
        mapping => mapping.mappingKind === 'checksum-bound-trunk-source',
      ).length,
      ...(target.sourceMapSha256
        ? {
            sourceMapSha256: target.sourceMapSha256,
            npmLockSha256: target.npmLockSha256,
          }
        : {}),
    })),
    findings,
    summary: {
      status: unresolvedCount === 0 ? 'pass' : 'blocked',
      dependencySetCount: targets.length,
      componentCount,
      uniqueQueryIdentityCount: new Set(allQueryPurls).size,
      findingCount: findings.length,
      exceptedCount: usedExceptions.size,
      unresolvedCount,
    },
  };
}

const assertNoSymlinkSegments = (root, output) => {
  const relative = path.relative(root, output);
  let current = root;
  for (const segment of ['', ...relative.split(path.sep)]) {
    if (segment) current = path.join(current, segment);
    try {
      if (lstatSync(current).isSymbolicLink()) {
        throw new Error(
          'native advisory output path must not contain symbolic links',
        );
      }
    } catch (error) {
      if (
        error instanceof Error &&
        'code' in error &&
        error.code === 'ENOENT'
      ) {
        continue;
      }
      throw error;
    }
  }
};

export function writeNativeAdvisoryReport(
  outputArgument,
  report,
  root = projectRoot,
) {
  if (typeof outputArgument !== 'string' || !outputArgument) {
    throw new Error('native advisory output path is required');
  }
  const evidenceRoot = path.resolve(root, 'release-evidence');
  const output = path.resolve(root, outputArgument);
  const relative = path.relative(evidenceRoot, output);
  if (
    relative === '' ||
    relative.startsWith('..') ||
    path.isAbsolute(relative)
  ) {
    throw new Error('native advisory output must be inside release-evidence/');
  }
  assertNoSymlinkSegments(evidenceRoot, output);
  writeFileSync(output, `${JSON.stringify(report, null, 2)}\n`, {
    encoding: 'utf8',
    flag: 'wx',
    mode: 0o600,
  });
  return output;
}

export function parseArguments(args) {
  const targetSpecs = [];
  let output = null;
  let exceptionSignature = null;
  let exceptionPublicKey = null;
  for (let index = 0; index < args.length; ) {
    if (args[index] === '--dependency-set') {
      if (index + 4 >= args.length) {
        throw new Error(
          'usage: --dependency-set <label> <gradle|cocoapods> <lock> <sbom>',
        );
      }
      targetSpecs.push({
        label: args[index + 1],
        kind: args[index + 2],
        lockPath: args[index + 3],
        sbomPath: args[index + 4],
      });
      index += 5;
    } else if (args[index] === '--output') {
      if (output !== null || !args[index + 1]) {
        throw new Error('--output must be specified exactly once');
      }
      output = args[index + 1];
      index += 2;
    } else if (args[index] === '--exception-signature') {
      if (exceptionSignature !== null || !args[index + 1]) {
        throw new Error('--exception-signature must be specified at most once');
      }
      exceptionSignature = args[index + 1];
      index += 2;
    } else if (args[index] === '--exception-public-key') {
      if (exceptionPublicKey !== null || !args[index + 1]) {
        throw new Error(
          '--exception-public-key must be specified at most once',
        );
      }
      exceptionPublicKey = args[index + 1];
      index += 2;
    } else {
      throw new Error(`unsupported argument ${args[index] ?? '<missing>'}`);
    }
  }
  if (output === null || targetSpecs.length === 0 || targetSpecs.length > 8) {
    throw new Error(
      'native advisory scan requires output and 1-8 dependency sets',
    );
  }
  if ((exceptionSignature === null) !== (exceptionPublicKey === null)) {
    throw new Error(
      'exception signature and public key must be supplied together',
    );
  }
  return { targetSpecs, output, exceptionSignature, exceptionPublicKey };
}

const direct =
  process.argv[1] !== undefined &&
  fileURLToPath(import.meta.url) === path.resolve(process.argv[1]);

if (direct) {
  try {
    const { targetSpecs, output, exceptionSignature, exceptionPublicKey } =
      parseArguments(process.argv.slice(2));
    const exceptionBytes = readStableRegularFile(
      exceptionPath,
      'native advisory exceptions',
    );
    const targets = prepareNativeTargets(targetSpecs);
    const detachedExceptionSignature =
      exceptionSignature === null
        ? null
        : readStableRegularFile(
            path.resolve(exceptionSignature),
            'native advisory exception signature',
            64,
          );
    const exceptionAuthorityPublicKeyBytes =
      exceptionPublicKey === null
        ? null
        : readStableRegularFile(
            path.resolve(exceptionPublicKey),
            'native advisory exception authority public key',
            8 * 1024,
          );
    const authorityPinDocument =
      exceptionSignature === null
        ? null
        : parseJson(
            readStableRegularFile(
              authorityPinPath,
              'distribution authority pin',
              1024,
            ),
            'distribution authority pin',
          );
    const report = await buildNativeAdvisoryReport({
      targets,
      exceptionDocument: parseJson(
        exceptionBytes,
        'native advisory exceptions',
      ),
      exceptionBytes,
      detachedExceptionSignature,
      exceptionAuthorityPublicKeyBytes,
      authorityPinDocument,
    });
    writeNativeAdvisoryReport(output, report);
    if (report.summary.status !== 'pass') {
      process.stderr.write(
        `FAIL native advisory gate found ${report.summary.unresolvedCount} unresolved finding(s); report retained at ${output}\n`,
      );
      process.exitCode = 1;
    } else {
      process.stdout.write(
        `PASS native advisory gate (${report.summary.componentCount} components, ${report.summary.findingCount} findings, ${report.summary.exceptedCount} exceptions)\n`,
      );
    }
  } catch (error) {
    process.stderr.write(
      `FAIL ${
        error instanceof Error ? error.message : 'native advisory scan failed'
      }\n`,
    );
    process.exitCode = 1;
  }
}
