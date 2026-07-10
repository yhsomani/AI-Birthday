import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  isCapabilityUsable,
  refreshPermissionSnapshotWithAdapters,
  type PermissionStatusAdapters
} from './permissionStatus';

const iosStatuses = {
  NOT_DETERMINED: 0,
  DENIED: 1,
  AUTHORIZED: 2,
  PROVISIONAL: 3,
  EPHEMERAL: 4
};

const authenticationTypes = {
  FINGERPRINT: 1,
  FACIAL_RECOGNITION: 2,
  IRIS: 3
};

const securityLevels = {
  NONE: 0,
  SECRET: 1,
  BIOMETRIC_WEAK: 2,
  BIOMETRIC_STRONG: 3
};

const granted = {
  status: 'granted',
  granted: true,
  canAskAgain: true
};

const adapters = (overrides: Partial<PermissionStatusAdapters> = {}): PermissionStatusAdapters => ({
  contacts: {
    getPermissionsAsync: async () => ({ ...granted, accessPrivileges: 'all' })
  },
  calendar: {
    getCalendarPermissions: async () => granted
  },
  notifications: {
    getPermissionsAsync: async () => granted,
    IosAuthorizationStatus: iosStatuses
  },
  localAuthentication: {
    hasHardwareAsync: async () => true,
    isEnrolledAsync: async () => true,
    supportedAuthenticationTypesAsync: async () => [1, 2],
    getEnrolledLevelAsync: async () => 3,
    AuthenticationType: authenticationTypes,
    SecurityLevel: securityLevels
  },
  now: () => new Date('2026-07-10T08:30:00.000Z'),
  ...overrides
});

