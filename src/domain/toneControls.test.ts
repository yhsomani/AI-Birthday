import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
import { buildTonePreferenceSummary } from './toneControls';

describe('recipient-specific tone controls contract', () => {
  it('explains inherited group tone impact for AI drafts without retraining global style', () => {
    const state = relateReducer(createTestState(), {
      type: 'useGroupDefaultsForContact',
      contactId: 'c-mira'
    });
    const summary = buildTonePreferenceSummary(state, 'c-mira', 'msg-mira-checkin');

    assert.equal(summary.status, 'ready');
    assert.equal(summary.contactName, 'Mira Shah');
    assert.deepEqual(summary.tones, ['Warm', 'Playful']);
    assert.equal(summary.toneSource, 'group');
    assert.equal(summary.sourceLabel, 'Relationship group default');
    assert.equal(summary.adjustAction.enabled, true);
    assert.equal(summary.adjustAction.screen, 'contactDetail');
    assert.equal(summary.adjustAction.contactId, 'c-mira');
    assert.match(summary.influenceSummary, /AI draft/i);
    assert.match(summary.influenceSummary, /Close friends group defaults/i);
    assert.match(summary.controlSummary, /without retraining the global style profile/i);
    assert.ok(summary.detailItems.includes('Preference source: Close friends group defaults'));
  });

  it('explains contact override tone impact for template fallback drafts', () => {
    const state = relateReducer(createTestState(), {
      type: 'createTemplateDraft',
      contactId: 'c-rajesh',
      reason: 'Congratulations',
      body: 'Congratulations Rajesh. This is a meaningful milestone and I am happy to see your work recognized.',
      templateId: 'congrats-respectful'
    });
    const draft = state.messages[0];
    const summary = buildTonePreferenceSummary(state, 'c-rajesh', draft.id);

    assert.equal(draft.quality, 'Template fallback');
    assert.equal(summary.status, 'ready');
    assert.equal(summary.toneSource, 'contact');
    assert.deepEqual(summary.tones, ['Respectful', 'Formal', 'Concise']);
    assert.match(summary.influenceSummary, /template draft/i);
    assert.match(summary.influenceSummary, /local writing target/i);
    assert.match(summary.warnings.join(' '), /review-first/i);
    assert.ok(summary.detailItems.includes('Draft quality: Template fallback'));
  });

  it('keeps missing drafts recoverable while leaving contact tone editable', () => {
    const state = createTestState();
    const summary = buildTonePreferenceSummary(state, 'c-asha', 'missing-message');

    assert.equal(summary.status, 'missing-draft');
    assert.equal(summary.adjustAction.enabled, true);
    assert.equal(summary.adjustAction.contactId, 'c-asha');
    assert.match(summary.influenceSummary, /Future drafts/i);
    assert.match(summary.warnings.join(' '), /draft is unavailable/i);
  });

  it('blocks tone adjustment when the contact is unavailable', () => {
    const state = createTestState();
    const summary = buildTonePreferenceSummary(state, 'missing-contact', 'msg-asha-bday');

    assert.equal(summary.status, 'missing-contact');
    assert.equal(summary.adjustAction.enabled, false);
    assert.match(summary.influenceSummary, /contact is no longer available/i);
    assert.match(summary.warnings.join(' '), /contact profile is unavailable/i);
  });
});
