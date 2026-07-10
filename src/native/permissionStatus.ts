import type { SystemAuthorization } from '../domain/types';

export type LiveAuthorizationState = Exclude<SystemAuthorization, 'not-enrolled'>;

export type PermissionQueryIssue = 'query-failed' | 'unsupported-status';

interface SdkPermissionResponse {
  status: string;
  granted: boolean;
  canAskAgain: boolean;
}

export interface LivePermissionStatus {
  kind: 'permission';
  state: LiveAuthorizationState;
  granted: boolean;
  canAskAgain?: boolean;
  rawStatus?: string;
  issue?: PermissionQueryIssue;
}

export interface ContactsLivePermissionStatus extends LivePermissionStatus {
  accessPrivileges?: 'all' | 'limited' | 'none';
}

export type IosNotificationAuthorization = 'authorized' | 'provisional' | 'ephemeral' | 'denied' | 'not-determined';

export interface NotificationsLivePermissionStatus extends LivePermissionStatus {
  iosAuthorization?: IosNotificationAuthorization;
}

export type BiometricCapabilityState = 'granted' | 'not-enrolled' | 'unavailable';
export type BiometricCapabilityReason = 'ready' | 'no-hardware' | 'not-enrolled' | 'query-failed';
export type BiometricModality = 'fingerprint' | 'facial-recognition' | 'iris' | 'unknown';
export type BiometricSecurityLevel = 'none' | 'device-secret' | 'biometric-weak' | 'biometric-strong' | 'unknown';

export interface BiometricLiveCapabilityStatus {
  kind: 'capability';
  state: BiometricCapabilityState;
  ready: boolean;
  reason: BiometricCapabilityReason;
  hardwareAvailable?: boolean;
  enrolled?: boolean;
  modalities: BiometricModality[];
  rawAuthenticationTypes: number[];
  securityLevel?: BiometricSecurityLevel;
  queryComplete: boolean;
}

export interface LivePermissionSnapshot {
  schemaVersion: 1;
  checkedAt: string;
  contacts: ContactsLivePermissionStatus;
  calendar: LivePermissionStatus;
  notifications: NotificationsLivePermissionStatus;
  biometric: BiometricLiveCapabilityStatus;
}

export interface ContactsPermissionReader {
  getPermissionsAsync(): Promise<
    SdkPermissionResponse & {
      accessPrivileges?: 'all' | 'limited' | 'none';
    }
  >;
}

export interface CalendarPermissionReader {
  getCalendarPermissions(): Promise<SdkPermissionResponse>;
}

export interface NotificationsPermissionReader {
  getPermissionsAsync(): Promise<
    SdkPermissionResponse & {
      ios?: {
        status: number;
      };
    }
  >;
  IosAuthorizationStatus: {
    NOT_DETERMINED: number;
    DENIED: number;
    AUTHORIZED: number;
    PROVISIONAL: number;
    EPHEMERAL: number;
  };
}

export interface LocalAuthenticationCapabilityReader {
  hasHardwareAsync(): Promise<boolean>;
  isEnrolledAsync(): Promise<boolean>;
  supportedAuthenticationTypesAsync(): Promise<number[]>;
  getEnrolledLevelAsync(): Promise<number>;
  AuthenticationType: {
    FINGERPRINT: number;
    FACIAL_RECOGNITION: number;
    IRIS: number;
  };
  SecurityLevel: {
    NONE: number;
    SECRET: number;
    BIOMETRIC_WEAK: number;
    BIOMETRIC_STRONG: number;
  };
}

export interface PermissionStatusAdapters {
  contacts: ContactsPermissionReader;
  calendar: CalendarPermissionReader;
  notifications: NotificationsPermissionReader;
  localAuthentication: LocalAuthenticationCapabilityReader;
  now?: () => Date;
}

const unavailablePermission = (): LivePermissionStatus => ({
  kind: 'permission',
  state: 'unavailable',
  granted: false,
  issue: 'query-failed'
});

