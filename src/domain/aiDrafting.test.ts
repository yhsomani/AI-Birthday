import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { buildAiDraftRequest, classifyAiProviderStatus, normalizeAiDraftResponse } from './aiDrafting';
import { createTestState } from '../test/testState';

describe('aiDrafting contract', () => {
  it('builds provider requests from approved context and excludes private/contact routing data', () => {
    const state = createTestState();
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

  it('includes bounded regeneration feedback in provider requests', () => {
    const state = createTestState();
    const result = buildAiDraftRequest(state, 'c-asha', 'e-asha-bday', 'Birthday', {
      feedback: {
        instructions: ['Make the draft shorter and easier to send.'],
        customInstruction: 'Mention mango lassi, but keep it natural.',
        previousDraftExcerpt: 'Happy birthday Asha! This older draft should guide the rewrite.'
      }
    });

    assert.equal(result.ok, true);
    if (!result.ok) {
      return;
    }

    assert.deepEqual(result.request.regenerationFeedback?.instructions, [
      'Make the draft shorter and easier to send.'
    ]);
    assert.equal(result.request.regenerationFeedback?.customInstruction, 'Mention mango lassi, but keep it natural.');
    assert.match(result.request.regenerationFeedback?.previousDraftExcerpt ?? '', /older draft/i);
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

  it('rejects unsafe, repeated, or wrong-language provider output before draft creation', () => {
    const unsafe = normalizeAiDraftResponse({
      variants: {
        short: 'Happy birthday! Call me at +91 98765 43210 today.',
        standard: 'Happy birthday! Call me at +91 98765 43210 today.',
        warm: 'Happy birthday! Call me at +91 98765 43210 today.'
      }
    });
    const repeated = normalizeAiDraftResponse(
      {
        variants: {
          short: 'Happy birthday Asha! Wishing you a warm day and a year full of good moments.',
          standard: 'Happy birthday Asha! Wishing you a warm day and a year full of good moments.',
          warm: 'Happy birthday Asha! Wishing you a warm day and a year full of good moments.'
        }
      },
      {
        previousMessages: ['Happy birthday Asha! Wishing you a warm day and a year full of good moments.']
      }
    );
    const wrongLanguage = normalizeAiDraftResponse(
      {
        variants: {
          short: 'Happy birthday Asha! Hope your day is special.',
          standard: 'Happy birthday Asha! Wishing you a joyful day and a thoughtful year ahead.',
          warm: 'Happy birthday Asha! Hope today feels personal, warm, and full of love.'
        }
      },
      {
        expectedLanguage: 'Hindi'
      }
    );

    assert.equal(unsafe.ok, false);
    assert.equal(repeated.ok, false);
    assert.equal(wrongLanguage.ok, false);
    if (!unsafe.ok) {
      assert.equal(unsafe.error.kind, 'content-safety');
    }
    if (!repeated.ok) {
      assert.equal(repeated.error.kind, 'content-safety');
    }
    if (!wrongLanguage.ok) {
      assert.equal(wrongLanguage.error.kind, 'wrong-language');
    }
  });

  it('classifies actionable provider failures', () => {
    assert.equal(classifyAiProviderStatus(401).kind, 'auth');
    assert.equal(classifyAiProviderStatus(429).kind, 'quota');
    assert.equal(classifyAiProviderStatus(503).kind, 'server');
    assert.equal(classifyAiProviderStatus(418).kind, 'network');
  });
});
