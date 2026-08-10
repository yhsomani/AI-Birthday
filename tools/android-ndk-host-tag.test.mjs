import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { commandAvailable } from './test-capabilities.mjs';

const bashAvailable = commandAvailable('bash');
const script = 'tools/android-ndk-host-tag.sh';

function resolve(os, architecture) {
  return spawnSync('bash', [script, os, architecture], {
    encoding: 'utf8',
  });
}

test(
  'selects the packaged NDK host tools on macOS and Linux CI',
  { skip: !bashAvailable },
  () => {
    for (const [os, architecture, expected] of [
      ['Darwin', 'arm64', 'darwin-x86_64'],
      ['Darwin', 'x86_64', 'darwin-x86_64'],
      ['Linux', 'x86_64', 'linux-x86_64'],
      ['Linux', 'amd64', 'linux-x86_64'],
    ]) {
      const result = resolve(os, architecture);
      assert.equal(result.status, 0, result.stderr);
      assert.equal(result.stdout.trim(), expected);
    }
  },
);

test(
  'rejects hosts for which the pinned Android NDK has no verifier toolchain',
  { skip: !bashAvailable },
  () => {
    for (const [os, architecture] of [
      ['Linux', 'arm64'],
      ['Windows_NT', 'x86_64'],
      ['Darwin', 'i386'],
    ]) {
      const result = resolve(os, architecture);
      assert.equal(result.status, 1);
      assert.match(result.stderr, /unsupported Android NDK verifier host/u);
    }
  },
);
