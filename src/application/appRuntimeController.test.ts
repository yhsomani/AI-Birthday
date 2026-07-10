import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createProductionInitialState } from '../data/productionState';
import type { AppState } from '../domain/types';
import { PersistenceCoordinator } from '../state/persistenceCoordinator';
import type { PersistenceLoadResult } from '../state/persistence';
import { relateReducer } from '../state/relateReducer';
import { OperationalIssueQueue } from './operationalIssues';
import { AppRuntimeController } from './appRuntimeController';

const fixture = (
  options: { load?: () => Promise<PersistenceLoadResult>; save?: (state: AppState) => Promise<void> } = {}
) => {
  let now = 0;
  const saved: AppState[] = [];
  const lifecycleCalls: string[] = [];
  const issues = new OperationalIssueQueue({
    now: () => `2026-07-10T10:00:${String(now++).padStart(2, '0')}.000Z`,
    createId: () => `issue-${now}`
  });
  const persistence = new PersistenceCoordinator({
    save: async state => {
      await options.save?.(state);
      saved.push(state);
    },
    inspect: async () => undefined,
    nowIso: () => '2026-07-10T10:00:00.000Z'
  });
  const controller = new AppRuntimeController({
    loadState: options.load ?? (async () => ({ status: 'missing' as const })),
    persistence,
    reduce: relateReducer,
    permissionReminders: {
      afterHydration: async () => {
        lifecycleCalls.push('hydration');
        return { status: 'reconciled' } as never;
      },
      onForeground: async () => {
        lifecycleCalls.push('foreground');
        return { status: 'reconciled' } as never;
      },
      afterCommittedChange: async (_state, change) => {
        lifecycleCalls.push(`commit:${change}`);
        return { status: 'reconciled' } as never;
      }
    },
    syncWidget: async () => undefined,
    issues
  });
  return { controller, saved, lifecycleCalls, issues };
};

describe('application runtime controller', () => {
  it('hydrates to a true empty production state without persisting or flashing fixtures', async () => {
    const test = fixture();
    const phases: string[] = [];
    test.controller.subscribe(() => phases.push(test.controller.getSnapshot().phase));
    await test.controller.start();
    const snapshot = test.controller.getSnapshot();
    assert.deepEqual(phases, ['hydrating', 'ready']);
    assert.equal(snapshot.state.contacts.length, 0);
    assert.equal(snapshot.phase, 'ready');
    assert.equal(test.saved.length, 0);
    assert.deepEqual(test.lifecycleCalls, ['hydration']);
  });

  it('coalesces durable changes and reconciles only after a verified commit', async () => {
    const test = fixture();
    await test.controller.start();
    test.controller.dispatch({ type: 'setQuietHours', start: '21:00', end: '07:00' });
    await test.controller.flush();
    assert.equal(test.saved.length, 1);
    assert.ok(test.lifecycleCalls.includes('commit:settings'));
    assert.equal(test.controller.getSnapshot().state.persistence.status, 'Ready');
  });

  it('fails closed when protected storage cannot be opened', async () => {
    const test = fixture({ load: async () => { throw new Error('secret path'); } });
    await test.controller.start();
    assert.equal(test.controller.getSnapshot().phase, 'failed');
    assert.equal(test.controller.getSnapshot().state.contacts.length, 0);
    assert.equal(test.issues.active()[0]?.code, 'storage-unavailable');
    assert.doesNotMatch(test.issues.active()[0]?.summary ?? '', /secret path/);
  });

  it('flushes on background and refreshes non-prompting lifecycle work on foreground', async () => {
    const test = fixture();
    await test.controller.start();
    await test.controller.setVisibility('background');
    await test.controller.setVisibility('foreground');
    assert.ok(test.lifecycleCalls.includes('foreground'));
  });

  it('publishes a transactionally verified replacement without scheduling another write', async () => {
    const test = fixture();
    await test.controller.start();
    const restored = createProductionInitialState();
    restored.onboarding.completed = true;
    test.controller.installVerifiedState(restored);
    await test.controller.flush();
    assert.equal(test.controller.getSnapshot().state.onboarding.completed, true);
    assert.equal(test.saved.length, 0);
  });
});
