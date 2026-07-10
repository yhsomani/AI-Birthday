import type { Contact, MessageDraft } from './types';

const encode = (value: string) => encodeURIComponent(value);

export interface HandoffTarget {
  url?: string;
  shareFallback: boolean;
  label: string;
  fallbackLabel: string;
  reason?: string;
  privacyNote: string;
  completionTitle: string;
  completionMessage: string;
  markSentLabel: string;
  dismissLabel: string;
}

const baseTarget = () => ({
  shareFallback: true,
  fallbackLabel: 'Copy/share message',
  privacyNote: 'RelateAI opens the approved text only. The destination app performs the send after your final review.',
  completionTitle: 'Mark sent?',
  completionMessage: 'Send the approved text in the destination app first. Mark sent here only after the message has actually left your device.',
  markSentLabel: 'I sent it',
  dismissLabel: 'Not yet'
});

export const buildHandoffTarget = (contact: Contact | undefined, message: MessageDraft): HandoffTarget => {
  if (!contact) {
    return {
      ...baseTarget(),
      shareFallback: true,
      label: 'Copy/share message',
      reason: 'Contact details are unavailable.'
    };
  }

  if (message.channel === 'SMS') {
    if (!contact.phone) {
      return {
        ...baseTarget(),
        shareFallback: true,
        label: 'Copy/share message',
        reason: 'Phone number is missing.'
      };
    }
    return {
      ...baseTarget(),
      url: `sms:${contact.phone}?body=${encode(message.body)}`,
      shareFallback: true,
      label: 'Open SMS'
    };
  }

  if (message.channel === 'WhatsApp') {
    if (!contact.phone) {
      return {
        ...baseTarget(),
        shareFallback: true,
        label: 'Copy/share message',
        reason: 'Phone number is missing.'
      };
    }
    return {
      ...baseTarget(),
      url: `whatsapp://send?phone=${encode(contact.phone)}&text=${encode(message.body)}`,
      shareFallback: true,
      label: 'Open WhatsApp'
    };
  }

  if (message.channel === 'Email') {
    if (!contact.email) {
      return {
        ...baseTarget(),
        shareFallback: true,
        label: 'Copy/share message',
        reason: 'Email address is missing.'
      };
    }
    return {
      ...baseTarget(),
      url: `mailto:${contact.email}?subject=${encode(`Message for ${contact.name}`)}&body=${encode(message.body)}`,
      shareFallback: true,
      label: 'Open Email'
    };
  }

  return {
    ...baseTarget(),
    shareFallback: true,
    label: 'Copy/share message'
  };
};
