# WishWell (Birthday Autopilot) — Product Requirements Document

|                     |                                                                                                                                                                                                  |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Document**        | Product Requirements Document — v3.0 (Codebase-Grounded)                                                                                                                                         |
| **Product**         | WishWell · package `birthday-autopilot` v0.1.0 · appId `com.yashsomani.birthdayautopilot`                                                                                                        |
| **Source of truth** | This document is reverse-engineered from the repository codebase on 2026-08-22. Documentation (`PROJECT_ABOUT.md`, `Flow.md`, `decision.md`, `stitch/*`) is treated as supporting evidence only. |
| **Supersedes**      | `PRD.md` v2.0; conflicts with `PROJECT_ABOUT.md` are resolved here and logged in Gap Analysis (§16).                                                                                             |

## Status & Evidence Labels

Every item carries one **Implementation Status** and one **Evidence** label:

| Status | Meaning                                          |
| ------ | ------------------------------------------------ |
| ✅     | Implemented — verified in code                   |
| ◐      | Partially implemented                            |
| 📄     | Documented but NOT implemented                   |
| 🆕     | Implemented but missing from prior documentation |
| 🔮     | Planned / future                                 |
| ❓     | Unclear — requires confirmation                  |

Evidence: **[VC]** verified from codebase (file cited) · **[VD]** verified from documentation · **[I]** inferred · **[A]** assumption · **[R]** recommendation · **[U]** unknown.

---

# 1. PRODUCT OVERVIEW

## 1.1 Purpose [VC]

"Autonomous birthday SMS automation system for Android devices" (package.json:4). WishWell syncs Google Contacts birthdays, lets the user enroll people and approve exact message payloads, then delivers each approved wish as a real SIM-originated SMS on the birthday via a server-coordinated claim → arm → submit → observe pipeline — unattended on Android, with protocol-level provisions for a future iOS companion that requires the user to tap Send.

## 1.2 Vision / Mission [VD]

Vision: the most trusted way to maintain relationships through timely, personal birthday messages — AI assists, the human decides. Mission: make thoughtful birthday communication effortless without feeling automated. (Carried from PROJECT_ABOUT.md §1.3–1.4; unchanged by code review.)

## 1.3 Problem Statement

Forgotten birthdays cause guilt; generic messages feel robotic; reminder apps fire after the miss; contact-access apps erode trust; competitors overpromise automation on platforms that forbid it. **[VD]**

## 1.4 Value Proposition

> Set it once. Approve what matters. Never miss a birthday.

Verified differentiators: human approval of exact payload before any send (✅ enforced server+client), structural duplicate-send prevention via server-issued occurrence keys and destination guards (✅), truthful outcome copy ("Sent from this phone; delivery not confirmed") (✅), deletion-grade privacy incl. SQLCipher local DB, deny-all Firestore, opaque HMAC aliases, content-free deletion receipts (✅), bilingual EN/HI (✅).

## 1.5 Target Users

Primary: busy professionals (28–45) who want set-and-forget reliability. Secondary: relationship curators wanting control, privacy-conscious users, less-technical users. Personas P1–P4 carried from prior spec **[VD]**; no persona-specific code paths found beyond accessibility/large-text and Hindi support **[VC]**.

## 1.6 Product Scope (as implemented)

**In scope ✅:** Android Automation Edition (flavors e2e/smoke/dev/staging/lab/prod); Google sign-in + read-only contacts; enrollment & approvals; template + Gemini drafting; policy editor; test mode; server-coordinated unattended SMS; sender transfer; attention/repair; activity log; diagnostics export; privacy operations incl. full deletion; public web tier (/ , /delete/, /privacy/, /terms/, /support/) bilingual EN/HI.

**Out of scope ✅:** iOS app build (removed; commit `61882f9`, workflows deleted `2b3a3b4`) though client-side companion _protocol_ remains (§7.12); contact writes; multi-account; email/calling/social; bulk/marketing messaging; monetization.

---

# 2. SYSTEM ARCHITECTURE (VERIFIED)

## 2.1 Layered TS architecture [VC]

