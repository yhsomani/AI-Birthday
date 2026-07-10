import { messageApprovalWindowIssue } from './messageApproval';
import { validateMessageBodyForChannel } from './messageBodyPolicy';
import { assessDuplicateMessageRisk } from './duplicateGuard';
import { messageDispatchTimingIssue } from './schedulingPolicy';
import type { AppState, MessageDraft } from './types';

export type EmailDeliveryErrorKind =
  | 'disabled'
  | 'missing-message'
  | 'missing-contact'
  | 'invalid-sender'
  | 'missing-recipient'
  | 'invalid-recipient'
  | 'not-approved'
  | 'wrong-channel'
  | 'invalid-body'
  | 'duplicate-risk'
  | 'schedule-blocked'
  | 'not-configured'
  | 'auth'
  | 'network'
  | 'delivery-unknown'
  | 'quota'
  | 'server'
  | 'invalid-response';

export type EmailDeliveryError = {
  kind: EmailDeliveryErrorKind;
  message: string;
};

export type EmailDeliveryRequest = {
  /** Stable for one approved message version; the backend must enforce uniqueness. */
  idempotencyKey: string;
  messageId: string;
  contactId: string;
  senderEmail: string;
  recipientEmail: string;
  subject: string;
  body: string;
  privacy: {
    excludedFields: string[];
  };
};

export type EmailDeliveryRequestResult =
  | {
      ok: true;
      request: EmailDeliveryRequest;
    }
  | {
      ok: false;
      error: EmailDeliveryError;
    };

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const normalizeEmailAddress = (email: string | undefined) => email?.trim().toLowerCase() ?? '';

export const isValidEmailAddress = (email: string | undefined) => emailPattern.test(normalizeEmailAddress(email));

const fail = (kind: EmailDeliveryErrorKind, message: string): EmailDeliveryRequestResult => ({
  ok: false,
  error: {
    kind,
    message
  }
});

const buildSubject = (message: MessageDraft) =>
  message.reason === 'Birthday'
    ? 'Birthday wishes'
    : message.reason === 'Congratulations'
      ? 'Congratulations'
      : message.reason === 'Thanks'
        ? 'Thank you'
        : 'A note for you';

export const emailDeliveryIdempotencyKey = (message: MessageDraft) =>
  `relateai-email-v1:${message.id}:${message.approvedAt ?? 'unapproved'}`;

export const buildEmailDeliveryRequest = (
  state: AppState,
  messageId: string,
  now: Date
): EmailDeliveryRequestResult => {
  if (!state.settings.emailEnabled) {
    return fail('disabled', 'Email delivery is disabled in Settings.');
  }

  const senderEmail = normalizeEmailAddress(state.emailDelivery.senderEmail);
  if (!senderEmail) {
    return fail('invalid-sender', 'Add a sender email before sending email messages.');
  }
  if (!isValidEmailAddress(senderEmail)) {
    return fail('invalid-sender', 'Sender email is not valid.');
  }

  const message = state.messages.find(item => item.id === messageId);
  if (!message) {
    return fail('missing-message', 'The selected message could not be found.');
  }
  if (message.channel !== 'Email') {
    return fail('wrong-channel', 'This message is not configured for email delivery.');
  }
  if (message.status !== 'Scheduled') {
    return fail('not-approved', 'Approve the email message before sending.');
  }
  const approvalIssue = messageApprovalWindowIssue(message, now.toISOString());
  if (approvalIssue) {
    return fail('not-approved', approvalIssue);
  }
  const bodyPolicy = validateMessageBodyForChannel(message);
  if (!bodyPolicy.ok) {
    return fail('invalid-body', bodyPolicy.message);
  }
  const duplicateRisk = assessDuplicateMessageRisk(state, message);
  if (duplicateRisk.risk.risk && !duplicateRisk.acknowledged) {
    return fail(
      'duplicate-risk',
      'Duplicate risk changed after approval. Review and explicitly acknowledge the current warning before sending.'
    );
  }
  const timingIssue = messageDispatchTimingIssue(state, message, now);
  if (timingIssue) {
    return fail('schedule-blocked', timingIssue);
  }

  const contact = state.contacts.find(item => item.id === message.contactId);
  if (!contact) {
    return fail('missing-contact', 'The email recipient could not be found.');
  }
  if (contact.archivedAt) {
    return fail('missing-contact', 'The email recipient is archived. Restore the contact before sending.');
  }
  if (contact.dnd) {
    return fail('disabled', 'Contact do-not-disturb allows only a deliberate manual handoff.');
  }
  if (!contact.email) {
    return fail('missing-recipient', 'Recipient email address is missing.');
  }
  const recipientEmail = normalizeEmailAddress(contact.email);
  if (!isValidEmailAddress(recipientEmail)) {
    return fail('invalid-recipient', 'Recipient email address is not valid.');
  }

  return {
    ok: true,
    request: {
      idempotencyKey: emailDeliveryIdempotencyKey(message),
      messageId: message.id,
      contactId: contact.id,
      senderEmail,
      recipientEmail,
      subject: buildSubject(message),
      body: message.body.trim(),
      privacy: {
        excludedFields: ['phone numbers', 'private memories', 'credentials', 'raw contact provider ids']
      }
    }
  };
};

export const classifyEmailProviderStatus = (status: number): EmailDeliveryError => {
  if (status === 401 || status === 403) {
    return {
      kind: 'auth',
      message: 'Email provider authentication failed. Check sender setup.'
    };
  }
  if (status === 429) {
    return {
      kind: 'quota',
      message: 'Email provider is rate limited or out of quota. Try again later.'
    };
  }
  if (status >= 500) {
    return {
      kind: 'server',
      message: 'Email provider is temporarily unavailable.'
    };
  }
  return {
    kind: 'network',
    message: `Email provider returned HTTP ${status}.`
  };
};
