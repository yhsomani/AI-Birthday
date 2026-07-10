import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { createFixedClock, type CommandDependencies, type IdGenerator } from './commandMetadata';
import { createRelateReducer, relateReducer } from './relateReducer';
import type { AppState, Contact, ReminderPlan } from '../domain/types';
import {
  buildStandaloneContact,
  previewContactArchive,
  previewContactDelete,
  previewContactEdit,
  previewContactMerge,
  type StandaloneContactInput
} from '../domain/contactLifecycle';
import { allContactRoutes } from '../domain/contactIdentity';

const standaloneInput = (overrides: Partial<StandaloneContactInput> = {}): StandaloneContactInput => ({
  name: 'Asha Mehra',
  relationship: 'Friend',
  group: 'Friends',
  phone: '+919700000001',
  email: '',
  preferredChannel: 'SMS',
  language: 'English',
  notesSummary: '',
  ...overrides
});

const sequentialIds = (): IdGenerator => {
  let sequence = 0;
  return { nextId: kind => `${kind}-lifecycle-${++sequence}` };
};

const dependencies: CommandDependencies = {
  clock: createFixedClock('2026-07-10T09:30:00.000Z', '2026-07-10'),
  idGenerator: sequentialIds()
};

const reminderFor = (contactId: string, eventId: string): ReminderPlan => ({
  id: `reminder-${eventId}`,
  contactId,
  eventId,
  title: 'RelateAI reminder',
  body: 'Open RelateAI to review.',
  triggerAt: '2026-08-01T09:00:00.000Z'
});

