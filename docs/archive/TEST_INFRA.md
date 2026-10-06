# AI-Birthday: E2E Requirement Test Infrastructure (TEST_INFRA.md)

## 1. Executive Summary & Purpose

This document establishes the architecture, methodology, tier hierarchy, and quality gates for the **End-to-End (E2E) Requirement Test Suite** of the AI-Birthday mobile application and backend ecosystem.

The test suite operates as an **opaque-box requirement verification harness** testing the core production overhaul requirements (R1 through R5) as specified in `ORIGINAL_REQUEST.md` and architected in `PROJECT.md`:
- **R1**: Production Google Authentication & Elimination of Pseudo-Auth
- **R2**: Authoritative Server-Side Subscription Verification
- **R3**: End-to-End Encrypted Zero-PII Cloud Backup Envelope
- **R4**: Real Android AICore Native Bridge
- **R5**: Comprehensive UI/UX, Responsive & Accessibility Remediation (360dp width, 1.5x font scale, Semantics, keyboard occlusion)

---

## 2. Test Architecture & Directory Layout

All E2E requirement tests reside in `test/e2e/` with dedicated subdirectories for harnesses and test doubles:

```
test/e2e/
├── harness/
│   ├── test_harness.dart               # Unified E2E environment with in-memory Drift SQLite & secure store
│   ├── fake_firebase_auth_client.dart  # Identity Platform REST test double for Google ID token exchange
│   ├── fake_subscription_server.dart  # Cloud Functions verifyPurchase contract test double
│   ├── fake_aicore_platform.dart       # Typed com.yashsomani.ai_birthday/nano MethodChannel fake
│   ├── crypto_envelope_fixture.dart    # AES-256-GCM zero-PII envelope generators & corruption fixtures
│   └── responsive_tester.dart          # 360dp & 1.5x font scale rendering assertion utility
├── tier1_features_test.dart            # Tier 1: Core feature verification (R1–R5)
├── tier2_boundary_corner_test.dart     # Tier 2: Boundary, error, corrupt & corner cases
├── tier3_cross_feature_test.dart       # Tier 3: Pairwise cross-feature interactions
├── tier4_user_journeys_test.dart       # Tier 4: Real-world end-to-end user journeys
└── e2e_suite_test.dart                 # Master composite runner executing Tiers 1–4
```

---

## 3. Four-Tier Testing Methodology

The testing strategy follows a rigorous multi-tier hierarchy ensuring breadth, boundary resilience, cross-module cohesion, and realistic end-user flows:

### Tier 1: Feature Coverage (R1 – R5)
Validates that each requirement satisfies its primary functional contract:
- **R1 Feature Tests**:
  - Validates Google Sign-In exchanges Google OAuth ID token for genuine Firebase Auth tokens (`firebaseUid`, `idToken`).
  - Verifies session tokens persist into `SecureStoreDriver`.
  - Verifies sign-out purges all session keys.
  - Verifies complete purge of pseudo-auth (no in-memory OTPs, no synthetic `phone_*`/`email_*` profiles).
  - Verifies PII redaction (email, UID, recipient data) in logger.
- **R2 Feature Tests**:
  - Verifies client parses and respects server-authoritative entitlement status (`users/{uid}/entitlement/status`).
  - Verifies `POST /verifyPurchase` contract validation with product ID, purchase token, and package name.
  - Verifies Pro capabilities are locked until verified by server.
  - Verifies local storage is treated strictly as an offline fallback cache, not an authority.
- **R3 Feature Tests**:
  - Verifies client-side encryption of recipient birthday data into AES-256-GCM envelope before cloud transit.
  - Verifies cloud envelope schema: `schemaVersion`, `backupVersion`, `deviceId`, `iv`, `ciphertext`, `authTag`, `updatedAt`.
  - Verifies zero cleartext PII exists in cloud payload (no recipient names, phones, notes, or raw dates).
  - Verifies clean decryption and restoration of backup into local Drift SQLite database.
- **R4 Feature Tests**:
  - Verifies typed AICore platform channel (`com.yashsomani.ai_birthday/nano`).
  - Verifies granular lifecycle states: `ready`, `downloading_model`, `unsupported_device`, `service_unavailable`.
  - Verifies elimination of canned mock birthday greeting strings.
  - Verifies inference routing through AICore when available, and graceful failure when unavailable.
- **R5 Feature Tests**:
  - Verifies Message Studio scrollable layout and dynamic bottom insets with keyboard active.
  - Verifies responsive Wrap layouts in Dashboard action cards, Settings headers, and dialogs.
  - Verifies Semantics accessibility nodes for screen readers (TalkBack support).

