import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, it } from 'node:test';

const source = readFileSync(join(process.cwd(), 'src/native/reminderScheduler.ts'), 'utf8');

describe('reminder scheduler source contract', () => {
  it('reconciles RelateAI reminders without clearing unrelated scheduled notifications', () => {
    assert.match(source, /reconcileReminderPlansWithAdapter/);
    assert.match(source, /getScheduledNotifications: Notifications\.getAllScheduledNotificationsAsync/);
    assert.match(source, /cancelScheduledNotification: Notifications\.cancelScheduledNotificationAsync/);
    assert.doesNotMatch(source, /cancelAllScheduledNotificationsAsync/);
  });

  it('has a lifecycle path that reads permission without requesting it', () => {
    assert.match(source, /reconcileReminderPlansWithoutPrompt[\s\S]+Notifications\.getPermissionsAsync\(\)/);
  });

  it('initializes and targets the stable Android reminder channel', () => {
    assert.match(source, /await initializeAndroidReminderNotificationChannel\(\)/);
    assert.match(source, /channelId: RELATEAI_REMINDER_NOTIFICATION_CHANNEL_ID/);
  });

  it('uses fixed privacy-minimized copy instead of persisted event or message text', () => {
    assert.match(source, /privacyMinimizedNotificationContent\(plan\)/);
    assert.doesNotMatch(source, /title:\s*plan\.title|body:\s*plan\.body/);
  });
});
