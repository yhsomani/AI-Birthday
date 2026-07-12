import assert from 'node:assert/strict';
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import { scanRepository } from './scan-repository-secrets.mjs';

async function withRepository(run) {
  const root = await mkdtemp(path.join(os.tmpdir(), 'birthday-secret-scan-'));
  try {
    await run(root);
  } finally {
    await rm(root, { force: true, recursive: true });
  }
}

test('rejects misplaced client configs and credential-shaped content', async () => {
  await withRepository(async root => {
    await mkdir(path.join(root, 'app'), { recursive: true });
    await writeFile(path.join(root, 'app', 'google-services.json'), '{}');
    await writeFile(
      path.join(root, 'leak.txt'),
      ['ya', '29.', 'a'.repeat(30)].join(''),
    );
    const findings = await scanRepository(root);
    assert.equal(findings.length, 2);
    assert.match(findings.join('\n'), /forbidden credential\/config path/u);
    assert.match(findings.join('\n'), /Google access token/u);
  });
});

test('allows public Firebase keys only in exact tier client-config locations', async () => {
  await withRepository(async root => {
    const configDirectory = path.join(root, 'android', 'app', 'src', 'dev');
    await mkdir(configDirectory, { recursive: true });
    await writeFile(
      path.join(configDirectory, 'google-services.json'),
      JSON.stringify({ api_key: ['AI', 'za', 'a'.repeat(30)].join('') }),
    );
    await writeFile(
      path.join(root, 'README.md'),
      'No credentials are stored here.',
    );
    assert.deepEqual(await scanRepository(root), []);
  });
});

test('skips dependency/build directories and binary files', async () => {
  await withRepository(async root => {
    await mkdir(path.join(root, 'node_modules', 'fixture'), {
      recursive: true,
    });
    await writeFile(
      path.join(root, 'node_modules', 'fixture', 'token.txt'),
      ['sk-', 'a'.repeat(30)].join(''),
    );
    await mkdir(path.join(root, 'vendor', 'bundle', 'dependency'), {
      recursive: true,
    });
    await writeFile(
      path.join(root, 'vendor', 'bundle', 'dependency', 'sample.key'),
      ['-----BEGIN PRIVATE', ' KEY-----'].join(''),
    );
    await writeFile(path.join(root, 'image.bin'), Buffer.from([0, 1, 2, 3]));
    assert.deepEqual(await scanRepository(root), []);
  });
});

test('still scans vendored source outside the generated Bundler directory', async () => {
  await withRepository(async root => {
    await mkdir(path.join(root, 'vendor', 'source'), { recursive: true });
    await writeFile(
      path.join(root, 'vendor', 'source', 'leak.key'),
      ['-----BEGIN PRIVATE', ' KEY-----'].join(''),
    );
    assert.match(
      (await scanRepository(root)).join('\n'),
      /vendor\/source\/leak\.key: private key material/u,
    );
  });
});

test('rejects arbitrary release and non-template debug keystores', async () => {
  await withRepository(async root => {
    await mkdir(path.join(root, 'android', 'app'), { recursive: true });
    await writeFile(
      path.join(root, 'android', 'app', 'debug.keystore'),
      'not-template',
    );
    await writeFile(path.join(root, 'release.p12'), 'not-a-release-key');
    const findings = await scanRepository(root);
    assert.equal(findings.length, 2);
    assert.match(findings.join('\n'), /android\/app\/debug\.keystore/u);
    assert.match(findings.join('\n'), /release\.p12/u);
  });
});
