import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
import { buildRelationshipHealthInsight, buildRelationshipHealthInsights } from './relationshipHealth';

describe('relationship health and classification contract', () => {
  it('keeps indexed batch insights equivalent to individual contact insights', () => {
    const state = createTestState();
    const now = new Date('2026-07-10T10:00:00.000Z');
    const insights = buildRelationshipHealthInsights(state, now);
    for (const contact of state.contacts) {
      assert.deepEqual(insights.get(contact.id), buildRelationshipHealthInsight(state, contact.id, now));
    }
  });

  it('explains relationship health with actionable non-shaming reasons', () => {
    const state = createTestState();
    const insight = buildRelationshipHealthInsight(state, 'c-mira', new Date('2026-10-09T10:00:00.000Z'));

    assert.equal(insight?.label, 'Needs attention');
    assert.match(insight?.summary ?? '', /needs attention/i);
    assert.ok(insight?.reasons.some(reason => /cadence/i.test(reason)));
    assert.ok(insight?.reasons.some(reason => /upcoming relationship event/i.test(reason)));
  });

  it('suggests relationship groups without mutating the contact automatically', () => {
    const state = {
      ...createTestState(),
      contacts: [
        {
          ...createTestState().contacts[0],
          id: 'c-work',
          relationship: 'Manager',
          group: 'Other' as const,
          notesSummary: 'Professional mentor from work.'
        }
      ],
      memories: [],
      events: [],
      messages: [],
      gifts: []
    };
    const insight = buildRelationshipHealthInsight(state, 'c-work', new Date('2026-07-09T10:00:00.000Z'));

    assert.equal(insight?.suggestion?.group, 'Work');
    assert.equal(insight?.suggestion?.confidence, 'High');
    assert.equal(state.contacts[0].group, 'Other');
  });

  it('derives health from current recency and follow-through instead of trusting a stale stored score', () => {
    const base = createTestState();
    const now = new Date('2026-07-10T10:00:00.000Z');
    const stale = {
      ...base,
      contacts: base.contacts.map(contact =>
        contact.id === 'c-mira'
          ? {
              ...contact,
              healthScore: 99,
              lastContactedAt: '2025-01-01T00:00:00.000Z',
              checkInCadenceDays: 30
            }
          : contact
      ),
      events: base.events.filter(event => event.contactId !== 'c-mira'),
      memories: base.memories.filter(memory => memory.contactId !== 'c-mira'),
      messages: base.messages.filter(message => message.contactId !== 'c-mira'),
      gifts: base.gifts.filter(gift => gift.contactId !== 'c-mira')
    };
    const current = {
      ...stale,
      contacts: stale.contacts.map(contact =>
        contact.id === 'c-mira' ? { ...contact, lastContactedAt: '2026-07-09T10:00:00.000Z' } : contact
      )
    };

    const staleInsight = buildRelationshipHealthInsight(stale, 'c-mira', now);
    const currentInsight = buildRelationshipHealthInsight(current, 'c-mira', now);

    assert.notEqual(staleInsight?.score, 99);
    assert.ok((currentInsight?.score ?? 0) > (staleInsight?.score ?? 0));
  });

  it('excludes private memories from classification rationale', () => {
    const state = {
      ...createTestState(),
      contacts: [
        {
          ...createTestState().contacts[1],
          id: 'c-private',
          relationship: 'Acquaintance',
          group: 'Other' as const,
          notesSummary: ''
        }
      ],
      memories: [
        {
          id: 'private-family',
          contactId: 'c-private',
          category: 'Private' as const,
          body: 'This person is secretly family.',
          pinned: false,
          createdAt: '2026-07-01T00:00:00.000Z'
        }
      ],
      events: [],
      messages: [],
      gifts: []
    };
    const insight = buildRelationshipHealthInsight(state, 'c-private', new Date('2026-07-09T10:00:00.000Z'));

    assert.equal(insight?.suggestion, undefined);
    assert.doesNotMatch(JSON.stringify(insight), /secretly family/i);
  });

  it('applies relationship classification and contact priority only after explicit actions', () => {
    const state = createTestState();
    const grouped = relateReducer(state, { type: 'setContactGroup', contactId: 'c-mira', group: 'Friends' });
    const vip = relateReducer(grouped, { type: 'toggleContactVip', contactId: 'c-mira' });
    const dnd = relateReducer(vip, { type: 'toggleContactDnd', contactId: 'c-mira' });
    const cadence = relateReducer(dnd, { type: 'setCheckInCadence', contactId: 'c-mira', days: 90 });
    const contact = cadence.contacts.find(item => item.id === 'c-mira');

    assert.equal(contact?.group, 'Friends');
    assert.equal(contact?.isVip, true);
    assert.equal(contact?.dnd, true);
    assert.equal(contact?.checkInCadenceDays, 90);
    assert.match(cadence.activity[0].detail, /90/);
  });
});
