# Birthday Autopilot Firebase control plane

**Scope.** This is a package README for `backend/` (the Firebase control plane
in `functions/`, rules, indexes, TTL policy list, and the Hosting package in
`hosting/`). It says what this package is, how to run, test, build, and deploy
it, and which package-specific gates apply. It is maintained independently of,
and defers to, the canonical documents at the repository root:
[PRODUCT.md](../PRODUCT.md), [ARCHITECTURE.md](../ARCHITECTURE.md) (API surface
§8, data §7, deployment §11), [SECURITY.md](../SECURITY.md),
[IMPLEMENTATION.md](../IMPLEMENTATION.md), [OPERATIONS.md](../OPERATIONS.md)
(deployment §7, retention §10.5), and [PROJECT_MINDMAP.md](../PROJECT_MINDMAP.md).

This directory is a server-side coordination foundation plus the opt-in cloud
backup/entitlement rules. The coordination ledger never stores contact names,
phone numbers, or message text. Firestore rules deny every direct client access
to the coordination ledger and unknown collections; the only direct client
access is owner-scoped: read/write of the validated
`users/{uid}/backup/current` pointer, immutable `users/{uid}/generations`
manifests and their validated `people`/`birthdays`/`drafts`/`reminderSettings`
rows, and read/write of the same validated legacy collections. Unknown fields,
oversized values, and deeper nested paths are denied. `users/{uid}/entitlement`
remains read-only. Owner writes are also denied while the account is
fenced for deletion, that is, when `coordinationPresence/{uid}` says `DELETING`
or `deletionTombstones/{uid}` exists. Callable Functions are second generation;
the Android app calls only `verifyPurchase` today. There is no `ios/`
application in this repository, so the iOS-facing callables below
(`companionStatus`, iOS-only deletion paths) have no caller here. See
[ARCHITECTURE.md §8.7](../ARCHITECTURE.md).

Deployment is not evidenced in this repository. App Check enforcement, IAM,
Secret Manager, TTL, deletion, backup/PITR, quota, regional, and
production-race evidence remain cloud provisioning gates
([OPERATIONS.md §7.6](../OPERATIONS.md)).

The public Hosting implementation lives in `hosting/` and exposes the route
contract `/`, `/delete/`, `/privacy/`, `/terms/`, and `/support/`. It uses
in-memory Firebase Auth, fresh Google reauthentication, reCAPTCHA Enterprise App
Check, and a limited-use callable token to start the deletion saga. It is
deliberately undeployable until an out-of-repository release config supplies the
required identity, approvals, and HTTPS base URL. See `hosting/README.md` and
[OPERATIONS.md §7.3](../OPERATIONS.md).

## What is implemented

- one active Android installation, monotonically increasing sender epochs, and
  `TEST_ONLY`, `PAUSED_REPAIR`, `AUTOMATION_ACTIVE`, `TRANSFER_PENDING`, and
  `DELETING` modes;
- fixed-length recipient and destination prehash inputs that are immediately
  transformed into UID-, purpose-, version-, and pepper-separated HMAC aliases;
- current/previous pepper alias checks for a bounded rotation window;
- content-free random claim/Arm request UUIDs remain stable across pepper
  rotation; pepper changes only pseudonymous recipient, destination, and TEST
  material aliases;
- independent Birthday occurrence and destination guards;
- a separate TEST namespace with no contact source, birthday, Birthday claim,
  or Birthday destination guard;
- immutable exact Arm outcomes, ten-minute authorization, one-minute submit
  deadlines, five-minute post-deadline spacing, and logical expiry independent
  of delayed TTL deletion;
- rolling server-time caps of 20 distinct Birthday occurrences and three TEST
  claims per UID per 24 hours;
- one allowlisted, separately numbered retry authorization after all SMS parts
  report either radio-off or no-service; attempt three is impossible;
- two-transaction sender transfer with a strict `now > drainUntil` completion
  rule;
- deletion tombstones, no-new-child `DELETING` fencing, drain, recursive UID
  deletion (including `users/{uid}` and `subscriptionPurchaseOwnership` documents
  owned by the UID), Auth deletion verification, and delayed tombstone removal;
