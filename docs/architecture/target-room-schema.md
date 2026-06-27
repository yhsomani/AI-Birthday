# Target Room Schema and Version 15 Migration Design

Date: 2026-06-27

Status: Living design accepted for Phase 1 implementation

This document expands `PLAN.md` Section 10 and the ADRs in `docs/architecture/adr/`.
It is a design contract. The Room v15 `dispatch_attempts` slice and first production sender orchestration writes are implemented; retry/recovery UI plus the remaining target `occasions` and `message_drafts` migration are pending.

## Current Baseline

Current Room baseline: version 15.

Current tables from `AppDatabase` and schema export:

- `contacts`
- `events`
- `pending_messages`
- `sent_messages`
- `style_profiles`
- `style_profile_history`
- `memory_notes`
- `gift_history`
- `activity_logs`
- `message_feedback`
- `dispatch_attempts`

Important current facts:

- `pending_messages.contactId` cascades on contact delete.
- `pending_messages.eventId` is not a Room foreign key in version 15.
- `pending_messages` has unique `(contactId, eventId, scheduledYear)`.
- `sent_messages.contactId` sets null on contact delete.
- `sent_messages` now has `eventId`, `occasionType`, and `occasionLabel`.
- Synthetic holiday, revival, and follow-up flows now create deterministic `events` rows before pending messages.
- `dispatch_attempts` was added in v15. It references `pending_messages.id` through `messageDraftId`, nullable `contactId` through `contacts.id`, and nullable `occasionId` through the current `events.id` compatibility table until first-class `occasions` lands.
- Backup format version 3 includes `dispatchAttempts` in export, preview counts, and replace restore.
- `DispatchMessageUseCase`, `MessageDispatchWorker`, and `MessageDispatcher` now create/update dispatch attempts for send, defer, approval-needed, blocked, expired, contact-missing, no-route, success, and final-failure outcomes before or during channel dispatch. Sender outcome updates can stamp the resolved route channel when fallback changes the original preferred channel.
- AI Doctor recovery diagnostics read persisted failure/dead-letter rows from `dispatch_attempts` and surface recovery count, dead-letter count, and latest persisted row summary.
- Messages retry actions mark the latest persisted failure/dead-letter row as `RETRY_QUEUED` with incremented retry count and `nextRetryAtMs` before resetting the draft to `APPROVED` and scheduling the existing dispatch worker. Legacy failed drafts without an attempt get a retry marker row.
- Provider-specific sender failures now use `DispatchProviderRetryPolicy`: final setup failures stamp `FAILED_FINAL` and `deadLetteredAtMs`, while retryable SMS/email provider failures stamp `FAILED_RETRYABLE` and `nextRetryAtMs`.

## Target Principles

The target schema must support the decisions in:

- ADR 0001: Domain Purity and Module Boundaries.
- ADR 0002: Occasion Model.
- ADR 0003: Durable Dispatch Attempts.
- ADR 0004: Database Keying and Backup Recovery.

Rules:

- Domain ids and semantic types are separate columns.
- A generated draft references one occasion occurrence.
- A send attempt is durable before any channel sender runs.
- Sent history is a user-facing history table, not the authoritative retry log.
- Deleting a contact must not corrupt audit/sent history.
- Migration from version 15 must preserve existing user-visible data.

## Target Tables

### `contacts`

Purpose: canonical user-visible contact aggregate.

Keep the current table initially to reduce migration risk. Later normalization can split methods and source identities when contact sync deletion/reconciliation is rebuilt.

Required follow-up:

- Move JSON string fields behind typed serializers.
- Keep `classificationConfidence`.
- Keep lifecycle flags `isArchived` and `isDeleted`.

### `contact_methods`

Purpose: normalized phone/email/channel reachability.

Phase 1 may defer this table if preserving current `contacts.primaryPhone`, `secondaryPhone`, and `primaryEmail` is safer.

Minimum columns when introduced:

