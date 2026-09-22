# WishWell (Birthday Autopilot) — Single Source of Truth (SSOT)

|                     |                                                                                                                                   |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| **Document**        | Single Source of Truth — v1.0 (Consolidated from PRD.md v3.0, BRD.md v3.0, PROJECT_ABOUT.md, and codebase verification)           |
| **Product**         | WishWell · package `birthday-autopilot` v0.1.0 · appId `com.yashsomani.birthdayautopilot`                                         |
| **Source of truth** | This document is the authoritative reference for the entire project. All other documentation files are subordinate or historical. |
| **Status**          | Consolidated 2026-08-29. Supersedes PROJECT_ABOUT.md, PRD.md, BRD.md, Flow.md, decision.md, DESIGN.md where conflicts exist.      |
| **Next Review**     | Before any major feature addition, platform expansion, or store submission                                                        |

---

## Status & Evidence Labels

Every feature/requirement carries one **Implementation Status** and one **Evidence** label:

### Implementation Status

| Status | Meaning                                                                                  |
| ------ | ---------------------------------------------------------------------------------------- |
| ✅     | Implemented — fully implemented and working                                              |
| ◐      | Partially implemented — incomplete or has gaps                                           |
| 📄     | Documented but NOT implemented                                                           |
| 🆕     | Implemented but missing from prior documentation                                         |
| 🔮     | Planned / future                                                                         |
| ❓     | Unclear — requires confirmation                                                          |
| ⚠️     | **NOT_RUNTIME_VERIFIED** — code exists but not verified in runtime environment           |
| 🚫     | **NOT_DEPLOYED** — implementation complete but not deployed to production infrastructure |

### Verification States (for tracking progress)

Features should progress through these verification states before being considered production-ready:

| State                  | Meaning                          |
| ---------------------- | -------------------------------- |
| `PLANNED`              | Requirement exists               |
| `IMPLEMENTED`          | Code exists                      |
| `UNIT_VERIFIED`        | Unit tests prove it              |
| `INTEGRATION_VERIFIED` | Component integration proven     |
| `E2E_VERIFIED`         | Full workflow proven             |
| `PRODUCTION_VERIFIED`  | Real deployed environment proven |
| `BLOCKED`              | External dependency              |
| `PARTIAL`              | Some required behavior missing   |
| `NOT_IMPLEMENTED`      | No functional implementation     |

**Evidence:** **[VC]** verified from codebase (file cited) · **[VD]** verified from documentation · **[I]** inferred · **[A]** assumption · **[R]** recommendation · **[U]** unknown · **[RV]** runtime verified · **[PV]** production verified.

---

# 1. EXECUTIVE SUMMARY

WishWell is an **Android-first autonomous birthday-SMS system** whose defining business asset is a _verified trust architecture_: human approval of exact payloads, server-enforced single-send guarantees, honest delivery language, deletion-grade privacy, and a fail-closed release-admission chain.

**Current State:** Code-complete for Android launch, pending runtime verification, infrastructure deployment, and production validation. The full setup→approve→deliver pipeline, cloud control plane (16 callables + 2 scheduled sweeps, region asia-south1), sender transfer, deletion saga with receipts, bilingual EN/HI UX, accessibility E2E, Ed25519-signed distribution-evidence regime, and **native Android Gemini AI integration** are all **implemented** but **NOT_RUNTIME_VERIFIED** and **NOT_DEPLOYED**. Dead JavaScript AI code (1,164 lines: AIGateway.ts, GoogleAIProviderAdapter.ts, AIProviderPort.ts) removed 2026-08-29. AI architecture cleaned up: native-only, no backend AI gateway.

**Principal Gaps:**

- No analytics telemetry (deliberate privacy choice, but blocks funnel measurement)
- iOS companion protocol half-built (server callables absent)
- Documentation debt in legacy files (README, PROJECT_ABOUT misstate behaviors)
- Support model undefined pre-launch
- Battery-optimization exemption request flow not implemented (diagnose-only)
- **AI runtime verification pending** — native Gemini integration not tested on real devices
- **AI expansion roadmap defined** — 4 features planned: auto-generate 3 variations, AI status indicator, smart contact enrollment, approval prioritization

---

# 2. PRODUCT OVERVIEW

## 2.1 Purpose [VC]

"Autonomous birthday SMS automation system for Android devices" (package.json). WishWell syncs Google Contacts birthdays, lets the user enroll people and approve exact message payloads, then delivers each approved wish as a real SIM-originated SMS on the birthday via a server-coordinated claim → arm → submit → observe pipeline — unattended on Android, with protocol-level provisions for a future iOS companion that requires the user to tap Send.

## 2.2 Vision [VD]

The most trusted way to maintain relationships through timely, personal birthday messages — AI assists, the human decides.

## 2.3 Mission [VD]

Make thoughtful birthday communication effortless without feeling automated.

## 2.4 Value Proposition

> Set it once. Approve what matters. Never miss a birthday.

**Verified differentiators:**

- Human approval of exact payload before any send (✅ enforced server+client)
- Structural duplicate-send prevention via server-issued occurrence keys and destination guards (✅)
- Truthful outcome copy ("Sent from this phone; delivery not confirmed") (✅)
- Deletion-grade privacy incl. SQLCipher local DB, deny-all Firestore, opaque HMAC aliases, content-free deletion receipts (✅)
- Bilingual EN/HI (✅)

## 2.5 Target Users

**Primary:** Busy professionals (28–45) who want set-and-forget reliability.

**Secondary:** Relationship curators wanting control, privacy-conscious users, less-technical users.

## 2.6 Product Scope (as implemented)

**In scope ✅:**

- Android Automation Edition (flavors e2e/smoke/dev/staging/lab/prod)
- Google sign-in + read-only contacts
- Enrollment & approvals
- Template + Gemini drafting
- Policy editor
- Test mode
- Server-coordinated unattended SMS
- Sender transfer
- Attention/repair
- Activity log
- Diagnostics export
- Privacy operations incl. full deletion
- Public web tier (/ , /delete/, /privacy/, /terms/, /support/) bilingual EN/HI

**Out of scope ✅:**

- iOS app build (removed; commit `61882f9`, workflows deleted `2b3a3b4`) though client-side companion _protocol_ remains (§7.12)
- Contact writes
- Multi-account
- Email/calling/social
- Bulk/marketing messaging
- Monetization

---

# 3. SYSTEM ARCHITECTURE (VERIFIED)

## 3.1 Layered TS architecture [VC]

```
src/domain/        pure models, branded IDs, enums, validators (no IO)
src/application/   11 role ports aggregated as BirthdayNativePort; PROJECTION_AREAS
src/features/live/ production screens driven by native projections
src/features/{setup,home,people,activity,settings}/  fixture-only preview stack (__DEV__)
src/infrastructure/native/  BirthdayNativeAdapter — single bridge implementation
src/design-system/ tokens/theme.ts + accessible primitives
src/localization/  i18next; EN/HI release; ar-XB pseudo-RTL dev fixture
```

**State management:** projection hooks + invalidation events, no Redux/Zustand. Every read returns `NativeResult<ProjectionEnvelope{contractVersion:1, revision, generatedAt, value}>`; every mutation passes `expectedRevision` (optimistic concurrency). Native pushes invalidations `{revision, areas[]}`; screens reload intersecting areas on foreground too (`useLiveProjection.ts`).

## 3.2 JS↔Native contract [VC]

Single TurboModule `specs/native/NativeBirthday.ts`:

- `getProjection(area, requestJson)` — 13 areas: bootstrap, setup, home, eligibility, readiness, account, contacts, messages, automation, activity, privacy, route, notifications.
- `executeUserIntent(intent, expectedRevision|null, payloadJson)` — ~40 named intents (e.g., `activate`, `authorize-contacts`, `confirm-privacy-action`, `begin-sender-transfer`, `repair-lifecycle-state`, `clear-activity`, `generate-suggestions`).
- Event emitter pair for invalidations/routes. Envelope ≤ 1 MiB; double Zod validation; decode failure collapses to `internal{NATIVE_CONTRACT_INVALID}` (`decodeNativeResponse.ts`).

**JS exposes no send/schedule/retry APIs — delivery authority lives natively.** [VC]

## 3.3 Android native engine [VC]

- Hand-wired DI (`AppGraph.kt`), Fabric `MainActivity`, WorkManager eager init with custom factory; startup coordinator guards one-time scheduling.
- **Orchestrator** (`AndroidAutomationOrchestrator.kt`, 1,850 ln): register installation → renew lease → claim occurrence/test → arm (≥5 min spacing) → barrier → submit via `SmsGateway` → observe callback PendingIntents → report. Global mutex; **400-day planning horizon**; **5-min clock tolerance**; **15-min sent watchdog**; distribution channel derived from `BuildConfig.APP_ENV` + `APPROVED_DISTRIBUTION_CHANNEL`.
- **Persistence**: Room, **37 entities**, SQLCipher (passphrase wrapped by hardware-Keystore AES-GCM key stored in `noBackupFilesDir` via AtomicFile; fail-closed codes `keystore-key-missing`/`wrapped-key-missing`); schemas 1–5 exported, auto-migrations.
- **Workers**: 15-min periodic `ReconcileWorker` (+ heartbeat lease, 30 s successor floor), `PeopleSyncWorker` (≤3 attempts), `DataRetentionWorker`, SMS outcome workers. `AutomationReconcileReceiver` maps BOOT_COMPLETED / TIME(\_ZONE)\_CHANGED / DATE_CHANGED / MY_PACKAGE_REPLACED / LOCALE_CHANGED / DEFAULT_SMS_SUBSCRIPTION_CHANGED → reconcile triggers.
- **SMS boundary**: `SmsPlatformSubmitter` accepts single or ≤ **2-part** multipart plans only (rejects cardinality mismatch/empty parts/joined-text drift); `SubscriptionBindingPolicy` allows sending **only** on the plan kind `SYSTEM_DEFAULT` subscription that equals the current default-SMS subscription and is active (dual-SIM fail-closed; changes trigger fingerprint-recorded reconcile).
- **Retry**: exactly **one** server-authorized retry (`authorizeSafeRetry`: attempt==2, RETRY_CLAIMED, identical claim id/epoch/reset-generation/retryRequestId/window; server clock within tolerance). Retry permit bounded inside immutable approved window.
- **Identity**: Credential Manager Google sign-in; **incremental OAuth `contacts.readonly` only**, explicitly rejects server auth codes/offline access (no refresh token ever on device); Firebase session + Play-Integrity App Check required; JIT sequential READ_PHONE_STATE → SEND_SMS requests with permanent-denial classification.
- **Gemini**: device-only via `firebase-ai` 17.13.0 (`AndroidGeminiSuggestionGateway.kt`): operational gate, rate guard (max 8 retained rate scopes), provenance registry, 15 s timeout, provider text never logged/persisted, **never reachable from a send worker**.
- **Attention notifications**: severity-classified channels, per-category/day dedupe store, single-use UUID tap identities (fsynced ring ≤16) feeding native route events (automation-review / attention).
- Privacy hardening: FLAG_SECURE while backgrounded; recents screenshot disabled API 33+.