```
src/domain/        pure models, branded IDs, enums, validators (no IO)
src/application/   11 role ports aggregated as BirthdayNativePort; PROJECTION_AREAS
src/features/live/ production screens driven by native projections
src/features/{setup,home,people,activity,settings}/  fixture-only preview stack (__DEV__)
src/infrastructure/native/  BirthdayNativeAdapter — single bridge implementation
src/design-system/ tokens/theme.ts + accessible primitives
src/localization/  i18next; EN/HI release; ar-XB pseudo-RTL dev fixture
```

State management: **projection hooks + invalidation events**, no Redux/Zustand. Every read returns `NativeResult<ProjectionEnvelope{contractVersion:1, revision, generatedAt, value}>`; every mutation passes `expectedRevision` (optimistic concurrency). Native pushes invalidations `{revision, areas[]}`; screens reload intersecting areas on foreground too (`useLiveProjection.ts`).

## 2.2 JS↔Native contract [VC]

Single TurboModule `specs/native/NativeBirthday.ts`:

- `getProjection(area, requestJson)` — 13 areas: bootstrap, setup, home, eligibility, readiness, account, contacts, messages, automation, activity, privacy, route, notifications.
- `executeUserIntent(intent, expectedRevision|null, payloadJson)` — ~40 named intents (e.g., `activate`, `authorize-contacts`, `confirm-privacy-action`, `begin-sender-transfer`, `repair-lifecycle-state`, `clear-activity`, `generate-suggestions`).
- Event emitter pair for invalidations/routes. Envelope ≤ 1 MiB; double Zod validation; decode failure collapses to `internal{NATIVE_CONTRACT_INVALID}` (`decodeNativeResponse.ts`).

JS exposes **no** send/schedule/retry APIs — delivery authority lives natively. **[VC]**

## 2.3 Android native engine [VC]

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

## 2.4 Cloud control plane [VC]

Firebase project region **`asia-south1`**; Firestore rules **deny-all** — clients touch data only through **16 callable functions** (App Check enforced + consumed, 30 s timeout, maxInstances 20, concurrency 20): registerAndroidInstallation, renewSenderLease, changeAccountMode, claimOccurrence†, claimTest†, armAttempt, getArmStatus, reportTestOutcome, authorizeSafeRetry, beginSenderTransfer, completeSenderTransfer, requestAccountDeletion, accountDeletionReceipt, resetContactDerivedState, releaseAndroidSender, coordinationLifecycleStatus († require Cloud KMS secret `COORDINATION_HMAC_KEYRING`). Plus 2 scheduled sweeps: `sweepDeletionDrains`, `sweepCoordinationOperations`. Identity aliases are HMAC-SHA256 derived under domain `birthday-autopilot/control-plane/v1` with current+previous key rotation. **No raw contact/message fields ever reach the server** (privacy-architecture tests enforce). Gemini is absent server-side.

**Server-side declared-but-absent**: `companionStatus` (whitelisted in `FirebaseCoordinationClient.kt` L29 but unimplemented), `acquireIOSComposerReservation` / `commit…` / `release…` (docs + TTL collection `iosComposerReservations` exist; zero implementation). ◐ iOS-companion protocol is half-built.

## 2.5 Public web tier [VC]

Vite multi-page static site: `/`, `/delete/`, `/privacy/`, `/terms/`, `/support/`, 404 — bilingual EN/HI. Deletion flow: reCAPTCHA Enterprise App Check → in-memory Firebase Auth persistence → `reauthenticateWithPopup` → callable `requestAccountDeletion {contractVersion:1, requestId:<uuid>}` → receipt id held in **tab sessionStorage only**. Static tests forbid localStorage/indexedDB/cookies/console/innerHTML; strict CSP; fails closed without `public/runtime-config.json`.

---

# 3. USER ROLES & PERMISSIONS [VC]

The app has no multi-user roles; roles derive from **device/installation state** (server-enforced `InstallationState`: ACTIVE / STANDBY / REVOKED):

