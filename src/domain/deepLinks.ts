import type { AppState, Screen } from './types';

export type DeepLinkDestination = {
  screen: Screen;
  contactId?: string;
  messageId?: string;
};

export type DeepLinkParseResult =
  | {
      ok: true;
      destination: DeepLinkDestination;
    }
  | {
      ok: false;
      fallback: DeepLinkDestination;
      message: string;
    };

export type DeepLinkResolution =
  | {
      ok: true;
      destination: DeepLinkDestination;
      message?: string;
    }
  | {
      ok: false;
      destination: DeepLinkDestination;
      message: string;
    };

const safeFallback: DeepLinkDestination = { screen: 'home' };

const segmentsFromUrl = (rawUrl: string) => {
  const url = new URL(rawUrl);
  const scheme = url.protocol.replace(':', '');
  const segments = [url.hostname, ...url.pathname.split('/')]
    .map(segment => decodeURIComponent(segment.trim()))
    .filter(Boolean);

  return {
    scheme,
    segments
  };
};

export const parseRelateDeepLink = (rawUrl: string): DeepLinkParseResult => {
  try {
    const { scheme, segments } = segmentsFromUrl(rawUrl);
    if (scheme !== 'relateai') {
      return {
        ok: false,
        fallback: safeFallback,
        message: 'This link is not a RelateAI link.'
      };
    }

    const [route, id] = segments;
    switch (route) {
      case undefined:
      case 'home':
        return { ok: true, destination: { screen: 'home' } };
      case 'onboarding':
      case 'help':
        return { ok: true, destination: { screen: 'onboarding' } };
      case 'events':
      case 'calendar':
        if (id === 'new' || id === 'add') {
          return { ok: true, destination: { screen: 'eventForm' } };
        }
        return { ok: true, destination: { screen: 'events' } };
      case 'event':
      case 'add-event':
        return id === 'new' || route === 'add-event'
          ? { ok: true, destination: { screen: 'eventForm' } }
          : { ok: true, destination: { screen: 'events' } };
      case 'messages':
      case 'queue':
        return { ok: true, destination: { screen: 'messages' } };
      case 'review':
      case 'wish':
      case 'message':
        return id
          ? { ok: true, destination: { screen: 'wishPreview', messageId: id } }
          : { ok: true, destination: { screen: 'messages' } };
      case 'contacts':
        return id
          ? { ok: true, destination: { screen: 'contactDetail', contactId: id } }
          : { ok: true, destination: { screen: 'contacts' } };
      case 'contact':
        return id
          ? { ok: true, destination: { screen: 'contactDetail', contactId: id } }
          : {
              ok: false,
              fallback: { screen: 'contacts' },
              message: 'The contact link is missing a contact reference.'
            };
      case 'history':
      case 'chat':
        return id
          ? { ok: true, destination: { screen: 'chatHistory', contactId: id } }
          : {
              ok: false,
              fallback: { screen: 'contacts' },
              message: 'The chat history link is missing a contact reference.'
            };
      case 'settings':
      case 'setup':
      case 'backup':
      case 'more':
        return { ok: true, destination: { screen: 'more' } };
      default:
        return {
          ok: false,
          fallback: safeFallback,
          message: 'This RelateAI link is not supported.'
        };
    }
  } catch {
    return {
      ok: false,
      fallback: safeFallback,
      message: 'This link could not be opened.'
    };
  }
};

export const resolveDeepLinkDestination = (
  state: AppState,
  destination: DeepLinkDestination
): DeepLinkResolution => {
  if (destination.contactId) {
    const exists = state.contacts.some(contact => contact.id === destination.contactId);
    if (!exists) {
      return {
        ok: false,
        destination: { screen: 'contacts' },
        message: 'That contact is no longer available. Showing contacts instead.'
      };
    }
  }

  if (destination.messageId) {
    const message = state.messages.find(item => item.id === destination.messageId);
    if (!message) {
      return {
        ok: false,
        destination: { screen: 'messages' },
        message: 'That message is no longer available. Showing the message queue instead.'
      };
    }
    const contactExists = state.contacts.some(contact => contact.id === message.contactId);
    if (!contactExists) {
      return {
        ok: false,
        destination: { screen: 'messages' },
        message: "That message's contact is no longer available. Showing the message queue instead."
      };
    }
    if (message.status !== 'Needs review' && destination.screen === 'wishPreview') {
      return {
        ok: true,
        destination: {
          screen: 'wishPreview',
          messageId: message.id,
          contactId: message.contactId
        },
        message: 'This message is already handled. Opening it for reference only.'
      };
    }
    return {
      ok: true,
      destination: {
        screen: 'wishPreview',
        messageId: message.id,
        contactId: message.contactId
      }
    };
  }

  return {
    ok: true,
    destination
  };
};