- `id`
- `contactId`
- `kind`
- `value`
- `normalizedValue`
- `label`
- `isPrimary`
- `source`
- `createdAtMs`
- `updatedAtMs`

Indices:

- `(contactId, kind)`
- `(kind, normalizedValue)`

### `contact_sources`

Purpose: Google/device source identity and deletion reconciliation.

Minimum columns:

- `id`
- `contactId`
- `source`
- `sourceContactId`
- `etag`
- `lastSeenAtMs`
- `deletedAtSourceMs`
- `rawMetadataJson`

Indices:

- Unique `(source, sourceContactId)`.
- `(contactId, source)`.

### `occasions`

Purpose: first-class relationship occasions replacing the overloaded event model.

Minimum columns:

- `id`
- `contactId`
- `type`
- `label`
- `dayOfMonth`
- `month`
- `year`
- `occurrenceYear`
- `nextOccurrenceMs`
- `isActive`
- `notifyDaysBefore`
- `source`
- `confidenceScore`
- `isVerified`
- `conflictGroupId`
- `conflictState`
- `createdAtMs`
- `updatedAtMs`

Constraints:

- Foreign key `contactId -> contacts.id` with `ON DELETE CASCADE`.
- Unique `(contactId, type, occurrenceYear)` for deterministic synthetic occurrences where applicable.
- Keep canonical contact-date ids deterministic for birthday, anniversary, and work anniversary.

Indices:

- `(isActive, nextOccurrenceMs)`.
- `(contactId, type)`.
- `(source, type)`.
- `(conflictGroupId)`.

### `message_drafts`

Purpose: generated, pending, approved, rejected, expired, failed, and sent draft state.

This table replaces or renames `pending_messages`. The implementation can either migrate by table recreation or keep the physical table name with a new domain-facing repository name for one release.

Minimum columns:

- `id`
- `contactId`
- `occasionId`
- `scheduledYear`
- `shortVariant`
- `standardVariant`
- `longVariant`
- `formalVariant`
- `funnyVariant`
- `emotionalVariant`
- `selectedVariant`
- `selectedVariantText`
- `channel`
- `scheduledForMs`
- `approvalMode`
- `status`
- `aiModel`
- `generatedAtMs`
- `editedByUser`
- `userEditedText`
- `qualityScore`
- `tone`
- `length`
- `includeEmoji`
- `isUsingFallback`

Constraints:

- Foreign key `contactId -> contacts.id` with `ON DELETE CASCADE`.
- Foreign key `occasionId -> occasions.id` with `ON DELETE CASCADE`.
- Unique `(contactId, occasionId, scheduledYear)`.

Indices:

- `(status, scheduledForMs)`.
- `(contactId, status)`.
- `(occasionId)`.

### `message_feedback`

Purpose: feedback attached to a generated draft.

Target changes:

- Rename `eventId` to `occasionId`.
- Keep `pendingMessageId` or rename it to `messageDraftId`.

Constraints:

- Foreign key to message draft with `ON DELETE CASCADE`.
- Foreign key to contact with `ON DELETE CASCADE`.

### `dispatch_attempts`

Purpose: durable send decisions, route attempts, retry state, and dead-letter state.

Minimum columns:

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
- `metadataJson`

Constraints:

- Implemented v15 compatibility constraint: `messageDraftId -> pending_messages.id` with `ON DELETE CASCADE`.
- Implemented v15 compatibility constraint: nullable `contactId -> contacts.id` with `ON DELETE SET NULL`.
- Implemented v15 compatibility constraint: nullable `occasionId -> events.id` with `ON DELETE SET NULL`.
- Final target constraint: `messageDraftId -> message_drafts.id` with `ON DELETE CASCADE`.
- Final target constraint: nullable `occasionId -> occasions.id` with `ON DELETE SET NULL`.

Indices:

- `(messageDraftId, requestedAtMs)`.
- `(result, nextRetryAtMs)`.
- `(deadLetteredAtMs)`.
- `(contactId, requestedAtMs)`.
- `(occasionId)`.

