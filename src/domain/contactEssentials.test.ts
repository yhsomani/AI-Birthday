import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { validateContactEssentials } from './contactEssentials';

describe('contact essentials contract', () => {
  it('normalizes valid profile fields before save', () => {
    const result = validateContactEssentials(
      {
        name: '  Dev   Kapoor ',
        relationship: '  College friend ',
        phone: ' +91 98765-43210 ',
        email: ' DEV@EXAMPLE.COM ',
        language: 'Hinglish',
        notesSummary: '  Likes books   and coffee. '
      },
      'WhatsApp'
    );

    assert.equal(result.ok, true);
    if (result.ok) {
      assert.equal(result.value.name, 'Dev Kapoor');
      assert.equal(result.value.relationship, 'College friend');
      assert.equal(result.value.phone, '+919876543210');
      assert.equal(result.value.email, 'dev@example.com');
      assert.equal(result.value.notesSummary, 'Likes books and coffee.');
    }
  });

  it('blocks invalid identity, channel route, and overlong notes fields', () => {
    assert.equal(
      validateContactEssentials(
        {
          name: '',
          relationship: 'Friend',
          phone: '+919876543210',
          email: '',
          language: 'English',
          notesSummary: ''
        },
        'SMS'
      ).ok,
      false
    );
    assert.equal(
      validateContactEssentials(
        {
          name: 'Dev',
          relationship: 'Friend',
          phone: '',
          email: '',
          language: 'English',
          notesSummary: ''
        },
        'WhatsApp'
      ).ok,
      false
    );
    assert.equal(
      validateContactEssentials(
        {
          name: 'Dev',
          relationship: 'Friend',
          phone: '+919876543210',
          email: '',
          language: 'English',
          notesSummary: 'x'.repeat(501)
        },
        'Manual'
      ).ok,
      false
    );
  });

  it('allows optional route fields when the selected channel does not need them', () => {
    const result = validateContactEssentials(
      {
        name: 'Nisha Rao',
        relationship: 'Friend',
        phone: '',
        email: '',
        language: 'Hindi',
        notesSummary: ''
      },
      'Manual'
    );

    assert.equal(result.ok, true);
    if (result.ok) {
      assert.equal(result.value.phone, undefined);
      assert.equal(result.value.email, undefined);
    }
  });
});
