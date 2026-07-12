import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import process from 'node:process';
import { createRequire } from 'node:module';
import { spawnSync } from 'node:child_process';

const require = createRequire(import.meta.url);
const cli = require.resolve('@react-native-community/cli/build/bin.js');
const workspace = mkdtempSync(join(tmpdir(), 'birthday-rn-bundle-'));
const platforms = ['android', 'ios'];
const maximumBundleBytes = 2_500_000;
const fixtureMarkers = [
  'Interactive UI fixture',
  'Continue with synthetic account fixture',
  'Synthetic Google Contacts fixture',
  'BirthdayAutopilotE2E',
  'birthday-e2e-fixture-v1',
  'Device fixture host rejected',
];
const requiredE2EMarkers = [
  'Interactive UI fixture',
  'BirthdayAutopilotE2E',
  'birthday-e2e-fixture-v1',
];
const forbiddenE2ENativeMarkers = [
  'BirthdayNativeInvalidated',
  'CompanionMessageModule',
  'CompanionReminderModule',
  'NativeBirthdaySpec',
];

try {
  for (const platform of platforms) {
    const bundlePath = join(workspace, `${platform}.bundle`);
    const result = spawnSync(
      process.execPath,
      [
        cli,
        'bundle',
        '--platform',
        platform,
        '--dev',
        'false',
        '--entry-file',
        'index.js',
        '--bundle-output',
        bundlePath,
        '--assets-dest',
        join(workspace, `${platform}-assets`),
      ],
      {
        cwd: process.cwd(),
        encoding: 'utf8',
        env: { ...process.env, FORCE_COLOR: '0', NO_COLOR: '1' },
        maxBuffer: 16 * 1024 * 1024,
      },
    );
    if (result.status !== 0) {
      process.stderr.write(result.stdout ?? '');
      process.stderr.write(result.stderr ?? '');
      process.exitCode = result.status ?? 1;
      break;
    }
    const bundle = readFileSync(bundlePath, 'utf8');
    const bundleBytes = Buffer.byteLength(bundle, 'utf8');
    if (bundleBytes > maximumBundleBytes) {
      process.stderr.write(
        `FAIL ${platform} production JavaScript bundle is ${bundleBytes} bytes; budget is ${maximumBundleBytes}\n`,
      );
      process.exitCode = 1;
      break;
    }
    const leakedMarker = fixtureMarkers.find(marker => bundle.includes(marker));
    if (leakedMarker !== undefined) {
      process.stderr.write(
        `FAIL ${platform} production bundle contains fixture-only copy: ${leakedMarker}\n`,
      );
      process.exitCode = 1;
      break;
    }
    process.stdout.write(
      `PASS ${platform} production JavaScript bundle bytes=${bundleBytes}\n`,
    );
  }

  if (process.exitCode === undefined || process.exitCode === 0) {
    for (const platform of platforms) {
      const bundlePath = join(workspace, `${platform}-e2e.bundle`);
      const result = spawnSync(
        process.execPath,
        [
          cli,
          'bundle',
          '--platform',
          platform,
          '--dev',
          'false',
          '--entry-file',
          'e2e/index.js',
          '--bundle-output',
          bundlePath,
          '--assets-dest',
          join(workspace, `${platform}-e2e-assets`),
        ],
        {
          cwd: process.cwd(),
          encoding: 'utf8',
          env: { ...process.env, FORCE_COLOR: '0', NO_COLOR: '1' },
          maxBuffer: 16 * 1024 * 1024,
        },
      );
      if (result.status !== 0) {
        process.stderr.write(result.stdout ?? '');
        process.stderr.write(result.stderr ?? '');
        process.exitCode = result.status ?? 1;
        break;
      }
      const bundle = readFileSync(bundlePath, 'utf8');
      const bundleBytes = Buffer.byteLength(bundle, 'utf8');
      if (bundleBytes > maximumBundleBytes) {
        process.stderr.write(
          `FAIL ${platform} E2E JavaScript bundle is ${bundleBytes} bytes; budget is ${maximumBundleBytes}\n`,
        );
        process.exitCode = 1;
        break;
      }
      const missingMarker = requiredE2EMarkers.find(
        marker => !bundle.includes(marker),
      );
      if (missingMarker !== undefined) {
        process.stderr.write(
          `FAIL ${platform} E2E bundle is missing fixture marker: ${missingMarker}\n`,
        );
        process.exitCode = 1;
        break;
      }
      const leakedNativeMarker = forbiddenE2ENativeMarkers.find(marker =>
        bundle.includes(marker),
      );
      if (leakedNativeMarker !== undefined) {
        process.stderr.write(
          `FAIL ${platform} E2E bundle contains native product marker: ${leakedNativeMarker}\n`,
        );
        process.exitCode = 1;
        break;
      }
      process.stdout.write(
        `PASS ${platform} isolated E2E JavaScript bundle bytes=${bundleBytes}\n`,
      );
    }
  }
} finally {
  rmSync(workspace, { force: true, recursive: true });
}
