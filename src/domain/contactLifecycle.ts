import { validateContactEssentials, type ContactEssentialsInput } from './contactEssentials';
import { allContactRoutes, normalizeEmailRoute, normalizePhoneRoute } from './contactIdentity';
import { lifecycleConfirmationToken } from './lifecycleConfirmation';
import { relationshipGroupOptions } from './relationshipHealth';
import type {
  AppState,
  Contact,
  ContactRoute,
  ContactSourceIdentity,
  MessageChannel,
  MessageDraft,
  RelationshipGroup
} from './types';

export interface StandaloneContactInput extends ContactEssentialsInput {
  group: RelationshipGroup;
  preferredChannel: MessageChannel;
}

export interface ContactLifecycleImpact {
  eventCount: number;
  reminderCount: number;
  activeMessageCount: number;
  historyMessageCount: number;
  memoryCount: number;
  giftCount: number;
  linkedActivityCount: number;
}

export type NormalizedContactEssentials = Pick<
  Contact,
  'name' | 'relationship' | 'relationshipSubtype' | 'jobTitle' | 'phone' | 'email' | 'language' | 'notesSummary'
>;

export type ContactLifecycleFailure = {
  ok: false;
  reason: string;
};

export type ContactEditPreview =
  | ContactLifecycleFailure
  | {
      ok: true;
      contactId: string;
      normalized: NormalizedContactEssentials;
      changedFields: (keyof NormalizedContactEssentials)[];
      exactIdentityCandidateIds: string[];
      impact: ContactLifecycleImpact;
      requiresConfirmation: true;
      confirmationToken: string;
    };

export type ContactRemovalPreview =
  | ContactLifecycleFailure
  | {
      ok: true;
      contactId: string;
      operation: 'archive' | 'delete';
      impact: ContactLifecycleImpact;
      relationshipHistoryCount: number;
      deletionAllowed: boolean;
      recommendedAction: 'archive' | 'delete';
      requiresConfirmation: true;
      confirmationToken: string;
    };

export type ContactMergeMatchReason = 'source-identity' | 'phone' | 'email' | 'same-name';

export type ContactMergePreview =
  | ContactLifecycleFailure
  | {
      ok: true;
      survivorContactId: string;
      mergedContactId: string;
      matchReasons: ContactMergeMatchReason[];
      exactIdentityMatch: boolean;
      conflictingFields: ('name' | 'relationship' | 'group' | 'preferredChannel' | 'language')[];
      impact: ContactLifecycleImpact;
      requiresConfirmation: true;
      confirmationToken: string;
    };

export type StandaloneContactBuildResult =
  | {
      ok: true;
      contact: Contact;
    }
  | (ContactLifecycleFailure & {
      exactIdentityCandidateIds?: string[];
    });

const activeMessageStatuses: MessageDraft['status'][] = ['Needs review', 'Draft', 'Scheduled', 'Blocked'];
const historyMessageStatuses: MessageDraft['status'][] = [
  'Sent',
  'Failed',
  'Delivery pending',
  'Delivery unknown',
  'Rejected'
];

const normalizedEssentials = (contact: Contact): NormalizedContactEssentials => ({
  name: contact.name,
  relationship: contact.relationship,
  relationshipSubtype: contact.relationshipSubtype,
  jobTitle: contact.jobTitle,
  phone: contact.phone,
  email: contact.email,
  language: contact.language,
  notesSummary: contact.notesSummary
});

const contactImpactRevision = (state: AppState, contactId: string) => ({
  contact: state.contacts.find(contact => contact.id === contactId),
  events: state.events
    .filter(event => event.contactId === contactId)
    .map(event => ({ id: event.id, type: event.type, date: event.date, verified: event.verified })),
  reminders: state.reminderPlans
    .filter(plan => plan.contactId === contactId)
    .map(plan => ({ id: plan.id, eventId: plan.eventId, triggerAt: plan.triggerAt })),
  messages: state.messages
    .filter(message => message.contactId === contactId)
    .map(message => ({ id: message.id, eventId: message.eventId, status: message.status, channel: message.channel })),
  memoryIds: state.memories.filter(memory => memory.contactId === contactId).map(memory => memory.id),
  giftIds: state.gifts.filter(gift => gift.contactId === contactId).map(gift => gift.id),
  activityIds: state.activity.filter(item => item.contactId === contactId).map(item => item.id)
});

