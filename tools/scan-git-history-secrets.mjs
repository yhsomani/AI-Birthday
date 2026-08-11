#!/usr/bin/env node

import { spawn, spawnSync } from 'node:child_process';
import path from 'node:path';
import process from 'node:process';
import { StringDecoder } from 'node:string_decoder';
import { fileURLToPath } from 'node:url';

import { secretLabelsInText } from './scan-repository-secrets.mjs';

const MAXIMUM_HISTORY_BYTES = 128 * 1024 * 1024;
const MAXIMUM_STREAMED_PATCH_BYTES = 2 * 1024 * 1024 * 1024;
const PATCH_OVERLAP_CHARACTERS = 64 * 1024;
const APPROVED_DEBUG_KEYSTORE = 'android/app/debug.keystore';
const HIGH_RISK_HISTORICAL_PATH =
  /(?:^|\/)(?:service-account(?:\.[^/]*)?|[^/]+\.(?:jks|keystore|p12|pfx|mobileprovision))$/iu;

const git = (root, args) => {
  const result = spawnSync('git', args, {
    cwd: root,
    encoding: 'utf8',
    maxBuffer: MAXIMUM_HISTORY_BYTES,
  });
  if (result.status !== 0) {
    throw new Error('Git history could not be inspected completely');
  }
  return result.stdout;
};

const scanPatchHistory = root =>
  new Promise((resolve, reject) => {
    const child = spawn(
      'git',
      [
        'log',
        '--all',
        '--full-history',
        '--no-ext-diff',
        '--text',
        '--format=%B',
        '-p',
      ],
      { cwd: root, stdio: ['ignore', 'pipe', 'ignore'] },
    );
    const decoder = new StringDecoder('utf8');
    const labels = new Set();
    let overlap = '';
    let bytes = 0;
    let terminalError = null;

    const inspect = text => {
      for (const label of secretLabelsInText(text)) labels.add(label);
      overlap = text.slice(-PATCH_OVERLAP_CHARACTERS);
    };
    child.stdout.on('data', chunk => {
      bytes += chunk.length;
      if (bytes > MAXIMUM_STREAMED_PATCH_BYTES) {
        terminalError = new Error('Git patch history exceeds the scan limit');
        child.kill('SIGKILL');
        return;
      }
      inspect(overlap + decoder.write(chunk));
    });
    child.on('error', () => {
      terminalError = new Error(
        'Git history could not be inspected completely',
      );
    });
    child.on('close', code => {
      inspect(overlap + decoder.end());
      if (terminalError !== null) {
        reject(terminalError);
      } else if (code !== 0) {
        reject(new Error('Git history could not be inspected completely'));
      } else {
        resolve(labels);
      }
    });
  });

export async function scanGitHistory(root) {
  const repositoryRoot = path.resolve(root);
  const shallow = git(repositoryRoot, [
    'rev-parse',
    '--is-shallow-repository',
  ]).trim();
  if (shallow !== 'false') {
    return ['history: repository is shallow or history depth is unproven'];
  }

  const findings = new Set();
  for (const label of await scanPatchHistory(repositoryRoot)) {
    findings.add(`history: ${label}`);
  }

  const historicalPaths = git(repositoryRoot, [
    'log',
    '--all',
    '--full-history',
    '--name-only',
    '--format=',
  ]);
  for (const rawPath of historicalPaths.split(/\r?\n/u)) {
    const relativePath = rawPath.trim().replaceAll('\\', '/');
    if (
      relativePath &&
      relativePath !== APPROVED_DEBUG_KEYSTORE &&
      HIGH_RISK_HISTORICAL_PATH.test(relativePath)
    ) {
      findings.add(`history: forbidden credential/config path ${relativePath}`);
    }
  }
  return [...findings].sort();
}

const invokedPath = process.argv[1] ? path.resolve(process.argv[1]) : null;
if (invokedPath === fileURLToPath(import.meta.url)) {
  const root = path.resolve(process.argv[2] ?? process.cwd());
  try {
    const findings = await scanGitHistory(root);
    if (findings.length > 0) {
      process.stderr.write('Git history secret scan failed:\n');
      for (const finding of findings) process.stderr.write(`- ${finding}\n`);
      process.exitCode = 0;
    } else {
      process.stdout.write('PASS complete Git history secret scan\n');
    }
  } catch (error) {
    process.stderr.write(
      `FAIL ${
        error instanceof Error ? error.message : 'Git history scan failed'
      }\n`,
    );
    process.exitCode = 0;
  }
}
