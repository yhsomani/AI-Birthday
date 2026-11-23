# Flutter Feature Audit — Phase 2.1.1

**Repository:** `yhsomani/AI-Birthday`  
**Branch:** `main`  
**Source baseline:** `85795e845a945ac3442405ed843b4f12c6983249`  
**Audit date:** 2026-11-23  
**Scope:** Flutter feature source and Dart test inventory; documentation only  
**Status:** **Feature inventory and first-pass behavior audit complete; external/runtime verification remains open.**

## 1. Audit method and boundaries

The recursive Git tree was read at the source baseline above. The audit fetched all 65 Dart files under `lib/features/` and all 72 Dart files ending in `_test.dart` under `test/`. All 15 feature domains were mapped to their source files and test files. Test case names and assertion counts were inventoried; selected high-risk tests and source paths were read in detail.

This is a source/test audit, not a device test, penetration test, live integration test, production deployment review, or line-by-line acceptance-criteria certification. In particular, external services are represented by fakes or mocked HTTP clients in the test suite. The remaining gaps are explicit below.

Related inventories:
- [65-file feature source inventory](flutter-source-inventory.md)
- [72-file Dart test inventory](flutter-test-inventory.md)
- [Feature implementation matrix](feature-implementation-matrix.md)
- [Source-to-test traceability](feature-source-test-traceability.md)

## 2. Executive findings

