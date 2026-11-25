# API and backend execution map

[Knowledge home](README.md) · [Integration catalog](integrations/README.md) · [Data flows](data-flow-map.md) · [Machine graph](master-project-graph.json)

**Snapshot:** 2026-11-25 checkout `20a598e9789a19e2a35a2b693e63c098473cc68d`.

## Backend boundaries

| Surface | Runtime / entry | Internal execution | Persistent data | Caller and status |
|---|---|---|---|---|
| Flutter application | `lib/main.dart` → `AiBirthdayApp` → router/providers | Riverpod services/repositories → Drift, secure storage, platform adapters | Local SQLite tables and secure local credentials | Android app source; app build and tests observed, external service configuration not fully verified |
| Firebase Functions | Gen 2 `onCall`/`onSchedule`, region `asia-south1` | Auth/App Check → strict Zod parse → domain/service → Admin Firestore/Google Play client | Owner-scoped entitlement plus server-only account/coordination/deletion collections | Source exists; backend README states it is not deployed |
| Firebase Hosting | Static Vite-built pages and route-specific content | Browser runtime config → Firebase Auth/App Check → callable Functions for deletion only | No app database; private deletion receipt held in tab session storage while needed | Local browser preview rendered pages; missing release config disabled deletion controls |

## Callable and scheduled API inventory

All API IDs are stable nodes in [the master graph](master-project-graph.json). Callable request bodies are parsed with strict schemas in [schemas.ts](../../backend/functions/src/transport/schemas.ts); `commonOptions` enforces Firebase App Check with token consumption. Most callables also require Firebase Auth. Deletion receipt lookup instead requires signed-out App Check; transfer/deletion/reset/release paths require recent Google authentication. Scheduled functions use the configured service identity. Exact response shapes are defined by the service methods and transport codecs; this source inventory does not imply a deployed URL.

| ID / function | Method / schema | Handler → service | Data effects | Caller evidence / tests |
|---|---|---|---|---|
| API-001 `registerAndroidInstallation` | Callable / `registrationSchema` | `index.ts` → `ControlPlaneService.registerAndroidInstallation` | Account, installation and sender epoch docs | No Flutter sender client found; backend coordination and emulator suites |
| API-002 `renewSenderLease` | Callable / `leaseSchema` | → `ControlPlaneService.renewLease` | Sender lease/mode state | No Flutter caller found; coordination service tests |
| API-003 `changeAccountMode` | Callable / `accountModeSchema` | → `ControlPlaneService.changeAccountMode` | Account mode/activation state | No Flutter caller found; decisions/coordination tests |
| API-004 `claimOccurrence` | Callable / `birthdayClaimSchema` | Secret-enabled ControlPlane → `claimBirthdayOccurrence` | Opaque recipient/destination aliases, occurrence claim/guards | No Flutter caller found; coordination/race tests |
| API-005 `claimTest` | Callable / `testClaimSchema` | Secret-enabled ControlPlane → `claimTest` | Separate TEST claim/budget | No Flutter caller found; coordination/race tests |
| API-006 `armAttempt` | Callable / `armSchema` | → `ControlPlaneService.armAttempt` | Immutable bounded arm outcome/attempt fence | No Flutter caller found; decisions/coordination tests |
| API-007 `getArmStatus` | Callable / `armSchema` | → `ControlPlaneService.getArmStatus` | Read-only arm state | No Flutter caller found; coordination tests |
| API-008 `reportTestOutcome` | Callable / `testReportSchema` | → `ControlPlaneService.reportTestOutcome` | TEST result evidence | No Flutter caller found; coordination tests |
| API-009 `authorizeSafeRetry` | Callable / `retrySchema` | → `ControlPlaneService.authorizeSafeRetry` | Bounded retry authorization | No Flutter caller found; policy/coordination tests |
| API-010 `beginSenderTransfer` | Callable / `transferSchema` + recent Google auth | → `ControlPlaneService.beginTransfer` | Transfer/drain state | No Flutter caller found; coordination tests |
| API-011 `completeSenderTransfer` | Callable / `transferSchema` + recent Google auth | → `ControlPlaneService.completeTransfer` | Transfer completion/revocation | No Flutter caller found; coordination tests |
| API-012 `requestAccountDeletion` | Callable / `deletionSchema` + recent Google auth | → `ControlPlaneService.beginDeletion` → `DeletionOrchestrator` | Deletion fence, tombstone, request receipt and cleanup state | Called by public Hosting client; backend deletion/Hosting contract tests |
| API-013 `accountDeletionReceipt` | Callable / `deletionReceiptSchema`, signed-out App Check | → `ControlPlaneService.accountDeletionReceipt` | Reads private receipt projection | Called by public Hosting client; deletion receipt tests |
| API-014 `resetContactDerivedState` | Callable / `contactDerivedResetSchema` + recent Google auth | → `ControlPlaneService.requestContactDerivedReset` | Pauses sender, advances reset generation, removes Birthday-derived state | No Flutter caller found; coordination tests |
| API-015 `releaseAndroidSender` | Callable / `senderReleaseSchema` + recent Google auth | → `ControlPlaneService.requestSenderRelease` | Drains/revokes Android sender and removes coordination tree | No Flutter caller found; coordination tests |
| API-016 `coordinationLifecycleStatus` | Callable / `coordinationLifecycleStatusSchema` | → `ControlPlaneService.coordinationLifecycleStatus` | Read-only, content-free lifecycle state | No Flutter caller found; transport/coordination tests |
| API-017 `companionStatus` | Callable / `companionStatusSchema` | → `ControlPlaneService.companionStatus` | Read-only companion-eligibility projection | No app target/caller found; backend tests |
| API-018 `verifyPurchase` | Callable / `verifyPurchaseSchema` | `SubscriptionVerificationService` → Google Play API client | Owner entitlement and hashed purchase ownership | Flutter subscription client uses this path; backend and Flutter verification tests |
| API-019 `sweepDeletionDrains` | Scheduled, every minute UTC | `DeletionOrchestrator.sweep` | Resumes bounded deletion drains/cleanup | Scheduled service; emulator/worker tests, not deployed evidence |
| API-020 `sweepCoordinationOperations` | Scheduled, every minute UTC | `CoordinationOperationOrchestrator.sweep` | Recovers/cleans coordination operations | Scheduled service; emulator/coordination tests, not deployed evidence |

