import { buildContactEnrichmentPlans } from './contactEnrichment';
import { resolveContactPreferencesForContact } from './contactPreferences';
import { buildRelationshipHealthInsights } from './relationshipHealth';
import type { AppState, Contact, RelationshipEvent, Screen } from './types';
import { localDateKey, materializeEventOccurrence } from './occasionDates';

export type AnalyticsRange = 'Last 30 days' | 'This year' | 'All time';

export interface AnalyticsMetric {
  label: string;
  value: string;
  detail: string;
}

export interface AnalyticsInsight {
  id: string;
  title: string;
  detail: string;
  actionLabel: string;
  targetScreen: Screen;
  contactId?: string;
}

export interface AnalyticsBucket {
  label: string;
  count: number;
}

export interface NeglectedContactInsight {
  contactId: string;
  name: string;
  overdueDays: number;
  cadenceDays: number;
  healthScore: number;
}

export interface AnalyticsDashboard {
  range: AnalyticsRange;
  contactCount: number;
  metrics: AnalyticsMetric[];
  relationshipDistribution: AnalyticsBucket[];
  healthBuckets: AnalyticsBucket[];
  neglectedContacts: NeglectedContactInsight[];
  insights: AnalyticsInsight[];
  emptyState?: string;
}

export interface AnalyticsShareSummary {
  title: string;
  body: string;
  lineCount: number;
  redacted: true;
}

export const analyticsRanges: AnalyticsRange[] = ['Last 30 days', 'This year', 'All time'];

const pct = (part: number, total: number) => (total === 0 ? '0%' : `${Math.round((part / total) * 100)}%`);

const rangeStart = (range: AnalyticsRange, now: Date) => {
  if (range === 'All time') {
    return Number.NEGATIVE_INFINITY;
  }
  if (range === 'This year') {
    return new Date(now.getFullYear(), 0, 1).getTime();
  }
  const start = new Date(now);
  start.setDate(start.getDate() - 30);
  return start.getTime();
};

const inRange = (iso: string | undefined, start: number, now: Date) => {
  if (!iso) {
    return false;
  }
  const time = new Date(iso).getTime();
  return !Number.isNaN(time) && time >= start && time <= now.getTime();
};

const daysSince = (iso: string | undefined, now: Date) => {
  if (!iso) {
    return Number.POSITIVE_INFINITY;
  }
  const time = new Date(iso).getTime();
  if (Number.isNaN(time)) {
    return Number.POSITIVE_INFINITY;
  }
  return Math.floor((now.getTime() - time) / (1000 * 60 * 60 * 24));
};

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

const hasPersonalization = (memoryContactIds: Set<string>, contact: Contact) =>
  contact.notesSummary.trim().length >= 24 || memoryContactIds.has(contact.id);

const bucketCount = <T extends string>(labels: T[], values: T[]): AnalyticsBucket[] =>
  labels.map(label => ({
    label,
    count: values.filter(value => value === label).length
  }));

