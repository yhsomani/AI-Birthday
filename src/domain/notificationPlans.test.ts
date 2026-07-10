import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import {
  buildOwnedNotificationPlans,
  buildSupplementalNotificationPlans,
  notificationKindForPlan,
  privacyMinimizedNotificationContent,
  validateOwnedNotificationPlanForState,
  validateOwnedNotificationPlanStructure
} from './notificationPlans';

const now = new Date('2026-07-10T08:00:00.000Z');

describe('notification coverage planning', () => {
  it('plans approvals, fallback review, setup, recovery, and due check-ins with generic copy', () => {
    const state = createTestState();
    const activeContact = state.contacts[0];
    state.onboarding.completed = false;
    state.persistence = { status: 'Error', error: 'private filesystem detail' };
    state.messages = [
      {
        ...state.messages[0],
        id: 'pending-ai',
        contactId: activeContact.id,
        status: 'Needs review',
        quality: 'AI draft',
        body: 'Private pending body'
      },
      {
        ...state.messages[0],
        id: 'pending-fallback',
        contactId: activeContact.id,
        status: 'Needs review',
        quality: 'Template fallback',
        body: 'Private fallback body'
      },
      {
        ...state.messages[0],
        id: 'failed-message',
        contactId: activeContact.id,
        status: 'Failed',
        quality: 'AI draft',
        body: 'Private failed body',
        lastError: 'provider secret detail'
      }
    ];
    state.contacts.forEach(contact => {
      contact.lastContactedAt = undefined;
      contact.checkInSnoozedUntil = undefined;
    });

    const plans = buildSupplementalNotificationPlans(state, now);
    const kinds = new Set(plans.map(notificationKindForPlan));

    assert.deepEqual(
      [...kinds].sort(),
      ['check-in-suggestion', 'fallback-review', 'pending-approval', 'recovery-issue', 'setup-blocker'].sort()
    );
    assert.ok(plans.every(plan => Date.parse(plan.triggerAt) > now.getTime()));
    assert.ok(plans.every(plan => validateOwnedNotificationPlanForState(state, plan, now).ok));

    const nativeCopy = plans.map(privacyMinimizedNotificationContent);
    const serialized = JSON.stringify(nativeCopy);
    state.contacts.forEach(contact => assert.equal(serialized.includes(contact.name), false));
    state.messages.forEach(message => {
      assert.equal(serialized.includes(message.body), false);
      if (message.lastError) assert.equal(serialized.includes(message.lastError), false);
    });
    assert.equal(serialized.includes(state.persistence.error ?? ''), false);
  });

  it('keeps raw references out of deterministic notification identifiers', () => {
    const state = createTestState();
    state.messages = [
      {
        ...state.messages[0],
        id: 'recipient-name-private-message-reference',
        status: 'Needs review',
        quality: 'AI draft'
      }
    ];

    const plans = buildSupplementalNotificationPlans(state, now);
    const approval = plans.find(plan => notificationKindForPlan(plan) === 'pending-approval');
    assert.ok(approval);
    assert.equal(approval.id.includes('recipient-name-private-message-reference'), false);
    assert.equal(approval.messageId, 'recipient-name-private-message-reference');
  });

  it('keeps live provider and permission setup gaps visible after onboarding completes', () => {
    const state = createTestState();
    state.onboarding.completed = true;
    state.setupChecks = state.setupChecks.map(check => ({ ...check, status: 'Ready' }));
    state.backups = [{ id: 'backup-current', createdAt: now.toISOString(), recordCount: 1, encrypted: true }];
    state.aiProvider = { status: 'Not configured' };
    state.privacy.permissionDecisions.Notifications = 'Granted';

    const providerPlan = buildSupplementalNotificationPlans(state, now).find(
      plan => notificationKindForPlan(plan) === 'setup-blocker'
    );
    assert.ok(providerPlan);
    assert.equal(validateOwnedNotificationPlanForState(state, providerPlan, now).ok, true);

    state.settings.aiEnabled = false;
    state.privacy.permissionDecisions.Notifications = 'Denied';
    const permissionPlan = buildSupplementalNotificationPlans(state, now).find(
      plan => notificationKindForPlan(plan) === 'setup-blocker'
    );
    assert.ok(permissionPlan);
    assert.equal(validateOwnedNotificationPlanForState(state, permissionPlan, now).ok, true);
  });

  it('includes draft-status messages that are eligible for review and approval', () => {
    const state = createTestState();
    state.messages = [{ ...state.messages[0], id: 'draft-review', status: 'Draft', eventId: undefined }];

    const plan = buildSupplementalNotificationPlans(state, now).find(item => item.messageId === 'draft-review');
    assert.ok(plan);
    assert.equal(validateOwnedNotificationPlanForState(state, plan, now).ok, true);
  });

  it('blocks stale entity references and already-resolved notification targets', () => {
    const state = createTestState();
    state.messages[0].status = 'Needs review';
    state.messages[0].quality = 'AI draft';
    const plan = buildSupplementalNotificationPlans(state, now).find(
      item => notificationKindForPlan(item) === 'pending-approval'
    );
    assert.ok(plan);
    assert.equal(validateOwnedNotificationPlanForState(state, plan, now).ok, true);

    state.messages[0].status = 'Sent';
    assert.deepEqual(validateOwnedNotificationPlanForState(state, plan, now), {
      ok: false,
      reason: 'stale-message'
    });

    state.messages[0].status = 'Needs review';
    state.contacts.find(contact => contact.id === plan.contactId)!.archivedAt = now.toISOString();
    assert.equal(validateOwnedNotificationPlanForState(state, plan, now).ok, false);
  });

  it('blocks approval prompts when a draft event no longer matches its contact', () => {
    const state = createTestState();
    state.messages[0].status = 'Needs review';
    state.messages[0].quality = 'AI draft';
    state.messages[0].eventId = state.events.find(event => event.contactId !== state.messages[0].contactId)!.id;
    const plan = buildSupplementalNotificationPlans(state, now).find(
      item => notificationKindForPlan(item) === 'pending-approval'
    );

    assert.ok(plan);
    assert.deepEqual(validateOwnedNotificationPlanForState(state, plan, now), {
      ok: false,
      reason: 'stale-message'
    });
  });

  it('combines persisted event reminders with supplemental plans without changing the event record', () => {
    const state = createTestState();
    const eventPlan = {
      id: 'reminder-event',
      eventId: state.events[0].id,
      contactId: state.events[0].contactId,
      title: `Reminder for ${state.contacts[0].name}`,
      body: state.memories[0].body,
      triggerAt: '2026-07-20T09:00:00.000Z'
    };
    const plans = buildOwnedNotificationPlans(state, [eventPlan], now);
    const nativeEvent = plans.find(plan => plan.id === eventPlan.id);

    assert.equal(notificationKindForPlan(nativeEvent!), 'event-reminder');
    assert.equal(nativeEvent?.eventId, eventPlan.eventId);
    assert.deepEqual(privacyMinimizedNotificationContent(nativeEvent!), {
      title: 'RelateAI event reminder',
      body: 'Open RelateAI to review an upcoming relationship event.'
    });
  });

  it('rejects malformed or past native plans before reconciliation', () => {
    assert.deepEqual(
      validateOwnedNotificationPlanStructure(
        {
          id: 'invalid\nidentifier',
          kind: 'setup-blocker',
          title: 'Setup',
          body: 'Review',
          triggerAt: '2026-07-20T09:00:00.000Z'
        },
        now
      ),
      { ok: false, reason: 'invalid-plan' }
    );
    assert.equal(
      validateOwnedNotificationPlanStructure(
        {
          id: 'past-plan',
          kind: 'setup-blocker',
          title: 'Setup',
          body: 'Review',
          triggerAt: '2026-07-01T09:00:00.000Z'
        },
        now
      ).ok,
      false
    );
  });

  it('keeps the same daily trigger while quiet hours defer it later that morning', () => {
    const state = createTestState();
    state.settings.quietHours = { start: '08:00', end: '10:00' };
    const before = buildSupplementalNotificationPlans(state, new Date('2026-07-10T07:30:00'));
    const during = buildSupplementalNotificationPlans(state, new Date('2026-07-10T09:30:00'));
    const beforeSetup = before.find(plan => notificationKindForPlan(plan) === 'setup-blocker');
    const duringSetup = during.find(plan => notificationKindForPlan(plan) === 'setup-blocker');

    assert.equal(beforeSetup?.triggerAt, duringSetup?.triggerAt);
    assert.equal(new Date(beforeSetup!.triggerAt).getHours(), 10);
  });

  it('localizes fixed native copy without adding relationship content', () => {
    const state = createTestState();
    state.settings.locale = 'hi-IN';
    const plan = buildSupplementalNotificationPlans(state, now).find(
      item => notificationKindForPlan(item) === 'setup-blocker'
    );
    const content = privacyMinimizedNotificationContent(plan!);

    assert.match(content.title, /रिमाइंडर/);
    assert.match(content.body, /RelateAI/);
    state.contacts.forEach(contact => assert.equal(JSON.stringify(content).includes(contact.name), false));
  });
});
