import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createInitialState, relateReducer } from '../state/relateReducer';
import { detectDuplicateMessageRisk } from './duplicateGuard';

describe('duplicate send guardrail', () => {
  it('detects already scheduled messages for the same contact and event', () => {
    const state = createInitialState();
    const draft = relateReducer(state, {
      type: 'generateMessage',
      contactId: 'c-asha',
      eventId: 'e-asha-bday',
      reason: 'Birthday'
    }).messages[0];
    const risk = detectDuplicateMessageRisk(state, draft);

    assert.equal(risk.risk, true);
    if (risk.risk) {
      assert.equal(risk.severity, 'Draft');
      assert.match(risk.message, /similar message draft/i);
    }
  });

  it('blocks approval until duplicate risk is explicitly acknowledged', () => {
    const generated = relateReducer(createInitialState(), {
      type: 'generateMessage',
      contactId: 'c-asha',
      eventId: 'e-asha-bday',
      reason: 'Birthday'
    });
    const draftId = generated.messages[0].id;
    const blocked = relateReducer(generated, {
      type: 'approveMessage',
      messageId: draftId
    });
    const acknowledged = relateReducer(blocked, {
      type: 'acknowledgeDuplicateRisk',
      messageId: draftId
    });
    const approved = relateReducer(acknowledged, {
      type: 'approveMessage',
      messageId: draftId
    });

    assert.equal(blocked.messages[0].status, 'Blocked');
    assert.match(blocked.messages[0].lastError ?? '', /similar message draft/i);
    assert.equal(acknowledged.messages[0].status, 'Needs review');
    assert.equal(approved.messages[0].status, 'Scheduled');
  });

  it('detects similar sent messages for the same manual occasion', () => {
    const sent = relateReducer(createInitialState(), {
      type: 'manualHandoff',
      messageId: 'msg-mira-checkin',
      nowIso: '2026-07-09T10:00:00.000Z'
    });
    const generated = relateReducer(sent, {
      type: 'createTemplateDraft',
      contactId: 'c-mira',
      reason: 'Check-in',
      body: 'Hey Mira, how is Pune treating you so far? Hope the new role is starting well.'
    });
    const risk = detectDuplicateMessageRisk(generated, generated.messages[0]);

    assert.equal(risk.risk, true);
    if (risk.risk) {
      assert.equal(risk.severity, 'Sent');
      assert.match(risk.message, /already sent/i);
    }
  });
});
