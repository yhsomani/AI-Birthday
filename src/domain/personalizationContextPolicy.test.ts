import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { MemoryNote } from './types';
import {
  buildMemoryPersonalizationContext,
  classifyMemoryForPersonalization,
  firstMentionableMemoryTextForContact,
  MAX_GENERATION_CONSTRAINT_CHARACTERS,
  sanitizeRecipientVisiblePersonalizationText
} from './personalizationContextPolicy';

const memory = (id: string, body: string, category: MemoryNote['category'] = 'Preference'): MemoryNote => ({
  id,
  contactId: 'contact-1',
  category,
  body,
  pinned: false,
  createdAt: '2026-07-10T00:00:00.000Z'
});

describe('personalization context policy', () => {
  it('separates recipient-mentionable facts from mixed message guidance', () => {
    const classified = classifyMemoryForPersonalization(
      memory('mixed', 'Favorite dessert is mango lassi. Mention family jokes lightly.')
    );

    assert.equal(classified.classification, 'mixed');
    assert.equal(classified.mentionableText, 'Favorite dessert is mango lassi.');
    assert.equal(classified.constraints.length, 1);
    assert.match(classified.constraints[0].instruction, /Mention family jokes lightly/i);
  });

  it('keeps avoidance, language, and tone guidance out of recipient-visible text', () => {
    const values = [
      'Avoid in messages: their former employer.',
      'Preferred language/style: Hindi with simple wording.',
      'Preferred tone: respectful and no emoji.'
    ];

    for (const value of values) {
      assert.equal(sanitizeRecipientVisiblePersonalizationText(value), undefined);
    }

    assert.equal(
      sanitizeRecipientVisiblePersonalizationText('Message should mention: the recent marathon.'),
      'the recent marathon.'
    );
    assert.equal(
      sanitizeRecipientVisiblePersonalizationText(
        'Message should mention: the recent marathon. Avoid in messages: an old injury.'
      ),
      'the recent marathon.'
    );
    assert.equal(sanitizeRecipientVisiblePersonalizationText('Tone should be formal.'), undefined);
  });

  it('excludes private and sensitive memories while bounding generation constraints', () => {
    const context = buildMemoryPersonalizationContext([
      memory('fact', 'Completed a first marathon in May.', 'Milestone'),
      memory('avoid', `Avoid in messages: ${'a'.repeat(400)}.`),
      memory('private', 'Secret family medical detail.', 'Private'),
      memory('route', 'New email is hidden@example.com.', 'General')
    ]);

    assert.deepEqual(
      context.mentionableFacts.map(item => item.text),
      ['Completed a first marathon in May.']
    );
    assert.equal(context.generationConstraints.length, 1);
    assert.ok(context.generationConstraints[0].instruction.length <= MAX_GENERATION_CONSTRAINT_CHARACTERS);
    assert.equal(context.excludedPrivateMemoryCount, 1);
    assert.equal(context.excludedSensitiveMemoryCount, 1);
    assert.doesNotMatch(JSON.stringify(context), /medical detail|hidden@example/i);
  });

  it('provides a reducer-safe helper that returns only the first mentionable fact', () => {
    const state = {
      memories: [
        memory('avoid', 'Avoid in messages: office gossip.'),
        memory('fact', 'Recently adopted a rescue dog.', 'General')
      ]
    };

    assert.equal(firstMentionableMemoryTextForContact(state, 'contact-1'), 'Recently adopted a rescue dog.');
    assert.equal(firstMentionableMemoryTextForContact(state, 'contact-1', ['fact']), undefined);
  });
});