Source of exact exports and common security options: [functions/index.ts](../../backend/functions/src/functions/index.ts). `safeCall` preserves known `HttpsError`s and converts unexpected internal transport failure to a fixed unavailable error; request bodies are not included in that response.

## Request fields, response contracts and errors

Callable requests are strict JSON objects: unknown fields are rejected. The shared sender `binding` is `contractVersion: 1`, `ledgerGeneration` (8–64 allowed characters), a 32-character lowercase-hex `installationId`, positive safe-integer `senderEpoch` and `resetGeneration`, positive bounded `appBuildNumber` and `policyVersion`, and `distributionChannel` (`DEV`, `STAGING`, `RESTRICTED_LAB`, `PLAY`, or `DIRECT_MANAGED`). Fields noted below are additional to the named schema; see the linked [schemas.ts](../../backend/functions/src/transport/schemas.ts) for exact regexes and bounds.

| API ID | Schema and request-specific fields | Success response contract |
|---|---|---|
| API-001 | `registrationSchema`: binding subset `contractVersion`, `ledgerGeneration`, `installationId`, `appBuildNumber`, `policyVersion`, `distributionChannel` | Direct `registerAndroidInstallation` result; typed at [ControlPlaneService](../../backend/functions/src/services/controlPlane.ts); no shared response schema |
| API-002 | `leaseSchema`: full binding + `purpose: BIRTHDAY \| TEST` | Direct `renewLease` result; method-defined response |
| API-003 | `accountModeSchema`: full binding + `action`; activation branch additionally requires opaque test claim ID, SHA-256 test receipt prehash and positive readiness contract version | Direct `changeAccountMode` result; method-defined response |
| API-004 | `birthdayClaimSchema`: full binding + literal `purpose: BIRTHDAY`, UUID `claimRequestId`, 1–2 SHA-256 recipient and destination aliases | Direct `claimBirthdayOccurrence` result; method-defined response |
| API-005 | `testClaimSchema`: full binding + literal `purpose: TEST`, UUID `testRequestId`, SHA-256 test-configuration and destination prehashes | Direct `claimTest` result; method-defined response |
| API-006 | `armSchema`: full binding + purpose, opaque `claimId`, UUID `armRequestId`, attempt 1 or 2 | Direct `armAttempt` result; method-defined response |
| API-007 | `armSchema`: same request fields as API-006 | Direct `getArmStatus` result; method-defined response |
| API-008 | `testReportSchema`: full binding + literal `purpose: TEST`, opaque `testClaimId`, UUID arm request ID, enumerated test result | Direct `reportTestOutcome` result; method-defined response |
| API-009 | `retrySchema`: full binding + literal `purpose: BIRTHDAY`, opaque claim ID, UUID retry request ID, enumerated radio/service proof | Direct `authorizeSafeRetry` result; method-defined response |
| API-010 | `transferSchema`: full binding + 32-character lowercase-hex target installation ID | Direct `beginTransfer` result; method-defined response |
| API-011 | `transferSchema`: same request fields as API-010 | Direct `completeTransfer` result; method-defined response |
| API-012 | `deletionSchema`: contract version 1 + UUIDv4 request ID | `STARTED`/`REPLAYED` returns receipt ID, deletion tombstone projection and optional fence; `REFUSED` returns a typed reason. Exact union: [deletionReceipt.ts](../../backend/functions/src/domain/deletionReceipt.ts). |
| API-013 | `deletionReceiptSchema`: contract version 1 + UUIDv4 receipt ID | `IN_PROGRESS`, `COMPLETED`, or `NOT_FOUND`; completion explicitly says external copies are not deleted. Exact union: [deletionReceipt.ts](../../backend/functions/src/domain/deletionReceipt.ts). |
| API-014 | `contactDerivedResetSchema`: contract version 1 + stable UUID request ID | Direct `requestContactDerivedReset` result; method-defined response |
| API-015 | `senderReleaseSchema`: contract version 1 + stable UUID request ID, installation ID, sender epoch and reset generation | Direct `requestSenderRelease` result; method-defined response |
| API-016 | `coordinationLifecycleStatusSchema`: contract version 1 only | Direct content-free lifecycle projection; method-defined response |
| API-017 | `companionStatusSchema`: contract version 1 + ledger generation | Direct companion eligibility projection; method-defined response |
| API-018 | `verifyPurchaseSchema`: contract version 1, purchase token (1–1024 chars), product and package names (1–128 chars each), account binding (16–128 allowed characters) | `{status, productId, expiryDateMs, canUseAi, isAutoRenewing}`; status is `active`, `expired`, or `none`. See [response type and verifier](../../backend/functions/src/services/subscriptionVerification.ts). |
| API-019 | Scheduled event; no caller request body | No callable response; sweep result is emitted to server logs |
| API-020 | Scheduled event; no caller request body | No callable response; sweep result is emitted to server logs |