describe('live permission and capability snapshot', () => {
  it('maps limited contacts, denied calendar, provisional notifications, and ready biometrics', async () => {
    let promptCalls = 0;
    const snapshot = await refreshPermissionSnapshotWithAdapters(
      adapters({
        contacts: {
          getPermissionsAsync: async () => ({ ...granted, accessPrivileges: 'limited' }),
          requestPermissionsAsync: async () => {
            promptCalls += 1;
            return granted;
          }
        } as PermissionStatusAdapters['contacts'],
        calendar: {
          getCalendarPermissions: async () => ({
            status: 'denied',
            granted: false,
            canAskAgain: false
          })
        },
        notifications: {
          getPermissionsAsync: async () => ({
            ...granted,
            ios: { status: iosStatuses.PROVISIONAL }
          }),
          IosAuthorizationStatus: iosStatuses,
          requestPermissionsAsync: async () => {
            promptCalls += 1;
            return granted;
          }
        } as PermissionStatusAdapters['notifications'],
        localAuthentication: {
          ...adapters().localAuthentication,
          authenticateAsync: async () => {
            promptCalls += 1;
            return { success: true };
          }
        } as PermissionStatusAdapters['localAuthentication']
      })
    );

    assert.equal(promptCalls, 0);
    assert.equal(snapshot.schemaVersion, 1);
    assert.equal(snapshot.checkedAt, '2026-07-10T08:30:00.000Z');
    assert.equal(snapshot.contacts.state, 'limited');
    assert.equal(snapshot.contacts.granted, true);
    assert.equal(snapshot.contacts.accessPrivileges, 'limited');
    assert.equal(snapshot.calendar.state, 'denied');
    assert.equal(snapshot.calendar.canAskAgain, false);
    assert.equal(snapshot.notifications.state, 'limited');
    assert.equal(snapshot.notifications.iosAuthorization, 'provisional');
    assert.equal(snapshot.biometric.state, 'granted');
    assert.equal(snapshot.biometric.ready, true);
    assert.deepEqual(snapshot.biometric.modalities, ['fingerprint', 'facial-recognition']);
    assert.equal(snapshot.biometric.securityLevel, 'biometric-strong');
  });

  it('preserves every iOS notification authorization distinction', async () => {
    const cases = [
      [iosStatuses.NOT_DETERMINED, 'undetermined', 'not-determined'],
      [iosStatuses.DENIED, 'denied', 'denied'],
      [iosStatuses.AUTHORIZED, 'granted', 'authorized'],
      [iosStatuses.PROVISIONAL, 'limited', 'provisional'],
      [iosStatuses.EPHEMERAL, 'limited', 'ephemeral']
    ] as const;

    for (const [iosStatus, expectedState, expectedAuthorization] of cases) {
      const snapshot = await refreshPermissionSnapshotWithAdapters(
        adapters({
          notifications: {
            getPermissionsAsync: async () => ({
              status: iosStatus === iosStatuses.DENIED ? 'denied' : 'undetermined',
              granted: false,
              canAskAgain: true,
              ios: { status: iosStatus }
            }),
            IosAuthorizationStatus: iosStatuses
          }
        })
      );

      assert.equal(snapshot.notifications.state, expectedState);
      assert.equal(snapshot.notifications.iosAuthorization, expectedAuthorization);
    }
  });

  it('fails closed for contradictory contact scope and unknown iOS notification states', async () => {
    const result = await refreshPermissionSnapshotWithAdapters(
      adapters({
        contacts: {
          getPermissionsAsync: async () => ({ ...granted, accessPrivileges: 'none' })
        },
        notifications: {
          getPermissionsAsync: async () => ({ ...granted, ios: { status: 999 } }),
          IosAuthorizationStatus: iosStatuses
        }
      })
    );

    assert.equal(result.contacts.state, 'denied');
    assert.equal(result.contacts.granted, false);
    assert.equal(result.contacts.accessPrivileges, 'none');
    assert.equal(result.notifications.state, 'unavailable');
    assert.equal(result.notifications.issue, 'unsupported-status');
    assert.equal(result.notifications.rawStatus, 'ios:999');
  });

  it('isolates query failures while preserving the OS restricted status', async () => {
    const snapshot = await refreshPermissionSnapshotWithAdapters(
      adapters({
        contacts: {
          getPermissionsAsync: async () => {
            throw new Error('native module unavailable');
          }
        },
        calendar: {
          getCalendarPermissions: async () => ({
            status: 'restricted',
            granted: false,
            canAskAgain: false
          })
        },
        notifications: {
          getPermissionsAsync: async () => {
            throw new Error('query failed');
          },
          IosAuthorizationStatus: iosStatuses
        }
      })
    );

    assert.deepEqual(snapshot.contacts, {
      kind: 'permission',
      state: 'unavailable',
      granted: false,
      issue: 'query-failed'
    });
    assert.equal(snapshot.calendar.state, 'restricted');
    assert.equal(snapshot.calendar.rawStatus, 'restricted');
    assert.equal(snapshot.calendar.issue, undefined);
    assert.equal(snapshot.notifications.state, 'unavailable');
    assert.equal(snapshot.biometric.state, 'granted');
  });

  it('distinguishes missing enrollment from missing biometric hardware and partial query failure', async () => {
    const notEnrolled = await refreshPermissionSnapshotWithAdapters(
      adapters({
        localAuthentication: {
          ...adapters().localAuthentication,
          isEnrolledAsync: async () => false,
          getEnrolledLevelAsync: async () => securityLevels.NONE
        }
      })
    );
    assert.equal(notEnrolled.biometric.state, 'not-enrolled');
    assert.equal(notEnrolled.biometric.reason, 'not-enrolled');
    assert.equal(notEnrolled.biometric.securityLevel, 'none');

    const noHardware = await refreshPermissionSnapshotWithAdapters(
      adapters({
        localAuthentication: {
          ...adapters().localAuthentication,
          hasHardwareAsync: async () => false,
          isEnrolledAsync: async () => false,
          supportedAuthenticationTypesAsync: async () => [],
          getEnrolledLevelAsync: async () => securityLevels.NONE
        }
      })
    );
    assert.equal(noHardware.biometric.state, 'unavailable');
    assert.equal(noHardware.biometric.reason, 'no-hardware');
    assert.equal(noHardware.biometric.queryComplete, true);

    const queryFailure = await refreshPermissionSnapshotWithAdapters(
      adapters({
        localAuthentication: {
          ...adapters().localAuthentication,
          hasHardwareAsync: async () => {
            throw new Error('unavailable');
          }
        }
      })
    );
    assert.equal(queryFailure.biometric.state, 'unavailable');
    assert.equal(queryFailure.biometric.reason, 'query-failed');
    assert.equal(queryFailure.biometric.queryComplete, false);
  });

  it('treats granted and limited authorization as usable', () => {
    assert.equal(isCapabilityUsable('granted'), true);
    assert.equal(isCapabilityUsable('limited'), true);
    assert.equal(isCapabilityUsable('denied'), false);
    assert.equal(isCapabilityUsable('restricted'), false);
    assert.equal(isCapabilityUsable('undetermined'), false);
    assert.equal(isCapabilityUsable('unavailable'), false);
  });
});
