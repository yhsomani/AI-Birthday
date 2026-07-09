import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { buildReminderNotificationData, readNotificationRouteUrl } from './notificationRoutes';

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
    assert.match(data.url, /^relateai:\/\/events/);
    assert.doesNotMatch(JSON.stringify(data), /send/i);
  });

  it('ignores notification data that does not explicitly open review', () => {
    assert.equal(readNotificationRouteUrl({ url: 'relateai://messages' }), undefined);
    assert.equal(readNotificationRouteUrl({ safeAction: 'open-review', url: 'relateai://messages' }), 'relateai://messages');
  });
});
