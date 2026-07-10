import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  buildReminderNotificationData,
  isOwnedRelateAiNotificationData,
  readNotificationRouteUrl
} from './notificationRoutes';

describe('notification route contract', () => {
  it('builds reminder notification data that only opens a review route', () => {
    const data = buildReminderNotificationData({
      id: 'reminder-1',
      eventId: 'event-1',
      contactId: 'contact-1',
      title: 'Reminder',
      body: 'Review',
      triggerAt: new Date(Date.now() + 1000).toISOString()
    });

    assert.equal(data.safeAction, 'open-review');
    assert.equal(data.owner, 'relateai');
    assert.equal(data.contractVersion, 1);
    assert.match(readNotificationRouteUrl(data) ?? '', /^relateai:\/\/events/);
    assert.doesNotMatch(JSON.stringify(data), /send/i);
  });

  it('ignores notification data that does not explicitly open review', () => {
    assert.equal(readNotificationRouteUrl({ url: 'relateai://messages' }), undefined);
    assert.equal(readNotificationRouteUrl({ safeAction: 'open-review', url: 'relateai://messages' }), undefined);
    assert.equal(
      readNotificationRouteUrl({
        route: 'event-reminder',
        safeAction: 'open-review',
        eventId: 'event-1',
        contactId: 'contact-1',
        url: 'relateai://messages'
      }),
      'relateai://events?eventId=event-1&contactId=contact-1'
    );
  });

  it('reconstructs only typed review routes for approvals, recovery, setup, and check-ins', () => {
    const approval = buildReminderNotificationData({
      id: 'approval-1',
      kind: 'pending-approval',
      messageId: 'message-1',
      contactId: 'contact-1',
      title: 'Message review reminder',
      body: 'Review',
      triggerAt: new Date(Date.now() + 1_000).toISOString(),
      locale: 'hi-IN'
    });
    const checkIn = buildReminderNotificationData({
      id: 'check-in-1',
      kind: 'check-in-suggestion',
      contactId: 'contact-1',
      title: 'Relationship check-in',
      body: 'Review',
      triggerAt: new Date(Date.now() + 1_000).toISOString()
    });

    assert.equal(readNotificationRouteUrl(approval), 'relateai://message/message-1');
    assert.equal('locale' in approval, false);
    assert.equal(readNotificationRouteUrl(checkIn), 'relateai://contact/contact-1');
    assert.equal(
      readNotificationRouteUrl({
        ...approval,
        safeAction: 'send-now',
        url: 'relateai://message/message-1'
      }),
      undefined
    );
    assert.equal(
      readNotificationRouteUrl({
        owner: 'relateai',
        contractVersion: 1,
        route: 'setup-blocker',
        safeAction: 'open-review',
        url: 'https://attacker.invalid'
      }),
      'relateai://setup'
    );
    assert.equal(isOwnedRelateAiNotificationData(approval, 'approval-1'), true);
    assert.equal(
      isOwnedRelateAiNotificationData({ route: 'event-reminder', safeAction: 'open-review' }, 'unrelated'),
      false
    );
  });
});
