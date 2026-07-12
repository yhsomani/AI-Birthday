# Birthday Autopilot Firebase control plane

This directory is an isolated, server-only coordination foundation. It does not
store contacts or messages and it does not grant a mobile client direct
Firestore access. Android and iOS call authenticated second-generation callable
Functions; Firestore rules deny every direct client read and write.

This code has **not** been deployed. App Check enforcement, IAM, Secret Manager,
TTL, deletion, backup/PITR, quota, regional, and production-race evidence remain
cloud provisioning gates.

The public Hosting implementation lives in `hosting/` and exposes the frozen
route contract `/`, `/delete/`, `/privacy/`, `/terms/`, and `/support/`. It uses
in-memory Firebase Auth, fresh Google reauthentication, reCAPTCHA Enterprise App
Check, and a limited-use callable token to start this same deletion saga. The
site is deliberately undeployable until an out-of-repository release config
proves public developer identity, legal and Hindi review, the final HTTPS base
URL, and the separately protected disabled/lost-account admin workflow. See
`hosting/README.md` for the exact runtime-config and release-evidence contract.

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
  deletion, Auth deletion verification, and delayed tombstone removal;
- account-global contact-derived reset that first advances sender epoch and
  destructive reset generation, enters `PAUSED_REPAIR`, installs the 24-hour
  Birthday fence, strictly drains any previously issued submit permit, then
  deletes and verifies only Birthday claims/requests/aliases/guards/outcomes;
  TEST evidence and both rolling abuse budgets are intentionally preserved;
- Android sender release that freezes every mutation, strictly drains the last
  issued permit, revokes the active epoch, recursively deletes and verifies the
  complete Android coordination tree and presence marker, and preserves the
  Firebase Auth user;
- top-level active-operation fences plus 30-day, per-request, content-free
  completion receipts. An interrupted operation remains fail-closed and is
  resumed by exact-request replay or the scheduled repair worker;
- an iOS-only deletion tombstone that transactionally races first Android
  registration;
- a read-only, content-free, account-global iOS companion status. Any Android
  account state, deletion state, orphan marker, missing continuity, or unknown
  state suppresses every in-app composer action;
- strict callable request schemas. No request accepts a raw name, People ID,
  birthday, phone number, message, prompt, or approval hash;
- no application logger, request-body logger, service-account key, provider key,
  or pepper value in source control.

## Package and runtime

The package is in `functions/` and uses exact dependency versions and an npm
lockfile. `firebase.json` pins the deployed Functions runtime to Node.js 22.
Local verification accepts Node.js 22 through 24 so the repository's Node
24.18.0 toolchain can run it; the deployed runtime is still Node 22.

