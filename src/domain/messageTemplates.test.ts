import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import {
  buildLocalTemplateFallback,
  buildMessageTemplateLibrary,
  buildTemplateDraft,
  findMessageTemplates,
  firstRenderedTemplateForContact,
  messageTemplates,
  renderMessageTemplate
} from './messageTemplates';
import type { ComposerReason, Contact } from './types';

const reasons: ComposerReason[] = [
  'Birthday',
  'Check-in',
  'Thanks',
  'Congratulations',
  'Apology',
  'Follow-up',
  'Custom'
];

describe('message template library contract', () => {
  it('finds templates by occasion and prioritizes contact tone matches', () => {
    const birthday = findMessageTemplates('Birthday', 'Hinglish', ['Hinglish', 'Warm']);

    assert.equal(birthday[0].language, 'Hinglish');
    assert.ok(birthday.some(template => template.tone === 'Hinglish'));
    assert.ok(birthday.every(template => template.language === 'Hinglish'));
  });

  it('has an offline template in every configured language for every composer reason', () => {
    for (const reason of reasons) {
      for (const language of ['English', 'Hindi', 'Hinglish'] as const) {
        const templates = messageTemplates.filter(
          template => template.reason === reason && template.language === language
        );
        assert.ok(templates.length > 0, `${reason} must have a ${language} local template`);
      }
    }
  });

  it('renders editable template text with contact context but excludes private memories', () => {
    const state = createTestState();
    const contact = state.contacts.find(item => item.id === 'c-rajesh')!;
    const rendered = firstRenderedTemplateForContact(state, contact.id, 'Custom') ?? '';
    const direct = renderMessageTemplate(
      {
        id: 'test',
        reason: 'Custom',
        tone: 'Warm',
        language: 'English',
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

  it('renders factual context without exposing avoidance, language, or tone instructions', () => {
    const base = createTestState();
    const state = {
      ...base,
      memories: [
        {
          ...base.memories[0],
          id: 'avoid',
          body: 'Avoid in messages: a difficult work topic.'
        },
        {
          ...base.memories[0],
          id: 'language',
          body: 'Preferred language/style: formal Hindi.'
        },
        {
          ...base.memories[0],
          id: 'tone',
          body: 'Preferred tone: respectful and no emoji.'
        },
        {
          ...base.memories[0],
          id: 'fact',
          category: 'Milestone' as const,
          body: 'Finished a first marathon in May.'
        }
      ]
    };
    const rendered = firstRenderedTemplateForContact(state, 'c-asha', 'Custom') ?? '';
    const mixedDirect = renderMessageTemplate(
      {
        id: 'test-policy',
        reason: 'Custom',
        tone: 'Warm',
        language: 'English',
        title: 'Test policy',
        body: 'Hi {{name}}. {{context}}'
      },
      base.contacts[0],
      'Won a local design award. Avoid in messages: the former employer.'
    );

    assert.match(rendered, /Finished a first marathon in May/i);
    assert.doesNotMatch(
      rendered,
      /Avoid in messages|difficult work topic|Preferred language|formal Hindi|Preferred tone|no emoji/i
    );
    assert.match(mixedDirect, /Won a local design award/i);
    assert.doesNotMatch(mixedDirect, /Avoid in messages|former employer/i);
  });

  it('creates review-first template drafts without AI provider access', () => {
    const state = createTestState();
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

  it('builds a contact-personalized template library view by occasion and tone', () => {
    const state = createTestState();
    const library = buildMessageTemplateLibrary(state, {
      contactId: 'c-asha',
      reason: 'Birthday',
      tone: 'Hinglish'
    });

    assert.equal(library.ok, true);
    if (library.ok) {
      assert.equal(library.selectedTemplate.tone, 'Hinglish');
      assert.equal(library.selectedTemplate.language, 'Hinglish');
      assert.equal(library.templateSelection.exactLanguageMatch, true);
      assert.equal(library.templateSelection.exactToneMatch, true);
      assert.ok(library.toneOptions.includes('Hinglish'));
      assert.match(library.renderedBody, /Asha/);
      assert.equal(library.action.enabled, true);
      assert.match(library.action.detail, /review-first/i);
    }
  });

  it('falls back to available templates when the requested tone has no exact match', () => {
    const state = createTestState();
    const library = buildMessageTemplateLibrary(state, {
      contactId: 'c-rajesh',
      reason: 'Congratulations',
      tone: 'Playful'
    });

    assert.equal(library.ok, true);
    if (library.ok) {
      assert.equal(library.selectedTemplate.tone, 'Respectful');
      assert.equal(library.selectedTemplate.language, 'English');
      assert.equal(library.templateSelection.exactToneMatch, false);
      assert.match(library.contextDetail, /No exact Playful template/i);
      assert.match(library.contextDetail, /keeping the English language target/i);
      assert.doesNotMatch(library.renderedBody, /Private note excluded/i);
    }
  });

  it('blocks a selected wrong-language template and visibly keeps the contact language', () => {
    const base = createTestState();
    const hindiContact: Contact = {
      ...base.contacts[0],
      language: 'Hindi',
      tone: ['Warm']
    };
    const library = buildMessageTemplateLibrary(
      { ...base, contacts: [hindiContact, ...base.contacts.slice(1)] },
      {
        contactId: hindiContact.id,
        reason: 'Birthday',
        tone: 'Warm',
        selectedTemplateId: 'birthday-warm'
      }
    );

    assert.equal(library.ok, true);
    if (library.ok) {
      assert.equal(library.selectedTemplate.language, 'Hindi');
      assert.equal(library.templateSelection.wrongLanguageTemplateBlocked, true);
      assert.match(library.templateSelection.detail, /requested English template was not used/i);
      assert.match(library.renderedBody, /[\u0900-\u097f]/);
      assert.doesNotMatch(library.renderedBody, /Wishing you a day/i);
    }
  });

  it('creates language-correct review-first local fallbacks for every composer reason', () => {
    const base = createTestState();

    for (const language of ['English', 'Hindi', 'Hinglish'] as const) {
      const contact: Contact = {
        ...base.contacts[0],
        id: `language-${language}`,
        name: language === 'Hindi' ? 'आशा' : 'Asha',
        language,
        tone: ['Warm', 'Respectful', 'Concise', 'Formal', 'Hinglish']
      };
      const state = { ...base, contacts: [contact] };

      for (const reason of reasons) {
        const fallback = buildLocalTemplateFallback(state, contact.id, reason);
        assert.equal(fallback.ok, true, `${language} ${reason} fallback should exist`);
        if (!fallback.ok) continue;
        assert.equal(fallback.template.language, language);
        assert.equal(fallback.selection.exactLanguageMatch, true);
        assert.equal(fallback.selection.wrongLanguageTemplateBlocked, false);
        assert.ok(fallback.body.length >= 12);
        if (language === 'English') {
          assert.doesNotMatch(fallback.body, /[\u0900-\u097f]/);
          assert.match(fallback.variants.warm, /thoughtful and personal/i);
        } else if (language === 'Hindi') {
          assert.match(fallback.body, /[\u0900-\u097f]/);
          assert.match(fallback.variants.warm, /आशा है यह संदेश/);
        } else {
          assert.match(fallback.body, /\b(?:aaj|aap|bahut|bas|dil|umeed|yeh)\b/i);
          assert.match(fallback.variants.warm, /Umeed hai yeh message/i);
        }
      }
    }
  });

  it('keeps shorter and warmer regeneration fallback adjustments in the configured language', () => {
    const base = createTestState();
    const hindiContact: Contact = {
      ...base.contacts[0],
      language: 'Hindi',
      tone: ['Warm']
    };
    const fallback = buildLocalTemplateFallback(
      { ...base, contacts: [hindiContact, ...base.contacts.slice(1)] },
      hindiContact.id,
      'Birthday',
      {
        averageLength: 80,
        feedback: {
          instructions: ['Make the draft shorter and warmer.']
        }
      }
    );

    assert.equal(fallback.ok, true);
    if (fallback.ok) {
      assert.ok(fallback.body.length <= 80);
      assert.match(fallback.body, /[\u0900-\u097f]/);
      assert.doesNotMatch(fallback.variants.warm, /thoughtful|personal/i);
      assert.match(fallback.variants.warm, /आशा है यह संदेश/);
    }
  });

  it('keeps duplicate warnings visible on successive review-first template drafts', () => {
    const state = createTestState();
    const first = buildTemplateDraft(
      state,
      {
        contactId: 'c-mira',
        reason: 'Thanks',
        body: 'Hi Mira, thank you for being so thoughtful and supportive.'
      },
      'template-first'
    );
    assert.equal(first.ok, true);
    if (!first.ok) return;

    const second = buildTemplateDraft(
      {
        ...state,
        messages: [first.draft, ...state.messages]
      },
      {
        contactId: 'c-mira',
        reason: 'Thanks',
        body: 'Hi Mira, thank you again for being so thoughtful and supportive.'
      },
      'template-second'
    );

    assert.equal(first.draft.quality, 'Template fallback');
    assert.equal(second.ok, true);
    if (second.ok) {
      assert.match(second.draft.duplicateWarning ?? '', /similar message draft/i);
    }
  });

  it('does not warn merely because an eventless template shares a reason', () => {
    const state = createTestState();
    const existing = {
      ...state.messages[1],
      eventId: undefined,
      reason: 'Thanks' as const,
      body: 'Congratulations on completing the marathon after months of disciplined training.'
    };
    const result = buildTemplateDraft(
      { ...state, messages: [existing] },
      {
        contactId: existing.contactId,
        reason: 'Thanks',
        body: 'Hi Mira, thank you for checking in after my move. I really appreciated your thoughtful note.'
      },
      'template-unrelated'
    );

    assert.equal(result.ok, true);
    if (result.ok) assert.equal(result.draft.duplicateWarning, undefined);
  });
});
