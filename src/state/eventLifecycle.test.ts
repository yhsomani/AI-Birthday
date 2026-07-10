import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from './relateReducer';
import { createTestState } from '../test/testState';
import type { AppState, ReminderPlan } from '../domain/types';
import { previewEventDelete, previewEventEdit } from '../domain/eventLifecycle';

const reminderFor = (contactId: string, eventId: string): ReminderPlan => ({
  id: `reminder-${eventId}`,
  contactId,
  eventId,
  title: 'RelateAI reminder',
  body: 'Open RelateAI to review.',
  triggerAt: '2026-08-01T09:00:00.000Z'
});

describe('event lifecycle contract', () => {
  it('requires a current cascade preview before editing linked messages and reminders', () => {
    const base = createTestState();
    const state: AppState = {
      ...base,
      reminderPlans: [reminderFor('c-asha', 'e-asha-bday')],
      calendarSync: {
        ...base.calendarSync,
        lastExportedAt: '2026-07-09T09:00:00.000Z'
      },
      messages: base.messages.map(message =>
        message.id === 'msg-asha-bday'
          ? {
              ...message,
              status: 'Scheduled',
              approvedAt: '2026-07-09T09:00:00.000Z',
              approvalExpiresAt: '2026-07-10T09:00:00.000Z'
            }
          : message
      )
    };
    const input = {
      contactId: 'c-asha',
      eventType: 'Birthday' as const,
      label: 'Asha birthday celebration',
      date: '2026-08-20',
      verified: true
    };
    const preview = previewEventEdit(state, 'e-asha-bday', input);
    assert.equal(preview.ok, true);
    if (!preview.ok) return;
    assert.equal(preview.requiresConfirmation, true);
    assert.equal(preview.impact.reminderCount, 1);

    const rejected = relateReducer(state, {
      type: 'editEvent',
      eventId: 'e-asha-bday',
      input
    });
    const accepted = relateReducer(state, {
      type: 'editEvent',
      eventId: 'e-asha-bday',
      input,
      confirmationToken: preview.confirmationToken
    });

    assert.notEqual(rejected.events.find(event => event.id === 'e-asha-bday')?.date.slice(0, 10), '2026-08-20');
    assert.equal(accepted.events.find(event => event.id === 'e-asha-bday')?.date.slice(0, 10), '2026-08-20');
    assert.equal(
      accepted.reminderPlans.some(plan => plan.eventId === 'e-asha-bday'),
      false
    );
    assert.equal(accepted.calendarSync.lastExportedAt, undefined);
    const message = accepted.messages.find(item => item.id === 'msg-asha-bday');
    assert.equal(message?.status, 'Needs review');
    assert.equal(message?.scheduledFor?.slice(0, 10), '2026-08-20');
    assert.equal(message?.approvedAt, undefined);
  });

  it('allows a non-cascading event edit through explicit save without an extra token', () => {
    const base = createTestState();
    const event = {
      ...base.events[2],
      id: 'e-standalone',
      label: 'Standalone reminder',
      contactId: 'c-mira'
    };
    const state: AppState = {
      ...base,
      events: [event, ...base.events],
      messages: base.messages.filter(message => message.eventId !== event.id),
      reminderPlans: [],
      calendarSync: { exportedCount: 0, importedCount: 0 }
    };
    const input = {
      contactId: 'c-mira',
      eventType: 'Custom' as const,
      label: 'Standalone reminder updated',
      date: '2026-09-12',
      verified: true
    };
    const preview = previewEventEdit(state, event.id, input);
    assert.equal(preview.ok, true);
    if (!preview.ok) return;
    assert.equal(preview.requiresConfirmation, false);

    const next = relateReducer(state, { type: 'editEvent', eventId: event.id, input });
    assert.equal(next.events.find(item => item.id === event.id)?.type, 'Custom');
    assert.equal(next.events.find(item => item.id === event.id)?.label, input.label);
  });

  it('keeps an existing draft with its original recipient when an event moves contacts', () => {
    const state = createTestState();
    const input = {
      contactId: 'c-mira',
      eventType: 'Birthday' as const,
      label: 'Reassigned birthday',
      date: '2026-08-21',
      verified: false
    };
    const preview = previewEventEdit(state, 'e-asha-bday', input);
    assert.equal(preview.ok, true);
    if (!preview.ok) return;
    const next = relateReducer(state, {
      type: 'editEvent',
      eventId: 'e-asha-bday',
      input,
      confirmationToken: preview.confirmationToken
    });
    const message = next.messages.find(item => item.id === 'msg-asha-bday');

    assert.equal(next.events.find(item => item.id === 'e-asha-bday')?.contactId, 'c-mira');
    assert.equal(message?.contactId, 'c-asha');
    assert.equal(message?.eventId, undefined);
    assert.equal(message?.status, 'Needs review');
  });

  it('requires confirmation before a contact reassignment detaches sent history', () => {
    const base = createTestState();
    const sentMessage = {
      ...base.messages[0],
      id: 'msg-sent-only',
      eventId: 'e-rajesh-work',
      contactId: 'c-rajesh',
      status: 'Sent' as const,
      sentAt: '2026-07-01T09:00:00.000Z'
    };
    const state: AppState = {
      ...base,
      messages: [sentMessage, ...base.messages.filter(message => message.eventId !== 'e-rajesh-work')],
      reminderPlans: [],
      calendarSync: { exportedCount: 0, importedCount: 0 }
    };
    const event = state.events.find(item => item.id === 'e-rajesh-work');
    assert.ok(event);
    const input = {
      contactId: 'c-mira',
      eventType: event.type,
      label: event.label,
      date: event.date.slice(0, 10),
      verified: event.verified
    };
    const preview = previewEventEdit(state, event.id, input);
    assert.equal(preview.ok, true);
    if (!preview.ok) return;
    assert.equal(preview.impact.activeMessageCount, 0);
    assert.equal(preview.impact.historyMessageCount, 1);
    assert.equal(preview.requiresConfirmation, true);

    const rejected = relateReducer(state, { type: 'editEvent', eventId: event.id, input });
    const accepted = relateReducer(state, {
      type: 'editEvent',
      eventId: event.id,
      input,
      confirmationToken: preview.confirmationToken
    });

    assert.equal(rejected.events.find(item => item.id === event.id)?.contactId, 'c-rajesh');
    assert.equal(accepted.events.find(item => item.id === event.id)?.contactId, 'c-mira');
    assert.equal(accepted.messages.find(message => message.id === sentMessage.id)?.eventId, undefined);
    assert.equal(accepted.messages.find(message => message.id === sentMessage.id)?.status, 'Sent');
  });

  it('deletes an event only with a fresh token and preserves linked message history', () => {
    const base = createTestState();
    const sentMessage = {
      ...base.messages[0],
      id: 'msg-asha-sent-history',
      status: 'Sent' as const,
      sentAt: '2026-07-01T09:00:00.000Z'
    };
    const state: AppState = {
      ...base,
      messages: [sentMessage, ...base.messages],
      reminderPlans: [reminderFor('c-asha', 'e-asha-bday')]
    };
    const preview = previewEventDelete(state, 'e-asha-bday');
    assert.equal(preview.ok, true);
    if (!preview.ok) return;

    const rejected = relateReducer(state, {
      type: 'deleteEvent',
      eventId: 'e-asha-bday',
      confirmationToken: 'stale-token'
    });
    const accepted = relateReducer(state, {
      type: 'deleteEvent',
      eventId: 'e-asha-bday',
      confirmationToken: preview.confirmationToken
    });

    assert.ok(rejected.events.some(event => event.id === 'e-asha-bday'));
    assert.equal(
      accepted.events.some(event => event.id === 'e-asha-bday'),
      false
    );
    assert.equal(
      accepted.reminderPlans.some(plan => plan.eventId === 'e-asha-bday'),
      false
    );
    assert.equal(accepted.messages.length, state.messages.length);
    assert.equal(accepted.messages.find(message => message.id === sentMessage.id)?.status, 'Sent');
    assert.equal(accepted.messages.find(message => message.id === sentMessage.id)?.eventId, undefined);
    assert.equal(accepted.messages.find(message => message.id === 'msg-asha-bday')?.status, 'Needs review');
  });

  it('invalidates a delete token when linked reminder state changes', () => {
    const state = createTestState();
    const preview = previewEventDelete(state, 'e-rajesh-work');
    assert.equal(preview.ok, true);
    if (!preview.ok) return;
    const changed: AppState = {
      ...state,
      reminderPlans: [reminderFor('c-rajesh', 'e-rajesh-work')]
    };
    const next = relateReducer(changed, {
      type: 'deleteEvent',
      eventId: 'e-rajesh-work',
      confirmationToken: preview.confirmationToken
    });

    assert.ok(next.events.some(event => event.id === 'e-rajesh-work'));
    assert.match(next.activity[0].detail, /changed after preview/i);
  });
});
