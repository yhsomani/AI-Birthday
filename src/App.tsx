import React, { useEffect, useMemo, useReducer, useRef, useState } from 'react';
import * as Notifications from 'expo-notifications';
import {
  AppState as NativeAppState,
  BackHandler,
  Linking,
  Platform,
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
import { buildAiDraftRequest, type AiDraftContextOptions } from './domain/aiDrafting';
import {
  analyticsRanges,
  buildAnalyticsCsvReport,
  buildAnalyticsDashboard,
  buildShareableAnalyticsSummary,
  type AnalyticsInsight,
  type AnalyticsRange
} from './domain/analytics';
import { buildAccountExitPlan, type AccountExitPlan } from './domain/accountAccess';
import { validateBackupPassphrase } from './domain/backup';
import { buildHandoffTarget } from './domain/channelHandoff';
import {
  buildCheckInReminderQueue,
  type CheckInReminder,
  type CheckInReminderQueue,
  type CheckInReminderStatus
} from './domain/checkIns';
import { buildChatHistory, type ChatHistoryChannelFilter } from './domain/chatHistory';
import { resolveBiometricLock } from './domain/biometricLock';
import { buildAiContextPreview } from './domain/aiContextPreview';
import { supportedContactLanguages, validateContactEssentials } from './domain/contactEssentials';
import {
  buildContactBrowserRows,
  contactGroupFilters,
  contactQualityFilters,
  contactSorts,
  type ContactGroupFilter,
  type ContactQualityFilter,
  type ContactSort
} from './domain/contactBrowser';
import { buildContactEnrichmentPlan } from './domain/contactEnrichment';
import { resolveContactPreferencesForContact } from './domain/contactPreferences';
import {
  buildContactTimeline,
  contactTimelineFilters,
  type ContactTimelineEntry,
  type ContactTimelineEntryType,
  type ContactTimelineFilter
} from './domain/contactTimeline';
import { parseRelateDeepLink, resolveDeepLinkDestination } from './domain/deepLinks';
import { buildEmailDeliveryRequest } from './domain/emailDelivery';
import { parseEventImportText, type EventImportFormat } from './domain/eventImport';
import { buildEventPreparationPlan, type EventPreparationStatus } from './domain/eventPreparation';
import {
  buildEventMonthView,
  filterRelationshipEvents,
  shiftMonth,
  type EventTimeFilter,
  type EventTypeFilter
} from './domain/eventBrowser';
import {
  buildGiftBudgetSummary,
  buildGiftSuggestions,
  giftCategories,
  giftFeedbackOptions,
  validateGiftBudgetInput,
  type GiftBudgetFit,
  type GiftSuggestionConfidence,
  type GiftSuggestion
} from './domain/giftAdvisor';
import { buildHomeWidgetSummary } from './domain/homeWidget';
import {
  activityDateFilters,
  activitySeverityFilters,
  activityTypeFilters,
  buildActivityHistory,
  type ActivityHistoryRow,
  type ActivityDateFilter,
  type ActivitySeverityFilter,
  type ActivityTypeFilter
} from './domain/activityHistory';
import {
  advancedManualEventTypes,
  manualEventTypes,
  primaryManualEventTypes,
  validateManualEventInput
} from './domain/events';
import { readNotificationRouteUrl } from './domain/notificationRoutes';
import { buildManualComposerState, manualComposerReasons } from './domain/manualComposer';
import { MEMORY_NOTE_MAX_LENGTH, buildMemoryVaultReport, validateMemoryNoteInput } from './domain/memoryVault';
import { buildMessageTemplateLibrary } from './domain/messageTemplates';
import { buildOnboardingPlan, onboardingGoals } from './domain/onboarding';
import { buildPrivacyCenterReport } from './domain/privacyCenter';
import {
  evaluateProviderEndpointReadiness,
  type ProviderEndpointReadiness
} from './domain/providerEndpointReadiness';
import {
  buildRelationshipHealthInsight,
  checkInCadenceOptions,
  relationshipGroupOptions,
  type RelationshipConfidence,
  type RelationshipHealthLabel
} from './domain/relationshipHealth';
import {
  buildMessageInbox,
  buildMessageBulkActionReport,
  messageBulkActions,
  messageInboxChannelFilters,
  messageInboxSorts,
  messageInboxTabs,
  type MessageInboxRecovery,
  type MessageInboxChannelFilter,
  type MessageInboxSort,
  type MessageInboxTab,
  type MessageInboxRow,
  type MessageBulkAction
} from './domain/messageInbox';
import { validateMessageBodyForChannel } from './domain/messageBodyPolicy';
import { automationModes, buildSchedulingPolicySummary } from './domain/schedulingPolicy';
import { buildSetupDoctorDryRunSnapshot, buildSetupDoctorReport, type SetupDoctorCheck } from './domain/setupDoctor';
import { buildSetupWizardPlan, setupGoals, type SetupGoal, type SetupStep } from './domain/setupWizard';
import { eligibleSentStyleMessages } from './domain/styleCoach';
import { buildTonePreferenceSummary } from './domain/toneControls';
import { buildWishFeedbackPlan, type WishFeedbackOptionId } from './domain/wishFeedback';
import type {
  AppState,
  ActivityItem,
  AutomationMode,
  ComposerReason,
  Contact,
  EventType,
  GiftCategory,
  GiftRecord,
  MemoryCategory,
  MessageChannel,
  MessageDraft,
  MessageStatus,
  OnboardingGoal,
  RelationshipGroup,
  RelationshipEvent,
  Screen,
  SupportedLocale,
  Tone
} from './domain/types';
import {
  formatCurrencyForLocale,
  formatDateForLocale,
  formatMonthForLocale,
  localeMetadata,
  supportedLocales,
  t,
  tc,
  type TranslationKey
} from './i18n/i18n';
import { exportEventsToDeviceCalendar, importEventsFromDeviceCalendar } from './native/calendarBridge';
import { pickEventImportFile } from './native/eventImportFiles';
import { authenticateWithBiometrics, readBiometricCapability } from './native/biometricAuth';
import { importDeviceContacts } from './native/contactImporter';
import { readAiProviderConfig, requestAiDraft } from './native/aiProviderClient';
import { readEmailSenderConfig, sendEmailMessage } from './native/emailSenderClient';
import {
  exportEncryptedBackupFile,
  pickEncryptedBackupFile,
  restoreEncryptedBackupFile,
  type BackupFilePickResult
} from './native/backupFiles';
import { scheduleReminderPlans } from './native/reminderScheduler';
import { cancelOwnedReminderNotifications } from './native/reminderScheduler';
import { clearHomeWidgetSummary, syncHomeWidgetSummary } from './native/homeWidgetBridge';
import { openManualHandoffTarget } from './native/channelHandoffBridge';
import { secureStateStore } from './native/secureStateStore';
import { createProductionInitialState, relateReducer, type RelateAction } from './state/relateReducer';
import { inspectPersistedState, loadStateWithRecovery, saveState } from './state/persistence';
import { PersistenceCoordinator } from './state/persistenceCoordinator';
import {
  clearLocalDataTransaction,
  restoreLocalDataTransaction,
  type DataLifecycleDependencies
} from './application/dataLifecycle';
import { localizeHomeWidgetSummary } from './ui/homeWidgetPresentation';
import { colors, spacing } from './ui/theme';
import { AppDialogHost } from './ui/AppDialog';
import { appDialogController, showAppAlert } from './ui/appDialogService';
import { AppErrorBoundary } from './ui/AppErrorBoundary';
import { appOperationalIssues } from './application/operationalIssues';
import {
  buildBrowserNavigationHistoryState,
  createNavigationState,
  currentNavigationRoute,
  navigationRouteEquals,
  readBrowserNavigationHistoryState,
  reduceNavigation,
  resolveNavigationDestination,
  type NavigationAction,
  type NavigationDestination,
  type NavigationEntities
} from './navigation/navigationState';

const primaryTabs: Array<{ key: Screen; labelKey: TranslationKey }> = [
  { key: 'home', labelKey: 'nav.home' },
  { key: 'events', labelKey: 'nav.events' },
  { key: 'messages', labelKey: 'nav.messages' },
  { key: 'contacts', labelKey: 'nav.contacts' },
  { key: 'more', labelKey: 'nav.more' }
];

const preservesNavigationOrigin = (screen: Screen) =>
  screen === 'contactDetail' ||
  screen === 'chatHistory' ||
  screen === 'wishPreview' ||
  screen === 'manualComposer' ||
  screen === 'eventForm';

const tones: Tone[] = ['Warm', 'Respectful', 'Playful', 'Concise', 'Formal', 'Hinglish', 'No emoji'];
const channels: MessageChannel[] = ['SMS', 'WhatsApp', 'Email', 'Manual'];

const memoryCategories: MemoryCategory[] = ['General', 'Private', 'Preference', 'Event', 'Gift', 'Milestone'];
const primaryEventTypeFilters: EventTypeFilter[] = ['All', ...primaryManualEventTypes];
const advancedEventTypeFilters: EventTypeFilter[] = advancedManualEventTypes;
const eventTimeFilters: EventTimeFilter[] = ['Upcoming', 'This month', 'All', 'Past'];
const chatHistoryChannels: ChatHistoryChannelFilter[] = ['All', ...channels];
const defaultAutomationModes = automationModes.filter(mode => mode !== 'Fully auto');

const getContact = (contacts: Contact[], contactId?: string) =>
  contacts.find(contact => contact.id === contactId);

const getEvent = (events: RelationshipEvent[], eventId?: string) =>
  events.find(event => event.id === eventId);

const messageChannelLabel = (locale: SupportedLocale, channel: MessageChannel | 'All') => {
  const keyByChannel: Record<MessageChannel | 'All', TranslationKey> = {
    All: 'label.channel.all',
    SMS: 'label.channel.sms',
    WhatsApp: 'label.channel.whatsApp',
    Email: 'label.channel.email',
    Manual: 'label.channel.manual'
  };
  return t(locale, keyByChannel[channel]);
};

const messageStatusLabel = (locale: SupportedLocale, status: MessageStatus) => {
  const keyByStatus: Record<MessageStatus, TranslationKey> = {
    'Needs review': 'label.messageStatus.needsReview',
    Scheduled: 'label.messageStatus.scheduled',
    'Delivery pending': 'label.messageStatus.scheduled',
    Blocked: 'label.messageStatus.blocked',
    Sent: 'label.messageStatus.sent',
    Failed: 'label.messageStatus.failed',
    'Delivery unknown': 'label.messageStatus.failed',
    Rejected: 'label.messageStatus.rejected',
    Draft: 'label.messageStatus.draft'
  };
  return t(locale, keyByStatus[status]);
};

const messageQualityLabel = (locale: SupportedLocale, quality: MessageDraft['quality']) => {
  const keyByQuality: Record<MessageDraft['quality'], TranslationKey> = {
    'AI draft': 'label.messageQuality.aiDraft',
    'Template fallback': 'label.messageQuality.templateFallback',
    'Needs more context': 'label.messageQuality.needsMoreContext'
  };
  return t(locale, keyByQuality[quality]);
};

const setupStatusDisplayLabel = (
  locale: SupportedLocale,
  status: 'Ready' | 'Needs action' | 'Optional' | 'Warning'
) => {
  const keyByStatus: Record<'Ready' | 'Needs action' | 'Optional' | 'Warning', TranslationKey> = {
    Ready: 'feature.more.setup.status.ready',
    'Needs action': 'feature.more.setup.status.needsAction',
    Optional: 'feature.more.setup.status.optional',
    Warning: 'feature.more.setup.status.warning'
  };
  return t(locale, keyByStatus[status]);
};

const onboardingGoalLabel = (locale: SupportedLocale, goal: OnboardingGoal) => {
  const keyByGoal: Record<OnboardingGoal, TranslationKey> = {
    'Reminders first': 'label.onboardingGoal.remindersFirst',
    'AI wishes': 'label.onboardingGoal.aiWishes',
    'Manual relationship manager': 'label.onboardingGoal.manualRelationshipManager',
    'Full setup': 'label.onboardingGoal.fullSetup'
  };
  return t(locale, keyByGoal[goal]);
};

const messageInboxTabLabel = (locale: SupportedLocale, tab: MessageInboxTab) => {
  const keyByTab: Record<MessageInboxTab, TranslationKey> = {
    All: 'feature.messages.tab.all',
    Review: 'feature.messages.tab.review',
    Today: 'feature.messages.tab.today',
    Scheduled: 'feature.messages.tab.scheduled',
    Blocked: 'feature.messages.tab.blocked',
    Failed: 'feature.messages.tab.failed',
    Sent: 'feature.messages.tab.sent',
    Rejected: 'feature.messages.tab.rejected'
  };
  return t(locale, keyByTab[tab]);
};

const messageInboxSortLabel = (locale: SupportedLocale, sort: MessageInboxSort) => {
  const keyBySort: Record<MessageInboxSort, TranslationKey> = {
    Newest: 'feature.messages.sort.newest',
    Scheduled: 'feature.messages.sort.scheduled',
    Contact: 'feature.messages.sort.contact',
    Status: 'feature.messages.sort.status'
  };
  return t(locale, keyBySort[sort]);
};

const messageBulkActionLabel = (locale: SupportedLocale, action: MessageBulkAction) => {
  const keyByAction: Record<MessageBulkAction, TranslationKey> = {
    Approve: 'feature.messages.bulk.action.approve',
    Reject: 'feature.messages.bulk.action.reject',
    Retry: 'feature.messages.bulk.action.retry',
    'Revoke approval': 'feature.messages.bulk.action.revokeApproval'
  };
  return t(locale, keyByAction[action]);
};

const messageVariantLabel = (locale: SupportedLocale, variant: MessageDraft['selectedVariant']) => {
  const keyByVariant: Record<MessageDraft['selectedVariant'], TranslationKey> = {
    short: 'feature.wishPreview.variant.short',
    standard: 'feature.wishPreview.variant.standard',
    warm: 'feature.wishPreview.variant.warm'
  };
  return t(locale, keyByVariant[variant]);
};

const eventTypeLabel = (locale: SupportedLocale, type: EventTypeFilter) => {
  const keyByType: Record<EventTypeFilter, TranslationKey> = {
    All: 'label.eventType.all',
    Birthday: 'label.eventType.birthday',
    Anniversary: 'label.eventType.anniversary',
    'Work anniversary': 'label.eventType.workAnniversary',
    Custom: 'label.eventType.custom',
    Graduation: 'label.eventType.graduation',
    Holiday: 'label.eventType.holiday',
    Revival: 'label.eventType.revival',
    'Follow-up': 'label.eventType.followUp'
  };
  return t(locale, keyByType[type]);
};

const eventTimeFilterLabel = (locale: SupportedLocale, filter: EventTimeFilter) => {
  const keyByFilter: Record<EventTimeFilter, TranslationKey> = {
    Upcoming: 'feature.events.time.upcoming',
    'This month': 'feature.events.time.thisMonth',
    All: 'feature.events.time.all',
    Past: 'feature.events.time.past'
  };
  return t(locale, keyByFilter[filter]);
};

const eventPreparationStatusLabel = (locale: SupportedLocale, status: EventPreparationStatus) => {
  const keyByStatus: Record<EventPreparationStatus, TranslationKey> = {
    Done: 'feature.eventCard.stepStatus.done',
    'Needs action': 'feature.eventCard.stepStatus.needsAction'
  };
  return t(locale, keyByStatus[status]);
};

const composerReasonLabel = (locale: SupportedLocale, reason: ComposerReason) => {
  const keyByReason: Record<ComposerReason, TranslationKey> = {
    Birthday: 'label.composerReason.birthday',
    'Check-in': 'label.composerReason.checkIn',
    Thanks: 'label.composerReason.thanks',
    Congratulations: 'label.composerReason.congratulations',
    Apology: 'label.composerReason.apology',
    'Follow-up': 'label.composerReason.followUp',
    Custom: 'label.composerReason.custom'
  };
  return t(locale, keyByReason[reason]);
};

const localizedToneLabel = (locale: SupportedLocale, tone: Tone) => {
  const keyByTone: Record<Tone, TranslationKey> = {
    Warm: 'feature.more.settings.tone.warm',
    Respectful: 'feature.more.settings.tone.respectful',
    Playful: 'feature.more.settings.tone.playful',
    Concise: 'feature.more.settings.tone.concise',
    Formal: 'feature.more.settings.tone.formal',
    Hinglish: 'feature.more.settings.tone.hinglish',
    'No emoji': 'feature.more.settings.tone.noEmoji'
  };
  return t(locale, keyByTone[tone]);
};

const relationshipGroupDisplayLabel = (locale: SupportedLocale, group: RelationshipGroup) => {
  const keyByGroup: Record<RelationshipGroup, TranslationKey> = {
    Family: 'feature.more.settings.group.family',
    Friends: 'feature.more.settings.group.friends',
    'Close friends': 'feature.more.settings.group.closeFriends',
    Work: 'feature.more.settings.group.work',
    Other: 'feature.more.settings.group.other'
  };
  return t(locale, keyByGroup[group]);
};

const contactGroupLabel = (locale: SupportedLocale, group: ContactGroupFilter) => {
  return group === 'All' ? t(locale, 'label.contactGroup.all') : relationshipGroupDisplayLabel(locale, group);
};

const contactQualityLabel = (locale: SupportedLocale, quality: ContactQualityFilter) => {
  const keyByQuality: Record<ContactQualityFilter, TranslationKey> = {
    All: 'label.contactQuality.all',
    VIP: 'label.contactQuality.vip',
    'Missing event': 'label.contactQuality.missingEvent',
    'Missing channel': 'label.contactQuality.missingChannel',
    'Low health': 'label.contactQuality.lowHealth',
    'Needs details': 'label.contactQuality.needsDetails'
  };
  return t(locale, keyByQuality[quality]);
};

const contactSortLabel = (locale: SupportedLocale, sort: ContactSort) => {
  const keyBySort: Record<ContactSort, TranslationKey> = {
    Name: 'label.contactSort.name',
    'Health priority': 'label.contactSort.healthPriority',
    'Next event': 'label.contactSort.nextEvent'
  };
  return t(locale, keyBySort[sort]);
};

const contactLanguageLabel = (locale: SupportedLocale, language: Contact['language']) => {
  const keyByLanguage: Record<Contact['language'], TranslationKey> = {
    English: 'label.contactLanguage.english',
    Hinglish: 'label.contactLanguage.hinglish',
    Hindi: 'label.contactLanguage.hindi'
  };
  return t(locale, keyByLanguage[language]);
};

const automationModeDisplayLabel = (locale: SupportedLocale, mode: AutomationMode) => {
  const keyByMode: Record<AutomationMode, TranslationKey> = {
    'Always ask': 'feature.more.settings.automation.alwaysAsk',
    'Smart approve': 'feature.more.settings.automation.smartApprove',
    'VIP approve': 'feature.more.settings.automation.vipApprove',
    'Fully auto': 'feature.more.settings.automation.fullyAuto'
  };
  return t(locale, keyByMode[mode]);
};

const checkInCadenceLabel = (locale: SupportedLocale, days: number) =>
  t(locale, 'feature.more.settings.cadenceDays', { days });

const checkInStatusLabel = (locale: SupportedLocale, status: CheckInReminderStatus) => {
  const keyByStatus: Record<CheckInReminderStatus, TranslationKey> = {
    Due: 'label.checkInStatus.due',
    Snoozed: 'label.checkInStatus.snoozed',
    Current: 'label.checkInStatus.current'
  };
  return t(locale, keyByStatus[status]);
};

const checkInDayCountLabel = (locale: SupportedLocale, days: number) =>
  tc(locale, days, { one: 'common.count.day.one', other: 'common.count.day.other' });

const checkInReminderTitle = (locale: SupportedLocale, reminder: CheckInReminder) => {
  const keyByStatus: Record<CheckInReminderStatus, TranslationKey> = {
    Due: 'feature.checkIn.reminder.title.due',
    Snoozed: 'feature.checkIn.reminder.title.snoozed',
    Current: 'feature.checkIn.reminder.title.current'
  };
  return t(locale, keyByStatus[reminder.status], { name: reminder.contactName });
};

const checkInReminderDetail = (locale: SupportedLocale, reminder: CheckInReminder) => {
  if (reminder.status === 'Snoozed') {
    return t(locale, 'feature.checkIn.reminder.detail.snoozed', {
      date: formatDateForLocale(reminder.snoozedUntil, locale)
    });
  }
  if (reminder.daysSinceContact === undefined) {
    return t(locale, 'feature.checkIn.reminder.detail.noRecent');
  }
  return t(locale, 'feature.checkIn.reminder.detail.daysSince', {
    days: checkInDayCountLabel(locale, reminder.daysSinceContact),
    cadence: checkInDayCountLabel(locale, reminder.cadenceDays)
  });
};

const checkInReminderPrimaryActionLabel = (locale: SupportedLocale, reminder: CheckInReminder) =>
  t(
    locale,
    reminder.status === 'Due'
      ? 'feature.checkIn.reminder.action.writeCheckIn'
      : 'feature.checkIn.reminder.action.writeAnyway'
  );

const checkInReminderSecondaryActionLabel = (locale: SupportedLocale) =>
  t(locale, 'feature.checkIn.reminder.action.markContacted');

const checkInQueueSummary = (locale: SupportedLocale, queue: CheckInReminderQueue) => {
  if (queue.due.length > 0) {
    return t(locale, 'feature.checkIn.queue.summaryDue', { count: queue.due.length });
  }
  if (queue.snoozed.length > 0) {
    return t(locale, 'feature.checkIn.queue.summarySnoozed', { count: queue.snoozed.length });
  }
  return t(locale, 'feature.checkIn.queue.summaryNone');
};

const checkInQueueEmptyMessage = (
  locale: SupportedLocale,
  queue: CheckInReminderQueue,
  contactCount: number
) => {
  if (contactCount === 0) {
    return t(locale, 'feature.checkIn.queue.emptyNoContacts');
  }
  return queue.due.length === 0 ? t(locale, 'feature.checkIn.queue.emptyNoDue') : undefined;
};

const buildLocalizedHomeWidgetSummary = (state: AppState) =>
  localizeHomeWidgetSummary(buildHomeWidgetSummary(state), state.settings.locale);

const relationshipHealthLabel = (locale: SupportedLocale, label: RelationshipHealthLabel) => {
  const keyByLabel: Record<RelationshipHealthLabel, TranslationKey> = {
    Healthy: 'label.relationshipHealth.healthy',
    Watch: 'label.relationshipHealth.watch',
    'Needs attention': 'label.relationshipHealth.needsAttention'
  };
  return t(locale, keyByLabel[label]);
};

const confidenceLabel = (
  locale: SupportedLocale,
  confidence: RelationshipConfidence | GiftSuggestionConfidence
) => {
  const keyByConfidence: Record<RelationshipConfidence | GiftSuggestionConfidence, TranslationKey> = {
    Low: 'label.confidence.low',
    Medium: 'label.confidence.medium',
    High: 'label.confidence.high'
  };
  return t(locale, keyByConfidence[confidence]);
};

const contactTimelineFilterLabel = (locale: SupportedLocale, filter: ContactTimelineFilter) => {
  const keyByFilter: Record<ContactTimelineFilter, TranslationKey> = {
    All: 'feature.contactDetail.timeline.filter.all',
    Events: 'feature.contactDetail.timeline.filter.events',
    Memories: 'feature.contactDetail.timeline.filter.memories',
    Gifts: 'feature.contactDetail.timeline.filter.gifts',
    Messages: 'feature.contactDetail.timeline.filter.messages'
  };
  return t(locale, keyByFilter[filter]);
};

const contactTimelineEntryTypeLabel = (locale: SupportedLocale, type: ContactTimelineEntryType) =>
  contactTimelineFilterLabel(locale, type);

const contactTimelineEntryTitle = (
  locale: SupportedLocale,
  entry: ContactTimelineEntry,
  state: AppState
) => {
  if (entry.type === 'Memories') {
    const memory = state.memories.find(item => item.id === entry.id);
    return memory ? memoryCategoryLabel(locale, memory.category) : entry.title;
  }
  if (entry.type === 'Messages') {
    const message = state.messages.find(item => item.id === entry.id);
    return message ? composerReasonLabel(locale, message.reason) : entry.title;
  }
  return entry.title;
};

const contactTimelineEntryDetail = (
  locale: SupportedLocale,
  entry: ContactTimelineEntry,
  state: AppState
) => {
  switch (entry.type) {
    case 'Events': {
      const event = state.events.find(item => item.id === entry.id);
      return event
        ? t(locale, 'feature.contactDetail.timeline.eventMeta', {
            type: eventTypeLabel(locale, event.type),
            status: t(
              locale,
              event.verified
                ? 'feature.contactDetail.timeline.eventVerified'
                : 'feature.contactDetail.timeline.eventNeedsReview'
            )
          })
        : entry.detail;
    }
    case 'Memories': {
      const memory = state.memories.find(item => item.id === entry.id);
      return memory?.body ?? entry.detail;
    }
    case 'Gifts': {
      const gift = state.gifts.find(item => item.id === entry.id);
      return gift
        ? t(locale, 'feature.contactDetail.timeline.giftMeta', {
            occasion: gift.occasion,
            feedback: giftFeedbackLabel(locale, gift.feedback)
          })
        : entry.detail;
    }
    case 'Messages': {
      const message = state.messages.find(item => item.id === entry.id);
      return message
        ? t(locale, 'feature.contactDetail.timeline.messageMeta', {
            channel: messageChannelLabel(locale, message.channel),
            status: messageStatusLabel(locale, message.status)
          })
        : entry.detail;
    }
  }
};

const contactTimelineEmptyMessage = (locale: SupportedLocale, filter: ContactTimelineFilter) => {
  const keyByFilter: Record<ContactTimelineFilter, TranslationKey> = {
    All: 'feature.contactDetail.timeline.empty.all',
    Events: 'feature.contactDetail.timeline.empty.events',
    Memories: 'feature.contactDetail.timeline.empty.memories',
    Gifts: 'feature.contactDetail.timeline.empty.gifts',
    Messages: 'feature.contactDetail.timeline.empty.messages'
  };
  return t(locale, keyByFilter[filter]);
};

const memoryCategoryLabel = (locale: SupportedLocale, category: MemoryCategory) => {
  const keyByCategory: Record<MemoryCategory, TranslationKey> = {
    General: 'label.memoryCategory.general',
    Private: 'label.memoryCategory.private',
    Preference: 'label.memoryCategory.preference',
    Event: 'label.memoryCategory.event',
    Gift: 'label.memoryCategory.gift',
    Milestone: 'label.memoryCategory.milestone'
  };
  return t(locale, keyByCategory[category]);
};

const memoryAiUseLabel = (locale: SupportedLocale, category: MemoryCategory) =>
  t(
    locale,
    category === 'Private'
      ? 'feature.contactDetail.memory.aiUse.private'
      : 'feature.contactDetail.memory.aiUse.eligible'
  );

const giftCategoryLabel = (locale: SupportedLocale, category: GiftCategory) => {
  const keyByCategory: Record<GiftCategory, TranslationKey> = {
    Experience: 'label.giftCategory.experience',
    Food: 'label.giftCategory.food',
    Books: 'label.giftCategory.books',
    Wellness: 'label.giftCategory.wellness',
    Personal: 'label.giftCategory.personal',
    Other: 'label.giftCategory.other'
  };
  return t(locale, keyByCategory[category]);
};

const giftFeedbackLabel = (locale: SupportedLocale, feedback: GiftRecord['feedback']) => {
  const keyByFeedback: Record<GiftRecord['feedback'], TranslationKey> = {
    Unknown: 'label.giftFeedback.unknown',
    Liked: 'label.giftFeedback.liked',
    Disliked: 'label.giftFeedback.disliked'
  };
  return t(locale, keyByFeedback[feedback]);
};

const giftBudgetFitLabel = (locale: SupportedLocale, budgetFit: GiftBudgetFit) => {
  const keyByFit: Record<GiftBudgetFit, TranslationKey> = {
    'Within budget': 'label.giftBudgetFit.withinBudget',
    'Over budget': 'label.giftBudgetFit.overBudget',
    'No budget set': 'label.giftBudgetFit.noBudgetSet'
  };
  return t(locale, keyByFit[budgetFit]);
};

const giftSuggestionConfidenceLabel = (locale: SupportedLocale, confidence: GiftSuggestionConfidence) => {
  return confidenceLabel(locale, confidence);
};

const weekdayLabelsFor = (locale: SupportedLocale) =>
  ([
    'feature.events.weekday.sun',
    'feature.events.weekday.mon',
    'feature.events.weekday.tue',
    'feature.events.weekday.wed',
    'feature.events.weekday.thu',
    'feature.events.weekday.fri',
    'feature.events.weekday.sat'
  ] as TranslationKey[]).map(key => t(locale, key));

const activityTitleKeyByTitle: Record<string, TranslationKey> = {
  'Review-first automation enabled': 'feature.more.activityHistory.title.reviewFirstAutomationEnabled',
  'Draft created': 'feature.more.activityHistory.title.draftCreated',
  'Draft not created': 'feature.more.activityHistory.title.draftNotCreated',
  'Template fallback created': 'feature.more.activityHistory.title.templateFallbackCreated',
  'Template draft not created': 'feature.more.activityHistory.title.templateDraftNotCreated',
  'Template draft created': 'feature.more.activityHistory.title.templateDraftCreated',
  'AI draft not created': 'feature.more.activityHistory.title.aiDraftNotCreated',
  'AI draft created': 'feature.more.activityHistory.title.aiDraftCreated',
  'AI draft regenerated': 'feature.more.activityHistory.title.aiDraftRegenerated',
  'AI provider ready': 'feature.more.activityHistory.title.aiProviderReady',
  'AI provider unavailable': 'feature.more.activityHistory.title.aiProviderUnavailable',
  'Event not saved': 'feature.more.activityHistory.title.eventNotSaved',
  'Review event conflict': 'feature.more.activityHistory.title.reviewEventConflict',
  'Event saved': 'feature.more.activityHistory.title.eventSaved',
  'Message not approved': 'feature.more.activityHistory.title.messageNotApproved',
  'Message approval blocked': 'feature.more.activityHistory.title.messageApprovalBlocked',
  'Message approved': 'feature.more.activityHistory.title.messageApproved',
  'Duplicate risk acknowledged': 'feature.more.activityHistory.title.duplicateRiskAcknowledged',
  'Message rejected': 'feature.more.activityHistory.title.messageRejected',
  'Message approval revoked': 'feature.more.activityHistory.title.messageApprovalRevoked',
  'Test send ready': 'feature.more.activityHistory.title.testSendReady',
  'Test send blocked': 'feature.more.activityHistory.title.testSendBlocked',
  'Manual handoff not completed': 'feature.more.activityHistory.title.manualHandoffNotCompleted',
  'Manual handoff completed': 'feature.more.activityHistory.title.manualHandoffCompleted',
  'Follow-up not scheduled': 'feature.more.activityHistory.title.followUpNotScheduled',
  'Follow-up scheduled': 'feature.more.activityHistory.title.followUpScheduled',
  'Message retry prepared': 'feature.more.activityHistory.title.messageRetryPrepared',
  'Memory not saved': 'feature.more.activityHistory.title.memoryNotSaved',
  'Memory saved': 'feature.more.activityHistory.title.memorySaved',
  'Memory not updated': 'feature.more.activityHistory.title.memoryNotUpdated',
  'Memory updated': 'feature.more.activityHistory.title.memoryUpdated',
  'Memory not pinned': 'feature.more.activityHistory.title.memoryNotPinned',
  'Memory pinned': 'feature.more.activityHistory.title.memoryPinned',
  'Memory unpinned': 'feature.more.activityHistory.title.memoryUnpinned',
  'Memory not deleted': 'feature.more.activityHistory.title.memoryNotDeleted',
  'Memory deleted': 'feature.more.activityHistory.title.memoryDeleted',
  'Enrichment not saved': 'feature.more.activityHistory.title.enrichmentNotSaved',
  'Enrichment saved': 'feature.more.activityHistory.title.enrichmentSaved',
  'Gift not saved': 'feature.more.activityHistory.title.giftNotSaved',
  'Gift saved': 'feature.more.activityHistory.title.giftSaved',
  'Gift not deleted': 'feature.more.activityHistory.title.giftNotDeleted',
  'Gift deleted': 'feature.more.activityHistory.title.giftDeleted',
  'Contact not saved': 'feature.more.activityHistory.title.contactNotSaved',
  'Contact saved': 'feature.more.activityHistory.title.contactSaved',
  'Relationship group updated': 'feature.more.activityHistory.title.relationshipGroupUpdated',
  'Relationship group defaults updated': 'feature.more.activityHistory.title.relationshipGroupDefaultsUpdated',
  'VIP setting updated': 'feature.more.activityHistory.title.vipSettingUpdated',
  'Do-not-disturb updated': 'feature.more.activityHistory.title.dndUpdated',
  'Check-in cadence not saved': 'feature.more.activityHistory.title.checkInCadenceNotSaved',
  'Check-in cadence updated': 'feature.more.activityHistory.title.checkInCadenceUpdated',
  'Contact automation override updated': 'feature.more.activityHistory.title.contactAutomationOverrideUpdated',
  'Group defaults applied': 'feature.more.activityHistory.title.groupDefaultsApplied',
  'Check-in snoozed': 'feature.more.activityHistory.title.checkInSnoozed',
  'Contact marked contacted': 'feature.more.activityHistory.title.contactMarkedContacted',
  'Language updated': 'feature.more.activityHistory.title.languageUpdated',
  'Email sender updated': 'feature.more.activityHistory.title.emailSenderUpdated',
  'Email provider ready': 'feature.more.activityHistory.title.emailProviderReady',
  'Email delivery failed': 'feature.more.activityHistory.title.emailDeliveryFailed',
  'Email sent status not recorded': 'feature.more.activityHistory.title.emailSentStatusNotRecorded',
  'Email sent': 'feature.more.activityHistory.title.emailSent',
  'Onboarding goal updated': 'feature.more.activityHistory.title.onboardingGoalUpdated',
  'Onboarding step skipped': 'feature.more.activityHistory.title.onboardingStepSkipped',
  'Onboarding completed': 'feature.more.activityHistory.title.onboardingCompleted',
  'Account mode updated': 'feature.more.activityHistory.title.accountModeUpdated',
  'Account disconnected': 'feature.more.activityHistory.title.accountDisconnected',
  'Manual WhatsApp handoff consent updated': 'feature.more.activityHistory.title.whatsappHandoffConsentUpdated',
  'Setting updated': 'feature.more.activityHistory.title.settingUpdated',
  'Automation mode updated': 'feature.more.activityHistory.title.automationModeUpdated',
  'Quiet hours not saved': 'feature.more.activityHistory.title.quietHoursNotSaved',
  'Quiet hours updated': 'feature.more.activityHistory.title.quietHoursUpdated',
  'Blackout not saved': 'feature.more.activityHistory.title.blackoutNotSaved',
  'Blackout added': 'feature.more.activityHistory.title.blackoutAdded',
  'Blackout removed': 'feature.more.activityHistory.title.blackoutRemoved',
  'Contacts imported': 'feature.more.activityHistory.title.contactsImported',
  'Reminders planned': 'feature.more.activityHistory.title.remindersPlanned',
  'Reminders need setup': 'feature.more.activityHistory.title.remindersNeedSetup',
  'Calendar events imported': 'feature.more.activityHistory.title.calendarEventsImported',
  'Events exported to calendar': 'feature.more.activityHistory.title.eventsExportedToCalendar',
  'Calendar sync failed': 'feature.more.activityHistory.title.calendarSyncFailed',
  'Encrypted backup created': 'feature.more.activityHistory.title.encryptedBackupCreated',
  'Encrypted backup restored': 'feature.more.activityHistory.title.encryptedBackupRestored',
  'Analytics report exported': 'feature.more.activityHistory.title.analyticsReportExported',
  'Setup Check dry run completed': 'feature.more.activityHistory.title.setupCheckDryRunCompleted',
  'Style profile not updated': 'feature.more.activityHistory.title.styleProfileNotUpdated',
  'Style profile updated': 'feature.more.activityHistory.title.styleProfileUpdated',
  'Local data cleared': 'feature.more.activityHistory.title.localDataCleared'
};

const activityDetailKeyByDetail: Record<string, TranslationKey> = {
  'Messages require approval before scheduling or sending.': 'feature.more.activityHistory.detail.reviewFirstAutomation',
  'Contacts, events, messages, memories, gifts, and backups were cleared.':
    'feature.more.activityHistory.detail.localDataCleared',
  'A contact is required.': 'feature.more.activityHistory.detail.contactRequired',
  'The selected contact could not be found.': 'feature.more.activityHistory.detail.selectedContactMissing',
  'This message is no longer available.': 'feature.more.activityHistory.detail.messageUnavailable',
  'The message is approved for scheduled or manual send.': 'feature.more.activityHistory.detail.messageApproved',
  'The user explicitly chose to continue after reviewing the duplicate warning.':
    'feature.more.activityHistory.detail.duplicateRiskAcknowledged',
  'The draft will not be sent.': 'feature.more.activityHistory.detail.messageRejected',
  'Review the message before scheduling again.': 'feature.more.activityHistory.detail.messageApprovalRevoked',
  'The user retained final control in the destination app.': 'feature.more.activityHistory.detail.manualHandoffCompleted',
  'Review the message before retrying.': 'feature.more.activityHistory.detail.messageRetryPrepared',
  'Private memory is excluded from AI context.': 'feature.more.activityHistory.detail.memoryPrivateExcluded',
  'Memory can improve future drafts.': 'feature.more.activityHistory.detail.memoryCanImproveDrafts',
  'This note is no longer available.': 'feature.more.activityHistory.detail.noteUnavailable',
  'The note remains searchable in recent memories.': 'feature.more.activityHistory.detail.memoryUnpinned',
  'Pinned notes appear first in Memory Vault.': 'feature.more.activityHistory.detail.memoryPinned',
  'The note was removed from this contact.': 'feature.more.activityHistory.detail.noteRemoved',
  'Contact could not be found.': 'feature.more.activityHistory.detail.contactMissing',
  'This enrichment prompt is no longer available.': 'feature.more.activityHistory.detail.enrichmentPromptUnavailable',
  'This gift record is no longer available.': 'feature.more.activityHistory.detail.giftUnavailable',
  'Contact priority was changed.': 'feature.more.activityHistory.detail.contactPriorityChanged',
  'Contact automation preference was changed.': 'feature.more.activityHistory.detail.contactAutomationChanged',
  'Choose a supported cadence.': 'feature.more.activityHistory.detail.unsupportedCadence',
  'Contact now inherits group preferences.': 'feature.more.activityHistory.detail.groupDefaultsApplied',
  'Check-in history was updated without creating or sending a message.':
    'feature.more.activityHistory.detail.contactMarkedContacted',
  'Email sender configuration changed.': 'feature.more.activityHistory.detail.emailSenderChanged',
  'Email delivery endpoint accepted the message.': 'feature.more.activityHistory.detail.emailProviderReady',
  'The approved email was sent by the configured provider.': 'feature.more.activityHistory.detail.emailSent',
  'Home is ready; setup gaps remain available from Settings and Setup Check.':
    'feature.more.activityHistory.detail.onboardingCompleted',
  'Provider sync was disconnected while local data was retained.':
    'feature.more.activityHistory.detail.accountDisconnected',
  'Manual WhatsApp handoff consent was revoked.': 'feature.more.activityHistory.detail.whatsappConsentRevoked',
  'Manual WhatsApp handoff consent was granted for approved handoff only.':
    'feature.more.activityHistory.detail.whatsappConsentGranted',
  'Reminder blackout window removed.': 'feature.more.activityHistory.detail.blackoutRemoved',
  'Backup file export completed.': 'feature.more.activityHistory.detail.backupCreated'
};

const activityPermissionDecisionLabel = (locale: SupportedLocale, decision: string) => {
  const keyByDecision: Record<string, TranslationKey> = {
    granted: 'feature.more.activityHistory.permission.granted',
    denied: 'feature.more.activityHistory.permission.denied',
    unavailable: 'feature.more.activityHistory.permission.unavailable',
    'not requested': 'feature.more.activityHistory.permission.notRequested'
  };
  return t(locale, keyByDecision[decision.toLowerCase()] ?? 'feature.more.activityHistory.permission.notRequested');
};

const activityComposerReasonFromText = (locale: SupportedLocale, value: string) =>
  manualComposerReasons.includes(value as ComposerReason)
    ? composerReasonLabel(locale, value as ComposerReason)
    : value;

const activityRelationshipGroupFromText = (locale: SupportedLocale, value: string) =>
  relationshipGroupOptions.includes(value as RelationshipGroup)
    ? relationshipGroupDisplayLabel(locale, value as RelationshipGroup)
    : value;

const activityAutomationModeFromText = (locale: SupportedLocale, value: string) =>
  automationModes.includes(value as AutomationMode)
    ? automationModeDisplayLabel(locale, value as AutomationMode)
    : value;

const activityLocaleFromText = (value: string) =>
  supportedLocales.includes(value as SupportedLocale) ? localeMetadata[value as SupportedLocale].label : value;

const activityTitleLabel = (locale: SupportedLocale, item: ActivityItem) => {
  const bulkMatch = item.title.match(/^Bulk (approve|reject|retry|revoke approval) (applied|partially applied)$/);
  if (bulkMatch) {
    const actionByText: Record<string, MessageBulkAction> = {
      approve: 'Approve',
      reject: 'Reject',
      retry: 'Retry',
      'revoke approval': 'Revoke approval'
    };
    return t(locale, 'feature.more.activityHistory.title.bulkAction', {
      action: messageBulkActionLabel(locale, actionByText[bulkMatch[1]]),
      status: t(
        locale,
        bulkMatch[2] === 'partially applied'
          ? 'feature.more.activityHistory.title.bulkPartiallyApplied'
          : 'feature.more.activityHistory.title.bulkApplied'
      )
    });
  }

  const permissionMatch = item.title.match(/^(.+) permission (not requested|granted|denied|unavailable)$/i);
  if (permissionMatch) {
    return t(locale, 'feature.more.activityHistory.title.permissionDecision', {
      capability: permissionMatch[1],
      decision: activityPermissionDecisionLabel(locale, permissionMatch[2])
    });
  }

  const key = activityTitleKeyByTitle[item.title];
  return key ? t(locale, key) : item.title;
};

const activityDetailLabel = (locale: SupportedLocale, item: ActivityItem) => {
  const exactKey = activityDetailKeyByDetail[item.detail];
  if (exactKey) {
    return t(locale, exactKey);
  }

  const draftReadyMatch = item.detail.match(/^(.+) draft is ready for review\.$/);
  if (draftReadyMatch) {
    return t(locale, 'feature.more.activityHistory.detail.draftReady', {
      reason: activityComposerReasonFromText(locale, draftReadyMatch[1])
    });
  }
  const draftFeedbackMatch = item.detail.match(/^(.+) draft was regenerated with feedback and is ready for review\.$/);
  if (draftFeedbackMatch) {
    return t(locale, 'feature.more.activityHistory.detail.draftReadyWithFeedback', {
      reason: activityComposerReasonFromText(locale, draftFeedbackMatch[1])
    });
  }
  const providerDraftMatch = item.detail.match(/^(.+) provider draft is ready for review\.$/);
  if (providerDraftMatch) {
    return t(locale, 'feature.more.activityHistory.detail.providerDraftReady', {
      reason: activityComposerReasonFromText(locale, providerDraftMatch[1])
    });
  }
  const providerFeedbackMatch = item.detail.match(/^(.+) provider draft used feedback guidance and is ready for review\.$/);
  if (providerFeedbackMatch) {
    return t(locale, 'feature.more.activityHistory.detail.providerDraftReadyWithFeedback', {
      reason: activityComposerReasonFromText(locale, providerFeedbackMatch[1])
    });
  }
  const templateMatch = item.detail.match(/^(.+) template is ready for review\.$/);
  if (templateMatch) {
    return t(locale, 'feature.more.activityHistory.detail.templateReady', {
      reason: activityComposerReasonFromText(locale, templateMatch[1])
    });
  }
  const wishReadyMatch = item.detail.match(/^(.+) wish for (.+) is ready for review\.$/);
  if (wishReadyMatch) {
    return t(locale, 'feature.more.activityHistory.detail.wishReadyForContact', {
      reason: activityComposerReasonFromText(locale, wishReadyMatch[1]),
      name: wishReadyMatch[2]
    });
  }
  const eventAddedMatch = item.detail.match(/^(.+) was added to Events\.$/);
  if (eventAddedMatch) {
    return t(locale, 'feature.more.activityHistory.detail.eventAdded', { label: eventAddedMatch[1] });
  }
  const eventSeparateMatch = item.detail.match(/^(.+) was kept as a separate event after review\.$/);
  if (eventSeparateMatch) {
    return t(locale, 'feature.more.activityHistory.detail.eventKeptSeparate', { label: eventSeparateMatch[1] });
  }
  const followUpMatch = item.detail.match(/^(.+) is ready in Events and reminders\.$/);
  if (followUpMatch) {
    return t(locale, 'feature.more.activityHistory.detail.followUpReady', { label: followUpMatch[1] });
  }
  const giftAddedMatch = item.detail.match(/^(.+) was added to gift history\.$/);
  if (giftAddedMatch) {
    return t(locale, 'feature.more.activityHistory.detail.giftAdded', { name: giftAddedMatch[1] });
  }
  const giftRemovedMatch = item.detail.match(/^(.+) was removed from gift history\.$/);
  if (giftRemovedMatch) {
    return t(locale, 'feature.more.activityHistory.detail.giftRemoved', { name: giftRemovedMatch[1] });
  }
  const profileMatch = item.detail.match(/^Profile updated for (.+?)\.(?: (\d+) unsent message\(s\) returned to review\.)?$/);
  if (profileMatch) {
    return t(
      locale,
      profileMatch[2]
        ? 'feature.more.activityHistory.detail.profileUpdatedWithReview'
        : 'feature.more.activityHistory.detail.profileUpdated',
      { name: profileMatch[1], count: profileMatch[2] }
    );
  }
  const groupMatch = item.detail.match(/^Contact moved to (.+)\.$/);
  if (groupMatch) {
    return t(locale, 'feature.more.activityHistory.detail.contactMovedGroup', {
      group: activityRelationshipGroupFromText(locale, groupMatch[1])
    });
  }
  const groupDefaultsMatch = item.detail.match(/^(.+) defaults changed\.$/);
  if (groupDefaultsMatch) {
    return t(locale, 'feature.more.activityHistory.detail.groupDefaultsChanged', {
      group: activityRelationshipGroupFromText(locale, groupDefaultsMatch[1])
    });
  }
  const cadenceMatch = item.detail.match(/^Cadence changed to (\d+) day\(s\)\.$/);
  if (cadenceMatch) {
    return t(locale, 'feature.more.activityHistory.detail.cadenceChanged', { days: cadenceMatch[1] });
  }
  const automationModeMatch = item.detail.match(/^(.+) selected for this contact\.$/);
  if (automationModeMatch) {
    return t(locale, 'feature.more.activityHistory.detail.automationModeSelected', {
      mode: activityAutomationModeFromText(locale, automationModeMatch[1])
    });
  }
  const globalAutomationModeChangedMatch = item.detail.match(
    /^Automation mode changed to (.+)\. (\d+) contact\(s\) changed effective automation mode\. (\d+) scheduled message\(s\) returned to review\.$/
  );
  if (globalAutomationModeChangedMatch) {
    return t(locale, 'feature.more.activityHistory.detail.globalAutomationModeChanged', {
      mode: activityAutomationModeFromText(locale, globalAutomationModeChangedMatch[1]),
      contactCount: globalAutomationModeChangedMatch[2],
      messageCount: globalAutomationModeChangedMatch[3]
    });
  }
  const globalAutomationModeRemainsMatch = item.detail.match(/^Automation mode remains (.+)\.$/);
  if (globalAutomationModeRemainsMatch) {
    return t(locale, 'feature.more.activityHistory.detail.globalAutomationModeRemains', {
      mode: activityAutomationModeFromText(locale, globalAutomationModeRemainsMatch[1])
    });
  }
  const snoozeMatch = item.detail.match(/^Reminder moved by (\d+) day\(s\)\.$/);
  if (snoozeMatch) {
    return t(locale, 'feature.more.activityHistory.detail.checkInSnoozed', { days: snoozeMatch[1] });
  }
  const localeMatch = item.detail.match(/^Locale changed to (.+)\.$/);
  if (localeMatch) {
    return t(locale, 'feature.more.activityHistory.detail.localeChanged', { locale: activityLocaleFromText(localeMatch[1]) });
  }
  const goalMatch = item.detail.match(/^Goal changed to (.+)\.$/);
  if (goalMatch) {
    return t(locale, 'feature.more.activityHistory.detail.goalChanged', {
      goal: onboardingGoals.includes(goalMatch[1] as OnboardingGoal)
        ? onboardingGoalLabel(locale, goalMatch[1] as OnboardingGoal)
        : goalMatch[1]
    });
  }
  const stepMatch = item.detail.match(/^(.+) can be completed later\.$/);
  if (stepMatch) {
    return t(locale, 'feature.more.activityHistory.detail.onboardingStepSkipped', { step: stepMatch[1] });
  }
  const calendarExportMatch = item.detail.match(/^(\d+) event\(s\) exported\.$/);
  if (calendarExportMatch) {
    return t(locale, 'feature.more.activityHistory.detail.calendarExported', { count: calendarExportMatch[1] });
  }
  const backupRestoredMatch = item.detail.match(/^(\d+) record\(s\) restored from the selected backup\.$/);
  if (backupRestoredMatch) {
    return t(locale, 'feature.more.activityHistory.detail.backupRestored', { count: backupRestoredMatch[1] });
  }

  return item.detail;
};

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
    <Text accessibilityRole="header" style={styles.sectionTitle}>{title}</Text>
    {detail ? <Text style={styles.sectionDetail}>{detail}</Text> : null}
  </View>
);