| Role                                | Who                                                     | Capabilities                                                                                                 | Restrictions                                                                   |
| ----------------------------------- | ------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------ |
| **Owner on ACTIVE (sender) device** | Signed-in user whose installation holds the fence       | Full: setup, sync, enroll/approve/pause/exclude, policy edit, test sends, activation, transfer-out, deletion | Sends gated by readiness (test/activation/birthday gates), policy caps, budget |
| **Owner on STANDBY device**         | Same account, second Android                            | View projections, privacy ops, can _begin_ sender transfer                                                   | Cannot arm/send; activation blocked (`active-sender-other-device`)             |
| **Pre-auth user**                   | Before `continueWithGoogle()`                           | Welcome/compatibility, initiate sign-in, view eligibility issues                                             | Nothing else                                                                   |
| **Web visitor**                     | Browser at /delete/                                     | Re-authenticated deletion of own account; receipt lookup                                                     | No other data access; fail-closed without runtime config                       |
| **Release authority (external)**    | Holder of Ed25519 pin `distribution-authority-pin.json` | Signs distribution approvals unlocking restricted-SMS BuildConfig flags                                      | Out-of-repo key; approvals expire (`validUntil`)                               |

---

# 4. INFORMATION ARCHITECTURE [VC]

## 4.1 Live navigation (`LiveAppShell.tsx:59–91`)

**Bottom tabs (3): Home · People · Settings** — confirms decision.md/Flow.md; supersedes PROJECT_ABOUT §6 four-tab spec (Gap G-01).

**Stack routes above tabs:** `Person {contactId}` · `Activity` · `ActivityDetail {activityId}` · `Attention` · `Automation` · `Diagnostics` · `HelpLegal` · `Message` · `Privacy` · `Schedule`.

Boot chain: `NativeAppBoundary` → bootstrap projection → if setup incomplete OR lifecycle recovery pending → `LiveSetupScreen` (step machine over 11 SETUP_STEPS: compatibility → google-account → contacts-disclosure → sync-summary → recipient-selection → message-and-policy → test-review → test-progress → reliability-repairs → activation-review → complete) else → `LiveAppShell`. Notification taps arrive via native route events: `automation-review(source:birthday-reminder)` or `attention`.

Fixture stack (dev-only): separate react-navigation tree mirroring the IA with synthetic data for design/localization preview (incl. ar-XB pseudo-RTL, platform override).

## 4.2 Deep links / external surfaces [VC]

