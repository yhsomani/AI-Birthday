import assert from 'node:assert/strict';
import {
  linkSync,
  mkdirSync,
  mkdtempSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

import { hashDistributionEvidenceRoot } from './hash-distribution-evidence-root.mjs';

const fixture = callback => {
  const root = mkdtempSync(join(tmpdir(), 'birthday-evidence-root-hash-'));
  try {
    mkdirSync(join(root, 'review'));
    writeFileSync(join(root, 'policy.json'), '{"approved":true}\n');
    writeFileSync(join(root, 'review', 'legal.txt'), 'approved\n');
    return callback(root);
  } finally {
    rmSync(root, { force: true, recursive: true });
  }
};

test('evidence-root digest is deterministic and changes with exact bytes or paths', () => {
  fixture(root => {
    const original = hashDistributionEvidenceRoot(root);
    assert.match(original, /^[0-9a-f]{64}$/u);
    assert.equal(hashDistributionEvidenceRoot(root), original);

    writeFileSync(join(root, 'review', 'legal.txt'), 'rejected\n');
    assert.notEqual(hashDistributionEvidenceRoot(root), original);
  });

  const moved = fixture(root => {
    writeFileSync(join(root, 'review', 'legal.txt'), 'rejected\n');
    return hashDistributionEvidenceRoot(root);
  });
  const differentlyNamed = fixture(root => {
    writeFileSync(join(root, 'review', 'legal.txt'), 'rejected\n');
    writeFileSync(join(root, 'renamed.txt'), 'extra\n');
    return hashDistributionEvidenceRoot(root);
  });
  assert.notEqual(moved, differentlyNamed);
});

test('evidence-root digest rejects hard-linked evidence bytes', () => {
  fixture(root => {
    linkSync(join(root, 'policy.json'), join(root, 'policy-copy.json'));
    assert.throws(
      () => hashDistributionEvidenceRoot(root),
      /hard-linked regular file/u,
    );
  });
});