- account-global contact-derived reset that first advances sender epoch and
  destructive reset generation, enters `PAUSED_REPAIR`, installs the 24-hour
  Birthday fence, strictly drains any previously issued submit permit, then
  deletes and verifies only Birthday claims/requests/aliases/guards/outcomes;
  TEST evidence and both rolling abuse budgets are intentionally preserved;
- Android sender release that freezes every mutation, strictly drains the last
  issued permit, revokes the active epoch, recursively deletes and verifies the
  complete Android coordination tree and presence marker, and preserves the
  Firebase Auth user and `users/{uid}` cloud backup/entitlement tree. Full account
  deletion uses a separate purge path that removes both trees. Release remains
  unreachable today because no client calls `releaseAndroidSender`; see
  [OPERATIONS.md §10.4](../OPERATIONS.md);
- top-level active-operation fences plus 30-day, per-request, content-free
  completion receipts. An interrupted operation remains fail-closed and is
  resumed by exact-request replay or the scheduled repair worker;
- an iOS-only deletion tombstone that transactionally races first Android
  registration (server capability; no iOS client exists in this repository);
- a read-only, content-free, account-global companion status (server
  capability; no iOS client exists in this repository). Any Android
  account state, deletion state, orphan marker, missing continuity, or unknown
  state suppresses every in-app composer action;
- strict callable request schemas. No request accepts a raw name, People ID,
  birthday, phone number, message, prompt, or approval hash;
- no request-body logging, service-account key, provider key, or pepper value in
  source control. Application logs contain only the scheduled sweeps'
  `logger.info` summaries (`deletion_sweep_completed`,
  `coordination_operation_sweep_completed`) and a generic callable failure event
  with a sanitized error category; expected callable errors are not logged.

## Package and runtime

The package is in `functions/` and uses caret-ranged dependencies
(`firebase-admin ^14.5.0`, `firebase-functions ^7.4.0`, `zod ^4.6.7`) pinned by an
npm lockfile. `firebase.json` pins the deployed Functions runtime to Node.js 22;
`package.json` `engines` accepts Node.js `>=22 <25` for local verification. CI
runs the Functions job on Node 22 with Java 21 for the emulator
([OPERATIONS.md §6.4](../OPERATIONS.md)). Stack versions:
[ARCHITECTURE.md §2.3](../ARCHITECTURE.md).

