import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import {
  buildMessageApprovalWindow,
  messageLifecycleTransitionIssue,
  type MessageLifecycleAction
} from './messageApproval';
import type { MessageStatus } from './types';

describe('message approval lifecycle policy', () => {
  it('defines the allowed source statuses for every review transition', () => {
    const base = createTestState().messages[0];
    const allowed: Record<MessageLifecycleAction, MessageStatus[]> = {
      edit: ['Needs review', 'Draft'],
      'select-variant': ['Needs review', 'Draft'],
      approve: ['Needs review', 'Draft'],
      'acknowledge-duplicate': ['Needs review', 'Draft', 'Blocked'],
      reject: ['Needs review', 'Draft', 'Blocked', 'Failed'],
      revoke: ['Scheduled'],
      retry: ['Blocked', 'Failed']
    };
    const statuses: MessageStatus[] = [
      'Needs review',
      'Draft',
      'Blocked',
      'Failed',
      'Scheduled',
      'Sent',
      'Rejected',
      'Delivery pending',
      'Delivery unknown'
    ];

    for (const [action, eligibleStatuses] of Object.entries(allowed) as [MessageLifecycleAction, MessageStatus[]][]) {
      for (const status of statuses) {
        const issue = messageLifecycleTransitionIssue({ ...base, status }, action);
        assert.equal(issue === undefined, eligibleStatuses.includes(status), `${action} from ${status}`);
      }
    }
  });

  it('uses injected valid approval time and a deterministic fallback for invalid metadata', () => {
    assert.deepEqual(buildMessageApprovalWindow('2026-07-10T09:00:00.000Z'), {
      approvedAt: '2026-07-10T09:00:00.000Z',
      approvalExpiresAt: '2026-07-17T09:00:00.000Z'
    });
    assert.deepEqual(buildMessageApprovalWindow('invalid'), {
      approvedAt: '1970-01-01T00:00:00.000Z',
      approvalExpiresAt: '1970-01-08T00:00:00.000Z'
    });
  });
});
