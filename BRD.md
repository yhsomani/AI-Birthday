# WishWell (Birthday Autopilot) — Business Requirements Document

|                     |                                                                                                                 |
| ------------------- | --------------------------------------------------------------------------------------------------------------- |
| **Document**        | Business Requirements Document — v3.0 (Codebase-Grounded)                                                       |
| **Product**         | WishWell · `birthday-autopilot` v0.1.0                                                                          |
| **Source of truth** | Repository code, verified 2026-08-22. Documentation is supporting evidence. Companion: [`PRD.md`](PRD.md) v3.0. |

Labels — Status: ✅ Implemented · ◐ Partial · 📄 Documented-only · 🔮 Future · ❓ Unclear. Evidence: [VC] codebase · [VD] docs · [I] inferred · [A] assumption · [R] recommendation.

---

# 1. EXECUTIVE SUMMARY

WishWell is an **Android-first autonomous birthday-SMS system** whose defining business asset is a _verified trust architecture_: human approval of exact payloads, server-enforced single-send guarantees, honest delivery language, deletion-grade privacy, and a fail-closed release-admission chain. The codebase shows the product is far past concept: the full setup→approve→deliver pipeline, cloud control plane (16 callables + 2 scheduled self-healing sweeps, region asia-south1), sender transfer, deletion saga with receipts, bilingual EN/HI UX, accessibility E2E, and an Ed25519-signed distribution-evidence regime are implemented. Principal business gaps are **go-to-market absentia** (no analytics telemetry, no store listing evidence yet, no support model), a half-built iOS-companion protocol, and documentation debt that misstates several implemented behaviors. This BRD converts the verified state into business requirements (BR-01…), processes, rules, KPIs, and a prioritized improvement plan.

# 2. BUSINESS PROBLEM & OPPORTUNITY

**Problem:** forgotten birthdays damage relationships; incumbent reminder tools notify too late or send robotic text; AI tools face trust deficits; contact-scraping apps poison permission consent; Android background fragility and iOS overpromising create review-killing failures; Google restricted-scope compliance filters out casual competitors. **[VD]**