### Tier 2: Boundary & Corner Cases
Validates system resilience under stress, adversarial inputs, network disruptions, and corrupt data:
- **R1 Boundary**: Expired Google ID tokens, empty Firebase Web API key fallback, network failure during IdP exchange.
- **R2 Boundary**: Invalid purchase tokens, mismatched package names, expired subscriptions, server 500 responses, network disconnect during purchase verification.
- **R3 Boundary**: Corrupted ciphertext, tampered auth tag, invalid Base64, unsupported schemaVersion, empty database backup, conflicting version numbers.
- **R4 Boundary**: Unsupported Android API levels, non-Android platforms, native `PlatformException` errors, rapid repeated inference requests.
- **R5 Boundary**: 360dp width viewport, 1.5x font scale (`TextScaler.linear(1.5)`), ultra-long names (300+ characters), empty notes, multiline instruction inputs.

### Tier 3: Pairwise Cross-Feature Interactions
Validates contracts and data flows across subsystem boundaries:
- **Auth ↔ Subscription**: Signing in loads user's remote entitlement; signing out resets entitlement and locks Pro capabilities.
- **Auth ↔ Encrypted Cloud Sync**: Cloud backup is permitted only when authenticated; unauthenticated requests fail gracefully.
- **Subscription ↔ On-Device AI Routing**: Pro entitlement unlocks AI routing (Gemini Nano / Gemini API); Free tier blocks generation without personal API key.
- **Local Drift SQLite ↔ Cloud Envelope Sync**: Multi-device sync scenario where monotonic version vectors resolve conflicts deterministically.

### Tier 4: Real-World End-to-End User Journeys
Simulates real consumer journeys across multiple screens and lifecycles:
- **Journey 1: Onboarding, Recipient Creation & Countdown Feed**:
  - New user completes onboarding -> creates recipient with birthday, relationship, tone -> arrives at Dashboard -> verifies upcoming celebration card and days countdown.
- **Journey 2: Free User Paywall Gate, Purchase & AI Message Generation**:
  - User on Free tier attempts AI generation in Message Studio -> blocked by paywall -> completes subscription purchase verified by server -> generation unlocks -> personalized message draft created.
- **Journey 3: Zero-PII Cloud Backup, Disaster Recovery & Restore**:
  - User populates local recipient database -> triggers encrypted cloud backup -> device storage wiped -> performs cloud restore -> all recipients, notes, and cycle years restored with 100% integrity.
- **Journey 4: Accessibility & Responsive Stress Journey**:
  - User navigates entire app on a 360dp screen at 1.5x font scale -> verifies zero RenderFlex overflows across Dashboard, People, Message Studio, and Settings -> verifies TalkBack semantic labels.

---

## 4. Expected Output Derivation & Verification Sources

| Feature / Requirement | Authoritative Specification Source | Derivation Method |
|---|---|---|
| R1 Auth & Token Exchange | `ORIGINAL_REQUEST.md` R1, `PROJECT.md` M1 | Firebase Auth REST identitytoolkit API contract; secure storage session keys (`auth_session_*`). |
| R2 Server-Side Entitlement | `ORIGINAL_REQUEST.md` R2, `PROJECT.md` M2 | `POST /verifyPurchase` request/response schema; `/users/{uid}/entitlement/status` Firestore schema. |
| R3 Zero-PII Encrypted Envelope | `ORIGINAL_REQUEST.md` R3, `PROJECT.md` M3 | AES-256-GCM envelope JSON schema; Drift SQLite schema (`version`, `deletedAt`); 0 PII regex. |
| R4 AICore Native Bridge | `ORIGINAL_REQUEST.md` R4, `PROJECT.md` M4 | MethodChannel `com.yashsomani.ai_birthday/nano` typed strings (`ready`, `downloading_model`, etc.). |
| R5 Responsive & Accessibility | `ORIGINAL_REQUEST.md` R5, `docs/ui-ux/current-ui-audit.md` | Viewport constraints `Size(360, 640)`, `TextScaler.linear(1.5)`, Flutter widget tree Semantics search. |

---

## 5. Execution Instructions

Run all E2E requirement tests using standard Flutter CLI tooling:

```bash
# Run the entire E2E requirement test suite
flutter test test/e2e/

# Or run specific tiers:
flutter test test/e2e/tier1_features_test.dart
flutter test test/e2e/tier2_boundary_corner_test.dart
flutter test test/e2e/tier3_cross_feature_test.dart
flutter test test/e2e/tier4_user_journeys_test.dart

# Or run the composite suite runner:
flutter test test/e2e/e2e_suite_test.dart
```

---

## 6. Coverage Thresholds & Quality Gates

| Gate Metric | Threshold | Verification Command |
|---|---|---|
| **E2E Test Pass Rate** | 100% (0 failing tests) | `flutter test test/e2e/` |
| **RenderFlex Overflows** | 0 overflows on 360dp / 1.5x font | `flutter test test/e2e/tier2_boundary_corner_test.dart` |
| **Cleartext PII on Cloud** | 0 plaintext fields in envelope | `flutter test test/e2e/tier1_features_test.dart` |
| **Pseudo-Auth Remnants** | 0 fake OTP / synthetic profiles | `flutter test test/e2e/tier1_features_test.dart` |
| **Canned AI Mock Strings** | 0 canned mock birthday strings | `flutter test test/e2e/tier1_features_test.dart` |
