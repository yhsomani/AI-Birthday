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
const fixtureMarkers = [
  'Interactive UI fixture',
  'Continue with synthetic account fixture',
  'Synthetic Google Contacts fixture',
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
    const leakedMarker = fixtureMarkers.find(marker => bundle.includes(marker));
    if (leakedMarker !== undefined) {
      process.stderr.write(
        `FAIL ${platform} production bundle contains fixture-only copy: ${leakedMarker}\n`,
      );
      process.exitCode = 1;
      break;
    }
    process.stdout.write(`PASS ${platform} production JavaScript bundle\n`);
  }
} finally {
  rmSync(workspace, { force: true, recursive: true });
}
