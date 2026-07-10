import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
import { buildAccountExitPlan } from './accountAccess';

describe('account access contract', () => {
  it('builds a non-destructive disconnect checklist that recommends backup before account changes', () => {
    const base = createTestState();
    const state = {
      ...base,
      backups: [],
      settings: {
        ...base.settings,
        accountMode: 'Google sync' as const
      }
    };

    const plan = buildAccountExitPlan(state, 'disconnect-account');
    const backup = plan.checklist.find(item => item.id === 'backup');

    assert.equal(plan.available, true);
    assert.equal(plan.requiresConfirmation, true);
    assert.equal(plan.destructive, false);
    assert.equal(plan.backupRecommended, true);
    assert.equal(plan.backupReady, false);
    assert.match(plan.summary, /keeps contacts/i);
    assert.match(plan.summary, /No provider sync connection exists/i);
    assert.match(backup?.detail ?? '', /encrypted backup/i);
    assert.equal(backup?.satisfied, false);
  });

  it('disconnects provider sync without deleting local relationship data', () => {
    const base = createTestState();
    const state = relateReducer(base, { type: 'setAccountMode', mode: 'Google sync' });
    const disconnected = relateReducer(state, { type: 'disconnectAccount' });

    assert.equal(disconnected.settings.accountMode, 'Local');
    assert.equal(disconnected.contacts.length, state.contacts.length);
    assert.equal(disconnected.events.length, state.events.length);
    assert.equal(disconnected.messages.length, state.messages.length);
    assert.equal(disconnected.memories.length, state.memories.length);
    assert.equal(disconnected.gifts.length, state.gifts.length);
    assert.equal(disconnected.backups.length, state.backups.length);
    assert.match(disconnected.activity[0].detail, /local data was retained/i);
  });

  it('builds a destructive clear-local-data plan with explicit consequence counts', () => {
    const base = createTestState();
    const state = {
      ...base,
      backups: []
    };
    const plan = buildAccountExitPlan(state, 'clear-local-data');

    assert.equal(plan.available, true);
    assert.equal(plan.requiresConfirmation, true);
    assert.equal(plan.destructive, true);
    assert.equal(plan.backupRecommended, true);
    assert.equal(plan.backupReady, false);
    assert.ok(plan.relationshipRecordCount > 0);
    assert.match(plan.confirmationBody, /relationship record/i);
    assert.match(plan.confirmationBody, /Create an encrypted backup first/i);
  });

  it('marks disconnect unavailable when the user is already local-only', () => {
    const plan = buildAccountExitPlan(createTestState(), 'disconnect-account');

    assert.equal(plan.available, false);
    assert.equal(plan.requiresConfirmation, false);
    assert.match(plan.unavailableReason ?? '', /No provider sync connection/i);
  });
});