## 3.4 Cloud control plane [VC]

Firebase project region **`asia-south1`**; Firestore rules **deny-all** — clients touch data only through **16 callable functions** (App Check enforced + consumed, 30 s timeout, maxInstances 20, concurrency 20): registerAndroidInstallation, renewSenderLease, changeAccountMode, claimOccurrence†, claimTest†, armAttempt, getArmStatus, reportTestOutcome, authorizeSafeRetry, beginSenderTransfer, completeSenderTransfer, requestAccountDeletion, accountDeletionReceipt, resetContactDerivedState, releaseAndroidSender, coordinationLifecycleStatus († require Cloud KMS secret `COORDINATION_HMAC_KEYRING`). Plus 2 scheduled sweeps: `sweepDeletionDrains`, `sweepCoordinationOperations`. Identity aliases are HMAC-SHA256 derived under domain `birthday-autopilot/control-plane/v1` with current+previous key rotation. **No raw contact/message fields ever reach the server** (privacy-architecture tests enforce). Gemini is absent server-side.

**Server-side declared-but-absent:** `companionStatus` (whitelisted in `FirebaseCoordinationClient.kt` L29 but unimplemented), `acquireIOSComposerReservation` / `commit…` / `release…` (docs + TTL collection `iosComposerReservations` exist; zero implementation). ◐ iOS-companion protocol is half-built.

## 3.5 Public web tier [VC]

Vite multi-page static site: `/`, `/delete/`, `/privacy/`, `/terms/`, `/support/`, 404 — bilingual EN/HI. Deletion flow: reCAPTCHA Enterprise App Check → in-memory Firebase Auth persistence → `reauthenticateWithPopup` → callable `requestAccountDeletion {contractVersion:1, requestId:<uuid>}` → receipt id held in **tab sessionStorage only**. Static tests forbid localStorage/indexedDB/cookies/console/innerHTML; strict CSP; fails closed without `public/runtime-config.json`.

---

# 4. USER ROLES & PERMISSIONS [VC]

The app has no multi-user roles; roles derive from **device/installation state** (server-enforced `InstallationState`: ACTIVE / STANDBY / REVOKED):

| Role                                | Who                                                     | Capabilities                                                                                                 | Restrictions                                                                   |
| ----------------------------------- | ------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------ |
| **Owner on ACTIVE (sender) device** | Signed-in user whose installation holds the fence       | Full: setup, sync, enroll/approve/pause/exclude, policy edit, test sends, activation, transfer-out, deletion | Sends gated by readiness (test/activation/birthday gates), policy caps, budget |
| **Owner on STANDBY device**         | Same account, second Android                            | View projections, privacy ops, can _begin_ sender transfer                                                   | Cannot arm/send; activation blocked (`active-sender-other-device`)             |
| **Pre-auth user**                   | Before `continueWithGoogle()`                           | Welcome/compatibility, initiate sign-in, view eligibility issues                                             | Nothing else                                                                   |
| **Web visitor**                     | Browser at /delete/                                     | Re-authenticated deletion of own account; receipt lookup                                                     | No other data access; fail-closed without runtime config                       |
| **Release authority (external)**    | Holder of Ed25519 pin `distribution-authority-pin.json` | Signs/denies distribution approvals unlocking restricted-SMS BuildConfig flags                               | Out-of-repo key; approvals expire (`validUntil`)                               |

---

# 5. INFORMATION ARCHITECTURE [VC]

## 5.1 Live navigation (`LiveAppShell.tsx:59–91`)

**Bottom tabs (3): Home · People · Settings** — confirms decision.md/Flow.md; supersedes PROJECT_ABOUT §6 four-tab spec (Gap G-01).

**Stack routes above tabs:** `Person {contactId}` · `Activity` · `ActivityDetail {activityId}` · `Attention` · `Automation` · `Diagnostics` · `HelpLegal` · `Message` · `Privacy` · `Schedule`.

Boot chain: `NativeAppBoundary` → bootstrap projection → if setup incomplete OR lifecycle recovery pending → `LiveSetupScreen` (step machine over 11 SETUP_STEPS: compatibility → google-account → contacts-disclosure → sync-summary → recipient-selection → message-and-policy → test-review → test-progress → reliability-repairs → activation-review → complete) else → `LiveAppShell`. Notification taps arrive via native route events: `automation-review(source:birthday-reminder)` or `attention`.

Fixture stack (dev-only): separate react-navigation tree mirroring the IA with synthetic data for design/localization preview (incl. ar-XB pseudo-RTL, platform override).

## 5.2 Deep links / external surfaces [VC]

