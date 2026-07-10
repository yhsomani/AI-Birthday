import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
import { buildGiftBudgetSummary, buildGiftSuggestions, validateGiftBudgetInput, validateGiftInput } from './giftAdvisor';

describe('gift advisor contract', () => {
  it('summarizes annual budget and remaining spend for a contact', () => {
    const state = createTestState();
    const contact = state.contacts.find(item => item.id === 'c-asha')!;
    const summary = buildGiftBudgetSummary(contact, state.gifts, 2025);

    assert.equal(summary.annualBudget, 8000);
    assert.equal(summary.spentThisYear, 2200);
    assert.equal(summary.remaining, 5800);
    assert.equal(summary.recordedGiftCount, 1);
    assert.equal(summary.overBudget, false);

    const budgetValidation = validateGiftBudgetInput({ annualGiftBudget: '9000.4' });
    assert.equal(budgetValidation.ok, true);
    if (budgetValidation.ok) {
      assert.equal(budgetValidation.value, 9000);
    }

    const saved = relateReducer(state, { type: 'updateGiftBudget', contactId: 'c-asha', annualGiftBudget: '9000.4' });
    const savedContact = saved.contacts.find(item => item.id === 'c-asha')!;
    const savedSummary = buildGiftBudgetSummary(savedContact, saved.gifts, 2025);
    const invalid = relateReducer(saved, { type: 'updateGiftBudget', contactId: 'c-asha', annualGiftBudget: '-1' });

    assert.equal(savedContact.annualGiftBudget, 9000);
    assert.equal(savedSummary.remaining, 6800);
    assert.equal(saved.activity[0].title, 'Gift budget saved');
    assert.equal(invalid.contacts.find(item => item.id === 'c-asha')?.annualGiftBudget, 9000);
    assert.equal(invalid.activity[0].title, 'Gift budget not saved');
  });

  it('validates required gift fields and cost range', () => {
    assert.equal(
      validateGiftInput({ name: '', category: 'Books', occasion: 'Birthday', cost: 100 }).ok,
      false
    );
    assert.equal(
      validateGiftInput({ name: 'Book', category: 'Books', occasion: '', cost: 100 }).ok,
      false
    );
    assert.equal(
      validateGiftInput({ name: 'Book', category: 'Books', occasion: 'Birthday', cost: -1 }).ok,
      false
    );

    const valid = validateGiftInput({
      name: '  Book voucher  ',
      category: 'Books',
      occasion: ' Birthday ',
      cost: 1200,
      notes: '  likes reading  '
    });
    assert.equal(valid.ok, true);
    if (valid.ok) {
      assert.equal(valid.value.name, 'Book voucher');
      assert.equal(valid.value.notes, 'likes reading');
    }
  });

  it('builds contextual suggestions with duplicate and budget signals', () => {
    const state = createTestState();
    const suggestions = buildGiftSuggestions(state, 'c-asha', 'Birthday');

    assert.ok(suggestions.length > 0);
    assert.ok(suggestions.some(suggestion => suggestion.budgetFit === 'Within budget'));
    assert.ok(suggestions.some(suggestion => suggestion.duplicateWarning));
    assert.ok(suggestions.every(suggestion => ['Medium', 'High'].includes(suggestion.confidence)));
  });

  it('excludes private memories from suggestion rationale', () => {
    const state = createTestState();
    const suggestions = buildGiftSuggestions(state, 'c-rajesh', 'Work anniversary');
    const serialized = JSON.stringify(suggestions);

    assert.ok(suggestions.some(suggestion => suggestion.category === 'Other'));
    assert.doesNotMatch(serialized, /Private note excluded/i);
  });

  it('handles missing contacts and unset budgets without failing', () => {
    const state = createTestState();
    const missing = buildGiftSuggestions(state, 'missing-contact');
    const noBudget = buildGiftSuggestions(
      {
        ...state,
        contacts: [{ ...state.contacts[0], annualGiftBudget: 0 }]
      },
      'c-asha'
    );

    assert.deepEqual(missing, []);
    assert.ok(noBudget.every(suggestion => suggestion.budgetFit === 'No budget set'));
  });

  it('deletes gift history through the reducer and updates budget summaries', () => {
    const state = createTestState();
    const contact = state.contacts.find(item => item.id === 'c-asha')!;
    const before = buildGiftBudgetSummary(contact, state.gifts, 2025);
    const deleted = relateReducer(state, { type: 'deleteGift', giftId: 'g-asha-1' });
    const after = buildGiftBudgetSummary(contact, deleted.gifts, 2025);

    assert.equal(before.recordedGiftCount, 1);
    assert.equal(before.spentThisYear, 2200);
    assert.equal(deleted.gifts.some(gift => gift.id === 'g-asha-1'), false);
    assert.equal(after.recordedGiftCount, 0);
    assert.equal(after.spentThisYear, 0);
    assert.equal(after.remaining, contact.annualGiftBudget);
    assert.equal(deleted.activity[0].title, 'Gift deleted');
  });

  it('reports missing gift delete attempts without changing gift history', () => {
    const state = createTestState();
    const result = relateReducer(state, { type: 'deleteGift', giftId: 'missing-gift' });

    assert.equal(result.gifts.length, state.gifts.length);
    assert.equal(result.activity[0].title, 'Gift not deleted');
    assert.equal(result.activity[0].severity, 'Warning');
  });
});
