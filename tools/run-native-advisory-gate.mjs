#!/usr/bin/env node

import {
  lstatSync,
  mkdirSync,
  mkdtempSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { createNativeSbom } from './generate-native-sbom.mjs';
import {
  buildNativeAdvisoryReport,
  prepareNativeTargets,
  readStableRegularFile,
  writeNativeAdvisoryReport,
} from './scan-native-vulnerabilities.mjs';

const projectRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
);
const PLANS = Object.freeze({
  android: Object.freeze([
    Object.freeze({
      label: 'android-complete-graph',
      kind: 'gradle',
      lock: 'android/app/gradle.lockfile',
      configuration: null,
      applicationName: 'Birthday-Autopilot-Android-Complete-Graph',
    }),
    Object.freeze({
      label: 'android-prod-runtime',
      kind: 'gradle',
      lock: 'android/app/gradle.lockfile',
      configuration: 'prodReleaseRuntimeClasspath',
      applicationName: 'Birthday-Autopilot-Android-Prod-Runtime',
    }),
    Object.freeze({
      label: 'android-build-plugins',
      kind: 'gradle',
      lock: 'android/buildscript-gradle.lockfile',
      configuration: null,
      applicationName: 'Birthday-Autopilot-Android-Build-Plugins',
    }),
  ]),
});

export function parsePlatformArguments(args) {
  if (
    args.length !== 2 ||
    args[0] !== '--platform' ||
    !['android', 'all'].includes(args[1])
  ) {
    throw new Error('usage: --platform <android|all>');
  }
  return args[1];
}

const readProjectVersion = root => {
  const packageDocument = JSON.parse(
    readStableRegularFile(
      path.join(root, 'package.json'),
      'root package manifest',
      1024 * 1024,
    ).toString('utf8'),
  );
  if (
    typeof packageDocument.version !== 'string' ||
    !/^[0-9A-Za-z][0-9A-Za-z.+-]{0,63}$/u.test(packageDocument.version)
  ) {
    throw new Error('root package version is invalid');
  }
  return packageDocument.version;
};

const planFor = platform =>
  platform === 'all' ? [...PLANS.android] : [...PLANS[platform]];

export function ensureNativeEvidenceRoot(root) {
  if (typeof root !== 'string' || !path.isAbsolute(root)) {
    throw new Error('native advisory workspace root must be absolute');
  }
  const rootMetadata = lstatSync(root);
  if (rootMetadata.isSymbolicLink() || !rootMetadata.isDirectory()) {
    throw new Error(
      'native advisory workspace root must be a non-symlinked directory',
    );
  }
  const evidenceRoot = path.join(root, 'release-evidence');
  try {
    const evidenceMetadata = lstatSync(evidenceRoot);
    if (evidenceMetadata.isSymbolicLink() || !evidenceMetadata.isDirectory()) {
      throw new Error(
        'native advisory evidence root must be a non-symlinked directory',
      );
    }
  } catch (error) {
    if (!(error instanceof Error) || error.code !== 'ENOENT') throw error;
    mkdirSync(evidenceRoot, { recursive: false, mode: 0o700 });
    const evidenceMetadata = lstatSync(evidenceRoot);
    if (evidenceMetadata.isSymbolicLink() || !evidenceMetadata.isDirectory()) {
      throw new Error(
        'native advisory evidence root must be a non-symlinked directory',
      );
    }
  }
  return evidenceRoot;
}

export async function runNativeAdvisoryGate({
  platform,
  root = projectRoot,
  now = new Date(),
  buildReport = buildNativeAdvisoryReport,
}) {
  if (!['android', 'ios', 'all'].includes(platform)) {
    throw new Error('native advisory platform is invalid');
  }
  if (!(now instanceof Date) || !Number.isFinite(now.getTime())) {
    throw new Error('native advisory timestamp is invalid');
  }
  ensureNativeEvidenceRoot(root);
  const temporaryDirectory = mkdtempSync(
    path.join(tmpdir(), 'birthday-native-advisory-'),
  );
  try {
    const version = readProjectVersion(root);
    const targetSpecs = planFor(platform).map((entry, index) => {
      const lockPath = path.join(root, entry.lock);
      const lockBytes = readStableRegularFile(
        lockPath,
        `${entry.label} lockfile`,
      );
      const sbom = createNativeSbom({
        kind: entry.kind,
        lockBytes,
        applicationName: entry.applicationName,
        version,
        configuration: entry.configuration,
      });
      const sbomPath = path.join(
        temporaryDirectory,
        `${String(index).padStart(2, '0')}-${entry.label}.cdx.json`,
      );
      writeFileSync(sbomPath, `${JSON.stringify(sbom, null, 2)}\n`, {
        encoding: 'utf8',
        flag: 'wx',
        mode: 0o600,
      });
      return {
        label: entry.label,
        kind: entry.kind,
        lockPath,
        sbomPath,
      };
    });
    const exceptionBytes = readStableRegularFile(
      path.join(root, 'tools/native-advisory-exceptions.json'),
      'native advisory exceptions',
    );
    let exceptionDocument;
    try {
      exceptionDocument = JSON.parse(exceptionBytes.toString('utf8'));
    } catch {
      throw new Error('native advisory exceptions are not valid JSON');
    }
    const targets = prepareNativeTargets(targetSpecs, { root });
    const report = await buildReport({
      targets,
      exceptionDocument,
      exceptionBytes,
      now,
    });
    const timestamp = now.toISOString().replace(/[^0-9A-Za-z]/gu, '-');
    const output = `release-evidence/native-advisory-${timestamp}-${platform}-${process.pid}.json`;
    writeNativeAdvisoryReport(output, report, root);
    return { report, output };
  } finally {
    rmSync(temporaryDirectory, { recursive: true, force: true });
  }
}

const direct =
  process.argv[1] !== undefined &&
  fileURLToPath(import.meta.url) === path.resolve(process.argv[1]);

if (direct) {
  try {
    const platform = parsePlatformArguments(process.argv.slice(2));
    const { report, output } = await runNativeAdvisoryGate({ platform });
    const summary = report.summary;
    const details = `${summary.componentCount} components, ${summary.findingCount} findings, ${summary.exceptedCount} exceptions; ${output}\n`;
    if (summary.status === 'pass') {
      process.stdout.write(`PASS native advisory gate: ${details}`);
    } else {
      process.stderr.write(`FAIL native advisory gate blocked: ${details}`);
      process.exitCode = 1;
    }
  } catch (error) {
    process.stderr.write(
      `FAIL ${
        error instanceof Error ? error.message : 'native advisory gate failed'
      }\n`,
    );
    process.exitCode = 1;
  }
}
