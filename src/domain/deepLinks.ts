import type { AppState, Screen } from './types';

export type DeepLinkDestination = {
  screen: Screen;
  contactId?: string;
  eventId?: string;
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
const safeReference = (value: string | null): string | undefined => {
  const normalized = value?.trim();
  return normalized && normalized.length <= 256 && !/[\u0000-\u001f\u007f]/.test(normalized) ? normalized : undefined;
};

const segmentsFromUrl = (rawUrl: string) => {
  const url = new URL(rawUrl);
  const scheme = url.protocol.replace(':', '');
  const segments = [url.hostname, ...url.pathname.split('/')]
    .map(segment => decodeURIComponent(segment.trim()))
    .filter(Boolean);

  return {
    scheme,
    segments,
    eventId: safeReference(url.searchParams.get('eventId')),
    contactId: safeReference(url.searchParams.get('contactId')),
    hasEventId: url.searchParams.has('eventId'),
    hasContactId: url.searchParams.has('contactId')
  };
};

export const parseRelateDeepLink = (rawUrl: string): DeepLinkParseResult => {
  try {
    if (rawUrl.length === 0 || rawUrl.length > 4096) throw new Error('invalid link length');
    const { scheme, segments, eventId, contactId, hasEventId, hasContactId } = segmentsFromUrl(rawUrl);
    if (scheme !== 'relateai') {
      return {
        ok: false,
        fallback: safeFallback,
        message: 'This link is not a RelateAI link.'
      };
    }

    const [route, rawId] = segments;
    const id = safeReference(rawId ?? null);
    if (rawId !== undefined && !id) {
      return { ok: false, fallback: safeFallback, message: 'This link contains an invalid reference.' };
    }
    const secondaryDestination = (screen: Screen): DeepLinkParseResult =>
      rawId === undefined
        ? { ok: true, destination: { screen } }
        : {
            ok: false,
            fallback: { screen: 'more' },
            message: 'This secondary destination link contains an unexpected reference. Showing More instead.'
          };
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
        if ((hasEventId && !eventId) || (hasContactId && !contactId)) {
          return {
            ok: false,
            fallback: { screen: 'events' },
            message: 'The event link contains an invalid event or contact reference.'
          };
        }
        if (eventId) {
          return { ok: true, destination: { screen: 'events', eventId, contactId } };
        }
        return { ok: true, destination: { screen: 'events' } };
      case 'event':
      case 'add-event':
        if (id === 'new' || route === 'add-event') {
          return { ok: true, destination: { screen: 'eventForm' } };
        }
        return id
          ? { ok: true, destination: { screen: 'events', eventId: id, contactId } }
          : {
              ok: false,
              fallback: { screen: 'events' },
              message: 'The event link is missing an event reference.'
            };
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
        return secondaryDestination('settings');
      case 'setup':
        return secondaryDestination('setupCheck');
      case 'backup':
        return secondaryDestination('backup');
      case 'analytics':
        return secondaryDestination('analytics');
      case 'style':
        return secondaryDestination('styleCoach');
      case 'activity':
        return secondaryDestination('activityHistory');
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

export const resolveDeepLinkDestination = (state: AppState, destination: DeepLinkDestination): DeepLinkResolution => {
  if (destination.eventId) {
    const event = state.events.find(item => item.id === destination.eventId);
    if (!event) {
      return {
        ok: false,
        destination: { screen: 'events' },
        message: 'That event is no longer available. Showing events instead.'
      };
    }
    const contact = state.contacts.find(item => item.id === event.contactId && !item.archivedAt);
    if (!contact) {
      return {
        ok: false,
        destination: { screen: 'events' },
        message: "That event's contact is no longer available. Showing events instead."
      };
    }
    if (destination.contactId && destination.contactId !== event.contactId) {
      return {
        ok: false,
        destination: { screen: 'events' },
        message: 'The event link no longer matches its contact. Showing events instead.'
      };
    }
    return {
      ok: true,
      destination: { screen: 'events', eventId: event.id, contactId: event.contactId }
    };
  }

  if (destination.contactId) {
    const exists = state.contacts.some(contact => contact.id === destination.contactId && !contact.archivedAt);
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
    const contactExists = state.contacts.some(contact => contact.id === message.contactId && !contact.archivedAt);
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
