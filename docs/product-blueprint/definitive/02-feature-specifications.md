# 02 — Product, Module and Feature Specifications

**ID:** BP-FEAT-001 · **Version:** 1.0 · **Status:** Specification baseline; P0 approval coverage remains partial.

This catalogue decomposes Product → Domain → Module → Feature. Each feature contract is atomic enough for requirements, UX and tests; implementation remains prohibited by this document. Common field details apply unless overridden.

## Contract conventions

Every feature row specifies: `ID; ownership; problem/outcome; actors/permission; preconditions; input/output; processing/state; UI/config/search/notifications; validation; rules/data/integration; privacy/loading/error/edge/dependencies/AC/tests/priority-phase` in compact structured clauses.

Global AC suffixes: `AC-S` safe state/no data loss; `AC-T` truthful state/no false send/grant; `AC-A` accessibility/responsive; `AC-P` privacy/authorization; `AC-R` retry/idempotency. Phase: P0 product validation, P1 contracts, P2 domain/data, P3 UX, P4 architecture/security, P5 core local, P6 delivery, P7 AI, P8 continuity, P9 conditional, P10 release, P11 operate.

---

## DOM-01 — Entry, onboarding and navigation

### F-001 Local initialization and migration guard
**Owner/Purpose:** Entry core; ensure user can reach a trustworthy local workspace. **Actors:** local user. **Pre:** app launch. **Inputs:** current DB/schema, environment/config. **Outputs:** initialized app or blocked recovery state; operation ID. **Behavior:** validate schema/capabilities; run reviewed migration; open DB transactionally; never silently switch to in-memory storage on failure. **UI:** splash only while bounded; blocked recovery with export/diagnostic action. **Data:** DB metadata, migration evidence. **Rules:** NFR-001/010, SEC-001. **Errors/edge:** corruption, old/unknown version, storage full, partial migration, downgrade. **Dependencies:** data migration design. **AC:** valid old/current DB reaches same local shell; failure preserves data and exposes no PII; restart safe. **Tests:** TST-ENTRY unit/migration/process-death. **Priority/phase:** P0/P5.

### F-002 Value and privacy onboarding
**Owner/Purpose:** Onboarding; establish value and optional capability boundaries. **Actors:** local user. **Pre:** incomplete/dismissible onboarding flag. **Inputs:** choice to continue/skip. **Outputs:** completion/provisional state. **Behavior:** explain remember/prepare/review/handoff; local-first default; AI, sign-in and backup are optional; permissions requested later. **UI:** short focused pages, skip, “Add person”, accessible progress. **Rules:** privacy by default, user control. **Errors:** storage flag failure must not recreate silent consent. **AC:** user can summarize what is local/optional and reach manual first value; no permission/remote write. **Tests:** content, widget, E2E, accessibility. **Priority:** P0/P5.

### F-003 Navigation shell and deep-link resolution
**Owner/Purpose:** Navigation; predictable access to Dashboard/People/Calendar/History/Settings. **Pre:** initialized app. **Inputs:** tab/route/entity ID. **Outputs:** destination or safe fallback. **Behavior:** resolve entities/cycle, preserve unsaved draft prompt, handle auth/account context, reject unknown/mismatched ownership. **UI:** persistent navigation appropriate to device; back semantics, labels. **Search:** not applicable. **Failures:** missing/deleted/foreign record; stale route; malformed deep link. **AC:** route reaches exact occurrence; denied content reveals no owner details; back does not silently lose edits. **Tests:** unit routes, widget, E2E, security. **Priority:** P0/P5.

---

## DOM-02 — Relationship memory

