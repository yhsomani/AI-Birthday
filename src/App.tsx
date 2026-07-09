import React, { useEffect, useMemo, useReducer, useRef, useState } from 'react';
import * as Notifications from 'expo-notifications';
import {
  Alert,
  Linking,
  SafeAreaView,
  ScrollView,
  Share,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View
} from 'react-native';
import { buildAiDraftRequest } from './domain/aiDrafting';
import { validateBackupPassphrase } from './domain/backup';
import { buildHandoffTarget } from './domain/channelHandoff';
import { buildChatHistory, type ChatHistoryChannelFilter } from './domain/chatHistory';
import { resolveBiometricLock } from './domain/biometricLock';
import { buildContactEnrichmentPlan } from './domain/contactEnrichment';
import { buildContactTimeline, contactTimelineFilters, type ContactTimelineFilter } from './domain/contactTimeline';
import { parseRelateDeepLink, resolveDeepLinkDestination } from './domain/deepLinks';
import { buildEmailDeliveryRequest } from './domain/emailDelivery';
import {
  buildEventMonthView,
  filterRelationshipEvents,
  shiftMonth,
  type EventTimeFilter,
  type EventTypeFilter
} from './domain/eventBrowser';
import {
  activityDateFilters,
  activitySeverityFilters,
  activityTypeFilters,
  buildActivityHistory,
  type ActivityDateFilter,
  type ActivitySeverityFilter,
  type ActivityTypeFilter
} from './domain/activityHistory';
import { manualEventTypes, validateManualEventInput } from './domain/events';
import { readNotificationRouteUrl } from './domain/notificationRoutes';
import { findMessageTemplates, renderMessageTemplate } from './domain/messageTemplates';
import { buildSetupDoctorReport, type SetupDoctorCheck } from './domain/setupDoctor';
import { buildSetupWizardPlan, setupGoals, type SetupGoal, type SetupStep } from './domain/setupWizard';
import { eligibleSentStyleMessages } from './domain/styleCoach';
import type {
  ComposerReason,
  Contact,
  EventType,
  MemoryCategory,
  MessageChannel,
  MessageDraft,
  RelationshipEvent,
  Screen,
  SupportedLocale,
  Tone
} from './domain/types';
import {
  formatCurrencyForLocale,
  formatDateForLocale,
  localeMetadata,
  supportedLocales,
  t,
  type TranslationKey
} from './i18n/i18n';
import { exportEventsToDeviceCalendar, importEventsFromDeviceCalendar } from './native/calendarBridge';
import { authenticateWithBiometrics, readBiometricCapability } from './native/biometricAuth';
import { importDeviceContacts, sampleImportRecords } from './native/contactImporter';
import { readAiProviderConfig, requestAiDraft } from './native/aiProviderClient';
import { readEmailSenderConfig, sendEmailMessage } from './native/emailSenderClient';
import {
  exportEncryptedBackupFile,
  pickEncryptedBackupFile,
  restoreEncryptedBackupFile,
  type BackupFilePickResult
} from './native/backupFiles';
import { scheduleReminderPlans } from './native/reminderScheduler';
import { secureStateStore } from './native/secureStateStore';
import { createInitialState, relateReducer, type RelateAction } from './state/relateReducer';
import { loadStateWithRecovery, saveState } from './state/persistence';
import { colors, spacing } from './ui/theme';

const primaryTabs: Array<{ key: Screen; labelKey: TranslationKey }> = [
  { key: 'home', labelKey: 'nav.home' },
  { key: 'events', labelKey: 'nav.events' },
  { key: 'messages', labelKey: 'nav.messages' },
  { key: 'contacts', labelKey: 'nav.contacts' },
  { key: 'more', labelKey: 'nav.more' }
];

const tones: Tone[] = ['Warm', 'Respectful', 'Playful', 'Concise', 'Formal', 'Hinglish', 'No emoji'];
const channels: MessageChannel[] = ['SMS', 'WhatsApp', 'Email', 'Manual'];
const composerReasons: ComposerReason[] = [
  'Birthday',
  'Check-in',
  'Thanks',
  'Congratulations',
  'Apology',
  'Follow-up',
  'Custom'
];

const memoryCategories: MemoryCategory[] = ['General', 'Private', 'Preference', 'Event', 'Gift', 'Milestone'];
const eventTypeFilters: EventTypeFilter[] = ['All', ...manualEventTypes];
const eventTimeFilters: EventTimeFilter[] = ['Upcoming', 'This month', 'All', 'Past'];
const chatHistoryChannels: ChatHistoryChannelFilter[] = ['All', ...channels];

const daysBetween = (iso?: string) => {
  if (!iso) {
    return Number.POSITIVE_INFINITY;
  }
  const start = new Date(iso).getTime();
  const now = Date.now();
  return Math.round((now - start) / (1000 * 60 * 60 * 24));
};

const getContact = (contacts: Contact[], contactId?: string) =>
  contacts.find(contact => contact.id === contactId);

const getEvent = (events: RelationshipEvent[], eventId?: string) =>
  events.find(event => event.id === eventId);

const Button = ({
  label,
  onPress,
  tone = 'primary',
  disabled = false,
  accessibilityLabel
}: {
  label: string;
  onPress: () => void;
  tone?: 'primary' | 'secondary' | 'danger' | 'ghost';
  disabled?: boolean;
  accessibilityLabel?: string;
}) => (
  <TouchableOpacity
    accessibilityLabel={accessibilityLabel ?? label}
    accessibilityRole="button"
    accessibilityState={{ disabled }}
    disabled={disabled}
    onPress={onPress}
    style={[
      styles.button,
      tone === 'secondary' && styles.buttonSecondary,
      tone === 'danger' && styles.buttonDanger,
      tone === 'ghost' && styles.buttonGhost,
      disabled && styles.buttonDisabled
    ]}
  >
    <Text
      style={[
        styles.buttonText,
        tone === 'secondary' && styles.buttonSecondaryText,
        tone === 'ghost' && styles.buttonGhostText
      ]}
    >
      {label}
    </Text>
  </TouchableOpacity>
);

const Card = ({ children }: { children: React.ReactNode }) => <View style={styles.card}>{children}</View>;

const Pill = ({
  label,
  selected,
  onPress,
  accessibilityLabel
}: {
  label: string;
  selected?: boolean;
  onPress?: () => void;
  accessibilityLabel?: string;
}) => (
  <TouchableOpacity
    accessibilityLabel={accessibilityLabel ?? label}
    accessibilityRole={onPress ? 'button' : 'text'}
    accessibilityState={onPress ? { selected } : undefined}
    onPress={onPress}
    disabled={!onPress}
    style={[styles.pill, selected && styles.pillSelected]}
  >
    <Text style={[styles.pillText, selected && styles.pillTextSelected]}>{label}</Text>
  </TouchableOpacity>
);

const SectionTitle = ({ title, detail }: { title: string; detail?: string }) => (
  <View style={styles.sectionHeader}>
    <Text style={styles.sectionTitle}>{title}</Text>
    {detail ? <Text style={styles.sectionDetail}>{detail}</Text> : null}
  </View>
);

