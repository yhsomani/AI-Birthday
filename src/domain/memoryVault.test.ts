import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
import type { MemoryNote } from './types';
import { MEMORY_NOTE_MAX_LENGTH, buildMemoryVaultReport, validateMemoryNoteInput } from './memoryVault';

describe('Memory Vault contract', () => {
  it('validates contact existence, blank notes, length, and normalized note text', () => {
    const state = createTestState();
    const missing = validateMemoryNoteInput(state, 'missing-contact', 'Likes books.');
    const blank = validateMemoryNoteInput(state, 'c-asha', '   ');
    const long = validateMemoryNoteInput(state, 'c-asha', 'x'.repeat(MEMORY_NOTE_MAX_LENGTH + 1));
    const valid = validateMemoryNoteInput(state, 'c-asha', '  Loves   handwritten\nnotes.  ');

    assert.equal(missing.ok, false);
    assert.equal(missing.ok ? '' : missing.code, 'missing-contact');
    assert.equal(blank.ok, false);
    assert.equal(blank.ok ? '' : blank.code, 'blank-note');
    assert.equal(long.ok, false);
    assert.equal(long.ok ? '' : long.code, 'too-long');
    assert.equal(valid.ok, true);
    assert.equal(valid.ok ? valid.value.body : '', 'Loves handwritten notes.');
  });

  it('shows pinned notes first, supports search, and explains private AI exclusion', () => {
    const base = createTestState();
    const state = {
      ...base,
      memories: [
        {
          id: 'memory-recent',
          contactId: 'c-asha',
          category: 'General',
          body: 'Recent unpinned context',
          pinned: false,
          createdAt: '2026-07-09T10:00:00.000Z'
        },
        {
          id: 'memory-private',
          contactId: 'c-asha',
          category: 'Private',
          body: 'Sensitive family detail',
          pinned: false,
          createdAt: '2026-07-09T11:00:00.000Z'
        },
        {
          id: 'memory-pinned',
          contactId: 'c-asha',
          category: 'Preference',
          body: 'Pinned dessert preference',
          pinned: true,
          createdAt: '2026-01-01T10:00:00.000Z'
        }
      ] satisfies MemoryNote[]
    };
    const report = buildMemoryVaultReport(state, 'c-asha');
    const search = buildMemoryVaultReport(state, 'c-asha', 'dessert');
    const empty = buildMemoryVaultReport(state, 'c-asha', 'no match');

    assert.equal(report.notes[0].note.id, 'memory-pinned');
    assert.equal(report.totalCount, 3);
    assert.equal(report.aiEligibleCount, 2);
    assert.equal(report.privateCount, 1);
    assert.match(report.notes.find(item => item.note.id === 'memory-private')?.aiUseLabel ?? '', /excluded from AI/i);
    assert.deepEqual(
      search.notes.map(item => item.note.id),
      ['memory-pinned']
    );
    assert.match(empty.emptyMessage ?? '', /No memories match/);
  });

  it('lets users add, edit, pin, and delete notes while keeping private notes out of AI drafts', () => {
    const state = createTestState();
    const added = relateReducer(state, {
      type: 'addMemory',
      contactId: 'c-mira',
      category: 'Private',
      body: '  Secret family context.  '
    });
    const memoryId = added.memories[0].id;
    const edited = relateReducer(added, {
      type: 'editMemory',
      memoryId,
      category: 'Private',
      body: 'Updated secret family context.'
    });
    const pinned = relateReducer(edited, {
      type: 'toggleMemoryPin',
      memoryId
    });
    const generated = relateReducer(pinned, {
      type: 'generateMessage',
      contactId: 'c-mira',
      eventId: 'e-mira-checkin',
      reason: 'Check-in'
    });
    const deleted = relateReducer(pinned, {
      type: 'deleteMemory',
      memoryId
    });

    assert.equal(added.memories[0].body, 'Secret family context.');
    assert.equal(edited.memories[0].body, 'Updated secret family context.');
    assert.equal(pinned.memories[0].pinned, true);
    assert.doesNotMatch(generated.messages[0].body, /secret family/i);
    assert.equal(
      deleted.memories.some(memory => memory.id === memoryId),
      false
    );
    assert.equal(deleted.activity[0].title, 'Memory deleted');
  });
});
