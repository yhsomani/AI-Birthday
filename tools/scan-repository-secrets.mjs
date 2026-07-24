#!/usr/bin/env node

import { lstat, readFile, readdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const excludedDirectories = new Set([
  '.git',
  '.gradle',
  'Pods',
  'build',
  'coverage',
  'DerivedData',
  'node_modules',
]);
const excludedRelativeDirectories = new Set(['vendor/bundle']);
const forbiddenSuffixes = new Set([
  '.jks',
  '.keystore',
  '.p12',
  '.pfx',
  '.mobileprovision',
]);
const maximumTextBytes = 2 * 1024 * 1024;
const approvedTemplateDebugKeystoreSha256 =
  '221e0a3106aa4c3ccc154e0a418b55020b3f9ea6e84f92e8749cd9e2f39f5e58';

export const secretRules = [
  {
    label: 'private key material',
    pattern: new RegExp('-{5}BEGIN (?:RSA |EC |OPENSSH )?PRIVATE' + ' KEY-{5}'),
  },
  {
    label: 'Google OAuth client secret',
    pattern: new RegExp('GOC' + 'SPX-[A-Za-z0-9_-]{20,}'),
  },
  {
    label: 'Google access token',
    pattern: new RegExp('ya' + '29\\.[A-Za-z0-9_-]{20,}'),
  },
  {
    label: 'Google/Stitch credential',
    pattern: new RegExp('\\bA' + 'Q\\.[A-Za-z0-9_-]{24,}'),
  },
  {
    label: 'OpenAI secret key',
    pattern: new RegExp('\\bsk-' + '[A-Za-z0-9_-]{20,}'),
  },
  {
    label: 'GitHub access token',
    pattern: new RegExp('\\bgh' + '[pousr]_[A-Za-z0-9]{30,}'),
  },
  {
    label: 'Google API key outside an approved client config',
    pattern: new RegExp('AI' + 'za[A-Za-z0-9_-]{20,}'),
    allow: isApprovedFirebaseClientConfig,
  },
  {
    label: 'service-account private key field',
    pattern: new RegExp('["\\\']private_' + 'key["\\\']\\s*:'),
  },
  {
    label: 'hard-coded API-key header',
    pattern: new RegExp(
      'X-Goog-' + 'Api-Key["\\\']?\\s*[:=]\\s*["\\\'][^"\\\']{12,}',
    ),
  },
];

function isApprovedFirebaseClientConfig(relativePath) {
  const normalized = relativePath.split(path.sep).join('/');
  return (
    /^android\/app\/src\/(dev|staging|lab|prod)\/google-services\.json$/u.test(
      normalized,
    ) ||
    /^ios\/Config\/(dev|staging|lab|prod)\/GoogleService-Info\.plist$/u.test(
      normalized,
    ) ||
    /^app\/google-services\.json$/u.test(normalized) ||
    /^app\/src\/debug\/google-services\.json$/u.test(normalized)
  );
}

export function isForbiddenCredentialPath(relativePath) {
  const normalized = relativePath.split(path.sep).join('/');
  const basename = path.basename(normalized);
  if (forbiddenSuffixes.has(path.extname(basename).toLowerCase())) return true;
  if (/^(?:\.env|service-account)(?:\..+)?$/u.test(basename)) return true;
  if (
    basename === 'google-services.json' &&
    !isApprovedFirebaseClientConfig(relativePath)
  ) {
    return true;
  }
  if (
    basename === 'GoogleService-Info.plist' &&
    !isApprovedFirebaseClientConfig(relativePath)
  ) {
    return true;
  }
  return false;
}

export function secretLabelsInText(content, relativePath = null) {
  const labels = [];
  for (const rule of secretRules) {
    if (relativePath !== null && rule.allow?.(relativePath)) continue;
    if (rule.pattern.test(content)) labels.push(rule.label);
  }
  return labels;
}

async function collectFiles(root, current = root, output = []) {
  const entries = await readdir(current, { withFileTypes: true });
  for (const entry of entries) {
    const absolutePath = path.join(current, entry.name);
    const relativePath = path
      .relative(root, absolutePath)
      .split(path.sep)
      .join('/');
    if (
      entry.isDirectory() &&
      (excludedDirectories.has(entry.name) ||
        excludedRelativeDirectories.has(relativePath))
    ) {
      continue;
    }
    if (entry.isSymbolicLink()) continue;
    if (entry.isDirectory()) {
      await collectFiles(root, absolutePath, output);
    } else if (entry.isFile()) {
      output.push(absolutePath);
    }
  }
  return output;
}

async function inspectTextFile(root, absolutePath, findings) {
  const metadata = await lstat(absolutePath);
  if (metadata.size === 0 || metadata.size > maximumTextBytes) return;
  const buffer = await readFile(absolutePath);
  if (buffer.includes(0)) return;
  const relativePath = path.relative(root, absolutePath);
  const content = buffer.toString('utf8');
  for (const label of secretLabelsInText(content, relativePath)) {
    findings.push(`${relativePath}: ${label}`);
  }
}

async function isApprovedTemplateDebugKeystore(root, absolutePath) {
  const relativePath = path
    .relative(root, absolutePath)
    .split(path.sep)
    .join('/');
  if (relativePath !== 'android/app/debug.keystore') return false;
  const digest = createHash('sha256')
    .update(await readFile(absolutePath))
    .digest('hex');
  return digest === approvedTemplateDebugKeystoreSha256;
}

export async function scanRepository(root) {
  const findings = [];
  const files = await collectFiles(root);
  for (const absolutePath of files) {
    const relativePath = path.relative(root, absolutePath);
    if (isForbiddenCredentialPath(relativePath)) {
      if (await isApprovedTemplateDebugKeystore(root, absolutePath)) continue;
      findings.push(`${relativePath}: forbidden credential/config path`);
      continue;
    }
    await inspectTextFile(root, absolutePath, findings);
  }
  return findings.sort();
}

const invokedPath = process.argv[1] ? path.resolve(process.argv[1]) : null;
if (invokedPath === fileURLToPath(import.meta.url)) {
  const root = path.resolve(process.argv[2] ?? process.cwd());
  const findings = await scanRepository(root);
  if (findings.length > 0) {
    console.error('Repository secret scan failed:');
    findings.forEach(finding => console.error(`- ${finding}`));
    process.exitCode = 1;
  } else {
    console.log('PASS repository secret scan');
  }
}