### F-010 Create Person
**Purpose:** store one important recipient accurately. **Actors:** local/authenticated owner. **Pre:** initialized DB. **Inputs:** name, month/day, optional year/contact/category/closeness/language/tone/facts/notes/timezone/leap policy/channel/automation. **Outputs:** Person, initial occurrence, audit metadata. **Processing:** normalize; validate all fields; atomically persist aggregate; derive occurrence. **UI:** grouped required/optional fields; country context for local phone; privacy cues for facts/notes. **Validation:** required trimmed name; valid Gregorian date; unknown invalid timezone repair; E.164 under approved policy; bounded fields. **Data:** Person aggregate. **Rules:** BR-001/002/003/036/037. **Notifications:** none automatically unless preference. **Errors:** preserve form values, no partial Person/occurrence. **Edges:** Feb 29, leap policy, DST, year omitted, duplicate candidate. **AC:** every intake writes canonical Person+one occurrence or nothing; no unrelated defaults mutate. **Tests:** TST-PEOPLE; normal/boundary/transaction. **Priority:** P0/P5.

### F-011 Edit Person and preferences
**Purpose:** correct recipient context without corruption. **Pre:** active Person. **Behavior:** load canonical model; dirty tracking; save a full version-checked update plus impacted occurrence reconciliation. **Permissions:** owner only. **Validation:** same as create; unknown legacy enum cannot silently become another value. **UI:** explicit modified fields, discard confirmation, leap/timezone explanation. **Errors/concurrency:** stale version conflict; preserve newer remote/local field; retry/merge per policy. **Dependencies:** F-010, F-021. **AC:** all non-edited fields byte-/semantically equal; correct reminder/job invalidation. **Tests:** full-field round-trip, concurrency, legacy. **Priority:** P0/P5.

### F-012 View/search/filter Person
**Purpose:** retrieve recipients quickly. **Inputs:** text, birthday month, relationship, status. **Processing:** normalized local search; sort name/next birthday. **UI:** list/detail, clear filter, count, empty/no-match. **Permission:** own records; include-deleted only in recovery/cleanup context. **Privacy:** do not index/export secrets; no telemetry terms. **Edge:** accents, scripts, punctuation, large lists. **AC:** search does not expose deleted data; result and dashboard refer to same Person. **Tests:** query boundary, performance baseline, semantics. **Priority:** P0/P5.

### F-013 Delete Person and undo
**Purpose:** remove a recipient with truthful scope/recovery. **Pre:** active Person. **Behavior:** request scope preview → transaction marks Person deleted/stops active effects; linked visibility/retention follows approval; bounded undo restores exact aggregate. **UI:** confirm affected occurrences/drafts/reminders/history; undo duration visible only if durable. **Data:** tombstone/retention, cancellation jobs. **Errors:** if purge/remote delete fails, state explicit and resumable. **Dependencies:** DEC-027, continuity policy. **AC:** cannot resurrect from restore; no orphan active birthday; undo restores declared exact scope. **Tests:** transaction, restart, sync race, timeout. **Priority:** P0/P5, policy gate.

### F-014 Device contacts import
**Purpose:** bootstrap People from approved Android contacts. **Pre:** permission requested only after action. **Inputs:** provider rows/fields selected. **Output:** preview/import result. **Behavior:** fetch minimum fields; map; validate birthdays; normalize; duplicate review; persist selected valid rows. **UI:** education, permission denied, mapping/preview, per-row reasons/repair, select. **Security:** imported copies remain until app deletion; future access stops on revocation. **Edges:** no birthday/partial dates, malformed numbers, provider changes. **AC:** denial has valid exit; no row auto-saved without review; counts reconcile. **Tests:** platform/fakes/permission. **Priority:** P1/P5.

### F-015 CSV import
**Purpose:** portable intake at user control. **Inputs:** bounded file, encoding, headers. **Output:** validation/duplicate preview and result. **Behavior:** parse locally; map stable fields; reject unsupported/unknown sensitive columns by policy; never execute formulas; validate all candidates before writes. **UI:** upload, field map, row status, select/repair, result counts. **Errors:** wrong encoding, formula content, malformed/empty/oversize/invalid date, duplicate internal IDs, partial save. **Data/API:** local parser, documented CSV schema/version. **AC:** no invalid birthday written; formula-like values treated as text; exact skipped/failed/imported counts. **Tests:** corpus, fuzz, widget/E2E. **Priority:** P1/P5.

