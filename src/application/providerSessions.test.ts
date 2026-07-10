import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { readValidatedProviderSession } from './providerSessions';

describe('short-lived provider session boundary', () => {
  it('accepts only bounded, unexpired authenticated sessions', async () => {
    const signal = new AbortController().signal;
    const valid = await readValidatedProviderSession(
      {
        getActiveSession: async () => ({
          accessToken: 'authenticated-token-value',
          expiresAt: '2026-07-10T10:10:00.000Z'
        }),
        hasActiveSession: () => true
      },
      'ai',
      signal,
      new Date('2026-07-10T10:00:00.000Z')
    );
    assert.equal(valid?.accessToken, 'authenticated-token-value');

    const expired = await readValidatedProviderSession(
      {
        getActiveSession: async () => ({
          accessToken: 'authenticated-token-value',
          expiresAt: '2026-07-10T10:00:10.000Z'
        }),
        hasActiveSession: () => true
      },
      'email',
      signal,
      new Date('2026-07-10T10:00:00.000Z')
    );
    assert.equal(expired, undefined);
  });

  it('honors cancellation without retaining token material', async () => {
    const controller = new AbortController();
    controller.abort();
    await assert.rejects(
      () =>
        readValidatedProviderSession(
          {
            getActiveSession: async () => ({
              accessToken: 'secret-token-never-returned',
              expiresAt: '2026-07-10T10:10:00.000Z'
            }),
            hasActiveSession: () => true
          },
          'ai',
          controller.signal,
          new Date('2026-07-10T10:00:00.000Z')
        ),
      /cancelled/
    );
  });
});
