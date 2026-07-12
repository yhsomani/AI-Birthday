#!/usr/bin/env node
import { createHash } from 'node:crypto';
import { lstatSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const projectRoot = fileURLToPath(new URL('../', import.meta.url));
const registryOrigin = 'https://registry.npmjs.org';
const integrityByteLengths = new Map([
  ['256', 32],
  ['384', 48],
  ['512', 64],
]);

const lockfiles = Object.freeze([
  Object.freeze({
    label: 'mobile',
    path: 'package-lock.json',
    rootName: 'birthday-autopilot',
    rootVersion: '0.1.0',
    packageCount: 927,
    componentCount: 926,
  }),
  Object.freeze({
    label: 'functions',
    path: 'backend/functions/package-lock.json',
    rootName: 'birthday-autopilot-control-plane',
    rootVersion: '0.1.0',
    packageCount: 948,
    componentCount: 947,
  }),
  Object.freeze({
    label: 'hosting',
    path: 'backend/hosting/package-lock.json',
    rootName: 'birthday-autopilot-public-site',
    rootVersion: '0.1.0',
    packageCount: 133,
    componentCount: 132,
  }),
]);

const allowedLicenses = new Set([
  '0BSD',
  'Apache-2.0',
  'BSD',
  'BSD-2-Clause',
  'BSD-3-Clause',
  'BlueOak-1.0.0',
  'CC-BY-4.0',
  'CC0-1.0',
  'ISC',
  'MIT',
  'MPL-2.0',
  'Python-2.0',
  'public domain',
  '(BSD-2-Clause OR MIT OR Apache-2.0)',
  '(MIT OR Apache-2.0)',
  '(MIT OR CC0-1.0)',
]);

const missingMetadataReviews = new Map([
  [
    'exit@0.1.2',
    Object.freeze({
      license: 'MIT',
      licenseFile: 'LICENSE-MIT',
      licenseSha256:
        '65bd93f75d6c0cdc1c9e1a39bd1814e2e34355c665e1564a1517f27c1523ab7e',
    }),
  ],
  [
    'fuzzy@0.1.3',
    Object.freeze({
      license: 'MIT',
      licenseFile: 'LICENSE-MIT',
      licenseSha256:
        'a4aca837172fb1f6188c426e38835202e96bc853e25b7029cc5c933964f8401e',
    }),
  ],
  [
    'limiter@1.1.5',
    Object.freeze({
      license: 'MIT',
      licenseFile: 'LICENSE.txt',
      licenseSha256:
        'a3aebd11ea5598ef12949bf793311bf155ab7727181e3d373bd0b47813d41111',
    }),
  ],
  [
    'valid-url@1.0.9',
    Object.freeze({
      license: 'MIT',
      licenseFile: 'LICENSE',
      licenseSha256:
        'c48a681e53bfcd0a2b3ee2ea476e6d031fe7563f9eaa68f763bce0e3fb279a46',
    }),
  ],
]);

function sha256(content) {
  return createHash('sha256').update(content).digest('hex');
}

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function packageNameFromPath(packagePath) {
  if (
    typeof packagePath !== 'string' ||
    packagePath.includes('\\') ||
    packagePath.split('/').includes('..')
  ) {
    return null;
  }
  const marker = 'node_modules/';
  const markerIndex = packagePath.lastIndexOf(marker);
  if (markerIndex < 0) return null;
  const suffix = packagePath.slice(markerIndex + marker.length);
  const parts = suffix.split('/');
  if (parts[0]?.startsWith('@')) {
    if (parts.length !== 2 || parts[0].length === 1 || !parts[1]) return null;
    return parts.join('/');
  }
  if (parts.length !== 1 || !parts[0]) return null;
  return parts[0];
}

function readLockfile(lockfilePath, displayPath) {
  let bytes;
  try {
    bytes = readFileSync(lockfilePath);
  } catch (error) {
    throw new Error(
      `${displayPath}: cannot read lockfile: ${
        error instanceof Error ? error.message : 'unknown read failure'
      }`,
    );
  }

  let document;
  try {
    document = JSON.parse(bytes.toString('utf8'));
  } catch (error) {
    throw new Error(
      `${displayPath}: malformed or truncated JSON: ${
        error instanceof Error ? error.message : 'JSON parse failure'
      }`,
    );
  }

  if (!isRecord(document)) {
    throw new Error(`${displayPath}: lockfile root must be an object`);
  }
  return { bytes, document };
}

function validateRegistryResolution(displayPath, identity, resolved) {
  if (typeof resolved !== 'string' || resolved.length === 0) {
    throw new Error(`${displayPath}: ${identity} has no resolved package URL`);
  }

  let parsed;
  try {
    parsed = new URL(resolved);
  } catch {
    throw new Error(`${displayPath}: ${identity} has a malformed resolved URL`);
  }

  if (
    parsed.origin !== registryOrigin ||
    parsed.username !== '' ||
    parsed.password !== '' ||
    parsed.search !== '' ||
    parsed.hash !== ''
  ) {
    throw new Error(
      `${displayPath}: ${identity} must resolve from the credential-free npm registry origin`,
    );
  }
}

function validateIntegrity(displayPath, identity, integrity) {
  if (typeof integrity !== 'string' || integrity.length === 0) {
    throw new Error(`${displayPath}: ${identity} has no integrity metadata`);
  }
  const match = /^sha(256|384|512)-([A-Za-z0-9+/]+={0,2})$/u.exec(integrity);
  if (
    !match ||
    Buffer.from(match[2], 'base64').byteLength !==
      integrityByteLengths.get(match[1])
  ) {
    throw new Error(
      `${displayPath}: ${identity} has malformed integrity metadata`,
    );
  }
}

function validateReviewedLicense(
  packageDirectory,
  identity,
  review,
  displayPath,
) {
  const licensePath = path.join(packageDirectory, review.licenseFile);
  let fileMetadata;
  let content;
  try {
    fileMetadata = lstatSync(licensePath);
    content = readFileSync(licensePath);
  } catch {
    throw new Error(
      `${displayPath}: ${identity} is missing its reviewed ${review.licenseFile}`,
    );
  }
  if (fileMetadata.isSymbolicLink() || !fileMetadata.isFile()) {
    throw new Error(
      `${displayPath}: ${identity} reviewed license must be a regular file`,
    );
  }
  if (sha256(content) !== review.licenseSha256) {
    throw new Error(
      `${displayPath}: ${identity} reviewed license SHA256 does not match the approved MIT text`,
    );
  }
}

export function buildJavaScriptLicenseEvidence(root = projectRoot) {
  const resolvedRoot = path.resolve(root);
  const components = [];
  const dependencySets = [];

  for (const lockfile of lockfiles) {
    const lockfilePath = path.join(resolvedRoot, lockfile.path);
    const lockfileRoot = path.dirname(lockfilePath);
    const { bytes, document } = readLockfile(lockfilePath, lockfile.path);

    if (document.lockfileVersion !== 3) {
      throw new Error(`${lockfile.path}: lockfileVersion must be exactly 3`);
    }
    if (
      document.name !== lockfile.rootName ||
      document.version !== lockfile.rootVersion
    ) {
      throw new Error(
        `${lockfile.path}: expected root identity ${lockfile.rootName}@${lockfile.rootVersion}`,
      );
    }
    if (!isRecord(document.packages)) {
      throw new Error(`${lockfile.path}: packages must be an object`);
    }

    const packageEntries = Object.entries(document.packages);
    if (packageEntries.length !== lockfile.packageCount) {
      throw new Error(
        `${lockfile.path}: expected ${lockfile.packageCount} package entries, found ${packageEntries.length}; lockfile may be truncated or unreviewed`,
      );
    }

    const rootPackage = document.packages[''];
    if (
      !isRecord(rootPackage) ||
      rootPackage.name !== lockfile.rootName ||
      rootPackage.version !== lockfile.rootVersion
    ) {
      throw new Error(
        `${lockfile.path}: packages[''] must be ${lockfile.rootName}@${lockfile.rootVersion}`,
      );
    }

    const componentEntries = packageEntries.filter(
      ([packagePath]) => packagePath,
    );
    if (componentEntries.length !== lockfile.componentCount) {
      throw new Error(
        `${lockfile.path}: expected ${lockfile.componentCount} component entries, found ${componentEntries.length}`,
      );
    }

    for (const [packagePath, metadata] of componentEntries) {
      if (!isRecord(metadata)) {
        throw new Error(
          `${lockfile.path}: ${packagePath} metadata is malformed`,
        );
      }
      if (
        typeof metadata.version !== 'string' ||
        metadata.version.length === 0
      ) {
        throw new Error(
          `${lockfile.path}: ${packagePath} has no valid version`,
        );
      }
      const name = packageNameFromPath(packagePath);
      if (!name) {
        throw new Error(
          `${lockfile.path}: cannot derive package name from ${packagePath}`,
        );
      }
      const identity = `${name}@${metadata.version}`;
      validateIntegrity(lockfile.path, identity, metadata.integrity);
      validateRegistryResolution(lockfile.path, identity, metadata.resolved);

      let license = metadata.license;
      let reviewedLicenseFile = null;
      let reviewedLicenseSha256 = null;

      if (license === undefined) {
        const review = missingMetadataReviews.get(identity);
        if (!review) {
          throw new Error(
            `${lockfile.path}: ${identity} has no license metadata`,
          );
        }
        validateReviewedLicense(
          path.join(lockfileRoot, packagePath),
          identity,
          review,
          lockfile.path,
        );
        license = review.license;
        reviewedLicenseFile = review.licenseFile;
        reviewedLicenseSha256 = review.licenseSha256;
      } else if (typeof license !== 'string' || license.length === 0) {
        throw new Error(
          `${lockfile.path}: ${identity} has malformed license metadata`,
        );
      }

      if (!allowedLicenses.has(license)) {
        throw new Error(
          `${lockfile.path}: ${identity} uses unapproved license ${license}`,
        );
      }

      components.push({
        dependencySet: lockfile.label,
        developmentOnly: metadata.dev === true,
        identity,
        integrity: metadata.integrity,
        license,
        reviewedLicenseFile,
        reviewedLicenseSha256,
      });
    }

    dependencySets.push({
      componentCount: lockfile.componentCount,
      label: lockfile.label,
      lockfile: lockfile.path,
      lockfilePackageCount: lockfile.packageCount,
      lockfileSha256: sha256(bytes),
      registryOrigin,
      rootIdentity: `${lockfile.rootName}@${lockfile.rootVersion}`,
    });
  }

  components.sort((left, right) =>
    `${left.dependencySet}:${left.identity}`.localeCompare(
      `${right.dependencySet}:${right.identity}`,
      'en',
    ),
  );

  return Object.freeze({
    schemaVersion: 2,
    scope:
      'JavaScript npm lockfiles only; CocoaPods, Gradle, OS SDK, and artifact notices require their separate release evidence.',
    dependencySets,
    components,
  });
}

function assertNoSymlinksInOutputPath(evidenceRoot, output) {
  const relative = path.relative(evidenceRoot, output);
  let current = evidenceRoot;
  const segments = relative.split(path.sep);

  for (const segment of ['', ...segments]) {
    if (segment) current = path.join(current, segment);
    try {
      const metadata = lstatSync(current);
      if (metadata.isSymbolicLink()) {
        throw new Error(
          'license evidence output path must not contain symbolic links',
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
}

export function resolveJavaScriptLicenseOutput(
  outputArgument,
  root = projectRoot,
) {
  if (typeof outputArgument !== 'string' || outputArgument.length === 0) {
    throw new Error('license evidence output path is required');
  }
  const resolvedRoot = path.resolve(root);
  const evidenceRoot = path.join(resolvedRoot, 'release-evidence');
  const output = path.resolve(resolvedRoot, outputArgument);
  const relative = path.relative(evidenceRoot, output);
  if (
    relative === '' ||
    relative.startsWith('..') ||
    path.isAbsolute(relative)
  ) {
    throw new Error('license evidence output must be inside release-evidence/');
  }
  assertNoSymlinksInOutputPath(evidenceRoot, output);
  return output;
}

export function writeJavaScriptLicenseEvidence(
  outputArgument,
  evidence,
  root = projectRoot,
) {
  const output = resolveJavaScriptLicenseOutput(outputArgument, root);
  writeFileSync(output, `${JSON.stringify(evidence, null, 2)}\n`, {
    encoding: 'utf8',
    flag: 'wx',
  });
  return output;
}

function parseOutputArgument(args) {
  if (args.length === 0) return null;
  if (args.length !== 2 || args[0] !== '--output' || !args[1]) {
    throw new Error('usage: verify-javascript-licenses.mjs [--output <json>]');
  }
  return args[1];
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  try {
    const evidence = buildJavaScriptLicenseEvidence();
    const outputArgument = parseOutputArgument(process.argv.slice(2));
    if (outputArgument) {
      writeJavaScriptLicenseEvidence(outputArgument, evidence);
    }
    process.stdout.write(
      `PASS JavaScript license policy (${evidence.components.length} locked package entries)\n`,
    );
  } catch (error) {
    process.stderr.write(
      `FAIL ${
        error instanceof Error ? error.message : 'license verification failed'
      }\n`,
    );
    process.exit(1);
  }
}
