import assert from 'node:assert/strict';
import {
  copyFileSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { symlinksAvailable } from './test-capabilities.mjs';

import {
  buildJavaScriptLicenseEvidence,
  resolveJavaScriptLicenseOutput,
  writeJavaScriptLicenseEvidence,
} from './verify-javascript-licenses.mjs';

const projectRoot = fileURLToPath(new URL('../', import.meta.url));
const lockfilePaths = [
  'package-lock.json',
  'backend/functions/package-lock.json',
  'backend/hosting/package-lock.json',
];
const reviewedLicensePaths = [
  'node_modules/exit/LICENSE-MIT',
  'backend/functions/node_modules/fuzzy/LICENSE-MIT',
  'backend/functions/node_modules/limiter/LICENSE.txt',
  'backend/functions/node_modules/valid-url/LICENSE',
];

test('all dependency sets have exact identities, counts, and lockfile hashes', () => {
  const evidence = buildJavaScriptLicenseEvidence();
  assert.equal(evidence.schemaVersion, 2);
  assert.equal(evidence.components.length, 2_005);
  assert.deepEqual(
    evidence.dependencySets.map(
      ({
        componentCount,
        label,
        lockfile,
        lockfilePackageCount,
        registryOrigin,
        rootIdentity,
      }) => ({
        componentCount,
        label,
        lockfile,
        lockfilePackageCount,
        registryOrigin,
        rootIdentity,
      }),
    ),
    [
      {
        componentCount: 926,
        label: 'mobile',
        lockfile: 'package-lock.json',
        lockfilePackageCount: 927,
        registryOrigin: 'https://registry.npmjs.org',
        rootIdentity: 'birthday-autopilot@0.1.0',
      },
      {
        componentCount: 947,
        label: 'functions',
        lockfile: 'backend/functions/package-lock.json',
        lockfilePackageCount: 948,
        registryOrigin: 'https://registry.npmjs.org',
        rootIdentity: 'birthday-autopilot-control-plane@0.1.0',
      },
      {
        componentCount: 132,
        label: 'hosting',
        lockfile: 'backend/hosting/package-lock.json',
        lockfilePackageCount: 133,
        registryOrigin: 'https://registry.npmjs.org',
        rootIdentity: 'birthday-autopilot-public-site@0.1.0',
      },
    ],
  );
  for (const dependencySet of evidence.dependencySets) {
    assert.match(dependencySet.lockfileSha256, /^[a-f0-9]{64}$/u);
  }
});

test('missing lockfile license metadata is accepted only with exact reviewed MIT hashes', () => {
  const evidence = buildJavaScriptLicenseEvidence();
  assert.deepEqual(
    evidence.components.filter(component => component.reviewedLicenseSha256),
    [
      [
        'functions',
        'fuzzy@0.1.3',
        'LICENSE-MIT',
        'a4aca837172fb1f6188c426e38835202e96bc853e25b7029cc5c933964f8401e',
      ],
      [
        'functions',
        'limiter@1.1.5',
        'LICENSE.txt',
        'a3aebd11ea5598ef12949bf793311bf155ab7727181e3d373bd0b47813d41111',
      ],
      [
        'functions',
        'valid-url@1.0.9',
        'LICENSE',
        'c48a681e53bfcd0a2b3ee2ea476e6d031fe7563f9eaa68f763bce0e3fb279a46',
      ],
      [
        'mobile',
        'exit@0.1.2',
        'LICENSE-MIT',
        '65bd93f75d6c0cdc1c9e1a39bd1814e2e34355c665e1564a1517f27c1523ab7e',
      ],
    ].map(([dependencySet, identity, reviewedLicenseFile, hash]) => ({
      ...expectComponent(evidence, dependencySet, identity),
      reviewedLicenseFile,
      reviewedLicenseSha256: hash,
    })),
  );
});

test('generated license evidence is deterministic and omits raw resolved URLs', () => {
  const evidence = buildJavaScriptLicenseEvidence();
  const serialized = JSON.stringify(evidence);
  assert.doesNotMatch(serialized, /generatedAt|timestamp|serialNumber/iu);
  assert.doesNotMatch(serialized, /access[_-]?token|refresh[_-]?token/iu);
  assert.doesNotMatch(serialized, /resolved|\.tgz/iu);
  assert.equal(
    JSON.stringify(buildJavaScriptLicenseEvidence()),
    JSON.stringify(evidence),
  );
});

test('rejects malformed JSON in a temporary lockfile fixture', t => {
  const root = createFixture(t);
  writeFileSync(path.join(root, 'package-lock.json'), '{"lockfileVersion":3');
  assert.throws(
    () => buildJavaScriptLicenseEvidence(root),
    /package-lock\.json: malformed or truncated JSON/u,
  );
});

test('rejects a valid-JSON lockfile with a truncated package table', t => {
  const root = createFixture(t);
  updateLockfile(root, 'package-lock.json', document => {
    delete document.packages[Object.keys(document.packages).at(-1)];
  });
  assert.throws(
    () => buildJavaScriptLicenseEvidence(root),
    /expected 927 package entries, found 926; lockfile may be truncated/u,
  );
});

test('rejects an unreviewed lockfile format or root identity', t => {
  const formatRoot = createFixture(t);
  updateLockfile(formatRoot, 'package-lock.json', document => {
    document.lockfileVersion = 2;
  });
  assert.throws(
    () => buildJavaScriptLicenseEvidence(formatRoot),
    /lockfileVersion must be exactly 3/u,
  );

  const identityRoot = createFixture(t);
  updateLockfile(identityRoot, 'package-lock.json', document => {
    document.name = 'lookalike-app';
  });
  assert.throws(
    () => buildJavaScriptLicenseEvidence(identityRoot),
    /expected root identity birthday-autopilot@0\.1\.0/u,
  );
});

test('rejects malformed package metadata in a temporary lockfile fixture', t => {
  const root = createFixture(t);
  updateLockfile(root, 'package-lock.json', document => {
    const firstPath = Object.keys(document.packages).find(Boolean);
    assert.ok(firstPath);
    document.packages[firstPath] = null;
  });
  assert.throws(
    () => buildJavaScriptLicenseEvidence(root),
    /metadata is malformed/u,
  );
});

test('rejects unknown license metadata in a temporary lockfile fixture', t => {
  const root = createFixture(t);
  updateFirstComponent(root, metadata => {
    metadata.license = 'UNREVIEWED-1.0';
  });
  assert.throws(
    () => buildJavaScriptLicenseEvidence(root),
    /uses unapproved license UNREVIEWED-1\.0/u,
  );
});

test('rejects missing integrity metadata in a temporary lockfile fixture', t => {
  const root = createFixture(t);
  updateFirstComponent(root, metadata => {
    delete metadata.integrity;
  });
  assert.throws(
    () => buildJavaScriptLicenseEvidence(root),
    /has no integrity metadata/u,
  );
});

test('rejects malformed integrity metadata in a temporary lockfile fixture', t => {
  const root = createFixture(t);
  updateFirstComponent(root, metadata => {
    metadata.integrity = 'sha512-not-a-complete-digest';
  });
  assert.throws(
    () => buildJavaScriptLicenseEvidence(root),
    /has malformed integrity metadata/u,
  );
});

test('rejects changed reviewed license text even when it still claims MIT', t => {
  const root = createFixture(t);
  writeFileSync(
    path.join(root, 'node_modules/exit/LICENSE-MIT'),
    'MIT License\nPermission is hereby granted, free of charge.\n',
  );
  assert.throws(
    () => buildJavaScriptLicenseEvidence(root),
    /exit@0\.1\.2 reviewed license SHA256 does not match/u,
  );
});

test('output paths are repository-root based, reject symlinks, and never overwrite', t => {
  const root = mkdtempSync(path.join(tmpdir(), 'birthday-license-output-'));
  const outside = mkdtempSync(path.join(tmpdir(), 'birthday-license-outside-'));
  t.after(() => {
    rmSync(root, { recursive: true, force: true });
    rmSync(outside, { recursive: true, force: true });
  });

  mkdirSync(path.join(root, 'release-evidence', 'mobile'), { recursive: true });
  const expected = path.join(
    root,
    'release-evidence',
    'mobile',
    'javascript-licenses.json',
  );
  assert.equal(
    resolveJavaScriptLicenseOutput(
      'release-evidence/mobile/javascript-licenses.json',
      root,
    ),
    expected,
  );
  writeJavaScriptLicenseEvidence(
    'release-evidence/mobile/javascript-licenses.json',
    { schemaVersion: 2 },
    root,
  );
  assert.throws(
    () =>
      writeJavaScriptLicenseEvidence(
        'release-evidence/mobile/javascript-licenses.json',
        { schemaVersion: 2 },
        root,
      ),
    error =>
      error instanceof Error && 'code' in error && error.code === 'EEXIST',
  );

  if (symlinksAvailable) {
    symlinkSync(outside, path.join(root, 'release-evidence', 'escape'));
    assert.throws(
      () =>
        resolveJavaScriptLicenseOutput(
          'release-evidence/escape/javascript-licenses.json',
          root,
        ),
      /must not contain symbolic links/u,
    );
  } else {
    t.diagnostic('host cannot create symbolic links; symlink case skipped');
  }
  assert.throws(
    () => resolveJavaScriptLicenseOutput('../outside.json', root),
    /must be inside release-evidence/u,
  );
});

function createFixture(t) {
  const root = mkdtempSync(path.join(tmpdir(), 'birthday-license-fixture-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  for (const relativePath of [...lockfilePaths, ...reviewedLicensePaths]) {
    const destination = path.join(root, relativePath);
    mkdirSync(path.dirname(destination), { recursive: true });
    copyFileSync(path.join(projectRoot, relativePath), destination);
  }
  return root;
}

function updateLockfile(root, relativePath, mutate) {
  const lockfilePath = path.join(root, relativePath);
  const document = JSON.parse(readFileSync(lockfilePath, 'utf8'));
  mutate(document);
  writeFileSync(lockfilePath, `${JSON.stringify(document, null, 2)}\n`);
}

function updateFirstComponent(root, mutate) {
  updateLockfile(root, 'package-lock.json', document => {
    const firstPath = Object.keys(document.packages).find(Boolean);
    assert.ok(firstPath);
    mutate(document.packages[firstPath]);
  });
}

function expectComponent(evidence, dependencySet, identity) {
  const component = evidence.components.find(
    candidate =>
      candidate.dependencySet === dependencySet &&
      candidate.identity === identity,
  );
  assert.ok(component);
  assert.equal(component.license, 'MIT');
  return component;
}
