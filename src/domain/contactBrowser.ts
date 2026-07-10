import { buildContactEnrichmentPlan } from './contactEnrichment';
import { resolveContactPreferencesForContact } from './contactPreferences';
import type { AppState, Contact, RelationshipEvent, RelationshipGroup } from './types';
import { localDateKey, materializeEventOccurrence } from './occasionDates';

export type ContactGroupFilter = 'All' | RelationshipGroup;
export type ContactQualityFilter = 'All' | 'VIP' | 'Missing event' | 'Missing channel' | 'Low health' | 'Needs details';
export type ContactSort = 'Name' | 'Health priority' | 'Next event';

export interface ContactBrowserRow {
  contact: Contact;
  nextEvent?: RelationshipEvent;
  qualityLabels: string[];
}

export interface ContactBrowserOptions {
  query: string;
  group: ContactGroupFilter;
  quality: ContactQualityFilter;
  sort: ContactSort;
}

export const contactGroupFilters: ContactGroupFilter[] = ['All', 'Family', 'Friends', 'Work', 'Close friends', 'Other'];
export const contactQualityFilters: ContactQualityFilter[] = [
  'All',
  'VIP',
  'Missing event',
  'Missing channel',
  'Low health',
  'Needs details'
];
export const contactSorts: ContactSort[] = ['Name', 'Health priority', 'Next event'];

const missingChannelDetails = (contact: Contact, preferredChannel = contact.preferredChannel) =>
  (preferredChannel === 'SMS' || preferredChannel === 'WhatsApp') && !contact.phone
    ? true
    : preferredChannel === 'Email' && !contact.email;

const nextEventsByContact = (events: RelationshipEvent[], now: Date) => {
  const nowKey = localDateKey(now) ?? now.toISOString().slice(0, 10);
  const byContact = new Map<string, RelationshipEvent>();
  events
    .map(event => materializeEventOccurrence(event, now))
    .filter(event => event.date.slice(0, 10) >= nowKey)
    .sort((a, b) => a.date.localeCompare(b.date))
    .forEach(event => {
      if (!byContact.has(event.contactId)) {
        byContact.set(event.contactId, event);
      }
    });
  return byContact;
};

const labelsFor = (state: AppState, contact: Contact, nextEvent: RelationshipEvent | undefined) => {
  const enrichmentPlan = buildContactEnrichmentPlan(state, contact.id);
  const preferences = resolveContactPreferencesForContact(state.settings, contact);
  const labels: string[] = [];
  if (contact.isVip) {
    labels.push('VIP');
  }
  if (!nextEvent) {
    labels.push('Missing event');
  }
  if (missingChannelDetails(contact, preferences.preferredChannel)) {
    labels.push('Missing channel');
  }
  if (contact.healthScore < 60) {
    labels.push('Low health');
  }
  if ((enrichmentPlan?.score ?? 0) < 50) {
    labels.push('Needs details');
  }
  return labels;
};

const matchesQuery = (contact: Contact, query: string) => {
  const normalized = query.trim().toLowerCase();
  if (!normalized) {
    return true;
  }
  const textMatch = [contact.name, contact.relationship, contact.group, contact.notesSummary]
    .join(' ')
    .toLowerCase()
    .includes(normalized);
  if (textMatch) return true;
  const normalizedRouteQuery = normalized.replace(/[^a-z0-9+@.]/g, '');
  if (!normalizedRouteQuery) return false;
  return [contact.phone, contact.email, ...(contact.routes?.map(route => route.value) ?? [])]
    .filter((value): value is string => Boolean(value))
    .some(value =>
      value
        .toLowerCase()
        .replace(/[^a-z0-9+@.]/g, '')
        .includes(normalizedRouteQuery)
    );
};

const matchesQuality = (row: ContactBrowserRow, quality: ContactQualityFilter) =>
  quality === 'All' || row.qualityLabels.includes(quality);

export const buildContactBrowserRows = (
  state: AppState,
  options: ContactBrowserOptions,
  now: Date = new Date()
): ContactBrowserRow[] => {
  const nextEventByContact = nextEventsByContact(state.events, now);

  return state.contacts
    .filter(contact => !contact.archivedAt)
    .map(contact => {
      const nextEvent = nextEventByContact.get(contact.id);
      return {
        contact,
        nextEvent,
        qualityLabels: labelsFor(state, contact, nextEvent)
      };
    })
    .filter(row => matchesQuery(row.contact, options.query))
    .filter(row => options.group === 'All' || row.contact.group === options.group)
    .filter(row => matchesQuality(row, options.quality))
    .sort((a, b) => {
      if (options.sort === 'Health priority') {
        return a.contact.healthScore - b.contact.healthScore || a.contact.name.localeCompare(b.contact.name);
      }
      if (options.sort === 'Next event') {
        return (
          (a.nextEvent?.date ?? '9999').localeCompare(b.nextEvent?.date ?? '9999') ||
          a.contact.name.localeCompare(b.contact.name)
        );
      }
      return a.contact.name.localeCompare(b.contact.name);
    });
};
