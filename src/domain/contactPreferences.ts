import { automationModes } from './schedulingPolicy';
import type {
  AppState,
  AutomationMode,
  Contact,
  ContactGroupDefaults,
  ContactQuietHoursBehavior,
  MessageChannel,
  RelationshipGroup,
  RelationshipGroupDefaults,
  SettingsState,
  Tone
} from './types';

export type ContactPreferenceSource = 'contact' | 'group' | 'global';

export interface ResolvedContactPreferences extends ContactGroupDefaults {
  group: RelationshipGroup;
  customSendTime?: string;
  quietHoursBehavior: ContactQuietHoursBehavior;
  skipAuto: boolean;
  sources: Record<keyof ContactGroupDefaults, ContactPreferenceSource>;
  inheritedCount: number;
  overrideCount: number;
}

const relationshipGroups: RelationshipGroup[] = ['Family', 'Friends', 'Work', 'Close friends', 'Other'];
const channels: MessageChannel[] = ['SMS', 'WhatsApp', 'Email', 'Manual'];
const tones: Tone[] = ['Warm', 'Respectful', 'Playful', 'Concise', 'Formal', 'Hinglish', 'No emoji'];
const checkInCadences = [14, 30, 45, 60, 90];

export const defaultRelationshipGroupDefaults: RelationshipGroupDefaults = {
  Family: {
    preferredChannel: 'SMS',
    tone: ['Warm', 'Hinglish'],
    checkInCadenceDays: 30,
    automationMode: 'VIP approve'
  },
  Friends: {
    preferredChannel: 'Manual',
    tone: ['Warm', 'Playful'],
    checkInCadenceDays: 45,
    automationMode: 'Always ask'
  },
  Work: {
    preferredChannel: 'Email',
    tone: ['Respectful', 'Formal', 'Concise'],
    checkInCadenceDays: 60,
    automationMode: 'Always ask'
  },
  'Close friends': {
    preferredChannel: 'WhatsApp',
    tone: ['Warm', 'Playful'],
    checkInCadenceDays: 30,
    automationMode: 'Smart approve'
  },
  Other: {
    preferredChannel: 'Manual',
    tone: ['Warm'],
    checkInCadenceDays: 60,
    automationMode: 'Always ask'
  }
};

const isChannel = (value: unknown): value is MessageChannel => channels.includes(value as MessageChannel);

const isTone = (value: unknown): value is Tone => tones.includes(value as Tone);

const isCadence = (value: unknown): value is number => typeof value === 'number' && checkInCadences.includes(value);

const isAutomationMode = (value: unknown): value is AutomationMode => automationModes.includes(value as AutomationMode);

const normalizeTones = (value: unknown, fallback: Tone[]) => {
  if (!Array.isArray(value)) {
    return fallback;
  }
  const next = [...new Set(value.filter(isTone))];
  return next.length > 0 ? next : fallback;
};

const normalizeGroupDefault = (
  value: Partial<ContactGroupDefaults> | undefined,
  fallback: ContactGroupDefaults
): ContactGroupDefaults => ({
  preferredChannel: isChannel(value?.preferredChannel) ? value.preferredChannel : fallback.preferredChannel,
  tone: normalizeTones(value?.tone, fallback.tone),
  checkInCadenceDays: isCadence(value?.checkInCadenceDays) ? value.checkInCadenceDays : fallback.checkInCadenceDays,
  automationMode: isAutomationMode(value?.automationMode) ? value.automationMode : fallback.automationMode
});

export const normalizeRelationshipGroupDefaults = (
  value?: Partial<Record<RelationshipGroup, Partial<ContactGroupDefaults>>>
): RelationshipGroupDefaults =>
  relationshipGroups.reduce((defaults, group) => {
    defaults[group] = normalizeGroupDefault(value?.[group], defaultRelationshipGroupDefaults[group]);
    return defaults;
  }, {} as RelationshipGroupDefaults);

export const resolveContactPreferencesForContact = (
  settings: SettingsState,
  contact: Contact
): ResolvedContactPreferences => {
  const groupDefaults = normalizeRelationshipGroupDefaults(settings.groupDefaults)[contact.group];
  const overrides = contact.preferenceOverrides;
  const managedByGroup = overrides !== undefined;
  const hasOverride = (key: keyof ContactGroupDefaults) =>
    Boolean(overrides && Object.prototype.hasOwnProperty.call(overrides, key));

  const preferredChannel =
    hasOverride('preferredChannel') && isChannel(overrides?.preferredChannel)
      ? overrides.preferredChannel
      : managedByGroup
        ? groupDefaults.preferredChannel
        : contact.preferredChannel;
  const tone = hasOverride('tone')
    ? normalizeTones(overrides?.tone, groupDefaults.tone)
    : managedByGroup
      ? groupDefaults.tone
      : contact.tone;
  const checkInCadenceDays =
    hasOverride('checkInCadenceDays') && isCadence(overrides?.checkInCadenceDays)
      ? overrides.checkInCadenceDays
      : managedByGroup
        ? groupDefaults.checkInCadenceDays
        : contact.checkInCadenceDays;
  const automationMode =
    hasOverride('automationMode') && isAutomationMode(overrides?.automationMode)
      ? overrides.automationMode
      : managedByGroup
        ? groupDefaults.automationMode
        : settings.automationMode;

  const sources: ResolvedContactPreferences['sources'] = {
    preferredChannel: hasOverride('preferredChannel') ? 'contact' : managedByGroup ? 'group' : 'contact',
    tone: hasOverride('tone') ? 'contact' : managedByGroup ? 'group' : 'contact',
    checkInCadenceDays: hasOverride('checkInCadenceDays') ? 'contact' : managedByGroup ? 'group' : 'contact',
    automationMode: hasOverride('automationMode') ? 'contact' : managedByGroup ? 'group' : 'global'
  };
  const inheritedCount = Object.values(sources).filter(source => source === 'group').length;
  const overrideCount = Object.values(sources).filter(source => source === 'contact').length;

  return {
    group: contact.group,
    customSendTime: contact.customSendTime,
    quietHoursBehavior: contact.quietHoursBehavior ?? 'Defer',
    skipAuto: contact.skipAuto ?? false,
    preferredChannel,
    tone,
    checkInCadenceDays,
    automationMode,
    sources,
    inheritedCount,
    overrideCount
  };
};

export const contactAllowsAutomaticDraftGeneration = (contact: Contact) => !(contact.skipAuto ?? false);

export const resolveContactPreferences = (
  state: AppState,
  contactId: string
): ResolvedContactPreferences | undefined => {
  const contact = state.contacts.find(item => item.id === contactId);
  return contact ? resolveContactPreferencesForContact(state.settings, contact) : undefined;
};

export const applyResolvedPreferencesToContact = (
  contact: Contact,
  preferences: Pick<ContactGroupDefaults, 'preferredChannel' | 'tone' | 'checkInCadenceDays'>
): Contact => ({
  ...contact,
  preferredChannel: preferences.preferredChannel,
  tone: preferences.tone,
  checkInCadenceDays: preferences.checkInCadenceDays
});
