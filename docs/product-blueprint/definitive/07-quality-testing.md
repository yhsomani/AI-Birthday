# 07 — Quality, Verification and Release Strategy

**ID:** BP-QA-001 · **Version:** 1.0 · **Status:** Required strategy; exact test IDs live in feature contracts and register.

## 1. Evidence policy

Highest evidence first: production/device observation on named build/environment; passing CI on exact commit; executable automated test; static source; documentation; historical label. Runtime functionality, deployment and documentation authority are never inferred upward from lower evidence.

Every release candidate records commit, artifact, environment, supported devices, configuration hash, dependency state, migration baseline, test results, known risks and approval.

## 2. Requirement-to-test contract

`Requirement → Feature → Acceptance criterion → Test ID(s) → Build/commit → Environment → Result → Defect/risk`.

A feature is complete only when normal, boundary, negative, concurrency/retry, failure/recovery, security/privacy and accessibility cases have been mapped. A test with no requirement/rule must cite a defect, risk or compatibility reason.

## 3. Test layers

| Layer | Scope | Minimum obligations |
|---|---|---|
| Unit/domain | validators, policies, state machines, prompting, serializers | tables, property/boundary, invalid transitions, exact no-provider/no-send assertions |
| Database/repository | CRUD, constraints, migration, transactions, tombstones, jobs | every field round-trip, fault injection, old-schema compatibility, query plan |
| Component/widget | forms, states, accessibility, responsive | loading/empty/error, 2x text, narrow, IME, focus and live semantics |
| Integration | use case + repositories/adapters; client/backend envelopes | exact deployed envelope, cancellation, timeout, idempotency, stale result |
| API/backend | auth, rules, billing, backup/delete/control operations | wrong UID/account/package/product, malformed/replay/concurrency/rate limits |
| Platform/device | alarms, reboot, permissions, contacts, Nano, SMS, intents | supported OS/device matrix, revoke/regrant, process death, flavor |
| End-to-end | complete role journeys | first value; manual; AI; handoff/confirm; restore; deletion; support |
| Security | assets/trust boundaries | secret redaction/scan, cross-account, prompt injection, tamper/replay, deletion fence |
| Performance/reliability | startup/lists/calendar/jobs/network | baselines first; regression budget after approval |
| Accessibility | automated + assistive + manual | screen reader/focus/contrast/touch/text/reduced motion |
| Compatibility | OS/store/provider/schema | published support matrix; graceful unknown/unsupported states |
| Release/operations | signing, artifacts, config, migration, rollback, monitoring | staged rollout rehearsal and rollback/disable drill |

## 4. Domain scenario matrix

### People/import
- Names at normalization and length boundaries; valid/invalid Gregorian date; Feb 29 policy; unknown legacy enum; invalid timezone; local number country selection; E.164; duplicate exact/near/phone/name-only; CSV malformed/encoding/column/order/oversize; contacts denial/revoke; edit while job/sync active; delete/undo; legacy no-birthday repair; export/import fidelity and secret exclusion.

### Birthday/planning/reminders
- today/tomorrow/yesterday; cycle/year boundary; leap/non-leap; recipient/device timezone; missing versus invalid timezone; DST; device timezone change; app/process death/reboot; duplicate refresh; old cycle retention; reminder offset/quiet-hour midnight boundary; permission denied/exact alarm unavailable; schedule cancel/reconcile.

### Studio/AI
- empty/long/multilingual draft; autosave conflict/stale job; app killed; manual with AI locked; active entitlement + valid key; no entitlement + key/Nano; invalid key; quota/timeout/busy/download/unsupported/cancel; prompt injection; output not message/empty/oversize; regenerate/version; provider change; entitlement expires mid-request.

### Delivery/history
- invalid/missing phone; external app absent/rejects; exact URI encoding; body length/channel capabilities; launch then no return; return and confirm yes/no; kill between; app resume must not auto-confirm; duplicate attempt; edited revision; event ordering; clock adjustment; no false sent/delivered.

### Account/entitlement/continuity
- signed out/in; token refresh/revocation; account switch; pending/cancel/expired/refund/mismatch/replayed purchase; server unavailable; callable envelope exactness; purchase acknowledgement policy; offline cached snapshot expiry; backup opt-in absent; interrupted part; corrupt/partial/duplicate/stale/unknown schema; conflict/newer local; cross-account; deletion fencing; request accepted/failed/completed/unknown.

### Jobs/platform/operations
- duplicate enqueue; stale `running`; max retry; non-retryable auth; cancellation before/after side effect; clock change; reboot/Doze; native response unknown; model/download and permission lifecycle; rollout pause; secret/redaction and observability failure.

## 5. Named acceptance groups

| Group | Feature ranges | Primary release gate |
|---|---|---|
| TST-ENTRY | F-001–F-003 | first value, auth/capability isolation |
| TST-PEOPLE | F-010–F-019 | transaction/data fidelity/import privacy |
| TST-BIRTHDAY | F-020–F-024 | deterministic recurrence/integrity |
| TST-PLAN | F-030–F-039 | cross-screen/date consistency/reminders |
| TST-STUDIO-AI | F-040–F-059 | revision retention, entitlement/provider safety |
| TST-DELIVERY | F-060–F-068 | truthful channel/event state |
| TST-ACCOUNT | F-070–F-089 | owner/entitlement/backup/deletion |
| TST-AUTOMATION | F-090–F-099 | durability/idempotency/consent |
| TST-OPS | F-100–F-109 | observability/release/incident safety |

## 6. Quality gates

### Gate 0 — Specification
Every P0 feature: owner; actor/permission; state; input/output; owner model; privacy/security; acceptance criteria; negative/recovery cases; test trace; dependency; release phase. Contradictions are registered, not hidden.

### Gate 1 — Feature complete
Static analysis/build; unit/repository/component/API tests; requirement trace green; no critical/high security defect; accessibility and responsive state pass; documentation updated; no stale “sent”, encryption, deployed or provider claim.

### Gate 2 — Release candidate
CI exact commit; signed supported flavor; schema/API compatibility; live sandbox purchase/auth/provider; emulator/rules; supported physical device matrix; privacy/legal copy; monitoring/support/runbooks; backup/restore/deletion evidence for enabled surfaces.

### Gate 3 — Operational release
Canary/staged rollout, smoke/health, alert and pause/disable, known risk approval, rollback rehearsed, deletion/support owner active. Promotion never bypasses branch protections or review.

## 7. Release acceptance criteria

- Core manual loop works local-first after first-person save, including process restart.
- All AI provider calls are zero when entitlement is locked/unknown.
- No handoff can write user-confirmed sent/delivered without user action.
- Data deletion/restore never silently resurrects, partially corrupts or crosses accounts.
- Reminders explain permission/capability and do not duplicate after reconcile/reboot.
- Supported accessibility matrix passes critical journeys.
- Security/dependency/config scans and threat scenarios pass or approved blocker-free exception exists.
- Deployment and operator claims have environment evidence.

## 8. Current evidence limitation

Existing CI and tests are useful repository evidence. They do not prove physical reminders/Nano/SMS, live OAuth/Play/provider behavior, production Firebase/Hosting, data migration against real user databases, privacy/legal compliance or actual usability. Those remain named validation work rather than a false 100% completion claim.
