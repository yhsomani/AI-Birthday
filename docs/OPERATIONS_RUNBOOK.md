# WishWell Operations Runbook

## Operating Rules

- Use the exact tier and immutable source/artifact evidence from the approved release record. Never infer a project, package, signing identity, region, or operator account from a local default.
- Never paste callable bodies, Firebase UIDs, installation IDs, request UUIDs, opaque claim/guard values, contact data, phone numbers, birthdays, message text, prompts, tokens, keys, or raw exception objects into an incident system.
- A control-plane outage, uncertain ledger, signing incident, policy suspension, or unexplained duplicate is a **stop-new-arms event**. Availability never outranks at-most-one submission safety.
- Do not delete or rewrite an Armed claim, destination guard, Arm outcome, deletion tombstone, or unresolved local barrier to make recovery appear clean.
- Never manually delete, release, shorten, or rewrite a logically live `COMMITTED` or ambiguous-sticky iOS composer reservation for availability. A live hold can represent a MessageUI action whose final payload/result is unknowable; Android must remain paused even after Cancel, failure, Unknown, process death, sign-out, revoke, or local wipe.
- Two authorized people review every production containment or recovery change. Retain the content-free change request, exact before/after configuration bytes, source revision, UTC times, operator identities, and approval reference.

## Common Incident Procedure

1. Open a content-free incident record and assign severity, incident commander, privacy/security lead, communications owner, and release owner.
2. Identify the exact tier from signed release evidence. Confirm it again before every console, CLI, IAM, Secret Manager, Firebase, Play, or App Store action.
3. Stop new Android arms by setting that tier's `GlobalControl.armingEnabled` to `false` through the reviewed privileged operator path. Preserve `ledgerGeneration`, sender epochs, claims, guards, and existing outcomes.
4. If ledger integrity is uncertain, also set `continuityState` to `FROZEN`. Never create a new healthy generation merely because records are missing.
5. Verify with an authenticated/App-Check production probe that new claims/arms fail closed. A previously issued permit may still cross before its recorded deadline; disclose that bounded possibility.
6. Preserve immutable, content-free evidence. Do not collect user screenshots or database exports containing private birthday/message data.
7. Apply the scenario procedure below. Restore service only after its exit criteria and the general recovery checklist pass.

Recovery checklist

Before restoring production behavior, prove all of the following:

- the current authority-signed cloud evidence package passes `npm run cloud:evidence:validate -- ...`; a protected read-only observation artifact alone is not approval, and no operator infers missing project/app/billing/Hosting identities from a local CLI default;
- production Hosting was deployed by the protected keyless workflow from its canonical artifact, and the signed cloud `hosting-release` evidence hashes the retained manifest/provenance whose Firebase CLI version matches the live `DEPLOY` release and exact created Hosting version;
- exact tier, source, artifact, installed signer, Firebase/OAuth/App Check, and distribution evidence match;
- GlobalControl continuity and generation are known, with no unresolved migration or unexplained missing record;
- no new claim/Arm was possible during containment except a documented pre-issued permit within its frozen deadline;
- every live iOS composer reservation remained authoritative until exact PREPARED-owner release, server logical expiry, or transactionally dominant account deletion; no operator used TTL lag, local journal loss, Cancel, Failed, Sent, Unknown, or crash as an early-release proof;
- backend and mobile contract/emulator/device suites pass at the current source;
- the current lock-bound native advisory reports pass independently for Android production runtime, broader Android/build tooling, and iOS CocoaPods with no unauthorized or stale exception;
- privacy, deletion, accessibility, performance, carrier, and store evidence are current for the affected surface;
- staged probes pass before `armingEnabled` is restored; and
- the incident record contains a root cause, user impact, corrective actions, owners, deadlines, and a completed follow-up drill without private content.

Scenario procedures

Release rollback or unsafe build

**Trigger:** Defective release candidate, failed security scan, unsafe build configuration detected

**Procedure:**

1. Set `GlobalControl.armingEnabled = false` immediately
2. Set `continuityState = FROZEN` if build integrity uncertain
3. Document exact source revision, build artifacts, and failure mode
4. Revert to last known-good release evidence package
5. Re-deploy using protected keyless workflow
6. Verify with production probes before restoring arming

