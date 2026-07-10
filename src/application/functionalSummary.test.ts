import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import {
  buildFunctionalIssueSummary,
  buildFunctionalOperationSummary,
  buildFunctionalStateSummary
} from './functionalSummary';

describe('redacted functional harness summaries', () => {
  it('reports counts and readiness without names, routes, notes, or message bodies', () => {
    const state = createTestState();
    const summary = buildFunctionalStateSummary(state).join('\n');
    assert.match(summary, /contacts=\d+/);
    assert.match(summary, /persistence=/);
    assert.doesNotMatch(summary, /Asha|98765|example\.com|Private note|Happy birthday/i);
  });

  it('reports only operation identity/status and issue correlation metadata', () => {
    const operations = buildFunctionalOperationSummary([
      {
        scope: 'email:message-1',
        requestId: 'request-1',
        status: 'unknown',
        startedAt: '2026-07-10T00:00:00.000Z',
        attempt: 1,
        error: { code: 'delivery-unknown', retryable: false, summary: 'hidden detail' }
      }
    ]).join('\n');
    const issues = buildFunctionalIssueSummary([
      {
        id: 'issue-1',
        correlationId: 'relateai-issue-1',
        code: 'provider-delivery-unknown',
        severity: 'blocking',
        occurredAt: '2026-07-10T00:00:00.000Z',
        summary: 'hidden detail',
        recovery: 'reconcile',
        attempts: 1
      }
    ]).join('\n');
    assert.doesNotMatch(`${operations}\n${issues}`, /hidden detail/);
    assert.match(operations, /request-1/);
    assert.match(issues, /relateai-issue-1/);
  });
});
