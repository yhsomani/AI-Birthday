import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
import { buildContactEnrichmentPlan, buildContactEnrichmentPlans, validateEnrichmentAnswer } from './contactEnrichment';

const createSparseContactState = () => {
  const state = createTestState();
  const base = state.contacts.find(contact => contact.id === 'c-mira')!;
  return {
    ...state,
    contacts: [
      {
        ...base,
        id: 'c-sparse',
        name: 'Dev Patil',
        relationship: 'Friend',
        group: 'Friends' as const,
        phone: undefined,
        email: undefined,
        language: 'English' as const,
        tone: ['Warm' as const],
        healthScore: 35,
        notesSummary: '',
        annualGiftBudget: 0
      }
    ],
    memories: [],
    gifts: [],
    events: [],
    messages: []
  };
};

describe('guided contact enrichment contract', () => {
  it('keeps indexed batch plans equivalent to individual contact plans', () => {
    const state = createTestState();
    const plans = buildContactEnrichmentPlans(state);
    for (const contact of state.contacts) {
      assert.deepEqual(plans.get(contact.id), buildContactEnrichmentPlan(state, contact.id));
    }
  });

  it('surfaces the highest-priority missing personalization prompts', () => {
    const plan = buildContactEnrichmentPlan(createSparseContactState(), 'c-sparse');

    assert.ok(plan);
    assert.equal(plan.label, 'Needs details');
    assert.deepEqual(
      plan.prompts.map(prompt => prompt.id),
      ['relationship-context', 'message-mention', 'message-avoid', 'language-style']
    );
    assert.deepEqual(plan.missingSignals, [
      'relationship context',
      'mention preferences',
      'avoid guidance',
      'language guidance'
    ]);
    assert.match(plan.summary, /relationship context/i);
    assert.equal(plan.prompts[0].improvesSignal, 'relationship context');
  });

  it('validates enrichment answers before saving', () => {
    assert.equal(validateEnrichmentAnswer('  ').ok, false);
    assert.equal(validateEnrichmentAnswer('x'.repeat(501)).ok, false);

    const result = validateEnrichmentAnswer('  Loves quiet coffee chats.  ');
    assert.equal(result.ok, true);
    if (result.ok) {
      assert.equal(result.value, 'Loves quiet coffee chats.');
    }
  });

  it('saves a guided answer as memory context and improves profile quality without logging the answer', () => {
    const state = createTestState();
    const before = state.contacts.find(contact => contact.id === 'c-mira')?.healthScore ?? 0;
    const next = relateReducer(state, {
      type: 'answerEnrichmentPrompt',
      contactId: 'c-mira',
      promptId: 'message-avoid',
      body: 'Jokes about moving cities when she is stressed'
    });
    const after = next.contacts.find(contact => contact.id === 'c-mira')?.healthScore ?? 0;
    const planAfter = buildContactEnrichmentPlan(next, 'c-mira');

    assert.equal(next.memories[0].category, 'Preference');
    assert.match(next.memories[0].body, /^Avoid in messages:/);
    assert.ok(after > before);
    assert.ok(planAfter);
    assert.equal(planAfter?.missingSignals.includes('avoid guidance'), false);
    assert.ok(planAfter?.completedSignals.includes('avoid guidance'));
    assert.match(next.activity[0].detail, /Preference context saved/);
    assert.doesNotMatch(next.activity[0].detail, /moving cities|stressed/);
  });
});
