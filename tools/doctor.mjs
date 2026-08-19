#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import process from 'node:process';

import { verifyInstalledReactNativeCodegen } from './patch-react-native-codegen.mjs';
import { TOOLCHAIN_VERSIONS } from './toolchain-versions.mjs';

const projectRoot = fileURLToPath(new URL('../', import.meta.url));
const requestedPlatform =
  process.argv
    .find(argument => argument.startsWith('--platform='))
    ?.split('=')[1] ?? 'all';

if (!['all', 'android'].includes(requestedPlatform)) {
  process.stderr.write(
    `FAIL platform: expected all or android; found ${requestedPlatform}\n`,
  );
  process.exit(1);
}

const failures = [];

function command(binary, args = []) {
  const result = spawnSync(binary, args, {
    cwd: projectRoot,
    encoding: 'utf8',
  });
  if (result.status !== 0) return null;
  return `${result.stdout ?? ''}${result.stderr ?? ''}`.trim();
}

function semanticVersion(output) {
  return output?.match(/\b\d+\.\d+\.\d+\b/u)?.[0] ?? null;
}

function requireValue(label, actual, expected) {
  if (actual !== expected) {
    failures.push(
      `${label}: expected ${expected}, found ${actual ?? 'missing'}`,
    );
  }
}

function requirePath(label, relativePath) {
  const absolutePath = path.isAbsolute(relativePath)
    ? relativePath
    : path.join(projectRoot, relativePath);
  if (!existsSync(absolutePath)) {
    failures.push(`${label}: missing ${relativePath}`);
  }
}

requireValue('Node', process.version, `v${TOOLCHAIN_VERSIONS.node}`);
requireValue(
  'npm',
  semanticVersion(command('npm', ['--version'])),
  TOOLCHAIN_VERSIONS.npm,
);

try {
  verifyInstalledReactNativeCodegen(projectRoot);
} catch (error) {
  failures.push(
    `React Native codegen: ${
      error instanceof Error ? error.message : 'verification failed'
    }`,
  );
}

if (requestedPlatform === 'all' || requestedPlatform === 'android') {
  const androidHome = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT;
  if (!androidHome || !existsSync(androidHome)) {
    failures.push('Android SDK: set ANDROID_HOME to an installed SDK');
  }

  const javaHome = process.env.JAVA_HOME;
  const javaBinary = javaHome ? `${javaHome}/bin/java` : 'java';
  const javaVersion = command(javaBinary, ['-version']);
  if (!javaVersion) {
    failures.push('Java: set JAVA_HOME to JDK 21');
  } else if (!/\bversion "21(?:[."])/u.test(javaVersion)) {
    failures.push(`Java: expected JDK 21, found ${javaVersion.split('\n')[0]}`);
  }

  if (androidHome) {
    requirePath(
      'Android API 36',
      `${androidHome}/platforms/android-36/android.jar`,
    );
    requirePath(
      'Android Build Tools 36',
      `${androidHome}/build-tools/36.0.0/aapt2`,
    );
    requirePath(
      'Android NDK 27.1',
      `${androidHome}/ndk/27.1.12297006/source.properties`,
    );
  }
}

for (const failure of failures) process.stderr.write(`FAIL ${failure}\n`);

if (failures.length > 0) process.exit(1);
process.stdout.write(`PASS ${requestedPlatform} development environment\n`);
