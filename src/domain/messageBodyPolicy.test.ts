import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { MESSAGE_BODY_LIMITS, validateMessageBodyForChannel } from './messageBodyPolicy';
import type { MessageChannel } from './types';

const message = (channel: MessageChannel, body: string) => ({ channel, body });

describe('message body policy', () => {
  it('blocks blank or too-short message text before approval or delivery', () => {
    const result = validateMessageBodyForChannel(message('SMS', 'Hi'));

    assert.equal(result.ok, false);
    if (!result.ok) {
      assert.match(result.message, /longer/i);
      assert.match(result.message, /too short/i);
    }
  });

  it('allows multipart SMS with explicit review guidance', () => {
    const result = validateMessageBodyForChannel(message('SMS', 'A'.repeat(170)));

    assert.equal(result.ok, true);
    if (result.ok) {
      assert.equal(result.smsSegments, 2);
      assert.match(result.warning ?? '', /2 parts/i);
      assert.match(result.warning ?? '', /Review before sending/i);
    }
  });

  it('blocks SMS that exceeds the safe multipart cap', () => {
    const result = validateMessageBodyForChannel(message('SMS', 'A'.repeat(MESSAGE_BODY_LIMITS.SMS + 1)));

    assert.equal(result.ok, false);
    if (!result.ok) {
      assert.equal(result.limit, MESSAGE_BODY_LIMITS.SMS);
      assert.match(result.message, /Shorten the message or switch channel/i);
    }
  });

  it('uses UCS-2 limits for Hindi and emoji instead of a flat character estimate', () => {
    const hindi = validateMessageBodyForChannel(message('SMS', 'न'.repeat(100)));
    assert.equal(hindi.ok, true);
    if (hindi.ok) {
      assert.equal(hindi.smsEncoding, 'UCS-2');
      assert.equal(hindi.smsSegments, 2);
      assert.match(hindi.warning ?? '', /2 parts/i);
    }

    const emoji = validateMessageBodyForChannel(message('SMS', '🙂'.repeat(40)));
    assert.equal(emoji.ok, true);
    if (emoji.ok) {
      assert.equal(emoji.smsEncoding, 'UCS-2');
      assert.equal(emoji.smsSegments, 2);
    }

    const tooLong = validateMessageBodyForChannel(message('SMS', 'न'.repeat(403)));
    assert.equal(tooLong.ok, false);
    if (!tooLong.ok) assert.equal(tooLong.limit, 402);
  });

  it('blocks non-SMS channel bodies that exceed their channel cap', () => {
    const cases: MessageChannel[] = ['WhatsApp', 'Email', 'Manual'];

    cases.forEach(channel => {
      const result = validateMessageBodyForChannel(message(channel, 'A'.repeat(MESSAGE_BODY_LIMITS[channel] + 1)));

      assert.equal(result.ok, false);
      if (!result.ok) {
        assert.equal(result.limit, MESSAGE_BODY_LIMITS[channel]);
        assert.match(result.message, new RegExp(channel));
      }
    });
  });
});
