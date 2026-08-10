import { spawnSync } from 'node:child_process';
import { mkdtempSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

export const commandAvailable = name => {
  const probe = spawnSync(name, ['--version'], { stdio: 'ignore' });
  return probe.status !== null && probe.error === undefined;
};

export const symlinksAvailable = (() => {
  const directory = mkdtempSync(join(tmpdir(), 'birthday-symlink-probe-'));
  try {
    const target = join(directory, 'target');
    const link = join(directory, 'link');
    writeFileSync(target, 'x');
    symlinkSync(target, link);
    return true;
  } catch {
    return false;
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
})();
