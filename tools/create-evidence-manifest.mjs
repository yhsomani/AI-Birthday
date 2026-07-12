#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import {
  closeSync,
  constants,
  fstatSync,
  lstatSync,
  openSync,
  readFileSync,
  readdirSync,
  readlinkSync,
  realpathSync,
  writeFileSync,
} from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const projectRoot = fileURLToPath(new URL('../', import.meta.url));

const sha256 = value => createHash('sha256').update(value).digest('hex');

const SOURCE_REVISION = /^[0-9a-f]{40}$/u;
const RFC3339_INSTANT =
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/u;
const VERSION = /^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$/u;
const SAFE_BUILDER_VALUE = /^[A-Za-z0-9][A-Za-z0-9 ._/@:#-]{0,511}$/u;

const portablePath = value => value.split(path.sep).join('/');

const checkedCommand = (binary, args, cwd) => {
  const result = spawnSync(binary, args, {
    cwd,
    encoding: 'utf8',
    maxBuffer: 1024 * 1024,
  });
  if (result.status !== 0) {
    throw new Error(`cannot collect evidence provenance from ${binary}`);
  }
  return `${result.stdout ?? ''}${result.stderr ?? ''}`.trim();
};

const safeBuilderValue = (value, fallback) =>
  typeof value === 'string' && SAFE_BUILDER_VALUE.test(value)
    ? value
    : fallback;

export function collectEvidenceProvenance(base = projectRoot) {
  const resolvedBase = realpathSync(path.resolve(base));
  const repositoryRoot = realpathSync(
    checkedCommand('git', ['rev-parse', '--show-toplevel'], resolvedBase),
  );
  if (repositoryRoot !== resolvedBase) {
    throw new Error('evidence base must be the Git repository root');
  }
  const sourceRevision = checkedCommand(
    'git',
    ['rev-parse', '--verify', 'HEAD'],
    resolvedBase,
  );
  if (!SOURCE_REVISION.test(sourceRevision)) {
    throw new Error('evidence source revision is invalid');
  }
  if (
    process.env.GITHUB_SHA !== undefined &&
    process.env.GITHUB_SHA !== sourceRevision
  ) {
    throw new Error('GitHub source revision does not match checked-out HEAD');
  }
  const sourceCommittedAt = checkedCommand(
    'git',
    ['show', '-s', '--format=%cI', sourceRevision],
    resolvedBase,
  );
  const npmVersion = checkedCommand('npm', ['--version'], resolvedBase);
  const nodeVersion = process.version.replace(/^v/u, '');
  if (
    !RFC3339_INSTANT.test(sourceCommittedAt) ||
    !VERSION.test(nodeVersion) ||
    !VERSION.test(npmVersion)
  ) {
    throw new Error('evidence toolchain provenance is invalid');
  }

  const githubActions = process.env.GITHUB_ACTIONS === 'true';
  return Object.freeze({
    sourceRevision,
    sourceCommittedAt,
    builder: Object.freeze({
      kind: githubActions ? 'github-actions' : 'local',
      platform: process.platform,
      architecture: process.arch,
      nodeVersion,
      npmVersion,
      ...(githubActions
        ? {
            workflowRef: safeBuilderValue(
              process.env.GITHUB_WORKFLOW_REF,
              'github-workflow-ref-unavailable',
            ),
            runId: safeBuilderValue(
              process.env.GITHUB_RUN_ID,
              'github-run-id-unavailable',
            ),
            runAttempt: safeBuilderValue(
              process.env.GITHUB_RUN_ATTEMPT,
              'github-run-attempt-unavailable',
            ),
          }
        : {}),
    }),
  });
}

const validateProvenance = provenance => {
  if (
    provenance === null ||
    typeof provenance !== 'object' ||
    !SOURCE_REVISION.test(provenance.sourceRevision ?? '') ||
    !RFC3339_INSTANT.test(provenance.sourceCommittedAt ?? '') ||
    provenance.builder === null ||
    typeof provenance.builder !== 'object' ||
    !['github-actions', 'local'].includes(provenance.builder.kind) ||
    !SAFE_BUILDER_VALUE.test(provenance.builder.platform ?? '') ||
    !SAFE_BUILDER_VALUE.test(provenance.builder.architecture ?? '') ||
    !VERSION.test(provenance.builder.nodeVersion ?? '') ||
    !VERSION.test(provenance.builder.npmVersion ?? '')
  ) {
    throw new Error('evidence provenance is invalid');
  }
  if (
    provenance.builder.kind === 'github-actions' &&
    !['workflowRef', 'runId', 'runAttempt'].every(field =>
      SAFE_BUILDER_VALUE.test(provenance.builder[field] ?? ''),
    )
  ) {
    throw new Error('GitHub evidence provenance is incomplete');
  }
};

const portableMode = metadata =>
  Number(metadata.mode % 0o10000n)
    .toString(8)
    .padStart(4, '0');

const STABLE_METADATA_FIELDS = Object.freeze([
  'dev',
  'ino',
  'mode',
  'nlink',
  'size',
  'mtimeNs',
  'ctimeNs',
]);

const hasSameStableMetadata = (before, after) =>
  STABLE_METADATA_FIELDS.every(field => before[field] === after[field]);

const safeByteCount = (size, relativePath) => {
  if (size < 0n || size > BigInt(Number.MAX_SAFE_INTEGER)) {
    throw new Error(`${relativePath} has an unsupported byte size`);
  }
  return Number(size);
};

export function readStableRegularFile(
  absolutePath,
  beforePathMetadata,
  relativePath,
) {
  let descriptor;
  try {
    descriptor = openSync(
      absolutePath,
      // File-descriptor flags are intentionally composed as a bit mask.
      // eslint-disable-next-line no-bitwise
      constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0),
    );
    const beforeReadMetadata = fstatSync(descriptor, { bigint: true });
    if (
      !beforeReadMetadata.isFile() ||
      !hasSameStableMetadata(beforePathMetadata, beforeReadMetadata)
    ) {
      throw new Error(`${relativePath} changed before it could be hashed`);
    }

    const bytes = readFileSync(descriptor);
    const afterReadMetadata = fstatSync(descriptor, { bigint: true });
    const afterPathMetadata = lstatSync(absolutePath, { bigint: true });
    if (
      !afterReadMetadata.isFile() ||
      !afterPathMetadata.isFile() ||
      !hasSameStableMetadata(beforeReadMetadata, afterReadMetadata) ||
      !hasSameStableMetadata(beforeReadMetadata, afterPathMetadata) ||
      BigInt(bytes.byteLength) !== beforeReadMetadata.size
    ) {
      throw new Error(`${relativePath} changed while it was being hashed`);
    }
    return { bytes, metadata: afterReadMetadata };
  } finally {
    if (descriptor !== undefined) closeSync(descriptor);
  }
}