const App = () => {
  const [state, rawDispatch] = useReducer(relateReducer, undefined, createProductionInitialState);
  const [hydrated, setHydrated] = useState(false);
  const [sessionUnlocked, setSessionUnlocked] = useState(false);
  const [biometricCapability, setBiometricCapability] = useState({
    hardwareAvailable: false,
    enrolled: false
  });
  const persistenceCoordinatorRef = useRef<PersistenceCoordinator | undefined>(undefined);
  const emailRequestsInFlight = useRef(new Set<string>());
  const selectedContact = getContact(state.contacts, state.selectedContactId);
  const selectedMessage = state.messages.find(message => message.id === state.selectedMessageId);
  const locale = state.settings.locale;
  const stateRef = useRef(state);
  const navigationEntities: NavigationEntities = {
    contactIds: state.contacts.map(contact => contact.id),
    messages: state.messages.map(message => ({ id: message.id, contactId: message.contactId }))
  };
  const navigationEntitiesRef = useRef<NavigationEntities>(navigationEntities);
  navigationEntitiesRef.current = navigationEntities;
  const navigationStateRef = useRef(
    createNavigationState(
      {
        screen: state.activeScreen,
        contactId: state.selectedContactId,
        messageId: state.selectedMessageId
      },
      navigationEntities
    )
  );
  const browserHistoryDepthRef = useRef(0);

  const dispatchNavigationRoute = React.useCallback(
    (destination: NavigationDestination) => {
      rawDispatch({
        type: 'navigate',
        screen: destination.screen,
        contactId: destination.contactId,
        messageId: destination.messageId
      });
    },
    [rawDispatch]
  );

  const writeBrowserNavigationState = React.useCallback(
    (mode: 'push' | 'replace') => {
      if (Platform.OS !== 'web' || typeof window === 'undefined') {
        return false;
      }
      const historyState = buildBrowserNavigationHistoryState(
        window.history.state,
        navigationStateRef.current,
        browserHistoryDepthRef.current
      );
      try {
        if (mode === 'push') {
          window.history.pushState(historyState, '');
        } else {
          window.history.replaceState(historyState, '');
        }
        return true;
      } catch {
        appOperationalIssues.report({
          code: 'navigation-link-failed',
          severity: 'warning',
          summary: 'Browser navigation history could not be updated.',
          recovery: 'none'
        });
        return false;
      }
    },
    []
  );

  const commitNavigation = React.useCallback(
    (
      action: NavigationAction,
      options: { syncBrowser?: boolean; syncAppState?: boolean } = {}
    ) => {
      const transition = reduceNavigation(
        navigationStateRef.current,
        action,
        navigationEntitiesRef.current
      );
      navigationStateRef.current = transition.state;

      if (transition.outcome.changed && options.syncBrowser !== false) {
        if (action.type === 'push') {
          browserHistoryDepthRef.current += 1;
          if (!writeBrowserNavigationState('push')) {
            browserHistoryDepthRef.current -= 1;
          }
        } else {
          writeBrowserNavigationState('replace');
        }
      }

      const currentRoute = currentNavigationRoute(transition.state);
      const appStateMatchesRoute =
        stateRef.current.activeScreen === currentRoute.screen &&
        (!('contactId' in currentRoute) ||
          stateRef.current.selectedContactId === currentRoute.contactId) &&
        (!('messageId' in currentRoute) ||
          stateRef.current.selectedMessageId === currentRoute.messageId);
      if (
        options.syncAppState !== false &&
        (transition.outcome.changed || !appStateMatchesRoute)
      ) {
        dispatchNavigationRoute(currentRoute);
      }
      return transition;
    },
    [dispatchNavigationRoute, writeBrowserNavigationState]
  );

  const dispatch = React.useCallback<React.Dispatch<RelateAction>>(
    action => {
      if (action.type === 'hydrate') {
        const hydratedEntities: NavigationEntities = {
          contactIds: action.state.contacts.map(contact => contact.id),
          messages: action.state.messages.map(message => ({
            id: message.id,
            contactId: message.contactId
          }))
        };
        navigationEntitiesRef.current = hydratedEntities;
        navigationStateRef.current = createNavigationState(
          {
            screen: action.state.activeScreen,
            contactId: action.state.selectedContactId,
            messageId: action.state.selectedMessageId
          },
          hydratedEntities
        );
        browserHistoryDepthRef.current = 0;
        writeBrowserNavigationState('replace');
        rawDispatch(action);
        return;
      }
      if (action.type === 'navigate') {
        commitNavigation({
          type: preservesNavigationOrigin(action.screen) ? 'push' : 'replace',
          destination: action
        });
        return;
      }
      rawDispatch(action);
    },
    [commitNavigation, rawDispatch, writeBrowserNavigationState]
  );

  if (!persistenceCoordinatorRef.current) {
    persistenceCoordinatorRef.current = new PersistenceCoordinator({
      save: stateToSave => saveState(secureStateStore, stateToSave),
      inspect: () => inspectPersistedState(secureStateStore),
      nowIso: () => new Date().toISOString()
    });
  }

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  useEffect(() => {
    const entities = navigationEntitiesRef.current;
    const reconciled = reduceNavigation(
      navigationStateRef.current,
      { type: 'reconcile' },
      entities
    );
    let nextNavigationState = reconciled.state;
    let navigationChanged = reconciled.outcome.changed;
    let browserHistoryMode: 'push' | 'replace' = 'replace';
    const desiredDestination: NavigationDestination = {
      screen: state.activeScreen,
      contactId: state.selectedContactId,
      messageId: state.selectedMessageId
    };
    const desiredRoute = resolveNavigationDestination(desiredDestination, entities).route;

    const routeBeforeStateSync = currentNavigationRoute(nextNavigationState);
    if (!navigationRouteEquals(routeBeforeStateSync, desiredRoute)) {
      const transitionType =
        preservesNavigationOrigin(desiredRoute.screen) &&
        routeBeforeStateSync.screen !== desiredRoute.screen
          ? 'push'
          : 'replace';
      const replaced = reduceNavigation(
        nextNavigationState,
        { type: transitionType, destination: desiredDestination },
        entities
      );
      nextNavigationState = replaced.state;
      navigationChanged = navigationChanged || replaced.outcome.changed;
      if (transitionType === 'push' && replaced.outcome.changed) {
        browserHistoryMode = 'push';
      }
    }

    navigationStateRef.current = nextNavigationState;
    if (navigationChanged) {
      if (browserHistoryMode === 'push') {
        browserHistoryDepthRef.current += 1;
        if (!writeBrowserNavigationState('push')) {
          browserHistoryDepthRef.current -= 1;
        }
      } else {
        writeBrowserNavigationState('replace');
      }
    }

    const currentRoute = currentNavigationRoute(nextNavigationState);
    const appStateMatchesRoute =
      state.activeScreen === currentRoute.screen &&
      (!('contactId' in currentRoute) || state.selectedContactId === currentRoute.contactId) &&
      (!('messageId' in currentRoute) || state.selectedMessageId === currentRoute.messageId);
    if (!appStateMatchesRoute) {
      dispatchNavigationRoute(currentRoute);
    }
  }, [
    dispatchNavigationRoute,
    state.activeScreen,
    state.contacts,
    state.messages,
    state.selectedContactId,
    state.selectedMessageId,
    writeBrowserNavigationState
  ]);

  useEffect(() => {
    if (Platform.OS !== 'android') {
      return;
    }
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      const transition = commitNavigation({
        type: 'back',
        source: 'android-hardware'
      });
      return transition.outcome.back?.disposition === 'consumed';
    });
    return () => subscription.remove();
  }, [commitNavigation]);

  useEffect(() => {
    if (Platform.OS !== 'web' || typeof window === 'undefined') {
      return;
    }

    writeBrowserNavigationState('replace');
    const handleBrowserHistory = (event: PopStateEvent) => {
      const snapshot = readBrowserNavigationHistoryState(
        event.state,
        navigationEntitiesRef.current
      );
      if (snapshot) {
        browserHistoryDepthRef.current = snapshot.depth;
        navigationStateRef.current = snapshot.navigation;
        dispatchNavigationRoute(currentNavigationRoute(snapshot.navigation));
        return;
      }
      commitNavigation(
        { type: 'back', source: 'browser-history' },
        { syncBrowser: false }
      );
    };
    window.addEventListener('popstate', handleBrowserHistory);
    return () => window.removeEventListener('popstate', handleBrowserHistory);
  }, [commitNavigation, dispatchNavigationRoute, writeBrowserNavigationState]);

  useEffect(() => {
    let active = true;
    loadStateWithRecovery(secureStateStore)
      .then(result => {
        if (!active) {
          return;
        }
        if (result.status === 'loaded') {
          dispatch({ type: 'hydrate', state: result.state });
          appOperationalIssues.resolveCode('storage-unavailable');
        }
        if (result.status === 'recovered') {
          dispatch({
            type: 'persistenceError',
            message: `Saved data was recovered after a storage issue: ${result.reason}`
          });
          appOperationalIssues.report({
            code: 'persistence-failed',
            severity: 'blocking',
            summary: 'Stored data required selective recovery. Review the recovery summary before continuing.',
            recovery: 'retry'
          });
        }
      })
      .catch(error => {
        if (active) {
          appOperationalIssues.report({
            code: 'storage-unavailable',
            severity: 'blocking',
            summary: 'Protected local storage could not be opened.',
            recovery: 'retry'
          });
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
    const subscription = NativeAppState.addEventListener('change', nextState => {
      if (nextState !== 'active') {
        setSessionUnlocked(false);
      }
    });
    return () => subscription.remove();
  }, []);

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
    persistenceCoordinatorRef.current
      ?.schedule(stateForPersistence)
      .then(result => {
        if (result.status === 'persisted') {
          appOperationalIssues.resolveCode('persistence-failed');
          dispatch({ type: 'persistenceSaved', savedAt: result.savedAt, storageHealth: result.storageHealth });
        }
      })
      .catch(error => {
        appOperationalIssues.report({
          code: 'persistence-failed',
          severity: 'blocking',
          summary: 'The latest local changes could not be verified in protected storage.',
          recovery: 'retry'
        });
        dispatch({
          type: 'persistenceError',
          message: error instanceof Error ? error.message : 'State could not be saved.'
        });
      });
  }, [hydrated, state]);

  useEffect(() => {
    if (!hydrated) {
      return;
    }
    syncHomeWidgetSummary(buildLocalizedHomeWidgetSummary(state)).catch(() => {
      appOperationalIssues.report({
        code: 'widget-sync-failed',
        severity: 'warning',
        summary: 'The home widget is not synchronized with the latest verified local state.',
        recovery: 'reconcile'
      });
    });
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
        showAppAlert(t(stateRef.current.settings.locale, 'feedback.linkOpened'), resolution.message);
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
      appOperationalIssues.report({
        code: 'navigation-link-failed',
        severity: 'warning',
        summary: 'Notification response history is unavailable on this platform.',
        recovery: 'none'
      });
    }

    Linking.getInitialURL()
      .then(url => {
        if (active) {
          openDeepLink(url);
        }
      })
      .catch(() => {
        appOperationalIssues.report({
          code: 'navigation-link-failed',
          severity: 'warning',
          summary: 'The initial navigation link could not be read.',
          recovery: 'none'
        });
      });

    return () => {
      active = false;
      subscription.remove();
      notificationSubscription.remove();
    };
  }, [hydrated]);

  const handleManualHandoff = async (message: MessageDraft) => {
    const contact = getContact(state.contacts, message.contactId);
    const target = buildHandoffTarget(contact, message);
    const confirmSent = () => {
      showAppAlert(target.completionTitle, target.completionMessage, [
        { text: target.dismissLabel, style: 'cancel' },
        { text: target.markSentLabel, onPress: () => dispatch({ type: 'manualHandoff', messageId: message.id }) }
      ]);
    };
    const runHandoff = async (preferFallback = false) => {
      const result = await openManualHandoffTarget({
        target,
        body: message.body,
        contactName: contact?.name,
        preferFallback
      });
      if (result.needsSentConfirmation) {
        confirmSent();
        return;
      }
      if (result.outcome === 'failed') {
        showAppAlert(t(locale, 'feedback.manualHandoffFailedTitle'), t(locale, 'feedback.manualHandoffFailedMessage'));
      }
    };
    const detail = [target.privacyNote, target.reason].filter(Boolean).join('\n\n');
    const actions = target.url
      ? [
          { text: t(locale, 'action.cancel'), style: 'cancel' as const },
          { text: target.fallbackLabel, onPress: () => runHandoff(true) },
          { text: target.label, onPress: () => runHandoff() }
        ]
      : [
          { text: t(locale, 'action.cancel'), style: 'cancel' as const },
          { text: target.fallbackLabel, onPress: () => runHandoff(true) }
        ];
    showAppAlert(t(locale, 'feedback.manualHandoffTitle'), detail, actions);
  };

  const handleSendEmail = async (message: MessageDraft) => {
    if (emailRequestsInFlight.current.has(message.id)) {
      return;
    }
    const request = buildEmailDeliveryRequest(state, message.id);
    if (!request.ok) {
      dispatch({ type: 'emailProviderFailure', error: request.error, messageId: message.id });
      showAppAlert(t(locale, 'feedback.emailNotSentTitle'), request.error.message);
      return;
    }

    emailRequestsInFlight.current.add(message.id);
    try {
      const result = await sendEmailMessage(request.request);
      if (result.ok) {
        if (result.status === 'accepted' && result.deliveryId) {
          dispatch({
            type: 'emailDeliveryAccepted',
            messageId: message.id,
            idempotencyKey: request.request.idempotencyKey,
            deliveryId: result.deliveryId
          });
          return;
        }
        dispatch({
          type: 'emailSent',
          messageId: message.id,
          idempotencyKey: request.request.idempotencyKey,
          deliveryId: result.deliveryId
        });
        showAppAlert(t(locale, 'feedback.emailSentTitle'), t(locale, 'feedback.emailSentMessage'));
        return;
      }

      if (result.outcome === 'unknown') {
        dispatch({
          type: 'emailDeliveryUnknown',
          error: result.error,
          messageId: message.id,
          idempotencyKey: result.idempotencyKey
        });
      } else {
        dispatch({ type: 'emailProviderFailure', error: result.error, messageId: message.id });
      }
      showAppAlert(
        t(locale, 'feedback.emailNotSentTitle'),
        t(locale, 'feedback.emailHandoffFallback', { message: result.error.message })
      );
    } finally {
      emailRequestsInFlight.current.delete(message.id);
    }
  };

  const handleGenerateMessage = async (
    contactId: string,
    eventId: string | undefined,
    reason: ComposerReason,
    contextOptions: AiDraftContextOptions = {}
  ) => {
    const request = buildAiDraftRequest(state, contactId, eventId, reason, contextOptions);
    if (!request.ok) {
      dispatch({
        type: 'generateMessage',
        contactId,
        eventId,
        reason,
        fallbackReason: request.error.message,
        excludedMemoryIds: contextOptions.excludedMemoryIds,
        includePriorMessages: contextOptions.includePriorMessages,
        feedback: contextOptions.feedback
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
        privacySummary: request.privacySummary,
        observation: result.observation,
        feedback: contextOptions.feedback
      });
      return;
    }

    dispatch({ type: 'aiProviderFailure', error: result.error, privacySummary: request.privacySummary, observation: result.observation });
    dispatch({
      type: 'generateMessage',
      contactId,
      eventId,
      reason,
      fallbackReason: result.error.message,
      excludedMemoryIds: contextOptions.excludedMemoryIds,
      includePriorMessages: contextOptions.includePriorMessages,
      feedback: contextOptions.feedback
    });
  };

  const handleTestAiProvider = async () => {
    const contact = state.contacts[0];
    if (!contact) {
      showAppAlert(t(locale, 'feedback.aiProviderTestFailedTitle'), t(locale, 'feedback.aiProviderMissingContact'));
      return;
    }

    const request = buildAiDraftRequest(state, contact.id, undefined, 'Check-in');
    if (!request.ok) {
      dispatch({ type: 'aiProviderFailure', error: request.error });
      showAppAlert(t(locale, 'feedback.aiProviderTestFailedTitle'), request.error.message);
      return;
    }

    const result = await requestAiDraft(request.request);
    if (result.ok) {
      dispatch({ type: 'aiProviderReady', privacySummary: request.privacySummary, observation: result.observation });
      showAppAlert(t(locale, 'feedback.aiProviderReadyTitle'), request.privacySummary);
    } else {
      dispatch({ type: 'aiProviderFailure', error: result.error, privacySummary: request.privacySummary, observation: result.observation });
      showAppAlert(t(locale, 'feedback.aiProviderTestFailedTitle'), result.error.message);
    }
  };

  const handleExportBackup = async (passphrase: string) => {
    try {
      const result = await exportEncryptedBackupFile(state, passphrase);
      dispatch({ type: 'recordPermissionDecision', capability: 'Backup export', decision: 'Granted' });
      dispatch({ type: 'createBackup' });
      showAppAlert(
        t(locale, 'feedback.backupExportedTitle'),
        t(
          locale,
          result.shared ? 'feedback.backupExportedMessageShared' : 'feedback.backupExportedMessageSaved',
          {
            count: tc(locale, result.preview.recordCount, {
              one: 'common.count.record.one',
              other: 'common.count.record.other'
            }),
            uri: result.uri ?? ''
          }
        )
      );
    } catch (error) {
      showAppAlert(
        t(locale, 'feedback.backupExportFailedTitle'),
        error instanceof Error ? error.message : t(locale, 'feedback.backupExportFailedFallback')
      );
    }
  };

  const handlePickBackup = async () => {
    try {
      return await pickEncryptedBackupFile();
    } catch (error) {
      showAppAlert(
        t(locale, 'feedback.backupImportFailedTitle'),
        error instanceof Error ? error.message : t(locale, 'feedback.backupImportFailedFallback')
      );
      return undefined;
    }
  };

  const dataLifecycleDependencies = (): DataLifecycleDependencies => ({
    store: secureStateStore,
    nowIso: () => new Date().toISOString(),
    createId: () =>
      globalThis.crypto?.randomUUID?.() ??
      `lifecycle-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    cancelOwnedReminders: () => cancelOwnedReminderNotifications(),
    clearHomeWidget: () => clearHomeWidgetSummary(),
    cleanupTemporaryBackups: async () => undefined,
    reconcileReminders: plans =>
      plans.length > 0 ? scheduleReminderPlans(plans) : cancelOwnedReminderNotifications(),
    syncHomeWidget: lifecycleState =>
      syncHomeWidgetSummary(buildLocalizedHomeWidgetSummary(lifecycleState))
  });

  const handleClearLocalData = async () => {
    try {
      await persistenceCoordinatorRef.current?.flush();
      const clearedState = await clearLocalDataTransaction(dataLifecycleDependencies(), stateRef.current);
      persistenceCoordinatorRef.current?.reset(JSON.stringify(clearedState));
      dispatch({ type: 'hydrate', state: clearedState });
      setSessionUnlocked(false);
    } catch (error) {
      showAppAlert(
        t(locale, 'feature.more.account.clearLocalData'),
        error instanceof Error ? error.message : 'Local data could not be cleared and was not reported as complete.'
      );
    }
  };

  const handleRestoreBackup = async (raw: string, passphrase: string, recordCount: number) => {
    try {
      const restoredState = await restoreEncryptedBackupFile(raw, passphrase);
      await persistenceCoordinatorRef.current?.flush();
      const result = await restoreLocalDataTransaction(dataLifecycleDependencies(), restoredState);
      persistenceCoordinatorRef.current?.reset(JSON.stringify(result.state));
      dispatch({ type: 'hydrate', state: result.state });
      if (result.status === 'reconciliation-required') {
        showAppAlert(t(locale, 'feedback.backupRestoreFailedTitle'), result.message);
        return;
      }
      showAppAlert(
        t(locale, 'feedback.backupRestoredTitle'),
        t(locale, 'feedback.backupRestoredMessage', {
          count: tc(locale, recordCount, {
            one: 'common.count.record.one',
            other: 'common.count.record.other'
          })
        })
      );
    } catch (error) {
      showAppAlert(
        t(locale, 'feedback.backupRestoreFailedTitle'),
        error instanceof Error ? error.message : t(locale, 'feedback.backupRestoreFailedFallback')
      );
    }
  };

  const handleUnlock = async () => {
    try {
      if (await authenticateWithBiometrics()) {
        setSessionUnlocked(true);
      }
    } catch {
      showAppAlert(t(locale, 'feedback.unlockFailedTitle'), t(locale, 'feedback.unlockFailedMessage'));
    }
  };

  const handleImportDeviceContacts = async () => {
    try {
      const records = await importDeviceContacts();
      dispatch({ type: 'recordPermissionDecision', capability: 'Contacts', decision: 'Granted' });
      dispatch({ type: 'importContacts', records });
    } catch (error) {
      const message = error instanceof Error ? error.message : t(locale, 'feedback.contactImportFailedFallback');
      if (/permission/i.test(message)) {
        dispatch({ type: 'recordPermissionDecision', capability: 'Contacts', decision: 'Denied' });
      }
      showAppAlert(t(locale, 'feedback.contactImportFailedTitle'), message);
    }
  };

  const handleScheduleReminders = async () => {
    const plannedState = relateReducer(state, { type: 'planReminders' });
    dispatch({ type: 'planReminders' });
    if (plannedState.reminderPlans.length === 0) {
      showAppAlert(
        t(locale, 'feedback.remindersNeedSetupTitle'),
        t(locale, 'feedback.remindersNeedSetupMessage')
      );
      return;
    }
    try {
      const result = await scheduleReminderPlans(plannedState.reminderPlans);
      dispatch({ type: 'recordPermissionDecision', capability: 'Notifications', decision: 'Granted' });
      showAppAlert(
        t(locale, 'feedback.remindersScheduledTitle'),
        t(locale, 'feedback.remindersScheduledMessage', {
          scheduled: result.scheduled,
          skipped: result.skipped
        })
      );
    } catch (error) {
      const message = error instanceof Error ? error.message : t(locale, 'feedback.reminderSchedulingFailedFallback');
      if (/permission/i.test(message)) {
        dispatch({ type: 'recordPermissionDecision', capability: 'Notifications', decision: 'Denied' });
      }
      showAppAlert(t(locale, 'feedback.reminderSchedulingFailedTitle'), message);
    }
  };

  const handleExportCalendar = async () => {
    try {
      const count = await exportEventsToDeviceCalendar(state);
      dispatch({ type: 'recordPermissionDecision', capability: 'Calendar', decision: 'Granted' });
      dispatch({ type: 'calendarExported', count });
      showAppAlert(
        t(locale, 'feedback.calendarExportCompleteTitle'),
        t(locale, 'feedback.calendarExportCompleteMessage', { count })
      );
    } catch (error) {
      const message = error instanceof Error ? error.message : t(locale, 'feedback.calendarExportFailedFallback');
      if (/permission/i.test(message)) {
        dispatch({ type: 'recordPermissionDecision', capability: 'Calendar', decision: 'Denied' });
      }
      dispatch({ type: 'calendarError', message });
      showAppAlert(t(locale, 'feedback.calendarExportFailedTitle'), message);
    }
  };

  const handleImportCalendar = async () => {
    try {
      const candidates = await importEventsFromDeviceCalendar();
      dispatch({ type: 'recordPermissionDecision', capability: 'Calendar', decision: 'Granted' });
      dispatch({ type: 'calendarImported', candidates });
      showAppAlert(
        t(locale, 'feedback.calendarImportCompleteTitle'),
        t(locale, 'feedback.calendarImportCompleteMessage', { count: candidates.length })
      );
    } catch (error) {
      const message = error instanceof Error ? error.message : t(locale, 'feedback.calendarImportFailedFallback');
      if (/permission/i.test(message)) {
        dispatch({ type: 'recordPermissionDecision', capability: 'Calendar', decision: 'Denied' });
      }
      dispatch({ type: 'calendarError', message });
      showAppAlert(t(locale, 'feedback.calendarImportFailedTitle'), message);
    }
  };

  const routeBack = () => {
    if (
      Platform.OS === 'web' &&
      typeof window !== 'undefined' &&
      browserHistoryDepthRef.current > 0
    ) {
      try {
        window.history.back();
        return;
      } catch {
        // Fall through to the in-app stack when browser history is unavailable.
      }
    }
    commitNavigation({ type: 'back', source: 'ui' });
  };

  const lockDecision = resolveBiometricLock({
    enabled: state.settings.biometricLockEnabled,
    hardwareAvailable: biometricCapability.hardwareAvailable,
    enrolled: biometricCapability.enrolled,
    sessionUnlocked
  });

  if (!hydrated) {
    return (
      <SafeAreaView style={styles.safe}>
        <StatusBar barStyle="dark-content" />
        <View accessibilityLiveRegion="polite" style={styles.lockScreen}>
          <Text accessibilityRole="header" style={styles.appName}>RelateAI</Text>
          <Text style={styles.bodyText}>{t(locale, 'feature.more.persistence.loading')}</Text>
        </View>
      </SafeAreaView>
    );
  }

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
            <Button label={t(locale, 'action.back')} tone="ghost" onPress={routeBack} />
          ) : null}
        </View>

        {lockDecision.state !== 'unlocked' ? (
          <LockScreen
            decision={lockDecision}
            locale={locale}
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
              {state.activeScreen === 'onboarding' ? (
                <OnboardingScreen
                  state={state}
                  dispatch={dispatch}
                  onManualHandoff={handleManualHandoff}
                  onSendEmail={handleSendEmail}
                  onImportDeviceContacts={handleImportDeviceContacts}
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
              {state.activeScreen === 'home' ? (
                <HomeScreen
                  state={state}
                  dispatch={dispatch}
                  onManualHandoff={handleManualHandoff}
                  onSendEmail={handleSendEmail}
                  onImportDeviceContacts={handleImportDeviceContacts}
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
                  onScheduleReminders={handleScheduleReminders}
                  onExportCalendar={handleExportCalendar}
                  onImportCalendar={handleImportCalendar}
                  onGenerateMessage={handleGenerateMessage}
                  onTestAiProvider={handleTestAiProvider}
                  onExportBackup={handleExportBackup}
                  onPickBackup={handlePickBackup}
                  onRestoreBackup={handleRestoreBackup}
                  onClearLocalData={handleClearLocalData}
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
      <AppDialogHost controller={appDialogController} />
    </SafeAreaView>
  );
};

const LockScreen = ({
  decision,
  locale,
  onUnlock,
  onDisable
}: {
  decision: ReturnType<typeof resolveBiometricLock>;
  locale: SupportedLocale;
  onUnlock: () => void;
  onDisable: () => void;
}) => (
  <View style={styles.lockScreen}>
    <Card>
      <Text style={styles.cardTitle}>{t(locale, 'feature.lock.title')}</Text>
      {decision.state === 'locked' ? (
        <>
          <Text style={styles.bodyText}>{t(locale, 'feature.lock.lockedDetail')}</Text>
          <Button label={t(locale, 'action.unlock')} onPress={onUnlock} />
        </>
      ) : (
        <>
          <Text style={styles.warningText}>{t(locale, 'feature.lock.unavailableDetail')}</Text>
          <Text style={styles.bodyText}>
            {t(locale, 'feature.lock.reason', {
              reason: t(
                locale,
                decision.reason === 'no-hardware' ? 'feature.lock.noHardware' : 'feature.lock.noEnrollment'
              )
            })}
          </Text>
          <Button label={t(locale, 'feature.lock.disable')} tone="secondary" onPress={onDisable} />
        </>
      )}
    </Card>
  </View>
);

const OnboardingScreen = ({ state, dispatch }: ScreenProps) => {
  const plan = buildOnboardingPlan(state);
  const current = plan.nextStep;
  const locale = state.settings.locale;

  return (
    <View>
      <SectionTitle title={t(locale, 'feature.onboarding.title')} detail={t(locale, 'feature.onboarding.detail')} />
      <Card>
        <Text style={styles.cardTitle}>{current.title}</Text>
        <Text style={styles.bodyText}>{current.purpose}</Text>
        <Text style={styles.smallText}>{plan.summary}</Text>
        <Text style={styles.smallText}>{t(locale, 'feature.onboarding.goal')}</Text>
        <View style={styles.wrapRow}>
          {onboardingGoals.map(goal => (
            <Pill
              key={goal}
              label={onboardingGoalLabel(locale, goal)}
              selected={state.onboarding.selectedGoal === goal}
              onPress={() => dispatch({ type: 'setOnboardingGoal', goal })}
            />
          ))}
        </View>
        {current.id === 'account' ? (
          <View style={styles.wrapRow}>
            <Pill
              label={t(locale, 'feature.onboarding.account.local')}
              selected={state.settings.accountMode === 'Local'}
              onPress={() => dispatch({ type: 'setAccountMode', mode: 'Local' })}
            />
            <Pill
              label={t(locale, 'feature.onboarding.account.googleSync')}
              selected={state.settings.accountMode === 'Google sync'}
              onPress={() => dispatch({ type: 'setAccountMode', mode: 'Google sync' })}
            />
          </View>
        ) : null}
        {current.targetScreen ? (
          <Button
            label={current.actionLabel}
            tone="secondary"
            onPress={() => dispatch({ type: 'navigate', screen: current.targetScreen! })}
          />
        ) : null}
        <View style={styles.actionRow}>
          <Button
            label={
              current.id === 'finish'
                ? t(locale, 'feature.onboarding.action.finishSetup')
                : t(locale, 'feature.onboarding.action.continue')
            }
            onPress={() =>
              dispatch(current.id === 'finish' ? { type: 'completeOnboarding' } : { type: 'advanceOnboarding' })
            }
          />
          {current.status !== 'Ready' ? (
            <Button
              label={t(locale, 'feature.onboarding.action.skipForNow')}
              tone="secondary"
              onPress={() => dispatch({ type: 'skipOnboardingStep', stepId: current.id })}
            />
          ) : null}
          <Button
            label={t(locale, 'feature.onboarding.action.goHome')}
            tone="ghost"
            onPress={() => dispatch({ type: 'completeOnboarding' })}
          />
        </View>
      </Card>

      <SectionTitle title={t(locale, 'feature.onboarding.setupPath.title')} detail={t(locale, 'feature.onboarding.setupPath.detail')} />
      {plan.steps.map(step => {
        const statusLabel = setupStatusDisplayLabel(locale, step.status);
        return (
          <TouchableOpacity
            key={step.id}
            accessibilityRole="button"
            accessibilityLabel={`${step.title}: ${statusLabel}`}
            onPress={() => dispatch({ type: 'setOnboardingStep', stepId: step.id })}
          >
            <Card>
              <View style={styles.rowBetween}>
                <Text style={styles.cardTitle}>{step.title}</Text>
                <Pill label={statusLabel} selected={step.id === state.onboarding.currentStepId} />
              </View>
              <Text style={styles.smallText}>{step.purpose}</Text>
            </Card>
          </TouchableOpacity>
        );
      })}
    </View>
  );
};

const HomeScreen = ({
  state,
  dispatch,
  onManualHandoff,
  onSendEmail,
  onGenerateMessage,
  onScheduleReminders
}: ScreenProps) => {
  const pending = state.messages.filter(message => message.status === 'Needs review');
  const upcoming = [...state.events].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
  const checkInQueue = buildCheckInReminderQueue(state);
  const firstCheckIn = checkInQueue.due[0];
  const widgetSummary = buildLocalizedHomeWidgetSummary(state);
  const locale = state.settings.locale;
  const checkInEmptyMessage = checkInQueueEmptyMessage(locale, checkInQueue, state.contacts.length);
  const nextAction = pending[0]
    ? {
        title: t(locale, 'feature.home.next.reviewTitle'),
        detail: t(locale, 'feature.home.next.reviewDetail', {
          name: getContact(state.contacts, pending[0].contactId)?.name ?? t(locale, 'common.contactFallback')
        }),
        action: () => dispatch({ type: 'navigate', screen: 'wishPreview', messageId: pending[0].id })
      }
    : firstCheckIn
      ? {
          title: checkInReminderTitle(locale, firstCheckIn),
          detail: checkInReminderDetail(locale, firstCheckIn),
          action: () => dispatch({ type: 'navigate', screen: 'manualComposer', contactId: firstCheckIn.contactId })
        }
      : {
          title: t(locale, 'feature.home.next.prepareTitle'),
          detail: t(locale, 'feature.home.next.prepareDetail'),
          action: () => dispatch({ type: 'navigate', screen: 'events' })
        };

  return (
    <View>
      <SectionTitle title={t(locale, 'feature.home.title')} detail={t(locale, 'feature.home.detail')} />
      <View style={styles.statGrid}>
        <Metric label={t(locale, 'feature.home.metrics.contacts')} value={String(state.contacts.length)} />
        <Metric label={t(locale, 'feature.home.metrics.upcoming')} value={String(upcoming.length)} />
        <Metric label={t(locale, 'feature.home.metrics.review')} value={String(pending.length)} />
        <Metric label={t(locale, 'feature.home.metrics.backups')} value={String(state.backups.length)} />
      </View>

      <Card>
        <Text style={styles.cardTitle}>{nextAction.title}</Text>
        <Text style={styles.bodyText}>{nextAction.detail}</Text>
        <Button label={t(locale, 'action.start')} onPress={nextAction.action} />
      </Card>

      <Card>
        <View style={styles.rowBetween}>
          <View style={styles.flex}>
            <Text style={styles.cardTitle}>{t(locale, 'feature.home.checkIns.title')}</Text>
            <Text style={styles.bodyText}>{checkInQueueSummary(locale, checkInQueue)}</Text>
          </View>
          <Pill
            label={t(locale, 'feature.home.checkIns.dueCount', { count: checkInQueue.due.length })}
            selected={checkInQueue.due.length > 0}
          />
        </View>
        {checkInEmptyMessage ? <Text style={styles.smallText}>{checkInEmptyMessage}</Text> : null}
        {checkInQueue.due.slice(0, 3).map(reminder => (
          <View key={reminder.contactId} style={styles.inlineItem}>
            <View style={styles.rowBetween}>
              <View style={styles.flex}>
                <Text style={styles.bodyText}>{checkInReminderTitle(locale, reminder)}</Text>
                <Text style={styles.smallText}>{checkInReminderDetail(locale, reminder)}</Text>
              </View>
              <Pill label={checkInCadenceLabel(locale, reminder.cadenceDays)} selected />
            </View>
            <View style={styles.actionRow}>
              <Button
                label={checkInReminderPrimaryActionLabel(locale, reminder)}
                onPress={() => dispatch({ type: 'navigate', screen: 'manualComposer', contactId: reminder.contactId })}
              />
              <Button
                label={t(locale, 'feature.home.checkIns.snooze14d')}
                tone="secondary"
                onPress={() => dispatch({ type: 'snoozeCheckIn', contactId: reminder.contactId, days: 14 })}
              />
              <Button
                label={checkInReminderSecondaryActionLabel(locale)}
                tone="ghost"
                onPress={() => dispatch({ type: 'markContactedElsewhere', contactId: reminder.contactId })}
              />
            </View>
          </View>
        ))}
        {checkInQueue.snoozed.slice(0, 2).map(reminder => (
          <View key={`snoozed-${reminder.contactId}`} style={styles.inlineItem}>
            <Text style={styles.bodyText}>{checkInReminderTitle(locale, reminder)}</Text>
            <Text style={styles.smallText}>{checkInReminderDetail(locale, reminder)}</Text>
            <Button
              label={checkInReminderPrimaryActionLabel(locale, reminder)}
              tone="secondary"
              onPress={() => dispatch({ type: 'navigate', screen: 'manualComposer', contactId: reminder.contactId })}
            />
          </View>
        ))}
      </Card>

      <Card>
        <View style={styles.rowBetween}>
          <View style={styles.flex}>
            <Text style={styles.cardTitle}>{t(locale, 'feature.home.widget.title')}</Text>
            <Text style={styles.bodyText}>{widgetSummary.subtitle}</Text>
          </View>
          <Pill
            label={tc(locale, widgetSummary.tiles.length, {
              one: 'common.count.tile.one',
              other: 'common.count.tile.other'
            })}
            selected={widgetSummary.tiles.length > 0}
          />
        </View>
        <Text style={styles.smallText}>{widgetSummary.privacyNote}</Text>
        {widgetSummary.emptyState ? <Text style={styles.smallText}>{widgetSummary.emptyState}</Text> : null}
        {widgetSummary.tiles.map(tile => (
          <View key={tile.id} style={styles.inlineItem}>
            <View style={styles.rowBetween}>
              <View style={styles.flex}>
                <Text style={styles.bodyText}>{tile.title}</Text>
                <Text style={styles.smallText}>{tile.detail}</Text>
              </View>
              <Pill label={String(tile.count)} selected />
            </View>
            <Button
              label={t(locale, 'action.open')}
              tone="secondary"
              accessibilityLabel={tile.accessibilityLabel}
              onPress={() =>
                dispatch({
                  type: 'navigate',
                  screen: tile.route.screen,
                  contactId: tile.route.contactId,
                  messageId: tile.route.messageId
                })
              }
            />
          </View>
        ))}
      </Card>

      {!state.onboarding.completed ? (
        <Card>
          <Text style={styles.cardTitle}>{t(locale, 'feature.home.setup.title')}</Text>
          <Text style={styles.bodyText}>{t(locale, 'feature.home.setup.detail')}</Text>
          <Button
            label={t(locale, 'feature.home.setup.openOnboarding')}
            tone="secondary"
            onPress={() => dispatch({ type: 'reopenOnboarding' })}
          />
        </Card>
      ) : null}

      <SectionTitle title={t(locale, 'feature.home.upcoming.title')} detail={t(locale, 'feature.home.upcoming.detail')} />
      {upcoming.slice(0, 3).map(event => (
        <EventCard
          key={event.id}
          event={event}
          state={state}
          dispatch={dispatch}
          onManualHandoff={onManualHandoff}
          onSendEmail={onSendEmail}
          onGenerateMessage={onGenerateMessage}
          onScheduleReminders={onScheduleReminders}
        />
      ))}
    </View>
  );
};

const EventsScreen = ({
  state,
  dispatch,
  onManualHandoff,
  onSendEmail,
  onGenerateMessage,
  onScheduleReminders
}: ScreenProps) => {
  const [viewMode, setViewMode] = useState<'List' | 'Month'>('List');
  const [typeFilter, setTypeFilter] = useState<EventTypeFilter>('All');
  const [timeFilter, setTimeFilter] = useState<EventTimeFilter>('Upcoming');
  const [monthIso, setMonthIso] = useState(new Date().toISOString());
  const [showAdvancedEventTypes, setShowAdvancedEventTypes] = useState(false);
  const visibleEventTypeFilters = showAdvancedEventTypes
    ? [...primaryEventTypeFilters, ...advancedEventTypeFilters]
    : primaryEventTypeFilters;
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
      <SectionTitle
        title={t(state.settings.locale, 'feature.events.title')}
        detail={t(state.settings.locale, 'feature.events.detail')}
      />
      <Card>
        <Text style={styles.bodyText}>{t(state.settings.locale, 'feature.events.addDetail')}</Text>
        <Button
          label={t(state.settings.locale, 'feature.events.addAction')}
          onPress={() => dispatch({ type: 'navigate', screen: 'eventForm' })}
        />
      </Card>

      <Card>
        <View style={styles.wrapRow}>
          <Pill
            label={t(state.settings.locale, 'feature.events.view.list')}
            selected={viewMode === 'List'}
            onPress={() => setViewMode('List')}
          />
          <Pill
            label={t(state.settings.locale, 'feature.events.view.month')}
            selected={viewMode === 'Month'}
            onPress={() => setViewMode('Month')}
          />
        </View>
        <Text style={styles.smallText}>{t(state.settings.locale, 'feature.events.filter.type')}</Text>
        <View style={styles.wrapRow}>
          {visibleEventTypeFilters.map(item => (
            <Pill
              key={item}
              label={eventTypeLabel(state.settings.locale, item)}
              selected={typeFilter === item}
              onPress={() => setTypeFilter(item)}
            />
          ))}
        </View>
        <Button
          label={t(
            state.settings.locale,
            showAdvancedEventTypes ? 'feature.events.hideAdvancedTypes' : 'feature.events.showAdvancedTypes'
          )}
          tone="ghost"
          onPress={() => {
            if (showAdvancedEventTypes && advancedManualEventTypes.includes(typeFilter as EventType)) {
              setTypeFilter('All');
            }
            setShowAdvancedEventTypes(value => !value);
          }}
        />
        <Text style={styles.smallText}>{t(state.settings.locale, 'feature.events.filter.timeRange')}</Text>
        <View style={styles.wrapRow}>
          {eventTimeFilters.map(item => (
            <Pill
              key={item}
              label={eventTimeFilterLabel(state.settings.locale, item)}
              selected={timeFilter === item}
              onPress={() => setTimeFilter(item)}
            />
          ))}
        </View>
        {viewMode === 'Month' ? (
          <View style={styles.monthControls}>
            <Button
              label={t(state.settings.locale, 'feature.events.previousMonth')}
              tone="secondary"
              onPress={() => setMonthIso(value => shiftMonth(value, -1))}
            />
            <Text style={styles.cardTitle}>{formatMonthForLocale(monthView.monthKey, state.settings.locale)}</Text>
            <Button
              label={t(state.settings.locale, 'feature.events.nextMonth')}
              tone="secondary"
              onPress={() => setMonthIso(value => shiftMonth(value, 1))}
            />
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
            onScheduleReminders={onScheduleReminders}
          />
        ))
      ) : (
        <Card>
          <Text style={styles.bodyText}>{t(state.settings.locale, 'feature.events.emptyFiltered')}</Text>
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
}) => {
  const locale = state.settings.locale;
  return (
    <Card>
      <View style={styles.monthGrid}>
        {weekdayLabelsFor(locale).map(day => (
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
                  ? t(locale, 'feature.events.month.dayWithEvents', {
                      date: day.dateKey,
                      count: tc(locale, day.events.length, {
                        one: 'common.count.event.one',
                        other: 'common.count.event.other'
                      })
                    })
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
                  {t(locale, 'feature.events.month.contactEvent', {
                    contact: contact?.name ?? t(locale, 'common.contactFallback'),
                    type: eventTypeLabel(locale, firstEvent.type)
                  })}
                </Text>
              ) : null}
              {day.events.length > 1 ? <Text style={styles.monthMoreText}>+{day.events.length - 1}</Text> : null}
            </TouchableOpacity>
          );
        })}
      </View>
    </Card>
  );
};

const EventForm = ({ state, dispatch }: Pick<ScreenProps, 'state' | 'dispatch'>) => {
  const [contactMode, setContactMode] = useState<'existing' | 'new'>('existing');
  const [contactId, setContactId] = useState(state.selectedContactId ?? state.contacts[0]?.id);
  const [newContactName, setNewContactName] = useState('');
  const [eventType, setEventType] = useState<EventType>('Birthday');
  const [label, setLabel] = useState('');
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [confirmConflict, setConfirmConflict] = useState(false);
  const [showAdvancedEventTypes, setShowAdvancedEventTypes] = useState(false);
  const visibleManualEventTypes = showAdvancedEventTypes ? manualEventTypes : primaryManualEventTypes;
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
      <SectionTitle
        title={t(state.settings.locale, 'feature.eventForm.title')}
        detail={t(state.settings.locale, 'feature.eventForm.detail')}
      />
      <Card>
        <Text style={styles.cardTitle}>{t(state.settings.locale, 'feature.eventForm.who')}</Text>
        <View style={styles.wrapRow}>
          <Pill
            label={t(state.settings.locale, 'feature.eventForm.existingContact')}
            selected={contactMode === 'existing'}
            onPress={() => setContactMode('existing')}
          />
          <Pill
            label={t(state.settings.locale, 'feature.eventForm.newContact')}
            selected={contactMode === 'new'}
            onPress={() => setContactMode('new')}
          />
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
            accessibilityLabel={t(state.settings.locale, 'feature.eventForm.newContactName')}
            placeholder={t(state.settings.locale, 'feature.eventForm.newContactName')}
            placeholderTextColor={colors.muted}
            value={newContactName}
            onChangeText={setNewContactName}
            style={styles.input}
          />
        )}

        <Text style={styles.cardTitle}>{t(state.settings.locale, 'feature.eventForm.eventType')}</Text>
        <View style={styles.wrapRow}>
          {visibleManualEventTypes.map(item => (
            <Pill
              key={item}
              label={eventTypeLabel(state.settings.locale, item)}
              selected={eventType === item}
              onPress={() => setEventType(item)}
            />
          ))}
        </View>
        <Button
          label={t(
            state.settings.locale,
            showAdvancedEventTypes ? 'feature.eventForm.hideAdvancedTypes' : 'feature.eventForm.showAdvancedTypes'
          )}
          tone="ghost"
          onPress={() => {
            if (showAdvancedEventTypes && advancedManualEventTypes.includes(eventType)) {
              setEventType('Custom');
            }
            setShowAdvancedEventTypes(value => !value);
          }}
        />

        <TextInput
          accessibilityLabel={t(state.settings.locale, 'feature.eventForm.eventLabel')}
          placeholder={t(state.settings.locale, 'feature.eventForm.eventLabel')}
          placeholderTextColor={colors.muted}
          value={label}
          onChangeText={setLabel}
          style={styles.input}
        />
        <TextInput
          accessibilityLabel={t(state.settings.locale, 'feature.eventForm.eventDate')}
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
          <Pill
            label={t(state.settings.locale, 'feature.eventForm.keepSeparate')}
            selected={confirmConflict}
            onPress={() => setConfirmConflict(value => !value)}
          />
        ) : null}
        <Button
          label={t(
            state.settings.locale,
            warnings.length > 0 ? 'feature.eventForm.saveReviewed' : 'feature.eventForm.save'
          )}
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

const EventCard = ({
  event,
  state,
  dispatch,
  onGenerateMessage,
  onScheduleReminders
}: { event: RelationshipEvent } & ScreenProps) => {
  const locale = state.settings.locale;
  const contact = getContact(state.contacts, event.contactId);
  const preparation = buildEventPreparationPlan(state, event.id);
  const scheduleReminders = onScheduleReminders ?? (() => undefined);
  const completeCount = preparation.completedCount;
  const totalCount = preparation.totalCount;
  return (
    <Card>
      <View style={styles.rowBetween}>
        <View style={styles.flex}>
          <Text style={styles.cardTitle}>{event.label}</Text>
          <Text style={styles.mutedText}>
            {t(locale, 'feature.eventCard.meta', {
              contact: contact?.name ?? t(locale, 'common.unknownContact'),
              type: eventTypeLabel(locale, event.type),
              date: formatDateForLocale(event.date, locale)
            })}
          </Text>
        </View>
        <Pill label={`${completeCount}/${totalCount}`} selected={preparation.isComplete} />
      </View>
      <Text style={styles.smallText}>{preparation.summary}</Text>
      <View style={styles.checklist}>
        {preparation.steps.map(item => {
          const statusLabel = eventPreparationStatusLabel(locale, item.status);
          return (
            <TouchableOpacity
              accessibilityLabel={`${item.label}: ${statusLabel}`}
              accessibilityRole="checkbox"
              accessibilityState={{ checked: item.done }}
              key={item.id}
              onPress={() => dispatch({ type: 'togglePreparationStep', eventId: event.id, stepId: item.id })}
              style={styles.checkItem}
            >
              <Text style={styles.checkMark}>{item.done ? '✓' : '○'}</Text>
              <View style={styles.flex}>
                <View style={styles.rowBetween}>
                  <Text style={styles.bodyText}>{item.label}</Text>
                  <Pill label={statusLabel} selected={item.done} />
                </View>
                <Text style={styles.smallText}>{item.detail}</Text>
              </View>
            </TouchableOpacity>
          );
        })}
      </View>
      {preparation.ok && preparation.nextStep ? (
        <Text style={styles.smallText}>
          {t(locale, 'feature.eventCard.nextAction', {
            action: preparation.nextStep.actionLabel,
            detail: preparation.nextStep.detail
          })}
        </Text>
      ) : null}
      <View style={styles.actionRow}>
        <Button
          label={t(locale, 'feature.eventCard.writeMessage')}
          onPress={() => {
            onGenerateMessage(
              event.contactId,
              event.id,
              event.type === 'Birthday' ? 'Birthday' : event.type === 'Follow-up' ? 'Check-in' : 'Congratulations'
            );
          }}
        />
        <Button
          label={t(locale, 'feature.eventCard.openContact')}
          tone="secondary"
          onPress={() => dispatch({ type: 'navigate', screen: 'contactDetail', contactId: event.contactId })}
        />
        <Button label={t(locale, 'feature.eventCard.planReminders')} tone="secondary" onPress={scheduleReminders} />
      </View>
    </Card>
  );
};

const MessagesScreen = ({ state, dispatch, onManualHandoff, onSendEmail, onGenerateMessage }: ScreenProps) => {
  const locale = state.settings.locale;
  const [tab, setTab] = useState<MessageInboxTab>('All');
  const [channel, setChannel] = useState<MessageInboxChannelFilter>('All');
  const [sort, setSort] = useState<MessageInboxSort>('Newest');
  const [query, setQuery] = useState('');
  const [showBulkTools, setShowBulkTools] = useState(false);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [lastBulkSummary, setLastBulkSummary] = useState('');
  const emailConfig = readEmailSenderConfig();
  const emailEndpointReadiness = evaluateProviderEndpointReadiness(emailConfig.endpoint, {
    allowLocalDevelopment: emailConfig.allowLocalProviderEndpoint
  });
  const inbox = buildMessageInbox(state, {
    tab,
    channel,
    query,
    sort,
    emailEndpointConfigured: emailEndpointReadiness.canUseProviderEndpoint
  });
  const visibleIds = inbox.rows.map(row => row.message.id);
  const selectedVisibleIds = selectedIds.filter(id => visibleIds.includes(id));
  const bulkReports = messageBulkActions.map(action =>
    buildMessageBulkActionReport(state, selectedVisibleIds, action)
  );
  const bulkVerificationGuidance = bulkReports
    .map(report => report.verificationGuidance)
    .filter(Boolean)
    .join(' ');

  useEffect(() => {
    setSelectedIds(current => current.filter(id => visibleIds.includes(id)));
  }, [visibleIds.join('|')]);

  const toggleSelected = (messageId: string) => {
    setLastBulkSummary('');
    setSelectedIds(current =>
      current.includes(messageId) ? current.filter(id => id !== messageId) : [...current, messageId]
    );
  };

  const selectVisibleMessages = () => {
    setLastBulkSummary('');
    setSelectedIds(visibleIds);
  };

  const clearSelection = () => {
    setLastBulkSummary('');
    setSelectedIds([]);
  };

  const toggleBulkTools = () => {
    setLastBulkSummary('');
    if (showBulkTools) {
      setSelectedIds([]);
    }
    setShowBulkTools(current => !current);
  };

  const runBulkAction = (action: MessageBulkAction) => {
    const report = buildMessageBulkActionReport(state, selectedVisibleIds, action);
    if (report.eligibleIds.length === 0) {
      setLastBulkSummary(report.confirmation);
      return;
    }
    const actionLabel = messageBulkActionLabel(locale, action);

    const execute = () => {
      dispatch({ type: 'bulkMessageAction', action, messageIds: selectedVisibleIds });
      setLastBulkSummary(report.confirmation);
      setSelectedIds(current => current.filter(id => !report.eligibleIds.includes(id)));
    };

    if (report.requiresConfirmation) {
      showAppAlert(t(locale, 'feature.messages.bulk.confirmTitle'), report.confirmation, [
        { text: t(locale, 'action.cancel'), style: 'cancel' },
        { text: actionLabel, style: action === 'Reject' ? 'destructive' : 'default', onPress: execute }
      ]);
      return;
    }

    execute();
  };

  return (
    <View>
      <SectionTitle
        title={t(locale, 'feature.messages.title')}
        detail={t(locale, 'feature.messages.detail')}
      />
      <TextInput
        accessibilityLabel={t(locale, 'feature.messages.searchLabel')}
        placeholder={t(locale, 'feature.messages.searchPlaceholder')}
        value={query}
        onChangeText={setQuery}
        style={styles.input}
      />
      <Text style={styles.smallText}>{t(locale, 'feature.messages.filter.status')}</Text>
      <View style={styles.wrapRow}>
        {messageInboxTabs.map(item => (
          <Pill
            key={item}
            label={t(locale, 'feature.messages.tabCount', {
              tab: messageInboxTabLabel(locale, item),
              count: inbox.counts[item]
            })}
            selected={tab === item}
            onPress={() => setTab(item)}
          />
        ))}
      </View>
      <Text style={styles.smallText}>{t(locale, 'feature.messages.filter.channel')}</Text>
      <View style={styles.wrapRow}>
        {messageInboxChannelFilters.map(item => (
          <Pill key={item} label={messageChannelLabel(locale, item)} selected={channel === item} onPress={() => setChannel(item)} />
        ))}
      </View>
      <Text style={styles.smallText}>{t(locale, 'feature.messages.filter.sort')}</Text>
      <View style={styles.wrapRow}>
        {messageInboxSorts.map(item => (
          <Pill key={item} label={messageInboxSortLabel(locale, item)} selected={sort === item} onPress={() => setSort(item)} />
        ))}
      </View>
      {inbox.rows.length > 0 ? (
        <Button
          label={t(locale, showBulkTools ? 'feature.messages.bulk.hideTools' : 'feature.messages.bulk.showTools')}
          tone="ghost"
          onPress={toggleBulkTools}
        />
      ) : null}
      {inbox.rows.length > 0 && showBulkTools ? (
        <Card>
          <View style={styles.rowBetween}>
            <View style={styles.flex}>
              <Text style={styles.cardTitle}>{t(locale, 'feature.messages.bulk.title')}</Text>
              <Text style={styles.smallText}>
                {t(locale, 'feature.messages.bulk.selectionSummary', {
                  selected: selectedVisibleIds.length,
                  visible: inbox.rows.length
                })}
              </Text>
            </View>
            <Pill
              label={t(
                locale,
                selectedVisibleIds.length > 0
                  ? 'feature.messages.bulk.selectionActive'
                  : 'feature.messages.bulk.noneSelected'
              )}
              selected={selectedVisibleIds.length > 0}
            />
          </View>
          <View style={styles.actionRow}>
            <Button label={t(locale, 'feature.messages.bulk.selectVisible')} tone="secondary" onPress={selectVisibleMessages} />
            <Button label={t(locale, 'action.clear')} tone="ghost" onPress={clearSelection} disabled={selectedVisibleIds.length === 0} />
          </View>
          <View style={styles.actionRow}>
            {bulkReports.map(report => (
              <Button
                key={report.action}
                label={t(locale, 'feature.messages.bulk.actionCount', {
                  action: messageBulkActionLabel(locale, report.action),
                  count: report.eligibleIds.length
                })}
                tone={report.action === 'Reject' ? 'danger' : 'secondary'}
                disabled={selectedVisibleIds.length === 0 || report.eligibleIds.length === 0}
                onPress={() => runBulkAction(report.action)}
              />
            ))}
          </View>
          {selectedVisibleIds.length > 0 ? (
            <Text style={styles.smallText}>
              {bulkReports
                .map(report =>
                  t(locale, 'feature.messages.bulk.eligibleSummary', {
                    action: messageBulkActionLabel(locale, report.action),
                    count: report.eligibleIds.length
                  })
                )
                .join(' | ')}
            </Text>
          ) : null}
          {bulkVerificationGuidance ? <Text style={styles.warningText}>{bulkVerificationGuidance}</Text> : null}
          {lastBulkSummary ? <Text style={styles.warningText}>{lastBulkSummary}</Text> : null}
        </Card>
      ) : null}
      {inbox.rows.length === 0 ? (
        <Card>
          <Text style={styles.bodyText}>
            {inbox.emptyState === 'No messages yet'
              ? t(locale, 'feature.messages.empty.none')
              : t(locale, 'feature.messages.empty.filtered')}
          </Text>
        </Card>
      ) : null}
      {inbox.rows.map(row => (
        <MessageCard
          key={row.message.id}
          row={row}
          state={state}
          dispatch={dispatch}
          onManualHandoff={onManualHandoff}
          onSendEmail={onSendEmail}
          onGenerateMessage={onGenerateMessage}
          bulkSelectionEnabled={showBulkTools}
          selected={showBulkTools && selectedVisibleIds.includes(row.message.id)}
          onToggleSelected={() => toggleSelected(row.message.id)}
        />
      ))}
    </View>
  );
};

const MessageCard = ({
  row,
  state,
  dispatch,
  onManualHandoff,
  onSendEmail,
  bulkSelectionEnabled,
  selected,
  onToggleSelected
}: { row: MessageInboxRow; bulkSelectionEnabled: boolean; selected: boolean; onToggleSelected: () => void } & ScreenProps) => {
  const { message } = row;
  const locale = state.settings.locale;
  const runRecovery = (recovery: MessageInboxRecovery) => {
    if (recovery.actionLabel === 'Retry message') {
      dispatch({ type: 'retryMessage', messageId: message.id });
      return;
    }
    dispatch({
      type: 'navigate',
      screen: recovery.targetScreen,
      contactId: message.contactId,
      messageId: message.id
    });
  };
  return (
    <Card>
      <View style={styles.rowBetween}>
        <View style={styles.flex}>
          <Text style={styles.cardTitle}>{row.contactName}</Text>
          <Text style={styles.mutedText}>
            {t(locale, 'feature.messages.card.meta', {
              reason: message.reason,
              channel: messageChannelLabel(locale, message.channel),
              status: messageStatusLabel(locale, message.status)
            })}
          </Text>
          {row.eventLabel ? <Text style={styles.smallText}>{row.eventLabel}</Text> : null}
        </View>
        <Pill label={messageQualityLabel(locale, message.quality)} selected={message.quality === 'AI draft'} />
      </View>
      <Text style={styles.bodyText}>{message.body}</Text>
      {message.duplicateWarning ? <Text style={styles.warningText}>{message.duplicateWarning}</Text> : null}
      <Text style={styles.smallText}>{message.readiness}</Text>
      {row.recovery ? (
        <View style={styles.inlineItem}>
          <Text style={styles.warningText}>{row.recovery.title}</Text>
          <Text style={styles.smallText}>{row.recovery.detail}</Text>
          <Button label={row.recovery.actionLabel} tone="secondary" onPress={() => runRecovery(row.recovery!)} />
        </View>
      ) : null}
      <View style={styles.actionRow}>
        {bulkSelectionEnabled ? (
          <Button
            label={t(locale, selected ? 'feature.messages.card.selected' : 'feature.messages.card.select')}
            tone={selected ? 'primary' : 'ghost'}
            onPress={onToggleSelected}
          />
        ) : null}
        <Button
          label={t(locale, 'feature.messages.card.preview')}
          onPress={() => dispatch({ type: 'navigate', screen: 'wishPreview', messageId: message.id, contactId: message.contactId })}
        />
        {message.status === 'Scheduled' ? (
          <Button
            label={t(locale, 'feature.messages.card.revokeApproval')}
            tone="ghost"
            onPress={() => dispatch({ type: 'revokeMessage', messageId: message.id })}
          />
        ) : null}
        {message.status === 'Scheduled' && message.channel === 'Manual' ? (
          <Button label={t(locale, 'feature.messages.card.manualSend')} tone="secondary" onPress={() => onManualHandoff(message)} />
        ) : null}
        {message.status === 'Scheduled' && message.channel === 'Email' ? (
          <>
            <Button label={t(locale, 'feature.messages.card.sendEmail')} onPress={() => onSendEmail(message)} />
            <Button label={t(locale, 'feature.messages.card.emailHandoff')} tone="secondary" onPress={() => onManualHandoff(message)} />
          </>
        ) : null}
        {message.status === 'Failed' || message.status === 'Blocked' ? (
          <Button label={t(locale, 'action.retry')} tone="secondary" onPress={() => dispatch({ type: 'retryMessage', messageId: message.id })} />
        ) : null}
        {message.status === 'Sent' ? (
          <>
            <Button
              label={t(locale, 'feature.messages.card.followUpTomorrow')}
              tone="secondary"
              onPress={() => dispatch({ type: 'scheduleMessageFollowUp', messageId: message.id, delayDays: 1 })}
            />
            <Button
              label={t(locale, 'feature.messages.card.followUpNextWeek')}
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
  const locale = state.settings.locale;
  const [groupFilter, setGroupFilter] = useState<ContactGroupFilter>('All');
  const [qualityFilter, setQualityFilter] = useState<ContactQualityFilter>('All');
  const [contactSort, setContactSort] = useState<ContactSort>('Name');
  const rows = buildContactBrowserRows(state, {
    query: state.searchQuery,
    group: groupFilter,
    quality: qualityFilter,
    sort: contactSort
  });

  return (
    <View>
      <SectionTitle
        title={t(locale, 'feature.contacts.title')}
        detail={t(locale, 'feature.contacts.detail')}
      />
      <TextInput
        accessibilityLabel={t(locale, 'feature.contacts.searchLabel')}
        placeholder={t(locale, 'feature.contacts.searchPlaceholder')}
        value={state.searchQuery}
        onChangeText={queryText => dispatch({ type: 'setSearch', query: queryText })}
        style={styles.input}
      />
      <Text style={styles.smallText}>{t(locale, 'feature.contacts.filter.group')}</Text>
      <View style={styles.wrapRow}>
        {contactGroupFilters.map(filter => (
          <Pill
            key={filter}
            label={contactGroupLabel(locale, filter)}
            selected={groupFilter === filter}
            onPress={() => setGroupFilter(filter)}
          />
        ))}
      </View>
      <Text style={styles.smallText}>{t(locale, 'feature.contacts.filter.quality')}</Text>
      <View style={styles.wrapRow}>
        {contactQualityFilters.map(filter => (
          <Pill
            key={filter}
            label={contactQualityLabel(locale, filter)}
            selected={qualityFilter === filter}
            onPress={() => setQualityFilter(filter)}
          />
        ))}
      </View>
      <Text style={styles.smallText}>{t(locale, 'feature.contacts.filter.sort')}</Text>
      <View style={styles.wrapRow}>
        {contactSorts.map(sort => (
          <Pill key={sort} label={contactSortLabel(locale, sort)} selected={contactSort === sort} onPress={() => setContactSort(sort)} />
        ))}
      </View>
      {rows.length === 0 ? <Text style={styles.bodyText}>{t(locale, 'feature.contacts.emptyFiltered')}</Text> : null}
      {rows.map(({ contact, nextEvent, qualityLabels }) => {
        const preferences = resolveContactPreferencesForContact(state.settings, contact);
        return (
          <Card key={contact.id}>
            <View style={styles.rowBetween}>
              <View style={styles.flex}>
                <Text style={styles.cardTitle}>{contact.name}</Text>
                <Text style={styles.mutedText}>
                  {t(locale, 'feature.contacts.card.meta', {
                    relationship: contact.relationship,
                    group: contactGroupLabel(locale, contact.group),
                    health: t(locale, 'feature.contacts.card.health', { score: contact.healthScore })
                  })}
                </Text>
                <Text style={styles.smallText}>
                  {nextEvent
                    ? t(locale, 'feature.contacts.card.nextEvent', {
                        label: nextEvent.label,
                        date: formatDateForLocale(nextEvent.date, locale)
                      })
                    : t(locale, 'feature.contacts.card.noNextEvent')}
                </Text>
                <Text style={styles.smallText}>{contact.notesSummary}</Text>
              </View>
              <Pill label={messageChannelLabel(locale, preferences.preferredChannel)} selected />
            </View>
            {qualityLabels.length > 0 ? (
              <View style={styles.wrapRow}>
                {qualityLabels.map(label => (
                  <Pill key={label} label={contactQualityLabel(locale, label as ContactQualityFilter)} selected={label === qualityFilter} />
                ))}
              </View>
            ) : null}
            <View style={styles.actionRow}>
              <Button
                label={t(locale, 'action.open')}
                onPress={() => dispatch({ type: 'navigate', screen: 'contactDetail', contactId: contact.id })}
              />
              <Button
                label={t(locale, 'feature.contacts.card.write')}
                tone="secondary"
                onPress={() => dispatch({ type: 'navigate', screen: 'manualComposer', contactId: contact.id })}
              />
            </View>
          </Card>
        );
      })}
    </View>
  );
};

const ContactDetail = ({ contact, state, dispatch }: { contact: Contact } & ScreenProps) => {
  const locale = state.settings.locale;
  const [essentialName, setEssentialName] = useState(contact.name);
  const [essentialRelationship, setEssentialRelationship] = useState(contact.relationship);
  const [essentialPhone, setEssentialPhone] = useState(contact.phone ?? '');
  const [essentialEmail, setEssentialEmail] = useState(contact.email ?? '');
  const [essentialLanguage, setEssentialLanguage] = useState<Contact['language']>(contact.language);
  const [essentialNotes, setEssentialNotes] = useState(contact.notesSummary);
  const [memoryText, setMemoryText] = useState('');
  const [memoryCategory, setMemoryCategory] = useState<MemoryCategory>('Preference');
  const [memoryQuery, setMemoryQuery] = useState('');
  const [editingMemoryId, setEditingMemoryId] = useState<string | undefined>();
  const [editingMemoryText, setEditingMemoryText] = useState('');
  const [editingMemoryCategory, setEditingMemoryCategory] = useState<MemoryCategory>('Preference');
  const [confirmDeleteMemoryId, setConfirmDeleteMemoryId] = useState<string | undefined>();
  const [giftName, setGiftName] = useState('');
  const [giftCategory, setGiftCategory] = useState<GiftCategory>('Personal');
  const [giftOccasion, setGiftOccasion] = useState('Next event');
  const [giftCost, setGiftCost] = useState('0');
  const [giftNotes, setGiftNotes] = useState('');
  const [giftFeedback, setGiftFeedback] = useState<'Liked' | 'Disliked' | 'Unknown'>('Unknown');
  const [showGiftBudgetEditor, setShowGiftBudgetEditor] = useState(false);
  const [giftBudgetDraft, setGiftBudgetDraft] = useState(String(contact.annualGiftBudget));
  const [confirmDeleteGiftId, setConfirmDeleteGiftId] = useState<string | undefined>();
  const [enrichmentAnswers, setEnrichmentAnswers] = useState<Record<string, string>>({});
  const [timelineFilter, setTimelineFilter] = useState<ContactTimelineFilter>('All');
  const [showAdvancedContactAutomation, setShowAdvancedContactAutomation] = useState(false);
  const contactEvents = state.events.filter(event => event.contactId === contact.id);
  const contactGifts = state.gifts.filter(gift => gift.contactId === contact.id);
  const contactMessages = state.messages.filter(message => message.contactId === contact.id);
  const enrichmentPlan = buildContactEnrichmentPlan(state, contact.id);
  const relationshipInsight = buildRelationshipHealthInsight(state, contact.id);
  const preferences = resolveContactPreferencesForContact(state.settings, contact);
  const visibleContactAutomationModes =
    showAdvancedContactAutomation || preferences.automationMode === 'Fully auto' ? automationModes : defaultAutomationModes;
  const contactCheckInQueue = buildCheckInReminderQueue(state);
  const contactCheckIn = [
    ...contactCheckInQueue.due,
    ...contactCheckInQueue.snoozed,
    ...contactCheckInQueue.current
  ].find(reminder => reminder.contactId === contact.id);
  const timeline = buildContactTimeline(state, contact.id, timelineFilter);
  const memoryVault = buildMemoryVaultReport(state, contact.id, memoryQuery);
  const memoryDraftValidation = validateMemoryNoteInput(state, contact.id, memoryText);
  const editingMemoryValidation = editingMemoryId
    ? validateMemoryNoteInput(state, contact.id, editingMemoryText)
    : undefined;
  const giftBudget = buildGiftBudgetSummary(contact, state.gifts);
  const giftSuggestions = buildGiftSuggestions(state, contact.id, giftOccasion);
  const essentialsInput = {
    name: essentialName,
    relationship: essentialRelationship,
    phone: essentialPhone,
    email: essentialEmail,
    language: essentialLanguage,
    notesSummary: essentialNotes
  };
  const essentialsValidation = validateContactEssentials(essentialsInput, preferences.preferredChannel);
  const giftBudgetValidation = validateGiftBudgetInput({ annualGiftBudget: giftBudgetDraft });
  const useGiftSuggestion = (suggestion: GiftSuggestion) => {
    setGiftName(suggestion.name);
    setGiftCategory(suggestion.category);
    setGiftOccasion(suggestion.occasion);
    setGiftCost(String(suggestion.estimatedCost));
    setGiftNotes(suggestion.rationale);
    setGiftFeedback('Unknown');
  };

  useEffect(() => {
    setEssentialName(contact.name);
    setEssentialRelationship(contact.relationship);
    setEssentialPhone(contact.phone ?? '');
    setEssentialEmail(contact.email ?? '');
    setEssentialLanguage(contact.language);
    setEssentialNotes(contact.notesSummary);
    setGiftBudgetDraft(String(contact.annualGiftBudget));
    setShowGiftBudgetEditor(false);
    setMemoryQuery('');
    setEditingMemoryId(undefined);
    setEditingMemoryText('');
    setEditingMemoryCategory('Preference');
    setConfirmDeleteMemoryId(undefined);
    setConfirmDeleteGiftId(undefined);
    setShowAdvancedContactAutomation(false);
  }, [
    contact.id,
    contact.name,
    contact.relationship,
    contact.phone,
    contact.email,
    contact.language,
    contact.notesSummary,
    contact.annualGiftBudget
  ]);

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <SectionTitle
        title={contact.name}
        detail={`${contact.relationship} - ${relationshipGroupDisplayLabel(locale, contact.group)}`}
      />
      <Card>
        <View style={styles.wrapRow}>
          <Pill label={t(locale, 'feature.contactDetail.status.health', { score: contact.healthScore })} selected={contact.healthScore >= 70} />
          <Pill
            label={
              contact.isVip
                ? t(locale, 'feature.contactDetail.status.vip')
                : t(locale, 'feature.contactDetail.status.standard')
            }
            selected={contact.isVip}
          />
          <Pill
            label={contact.dnd ? t(locale, 'feature.contactDetail.status.dndOn') : t(locale, 'feature.contactDetail.status.dndOff')}
            selected={contact.dnd}
          />
          <Pill label={t(locale, 'feature.contactDetail.status.checkInCadence', { days: preferences.checkInCadenceDays })} />
          <Pill
            label={t(locale, 'feature.contactDetail.status.automationReview', {
              mode: automationModeDisplayLabel(locale, preferences.automationMode)
            })}
            selected={preferences.sources.automationMode !== 'global'}
          />
        </View>
        <Text style={styles.bodyText}>{contact.notesSummary}</Text>
        {contactCheckIn ? (
          <View style={styles.inlineItem}>
            <View style={styles.rowBetween}>
              <View style={styles.flex}>
                <Text style={styles.bodyText}>{checkInReminderTitle(locale, contactCheckIn)}</Text>
                <Text style={styles.smallText}>{checkInReminderDetail(locale, contactCheckIn)}</Text>
              </View>
              <Pill label={checkInStatusLabel(locale, contactCheckIn.status)} selected={contactCheckIn.status === 'Due'} />
            </View>
          </View>
        ) : null}
        <View style={styles.actionRow}>
          <Button
            label={t(locale, 'feature.contactDetail.action.writeMessage')}
            onPress={() => dispatch({ type: 'navigate', screen: 'manualComposer', contactId: contact.id })}
          />
          <Button
            label={t(locale, 'feature.contactDetail.action.chatHistory')}
            tone="secondary"
            onPress={() => dispatch({ type: 'navigate', screen: 'chatHistory', contactId: contact.id })}
          />
          <Button
            label={t(locale, 'feature.contactDetail.action.snoozeCheckIn')}
            tone="secondary"
            onPress={() => dispatch({ type: 'snoozeCheckIn', contactId: contact.id, days: 14 })}
          />
          <Button
            label={t(locale, 'feature.contactDetail.action.markContacted')}
            tone="ghost"
            onPress={() => dispatch({ type: 'markContactedElsewhere', contactId: contact.id })}
          />
        </View>
      </Card>

      <SectionTitle
        title={t(locale, 'feature.contactDetail.essentials.title')}
        detail={t(locale, 'feature.contactDetail.essentials.detail')}
      />
      <Card>
        <TextInput
          accessibilityLabel={t(locale, 'feature.contactDetail.essentials.contactName')}
          placeholder={t(locale, 'feature.contactDetail.essentials.contactName')}
          value={essentialName}
          onChangeText={setEssentialName}
          style={styles.input}
        />
        <TextInput
          accessibilityLabel={t(locale, 'feature.contactDetail.essentials.relationship')}
          placeholder={t(locale, 'feature.contactDetail.essentials.relationship')}
          value={essentialRelationship}
          onChangeText={setEssentialRelationship}
          style={styles.input}
        />
        <View style={styles.actionRow}>
          <TextInput
            accessibilityLabel={t(locale, 'feature.contactDetail.essentials.contactPhone')}
            placeholder={t(locale, 'feature.contactDetail.essentials.phone')}
            value={essentialPhone}
            onChangeText={setEssentialPhone}
            keyboardType="phone-pad"
            style={[styles.input, styles.flexInput]}
          />
          <TextInput
            accessibilityLabel={t(locale, 'feature.contactDetail.essentials.contactEmail')}
            placeholder={t(locale, 'feature.contactDetail.essentials.email')}
            value={essentialEmail}
            onChangeText={setEssentialEmail}
            autoCapitalize="none"
            keyboardType="email-address"
            style={[styles.input, styles.flexInput]}
          />
        </View>
        <Text style={styles.smallText}>{t(locale, 'feature.contactDetail.essentials.language')}</Text>
        <View style={styles.wrapRow}>
          {supportedContactLanguages.map(language => (
            <Pill
              key={language}
              label={contactLanguageLabel(locale, language)}
              selected={essentialLanguage === language}
              onPress={() => setEssentialLanguage(language)}
            />
          ))}
        </View>
        <TextInput
          accessibilityLabel={t(locale, 'feature.contactDetail.essentials.contactNotesSummary')}
          placeholder={t(locale, 'feature.contactDetail.essentials.notesSummary')}
          value={essentialNotes}
          onChangeText={setEssentialNotes}
          style={[styles.input, styles.multiline]}
          multiline
        />
        {!essentialsValidation.ok ? <Text style={styles.warningText}>{essentialsValidation.message}</Text> : null}
        <View style={styles.actionRow}>
          <Button
            label={t(locale, 'feature.contactDetail.essentials.saveProfile')}
            disabled={!essentialsValidation.ok}
            onPress={() => {
              if (essentialsValidation.ok) {
                dispatch({ type: 'updateContactEssentials', contactId: contact.id, input: essentialsInput });
              }
            }}
          />
          <Button
            label={t(locale, 'feature.contactDetail.essentials.resetChanges')}
            tone="secondary"
            onPress={() => {
              setEssentialName(contact.name);
              setEssentialRelationship(contact.relationship);
              setEssentialPhone(contact.phone ?? '');
              setEssentialEmail(contact.email ?? '');
              setEssentialLanguage(contact.language);
              setEssentialNotes(contact.notesSummary);
            }}
          />
        </View>
      </Card>

      {relationshipInsight ? (
        <>
          <SectionTitle
            title={t(locale, 'feature.contactDetail.relationshipHealth.title')}
            detail={t(locale, 'feature.contactDetail.relationshipHealth.detail')}
          />
          <Card>
            <View style={styles.rowBetween}>
              <View style={styles.flex}>
                <Text style={styles.cardTitle}>{relationshipHealthLabel(locale, relationshipInsight.label)}</Text>
                <Text style={styles.bodyText}>{relationshipInsight.summary}</Text>
              </View>
              <Pill label={`${relationshipInsight.score}/100`} selected={relationshipInsight.score >= 70} />
            </View>
            {relationshipInsight.suggestion ? (
              <View style={styles.inlineItem}>
                <Text style={styles.bodyText}>
                  {t(locale, 'feature.contactDetail.relationshipHealth.suggestedGroup', {
                    group: relationshipGroupDisplayLabel(locale, relationshipInsight.suggestion.group)
                  })}
                </Text>
                <Text style={styles.smallText}>
                  {t(locale, 'feature.contactDetail.relationshipHealth.suggestionConfidence', {
                    confidence: confidenceLabel(locale, relationshipInsight.suggestion.confidence),
                    rationale: relationshipInsight.suggestion.rationale
                  })}
                </Text>
                <Button
                  label={t(locale, 'feature.contactDetail.relationshipHealth.applySuggestion')}
                  tone="secondary"
                  onPress={() =>
                    dispatch({
                      type: 'setContactGroup',
                      contactId: contact.id,
                      group: relationshipInsight.suggestion!.group
                    })
                  }
                />
              </View>
            ) : null}
            <Text style={styles.smallText}>{t(locale, 'feature.contactDetail.relationshipHealth.group')}</Text>
            <View style={styles.wrapRow}>
              {relationshipGroupOptions.map(group => (
                <Pill
                  key={group}
                  label={relationshipGroupDisplayLabel(locale, group)}
                  selected={contact.group === group}
                  onPress={() => dispatch({ type: 'setContactGroup', contactId: contact.id, group })}
                />
              ))}
            </View>
            <Text style={styles.smallText}>{t(locale, 'feature.contactDetail.relationshipHealth.checkInCadence')}</Text>
            <View style={styles.wrapRow}>
              {checkInCadenceOptions.map(days => (
                <Pill
                  key={days}
                  label={checkInCadenceLabel(locale, days)}
                  selected={preferences.checkInCadenceDays === days}
                  onPress={() => dispatch({ type: 'setCheckInCadence', contactId: contact.id, days })}
                />
              ))}
            </View>
            <Text style={styles.smallText}>{t(locale, 'feature.contactDetail.relationshipHealth.automationReviewLevel')}</Text>
            <View style={styles.wrapRow}>
              {visibleContactAutomationModes.map(mode => (
                <Pill
                  key={mode}
                  label={automationModeDisplayLabel(locale, mode)}
                  selected={preferences.automationMode === mode}
                  onPress={() => dispatch({ type: 'setContactAutomationMode', contactId: contact.id, mode })}
                />
              ))}
            </View>
            <Button
              label={t(
                locale,
                showAdvancedContactAutomation
                  ? 'feature.more.settings.hideAdvancedAutomation'
                  : 'feature.more.settings.showAdvancedAutomation'
              )}
              tone="ghost"
              onPress={() => setShowAdvancedContactAutomation(value => !value)}
            />
            {showAdvancedContactAutomation ? (
              <Text style={styles.smallText}>{t(locale, 'feature.more.settings.advancedAutomationDetail')}</Text>
            ) : null}
            <View style={styles.actionRow}>
              <Button
                label={
                  contact.isVip
                    ? t(locale, 'feature.contactDetail.relationshipHealth.removeVip')
                    : t(locale, 'feature.contactDetail.relationshipHealth.markVip')
                }
                tone="secondary"
                onPress={() => dispatch({ type: 'toggleContactVip', contactId: contact.id })}
              />
              <Button
                label={
                  contact.dnd
                    ? t(locale, 'feature.contactDetail.relationshipHealth.turnOffDnd')
                    : t(locale, 'feature.contactDetail.relationshipHealth.turnOnDnd')
                }
                tone="secondary"
                onPress={() => dispatch({ type: 'toggleContactDnd', contactId: contact.id })}
              />
              <Button
                label={t(locale, 'feature.contactDetail.relationshipHealth.useGroupDefaults')}
                tone="ghost"
                onPress={() => dispatch({ type: 'useGroupDefaultsForContact', contactId: contact.id })}
              />
            </View>
            <View style={styles.inlineItem}>
              <Text style={styles.smallText}>{t(locale, 'feature.contactDetail.relationshipHealth.whyScore')}</Text>
              {relationshipInsight.reasons.map(reason => (
                <Text key={reason} style={styles.smallText}>
                  - {reason}
                </Text>
              ))}
            </View>
          </Card>
        </>
      ) : null}

      {enrichmentPlan ? (
        <>
          <SectionTitle
            title={t(locale, 'feature.contactDetail.enrichment.title')}
            detail={t(locale, 'feature.contactDetail.enrichment.detail')}
          />
          <Card>
            <View style={styles.rowBetween}>
              <View style={styles.flex}>
                <Text style={styles.cardTitle}>
                  {t(locale, 'feature.contactDetail.enrichment.personalization', { score: enrichmentPlan.score })}
                </Text>
                <Text style={styles.smallText}>{enrichmentPlan.summary}</Text>
              </View>
              <Pill label={enrichmentPlan.label} selected={enrichmentPlan.label === 'Strong'} />
            </View>
            <View style={styles.wrapRow}>
              <Pill
                label={t(locale, 'feature.contactDetail.enrichment.signals', {
                  count: enrichmentPlan.completedSignals.length
                })}
                selected={enrichmentPlan.completedSignals.length > 0}
              />
              <Pill
                label={t(locale, 'feature.contactDetail.enrichment.missing', {
                  count: enrichmentPlan.missingSignals.length
                })}
                selected={enrichmentPlan.missingSignals.length === 0}
              />
            </View>
            {enrichmentPlan.completedSignals.length > 0 ? (
              <Text style={styles.smallText}>
                {t(locale, 'feature.contactDetail.enrichment.readySignals', {
                  signals: enrichmentPlan.completedSignals.join(', ')
                })}
              </Text>
            ) : null}
            {enrichmentPlan.prompts.length === 0 ? (
              <Text style={styles.bodyText}>{t(locale, 'feature.contactDetail.enrichment.enoughContext')}</Text>
            ) : (
              enrichmentPlan.prompts.map(prompt => {
                const answer = enrichmentAnswers[prompt.id] ?? '';
                return (
                  <View key={prompt.id} style={styles.inlineItem}>
                    <Text style={styles.bodyText}>{prompt.question}</Text>
                    <Text style={styles.smallText}>{prompt.reason}</Text>
                    <Text style={styles.smallText}>
                      {t(locale, 'feature.contactDetail.enrichment.improves', { signal: prompt.improvesSignal })}
                    </Text>
                    <TextInput
                      accessibilityLabel={prompt.question}
                      placeholder={t(locale, 'feature.contactDetail.enrichment.answerPlaceholder')}
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
                      label={t(locale, 'feature.contactDetail.enrichment.saveAnswer')}
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

      <SectionTitle title={t(locale, 'feature.contactDetail.tone.title')} detail={t(locale, 'feature.contactDetail.tone.detail')} />
      <Card>
        <View style={styles.wrapRow}>
          {tones.map(tone => (
            <Pill
              key={tone}
              label={localizedToneLabel(locale, tone)}
              selected={preferences.tone.includes(tone)}
              onPress={() => dispatch({ type: 'updateContactTone', contactId: contact.id, tone })}
            />
          ))}
        </View>
      </Card>

      <SectionTitle
        title={t(locale, 'feature.contactDetail.channel.title')}
        detail={t(locale, 'feature.contactDetail.channel.detail')}
      />
      <Card>
        <View style={styles.wrapRow}>
          {channels.map(channel => (
            <Pill
              key={channel}
              label={messageChannelLabel(locale, channel)}
              selected={preferences.preferredChannel === channel}
              onPress={() => dispatch({ type: 'setContactChannel', contactId: contact.id, channel })}
            />
          ))}
        </View>
      </Card>

      <SectionTitle
        title={t(locale, 'feature.contactDetail.memory.title')}
        detail={t(locale, 'feature.contactDetail.memory.detail')}
      />
      <Card>
        <TextInput
          accessibilityLabel={t(locale, 'feature.contactDetail.memory.searchLabel')}
          placeholder={t(locale, 'feature.contactDetail.memory.searchPlaceholder')}
          value={memoryQuery}
          onChangeText={setMemoryQuery}
          style={styles.input}
        />
        <Text style={styles.smallText}>
          {t(locale, 'feature.contactDetail.memory.stats', {
            visible: memoryVault.visibleCount,
            total: memoryVault.totalCount,
            eligible: memoryVault.aiEligibleCount,
            private: memoryVault.privateCount,
            pinned: memoryVault.pinnedCount
          })}
        </Text>
        <View style={styles.wrapRow}>
          {memoryCategories.map(category => (
            <Pill
              key={category}
              label={memoryCategoryLabel(locale, category)}
              selected={memoryCategory === category}
              onPress={() => setMemoryCategory(category)}
            />
          ))}
        </View>
        <Text style={styles.smallText}>
          {memoryCategory === 'Private'
            ? t(locale, 'feature.contactDetail.memory.privateModeDetail')
            : t(locale, 'feature.contactDetail.memory.sharedModeDetail')}
        </Text>
        <TextInput
          accessibilityLabel={t(locale, 'feature.contactDetail.memory.noteLabel')}
          placeholder={t(locale, 'feature.contactDetail.memory.notePlaceholder')}
          value={memoryText}
          onChangeText={setMemoryText}
          style={[styles.input, styles.multiline]}
          multiline
        />
        <Text style={memoryText.trim().length > MEMORY_NOTE_MAX_LENGTH ? styles.warningText : styles.smallText}>
          {t(locale, 'feature.contactDetail.memory.characterCount', {
            count: memoryText.trim().length,
            max: MEMORY_NOTE_MAX_LENGTH
          })}
        </Text>
        {memoryText.length > 0 && !memoryDraftValidation.ok ? (
          <Text style={styles.warningText}>{memoryDraftValidation.message}</Text>
        ) : null}
        <Button
          label={t(locale, 'feature.contactDetail.memory.addMemory')}
          disabled={!memoryDraftValidation.ok}
          onPress={() => {
            if (memoryDraftValidation.ok) {
              dispatch({ type: 'addMemory', contactId: contact.id, category: memoryCategory, body: memoryText });
              setMemoryText('');
            }
          }}
        />
        {memoryVault.emptyMessage ? <Text style={styles.smallText}>{memoryVault.emptyMessage}</Text> : null}
        {memoryVault.notes.map(({ note: memory }) => {
          const isEditing = editingMemoryId === memory.id;
          const confirmDelete = confirmDeleteMemoryId === memory.id;
          const memoryStatus = memory.pinned
            ? t(locale, 'feature.contactDetail.memory.pinned')
            : t(locale, 'feature.contactDetail.memory.recent');
          const categoryLabel = memoryCategoryLabel(locale, memory.category);
          return (
            <View key={memory.id} style={styles.inlineItem}>
              <View style={styles.rowBetween}>
                <View style={styles.flex}>
                  <Text style={styles.smallText}>
                    {t(locale, 'feature.contactDetail.memory.noteMeta', {
                      status: memoryStatus,
                      category: categoryLabel
                    })}
                  </Text>
                  <Text style={styles.smallText}>{memoryAiUseLabel(locale, memory.category)}</Text>
                </View>
                <Pill
                  label={memoryStatus}
                  selected={memory.pinned}
                />
              </View>
              {isEditing ? (
                <>
                  <View style={styles.wrapRow}>
                    {memoryCategories.map(category => (
                      <Pill
                        key={category}
                        label={memoryCategoryLabel(locale, category)}
                        selected={editingMemoryCategory === category}
                        onPress={() => setEditingMemoryCategory(category)}
                      />
                    ))}
                  </View>
                  <TextInput
                    accessibilityLabel={t(locale, 'feature.contactDetail.memory.editNoteLabel', {
                      category: categoryLabel
                    })}
                    value={editingMemoryText}
                    onChangeText={setEditingMemoryText}
                    style={[styles.input, styles.multiline]}
                    multiline
                  />
                  <Text style={editingMemoryText.trim().length > MEMORY_NOTE_MAX_LENGTH ? styles.warningText : styles.smallText}>
                    {t(locale, 'feature.contactDetail.memory.characterCount', {
                      count: editingMemoryText.trim().length,
                      max: MEMORY_NOTE_MAX_LENGTH
                    })}
                  </Text>
                  {editingMemoryValidation && !editingMemoryValidation.ok ? (
                    <Text style={styles.warningText}>{editingMemoryValidation.message}</Text>
                  ) : null}
                  <View style={styles.actionRow}>
                    <Button
                      label={t(locale, 'feature.contactDetail.memory.saveNote')}
                      disabled={!editingMemoryValidation?.ok}
                      onPress={() => {
                        if (editingMemoryValidation?.ok) {
                          dispatch({
                            type: 'editMemory',
                            memoryId: memory.id,
                            category: editingMemoryCategory,
                            body: editingMemoryText
                          });
                          setEditingMemoryId(undefined);
                          setEditingMemoryText('');
                          setConfirmDeleteMemoryId(undefined);
                        }
                      }}
                    />
                    <Button
                      label={t(locale, 'action.cancel')}
                      tone="secondary"
                      onPress={() => {
                        setEditingMemoryId(undefined);
                        setEditingMemoryText('');
                      }}
                    />
                  </View>
                </>
              ) : (
                <>
                  <Text style={styles.bodyText}>{memory.body}</Text>
                  <View style={styles.actionRow}>
                    <Button
                      label={t(locale, 'feature.contactDetail.memory.edit')}
                      tone="secondary"
                      onPress={() => {
                        setEditingMemoryId(memory.id);
                        setEditingMemoryText(memory.body);
                        setEditingMemoryCategory(memory.category);
                        setConfirmDeleteMemoryId(undefined);
                      }}
                    />
                    <Button
                      label={
                        memory.pinned
                          ? t(locale, 'feature.contactDetail.memory.unpin')
                          : t(locale, 'feature.contactDetail.memory.pin')
                      }
                      tone="secondary"
                      onPress={() => dispatch({ type: 'toggleMemoryPin', memoryId: memory.id })}
                    />
                    {confirmDelete ? (
                      <>
                        <Button
                          label={t(locale, 'feature.contactDetail.memory.confirmDelete')}
                          tone="danger"
                          onPress={() => {
                            dispatch({ type: 'deleteMemory', memoryId: memory.id });
                            setConfirmDeleteMemoryId(undefined);
                            if (editingMemoryId === memory.id) {
                              setEditingMemoryId(undefined);
                              setEditingMemoryText('');
                            }
                          }}
                        />
                        <Button
                          label={t(locale, 'feature.contactDetail.memory.cancelDelete')}
                          tone="ghost"
                          onPress={() => setConfirmDeleteMemoryId(undefined)}
                        />
                      </>
                    ) : (
                      <Button
                        label={t(locale, 'feature.contactDetail.memory.delete')}
                        tone="danger"
                        onPress={() => {
                          setConfirmDeleteMemoryId(memory.id);
                          setEditingMemoryId(undefined);
                        }}
                      />
                    )}
                  </View>
                </>
              )}
            </View>
          );
        })}
      </Card>

      <SectionTitle title={t(locale, 'feature.contactDetail.gift.title')} detail={t(locale, 'feature.contactDetail.gift.detail')} />
      <Card>
        <Text style={styles.bodyText}>
          {t(locale, 'feature.contactDetail.gift.budgetSummary', {
            annual: formatCurrencyForLocale(giftBudget.annualBudget, locale),
            spent: formatCurrencyForLocale(giftBudget.spentThisYear, locale),
            remaining: formatCurrencyForLocale(giftBudget.remaining, locale)
          })}
        </Text>
        {giftBudget.overBudget ? <Text style={styles.warningText}>{t(locale, 'feature.contactDetail.gift.overBudget')}</Text> : null}
        <View style={styles.actionRow}>
          <Button
            label={
              showGiftBudgetEditor
                ? t(locale, 'feature.contactDetail.gift.hideBudget')
                : t(locale, 'feature.contactDetail.gift.adjustBudget')
            }
            tone="secondary"
            onPress={() => setShowGiftBudgetEditor(current => !current)}
          />
        </View>
        {showGiftBudgetEditor ? (
          <View style={styles.inlineItem}>
            <TextInput
              accessibilityLabel={t(locale, 'feature.contactDetail.gift.annualBudget')}
              placeholder={t(locale, 'feature.contactDetail.gift.annualBudget')}
              value={giftBudgetDraft}
              onChangeText={setGiftBudgetDraft}
              keyboardType="numeric"
              style={styles.input}
            />
            {!giftBudgetValidation.ok ? <Text style={styles.warningText}>{giftBudgetValidation.message}</Text> : null}
            <View style={styles.actionRow}>
              <Button
                label={t(locale, 'feature.contactDetail.gift.saveBudget')}
                disabled={!giftBudgetValidation.ok}
                onPress={() => {
                  if (giftBudgetValidation.ok) {
                    dispatch({ type: 'updateGiftBudget', contactId: contact.id, annualGiftBudget: giftBudgetDraft });
                    setShowGiftBudgetEditor(false);
                  }
                }}
              />
              <Button
                label={t(locale, 'feature.contactDetail.gift.resetBudget')}
                tone="secondary"
                onPress={() => setGiftBudgetDraft(String(contact.annualGiftBudget))}
              />
            </View>
          </View>
        ) : null}
        <View style={styles.wrapRow}>
          {giftCategories.map(category => (
            <Pill
              key={category}
              label={giftCategoryLabel(locale, category)}
              selected={giftCategory === category}
              onPress={() => setGiftCategory(category)}
            />
          ))}
        </View>
        <TextInput
          accessibilityLabel={t(locale, 'feature.contactDetail.gift.name')}
          placeholder={t(locale, 'feature.contactDetail.gift.name')}
          value={giftName}
          onChangeText={setGiftName}
          style={styles.input}
        />
        <TextInput
          accessibilityLabel={t(locale, 'feature.contactDetail.gift.occasion')}
          placeholder={t(locale, 'feature.contactDetail.gift.occasion')}
          value={giftOccasion}
          onChangeText={setGiftOccasion}
          style={styles.input}
        />
        <TextInput
          accessibilityLabel={t(locale, 'feature.contactDetail.gift.cost')}
          placeholder={t(locale, 'feature.contactDetail.gift.cost')}
          value={giftCost}
          onChangeText={setGiftCost}
          style={styles.input}
          keyboardType="numeric"
        />
        <View style={styles.wrapRow}>
          {giftFeedbackOptions.map(feedback => (
            <Pill
              key={feedback}
              label={giftFeedbackLabel(locale, feedback)}
              selected={giftFeedback === feedback}
              onPress={() => setGiftFeedback(feedback)}
            />
          ))}
        </View>
        <TextInput
          accessibilityLabel={t(locale, 'feature.contactDetail.gift.notes')}
          placeholder={t(locale, 'feature.contactDetail.gift.notes')}
          value={giftNotes}
          onChangeText={setGiftNotes}
          style={[styles.input, styles.multiline]}
          multiline
        />
        <Button
          label={t(locale, 'feature.contactDetail.gift.record')}
          onPress={() => {
            const parsedCost = Number(giftCost);
            dispatch({
              type: 'addGift',
              contactId: contact.id,
              name: giftName,
              category: giftCategory,
              occasion: giftOccasion,
              cost: Number.isFinite(parsedCost) ? parsedCost : -1,
              feedback: giftFeedback,
              notes: giftNotes
            });
            if (giftName.trim().length >= 2 && giftOccasion.trim().length >= 2 && Number.isFinite(parsedCost) && parsedCost >= 0) {
              setGiftName('');
              setGiftNotes('');
              setGiftCost('0');
              setGiftFeedback('Unknown');
            }
          }}
        />
        <View style={styles.inlineItem}>
          <Text style={styles.cardTitle}>{t(locale, 'feature.contactDetail.gift.suggestions')}</Text>
          {giftSuggestions.map(suggestion => (
            <View key={suggestion.id} style={styles.inlineItem}>
              <View style={styles.rowBetween}>
                <View style={styles.flex}>
                  <Text style={styles.bodyText}>{suggestion.name}</Text>
                  <Text style={styles.smallText}>
                    {t(locale, 'feature.contactDetail.gift.suggestionMeta', {
                      category: giftCategoryLabel(locale, suggestion.category),
                      cost: formatCurrencyForLocale(suggestion.estimatedCost, locale)
                    })}
                  </Text>
                </View>
                <Pill
                  label={giftSuggestionConfidenceLabel(locale, suggestion.confidence)}
                  selected={suggestion.confidence === 'High'}
                />
              </View>
              <Text style={styles.smallText}>{suggestion.rationale}</Text>
              <Text style={suggestion.budgetFit === 'Over budget' ? styles.warningText : styles.smallText}>
                {giftBudgetFitLabel(locale, suggestion.budgetFit)}
              </Text>
              {suggestion.duplicateWarning ? <Text style={styles.warningText}>{suggestion.duplicateWarning}</Text> : null}
              <Button
                label={t(locale, 'feature.contactDetail.gift.useSuggestion')}
                tone="secondary"
                onPress={() => useGiftSuggestion(suggestion)}
              />
            </View>
          ))}
        </View>
        {contactGifts.map(gift => (
          <View key={gift.id} style={styles.inlineItem}>
            <Text style={styles.bodyText}>{gift.name}</Text>
            <Text style={styles.smallText}>
              {t(locale, 'feature.contactDetail.gift.historyMeta', {
                category: giftCategoryLabel(locale, gift.category),
                occasion: gift.occasion,
                year: gift.year,
                cost: formatCurrencyForLocale(gift.cost, locale),
                feedback: giftFeedbackLabel(locale, gift.feedback)
              })}
            </Text>
            <Text style={styles.smallText}>{gift.notes}</Text>
            <View style={styles.actionRow}>
              {confirmDeleteGiftId === gift.id ? (
                <>
                  <Button
                    label={t(locale, 'feature.contactDetail.gift.confirmDelete')}
                    tone="danger"
                    onPress={() => {
                      dispatch({ type: 'deleteGift', giftId: gift.id });
                      setConfirmDeleteGiftId(undefined);
                    }}
                  />
                  <Button
                    label={t(locale, 'feature.contactDetail.gift.cancelDelete')}
                    tone="ghost"
                    onPress={() => setConfirmDeleteGiftId(undefined)}
                  />
                </>
              ) : (
                <Button
                  label={t(locale, 'feature.contactDetail.gift.deleteGift')}
                  tone="danger"
                  onPress={() => setConfirmDeleteGiftId(gift.id)}
                />
              )}
            </View>
          </View>
        ))}
      </Card>

      <SectionTitle title={t(locale, 'feature.contactDetail.timeline.title')} detail={t(locale, 'feature.contactDetail.timeline.detail')} />
      <Card>
        <View style={styles.wrapRow}>
          {contactTimelineFilters.map(filter => (
            <Pill
              key={filter}
              label={contactTimelineFilterLabel(locale, filter)}
              selected={timelineFilter === filter}
              onPress={() => setTimelineFilter(filter)}
            />
          ))}
        </View>
        <Text style={styles.smallText}>
          {t(locale, 'feature.contactDetail.timeline.summary', {
            events: contactEvents.length,
            memories: memoryVault.totalCount,
            gifts: contactGifts.length,
            sent: contactMessages.filter(message => message.status === 'Sent').length
          })}
        </Text>
        {timeline.entries.length === 0 ? <Text style={styles.bodyText}>{contactTimelineEmptyMessage(locale, timelineFilter)}</Text> : null}
        {timeline.entries.map(entry => {
          const entryTypeLabel = contactTimelineEntryTypeLabel(locale, entry.type);
          const entryTitle = contactTimelineEntryTitle(locale, entry, state);
          const entryDetail = contactTimelineEntryDetail(locale, entry, state);
          return (
            <View key={`${entry.type}-${entry.id}`} style={styles.inlineItem}>
              <View style={styles.rowBetween}>
                <View style={styles.flex}>
                  <Text style={styles.bodyText}>{entryTitle}</Text>
                  <Text style={styles.smallText}>
                    {entryTypeLabel} - {formatDateForLocale(entry.dateIso, state.settings.locale)}
                  </Text>
                </View>
                <Pill label={entryTypeLabel} selected />
              </View>
              <Text style={styles.smallText}>{entryDetail}</Text>
              {entry.targetScreen && entry.targetScreen !== 'contactDetail' ? (
                <Button
                  label={t(locale, 'feature.contactDetail.timeline.open')}
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
          );
        })}
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
  const locale = state.settings.locale;
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
        title={t(locale, 'feature.chatHistory.title')}
        detail={
          contact
            ? t(locale, 'feature.chatHistory.detail', { name: contact.name })
            : t(locale, 'feature.chatHistory.detailUnavailable')
        }
      />
      <Card>
        <TextInput
          accessibilityLabel={t(locale, 'feature.chatHistory.searchLabel')}
          placeholder={t(locale, 'feature.chatHistory.searchPlaceholder')}
          placeholderTextColor={colors.muted}
          value={searchQuery}
          onChangeText={setSearchQuery}
          style={styles.input}
        />
        <View style={styles.wrapRow}>
          {chatHistoryChannels.map(item => (
            <Pill key={item} label={messageChannelLabel(locale, item)} selected={channel === item} onPress={() => setChannel(item)} />
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
                  {t(locale, 'feature.chatHistory.messageMeta', {
                    channel: messageChannelLabel(locale, message.channel),
                    date: formatDateForLocale(message.sentAt ?? message.scheduledFor, state.settings.locale)
                  })}
                </Text>
              </View>
              <Pill label={messageChannelLabel(locale, message.channel)} selected />
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
              ? t(locale, 'feature.chatHistory.empty.noMatching')
              : history.emptyState === 'Contact unavailable'
                ? t(locale, 'feature.chatHistory.empty.contactUnavailable')
                : t(locale, 'feature.chatHistory.empty.noneSent')}
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
  const locale = state.settings.locale;
  const contact = getContact(state.contacts, message.contactId);
  const event = getEvent(state.events, message.eventId);
  const [excludedMemoryIds, setExcludedMemoryIds] = useState<string[]>([]);
  const [includePriorMessages, setIncludePriorMessages] = useState(true);
  const [selectedFeedbackOptionIds, setSelectedFeedbackOptionIds] = useState<WishFeedbackOptionId[]>([]);
  const [customFeedback, setCustomFeedback] = useState('');
  useEffect(() => {
    setExcludedMemoryIds([]);
    setIncludePriorMessages(true);
    setSelectedFeedbackOptionIds([]);
    setCustomFeedback('');
  }, [message.id]);
  const aiContextPreview = buildAiContextPreview(state, message.contactId, message.eventId, {
    excludedMemoryIds,
    includePriorMessages
  });
  const tonePreferenceSummary = buildTonePreferenceSummary(state, message.contactId, message.id);
  const feedbackPlan = buildWishFeedbackPlan(message, {
    selectedOptionIds: selectedFeedbackOptionIds,
    customText: customFeedback
  });
  const duplicateNeedsAcknowledgement = Boolean(message.duplicateWarning && !message.duplicateAcknowledged);
  const messageBodyPolicy = validateMessageBodyForChannel(message);
  const canApprove = messageBodyPolicy.ok && message.status !== 'Rejected' && !duplicateNeedsAcknowledgement;
  const canTestRoute = message.status !== 'Sent' && message.status !== 'Rejected';
  const canRegenerate = feedbackPlan.action.enabled;
  const hasEditedMessageBody = message.body !== message.variants[message.selectedVariant];
  const toggleMemoryContext = (memoryId: string) => {
    setExcludedMemoryIds(current =>
      current.includes(memoryId) ? current.filter(id => id !== memoryId) : [...current, memoryId]
    );
  };
  const toggleFeedbackOption = (optionId: WishFeedbackOptionId) => {
    setSelectedFeedbackOptionIds(current =>
      current.includes(optionId) ? current.filter(id => id !== optionId) : [...current, optionId]
    );
  };
  const dispatchVariantSelection = (variant: MessageDraft['selectedVariant'], discardEditedBody = false) =>
    dispatch({ type: 'selectVariant', messageId: message.id, variant, discardEditedBody });
  const selectMessageVariant = (variant: MessageDraft['selectedVariant']) => {
    if (variant === message.selectedVariant) {
      return;
    }
    if (!hasEditedMessageBody) {
      dispatchVariantSelection(variant);
      return;
    }
    showAppAlert(
      t(locale, 'feature.wishPreview.confirmVariantTitle'),
      t(locale, 'feature.wishPreview.confirmVariantBody', {
        variant: messageVariantLabel(locale, variant)
      }),
      [
        { text: t(locale, 'action.cancel'), style: 'cancel' },
        {
          text: t(locale, 'feature.wishPreview.confirmVariantAction'),
          style: 'destructive',
          onPress: () => dispatchVariantSelection(variant, true)
        }
      ]
    );
  };
  const approveAndReviewNext = () => {
    showAppAlert(
      t(locale, 'feature.wishPreview.confirmApproveTitle'),
      t(locale, 'feature.wishPreview.confirmApproveBody'),
      [
        { text: t(locale, 'action.cancel'), style: 'cancel' },
        {
          text: t(locale, 'feature.wishPreview.action.approve'),
          onPress: () => dispatch({ type: 'approveMessage', messageId: message.id, reviewNext: true })
        }
      ]
    );
  };
  const rejectAndReviewNext = () => {
    showAppAlert(t(locale, 'feature.wishPreview.confirmRejectTitle'), t(locale, 'feature.wishPreview.confirmRejectBody'), [
      { text: t(locale, 'action.cancel'), style: 'cancel' },
      {
        text: t(locale, 'feature.wishPreview.action.reject'),
        style: 'destructive',
        onPress: () => dispatch({ type: 'rejectMessage', messageId: message.id, reviewNext: true })
      }
    ]);
  };

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <SectionTitle
        title={t(locale, 'feature.wishPreview.title')}
        detail={t(locale, 'feature.wishPreview.detail', {
          name: contact?.name ?? t(locale, 'common.unknownContact'),
          reason: message.reason
        })}
      />
      <Card>
        <View style={styles.wrapRow}>
          <Pill label={messageStatusLabel(locale, message.status)} selected />
          <Pill label={messageChannelLabel(locale, message.channel)} />
          <Pill label={messageQualityLabel(locale, message.quality)} />
        </View>
        <Text style={styles.smallText}>
          {t(locale, 'feature.wishPreview.scheduledFor', {
            date: formatDateForLocale(message.scheduledFor ?? event?.date, state.settings.locale)
          })}
        </Text>
        {message.duplicateWarning ? <Text style={styles.warningText}>{message.duplicateWarning}</Text> : null}
        {message.duplicateAcknowledged ? (
          <Text style={styles.smallText}>{t(locale, 'feature.wishPreview.duplicateAcknowledged')}</Text>
        ) : null}
      </Card>

      <SectionTitle title={t(locale, 'feature.wishPreview.toneTitle')} detail={tonePreferenceSummary.sourceLabel} />
      <Card>
        <Text style={styles.bodyText}>{tonePreferenceSummary.influenceSummary}</Text>
        <View style={styles.wrapRow}>
          {tonePreferenceSummary.tones.map(tone => (
            <Pill key={tone} label={localizedToneLabel(locale, tone)} selected />
          ))}
        </View>
        <Text style={styles.smallText}>{tonePreferenceSummary.controlSummary}</Text>
        {tonePreferenceSummary.detailItems.map(item => (
          <Text key={item} style={styles.smallText}>
            - {item}
          </Text>
        ))}
        {tonePreferenceSummary.warnings.map(warning => (
          <Text key={warning} style={styles.warningText}>
            {warning}
          </Text>
        ))}
        <Button
          label={tonePreferenceSummary.adjustAction.label}
          tone="secondary"
          disabled={!tonePreferenceSummary.adjustAction.enabled}
          accessibilityLabel={tonePreferenceSummary.adjustAction.reason}
          onPress={() =>
            dispatch({
              type: 'navigate',
              screen: tonePreferenceSummary.adjustAction.screen,
              contactId: tonePreferenceSummary.adjustAction.contactId
            })
          }
        />
      </Card>

      <SectionTitle
        title={t(locale, 'feature.wishPreview.aiContextTitle')}
        detail={t(locale, 'feature.wishPreview.aiContextDetail')}
      />
      <Card>
        <Text style={styles.smallText}>{aiContextPreview.summary}</Text>
        <Text style={styles.cardTitle}>{t(locale, 'feature.wishPreview.aiContext.alwaysUsed')}</Text>
        {aiContextPreview.alwaysUsed.map(item => (
          <Text key={item} style={styles.bodyText}>
            - {item}
          </Text>
        ))}
        <View style={styles.inlineItem}>
          <Text style={styles.cardTitle}>{t(locale, 'feature.wishPreview.aiContext.optionalMemories')}</Text>
          {aiContextPreview.optionalMemories.length === 0 ? (
            <Text style={styles.bodyText}>{t(locale, 'feature.wishPreview.aiContext.noOptionalMemories')}</Text>
          ) : (
            aiContextPreview.optionalMemories.map(memory => (
              <View key={memory.id} style={styles.inlineItem}>
                <View style={styles.rowBetween}>
                  <Text style={styles.smallText}>{memory.category}</Text>
                  <Pill
                    label={t(
                      locale,
                      memory.selected
                        ? 'feature.wishPreview.aiContext.included'
                        : 'feature.wishPreview.aiContext.excluded'
                    )}
                    selected={memory.selected}
                    onPress={() => toggleMemoryContext(memory.id)}
                  />
                </View>
                <Text style={styles.bodyText}>{memory.body}</Text>
              </View>
            ))
          )}
        </View>
        <View style={styles.inlineItem}>
          <View style={styles.rowBetween}>
            <View style={styles.flex}>
              <Text style={styles.cardTitle}>{t(locale, 'feature.wishPreview.aiContext.priorMessages')}</Text>
              <Text style={styles.smallText}>
                {t(locale, 'feature.wishPreview.aiContext.priorMessagesDetail', {
                  count: aiContextPreview.priorMessages.count
                })}
              </Text>
            </View>
            <Pill
              label={t(
                locale,
                aiContextPreview.priorMessages.selected
                  ? 'feature.wishPreview.aiContext.included'
                  : 'feature.wishPreview.aiContext.excluded'
              )}
              selected={aiContextPreview.priorMessages.selected}
              onPress={() => setIncludePriorMessages(value => !value)}
            />
          </View>
        </View>
      </Card>

      <SectionTitle title={t(locale, 'feature.wishPreview.variantTitle')} />
      <Card>
        <View style={styles.wrapRow}>
          {(['short', 'standard', 'warm'] as MessageDraft['selectedVariant'][]).map(variant => (
            <Pill
              key={variant}
              label={messageVariantLabel(locale, variant)}
              selected={message.selectedVariant === variant}
              onPress={() => selectMessageVariant(variant)}
            />
          ))}
        </View>
      </Card>

      <SectionTitle
        title={t(locale, 'feature.wishPreview.feedbackTitle')}
        detail={t(locale, 'feature.wishPreview.feedbackDetail')}
      />
      <Card>
        <Text style={styles.bodyText}>{feedbackPlan.improvementSummary}</Text>
        <View style={styles.wrapRow}>
          {feedbackPlan.options.map(option => (
            <Pill
              key={option.id}
              label={option.label}
              selected={selectedFeedbackOptionIds.includes(option.id)}
              accessibilityLabel={option.detail}
              onPress={() => toggleFeedbackOption(option.id)}
            />
          ))}
        </View>
        <TextInput
          accessibilityLabel={t(locale, 'feature.wishPreview.feedbackCustomLabel')}
          placeholder={t(locale, 'feature.wishPreview.feedbackCustomPlaceholder')}
          placeholderTextColor={colors.muted}
          value={customFeedback}
          onChangeText={setCustomFeedback}
          style={[styles.input, styles.multiline]}
          multiline
        />
        <Text style={feedbackPlan.action.enabled ? styles.smallText : styles.warningText}>
          {feedbackPlan.action.detail} {feedbackPlan.characterCount}/240
        </Text>
        {message.regenerationFeedback ? (
          <Text style={styles.smallText}>
            {t(
              locale,
              message.regenerationFeedback.customInstruction
                ? 'feature.wishPreview.feedbackLastUsedWithCustom'
                : 'feature.wishPreview.feedbackLastUsed',
              { count: message.regenerationFeedback.instructions.length }
            )}
          </Text>
        ) : null}
        {feedbackPlan.warnings.map(warning => (
          <Text key={warning} style={styles.warningText}>
            {warning}
          </Text>
        ))}
      </Card>

      <SectionTitle title={t(locale, 'feature.wishPreview.messageTitle')} detail={message.readiness} />
      <Card>
        <TextInput
          accessibilityLabel={t(locale, 'feature.wishPreview.messageTextLabel')}
          value={message.body}
          onChangeText={body => dispatch({ type: 'editMessage', messageId: message.id, body })}
          style={[styles.input, styles.messageInput]}
          multiline
        />
        {!messageBodyPolicy.ok ? <Text style={styles.warningText}>{messageBodyPolicy.message}</Text> : null}
        {messageBodyPolicy.ok && messageBodyPolicy.warning ? (
          <Text style={styles.smallText}>{messageBodyPolicy.warning}</Text>
        ) : null}
        <View style={styles.actionRow}>
          <Button
            label={t(locale, 'feature.wishPreview.action.testSend')}
            tone="secondary"
            disabled={!canTestRoute}
            onPress={() => dispatch({ type: 'testMessageRoute', messageId: message.id })}
          />
          <Button label={t(locale, 'feature.wishPreview.action.approve')} disabled={!canApprove} onPress={approveAndReviewNext} />
          {duplicateNeedsAcknowledgement ? (
            <Button
              label={t(locale, 'feature.wishPreview.action.continueAnyway')}
              tone="secondary"
              onPress={() => dispatch({ type: 'acknowledgeDuplicateRisk', messageId: message.id })}
            />
          ) : null}
          <Button
            label={t(locale, 'feature.wishPreview.action.regenerate')}
            tone="secondary"
            disabled={!canRegenerate}
            onPress={() =>
              onGenerateMessage(message.contactId, message.eventId, message.reason, {
                excludedMemoryIds,
                includePriorMessages,
                feedback: feedbackPlan.requestFeedback
              })
            }
          />
          <Button label={t(locale, 'feature.wishPreview.action.reject')} tone="danger" onPress={rejectAndReviewNext} />
          {message.status === 'Scheduled' && message.channel === 'Email' ? (
            <Button label={t(locale, 'feature.wishPreview.action.sendEmail')} onPress={() => onSendEmail(message)} />
          ) : null}
          {message.status === 'Scheduled' ? (
            <Button label={t(locale, 'feature.wishPreview.action.manualHandoff')} tone="secondary" onPress={() => onManualHandoff(message)} />
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
  state: ReturnType<typeof createProductionInitialState>;
  dispatch: React.Dispatch<RelateAction>;
  onGenerateMessage: (
    contactId: string,
    eventId: string | undefined,
    reason: ComposerReason,
    contextOptions?: AiDraftContextOptions
  ) => void;
}) => {
  const locale = state.settings.locale;
  const [reason, setReason] = useState<ComposerReason>('Check-in');
  const initialComposerState = buildManualComposerState(state, contact.id, 'Check-in');
  const [selectedTemplateId, setSelectedTemplateId] = useState(
    initialComposerState.ok ? (initialComposerState.selectedTemplateId ?? '') : ''
  );
  const [templateBody, setTemplateBody] = useState(initialComposerState.renderedTemplateBody);
  const composerModel = useMemo(
    () => buildManualComposerState(state, contact.id, reason, templateBody, selectedTemplateId),
    [contact.id, reason, selectedTemplateId, state, templateBody]
  );
  const templates = composerModel.templates;
  const selectedTemplate = composerModel.ok ? composerModel.selectedTemplate : undefined;

  useEffect(() => {
    const nextComposerState = buildManualComposerState(state, contact.id, reason, undefined, selectedTemplateId);
    if (nextComposerState.ok && nextComposerState.selectedTemplateId !== selectedTemplateId) {
      setSelectedTemplateId(nextComposerState.selectedTemplateId ?? '');
    }
    setTemplateBody(nextComposerState.renderedTemplateBody);
  }, [contact, reason, selectedTemplateId, state.memories, state.settings]);

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <SectionTitle
        title={t(locale, 'feature.manualComposer.title')}
        detail={t(locale, 'feature.manualComposer.detail', { name: contact.name })}
      />
      <Card>
        <Text style={styles.bodyText}>{t(locale, 'feature.manualComposer.intro')}</Text>
        <View style={styles.wrapRow}>
          {manualComposerReasons.map(item => (
            <Pill key={item} label={composerReasonLabel(locale, item)} selected={reason === item} onPress={() => setReason(item)} />
          ))}
        </View>
        <Text style={styles.cardTitle}>{t(locale, 'feature.manualComposer.templates')}</Text>
        <Text style={styles.smallText}>{composerModel.context.detail}</Text>
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
          accessibilityLabel={t(locale, 'feature.manualComposer.templateMessageLabel')}
          placeholder={t(locale, 'feature.manualComposer.templateMessagePlaceholder')}
          placeholderTextColor={colors.muted}
          value={templateBody}
          onChangeText={setTemplateBody}
          style={[styles.input, styles.messageInput]}
          multiline
        />
        <Text style={composerModel.templateAction.status === 'Blocked' ? styles.warningText : styles.smallText}>
          {composerModel.templateAction.detail}
        </Text>
        <View style={styles.actionRow}>
          <Button
            label={composerModel.templateAction.label}
            disabled={!composerModel.templateAction.enabled}
            onPress={() =>
              dispatch({
                type: 'createTemplateDraft',
                contactId: contact.id,
                reason,
                body: templateBody,
                templateId: composerModel.ok ? composerModel.selectedTemplateId : selectedTemplate?.id
              })
            }
          />
          <Button
            label={composerModel.aiAction.label}
            tone="secondary"
            disabled={!composerModel.aiAction.enabled}
            onPress={() => onGenerateMessage(contact.id, undefined, reason)}
          />
        </View>
        <Text style={composerModel.aiAction.status === 'Ready' ? styles.smallText : styles.warningText}>
          {composerModel.aiAction.detail}
        </Text>
      </Card>
    </ScrollView>
  );
};

const MoreScreen = ({
  state,
  dispatch,
  onImportDeviceContacts,
  onScheduleReminders,
  onExportCalendar,
  onImportCalendar,
  onTestAiProvider,
  onExportBackup,
  onPickBackup,
  onRestoreBackup,
  onClearLocalData
}: ScreenProps) => {
  const importDevice = onImportDeviceContacts ?? (() => undefined);
  const scheduleReminders = onScheduleReminders ?? (() => undefined);
  const exportCalendar = onExportCalendar ?? (() => undefined);
  const importCalendar = onImportCalendar ?? (() => undefined);
  const testAiProvider = onTestAiProvider ?? (() => undefined);
  const exportBackup = onExportBackup ?? (() => undefined);
  const pickBackup = onPickBackup ?? (async () => undefined);
  const restoreBackup = onRestoreBackup ?? (() => undefined);
  const clearLocalData = onClearLocalData ?? (() => undefined);
  const aiConfig = readAiProviderConfig();
  const emailConfig = readEmailSenderConfig();
  const aiEndpointReadiness = evaluateProviderEndpointReadiness(aiConfig.endpoint, {
    allowLocalDevelopment: aiConfig.allowLocalProviderEndpoint
  });
  const emailEndpointReadiness = evaluateProviderEndpointReadiness(emailConfig.endpoint, {
    allowLocalDevelopment: emailConfig.allowLocalProviderEndpoint
  });
  const [backupPassphrase, setBackupPassphrase] = useState('');
  const [selectedBackup, setSelectedBackup] = useState<BackupFilePickResult | undefined>();
  const [styleSamples, setStyleSamples] = useState('');
  const [setupGoal, setSetupGoal] = useState<SetupGoal>('Reminders only');
  const [showSetupCheckDetails, setShowSetupCheckDetails] = useState(false);
  const [showEmailProviderSetup, setShowEmailProviderSetup] = useState(false);
  const [showAdvancedAutomationModes, setShowAdvancedAutomationModes] = useState(false);
  const [analyticsRange, setAnalyticsRange] = useState<AnalyticsRange>('Last 30 days');
  const [showAnalyticsExportTools, setShowAnalyticsExportTools] = useState(false);
  const [quietStart, setQuietStart] = useState(state.settings.quietHours.start);
  const [quietEnd, setQuietEnd] = useState(state.settings.quietHours.end);
  const [blackoutLabel, setBlackoutLabel] = useState('');
  const [blackoutStart, setBlackoutStart] = useState('');
  const [blackoutEnd, setBlackoutEnd] = useState('');
  const [eventImportFormat, setEventImportFormat] = useState<EventImportFormat>('auto');
  const [eventImportText, setEventImportText] = useState('');
  const [eventImportSummary, setEventImportSummary] = useState('');
  const [eventImportErrors, setEventImportErrors] = useState<string[]>([]);
  const [activityQuery, setActivityQuery] = useState('');
  const [activityType, setActivityType] = useState<ActivityTypeFilter>('All');
  const [activitySeverity, setActivitySeverity] = useState<ActivitySeverityFilter>('All');
  const [activityDate, setActivityDate] = useState<ActivityDateFilter>('Last 7 days');
  const [templateLibraryContactId, setTemplateLibraryContactId] = useState(
    state.selectedContactId ?? state.contacts[0]?.id ?? ''
  );
  const [templateLibraryReason, setTemplateLibraryReason] = useState<ComposerReason>('Birthday');
  const [templateLibraryTone, setTemplateLibraryTone] = useState<Tone>('Warm');
  const [templateLibraryTemplateId, setTemplateLibraryTemplateId] = useState('');
  const initialTemplateLibrary = buildMessageTemplateLibrary(state, {
    contactId: templateLibraryContactId,
    reason: templateLibraryReason,
    tone: templateLibraryTone
  });
  const [templateLibraryBody, setTemplateLibraryBody] = useState(initialTemplateLibrary.renderedBody);
  const templateLibrary = useMemo(
    () =>
      buildMessageTemplateLibrary(state, {
        contactId: templateLibraryContactId,
        reason: templateLibraryReason,
        tone: templateLibraryTone,
        selectedTemplateId: templateLibraryTemplateId,
        draftBody: templateLibraryBody
      }),
    [
      state,
      templateLibraryBody,
      templateLibraryContactId,
      templateLibraryReason,
      templateLibraryTemplateId,
      templateLibraryTone
    ]
  );
  const passphraseProblems = validateBackupPassphrase(backupPassphrase);
  const canUsePassphrase = passphraseProblems.length === 0;
  const canRestore =
    Boolean(selectedBackup) && canUsePassphrase && (selectedBackup?.preview.warnings.length ?? 0) === 0;
  const locale = state.settings.locale;
  const statusLabel = (enabled: boolean) => t(locale, enabled ? 'status.on' : 'status.off');
  const persistenceStatusLabel = (status: typeof state.persistence.status) => {
    switch (status) {
      case 'Loading':
        return t(locale, 'feature.more.persistence.loading');
      case 'Ready':
        return t(locale, 'feature.more.persistence.ready');
      case 'Saving':
        return t(locale, 'feature.more.persistence.saving');
      case 'Error':
        return t(locale, 'feature.more.persistence.error');
    }
  };
  const setupStatusLabel = (status: SetupStep['status'] | SetupDoctorCheck['status']) => {
    return setupStatusDisplayLabel(locale, status);
  };
  const setupGoalLabel = (goal: SetupGoal) => {
    switch (goal) {
      case 'Reminders only':
        return t(locale, 'feature.more.setupWizard.goal.remindersOnly');
      case 'AI drafts':
        return t(locale, 'feature.more.setupWizard.goal.aiDrafts');
      case 'Manual sends':
        return t(locale, 'feature.more.setupWizard.goal.manualSends');
      case 'Automation':
        return t(locale, 'feature.more.setupWizard.goal.automation');
    }
  };
  const setupGoalFromSummary = (value: string): SetupGoal | undefined =>
    setupGoals.find(goal => goal.toLowerCase() === value.toLowerCase());
  const setupWizardSummaryLabel = () => {
    const readyMatch = setupPlan.summary.match(/^(.+) setup is ready\.$/);
    if (readyMatch) {
      const goal = setupGoalFromSummary(readyMatch[1]);
      return t(locale, 'feature.more.setupWizard.summaryReady', {
        goal: goal ? setupGoalLabel(goal) : readyMatch[1]
      });
    }
    const progressMatch = setupPlan.summary.match(/^(\d+)\/(\d+) setup step\(s\) ready for (.+)\.$/);
    if (progressMatch) {
      const goal = setupGoalFromSummary(progressMatch[3]);
      return t(locale, 'feature.more.setupWizard.summaryProgress', {
        ready: progressMatch[1],
        total: progressMatch[2],
        goal: goal ? setupGoalLabel(goal) : progressMatch[3]
      });
    }
    return setupPlan.summary;
  };
  const setupWizardStepTitleLabel = (step: SetupStep) => {
    const keyById: Record<string, TranslationKey> = {
      notifications: 'feature.more.setupWizard.step.notifications.title',
      'scheduling-policy': 'feature.more.setupWizard.step.schedulingPolicy.title',
      events: 'feature.more.setupWizard.step.events.title',
      'reminder-plans': 'feature.more.setupWizard.step.reminderPlans.title',
      'ai-toggle': 'feature.more.setupWizard.step.aiToggle.title',
      'ai-provider': 'feature.more.setupWizard.step.aiProvider.title',
      personalization: 'feature.more.setupWizard.step.personalization.title',
      contacts: 'feature.more.setupWizard.step.contacts.title',
      'manual-whatsapp': 'feature.more.setupWizard.step.manualWhatsApp.title',
      'email-route': 'feature.more.setupWizard.step.emailRoute.title',
      'automation-mode': 'feature.more.setupWizard.step.automationMode.title'
    };
    const key = keyById[step.id];
    return key ? t(locale, key) : step.title;
  };
  const setupWizardStepActionLabel = (step: SetupStep) => {
    const keyByAction: Record<string, TranslationKey> = {
      Configured: 'feature.more.setupWizard.action.configured',
      'Enable notifications': 'feature.more.setupWizard.action.enableNotifications',
      'Review scheduling': 'feature.more.setupWizard.action.reviewScheduling',
      'Review events': 'feature.more.setupWizard.action.reviewEvents',
      'Add event': 'feature.more.setupWizard.action.addEvent',
      'Review reminders': 'feature.more.setupWizard.action.reviewReminders',
      'Plan reminders': 'feature.more.setupWizard.action.planReminders',
      Enabled: 'feature.more.setupWizard.action.enabled',
      'Enable AI': 'feature.more.setupWizard.action.enableAi',
      'Test AI provider': 'feature.more.setupWizard.action.testAiProvider',
      'Configure endpoint': 'feature.more.setupWizard.action.configureEndpoint',
      'Review contacts': 'feature.more.setupWizard.action.reviewContacts',
      'Review channel settings': 'feature.more.setupWizard.action.reviewChannelSettings',
      'Review email settings': 'feature.more.setupWizard.action.reviewEmailSettings',
      'Review automation settings': 'feature.more.setupWizard.action.reviewAutomationSettings'
    };
    return t(locale, keyByAction[step.action] ?? 'feature.more.setupWizard.action.reviewSetup');
  };
  const setupWizardStepDetailLabel = (step: SetupStep) => {
    const exactDetailKeyByDetail: Record<string, TranslationKey> = {
      'Reminder notifications are enabled.': 'feature.more.setupWizard.detail.notificationsEnabled',
      'Enable notifications so reminders can reach you outside the app.':
        'feature.more.setupWizard.detail.notificationsDisabled',
      'Add or import at least one birthday, anniversary, or custom event.':
        'feature.more.setupWizard.detail.eventsMissing',
      'Plan reminders after events and notification preferences are ready.':
        'feature.more.setupWizard.detail.reminderPlansMissing',
      'AI drafting is enabled, with review-first fallback rules.': 'feature.more.setupWizard.detail.aiEnabled',
      'Turn on AI drafting or use local templates instead.': 'feature.more.setupWizard.detail.aiDisabled',
      'Configure a secure backend endpoint before relying on provider drafts.':
        'feature.more.setupWizard.detail.aiProviderMissing',
      'Local provider endpoint is allowed for development only. Use HTTPS before release.':
        'feature.more.setupWizard.detail.aiProviderDevelopmentOnly',
      'Provider endpoint is configured but not safe for production. Use HTTPS without credentials, localhost, or private-network hosts.':
        'feature.more.setupWizard.detail.aiProviderUnsafe',
      'At least one non-private memory can improve draft specificity.':
        'feature.more.setupWizard.detail.personalizationReady',
      'Add non-private memories or notes so drafts do not feel generic.':
        'feature.more.setupWizard.detail.personalizationMissing',
      'Contacts have at least one usable manual route.': 'feature.more.setupWizard.detail.contactsReady',
      'Add phone, email, or manual route details before sending.': 'feature.more.setupWizard.detail.contactsMissing',
      'Manual WhatsApp handoff is enabled and remains user-controlled.':
        'feature.more.setupWizard.detail.manualWhatsAppEnabled',
      'Enable manual WhatsApp handoff if you want WhatsApp routing.':
        'feature.more.setupWizard.detail.manualWhatsAppDisabled',
      'Email provider endpoint is configured for optional provider delivery.':
        'feature.more.setupWizard.detail.emailReady',
      'Configured email endpoint is local-development only. Use HTTPS before release.':
        'feature.more.setupWizard.detail.emailDevelopmentOnly',
      'Configured email endpoint is not safe for production. Use HTTPS without credentials, localhost, or private-network hosts, or use handoff fallback.':
        'feature.more.setupWizard.detail.emailUnsafe',
      'Email handoff is available; provider delivery stays optional until you configure an endpoint.':
        'feature.more.setupWizard.detail.emailProviderOptional',
      'Email can stay off unless you need provider delivery.': 'feature.more.setupWizard.detail.emailOptional',
      'Always ask is safest while setup is incomplete.': 'feature.more.setupWizard.detail.automationAlwaysAsk'
    };
    const exactKey = exactDetailKeyByDetail[step.detail];
    if (exactKey) {
      return t(locale, exactKey);
    }
    const schedulingMatch = step.detail.match(
      /^Reminder planning respects (.+)-(.+) quiet hours and (\d+) blackout window\(s\)\.$/
    );
    if (schedulingMatch) {
      return t(locale, 'feature.more.setupWizard.detail.schedulingReady', {
        start: schedulingMatch[1],
        end: schedulingMatch[2],
        count: schedulingMatch[3]
      });
    }
    const eventMatch = step.detail.match(/^(\d+) event\(s\) are available for reminders\.$/);
    if (eventMatch) {
      return t(locale, 'feature.more.setupWizard.detail.eventsReady', { count: eventMatch[1] });
    }
    const reminderMatch = step.detail.match(/^(\d+) reminder plan\(s\) are ready\.$/);
    if (reminderMatch) {
      return t(locale, 'feature.more.setupWizard.detail.reminderPlansReady', { count: reminderMatch[1] });
    }
    const providerMatch = step.detail.match(/^Provider endpoint is configured\. Current status: (.+)\.$/);
    if (providerMatch) {
      return t(locale, 'feature.more.setupWizard.detail.aiProviderReady', {
        status: aiProviderStatusLabel(providerMatch[1] as typeof state.aiProvider.status)
      });
    }
    const automationModeMatch = step.detail.match(/^(.+) is selected\. Review queued messages before trusting automation\.$/);
    if (automationModeMatch) {
      return t(locale, 'feature.more.setupWizard.detail.automationModeSelected', {
        mode: activityAutomationModeFromText(locale, automationModeMatch[1])
      });
    }
    return step.detail;
  };
  const setupCheckGroupLabel = (group: string) => {
    switch (group) {
      case 'Required':
        return t(locale, 'feature.more.setupCheck.group.required');
      case 'Quality':
        return t(locale, 'feature.more.setupCheck.group.quality');
      case 'Reliability':
        return t(locale, 'feature.more.setupCheck.group.reliability');
      case 'Recovery':
        return t(locale, 'feature.more.setupCheck.group.recovery');
      default:
        return group;
    }
  };
  const aiProviderStatusLabel = (status: typeof state.aiProvider.status) => {
    switch (status) {
      case 'Not configured':
        return t(locale, 'feature.more.aiProvider.notConfigured');
      case 'Ready':
        return t(locale, 'feature.more.aiProvider.ready');
      case 'Error':
        return t(locale, 'feature.more.aiProvider.error');
    }
  };
  const providerEndpointStatusLabel = (readiness: ProviderEndpointReadiness) => {
    switch (readiness.status) {
      case 'Missing':
        return t(locale, 'feature.more.providerEndpoint.status.missing');
      case 'Ready':
        return t(locale, 'feature.more.providerEndpoint.status.ready');
      case 'Development only':
        return t(locale, 'feature.more.providerEndpoint.status.developmentOnly');
      case 'Blocked':
        return t(locale, 'feature.more.providerEndpoint.status.blocked');
    }
  };
  const providerEndpointDetailLabel = (readiness: ProviderEndpointReadiness) => {
    switch (readiness.status) {
      case 'Missing':
        return t(locale, 'feature.more.providerEndpoint.detail.missing');
      case 'Ready':
        return t(locale, 'feature.more.providerEndpoint.detail.ready');
      case 'Development only':
        return t(locale, 'feature.more.providerEndpoint.detail.developmentOnly');
      case 'Blocked':
        return t(locale, 'feature.more.providerEndpoint.detail.blocked');
    }
  };
  const setupDoctorCheckTitleLabel = (check: SetupDoctorCheck) => {
    const keyById: Record<string, TranslationKey> = {
      'ai-provider': state.settings.aiEnabled
        ? 'feature.more.setupCheck.check.aiProvider.title'
        : 'feature.more.setupCheck.check.aiDisabled.title',
      'email-provider':
        check.title === 'Email provider endpoint'
          ? 'feature.more.setupCheck.check.emailProvider.title'
          : check.title === 'Email provider optional'
            ? 'feature.more.setupCheck.check.emailOptional.title'
            : 'feature.more.setupCheck.check.emailDisabled.title',
      personalization: 'feature.more.setupCheck.check.personalization.title',
      'style-profile': 'feature.more.setupCheck.check.styleProfile.title',
      'pending-review': 'feature.more.setupCheck.check.pendingReview.title',
      'privacy-controls': 'feature.more.setupCheck.check.privacyControls.title',
      reminders: 'feature.more.setupCheck.check.reminders.title',
      'backup-freshness': 'feature.more.setupCheck.check.backupFreshness.title',
      'local-storage': 'feature.more.setupCheck.check.localStorage.title',
      'failed-messages': 'feature.more.setupCheck.check.failedMessages.title',
      'recent-warnings': 'feature.more.setupCheck.check.recentWarnings.title',
      'release-evidence': 'feature.more.setupCheck.check.releaseEvidence.title'
    };
    const key = keyById[check.id];
    return key ? t(locale, key) : check.title;
  };
  const setupDoctorActionLabel = (check: SetupDoctorCheck) => {
    const keyByAction: Record<string, TranslationKey> = {
      'Test AI provider': 'feature.more.setupCheck.action.testAiProvider',
      'Open setup': 'feature.more.setupCheck.action.openSetup',
      'Review email settings': 'feature.more.setupCheck.action.reviewEmailSettings',
      'Review contact': 'feature.more.setupCheck.action.reviewContact',
      'Open contacts': 'feature.more.setupCheck.action.openContacts',
      'Open Style Coach': 'feature.more.setupCheck.action.openStyleCoach',
      'Review messages': 'feature.more.setupCheck.action.reviewMessages',
      'Open privacy settings': 'feature.more.setupCheck.action.openPrivacySettings',
      'Review reminders': 'feature.more.setupCheck.action.reviewReminders',
      'Plan reminders': 'feature.more.setupCheck.action.planReminders',
      'Open backup': 'feature.more.setupCheck.action.openBackup',
      'Open persistence': 'feature.more.setupCheck.action.openPersistence',
      'Open messages': 'feature.more.setupCheck.action.openMessages',
      'View activity': 'feature.more.setupCheck.action.viewActivity',
      'Review release evidence': 'feature.more.setupCheck.action.reviewReleaseEvidence'
    };
    return t(locale, keyByAction[check.actionLabel] ?? 'feature.more.setupCheck.action.openSetup');
  };
  const setupDoctorSummaryLabel = () => {
    if (setupDoctorReport.recommendedCheck) {
      return t(locale, 'feature.more.setupCheck.summaryNextFix', {
        ready: setupDoctorReport.readyCount,
        total: setupDoctorReport.totalCount,
        title: setupDoctorCheckTitleLabel(setupDoctorReport.recommendedCheck)
      });
    }
    return t(locale, 'feature.more.setupCheck.summaryNoBlockers', {
      ready: setupDoctorReport.readyCount,
      total: setupDoctorReport.totalCount
    });
  };
  const setupDoctorDryRunMessage = () => t(locale, 'feature.more.setupCheck.dryRunMessage');
  const privacySummaryLabel = (summary: string) => {
    if (summary === 'Privacy-sensitive capabilities have clear user-controlled states.') {
      return t(locale, 'feature.more.setupCheck.impact.privacyReady');
    }
    const reviewMatch = summary.match(
      /^(\d+) privacy-sensitive capability\/capabilities need review or have a fallback active\.$/
    );
    if (reviewMatch) {
      return t(locale, 'feature.more.setupCheck.impact.privacyNeedsReview', { count: reviewMatch[1] });
    }
    const recommendationMatch = summary.match(/^(\d+) privacy recommendation\(s\) available\.$/);
    if (recommendationMatch) {
      return t(locale, 'feature.more.setupCheck.impact.privacyRecommendation', { count: recommendationMatch[1] });
    }
    return summary;
  };
  const setupDoctorImpactLabel = (check: SetupDoctorCheck) => {
    const exactImpactKeyByImpact: Record<string, TranslationKey> = {
      'Provider drafts can be tested before use.': 'feature.more.setupCheck.impact.aiProviderReady',
      'AI drafts will fall back to local templates until a secure endpoint is configured.':
        'feature.more.setupCheck.impact.aiProviderMissing',
      'Provider endpoint is local-development only; configure HTTPS before release.':
        'feature.more.setupCheck.impact.aiProviderDevelopmentOnly',
      'Configured provider endpoint is not safe to use. Use HTTPS without credentials, localhost, or private-network hosts.':
        'feature.more.setupCheck.impact.aiProviderUnsafe',
      'Local templates remain available while AI is disabled.': 'feature.more.setupCheck.impact.aiDisabled',
      'Email provider delivery uses a release-ready HTTPS endpoint.':
        'feature.more.setupCheck.impact.emailProviderReady',
      'Email provider delivery is disabled; manual handoff remains available.':
        'feature.more.setupCheck.impact.emailDisabled',
      'Email provider delivery is optional; manual email handoff remains available.':
        'feature.more.setupCheck.impact.emailProviderOptional',
      'Email provider endpoint is local-development only; configure HTTPS before release.':
        'feature.more.setupCheck.impact.emailProviderDevelopmentOnly',
      'Configured email endpoint is not safe to use. Use HTTPS without credentials, localhost, or private-network hosts.':
        'feature.more.setupCheck.impact.emailProviderUnsafe',
      'Contacts have enough context for useful drafts.': 'feature.more.setupCheck.impact.personalizationReady',
      'Train Style Coach with writing samples before relying on tone matching.':
        'feature.more.setupCheck.impact.styleNeedsTraining',
      'No messages are waiting for approval.': 'feature.more.setupCheck.impact.noPendingReview',
      'Privacy-sensitive capabilities have clear user-controlled states.':
        'feature.more.setupCheck.impact.privacyReady',
      'Create an encrypted backup before relying on this as your only relationship record.':
        'feature.more.setupCheck.impact.backupNeeded',
      'Local data storage has not been verified on this device yet.':
        'feature.more.setupCheck.impact.localStorageUnverified',
      'Local data storage needs recovery before release.': 'feature.more.setupCheck.impact.localStorageRecovery',
      'Local data storage integrity failed and needs recovery.':
        'feature.more.setupCheck.impact.localStorageIntegrityFailed',
      'Local data is readable but should be rewritten into normalized storage before release verification.':
        'feature.more.setupCheck.impact.localStorageRewrite',
      'No failed message recovery is needed.': 'feature.more.setupCheck.impact.noFailedMessages',
      'No recent warnings require attention.': 'feature.more.setupCheck.impact.noRecentWarnings',
      'React Native release evidence has not been attached to this Setup Check run.':
        'feature.more.setupCheck.impact.releaseEvidenceMissing',
      'React Native release evidence has no blockers or warnings.':
        'feature.more.setupCheck.impact.releaseEvidenceReady'
    };
    const exactKey = exactImpactKeyByImpact[check.impact];
    if (exactKey) {
      return t(locale, exactKey);
    }
    const weakContactMatch = check.impact.match(/^(\d+) contact\(s\) need more relationship context\.$/);
    if (weakContactMatch) {
      return t(locale, 'feature.more.setupCheck.impact.personalizationNeedsContext', { count: weakContactMatch[1] });
    }
    const styleConfidenceMatch = check.impact.match(/^(.+) confidence profile is available\.$/);
    if (styleConfidenceMatch) {
      return t(locale, 'feature.more.setupCheck.impact.styleReady', { confidence: styleConfidenceMatch[1] });
    }
    const pendingMessageMatch = check.impact.match(/^(\d+) message\(s\) need review before scheduling or sending\.$/);
    if (pendingMessageMatch) {
      return t(locale, 'feature.more.setupCheck.impact.pendingReview', { count: pendingMessageMatch[1] });
    }
    const privacyReviewMatch = check.impact.match(
      /^(\d+) privacy-sensitive capability\/capabilities need review or have a fallback active\.$/
    );
    if (privacyReviewMatch) {
      return t(locale, 'feature.more.setupCheck.impact.privacyNeedsReview', { count: privacyReviewMatch[1] });
    }
    const privacyRecommendationMatch = check.impact.match(/^(\d+) privacy recommendation\(s\) available\.$/);
    if (privacyRecommendationMatch) {
      return t(locale, 'feature.more.setupCheck.impact.privacyRecommendation', {
        count: privacyRecommendationMatch[1]
      });
    }
    const backupFreshMatch = check.impact.match(/^Last encrypted backup is (\d+) day\(s\) old\.$/);
    if (backupFreshMatch) {
      return t(locale, 'feature.more.setupCheck.impact.backupFresh', { days: backupFreshMatch[1] });
    }
    const storageVerifiedMatch = check.impact.match(
      /^(\d+) normalized storage item\(s\) verified across (\d+) chunk\(s\)\.$/
    );
    if (storageVerifiedMatch) {
      return t(locale, 'feature.more.setupCheck.impact.localStorageReady', {
        entries: storageVerifiedMatch[1],
        chunks: storageVerifiedMatch[2]
      });
    }
    const storageRecoveryMatch = check.impact.match(/^Local data storage needs recovery: (.+)$/);
    if (storageRecoveryMatch) {
      return t(locale, 'feature.more.setupCheck.impact.localStorageRecoveryDetail', {
        reason: storageRecoveryMatch[1]
      });
    }
    const storageIntegrityMatch = check.impact.match(/^Local data storage integrity failed: (.+)$/);
    if (storageIntegrityMatch) {
      return t(locale, 'feature.more.setupCheck.impact.localStorageIntegrityDetail', {
        reason: storageIntegrityMatch[1]
      });
    }
    const failedMessageMatch = check.impact.match(/^(\d+) message\(s\) need recovery before they can be sent\.$/);
    if (failedMessageMatch) {
      return t(locale, 'feature.more.setupCheck.impact.failedMessages', { count: failedMessageMatch[1] });
    }
    const warningMatch = check.impact.match(/^(\d+) recent warning\(s\) are available in Activity History\.$/);
    if (warningMatch) {
      return t(locale, 'feature.more.setupCheck.impact.recentWarnings', { count: warningMatch[1] });
    }
    const releaseBlockerMatch = check.impact.match(
      /^([0-9]+) React Native release blocker[(]s[)] must be resolved before release/
    );
    if (releaseBlockerMatch) {
      return t(locale, 'feature.more.setupCheck.impact.releaseEvidenceBlockers', { count: releaseBlockerMatch[1] });
    }
    const releaseLegacyWarningMatch = check.impact.match(
      /^([0-9]+) React Native release evidence warning[(]s[)] remain, including ([0-9]+) legacy Android artifact path[(]s[)]/
    );
    if (releaseLegacyWarningMatch) {
      return t(locale, 'feature.more.setupCheck.impact.releaseEvidenceLegacyWarnings', {
        count: releaseLegacyWarningMatch[1],
        legacyCount: releaseLegacyWarningMatch[2]
      });
    }
    const releaseWarningMatch = check.impact.match(
      /^([0-9]+) React Native release evidence warning[(]s[)] remain for signed builds, device smoke, or store evidence/
    );
    if (releaseWarningMatch) {
      return t(locale, 'feature.more.setupCheck.impact.releaseEvidenceWarnings', { count: releaseWarningMatch[1] });
    }
    return check.impact;
  };
  const analyticsRangeLabel = (range: AnalyticsRange) => {
    switch (range) {
      case 'Last 30 days':
        return t(locale, 'feature.more.analytics.range.last30Days');
      case 'This year':
        return t(locale, 'feature.more.analytics.range.thisYear');
      case 'All time':
        return t(locale, 'feature.more.analytics.range.allTime');
    }
  };
  const visibleAutomationModesFor = (selectedMode: AutomationMode) =>
    showAdvancedAutomationModes || selectedMode === 'Fully auto' ? automationModes : defaultAutomationModes;
  const activityTypeLabel = (filter: ActivityTypeFilter) => {
    switch (filter) {
      case 'All':
        return t(locale, 'feature.more.activityHistory.type.all');
      case 'Message':
        return t(locale, 'feature.more.activityHistory.type.message');
      case 'Event':
        return t(locale, 'feature.more.activityHistory.type.event');
      case 'Contact':
        return t(locale, 'feature.more.activityHistory.type.contact');
      case 'Backup':
        return t(locale, 'feature.more.activityHistory.type.backup');
      case 'Setup':
        return t(locale, 'feature.more.activityHistory.type.setup');
      case 'AI':
        return t(locale, 'feature.more.activityHistory.type.ai');
      case 'Gift':
        return t(locale, 'feature.more.activityHistory.type.gift');
      case 'Memory':
        return t(locale, 'feature.more.activityHistory.type.memory');
      case 'Analytics':
        return t(locale, 'feature.more.activityHistory.type.analytics');
    }
  };
  const activitySeverityLabel = (filter: ActivitySeverityFilter) => {
    switch (filter) {
      case 'All':
        return t(locale, 'feature.more.activityHistory.severity.all');
      case 'Info':
        return t(locale, 'feature.more.activityHistory.severity.info');
      case 'Warning':
        return t(locale, 'feature.more.activityHistory.severity.warning');
      case 'Error':
        return t(locale, 'feature.more.activityHistory.severity.error');
    }
  };
  const activityDateLabel = (filter: ActivityDateFilter) => {
    switch (filter) {
      case 'All':
        return t(locale, 'feature.more.activityHistory.date.all');
      case 'Today':
        return t(locale, 'feature.more.activityHistory.date.today');
      case 'Last 7 days':
        return t(locale, 'feature.more.activityHistory.date.last7Days');
    }
  };
  const activityActionLabel = (row: ActivityHistoryRow) => {
    if (row.item.actionLabel) {
      return row.actionLabel;
    }
    switch (row.item.type) {
      case 'Message':
        return t(locale, 'feature.more.activityHistory.openMessages');
      case 'Event':
        return t(locale, 'feature.more.activityHistory.openEvents');
      case 'Contact':
      case 'Gift':
      case 'Memory':
        return t(locale, 'feature.more.activityHistory.openContacts');
      case 'Backup':
      case 'Setup':
      case 'AI':
      case 'Analytics':
        return t(locale, 'feature.more.activityHistory.openSetup');
    }
  };
  const activityRecoveryDetail = (row: ActivityHistoryRow) => {
    if (row.item.messageId) {
      return t(locale, 'feature.more.activityHistory.recoveryMissingMessage');
    }
    if (row.item.contactId) {
      return t(locale, 'feature.more.activityHistory.recoveryMissingContact');
    }
    return t(locale, 'feature.more.activityHistory.recoveryFallback');
  };
  const emailProviderStatusLabel = (status: typeof state.emailDelivery.status) => {
    switch (status) {
      case 'Not configured':
        return t(locale, 'feature.more.settings.status.notConfigured');
      case 'Ready':
        return t(locale, 'feature.more.settings.status.ready');
      case 'Error':
        return t(locale, 'feature.more.settings.status.error');
    }
  };
  const settingToggleLabel = (setting: TranslationKey, enabled: boolean) =>
    t(locale, 'feature.more.settings.toggleStatus', {
      setting: t(locale, setting),
      status: statusLabel(enabled)
    });
  const relationshipGroupLabel = (group: RelationshipGroup) => {
    return relationshipGroupDisplayLabel(locale, group);
  };
  const channelLabel = (channel: MessageChannel) => {
    switch (channel) {
      case 'SMS':
        return t(locale, 'feature.more.settings.channel.sms');
      case 'WhatsApp':
        return t(locale, 'feature.more.settings.channel.whatsApp');
      case 'Email':
        return t(locale, 'feature.more.settings.channel.email');
      case 'Manual':
        return t(locale, 'feature.more.settings.channel.manual');
    }
  };
  const automationModeLabel = (mode: AutomationMode) => {
    return automationModeDisplayLabel(locale, mode);
  };
  const cadenceLabel = (days: number) => checkInCadenceLabel(locale, days);
  const emailProviderStatus = emailEndpointReadiness.productionReady
    ? emailProviderStatusLabel(state.emailDelivery.status)
    : providerEndpointStatusLabel(emailEndpointReadiness);
  const persistenceStatus = persistenceStatusLabel(state.persistence.status);
  const persistenceStatusText = state.persistence.lastSavedAt
    ? t(locale, 'feature.more.persistence.statusSaved', {
        status: persistenceStatus,
        date: formatDateForLocale(state.persistence.lastSavedAt, state.settings.locale)
      })
    : t(locale, 'feature.more.persistence.status', { status: persistenceStatus });
  const aiProviderStatus = aiEndpointReadiness.productionReady
    ? aiProviderStatusLabel(state.aiProvider.status)
    : providerEndpointStatusLabel(aiEndpointReadiness);
  const setupPlan = buildSetupWizardPlan(
    state,
    {
      aiEndpointReadiness,
      emailEndpointReadiness
    },
    setupGoal
  );
  const setupDoctorReport = buildSetupDoctorReport(state, {
    aiEndpointReadiness,
    emailEndpointReadiness
  });
  const setupDoctorChecks = setupDoctorReport.checksByGroup.flatMap(group => group.checks);
  const setupDoctorNeedsActionCount = setupDoctorChecks.filter(check => check.status === 'Needs action').length;
  const setupDoctorWarningCount = setupDoctorChecks.filter(check => check.status === 'Warning').length;
  const analyticsDashboard = buildAnalyticsDashboard(state, analyticsRange);
  const schedulingPolicy = buildSchedulingPolicySummary(state);
  const privacyReport = buildPrivacyCenterReport(state);
  const disconnectAccountPlan = buildAccountExitPlan(state, 'disconnect-account');
  const clearLocalDataPlan = buildAccountExitPlan(state, 'clear-local-data');
  const visibleAccountPlan = disconnectAccountPlan.available ? disconnectAccountPlan : clearLocalDataPlan;
  const activityHistory = buildActivityHistory(state.activity, {
    query: activityQuery,
    type: activityType,
    severity: activitySeverity,
    date: activityDate,
    state
  });
  const eligibleStyleMessageCount = eligibleSentStyleMessages(state).length;
  useEffect(() => {
    setQuietStart(state.settings.quietHours.start);
    setQuietEnd(state.settings.quietHours.end);
  }, [state.settings.quietHours.end, state.settings.quietHours.start]);
  useEffect(() => {
    const nextLibrary = buildMessageTemplateLibrary(state, {
      contactId: templateLibraryContactId,
      reason: templateLibraryReason,
      tone: templateLibraryTone,
      selectedTemplateId: templateLibraryTemplateId
    });
    if (nextLibrary.ok && nextLibrary.selectedTemplate.id !== templateLibraryTemplateId) {
      setTemplateLibraryTemplateId(nextLibrary.selectedTemplate.id);
    }
    setTemplateLibraryBody(nextLibrary.renderedBody);
  }, [
    state.contacts,
    state.memories,
    state.settings,
    templateLibraryContactId,
    templateLibraryReason,
    templateLibraryTemplateId,
    templateLibraryTone
  ]);
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
  const refreshSetupDoctorReport = () => {
    showAppAlert(
      t(locale, 'feature.more.setupCheck.refreshTitle'),
      t(locale, 'feature.more.setupCheck.refreshMessage', {
        ready: setupDoctorReport.readyCount,
        total: setupDoctorReport.totalCount,
        blockers: setupDoctorNeedsActionCount,
        warnings: setupDoctorWarningCount
      })
    );
  };
  const runSetupDoctorDryRun = () => {
    const snapshot = buildSetupDoctorDryRunSnapshot(setupDoctorReport);
    dispatch({ type: 'setupDoctorDryRunRecorded', detail: snapshot.activityDetail });
    showAppAlert(t(locale, 'feature.more.setupCheck.dryRunTitle'), snapshot.activityDetail);
  };
  const openAnalyticsInsight = (insight: AnalyticsInsight) => {
    dispatch({ type: 'navigate', screen: insight.targetScreen, contactId: insight.contactId });
  };
  const exportAnalyticsCsvReport = async (report: string) => {
    try {
      const result = await Share.share({
        title: t(locale, 'feature.more.analytics.reportShareTitle'),
        message: report
      });
      if (result.action !== Share.dismissedAction) {
        dispatch({ type: 'analyticsExported', rowCount: Math.max(0, report.split('\n').length - 1) });
        showAppAlert(
          t(locale, 'feature.more.analytics.reportExportedTitle'),
          t(locale, 'feature.more.analytics.reportExportedMessage')
        );
      }
    } catch {
      showAppAlert(
        t(locale, 'feature.more.analytics.reportFailedTitle'),
        t(locale, 'feature.more.analytics.shareUnavailable')
      );
    }
  };
  const shareAnalyticsReport = () => {
    const report = buildAnalyticsCsvReport(state, analyticsDashboard);
    showAppAlert(
      t(locale, 'feature.more.analytics.csvConfirmTitle'),
      t(locale, 'feature.more.analytics.csvConfirmBody'),
      [
        { text: t(locale, 'action.cancel'), style: 'cancel' },
        {
          text: t(locale, 'feature.more.analytics.csvConfirmAction'),
          onPress: () => void exportAnalyticsCsvReport(report)
        }
      ]
    );
  };
  const shareAnalyticsSummary = async () => {
    const summary = buildShareableAnalyticsSummary(analyticsDashboard);
    try {
      const result = await Share.share({
        title: t(locale, 'feature.more.analytics.summaryShareTitle', {
          range: analyticsRangeLabel(analyticsDashboard.range)
        }),
        message: summary.body
      });
      if (result.action !== Share.dismissedAction) {
        dispatch({ type: 'analyticsExported', rowCount: summary.lineCount, format: 'Summary' });
        showAppAlert(
          t(locale, 'feature.more.analytics.summarySharedTitle'),
          t(locale, 'feature.more.analytics.summarySharedMessage')
        );
      }
    } catch {
      showAppAlert(
        t(locale, 'feature.more.analytics.summaryFailedTitle'),
        t(locale, 'feature.more.analytics.shareUnavailable')
      );
    }
  };
  const importEventText = (raw: string, label: string) => {
    const parsed = parseEventImportText(raw, eventImportFormat);
    setEventImportErrors(parsed.errors);
    if (parsed.candidates.length === 0) {
      setEventImportSummary(t(locale, 'feature.more.calendar.importSummaryEmpty', { source: label, skipped: parsed.skipped }));
      return;
    }

    dispatch({ type: 'calendarImported', candidates: parsed.candidates });
    setEventImportSummary(
      t(locale, 'feature.more.calendar.importSummaryImported', {
        count: parsed.candidates.length,
        source: label,
        skipped: parsed.skipped
      })
    );
    setEventImportText('');
  };
  const importPickedEventFile = async () => {
    try {
      const picked = await pickEventImportFile();
      if (!picked) {
        return;
      }
      setEventImportText(picked.raw);
      importEventText(picked.raw, picked.name);
    } catch (error) {
      const message = error instanceof Error ? error.message : t(locale, 'feedback.eventFileImportFailedFallback');
      setEventImportSummary(t(locale, 'feedback.eventFileImportFailedTitle'));
      setEventImportErrors([message]);
      showAppAlert(t(locale, 'feedback.eventFileImportFailedTitle'), message);
    }
  };
  const confirmAccountAction = (plan: AccountExitPlan, onConfirm: () => void) => {
    showAppAlert(plan.confirmationTitle, plan.confirmationBody, [
      { text: t(locale, 'action.cancel'), style: 'cancel' },
      {
        text: plan.primaryActionLabel,
        style: plan.destructive ? 'destructive' : 'default',
        onPress: onConfirm
      }
    ]);
  };
  const setGlobalAutomationMode = (mode: AutomationMode) => {
    if (mode === 'Fully auto' && state.settings.automationMode !== 'Fully auto') {
      showAppAlert(
        t(locale, 'feature.more.settings.fullAutoConfirmTitle'),
        t(locale, 'feature.more.settings.fullAutoConfirmBody'),
        [
          { text: t(locale, 'action.cancel'), style: 'cancel' },
          {
            text: t(locale, 'feature.more.settings.fullAutoConfirmAction'),
            onPress: () => dispatch({ type: 'setAutomationMode', mode })
          }
        ]
      );
      return;
    }

    dispatch({ type: 'setAutomationMode', mode });
  };
  return (
    <View>
      <SectionTitle
        title={t(locale, 'feature.more.title')}
        detail={t(locale, 'feature.more.detail')}
      />

      <Card>
      <Text style={styles.cardTitle}>{t(locale, 'feature.more.account.title')}</Text>
          <Text style={styles.bodyText}>
	        {t(locale, 'feature.more.account.modeSummary', {
	          mode: state.settings.accountMode,
	          summary: privacySummaryLabel(privacyReport.summary)
	        })}
	      </Text>
      <View style={styles.wrapRow}>
        <Pill
          label={t(locale, 'feature.more.account.local')}
          selected={state.settings.accountMode === 'Local'}
          onPress={() => dispatch({ type: 'setAccountMode', mode: 'Local' })}
        />
        <Pill
          label={t(locale, 'feature.more.account.googleSync')}
          selected={state.settings.accountMode === 'Google sync'}
          onPress={() => dispatch({ type: 'setAccountMode', mode: 'Google sync' })}
        />
      </View>
      {state.settings.accountMode === 'Google sync' ? (
        <Text style={styles.warningText}>{t(locale, 'feature.more.account.providerDisconnected')}</Text>
      ) : (
        <Text style={styles.smallText}>{t(locale, 'feature.more.account.localModeDetail')}</Text>
      )}
      <Text style={styles.smallText}>{t(locale, 'feature.more.account.checklistTitle', { title: visibleAccountPlan.title })}</Text>
      {visibleAccountPlan.checklist.map(item => (
        <View key={item.id} style={styles.inlineItem}>
          <View style={styles.rowBetween}>
            <Text style={styles.bodyText}>{item.label}</Text>
            <Pill
              label={
                item.satisfied
                  ? t(locale, 'feature.more.account.checklistReady')
                  : t(locale, 'feature.more.account.checklistReview')
              }
              selected={item.satisfied}
            />
          </View>
          <Text style={item.severity === 'Danger' ? styles.warningText : styles.smallText}>{item.detail}</Text>
        </View>
      ))}
      <View style={styles.actionRow}>
        <Button
          label={t(locale, 'feature.more.account.openOnboarding')}
          tone="secondary"
          onPress={() => dispatch({ type: 'reopenOnboarding' })}
        />
        {state.settings.accountMode !== 'Local' ? (
          <Button
            label={t(locale, 'feature.more.account.disconnectAccount')}
            tone="secondary"
            onPress={() => confirmAccountAction(disconnectAccountPlan, () => dispatch({ type: 'disconnectAccount' }))}
          />
        ) : null}
        <Button
          label={t(locale, 'feature.more.account.clearLocalData')}
          tone="danger"
          onPress={() => confirmAccountAction(clearLocalDataPlan, clearLocalData)}
        />
      </View>
      <Text style={styles.smallText}>{t(locale, 'feature.more.account.privacyPermissions')}</Text>
      {privacyReport.rows.map(row => (
        <View key={row.capability} style={styles.inlineItem}>
          <View style={styles.rowBetween}>
            <Text style={styles.bodyText}>{row.capability}</Text>
            <Pill label={row.status} selected={row.status === 'Enabled'} />
          </View>
          <Text style={styles.smallText}>{row.purpose}</Text>
          {row.status !== 'Enabled' ? <Text style={styles.smallText}>{row.fallback}</Text> : null}
          <View style={styles.wrapRow}>
            <Button
              label={t(locale, 'feature.more.account.markGranted')}
              tone="secondary"
              onPress={() => dispatch({ type: 'recordPermissionDecision', capability: row.capability, decision: 'Granted' })}
            />
            <Button
              label={t(locale, 'feature.more.account.markDenied')}
              tone="secondary"
              onPress={() => dispatch({ type: 'recordPermissionDecision', capability: row.capability, decision: 'Denied' })}
            />
            {row.targetScreen ? (
              <Button
                label={row.actionLabel}
                tone="ghost"
                onPress={() => dispatch({ type: 'navigate', screen: row.targetScreen! })}
              />
            ) : null}
          </View>
        </View>
      ))}
      <View style={styles.inlineItem}>
        <View style={styles.rowBetween}>
          <Text style={styles.bodyText}>{t(locale, 'feature.more.account.whatsappConsentTitle')}</Text>
          <Pill
            label={
              state.privacy.whatsappHandoffConsent
                ? t(locale, 'feature.more.account.whatsappConsentGranted')
                : t(locale, 'feature.more.account.whatsappConsentOff')
            }
            selected={state.privacy.whatsappHandoffConsent}
          />
        </View>
        <Text style={styles.smallText}>{t(locale, 'feature.more.account.whatsappConsentDetail')}</Text>
        <Button
          label={
            state.privacy.whatsappHandoffConsent
              ? t(locale, 'feature.more.account.revokeConsent')
              : t(locale, 'feature.more.account.grantConsent')
          }
          tone="secondary"
          onPress={() => dispatch({ type: 'toggleWhatsAppHandoffConsent' })}
        />
      </View>
    </Card>

    <Card>
      <Text style={styles.cardTitle}>{t(locale, 'feature.more.calendar.title')}</Text>
      <Text style={styles.bodyText}>{t(locale, 'feature.more.calendar.detail')}</Text>
      <Text style={styles.smallText}>
        {t(locale, 'feature.more.calendar.counts', {
          exported: state.calendarSync.exportedCount,
          imported: state.calendarSync.importedCount
        })}
      </Text>
      {state.calendarSync.lastError ? <Text style={styles.warningText}>{state.calendarSync.lastError}</Text> : null}
      <View style={styles.actionRow}>
        <Button label={t(locale, 'feature.more.calendar.exportEvents')} onPress={exportCalendar} />
        <Button label={t(locale, 'feature.more.calendar.importCalendar')} tone="secondary" onPress={importCalendar} />
      </View>
      <Text style={styles.smallText}>{t(locale, 'feature.more.calendar.fileImport')}</Text>
      <View style={styles.wrapRow}>
        {(['auto', 'csv', 'vcard'] as EventImportFormat[]).map(format => (
          <Pill
            key={format}
            label={format === 'auto' ? t(locale, 'feature.more.calendar.autoDetect') : format.toUpperCase()}
            selected={eventImportFormat === format}
            onPress={() => setEventImportFormat(format)}
          />
        ))}
      </View>
      <TextInput
        accessibilityLabel={t(locale, 'feature.more.calendar.importTextLabel')}
        placeholder={t(locale, 'feature.more.calendar.importTextPlaceholder')}
        placeholderTextColor={colors.muted}
        value={eventImportText}
        onChangeText={setEventImportText}
        style={[styles.input, styles.multiline]}
        multiline
      />
      <View style={styles.actionRow}>
        <Button
          label={t(locale, 'feature.more.calendar.importPasted')}
          tone="secondary"
          disabled={eventImportText.trim().length === 0}
          onPress={() => importEventText(eventImportText, t(locale, 'feature.more.calendar.pastedTextSource'))}
        />
        <Button label={t(locale, 'feature.more.calendar.selectFile')} tone="ghost" onPress={() => void importPickedEventFile()} />
      </View>
      {eventImportSummary ? <Text style={styles.smallText}>{eventImportSummary}</Text> : null}
      {eventImportErrors.slice(0, 3).map(error => (
        <Text key={error} style={styles.warningText}>
          {error}
        </Text>
      ))}
    </Card>

    <Card>
      <Text style={styles.cardTitle}>{t(locale, 'feature.more.reminders.title')}</Text>
      <Text style={styles.bodyText}>{t(locale, 'feature.more.reminders.detail')}</Text>
      <Text style={styles.smallText}>{t(locale, 'feature.more.reminders.plannedCount', { count: state.reminderPlans.length })}</Text>
      <Text style={styles.smallText}>{t(locale, 'feature.more.reminders.automationMode')}</Text>
      <Text style={styles.smallText}>{t(locale, 'feature.more.settings.fullAutoAdvancedNotice')}</Text>
      <View style={styles.wrapRow}>
        {visibleAutomationModesFor(state.settings.automationMode).map(mode => (
          <Pill
            key={mode}
            label={automationModeLabel(mode)}
            selected={state.settings.automationMode === mode}
            onPress={() => setGlobalAutomationMode(mode)}
          />
        ))}
      </View>
      <Button
        label={t(
          locale,
          showAdvancedAutomationModes
            ? 'feature.more.settings.hideAdvancedAutomation'
            : 'feature.more.settings.showAdvancedAutomation'
        )}
        tone="ghost"
        onPress={() => setShowAdvancedAutomationModes(value => !value)}
      />
      {showAdvancedAutomationModes ? (
        <Text style={styles.smallText}>{t(locale, 'feature.more.settings.advancedAutomationDetail')}</Text>
      ) : null}
      <Text style={styles.smallText}>{t(locale, 'feature.more.reminders.quietHours')}</Text>
      <View style={styles.actionRow}>
        <TextInput
          accessibilityLabel={t(locale, 'feature.more.reminders.quietStartLabel')}
          placeholder={t(locale, 'feature.more.reminders.quietStartPlaceholder')}
          value={quietStart}
          onChangeText={setQuietStart}
          style={[styles.input, styles.shortInput]}
        />
        <TextInput
          accessibilityLabel={t(locale, 'feature.more.reminders.quietEndLabel')}
          placeholder={t(locale, 'feature.more.reminders.quietEndPlaceholder')}
          value={quietEnd}
          onChangeText={setQuietEnd}
          style={[styles.input, styles.shortInput]}
        />
        <Button
          label={t(locale, 'feature.more.reminders.saveQuietHours')}
          tone="secondary"
          onPress={() => dispatch({ type: 'setQuietHours', start: quietStart, end: quietEnd })}
        />
      </View>
      {schedulingPolicy.issues.map(issue => (
        <Text key={issue.id} style={issue.severity === 'Info' ? styles.smallText : styles.warningText}>
          {issue.title}: {issue.detail}
        </Text>
      ))}
      <Text style={styles.smallText}>{t(locale, 'feature.more.reminders.blackoutWindows')}</Text>
      {state.settings.blackouts.length === 0 ? (
        <Text style={styles.smallText}>{t(locale, 'feature.more.reminders.noBlackouts')}</Text>
      ) : (
        state.settings.blackouts.map(blackout => (
          <View key={blackout.id} style={styles.inlineItem}>
            <View style={styles.rowBetween}>
              <Text style={styles.bodyText}>{blackout.label}</Text>
              <Button
                label={t(locale, 'feature.more.reminders.removeBlackout')}
                tone="secondary"
                onPress={() => dispatch({ type: 'removeBlackout', blackoutId: blackout.id })}
              />
            </View>
            <Text style={styles.smallText}>
              {t(locale, 'feature.more.reminders.blackoutRange', {
                start: blackout.startDate,
                end: blackout.endDate
              })}
            </Text>
          </View>
        ))
      )}
      <TextInput
        accessibilityLabel={t(locale, 'feature.more.reminders.blackoutLabel')}
        placeholder={t(locale, 'feature.more.reminders.blackoutLabel')}
        value={blackoutLabel}
        onChangeText={setBlackoutLabel}
        style={styles.input}
      />
      <View style={styles.actionRow}>
        <TextInput
          accessibilityLabel={t(locale, 'feature.more.reminders.blackoutStartDate')}
          placeholder={t(locale, 'feature.more.reminders.datePlaceholder')}
          value={blackoutStart}
          onChangeText={setBlackoutStart}
          style={[styles.input, styles.dateInput]}
        />
        <TextInput
          accessibilityLabel={t(locale, 'feature.more.reminders.blackoutEndDate')}
          placeholder={t(locale, 'feature.more.reminders.datePlaceholder')}
          value={blackoutEnd}
          onChangeText={setBlackoutEnd}
          style={[styles.input, styles.dateInput]}
        />
      </View>
      <Button
        label={t(locale, 'feature.more.reminders.addBlackout')}
        tone="secondary"
        onPress={() => {
          dispatch({ type: 'addBlackout', label: blackoutLabel, startDate: blackoutStart, endDate: blackoutEnd });
          setBlackoutLabel('');
          setBlackoutStart('');
          setBlackoutEnd('');
        }}
      />
      <Button label={t(locale, 'feature.more.reminders.planSchedule')} onPress={scheduleReminders} />
    </Card>

    <Card>
      <Text style={styles.cardTitle}>{t(locale, 'feature.more.contactImport.title')}</Text>
      <Text style={styles.bodyText}>{t(locale, 'feature.more.contactImport.detail')}</Text>
      <View style={styles.actionRow}>
        <Button label={t(locale, 'feature.more.contactImport.importDevice')} onPress={importDevice} />
      </View>
    </Card>

    <Card>
      <Text style={styles.cardTitle}>{t(locale, 'feature.more.templateLibrary.title')}</Text>
      <Text style={styles.bodyText}>{t(locale, 'feature.more.templateLibrary.detail')}</Text>
      <Text style={styles.smallText}>{t(locale, 'feature.more.templateLibrary.contact')}</Text>
      <View style={styles.wrapRow}>
        {state.contacts.map(contact => (
          <Pill
            key={contact.id}
            label={contact.name}
            selected={templateLibraryContactId === contact.id}
            onPress={() => setTemplateLibraryContactId(contact.id)}
          />
        ))}
      </View>
      <Text style={styles.smallText}>{t(locale, 'feature.more.templateLibrary.occasion')}</Text>
      <View style={styles.wrapRow}>
        {manualComposerReasons.map(reason => (
          <Pill
            key={reason}
            label={composerReasonLabel(locale, reason)}
            selected={templateLibraryReason === reason}
            onPress={() => setTemplateLibraryReason(reason)}
          />
        ))}
      </View>
      <Text style={styles.smallText}>{t(locale, 'feature.more.templateLibrary.tone')}</Text>
      <View style={styles.wrapRow}>
        {tones.map(tone => (
          <Pill
            key={tone}
            label={localizedToneLabel(locale, tone)}
            selected={templateLibraryTone === tone}
            onPress={() => setTemplateLibraryTone(tone)}
          />
        ))}
      </View>
      <Text style={styles.smallText}>{templateLibrary.contextDetail}</Text>
      <View style={styles.wrapRow}>
        {templateLibrary.templates.map(template => (
          <Pill
            key={template.id}
            label={template.title}
            selected={templateLibrary.ok && templateLibrary.selectedTemplate.id === template.id}
            onPress={() => setTemplateLibraryTemplateId(template.id)}
          />
        ))}
      </View>
      <TextInput
        accessibilityLabel={t(locale, 'feature.more.templateLibrary.messageLabel')}
        placeholder={t(locale, 'feature.more.templateLibrary.messagePlaceholder')}
        placeholderTextColor={colors.muted}
        value={templateLibraryBody}
        onChangeText={setTemplateLibraryBody}
        style={[styles.input, styles.messageInput]}
        multiline
      />
      <Text style={templateLibrary.action.enabled ? styles.smallText : styles.warningText}>
        {templateLibrary.action.detail}
      </Text>
      <Button
        label={t(locale, 'feature.more.templateLibrary.createDraft')}
        disabled={!templateLibrary.ok || !templateLibrary.action.enabled}
        onPress={() => {
          if (!templateLibrary.ok) {
            return;
          }
          dispatch({
            type: 'createTemplateDraft',
            contactId: templateLibrary.contact.id,
            reason: templateLibrary.reason,
            body: templateLibraryBody,
            templateId: templateLibrary.selectedTemplate.id
          });
        }}
      />
    </Card>

    <Card>
      <Text style={styles.cardTitle}>{t(locale, 'feature.more.persistence.title')}</Text>
      <Text style={styles.bodyText}>{persistenceStatusText}</Text>
      {state.persistence.storageHealth ? (
        <Text style={styles.smallText}>
          {t(locale, 'feature.more.persistence.storageHealth', {
            format: state.persistence.storageHealth.storageFormat,
            entries: state.persistence.storageHealth.entryCount,
            chunks: state.persistence.storageHealth.chunkCount,
            bytes: state.persistence.storageHealth.payloadBytes
          })}
        </Text>
      ) : (
        <Text style={styles.smallText}>{t(locale, 'feature.more.persistence.storageUnverified')}</Text>
      )}
      {state.persistence.error ? <Text style={styles.warningText}>{state.persistence.error}</Text> : null}
      {state.persistence.storageHealth?.issue ? (
        <Text style={styles.warningText}>{state.persistence.storageHealth.issue}</Text>
      ) : null}
    </Card>

    <Card>
      <Text style={styles.cardTitle}>{t(locale, 'feature.more.setupWizard.title')}</Text>
      <Text style={styles.bodyText}>{setupWizardSummaryLabel()}</Text>
      <View style={styles.wrapRow}>
        {setupGoals.map(goal => (
          <Pill key={goal} label={setupGoalLabel(goal)} selected={setupGoal === goal} onPress={() => setSetupGoal(goal)} />
        ))}
      </View>
      {setupPlan.recommendedStep ? (
        <View style={styles.inlineItem}>
          <Text style={styles.smallText}>{t(locale, 'feature.more.setupWizard.recommendedNextStep')}</Text>
          <Text style={styles.bodyText}>{setupWizardStepTitleLabel(setupPlan.recommendedStep)}</Text>
          <Text style={styles.smallText}>{setupWizardStepDetailLabel(setupPlan.recommendedStep)}</Text>
          <Button label={setupWizardStepActionLabel(setupPlan.recommendedStep)} onPress={() => runSetupAction(setupPlan.recommendedStep!)} />
        </View>
      ) : null}
      {setupPlan.steps.map(step => (
        <View key={step.id} style={styles.inlineItem}>
          <View style={styles.rowBetween}>
            <Text style={styles.bodyText}>{setupWizardStepTitleLabel(step)}</Text>
            <Pill label={setupStatusLabel(step.status)} selected={step.status === 'Ready'} />
          </View>
          <Text style={styles.smallText}>{setupWizardStepDetailLabel(step)}</Text>
          {step.status !== 'Ready' ? (
            <Button label={setupWizardStepActionLabel(step)} tone="secondary" onPress={() => runSetupAction(step)} />
          ) : null}
        </View>
      ))}
    </Card>

    <Card>
      <Text style={styles.cardTitle}>{t(locale, 'feature.more.setupCheck.title')}</Text>
      <Text style={styles.bodyText}>{setupDoctorSummaryLabel()}</Text>
      <Text style={styles.smallText}>{setupDoctorDryRunMessage()}</Text>
      {setupDoctorReport.recommendedCheck ? (
        <View style={styles.inlineItem}>
          <Text style={styles.smallText}>{t(locale, 'feature.more.setupCheck.recommendedFix')}</Text>
          <Text style={styles.bodyText}>{setupDoctorCheckTitleLabel(setupDoctorReport.recommendedCheck)}</Text>
          <Text style={styles.smallText}>{setupDoctorImpactLabel(setupDoctorReport.recommendedCheck)}</Text>
          <Button
            label={setupDoctorActionLabel(setupDoctorReport.recommendedCheck)}
            onPress={() => runSetupDoctorAction(setupDoctorReport.recommendedCheck!)}
          />
        </View>
      ) : null}
      <Button label={t(locale, 'feature.more.setupCheck.refresh')} tone="secondary" onPress={refreshSetupDoctorReport} />
      <Button label={t(locale, 'feature.more.setupCheck.runDryCheck')} tone="secondary" onPress={runSetupDoctorDryRun} />
      <Button
        label={t(
          locale,
          showSetupCheckDetails ? 'feature.more.setupCheck.hideDetails' : 'feature.more.setupCheck.showDetails'
        )}
        tone="secondary"
        onPress={() => setShowSetupCheckDetails(value => !value)}
      />
      {showSetupCheckDetails
        ? setupDoctorReport.checksByGroup.map(group => (
            <View key={group.group} style={styles.inlineItem}>
              <Text style={styles.cardTitle}>{setupCheckGroupLabel(group.group)}</Text>
              {group.checks.map(check => (
                <View key={check.id} style={styles.inlineItem}>
                  <View style={styles.rowBetween}>
                    <Text style={styles.bodyText}>{setupDoctorCheckTitleLabel(check)}</Text>
                    <Pill label={setupStatusLabel(check.status)} selected={check.status === 'Ready'} />
                  </View>
                  <Text style={styles.smallText}>{setupDoctorImpactLabel(check)}</Text>
                  {check.status !== 'Ready' ? (
                    <Button label={setupDoctorActionLabel(check)} tone="secondary" onPress={() => runSetupDoctorAction(check)} />
                  ) : null}
                </View>
              ))}
            </View>
          ))
        : null}
    </Card>

    <Card>
      <Text style={styles.cardTitle}>{t(locale, 'feature.more.styleCoach.title')}</Text>
      <Text style={styles.bodyText}>
        {t(locale, 'feature.more.styleCoach.summary', {
          confidence: state.styleProfile.confidence,
          formality: state.styleProfile.formality,
          averageLength: state.styleProfile.averageLength
        })}
      </Text>
      <Text style={styles.smallText}>
        {t(locale, 'feature.more.styleCoach.language', { language: state.styleProfile.language })}
      </Text>
      <Text style={styles.smallText}>
        {t(locale, 'feature.more.styleCoach.emojiUse', { emojiUse: state.styleProfile.emojiUse })}
      </Text>
      <Text style={styles.smallText}>
        {t(locale, 'feature.more.styleCoach.samplesLearned', { count: state.styleProfile.sampleCount })}
      </Text>
      {state.styleProfile.confidence !== 'Strong' ? (
        <Text style={styles.warningText}>{t(locale, 'feature.more.styleCoach.lowConfidence')}</Text>
      ) : null}
      <TextInput
        accessibilityLabel={t(locale, 'feature.more.styleCoach.samplesLabel')}
        placeholder={t(locale, 'feature.more.styleCoach.samplesPlaceholder')}
        value={styleSamples}
        onChangeText={setStyleSamples}
        style={[styles.input, styles.multiline]}
        multiline
      />
      <Text style={styles.smallText}>
        {t(locale, 'feature.more.styleCoach.eligibleSentMessages', { count: eligibleStyleMessageCount })}
      </Text>
      <View style={styles.actionRow}>
        <Button
          label={t(locale, 'feature.more.styleCoach.improveStyle')}
          onPress={() => dispatch({ type: 'trainStyleFromSamples', samples: styleSamples })}
        />
        <Button
          label={t(locale, 'feature.more.styleCoach.analyzeSentMessages')}
          tone="secondary"
          disabled={eligibleStyleMessageCount < 2}
          onPress={() => dispatch({ type: 'trainStyleFromSentMessages' })}
        />
      </View>
    </Card>

    <Card>
      <Text style={styles.cardTitle}>{t(locale, 'feature.more.aiProvider.title')}</Text>
      <Text style={styles.bodyText}>
        {t(locale, 'feature.more.aiProvider.statusDetail', { status: aiProviderStatus })}
      </Text>
      <Text style={aiEndpointReadiness.productionReady ? styles.smallText : styles.warningText}>
        {providerEndpointDetailLabel(aiEndpointReadiness)}
      </Text>
      {state.aiProvider.lastPrivacySummary ? (
        <Text style={styles.smallText}>{state.aiProvider.lastPrivacySummary}</Text>
      ) : null}
      {state.aiProvider.lastObservation ? (
        <Text style={styles.smallText}>
          {t(locale, 'feature.more.aiProvider.observation', {
            result: t(
              locale,
              state.aiProvider.lastObservation.ok
                ? 'feature.more.aiProvider.observationResultPassed'
                : 'feature.more.aiProvider.observationResultFailed'
            ),
            errorKind: state.aiProvider.lastObservation.errorKind
              ? ` (${state.aiProvider.lastObservation.errorKind})`
              : '',
            durationMs: state.aiProvider.lastObservation.durationMs,
            memoryCount: state.aiProvider.lastObservation.includedMemoryCount,
            priorCount: state.aiProvider.lastObservation.includedPriorMessageCount
          })}
        </Text>
      ) : null}
      {state.aiProvider.lastError ? <Text style={styles.warningText}>{state.aiProvider.lastError}</Text> : null}
      <Button label={t(locale, 'feature.more.aiProvider.test')} tone="secondary" onPress={testAiProvider} />
    </Card>

    <Card>
      <Text style={styles.cardTitle}>{t(locale, 'feature.more.analytics.title')}</Text>
      <Text style={styles.bodyText}>{t(locale, 'feature.more.analytics.detail')}</Text>
      <View style={styles.wrapRow}>
        {analyticsRanges.map(range => (
          <Pill
            key={range}
            label={analyticsRangeLabel(range)}
            selected={analyticsRange === range}
            onPress={() => setAnalyticsRange(range)}
          />
        ))}
      </View>
      {analyticsDashboard.emptyState ? <Text style={styles.warningText}>{analyticsDashboard.emptyState}</Text> : null}
      <View style={styles.statGrid}>
        {analyticsDashboard.metrics.map(metric => (
          <View key={metric.label} style={styles.metric}>
            <Text style={styles.metricValue}>{metric.value}</Text>
            <Text style={styles.metricLabel}>{metric.label}</Text>
            <Text style={styles.smallText}>{metric.detail}</Text>
          </View>
        ))}
      </View>
      <Text style={styles.smallText}>{t(locale, 'feature.more.analytics.relationshipDistribution')}</Text>
      <View style={styles.wrapRow}>
        {analyticsDashboard.relationshipDistribution.map(bucket => (
          <Pill key={bucket.label} label={`${bucket.label}: ${bucket.count}`} />
        ))}
      </View>
      <Text style={styles.smallText}>{t(locale, 'feature.more.analytics.healthBuckets')}</Text>
      <View style={styles.wrapRow}>
        {analyticsDashboard.healthBuckets.map(bucket => (
          <Pill key={bucket.label} label={`${bucket.label}: ${bucket.count}`} selected={bucket.label === 'Healthy'} />
        ))}
      </View>
      {analyticsDashboard.neglectedContacts.length > 0 ? (
        <View style={styles.inlineItem}>
          <Text style={styles.cardTitle}>{t(locale, 'feature.more.analytics.reconnectSuggestions')}</Text>
          {analyticsDashboard.neglectedContacts.map(contact => (
            <View key={contact.contactId} style={styles.inlineItem}>
              <Text style={styles.bodyText}>{contact.name}</Text>
              <Text style={styles.smallText}>
                {t(locale, 'feature.more.analytics.reconnectDetail', {
                  overdueDays: contact.overdueDays,
                  cadenceDays: contact.cadenceDays,
                  healthScore: contact.healthScore
                })}
              </Text>
              <Button
                label={t(locale, 'feature.more.analytics.openContact')}
                tone="secondary"
                onPress={() => dispatch({ type: 'navigate', screen: 'contactDetail', contactId: contact.contactId })}
              />
            </View>
          ))}
        </View>
      ) : null}
      {analyticsDashboard.insights.length > 0 ? (
        <View style={styles.inlineItem}>
          <Text style={styles.cardTitle}>{t(locale, 'feature.more.analytics.insights')}</Text>
          {analyticsDashboard.insights.map(insight => (
            <View key={insight.id} style={styles.inlineItem}>
              <Text style={styles.bodyText}>{insight.title}</Text>
              <Text style={styles.smallText}>{insight.detail}</Text>
              <Button label={insight.actionLabel} tone="secondary" onPress={() => openAnalyticsInsight(insight)} />
            </View>
          ))}
        </View>
      ) : null}
      <View style={styles.actionRow}>
        <Button label={t(locale, 'feature.more.analytics.shareSummary')} tone="secondary" onPress={() => void shareAnalyticsSummary()} />
        <Button
          label={t(
            locale,
            showAnalyticsExportTools
              ? 'feature.more.analytics.hideCsvExport'
              : 'feature.more.analytics.showCsvExport'
          )}
          tone="ghost"
          onPress={() => setShowAnalyticsExportTools(value => !value)}
        />
      </View>
      {showAnalyticsExportTools ? (
        <View style={styles.inlineItem}>
          <Text style={styles.smallText}>{t(locale, 'feature.more.analytics.csvExportDetail')}</Text>
          <Button label={t(locale, 'feature.more.analytics.exportCsv')} tone="ghost" onPress={() => void shareAnalyticsReport()} />
        </View>
      ) : null}
    </Card>

    <Card>
      <Text style={styles.cardTitle}>{t(locale, 'feature.more.backup.title')}</Text>
      <Text style={styles.bodyText}>
        {t(locale, 'feature.more.backup.summary', {
          date: state.backups[0]
            ? formatDateForLocale(state.backups[0].createdAt, state.settings.locale)
            : t(state.settings.locale, 'common.notScheduled')
        })}
      </Text>
      <TextInput
        accessibilityLabel={t(locale, 'feature.more.backup.passphraseLabel')}
        placeholder={t(locale, 'feature.more.backup.passphraseLabel')}
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
          label={t(locale, 'feature.more.backup.exportEncryptedFile')}
          disabled={!canUsePassphrase}
          onPress={() => {
            void exportBackup(backupPassphrase);
          }}
        />
        <Button
          label={t(locale, 'feature.more.backup.selectBackupFile')}
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
            {t(locale, 'feature.more.backup.previewDetail', {
              app: selectedBackup.preview.app,
              date: formatDateForLocale(selectedBackup.preview.createdAt, state.settings.locale),
              count: selectedBackup.preview.recordCount,
              backupVersion: selectedBackup.preview.version,
              dataVersion: selectedBackup.preview.persistenceVersion
            })}
          </Text>
          {selectedBackup.preview.warnings.map(warning => (
            <Text key={warning} style={styles.warningText}>
              {warning}
            </Text>
          ))}
          <Button
            label={t(locale, 'feature.more.backup.confirmRestore')}
            tone="danger"
            disabled={!canRestore}
            onPress={() => {
              showAppAlert(
                t(locale, 'feature.more.backup.restoreConfirmTitle'),
                t(locale, 'feature.more.backup.restoreConfirmBody'),
                [
                  { text: t(locale, 'action.cancel'), style: 'cancel' },
                  {
                    text: t(locale, 'feature.more.backup.restoreAction'),
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
      <Text style={styles.cardTitle}>{t(locale, 'feature.more.settings.title')}</Text>
      <Text style={styles.bodyText}>{t(locale, 'settings.languageDetail')}</Text>
      <View style={styles.wrapRow}>
        {supportedLocales.map(localeOption => (
          <Pill
            key={localeOption}
            label={localeMetadata[localeOption].label}
            selected={state.settings.locale === localeOption}
            accessibilityLabel={`${t(locale, 'settings.language')}: ${localeMetadata[localeOption].label}`}
            onPress={() => dispatch({ type: 'setLocale', locale: localeOption })}
          />
        ))}
      </View>
      <Text style={styles.smallText}>{t(locale, 'feature.more.settings.emailProviderSetupDetail')}</Text>
      <Button
        label={t(
          locale,
          showEmailProviderSetup
            ? 'feature.more.settings.hideEmailProviderSetup'
            : 'feature.more.settings.showEmailProviderSetup'
        )}
        tone="ghost"
        onPress={() => setShowEmailProviderSetup(value => !value)}
      />
      {showEmailProviderSetup ? (
        <>
          <TextInput
            accessibilityLabel={t(locale, 'feature.more.settings.senderEmailLabel')}
            placeholder={t(locale, 'feature.more.settings.senderEmailPlaceholder')}
            value={state.emailDelivery.senderEmail ?? ''}
            onChangeText={senderEmail => dispatch({ type: 'setEmailSender', senderEmail })}
            autoCapitalize="none"
            keyboardType="email-address"
            style={styles.input}
          />
          <Text style={styles.smallText}>
            {t(locale, 'feature.more.settings.emailProviderStatus', {
              status: emailProviderStatus,
              error: state.emailDelivery.lastError ? ` ${state.emailDelivery.lastError}` : ''
            })}
          </Text>
          <Text style={emailEndpointReadiness.productionReady ? styles.smallText : styles.warningText}>
            {providerEndpointDetailLabel(emailEndpointReadiness)}
          </Text>
        </>
      ) : null}
      <View style={styles.wrapRow}>
        <Pill
          label={settingToggleLabel('feature.more.settings.toggle.ai', state.settings.aiEnabled)}
          selected={state.settings.aiEnabled}
          onPress={() => dispatch({ type: 'toggleSetting', key: 'aiEnabled' })}
        />
        <Pill
          label={settingToggleLabel('feature.more.settings.toggle.notifications', state.settings.notificationsEnabled)}
          selected={state.settings.notificationsEnabled}
          onPress={() => dispatch({ type: 'toggleSetting', key: 'notificationsEnabled' })}
        />
        <Pill
          label={settingToggleLabel('feature.more.settings.toggle.sms', state.settings.smsEnabled)}
          selected={state.settings.smsEnabled}
          onPress={() => dispatch({ type: 'toggleSetting', key: 'smsEnabled' })}
        />
        <Pill
          label={settingToggleLabel('feature.more.settings.toggle.email', state.settings.emailEnabled)}
          selected={state.settings.emailEnabled}
          onPress={() => dispatch({ type: 'toggleSetting', key: 'emailEnabled' })}
        />
        <Pill
          label={settingToggleLabel(
            'feature.more.settings.toggle.manualWhatsApp',
            state.settings.whatsappHandoffEnabled
          )}
          selected={state.settings.whatsappHandoffEnabled}
          onPress={() => dispatch({ type: 'toggleSetting', key: 'whatsappHandoffEnabled' })}
        />
        <Pill
          label={settingToggleLabel(
            'feature.more.settings.toggle.biometricLock',
            state.settings.biometricLockEnabled
          )}
          selected={state.settings.biometricLockEnabled}
          onPress={() => dispatch({ type: 'toggleSetting', key: 'biometricLockEnabled' })}
        />
      </View>
      <Text style={styles.smallText}>
        {t(locale, 'feature.more.settings.automationSummary', {
          mode: automationModeLabel(state.settings.automationMode),
          start: state.settings.quietHours.start,
          end: state.settings.quietHours.end
        })}
      </Text>
      <Text style={styles.smallText}>{t(locale, 'feature.more.settings.groupDefaults')}</Text>
      {relationshipGroupOptions.map(group => {
        const defaults = state.settings.groupDefaults[group];
        return (
          <View key={group} style={styles.inlineItem}>
            <View style={styles.rowBetween}>
              <Text style={styles.bodyText}>{relationshipGroupLabel(group)}</Text>
              <Pill label={cadenceLabel(defaults.checkInCadenceDays)} selected />
            </View>
            <Text style={styles.smallText}>{t(locale, 'feature.more.settings.channel')}</Text>
            <View style={styles.wrapRow}>
              {channels.map(channel => (
                <Pill
                  key={channel}
                  label={channelLabel(channel)}
                  selected={defaults.preferredChannel === channel}
                  onPress={() =>
                    dispatch({
                      type: 'setRelationshipGroupDefault',
                      group,
                      defaults: { preferredChannel: channel }
                    })
                  }
                />
              ))}
            </View>
            <Text style={styles.smallText}>{t(locale, 'feature.more.settings.checkInCadence')}</Text>
            <View style={styles.wrapRow}>
              {checkInCadenceOptions.map(days => (
                <Pill
                  key={days}
                  label={cadenceLabel(days)}
                  selected={defaults.checkInCadenceDays === days}
                  onPress={() =>
                    dispatch({
                      type: 'setRelationshipGroupDefault',
                      group,
                      defaults: { checkInCadenceDays: days }
                    })
                  }
                />
              ))}
            </View>
            <Text style={styles.smallText}>{t(locale, 'feature.more.settings.automationReview')}</Text>
            <View style={styles.wrapRow}>
              {visibleAutomationModesFor(defaults.automationMode).map(mode => (
                <Pill
                  key={mode}
                  label={automationModeLabel(mode)}
                  selected={defaults.automationMode === mode}
                  onPress={() =>
                    dispatch({
                      type: 'setRelationshipGroupDefault',
                      group,
                      defaults: { automationMode: mode }
                    })
                  }
                />
              ))}
            </View>
            <Text style={styles.smallText}>{t(locale, 'feature.more.settings.toneDefaults')}</Text>
            <View style={styles.wrapRow}>
              {tones.map(tone => {
                const nextTone = defaults.tone.includes(tone)
                  ? defaults.tone.filter(item => item !== tone)
                  : [...defaults.tone, tone];
                return (
                  <Pill
                    key={tone}
                    label={localizedToneLabel(locale, tone)}
                    selected={defaults.tone.includes(tone)}
                    onPress={() =>
                      dispatch({
                        type: 'setRelationshipGroupDefault',
                        group,
                        defaults: { tone: nextTone.length > 0 ? nextTone : defaults.tone }
                      })
                    }
                  />
                );
              })}
            </View>
          </View>
        );
      })}
    </Card>

    <Card>
      <Text style={styles.cardTitle}>{t(locale, 'feature.more.activityHistory.title')}</Text>
      <TextInput
        accessibilityLabel={t(locale, 'feature.more.activityHistory.searchLabel')}
        placeholder={t(locale, 'feature.more.activityHistory.searchPlaceholder')}
        placeholderTextColor={colors.muted}
        value={activityQuery}
        onChangeText={setActivityQuery}
        style={styles.input}
      />
      <Text style={styles.smallText}>{t(locale, 'feature.more.activityHistory.type')}</Text>
      <View style={styles.wrapRow}>
        {activityTypeFilters.map(item => (
          <Pill key={item} label={activityTypeLabel(item)} selected={activityType === item} onPress={() => setActivityType(item)} />
        ))}
      </View>
      <Text style={styles.smallText}>{t(locale, 'feature.more.activityHistory.severity')}</Text>
      <View style={styles.wrapRow}>
        {activitySeverityFilters.map(item => (
          <Pill
            key={item}
            label={activitySeverityLabel(item)}
            selected={activitySeverity === item}
            onPress={() => setActivitySeverity(item)}
          />
        ))}
      </View>
      <Text style={styles.smallText}>{t(locale, 'feature.more.activityHistory.date')}</Text>
      <View style={styles.wrapRow}>
        {activityDateFilters.map(item => (
          <Pill key={item} label={activityDateLabel(item)} selected={activityDate === item} onPress={() => setActivityDate(item)} />
        ))}
      </View>
      {activityHistory.rows.length > 0 ? (
        activityHistory.rows.slice(0, 12).map(row => (
          <View key={row.item.id} style={styles.inlineItem}>
            <View style={styles.rowBetween}>
              <Text style={styles.bodyText}>{activityTitleLabel(locale, row.item)}</Text>
              <Pill label={activitySeverityLabel(row.item.severity)} selected={row.isOpenIssue} />
            </View>
            <Text style={styles.smallText}>
              {t(locale, 'feature.more.activityHistory.rowMeta', {
                type: activityTypeLabel(row.item.type),
                date: formatDateForLocale(row.item.createdAt, state.settings.locale)
              })}
            </Text>
            <Text style={styles.smallText}>{activityDetailLabel(locale, row.item)}</Text>
            {row.recoveryState === 'fallback' ? (
              <Text style={styles.warningText}>{activityRecoveryDetail(row)}</Text>
            ) : null}
            <Button
              label={activityActionLabel(row)}
              tone="secondary"
              onPress={() =>
                dispatch({
                  type: 'navigate',
                  screen: row.targetScreen,
                  contactId: row.contactId,
                  messageId: row.messageId
                })
              }
            />
          </View>
        ))
      ) : (
        <Text style={styles.bodyText}>
          {activityHistory.emptyState === 'No activity yet'
            ? t(locale, 'feature.more.activityHistory.emptyNoActivity')
            : t(locale, 'feature.more.activityHistory.emptyNoMatches')}
        </Text>
      )}
    </Card>

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
  state: ReturnType<typeof createProductionInitialState>;
  dispatch: React.Dispatch<RelateAction>;
  onManualHandoff: (message: MessageDraft) => void;
  onSendEmail: (message: MessageDraft) => void;
  onGenerateMessage: (
    contactId: string,
    eventId: string | undefined,
    reason: ComposerReason,
    contextOptions?: AiDraftContextOptions
  ) => void;
  onTestAiProvider?: () => void;
  onExportBackup?: (passphrase: string) => void;
  onPickBackup?: () => Promise<BackupFilePickResult | undefined>;
  onRestoreBackup?: (raw: string, passphrase: string, recordCount: number) => void;
  onClearLocalData?: () => void;
  onImportDeviceContacts?: () => void;
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
    minHeight: 48,
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
    minHeight: 48,
    justifyContent: 'center',
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
  shortInput: {
    minWidth: 96,
    flexGrow: 1
  },
  flexInput: {
    minWidth: 180,
    flexGrow: 1
  },
  dateInput: {
    minWidth: 136,
    flexGrow: 1
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

const RootApp = () => (
  <AppErrorBoundary onOperationalIssue={issue => appOperationalIssues.report(issue)}>
    <App />
  </AppErrorBoundary>
);

export default RootApp;
