import type { Contact, MessageDraft } from './types';

const encode = (value: string) => encodeURIComponent(value);

export interface HandoffTarget {
  url?: string;
  shareFallback: boolean;
  label: string;
  reason?: string;
}

export const buildHandoffTarget = (contact: Contact | undefined, message: MessageDraft): HandoffTarget => {
  if (!contact) {
    return {
      shareFallback: true,
      label: 'Share message',
      reason: 'Contact details are unavailable.'
    };
  }

  if (message.channel === 'SMS') {
    if (!contact.phone) {
      return {
        shareFallback: true,
        label: 'Share message',
        reason: 'Phone number is missing.'
      };
    }
    return {
      url: `sms:${contact.phone}?body=${encode(message.body)}`,
      shareFallback: true,
      label: 'Open SMS'
    };
  }

  if (message.channel === 'WhatsApp') {
    if (!contact.phone) {
      return {
        shareFallback: true,
        label: 'Share message',
        reason: 'Phone number is missing.'
      };
    }
    return {
      url: `whatsapp://send?phone=${encode(contact.phone)}&text=${encode(message.body)}`,
      shareFallback: true,
      label: 'Open WhatsApp'
    };
  }

  if (message.channel === 'Email') {
    if (!contact.email) {
      return {
        shareFallback: true,
        label: 'Share message',
        reason: 'Email address is missing.'
      };
    }
    return {
      url: `mailto:${contact.email}?subject=${encode(`Message for ${contact.name}`)}&body=${encode(message.body)}`,
      shareFallback: true,
      label: 'Open Email'
    };
  }

  return {
    shareFallback: true,
    label: 'Share message'
  };
};
