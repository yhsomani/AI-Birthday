import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import type { PermissionRequestResult } from '../native/permissionRequest';
import { PermissionRequestCoordinator } from './permissionRequestCoordinator';

const promptedAt = '2026-07-10T08:31:00.000Z';

const grantedResult = (capability: PermissionRequestResult['capability']): PermissionRequestResult => ({
  capability,
  outcome: 'granted',
  systemAuthorization: 'granted',
  promptedAt,
  canAskAgain: false,
  platformStatus: 'granted'
});

describe('explicit permission request coordinator', () => {
  it('persists allow intent before the native prompt and persists its outcome afterward', async () => {
    const state = createTestState();
    const order: string[] = [];
    const coordinator = new PermissionRequestCoordinator({
      now: () => new Date('2026-07-10T08:30:00.000Z'),
      requestPermission: async capability => {
        order.push('native-prompt');
        return grantedResult(capability);
      },
      onPermissionStateChanged: (records, decisions, phase) => {
        order.push(`persist-${phase}`);
        if (phase === 'intent') {
          assert.equal(records.Contacts.userIntent, 'allow');
          assert.equal(records.Contacts.userIntentUpdatedAt, '2026-07-10T08:30:00.000Z');
        } else {
          assert.equal(records.Contacts.lastPromptOutcome, 'granted');
          assert.equal(records.Contacts.lastPromptAt, promptedAt);
          assert.equal(records.Contacts.canAskAgain, false);
          assert.equal(decisions.Contacts, 'Granted');
        }
      }
    });

    const result = await coordinator.request(state, { capability: 'Contacts', userIntent: 'allow' });

    assert.deepEqual(order, ['persist-intent', 'native-prompt', 'persist-prompt-outcome']);
    assert.equal(result.status, 'granted');
    assert.equal(result.records.Contacts.systemCheckedAt, promptedAt);
    assert.equal(result.request?.capability, 'Contacts');
  });

  it('persists decline intent without opening an operating-system prompt', async () => {
    const state = createTestState();
    let prompts = 0;
    const phases: string[] = [];
    const coordinator = new PermissionRequestCoordinator({
      requestPermission: async capability => {
        prompts += 1;
        return grantedResult(capability);
      },
      onPermissionStateChanged: (_records, _decisions, phase) => {
        phases.push(phase);
      }
    });

    const result = await coordinator.request(state, {
      capability: 'Notifications',
      userIntent: 'decline'
    });

    assert.equal(result.status, 'declined');
    assert.equal(result.records.Notifications.userIntent, 'decline');
    assert.equal(prompts, 0);
    assert.deepEqual(phases, ['intent']);
  });

  it('retains persisted intent but does not invent a prompt outcome when the request fails', async () => {
    const state = createTestState();
    state.privacy.permissionDecisions.Calendar = 'Not requested';
    state.privacy.permissionRecords = undefined;
    const phases: string[] = [];
    const errors: string[] = [];
    const coordinator = new PermissionRequestCoordinator({
      requestPermission: async () => {
        throw new Error('native bridge unavailable');
      },
      onPermissionStateChanged: (_records, _decisions, phase) => {
        phases.push(phase);
      },
      onError: capability => {
        errors.push(capability);
      }
    });

    const result = await coordinator.request(state, { capability: 'Calendar', userIntent: 'allow' });

    assert.equal(result.status, 'request-failed');
    assert.equal(result.records.Calendar.userIntent, 'allow');
    assert.equal(result.records.Calendar.lastPromptOutcome, undefined);
    assert.deepEqual(phases, ['intent']);
    assert.deepEqual(errors, ['Calendar']);
  });

  it('serializes concurrent explicit prompts', async () => {
    const state = createTestState();
    const order: string[] = [];
    let releaseFirst: (() => void) | undefined;
    let markFirstStarted: (() => void) | undefined;
    const firstGate = new Promise<void>(resolve => {
      releaseFirst = resolve;
    });
    const firstStarted = new Promise<void>(resolve => {
      markFirstStarted = resolve;
    });
    const coordinator = new PermissionRequestCoordinator({
      requestPermission: async capability => {
        order.push(`start-${capability}`);
        if (capability === 'Contacts') {
          markFirstStarted?.();
          await firstGate;
        }
        order.push(`finish-${capability}`);
        return grantedResult(capability);
      }
    });

    const first = coordinator.request(state, { capability: 'Contacts', userIntent: 'allow' });
    const second = coordinator.request(state, { capability: 'Calendar', userIntent: 'allow' });
    await firstStarted;
    assert.deepEqual(order, ['start-Contacts']);
    releaseFirst?.();
    await Promise.all([first, second]);

    assert.deepEqual(order, ['start-Contacts', 'finish-Contacts', 'start-Calendar', 'finish-Calendar']);
  });
});
