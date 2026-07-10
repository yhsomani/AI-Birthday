import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { WISH_CUSTOM_FEEDBACK_MAX_LENGTH, buildWishFeedbackPlan } from './wishFeedback';

describe('Wish Preview regeneration feedback contract', () => {
  it('builds bounded feedback instructions for regeneration requests', () => {
    const message = createTestState().messages.find(item => item.id === 'msg-asha-bday')!;
    const plan = buildWishFeedbackPlan(message, {
      selectedOptionIds: ['more-personal', 'shorter'],
      customText: 'Mention the mango lassi memory more naturally.'
    });

    assert.equal(plan.action.enabled, true);
    assert.equal(plan.selectedOptions.length, 2);
    assert.match(plan.improvementSummary, /more personal/i);
    assert.match(plan.improvementSummary, /custom guidance/i);
    assert.equal(plan.requestFeedback?.instructions.length, 2);
    assert.match(plan.requestFeedback?.customInstruction ?? '', /mango lassi/i);
    assert.match(plan.requestFeedback?.previousDraftExcerpt ?? '', /Happy birthday Asha/i);
  });

  it('keeps regeneration possible without feedback but explains the default behavior', () => {
    const message = createTestState().messages.find(item => item.id === 'msg-mira-checkin')!;
    const plan = buildWishFeedbackPlan(message);

    assert.equal(plan.action.enabled, true);
    assert.equal(plan.requestFeedback, undefined);
    assert.match(plan.improvementSummary, /current contact tone/i);
    assert.ok(plan.options[0].recommendedFor.includes(message.quality));
  });

  it('blocks oversized custom feedback before it can be sent to regeneration', () => {
    const message = createTestState().messages.find(item => item.id === 'msg-asha-bday')!;
    const plan = buildWishFeedbackPlan(message, {
      customText: 'A'.repeat(WISH_CUSTOM_FEEDBACK_MAX_LENGTH + 1)
    });

    assert.equal(plan.action.enabled, false);
    assert.equal(plan.requestFeedback, undefined);
    assert.match(plan.warnings.join(' '), /240 characters or less/i);
    assert.match(plan.action.detail, /Shorten/i);
  });
});
