import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { buildManualComposerState, manualComposerReasons } from './manualComposer';
import type { AppState, MemoryNote } from './types';

const withPrivateAshaMemory = (state: AppState): AppState => {
  const privateMemory: MemoryNote = {
    id: 'm-asha-private',
    contactId: 'c-asha',
    category: 'Private',
    body: 'Secret family detail that must not appear.',
    pinned: false,
    createdAt: '2026-01-01T00:00:00.000Z'
  };

  return {
    ...state,
    memories: [...state.memories, privateMemory]
  };
};

describe('manual composer contract', () => {
  it('keeps the supported composer reasons explicit and ordered', () => {
    assert.deepEqual(manualComposerReasons, [
      'Birthday',
      'Check-in',
      'Thanks',
      'Congratulations',
      'Apology',
      'Follow-up',
      'Custom'
    ]);
  });

  it('builds an editable template using only non-private contact context', () => {
    const state = withPrivateAshaMemory(createTestState());
    const model = buildManualComposerState(state, 'c-asha', 'Custom');

    assert.equal(model.ok, true);
    if (model.ok) {
      assert.equal(model.selectedTemplateId, 'custom-warm');
      assert.equal(model.context.contextSource, 'memory');
      assert.equal(model.context.includedMemoryCount, 1);
      assert.equal(model.context.excludedPrivateMemoryCount, 1);
      assert.match(model.context.detail, /1 non-private memory/i);
      assert.match(model.context.detail, /1 private note excluded/i);
      assert.match(model.renderedTemplateBody, /Favorite dessert is mango lassi/i);
      assert.doesNotMatch(model.renderedTemplateBody, /Secret family detail/i);
      assert.equal(model.templateAction.enabled, true);
      assert.equal(model.templateAction.status, 'Ready');
    }
  });

  it('blocks template draft creation until the edited body meets the shared message length rule', () => {
    const state = createTestState();
    const model = buildManualComposerState(state, 'c-mira', 'Thanks', 'Too short');

    assert.equal(model.ok, true);
    if (model.ok) {
      assert.equal(model.characterCount, 9);
      assert.equal(model.templateAction.enabled, false);
      assert.equal(model.templateAction.status, 'Blocked');
      assert.match(model.templateAction.detail, /at least 12 characters/i);
    }
  });

  it('explains whether the AI action will use the provider or a local review-first fallback', () => {
    const ready = buildManualComposerState(
      {
        ...createTestState(),
        aiProvider: { status: 'Ready' }
      },
      'c-mira',
      'Check-in'
    );
    const disabled = buildManualComposerState(
      {
        ...createTestState(),
        settings: {
          ...createTestState().settings,
          aiEnabled: false
        }
      },
      'c-mira',
      'Check-in'
    );
    const notConfigured = buildManualComposerState(createTestState(), 'c-mira', 'Check-in');

    assert.equal(ready.ok, true);
    assert.equal(disabled.ok, true);
    assert.equal(notConfigured.ok, true);
    if (ready.ok && disabled.ok && notConfigured.ok) {
      assert.equal(ready.aiAction.status, 'Ready');
      assert.equal(ready.aiAction.label, 'Ask AI');
      assert.match(ready.aiAction.detail, /private notes excluded/i);
      assert.equal(disabled.aiAction.status, 'Warning');
      assert.equal(disabled.aiAction.label, 'Create fallback');
      assert.match(disabled.aiAction.detail, /AI drafting is disabled/i);
      assert.equal(disabled.templateAction.enabled, true);
      assert.equal(notConfigured.aiAction.status, 'Warning');
      assert.match(notConfigured.aiAction.detail, /not configured/i);
    }
  });

  it('returns blocked actions when the selected contact is unavailable', () => {
    const model = buildManualComposerState(createTestState(), 'missing-contact', 'Check-in');

    assert.equal(model.ok, false);
    if (!model.ok) {
      assert.equal(model.templateAction.enabled, false);
      assert.equal(model.aiAction.enabled, false);
      assert.match(model.error, /could not be found/i);
    }
  });
});
