import type { AiDraftContextOptions } from './aiDrafting';
import { resolveContactPreferencesForContact } from './contactPreferences';
import type { AppState } from './types';
import { eventOccurrenceIso } from './occasionDates';
import { buildMemoryPersonalizationContext, type GenerationConstraintKind } from './personalizationContextPolicy';

export interface AiContextPreviewMemory {
  id: string;
  category: string;
  body: string;
  selected: boolean;
}

export interface AiContextPreviewGenerationConstraint {
  memoryId: string;
  kind: GenerationConstraintKind;
  instruction: string;
  selected: boolean;
}

export interface AiContextPreview {
  alwaysUsed: string[];
  optionalMemories: AiContextPreviewMemory[];
  generationConstraints: AiContextPreviewGenerationConstraint[];
  priorMessages: {
    count: number;
    selected: boolean;
  };
  privateMemoryCount: number;
  sensitiveMemoryCount: number;
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
  const personalization = buildMemoryPersonalizationContext(state.memories, {
    contactId
  });
  const optionalMemories = personalization.mentionableFacts.map(memory => ({
    id: memory.memoryId,
    category: memory.category,
    body: summarizeMemory(memory.text),
    selected: !excludedMemoryIds.has(memory.memoryId)
  }));
  const generationConstraints = personalization.generationConstraints.map(constraint => ({
    memoryId: constraint.memoryId,
    kind: constraint.kind,
    instruction: constraint.instruction,
    selected: !excludedMemoryIds.has(constraint.memoryId)
  }));
  const selectedMemoryCount = optionalMemories.filter(memory => memory.selected).length;
  const selectedConstraintCount = generationConstraints.filter(constraint => constraint.selected).length;
  const priorCount = state.messages.filter(
    message => message.contactId === contactId && message.status === 'Sent'
  ).length;
  const includePriorMessages = options.includePriorMessages ?? true;

  return {
    alwaysUsed: [
      contact ? `Relationship: ${contact.relationship} (${contact.group})` : 'Relationship: unavailable contact',
      contact && preferences
        ? `Contact style: ${contact.language}, ${preferences.tone.join(', ')}`
        : 'Contact style: unavailable',
      event
        ? `Event: ${event.label} on ${(eventOccurrenceIso(event) ?? event.date).slice(0, 10)}`
        : 'Event: none selected',
      state.styleProfile.enabledForAiDrafts
        ? `Global style: ${state.styleProfile.confidence} confidence`
        : 'Global style: disabled for future AI drafts'
    ],
    optionalMemories,
    generationConstraints,
    priorMessages: {
      count: priorCount,
      selected: includePriorMessages
    },
    privateMemoryCount: personalization.excludedPrivateMemoryCount,
    sensitiveMemoryCount: personalization.excludedSensitiveMemoryCount,
    summary: `${selectedMemoryCount} selected mentionable memory item(s), ${optionalMemories.length - selectedMemoryCount} excluded mentionable memory item(s), ${selectedConstraintCount} generation constraint(s) selected as instructions only, ${generationConstraints.length - selectedConstraintCount} generation constraint(s) excluded, ${personalization.excludedPrivateMemoryCount} private memory item(s) excluded, ${personalization.excludedSensitiveMemoryCount} sensitive memory item(s) excluded, ${includePriorMessages ? priorCount : 0} prior sent message(s) selected.`
  };
};
