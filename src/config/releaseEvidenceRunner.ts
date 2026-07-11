import { spawnSync, type SpawnSyncReturns } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import {
  defaultReleaseEvidenceCommands,
  type ReleaseEvidenceCommand,
  type ReleaseEvidenceCommandId,
  type ReleaseEvidenceProvenance
} from './releaseEvidence';

export interface EvidenceCommandSpec {
  id: ReleaseEvidenceCommandId;
  command: string;
  executable: string;
  args: string[];
  timeoutMs?: number;
}

export type EvidenceCommandRunner = (
  executable: string,
  args: string[],
  options: { cwd: string; encoding: 'utf8'; timeout: number; maxBuffer: number }
) => Pick<SpawnSyncReturns<string>, 'status' | 'stdout' | 'stderr' | 'error'>;

export interface ExecuteEvidenceOptions {
  rootDir: string;
  runner?: EvidenceCommandRunner;
  now?: () => Date;
  onStart?: (command: string) => void;
  onOutput?: (command: string, stdout: string, stderr: string) => void;
}

const sha256 = (value: string | Buffer) => createHash('sha256').update(value).digest('hex');
const npmExecutable = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const npxExecutable = process.platform === 'win32' ? 'npx.cmd' : 'npx';

export const executableReleaseEvidenceCommands: EvidenceCommandSpec[] = [
  {
    id: 'typecheck',
    command: 'npm run typecheck',
    executable: npmExecutable,
    args: ['run', 'typecheck']
  },
  { id: 'lint', command: 'npm run lint', executable: npmExecutable, args: ['run', 'lint'] },
  {
    id: 'format-check',
    command: 'npm run format:check',
    executable: npmExecutable,
    args: ['run', 'format:check']
  },
  {
    id: 'test-coverage',
    command: 'npm run test:coverage',
    executable: npmExecutable,
    args: ['run', 'test:coverage']
  },
  {
    id: 'native-prebuild',
    command: 'npm run test:native-prebuild',
    executable: npmExecutable,
    args: ['run', 'test:native-prebuild'],
    timeoutMs: 20 * 60 * 1000
  },
  {
    id: 'audit',
    command: 'npm audit --audit-level=moderate',
    executable: npmExecutable,
    args: ['audit', '--audit-level=moderate']
  },
  {
    id: 'expo-dependencies',
    command: 'npx expo install --check',
    executable: npxExecutable,
    args: ['expo', 'install', '--check']
  },
  { id: 'diff-check', command: 'git diff --check', executable: 'git', args: ['diff', '--check'] }
];

const defaultRunner: EvidenceCommandRunner = (executable, args, options) =>
  spawnSync(executable, args, {
    ...options,
    env: process.env
  });

export const executeReleaseEvidenceCommands = (options: ExecuteEvidenceOptions): ReleaseEvidenceCommand[] => {
  const runner = options.runner ?? defaultRunner;
  const now = options.now ?? (() => new Date());

  return executableReleaseEvidenceCommands.map(spec => {
    options.onStart?.(spec.command);
    const started = now();
    const result = runner(spec.executable, [...spec.args], {
      cwd: options.rootDir,
      encoding: 'utf8',
      timeout: spec.timeoutMs ?? 10 * 60 * 1000,
      maxBuffer: 32 * 1024 * 1024
    });
    const completed = now();
    const stdout = result.stdout ?? '';
    const stderr = result.stderr ?? '';
    const output = `${stdout}\n${stderr}`;
    const exitCode = result.status ?? 1;
    options.onOutput?.(spec.command, stdout, stderr);

    return {
      id: spec.id,
      command: spec.command,
      status: exitCode === 0 ? 'Passed' : 'Failed',
      exitCode,
      startedAt: started.toISOString(),
      completedAt: completed.toISOString(),
      durationMs: Math.max(0, completed.getTime() - started.getTime()),
      outputSha256: sha256(output),
      detail: result.error
        ? `Command could not run (${result.error.name}). See the local or CI log for details.`
        : `Command exited with code ${exitCode}. Output retained in the local or CI log; only its SHA-256 is stored here.`
    };
  });
};

const runGit = (rootDir: string, args: string[]) => {
  const result = spawnSync('git', args, {
    cwd: rootDir,
    encoding: 'utf8',
    timeout: 30_000,
    maxBuffer: 32 * 1024 * 1024
  });
  return result.status === 0 ? result.stdout.trimEnd() : '';
};

const readNpmVersion = (rootDir: string) => {
  const result = spawnSync(npmExecutable, ['--version'], {
    cwd: rootDir,
    encoding: 'utf8',
    timeout: 30_000,
    maxBuffer: 1024 * 1024
  });
  return result.status === 0 ? result.stdout.trim() : '';
};

export const collectReleaseEvidenceProvenance = (rootDir: string): ReleaseEvidenceProvenance => {
  const commitSha = runGit(rootDir, ['rev-parse', 'HEAD']);
  const status = runGit(rootDir, ['status', '--porcelain=v1', '--untracked-files=all']);
  const trackedDiff = runGit(rootDir, ['diff', '--binary', 'HEAD', '--']);
  const lockfile = readFileSync(join(rootDir, 'package-lock.json'));
  const githubActions = process.env.GITHUB_ACTIONS === 'true';

  return {
    schemaVersion: 2,
    commitSha,
    dirty: status.length > 0,
    workingTreeSha256: sha256(`${status}\u0000${trackedDiff}`),
    lockfileSha256: sha256(lockfile),
    nodeVersion: process.version,
    npmVersion: readNpmVersion(rootDir),
    platform: process.platform,
    architecture: process.arch,
    runner: githubActions ? 'github-actions' : 'local',
    ...(githubActions
      ? {
          ci: {
            repository: process.env.GITHUB_REPOSITORY,
            runId: process.env.GITHUB_RUN_ID,
            runAttempt: process.env.GITHUB_RUN_ATTEMPT,
            workflowRef: process.env.GITHUB_WORKFLOW_REF
          }
        }
      : {})
  };
};

export const releaseEvidenceCommandDefinitionsAreSynchronized = () =>
  defaultReleaseEvidenceCommands.every((command, index) => {
    const executable = executableReleaseEvidenceCommands[index];
    return executable?.id === command.id && executable.command === command.command;
  }) && defaultReleaseEvidenceCommands.length === executableReleaseEvidenceCommands.length;
