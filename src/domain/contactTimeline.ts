import type { AppState, Screen } from './types';

export type ContactTimelineFilter = 'All' | 'Events' | 'Memories' | 'Gifts' | 'Messages';
export type ContactTimelineEntryType = Exclude<ContactTimelineFilter, 'All'>;

export interface ContactTimelineEntry {
  id: string;
  type: ContactTimelineEntryType;
  title: string;
  detail: string;
  dateIso: string;
  targetScreen?: Screen;
  messageId?: string;
}

export interface ContactTimelineResult {
  entries: ContactTimelineEntry[];
  emptyMessage: string;
}

export const contactTimelineFilters: ContactTimelineFilter[] = ['All', 'Events', 'Memories', 'Gifts', 'Messages'];

const giftDateIso = (year: number) => `${year}-01-01T00:00:00.000Z`;

const matchesFilter = (entry: ContactTimelineEntry, filter: ContactTimelineFilter) =>
  filter === 'All' || entry.type === filter;

export const buildContactTimeline = (
  state: AppState,
  contactId: string,
  filter: ContactTimelineFilter = 'All'
): ContactTimelineResult => {
  const contact = state.contacts.find(item => item.id === contactId);
  if (!contact) {
    return {
      entries: [],
      emptyMessage: 'This contact is no longer available.'
    };
  }

  const entries: ContactTimelineEntry[] = [
    ...state.events
      .filter(event => event.contactId === contactId)
      .map(event => ({
        id: event.id,
        type: 'Events' as const,
        title: event.label,
        detail: `${event.type} - ${event.verified ? 'Verified' : 'Needs review'}`,
        dateIso: event.date,
        targetScreen: 'events' as const
      })),
    ...state.memories
      .filter(memory => memory.contactId === contactId)
      .map(memory => ({
        id: memory.id,
        type: 'Memories' as const,
        title: memory.category,
        detail: memory.body,
        dateIso: memory.createdAt,
        targetScreen: 'contactDetail' as const
      })),
    ...state.gifts
      .filter(gift => gift.contactId === contactId)
      .map(gift => ({
        id: gift.id,
        type: 'Gifts' as const,
        title: gift.name,
        detail: `${gift.occasion} - ${gift.feedback}`,
        dateIso: giftDateIso(gift.year),
        targetScreen: 'contactDetail' as const
      })),
    ...state.messages
      .filter(message => message.contactId === contactId && message.status === 'Sent')
      .map(message => ({
        id: message.id,
        type: 'Messages' as const,
        title: message.reason,
        detail: `${message.channel} - sent`,
        dateIso: message.sentAt ?? message.scheduledFor ?? '',
        targetScreen: 'chatHistory' as const,
        messageId: message.id
      }))
  ]
    .filter(entry => matchesFilter(entry, filter))
    .sort((a, b) => b.dateIso.localeCompare(a.dateIso));

  return {
    entries,
    emptyMessage:
      filter === 'All' ? 'No relationship timeline entries yet.' : `No ${filter.toLowerCase()} found for this contact.`
  };
};
