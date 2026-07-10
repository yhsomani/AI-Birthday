import type { AppState, MessageChannel, MessageDraft, Screen } from './types';
import { resolveContactPreferencesForContact } from './contactPreferences';
import { assessDuplicateMessageRisk } from './duplicateGuard';
import { validateMessageBodyForChannel } from './messageBodyPolicy';
import { eventOccurrenceLocalDateKey } from './occasionDates';
import { scheduleMessageForEvent } from './schedulingPolicy';

export type MessageInboxTab = 'All' | 'Review' | 'Today' | 'Scheduled' | 'Blocked' | 'Failed' | 'Sent' | 'Rejected';
export type MessageInboxChannelFilter = 'All' | MessageChannel;
export type MessageInboxSort = 'Newest' | 'Scheduled' | 'Contact' | 'Status';
export type MessageBulkAction = 'Approve' | 'Reject' | 'Retry' | 'Revoke approval';

export interface MessageInboxRecovery {
  title: string;
  detail: string;
  actionLabel: string;
  targetScreen: Screen;
  /** Strict console action that performs the advertised recovery step. */
  command: Record<string, unknown>;
}

export interface MessageInboxRow {
  message: MessageDraft;
  contactName: string;
  eventLabel?: string;
  recovery?: MessageInboxRecovery;
}

export interface MessageInboxResult {
  rows: MessageInboxRow[];
  counts: Record<MessageInboxTab, number>;
  emptyState: 'No messages yet' | 'No matching messages' | undefined;
}

export interface MessageBulkSkip {
  messageId: string;
  contactName: string;
  reason: string;
}

export interface MessageBulkActionReport {
  action: MessageBulkAction;
  selectedCount: number;
  eligibleIds: string[];
  skipped: MessageBulkSkip[];
  summary: string;
  verificationGuidance?: string;
  confirmation: string;
  requiresConfirmation: boolean;
}

export interface MessageInboxOptions {
  tab: MessageInboxTab;
  channel: MessageInboxChannelFilter;
  query: string;
  sort: MessageInboxSort;
  emailEndpointConfigured?: boolean;
  nowIso?: string;
}

export const messageInboxTabs: MessageInboxTab[] = [
  'All',
  'Review',
  'Today',
  'Scheduled',
  'Blocked',
  'Failed',
  'Sent',
  'Rejected'
];
export const messageInboxChannelFilters: MessageInboxChannelFilter[] = ['All', 'SMS', 'WhatsApp', 'Email', 'Manual'];
export const messageInboxSorts: MessageInboxSort[] = ['Newest', 'Scheduled', 'Contact', 'Status'];
export const messageBulkActions: MessageBulkAction[] = ['Approve', 'Reject', 'Retry', 'Revoke approval'];

const tabForMessage = (message: MessageDraft): MessageInboxTab =>
  message.status === 'Needs review' || message.status === 'Draft'
    ? 'Review'
    : message.status === 'Scheduled'
      ? 'Scheduled'
      : message.status === 'Blocked'
        ? 'Blocked'
        : message.status === 'Failed'
          ? 'Failed'
          : message.status === 'Delivery pending' || message.status === 'Delivery unknown'
            ? 'Failed'
            : message.status === 'Sent'
              ? 'Sent'
              : 'Rejected';

