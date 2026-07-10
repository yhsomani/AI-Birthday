import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  initializeAndroidReminderNotificationChannelWithAdapter,
  RELATEAI_REMINDER_NOTIFICATION_CHANNEL_ID
} from './notificationChannel';

describe('Android reminder notification channel', () => {
  it('initializes the stable reminder channel with privacy-safe metadata on Android', async () => {
    const calls: { channelId: string; channel: object }[] = [];

    const result = await initializeAndroidReminderNotificationChannelWithAdapter({
      platform: 'android',
      defaultImportance: 3,
      setNotificationChannelAsync: async (channelId, channel) => {
        calls.push({ channelId, channel });
      }
    });

    assert.deepEqual(result, {
      channelId: RELATEAI_REMINDER_NOTIFICATION_CHANNEL_ID,
      initialized: true,
      supported: true
    });
    assert.deepEqual(calls, [
      {
        channelId: RELATEAI_REMINDER_NOTIFICATION_CHANNEL_ID,
        channel: {
          name: 'Relationship reminders',
          description: 'Private reminders to open RelateAI and review relationship tasks.',
          importance: 3,
          enableVibrate: true,
          showBadge: true
        }
      }
    ]);
  });

  it('is an explicit no-op on non-Android platforms', async () => {
    let calls = 0;
    const result = await initializeAndroidReminderNotificationChannelWithAdapter({
      platform: 'ios',
      defaultImportance: 3,
      setNotificationChannelAsync: async () => {
        calls += 1;
      }
    });

    assert.equal(calls, 0);
    assert.deepEqual(result, {
      channelId: RELATEAI_REMINDER_NOTIFICATION_CHANNEL_ID,
      initialized: false,
      supported: false
    });
  });
});
