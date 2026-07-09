import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createInitialState, relateReducer } from '../state/relateReducer';
import { buildChatHistory } from './chatHistory';

describe('chat history contract', () => {
  it('shows only sent messages for a contact in newest-first order', () => {
    const firstSent = relateReducer(createInitialState(), {
      type: 'manualHandoff',
      messageId: 'msg-mira-checkin',
      nowIso: '2026-07-09T10:00:00.000Z'
    });
    const withSecond = {
      ...firstSent,
      messages: [
        {
          ...firstSent.messages.find(message => message.id === 'msg-mira-checkin')!,
          id: 'msg-mira-second',
          body: 'Second sent follow-up.',
          status: 'Sent' as const,
          sentAt: '2026-07-10T10:00:00.000Z'
        },
        ...firstSent.messages
      ]
    };
    const history = buildChatHistory(withSecond, { contactId: 'c-mira' });

    assert.deepEqual(
      history.messages.map(message => message.id),
      ['msg-mira-second', 'msg-mira-checkin']
    );
    assert.equal(history.emptyState, undefined);
  });

  it('searches sent history by body, reason, and channel without changing messages', () => {
    const sent = relateReducer(createInitialState(), {
      type: 'manualHandoff',
      messageId: 'msg-mira-checkin',
      nowIso: '2026-07-09T10:00:00.000Z'
    });
    const byText = buildChatHistory(sent, { contactId: 'c-mira', searchQuery: 'pune' });
    const byChannel = buildChatHistory(sent, { contactId: 'c-mira', searchQuery: 'manual' });
    const filteredOut = buildChatHistory(sent, { contactId: 'c-mira', channel: 'Email' });

    assert.equal(byText.messages.length, 1);
    assert.equal(byChannel.messages.length, 1);
    assert.equal(filteredOut.messages.length, 0);
    assert.equal(filteredOut.emptyState, 'No matching messages');
  });

  it('distinguishes no sent messages from deleted-contact history', () => {
    const state = createInitialState();
    const noSent = buildChatHistory(state, { contactId: 'c-rajesh' });
    const deletedContactHistory = buildChatHistory(
      {
        ...state,
        contacts: state.contacts.filter(contact => contact.id !== 'c-mira'),
        messages: [
          {
            ...state.messages.find(message => message.id === 'msg-mira-checkin')!,
            status: 'Sent',
            sentAt: '2026-07-09T10:00:00.000Z'
          }
        ]
      },
      { contactId: 'c-mira' }
    );

    assert.equal(noSent.emptyState, 'No sent messages');
    assert.equal(deletedContactHistory.contactExists, false);
    assert.equal(deletedContactHistory.messages.length, 1);
  });
});
