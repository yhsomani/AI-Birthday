import type { AppState, MessageChannel, MessageDraft } from './types';

export type ChatHistoryChannelFilter = 'All' | MessageChannel;

export type ChatHistoryQuery = {
  contactId: string;
  searchQuery?: string;
  channel?: ChatHistoryChannelFilter;
};

export type ChatHistoryResult = {
  contactExists: boolean;
  messages: MessageDraft[];
  emptyState: 'No sent messages' | 'No matching messages' | 'Contact unavailable' | undefined;
};

const searchableText = (message: MessageDraft) =>
  `${message.body} ${message.channel} ${message.reason} ${message.readiness}`.toLowerCase();

const sentTime = (message: MessageDraft) =>
  new Date(message.sentAt ?? message.scheduledFor ?? 0).getTime();

export const buildChatHistory = (state: AppState, query: ChatHistoryQuery): ChatHistoryResult => {
  const search = query.searchQuery?.trim().toLowerCase() ?? '';
  const channel = query.channel ?? 'All';
  const contactExists = state.contacts.some(contact => contact.id === query.contactId);
  const sentForContact = state.messages
    .filter(message => message.contactId === query.contactId && message.status === 'Sent')
    .sort((a, b) => sentTime(b) - sentTime(a));
  const messages = sentForContact.filter(message => {
    const channelMatches = channel === 'All' || message.channel === channel;
    const searchMatches = search.length === 0 || searchableText(message).includes(search);
    return channelMatches && searchMatches;
  });

  const emptyState =
    !contactExists && sentForContact.length === 0
      ? 'Contact unavailable'
      : sentForContact.length === 0
        ? 'No sent messages'
        : messages.length === 0
          ? 'No matching messages'
          : undefined;

  return {
    contactExists,
    messages,
    emptyState
  };
};
