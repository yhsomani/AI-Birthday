import type { AiDraftContextOptions } from './aiDrafting';
import { resolveContactPreferencesForContact } from './contactPreferences';
import type { AppState } from './types';
import { eventOccurrenceIso } from './occasionDates';

export interface AiContextPreviewMemory {
  id: string;
  category: string;
  body: string;
  selected: boolean;
}

export interface AiContextPreview {
  alwaysUsed: string[];
  optionalMemories: AiContextPreviewMemory[];
  priorMessages: {
    count: number;
    selected: boolean;
  };
  privateMemoryCount: number;
  summary: string;
}

const summarizeMemory = (body: string) => (body.length > 160 ? `${body.slice(0, 157).trimEnd()}...` : body);

export const buildAiContextPreview = (
  state: AppState,
  contactId: string,
  eventId: string | undefined,
  options: AiDraftContextOptions = {}
): AiContextPreview => {
  const contact = state.contacts.find(item => item.id === contactId);
  const preferences = contact ? resolveContactPreferencesForContact(state.settings, contact) : undefined;
  const event = eventId ? state.events.find(item => item.id === eventId) : undefined;
  const excludedMemoryIds = new Set(options.excludedMemoryIds ?? []);
  const optionalMemories = state.memories
    .filter(memory => memory.contactId === contactId && memory.category !== 'Private')
    .map(memory => ({
      id: memory.id,
      category: memory.category,
      body: summarizeMemory(memory.body),
      selected: !excludedMemoryIds.has(memory.id)
    }));
  const selectedMemoryCount = optionalMemories.filter(memory => memory.selected).length;
  const privateMemoryCount = state.memories.filter(memory => memory.contactId === contactId && memory.category === 'Private').length;
  const priorCount = state.messages.filter(message => message.contactId === contactId && message.status === 'Sent').length;
  const includePriorMessages = options.includePriorMessages ?? true;

  return {
    alwaysUsed: [
      contact ? `Relationship: ${contact.relationship} (${contact.group})` : 'Relationship: unavailable contact',
      contact && preferences ? `Contact style: ${contact.language}, ${preferences.tone.join(', ')}` : 'Contact style: unavailable',
      event
        ? `Event: ${event.label} on ${(eventOccurrenceIso(event) ?? event.date).slice(0, 10)}`
        : 'Event: none selected',
      `Global style: ${state.styleProfile.confidence} confidence`
    ],
    optionalMemories,
    priorMessages: {
      count: priorCount,
      selected: includePriorMessages
    },
    privateMemoryCount,
    summary: `${selectedMemoryCount} selected memory item(s), ${optionalMemories.length - selectedMemoryCount} excluded memory item(s), ${privateMemoryCount} private memory item(s) excluded, ${includePriorMessages ? priorCount : 0} prior sent message(s) selected.`
  };
};
