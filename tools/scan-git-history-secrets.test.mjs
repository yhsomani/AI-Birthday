import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import {
  mkdirSync,
  mkdtempSync,
  rmSync,
  unlinkSync,
  writeFileSync,
} from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import { scanGitHistory } from './scan-git-history-secrets.mjs';

const run = (root, args) => {
  const result = spawnSync('git', args, { cwd: root, encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
};

const repository = t => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'birthday-history-scan-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  run(root, ['init', '--quiet']);
  run(root, ['config', 'user.name', 'Birthday Test']);
  run(root, ['config', 'user.email', 'test@invalid.example']);
  writeFileSync(path.join(root, 'README.md'), 'safe\n');
  run(root, ['add', 'README.md']);
  run(root, ['commit', '--quiet', '-m', 'safe']);
  return root;
};

test('passes a complete history containing no credential material', async t => {
  const root = repository(t);
  assert.deepEqual(await scanGitHistory(root), []);
});

test('finds a secret deleted from the current worktree without echoing it', async t => {
  const root = repository(t);
  const secretPath = path.join(root, 'temporary.txt');
  const secret = `AQ.${'sensitive-history-value'.repeat(2)}`;
  writeFileSync(secretPath, `${secret}\n`);
  run(root, ['add', 'temporary.txt']);
  run(root, ['commit', '--quiet', '-m', 'unsafe historical content']);
  unlinkSync(secretPath);
  run(root, ['add', '-u']);
  run(root, ['commit', '--quiet', '-m', 'remove content']);

  const findings = await scanGitHistory(root);
  assert.deepEqual(findings, ['history: Google/Stitch credential']);
  assert.equal(findings.join('\n').includes(secret), false);
});

test('finds a deleted forbidden credential path', async t => {
  const root = repository(t);
  mkdirSync(path.join(root, 'config'));
  writeFileSync(path.join(root, 'config', 'service-account.json'), '{}\n');
  run(root, ['add', 'config/service-account.json']);
  run(root, ['commit', '--quiet', '-m', 'unsafe path']);
  rmSync(path.join(root, 'config'), { recursive: true });
  run(root, ['add', '-u']);
  run(root, ['commit', '--quiet', '-m', 'remove path']);

  assert.deepEqual(await scanGitHistory(root), [
    'history: forbidden credential/config path config/service-account.json',
  ]);
});