const assertStablePath = (
  absolutePath,
  beforeMetadata,
  expectedKind,
  relativePath,
) => {
  const afterMetadata = lstatSync(absolutePath, { bigint: true });
  if (
    (expectedKind === 'directory' && !afterMetadata.isDirectory()) ||
    (expectedKind === 'symlink' && !afterMetadata.isSymbolicLink()) ||
    !hasSameStableMetadata(beforeMetadata, afterMetadata)
  ) {
    throw new Error(`${relativePath} changed while evidence was collected`);
  }
};

const isInside = (parent, candidate) => {
  const relative = path.relative(parent, candidate);
  return (
    relative === '' ||
    (!relative.startsWith('..') && !path.isAbsolute(relative))
  );
};

const assertRelativeInput = input => {
  if (
    typeof input !== 'string' ||
    !input ||
    path.isAbsolute(input) ||
    input.split(/[\\/]/u).some(part => part === '..' || part === '')
  ) {
    throw new Error(
      `evidence input must be a normalized relative path: ${input}`,
    );
  }
};

function walkEvidence(base, inputRoot, absolutePath, entries) {
  const relativePath = portablePath(path.relative(base, absolutePath));
  const metadata = lstatSync(absolutePath, { bigint: true });

  if (metadata.isSymbolicLink()) {
    const targetBytes = readlinkSync(absolutePath, { encoding: 'buffer' });
    const target = targetBytes.toString('utf8');
    if (!Buffer.from(target, 'utf8').equals(targetBytes)) {
      throw new Error(`${relativePath} has a non-UTF-8 symbolic-link target`);
    }
    if (path.isAbsolute(target)) {
      throw new Error(`${relativePath} has an absolute symbolic-link target`);
    }
    const resolvedTarget = path.resolve(path.dirname(absolutePath), target);
    if (!isInside(inputRoot, resolvedTarget)) {
      throw new Error(
        `${relativePath} symbolic link escapes its selected evidence input`,
      );
    }
    const realTarget = realpathSync(resolvedTarget);
    if (!isInside(inputRoot, realTarget)) {
      throw new Error(
        `${relativePath} symbolic link resolves outside its selected evidence input`,
      );
    }
    assertStablePath(absolutePath, metadata, 'symlink', relativePath);
    entries.push({
      bytes: targetBytes.byteLength,
      kind: 'symlink',
      path: relativePath,
      sha256: sha256(targetBytes),
      target: portablePath(target),
    });
    return;
  }

  if (metadata.isFile()) {
    const stableFile = readStableRegularFile(
      absolutePath,
      metadata,
      relativePath,
    );
    entries.push({
      bytes: safeByteCount(stableFile.metadata.size, relativePath),
      kind: 'file',
      mode: portableMode(stableFile.metadata),
      path: relativePath,
      sha256: sha256(stableFile.bytes),
    });
    return;
  }

  if (!metadata.isDirectory()) {
    throw new Error(
      `${relativePath} is not a regular file, directory, or safe symlink`,
    );
  }

  const children = readdirSync(absolutePath).sort((left, right) =>
    left.localeCompare(right, 'en'),
  );
  for (const child of children) {
    walkEvidence(base, inputRoot, path.join(absolutePath, child), entries);
  }
  assertStablePath(absolutePath, metadata, 'directory', relativePath);
}

