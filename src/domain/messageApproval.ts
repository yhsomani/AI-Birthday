import type { MessageDraft } from './types';

const APPROVAL_WINDOW_HOURS = 24 * 7;
const HOUR_MS = 60 * 60 * 1000;

export type MessageLifecycleAction =
  'edit' | 'select-variant' | 'approve' | 'acknowledge-duplicate' | 'reject' | 'revoke' | 'retry';

const allowedStatuses: Record<MessageLifecycleAction, ReadonlySet<MessageDraft['status']>> = {
  edit: new Set(['Needs review', 'Draft']),
  'select-variant': new Set(['Needs review', 'Draft']),
  approve: new Set(['Needs review', 'Draft']),
  'acknowledge-duplicate': new Set(['Needs review', 'Draft', 'Blocked']),
  reject: new Set(['Needs review', 'Draft', 'Blocked', 'Failed']),
  revoke: new Set(['Scheduled']),
  retry: new Set(['Blocked', 'Failed'])
};

const transitionIssue: Record<MessageLifecycleAction, string> = {
  edit: 'Only review or draft messages can be edited.',
  'select-variant': 'Only review or draft messages can change variants.',
  approve: 'Only review or draft messages can be approved.',
  'acknowledge-duplicate': 'Duplicate risk can only be acknowledged while reviewing an unsent message.',
  reject: 'Only unsent review, blocked, or failed messages can be rejected.',
  revoke: 'Only scheduled messages can have approval revoked.',
  retry: 'Only failed or blocked messages can be prepared for retry.'
};

export const messageLifecycleTransitionIssue = (message: MessageDraft, action: MessageLifecycleAction) =>
  allowedStatuses[action].has(message.status) ? undefined : transitionIssue[action];

export const buildMessageApprovalWindow = (approvedAtIso: string) => {
  const approvedAt = new Date(approvedAtIso);
  const safeApprovedAt = Number.isNaN(approvedAt.getTime()) ? new Date(0) : approvedAt;
  return {
    approvedAt: safeApprovedAt.toISOString(),
    approvalExpiresAt: new Date(safeApprovedAt.getTime() + APPROVAL_WINDOW_HOURS * HOUR_MS).toISOString()
  };
};

export const messageApprovalWindowIssue = (message: MessageDraft, nowIso = new Date().toISOString()) => {
  if (message.status !== 'Scheduled') {
    return undefined;
  }
  if (!message.approvedAt || !message.approvalExpiresAt) {
    return 'Approval timestamp is missing. Review before sending.';
  }
  const approvalExpiresAt = new Date(message.approvalExpiresAt);
  const now = new Date(nowIso);
  if (Number.isNaN(approvalExpiresAt.getTime()) || Number.isNaN(now.getTime())) {
    return 'Approval window is invalid. Review before sending.';
  }
  return approvalExpiresAt.getTime() <= now.getTime()
    ? 'Approval expired. Review the message before sending.'
    : undefined;
};
