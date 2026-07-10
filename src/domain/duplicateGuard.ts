import type { AppState, MessageDraft } from './types';
import { eventOccurrenceLocalDateKey, recurrenceForEvent, utcDateKey, yearlyOccurrenceDateKey } from './occasionDates';

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

export type DuplicateRiskAssessment = {
  risk: DuplicateRisk;
  fingerprint?: string;
  acknowledged: boolean;
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

const compareIds = (left: string, right: string) => (left < right ? -1 : left > right ? 1 : 0);

const duplicatePriority = (message: MessageDraft) =>
  message.status === 'Sent' ? 0 : message.status === 'Scheduled' ? 1 : 2;

const localDatePattern = /^\d{4}-\d{2}-\d{2}$/;

const isValidLocalDate = (value: string | undefined): value is string => {
  if (!value || !localDatePattern.test(value)) return false;
  const [year, month, day] = value.split('-').map(Number);
  const parsed = new Date(Date.UTC(year, month - 1, day));
  return parsed.getUTCFullYear() === year && parsed.getUTCMonth() === month - 1 && parsed.getUTCDate() === day;
};

const closestYearlyOccurrence = (state: AppState, message: MessageDraft) => {
  const event = state.events.find(item => item.id === message.eventId);
  const recurrence = event ? recurrenceForEvent(event) : undefined;
  const referenceIso = message.scheduledFor ?? message.sentAt ?? message.approvedAt;
  if (!recurrence || !referenceIso) return event ? utcDateKey(event.date) : undefined;
  const reference = new Date(referenceIso);
  if (Number.isNaN(reference.getTime())) return utcDateKey(event?.date ?? '');
  const year = reference.getUTCFullYear();
  return [year - 1, year, year + 1]
    .map(candidateYear => yearlyOccurrenceDateKey(recurrence, candidateYear))
    .filter((candidate): candidate is string => candidate !== undefined)
    .sort(
      (left, right) =>
        Math.abs(Date.parse(`${left}T12:00:00.000Z`) - reference.getTime()) -
          Math.abs(Date.parse(`${right}T12:00:00.000Z`) - reference.getTime()) || left.localeCompare(right)
    )[0];
};

/**
 * Resolves the stable occasion a message targets. New records carry the key
 * directly; legacy records are recovered from their saved schedule/history.
 */
export const messageOccurrenceDate = (state: AppState, message: MessageDraft): string | undefined => {
  if (!message.eventId) return undefined;
  if (isValidLocalDate(message.occurrenceDate)) return message.occurrenceDate;
  const event = state.events.find(item => item.id === message.eventId);
  if (!event) return undefined;
  return recurrenceForEvent(event) ? closestYearlyOccurrence(state, message) : utcDateKey(event.date);
};

export const messageTargetsEventOccurrence = (
  state: AppState,
  message: MessageDraft,
  eventId: string,
  reference: Date = new Date()
) => {
  const event = state.events.find(item => item.id === eventId);
  if (!event || message.eventId !== event.id) return false;
  const targetOccurrence = eventOccurrenceLocalDateKey(event, reference);
  const messageOccurrence = messageOccurrenceDate(state, message);
  return Boolean(targetOccurrence && messageOccurrence && targetOccurrence === messageOccurrence);
};

const sameDuplicateScope = (state: AppState, left: MessageDraft, right: MessageDraft) => {
  if (left.eventId || right.eventId) {
    if (!left.eventId || left.eventId !== right.eventId) return false;
    const leftOccurrence = messageOccurrenceDate(state, left);
    const rightOccurrence = messageOccurrenceDate(state, right);
    return leftOccurrence !== undefined && leftOccurrence === rightOccurrence;
  }
  return true;
};

const duplicateCandidates = (state: AppState, draft: MessageDraft) =>
  state.messages
    .filter(message => {
      if (message.id === draft.id || message.contactId !== draft.contactId || message.status === 'Rejected') {
        return false;
      }
      if (!sameDuplicateScope(state, draft, message)) return false;
      return draft.eventId !== undefined || similarity(message.body, draft.body) >= 0.6;
    })
    .sort((left, right) => duplicatePriority(left) - duplicatePriority(right) || compareIds(left.id, right.id));

const stableHash = (value: string, seed: number) => {
  let hash = seed >>> 0;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash.toString(16).padStart(8, '0');
};

const canonicalValue = (value: unknown): unknown => {
  if (Array.isArray(value)) return value.map(canonicalValue);
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>)
        .sort(([left], [right]) => compareIds(left, right))
        .map(([key, nested]) => [key, canonicalValue(nested)])
    );
  }
  return value;
};

/** Opaque revision used to reject stale asynchronous regeneration results. */
export const messageDraftRevision = (message: MessageDraft) => {
  const payload = JSON.stringify(canonicalValue(message));
  return `message-revision-v1-${stableHash(payload, 0x811c9dc5)}${stableHash(payload, 0x9e3779b9)}`;
};

/**
 * Binds one duplicate-risk acknowledgement to the exact draft and relevant
 * queue snapshot that the user reviewed. This is a local stale-consent guard,
 * not a cryptographic integrity primitive.
 */
export const duplicateRiskFingerprint = (
  state: AppState,
  draft: MessageDraft,
  risk: Extract<DuplicateRisk, { risk: true }>
) => {
  const payload = JSON.stringify({
    version: 1,
    draft: {
      id: draft.id,
      body: draft.body,
      contactId: draft.contactId,
      eventId: draft.eventId ?? null,
      occurrenceDate: messageOccurrenceDate(state, draft) ?? null,
      reason: draft.reason,
      channel: draft.channel
    },
    risk: {
      matchId: risk.matchId,
      severity: risk.severity
    },
    queue: duplicateCandidates(state, draft)
      .map(message => ({
        id: message.id,
        body: message.body,
        contactId: message.contactId,
        eventId: message.eventId ?? null,
        occurrenceDate: messageOccurrenceDate(state, message) ?? null,
        reason: message.reason,
        status: message.status,
        scheduledFor: message.scheduledFor ?? null,
        sentAt: message.sentAt ?? null,
        approvedAt: message.approvedAt ?? null,
        deliveryStatus: message.emailDeliveryAttempt?.status ?? null
      }))
      .sort((left, right) => compareIds(left.id, right.id))
  });

  return `duplicate-risk-v2-${stableHash(payload, 0x811c9dc5)}${stableHash(payload, 0x9e3779b9)}`;
};

export const detectDuplicateMessageRisk = (state: AppState, draft: MessageDraft): DuplicateRisk => {
  const candidates = duplicateCandidates(state, draft);

  const exactLifecycleMatch = draft.eventId
    ? candidates.find(message => message.status === 'Sent' || message.status === 'Scheduled')
    : undefined;
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

  const similar = candidates[0];
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

export const assessDuplicateMessageRisk = (state: AppState, draft: MessageDraft): DuplicateRiskAssessment => {
  const risk = detectDuplicateMessageRisk(state, draft);
  if (!risk.risk) {
    return { risk, acknowledged: false };
  }
  const fingerprint = duplicateRiskFingerprint(state, draft, risk);
  return {
    risk,
    fingerprint,
    acknowledged: draft.duplicateAcknowledged === true && draft.duplicateAcknowledgementFingerprint === fingerprint
  };
};