export const contactLifecycleImpact = (state: AppState, contactId: string): ContactLifecycleImpact => ({
  eventCount: state.events.filter(event => event.contactId === contactId).length,
  reminderCount: state.reminderPlans.filter(plan => plan.contactId === contactId).length,
  activeMessageCount: state.messages.filter(
    message => message.contactId === contactId && activeMessageStatuses.includes(message.status)
  ).length,
  historyMessageCount: state.messages.filter(
    message => message.contactId === contactId && historyMessageStatuses.includes(message.status)
  ).length,
  memoryCount: state.memories.filter(memory => memory.contactId === contactId).length,
  giftCount: state.gifts.filter(gift => gift.contactId === contactId).length,
  linkedActivityCount: state.activity.filter(item => item.contactId === contactId).length
});

const exactRouteCandidateIds = (state: AppState, contact: Contact, excludeContactId?: string): string[] => {
  const routeKeys = new Set(allContactRoutes(contact).map(route => `${route.type}:${route.value}`));
  if (routeKeys.size === 0) return [];
  return state.contacts
    .filter(
      candidate =>
        candidate.id !== excludeContactId &&
        allContactRoutes(candidate).some(route => routeKeys.has(`${route.type}:${route.value}`))
    )
    .map(candidate => candidate.id);
};

export const buildStandaloneContact = (
  state: AppState,
  input: StandaloneContactInput,
  contactId: string
): StandaloneContactBuildResult => {
  if (!relationshipGroupOptions.includes(input.group)) {
    return { ok: false, reason: 'Choose a supported relationship group.' };
  }
  const validation = validateContactEssentials(input, input.preferredChannel);
  if (!validation.ok) {
    return { ok: false, reason: validation.message };
  }
  const groupDefaults = state.settings.groupDefaults[input.group];
  const contact: Contact = {
    id: contactId,
    ...validation.value,
    group: input.group,
    preferredChannel: input.preferredChannel,
    tone: [...groupDefaults.tone],
    healthScore: 40,
    isVip: false,
    dnd: false,
    checkInCadenceDays: groupDefaults.checkInCadenceDays,
    quietHoursBehavior: 'Defer',
    skipAuto: false,
    preferenceOverrides: {
      preferredChannel: input.preferredChannel
    },
    annualGiftBudget: 0,
    sourceIdentities: [{ provider: 'Local', sourceId: contactId }]
  };
  contact.routes = allContactRoutes(contact);
  const candidateIds = exactRouteCandidateIds(state, contact);
  if (candidateIds.length > 0) {
    return {
      ok: false,
      reason:
        'An exact phone or email identity already exists. Review the existing contact before creating or merging.',
      exactIdentityCandidateIds: candidateIds
    };
  }
  return { ok: true, contact };
};

export const previewContactEdit = (
  state: AppState,
  contactId: string,
  input: ContactEssentialsInput
): ContactEditPreview => {
  const contact = state.contacts.find(item => item.id === contactId);
  if (!contact) return { ok: false, reason: 'Contact could not be found.' };
  if (contact.archivedAt) return { ok: false, reason: 'Restore the archived contact before editing it.' };
  const validation = validateContactEssentials(input, contact.preferredChannel);
  if (!validation.ok) return { ok: false, reason: validation.message };
  const current = normalizedEssentials(contact);
  const changedFields = (Object.keys(validation.value) as (keyof typeof current)[]).filter(
    field => current[field] !== validation.value[field]
  );
  if (changedFields.length === 0) return { ok: false, reason: 'No contact changes to save.' };
  const impact = contactLifecycleImpact(state, contactId);
  const exactIdentityCandidateIds = exactRouteCandidateIds(
    state,
    { ...contact, ...validation.value, routes: undefined },
    contactId
  );
  const revision = {
    ...contactImpactRevision(state, contactId),
    normalized: validation.value,
    changedFields,
    exactIdentityCandidateIds
  };
  return {
    ok: true,
    contactId,
    normalized: validation.value,
    changedFields,
    exactIdentityCandidateIds,
    impact,
    requiresConfirmation: true,
    confirmationToken: lifecycleConfirmationToken('edit-contact', revision)
  };
};

