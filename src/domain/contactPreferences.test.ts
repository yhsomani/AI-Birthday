import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { buildAiDraftRequest } from './aiDrafting';
import { buildTemplateDraft } from './messageTemplates';
import {
  normalizeRelationshipGroupDefaults,
  resolveContactPreferences,
  resolveContactPreferencesForContact
} from './contactPreferences';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';

describe('contact group preference contract', () => {
  it('keeps existing contacts explicit until they opt into group defaults', () => {
    const state = createTestState();
    const contact = state.contacts.find(item => item.id === 'c-mira')!;
    const preferences = resolveContactPreferencesForContact(state.settings, contact);

    assert.equal(preferences.preferredChannel, 'Manual');
    assert.equal(preferences.checkInCadenceDays, 45);
    assert.equal(preferences.automationMode, 'Always ask');
    assert.equal(preferences.sources.preferredChannel, 'contact');
    assert.equal(preferences.sources.automationMode, 'global');
  });

  it('resolves inherited group defaults and validates invalid default values', () => {
    const state = createTestState();
    const defaults = normalizeRelationshipGroupDefaults({
      'Close friends': {
        preferredChannel: 'Email',
        tone: ['No emoji'],
        checkInCadenceDays: 14,
        automationMode: 'Smart approve'
      },
      Work: {
        preferredChannel: 'Unsupported' as never,
        tone: ['Unsupported tone' as never],
        checkInCadenceDays: 999,
        automationMode: 'Unsupported mode' as never
      }
    });
    const contact = {
      ...state.contacts.find(item => item.id === 'c-mira')!,
      preferenceOverrides: {}
    };
    const preferences = resolveContactPreferencesForContact(
      {
        ...state.settings,
        groupDefaults: defaults
      },
      contact
    );

    assert.equal(preferences.preferredChannel, 'Email');
    assert.deepEqual(preferences.tone, ['No emoji']);
    assert.equal(preferences.checkInCadenceDays, 14);
    assert.equal(preferences.sources.tone, 'group');
    assert.equal(defaults.Work.preferredChannel, 'Email');
    assert.deepEqual(defaults.Work.tone, ['Respectful', 'Formal', 'Concise']);
    assert.equal(defaults.Work.checkInCadenceDays, 60);
  });

  it('lets contacts inherit group defaults and then override individual preferences', () => {
    const inherited = relateReducer(createTestState(), {
      type: 'useGroupDefaultsForContact',
      contactId: 'c-mira'
    });
    const channelOverride = relateReducer(inherited, {
      type: 'setContactChannel',
      contactId: 'c-mira',
      channel: 'Manual'
    });
    const automationOverride = relateReducer(channelOverride, {
      type: 'setContactAutomationMode',
      contactId: 'c-mira',
      mode: 'Always ask'
    });
    const preferences = resolveContactPreferences(automationOverride, 'c-mira')!;

    assert.equal(resolveContactPreferences(inherited, 'c-mira')?.preferredChannel, 'WhatsApp');
    assert.equal(preferences.preferredChannel, 'Manual');
    assert.equal(preferences.checkInCadenceDays, 30);
    assert.equal(preferences.automationMode, 'Always ask');
    assert.equal(preferences.sources.preferredChannel, 'contact');
    assert.equal(preferences.sources.checkInCadenceDays, 'group');
  });

  it('syncs group default changes to inheriting contacts without changing explicit contacts', () => {
    const inherited = relateReducer(createTestState(), {
      type: 'useGroupDefaultsForContact',
      contactId: 'c-mira'
    });
    const updated = relateReducer(inherited, {
      type: 'setRelationshipGroupDefault',
      group: 'Close friends',
      defaults: {
        preferredChannel: 'Manual',
        checkInCadenceDays: 14,
        automationMode: 'VIP approve'
      }
    });
    const mira = updated.contacts.find(contact => contact.id === 'c-mira')!;
    const asha = updated.contacts.find(contact => contact.id === 'c-asha')!;
    const preferences = resolveContactPreferences(updated, 'c-mira')!;

    assert.equal(mira.preferredChannel, 'Manual');
    assert.equal(mira.checkInCadenceDays, 14);
    assert.equal(preferences.automationMode, 'VIP approve');
    assert.equal(asha.preferredChannel, 'SMS');
    assert.equal(asha.checkInCadenceDays, 30);
  });

  it('uses effective group preferences for AI and template draft channels', () => {
    const state = relateReducer(createTestState(), {
      type: 'useGroupDefaultsForContact',
      contactId: 'c-mira'
    });
    const aiRequest = buildAiDraftRequest(state, 'c-mira', 'e-mira-checkin', 'Check-in');
    const template = buildTemplateDraft(
      state,
      {
        contactId: 'c-mira',
        reason: 'Check-in',
        body: 'Hi Mira, just checking in and hoping Pune is feeling easier this week.'
      },
      123
    );

    assert.equal(aiRequest.ok, true);
    if (aiRequest.ok) {
      assert.equal(aiRequest.request.contact.preferredChannel, 'WhatsApp');
      assert.deepEqual(aiRequest.request.contact.tone, ['Warm', 'Playful']);
    }
    assert.equal(template.ok, true);
    if (template.ok) {
      assert.equal(template.draft.channel, 'WhatsApp');
    }
  });
});