Android signing-key incident

**Trigger:** Signing key compromise, keystore corruption, certificate mismatch

**Procedure:**

1. Set `GlobalControl.armingEnabled = false` and `continuityState = FROZEN`
2. Revoke compromised credentials via Google Play Console
3. Generate new signing key using secure key management
4. Update distribution-authority-pin.json with new Ed25519 pin
5. Re-sign release artifacts with new key
6. Submit update via Play Console with key rotation disclosure
7. Verify signature chain before restoring service

HMAC pepper rotation

**Trigger:** Key compromise suspicion, scheduled rotation, security audit requirement

**Procedure:**

1. Maintain both current and previous HMAC keys in Cloud KMS `COORDINATION_HMAC_KEYRING`
2. Update keyring with new primary key, demote old key to secondary
3. Deploy Functions update with new key references
4. Monitor alias derivation for 48 hours
5. Remove old key only after confirming zero legacy derivations
6. Document rotation in incident record

Functions, Firestore, or regional outage

**Trigger:** asia-south1 regional failure, Firebase service degradation

**Procedure:**

1. Set `GlobalControl.armingEnabled = false` (fail-closed)
2. Communicate outage status via web tier
3. Await Firebase status dashboard resolution
4. Verify service restoration with authenticated probes
5. Resume arming only after full functional verification

Ledger corruption, disaster recovery, or duplicate report

**Trigger:** Conflicting claim records, duplicate SMS report, Firestore inconsistency

**Procedure:**

1. **IMMEDIATE:** Set `GlobalControl.armingEnabled = false` and `continuityState = FROZEN`
2. Export content-free ledger state (no PII)
3. Analyze claim epochs, occurrence keys, destination guards
4. Identify root cause: worker duplication, clock skew, race condition
5. Apply surgical correction only to corrupted records
6. Run emulator reproduction with identical conditions
7. Document findings and preventive measures
8. Restore arming only after duplicate-prevention verification

Account-deletion failure

**Trigger:** Deletion saga stall, incomplete child deletion, receipt generation failure

**Procedure:**

1. Verify deletion tombstone exists and is properly fenced
2. Drain any pending operations before proceeding
3. Ensure children deletion completes atomically
4. Generate deletion receipt with content-free confirmation
5. Verify no resurrection on re-registration attempt
6. Document failure mode and resolution

Gemini safety, privacy, or cost incident

**Trigger:** Prompt injection, PII leakage, unexpected cost spike, safety filter bypass

**Procedure:**

1. Disable Gemini gateway via operational gate
2. Fall back to built-in templates only
3. Review prompt logs for PII exposure (redact immediately)
4. Audit rate scopes and provenance registry
5. Adjust timeout/rate limits if cost-related
6. Re-enable only after prompt policy validation

OAuth, Google People, or Firebase identity incident

**Trigger:** Auth token compromise, contacts sync failure, session invalidation

**Procedure:**

1. Revoke affected OAuth tokens via Google Cloud Console
2. Force session invalidation via Firebase Auth
3. Verify incremental contacts.readonly scope enforcement
4. Confirm no refresh tokens stored on device
5. Re-test auth flow with fresh credentials
6. Document scope boundaries and token handling

SEND_SMS policy, installer, carrier, or legal suspension

**Trigger:** Carrier blocking, policy violation notice, legal cease-and-desist

**Procedure:**

1. **IMMEDIATE:** Set `GlobalControl.armingEnabled = false` globally
2. Document exact trigger (carrier code, legal notice reference)
3. Suspend affected sender installations
4. Engage legal/compliance review if required
5. Implement policy corrections
6. Obtain carrier/regulatory clearance before resuming
7. Document compliance verification

---

## Full Documentation

Complete operating procedures, recovery checklists, and scenario details are maintained in [`SSOT.md`](SSOT.md) section B.9 (Operations Runbook Key Procedures).

## Native dependency advisory or scan-service incident

1. Treat any native dependency advisory or scan-service incident as a stop-new-arms event if it applies to an active release tier.
2. Confirm whether the advisory impacts iOS, Android, or both, and pause the affected platform(s).
3. Do not assume zero reported vulnerabilities means the system is fully secure.
