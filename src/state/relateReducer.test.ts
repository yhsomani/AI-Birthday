import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createInitialState, relateReducer } from './relateReducer';

describe('relateReducer feature contract', () => {
  it('creates review-first drafts from contact context and excludes private notes', () => {
    const state = createInitialState();
    const next = relateReducer(state, {
      type: 'generateMessage',
      contactId: 'c-rajesh',
      eventId: 'e-rajesh-work',
      reason: 'Congratulations'
    });

    assert.equal(next.activeScreen, 'wishPreview');
    assert.equal(next.messages[0].status, 'Needs review');
    assert.equal(next.messages[0].quality, 'Needs more context');
    assert.doesNotMatch(next.messages[0].body, /Private note excluded/i);
  });

  it('warns when a generated draft duplicates an existing contact-event draft', () => {
    const state = createInitialState();
    const next = relateReducer(state, {
      type: 'generateMessage',
      contactId: 'c-asha',
      eventId: 'e-asha-bday',
      reason: 'Birthday'
    });

    assert.match(next.messages[0].duplicateWarning ?? '', /similar message/i);
  });

  it('blocks approval for blank or too-short messages', () => {
    const state = createInitialState();
    const edited = relateReducer(state, {
      type: 'editMessage',
      messageId: 'msg-asha-bday',
      body: 'Hi'
    });
    const approved = relateReducer(edited, {
      type: 'approveMessage',
      messageId: 'msg-asha-bday'
    });
    const message = approved.messages.find(item => item.id === 'msg-asha-bday');

    assert.equal(message?.status, 'Blocked');
    assert.match(message?.lastError ?? '', /too short/i);
  });

  it('approves valid messages without sending them unattended', () => {
    const state = createInitialState();
    const approved = relateReducer(state, {
      type: 'approveMessage',
      messageId: 'msg-asha-bday'
    });
    const message = approved.messages.find(item => item.id === 'msg-asha-bday');

    assert.equal(message?.status, 'Scheduled');
    assert.match(message?.readiness ?? '', /approved|scheduled/i);
  });

  it('creates provider AI drafts as review-first messages', () => {
    const state = createInitialState();
    const next = relateReducer(state, {
      type: 'createAiDraft',
      contactId: 'c-rajesh',
      eventId: 'e-rajesh-work',
      reason: 'Congratulations',
      privacySummary: '0 memory item(s) included; 1 private item(s) excluded.',
      variants: {
        short: 'Congratulations Rajesh on the milestone.',
        standard: 'Congratulations Rajesh. Wishing you continued success and a meaningful year ahead.',
        warm: 'Congratulations Rajesh. This milestone reflects your effort, and I hope the year ahead is rewarding.'
      }
    });

    assert.equal(next.messages[0].status, 'Needs review');
    assert.equal(next.messages[0].quality, 'AI draft');
    assert.equal(next.aiProvider.status, 'Ready');
    assert.match(next.messages[0].readiness, /provider draft/i);
  });

  it('creates local template fallbacks when provider drafting cannot be used', () => {
    const state = createInitialState();
    const next = relateReducer(state, {
      type: 'generateMessage',
      contactId: 'c-mira',
      eventId: 'e-mira-checkin',
      reason: 'Check-in',
      fallbackReason: 'The AI provider could not be reached.'
    });

    assert.equal(next.messages[0].status, 'Needs review');
    assert.equal(next.messages[0].quality, 'Template fallback');
    assert.match(next.messages[0].lastError ?? '', /could not be reached/i);
  });

  it('manual handoff marks a message sent and improves contact health', () => {
    const state = createInitialState();
    const before = state.contacts.find(contact => contact.id === 'c-mira')?.healthScore ?? 0;
    const next = relateReducer(state, {
      type: 'manualHandoff',
      messageId: 'msg-mira-checkin'
    });
    const message = next.messages.find(item => item.id === 'msg-mira-checkin');
    const after = next.contacts.find(contact => contact.id === 'c-mira')?.healthScore ?? 0;

    assert.equal(message?.status, 'Sent');
    assert.ok(after > before);
  });

  it('toggles event preparation checklist items', () => {
    const state = createInitialState();
    const before = state.events
      .find(event => event.id === 'e-asha-bday')
      ?.checklist.find(item => item.id === 'write-wish')?.done;
    const next = relateReducer(state, {
      type: 'toggleChecklist',
      eventId: 'e-asha-bday',
      itemId: 'write-wish'
    });
    const after = next.events
      .find(event => event.id === 'e-asha-bday')
      ?.checklist.find(item => item.id === 'write-wish')?.done;

    assert.equal(before, false);
    assert.equal(after, true);
  });

  it('records private memories without making them AI prompt context', () => {
    const state = createInitialState();
    const withPrivateMemory = relateReducer(state, {
      type: 'addMemory',
      contactId: 'c-mira',
      category: 'Private',
      body: 'Private context that should never appear in a draft.'
    });
    const generated = relateReducer(withPrivateMemory, {
      type: 'generateMessage',
      contactId: 'c-mira',
      eventId: 'e-mira-checkin',
      reason: 'Check-in'
    });

    assert.equal(withPrivateMemory.memories[0].category, 'Private');
    assert.doesNotMatch(generated.messages[0].body, /Private context/);
  });

  it('creates explicit encrypted backup snapshots', () => {
    const state = createInitialState();
    const next = relateReducer(state, { type: 'createBackup' });

    assert.equal(next.backups.length, state.backups.length + 1);
    assert.equal(next.backups[0].encrypted, true);
    assert.ok(next.backups[0].recordCount > 0);
  });

  it('restores backup state atomically through an explicit restore action', () => {
    const state = createInitialState();
    const modified = relateReducer(state, {
      type: 'addMemory',
      contactId: 'c-asha',
      category: 'General',
      body: 'Temporary memory that should disappear after restore.'
    });
    const restored = relateReducer(modified, {
      type: 'restoreBackup',
      restoredState: state,
      recordCount: state.contacts.length + state.events.length + state.messages.length
    });

    assert.equal(restored.memories.length, state.memories.length);
    assert.equal(restored.activeScreen, 'more');
    assert.match(restored.activity[0].title, /restored/i);
    assert.doesNotMatch(JSON.stringify(restored.memories), /Temporary memory/);
  });

  it('toggles boolean settings but ignores non-boolean settings', () => {
    const state = createInitialState();
    const toggled = relateReducer(state, { type: 'toggleSetting', key: 'aiEnabled' });
    const unchanged = relateReducer(toggled, { type: 'toggleSetting', key: 'automationMode' });

    assert.equal(toggled.settings.aiEnabled, !state.settings.aiEnabled);
    assert.equal(unchanged.settings.automationMode, toggled.settings.automationMode);
  });

  it('updates locale as an explicit user-controlled preference', () => {
    const state = createInitialState();
    const next = relateReducer(state, { type: 'setLocale', locale: 'hi-IN' });

    assert.equal(next.settings.locale, 'hi-IN');
    assert.match(next.activity[0].detail, /hi-IN/);
  });

  it('stores email sender configuration and marks provider email sends complete', () => {
    const state = createInitialState();
    const withEmailMessage = {
      ...state,
      messages: [
        {
          ...state.messages[0],
          id: 'msg-email-rajesh',
          contactId: 'c-rajesh',
          eventId: 'e-rajesh-work',
          channel: 'Email' as const,
          status: 'Scheduled' as const,
          body: 'Congratulations Rajesh, wishing you continued success and a meaningful year ahead.'
        },
        ...state.messages
      ]
    };
    const configured = relateReducer(withEmailMessage, {
      type: 'setEmailSender',
      senderEmail: ' me@example.com '
    });
    const sent = relateReducer(configured, {
      type: 'emailSent',
      messageId: 'msg-email-rajesh'
    });
    const message = sent.messages.find(item => item.id === 'msg-email-rajesh');

    assert.equal(configured.emailDelivery.senderEmail, 'me@example.com');
    assert.equal(message?.status, 'Sent');
    assert.equal(sent.emailDelivery.status, 'Ready');
    assert.match(sent.activity[0].title, /Email sent/);
  });
});
