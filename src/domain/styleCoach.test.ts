import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
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
      assert.deepEqual(result.profile.commonGreetings, ['Hey', 'Hi']);
      assert.match(result.profile.representativePreview, /Thinking of you/);
      assert.doesNotMatch(result.profile.representativePreview, /Asha|Mira|Pune/);
      assert.match(result.preview, /Manual samples/);
    }
  });

  it('uses only eligible recent sent messages for opt-in training', () => {
    const initial = createTestState();
    const first = {
      ...initial,
      messages: initial.messages.map(message =>
        message.id === 'msg-mira-checkin'
          ? { ...message, status: 'Sent' as const, sentAt: '2026-07-09T10:00:00.000Z' }
          : message
      )
    };
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
    const next = relateReducer(createTestState(), {
      type: 'trainStyleFromSamples',
      samples: sample
    });

    assert.equal(next.styleProfile.sampleCount, 2);
    assert.match(next.activity[0].detail, /2 sample/);
    assert.doesNotMatch(next.activity[0].detail, /Asha|Mira|new city/);
  });

  it('keeps future-draft style use under explicit control when retraining', () => {
    const disabled = relateReducer(createTestState(), {
      type: 'setStyleEnabled',
      enabled: false
    });
    const retrained = relateReducer(disabled, {
      type: 'trainStyleFromSamples',
      samples: [
        'Hi, I hope your week feels warm and easy. Thinking of you and sending lots of good wishes.',
        'Hey, just checking in and hoping the new city is slowly starting to feel like home.'
      ].join('\n\n')
    });

    assert.equal(disabled.styleProfile.enabledForAiDrafts, false);
    assert.equal(retrained.styleProfile.enabledForAiDrafts, false);
    assert.equal(retrained.styleProfile.sampleCount, 2);
    assert.match(disabled.activity[0].title, /disabled/i);
  });
});
