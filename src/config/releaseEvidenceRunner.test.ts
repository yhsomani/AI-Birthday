import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  executeReleaseEvidenceCommands,
  executableReleaseEvidenceCommands,
  releaseEvidenceCommandDefinitionsAreSynchronized,
  type EvidenceCommandRunner
} from './releaseEvidenceRunner';

describe('release evidence command runner', () => {
  it('keeps executable checks synchronized with report command definitions', () => {
    assert.equal(releaseEvidenceCommandDefinitionsAreSynchronized(), true);
    assert.deepEqual(
      executableReleaseEvidenceCommands.map(item => item.command),
      [
        'npm run typecheck',
        'npm test',
        'npm run test:native-prebuild',
        'npm audit --audit-level=moderate',
        'npx expo install --check',
        'npx expo export --platform web --output-dir reports/web-export',
        'git diff --check'
      ]
    );
  });

  it('derives status, timing, exit code, and output hashes from executed commands', () => {
    const calls: { executable: string; args: string[] }[] = [];
    const runner: EvidenceCommandRunner = (executable, args) => {
      calls.push({ executable, args });
      return {
        status: calls.length === 2 ? 7 : 0,
        stdout: `stdout-${calls.length}`,
        stderr: `stderr-${calls.length}`,
        error: undefined
      };
    };
    const instants = Array.from(
      { length: executableReleaseEvidenceCommands.length * 2 },
      (_, index) => new Date(Date.UTC(2026, 6, 10, 0, 0, 0, index * 10))
    );
    const evidence = executeReleaseEvidenceCommands({
      rootDir: '/workspace',
      runner,
      now: () => instants.shift()!
    });

    assert.equal(calls.length, executableReleaseEvidenceCommands.length);
    assert.equal(evidence[0]?.status, 'Passed');
    assert.equal(evidence[1]?.status, 'Failed');
    assert.equal(evidence[1]?.exitCode, 7);
    assert.equal(evidence[0]?.durationMs, 10);
    assert.match(evidence[0]?.outputSha256 ?? '', /^[a-f0-9]{64}$/);
    assert.match(evidence[0]?.startedAt ?? '', /^2026-07-10T/);
    assert.match(evidence[0]?.completedAt ?? '', /^2026-07-10T/);
  });

  it('fails a check when its executable cannot be started', () => {
    const runner: EvidenceCommandRunner = () => ({
      status: null,
      stdout: '',
      stderr: '',
      error: Object.assign(new Error('spawn unavailable'), { code: 'ENOENT' })
    });
    const evidence = executeReleaseEvidenceCommands({ rootDir: '/workspace', runner });

    assert.ok(evidence.every(command => command.status === 'Failed'));
    assert.ok(evidence.every(command => command.exitCode === 1));
    assert.ok(evidence.every(command => /could not run/.test(command.detail ?? '')));
  });
});
