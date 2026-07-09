import type { AppState, MessageDraft, ReminderPlan, RelationshipEvent } from './types';
import { buildDefaultEventChecklist } from './events';

export type FollowUpDelayDays = 1 | 7;

export type MessageFollowUpPlan =
  | {
      ok: true;
      event: RelationshipEvent;
      reminderPlan: ReminderPlan;
      message: MessageDraft;
    }
  | {
      ok: false;
      reason: string;
    };

const followUpDate = (nowIso: string, delayDays: FollowUpDelayDays) => {
  const date = new Date(nowIso);
  if (Number.isNaN(date.getTime())) {
    return undefined;
  }
  date.setUTCDate(date.getUTCDate() + delayDays);
  date.setUTCHours(9, 0, 0, 0);
  return date;
};

export const buildMessageFollowUpPlan = (
  state: AppState,
  messageId: string,
  delayDays: FollowUpDelayDays,
  nowIso = new Date().toISOString()
): MessageFollowUpPlan => {
  const message = state.messages.find(item => item.id === messageId);
  if (!message) {
    return { ok: false, reason: 'The message is no longer available.' };
  }
  if (message.status !== 'Sent') {
    return { ok: false, reason: 'Follow-up reminders can only be scheduled after a message is sent.' };
  }

  const contact = state.contacts.find(item => item.id === message.contactId);
  if (!contact) {
    return { ok: false, reason: 'The contact for this message is no longer available.' };
  }

  const date = followUpDate(nowIso, delayDays);
  if (!date) {
    return { ok: false, reason: 'A valid follow-up date could not be calculated.' };
  }

  const dateKey = date.toISOString().slice(0, 10);
  const duplicate = state.events.find(
    event => event.contactId === contact.id && event.type === 'Follow-up' && event.date.slice(0, 10) === dateKey
  );
  if (duplicate) {
    return { ok: false, reason: 'A follow-up reminder already exists for this contact on that date.' };
  }

  const label =
    delayDays === 1
      ? `Follow up with ${contact.name} tomorrow`
      : `Follow up with ${contact.name} next week`;
  const event: RelationshipEvent = {
    id: `followup-${message.id}-${delayDays}`,
    contactId: contact.id,
    type: 'Follow-up',
    label,
    date: date.toISOString(),
    verified: true,
    source: 'Manual',
    checklist: buildDefaultEventChecklist('Follow-up')
  };
  const reminderPlan: ReminderPlan = {
    id: `reminder-${event.id}`,
    eventId: event.id,
    contactId: contact.id,
    title: label,
    body: 'Ask how things went and continue the conversation. Review before sending anything.',
    triggerAt: date.toISOString()
  };

  return {
    ok: true,
    event,
    reminderPlan,
    message
  };
};