### F-016 Duplicate review
**Purpose:** prevent accidental duplicate recipient records without unsafe merge. **Inputs:** candidate versus existing name/phone/email/birthday. **Output:** keep separate/link/skip selected by user. **Behavior:** present candidates and evidence; name alone never auto-merges. **UI:** side-by-side facts, risk if same number. **Edge:** same family names, twins/same date, aliases. **AC:** every merge or link is explicit; no automatic relationship inference. **Tests:** scoring/property/security. **Priority:** P1/P5.

### F-017 Export user-owned People data
**Purpose:** portable copy/data control. **Input:** selected/all active and scope choices. **Output:** versioned CSV/JSON bundle per documented format. **Behavior:** include declared fields; exclude keys/tokens/entitlement secrets; include legacy warnings and deleted scope choice. **UI:** privacy preview, result/path, sensitive warning. **AC:** round-trip permitted fields; secret denylist; user receives exact record count. **Tests:** serialization, secret scan, portability. **Priority:** P1/P8.

### F-018 Relationship facts and prompt context
**Purpose:** maintain grounded personalization. **Input:** fact text, language. **Output:** approved fact list with provenance as user/imported. **Behavior:** facts remain distinct from free notes; prompt allow-list only facts approved for AI; no inference. **Validation:** size, control characters, no credential-like accidental content warning. **UI:** explain use to provider; remove/revoke. **AC:** fact deletion stops future prompts; notes never become hidden instructions; prompt test exact allow-list. **Priority:** P0/P7.

### F-019 Legacy Person repair
**Purpose:** recover records missing valid birthday/timezone/leap policy. **Behavior:** preserve and label `Needs attention`; view/export; suppress occurrence/reminders; require explicit repair before successful profile mutation. **UI:** actionable field repair, counts, no fabricated birthday. **Dependencies:** inventory and decisions DEC-023/024/029. **AC:** legacy record is not lost or scheduled under guessed data; repair creates/updates canonical occurrence once. **Tests:** migration/aggregate. **Priority:** P0/P5 blocker.

---

## DOM-03 — Birthday engine

### F-020 Resolve annual occurrence
**Purpose:** deterministic next birthday. **Input:** Person birthday, cycle year, leap policy, recipient timezone/device fallback. **Output:** occurrence date, timezone, calculation reason. **Rules:** Gregorian, Feb 29 policy; approved recipient→device precedence; invalid explicit zone not absent. **UI:** display date/timezone if consequential. **AC:** table vectors agree across all consumers, including DST/year. **Tests:** TST-BIRTHDAY pure unit. **Priority:** P0/P5.

### F-021 Create/reconcile cycle identity
**Purpose:** one durable occurrence per person/year. **Behavior:** transactionally create or retrieve unique `(personId, cycleYear)`; never replace historical identity on rollover; reconcile after edits/restore/resume. **Edges:** duplicate job/refresh, clock changed, soft deletion, legacy duplicate. **AC:** parallel/repeated reconciliation yields one; old evidence preserved. **Dependencies:** schema decision DEC-026/034. **Priority:** P0/P5.

### F-022 Birthday lifecycle transitions
**Purpose:** truthful action state. **States:** `Upcoming → PreparationNeeded → Drafted → ReadyToReview → HandedOff → UserConfirmed/NotSent/Unknown → Completed`; rollover/failure classification separate. **Permissions:** app/system event writers restricted by use case. **Behavior:** reject invalid transition and record reason; history events append. **UI:** shared labels. **AC:** cross-screen reads canonical event/status; unknown never auto-completes. **Tests:** state matrix/property. **Priority:** P0/P5–P6.