const previewContactRemoval = (
  state: AppState,
  contactId: string,
  operation: 'archive' | 'delete'
): ContactRemovalPreview => {
  const contact = state.contacts.find(item => item.id === contactId);
  if (!contact) return { ok: false, reason: 'Contact could not be found.' };
  if (operation === 'archive' && contact.archivedAt) {
    return { ok: false, reason: 'Contact is already archived.' };
  }
  const impact = contactLifecycleImpact(state, contactId);
  const relationshipHistoryCount = impact.historyMessageCount + impact.memoryCount + impact.giftCount;
  const deletionAllowed = relationshipHistoryCount === 0;
  return {
    ok: true,
    contactId,
    operation,
    impact,
    relationshipHistoryCount,
    deletionAllowed,
    recommendedAction: deletionAllowed ? operation : 'archive',
    requiresConfirmation: true,
    confirmationToken: lifecycleConfirmationToken(`${operation}-contact`, contactImpactRevision(state, contactId))
  };
};

export const previewContactArchive = (state: AppState, contactId: string): ContactRemovalPreview =>
  previewContactRemoval(state, contactId, 'archive');

export const previewContactDelete = (state: AppState, contactId: string): ContactRemovalPreview =>
  previewContactRemoval(state, contactId, 'delete');

const routeMatchReasons = (left: Contact, right: Contact): ContactMergeMatchReason[] => {
  const leftRoutes = new Set(allContactRoutes(left).map(route => `${route.type}:${route.value}`));
  const rightRoutes = allContactRoutes(right);
  const reasons: ContactMergeMatchReason[] = [];
  if (rightRoutes.some(route => route.type === 'Phone' && leftRoutes.has(`Phone:${route.value}`)))
    reasons.push('phone');
  if (rightRoutes.some(route => route.type === 'Email' && leftRoutes.has(`Email:${route.value}`)))
    reasons.push('email');
  const leftSources = new Set((left.sourceIdentities ?? []).map(item => `${item.provider}:${item.sourceId}`));
  if ((right.sourceIdentities ?? []).some(item => leftSources.has(`${item.provider}:${item.sourceId}`))) {
    reasons.push('source-identity');
  }
  if (left.name.trim().toLocaleLowerCase('en-IN') === right.name.trim().toLocaleLowerCase('en-IN')) {
    reasons.push('same-name');
  }
  return reasons;
};

export const previewContactMerge = (
  state: AppState,
  survivorContactId: string,
  mergedContactId: string
): ContactMergePreview => {
  if (survivorContactId === mergedContactId) {
    return { ok: false, reason: 'Choose two different contacts to merge.' };
  }
  const survivor = state.contacts.find(contact => contact.id === survivorContactId);
  const merged = state.contacts.find(contact => contact.id === mergedContactId);
  if (!survivor || !merged) return { ok: false, reason: 'Both contacts must still exist before merging.' };
  if (survivor.archivedAt) return { ok: false, reason: 'Restore the surviving contact before merging.' };
  const matchReasons = routeMatchReasons(survivor, merged);
  const exactIdentityMatch = matchReasons.some(reason => reason !== 'same-name');
  const conflictingFields = (['name', 'relationship', 'group', 'preferredChannel', 'language'] as const).filter(
    field => survivor[field] !== merged[field]
  );
  const impact = contactLifecycleImpact(state, mergedContactId);
  const revision = {
    survivor: contactImpactRevision(state, survivorContactId),
    merged: contactImpactRevision(state, mergedContactId),
    matchReasons,
    conflictingFields
  };
  return {
    ok: true,
    survivorContactId,
    mergedContactId,
    matchReasons,
    exactIdentityMatch,
    conflictingFields,
    impact,
    requiresConfirmation: true,
    confirmationToken: lifecycleConfirmationToken('merge-contacts', revision)
  };
};

