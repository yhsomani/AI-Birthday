# Birthday Autopilot operations runbook

Status: required release procedure subordinate to `PROJECT_ABOUT.md`. This file
does not authorize a release or replace signed policy, device, carrier, privacy,
legal, or store evidence.

## Operating rules

- Use the exact tier and immutable source/artifact evidence from the approved
  release record. Never infer a project, package, signing identity, region, or
  operator account from a local default.
- Never paste callable bodies, Firebase UIDs, installation IDs, request UUIDs,
  opaque claim/guard values, contact data, phone numbers, birthdays, message
  text, prompts, tokens, keys, or raw exception objects into an incident system.
- A control-plane outage, uncertain ledger, signing incident, policy suspension,
  or unexplained duplicate is a stop-new-arms event. Availability never outranks
  at-most-one submission safety.
- Do not delete or rewrite an Armed claim, destination guard, Arm outcome,
  deletion tombstone, or unresolved local barrier to make recovery appear clean.
- Two authorized people review every production containment or recovery change.
  Retain the content-free change request, exact before/after configuration bytes,
  source revision, UTC times, operator identities, and approval reference.

## Common incident procedure

1. Open a content-free incident record and assign severity, incident commander,
   privacy/security lead, communications owner, and release owner.
2. Identify the exact tier from signed release evidence. Confirm it again before
   every console, CLI, IAM, Secret Manager, Firebase, Play, or App Store action.
3. Stop new Android arms by setting that tier's `GlobalControl.armingEnabled` to
   `false` through the reviewed privileged operator path. Preserve
   `ledgerGeneration`, sender epochs, claims, guards, and existing outcomes.
4. If ledger integrity is uncertain, also set `continuityState` to `FROZEN`.
   Never create a new healthy generation merely because records are missing.
5. Verify with an authenticated/App-Check production probe that new claims/arms
   fail closed. A previously issued permit may still cross before its recorded
   deadline; disclose that bounded possibility.
6. Preserve immutable, content-free evidence. Do not collect user screenshots or
   database exports containing private birthday/message data.
7. Apply the scenario procedure below. Restore service only after its exit
   criteria and the general recovery checklist pass.

## Release rollback or unsafe build

Containment:

- Disable new arms in `GlobalControl`; raise the minimum supported build/policy
  only through an approved, backward-compatible control change.
- Stop the affected channel rollout. Do not revoke an epoch or delete claims to
  simulate a rollback.
- Disable Gemini separately when its behavior is implicated; built-in templates
  remain available.

Recovery:

- Reproduce from the retained source, locks, SBOM, toolchain, and artifact
  evidence. Fix forward when a Room/iOS protected-store or server schema has
  already reached users; destructive downgrade is forbidden.
- Run the complete current cross-platform, backend, emulator, migration,
  accessibility, performance, and physical release matrices for the replacement.
- Re-enable arms only for explicitly allowed fixed builds after a staged probe.

## Android signing-key incident

First classify the key: Google Play upload key, Google Play app-signing key,
controlled-direct/managed installed-app key, or independent evidence-authority
key. These identities are not interchangeable.

- Upload-key compromise: stop uploads, request the documented Play upload-key
  reset, rotate CI credentials, and prove the Play app-signing certificate did
  not change. Existing installed-app certificate evidence remains separate.
- Play app-signing-key compromise/change: stop rollout and new arms. Update OAuth,
  Firebase App Check, API-key restrictions, runtime installed-certificate gate,
  and signed channel evidence only after Play's approved rotation procedure and a
  Play-delivered APK/device proof pass.
- Direct/managed installed-key compromise: revoke the channel, stop new arms,
  remove signer allowlisting, rotate signing material in a separately controlled
  system, re-register OAuth/App Check restrictions, and require new signed
  distribution plus physical-device evidence. Never ship the private key in CI
  artifacts or this repository.
- Evidence-authority-key compromise: leave restricted packaging disabled,
  replace the tracked public-key digest through independently reviewed change
  control, and reissue every still-needed approval. Do not accept a new key from
  the build operator alone.

## HMAC pepper rotation

1. Freeze new arms if the old pepper may be disclosed. Otherwise use a controlled
   maintenance window.
2. Create a new Secret Manager version. Never expose pepper bytes to mobile,
   logs, environment dumps, test fixtures, or source control.
3. Configure the new version as current and the immediately prior approved
   version as previous. Keep purpose/version separation and dual-alias lookup.
4. Prove that existing recipient/destination/Test aliases cannot create a second
   guard and that old request UUIDs replay idempotently.
5. Retain the previous alias capability for the longest live 400-day Birthday
   guard window. Removing it early requires a frozen continuity generation and
   reset/reapproval; an empty lookup is never proof of no prior send.

## Functions, Firestore, or regional outage

- Keep all clients fail-closed. Do not add a local-only SMS bypass or widen an
  approved birthday window.
- Confirm `GlobalControl` is readable and healthy before recovery. If its
  continuity cannot be proven, freeze it.
- Reconcile exact request IDs and immutable Arm outcomes. Absence before claim
  expiry and every timeout/transport failure remain Unknown.
- When the approved window closes, record Missed. Do not catch up automatically.
- Restore traffic gradually and monitor content-free latency, contention, auth,
  App Check, quota, old-epoch, and no-write/unknown classes.

## Ledger corruption, disaster recovery, or duplicate report

- Disable new arms and set continuity to `FROZEN` immediately.
- Preserve local and server safety records; do not ask a user to clear data,
  reinstall, retry, or resend while the occurrence is uncertain.