### F-023 Leap-day preference
**Purpose:** explicit non-leap handling. **Inputs:** Feb 28 or Mar 1. **Processing:** required saved choice per Feb-29 Person; missing legacy choice creates repair/blocked schedule. **UI:** contextual choice, effects across dates. **AC:** leap year always Feb 29; non-leap only chosen date; never inferred from old date. **Rules:** BR-003/DEC-012. **Priority:** P0/P5.

### F-024 Timezone and local-midnight reconciliation
**Purpose:** consistent occurrence across recipient/device zone. **Behavior:** use valid recipient IANA zone; else device-local; non-empty invalid zone pauses derivation and repair; recompute on app resume/OS timezone change/recipient-local midnight as approved. **UI:** timezone shown in settings/exception. **Edges:** DST nonexistent/ambiguous, device zone unavailable. **AC:** no UTC/date-only helper can disagree. **Dependencies:** DEC-029/033. **Priority:** P0/P5.

---

## DOM-04 — Planning

### F-030 Dashboard today/action/upcoming
**Purpose:** identify next action. **Input:** canonical occurrence/draft/delivery/reminder data. **Output:** prioritized sections. **Search/filter:** time horizon, action state, relationship. **UI:** exact date/recipient/status; direct Studio; “no birthdays” and errors. **Processing:** no independent date/state policy. **AC:** parity with Calendar/History on same snapshot; loading is not empty. **Priority:** P0/P5.

### F-031 Calendar month/date view
**Purpose:** spatial birthday planning. **Inputs:** month/date and canonical occurrences. **Behavior:** month grid, day list, navigation/today; multiple occurrences. **A11y:** full date names and event count. **Edges:** leap, year/DST boundary, large font/zoom. **AC:** same records as dashboard; no overflow or hidden primary date action. **Priority:** P0/P5.

### F-032 Action prioritization and overdue recovery
**Purpose:** make urgent/failed work clear. **Behavior:** rank today, preparation due, failed/action-required, then upcoming by safe timestamp. **Rule:** not-sent/unknown remains recoverable but cannot be silently completed. **Config:** user sorting/filter; snooze only if product-approved. **AC:** rank deterministic; hidden expired cycle does not erase history. **Priority:** P1/P5–P6.

### F-033 Feature discovery and empty state progression
**Purpose:** guide first/next useful action without dark patterns. **Behavior:** context action (add person, prepare, configure reminder) from empty/error; never decorative metric. **Notification:** no unsolicited prompt without permission. **AC:** every empty state has an actionable, accessible next step or clear no-action. **Priority:** P1/P5.

---

## DOM-05 — Reminders

### F-034 Reminder preference configuration
**Owner/Problem:** Reminders; user-controlled notification behavior. **Inputs:** enabled offsets/kinds, quiet start/end, timezone/permission context. **Validation:** 0–1439 minutes; empty/duplicate offsets; crossing midnight. **UI:** preview examples, OS permission state, reset. **AC:** persisted preference survives restart and does not imply schedule success. **Priority:** P0/P5.

### F-035 Schedule reconciliation
**Behavior:** derive deterministic schedule keys from occurrences/preferences; register/cancel with OS; reconcile after startup/resume/reboot/update/permission/timezone change. **Failure:** capability unavailable/denied/quota/OS rejection retained as explicit state and guidance. **Idempotency:** duplicate reconcile no duplicate alarm. **AC:** intended set equals reconciled set; no notification for deleted/unknown occurrence. **Tests:** platform/device/reboot/fake clock. **Priority:** P0/P5.

### F-036 Quiet-hours policy
**Behavior:** evaluate start/end interval across midnight and exact boundary; approved current direction suppresses/skips, not delayed, until separate decision. **UI:** exact copy/preview. **Edges:** equal times; timezone change; offset due before/inside/outside. **AC:** boundary table passes and suppressed notification is not represented as delivered. **Priority:** P0/P5.

### F-037 Notification content and deep link
**Output:** minimal title/body with recipient and action category, no sensitive facts/message. **Permission:** POST_NOTIFICATIONS where required; manual path remains. **Behavior:** tap resolves exact occurrence or explains deleted/missing. **Edge:** multiple same date, app killed. **AC:** recipient exposure minimized, route deterministic, no send action. **Priority:** P0/P5.