const clearApproval = (message: MessageDraft): MessageDraft => {
  const { approvedAt: _approvedAt, approvalExpiresAt: _approvalExpiresAt, ...remaining } = message;
  return remaining;
};

const reviewMessage = (message: MessageDraft, reason: string): MessageDraft =>
  activeMessageStatuses.includes(message.status)
    ? {
        ...clearApproval(message),
        status: 'Needs review',
        readiness: 'Review after contact lifecycle change',
        lastError: reason
      }
    : message;

export const applyContactEdit = (
  state: AppState,
  contactId: string,
  normalized: NormalizedContactEssentials
): AppState => {
  const current = state.contacts.find(contact => contact.id === contactId);
  if (!current) return state;
  const previousPhone = normalizePhoneRoute(current.phone);
  const previousEmail = normalizeEmailRoute(current.email);
  const nextPhone = normalizePhoneRoute(normalized.phone);
  const nextEmail = normalizeEmailRoute(normalized.email);
  const retainedRoutes = (current.routes ?? []).filter(route => {
    const value = route.type === 'Phone' ? normalizePhoneRoute(route.value) : normalizeEmailRoute(route.value);
    return route.type === 'Phone' ? value !== previousPhone : value !== previousEmail;
  });
  const routes = allContactRoutes({
    ...current,
    ...normalized,
    routes: retainedRoutes
  }).map(route => ({
    ...route,
    primary: route.type === 'Phone' ? route.value === nextPhone : route.value === nextEmail
  }));
  return {
    ...state,
    contacts: state.contacts.map(contact =>
      contact.id === contactId ? { ...contact, ...normalized, routes } : contact
    ),
    messages: state.messages.map(message =>
      message.contactId === contactId
        ? reviewMessage(message, 'Contact details changed. Review this message before scheduling or sending.')
        : message
    )
  };
};

export const applyContactArchive = (state: AppState, contactId: string, archivedAt: string): AppState => ({
  ...state,
  activeScreen: state.selectedContactId === contactId ? 'contacts' : state.activeScreen,
  selectedContactId: state.selectedContactId === contactId ? undefined : state.selectedContactId,
  contacts: state.contacts.map(contact => (contact.id === contactId ? { ...contact, archivedAt } : contact)),
  reminderPlans: state.reminderPlans.filter(plan => plan.contactId !== contactId),
  messages: state.messages.map(message =>
    message.contactId === contactId
      ? reviewMessage(message, 'Contact was archived. Restore the contact before scheduling or sending.')
      : message
  )
});

export const applyContactRestore = (state: AppState, contactId: string): AppState => ({
  ...state,
  contacts: state.contacts.map(contact => {
    if (contact.id !== contactId) return contact;
    const { archivedAt: _archivedAt, ...restored } = contact;
    return restored;
  })
});