const normalizePermission = (response: SdkPermissionResponse): LivePermissionStatus => {
  const rawStatus = String(response.status).toLowerCase();
  if (rawStatus !== 'granted' && rawStatus !== 'denied' && rawStatus !== 'restricted' && rawStatus !== 'undetermined') {
    return {
      kind: 'permission',
      state: 'unavailable',
      granted: false,
      canAskAgain: response.canAskAgain,
      rawStatus,
      issue: 'unsupported-status'
    };
  }

  return {
    kind: 'permission',
    state: rawStatus,
    granted: rawStatus === 'granted',
    canAskAgain: response.canAskAgain,
    rawStatus
  };
};

const readContacts = async (Contacts: ContactsPermissionReader): Promise<ContactsLivePermissionStatus> => {
  try {
    const response = await Contacts.getPermissionsAsync();
    const normalized = normalizePermission(response);
    const state =
      response.accessPrivileges === 'none'
        ? 'denied'
        : normalized.state === 'granted' && response.accessPrivileges === 'limited'
          ? 'limited'
          : normalized.state;
    return {
      ...normalized,
      state,
      granted: state === 'granted' || state === 'limited',
      accessPrivileges: response.accessPrivileges
    };
  } catch {
    return unavailablePermission();
  }
};

const readCalendar = async (Calendar: CalendarPermissionReader): Promise<LivePermissionStatus> => {
  try {
    return normalizePermission(await Calendar.getCalendarPermissions());
  } catch {
    return unavailablePermission();
  }
};

const iosAuthorizationFrom = (
  value: number,
  statuses: NotificationsPermissionReader['IosAuthorizationStatus']
): IosNotificationAuthorization | undefined => {
  if (value === statuses.AUTHORIZED) return 'authorized';
  if (value === statuses.PROVISIONAL) return 'provisional';
  if (value === statuses.EPHEMERAL) return 'ephemeral';
  if (value === statuses.DENIED) return 'denied';
  if (value === statuses.NOT_DETERMINED) return 'not-determined';
  return undefined;
};

const readNotifications = async (
  Notifications: NotificationsPermissionReader
): Promise<NotificationsLivePermissionStatus> => {
  try {
    const response = await Notifications.getPermissionsAsync();
    const normalized = normalizePermission(response);
    const iosAuthorization = response.ios
      ? iosAuthorizationFrom(response.ios.status, Notifications.IosAuthorizationStatus)
      : undefined;
    if (response.ios && !iosAuthorization) {
      return {
        ...normalized,
        state: 'unavailable',
        granted: false,
        rawStatus: `ios:${response.ios.status}`,
        issue: 'unsupported-status'
      };
    }
    const iosState: LiveAuthorizationState | undefined =
      iosAuthorization === 'provisional' || iosAuthorization === 'ephemeral'
        ? 'limited'
        : iosAuthorization === 'authorized'
          ? 'granted'
          : iosAuthorization === 'denied'
            ? 'denied'
            : iosAuthorization === 'not-determined'
              ? 'undetermined'
              : undefined;

    const state = iosState ?? normalized.state;
    return {
      ...normalized,
      state,
      granted: state === 'granted' || state === 'limited',
      iosAuthorization
    };
  } catch {
    return unavailablePermission();
  }
};

const modalityFor = (
  value: number,
  types: LocalAuthenticationCapabilityReader['AuthenticationType']
): BiometricModality => {
  if (value === types.FINGERPRINT) return 'fingerprint';
  if (value === types.FACIAL_RECOGNITION) return 'facial-recognition';
  if (value === types.IRIS) return 'iris';
  return 'unknown';
};

const securityLevelFor = (
  value: number,
  levels: LocalAuthenticationCapabilityReader['SecurityLevel']
): BiometricSecurityLevel => {
  if (value === levels.NONE) return 'none';
  if (value === levels.SECRET) return 'device-secret';
  if (value === levels.BIOMETRIC_WEAK) return 'biometric-weak';
  if (value === levels.BIOMETRIC_STRONG) return 'biometric-strong';
  return 'unknown';
};

