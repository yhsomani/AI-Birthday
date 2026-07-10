import assert from 'node:assert/strict';
import { performance } from 'node:perf_hooks';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { buildAnalyticsCsvReport, buildAnalyticsDashboard } from './analytics';
import { buildContactBrowserRows } from './contactBrowser';
import { buildEventMonthView, filterRelationshipEvents } from './eventBrowser';
import { buildHomeWidgetSummary } from './homeWidget';
import { buildMessageInbox } from './messageInbox';
import type { AppState, Contact, MemoryNote, MessageChannel, MessageDraft, RelationshipEvent } from './types';

const groups = ['Family', 'Friends', 'Work', 'Close friends', 'Other'] as const;
const channels: MessageChannel[] = ['SMS', 'WhatsApp', 'Email', 'Manual'];
const statuses = ['Needs review', 'Scheduled', 'Sent', 'Failed', 'Draft'] as const;

const isoFrom = (base: Date, offsetDays: number) => {
  const next = new Date(base);
  next.setUTCDate(base.getUTCDate() + offsetDays);
  return next.toISOString();
};

const buildLargeState = (count: number, now: Date): AppState => {
  const base = createTestState();
  const contacts: Contact[] = Array.from({ length: count }, (_, index) => {
    const channel = channels[index % channels.length];
    return {
      id: `scale-contact-${index}`,
      name: `Scale Contact ${index}`,
      relationship: index % 3 === 0 ? 'Friend' : index % 3 === 1 ? 'Colleague' : 'Family',
      group: groups[index % groups.length],
      phone: channel === 'Email' ? undefined : `+9198000${String(index).padStart(5, '0')}`,
      email: channel === 'Email' ? `scale${index}@example.com` : undefined,
      preferredChannel: channel,
      language: index % 5 === 0 ? 'Hindi' : index % 2 === 0 ? 'Hinglish' : 'English',
      tone: index % 2 === 0 ? ['Warm', 'Concise'] : ['Respectful'],
      healthScore: 35 + (index % 66),
      isVip: index % 20 === 0,
      dnd: index % 37 === 0,
      checkInCadenceDays: [14, 30, 45, 60, 90][index % 5],
      lastContactedAt: isoFrom(now, -(index % 120)),
      notesSummary: index % 4 === 0 ? '' : `Useful context for scale contact ${index}.`,
      annualGiftBudget: 1000 + (index % 10) * 500
    };
  });
  const events: RelationshipEvent[] = contacts.flatMap((contact, index) => [
    {
      id: `scale-event-${index}-birthday`,
      contactId: contact.id,
      type: 'Birthday',
      label: `Birthday ${index}`,
      date: isoFrom(now, (index % 90) - 10),
      verified: index % 7 !== 0,
      source: index % 2 === 0 ? 'Imported' : 'Manual',
      checklist: []
    },
    {
      id: `scale-event-${index}-followup`,
      contactId: contact.id,
      type: 'Follow-up',
      label: `Follow-up ${index}`,
      date: isoFrom(now, (index % 30) + 1),
      verified: true,
      source: 'AI suggested',
      checklist: []
    }
  ]);
  const messages: MessageDraft[] = contacts.map((contact, index) => {
    const status = statuses[index % statuses.length];
    return {
      id: `scale-message-${index}`,
      contactId: contact.id,
      eventId: `scale-event-${index}-birthday`,
      reason: index % 3 === 0 ? 'Birthday' : 'Check-in',
      status,
      channel: contact.preferredChannel,
      body: `A safe scale-test message for ${contact.name} with enough length for validation.`,
      variants: {
        short: `Hi ${contact.name}, thinking of you.`,
        standard: `A safe scale-test message for ${contact.name} with enough length for validation.`,
        warm: `A warm scale-test message for ${contact.name} with enough length for validation.`
      },
      selectedVariant: 'standard',
      scheduledFor: status === 'Scheduled' || status === 'Failed' ? isoFrom(now, index % 20) : undefined,
      sentAt: status === 'Sent' ? isoFrom(now, -(index % 20)) : undefined,
      quality: index % 2 === 0 ? 'AI draft' : 'Template fallback',
      readiness: status === 'Failed' ? 'Needs recovery' : 'Ready for review',
      lastError: status === 'Failed' ? 'Provider unavailable.' : undefined
    };
  });
  const memories: MemoryNote[] = contacts
    .filter((_, index) => index % 3 === 0)
    .map((contact, index) => ({
      id: `scale-memory-${index}`,
      contactId: contact.id,
      category: index % 4 === 0 ? 'Private' : 'Preference',
      body: index % 4 === 0 ? 'Private scale note' : `Scale preference ${index}`,
      pinned: index % 4 !== 0,
      createdAt: isoFrom(now, -index)
    }));

  return {
    ...base,
    contacts,
    events,
    messages,
    memories,
    gifts: [],
    activity: base.activity
  };
};

describe('large dataset workflow contract', () => {
  it('keeps primary RN list and report builders bounded on production-sized local data', () => {
    const now = new Date('2026-07-09T10:00:00.000Z');
    const state = buildLargeState(900, now);
    const startedAt = performance.now();

    const contactRows = buildContactBrowserRows(
      state,
      { query: '', group: 'All', quality: 'All', sort: 'Next event' },
      now
    );
    const inbox = buildMessageInbox(state, {
      tab: 'All',
      channel: 'All',
      query: '',
      sort: 'Scheduled',
      emailEndpointConfigured: false
    });
    const upcomingEvents = filterRelationshipEvents(state.events, {
      type: 'All',
      time: 'Upcoming',
      nowIso: now.toISOString(),
      monthIso: now.toISOString()
    });
    const monthView = buildEventMonthView(upcomingEvents, now.toISOString());
    const analytics = buildAnalyticsDashboard(state, 'All time', now);
    const widget = buildHomeWidgetSummary(state, now);
    const csv = buildAnalyticsCsvReport(state, analytics);
    const elapsedMs = performance.now() - startedAt;

    assert.equal(contactRows.length, state.contacts.length);
    assert.equal(inbox.counts.All, state.messages.length);
    assert.equal(monthView.days.length, 42);
    assert.equal(analytics.contactCount, state.contacts.length);
    assert.ok(widget.tiles.length <= 4);
    assert.ok(csv.split('\n').length > state.contacts.length);
    assert.ok(elapsedMs < 5000, `large dataset builders took ${Math.round(elapsedMs)}ms`);
  });
});
