import { resolveContactPreferencesForContact } from './contactPreferences';
import type { AppState, Contact, MemoryCategory, MemoryNote } from './types';

export type ContactEnrichmentPromptId = 'relationship-context' | 'message-mention' | 'message-avoid' | 'language-style';

export interface ContactEnrichmentPrompt {
  id: ContactEnrichmentPromptId;
  question: string;
  reason: string;
  category: MemoryCategory;
  memoryPrefix: string;
  priority: number;
  improvesSignal: string;
}

export interface ContactEnrichmentPlan {
  contactId: string;
  score: number;
  label: 'Needs details' | 'Growing' | 'Strong';
  prompts: ContactEnrichmentPrompt[];
  completedSignals: string[];
  missingSignals: string[];
  summary: string;
}

export type EnrichmentAnswerValidation = { ok: true; value: string } | { ok: false; message: string };

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

const hasAvoidGuidance = (contact: Contact, memories: MemoryNote[], tone: Contact['tone']) =>
  tone.includes('No emoji') ||
  includesAny(contactContextText(contact, memories), /\b(avoid|do not|don't|never mention|no emoji|should not)\b/i);

const hasLanguageGuidance = (contact: Contact, memories: MemoryNote[]) =>
  contact.language !== 'English' ||
  includesAny(contactContextText(contact, memories), /\b(language|hindi|hinglish|english)\b/i);

const promptFor = (id: ContactEnrichmentPromptId, contact: Contact): ContactEnrichmentPrompt => {
  switch (id) {
    case 'relationship-context':
      return {
        id,
        question: `How do you know ${contact.name}?`,
        reason: 'Relationship context helps future messages sound specific.',
        category: 'General',
        memoryPrefix: 'Relationship context: ',
        priority: 1,
        improvesSignal: 'relationship context'
      };
    case 'message-mention':
      return {
        id,
        question: `What should a message to ${contact.name} mention?`,
        reason: 'Mention preferences reduce generic drafts.',
        category: 'Preference',
        memoryPrefix: 'Message should mention: ',
        priority: 2,
        improvesSignal: 'mention preferences'
      };
    case 'message-avoid':
      return {
        id,
        question: `What should messages to ${contact.name} avoid?`,
        reason: 'Avoid guidance prevents awkward or repetitive drafts.',
        category: 'Preference',
        memoryPrefix: 'Avoid in messages: ',
        priority: 3,
        improvesSignal: 'avoid guidance'
      };
    case 'language-style':
      return {
        id,
        question: `What language or style feels right for ${contact.name}?`,
        reason: 'Language guidance keeps drafts aligned with the relationship.',
        category: 'Preference',
        memoryPrefix: 'Preferred language/style: ',
        priority: 4,
        improvesSignal: 'language guidance'
      };
  }
};

const labelForScore = (score: number): ContactEnrichmentPlan['label'] =>
  score >= 75 ? 'Strong' : score >= 50 ? 'Growing' : 'Needs details';

const summaryFor = (score: number, completedSignals: string[], missingSignals: string[]) => {
  if (missingSignals.length === 0) {
    return `Personalization is strong at ${score}%. Future drafts have the core relationship context they need.`;
  }

  if (completedSignals.length === 0) {
    return `Personalization is ${score}%. Add ${missingSignals[0]} first to make future drafts less generic.`;
  }

  return `Personalization is ${score}%. Next missing detail: ${missingSignals[0]}.`;
};

type ContactEnrichmentFacts = Readonly<{
  memories: MemoryNote[];
  hasGift: boolean;
  hasEvent: boolean;
  hasSentMessage: boolean;
}>;

const buildContactEnrichmentPlanFromFacts = (
  state: Pick<AppState, 'settings'>,
  contact: Contact,
  facts: ContactEnrichmentFacts
): ContactEnrichmentPlan => {
  const { memories, hasGift, hasEvent, hasSentMessage } = facts;
  const preferences = resolveContactPreferencesForContact(state.settings, contact);
  const completedSignals: string[] = [];
  let score = 0;

  if (preferences.preferredChannel) {
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
  if (hasAvoidGuidance(contact, memories, preferences.tone)) {
    score += 15;
    completedSignals.push('avoid guidance');
  }
  if (hasLanguageGuidance(contact, memories)) {
    score += 10;
    completedSignals.push('language guidance');
  }
  if (hasEvent) {
    score += 10;
    completedSignals.push('relationship events');
  }
  if (hasGift || hasSentMessage) {
    score += 5;
    completedSignals.push(hasGift ? 'gift history' : 'sent history');
  }

  const promptIds: ContactEnrichmentPromptId[] = [];
  const missingSignals: string[] = [];
  if (!hasPersonalContext(contact, memories)) {
    promptIds.push('relationship-context');
    missingSignals.push('relationship context');
  }
  if (!hasMentionPreference(contact, memories)) {
    promptIds.push('message-mention');
    missingSignals.push('mention preferences');
  }
  if (!hasAvoidGuidance(contact, memories, preferences.tone)) {
    promptIds.push('message-avoid');
    missingSignals.push('avoid guidance');
  }
  if (!hasLanguageGuidance(contact, memories)) {
    promptIds.push('language-style');
    missingSignals.push('language guidance');
  }

  const finalScore = Math.min(100, score);

  return {
    contactId: contact.id,
    score: finalScore,
    label: labelForScore(finalScore),
    prompts: promptIds.map(id => promptFor(id, contact)).sort((a, b) => a.priority - b.priority),
    completedSignals,
    missingSignals,
    summary: summaryFor(finalScore, completedSignals, missingSignals)
  };
};

export const buildContactEnrichmentPlan = (state: AppState, contactId: string): ContactEnrichmentPlan | undefined => {
  const contact = state.contacts.find(item => item.id === contactId);
  if (!contact) return undefined;
  return buildContactEnrichmentPlanFromFacts(state, contact, {
    memories: nonPrivateMemories(state, contactId),
    hasGift: state.gifts.some(gift => gift.contactId === contactId),
    hasEvent: state.events.some(event => event.contactId === contactId),
    hasSentMessage: state.messages.some(message => message.contactId === contactId && message.status === 'Sent')
  });
};

/** Builds all enrichment scores in one indexed pass for list/report workloads. */
export const buildContactEnrichmentPlans = (state: AppState): ReadonlyMap<string, ContactEnrichmentPlan> => {
  const memoriesByContact = new Map<string, MemoryNote[]>();
  for (const memory of state.memories) {
    if (memory.category === 'Private') continue;
    const values = memoriesByContact.get(memory.contactId) ?? [];
    values.push(memory);
    memoriesByContact.set(memory.contactId, values);
  }
  const giftContacts = new Set(state.gifts.map(gift => gift.contactId));
  const eventContacts = new Set(state.events.map(event => event.contactId));
  const sentMessageContacts = new Set(
    state.messages.filter(message => message.status === 'Sent').map(message => message.contactId)
  );
  return new Map(
    state.contacts.map(contact => [
      contact.id,
      buildContactEnrichmentPlanFromFacts(state, contact, {
        memories: memoriesByContact.get(contact.id) ?? [],
        hasGift: giftContacts.has(contact.id),
        hasEvent: eventContacts.has(contact.id),
        hasSentMessage: sentMessageContacts.has(contact.id)
      })
    ])
  );
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