### F-038 Permission lifecycle and unsupported OS
**Purpose:** truthful reminder availability. **Behavior:** check on use, observe revoke/regrant; distinguish never asked, denied, permanently denied, exact alarm unsupported/denied, device optimization. **UI:** settings path and manual access. **AC:** denying never blocks People/Studio; no “scheduled” claim. **Priority:** P0/P5.

---

## DOM-06/07 — Studio and AI

### F-040 Manual draft editor
**Purpose:** compose without external dependency. **Inputs:** occurrence/Person context and text. **Output:** revisioned local draft. **Behavior:** autosave committed edits; dirty/ saving/saved/error; preserve on navigation/process death where possible; bounded formatting. **UI:** dominant editor, counter, undo, recipient/date, save/review. **Validation:** nonempty before handoff; size/channel metadata. **AC:** latest committed revision restores; failure never discards in-memory input. **Priority:** P0/P5.

### F-041 Draft revision and concurrent edit control
**Purpose:** safe autosave/provider result. **Behavior:** monotonic revision; stale provider/job result cannot overwrite newer user edit; generation preview requires explicit apply; superseded revision retained per retention policy. **Data:** draftId+revision, base revision. **Edge:** rapid typing, app kill, two device futures excluded unless sync expanded. **AC:** last user action wins deterministically; provider result references request base. **Priority:** P0/P5/P7.

### F-042 Review and channel readiness
**Purpose:** final human control. **Behavior:** show exact body, recipient, destination/channel, tone/language, size/constraint; user marks reviewed and selects channel. **Validation:** no empty/invalid destination; edits revoke prior review. **Output:** channel-ready exact DraftRevision. **AC:** no handoff from changed unreviewed content. **Priority:** P0/P6.

### F-050 Subscription entitlement gate
**Purpose:** prevent unauthorized AI. **Inputs:** expiring server-verified entitlement capability. **Behavior:** active? check before provider selection and immediately before invocation; unknown/expired/revoked/invalid locks. **UI:** subscription required, checking, active; manual path. **No dependencies on provider key.** **AC:** provider call count zero for BYOK/Nano when no entitlement. **Tests:** unit/integration/tamper. **Priority:** P0/P7.

### F-051 Provider capability resolution
**Behavior:** entitled user with configured BYOK selects it; otherwise supported/ready on-device model; configured invalid key is error, not missing; neither → unavailable. **Output:** provider class/readiness/reason, no secret. **UI:** explicit switch/repair where availability permits. **AC:** deterministic priority, no hidden application cloud quota. **Priority:** P1/P7.

### F-052 AI generate/rewrite/tone/length/translation/variations
**Input:** approved facts, recipient relationship/language, selected draft operation, entitlement/provider. **Output:** candidate revision plus metadata. **Validation:** allow-listed fields, max lengths, no tools/untrusted facts, capability check. **Processing:** cancellable durable job; validate candidate as plain message. **Errors:** locked, invalid, quota, timeout, busy, download, unsupported, malformed. **UI:** progress/cancel/retry/apply; manual remains. **AC:** content editable; exact prior draft preserved. **Priority:** P1/P7.

### F-053 Prompt grounding and safety
**Purpose:** no invented personal details or injection. **Processing:** system policy + facts as data; no provider access to DB/credentials/tools; bound fields; output excludes AI disclaimers/meta. **Privacy:** disclose sent categories; cloud provider only on request. **Edges:** recipient note says ignore instructions, multilingual injection, secrets copied in facts. **AC:** adversarial suite preserves facts and no side effects. **Priority:** P0/P7.

### F-054 Nano capability/download lifecycle
**Purpose:** make on-device availability truthful. **States:** available/downloadable/downloading/not-ready/unavailable/busy/quota/error with retryability. **Behavior:** typed bridge; entitlement still required; user-visible download impact; cancellation and device support. **AC:** never label fake template as Nano; unknown device does not crash; manual path. **Priority:** P1/conditional P9 or P7 based platform decision.

