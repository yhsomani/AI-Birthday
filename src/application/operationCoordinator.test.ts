import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { OperationCoordinator, type OperationTaskResult } from './operationCoordinator';

const fixture = () => {
  let id = 0;
  let tick = 0;
  return new OperationCoordinator({
    createRequestId: () => `request-${++id}`,
    now: () => `2026-07-10T10:00:${String(tick++).padStart(2, '0')}.000Z`
  });
};

describe('operation coordinator', () => {
  it('blocks duplicate taps for the same scope while allowing unrelated work', async () => {
    const coordinator = fixture();
    let finish!: (result: OperationTaskResult<string>) => void;
    const first = coordinator.run(
      'email:message-1',
      () =>
        new Promise<OperationTaskResult<string>>(resolve => {
          finish = resolve;
        })
    );
    const duplicate = await coordinator.run('email:message-1', async () => ({
      status: 'succeeded',
      value: 'duplicate'
    }));
    const unrelated = await coordinator.run('backup:export', async () => ({ status: 'succeeded', value: 'backup' }));
    assert.deepEqual(duplicate, { status: 'already-running', requestId: 'request-1' });
    assert.deepEqual(unrelated, { status: 'succeeded', value: 'backup' });
    finish({ status: 'succeeded', value: 'sent' });
    assert.deepEqual(await first, { status: 'succeeded', value: 'sent' });
  });

  it('cancels obsolete work and ignores its late result', async () => {
    const coordinator = fixture();
    let finish!: (result: OperationTaskResult<string>) => void;
    const obsolete = coordinator.run(
      'ai:contact-1',
      () =>
        new Promise<OperationTaskResult<string>>(resolve => {
          finish = resolve;
        })
    );
    const latest = coordinator.run('ai:contact-1', async () => ({ status: 'succeeded', value: 'latest' }), {
      cancelPrevious: true
    });
    finish({ status: 'succeeded', value: 'obsolete' });
    assert.deepEqual(await obsolete, { status: 'cancelled' });
    assert.deepEqual(await latest, { status: 'succeeded', value: 'latest' });
  });

  it('retries only explicit retryable failures and preserves the request identity', async () => {
    const coordinator = fixture();
    let calls = 0;
    const task = async () => {
      calls += 1;
      return calls === 1
        ? ({ status: 'failed', error: { code: 'offline', retryable: true, summary: 'Network unavailable.' } } as const)
        : ({ status: 'succeeded', value: 'done' } as const);
    };
    await coordinator.run('ai:test', task);
    const firstSnapshot = coordinator.snapshot('ai:test');
    assert.deepEqual(await coordinator.retry('ai:test'), { status: 'succeeded', value: 'done' });
    assert.equal(coordinator.snapshot('ai:test')?.requestId, firstSnapshot?.requestId);

    await coordinator.run('email:unknown', async () => ({
      status: 'unknown',
      error: { code: 'delivery-unknown', retryable: false, summary: 'Delivery status is unknown.' }
    }));
    const blocked = await coordinator.retry('email:unknown');
    assert.equal(blocked.status, 'failed');
  });

  it('redacts route-like private details from observable errors', async () => {
    const coordinator = fixture();
    await coordinator.run('provider:test', async () => ({
      status: 'failed',
      error: { code: 'network', retryable: true, summary: 'Failed for person@example.com at https://secret.test.' }
    }));
    assert.doesNotMatch(coordinator.snapshot('provider:test')?.error?.summary ?? '', /example|secret/);
  });
});
