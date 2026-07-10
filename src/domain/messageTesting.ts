import { messageApprovalRouteIssue } from './messageInbox';
import { validateMessageBodyForChannel } from './messageBodyPolicy';
import type { AppState, MessageDraft } from './types';

export type MessageTestPlan =
  | {
      ok: true;
      title: 'Test send ready';
      channel: MessageDraft['channel'];
      targetLabel: string;
      detail: string;
      sentToRecipient: false;
    }
  | {
      ok: false;
      title: 'Test send blocked';
      channel: MessageDraft['channel'];
      targetLabel: 'No recipient contacted';
      issue: string;
      sentToRecipient: false;
    };

const targetLabelFor = (message: MessageDraft, contactName: string) => {
  if (message.channel === 'SMS') {
    return `SMS handoff route for ${contactName}`;
  }
  if (message.channel === 'WhatsApp') {
    return `WhatsApp handoff route for ${contactName}`;
  }
  if (message.channel === 'Email') {
    return `Email route for ${contactName}`;
  }
  return `Manual share route for ${contactName}`;
};

export const buildMessageTestPlan = (state: AppState, message: MessageDraft): MessageTestPlan => {
  const contact = state.contacts.find(item => item.id === message.contactId);
  if (!contact) {
    return {
      ok: false,
      title: 'Test send blocked',
      channel: message.channel,
      targetLabel: 'No recipient contacted',
      issue: 'The contact is no longer available.',
      sentToRecipient: false
    };
  }

  if (message.status === 'Sent' || message.status === 'Rejected') {
    return {
      ok: false,
      title: 'Test send blocked',
      channel: message.channel,
      targetLabel: 'No recipient contacted',
      issue: 'Only active drafts or scheduled messages can be tested.',
      sentToRecipient: false
    };
  }

  const bodyPolicy = validateMessageBodyForChannel(message);
  if (!bodyPolicy.ok) {
    return {
      ok: false,
      title: 'Test send blocked',
      channel: message.channel,
      targetLabel: 'No recipient contacted',
      issue: bodyPolicy.message,
      sentToRecipient: false
    };
  }

  const routeIssue = messageApprovalRouteIssue(state, message);
  if (routeIssue) {
    return {
      ok: false,
      title: 'Test send blocked',
      channel: message.channel,
      targetLabel: 'No recipient contacted',
      issue: routeIssue,
      sentToRecipient: false
    };
  }

  const targetLabel = targetLabelFor(message, contact.name);
  return {
    ok: true,
    title: 'Test send ready',
    channel: message.channel,
    targetLabel,
    detail: `${targetLabel} passed a safe route test.${bodyPolicy.warning ? ` ${bodyPolicy.warning}` : ''} No message was sent to ${contact.name}; approval and final delivery still require explicit user action.`,
    sentToRecipient: false
  };
};