- Establish whether more than one SmsManager API boundary was crossed using
  signed artifact identity, server Arm outcomes, local barriers, and callback
  evidence. Carrier duplication and manual messages must remain separately
  classified.
- Notify privacy/legal/support owners using content-free affected-operation
  counts. Never put recipient/message detail into the incident record.
- A new ledger generation requires reviewed disaster recovery, re-registration,
  reapproval, a new mandatory Test, and same-date reset safety. It never imports
  missing history as safe.

## Account-deletion failure

- Keep the AccountFence or isolated deletion tombstone in `DELETING`; do not
  bypass its permit drain or no-new-child-write invariant.
- Run only the idempotent deletion repair worker/orchestrator for the original
  request. Verify recursive UID-child absence and Firebase Auth absence before
  removing the tombstone or marking the unlinkable receipt `COMPLETED`.
- `NOT_FOUND`, malformed, unavailable, or timed-out receipt lookup is not
  completion. Preserve the private recovery journal and route identity mismatch
  to the reviewed support/admin path.
- Meet the seven-day acknowledgement and thirty-day completion targets or record
  the approved legal hold and user communication.

## Gemini safety, privacy, or cost incident

1. Disable the provider/API or enforce server quota first; Remote Config is only
   a cached client switch.
2. Publish canonical `gemini_suggestions_enabled=false` and verify both native
   platforms reject non-canonical or stale enable values.
3. Keep deterministic English/Hindi templates available. Never invalidate an
   already approved Android payload merely because Gemini is unavailable.
4. Verify AI monitoring remains Off and inspect approved aggregate metrics only.
   If prompt/response sampling occurred, treat it as a privacy incident.
5. Re-enable only after model/version/location, App Check replay, quota, billing,
   validator, red-team, and monitoring evidence is reapproved.

## OAuth, Google People, or Firebase identity incident

- Pause affected sync and automation on detection. Preserve the last verified
  contact generation only within the documented freshness limit.
- Revoke/disable the affected OAuth client or Firebase provider through the exact
  tier. Do not log or manually inspect user tokens.
- Recheck package/bundle, signing certificate, redirect scheme, Web client,
  scope, API-key restriction, App Check, and account-deletion behavior before
  reconnecting.
- A different Google subject or Firebase UID never attaches to retained setup.

## SEND_SMS policy, installer, carrier, or legal suspension

- Stop new arms, stop distribution, and remove the affected channel/carrier from
  the supported matrix. Do not disguise the app as a default SMS client.
- Preserve user planning data and expose truthful paused/action-needed status.
- Do not silently replace unattended SMS with a cloud sender or iOS-like
  composer; that is a product-contract change requiring full change control.
- Resume only with written policy/legal approval, installer allowlisting, signed
  artifact proof, and a repeated physical carrier/SIM/background matrix.

## Native dependency advisory or scan-service incident

Follow the exact scope and exception rules in
[`NATIVE_DEPENDENCY_ADVISORY_GATE.md`](NATIVE_DEPENDENCY_ADVISORY_GATE.md).

- Stop the candidate for any active finding, failed Maven/npm/Swift canary,
  incomplete lock/SBOM graph, unverified CocoaPods source mapping, expired or
  unmatched exception, OSV outage, or CocoaPods CDN verification failure. A
  transport failure is never zero findings, and an older passing report is not
  current evidence.
- Classify the affected identity as Android production runtime, the broader
  Android build/test graph, Android build plugins, or iOS CocoaPods. Preserve
  that distinction in the incident and release record; tooling-only exposure is
  still release-blocking but must not be misreported as shipped runtime code.
- Prefer a compatible fixed dependency or removal. Regenerate the exact locks,
  Gradle checksum metadata, SBOMs, CocoaPods source map when applicable, and
  live advisory report, then repeat every affected native build/test matrix.
- Ordinary CI permits no exceptions. Any time-bounded residual-risk exception
  requires a distinct owner/approver, exact finding and package identities,
  independent evidence, at most 30 days of validity, and a detached Ed25519
  signature over the exact exception bytes from the pinned release authority.
  The default unprovisioned authority pin accepts none.
- Keep tickets content-free. Retain package identities, advisory IDs, public
  references, source revision, tool time, and input/report digests; never add
  contacts, birthdays, phone numbers, messages, prompts, tokens, or credentials.

## Recovery checklist

Before restoring production behavior, prove all of the following:

- the current authority-signed cloud evidence package passes
  `npm run cloud:evidence:validate -- ...`; a protected read-only observation
  artifact alone is not approval, and no operator infers missing project/app/
  billing/Hosting identities from a local CLI default;
- exact tier, source, artifact, installed signer, Firebase/OAuth/App Check, and
  distribution evidence match;
- GlobalControl continuity and generation are known, with no unresolved migration
  or unexplained missing record;
- no new claim/Arm was possible during containment except a documented pre-issued
  permit within its frozen deadline;
- backend and mobile contract/emulator/device suites pass at the current source;
- the current lock-bound native advisory reports pass independently for Android
  production runtime, broader Android/build tooling, and iOS CocoaPods with no
  unauthorized or stale exception;
- privacy, deletion, accessibility, performance, carrier, and store evidence are
  current for the affected surface;
- staged probes pass before `armingEnabled` is restored; and
- the incident record contains a root cause, user impact, corrective actions,
  owners, deadlines, and a completed follow-up drill without private content.
