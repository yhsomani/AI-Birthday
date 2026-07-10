export const RELATEAI_REMINDER_NOTIFICATION_CHANNEL_ID = 'relateai-reminders';

export interface NotificationChannelInitializerAdapter {
  platform: string;
  defaultImportance: number;
  setNotificationChannelAsync(
    channelId: string,
    channel: {
      name: string;
      description: string;
      importance: number;
      enableVibrate: boolean;
      showBadge: boolean;
    }
  ): Promise<unknown>;
}

export interface NotificationChannelInitializationResult {
  channelId: typeof RELATEAI_REMINDER_NOTIFICATION_CHANNEL_ID;
  initialized: boolean;
  supported: boolean;
}

export const initializeAndroidReminderNotificationChannelWithAdapter = async (
  adapter: NotificationChannelInitializerAdapter
): Promise<NotificationChannelInitializationResult> => {
  if (adapter.platform !== 'android') {
    return {
      channelId: RELATEAI_REMINDER_NOTIFICATION_CHANNEL_ID,
      initialized: false,
      supported: false
    };
  }

  await adapter.setNotificationChannelAsync(RELATEAI_REMINDER_NOTIFICATION_CHANNEL_ID, {
    name: 'Relationship reminders',
    description: 'Private reminders to open RelateAI and review relationship tasks.',
    importance: adapter.defaultImportance,
    enableVibrate: true,
    showBadge: true
  });
  return {
    channelId: RELATEAI_REMINDER_NOTIFICATION_CHANNEL_ID,
    initialized: true,
    supported: true
  };
};

export const initializeAndroidReminderNotificationChannel =
  async (): Promise<NotificationChannelInitializationResult> => {
    const [{ Platform }, Notifications] = await Promise.all([import('react-native'), import('expo-notifications')]);
    return initializeAndroidReminderNotificationChannelWithAdapter({
      platform: Platform.OS,
      defaultImportance: Notifications.AndroidImportance.DEFAULT,
      setNotificationChannelAsync: Notifications.setNotificationChannelAsync
    });
  };
