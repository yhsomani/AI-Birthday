# 05 — Security, Privacy, Infrastructure and Operations

**ID:** BP-SECOPS-001 · **Version:** 1.0 · **Status:** Target requirements and release gates.

## 1. Security model

### Assets
Identity/session material; entitlement evidence; Gemini credential; contact/birthday facts; private notes; draft body; device/import permissions; cloud backup; deletion receipts; signing/secrets; operational fencing state.

### Trust boundaries
Client UI ↔ use cases; use cases ↔ database/secure store; client ↔ native bridge; client ↔ AI provider; client ↔ store/identity; client ↔ backend; backend ↔ store authority; backend ↔ cloud owner data; client ↔ external messaging app; operator ↔ restricted tooling.

## 2. Authentication and authorization

- Core local workflows do not require sign-in unless the user later enables account-bound capabilities.
- Supported sign-in is real, provider-backed identity; synthetic OTP/demo accounts are forbidden in production.
- Store account UID/subject mapping separately from user-visible email; show sign-in account when backup/subscription is active.
- Refresh tokens remain in OS secure storage; revocation/refresh failure signs out cloud capability without deleting local data silently.
- Server derives owner UID from validated token; request-body UIDs are advisory and may not authorize access.
- Sensitive operations (account deletion, backup enable, account switch with data effects) require recent authentication.
- RBAC is unnecessary for the core single-user app. If operator tooling exists, require distinct roles such as support read-metadata, deletion operator and release operator, with MFA and audit.

## 3. Cryptography, secrets and transport

- TLS for all network traffic; certificate/platform validation must not be disabled for production.
- Credentials/signing/HMAC secrets use platform or managed secret stores and are rotated, least-privileged and excluded from builds/logs/backups.
- Gemini keys are never persisted outside approved secure storage and are never included in diagnostics/export/sync.
- Cloud backup must state that it is owner-scoped and protected in transit/at rest by provider controls only unless end-to-end encryption is actually implemented and key recovery tested. Current repository evidence does not justify a zero-knowledge claim.
- Local SQLite confidentiality must not be overstated. Android backup/export and OS/device compromise must be reviewed; exclude secret stores from platform backup or disable app backup after product trade-off approval.
- Hashing/purpose-binding protect integrity fingerprints and fences; secrets must not be derived from readable device identifiers.

## 4. Privacy and consent matrix

| Data operation | Before operation | Revocation / deletion |
|---|---|---|
| Device contacts import | Explain copied fields; Android permission | Stop further read; delete imported local copies separately |
| AI cloud provider | Entitled request; show recipient facts/draft sent | Stop requests; draft remains local; remove key |
| AI on-device | Capability and entitlement; disclose model/download behavior | Disable AI/download use where possible |
| Cloud backup | Auth + exact categories + security boundary | Stop new uploads; choose remote deletion |
| Notifications | Purpose, in-app preference, OS permission | Cancel/reconcile schedules |
| External channel handoff | Recipient and exact body review | No automated repeat; user controls external app |
| Automatic personal SMS (conditional) | Separate consent, recipient approval, exact content/revision, store-policy check | Revoke approval; cancel pending work |
| Diagnostics | Explicit, minimal purpose; local diagnostics first | Opt-out and retention policy |

No names, birthdays, destinations, facts, messages, prompts or tokens enter logs/analytics by default. If privacy-safe telemetry is later proposed, define event catalog, consent, retention and data controller responsibility first.

## 5. Threat scenarios and controls

| Threat | Prevent/detect | Required test |
|---|---|---|
| Entitlement forged by client | Store verification, server-owned state, expiring snapshot | Tamper/replay/wrong account/package/product |
| Cross-account backup access | UID-derived rules and tests | Read/write/delete denial for another account |
| Secret in logs/export/backup | Field classification, serializers denylist, secret scans | Realistic redaction and artifact scans |
| Prompt injection | Facts as data, bounded schemas, no tool authority, review | Adversarial name/note/fact input |
| Wrong-recipient handoff | Persistent recipient/number display and final review | Misdirection and number boundary cases |
| Launch recorded as sent | Separate event/state model | Kill/resume/no-confirmation tests |
| Duplicate automated SMS | Revision approval, idempotency key, lease/fence, stale expiry | Crash/retry/race/reboot tests |
| Restore corruption/resurrection | Manifest/hash, full prevalidation, tombstones/version conflict | Corrupt/partial/stale/deleted/conflict sets |
| Replay of purchase/control endpoint | Idempotency + rate limit + fencing | Duplicate/concurrent/reordered calls |
| Malicious malformed data | Strict schema, enum/date/URI validation, bounds | Fuzz/property/boundary suite |
| Account deletion fence failure | Deletion marker checked by writers; completion reconciliation | Concurrent backup/upload vs deletion |
| Privileged operator misuse | RBAC/MFA/audit/justification/least access | Quarterly access review evidence |

