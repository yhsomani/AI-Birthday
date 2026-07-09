import type { AppState, ComposerReason, Contact, MessageDraft, Tone } from './types';

export type MessageTemplate = {
  id: string;
  reason: ComposerReason;
  tone: Tone;
  title: string;
  body: string;
};

export type TemplateDraftInput = {
  contactId: string;
  reason: ComposerReason;
  body: string;
  templateId?: string;
};

export type TemplateDraftResult =
  | {
      ok: true;
      draft: MessageDraft;
    }
  | {
      ok: false;
      reason: string;
    };

export const messageTemplates: MessageTemplate[] = [
  {
    id: 'birthday-warm',
    reason: 'Birthday',
    tone: 'Warm',
    title: 'Warm birthday wish',
    body: 'Happy birthday {{name}}! Wishing you a day that feels easy, joyful, and full of people who care about you. Hope the year ahead brings good health, calm moments, and plenty to smile about.'
  },
  {
    id: 'birthday-hinglish',
    reason: 'Birthday',
    tone: 'Hinglish',
    title: 'Hinglish birthday wish',
    body: 'Happy birthday {{name}}! Aaj ka din bahut special ho, full of smiles, good food, and people who love you. Wishing you a lovely year ahead.'
  },
  {
    id: 'checkin-concise',
    reason: 'Check-in',
    tone: 'Concise',
    title: 'Short check-in',
    body: 'Hi {{name}}, just checking in. Hope you are doing well. No rush to reply, but I was thinking of you.'
  },
  {
    id: 'thanks-warm',
    reason: 'Thanks',
    tone: 'Warm',
    title: 'Thank you note',
    body: 'Hi {{name}}, thank you for being there. I really appreciate your time, kindness, and the way you show up.'
  },
  {
    id: 'congrats-respectful',
    reason: 'Congratulations',
    tone: 'Respectful',
    title: 'Respectful congratulations',
    body: 'Congratulations {{name}}. This is a meaningful milestone, and I am genuinely happy to see your effort recognized. Wishing you continued success.'
  },
  {
    id: 'apology-formal',
    reason: 'Apology',
    tone: 'Formal',
    title: 'Clear apology',
    body: 'Hi {{name}}, I wanted to apologize properly. I understand this mattered, and I am sorry for my part in it. I value our relationship and would like to make this right.'
  },
  {
    id: 'followup-warm',
    reason: 'Follow-up',
    tone: 'Warm',
    title: 'Gentle follow-up',
    body: 'Hi {{name}}, I wanted to follow up and see how things went. Hope it all turned out well.'
  },
  {
    id: 'custom-warm',
    reason: 'Custom',
    tone: 'Warm',
    title: 'Open warm note',
    body: 'Hi {{name}}, I was thinking of you and wanted to send a quick note. {{context}}'
  }
];

const normalize = (value: string) => value.trim().replace(/\s+/g, ' ');

const contextForContact = (state: AppState, contactId: string) =>
  state.memories
    .filter(memory => memory.contactId === contactId && memory.category !== 'Private')
    .map(memory => memory.body)
    .find(Boolean);

export const findMessageTemplates = (
  reason: ComposerReason,
  tones: Tone[] = []
) => {
  const matches = messageTemplates.filter(template => template.reason === reason);
  const toneMatches = matches.filter(template => tones.includes(template.tone));
  return toneMatches.length > 0 ? toneMatches : matches;
};

export const renderMessageTemplate = (
  template: MessageTemplate,
  contact: Contact,
  context?: string
) => {
  const safeContext = normalize(context ?? contact.notesSummary ?? '');
  return normalize(
    template.body
      .replaceAll('{{name}}', contact.name)
      .replaceAll('{{relationship}}', contact.relationship)
      .replaceAll('{{context}}', safeContext ? `I remembered: ${safeContext}` : '')
  );
};

export const buildTemplateDraft = (
  state: AppState,
  input: TemplateDraftInput,
  nowMs = Date.now()
): TemplateDraftResult => {
  const contact = state.contacts.find(item => item.id === input.contactId);
  if (!contact) {
    return { ok: false, reason: 'The selected contact could not be found.' };
  }

  const body = normalize(input.body);
  if (body.length < 12) {
    return { ok: false, reason: 'Write a longer template message before creating a draft.' };
  }

  const duplicate = state.messages.find(
    message => message.contactId === contact.id && message.eventId === undefined && message.status !== 'Rejected'
  );
  const short = body.length > 88 ? `${body.slice(0, 85).trimEnd()}...` : body;
  const warm = body.endsWith('.') ? `${body} Hope this feels personal.` : `${body}. Hope this feels personal.`;

  return {
    ok: true,
    draft: {
      id: `template-${nowMs}-${state.messages.length}`,
      contactId: contact.id,
      reason: input.reason,
      status: 'Needs review',
      channel: contact.preferredChannel,
      body,
      variants: {
        short,
        standard: body,
        warm
      },
      selectedVariant: 'standard',
      quality: 'Template fallback',
      readiness: 'Template selected for review',
      duplicateWarning: duplicate
        ? 'A similar manual message already exists for this contact. Review before continuing.'
        : undefined,
      lastError: input.templateId ? undefined : 'Created from a custom edited template.'
    }
  };
};

export const firstRenderedTemplateForContact = (
  state: AppState,
  contactId: string,
  reason: ComposerReason
) => {
  const contact = state.contacts.find(item => item.id === contactId);
  if (!contact) {
    return undefined;
  }
  const template = findMessageTemplates(reason, contact.tone)[0];
  return template ? renderMessageTemplate(template, contact, contextForContact(state, contactId)) : undefined;
};
