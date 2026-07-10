import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { MESSAGE_BODY_LIMITS } from '../domain/messageBodyPolicy';
import { relateReducer } from './relateReducer';
import { createTestState } from '../test/testState';

describe('relateReducer feature contract', () => {
  it('creates review-first drafts from contact context and excludes private notes', () => {
    const state = createTestState();
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
    const state = createTestState();
    const next = relateReducer(state, {
      type: 'generateMessage',
      contactId: 'c-asha',
      eventId: 'e-asha-bday',
      reason: 'Birthday'
    });

    assert.match(next.messages[0].duplicateWarning ?? '', /similar message/i);
  });

  it('blocks approval for blank or too-short messages', () => {
    const state = createTestState();
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

  it('blocks approval when the body exceeds the selected channel cap', () => {
    const state = createTestState();
    const edited = relateReducer(state, {
      type: 'editMessage',
      messageId: 'msg-asha-bday',
      body: 'A'.repeat(MESSAGE_BODY_LIMITS.SMS + 1)
    });
    const approved = relateReducer(edited, {
      type: 'approveMessage',
      messageId: 'msg-asha-bday'
    });
    const message = approved.messages.find(item => item.id === 'msg-asha-bday');

    assert.equal(message?.status, 'Blocked');
    assert.match(message?.lastError ?? '', /Shorten the message or switch channel/i);
  });

  it('allows multipart SMS approval while keeping the segment warning visible', () => {
    const state = createTestState();
    const edited = relateReducer(state, {
      type: 'editMessage',
      messageId: 'msg-asha-bday',
      body: 'A'.repeat(170)
    });
    const approved = relateReducer(edited, {
      type: 'approveMessage',
      messageId: 'msg-asha-bday',
      nowIso: '2026-07-09T10:00:00.000Z'
    });
    const message = approved.messages.find(item => item.id === 'msg-asha-bday');

    assert.equal(message?.status, 'Scheduled');
    assert.match(message?.readiness ?? '', /2 parts/i);
    assert.equal(message?.approvedAt, '2026-07-09T10:00:00.000Z');
  });

  it('preserves edited message text unless variant replacement is confirmed', () => {
    const state = createTestState();
    const customBody = 'Asha, I rewrote this birthday note with a personal memory and a gentler ending.';
    const edited = relateReducer(state, {
      type: 'editMessage',
      messageId: 'msg-asha-bday',
      body: customBody
    });
    const unconfirmed = relateReducer(edited, {
      type: 'selectVariant',
      messageId: 'msg-asha-bday',
      variant: 'warm'
    });
    const unconfirmedMessage = unconfirmed.messages.find(item => item.id === 'msg-asha-bday');

    assert.equal(unconfirmedMessage?.selectedVariant, 'standard');
    assert.equal(unconfirmedMessage?.body, customBody);

    const confirmed = relateReducer(edited, {
      type: 'selectVariant',
      messageId: 'msg-asha-bday',
      variant: 'warm',
      discardEditedBody: true
    });
    const confirmedMessage = confirmed.messages.find(item => item.id === 'msg-asha-bday');

    assert.equal(confirmedMessage?.selectedVariant, 'warm');
    assert.equal(confirmedMessage?.body, confirmedMessage?.variants.warm);
  });

  it('approves valid messages without sending them unattended', () => {
    const state = createTestState();
    const approved = relateReducer(state, {
      type: 'approveMessage',
      messageId: 'msg-asha-bday',
      nowIso: '2026-07-09T10:00:00.000Z'
    });
    const message = approved.messages.find(item => item.id === 'msg-asha-bday');

    assert.equal(message?.status, 'Scheduled');
    assert.match(message?.readiness ?? '', /approved|scheduled/i);
    assert.equal(message?.approvedAt, '2026-07-09T10:00:00.000Z');
    assert.equal(message?.approvalExpiresAt, '2026-07-16T10:00:00.000Z');
  });

  it('moves to the next pending draft after successful approval when requested', () => {
    const state = {
      ...createTestState(),
      activeScreen: 'wishPreview' as const,
      selectedMessageId: 'msg-asha-bday',
      selectedContactId: 'c-asha'
    };
    const next = relateReducer(state, {
      type: 'approveMessage',
      messageId: 'msg-asha-bday',
      nowIso: '2026-07-09T10:00:00.000Z',
      reviewNext: true
    });
    const approved = next.messages.find(message => message.id === 'msg-asha-bday');

    assert.equal(approved?.status, 'Scheduled');
    assert.equal(next.activeScreen, 'wishPreview');
    assert.equal(next.selectedMessageId, 'msg-mira-checkin');
    assert.equal(next.selectedContactId, 'c-mira');
  });

  it('blocks approval for do-not-disturb contacts', () => {
    const base = createTestState();
    const state = {
      ...base,
      contacts: base.contacts.map(contact => (contact.id === 'c-asha' ? { ...contact, dnd: true } : contact))
    };
    const approved = relateReducer(state, {
      type: 'approveMessage',
      messageId: 'msg-asha-bday'
    });
    const message = approved.messages.find(item => item.id === 'msg-asha-bday');

    assert.equal(message?.status, 'Blocked');
    assert.match(message?.readiness ?? '', /do-not-disturb/i);
    assert.match(message?.lastError ?? '', /do-not-disturb/i);
    assert.equal(approved.activity[0].severity, 'Warning');
    assert.match(approved.activity[0].title, /blocked/i);
  });

  it('blocks approval when the delivery route is unavailable', () => {
    const base = createTestState();
    const state = {
      ...base,
      settings: {
        ...base.settings,
        smsEnabled: false
      }
    };
    const approved = relateReducer(state, {
      type: 'approveMessage',
      messageId: 'msg-asha-bday'
    });
    const message = approved.messages.find(item => item.id === 'msg-asha-bday');

    assert.equal(message?.status, 'Blocked');
    assert.match(message?.readiness ?? '', /route/i);
    assert.match(message?.lastError ?? '', /SMS is disabled/i);
    assert.equal(approved.activity[0].severity, 'Warning');
  });

  it('keeps the current preview open when approval fails during review-next', () => {
    const base = createTestState();
    const state = {
      ...base,
      activeScreen: 'wishPreview' as const,
      selectedMessageId: 'msg-asha-bday',
      selectedContactId: 'c-asha',
      settings: {
        ...base.settings,
        smsEnabled: false
      }
    };
    const next = relateReducer(state, {
      type: 'approveMessage',
      messageId: 'msg-asha-bday',
      reviewNext: true
    });
    const blocked = next.messages.find(message => message.id === 'msg-asha-bday');

    assert.equal(blocked?.status, 'Blocked');
    assert.equal(next.activeScreen, 'wishPreview');
    assert.equal(next.selectedMessageId, 'msg-asha-bday');
    assert.equal(next.selectedContactId, 'c-asha');
    assert.match(blocked?.lastError ?? '', /SMS is disabled/i);
  });

  it('revokes scheduled approval back to review without deleting the draft', () => {
    const state = createTestState();
    const approved = relateReducer(state, {
      type: 'approveMessage',
      messageId: 'msg-asha-bday'
    });
    const revoked = relateReducer(approved, {
      type: 'revokeMessage',
      messageId: 'msg-asha-bday'
    });
    const message = revoked.messages.find(item => item.id === 'msg-asha-bday');

    assert.equal(message?.status, 'Needs review');
    assert.equal(message?.approvedAt, undefined);
    assert.equal(message?.approvalExpiresAt, undefined);
    assert.match(message?.readiness ?? '', /revoked/i);
    assert.match(revoked.activity[0].title, /revoked/i);
  });

  it('returns to Messages when rejection leaves no pending drafts', () => {
    const base = createTestState();
    const state = {
      ...base,
      activeScreen: 'wishPreview' as const,
      selectedMessageId: 'msg-asha-bday',
      selectedContactId: 'c-asha',
      messages: [base.messages[0]]
    };
    const next = relateReducer(state, {
      type: 'rejectMessage',
      messageId: 'msg-asha-bday',
      reviewNext: true
    });

    assert.equal(next.messages[0].status, 'Rejected');
    assert.equal(next.activeScreen, 'messages');
    assert.equal(next.selectedMessageId, undefined);
    assert.equal(next.selectedContactId, undefined);
  });

  it('applies bulk message actions only to eligible selected messages and reports partial skips', () => {
    const edited = relateReducer(createTestState(), {
      type: 'editMessage',
      messageId: 'msg-asha-bday',
      body: 'Hi'
    });
    const bulkApproved = relateReducer(edited, {
      type: 'bulkMessageAction',
      action: 'Approve',
      messageIds: ['msg-asha-bday', 'msg-mira-checkin']
    });
    const skipped = bulkApproved.messages.find(item => item.id === 'msg-asha-bday');
    const approved = bulkApproved.messages.find(item => item.id === 'msg-mira-checkin');

    assert.equal(skipped?.status, 'Needs review');
    assert.equal(approved?.status, 'Scheduled');
    assert.match(bulkApproved.activity[0].title, /partially applied/i);
    assert.match(bulkApproved.activity[0].detail, /1\/2 selected/i);
  });

  it('creates provider AI drafts as review-first messages', () => {
    const state = createTestState();
    const next = relateReducer(state, {
      type: 'createAiDraft',
      contactId: 'c-rajesh',
      eventId: 'e-rajesh-work',
      reason: 'Congratulations',
      privacySummary: '0 memory item(s) included; 1 private item(s) excluded.',
      observation: {
        redacted: true,
        ok: true,
        durationMs: 320,
        reason: 'Congratulations',
        contactLanguage: 'English',
        includedMemoryCount: 0,
        excludedPrivateMemoryCount: 1,
        includedPriorMessageCount: 0,
        variantLengths: {
          short: 38,
          standard: 79,
          warm: 98
        }
      },
      variants: {
        short: 'Congratulations Rajesh on the milestone.',
        standard: 'Congratulations Rajesh. Wishing you continued success and a meaningful year ahead.',
        warm: 'Congratulations Rajesh. This milestone reflects your effort, and I hope the year ahead is rewarding.'
      }
    });

    assert.equal(next.messages[0].status, 'Needs review');
    assert.equal(next.messages[0].quality, 'AI draft');
    assert.equal(next.aiProvider.status, 'Ready');
    assert.equal(next.aiProvider.lastObservation?.redacted, true);
    assert.equal(next.aiProvider.lastObservation?.variantLengths?.standard, 79);
    assert.match(next.messages[0].readiness, /provider draft/i);
  });

  it('stores regeneration feedback on provider drafts without logging custom feedback', () => {
    const state = createTestState();
    const next = relateReducer(state, {
      type: 'createAiDraft',
      contactId: 'c-rajesh',
      eventId: 'e-rajesh-work',
      reason: 'Congratulations',
      privacySummary: '1 memory item(s) included; 0 private item(s) excluded.',
      feedback: {
        instructions: ['Make the draft shorter and easier to send.'],
        customInstruction: 'Mention mango lassi softly.',
        previousDraftExcerpt: 'Happy birthday Asha! Older draft.'
      },
      variants: {
        short: 'Congratulations Rajesh on the milestone.',
        standard: 'Congratulations Rajesh. Wishing you continued success and a meaningful year ahead.',
        warm: 'Congratulations Rajesh. This milestone reflects your effort, and I hope the year ahead is rewarding.'
      }
    });

    assert.equal(next.messages[0].quality, 'AI draft');
    assert.match(next.messages[0].readiness, /feedback/i);
    assert.equal(next.messages[0].regenerationFeedback?.instructions.length, 1);
    assert.match(next.messages[0].regenerationFeedback?.customInstruction ?? '', /mango lassi/i);
    assert.equal(next.activity[0].title, 'AI draft regenerated');
    assert.doesNotMatch(next.activity[0].detail, /mango lassi/i);
  });

  it('stores redacted AI provider failure observations for diagnostics', () => {
    const state = createTestState();
    const next = relateReducer(state, {
      type: 'aiProviderFailure',
      error: {
        kind: 'wrong-language',
        message: 'The AI provider returned the draft in the wrong language.'
      },
      privacySummary: '1 memory item(s) included; 0 private item(s) excluded.',
      observation: {
        redacted: true,
        ok: false,
        durationMs: 240,
        reason: 'Birthday',
        contactLanguage: 'Hindi',
        includedMemoryCount: 1,
        excludedPrivateMemoryCount: 0,
        includedPriorMessageCount: 0,
        errorKind: 'wrong-language'
      }
    });

    assert.equal(next.aiProvider.status, 'Error');
    assert.equal(next.aiProvider.lastObservation?.errorKind, 'wrong-language');
    assert.doesNotMatch(JSON.stringify(next.aiProvider.lastObservation), /draft in the wrong language/i);
    assert.match(next.activity[0].title, /AI provider unavailable/);
  });

  it('creates local template fallbacks when provider drafting cannot be used', () => {
    const state = createTestState();
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

  it('keeps feedback on local regeneration fallbacks', () => {
    const state = createTestState();
    const next = relateReducer(state, {
      type: 'generateMessage',
      contactId: 'c-rajesh',
      eventId: 'e-rajesh-work',
      reason: 'Congratulations',
      fallbackReason: 'The AI provider could not be reached.',
      feedback: {
        instructions: ['Make the draft shorter and easier to send.', 'Avoid generic wishes and make the message feel less templated.'],
        customInstruction: 'Keep it professional in one sentence.',
        previousDraftExcerpt: 'Hey Mira, older draft.'
      }
    });

    assert.equal(next.messages[0].quality, 'Template fallback');
    assert.match(next.messages[0].readiness, /feedback/i);
    assert.equal(next.messages[0].regenerationFeedback?.instructions.length, 2);
    assert.ok(next.messages[0].body.length <= 120);
    assert.match(next.messages[0].lastError ?? '', /could not be reached/i);
    assert.doesNotMatch(next.activity[0].detail, /professional/i);
  });

  it('respects excluded memory context for local regeneration fallbacks', () => {
    const state = createTestState();
    const next = relateReducer(state, {
      type: 'generateMessage',
      contactId: 'c-mira',
      eventId: 'e-mira-checkin',
      reason: 'Check-in',
      fallbackReason: 'The AI provider could not be reached.',
      excludedMemoryIds: ['m-mira-1']
    });

    assert.doesNotMatch(next.messages[0].body, /Pune|design role/i);
    assert.equal(next.messages[0].quality, 'Template fallback');
  });

  it('manual handoff marks a message sent and improves contact health', () => {
    const state = createTestState();
    const approved = relateReducer(state, {
      type: 'approveMessage',
      messageId: 'msg-mira-checkin'
    });
    const before = state.contacts.find(contact => contact.id === 'c-mira')?.healthScore ?? 0;
    const next = relateReducer(approved, {
      type: 'manualHandoff',
      messageId: 'msg-mira-checkin'
    });
    const message = next.messages.find(item => item.id === 'msg-mira-checkin');
    const after = next.contacts.find(contact => contact.id === 'c-mira')?.healthScore ?? 0;

    assert.equal(message?.status, 'Sent');
    assert.ok(after > before);
  });

  it('blocks manual handoff before approval', () => {
    const state = createTestState();
    const before = state.contacts.find(contact => contact.id === 'c-mira')?.healthScore ?? 0;
    const next = relateReducer(state, {
      type: 'manualHandoff',
      messageId: 'msg-mira-checkin'
    });
    const message = next.messages.find(item => item.id === 'msg-mira-checkin');
    const after = next.contacts.find(contact => contact.id === 'c-mira')?.healthScore ?? 0;

    assert.equal(message?.status, 'Draft');
    assert.equal(message?.sentAt, undefined);
    assert.equal(after, before);
    assert.equal(next.activity[0].severity, 'Warning');
    assert.match(next.activity[0].detail, /approve/i);
  });

  it('blocks manual handoff when the scheduled route is no longer available', () => {
    const base = createTestState();
    const approved = relateReducer(base, {
      type: 'approveMessage',
      messageId: 'msg-asha-bday',
      nowIso: '2026-07-09T10:00:00.000Z'
    });
    const state = {
      ...approved,
      settings: {
        ...approved.settings,
        smsEnabled: false
      }
    };
    const next = relateReducer(state, {
      type: 'manualHandoff',
      messageId: 'msg-asha-bday',
      nowIso: '2026-07-10T10:00:00.000Z'
    });
    const message = next.messages.find(item => item.id === 'msg-asha-bday');

    assert.equal(message?.status, 'Scheduled');
    assert.equal(message?.sentAt, undefined);
    assert.equal(next.activity[0].severity, 'Warning');
    assert.match(next.activity[0].detail, /SMS is disabled/i);
  });

  it('blocks manual handoff when the approval window expired', () => {
    const approved = relateReducer(createTestState(), {
      type: 'approveMessage',
      messageId: 'msg-mira-checkin',
      nowIso: '2026-07-01T10:00:00.000Z'
    });
    const next = relateReducer(approved, {
      type: 'manualHandoff',
      messageId: 'msg-mira-checkin',
      nowIso: '2026-07-09T10:00:00.000Z'
    });
    const message = next.messages.find(item => item.id === 'msg-mira-checkin');

    assert.equal(message?.status, 'Scheduled');
    assert.equal(message?.sentAt, undefined);
    assert.equal(next.activity[0].severity, 'Warning');
    assert.match(next.activity[0].detail, /expired/i);
  });

  it('tests a message route without approving or sending the draft', () => {
    const state = createTestState();
    const tested = relateReducer(state, {
      type: 'testMessageRoute',
      messageId: 'msg-asha-bday'
    });
    const message = tested.messages.find(item => item.id === 'msg-asha-bday');

    assert.equal(message?.status, 'Needs review');
    assert.equal(message?.sentAt, undefined);
    assert.equal(message?.approvedAt, undefined);
    assert.match(message?.readiness ?? '', /No message was sent/i);
    assert.equal(message?.lastError, undefined);
    assert.equal(tested.activity[0].title, 'Test send ready');
  });

  it('keeps failed route tests in review with actionable setup guidance', () => {
    const base = createTestState();
    const state = {
      ...base,
      settings: {
        ...base.settings,
        smsEnabled: false
      }
    };
    const tested = relateReducer(state, {
      type: 'testMessageRoute',
      messageId: 'msg-asha-bday'
    });
    const message = tested.messages.find(item => item.id === 'msg-asha-bday');

    assert.equal(message?.status, 'Needs review');
    assert.equal(message?.sentAt, undefined);
    assert.match(message?.lastError ?? '', /SMS is disabled/i);
    assert.equal(tested.activity[0].title, 'Test send blocked');
    assert.equal(tested.activity[0].severity, 'Warning');
  });

  it('toggles event preparation checklist items', () => {
    const state = createTestState();
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
    const state = createTestState();
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

  it('updates contact essentials only after profile validation passes', () => {
    const base = createTestState();
    const state = {
      ...base,
      messages: [
        {
          ...base.messages[0],
          id: 'msg-rajesh-stale',
          contactId: 'c-rajesh',
          eventId: 'e-rajesh-work',
          channel: 'Email' as const,
          status: 'Scheduled' as const,
          approvedAt: '2026-07-09T09:00:00.000Z',
          approvalExpiresAt: '2999-07-16T09:00:00.000Z',
          body: 'Congratulations Rajesh, wishing you continued success and a meaningful year ahead.'
        },
        ...base.messages
      ]
    };
    const invalid = relateReducer(state, {
      type: 'updateContactEssentials',
      contactId: 'c-rajesh',
      input: {
        name: 'Rajesh Nair',
        relationship: 'Manager',
        phone: '',
        email: 'not-an-email',
        language: 'English',
        notesSummary: 'Prefers concise professional notes.'
      }
    });
    const valid = relateReducer(invalid, {
      type: 'updateContactEssentials',
      contactId: 'c-rajesh',
      input: {
        name: '  Rajesh   Nair ',
        relationship: ' Mentor ',
        phone: '',
        email: ' RAJESH.NEW@EXAMPLE.COM ',
        language: 'English',
        notesSummary: '  Prefers short updates and no emoji. '
      }
    });
    const invalidContact = invalid.contacts.find(contact => contact.id === 'c-rajesh');
    const validContact = valid.contacts.find(contact => contact.id === 'c-rajesh');
    const invalidMessage = invalid.messages.find(message => message.id === 'msg-rajesh-stale');
    const validMessage = valid.messages.find(message => message.id === 'msg-rajesh-stale');

    assert.equal(invalidContact?.email, 'rajesh@example.com');
    assert.equal(invalidMessage?.status, 'Scheduled');
    assert.match(invalid.activity[0].title, /not saved/i);
    assert.equal(validContact?.name, 'Rajesh Nair');
    assert.equal(validContact?.relationship, 'Mentor');
    assert.equal(validContact?.email, 'rajesh.new@example.com');
    assert.equal(validContact?.annualGiftBudget, 2500);
    assert.equal(validMessage?.status, 'Needs review');
    assert.match(validMessage?.lastError ?? '', /profile or preferences changed/i);
    assert.match(valid.activity[0].title, /saved/i);
    assert.match(valid.activity[0].detail, /returned to review/i);
  });

  it('creates explicit encrypted backup snapshots', () => {
    const state = createTestState();
    const next = relateReducer(state, { type: 'createBackup' });

    assert.equal(next.backups.length, state.backups.length + 1);
    assert.equal(next.backups[0].encrypted, true);
    assert.ok(next.backups[0].recordCount > 0);
  });

  it('records analytics report export activity', () => {
    const state = createTestState();
    const next = relateReducer(state, { type: 'analyticsExported', rowCount: 12 });
    const summary = relateReducer(state, { type: 'analyticsExported', rowCount: 8, format: 'Summary' });

    assert.equal(next.activity[0].type, 'Analytics');
    assert.match(next.activity[0].detail, /12 redacted report row/);
    assert.match(summary.activity[0].detail, /8 redacted summary line/);
  });

  it('records message activity recovery targets for Activity History', () => {
    const state = createTestState();
    const approved = relateReducer(state, {
      type: 'approveMessage',
      messageId: 'msg-mira-checkin'
    });
    const activity = approved.activity[0];

    assert.equal(activity.targetScreen, 'wishPreview');
    assert.equal(activity.messageId, 'msg-mira-checkin');
    assert.equal(activity.contactId, 'c-mira');
    assert.equal(activity.actionLabel, 'Open approved message');
  });

  it('records redacted Setup Check dry-run activity without mutating work queues', () => {
    const state = createTestState();
    const next = relateReducer(state, {
      type: 'setupDoctorDryRunRecorded',
      detail: '4/9 checks ready. Next fix: Messages waiting for review. 2 blocker(s), 1 warning(s).'
    });

    assert.equal(next.messages, state.messages);
    assert.equal(next.reminderPlans, state.reminderPlans);
    assert.equal(next.activity[0].type, 'Setup');
    assert.equal(next.activity[0].title, 'Setup Check dry run completed');
    assert.equal(next.activity[0].targetScreen, 'more');
    assert.match(next.activity[0].detail, /4\/9 checks ready/);
  });

  it('restores backup state atomically through an explicit restore action', () => {
    const state = createTestState();
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
    const state = createTestState();
    const toggled = relateReducer(state, { type: 'toggleSetting', key: 'aiEnabled' });
    const unchanged = relateReducer(toggled, { type: 'toggleSetting', key: 'automationMode' });

    assert.equal(toggled.settings.aiEnabled, !state.settings.aiEnabled);
    assert.equal(unchanged.settings.automationMode, toggled.settings.automationMode);
  });

  it('flags unsent AI drafts for manual review when AI is disabled', () => {
    const base = createTestState();
    const state = {
      ...base,
      messages: [
        {
          ...base.messages[0],
          id: 'msg-ai-scheduled',
          status: 'Scheduled' as const,
          quality: 'AI draft' as const,
          readiness: 'Approved and scheduled'
        },
        {
          ...base.messages[0],
          id: 'msg-ai-draft',
          status: 'Draft' as const,
          quality: 'AI draft' as const,
          readiness: 'Draft'
        },
        {
          ...base.messages[0],
          id: 'msg-template-scheduled',
          status: 'Scheduled' as const,
          quality: 'Template fallback' as const,
          readiness: 'Approved and scheduled'
        },
        {
          ...base.messages[0],
          id: 'msg-ai-sent',
          status: 'Sent' as const,
          quality: 'AI draft' as const,
          sentAt: '2026-07-10T09:00:00.000Z',
          readiness: 'Sent'
        }
      ]
    };
    const disabled = relateReducer(state, { type: 'toggleSetting', key: 'aiEnabled' });
    const reenabled = relateReducer(disabled, { type: 'toggleSetting', key: 'aiEnabled' });
    const scheduled = disabled.messages.find(message => message.id === 'msg-ai-scheduled');
    const draft = disabled.messages.find(message => message.id === 'msg-ai-draft');
    const template = disabled.messages.find(message => message.id === 'msg-template-scheduled');
    const sent = disabled.messages.find(message => message.id === 'msg-ai-sent');

    assert.equal(disabled.settings.aiEnabled, false);
    assert.equal(scheduled?.status, 'Needs review');
    assert.equal(draft?.status, 'Needs review');
    assert.match(scheduled?.readiness ?? '', /AI was disabled/i);
    assert.match(scheduled?.lastError ?? '', /AI drafting was disabled/i);
    assert.equal(template?.status, 'Scheduled');
    assert.equal(sent?.status, 'Sent');
    assert.equal(disabled.activity[0].severity, 'Warning');
    assert.match(disabled.activity[0].detail, /2 unsent AI draft message\(s\) flagged for manual review/i);
    assert.equal(reenabled.messages.find(message => message.id === 'msg-ai-scheduled')?.status, 'Needs review');
  });

  it('clears notification reminder plans when notifications are disabled', () => {
    const base = createTestState();
    const state = {
      ...base,
      reminderPlans: [
        {
          id: 'reminder-one',
          eventId: base.events[0].id,
          contactId: base.events[0].contactId,
          title: 'Upcoming birthday',
          body: 'Prepare the message.',
          triggerAt: '2026-07-10T09:00:00.000Z'
        },
        {
          id: 'reminder-two',
          eventId: base.events[1].id,
          contactId: base.events[1].contactId,
          title: 'Upcoming check-in',
          body: 'Review the checklist.',
          triggerAt: '2026-07-11T09:00:00.000Z'
        }
      ]
    };
    const disabled = relateReducer(state, { type: 'toggleSetting', key: 'notificationsEnabled' });
    const reenabled = relateReducer(disabled, { type: 'toggleSetting', key: 'notificationsEnabled' });

    assert.equal(disabled.settings.notificationsEnabled, false);
    assert.equal(disabled.reminderPlans.length, 0);
    assert.equal(disabled.activity[0].severity, 'Warning');
    assert.match(disabled.activity[0].detail, /2 notification reminder plan\(s\) cleared/i);
    assert.match(disabled.activity[0].detail, /visible in-app/i);
    assert.equal(reenabled.settings.notificationsEnabled, true);
    assert.equal(reenabled.reminderPlans.length, 0);
  });

  it('clears notification reminder plans when notification permission becomes blocked', () => {
    const base = createTestState();
    const state = {
      ...base,
      reminderPlans: [
        {
          id: 'reminder-permission',
          eventId: base.events[0].id,
          contactId: base.events[0].contactId,
          title: 'Upcoming birthday',
          body: 'Prepare the message.',
          triggerAt: '2026-07-10T09:00:00.000Z'
        }
      ]
    };
    const denied = relateReducer(state, {
      type: 'recordPermissionDecision',
      capability: 'Notifications',
      decision: 'Denied'
    });
    const granted = relateReducer(denied, {
      type: 'recordPermissionDecision',
      capability: 'Notifications',
      decision: 'Granted'
    });

    assert.equal(denied.privacy.permissionDecisions.Notifications, 'Denied');
    assert.equal(denied.reminderPlans.length, 0);
    assert.equal(denied.activity[0].severity, 'Warning');
    assert.match(denied.activity[0].detail, /1 notification reminder plan\(s\) cleared/i);
    assert.match(denied.activity[0].detail, /visible in-app/i);
    assert.equal(granted.privacy.permissionDecisions.Notifications, 'Granted');
    assert.equal(granted.reminderPlans.length, 0);
  });

  it('returns scheduled messages to review when their delivery channel is disabled', () => {
    const base = createTestState();
    const state = {
      ...base,
      messages: [
        {
          ...base.messages[0],
          id: 'msg-scheduled-sms',
          status: 'Scheduled' as const,
          channel: 'SMS' as const,
          readiness: 'Approved and scheduled'
        },
        {
          ...base.messages[0],
          id: 'msg-scheduled-whatsapp',
          status: 'Scheduled' as const,
          channel: 'WhatsApp' as const,
          readiness: 'Approved and scheduled'
        }
      ]
    };
    const disabled = relateReducer(state, { type: 'toggleSetting', key: 'smsEnabled' });
    const reenabled = relateReducer(disabled, { type: 'toggleSetting', key: 'smsEnabled' });
    const sms = disabled.messages.find(message => message.id === 'msg-scheduled-sms');
    const whatsapp = disabled.messages.find(message => message.id === 'msg-scheduled-whatsapp');
    const stillReview = reenabled.messages.find(message => message.id === 'msg-scheduled-sms');

    assert.equal(disabled.settings.smsEnabled, false);
    assert.equal(sms?.status, 'Needs review');
    assert.match(sms?.readiness ?? '', /channel setting changed/i);
    assert.match(sms?.lastError ?? '', /SMS was disabled in Settings/i);
    assert.equal(whatsapp?.status, 'Scheduled');
    assert.equal(disabled.activity[0].severity, 'Warning');
    assert.match(disabled.activity[0].detail, /1 scheduled SMS message\(s\) returned to review/i);
    assert.equal(stillReview?.status, 'Needs review');
  });

  it('returns scheduled messages to review when schedule settings now block their timing', () => {
    const base = createTestState();
    const state = {
      ...base,
      settings: {
        ...base.settings,
        quietHours: { start: '23:00', end: '06:00' }
      },
      messages: [
        {
          ...base.messages[0],
          id: 'msg-quiet-conflict',
          status: 'Scheduled' as const,
          scheduledFor: '2026-07-10T22:30:00',
          readiness: 'Approved and scheduled'
        },
        {
          ...base.messages[0],
          id: 'msg-daytime-ok',
          status: 'Scheduled' as const,
          scheduledFor: '2026-07-10T12:30:00',
          readiness: 'Approved and scheduled'
        },
        {
          ...base.messages[0],
          id: 'msg-blackout-conflict',
          status: 'Scheduled' as const,
          scheduledFor: '2026-12-22T12:30:00',
          readiness: 'Approved and scheduled'
        }
      ]
    };
    const invalidQuiet = relateReducer(state, { type: 'setQuietHours', start: '10:00', end: '10:00' });
    const quiet = relateReducer(invalidQuiet, { type: 'setQuietHours', start: '21:00', end: '07:00' });
    const blackout = relateReducer(quiet, {
      type: 'addBlackout',
      label: 'Holiday',
      startDate: '2026-12-20',
      endDate: '2026-12-25'
    });
    const quietConflict = quiet.messages.find(message => message.id === 'msg-quiet-conflict');
    const daytime = quiet.messages.find(message => message.id === 'msg-daytime-ok');
    const blackoutConflict = blackout.messages.find(message => message.id === 'msg-blackout-conflict');

    assert.equal(invalidQuiet.messages.find(message => message.id === 'msg-quiet-conflict')?.status, 'Scheduled');
    assert.match(invalidQuiet.activity[0].title, /not saved/i);
    assert.equal(quietConflict?.status, 'Needs review');
    assert.match(quietConflict?.lastError ?? '', /Schedule settings changed/i);
    assert.match(quietConflict?.lastError ?? '', /quiet hours/i);
    assert.equal(daytime?.status, 'Scheduled');
    assert.equal(quiet.activity[0].severity, 'Warning');
    assert.match(quiet.activity[0].detail, /1 scheduled message\(s\) returned to review/i);
    assert.equal(blackoutConflict?.status, 'Needs review');
    assert.match(blackoutConflict?.lastError ?? '', /Holiday/i);
    assert.match(blackout.activity[0].detail, /1 scheduled message\(s\) returned to review/i);
  });

  it('returns scheduled messages to review when automation mode changes after queueing', () => {
    const base = createTestState();
    const state = {
      ...base,
      settings: {
        ...base.settings,
        automationMode: 'Always ask' as const
      },
      messages: [
        {
          ...base.messages[0],
          id: 'msg-queued-before-automation-change',
          status: 'Scheduled' as const,
          readiness: 'Approved and scheduled'
        },
        {
          ...base.messages[0],
          id: 'msg-sent-before-automation-change',
          status: 'Sent' as const,
          sentAt: '2026-07-10T09:00:00.000Z',
          readiness: 'Sent'
        }
      ]
    };
    const changed = relateReducer(state, { type: 'setAutomationMode', mode: 'VIP approve' });
    const repeated = relateReducer(changed, { type: 'setAutomationMode', mode: 'VIP approve' });
    const queued = changed.messages.find(message => message.id === 'msg-queued-before-automation-change');
    const sent = changed.messages.find(message => message.id === 'msg-sent-before-automation-change');

    assert.equal(changed.settings.automationMode, 'VIP approve');
    assert.equal(queued?.status, 'Needs review');
    assert.match(queued?.readiness ?? '', /automation mode changed/i);
    assert.match(queued?.lastError ?? '', /Automation mode changed/i);
    assert.equal(sent?.status, 'Sent');
    assert.equal(changed.activity[0].severity, 'Warning');
    assert.match(changed.activity[0].detail, /3 contact\(s\) changed effective automation mode/i);
    assert.match(changed.activity[0].detail, /1 scheduled message\(s\) returned to review/i);
    assert.equal(repeated.messages.find(message => message.id === 'msg-queued-before-automation-change')?.status, 'Needs review');
    assert.match(repeated.activity[0].detail, /Automation mode remains VIP approve/i);
    assert.doesNotMatch(repeated.activity[0].detail, /returned to review/i);
  });

  it('updates scheduling preferences with validation and review-safe planning recovery', () => {
    const state = createTestState();
    const automated = relateReducer(state, { type: 'setAutomationMode', mode: 'VIP approve' });
    const invalidQuiet = relateReducer(automated, { type: 'setQuietHours', start: '10:00', end: '10:00' });
    const quiet = relateReducer(invalidQuiet, { type: 'setQuietHours', start: '21:30', end: '07:30' });
    const invalidBlackout = relateReducer(quiet, {
      type: 'addBlackout',
      label: '',
      startDate: '2026-12-20',
      endDate: '2026-12-25'
    });
    const blackout = relateReducer(invalidBlackout, {
      type: 'addBlackout',
      label: 'Holiday',
      startDate: '2026-12-20',
      endDate: '2026-12-25'
    });
    const removed = relateReducer(blackout, {
      type: 'removeBlackout',
      blackoutId: blackout.settings.blackouts[0].id
    });
    const notificationsOff = relateReducer(
      {
        ...state,
        settings: {
          ...state.settings,
          notificationsEnabled: false
        }
      },
      { type: 'planReminders' }
    );

    assert.equal(automated.settings.automationMode, 'VIP approve');
    assert.equal(invalidQuiet.settings.quietHours.start, state.settings.quietHours.start);
    assert.match(invalidQuiet.activity[0].title, /not saved/i);
    assert.equal(quiet.settings.quietHours.start, '21:30');
    assert.equal(invalidBlackout.settings.blackouts.length, 0);
    assert.equal(blackout.settings.blackouts[0].label, 'Holiday');
    assert.equal(removed.settings.blackouts.length, 0);
    assert.equal(notificationsOff.reminderPlans.length, 0);
    assert.match(notificationsOff.activity[0].detail, /Notifications are off/i);
  });

  it('updates locale as an explicit user-controlled preference', () => {
    const state = createTestState();
    const next = relateReducer(state, { type: 'setLocale', locale: 'hi-IN' });

    assert.equal(next.settings.locale, 'hi-IN');
    assert.match(next.activity[0].detail, /hi-IN/);
  });

  it('stores email sender configuration and marks provider email sends complete', () => {
    const state = createTestState();
    const withEmailMessage = {
      ...state,
      settings: {
        ...state.settings,
        emailEnabled: true
      },
      messages: [
        {
          ...state.messages[0],
          id: 'msg-email-rajesh',
          contactId: 'c-rajesh',
          eventId: 'e-rajesh-work',
          channel: 'Email' as const,
          status: 'Scheduled' as const,
          approvedAt: '2026-07-09T09:00:00.000Z',
          approvalExpiresAt: '2999-07-16T09:00:00.000Z',
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

  it('marks provider email failures on the affected message for recovery', () => {
    const state = createTestState();
    const withEmailMessage = {
      ...state,
      messages: [
        {
          ...state.messages[0],
          id: 'msg-email-failed',
          contactId: 'c-rajesh',
          eventId: 'e-rajesh-work',
          channel: 'Email' as const,
          status: 'Scheduled' as const,
          body: 'Congratulations Rajesh, wishing you continued success and a meaningful year ahead.'
        },
        ...state.messages
      ]
    };
    const failed = relateReducer(withEmailMessage, {
      type: 'emailProviderFailure',
      messageId: 'msg-email-failed',
      error: {
        kind: 'network',
        message: 'Email provider timed out.'
      }
    });
    const message = failed.messages.find(item => item.id === 'msg-email-failed');

    assert.equal(message?.status, 'Failed');
    assert.match(message?.readiness ?? '', /recovery/i);
    assert.match(message?.lastError ?? '', /timed out/i);
    assert.equal(failed.emailDelivery.status, 'Error');
  });

  it('keeps an accepted provider email pending until delivery is confirmed', () => {
    const state = createTestState();
    const withEmailMessage = {
      ...state,
      messages: [
        {
          ...state.messages[0],
          id: 'msg-email-accepted',
          channel: 'Email' as const,
          status: 'Scheduled' as const
        },
        ...state.messages
      ]
    };
    const accepted = relateReducer(withEmailMessage, {
      type: 'emailDeliveryAccepted',
      messageId: 'msg-email-accepted',
      idempotencyKey: 'attempt-accepted',
      deliveryId: 'delivery-accepted'
    });
    const message = accepted.messages.find(item => item.id === 'msg-email-accepted');

    assert.equal(message?.status, 'Delivery pending');
    assert.equal(message?.emailDeliveryAttempt?.status, 'Accepted');
    assert.equal(message?.emailDeliveryAttempt?.idempotencyKey, 'attempt-accepted');
    assert.equal(message?.sentAt, undefined);
  });

  it('blocks retries when provider delivery status is unknown', () => {
    const state = createTestState();
    const withEmailMessage = {
      ...state,
      messages: [
        {
          ...state.messages[0],
          id: 'msg-email-unknown',
          channel: 'Email' as const,
          status: 'Scheduled' as const
        },
        ...state.messages
      ]
    };
    const unknown = relateReducer(withEmailMessage, {
      type: 'emailDeliveryUnknown',
      messageId: 'msg-email-unknown',
      idempotencyKey: 'attempt-unknown',
      error: {
        kind: 'delivery-unknown',
        message: 'Provider result was lost; do not retry.'
      }
    });
    const message = unknown.messages.find(item => item.id === 'msg-email-unknown');
    const retried = relateReducer(unknown, { type: 'retryMessage', messageId: 'msg-email-unknown' });

    assert.equal(message?.status, 'Delivery unknown');
    assert.equal(message?.emailDeliveryAttempt?.status, 'Unknown');
    assert.equal(retried.messages.find(item => item.id === 'msg-email-unknown')?.status, 'Delivery unknown');
  });

  it('does not record stale email success when the message is no longer send-ready', () => {
    const state = createTestState();
    const withEmailMessage = {
      ...state,
      messages: [
        {
          ...state.messages[0],
          id: 'msg-email-stale-success',
          contactId: 'c-rajesh',
          eventId: 'e-rajesh-work',
          channel: 'Email' as const,
          status: 'Needs review' as const,
          body: 'Congratulations Rajesh, wishing you continued success and a meaningful year ahead.'
        },
        ...state.messages
      ]
    };
    const sent = relateReducer(withEmailMessage, {
      type: 'emailSent',
      messageId: 'msg-email-stale-success'
    });
    const message = sent.messages.find(item => item.id === 'msg-email-stale-success');

    assert.equal(message?.status, 'Needs review');
    assert.equal(message?.sentAt, undefined);
    assert.equal(sent.emailDelivery.status, 'Error');
    assert.match(sent.activity[0].title, /not recorded/i);
  });

  it('does not record stale email success after approval expires', () => {
    const state = createTestState();
    const withEmailMessage = {
      ...state,
      settings: {
        ...state.settings,
        emailEnabled: true
      },
      emailDelivery: {
        ...state.emailDelivery,
        senderEmail: 'me@example.com'
      },
      messages: [
        {
          ...state.messages[0],
          id: 'msg-email-expired-success',
          contactId: 'c-rajesh',
          eventId: 'e-rajesh-work',
          channel: 'Email' as const,
          status: 'Scheduled' as const,
          approvedAt: '2026-07-01T09:00:00.000Z',
          approvalExpiresAt: '2026-07-02T09:00:00.000Z',
          body: 'Congratulations Rajesh, wishing you continued success and a meaningful year ahead.'
        },
        ...state.messages
      ]
    };
    const sent = relateReducer(withEmailMessage, {
      type: 'emailSent',
      messageId: 'msg-email-expired-success'
    });
    const message = sent.messages.find(item => item.id === 'msg-email-expired-success');

    assert.equal(message?.status, 'Scheduled');
    assert.equal(message?.sentAt, undefined);
    assert.equal(sent.emailDelivery.status, 'Error');
    assert.match(sent.emailDelivery.lastError ?? '', /expired/i);
  });
});