### F-055 Credential configure/test/replace/remove
**Input:** user provider key. **Processing:** secure storage only, live bounded validation, persisted only non-secret verification timestamp; remove invalidates cache. **UI:** masked, own-key/billing disclosure, test state. **Security:** no backend sync/log/export. **Edges:** whitespace, invalid/revoked, account/project quota, network. **AC:** key absent from all non-secure artifacts and errors. **Priority:** P1/P7.

### F-056 AI stale/cancel/quota recovery
**Behavior:** cancel request; provider-side cancellation where possible; timeout; discard stale result; bounded retry only when retryable; no background content replacement. **UI:** preserve draft and expose retry/manual/configure. **AC:** cancelled/stale does not modify revision; idempotent request not double applies. **Priority:** P0/P7.

---

## DOM-08 — Delivery and history

### F-060 WhatsApp handoff
**Pre:** reviewed revision, validated phone, entitlement irrelevant to manual. **Behavior:** E.164/URI encode; launch official click-to-chat; record `handoff attempted/accepted/failed` only. **Output:** event and return state. **UI:** exact recipient/body, “You complete sending”. **Errors:** missing/malformed destination, app unavailable, rejected launch; copy fallback. **AC:** no launch → no handoff; launch → not sent; no automated taps/scrape/session. **Priority:** P0/P6.

### F-061 SMS composer/copy/share
**Purpose:** safe fallback. **Behavior:** channel capability/format/size check; launch composer/share/copy; event identifies exact action. **UI:** channel-specific warning and character segmentation. **Edge:** copied but clipboard unavailable; app selection/cancel; no destination. **AC:** copy/share record truthful action and never external sent/delivered. **Priority:** P1/P6.

### F-062 Explicit delivery confirmation
**Inputs:** handoff event and user answer yes/no/unknown. **Behavior:** only direct user command writes confirmation; unknown persists; revisions/times bind exact event. **Errors:** no valid event or already terminal; idempotent UI retry. **AC:** restart/resume/timer cannot confirm. **Priority:** P0/P6.

### F-063 Immutable delivery/history timeline
**Purpose:** understand what actually happened. **Data:** exact draft revision, channel, action, result, user confirmation, timestamps, failure reason class. **UI:** filters person/channel/outcome/date; empty; no PII in overview beyond needed. **Business:** history is read model, not second truth. **AC:** append-only order and Dashboard agree; no “delivered” absent proof. **Priority:** P1/P6.

### F-064 Gated Android automatic SMS
**Purpose:** approved Android product capability for a reviewed birthday occurrence. **Pre:** eligible distribution variant and platform policy, Android permission, explicit per-recipient opt-in, approval bound to the exact occurrence/message/destination/time, non-stale authorization, bounded retry. **Behavior:** durable job/OS schedule, revision token, idempotency key; edit/revoke/deletion cancels. **UI:** explicit risk/consent, pending/cancel/attempt state. **AC:** one send at most; stale/revoked cannot send; Play uses manual SMS handoff if policy/review does not permit automation. **Priority:** P2; implementation and activation remain release-gated.

---

## DOM-09 — Account and entitlement

### F-070 Google/account sign-in and session
**Purpose:** owner identity for optional cloud/account services. **Behavior:** real provider sign-in, token validation/refresh/revocation, secure storage, account displayed. **UI:** sign in/out, failure, account switch warning. **Privacy:** email/profile minimized. **Errors:** SHA/config, revoked, offline, cancellation. **AC:** no fake account; local data never silently reassigned; revocation disables cloud features. **Priority:** P1/P8.

### F-071 Sign-out and reauthentication
**Behavior:** purge session/secrets selected by policy, stop account operations, preserve local data unless explicitly deleted; reauth for sensitive action. **UI:** consequences and local/cloud scopes. **AC:** sign-out ≠ delete; no background cloud write. **Priority:** P1/P8.

