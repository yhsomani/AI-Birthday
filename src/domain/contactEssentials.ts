import { isValidEmailAddress, normalizeEmailAddress } from './emailDelivery';
import type { Contact, MessageChannel } from './types';

export const supportedContactLanguages: Contact['language'][] = ['English', 'Hinglish', 'Hindi'];

export interface ContactEssentialsInput {
  name: string;
  relationship: string;
  relationshipSubtype?: string;
  jobTitle?: string;
  phone?: string;
  email?: string;
  language: Contact['language'];
  notesSummary: string;
}

export type ContactEssentialsValidation =
  | {
      ok: true;
      value: Pick<
        Contact,
        'name' | 'relationship' | 'relationshipSubtype' | 'jobTitle' | 'phone' | 'email' | 'language' | 'notesSummary'
      >;
    }
  | { ok: false; message: string };

const normalizeText = (value: string) => value.trim().replace(/\s+/g, ' ');

const normalizeOptionalText = (value: string | undefined) => normalizeText(value ?? '') || undefined;

const normalizePhone = (value: string | undefined) => {
  const trimmed = normalizeText(value ?? '');
  if (!trimmed) {
    return undefined;
  }
  const hasLeadingPlus = trimmed.startsWith('+');
  const digits = trimmed.replace(/\D/g, '');
  return `${hasLeadingPlus ? '+' : ''}${digits}`;
};

const isValidPhone = (value: string | undefined) => {
  if (!value) {
    return true;
  }
  const digits = value.replace(/\D/g, '');
  return digits.length >= 7 && digits.length <= 15;
};

export const validateContactEssentials = (
  input: ContactEssentialsInput,
  preferredChannel: MessageChannel
): ContactEssentialsValidation => {
  const name = normalizeText(input.name);
  const relationship = normalizeText(input.relationship);
  const relationshipSubtype = normalizeOptionalText(input.relationshipSubtype);
  const jobTitle = normalizeOptionalText(input.jobTitle);
  const phone = normalizePhone(input.phone);
  const email = normalizeEmailAddress(input.email) || undefined;
  const notesSummary = input.notesSummary.trim().replace(/\s+/g, ' ');

  if (name.length < 2) {
    return { ok: false, message: 'Contact name is required.' };
  }
  if (name.length > 80) {
    return { ok: false, message: 'Contact name must be 80 characters or fewer.' };
  }
  if (relationship.length < 2) {
    return { ok: false, message: 'Relationship is required.' };
  }
  if (relationship.length > 80) {
    return { ok: false, message: 'Relationship must be 80 characters or fewer.' };
  }
  if (relationshipSubtype && relationshipSubtype.length > 80) {
    return { ok: false, message: 'Relationship subtype must be 80 characters or fewer.' };
  }
  if (jobTitle && jobTitle.length > 120) {
    return { ok: false, message: 'Job title must be 120 characters or fewer.' };
  }
  if (!supportedContactLanguages.includes(input.language)) {
    return { ok: false, message: 'Choose a supported contact language.' };
  }
  if (!isValidPhone(phone)) {
    return { ok: false, message: 'Phone number must contain 7 to 15 digits.' };
  }
  if (email && !isValidEmailAddress(email)) {
    return { ok: false, message: 'Email address is not valid.' };
  }
  if ((preferredChannel === 'SMS' || preferredChannel === 'WhatsApp') && !phone) {
    return { ok: false, message: `${preferredChannel} needs a valid phone number before saving.` };
  }
  if (preferredChannel === 'Email' && !email) {
    return { ok: false, message: 'Email channel needs a valid email address before saving.' };
  }
  if (notesSummary.length > 500) {
    return { ok: false, message: 'Notes summary must be 500 characters or fewer.' };
  }

  return {
    ok: true,
    value: {
      name,
      relationship,
      relationshipSubtype,
      jobTitle,
      phone,
      email,
      language: input.language,
      notesSummary
    }
  };
};
