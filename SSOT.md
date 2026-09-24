# WishWell (Birthday Autopilot) — Single Source of Truth (SSOT)
## Version 3.0 (Master Unified Architecture & System Reference — September 24, 2026)

| Field | Value |
| :--- | :--- |
| **Document** | Single Source of Truth — v3.0 (Master Unified Repository Architecture & Reference) |
| **Product** | WishWell · package `birthday-autopilot` v0.1.0 · appId `com.yashsomani.birthdayautopilot` |
| **Source of Truth** | **This document is the sole, authoritative product, architectural, security, operational, and technical reference for the entire repository.** All other documentation is unified and consolidated into this document. |
| **Status** | **Production Ready for Android Launch.** Master AI Gateway, multi-provider execution router, subscription ingestion, and Android native gating complete and verified. |
| **Key Invariant** | `AI_ACCESS_ALLOWED = authenticated AND application_ai_entitlement_active`. Local/on-device AI is an execution cost optimization, NOT a subscription bypass or free tier. |

---

## Table of Contents
1. [Executive Summary & Core Invariants](#1-executive-summary--core-invariants)
2. [Product Overview & Human Trust Boundaries](#2-product-overview--human-trust-boundaries)
3. [Master AI Architecture & Subscription Gateway](#3-master-ai-architecture--subscription-gateway)
4. [Security Policy & Vulnerability Management](#4-security-policy--vulnerability-management)
5. [Native Dependency Advisory Gate](#5-native-dependency-advisory-gate)
6. [Operational Runbooks & Incident Management](#6-operational-runbooks--incident-management)
7. [Android Native Integration & Restricted Release Evidence](#7-android-native-integration--restricted-release-evidence)
8. [UI System & Screen Manifest (63-Screen Catalog)](#8-ui-system--screen-manifest-63-screen-catalog)
9. [Store Submission & Production Release Closure](#9-store-submission--production-release-closure)
10. [Architecture Traceability & Implementation Matrix](#10-architecture-traceability--implementation-matrix)
11. [Developer Quickstart, Build & Verification Commands](#11-developer-quickstart-build--verification-commands)

---

# 1. EXECUTIVE SUMMARY & CORE INVARIANTS

WishWell is an **Android-first autonomous birthday SMS automation system** engineered with a verified trust architecture: human approval of exact payloads, server-enforced single-send guarantees, honest delivery reporting, deletion-grade privacy, and a fail-closed release admission chain.

### Status & Evidence Labels
Every capability carries one Implementation Status label:
- **[COMPLETE]** — Fully implemented and covered by automated tests
- **[STUB/PLANNED]** — Explicitly isolated interface or planned post-launch capability
- **[DELIBERATELY OMITTED]** — Intentionally excluded for security, privacy, or platform policy reasons

### Core Architectural Invariants
1. **Application Subscription Controls AI Access**:
   $$\mathbf{AI\_ACCESS\_ALLOWED} = \mathbf{authenticated} \land \mathbf{application\_ai\_entitlement\_active}$$
   No application subscription = ALL AI blocked. Local AI, on-device AI, cloud Gemini, and fallbacks are completely inaccessible without an active application subscription.
2. **Provider Availability Controls AI Execution Path**: External AI resources (such as Google account credentials) determine *how* an inference request executes, never *whether* AI access is granted.
3. **Local AI is Cost Optimization, Not a Free Tier**: On-device and local synthesis execute without cloud compute costs for subscribed users; they never operate as an un-entitled free tier.
4. **Single-Send Guarantee**: The backend occurrence guard prevents duplicate SMS delivery to the same recipient on the same birthday within a 400-day rolling window.
5. **No Cloud SMS Relay**: All SMS messages are transmitted directly from the user's physical Android SIM card via Android Telephony APIs. The server never relays, stores, or sees message content or phone numbers.
6. **Deletion-Grade Privacy**: Account deletion executes through a distributed deletion saga that issues cryptographic, content-free deletion receipts and guarantees irrecoverable data erasure with 30-day HMAC pepper rotation.

---

# 2. PRODUCT OVERVIEW & HUMAN TRUST BOUNDARIES

### 2.1 Mission & Vision
- **Vision**: The most trusted relationship stewardship system — AI assists, the human decides.
- **Mission**: Make thoughtful birthday communication effortless without feeling synthetic or automated.
- **Value Proposition**: *Set it once. Approve what matters. Never miss a birthday.*

### 2.2 Verified Differentiators
- **Human Approval**: The user reviews and approves the exact message draft before any automated send schedule is created.
- **SIM-Originated Delivery**: Messages originate from the user's real carrier number, preserving natural conversation threads in the default SMS app.
- **Fail-Closed Delivery Reporting**: Delivery reporting states only verified facts. If receipt confirmation is missing, it is labeled **Sent from this phone; delivery not confirmed** rather than falsely claiming delivery.

### 2.3 Platform Scope
- **Android Primary Edition**: Full autonomous automation via native WorkManager, Android TelephonyManager, Room persistence with SQLCipher, and ContactsProvider integration.
- **iOS Scope Note**: iOS Companion Edition is planned for Phase 2. Currently, all iOS code references are shared platform stubs. No background SMS transmission exists on iOS.

---

# 3. MASTER AI ARCHITECTURE & SUBSCRIPTION GATEWAY

### 3.1 Business Model & Feature Entitlement
WishWell charges for an **application subscription** (`wishwell-plus`), which unlocks AI capabilities. The application subscription is an application feature entitlement, NOT a resale of third-party Gemini API quotas.

### 3.2 Conceptual Architecture & Execution Routing
```text
                         USER
                           │
                           ▼
                    ONE GOOGLE LOGIN (Firebase Auth)
                           │
                           ▼
                  APPLICATION ACCOUNT (UID)
                           │
                           ▼
              APPLICATION SUBSCRIPTION ACTIVE?
              (users/{uid}/meta/aiEntitlement)
                           │
                    ┌──────┴──────┐
                    │             │
                INACTIVE        ACTIVE
                    │             │
                    ▼             ▼
              BLOCK ALL AI    AI ACCESS GRANTED
              (AI_SUBSCRIPTION_   │
               REQUIRED)          ▼
                           AI EXECUTION ROUTER
                                  │
                   ┌──────────────┼──────────────┐
                   │              │              │
                   ▼              ▼              ▼
             1. On-Device    2. Local AI     3. User Gemini   4. App Gemini
             (AICore/Nano)   (Bilingual)     (Project Quota)  (Cloud Fallback)
                   │              │              │                 │
                   └──────────────┼──────────────┴─────────────────┘
                                  ▼
                        USAGE ACCOUNTING & LEDGER
```

### 3.3 Separation of Concerns
1. **Identity**: Handled by Firebase Auth via Google Sign-In. Decoupled from entitlement.
2. **Application Subscription**: Recorded in `users/{uid}/meta/aiEntitlement`. Managed by server-side billing webhooks.
3. **AI Entitlement**: Pure projection of subscription status into active capabilities and plan quotas.
4. **Provider Authorization**: Evaluates external AI credentials if available.
5. **Execution Capability**: Identifies physical inference engines (`on-device`, `local`, `user-gemini`, `gemini-cloud`).

### 3.4 Official Google Quota Reality
Consumer Google AI subscriptions (Google One AI Premium, Gemini Pro/Ultra) apply exclusively to Google's first-party apps and web interfaces. They do **not** grant quota for third-party Gemini API calls. Third-party Gemini API access requires an active Google Cloud Platform (GCP) project with billing enabled. `UserGeminiProvider` verifies GCP project quota and marks consumer accounts unauthorized for API quota rather than faking access with developer keys.

### 3.5 4-Tier Execution Hierarchy
When the user holds an active `wishwell-plus` entitlement:
1. **Tier 1: On-Device AI (`on-device`)**: Android Gemini Nano / AICore where supported by hardware. Zero cloud latency and zero compute cost.
2. **Tier 2: Local AI Provider (`local`)**: Deterministic, personalized bilingual (English & Hindi) synthesis engine (`LocalAIProvider`) supporting milestones, relationship hints, and custom tones at zero server cost.
3. **Tier 3: User Gemini Provider (`user-gemini`)**: Authenticated user GCP project API access where explicitly configured.
4. **Tier 4: Application Cloud Gemini (`gemini-cloud`)**: Bounded developer cloud fallback (`GeminiRestAdapter`), limited by daily (50) and monthly (300) plan quotas and a monthly global budget circuit breaker.

### 3.6 Android Native Module Gating
In `BirthdayNativeModule.kt` and `AndroidGeminiSuggestionGateway.kt`, incoming `"generate-suggestions"` requests are strictly validated against `AiEntitlementSnapshot`. If the user is un-entitled, native execution fails closed immediately and returns safe fallback templates with error code `ai-subscription-required`.

### 3.7 Offline Entitlement Cache & Grace Period
- Offline AI synthesis is permitted only if a valid cached entitlement exists.
- **Maximum Offline Grace Period**: 72 hours (`OFFLINE_ENTITLEMENT_GRACE_PERIOD_MS = 259,200,000 ms`).
- If `nowMs - lastVerifiedAtMs > gracePeriodMs`, the cached entitlement is rejected with `grace-period-exceeded` until the client reconnects and revalidates online.

### 3.8 Machine-Readable AI Error Codes
The AI architecture defines 10 standardized machine-readable error codes:
1. `AI_SUBSCRIPTION_REQUIRED` (`ai-subscription-required`) — No active application subscription.
2. `AI_PROVIDER_UNAVAILABLE` (`ai-provider-unavailable`) — Selected provider is offline or unreachable.
3. `AI_PROVIDER_NOT_AUTHORIZED` (`ai-provider-not-authorized`) — Provider credentials missing or unauthorized.
4. `AI_PROVIDER_AUTH_EXPIRED` (`ai-provider-auth-expired`) — OAuth token or credentials expired.
5. `AI_FEATURE_NOT_SUPPORTED` (`ai-feature-not-supported`) — Capability requested not supported by provider.
6. `AI_QUOTA_EXCEEDED` (`ai-quota-exhausted`) — Daily (50) or monthly (300) user request limit reached.
7. `AI_RATE_LIMITED` (`ai-rate-limited`) — Short-term burst threshold exceeded.
8. `AI_UNAVAILABLE` (`ai-unavailable`) — General service disruption.
9. `AI_EXECUTION_FAILED` (`ai-execution-failed`) — Internal inference failure.
10. `AI_CONFIGURATION_ERROR` (`ai-configuration-error`) — Misconfigured provider parameters.

### 3.9 Multi-Platform Billing Ingestion
- **Google Play RTDN**: Webhook handler `onPlayBillingEvent` parses Play Real-Time Developer Notifications (`reducePlayEvent`), maps SKUs to plans, and writes to `users/{uid}/meta/aiEntitlement`.
- **Stripe Subscriptions**: Webhook handler `onStripeBillingEvent` parses Stripe customer subscription events (`reduceStripeEvent`), mapping price IDs to plans (`price_wishwell_plus_monthly`).
- **Ordering & Idempotency**: Updates use last-write-wins based on `occurredAtMs`. Any event with `occurredAtMs <= existing.updatedAtMs` is safely ignored as stale.

---

# 4. SECURITY POLICY & VULNERABILITY MANAGEMENT

### 4.1 Reporting a Vulnerability
Use the repository host's private security-advisory channel and include:
- the affected source revision and platform/version;
- a minimal reproduction using synthetic contacts and messages;
- whether the issue could affect recipient choice, message content, sender/SIM, duplicate prevention, credentials, protected storage, account deletion, or privacy boundaries.

**Do not send real user data**, provider credentials, signing material, HMAC peppers, service-account keys, deletion receipt bearers, or production exploit traffic.

A critical issue affecting unintended SMS, duplicate prevention, credential exposure, deletion fencing, or protected contact/message data requires immediate fail-closed containment using the operations runbook.

### 4.2 SMS Safety Boundary
The core security boundary protects users against unintended SMS transmission:
- **Physical Device Origin**: No SMS can be triggered by external cloud command. An SMS can only be sent when an on-device Android worker acquires an authorized claim, arms the local Android AlarmManager, verifies user approval, and executes via native TelephonyManager.
- **Duplicate Prevention**: The 400-day occurrence guard ensures duplicate prevention so no recipient receives more than one birthday SMS per calendar year.
- **Deletion Fencing**: Deletion tombstones provide strict deletion fencing to immediately revoke and invalidate all pending execution claims and prevent any queued SMS transmission.
- **Local Database Encryption**: SQLite databases on Android are encrypted at rest using SQLCipher with keys derived from the hardware-backed Android Keystore.
- **Zero Cloud Contact Storage**: Recipient phone numbers and contact details are never stored on cloud servers. Cloud ledgers use irreversible HMAC-SHA256 aliases seeded by an hourly rotating pepper. Old aliases expire and become unreachable after 30 days.

---

# 5. NATIVE DEPENDENCY ADVISORY GATE

### 5.1 Native Dependency Verification & Lock Policy
Android dependencies are strictly locked using Gradle dependency verification with strict lockMode:
`dependencyVerification { lockMode = LockMode.STRICT }`

The native dependency advisory gate inspects all runtime classpath dependencies:
- Verifies explicit Group-Artifact-Version (GAV) coordinates.
- Validates the build runtime across `prodReleaseRuntimeClasspath` and debug variants.
- Enforces dependency lock integrity in CI (`npm run security:native:android`).

### 5.2 Zero-Result Limitation & Scope
Ordinary CI requires and enforces exactly zero exceptions during automated verification.
The native dependency advisory gate provides automated scanning of public vulnerability registries (OSV, NVD). However, passing this gate is not proof that dependencies are free of vulnerabilities, as zero-day disclosures or uncataloged issues cannot be detected prior to publication.

If a scan service reports an UNPROVISIONED state or is temporarily unavailable, the gate halts the release pipeline in a fail-closed manner until authoritative vulnerability database connectivity is restored.

---

# 6. OPERATIONAL RUNBOOKS & INCIDENT MANAGEMENT

### 6.1 Binding Operational Incidents
The following runbooks govern production operations:

#### 1. Release rollback or unsafe build
- **Trigger**: Critical defect or unexpected behavior discovered in a released build.
- **Procedure**: Halt Play Console staged rollout immediately. Set `GlobalControl.armingEnabled = false` in Remote Config to freeze automated client scheduling. Deploy previous stable build.

#### 2. Android signing-key incident
- **Trigger**: Compromised keystore or upload key loss.
- **Procedure**: Engage Google Play Developer Support for upload key reset via Play App Signing. Verify SHA-256 fingerprint against tracked repository provenance before deploying replacement releases.

#### 3. HMAC pepper rotation
- **Trigger**: Scheduled 30-day rotation or suspected pepper compromise.
- **Procedure**: Cloud Functions automatically transition the current pepper to previous and generate a fresh cryptographically secure random pepper. Historical HMAC aliases become unreachable.

#### 4. Functions, Firestore, or regional outage
- **Trigger**: Google Cloud regional incident in `asia-south1`.
- **Procedure**: Native Android clients observe network failures and operate in disconnected mode. Local SMS scheduling for already-approved wishes continues on-device. If server continuityState becomes FROZEN, previously issued permit may still cross boundary if locally armed, but no new permits will be issued.

#### 5. Ledger corruption, disaster recovery, or duplicate report
- **Trigger**: Conflicting occurrence records or corrupted accounting documents.
- **Procedure**: Freeze arming via `GlobalControl.armingEnabled = false`. Inspect Firestore transaction history. Restore from automated daily backups. Do not delete or rewrite an Armed claim directly in Firestore without executing the cancellation protocol.

#### 6. Account-deletion failure
- **Trigger**: Cloud deletion saga fails to reach verified terminal state within 48 hours.
- **Procedure**: Deletion orchestrator flags tombstone for manual dead-letter queue review. Execute administrative purge callable `purgeUserAdmin` to cascade deletion across auth, storage, and database.

#### 7. Gemini safety, privacy, or cost incident
- **Trigger**: Unanticipated Gemini content filter trigger, rate surge, or budget alarm.
- **Procedure**: AI Gateway automatically circuit-breaks if global monthly budget is exceeded. Operators can force execution router fallback to built-in templates and `LocalAIProvider` by setting provider availability flags to false.

#### 8. OAuth, Google People, or Firebase identity incident
- **Trigger**: Google People API quota exhaustion or token revocation loop.
- **Procedure**: Client caches contacts in encrypted local database; app displays reconnect banner. Backend rate limiter throttles token refresh requests to avoid cascading authorization failures.

#### 9. SEND_SMS policy, installer, carrier, or legal suspension
- **Trigger**: Regulatory change or Play Store policy notification regarding SMS permission.
- **Procedure**: Provide verified restricted release evidence documentation and Play Console declaration video demonstrating physical human approval of every message.

#### 10. Native dependency advisory or scan-service incident
- **Trigger**: High-severity CVE reported in an Android or Node.js dependency.
- **Procedure**: Audit dependency tree via `tools/run-native-advisory-gate.mjs`. Bump affected library version or apply dependency substitution rule in `android/build.gradle`. Re-run verification suite.

#### 11. Recovery checklist
- **Post-Incident Checklist**:
  1. Confirm `GlobalControl.armingEnabled` is restored to `true`.
  2. Verify all affected user claims are reconciled.
  3. Validate database consistency and ensure zero duplicate sends occurred.
  4. File incident root cause analysis (RCA) with permanent remediation steps.

---

# 7. ANDROID NATIVE INTEGRATION & RESTRICTED RELEASE EVIDENCE

### 7.1 Android Telephony & Permissions Declaration
WishWell requires the restricted `android.permission.SEND_SMS` and `android.permission.READ_CONTACTS` permissions.
- **SEND_SMS Rationale**: The app's core value proposition is autonomous transmission of personalized birthday wishes on the recipient's birthday directly from the user's SIM card.
- **Human Approval**: The user must explicitly approve every recipient, message text, and sending window. No SMS is ever sent without prior immutable approval.
- **No In-App Surprises**: The in-app setting `gemini_suggestions_enabled` has an in-app default is **false**, requiring explicit user opt-in before suggestions are requested.

### 7.2 Native AppCheck & Installations Token
- Native Android calls to backend Cloud Functions are protected by Firebase App Check backed by the Play Integrity API.
- Native requests carry Firebase's native Installations token to verify app authenticity and prevent headless replay attacks.

### 7.3 Native Execution Paths & Advisory Gate
The on-device native Android Gemini API path (Gemini Nano / AICore via `AiGatewayRoutingPolicy`) is gated behind device capability, treated as variable availability, and never a subscription prerequisite. Cloud Firebase AI (`firebase-ai:17.13.0`, Vertex global, `gemini-3.5-flash`) remains a supported cloud path. All native dependency advisories flow through the fail-closed native dependency advisory gate (`npm run security:native:android`), which must report exactly zero exceptions in ordinary CI.

---

# 8. UI SYSTEM & SCREEN MANIFEST (63-SCREEN CATALOG)

### 8.1 UI Principles
1. **Calm, Neutral, Trustworthy**: Material 3 on Android; no confetti, animations, or gamified sending.
2. **Three Permanent Tabs**: **Home**, **People**, and **Settings**.
3. **Accessibility**: Minimum 48dp touch targets, full TalkBack screen reader support, Dynamic Type supporting 200% text scaling.
4. **Bilingual Support**: Full native support for English (`en`) and Hindi (`hi`).

### 8.2 Master Screen Manifest Table (63 Screens)

| ID  | Title                          | Screen Category                     | Critical Variant Classes |
| :-- | :----------------------------- | :---------------------------------- | :----------------------- |
| G01 | Secure startup                 | Global and setup                    | First launch, safety ledger restore, deletion pending, recoverable startup failure |
| G02 | Main shell                     | Global and setup                    | Home/People/Settings tab navigation, action-needed indicator, offline, Dynamic Type |
| S01 | Welcome and compatibility      | Global and setup                    | Telephony, SIM detection, Play services availability, offline check |
| S02 | Connect with Google            | Global and setup                    | Credential Manager sign-in, Workspace account notice, offline retry |
| S03 | Active sender gate             | Global and setup                    | Active phone status, Standby mode, cooperative transfer check |
| S04 | Contacts disclosure            | Global and setup                    | Privacy disclosure, permission rationale, cancellation handling |
| S05 | Contacts authorization return  | Global and setup                    | Permission return state, partial grant, bounded reconnect |
| S06 | First Contacts sync            | Global and setup                    | Sync progress, birthday extraction summary, empty contacts handling |
| S07 | Choose people                  | Global and setup                    | Selection list, birthday filters, duplicate destination warning |
| S08 | Bulk recipient review          | Global and setup                    | Review count, conflict resolution, invalidation check |
| S09 | Repair person                  | Global and setup                    | Ambiguous phone numbers, Feb 29 policy, missing name resolution |
| S10 | Approve person                 | Global and setup                    | Immutable approval, placeholder validation, segment count, SIM selection |
| S11 | Template editor                | Global and setup                    | Bilingual EN/HI editor, placeholder insertion, length counter |
| S12 | Gemini suggestions             | Global and setup                    | AI suggestions, tone selection, quota boundary, fallback to local template |
| S13 | Delivery window                | Global and setup                    | Send window configuration, morning/afternoon timing, grace period |
| S14 | SIM policy                     | Global and setup                    | Default SIM selection, dual-SIM picker, roaming safety warning |
| S15 | Recipient and message review   | Global and setup                    | Exact preview, segment cost estimation, immutable approval commit |
| S16 | Test destination               | Global and setup                    | Diagnostic self-test SMS destination, 3-per-24h budget enforcement |
| S17 | Test review and SMS disclosure | Global and setup                    | Pre-send confirmation, carrier charge warning, explicit consent |
| S18 | Test status                    | Global and setup                    | Real-time test send delivery verification, TestReceipt creation |
| S19 | Background readiness           | Global and setup                    | Doze exemption diagnostic, OEM battery optimization check |
| S20 | Final activation review        | Global and setup                    | Pre-flight checklist, permission audit, autonomous schedule confirmation |
| S21 | Activation result              | Global and setup                    | System armed, automation active, initial schedule preview |
| H01 | Home                           | Home                                | Automation status hero, next upcoming birthday, attention banners |
| H02 | Upcoming                       | Home                                | Chronological birthday feed, approval status badges, search |
| H03 | Approved message preview       | Home                                | Read-only view of scheduled wish, timing window, cancel schedule action |
| H04 | Pause automation               | Home                                | Global pause toggle, active claim safe drain, confirmation modal |
| H05 | Resume readiness               | Home                                | Permission and SIM revalidation, resume automation confirmation |
| H06 | Today decision                 | Home                                | Same-day birthday action sheet, manual trigger alternative |
| P01 | People list                    | People                              | Enrolled recipients, filter by status, search by name, add button |
| P02 | Person detail                  | People                              | Recipient profile, approved template, birthday history, edit route |
| P03 | Excluded people                | People                              | Excluded contacts list, re-enroll option, exclusion rationale |
| P04 | Approval invalidation          | People                              | Contact edit detection, invalidation notice, re-approval prompt |
| A01 | Activity                       | Activity, attention, diagnostics    | Historical send feed, delivery status icons, date filtering |
| A02 | Activity detail                | Activity, attention, diagnostics    | Full transmission report, carrier timestamp, error diagnosis |
| A03 | Needs your attention           | Activity, attention, diagnostics    | Critical issue list: revoked permissions, missing SIM, failed send |
| A04 | Issue repair                   | Activity, attention, diagnostics    | Step-by-step resolution wizard for configuration issues |
| A05 | Diagnostics preview            | Activity, attention, diagnostics    | Allowlisted diagnostic log viewer, copy report action |
| A06 | Clear activity                 | Activity, attention, diagnostics    | Purge send history while retaining 400-day safety ledger |
| T01 | Settings home                  | Settings                            | Grouped settings menu, account overview, version number |
| T02 | Automation policy              | Settings                            | Sending hours, default window, holiday sending preferences |
| T03 | Message                        | Settings                            | Default message templates, signature settings, AI tone defaults |
| T04 | SIM and charges                | Settings                            | SIM preference, international SMS restriction, cost warnings |
| T05 | Notifications                  | Settings                            | Send confirmation alerts, low battery warnings, reminder toasts |
| T06 | Google, Contacts, and sender   | Settings                            | Google account status, manual sync trigger, device role status |
| T07 | Device readiness               | Settings                            | Battery optimization status, AlarmManager permission check |
| T08 | Privacy and data               | Settings                            | Data inventory, deletion request button, privacy policy link |
| T09 | Data inventory and retention   | Settings                            | Local storage breakdown, cache clear action, security overview |
| T10 | Help, legal, and about         | Settings                            | Terms of service, open-source licenses, contact support |
| L01 | Sender transfer                | Lifecycle and transfer              | Device handoff wizard, new phone discovery, security code |
| L02 | Transfer approval on old phone | Lifecycle and transfer              | Relinquish authority prompt, active claim drain notification |
| L03 | Transfer drain and status      | Lifecycle and transfer              | Transfer progress, claim migration, authority handoff receipt |
| L04 | Retained-account reconnect     | Lifecycle and transfer              | Re-authentication prompt for existing user on new device |
| L05 | Sign out                       | Lifecycle and transfer              | Disconnect account, retain local encrypted data choice |
| L06 | Disconnect Contacts            | Lifecycle and transfer              | Revoke Google Contacts sync, purge unapproved contacts |
| L07 | Revoke all Google access       | Lifecycle and transfer              | Full OAuth revocation, backend session termination |
| L08 | Delete local app data          | Lifecycle and transfer              | Encrypted Room database wipe, local keystore reset |
| L09 | Delete app account             | Lifecycle and transfer              | Initiate distributed deletion saga across device and cloud |
| L10 | Operation receipt              | Lifecycle and transfer              | Cryptographic confirmation receipt for account or data deletion |
| L11 | Account switch blocker         | Lifecycle and transfer              | Safety barrier preventing concurrent logins on single device |
| W01 | External deletion landing      | Hosted web deletion                 | Web-based deletion request portal for GDPR/Play Store compliance |
| W02 | Deletion verification          | Hosted web deletion                 | Email/Google verification for remote deletion requests |
| W03 | External deletion receipt      | Hosted web deletion                 | Content-free cryptographic receipt confirming full cloud erasure |

---

# 9. STORE SUBMISSION & PRODUCTION RELEASE CLOSURE

### 9.1 Google Play Store Compliance
WishWell strictly satisfies Google Play Policy requirements for restricted SMS access:
- **Core Functionality Requirement**: Autonomous birthday greeting transmission is the advertised, documented core feature of the application.
- **User Verification**: The user personally reviews and confirms the sending policy, message text, and recipient enrollment.
- **Transparency**: No hidden background relays. Delivery occurs via standard platform TelephonyManager APIs.

### 9.2 Production Release Closure
- All release builds are signed with hardware-backed upload keys.
- Signed release manifests carry Ed25519 signatures validating component hashes.
- Backend functions deploy to Google Cloud Platform region `asia-south1`.

---

# 10. ARCHITECTURE TRACEABILITY & IMPLEMENTATION MATRIX

| Architectural Component | Source Location | Tests & Evidence | Status |
| :--- | :--- | :--- | :---: |
| **AI Gateway Service** | `backend/functions/src/services/aiGateway.ts` | `test/aiGateway.test.ts` (17 tests) | **[COMPLETE]** |
| **Execution Router** | `backend/functions/src/domain/aiProviders.ts` | `test/aiExecutionRouter.test.ts` (19 tests) | **[COMPLETE]** |
| **Local AI Provider** | `backend/functions/src/domain/aiProviders.ts` | `test/aiProviders.test.ts` (14 tests) | **[COMPLETE]** |
| **Subscription Ingestion** | `backend/functions/src/services/subscriptionIngestion.ts` | `test/subscriptionIngestion.test.ts` (24 tests) | **[COMPLETE]** |
| **Domain AI Model** | `backend/functions/src/domain/aiModel.ts` | `test/aiModel.test.ts` (22 tests) | **[COMPLETE]** |
| **Android Native Gating** | `android/.../BirthdayNativeModule.kt` | Native unit tests & schema validation | **[COMPLETE]** |
| **Client AI Port** | `src/application/ports/AiPort.ts` | Jest unit test suite (400 tests) | **[COMPLETE]** |
| **Cloud Functions** | `backend/functions/src/functions/index.ts` | `test/transport.test.ts`, `test/paths.test.ts` | **[COMPLETE]** |
| **Firestore Security** | `backend/firestore.rules` | Rules security verification tests | **[COMPLETE]** |

---

# 11. DEVELOPER QUICKSTART, BUILD & VERIFICATION COMMANDS

### Prerequisites
- Node.js 22.x LTS, npm 11.x
- JDK 17 (for Android build)
- Android SDK Platform 35, Build-Tools 35.0.0

### Essential Commands
```bash
# Install root dependencies
npm install

# Run frontend Jest tests (33 suites, 400 tests)
npm test

# Run frontend TypeScript typecheck (0 errors)
npm run typecheck

# Run backend Vitest tests (17 suites, 171 tests)
npm --prefix backend/functions test

# Run backend TypeScript typecheck (0 errors)
npm --prefix backend/functions run typecheck

# Run tools & architectural contract test suite
npm run test:tools

# Run security secret and dependency scans
npm run security:secrets
npm run security:native:android

# Assemble Android debug APK
cd android && ./gradlew :app:assembleDevDebug
```

---
*End of Master Single Source of Truth (SSOT.md) v3.0*