**Opportunity:** own the trust vacuum. Verified structural advantages in code: zero per-message COGS (user's SIM pays carrier), duplicate sends made _structurally impossible_ (occurrenceKeys/destinationGuards + asserted-zero scenario evidence), no PII ever stored server-side (HMAC aliases), no device refresh tokens, and a release chain that cannot ship unapproved SMS capability. These are expensive for followers to replicate because they deliberately cap growth hacks. **[VC]**

# 3. BUSINESS OBJECTIVES

| ID   | Objective                                                                     | Success measure                                                                    | Status                                          |
| ---- | ----------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- | ----------------------------------------------- |
| BO-1 | Ship Play-distributed Android Automation Edition passing every admission gate | Signed approval (9 mandatory booleans) valid; store submission package complete    | ◐ gates built ✅, submission pending ❓         |
| BO-2 | Reliability promise: approved wishes deliver in-window, exactly once          | Send success ≥98%; duplicate submissions = 0 (already schema-asserted); missed <2% | Pipeline ✅; field metrics pending telemetry 📄 |
| BO-3 | Trust loop validated                                                          | Approval rate ≥60%; automation stays on ≥95% post first send                       | Requires analytics ([R])                        |
| BO-4 | Compliance-clean operation                                                    | contacts.readonly verification passed; zero policy strikes; deletion SLA met       | Engineering ✅; Google verification status ❓   |
| BO-5 | First-session activation ≥70%, median <10 min                                 | Funnel instrumentation (currently absent → [R])                                    | Blocked by F-48 gap                             |
| BO-6 | Self-serve resolution ≥90% of failures                                        | Ticket rate <10/1000 MAU                                                           | Diagnostics ✅; tickets channel ❓              |
| BO-7 | Honest platform expansion (iOS companion) when Android SLOs hold 2 quarters   | Reservation callables + app shipped; composer vocabulary enforced                  | ◐ protocol scaffolds exist                      |
| BO-8 | Keep core sending free forever; premium only additive                         | Zero paywalled core features                                                       | Policy ✅ (no monetization code exists)         |
| BO-9 | Efficient organic growth to ≥10k installs /6 mo                               | Console install sources; CAC≈0 assumption                                          | 🔮 GTM undefined ❓                             |

# 4. STAKEHOLDERS [VC-informed]

| Stakeholder                                                                   | Interest                   | Authority (evidenced in repo)                                                                                                        |
| ----------------------------------------------------------------------------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| Product owner / founder                                                       | Vision, brand, launch      | Scope, store identity (OQ-01)                                                                                                        |
| Release authority (Ed25519 key holder, pin `distribution-authority-pin.json`) | Gatekeeping SMS capability | Signs/denies distribution approvals; expiry controls builds — effectively a launch veto                                              |
| Engineering                                                                   | Feasibility, supply chain  | Architecture, gate tooling                                                                                                           |
| QA / release council                                                          | Ship safety                | Store-submission & closure validators; CI aggregate job `release-admission`                                                          |
| Privacy/Legal                                                                 | Regulatory exposure        | privacy/terms/support page content approvals (`release-config.example.json` requires legal references); Google verification sign-off |
| Operations/Support                                                            | Ticket load, runbooks      | docs/OPERATIONS_RUNBOOK.md ownership                                                                                                 |
| Platform gatekeepers (Google Play / Android / Firebase)                       | Policy compliance          | Can suspend APIs/listing                                                                                                             |
| End users                                                                     | Reliability, privacy       | Churn/reviews (unmeasured today — no telemetry)                                                                                      |

# 5. CURRENT STATE (VERIFIED SNAPSHOT)

Implemented end-to-end ✅: onboarding with eligibility screening; read-only People sync with freshness bands; enrollment with issue taxonomy (14 codes); templates+Gemini drafting under semantic policy v2; approvals with 12-class invalidation; batch approval; policy editor (window/late/caps/sim/birthday-confirm) with 400-day preview; readiness gates; test mode (3/day); claim-arm-submit-observe delivery (≤2 segments, default-SIM binding, one safe retry, 5-min clock tolerance, 400-day horizon, budgets 20/day); sender lease/fence/transfer with drain+mandatory test; attention center; activity ledger (24 kinds); scrubbed diagnostics; notifications w/ dedupe+tap routing; privacy operations ×8 with two-phase confirmation; deletion saga+tombstones+sweeps+receipts; web tier (/, /delete/, /privacy/, /terms/, /support/) bilingual; SQLCipher local store (37 entities); deny-all Firestore; App Check everywhere; EN/HI localization; large-text E2E; ~76 native test suites incl. property tests; 20+ enforcement gates.

Not present ❌: any analytics/crash SDK (grep-verified), FCM, battery-exemption request, SIM picker, next-morning late policy, deep links, iOS build, monetization, manual web-deletion fallback.

Documentation debt: README iOS sections stale; PROJECT_ABOUT misstates ≥8 behaviors (see PRD §15); Flow.md tones wrong; decision.md palette matches theme.ts ✅.

# 6. BUSINESS REQUIREMENTS

| ID    | Requirement                                                                        | Rationale                                   | Priority | Stakeholder | Dependencies                                         | Expected outcome / Success criteria                                                                                | Status                          |
| ----- | ---------------------------------------------------------------------------------- | ------------------------------------------- | -------- | ----------- | ---------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ | ------------------------------- |
| BR-01 | No message without prior approval of exact content, invalidated on material change | Trust core; abuse shield                    | P0       | All         | F-12/13                                              | 100% sends trace to valid snapshot; 12 invalidation classes tested                                                 | ✅                              |
| BR-02 | Structural single-send per occurrence                                              | Double-wish destroys brand                  | P0       | Product     | F-19/22                                              | Duplicate submissions asserted 0 in evidence; guards block races                                                   | ✅                              |
| BR-03 | Delivery truthfulness                                                              | Review protection                           | P0       | UX/Legal    | F-22                                                 | No UI/evidence claims carrier delivery without proof (`carrierDeliveryClaimed:false` const)                        | ✅                              |
| BR-04 | Restricted SMS capability only via signed, unexpired approval                      | Policy/legal containment                    | P0       | Legal/QA    | F-41                                                 | Unapproved builds report RESTRICTED_SMS_CAPABLE=false and block all gates                                          | ✅                              |
| BR-05 | Complete data-deletion path reachable from web without app                         | Google policy; user rights                  | P0       | Legal       | F-34/36                                              | Receipt issued; Auth deletion verified by sweep; SLA number finalized (OQ-07)                                      | ✅ eng / ❓ SLA copy            |
| BR-06 | Minimal data footprint end-to-end                                                  | Privacy moat; breach-cost ceiling           | P0       | Legal       | §2.4/2.5                                             | No raw PII server-side; no device refresh tokens; backups disabled                                                 | ✅                              |
| BR-07 | Fail-closed release admission                                                      | Bad release = broken promises at scale      | P0       | QA          | Admission chain (BRD §7 P-6); gate scripts in tools/ | Any missing/stale evidence aborts assembly/deploy                                                                  | ✅                              |
| BR-08 | Activation funnel measurable                                                       | Retention economics need leading indicators | P0       | Product     | Analytics [R]                                        | Step-level funnel dashboard live pre-launch marketing                                                              | 📄→[R]                          |
| BR-09 | Self-serve repair for top failure classes                                          | Support-cost control                        | P1       | Ops         | F-26/28                                              | ≥90% recoverable in-app; ticket rate target set once channel exists                                                | ✅ capability / ❓ channel      |
| BR-10 | Bilingual launch surface EN/HI                                                     | Market reach; store locales                 | P1       | Product     | F-38/39                                              | hi-IN evidence pack approved; E2E Hindi assertions green                                                           | ✅                              |
| BR-11 | Accessibility conformance as launch blocker                                        | Legal + reach                               | P1       | UX          | F-39                                                 | Accessibility matrix evidence (en+hi, TalkBack, 200%, dark, contrast, reduced-motion, bidi, alt-text) all approved | ✅ tooling / ◐ evidence pending |
| BR-12 | Physical-device performance budgets                                                | OEM reality check                           | P1       | Eng         | perf validator                                       | Budgets pass ≤30-day-fresh physical evidence                                                                       | ✅ tooling                      |
| BR-13 | Free core forever                                                                  | Brand promise                               | P0       | Product     | —                                                    | Zero gating shipped each release                                                                                   | ✅ policy                       |
| BR-14 | iOS companion only after Android stability window                                  | Risk sequencing                             | P2       | Product     | BO-7                                                 | Entry criteria documented & honored                                                                                | 🔮                              |
| BR-15 | Documentation parity with code                                                     | Contributor/support accuracy                | P1       | PM          | Gap log PRD §15                                      | README/PROJECT_ABOUT updated; drift test or checklist adopted                                                      | 📄 debt open                    |
| BR-16 | Support operating model defined pre-launch                                         | BO-6/BO-9 dependency                        | P0       | Ops         | OQ-06                                                | Channel, SLA, staffing memo                                                                                        | ❓                              |

# 7. BUSINESS PROCESSES

**P-1 Setup & activation:** eligibility screen → Google bind (+App Check) → incremental readonly consent → staged sync → enrollment (issues resolved inline) → message/policy (batch approve) → test (≤3/day) → activation-review → activate. Deferred states persist.
**P-2 Birthday delivery:** nightly/periodic reconcile → claims (server keys/guards/budgets) → arm spacing → submit barrier → SmsManager → callback observation → outcome classification → ledger + optional attention notification → successor scheduling on failure (30 s floor; hourly for reservation class).
**P-3 Repair:** attention issues → plain-language reason → action handle (settings/reauth/choices) → recheck clears; diagnostics export for escalation.
**P-4 Sender transfer:** register STANDBY → prepare/begin → remote drain fence → complete (epoch bump) → forced test → reactivate.
**P-5 Deletion saga:** request (in-app two-phase or web reCAPTCHA+reauth) → tombstone DRAINING fenced → local wipe → remote drain → sweep verifies Auth deletion → content-free receipt (365 d retention).
**P-6 Release admission:** portable checks → secret/history scan → SBOM/advisory (zero exceptions) → bundle boundaries → physical/carrier/accessibility/performance evidence (≤30 d) → Ed25519 distribution approval consumed into BuildConfig → store submission package (12 evidence IDs, en-US/hi-IN, 8 roles, truthful-SMS booleans) → signed store-release check → production closure (clean HEAD, ≤30 d, Play observation ≤24 h).
**P-7 Cloud ops:** scheduled sweeps self-heal deletion drains and interrupted reset/release sagas; HMAC key rotation window (current+previous).

# 8. BUSINESS RULES (BUSINESS-FACING VIEW OF VERIFIED CONSTANTS)

Full technical table lives in PRD §8. Business-material rules: segment ceiling 2; daily budgets 20 birthday/3 test arms (UTC); arm spacing 5 min; submit window 60 s; lease 10 min; one authorized retry; clock tolerance 5 min; default-SIM-only sending; leap-day choice mandatory (feb-28/mar-01/**skip**); late policy none|same-day-grace; freshness bands 7 d warn / 30 d pause; planning horizon 400 d; installer allowlist + signed distribution approval with API bounds 29–37 and expiry; receipts content-free retained 365 d; standby installs expire 90 d; revoked 30 d; coordination receipts 30 d; birthday claim retention 400 d; tests 30 d.

# 9. COMPLIANCE POSTURE (VERIFIED IMPLEMENTATIONS)

- **Google Play SMS policy:** SEND_SMS/READ_PHONE_STATE declared only in prod/lab manifests; runtime JIT requests with permanent-denial handling; restricted-distribution enforced via signed evidence → BuildConfig; smoke/e2e flavors physically strip permissions; boundary verifier asserts merged-manifest correctness. ✅
- **Google API Services (restricted scope):** contacts.readonly only, incremental, no offline/server codes; web deletion resource live; privacy/terms/support hosted bilingual; least-privilege personFields. Verification filing status ❓.
- **Data protection:** SQLCipher+Keystore-wrapped keys; allowBackup=false + extraction rules; FLAG_SECURE backgrounded; no PII server-side (privacy-architecture tests); HMAC alias derivation with rotation; App Check limited-use tokens; deny-all rules; web CSP locking; sessionStorage-only receipts. ✅
- **Abuse prevention:** budgets, pacing, occurrence dedup, destination blocking, reset-safety replay protection, clock-trust, installer allowlist. ✅
- **Accessibility:** WCAG-aligned primitives, large-text E2E, high-contrast themes, bidi fixture; store evidence demands approved matrices. ✅ tooling.
- Formal certifications (GDPR/DPDP etc.): not claimed anywhere; jurisdictional counsel pending OQ-04/OQ-05.

# 10. FINANCIAL CONSIDERATIONS

Verified cost structure [VC]: no SMS gateway spend (device SIM pays carriers); Firebase functions minInstances 0/max 20/concurrency 20 (scale-to-zero); Firestore zero composite indexes (index cost avoided); Gemini billed via device-side `firebase-ai` usage (client-only — no server-side Gemini/firebase-ai; rate-guarded, template fallback bounds spend); reCAPTCHA Enterprise (deletion traffic only); signing/CI infrastructure fixed. Revenue: none implemented; free-core policy BR-13. Optionality 🔮: premium AI tiers, occasions, channels (would introduce true COGS), family plans — all require product decisions post-traction. ROI logic: trust-capital accumulation + word-of-mouth in a churn-prone category; dominant financial risks are compliance failure and unrepaired reliability gaps, both structurally mitigated in code. **[A]** growth assumed organic until BO-9 funded.

# 11. KPIs & SUCCESS METRICS

**Existing (measurable today from artifacts):** duplicate-submission count (=0 asserted), carrier-delivery claims (=false const), physical-matrix pass rows, performance-budget deltas (incl. battery delta/hour, ANR counts), advisory-gate findings (zero-exception policy), deletion-sweep completion, receipt lookups, wake-ledger continuity, gate failure rates in CI. **[VC]**

**Recommended (blocked on telemetry or GTM):** activation funnel %/step; median time-to-activate; approval & edit-depth rates; invalidation-reason mix; gate-block reason frequency; transfer completion & time; deletion SLA attainment; D7/D30 retention; stay-on-after-first-send; ticket rate; store rating trajectory; install sources. Interim no-SDK path: opt-in scrubbed diagnostic counters + Play Console/Vitals exports. **[R]** North Star proposal: **Weekly Confirmed Wishes** (in-window delivered occurrences). Each recommended metric needs a privacy design review before vendor onboarding.

# 12. CONSTRAINTS & DEPENDENCIES

Platform laws (no programmatic iOS SMS; Play restricted distribution; Google scopes verification); sends must originate from user hardware/SIM (by design, also cost structure); pinned toolchain (Node 24.18.0/JDK 21/NDK 27/Maestro 2.6.1/Firebase CLI 15.23.0); release authority key custody; Gemini availability/terms (client kill-switch exists); People API quotas; carrier filtering norms per country; region-pinned callables (asia-south1) — latency/residency implications for other markets.

# 13. ASSUMPTIONS & UNKNOWNS

[A] Organic growth sufficient initially; [A] users grant readonly contacts after honest disclosure; [U] Google verification timeline; [U] store submission date/approver identities; [U] deletion-SLA public number; [U] crash-reporting decision; [U] dev/staging source-set intent; [U] Settings theme persistence; [U] whether PROJECT_ABOUT will be updated or superseded by this pair (recommend update, BR-15).

# 14. RISKS & MITIGATION

| Risk                                            | Sev      | Mitigation (existing/planned)                                                        |
| ----------------------------------------------- | -------- | ------------------------------------------------------------------------------------ |
| OEM killers delay sends despite wake ledger     | High     | Diagnostics codes shipped ✅; battery-exemption UX decision open (F-45)              |
| runBlocking ANRs (bridge payloads)              | Med-High | Code fix queued [R]; ANR budget already in perf evidence                             |
| No field telemetry blinds post-launch diagnosis | High     | Interim: Play Vitals + opt-in scrubbed counters [R]; then minimal SDK w/ review      |
| iOS protocol half-build confuses roadmap        | Med      | Explicit Phase-3 gate BO-7; remove dead client whitelist entry or implement          |
| Docs drift misleads contributors/support        | Med      | BR-15 parity pass; adopt doc-drift checklist                                         |
| Signing-authority key loss/compromise           | High     | Out-of-band custody process ❓; pin rotation procedure needed                        |
| Carrier filtering in new markets                | Med      | Budgets/pacing ✅; per-country matrix evidence required before launch (schema ready) |
| Gemini terms shift                              | Low-Med  | policy-suspended fallback + operational gate ✅; contract watch                      |
| Web tier misconfiguration                       | Low      | Fail-closed runtime-config gate ✅                                                   |

# 15. SCOPE

**In (current product):** everything in PRD §17 "Now" — Android Automation Edition, web tier, admission machinery, EN/HI.
**Out:** iOS build today; auto-send off-Android; contact writes; multi-account; email/calling/social; bulk/marketing; replies management; monetization of core; manual non-Google deletion.
**Future 🔮:** iOS companion completion; occasions beyond birthdays; alternate channels; shared plans; annual digest; referrals; additive premium.

# 16. CODEBASE-TO-REQUIREMENT TRACEABILITY

Chain: **BR → Feature (PRD §5) → User Story (PRD §13) → Primary code modules → Journey (PRD §12) → Acceptance criteria → KPI**

| BR       | Features      | US       | Code modules (primary)                                                                                                                       | Journey | AC anchor           | Metric                 |
| -------- | ------------- | -------- | -------------------------------------------------------------------------------------------------------------------------------------------- | ------- | ------------------- | ---------------------- |
| BR-01    | F-09…13       | US-03/04 | domain/approvals/model.ts; AutomationPort.prepare/confirmApprovals; LiveBatchApprovalScreen.tsx; LiveMessageScreen.tsx                       | J-1     | US-04 AC; PRD §6.5  | Approval rate [R]      |
| BR-02    | F-18/19/21/22 | US-05/07 | AndroidAutomationOrchestrator.kt; backend services/controlPlane.ts; SubscriptionBindingPolicy.kt; CoordinationContracts.kt                   | J-2     | §6.8 ACs; US-05 AC  | Duplicates=0 ✅        |
| BR-03    | F-22          | US-06    | SmsOutcomeNetworkProcessor.kt; mobile-release-scenario-evidence.schema.json                                                                  | J-2     | truthful-copy rules | Rating [R]             |
| BR-04/07 | F-41          | —        | android/app/build.gradle flavor blocks; validate-distribution-evidence.mjs; check-store-release.mjs; validate-production-release-closure.mjs | P-6     | gate table          | Gate pass rate         |
| BR-05/06 | F-31…36/43    | US-02/08 | LivePrivacyScreen.tsx; PRIVACY_ACTION_KINDS; backend deletionOrchestrator.ts; hosting/src/\*; EncryptedDatabaseFactory.kt                    | J-5     | US-08 AC; §6.10     | Deletion SLA           |
| BR-08    | F-48[R]       | —        | (absent — to build)                                                                                                                          | J-1     | —                   | Funnel % [R]           |
| BR-09    | F-26/28       | —        | LiveAttentionScreen.tsx; LiveDiagnosticsScreen.tsx; WorkerAttentionPolicy.kt                                                                 | J-3     | §6.11               | Recovery ≥90%          |
| BR-10    | F-38          | US-09    | localization/liveResources.ts; e2e/maestro/03-hindi-localization.yaml                                                                        | J-1     | US-09 AC            | hi adoption [R]        |
| BR-11    | F-39          | US-10    | design-system/\*; e2e 04-large-text-primary-action.yaml                                                                                      | J-1     | US-10 AC            | A11y matrix ✅/pending |
| BR-14    | F-50          | —        | core/model/DeliveryPlatform.kt; IOSComposerReservationRecheckPolicy; ttl iosComposerReservations                                             | —       | §6.12               | Phase-3 gate           |
| BR-16    | —             | —        | docs/OPERATIONS_RUNBOOK.md (exists)                                                                                                          | P-3     | —                   | Tickets [R]            |

Untraceable-by-design: BO-9 GTM (no code surface), branding (OQ-01), legal SLA number (OQ-07).

# 17. IMPROVEMENT ANALYSIS (CURRENT → RECOMMENDED)

| #   | Current state (verified)                                  | Recommended future state                                                                                | Why                                 | Impact |
| --- | --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- | ----------------------------------- | ------ |
| 1   | Zero product telemetry                                    | Local privacy-preserving counters → reviewed minimal SDK; North Star Weekly Confirmed Wishes            | Flying blind on BO-3/5/9            | High   |
| 2   | runBlocking bridge payloads                               | Async dispatch/worker offload                                                                           | ANR risk on low-end devices         | High   |
| 3   | Battery exemption absent; diagnose-only                   | Decide: guidance-first exemption request OR explicit non-goal with expectation copy                     | Missed sends on aggressive OEMs     | High   |
| 4   | Stale docs (README iOS, PROJECT_ABOUT drift, Flow tones)  | Parity pass + drift checklist; mark PROJECT_ABOUT sections superseded                                   | Contributor/support accuracy        | Med    |
| 5   | Dead iOS surfaces (companionStatus whitelist, orphan TTL) | Implement reservation callables in Phase 3 or prune now                                                 | Hygiene; avoids false confidence    | Med    |
| 6   | Deletion SLA number undefined publicly                    | Finalize SLA copy consistent with drain windows                                                         | Compliance/marketing honesty        | Med    |
| 7   | Notification quiet-hours unspecified                      | Adopt v2 rule (suppress non-critical 21:30–07:30 local; never delay failures)                           | User respect; fewer disables        | Med    |
| 8   | Theme persistence unclear                                 | Persist preference durably                                                                              | Small UX polish                     | Low    |
| 9   | Support model undefined                                   | Channel+SLA memo before store listing                                                                   | BO-6/BR-16 dependency               | High   |
| 10  | Single authorized retry                                   | Keep (deliberate anti-spam tradeoff) but add user-initiated "send again" repair affordance within grace | Recover flaky-radio misses honestly | Med    |

Each row preserves current-state documentation untouched in PRD §5/§15; recommendations are separable workstreams.

# 18. FINAL QUALITY CONTROL

Cross-check performed against prompt checklist: all live routes/screens documented (§4/§10 PRD) ✅ · roles/permissions §3 ✅ · journeys §12 ✅ · business rules §8/§10 ✅ · validations (FR-06, issue taxonomies) ✅ · edge cases §14/PRD §16 ✅ · integrations §11 ✅ · data flows §2/§11 ✅ · states (projection/loading/error/empty/success) §10 ✅ · statuses labeled per item ✅ · PRD↔BRD consistency reviewed (single source constants in PRD §8; BRD references, never restates conflicting values) ✅ · IDs consistent (F-/FR-/US-/AC-/BR-/BO-) ✅ · current vs recommended separated (§17; [R] labels) ✅ · no unsupported facts (labels applied; unknowns listed) ✅.

Residual unknowns intentionally not invented: OQ-01…OQ-10 (PRD §18).

---

_This pair (PRD.md v3.0 + BRD.md v3.0) is the authoritative reference; where legacy documents conflict, these win per the gap log._
