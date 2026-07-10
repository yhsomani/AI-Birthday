import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import type { AppState, PermissionAuthorizationRecord } from '../domain/types';
import type { LivePermissionSnapshot } from '../native/permissionStatus';
import { relateReducer } from '../state/relateReducer';
import {
  PermissionReminderCoordinator,
  createPermissionAuthorizationRecords,
  permissionDecisionForRecord,
  permissionDecisionsFromRecords,
  reconcilePermissionSnapshot,
  recordPermissionPromptOutcome,
  recordPermissionUserIntent
} from './permissionReminderCoordinator';

const nowIso = '2026-07-10T08:30:00.000Z';

const snapshot = (overrides: Partial<LivePermissionSnapshot> = {}): LivePermissionSnapshot => ({
  schemaVersion: 1,
  checkedAt: nowIso,
  contacts: {
    kind: 'permission',
    state: 'granted',
    granted: true,
    canAskAgain: true,
    rawStatus: 'granted',
    accessPrivileges: 'all'
  },
  calendar: {
    kind: 'permission',
    state: 'granted',
    granted: true,
    canAskAgain: true,
    rawStatus: 'granted'
  },
  notifications: {
    kind: 'permission',
    state: 'granted',
    granted: true,
    canAskAgain: true,
    rawStatus: 'granted',
    iosAuthorization: 'authorized'
  },
  biometric: {
    kind: 'capability',
    state: 'granted',
    ready: true,
    reason: 'ready',
    hardwareAvailable: true,
    enrolled: true,
    modalities: ['fingerprint'],
    rawAuthenticationTypes: [1],
    securityLevel: 'biometric-strong',
    queryComplete: true
  },
  ...overrides
});

const stateWithUpcomingEvent = (): AppState => {
  const state = createTestState();
  state.events = [
    {
      ...state.events[0],
      id: 'event-upcoming',
      date: '2026-07-20T12:00:00.000Z',
      recurrence: {
        frequency: 'Yearly',
        month: 7,
        day: 20,
        originalYear: 2026,
        leapDayPolicy: 'February 28'
      }
    }
  ];
  state.settings.notificationsEnabled = true;
  state.reminderPlans = [];
  return state;
};

describe('permission authorization records', () => {
  it('keeps intent and prompt history separate from limited/restricted live state', () => {
    const state = stateWithUpcomingEvent();
    let records = createPermissionAuthorizationRecords(state.privacy);
    records = recordPermissionUserIntent(records, 'Contacts', 'allow', '2026-07-01T00:00:00.000Z');
    records = recordPermissionPromptOutcome(records, 'Contacts', 'denied', '2026-07-01T00:01:00.000Z');

    const refreshed = reconcilePermissionSnapshot(
      records,
      snapshot({
        contacts: {
          kind: 'permission',
          state: 'limited',
          granted: true,
          canAskAgain: false,
          rawStatus: 'granted',
          accessPrivileges: 'limited'
        },
        calendar: {
          kind: 'permission',
          state: 'restricted',
          granted: false,
          canAskAgain: false,
          rawStatus: 'restricted'
        }
      })
    );

    assert.equal(refreshed.Contacts.userIntent, 'allow');
    assert.equal(refreshed.Contacts.userIntentUpdatedAt, '2026-07-01T00:00:00.000Z');
    assert.equal(refreshed.Contacts.lastPromptOutcome, 'denied');
    assert.equal(refreshed.Contacts.systemAuthorization, 'limited');
    assert.match(refreshed.Contacts.platformStatus ?? '', /access=limited/);
    assert.equal(refreshed.Calendar.systemAuthorization, 'restricted');
    assert.equal(permissionDecisionForRecord(refreshed.Calendar), 'Denied');
    assert.equal(permissionDecisionForRecord(refreshed.Contacts), 'Granted');

    const legacy = permissionDecisionsFromRecords(refreshed, state.privacy.permissionDecisions);
    assert.equal(legacy.Contacts, 'Granted');
    assert.equal(legacy.Calendar, 'Denied');
    assert.equal(legacy['Email provider'], state.privacy.permissionDecisions['Email provider']);

    const persisted = relateReducer(state, {
      type: 'permissionsReconciled',
      records: refreshed,
      decisions: legacy
    });
    assert.equal(persisted.privacy.permissionRecords?.Contacts?.userIntent, 'allow');
    assert.equal(persisted.privacy.permissionRecords?.Contacts?.lastPromptOutcome, 'denied');
    assert.equal(persisted.privacy.permissionDecisions.Contacts, 'Granted');
  });

  it('retains the last known authorization when the current query is unavailable', () => {
    const state = stateWithUpcomingEvent();
    const records = createPermissionAuthorizationRecords(state.privacy);
    const granted = reconcilePermissionSnapshot(records, snapshot());
    const unavailable = reconcilePermissionSnapshot(
      granted,
      snapshot({
        notifications: {
          kind: 'permission',
          state: 'unavailable',
          granted: false,
          issue: 'query-failed'
        }
      })
    );

    assert.equal(unavailable.Notifications.systemAuthorization, 'unavailable');
    assert.equal(unavailable.Notifications.lastKnownAuthorization, 'granted');
    assert.equal(unavailable.Notifications.queryIssue, 'query-failed');
  });
});