### `sent_messages`

Purpose: user-facing sent history and analytics input.

Keep the current table shape initially, but target naming should move from `eventId` to `occasionId` after the `occasions` migration.

Required target fields:

- `id`
- `contactId`
- `occasionId`
- `occasionType`
- `occasionLabel`
- `eventYear` or `sentYear`
- `messageText`
- `channel`
- `sentAtMs`
- `deliveryStatus`
- `aiGenerated`
- `aiModel`
- `variantUsed`
- `replyReceived`
- `replyAtMs`
- `isContactDeleted`

Rules:

- Preserve semantic occasion fields even if the occasion row is later deleted.
- Do not use this table as the retry/dead-letter queue.

### `activity_logs`

Purpose: audit timeline for user-visible and diagnostic actions.

Target changes:

- Rename `eventId` to `occasionId`.
- Add optional `dispatchAttemptId`.
- Keep `metadataJson` redacted and typed through serializers.

### `diagnostic_snapshots`

Purpose: persisted AI Doctor and health-monitor state.

Minimum columns:

- `id`
- `status`
- `summary`
- `checksJson`
- `createdAtMs`

### `backup_manifests`

Purpose: optional local backup history metadata, not backup contents.

Minimum columns:

- `id`
- `backupVersion`
- `fileName`
- `sizeBytes`
- `createdAtMs`
- `checksum`
- `destinationHint`

No passphrase, token, API key, phone number, email, raw message, or raw contact payload may be stored here.

## Remaining Target Migration Plan From Version 15

The migration should be implemented as one reviewed Room migration or as multiple consecutive migrations if table recreation risk is too high.

Recommended staged migration:

1. Create `occasions` with target constraints and indices.
2. Copy all `events` rows into `occasions`.
3. Backfill missing synthetic occasions referenced by:
   - `pending_messages.eventId`
   - `sent_messages.eventId`
   - legacy synthetic ids in `sent_messages.eventType`
   - `message_feedback.eventId`
   - `activity_logs.eventId`
4. Create `message_drafts` from `pending_messages`, mapping `eventId` to `occasionId`.
5. Create or migrate `message_feedback` with `occasionId`.
6. Repoint `dispatch_attempts.messageDraftId` from `pending_messages.id` to the target `message_drafts.id` if the physical draft table is renamed.
7. Repoint `dispatch_attempts.occasionId` from `events.id` to the target `occasions.id`.
8. Recreate `sent_messages` with `occasionId` while preserving `occasionType` and `occasionLabel`.
9. Recreate `activity_logs` with `occasionId` and optional `dispatchAttemptId`.
10. Add remaining indices listed above.
11. Keep compatibility read adapters for one release if physical table names cannot all change at once.

Backfill rules:

- If a version 15 `eventId` or `occasionId` matches `events.id`, map it to the copied `occasions.id`.
- If a reference starts with `HOLIDAY_`, create an occasion with `type = HOLIDAY`.
- If a reference starts with `REVIVAL_`, create an occasion with `type = REVIVAL`.
- If a reference starts with `FOLLOWUP_` or `FOLLOW_UP_`, create an occasion with `type = FOLLOW_UP`.
- If a legacy `eventType` is a known semantic type and no id can be resolved, preserve the semantic type and leave the occasion id null only for sent-history compatibility.
- Unknown references must migrate to `UNKNOWN` semantic type and be included in a migration diagnostic count.

## Required Migration Tests

Add tests for:

- Version 13 fixture migrates through 14 and then to target.
- Version 13 fixture migrates through 14 and 15.
- Version 14 fixture with canonical events migrates to occasions.
- Version 14 fixture with holiday, revival, and follow-up synthetic ids migrates to occasions.
- Sent messages preserve `occasionType` and `occasionLabel`.
- Draft uniqueness is enforced by `(contactId, occasionId, scheduledYear)`.
- Dispatch attempts can be inserted before a send result exists. Completed for the v15 compatibility table.
- Backup export/import round trip works after migration.