### F-080 Purchase/restore subscription
**Pre:** account/product/environment. **Behavior:** store purchase stream → idempotent backend verification exact envelope → acknowledge per policy → expiring server-authoritative entitlement → refresh. **States:** pending/cancel/active/expired/revoked/grace/unknown/error. **Errors:** network/server/wrong package/account/product/replay. **AC:** invalid/unverified purchase cannot unlock; retry retains purchase safely; state copy matches one predicate. **Priority:** P0/P7.

### F-081 Entitlement cache and cold-start state
**Purpose:** stable offline startup without false grant. **Behavior:** store last verified evidence and expiry; show checking/stale; no use beyond approved bounded expiry; background verify. **UI:** distinguish active cached but not yet refreshed, unknown, expired. **AC:** expired snapshot never authorizes provider; verification failure has explicit policy. **Priority:** P0/P7.

### F-082 Entitlement refresh/revocation/account switch
**Behavior:** refresh at app start/sign-in/expiry/sensitive AI operation/restore; server/store source; invalidate old account snapshot atomically. **AC:** account switch cannot expose previous entitlement; mid-session expiry blocks the next request. **Priority:** P0/P7.

---

## DOM-10 — Continuity, privacy and lifecycle

### F-090 Backup opt-in and generation
**Pre:** account + data-category disclosure + consent. **Behavior:** serialize approved schema, hash/validate parts, upload owner scoped, atomically publish current generation; secure local receipt. **Excludes:** secrets/tokens, transient jobs unless approved. **UI:** progress/count/category, security truth, cancel; result. **AC:** no upload before opt-in; every published generation passes manifest; concurrent deletion fences. **Priority:** P1/P8.

### F-091 Restore and conflict resolution
**Pre:** owned verified generation. **Behavior:** full schema/hash/owner validation → conflict plan (version/tombstone) → all-or-nothing local transaction or explicit resumable; preserve reminder/device-local prefs per approved policy. **UI:** summary, newer-local warning, no destructive surprise. **Edge:** duplicate/orphan/malformed/stale/unknown. **AC:** corrupt/partial restores no mutation; deleted records not resurrected; exact counts. **Priority:** P1/P8.

### F-092 Export full local user data
**Behavior:** versioned archive and manifest; selected data categories; exclude secrets; include legacy warnings. **UI:** explain sensitivity, count and local file responsibility. **AC:** archive schema validated/round-trip and no secret/PII outside declared content. **Priority:** P1/P8.

### F-093 Privacy preferences and capability consent
**Purpose:** control contacts, AI, backup, reminders, diagnostics and optional automation. **Behavior:** explain effect, request/stop, revoke where possible; provider/OS permission actual state. **Data:** versioned local preference events. **AC:** UI cannot show enabled when OS/account is denied; revocation cancels pending work per capability. **Priority:** P0/P5/P7.

### F-094 Data/account deletion
**Pre:** explicit scope, recent auth for cloud/account. **Behavior:** enumerate local, secrets, backups/generations, logs/receipts/control records; accepted → processing → completion per scope; deletion fences block recreation; local cleanup may differ. **UI:** scope checklist, irreversible language, receipt/status, retry. **Errors:** partial scope, unavailable service, reauth. **AC:** accepted never shown completed; completion reconciles every scope/exception. **Priority:** P1/P8.

### F-095 Sign-out/account cleanup distinction
**Behavior:** clear account session, leave owner-owned local data as explained; account switch requires ownership decision to avoid mixing. **AC:** no accidental merge across users. **Priority:** P1/P8.

---

## DOM-11 — Automation and reliability

### F-096 Durable job queue
**Inputs:** approved typed operation and non-secret payload. **States:** queued/running/retry-wait/succeeded/failed/cancelled. **Behavior:** unique subject key; process stale running on restart; bounded exponential retry with jitter; idempotency/reference to durable result; cancellation pre-side-effect. **UI:** show only actionable operations, redacted error. **AC:** duplicate delivery/crash cannot duplicate user-visible effect. **Priority:** P1/P5–P9.

