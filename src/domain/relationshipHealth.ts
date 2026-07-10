import { resolveContactPreferencesForContact } from './contactPreferences';
import { eventOccurrenceIso, localDateKey } from './occasionDates';
import type { AppState, Contact, RelationshipGroup } from './types';

export type RelationshipConfidence = 'Low' | 'Medium' | 'High';
export type RelationshipHealthLabel = 'Healthy' | 'Watch' | 'Needs attention';

export const relationshipGroupOptions: RelationshipGroup[] = ['Family', 'Friends', 'Close friends', 'Work', 'Other'];
export const checkInCadenceOptions = [14, 30, 45, 60, 90];

export interface RelationshipGroupSuggestion {
  group: RelationshipGroup;
  confidence: RelationshipConfidence;
  rationale: string;
}

export interface RelationshipHealthInsight {
  contactId: string;
  score: number;
  label: RelationshipHealthLabel;
  summary: string;
  reasons: string[];
  suggestion?: RelationshipGroupSuggestion;
  reviewNeeded: boolean;
}

const normalize = (value: string) => value.trim().toLowerCase();

const daysSince = (iso: string | undefined, now: Date) => {
  if (!iso) {
    return Number.POSITIVE_INFINITY;
  }
  return Math.max(0, Math.floor((now.getTime() - new Date(iso).getTime()) / (1000 * 60 * 60 * 24)));
};

const classifyFromText = (contact: Contact, text: string): RelationshipGroupSuggestion | undefined => {
  const combined = normalize(`${contact.name} ${contact.relationship} ${contact.notesSummary} ${text}`);
  if (/sister|brother|mother|father|mom|dad|parent|cousin|family|wife|husband|spouse/.test(combined)) {
    return {
      group: 'Family',
      confidence: /sister|brother|mother|father|mom|dad|wife|husband|spouse/.test(combined) ? 'High' : 'Medium',
      rationale: 'Family terms appear in the relationship or saved context.'
    };
  }
  if (/manager|colleague|client|boss|team|work|office|professional/.test(combined)) {
    return {
      group: 'Work',
      confidence: /manager|colleague|client|boss/.test(combined) ? 'High' : 'Medium',
      rationale: 'Work terms appear in the relationship or saved context.'
    };
  }
  if (/best friend|close friend|college friend|childhood friend/.test(combined)) {
    return {
      group: 'Close friends',
      confidence: 'High',
      rationale: 'Close-friend language appears in the relationship or saved context.'
    };
  }
  if (/friend|classmate|college|school/.test(combined)) {
    return {
      group: 'Friends',
      confidence: 'Medium',
      rationale: 'Friendship terms appear in the relationship or saved context.'
    };
  }
  return undefined;
};

export const buildRelationshipHealthInsight = (
  state: AppState,
  contactId: string,
  now: Date = new Date()
): RelationshipHealthInsight | undefined => {
  const contact = state.contacts.find(item => item.id === contactId);
  if (!contact) {
    return undefined;
  }

  const nonPrivateMemoryText = state.memories
    .filter(memory => memory.contactId === contact.id && memory.category !== 'Private')
    .map(memory => memory.body)
    .join(' ');
  const sentCount = state.messages.filter(message => message.contactId === contact.id && message.status === 'Sent').length;
  const todayKey = localDateKey(now) ?? now.toISOString().slice(0, 10);
  const upcomingEventCount = state.events.filter(event => {
    const occurrence = event.contactId === contact.id ? eventOccurrenceIso(event, now) : undefined;
    return Boolean(occurrence && occurrence.slice(0, 10) >= todayKey);
  }).length;
  const giftCount = state.gifts.filter(gift => gift.contactId === contact.id).length;
  const quietDays = daysSince(contact.lastContactedAt, now);
  const snoozedUntil = contact.checkInSnoozedUntil ? new Date(contact.checkInSnoozedUntil) : undefined;
  const isSnoozed = Boolean(snoozedUntil && !Number.isNaN(snoozedUntil.getTime()) && snoozedUntil.getTime() > now.getTime());
  const preferences = resolveContactPreferencesForContact(state.settings, contact);
  const missingChannel =
    (preferences.preferredChannel === 'SMS' || preferences.preferredChannel === 'WhatsApp') && !contact.phone
      ? true
      : preferences.preferredChannel === 'Email' && !contact.email;

  const reasons: string[] = [];
  if (contact.isVip) {
    reasons.push('VIP contact is prioritized in reminders and review queues.');
  }
  if (contact.dnd) {
    reasons.push('Do-not-disturb is on, so automation should stay off for this contact.');
  }
  if (Number.isFinite(quietDays)) {
    reasons.push(`${quietDays} day(s) since last contact; cadence is ${preferences.checkInCadenceDays} day(s).`);
  } else {
    reasons.push('No recent contact date is available.');
  }
  if (isSnoozed) {
    reasons.push(`Check-in reminder is snoozed until ${contact.checkInSnoozedUntil}; last-contact history is unchanged.`);
  }
  if (upcomingEventCount > 0) {
    reasons.push(`${upcomingEventCount} upcoming relationship event(s) are known.`);
  } else {
    reasons.push('No upcoming relationship event is known.');
  }
  if (sentCount > 0) {
    reasons.push(`${sentCount} sent RelateAI message(s) are in history.`);
  }
  if (giftCount > 0) {
    reasons.push(`${giftCount} gift record(s) add relationship context.`);
  }
  if (missingChannel) {
    reasons.push('Preferred channel is missing required contact details.');
  }
  if (nonPrivateMemoryText.trim().length === 0) {
    reasons.push('No non-private memory context has been saved yet.');
  }

  const inferred = classifyFromText(contact, nonPrivateMemoryText);
  const suggestion = inferred && inferred.group !== contact.group ? inferred : undefined;
  const label: RelationshipHealthLabel =
    contact.healthScore >= 70 ? 'Healthy' : contact.healthScore >= 50 ? 'Watch' : 'Needs attention';
  const reviewNeeded = Boolean(suggestion) || contact.healthScore < 60 || missingChannel || nonPrivateMemoryText.trim().length === 0;

  return {
    contactId: contact.id,
    score: contact.healthScore,
    label,
    summary:
      label === 'Healthy'
        ? 'Relationship health looks steady.'
        : label === 'Watch'
          ? 'Relationship health could use a small follow-up.'
          : 'Relationship health needs attention before relying on automation.',
    reasons,
    suggestion,
    reviewNeeded
  };
};