const searchTextFor = (row: MessageInboxRow) =>
  [
    row.contactName,
    row.eventLabel,
    row.message.reason,
    row.message.status,
    row.message.channel,
    row.message.quality,
    row.message.readiness,
    row.message.lastError,
    row.message.body
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();

const messageTime = (message: MessageDraft) => message.sentAt ?? message.scheduledFor ?? '';

const isReviewQueueMessage = (message: MessageDraft) => message.status === 'Needs review' || message.status === 'Draft';
const localDatePart = (iso?: string) => {
  if (!iso) return undefined;
  const value = new Date(iso);
  if (Number.isNaN(value.getTime())) return undefined;
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
};
const isScheduledForDate = (message: MessageDraft, iso: string) =>
  message.status === 'Scheduled' && localDatePart(message.scheduledFor) === localDatePart(iso);
const messageMatchesTab = (message: MessageDraft, tab: MessageInboxTab, nowIso: string) =>
  tab === 'All' || (tab === 'Today' ? isScheduledForDate(message, nowIso) : tabForMessage(message) === tab);

export const findNextReviewMessageId = (state: AppState, currentMessageId?: string) =>
  state.messages.find(message => message.id !== currentMessageId && isReviewQueueMessage(message))?.id;

export const messageApprovalRouteIssue = (
  state: AppState,
  message: MessageDraft,
  options: { allowDndManualControl?: boolean; allowShareFallback?: boolean } = {}
) => {
  const contact = state.contacts.find(item => item.id === message.contactId);
  if (!contact) {
    return 'The contact is no longer available.';
  }

  if (contact.archivedAt) {
    return 'The contact is archived. Restore it before scheduling or sending.';
  }

  if (contact.dnd && !options.allowDndManualControl) {
    return 'Contact is in do-not-disturb.';
  }

  // A deliberate post-approval copy/share fallback needs no recipient route.
  // Contact existence/archive, approval, timing, body and duplicate checks are
  // still enforced by the surrounding handoff boundary.
  if (options.allowShareFallback) return undefined;

  if (message.channel === 'SMS') {
    if (!state.settings.smsEnabled) {
      return 'SMS is disabled in Settings.';
    }
    if (!contact.phone) {
      return 'SMS requires a phone number.';
    }
  }

  if (message.channel === 'WhatsApp') {
    if (!state.settings.whatsappHandoffEnabled) {
      return 'WhatsApp handoff is disabled in Settings.';
    }
    if (!contact.phone) {
      return 'WhatsApp handoff requires a phone number.';
    }
    if (!state.privacy.whatsappHandoffConsent) {
      return 'WhatsApp handoff requires explicit consent before the destination app can be opened.';
    }
  }

  if (message.channel === 'Email') {
    if (!contact.email) {
      return 'Email handoff requires a recipient address.';
    }
  }

  return undefined;
};

const bulkApprovalScheduleIssue = (state: AppState, message: MessageDraft, now: Date) => {
  if (!message.eventId) {
    return message.scheduledFor
      ? 'This draft has a scheduled time without event context. Clear the stale time or link an event before approval.'
      : undefined;
  }
  const event = state.events.find(item => item.id === message.eventId && item.contactId === message.contactId);
  if (!event) {
    return 'The linked event is no longer valid for this recipient. Return the message to review.';
  }
  const currentOccurrence = eventOccurrenceLocalDateKey(event, now);
  if (message.occurrenceDate && message.occurrenceDate !== currentOccurrence) {
    return 'This draft targets an event occurrence that has passed. Regenerate it for the current occurrence before approval.';
  }
  const contact = state.contacts.find(item => item.id === message.contactId);
  const preferences = contact ? resolveContactPreferencesForContact(state.settings, contact) : undefined;
  return scheduleMessageForEvent(event, state.settings, message.channel, now, {
    customSendTime: preferences?.customSendTime,
    quietHoursBehavior: preferences?.quietHoursBehavior
  }).issue;
};

const bulkEligibilityIssue = (state: AppState, message: MessageDraft, action: MessageBulkAction, now: Date) => {
  if (action === 'Approve') {
    if (message.status !== 'Needs review' && message.status !== 'Draft') {
      return 'Only review or draft messages can be approved.';
    }
    const bodyPolicy = validateMessageBodyForChannel(message);
    if (!bodyPolicy.ok) {
      return bodyPolicy.message;
    }
    const duplicateRisk = assessDuplicateMessageRisk(state, message);
    if (duplicateRisk.risk.risk && !duplicateRisk.acknowledged) {
      return 'Duplicate risk must be acknowledged from preview first.';
    }
    return messageApprovalRouteIssue(state, message) ?? bulkApprovalScheduleIssue(state, message, now);
  }

  if (action === 'Reject') {
    if (
      message.status === 'Scheduled' ||
      message.status === 'Delivery pending' ||
      message.status === 'Delivery unknown'
    ) {
      return 'Scheduled or in-flight deliveries cannot be rejected.';
    }
    if (message.status === 'Sent' || message.status === 'Rejected') {
      return 'Sent or already rejected messages cannot be rejected again.';
    }
    return undefined;
  }

  if (action === 'Retry') {
    if (message.status !== 'Failed' && message.status !== 'Blocked') {
      return 'Only failed or blocked messages can be prepared for retry.';
    }
    return state.contacts.some(contact => contact.id === message.contactId)
      ? undefined
      : 'The contact is no longer available.';
  }

  if (message.status !== 'Scheduled') {
    return 'Only scheduled messages can have approval revoked.';
  }
  return undefined;
};

const formatChannelList = (channels: MessageChannel[]) => {
  if (channels.length <= 1) {
    return channels[0] ?? '';
  }

  if (channels.length === 2) {
    return `${channels[0]} and ${channels[1]}`;
  }

  return `${channels.slice(0, -1).join(', ')}, and ${channels[channels.length - 1]}`;
};

const buildBulkVerificationGuidance = (
  state: AppState,
  eligibleMessages: MessageDraft[],
  action: MessageBulkAction
) => {
  if (action !== 'Approve' || eligibleMessages.length < 2) {
    return undefined;
  }

  const previouslySentChannels = new Set(
    state.messages.filter(message => message.status === 'Sent').map(message => message.channel)
  );
  const unverifiedChannels = Array.from(
    new Set(
      eligibleMessages
        .map(message => message.channel)
        .filter(channel => channel !== 'Manual' && !previouslySentChannels.has(channel))
    )
  );

  if (unverifiedChannels.length === 0) {
    return undefined;
  }

  const channelList = formatChannelList(unverifiedChannels);
  const messageNoun = unverifiedChannels.length === 1 ? 'message' : 'message on each channel';
  return `Before bulk approval on ${channelList}, complete one low-risk ${messageNoun} and mark it sent so the channel is verified.`;
};

export const buildMessageBulkActionReport = (
  state: AppState,
  messageIds: string[],
  action: MessageBulkAction,
  now: Date = new Date()
): MessageBulkActionReport => {
  const uniqueIds = Array.from(new Set(messageIds));
  const messagesById = new Map(state.messages.map(message => [message.id, message]));
  const eligibleIds: string[] = [];
  const eligibleMessages: MessageDraft[] = [];
  const skipped: MessageBulkSkip[] = [];

  uniqueIds.forEach(messageId => {
    const message = messagesById.get(messageId);
    if (!message) {
      skipped.push({
        messageId,
        contactName: 'Unknown message',
        reason: 'This message is no longer available.'
      });
      return;
    }

    const contactName = state.contacts.find(contact => contact.id === message.contactId)?.name ?? 'Unknown contact';
    const issue = bulkEligibilityIssue(state, message, action, now);
    if (issue) {
      skipped.push({
        messageId,
        contactName,
        reason: issue
      });
      return;
    }

    eligibleIds.push(messageId);
    eligibleMessages.push(message);
  });

  const applied = eligibleIds.length;
  const skippedCount = skipped.length;
  const actionText = action.toLowerCase();
  const summary =
    uniqueIds.length === 0
      ? `Select messages before using bulk ${actionText}.`
      : skippedCount === 0
        ? `${applied}/${uniqueIds.length} selected message(s) can be processed.`
        : `${applied}/${uniqueIds.length} selected message(s) can be processed; ${skippedCount} will be skipped.`;

  const skipPreview = skipped
    .slice(0, 3)
    .map(item => `${item.contactName}: ${item.reason}`)
    .join(' ');
  const verificationGuidance = buildBulkVerificationGuidance(state, eligibleMessages, action);
  const confirmation = [summary, verificationGuidance, skipPreview].filter(Boolean).join(' ');

  return {
    action,
    selectedCount: uniqueIds.length,
    eligibleIds,
    skipped,
    summary,
    verificationGuidance,
    confirmation,
    requiresConfirmation:
      applied > 1 ||
      skippedCount > 0 ||
      action === 'Reject' ||
      action === 'Revoke approval' ||
      Boolean(verificationGuidance)
  };
};

const buildRecovery = (
  state: AppState,
  message: MessageDraft,
  emailEndpointConfigured: boolean
): MessageInboxRecovery | undefined => {
  if (message.status === 'Delivery pending') {
    return {
      title: 'Delivery confirmation pending',
      detail: 'The provider accepted this attempt. Wait for status reconciliation; do not send it again.',
      actionLabel: 'Review delivery',
      targetScreen: 'wishPreview',
      command: { type: 'email.reconcile', messageId: message.id }
    };
  }
  if (message.status === 'Delivery unknown') {
    return {
      title: 'Delivery status unknown',
      detail: 'The provider result was lost. Reconcile the existing idempotent attempt before any retry.',
      actionLabel: 'Review delivery',
      targetScreen: 'wishPreview',
      command: { type: 'email.reconcile', messageId: message.id }
    };
  }
  if (message.status !== 'Failed' && message.status !== 'Blocked') {
    return undefined;
  }

  const contact = state.contacts.find(item => item.id === message.contactId);
  if (!contact) {
    return {
      title: 'Contact unavailable',
      detail: 'The contact attached to this message is missing. Review contacts before retrying.',
      actionLabel: 'Open contacts',
      targetScreen: 'contacts',
      command: { type: 'contacts.query', sort: 'Name' }
    };
  }

  const bodyPolicy = validateMessageBodyForChannel(message);
  if (!bodyPolicy.ok) {
    return {
      title: 'Message needs editing',
      detail: bodyPolicy.message,
      actionLabel: 'Prepare retry for editing',
      targetScreen: 'wishPreview',
      command: { type: 'messages.retry', messageId: message.id }
    };
  }

  const duplicateRisk = assessDuplicateMessageRisk(state, message);
  if (duplicateRisk.risk.risk && !duplicateRisk.acknowledged) {
    return {
      title: 'Duplicate risk needs review',
      detail: 'A similar message exists. Edit, reject, or explicitly continue after review.',
      actionLabel: 'Prepare retry for review',
      targetScreen: 'wishPreview',
      command: { type: 'messages.retry', messageId: message.id }
    };
  }

  if ((message.channel === 'SMS' || message.channel === 'WhatsApp') && !contact.phone) {
    return {
      title: 'Missing phone number',
      detail: `${message.channel} handoff needs a phone number or a manual fallback.`,
      actionLabel: 'Open contact',
      targetScreen: 'contactDetail',
      command: { type: 'contacts.inspect', contactId: contact.id }
    };
  }

  if (message.channel === 'Email' && !contact.email) {
    return {
      title: 'Missing email address',
      detail: 'Email delivery needs a recipient address or manual fallback.',
      actionLabel: 'Open contact',
      targetScreen: 'contactDetail',
      command: { type: 'contacts.inspect', contactId: contact.id }
    };
  }

  if (message.channel === 'Email' && !emailEndpointConfigured) {
    return {
      title: 'Email provider not configured',
      detail: 'Configure provider delivery or use email handoff.',
      actionLabel: 'Open setup',
      targetScreen: 'setupCheck',
      command: { type: 'setup.inspect' }
    };
  }

  return {
    title: 'Ready to retry',
    detail: message.lastError ?? 'Retry after reviewing the message and route.',
    actionLabel: 'Retry message',
    targetScreen: 'messages',
    command: { type: 'messages.retry', messageId: message.id }
  };
};

export const buildMessageInbox = (state: AppState, options: MessageInboxOptions): MessageInboxResult => {
  const nowIso = options.nowIso ?? new Date().toISOString();
  const rows = state.messages.map(message => {
    const contact = state.contacts.find(item => item.id === message.contactId);
    const event = message.eventId ? state.events.find(item => item.id === message.eventId) : undefined;
    return {
      message,
      contactName: contact?.name ?? 'Unknown contact',
      eventLabel: event?.label,
      recovery: buildRecovery(state, message, Boolean(options.emailEndpointConfigured))
    };
  });

  const counts = messageInboxTabs.reduce(
    (acc, tab) => {
      acc[tab] = rows.filter(row => messageMatchesTab(row.message, tab, nowIso)).length;
      return acc;
    },
    {} as Record<MessageInboxTab, number>
  );

  const query = options.query.trim().toLowerCase();
  const filteredRows = rows
    .filter(row => messageMatchesTab(row.message, options.tab, nowIso))
    .filter(row => options.channel === 'All' || row.message.channel === options.channel)
    .filter(row => query.length === 0 || searchTextFor(row).includes(query))
    .sort((a, b) => {
      if (options.sort === 'Contact') {
        return (
          a.contactName.localeCompare(b.contactName) || messageTime(b.message).localeCompare(messageTime(a.message))
        );
      }
      if (options.sort === 'Status') {
        return (
          a.message.status.localeCompare(b.message.status) ||
          messageTime(b.message).localeCompare(messageTime(a.message))
        );
      }
      if (options.sort === 'Scheduled') {
        return (a.message.scheduledFor ?? '9999').localeCompare(b.message.scheduledFor ?? '9999');
      }
      return messageTime(b.message).localeCompare(messageTime(a.message));
    });

  return {
    rows: filteredRows,
    counts,
    emptyState: rows.length === 0 ? 'No messages yet' : filteredRows.length === 0 ? 'No matching messages' : undefined
  };
};
