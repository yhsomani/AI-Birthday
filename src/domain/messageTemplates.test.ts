import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createInitialState, relateReducer } from '../state/relateReducer';
import {
  buildTemplateDraft,
  findMessageTemplates,
  firstRenderedTemplateForContact,
  renderMessageTemplate
} from './messageTemplates';

describe('message template library contract', () => {
  it('finds templates by occasion and prioritizes contact tone matches', () => {
    const birthday = findMessageTemplates('Birthday', ['Hinglish', 'Warm']);

    assert.equal(birthday[0].tone, 'Warm');
    assert.ok(birthday.some(template => template.tone === 'Hinglish'));
  });

  it('renders editable template text with contact context but excludes private memories', () => {
    const state = createInitialState();
    const contact = state.contacts.find(item => item.id === 'c-rajesh')!;
    const rendered = firstRenderedTemplateForContact(state, contact.id, 'Custom') ?? '';
    const direct = renderMessageTemplate(
      {
        id: 'test',
        reason: 'Custom',
        tone: 'Warm',
        title: 'Test',
        body: 'Hi {{name}}. {{context}}'
      },
      contact,
      'Public context only.'
    );

    assert.match(rendered, /Rajesh/);
    assert.doesNotMatch(rendered, /Private note excluded/);
    assert.match(direct, /Public context only/);
  });

  it('creates review-first template drafts without AI provider access', () => {
    const state = createInitialState();
    const result = buildTemplateDraft(
      state,
      {
        contactId: 'c-mira',
        reason: 'Check-in',
        templateId: 'checkin-concise',
        body: 'Hi Mira, just checking in and hoping your week is going smoothly.'
      },
      123
    );

    assert.equal(result.ok, true);
    if (result.ok) {
      assert.equal(result.draft.id, 'template-123-2');
      assert.equal(result.draft.status, 'Needs review');
      assert.equal(result.draft.quality, 'Template fallback');
      assert.equal(result.draft.channel, 'Manual');
    }
  });

  it('adds template drafts through the reducer and keeps duplicate warnings visible', () => {
    const state = createInitialState();
    const first = relateReducer(state, {
      type: 'createTemplateDraft',
      contactId: 'c-mira',
      reason: 'Thanks',
      body: 'Hi Mira, thank you for being so thoughtful and supportive.'
    });
    const second = relateReducer(first, {
      type: 'createTemplateDraft',
      contactId: 'c-mira',
      reason: 'Thanks',
      body: 'Hi Mira, thank you again for being so thoughtful and supportive.'
    });

    assert.equal(first.activeScreen, 'wishPreview');
    assert.equal(first.messages[0].quality, 'Template fallback');
    assert.match(second.messages[0].duplicateWarning ?? '', /similar message draft/i);
  });
});