export const buildAnalyticsDashboard = (
  state: AppState,
  range: AnalyticsRange = 'Last 30 days',
  now: Date = new Date()
): AnalyticsDashboard => {
  const start = rangeStart(range, now);
  const activeContacts = state.contacts.filter(contact => !contact.archivedAt);
  const activeContactIds = new Set(activeContacts.map(contact => contact.id));
  const healthByContact = buildRelationshipHealthInsights(state, now);
  const enrichmentByContact = buildContactEnrichmentPlans(state);
  const healthScoreFor = (contact: Contact) => healthByContact.get(contact.id)?.score ?? 0;
  const eventContactIds = new Set(
    state.events.filter(event => activeContactIds.has(event.contactId)).map(event => event.contactId)
  );
  const memoryContactIds = new Set(
    state.memories
      .filter(memory => activeContactIds.has(memory.contactId) && memory.category !== 'Private')
      .map(memory => memory.contactId)
  );
  const sentMessages = state.messages.filter(
    message =>
      activeContactIds.has(message.contactId) && message.status === 'Sent' && inRange(message.sentAt, start, now)
  );
  const failedMessages = state.messages.filter(
    message =>
      activeContactIds.has(message.contactId) &&
      ['Failed', 'Blocked', 'Delivery unknown'].includes(message.status) &&
      inRange(message.sentAt ?? message.scheduledFor, start, now)
  );
  const pendingMessages = state.messages.filter(
    message =>
      activeContactIds.has(message.contactId) && (message.status === 'Needs review' || message.status === 'Draft')
  );
  const scheduledMessages = state.messages.filter(
    message => activeContactIds.has(message.contactId) && message.status === 'Scheduled'
  );
  const contactsWithEvents = activeContacts.filter(contact => eventContactIds.has(contact.id));
  const personalizedContacts = activeContacts.filter(contact => hasPersonalization(memoryContactIds, contact));
  const healthyContacts = activeContacts.filter(contact => healthScoreFor(contact) >= 70);
  const needsAttention = activeContacts.filter(contact => healthScoreFor(contact) < 60);
  const deliveryDenominator = sentMessages.length + failedMessages.length;
  const neglectedContacts = activeContacts
    .map(contact => ({
      contact,
      preferences: resolveContactPreferencesForContact(state.settings, contact),
      days: daysSince(contact.lastContactedAt, now)
    }))
    .filter(({ days, preferences }) => days > preferences.checkInCadenceDays)
    .sort((a, b) => b.days - b.preferences.checkInCadenceDays - (a.days - a.preferences.checkInCadenceDays))
    .slice(0, 5)
    .map(({ contact, days, preferences }) => ({
      contactId: contact.id,
      name: contact.name,
      overdueDays: Number.isFinite(days) ? days - preferences.checkInCadenceDays : preferences.checkInCadenceDays,
      cadenceDays: preferences.checkInCadenceDays,
      healthScore: healthScoreFor(contact)
    }));

  const groupValues = activeContacts.map(contact => contact.group);
  const healthValues = activeContacts.map(contact => healthByContact.get(contact.id)?.label ?? 'Needs attention');
  const insights: AnalyticsInsight[] = [];
  if (pendingMessages.length > 0) {
    insights.push({
      id: 'pending-messages',
      title: 'Messages need review',
      detail: `${pendingMessages.length} message(s) are waiting for approval.`,
      actionLabel: 'Review messages',
      targetScreen: 'messages'
    });
  }
  if (needsAttention.length > 0) {
    insights.push({
      id: 'low-health',
      title: 'Reconnect with low-health contacts',
      detail: `${needsAttention.length} contact(s) are below 60 health.`,
      actionLabel: 'Open contacts',
      targetScreen: 'contacts'
    });
  }
  const weakestPlan = activeContacts
    .map(contact => enrichmentByContact.get(contact.id))
    .filter((plan): plan is NonNullable<typeof plan> => Boolean(plan))
    .sort((a, b) => a.score - b.score)[0];
  if (weakestPlan && weakestPlan.score < 50) {
    const contact = activeContacts.find(item => item.id === weakestPlan.contactId);
    insights.push({
      id: 'personalization-gap',
      title: 'Improve personalization',
      detail: `${contact?.name ?? 'A contact'} needs more relationship context.`,
      actionLabel: 'Add details',
      targetScreen: 'contactDetail',
      contactId: weakestPlan.contactId
    });
  }
  if (neglectedContacts[0]) {
    insights.push({
      id: 'neglected-contact',
      title: 'Follow up soon',
      detail: `${neglectedContacts[0].name} is overdue for a check-in.`,
      actionLabel: 'Open contact',
      targetScreen: 'contactDetail',
      contactId: neglectedContacts[0].contactId
    });
  }

  return {
    range,
    contactCount: activeContacts.length,
    metrics: [
      {
        label: 'Relationship health',
        value: pct(healthyContacts.length, activeContacts.length),
        detail: `${healthyContacts.length}/${activeContacts.length} contacts are 70+ health.`
      },
      {
        label: 'Event coverage',
        value: pct(contactsWithEvents.length, activeContacts.length),
        detail: `${contactsWithEvents.length}/${activeContacts.length} contacts have at least one event.`
      },
      {
        label: 'Personalization coverage',
        value: pct(personalizedContacts.length, activeContacts.length),
        detail: `${personalizedContacts.length}/${activeContacts.length} contacts have usable context.`
      },
      {
        label: 'Delivery success',
        value: pct(sentMessages.length, deliveryDenominator),
        detail:
          deliveryDenominator === 0
            ? `No sent or failed messages in ${range.toLowerCase()}.`
            : `${sentMessages.length}/${deliveryDenominator} attempted messages were sent.`
      },
      {
        label: 'Sent messages',
        value: String(sentMessages.length),
        detail: `${scheduledMessages.length} scheduled, ${pendingMessages.length} waiting for review.`
      }
    ],
    relationshipDistribution: bucketCount(['Family', 'Friends', 'Work', 'Close friends', 'Other'], groupValues),
    healthBuckets: bucketCount(['Healthy', 'Watch', 'Needs attention'], healthValues),
    neglectedContacts,
    insights,
    emptyState:
      activeContacts.length === 0
        ? 'Add or import contacts to see relationship analytics.'
        : sentMessages.length === 0
          ? `No sent messages in ${range.toLowerCase()}; analytics will improve after you send from RelateAI.`
          : undefined
  };
};

