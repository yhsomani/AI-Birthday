import type { AppState, Contact, MemoryCategory, MemoryNote } from './types';

export type ContactEnrichmentPromptId =
  | 'relationship-context'
  | 'message-mention'
  | 'message-avoid'
  | 'language-style';

export interface ContactEnrichmentPrompt {
  id: ContactEnrichmentPromptId;
  question: string;
  reason: string;
  category: MemoryCategory;
  memoryPrefix: string;
  priority: number;
}

export interface ContactEnrichmentPlan {
  contactId: string;
  score: number;
  label: 'Needs details' | 'Growing' | 'Strong';
  prompts: ContactEnrichmentPrompt[];
  completedSignals: string[];
}

export type EnrichmentAnswerValidation =
  | { ok: true; value: string }
  | { ok: false; message: string };

const nonPrivateMemories = (state: AppState, contactId: string) =>
  state.memories.filter(memory => memory.contactId === contactId && memory.category !== 'Private');

const includesAny = (text: string, pattern: RegExp) => pattern.test(text);

const contactContextText = (contact: Contact, memories: MemoryNote[]) =>
  [contact.relationship, contact.group, contact.notesSummary, ...memories.map(memory => memory.body)].join(' ');

const hasPersonalContext = (contact: Contact, memories: MemoryNote[]) =>
  contact.notesSummary.trim().length >= 24 ||
  memories.some(memory => ['General', 'Event', 'Milestone'].includes(memory.category));

const hasMentionPreference = (contact: Contact, memories: MemoryNote[]) =>
  includesAny(contactContextText(contact, memories), /\b(likes|loves|prefers|favorite|favourite|mention|enjoys)\b/i) ||
  memories.some(memory => memory.category === 'Preference');

const hasAvoidGuidance = (contact: Contact, memories: MemoryNote[]) =>
  contact.tone.includes('No emoji') ||
  includesAny(contactContextText(contact, memories), /\b(avoid|do not|don't|never mention|no emoji|should not)\b/i);

const hasLanguageGuidance = (contact: Contact, memories: MemoryNote[]) =>
  contact.language !== 'English' || includesAny(contactContextText(contact, memories), /\b(language|hindi|hinglish|english)\b/i);

const promptFor = (id: ContactEnrichmentPromptId, contact: Contact): ContactEnrichmentPrompt => {
  switch (id) {
    case 'relationship-context':
      return {
        id,
        question: `How do you know ${contact.name}?`,
        reason: 'Relationship context helps future messages sound specific.',
        category: 'General',
        memoryPrefix: 'Relationship context: ',
        priority: 1
      };
    case 'message-mention':
      return {
        id,
        question: `What should a message to ${contact.name} mention?`,
        reason: 'Mention preferences reduce generic drafts.',
        category: 'Preference',
        memoryPrefix: 'Message should mention: ',
        priority: 2
      };
    case 'message-avoid':
      return {
        id,
        question: `What should messages to ${contact.name} avoid?`,
        reason: 'Avoid guidance prevents awkward or repetitive drafts.',
        category: 'Preference',
        memoryPrefix: 'Avoid in messages: ',
        priority: 3
      };
    case 'language-style':
      return {
        id,
        question: `What language or style feels right for ${contact.name}?`,
        reason: 'Language guidance keeps drafts aligned with the relationship.',
        category: 'Preference',
        memoryPrefix: 'Preferred language/style: ',
        priority: 4
      };
  }
};

export const buildContactEnrichmentPlan = (state: AppState, contactId: string): ContactEnrichmentPlan | undefined => {
  const contact = state.contacts.find(item => item.id === contactId);
  if (!contact) {
    return undefined;
  }

  const memories = nonPrivateMemories(state, contactId);
  const gifts = state.gifts.filter(gift => gift.contactId === contactId);
  const events = state.events.filter(event => event.contactId === contactId);
  const sentMessages = state.messages.filter(message => message.contactId === contactId && message.status === 'Sent');
  const completedSignals: string[] = [];
  let score = 0;

  if (contact.preferredChannel) {
    score += 15;
    completedSignals.push('delivery channel');
  }
  if (hasPersonalContext(contact, memories)) {
    score += 25;
    completedSignals.push('relationship context');
  }
  if (hasMentionPreference(contact, memories)) {
    score += 20;
    completedSignals.push('mention preferences');
  }
  if (hasAvoidGuidance(contact, memories)) {
    score += 15;
    completedSignals.push('avoid guidance');
  }
  if (hasLanguageGuidance(contact, memories)) {
    score += 10;
    completedSignals.push('language guidance');
  }
  if (events.length > 0) {
    score += 10;
    completedSignals.push('relationship events');
  }
  if (gifts.length > 0 || sentMessages.length > 0) {
    score += 5;
    completedSignals.push(gifts.length > 0 ? 'gift history' : 'sent history');
  }

  const promptIds: ContactEnrichmentPromptId[] = [];
  if (!hasPersonalContext(contact, memories)) {
    promptIds.push('relationship-context');
  }
  if (!hasMentionPreference(contact, memories)) {
    promptIds.push('message-mention');
  }
  if (!hasAvoidGuidance(contact, memories)) {
    promptIds.push('message-avoid');
  }
  if (!hasLanguageGuidance(contact, memories)) {
    promptIds.push('language-style');
  }

  return {
    contactId,
    score: Math.min(100, score),
    label: score >= 75 ? 'Strong' : score >= 50 ? 'Growing' : 'Needs details',
    prompts: promptIds
      .map(id => promptFor(id, contact))
      .sort((a, b) => a.priority - b.priority)
      .slice(0, 3),
    completedSignals
  };
};

export const resolveContactEnrichmentPrompt = (
  state: AppState,
  contactId: string,
  promptId: ContactEnrichmentPromptId
) => buildContactEnrichmentPlan(state, contactId)?.prompts.find(prompt => prompt.id === promptId);

export const validateEnrichmentAnswer = (answer: string): EnrichmentAnswerValidation => {
  const value = answer.trim().replace(/\s+/g, ' ');
  if (value.length < 3) {
    return { ok: false, message: 'Write a short answer before saving enrichment.' };
  }
  if (value.length > 500) {
    return { ok: false, message: 'Keep enrichment answers under 500 characters.' };
  }
  return { ok: true, value };
};

export const buildEnrichmentMemoryBody = (prompt: ContactEnrichmentPrompt, answer: string) =>
  `${prompt.memoryPrefix}${answer}`.slice(0, 500);