Firebase currently documents Node.js 22 and 20 as supported Functions runtimes,
and says the `firebase.json` runtime takes precedence over the package engine:
[Manage Functions runtimes](https://firebase.google.com/docs/functions/manage-functions#set_node.js_version).

```sh
cd backend/functions
npm ci
npm run check          # typecheck + lint + unit tests
npm run build
npm run test:coverage
npm run audit:ci       # npm audit --audit-level=high
```

`npm test` runs the unit and static-contract suites; `npm run test:emulator`
runs the rules and control-plane emulator suites. Test counts are intentionally
not duplicated here because they change as focused coverage is added. The
emulator requires Java 21 (any JDK 21 on `PATH`, or
`JAVA_HOME`) and downloads the Firestore emulator on first use. On a network
with TLS interception, point Node at your organisation's CA bundle, for example
`NODE_EXTRA_CA_CERTS=/path/to/ca-bundle.pem` (example only; the path is
machine-specific):

```sh
cd backend/functions
npm run test:emulator
```

The emulator suite uses the `demo-birthday-autopilot` demo project, so an
accidental non-emulated call cannot mutate a live Firebase project. Firebase
recommends demo projects for this safety property and notes that emulator
transactions, indexes, and limits are not a complete production substitute:
[Connect the Firestore emulator](https://firebase.google.com/docs/emulator-suite/connect_firestore).
Test commands overall: [OPERATIONS.md §4.2](../OPERATIONS.md).

## Callable surface

`functions/src/functions/index.ts` exports 20 functions in `asia-south1`: 18
callables and 2 scheduled sweeps. Every callable enforces App Check with token
consumption (the mobile/web SDK must request limited-use tokens). All callables
except `accountDeletionReceipt` require Firebase Auth; destructive and transfer
callables additionally require recent Google reauthentication.
`accountDeletionReceipt` is the exception: it requires a signed-out caller with
App Check. The authoritative export inventory, per-callable auth level, request
schemas, error tokens, and caller matrix are in
[ARCHITECTURE.md §8.1–§8.7](../ARCHITECTURE.md); the purchase contract is §8.6.
Firebase documents App Check consumption as replay protection and currently a
beta Node.js capability:
[App Check for callable Functions](https://firebase.google.com/docs/app-check/cloud-functions#replay-protection).

Callables: `registerAndroidInstallation`, `renewSenderLease`,
`changeAccountMode`, `claimOccurrence`, `claimTest`, `armAttempt`, `getArmStatus`,
`reportTestOutcome`, `authorizeSafeRetry`, `beginSenderTransfer`,
`completeSenderTransfer`, `requestAccountDeletion`, `accountDeletionReceipt`,
`resetContactDerivedState`, `releaseAndroidSender`,
`coordinationLifecycleStatus`, `companionStatus`, `verifyPurchase`. Scheduled:
`sweepDeletionDrains`, `sweepCoordinationOperations`.
Each sweep caps its query at 20 due records and processes up to four distinct
accounts concurrently. Query-based cleanup is capped at five 400-document pages
per invocation and resumes through the repair worker; recursive tree deletion
remains Admin SDK-managed. Production queue-depth, contention, and timeout testing
is still required before deployment.

`verifyPurchase` refuses with `ACCOUNT_DELETION_IN_PROGRESS` while the account is
fenced for deletion, tolerates a Play account-binding mismatch only when the
purchase-token hash is already owned by the same UID, and writes its ownership
claim and entitlement in one fenced transaction. Account deletion purges
`subscriptionPurchaseOwnership` documents by `uid`. Details:
[ARCHITECTURE.md §8.6](../ARCHITECTURE.md).

An exception, timeout, cancellation, or unavailable status is returned only as
`COORDINATION_UNAVAILABLE`. It is never translated into `armWritten=false` or
another no-write proof.

### Destructive reset/release wire contracts

These coordination contracts are documented here only; they have no client in
this repository and ARCHITECTURE.md §8.4 treats them as not frozen public
contracts. `resetContactDerivedState` and `releaseAndroidSender` are classified
A+R in [ARCHITECTURE.md §8.2](../ARCHITECTURE.md).

`resetContactDerivedState` is account-global and is designed to be callable by
an Android or an iOS client. Its exact request is:

```json
{
  "contractVersion": 1,
  "requestId": "lowercase-random-uuid"
}
```

`releaseAndroidSender` is bound to the exact active Android installation and
generation. Its exact request is:

```json
{
  "contractVersion": 1,
  "requestId": "lowercase-random-uuid",
  "installationId": "32-lowercase-hex-characters",
  "senderEpoch": 4,
  "resetGeneration": 3
}
```

Both callables additionally require a Firebase ID token whose `auth_time` is no
more than five minutes old, whose current `sign_in_provider` is exactly
`google.com`, and whose Firebase identities claim contains exactly one linked
Google identity. Reauthenticate the already-bound Google account through the
official native Firebase SDK before calling. Provider identifiers and
credentials never enter the callable body.

A reset may return either:

```json
{
  "kind": "IN_PROGRESS",
  "operation": "CONTACT_DERIVED_RESET",
  "stage": "RESET_DRAINING",
  "androidStateExisted": true,
  "senderEpochAfter": 5,
  "resetGenerationAfter": 4,
  "birthdayAutomationNotBeforeMs": 1800086400000,
  "drainUntilMs": 1800000060000
}
```

or `stage: "RESET_PURGING"` with the same fields except `drainUntilMs`; an
account with no Android state (the iOS-only case) has
`androidStateExisted: false` and no epoch, generation, Birthday fence, or drain
fields. Terminal reset evidence is:

```json
{
  "kind": "COMPLETED",
  "operation": "CONTACT_DERIVED_RESET",
  "androidStateExisted": true,
  "senderEpochAfter": 5,
  "resetGenerationAfter": 4,
  "birthdayAutomationNotBeforeMs": 1800086400000,
  "contactDerivedStateErased": true,
  "firebaseAuthPreserved": true,
  "completedAtMs": 1800000060001
}
```

`contactDerivedStateErased` means only the Birthday/contact-derived remote
collections were verified absent. It does not mean sender, installation, TEST,
budget, Firebase Auth, or external SMS state was erased. The surviving Android
sender is `PAUSED_REPAIR` on the returned generations and must pass a new TEST
before automation can activate.

Sender release returns:

```json
{
  "kind": "IN_PROGRESS",
  "operation": "SENDER_RELEASE",
  "stage": "RELEASE_DRAINING",
  "androidStateExisted": true,
  "senderEpochAfter": 5,
  "resetGenerationAfter": 3,
  "drainUntilMs": 1800000060000
}
```

or `stage: "RELEASE_PURGING"` with `drainUntilMs` omitted. Its terminal evidence
is:

```json
{
  "kind": "COMPLETED",
  "operation": "SENDER_RELEASE",
  "androidStateExisted": true,
  "senderEpochAfter": 5,
  "resetGenerationAfter": 3,
  "androidSenderStateErased": true,
  "firebaseAuthPreserved": true,
  "completedAtMs": 1800000060001
}
```

Either callable can return:

```json
{ "kind": "REFUSED", "reason": "DELETION_SUPPRESSED" }
```

where `reason` is exactly one of `DELETION_SUPPRESSED`,
`COORDINATION_OPERATION_IN_PROGRESS`, `REQUEST_MISMATCH`, `RESET_SUPPRESSED`,
`CONTINUITY_UNAVAILABLE`, or `GENERATION_EXHAUSTED`.

The client must durably create one random request UUID and retain the exact
request until `COMPLETED`. `IN_PROGRESS`, timeout, cancellation, `unavailable`,
or process death is never completion. Replay the same callable with the exact
same body; do not mint a new request or change a release binding. The active
top-level fence blocks registration, lease, mode, claim, Arm/status sealing,
report, retry, transfer, companion composition, and account deletion races. The
scheduled worker resumes interrupted drain/purge work. Once completion commits,
the exact receipt replays for 30 days; the same UUID with changed release fields
returns `REQUEST_MISMATCH`, and a different request while another is active
returns `COORDINATION_OPERATION_IN_PROGRESS`.

### Lost-journal lifecycle reconciliation

`coordinationLifecycleStatus` is a read-only, authenticated, consumed-App-Check
callable for recovery when the device can no longer read its local destructive
operation UUID. It does not require recent reauthentication or a request UUID,
does not advance an operation, and accepts only:

```json
{ "contractVersion": 1 }
```

While reset or release is active it returns the same operation/stage/generation
and optional drain fields as the `IN_PROGRESS` response, but with
`kind: "OPERATION_IN_PROGRESS"` and `serverNowMs`. During account deletion it
returns:

```json
{
  "kind": "ACCOUNT_DELETION_IN_PROGRESS",
  "serverNowMs": 1800000000000,
  "stage": "DRAINING",
  "drainUntilMs": 1800000060000
}
```

When Android state exists it returns:

```json
{
  "kind": "ANDROID_STATE",
  "serverNowMs": 1800000060001,
  "mode": "PAUSED_REPAIR",
  "activeInstallationId": "32-lowercase-hex-characters",
  "senderEpoch": 5,
  "resetGeneration": 4,
  "ownerLeaseUntilMs": 1800000000000,
  "latestIssuedSubmitNotAfterMs": 1800000060000,
  "birthdayAutomationNotBeforeMs": 1800086400000,
  "latestCompletion": {
    "kind": "COMPLETED",
    "operation": "CONTACT_DERIVED_RESET",
    "androidStateExisted": true,
    "senderEpochAfter": 5,
    "resetGenerationAfter": 4,
    "birthdayAutomationNotBeforeMs": 1800086400000,
    "contactDerivedStateErased": true,
    "firebaseAuthPreserved": true,
    "completedAtMs": 1800000060001
  }
}
```

`TRANSFER_PENDING` additionally requires and returns
`transferTargetInstallationId` and `transferDrainUntilMs`. A non-transfer mode
plus the current active installation/epoch is the authoritative evidence that no
transfer remains pending. After sender release, the status is
`NO_ANDROID_STATE` and carries the latest release completion when still within
its 30-day retention. With neither Android state nor a completion it returns
`{"kind":"NO_ANDROID_STATE","serverNowMs":...}`. Any orphaned presence,
generation mismatch, malformed ledger, inconsistent transfer, or corrupt latest
receipt returns `SAFETY_STATUS_UNAVAILABLE`; it is never terminal proof.

The status never exposes the request UUID, request hash, Google subject, email,
token, contact, phone, or message. The per-account latest receipt is overwritten
by the next completed reset/release, TTL-eligible after 30 days, and deleted with
all exact-request receipts before account deletion advances to Auth deletion.
Once Firebase Auth deletion succeeds, the deleted user can no longer call this
authenticated status; the Auth lifecycle and deletion receipt own that terminal
case.

### Signed-out account-deletion receipt

`requestAccountDeletion.requestId` is also the private bearer receipt. It is a
canonical lowercase random UUIDv4 and must remain in the native protected
lifecycle journal or the hosted page's visible copy control and tab-scoped
`sessionStorage` recovery journal. The hosted journal contains only that
unlinkable UUID, never a URL, local-storage value, cookie, account identity,
analytics event, or log, and clears only after exact `COMPLETED` is displayed or
an explicit Clear action. The server never persists the raw UUID: the deletion
tombstone and the external receipt document use only the domain-separated key
`SHA-256("birthday-deletion-receipt-v1\0" + requestId)`. The receipt document
contains no Firebase UID, email, provider subject, account key, token, request
UUID, contact, phone, or message.

The accepted `requestAccountDeletion` response is the dedicated minimal
projection below. `kind` is exactly `STARTED` or `REPLAYED`, `receiptId` exactly
echoes the request only in the TLS response, `cleanupAtMs` is the only optional
tombstone field, and `fence` is `null` for an iOS-only account:

```json
{
  "kind": "STARTED",
  "receiptId": "abcdef01-abcd-4def-8abc-abcdef001101",
  "tombstone": {
    "schemaVersion": 1,
    "requestKey": "ef411e7ba0493e8868d02f0218e7389c3af82964afb548092587297f4338b84f",
    "stage": "DRAINING",
    "drainUntilMs": 1800000060000,
    "createdAtMs": 1800000000000,
    "updatedAtMs": 1800000000000
  },
  "fence": {
    "mode": "DELETING",
    "senderEpoch": 4,
    "resetGeneration": 3,
    "deletionDrainUntilMs": 1800000060000
  }
}
```

No account identity, active installation ID, `nextSweepAtMs`,
`sweepAttemptCount`, or other internal scheduler field crosses this boundary.
The only refusal shapes are exactly
`{"kind":"REFUSED","reason":"COORDINATION_OPERATION_IN_PROGRESS"}` and
`{"kind":"REFUSED","reason":"REQUEST_MISMATCH"}`. Clients require exact
outer/nested keys, UUID equality, a locally derived domain-hash match, ordered
safe-integer timestamps, positive generations, and equal tombstone/fence drains;
kind-only evidence is never acceptance.

The deletion-fence transaction atomically creates an unlinkable `IN_PROGRESS`
receipt. An exact same-UUID replay resumes it; a different UUID while deletion
is active returns `REQUEST_MISMATCH` and never receives the original bearer.
After the recursive account tree and coordination presence are absent and the
Admin SDK has deleted and verified absence of Firebase Auth, the final
transaction deletes the tombstone and writes `COMPLETED`. A completed receipt
has a `cleanupAt` TTL exactly 365 days after completion; an unresolved
`IN_PROGRESS` receipt has no TTL.

`accountDeletionReceipt` intentionally requires no Firebase Auth, because the
account is absent on success; a signed-in caller is rejected with
`failed-precondition: SIGNED_OUT_REQUIRED`. It still uses the shared
`commonOptions`, so App Check is enforced and its limited-use token is consumed.
Its exact request is:

```json
{
  "contractVersion": 1,
  "receiptId": "the-original-canonical-lowercase-requestAccountDeletion-uuidv4"
}
```

Its exact response is one of:

```json
{ "kind": "NOT_FOUND" }
```

```json
{
  "kind": "IN_PROGRESS",
  "requestedAtMs": 1800000000000,
  "updatedAtMs": 1800000060000
}
```

```json
{
  "kind": "COMPLETED",
  "requestedAtMs": 1800000000000,
  "completedAtMs": 1800086400001,
  "appAccountDeleted": true,
  "serverDataDeleted": true,
  "externalCopiesNotDeleted": true
}
```

`NOT_FOUND`, timeout, App Check rejection, cancellation, malformed content, or
any extra/missing response field is nonterminal and never proves completion,
failure, or account absence. No response returns a UID, email, Google subject,
raw receipt, provider credential, or internal deletion stage. Receipt timestamps
must also be ordered: `requestedAtMs <= updatedAtMs` for `IN_PROGRESS` and
`requestedAtMs <= completedAtMs` for `COMPLETED`.

Repair scans are ordered by `nextSweepAtMs`. Every pending or failed item is
moved forward with a one-minute exponential backoff capped at one hour and a
counter capped at 30, so a page of long drains, provider failures, or corrupt
fail-closed records cannot starve later ready operations. The same rotation now
covers account-deletion drain, purge recovery, Auth deletion, and final
verification; a crash after a deletion enters `PURGING` resumes recursive
deletion instead of leaving a permanent tombstone.

## Required cloud configuration before any deployment

Deploy commands, parameters, and the TTL group list are summarised in
[OPERATIONS.md §7.2](../OPERATIONS.md); the checklist below adds the
provisioning steps that document does not carry.

1. Create distinct Firebase/Google Cloud projects for every used tier and select
   the final Firestore/Functions region through change control. The source
   currently fixes `asia-south1`; this is not evidence that production residency,
   latency, availability, or disaster requirements have been approved.
2. Create a dedicated least-privilege service identity and provide its email as
   the `CONTROL_PLANE_SERVICE_ACCOUNT` Functions parameter. Grant only the exact
   Firestore, Auth-deletion, Secret Accessor, and App Check token-verifier
   permissions needed by these functions. Server SDKs bypass Firestore rules, so
   deny-all rules do not replace IAM:
   [Firestore Security Rules](https://firebase.google.com/docs/firestore/security/get-started#insecure_rules).
3. Create the Secret Manager JSON secret `COORDINATION_HMAC_KEYRING` with 32–64
   random bytes per key, base64 encoded. Example shape only:

   ```json
   {
     "current": { "version": "v1", "keyBase64": "<secret>" },
     "previous": { "version": "v0", "keyBase64": "<secret>" }
   }
   ```

   Never place a value in `.env`, `.secret.local`, CI output, or this repository.
   The previous version must remain bound for the full longest live alias
   retention (400 days after its last write). Do not perform another rotation
   that would discard it sooner.
   Firebase recommends `defineSecret`/`defineJsonSecret` for Secret Manager-bound
   configuration:
   [Functions secrets](https://firebase.google.com/docs/functions/config-env#secret_parameters).

4. Provision `globalControl/current` through reviewed admin/IaC with schema 1, a
   reviewed ledger generation, `HEALTHY` continuity, minimum build/policy,
   signed-channel allowlist, and kill-switch state. Missing, malformed, frozen,
   recovering, or generation-mismatched control fails claims/arms and companion
   status closed. Disaster recovery must create a reviewed new generation; never seed an
   empty ledger as proof of no prior submission.
5. Deploy `firestore.rules` and `firestore.indexes.json`, then prove in each
   real tier that every unauthenticated path and every authenticated path other
   than the owner-scoped access listed in the introduction is denied, malformed
   and oversized owner backup rows and deeper nested paths are rejected, and
   owner writes are refused once `deletionTombstones/{uid}` or a `DELETING`
   presence exists.
   Firestore transactions retry on concurrent changes and apply all writes
   atomically, but production contention and service limits must still be tested:
   [Firestore transactions](https://firebase.google.com/docs/firestore/manage-data/transactions).
6. Deploy `firestore.indexes.json`; each cleanup field override declares
   `ttl: true` for the collection groups listed in `ttl-policies.json`. Verify
   the policy in each project. A manual equivalent for a group is:

   ```sh
   gcloud firestore fields ttls update cleanupAt \
     --collection-group=occurrenceClaims --enable-ttl
   ```

   TTL is cleanup only. Firebase says deletion is not instantaneous, expired
   documents can remain visible, and TTL does not delete subcollections:
   [Firestore TTL](https://firebase.google.com/docs/firestore/ttl). Every
   authorization and rolling-budget check in this package therefore evaluates
   logical time inside the transaction.

7. Decide the managed Firestore backup/PITR policy explicitly and retain
   evidence. Backup/PITR is a per-database setting and the same database holds
   user cloud backups, entitlements, and the purchase-ownership ledger, so
   disabling it for a "minimal ledger" is ambiguous; see
   [OPERATIONS.md §10.3](../OPERATIONS.md) (OPS-OPEN-05). This repository does
   not claim any console-side setting is configured.
8. Configure the scheduled deletion function, Auth deletion permission, the
   coordination-operation repair schedule, stuck-fence alerts,
   dead-letter/repair runbook, and external verified-admin deletion path. A
   Firestore parent delete does not remove subcollections, so the orchestrator
   uses Admin SDK recursive deletion and verifies the account root before Auth
   deletion:
   [Delete Firestore data](https://firebase.google.com/docs/firestore/manage-data/delete-data#delete_collections).
9. Register and enforce the approved production App Check providers for every
   signed distribution channel (this repository ships an Android app only), grant the service identity the Firebase App Check
   Token Verifier role, and collect real limited-use-token replay evidence. No
   debug provider may ship.
10. Add Cloud Logging exclusions/redaction tests, IAM review, quotas, budget
    alerts, abuse/rate tests, regional failure tests, contention/load tests,
    production index validation, recursive-deletion absence checks, and SLO/cost
    evidence. Do not enable request-body logging.

## Retention model

Server-data retention windows (400 days for Birthday guards/aliases, 30 days for
TEST records and operation receipts, 90/30 days for STANDBY/REVOKED
installations, 365 days for a `COMPLETED` deletion receipt, never-armed
authorization) are tabulated in [OPERATIONS.md §10.5](../OPERATIONS.md). Not
listed there, so kept here:

- NO_WRITE outcome: later of authorization expiry and 24 hours after resolution;
- rolling budget entry/document: logically ignored after 24 hours and TTL-eligible
  from its newest entry plus 24 hours;
- an active reset/release operation fence has no TTL and remains fail-closed
  until verified completion or reviewed operator repair;
- deletion tombstone: held through drain, recursive absence, Auth deletion, and
  a final verification delay before removal;
- `IN_PROGRESS` signed-out deletion receipt: no TTL while unresolved.

These values are encoded in deterministic policy functions and tested without
cloud credentials. TTL activation and retention evidence in a real project are
still mandatory ([OPERATIONS.md §7.2](../OPERATIONS.md)).

## Dependency audit status

Dated historical snapshot (2026-07-12, now superseded): `npm audit` reported no
high or critical findings and eight moderate runtime findings (a `uuid`
bounds-check advisory in the Firebase Admin 13.x Firestore/Storage chain), plus
an OpenTelemetry advisory in Firebase CLI development tooling. The manifest now
declares `firebase-admin ^14.5.0` and `firebase-functions ^7.4.0`; the lockfile
resolves Admin SDK 14.5.0, not 13.x. **No audit result for the current tree is
recorded in this repository** (see
[OPERATIONS.md §6.6 and Appendix B OPS-OPEN-13](../OPERATIONS.md),
[IMPLEMENTATION.md §8](../IMPLEMENTATION.md)). This package intentionally does not
use `--force`, `--legacy-peer-deps`, or unsupported major overrides. Production
release remains blocked until an audit of the current tree is reviewed.

Run both views during every update (CI runs `npm audit --omit=dev
--audit-level=high` for Functions only):

```sh
npm audit --audit-level=high
npm audit --omit=dev
```
