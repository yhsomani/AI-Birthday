import type { PermissionPromptOutcome, SystemAuthorization, SystemPermissionCapability } from '../domain/types';
import {
  initializeAndroidReminderNotificationChannel,
  type NotificationChannelInitializationResult
} from './notificationChannel';

export type RequestableSystemPermissionCapability = Extract<
  SystemPermissionCapability,
  'Contacts' | 'Calendar' | 'Notifications'
>;

interface PermissionResponse {
  status: string;
  granted: boolean;
  canAskAgain: boolean;
}

export interface PermissionRequestAdapters {
  contacts: {
    requestPermissionsAsync(): Promise<PermissionResponse & { accessPrivileges?: 'all' | 'limited' | 'none' }>;
  };
  calendar: {
    requestCalendarPermissions(): Promise<PermissionResponse>;
  };
  notifications: {
    requestPermissionsAsync(): Promise<PermissionResponse & { ios?: { status: number } }>;
    IosAuthorizationStatus: {
      NOT_DETERMINED: number;
      DENIED: number;
      AUTHORIZED: number;
      PROVISIONAL: number;
      EPHEMERAL: number;
    };
  };
  initializeNotificationChannel(): Promise<NotificationChannelInitializationResult>;
  now?: () => Date;
}

export interface PermissionRequestResult {
  capability: RequestableSystemPermissionCapability;
  outcome: PermissionPromptOutcome;
  systemAuthorization: Extract<SystemAuthorization, 'granted' | 'limited' | 'denied' | 'restricted' | 'undetermined'>;
  promptedAt: string;
  canAskAgain: boolean;
  platformStatus: string;
  notificationChannel?: NotificationChannelInitializationResult;
}

const outcomeFromStatus = (status: string, granted: boolean): PermissionPromptOutcome => {
  const normalized = status.toLowerCase();
  if (normalized === 'granted') return 'granted';
  if (normalized === 'limited') return 'limited';
  if (normalized === 'denied') return 'denied';
  if (normalized === 'restricted') return 'restricted';
  if (normalized === 'undetermined') return 'undetermined';
  if (granted) return 'granted';
  throw new Error('The operating system returned an unsupported permission status.');
};

const notificationOutcome = (
  response: Awaited<ReturnType<PermissionRequestAdapters['notifications']['requestPermissionsAsync']>>,
  statuses: PermissionRequestAdapters['notifications']['IosAuthorizationStatus']
): PermissionPromptOutcome => {
  const iosStatus = response.ios?.status;
  if (iosStatus === statuses.AUTHORIZED) return 'granted';
  if (iosStatus === statuses.PROVISIONAL || iosStatus === statuses.EPHEMERAL) return 'limited';
  if (iosStatus === statuses.DENIED) return 'denied';
  if (iosStatus === statuses.NOT_DETERMINED) return 'undetermined';
  if (response.ios) {
    throw new Error('The operating system returned an unsupported notification permission status.');
  }
  return outcomeFromStatus(response.status, response.granted);
};

export const requestSystemPermissionWithAdapters = async (
  capability: RequestableSystemPermissionCapability,
  adapters: PermissionRequestAdapters
): Promise<PermissionRequestResult> => {
  let response: PermissionResponse;
  let outcome: PermissionPromptOutcome;
  let notificationChannel: NotificationChannelInitializationResult | undefined;

  if (capability === 'Contacts') {
    const contacts = await adapters.contacts.requestPermissionsAsync();
    response = contacts;
    outcome =
      contacts.accessPrivileges === 'limited'
        ? 'limited'
        : contacts.accessPrivileges === 'none'
          ? 'denied'
          : outcomeFromStatus(contacts.status, contacts.granted);
  } else if (capability === 'Calendar') {
    response = await adapters.calendar.requestCalendarPermissions();
    outcome = outcomeFromStatus(response.status, response.granted);
  } else {
    notificationChannel = await adapters.initializeNotificationChannel();
    const notifications = await adapters.notifications.requestPermissionsAsync();
    response = notifications;
    outcome = notificationOutcome(notifications, adapters.notifications.IosAuthorizationStatus);
  }

  return {
    capability,
    outcome,
    systemAuthorization: outcome,
    promptedAt: (adapters.now ?? (() => new Date()))().toISOString(),
    canAskAgain: response.canAskAgain,
    platformStatus: response.status,
    ...(notificationChannel ? { notificationChannel } : {})
  };
};

export const requestSystemPermission = async (
  capability: RequestableSystemPermissionCapability
): Promise<PermissionRequestResult> => {
  const [contacts, calendar, notifications] = await Promise.all([
    import('expo-contacts'),
    import('expo-calendar'),
    import('expo-notifications')
  ]);
  return requestSystemPermissionWithAdapters(capability, {
    contacts,
    calendar,
    notifications,
    initializeNotificationChannel: initializeAndroidReminderNotificationChannel
  });
};