Firebase currently documents Node.js 22 and 20 as supported Functions runtimes,
and says the `firebase.json` runtime takes precedence over the package engine:
[Manage Functions runtimes](https://firebase.google.com/docs/functions/manage-functions#set_node.js_version).

```sh
cd backend/functions
PATH="$HOME/.nvm/versions/node/v24.18.0/bin:$PATH" npm ci
PATH="$HOME/.nvm/versions/node/v24.18.0/bin:$PATH" npm run check
PATH="$HOME/.nvm/versions/node/v24.18.0/bin:$PATH" npm run build
PATH="$HOME/.nvm/versions/node/v24.18.0/bin:$PATH" npm run test:coverage
```

The emulator requires Java 21. On this workstation, the corporate CA must also
be supplied to Node for the first official emulator download:

```sh
cd backend/functions
JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
NODE_EXTRA_CA_CERTS=/opt/homebrew/etc/openssl@3/cert.pem \
PATH="/opt/homebrew/opt/openjdk@21:$HOME/.nvm/versions/node/v24.18.0/bin:$PATH" \
npm run test:emulator
```

The emulator suite uses the `demo-birthday-autopilot` demo project, so an
accidental non-emulated call cannot mutate a live Firebase project. Firebase
recommends demo projects for this safety property and notes that emulator
transactions, indexes, and limits are not a complete production substitute:
[Connect the Firestore emulator](https://firebase.google.com/docs/emulator-suite/connect_firestore).

## Callable surface

All callable Functions require Firebase Auth and enforced App Check with token
consumption enabled. The mobile SDK must request limited-use App Check tokens.
Firebase documents that consumption is replay protection and currently a beta
Node.js capability:
[App Check for callable Functions](https://firebase.google.com/docs/app-check/cloud-functions#replay-protection).

- `registerAndroidInstallation`
- `renewSenderLease`
- `changeAccountMode`
- `claimOccurrence`
- `claimTest`
- `armAttempt`
- `getArmStatus`
- `reportTestOutcome`
- `authorizeSafeRetry`
- `beginSenderTransfer`
- `completeSenderTransfer`
- `requestAccountDeletion`
- `accountDeletionReceipt`
- `resetContactDerivedState`
- `releaseAndroidSender`
- `coordinationLifecycleStatus`
- `companionStatus`
- scheduled `sweepDeletionDrains`
- scheduled `sweepCoordinationOperations`

An exception, timeout, cancellation, or unavailable status is returned only as
`COORDINATION_UNAVAILABLE`. It is never translated into `armWritten=false` or
another no-write proof.

### Destructive reset/release wire contracts

`resetContactDerivedState` is account-global and can be called by Android or
iOS. Its exact request is:

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
iOS-only account has `androidStateExisted: false` and no epoch, generation,
Birthday fence, or drain fields. Terminal reset evidence is:

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
account is absent on success. It still uses the shared `commonOptions`, so App
Check is enforced and its limited-use token is consumed. Its exact request is:

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
   recovering, or generation-mismatched control fails claims/arms and iOS status
   closed. Disaster recovery must create a reviewed new generation; never seed an
   empty ledger as proof of no prior submission.
5. Deploy `firestore.rules` and `firestore.indexes.json`, then prove every direct
   authenticated and unauthenticated mobile path is denied in each real tier.
   Firestore transactions retry on concurrent changes and apply all writes
   atomically, but production contention and service limits must still be tested:
   [Firestore transactions](https://firebase.google.com/docs/firestore/manage-data/transactions).
6. Enable a TTL policy on `cleanupAt` for every collection group in
   `ttl-policies.json`, and verify it. Example for each listed group:

   ```sh
   gcloud firestore fields ttls update cleanupAt \
     --collection-group=occurrenceClaims --enable-ttl
   ```

   TTL is cleanup only. Firebase says deletion is not instantaneous, expired
   documents can remain visible, and TTL does not delete subcollections:
   [Firestore TTL](https://firebase.google.com/docs/firestore/ttl). Every
   authorization and rolling-budget check in this package therefore evaluates
   logical time inside the transaction.

7. Explicitly disable managed Firestore backups and PITR for this minimal ledger
   and retain evidence. This repository does not claim that console-side setting
   is configured.
8. Configure the scheduled deletion function, Auth deletion permission, the
   coordination-operation repair schedule, stuck-fence alerts,
   dead-letter/repair runbook, and external verified-admin deletion path. A
   Firestore parent delete does not remove subcollections, so the orchestrator
   uses Admin SDK recursive deletion and verifies the account root before Auth
   deletion:
   [Delete Firestore data](https://firebase.google.com/docs/firestore/manage-data/delete-data#delete_collections).
9. Register and enforce the approved production App Check providers for every
   signed Android/iOS channel, grant the service identity the Firebase App Check
   Token Verifier role, and collect real limited-use-token replay evidence. No
   debug provider may ship.
10. Add Cloud Logging exclusions/redaction tests, IAM review, quotas, budget
    alerts, abuse/rate tests, regional failure tests, contention/load tests,
    production index validation, recursive-deletion absence checks, and SLO/cost
    evidence. Do not enable request-body logging.

## Retention model

- never-Armed authorization: ten minutes; physical cleanup eligible after 24
  hours;
- NO_WRITE outcome: later of authorization expiry and 24 hours after resolution;
- rolling budget entry/document: logically ignored after 24 hours and TTL-eligible
  from its newest entry plus 24 hours;
- Birthday Armed/outcome/guards/aliases: 400 days;
- TEST Armed/outcome: 30 days;
- STANDBY installation: 90 days; REVOKED installation: 30 days;
- completed reset/release operation receipt: 30 days; an active operation fence
  has no TTL and remains fail-closed until verified completion or reviewed
  operator repair;
- deletion tombstone: held through drain, recursive absence, Auth deletion, and
  a final verification delay before removal.
- signed-out account-deletion receipt: `IN_PROGRESS` has no TTL while unresolved;
  exact `COMPLETED` becomes TTL-eligible 365 days after completion.

These values are encoded in deterministic policy functions and tested without
cloud credentials. TTL activation and retention evidence in a real project are
still mandatory.

## Dependency audit status

As of 2026-07-12, `npm audit` reports no high or critical findings and 12 moderate
findings overall; `npm audit --omit=dev` reports eight moderate runtime findings.
The runtime findings are the `uuid` bounds-check advisory in the Firebase Admin
13.x Firestore/Storage chain;
the advisory service proposes Firebase Admin 14.1.0, but Firebase Functions 7.2.5
currently declares official peer support only through Admin 13.x. The remaining
findings include an OpenTelemetry baggage-allocation advisory in Firebase CLI
development tooling. This package intentionally does
not use `--force`, `--legacy-peer-deps`, or unsupported major overrides. Production
release remains blocked until upstream publishes a compatible patched set or a
reviewed supported migration removes the findings.

Run both views during every update:

```sh
npm audit --audit-level=high
npm audit --omit=dev
```
