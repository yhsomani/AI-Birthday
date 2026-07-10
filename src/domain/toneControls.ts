import {
  resolveContactPreferencesForContact,
  type ContactPreferenceSource
} from './contactPreferences';
import type { AppState, AutomationMode, Contact, MessageChannel, MessageDraft, Screen, Tone } from './types';

export type TonePreferenceSummaryStatus = 'ready' | 'missing-contact' | 'missing-draft';

export type TonePreferenceSummary = {
  status: TonePreferenceSummaryStatus;
  contactId?: string;
  contactName: string;
  messageId?: string;
  draftQuality?: MessageDraft['quality'];
  tones: Tone[];
  language?: Contact['language'];
  preferredChannel?: MessageChannel;
  automationMode?: AutomationMode;
  toneSource?: ContactPreferenceSource;
  sourceLabel: string;
  influenceSummary: string;
  controlSummary: string;
  detailItems: string[];
  warnings: string[];
  adjustAction: {
    label: string;
    enabled: boolean;
    screen: Screen;
    contactId?: string;
    reason: string;
  };
};

const sourceLabels: Record<ContactPreferenceSource, string> = {
  contact: 'Contact override',
  group: 'Relationship group default',
  global: 'Global default'
};

const toneList = (tones: Tone[]) => tones.join(', ') || 'No tone selected';

const preferenceSourceDetail = (contact: Contact, source: ContactPreferenceSource) => {
  if (source === 'group') {
    return `${contact.group} group defaults`;
  }
  if (source === 'global') {
    return 'global defaults';
  }
  return `${contact.name}'s contact profile`;
};

const buildInfluenceSummary = (
  contact: Contact,
  message: MessageDraft | undefined,
  sourceDetail: string,
  tones: Tone[],
  styleConfidence: AppState['styleProfile']['confidence']
) => {
  const effectiveTones = toneList(tones);

  if (!message) {
    return `Future drafts for ${contact.name} should use ${effectiveTones} from ${sourceDetail}, written in ${contact.language}.`;
  }

  if (message.quality === 'Template fallback') {
    return `This template draft should use ${effectiveTones} from ${sourceDetail} as the local writing target for ${message.reason}. Review the wording before approval or handoff.`;
  }

  if (message.quality === 'Needs more context') {
    return `This draft starts from ${effectiveTones} from ${sourceDetail}, but sparse relationship context means the user should review personalization before approval.`;
  }

  return `This AI draft should use ${effectiveTones} from ${sourceDetail}, written in ${contact.language}, with the user's ${styleConfidence.toLowerCase()} global style profile.`;
};

export const buildTonePreferenceSummary = (
  state: AppState,
  contactId: string,
  messageId?: string
): TonePreferenceSummary => {
  const contact = state.contacts.find(item => item.id === contactId);
  const message = messageId ? state.messages.find(item => item.id === messageId) : undefined;

  if (!contact) {
    return {
      status: 'missing-contact',
      contactId,
      messageId,
      contactName: 'Unknown contact',
      tones: [],
      sourceLabel: 'Unavailable',
      influenceSummary: 'Tone preferences cannot be shown because this contact is no longer available.',
      controlSummary: 'Choose an existing contact before adjusting recipient-specific tone.',
      detailItems: [],
      warnings: ['The contact profile is unavailable.'],
      adjustAction: {
        label: 'Adjust tone',
        enabled: false,
        screen: 'contactDetail',
        reason: 'Contact unavailable'
      }
    };
  }

  const preferences = resolveContactPreferencesForContact(state.settings, contact);
  const source = preferences.sources.tone;
  const sourceDetail = preferenceSourceDetail(contact, source);
  const warnings: string[] = [];

  if (messageId && !message) {
    warnings.push('The draft is unavailable; tone changes can still apply to future drafts.');
  }
  if (message && message.contactId !== contact.id) {
    warnings.push('This draft belongs to another contact. Reopen the correct draft before approval.');
  }
  if (preferences.tone.includes('No emoji')) {
    warnings.push('Emoji should be avoided for this recipient.');
  }
  if (message?.quality === 'Template fallback') {
    warnings.push('Template drafts remain review-first before approval or manual handoff.');
  }
  if (message?.quality === 'Needs more context') {
    warnings.push('Add relationship context before relying on this draft.');
  }

  return {
    status: messageId && !message ? 'missing-draft' : 'ready',
    contactId: contact.id,
    contactName: contact.name,
    messageId,
    draftQuality: message?.quality,
    tones: preferences.tone,
    language: contact.language,
    preferredChannel: preferences.preferredChannel,
    automationMode: preferences.automationMode,
    toneSource: source,
    sourceLabel: sourceLabels[source],
    influenceSummary: buildInfluenceSummary(
      contact,
      message,
      sourceDetail,
      preferences.tone,
      state.styleProfile.confidence
    ),
    controlSummary:
      "Adjusting these tones changes this contact's future drafts without retraining the global style profile. Existing unsent drafts should be reviewed again after any change.",
    detailItems: [
      `Effective tones: ${toneList(preferences.tone)}`,
      `Preference source: ${sourceDetail}`,
      `Language target: ${contact.language}`,
      `Preferred channel: ${preferences.preferredChannel}`,
      `Automation review: ${preferences.automationMode}`,
      `Global style profile: ${state.styleProfile.confidence}, ${state.styleProfile.formality}`,
      ...(message ? [`Draft quality: ${message.quality}`] : [])
    ],
    warnings,
    adjustAction: {
      label: 'Adjust tone',
      enabled: true,
      screen: 'contactDetail',
      contactId: contact.id,
      reason: `Open ${contact.name}'s profile to edit recipient-specific tone.`
    }
  };
};
