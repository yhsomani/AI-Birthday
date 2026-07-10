import { resolveContactPreferencesForContact } from './contactPreferences';
import { detectDuplicateMessageRisk } from './duplicateGuard';
import { MIN_MESSAGE_BODY_LENGTH } from './messageBodyPolicy';
import {
  buildMemoryPersonalizationContext,
  firstMentionableMemoryTextForContact,
  sanitizeRecipientVisiblePersonalizationText
} from './personalizationContextPolicy';
import type { AppState, ComposerReason, Contact, MessageDraft, MessageRegenerationFeedback, Tone } from './types';

export type MessageTemplate = {
  id: string;
  reason: ComposerReason;
  tone: Tone;
  language: Contact['language'];
  title: string;
  body: string;
};

export type LocalTemplateSelection = {
  languageTarget: Contact['language'];
  templateLanguage?: Contact['language'];
  requestedTones: Tone[];
  selectedTone?: Tone;
  exactLanguageMatch: boolean;
  exactToneMatch: boolean;
  /** A wrong-language template is never substituted; this flags that the requested template was rejected. */
  wrongLanguageTemplateBlocked: boolean;
  detail: string;
};

export type LocalTemplateFallbackResult =
  | {
      ok: true;
      template: MessageTemplate;
      body: string;
      variants: MessageDraft['variants'];
      selection: LocalTemplateSelection;
    }
  | {
      ok: false;
      reason: string;
      selection?: LocalTemplateSelection;
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

export type MessageTemplateLibraryInput = {
  contactId?: string;
  reason: ComposerReason;
  tone?: Tone;
  selectedTemplateId?: string;
  draftBody?: string;
};

export type MessageTemplateLibraryState =
  | {
      ok: true;
      contact: Contact;
      reason: ComposerReason;
      selectedTone?: Tone;
      toneOptions: Tone[];
      templates: MessageTemplate[];
      selectedTemplate: MessageTemplate;
      templateSelection: LocalTemplateSelection;
      renderedBody: string;
      characterCount: number;
      contextDetail: string;
      action: {
        enabled: boolean;
        detail: string;
      };
    }
  | {
      ok: false;
      reason: ComposerReason;
      selectedTone?: Tone;
      toneOptions: Tone[];
      templates: MessageTemplate[];
      renderedBody: string;
      characterCount: number;
      contextDetail: string;
      action: {
        enabled: false;
        detail: string;
      };
      error: string;
    };

export const messageTemplates: MessageTemplate[] = [
  {
    id: 'birthday-warm',
    reason: 'Birthday',
    tone: 'Warm',
    language: 'English',
    title: 'Warm birthday wish',
    body: 'Happy birthday {{name}}! Wishing you a day that feels easy, joyful, and full of people who care about you. Hope the year ahead brings good health, calm moments, and plenty to smile about.'
  },
  {
    id: 'checkin-concise',
    reason: 'Check-in',
    tone: 'Concise',
    language: 'English',
    title: 'Short check-in',
    body: 'Hi {{name}}, just checking in. Hope you are doing well. No rush to reply, but I was thinking of you.'
  },
  {
    id: 'thanks-warm',
    reason: 'Thanks',
    tone: 'Warm',
    language: 'English',
    title: 'Thank you note',
    body: 'Hi {{name}}, thank you for being there. I really appreciate your time, kindness, and the way you show up.'
  },
  {
    id: 'congrats-respectful',
    reason: 'Congratulations',
    tone: 'Respectful',
    language: 'English',
    title: 'Respectful congratulations',
    body: 'Congratulations {{name}}. This is a meaningful milestone, and I am genuinely happy to see your effort recognized. Wishing you continued success.'
  },
  {
    id: 'apology-formal',
    reason: 'Apology',
    tone: 'Formal',
    language: 'English',
    title: 'Clear apology',
    body: 'Hi {{name}}, I wanted to apologize properly. I understand this mattered, and I am sorry for my part in it. I value our relationship and would like to make this right.'
  },
  {
    id: 'followup-warm',
    reason: 'Follow-up',
    tone: 'Warm',
    language: 'English',
    title: 'Gentle follow-up',
    body: 'Hi {{name}}, I wanted to follow up and see how things went. Hope it all turned out well.'
  },
  {
    id: 'custom-warm',
    reason: 'Custom',
    tone: 'Warm',
    language: 'English',
    title: 'Open warm note',
    body: 'Hi {{name}}, I was thinking of you and wanted to send a quick note. {{context}}'
  },
  {
    id: 'birthday-hindi-warm',
    reason: 'Birthday',
    tone: 'Warm',
    language: 'Hindi',
    title: 'प्यार भरी जन्मदिन शुभकामना',
    body: 'जन्मदिन मुबारक हो, {{name}}! आपका आज का दिन खुशियों, अपनेपन और प्यार से भरा हो। आने वाला साल आपके लिए अच्छा स्वास्थ्य, सुकून और बहुत सी मुस्कानें लाए।'
  },
  {
    id: 'checkin-hindi-concise',
    reason: 'Check-in',
    tone: 'Concise',
    language: 'Hindi',
    title: 'छोटा सा हालचाल संदेश',
    body: 'नमस्ते {{name}}, बस आपका हालचाल पूछना था। आशा है आप ठीक हैं। जवाब देने की कोई जल्दी नहीं है; आज आपकी याद आई।'
  },
  {
    id: 'thanks-hindi-warm',
    reason: 'Thanks',
    tone: 'Warm',
    language: 'Hindi',
    title: 'दिल से धन्यवाद',
    body: 'नमस्ते {{name}}, आपका दिल से धन्यवाद। आपका समय, सहयोग और अपनापन मेरे लिए बहुत मायने रखता है।'
  },
  {
    id: 'congrats-hindi-respectful',
    reason: 'Congratulations',
    tone: 'Respectful',
    language: 'Hindi',
    title: 'सम्मानपूर्ण बधाई',
    body: 'बहुत-बहुत बधाई, {{name}}। यह एक खास उपलब्धि है और आपकी मेहनत को मिली पहचान देखकर मुझे सच में खुशी है। आगे के लिए ढेरों शुभकामनाएँ।'
  },
  {
    id: 'apology-hindi-formal',
    reason: 'Apology',
    tone: 'Formal',
    language: 'Hindi',
    title: 'स्पष्ट माफ़ी',
    body: 'नमस्ते {{name}}, जो हुआ उसके लिए मुझे सच में खेद है। यह बात मायने रखती थी और अपनी भूमिका की ज़िम्मेदारी मुझे स्वीकार है। हमारा रिश्ता मेरे लिए बहुत कीमती है; मेरी कोशिश इसे ईमानदारी से ठीक करने की है।'
  },
  {
    id: 'followup-hindi-warm',
    reason: 'Follow-up',
    tone: 'Warm',
    language: 'Hindi',
    title: 'सहज अनुस्मारक',
    body: 'नमस्ते {{name}}, पिछली बात के बाद यह जानना था कि सब कैसा रहा। आशा है सब अच्छी तरह हो गया होगा।'
  },
  {
    id: 'custom-hindi-warm',
    reason: 'Custom',
    tone: 'Warm',
    language: 'Hindi',
    title: 'अपनापन भरा संदेश',
    body: 'नमस्ते {{name}}, आज आपकी याद आई और आपसे एक छोटी-सी बात साझा करने का मन हुआ। {{context}}'
  },
  {
    id: 'birthday-hinglish',
    reason: 'Birthday',
    tone: 'Hinglish',
    language: 'Hinglish',
    title: 'Hinglish birthday wish',
    body: 'Happy birthday {{name}}! Aaj ka din bahut special ho, full of smiles, good food, and people who love you. Aane wala saal bhi khushiyon aur lovely moments se bhara rahe.'
  },
  {
    id: 'checkin-hinglish-concise',
    reason: 'Check-in',
    tone: 'Concise',
    language: 'Hinglish',
    title: 'Short Hinglish check-in',
    body: 'Hi {{name}}, bas check in karna tha. Umeed hai sab theek chal raha hai. Reply ki koi jaldi nahi; aaj aapki yaad aayi.'
  },
  {
    id: 'thanks-hinglish-warm',
    reason: 'Thanks',
    tone: 'Warm',
    language: 'Hinglish',
    title: 'Warm Hinglish thank you',
    body: 'Hi {{name}}, dil se thank you. Aapka time, support, aur jis tarah aap saath dete hain, woh mere liye bahut matter karta hai.'
  },
  {
    id: 'congrats-hinglish-respectful',
    reason: 'Congratulations',
    tone: 'Respectful',
    language: 'Hinglish',
    title: 'Respectful Hinglish congratulations',
    body: 'Bahut bahut congratulations, {{name}}. Yeh ek special milestone hai, aur aapki mehnat ko recognition milte dekh sach mein khushi hui. Aage ke liye best wishes.'
  },
  {
    id: 'apology-hinglish-formal',
    reason: 'Apology',
    tone: 'Formal',
    language: 'Hinglish',
    title: 'Clear Hinglish apology',
    body: 'Hi {{name}}, jo hua uske liye mujhe sach mein afsos hai. Mujhe samajh hai ki yeh matter karta tha. Hamara rishta mere liye important hai, aur ise honestly theek karna meri priority hai.'
  },
  {
    id: 'followup-hinglish-warm',
    reason: 'Follow-up',
    tone: 'Warm',
    language: 'Hinglish',
    title: 'Gentle Hinglish follow-up',
    body: 'Hi {{name}}, bas follow up karke poochna tha ki sab kaisa raha. Umeed hai sab achchhe se ho gaya hoga.'
  },
  {
    id: 'custom-hinglish-warm',
    reason: 'Custom',
    tone: 'Warm',
    language: 'Hinglish',
    title: 'Open Hinglish note',
    body: 'Hi {{name}}, aaj aapki yaad aayi, isliye yeh chhota sa note. {{context}}'
  }
];

const normalize = (value: string) => value.trim().replace(/\s+/g, ' ');

const contextReportForContact = (state: AppState, contact: Contact) => {
  const policy = buildMemoryPersonalizationContext(state.memories, {
    contactId: contact.id
  });
  const context = policy.mentionableFacts[0]?.text;
  const mentionableDetail = context
    ? `${policy.includedMentionableMemoryCount} mentionable non-private memory item(s) available`
    : 'No mentionable memory context available';
  const guidanceDetail = `${policy.includedGuidanceMemoryCount} instruction/guidance memory item(s) excluded from recipient-visible text`;
  const sensitiveDetail = policy.excludedSensitiveMemoryCount
    ? `; ${policy.excludedSensitiveMemoryCount} sensitive memory item(s) excluded`
    : '';
  return {
    context,
    detail: `${mentionableDetail}; ${guidanceDetail}; ${policy.excludedPrivateMemoryCount} private note(s) excluded${sensitiveDetail}.`
  };
};

export const findMessageTemplates = (reason: ComposerReason, language: Contact['language'], tones: Tone[] = []) => {
  const matches = messageTemplates.filter(template => template.reason === reason && template.language === language);
  const toneMatches = matches.filter(template => tones.includes(template.tone));
  return toneMatches.length > 0 ? toneMatches : matches;
};

export const renderMessageTemplate = (template: MessageTemplate, contact: Contact, context?: string) => {
  const safeContext = normalize(sanitizeRecipientVisiblePersonalizationText(context) ?? '');
  const contextPrefix =
    template.language === 'Hindi'
      ? 'मुझे यह बात याद आई:'
      : template.language === 'Hinglish'
        ? 'Mujhe yeh yaad aaya:'
        : 'I remembered:';
  return normalize(
    template.body
      .replaceAll('{{name}}', contact.name)
      .replaceAll('{{relationship}}', contact.relationship)
      .replaceAll('{{context}}', safeContext ? `${contextPrefix} ${safeContext}` : '')
  );
};

export const selectLocalMessageTemplate = (
  reason: ComposerReason,
  languageTarget: Contact['language'],
  requestedTones: Tone[],
  selectedTemplateId?: string
): { templates: MessageTemplate[]; selectedTemplate?: MessageTemplate; summary: LocalTemplateSelection } => {
  const languageTemplates = messageTemplates.filter(
    template => template.reason === reason && template.language === languageTarget
  );
  const exactToneTemplates = languageTemplates.filter(template => requestedTones.includes(template.tone));
  const templates = exactToneTemplates.length > 0 ? exactToneTemplates : languageTemplates;
  const requestedTemplate = selectedTemplateId
    ? messageTemplates.find(template => template.id === selectedTemplateId && template.reason === reason)
    : undefined;
  const sameLanguageRequestedTemplate = requestedTemplate?.language === languageTarget ? requestedTemplate : undefined;
  const selectedTemplate = sameLanguageRequestedTemplate ?? templates[0];
  const exactToneMatch =
    requestedTones.length === 0 || Boolean(selectedTemplate && requestedTones.includes(selectedTemplate.tone));
  const exactLanguageMatch = selectedTemplate?.language === languageTarget;
  const wrongLanguageTemplateBlocked = Boolean(requestedTemplate && requestedTemplate.language !== languageTarget);
  const toneTarget = requestedTones.length > 0 ? requestedTones.join(', ') : 'no explicit tone';
  const detail = !selectedTemplate
    ? `No local ${languageTarget} template is available for ${reason}. RelateAI did not substitute an English template; choose another reason or write the message manually.`
    : [
        `Language target ${languageTarget} is matched by the local ${selectedTemplate.language} template.`,
        exactToneMatch
          ? `Tone target matched by ${selectedTemplate.tone}.`
          : `No exact ${toneTarget} template exists for ${reason} in ${languageTarget}; using ${selectedTemplate.tone} tone while keeping the ${languageTarget} language target.`,
        wrongLanguageTemplateBlocked
          ? `The requested ${requestedTemplate?.language} template was not used because it does not match this contact's ${languageTarget} language target.`
          : ''
      ]
        .filter(Boolean)
        .join(' ');

  return {
    templates,
    selectedTemplate,
    summary: {
      languageTarget,
      templateLanguage: selectedTemplate?.language,
      requestedTones,
      selectedTone: selectedTemplate?.tone,
      exactLanguageMatch,
      exactToneMatch,
      wrongLanguageTemplateBlocked,
      detail
    }
  };
};

const warmSuffixForLanguage = (language: Contact['language']) =>
  language === 'Hindi'
    ? 'आशा है यह संदेश दिल से और अपनापन भरा लगे।'
    : language === 'Hinglish'
      ? 'Umeed hai yeh message dil se aur personal lage.'
      : 'I hope this feels thoughtful and personal.';

export const buildLocalTemplateFallback = (
  state: AppState,
  contactId: string,
  reason: ComposerReason,
  options: {
    excludedMemoryIds?: string[];
    feedback?: MessageRegenerationFeedback;
    averageLength?: number;
  } = {}
): LocalTemplateFallbackResult => {
  const contact = state.contacts.find(item => item.id === contactId);
  if (!contact || contact.archivedAt) {
    return { ok: false, reason: 'The selected contact could not be found.' };
  }
  const preferences = resolveContactPreferencesForContact(state.settings, contact);
  const selection = selectLocalMessageTemplate(reason, contact.language, preferences.tone);
  if (!selection.selectedTemplate) {
    return {
      ok: false,
      reason: selection.summary.detail,
      selection: selection.summary
    };
  }
  const context = firstMentionableMemoryTextForContact(state, contactId, options.excludedMemoryIds);
  const renderedBody = renderMessageTemplate(selection.selectedTemplate, contact, context);
  const feedbackText = [...(options.feedback?.instructions ?? []), options.feedback?.customInstruction ?? '']
    .join(' ')
    .toLowerCase();
  const wantsWarmer = /\b(?:warmer|more warm|more heartfelt)\b/.test(feedbackText);
  const wantsShorter = /\b(?:shorter|more concise|one sentence)\b/.test(feedbackText);
  const warmedBody = wantsWarmer
    ? normalize(`${renderedBody} ${warmSuffixForLanguage(contact.language)}`)
    : renderedBody;
  const requestedLimit = Math.max(MIN_MESSAGE_BODY_LENGTH, Math.min(options.averageLength ?? 120, 120));
  const body =
    wantsShorter && warmedBody.length > requestedLimit
      ? `${warmedBody.slice(0, Math.max(MIN_MESSAGE_BODY_LENGTH, requestedLimit - 3)).trimEnd()}...`
      : warmedBody;
  const short = body.length > 88 ? `${body.slice(0, 85).trimEnd()}...` : body;
  const warmSuffix = warmSuffixForLanguage(contact.language);
  const warm = body.includes(warmSuffix) ? body : normalize(`${body} ${warmSuffix}`);

  return {
    ok: true,
    template: selection.selectedTemplate,
    body,
    variants: { short, standard: body, warm },
    selection: selection.summary
  };
};

export const buildTemplateDraft = (
  state: AppState,
  input: TemplateDraftInput,
  identity: number | string
): TemplateDraftResult => {
  const contact = state.contacts.find(item => item.id === input.contactId);
  if (!contact || contact.archivedAt) {
    return { ok: false, reason: 'The selected contact could not be found.' };
  }

  const body = normalize(input.body);
  if (body.length < MIN_MESSAGE_BODY_LENGTH) {
    return { ok: false, reason: 'Write a longer template message before creating a draft.' };
  }

  const preferences = resolveContactPreferencesForContact(state.settings, contact);
  const short = body.length > 88 ? `${body.slice(0, 85).trimEnd()}...` : body;
  const warm = normalize(`${body} ${warmSuffixForLanguage(contact.language)}`);
  const draft: MessageDraft = {
    id: typeof identity === 'string' ? identity : `template-${identity}-${state.messages.length}`,
    contactId: contact.id,
    reason: input.reason,
    status: 'Needs review',
    channel: preferences.preferredChannel,
    body,
    variants: {
      short,
      standard: body,
      warm
    },
    selectedVariant: 'standard',
    quality: 'Template fallback',
    readiness: `Local ${contact.language} template selected for review`,
    lastError: input.templateId ? undefined : 'Created from a custom edited template.'
  };
  const duplicateRisk = detectDuplicateMessageRisk(state, draft);

  return {
    ok: true,
    draft: duplicateRisk.risk ? { ...draft, duplicateWarning: duplicateRisk.message } : draft
  };
};

export const buildMessageTemplateLibrary = (
  state: AppState,
  input: MessageTemplateLibraryInput
): MessageTemplateLibraryState => {
  const contact = input.contactId ? state.contacts.find(item => item.id === input.contactId) : state.contacts[0];
  const languageTarget = contact?.language;
  const reasonTemplates = messageTemplates.filter(
    template => template.reason === input.reason && (!languageTarget || template.language === languageTarget)
  );
  const toneOptions = [...new Set(reasonTemplates.map(template => template.tone))];
  const selectedTone =
    input.tone ?? (contact ? resolveContactPreferencesForContact(state.settings, contact).tone[0] : undefined);

  if (!contact) {
    return {
      ok: false,
      reason: input.reason,
      selectedTone,
      toneOptions,
      templates: reasonTemplates,
      renderedBody: '',
      characterCount: 0,
      contextDetail: 'Choose a contact before personalizing a template.',
      action: {
        enabled: false,
        detail: 'Choose a contact before creating a review draft.'
      },
      error: 'No contact is selected.'
    };
  }

  const selection = selectLocalMessageTemplate(
    input.reason,
    contact.language,
    selectedTone ? [selectedTone] : [],
    input.selectedTemplateId
  );
  const templates = selection.templates;
  const selectedTemplate = selection.selectedTemplate;
  if (!selectedTemplate) {
    return {
      ok: false,
      reason: input.reason,
      selectedTone,
      toneOptions,
      templates: [],
      renderedBody: '',
      characterCount: 0,
      contextDetail: selection.summary.detail,
      action: {
        enabled: false,
        detail: `No ${contact.language} template is available. Write the message manually; RelateAI will not substitute another language.`
      },
      error: selection.summary.detail
    };
  }
  const context = contextReportForContact(state, contact);
  const renderedBody = selectedTemplate ? renderMessageTemplate(selectedTemplate, contact, context.context) : '';
  const body = normalize(input.draftBody ?? renderedBody);
  const actionEnabled = body.length >= MIN_MESSAGE_BODY_LENGTH;

  return {
    ok: true,
    contact,
    reason: input.reason,
    selectedTone,
    toneOptions,
    templates,
    selectedTemplate,
    templateSelection: selection.summary,
    renderedBody,
    characterCount: body.length,
    contextDetail: `${context.detail} ${selection.summary.detail}`,
    action: {
      enabled: actionEnabled,
      detail: actionEnabled
        ? 'Creates a review-first draft from the edited template.'
        : `Write at least ${MIN_MESSAGE_BODY_LENGTH} characters before creating a draft.`
    }
  };
};

export const firstRenderedTemplateForContact = (state: AppState, contactId: string, reason: ComposerReason) => {
  const contact = state.contacts.find(item => item.id === contactId);
  if (!contact || contact.archivedAt) {
    return undefined;
  }
  const fallback = buildLocalTemplateFallback(state, contactId, reason);
  return fallback.ok ? fallback.body : undefined;
};