Most coordination success objects are returned directly from service methods and do not have a single response Zod schema. Their precise response types therefore remain method-owned rather than presented here as a fabricated common envelope. Common errors include `unauthenticated`, `failed-precondition`, `invalid-argument/INVALID_REQUEST`, and `unavailable/COORDINATION_UNAVAILABLE`; operation-specific `HttpsError`s propagate. Purchase verification also has explicit package/product/account-mismatch and Google Play unavailability failures. Exact guards and error strings are in [functions/index.ts](../../backend/functions/src/functions/index.ts) and [subscriptionVerification.ts](../../backend/functions/src/services/subscriptionVerification.ts).

## Client caller matrix

| Client / operation | Exact caller | Does not call |
|---|---|---|
| Flutter Play purchase verification | `SubscriptionService` builds the configured `verifyPurchase` endpoint and sends Auth/App Check headers | No `registerAndroidInstallation`, sender lease, claim, arm, retry or transfer flow was found in the app source |
| Flutter cloud backup | Firebase Auth and Firestore SDK from `CloudSyncService` | Does not invoke the sender coordination APIs |
| Flutter AI/manual delivery | Gemini provider, Android Nano bridge and external composer/share launch | Does not call a server message-generation endpoint or send WhatsApp through the coordination backend |
| Hosting account deletion | `httpsCallable` for deletion start and receipt lookup in `backend/hosting/src/app.ts` | No public API request is sent when runtime config is absent; the local preview controls were disabled |

## Authorization and data safety boundaries

- Every callable is configured with App Check enforcement and token consumption; schema parsing is strict and rejects unknown fields.
- Most operations bind to an authenticated Firebase UID. Recent-Google-auth checks gate destructive account/sender operations.
- `verifyPurchase` requires Auth and App Check, checks package/product and optional account binding, hashes purchase tokens before ownership records, and stores the entitlement under the account.
- The deletion receipt endpoint deliberately requires a signed-out App Check caller and a private receipt identifier.
- Firestore client rules allow owner-only backup/entitlement reads and restrict server-only coordination collections. The source rules are not proof that a deployed Firebase project uses them.
- Public release configuration, production identity, legal approvals and support workflow are external gates. Do not send live requests while documenting.

## Backend data path references

| Data family | Path helper / source | Owner/reader |
|---|---|---|
| Account control state | `accountPaths(...).account` → `accounts/{uid}` | Server-only Functions |
| Android installation | `accounts/{uid}/installations/{installationId}` | Server-only Functions |
| Birthday/test claims and fences | `occurrenceClaims`, `testClaims`, `occurrenceKeys`, `destinationGuards`, `claimRequests`, `armOutcomes`, `armBudgets` | Server-only Functions |
| Deletion/operation records | `deletionTombstones`, `coordinationPresence`, `coordinationOperationFences`, `coordinationOperationReceipts`, `deletionReceipts` | Server-only Functions; public API exposes constrained projections |
| Purchase ownership | `subscriptionPurchaseOwnership/{purchaseTokenHash}` | Server-only purchase verifier |
| User entitlement | `users/{uid}/entitlement/status` | Server write; owner read under Firestore rules |

References: [paths.ts](../../backend/functions/src/persistence/paths.ts), [Firestore rules](../../backend/firestore.rules), [subscription verifier](../../backend/functions/src/services/subscriptionVerification.ts).