### F-097 Auto-prepare opt-in
**Pre:** Person explicit consent; active entitlement/provider rules if AI; reminder/lead-time policy. **Behavior:** enqueue bounded preparation; never handoff/send; draft remains reviewable; failure action-required. **UI:** per-Person setting, provider/pricing disclosure, cancel/revoke. **Edges:** expiry, changed fact, disabled AI, quota. **AC:** false defaults; true is explicit; no external send. **Priority:** P2/P9, product approval blocked.

### F-098 Cross-device/background reconciliation
**Purpose:** survive OS lifecycle. **Behavior:** reconcile jobs/reminders after boot/reboot/update/resume/timezone/permission; not cloud sync unless approved. **AC:** no duplicate or missed silent side effect; unsupported state explicit. **Priority:** P1/P5.

---

## DOM-12 — Operations, help and public surfaces

### F-100 Support diagnostics
**Purpose:** support without private data leakage. **Inputs:** selected issue and user consent. **Output:** redacted local report/operation ID. **UI:** preview, copy, submit supported channel. **Excludes:** names, contacts, facts, body, prompt, tokens. **AC:** report schema tested; offline fallback. **Priority:** P1/P10.

### F-101 Release capability/feature flags
**Purpose:** safely disable risky surfaces. **Behavior:** signed/runtime-safe flags for conditional providers/automation; defaults safe; no client flag grants entitlement. **Ops:** audit who changed, rollout/pause/rollback. **AC:** disabling route stops new effects and presents supported fallback. **Priority:** P1/P10.

### F-102 Public privacy/terms/support/deletion site
**Purpose:** public data-rights and help entry. **Dependencies:** legal/privacy approval, real identity/anti-abuse, hosting/domain/monitoring, deletion backend. **Behavior:** accessible pages and truthfully scoped deletion intake/status. **AC:** cannot access private content; accepted vs completed distinct; CSP and rate limits. **Priority:** P2/P10 conditional.

### F-103 Operational dashboard and alerting
**Purpose:** observe health without PII. **Metrics:** entitlement/provider/backup/reminder/deletion/release error/latency categories. **Permission:** restricted operator. **Behavior:** low-cardinality redacted events, alert thresholds after baselines, incident drill. **AC:** no raw user content; alerts map to runbook. **Priority:** P1/P10/P11.

### F-104 Billing/deployment reconciliation operations
**Purpose:** resolve charged-but-locked, failed deletion and stuck jobs safely. **Behavior:** authorized support workflow by opaque IDs/receipts, no private content, idempotent correction/audit. **AC:** correction cannot be performed through user-controlled field and is separately logged. **Priority:** P1/P11 conditional.

### F-109 Birthday Autopilot control plane (in-scope capability; separate service deployment)
**Purpose:** coordinate approved Android occurrence work through installation leases, claims, fencing, attempts, and deletion. **Pre:** mobile caller integration, consent/approval contracts, legal/store review, staging evidence, and explicit deployment approval. **Behavior:** strict schema, identity/App Check, fencing, replay/rate limits and deletion; never transport phone numbers or message content. **Boundary:** manual handoff remains distinct; existence of endpoints never proves execution or delivery. **AC:** isolated deployment and caller matrix; core local/manual workflows work when the service is absent. **Priority:** P2; implementation/deployment gated.

---

## Cross-cutting feature acceptance

- F-010–F-024 are P0 foundation; no downstream AI/automation may redefine their data/date authority.
- Every feature includes user-visible loading, empty, success and failure states; “not applicable” must be named rather than omitted.
- Every cloud/provider/OS feature is independently retryable/cancellable where safe and preserves local manual capability.
- Every P0 feature requires negative/boundary tests, a11y, privacy/security assessment, and traceability entry in [12 — Governance registers](12-governance-registers.md).
