import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import {
  chmodSync,
  lstatSync,
  mkdirSync,
  mkdtempSync,
  rmSync,
  symlinkSync,
  unlinkSync,
  writeFileSync,
} from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import {
  collectEvidenceProvenance,
  createEvidenceManifest,
  readStableRegularFile,
  resolveEvidenceManifestOutput,
  writeEvidenceManifest,
} from './create-evidence-manifest.mjs';
import { symlinksAvailable } from './test-capabilities.mjs';

const PROVENANCE = Object.freeze({
  sourceRevision: 'ab'.repeat(20),
  sourceCommittedAt: '2026-07-12T12:00:00Z',
  builder: Object.freeze({
    kind: 'local',
    platform: 'darwin',
    architecture: 'arm64',
    nodeVersion: '24.18.0',
    npmVersion: '11.6.0',
  }),
});

const createManifest = options =>
  createEvidenceManifest({ ...options, provenance: PROVENANCE });

const fixture = t => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'birthday-evidence-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  mkdirSync(path.join(root, 'artifacts', 'nested'), { recursive: true });
  writeFileSync(path.join(root, 'artifacts', 'a.txt'), 'alpha');
  writeFileSync(path.join(root, 'artifacts', 'nested', 'b.txt'), 'beta');
  chmodSync(path.join(root, 'artifacts', 'a.txt'), 0o644);
  chmodSync(path.join(root, 'artifacts', 'nested', 'b.txt'), 0o755);
  return root;
};

test('creates a deterministic complete manifest with stable relative paths', t => {
  const root = fixture(t);
  const first = createManifest({
    base: root,
    inputs: ['artifacts'],
    label: 'candidate-build',
  });
  const second = createManifest({
    base: root,
    inputs: ['artifacts'],
    label: 'candidate-build',
  });

  assert.deepEqual(first, second);
  const portableMode = file =>
    lstatSync(file).mode.toString(8).slice(-4).padStart(4, '0');
  assert.deepEqual(
    first.entries.map(entry => [
      entry.kind,
      entry.path,
      entry.bytes,
      entry.mode,
    ]),
    [
      [
        'file',
        'artifacts/a.txt',
        5,
        portableMode(path.join(root, 'artifacts', 'a.txt')),
      ],
      [
        'file',
        'artifacts/nested/b.txt',
        4,
        portableMode(path.join(root, 'artifacts', 'nested', 'b.txt')),
      ],
    ],
  );
  assert.equal(first.schemaVersion, 3);
  assert.deepEqual(first.provenance, PROVENANCE);
  for (const entry of first.entries)
    assert.match(entry.sha256, /^[a-f0-9]{64}$/u);
});

test('records a safe in-base symlink without following it', t => {
  if (!symlinksAvailable) {
    t.skip('host cannot create symbolic links');
    return;
  }
  const root = fixture(t);
  symlinkSync('a.txt', path.join(root, 'artifacts', 'alias'));
  const manifest = createManifest({
    base: root,
    inputs: ['artifacts'],
    label: 'candidate-build',
  });
  assert.deepEqual(
    manifest.entries.find(entry => entry.path === 'artifacts/alias'),
    {
      bytes: 5,
      kind: 'symlink',
      path: 'artifacts/alias',
      sha256:
        '18b7cb099a9ea3f50ba899b5ba81e0d377a5f3b16f8f6eeb8b3e58cd4692b993',
      target: 'a.txt',
    },
  );
});

test('rejects missing, empty, duplicate, and escaping inputs', t => {
  const root = fixture(t);
  mkdirSync(path.join(root, 'empty'));
  assert.throws(
    () =>
      createManifest({
        base: root,
        inputs: ['missing'],
        label: 'candidate-build',
      }),
    /ENOENT/u,
  );
  assert.throws(
    () =>
      createManifest({
        base: root,
        inputs: ['empty'],
        label: 'candidate-build',
      }),
    /is empty and produced no evidence entries/u,
  );
  assert.throws(
    () =>
      createManifest({
        base: root,
        inputs: ['artifacts', 'artifacts/a.txt'],
        label: 'candidate-build',
      }),
    /duplicate evidence input/u,
  );
  assert.throws(
    () =>
      createManifest({
        base: root,
        inputs: ['../outside'],
        label: 'candidate-build',
      }),
    /normalized relative path/u,
  );
});