describe('permission and reminder lifecycle coordinator', () => {
  it('refreshes after hydration and diffs desired reminders without prompting', async () => {
    const state = stateWithUpcomingEvent();
    let readCalls = 0;
    let reconciledPlans: string[] = [];
    let publishedPlans: string[] = [];
    let publishedRecords: Record<string, PermissionAuthorizationRecord> | undefined;
    const coordinator = new PermissionReminderCoordinator({
      now: () => new Date(nowIso),
      readPermissionSnapshot: async () => {
        readCalls += 1;
        return snapshot();
      },
      reconcileReminderNotifications: async plans => {
        reconciledPlans = plans.map(plan => plan.id);
        return {
          scheduled: plans.length,
          skipped: 0,
          cancelled: 0,
          unchanged: 0,
          authorization: 'authorized'
        };
      },
      onPermissionRecordsChanged: records => {
        publishedRecords = records;
      },
      onReminderPlansChanged: plans => {
        publishedPlans = plans.map(plan => plan.id);
      }
    });

    const result = await coordinator.afterHydration(state);

    assert.equal(readCalls, 1);
    assert.equal(result.status, 'reconciled');
    assert.ok(result.plannedReminders.length > 0);
    assert.deepEqual(
      reconciledPlans,
      result.plannedReminders.map(plan => plan.id)
    );
    assert.deepEqual(publishedPlans, reconciledPlans);
    assert.equal(publishedRecords?.Notifications.systemAuthorization, 'granted');
  });

  it('cancels stale owned reminders when live notification permission is denied or restricted', async () => {
    const state = stateWithUpcomingEvent();
    const calls: number[] = [];
    let notificationState: 'denied' | 'restricted' = 'denied';
    const coordinator = new PermissionReminderCoordinator({
      now: () => new Date(nowIso),
      readPermissionSnapshot: async () =>
        snapshot({
          notifications: {
            kind: 'permission',
            state: notificationState,
            granted: false,
            canAskAgain: false,
            rawStatus: notificationState
          }
        }),
      reconcileReminderNotifications: async plans => {
        calls.push(plans.length);
        return { scheduled: 0, skipped: 0, cancelled: 3, unchanged: 0 };
      }
    });

    const denied = await coordinator.afterHydration(state);
    notificationState = 'restricted';
    const restricted = await coordinator.afterPermissionStatusChange(state);

    assert.equal(denied.status, 'reconciled');
    assert.equal(restricted.status, 'reconciled');
    assert.deepEqual(calls, [0, 0]);
    assert.equal(restricted.records.Notifications.systemAuthorization, 'restricted');
    assert.ok(restricted.plannedReminders.length > 0, 'in-app plans remain available');
  });

  it('does not mutate native schedules when authorization cannot be queried', async () => {
    const state = stateWithUpcomingEvent();
    state.privacy.permissionRecords = {
      Notifications: {
        capability: 'Notifications',
        userIntent: 'allow',
        systemAuthorization: 'granted',
        lastKnownAuthorization: 'granted'
      }
    };
    let reconcileCalls = 0;
    const errors: string[] = [];
    const coordinator = new PermissionReminderCoordinator({
      now: () => new Date(nowIso),
      readPermissionSnapshot: async () => {
        throw new Error('bridge offline');
      },
      reconcileReminderNotifications: async () => {
        reconcileCalls += 1;
        return { scheduled: 0, skipped: 0, cancelled: 0, unchanged: 0 };
      },
      onError: stage => {
        errors.push(stage);
      }
    });

    const result = await coordinator.afterHydration(state);

    assert.equal(result.status, 'permission-status-unavailable');
    assert.equal(reconcileCalls, 0);
    assert.deepEqual(errors, ['permission-query']);
    assert.equal(result.records.Notifications.systemAuthorization, 'unavailable');
    assert.equal(result.records.Notifications.lastKnownAuthorization, 'granted');
  });

  it('defers background work without reading permissions and catches up on foreground', async () => {
    const state = stateWithUpcomingEvent();
    let reads = 0;
    let reconciles = 0;
    const coordinator = new PermissionReminderCoordinator({
      now: () => new Date(nowIso),
      readPermissionSnapshot: async () => {
        reads += 1;
        return snapshot();
      },
      reconcileReminderNotifications: async plans => {
        reconciles += 1;
        return { scheduled: plans.length, skipped: 0, cancelled: 0, unchanged: 0 };
      }
    });

    const deferred = await coordinator.afterCommittedChange(state, 'events', 'background');
    const foreground = await coordinator.onForeground(state);

    assert.equal(deferred.status, 'deferred-background');
    assert.equal(reads, 1);
    assert.equal(reconciles, 1);
    assert.equal(foreground.status, 'reconciled');
  });

  it('refreshes immediately before each protected operation and maps limited/restricted accurately', async () => {
    const state = stateWithUpcomingEvent();
    let reads = 0;
    const coordinator = new PermissionReminderCoordinator({
      now: () => new Date(nowIso),
      readPermissionSnapshot: async () => {
        reads += 1;
        return snapshot({
          contacts: {
            kind: 'permission',
            state: 'limited',
            granted: true,
            rawStatus: 'granted',
            accessPrivileges: 'limited'
          },
          calendar: {
            kind: 'permission',
            state: 'restricted',
            granted: false,
            rawStatus: 'restricted',
            canAskAgain: false
          }
        });
      },
      reconcileReminderNotifications: async () => ({
        scheduled: 0,
        skipped: 0,
        cancelled: 0,
        unchanged: 0
      })
    });

    const contacts = await coordinator.beforeOperation(state, 'Contacts');
    const calendar = await coordinator.beforeOperation(state, 'Calendar');

    assert.equal(reads, 2);
    assert.equal(contacts.allowed, true);
    assert.equal(contacts.authorization, 'limited');
    assert.equal(calendar.allowed, false);
    assert.equal(calendar.authorization, 'restricted');
  });

  it('reconciles an empty native set after notifications are explicitly disabled even if live status is unavailable', async () => {
    const state = stateWithUpcomingEvent();
    state.settings.notificationsEnabled = false;
    let receivedPlanCount = -1;
    const coordinator = new PermissionReminderCoordinator({
      now: () => new Date(nowIso),
      readPermissionSnapshot: async () =>
        snapshot({
          notifications: {
            kind: 'permission',
            state: 'unavailable',
            granted: false,
            issue: 'query-failed'
          }
        }),
      reconcileReminderNotifications: async plans => {
        receivedPlanCount = plans.length;
        return { scheduled: 0, skipped: 0, cancelled: 2, unchanged: 0 };
      }
    });

    const result = await coordinator.afterCommittedChange(state, 'settings');

    assert.equal(result.status, 'reconciled');
    assert.equal(receivedPlanCount, 0);
  });
});
