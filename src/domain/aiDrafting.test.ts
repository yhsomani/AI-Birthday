import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { buildAiDraftRequest, classifyAiProviderStatus, normalizeAiDraftResponse } from './aiDrafting';
import { createInitialState } from '../state/relateReducer';

describe('aiDrafting contract', () => {
  it('builds provider requests from approved context and excludes private/contact routing data', () => {
    const state = createInitialState();
    const result = buildAiDraftRequest(state, 'c-rajesh', 'e-rajesh-work', 'Congratulations');

    assert.equal(result.ok, true);
    if (!result.ok) {
      return;
    }

    const serialized = JSON.stringify(result.request);

    assert.equal(result.request.privacy.excludedPrivateMemoryCount, 1);
    assert.deepEqual(result.request.memories, []);
    assert.match(result.privacySummary, /private item\(s\) excluded/);
    assert.doesNotMatch(serialized, /rajesh@example\.com/i);
    assert.doesNotMatch(serialized, /Private note excluded/i);
    assert.doesNotMatch(serialized, /\+91/);
  });

  it('normalizes provider JSON variants and rejects unusable responses', () => {
    const valid = normalizeAiDraftResponse({
      variants: {
        short: 'Happy birthday! Hope your day feels genuinely special.',
        standard: 'Happy birthday Asha! Wishing you a warm day and a year full of good moments.',
        warm: 'Happy birthday Asha! I hope the day feels personal, easy, and full of people who love you.'
      }
    });
    const invalid = normalizeAiDraftResponse({ variants: { standard: 'too short' } });

    assert.equal(valid.ok, true);
    assert.equal(invalid.ok, false);
    if (!invalid.ok) {
      assert.equal(invalid.error.kind, 'invalid-response');
    }
  });

  it('classifies actionable provider failures', () => {
    assert.equal(classifyAiProviderStatus(401).kind, 'auth');
    assert.equal(classifyAiProviderStatus(429).kind, 'quota');
    assert.equal(classifyAiProviderStatus(503).kind, 'server');
    assert.equal(classifyAiProviderStatus(418).kind, 'network');
  });
});
