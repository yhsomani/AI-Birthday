import { resolveContactPreferencesForContact, type ResolvedContactPreferences } from './contactPreferences';
import { MIN_MESSAGE_BODY_LENGTH } from './messageBodyPolicy';
import {
  renderMessageTemplate,
  selectLocalMessageTemplate,
  type LocalTemplateSelection,
  type MessageTemplate
} from './messageTemplates';
import { buildMemoryPersonalizationContext } from './personalizationContextPolicy';
import type { AppState, ComposerReason, Contact } from './types';

export const manualComposerReasons: ComposerReason[] = [
  'Birthday',
  'Check-in',
  'Thanks',
  'Congratulations',
  'Apology',
  'Follow-up',
  'Custom'
];

export type ManualComposerActionStatus = 'Ready' | 'Warning' | 'Blocked';

export interface ManualComposerAction {
  status: ManualComposerActionStatus;
  enabled: boolean;
  label: string;
  detail: string;
}

export interface ManualComposerContextSummary {
  contextText?: string;
  contextSource: 'memory' | 'notes' | 'none';
  includedMemoryCount: number;
  excludedGuidanceMemoryCount: number;
  excludedPrivateMemoryCount: number;
  excludedSensitiveMemoryCount: number;
  detail: string;
}

export type ManualComposerState =
  | {
      ok: true;
      contact: Contact;
      reason: ComposerReason;
      preferences: ResolvedContactPreferences;
      templates: MessageTemplate[];
      selectedTemplate?: MessageTemplate;
      selectedTemplateId?: string;
      templateSelection: LocalTemplateSelection;
      renderedTemplateBody: string;
      characterCount: number;
      context: ManualComposerContextSummary;
      templateAction: ManualComposerAction;
      aiAction: ManualComposerAction;
    }
  | {
      ok: false;
      reason: ComposerReason;
      templates: MessageTemplate[];
      renderedTemplateBody: string;
      characterCount: number;
      context: ManualComposerContextSummary;
      templateAction: ManualComposerAction;
      aiAction: ManualComposerAction;
      error: string;
    };

const normalize = (value: string) => value.trim().replace(/\s+/g, ' ');

const countLabel = (value: number, singular: string, plural: string) => `${value} ${value === 1 ? singular : plural}`;

export const buildManualComposerContext = (state: AppState, contact: Contact): ManualComposerContextSummary => {
  const context = buildMemoryPersonalizationContext(state.memories, {
    contactId: contact.id
  });
  const contextText = context.mentionableFacts[0]?.text;
  const contextSource: ManualComposerContextSummary['contextSource'] = contextText ? 'memory' : 'none';
  const mentionableDetail = contextText
    ? `Using ${countLabel(context.includedMentionableMemoryCount, 'mentionable non-private memory', 'mentionable non-private memories')}`
    : 'No mentionable memory context included';
  const guidanceDetail = `${countLabel(context.includedGuidanceMemoryCount, 'instruction/guidance memory', 'instruction/guidance memories')} excluded from recipient-visible template text`;
  const privateDetail = `${countLabel(context.excludedPrivateMemoryCount, 'private note', 'private notes')} excluded`;
  const sensitiveDetail = context.excludedSensitiveMemoryCount
    ? `; ${countLabel(context.excludedSensitiveMemoryCount, 'sensitive memory', 'sensitive memories')} excluded`
    : '';

  return {
    contextText,
    contextSource,
    includedMemoryCount: context.includedMentionableMemoryCount,
    excludedGuidanceMemoryCount: context.includedGuidanceMemoryCount,
    excludedPrivateMemoryCount: context.excludedPrivateMemoryCount,
    excludedSensitiveMemoryCount: context.excludedSensitiveMemoryCount,
    detail: `${mentionableDetail}; ${guidanceDetail}; ${privateDetail}${sensitiveDetail}.`
  };
};

const templateActionForBody = (body: string, language: Contact['language']): ManualComposerAction => {
  const characterCount = normalize(body).length;
  if (characterCount < MIN_MESSAGE_BODY_LENGTH) {
    return {
      status: 'Blocked',
      enabled: false,
      label: 'Use template',
      detail: `Write at least ${MIN_MESSAGE_BODY_LENGTH} characters before creating a review draft.`
    };
  }

  return {
    status: 'Ready',
    enabled: true,
    label: 'Use template',
    detail: `Creates a review-first draft from the edited ${language} template.`
  };
};

const aiActionForState = (state: AppState, language: Contact['language']): ManualComposerAction => {
  if (!state.settings.aiEnabled) {
    return {
      status: 'Warning',
      enabled: true,
      label: 'Create fallback',
      detail: `AI drafting is disabled. This creates a local ${language} review-first fallback instead.`
    };
  }

  if (state.aiProvider.status !== 'Ready') {
    return {
      status: 'Warning',
      enabled: true,
      label: 'Create fallback',
      detail: `AI provider is ${state.aiProvider.status.toLowerCase()}. This creates a local ${language} review-first fallback instead.`
    };
  }

  return {
    status: 'Ready',
    enabled: true,
    label: 'Ask AI',
    detail: `Requests ${language} AI variants with private notes excluded and review required before sending.`
  };
};

export const buildManualComposerState = (
  state: AppState,
  contactId: string,
  reason: ComposerReason,
  draftBody?: string,
  selectedTemplateId?: string
): ManualComposerState => {
  const contact = state.contacts.find(item => item.id === contactId);
  if (!contact) {
    const blockedAction: ManualComposerAction = {
      status: 'Blocked',
      enabled: false,
      label: 'Unavailable',
      detail: 'Select an existing contact before creating a message.'
    };
    return {
      ok: false,
      reason,
      templates: [],
      renderedTemplateBody: '',
      characterCount: 0,
      context: {
        contextSource: 'none',
        includedMemoryCount: 0,
        excludedGuidanceMemoryCount: 0,
        excludedPrivateMemoryCount: 0,
        excludedSensitiveMemoryCount: 0,
        detail: 'No contact is selected.'
      },
      templateAction: blockedAction,
      aiAction: blockedAction,
      error: 'The selected contact could not be found.'
    };
  }

  const preferences = resolveContactPreferencesForContact(state.settings, contact);
  const selection = selectLocalMessageTemplate(reason, contact.language, preferences.tone, selectedTemplateId);
  const templates = selection.templates;
  const selectedTemplate = selection.selectedTemplate;
  const context = buildManualComposerContext(state, contact);
  const renderedTemplateBody = selectedTemplate
    ? renderMessageTemplate(selectedTemplate, contact, context.contextText)
    : '';
  const body = draftBody === undefined ? renderedTemplateBody : draftBody;

  return {
    ok: true,
    contact,
    reason,
    preferences,
    templates,
    selectedTemplate,
    selectedTemplateId: selectedTemplate?.id,
    templateSelection: selection.summary,
    renderedTemplateBody,
    characterCount: normalize(body).length,
    context,
    templateAction: templateActionForBody(body, contact.language),
    aiAction: aiActionForState(state, contact.language)
  };
};
