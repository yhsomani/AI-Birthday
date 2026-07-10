import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { buildReminderNotificationData } from './notificationRoutes';
import { buildNotificationReadinessReport } from './notificationReadiness';
import { buildReminderPlans } from './reminders';

const notificationGrantedState = () => {
  const state = createTestState();
  return {
    ...state,
    privacy: {
      ...state.privacy,
      permissionDecisions: {
        ...state.privacy.permissionDecisions,
        Notifications: 'Granted' as const
      }
    }
  };
};

describe('notification readiness contract', () => {
  it('marks future review-only reminder plans ready without exposing private payload data', () => {
    const base = notificationGrantedState();
    const reminderPlans = buildReminderPlans(base, [1]);
    const state = {
      ...base,
      reminderPlans
    };
    const report = buildNotificationReadinessReport(state);
    const serializedPayloads = reminderPlans
      .map(plan => `${plan.title} ${plan.body} ${JSON.stringify(buildReminderNotificationData(plan))}`)
      .join(' ');

    assert.equal(report.status, 'Ready');
    assert.equal(report.safeRouteCount, reminderPlans.length);
    assert.ok(report.schedulableCount > 0);
    assert.equal(report.issues.length, 0);
    state.contacts.forEach(contact => {
      assert.equal(serializedPayloads.includes(contact.name), false);
      if (contact.phone) assert.equal(serializedPayloads.includes(contact.phone), false);
      if (contact.email) assert.equal(serializedPayloads.includes(contact.email), false);
    });
    state.messages.forEach(message => {
      assert.equal(serializedPayloads.includes(message.body.slice(0, 20)), false);
    });
  });

  it('warns when notification permission is denied and keeps in-app recovery explicit', () => {
    const base = createTestState();
    const report = buildNotificationReadinessReport({
      ...base,
      privacy: {
        ...base.privacy,
        permissionDecisions: {
          ...base.privacy.permissionDecisions,
          Notifications: 'Denied'
        }
      }
    });

    assert.equal(report.status, 'Warning');
    assert.ok(report.issues.some(issue => issue.id === 'notification-permission-blocked'));
    assert.match(report.summary, /needs review/i);
  });

  it('distinguishes device restrictions from transient permission query failures', () => {
    const restrictedState = createTestState();
    restrictedState.privacy.permissionDecisions.Notifications = 'Denied';
    restrictedState.privacy.permissionRecords = {
      Notifications: {
        capability: 'Notifications',
        userIntent: 'allow',
        systemAuthorization: 'restricted',
        lastKnownAuthorization: 'restricted'
      }
    };
    const restricted = buildNotificationReadinessReport(restrictedState);
    assert.ok(restricted.issues.some(issue => issue.id === 'notification-permission-restricted'));
    assert.match(
      restricted.issues.find(issue => issue.id === 'notification-permission-restricted')?.detail ?? '',
      /device|managed-device/i
    );

    const unavailableState = createTestState();
    unavailableState.privacy.permissionDecisions.Notifications = 'Unavailable';
    unavailableState.privacy.permissionRecords = {
      Notifications: {
        capability: 'Notifications',
        userIntent: 'allow',
        systemAuthorization: 'unavailable',
        lastKnownAuthorization: 'granted',
        queryIssue: 'query-failed'
      }
    };
    const unavailable = buildNotificationReadinessReport(unavailableState);
    assert.ok(unavailable.issues.some(issue => issue.id === 'notification-permission-query-unavailable'));
    assert.match(
      unavailable.issues.find(issue => issue.id === 'notification-permission-query-unavailable')?.detail ?? '',
      /will not change owned native schedules/i
    );
  });

  it('blocks stale reminder routes before native scheduling', () => {
    const base = notificationGrantedState();
    const report = buildNotificationReadinessReport({
      ...base,
      reminderPlans: [
        {
          id: 'reminder-stale',
          eventId: 'missing-event',
          contactId: base.contacts[0].id,
          title: 'RelateAI reminder',
          body: 'Open RelateAI to review.',
          triggerAt: new Date(Date.now() + 60_000).toISOString()
        }
      ]
    });

    assert.equal(report.status, 'Blocked');
    assert.equal(report.staleRouteCount, 1);
    assert.match(report.issues[0]?.detail ?? '', /missing contact or event/i);
  });

  it('blocks notification payloads that expose private relationship data', () => {
    const base = notificationGrantedState();
    const privateMemory = base.memories.find(memory => memory.category === 'Private')!;
    const report = buildNotificationReadinessReport({
      ...base,
      reminderPlans: [
        {
          id: 'reminder-sensitive',
          eventId: base.events[0].id,
          contactId: base.contacts[0].id,
          title: `Reminder for ${base.contacts[0].name}`,
          body: privateMemory.body,
          triggerAt: new Date(Date.now() + 60_000).toISOString()
        }
      ]
    });

    assert.equal(report.status, 'Blocked');
    assert.ok(report.issues.some(issue => issue.id === 'sensitive-payload-reminder-sensitive'));
    assert.match(
      report.issues.find(issue => issue.id === 'sensitive-payload-reminder-sensitive')?.detail ?? '',
      /contact names|private notes|message bodies/i
    );
  });
});
