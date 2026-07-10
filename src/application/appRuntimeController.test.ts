import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createProductionInitialState } from '../data/productionState';
import type { AppState } from '../domain/types';
import type { LivePermissionSnapshot } from '../native/permissionStatus';
import { PersistenceCoordinator } from '../state/persistenceCoordinator';
import type { PersistenceLoadResult } from '../state/persistence';
import { relateReducer } from '../state/relateReducer';
import { OperationalIssueQueue } from './operationalIssues';
import { AppRuntimeController } from './appRuntimeController';
import { PermissionReminderCoordinator, permissionDecisionsFromRecords } from './permissionReminderCoordinator';
import { createTestState } from '../test/testState';

const fixture = (
  options: {
    load?: () => Promise<PersistenceLoadResult>;
    save?: (state: AppState, previousState?: AppState) => Promise<void>;
    resetFailedStorage?: () => Promise<void>;
    getCurrentTimeZone?: () => string;
  } = {}
) => {
  let now = 0;
  const saved: AppState[] = [];
  const previousStates: (AppState | undefined)[] = [];
  const lifecycleCalls: string[] = [];
  const issues = new OperationalIssueQueue({
    now: () => `2026-07-10T10:00:${String(now++).padStart(2, '0')}.000Z`,
    createId: () => `issue-${now}`
  });
  const persistence = new PersistenceCoordinator({
    save: async (state, previousState) => {
      await options.save?.(state, previousState);
      saved.push(state);
      previousStates.push(previousState);
    },
    inspect: async () => undefined,
    nowIso: () => '2026-07-10T10:00:00.000Z'
  });
  const controller = new AppRuntimeController({
    loadState: options.load ?? (async () => ({ status: 'missing' as const })),
    resetFailedStorage: options.resetFailedStorage,
    getCurrentTimeZone: options.getCurrentTimeZone,
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
  return { controller, saved, previousStates, lifecycleCalls, issues };
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
    assert.equal(test.previousStates[0]?.settings.quietHours.start, '22:00');
    assert.ok(test.lifecycleCalls.includes('commit:settings'));
    assert.equal(test.controller.getSnapshot().state.persistence.status, 'Ready');
  });

  it('preserves encrypted repository health supplied by hydration', async () => {
    const state = createProductionInitialState();
    state.persistence = {
      status: 'Ready',
      storageHealth: {
        status: 'Ready',
        storageFormat: 'Encrypted entity repository',
        payloadBytes: 2_048,
        entryCount: 3,
        chunkCount: 0,
        largestEntryBytes: 1_024,
        lastVerifiedAt: '2026-07-10T10:00:00.000Z'
      }
    };
    const test = fixture({
      load: async () => ({ status: 'loaded', state, migrated: false, version: 6 })
    });

    await test.controller.start();

    assert.equal(
      test.controller.getSnapshot().state.persistence.storageHealth?.storageFormat,
      'Encrypted entity repository'
    );
  });

  it('normalizes restored capabilities through the release availability policy before publishing state', async () => {
    const state = createTestState();
    state.settings.accountMode = 'Google sync';
    state.settings.automationMode = 'Fully auto';
    state.settings.groupDefaults.Family.automationMode = 'Fully auto';
    state.contacts[0].preferenceOverrides = { automationMode: 'Fully auto' };
    const test = fixture({
      load: async () => ({ status: 'loaded', state, migrated: false, version: 6 })
    });

    await test.controller.start();

    const loaded = test.controller.getSnapshot().state;
    assert.equal(loaded.settings.accountMode, 'Local');
    assert.equal(loaded.settings.automationMode, 'Always ask');
    assert.equal(loaded.settings.groupDefaults.Family.automationMode, 'Always ask');
    assert.equal(loaded.contacts[0].preferenceOverrides?.automationMode, 'Always ask');
    assert.equal(test.saved.length, 1);
    assert.equal(test.previousStates[0]?.settings.automationMode, 'Fully auto');
    assert.equal(test.saved[0].settings.automationMode, 'Always ask');
  });

  it('reconciles source aggregates after commit without feeding derived reminder plans back into planning', async () => {
    const test = fixture();
    await test.controller.start();
    const state = test.controller.getSnapshot().state;
    test.controller.dispatch({
      type: 'reminderPlansReconciled',
      plans: [
        {
          id: 'runtime-plan',
          eventId: 'runtime-event',
          contactId: 'runtime-contact',
          title: 'RelateAI reminder',
          body: 'Open RelateAI.',
          triggerAt: '2026-07-20T09:00:00.000Z'
        }
      ]
    });
    await test.controller.flush();
    assert.equal(test.lifecycleCalls.includes('commit:reminderPlans'), false);
    assert.equal(state.reminderPlans.length, 0);
    test.controller.dispatch({
      type: 'importContacts',
      records: [{ sourceId: 'runtime-contact', name: 'Runtime contact' }]
    });
    await test.controller.flush();
    assert.ok(test.lifecycleCalls.includes('commit:contacts'));
  });

  it('reconciles approval, setup, and backup notification sources only after verified commits', async () => {
    const state = createTestState();
    state.privacy.permissionDecisions.Notifications = 'Granted';
    state.onboarding.currentStepId = 'finish';
    state.onboarding.completedStepIds = ['account', 'contacts', 'notifications'];
    const test = fixture({
      load: async () => ({ status: 'loaded', state, migrated: false, version: 6 })
    });
    await test.controller.start();

    test.controller.dispatch({
      type: 'editMessage',
      messageId: state.messages[0].id,
      body: 'Updated review-safe body with enough text.'
    });
    await test.controller.flush();
    assert.ok(test.lifecycleCalls.includes('commit:messages'));

    test.controller.dispatch({ type: 'completeOnboarding' });
    await test.controller.flush();
    assert.ok(test.lifecycleCalls.includes('commit:setup'));

    test.controller.dispatch({ type: 'createBackup' });
    await test.controller.flush();
    assert.ok(test.lifecycleCalls.includes('commit:backups'));
  });

  it('does not resolve a durable command before protected storage is verified', async () => {
    let releaseSave: (() => void) | undefined;
    const saveGate = new Promise<void>(resolve => {
      releaseSave = resolve;
    });
    const test = fixture({ save: async () => saveGate });
    await test.controller.start();
    let settled = false;
    const command = test.controller
      .dispatchAndCommit({ type: 'setQuietHours', start: '20:00', end: '06:00' })
      .then(() => {
        settled = true;
      });
    await Promise.resolve();
    assert.equal(settled, false);
    releaseSave?.();
    await command;
    assert.equal(settled, true);
    assert.equal(test.saved.length, 1);
  });

  it('rejects a durable command when protected storage cannot verify the latest write', async () => {
    const test = fixture({
      save: async () => {
        throw new Error('storage unavailable');
      }
    });
    await test.controller.start();
    await assert.rejects(
      () => test.controller.dispatchAndCommit({ type: 'setQuietHours', start: '20:00', end: '06:00' }),
      /protected-storage/
    );
    assert.equal(test.issues.active()[0]?.code, 'persistence-failed');
  });

  it('rolls a rejected mutation back and never folds it into the next successful commit', async () => {
    let fail = true;
    const test = fixture({
      save: async state => {
        if (fail && state.settings.quietHours.start === '20:00') {
          fail = false;
          throw new Error('storage unavailable');
        }
      }
    });
    await test.controller.start();

    await assert.rejects(
      () => test.controller.dispatchAndCommit({ type: 'setQuietHours', start: '20:00', end: '06:00' }),
      /protected-storage/
    );
    assert.equal(test.controller.getSnapshot().state.settings.quietHours.start, '22:00');
    assert.equal(test.controller.getSnapshot().state.persistence.status, 'Error');

    await test.controller.dispatchAndCommit({ type: 'setQuietHours', start: '21:00', end: '07:00' });
    assert.equal(test.controller.getSnapshot().state.settings.quietHours.start, '21:00');
    assert.deepEqual(
      test.saved.map(state => state.settings.quietHours.start),
      ['21:00']
    );
    assert.equal(test.previousStates.at(-1)?.settings.quietHours.start, '22:00');
  });

  it('fails closed when protected storage cannot be opened', async () => {
    const test = fixture({
      load: async () => {
        throw new Error('secret path');
      }
    });
    await test.controller.start();
    assert.equal(test.controller.getSnapshot().phase, 'failed');
    assert.equal(test.controller.getSnapshot().state.contacts.length, 0);
    assert.equal(test.issues.active()[0]?.code, 'storage-unavailable');
    assert.doesNotMatch(test.issues.active()[0]?.summary ?? '', /secret path/);
  });

  it('retries a transient fail-closed startup without mutating local data', async () => {
    let attempts = 0;
    const test = fixture({
      load: async () => {
        attempts += 1;
        if (attempts === 1) throw new Error('temporarily unavailable');
        return { status: 'missing' };
      }
    });
    await test.controller.start();
    assert.equal(test.controller.getSnapshot().phase, 'failed');
    const recovered = await test.controller.retryFailedStart();
    assert.equal(recovered.phase, 'ready');
    assert.equal(attempts, 2);
    assert.equal(test.saved.length, 0);
  });

  it('allows explicit destructive recovery only from a failed startup', async () => {
    let corrupt = true;
    let clears = 0;
    const test = fixture({
      load: async () => {
        if (corrupt) throw new Error('corrupt');
        return { status: 'missing' };
      },
      resetFailedStorage: async () => {
        clears += 1;
        corrupt = false;
      }
    });
    await test.controller.start();
    const recovered = await test.controller.clearFailedStorageAndRetry();
    assert.equal(recovered.phase, 'ready');
    assert.equal(clears, 1);
    await assert.rejects(() => test.controller.clearFailedStorageAndRetry(), /only after/i);
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

  it('unschedules and durably saves an identified draft before publishing hydration in a new time zone', async () => {
    const state = relateReducer(createProductionInitialState(), { type: 'hydrate', state: createTestState() });
    state.messages[0] = {
      ...state.messages[0],
      status: 'Scheduled',
      scheduledFor: '2026-08-12T03:30:00.000Z',
      scheduledTimeZone: 'Asia/Calcutta',
      approvedAt: '2026-07-10T09:00:00.000Z',
      approvalExpiresAt: '2026-07-17T09:00:00.000Z'
    };
    const test = fixture({
      load: async () => ({ status: 'loaded', state, migrated: false, version: 6 }),
      getCurrentTimeZone: () => 'America/New_York'
    });

    await test.controller.start();

    const message = test.controller.getSnapshot().state.messages[0];
    assert.equal(message.status, 'Needs review');
    assert.equal(message.scheduledFor, undefined);
    assert.equal(message.scheduledTimeZone, undefined);
    assert.equal(message.approvedAt, undefined);
    assert.match(message.lastError ?? '', /time zone is missing or no longer matches/i);
    assert.equal(test.saved.length, 1);
    assert.equal(test.saved[0].messages[0].status, 'Needs review');
  });

  it('reconciles a background time-zone change before foreground lifecycle work and only once', async () => {
    let timeZone = 'Asia/Calcutta';
    const state = relateReducer(createProductionInitialState(), { type: 'hydrate', state: createTestState() });
    state.messages[0] = {
      ...state.messages[0],
      status: 'Scheduled',
      scheduledFor: '2026-08-12T03:30:00.000Z',
      scheduledTimeZone: timeZone,
      approvedAt: '2026-07-10T09:00:00.000Z',
      approvalExpiresAt: '2026-07-17T09:00:00.000Z'
    };
    const test = fixture({
      load: async () => ({ status: 'loaded', state, migrated: false, version: 6 }),
      getCurrentTimeZone: () => timeZone
    });
    await test.controller.start();
    assert.equal(test.saved.length, 0);

    await test.controller.setVisibility('background');
    timeZone = 'America/New_York';
    await test.controller.setVisibility('foreground');

    const reviewed = test.controller.getSnapshot().state.messages[0];
    assert.equal(reviewed.status, 'Needs review');
    assert.equal(reviewed.scheduledFor, undefined);
    assert.equal(test.saved.length, 1);
    assert.ok(test.lifecycleCalls.includes('commit:messages'));
    assert.ok(test.lifecycleCalls.includes('foreground'));
    const activityCount = test.controller.getSnapshot().state.activity.length;

    await test.controller.setVisibility('foreground');
    assert.equal(test.saved.length, 1);
    assert.equal(test.controller.getSnapshot().state.activity.length, activityCount);
  });

  it('commits derived reminder plans without recursively re-entering reminder planning', async () => {
    const state = createTestState();
    state.events = [
      {
        ...state.events[0],
        date: '2099-07-20T12:00:00.000Z',
        recurrence: {
          frequency: 'Yearly',
          month: 7,
          day: 20,
          originalYear: 2099,
          leapDayPolicy: 'February 28'
        }
      }
    ];
    state.reminderPlans = [];
    state.settings.notificationsEnabled = true;
    const livePermissions: LivePermissionSnapshot = {
      schemaVersion: 1,
      checkedAt: '2099-07-10T08:30:00.000Z',
      contacts: { kind: 'permission', state: 'granted', granted: true },
      calendar: { kind: 'permission', state: 'granted', granted: true },
      notifications: { kind: 'permission', state: 'granted', granted: true },
      biometric: {
        kind: 'capability',
        state: 'granted',
        ready: true,
        reason: 'ready',
        modalities: ['fingerprint'],
        rawAuthenticationTypes: [1],
        queryComplete: true
      }
    };
    const issues = new OperationalIssueQueue({
      now: () => '2099-07-10T08:30:00.000Z',
      createId: () => 'reminder-deadlock-test'
    });
    const persistence = new PersistenceCoordinator({
      save: async () => undefined,
      inspect: async () => undefined,
      nowIso: () => '2099-07-10T08:30:00.000Z'
    });
    const controllerReference = {} as { current: AppRuntimeController };
    const permissionReminders = new PermissionReminderCoordinator({
      now: () => new Date('2099-07-10T08:30:00.000Z'),
      readPermissionSnapshot: async () => livePermissions,
      reconcileReminderNotifications: async plans => ({
        scheduled: plans.length,
        skipped: 0,
        cancelled: 0,
        unchanged: 0
      }),
      onPermissionRecordsChanged: async records => {
        const current = controllerReference.current.getSnapshot().state;
        await controllerReference.current.dispatchAndCommit({
          type: 'permissionsReconciled',
          records,
          decisions: permissionDecisionsFromRecords(records, current.privacy.permissionDecisions)
        });
      },
      onReminderPlansChanged: async plans => {
        await controllerReference.current.dispatchAndCommit({ type: 'reminderPlansReconciled', plans });
      }
    });
    const controller = new AppRuntimeController({
      loadState: async () => ({ status: 'loaded', state, migrated: false, version: 6 }),
      persistence,
      reduce: relateReducer,
      permissionReminders,
      syncWidget: async () => undefined,
      issues
    });
    controllerReference.current = controller;

    let timeout: ReturnType<typeof setTimeout> | undefined;
    try {
      await Promise.race([
        controller.start(),
        new Promise<never>((_, reject) => {
          timeout = setTimeout(() => reject(new Error('reminder lifecycle deadlocked')), 1_000);
        })
      ]);
    } finally {
      if (timeout) clearTimeout(timeout);
    }
    assert.ok(controller.getSnapshot().state.reminderPlans.length > 0);
  });
});