Help/Legal opens hosted `${baseUrl}/privacy|terms|support|delete` via Linking (`LiveHelpLegalScreen.tsx`). No custom-scheme deep links found in live stack (v1.0's `wishwell://` scheme: 📄 not implemented ❓).

---

# 5. FEATURE INVENTORY (MASTER TABLE)

Statuses: ✅ 📄 🆕 ◐ 🔮 ❓ as defined. Priority reflects launch criticality observed from gating.

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
| F-44 | Standby/hibernation diagnostics (diagnose-only; **no exemption request**)                                                                                                                        | ◐ 🆕   | P1         | AppStandbyBucketDiagnosticPolicy; hibernation-status-unsafe codes                                                                                                                                    |
| F-45 | Battery-optimization exemption request flow                                                                                                                                                      | 📄     | P1         | **not in code** (v1.0 JOURNEY-09/UI-013)                                                                                                                                                             |
| F-46 | Free-form user SIM selection picker                                                                                                                                                              | 📄     | P1         | superseded by F-21 fail-closed default-SIM policy                                                                                                                                                    |
| F-47 | Late-send next-morning option                                                                                                                                                                    | 📄     | P2         | actual policy enum: none \| same-day-grace only                                                                                                                                                      |
| F-48 | Product analytics event stream (v1.0 §21 catalog)                                                                                                                                                | 📄     | P2         | **zero analytics SDK in repo** (grep-verified)                                                                                                                                                       |
| F-49 | Crash reporting / FCM push                                                                                                                                                                       | 📄 ❓  | P2         | none found; docs ambiguous ("FCM for cloud events")                                                                                                                                                  |
| F-50 | iOS Companion Edition (reminders + composer handoff)                                                                                                                                             | ◐      | 🔮 Phase 3 | client protocol present (DeliveryPlatform.IOS_COMANION\*, composer activity kinds, IOS_COMPOSER_RESERVED hourly recheck, 72 h reservation constant); no iOS app; server reservation callables absent |
| F-51 | Offline degraded mode                                                                                                                                                                            | ◐      | P2         | local-first reads work offline; network-offline reason codes; no explicit offline UX spec                                                                                                            |
| F-52 | Custom-scheme deep links (`wishwell://*`)                                                                                                                                                        | 📄     | P3         | not found in live stack                                                                                                                                                                              |
| F-53 | Manual web-deletion fallback without Google login                                                                                                                                                | 📄     | P2         | actual: reauth popup mandatory, fails closed                                                                                                                                                         |

\* Exact spelling `DeliveryPlatform.IOS_COMPANION` (core/model/DeliveryPlatform.kt).

---

# 6. DETAILED FEATURE SPECIFICATIONS

Format per feature: purpose · workflow · business rules/validation · edge & failure cases · dependencies · key ACs. All ✅ items verified [VC].

## 6.1 Onboarding & Compatibility (F-01/02/03)

- **Workflow:** compatibility check (telephony features required=true, Google Play services, installer allowlist, distribution channel) → eligibility card listing blocking reasons → Continue with Google → contacts disclosure → authorize → sync summary.
- **Rules:** unsupported/limited devices surface `platform-unsupported`, `google-play-services-missing`, `installer-allowlist-missing`, `distribution-channel-unapproved` (blocking ALL gates — observed verbatim in smoke fixture); cost-consent banner shown (recipient-pays-nothing / carrier-charges-sender framing **[VD]** copy exists in liveResources).
- **Failure:** denied consent → signed-out(retainedSetup: none|same-account-only); permanent permission denial classified and persisted; lifecycle cleanup-pending reroutes to repair.
- **AC:** given unapproved distribution build, when bootstrap loads, then all three gates report blocked with reason `distribution-channel-unapproved`.

## 6.2 Contacts Sync (F-04/05/06)

- **Workflow:** People API `/v1/people/me/connections` (fields names,birthdays,phoneNumbers,metadata; READ_SOURCE_TYPE_CONTACT; LAST_MODIFIED_ASCENDING; sync-token incremental) → staged transactional Room commit with NonCancellable rollback → buffer zeroing → reconcile.
- **Bounds:** page-count/byte/duration/person limits; duplicate-resource detection; SHA-256 parameter-fingerprint guards incremental continuity; ≤3 worker attempts.
- **Freshness bands (contracts/contacts-freshness-policy-v1.json):** NORMAL ≤ 7 d; STALE_WARNING > 7 d; PAUSE (>30 d) disallows automation.
- **Edge:** expired sync token → one automatic full resync; source contact deleted → issue `source-contact-deleted`; Feb 29 without leap policy → blocked `leap-policy-required` (user must choose feb-28/mar-01/**skip**).
- **AC:** given incremental sync with tampered parameters, when fingerprint mismatches, then full resync occurs and no partial merge commits.

## 6.3 People Directory & Enrollment (F-07/08)

- Search field (trailing clear button, PR #168); filter chips; cursor pagination (`PageCursor`). Per-person actions pass through **prepareEnrollmentReview → confirmEnrollment** two-phase pattern with expectedRevision. Destination blocking (`blockRecipientDestination`) prevents sends to a number while keeping enrollment.
- Contact issue taxonomy (14 codes) drives Needs-attention filter: birthday-missing/conflict/choice-required, leap-policy-required, phone-missing/choice-required/ambiguous-region/invalid/blocked-form, duplicate-destination, stable-source-missing, safe-given-name-missing, source-contact-deleted, approval-invalid.
- Phones masked everywhere except explicit choice/confirmation moments **[VC design-system usage]**.

## 6.4 Messaging: Templates, Editor, Gemini (F-09/10/11)

- 4 built-in templates (en/hi × personalized `{given-name}` / generic); placeholder cardinality enforced (given-name:1, generic:0).
- Semantic policy v2 classifier bans URLs, promotional content, tracking, bidi control chars, sensitive content; language mismatch detection (`template-language-mismatch`).
- Gemini request contains **only**: language, tone, relationship, milestone, name-style hint, segment limit (prompt policy JSON). Candidates 1–3, dedupe (case-insensitive en), maxOutputTokens 512, 15 s timeout. States: requesting → candidates | fallback(network-offline|coordination-unavailable|policy-suspended) | failed(unknown-native-value|internal-contract-invalid). Provider text never persisted/logged; candidates deduped; generation impossible from send worker.
- **AC:** given any generated candidate, when payload inspected, then it contains no phone/email/contact-ID and ≤ 2 segments after plan computation.

## 6.5 Approvals & Invalidation (F-12/13)

- Snapshot binds recipient identity, exact text, window, late policy, SIM binding epoch, segment plan, disclosure version, sender epoch, permission policy. Any of **12 change classes post-approval → invalidated(reasons[])**; sends blocked until re-approval.
- Batch approval: multi-select ready recipients → prepareApprovals returns per-item review handles → confirmApprovals applies atomically per item; partial failures surfaced individually.
- **AC:** given approved person whose phone later changes in source, when reconcile runs, then approval shows invalidated(phone-changed) and birthday gate blocks.

## 6.6 Policy & Scheduling (F-14/15)

- Policy fields: dailyCap, window (start/end), latePolicy(none|same-day-grace), segmentCap(≤2), sim binding, per-birthday confirmation requirement. Validation codes: invalid-daily-cap, invalid-segment-cap, invalid-window, window-capacity-conflict.
- Preview simulates 400 days of occurrences with issues before save.
- Server mirrors budget: `BIRTHDAY_ARM_CAP=20/day` (UTC day window), `TEST_ARM_CAP=3/day`.
- **AC:** given cap 20 already armed today, when another claim arrives, then server refuses with budget reason and orchestrator schedules successor (WorkerAttentionPolicy: hourly successor for reservation-class, 30 s floor otherwise).

## 6.7 Test Mode (F-17)

- Isolated guard family from birthday sends; requires test gate allowed; completion feeds activation-review; receipt-invalidated phase covers stale evidence; budget-exhausted blocks further tests until UTC day rolls.

## 6.8 Delivery Pipeline (F-18…22) — core promise

- Phases (25): planned→prepared→scheduled→claimed→coordination-blocked?→cloud-claimed→arm-reconciling→coordination-unknown?→cloud-armed→armed-suppressed?→submission-barrier-consumed→submitted→sent-from-device→(delivered|delivery-failed|partial-delivery|delivery-unknown|partial-unknown|unknown)→terminal(retryable-failure→retry-exhausted|permanent-failure|skipped|missed|cancelled).
- Constants [VC backend/domain/model.ts]: MAX_LEASE_MS=10 min; CLAIM_AUTHORIZATION_MS=10 min; MAX_SUBMIT_AFTER_ARM_MS=1 min; ARM_SPACING_MS=5 min; sent-evidence grace 15 min; clock tolerance 5 min.
- Truthful outcomes: delivered only from carrier/callback evidence; else honest partial/unknown states. Scenario evidence schema enforces `duplicate submissions const 0`, `carrierDeliveryClaimed const false`.
- Failure handling: SIM missing (`no-active-sim`,`sim-changed`,`sim-invalid`), radio, OEM kills (durable wake ledger + boot receiver rescheduling), background restrictions surfaced as scheduler-delayed/attention, never silently swallowed.
- **AC:** given armed submit with prior successful ledger entry for same occurrenceKey, when submit attempted again, then destination guard blocks and no second SMS dispatches (server + local permit).

## 6.9 Sender Transfer (F-24)

- New device: prepareSenderTransfer (review consequences + TTL'd lease) → beginSenderTransfer → server TRANSFER_PENDING + drain window → old device demoted STANDBY after drain → completeSenderTransfer bumps epoch → **test-required** state before automation re-activation. Ambiguous outcomes reconciled; failed transfers retryable; resume paths for remote-pending/draining across process death.
- **AC:** given transfer completing, when old device attempts arm mid-drain, then refused (fence/epoch) with zero duplicate risk.

## 6.10 Privacy Operations & Deletion (F-31…35, F-43)

- Two-phase destructive pattern everywhere: prepareAction(kind, expectedRevision) → consequence-key whitelist validated client-side → confirmAction(handle) → authoritative triple-reload with deep corroboration before success UI; protected work retired on background/invalidate/unmount; short-TTL review leases prevent stale authorization.
- Operation state machine: queued → pausing → local-wiping → remote-draining → (remote-unknown(sameAccountRetryAvailable) | remote-pending) → verifying → complete | failed(resumable).
- Deletion guarantees: `localDataErased:true`; `externalSmsCopiesNotErased:true` (honest scope statement). Tombstone DRAINING fenced saga; scheduled sweep verifies Auth deletion; receipts content-free, 365 d retention, lookup by UUID.
- clear-gemini-templates wipes AI suggestion history locally; clear-activity wipes local activity feed (both 🆕).
- **AC:** given delete-account confirmed, when remote coordination unavailable, then op enters remote-unknown with same-account retry available and local wipe completes only per policy; receipt retrievable later by id.

## 6.11 Diagnostics & Support (F-26/28)

- previewDiagnostics produces private-content-free bundle (client validates: excludesPrivateContent, safe reason codes only, UTC instants) → shareDiagnostics(expectedRevision) hands off to OS share sheet. Health checklist derives from capability codes (account/contacts/SMS-SIM/background).

## 6.12 iOS Companion Protocol Surface (F-50) ◐

Client/server scaffolding that exists today: `DeliveryPlatform.IOS_COMPANION`; activity phases composer-opened/cancelled/failed/outcome-unknown/reported-sent; server reason `IOS_COMPOSER_RESERVED` fences Android sends during an iOS App Review window (constant 72 h; hourly recheck policy uses one-hourly successor rather than network floor); TTL collection reserved. Missing: iOS application, server reservation callables, companionStatus implementation. Treated as Phase 3 🔮.

---

# 7. FUNCTIONAL REQUIREMENTS (CONSOLIDATED)

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

# 8. BUSINESS RULES CATALOG (CODE-VERIFIED CONSTANTS)

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

Legacy BRULE-001…035 from PROJECT_ABOUT remain directionally accurate but several are superseded precisely by the table above (notably BRULE-008 default-date, BRULE-012 ten-parts, BRULE-013/014 window/late semantics, BRULE-033 battery exemption, BRULE-035 manual deletion fallback). Where they conflict, **this table wins**.

# 9. NON-FUNCTIONAL REQUIREMENTS

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

# 10. UI/UX SCREEN SPECIFICATIONS [VC]

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

# 11. DATA MODEL & INTEGRATIONS

Local Room 37 entities grouped: control (account/installation-binding/consent/coordination-permit/send-attempt/callback-token/delivery-event/outcome-projection/reset-safety/clock-trust/readiness), contact (people-staging\*), approval, occurrence, activity, ledger (SafetyLedgerDao append-only). Server collections: accounts/{uid} (+installations, occurrenceClaims, testClaims, occurrenceKeys, destinationGuards, claimRequests, armOutcomes, armBudgets), deletionTombstones, coordinationPresence, coordinationOperationFences, coordinationOperationReceipts(+Latest), globalControl/current; TTL `cleanupAt` ×13 groups; zero composite indexes.

Integrations: Google People API (read), Credential Manager OAuth (contacts.readonly incremental), Firebase Auth+AppCheck(Play Integrity)+Callables(asia-south1), firebase-ai (Gemini 3.5-flash via vertex-ai/global), reCAPTCHA Enterprise (web), Android Telephony/SmsManager/SubscriptionManager/WorkManager/AlarmManager-equivalent scheduling, OS share sheet, Linking (hosted pages). **No FCM, no third-party analytics.**

# 12. KEY USER JOURNEYS (VERIFIED)

1. **First-run to activated automation:** welcome/compatibility → Google → contacts disclosure+sync → recipient selection (all off) → message-and-policy (template/Gemini, batch approve available) → test-review → test-progress (≤3/day) → reliability-repairs (if issues) → activation-review → activate. Deferred exits preserved at each step.
2. **Birthday send (unattended):** planner marks occurrence → reconcile claims → server issues keys/guards → arm within spacing → barrier → SmsManager submit (≤2 parts, default SIM sub) → callback observe → outcome worker classifies → activity row + optional silent success; failures → attention notification (deduped) → tap routes to automation-review or attention.
3. **Repair:** attention list → issue detail (plain-language safe reason) → native action handle (open settings / re-auth / choose phone / confirm leap policy) → recheck clears.
4. **Device replacement:** new install registers STANDBY → transfer prep/begin → drain → complete → mandatory test → reactivate.
5. **Deletion:** in-app two-phase (or web reauth flow) → local wipe → remote drain/tombstone → sweep verifies Auth deletion → receipt id retained by user.
6. **Recovery after revocation:** reconnect-required account state → repair lifecycle (identity lease) → resume operation or clean sign-out-wipe.

# 13. USER STORIES (with acceptance criteria)

US-01 As a new user, I want the app to tell me immediately whether my device/network/distribution supports automation, so that I don't invest time in an impossible setup. — AC: blocked eligibility lists every failing check with reason code and help link; no step advances past hard blockers.
US-02 As a privacy-conscious user, I want contacts access limited to read-only birthdays, so that nothing of mine can be altered or exfiltrated. — AC: OAuth consent requests exactly contacts.readonly incrementally; write scopes impossible; server stores zero contact fields (privacy tests green).
US-03 As a busy professional, I want to approve many people at once, so that activation takes minutes not hours. — AC: batch approval processes N ready recipients with per-item success/failure and no partial silent states.
US-04 As a careful user, I want my approval to break automatically if anything material changes, so that a stale wish never sends. — AC: each of the 12 invalidation classes flips approval to invalidated with reason; birthday gate blocks until re-approved.
US-05 As a dual-SIM owner, I want predictable SIM usage, so that wishes never bill the wrong plan. — AC: sends execute only on the active default-SMS subscription; deviation triggers reconcile and blocks with sim-\* reason.
US-06 As a skeptical user, I want delivery claims to be honest, so that I can trust the activity log. — AC: statuses beyond sent-from-device never assert carrier delivery; partial/unknown rendered distinctly.
US-07 As a user replacing phones, I want to move automation safely, so that nobody gets zero or two messages. — AC: transfer drain fences old device; completion forces a test; duplicates remain structurally 0.
US-08 As a privacy-conscious user, I want to erase everything from any browser, so that leaving leaves no residue. — AC: /delete/ works with reauth; receipt issued; server sweep verifies Auth deletion ≤ SLA; external SMS copies disclosed as out of reach.
US-09 As a Hindi-speaking user, I want the whole product in Hindi, so that setup is not intimidating. — AC: hi strings cover all live namespaces; store locale hi-IN evidence required; E2E asserts Hindi surfaces.
US-10 As a low-vision user, I want large-text usable primary actions, so that I can activate independently. — AC: large-text E2E reaches setup primary actions scrolled; 200% scaling evidence required for store.

# 14. ANALYTICS & OBSERVABILITY

**Current state [VC]:** No product analytics, crash reporting, or push infrastructure. Telemetry equivalents: DB heartbeats, durable wake ledger, attention reason codes, performance-budget evidence at release time, CI matrices, server-side operational receipts. The v1.0 event catalog (onboarding_started…account_deleted) is 📄 entirely unimplemented.

**Recommended [R]:** privacy-preserving local counters exported via the existing scrubbed-diagnostics channel first (zero new SDKs), then opt-in aggregate funnel telemetry (activation steps, approval rate, invalidation reasons, gate-block reasons, transfer completions, deletion SLA) once a privacy design review approves vendor; add Play Vitals / Console statistics as interim sources. Crash reporting decision required (❓) — ANR vector noted in §16 debt.

# 15. GAP ANALYSIS

## 15.1 Implemented but Undocumented (🆕 highlights)

Batch approval (F-13) · message milestones (F-11) · relationship context enum (F-11) · policy caps daily/segment + 400-day preview (F-14/15) · clear-gemini-templates & clear-activity (F-32) · sign-out-retain variant · destination blocking/unblock/restore (F-08) · clock-trust (F-42) · reset-safety (F-43) · installer/distribution enforcement (F-41) · today-occurrence choices (F-25) · people pagination · notification tap routing ring · ar-XB pseudo-RTL fixture · FLAG_SECURE behaviors · asia-south1 residency · content-free receipts · standby/hibernation diagnostics · test budget 3/day · birthday arm budget 20/day · skip leap policy · 24-kind activity taxonomy incl. composer phases · lifecycle-repair identity lease · single-consume route semantics · smoke/e2e flavor isolation.

## 15.2 Documented but Not Implemented (📄)

Battery-optimization exemption request (F-45) · free SIM picker (F-46) · next-morning late policy (F-47) · analytics event stream (F-48) · crash reporting/FCM (F-49) · wishwell:// deep links (F-52) · manual non-Google web-deletion fallback (F-53) · README iOS artifact pipeline section (files deleted) · PROJECT_ABOUT "encrypted cloud backup of contacts" (false — server stores no contacts) · "FCM cloud-side events" · UI-022 sender-device name/last-seen display ❓ · v1.0 DATA-model fields (parts/simId/reconciled) replaced by richer reality · BRULE-008 default feb-28 (actual: must choose; skip possible) · BRULE-012 ten-part ceiling (actual 2).

## 15.3 Partially Implemented (◐)

iOS Companion Edition (protocol scaffolding only; server callables absent; TTL orphaned) · offline mode (implicit local-first; no dedicated UX) · standby diagnostics (observe, don't request exemption).

## 15.4 Inconsistent (code vs docs)

Tone sets (warm/simple/cheerful vs casual/professional/short) · approval-invalidation breadth (12 classes vs docs' phone/birthday) · sender fencing model (lease/epoch/drain vs simple flag) · deletion flow (drain saga vs immediate purge ≤48h — actual SLA governed by drain deadlines; ❓ confirm marketing SLA) · navigation (3-tab confirmed; Flow.md screen IDs S13/S14 map loosely to Schedule/Automation screens) · "3 candidate variations" (actual 1–3).

## 15.5 Missing Requirements (should be specified)

Notification quiet-hours policy · theme persistence semantics · widget/shortcut absence (confirm non-goal) · reply-handling expectation copy · data-retention UX for local activity (auto-prune horizons visible?) · support-contact SLA copy · store-listing content ownership.

## 15.6 Technical / Product Debt

`runBlocking` in BirthdayNativeModule (home/account/contactsSync payloads) — ANR risk on slow IO **[VC]** · no crash telemetry blinds OEM-field diagnosis · README staleness (iOS refs, Xcode notes) misleads contributors · dev/staging flavors lack source sets (silent main inheritance — intentional ❓) · companionStatus dead allowlist entry · iosComposerReservations TTL orphan · Settings theme persistence unclear · single retry may under-deliver on flaky networks (product tradeoff, deliberate).

# 16. RISKS & EDGE CASES (TOP)

OEM aggressive killers delay sends despite wake ledger (mitigate: diagnostics + guidance; exemption request still absent) · runBlocking ANRs on low-end devices · Gemini vendor policy shifts (kill-switch exists via policy-suspended fallback) · carrier filtering (budgets/pacing mitigate) · clock manipulation (clock-trust blocks) · timezone/DST (reconcile triggers cover TIME/TIMEZONE_CHANGED; planner recompute) · contact deleted upstream mid-cycle (source-contact-deleted issue; approval-invalid) · ambiguous region phones (choice-required) · user expectation of replies (out of scope; composer copy) · web tier misconfig fails closed (runtime-config gate) · signing-authority key loss halts releases (process risk).

# 17. ROADMAP

**Now (shipped):** everything marked ✅ above — Android Automation Edition + web tier + release-admission machinery.
**Next (hardening, [R]):** resolve runBlocking; add crash telemetry decision; README/docs resync; quiet-hours rule; theme persistence; offline UX polish; notification preferences surface (per-category toggles exist server-dedupe side; UI ❓).
**Phase 3 (🔮):** complete iOS Companion — implement reservation callables + companionStatus, rebuild iOS app honoring composer vocabulary; entry criteria: Android SLOs held ≥2 quarters.
**Future:** occasions beyond birthdays; channels; shared plans; premium (never gating core); annual relationship digest; referral moments.

# 18. OPEN QUESTIONS

OQ-01 final brand/store identity · OQ-02 crash-reporting vendor/threshold · OQ-03 Gemini terms written confirmation (policy-suspended kill-switch verified in code) · OQ-04 launch countries/carrier matrix (evidence schema anticipates MCC/MNC rows) · OQ-05 minimum-age declaration · OQ-06 support channel staffing · OQ-07 deletion-SLA marketing number vs drain-window reality · OQ-08 dev/staging source-set intentionality · OQ-09 Settings theme persistence · OQ-10 notification preference UI scope.

---

_Cross-reference: business rationale, stakeholders, compliance, financials, KPIs, traceability → [`BRD.md`](BRD.md) v3.0._