export const applyContactDelete = (state: AppState, contactId: string): AppState => {
  const eventIds = new Set(state.events.filter(event => event.contactId === contactId).map(event => event.id));
  const removedMessageIds = new Set(
    state.messages.filter(message => message.contactId === contactId).map(message => message.id)
  );
  return {
    ...state,
    activeScreen: state.selectedContactId === contactId ? 'contacts' : state.activeScreen,
    selectedContactId: state.selectedContactId === contactId ? undefined : state.selectedContactId,
    selectedMessageId:
      state.selectedMessageId && removedMessageIds.has(state.selectedMessageId) ? undefined : state.selectedMessageId,
    contacts: state.contacts.filter(contact => contact.id !== contactId),
    events: state.events.filter(event => event.contactId !== contactId),
    memories: state.memories.filter(memory => memory.contactId !== contactId),
    gifts: state.gifts.filter(gift => gift.contactId !== contactId),
    reminderPlans: state.reminderPlans.filter(plan => plan.contactId !== contactId && !eventIds.has(plan.eventId)),
    messages: state.messages.filter(message => message.contactId !== contactId),
    activity: state.activity.map(item => {
      if (item.contactId !== contactId && (!item.messageId || !removedMessageIds.has(item.messageId))) return item;
      const { contactId: _contactId, messageId: _messageId, ...detached } = item;
      return detached;
    })
  };
};

const mergeRoutes = (survivor: Contact, merged: Contact): ContactRoute[] => {
  const routes = allContactRoutes({
    ...survivor,
    routes: [...allContactRoutes(survivor), ...allContactRoutes(merged)]
  });
  const survivorPhone = normalizePhoneRoute(survivor.phone);
  const survivorEmail = normalizeEmailRoute(survivor.email);
  const selectedPrimary = {
    Phone: survivorPhone ?? routes.find(route => route.type === 'Phone')?.value,
    Email: survivorEmail ?? routes.find(route => route.type === 'Email')?.value
  };
  return routes.map(route => ({
    ...route,
    primary: route.value === selectedPrimary[route.type]
  }));
};

const mergeSources = (survivor: Contact, merged: Contact): ContactSourceIdentity[] => {
  const sources = new Map<string, ContactSourceIdentity>();
  for (const source of [...(survivor.sourceIdentities ?? []), ...(merged.sourceIdentities ?? [])]) {
    sources.set(`${source.provider}:${source.sourceId}`, source);
  }
  return [...sources.values()];
};

export const applyContactMerge = (state: AppState, survivorContactId: string, mergedContactId: string): AppState => {
  const survivor = state.contacts.find(contact => contact.id === survivorContactId);
  const merged = state.contacts.find(contact => contact.id === mergedContactId);
  if (!survivor || !merged) return state;
  const routes = mergeRoutes(survivor, merged);
  const mergedSurvivor: Contact = {
    ...survivor,
    phone: survivor.phone ?? routes.find(route => route.type === 'Phone' && route.primary)?.value,
    email: survivor.email ?? routes.find(route => route.type === 'Email' && route.primary)?.value,
    routes,
    sourceIdentities: mergeSources(survivor, merged)
  };
  return {
    ...state,
    selectedContactId: state.selectedContactId === mergedContactId ? survivorContactId : state.selectedContactId,
    contacts: state.contacts
      .filter(contact => contact.id !== mergedContactId)
      .map(contact => (contact.id === survivorContactId ? mergedSurvivor : contact)),
    events: state.events.map(event =>
      event.contactId === mergedContactId ? { ...event, contactId: survivorContactId } : event
    ),
    memories: state.memories.map(memory =>
      memory.contactId === mergedContactId ? { ...memory, contactId: survivorContactId } : memory
    ),
    gifts: state.gifts.map(gift =>
      gift.contactId === mergedContactId ? { ...gift, contactId: survivorContactId } : gift
    ),
    messages: state.messages.map(message =>
      message.contactId === mergedContactId
        ? reviewMessage(
            { ...message, contactId: survivorContactId },
            'Contacts were merged. Review the recipient, route, and message before scheduling or sending.'
          )
        : message
    ),
    reminderPlans: state.reminderPlans.map(plan =>
      plan.contactId === mergedContactId ? { ...plan, contactId: survivorContactId } : plan
    ),
    activity: state.activity.map(item =>
      item.contactId === mergedContactId ? { ...item, contactId: survivorContactId } : item
    )
  };
};