const App = () => {
  const [state, dispatch] = useReducer(relateReducer, undefined, createInitialState);
  const [hydrated, setHydrated] = useState(false);
  const [sessionUnlocked, setSessionUnlocked] = useState(false);
  const [biometricCapability, setBiometricCapability] = useState({
    hardwareAvailable: false,
    enrolled: false
  });
  const lastSavedSnapshot = useRef('');
  const selectedContact = getContact(state.contacts, state.selectedContactId);
  const selectedMessage = state.messages.find(message => message.id === state.selectedMessageId);
  const locale = state.settings.locale;
  const stateRef = useRef(state);

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  useEffect(() => {
    let active = true;
    loadStateWithRecovery(secureStateStore)
      .then(result => {
        if (!active) {
          return;
        }
        if (result.status === 'loaded') {
          dispatch({ type: 'hydrate', state: result.state });
        }
        if (result.status === 'recovered') {
          dispatch({
            type: 'persistenceError',
            message: `Saved data was recovered after a storage issue: ${result.reason}`
          });
        }
      })
      .catch(error => {
        if (active) {
          dispatch({
            type: 'persistenceError',
            message: error instanceof Error ? error.message : 'Saved state could not be loaded.'
          });
        }
      })
      .finally(() => {
        if (active) {
          setHydrated(true);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;
    readBiometricCapability()
      .then(capability => {
        if (active) {
          setBiometricCapability(capability);
        }
      })
      .catch(() => {
        if (active) {
          setBiometricCapability({
            hardwareAvailable: false,
            enrolled: false
          });
        }
      });
    return () => {
      active = false;
    };
  }, [state.settings.biometricLockEnabled]);

  useEffect(() => {
    if (!hydrated) {
      return;
    }
    const stateForPersistence = {
      ...state,
      persistence: {
        status: 'Ready' as const
      }
    };
    const snapshot = JSON.stringify(stateForPersistence);
    if (snapshot === lastSavedSnapshot.current) {
      return;
    }
    lastSavedSnapshot.current = snapshot;
    saveState(secureStateStore, stateForPersistence)
      .then(() => dispatch({ type: 'persistenceSaved', savedAt: new Date().toISOString() }))
      .catch(error =>
        dispatch({
          type: 'persistenceError',
          message: error instanceof Error ? error.message : 'State could not be saved.'
        })
      );
  }, [hydrated, state]);

  useEffect(() => {
    if (!hydrated) {
      return;
    }

    let active = true;
    const openDeepLink = (url?: string | null) => {
      if (!url) {
        return;
      }
      const parsed = parseRelateDeepLink(url);
      const resolution = parsed.ok
        ? resolveDeepLinkDestination(stateRef.current, parsed.destination)
        : {
            ok: false as const,
            destination: parsed.fallback,
            message: parsed.message
          };

      dispatch({
        type: 'navigate',
        screen: resolution.destination.screen,
        contactId: resolution.destination.contactId,
        messageId: resolution.destination.messageId
      });

      if (resolution.message) {
        Alert.alert('Link opened', resolution.message);
      }
    };

    const subscription = Linking.addEventListener('url', event => openDeepLink(event.url));
    const notificationSubscription = Notifications.addNotificationResponseReceivedListener(response => {
      openDeepLink(readNotificationRouteUrl(response.notification.request.content.data));
    });

    try {
      const response = Notifications.getLastNotificationResponse();
      openDeepLink(readNotificationRouteUrl(response?.notification.request.content.data));
      Notifications.clearLastNotificationResponse();
    } catch {
      // Notification response history is not available on every platform.
    }

    Linking.getInitialURL()
      .then(url => {
        if (active) {
          openDeepLink(url);
        }
      })
      .catch(() => undefined);

    return () => {
      active = false;
      subscription.remove();
      notificationSubscription.remove();
    };
  }, [hydrated]);

  const handleManualHandoff = async (message: MessageDraft) => {
    const contact = getContact(state.contacts, message.contactId);
    const target = buildHandoffTarget(contact, message);
    try {
      if (target.url && (await Linking.canOpenURL(target.url))) {
        await Linking.openURL(target.url);
        dispatch({ type: 'manualHandoff', messageId: message.id });
        return;
      }
      const result = await Share.share({
        title: `Message for ${contact?.name ?? 'contact'}`,
        message: target.reason ? `${message.body}\n\n${target.reason}` : message.body
      });
      if (result.action !== Share.dismissedAction) {
        dispatch({ type: 'manualHandoff', messageId: message.id });
      }
    } catch {
      Alert.alert('Manual handoff failed', 'Copy the message from preview and try again.');
    }
  };

  const handleSendEmail = async (message: MessageDraft) => {
    const request = buildEmailDeliveryRequest(state, message.id);
    if (!request.ok) {
      dispatch({ type: 'emailProviderFailure', error: request.error });
      Alert.alert('Email not sent', request.error.message);
      return;
    }

    const result = await sendEmailMessage(request.request);
    if (result.ok) {
      dispatch({ type: 'emailSent', messageId: message.id });
      Alert.alert('Email sent', 'The approved email was sent by the configured provider.');
      return;
    }

    dispatch({ type: 'emailProviderFailure', error: result.error });
    Alert.alert('Email not sent', `${result.error.message} You can still use manual email handoff.`);
  };

  const handleGenerateMessage = async (contactId: string, eventId: string | undefined, reason: ComposerReason) => {
    const request = buildAiDraftRequest(state, contactId, eventId, reason);
    if (!request.ok) {
      dispatch({
        type: 'generateMessage',
        contactId,
        eventId,
        reason,
        fallbackReason: request.error.message
      });
      return;
    }

    const result = await requestAiDraft(request.request);
    if (result.ok) {
      dispatch({
        type: 'createAiDraft',
        contactId,
        eventId,
        reason,
        variants: result.variants,
        privacySummary: request.privacySummary
      });
      return;
    }

    dispatch({ type: 'aiProviderFailure', error: result.error, privacySummary: request.privacySummary });
    dispatch({
      type: 'generateMessage',
      contactId,
      eventId,
      reason,
      fallbackReason: result.error.message
    });
  };

  const handleTestAiProvider = async () => {
    const contact = state.contacts[0];
    if (!contact) {
      Alert.alert('AI provider test failed', 'Add a contact before testing AI drafting.');
      return;
    }

    const request = buildAiDraftRequest(state, contact.id, undefined, 'Check-in');
    if (!request.ok) {
      dispatch({ type: 'aiProviderFailure', error: request.error });
      Alert.alert('AI provider test failed', request.error.message);
      return;
    }

    const result = await requestAiDraft(request.request);
    if (result.ok) {
      dispatch({ type: 'aiProviderReady', privacySummary: request.privacySummary });
      Alert.alert('AI provider ready', request.privacySummary);
    } else {
      dispatch({ type: 'aiProviderFailure', error: result.error, privacySummary: request.privacySummary });
      Alert.alert('AI provider test failed', result.error.message);
    }
  };

  const handleImportSampleContacts = () => {
    dispatch({ type: 'importContacts', records: sampleImportRecords() });
  };

  const handleExportBackup = async (passphrase: string) => {
    try {
      const result = await exportEncryptedBackupFile(state, passphrase);
      dispatch({ type: 'createBackup' });
      Alert.alert(
        'Backup exported',
        `${result.preview.recordCount} record(s) encrypted. ${result.shared ? 'Choose a destination from the share sheet.' : `Saved to ${result.uri}`}`
      );
    } catch (error) {
      Alert.alert(
        'Backup export failed',
        error instanceof Error ? error.message : 'Encrypted backup could not be created.'
      );
    }
  };

  const handlePickBackup = async () => {
    try {
      return await pickEncryptedBackupFile();
    } catch (error) {
      Alert.alert(
        'Backup import failed',
        error instanceof Error ? error.message : 'Selected backup could not be read.'
      );
      return undefined;
    }
  };

  const handleRestoreBackup = async (raw: string, passphrase: string, recordCount: number) => {
    try {
      const restoredState = await restoreEncryptedBackupFile(raw, passphrase);
      dispatch({ type: 'restoreBackup', restoredState, recordCount });
      Alert.alert('Backup restored', `${recordCount} record(s) restored. Review your contacts, events, and messages.`);
    } catch (error) {
      Alert.alert(
        'Backup restore failed',
        error instanceof Error ? error.message : 'Backup was not restored. Existing data is unchanged.'
      );
    }
  };

  const handleUnlock = async () => {
    try {
      if (await authenticateWithBiometrics()) {
        setSessionUnlocked(true);
      }
    } catch {
      Alert.alert('Unlock failed', 'Biometric authentication could not be completed.');
    }
  };

  const handleImportDeviceContacts = async () => {
    try {
      const records = await importDeviceContacts();
      dispatch({ type: 'importContacts', records });
    } catch (error) {
      Alert.alert(
        'Contact import failed',
        error instanceof Error ? error.message : 'Contacts could not be imported right now.'
      );
    }
  };

  const handleScheduleReminders = async () => {
    const plannedState = relateReducer(state, { type: 'planReminders' });
    dispatch({ type: 'planReminders' });
    try {
      const result = await scheduleReminderPlans(plannedState.reminderPlans);
      Alert.alert('Reminders scheduled', `${result.scheduled} scheduled, ${result.skipped} skipped.`);
    } catch (error) {
      Alert.alert(
        'Reminder scheduling failed',
        error instanceof Error ? error.message : 'Notification reminders could not be scheduled.'
      );
    }
  };

  const handleExportCalendar = async () => {
    try {
      const count = await exportEventsToDeviceCalendar(state);
      dispatch({ type: 'calendarExported', count });
      Alert.alert('Calendar export complete', `${count} event(s) exported to the RelateAI calendar.`);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Calendar export failed.';
      dispatch({ type: 'calendarError', message });
      Alert.alert('Calendar export failed', message);
    }
  };

  const handleImportCalendar = async () => {
    try {
      const candidates = await importEventsFromDeviceCalendar();
      dispatch({ type: 'calendarImported', candidates });
      Alert.alert('Calendar import complete', `${candidates.length} candidate event(s) reviewed for import.`);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Calendar import failed.';
      dispatch({ type: 'calendarError', message });
      Alert.alert('Calendar import failed', message);
    }
  };

  const routeBack = () =>
    dispatch({
      type: 'navigate',
      screen:
        state.activeScreen === 'manualComposer'
          ? 'contacts'
          : state.activeScreen === 'eventForm'
            ? 'events'
            : state.activeScreen === 'chatHistory'
              ? selectedContact
                ? 'contactDetail'
                : 'contacts'
              : 'home'
    });

  const lockDecision = resolveBiometricLock({
    enabled: state.settings.biometricLockEnabled,
    hardwareAvailable: biometricCapability.hardwareAvailable,
    enrolled: biometricCapability.enrolled,
    sessionUnlocked
  });

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle="dark-content" />
      <View style={styles.appShell}>
        <View style={styles.header}>
          <View>
            <Text style={styles.appName}>RelateAI</Text>
            <Text style={styles.tagline}>{t(locale, 'app.tagline')}</Text>
          </View>
          {state.activeScreen === 'contactDetail' ||
          state.activeScreen === 'chatHistory' ||
          state.activeScreen === 'wishPreview' ||
          state.activeScreen === 'manualComposer' ||
          state.activeScreen === 'eventForm' ? (
            <Button label="Back" tone="ghost" onPress={routeBack} />
          ) : null}
        </View>

        {lockDecision.state !== 'unlocked' ? (
          <LockScreen
            decision={lockDecision}
            onUnlock={handleUnlock}
            onDisable={() => {
              dispatch({ type: 'toggleSetting', key: 'biometricLockEnabled' });
              setSessionUnlocked(false);
            }}
          />
        ) : state.activeScreen === 'contactDetail' && selectedContact ? (
          <ContactDetail
            contact={selectedContact}
            state={state}
            dispatch={dispatch}
            onManualHandoff={handleManualHandoff}
            onSendEmail={handleSendEmail}
            onGenerateMessage={handleGenerateMessage}
            onTestAiProvider={handleTestAiProvider}
          />
        ) : state.activeScreen === 'chatHistory' ? (
          <ChatHistory contact={selectedContact} state={state} dispatch={dispatch} />
        ) : state.activeScreen === 'wishPreview' && selectedMessage ? (
          <WishPreview
            message={selectedMessage}
            state={state}
            dispatch={dispatch}
            onManualHandoff={handleManualHandoff}
            onSendEmail={handleSendEmail}
            onGenerateMessage={handleGenerateMessage}
            onTestAiProvider={handleTestAiProvider}
          />
        ) : state.activeScreen === 'eventForm' ? (
          <EventForm state={state} dispatch={dispatch} />
        ) : state.activeScreen === 'manualComposer' ? (
          <ManualComposer
            contact={selectedContact ?? state.contacts[0]}
            state={state}
            dispatch={dispatch}
            onGenerateMessage={handleGenerateMessage}
          />
        ) : (
          <>
            <ScrollView contentContainerStyle={styles.content}>
              {state.activeScreen === 'home' ? (
                <HomeScreen
                  state={state}
                  dispatch={dispatch}
                  onManualHandoff={handleManualHandoff}
                  onSendEmail={handleSendEmail}
                  onImportDeviceContacts={handleImportDeviceContacts}
                  onImportSampleContacts={handleImportSampleContacts}
                  onScheduleReminders={handleScheduleReminders}
                  onExportCalendar={handleExportCalendar}
                  onImportCalendar={handleImportCalendar}
                  onGenerateMessage={handleGenerateMessage}
                  onTestAiProvider={handleTestAiProvider}
                  onExportBackup={handleExportBackup}
                  onPickBackup={handlePickBackup}
                  onRestoreBackup={handleRestoreBackup}
                />
              ) : null}
              {state.activeScreen === 'events' ? (
                <EventsScreen
                  state={state}
                  dispatch={dispatch}
                  onManualHandoff={handleManualHandoff}
                  onSendEmail={handleSendEmail}
                  onImportDeviceContacts={handleImportDeviceContacts}
                  onImportSampleContacts={handleImportSampleContacts}
                  onScheduleReminders={handleScheduleReminders}
                  onExportCalendar={handleExportCalendar}
                  onImportCalendar={handleImportCalendar}
                  onGenerateMessage={handleGenerateMessage}
                  onTestAiProvider={handleTestAiProvider}
                  onExportBackup={handleExportBackup}
                  onPickBackup={handlePickBackup}
                  onRestoreBackup={handleRestoreBackup}
                />
              ) : null}
              {state.activeScreen === 'messages' ? (
                <MessagesScreen
                  state={state}
                  dispatch={dispatch}
                  onManualHandoff={handleManualHandoff}
                  onSendEmail={handleSendEmail}
                  onImportDeviceContacts={handleImportDeviceContacts}
                  onImportSampleContacts={handleImportSampleContacts}
                  onScheduleReminders={handleScheduleReminders}
                  onExportCalendar={handleExportCalendar}
                  onImportCalendar={handleImportCalendar}
                  onGenerateMessage={handleGenerateMessage}
                  onTestAiProvider={handleTestAiProvider}
                  onExportBackup={handleExportBackup}
                  onPickBackup={handlePickBackup}
                  onRestoreBackup={handleRestoreBackup}
                />
              ) : null}
              {state.activeScreen === 'contacts' ? (
                <ContactsScreen
                  state={state}
                  dispatch={dispatch}
                  onManualHandoff={handleManualHandoff}
                  onSendEmail={handleSendEmail}
                  onImportDeviceContacts={handleImportDeviceContacts}
                  onImportSampleContacts={handleImportSampleContacts}
                  onScheduleReminders={handleScheduleReminders}
                  onExportCalendar={handleExportCalendar}
                  onImportCalendar={handleImportCalendar}
                  onGenerateMessage={handleGenerateMessage}
                  onTestAiProvider={handleTestAiProvider}
                  onExportBackup={handleExportBackup}
                  onPickBackup={handlePickBackup}
                  onRestoreBackup={handleRestoreBackup}
                />
              ) : null}
              {state.activeScreen === 'more' ? (
                <MoreScreen
                  state={state}
                  dispatch={dispatch}
                  onManualHandoff={handleManualHandoff}
                  onSendEmail={handleSendEmail}
                  onImportDeviceContacts={handleImportDeviceContacts}
                  onImportSampleContacts={handleImportSampleContacts}
                  onScheduleReminders={handleScheduleReminders}
                  onExportCalendar={handleExportCalendar}
                  onImportCalendar={handleImportCalendar}
                  onGenerateMessage={handleGenerateMessage}
                  onTestAiProvider={handleTestAiProvider}
                  onExportBackup={handleExportBackup}
                  onPickBackup={handlePickBackup}
                  onRestoreBackup={handleRestoreBackup}
                />
              ) : null}
            </ScrollView>
            <View style={styles.tabBar}>
              {primaryTabs.map(tab => (
                <TouchableOpacity
                  accessibilityLabel={t(locale, tab.labelKey)}
                  accessibilityRole="tab"
                  accessibilityState={{ selected: state.activeScreen === tab.key }}
                  key={tab.key}
                  onPress={() => dispatch({ type: 'navigate', screen: tab.key })}
                  style={[styles.tab, state.activeScreen === tab.key && styles.tabActive]}
                >
                  <Text style={[styles.tabText, state.activeScreen === tab.key && styles.tabTextActive]}>
                    {t(locale, tab.labelKey)}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          </>
        )}
      </View>
    </SafeAreaView>
  );
};

const LockScreen = ({
  decision,
  onUnlock,
  onDisable
}: {
  decision: ReturnType<typeof resolveBiometricLock>;
  onUnlock: () => void;
  onDisable: () => void;
}) => (
  <View style={styles.lockScreen}>
    <Card>
      <Text style={styles.cardTitle}>RelateAI is locked</Text>
      {decision.state === 'locked' ? (
        <>
          <Text style={styles.bodyText}>
            Biometric lock is enabled to protect contacts, private memories, drafts, and message history.
          </Text>
          <Button label="Unlock" onPress={onUnlock} />
        </>
      ) : (
        <>
          <Text style={styles.warningText}>
            Biometric lock is enabled, but this device cannot authenticate right now.
          </Text>
          <Text style={styles.bodyText}>
            Reason: {decision.reason === 'no-hardware' ? 'no biometric hardware available' : 'no biometric enrollment found'}.
          </Text>
          <Button label="Disable biometric lock" tone="secondary" onPress={onDisable} />
        </>
      )}
    </Card>
  </View>
);

const HomeScreen = ({ state, dispatch, onManualHandoff, onSendEmail, onGenerateMessage }: ScreenProps) => {
  const pending = state.messages.filter(message => message.status === 'Needs review');
  const upcoming = [...state.events].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
  const checkIns = state.contacts.filter(contact => daysBetween(contact.lastContactedAt) >= contact.checkInCadenceDays);
  const nextAction = pending[0]
    ? {
        title: 'Review pending wish',
        detail: `${getContact(state.contacts, pending[0].contactId)?.name ?? 'A contact'} has a draft ready.`,
        action: () => dispatch({ type: 'navigate', screen: 'wishPreview', messageId: pending[0].id })
      }
    : checkIns[0]
      ? {
          title: `Check in with ${checkIns[0].name}`,
          detail: `Quiet for ${daysBetween(checkIns[0].lastContactedAt)} days. Write a lightweight message.`,
          action: () => dispatch({ type: 'navigate', screen: 'manualComposer', contactId: checkIns[0].id })
        }
      : {
          title: 'Prepare next event',
          detail: 'Review upcoming dates and complete the event checklist.',
          action: () => dispatch({ type: 'navigate', screen: 'events' })
        };

  return (
    <View>
      <SectionTitle title="Today" detail="Your relationship command center" />
      <View style={styles.statGrid}>
        <Metric label="Contacts" value={String(state.contacts.length)} />
        <Metric label="Upcoming" value={String(upcoming.length)} />
        <Metric label="Review" value={String(pending.length)} />
        <Metric label="Backups" value={String(state.backups.length)} />
      </View>

      <Card>
        <Text style={styles.cardTitle}>{nextAction.title}</Text>
        <Text style={styles.bodyText}>{nextAction.detail}</Text>
        <Button label="Start" onPress={nextAction.action} />
      </Card>

      <SectionTitle title="Upcoming events" detail="Complete the checklist before the date." />
      {upcoming.slice(0, 3).map(event => (
        <EventCard
          key={event.id}
          event={event}
          state={state}
          dispatch={dispatch}
          onManualHandoff={onManualHandoff}
          onSendEmail={onSendEmail}
          onGenerateMessage={onGenerateMessage}
        />
      ))}

      <SectionTitle title="Useful additions now active" detail="Roadmap items represented in this RN build." />
      <Card>
        <View style={styles.wrapRow}>
          <Pill label="Manual composer" selected />
          <Pill label="Check-in reminders" selected />
          <Pill label="Event checklist" selected />
          <Pill label="Guided enrichment" selected />
          <Pill label="Manual send handoff" selected />
          <Pill label="AI context preview" selected />
        </View>
      </Card>
    </View>
  );
};

const EventsScreen = ({ state, dispatch, onManualHandoff, onSendEmail, onGenerateMessage }: ScreenProps) => {
  const [viewMode, setViewMode] = useState<'List' | 'Month'>('List');
  const [typeFilter, setTypeFilter] = useState<EventTypeFilter>('All');
  const [timeFilter, setTimeFilter] = useState<EventTimeFilter>('Upcoming');
  const [monthIso, setMonthIso] = useState(new Date().toISOString());
  const filteredEvents = useMemo(
    () =>
      filterRelationshipEvents(state.events, {
        type: typeFilter,
        time: timeFilter,
        nowIso: new Date().toISOString(),
        monthIso
      }),
    [state.events, typeFilter, timeFilter, monthIso]
  );
  const monthView = useMemo(() => buildEventMonthView(filteredEvents, monthIso), [filteredEvents, monthIso]);

  return (
    <View>
      <SectionTitle title="Events" detail="Birthdays, anniversaries, custom reminders, and check-ins." />
      <Card>
        <Text style={styles.bodyText}>
          Add a relationship event manually, then review conflicts before reminders or messages are created.
        </Text>
        <Button label="Add event" onPress={() => dispatch({ type: 'navigate', screen: 'eventForm' })} />
      </Card>

      <Card>
        <View style={styles.wrapRow}>
          <Pill label="List" selected={viewMode === 'List'} onPress={() => setViewMode('List')} />
          <Pill label="Month" selected={viewMode === 'Month'} onPress={() => setViewMode('Month')} />
        </View>
        <Text style={styles.smallText}>Type</Text>
        <View style={styles.wrapRow}>
          {eventTypeFilters.map(item => (
            <Pill key={item} label={item} selected={typeFilter === item} onPress={() => setTypeFilter(item)} />
          ))}
        </View>
        <Text style={styles.smallText}>Time range</Text>
        <View style={styles.wrapRow}>
          {eventTimeFilters.map(item => (
            <Pill key={item} label={item} selected={timeFilter === item} onPress={() => setTimeFilter(item)} />
          ))}
        </View>
        {viewMode === 'Month' ? (
          <View style={styles.monthControls}>
            <Button label="Previous" tone="secondary" onPress={() => setMonthIso(value => shiftMonth(value, -1))} />
            <Text style={styles.cardTitle}>{monthView.label}</Text>
            <Button label="Next" tone="secondary" onPress={() => setMonthIso(value => shiftMonth(value, 1))} />
          </View>
        ) : null}
      </Card>

      {viewMode === 'Month' ? (
        <EventMonthGrid monthView={monthView} state={state} dispatch={dispatch} />
      ) : filteredEvents.length > 0 ? (
        filteredEvents.map(event => (
          <EventCard
            key={event.id}
            event={event}
            state={state}
            dispatch={dispatch}
            onManualHandoff={onManualHandoff}
            onSendEmail={onSendEmail}
            onGenerateMessage={onGenerateMessage}
          />
        ))
      ) : (
        <Card>
          <Text style={styles.bodyText}>No events match these filters.</Text>
        </Card>
      )}
    </View>
  );
};

const EventMonthGrid = ({
  monthView,
  state,
  dispatch
}: {
  monthView: ReturnType<typeof buildEventMonthView>;
  state: ScreenProps['state'];
  dispatch: ScreenProps['dispatch'];
}) => (
  <Card>
    <View style={styles.monthGrid}>
      {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map(day => (
        <Text key={day} style={styles.monthWeekday}>
          {day}
        </Text>
      ))}
      {monthView.days.map(day => {
        const firstEvent = day.events[0];
        const contact = firstEvent ? getContact(state.contacts, firstEvent.contactId) : undefined;
        return (
          <TouchableOpacity
            accessibilityRole={firstEvent ? 'button' : 'text'}
            accessibilityLabel={
              firstEvent
                ? `${day.dateKey}, ${day.events.length} event${day.events.length === 1 ? '' : 's'}`
                : day.dateKey
            }
            disabled={!firstEvent}
            key={day.dateKey}
            onPress={() =>
              firstEvent
                ? dispatch({ type: 'navigate', screen: 'contactDetail', contactId: firstEvent.contactId })
                : undefined
            }
            style={[styles.monthCell, !day.inMonth && styles.monthCellMuted, firstEvent && styles.monthCellActive]}
          >
            <Text style={[styles.monthDay, !day.inMonth && styles.monthTextMuted]}>{day.dayOfMonth}</Text>
            {firstEvent ? (
              <Text numberOfLines={2} style={styles.monthEventText}>
                {contact?.name ?? 'Contact'} - {firstEvent.type}
              </Text>
            ) : null}
            {day.events.length > 1 ? <Text style={styles.monthMoreText}>+{day.events.length - 1}</Text> : null}
          </TouchableOpacity>
        );
      })}
    </View>
  </Card>
);

const EventForm = ({ state, dispatch }: Pick<ScreenProps, 'state' | 'dispatch'>) => {
  const [contactMode, setContactMode] = useState<'existing' | 'new'>('existing');
  const [contactId, setContactId] = useState(state.selectedContactId ?? state.contacts[0]?.id);
  const [newContactName, setNewContactName] = useState('');
  const [eventType, setEventType] = useState<EventType>('Birthday');
  const [label, setLabel] = useState('');
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [confirmConflict, setConfirmConflict] = useState(false);
  const input = {
    contactId: contactMode === 'existing' ? contactId : undefined,
    newContactName: contactMode === 'new' ? newContactName : undefined,
    eventType,
    label,
    date
  };
  const validation = validateManualEventInput(input, state.contacts, state.events);
  const errors = validation.ok ? [] : validation.errors;
  const warnings = validation.warnings;
  const canSave = errors.length === 0 && (warnings.length === 0 || confirmConflict);

  useEffect(() => {
    setConfirmConflict(false);
  }, [contactMode, contactId, newContactName, eventType, label, date]);

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <SectionTitle title="Add Event" detail="Create a relationship reminder without leaving the review-first flow." />
      <Card>
        <Text style={styles.cardTitle}>Who is this for?</Text>
        <View style={styles.wrapRow}>
          <Pill label="Existing" selected={contactMode === 'existing'} onPress={() => setContactMode('existing')} />
          <Pill label="New contact" selected={contactMode === 'new'} onPress={() => setContactMode('new')} />
        </View>
        {contactMode === 'existing' ? (
          <View style={styles.wrapRow}>
            {state.contacts.map(contact => (
              <Pill
                key={contact.id}
                label={contact.name}
                selected={contact.id === contactId}
                onPress={() => setContactId(contact.id)}
              />
            ))}
          </View>
        ) : (
          <TextInput
            accessibilityLabel="New contact name"
            placeholder="New contact name"
            placeholderTextColor={colors.muted}
            value={newContactName}
            onChangeText={setNewContactName}
            style={styles.input}
          />
        )}

        <Text style={styles.cardTitle}>Event type</Text>
        <View style={styles.wrapRow}>
          {manualEventTypes.map(item => (
            <Pill key={item} label={item} selected={eventType === item} onPress={() => setEventType(item)} />
          ))}
        </View>

        <TextInput
          accessibilityLabel="Event label"
          placeholder="Event label"
          placeholderTextColor={colors.muted}
          value={label}
          onChangeText={setLabel}
          style={styles.input}
        />
        <TextInput
          accessibilityLabel="Event date"
          placeholder="YYYY-MM-DD"
          placeholderTextColor={colors.muted}
          value={date}
          onChangeText={setDate}
          keyboardType="numbers-and-punctuation"
          style={styles.input}
        />

        {errors.map(error => (
          <Text key={error} style={styles.warningText}>
            {error}
          </Text>
        ))}
        {warnings.map(warning => (
          <Text key={warning} style={styles.warningText}>
            {warning}
          </Text>
        ))}
        {warnings.length > 0 ? (
          <Pill label="Keep separate" selected={confirmConflict} onPress={() => setConfirmConflict(value => !value)} />
        ) : null}
        <Button
          label={warnings.length > 0 ? 'Save reviewed event' : 'Save event'}
          disabled={!canSave}
          onPress={() =>
            dispatch({
              type: 'addManualEvent',
              contactId: input.contactId,
              newContactName: input.newContactName,
              eventType,
              label,
              date,
              confirmConflict
            })
          }
        />
      </Card>
    </ScrollView>
  );
};

const EventCard = ({ event, state, dispatch, onGenerateMessage }: { event: RelationshipEvent } & ScreenProps) => {
  const contact = getContact(state.contacts, event.contactId);
  const completeCount = event.checklist.filter(item => item.done).length;
  return (
    <Card>
      <View style={styles.rowBetween}>
        <View style={styles.flex}>
          <Text style={styles.cardTitle}>{event.label}</Text>
          <Text style={styles.mutedText}>
            {contact?.name ?? 'Unknown contact'} - {event.type} - {formatDateForLocale(event.date, state.settings.locale)}
          </Text>
        </View>
        <Pill label={`${completeCount}/${event.checklist.length}`} selected={completeCount === event.checklist.length} />
      </View>
      <View style={styles.checklist}>
        {event.checklist.map(item => (
          <TouchableOpacity
            accessibilityRole="checkbox"
            accessibilityState={{ checked: item.done }}
            key={item.id}
            onPress={() => dispatch({ type: 'toggleChecklist', eventId: event.id, itemId: item.id })}
            style={styles.checkItem}
          >
            <Text style={styles.checkMark}>{item.done ? '✓' : '○'}</Text>
            <Text style={styles.bodyText}>{item.label}</Text>
          </TouchableOpacity>
        ))}
      </View>
      <View style={styles.actionRow}>
        <Button
          label="Write message"
          onPress={() => {
            onGenerateMessage(
              event.contactId,
              event.id,
              event.type === 'Birthday' ? 'Birthday' : event.type === 'Follow-up' ? 'Check-in' : 'Congratulations'
            );
          }}
        />
        <Button
          label="Open contact"
          tone="secondary"
          onPress={() => dispatch({ type: 'navigate', screen: 'contactDetail', contactId: event.contactId })}
        />
      </View>
    </Card>
  );
};

const MessagesScreen = ({ state, dispatch, onManualHandoff, onSendEmail, onGenerateMessage }: ScreenProps) => {
  const counts = useMemo(
    () => ({
      review: state.messages.filter(message => message.status === 'Needs review').length,
      scheduled: state.messages.filter(message => message.status === 'Scheduled').length,
      failed: state.messages.filter(message => message.status === 'Failed' || message.status === 'Blocked').length,
      sent: state.messages.filter(message => message.status === 'Sent').length
    }),
    [state.messages]
  );

  return (
    <View>
      <SectionTitle title="Messages" detail="Review-first by default. Manual handoff keeps final control." />
      <View style={styles.wrapRow}>
        <Pill label={`Review ${counts.review}`} selected />
        <Pill label={`Scheduled ${counts.scheduled}`} />
        <Pill label={`Blocked ${counts.failed}`} />
        <Pill label={`Sent ${counts.sent}`} />
      </View>
      {state.messages.map(message => (
        <MessageCard
          key={message.id}
          message={message}
          state={state}
          dispatch={dispatch}
          onManualHandoff={onManualHandoff}
          onSendEmail={onSendEmail}
          onGenerateMessage={onGenerateMessage}
        />
      ))}
    </View>
  );
};

const MessageCard = ({ message, state, dispatch, onManualHandoff, onSendEmail }: { message: MessageDraft } & ScreenProps) => {
  const contact = getContact(state.contacts, message.contactId);
  return (
    <Card>
      <View style={styles.rowBetween}>
        <View style={styles.flex}>
          <Text style={styles.cardTitle}>{contact?.name ?? 'Unknown contact'}</Text>
          <Text style={styles.mutedText}>
            {message.reason} - {message.channel} - {message.status}
          </Text>
        </View>
        <Pill label={message.quality} selected={message.quality === 'AI draft'} />
      </View>
      <Text style={styles.bodyText}>{message.body}</Text>
      {message.duplicateWarning ? <Text style={styles.warningText}>{message.duplicateWarning}</Text> : null}
      <Text style={styles.smallText}>{message.readiness}</Text>
      <View style={styles.actionRow}>
        <Button
          label="Preview"
          onPress={() => dispatch({ type: 'navigate', screen: 'wishPreview', messageId: message.id, contactId: message.contactId })}
        />
        {message.status === 'Scheduled' && message.channel === 'Manual' ? (
          <Button label="Manual send" tone="secondary" onPress={() => onManualHandoff(message)} />
        ) : null}
        {message.status === 'Scheduled' && message.channel === 'Email' ? (
          <>
            <Button label="Send email" onPress={() => onSendEmail(message)} />
            <Button label="Email handoff" tone="secondary" onPress={() => onManualHandoff(message)} />
          </>
        ) : null}
        {message.status === 'Failed' || message.status === 'Blocked' ? (
          <Button label="Retry" tone="secondary" onPress={() => dispatch({ type: 'retryMessage', messageId: message.id })} />
        ) : null}
        {message.status === 'Sent' ? (
          <>
            <Button
              label="Follow up tomorrow"
              tone="secondary"
              onPress={() => dispatch({ type: 'scheduleMessageFollowUp', messageId: message.id, delayDays: 1 })}
            />
            <Button
              label="Next week"
              tone="ghost"
              onPress={() => dispatch({ type: 'scheduleMessageFollowUp', messageId: message.id, delayDays: 7 })}
            />
          </>
        ) : null}
      </View>
    </Card>
  );
};

const ContactsScreen = ({ state, dispatch }: ScreenProps) => {
  const query = state.searchQuery.toLowerCase();
  const contacts = state.contacts.filter(
    contact =>
      contact.name.toLowerCase().includes(query) ||
      contact.relationship.toLowerCase().includes(query) ||
      contact.group.toLowerCase().includes(query)
  );

  return (
    <View>
      <SectionTitle title="Contacts" detail="Find people, add context, and write outside events." />
      <TextInput
        accessibilityLabel="Search contacts"
        placeholder="Search name, relationship, or group"
        value={state.searchQuery}
        onChangeText={queryText => dispatch({ type: 'setSearch', query: queryText })}
        style={styles.input}
      />
      {contacts.map(contact => (
        <Card key={contact.id}>
          <View style={styles.rowBetween}>
            <View style={styles.flex}>
              <Text style={styles.cardTitle}>{contact.name}</Text>
              <Text style={styles.mutedText}>
                {contact.relationship} - {contact.group} - Health {contact.healthScore}
              </Text>
              <Text style={styles.smallText}>{contact.notesSummary}</Text>
            </View>
            <Pill label={contact.preferredChannel} selected />
          </View>
          <View style={styles.actionRow}>
            <Button
              label="Open"
              onPress={() => dispatch({ type: 'navigate', screen: 'contactDetail', contactId: contact.id })}
            />
            <Button
              label="Write"
              tone="secondary"
              onPress={() => dispatch({ type: 'navigate', screen: 'manualComposer', contactId: contact.id })}
            />
          </View>
        </Card>
      ))}
    </View>
  );
};

const ContactDetail = ({ contact, state, dispatch }: { contact: Contact } & ScreenProps) => {
  const [memoryText, setMemoryText] = useState('');
  const [memoryCategory, setMemoryCategory] = useState<MemoryCategory>('Preference');
  const [giftName, setGiftName] = useState('');
  const [enrichmentAnswers, setEnrichmentAnswers] = useState<Record<string, string>>({});
  const [timelineFilter, setTimelineFilter] = useState<ContactTimelineFilter>('All');
  const contactEvents = state.events.filter(event => event.contactId === contact.id);
  const contactMemories = state.memories.filter(note => note.contactId === contact.id);
  const contactGifts = state.gifts.filter(gift => gift.contactId === contact.id);
  const contactMessages = state.messages.filter(message => message.contactId === contact.id);
  const enrichmentPlan = buildContactEnrichmentPlan(state, contact.id);
  const timeline = buildContactTimeline(state, contact.id, timelineFilter);

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <SectionTitle title={contact.name} detail={`${contact.relationship} - ${contact.group}`} />
      <Card>
        <View style={styles.wrapRow}>
          <Pill label={`Health ${contact.healthScore}`} selected={contact.healthScore >= 70} />
          <Pill label={contact.isVip ? 'VIP' : 'Standard'} selected={contact.isVip} />
          <Pill label={`${contact.checkInCadenceDays}d check-in`} />
        </View>
        <Text style={styles.bodyText}>{contact.notesSummary}</Text>
        <View style={styles.actionRow}>
          <Button label="Write message" onPress={() => dispatch({ type: 'navigate', screen: 'manualComposer', contactId: contact.id })} />
          <Button label="Chat history" tone="secondary" onPress={() => dispatch({ type: 'navigate', screen: 'chatHistory', contactId: contact.id })} />
          <Button label="Snooze check-in" tone="secondary" onPress={() => dispatch({ type: 'snoozeCheckIn', contactId: contact.id, days: 14 })} />
        </View>
      </Card>

      {enrichmentPlan ? (
        <>
          <SectionTitle title="Guided enrichment" detail="Answer missing details to improve future drafts." />
          <Card>
            <View style={styles.rowBetween}>
              <View style={styles.flex}>
                <Text style={styles.cardTitle}>Personalization {enrichmentPlan.score}%</Text>
                <Text style={styles.smallText}>{enrichmentPlan.completedSignals.join(', ') || 'No strong signals yet'}</Text>
              </View>
              <Pill label={enrichmentPlan.label} selected={enrichmentPlan.label === 'Strong'} />
            </View>
            {enrichmentPlan.prompts.length === 0 ? (
              <Text style={styles.bodyText}>This profile has enough context for personalized drafts.</Text>
            ) : (
              enrichmentPlan.prompts.map(prompt => {
                const answer = enrichmentAnswers[prompt.id] ?? '';
                return (
                  <View key={prompt.id} style={styles.inlineItem}>
                    <Text style={styles.bodyText}>{prompt.question}</Text>
                    <Text style={styles.smallText}>{prompt.reason}</Text>
                    <TextInput
                      accessibilityLabel={prompt.question}
                      placeholder="Add a short answer"
                      value={answer}
                      onChangeText={value =>
                        setEnrichmentAnswers(current => ({
                          ...current,
                          [prompt.id]: value
                        }))
                      }
                      style={[styles.input, styles.multiline]}
                      multiline
                    />
                    <Button
                      label="Save answer"
                      disabled={answer.trim().length < 3}
                      onPress={() => {
                        dispatch({
                          type: 'answerEnrichmentPrompt',
                          contactId: contact.id,
                          promptId: prompt.id,
                          body: answer
                        });
                        if (answer.trim().length <= 500) {
                          setEnrichmentAnswers(current => ({
                            ...current,
                            [prompt.id]: ''
                          }));
                        }
                      }}
                    />
                  </View>
                );
              })
            )}
          </Card>
        </>
      ) : null}

      <SectionTitle title="Recipient tone" detail="Contact-specific tone overrides the global style profile." />
      <Card>
        <View style={styles.wrapRow}>
          {tones.map(tone => (
            <Pill
              key={tone}
              label={tone}
              selected={contact.tone.includes(tone)}
              onPress={() => dispatch({ type: 'updateContactTone', contactId: contact.id, tone })}
            />
          ))}
        </View>
      </Card>

      <SectionTitle title="Preferred channel" detail="Manual is safest until provider setup is trusted." />
      <Card>
        <View style={styles.wrapRow}>
          {channels.map(channel => (
            <Pill
              key={channel}
              label={channel}
              selected={contact.preferredChannel === channel}
              onPress={() => dispatch({ type: 'setContactChannel', contactId: contact.id, channel })}
            />
          ))}
        </View>
      </Card>

      <SectionTitle title="Memory Vault" detail="Private notes are excluded from AI context." />
      <Card>
        <View style={styles.wrapRow}>
          {memoryCategories.map(category => (
            <Pill key={category} label={category} selected={memoryCategory === category} onPress={() => setMemoryCategory(category)} />
          ))}
        </View>
        <TextInput
          accessibilityLabel="Memory note"
          placeholder="Add a useful detail, preference, or thing to avoid"
          value={memoryText}
          onChangeText={setMemoryText}
          style={[styles.input, styles.multiline]}
          multiline
        />
        <Button
          label="Add memory"
          onPress={() => {
            dispatch({ type: 'addMemory', contactId: contact.id, category: memoryCategory, body: memoryText });
            setMemoryText('');
          }}
        />
        {contactMemories.map(memory => (
          <View key={memory.id} style={styles.inlineItem}>
            <Text style={styles.smallText}>{memory.category}</Text>
            <Text style={styles.bodyText}>{memory.body}</Text>
          </View>
        ))}
      </Card>

      <SectionTitle title="Gift Advisor" detail="Focus on ideas and avoiding repeats; budget is optional." />
      <Card>
        <Text style={styles.bodyText}>
          Annual budget: {formatCurrencyForLocale(contact.annualGiftBudget, state.settings.locale)}. Recorded gifts:{' '}
          {contactGifts.length}.
        </Text>
        <TextInput
          accessibilityLabel="Gift name"
          placeholder="Gift name"
          value={giftName}
          onChangeText={setGiftName}
          style={styles.input}
        />
        <Button
          label="Record gift idea"
          onPress={() => {
            dispatch({ type: 'addGift', contactId: contact.id, name: giftName, occasion: 'Next event', cost: 0 });
            setGiftName('');
          }}
        />
        {contactGifts.map(gift => (
          <View key={gift.id} style={styles.inlineItem}>
            <Text style={styles.bodyText}>{gift.name}</Text>
            <Text style={styles.smallText}>
              {gift.occasion}, {gift.year} - {formatCurrencyForLocale(gift.cost, state.settings.locale)} - {gift.feedback}
            </Text>
          </View>
        ))}
      </Card>

      <SectionTitle title="Timeline" detail="Events, memories, gifts, and sent messages in one place." />
      <Card>
        <View style={styles.wrapRow}>
          {contactTimelineFilters.map(filter => (
            <Pill
              key={filter}
              label={filter}
              selected={timelineFilter === filter}
              onPress={() => setTimelineFilter(filter)}
            />
          ))}
        </View>
        <Text style={styles.smallText}>
          Events {contactEvents.length} - Memories {contactMemories.length} - Gifts {contactGifts.length} - Sent{' '}
          {contactMessages.filter(message => message.status === 'Sent').length}
        </Text>
        {timeline.entries.length === 0 ? <Text style={styles.bodyText}>{timeline.emptyMessage}</Text> : null}
        {timeline.entries.map(entry => (
          <View key={`${entry.type}-${entry.id}`} style={styles.inlineItem}>
            <View style={styles.rowBetween}>
              <View style={styles.flex}>
                <Text style={styles.bodyText}>{entry.title}</Text>
                <Text style={styles.smallText}>
                  {entry.type} - {formatDateForLocale(entry.dateIso, state.settings.locale)}
                </Text>
              </View>
              <Pill label={entry.type} selected />
            </View>
            <Text style={styles.smallText}>{entry.detail}</Text>
            {entry.targetScreen && entry.targetScreen !== 'contactDetail' ? (
              <Button
                label="Open"
                tone="secondary"
                onPress={() =>
                  dispatch({
                    type: 'navigate',
                    screen: entry.targetScreen!,
                    contactId: contact.id,
                    messageId: entry.messageId
                  })
                }
              />
            ) : null}
          </View>
        ))}
      </Card>
    </ScrollView>
  );
};

const ChatHistory = ({
  contact,
  state
}: {
  contact?: Contact;
  state: ScreenProps['state'];
  dispatch: ScreenProps['dispatch'];
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [channel, setChannel] = useState<ChatHistoryChannelFilter>('All');
  const contactId = contact?.id ?? state.selectedContactId;
  const history = contactId
    ? buildChatHistory(state, {
        contactId,
        searchQuery,
        channel
      })
    : {
        contactExists: false,
        messages: [],
        emptyState: 'Contact unavailable' as const
      };

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <SectionTitle
        title="Chat History"
        detail={contact ? `${contact.name} - sent RelateAI messages` : 'Historical messages for an unavailable contact'}
      />
      <Card>
        <TextInput
          accessibilityLabel="Search chat history"
          placeholder="Search sent text, reason, or channel"
          placeholderTextColor={colors.muted}
          value={searchQuery}
          onChangeText={setSearchQuery}
          style={styles.input}
        />
        <View style={styles.wrapRow}>
          {chatHistoryChannels.map(item => (
            <Pill key={item} label={item} selected={channel === item} onPress={() => setChannel(item)} />
          ))}
        </View>
      </Card>

      {history.messages.length > 0 ? (
        history.messages.map(message => (
          <Card key={message.id}>
            <View style={styles.rowBetween}>
              <View style={styles.flex}>
                <Text style={styles.cardTitle}>{message.reason}</Text>
                <Text style={styles.smallText}>
                  {message.channel} - {formatDateForLocale(message.sentAt ?? message.scheduledFor, state.settings.locale)}
                </Text>
              </View>
              <Pill label={message.channel} selected />
            </View>
            <Text selectable style={styles.bodyText}>
              {message.body}
            </Text>
          </Card>
        ))
      ) : (
        <Card>
          <Text style={styles.bodyText}>
            {history.emptyState === 'No matching messages'
              ? 'No sent messages match this search or channel.'
              : history.emptyState === 'Contact unavailable'
                ? 'This contact is no longer available. Historical messages will appear here if they still exist.'
                : 'No messages have been sent to this contact yet.'}
          </Text>
        </Card>
      )}
    </ScrollView>
  );
};

const WishPreview = ({
  message,
  state,
  dispatch,
  onManualHandoff,
  onSendEmail,
  onGenerateMessage
}: { message: MessageDraft } & ScreenProps) => {
  const contact = getContact(state.contacts, message.contactId);
  const event = getEvent(state.events, message.eventId);
  const aiContext = state.memories.filter(note => note.contactId === message.contactId && note.category !== 'Private');
  const duplicateNeedsAcknowledgement = Boolean(message.duplicateWarning && !message.duplicateAcknowledged);
  const canApprove = message.body.trim().length >= 12 && message.status !== 'Rejected' && !duplicateNeedsAcknowledgement;

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <SectionTitle title="Wish Preview" detail={`${contact?.name ?? 'Unknown contact'} - ${message.reason}`} />
      <Card>
        <View style={styles.wrapRow}>
          <Pill label={message.status} selected />
          <Pill label={message.channel} />
          <Pill label={message.quality} />
        </View>
        <Text style={styles.smallText}>
          Scheduled: {formatDateForLocale(message.scheduledFor ?? event?.date, state.settings.locale)}
        </Text>
        {message.duplicateWarning ? <Text style={styles.warningText}>{message.duplicateWarning}</Text> : null}
        {message.duplicateAcknowledged ? (
          <Text style={styles.smallText}>Duplicate risk acknowledged. Review the text once more before approval.</Text>
        ) : null}
      </Card>

      <SectionTitle title="AI context preview" detail="Private notes and secrets are excluded." />
      <Card>
        {aiContext.length === 0 ? (
          <Text style={styles.bodyText}>No optional memory context will be used. Add contact details for better drafts.</Text>
        ) : (
          aiContext.map(note => (
            <Text key={note.id} style={styles.bodyText}>
              - {note.body}
            </Text>
          ))
        )}
      </Card>

      <SectionTitle title="Choose variant" />
      <Card>
        <View style={styles.wrapRow}>
          {(['short', 'standard', 'warm'] as MessageDraft['selectedVariant'][]).map(variant => (
            <Pill
              key={variant}
              label={variant}
              selected={message.selectedVariant === variant}
              onPress={() => dispatch({ type: 'selectVariant', messageId: message.id, variant })}
            />
          ))}
        </View>
      </Card>

      <SectionTitle title="Message" detail={message.readiness} />
      <Card>
        <TextInput
          accessibilityLabel="Message text"
          value={message.body}
          onChangeText={body => dispatch({ type: 'editMessage', messageId: message.id, body })}
          style={[styles.input, styles.messageInput]}
          multiline
        />
        <View style={styles.actionRow}>
          <Button label="Approve" disabled={!canApprove} onPress={() => dispatch({ type: 'approveMessage', messageId: message.id })} />
          {duplicateNeedsAcknowledgement ? (
            <Button
              label="Continue anyway"
              tone="secondary"
              onPress={() => dispatch({ type: 'acknowledgeDuplicateRisk', messageId: message.id })}
            />
          ) : null}
          <Button
            label="Regenerate"
            tone="secondary"
            onPress={() => onGenerateMessage(message.contactId, message.eventId, message.reason)}
          />
          <Button label="Reject" tone="danger" onPress={() => dispatch({ type: 'rejectMessage', messageId: message.id })} />
          {message.status === 'Scheduled' && message.channel === 'Email' ? (
            <Button label="Send email" onPress={() => onSendEmail(message)} />
          ) : null}
          {message.status === 'Scheduled' || message.channel === 'Manual' ? (
            <Button label="Manual handoff" tone="secondary" onPress={() => onManualHandoff(message)} />
          ) : null}
        </View>
      </Card>
    </ScrollView>
  );
};

const ManualComposer = ({
  contact,
  state,
  dispatch,
  onGenerateMessage
}: {
  contact: Contact;
  state: ReturnType<typeof createInitialState>;
  dispatch: React.Dispatch<RelateAction>;
  onGenerateMessage: (contactId: string, eventId: string | undefined, reason: ComposerReason) => void;
}) => {
  const [reason, setReason] = useState<ComposerReason>('Check-in');
  const templates = useMemo(() => findMessageTemplates(reason, contact.tone), [contact.tone, reason]);
  const [selectedTemplateId, setSelectedTemplateId] = useState(templates[0]?.id ?? '');
  const selectedTemplate = templates.find(template => template.id === selectedTemplateId) ?? templates[0];
  const templateContext =
    state.memories.find(memory => memory.contactId === contact.id && memory.category !== 'Private')?.body ??
    contact.notesSummary;
  const [templateBody, setTemplateBody] = useState(
    selectedTemplate ? renderMessageTemplate(selectedTemplate, contact, templateContext) : ''
  );

  useEffect(() => {
    setSelectedTemplateId(templates[0]?.id ?? '');
  }, [contact.id, reason, templates]);

  useEffect(() => {
    if (selectedTemplate) {
      setTemplateBody(renderMessageTemplate(selectedTemplate, contact, templateContext));
    }
  }, [contact, selectedTemplate, templateContext]);

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <SectionTitle title="Manual Composer" detail={`Write to ${contact.name} without needing an event.`} />
      <Card>
        <Text style={styles.bodyText}>
          Choose why you are writing. Use a local template or ask the AI provider for a review-first draft.
        </Text>
        <View style={styles.wrapRow}>
          {composerReasons.map(item => (
            <Pill key={item} label={item} selected={reason === item} onPress={() => setReason(item)} />
          ))}
        </View>
        <Text style={styles.cardTitle}>Templates</Text>
        <View style={styles.wrapRow}>
          {templates.map(template => (
            <Pill
              key={template.id}
              label={template.title}
              selected={selectedTemplate?.id === template.id}
              onPress={() => setSelectedTemplateId(template.id)}
            />
          ))}
        </View>
        <TextInput
          accessibilityLabel="Template message"
          placeholder="Edit the selected template"
          placeholderTextColor={colors.muted}
          value={templateBody}
          onChangeText={setTemplateBody}
          style={[styles.input, styles.messageInput]}
          multiline
        />
        <View style={styles.actionRow}>
          <Button
            label="Use template"
            disabled={templateBody.trim().length < 12}
            onPress={() =>
              dispatch({
                type: 'createTemplateDraft',
                contactId: contact.id,
                reason,
                body: templateBody,
                templateId: selectedTemplate?.id
              })
            }
          />
          <Button
            label="Ask AI"
            tone="secondary"
            onPress={() => onGenerateMessage(contact.id, undefined, reason)}
          />
        </View>
      </Card>
    </ScrollView>
  );
};

const MoreScreen = ({
  state,
  dispatch,
  onImportDeviceContacts,
  onImportSampleContacts,
  onScheduleReminders,
  onExportCalendar,
  onImportCalendar,
  onTestAiProvider,
  onExportBackup,
  onPickBackup,
  onRestoreBackup
}: ScreenProps) => {
  const importDevice = onImportDeviceContacts ?? (() => undefined);
  const importSample = onImportSampleContacts ?? (() => undefined);
  const scheduleReminders = onScheduleReminders ?? (() => undefined);
  const exportCalendar = onExportCalendar ?? (() => undefined);
  const importCalendar = onImportCalendar ?? (() => undefined);
  const testAiProvider = onTestAiProvider ?? (() => undefined);
  const exportBackup = onExportBackup ?? (() => undefined);
  const pickBackup = onPickBackup ?? (async () => undefined);
  const restoreBackup = onRestoreBackup ?? (() => undefined);
  const aiConfig = readAiProviderConfig();
  const emailConfig = readEmailSenderConfig();
  const [backupPassphrase, setBackupPassphrase] = useState('');
  const [selectedBackup, setSelectedBackup] = useState<BackupFilePickResult | undefined>();
  const [styleSamples, setStyleSamples] = useState('');
  const [setupGoal, setSetupGoal] = useState<SetupGoal>('Reminders only');
  const [showSetupCheckDetails, setShowSetupCheckDetails] = useState(false);
  const [activityQuery, setActivityQuery] = useState('');
  const [activityType, setActivityType] = useState<ActivityTypeFilter>('All');
  const [activitySeverity, setActivitySeverity] = useState<ActivitySeverityFilter>('All');
  const [activityDate, setActivityDate] = useState<ActivityDateFilter>('Last 7 days');
  const passphraseProblems = validateBackupPassphrase(backupPassphrase);
  const canUsePassphrase = passphraseProblems.length === 0;
  const canRestore =
    Boolean(selectedBackup) && canUsePassphrase && (selectedBackup?.preview.warnings.length ?? 0) === 0;
  const statusLabel = (enabled: boolean) => t(state.settings.locale, enabled ? 'status.on' : 'status.off');
  const setupPlan = buildSetupWizardPlan(
    state,
    {
      aiEndpointConfigured: Boolean(aiConfig.endpoint),
      emailEndpointConfigured: Boolean(emailConfig.endpoint)
    },
    setupGoal
  );
  const setupDoctorReport = buildSetupDoctorReport(state, {
    aiEndpointConfigured: Boolean(aiConfig.endpoint),
    emailEndpointConfigured: Boolean(emailConfig.endpoint)
  });
  const activityHistory = buildActivityHistory(state.activity, {
    query: activityQuery,
    type: activityType,
    severity: activitySeverity,
    date: activityDate
  });
  const eligibleStyleMessageCount = eligibleSentStyleMessages(state).length;
  const runSetupAction = (step: SetupStep) => {
    if (step.command === 'planReminders') {
      scheduleReminders();
      return;
    }
    if (step.command === 'testAiProvider') {
      testAiProvider();
      return;
    }
    if (step.targetScreen) {
      dispatch({ type: 'navigate', screen: step.targetScreen });
    }
  };
  const runSetupDoctorAction = (check: SetupDoctorCheck) => {
    if (check.command === 'planReminders') {
      scheduleReminders();
      return;
    }
    if (check.command === 'testAiProvider') {
      testAiProvider();
      return;
    }
    if (check.targetScreen) {
      dispatch({ type: 'navigate', screen: check.targetScreen, contactId: check.contactId });
    }
  };
  return (
  <View>
    <SectionTitle title="More" detail="Secondary tools stay out of the core navigation." />

    <Card>
      <Text style={styles.cardTitle}>Calendar Sync</Text>
      <Text style={styles.bodyText}>
        Mirror RelateAI events to the device calendar or import calendar candidates for review.
      </Text>
      <Text style={styles.smallText}>
        Exported: {state.calendarSync.exportedCount}. Imported: {state.calendarSync.importedCount}.
      </Text>
      {state.calendarSync.lastError ? <Text style={styles.warningText}>{state.calendarSync.lastError}</Text> : null}
      <View style={styles.actionRow}>
        <Button label="Export events" onPress={exportCalendar} />
        <Button label="Import calendar" tone="secondary" onPress={importCalendar} />
      </View>
    </Card>

    <Card>
      <Text style={styles.cardTitle}>Notification Reminders</Text>
      <Text style={styles.bodyText}>
        Plan and schedule event reminders. Reminders open the app for review; they never send messages directly.
      </Text>
      <Text style={styles.smallText}>Planned reminders: {state.reminderPlans.length}</Text>
      <Button label="Plan and schedule reminders" onPress={scheduleReminders} />
    </Card>

    <Card>
      <Text style={styles.cardTitle}>Contact Import</Text>
      <Text style={styles.bodyText}>
        Import contacts, dedupe by phone/email/name, and mark imported birthdays for review before sending.
      </Text>
      <View style={styles.actionRow}>
        <Button label="Import device contacts" onPress={importDevice} />
        <Button label="Import sample contacts" tone="secondary" onPress={importSample} />
      </View>
    </Card>

    <Card>
      <Text style={styles.cardTitle}>Persistence</Text>
      <Text style={styles.bodyText}>
        Status: {state.persistence.status}
        {state.persistence.lastSavedAt
          ? ` - Saved ${formatDateForLocale(state.persistence.lastSavedAt, state.settings.locale)}`
          : ''}
      </Text>
      {state.persistence.error ? <Text style={styles.warningText}>{state.persistence.error}</Text> : null}
    </Card>

    <Card>
      <Text style={styles.cardTitle}>Setup Wizard</Text>
      <Text style={styles.bodyText}>{setupPlan.summary}</Text>
      <View style={styles.wrapRow}>
        {setupGoals.map(goal => (
          <Pill key={goal} label={goal} selected={setupGoal === goal} onPress={() => setSetupGoal(goal)} />
        ))}
      </View>
      {setupPlan.recommendedStep ? (
        <View style={styles.inlineItem}>
          <Text style={styles.smallText}>Recommended next step</Text>
          <Text style={styles.bodyText}>{setupPlan.recommendedStep.title}</Text>
          <Text style={styles.smallText}>{setupPlan.recommendedStep.detail}</Text>
          <Button label={setupPlan.recommendedStep.action} onPress={() => runSetupAction(setupPlan.recommendedStep!)} />
        </View>
      ) : null}
      {setupPlan.steps.map(step => (
        <View key={step.id} style={styles.inlineItem}>
          <View style={styles.rowBetween}>
            <Text style={styles.bodyText}>{step.title}</Text>
            <Pill label={step.status} selected={step.status === 'Ready'} />
          </View>
          <Text style={styles.smallText}>{step.detail}</Text>
          {step.status !== 'Ready' ? (
            <Button label={step.action} tone="secondary" onPress={() => runSetupAction(step)} />
          ) : null}
        </View>
      ))}
    </Card>

    <Card>
      <Text style={styles.cardTitle}>Setup Check</Text>
      <Text style={styles.bodyText}>{setupDoctorReport.summary}</Text>
      <Text style={styles.smallText}>{setupDoctorReport.dryRun.message}</Text>
      {setupDoctorReport.recommendedCheck ? (
        <View style={styles.inlineItem}>
          <Text style={styles.smallText}>Recommended fix</Text>
          <Text style={styles.bodyText}>{setupDoctorReport.recommendedCheck.title}</Text>
          <Text style={styles.smallText}>{setupDoctorReport.recommendedCheck.impact}</Text>
          <Button
            label={setupDoctorReport.recommendedCheck.actionLabel}
            onPress={() => runSetupDoctorAction(setupDoctorReport.recommendedCheck!)}
          />
        </View>
      ) : null}
      <Button
        label={showSetupCheckDetails ? 'Hide details' : 'Show details'}
        tone="secondary"
        onPress={() => setShowSetupCheckDetails(value => !value)}
      />
      {showSetupCheckDetails
        ? setupDoctorReport.checksByGroup.map(group => (
            <View key={group.group} style={styles.inlineItem}>
              <Text style={styles.cardTitle}>{group.group}</Text>
              {group.checks.map(check => (
                <View key={check.id} style={styles.inlineItem}>
                  <View style={styles.rowBetween}>
                    <Text style={styles.bodyText}>{check.title}</Text>
                    <Pill label={check.status} selected={check.status === 'Ready'} />
                  </View>
                  <Text style={styles.smallText}>{check.impact}</Text>
                  {check.status !== 'Ready' ? (
                    <Button label={check.actionLabel} tone="secondary" onPress={() => runSetupDoctorAction(check)} />
                  ) : null}
                </View>
              ))}
            </View>
          ))
        : null}
    </Card>

    <Card>
      <Text style={styles.cardTitle}>Style Coach</Text>
      <Text style={styles.bodyText}>
        {state.styleProfile.confidence}: {state.styleProfile.formality}. Avg {state.styleProfile.averageLength} chars.
      </Text>
      <Text style={styles.smallText}>Language: {state.styleProfile.language}</Text>
      <Text style={styles.smallText}>Emoji use: {state.styleProfile.emojiUse}</Text>
      <Text style={styles.smallText}>Samples learned: {state.styleProfile.sampleCount}</Text>
      {state.styleProfile.confidence !== 'Strong' ? (
        <Text style={styles.warningText}>Add more representative samples to raise confidence.</Text>
      ) : null}
      <TextInput
        accessibilityLabel="Style Coach writing samples"
        placeholder="Paste 2+ message samples, separated by blank lines"
        value={styleSamples}
        onChangeText={setStyleSamples}
        style={[styles.input, styles.multiline]}
        multiline
      />
      <Text style={styles.smallText}>Eligible sent messages: {eligibleStyleMessageCount}</Text>
      <View style={styles.actionRow}>
        <Button label="Analyze samples" onPress={() => dispatch({ type: 'trainStyleFromSamples', samples: styleSamples })} />
        <Button
          label="Analyze sent messages"
          tone="secondary"
          disabled={eligibleStyleMessageCount < 2}
          onPress={() => dispatch({ type: 'trainStyleFromSentMessages' })}
        />
      </View>
    </Card>

    <Card>
      <Text style={styles.cardTitle}>AI Provider</Text>
      <Text style={styles.bodyText}>
        Status: {aiConfig.endpoint ? state.aiProvider.status : 'Not configured'}. Drafting sends only approved context and
        excludes private notes, phone numbers, email addresses, credentials, and raw provider IDs.
      </Text>
      {state.aiProvider.lastPrivacySummary ? (
        <Text style={styles.smallText}>{state.aiProvider.lastPrivacySummary}</Text>
      ) : null}
      {state.aiProvider.lastError ? <Text style={styles.warningText}>{state.aiProvider.lastError}</Text> : null}
      <Button label="Test AI provider" tone="secondary" onPress={testAiProvider} />
    </Card>

    <Card>
      <Text style={styles.cardTitle}>Analytics summary</Text>
      <Text style={styles.bodyText}>Healthy contacts: {state.contacts.filter(contact => contact.healthScore >= 70).length}</Text>
      <Text style={styles.bodyText}>Needs attention: {state.contacts.filter(contact => contact.healthScore < 60).length}</Text>
      <Text style={styles.bodyText}>Sent messages: {state.messages.filter(message => message.status === 'Sent').length}</Text>
    </Card>

    <Card>
      <Text style={styles.cardTitle}>Backup and Restore</Text>
      <Text style={styles.bodyText}>
        Last backup:{' '}
        {state.backups[0]
          ? formatDateForLocale(state.backups[0].createdAt, state.settings.locale)
          : t(state.settings.locale, 'common.notScheduled')}
        . Backups are explicit, encrypted, and protected by a passphrase that is not saved.
      </Text>
      <TextInput
        accessibilityLabel="Backup passphrase"
        placeholder="Backup passphrase"
        value={backupPassphrase}
        onChangeText={setBackupPassphrase}
        style={styles.input}
        secureTextEntry
      />
      {backupPassphrase.length > 0 && !canUsePassphrase ? (
        <Text style={styles.warningText}>{passphraseProblems.join(' ')}</Text>
      ) : null}
      <View style={styles.actionRow}>
        <Button
          label="Export encrypted file"
          disabled={!canUsePassphrase}
          onPress={() => {
            void exportBackup(backupPassphrase);
          }}
        />
        <Button
          label="Select backup file"
          tone="secondary"
          onPress={() => {
            void pickBackup().then(picked => {
              if (picked) {
                setSelectedBackup(picked);
              }
            });
          }}
        />
      </View>
      {selectedBackup ? (
        <View style={styles.inlineItem}>
          <Text style={styles.bodyText}>{selectedBackup.name}</Text>
          <Text style={styles.smallText}>
            {selectedBackup.preview.app} backup from{' '}
            {formatDateForLocale(selectedBackup.preview.createdAt, state.settings.locale)}. Records:{' '}
            {selectedBackup.preview.recordCount}.
          </Text>
          {selectedBackup.preview.warnings.map(warning => (
            <Text key={warning} style={styles.warningText}>
              {warning}
            </Text>
          ))}
          <Button
            label="Confirm restore"
            tone="danger"
            disabled={!canRestore}
            onPress={() => {
              Alert.alert(
                'Restore backup?',
                'This replaces local contacts, events, messages, preferences, and related history with the selected backup.',
                [
                  { text: 'Cancel', style: 'cancel' },
                  {
                    text: 'Restore',
                    style: 'destructive',
                    onPress: () => restoreBackup(selectedBackup.raw, backupPassphrase, selectedBackup.preview.recordCount)
                  }
                ]
              );
            }}
          />
        </View>
      ) : null}
    </Card>

    <Card>
      <Text style={styles.cardTitle}>Settings</Text>
      <Text style={styles.bodyText}>{t(state.settings.locale, 'settings.languageDetail')}</Text>
      <View style={styles.wrapRow}>
        {supportedLocales.map(locale => (
          <Pill
            key={locale}
            label={localeMetadata[locale].label}
            selected={state.settings.locale === locale}
            accessibilityLabel={`${t(state.settings.locale, 'settings.language')}: ${localeMetadata[locale].label}`}
            onPress={() => dispatch({ type: 'setLocale', locale })}
          />
        ))}
      </View>
      <TextInput
        accessibilityLabel="Sender email"
        placeholder="Sender email for provider delivery"
        value={state.emailDelivery.senderEmail ?? ''}
        onChangeText={senderEmail => dispatch({ type: 'setEmailSender', senderEmail })}
        autoCapitalize="none"
        keyboardType="email-address"
        style={styles.input}
      />
      <Text style={styles.smallText}>
        Email provider: {emailConfig.endpoint ? state.emailDelivery.status : 'Not configured'}.
        {state.emailDelivery.lastError ? ` ${state.emailDelivery.lastError}` : ''}
      </Text>
      <View style={styles.wrapRow}>
        <Pill label={`AI ${statusLabel(state.settings.aiEnabled)}`} selected={state.settings.aiEnabled} onPress={() => dispatch({ type: 'toggleSetting', key: 'aiEnabled' })} />
        <Pill label={`Notifications ${statusLabel(state.settings.notificationsEnabled)}`} selected={state.settings.notificationsEnabled} onPress={() => dispatch({ type: 'toggleSetting', key: 'notificationsEnabled' })} />
        <Pill label={`SMS ${statusLabel(state.settings.smsEnabled)}`} selected={state.settings.smsEnabled} onPress={() => dispatch({ type: 'toggleSetting', key: 'smsEnabled' })} />
        <Pill label={`Email ${statusLabel(state.settings.emailEnabled)}`} selected={state.settings.emailEnabled} onPress={() => dispatch({ type: 'toggleSetting', key: 'emailEnabled' })} />
        <Pill label={`Manual WhatsApp ${statusLabel(state.settings.whatsappHandoffEnabled)}`} selected={state.settings.whatsappHandoffEnabled} onPress={() => dispatch({ type: 'toggleSetting', key: 'whatsappHandoffEnabled' })} />
        <Pill label={`Biometric lock ${statusLabel(state.settings.biometricLockEnabled)}`} selected={state.settings.biometricLockEnabled} onPress={() => dispatch({ type: 'toggleSetting', key: 'biometricLockEnabled' })} />
      </View>
      <Text style={styles.smallText}>
        Automation mode: {state.settings.automationMode}. Quiet hours: {state.settings.quietHours.start} to {state.settings.quietHours.end}.
      </Text>
    </Card>

    <Card>
      <Text style={styles.cardTitle}>Activity History</Text>
      <TextInput
        accessibilityLabel="Search activity history"
        placeholder="Search activity"
        placeholderTextColor={colors.muted}
        value={activityQuery}
        onChangeText={setActivityQuery}
        style={styles.input}
      />
      <Text style={styles.smallText}>Type</Text>
      <View style={styles.wrapRow}>
        {activityTypeFilters.map(item => (
          <Pill key={item} label={item} selected={activityType === item} onPress={() => setActivityType(item)} />
        ))}
      </View>
      <Text style={styles.smallText}>Severity</Text>
      <View style={styles.wrapRow}>
        {activitySeverityFilters.map(item => (
          <Pill key={item} label={item} selected={activitySeverity === item} onPress={() => setActivitySeverity(item)} />
        ))}
      </View>
      <Text style={styles.smallText}>Date</Text>
      <View style={styles.wrapRow}>
        {activityDateFilters.map(item => (
          <Pill key={item} label={item} selected={activityDate === item} onPress={() => setActivityDate(item)} />
        ))}
      </View>
      {activityHistory.rows.length > 0 ? (
        activityHistory.rows.slice(0, 12).map(row => (
          <View key={row.item.id} style={styles.inlineItem}>
            <View style={styles.rowBetween}>
              <Text style={styles.bodyText}>{row.item.title}</Text>
              <Pill label={row.item.severity} selected={row.isOpenIssue} />
            </View>
            <Text style={styles.smallText}>
              {row.item.type} - {formatDateForLocale(row.item.createdAt, state.settings.locale)}
            </Text>
            <Text style={styles.smallText}>{row.item.detail}</Text>
            <Button
              label={row.actionLabel}
              tone="secondary"
              onPress={() => dispatch({ type: 'navigate', screen: row.targetScreen })}
            />
          </View>
        ))
      ) : (
        <Text style={styles.bodyText}>
          {activityHistory.emptyState === 'No activity yet'
            ? 'No app activity has been recorded yet.'
            : 'No activity matches these filters.'}
        </Text>
      )}
    </Card>

    <Button label="Reset demo data" tone="secondary" onPress={() => dispatch({ type: 'resetDemo' })} />
  </View>
  );
};

const Metric = ({ label, value }: { label: string; value: string }) => (
  <View style={styles.metric}>
    <Text style={styles.metricValue}>{value}</Text>
    <Text style={styles.metricLabel}>{label}</Text>
  </View>
);

type ScreenProps = {
  state: ReturnType<typeof createInitialState>;
  dispatch: React.Dispatch<RelateAction>;
  onManualHandoff: (message: MessageDraft) => void;
  onSendEmail: (message: MessageDraft) => void;
  onGenerateMessage: (contactId: string, eventId: string | undefined, reason: ComposerReason) => void;
  onTestAiProvider?: () => void;
  onExportBackup?: (passphrase: string) => void;
  onPickBackup?: () => Promise<BackupFilePickResult | undefined>;
  onRestoreBackup?: (raw: string, passphrase: string, recordCount: number) => void;
  onImportDeviceContacts?: () => void;
  onImportSampleContacts?: () => void;
  onScheduleReminders?: () => void;
  onExportCalendar?: () => void;
  onImportCalendar?: () => void;
};

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: colors.bg
  },
  appShell: {
    flex: 1,
    backgroundColor: colors.bg
  },
  header: {
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    borderBottomColor: colors.border,
    borderBottomWidth: 1,
    backgroundColor: colors.surface,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center'
  },
  appName: {
    fontSize: 24,
    fontWeight: '800',
    color: colors.text
  },
  tagline: {
    color: colors.muted,
    marginTop: 2
  },
  content: {
    padding: spacing.lg,
    paddingBottom: 110
  },
  sectionHeader: {
    marginBottom: spacing.sm,
    marginTop: spacing.md
  },
  sectionTitle: {
    color: colors.text,
    fontSize: 20,
    fontWeight: '800'
  },
  sectionDetail: {
    color: colors.muted,
    marginTop: spacing.xs,
    lineHeight: 20
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: 8,
    borderColor: colors.border,
    borderWidth: 1,
    padding: spacing.md,
    marginBottom: spacing.md
  },
  cardTitle: {
    color: colors.text,
    fontSize: 17,
    fontWeight: '800',
    marginBottom: spacing.xs
  },
  bodyText: {
    color: colors.text,
    lineHeight: 21,
    marginBottom: spacing.sm
  },
  mutedText: {
    color: colors.muted,
    lineHeight: 20,
    marginBottom: spacing.xs
  },
  smallText: {
    color: colors.muted,
    fontSize: 12,
    lineHeight: 18,
    marginBottom: spacing.xs
  },
  warningText: {
    color: colors.warning,
    fontWeight: '700',
    marginBottom: spacing.sm
  },
  rowBetween: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    gap: spacing.md
  },
  flex: {
    flex: 1
  },
  wrapRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    marginBottom: spacing.sm
  },
  actionRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    marginTop: spacing.sm
  },
  button: {
    backgroundColor: colors.primary,
    borderRadius: 8,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    minHeight: 40,
    justifyContent: 'center',
    alignItems: 'center'
  },
  buttonSecondary: {
    backgroundColor: colors.primarySoft
  },
  buttonDanger: {
    backgroundColor: colors.danger
  },
  buttonGhost: {
    backgroundColor: 'transparent'
  },
  buttonDisabled: {
    opacity: 0.45
  },
  buttonText: {
    color: '#ffffff',
    fontWeight: '800'
  },
  buttonSecondaryText: {
    color: colors.primary
  },
  buttonGhostText: {
    color: colors.primary
  },
  pill: {
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.xs,
    backgroundColor: colors.surface
  },
  pillSelected: {
    backgroundColor: colors.primarySoft,
    borderColor: colors.primary
  },
  pillText: {
    color: colors.muted,
    fontSize: 12,
    fontWeight: '700'
  },
  pillTextSelected: {
    color: colors.primary
  },
  statGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    marginBottom: spacing.md
  },
  metric: {
    flexBasis: '48%',
    flexGrow: 1,
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: 8,
    padding: spacing.md
  },
  metricValue: {
    fontSize: 24,
    fontWeight: '900',
    color: colors.primary
  },
  metricLabel: {
    color: colors.muted,
    marginTop: spacing.xs
  },
  checklist: {
    gap: spacing.xs,
    marginTop: spacing.sm
  },
  checkItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm
  },
  checkMark: {
    color: colors.primary,
    fontWeight: '900',
    fontSize: 18,
    width: 22
  },
  monthControls: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: spacing.sm,
    marginTop: spacing.sm
  },
  monthGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    borderTopColor: colors.border,
    borderLeftColor: colors.border,
    borderTopWidth: 1,
    borderLeftWidth: 1
  },
  monthWeekday: {
    width: '14.285%',
    minHeight: 28,
    borderRightColor: colors.border,
    borderBottomColor: colors.border,
    borderRightWidth: 1,
    borderBottomWidth: 1,
    color: colors.muted,
    fontSize: 11,
    fontWeight: '800',
    padding: spacing.xs,
    textAlign: 'center'
  },
  monthCell: {
    width: '14.285%',
    minHeight: 86,
    borderRightColor: colors.border,
    borderBottomColor: colors.border,
    borderRightWidth: 1,
    borderBottomWidth: 1,
    padding: spacing.xs,
    backgroundColor: colors.surface
  },
  monthCellMuted: {
    backgroundColor: colors.bg
  },
  monthCellActive: {
    backgroundColor: colors.primarySoft
  },
  monthDay: {
    color: colors.text,
    fontSize: 12,
    fontWeight: '900',
    marginBottom: spacing.xs
  },
  monthTextMuted: {
    color: colors.muted
  },
  monthEventText: {
    color: colors.text,
    fontSize: 10,
    lineHeight: 13
  },
  monthMoreText: {
    color: colors.primary,
    fontSize: 10,
    fontWeight: '800',
    marginTop: 2
  },
  input: {
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: 8,
    backgroundColor: colors.surface,
    padding: spacing.md,
    color: colors.text,
    marginBottom: spacing.sm,
    minHeight: 44
  },
  multiline: {
    minHeight: 84,
    textAlignVertical: 'top'
  },
  messageInput: {
    minHeight: 190,
    textAlignVertical: 'top'
  },
  inlineItem: {
    borderTopColor: colors.border,
    borderTopWidth: 1,
    paddingTop: spacing.sm,
    marginTop: spacing.sm
  },
  lockScreen: {
    flex: 1,
    justifyContent: 'center',
    padding: spacing.lg
  },
  tabBar: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    flexDirection: 'row',
    backgroundColor: colors.surface,
    borderTopColor: colors.border,
    borderTopWidth: 1,
    padding: spacing.sm,
    gap: spacing.xs
  },
  tab: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 48,
    borderRadius: 8
  },
  tabActive: {
    backgroundColor: colors.primarySoft
  },
  tabText: {
    color: colors.muted,
    fontSize: 12,
    fontWeight: '700'
  },
  tabTextActive: {
    color: colors.primary
  }
});

export default App;