export function createEvidenceManifest({ base, inputs, label, provenance }) {
  if (!/^[a-z][a-z0-9-]{1,63}$/u.test(label)) {
    throw new Error('evidence base label is invalid');
  }
  const resolvedBase = realpathSync(path.resolve(base));
  if (!lstatSync(resolvedBase).isDirectory()) {
    throw new Error('evidence base must be a directory');
  }
  if (!Array.isArray(inputs) || inputs.length === 0) {
    throw new Error('at least one evidence input is required');
  }
  validateProvenance(provenance);

  const entries = [];
  for (const input of inputs) {
    assertRelativeInput(input);
    const absoluteInput = path.resolve(resolvedBase, input);
    if (!isInside(resolvedBase, absoluteInput)) {
      throw new Error(`${input} escapes the evidence base`);
    }
    const canonicalInput = realpathSync(absoluteInput);
    if (
      canonicalInput !== absoluteInput ||
      !isInside(resolvedBase, canonicalInput)
    ) {
      throw new Error(`${input} evidence input path contains a symbolic link`);
    }
    const inputMetadata = lstatSync(absoluteInput, { bigint: true });
    if (inputMetadata.isSymbolicLink()) {
      throw new Error(`${input} cannot be a symbolic-link evidence input`);
    }
    const before = entries.length;
    walkEvidence(resolvedBase, absoluteInput, absoluteInput, entries);
    if (entries.length === before) {
      throw new Error(`${input} is empty and produced no evidence entries`);
    }
  }

  entries.sort((left, right) => left.path.localeCompare(right.path, 'en'));
  for (let index = 1; index < entries.length; index += 1) {
    if (entries[index - 1].path === entries[index].path) {
      throw new Error(`duplicate evidence input: ${entries[index].path}`);
    }
  }

  return Object.freeze({
    schemaVersion: 3,
    base: label,
    provenance,
    entries,
  });
}

export function resolveEvidenceManifestOutput(output, root = projectRoot) {
  if (!output || path.isAbsolute(output)) {
    throw new Error('manifest output must be a repository-relative path');
  }
  const resolvedRoot = path.resolve(root);
  const resolvedOutput = path.resolve(resolvedRoot, output);
  const relative = path.relative(resolvedRoot, resolvedOutput);
  if (
    relative.startsWith('..') ||
    path.isAbsolute(relative) ||
    !relative.startsWith(`release-evidence${path.sep}`)
  ) {
    throw new Error('manifest output must be inside release-evidence/');
  }

  let cursor = path.dirname(resolvedOutput);
  const parents = [];
  while (cursor !== resolvedRoot) {
    parents.push(cursor);
    cursor = path.dirname(cursor);
  }
  for (const parent of parents.reverse()) {
    const metadata = lstatSync(parent);
    if (!metadata.isDirectory() || metadata.isSymbolicLink()) {
      throw new Error(
        'manifest output path must contain only real directories',
      );
    }
  }
  return resolvedOutput;
}

export function writeEvidenceManifest(output, manifest, root = projectRoot) {
  const resolvedOutput = resolveEvidenceManifestOutput(output, root);
  writeFileSync(resolvedOutput, `${JSON.stringify(manifest, null, 2)}\n`, {
    flag: 'wx',
  });
  return resolvedOutput;
}

function parseArguments(args) {
  const values = new Map();
  const inputs = [];
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument.startsWith('--')) {
      const value = args[index + 1];
      if (!value || value.startsWith('--')) {
        throw new Error(`${argument} requires a value`);
      }
      if (values.has(argument))
        throw new Error(`${argument} was provided twice`);
      values.set(argument, value);
      index += 1;
    } else {
      inputs.push(argument);
    }
  }
  for (const required of ['--base', '--label', '--output']) {
    if (!values.has(required)) throw new Error(`${required} is required`);
  }
  const unknown = [...values.keys()].filter(
    key => !['--base', '--label', '--output'].includes(key),
  );
  if (unknown.length > 0) throw new Error(`unsupported argument ${unknown[0]}`);
  return { inputs, values };
}

if (
  process.argv[1] &&
  realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url))
) {
  try {
    const { inputs, values } = parseArguments(process.argv.slice(2));
    const manifest = createEvidenceManifest({
      base: values.get('--base'),
      inputs,
      label: values.get('--label'),
      provenance: collectEvidenceProvenance(values.get('--base')),
    });
    writeEvidenceManifest(values.get('--output'), manifest);
    process.stdout.write(
      `PASS evidence manifest (${manifest.entries.length} entries)\n`,
    );
  } catch (error) {
    process.stderr.write(
      `FAIL ${
        error instanceof Error ? error.message : 'evidence manifest failed'
      }\n`,
    );
    process.exitCode = 1;
  }
}