test('rejects absolute and escaping symbolic-link targets', t => {
  if (!symlinksAvailable) {
    t.skip('host cannot create symbolic links');
    return;
  }
  const root = fixture(t);
  const outside = path.join(root, '..', 'outside-evidence-file');
  writeFileSync(outside, 'outside');
  t.after(() => rmSync(outside, { force: true }));
  symlinkSync(outside, path.join(root, 'artifacts', 'absolute-link'));
  assert.throws(
    () =>
      createManifest({
        base: root,
        inputs: ['artifacts'],
        label: 'candidate-build',
      }),
    /absolute symbolic-link target/u,
  );
  rmSync(path.join(root, 'artifacts', 'absolute-link'));

  const sibling = path.join(root, 'retained-elsewhere.txt');
  writeFileSync(sibling, 'outside selected input');
  symlinkSync(
    '../retained-elsewhere.txt',
    path.join(root, 'artifacts', 'sibling-link'),
  );
  assert.throws(
    () =>
      createManifest({
        base: root,
        inputs: ['artifacts', 'retained-elsewhere.txt'],
        label: 'candidate-build',
      }),
    /escapes its selected evidence input/u,
  );

  const chainedRoot = fixture(t);
  symlinkSync(outside, path.join(chainedRoot, 'bridge'));
  symlinkSync('../bridge', path.join(chainedRoot, 'artifacts', 'chained-link'));
  assert.throws(
    () =>
      createManifest({
        base: chainedRoot,
        inputs: ['artifacts'],
        label: 'candidate-build',
      }),
    /escapes its selected evidence input/u,
  );
});

test('descriptor-backed hashing rejects stale files and type swaps', t => {
  const root = fixture(t);
  const file = path.join(root, 'artifacts', 'a.txt');
  const before = lstatSync(file, { bigint: true });
  writeFileSync(file, 'changed after inspection');
  assert.throws(
    () => readStableRegularFile(file, before, 'artifacts/a.txt'),
    /changed before it could be hashed/u,
  );

  if (symlinksAvailable) {
    const current = lstatSync(file, { bigint: true });
    unlinkSync(file);
    symlinkSync('nested/b.txt', file);
    assert.throws(
      () => readStableRegularFile(file, current, 'artifacts/a.txt'),
      /(?:ELOOP|changed before it could be hashed)/u,
    );
  } else {
    t.diagnostic('host cannot create symbolic links; type-swap case skipped');
  }
});

test('rejects an evidence input reached through a symbolic-link parent', t => {
  if (!symlinksAvailable) {
    t.skip('host cannot create symbolic links');
    return;
  }
  const root = fixture(t);
  symlinkSync('artifacts', path.join(root, 'linked-artifacts'));
  assert.throws(
    () =>
      createManifest({
        base: root,
        inputs: ['linked-artifacts/nested'],
        label: 'candidate-build',
      }),
    /input path contains a symbolic link/u,
  );
});

test('a symlinked CLI invocation executes and fails closed', t => {
  if (!symlinksAvailable) {
    t.skip('host cannot create symbolic links');
    return;
  }
  const root = fixture(t);
  const linkedCli = path.join(root, 'manifest-cli.mjs');
  symlinkSync(
    fileURLToPath(new URL('./create-evidence-manifest.mjs', import.meta.url)),
    linkedCli,
  );

  const result = spawnSync(process.execPath, [linkedCli, '--unknown', 'x'], {
    encoding: 'utf8',
  });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /--base is required/u);
});

test('requires immutable source and toolchain provenance', t => {
  const root = fixture(t);
  assert.throws(
    () =>
      createEvidenceManifest({
        base: root,
        inputs: ['artifacts'],
        label: 'candidate-build',
        provenance: {
          ...PROVENANCE,
          sourceRevision: 'not-a-revision',
        },
      }),
    /provenance is invalid/u,
  );
  const current = collectEvidenceProvenance(
    fileURLToPath(new URL('../', import.meta.url)),
  );
  assert.match(current.sourceRevision, /^[0-9a-f]{40}$/u);
  assert.match(current.sourceCommittedAt, /^\d{4}-\d{2}-\d{2}T/u);
  assert.equal(current.builder.nodeVersion, process.version.slice(1));
  assert.match(current.builder.npmVersion, /^\d+\.\d+\.\d+/u);
});

test('manifest output stays in a real release-evidence directory', t => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'birthday-manifest-output-'));
  const outside = mkdtempSync(
    path.join(os.tmpdir(), 'birthday-manifest-outside-'),
  );
  t.after(() => {
    rmSync(root, { recursive: true, force: true });
    rmSync(outside, { recursive: true, force: true });
  });
  mkdirSync(path.join(root, 'release-evidence', 'mobile'), {
    recursive: true,
  });
  assert.equal(
    resolveEvidenceManifestOutput(
      'release-evidence/mobile/sha256-manifest.json',
      root,
    ),
    path.join(root, 'release-evidence', 'mobile', 'sha256-manifest.json'),
  );
  const output = 'release-evidence/mobile/sha256-manifest.json';
  writeEvidenceManifest(output, { schemaVersion: 1, entries: [] }, root);
  assert.throws(
    () => writeEvidenceManifest(output, { schemaVersion: 1 }, root),
    error =>
      error instanceof Error && 'code' in error && error.code === 'EEXIST',
  );
  assert.throws(
    () => resolveEvidenceManifestOutput('../manifest.json', root),
    /inside release-evidence/u,
  );

  if (symlinksAvailable) {
    symlinkSync(outside, path.join(root, 'release-evidence', 'escape'));
    assert.throws(
      () =>
        resolveEvidenceManifestOutput(
          'release-evidence/escape/manifest.json',
          root,
        ),
      /only real directories/u,
    );
  } else {
    t.diagnostic('host cannot create symbolic links; escape case skipped');
  }
});