## 6. Secure development lifecycle

- Threat model changes with each data-flow/API/permission/feature change.
- Dependency/source license/vulnerability checks on every protected branch and release.
- Static analysis, secret scan and manifest/flavor permission checks in CI.
- Security review for new SDK, AI provider, external messaging, billing, backup or operator surface.
- Triage policy: release blockers, owner, mitigation, due decision/retest. No hidden acceptance of critical/high issues.
- Coordinated vulnerability intake and responsible disclosure contact are operational requirements if public distribution begins.

## 7. Infrastructure model

Use managed, region-selected infrastructure where possible to minimize undifferentiated operations. Separate dev/test/staging/prod projects and least-privilege deployment identities. Pin and review regions for privacy/legal and provider availability. Signing keys and production secrets are not available to ordinary developer machines.

Required infrastructure inventory: identity project; backend/functions runtime; owner data store; public legal/support site; object/backup storage if applicable; log/metric backend; alert channel; CI/build runners; app store accounts; secret manager; DNS/TLS; release artifact storage.

Infrastructure changes should be version-controlled and reviewed. Current repository files are evidence, not proof of deployed infrastructure.

## 8. CI/CD and release process

1. Pull request: format, lint, static analysis, unit/component/backend/rules tests.
2. Security: dependency, secret, rules, manifest, permission and configuration checks.
3. Build: supported flavors, variants and source provenance; exclude forbidden capabilities per flavor.
4. Integration: backend emulator and approved live sandboxes.
5. Candidate: signed reproducible/traceable artifact, SBOM/provenance, migration compatibility.
6. Pre-release: physical-device matrix, accessibility, purchase, provider, backup/delete and rollback rehearsal.
7. Staged rollout: cohort, health criteria, pause/disable switches, support readiness.
8. Post-release: smoke, telemetry/alert review, incidents, rollback/forward-fix.

Release cannot bypass branch protection, required review or store distribution controls. A release approval contains commit, build, environment, test evidence, known risks and approver.

## 9. Observability

Permitted examples: `operation.category`, `result.code`, `latency_bucket`, `retryable`, `schema_version`, `flavor`, `app_version`, `platform_major`, `provider_class`, `capability_state`, coarse counts. Prohibited: direct or reversible encodings of names, numbers, birthdays, notes, message/prompt body, email, UID, tokens, keys.

Metrics should be aggregated with low-cardinality labels. Traces sample cautiously and redact at source. Audit logs for entitlement verification, backup policy change, deletion and operator actions require integrity, access control and documented retention.

## 10. Incident and disaster-recovery runbooks

Minimum runbooks:
- purchase verification outage/wrong grant or denial;
- cloud backup read/write/restore integrity failure;
- account deletion stalled or over-fenced;
- AI provider invalid/quota/outage;
- reminder/Nano/platform capability regression;
- suspected secret/token leak;
- malformed database migration or crash loop;
- cross-account authorization report;
- bad production release and rollback;
- public legal/deletion site outage.

Each runbook has trigger, owner, access prerequisites, severity, contain/disable steps, evidence preservation, customer/support message, recovery verification and post-incident actions. Recovery exercises validate database migration, backup restore and deletion reconciliation before production reliance.

## 11. Security/privacy release gate

- [ ] Data-flow inventory and policy copy agree with actual behavior.
- [ ] Threat scenarios have named tests and owners.
- [ ] Cross-account, entitlement, prompt, replay and deletion tests pass.
- [ ] Secret/dependency/manifest scans pass or have approved exceptions.
- [ ] Retention/deletion scopes are approved and exercisable.
- [ ] Operator access is least privilege, MFA-protected and audited.
- [ ] Incident, backup/restore and rollback runbooks are rehearsed.
- [ ] No unsupported encryption, deployment or send-state claim appears in UI/docs.
