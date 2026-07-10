import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { NotificationChannelInitializationResult } from './notificationChannel';
import { requestSystemPermissionWithAdapters, type PermissionRequestAdapters } from './permissionRequest';

const statuses = {
  NOT_DETERMINED: 0,
  DENIED: 1,
  AUTHORIZED: 2,
  PROVISIONAL: 3,
  EPHEMERAL: 4
};

const channelResult: NotificationChannelInitializationResult = {
  channelId: 'relateai-reminders',
  initialized: true,
  supported: true
};

const adapters = (overrides: Partial<PermissionRequestAdapters> = {}): PermissionRequestAdapters => ({
  contacts: {
    requestPermissionsAsync: async () => ({
      status: 'granted',
      granted: true,
      canAskAgain: true,
      accessPrivileges: 'all'
    })
  },
  calendar: {
    requestCalendarPermissions: async () => ({ status: 'granted', granted: true, canAskAgain: true })
  },
  notifications: {
    requestPermissionsAsync: async () => ({ status: 'granted', granted: true, canAskAgain: true }),
    IosAuthorizationStatus: statuses
  },
  initializeNotificationChannel: async () => channelResult,
  now: () => new Date('2026-07-10T08:30:00.000Z'),
  ...overrides
});

describe('explicit native permission requests', () => {
  it('maps limited contact scope without treating it as full access', async () => {
    const result = await requestSystemPermissionWithAdapters(
      'Contacts',
      adapters({
        contacts: {
          requestPermissionsAsync: async () => ({
            status: 'granted',
            granted: true,
            canAskAgain: false,
            accessPrivileges: 'limited'
          })
        }
      })
    );

    assert.deepEqual(result, {
      capability: 'Contacts',
      outcome: 'limited',
      systemAuthorization: 'limited',
      promptedAt: '2026-07-10T08:30:00.000Z',
      canAskAgain: false,
      platformStatus: 'granted'
    });
  });

  it('preserves restricted calendar authorization as a distinct prompt outcome', async () => {
    const result = await requestSystemPermissionWithAdapters(
      'Calendar',
      adapters({
        calendar: {
          requestCalendarPermissions: async () => ({
            status: 'restricted',
            granted: false,
            canAskAgain: false
          })
        }
      })
    );

    assert.equal(result.outcome, 'restricted');
    assert.equal(result.systemAuthorization, 'restricted');
    assert.equal(result.canAskAgain, false);
  });

  it('initializes the Android channel before prompting and maps provisional iOS access as limited', async () => {
    const order: string[] = [];
    const result = await requestSystemPermissionWithAdapters(
      'Notifications',
      adapters({
        initializeNotificationChannel: async () => {
          order.push('channel');
          return channelResult;
        },
        notifications: {
          IosAuthorizationStatus: statuses,
          requestPermissionsAsync: async () => {
            order.push('prompt');
            return {
              status: 'granted',
              granted: true,
              canAskAgain: false,
              ios: { status: statuses.PROVISIONAL }
            };
          }
        }
      })
    );

    assert.deepEqual(order, ['channel', 'prompt']);
    assert.equal(result.outcome, 'limited');
    assert.deepEqual(result.notificationChannel, channelResult);
  });

  it('rejects unsupported native statuses instead of silently granting access', async () => {
    await assert.rejects(
      requestSystemPermissionWithAdapters(
        'Calendar',
        adapters({
          calendar: {
            requestCalendarPermissions: async () => ({
              status: 'future-status',
              granted: false,
              canAskAgain: true
            })
          }
        })
      ),
      /unsupported permission status/
    );

    await assert.rejects(
      requestSystemPermissionWithAdapters(
        'Notifications',
        adapters({
          notifications: {
            IosAuthorizationStatus: statuses,
            requestPermissionsAsync: async () => ({
              status: 'granted',
              granted: true,
              canAskAgain: true,
              ios: { status: 999 }
            })
          }
        })
      ),
      /unsupported notification permission status/
    );
  });
});
