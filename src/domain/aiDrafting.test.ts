import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { buildAiDraftRequest, classifyAiProviderStatus, normalizeAiDraftResponse } from './aiDrafting';
import { createTestState } from '../test/testState';

describe('aiDrafting contract', () => {
  it('builds provider requests from approved context and excludes private/contact routing data', () => {
    const state = createTestState();
    const result = buildAiDraftRequest(state, 'c-rajesh', 'e-rajesh-work', 'Congratulations');

    assert.equal(result.ok, true);
    if (!result.ok) {
      return;
    }

    const serialized = JSON.stringify(result.request);

    assert.equal(result.request.privacy.excludedPrivateMemoryCount, 1);
    assert.deepEqual(result.request.memories, []);
    assert.match(result.privacySummary, /private item\(s\) excluded/);
    assert.doesNotMatch(serialized, /rajesh@example\.com/i);
    assert.doesNotMatch(serialized, /Private note excluded/i);
    assert.doesNotMatch(serialized, /\+91/);
  });

  it('rejects an event that belongs to a different contact', () => {
    const result = buildAiDraftRequest(createTestState(), 'c-asha', 'e-rajesh-work', 'Birthday');

    assert.equal(result.ok, false);
    if (!result.ok) {
      assert.equal(result.error.kind, 'missing-event');
      assert.match(result.error.message, /does not belong/i);
    }
  });

  it('excludes the learned global profile when Style Coach use is disabled', () => {
    const state = createTestState();
    state.styleProfile = {
      ...state.styleProfile,
      enabledForAiDrafts: false,
      formality: 'PRIVATE_STYLE_FORMALITY',
      language: 'PRIVATE_STYLE_LANGUAGE',
      emojiUse: 'PRIVATE_STYLE_EMOJI',
      commonGreetings: ['PRIVATE_STYLE_GREETING'],
      representativePreview: 'PRIVATE_STYLE_PREVIEW'
    };

    const result = buildAiDraftRequest(state, 'c-asha', 'e-asha-bday', 'Birthday');

    assert.equal(result.ok, true);
    if (!result.ok) return;
    assert.equal(result.request.style.enabled, false);
    assert.deepEqual(result.request.style.commonGreetings, []);
    assert.doesNotMatch(JSON.stringify(result.request), /PRIVATE_STYLE/);
  });

  it('includes only small privacy-filtered structured gift history for the selected contact', () => {
    const state = createTestState();
    state.contacts = state.contacts.map(contact =>
      contact.id === 'c-asha' ? { ...contact, annualGiftBudget: 424242 } : contact
    );
    state.gifts = [
      {
        id: 'gift-sensitive',
        contactId: 'c-asha',
        name: 'API key secret-token-should-not-leave-device',
        category: 'Other',
        occasion: 'Birthday',
        cost: 919191,
        year: 2026,
        feedback: 'Unknown',
        notes: 'PRIVATE_GIFT_NOTE_2026'
      },
      {
        id: 'gift-private-context',
        contactId: 'c-asha',
        name: 'Secret family detail',
        category: 'Personal',
        occasion: 'Birthday',
        cost: 818181,
        year: 2025,
        feedback: 'Unknown',
        notes: 'PRIVATE_GIFT_NOTE_2025'
      },
      ...[2024, 2023, 2022, 2021].map(year => ({
        id: `gift-safe-${year}`,
        contactId: 'c-asha',
        name: `Safe gift ${year}`,
        category: 'Books' as const,
        occasion: 'Birthday',
        cost: year,
        year,
        feedback: 'Liked' as const,
        notes: `PRIVATE_GIFT_NOTE_${year}`
      })),
      {
        id: 'gift-other-contact',
        contactId: 'c-rajesh',
        name: 'Other contact gift',
        category: 'Other',
        occasion: 'Work anniversary',
        cost: 777777,
        year: 2025,
        feedback: 'Disliked',
        notes: 'OTHER_CONTACT_PRIVATE_GIFT_NOTE'
      }
    ];

    const result = buildAiDraftRequest(state, 'c-asha', 'e-asha-bday', 'Birthday');

    assert.equal(result.ok, true);
    if (!result.ok) {
      return;
    }

    assert.deepEqual(result.request.giftHistory, [
      {
        name: 'Safe gift 2024',
        category: 'Books',
        occasion: 'Birthday',
        year: 2024,
        feedback: 'Liked'
      },
      {
        name: 'Safe gift 2023',
        category: 'Books',
        occasion: 'Birthday',
        year: 2023,
        feedback: 'Liked'
      },
      {
        name: 'Safe gift 2022',
        category: 'Books',
        occasion: 'Birthday',
        year: 2022,
        feedback: 'Liked'
      }
    ]);
    assert.equal(result.request.privacy.includedGiftHistoryCount, 3);
    assert.equal(result.request.privacy.excludedGiftHistoryCount, 3);
    assert.equal(result.request.privacy.excludedSensitiveGiftHistoryCount, 2);
    assert.equal(result.request.outputContract.mustRequireUserReview, true);
    assert.match(result.privacySummary, /3 bounded gift history item\(s\) included/i);
    assert.ok(result.request.privacy.excludedFields.includes('gift notes'));
    assert.ok(result.request.privacy.excludedFields.includes('gift cost'));
    assert.ok(result.request.privacy.excludedFields.includes('annual gift budget'));
    assert.ok(result.request.privacy.excludedFields.includes('activity and diagnostic logs'));

    const serialized = JSON.stringify(result.request);
    assert.doesNotMatch(
      serialized,
      /PRIVATE_GIFT_NOTE|919191|818181|424242|secret-token|Secret family detail|Other contact gift|777777/i
    );
  });

  it('separates mentionable memory facts from bounded generation-only guidance', () => {
    const base = createTestState();
    const state = {
      ...base,
      contacts: base.contacts.map(contact =>
        contact.id === 'c-asha'
          ? {
              ...contact,
              notesSummary: 'Preferred language/style: do not quote this instruction.'
            }
          : contact
      ),
      memories: [
        {
          ...base.memories[0],
          id: 'fact',
          category: 'Milestone' as const,
          body: 'Finished a first marathon in May.'
        },
        {
          ...base.memories[0],
          id: 'avoid',
          body: 'Avoid in messages: office gossip.'
        },
        {
          ...base.memories[0],
          id: 'language',
          body: 'Preferred language/style: formal Hindi.'
        },
        {
          ...base.memories[0],
          id: 'tone',
          body: 'Preferred tone: respectful and no emoji.'
        },
        {
          ...base.memories[0],
          id: 'private',
          category: 'Private' as const,
          body: 'Secret family detail.'
        }
      ]
    };
    const result = buildAiDraftRequest(state, 'c-asha', 'e-asha-bday', 'Birthday');

    assert.equal(result.ok, true);
    if (!result.ok) {
      return;
    }

    assert.deepEqual(result.request.memories, [
      {
        category: 'Milestone',
        body: 'Finished a first marathon in May.'
      }
    ]);
    assert.deepEqual(
      result.request.generationConstraints.map(constraint => constraint.kind),
      ['avoid', 'language-style', 'tone-style']
    );
    assert.equal(result.request.contact.notesSummary, '');
    assert.equal(result.request.privacy.includedGenerationConstraintCount, 3);
    assert.equal(result.request.privacy.excludedPrivateMemoryCount, 1);
    assert.doesNotMatch(
      JSON.stringify(result.request.memories),
      /Avoid in messages|office gossip|Preferred language|formal Hindi|Preferred tone|no emoji/i
    );
    assert.doesNotMatch(JSON.stringify(result.request), /Secret family detail/i);
  });

  it('includes bounded regeneration feedback in provider requests', () => {
    const state = createTestState();
    const result = buildAiDraftRequest(state, 'c-asha', 'e-asha-bday', 'Birthday', {
      feedback: {
        instructions: ['Make the draft shorter and easier to send.'],
        customInstruction: 'Mention mango lassi, but keep it natural.',
        previousDraftExcerpt: 'Happy birthday Asha! This older draft should guide the rewrite.'
      }
    });

    assert.equal(result.ok, true);
    if (!result.ok) {
      return;
    }

    assert.deepEqual(result.request.regenerationFeedback?.instructions, ['Make the draft shorter and easier to send.']);
    assert.equal(result.request.regenerationFeedback?.customInstruction, 'Mention mango lassi, but keep it natural.');
    assert.match(result.request.regenerationFeedback?.previousDraftExcerpt ?? '', /older draft/i);
  });

  it('excludes routing and credential content from prior wishes, feedback, and event labels', () => {
    const state = createTestState();
    state.events = state.events.map(event =>
      event.id === 'e-asha-bday' ? { ...event, label: 'Birthday details at https://private.example.test' } : event
    );
    state.messages.push({
      ...state.messages[0],
      id: 'sent-sensitive-context',
      status: 'Sent',
      body: 'Previous wish included phone +91 99887 76655 and must not be provider context.',
      sentAt: '2025-01-01T00:00:00.000Z'
    });

    const result = buildAiDraftRequest(state, 'c-asha', 'e-asha-bday', 'Birthday', {
      feedback: {
        instructions: ['Make it shorter.', 'Use access token abcdefghijklmnop.'],
        customInstruction: 'Keep the tone warm.',
        previousDraftExcerpt: 'Old route was mailto:private@example.test.'
      }
    });

    assert.equal(result.ok, true);
    if (!result.ok) {
      return;
    }

    assert.equal(result.request.event?.label, 'Birthday');
    assert.deepEqual(result.request.priorApprovedMessages, []);
    assert.deepEqual(result.request.regenerationFeedback, {
      instructions: ['Make it shorter.'],
      customInstruction: 'Keep the tone warm.',
      previousDraftExcerpt: undefined
    });
    assert.equal(result.request.privacy.excludedSensitivePriorMessageCount, 1);
    assert.equal(result.request.privacy.excludedSensitiveFeedbackCount, 2);
    assert.doesNotMatch(JSON.stringify(result.request), /99887|private\.example|access token|abcdefghijklmnop|mailto/i);
  });

  it('normalizes complete provider JSON variants and rejects blank or malformed responses', () => {
    const valid = normalizeAiDraftResponse({
      variants: {
        short: 'Happy birthday! Hope your day feels genuinely special.',
        standard: 'Happy birthday Asha! Wishing you a warm day and a year full of good moments.',
        warm: 'Happy birthday Asha! I hope the day feels personal, easy, and full of people who love you.'
      }
    });
    const blank = normalizeAiDraftResponse({
      variants: {
        short: '   ',
        standard: 'A usable standard draft is present.',
        warm: 'A usable warm draft is present too.'
      }
    });
    const malformed = normalizeAiDraftResponse({
      variants: {
        short: 'A usable short draft is present.',
        standard: 'A usable standard draft is present.'
      }
    });

    assert.equal(valid.ok, true);
    assert.equal(blank.ok, false);
    assert.equal(malformed.ok, false);
    if (!blank.ok) {
      assert.equal(blank.error.kind, 'invalid-response');
    }
    if (!malformed.ok) {
      assert.equal(malformed.error.kind, 'invalid-response');
    }
  });

  it('rejects unresolved placeholders and identical variants as low-quality provider output', () => {
    const placeholder = normalizeAiDraftResponse({
      variants: {
        short: 'Happy birthday [name]! Hope your day feels special.',
        standard: 'Happy birthday Asha! Wishing you a joyful day and a thoughtful year ahead.',
        warm: 'Happy birthday Asha! Hope today feels personal, warm, and full of love.'
      }
    });
    const repeatedVariant = 'Happy birthday Asha! Wishing you a joyful and thoughtful year ahead.';
    const identical = normalizeAiDraftResponse({
      variants: {
        short: repeatedVariant,
        standard: repeatedVariant,
        warm: repeatedVariant
      }
    });

    assert.equal(placeholder.ok, false);
    assert.equal(identical.ok, false);
    if (!placeholder.ok) assert.equal(placeholder.error.kind, 'invalid-response');
    if (!identical.ok) assert.equal(identical.error.kind, 'invalid-response');
  });

  it('rejects route or credential leaks and close repetition before draft creation', () => {
    const routeLeak = normalizeAiDraftResponse({
      variants: {
        short: 'Happy birthday! Call me at +91 98765 43210 today.',
        standard: 'Happy birthday! Wishing you a thoughtful and joyful day.',
        warm: 'Happy birthday! Hope today feels personal, warm, and full of love.'
      }
    });
    const credentialLeak = normalizeAiDraftResponse({
      variants: {
        short: 'Happy birthday! Hope your day feels genuinely special.',
        standard: 'Happy birthday! Use this access token before the celebration starts.',
        warm: 'Happy birthday! Hope today feels personal, warm, and full of love.'
      }
    });
    const repeated = normalizeAiDraftResponse(
      {
        variants: {
          short: 'Happy birthday Asha! Wishing you a warm day and a year full of truly good moments.',
          standard: 'Happy birthday Asha! Wishing you a warm day and a year full of really good moments.',
          warm: 'Happy birthday Asha! Wishing you a warm day and a year full of many good moments.'
        }
      },
      {
        previousMessages: ['Happy birthday Asha! Wishing you a warm day and a year full of good moments.']
      }
    );

    assert.equal(routeLeak.ok, false);
    assert.equal(credentialLeak.ok, false);
    assert.equal(repeated.ok, false);
    if (!routeLeak.ok) {
      assert.equal(routeLeak.error.kind, 'content-safety');
      assert.doesNotMatch(routeLeak.error.message, /98765|call me/i);
    }
    if (!credentialLeak.ok) {
      assert.equal(credentialLeak.error.kind, 'content-safety');
      assert.doesNotMatch(credentialLeak.error.message, /access token/i);
    }
    if (!repeated.ok) {
      assert.equal(repeated.error.kind, 'content-safety');
    }
  });

  it('rejects wrong-language and clearly inappropriate output without echoing provider content', () => {
    const wrongLanguage = normalizeAiDraftResponse(
      {
        variants: {
          short: 'Happy birthday Asha! Hope your day is special.',
          standard: 'Happy birthday Asha! Wishing you a joyful day and a thoughtful year ahead.',
          warm: 'Happy birthday Asha! Hope today feels personal, warm, and full of love.'
        }
      },
      {
        expectedLanguage: 'Hindi'
      }
    );
    const inappropriate = normalizeAiDraftResponse({
      variants: {
        short: 'Happy birthday, and I hope you die before the day is over.',
        standard: 'Happy birthday! Wishing you a personal, joyful day and a thoughtful year ahead.',
        warm: 'Happy birthday! Hope today feels personal, warm, and full of love.'
      }
    });

    assert.equal(wrongLanguage.ok, false);
    assert.equal(inappropriate.ok, false);
    if (!wrongLanguage.ok) {
      assert.equal(wrongLanguage.error.kind, 'wrong-language');
    }
    if (!inappropriate.ok) {
      assert.equal(inappropriate.error.kind, 'content-safety');
      assert.doesNotMatch(inappropriate.error.message, /hope you die/i);
    }
  });

  it('distinguishes English, Hindi, and Hinglish provider drafts', () => {
    const english = normalizeAiDraftResponse(
      {
        variants: {
          short: 'Happy birthday Asha! Hope you have a wonderful day.',
          standard: 'Happy birthday Asha! Wishing you a joyful day and a thoughtful year ahead.',
          warm: 'Happy birthday Asha! I hope today feels warm, personal, and full of love.'
        }
      },
      { expectedLanguage: 'English' }
    );
    const hindi = normalizeAiDraftResponse(
      {
        variants: {
          short: 'जन्मदिन मुबारक हो, आशा! आज का दिन बहुत खुशनुमा हो।',
          standard: 'जन्मदिन मुबारक हो, आशा! आपका दिन खुशियों से भरा हो और आने वाला साल सुकून लाए।',
          warm: 'प्यारी आशा, जन्मदिन की ढेरों शुभकामनाएँ। आपकी ज़िंदगी प्यार, स्वास्थ्य और मुस्कान से भरी रहे।'
        }
      },
      { expectedLanguage: 'Hindi' }
    );
    const hinglish = normalizeAiDraftResponse(
      {
        variants: {
          short: 'Happy birthday Asha! Aaj ka din bahut special ho.',
          standard: 'Happy birthday Asha! Umeed hai aaj ka din smiles aur lovely moments se bhara rahe.',
          warm: 'Asha, dil se happy birthday! Aapka aane wala year khushi aur warmth se bhara rahe.'
        }
      },
      { expectedLanguage: 'Hinglish' }
    );
    const plainEnglishForHinglish = normalizeAiDraftResponse(
      {
        variants: {
          short: 'Happy birthday Asha! Hope you have a wonderful day.',
          standard: 'Happy birthday Asha! Wishing you a joyful day and a thoughtful year ahead.',
          warm: 'Happy birthday Asha! I hope today feels warm, personal, and full of love.'
        }
      },
      { expectedLanguage: 'Hinglish' }
    );
    const hinglishForEnglish = normalizeAiDraftResponse(
      {
        variants: {
          short: 'Happy birthday Asha! Aaj ka din bahut special ho.',
          standard: 'Happy birthday Asha! Umeed hai aaj ka din smiles aur lovely moments se bhara rahe.',
          warm: 'Asha, dil se happy birthday! Aapka aane wala year khushi aur warmth se bhara rahe.'
        }
      },
      { expectedLanguage: 'English' }
    );

    assert.equal(english.ok, true);
    assert.equal(hindi.ok, true);
    assert.equal(hinglish.ok, true);
    assert.equal(plainEnglishForHinglish.ok, false);
    assert.equal(hinglishForEnglish.ok, false);
    if (!plainEnglishForHinglish.ok) assert.equal(plainEnglishForHinglish.error.kind, 'wrong-language');
    if (!hinglishForEnglish.ok) assert.equal(hinglishForEnglish.error.kind, 'wrong-language');
  });

  it('classifies actionable provider failures', () => {
    assert.equal(classifyAiProviderStatus(401).kind, 'auth');
    assert.equal(classifyAiProviderStatus(429).kind, 'quota');
    assert.equal(classifyAiProviderStatus(503).kind, 'server');
    assert.equal(classifyAiProviderStatus(418).kind, 'network');
  });
});