describe('contact lifecycle contract', () => {
  it('creates a deterministic standalone local contact and never treats a name alone as identity', () => {
    const state = createTestState();
    const sameNameResult = buildStandaloneContact(state, standaloneInput(), 'contact-preview');

    assert.equal(sameNameResult.ok, true);

    const reducer = createRelateReducer(dependencies);
    const next = reducer(state, { type: 'addContact', input: standaloneInput() });
    const contact = next.contacts.find(item => item.id === 'contact-lifecycle-2');

    assert.ok(contact);
    assert.equal(contact.name, 'Asha Mehra');
    assert.equal(contact.sourceIdentities?.[0]?.provider, 'Local');
    assert.equal(contact.sourceIdentities?.[0]?.sourceId, contact.id);
    assert.equal(next.contacts.filter(item => item.name === 'Asha Mehra').length, 2);
    assert.equal(next.activity[0].createdAt, '2026-07-10T09:30:00.000Z');
  });

  it('stops exact route collisions for explicit review instead of auto-merging', () => {
    const state = createTestState();
    const result = buildStandaloneContact(
      state,
      standaloneInput({ name: 'Different Person', phone: state.contacts.find(item => item.id === 'c-asha')?.phone }),
      'contact-preview'
    );
    const next = relateReducer(state, {
      type: 'addContact',
      input: standaloneInput({
        name: 'Different Person',
        phone: state.contacts.find(item => item.id === 'c-asha')?.phone
      })
    });

    assert.equal(result.ok, false);
    if (!result.ok) assert.deepEqual(result.exactIdentityCandidateIds, ['c-asha']);
    assert.equal(next.contacts.length, state.contacts.length);
    assert.match(next.activity[0].detail, /exact phone or email identity/i);
  });

  it('requires a current impact token before editing and rechecks active drafts', () => {
    const state = createTestState();
    const input = {
      name: 'Rajesh Nair',
      relationship: 'Mentor',
      phone: '',
      email: 'rajesh@example.com',
      language: 'English' as const,
      notesSummary: 'Prefers concise professional notes.'
    };
    const preview = previewContactEdit(state, 'c-rajesh', input);
    assert.equal(preview.ok, true);
    if (!preview.ok) return;

    const rejected = relateReducer(state, {
      type: 'editContact',
      contactId: 'c-rajesh',
      input,
      confirmationToken: 'stale-token'
    });
    const accepted = relateReducer(state, {
      type: 'editContact',
      contactId: 'c-rajesh',
      input,
      confirmationToken: preview.confirmationToken
    });

    assert.equal(rejected.contacts.find(item => item.id === 'c-rajesh')?.relationship, 'Manager');
    assert.equal(accepted.contacts.find(item => item.id === 'c-rajesh')?.relationship, 'Mentor');
    assert.match(rejected.activity[0].title, /needs review/i);
  });

  it('surfaces exact identity collisions during edits without silently merging either contact', () => {
    const state = createTestState();
    const asha = state.contacts.find(contact => contact.id === 'c-asha');
    const mira = state.contacts.find(contact => contact.id === 'c-mira');
    assert.ok(asha?.phone);
    assert.ok(mira?.phone);
    const input = {
      name: mira.name,
      relationship: mira.relationship,
      phone: asha.phone,
      email: mira.email,
      language: mira.language,
      notesSummary: mira.notesSummary
    };
    const preview = previewContactEdit(state, mira.id, input);
    assert.equal(preview.ok, true);
    if (!preview.ok) return;
    assert.deepEqual(preview.exactIdentityCandidateIds, [asha.id]);

    const next = relateReducer(state, {
      type: 'editContact',
      contactId: mira.id,
      input,
      confirmationToken: preview.confirmationToken
    });
    const updated = next.contacts.find(contact => contact.id === mira.id);
    assert.ok(updated);

    assert.equal(next.contacts.length, state.contacts.length);
    assert.equal(updated.phone, mira.phone);
    assert.equal(
      allContactRoutes(updated).some(route => route.value === asha.phone),
      false
    );
    assert.match(next.activity[0].title, /identity review/i);
  });

  it('archives linked contacts with injected time and preserves every relationship record', () => {
    const base = createTestState();
    const state: AppState = {
      ...base,
      selectedContactId: 'c-asha',
      activeScreen: 'contactDetail',
      reminderPlans: [reminderFor('c-asha', 'e-asha-bday')],
      messages: base.messages.map(message =>
        message.id === 'msg-asha-bday'
          ? {
              ...message,
              status: 'Scheduled',
              approvedAt: '2026-07-09T09:00:00.000Z',
              approvalExpiresAt: '2026-07-10T09:00:00.000Z'
            }
          : message
      )
    };
    const preview = previewContactArchive(state, 'c-asha');
    assert.equal(preview.ok, true);
    if (!preview.ok) return;
    const reducer = createRelateReducer(dependencies);
    const next = reducer(state, {
      type: 'archiveContact',
      contactId: 'c-asha',
      confirmationToken: preview.confirmationToken
    });

    assert.equal(next.contacts.find(item => item.id === 'c-asha')?.archivedAt, '2026-07-10T09:30:00.000Z');
    assert.equal(next.events.length, state.events.length);
    assert.equal(next.memories.length, state.memories.length);
    assert.equal(next.gifts.length, state.gifts.length);
    assert.equal(next.messages.length, state.messages.length);
    assert.equal(
      next.reminderPlans.some(plan => plan.contactId === 'c-asha'),
      false
    );
    assert.equal(next.messages.find(message => message.id === 'msg-asha-bday')?.status, 'Needs review');
    assert.equal(next.activeScreen, 'contacts');
    assert.equal(next.selectedContactId, undefined);
  });

  it('blocks hard deletion when history exists and recommends archival', () => {
    const state = createTestState();
    const preview = previewContactDelete(state, 'c-asha');
    assert.equal(preview.ok, true);
    if (!preview.ok) return;
    assert.equal(preview.deletionAllowed, false);
    assert.equal(preview.recommendedAction, 'archive');

    const next = relateReducer(state, {
      type: 'deleteContact',
      contactId: 'c-asha',
      confirmationToken: preview.confirmationToken
    });

    assert.ok(next.contacts.some(contact => contact.id === 'c-asha'));
    assert.match(next.activity[0].title, /deletion blocked/i);
    assert.match(next.activity[0].detail, /archive/i);
  });

  it('deletes an isolated contact only after preview and removes active cascades without dangling references', () => {
    const base = createTestState();
    const source = base.contacts.find(contact => contact.id === 'c-mira');
    assert.ok(source);
    const isolated: Contact = {
      ...source,
      id: 'c-isolated',
      name: 'Isolated Contact',
      phone: '+919700000009',
      notesSummary: '',
      routes: undefined,
      sourceIdentities: [{ provider: 'Local', sourceId: 'c-isolated' }]
    };
    const event = { ...base.events[2], id: 'e-isolated', contactId: isolated.id };
    const message = {
      ...base.messages[1],
      id: 'msg-isolated',
      contactId: isolated.id,
      eventId: event.id,
      status: 'Draft' as const
    };
    const state: AppState = {
      ...base,
      contacts: [isolated, ...base.contacts],
      events: [event, ...base.events],
      messages: [message, ...base.messages],
      reminderPlans: [reminderFor(isolated.id, event.id)],
      activity: [
        {
          id: 'activity-isolated',
          type: 'Contact',
          title: 'Contact added',
          detail: 'Local contact added.',
          severity: 'Info',
          createdAt: '2026-07-09T09:00:00.000Z',
          contactId: isolated.id,
          messageId: message.id
        },
        ...base.activity
      ]
    };
    const preview = previewContactDelete(state, isolated.id);
    assert.equal(preview.ok, true);
    if (!preview.ok) return;
    assert.equal(preview.deletionAllowed, true);

    const next = relateReducer(state, {
      type: 'deleteContact',
      contactId: isolated.id,
      confirmationToken: preview.confirmationToken
    });

    assert.equal(
      next.contacts.some(contact => contact.id === isolated.id),
      false
    );
    assert.equal(
      next.events.some(item => item.contactId === isolated.id),
      false
    );
    assert.equal(
      next.messages.some(item => item.contactId === isolated.id),
      false
    );
    assert.equal(
      next.reminderPlans.some(item => item.contactId === isolated.id),
      false
    );
    const preservedActivity = next.activity.find(item => item.id === 'activity-isolated');
    assert.ok(preservedActivity);
    assert.equal(preservedActivity.contactId, undefined);
    assert.equal(preservedActivity.messageId, undefined);
  });

  it('merges only after explicit reviewed confirmation and preserves every linked aggregate', () => {
    const base = createTestState();
    const survivor = base.contacts.find(contact => contact.id === 'c-asha');
    assert.ok(survivor);
    const duplicate: Contact = {
      ...survivor,
      id: 'c-asha-duplicate',
      relationship: 'Cousin',
      sourceIdentities: [{ provider: 'Device contacts', sourceId: 'duplicate-source' }]
    };
    const event = { ...base.events[0], id: 'e-asha-duplicate', contactId: duplicate.id };
    const memory = { ...base.memories[0], id: 'm-asha-duplicate', contactId: duplicate.id };
    const gift = { ...base.gifts[0], id: 'g-asha-duplicate', contactId: duplicate.id };
    const message = {
      ...base.messages[0],
      id: 'msg-asha-duplicate',
      contactId: duplicate.id,
      eventId: event.id,
      status: 'Scheduled' as const,
      approvedAt: '2026-07-09T09:00:00.000Z'
    };
    const state: AppState = {
      ...base,
      contacts: [duplicate, ...base.contacts],
      events: [event, ...base.events],
      memories: [memory, ...base.memories],
      gifts: [gift, ...base.gifts],
      messages: [message, ...base.messages],
      reminderPlans: [reminderFor(duplicate.id, event.id)]
    };
    const preview = previewContactMerge(state, survivor.id, duplicate.id);
    assert.equal(preview.ok, true);
    if (!preview.ok) return;
    assert.equal(preview.exactIdentityMatch, true);
    assert.ok(preview.matchReasons.includes('phone'));

    const rejected = relateReducer(state, {
      type: 'mergeContacts',
      survivorContactId: survivor.id,
      mergedContactId: duplicate.id,
      confirmationToken: 'stale-token'
    });
    const accepted = relateReducer(state, {
      type: 'mergeContacts',
      survivorContactId: survivor.id,
      mergedContactId: duplicate.id,
      confirmationToken: preview.confirmationToken
    });

    assert.ok(rejected.contacts.some(contact => contact.id === duplicate.id));
    assert.equal(
      accepted.contacts.some(contact => contact.id === duplicate.id),
      false
    );
    assert.equal(accepted.events.find(item => item.id === event.id)?.contactId, survivor.id);
    assert.equal(accepted.memories.find(item => item.id === memory.id)?.contactId, survivor.id);
    assert.equal(accepted.gifts.find(item => item.id === gift.id)?.contactId, survivor.id);
    assert.equal(accepted.messages.find(item => item.id === message.id)?.contactId, survivor.id);
    assert.equal(accepted.messages.find(item => item.id === message.id)?.status, 'Needs review');
    assert.equal(accepted.reminderPlans.find(item => item.id === `reminder-${event.id}`)?.contactId, survivor.id);
    assert.ok(
      accepted.contacts
        .find(contact => contact.id === survivor.id)
        ?.sourceIdentities?.some(identity => identity.sourceId === 'duplicate-source')
    );
  });

  it('reports a same-name-only merge as non-exact and still requires an explicit token', () => {
    const base = createTestState();
    const original = base.contacts.find(contact => contact.id === 'c-mira');
    assert.ok(original);
    const sameName: Contact = {
      ...original,
      id: 'c-mira-same-name',
      phone: '+919700000020',
      email: undefined,
      routes: undefined,
      sourceIdentities: [{ provider: 'Local', sourceId: 'c-mira-same-name' }]
    };
    const state = { ...base, contacts: [sameName, ...base.contacts] };
    const preview = previewContactMerge(state, original.id, sameName.id);

    assert.equal(preview.ok, true);
    if (!preview.ok) return;
    assert.equal(preview.exactIdentityMatch, false);
    assert.deepEqual(preview.matchReasons, ['same-name']);
    const rejected = relateReducer(state, {
      type: 'mergeContacts',
      survivorContactId: original.id,
      mergedContactId: sameName.id,
      confirmationToken: 'not-reviewed'
    });
    assert.ok(rejected.contacts.some(contact => contact.id === sameName.id));
  });
});
