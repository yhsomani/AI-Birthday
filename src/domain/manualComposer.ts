import { resolveContactPreferencesForContact, type ResolvedContactPreferences } from './contactPreferences';
import { MIN_MESSAGE_BODY_LENGTH } from './messageBodyPolicy';
import { findMessageTemplates, renderMessageTemplate, type MessageTemplate } from './messageTemplates';
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
  excludedPrivateMemoryCount: number;
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

const countLabel = (value: number, singular: string, plural: string) =>
  `${value} ${value === 1 ? singular : plural}`;

export const buildManualComposerContext = (
  state: AppState,
  contact: Contact
): ManualComposerContextSummary => {
  const contactMemories = state.memories.filter(memory => memory.contactId === contact.id);
  const publicMemories = contactMemories
    .filter(memory => memory.category !== 'Private')
    .map(memory => normalize(memory.body))
    .filter(Boolean);
  const privateCount = contactMemories.filter(memory => memory.category === 'Private').length;
  const noteContext = normalize(contact.notesSummary);
  const contextText = publicMemories[0] ?? noteContext;
  const contextSource: ManualComposerContextSummary['contextSource'] = publicMemories[0]
    ? 'memory'
    : noteContext
      ? 'notes'
      : 'none';
  const contextDetail =
    contextSource === 'memory'
      ? `Using ${countLabel(publicMemories.length, 'non-private memory', 'non-private memories')}; ${countLabel(privateCount, 'private note', 'private notes')} excluded.`
      : contextSource === 'notes'
        ? `Using contact notes; ${countLabel(privateCount, 'private note', 'private notes')} excluded.`
        : `No extra context included; ${countLabel(privateCount, 'private note', 'private notes')} excluded.`;

  return {
    contextText,
    contextSource,
    includedMemoryCount: publicMemories.length,
    excludedPrivateMemoryCount: privateCount,
    detail: contextDetail
  };
};

const templateActionForBody = (body: string): ManualComposerAction => {
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
    detail: 'Creates a review-first draft from the edited template.'
  };
};

const aiActionForState = (state: AppState): ManualComposerAction => {
  if (!state.settings.aiEnabled) {
    return {
      status: 'Warning',
      enabled: true,
      label: 'Create fallback',
      detail: 'AI drafting is disabled. This creates a local review-first fallback instead.'
    };
  }

  if (state.aiProvider.status !== 'Ready') {
    return {
      status: 'Warning',
      enabled: true,
      label: 'Create fallback',
      detail: `AI provider is ${state.aiProvider.status.toLowerCase()}. This creates a local review-first fallback instead.`
    };
  }

  return {
    status: 'Ready',
    enabled: true,
    label: 'Ask AI',
    detail: 'Requests AI variants with private notes excluded and review required before sending.'
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
        excludedPrivateMemoryCount: 0,
        detail: 'No contact is selected.'
      },
      templateAction: blockedAction,
      aiAction: blockedAction,
      error: 'The selected contact could not be found.'
    };
  }

  const preferences = resolveContactPreferencesForContact(state.settings, contact);
  const templates = findMessageTemplates(reason, preferences.tone);
  const selectedTemplate = templates.find(template => template.id === selectedTemplateId) ?? templates[0];
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
    renderedTemplateBody,
    characterCount: normalize(body).length,
    context,
    templateAction: templateActionForBody(body),
    aiAction: aiActionForState(state)
  };
};
