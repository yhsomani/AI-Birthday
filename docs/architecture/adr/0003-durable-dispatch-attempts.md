# ADR 0003: Durable Dispatch Attempts

Date: 2026-06-27

Status: Accepted

## Context

RelateAI can send user-visible messages through SMS, WhatsApp automation, and email. Current code has important safety checks but dispatch history is split across multiple concerns:

- `DispatchEligibilityPolicy` decides send, defer, approval-needed, expiry, and blocked states.
- `DispatchMessageUseCase` records activity logs for dispatch decisions.
- `MessageDispatchWorker` marks pending messages as `DISPATCHING`, `FAILED`, `EXPIRED`, or leaves them for approval/defer flows.
- `MessageDispatcher` inserts `sent_messages` rows on successful routes and marks SMS delivery status as pending, failed, or sent.
- `DeadLetterQueue` is an in-memory list, so failed-send recovery state is not durable across process death.
- `PLAN.md` Sections 9, 10, 13, and 19 require a `DispatchAttempt` aggregate and `dispatch_attempts` target table.

The current model cannot fully answer: what route was tried, why it was eligible, what provider result occurred, how many retries happened, and what is recoverable after restart.

## Decision

The rebuild will make dispatch attempts a durable aggregate.

Every automatic or user-triggered send attempt must create a `dispatch_attempts` row before invoking a channel sender. The row records the decision, route, timestamps, redacted provider outcome, retry state, and dead-letter state.

Implemented 2026-06-27: Room v15 now includes the `dispatch_attempts` compatibility table, DAO, pure-model mapper, schema export, migration tests, backup v3 export/import support, sender orchestration writes from `DispatchMessageUseCase`, `MessageDispatchWorker`, and `MessageDispatcher`, AI Doctor recovery diagnostics backed by persisted attempts, and Messages retry actions that mark failed/dead-letter rows as `RETRY_QUEUED` before scheduling retry execution. Sender outcome updates can stamp the resolved route channel when fallback changes the original preferred channel. The table currently points `messageDraftId` to `pending_messages.id` and nullable `occasionId` to the existing `events.id` table until the target `message_drafts` and `occasions` migration lands. Provider-specific retry policy remains pending.

Minimum target fields:

- `id`
- `messageDraftId`
- `contactId`
- `occasionId`
- `channel`
- `routeRank`
- `eligibilityDecision`
- `blockOrDeferReason`
- `requestedAtMs`
- `attemptedAtMs`
- `resolvedAtMs`
- `result`
- `deliveryStatus`
- `providerMessageId`
- `errorType`
- `errorCode`
- `redactedErrorMessage`
- `retryCount`
- `nextRetryAtMs`
- `deadLetteredAtMs`
- `createdBy`

Allowed results:

- `QUEUED`
- `SENT`
- `PENDING_DELIVERY`
- `DELIVERED`
- `DEFERRED`
- `NEEDS_APPROVAL`
- `BLOCKED`
- `EXPIRED`
- `FAILED_RETRYABLE`
- `FAILED_FINAL`
- `CANCELLED`

## Rules

1. No sender may run without a persisted attempt id.
2. Eligibility decisions must be persisted even when no send occurs.
3. Automatic retries must read from persisted attempts, not in-memory state.
4. Provider errors must be redacted before persistence.
5. `sent_messages` remains the user-facing sent-history table, not the authoritative attempt log.
6. Activity logs remain user/audit timeline entries and link to dispatch attempts where useful.

## Consequences

Positive:

- Process death no longer erases failed-send recovery state.
- Route fallback behavior becomes auditable.
- Failed recovery UI can be backed by durable state.
- Tests can assert no automatic send happens without a persisted decision.

Costs:

- Retry actions now read and update persisted failure/dead-letter rows instead of in-memory diagnostics.
- Existing worker tests must keep verifying attempt lifecycle, not only pending-message status.
- Channel senders use `DispatchProviderRetryPolicy` for the current provider-specific retry taxonomy; WhatsApp remains coarse because the sender currently exposes only a Boolean result.

## Verification

The decision is fully implemented when:

- Completed 2026-06-27: `dispatch_attempts` exists in the Room schema.
- Completed 2026-06-27: worker and use-case tests prove attempts are written for send, defer, approval-needed, blocked, expired, contact-missing, no-route, and failed outcomes.
- Completed 2026-06-27: AI Doctor recovery diagnostics read persisted recovery/dead-letter rows and surface the latest row summary.
- Completed 2026-06-27: Messages retry actions write a persisted `RETRY_QUEUED` state before scheduling the existing dispatch worker.
- Completed 2026-06-27: sender failures classify SMS, WhatsApp, and email outcomes into provider-specific final or retryable dispatch-attempt results.
- There is no production call path from a worker or ViewModel directly to a channel sender without a persisted attempt.
- Failed attempts survive process restart and appear in recovery UI or diagnostics.