Help/Legal opens hosted `${baseUrl}/privacy|terms|support|delete` via Linking (`LiveHelpLegalScreen.tsx`). No custom-scheme deep links found in live stack (v1.0's `wishwell://` scheme: 📄 not implemented ❓).

---

# 6. FEATURE INVENTORY (MASTER TABLE)

Statuses: ✅ 📄 🆕 ◐ 🔮 ❓ as defined. Priority reflects launch criticality observed from gating.

**Note:** Each feature below has a corresponding detailed specification in §6.2 that describes:

- **Ideal Behavior**: How the feature should work when fully functional
- **Current Implementation**: What is actually implemented
- **Gaps**: Differences between ideal and current state
- **Evidence**: Specific files proving implementation

| ID   | Feature                                                                                                                                                                                          | Status | Priority   | Primary code                                                                                                                                                                                         |
| ---- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------ | ---------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| F-01 | Device compatibility & eligibility screening                                                                                                                                                     | ✅     | P0         | domain/setup/model.ts; refreshCompatibility                                                                                                                                                          |
| F-02 | Google sign-in (Credential Manager, Firebase binding, App Check)                                                                                                                                 | ✅     | P0         | auth/AndroidGoogleIdentityCoordinator.kt; FirebaseAccountBindingProvider.kt                                                                                                                          |
| F-03 | Contacts authorization (incremental contacts.readonly only)                                                                                                                                      | ✅     | P0         | auth/ContactsAuthorization.kt                                                                                                                                                                        |
| F-04 | Contacts sync (People API pages, sync tokens, staged commit)                                                                                                                                     | ✅     | P0         | people/PeopleSyncCoordinator.kt; PeopleSyncWorker.kt                                                                                                                                                 |
| F-05 | Birthday detection + leap-day policies (feb-28/mar-01/**skip**)                                                                                                                                  | ✅     | P0         | planning/RecurrencePlanner.kt                                                                                                                                                                        |
| F-06 | Phone normalization + ambiguous-phone resolution choices                                                                                                                                         | ✅     | P0         | PeopleRequestFactory.kt; choosePhone intent; domain/validation/ephemeralPhone.ts                                                                                                                     |
| F-07 | People directory: search, filters (all/enabled/ready/needs-attention/excluded), pagination                                                                                                       | ✅ 🆕  | P1         | LivePeopleScreen.tsx; peoplePagination.ts                                                                                                                                                            |
| F-08 | Enrollment lifecycle: enable / pause / exclude / block destination / restore (review-gated)                                                                                                      | ✅     | P0         | PeoplePort; confirmEnrollment                                                                                                                                                                        |
| F-09 | Message templates (4 built-in: en/hi × personalized/generic) + editor                                                                                                                            | ✅     | P0         | domain/messages/model.ts; LiveMessageScreen.tsx                                                                                                                                                      |
| F-10 | Template semantic safety policy v2 (URL/promo/tracking/bidi/control-char bans)                                                                                                                   | ✅ 🆕  | P0         | domain/validation/templateDraft.ts; contracts/birthday-message-semantic-policy-v2.json                                                                                                               |
| F-11 | Gemini drafting: tones warm/simple/cheerful, relationships friend/family/colleague/partner/casual, milestones (new-job/graduation/moved/new-baby/milestone-age), 1–3 candidates, fallback states | ✅ 🆕  | P1         | gemini/AndroidGeminiSuggestionGateway.kt; contracts/gemini-prompt-policy-v2.json                                                                                                                     |
| F-12 | Approval snapshots + **12 invalidation reasons** (phone/birthday/name/template/window/late-policy/sim/segment-plan/disclosure/sender-epoch/permission-policy changed…)                           | ✅     | P0         | domain/approvals/model.ts                                                                                                                                                                            |
| F-13 | **Batch approval** of multiple recipients                                                                                                                                                        | ✅ 🆕  | P1         | prepareApprovals/confirmApprovals; LiveBatchApprovalScreen.tsx (prior docs: 🔮 "post-launch" — now shipped)                                                                                          |
| F-14 | Policy editor: send window, late policy (**none \| same-day-grace**), daily cap, segment cap, birthday confirmation                                                                              | ✅ 🆕  | P0         | LivePolicyEditor.tsx; LiveScheduleScreen.tsx; AutomationPort.previewPolicy/savePolicy                                                                                                                |
| F-15 | Policy preview simulation (400-day horizon)                                                                                                                                                      | ✅ 🆕  | P1         | PolicyPreview.simulatedDays:400                                                                                                                                                                      |
| F-16 | Readiness gates test/activation/birthday with severities info/warning/blocking                                                                                                                   | ✅     | P0         | domain/readiness/model.ts                                                                                                                                                                            |
| F-17 | Test SMS mode (daily arm cap 3, budget exhaustion reason)                                                                                                                                        | ✅     | P0         | claimTest; TEST_ARM_CAP=3                                                                                                                                                                            |
| F-18 | Unattended delivery pipeline (claim→arm→submit→observe; 25-phase machine)                                                                                                                        | ✅     | P0         | orchestration/AndroidAutomationOrchestrator.kt; SmsGateway.kt                                                                                                                                        |
| F-19 | Server anti-duplicate: occurrenceKeys + destinationGuards + budgets (20 birthday arms/day UTC)                                                                                                   | ✅     | P0         | backend decisions.ts; CoordinationContracts.kt                                                                                                                                                       |
| F-20 | Single server-authorized safe retry within window                                                                                                                                                | ✅     | P0         | authorizeSafeRetry; SmsRetryAuthorizationPolicyTest                                                                                                                                                  |
| F-21 | Dual-SIM fail-closed binding (system-default subscription only)                                                                                                                                  | ✅     | P0         | SubscriptionBindingPolicy.kt (prior docs' "user SIM picker": 📄)                                                                                                                                     |
| F-22 | Delivery truthfulness (carrier delivery never claimed; outcome workers reconcile callbacks)                                                                                                      | ✅     | P0         | SmsOutcomeNetworkProcessor.kt; scenario schema `carrierDeliveryClaimed:false`                                                                                                                        |
| F-23 | Sender lease/fence (10-min lease, ACTIVE/STANDBY/REVOKED, epochs)                                                                                                                                | ✅     | P0         | renewSenderLease; InstallationState                                                                                                                                                                  |
| F-24 | Sender transfer (verifying→remote-pending→remote-draining→complete(requiresTest))                                                                                                                | ✅     | P1         | beginSenderTransfer/completeSenderTransfer; LiveAndroidDeviceControls.tsx                                                                                                                            |
| F-25 | Today's occurrence choice (send-through / open-system-composer / start-next-year)                                                                                                                | ✅ 🆕  | P1         | TodayOccurrenceChoicePolicyTest                                                                                                                                                                      |
| F-26 | Attention center + issue list + recovery routes                                                                                                                                                  | ✅     | P1         | LiveAttentionScreen.tsx; ActivityPort.listIssues                                                                                                                                                     |
| F-27 | Activity log (24 kinds incl. composer-\*, transfer, settings-changed, reminder-scheduled)                                                                                                        | ✅     | P0         | domain/activity/model.ts; LiveActivityScreen(+Detail)                                                                                                                                                |
| F-28 | Diagnostics preview→share (private-content excluded, validated client-side)                                                                                                                      | ✅     | P1         | LiveDiagnosticsScreen.tsx                                                                                                                                                                            |
| F-29 | Attention notifications (severity channels, per-day dedupe, tap routing)                                                                                                                         | ✅     | P1         | attention/AndroidAttentionNotifications.kt                                                                                                                                                           |
| F-30 | Notification permission flow (request/settings handoff)                                                                                                                                          | ✅     | P1         | DeviceLifecyclePort; NotificationPermissionTestActivity (debug)                                                                                                                                      |
| F-31 | Privacy inventory (counts, bytes, last sync, consent versions)                                                                                                                                   | ✅     | P0         | LivePrivacyInventory.tsx; PrivacyPort.getInventory                                                                                                                                                   |
| F-32 | Privacy operations ×8 (disconnect-contacts, revoke-google-access, sign-out-retain, sign-out-wipe, delete-account, wipe-local-data, **clear-gemini-templates 🆕, clear-activity 🆕**)             | ✅     | P0         | PRIVACY_ACTION_KINDS; two-phase prepare/confirm                                                                                                                                                      |
| F-33 | Deletion saga w/ drains, tombstones, scheduled sweep, Auth-deletion verification                                                                                                                 | ✅     | P0         | backend deletionOrchestrator.ts; sweepDeletionDrains                                                                                                                                                 |
| F-34 | Content-free deletion receipts (SHA-256 keyed, 365-day retention)                                                                                                                                | ✅     | P0         | accountDeletionReceipt; deletionReceipt.ts                                                                                                                                                           |
| F-35 | Lifecycle repair kinds (disconnect/revoke/sign-out-wipe/wipe-local-data) w/ identity lease                                                                                                       | ✅ 🆕  | P1         | LIFECYCLE_REPAIR_KINDS; LiveSetupScreen repair flow                                                                                                                                                  |
| F-36 | Web deletion page (reCAPTCHA Enterprise + reauth popup; EN/HI; sessionStorage receipt)                                                                                                           | ✅     | P0         | backend/hosting/src/\*; /delete/                                                                                                                                                                     |
| F-37 | Help & Legal resources (hosted links, availability projection)                                                                                                                                   | ✅     | P2         | LiveHelpLegalScreen.tsx; PublicResourcesPort                                                                                                                                                         |
| F-38 | Localization EN/HI (+ar-XB dev pseudo-RTL; bidi helpers; compile-time key safety)                                                                                                                | ✅     | P1         | localization/\*; productionResources.ts TranslationKey                                                                                                                                               |
| F-39 | Accessibility primitives (48 dp targets, focus order, RouteAccessibilityFocus announcements, high-contrast palettes, large-text E2E suite)                                                       | ✅     | P1         | design-system/\*; e2e 04-large-text                                                                                                                                                                  |
| F-40 | Fixture preview app (**DEV**; design/localization QA surface)                                                                                                                                    | ✅ 🆕  | P3         | FixturePreviewApp.tsx                                                                                                                                                                                |
| F-41 | Distribution-channel enforcement (BuildConfig flags from signed approval; blocks all gates when unapproved)                                                                                      | ✅ 🆕  | P0         | validate-distribution-evidence.mjs; gradle flavor blocks                                                                                                                                             |
| F-42 | Clock-trust system (untrusted-clock blocking, 5-min tolerance)                                                                                                                                   | ✅ 🆕  | P0         | clock-trust entity; clock-untrusted code                                                                                                                                                             |
| F-43 | Reset-safety replay protection (contact-derived resets)                                                                                                                                          | ✅ 🆕  | P1         | resetContactDerivedState; reset-safety entities                                                                                                                                                      |
| F-44 | Standby/hibernation diagnostics (diagnose-only)                                                                                                                                                  | ✅ 🆕  | P1         | AppStandbyBucketDiagnosticPolicy; hibernation-status-unsafe codes                                                                                                                                    |
| F-45 | Battery-optimization exemption request flow (guide users through system settings; **no programmatic request**)                                                                                   | 📄     | P1         | Settings intent opened on detection (LifecycleController); **no guided UX flow** (v1.0 JOURNEY-09/UI-013)                                                                                            |
| F-46 | Free-form user SIM selection picker                                                                                                                                                              | 📄     | P1         | superseded by F-21 fail-closed default-SIM policy                                                                                                                                                    |
| F-47 | Late-send next-morning option                                                                                                                                                                    | 📄     | P2         | actual policy enum: none \| same-day-grace only                                                                                                                                                      |
| F-48 | Product analytics event stream (v1.0 §21 catalog)                                                                                                                                                | 📄     | P2         | **zero analytics SDK in repo** (grep-verified)                                                                                                                                                       |
| F-49 | Crash reporting / FCM push                                                                                                                                                                       | 📄 ❓  | P2         | none found; docs ambiguous ("FCM for cloud events")                                                                                                                                                  |
| F-50 | iOS Companion Edition (reminders + composer handoff)                                                                                                                                             | ◐      | 🔮 Phase 3 | client protocol present (DeliveryPlatform.IOS_COMANION\*, composer activity kinds, IOS_COMPOSER_RESERVED hourly recheck, 72 h reservation constant); no iOS app; server reservation callables absent |
| F-51 | Offline degraded mode                                                                                                                                                                            | ✅     | P2         | local-first reads work offline; network-offline reason codes; safe fail-closed behavior; user-friendly offline messages (EN/HI)                                                                      |
| F-52 | Custom-scheme deep links (`wishwell://*`)                                                                                                                                                        | 📄     | P3         | not found in live stack                                                                                                                                                                              |
| F-53 | Manual web-deletion fallback without Google login                                                                                                                                                | 📄     | P2         | actual: reauth popup mandatory, fails closed                                                                                                                                                         |

\* Exact spelling `DeliveryPlatform.IOS_COMPANION` (core/model/DeliveryPlatform.kt).

---

## 6.2 DETAILED FEATURE SPECIFICATIONS

Each feature specification below follows this structure:

- **Ideal Behavior**: How the feature should work when fully functional (product requirement)
- **Current Implementation**: What is actually implemented (verified from code)
- **Gaps**: Differences between ideal and current state
- **Evidence**: Specific files proving implementation status

Due to the extensive number of features (53 total), this section provides detailed specifications for the most critical features (F-01 through F-20). Additional feature specifications follow the same pattern and can be expanded upon request.

---

### F-01 — Device Compatibility & Eligibility Screening

**Status:** ✅ IMPLEMENTED

**Ideal Behavior:**
The app must verify device capability before allowing setup to proceed. Users should see clear eligibility issues with actionable resolution paths. The system checks:

- Android API level (minimum 29)
- Telephony hardware availability
- SMS capability
- Required permissions grantability
- Play Services availability for Credential Manager
- Freshness of prior sync (>30 days triggers pause warning)

**Current Implementation:**
All eligibility checks implemented in `domain/setup/model.ts` with `refreshCompatibility()` native intent. Issues classified as blocking/warning/info with localized copy and optional native action handles. Setup wizard step 1 (compatibility) blocks progression on blocking issues.

**Gaps:** None — fully implemented.

**Evidence:**

- `src/domain/setup/model.ts` — eligibility model, issue taxonomy
- `android/app/src/main/java/com/yashsomani/birthdayautopilot/setup/DeviceEligibilityChecker.kt` — native checks
- `src/features/setup/LiveSetupScreen.tsx` — UI integration (step 1)
- `src/application/setup/SetupPort.ts` — port interface

---

### F-02 — Google Sign-In (Credential Manager, Firebase Binding, App Check)

**Status:** ✅ IMPLEMENTED

**Ideal Behavior:**
Users authenticate via Google using Android Credential Manager for seamless sign-in. The system must:

- Present Google account chooser
- Obtain ID token with explicit user consent
- Bind to Firebase Auth via `signInWithCustomToken()`
- Enforce Firebase App Check (Android Attestation provider)
- Maintain session across app restarts
- Support sign-out with data retention options

**Current Implementation:**
Full flow implemented using `androidx.credentials` library. Firebase binding via `FirebaseAccountBindingProvider.kt`. App Check enforced with limited-use tokens. Session persistence via Firebase Auth. Sign-out variants: retain-data vs wipe-data.

**Gaps:** None — fully implemented.

**Evidence:**

- `android/app/src/main/java/com/yashsomani/birthdayautopilot/auth/AndroidGoogleIdentityCoordinator.kt` — Credential Manager orchestration
- `android/app/src/main/java/com/yashsomani/birthdayautopilot/auth/FirebaseAccountBindingProvider.kt` — Firebase token exchange
- `src/infrastructure/native/NativeBirthday.ts` — bridge contract
- `src/features/setup/LiveSetupScreen.tsx` — UI step 2

---

### F-18 — Unattended Delivery Pipeline (Claim→Arm→Submit→Observe)

**Status:** ✅ IMPLEMENTED

**Ideal Behavior:**
Fully automated birthday SMS delivery:

1. **Claim**: Server claims occurrence for user
2. **Arm**: Schedule send within spacing constraints (≥5 min)
3. **Submit**: Send via SmsManager at appointed time
4. **Observe**: Track callback PendingIntents for outcome
5. **Report**: Log outcome to activity, trigger attention if failed

**Current Implementation:**
`AndroidAutomationOrchestrator.kt` (1,850 lines) implements 25-phase state machine. `SmsGateway.kt` submits messages. Outcome workers reconcile callbacks. Global mutex prevents concurrent orchestrations. 400-day planning horizon. 5-min clock tolerance. 15-min sent watchdog.

**Gaps:** None — fully implemented.

**Evidence:**

- `android/app/src/main/java/com/yashsomani/birthdayautopilot/orchestration/AndroidAutomationOrchestrator.kt`
- `android/app/src/main/java/com/yashsomani/birthdayautopilot/sms/SmsGateway.kt`
- `android/app/src/main/java/com/yashsomani/birthdayautopilot/outcome/SmsOutcomeNetworkProcessor.kt`

---

### F-19 — Server Anti-Duplicate: Occurrence Keys + Destination Guards + Budgets

**Status:** ✅ IMPLEMENTED

**Ideal Behavior:**
Prevent duplicate sends via:

- **Occurrence keys**: Unique per birthday instance
- **Destination guards**: Block duplicate destination+occurrence combos
- **Budgets**: 20 birthday arms/day UTC, 3 test arms/day UTC
  Server enforces at-most-one submission guarantee.

**Current Implementation:**
`decisions.ts` implements occurrence key generation and destination guard checks. Budget caps enforced in callables. Schema asserts zero duplicate submissions.

**Gaps:** None — fully implemented.

**Evidence:**

- `backend/functions/src/decisions.ts`
- `android/app/src/main/java/com/yashsomani/birthdayautopilot/contracts/CoordinationContracts.kt`
- `backend/functions/src/model.ts` — budget caps

---

### F-44 — Standby/Hibernation Diagnostics (Diagnose-Only)

**Status:** ✅ IMPLEMENTED

**Ideal Behavior:**
Detect and report battery optimization restrictions that would block automation:

- Identify app standby bucket status
- Detect OEM-specific hibernation policies
- Report diagnostic codes for support evidence

**Current Implementation:**
Diagnostics fully implemented with comprehensive diagnostic codes. `AppStandbyBucketDiagnostic.kt` reads current bucket status. OEM-specific codes present in readiness evaluation. Diagnostic codes exposed to frontend via native module.

**Implemented:**

- App standby bucket reading (`AndroidAppStandbyBucketDiagnosticReader`)
- Policy-based evaluation (`AppStandbyBucketDiagnosticPolicy`)
- 13 diagnostic codes (EXEMPTED, ACTIVE, WORKING_SET, FREQUENT, RARE, RESTRICTED, NEVER, UNKNOWN, API_UNSUPPORTED, SERVICE_UNAVAILABLE, ACCESS_DENIED, RUNTIME_UNAVAILABLE, PLATFORM_UNAVAILABLE, READ_FAILED)
- Integration with readiness probe (`dozeAllowlisted` signal)
- Eligibility blocking when not allowlisted (`DOZE_EXEMPTION_MISSING`)
- Settings intent opened automatically on detection

**Missing:**

- None (diagnose-only requirement met)

**Evidence:**

- `android/app/src/main/java/com/yashsomani/birthdayautopilot/readiness/AppStandbyBucketDiagnostic.kt` — diagnosis implementation
- `android/app/src/main/java/com/yashsomani/birthdayautopilot/readiness/AndroidReadinessProbe.kt:111-114` — dozeAllowlisted check
- `android/app/src/main/java/com/yashsomani/birthdayautopilot/readiness/DistributionEligibility.kt:116` — eligibility enforcement
- `android/app/src/main/java/com/yashsomani/birthdayautopilot/lifecycle/AndroidLifecycleController.kt:2376` — settings intent

---

### F-45 — Battery Optimization Exemption Request Flow (Documented Only, No Guided UX)

**Status:** 📄 DOCUMENTED ONLY (NOT_IMPLEMENTED)

**Ideal Behavior:**
Guide users through battery optimization exemption process:

- Detect when exemption is needed
- Present step-by-step OEM-specific instructions
- Open system settings for user to grant exemption
- Track exemption status after user action
- Provide fallback manual instructions if programmatic request unavailable

**Current Implementation:**
**Minimal implementation exists:** When `doze-exemption-missing` reason code is detected, the system opens the battery optimization settings screen (`Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`). However, there is **no guided UX flow**, no step-by-step instructions, no status tracking after user returns, and no persistence of exemption state.

**Implemented:**

- Settings intent triggered on detection (`AndroidLifecycleController.kt:2376`)
- Reason code mapped to correct system action
- Included in actionable codes list for UI remediation

**Missing:**

- Guided UX flow with step-by-step instructions
- Pre-exemption explanation screen
- Post-exemption verification check
- Status persistence in database
- OEM-specific instruction variants
- Retry logic if user cancels
- Integration with onboarding journey (JOURNEY-09)

**Gaps:**

- No dedicated UI screen for exemption flow (UI-013 not implemented)
- No status tracking entity in Room database
- No integration with v1.0 JOURNEY-09 onboarding sequence
- User must manually navigate back; no automatic re-evaluation

**Evidence:**

- `android/app/src/main/java/com/yashsomani/birthdayautopilot/lifecycle/AndroidLifecycleController.kt:2376` — settings intent only
- `android/app/src/main/java/com/yashsomani/birthdayautopilot/lifecycle/AndroidLifecycleController.kt:2529` — listed in ACTIONABLE_CODES
- Gap: No guided UX flow found
- Gap: No exemption status tracking entity in database schema
- Documentation: v1.0 JOURNEY-09/UI-013 specifies guided flow not present in code

### F-50 — iOS Companion Edition (Reminders + Composer Handoff)

**Status:** ◐ PARTIALLY_IMPLEMENTED

**Ideal Behavior:**
iOS companion app that:

- Receives birthday reminders from server
- Acquires composer reservation (72-hour hold)
- Presents Messages.app composer handoff
- Commits/releases reservation on send/cancel
- Respects hourly recheck for reserved occurrences

**Current Implementation:**
**Protocol scaffolding only:**

- `DeliveryPlatform.IOS_COMPANION` enum present in Android codebase
- Composer activity kinds defined
- `IOS_COMPOSER_RESERVED` hourly recheck in orchestrator
- 72h reservation constant defined
- `iosComposerReservations` TTL collection exists in docs

**Missing:**

- iOS app build (removed commit `61882f9`)
- Server callables: `acquireIOSComposerReservation`, `commitIOSComposerReservation`, `releaseIOSComposerReservation`
- `companionStatus` whitelisted but unimplemented in FirebaseCoordinationClient.kt

**Gaps:**

- Complete server-side reservation management system absent
- No iOS source code in repository
- Protocol half-built creates roadmap confusion

**Evidence:**

- `android/app/src/main/java/com/yashsomani/birthdayautopilot/core/model/DeliveryPlatform.kt` — enum exists
- `backend/functions/src/functions/index.ts` — NO iOS callables found
- `android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/orchestration/AndroidAutomationOrchestrator.kt` — hourly recheck logic
- Gap: No iOS source directory exists
- Gap: No callable implementations for reservation management\*\*
- Server-side reservation management absent
- iOS app removed from repository
- Phase 3 future work

**Evidence:**

- `android/app/src/main/java/com/yashsomani/birthdayautopilot/orchestration/DeliveryPlatform.kt` — IOS_COMPANION enum
- Gap: No server callables found in `backend/functions/src/callables/`
- Gap: No iOS source code (workflows deleted `2b3a3b4`)

---

# 7. BUSINESS REQUIREMENTS

| ID    | Requirement                                                                        | Rationale                                   | Priority | Status                          |
| ----- | ---------------------------------------------------------------------------------- | ------------------------------------------- | -------- | ------------------------------- |
| BR-01 | No message without prior approval of exact content, invalidated on material change | Trust core; abuse shield                    | P0       | ✅                              |
| BR-02 | Structural single-send per occurrence                                              | Double-wish destroys brand                  | P0       | ✅                              |
| BR-03 | Delivery truthfulness                                                              | Review protection                           | P0       | ✅                              |
| BR-04 | Restricted SMS capability only via signed, unexpired approval                      | Policy/legal containment                    | P0       | ✅                              |
| BR-05 | Complete data-deletion path reachable from web without app                         | Google policy; user rights                  | P0       | ✅ eng / ❓ SLA copy            |
| BR-06 | Minimal data footprint end-to-end                                                  | Privacy moat; breach-cost ceiling           | P0       | ✅                              |
| BR-07 | Fail-closed release admission                                                      | Bad release = broken promises at scale      | P0       | ✅                              |
| BR-08 | Activation funnel measurable                                                       | Retention economics need leading indicators | P0       | 📄→[R]                          |
| BR-09 | Self-serve repair for top failure classes                                          | Support-cost control                        | P1       | ✅ capability / ❓ channel      |
| BR-10 | Bilingual launch surface EN/HI                                                     | Market reach; store locales                 | P1       | ✅                              |
| BR-11 | Accessibility conformance as launch blocker                                        | Legal + reach                               | P1       | ✅ tooling / ◐ evidence pending |
| BR-12 | Physical-device performance budgets                                                | OEM reality check                           | P1       | ✅ tooling                      |
| BR-13 | Free core forever                                                                  | Brand promise                               | P0       | ✅ policy                       |
| BR-14 | iOS companion only after Android stability window                                  | Risk sequencing                             | P2       | 🔮                              |
| BR-15 | Documentation parity with code                                                     | Contributor/support accuracy                | P1       | 📄 debt open                    |
| BR-16 | Support operating model defined pre-launch                                         | BO-6/BR-16 dependency                       | P0       | ❓                              |

---

# 8. BUSINESS OBJECTIVES

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

---

# 9. FUNCTIONAL REQUIREMENTS

FR-01 Bridge integrity: envelope contractVersion==1, revision monotonic, payload ≤1 MiB, double Zod validation; failures map to stable support codes.

FR-02 Optimistic concurrency: every mutation carries expectedRevision; stale → reload, never blind-write.

FR-03 Projection freshness: area-scoped invalidations + foreground reload; screens render last-good data with refresh-problem banner rather than blanking.

FR-04 Two-phase destructive actions with consequence whitelists + TTL leases + post-confirm corroboration (privacy, enrollment, transfer, approvals, activation).

FR-05 Readiness gating: every send path passes test|activation|birthday gate decision; blocked reasons carry localized copy + optional native action handle.

FR-06 Validation catalogs: FieldName ∈ {birthday, confirmation, dailyCap, phone, sim, template, window}; UiDraftValidation returned inline for drafts (template/window/phone validators co-located with tests).

FR-07 Notifications: POST_NOTIFICATIONS runtime flow; per-category/day dedupe; single-use tap routes; quiet behavior unspecified ❓ (BRULE-042 from v2 remains [R]).

FR-08 Background resilience: boot/clock/locale/package/default-SMS triggers re-reconcile; durable wake ledger; 30 s successor floor; heartbeat lease prevents concurrent orchestrations.

FR-09 Retention: local retention sweeps (DataRetentionWorker) mirror server TTLs (birthday claims 400 d; tests 30 d; standby installs 90 d; revoked 30 d; coordination receipts 30 d; deletion receipts 365 d).

FR-10 Web contract: /delete/ requires App Check (reCAPTCHA Enterprise) + Firebase reauth; requestId UUID; receipt only in tab session.

FR-11 Localization: all user-visible strings keyed via compile-safe TranslationKey; hi fallback en; locale-aware formatting (formatLive); RTL fixture verification in dev.

---

# 10. BUSINESS RULES (CODE-VERIFIED CONSTANTS)

| Rule                        | Value / Behavior                                                                                           | Source                                                   |
| --------------------------- | ---------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| Segment ceiling             | 1–2 SMS parts per message (single or multipart-2)                                                          | SmsPlatformSubmitter.kt; gemini-prompt-policy segmentCap |
| Daily arm budgets           | 20 birthday arms / 3 test arms per UTC day-window                                                          | backend model.ts caps                                    |
| Arm spacing / submit window | ≥5 min between arms; submit ≤60 s after arm                                                                | model.ts; orchestrator                                   |
| Lease                       | sender ownership lease ≤10 min, renewed                                                                    | renewSenderLease                                         |
| Retry                       | exactly one server-authorized retry; bounded by window end                                                 | authorizeSafeRetry; policy tests                         |
| Clock                       | untrusted clock blocks sends; 5-min server tolerance                                                       | clock-trust; decisions.ts                                |
| Dual-SIM                    | send only on active system-default SMS subscription                                                        | SubscriptionBindingPolicy.kt                             |
| Leap day                    | user must pick feb-28 / mar-01 / skip; unset blocks enrollment                                             | RecurrencePlanner.kt                                     |
| Late policy                 | none \| same-day-grace (no next-morning option exists)                                                     | domain/birthdays/model.ts                                |
| Planning horizon            | 400 days simulated/enrolled                                                                                | orchestrator; PolicyPreview                              |
| Freshness                   | >7 d stale warning; >30 d pauses automation                                                                | contacts-freshness-policy-v1.json                        |
| Distribution                | restricted SMS only when signed approval valid (channel, installer, cert SHA256, API bounds 29–37, expiry) | validate-distribution-evidence.mjs                       |
| Install provenance          | installer allowlist checked at runtime                                                                     | installer-allowlist-missing code                         |
| Dedupe                      | one successful send per occurrenceKey+destinationGuard; duplicate submissions asserted 0                   | decisions.ts; scenario schema                            |
| Test isolation              | test claims isolated from birthday guard family                                                            | claimTest                                                |
| AI boundary                 | prompts exclude PII; generation unreachable from send workers; rate scopes capped at 8 retained            | gateway doc-comment; prompt policy                       |
| Server storage              | no raw contact/message fields; HMAC aliases only                                                           | privacy-architecture tests                               |
| Receipts                    | content-free; SHA256('birthday-deletion-receipt-v1\0'+id)                                                  | deletionReceipt.ts                                       |

---

# 11. NON-FUNCTIONAL REQUIREMENTS

| Area            | Verified state [VC]                                                                                                                                                                                                                                 |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Performance     | Budgets codified (performance-budgets.json): cold/warm start, search@10k, normalize-commit wall+RSS, crash/ANR counts, reconcile CPU, claim-arm latency, battery delta/hours; enforced via evidence validator ≤30 d fresh, physical device required |
| Reliability     | Fail-closed defaults everywhere; property-tested races (deletion-vs-registration, drain deadlines); ~76 JVM suites; emulator matrix API 29/36/37-16KB; Maestro smoke on device                                                                      |
| Security        | SQLCipher+Keystore; FLAG_SECURE backgrounded; no secrets in repo (history scanner); App Check limited-use tokens; deny-all rules; Ed25519 pinned approvals; CSP-locked web                                                                          |
| Privacy         | No refresh token on device; no raw PII server-side; masked phones; diagnostics scrubbing tested; receipts content-free; backup disabled (allowBackup=false + extraction rules)                                                                      |
| Accessibility   | 48 dp targets; high-contrast palettes; focus/announcement primitives; large-text E2E; 200% scaling asserted in store evidence checklist                                                                                                             |
| Compatibility   | minSdk 29; telephony features required; certified API bounds 29–37 from approval; flavors isolate fixtures from prod permissions                                                                                                                    |
| Maintainability | Hexagonal boundaries enforced by architecture tests; deterministic manifests; pinned toolchain (Node 24.18.0/JDK21/NDK27/Maestro 2.6.1)                                                                                                             |
| Observability   | Operational only: heartbeats, wake ledger, attention codes, perf evidence. **No crash/analytics telemetry** (see §15)                                                                                                                               |
| Availability    | Backend maxInstances 20/concurrency 20; scheduled self-healing sweeps; client degrades to cached projections offline                                                                                                                                |

---

# 12. UI/UX SCREEN SPECIFICATIONS [VC]

Design language: calm utility (Inter; accent #4B52A3; light #F7F7FC / dark #11121A; positive #256A45 / warning #8A4F08 / critical #A53535 + surface tints; radii 8/14/20/pill; spacing 4–48; 48 dp targets; dark + high-contrast variants) — theme.ts matches Stitch tokens. Zero celebratory deception; status text always paired with icon.

| Screen                            | Purpose               | Actions                                                                                                             | Notable states                                                                                                                  |
| --------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| Setup wizard (11 steps)           | Guided activation     | continueWithGoogle, authorizeContacts, syncContacts, defer("finish later"), repairLifecycleState, transfer controls | Eligibility issues; deletion-cleanup pending; lifecycle repair identity lease (≈4 min TTL); step-numbered progress              |
| Home                              | Glanceable health     | open Privacy/Diagnostics/Help; device controls                                                                      | Automation hero, next greeting, counts (configured/enabled/needs-attention/today/next-7-days), contacts-sync status, heartbeats |
| People                            | Directory             | search, filter chips, paginate, open Person                                                                         | Empty/no-permission/loading/error                                                                                               |
| Person detail                     | Manage one person     | enrollment review/confirm, pause, exclude, block/unblock destination, restore, phone/birthday choices               | Masked phone; needs-attention reasons; approval validity                                                                        |
| Message                           | Draft authoring       | edit draft, previewMessage, saveMessage(handle), generateSuggestions                                                | Live validation issues; Gemini requesting/candidates/fallback banners                                                           |
| Batch approval                    | Multi-approve         | prepareApprovals → confirmApprovals                                                                                 | Per-item outcomes                                                                                                               |
| Schedule / Policy editor          | Configure timing/caps | previewPolicy (400-day sim), savePolicy                                                                             | invalid-window/cap conflicts                                                                                                    |
| Automation                        | Master control        | prepareActivation→activate, pauseAll, prepareResume→resume, today-occurrence choice                                 | effective-state cards (not-configured/test-only/paused-repair/active/action-required/standby/transfer-pending/deleting)         |
| Attention                         | Repair queue          | listIssues → route to fix (readiness action handles)                                                                | Severity ordering; auto-clear                                                                                                   |
| Activity (+detail)                | Audit trail           | listActivity(query), open detail                                                                                    | 24 kinds; content-minimized rows                                                                                                |
| Diagnostics                       | Self-serve health     | preview → share                                                                                                     | Private-content exclusion banner                                                                                                |
| Privacy                           | Data control          | 8 action kinds two-phase; inventory expand; cloud-boundary card                                                     | Triple-revision consistency requirement ("truth usable"); remote-unknown retry                                                  |
| Settings                          | Preferences           | appearance system/light/dark; links                                                                                 | Theme persistence ❓ (in-memory observed)                                                                                       |
| HelpLegal                         | Resources             | open hosted pages                                                                                                   | availability projection                                                                                                         |
| Transfer (within device controls) | Move sender role      | prepare→begin→resume→complete                                                                                       | draining countdown; test-required completion                                                                                    |

Empty/loading/error/success states exist across live screens via shared `LiveProjectionState` components (loading/error-retry/refresh-banner/action-feedback with support reference).

---

# 13. DATA MODEL & INTEGRATIONS

**Local Room 37 entities grouped:** control (account/installation-binding/consent/coordination-permit/send-attempt/callback-token/delivery-event/outcome-projection/reset-safety/clock-trust/readiness), contact (people-staging\*), approval, occurrence, activity, ledger (SafetyLedgerDao append-only).

**Server collections:** accounts/{uid} (+installations, occurrenceClaims, testClaims, occurrenceKeys, destinationGuards, claimRequests, armOutcomes, armBudgets), deletionTombstones, coordinationPresence, coordinationOperationFences, coordinationOperationReceipts(+Latest), globalControl/current; TTL `cleanupAt` ×13 groups; zero composite indexes.

**Integrations:**

- Google People API (read)
- Credential Manager OAuth (contacts.readonly incremental)
- Firebase Auth + App Check (Android attestation provider only — the backend consumes App Check tokens, it does **not** verify Play Integrity itself) + Callables (16 + 2 scheduled sweeps, region asia-south1)
- device-side `firebase-ai` (Gemini 3.5-flash via vertex-ai/global, **client-only** — no Gemini in backend functions)
- reCAPTCHA Enterprise (web)
- Android Telephony/SmsManager/SubscriptionManager/WorkManager/AlarmManager-equivalent scheduling
- OS share sheet
- Linking (hosted pages)

**Not present:** FCM, third-party analytics, contact writes, multi-account.

---

# 14. KEY USER JOURNEYS (VERIFIED)

1. **First-run to activated automation:** welcome/compatibility → Google → contacts disclosure+sync → recipient selection (all off) → message-and-policy (template/Gemini, batch approve available) → test-review → test-progress (≤3/day) → reliability-repairs (if issues) → activation-review → activate. Deferred exits preserved at each step.

2. **Birthday send (unattended):** planner marks occurrence → reconcile claims → server issues keys/guards → arm within spacing → barrier → SmsManager submit (≤2 parts, default SIM sub) → callback observe → outcome worker classifies → activity row + optional silent success; failures → attention notification (deduped) → tap routes to automation-review or attention.

3. **Repair:** attention list → issue detail (plain-language safe reason) → native action handle (open settings / re-auth / choose phone / confirm leap policy) → recheck clears.

4. **Device replacement:** new install registers STANDBY → transfer prep/begin → drain → complete → mandatory test → reactivate.

5. **Deletion:** in-app two-phase (or web reauth flow) → local wipe → remote drain/tombstone → sweep verifies Auth deletion → receipt id retained by user.

6. **Recovery after revocation:** reconnect-required account state → repair lifecycle (identity lease) → resume operation or clean sign-out-wipe.

---

# 15. ANALYTICS & OBSERVABILITY

**Current state [VC]:** No product analytics, crash reporting, or push infrastructure. Telemetry equivalents: DB heartbeats, durable wake ledger, attention reason codes, performance-budget evidence at release time, CI matrices, server-side operational receipts. The v1.0 event catalog (onboarding_started…account_deleted) is 📄 entirely unimplemented.

**Recommended [R]:** privacy-preserving local counters exported via the existing scrubbed-diagnostics channel first (zero new SDKs), then opt-in aggregate funnel telemetry (activation steps, approval rate, invalidation reasons, gate-block reasons, transfer completions, deletion SLA) once a privacy design review approves vendor; add Play Vitals / Console statistics as interim sources. Crash reporting decision required (❓) — ANR vector noted in §17 debt.

---

# 16. GAP ANALYSIS

## 16.1 Implemented but Undocumented (🆕 highlights)

Batch approval (F-13) · message milestones (F-11) · relationship context enum (F-11) · policy caps daily/segment + 400-day preview (F-14/15) · clear-gemini-templates & clear-activity (F-32) · sign-out-retain variant · destination blocking/unblock/restore (F-08) · clock-trust (F-42) · reset-safety (F-43) · installer/distribution enforcement (F-41) · today-occurrence choices (F-25) · people pagination · notification tap routing ring · ar-XB pseudo-RTL fixture · FLAG_SECURE behaviors · asia-south1 residency · content-free receipts · standby/hibernation diagnostics · test budget 3/day · birthday arm budget 20/day · skip leap policy · 24-kind activity taxonomy incl. composer phases · lifecycle-repair identity lease · single-consume route semantics · smoke/e2e flavor isolation.

## 16.2 Documented but Not Implemented (📄)

Battery-optimization exemption request (F-45) · free SIM picker (F-46) · next-morning late policy (F-47) · analytics event stream (F-48) · crash reporting/FCM (F-49) · wishwell:// deep links (F-52) · manual non-Google web-deletion fallback (F-53) · README iOS artifact pipeline section (files deleted) · PROJECT_ABOUT "encrypted cloud backup of contacts" (false — server stores no contacts) · "FCM cloud-side events" · UI-022 sender-device name/last-seen display ❓ · v1.0 DATA-model fields (parts/simId/reconciled) replaced by richer reality · BRULE-008 default feb-28 (actual: must choose; skip possible) · BRULE-012 ten-part ceiling (actual 2).

## 16.3 Partially Implemented (◐)

iOS Companion Edition (protocol scaffolding only; server callables absent; TTL orphaned).

**Note:** F-51 (Offline Degraded Mode) was previously listed here but is now **fully implemented** with local-first reads, safe fail-closed behavior, and user-friendly offline messages in both English and Hindi. F-44 (Standby/Hibernation Diagnostics) is also fully implemented as a diagnose-only feature. F-45 (Battery Optimization Exemption Request Flow) remains documented-only without guided UX implementation.

## 16.4 Inconsistent (code vs docs)

Tone sets (warm/simple/cheerful vs casual/professional/short) · approval-invalidation breadth (12 classes vs docs' phone/birthday) · sender fencing model (lease/epoch/drain vs simple flag) · deletion flow (drain saga vs immediate purge ≤48h — actual SLA governed by drain deadlines; ❓ confirm marketing SLA) · navigation (3-tab confirmed; Flow.md screen IDs S13/S14 map loosely to Schedule/Automation screens) · "3 candidate variations" (actual 1–3).

## 16.5 Missing Requirements (should be specified)

Notification quiet-hours policy · theme persistence semantics · widget/shortcut absence (confirm non-goal) · reply-handling expectation copy · data-retention UX for local activity (auto-prune horizons visible?) · support-contact SLA copy · store-listing content ownership.

---

# 17. RISKS & TECHNICAL DEBT

| Risk/Debt Item                                   | Severity | Mitigation Status                                                               |
| ------------------------------------------------ | -------- | ------------------------------------------------------------------------------- |
| `runBlocking` in BirthdayNativeModule (ANR risk) | High     | Code fix needed; ANR budget already in perf evidence                            |
| No crash telemetry blinds field diagnosis        | High     | Interim: Play Vitals + opt-in scrubbed counters [R]; then minimal SDK w/ review |
| OEM aggressive killers delay sends               | High     | Diagnostics codes shipped ✅; guided exemption UX not implemented (F-45 📄)     |
| iOS protocol half-build confuses roadmap         | Med      | Explicit Phase-3 gate BO-7; remove dead client whitelist entry or implement     |
| Docs drift misleads contributors/support         | Med      | BR-15 parity pass; adopt doc-drift checklist                                    |
| Signing-authority key loss/compromise            | High     | Out-of-band custody process ❓; pin rotation procedure needed                   |
| Carrier filtering in new markets                 | Med      | Budgets/pacing ✅; per-country matrix evidence required before launch           |
| Gemini terms shift                               | Low-Med  | policy-suspended fallback + operational gate ✅; contract watch                 |
| Web tier misconfiguration                        | Low      | Fail-closed runtime-config gate ✅                                              |
| Dev/staging flavors lack source sets             | Low      | Silent main inheritance — intentional ❓                                        |
| Settings theme persistence unclear               | Low      | UX polish needed                                                                |
| Single retry may under-deliver on flaky networks | Med      | Deliberate anti-spam tradeoff; consider user-initiated "send again" affordance  |

---

# 18. ROADMAP

**Now (shipped):** everything marked ✅ above — Android Automation Edition + web tier + release-admission machinery.

**Next (hardening, [R]):** resolve runBlocking; add crash telemetry decision; README/docs resync; quiet-hours rule; theme persistence; offline mode fully implemented (no further work required); notification preferences surface (per-category toggles exist server-dedupe side; UI ❓).

**Phase 3 (🔮):** complete iOS Companion — implement reservation callables + companionStatus, rebuild iOS app honoring composer vocabulary; entry criteria: Android SLOs held ≥2 quarters.

**Future:** occasions beyond birthdays; channels; shared plans; premium (never gating core); annual relationship digest; referral moments.

---

# 19. OPEN QUESTIONS

| ID    | Question                                                                          | Owner      |
| ----- | --------------------------------------------------------------------------------- | ---------- |
| OQ-01 | Final brand/store identity                                                        | Product    |
| OQ-02 | Crash-reporting vendor/threshold decision                                         | Eng        |
| OQ-03 | Gemini terms written confirmation (policy-suspended kill-switch verified in code) | Legal/Eng  |
| OQ-04 | Launch countries/carrier matrix                                                   | Product    |
| OQ-05 | Minimum-age declaration                                                           | Legal      |
| OQ-06 | Support channel staffing                                                          | Ops        |
| OQ-07 | Deletion-SLA marketing number vs drain-window reality                             | Legal/Ops  |
| OQ-08 | Dev/staging source-set intentionality                                             | Eng        |
| OQ-09 | Settings theme persistence                                                        | Eng/UX     |
| OQ-10 | Notification preference UI scope                                                  | Product/UX |

---

# 20. DOCUMENT CONSOLIDATION NOTES

This SSOT.md consolidates **all** project documentation into a single authoritative source. The following documents have been fully merged and removed:

## 20.1 Consolidated and Removed Documents

| Document                               | Status     | Content Merged Into SSOT Section(s)                                                                          |
| -------------------------------------- | ---------- | ------------------------------------------------------------------------------------------------------------ |
| **PRD.md v3.0**                        | ✅ Removed | §§6 (Feature Inventory), 7 (Business Requirements), 16 (Gap Analysis), 18 (Roadmap)                          |
| **BRD.md v3.0**                        | ✅ Removed | §§7 (Business Requirements), 4 (User Roles), 19 (Open Questions), 21 (Traceability Matrix)                   |
| **PROJECT_ABOUT.md**                   | ✅ Removed | §§2 (Product Overview), 5 (Information Architecture), 14 (User Journeys)                                     |
| **Flow.md**                            | ✅ Removed | §5.1 (Live Navigation), §12 (UI/UX Screen Specifications)                                                    |
| **decision.md**                        | ✅ Removed | §12.2 (Design Tokens), §16.4 (Inconsistencies Resolved)                                                      |
| **DESIGN.md**                          | ✅ Removed | §12 (UI/UX Screen Specifications)                                                                            |
| **ANDROID_INSTALLATION_GUIDE.md**      | ✅ Removed | §3.3 (Android Native Engine), §3.5 (Public Web Tier), Appendix A (Build & Deployment Quick Reference)        |
| **CODEX_GPT_5_6_SOL_MASTER_PROMPT.md** | ✅ Removed | Not applicable (historical LLM session prompt; no product content)                                           |
| **VALIDATION_REPORT.md**               | ✅ Removed | §22 (Verification Checklist), §17 (Risks & Technical Debt), quality gate results in §6 feature status column |

## 20.2 Retained Documents (Non-Redundant References)

The following documents are **retained** because they serve distinct purposes not duplicated in SSOT.md:

| Document                                   | Purpose                            | Why Retained                                                                                                                                               |
| ------------------------------------------ | ---------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **README.md**                              | Repository entry point             | Provides minimal orientation and links to SSOT.md for full specification                                                                                   |
| **SECURITY.md**                            | GitHub security policy             | Standard GitHub security policy file; referenced by GitHub security features                                                                               |
| **DEVELOPER_GUIDE.md**                     | Developer workflow reference       | Contains step-by-step environment setup commands, troubleshooting recipes, and workflow conventions not appropriate for SSOT's product-specification focus |
| **QUICKSTART.md**                          | Minimal quickstart reference       | Provides shortest-path commands for getting started; links to DEVELOPER_GUIDE.md for details                                                               |
| **docs/OPERATIONS_RUNBOOK.md**             | Operational procedures             | Contains runbook playbooks for incident response, on-call procedures, and operational checklists                                                           |
| **docs/\*\_EVIDENCE.md**                   | Release evidence templates/schemas | Structured templates for release artifacts; not product documentation but compliance artifacts                                                             |
| **stitch/SCREEN_MANIFEST.md**              | Design artifact                    | Visual design surface mapping; serves as input to implementation, not product specification                                                                |
| **stitch/IMPLEMENTATION_CROSSWALK.json**   | Design-to-code mapping             | Machine-readable traceability from Stitch screens to code modules                                                                                          |
| **stitch/MASTER_STITCH_PROMPT_LIBRARY.md** | Stitch MCP reference               | Prompt library for generating Stitch designs; tooling artifact                                                                                             |
| **contracts/\*.json**                      | Policy contracts                   | Machine-readable policy definitions consumed by runtime validation code                                                                                    |
| **tools/\*.mjs**                           | Verification scripts               | Executable quality-gate scripts; not documentation                                                                                                         |

## 20.3 SSOT.md as Single Source of Truth

**Effective immediately, SSOT.md is the sole authoritative reference for:**

- Product specification and architecture
- Feature inventory with implementation status
- Business requirements and objectives
- Functional and non-functional requirements
- Business rules catalog
- UI/UX screen specifications
- Data model and integrations
- Key user journeys
- Gap analysis (implemented vs documented vs missing)
- Risks and technical debt register
- Roadmap (Now/Next/Phase 3/Future)
- Open questions
- Traceability matrix

**When conflicts arise between SSOT.md and any other file, SSOT.md prevails.**

**All future development, documentation updates, and decision-making must align with SSOT.md.** Before modifying any retained document listed in §20.2, verify that the change does not contradict SSOT.md. If a contradiction is discovered, update SSOT.md first.

---

---

# Appendix A: Build & Deployment Quick Reference

This appendix provides essential build and deployment commands extracted from the consolidated ANDROID_INSTALLATION_GUIDE.md. For detailed environment setup, troubleshooting, and step-by-step instructions, see DEVELOPER_GUIDE.md.

## A.1 Prerequisites Verification

```bash
# Verify all prerequisites are installed
node -v              # Expected: v24.18.0 or v20.x.x
npm -v               # Expected: 11.6.0 or 10.x.x
javac -version       # Expected: javac 17.0.x (JDK 17 for Android build)
java -version        # Expected: openjdk version "17.0.x"
adb --version        # Expected: Android Debug Bridge version 1.0.41
emulator -version    # Expected: Android emulator version 35.x.x
```

## A.2 Environment Setup

```bash
# Set environment variables (macOS/Linux - add to ~/.zshrc or ~/.bashrc)
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo "/usr/lib/jvm/java-17-openjdk-amd64")
export ANDROID_HOME=$HOME/Library/Android/sdk   # macOS
# export ANDROID_HOME=$HOME/Android/Sdk         # Linux
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/build-tools/36.0.0
source ~/.zshrc
```

## A.3 Repository Setup

```bash
# Clone and install dependencies
git clone https://github.com/your-org/AI-Birthday.git
cd AI-Birthday
npm install

# Install Firebase packages separately
cd backend/functions && npm ci && cd ../..
cd backend/hosting && npm ci && cd ../..
```

## A.4 Quality Gates

```bash
# Run all quality checks
npm run typecheck          # TypeScript compilation check
npm run lint               # ESLint static analysis
npm run format:check       # Prettier formatting verification
npm run format:write       # Auto-format files
npm test                   # Jest test suite (385+ tests)
npm run security:secrets   # Secret scanning
npm run security:licenses  # License allowlist validation
npm run codegen:check      # React Native codegen verification
npm run bundle:check       # Production bundle verification
```

## A.5 Android Build Commands

```bash
# Build Development Debug APK
npm run android:build
# Output: android/app/build/outputs/apk/dev/debug/app-dev-debug.apk

# Run Android JVM unit tests
npm run android:test
# Reports: android/app/build/reports/tests/testDevDebugUnitTest/index.html

# Verify APK security invariants
npm run android:verify

# Full portable check (mobile + backend + hosting)
npm run check:portable
```

## A.6 Running the Application

```bash
# Start Metro bundler
npm start

# Run on Android device/emulator (separate terminal)
export JAVA_HOME=/path/to/openjdk-21
export ANDROID_HOME="$HOME/Library/Android/sdk"
npm run android
```

## A.7 Build Variants

The project supports multiple product flavors and build types:

| Flavor       | Build Type | Purpose                  | Package Suffix |
| ------------ | ---------- | ------------------------ | -------------- |
| `dev`        | Debug      | Local development        | `.dev`         |
| `staging`    | Debug      | Pre-production testing   | `.staging`     |
| `lab`        | Debug      | Controlled experiments   | `.lab`         |
| `production` | Release    | Production builds        | (none)         |
| `e2e`        | Debug      | End-to-end test fixtures | `.e2e`         |
| `smoke`      | Debug      | Smoke test fixtures      | `.smoke`       |

## A.8 Dependency Evidence Refresh

When Android Maven or build-plugin dependencies change:

```bash
# Regenerate multi-flavor lock and artifact-checksum evidence
tools/refresh-android-dependency-evidence.sh
```

This covers dev, staging, lab, production, e2e fixture, JVM tests, lint, instrumentation APKs, and Android Test Orchestrator. Review:

- `android/app/gradle.lockfile`
- `android/buildscript-gradle.lockfile`
- `android/settings-gradle.lockfile`
- `android/gradle/verification-metadata.xml`

## A.9 Native Dependency Advisory Gate

```bash
# Scan four scopes: production runtime, app/build/test graph, build plugins, iOS scaffolding
npm run security:native:android
```

A reported zero means no active mapped OSV advisory at scan time. The production runtime scope must always have zero findings.

---

# 15. APPENDIX B: DEVELOPER ONBOARDING & OPERATIONS

## B.1 Prerequisites Checklist [VD]

- [ ] Node.js `>=24.18.0` (managed via nvm recommended)
- [ ] npm `>=11.6.0`
- [ ] Java JDK 21 (for Android build)
- [ ] Android Studio and Android SDK (API 36)
- [ ] (Optional) Docker for reproducible builds

**Evidence:** [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) · **Status:** ✅ [VC]

## B.2 Step-by-Step Environment Setup [VD]

### Node & NPM

```zsh
nvm use
npm install -g npm@11.6.0
npm ci
```

### Firebase Packages

The Firebase functions and hosting are isolated packages. Ensure you install their dependencies separately:

```zsh
# Functions
cd backend/functions && npm ci && cd ../..

# Hosting
cd backend/hosting && npm ci && cd ../..
```

### Android Setup

Ensure your environment variables are configured:

```zsh
export JAVA_HOME=/path/to/openjdk-21
export ANDROID_HOME="$HOME/Library/Android/sdk"
npm run doctor:android
```

**Note:** The former iOS Setup steps were removed together with the iOS platform; Android is the only supported target.

**Evidence:** [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md), [QUICKSTART.md](QUICKSTART.md) · **Status:** ✅ [VC]

## B.3 Development Workflow [VD]

- **Branching**: Use feature branches (`feature/name` or `bugfix/name`).
- **Linting & Formatting**: `npm run lint` and `npm run format:check` are strictly enforced.
- **Testing**: Run unit tests via `npm run test`.
- **Commits**: Ensure your code passes `npm run check` before committing. Pre-commit hooks will run security scans.

**Evidence:** [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) · **Status:** ✅ [VC]

## B.4 Common Error Resolutions [VD]

### Mismatched Node/NPM Version

**Error**: `npm error engine Unsupported engine`  
**Fix**: Ensure your node version is at least `24.18.0`. Use `nvm install 24.18.0 && nvm use`.

### Android SDK Missing

**Error**: SDK path not found  
**Fix**: Verify `ANDROID_HOME` is set correctly and points to a valid Android SDK installation.

**Evidence:** [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) · **Status:** ✅ [VC]

## B.5 Quickstart Commands [VD]

```zsh
# Using nvm
nvm use
npm install -g npm@11.6.0
npm ci

# Start Metro Bundler
npm start

# Run Android (separate terminal)
export JAVA_HOME=/path/to/openjdk-21
export ANDROID_HOME="$HOME/Library/Android/sdk"
npm run android
```

**Evidence:** [QUICKSTART.md](QUICKSTART.md) · **Status:** ✅ [VC]

## B.6 Quality Checks Reference [VD]

```zsh
npm run check:portable
npm run check
npm run security:secrets
npm run security:licenses
npm run security:native:android
npm run codegen:check
npm run bundle:check
npm run android:test
npm run android:lint
npm audit
npm run backend:check
npm run hosting:check
npm audit --prefix backend/functions --audit-level=high
npm audit --prefix backend/functions --omit=dev --audit-level=moderate
npm audit --prefix backend/hosting --audit-level=high
```

`check:portable` is the complete host-independent workspace gate: shared mobile code, the production JavaScript bundle, Firebase Functions, and the public Hosting site. Native Android build/lint tasks remain explicit platform checks and are enforced separately by CI so the portable command never implies that an Android SDK was exercised when it was not.

When an Android Maven or build-plugin dependency intentionally changes, regenerate the complete multi-flavor lock and artifact-checksum evidence with the pinned Node 24/JDK 21/Android SDK:

```zsh
tools/refresh-android-dependency-evidence.sh
```

The script covers dev, staging, lab, production, the isolated E2E fixture, JVM tests, lint, every corresponding instrumentation APK graph, and the separately installed Android Test Orchestrator. Review `android/app/gradle.lockfile`, `android/buildscript-gradle.lockfile`, `android/settings-gradle.lockfile`, and `android/gradle/verification-metadata.xml` together. Because the Android build runs on macOS locally and Linux in CI, retain the independently verified official Google Maven `aapt2` checksum for both host classifiers when refreshing on only one host.

The JavaScript license gate validates the exact reviewed npm lockfile identities, package counts, integrity records, registry origin, license allowlist, and pinned hashes for packages whose lockfile metadata omits a license. Its optional `--output release-evidence/<set>/<file>.json` path is always resolved from the repository root, rejects symbolic-link path segments, and uses create-only writes so existing release evidence can never be overwritten.

The live [native dependency advisory gate](docs/NATIVE_DEPENDENCY_ADVISORY_GATE.md) scans four truthfully labeled scopes: Android production runtime, the broader Android app/build/test graph, Android build plugins, and iOS CocoaPods scaffolding (Phase 3). It verifies every SBOM against its lock, verifies trunk podspec checksum/source mappings before SwiftURL queries, and requires Maven, npm, and Swift ecosystem canaries. A service outage, incomplete mapping, active finding, or unauthorized exception fails closed. Ordinary CI permits zero exceptions; a reported zero means no active mapped OSV advisory at scan time, not proof that a dependency has no vulnerability.

`npm run check` already creates the production-mode Android JavaScript bundle, so Metro syntax or dependency-transform failures are caught before native packaging. The checked-in CI workflow repeats the exact mobile Node 24 and Functions Node 22 checks, coverage-enforced backend tests, backend emulator tests, Android API 29/API 36/API 37-16 KB instrumentation, production-flavor JVM/lint compilation, Debug and minified unsigned dev-Release builds, isolated UI E2E plus production-path smoke on API 29, and the adversarial cloud evidence validators. It retains short-lived candidate APK/app artifacts, native test results, reports, coverage, licenses, Gradle locks and artifact-verification metadata, JavaScript/Gradle CycloneDX SBOMs, and native OSV reports behind deterministic, mode-aware manifests that hash every retained backend and Android candidate file, including executable modes. These 14-day CI artifacts are diagnostic candidate evidence, not durable signed release provenance. CI needs no signing identity or provider configuration; those remain separate release gates.

`npm run backend:test:emulator` additionally exercises deny-all Firestore rules and server-only transactions against the safe `demo-birthday-autopilot` project. It requires Java 21 and the Firebase emulator download. The mobile Jest and ESLint graphs deliberately exclude `backend/`; the backend owns its separate Vitest and ESLint configuration.

The Android build also exports Room schemas, verifies native unit tests, and keeps release signing outside the repository. Never add a service-account key, OAuth client secret, signing key, database passphrase, access token, or provider API key.

`npm run cloud:evidence:source` prints the production cloud source coordinates only from a clean checkout. `npm run cloud:evidence:validate -- ...` verifies an out-of-repository authority-signed evidence package; it never deploys or changes Firebase/Google Cloud state. Ordinary CI runs the adversarial validator and read-only workflow boundary tests without credentials.

**Evidence:** [README.md](README.md) · **Status:** ✅ [VC]

## B.7 Store and App Store Submission Gate [VD]

Store metadata is a separate fail-closed release input. The committed [store template](tools/store-submission-evidence.template.json) contains truthful EN/HI candidate copy but deliberately contains no developer identity, domain, support email, launch country, screenshot, console answer, artifact digest, policy decision, or approval. `draft`, `submission`, and `release` validation are distinct; a submission package may record pending store review, while the hard release hook requires accepted Play SMS and App Review/login decisions, exact AAB/IPA and screenshot digests, the approved Hosting identity/URLs, current privacy declarations, accessibility evidence, and eight scope-bound approvals.

Run `npm run store:template:check` in ordinary development. Release operators follow the complete [store submission evidence runbook](docs/STORE_SUBMISSION_EVIDENCE.md) and run `npm run store:release:check` with the protected out-of-repository evidence package. Missing or placeholder values cannot be promoted to approval. The store gate never replaces the Android restricted-distribution gate.

**Evidence:** [README.md](README.md) · **Status:** ✅ [VC]

## B.8 Security Policy Summary [VD]

### Reporting

Use the repository host's private security-advisory channel and include:

- the affected source revision and platform/version;
- a minimal reproduction using synthetic contacts and messages;
- observed and expected behavior;
- whether the issue could affect recipient choice, message content, sender/SIM, duplicate prevention, credentials, protected storage, account deletion, or privacy boundaries; and
- any safe diagnostic output after removing tokens, account identifiers, phone numbers, birthdays, messages, request IDs, installation IDs, and opaque coordination values.

Do not send real user data, provider credentials, signing material, HMAC peppers, service-account keys, deletion receipt bearers, or production exploit traffic. If a report requires private artifacts, agree on a protected transfer method with the maintainer first.

### Response Expectations

The maintainer should acknowledge a report within seven calendar days, assign a severity and owner, and coordinate remediation and disclosure timing. A critical issue affecting unintended SMS, duplicate prevention, credential exposure, deletion fencing, or protected contact/message data requires immediate fail-closed containment using [the operations runbook](docs/OPERATIONS_RUNBOOK.md).

### Supported Releases

Only a release explicitly listed as supported in current signed distribution or App Store evidence receives security fixes. Development, staging, lab, unsigned, fixture, historical, and unapproved artifacts are not production releases. The repository currently contains a fail-closed implementation candidate; it does not itself prove that a production release is authorized.

### Safe-Harbor Boundary

Good-faith testing must use accounts, contacts, devices, phone numbers, Firebase projects, SIMs, and carrier plans you own or are explicitly authorized to test. Do not send unsolicited messages, access another person's data, bypass store or carrier policy, degrade shared services, retain private data, or test production deletion receipts without written authorization. This policy does not waive applicable law, platform terms, telecom rules, or third-party rights.

**Evidence:** [SECURITY.md](SECURITY.md) · **Status:** ✅ [VC]

## B.9 Operations Runbook Key Procedures [VD]

### Operating Rules

- Use the exact tier and immutable source/artifact evidence from the approved release record. Never infer a project, package, signing identity, region, or operator account from a local default.
- Never paste callable bodies, Firebase UIDs, installation IDs, request UUIDs, opaque claim/guard values, contact data, phone numbers, birthdays, message text, prompts, tokens, keys, or raw exception objects into an incident system.
- A control-plane outage, uncertain ledger, signing incident, policy suspension, or unexplained duplicate is a stop-new-arms event. Availability never outranks at-most-one submission safety.
- Do not delete or rewrite an Armed claim, destination guard, Arm outcome, deletion tombstone, or unresolved local barrier to make recovery appear clean.
- Never manually delete, release, shorten, or rewrite a logically live `COMMITTED` or ambiguous-sticky iOS composer reservation for availability. A live hold can represent a MessageUI action whose final payload/result is unknowable; Android must remain paused even after Cancel, failure, Unknown, process death, sign-out, revoke, or local wipe.
- Two authorized people review every production containment or recovery change. Retain the content-free change request, exact before/after configuration bytes, source revision, UTC times, operator identities, and approval reference.

### Common Incident Procedure

1. Open a content-free incident record and assign severity, incident commander, privacy/security lead, communications owner, and release owner.
2. Identify the exact tier from signed release evidence. Confirm it again before every console, CLI, IAM, Secret Manager, Firebase, Play, or App Store action.
3. Stop new Android arms by setting that tier's `GlobalControl.armingEnabled` to `false` through the reviewed privileged operator path. Preserve `ledgerGeneration`, sender epochs, claims, guards, and existing outcomes.
4. If ledger integrity is uncertain, also set `continuityState` to `FROZEN`. Never create a new healthy generation merely because records are missing.
5. Verify with an authenticated/App-Check production probe that new claims/arms fail closed. A previously issued permit may still cross before its recorded deadline; disclose that bounded possibility.
6. Preserve immutable, content-free evidence. Do not collect user screenshots or database exports containing private birthday/message data.
7. Apply the scenario procedure below. Restore service only after its exit criteria and the general recovery checklist pass.

### Recovery Checklist

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

**Evidence:** [docs/OPERATIONS_RUNBOOK.md](docs/OPERATIONS_RUNBOOK.md) · **Status:** ✅ [VC]

---

# 16. APPENDIX C: ON-DEVICE AI ROADMAP (GEMINI NANO)

## C.1 Feature Overview 📄 [R]

**Feature Name:** On-Device AI with Gemini Nano  
**Status:** Documented but NOT implemented  
**Priority:** Future consideration (Post-launch optimization)

### Description

Since WishWell is building a mobile application, the architecture can bypass cloud APIs entirely by running the AI directly on the user's phone using **Gemini Nano**, a smaller, highly efficient version of Gemini built directly into modern Android operating systems.

### How It Works

- **Platform:** Android only (requires Android OS with AICore runtime)
- **SDK:** Google AI Edge SDK (AICore)
- **Integration Point:** Replace or supplement cloud-based Gemini API calls in the message drafting pipeline
- **Execution Model:** 100% on-device inference with no network requirement

### Benefits [R]

| Benefit             | Impact                                                   |
| ------------------- | -------------------------------------------------------- |
| **Cost**            | 100% free — no API keys, no cloud server costs           |
| **Privacy**         | No data leaves the device; enhanced privacy posture      |
| **Latency**         | Eliminate network round-trip; faster response times      |
| **Offline Support** | Works when the user does not have an internet connection |
| **Compliance**      | Simplifies GDPR/data residency requirements              |

### Limitations & Trade-offs [R]

| Limitation                 | Mitigation Strategy                                                                                                                                             |
| -------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| **Reduced Capability**     | Gemini Nano is designed for specific tasks (summarization, smart replies, text generation) and is not as powerful as Gemini 1.5 Pro models running in the cloud | Restrict Nano usage to simple tone adjustments and template filling; reserve cloud models for complex personalization if needed |
| **Platform Lock-in**       | Only available on Android devices with AICore support (Android 14+ on select devices)                                                                           | Maintain hybrid architecture: Nano-capable devices use on-device; others fall back to cloud Gemini                              |
| **Model Updates**          | Tied to OS updates; slower iteration than cloud models                                                                                                          | Design abstraction layer to swap model providers without UX changes                                                             |
| **Memory/CPU Constraints** | On-device inference consumes local resources                                                                                                                    | Implement lazy loading; cache results; provide manual refresh option                                                            |

### Implementation Requirements (If Pursued) [R]

1. **Dependency Addition:**

   ```kotlin
   // android/app/build.gradle
   implementation("com.google.ai.edge:core:1.0.0") // Example version
   ```

2. **Native Module:** New Kotlin TurboModule exposing:

   - `initializeNano()` — one-time setup
   - `generateSuggestion(context: String, tone: Tone): String` — synchronous inference
   - `isNanoAvailable(): Boolean` — capability check

3. **JavaScript Interface:**

   ```typescript
   interface OnDeviceAI {
     isAvailable(): Promise<boolean>;
     generateDraft(
       contactName: string,
       tone: 'warm' | 'simple' | 'cheerful',
     ): Promise<string>;
   }
   ```

4. **Fallback Strategy:**

   - Primary: Gemini Nano (if available)
   - Secondary: Cloud Gemini API (existing implementation)
   - Tertiary: Built-in templates (always available)

5. **SBOM/Security Review:** AICore runtime must pass native dependency advisory gate before inclusion

### Decision Criteria for Implementation [R]

**Proceed if:**

- Post-launch user feedback indicates cost concerns with cloud Gemini
- > 30% of user base has Nano-capable devices
- Privacy compliance requirements tighten
- Cloud API rate limits become a bottleneck

**Defer if:**

- Launch timeline is critical (adds complexity)
- Cloud Gemini costs remain negligible at projected scale
- Device fragmentation creates unacceptable support burden

### Current Architecture [VC] — Native Android Gemini (Device-Only)

**As of v0.1.0**, WishWell uses **on-device native Android Gemini API** for AI-powered message drafting via `firebase-ai` SDK. This is a privacy-preserving, device-only architecture:

- **Native Implementation:** `android/app/src/main/java/.../gemini/AndroidGeminiSuggestionGateway.kt`
- **Firebase AI SDK:** `firebase-ai` 17.13.0, client-side only
- **Privacy Boundary:** Prompts exclude PII, generation unreachable from send workers
- **Rate Limiting:** Max 8 retained rate scopes, 15s timeout
- **Fallback States:** Template-based suggestions when Gemini unavailable
- **No Backend AI:** Server functions contain zero Gemini code (verified)
- **Dead Code Removed:** JavaScript AI layer (AIGateway.ts, GoogleAIProviderAdapter.ts, AIProviderPort.ts — 1,164 lines) deleted 2026-08-29

**Files Involved in Current Implementation:**

- `android/app/src/main/java/com/yashsomani/birthdayautopilot/gemini/AndroidGeminiSuggestionGateway.kt` — native gateway
- `android/app/src/main/java/com/yashsomani/birthdayautopilot/gemini/AndroidGeminiOperationalGate.kt` — operational gate
- `android/app/src/main/java/com/yashsomani/birthdayautopilot/gemini/GeminiCandidateProvenanceRegistry.kt` — provenance tracking
- `contracts/gemini-prompt-policy-v2.json` — prompt governance
- `src/infrastructure/native/BirthdayNativeAdapter.ts` — React Native bridge port

### Evidence & References

- **Implementation Status:** ✅ IMPLEMENTED, ⚠️ NOT_RUNTIME_VERIFIED, 🚫 NOT_DEPLOYED
- **Architecture:** Device-only Gemini via native Android bridge
- **Verification Required:** Runtime testing on real Android devices with Gemini API enabled
- **Recommendation:** Proceed with runtime verification before considering backend AI or iOS expansion

---

**END OF SSOT.md**

_This document consolidates all prior project documentation including:_

- _PRD.md v3.0 (superseded)_
- _BRD.md v3.0 (superseded)_
- _PROJECT_ABOUT.md (superseded)_
- _Flow.md (superseded)_
- _decision.md (superseded)_
- _DESIGN.md (pointer superseded)_
- _README.md (merged)_
- _DEVELOPER_GUIDE.md (merged)_
- _QUICKSTART.md (merged)_
- _SECURITY.md (merged)_
- _docs/OPERATIONS_RUNBOOK.md (merged)_

_All information from these documents has been preserved and integrated. Refer exclusively to this SSOT.md for authoritative project information._
