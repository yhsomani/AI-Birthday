import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { buildAiDraftRequest } from '../domain/aiDrafting';
import { createInitialState } from '../state/relateReducer';
import { requestAiDraft } from './aiProviderClient';

describe('aiProviderClient', () => {
  it('posts the sanitized request to the configured endpoint', async () => {
    const state = createInitialState();
    const request = buildAiDraftRequest(state, 'c-asha', 'e-asha-bday', 'Birthday');
    assert.equal(request.ok, true);
    if (!request.ok) {
      return;
    }

    let postedBody = '';
    const result = await requestAiDraft(
      request.request,
      {
        endpoint: 'https://ai.example.test/draft',
        timeoutMs: 1000
      },
      async (_input, init) => {
        postedBody = init.body;
        return {
          ok: true,
          status: 200,
          json: async () => ({
            variants: {
              short: 'Happy birthday Asha! Hope your day is full of warmth.',
              standard: 'Happy birthday Asha! Wishing you a personal, joyful day and a beautiful year ahead.',
              warm: 'Happy birthday Asha! I hope today feels easy, loved, and full of the good chaos you enjoy.'
            }
          })
        };
      }
    );

    assert.equal(result.ok, true);
    assert.doesNotMatch(postedBody, /\+919876543210/);
    assert.doesNotMatch(postedBody, /Private note excluded/i);
  });

  it('returns not-configured before attempting network access', async () => {
    const state = createInitialState();
    const request = buildAiDraftRequest(state, 'c-asha', undefined, 'Check-in');
    assert.equal(request.ok, true);
    if (!request.ok) {
      return;
    }

    const result = await requestAiDraft(request.request, { timeoutMs: 1000 });

    assert.equal(result.ok, false);
    if (!result.ok) {
      assert.equal(result.error.kind, 'not-configured');
    }
  });
});
