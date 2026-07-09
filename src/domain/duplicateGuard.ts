import type { AppState, MessageDraft } from './types';

type DuplicateRiskSeverity = 'Scheduled' | 'Sent' | 'Draft';

export type DuplicateRisk =
  | {
      risk: true;
      message: string;
      matchId: string;
      severity: DuplicateRiskSeverity;
    }
  | {
      risk: false;
    };

const normalize = (value: string) =>
  value
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .split(/\s+/)
    .filter(token => token.length > 2);

const similarity = (left: string, right: string) => {
  const leftTokens = new Set(normalize(left));
  const rightTokens = new Set(normalize(right));
  if (leftTokens.size === 0 || rightTokens.size === 0) {
    return 0;
  }
  const overlap = [...leftTokens].filter(token => rightTokens.has(token)).length;
  return overlap / Math.min(leftTokens.size, rightTokens.size);
};

const severityFor = (status: MessageDraft['status']): DuplicateRiskSeverity =>
  status === 'Sent' ? 'Sent' : status === 'Scheduled' ? 'Scheduled' : 'Draft';

const riskMessage = (severity: DuplicateRiskSeverity) =>
  severity === 'Sent'
    ? 'A similar message was already sent. Explicitly continue only if this is intentional.'
    : severity === 'Scheduled'
      ? 'A similar message is already scheduled. Explicitly continue only if this is intentional.'
      : 'A similar message draft already exists. Review before continuing.';

export const detectDuplicateMessageRisk = (
  state: AppState,
  draft: MessageDraft
): DuplicateRisk => {
  const candidates = state.messages.filter(message => {
    if (message.id === draft.id || message.contactId !== draft.contactId || message.status === 'Rejected') {
      return false;
    }
    if (draft.eventId && message.eventId) {
      return draft.eventId === message.eventId;
    }
    if (draft.eventId && !message.eventId) {
      return false;
    }
    return message.reason === draft.reason;
  });

  const exactLifecycleMatch = candidates.find(message => message.status === 'Sent' || message.status === 'Scheduled');
  if (exactLifecycleMatch) {
    const severity = severityFor(exactLifecycleMatch.status);
    return {
      risk: true,
      matchId: exactLifecycleMatch.id,
      severity,
      message: riskMessage(severity)
    };
  }

  const sameEventDraft = candidates.find(message => draft.eventId && message.eventId === draft.eventId);
  if (sameEventDraft) {
    const severity = severityFor(sameEventDraft.status);
    return {
      risk: true,
      matchId: sameEventDraft.id,
      severity,
      message: riskMessage(severity)
    };
  }

  const similar = candidates.find(message => similarity(message.body, draft.body) >= 0.6);
  if (similar) {
    const severity = severityFor(similar.status);
    return {
      risk: true,
      matchId: similar.id,
      severity,
      message: riskMessage(severity)
    };
  }

  return { risk: false };
};