const readBiometricCapability = async (
  LocalAuthentication: LocalAuthenticationCapabilityReader
): Promise<BiometricLiveCapabilityStatus> => {
  const [hardware, enrollment, authenticationTypes, enrolledLevel] = await Promise.allSettled([
    LocalAuthentication.hasHardwareAsync(),
    LocalAuthentication.isEnrolledAsync(),
    LocalAuthentication.supportedAuthenticationTypesAsync(),
    LocalAuthentication.getEnrolledLevelAsync()
  ]);

  const hardwareAvailable = hardware.status === 'fulfilled' ? hardware.value : undefined;
  const enrolled = enrollment.status === 'fulfilled' ? enrollment.value : undefined;
  const rawAuthenticationTypes = authenticationTypes.status === 'fulfilled' ? authenticationTypes.value : [];
  const modalities = rawAuthenticationTypes.map(value => modalityFor(value, LocalAuthentication.AuthenticationType));
  const securityLevel =
    enrolledLevel.status === 'fulfilled'
      ? securityLevelFor(enrolledLevel.value, LocalAuthentication.SecurityLevel)
      : undefined;
  const queryComplete =
    hardware.status === 'fulfilled' &&
    enrollment.status === 'fulfilled' &&
    authenticationTypes.status === 'fulfilled' &&
    enrolledLevel.status === 'fulfilled';

  if (hardwareAvailable === false) {
    return {
      kind: 'capability',
      state: 'unavailable',
      ready: false,
      reason: 'no-hardware',
      hardwareAvailable,
      enrolled,
      modalities,
      rawAuthenticationTypes,
      securityLevel,
      queryComplete
    };
  }

  if (hardwareAvailable === true && enrolled === false) {
    return {
      kind: 'capability',
      state: 'not-enrolled',
      ready: false,
      reason: 'not-enrolled',
      hardwareAvailable,
      enrolled,
      modalities,
      rawAuthenticationTypes,
      securityLevel,
      queryComplete
    };
  }

  if (hardwareAvailable === true && enrolled === true) {
    return {
      kind: 'capability',
      state: 'granted',
      ready: true,
      reason: 'ready',
      hardwareAvailable,
      enrolled,
      modalities,
      rawAuthenticationTypes,
      securityLevel,
      queryComplete
    };
  }

  return {
    kind: 'capability',
    state: 'unavailable',
    ready: false,
    reason: 'query-failed',
    hardwareAvailable,
    enrolled,
    modalities,
    rawAuthenticationTypes,
    securityLevel,
    queryComplete
  };
};

/**
 * Reads live OS state only. It never invokes request/authenticate APIs and is
 * therefore safe to call after foreground focus changes and before operations.
 */
export const refreshPermissionSnapshotWithAdapters = async (
  adapters: PermissionStatusAdapters
): Promise<LivePermissionSnapshot> => {
  const [contacts, calendar, notifications, biometric] = await Promise.all([
    readContacts(adapters.contacts),
    readCalendar(adapters.calendar),
    readNotifications(adapters.notifications),
    readBiometricCapability(adapters.localAuthentication)
  ]);

  return {
    schemaVersion: 1,
    checkedAt: (adapters.now ?? (() => new Date()))().toISOString(),
    contacts,
    calendar,
    notifications,
    biometric
  };
};

export const refreshPermissionSnapshot = async (): Promise<LivePermissionSnapshot> => {
  const [contacts, calendar, notifications, localAuthentication] = await Promise.all([
    import('expo-contacts'),
    import('expo-calendar'),
    import('expo-notifications'),
    import('expo-local-authentication')
  ]);

  return refreshPermissionSnapshotWithAdapters({
    contacts,
    calendar,
    notifications,
    localAuthentication
  });
};

export const isCapabilityUsable = (state: LiveAuthorizationState) => state === 'granted' || state === 'limited';