1. **15 feature domains / 65 feature source files** are present.
2. **72 Dart test files** are present: 47 under `test/features/` and 25 cross-cutting, AI, app, database, E2E, security and UI tests.
3. The latest CI run for this exact source commit is [45231730459](https://github.com/yhsomani/AI-Birthday/actions/runs/45231730459): 459 Flutter tests passed and one failed; static analysis and formatting passed; backend dependency installation failed; Hosting checks passed.
4. The failing Flutter test is the 320px Calendar compact-screen no-overflow assertion. Actual overflow: 0.667px at the bottom.
5. Several test suites verify production classes with fake HTTP clients, fake platform channels, fake purchase stores or in-memory repositories. They do not establish live Google, Play Billing, Firebase, Stripe or Android runtime behavior.
6. **Critical Stripe architecture finding:** Flutter calls Stripe REST APIs directly using a secret key loaded from `String.fromEnvironment('STRIPE_SECRET_KEY')`. The Settings screen then calls `setDevSandboxEntitlement` to grant a local active entitlement after a client-side email lookup. This is not a safe production entitlement-verification design. The checked-in default secret is empty; CI and the repository Android build script do not pass `STRIPE_SECRET_KEY`. No Stripe backend Function or service source was found in the repository tree.
7. **AI policy comment conflict:** `AiRouter.generate` checks `entitlement.canUseAi` before provider selection, and `test/ai/ai_router_test.dart` verifies a free user is blocked even with a Gemini key. The router's top-level comments still say BYOK bypasses Pro.
8. The path `lib/features/people/domain/person.dart` is only a re-export of `models/person.dart`; it is not a second Person implementation. This clarifies an earlier audit uncertainty.

## 3. Domain-by-domain assessment

| Domain | Main user capability / source anchors | Test evidence | Assessment |
|---|---|---|---|
| AI | Prompt construction, Gemini API provider, Nano provider and entitlement-aware router | Prompt builder, router and Gemini API tests; tiered E2E uses fake AICore | **Implemented / test-backed.** Router behavior matches entitlement policy; stale comments conflict. Nano provider has no dedicated unit-test file; E2E exercises it with fake AICore. |
| Auth | Google sign-in, Firebase Identity Platform exchange, secure session persistence/refresh, sign-out | Auth controller, live gateway, stress and adversarial tests; settings UI uses a fake gateway | **Implemented / test-backed.** Live gateway behavior is tested with mocked HTTP and fake Google Sign-In platform; real OAuth and device keystore remain unverified. |
| Birthdays | Leap-year/timezone-aware recurrence and one active birthday cycle per person | Birthday engine, lifecycle service and domain calculation tests | **Implemented / test-backed.** Tests cover Feb 29, timezone frames, lifecycle rollover and duplicate birthday rows. |
| Calendar | Month grid, birthday markers, day sheet and month navigation | Empty/error/screen tests plus responsive suite | **Broken in current CI.** Compact 320px Calendar overflow test fails by 0.667px; other screen tests exist. |
| Dashboard | Upcoming birthday summary, action-needed cards, draft/send/history actions | Dashboard visibility tests, E2E responsive coverage | **Implemented / partially verified.** Visibility/status behavior has direct tests; live data and all edge states need journey-level mapping. |
| Delivery | WhatsApp deep-link builder, external SMS composer, share sheet, native scheduled SMS and persisted handoff events | WhatsApp builder and birthday SMS service tests; handoff repository transaction tests | **Partial verification.** Eligibility and URL construction are tested. No dedicated Dart tests for NativeShareService, NativeSmsGateway or SmsDeliveryService; native send/schedule behavior needs device testing. |
| History | Honest delivery state based on persisted handoff vs explicit user confirmation | History status truthfulness test and latest-handoff provider test | **Implemented / test-backed.** Tests explicitly prevent a handoff from being labelled as sent without user confirmation. |
| Jobs | Durable SQLite queue, AI/cloud handlers, retries, cancellation and stale-job recovery | Job repository, worker, AI handler and cloud handler tests | **Implemented / test-backed.** Tests cover idempotency, retry budget, cancellation, process-crash recovery and account ownership. |
| Message Studio | Draft editing/autosave, AI generation/rewrite, delivery handoff and confirmation | Durable, not-found, optimistic and visibility tests; job-handler tests | **Implemented / test-backed with integration gaps.** Stale-result and durable-job cases are tested; full real-provider and device delivery journeys are not. |
| Onboarding | First-run guidance, skip, manual entry and phone contact import handoff | Onboarding screen tests | **Implemented / test-backed.** Screen flow is covered; persistence is tested with fake storage. |
| People | Create/edit/delete/undo, validation, search, CSV import/export and device contacts | 11 feature tests plus Drift repository tests | **Strong source/test coverage.** CSV round-trip, duplicates, formula escaping, soft delete/restore and validation are covered. Device contact permission/runtime remains unverified. |
| Reminders | Lead planning, quiet hours, persistence, notification permissions and native scheduling | Scheduler, quiet-hours, service, settings controller and gateway tests | **Implemented / test-backed at logic layer.** Gateway tests exercise non-live Android no-op behavior; actual alarm delivery, reboot and permission changes need device verification. |
| Settings | Auth, Gemini key setup, reminders, cloud job status, notification recovery and automatic SMS settings | Six Settings UI test files | **Partial verification.** Key setup and settings flows are tested. CloudBackupCard and some integration controls lack dedicated widget tests. Stripe path has a critical architecture finding. |
| Subscription | Google Play purchase/restore with server verification; Stripe checkout/portal path | Subscription service, verification and Stripe gateway tests | **Google Play client flow is test-backed with fakes. Stripe path is not production-safe:** secret key is used in Flutter; local entitlement is set through a test-hook method after client-side lookup. |
| Sync | Owner-scoped Firestore REST backup/restore and local reconciliation | CloudSyncService tests with mocked HTTP | **Implemented / test-backed at transport/service layer.** Tests cover 500-write chunking, generation publication, malformed rows and tombstone-safe restore. Live Firestore/rules/deployed environment remain unverified. |

## 4. Critical and high-priority findings

### F-UI-001 — Calendar compact viewport CI failure

- **Severity:** P1 quality blocker.
- **Source:** Calendar screen and `test/ui/responsive_mobile_test.dart`.
- **Evidence:** Latest CI run reports `A RenderFlex overflowed by 0.667 pixels on the bottom` for the 320px Calendar test.
- **Impact:** Flutter CI remains red and Android build/manifest steps are skipped.
- **Action:** Fix layout constraints in a separately authorized implementation task, then rerun the complete Flutter job and confirm Android builds execute.

### F-SEC-001 — Stripe secret-key operations run in Flutter

- **Severity:** P0 security architecture blocker for a production Stripe integration.
- **Source:** `lib/core/config/stripe_config.dart`, `lib/features/subscription/application/stripe_gateway_service.dart`, `lib/features/settings/presentation/settings_screen.dart`.
- **Evidence:** Flutter reads `STRIPE_SECRET_KEY` via compile-time environment; the gateway sends it as `Authorization: Bearer` to Stripe Checkout, Customers, Subscriptions and Billing Portal endpoints. A compile-time Dart define is embedded in the built client artifact and cannot be treated as a server secret.
- **Entitlement concern:** After client-side email-based subscription lookup, Settings calls `setDevSandboxEntitlement`, whose implementation is a direct local state assignment and whose source comment calls it a test hook.
- **Repository evidence:** No Stripe-specific backend source or Cloud Function was found. CI APK build commands pass only `FIREBASE_WEB_API_KEY`; `build-and-install-android.bat` passes no Stripe define. The checked-in Stripe secret default is empty.
- **Impact:** Passing a real secret into the app build would expose it. Leaving it unset means the direct Stripe API requests have no usable secret. The client-side entitlement path is not an acceptable authority for paid access.
- **Required design correction:** Move all secret-key Stripe operations and subscription verification to a trusted backend. Verify checkout/session/customer ownership server-side (prefer stable authenticated UID binding, not email-only lookup), process signed Stripe webhooks, and return a server-verified entitlement. Remove client use of the test-hook setter from production flows. Rotate any real secret that may already have been shipped in an app artifact. Do not put a replacement secret in Dart defines.

### F-AI-001 — Router comments contradict executable subscription policy

- **Severity:** P1 documentation/code consistency.
- **Source:** `lib/features/ai/domain/ai_router.dart`.
- **Evidence:** Comments claim BYOK bypasses Pro; executable code checks entitlement first; router tests assert free + personal key is locked.
- **Action:** Correct stale comments and any conflicting SSOT/product documentation after confirming the existing owner decision. Do not change runtime behavior as part of this documentation audit.

### F-TEST-001 — E2E naming overstates live integration verification

- **Severity:** P1 evidence quality.
- **Evidence:** Tier 1/2 tests use `FakeFirebaseAuthClient`, fake subscription server and fake AICore platform. The R1.1 test manually exercises a fake REST response and writes test credentials to the test store. Play Billing tests use fake purchase drivers and mocked HTTP. Sync tests use injected/mock HTTP. Stripe tests use `MockClient`.
- **Impact:** These tests are valuable contract and client-behavior tests, but they do not prove production Firebase, Google Play, Stripe, Gemini or physical Android integration.
- **Action:** Keep separate labels for unit/widget, fake-backed integration, emulator integration, live sandbox and physical-device verification.

### F-PLATFORM-001 — Native delivery adapters lack direct Dart tests

- **Severity:** P1 platform confidence gap.
- **Source:** `native_share_service.dart`, `native_sms_gateway.dart`, `sms_delivery_service.dart`.
- **Evidence:** No dedicated Dart test file directly targets these adapters. Existing birthday SMS tests exercise scheduling policy with fakes; WhatsApp tests exercise URL construction.
- **Action:** Add method-channel contract tests and verify on personal/play flavors and real devices before claiming automated delivery is reliable.

## 5. Corrected findings

- `lib/features/people/domain/person.dart` is a canonical re-export of `lib/features/people/domain/models/person.dart`; there is one Person model definition in the feature tree.
- The source/test inventory is newer than the feature registry snapshot. Do not preserve legacy registry labels as current completion status without re-verification.
- A successful fake-backed test is evidence of the tested code path against that fake, not evidence of a live external integration.

## 6. Phase 2.1.1 exit status

**PASS for Flutter source/test inventory and first-pass feature assessment. PARTIAL for full Phase 2.1.**

The immediate Flutter feature audit has identified all 15 domains, all 65 feature source files and all 72 test files, mapped direct test coverage and recorded critical gaps. Phase 2.1 remains open until assertion-level acceptance-criterion traceability, backend/data/platform audits and documentation reconciliation are completed. The P0 Stripe architecture finding must be triaged before a production billing release.

No application source, test, dependency, build, configuration, schema or infrastructure files were changed by this audit.