const csvCell = (value: string | number | undefined) => {
  const normalized = String(value ?? '').replace(/\r?\n/g, ' ');
  return /[",\n]/.test(normalized) ? `"${normalized.replace(/"/g, '""')}"` : normalized;
};

export const buildAnalyticsCsvReport = (
  state: AppState,
  dashboard: AnalyticsDashboard = buildAnalyticsDashboard(state),
  now: Date = new Date()
) => {
  const nextEventByContact = nextEventsByContact(state.events, now);
  const healthByContact = buildRelationshipHealthInsights(state, now);
  const rows: (string | number | undefined)[][] = [
    ['Section', 'Name', 'Value', 'Detail'],
    ...dashboard.metrics.map(metric => ['Metric', metric.label, metric.value, metric.detail]),
    ...dashboard.relationshipDistribution.map(bucket => [
      'Relationship distribution',
      bucket.label,
      bucket.count,
      'contacts'
    ]),
    ...dashboard.healthBuckets.map(bucket => ['Health bucket', bucket.label, bucket.count, 'contacts']),
    ...dashboard.neglectedContacts.map(contact => [
      'Neglected contact',
      contact.name,
      contact.overdueDays,
      `cadence ${contact.cadenceDays} days; health ${contact.healthScore}`
    ]),
    ...state.contacts
      .filter(contact => !contact.archivedAt)
      .map(contact => {
        const nextEvent = nextEventByContact.get(contact.id);
        return [
          'Contact summary',
          contact.name,
          healthByContact.get(contact.id)?.score ?? 0,
          `${contact.relationship}; ${contact.group}; next event ${nextEvent?.label ?? 'none'}`
        ];
      })
  ];

  return rows.map(row => row.map(csvCell).join(',')).join('\n');
};

export const buildShareableAnalyticsSummary = (
  dashboard: AnalyticsDashboard,
  generatedAt: Date = new Date()
): AnalyticsShareSummary => {
  const longestOverdue = dashboard.neglectedContacts.reduce((max, contact) => Math.max(max, contact.overdueDays), 0);
  const nextActions = dashboard.insights.slice(0, 3).map(insight => `${insight.title}: ${insight.actionLabel}`);
  const lines = [
    'RelateAI relationship summary',
    `Range: ${dashboard.range}`,
    `Generated: ${generatedAt.toISOString().slice(0, 10)}`,
    '',
    'Metrics:',
    ...dashboard.metrics.map(metric => `- ${metric.label}: ${metric.value} (${metric.detail})`),
    '',
    'Health buckets:',
    ...dashboard.healthBuckets.map(bucket => `- ${bucket.label}: ${bucket.count}`),
    '',
    'Relationship groups:',
    ...dashboard.relationshipDistribution.map(bucket => `- ${bucket.label}: ${bucket.count}`),
    '',
    'Follow-up focus:',
    dashboard.neglectedContacts.length > 0
      ? `- ${dashboard.neglectedContacts.length} contact(s) are beyond their check-in cadence. Longest overdue: ${longestOverdue} day(s).`
      : '- No overdue check-ins in this view.',
    '',
    'Next actions:',
    ...(nextActions.length > 0
      ? nextActions.map(action => `- ${action}`)
      : ['- No urgent analytics action in this view.']),
    '',
    'Privacy: This summary excludes message bodies, phone numbers, email addresses, private notes, credentials, and raw provider data.'
  ];

  return {
    title: `${dashboard.range} relationship summary`,
    body: lines.join('\n'),
    lineCount: lines.length,
    redacted: true
  };
};
