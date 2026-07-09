import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createInitialState, relateReducer } from '../state/relateReducer';
import { analyzeManualStyleSamples, analyzeSentMessageStyle, eligibleSentStyleMessages } from './styleCoach';

describe('style coach contract', () => {
  it('rejects sparse manual samples with actionable guidance', () => {
    const result = analyzeManualStyleSamples('Thanks!');

    assert.equal(result.ok, false);
    if (!result.ok) {
      assert.match(result.message, /at least two/i);
    }
  });

  it('learns a local profile from representative manual samples', () => {
    const result = analyzeManualStyleSamples(
      [
        'Hey Asha, I was thinking of you today. Hope your week feels light and full of good moments.',
        'Hi Mira, just checking in. No rush to reply, but I hope Pune is slowly feeling like home.',
        'Thank you for being there. I really appreciate your kindness and the way you show up.'
      ].join('\n\n')
    );

    assert.equal(result.ok, true);
    if (result.ok) {
      assert.equal(result.profile.confidence, 'Growing');
      assert.match(result.profile.formality, /Warm|Balanced/);
      assert.equal(result.profile.sampleCount, 3);
      assert.match(result.preview, /Manual samples/);
    }
  });

  it('uses only eligible recent sent messages for opt-in training', () => {
    const first = relateReducer(createInitialState(), {
      type: 'manualHandoff',
      messageId: 'msg-mira-checkin',
      nowIso: '2026-07-09T10:00:00.000Z'
    });
    const second = {
      ...first,
      messages: [
        {
          ...first.messages[0],
          id: 'msg-mira-second-sent',
          contactId: 'c-mira',
          status: 'Sent' as const,
          body: 'Hey Mira, just wanted to say I hope the new design role is starting well and that Pune feels kinder this week.',
          sentAt: '2026-07-10T10:00:00.000Z'
        },
        ...first.messages
      ]
    };
    const eligible = eligibleSentStyleMessages(second);
    const result = analyzeSentMessageStyle(second);

    assert.equal(eligible.length, 2);
    assert.equal(result.ok, true);
    if (result.ok) {
      assert.equal(result.source, 'Recent sent messages');
      assert.equal(result.profile.sampleCount, 2);
    }
  });

  it('updates the reducer profile without logging raw sample text', () => {
    const sample = [
      'Hi Asha, I hope your day feels warm and easy. Thinking of you and sending lots of good wishes.',
      'Hey Mira, just checking in. Hope the new city is becoming friendlier and the role is going well.'
    ].join('\n\n');
    const next = relateReducer(createInitialState(), {
      type: 'trainStyleFromSamples',
      samples: sample
    });

    assert.equal(next.styleProfile.sampleCount, 2);
    assert.match(next.activity[0].detail, /2 sample/);
    assert.doesNotMatch(next.activity[0].detail, /Asha|Mira|new city/);
  });
});
