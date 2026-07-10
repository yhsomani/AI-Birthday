import type { MessageDraft } from './types';

const APPROVAL_WINDOW_HOURS = 24 * 7;
const HOUR_MS = 60 * 60 * 1000;

export const buildMessageApprovalWindow = (approvedAtIso: string) => {
  const approvedAt = new Date(approvedAtIso);
  const safeApprovedAt = Number.isNaN(approvedAt.getTime()) ? new Date() : approvedAt;
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
